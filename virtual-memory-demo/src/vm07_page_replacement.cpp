// =============================================================================
// vm07 · 清仓算法对决：FIFO / CLOCK / LRU 缺页率实测 + Bélády 异常实证
// =============================================================================
// 对应知识库：《🧠 虚拟内存》
//   - Level 4.1「老王的清仓算法演进」（硬核代码验证 #4：时钟算法教学实现）
//   - 核心实验：经典引用串 1,2,3,4,1,2,5,1,2,3,4,5
//       * FIFO：3 格缺 9 次，4 格缺 10 次 —— 格子变多缺页反增（Bélády 异常）！
//       * LRU ：3 格缺 10 次，4 格缺 8 次 —— 栈算法，格子越多绝不越差
//       * CLOCK：近似 LRU 的工程方案（访问章 + 转圈指针），附完整推演日志
//   - 运行方式：g++ -O2 vm07_page_replacement.cpp -o vm07 && ./vm07
//   - 教学级参考实现（非生产可用）
// =============================================================================
#include <cstdio>
#include <vector>
#include <deque>
#include <algorithm>

static const std::vector<int> REF = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};

// ── 先进先出（最早搬进来的先搬走）──────────────────────────────────────────
static int fifo_faults(int frames) {
    std::vector<int> f; std::deque<int> q; int miss = 0;
    for (int p : REF) {
        if (std::find(f.begin(), f.end(), p) != f.end()) continue;   // 命中
        ++miss;
        if ((int)f.size() < frames) { f.push_back(p); q.push_back(p); }
        else { int victim = q.front(); q.pop_front();
               *std::find(f.begin(), f.end(), victim) = p; q.push_back(p); }
    }
    return miss;
}

// ── 最久未用（挪走最久没人翻的）：理论效果接近理想算法 ──────────────────────
static int lru_faults(int frames) {
    std::vector<int> f; std::vector<int> last; int miss = 0;
    for (int t = 0; t < (int)REF.size(); ++t) {
        int p = REF[t];
        auto it = std::find(f.begin(), f.end(), p);
        if (it != f.end()) { last[it - f.begin()] = t; continue; }   // 命中刷新时间戳
        ++miss;
        if ((int)f.size() < frames) { f.push_back(p); last.push_back(t); }
        else { int v = (int)(std::min_element(last.begin(), last.end()) - last.begin());
               f[v] = p; last[v] = t; }
    }
    return miss;
}

// ── 时钟算法（近似 LRU，硬核代码验证 #4 的工程版，带推演日志）──────────────
struct Clock {
    struct Slot { int book = -1; bool accessed = false; };
    std::vector<Slot> slots; int hand = 0; bool verbose;
    explicit Clock(int frames, bool v = false) : slots(frames), verbose(v) {}
    int faults() {
        int miss = 0;
        for (int p : REF) {
            auto it = std::find_if(slots.begin(), slots.end(),
                                   [&](const Slot& s) { return s.book == p; });
            if (it != slots.end()) {                                  // 命中 → 盖上访问章
                it->accessed = true;
                if (verbose) std::printf("      翻书 %d：命中，盖访问章\n", p);
                continue;
            }
            ++miss;
            auto empty = std::find_if(slots.begin(), slots.end(),
                                      [](const Slot& s) { return s.book == -1; });
            if (empty != slots.end()) {                               // 有空格 → 直接上架
                empty->book = p; empty->accessed = true;
                if (verbose) std::printf("      翻书 %d：缺页，有空格直接上架\n", p);
                continue;
            }
            while (true) {                                            // 转圈找清仓对象
                Slot& s = slots[hand];
                if (!s.accessed) {                                    // 没盖访问章 → 挪走它
                    if (verbose) std::printf("      翻书 %d：缺页，挪走格子 %d 里的旧书 %d\n", p, hand, s.book);
                    s.book = p; s.accessed = true;
                    hand = (hand + 1) % (int)slots.size(); break;
                }
                s.accessed = false;                                   // 盖了章 → 擦掉，给一次复活机会
                if (verbose) std::printf("      …格子 %d 的书盖过访问章，擦掉放行（复活一次）\n", hand);
                hand = (hand + 1) % (int)slots.size();
            }
        }
        return miss;
    }
};

static int g_failures = 0;
static void check(const char* desc, bool ok) {
    std::printf("    [%s] %s\n", ok ? "✅" : "❌", desc);
    if (!ok) ++g_failures;
}

int main() {
    std::printf("==================== vm07 · 清仓算法对决 + Bélády 异常实证 ====================\n");
    std::printf("引用串（读者翻书顺序）：1,2,3,4,1,2,5,1,2,3,4,5（共 %zu 次翻阅）\n\n", REF.size());

    int fifo3 = fifo_faults(3), fifo4 = fifo_faults(4);
    int lru3  = lru_faults(3),  lru4  = lru_faults(4);
    int clk3  = Clock(3).faults(), clk4 = Clock(4).faults();

    std::printf("  ┌──────────────┬───────────┬───────────┬──────────────────┐\n");
    std::printf("  │ 清仓算法      │ 3 格缺页  │ 4 格缺页  │ 格子变多的表现    │\n");
    std::printf("  ├──────────────┼───────────┼───────────┼──────────────────┤\n");
    std::printf("  │ FIFO 先进先出 │ %2d 次     │ %2d 次     │ ❗ 缺页反增(异常) │\n", fifo3, fifo4);
    std::printf("  │ LRU 最久未用  │ %2d 次     │ %2d 次     │ ✅ 严格改善      │\n", lru3, lru4);
    std::printf("  │ CLOCK 时钟    │ %2d 次     │ %2d 次     │ ⚠️ 近似 LRU      │\n", clk3, clk4);
    std::printf("  └──────────────┴───────────┴───────────┴──────────────────┘\n\n");

    std::printf("  ── CLOCK(3 格) 完整推演日志（对照硬核代码验证 #4 的转圈逻辑）──\n");
    Clock demo(3, true); int replay = demo.faults();
    std::printf("      推演复跑缺页 %d 次（与上表一致：%s）\n\n", replay, replay == clk3 ? "✅" : "❌");

    std::printf("  ── 自校验（教科书结论逐一钉死）──\n");
    check("FIFO(3格) == 9 次缺页",  fifo3 == 9);
    check("FIFO(4格) == 10 次缺页（Bélády 异常：多一格反而多缺一次）", fifo4 == 10);
    check("FIFO 出现 Bélády 异常（fifo4 > fifo3）", fifo4 > fifo3);
    check("LRU(3格) == 10 次缺页", lru3 == 10);
    check("LRU(4格) == 8 次缺页",  lru4 == 8);
    check("LRU 是栈算法：格子变多缺页绝不反增（lru4 < lru3）", lru4 < lru3);
    check("CLOCK(3格) 表现不差于 FIFO(3格)（访问章带来近似 LRU 的收益）", clk3 <= fifo3);
    check("CLOCK 推演复跑与批量结果一致", replay == clk3);

    std::printf("\n  结论：%s\n", g_failures == 0
        ? "✅ PASS —— FIFO 的 Bélády 异常与 LRU 的栈性质被精确复现，CLOCK 近似收益可复跑"
        : "❌ FAIL —— 置换算法行为与教科书结论不符");
    return g_failures == 0 ? 0 : 1;
}
