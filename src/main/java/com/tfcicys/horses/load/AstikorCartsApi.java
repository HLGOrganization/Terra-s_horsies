package com.tfcicys.horses.load;

import com.tfcicys.horses.TfcIcysHorses;

import de.mennomax.astikorcarts.entity.AbstractDrawnEntity;
import de.mennomax.astikorcarts.world.AstikorWorld;
import net.minecraft.world.entity.Entity;

/**
 * 与 AstikorCarts 的第二个接触面（第一个是 {@link MoreAttributesApi} 那样的
 * 反射式隔离；这里改用「签名只出现 Minecraft 类型」的包装类）。
 *
 * <p>和 {@code MoreAttributesApi} 同一个道理：JVM 只在真正执行到某条字节码时才解析
 * 它的常量池项，所以只要调用方先确认 {@link TfcIcysHorses#hasTfcAstikorCarts()}，
 * 这个类里对 {@code de.mennomax.astikorcarts} 的引用就永远不会被解析，
 * 模组缺席时不会 {@code NoClassDefFoundError}。
 *
 * <p>为什么需要它：哨子传送那件事要「让马放下车」，而找车、解挂都是上游自己的 API。
 * 直接写业务代码里会把这些类型带进常量池；包一层既安全又读得懂。
 */
public final class AstikorCartsApi {

    private AstikorCartsApi() {}

    /**
     * 上游自己记的「这个拉车者此刻拉着哪辆车」。
     *
     * <p>用 {@code AstikorWorld}（内部是 {@code Int2ObjectMap}，按拉车者 id 记账）
     * 而不是我们那张索引：上游会 tick 这张表里的车，所以它才是权威。
     * 而且即使车处于「停放」状态（不在世界实体索引里）它也能找到。
     *
     * @return 车；没挂车、模组缺席或出错时返回 null
     */
    public static Entity drawnOf(Entity puller) {
        if (puller == null || !TfcIcysHorses.hasTfcAstikorCarts()) {
            return null;
        }
        try {
            return AstikorWorld.get(puller.level())
                    .map(world -> world.getDrawn(puller).orElse(null))
                    .orElse(null);
        } catch (final Throwable t) {
            return null;
        }
    }

    /**
     * 让这辆车放下（等价于玩家自己解开挂接）。
     *
     * <p>{@code setPulling(null)} 是上游「解除挂接」的唯一正规入口：它会清掉拉车者身上的
     * PULL/PULL_SLOWLY 移速修饰符、把 {@code pulling}/{@code pullingUUID}/{@code pullingId}
     * 一并清空、通知客户端、并从 {@code AstikorWorld} 里摘掉自己。
     *
     * @return 真的执行了解挂才返回 true
     */
    public static boolean unhitch(Entity cart) {
        if (cart == null || !TfcIcysHorses.hasTfcAstikorCarts()) {
            return false;
        }
        try {
            if (cart instanceof AbstractDrawnEntity drawn) {
                drawn.setPulling(null);
                return true;
            }
        } catch (final Throwable t) {
            // 上游签名变动不应影响主流程。
        }
        return false;
    }
}
