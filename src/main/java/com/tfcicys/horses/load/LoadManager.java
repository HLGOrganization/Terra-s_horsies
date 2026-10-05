package com.tfcicys.horses.load;

import java.util.UUID;

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

    /**
     * 最后一次成功读到的货物重量。
     *
     * <p>马车货箱是个 {@code ItemStackHandler}，实测会在相邻 tick 之间
     * 返回「有货」与「空」两种结果（同一实体 id，1 tick 内 4608 → 0）。
     * 这是货箱读取的不一致，不是玩家真的把货搬空了。
     * 配合同步摘要把它与「真的空了」区分开，见 {@link #cargoWeightOf}。
     */
    private static double lastKnownCargo;

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

    /** 车厢内货物的 More Attributes 重量合计。 */
    public static double cargoWeightOf(Entity cart) {
        final double sum = readCargoDirect(cart);
        if (sum > 0.0D) {
            // 读到了就是准的，记下来备用。
            lastKnownCargo = sum;
            return sum;
        }
        // 直接读取为 0 有两种可能，必须区分：
        //   ① 车真的空了 —— 玩家把货搬走了；
        //   ② 货箱这一次读不到内容 —— ItemStackHandler 会在服务端/客户端的
        //      不同实例之间给出不一致的结果。
        //
        // 用同步数据 CARGO 当权威判据：它由上游在货箱内容变化时刷新，
        // 两端一致，且玩家搬空时必然同步变成全空。所以——
        //   CARGO 还有货  → 属于 ②，沿用最后已知值
        //   CARGO 全空    → 属于 ①，归零
        //
        // 【注意】这里刻意不设时间窗口。早先版本给了 60 tick 宽限，
        // 结果因为 lastKnownCargoTick 在持续读 0 时不再更新，宽限一到
        // 负重就被打回裸车重（实测表现为「生效约 4 秒后又变轻」）。
        // 只要 CARGO 没清空，就不该认为玩家卸了货。
        return cargoSummaryNonEmpty(cart) ? lastKnownCargo : 0.0D;
    }

    /** 直接读容器，不做任何补偿。 */
    private static double readCargoDirect(Entity cart) {
        double sum = 0.0D;
        try {
            // 补给车实现了 Container；动物车没有货箱，会走到下面的能力查询并返回 0。
            if (cart instanceof net.minecraft.world.Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    sum += MoreAttributesApi.itemWeight(container.getItem(i));
                }
                return sum;
            }
            final var handlerOpt = cart.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (handlerOpt.isPresent()) {
                final IItemHandler handler = handlerOpt.orElse(null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        sum += MoreAttributesApi.itemWeight(handler.getStackInSlot(i));
                    }
                }
            }
        } catch (final Throwable t) {
            // 读容器失败不应该让整辆车的牵引逻辑崩掉，但必须留下痕迹——
            // 静默吞异常会让「货物重量算不出来」变成一个无从排查的黑洞。
            LOGGER.warn("[terras_horsies] 读取马车 {} 货箱失败，本车货物重量按 0 计", cart, t);
        }
        return sum;
    }

    /**
     * 用反射读同步数据 {@code getCargo()}，判断摘要里是否还有东西。
     *
     * <p>走反射是因为 {@code TFCSupplyCartEntity} 属于可选依赖，不能出现在签名或
     * 常量池引用里，否则 AstikorCarts 缺席时会 {@code NoClassDefFoundError}。
     *
     * <p>摘要的数量会被上游 {@code setCount(min(maxStackSize, count/k))} 截断，
     * 所以<b>不用它算重量</b>，只用来回答「车里到底还有没有货」。
     */
    private static boolean cargoSummaryNonEmpty(Entity cart) {
        try {
            final java.lang.reflect.Method getCargo = cart.getClass().getMethod("getCargo");
            final Object cargo = getCargo.invoke(cart);
            if (cargo instanceof java.util.List<?> list) {
                for (final Object element : list) {
                    if (element instanceof ItemStack stack && !stack.isEmpty()) {
                        return true;
                    }
                }
            }
        } catch (final Throwable ignored) {
            // 不是补给车（没有 getCargo），或反射被拒：当作「无法确认」。
        }
        return false;
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
        maxInstance.setBaseValue(capOf(entity));
        curInstance.setBaseValue(loadOf(entity));

        applyPenalty(entity, curInstance.getValue(), maxInstance.getValue());
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

    private static void setAddition(AttributeInstance instance, UUID id, String name, double amount) {
        instance.removeModifier(id);
        if (amount > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
        }
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
