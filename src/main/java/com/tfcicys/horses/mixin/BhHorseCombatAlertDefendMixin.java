package com.tfcicys.horses.mixin;

import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.ai.FrightenedHorse;

import icy.betterhorses.net.BhHorseCombatAlert;
import icy.betterhorses.net.IHorseData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「替主人报仇」的开关 —— 默认<b>放行</b> Icy 的原行为，只有配置关掉时才取消。
 *
 * <p>Icy 的 {@code PlayerHurtMixin} 在玩家受伤时调用
 * {@code BhHorseCombatAlert.rouse(level, player, attacker)}；{@code rouse} 遍历
 * {@code HorseTracker} 里 16 格内、主人是该玩家、且 bond ≥ 1 的马，对未骑乘的调用
 * {@code defend(data, attacker)}，而 {@code defend} 是<b>整个模组里唯一</b>会写
 * {@code bh_setCombatTarget} 的地方（已反编译核对全部 369 个类）：
 *
 * <pre>
 * private static void defend(IHorseData data, LivingEntity attacker) {
 *     if (!HorseCombat.mayTarget(attacker)) return;
 *     if (BhHorseTrait.bondTier(data.bh_getBond()) &lt; 1) return;
 *     if (data.bh_getCommand() == COMMAND_STAY) return;
 *     if (data.bh_getCombatTarget() != null) return;
 *     data.bh_setCombatTarget(attacker.getUUID());   // ← 目标一写下去，DefendOwnerGoal 就开始追
 * }
 * </pre>
 *
 * <p>写进目标之后，Icy 的 {@code DefendOwnerGoal} 会以 1.35（远距离 1.7）的速度追击，
 * 6 格内冲锋撞击、之后每 12 tick 打一次。这就是「马会替主人报仇」。
 *
 * <h2>默认不动它</h2>
 *
 * <p>这是 Icy 的原版行为，也是马匹<b>唯一</b>会主动攻击的来源 —— 属于需求里明确要保留的
 * 功能，所以出厂放行。要「完全中立」时才把它关掉（{@code owner_defend.ownerDefend = false}），
 * 那时马就只剩自身被打时的反击（见 {@link HorseCombatRetaliateMixin}）。
 *
 * <p>刻意<b>不</b>去关掉 Icy 配置里的 {@code horse_defend}：那个开关会让
 * {@code DefendOwnerGoal.m_8036_()} 直接返回 false，连反击要复用的追击逻辑一起废掉。
 * 这里只掐掉「设目标」这一步，追击能力保留给反击用。
 *
 * <p>{@code rouse} 里另一条分支（玩家正骑着 → {@code rollSpook}）不受影响：
 * 那是受惊、不是攻击。
 *
 * <h2>顺便记一笔「主人被谁打了」</h2>
 *
 * <p>仇恨要<b>续时</b>：敌人还在打主人（或打马自己），仇恨计时就一次次往后推，
 * 停手满 {@code ownerDefendSeconds}（默认 10 秒）才脱战。所以这里每次都要记，
 * 而不是只在开打时记一次。记在马的威胁记忆里，由
 * {@link FrightenedHorse#tfcicys$provokedAt(net.minecraft.world.entity.LivingEntity)} 读取。
 *
 * <p>参数 {@code data} 就是马实体本身，可以直接 {@code instanceof} 判断：
 * {@code IHorseData.of} 编译出来是 {@code aload_0; checkcast IHorseData; areturn}。
 *
 * <p>处理器必须是 {@code private static}：目标 {@code defend} 是静态方法，
 * 修饰符不匹配会在加载期抛 {@code InvalidInjectionException}。
 * {@code remap = false} 是因为 {@code defend} 是 Icy 自己加的方法，
 * 不是原版覆写，没有混淆映射。
 */
@Mixin(value = BhHorseCombatAlert.class, remap = false)
public abstract class BhHorseCombatAlertDefendMixin {

    @Inject(
            method = "defend(Licy/betterhorses/net/IHorseData;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void tfcicys$gateOwnerDefend(final IHorseData data, final LivingEntity attacker,
                                                final CallbackInfo ci) {
        if (!TFCICYSConfig.ownerDefend()) {
            ci.cancel();
            return;
        }
        if (data instanceof FrightenedHorse frightened) {
            frightened.tfcicys$noteOwnerThreat(attacker);
        }
    }
}
