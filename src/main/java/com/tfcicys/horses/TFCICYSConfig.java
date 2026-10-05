package com.tfcicys.horses;

import java.util.List;

import com.tfcicys.horses.load.MoreAttributesApi;

import icy.betterhorses.net.IHorseData;
import net.dries007.tfc.common.entities.livestock.horse.HorseProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 所有负重相关的数值都在这里，方便整合包按自己的节奏调整。
 *
 * <p>默认值与需求一致：
 * <ul>
 *   <li>生物默认上限 1000，五类 Icy 马按 900 / 1600 / 1800 / 2000 / 4800 覆盖</li>
 *   <li>马车自带 512 固定重量，货物按 More Attributes 重量 ×60%（即减轻 40%）</li>
 *   <li>挽马拉车再降到 40%，玩家骑乘自身 350（挽马 250）</li>
 *   <li>衰老 100% 时上限降为 60%</li>
 * </ul>
 */
public final class TFCICYSConfig {

    /**
     * 生物负重上限的出厂默认值。
     *
     * <p>单独提出来是因为它有两个用处：构建配置项的默认值，
     * 以及在配置尚未加载时作为 {@code EntityAttributeModificationEvent} 的兜底
     * （见 {@link #creatureDefaultLoadOrFallback()}）。
     */
    public static final int DEFAULT_CREATURE_LOAD = 1000;

    /**
     * 拉车生物的负重刷新间隔出厂默认值（tick）。
     *
     * <p>5 tick = 0.25 秒。同样需要一个兜底入口：这个值是在
     * {@code LivingTickEvent} 里读的，虽然那时配置早已加载，
     * 但读配置项本身会抛 {@code IllegalStateException}，不能让它有机会崩在 tick 里。
     */
    public static final int DEFAULT_CART_REFRESH_TICKS = 5;

    /**
     * 环境伤害逃跑默认忽略的伤害类型。
     *
     * <p>判断标准只有一条：<b>跑开能不能改善处境</b>。溺水、摔落、饥饿、虚空、
     * 魔法与凋零这些，马跑多远都照旧受伤，让它满地乱跑只会更难处理。
     * 岩浆、岩浆块、火焰、甜浆果丛这些则相反 —— 挪开就是唯一的活路。
     *
     * <p>{@code minecraft:cactus} 也被排掉了：仙人掌有自己的「贴着就停」收尾判定
     * （见 {@code fleeFromCactus}），走环境逃跑这条路会把那套逻辑顶掉。
     */
    public static final List<String> DEFAULT_HAZARD_IGNORE = List.of(
            "minecraft:cactus",
            "minecraft:drowning",
            "minecraft:fall",
            "minecraft:fly_into_wall",
            "minecraft:starve",
            "minecraft:generic",
            "minecraft:generic_kill",
            "minecraft:bad_respawn_point",
            "minecraft:magic",
            "minecraft:indirect_magic",
            "minecraft:wither",
            "minecraft:out_of_world");

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private TFCICYSConfig() {}

    /**
     * 安全地读取生物默认上限。
     *
     * <p>{@code EntityAttributeModificationEvent} 与通用配置的加载同在 common setup
     * 阶段，两者先后并无保证。配置没加载时 {@code ForgeConfigSpec.ConfigValue.get()}
     * 会抛 {@code IllegalStateException}，在启动期就是一次硬崩。
     * 这里的值只是属性的<b>初始</b>默认值——运行时 {@code LoadManager} 每 20 tick
     * 会用 {@code capOf()} 覆盖它——所以退回出厂值不影响最终行为。
     */
    public static double creatureDefaultLoadOrFallback() {
        try {
            return COMMON.creatureDefaultLoad.get();
        } catch (final IllegalStateException notLoadedYet) {
            return DEFAULT_CREATURE_LOAD;
        }
    }

    /**
     * 拉车生物的负重刷新间隔（tick），读取失败时退回 {@value #DEFAULT_CART_REFRESH_TICKS}。
     *
     * <p>这个值在 {@code LivingTickEvent} 里读，那时配置早已加载，{@code catch}
     * 只是防御性的——但读配置项抛异常会让每一次 tick 都炸，不能给它机会。
     * 同时夹到 ≥1，避免有人手改配置文件写成 0 造成每 tick 取模除零。
     */
    public static int cartRefreshIntervalOrFallback() {
        try {
            return Math.max(1, COMMON.cartRefreshIntervalTicks.get());
        } catch (final IllegalStateException notLoadedYet) {
            return DEFAULT_CART_REFRESH_TICKS;
        }
    }

