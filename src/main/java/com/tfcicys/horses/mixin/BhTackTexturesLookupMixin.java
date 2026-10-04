package com.tfcicys.horses.mixin;

import java.util.Map;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

/**
 * 让 Icy 能找到我们放进 jar 的 TFC 马铠贴图。
 *
 * <h2>问题</h2>
 *
 * <p>Icy 的 {@code lookup} 第一分支用
 * {@code Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()}
 * 判断专属贴图是否存在，存在就采用。我们按它拼路径的规则，把 72 张贴图放进
 * {@code assets/icys-better-horses/textures/entity/horse/<分组>/armor/tfc/metal/horse_armor/<金属>.png}，
 * 路径与它构造的 {@code candidate} 逐字符一致。
 *
 * <p>但实测运行时出现了一个自相矛盾的现象：同一个 {@code ResourceLocation}，
 * 同一毫秒内
 *
 * <pre>
 *   ResourceManager.listResources(...) → 枚举到它，Resource 对象非空
 *   ResourceManager.getResource(it)    → Optional.empty()
 * </pre>
 *
 * <p>两个 API 查的是同一份数据，却给出相反答案。这种不一致不是「文件不存在」，
 * 而是查询路径本身出了问题——用户环境启用了
 * {@code ModernFix} 的 {@code mixin.perf.dynamic_resources}（惰性资源加载），
 * 该功能会改写资源查询的时机与路径。Icy 的 {@code armor_iron} 等原生贴图
 * 走的是另一条更早的查询路径，所以没被影响，唯独我们的新资源命中不了。
 *
 * <h2>对策</h2>
 *
 * <p>不跟 {@code getResource} 纠缠，改用能可靠枚举的 {@code listResources}
 * 来判定。注入 {@code lookup} 的 HEAD：<b>只在能找到我们的专属贴图时</b>
 * 直接返回，其余情况一律不干预，让 Icy 原本的回退链
 * （皮革／铁／金／钻／generic）照常工作。
 *
 * <p>开销可以忽略：Icy 自己有 {@code armors} 缓存，每种马铠只会查一次。
 */
@Mixin(targets = "icy.betterhorses.net.client.render.BhTackTextures", remap = false)
public class BhTackTexturesLookupMixin {

    private static final Logger TFCICYS_LOGGER = LogUtils.getLogger();

    /** Icy 的品种前缀，形如 {@code textures/entity/horse/belgian/}。 */
    @Shadow(remap = false)
    private String base;

    @Inject(method = "lookup", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfcicys$lookupOwnTexture(Item item,
                                          CallbackInfoReturnable<ResourceLocation> cir) {
        final ResourceLocation hit = tfcicys$findOwnTexture(item);
        if (hit != null) {
            // 只有确实找到才短路；找不到就什么都不做，交还 Icy 的回退逻辑。
            cir.setReturnValue(hit);
        }
    }

    /**
     * 按 Icy 的命名规则拼出专属贴图路径，再用 {@code listResources} 判断是否存在。
     *
     * @return 命中的贴图位置；没有则 null
     */
    private ResourceLocation tfcicys$findOwnTexture(Item item) {
        try {
            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            // 只管 TFC 的物品（马铠的 registry name 形如 tfc:metal/horse_armor/copper）。
            // 原版马铠交给 Icy 自己的回退分支处理。
            if (!"tfc".equals(id.getNamespace())) {
                return null;
            }

            final String path = base + "armor/" + id.getNamespace() + "/" + id.getPath() + ".png";
            final ResourceLocation candidate = new ResourceLocation("icys-better-horses", path);

            final ResourceManager rm = Minecraft.getInstance().getResourceManager();

            // 先用 getResource 试（正常环境下这就够了，而且最快）。
            if (rm.getResource(candidate).isPresent()) {
                return candidate;
            }

            // getResource 不可靠时退到 listResources —— 它在本环境里是被证明可用的那个。
            final Map<ResourceLocation, Resource> all =
                    rm.listResources("textures/entity/horse", rl -> true);
            return all.containsKey(candidate) ? candidate : null;
        } catch (final Throwable t) {
            TFCICYS_LOGGER.warn("[terras_horsies] 查找专属马铠贴图失败，回退到 Icy 默认外观", t);
            return null;
        }
    }
}
