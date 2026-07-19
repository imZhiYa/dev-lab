# 🧠 virtual-memory-demo · 虚拟内存的 8 个可执行实证（C++20）

> dev-lab 的 OS 内存验证模块 —— 把知识库《🧠 虚拟内存》从"背下来"变成"跑出来"。
> 每个实验 = 一个单文件 `main()` + 自校验断言，跑完自己判 **✅ PASS / ❌ FAIL**（退出码说话，CI 直接吃）。
> 零外部依赖，Linux（/proc）与 macOS（libproc）双平台。

## 🧪 八个实验 ↔ 知识库映射

| 实验 | 它在验证什么（知识库 Level） |
|---|---|
| `vm01_address_space_layout` | 地址空间四大件真实排序，ASLR 只动基址不动次序（L1.1） |
| `vm02_base_limit_translation` | Base+Limit 翻译语义 + 越界拒绝 = 段错误的由来（L1.4） |
| `vm03_page_table_walk` | PTE 位段、四级页表拆解、512GB 单级灾难 → 40KB 救赎（L2/L3） |
| `vm04_demand_paging` | malloc 的 128MB 物理内存真立即给吗——缺页三闸门实证（L2.4） |
| `vm05_tlb_sensitivity` | 工作集 16 页 → 16384 页阶梯扫描，摸到 TLB 挤爆的单价跳档（L3.2） |
| `vm06_copy_on_write` | fork 零成本 + 真涂改才誊抄，誊抄计数一页不差（L4.4） |
| `vm07_page_replacement` | FIFO 的 Bélády 异常 vs LRU 的栈性质，CLOCK 近似收益（L4.1） |
| `vm08_ept_nested_walk` | EPT/NPT 双层翻译最坏 20~24 次 vs 单层 5 次（L6.1） |

## ⚡ 本地速跑（克隆后 3 步）

**① 环境（一行装好）**

| 系统 | 准备命令 |
|---|---|
| macOS | `xcode-select --install`（自带 clang++ 与 make；CMake 可选 `brew install cmake`） |
| Ubuntu/Debian | `sudo apt install -y build-essential`（CMake 可选 `sudo apt install cmake`） |
| Windows | 走 WSL2（vm04/vm06 的观测通道是 Linux/macOS 专属；其余实验纯逻辑任意系统可跑） |

**② 进目录 + 一条命令**

```bash
git clone https://github.com/imZhiYa/dev-lab.git && cd dev-lab/virtual-memory-demo
make run        # 快道：自动编译 8 个实验到 bin/ 并依序公审
```

**③ 验收：8 个 PASS 收尾才算跑对**

```
>>> 公审 bin/vm01_address_space_layout
  结论：✅ PASS —— 四个区的真实地址排序与知识库 1.1 完全一致……
>>> 公审 bin/vm04_demand_paging
  结论：✅ PASS —— 「先答应借、真翻才调」被三闸门数据实证……
……
✅ virtual-memory-demo 全部 8 个实验公审通过
```

任一实验 `❌ FAIL` 会立即中止并打印实测值 —— 先对照下方「⚠️ 平台差异须知」再下结论。

**进阶**：标准道 CTest 逐个过，或免构建系统单文件直编：

```bash
cmake -S . -B build && cmake --build build && ctest --test-dir build --output-on-failure
c++ -O2 -std=c++20 -Iinclude src/vm04_demand_paging.cpp -o vm04 && ./vm04
```

## ⚠️ 平台差异须知（三条血泪，细节在源码 ★ 注脚里）

1. **Apple Silicon** 用户态页是 **16KB**（不是 4KB）—— 页数类数字天然除以 4；
2. **CI/云宿主机**普遍开 **THP 透明大页**，缺页计数会被压成 1/512 —— `vm04` 已内置 `MADV_NOHUGEPAGE` 校正器；
3. **macOS** free 后 RSS 不回落是 **MADV_FREE 惰性回收**设计，不是泄漏；`vm06` 的 RSS 通道是「独占页账本」，形态与 Linux 相反但语义互证。

## 📏 参考形态（Linux 4KB 页，数字可漂、形态必须稳）

- vm04：分配后 RSS **+0.00MB** → 逐页触碰 **+128MB**、缺页 ≈ **32768** 次；
- vm06：fork 瞬间系统空闲 ≈ 0；子进程涂改一半 → 誊抄计数 **= 涂改页数，一页不多**；
- vm07：FIFO **9→10** 次缺页（异常成立）、LRU **10→8**（栈性质成立）。

---

📖 仓库总览与全模块对照见[根 README](../README.md) · CI：push 触碰本目录自动双通道公审（make + ctest）
