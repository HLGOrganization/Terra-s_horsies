package com.tfcicys.horses.load;

import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * 与 Icy's Better Horses 的接触面，全部集中在这里。
 *
 * <p>和 {@link MoreAttributesApi} 同样的理由：签名只出现 Minecraft 类型。
 * 不过 Icy 是本模组的硬依赖（马匹功能全建立在它上面），
 * 所以这里没有做缺席保护——真正的可选依赖只有 More Attributes 与马车。
 */
public final class IcyBreedApi {

    private IcyBreedApi() {}

    /**
     * 这匹马是不是 Icy 管理的品种。
     *
     * <p>{@code BhHorseKind.managed} 读的是 Icy 自己的 {@code MANAGED} 实体标签，
     * 比 {@code instanceof BhBreedHorse} 更稳：Icy 日后换实体基类也不会失效。
     */
    public static boolean isIcyHorse(AbstractHorse horse) {
        if (horse == null) {
            return false;
        }
        try {
            return icy.betterhorses.net.BhHorseKind.managed(horse);
        } catch (final Throwable t) {
            return false;
        }
    }

    /**
     * 取品种 ID，例如 {@code "arabian"}、{@code "shire"}。
     *
     * <p><b>这里曾经用错过 API，务必保留注释。</b>原先取的是
     * {@code HorseBreed.speciesFor(horse)}，但 javap 显示它的第一个分支是
     *
     * <pre>
     *   if (horse instanceof Horse) return null;   // 原版 Horse 一律返回 null
     * </pre>
     *
     * <p>而 {@code BhBreedHorse extends Horse}，所以 Icy 的每一匹马都会命中这一分支、
     * 拿到 {@code null}，于是所有马匹都被归入 {@link HorseCategory#DEFAULT}。
     * 症状极具迷惑性：配置改了什么反应都没有（因为 {@code draftLoad} 等键从未被读到），
     * 同时超重惩罚按默认上限 1000 计算，导致马匹几乎走不动。
     *
     * <p>正确的入口是 {@code BhBreedEntity.bhFixedBreed()}，它返回
     * {@code ResourceKey<BreedType>}，形如 {@code icys-better-horses:belgian}。
     *
     * <p>{@code speciesFor} 仍然作为回退保留：它对原版驴／骡真正有效，
     * 而那些实体不属于 {@code BhBreedEntity}。
     */
    public static String breedIdOf(AbstractHorse horse) {
        if (horse == null) {
            return "unknown";
        }
        try {
            // 首选：Icy 自己的品种键。Icy 的马实体实现了 BhBreedEntity。
            if (horse instanceof icy.betterhorses.net.entity.BhBreedEntity breedEntity) {
                final net.minecraft.resources.ResourceKey<?> key = breedEntity.bhFixedBreed();
                if (key != null) {
                    return key.location().getPath();
                }
            }
        } catch (final Throwable ignored) {
            // 落到下面的回退路径。
        }
        try {
            // 回退：对原版驴／骡有效（它们不是 BhBreedEntity）。
            final icy.betterhorses.net.HorseBreed breed = icy.betterhorses.net.HorseBreed.speciesFor(horse);
            return breed == null ? "unknown" : breed.id();
        } catch (final Throwable t) {
            return "unknown";
        }
    }
}
