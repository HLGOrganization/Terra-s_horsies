package com.tfcicys.horses.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tfcicys.horses.taming.TfcEquineTaming;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;

/**
 * TFC 马科的骑乘驯服：把成功率换成亲密度曲线，并让「驯服成功」在 TFC 眼里真的成立。
 *
 * <p>这是一个混入<b>原版</b> {@code AbstractHorse} 的 mixin，但两个注入都以
 * {@link TfcEquineTaming#isTfcEquine} 收口，所以只有 TFC 的马／驴／骡会走到里面的逻辑，
 * 原版马、羊驼、Icy 的马一律原样返回。
 *
 * <h2>为什么用注入而不是覆写</h2>
 *
 * <p>更直观的写法是混入 {@code TFCHorse}／{@code TFCChestedHorse} 然后 {@code @Override getTemper()}，
 * 但那要求混入类声明目标的直接父类（才调得到 {@code super.getTemper()}），
 * 而且覆写是否被改名取决于 Mixin 对混入类继承链的推断 —— 一旦推断不出来，
 * 方法会带着 {@code getTemper} 这个名字留在类里，而目标类的同名方法叫 {@code m_30624_}，
 * 覆写<b>静默失效</b>：编译通过、构建通过、进游戏毫无效果。
 *
 * <p>注入形式没有这个隐患：{@code method = "getTemper"} 是一个注入点选择器，
 * 构建期一定会被写进 refmap（已核对 jar 内的 refmap 含
 * {@code AbstractHorse;m_30624_()I}），运行期由 Mixin 改写原方法本身。
 * 顺带还省掉了三个 mixin 类和一个 {@code super} 调用。
 *
 * <p>两个注入挂在 {@code AbstractHorse} 上之所以能覆盖 TFC 的马，是因为
 * TFC 的这两个类<b>都没有覆写</b>这两个方法（已用 {@code javap} 核对字节码）：
 * 它们改的是 {@code isTamed()}，而不是 {@code getTemper()} 或 {@code tameWithName()}。
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseTamingMixin {

    /**
     * 第 1 环：把原版的 temper（= 成功率百分比）换成 TFC 亲密度曲线。
     *
     * <p>挂在 {@code RETURN} 上，所以拿到的 {@code cir.getReturnValue()} 就是原版算好的数值，
     * 不需要 {@code super} 调用、也不需要知道原版把 temper 存在哪里。
     *
     * <p>只有三处会读到 {@code getTemper()}：驯服掷骰本身、{@code handleEating} 里
     * 「temper 是否已满」的判断、以及 NBT 存档。前两者换成曲线都符合预期；
     * 存档里那个值会被 TFC 自己的亲密度覆盖掉，不影响。
     */
    @Inject(method = "getTemper", at = @At("RETURN"), cancellable = true)
    private void tfcicys$temperFromFamiliarity(final CallbackInfoReturnable<Integer> cir) {
        final AbstractHorse self = (AbstractHorse) (Object) this;
        if (!TfcEquineTaming.isTfcEquine(self)) {
            return;
        }
        cir.setReturnValue(TfcEquineTaming.temperOf(self, cir.getReturnValue()));
    }

    /**
     * 第 2 环：原版驯服成功的那一刻，把 TFC 认账的「驯服线」补上。
     *
     * <p>挂在 {@code tameWithName} 而不是 {@code setTamed}，是因为 {@code setTamed}
     * 在读档时也会被调用 —— 挂在那边会让「读档」把亲密度平白推上去；
     * 而 {@code tameWithName} 只在真正驯服成功时走。
     *
     * <p>只加不减：已经喂得更熟的马不会被降回来。
     */
    @Inject(method = "tameWithName", at = @At("TAIL"))
    private void tfcicys$promoteTfcFamiliarity(final Player player, final CallbackInfo ci) {
        TfcEquineTaming.promoteFamiliarity((AbstractHorse) (Object) this);
    }
}
