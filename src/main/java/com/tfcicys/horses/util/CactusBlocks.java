package com.tfcicys.horses.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 「类仙人掌」的识别与查找，仙人掌相关的两个功能共用。
 *
 * <p>共用方：
 *
 * <ul>
 *   <li>{@code AbstractHorseCactusMixin} —— 穿马铠免疫刺伤、被扎就走开；</li>
 *   <li>{@code BhBreedHorseFamiliarityMixin} —— 判断「已经脱离那丛仙人掌了吧」，
 *       用来提前结束逃跑，见 {@link #isNear}。</li>
 * </ul>
 *
 * <h2>为什么识别要分三层</h2>
 *
 * <p>需求是「其他模组的类仙人掌方块也一并生效」。三层依次尝试，命中即算：
 *
 * <ol>
 *   <li>{@code state.is(Blocks.CACTUS)} —— 原版。</li>
 *   <li>{@code state.getBlock() instanceof CactusBlock} —— 绝大多数模组的类仙人掌方块
 *       都是继承原版写的，<b>这一层覆盖面最大</b>，也不依赖命名。</li>
 *   <li>注册名里含 {@code "cactus"}（命名空间或路径任一段）。</li>
 * </ol>
 *
 * <p>三层都不中也<b>不算致命</b>：{@link #findNearest} 返回 {@code null} 时调用方会退回
 * 「离开当前位置」，照样能脱离。
 */
public final class CactusBlocks {

    private CactusBlocks() {}

    /**
     * 扫描时包围盒外扩多少格。
     *
     * <p>取 1 格是有依据的：原版 {@code CactusBlock.entityInside} 只在方块与实体包围盒
     * <b>相交</b>时才被调用，所以扎到马的那株仙人掌必定落在包围盒内；外扩 1 格是为了容忍
     * 「刚擦过、这一 tick 已经离开」的边界情况。
     *
     * <p>「是否脱离」用的也是这个范围（见 {@link #isNear}），语义因此是对称的：
     * 「还有仙人掌能扎到它」为真，否则为假。
     */
    public static final double SCAN_INFLATE = 1.0D;

    /** 这个方块算不算类仙人掌方块。三层判定见类注释。 */
    public static boolean isCactus(final BlockState state) {
        if (state.is(Blocks.CACTUS)) {
            return true;
        }
        if (state.getBlock() instanceof CactusBlock) {
            return true;
        }
        final ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
        return id.getPath().contains("cactus") || id.getNamespace().contains("cactus");
    }

    /**
     * 这份伤害是不是仙人掌刺伤。三层依次尝试，任一层命中即算：
     *
     * <ol>
     *   <li>{@code source.is(DamageTypes.CACTUS)} —— 原版类型。所有直接复用
     *       {@code damageSources().cactus()} 的模组（含继承 {@code CactusBlock}
     *       但没覆写 {@code entityInside} 的）都走这一条。</li>
     *   <li>{@code getMsgId()} 里含 {@code "cactus"} —— 取的是伤害类型的消息 ID。</li>
     *   <li>伤害类型的注册名（{@code typeHolder().unwrapKey()}）含 {@code "cactus"}。</li>
     * </ol>
     *
     * <p><b>已知覆盖不到</b>：某个模组注册了名字里完全没有 {@code cactus} 的伤害类型，
     * 并用它自己的方块造成刺伤。这种情况无法从伤害来源识别 —— 要兜住只能改成按方块位置判断，
     * 但那样会把「站在仙人掌旁边被摔伤」也算成刺伤，所以不做。
     */
    public static boolean isCactusDamage(final DamageSource source) {
        if (source.is(DamageTypes.CACTUS)) {
            return true;
        }
        final String msgId = source.getMsgId();
        if (msgId != null && msgId.toLowerCase(Locale.ROOT).contains("cactus")) {
            return true;
        }
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().getPath().contains("cactus")
                        || key.location().getNamespace().contains("cactus"))
                .orElse(false);
    }

    /**
     * 马匹身上／紧邻有没有类仙人掌方块。
     *
     * <p>找到第一个就返回，不比距离 —— 逃跑的收尾判定只关心「还有没有」，
     * 而且这个方法是<b>每 tick 都会被问到</b>的（见 {@code tfcicys$fleePoint}），
     * 所以让它尽早返回。
     */
    public static boolean isNear(final AbstractHorse horse) {
        for (final BlockPos pos : scanPositions(horse)) {
            if (isCactus(horse.level().getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出马身上／紧邻<b>最近</b>的一株类仙人掌方块，返回它的方块中心；找不到返回 {@code null}。
     *
     * <p>起点取方块中心而不是方块角，是为了让「朝反方向跑」算出来的方向更稳。
     */
    @Nullable
    public static Vec3 findNearest(final AbstractHorse horse) {
        Vec3 nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (final BlockPos pos : scanPositions(horse)) {
            if (!isCactus(horse.level().getBlockState(pos))) {
                continue;
            }
            // 注意：betweenClosed 给的是复用的 MutableBlockPos，必须在循环内立刻取值。
            final Vec3 center = Vec3.atCenterOf(pos);
            final double distSqr = horse.position().distanceToSqr(center);
            if (distSqr < nearestSqr) {
                nearestSqr = distSqr;
                nearest = center;
            }
        }
        return nearest;
    }

    private static Iterable<BlockPos> scanPositions(final AbstractHorse horse) {
        final AABB box = horse.getBoundingBox().inflate(SCAN_INFLATE);
        return BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ));
    }
}
