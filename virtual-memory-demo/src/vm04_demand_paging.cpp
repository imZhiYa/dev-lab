// =============================================================================
// vm04 · 按需调页（Demand Paging）：「先答应你借，真翻了才去调」实证
// =============================================================================
// ★ 本实验回答的问题：malloc/mmap 成功的 128MB，操作系统真的立刻给了 128MB 物理内存吗？
//   ——答案：没有。编号空间(vsize)立即预留，真实书架(rss)一页没给；真翻哪页才给哪页。
//
// 对应知识库：《🧠 虚拟内存》
//   - Level 2.4「缺页处理与边读边取的完整生命周期」阶段1/阶段2
//   - 数字推导 #2：空白页缺页 ≈ 微秒级，比机械盘调取便宜四个数量级
//
// 验证手法（三个闸门全程记录，观测层见 include/vm_probe.h）：
//   ① 分配后：vsize 立刻 +128MB（花名册登记），rss 几乎不动（真实消耗为零）
//   ② 逐页触碰后：rss ≈ +128MB，缺页计数增量 ≈ 页数（每页一次缺页中断）
//   ③ free 后：rss 语义回落 —— 平台分裂：
//      Linux/glibc ≥128KB 走 munmap 立即腾退；
//      macOS 走 MADV_FREE 惰性回收（贴"可回收"标签，页面仍驻留，内核吃紧时才清场）
// 铁规落点：①②的语义断言 = 严格；缺页时延倍率 = 计时噪声级，仅 ⚠️ 建议告警
//
// 运行方式：c++ -O2 -std=c++20 -Iinclude src/vm04_demand_paging.cpp -o vm04 && ./vm04
// 平台支持：Linux（/proc）与 macOS（libproc）双通道，其它平台编译期拦截
// =============================================================================
#include "vm_probe.h"      // 跨平台观测层：Linux /proc 与 macOS libproc 收敛成同一套 API

#include <sys/mman.h>       // madvise（Linux 分支用于 MADV_NOHUGEPAGE 校正器）

#include <cstdio>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <chrono>

using vm_probe::MemSnap;
using vm_probe::read_mem;
using vm_probe::page_size;

static const size_t SZ = 128UL * 1024 * 1024;   // 借阅规模：128MB

#if defined(__linux__)
static const char* kProbe = "Linux /proc（VmSize/VmRSS + minflt/majflt）";
#elif defined(__APPLE__)
static const char* kProbe = "macOS libproc（proc_taskinfo: vsize/rss/faults/pageins）";
#endif

static int g_failures = 0;
static int g_warnings = 0;
// 严格断言：语义/算术事实，挂了必须红（CI 亮红灯）
static void check(const char* desc, bool ok) {
    std::printf("    [%s] %s\n", ok ? "✅" : "❌", desc);
    if (!ok) ++g_failures;
}
// 建议告警：计时/平台行为类观测，只亮黄灯，不掀桌子
static void advise(const char* desc, bool ok) {
    std::printf("    [%s] %s\n", ok ? "✅" : "⚠️ WARN", desc);
    if (!ok) ++g_warnings;
}
static void print_snap(const char* tag, const MemSnap& s) {
    std::printf("    %-10s vsize=%9.1f MB   rss=%8.2f MB   minflt=%-8llu majflt=%llu\n",
                tag, s.vsize / 1048576.0, s.rss / 1048576.0,
                (unsigned long long)s.minflt, (unsigned long long)s.majflt);
}

