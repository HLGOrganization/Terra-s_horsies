package com.tfcicys.horses.mixin;

import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.ai.FrightenedHorse;
import com.tfcicys.horses.util.CactusBlocks;

import icy.betterhorses.net.entity.BhBreedHorse;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Icy 马匹的两个仙人掌行为，共用同一次 {@code hurt} 拦截：
 *
 * <ol>
 *   <li><b>穿马铠免疫刺伤</b> —— 配置 {@code armor.cactusProofArmor}。</li>
 *   <li><b>野马被扎就走开</b> —— 配置 {@code neutral_combat.flee.fleeFromCactus}。
 *       原版马不会躲仙人掌：走到旁边只会站着被扎死。</li>
 * </ol>
 *
 * <h2>为什么挂在 {@code hurt} 上</h2>
 *
 * <p>原版仙人掌的伤害路径只有一条：
 *
 * <pre>
 * CactusBlock.entityInside(state, level, pos, entity)   // m_7892_
 *   → level.damageSources().cactus()                    // DamageSources.m_269325_
 *   → entity.hurt(source, 1.0F)                         // m_6469_
 * </pre>
 *
 * <p>所以拦截点选在 {@code hurt}：既能认伤害类型，也能顺带覆盖其他模组的仙人掌 ——
 * 只要它们的伤害类型能被 {@link CactusBlocks#isCactusDamage} 认出来。
 *
 * <h2>逃跑：只挪一下，不跑远</h2>
 *
 * <p>这里只往 {@link FrightenedHorse} 里写「要远离哪个点 + 最多挪几格」，
 * 移动交给 {@code FleeFromThreatGoal}。两个数值是<b>故意给小</b>的：
 *
 * <ul>
 *   <li><b>距离</b> {@code cactusFleeDistance}（默认 4 格）—— 仙人掌是<b>成丛</b>长的，
 *       按通用逃跑那套冲刺十几格，马很容易一头扎进旁边另一丛里继续挨扎。
 *       所以这里要的是「挪出这一丛」，不是「跑得越远越好」。</li>
 *   <li><b>速度</b> {@code cactusFleeSpeed}（默认 1.2，走路而不是冲刺）—— 同上，
 *       1.2 才不会一步跨进下一丛。</li>
 * </ul>
 *
 * <p>更关键的是<b>收尾方式</b>：逃跑状态由 {@code tfcicys$fleePoint()} 决定何时失效，
 * 而仙人掌那一档会在「身上和紧邻都没有类仙人掌方块」时立刻返回 {@code null} ——
 * 也就是说马一旦真的脱离了那丛仙人掌就停下，而不是傻跑完 4 格。
 * 万一一头扎进另一丛，下一次扎刺会重新触发一次短逃跑，如此逐步挪出去。
 *
 * <h2>为什么逃跑判定放在免疫判定之前</h2>
 *
 * <p>穿了马铠的马掉不了血，但「站在仙人掌里不动」本身就是不该发生的事，
 * 所以免疫的马也让它走开。两者互相独立：关掉免疫不影响逃跑，反之亦然。
 *
 * <h2>作用范围</h2>
 *
 * <p>两个行为都只对 Icy 自己的品种生效（{@code instanceof BhBreedHorse}）。
 * 原版马、TFC 的马／驴／骡、羊驼都不受影响。想扩大范围就把那一行 {@code instanceof} 删掉，
 * 但逃跑还需要目标类实现了 {@link FrightenedHorse}，目前只有 Icy 的品种有。
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseCactusMixin {

    @Inject(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void tfcicys$onCactusHurt(final DamageSource source, final float amount,
                                      final CallbackInfoReturnable<Boolean> cir) {
        final boolean proofArmor = TFCICYSConfig.cactusProofArmor();
        final boolean fleeAway = TFCICYSConfig.fleeFromCactus();
        if (!proofArmor && !fleeAway) {
            return;
        }
        final AbstractHorse self = (AbstractHorse) (Object) this;
        if (!(self instanceof BhBreedHorse)) {
            return;
        }
        // 先判伤害类型：绝大多数 hurt 调用在这里就返回了，字符串比较只在仙人掌伤害时发生。
        if (!CactusBlocks.isCactusDamage(source)) {
            return;
        }

        // ① 野马 → 挪开。短距离 + 走速，见类注释。
        if (fleeAway
                && !self.level().isClientSide
                && TFCICYSConfig.isWildHorse(self)
                && self instanceof FrightenedHorse frightened) {
            // 起点优先取「身上那株仙人掌」的方块中心，这样是朝反方向挪，脱离最快。
            // 找不到具体方块时退回「离开当前位置」—— 这条路不依赖任何方块识别，
            // 所以名字里没有 cactus、也没继承 CactusBlock 的异界仙人掌一样兜得住。
            final Vec3 threat = CactusBlocks.findNearest(self);
            frightened.tfcicys$startFleeingLocal(
                    threat != null ? threat : self.position(),
                    TFCICYSConfig.cactusFleeTicks(),
                    TFCICYSConfig.cactusFleeDistance(),
                    TFCICYSConfig.cactusFleeSpeed());
        }

        // ② 穿马铠 → 免疫。与 isInvulnerableTo 一样，返回 false 表示「没受伤」：
        //    不掉血、不出音效、不播动画。
        if (proofArmor && self.isWearingArmor()) {
            cir.setReturnValue(false);
        }
    }
}
