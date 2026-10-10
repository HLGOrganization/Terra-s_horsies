package com.tfcicys.horses.mixin;

import java.util.UUID;

import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.ai.FleeFromThreatGoal;
import com.tfcicys.horses.ai.FrightenedHorse;
import com.tfcicys.horses.util.CactusBlocks;
import icy.betterhorses.net.IHorseData;
import icy.betterhorses.net.entity.BhBreedHorse;
import icy.betterhorses.net.registry.BhContent;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.capabilities.food.DynamicBowlHandler;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.dries007.tfc.common.entities.EntityHelpers;
import net.dries007.tfc.common.entities.livestock.CommonAnimalData;
import net.dries007.tfc.common.entities.livestock.MammalProperties;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.dries007.tfc.common.entities.livestock.horse.HorseProperties;
import net.dries007.tfc.common.entities.livestock.horse.TFCChestedHorse;
import net.dries007.tfc.common.entities.livestock.horse.TFCDonkey;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.config.animals.AnimalConfig;
import net.dries007.tfc.config.animals.MammalConfig;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Requirements 2 + 4: give Icy's horses the same familiarity, pregnancy and mating mechanics
 * TFC's own livestock uses.
 * <p>
 * <b>On the Icy dependency:</b> Icy's Better Horses is a {@code compileOnly} dependency, so the mixin
 * annotation processor can resolve the target class and its inherited method descriptors. Nothing from
 * Icy is copied into or bundled with this addon -- the code below only calls <em>vanilla</em> and
 * <em>TFC</em> APIs. {@code mods.toml} declares Icy as a mandatory dependency, which is what lets a
 * compile-time-bound mixin target be safe.
 * <p>
 * <b>Why {@code HorseProperties} and not {@code TFCAnimalProperties}:</b> TFC's own Jade integration
 * ({@code EntityTooltips.ANIMAL}) gates its extra lines on {@code instanceof} checks against
 * {@code TFCAnimalProperties}, {@code MammalProperties} and {@code HorseProperties}. Implementing the
 * most derived one ({@code HorseProperties extends MammalProperties extends TFCAnimalProperties})
 * satisfies all three, which is what makes sex / familiarity / can-mate / pregnancy / age / can-ride
 * appear for Icy horses exactly as they do for TFC's.
 * <p>
 * <b>Why {@code BhBreedHorse} and not {@code AbstractHorse}:</b> attaching this to {@code AbstractHorse}
 * would give familiarity to vanilla horses <em>and</em> to TFC's own {@code TFCHorse}/
 * {@code TFCChestedHorse}, which already implement {@code HorseProperties}. TFC's animals run
 * {@code tickAnimalData()} from their own {@code tick()}, so they would decay twice.
 * <p>
 * Only the abstract members are implemented; everything else (feeding, decay, growth, NBT, sync
 * registration, gestation, gene inheritance) is reused from TFC's default methods, so the numbers stay
 * identical to TFC by construction: +0.06 per feeding, at most one feeding per TFC day, -0.02 per day
 * only below the decay limit, adult cap from TFC's own {@code horseConfig}.
 */
@Mixin(BhBreedHorse.class)
public abstract class BhBreedHorseFamiliarityMixin extends Horse implements HorseProperties, FrightenedHorse {

