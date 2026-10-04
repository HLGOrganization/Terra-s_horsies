package com.tfcicys.horses;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(TfcIcysHorses.MOD_ID)
public final class TfcIcysHorses {

    /**
     * The mod id. Renamed from {@code tfc_icys_horses} to {@code terras_horsies}
     * (display name "Terra's horsies [TFC x icy's horses]", 大地上的马驹们).
     *
     * <p>Renaming this renames Forge's config file with it -- the user's tuned values live in
     * {@code config/terras_horsies-common.toml} and must be carried over from the old
     * {@code config/tfc_icys_horses-common.toml}.
     */
    public static final String MOD_ID = "terras_horsies";

    /** More Attributes 的 modId，负重系统的来源。 */
    public static final String MORE_ATTRIBUTES = "more_attributes";
    /** TFCAstikorCarts 的 modId，马车过载效果的来源。 */
    public static final String TFC_ASTIKOR_CARTS = "tfcastikorcarts";

    /**
     * 查询结果的缓存。
     *
     * <p>这些判定在 {@code LivingTickEvent} 里会按实体逐个命中，
     * 每次去 {@code ModList} 里查表是白费。用 {@code Boolean} 而非
     * {@code boolean}，是为了区分「还没查过」与「查过，结果是 false」。
     * {@code volatile}：加载完成后只写一次，之后都是并发读。
     */
    private static volatile Boolean moreAttributesCached;
    private static volatile Boolean tfcAstikorCartsCached;

    public TfcIcysHorses() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TFCICYSConfig.COMMON_SPEC);
    }

    /** More Attributes 是否在场。 */
    public static boolean hasMoreAttributes() {
        Boolean cached = moreAttributesCached;
        if (cached == null) {
            cached = queryMod(MORE_ATTRIBUTES);
            if (cached != null) {
                moreAttributesCached = cached;
                return cached;
            }
            return false;
        }
        return cached;
    }

    /** TFCAstikorCarts 是否在场。 */
    public static boolean hasTfcAstikorCarts() {
        Boolean cached = tfcAstikorCartsCached;
        if (cached == null) {
            cached = queryMod(TFC_ASTIKOR_CARTS);
            if (cached != null) {
                tfcAstikorCartsCached = cached;
                return cached;
            }
            return false;
        }
        return cached;
    }

    /**
     * 查询模组是否加载；{@code ModList} 尚未初始化时返回 {@code null}
     * 表示「暂时无法判断」，让调用方<b>不要缓存</b>这个结果。
     *
     * <p>{@code ModList.get()} 在极早的加载阶段会返回 null，
     * 此时若把 false 缓存下来，等 ModList 就绪后仍会一直以为模组缺席。
     */
    private static Boolean queryMod(String modId) {
        try {
            final ModList list = ModList.get();
            return list == null ? null : list.isLoaded(modId);
        } catch (final Throwable ignored) {
            return null;
        }
    }
}
