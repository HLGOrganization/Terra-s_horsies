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
 * <p>{@code AbstractDrawnEntity.setPulling(Entity)} 是挂接与解除挂接的主入口
 * （{@code attemptReattach}、{@code pulledTick}、受伤脱落等最终都汇到这里），
 * 在这里记录能覆盖绝大多数状态变化。
 *
 * <p><b>但它不是唯一的写入点。</b>{@code shouldStopPulledTick()} 在拉车者不是玩家时
 * 会绕开 {@code setPulling}，直接 {@code this.pulling = null}（字节码里就是一条
 * {@code putfield pulling}）。只挂 {@code setPulling} 的索引会因此与事实脱节：
 * 车已经不拉了，马的负重里还挂着它，或者反过来该挂的没挂上——
 * 后者正是「马匹负重太轻、装货不更新」的成因之一。
 * 所以这里再加一个每 tick 的锚点 {@code attemptReattach()}（由 {@code tick()} 每 tick
 * 调用一次），按字段把索引重写一遍：索引可以短暂偏离，但不会长期错误。
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

    /**
     * 每 tick 按上游字段校准索引，见类注释。
     *
     * <p>只有在「索引说的人和字段说的人不一致」时才动负重，所以稳态下
     * 这个注入点只是一次字段读取加一次比较，不产生任何额外开销。
     */
    @Inject(method = "attemptReattach", at = @At("TAIL"), remap = false)
    private void tfcicys$resyncPulling(CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        final Entity current = this.getPulling();
        final Entity indexed = CartPullRegistry.pullerOf(self);
        if (indexed == current) {
            // 两个都为 null，或指向同一只生物：索引和事实一致，不必打搅负重。
            return;
        }
        CartPullRegistry.detach(self);
        if (current != null) {
            CartPullRegistry.attach(current, self);
        }
        // 失衡的那一侧要立刻按新事实重算：旧拉车者卸下、新拉车者加上。
        if (indexed != null) {
            LoadEvents.refreshPuller(indexed);
        }
        if (current != null) {
            LoadEvents.refreshPuller(current);
        }
    }
}
