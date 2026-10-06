package com.tfcicys.horses.load;

import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

import org.slf4j.Logger;

/**
 * 「传送一匹马之前先放手」这件事的公用实现。
 *
 * <p>背景：Icy 的哨子会把马直接传送到玩家身边。它的检查只认自己的马车装备
 * （{@code IHorseData.bh_hasCartGear()} / {@code ModItems.HORSE_CART}），
 * 完全不知道 AstikorCarts／TFC 的车，于是挂在马后面的车厢会跟着马直线飞过来——
 * 尤其是本来就拉不动那辆车的马，看起来完全违背物理。
 *
 * <p>所以在这匹马即将被传送/重塑之前：
 * <ol>
 *   <li>{@code ejectPassengers()}：骑手（玩家或其他生物）先下来；</li>
 *   <li>解挂马车：{@code setPulling(null)}，等价于玩家自己解开车，车留在原地；</li>
 *   <li>刷新这匹马的负重，免得传送之后还挂着那辆车的重量。</li>
 * </ol>
 *
 * <p>有一个 {@link Logger} 是刻意的：解挂失败（例如上游改了 API）不该让传送崩掉，
 * 但也不能静默——那种情况下马会带着车一起飞，正是这个功能要消灭的现象。
 */
public final class WhistleRelease {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 同一种失败只报一次。 */
    private static boolean detachFailureLogged;

    private WhistleRelease() {}

    public static void release(AbstractHorse horse) {
        if (horse == null) {
            return;
        }
        // 先请乘客下来：否则玩家会跟着一起被传送走。
        horse.ejectPassengers();

        final Entity cart = AstikorCartsApi.drawnOf(horse);
        if (cart == null) {
            return;
        }
        if (AstikorCartsApi.unhitch(cart)) {
            CartPullRegistry.detach(cart);
            LoadEvents.refreshPuller(horse);
        } else if (!detachFailureLogged) {
            detachFailureLogged = true;
            LOGGER.warn("[terras_horsies] 传送前解挂马车失败（车 {}），后续同类失败不再重复报告。"
                    + "这会让马车跟着一起被传送。", cart);
        }
    }
}
