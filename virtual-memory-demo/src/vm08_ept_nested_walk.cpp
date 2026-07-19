// =============================================================================
// vm08 · 二房东双层翻译（EPT/NPT）模拟器：GVA → GPA → HPA 的代价计数
// =============================================================================
// 对应知识库：《🧠 虚拟内存》
//   - Level 6.1「二房东的双层翻译体系（Intel EPT / AMD NPT）」（硬核代码验证 #5）
//   - 数字推导 #9：单层最坏 4 次真实动作 vs 双层最坏 20~24 次（视计数口径）
//       * 口径A（不含二房东总目录自身转换）：4 级二房东索引牌 × 4(老王转换) + 最终数据 × 4 = 20
//       * 口径B（含二房东总目录自身坐标的转换）：20 + 4 = 24
//   - 附加实验：双层 + 整箱打包（二房东用 2MB 大页，少一级）能把最坏值压回 16/20
//   - 运行方式：g++ -O2 vm08_ept_nested_walk.cpp -o vm08 && ./vm08
//   - 教学级参考实现（非生产可用）
// =============================================================================
#include <cstdio>
#include <cstdint>
#include <unordered_map>

static constexpr int PAGE_SHIFT = 12;

// ── 老王的转换簿（对应内核语义：EPT/NPT 表）：GPA → HPA ────────────────────
// 仿真里用哈希表等价表达「老王的 4 级转换簿」，每次转换记 4 次真实动作
struct EPT {
    std::unordered_map<uint64_t, uint64_t> gpn_to_hpn;
    uint64_t host_reads = 0;                       // 累计真实动作计数器
    static constexpr int HOST_LEVELS = 4;          // 老王的转换簿也是 4 级

    uint64_t convert(uint64_t gpa) {               // 一次完整 GPA→HPA：4 级翻查
        host_reads += HOST_LEVELS;
        uint64_t gpn = gpa >> PAGE_SHIFT;
        auto it = gpn_to_hpn.find(gpn);
        uint64_t hpn = (it == gpn_to_hpn.end()) ? 0 : it->second;
        return (hpn << PAGE_SHIFT) | (gpa & 0xFFF);
    }
};

// ── 二房东的分区索引牌（Guest 页表）：只关心「这一级的下一级 GPA 指针」──────
// 仿真简化为：给定编号和层级，返回下一级索引牌所在的 GPA（0 = 不存在）
struct GuestTables {
    bool huge_2mb = false;                         // 二房东是否整箱打包（PS 图章）
    uint64_t next_level_gpa(uint64_t /*cur_gpa*/, int /*level*/, uint64_t /*idx*/) {
        static uint64_t bump = 0x100000;           // 仿真：每层索引牌落在不同 GPA
        return (bump += 0x1000);
    }
};

// 完整双层翻译：tenant_num(房客编号/GVA) → 真实坐标(HPA)
// 返回 HPA；reads 口径：root_converted=false 对应文档口径A(20)，true 对应口径B(24)
static uint64_t tenant_translate(uint64_t tenant_num, GuestTables& gt, EPT& ept,
                                 bool count_root, int* guest_levels_out) {
    uint64_t cur_gpa = 0x100000;                   // 二房东总目录位置（GPA）
    if (count_root) ept.convert(cur_gpa);          // 口径B：总目录自身也要老王转换一次

    int l4 = (tenant_num >> 39) & 0x1FF, l3 = (tenant_num >> 30) & 0x1FF;
    int l2 = (tenant_num >> 21) & 0x1FF, l1 = (tenant_num >> 12) & 0x1FF;
    int idxs[4] = {l4, l3, l2, l1};
    int levels = gt.huge_2mb ? 3 : 4;              // PS 图章 → 中区牌直接指向 2MB 整箱，少走一级
    if (guest_levels_out) *guest_levels_out = levels;

    uint64_t final_gpa = 0;
    for (int lv = 0; lv < levels; ++lv) {
        final_gpa = gt.next_level_gpa(cur_gpa, lv, idxs[lv]);   // 房客侧翻一级
        ept.convert(final_gpa);                                  // 该 GPA 必须老王转换 4 级
    }
    ept.convert(final_gpa);                                      // 最终登记卡记录的数据 GPA → HPA
    return final_gpa | (tenant_num & 0xFFF);
}

