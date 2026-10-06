package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tfcicys.horses.load.WhistleRelease;

import icy.betterhorses.net.HorsePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * Icy 真正把马「挪过去」的那一步：{@code HorsePlacement.teleport(马, 位置)}。
 *
 * <p>哨子路径里它只在距离超过 1024 格时被调用（近处只是下个跟随指令、马自己走过去），
 * 而「车直线飞过来」正是发生在被真正传送的时候。挂载它而不是挂 {@code whistle} 的原因：
 * 哨子在「没有羁绊 / 不安全落点 / Icy 自己的车」这些情况下会失败，
 * 那种时候不该把玩家的车解挂——挂在真正执行传送的这一处，语义最准。
 *
 * <p>「送回马厩」也走这个传送入口，同样受益：任何把马瞬移走的操作，
 * 都不该顺手把一辆车厢也拖过去。
 *
 * <p>目标是 {@code static}，处理器也必须是 {@code static}（Mixin 的硬性要求）。
 */
@Mixin(HorsePlacement.class)
public abstract class HorsePlacementReleaseMixin {

    @Inject(method = "teleport", at = @At("HEAD"), remap = false)
    private static void tfcicys$releaseBeforeTeleport(AbstractHorse horse, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        WhistleRelease.release(horse);
    }
}
