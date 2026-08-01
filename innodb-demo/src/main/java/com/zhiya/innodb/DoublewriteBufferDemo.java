package com.zhiya.innodb;
/**
 * 演示 InnoDB 核心机制：Doublewrite Buffer (两次写)
 * 解决痛点："Partial Page Write" (部分写失效 / 撕裂页)。
 *
 * 核心逻辑（因果链）：
 * 1. InnoDB 的一页是 16KB，而操作系统的文件系统一页通常是 4KB。
 * 2. 刷脏页时，操作系统需要分 4 次 I/O 把这 16KB 写进磁盘。
 * 3. 如果在写第 2 个 4KB 的时候断电了，磁盘上的这个 16KB 页就变成了一个"半新半旧"的撕裂页！
 * 4. Redo Log 救不了撕裂页，因为 Redo Log 记录的是物理修改（比如"把偏移量100的值改为A"），它依赖一个完好的原版页。
 * 5. 架构师的解法：刷入数据表(.ibd)之前，先顺序写入一段专属空间（Doublewrite Buffer）。
 *    如果写表时断电，重启时直接从 DW Buffer 拿完好的 16KB 覆盖回去，再应用 Redo Log！
 *
 */
public class DoublewriteBufferDemo {

    // 模拟一段 16KB 的数据 (分 4 个 Chunk)
    static class Page16KB {
        String chunk1 = "旧_4K_A";
        String chunk2 = "旧_4K_B";
        String chunk3 = "旧_4K_C";
        String chunk4 = "旧_4K_D";

        // 模拟事务把内存里的页改成了新数据
        public void updateInMemory() {
            this.chunk1 = "新_4K_A";
            this.chunk2 = "新_4K_B";
            this.chunk3 = "新_4K_C";
            this.chunk4 = "新_4K_D";
        }

        @Override
        public String toString() {
            return "[" + chunk1 + "|" + chunk2 + "|" + chunk3 + "|" + chunk4 + "]";
        }

        public Page16KB clonePage() {
            Page16KB p = new Page16KB();
            p.chunk1 = this.chunk1; p.chunk2 = this.chunk2;
            p.chunk3 = this.chunk3; p.chunk4 = this.chunk4;
            return p;
        }
    }

    private Page16KB diskDataFile = new Page16KB(); // 真实的表文件 (.ibd)
    private Page16KB doubleWriteBuffer = null;      // 两次写的备份区 (系统表空间)

    public void simulateCrashWrite(Page16KB dirtyPage, boolean enableDoubleWrite) {
        if (enableDoubleWrite) {
            System.out.println("📦 [Doublewrite开启] Step 1: 先将脏页顺序且完整地写入 DW Buffer...");
            this.doubleWriteBuffer = dirtyPage.clonePage(); // 模拟顺序写入 DW，这步极快且极度安全
        }

        System.out.println("💾 Step 2: 开始将脏页刷入真实的数据表文件 (.ibd)...");
        System.out.println("  -> 正在写入 Chunk 1...");
        this.diskDataFile.chunk1 = dirtyPage.chunk1;

        System.out.println("  -> 正在写入 Chunk 2...");
        this.diskDataFile.chunk2 = dirtyPage.chunk2;

        // 💥 灾难发生！
        System.out.println("💥 [断电宕机] 操作系统突然崩溃！Chunk 3 和 4 没来得及写进去！\n");
    }

    public void recoverAfterReboot(boolean enableDoubleWrite) {
        System.out.println("🔄 [重启恢复] 数据库重启，检查磁盘上的表文件...");
        System.out.println("   磁盘表文件现状: " + this.diskDataFile);

        if (this.diskDataFile.chunk1.startsWith("新") && this.diskDataFile.chunk3.startsWith("旧")) {
            System.out.println("   🚨 发现撕裂页！(Half-written Page) 页数据损坏！");
        }

        if (!enableDoubleWrite) {
            System.out.println("   ❌ 没有 Doublewrite Buffer 兜底，Redo Log 无法基于损坏的页做恢复。数据库起不来了！");
        } else {
            System.out.println("   🛡️ 发现开启了 Doublewrite Buffer！");
            System.out.println("   -> 正在从 DW Buffer 提取完好的页: " + this.doubleWriteBuffer);

            // 用 DW Buffer 的好页覆盖掉磁盘上的烂页
            this.diskDataFile = this.doubleWriteBuffer.clonePage();

            System.out.println("   ✅ 成功用 DW Buffer 修复了表文件！现状: " + this.diskDataFile);
            System.out.println("   （接下来只需顺畅地重放 Redo Log 即可确保数据不丢）");
        }
    }

    // 场景 C：写 Doublewrite Buffer 中途断电
    public void simulateCrashDuringDoubleWrite(Page16KB dirtyPage) {
        System.out.println("📦 [Doublewrite开启] Step 1: 将脏页顺序写入 DW Buffer...");
        System.out.println("  -> DW Buffer 正在写入 Chunk 1...");
        this.doubleWriteBuffer = new Page16KB(); // 新开一个空的 DW 页
        this.doubleWriteBuffer.chunk1 = dirtyPage.chunk1;
        System.out.println("  -> DW Buffer 正在写入 Chunk 2...");
        this.doubleWriteBuffer.chunk2 = dirtyPage.chunk2;
        System.out.println("💥 [断电宕机] 操作系统突然崩溃！DW Buffer 还没写完，且 .ibd 表文件压根还没碰！\n");
        // 注意：diskDataFile 从未被写入，仍是完整的旧页！
    }

    public void recoverFromDoubleWriteCrash() {
        System.out.println("🔄 [重启恢复] 数据库重启，检查磁盘上的表文件...");
        System.out.println("   磁盘表文件现状: " + this.diskDataFile);

        System.out.println("   ✅ 表文件完好无损（旧页 + Redo Log 即可恢复）！");
        System.out.println("   -> 为什么？因为 Doublewrite 的写入顺序是【先 DW 区，后 .ibd】！");
        System.out.println("   -> 崩溃时 .ibd 压根没被写入，磁盘上的页依然是完整的旧版本。");
        System.out.println("   -> DW Buffer 里只有半截数据 → 丢弃 DW 残页，直接用 .ibd 旧页 + 重放 Redo Log。");
    }

    public static void main(String[] args) {
        System.out.println("========== 场景 A：不开 Doublewrite 遭遇断电（对照组） ==========");
        DoublewriteBufferDemo engineA = new DoublewriteBufferDemo();
        Page16KB dirtyPageA = new Page16KB();
        dirtyPageA.updateInMemory();
        engineA.simulateCrashWrite(dirtyPageA, false);
        engineA.recoverAfterReboot(false);

        System.out.println("\n----------------------------------------------------\n");

        System.out.println("========== 场景 B：开启 Doublewrite 遭遇断电（写 .ibd 途中崩） ==========");
        DoublewriteBufferDemo engineB = new DoublewriteBufferDemo();
        Page16KB dirtyPageB = new Page16KB();
        dirtyPageB.updateInMemory();
        engineB.simulateCrashWrite(dirtyPageB, true);
        engineB.recoverAfterReboot(true);

        System.out.println("\n----------------------------------------------------\n");

        System.out.println("========== 场景 C：开启 Doublewrite 遭遇断电（写 DW Buffer 途中崩） ==========");
        DoublewriteBufferDemo engineC = new DoublewriteBufferDemo();
        Page16KB dirtyPageC = new Page16KB();
        dirtyPageC.updateInMemory();
        engineC.simulateCrashDuringDoubleWrite(dirtyPageC);
        engineC.recoverFromDoubleWriteCrash();
    }
}
