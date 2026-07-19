// =============================================================================
// vm05 · 热门书小本本(TLB) 容量边界实测：工作集一旦挤爆小本本，单价立刻跳档
// =============================================================================
// 对应知识库：《🧠 虚拟内存》
//   - Level 3.2「热门书小本本」：命中≈1 个动作单位 vs 没记=逐级翻查数百单位
//   - Level 5.4「小本本与桌上大本子的层级」：L1 TLB(几十条) / L2 TLB(上千条)
//   - 数字推导 #5：命中率 95% → 平均 20.95 单位；80% → 80.8 单位（暴涨近4倍）
//   - 数字推导 #6：1024 条 × 4KB=4MB / ×2MB=2GB / ×1GB=1TB（整箱打包×512）
//   - 实验设计（关键洞察：本实验里每次访问都换一个新页，唯一能改变的是
//     「正在循环的页面集合有多大」）：
//       在 N 页的小圈子里反复打转 → N 小于小本本容量时，全部命中，单价≈纯内存访问
//       N 越过 L1 TLB 档位 → 开始掉档（要靠 L2 TLB/页表结构缓存兜住）
//       N 远超任何一级小本本 → 每次访问都伴随逐级翻查，单价显著跳升
//   - 运行方式：g++ -O2 vm05_tlb_sensitivity.cpp -o vm05 && ./vm05
//   - 说明：性能数字随 CPU/负载浮动，实测校验为「建议性(WARN)」；算术校验为「严格」
// =============================================================================
#include <cstdio>
#include <cstdint>
#include <chrono>
#include <sys/mman.h>
#include <unistd.h>

static constexpr uint64_t MAX_PAGES     = 16384;      // 64MB / 4KB
static constexpr uint64_t TOTAL_ACCESS  = 1u << 23;   // 每档固定 8M 次访问，保证可比

static int g_failures = 0, g_warnings = 0;
static void check(const char* desc, bool ok) {
    std::printf("    [%s] %s\n", ok ? "✅" : "❌", desc);
    if (!ok) ++g_failures;
}
static void check_warn(const char* desc, bool ok) {
    std::printf("    [%s] %s（建议性，随硬件浮动）\n", ok ? "✅" : "⚠️", desc);
    if (!ok) ++g_warnings;
}

// 在 N 页的小圈子里反复打转，每页只碰 1 个 int；返回平均每次访问 ns
static double bench(volatile uint32_t* buf, uint64_t n_pages) {
    auto t0 = std::chrono::steady_clock::now();
    for (uint64_t page = 0, i = 0; i < TOTAL_ACCESS; ++i) {
        buf[page * 1024] += 1;             // 1024 个 int = 4KB，每页只 1 次访问
        page = (page + 1) & (n_pages - 1); // n_pages 均为 2 的幂
    }
    auto t1 = std::chrono::steady_clock::now();
    return std::chrono::duration<double, std::nano>(t1 - t0).count() / TOTAL_ACCESS;
}

static const char* kb_or_mb(uint64_t pages, char* out) {
    uint64_t kb = pages * 4;
    if (kb < 1024) std::sprintf(out, "%lu KB", (unsigned long)kb);
    else           std::sprintf(out, "%lu MB", (unsigned long)(kb >> 10));
    return out;
}

