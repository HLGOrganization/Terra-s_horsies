package com.tfcicys.horses.mixin;

import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.ai.FrightenedHorse;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 有马匹死亡时，让周围的马以最快速度逃离那具尸体。
 *
 * <h2>为什么挂在 {@code LivingEntity.die} 而不是 {@code AbstractHorse} 上</h2>
 *
 * <p>{@code die} 声明在 {@code LivingEntity}，{@code AbstractHorse} 和 {@code BhBreedHorse}
 * 都没有覆写它（已 javap 核对）。Mixin 只能注入目标类<b>自己声明</b>的方法，
 * 所以这里注入到 {@code LivingEntity.die}，再用 {@code instanceof AbstractHorse} 把自己收窄回马科。
 * 代价是每次任意生物死亡都会走一遍这个方法，但第一行就是 {@code instanceof}，可以忽略。
 *
 * <h2>判定范围</h2>
 *
 * <p>「马匹死亡」接受任意 {@code AbstractHorse} —— 原版马、TFC 的马／驴／骡、Icy 的 15 个品种
 * 都算。羊驼（{@code Llama}）继承自 {@code AbstractHorse}，因此也会算数；这在实际游戏里
 * 无害（马被羊驼的尸体吓到只是跑开），但属于刻意取舍，写在这里留档。
 *
 * <p>「被吓到的马」只包括实现了 {@link FrightenedHorse} 的实体，也就是只有 Icy 自己的品种。
 * 所以 TFC 本家的马不会因为同伴死亡而逃跑。
 *
 * <h2>用 HEAD 而不是 TAIL</h2>
 *
 * <p>{@code die} 内部会走死亡动画、掉落物、移除实体等流程，位置在 TAIL 时仍然有效，
 * 但 HEAD 更省事也更快 —— 我们只读 {@code position()}。
 */
@Mixin(LivingEntity.class)
public abstract class AbstractHorseDeathFrightMixin {

    @Inject(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"))
    private void tfcicys$scareNearbyHorses(final DamageSource source, final CallbackInfo ci) {
        if (!TFCICYSConfig.neutralCombat()) {
            return;
        }
        final LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof AbstractHorse)) {
            return;
        }
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }

        final double radius = TFCICYSConfig.horseDeathFleeRadius();
        if (radius <= 0.0D) {
            return;
        }
        final int ticks = TFCICYSConfig.horseDeathFleeTicks();
        if (ticks <= 0) {
            return;
        }

        final Vec3 death = self.position();
        for (final AbstractHorse other : level.getEntitiesOfClass(
                AbstractHorse.class,
                self.getBoundingBox().inflate(radius),
                // 只吓野马：已驯服的马不因同伴死亡逃跑（判定见 TFCICYSConfig#isWildHorse）。
                // 在这里过滤而不是只靠 FleeFromThreatGoal，是为了别给已驯服的马
                // 写一份永远用不上的受惊状态。
                candidate -> candidate != self
                        && candidate.isAlive()
                        && TFCICYSConfig.isWildHorse(candidate))) {
            if (other instanceof FrightenedHorse frightened) {
                frightened.tfcicys$startFleeing(death, ticks);
            }
        }
    }
}
