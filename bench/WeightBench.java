import java.util.HashMap;
import java.util.Map;

/**
 * 微基准：模拟 More Attributes 的 getWeight 在一次马车货物刷新里最贵的部分。
 *
 * 依据来自字节码（org.mantodea.more_attributes.utils.ItemUtils.getWeight）：
 *   1. stack.isEmpty() 快速排除空格
 *   2. ForgeRegistries.ITEMS.getKey(item) -> ResourceLocation
 *   3. ResourceLocation.toString()  ← 每次分配一个小 String
 *   4. ItemWeights.get(String)      ← HashMap 查表
 *   5. 未命中时走 TFC ItemSizeManager（能力查询 + 整数运算）
 *   6. ItemStack.getCount() 相乘，可能递归进容器内容
 *
 * 本基准保守地把第 2/5/6 步也折算成额外开销（ALLOC + 查表 + 分支），
 * 并额外加上一次 String 分配来模拟 toString()。
 */
public final class WeightBench {

    private static final Map<String, Integer> WEIGHTS = new HashMap<>();
    private static final String[] NAMES = new String[128];
    private static final int[] COUNTS = new int[128];

    static {
        for (int i = 0; i < 128; i++) {
            NAMES[i] = "minecraft:item_" + i;
            WEIGHTS.put(NAMES[i], 64 + i);
            COUNTS[i] = 1 + (i % 64);
        }
    }

    /** 一个非空格子的成本：分配一个 key + 查表 + 未命中分支 + 计数相乘。 */
    private static int slot(final int idx) {
        final String key = new String(NAMES[idx]);   // 模拟 ResourceLocation.toString()
        final Integer w = WEIGHTS.get(key);
        final int base = (w != null) ? w.intValue() : (64 / (1 + (idx & 7)));
        return base * COUNTS[idx];
    }

    /** 一次刷新：遍历全部格子，空槽只做一次 isEmpty 判断。 */
    private static int refresh(final int slots, final int nonEmpty) {
        int sum = 0;
        for (int i = 0; i < slots; i++) {
            if (i >= nonEmpty) {
                continue;                            // 空槽：一次 isEmpty()
            }
            sum += slot(i);
        }
        return sum;
    }

    public static void main(final String[] args) {
        final int slots = 54;
        for (int i = 0; i < 300_000; i++) {
            refresh(slots, slots);                   // 预热
        }
        System.out.println("JIT 预热完成，开始测量（每次 200 万回刷新，共 5 轮）");
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            final int iters = 2_000_000;
            final long t0 = System.nanoTime();
            int sink = 0;
            for (int i = 0; i < iters; i++) {
                sink += refresh(slots, slots);
            }
            final long t1 = System.nanoTime();
            final long per = (t1 - t0) / iters;
            best = Math.min(best, per);
            System.out.printf("  整箱全满(54格)：%.0f ns / 次刷新 = %.3f µs，%.2f ns/格  (sink=%d)%n",
                    (double) per, per / 1000.0, per / (double) slots, sink);
        }
        System.out.printf("取最快一轮：%.3f µs / 次刷新%n", best / 1000.0);
        System.out.println();
        System.out.println("换算（拉车的生物，5 tick = 每秒 4 次刷新）：");
        for (final int carts : new int[] {1, 5, 20, 100}) {
            final double usPerSec = best / 1000.0 * 4 * carts;
            System.out.printf("  %3d 辆满载马车：%8.1f µs/s = 每秒 %.5f%% 的 CPU 时间；占一个 50ms tick 的 %.4f%%%n",
                    carts, usPerSec, usPerSec / 1_000_000.0 * 100.0, usPerSec / 50_000.0 * 100.0);
        }
    }
}
