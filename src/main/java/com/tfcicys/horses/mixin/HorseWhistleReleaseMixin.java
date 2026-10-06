package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tfcicys.horses.load.WhistleRelease;

import icy.betterhorses.net.HorseManagement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * 马未加载时 Icy 走的是「丢弃旧身体 → 按快照在玩家身边重塑一匹」。
 *
 * <p>这条路必须在<b>丢旧身体时</b>就解挂：重塑出来的新马用的是同一个 UUID，
 * 而 AstikorCarts 的车是按 {@code pullingUUID} 记账的（{@code attemptReattach} 会按 id 重新认领），
 * 所以不解挂的话，车会在新马出现后立刻重新挂上、再飞过去一次。
 *
 * <p>{@code discardOldBody(MinecraftServer, AbstractHorse)} 是 void，回调用 {@code CallbackInfo}。
 */
@Mixin(HorseManagement.class)
public abstract class HorseWhistleReleaseMixin {

    @Inject(method = "discardOldBody", at = @At("HEAD"), remap = false)
    private static void tfcicys$releaseBeforeDiscard(MinecraftServer server, AbstractHorse oldBody,
            CallbackInfo ci) {
        WhistleRelease.release(oldBody);
    }
}
