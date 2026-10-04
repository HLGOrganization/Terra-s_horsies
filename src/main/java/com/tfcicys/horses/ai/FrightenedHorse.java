package com.tfcicys.horses.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 「有东西吓到我了」这个状态的载体。
 *
 * <p>存在的理由是<b>跨类传递</b>：吓到马匹的有两个来源（马匹死亡、被仙人掌扎），
 * 它们分别在别的 mixin 里，而逃跑行为是一个 {@code Goal}（{@link FleeFromThreatGoal}）。
 * 两者都要读写同一份状态，而这份状态是 {@code BhBreedHorseFamiliarityMixin} 里的
 * {@code @Unique} 字段 —— mixin 的私有字段没法从别的类直接碰，所以开一个接口让目标类实现它。
 *
 * <p>由 {@code BhBreedHorseFamiliarityMixin} 实现：在类声明上 {@code implements FrightenedHorse}，
 * 调用方只需要 {@code horse instanceof FrightenedHorse} 就能拿到这几个方法。
 *
 * <p>作用范围只有 Icy 的马（只有那个 mixin 实现了它），所以不需要额外的类型判断。
 *
 * <h2>为什么逃跑要分「长」「短」两种</h2>
 *
 * <p>两个触发源要的行为完全不同：
 *
 * <table border="1">
 *   <caption>两种逃跑的差别</caption>
 *   <tr><th>触发源</th><th>要的行为</th><th>半径</th><th>速度</th></tr>
 *   <tr>
 *     <td>附近有马匹死亡</td>
 *     <td>用最快速度拉开距离</td>
 *     <td>默认（16 格）</td>
 *     <td>冲刺 2.0</td>
 *   </tr>
 *   <tr>
 *     <td>被仙人掌扎到</td>
 *     <td>只挪出那丛仙人掌，不要跑远</td>
 *     <td>4 格</td>
 *     <td>走 1.2</td>
 *   </tr>
 * </table>
 *
 * <p>仙人掌那一档之所以必须短：仙人掌是<b>成丛</b>长的，让马按「逃跑」的逻辑冲刺十几格，
 * 它很可能一头扎进旁边另一丛里继续挨扎。所以短距离 + 脱离即停（见下文）才是对的。
 */
public interface FrightenedHorse {

    /**
     * 记录一个「逃离点」，长距离冲刺一档，半径与速度取 {@link FleeFromThreatGoal} 的默认值。
     *
     * <p>用于「附近有马匹死亡」：那时候确实要跑得越远越好。
     *
     * @param point 要远离的世界坐标
     * @param ticks 持续多少 tick；从「现在」开始计时
     */
    void tfcicys$startFleeing(Vec3 point, int ticks);

    /**
     * 记录一个「逃离点」，短距离挪开一档。
     *
     * <p>用于「被仙人掌扎到」：只挪出那丛仙人掌即可，跑远反而危险。
     *
     * @param point  要远离的世界坐标（通常是扎到它的那株仙人掌）
     * @param ticks  持续多少 tick（<b>只是安全上限</b>：脱离仙人掌后会立刻结束，见下）
     * @param radius 最多挪开多少格；{@code <= 0} 表示用默认半径
     * @param speed  移动速度修正；{@code <= 0} 表示用默认速度
     */
    void tfcicys$startFleeingLocal(Vec3 point, int ticks, double radius, double speed);

    /**
     * 当前有效的逃离点；没有、已过期、或者<b>已经没必要再逃</b>时返回 {@code null}。
     *
     * <p>过期判断用的是 {@code level().getGameTime()}，所以只在服务端有意义。
     *
     * <p>「已经没必要再逃」指短距离那一档：一旦马身上和紧邻都没有类仙人掌方块了，
     * 这里就直接返回 {@code null}，{@link FleeFromThreatGoal} 随之结束。
     * 这是仙人掌「成丛」问题的关键 —— 不靠跑固定距离，而靠<b>事实脱离</b>来收尾。
     */
    @Nullable
    Vec3 tfcicys$fleePoint();

    /** 本次逃跑的目标半径（格）。{@code <= 0} 表示由 {@link FleeFromThreatGoal} 用默认值。 */
    double tfcicys$fleeRadius();

