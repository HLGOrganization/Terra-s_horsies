package com.tfcicys.horses.compat;

import com.mojang.logging.LogUtils;
import com.tfcicys.horses.TfcIcysHorses;
import com.tfcicys.horses.load.HorseCategory;
import com.tfcicys.horses.load.IcyBreedApi;
import com.tfcicys.horses.load.LoadManager;
import com.tfcicys.horses.load.MoreAttributesApi;

import icy.betterhorses.net.entity.BhBreedHorse;
import net.dries007.tfc.common.entities.livestock.horse.TFCDonkey;
import net.dries007.tfc.common.entities.livestock.horse.TFCHorse;
import net.dries007.tfc.common.entities.livestock.horse.TFCMule;
import net.dries007.tfc.compat.jade.common.EntityTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.slf4j.Logger;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows TFC's livestock readout on Icy's horses in Jade, plus our load line on every TFC-linked
 * equine.
 * <p>
 * <b>Why the ANIMAL part is needed:</b> TFC registers its entity tooltip by <em>concrete class</em> --
 * {@code TFCAnimal.class}, {@code TFCHorse.class}, {@code TFCChestedHorse.class} -- so Icy's horses
 * match none of them and Jade never invokes it. The tooltip body itself is entirely
 * {@code instanceof}-based (it tests {@code TFCAnimalProperties}, {@code MammalProperties} and
 * {@code HorseProperties}), so the only missing piece is the registration.
 * <p>
 * Rather than reimplement the lines, this registers TFC's own {@code EntityTooltips.ANIMAL} against
 * Icy's horse base class. Combined with the {@code HorseProperties} implementation added by
 * {@code BhBreedHorseFamiliarityMixin}, that yields the same sex / familiarity / can-mate /
 * pregnancy / wear / can-ride lines TFC's animals get, using TFC's own translations and styling.
 * Nothing is copied from TFC; the constant is referenced directly.
 * <p>
 * <b>Why the load line is a second, separate component:</b> TFC already registers {@code ANIMAL} for
 * its own {@code TFCHorse} / {@code TFCDonkey} / {@code TFCMule}, so registering it again there would
 * print every line twice. The load line, however, is ours and applies to all of them -- so it is
 * registered separately, on both Icy's horses and TFC's three equines.
 * <p>
 * The load line shows the category, the current load, the cap, and the resulting speed multiplier.
 * Those are the exact numbers {@link LoadManager} uses, so a horse sorted into the wrong category is
 * visible at a glance instead of only showing up as "my config change did nothing".
 * <p>
 * Jade discovers this through the {@code @WailaPlugin} annotation, and {@code registerClient} only
 * runs when Jade is present on the client, so the addon works fine without Jade installed.
 *
 * <h2>为什么要给每个 provider 套一层 try/catch</h2>
 *
 * <p>Jade 会抓住 provider 抛出的任何 {@code Throwable}，然后把责任算在<b>注册该 provider 的模组</b>
 * 头上 —— 悬停里出现一行红字 {@code <发生错误，请反馈至TFCxIcy'sBetterHorses>}，
 * 而真正的堆栈只写进 {@code logs/JadeErrorOutput.txt}。这带来两个问题：
 *
 * <ol>
 *   <li>玩家在游戏里只看到一行的红字，不知道到底哪一步挂了。</li>
 *   <li><b>责任归属会出错</b>：{@link #appendAnimalLines} 调的是 <b>TFC 自己的</b>
 *       {@code EntityTooltips.ANIMAL}。它在某个异常状态下抛异常时，红字照样算在本模组头上。</li>
 * </ol>
 *
 * <p>所以这里自己包一层：失败时在自己的 logger 里打一条带<b>完整诊断串</b>的 ERROR
 * （实体类名、实体类型、品种、负重类别、乘客数），并且只在第一次报告，避免悬停时刷屏。
 * 堆栈同时出现在 {@code latest.log} 与 Jade 的文件里，反馈时给哪一份都行。
 *
 * <p>降级方式：TFC 那部分失败就不显示（其余行还在），负重行失败显示一行灰字提示，
 * 都不再往游戏里抛红字。
 */
@WailaPlugin
public class IcyHorseJadePlugin implements IWailaPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 只报告第一次失败。
     *
     * <p>这几个 provider 是<b>每一帧悬停都会跑</b>的，异常若在 tick 循环里持续发生，
     * 每帧一条 ERROR 会瞬间淹没日志、也拖慢客户端。失败通常是某个实体的某个状态导致的
     * 确定性错误，看到第一条并拿到诊断串就足够定位了。
     */
    private static final AtomicBoolean ANIMAL_FAILURE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean LOAD_FAILURE_LOGGED = new AtomicBoolean(false);

    @Override
    public void registerClient(IWailaClientRegistration registry) {
        // ① Icy 的马：TFC 的完整动物信息 + 我们的负重行。
        //    TFC 不认这些类，所以 ANIMAL 必须由我们补上。
        registry.registerEntityComponent(new IEntityComponentProvider() {
            @Override
            public void appendTooltip(ITooltip tooltip, EntityAccessor access, IPluginConfig config) {
                final Entity entity = access.getEntity();
                appendAnimalLines(tooltip, access, entity);
                if (entity instanceof LivingEntity living) {
                    appendLoadLine(tooltip, living);
                }
            }

            @Override
            public ResourceLocation getUid() {
                return new ResourceLocation(TfcIcysHorses.MOD_ID, "icy_horse");
            }
        }, BhBreedHorse.class);

        // ② TFC 本家的马科：只补负重行。
        //    ANIMAL 已由 TFC 自己的插件注册，这里若重复注册会导致每行打印两次。
        //    注意 registerEntityComponent 只接受单个类，不能传 varargs，故逐个注册。
        final IEntityComponentProvider loadOnly = new IEntityComponentProvider() {
            @Override
            public void appendTooltip(ITooltip tooltip, EntityAccessor access, IPluginConfig config) {
                if (access.getEntity() instanceof LivingEntity living) {
                    appendLoadLine(tooltip, living);
                }
            }

            @Override
            public ResourceLocation getUid() {
                return new ResourceLocation(TfcIcysHorses.MOD_ID, "tfc_equine_load");
            }
        };
        registry.registerEntityComponent(loadOnly, TFCHorse.class);
        registry.registerEntityComponent(loadOnly, TFCDonkey.class);
        registry.registerEntityComponent(loadOnly, TFCMule.class);
    }

    /**
     * 显示 TFC 自己的动物提示行（性别／亲密度／可交配／怀孕／衰老……）。
     *
     * <p>见类注释：这段逻辑属于 TFC，但异常会被 Jade 算到本模组头上，所以要自己接住。
     * 接住之后 TFC 那部分行不显示，但本模组的负重行仍然照常 —— 比整行红字有用。
     */
    private static void appendAnimalLines(ITooltip tooltip, EntityAccessor access, Entity entity) {
        try {
            EntityTooltips.ANIMAL.display(access.getLevel(), entity, tooltip::add);
        } catch (final Throwable t) {
            if (ANIMAL_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.error("[terras_horsies] 调用 TFC 的动物提示（EntityTooltips.ANIMAL）失败，"
                        + "实体 = {}。这只是显示问题，不影响游戏行为，"
                        + "但请把这条堆栈发给作者。后续同类失败不再重复报告。", describe(entity), t);
            }
            tooltip.add(Component.literal("TFC 信息读取失败（详见日志）")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * 追加一行负重信息。
     *
     * <p><b>当前负重必须读同步下来的属性，不能在客户端重算。</b>
     * Jade 的提示框是在客户端拼的，而箱子与车厢的内容都<b>不会同步到客户端</b>：
     * {@code AbstractHorse.inventory}（驴/骡的箱子）与马车的货箱在客户端都是空的。
     * 早先这里直接调 {@code LoadManager.loadOf}，于是服务端明明算对了
     * （实测放进 4 个铁砧 = 256），工具提示却恒等于基础值 ——
     * 这正是「驴装上箱子后负重没生效」的真正原因：不是没生效，是显示读错了地方。
     * 同一课在马车上早就记过一次（见 {@code LoadManager#updatePlayer} 的注释）。
     *
     * <p>上限则取自 {@link LoadManager#capOf}：它只由配置与衰老推导，不读任何容器，
     * 客户端算出来的与服务端一致。
     *
     * <p>同时显示移速倍率：它直接反映惩罚是否生效。只看「当前 / 上限」很难判断体感是否合理，
     * 而倍率是最终作用到马身上的那个系数。
     *
     * <p>刻意不用 {@code Component.translatable}：这一行是诊断性质的即时读数，
     * 字面量不依赖资源加载，更稳。
     */
    private static void appendLoadLine(ITooltip tooltip, LivingEntity living) {
        try {
            final HorseCategory category = HorseCategory.of(living);
            final int cap = LoadManager.capOf(living);
            final Integer current = syncedLoad(living);

            // 与 applyPenalty 同一个公式，保证显示值与实际减速一致。
            final String ratio;
            if (current == null || cap <= 0) {
                ratio = "—";
            } else {
                final double r = (double) current / cap;
                final double mult = r <= 1.0D ? 1.0D : Math.max(0.0D, 1.0D - (r - 1.0D) / 2.0D);
                ratio = String.format(java.util.Locale.ROOT, "%.2f×", mult);
            }

            final String shown = current == null ? "?" : Integer.toString(current);
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT,
                            "%s 负重: %s / %d  (%s)",
                            categoryName(category, living), shown, cap, ratio))
                    .withStyle(ChatFormatting.GRAY));
        } catch (final Throwable t) {
            if (LOAD_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.error("[terras_horsies] 计算 Jade 的负重行失败，实体 = {}。"
                        + "请把这条堆栈发给作者。后续同类失败不再重复报告。", describe(living), t);
            }
            tooltip.add(Component.literal("负重: 读取失败（详见日志）")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * 当前负重，取自 More Attributes 同步到客户端的 {@code equip_load_current}。
     *
     * <p>用 {@code getValue()} 而不是 {@code getBaseValue()}：服务端算惩罚时用的也是它
     * （玩家那份马车重量挂的是 ADDITION 修饰符），这样显示值与实际生效值完全一致。
     *
     * @return 读不到时返回 {@code null}（More Attributes 缺席，或该实体没有这个属性）
     */
    private static Integer syncedLoad(LivingEntity living) {
        final Attribute attribute = MoreAttributesApi.equipLoadCurrent();
        if (attribute == null) {
            return null;
        }
        final AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            return null;
        }
        return (int) Math.round(instance.getValue());
    }

    /**
     * 类别显示名。
     *
     * <p>TFC 三类虽然内部映射到了 PONY／DRAFT／WESTERN，但对玩家来说它们是独立的动物，
     * 直接显示「TFC 驴」比显示「矮马」更清楚，也避免与 Icy 的品种混淆。
     */
    private static String categoryName(HorseCategory category, LivingEntity living) {
        if (living instanceof TFCMule) {
            return "TFC 骡";
        }
        if (living instanceof TFCDonkey) {
            return "TFC 驴";
        }
        if (living instanceof TFCHorse) {
            return "TFC 马";
        }
        return switch (category) {
            case RACE -> "竞速马";
            case PONY -> "矮马";
            case WESTERN -> "西部马";
            case WAR -> "战马";
            case DRAFT -> "挽马";
            case DEFAULT -> "未分类";
        };
    }

    /**
     * 出错时用来定位的诊断串：实体类名 / 实体类型注册名 / 品种 / 负重类别 / 乘客数 / 是否被骑。
     *
     * <p>特意包含实体类名与实体类型：错误往往只在<b>某个模组的某个实体</b>上出现
     * （例如它给实体加了特殊状态），只报一句「显示失败」是定位不了的。
     *
     * <p>自身也包一层 try/catch：它是在 catch 块里跑的，再抛一次就会把真正的原始异常盖掉。
     */
    private static String describe(final Entity entity) {
        if (entity == null) {
            return "null";
        }
        try {
            final StringBuilder sb = new StringBuilder();
            sb.append(entity.getClass().getName());
            sb.append(" / 类型=").append(EntityType.getKey(entity.getType()));
            if (entity instanceof AbstractHorse horse) {
                sb.append(" / 品种=").append(IcyBreedApi.breedIdOf(horse));
                sb.append(" / 类别=").append(HorseCategory.of(horse));
                sb.append(" / 乘客数=").append(horse.getPassengers().size());
                sb.append(" / 被骑=").append(horse.isVehicle());
                sb.append(" / 已驯服=").append(horse.isTamed());
            }
            return sb.toString();
        } catch (final Throwable t) {
            return "<诊断串构造失败: " + t + ">";
        }
    }

    /** Kept for API-shape symmetry with TFC's own plugin; no common-side data is needed. */
    public static Class<? extends Entity> target() {
        return BhBreedHorse.class;
    }
}
