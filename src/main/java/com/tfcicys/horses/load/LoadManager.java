package com.tfcicys.horses.load;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.TfcIcysHorses;

import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
     * 生物此刻承受的总负重 = 全部乘客的自身体重与随身负重 + 它拉着的马车。
     */
    public static int loadOf(LivingEntity entity) {
        double total = 0.0D;
        total += passengerLoadOf(entity);
        total += cartLoadFor(entity);
        return Math.max(0, (int) Math.round(total));
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
        final Entity cart = CartPullRegistry.cartOf(puller);
        if (cart == null) {
            return 0.0D;
        }

        final TFCICYSConfig.Common c = TFCICYSConfig.COMMON;
        final boolean draftCart = puller instanceof LivingEntity living && HorseCategory.of(living).isDraft();

        // 自重不减免：无论挽马与否都是全额。
        final double base = c.cartBaseLoad.get();
        final double cargo = cargoWeightOf(cart);
        final double cargoFactor = draftCart ? c.draftCartFactor.get() : c.cartCargoFactor.get();
        return base + cargo * cargoFactor;
    }

    /**
     * 车厢内货物的 More Attributes 重量合计。
     *
     * <p><b>多路来源取最大值</b>：任何一路读到内容，都不会被另一路读到 0 压掉。
     * 三路来源是：
     * <ol>
     *   <li>{@code Container} 直读 —— 补给车实现了这个接口，服务端读到的就是
     *       实体自己的 {@code ItemStackHandler}。玩家打开的界面写的也是同一个对象
     *       （{@code SupplyCartContainer} 的构造直接引用
     *       {@code AbstractDrawnInventoryEntity.inventory}），所以这条是实时的。</li>
     *   <li>{@code ITEM_HANDLER} 能力 —— 没有实现 {@code Container} 的车走这条。</li>
     *   <li>同步摘要 {@code getCargo()} —— 上游在货箱内容变化时刷新的
     *       {@code EntityDataAccessor} 列表，客户端也拿得到。</li>
     * </ol>
     *
     * <p>这里<b>刻意不保存任何跨 tick 的状态</b>。旧版本维护过一个
     * 「最后一次成功读到的值」，读到 0 且摘要非空时就沿用它。那个兜底有两个后果：
     * <ul>
     *   <li>负重会<b>冻在上一次成功读取的那一刻</b>：往车里加东西，马的负重纹丝不动；
     *       把车清空，反而立刻变得正确（因为空车走的是另一条分支）。</li>
     *   <li>它是<b>整个模组共用一个静态值</b>，同时存在两辆以上马车时会互相串味。</li>
     * </ul>
     * 现在改为按来源取最大值：空车就是 0，装货就是当前值，不需要猜。
     */
    public static double cargoWeightOf(Entity cart) {
        final double direct = readCargoDirect(cart);
        final double synced = syncedCargoWeight(cart);
        final double result = Math.max(direct, synced);
        LoadDebug.cargoRead(cart, direct, synced, result);
        return result;
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
     * 同步摘要里的货物重量。
     *
     * <p>走反射是因为 {@code TFCSupplyCartEntity} 属于可选依赖，不能出现在签名或
     * 常量池引用里，否则 AstikorCarts 缺席时会 {@code NoClassDefFoundError}。
     *
     * <p>摘要的数量会被上游 {@code setCount(min(maxStackSize, count/k))} 截断，
     * 所以它可能<b>低估</b>重量——因此只作为「直读为 0 时的补充来源」，
     * 与直读取最大值，绝不会用它去覆盖一个更大的直读结果。
     */
    private static double syncedCargoWeight(Entity cart) {
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
            // 不是补给车（没有 getCargo），或反射被拒：这一路就没有贡献。
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