    /** 本次逃跑的速度修正。{@code <= 0} 表示由 {@link FleeFromThreatGoal} 用默认值。 */
    double tfcicys$fleeSpeed();

    /**
     * 记下「刚才是谁打了我」。
     *
     * <h2>为什么不能只靠 {@code bh_getCombatTarget}</h2>
     *
     * <p>低血量逃跑原先读的是 Icy 的战斗目标，结果被 Icy 自己在同一个阈值上清掉了 ——
     * 已从字节码确认，{@code DefendOwnerGoal.canContinueToUse}（{@code m_8045_}）里：
     *
     * <pre>
     * 77: horse.getHealth()          // m_21223_
     * 84: horse.getMaxHealth()       // m_21233_
     * 87: ldc 0.3f
     * 89: fmul
     * 91: iflt 161                   // 血量 &lt; 30% 就跳去「放弃」
     * ...
     * 163: IHorseData.bh_setCombatTarget(null)   // 顺手把目标清了
     * </pre>
     *
     * <p>而「血量 &lt; 30%」正好就是逃跑的触发条件。两者阈值完全相同，所以马一旦掉到
     * 该逃的血量，触发源就在同一 tick 被抹掉 —— 表现为<b>打到残血却站着不动</b>。
     *
     * <p>另外，战斗目标本来就<b>不一定存在</b>：亲密度 ≥ 18 的马被玩家打时压根不反击
     * （这是需求里明确要的），PvP 关掉时也不会把玩家写成目标。可「血少了要逃」不该
     * 取决于「它刚才有没有还手」。
     *
     * <p>所以这里独立记一份「最后打我的人／生物」，由 {@code AbstractHorseThreatMixin}
     * 在 {@code hurt} 的 HEAD 写入 —— 那条路径不经过 Icy 的战斗系统，不受 PvP 设置、
     * 亲密度曲线、是否骑乘影响。带时限：停止挨打一段时间后马就冷静下来。
     *
     * @param attacker 攻击者；为 {@code null} 或马自己时忽略
     */
    void tfcicys$noteAttacker(LivingEntity attacker);

    /**
     * 最近一次攻击者；没有、已超时、或它已经死了／不在同一维度时返回 {@code null}。
     *
     * <p>返回的是活体实体，所以 {@link FleeFromThreatGoal} 可以直接拿它的
     * {@code position()} 当逃离点。
     */
    @Nullable
    LivingEntity tfcicys$recentAttacker();

    /** 忘掉攻击者（逃到安全距离、或已经冷静下来了）。 */
    void tfcicys$forgetAttacker();

    /**
     * 记下「主人刚被谁打了」。
     *
     * <p>替主人报仇时，仇恨时间要从<b>最后一次挑衅</b>算起：敌人还在打主人（或打马自己），
     * 计时就一次次续上去；停手满一段时间才真正脱战。所以主人每次挨打都要在这里记一笔，
     * 而不是只在开打时记一次。
     *
     * <p>由 {@code BhHorseCombatAlertDefendMixin} 在 Icy 的 {@code defend} 入口调用 ——
     * 那个方法的 {@code IHorseData} 参数经过字节码确认就是马实体本身
     * （{@code IHorseData.of} 编译成 {@code aload_0; checkcast; areturn}）。
     *
     * @param attacker 打主人的人／生物；为 {@code null} 时忽略
     */
    void tfcicys$noteOwnerThreat(LivingEntity attacker);

    /**
     * 「{@code attacker} 最后一次挑衅这匹马」的游戏刻 —— 取两个来源里更新的那个：
     * <b>打了马自己</b>，或者<b>打了马的主人</b>。
     *
     * <p>两个都没有、或者都不是 {@code attacker} 干的，返回 {@code Long.MIN_VALUE}。
     *
     * <p>带的是<b>时刻</b>而不是布尔值，因为脱战判定的形式是
     * 「现在 − 最后一次被挑衅 ＞ 本场战斗的时限」：这样敌人持续动手时计时自然续上，
     * 停手后到点就脱战，不需要额外的「重置」逻辑。
     */
    long tfcicys$provokedAt(LivingEntity attacker);

    /** 立刻清掉逃离状态（逃到了、或者被骑上去了）。 */
    void tfcicys$stopFleeing();
}
