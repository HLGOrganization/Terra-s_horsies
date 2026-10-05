package com.tfcicys.horses.mixin;

import com.tfcicys.horses.ai.FrightenedHorse;
import com.tfcicys.horses.util.EnvironmentalDamage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * <h2>不是活物打的时候：不记「谁」，改成挪开</h2>
 *
 * <p>{@code source.getEntity()} 不是活体有两种情形 —— 为 {@code null}（岩浆、岩浆块、火焰、
 * 仙人掌这类方块与自然现象），或者是个非生物实体（掉落的铁砧、TNT）。两种都<b>没有
 * 「谁」可记</b>，但都要处理：站着挨烫会死。所以这条分支交给
 * {@link EnvironmentalDamage}，它认的是伤害类型而不是攻击者，让马朝反方向挪开，
 * 并且每次挨烫都续一次时限 —— 直到不再受伤（= 已经离开伤害区域）才结束。
 *
 * <p>仙人掌是唯一被排除在外的：它有自己的「贴着就停」收尾判定，
 * 两者同时触发会互相顶掉标志位。
 *
 * <h2>作用范围</h2>
 *
 * <p>只有实现了 {@link FrightenedHorse} 的实体（也就是 Icy 的 15 个品种）会记、会逃。
 * 原版马、TFC 的马／驴／骡、羊驼都不受影响 —— 它们的低血量逃跑本来也不是本模组管的，
 * 而 TFC 自己的马科有另一套（亲密度）体系，见 docs/DEV_NOTES.md。
 *
 * <p>被骑着时一样会记：记下来是无害的，逃跑那边自己会因为 {@code isVehicle()} 拒绝接管移动。
 * 这样玩家一下马，马立刻就能按已有的血量判断逃开；环境伤害那一档同理 ——
 * 骑着的时候该由玩家把马带出岩浆，而不是让马跟缰绳较劲。
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
        // 0 点伤害是某些模组用来试探的，不该惊动马匹。
        if (amount <= 0.0F) {
            return;
        }
        final Entity sourceEntity = source.getEntity();
        if (sourceEntity == self) {
            // 自己弄伤自己（箭弹回来、自己的弹射物）：既没有攻击者，也不是环境危害。
            return;
        }
        if (sourceEntity instanceof LivingEntity attacker) {
            frightened.tfcicys$noteAttacker(attacker);
            return;
        }
        // 非生物来源：岩浆、岩浆块、火焰、掉落的铁砧、TNT……没有「谁」可记，改成挪开。
        // 只在服务端启动：逃跑状态读 level().getGameTime()，客户端那份没有意义。
        if (!self.level().isClientSide) {
            EnvironmentalDamage.maybeFlee(self, frightened, source);
        }
    }
}
