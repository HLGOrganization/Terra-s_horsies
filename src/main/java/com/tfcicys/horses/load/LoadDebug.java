package com.tfcicys.horses.load;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.TfcIcysHorses;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * 负重链路的诊断输出。
 *
 * <p>存在的理由：马匹的负重要经过两道<b>静默 return</b>——
 * 一是负重属性没挂到该实体上（{@code LoadAttributeRegistration} 没生效），
 * 二是「这匹马在拉哪辆车」的索引没命中（{@link CartPullRegistry} 是空的）。
 * 两者的症状完全一样：马匹的负重纹丝不动，日志里一个字都没有。
 * 而玩家那条路不经过第一道（玩家属性由 More Attributes 自己注册），
 * 所以「玩家正常、马匹不生效」几乎必然是这两道之一。这里把中间量全部打出来。
 *
 * <p>默认关闭，用 {@code -Dterras_horsies.debugLoad=true} 或配置项打开；
 * 诊断本身出错绝不向上抛——它在 tick 循环里跑，崩了就是服务器崩。
 */
public final class LoadDebug {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 每只实体上次输出的时刻，防止 100 tick 的采样被重复打成刷屏。 */
    private static final Map<UUID, Long> LAST_DUMP = new ConcurrentHashMap<>();

    /** 每辆车上次的货箱读取摘要，只在结果变化时打一行。 */
    private static final Map<UUID, String> LAST_CARGO = new ConcurrentHashMap<>();

    /** 每辆车上次的货物审计摘要（不受开关控制，见 cargoAudit）。 */
    private static final Map<UUID, String> LAST_AUDIT = new ConcurrentHashMap<>();

    /** 已经报告过「属性缺失」的实体类型，每种只报一次。 */
    private static final Set<String> MISSING_REPORTED = ConcurrentHashMap.newKeySet();

    /**
     * 诊断开关，解析一次后缓存。
     *
     * <p>它在 tick 循环里被每匹马每 tick 问到，所以不能每次都去读配置或系统属性。
     * 配置尚未加载时不缓存——那时读到的 false 是假的。
     */
    private static volatile Boolean enabled;

    private LoadDebug() {}

    /** 诊断开关。任何读取失败都当作关闭。 */
    public static boolean enabled() {
        final Boolean cached = enabled;
        if (cached != null) {
            return cached;
        }
        // JVM 参数优先：专用服务器改启动脚本比改配置快，也不会被 Forge 重写配置时覆盖。
        if (Boolean.parseBoolean(System.getProperty("terras_horsies.debugLoad", "false"))) {
            enabled = Boolean.TRUE;
            return true;
        }
        try {
            if (!TFCICYSConfig.COMMON_SPEC.isLoaded()) {
                return false;
            }
            final boolean resolved = TFCICYSConfig.COMMON.debugLoad.get();
            enabled = resolved;
            return resolved;
        } catch (final Throwable notReadyYet) {
            return false;
        }
    }

    /**
     * 每 100 tick 输出一次这匹马的负重链路快照。
     *
     * <p>客户端也会跑：如果服务端算得对而客户端 {@code maxInstance/curInstance} 是 null，
     * 说明属性根本没同步过去（界面看不到数字），那是另一个问题，必须能区分开。
     */
    public static void dumpHorse(LivingEntity horse) {
        if (!enabled()) {
            return;
        }
        final long now = horse.level().getGameTime();
        if (now % 100L != 0L) {
            return;
        }
        final UUID id = horse.getUUID();
        final Long previous = LAST_DUMP.get(id);
        if (previous != null && previous == now) {
            return;
        }
        if (LAST_DUMP.size() > 512) {
            LAST_DUMP.clear();
        }
        LAST_DUMP.put(id, now);

        try {
            final String side = horse.level().isClientSide() ? "CLIENT" : "SERVER";
            final Entity cart = CartPullRegistry.cartOf(horse);
            // 索引说这匹马拉着 cart，而 cart 的字段说的拉车者是谁？
            // 两者不一致（drift=true）就说明索引与上游事实脱节了。
            final Entity cartFieldPuller = cart == null ? null : fieldPullerOf(cart);
            final Attribute maxAttr = MoreAttributesApi.equipLoadMax();
            final Attribute curAttr = MoreAttributesApi.equipLoadCurrent();
            final AttributeInstance maxInstance = maxAttr == null ? null : horse.getAttribute(maxAttr);
            final AttributeInstance curInstance = curAttr == null ? null : horse.getAttribute(curAttr);

            LOGGER.info("[terras_horsies/debug] side={} horse={}#{} dim={} cart={} cartFieldPuller={} drift={} | attrMax={} attrCur={} instMax={} instCur={} | curValue={} cap={} load={} passengers={} | moreAttributes={} astikorCarts={}",
                    side,
                    horse.getType(),
                    horse.getId(),
                    horse.level().dimension().location(),
                    cart == null ? "null" : cart.getType() + "#" + cart.getId(),
                    describeEntity(cartFieldPuller),
                    cart != null && cartFieldPuller != horse,
                    maxAttr != null,
                    curAttr != null,
                    maxInstance != null,
                    curInstance != null,
                    curInstance == null ? "-" : String.format("%.1f", curInstance.getValue()),
                    safeCap(horse),
                    safeLoad(horse),
                    horse.getPassengers().size(),
                    TfcIcysHorses.hasMoreAttributes(),
                    TfcIcysHorses.hasTfcAstikorCarts());
        } catch (final Throwable t) {
            // 诊断不允许影响主流程。
            LOGGER.warn("[terras_horsies/debug] 输出马匹负重快照失败", t);
        }
    }

