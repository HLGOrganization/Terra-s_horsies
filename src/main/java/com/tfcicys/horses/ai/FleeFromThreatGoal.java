package com.tfcicys.horses.ai;

import com.tfcicys.horses.TFCICYSConfig;

import icy.betterhorses.net.IHorseData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * 让马匹以最快速度逃离一个威胁点。两个触发源：
 *
 * <ol>
 *   <li><b>血量不足</b>（默认 &lt; 30%）：逃离当前的仇恨目标。用户明确要求「包括玩家」——
 *       所以这里不区分目标是人还是生物。</li>
 *   <li><b>附近有马匹死亡</b>：逃离那具尸体的位置，由
 *       {@code AbstractHorseDeathFrightMixin} 通过 {@link FrightenedHorse} 写进来。</li>
 * </ol>
 *
 * <h2>为什么是独立 Goal 而不是改 Icy 的 DefendOwnerGoal</h2>
 *
 * <p>Icy 的 {@code DefendOwnerGoal.m_8045_} 本来就会在血量低于 30% 时<b>放弃追击</b>，
 * 但那只是「停下」，马会站在原地继续挨打。用户要的是<b>主动逃跑</b>，这是新行为，
 * 不适合塞进别人已有的追击逻辑里。
 *
 * <p>它被注册在优先级 <b>0</b>（见 {@code BhBreedHorseFamiliarityMixin.registerGoals}）：
 * 必须高于原版 {@code PanicGoal} 所在的优先级 1，否则同为 {@code MOVE} 旗标时
 * 先注册的 PanicGoal 会一直压住它。优先级 0 原本只有 {@code FloatGoal}，
 * 而 FloatGoal 只占 {@code JUMP} 旗标，两者不冲突。
 *
 * <h2>「最快奔跑速度」</h2>
 *
 * <p>{@link #FLEE_SPEED} = 2.0。<b>作为参照</b>：Icy 自己的 {@code DefendOwnerGoal}
 * 追击用 1.35、冲锋用 1.7，原版 {@code PanicGoal} 用 1.2。2.0 明显高于它们，
 * 是这个模组里给马匹用过的最大值。
 *
 * <h2>怎么挑逃跑方向</h2>
 *
 * <p>用 {@link LandRandomPos#getPosAway} —— 和原版 {@code AvoidEntityGoal} 同一个工具。
 * 它会在「离威胁点更远」的候选点里挑一个<b>实际站得住、走得到</b>的位置，
 * 避免直接往墙里或悬崖外算坐标。返回 {@code null}（找不到合法落点）时退化成
 * 直线反方向 16 格，交给寻路器尽量靠近。
 *
 * <p>每 {@link #REPATH_INTERVAL} tick 重算一次，所以威胁移动时马会跟着调整方向。
 *
 * <h2>两档尺度</h2>
 *
 * <p>半径与速度由触发源通过 {@link FrightenedHorse#tfcicys$fleeRadius()} /
 * {@link FrightenedHorse#tfcicys$fleeSpeed()} 给出，返回 {@code <= 0} 时用本类的默认值：
 *
 * <table border="1">
 *   <caption>两档逃跑</caption>
 *   <tr><th>触发源</th><th>半径</th><th>速度</th><th>怎么收尾</th></tr>
 *   <tr><td>附近有马匹死亡</td><td>16 格</td><td>2.0 冲刺</td>
 *       <td>拉开 16 格后停下</td></tr>
 *   <tr><td>被仙人掌扎到</td><td>4 格</td><td>1.2 走</td>
 *       <td><b>脱离那丛仙人掌就停</b></td></tr>
 * </table>
 *
 * <p>仙人掌那一档必须短：仙人掌<b>成丛</b>生长，让马按「逃跑」冲刺十几格，
 * 它很可能一头扎进旁边另一丛里继续挨扎。所以那一档真正的收尾条件是
 * 「身上和紧邻还有没有仙人掌」——判断写在
 * {@link FrightenedHorse#tfcicys$fleePoint()} 里，本类的半径只是防呆上限。
 */
public class FleeFromThreatGoal extends Goal {

    /** 长距离冲刺（附近有马匹死亡）用的速度修正。见类注释里的对照值。 */
    private static final double FLEE_SPEED = 2.0D;

    /** 重新选点的间隔（tick）。太短会一直打断寻路，太长会跟不上移动的威胁。 */
    private static final int REPATH_INTERVAL = 8;

    /** 长距离冲刺时的水平搜索半径，同时也是那一档的默认脱离半径。 */
    private static final int SEARCH_XZ = 16;

    /** 退化方案（直线反推）的最小距离，免得半径很小时原地打转。 */
    private static final double MIN_PUSH = 3.0D;

    /** {@link LandRandomPos#getPosAway} 的垂直搜索半径。 */
    private static final int SEARCH_Y = 7;

    private final AbstractHorse horse;

    /** 当前要远离的点。 */
    @Nullable private Vec3 threat;

    /** true = 这次逃跑是因为血量不足、在躲仇恨目标；false = 附近有马匹死亡或挨了仙人掌。 */
    private boolean fromCombatTarget;

    /**
     * 本次逃跑的脱离半径（格）：马离逃离点超过它就算脱离，Goal 自行结束。
     *
     * <p>同时也是 {@link LandRandomPos#getPosAway} 的水平搜索半径 —— 两者取同一个值，
     * 「往哪儿跑」和「跑到哪儿算完」才自洽。否则会出现「跑了 16 格但 4 格就该停」这种矛盾。
     */
    private double radius = SEARCH_XZ;

    /** 本次逃跑的速度修正。 */
    private double speed = FLEE_SPEED;

    private int repathTimer;

    public FleeFromThreatGoal(final AbstractHorse horse) {
        this.horse = horse;
        // MOVE 用来跑，LOOK 让它别一边逃一边回头盯着威胁。
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 被骑着时不接管移动：这时候方向由玩家决定，自动逃跑只会和玩家抢操控。
        if (this.horse.isVehicle()) {
            return false;
        }
        if (!(this.horse instanceof FrightenedHorse)) {
            return false;
        }
        final FrightenedHorse frightened = (FrightenedHorse) this.horse;

        // ⓪ 环境伤害（岩浆、岩浆块、火焰、甜浆果丛……）。
        //    排在最前面，而且刻意不看 neutralCombat、也不看野马/家马：
        //    「被岩浆烧死」和「要不要中立」无关，家养马同样得跑出来。
        //    收尾完全交给计时器 —— 每次挨烫续一次，不再受伤就结束（见 canContinueToUse）。
        final Vec3 hazard = frightened.tfcicys$fleePoint();
        if (hazard != null && frightened.tfcicys$isHazardFlee()) {
            this.threat = hazard;
            this.fromCombatTarget = false;
            final double hazardRadius = frightened.tfcicys$fleeRadius();
            this.radius = hazardRadius > 0.0D ? Math.max(MIN_PUSH, hazardRadius) : SEARCH_XZ;
            final double hazardSpeed = frightened.tfcicys$fleeSpeed();
            this.speed = hazardSpeed > 0.0D ? hazardSpeed : FLEE_SPEED;
            return true;
        }

        if (!TFCICYSConfig.neutralCombat()) {
            return false;
        }
        // 下面两条逃跑规则只给野马。已驯服的马（Icy 归属，或 TFC 亲密度 ≥ 0.15）不逃。
        // 判定方式见 TFCICYSConfig#isWildHorse。
        if (!TFCICYSConfig.isWildHorse(this.horse)) {
            return false;
        }

        // ① 血量不足 —— 躲开威胁源（人也是威胁源）。
        //    注意这里<b>不</b>直接读 bh_getCombatTarget：见 tfcicys$threat() 的注释，
        //    Icy 会在同一个 30% 阈值上把那个目标清掉，正好把这里的触发条件抹掉。
        final float ratio = TFCICYSConfig.fleeHealthRatio();
        if (ratio > 0.0F && this.horse.getHealth() < this.horse.getMaxHealth() * ratio) {
            final LivingEntity target = this.tfcicys$threat();
            if (target != null) {
                this.threat = target.position();
                this.fromCombatTarget = true;
                // 躲威胁源：跑到 escapeDistance 就算脱离（收尾在 canContinueToUse）。
                this.radius = Math.max(MIN_PUSH, TFCICYSConfig.escapeDistance());
                this.speed = FLEE_SPEED;
                return true;
            }
        }

        // ② 外部写入的受惊点：附近有马匹死亡，或挨了仙人掌。
        //    两档的半径／速度由写入方给出，<= 0 表示用本类的默认值。
        final Vec3 fright = frightened.tfcicys$fleePoint();
        if (fright != null) {
            this.threat = fright;
            this.fromCombatTarget = false;
            final double r = frightened.tfcicys$fleeRadius();
            this.radius = r > 0.0D ? Math.max(MIN_PUSH, r) : SEARCH_XZ;
            final double s = frightened.tfcicys$fleeSpeed();
            this.speed = s > 0.0D ? s : FLEE_SPEED;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.horse.isVehicle()) {
            return false;
        }
        if (this.threat == null) {
            return false;
        }
        if (!this.fromCombatTarget) {
            final FrightenedHorse frightened = (FrightenedHorse) this.horse;
            final Vec3 fright = frightened.tfcicys$fleePoint();
            if (fright == null) {
                // 两种结束：时长到了，或者（仙人掌那一档）已经不挨着仙人掌了。
                return false;
            }
            // 防呆上限：真的拉开 radius 格也算脱离，顺手清掉状态，免得 Goal 反复重启。
            // 仙人掌那一档半径只有 4 格，通常根本走不到这里 —— 它的实际停止点
            // 是上面那句 fleePoint() == null，也就是「脱离即停」。
            //
            // 环境伤害那一档刻意<b>没有</b>这个上限：走够 6 格就停吗？不 ——
            // 还在挨烫就继续走。它唯一的收尾条件是不再受伤（计时器到点）。
            if (!frightened.tfcicys$isHazardFlee()
                    && this.horse.position().distanceToSqr(fright) >= this.radius * this.radius) {
                frightened.tfcicys$stopFleeing();
                return false;
            }
            return true;
        }

        // 血量不足触发：血量回来了就停。
        final float ratio = TFCICYSConfig.fleeHealthRatio();
        if (ratio <= 0.0F || this.horse.getHealth() >= this.horse.getMaxHealth() * ratio) {
            return false;
        }
        final LivingEntity target = this.tfcicys$threat();
        if (target == null) {
            return false;
        }
        // 拉开到 escapeDistance 就算「脱离仇恨」：清掉目标与攻击者记忆，逃到这里为止。
        // 这是唯一的收尾口 —— 不清的话「血少 + 记着攻击者」会让 Goal 每 tick 重启一次，
        // 马会一边原地抖动一边反复重算路径。
        final double escape = TFCICYSConfig.escapeDistance();
        if (this.horse.distanceToSqr(target) > escape * escape) {
            IHorseData.of(this.horse).bh_setCombatTarget(null);
            ((FrightenedHorse) this.horse).tfcicys$forgetAttacker();
            return false;
        }
        // 目标在动，每 tick 更新要远离的点。
        this.threat = target.position();
        return true;
    }

    @Override
    public void start() {
        this.repathTimer = 0;
        this.tfcicys$repath();
    }

    @Override
    public void tick() {
        if (--this.repathTimer <= 0) {
            this.tfcicys$repath();
        }
    }

    @Override
    public void stop() {
        this.threat = null;
        this.fromCombatTarget = false;
        this.radius = SEARCH_XZ;
        this.speed = FLEE_SPEED;
        this.horse.getNavigation().stop();
    }

    /** 重新挑一个远离点并下指令。 */
    private void tfcicys$repath() {
        this.repathTimer = REPATH_INTERVAL;
        final Vec3 avoid = this.threat;
        if (avoid == null) {
            return;
        }
        // 搜索半径跟着脱离半径走：「往哪儿跑」和「跑到哪儿算完」必须自洽。
        final int search = Mth.clamp((int) Math.round(this.radius), (int) MIN_PUSH, SEARCH_XZ);
        Vec3 dest = LandRandomPos.getPosAway(this.horse, search, SEARCH_Y, avoid);
        if (dest == null) {
            dest = this.tfcicys$straightAway(avoid);
        }
        if (dest != null) {
            this.horse.getNavigation().moveTo(dest.x, dest.y, dest.z, this.speed);
        }
    }

    /**
     * {@link LandRandomPos} 找不到落点时的退化方案：正相反方向推 {@link #radius} 格。
     *
     * <p>与威胁点完全重合时分不出「反方向」，此时随便挑一个水平朝向 —— 总比原地不动强。
     */
    @Nullable
    private Vec3 tfcicys$straightAway(final Vec3 avoid) {
        Vec3 away = this.horse.position().subtract(avoid);
        if (away.lengthSqr() < 1.0E-4D) {
            final float yaw = this.horse.getRandom().nextFloat() * ((float) Math.PI * 2.0F);
            away = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        }
        final Vec3 dir = away.normalize();
        final double push = Math.max(MIN_PUSH, this.radius);
        // 只推水平分量：垂直方向交给寻路器，免得算到天上或地底。
        return this.horse.position().add(dir.x * push, 0.0D, dir.z * push);
    }

    /**
     * 谁在威胁这匹马：优先「最后打我的人」，其次才是 Icy 的战斗目标。
     *
     * <h2>为什么不能只读战斗目标</h2>
     *
     * <p>低血量逃跑最初读的就是 {@code bh_getCombatTarget}，结果和 Icy 撞在同一个阈值上。
     * Icy 的 {@code DefendOwnerGoal.canContinueToUse}（{@code m_8045_}）在血量 &lt; 30% 时
     * 放弃追击，<b>并且顺手把战斗目标清成 null</b>（字节码偏移 163 的
     * {@code bh_setCombatTarget}）。而 30% 正是这里的触发阈值 ——
     * 马刚掉到该逃的血量，触发源就在同一 tick 被抹掉，表现为<b>打到残血却站着不动</b>。
     *
     * <p>而且战斗目标本来就可能不存在：亲密度 ≥ 18 的马被玩家打时不反击（需求要的），
     * PvP 关掉时也不会把玩家写成目标。「血少了要逃」不该取决于「它刚才有没有还手」。
     *
     * <p>攻击者记忆由 {@code AbstractHorseThreatMixin} 在 {@code hurt} 的 HEAD 写入，
     * 不经过 Icy 的战斗系统，所以上面那些开关都影响不到它。战斗目标只作为<b>兜底</b>保留：
     * Icy 自己也会给「替主人出头」写目标，那种情况下马被打到时没有攻击者记忆，
     * 但一样应该在残血时跑开。
     */
    @Nullable
    private LivingEntity tfcicys$threat() {
        final LivingEntity attacker = ((FrightenedHorse) this.horse).tfcicys$recentAttacker();
        if (attacker != null) {
            return attacker;
        }
        return this.tfcicys$combatTarget();
    }

    /**
     * 把 Icy 的战斗目标 UUID 解析成活体。
     *
     * <p>和 Icy 自己的 {@code DefendOwnerGoal.m_8036_} 用同一条路径：
     * {@code ((ServerLevel) level).getEntity(uuid)}，再要求它还是活的。
     */
    @Nullable
    private LivingEntity tfcicys$combatTarget() {
        final UUID id = IHorseData.of(this.horse).bh_getCombatTarget();
        if (id == null) {
            return null;
        }
        if (!(this.horse.level() instanceof ServerLevel level)) {
            return null;
        }
        final Entity entity = level.getEntity(id);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }
}
