package com.tfcicys.horses.load;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.TfcIcysHorses;

import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * 负重的计算与施加。
 *
 * <p>玩家与生物走两条不同的路，原因是 More Attributes 的行为不对称：
 * <ul>
 *   <li><b>玩家</b>：上游每 tick 在 {@code EquipLoad.calculateLoad} 里
 *       {@code setBaseValue(背包总重)}。基础值会被反复覆盖，
 *       所以外部追加的重量只能用 {@code AttributeModifier(ADDITION)}。</li>
 *   <li><b>生物</b>：上游从未碰过——它的 {@code registerPlayerAttribute} 只注册了玩家，
 *       {@code calculateLoad/rebuildModifier} 也都以 {@code Player} 为参数。
 *       马匹这边整条链路由我们接管，因此可以放心用 {@code setBaseValue}。</li>
 * </ul>
 */
public final class LoadManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 货箱读取失败每种只报告一次：拉车中的实体是每 5 tick 读一次的，不能刷屏。 */
    private static final AtomicBoolean CONTAINER_FAILURE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean HANDLER_FAILURE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean NBT_FALLBACK_FAILURE_LOGGED = new AtomicBoolean(false);

    /** 每辆车上次报告「直读不出内容」的时刻，按车 60 秒限流。 */
    private static final Map<UUID, Long> lastReadFailureReport = new ConcurrentHashMap<>();

    /** 已经报告过「这辆解出来的车根本没有货箱」的车，每辆只说一次。 */
    private static final Map<UUID, Boolean> noContainerReported = new ConcurrentHashMap<>();

    /** 沿 {@code drawn} 链最多走几节，防环。 */
    private static final int MAX_TRAIN_LENGTH = 8;

    /**
     * 读坐骑「自己带着的东西」时最多探几格。
     *
     * <p>用 {@code Entity.getSlot(i)} 逐格试探、遇到 {@code SlotAccess.NULL} 停，
     * 所以这个数只是个安全上限（原版驴 5 格、骡 5 格、羊驼 3~15 格、
     * TFC 有箱子马按配置最多十几格）。
     */
    private static final int MAX_CARRIED_SLOTS = 32;

    /** 每辆车最近一次可信的货物重量：{重量, 记录时的 gameTime}。 */
    private static final Map<UUID, double[]> CARGO_MEMORY = new ConcurrentHashMap<>();

    /**
     * 同一份记忆的「按实体 id」索引。
     *
     * <p>为什么要两份索引：实测的抖动是**同一辆车相隔 25 ms 一次读到真值、一次读到空**
     * （连存档 NBT 也读不到），而这有可能来自「同一辆车的两个实体实例」——
     * 那种情况下 UUID 未必相同，但审计日志里两次的**实体 id 都是 13**。
     * 按 id 再存一份，两种成因都能覆盖。类型必须一致才认，避免 id 回收后串味。
     */
    private static final Map<Integer, double[]> CARGO_MEMORY_BY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, String> CARGO_ID_TYPE = new ConcurrentHashMap<>();

    /** 可信重量的有效期（tick）。100 tick = 5 秒，且摘要一旦变空就立即作废。 */
    private static final int CARGO_MEMORY_TICKS = 100;

    /** 缓存反射找到的 {@code drawn} 字段；找不到的类单独记下来，避免反复抛异常。 */
    private static final Map<Class<?>, java.lang.reflect.Field> DRAWN_FIELDS = new ConcurrentHashMap<>();
    private static final java.util.Set<Class<?>> NO_DRAWN_FIELD = ConcurrentHashMap.newKeySet();

    /** 每辆车「世界里的那个对象」。见 {@link #pickWorldCart}：记住身份而不是数值。 */
    private static final Map<UUID, Entity> CARGO_WORLD_CART = new ConcurrentHashMap<>();

    /** 马匹负重上限的修饰符。 */
    private static final UUID ANIMAL_SPEED_UUID = UUID.fromString("3c1a7e90-5b24-4d18-9f60-2ab7c0d4e815");
    private static final UUID ANIMAL_JUMP_UUID = UUID.fromString("7d2b4f61-8a35-4c92-b1e7-50f3a9c6d208");

    /** 玩家拉车时，追加到 equip_load_current 上的马车重量。 */
    private static final UUID PLAYER_CART_LOAD_UUID = UUID.fromString("b5e8c312-4f76-4a0d-8e29-1c4d7b6f9a03");

    private LoadManager() {}

    // ══════════════════════════════════════════════════════════════
    //  上限
    // ══════════════════════════════════════════════════════════════

    /**
     * 生物（含马匹）的负重上限，已计入衰老。
     *
     * <p>衰老取 TFC 的 {@code uses / usesToElderly}（Jade 显示的「衰老值」就是它）。
     * 线性插值到配置的 {@code agedLoadFactor}：衰老 0% 时是原值，
     * 100% 时降到 70%。
     */
    public static int capOf(LivingEntity entity) {
        final int base = HorseCategory.capFor(entity);
        return Math.max(0, (int) Math.round(base * agingFactor(entity)));
    }

    /** 衰老折算出的上限系数：1.0（年轻）→ agedLoadFactor（满衰老）。 */
    private static double agingFactor(LivingEntity entity) {
        final double floor = TFCICYSConfig.COMMON.agedLoadFactor.get();
        if (floor >= 1.0D) {
            return 1.0D;
        }
        final double wear = wearOf(entity);
        return 1.0D - (1.0D - floor) * wear;
    }

    /** TFC 的衰老比例 0..1。不是 TFC 动物时返回 0。 */
    public static double wearOf(LivingEntity entity) {
        if (!(entity instanceof TFCAnimalProperties animal)) {
            return 0.0D;
        }
        final int toElderly = animal.getUsesToElderly();
        if (toElderly <= 0) {
            return 0.0D;
        }
        return Mth.clamp((float) animal.getUses() / toElderly, 0.0F, 1.0F);
    }

    // ══════════════════════════════════════════════════════════════
    //  当前负重
    // ══════════════════════════════════════════════════════════════

    /**
     * 生物此刻承受的总负重 = 自己带着的东西 + 全部乘客的自身体重与随身负重 + 它拉着的马车。
     */
    public static int loadOf(LivingEntity entity) {
        double total = 0.0D;
        total += carriedLoadOf(entity);
        total += passengerLoadOf(entity);
        total += cartLoadFor(entity);
        return Math.max(0, (int) Math.round(total));
    }

    /**
     * 坐骑**自己带着**的东西的重量。
     *
     * <p>用户需求：TFC 的驴和骡可以用箱子右键装上箱子装东西，
     * 箱子里的货物也应该压在它们身上。
     *
     * <p>读法用原版 {@code Entity.getSlot(int)}，**不引用 TFC 的任何类型**：
     * TFC 的 {@code TFCChestedHorse} 继承原版 {@code AbstractChestedHorse}，
     * 箱子内容正是通过 {@code m_141942_}（{@code getSlot}）暴露出来的
     * （它内部返回一个 {@code SlotAccess}）。越界的槽位上游返回
     * {@code SlotAccess.NULL}，遇到就停——这样不需要知道箱子有几格。
     *
     * <p>玩家不走这条：玩家背包由 More Attributes 自己按 {@code equip_load_current} 算，
     * 这里再算一遍就是同一批物品罚两次。
     */
    private static double carriedLoadOf(LivingEntity entity) {
        if (entity instanceof Player) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (int slot = 0; slot < MAX_CARRIED_SLOTS; slot++) {
            final SlotAccess access = entity.getSlot(slot);
            if (access == null || access == SlotAccess.NULL) {
                break;
            }
            sum += MoreAttributesApi.itemWeight(access.get());
        }
        return sum;
    }

    /**
     * 乘客给坐骑的负重。
     *
     * <p>玩家固定 350（挽马 300）；挽马能坐两人，所以两人正好是两倍。
     * 非玩家乘客（例如另一个模组的生物）按玩家标准计。
     * 再叠加乘客自身的负重——负重 560 的玩家骑竞速马，
     * 这匹马就吃到 560 + 350 = 910。
     */
    private static double passengerLoadOf(LivingEntity mount) {
        final TFCICYSConfig.Common c = TFCICYSConfig.COMMON;
        final boolean draft = HorseCategory.of(mount).isDraft();
        final int selfWeight = draft ? c.draftRiderLoad.get() : c.riderLoad.get();
        final boolean inheritRiderBackpack = c.inheritRiderBackpackLoad.get();

        double sum = 0.0D;
        for (final Entity passenger : mount.getPassengers()) {
            sum += selfWeight;
            if (inheritRiderBackpack && passenger instanceof Player player) {
                sum += MoreAttributesApi.currentLoadOf(player);
            }
        }
        return sum;
    }

    // ══════════════════════════════════════════════════════════════
    //  马车
    // ══════════════════════════════════════════════════════════════

    /**
     * 拉车者继承到的马车重量。
     *
     * <p>构成：固定自重（默认 512，空车也算，不参与减免）+ 货物重量 × 系数。
     * 货物用 More Attributes 的算法逐格计算，按需求减轻 40%（系数 0.6）。
     * 挽马拉车时整车再降到 40%——这一档对玩家无效。
     */
    public static double cartLoadFor(Entity puller) {
        if (!TfcIcysHorses.hasTfcAstikorCarts()) {
            return 0.0D;
        }
        Entity head = CartPullRegistry.cartOf(puller);
        if (head == null) {
            return 0.0D;
        }
        // 同一辆车会有两个对象：世界里的那个（能被右键、被渲染、被存档），
        // 以及 AstikorWorld 留下的停放副本（同 UUID，但不在世界实体索引里，
        // 却被 AstikorWorld.tick() 继续 tick 所以一直活着，并轮流抢索引槽）。
        // 实测：世界里的那个 direct=12544（装着原木），副本 direct=0。
        head = pickWorldCart(head);
        final double bestCargo = cargoWeightOf(head);

        final TFCICYSConfig.Common c = TFCICYSConfig.COMMON;
        final boolean draftCart = puller instanceof LivingEntity living && HorseCategory.of(living).isDraft();

        // 自重不减免：无论挽马与否都是全额。
        final double base = c.cartBaseLoad.get();
        final double cargoFactor = draftCart ? c.draftCartFactor.get() : c.cartCargoFactor.get();

        // 马后面可能挂着不止一辆车：TFC 有「牵引车」这种自己再拖一节的车，
        // 那种情况下马直接拉的是牵引车（自重 512、没有货箱），
        // 真正的货物在它拖着的下一节里。整列的总重都该由拉车者承担，
        // 所以沿 drawn 链一路累加。没有链条时循环只跑一次，行为与从前完全一致。
        //
        // 首节用的是上面已经选好的读数（bestCargo），不再重复读一次——
        // 重复读有可能又抽到那份空副本。
        double total = base + bestCargo * cargoFactor;
        Entity cart = drawnByOf(head);
        for (int depth = 1; cart != null && depth < MAX_TRAIN_LENGTH; depth++) {
            // 同 UUID 的「下一节」不是真的第二节车，而是同一辆车的另一个对象（停放副本），
            // 计进去会把自重算两遍。真第二节车的 UUID 必然不同。
            if (cart.getUUID().equals(head.getUUID())) {
                break;
            }
            total += base + cargoWeightOf(cart) * cargoFactor;
            cart = drawnByOf(cart);
        }
        return total;
    }

    /**
     * 这辆车拖着的下一节（上游 {@code AbstractDrawnEntity.drawn} 字段）。
     *
     * <p>用反射而不是直接引用类型：AstikorCarts 是可选依赖，
     * 它的类名不能出现在我们的常量池里（见 {@code MoreAttributesApi} 的说明）。
     * 字段是 {@code protected}，所以沿继承链找 {@code getDeclaredField}；
     * 找不到（不是可牵引的车）就缓存下来，避免每次 tick 都抛一次异常。
     */
    private static Entity drawnByOf(Entity cart) {
        try {
            final Class<?> type = cart.getClass();
            java.lang.reflect.Field field = DRAWN_FIELDS.get(type);
            if (field == null && !NO_DRAWN_FIELD.contains(type)) {
                Class<?> cursor = type;
                while (cursor != null && field == null) {
                    try {
                        field = cursor.getDeclaredField("drawn");
                    } catch (final NoSuchFieldException next) {
                        cursor = cursor.getSuperclass();
                    }
                }
                if (field == null) {
                    NO_DRAWN_FIELD.add(type);
                    return null;
                }
                field.setAccessible(true);
                DRAWN_FIELDS.put(type, field);
            }
            return field == null ? null : (field.get(cart) instanceof Entity drawn ? drawn : null);
        } catch (final Throwable t) {
            return null;
        }
    }

    /** 这辆车的货箱格数；不是 {@code Container} 时返回 -1。 */
    private static int containerSlotsOf(Entity cart) {
        return cart instanceof net.minecraft.world.Container container ? container.getContainerSize() : -1;
    }

    /**
     * 车厢内货物的 More Attributes 重量合计。
     *
     * <p>取值顺序：
     * <ol>
     *   <li><b>直读货箱</b>（主路径）。补给车实现了 {@code Container}，服务端读到的就是
     *       实体自己的 {@code ItemStackHandler}；玩家打开的界面写的也是同一个对象
     *       （{@code SupplyCartContainer} 的构造直接引用
     *       {@code AbstractDrawnInventoryEntity.inventory}），所以这条实时、无水份。
     *       没有实现 {@code Container} 的车再退到 {@code ITEM_HANDLER} 能力。</li>
     *   <li><b>存档 NBT 兜底</b>。直读为 0、而渲染摘要却报告车厢里有东西时，
     *       说明是读取路径失效而不是车空了，于是改读实体存档的 {@code "Items"}
     *       （上游把货箱原样写在那里），并打一条不需要开关的警告。</li>
     * </ol>
     *
     * <p><b>绝不能拿 {@code getCargo()} 算重量</b>，即使用来兜底也不行。它是上游给渲染用的
     * 「摘要」：{@code onContentsChanged} 先按物品聚合总数，按数量降序、方块物品靠后排序，
     * 只保留前 {@code CARGO.size()} 种，再把每种铺成
     * {@code Math.max(1, (count + k/2) / k)} 格（{@code k = getSlots() / CARGO.size()}），
     * 第 i 格写 {@code min(原有堆叠, count / i)}。也就是说它<b>既可能偏大也可能偏小、
     * 还会整类丢弃物品</b>，没有任何可靠的换算关系。它只用于诊断输出。
     *
     * <p>这里也<b>刻意不保存任何跨 tick 的状态</b>。旧版本维护过一个
     * 「最后一次成功读到的值」，会把负重冻在上一次成功读取的那一刻，
     * 而且全模组共用一个静态值、多辆车会互相串味。
     */
    public static double cargoWeightOf(Entity cart) {
        final boolean server = !cart.level().isClientSide();
        final double direct = readCargoDirect(cart);
        if (direct > 0.0D) {
            rememberCargo(cart, direct);
            final double summary = syncedSummaryWeightForDiagnosisOnly(cart);
            LoadDebug.cargoRead(cart, direct, summary);
            LoadDebug.cargoAudit(cart, direct, summary, direct, "direct");
            return direct;
        }

        // 直读为 0。三种可能必须分开：车真的空了、读取路径失效、或索引指错了车。
        // 摘要（getCargo）在「车里有没有货」这件事上是可靠的：它由货箱内容变化时
        // 整体重建（TFCSupplyCartEntity$1.onContentsChanged），清空必然立刻变空。
        // 它只是<b>不能给出数量</b>（只保留前 N 种、数量还按 k 摊开），所以只当判据用。
        final double summary = syncedSummaryWeightForDiagnosisOnly(cart);
        double resolved = 0.0D;
        String source = "empty";
        if (summary > 0.0D && server) {
            // 兜底一：实体存档 NBT 的 "Items"。无损，且不经过容器/能力这两条路。
            final double fromNbt = cargoWeightFromSavedNbt(cart);
            if (fromNbt > 0.0D) {
                reportReadFailure(cart, summary, fromNbt);
                rememberCargo(cart, fromNbt);
                resolved = fromNbt;
                source = "nbt";
            } else {
                // 兜底二：两条路都读到空、但摘要说车里有货 —— 这就是
                // 「读数不稳定地返回空」的现场（实测同一辆车相隔 25 ms
                // 一次 12560 一次 0）。此时沿用该车最近一次可信读数，
                // 并且只在摘要仍然说「有货」期间有效。
                final double remembered = recallCargo(cart);
                if (remembered > 0.0D) {
                    resolved = remembered;
                    source = "memory";
                }
            }
        }
        if (summary <= 0.0D) {
            // 车真的空了：立刻清掉记忆，绝不让它像旧实现那样永远冻住。
            forgetCargo(cart);
        }

        // 兜底也没货：如果这辆车压根没有货箱（不是 Container，或格数为 0），
        // 那多半是索引指向的车不对——最典型的是马直接拉着一辆「牵引车」，
        // 真正的货箱挂在它拖着的那一节上。每辆车只报一次，不会刷屏。
        if (resolved == 0.0D && server
                && containerSlotsOf(cart) <= 0
                && noContainerReported.putIfAbsent(cart.getUUID(), Boolean.TRUE) == null) {
            LOGGER.warn("[terras_horsies] 为拉车者解出的马车 {}#{} 没有货箱（Container={}）。"
                            + "若你实际装货的是另一节车（例如中间有牵引车），请把这一行连同马车编组一起反馈——"
                            + "这代表负重挂到了错误的车上。",
                    cart.getType(), cart.getId(), containerSlotsOf(cart) >= 0);
        }
        LoadDebug.cargoRead(cart, direct, summary);
        LoadDebug.cargoAudit(cart, direct, summary, resolved, source);
        return resolved;
    }

    /**
     * 记住这辆车最近一次可信的货物重量。
     *
     * <p>为什么需要它：实测直读会「忽真忽空」——同一辆车、同一头马、相隔 25 ms，
     * 一次读到 12560（785 根原木 × 16，完全正确），下一次整箱读成空，
     * 连存档 NBT 里也读不到东西。负重因此一直在真值与裸车重之间抖，
     * 玩家看到的就是「马车上的货完全不算」。
     *
     * <p>与旧实现（全局静态的 {@code lastKnownCargo}）的三点关键区别：
     * <ol>
     *   <li><b>按车保存</b>：多辆车不会互相串味；</li>
     *   <li><b>清空立刻失效</b>：摘要变空说明车真的空了，记忆当场丢弃；</li>
     *   <li><b>会刷新</b>：只要再有任意一次读成功，值立刻更新成新的。</li>
     * </ol>
     */
    private static void rememberCargo(Entity cart, double weight) {
        final double[] entry = new double[] {weight, cart.level().getGameTime()};
        CARGO_MEMORY.put(cart.getUUID(), entry);
        CARGO_MEMORY_BY_ID.put(cart.getId(), entry);
        CARGO_ID_TYPE.put(cart.getId(), String.valueOf(cart.getType()));
    }

    /** 取回最近一次可信读数；超过有效期、或换成了别的车就当作没有。 */
    private static double recallCargo(Entity cart) {
        double[] remembered = CARGO_MEMORY.get(cart.getUUID());
        if (remembered == null && String.valueOf(cart.getType()).equals(CARGO_ID_TYPE.get(cart.getId()))) {
            remembered = CARGO_MEMORY_BY_ID.get(cart.getId());
        }
        if (remembered == null) {
            return 0.0D;
        }
        if (cart.level().getGameTime() - (long) remembered[1] > CARGO_MEMORY_TICKS) {
            forgetCargo(cart);
            return 0.0D;
        }
        return remembered[0];
    }

    private static void forgetCargo(Entity cart) {
        CARGO_MEMORY.remove(cart.getUUID());
        if (String.valueOf(cart.getType()).equals(CARGO_ID_TYPE.get(cart.getId()))) {
            CARGO_MEMORY_BY_ID.remove(cart.getId());
            CARGO_ID_TYPE.remove(cart.getId());
        }
    }

    /**
     * 从同一辆车的两个对象里挑出**世界里的那一个**。
     *
     * <p>实测证据（用户服务端日志，cartId=104）：
     * <pre>
     * inst=52140f0d  direct=12544  inLevel=true     ← 装着原木；右键、渲染、存档用的都是它
     * inst=49fbddd8  direct=0      inLevel=false    ← 上游留下的停放副本
     * </pre>
     * 副本不在世界的实体索引里，却被 {@code AstikorWorld.tick()} 继续 tick
     * （{@code SimpleAstikorWorld} 内部是 {@code Int2ObjectMap}，按拉车者 id 记账），
     * 所以它一直活着、一直重新写进我们的索引，读数便在真值与空之间抖。
     *
     * <p>判定办法：<b>以世界实体索引为准</b>——{@code ServerLevel.getEntity(UUID)} 返回的
     * 必然是世界里那个（副本查不到）。但这辆车会在「停放/恢复」之间抖动，某些时刻
     * 世界索引里什么都查不到，所以查不到时改用<b>记住的那个对象</b>。
     *
     * <p>记住的是<b>身份</b>而不是数值：玩家把货卸空后读到的仍是这辆车 ⇒ 立刻归零，
     * 不会像「缓存重量」那样冻在旧值上。缓存对象被移除、或类型对不上就放弃它。
     */
    private static Entity pickWorldCart(Entity registryCart) {
        final UUID id = registryCart.getUUID();
        if (registryCart.level() instanceof net.minecraft.server.level.ServerLevel server) {
            final Entity found = server.getEntity(id);
            if (found != null) {
                CARGO_WORLD_CART.put(id, found);
                LoadDebug.cartDuplicate(found, found == registryCart ? null : registryCart, "世界索引");
                return found;
            }
        }
        final Entity remembered = CARGO_WORLD_CART.get(id);
        if (remembered != null && !remembered.isRemoved()
                && String.valueOf(remembered.getType()).equals(String.valueOf(registryCart.getType()))) {
            LoadDebug.cartDuplicate(remembered, registryCart, "记忆（世界索引此刻查不到）");
            return remembered;
        }
        CARGO_WORLD_CART.remove(id);
        LoadDebug.cartDuplicate(registryCart, null, "无世界对象可用，先用索引里的");
        return registryCart;
    }

    /**
     * 从实体存档 NBT 的 {@code "Items"} 里取货物重量。
     *
     * <p>这是<b>兜底</b>路径，正常情况下永远不会执行：只有在直读返回 0、
     * 而渲染摘要却报告车厢里有东西（说明是读取出了问题，不是车空了）时才走。
     * {@code saveWithoutId} 会走一遍 {@code addAdditionalSaveData}，有分配开销，
     * 所以不能放在主路径上。
     *
     * <p>解析用纯原版 API（{@code ItemStack.of}），不引用 AstikorCarts 的任何类型，
     * 这样可选依赖缺席时也不会有类加载问题。
     */
    private static double cargoWeightFromSavedNbt(Entity cart) {
        try {
            final CompoundTag saved = cart.saveWithoutId(new CompoundTag());
            final ListTag items = saved.getList("Items", 10); // 10 = CompoundTag
            double sum = 0.0D;
            for (int i = 0; i < items.size(); i++) {
                final ItemStack stack = ItemStack.of(items.getCompound(i));
                if (!stack.isEmpty()) {
                    sum += MoreAttributesApi.itemWeight(stack);
                }
            }
            return sum;
        } catch (final Throwable t) {
            if (NBT_FALLBACK_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("[terras_horsies] 从存档 NBT 读取马车 {} 货物失败，后续同类失败不再重复报告", cart, t);
            }
            return 0.0D;
        }
    }

    /**
     * 报告一次「货箱直读不出内容」。
     *
     * <p><b>这条日志不需要打开任何诊断开关</b>：它代表一个真实的异常状态
     * （渲染摘要说有货、直读却说没有），正是「马匹负重太轻／装货不生效」的现场证据，
     * 所以按车、每 60 秒最多报一次，避免刷屏但绝不静默。
     */
    private static void reportReadFailure(Entity cart, double summary, double fromNbt) {
        final long now = cart.level().getGameTime();
        final Long previous = lastReadFailureReport.get(cart.getUUID());
        if (previous != null && now - previous < 1200L) {
            return;
        }
        if (lastReadFailureReport.size() > 256) {
            lastReadFailureReport.clear();
        }
        lastReadFailureReport.put(cart.getUUID(), now);
        LOGGER.warn("[terras_horsies] 马车 {}#{} 的货箱直读返回 0，但渲染摘要报告有货——已改用存档 NBT 兜底（重量约 {}，摘要读数 {}）。"
                        + "这通常意味着货箱读取路径在该环境下失效，请把这条日志连同上下文一起反馈。",
                cart.getType(), cart.getId(), (long) fromNbt, (long) summary);
    }

    /** 直接从容器 / 能力读，两条都试，取较大的那个。 */
    private static double readCargoDirect(Entity cart) {
        double best = 0.0D;
        try {
            // 补给车实现了 Container；动物车没有货箱，会走到下面的能力查询并返回 0。
            if (cart instanceof net.minecraft.world.Container container) {
                best = Math.max(best, sumContainer(container));
            }
        } catch (final Throwable t) {
            // 读容器失败不应该让整辆车的牵引逻辑崩掉，但必须留下痕迹——
            // 静默吞异常会让「货物重量算不出来」变成一个无从排查的黑洞。
            // 拉车中的实体是每 5 tick 读一次的，所以每种失败只报一次。
            if (CONTAINER_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("[terras_horsies] 通过 Container 读取马车 {} 货箱失败，后续同类失败不再重复报告", cart, t);
            }
        }
        try {
            final var handlerOpt = cart.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (handlerOpt.isPresent()) {
                final IItemHandler handler = handlerOpt.orElse(null);
                if (handler != null) {
                    best = Math.max(best, sumHandler(handler));
                }
            }
        } catch (final Throwable t) {
            if (HANDLER_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("[terras_horsies] 通过 ITEM_HANDLER 读取马车 {} 货箱失败，后续同类失败不再重复报告", cart, t);
            }
        }
        return best;
    }

    private static double sumContainer(net.minecraft.world.Container container) {
        double sum = 0.0D;
        for (int i = 0; i < container.getContainerSize(); i++) {
            sum += MoreAttributesApi.itemWeight(container.getItem(i));
        }
        return sum;
    }

    private static double sumHandler(IItemHandler handler) {
        double sum = 0.0D;
        for (int i = 0; i < handler.getSlots(); i++) {
            sum += MoreAttributesApi.itemWeight(handler.getStackInSlot(i));
        }
        return sum;
    }

    /**
     * 同步摘要的负重。<b>只用于诊断输出，绝不参与负重计算。</b>
     *
     * <p>走反射是因为 {@code TFCSupplyCartEntity} 属于可选依赖，不能出现在签名或
     * 常量池引用里，否则 AstikorCarts 缺席时会 {@code NoClassDefFoundError}。
     *
     * <p>数值本身是被上游按 {@code count / k} 截断过的（见 {@link #cargoWeightOf}），
     * 所以它比真值小一个数量级；把它和直读一起打出来，是为了让
     * 「读到的是不是同一份数据」一眼可辨。
     */
    private static double syncedSummaryWeightForDiagnosisOnly(Entity cart) {
        try {
            final java.lang.reflect.Method getCargo = cart.getClass().getMethod("getCargo");
            final Object cargo = getCargo.invoke(cart);
            if (cargo instanceof java.util.List<?> list) {
                double sum = 0.0D;
                for (final Object element : list) {
                    if (element instanceof ItemStack stack && !stack.isEmpty()) {
                        sum += MoreAttributesApi.itemWeight(stack);
                    }
                }
                return sum;
            }
        } catch (final Throwable ignored) {
            // 不是补给车（没有 getCargo），或反射被拒：这一路就没有数值可报。
        }
        return 0.0D;
    }

    // ══════════════════════════════════════════════════════════════
    //  施加
    // ══════════════════════════════════════════════════════════════

    /**
     * 刷新一只生物的负重属性并施加超重惩罚。每 20 tick 调一次即可——
     * 上限与负重都是渐变量，没必要每 tick 重算。
     */
    public static void updateAnimal(LivingEntity entity) {
        // 同 updatePlayer：客户端数据不全，写进去只会与服务端打架。
        if (entity.level().isClientSide()) {
            return;
        }
        final Attribute maxAttr = MoreAttributesApi.equipLoadMax();
        final Attribute curAttr = MoreAttributesApi.equipLoadCurrent();
        if (maxAttr == null || curAttr == null) {
            // 上游缺席：整条负重链路都不该工作，这是预期内的静默退化。
            return;
        }
        final AttributeInstance maxInstance = entity.getAttribute(maxAttr);
        final AttributeInstance curInstance = entity.getAttribute(curAttr);
        if (maxInstance == null || curInstance == null) {
            // 上游在场、属性却不在这个实体上——只可能是我们的注册没生效。
            // 这一条绝不能再静默：它正是「玩家正常、马匹负重怎么都不涨」最可能的成因，
            // 而玩家不需要我们的注册（More Attributes 自己注册了玩家），所以只会马匹中招。
            if (entity instanceof AbstractHorse) {
                LoadDebug.missingAttribute(entity);
            }
            return;
        }

        // 生物这边没有竞争者，可以直接写基础值。
        //
        // 只在数值真的变了才写：拉车的生物现在每 5 tick 刷新一次，
        // 无条件 setBaseValue 会把属性标记为脏、每 5 tick 发一次同步包，
        // 而绝大多数时候负重根本没变。
        final double cap = capOf(entity);
        final double load = loadOf(entity);
        final boolean changed = maxInstance.getBaseValue() != cap || curInstance.getBaseValue() != load;
        if (changed) {
            maxInstance.setBaseValue(cap);
            curInstance.setBaseValue(load);
        }

        // 修饰符缺失时也要重挂（例如刚上完马、或实体刚被创建）。
        final AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (changed || speed == null || speed.getModifier(ANIMAL_SPEED_UUID) == null) {
            applyPenalty(entity, curInstance.getValue(), maxInstance.getValue());
        }
    }

    /**
     * 玩家侧的负重追加。
     *
     * <p>玩家背包里的东西由 More Attributes 自己算，这里只补它看不见的那一份：
     * 玩家亲自拉车时的马车重量。因为它每 tick 会 {@code setBaseValue} 覆盖基础值，
     * 追加量必须走 ADDITION 修饰符，否则下一 tick 就被抹掉。
     *
     * <p><b>这里刻意不调 {@link #applyPenalty}。</b>
     * 上游的 {@code EquipLoad.rebuildModifier} 已经读着 {@code EquipLoadCurrent}
     * 给玩家挂了一份移速修饰符，再挂一份就是同一份超重被罚两次。
     * 我们只要把重量写进属性，减速自然由它按同一个公式产生。
     *
     * <p>骑乘方向不需要在这里处理——那份负担是记在坐骑身上的，
     * 见 {@link #passengerLoadOf}。
     */
    public static void updatePlayer(Player player) {
        if (!TfcIcysHorses.hasMoreAttributes()) {
            return;
        }
        // 客户端绝不可参与：马车货箱不同步到客户端，这里算出来的货物重量恒为 0，
        // 一旦写入就会把服务端同步来的正确值顶掉。见 LoadEvents#refreshPuller。
        if (player.level().isClientSide()) {
            return;
        }
        final Attribute curAttr = MoreAttributesApi.equipLoadCurrent();
        if (curAttr == null) {
            return;
        }
        final AttributeInstance instance = player.getAttribute(curAttr);
        if (instance == null) {
            return;
        }

        final double cartLoad = cartLoadFor(player);
        setAddition(instance, PLAYER_CART_LOAD_UUID, "tfcicys:cart_load", cartLoad);
    }

    /**
     * 把某个 UUID 的 ADDITION 追加量设成 {@code amount}（≤0 表示不追加）。
     *
     * <p>只有在数值真的变化时才动修饰符：{@code addTransientModifier} /
     * {@code removeModifier} 都会把属性标记为脏并触发一次同步包，
     * 而玩家侧是每 10 tick 跑一次的，绝大多数时候追加量没变。
     */
    private static void setAddition(AttributeInstance instance, UUID id, String name, double amount) {
        final AttributeModifier existing = instance.getModifier(id);
        if (amount <= 0.0D) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.getAmount() == amount) {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }

    /**
     * 与 More Attributes 完全一致的超重惩罚。
     *
     * <p>移速是<b>连续线性</b>而非分档常量：{@code mult = 1 - (r-1)/2}，
     * 其中 {@code r = 当前负重 / 上限}。
     * r=1.0 → 不减速，r=1.5 → ×0.75，r=2.0 → ×0.5，r=3.0 → ×0（钳在 0，不会更差）。
     *
     * <p>跳跃在 2.5r~3r 之间减半，达到 3r 归零。
     */
    public static void applyPenalty(LivingEntity entity, double currentLoad, double maxLoad) {
        final AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        final AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed == null) {
            return;
        }

        speed.removeModifier(ANIMAL_SPEED_UUID);
        if (jump != null) {
            jump.removeModifier(ANIMAL_JUMP_UUID);
        }

        double speedMultiplier = 1.0D;
        double jumpMultiplier = 1.0D;

        if (maxLoad <= 0.0D) {
            // 上限为 0 却背着东西：彻底动不了。空身时保持正常。
            if (currentLoad > 0.0D) {
                speedMultiplier = 0.0D;
                jumpMultiplier = 0.0D;
            }
        } else if (currentLoad > maxLoad) {
            speedMultiplier = 1.0D - (currentLoad - maxLoad) / maxLoad / 2.0D;
            if (speedMultiplier < 0.0D) {
                speedMultiplier = 0.0D;
            }
            if (currentLoad >= maxLoad * 3.0D) {
                jumpMultiplier = 0.0D;
            } else if (currentLoad > maxLoad * 2.5D) {
                jumpMultiplier = 0.5D;
            }
        }

        speed.addTransientModifier(new AttributeModifier(
                ANIMAL_SPEED_UUID, "tfcicys:load_speed", speedMultiplier - 1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (jump != null) {
            jump.addTransientModifier(new AttributeModifier(
                    ANIMAL_JUMP_UUID, "tfcicys:load_jump", jumpMultiplier - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}
