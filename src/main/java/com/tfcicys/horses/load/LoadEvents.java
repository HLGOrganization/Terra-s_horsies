package com.tfcicys.horses.load;

import com.tfcicys.horses.TfcIcysHorses;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 驱动 {@link LoadManager} 的事件入口。
 *
 * <p>节流理由：负重上限与负重都是缓变量，而且应用惩罚要读写 attribute 修饰符，
 * 每 tick 对每只生物做一遍纯属浪费。玩家那边之所以仍需较高的频率，
 * 是因为 More Attributes 每 tick 会 {@code setBaseValue} 覆盖基础值——
 * 但我们的追加量走的是 ADDITION 修饰符，不受覆盖影响，所以 10 tick 一次足够。
 *
 * <p>上下马是唯一需要「立刻」生效的时刻：若等到下一个节流窗口，
 * 玩家会看到坐骑的速度慢一拍才变，或者下车后残留一瞬间的减速。
 */
@Mod.EventBusSubscriber(modid = TfcIcysHorses.MOD_ID)
public final class LoadEvents {

    private LoadEvents() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        final LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!TfcIcysHorses.hasMoreAttributes()) {
            return;
        }

        final long time = entity.level().getGameTime();

        if (entity instanceof Player player) {
            // 玩家：补上马车与骑乘份额。
            if (time % 10L == 0L) {
                LoadManager.updatePlayer(player);
            }
        } else if (time % 20L == 0L) {
            // 生物：整条链路由我们接管。
            LoadManager.updateAnimal(entity);
        }
    }

    /**
     * 上下马时立刻重算两侧。
     *
     * <p>坐骑侧关心的是乘客份额（350 / 300 以及乘客自身负重），
     * 玩家侧关心的是马车份额。两者都在这一处刷新。
     */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.getLevel().isClientSide() || !TfcIcysHorses.hasMoreAttributes()) {
            return;
        }
        if (event.getEntityMounting() instanceof Player player) {
            LoadManager.updatePlayer(player);
        }
        if (event.getEntityBeingMounted() instanceof LivingEntity mount) {
            LoadManager.updateAnimal(mount);
        }
    }

    /**
     * 马车被挂接/解挂时，拉车者的负重应当立刻改变。
     *
     * <p><b>必须挡掉客户端。</b>{@code setPulling} 是双向同步的，客户端也会触发到这里。
     * 而马车货箱 {@code inventory} 是个 {@code ItemStackHandler}，<b>它不同步到客户端</b>
     * ——货物走的是 {@code CARGO}（{@code EntityDataAccessor}）那条路。于是客户端
     * 读到的货物永远是 0，算出「只有自重」，再用同一个 UUID 把自己的修饰符盖到
     * {@code equip_load_current} 上，把服务端同步过来的正确值顶掉。
     *
     * <p>症状就是：服务端日志显示 3276.8，而玩家界面上永远是 512。
     */
    public static void refreshPuller(Entity puller) {
        if (puller == null || puller.level().isClientSide()) {
            return;
        }
        if (puller instanceof Player player) {
            LoadManager.updatePlayer(player);
        } else if (puller instanceof LivingEntity living) {
            LoadManager.updateAnimal(living);
        }
    }
}
