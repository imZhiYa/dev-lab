package com.zhiya.innodb;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示 InnoDB 核心机制：Next-Key Lock (临键锁) 与 幻读防御
 * 解决痛点：在 RR 隔离级别下，MVCC 只能解决"快照读"的幻读。如果执行 SELECT ... FOR UPDATE (当前读)，
 * 如何防止别的事务在这个区间"凭空插入"新数据（幻读）？
 *
 * 核心逻辑（因果链）：
 * 1. B+ 树的叶子节点是一条条存在的记录，没有"空隙"的实体。
 * 2. 为了防止插入，InnoDB 发明了 Gap Lock（间隙锁），锁住两条记录之间的"空气"。
 * 3. Next-Key Lock = Record Lock (记录锁) + Gap Lock (间隙锁)，锁住 (左区间, 当前记录]。
 * 4. 当别的事务尝试插入新 ID 时，如果落在被锁住的 Gap 内，直接阻塞排队。
 *
 */
public class NextKeyLockDemo {

    // 模拟 B+ 树叶子节点上现存的索引值
    private final List<Integer> indexKeys = new ArrayList<>(List.of(10, 20, 30, 40));

    // 存放当前持有的 Next-Key Locks (左开右闭区间)
    static class NextKeyLock {
        int leftOpen;   // (
        int rightClose; // ]

        public NextKeyLock(int leftOpen, int rightClose) {
            this.leftOpen = leftOpen;
            this.rightClose = rightClose;
        }

        // 判定一个 insertId 是否被当前锁挡住
        public boolean blocks(int insertId) {
            return insertId > leftOpen && insertId <= rightClose;
        }

        @Override
        public String toString() {
            return "(" + leftOpen + ", " + rightClose + "]";
        }
    }

    private final List<NextKeyLock> activeLocks = new ArrayList<>();

    /**
     * 模拟事务发起区间查询：SELECT * FROM table WHERE id > 15 AND id < 25 FOR UPDATE;
     * InnoDB 会怎么加锁？
     */
    public void executeRangeQueryForUpdate(String txName) {
        System.out.println("🔒 [" + txName + "] 发起当前读：SELECT * WHERE id > 15 AND id < 25 FOR UPDATE;");

        // InnoDB 扫描索引，寻找覆盖这个区间的 Next-Key
        // 15 到 25 覆盖了记录 20，并延伸到了 30 的间隙
        // 锁定 20 的 Next-Key -> (10, 20]
        activeLocks.add(new NextKeyLock(10, 20));
        System.out.println("   -> [加锁] 扫描到记录 20，加 Next-Key Lock: (10, 20]");

        // 锁定 30 的 Next-Key -> (20, 30] (因为查询范围 <25，卡在了 20~30 这个区间)
        activeLocks.add(new NextKeyLock(20, 30));
        System.out.println("   -> [加锁] 扫描到记录 30 发现超出范围，加 Next-Key Lock: (20, 30]，停止扫描");
    }

    /**
     * 模拟另一个事务尝试插入新数据
     */
    public void tryInsert(String txName, int insertId) {
        System.out.print("📝 [" + txName + "] 尝试插入 id = " + insertId + " ... ");
        for (NextKeyLock lock : activeLocks) {
            if (lock.blocks(insertId)) {
                System.out.println("❌ 被锁 " + lock + " 阻塞！发生了死等 (防止了幻读)！");
                return;
            }
        }
        // 如果没有被任何锁挡住，插入成功
        System.out.println("✅ 插入成功！");
    }

    public static void main(String[] args) {
        NextKeyLockDemo engine = new NextKeyLockDemo();
        System.out.println("=== 现有 B+ 树索引数据: " + engine.indexKeys + " ===");

        // 事务 1 发起区间加锁查询
        engine.executeRangeQueryForUpdate("Tx_A");

        System.out.println("\n=== 并发事务尝试插入 (验证幻读防御) ===");
        // 事务 2 尝试插入 18 (落在 10~20 间隙)
        engine.tryInsert("Tx_B", 18);

        // 事务 3 尝试插入 26 (落在 20~30 间隙)
        engine.tryInsert("Tx_C", 26);

        // 事务 4 尝试插入 35 (落在 30~40 间隙，未被加锁)
        engine.tryInsert("Tx_D", 35);

        System.out.println("\n=== 边界验证 (左开右闭语义) ===");
        // 锁是 (10, 20] 和 (20, 30]：
        //   右闭 → 20 应被挡；左开 → 10 和 15 的"下界本身"不被锁区间含住
        engine.tryInsert("Tx_E", 20);   // 右闭：20 属于 (10,20] → 被挡
        engine.tryInsert("Tx_F", 10);   // 左开：(10,20] 不含 10 → 可插入
        engine.tryInsert("Tx_G", 5);    // 完全在锁区间左侧 → 可插入

        System.out.println("\n💡 架构师总结：");
        System.out.println("这就是为什么有时候你明明只查了 id=16 的数据，结果别人连 id=28 都插不进去的原因。");
        System.out.println("因为 InnoDB 为了保证你下次再查的时候，不会莫名其妙多出数据，把整个【空气间隙】给锁死了！");
        System.out.println("注意 Next-Key Lock 是【左开右闭】：(10,20] 会挡住 20 本身（记录锁），但不会挡住 10 这个下界。");
    }
}
