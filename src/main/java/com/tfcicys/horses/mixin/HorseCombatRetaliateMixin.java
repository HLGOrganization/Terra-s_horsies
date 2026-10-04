package com.tfcicys.horses.mixin;

import com.tfcicys.horses.TFCICYSConfig;

import icy.betterhorses.net.IHorseData;
import icy.betterhorses.net.feature.HorseCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 被打时反击 —— 中立化的另一半。
 *
 * <p>Icy 原本的 {@code HorseCombat.onHurt} 只做一件事：<b>从背后</b>被打时踢一记
 * （要求攻击方向与马朝向的点积 ≤ -0.3，也就是攻击者大致在马屁股后面），
 * 然后若当前没有战斗目标就受惊 60 tick。它<b>不会</b>让马去追打攻击者 ——
 * 也就是说原版 Icy 的马被打之后并不会「反击」，只会原地踢一下。
 *
 * <p>这里在 {@code onHurt} 的 HEAD 把攻击者写成 {@code bh_setCombatTarget}，
 * 于是 Icy 自带的 {@code DefendOwnerGoal} 会替我们完成追击 + 冲锋撞击 + 挥击，
 * 脱战条件由 {@link DefendOwnerGoalAggroMixin} 补上（时间 + 距离）。
 *
 * <p>放在 HEAD 而不是 TAIL：{@code onHurt} 有多条提前 return（没开 {@code horse_kick}、
 * 不是 Icy 品种、攻击者不是活体、是主人、方向不对……），TAIL 只在最后一条 return 前触发，
 * 会漏掉「从正面被打」这种情况。中立反击不该挑方向。
 *
 * <p>只对 Icy 自己的品种生效（{@code bh_getBreedKey() != null}）：
 * TFC 本家的马／驴／骡有它们自己的一套行为，不在这里插手。
 */
@Mixin(value = HorseCombat.class, remap = false)
public abstract class HorseCombatRetaliateMixin {

    @Inject(
            method = "onHurt(Lnet/minecraft/world/entity/animal/horse/AbstractHorse;Licy/betterhorses/net/IHorseData;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"),
            remap = false)
    private void tfcicys$retaliate(final AbstractHorse horse, final IHorseData data,
                                   final DamageSource source, final CallbackInfo ci) {
        if (!TFCICYSConfig.neutralCombat()) {
            return;
        }
        if (data.bh_getBreedKey() == null) {
            return;
        }
        // 有人骑着时不自动索敌：DefendOwnerGoal 自己也会拒绝上马的目标，
        // 但目标写下去没人清，会一直留到玩家下马，所以干脆不写。
        if (horse.isVehicle()) {
            return;
        }
        final Entity raw = source.getEntity();
        if (!(raw instanceof LivingEntity attacker) || attacker == horse) {
            return;
        }
        if (horse.isPassengerOfSameVehicle(attacker)) {
            return;
        }
        // 默认<b>不</b>排除主人：亲密度只有喂自己的马才能涨，若主人免疫，
        // 下面那条亲密度反击曲线就永远走不到了。想恢复「不咬主人」的行为，
        // 把配置里的 neverRetaliateAgainstOwner 打开即可。
        if (TFCICYSConfig.neverRetaliateAgainstOwner()
                && data.bh_isOwned()
                && data.bh_mayHandle(attacker.getUUID())) {
            return;
        }
        // 复用 Icy 自己的过滤：关掉 PvP 时不打玩家，也不打别人拥有的生物。
        if (!HorseCombat.mayTarget(attacker)) {
            return;
        }
        // 亲密度够高就不反击玩家 —— 直接不写目标，比「写下去再靠 Goal 立刻放弃」干净。
        // aggroTicks 对非玩家攻击者永远返回固定值（默认 30 秒），所以只有打玩家的那一路会命中 0。
        if (TFCICYSConfig.aggroTicks(attacker, TFCICYSConfig.familiarityOf(horse)) <= 0) {
            return;
        }
        data.bh_setCombatTarget(attacker.getUUID());
    }
}