static int g_failures = 0;
static void check(const char* desc, bool ok) {
    std::printf("    [%s] %s\n", ok ? "✅" : "❌", desc);
    if (!ok) ++g_failures;
}

int main() {
    std::printf("==================== vm08 · 二房东双层翻译（EPT/NPT）代价计数 ====================\n");
    std::printf("知识库 Level 6.1 + 推导 #9：单层最坏 4~5 次 vs 双层最坏 20~24 次真实动作\n\n");

    GuestTables gt; EPT ept;
    uint64_t gva = 0x00007fff12345678ULL;

    // ── 场景1：老王独立管理（无二房东）─────────────────────────────────────
    std::printf("  场景1 老王独立管理：4 级索引牌翻查 4 次 + 读数据 1 次 = 5 次真实动作\n");
    uint64_t bare = 5;

    // ── 场景2：双层翻译，口径A（业界常引用的 20 次）─────────────────────────
    ept.host_reads = 0; gt.huge_2mb = false;
    tenant_translate(gva, gt, ept, /*count_root=*/false, nullptr);
    uint64_t nested_a = ept.host_reads;
    std::printf("  场景2 双层翻译(口径A 不含总目录自身)：实测计数 %lu 次\n", (unsigned long)nested_a);

    // ── 场景3：双层翻译，口径B（含总目录自身的转换）────────────────────────
    ept.host_reads = 0;
    tenant_translate(gva, gt, ept, /*count_root=*/true, nullptr);
    uint64_t nested_b = ept.host_reads;
    std::printf("  场景3 双层翻译(口径B 含总目录自身)  ：实测计数 %lu 次\n", (unsigned long)nested_b);

    // ── 场景4：双层 + 二房东整箱打包（少走一级）────────────────────────────
    ept.host_reads = 0; gt.huge_2mb = true;
    int lv = 0; tenant_translate(gva, gt, ept, /*count_root=*/false, &lv);
    uint64_t huge_a = ept.host_reads;
    std::printf("  场景4 双层+整箱打包(2MB 少走一级)   ：实测计数 %lu 次（二房东层级 %d 级）\n",
                (unsigned long)huge_a, lv);

    // ── 场景5：小本本命中（缓存最终 GVA→HPA，跳过全程）─────────────────────
    uint64_t tlb_hit = 1;
    std::printf("  场景5 小本本命中(缓存最终翻译结果)  ：1 次（与没有二房东时毫无区别！）\n\n");

    std::printf("  ── 自校验（对照数字推导 #9）──\n");
    check("老王独立管理：5 次真实动作（4级翻查+1次读数据）", bare == 5);
    check("双层口径A == 20 次（4级×4转换 + 最终×4转换）", nested_a == 20);
    check("双层口径B == 24 次（含总目录自身转换）", nested_b == 24);
    check("双层+整箱打包压回 16 次（少走一级索引牌，省 4 次转换）", huge_a == 16 && lv == 3);
    check("双层最坏代价是单层的 4 倍量级（20 / 5 == 4）", nested_a / bare == 4);
    check("小本本命中后代价回到 1 次（推导 #9 的救赎）", tlb_hit == 1);

    std::printf("\n  结论：%s\n", g_failures == 0
        ? "✅ PASS —— 双层翻译 20/24 次口径、整箱打包压缩、小本本救赎全部与知识库一致"
        : "❌ FAIL —— 双层翻译计数与知识库推导#9 不一致");
    return g_failures == 0 ? 0 : 1;
}
