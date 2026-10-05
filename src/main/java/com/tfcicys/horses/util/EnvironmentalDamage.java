package com.tfcicys.horses.util;

import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.ai.FrightenedHorse;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 「不是活物打的，但一样会疼」那一类伤害。
 *
 * <p>仙人掌的 {@link CactusBlocks#isCactusDamage} 只认仙人掌，因为它要用<b>具体哪一株</b>
 * 算逃离方向，还得判断「挪开之后是不是真的不扎了」。环境伤害没有这个便利：
 * 岩浆不会告诉马它的边界在哪。所以这里不识别方块，只认一件事 ——
 * <b>刚挨了一下，来自非生物来源</b>，然后就往反方向挪；不再挨了就停。
 *
 * <p>判定入口在 {@code AbstractHorseThreatMixin} 的 {@code hurt} HEAD，和记攻击者同一处。
 * 那边的分支条件是「{@code source.getEntity()} 不是活体」—— 覆盖 {@code null}（方块、自然现象）
 * 和非生物实体（掉落的铁砧、TNT）两种。
 *
 * <h2>哪些不逃</h2>
 *
 * <p>由配置项 {@code hazardFleeIgnore} 决定，默认排掉跑也没用的那些（溺水、摔落、饥饿、虚空、
 * 魔法、凋零……）。判断标准只有一条：<b>挪开能不能改善处境</b>。
 *
 * <p>另外无论配置怎么写，仙人掌伤害都不走这条路 —— 它有自己的「贴着就停」收尾判定，
 * 两者同时触发会把那个标志位顶掉，见 {@code FrightenedHorse#tfcicys$startFleeingHazard}。
 */
public final class EnvironmentalDamage {

    private EnvironmentalDamage() {}

    /**
     * 如果这次伤害属于「该挪开的环境伤害」，就启动一次环境逃跑。
     *
     * <p>反复调用是安全的，也是必要的：每次挨烫都会把「还要逃多久」往后推，
     * 于是马会一直走到不再受伤为止。逃离点取<b>挨烫时马所在的位置</b> ——
     * 这个位置会在马每挨一次烫时更新一次，形成「顺着一个方向往外走」的效果，
     * 而不是原地打转。
     *
     * @param horse     挨伤害的马
     * @param frightened 同一匹马的状态接口（调用方已确认实现）
     * @param source    伤害来源，必须是非生物来源；活物来源由调用方走另一条路
     */
    public static void maybeFlee(final LivingEntity horse, final FrightenedHorse frightened,
                                 final DamageSource source) {
        if (!TFCICYSConfig.hazardFlee()) {
            return;
        }
        // 仙人掌单列：它靠「身边还有没有仙人掌」收尾，这里靠计时器收尾，不能混。
        if (CactusBlocks.isCactusDamage(source)) {
            return;
        }
        if (isIgnored(source)) {
            return;
        }
        frightened.tfcicys$startFleeingHazard(
                horse.position(),
                TFCICYSConfig.hazardFleeTicks(),
                TFCICYSConfig.hazardFleeDistance(),
                TFCICYSConfig.hazardFleeSpeed());
    }

    /**
     * 这个伤害类型是否在忽略名单里。
     *
     * <p>名单项写成完整的 {@code minecraft:drowning}，也可以只写 {@code drowning}；
     * 两种都能匹配上（比较时忽略大小写）。取不到类型名时保守地当作「忽略」，
     * 免得因为一次 API 异常让马无缘无故开始乱跑。
     */
    public static boolean isIgnored(final DamageSource source) {
        final String id = idOf(source);
        if (id == null) {
            return true;
        }
        final String path = id.substring(id.indexOf(':') + 1);
        for (final String entry : TFCICYSConfig.hazardFleeIgnore()) {
            if (entry == null) {
                continue;
            }
            final String wanted = entry.trim().toLowerCase(Locale.ROOT);
            if (wanted.equals(id) || wanted.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** 伤害类型的注册名，例如 {@code minecraft:lava}；取不到时返回 {@code null}。 */
    @Nullable
    private static String idOf(final DamageSource source) {
        try {
            return source.typeHolder().unwrapKey()
                    .map(ResourceKey::location)
                    .map(Object::toString)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .orElse(null);
        } catch (final Throwable ignored) {
            // 上游模组注册了畸形伤害类型时不要让伤害事件本身炸掉。
            return null;
        }
    }
}
