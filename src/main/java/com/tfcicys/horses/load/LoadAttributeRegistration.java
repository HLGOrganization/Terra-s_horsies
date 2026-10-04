package com.tfcicys.horses.load;

import java.util.List;

import com.tfcicys.horses.TFCICYSConfig;
import com.tfcicys.horses.TfcIcysHorses;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把 More Attributes 的两个负重属性挂到玩家以外的生物身上。
 *
 * <p>为什么必须由我们来做：More Attributes 只在
 * {@code AttributeUtils.registerPlayerAttribute} 里写了唯一一行
 * {@code event.add(EntityType.PLAYER, attr)}，全仓库再无第二个 EntityType 引用。
 * 所以马匹上 {@code getAttribute(EquipLoadMax)} 返回 {@code null}，
 * 而它自己的 {@code calculateLoad} / {@code rebuildModifier} 也都以 {@code Player} 为参数。
 * 简单说：上游只做了玩家，生物这一半得由兼容层补齐。
 *
 * <p>注册范围刻意取全体 {@link LivingEntity}：AstikorCarts 的 {@code pull_animals}
 * 默认是空列表，语义为「任何能穿鞍但不由物品操控的生物都能拉车」，
 * 硬编码一份马科白名单反而会在整合包换马匹模组时漏掉。
 */
@Mod.EventBusSubscriber(modid = TfcIcysHorses.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LoadAttributeRegistration {

    private LoadAttributeRegistration() {}

    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (!TfcIcysHorses.hasMoreAttributes()) {
            return;
        }

        final Attribute max = MoreAttributesApi.equipLoadMax();
        final Attribute current = MoreAttributesApi.equipLoadCurrent();
        if (max == null || current == null) {
            // 上游改了字段名或注册失败：整体退化为「无负重」，绝不半残。
            return;
        }

        // 不能直接 COMMON.creatureDefaultLoad.get()：本事件与通用配置的加载同在
        // common setup 阶段，先后没有保证，配置没加载时 get() 会抛异常导致启动硬崩。
        final double defaultCap = TFCICYSConfig.creatureDefaultLoadOrFallback();
        final List<EntityType<? extends LivingEntity>> types = event.getTypes();
        for (final EntityType<? extends LivingEntity> type : types) {
            // 玩家已经由 More Attributes 自己注册过，重复添加会抛错。
            if (type == EntityType.PLAYER) {
                continue;
            }
            if (!event.has(type, max)) {
                event.add(type, max, defaultCap);
            }
            if (!event.has(type, current)) {
                event.add(type, current, 0.0D);
            }
        }
    }
}
