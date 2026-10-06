package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tfcicys.horses.TFCICYSConfig;

import net.minecraft.world.item.ItemStack;
import tfcastikorcarts.common.entities.carts.TFCSupplyCartEntity;

/**
 * 接管 TFC 补给马车的两条「上游限制」。
 *
 * <p>第一条：移除「过载 → 击倒」行为。
 * TFCAstikorCarts 在 {@code pulledTick()} 里拿 {@code countOverburdened()} 的返回值
 * 和三个阈值比较，超过就依次施加 TFC 的 PINNED（击倒）、OVERBURDENED（过载）、
 * EXHAUSTED（力竭）效果。把这里的返回值固定为 0，三个分支就都进不去。
 * 之所以不直接在 {@code pulledTick} 上 cancel，是因为那个方法第一行就是
 * {@code super.pulledTick()}，整段取消会连带破坏 AstikorCarts 的物理与乘客更新。
 * 改这一个取值点既最小侵入，又让 TFC 的整条过载判定自然失效，
 * 把负重的话语权交给 More Attributes。
 *
 * <p>第二条：解除车厢的**物品尺寸上限**，见 {@link #tfcicys$allowAnyItemSize}。
 */
@Mixin(TFCSupplyCartEntity.class)
public abstract class SupplyCartOverburdenMixin {

    @Inject(method = "countOverburdened()F", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfcicys$disableOverburdenEffects(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0F);
    }

    /**
     * 解除车厢的「物品尺寸上限」。
     *
     * <p>{@code TFCSupplyCartEntity.isValid(ItemStack)} 是尺寸限制的<b>唯一判据</b>：
     * {@code ItemSizeManager.get(stack).getSize().isEqualOrSmallerThan(config.maxItemSize)}。
     * 两个槽位实现（{@code SupplyCartContainer$RestrictedSlotItemHandler} 与
     * {@code CartContainer$RestrictedSlot} 的 {@code mayPlace}）以及
     * {@code isItemValid(int, ItemStack)}（漏斗、管道这类自动化入口）全都调它，
     * 所以只在这一处放行就能全局生效。
     *
     * <p>为什么可以放行：尺寸限制原本是给「装不下」一个上限，
     * 而现在负重已经由 More Attributes 的重量体系接管（装了就会被压慢、压趴），
     * 再叠一层尺寸限制就是重复约束。可在配置里用
     * {@code load.cart.cartIgnoresItemSize = false} 复原。
     *
     * <p>目标是 {@code static} 方法，所以处理器也必须是 {@code static}（Mixin 的硬性要求）。
     */
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tfcicys$allowAnyItemSize(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (TFCICYSConfig.cartIgnoresItemSize()) {
            cir.setReturnValue(true);
        }
    }
}