    /**
     * 货物读数的<b>审计</b>记录：刻意<b>不受 {@code debugLoad} 开关控制</b>。
     *
     * <p>存在的理由：有些故障在日志里完全静默——读取既不抛异常、也不触发任何警告，
     * 只是「读出来是空」。要定位这种问题，就必须留下货物读数的变化轨迹。
     * 只在<b>某辆车的读数发生变化</b>时输出一行，所以一局游戏只会多出几条到几十条
     * INFO，而不是刷屏。
     *
     * <p>行里同时给出：解出的车是谁、有没有货箱、格数、直读读数、摘要读数、
     * 最终采用值，以及直读与摘要各自的逐格内容。据此足以区分：
     * <ul>
     *   <li>{@code inv=[...]} 有货 → 重量算法没问题；</li>
     *   <li>{@code inv=[]} 全空而 {@code cargo=[...]} 有货 → 读到的不是装货的那份数据；</li>
     *   <li>两者都空 → 这辆车当时确实没有货物（或负重挂到了别的车上）。</li>
     * </ul>
     */
    public static void cargoAudit(Entity cart, double direct, double summary, double resolved) {
        if (cart == null) {
            return;
        }
        try {
            final String line = describe(false, cart)
                    + String.format(" container=%s slots=%d direct=%.0f summary=%.0f resolved=%.0f",
                            cart instanceof net.minecraft.world.Container,
                            cart instanceof net.minecraft.world.Container c ? c.getContainerSize() : -1,
                            direct,
                            summary,
                            resolved)
                    + " inv=" + slotContents(cart, false)
                    + " cargo=" + slotContents(cart, true);
            final UUID id = cart.getUUID();
            if (line.equals(LAST_AUDIT.get(id))) {
                return;
            }
            if (LAST_AUDIT.size() > 512) {
                LAST_AUDIT.clear();
            }
            LAST_AUDIT.put(id, line);
            LOGGER.info("[terras_horsies/audit] {}", line);
        } catch (final Throwable t) {
            // 审计不允许影响主流程。
            LOGGER.warn("[terras_horsies/audit] 输出货物读数失败", t);
        }
    }

    /**
     * 输出一次马车货箱的读取结果。
     *
     * <p>只在<b>这辆车的读数发生变化</b>时输出一行，所以开着诊断往车里搬货，
     * 日志里就是一条干净的阶梯：装货 → 数字变大，清空 → 归零。
     *
     * <p>同时把「谁在拉这辆车」「货箱逐格装了什么」「摘要逐格装了什么」一起打出来。
     * 这三样是分辨下面几种完全不同故障的唯一依据：
     * <ul>
     *   <li>{@code inv=[...]} 有货而 {@code direct=0} —— 重量算法没读到东西；</li>
     *   <li>{@code inv=[]} 全空而 {@code cargo=[...]} 有货 —— 索引指向了另一辆车
     *       （或读到的实体不是玩家在装货的那辆）；</li>
     *   <li>{@code direct} 与 {@code summary} 的差距 —— 摘要与真值没有可靠换算关系，
     *       这也是它不能参与算重量的原因。</li>
     * </ul>
     */
    public static void cargoRead(Entity cart, double direct, double summary) {
        if (!enabled() || cart == null) {
            return;
        }
        try {
            final String line = describe(false, cart)
                    + String.format(" direct=%.0f summary=%.0f", direct, summary)
                    + " inv=" + slotContents(cart, false)
                    + " cargo=" + slotContents(cart, true);
            final UUID id = cart.getUUID();
            if (line.equals(LAST_CARGO.get(id))) {
                return;
            }
            if (LAST_CARGO.size() > 512) {
                LAST_CARGO.clear();
            }
            LAST_CARGO.put(id, line);
            LOGGER.info("[terras_horsies/debug] cargo {}", line);
        } catch (final Throwable t) {
            LOGGER.warn("[terras_horsies/debug] 输出货箱读取结果失败", t);
        }
    }

