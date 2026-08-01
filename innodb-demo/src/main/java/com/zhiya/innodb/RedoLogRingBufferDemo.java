package com.zhiya.innodb;

import java.util.Arrays;

/**
 * 演示 InnoDB 核心机制：WAL (Write-Ahead Logging) 的核心底座 —— Redo Log 环形缓冲区
 * 解决痛点：数据库宕机怕丢数据，必须写磁盘(落盘)；但事务提交时直接把 Data Page 写回磁盘是随机 I/O，慢得发指。
 *
 * 核心逻辑（因果链）：
 * 1. 采用 WAL 机制，把随机 I/O 变成 "追加式的顺序 I/O"。先写 Redo Log，就认为事务成功。
 * 2. 磁盘空间有限，Redo Log 文件不能无限增长，必须做成首尾相连的【环形(Ring)】。
 * 3. 环上有两个重要指针在赛跑：
 *    - write_pos：当前写日志的位置。
 *    - checkpoint：后台线程默默把 Data Page 刷入磁盘后，擦除废弃日志的位置。
 * 4. 如果 write_pos 追上了 checkpoint (发生套圈)，说明日志写满了！数据库必须**阻塞死等**，直到后台线程刷脏页腾出空间。
 *
 */
public class RedoLogRingBufferDemo {

    private final String[] ringBuffer;
    private final int capacity;

    // 写入指针 (前锋)
    private int writePos = 0;
    // 擦除/推进指针 (后卫)
    private int checkPoint = 0;

    // LSN (Log Sequence Number)：每次追加日志单调递增，崩溃恢复的重放基准线
    private long nextLsn = 1000;
    private final long[] lsnAtPos; // 每个 slot 记录它对应的 LSN 起点

    public RedoLogRingBufferDemo(int capacity) {
        this.capacity = capacity;
        this.ringBuffer = new String[capacity];
        this.lsnAtPos = new long[capacity];
    }

    /**
     * 模拟前台事务写入 Redo Log
     */
    public synchronized boolean writeLog(String txName, String redoData) {
        // 计算下一个要写的位置 (预判)
        int nextWritePos = (writePos + 1) % capacity;

        // 【核心判断】：如果 write_pos 下一步就踩到 checkPoint 了，说明被套圈了，空间耗尽！
        if (nextWritePos == checkPoint) {
            System.out.println("❌ [阻塞] " + txName + " 事务写入失败！Redo Log 环已满 (writePos 追上 checkPoint)！" +
                    "必须等待后台线程 Flush 脏页！");
            return false;
        }

        ringBuffer[writePos] = redoData;
        lsnAtPos[writePos] = nextLsn;
        long thisLsn = nextLsn;
        nextLsn += 50; // 每条日志占用 50 字节 LSN 空间（简化示意）
        System.out.println("📝 [日志追加] " + txName + " 追加日志成功: '" + redoData
                + "' (LSN " + thisLsn + ") -> 写入位置 Slot[" + writePos + "]");

        // 指针向前推进
        writePos = nextWritePos;
        return true;
    }

    /**
     * 模拟后台线程把 Buffer Pool 的脏页真实刷入磁盘，随后推进 checkpoint 擦除日志空间
     */
    public synchronized void flushAndCheckpoint(int count) {
        System.out.println("\n⚙️ [后台线程启动] 开始将脏页刷盘，并推进 CheckPoint，擦除旧日志...");
        for (int i = 0; i < count; i++) {
            if (checkPoint == writePos) {
                System.out.println("   -> 已经没有旧日志需要擦除 (checkPoint 追平 writePos)！");
                break;
            }
            System.out.println("   -> 擦除位置 Slot[" + checkPoint + "] 的旧日志: " + ringBuffer[checkPoint]);
            ringBuffer[checkPoint] = null; // 物理擦除

            checkPoint = (checkPoint + 1) % capacity;
        }
        System.out.println("⚙️ [后台线程结束] 脏页刷盘完成。当前 writePos=" + writePos + ", checkPoint=" + checkPoint + "\n");
    }

    public void printRingStatus() {
        System.out.print("📊 [当前环状图] [ ");
        for (int i = 0; i < capacity; i++) {
            if (i == writePos && i == checkPoint) {
                System.out.print("[W/C] ");
            } else if (i == writePos) {
                System.out.print("[ W ] ");
            } else if (i == checkPoint) {
                System.out.print("[ C ] ");
            } else {
                System.out.print(ringBuffer[i] == null ? "____ " : "#### ");
            }
        }
        System.out.println("]");
    }

    /**
     * 模拟宕机后的崩溃恢复：从 checkpoint 记录的 LSN 开始重放日志
     * （checkpoint 之前的日志所对应的脏页已刷盘，无需重放）
     */
    public void crashRecovery() {
        System.out.println("\n💀 [系统崩溃] 数据库宕机！重启后进入崩溃恢复流程...");
        long startLsn = lsnAtPos[checkPoint] == 0 ? 1000 : lsnAtPos[checkPoint];
        System.out.println("   📌 checkpoint 记录 LSN = " + startLsn + "（之前的脏页已刷盘，无需重放）");

        int pos = checkPoint;
        int replayed = 0;
        while (ringBuffer[pos] != null) {
            System.out.println("   🔄 [重放] LSN " + lsnAtPos[pos] + ": " + ringBuffer[pos]);
            replayed++;
            pos = (pos + 1) % capacity;
            if (pos == writePos) break; // 重放到 writePos 为止（未落盘的最新日志）
        }
        System.out.println("   ✅ 崩溃恢复完成：从 LSN " + startLsn + " 起重放了 " + replayed + " 条日志，数据追平到最新！");
    }

    public static void main(String[] args) {
        // 创建一个容量为 5 的环形日志 (实际 InnoDB 里通常是几个 GB 大小的 ib_logfile0, ib_logfile1)
        // 注意：因为环形队列的实现特性，容量5实际上只能存4条数据，留1个空位判满。
        RedoLogRingBufferDemo ring = new RedoLogRingBufferDemo(5);

        System.out.println("=== 1. 正常高频交易时刻 ===");
        ring.writeLog("Tx1", "Update id=1");
        ring.writeLog("Tx2", "Insert id=2");
        ring.writeLog("Tx3", "Delete id=3");
        ring.printRingStatus();

        System.out.println("\n=== 2. 后台线程发挥作用 (异步刷盘) ===");
        // 后台线程默默把前2个事务对应的实际数据写进磁盘，腾出2个空位
        ring.flushAndCheckpoint(2);
        ring.printRingStatus();

        System.out.println("\n=== 3. 突发秒杀流量，写爆系统！ ===");
        ring.writeLog("Tx4", "Update id=4");
        ring.writeLog("Tx5", "Update id=5");
        ring.writeLog("Tx6", "Update id=6");
        ring.printRingStatus();

        System.out.println("\n[紧接着新事务 Tx7 尝试进场...]");
        ring.writeLog("Tx7", "Update id=7"); // 这里应该触发套圈阻塞报警！

        System.out.println("\n=== 4. 解决阻塞，被迫同步刷盘 ===");
        ring.flushAndCheckpoint(3);
        ring.printRingStatus();
        ring.writeLog("Tx7", "Update id=7"); // 此时终于可以写了

        System.out.println("\n=== 5. 某刻突然宕机，崩溃恢复重放 ===");
        ring.crashRecovery();
    }
}
