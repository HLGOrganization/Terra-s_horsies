package com.tfcicys.horses.mixin;

import net.dries007.tfc.common.TFCTags;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Requirement 3: let Icy / vanilla horses (and donkeys, mules, and every Icy breed entity)
 * accept items carrying TFC's horse food tag.
 * <p>
 * TFC's own animals never reach this code path: {@code TFCHorse.isFood} and
 * {@code TFCChestedHorse.isFood} call the {@code TFCAnimalProperties} interface default directly and
 * never call {@code super}. Those are covered by the datapack bridge that appends our tag into
 * {@code tfc:horse_food} instead.
 * <p>
 * The injection targets {@code RETURN} rather than {@code HEAD} so the vanilla behaviour (golden
 * apples and whatever the platform's hardcoded food ingredient contains) is fully preserved, and so
 * this stays composable with other mods that also extend {@code isFood}.
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseFoodMixin {

    @Inject(
            method = "isFood(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void tfcicys$acceptHorseFood(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && !stack.isEmpty() && stack.is(TFCTags.Items.HORSE_FOOD)) {
            cir.setReturnValue(true);
        }
    }
}
