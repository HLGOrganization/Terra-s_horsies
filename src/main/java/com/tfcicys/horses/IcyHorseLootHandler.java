package com.tfcicys.horses;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives every Icy horse TFC's horse drops.
 * <p>
 * <b>What Icy ships today.</b> All fifteen Icy horses have their own loot table under
 * {@code data/icys-better-horses/loot_tables/entities/}, and all fifteen are byte-for-byte identical
 * (verified by MD5 against the 2.0.6 jar). Each one is nothing but a reference:
 * <pre>
 * { "pools": [ { "rolls": 1, "entries": [
 *     { "type": "minecraft:loot_table", "name": "minecraft:entities/horse" } ] } ] }
 * </pre>
 * So an Icy horse currently drops the <em>vanilla</em> horse table (leather and bones), while TFC's own
 * horse, donkey and mule all drop {@code tfc:food/horse_meat}, {@code tfc:medium_raw_hide} and bones.
 * <p>
 * <b>Why an event and not a datapack file.</b> Re-shipping
 * {@code data/icys-better-horses/loot_tables/entities/<horse>.json} from this mod would put two
 * identical paths on the resource stack, and which one wins depends purely on mod load order -- a
 * silent breakage the moment anything reorders. {@code LootTableLoadEvent} is deterministic: it fires
 * once per table as it is loaded, and whatever table is set here is the table that gets used.
 * <p>
 * <b>Why a reference instead of copying TFC's JSON.</b> Pointing at {@code tfc:entities/horse} means the
 * drops stay correct if TFC ever changes them, and no TFC data is duplicated into this addon. The
 * {@code tfc:animal_yield} count provider inside that table works for Icy horses as-is, because
 * {@code BhBreedHorseFamiliarityMixin} makes them implement {@code TFCAnimalProperties}; the provider
 * tests {@code instanceof TFCAnimalProperties} and falls back to the minimum count otherwise
 * ({@code AnimalYieldProvider:32-51}).
 * <p>
 * One consequence worth knowing: yield scales with {@code getGeneticSize()} and {@code getFamiliarity()}.
 * TFC's synched-data default for size is 16 ({@code TFCAnimalProperties:255}), comfortably inside TFC's
 * natural 4-18 range, so an un-bred Icy horse drops a normal-looking amount rather than nothing.
 */
@Mod.EventBusSubscriber(modid = TfcIcysHorses.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IcyHorseLootHandler {

    /** TFC's own horse table. TFC's horse, donkey and mule tables are identical, so one is enough. */
    private static final ResourceLocation TFC_HORSE_LOOT = new ResourceLocation("tfc", "entities/horse");

    /**
     * The fifteen horse types registered in {@code ModEntities.java:48-151}. Listed explicitly rather
     * than matched by namespace: {@code horse_cart} lives in the same namespace and directory but is a
     * {@code MobCategory.MISC} vehicle, not a horse, and must keep its own drops.
     */
    private static final Set<ResourceLocation> ICY_HORSE_LOOT_TABLES = Set.of(
            id("icelandic_horse"),
            id("friesian_horse"),
            id("haflinger_horse"),
            id("percheron_horse"),
            id("shire_horse"),
            id("belgian_horse"),
            id("clydesdale_horse"),
            id("appaloosa_horse"),
            id("thoroughbred_horse"),
            id("american_paint_horse"),
            id("andalusian_horse"),
            id("mustang_horse"),
            id("quarter_horse"),
            id("arabian_horse"),
            id("morgan_horse"));

    private IcyHorseLootHandler() {}

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!ICY_HORSE_LOOT_TABLES.contains(event.getName())) {
            return;
        }
        event.setTable(LootTable.lootTable()
                // Must be set explicitly: LootTable.Builder starts on LootContextParamSets.EMPTY, and
                // TFC's tfc:animal_yield inside the referenced table reads LootContextParams.THIS_ENTITY
                // to scale the meat count by the animal's size and familiarity (AnimalYieldProvider:30).
                .setParamSet(LootContextParamSets.ENTITY)
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootTableReference.lootTableReference(TFC_HORSE_LOOT)))
                .build());
    }

    private static ResourceLocation id(String path) {
        // Icy registers its content under the hyphenated resource namespace, which differs from its
        // underscore mod id (mods.toml: modId="icys_better_horses").
        return new ResourceLocation("icys-better-horses", "entities/" + path);
    }
}