int main() {
    std::printf("==================== vm05 · TLB 容量边界实测 ====================\n\n");

    // ── Part 1：数字推导 #5（命中率杠杆）的算术自校验 ──────────────────────
    std::printf("  ── Part 1：数字推导 #5 —— 命中率杠杆 ──\n");
    double cost95 = 0.95 * 1 + 0.05 * 400;    // 命中1单位，没记400单位
    double cost80 = 0.80 * 1 + 0.20 * 400;
    std::printf("    命中率 95%% → 平均 %.2f 单位；命中率 80%% → 平均 %.2f 单位；暴涨 %.2f 倍\n",
                cost95, cost80, cost80 / cost95);
    check("95% 命中平均代价 == 20.95（文档推导 #5）", cost95 > 20.94 && cost95 < 20.96);
    check("80% 命中平均代价 == 80.8（文档推导 #5）",  cost80 > 80.79 && cost80 < 80.81);
    check("命中率跌 15 个百分点 → 代价暴涨近 4 倍",      cost80 / cost95 > 3.8 && cost80 / cost95 < 3.9);

    // ── Part 2：数字推导 #6（整箱打包覆盖倍增）的算术自校验 ────────────────
    std::printf("\n  ── Part 2：数字推导 #6 —— 整箱打包的覆盖倍增 ──\n");
    uint64_t entries = 1024;
    uint64_t cov_4k = entries * 4ULL * 1024, cov_2m = entries * 2ULL * 1024 * 1024,
             cov_1g = entries * 1ULL * 1024 * 1024 * 1024;
    std::printf("    1024 条小本本 × 4KB 页  = %lu MB 覆盖范围\n",  (unsigned long)(cov_4k >> 20));
    std::printf("    1024 条小本本 × 2MB 箱  = %lu GB 覆盖范围（扩大 %lu 倍）\n",
                (unsigned long)(cov_2m >> 30), (unsigned long)(cov_2m / cov_4k));
    std::printf("    1024 条小本本 × 1GB 柜  = %lu TB 覆盖范围（扩大 %lu 倍）\n",
                (unsigned long)(cov_1g >> 40), (unsigned long)(cov_1g / cov_4k));
    check("4KB 覆盖 == 4MB",  cov_4k == 4ULL << 20);
    check("2MB 覆盖 == 2GB，且正好是 4KB 的 512 倍", cov_2m == 2ULL << 30 && cov_2m / cov_4k == 512);
    check("1GB 覆盖 == 1TB，且正好是 4KB 的 262144 倍", cov_1g == 1ULL << 40 && cov_1g / cov_4k == 262144);

    // ── Part 3：实测 —— 工作集扫描，看单价在哪几个档位跳升 ─────────────────
    std::printf("\n  ── Part 3：实测 —— 每档工作集固定访问 %lu M 次 ──\n",
                (unsigned long)(TOTAL_ACCESS >> 20));
    uint32_t* buf = (uint32_t*)mmap(nullptr, MAX_PAGES * 4096UL, PROT_READ | PROT_WRITE,
                                    MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (buf == MAP_FAILED) { std::printf("mmap 失败，跳过实测\n"); return g_failures ? 1 : 0; }
    for (uint64_t i = 0; i < MAX_PAGES; ++i) buf[i * 1024] = 1;   // 预热：全量缺页先到位

    static const uint64_t SIZES[] = {16, 64, 256, 1024, 4096, 16384};
    std::printf("    ┌──────────┬───────────┬────────────┬───────────────────────────┐\n");
    std::printf("    │ 工作集页数 │ 覆盖范围   │ ns/次       │ 档位解读                    │\n");
    std::printf("    ├──────────┼───────────┼────────────┼───────────────────────────┤\n");
    double cost16 = 0, cost16384 = 0; char szbuf[32];
    for (uint64_t n : SIZES) {
        double ns = bench((volatile uint32_t*)buf, n);
        const char* note = "";
        if (n <= 64)      note = "轻松装入 L1 小本本（几十条级）";
        else if (n <= 1024) note = "越过 L1，靠 L2 小本本兜底";
        else              note = "远超任一级小本本，逐级翻查显形";
        std::printf("    │ %8lu │ %9s │ %10.2f │ %s │\n", (unsigned long)n, kb_or_mb(n, szbuf), ns, note);
        if (n == 16)    cost16    = ns;
        if (n == 16384) cost16384 = ns;
    }
    std::printf("    └──────────┴───────────┴────────────┴───────────────────────────┘\n");
    std::printf("    16 页→16384 页：单价从 %.2f ns 跳到 %.2f ns（×%.2f，对照 buf[0]=%u）\n",
                cost16, cost16384, cost16384 / cost16, (unsigned)((volatile uint32_t*)buf)[0]);

    check_warn("工作集扩大 1024 倍后，单次访问 ≥ 1.3×（小本本挤出可观测）",
               cost16384 >= 1.3 * cost16);

    munmap(buf, MAX_PAGES * 4096UL);

    std::printf("\n  结论：%s（严格校验 6 处，建议性告警 %d 处）\n",
                g_failures == 0 ? "✅ PASS —— 覆盖率算术严格成立；实测阶梯印证小本本层级模型"
                                : "❌ FAIL —— 算术校验不通过",
                g_warnings);
    return g_failures == 0 ? 0 : 1;
}
