package com.tfcicys.horses.load;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.tfcicys.horses.TfcIcysHorses;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 与 More Attributes 的唯一接触面。
 *
 * <p>隔离的意义在于：本类所有<b>方法签名只出现 Minecraft 类型</b>。
 * JVM 是在真正执行到某条字节码时才去解析它的常量池项的，所以只要
 * {@link TfcIcysHorses#hasMoreAttributes()} 为假、调用方提前返回，
 * 这个类里对 {@code org.mantodea.more_attributes} 的引用就永远不会被解析，
 * 模组缺席时不会抛 {@code NoClassDefFoundError}。
 *
 * <p>反过来说，一旦把 MA 的类型写进签名（哪怕只是参数），
 * 类校验阶段就会去加载它——这正是要在这一层避开的。
 */
public final class MoreAttributesApi {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 负重计算失败只报告一次，避免在 tick 循环里刷屏。 */
    private static final AtomicBoolean weighFailureLogged = new AtomicBoolean(false);

    private MoreAttributesApi() {}

    /** {@code more_attributes:equip_load_max}，负重上限。缺席时返回 null。 */
    public static Attribute equipLoadMax() {
        if (!TfcIcysHorses.hasMoreAttributes()) {
            return null;
        }
        try {
            return org.mantodea.more_attributes.attributes.DetailAttributes.EquipLoadMax;
        } catch (final Throwable t) {
            return null;
        }
    }

    /** {@code more_attributes:equip_load_current}，当前负重。缺席时返回 null。 */
    public static Attribute equipLoadCurrent() {
        if (!TfcIcysHorses.hasMoreAttributes()) {
            return null;
        }
        try {
            return org.mantodea.more_attributes.attributes.DetailAttributes.EquipLoadCurrent;
        } catch (final Throwable t) {
            return null;
        }
    }

    /**
     * 单格物品的负重，直接复用 More Attributes 的算法。
     *
     * <p>它的公式是 {@code ItemWeights.getOrDefault(id, 64 / Weight.stackSize) * count}，
     * 再乘 TFC 的 {@code Size} 系数（NORMAL×2 / LARGE×3 / VERY_LARGE×4 / HUGE×8），
     * 并递归累加背包类容器内容。这里不重写一遍，是为了让马车里的物品
     * 与玩家背包里的同一件物品永远算出同一个数。
     *
     * @return 负重；模组缺席或出错时返回 0
     */
    public static int itemWeight(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !TfcIcysHorses.hasMoreAttributes()) {
            return 0;
        }
        try {
            return org.mantodea.more_attributes.utils.ItemUtils.getWeight(stack);
        } catch (final Throwable t) {
            // 只在第一次失败时报告：负重是在 tick 循环里按格计算的，
            // 每格每 tick 打一条日志会瞬间淹没 log。
            if (weighFailureLogged.compareAndSet(false, true)) {
                LOGGER.warn("[terras_horsies] 调用 More Attributes 的物品负重计算失败，"
                        + "物品 {} 一律按 0 计。后续同类失败不再重复报告。", stack.getItem(), t);
            }
            return 0;
        }
    }

    /**
     * 请 More Attributes 立刻重算该玩家的负重与减速。
     *
     * <p>不调用也不会错——它每 tick 在 {@code PlayerEvents.tick} 里自己会跑一遍；
     * 这里只是让「刚骑上马／刚挂上车」的瞬间就生效，避免一 tick 的延迟。
     */
    public static void requestRebuild(Player player) {
        if (player == null || !TfcIcysHorses.hasMoreAttributes()) {
            return;
        }
        try {
            org.mantodea.more_attributes.utils.ModifierUtils.DetailModifiers.EquipLoad.rebuildModifier(player);
        } catch (final Throwable ignored) {
            // 上游签名变动不应影响主流程。
        }
    }

    /** 玩家当前的负重值，用于继承给坐骑。缺席时返回 0。 */
    public static double currentLoadOf(Player player) {
        final Attribute current = equipLoadCurrent();
        if (player == null || current == null) {
            return 0.0D;
        }
        final var instance = player.getAttribute(current);
        return instance == null ? 0.0D : instance.getValue();
    }

    /**
     * 玩家某项<b>主属性</b>的等级（{@code "strength"} 力量、{@code "skill"} 技巧……）。
     *
     * <p>主属性不是原版 {@code Attribute}，而是 More Attributes 自己的一套「等级」，
     * 存在 {@code PlayerClassCapability} 里，官方入口是
     * {@code LevelUtils.getLevel(Player, String)}。
     *
     * <p>键名就是 {@code data/more_attributes/attributes/<name>.json} 里的 {@code name}
     * 字段（每级效果与 {@code baseLevel} 都在那个文件里）。出厂 9 项：
     * {@code strength / health / focus / stamina / endurance / intelligence / agility / skill / luck}。
     * 两项目标属性的 {@code baseLevel} 都是 10。
     *
     * <p>返回 0 的三种情况：模组缺席、键名不存在、上游出错。返回 0 会让调用方算不出加成，
     * 也就是「不生效」而不是「算错」，这比抛异常安全。
     *
     * @param name 属性名，如 {@code "strength"}
     * @return 等级；未知或出错时返回 0
     */
    public static int mainAttributeLevel(Player player, String name) {
        if (player == null || name == null || !TfcIcysHorses.hasMoreAttributes()) {
            return 0;
        }
        try {
            return org.mantodea.more_attributes.utils.LevelUtils.getLevel(player, name);
        } catch (final Throwable t) {
            return 0;
        }
    }
}
