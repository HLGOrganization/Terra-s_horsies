package com.tfcicys.horses.mixin;

import com.tfcicys.horses.ai.FrightenedHorse;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 记下「刚才是谁打了我」，供低血量逃跑使用。
 *
 * <h2>为什么需要单独一份记忆</h2>
 *
 * <p>低血量逃跑原先读的是 Icy 的 {@code bh_getCombatTarget}，而那个值有两个问题：
 *
 * <ol>
 *   <li><b>阈值撞车</b>：Icy 的 {@code DefendOwnerGoal.canContinueToUse}（{@code m_8045_}）
 *       在血量 &lt; 30% 时放弃追击，<b>并且顺手把战斗目标清成 null</b>（字节码偏移 163 处
 *       的 {@code bh_setCombatTarget}）。而 30% 正好是逃跑的触发阈值 ——
 *       马刚掉到该逃的血量，触发源就在同一 tick 被抹掉。
 *       症状就是<b>打到残血却站着不动</b>。</li>
 *   <li><b>它本来就可能不存在</b>：亲密度 ≥ 18 的马被玩家打时不反击（需求明确要的），
 *       PvP 关掉时也不会把玩家写成目标。但「血少了要逃」不该取决于「它刚才有没有还手」。</li>
 * </ol>
 *
 * <p>所以在 {@code hurt} 的 HEAD 独立记一份。这条路径<b>不经过</b> Icy 的战斗系统，
 * 因此不受 PvP 设置、亲密度反击曲线、{@code horse_kick} 等功能开关影响 ——
 * 「谁在打我」是一个事实，不需要谁的许可。
 *
 * <h2>与 {@code AbstractHorseCactusMixin} 同点注入</h2>
 *
 * <p>两者都是 {@code AbstractHorse.hurt} 的 HEAD 注入，但分属不同 mixin 类，Mixin 会都应用。
 * 顺序无所谓：仙人掌伤害的 {@code source.getEntity()} 是 {@code null}（是方块造成的），
 * 这里本来就什么都不做；而其他伤害在对面会被「不是仙人掌伤害」挡掉，也轮不到顺序问题。
 *
 * <p>只看 {@code source.getEntity()}：对弹射物来说它返回的是<b>发射者</b>而不是箭本身，
 * 这正是「谁打的我」想问的东西。
 *
 * <h2>作用范围</h2>
 *
 * <p>只有实现了 {@link FrightenedHorse} 的实体（也就是 Icy 的 15 个品种）会记。
 * 原版马、TFC 的马／驴／骡、羊驼都不受影响 —— 它们的低血量逃跑本来也不是本模组管的。
 *
 * <p>被骑着时一样会记：记下来是无害的，逃跑那边自己会因为 {@code isVehicle()} 拒绝接管移动。
 * 这样玩家一下马，马立刻就能按已有的血量判断逃开。
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseThreatMixin {

    @Inject(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"))
    private void tfcicys$rememberAttacker(final DamageSource source, final float amount,
                                          final CallbackInfoReturnable<Boolean> ci) {
        final AbstractHorse self = (AbstractHorse) (Object) this;
        if (!(self instanceof FrightenedHorse frightened)) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == self) {
            return;
        }
        frightened.tfcicys$noteAttacker(attacker);
    }
}
