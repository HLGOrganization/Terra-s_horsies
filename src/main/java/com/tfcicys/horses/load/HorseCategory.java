package com.tfcicys.horses.load;

import com.tfcicys.horses.TFCICYSConfig;

import net.dries007.tfc.common.entities.livestock.horse.TFCDonkey;
import net.dries007.tfc.common.entities.livestock.horse.TFCHorse;
import net.dries007.tfc.common.entities.livestock.horse.TFCMule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;

/**
 * 马匹的负重类别。
 *
 * <p>Icy 的 {@code BreedArchetype} 是包私有地挂在 {@code HorseBreed} 上的
 * （{@code builtInArchetype()} 没有 public 修饰符），而 {@code archetype()} 返回的是
 * 注册表对象而不是那个枚举。这里改成按品种 ID 直接映射——映射表来自 Icy 自己的
 * {@code data/icys-better-horses/better_horses/breed/*.json} 的 {@code class} 字段，
 * 是权威来源，也不依赖反射：
 *
 * <pre>
 *   race    → arabian, quarter, thoroughbred
 *   pony    → haflinger, icelandic
 *   western → american_paint, appaloosa, morgan
 *   war     → andalusian, friesian, mustang
 *   draft   → belgian, clydesdale, percheron, shire
 * </pre>
 */
public enum HorseCategory {

    RACE,
    PONY,
    WESTERN,
    WAR,
    DRAFT,
    /** 非马科生物，或不属于以上任何一类的马。 */
    DEFAULT;

    /** 该类别的负重上限，取自配置文件。 */
    public int cap() {
        final TFCICYSConfig.Common c = TFCICYSConfig.COMMON;
        return switch (this) {
            case RACE -> c.raceLoad.get();
            case PONY -> c.ponyLoad.get();
            case WESTERN -> c.westernLoad.get();
            case WAR -> c.warLoad.get();
            case DRAFT -> c.draftLoad.get();
            case DEFAULT -> c.creatureDefaultLoad.get();
        };
    }

    /**
     * 判断实体的负重类别。
     *
     * <p>顺序上先判 TFC 再判原版：{@code TFCHorse} 继承原版 {@code Horse}，
     * 若不先拦下来就会被当成普通马按 western 处理（数值恰好相同，但语义不同）；
     * 而 {@code TFCMule} 与 {@code TFCDonkey} 是兄弟关系，不存在覆盖问题。
     */
    public static HorseCategory of(LivingEntity entity) {
        // ── TFC 本家的马科：用户指定 马=western、驴=pony、骡=draft ──
        if (entity instanceof TFCMule) {
            return DRAFT;
        }
        if (entity instanceof TFCDonkey) {
            return PONY;
        }
        if (entity instanceof TFCHorse) {
            return WESTERN;
        }

        // ── Icy 的十五个品种 ──
        if (entity instanceof AbstractHorse horse && IcyBreedApi.isIcyHorse(horse)) {
            final HorseCategory byBreed = fromBreedId(IcyBreedApi.breedIdOf(horse));
            if (byBreed != DEFAULT) {
                return byBreed;
            }
            // 品种解析不出来时不要直接落 DEFAULT：那会把上限压到 creatureDefaultLoad，
            // 而 DEFAULT 的默认值（1000）远低于任何真实马匹，马会几乎走不动。
            // Icy 的马继承原版 Horse，按 western 给一个合理值远比 1000 安全。
            // 至于正版的原版马／驴／骡，下面那一段会给出各自正确的类别。
        }

        // ── 原版三类 ──
        if (entity instanceof Mule) {
            return DRAFT;
        }
        if (entity instanceof Donkey) {
            return PONY;
        }
        if (entity instanceof Horse) {
            return WESTERN;
        }

        return DEFAULT;
    }

    private static HorseCategory fromBreedId(String breedId) {
        if (breedId == null || breedId.isEmpty()) {
            return DEFAULT;
        }
        // 规范化：去掉可能的命名空间前缀，统一小写。
        // Icy 的 HorseBreed.id() 目前是 name().toLowerCase()，确实是纯小写。
        // 这里多一道防线，是为了避免日后它改用 ResourceLocation 形式
        // （"icys-better-horses:belgian"）时，整张映射表静默失效 ——
        // 那种失败不会报错，只会让所有马匹悄悄落回 creatureDefaultLoad，
        // 表现为「配置改了却不起作用」，极难排查。
        String key = breedId;
        final int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        key = key.toLowerCase(java.util.Locale.ROOT);

        return switch (key) {
            case "arabian", "quarter", "thoroughbred" -> RACE;
            case "haflinger", "icelandic" -> PONY;
            case "american_paint", "appaloosa", "morgan" -> WESTERN;
            case "andalusian", "friesian", "mustang" -> WAR;
            case "belgian", "clydesdale", "percheron", "shire" -> DRAFT;

            // Icy 的非品种条目。它们不是可繁育的品种，但仍然是马科动物，
            // 应当按最接近的类别给一个合理上限，而不是落回 creatureDefaultLoad。
            case "donkey_species" -> PONY;
            case "mule_species" -> DRAFT;

            default -> DEFAULT;
        };
    }

    /** 挽马：拉车负重额外降到 40%，且骑手自身体重取 300 而非 350。 */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * 取负重上限时，TFC 三类要走自己的配置项而不是枚举默认值，
     * 因为它们有独立的键。
     */
    public static int capFor(LivingEntity entity) {
        final TFCICYSConfig.Common c = TFCICYSConfig.COMMON;
        if (entity instanceof TFCMule) {
            return c.tfcMuleLoad.get();
        }
        if (entity instanceof TFCDonkey) {
            return c.tfcDonkeyLoad.get();
        }
        if (entity instanceof TFCHorse) {
            return c.tfcHorseLoad.get();
        }
        return of(entity).cap();
    }
}
