import java.util.HashMap;
import java.util.Map;

/**
 * 最坏情况：车厢里全是可递归翻找的容器（TFC 背包之类）。
 *
 * More Attributes 的 getWeight 在 isContainerScanEnabled() 为真、物品不在黑名单、
 * 且递归层数没超过 getMaxContainerDepth() 时，会把容器内容也累加进去
 * （getContentsWeight(stack, depth)）。所以一辆装满背包的马车比装满石头的
 * 马车贵一个数量级。
 *
 * 这里按「54 格 × 每格 9 个内层容器 × 每层 9 件」= 54 + 486 + 4374 次查表来测，
 * 已经超出真实配置能造出的负载，用来给结论留足安全余量。
 */
public final class WeightBenchDeep {

    private static final Map<String, Integer> WEIGHTS = new HashMap<>();
    private static final String[] NAMES = new String[4096];
    private static final int[] COUNTS = new int[4096];

    static {
        for (int i = 0; i < 4096; i++) {
            NAMES[i] = "minecraft:item_" + (i & 255);
            COUNTS[i] = 1 + (i & 7);
            WEIGHTS.put(NAMES[i], 64 + (i & 255));
        }
    }

    /** 单件物品的重量；depth > 0 且是容器时继续往下翻。 */
    private static int weight(final int idx, final int depth, final int fanout) {
        final String key = new String(NAMES[idx]);
        final Integer w = WEIGHTS.get(key);
        int base = (w != null) ? w.intValue() : (64 / (1 + (idx & 7)));
        base *= COUNTS[idx];
        if (depth > 0 && (idx & 3) == 0) {           // 1/4 的物品是容器
            int inner = 0;
            for (int i = 0; i < fanout; i++) {
                inner += weight((idx * 7 + i * 13) & 4095, depth - 1, fanout);
            }
            base += inner / 2;                       // 上游按比例折算
        }
        return base;
    }

    private static long refresh(final int slots, final int depth, final int fanout) {
        long sum = 0;
        for (int i = 0; i < slots; i++) {
            sum += weight(i, depth, fanout);
        }
        return sum;
    }

    public static void main(final String[] args) {
        for (int i = 0; i < 20_000; i++) {
            refresh(54, 2, 9);
        }
        System.out.println("JIT 预热完成");
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            final int iters = 20_000;
            final long t0 = System.nanoTime();
            long sink = 0;
            for (int i = 0; i < iters; i++) {
                sink += refresh(54, 2, 9);
            }
            final long t1 = System.nanoTime();
            final long per = (t1 - t0) / iters;
            best = Math.min(best, per);
            System.out.printf("  54 格、2 层嵌套、每层 9 件：%d ns / 次刷新 = %.2f µs  (sink=%d)%n",
                    per, per / 1000.0, sink);
        }
        System.out.printf("取最快一轮：%.2f µs / 次刷新%n%n", best / 1000.0);
        System.out.println("换算（5 tick = 每秒 4 次刷新，这是刻意夸大的最坏情况）：");
        for (final int carts : new int[] {1, 5, 20, 100}) {
            final double usPerSec = best / 1000.0 * 4 * carts;
            System.out.printf("  %3d 辆这种极端马车：%9.1f µs/s = 一个 50ms tick 的 %.3f%%%n",
                    carts, usPerSec, usPerSec / 50_000.0 * 100.0);
        }
    }
}
