package com.tfcicys.horses.mixin;

import icy.betterhorses.net.client.BhInventoryEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the player's potion-effect panel that Icy draws beside the horse inventory.
 * <p>
 * Icy's {@code HorseInventoryScreenMixin} appends {@code BhInventoryEffects.render(...)} to the screen's
 * {@code render} method, which draws the player's active mob effects in a column to the right of the
 * container. That is Icy's own mixin, so it cannot be un-applied from here; instead this cancels the
 * method it delegates to, at HEAD, so the panel is simply never drawn.
 * <p>
 * Only the effect panel is suppressed -- the horse inventory itself, its gear panel, stat lines and
 * bond star all render as usual.
 * <p>
 * Client-only: listed in the {@code client} array of the mixin config, since the target class
 * references client-only types.
 */
@Mixin(BhInventoryEffects.class)
public abstract class BhInventoryEffectsMixin {

    // remap = false is required: render is a method Icy *added*, not a vanilla override, so it has no
    // SRG mapping for the annotation processor to resolve. Mod-authored method names are not obfuscated
    // at runtime, so the literal name is correct in both dev and production.
    //
    // The handler MUST be static, because the target is static too -- Mixin enforces that the handler's
    // 'static' modifier matches the target's, and fails at APPLY time otherwise.
    @Inject(
            method = "render(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;II)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void tfcicys$disableEffectPanel(AbstractContainerScreen<?> screen, GuiGraphics gfx, Font font,
                                                   int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
    }
}
