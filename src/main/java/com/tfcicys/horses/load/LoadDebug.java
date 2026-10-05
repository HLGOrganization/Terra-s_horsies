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
            final Attribute maxAttr = MoreAttributesApi.equipLoadMax();
            final Attribute curAttr = MoreAttributesApi.equipLoadCurrent();
            final AttributeInstance maxInstance = maxAttr == null ? null : horse.getAttribute(maxAttr);
            final AttributeInstance curInstance = curAttr == null ? null : horse.getAttribute(curAttr);

            LOGGER.info("[terras_horsies/debug] side={} horse={}#{} dim={} cart={} | attrMax={} attrCur={} instMax={} instCur={} | curValue={} cap={} load={} passengers={} | moreAttributes={} astikorCarts={}",
                    side,
                    horse.getType(),
                    horse.getId(),
                    horse.level().dimension().location(),
                    cart == null ? "null" : cart.getType() + "#" + cart.getId(),
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