int main() {
    std::printf("==================== vm04 · 按需调页（边读边取）实证 ====================\n");
    const long g_pagesize = page_size();
    std::printf("观测通道：%s；本机页大小 = %ld 字节\n", kProbe, g_pagesize);
    std::printf("知识库 Level 2.4：申请一大套百科全书时老王一本都没真调——真翻哪页才调哪页\n\n");

    MemSnap s0{};
    if (!read_mem(s0)) { std::printf("观测通道不可用，跳过。\n"); return 0; }
    print_snap("分配前", s0);

    // ── 阶段1：填写借阅申请（malloc = 花名册登记，不真调书）──────────────────
    char* buf = (char*)std::malloc(SZ);
    if (!buf) { std::printf("malloc 失败\n"); return 1; }
    MemSnap s1; read_mem(s1);
    print_snap("malloc 后", s1);

#if defined(__linux__)
    // ── THP 校正器（云主机/CI 实录）：透明大页(THP)会把 128MB 匿名区接管成 64 张 2MB 大页，
    //    缺页计数从理论 32768 塌缩成 ~64（Actions 真凶实录：71 = 64 大页 + 7 杂项，rss 照涨不误）。
    //    本实验的口径是「每页一次缺页」→ 明确声明这段地址不走 THP，还原 4KB 粒度。
    {
        const uintptr_t pg   = (uintptr_t)g_pagesize;
        const uintptr_t from = ((uintptr_t)buf) & ~(pg - 1);
        const size_t    alen = (size_t)((((uintptr_t)buf + SZ) - from + pg - 1) & ~(pg - 1));
        if (madvise((void*)from, alen, MADV_NOHUGEPAGE) == 0)
            std::printf("    ★ Linux 注脚：已声明 MADV_NOHUGEPAGE（防御云端 THP 吞掉缺页计数口径）\n");
    }
#endif

    // ── 阶段2：读者第一次真去翻每一页（每 4KB 写 1 字节 → 每页一次缺页中断）──
    auto t0 = std::chrono::steady_clock::now();
    const uint64_t touches = SZ / 4096;                       // 固定 4KB 步长，保证 16KB 页也被碰到
    for (uint64_t i = 0; i < touches; ++i) buf[i * 4096] = 1;
    auto t1 = std::chrono::steady_clock::now();
    MemSnap s2; read_mem(s2);
    print_snap("逐页触碰后", s2);
    double first_touch_ns = std::chrono::duration<double, std::nano>(t1 - t0).count() / touches;

    // ── 阶段3：再翻一遍（页面已在馆，零缺页）────────────────────────────────
    auto t2 = std::chrono::steady_clock::now();
    uint64_t sum = 0;
    for (uint64_t i = 0; i < touches; ++i) sum += buf[i * 4096];
    auto t3 = std::chrono::steady_clock::now();
    double retouch_ns = std::chrono::duration<double, std::nano>(t3 - t2).count() / touches;

    // ── 阶段4：还书（free → 真实格子归还公共空闲池）─────────────────────────
    std::free(buf);
    MemSnap s3; read_mem(s3);
    print_snap("free 后", s3);

    double vsize_gain_mb = (s1.vsize - s0.vsize) / 1048576.0;
    double rss_gain_before_touch_mb = (s1.rss - s0.rss) / 1048576.0;
    double rss_gain_after_touch_mb  = (s2.rss - s0.rss) / 1048576.0;
    long   minflt_delta = (long)s2.minflt - (long)s0.minflt;
    long   expect_faults = (long)(SZ / g_pagesize);                // 理论缺页次数 = 字节数/页大小
    double rss_after_free_mb = (s3.rss - s0.rss) / 1048576.0;

    std::printf("\n  ── 关键观测（对照文档 2.4 两阶段）──\n");
    std::printf("    malloc 让 vsize 立刻 +%.1f MB，而 rss 只 +%.2f MB  → 「一本都没真调」真实消耗≈0\n",
                vsize_gain_mb, rss_gain_before_touch_mb);
    std::printf("    逐页写入后 rss +%.1f MB，缺页计数增加 %ld 次（理论值=%ld，按本机页大小 %ldB 折算）\n",
                rss_gain_after_touch_mb, minflt_delta, expect_faults, g_pagesize);
    std::printf("    首次触碰 %.0f ns/次 vs 再次读取 %.1f ns/次 = %.1f×（文档推导 #2：空白页缺页≈微秒级；macOS/苹果芯内核零填充快路径会把这个差压到亚微秒）\n",
                first_touch_ns, retouch_ns, first_touch_ns / retouch_ns);
    std::printf("    free 后 rss 增量回落到 %.2f MB（sum=%lu 防编译器优化）\n",
                rss_after_free_mb, (unsigned long)sum);
#if defined(__APPLE__)
    std::printf("    ★ macOS 注脚：libmalloc 走 MADV_FREE 惰性回收——格子贴了「可回收」标签但仍驻留馆里，\n"
                "      内核内存吃紧时才清场；所以 rss 不立即回落 ≠ 格子没还（Linux glibc 是 munmap 秒退）\n");
#endif

    std::printf("\n  ── 自校验 ──\n");
    check("阶段1：vsize 增量 ≥ 110MB（编号空间立即预留）", vsize_gain_mb >= 110.0);
    check("阶段1：rss 增量 ≤ 10MB（真实消耗为零：超额承诺的物理证据）", rss_gain_before_touch_mb <= 10.0);
    check("阶段2：rss 增量 ∈ [110, 150] MB（真翻才真调，按需分配）",
          rss_gain_after_touch_mb >= 110.0 && rss_gain_after_touch_mb <= 150.0);
    check("阶段2：缺页增量 ∈ [理论值×0.5, 理论值×1.5]（每页触发一次缺页）",
          minflt_delta >= expect_faults / 2 && minflt_delta <= expect_faults * 3 / 2 + 512);
    check("阶段2：majflt 几乎为零（匿名页无需跑地下档案库）",
          (long)s2.majflt - (long)s0.majflt <= 64);
    // 时延倍率 = 计时噪声级指标：Linux 沙箱实测 ~130×，Apple Silicon 实测 ~2-3×
    // （内核零填充快路径 + DRAM 直读 ~160ns）。语义证据是缺页计数（上面已严格校验），此处只立 ⚠️
    advise("阶段2：首次触碰慢于再次访问（缺页中断确实更贵，倍率随平台大幅漂移）",
           first_touch_ns >= 1.5 * retouch_ns);
#if defined(__linux__)
    // glibc：≥ MMAP_THRESHOLD(默认128KB) 的块 free 即 munmap，rss 应当场回落 —— 严格
    check("阶段4：free 后 rss 回落（增量 ≤ 28MB，格子归还公共空闲池）",
          rss_after_free_mb <= 28.0);
#elif defined(__APPLE__)
    // libmalloc：MADV_FREE 惰性回收，rss 不立即回落是天性 —— 建议项
    advise("阶段4：free 后 rss 回落（macOS 惰性回收不强求，见上方 ★ 注脚）",
           rss_after_free_mb <= 28.0);
#endif

    std::printf("\n  严格校验 %d 处 FAIL，建议告警 %d 处。\n", g_failures, g_warnings);
    std::printf("\n  结论：%s\n", g_failures == 0
        ? "✅ PASS —— 「先答应借、真翻才调」的按需调页机制被三个闸门数据完整实证"
        : "❌ FAIL —— 观测数据不符合按需调页模型");
    return g_failures == 0 ? 0 : 1;
}
