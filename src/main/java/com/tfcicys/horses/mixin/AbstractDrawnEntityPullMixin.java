package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tfcicys.horses.load.CartPullRegistry;
import com.tfcicys.horses.load.LoadEvents;

import de.mennomax.astikorcarts.entity.AbstractDrawnEntity;
import net.minecraft.world.entity.Entity;

/**
 * 维护 {@link CartPullRegistry} 的「拉车者 → 马车」索引。
 *
 * <p>{@code AbstractDrawnEntity.setPulling(Entity)} 是挂接与解除挂接的唯一入口
 * （{@code attemptReattach}、{@code shouldRemovePulling} 等最终都汇到这里），
 * 所以在这一处记录即可覆盖所有状态变化。
 *
 * <p>用 {@code @Inject} 而非 {@code @Redirect}/{@code @Overwrite}：
 * 原逻辑照常执行，我们只在旁边记一笔，任何一方出问题都不至于让马车失去动力。
 *
 * <p>注意这里 shadow 的是 <b>方法</b> {@code getPulling()} 而不是字段 {@code pulling}：
 * 字段名不会进 refmap，注解处理器会报「Unable to locate obfuscation mapping for @Shadow field」。
 * 去掉混淆映射（{@code remap = false}）同样是必须的——这些名字属于 AstikorCarts，不是原版。
 */
@Mixin(AbstractDrawnEntity.class)
public abstract class AbstractDrawnEntityPullMixin {

    @Shadow(remap = false)
    public abstract Entity getPulling();

    @Inject(method = "setPulling", at = @At("TAIL"), remap = false)
    private void tfcicys$trackPulling(Entity puller, CallbackInfo ci) {
        // 先把这辆车从旧记录里摘掉：换马时不能留下悬空的旧映射。
        final Entity self = (Entity) (Object) this;
        final Entity previous = CartPullRegistry.pullerOf(self);
        CartPullRegistry.detach(self);
        final Entity pulling = this.getPulling();
        if (pulling != null) {
            CartPullRegistry.attach(pulling, self);
        }

        // 挂接状态变了，两边的负重都得立刻重算：旧拉车者卸下、新拉车者加上。
        // 若只等下一个节流窗口，玩家会看到速度慢一拍才降下来。
        if (previous != null && previous != pulling) {
            LoadEvents.refreshPuller(previous);
        }
        if (pulling != null) {
            LoadEvents.refreshPuller(pulling);
        }
    }
}
