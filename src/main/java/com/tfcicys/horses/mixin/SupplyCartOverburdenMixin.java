package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import tfcastikorcarts.common.entities.carts.TFCSupplyCartEntity;

/**
 * 移除 TFC 补给马车的「过载 → 击倒」行为。
 *
 * <p>TFCAstikorCarts 在 {@code pulledTick()} 里拿 {@code countOverburdened()} 的返回值
 * 和三个阈值比较，超过就依次施加 TFC 的 PINNED（击倒）、OVERBURDENED（过载）、
 * EXHAUSTED（力竭）效果。把这里的返回值固定为 0，三个分支就都进不去。
 *
 * <p>之所以不直接在 {@code pulledTick} 上 cancel，是因为那个方法第一行就是
 * {@code super.pulledTick()}，整段取消会连带破坏 AstikorCarts 的物理与乘客更新。
 * 改这一个取值点既最小侵入，又让 TFC 的整条过载判定自然失效，
 * 把负重的话语权交给 More Attributes。
 */
@Mixin(TFCSupplyCartEntity.class)
public abstract class SupplyCartOverburdenMixin {

    @Inject(method = "countOverburdened()F", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfcicys$disableOverburdenEffects(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0F);
    }
}
