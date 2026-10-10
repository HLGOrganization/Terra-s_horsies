package com.tfcicys.horses;

import icy.betterhorses.net.IHorseData;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 羁绊的第二个作用：马会自己慢慢回血，羁绊越高回得越快。
 *
 * <p>出厂曲线是<b>每 1 点羁绊快 0.1 秒</b>：0 羁绊 15 秒一次、50 羁绊 10 秒、80 羁绊 7 秒、
 * 100 羁绊 5 秒 —— 四个锚点正好落在同一条直线上（{@code 15 - 0.1 × bond}），
 * 所以实现成一个两锚点线性插值（{@link TFCICYSConfig#bondRegenIntervalTicks(int)}），
 * 而不是写四段分段函数。每次回 {@code healPerPulse} 点生命（默认 1.0 = 半颗心）。
 *
 * <h2>为什么用事件而不是 mixin</h2>
 *
 * <p>回血不需要改任何人的方法体：本模组已经在监听 {@code LivingEvent.LivingTickEvent}
 * （见 {@code load/LoadEvents}），多一个订阅者即可。Icy 那边完全不用碰 ——
 * 与"羁绊给属性加成"必须接管公式（见 {@code BhBondAttributesMixin}）不同。
 *
 * <h2>为什么计时是无状态的</h2>
 *
 * <p>相位用 {@code (世界时间 + 实体 id) % 间隔}，而不是把"下次回血时刻"存进实体 NBT：
 *
 * <ul>
 *   <li>不往马的存档里塞自定义键；</li>
 *   <li>读档/重启后不会因为"存的那个时刻早就过去了"而一次补出一串心跳 ——
 *       用累加式计时器时这是必须额外防的坑；</li>
 *   <li>实体 id 天然把同一时刻的回血错开，不会全场马同时起搏。</li>
 * </ul>
 *
 * <p>代价是羁绊变化时会顺带改变相位，可能提前或推迟一次脉冲 —— 对"慢速自愈"无所谓。
 *
 * <h2>作用范围</h2>
 *
 * <p>默认只对<b>有主人</b>的马生效（{@code bond.regen.ownedOnly = true}）：
 * 羁绊本来就是"和主人的关系"，而且关掉这个开关等于让世界上每一匹野马
 * （以及 TFC 的家畜马）按 15 秒一档自愈，那是另一个量级的平衡改动。
 *
 * <p><b>会与 Icy 自己的同类能力叠加</b>：Mustang 的 {@code mustang_self_heal}
 * 与 Pony 类别的 {@code pony_heal} 也各自在回血，本功能不会去关它们。
 * 不想要叠加就把 Icy 配置 `abilities.breed.mustang_self_heal` 等关掉。
 *
 * <p>只在服务端跑：生命值是服务端权威的，客户端跑一遍只会白算。
 */
@Mod.EventBusSubscriber(modid = TfcIcysHorses.MOD_ID)
public final class BondRegeneration {

    private BondRegeneration() {}

    @SubscribeEvent
    public static void onLivingTick(final LivingEvent.LivingTickEvent event) {
        // 只有马科有羁绊。Icy 的 AbstractHorseMixin 在 AbstractHorse 上 implements IHorseData，
        // 所以马的这条 instanceof 就是"有没有羁绊数据"的判据（TFC 的马同样成立）。
        if (!(event.getEntity() instanceof AbstractHorse horse)) {
            return;
        }
        if (horse.level().isClientSide()) {
            return;
        }
        if (!TFCICYSConfig.bondRegenEnabled()) {
            return;
        }
        // 满血什么都不做：heal() 会把生命值 set 回原值，白标一次脏数据。
        if (horse.getHealth() >= horse.getMaxHealth()) {
            return;
        }
        final double heal = TFCICYSConfig.bondRegenHeal();
        if (heal <= 0.0D) {
            return;
        }

        final IHorseData data = IHorseData.of(horse);
        if (TFCICYSConfig.bondRegenOwnedOnly() && !data.bh_isOwned()) {
            return;
        }

        final int interval = TFCICYSConfig.bondRegenIntervalTicks(data.bh_getBond());
        // 见类注释「为什么计时是无状态的」。
        if (Math.floorMod(horse.level().getGameTime() + horse.getId(), interval) != 0L) {
            return;
        }
        horse.heal((float) heal);
    }
}
