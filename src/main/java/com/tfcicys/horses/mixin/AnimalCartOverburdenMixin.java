package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tfcastikorcarts.common.entities.carts.TFCAnimalCartEntity;

/**
 * {@link SupplyCartOverburdenMixin} 的动物车版本。
 *
 * <p>{@code TFCAnimalCartEntity} 与 {@code TFCSupplyCartEntity} 各自独立实现了
 * {@code pulledTick()} 与 {@code countOverburdened()}，两者都要处理，
 * 否则用动物车时玩家仍会被施加击倒。
 */
@Mixin(TFCAnimalCartEntity.class)
public abstract class AnimalCartOverburdenMixin {

    @Inject(method = "countOverburdened()F", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfcicys$disableOverburdenEffects(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0F);
    }
}