    /**
     * 一辆车的身份描述，含「谁在拉它」。
     *
     * <p>{@code puller=null} 就是「索引里没有这辆车」——那马的负重会只剩乘客份额，
     * 表现正是「太轻」。{@code puller} 与 {@code fieldPuller} 不一致则说明
     * 索引与上游字段脱节了（{@code shouldStopPulledTick} 会绕过 {@code setPulling}
     * 直接写字段，见 DEV_NOTES 14.14）。
     */
    private static String describe(boolean horseSide, Entity entity) {
        final StringBuilder b = new StringBuilder();
        b.append(horseSide ? "horse=" : "cart=").append(entity.getType()).append('#').append(entity.getId());
        final Entity puller = CartPullRegistry.pullerOf(entity);
        final Entity fieldPuller = fieldPullerOf(entity);
        b.append(" puller=").append(describeEntity(puller));
        b.append(" fieldPuller=").append(describeEntity(fieldPuller));
        return b.toString();
    }

    private static String describeEntity(Entity entity) {
        return entity == null ? "null" : entity.getType() + "#" + entity.getId();
    }

    /** 反射读上游 {@code pulling} 字段，用来和索引对照。 */
    private static Entity fieldPullerOf(Entity cart) {
        try {
            final java.lang.reflect.Field field = cart.getClass().getField("pulling");
            if (field.get(cart) instanceof Entity puller) {
                return puller;
            }
        } catch (final Throwable ignored) {
            // 动物车之类的没有这个字段。
        }
        return null;
    }

    /** 直读货箱（{@code inv}）或读摘要（{@code cargo}）的逐格内容，形如 {@code [slot:item xN]}。 */
    private static String slotContents(Entity cart, boolean summary) {
        try {
            final StringBuilder b = new StringBuilder("[");
            if (summary) {
                final java.lang.reflect.Method getCargo = cart.getClass().getMethod("getCargo");
                if (getCargo.invoke(cart) instanceof java.util.List<?> list) {
                    int i = 0;
                    for (final Object element : list) {
                        if (element instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
                            if (b.length() > 1) {
                                b.append(", ");
                            }
                            b.append(i).append(':').append(stack.getItem()).append(" x").append(stack.getCount());
                        }
                        i++;
                    }
                }
            } else if (cart instanceof net.minecraft.world.Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    final net.minecraft.world.item.ItemStack stack = container.getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        if (b.length() > 1) {
                            b.append(", ");
                        }
                        b.append(i).append(':').append(stack.getItem()).append(" x").append(stack.getCount());
                    }
                }
            } else {
                return "n/a";
            }
            return b.append(']').toString();
        } catch (final Throwable t) {
            return "<读失败:" + t.getClass().getSimpleName() + ">";
        }
    }

    /** 记录一次挂接/解挂：用来判断服务端到底有没有把索引写进去。 */
    public static void pullChange(Entity puller, Entity cart, boolean detach) {
        if (!enabled()) {
            return;
        }
        try {
            final String side = cart.level().isClientSide() ? "CLIENT" : "SERVER";
            LOGGER.info("[terras_horsies/debug] {} side={} puller={}#{} cart={}#{} dim={}",
                    detach ? "detach" : "attach",
                    side,
                    puller == null ? "null" : puller.getType(),
                    puller == null ? "-" : puller.getId(),
                    cart.getType(),
                    cart.getId(),
                    cart.level().dimension().location());
        } catch (final Throwable t) {
            LOGGER.warn("[terras_horsies/debug] 输出挂接状态失败", t);
        }
    }

    /**
     * 报告「马匹身上没有负重属性」。
     *
     * <p>这条不依赖 debug 开关：它只可能在兼容层注册失败时出现，
     * 每种实体类型只报一次，宁可日志里多一行也不能让它继续静默。
     */
    public static void missingAttribute(LivingEntity entity) {
        final String type = String.valueOf(entity.getType());
        if (!MISSING_REPORTED.add(type)) {
            return;
        }
        LOGGER.warn("[terras_horsies] {} 身上没有 More Attributes 的负重属性"
                        + "（equip_load_max / equip_load_current），它不会获得负重与超重减速。"
                        + "这通常意味着属性注册没生效——请把这一行连同启动日志一起反馈。",
                type);
    }

    private static String safeCap(LivingEntity entity) {
        try {
            return String.valueOf(LoadManager.capOf(entity));
        } catch (final Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    private static String safeLoad(LivingEntity entity) {
        try {
            return String.valueOf(LoadManager.loadOf(entity));
        } catch (final Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }
}
