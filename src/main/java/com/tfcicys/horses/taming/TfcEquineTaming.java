package com.tfcicys.horses.taming;

import com.tfcicys.horses.TFCICYSConfig;

import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.dries007.tfc.common.entities.livestock.horse.HorseProperties;
import net.dries007.tfc.common.entities.livestock.horse.TFCChestedHorse;
import net.dries007.tfc.common.entities.livestock.horse.TFCHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;

/**
 * TFC 马科（马／驴／骡）的「按亲密度概率驯服」。
 *
 * <h2>为什么 Icy 那套照搬过来没用</h2>
 *
 * <p>Icy 的马走的是原版驯服流程，只要把 {@code getTemper()} 的返回值换掉就能把概率
 * 换成亲密度曲线（见 {@code BhBreedHorseFamiliarityMixin}）。TFC 的马不行，
 * 因为它把「已驯服」整个换掉了：
 *
 * <pre>
 *   // TFCHorse / TFCChestedHorse
 *   public boolean isTamed() { return getFamiliarity() &gt; 0.15f; }   // ← 只看亲密度
 * </pre>
 *
 * <p>于是原版驯服的两个环节各自断掉：
 *
 * <ol>
 *   <li><b>概率是 0</b>。原版 {@code getTemper()} 出厂就是 0，成功率 = temper/100 = 0%。
 *       喂食和失败才会慢慢涨，所以「骑着驯服」几乎不可能发生。</li>
 *   <li><b>成功也没用</b>。原版掷骰成功调的是 {@code tameWithName()}，它只设原版的
 *       tame 标记和主人；而 TFC 的 {@code isTamed()} <b>完全不看那个标记</b>。
 *       于是马在 TFC 眼里始终未驯服，{@code RunAroundLikeCrazyGoal} 永远在跑，
 *       玩家被无限甩下来 —— 这正是「概率驯服在 TFC 马驴骡上没生效」的现象。</li>
 * </ol>
 *
 * <p>所以这里补两环：把 {@code getTemper()} 的返回值换成亲密度曲线（第 1 环），
 * 并在 {@code tameWithName()} 返回时把亲密度顶过 TFC 的驯服线（第 2 环）。
 * 两处注入都在 {@code AbstractHorseTamingMixin} 里。原版的甩人动作、粒子、音效、
 * {@code TAME_ANIMAL} 统计全部保持不变，因为那一切都是原版自己在做的。
 *
 * <h2>作用范围</h2>
 *
 * <p>只认 TFC 自己的三个类，判定见 {@link #isTfcEquine}。Icy 的马虽然也实现了
 * {@code HorseProperties}（那是本模组加上的），但它们有自己的驯服实现，
 * 这里刻意不接手，避免两套机制在同一个 {@code getTemper()} 上叠算。
 */
public final class TfcEquineTaming {

    private TfcEquineTaming() {}

    /**
     * 这匹马是不是 TFC 自己的马科。
     *
     * <p>{@code TFCDonkey} 与 {@code TFCMule} 都继承抽象的 {@code TFCChestedHorse}，
     * 所以两个 {@code instanceof} 就覆盖了马／驴／骡三种。
     *
     * <p>不用更宽松的 {@code instanceof HorseProperties}：Icy 的马由本模组的 mixin
     * 实现了那个接口，宽判会把 Icy 一起卷进来（它们已有自己的驯服实现）。
     */
    public static boolean isTfcEquine(final AbstractHorse horse) {
        return horse instanceof TFCHorse || horse instanceof TFCChestedHorse;
    }

    /**
     * 该用哪个 temper 值（= 成功率百分比）。
     *
     * <p>原版 {@code getMaxTemper()} 恒为 100，且掷骰是
     * {@code random.nextInt(100) < temper}，所以返回值就是百分比。
     *
     * <p>只在「TFC 马科 + 被玩家骑着 + 尚未驯服」时才换成曲线，其余一律返回原版值：
     * 空着站的马、已驯服的马、以及非 TFC 的马都不受影响。
     *
     * @param vanillaTemper 原版数值，作为所有不适用情形的回退
     */
    public static int temperOf(final AbstractHorse horse, final int vanillaTemper) {
        if (!TFCICYSConfig.tamingByFamiliarity() || horse.isTamed()) {
            return vanillaTemper;
        }
        if (!isTfcEquine(horse)) {
            return vanillaTemper;
        }
        // 与 RunAroundLikeCrazyGoal.tick() 里的 getPassengers().get(0) 保持一致。
        if (!(horse.getFirstPassenger() instanceof Player rider)) {
            return vanillaTemper;
        }
        return (int) Math.round(TFCICYSConfig.tamePercent(rider, TFCICYSConfig.familiarityOf(horse)));
    }

    /**
     * 原版掷骰成功之后，把 TFC 认账的那条线补上。
     *
     * <p>TFC 用 {@code getFamiliarity() > TAMED_FAMILIARITY} 判断驯服，
     * 所以这里必须推到<b>严格大于</b>阈值，否则马在 TFC 看来依然未驯服。
     * 已经喂得更熟的马不会被降回来。
     */
    public static void promoteFamiliarity(final AbstractHorse horse) {
        if (!isTfcEquine(horse) || !(horse instanceof TFCAnimalProperties animal)) {
            return;
        }
        final float threshold = HorseProperties.TAMED_FAMILIARITY;
        if (animal.getFamiliarity() <= threshold) {
            animal.setFamiliarity(threshold + 0.01F);
        }
    }
}
