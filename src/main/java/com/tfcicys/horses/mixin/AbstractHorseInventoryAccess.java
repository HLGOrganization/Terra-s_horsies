package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * 直接取 {@code AbstractHorse.inventory} 这个 {@code SimpleContainer}。
 *
 * <p>为什么需要它：驴/骡（含 TFC 的 {@code TFCDonkey}/{@code TFCMule}，
 * 它们继承 {@code TFCChestedHorse} → 原版 {@code AbstractChestedHorse}）的箱子内容
 * 就存在这个字段里，而它是 {@code protected}，且**没有公开的访问器**。
 *
 * <p>为什么不走 Forge 的 {@code ITEM_HANDLER} 能力：那条路有一串前置条件
 * （{@code itemHandler != null} 且实体存活），实测读出来是空；
 * 而界面上显示的正是这个字段里的内容，直接取字段没有中间环节。
 *
 * <p>用 {@code @Accessor} 而不是反射：这是原版类，字段名会进 refmap，
 * 由 Mixin 注解处理器核对，改了名会在编译期报错，而不是运行时静默返回空。
 */
@Mixin(AbstractHorse.class)
public interface AbstractHorseInventoryAccess {

    /** 这匹马的物品栏：0 = 鞍、1 = 马铠，2 起是箱子内容（有箱子时共 17 格）。 */
    @Accessor("inventory")
    SimpleContainer tfcicys$inventory();
}
