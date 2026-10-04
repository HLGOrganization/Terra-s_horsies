package com.tfcicys.horses.load;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;

/**
 * 「拉车者 → 马车」的反向索引。
 *
 * <p>AstikorCarts 只在马车一侧保存 {@code pulling} 字段，马身上没有任何反向引用，
 * 所以要知道「这匹马此刻拉着什么车」就只能每 tick 扫附近的实体——太贵。
 * 这里改为在 {@code AbstractDrawnEntity.setPulling()} 被调用时记一笔，
 * 把 O(n) 的扫描降成一次查表。
 *
 * <p>用 {@link WeakHashMap}：实体卸载后条目会被自动回收，
 * 不会因为长期挂机而攒出一张越用越大的表。键和值都是实体，
 * 弱引用同时挂在两侧，任一方被回收都能让条目消失。
 */
public final class CartPullRegistry {

    /** 拉车者 → 它拉着的马车。 */
    private static final Map<Entity, Entity> PULLING = new WeakHashMap<>();

    private CartPullRegistry() {}

    /**
     * 记录一次挂接。
     *
     * @param puller 拉车者（玩家或生物）
     * @param cart   马车；传 null 表示解除挂接
     */
    public static void attach(Entity puller, Entity cart) {
        if (cart == null) {
            return;
        }
        if (puller == null) {
            // 只清掉原本指向这辆车的记录。
            PULLING.values().removeIf(v -> v == cart);
            return;
        }
        PULLING.put(puller, cart);
    }

    /** 解除某辆车的挂接（换拉车者、拆车、分块卸载时调用）。 */
    public static void detach(Entity cart) {
        if (cart != null) {
            PULLING.values().removeIf(v -> v == cart);
        }
    }

    /** 该实体此刻拉着的马车，没有则返回 null。 */
    public static Entity cartOf(Entity puller) {
        if (puller == null) {
            return null;
        }
        final Entity cart = PULLING.get(puller);
        if (cart != null && (cart.isRemoved() || !cart.isAlive())) {
            PULLING.remove(puller);
            return null;
        }
        return cart;
    }

    /**
     * 此刻正拉着这辆车的实体，没有则返回 null。
     *
     * <p>换马／解挂时需要找到「旧的拉车者」把它的负重降回去，
     * 而 {@code setPulling} 的回调只拿得到马车本身，所以需要这个反向查询。
     * 表很小（同时存在的马车数），线性扫描可以接受。
     */
    public static Entity pullerOf(Entity cart) {
        if (cart == null) {
            return null;
        }
        for (final Map.Entry<Entity, Entity> entry : PULLING.entrySet()) {
            if (entry.getValue() == cart) {
                return entry.getKey();
            }
        }
        return null;
    }
}
