package com.tfcicys.horses;

import icy.betterhorses.net.IHorseData;
import icy.betterhorses.net.entity.BhBreedHorse;
import icy.betterhorses.net.registry.BhContent;
import icy.betterhorses.net.registry.GenderType;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps TFC's {@code Gender} and Icy's {@code BH_GENDER_ID} in agreement, so that the two systems can
 * never disagree about an animal.
 * <p>
 * <b>The bug this fixes.</b> Adding {@code tfc:donkey}/{@code tfc:mule}/{@code tfc:horse} to Icy's
 * {@code icys-better-horses:horses} entity tag makes Icy consider them "managed", and managed horses
 * get an Icy gender rolled independently at spawn
 * ({@code AbstractHorseMixin.java:853} / {@code :891}, both
 * {@code random.nextBoolean() ? BhContent.MALE : BhContent.FEMALE}). That roll has nothing to do with
 * TFC's own {@code Gender}. Vanilla breeding is evaluated in both directions -- {@code AnimalMakeLove}
 * asks the <em>partner</em> {@code animal.canMate(initiator)} -- so a TFC donkey that TFC considers male
 * could be seen as female by Icy, and an Icy stallion would happily pair with it.
 * <p>
 * <b>Why mirroring instead of blocking.</b> Making both systems report the same value fixes the cause
 * rather than the symptom: a TFC male donkey is then male to Icy as well, so Icy's own same-sex check
 * (and TFC's) both refuse the pairing. Nothing has to be forbidden, and legitimate opposite-sex pairs
 * keep working exactly as either system intended.
 * <p>
 * <b>Which side is authoritative.</b> Whichever system actually owns the animal's identity:
 * for TFC's native horses/donkeys/mules that is TFC (Icy's roll is the stray value, so it gets
 * overwritten); for Icy's own breed horses it is Icy (the TFC gender added by
 * {@code BhBreedHorseFamiliarityMixin} is the stray value, so it gets overwritten).
 * <p>
 * {@code EntityJoinLevelEvent} is the right hook because it fires after the entity's data is fully
 * populated -- both Icy's {@code readAdditionalSaveData} restore and its {@code finalizeSpawn} roll
 * have already run -- so this always has the last word.
 */
@Mod.EventBusSubscriber(modid = TfcIcysHorses.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GenderSyncHandler {

    private GenderSyncHandler() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Gender is synched data; correcting it server-side propagates to clients on its own.
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof AbstractHorse horse)
                || !(horse instanceof TFCAnimalProperties tfcAnimal)) {
            return;
        }

        final IHorseData data = IHorseData.of(horse);

        if (horse instanceof BhBreedHorse) {
            // Icy's own breed entity: Icy is authoritative, mirror it into TFC.
            final ResourceKey<GenderType> icyGender = data.bh_getGender();
            if (icyGender != null) {
                tfcAnimal.setGender(toTfc(icyGender));
            }
        } else {
            // TFC's native horse/donkey/mule: TFC is authoritative, mirror it into Icy.
            final ResourceKey<GenderType> asIcy = toIcy(tfcAnimal.getGender());
            if (asIcy != null && !asIcy.equals(data.bh_getGender())) {
                data.bh_setGender(asIcy);
            }
        }
    }

    private static TFCAnimalProperties.Gender toTfc(ResourceKey<GenderType> icyGender) {
        final ResourceKey<GenderType> male = BhContent.MALE.getKey();
        return male != null && male.equals(icyGender)
                ? TFCAnimalProperties.Gender.MALE
                : TFCAnimalProperties.Gender.FEMALE;
    }

    private static ResourceKey<GenderType> toIcy(TFCAnimalProperties.Gender gender) {
        return gender == TFCAnimalProperties.Gender.MALE
                ? BhContent.MALE.getKey()
                : BhContent.FEMALE.getKey();
    }
}
