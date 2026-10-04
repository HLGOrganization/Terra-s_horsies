package com.tfcicys.horses.mixin;

import icy.betterhorses.net.client.BhHorseHud;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables Icy's hunger bar overlay while riding.
 * <p>
 * {@code BhHorseHud.hungerOnHorseback} re-draws the vanilla food bar below the crosshair whenever the
 * player is mounted on a horse, which duplicates TFC's own hunger bar and leaves two of them on screen.
 * Cancelling at HEAD means the overlay is never drawn; nothing else about riding changes.
 * <p>
 * Only that one method is touched. Icy's experience bar over the jump bar
 * ({@code BhHorseHud.experience}) and the horse stat readout ({@code BhHorseHud.render}) are left
 * alone, so this does not disable unrelated Icy HUD features.
 * <p>
 * This is a client-only mixin: it is listed in the {@code client} array of the mixin config, so a
 * dedicated server never tries to load it (the target class references client-only types).
 */
@Mixin(BhHorseHud.class)
public abstract class BhHorseHudMixin {

    // remap = false is required: hungerOnHorseback is a method Icy *added*, not a vanilla override, so
    // it has no SRG mapping for the annotation processor to resolve. Mod-authored method names are not
    // obfuscated at runtime, so the literal name is correct in both dev and production.
    //
    // The handler MUST be static, because the target is static too -- Mixin enforces that the handler's
    // 'static' modifier matches the target's, and fails at APPLY time with
    // "'static' modifier of handler method does not match target" otherwise.
    @Inject(
            method = "hungerOnHorseback(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void tfcicys$disableHungerOverlay(GuiGraphics gfx, int width, int height, CallbackInfo ci) {
        ci.cancel();
    }
}
