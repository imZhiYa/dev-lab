// =============================================================================
// vm01 · 进程地址空间四大件：Text / Data / BSS / Heap / Stack 真实布局验证
// =============================================================================
// 对应知识库：《🧠 虚拟内存》 Level 1.1「老王管理的一层四个区」
//   - 验证目标：亲眼看到地址从低到高严格按 Text < Data ≈ BSS < Heap << Stack 排列
//   - 运行方式：g++ -O2 vm01_address_space_layout.cpp -o vm01 && ./vm01
//   - 教学级参考实现（非生产可用）：仅用于还原原理，未做生产级加固
// 备注：Linux x86-64 + PIE 下该排序稳定成立；ASLR 只改变基址，不改变区间相对顺序
// =============================================================================
#include <cstdio>
#include <cstdlib>
#include <cstdint>

// ── 老王的两个登记区 ─────────────────────────────────────────────────────────
static int filled_registry = 42;    // 已填登记区（标准术语：Data 段，已初始化全局变量）
static int reserved_registry;       // 预留登记区（标准术语：BSS 段，未初始化全局变量）

// 函数自身的代码地址落在固定馆藏区（标准术语：Text 段）
static uintptr_t text_probe() { return reinterpret_cast<uintptr_t>(&text_probe); }

static int g_failures = 0;
static void check(const char* desc, bool ok) {
    std::printf("    [%s] %s\n", ok ? "✅" : "❌", desc);
    if (!ok) ++g_failures;
}

int main() {
    std::printf("==================== vm01 · 进程地址空间四大件布局验证 ====================\n");
    std::printf("知识库 Level 1.1：一层楼四个区，编号从低到高 Text < Data ≈ BSS < Heap << Stack\n\n");

    int  sticky_note   = 1;                          // 便签区（标准术语：Stack，函数局部变量）
    void* borrowed_book = std::malloc(16);           // 临时借阅区（标准术语：Heap）

    uintptr_t text  = text_probe();
    uintptr_t data  = reinterpret_cast<uintptr_t>(&filled_registry);
    uintptr_t bss   = reinterpret_cast<uintptr_t>(&reserved_registry);
    uintptr_t heap  = reinterpret_cast<uintptr_t>(borrowed_book);
    uintptr_t stack = reinterpret_cast<uintptr_t>(&sticky_note);

    std::printf("  固定馆藏区 (Text)  : 0x%012lx   ← 编译后的机器指令，只读+可执行\n", (unsigned long)text);
    std::printf("  已填登记区 (Data)  : 0x%012lx   ← 值=%d\n", (unsigned long)data, filled_registry);
    std::printf("  预留登记区 (BSS)   : 0x%012lx   ← 值=%d\n", (unsigned long)bss, reserved_registry);
    std::printf("  临时借阅区 (Heap)  : 0x%012lx\n", (unsigned long)heap);
    std::printf("  便签区     (Stack) : 0x%012lx   ← 从高处往下贴\n\n", (unsigned long)stack);

    // ── 自校验：知识库 1.1 给出的排序必须成立 ────────────────────────────────
    std::printf("  ── 自校验（对照知识库 1.1 表格）──\n");
    check("Text  < Data （代码段在数据段下方）",        text  < data);
    check("Data  ≤ BSS  （BSS 紧跟 Data）",            data  <= bss);
    check("BSS   < Heap （堆在数据段上方）",            bss   < heap);
    check("Heap  << Stack（栈远在顶部，隔着巨大空地）", heap  < stack && stack - heap > (1UL << 30));
    check("预留登记区(BSS)开馆时统一清零",              reserved_registry == 0);

    std::free(borrowed_book);
    std::printf("\n  结论：%s\n", g_failures == 0
        ? "✅ PASS —— 四个区的真实地址排序与知识库 1.1 完全一致（ASLR 只动基址，不动次序）"
        : "❌ FAIL —— 排序不符合预期，请检查运行平台（本实验预设 Linux x86-64）");
    return g_failures == 0 ? 0 : 1;
}