    /**
     * 是否把 Icy 马匹当中立生物。
     *
     * <p>mixin 是在运行时被调用的，配置那时早已加载，{@code catch} 只是防御性的。
     * 读取失败时回退出厂值，也就是「中立」——与默认值一致。
     */
    public static boolean neutralCombat() {
        try {
            return COMMON.horseNeutralCombat.get();
        } catch (final IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /**
     * 是否输出负重链路的诊断日志。
     *
     * <p>同时接受 JVM 参数 {@code -Dterras_horsies.debugLoad=true}：
     * 专用服务器改启动脚本比改配置文件快，而且不会被 Forge 重写配置时覆盖掉。
     */
    public static boolean debugLoad() {
        if (Boolean.parseBoolean(System.getProperty("terras_horsies.debugLoad", "false"))) {
            return true;
        }
        try {
            return COMMON.debugLoad.get();
        } catch (final IllegalStateException notLoadedYet) {
            return false;
        }
    }

    /** 被自己的马主攻击时是否完全不反击。 */
    public static boolean neverRetaliateAgainstOwner() {
        try {
            return COMMON.neverRetaliateAgainstOwner.get();
        } catch (final IllegalStateException notLoadedYet) {
            return false;
        }
    }

    /**
     * 主人被攻击时，马是否去攻击那个攻击者（Icy 原版行为）。
     *
     * <p>默认 true。读取失败时也回退 true —— 与默认值一致，宁可保留功能，
     * 也不要因为一次读取异常静默把它阉掉。
     */
    public static boolean ownerDefend() {
        try {
            return COMMON.ownerDefend.get();
        } catch (final IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /**
     * 替主人报仇时追击多少 tick。
     *
     * <p>与亲密度无关：亲密度曲线衡量的是「马和玩家的关系」，
     * 而这里马是在替主人出头，不该因为和主人熟就放弃。
     */
    public static int ownerDefendTicks() {
        try {
            return secondsToTicks(COMMON.ownerDefendSeconds.get());
        } catch (final IllegalStateException notLoadedYet) {
            return secondsToTicks(5.0D);
        }
    }

    /** 反击时目标离开多少格后脱战。 */
    public static double retaliateDistance() {
        try {
            return COMMON.retaliateDistance.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 16.0D;
        }
    }

    /** 血量低于该比例时改为逃跑。 */
    public static float fleeHealthRatio() {
        try {
            return COMMON.fleeHealthRatio.get().floatValue();
        } catch (final IllegalStateException notLoadedYet) {
            return 0.30F;
        }
    }

    /** 逃跑时拉开到多少格算已脱离仇恨。 */
    public static double escapeDistance() {
        try {
            return COMMON.escapeDistance.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 32.0D;
        }
    }

    /** 附近多少格内有马匹死亡会吓到马。 */
    public static double horseDeathFleeRadius() {
        try {
            return COMMON.horseDeathFleeRadius.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 16.0D;
        }
    }

    /** 因附近马匹死亡而逃跑的持续 tick 数。 */
    public static int horseDeathFleeTicks() {
        try {
            return (int) Math.round(COMMON.horseDeathFleeSeconds.get() * 20.0D);
        } catch (final IllegalStateException notLoadedYet) {
            return 200;
        }
    }

    /**
     * 读出实体的 TFC 亲密度，范围 0~1；不是 TFC 动物就返回 0。
     *
     * <p>Icy 的马靠 {@code BhBreedHorseFamiliarityMixin} 实现了 TFC 的
     * {@code HorseProperties}，所以这里能直接转。TFC 本家的马、驴、骡同样实现它。
     */
    public static float familiarityOf(final Object entity) {
        if (entity instanceof net.dries007.tfc.common.entities.livestock.TFCAnimalProperties properties) {
            return properties.getFamiliarity();
        }
        return 0.0F;
    }

    /**
     * 这只马算不算「野马」。
     *
     * <p><b>「血量不足逃跑」和「附近有马匹死亡受惊」两条规则只对野马生效</b>，
     * 已驯服的马两条都不走。两个判断取「或」的关系 —— 满足任意一条就算已驯服：
     *
     * <ol>
     *   <li><b>Icy 的归属</b>：{@code bh_isOwned()} 为 true，也就是你把它驯服并占有了。</li>
     *   <li><b>TFC 的驯服线</b>：亲密度达到 {@link HorseProperties#TAMED_FAMILIARITY}
     *       （0.15，也就是 Jade 上的 15）。这是 TFC 自己的马匹驯服判定，
     *       喂几次就能到，数值上等同于「已经开始认人了」。</li>
     * </ol>
     *
     * <p>取「或」而不是「与」：只要有任何一条说明它不再是无主的野马，
     * 就不该再表现得像野生动物。所以一匹只喂过几次、还没被 Icy 占有的马也会停止逃跑。
     */
    public static boolean isWildHorse(final AbstractHorse horse) {
        if (IHorseData.of(horse).bh_isOwned()) {
            return false;
        }
        return familiarityOf(horse) < HorseProperties.TAMED_FAMILIARITY;
    }

    /**
     * 这次反击应该持续多少 tick，0 表示不反击。
     *
     * <p><b>非玩家攻击者</b>：一律返回 {@code mobAggroSeconds}，与亲密度无关 ——
     * 亲密度衡量的是马和玩家的关系，对一只僵尸没有意义。
     *
     * <p><b>玩家攻击者</b>：按亲密度在三个锚点之间做分段直线插值。
     * 传入的 {@code familiarity} 是 0~1，这里先乘 100 换算成 Jade 上显示的那个刻度，
     * 配置项用的也是同一个刻度，免得对不上。
     *
     * <p>曲线（出厂值）：亲密度 0 → 30 秒，10 → 10 秒，18 及以上 → 0。
     * 两段的斜率不同（0~10 段每秒掉 2 秒，10~18 段每秒掉 1.25 秒），
     * 这是按需求给的三点定的，不是一条直线。
     */
    public static int aggroTicks(final LivingEntity target, final float familiarity) {
        if (!(target instanceof Player)) {
            return secondsToTicks(mobAggroSecondsOrFallback());
        }

        final double zeroSeconds;
        final double midSeconds;
        final double mid;
        final double pacify;
        try {
            zeroSeconds = COMMON.aggroSecondsAtZero.get();
            midSeconds = COMMON.aggroSecondsAtMid.get();
            mid = COMMON.midFamiliarity.get();
            pacify = COMMON.pacifyFamiliarity.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 20 * 10;
        }

        // 0~1 -> Jade 上显示的 0~100。
        final double shown = Math.max(0.0D, familiarity) * 100.0D;
        if (shown >= pacify) {
            return 0;
        }

        final double seconds;
        if (shown <= mid) {
            // 第一段：(0, zeroSeconds) -> (mid, midSeconds)
            final double t = mid <= 0.0D ? 1.0D : shown / mid;
            seconds = zeroSeconds + (midSeconds - zeroSeconds) * t;
        } else {
            // 第二段：(mid, midSeconds) -> (pacify, 0)
            // pacify <= mid 时这一段的跨度是 0（或负），直接取 0 免得除零。
            final double span = pacify - mid;
            final double t = span <= 0.0D ? 1.0D : (shown - mid) / span;
            seconds = midSeconds * (1.0D - Math.min(1.0D, Math.max(0.0D, t)));
        }
        return secondsToTicks(seconds);
    }

    /** 非玩家攻击者用的秒数。 */
    private static double mobAggroSecondsOrFallback() {
        try {
            return COMMON.mobAggroSeconds.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 30.0D;
        }
    }

    /** 秒 -> tick，负数或零一律返回 0（0 表示不反击），并夹到 int 范围。 */
    private static int secondsToTicks(final double seconds) {
        if (seconds <= 0.0D) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(seconds * 20.0D));
    }

    /** 已驯服的马血量未满时，吃东西是否回血。 */
    public static boolean healByEating() {
        try {
            return COMMON.healByEating.get();
        } catch (final IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /** TFC 食物回血量 = 饥饿值 × 本系数。 */
    public static float healPerHunger() {
        try {
            return COMMON.healPerHunger.get().floatValue();
        } catch (final IllegalStateException notLoadedYet) {
            return 2.0F;
        }
    }

    /** 任何 tag 内食物的回血下限。 */
    public static float minHeal() {
        try {
            return COMMON.minHeal.get().floatValue();
        } catch (final IllegalStateException notLoadedYet) {
            return 1.0F;
        }
    }

    /** Icy 马匹穿着马铠时是否免疫仙人掌刺伤。 */
    public static boolean cactusProofArmor() {
        try {
            return COMMON.cactusProofArmor.get();
        } catch (final IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /** 野马被仙人掌扎到时是否逃开。 */
    public static boolean fleeFromCactus() {
        try {
            return COMMON.fleeFromCactus.get();
        } catch (final IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /** 被仙人掌扎到后最多挪开多少格。 */
    public static double cactusFleeDistance() {
        try {
            return COMMON.cactusFleeDistance.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 4.0D;
        }
    }

    /** 被仙人掌扎到后的移动速度修正。1.0 = 正常走，2.0 = 冲刺。 */
    public static double cactusFleeSpeed() {
        try {
            return COMMON.cactusFleeSpeed.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 1.2D;
        }
    }

    /**
     * 被打之后记忆攻击者多少 tick。0 表示不记忆。
     *
     * <p>低血量逃跑用它当触发依据，理由见 {@code FrightenedHorse#tfcicys$noteAttacker}：
     * Icy 会在同一个 30% 阈值上把战斗目标清掉，正好抹掉逃跑的触发条件。
     */
    public static int threatMemoryTicks() {
        final double seconds;
        try {
            seconds = COMMON.threatMemorySeconds.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 20 * 10;
        }
        return secondsToTicks(seconds);
    }

    /** 被仙人掌扎到后逃跑持续多少 tick。0 表示不逃。 */
    public static int cactusFleeTicks() {
        final double seconds;
        try {
            seconds = COMMON.cactusFleeSeconds.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 20 * 10;
        }
        return secondsToTicks(seconds);
    }

    /** 是否从非生物来源的环境伤害（岩浆、岩浆块、火焰……）中逃开。 */
    public static boolean hazardFlee() {
        try {
            return COMMON.hazardFlee.get();
        } catch (final IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /** 环境伤害逃跑在最远多少格外找落点。 */
    public static double hazardFleeDistance() {
        try {
            return COMMON.hazardFleeDistance.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 6.0D;
        }
    }

    /** 环境伤害逃跑的移动速度修正。1.0 = 正常走，2.0 = 冲刺。 */
    public static double hazardFleeSpeed() {
        try {
            return COMMON.hazardFleeSpeed.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 1.3D;
        }
    }

    /**
     * 不再受到环境伤害之后，还继续逃多少 tick。
     *
     * <p>岩浆与岩浆块的伤害是每 10 tick 结算一次，所以这个值必须明显大于 10，
     * 否则马会在两次烫伤之间停下来。默认 3 秒。
     */
    public static int hazardFleeTicks() {
        final double seconds;
        try {
            seconds = COMMON.hazardFleeSeconds.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 20 * 3;
        }
        return secondsToTicks(seconds);
    }

    /**
     * 不触发环境逃跑的伤害类型 ID 列表。
     *
     * <p>默认排掉「逃也没用」的那些：溺水、摔落、饥饿、虚空、魔法、凋零之类。
     * 另外把 {@code minecraft:cactus} 也排掉了 —— 仙人掌有自己的「贴着就停」判定，
     * 走这条路反而会把那套收尾逻辑顶掉。
     */
    public static List<String> hazardFleeIgnore() {
        try {
            return List.copyOf(COMMON.hazardFleeIgnore.get());
        } catch (final IllegalStateException notLoadedYet) {
            return DEFAULT_HAZARD_IGNORE;
        } catch (final Throwable broken) {
            return DEFAULT_HAZARD_IGNORE;
        }
    }

    /** 骑乘驯服的概率是否改由 TFC 亲密度决定。 */
    public static boolean tamingByFamiliarity() {
        try {
            return COMMON.tamingByFamiliarity.get();
        } catch (final IllegalStateException notLoadedYet) {
            return true;
        }
    }

    /**
     * 骑乘驯服一次的成功率，百分数（0~100）。
     *
     * <h2>怎么落到游戏里</h2>
     *
     * <p>原版 {@code RunAroundLikeCrazyGoal.tick()} 的骰子是：
     *
     * <pre>
     * int temper    = horse.getTemper();        // SRG m_30624_，虚调用
     * int maxTemper = horse.getMaxTemper();     // SRG m_7555_，原版硬编码 100
     * if (maxTemper &gt; 0 &amp;&amp; random.nextInt(maxTemper) &lt; temper) horse.tameWithName(player);
     * </pre>
     *
     * <p>也就是成功率<b>正好等于 {@code getTemper() / 100}</b>。所以本模组不去改那个 Goal，
     * 而是在 {@code BhBreedHorseFamiliarityMixin} 里覆写 {@code getTemper()}：
     * 被玩家骑着且尚未驯服时，返回本方法算出的百分数。原版的尝试闸门
     * （每 tick 1/50）、甩人动作、粒子与音效<b>全部保持原样</b>。
     *
     * <h2>曲线</h2>
     *
     * <p>传入的 {@code familiarity} 是 0~1，先乘 100 换算成 Jade 上显示的刻度 ——
     * 和 {@link #aggroTicks} 用的是同一套刻度，配置项也是这个刻度，免得对不上。
     *
     * <p>在四个锚点之间做分段直线插值（出厂值）：
     *
     * <pre>
     *   0 →  1%      6 →  5%     18 → 20%     35 → 90%     大于 35 → 100%
     * </pre>
     *
     * <p>三段斜率依次变陡（0.67 / 1.25 / 4.12 个百分点每级），低亲密度几乎白搭、
     * 高亲密度收益陡增，这是按需求给的四个点定的，不是一条直线。
     *
     * <p>马匹的亲密度上限就是 35（TFC 的 {@code familiarityCap}），所以最后那个
     * 「大于 35 → 100%」实际上只在配置调高了上限时才会生效；正常情况下最大值是 90%。
     *
     * <h2>More Attributes 加成</h2>
     *
     * <p>骑手的<b>力量 + 技巧</b>等级之和超过阈值（默认 30）时，每多 1 级加
     * {@code maLevelBonusPerLevel}（默认 5 个百分点），最多加 {@code maLevelBonusMax}
     * （默认 50）。两项的出厂 {@code baseLevel} 都是 10，也就是新角色是 20，
     * 必须真投入 11 级才吃得到第一档加成。模组缺席时加成恒为 0。
     *
     * <p>基础概率与加成<b>相加</b>后夹到 0~100。相加意味着满亲密度 + 满加成必定驯服。
     */
    public static double tamePercent(final Player rider, final float familiarity) {
        final double anchor2;
        final double anchor3;
        final double anchor4;
        final double c1;
        final double c2;
        final double c3;
        final double c4;
        final double above;
        try {
            anchor2 = COMMON.tameAnchor2.get();
            anchor3 = COMMON.tameAnchor3.get();
            anchor4 = COMMON.tameAnchor4.get();
            c1 = COMMON.tameChanceAtZero.get();
            c2 = COMMON.tameChanceAtAnchor2.get();
            c3 = COMMON.tameChanceAtAnchor3.get();
            c4 = COMMON.tameChanceAtAnchor4.get();
            above = COMMON.tameChanceAboveAnchor4.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 1.0D;
        }

        // 0~1 -> Jade 上显示的 0~100。
        final double shown = Math.max(0.0D, familiarity) * 100.0D;

        final double base;
        if (shown <= 0.0D) {
            base = c1;
        } else if (shown > anchor4) {
            base = above;
        } else if (shown <= anchor2) {
            base = lerp(shown, 0.0D, c1, anchor2, c2);
        } else if (shown <= anchor3) {
            base = lerp(shown, anchor2, c2, anchor3, c3);
        } else {
            base = lerp(shown, anchor3, c3, anchor4, c4);
        }

        return clampPercent(base + moreAttributesTameBonus(rider));
    }

    /**
     * More Attributes 的驯服加成（百分点）。
     *
     * <p>读的是<b>主属性等级</b>，不是原版 {@code Attribute} —— 见
     * {@link MoreAttributesApi#mainAttributeLevel}。等级读不到（模组缺席／键名未知）时返回 0。
     */
    private static double moreAttributesTameBonus(final Player rider) {
        if (rider == null) {
            return 0.0D;
        }
        final double threshold;
        final double perLevel;
        final double max;
        try {
            threshold = COMMON.maLevelThreshold.get();
            perLevel = COMMON.maLevelBonusPerLevel.get();
            max = COMMON.maLevelBonusMax.get();
        } catch (final IllegalStateException notLoadedYet) {
            return 0.0D;
        }

        final int levels = MoreAttributesApi.mainAttributeLevel(rider, "strength")
                + MoreAttributesApi.mainAttributeLevel(rider, "skill");
        // levels <= 0 说明两项都没读出来（模组缺席或键名不匹配），不是「等级很低」。
        if (levels <= 0 || levels <= threshold) {
            return 0.0D;
        }
        return Math.min(max, (levels - threshold) * perLevel);
    }

    /** 在 (x0,y0)~(x1,y1) 之间按 x 插值，夹在两端之间。 */
    private static double lerp(final double x, final double x0, final double y0,
                               final double x1, final double y1) {
        final double span = x1 - x0;
        if (span <= 0.0D) {
            return y1;
        }
        final double t = Math.min(1.0D, Math.max(0.0D, (x - x0) / span));
        return y0 + (y1 - y0) * t;
    }

    /** 夹到 0~100 的百分数。 */
    private static double clampPercent(final double percent) {
        return Math.min(100.0D, Math.max(0.0D, percent));
    }

    public static final class Common {

        // ── 负重上限 ────────────────────────────────────────────────
        public final ForgeConfigSpec.IntValue creatureDefaultLoad;
        public final ForgeConfigSpec.IntValue raceLoad;
        public final ForgeConfigSpec.IntValue ponyLoad;
        public final ForgeConfigSpec.IntValue westernLoad;
        public final ForgeConfigSpec.IntValue warLoad;
        public final ForgeConfigSpec.IntValue draftLoad;
        public final ForgeConfigSpec.IntValue tfcHorseLoad;
        public final ForgeConfigSpec.IntValue tfcDonkeyLoad;
        public final ForgeConfigSpec.IntValue tfcMuleLoad;

        // ── 衰老 ────────────────────────────────────────────────────
        public final ForgeConfigSpec.DoubleValue agedLoadFactor;

        // ── 马车 ────────────────────────────────────────────────────
        public final ForgeConfigSpec.IntValue cartBaseLoad;
        public final ForgeConfigSpec.DoubleValue cartCargoFactor;
        public final ForgeConfigSpec.DoubleValue draftCartFactor;
        public final ForgeConfigSpec.IntValue cartRefreshIntervalTicks;

        // ── 骑乘 ────────────────────────────────────────────────────
        public final ForgeConfigSpec.IntValue riderLoad;
        public final ForgeConfigSpec.IntValue draftRiderLoad;
        public final ForgeConfigSpec.BooleanValue inheritRiderBackpackLoad;

        // ── 中立化战斗 ──────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue horseNeutralCombat;
        public final ForgeConfigSpec.BooleanValue ownerDefend;
        public final ForgeConfigSpec.DoubleValue ownerDefendSeconds;
        public final ForgeConfigSpec.BooleanValue neverRetaliateAgainstOwner;
        public final ForgeConfigSpec.DoubleValue retaliateDistance;
        public final ForgeConfigSpec.DoubleValue aggroSecondsAtZero;
        public final ForgeConfigSpec.DoubleValue midFamiliarity;
        public final ForgeConfigSpec.DoubleValue aggroSecondsAtMid;
        public final ForgeConfigSpec.DoubleValue pacifyFamiliarity;
        public final ForgeConfigSpec.DoubleValue mobAggroSeconds;
        public final ForgeConfigSpec.DoubleValue fleeHealthRatio;
        public final ForgeConfigSpec.DoubleValue escapeDistance;
        public final ForgeConfigSpec.DoubleValue horseDeathFleeRadius;
        public final ForgeConfigSpec.DoubleValue horseDeathFleeSeconds;

        // ── 仙人掌逃跑（挂在 neutral_combat.flee 下）─────────────────
        public final ForgeConfigSpec.BooleanValue fleeFromCactus;
        public final ForgeConfigSpec.DoubleValue cactusFleeDistance;
        public final ForgeConfigSpec.DoubleValue cactusFleeSpeed;
        public final ForgeConfigSpec.DoubleValue cactusFleeSeconds;

        // ── 环境伤害逃跑（同一节下）─────────────────────────────────
        public final ForgeConfigSpec.BooleanValue hazardFlee;
        public final ForgeConfigSpec.DoubleValue hazardFleeDistance;
        public final ForgeConfigSpec.DoubleValue hazardFleeSpeed;
        public final ForgeConfigSpec.DoubleValue hazardFleeSeconds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hazardFleeIgnore;

        // ── 攻击者记忆（低血量逃跑的触发依据）───────────────────────
        public final ForgeConfigSpec.DoubleValue threatMemorySeconds;

        // ── 吃东西回血 ──────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue healByEating;
        public final ForgeConfigSpec.DoubleValue healPerHunger;
        public final ForgeConfigSpec.DoubleValue minHeal;

        // ── 骑乘驯服 ────────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue tamingByFamiliarity;
        public final ForgeConfigSpec.DoubleValue tameAnchor2;
        public final ForgeConfigSpec.DoubleValue tameAnchor3;
        public final ForgeConfigSpec.DoubleValue tameAnchor4;
        public final ForgeConfigSpec.DoubleValue tameChanceAtZero;
        public final ForgeConfigSpec.DoubleValue tameChanceAtAnchor2;
        public final ForgeConfigSpec.DoubleValue tameChanceAtAnchor3;
        public final ForgeConfigSpec.DoubleValue tameChanceAtAnchor4;
        public final ForgeConfigSpec.DoubleValue tameChanceAboveAnchor4;
        public final ForgeConfigSpec.DoubleValue maLevelThreshold;
        public final ForgeConfigSpec.DoubleValue maLevelBonusPerLevel;
        public final ForgeConfigSpec.DoubleValue maLevelBonusMax;

        // ── 马铠 ────────────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue cactusProofArmor;

        // ── 诊断 ────────────────────────────────────────────────────
        public final ForgeConfigSpec.BooleanValue debugLoad;

        Common(ForgeConfigSpec.Builder b) {
            b.comment("Terra's horsies [TFC x icy's horses] -- carry weight and cart integration").push("load");

            creatureDefaultLoad = b
                    .comment("Base carry capacity for every living entity. Horses override it with the values below.")
                    .defineInRange("creatureDefaultLoad", DEFAULT_CREATURE_LOAD, 0, Integer.MAX_VALUE);

            b.comment("Carry capacity per Icy archetype. Breed mapping: see docs/DEV_NOTES.md.").push("horses");
            raceLoad    = b.comment("RACE -- Arabian / Quarter / Thoroughbred").defineInRange("raceLoad", 900, 0, Integer.MAX_VALUE);
            ponyLoad    = b.comment("PONY -- Haflinger / Icelandic").defineInRange("ponyLoad", 1600, 0, Integer.MAX_VALUE);
            westernLoad = b.comment("WESTERN -- American Paint / Appaloosa / Morgan").defineInRange("westernLoad", 1800, 0, Integer.MAX_VALUE);
            warLoad     = b.comment("WAR -- Andalusian / Friesian / Mustang").defineInRange("warLoad", 2000, 0, Integer.MAX_VALUE);
            draftLoad   = b.comment("DRAFT -- Belgian / Clydesdale / Percheron / Shire").defineInRange("draftLoad", 4800, 0, Integer.MAX_VALUE);
            b.pop();

            b.comment("TFC's own equines, which have no Icy archetype.").push("tfc_animals");
            tfcHorseLoad  = b.comment("TFC horse -- treated as WESTERN").defineInRange("tfcHorseLoad", 1700, 0, Integer.MAX_VALUE);
            tfcDonkeyLoad = b.comment("TFC donkey -- treated as PONY").defineInRange("tfcDonkeyLoad", 2500, 0, Integer.MAX_VALUE);
            tfcMuleLoad   = b.comment("TFC mule -- treated as DRAFT").defineInRange("tfcMuleLoad", 4000, 0, Integer.MAX_VALUE);
            b.pop();

            agedLoadFactor = b
                    .comment("Share of the carry cap left once old age reaches 100%. 0.6 = down to 60%.")
                    .defineInRange("agedLoadFactor", 0.6D, 0.0D, 1.0D);

            b.pop();

            b.comment("Carts (AstikorCarts TFC)").push("cart");

            cartBaseLoad = b
                    .comment("Fixed cart weight. An empty cart hands all of it to the puller and gets no cargo discount.")
                    .defineInRange("cartBaseLoad", 512, 0, Integer.MAX_VALUE);

            cartCargoFactor = b
                    .comment("Weight factor for the cargo inside. 0.6 = 40% lighter when inherited.")
                    .defineInRange("cartCargoFactor", 0.6D, 0.0D, 10.0D);

            draftCartFactor = b
                    .comment("Whole-cart factor when a DRAFT horse pulls it. 0.4 = only 40% is felt. No effect on players.")
                    .defineInRange("draftCartFactor", 0.4D, 0.0D, 10.0D);

            cartRefreshIntervalTicks = b
                    .comment("How often (in ticks) a creature that is pulling a cart re-reads the cart cargo.",
                            "The cargo lives in a container a player edits through the cart GUI, so it is polled.",
                            "5 = 0.25s: loading the cart is felt by the animal at once, with no visible delay.",
                            "Measured cost of one poll: ~0.5us for a full cart (~5.5us even for a deliberately",
                            "exaggerated cart stuffed with nested containers). Even 20 loaded carts polled this",
                            "often stay around 0.1-1% of a single 50ms server tick, so raising this value is",
                            "only worth it if you want to shave off that last fraction of a percent.")
                    .defineInRange("cartRefreshIntervalTicks", 5, 1, 40);

            b.pop();

            b.comment("Riding").push("rider");

            riderLoad = b
                    .comment("Extra body weight the mount takes on from its rider. Backpack not included.")
                    .defineInRange("riderLoad", 350, 0, Integer.MAX_VALUE);

            draftRiderLoad = b
                    .comment("Rider body weight for DRAFT horses. A draft horse seats two, so two riders count twice.")
                    .defineInRange("draftRiderLoad", 250, 0, Integer.MAX_VALUE);

            inheritRiderBackpackLoad = b
                    .comment("Also count the rider's backpack weight against the mount. false = body weight only.")
                    .define("inheritRiderBackpackLoad", true);

            b.pop();

            b.comment("Neutral-combat rework for Icy horses").push("neutral_combat");

            horseNeutralCombat = b
                    .comment("Let a horse fight back when it is attacked, and give up after a time or a distance.",
                             "false = full Icy behaviour (one kick, no chase); every other option in this section is ignored.")
                    .define("horseNeutralCombat", true);

            b.comment("-- Defending the owner (Icy behaviour, kept by default) --").push("owner_defend");

            ownerDefend = b
                    .comment("When the owner is attacked, the horse goes after the attacker. Default true.",
                             "false = fully neutral: the horse only fights back when hit itself.")
                    .define("ownerDefend", true);

            ownerDefendSeconds = b
                    .comment("Seconds the horse keeps chasing the owner's attacker. Default 5. Ignores familiarity.",
                             "Counted from the last provocation: hitting the owner or the horse again renews it.")
                    .defineInRange("ownerDefendSeconds", 5.0D, 0.0D, 3600.0D);

            b.pop();

            neverRetaliateAgainstOwner = b
                    .comment("Never fight back when the horse's own owner hits it. Default false (it still chases, shorter the tamer it is).")
                    .define("neverRetaliateAgainstOwner", false);

            b.comment("-- Hit by a player: how long it fights back, by familiarity --",
                      "Scale is the Jade readout (0-100), linearly interpolated between three anchors.",
                      "Defaults: 0 -> 30 s, 10 -> 10 s, >= 18 -> no retaliation.",
                      "Every count starts at the last provocation and renews while the attacker keeps it up.")
                    .push("player_aggro");

            aggroSecondsAtZero = b
                    .comment("Seconds it fights back when hit by a player at familiarity 0.")
                    .defineInRange("aggroSecondsAtZero", 30.0D, 0.0D, 3600.0D);

            midFamiliarity = b
                    .comment("Middle anchor of the curve, on the Jade scale. Default 10.")
                    .defineInRange("midFamiliarity", 10.0D, 0.0D, 100.0D);

            aggroSecondsAtMid = b
                    .comment("Seconds it fights back when familiarity equals midFamiliarity.")
                    .defineInRange("aggroSecondsAtMid", 10.0D, 0.0D, 3600.0D);

            pacifyFamiliarity = b
                    .comment("At or above this familiarity, being hit by a player triggers no retaliation. Default 18.")
                    .defineInRange("pacifyFamiliarity", 18.0D, 0.0D, 100.0D);

            b.pop();

            b.comment("-- Hit by a non-player mob --").push("mob_aggro");

            mobAggroSeconds = b
                    .comment("Seconds it fights back when hit by another mob. Default 30, ignores familiarity.",
                             "Also counted from the last hit, so a mob that keeps attacking keeps it renewed.")
                    .defineInRange("mobAggroSeconds", 30.0D, 0.0D, 3600.0D);

            b.pop();

            b.comment("-- Losing aggro, and fleeing --").push("flee");

            retaliateDistance = b
                    .comment("Drop aggro once the target is this many blocks away. Default 16.")
                    .defineInRange("retaliateDistance", 16.0D, 1.0D, 512.0D);

            fleeHealthRatio = b
                    .comment("Below this health ratio a wild horse flees the threat instead. Default 0.30, 0 = off.",
                             "Wild horses only; a tamed horse never flees.")
                    .defineInRange("fleeHealthRatio", 0.30D, 0.0D, 1.0D);

            escapeDistance = b
                    .comment("Fleeing counts as escaped once this far away. Default 32, keep it above retaliateDistance.")
                    .defineInRange("escapeDistance", 32.0D, 1.0D, 512.0D);

            horseDeathFleeRadius = b
                    .comment("A horse dying within this radius spooks nearby horses into fleeing. Default 16, 0 = off. Wild only.")
                    .defineInRange("horseDeathFleeRadius", 16.0D, 0.0D, 128.0D);

            horseDeathFleeSeconds = b
                    .comment("Upper bound in seconds for fleeing because a companion died. Default 10.")
                    .defineInRange("horseDeathFleeSeconds", 10.0D, 0.0D, 3600.0D);

            fleeFromCactus = b
                    .comment("Wild horses step away when a cactus stings them. Default true.",
                             "A short step only (see the next two), ending as soon as the cactus is cleared. Wild only.")
                    .define("fleeFromCactus", true);

            cactusFleeDistance = b
                    .comment("How far a horse steps away from the cactus that stung it, in blocks. Default 4.")
                    .defineInRange("cactusFleeDistance", 4.0D, 1.0D, 16.0D);

            cactusFleeSpeed = b
                    .comment("Movement speed modifier for that step. Default 1.2 (a walk).")
                    .defineInRange("cactusFleeSpeed", 1.2D, 0.5D, 3.0D);

            cactusFleeSeconds = b
                    .comment("Safety bound in seconds for the cactus step. Default 10; rarely reached.")
                    .defineInRange("cactusFleeSeconds", 10.0D, 0.0D, 3600.0D);

            hazardFlee = b
                    .comment("Horses try to walk out of damage that comes from something other than a living attacker:",
                             "lava, magma blocks, fire, sweet berry bushes and the like. Default true.",
                             "Unlike the cactus step this is not wild-only -- burning to death is never acceptable --",
                             "but a ridden horse still yields to its rider. See hazardFleeIgnore for the exclusions.")
                    .define("hazardFlee", true);

            hazardFleeDistance = b
                    .comment("How far away from the hurting spot the horse aims for, in blocks. Default 6.",
                             "The flee keeps renewing as long as the damage keeps coming, so a horse inside a large",
                             "lava pool walks out step by step rather than stopping at one fixed distance.")
                    .defineInRange("hazardFleeDistance", 6.0D, 1.0D, 32.0D);

            hazardFleeSpeed = b
                    .comment("Movement speed modifier while escaping that damage. Default 1.3 (a brisk trot).")
                    .defineInRange("hazardFleeSpeed", 1.3D, 0.5D, 3.0D);

            hazardFleeSeconds = b
                    .comment("How many seconds the horse keeps escaping after the last such hit. Default 3.",
                             "Lava and magma damage land every 10 ticks, so keep this above 0.5 or the horse",
                             "would stop between two ticks of damage. This timer is what makes the flee end",
                             "once the horse has actually left the damaging area.")
                    .defineInRange("hazardFleeSeconds", 3.0D, 0.0D, 3600.0D);

            hazardFleeIgnore = b
                    .comment("Damage type ids the horse will NOT run from, because running cannot help.",
                             "Defaults cover drowning, falling, starvation, the void, magic and wither -- plus",
                             "minecraft:cactus, which has its own 'stop as soon as the cactus is cleared' logic.",
                             "Anything not listed here counts as a hazard. Use the full id, e.g. minecraft:lava.")
                    .defineListAllowEmpty("hazardFleeIgnore", DEFAULT_HAZARD_IGNORE,
                            o -> o instanceof String);

            threatMemorySeconds = b
                    .comment("How long a horse remembers who hit it, in seconds. Default 10, 0 = no memory.",
                             "Low-health fleeing reads it, and every new hit refreshes it.")
                    .defineInRange("threatMemorySeconds", 10.0D, 0.0D, 3600.0D);

            b.pop();

            b.pop();

            b.comment("Healing by eating").push("feeding");

            healByEating = b
                    .comment("A tamed horse below full health heals when fed. Default true.",
                             "Separate from the once-a-day meal: no familiarity, no daily quota, no mating.",
                             "Order of use: hurt -> heal; full and not fed today -> meal; otherwise the food is not eaten.")
                    .define("healByEating", true);

            healPerHunger = b
                    .comment("TFC food heals hunger value x this factor. Default 2.0.",
                             "The six vanilla horse foods keep their fixed vanilla values and skip this factor.")
                    .defineInRange("healPerHunger", 2.0D, 0.0D, 100.0D);

            minHeal = b
                    .comment("Any food in horse_food heals at least this much. Default 1.")
                    .defineInRange("minHeal", 1.0D, 0.0D, 100.0D);

            b.pop();

            b.comment("Riding-to-tame odds driven by TFC familiarity").push("taming");

            tamingByFamiliarity = b
                    .comment("Replace vanilla's riding-to-tame dice roll with this mod's familiarity curve. Default true.",
                             "Done by overriding getTemper(); vanilla's attempt gate, bucking, particles and sounds are unchanged.",
                             "Covers Icy's breeds and TFC's own horse / donkey / mule. TFC equines additionally get their",
                             "familiarity pushed just past HorseProperties.TAMED_FAMILIARITY on a successful roll, because",
                             "TFC's isTamed() ignores vanilla's tame flag and reads familiarity only.",
                             "false = back to the vanilla temper system.")
                    .define("tamingByFamiliarity", true);

            b.comment("-- The four anchors of the chance curve --",
                      "Scale is the Jade readout (0-100). Linear between anchors; the first anchor is fixed at 0.")
                    .push("curve");

            tameAnchor2 = b
                    .comment("Familiarity of the second anchor. Default 6.")
                    .defineInRange("anchor2", 6.0D, 0.0D, 100.0D);

            tameAnchor3 = b
                    .comment("Familiarity of the third anchor. Default 18.")
                    .defineInRange("anchor3", 18.0D, 0.0D, 100.0D);

            tameAnchor4 = b
                    .comment("Familiarity of the fourth anchor. Default 35 (the horse familiarity cap).")
                    .defineInRange("anchor4", 35.0D, 0.0D, 100.0D);

            tameChanceAtZero = b
                    .comment("Chance per attempt (%) at familiarity 0. Default 1.")
                    .defineInRange("chanceAtZero", 1.0D, 0.0D, 100.0D);

            tameChanceAtAnchor2 = b
                    .comment("Chance per attempt (%) at familiarity 6. Default 5.")
                    .defineInRange("chanceAtAnchor2", 5.0D, 0.0D, 100.0D);

            tameChanceAtAnchor3 = b
                    .comment("Chance per attempt (%) at familiarity 18. Default 20.")
                    .defineInRange("chanceAtAnchor3", 20.0D, 0.0D, 100.0D);

            tameChanceAtAnchor4 = b
                    .comment("Chance per attempt (%) at familiarity 35. Default 90.")
                    .defineInRange("chanceAtAnchor4", 90.0D, 0.0D, 100.0D);

            tameChanceAboveAnchor4 = b
                    .comment("Chance per attempt (%) above anchor4. Default 100 (always tames).")
                    .defineInRange("chanceAboveAnchor4", 100.0D, 0.0D, 100.0D);

            b.pop();

            b.comment("-- More Attributes bonus --",
                      "Rider strength + skill above the threshold adds points per level, added to the curve and clamped to 0-100.")
                    .push("more_attributes");

            maLevelThreshold = b
                    .comment("Strength + skill level at which the bonus starts. Default 30.")
                    .defineInRange("levelThreshold", 30.0D, 0.0D, 1000.0D);

            maLevelBonusPerLevel = b
                    .comment("Taming chance added per level above the threshold, in percentage points. Default 5.")
                    .defineInRange("bonusPerLevel", 5.0D, 0.0D, 100.0D);

            maLevelBonusMax = b
                    .comment("Cap for that bonus, in percentage points. Default 50.")
                    .defineInRange("bonusMax", 50.0D, 0.0D, 100.0D);

            b.pop();

            b.pop();

            b.comment("Extra protection from horse armor").push("armor");

            cactusProofArmor = b
                    .comment("Wearing any horse armor makes the horse immune to cactus damage (including cactus-like blocks). Default true.",
                             "Icy's 15 breeds only.")
                    .define("cactusProofArmor", true);

            b.pop();

            b.comment("Diagnostics. Normal play never needs these; they exist to debug bug reports.").push("debug");

            debugLoad = b
                    .comment("Log the carry-weight pipeline once a second for every horse: which side it runs on,",
                             "the cart that entity is registered as pulling, whether the More Attributes",
                             "attributes exist on that entity, and the computed current / max load.",
                             "Also honoured as a JVM flag, which is handier on a dedicated server:",
                             "  -Dterras_horsies.debugLoad=true",
                             "Verbose by design. Default false.")
                    .define("debugLoad", false);

            b.pop();
        }

        /** 把 double 系数安全地转成 int（四舍五入，且不为负）。 */
        public int scaled(int base, double factor) {
            return Math.max(0, (int) Math.round(base * factor));
        }
    }
}
