// =============================================================================
// vm02 · 老王最早期的翻译簿：「起始坐标 + 边界长度」模拟器
// =============================================================================
// 对应知识库：《🧠 虚拟内存》 Level 1.4「Why-What-How：起始坐标+边界长度」
//   - 复刻文档 Step-by-Step 推演：base=0x8000, limit=0x2000，三次请求的真实结果
//   - 追加验证 1.0 的隔离性结论：两位读者报同一个编号，翻译成不同真实格子
//   - 运行方式：g++ -O2 vm02_base_limit_translation.cpp -o vm02 && ./vm02
//   - 教学级参考实现（非生产可用）
// =============================================================================
#include <cstdio>
#include <cstdint>

// 老王的翻译簿（标准术语：Base Register + Limit Register）
struct LaoWangLedger {
    uint64_t base;   // 起始坐标
    uint64_t limit;  // 边界长度
};

// 翻译函数：对应 MMU 的 Base+Limit 翻译电路
// 返回 true = 翻译成功；false = 越界（Segmentation Fault 的名字由来）
static bool laowang_translate(const LaoWangLedger& ledger, uint64_t reader_offset, uint64_t& real_shelf) {
    if (reader_offset >= ledger.limit) return false;   // 越界 → 老王当场拒绝
    real_shelf = ledger.base + reader_offset;
    return true;
}

static int g_failures = 0;
static void expect_u64(const char* desc, uint64_t actual, uint64_t expected) {
    bool ok = (actual == expected);
    std::printf("    [%s] %s：实得 0x%lx，期望 0x%lx\n", ok ? "✅" : "❌", desc,
                (unsigned long)actual, (unsigned long)expected);
    if (!ok) ++g_failures;
}

int main() {
    std::printf("==================== vm02 · Base+Limit 翻译簿模拟器 ====================\n");
    std::printf("知识库 Level 1.4：真实格子 = 起始坐标 + 读者编号；编号 ≥ 边界长度 → 段错误\n\n");

    // 老王给读者甲划了从格子 0x8000 开始、共 0x2000 个格子的区域（文档原样参数）
    LaoWangLedger reader_a{0x8000, 0x2000};
    uint64_t real = 0;

    std::printf("  ── Step 复刻（文档 1.4 三步推演）──\n");
    bool ok1 = laowang_translate(reader_a, 0x0100, real);
    expect_u64("Step1: 编号 0x0100 → 真实格子", real, 0x8100);

    bool ok2 = laowang_translate(reader_a, 0x1FFF, real);
    expect_u64("Step2: 编号 0x1FFF → 真实格子", real, 0x9FFF);

    bool ok3 = laowang_translate(reader_a, 0x2500, real);
    std::printf("    [%s] Step3: 编号 0x2500 越界被拒绝（对应 Segmentation Fault 的由来）\n",
                !ok3 ? "✅" : "❌");
    if (ok3) ++g_failures;
    if (!ok1 || !ok2) ++g_failures;

    // ── 追加验证：同一编号，不同读者 → 不同真实位置（1.0「互不践踏」的根源）──
    std::printf("\n  ── 隔离性验证（文档 1.0：每个读者各拿一份自己的地图）──\n");
    LaoWangLedger reader_b{0x40000, 0x2000};     // 读者乙在完全不同的区域
    uint64_t real_a = 0, real_b = 0;
    laowang_translate(reader_a, 0x0100, real_a);
    laowang_translate(reader_b, 0x0100, real_b);
    std::printf("    读者甲报 0x0100 → 0x%lx；读者乙报 0x0100 → 0x%lx\n",
                (unsigned long)real_a, (unsigned long)real_b);
    expect_u64("同一编号 0x0100 被分别翻译到不同真实格子", real_b - real_a, 0x40000 - 0x8000);

    std::printf("\n  结论：%s\n", g_failures == 0
        ? "✅ PASS —— Base+Limit 翻译语义与越界拒绝行为复刻成功"
        : "❌ FAIL —— 模拟器行为与知识库推演不一致");
    return g_failures == 0 ? 0 : 1;
}