    @Unique private static final EntityDataAccessor<Boolean> TFCICYS_GENDER =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.BOOLEAN);
    @Unique private static final EntityDataAccessor<Long> TFCICYS_BIRTHDAY =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.LONG);
    @Unique private static final EntityDataAccessor<Float> TFCICYS_FAMILIARITY =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Integer> TFCICYS_USES =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.INT);
    @Unique private static final EntityDataAccessor<Boolean> TFCICYS_FERTILIZED =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.BOOLEAN);
    @Unique private static final EntityDataAccessor<Long> TFCICYS_OLD_DAY =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.LONG);
    @Unique private static final EntityDataAccessor<Integer> TFCICYS_GENETIC_SIZE =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.INT);
    @Unique private static final EntityDataAccessor<Long> TFCICYS_LAST_FED =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.LONG);

    /**
     * TFC syncs the pregnancy start day on every mammal (see {@code TFCHorse.PREGNANT_TIME}), because
     * Jade reads it on the client to draw the gestation countdown. Same deal here.
     */
    @Unique private static final EntityDataAccessor<Long> TFCICYS_PREGNANT_TIME =
            SynchedEntityData.defineId(BhBreedHorse.class, EntityDataSerializers.LONG);

    /** Accessor bundle handed back to TFC's default methods. Field order matches the record. */
    @Unique private static final CommonAnimalData TFCICYS_ANIMAL_DATA = new CommonAnimalData(
            TFCICYS_GENDER,
            TFCICYS_BIRTHDAY,
            TFCICYS_FAMILIARITY,
            TFCICYS_USES,
            TFCICYS_FERTILIZED,
            TFCICYS_OLD_DAY,
            TFCICYS_GENETIC_SIZE,
            TFCICYS_LAST_FED);

    /**
     * Speed lost at 100% wear. Applied as {@code MULTIPLY_TOTAL = -0.3}, i.e. the horse keeps
     * {@code 1 - 0.3 = 70%} of its speed -- the figure requested for a fully worn-out horse.
     */
    @Unique private static final float MAX_AGING_SPEED_LOSS = 0.3F;

    /**
     * Stable identity for this addon's aging modifier. Fixed rather than random so the previous
     * cycle's modifier can always be found and replaced when the wear value changes.
     */
    @Unique private static final UUID TFCICYS_AGING_SPEED_ID =
            UUID.fromString("b7f4c1a2-3d5e-4f6a-8b9c-0d1e2f3a4b5c");

    /**
     * Speed lost by a foal when its parents are at 100% wear, on top of whatever it would otherwise
     * have inherited: {@code 0.15} means the foal comes out at 85% of that value.
     * <p>
     * Kept milder than {@link #MAX_AGING_SPEED_LOSS} on purpose -- this one compounds across
     * generations rather than applying to a single animal.
     */
    @Unique private static final float MAX_INHERITED_SPEED_LOSS = 0.15F;

    /** Key storing the sire's wear inside TFC's persisted genes tag. See {@link #tfcicys$tfcBreeding}. */
    @Unique private static final String TFCICYS_SIRE_WEAR = "tfcicys_sire_wear";

    /** Deliberately not synced, exactly like TFC: decay is server-only (see TFCAnimalProperties:136). */
    @Unique private long tfcicys$lastFDecay = -1L;
    @Unique private long tfcicys$mated = 0L;
    @Unique private Age tfcicys$lastAge = Age.CHILD;

    /** Genes are server-side only in TFC too -- they are persisted but never synced. */
    @Unique @Nullable private CompoundTag tfcicys$genes = null;

    protected BhBreedHorseFamiliarityMixin(EntityType<? extends Horse> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------- required members

    @Override
    public CommonAnimalData animalData() {
        return TFCICYS_ANIMAL_DATA;
    }

    @Override
    public AnimalConfig animalConfig() {
        return TFCConfig.SERVER.horseConfig.inner();
    }

    @Override
    public MammalConfig getMammalConfig() {
        return TFCConfig.SERVER.horseConfig;
    }

    @Override
    public long getLastFamiliarityDecay() {
        return tfcicys$lastFDecay;
    }

    @Override
    public void setLastFamiliarityDecay(long days) {
        tfcicys$lastFDecay = days;
    }

    @Override
    public void setMated(long time) {
        tfcicys$mated = time;
    }

    @Override
    public long getMated() {
        return tfcicys$mated;
    }

    @Override
    public Age getLastAge() {
        return tfcicys$lastAge;
    }

    @Override
    public void setLastAge(Age age) {
        tfcicys$lastAge = age;
    }

    @Override
    public TagKey<Item> getFoodTag() {
        return TFCTags.Items.HORSE_FOOD;
    }

    @Override
    public long getPregnantTime() {
        return this.entityData.get(TFCICYS_PREGNANT_TIME);
    }

    @Override
    public void setPregnantTime(long day) {
        this.entityData.set(TFCICYS_PREGNANT_TIME, day);
    }

    @Override
    @Nullable
    public CompoundTag getGenes() {
        return tfcicys$genes;
    }

    @Override
    public void setGenes(@Nullable CompoundTag tag) {
        tfcicys$genes = tag;
    }

    // ---------------------------------------------------------------- behaviour

    /**
     * TFC's four food-related defaults (isFood / isHungry / eatFood / TFC's own mobInteract branch)
     * must be able to see our tag. The concrete {@code isFood} inherited from {@code Horse} would
     * otherwise shadow the interface default, which is exactly the trap TFC itself avoids by
     * overriding {@code isFood} in every animal class.
     */
    @Override
    public boolean isFood(ItemStack stack) {
        if (super.isFood(stack)) {
            return true;
        }
        return (eatsRottenFood() || !FoodCapability.isRotten(stack))
                && stack.is(getFoodTag());
    }

    /**
     * 喂食分两套互不影响的机制。
     *
     * <ol>
     *   <li><b>回血吃东西</b>（{@link #tfcicys$eatToHeal}）：已驯服 + 血量未满 → 只回血。
     *       不给亲密度、不动 {@code lastFed}（所以不消耗当天的正餐额度）、不触发求偶爱心。</li>
     *   <li><b>每日正餐</b>（TFC 的 {@code eatFood}）：按 TFC 规则 +0.06 亲密度、设置
     *       {@code lastFed}（当天不能再吃）、并且只回 1 点血。</li>
     * </ol>
     *
     * <p>优先级是「血量未满优先走回血」，因为 TFC 的 {@code eatFood} 只回 1 点血 —— 一只受伤的马
     * 喂一整组干草块却几乎不回血，这显然不对。血满了以后再喂才是正餐。
     *
     * <p><b>为什么第三条分支返回 {@code FAIL} 而不是落到 {@code super.mobInteract}</b>：
     * 原版 {@code Horse.mobInteract} 对食物会走 {@code fedFood → AbstractHorse.handleEating}，
     * 而 {@code handleEating} 除了回血还会调 {@code setInLove}。于是在「马血满 + 今天已经喂过」时，
     * 玩家会看到冒爱心但按 TFC 规则（{@code isReadyToMate}）根本不怀孕的假象，
     * 而且那一下食物是被真扣掉的。返回 {@code FAIL} 就什么也不会发生。
     * 这也和 TFC 自己「不饿就 {@code return InteractionResult.FAIL}」的处理一致。
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isFood(stack)) {
            // ① 回血：已驯服 + 血量未满。野马走 TFC 原本的路。
            if (TFCICYSConfig.healByEating()
                    && !TFCICYSConfig.isWildHorse(this)
                    && getHealth() < getMaxHealth()) {
                return tfcicys$eatToHeal(player, stack);
            }
            // ② 每日一次的正餐：给亲密度。
            if (isHungry()) {
                return eatFood(stack, hand, player);
            }
            // ③ 血满 + 今天已喂过：什么也不做，食物不扣。
            return InteractionResult.FAIL;
        }
        return super.mobInteract(player, hand);
    }

    /**
     * 「回血吃东西」—— 和每日正餐完全分开的一套。
     *
     * <p>刻意<b>不</b>调用 TFC 的 {@code eatFood}，也<b>不</b>调用原版的 {@code handleEating}：
     *
     * <ul>
     *   <li>不调 {@code eatFood}：就不会写 {@code lastFed}／{@code lastFamiliarityDecay}，
     *       也就不会吃掉当天那一次加亲密度的额度；</li>
     *   <li>不调 {@code handleEating}：就不会走 {@code setInLove}，不冒假爱心；
     *       而且 {@code handleEating} 的回血表只有 6 种原版物品，
     *       我们的 horse_food tag 里全是 TFC 食物，交给它会出现「扣了物品但不回血」。</li>
     * </ul>
     *
     * <p>反馈照抄 TFC 的 {@code eatFood}：5 颗物品粒子 + {@code eatingSound}，
     * 所以「吃东西」的视听表现和正餐一致，玩家分不出是两个系统（本来也不需要分）。
     */
    private InteractionResult tfcicys$eatToHeal(Player player, ItemStack stack) {
        final Level level = level();
        final RandomSource random = getRandom();
        for (int i = 0; i < 5; i++) {
            level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack),
                    getX() + 0.5D, getEyeY(), getZ(),
                    (random.nextDouble() - 0.5D) * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.2D,
                    (random.nextDouble() - 0.5D) * 0.2D);
        }
        heal(tfcicys$healValueOf(stack));

        if (!level.isClientSide) {
            if (!player.isCreative()) {
                // 碗类食物要把碗还回去，和 TFC 的 eatFood 一致，免得吃一次少一个碗。
                stack.getCapability(FoodCapability.CAPABILITY).ifPresent(cap -> {
                    if (cap instanceof DynamicBowlHandler bowl) {
                        ItemHandlerHelper.giveItemToPlayer(player, bowl.getBowl().copy());
                    }
                });
                if (stack.hasCraftingRemainingItem()) {
                    ItemHandlerHelper.giveItemToPlayer(player, stack.getCraftingRemainingItem());
                }
                stack.shrink(1);
            }
            playSound(eatingSound(stack), 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 一个食物回多少血。
     *
     * <p>两个来源取<b>较大值</b>，理由各不相同：
     *
     * <ul>
     *   <li><b>原版表的固定值</b>：这 6 个数是从 {@code AbstractHorse.handleEating} 的字节码里
     *       逐条核对出来的（{@code m_5994_}）。干草块 20 点是有意义的强度，不能被 TFC 的饥饿值
     *       换算拉低。</li>
     *   <li><b>TFC 的饥饿值 × 系数</b>：horse_food tag 里是 {@code #tfc:foods/grains} 和
     *       {@code #tfc:foods/fruits}，全是 TFC 食物，原版表里一个都没有。</li>
     * </ul>
     *
     * <p>取大值意味着「接了 TFC 只会更强，不会更弱」：万一某个物品两边都命中
     * （比如苹果可能同时在 {@code #tfc:foods/fruits} 里），也不会比原版回得少。
     *
     * <p>最后再和 {@code minHeal} 取大值，兜住饥饿值查不到或为 0 的食物 ——
     * 否则会出现「物品被吃掉但一点血都没回」。
     */
    private static float tfcicys$healValueOf(ItemStack stack) {
        float vanilla = 0.0F;
        if (stack.is(Items.SUGAR)) {
            vanilla = 1.0F;
        } else if (stack.is(Items.WHEAT)) {
            vanilla = 2.0F;
        } else if (stack.is(Items.APPLE)) {
            vanilla = 3.0F;
        } else if (stack.is(Items.GOLDEN_CARROT)) {
            vanilla = 4.0F;
        } else if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            vanilla = 10.0F;
        } else if (stack.is(Items.HAY_BLOCK)) {
            vanilla = 20.0F;
        }

        float tfc = 0.0F;
        final IFood food = FoodCapability.get(stack);
        if (food != null) {
            tfc = food.getData().hunger() * TFCICYSConfig.healPerHunger();
        }

        return Math.max(TFCICYSConfig.minHeal(), Math.max(vanilla, tfc));
    }

    /**
     * 骑乘驯服的概率改由 TFC 亲密度决定，曲线见 {@link TFCICYSConfig#tamePercent}。
     *
     * <h2>为什么是覆写 getTemper</h2>
     *
     * <p>原版驯服的骰子在 {@code RunAroundLikeCrazyGoal.tick()}（SRG {@code m_8037_}）：
     *
     * <pre>
     * if (!horse.isTamed() &amp;&amp; horse.getRandom().nextInt(50) == 0) {   // 每 tick 1/50 的尝试闸门
     *     if (passenger instanceof Player player) {
     *         int temper    = horse.getTemper();        // ← 虚调用，SRG m_30624_
     *         int maxTemper = horse.getMaxTemper();     // ← 原版硬编码 100，SRG m_7555_
     *         if (maxTemper &gt; 0 &amp;&amp; random.nextInt(maxTemper) &lt; temper) {
     *             horse.tameWithName(player);
     *             return;
     *         }
     *         horse.modifyTemper(5);                    // 失败也涨 5
     *     }
     *     horse.makeMad(); ...                          // 甩人 + 粒子
     * }
     * </pre>
     *
     * <p>{@code getMaxTemper()} 是常量 100，所以成功率<b>正好等于 getTemper()/100</b>。
     * 覆写 {@code getTemper()} 就等于直接设定百分比，而且：
     *
     * <ul>
     *   <li>不用注入那个 Goal 的字节码，别的模组改它也不会冲突。</li>
     *   <li>原版的 1/50 尝试闸门、甩人动作、粒子、音效全部保持原样 ——
     *       玩家看到的还是熟悉的「骑上去被甩下来」。</li>
     *   <li>{@code tameWithName} 仍然由原版调用，所以 Icy 的
     *       {@code bh_claimHorseOnTame}（驯服瞬间认领归属）与原版
     *       {@code CriteriaTriggers.TAME_ANIMAL} 照常触发。</li>
     * </ul>
     *
     * <h2>为什么可以安全覆写</h2>
     *
     * <p>{@code getTemper()} 在运行时只有三个读者：{@code RunAroundLikeCrazyGoal.tick()}、
     * {@code AbstractHorse.handleEating()} 里的 {@code getTemper() &lt; getMaxTemper()} 判断，
     * 以及存档写入。已核对 Icy 的 jar：它<b>完全不引用</b> {@code getTemper} /
     * {@code getMaxTemper}（只碰 {@code tameWithName} 与 {@code setTamed}），
     * 所以没有第三方的读取会被带偏。
     *
     * <p>只在「被玩家骑着 + 尚未驯服」时改值，其余一律返回原版数值：空着站的马、
     * 已驯服的马、存档读写都不受影响。
     */
    @Override
    public int getTemper() {
        if (!TFCICYSConfig.tamingByFamiliarity() || isTamed()) {
            return super.getTemper();
        }
        // 用第一个乘客，与 RunAroundLikeCrazyGoal.tick() 里的 getPassengers().get(0) 一致。
        if (!(getFirstPassenger() instanceof Player rider)) {
            return super.getTemper();
        }
        return (int) Math.round(TFCICYSConfig.tamePercent(rider, TFCICYSConfig.familiarityOf(this)));
    }

    /**
     * Age now follows TFC's own definition (days since {@code birthDay}), instead of the vanilla
     * {@code isBaby()} flag.
     * <p>
     * <b>Why this changed:</b> the previous implementation returned
     * {@code isBaby() ? CHILD : ADULT}, which broke newborns in two ways. TFC's
     * {@code MammalProperties.birthChildren()} creates the foal and adds it to the level <em>without</em>
     * ever calling {@code setBaby(true)} (TFC does not use vanilla ageing), and Icy's
     * {@code getBreedOffspring} override returns a freshly {@code create()}-d entity whose age is 0.
     * So {@code isBaby()} was false and every newborn was classified as an adult immediately.
     * <p>
     * TFC's default ({@code TFCAnimalProperties.getAgeType()}) derives the age from
     * {@code getCalendar().getTotalDays() - getBirthDay()}, which is exactly why
     * {@code setBabyTraits} stamps {@code baby.setBirthDay(today)}. Not overriding it restores that.
     *
     * @see #tfcicys$applyBabyTraits which makes sure that stamp actually happens for Icy foals
     */
    @Override
    public boolean isBaby() {
        return getAgeType() == Age.CHILD;
    }

    /**
     * Vanilla ageing is a no-op, exactly as in {@code TFCHorse}/{@code TFCChestedHorse}. TFC drives
     * growth from {@code birthDay}, so letting {@code super.setAge} run would fight it.
     */
    @Override
    public void setAge(int age) {
        super.setAge(0);
    }

    @Override
    public int getAge() {
        return isBaby() ? -24000 : 0;
    }

    /**
     * Translates vanilla's baby/adult switch into TFC's birthday stamp.
     *
     * <p><b>Why this is needed.</b> Icy's horse panel renders every horse from a throw-away
     * <i>preview entity</i> ({@code icy.betterhorses.net.client.HorsePreviewCache.build}):
     * {@code EntityType.create(ClientLevel)} then {@code setBaby(entry.baby())}. That bare entity carries no
     * TFC data, so its birthday is {@code registerCommonData()}'s default {@code 0L} and {@link #isBaby()}
     * evaluates {@code getAgeType()} as {@code currentDays - 0} -- which is {@code CHILD} for as long as the
     * world is younger than the horse's adulthood days. {@code BhHorseRenderer} picks its model from
     * {@code isBaby()}, so every preview was drawn with a foal model, and because every preview shares that
     * same default birthday they <em>all</em> looked like foals. The flag Icy sends could not correct it:
     * it arrives as {@code setAge(-24000)}, which {@link #setAge} discards by design.
     *
     * <p>Real horses need no translation in either direction: their birthday is stamped by
     * {@code initCommonAnimalData} (wild spawns -- {@code getRandomGrowth} hands out negative birthdays) or
     * by {@code birthChildren} (newborns -- birthday = today), and it round-trips through NBT.
     *
     * <p><b>Why {@code setBaby} and not {@code setAge}.</b> {@code AgeableMob.aiStep()} calls
     * {@code setAge(getAge() + 1)} on <em>every tick</em> for as long as the animal is a baby, and in this
     * class {@code getAge()} is pinned at {@code -24000}, so stamping a birthday there would re-newborn a
     * foal continuously and it would never grow up. {@code setBaby} is only reached by callers that
     * explicitly want the age class changed; vanilla's breeding path is already short-circuited by
     * {@code getBreedOffspring} returning {@code null}, which leaves Icy's preview entity.
     *
     * <p>The early return is what keeps this harmless: marking an actual newborn {@code setBaby(true)}, or
     * an actual adult {@code setBaby(false)}, says nothing new, and the horse keeps its real birthday.
     *
     * @see #isBaby()
     * @see #setAge(int)
     */
    @Override
    public void setBaby(boolean baby) {
        super.setBaby(baby);
        if (isBaby() == baby) {
            return;
        }
        final long today = getCalendar().getTotalDays();
        // baby  -> birthday = today                  (elapsed 0 <= adulthoodDays       => CHILD)
        // adult -> birthday = just old enough        (elapsed adulthoodDays + 1 > cap  => ADULT)
        setBirthDay(baby ? today : today - getDaysToAdulthood() - 1L);
    }

    /**
     * Replaces vanilla's instant foal with TFC's pregnancy, <b>using TFC's own mechanism</b>.
     * <p>
     * TFC does not cancel {@code Animal.spawnChildFromBreeding} and neither do we.
     * {@code TFCAnimalProperties.getBreedOffspring} simply <b>returns {@code null}</b>, which makes the
     * caller's {@code if (ageablemob != null)} block -- the one that sets the baby flag, spawns the
     * entity and calls {@code finalizeSpawnChildFromBreeding} -- skip itself wholesale. Its own comment
     * says so: <i>"Cancel default vanilla behaviour (immediately spawns children of this animal) and set
     * this female as fertilized"</i>. This injector reproduces those lines exactly.
     * <p>
     * <b>An earlier version of this addon cancelled {@code spawnChildFromBreeding} at HEAD instead, and
     * that was wrong in two ways.</b> Skipping the caller skips
     * {@code finalizeSpawnChildFromBreeding} as well, which is where Icy does two things that matter:
     * <ul>
     *   <li>{@code AnimalMixin:91} rolls the foal's <b>Icy</b> gender ({@code bh_setGender}). With that
     *       skipped, {@code AbstractHorseMixin:450} falls back to its default -- {@code MALE} -- so every
     *       foal was born a stallion.</li>
     *   <li>{@code bh_awardFoal} fires Icy's breeding advancements.</li>
     * </ul>
     * Returning {@code null} from here keeps the caller running, so all of that still happens.
     * <p>
     * The {@code partner != this} test is what separates the two callers. Vanilla/TFC breeding passes
     * the mate, and the mare is the one to fertilize; {@code MammalProperties.birthChildren()} passes
     * {@code ageable} -- the mare herself -- and that is the one case that must fall through to Icy's
     * real implementation below. The {@code !isFertilized()} guard is TFC's own, for the same reason its
     * comment gives: the breeding behaviour can call this repeatedly, and fertilization must not stack.
     *
     * @see #tfcicys$applyBabyTraits which stamps TFC's growth data on the foal TFC does produce
     */
    @Inject(
            method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void tfcicys$tfcBreeding(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<AgeableMob> cir) {
        if (partner == this) {
            // MammalProperties.birthChildren(): this is the mare, deliver the foal Icy's way.
            return;
        }
        if (partner instanceof TFCAnimalProperties other) {
            // Conception must not depend on who walked over first. Vanilla BreedGoal.tick() calls
            // spawnChildFromBreeding on whichever animal closed the distance, so either parent can be
            // `this` here. TFC itself only handles the case where `this` is the female, which is
            // sufficient for TFC because its brain BreedBehavior calls getBreedOffspring on the
            // *target* -- always the female. Icy horses run the vanilla BreedGoal instead, so a stallion
            // arriving first used to leave the mare unfertilized and nothing was ever born.
            final TFCAnimalProperties female;
            final TFCAnimalProperties male;
            if (getGender() == Gender.FEMALE && other.getGender() == Gender.MALE) {
                female = this;
                male = other;
            } else if (getGender() == Gender.MALE && other.getGender() == Gender.FEMALE) {
                female = other;
                male = this;
            } else {
                female = null; // same gender pair, or a partner that cannot breed
                male = null;
            }

            if (female != null && !female.isFertilized()) {
                female.onFertilized(male);
                // The sire is only reachable here, at pairing time: birthChildren() runs ~19 days later
                // and passes the mare to itself (partner == this), so by then the father is long gone.
                // TFC's genes tag is persisted by MammalProperties.saveCommonAnimalData, so stashing his
                // wear there is what carries it across the gestation -- including through a world reload.
                // Written onto whichever animal is the mother, so a TFC donkey mare keeps it too.
                // getGenes() lives on MammalProperties, not TFCAnimalProperties.
                if (female instanceof MammalProperties mammal) {
                    final CompoundTag genes = mammal.getGenes();
                    if (genes != null) {
                        genes.putFloat(TFCICYS_SIRE_WEAR, tfcicys$wearOf(male));
                    }
                }
            }
        }
        // Either direction of the vanilla pairing lands here, so TFC's "no instant child" holds.
        cir.setReturnValue(null);
    }

    /**
     * Gives an Icy foal the same growth data TFC gives its own livestock.
     * <p>
     * <b>Why this cannot just call {@code setBabyTraits}:</b> the inherited {@code MammalProperties}
     * override chains into {@code HorseProperties.applyGenes}, which writes
     * {@code MAX_HEALTH} / {@code MOVEMENT_SPEED} / {@code JUMP_STRENGTH}. Icy's
     * {@code bhInheritStats} has already computed those three from both parents in the method body -- this
     * runs at RETURN, i.e. after it -- so delegating would silently overwrite Icy's genetics with TFC's.
     * The three lines below are therefore inlined verbatim from
     * {@code TFCAnimalProperties.setBabyTraits:457-459}, which is the part that is purely TFC bookkeeping
     * and touches no attribute: roll the gender, stamp today as the birthday, and inherit familiarity as
     * {@code parent < 0.9 ? parent / 2 : parent * 0.9}.
     * <p>
     * The birthday is what makes the foal a foal: {@link #isBaby()} reads TFC's
     * {@code getAgeType()}, which counts days since {@code birthDay}, so without this stamp a newborn
     * was classified as an adult the instant it existed. Icy renders baby horses with a dedicated model
     * and foal coat variant ({@code BhHorseRenderer:95}, {@code BhNamedCoats:37}), so this also decides
     * which model it gets.
     */
    @Inject(
            method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;",
            at = @At("RETURN")
    )
    private void tfcicys$applyBabyTraits(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<AgeableMob> cir) {
        if (!(cir.getReturnValue() instanceof TFCAnimalProperties baby)) {
            return;
        }
        baby.setGender(Gender.valueOf(getEntity().getRandom().nextBoolean()));
        baby.setBirthDay(Calendars.SERVER.getTotalDays());
        baby.setFamiliarity(getFamiliarity() < 0.9F ? getFamiliarity() / 2.0F : getFamiliarity() * 0.9F);

        // Icy's gender lives in its own datapack registry and is normally rolled by
        // AnimalMixin.finalizeSpawnChildFromBreeding. A foal produced by birthChildren() is created with
        // EntityType#create, so it never runs finalizeSpawn and would otherwise keep Icy's default
        // (MALE). Roll it here to match how Icy spawns its own horses.
        if (cir.getReturnValue() instanceof BhBreedHorse foal) {
            IHorseData.of(foal).bh_setGender(
                    getEntity().getRandom().nextBoolean() ? BhContent.MALE.getKey() : BhContent.FEMALE.getKey());
            tfcicys$weakenInheritedSpeed(foal);
        }
    }

    /**
     * Worn-out parents pass a weaker speed on to their offspring.
     * <p>
     * <b>Why the penalty has to be applied here.</b> Icy inherits attributes through
     * {@code BhBreedHorse.mix}, which averages {@code getAttributeBaseValue} of both parents
     * ({@code BhBreedHorse:85}). Base values deliberately exclude modifiers, so the transient aging
     * penalty from {@link #tfcicys$applyAgingSpeedPenalty()} is invisible to inheritance -- a horse
     * could be slowed to 70% by age and still sire perfectly fast foals. Writing the reduction into the
     * foal's <em>base</em> value is what makes it stick: the next generation reads it through that same
     * {@code mix}, so wear genuinely propagates down the bloodline.
     * <p>
     * Scaling by {@code 1 - 0.15 * wear} means a fully worn parent yields a foal at <b>85%</b> of the
     * speed it would otherwise have inherited. Note this compounds: a foal of two worn parents, bred to
     * another such foal, keeps stepping down each generation. {@code ArchType.clampSpeed} inside Icy
     * still bounds the result, so a bloodline degrades toward the archetype's floor rather than to zero.
     * <p>
     * The wear used is the <b>greater</b> of the two parents'. Taking the average would let one
     * completely worn-out parent be diluted to almost nothing by a fresh partner, whereas "this horse is
     * old, so its foals are weaker" should hold even when only one side is worn.
     */
    @Unique
    private void tfcicys$weakenInheritedSpeed(BhBreedHorse foal) {
        float wear = tfcicys$wearOf(this);

        // The sire's wear was stashed in the genes tag at pairing time; this runs during birthChildren()
        // where only the mare is reachable.
        final CompoundTag genes = getGenes();
        if (genes != null && genes.contains(TFCICYS_SIRE_WEAR)) {
            wear = Math.max(wear, genes.getFloat(TFCICYS_SIRE_WEAR));
        }

        if (wear <= 0.0F) {
            return;
        }
        final AttributeInstance speed = foal.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.setBaseValue(speed.getBaseValue() * (1.0D - (double) MAX_INHERITED_SPEED_LOSS * wear));
    }

    /**
     * TFC's "wear" figure: how used-up an animal is, as a 0-1 fraction of its elderly threshold.
     * This is the same number TFC shows players as a percentage in Jade
     * ({@code EntityTooltips:124-128}).
     */
    @Unique
    private static float tfcicys$wearOf(TFCAnimalProperties animal) {
        final int cap = animal.getUsesToElderly();
        if (cap <= 0) {
            return 0.0F;
        }
        return Mth.clamp((float) animal.getUses() / (float) cap, 0.0F, 1.0F);
    }

    @Override
    public SoundEvent eatingSound(ItemStack stack) {
        return SoundEvents.HORSE_EAT;
    }

    /**
     * <b>Note on {@code HorseProperties.tickAnimalData()} and cross-species matchmaking:</b> that
     * default calls {@code EntityHelpers.findFemaleMate}, which scans
     * {@code getEntitiesOfClass(Animal.class, ...)} with <em>no species filter</em> -- it leans
     * entirely on {@code checkExtraBreedConditions}, and TFC's own implementation accepts anything
     * that is {@code instanceof Horse}. Because Icy's horses are {@code Horse} subclasses, an Icy
     * stallion can therefore put a nearby TFC mare into the vanilla in-love state.
     * <p>
     * That is harmless and deliberately left alone. {@code findFemaleMate} only calls
     * {@code setInLove}; it never produces offspring. Actual pairing is still blocked on both sides:
     * vanilla's {@code AnimalMakeLove} selects by {@code getClass()}, and TFC's {@code BreedBehavior}
     * requires {@code getType() == getType()}. A charmed TFC mare simply runs her own brain and looks
     * for a TFC stallion, which is exactly what she would have done anyway. Suppressing it here would
     * mean either reimplementing TFC's default method (copying licensed code) or overriding
     * {@code isReadyToMate} to always return false (which would break breeding outright); reaching
     * {@code MammalProperties.super} from this class is not possible, since Java rejects listing
     * {@code MammalProperties} in the {@code implements} clause when {@code HorseProperties} already
     * extends it.
     * <p>
     * {@code tick()} below drives the inherited chain, which is what TFC's own horses do.
     */
    @Override
    public void tick() {
        super.tick();
        // Same 20-tick throttle TFC uses in TFCHorse.tick / TFCChestedHorse.tick.
        if (this.level().getGameTime() % 20L == 0L) {
            tickAnimalData();
            // Must run after tickAnimalData: that is what (re)installs TFC's own old-age modifier.
            tfcicys$applyAgingSpeedPenalty();
        }
    }

    /**
     * Turns TFC's wear value into a smooth speed penalty.
     * <p>
     * <b>What TFC does on its own.</b> {@code getUses()} counts how much an animal has been worked --
     * breeding adds 10 to the mother ({@code MammalProperties:49}) and 5 to the father
     * ({@code TFCAnimalProperties:452}). Once it exceeds {@code getUsesToElderly()} the animal is
     * scheduled to become {@code Age.OLD}, and {@code HorseProperties:130-137} then applies
     * {@code OLD_AGE_MODIFIER} -- a flat {@code -0.5 MULTIPLY_TOTAL}, i.e. horses lose <em>half</em>
     * their speed the instant they cross the line. TFC already shows the underlying 0-100% wear to
     * players ({@code EntityTooltips:124-128}, "animal wear"), but that number has no effect on speed;
     * the only thing speed reacts to is the binary ADULT-&gt;OLD flip.
     * <p>
     * <b>What this does instead.</b> The same 0-100% wear drives speed continuously:
     * {@code uses / usesToElderly} scaled to a {@code -0.3 MULTIPLY_TOTAL} at full wear. Because
     * Minecraft composes {@code MULTIPLY_TOTAL} as {@code value *= 1 + sum(amounts)}, a full-wear horse
     * ends up at exactly <b>70%</b> of its unaged speed, and a half-worn horse at 85%.
     * <p>
     * <b>Why TFC's modifier is removed.</b> Leaving both active would multiply the two penalties:
     * at full wear the horse would sit at {@code 0.7 * 0.5 = 35%}, not the intended 70%. The removal is
     * re-applied every cycle because {@code HorseProperties.tickAnimalData} re-adds its own modifier
     * whenever the animal is {@code Age.OLD}; running this straight afterwards wins the ordering.
     * <p>
     * Only the transient modifier is touched. Icy writes its inherited speed through
     * {@code setBaseValue} ({@code bhInheritStats}), which is left completely alone -- so a foal's
     * genetic speed is unchanged and only the runtime penalty scales with wear.
     */
    @Unique
    private void tfcicys$applyAgingSpeedPenalty() {
        final AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        // TFC's binary old-age penalty is replaced by the graduated one below.
        speed.removeModifier(HorseProperties.OLD_AGE_MODIFIER);
        // Remove last cycle's value; keyed by UUID so the changing amount still matches.
        speed.removeModifier(TFCICYS_AGING_SPEED_ID);

        final float wear = tfcicys$wearOf(this);
        if (wear <= 0.0F) {
            return;
        }

        speed.addTransientModifier(new AttributeModifier(
                TFCICYS_AGING_SPEED_ID,
                "tfc_icys_aging",
                -(double) MAX_AGING_SPEED_LOSS * wear,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /**
     * Mirrors {@code TFCHorse.canMate} ({@code TFCHorse:198-200}).
     * <p>
     * <b>Why vanilla's check cannot be used.</b> {@code Animal.canMate} requires
     * {@code other.getClass() == this.getClass()}, which makes horse x donkey impossible by
     * construction. TFC sidesteps that by replacing the whole method instead of extending it, so the
     * same has to happen here -- otherwise an Icy horse can never pair with a TFC donkey.
     * <p>
     * <b>Why the gender test is inlined.</b> Overriding {@code canMate} on this class shadows the
     * injection Icy makes into {@code Horse.canMate} ({@code EquineBreedingMixin}, which rejects
     * same-gender pairs). Without this line that guard would silently vanish for Icy horses. It also
     * happens to be exactly what TFC writes.
     */
    @Override
    public boolean canMate(Animal otherAnimal) {
        return otherAnimal instanceof TFCAnimalProperties other
                && this.getGender() != other.getGender()
                && this.isReadyToMate()
                && other.isReadyToMate()
                && checkExtraBreedConditions(other);
    }

    /**
     * Mirrors {@code TFCHorse.checkExtraBreedConditions} ({@code TFCHorse:204-212}).
     * <p>
     * <b>This used to read {@code otherAnimal instanceof BhBreedHorse}, which was a bug:</b> it made
     * every TFC donkey and TFC horse an invalid partner, so horses refused to breed with donkeys in
     * either direction. The rule has to be "a horse, or a TFC donkey" -- {@code Horse} is the vanilla
     * base class covering both {@code BhBreedHorse} and {@code TFCHorse}.
     * <p>
     * {@code vanillaParentingCheck} is TFC's real precondition for horse breeding: not being ridden,
     * not riding anything, tamed, and not a baby.
     * <p>
     * <b>Why this does not call {@code HorseProperties.super.checkExtraBreedConditions(...)}:</b> TFC's
     * own {@code TFCHorse} does exactly that, and it is valid Java, but it compiles to
     * {@code invokespecial InterfaceMethod HorseProperties.checkExtraBreedConditions}. That works in a
     * normal class that declares {@code implements HorseProperties} at compile time, but this method is
     * merged into {@code BhBreedHorse} by Mixin, and at runtime the JVM then fails to link the call:
     * <pre>
     * IncompatibleClassChangeError: Method 'boolean BhBreedHorse.checkExtraBreedConditions(...)'
     *     must be Methodref constant
     * </pre>
     * So the delegate is inlined instead. That is semantically identical, not an approximation: TFC's
     * default implementation ({@code TFCAnimalProperties.java:487-490}) is literally
     * {@code return true;}, and neither {@code MammalProperties} nor {@code HorseProperties} overrides
     * it -- verified against the 3.2.20 jar the runtime uses. So TFC's expression reduces to exactly
     * the two instanceof tests written below.
     */
    @Override
    public boolean checkExtraBreedConditions(TFCAnimalProperties otherAnimal) {
        if (!(otherAnimal instanceof Horse) && !(otherAnimal instanceof TFCDonkey)) {
            return false;
        }
        final AbstractHorse otherHorse = (AbstractHorse) otherAnimal;
        return TFCChestedHorse.vanillaParentingCheck(this)
                && TFCChestedHorse.vanillaParentingCheck(otherHorse);
    }

    /**
     * Makes Icy horses follow a player holding food, the way TFC's horses do.
     * <p>
     * TFC installs this in {@code TFCHorse.registerGoals} ({@code TFCHorse:186}):
     * {@code new TemptGoal(this, 1.25f, Ingredient.of(getFoodTag()), false)}. Icy horses extend vanilla
     * {@code Horse}, whose goal set has no temptation at all, so holding food did nothing.
     * <p>
     * This is written as an override rather than an {@code @Inject} because {@code registerGoals} is
     * declared on {@code Mob}, not on {@code BhBreedHorse} -- Mixin can only inject into methods the
     * target class actually declares. Overriding works because {@code Mob}'s constructor calls
     * {@code registerGoals()} virtually, so this body runs during construction just as TFC's does.
     * <p>
     * <b>优先级只能取 5，绝不能照抄 TFC 的「清掉优先级 3」。</b>TFC 当年写
     * {@code removeGoalOfPriority(goalSelector, 3)} 的前提是原版把 3 空着；而 Icy 把
     * <b>四个指令轮盘目标全部装在优先级 3</b>：{@code AbstractHorseMixin.bh_onRegisterGoals}
     * 是 {@code AbstractHorse.registerGoals} 的 {@code @At("TAIL")} 注入（该 mixin 的常量池里
     * 同时有 {@code registerGoals} 与 {@code TAIL}），四个 {@code iconst_3} 依次装上
     * {@code HorseStayGoal} / {@code HorseFollowOwnerGoal} / {@code HorseReturnHomeGoal} /
     * {@code HorseWanderBoundsGoal}。而 TFC 的 {@code EntityHelpers.removeGoalOfPriority} 是
     * {@code getAvailableGoals().removeIf(w -> w.getPriority() == p)} —— <b>按优先级整批删</b>。
     * 于是 {@code super.registerGoals()} 刚把它们装好、下一行就被一次删光：玩家在轮盘上选
     * 停留／游荡／跟随／回厩时 {@code bh_command} 确实变了，但对应目标已经不在选择器里，
     * 表现就是「指令轮盘完全失效」。2026-10-11 查实的真实缺陷，证据与反汇编记在
     * docs/DEV_NOTES.md 第 23 节。
     * <p>
     * 空槽是算出来的，不是猜的：原版 {@code AbstractHorse.registerGoals} 逐个 {@code addGoal}
     * 用的是 1/2/4/6/7/8/9，Icy 用 1/2/3，0 是 {@code FloatGoal} —— 只有 5 还空着，而且比 6 的
     * {@code WaterAvoidingRandomStrollGoal} 靠前。这与 TFC 当年选 3 的理由完全同构：占最靠前的
     * 空槽，让"被食物吸引"压过闲逛。指令目标在 3、本目标在 5，于是"有指令时听指令、
     * 没指令时才被食物引走"—— 这是刻意的优先级安排，不是将就。
     * <p>
     * 1.25 的速度系数与 {@code canScare = false} 都是 TFC 的值。按<b>类</b>清一遍既有
     * {@code TemptGoal}，只为保留"我们的目标不会被另一个诱惑目标顶掉"这层原意，
     * 不碰任何其他优先级。{@code getFoodTag()} 与 {@code isFood} 用的是同一个
     * {@code tfc:horse_food} 标签，所以能喂的东西也是马会走过来的东西。
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 绝不能动优先级 3：Icy 的四个指令轮盘目标（Stay / Follow / ReturnHome / Wander）都在那里。
        // 曾经照抄 TFC 的 removeGoalOfPriority(goalSelector, 3)，把轮盘整条删没了 —— 见上。
        EntityHelpers.removeGoalOfClass(this.goalSelector, TemptGoal.class);
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.25F, Ingredient.of(getFoodTag()), false));

        // Vanilla builds BreedGoal with the two-argument constructor, which captures
        // partnerClass = animal.getClass() -- i.e. it only ever looks for its own exact class and would
        // never find a TFC donkey, no matter what canMate() says. Rebuilding it with Animal.class
        // widens the search; canMate() is what actually decides whether a candidate is valid.
        // Removing by class rather than by priority avoids depending on which priority Animal chose.
        EntityHelpers.removeGoalOfClass(this.goalSelector, BreedGoal.class);
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D, Animal.class));

        // 逃跑。必须放在优先级 0：原版 PanicGoal 在优先级 1，两者都占 MOVE 旗标，
        // 同优先级时先注册的会一直压住后注册的，放到 1 就等于没加。
        // 优先级 0 原本只有 FloatGoal，而 FloatGoal 只占 JUMP，不冲突。
        this.goalSelector.addGoal(0, new FleeFromThreatGoal(this));
    }

    // ── FrightenedHorse：被附近马匹死亡吓到时的状态 ────────────────────────────
    //
    // 「血少了要逃」不在这里，那是 FleeFromThreatGoal 自己按血量判断的；
    // 这里只承载「附近死了马」这种<b>外部</b>写入的、带时限的逃离点。

    /** 要远离的点。 */
    @Unique @Nullable private Vec3 tfcicys$fleePoint;

    /** 逃离状态到期的游戏刻。 */
    @Unique private long tfcicys$fleeUntil;

    /** 本次逃跑的脱离半径（格）。{@code <= 0} = 由 FleeFromThreatGoal 用默认值（16 格）。 */
    @Unique private double tfcicys$fleeRadius;

    /** 本次逃跑的速度修正。{@code <= 0} = 由 FleeFromThreatGoal 用默认值（2.0 冲刺）。 */
    @Unique private double tfcicys$fleeSpeed;

    /**
     * 本次逃跑是不是「被仙人掌扎到」那一档。
     *
     * <p>为 true 时 {@link #tfcicys$fleePoint()} 会额外检查「还挨不挨着仙人掌」，
     * 脱离了就直接返回 {@code null} 让逃跑立刻结束 —— 这是仙人掌<b>成丛</b>问题的解药：
     * 不靠跑固定距离，而靠事实脱离来收尾。
     */
    @Unique private boolean tfcicys$fleeFromCactus;

    /**
     * 本次逃离是否由环境伤害触发（岩浆、岩浆块、火焰……）。
     *
     * <p>与 {@code tfcicys$fleeFromCactus} 互斥，两者分别对应「脱离即停」和「计时器到点即停」
     * 两种收尾；{@link FleeFromThreatGoal} 还要靠它决定是否绕开「只给野马」的限制。
     */
    @Unique private boolean tfcicys$fleeFromHazard;

    /** {@inheritDoc} */
    @Override
    public void tfcicys$startFleeing(final Vec3 point, final int ticks) {
        // 长距离冲刺一档：半径与速度交给 FleeFromThreatGoal 的默认值（16 格 / 2.0）。
        this.tfcicys$startFleeing(point, ticks, 0.0D, 0.0D, false, false);
    }

    /** {@inheritDoc} */
    @Override
    public void tfcicys$startFleeingLocal(final Vec3 point, final int ticks,
                                          final double radius, final double speed) {
        this.tfcicys$startFleeing(point, ticks, radius, speed, true, false);
    }

    /** {@inheritDoc} */
    @Override
    public void tfcicys$startFleeingHazard(final Vec3 point, final int ticks,
                                           final double radius, final double speed) {
        // 环境伤害这一档刻意不设 fromCactus：它的收尾条件就是计时器本身。
        // 每次挨烫都会把 tfcicys$fleeUntil 往后推（见下面的合并逻辑），
        // 不再受伤就自然到点结束 —— 也就是「逃到离开伤害区域为止」。
        this.tfcicys$startFleeing(point, ticks, radius, speed, false, true);
    }

    /** {@inheritDoc} */
    @Override
    public boolean tfcicys$isHazardFlee() {
        return this.tfcicys$fleeFromHazard;
    }

    /**
     * {@inheritDoc}
     *
     * <p>取「更危险的说了算」：时长取更长的，地点取离马更近的。附近连续倒两匹马时，
     * 马会朝更近的那具跑，而不是被后一次调用冲淡。
     *
     * <p>半径／速度跟着<b>赢得地点的那一次调用</b>走，和地点保持同一套语义 ——
     * 否则会出现「点是最危险那个、半径却是上一次的」这种自相矛盾的状态。
     */
    private void tfcicys$startFleeing(final Vec3 point, final int ticks,
                                      final double radius, final double speed,
                                      final boolean fromCactus, final boolean fromHazard) {
        if (point == null || ticks <= 0) {
            return;
        }
        final long until = this.level().getGameTime() + ticks;
        if (this.tfcicys$fleePoint == null
                || until > this.tfcicys$fleeUntil
                || point.distanceToSqr(this.position()) < this.tfcicys$fleePoint.distanceToSqr(this.position())) {
            this.tfcicys$fleePoint = point;
            this.tfcicys$fleeRadius = radius;
            this.tfcicys$fleeSpeed = speed;
            this.tfcicys$fleeFromCactus = fromCactus;
            this.tfcicys$fleeFromHazard = fromHazard;
        }
        this.tfcicys$fleeUntil = Math.max(this.tfcicys$fleeUntil, until);
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public Vec3 tfcicys$fleePoint() {
        if (this.tfcicys$fleePoint == null || this.level().getGameTime() > this.tfcicys$fleeUntil) {
            return null;
        }
        // 仙人掌那一档：只要身上和紧邻都没有类仙人掌方块了，就没什么好躲的，直接结束。
        // 这一条比「跑够 radius 格」更准 —— 仙人掌成丛分布，跑够距离不代表真的安全。
        if (this.tfcicys$fleeFromCactus && !CactusBlocks.isNear(this)) {
            return null;
        }
        return this.tfcicys$fleePoint;
    }

    /** {@inheritDoc} */
    @Override
    public double tfcicys$fleeRadius() {
        return this.tfcicys$fleeRadius;
    }

    /** {@inheritDoc} */
    @Override
    public double tfcicys$fleeSpeed() {
        return this.tfcicys$fleeSpeed;
    }

    /** {@inheritDoc} */
    @Override
    public void tfcicys$stopFleeing() {
        this.tfcicys$fleePoint = null;
        this.tfcicys$fleeUntil = 0L;
        this.tfcicys$fleeFromCactus = false;
        this.tfcicys$fleeFromHazard = false;
    }

    // ── 攻击者记忆：低血量逃跑用 ─────────────────────────────────────────────
    //
    // 为什么不复用 bh_getCombatTarget，见 FrightenedHorse#tfcicys$noteAttacker 的注释：
    // Icy 会在同一个 30% 阈值上把战斗目标清掉，正好抹掉逃跑的触发条件。

    /** 最近一次攻击者的 UUID。 */
    @Unique @Nullable private UUID tfcicys$attackerId;

    /** 攻击者记忆的到期游戏刻。 */
    @Unique private long tfcicys$attackerUntil;

    /** 马自己最后一次挨打的游戏刻。脱战计时用，不做超时判断。 */
    @Unique private long tfcicys$hurtAt;

    /** 最近一次「主人被谁打了」的 UUID。替主人报仇的续时用。 */
    @Unique @Nullable private UUID tfcicys$ownerThreatId;

    /** 主人最后一次挨打的游戏刻。 */
    @Unique private long tfcicys$ownerThreatAt;

    /**
     * {@inheritDoc}
     *
     * <p>只存 UUID 和到期时刻，不持有实体引用 —— 攻击者可能下一秒就被移除或换了维度，
     * 拿着旧引用迟早出事。真的要用时再按 UUID 去 {@link ServerLevel} 里解析。
     */
    @Override
    public void tfcicys$noteAttacker(final LivingEntity attacker) {
        if (attacker == null) {
            return;
        }
        final long now = this.level().getGameTime();
        this.tfcicys$attackerId = attacker.getUUID();
        this.tfcicys$attackerUntil = now + TFCICYSConfig.threatMemoryTicks();
        this.tfcicys$hurtAt = now;
    }

    /** {@inheritDoc} */
    @Override
    public void tfcicys$noteOwnerThreat(final LivingEntity attacker) {
        if (attacker == null) {
            return;
        }
        this.tfcicys$ownerThreatId = attacker.getUUID();
        this.tfcicys$ownerThreatAt = this.level().getGameTime();
    }

    /** {@inheritDoc} */
    @Override
    public long tfcicys$provokedAt(final LivingEntity attacker) {
        if (attacker == null) {
            return Long.MIN_VALUE;
        }
        final UUID id = attacker.getUUID();
        long latest = Long.MIN_VALUE;
        if (id.equals(this.tfcicys$attackerId)) {
            latest = Math.max(latest, this.tfcicys$hurtAt);
        }
        if (id.equals(this.tfcicys$ownerThreatId)) {
            latest = Math.max(latest, this.tfcicys$ownerThreatAt);
        }
        return latest;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public LivingEntity tfcicys$recentAttacker() {
        if (this.tfcicys$attackerId == null || this.level().getGameTime() > this.tfcicys$attackerUntil) {
            return null;
        }
        // 只在服务端解析：逃跑本身是服务端行为，客户端没有权威实体表。
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        final Entity entity = serverLevel.getEntity(this.tfcicys$attackerId);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void tfcicys$forgetAttacker() {
        this.tfcicys$attackerId = null;
        this.tfcicys$attackerUntil = 0L;
    }

    /**
     * 野外生成时初始化 TFC 的动物数据。
     *
     * <p><b>这是修复一个真实 bug 的代码，注释必须保留。</b>
     *
     * <p>{@link #isBaby()} 交给 TFC 的 {@code getAgeType()} 判断，而它算的是
     * {@code getCalendar().getTotalDays() - getBirthDay()}。TFC 的
     * {@code registerCommonData()} 把 birthday 默认定义为 {@code 0L}，
     * 于是一只刚生成的马「生日 = 第 0 天」，世界天数减 0 若还没超过成年天数，
     * 就被判成幼年 —— 症状是野外刷出来的马<b>全部是幼年</b>。
     *
     * <p>TFC 自己是在 {@code TFCHorse.finalizeSpawn} 里调
     * {@code initCommonAnimalData(level, difficulty, reason)} 补这个戳的，该方法会
     * {@code setBirthDay(getRandomGrowth(...))}，即
     * <b>5% 概率生成幼年，95% 概率生成一只年龄随机的成年个体</b>。
     * 我们实现了 {@code HorseProperties} 却漏了这一步。
     *
     * <p>之所以只有这一处能决定年龄：本类同时把 {@code setAge} 覆写成了空操作
     * （TFC 风格，不使用原版的 {@code age} 字段），所以连 Icy 若调用
     * {@code setBaby(true)} 也不会生效 —— 年龄<b>完全</b>由 {@code getAgeType()} 决定。
     *
     * <p><b>这里必须用 {@code @Inject} 而不是覆写。</b>反编译 Icy 的
     * {@code BhBreedHorse.finalizeSpawn} 可以看到它<b>自己声明了</b>这个方法
     * （jar 内为 SRG 名 {@code m_6518_}）：
     *
     * <pre>
     *   SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
     *   this.bhSetCoat(this.bhCoatSet().roll(this.random));
     *   this.bhRollStats();
     *   return result;
     * </pre>
     *
     * <p>它<b>完全不碰生日</b>，所以我们的逻辑补在 TAIL 即可；若改成在 mixin 里重新声明
     * 同名方法，就会与目标类已有的方法冲突。
     *
     * <p>{@code reason != BREEDING} 的守卫照抄 {@code TFCHorse}：繁殖走的是
     * {@code finalizeSpawnChildFromBreeding} 那条路，生日由 {@code birthChildren()} 负责盖，
     * 不能在这里再随机一次，否则新生幼驹会被随机成成年。
     */
    @Inject(
        method = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;",
        at = @At("TAIL")
    )
    private void tfcicys$initCommonAnimalDataOnSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (reason != MobSpawnType.BREEDING) {
            initCommonAnimalData(level, difficulty, reason);
        }
    }

    // ---------------------------------------------------------------- wiring

    @Inject(method = "defineSynchedData()V", at = @At("TAIL"))
    private void tfcicys$defineCommonData(CallbackInfo ci) {
        registerCommonData();
        this.entityData.define(TFCICYS_PREGNANT_TIME, -1L);
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void tfcicys$saveCommonData(CompoundTag tag, CallbackInfo ci) {
        saveCommonAnimalData(tag);
    }

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void tfcicys$readCommonData(CompoundTag tag, CallbackInfo ci) {
        readCommonAnimalData(tag);
    }
}
