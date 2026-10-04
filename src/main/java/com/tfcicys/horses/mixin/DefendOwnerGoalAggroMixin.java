package com.tfcicys.horses.mixin;

import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.ai.FrightenedHorse;

import icy.betterhorses.net.IHorseData;
import icy.betterhorses.net.goal.DefendOwnerGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给战斗加上脱战条件：<b>距离</b>或<b>时间</b>任一满足就失去仇恨。
 *
 * <p>这里复用 Icy 的 {@code DefendOwnerGoal} 作为追击执行者（它有完整的
 * 追踪速度分级、6 格冲锋撞击、12 tick 挥击、寻路和视线控制）。它本来就有两个脱战条件：
 *
 * <ul>
 *   <li>马与目标距离 &gt; 32 格</li>
 *   <li>马的血量 &lt; 30%（这条保留不动，它才是「别把马打死去追一个敌人」的保险）</li>
 * </ul>
 *
 * <p>缺的是<b>时间</b>上限，而且 32 格对「一小段距离」来说太远了。这里补上：
 *
 * <ul>
 *   <li>目标离马超过 {@code retaliateDistance} 格（默认 16）→ 脱战</li>
 *   <li>距<b>最后一次被挑衅</b>超过本场战斗的时限 → 脱战</li>
 * </ul>
 *
 * <h2>时间是「续时」的，不是一次性的</h2>
 *
 * <p>计时起点是「目标最后一次挑衅这匹马」而不是「开打那一刻」：敌人还在打主人、
 * 或者还在打马自己，起点就一次次往后推，脱战倒计时重新开始；一旦停手满一个时限，
 * 就真正脱战。这样马不会因为一次挨打就追杀到天涯，也不会在敌人还在动手时中途「想通」。
 *
 * <p>「最后一次被挑衅」由 {@link FrightenedHorse#tfcicys$provokedAt(LivingEntity)} 给出，
 * 里面合并了两个来源：<b>打了马自己</b>（威胁记忆）与<b>打了马的主人</b>
 * （由 {@link BhHorseCombatAlertDefendMixin} 记下）。取更新的那个。
 *
 * <p>脱战的做法与 Icy 自己一致：把 {@code bh_setCombatTarget} 清成 {@code null}。
 * 目标一清，{@code canUse()} 就再也返回不了 true（它开头就要求目标非 null），
 * 所以只有<b>再次被挑衅</b>才会重新进入战斗 —— 这正是「失去仇恨」。
 *
 * <h2>时限取哪一个，取决于这一仗是怎么打起来的</h2>
 *
 * <ul>
 *   <li><b>马自己挨打后的反击</b>（{@link HorseCombatRetaliateMixin} 写的目标）：
 *       时限由亲密度曲线给出，亲密度到 {@code pacifyFamiliarity}（默认 18）以上
 *       干脆不反击 —— 这是需求里明确要的「驯熟的马不咬玩家」。</li>
 *   <li><b>替主人报仇</b>（Icy 原版的 {@code BhHorseCombatAlert.defend} 写的目标）：
 *       与亲密度<b>无关</b>，固定 {@code ownerDefendSeconds}（默认 10 秒）。</li>
 * </ul>
 *
 * <p>区分办法：目标是不是「刚才打了马的那个」。是 → 自己挨打后的反击；不是 → 替主人报仇。
 * 时限在 {@code canUse()} 成功时算一次并固定下来，免得追击途中「想通」。
 *
 * <p>三个注入点：
 *
 * <ul>
 *   <li>{@code canUse()Z} 的 RETURN：记录本场战斗的计时起点与时限。用 RETURN 而不是 TAIL，
 *       因为该方法有多条 return，需要覆盖每一条。<br>
 *       用 {@code cir.getReturnValueZ()} 区分成功／失败，失败时清掉计时。</li>
 *   <li>{@code canContinueToUse()Z} 的 HEAD（可取消）：续时 + 判断是否该脱战。</li>
 *   <li>{@code stop()V} 的 TAIL：清计时，让下一次战斗重新计时。</li>
 * </ul>
 *
 * <p>字段 {@code horse} / {@code target} 是 Icy 自己加的私有字段，不是原版，
 * 所以 {@code @Shadow} 要带 {@code remap = false}，否则注解处理器会报
 * 「Unable to locate obfuscation mapping for @Shadow field」。
 * 反过来，{@code canUse} / {@code canContinueToUse} / {@code stop} 是原版
 * {@code Goal} 的方法名，走默认 remap 由 refmap 翻译成 SRG。
 */
@Mixin(DefendOwnerGoal.class)
public abstract class DefendOwnerGoalAggroMixin {

    @Shadow(remap = false) private AbstractHorse horse;

    @Shadow(remap = false) private LivingEntity target;

    /** 本次战斗的计时起点（游戏刻）；{@code < 0} 表示不在战斗中。 */
    @Unique private long tfcicys$aggroStart = -1L;

    /**
     * 本次战斗的时限（tick），从 {@link #tfcicys$aggroStart} 起算。
     *
     * <p>在 {@code canUse()} 成功时算一次并固定下来，因为时限取决于<b>这一仗是怎么打起来的</b>
     * 和<b>当时的亲密度</b>；进入战斗之后目标不会再变，亲密度也不该中途改口。
     *
     * <p>0 表示这一仗根本不该打：亲密度已到豁免线，或者主人报仇被
     * {@code ownerDefendSeconds = 0} 关掉。正常情况下目标压根不会被写进来，这里是第二道保险。
     */
    @Unique private int tfcicys$aggroWindow = -1;

    /** 这一仗的时限：目标是不是「刚才打马的那个」。 */
    @Unique
    private int tfcicys$windowFor(final LivingEntity target) {
        if (this.horse instanceof FrightenedHorse frightened
                && frightened.tfcicys$recentAttacker() == target) {
            // 自己挨打后的反击：走亲密度曲线，够熟就直接返回 0（不追）。
            return TFCICYSConfig.aggroTicks(target, TFCICYSConfig.familiarityOf(this.horse));
        }
        // 替主人报仇：与亲密度无关。
        return TFCICYSConfig.ownerDefendTicks();
    }

    /** 目标最后一次挑衅这匹马的游戏刻；查不到返回 {@code Long.MIN_VALUE}。 */
    @Unique
    private long tfcicys$provokedAt() {
        if (this.target != null && this.horse instanceof FrightenedHorse frightened) {
            return frightened.tfcicys$provokedAt(this.target);
        }
        return Long.MIN_VALUE;
    }

    @Inject(method = "canUse()Z", at = @At("RETURN"))
    private void tfcicys$rememberAggroStart(final CallbackInfoReturnable<Boolean> cir) {
        if (!TFCICYSConfig.neutralCombat()) {
            return;
        }
        if (cir.getReturnValueZ()) {
            if (this.tfcicys$aggroStart < 0L && this.horse != null) {
                this.tfcicys$aggroWindow = this.target == null ? 0 : this.tfcicys$windowFor(this.target);
                // 起点取「最后一次被挑衅」而不是「开打这一刻」：
                // 引发这一仗的那一击本身就该算进计时里。
                final long provokedAt = this.tfcicys$provokedAt();
                this.tfcicys$aggroStart = provokedAt > Long.MIN_VALUE
                        ? provokedAt
                        : this.horse.level().getGameTime();
            }
        } else {
            this.tfcicys$aggroStart = -1L;
            this.tfcicys$aggroWindow = -1;
        }
    }

    @Inject(method = "canContinueToUse()Z", at = @At("HEAD"), cancellable = true)
    private void tfcicys$dropAggroWhenExpired(final CallbackInfoReturnable<Boolean> cir) {
        if (!TFCICYSConfig.neutralCombat()) {
            return;
        }
        if (this.horse == null || this.target == null) {
            return;
        }
        // 续时：敌人又动手了，就把它记下的时刻当作新的起点，倒计时重新开始。
        final long provokedAt = this.tfcicys$provokedAt();
        if (provokedAt > this.tfcicys$aggroStart) {
            this.tfcicys$aggroStart = provokedAt;
        }
        // 时限为 0：亲密度豁免线以上，或者主人报仇被配置关掉。一次都不该追。
        final boolean forbidden = this.tfcicys$aggroWindow == 0;
        final boolean tooLong = this.tfcicys$aggroStart >= 0L && this.tfcicys$aggroWindow > 0
                && this.horse.level().getGameTime() - this.tfcicys$aggroStart > this.tfcicys$aggroWindow;
        final double reach = TFCICYSConfig.retaliateDistance();
        final boolean tooFar = this.horse.distanceToSqr(this.target) > reach * reach;
        if (forbidden || tooLong || tooFar) {
            IHorseData.of(this.horse).bh_setCombatTarget(null);
            this.tfcicys$aggroStart = -1L;
            this.tfcicys$aggroWindow = -1;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "stop()V", at = @At("TAIL"))
    private void tfcicys$forgetAggroStart(final CallbackInfo ci) {
        this.tfcicys$aggroStart = -1L;
        this.tfcicys$aggroWindow = -1;
    }
}
