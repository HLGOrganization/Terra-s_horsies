package com.tfcicys.horses.mixin;

import com.tfcicys.horses.TFCICYSConfig;

import icy.betterhorses.net.BhHorseAttributes;
import icy.betterhorses.net.BhHorseTraits;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把羁绊给马匹的属性加成改成整合包要的曲线（Icy 原来的太陡）。
 *
 * <h2>接管的是什么</h2>
 *
 * <p>Icy 的 {@code BhHorseTraits.applyBondAttributes(AbstractHorse, int)} 全文只有三行：
 *
 * <pre>
 * double growth = Math.min(bond / 20, 5) * 0.15;                       // 每 20 点 +15%，满 100 点 +75%
 * BhHorseAttributes.apply(horse, MOVEMENT_SPEED, Source.BOND, "growth", growth, MULTIPLY_BASE);
 * BhHorseAttributes.apply(horse, JUMP_STRENGTH,  Source.BOND, "growth", growth, MULTIPLY_BASE);
 * </pre>
 *
 * <p>逐字节确认：偏移 0-13 就是 {@code bond/20} → {@code Math.min(_, 5)} → {@code ×0.15}，
 * 随后两次 {@code apply} 用的是同一个名字 {@code "growth"} —— 速度和跳跃共用同一个系数。
 *
 * <p>本 mixin 在 HEAD 取消它、按配置重装两条修饰符。之所以是"接管"而不是"改参数"：
 * 那个公式把速度与跳跃绑在一个系数上，而整合包要的是速度 +40%、跳跃 +20%，两个数不同。
 *
 * <h2>为什么能安全接管（不会叠成双倍）</h2>
 *
 * <p>{@code BhHorseAttributes.apply} 的实现是：按 {@code (Source, name)} 派生一个固定 UUID，
 * <b>先 {@code removeModifier(UUID)}、再 {@code addTransientModifier}</b>
 * （反汇编偏移 20-57：{@code m_22120_} 后跟 {@code m_22118_}）。
 * 也就是说同一个身份是<b>替换</b>而不是叠加。这里沿用 Icy 自己的
 * {@code Source.BOND} + {@code "growth"}，所以反复调用不会滚雪球，
 * 万一 {@code applyBondAttributes} 被重复触发也只是把同一个 UUID 的修饰符换掉。
 *
 * <p>触发时机覆盖全部路径：Icy 只在两处走到这个方法 ——
 * {@code AbstractHorseMixin.bh_applyBondAttributes()}（由 {@code bh_setBond} 和 NBT 读取后调用），
 * 以及 {@code BhHorseTraits.grantBond} 经 {@code bh_setBond} 间接到达。
 *
 * <h2>速度与跳跃各是哪个属性（装反会把 40/20 对调）</h2>
 *
 * <p>两个 SRG 字段的身份是从 Icy 自己的用法反推出来的，不是猜的：
 * {@code HorseInfoScreen} 里 {@code f_22279_} 乘 43.2 后配文案 {@code info.speed}，
 * {@code f_22288_} 走 {@code max(v × 6 − 1, 0)} 后配文案 {@code info.jump}；
 * 而只用到 {@code f_22279_} 的四个类（{@code ArchetypePerks} 的道路加速、
 * {@code Endurance}／{@code StandstillBurst}／{@code TopEnd} 的冲刺）全是速度语义。
 * 因此 {@code f_22279_ = MOVEMENT_SPEED}、{@code f_22288_ = JUMP_STRENGTH}。
 *
 * <h2>代价</h2>
 *
 * <p>接管意味着 Icy 以后若在这个方法里再加第三条属性，本 mixin 不会跟着加。
 * 升级 Icy 时按 DEV_NOTES 第 14.18 节的办法重新核对一次这个方法体即可。
 * 证据链与配置说明见 docs/DEV_NOTES.md 第 24 节。
 */
@Mixin(value = BhHorseTraits.class, remap = false)
public abstract class BhBondAttributesMixin {

    /** 羁绊满值。Icy 的 {@code bh_setBond} 本来就会钳到 0~100，这里再夹一次是防手改 NBT。 */
    @Unique private static final int TFCICYS_MAX_BOND = 100;

    /** 加成按每 5 点羁绊一档。 */
    @Unique private static final int TFCICYS_BOND_PER_STEP = 5;

    /** 沿用 Icy 的修饰符名字，保证同一身份是"替换"而不是"叠加"。 */
    @Unique private static final String TFCICYS_BOND_MODIFIER = "growth";

    @Inject(
            method = "applyBondAttributes(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void tfcicys$bondBonus(final AbstractHorse horse, final int bond, final CallbackInfo ci) {
        final int steps = Math.min(Math.max(bond, 0), TFCICYS_MAX_BOND) / TFCICYS_BOND_PER_STEP;

        BhHorseAttributes.apply(horse, Attributes.MOVEMENT_SPEED, BhHorseAttributes.Source.BOND,
                TFCICYS_BOND_MODIFIER, steps * TFCICYSConfig.bondSpeedPerFive() / 100.0D,
                AttributeModifier.Operation.MULTIPLY_BASE);

        BhHorseAttributes.apply(horse, Attributes.JUMP_STRENGTH, BhHorseAttributes.Source.BOND,
                TFCICYS_BOND_MODIFIER, steps * TFCICYSConfig.bondJumpPerFive() / 100.0D,
                AttributeModifier.Operation.MULTIPLY_BASE);

        // 原公式不再执行：速度与跳跃两档数值不同，Icy 那套单一系数装不出来。
        ci.cancel();
    }
}
