package com.zhiya.innodb;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 演示 InnoDB 核心机制：MVCC 的底层可见性算法
 * 解决痛点：读写冲突时，不想加锁阻塞，但又必须保证数据的一致性（RC 或 RR 隔离级别）。
 *
 * 核心逻辑（因果链）：
 * 1. 每一行记录都有隐藏字段：trx_id (最后修改它的事务ID) 和 roll_pointer (指向 Undo Log 的旧版本)。
 * 2. 当你发起 SELECT 时，会生成一个 ReadView（相机的快照）。
 * 3. 核心算法就是拿【数据行上的 trx_id】 和 【ReadView 中的事务状态】做比对。
 * 4. 如果判定不可见，顺着 roll_pointer 找上一个版本，继续判定，直到可见。
 *
 */
public class MVCCDemo {

    // 1. 模拟记录的版本链 (Undo Log 节点)
    static class UndoLogNode {
        long trxId;       // 产生这个版本的事务 ID
        String data;      // 版本的内容
        UndoLogNode next; // 回滚指针 (roll_pointer)，指向更老的版本

        public UndoLogNode(long trxId, String data, UndoLogNode next) {
            this.trxId = trxId;
            this.data = data;
            this.next = next;
        }
    }

    // 2. 模拟 Read View (核心快照)
    static class ReadView {
        long creatorTrxId;        // 当前执行查询的事务ID (即快照的拥有者)
        Set<Long> mIds;           // 拍快照时，当前系统【活跃（未提交）】的事务ID列表
        long minTrxId;            // up_limit_id: mIds 中的最小值
        long maxTrxId;            // low_limit_id: 拍快照时，系统将要分配的下一个事务ID (全局最大事务ID + 1)

        public ReadView(long creatorTrxId, Set<Long> mIds, long maxTrxId) {
            this.creatorTrxId = creatorTrxId;
            this.mIds = mIds;
            this.maxTrxId = maxTrxId;

            // 计算 minTrxId
            long min = Long.MAX_VALUE;
            for (Long id : mIds) {
                if (id < min) min = id;
            }
            this.minTrxId = mIds.isEmpty() ? maxTrxId : min;
        }
    }

    // 3. 核心可见性判定算法 (完全映射 InnoDB 源码)
    public static boolean isVisible(long rowTrxId, ReadView view) {
        // 规则 1：这个版本是我自己改的，当然可见！
        if (rowTrxId == view.creatorTrxId) {
            System.out.println("     => 规则1命中: 是我(Tx" + view.creatorTrxId + ")自己改的，可见！");
            return true;
        }

        // 规则 2：这个版本的事务，在我拍快照之前就已经提交了，绝对可见！
        if (rowTrxId < view.minTrxId) {
            System.out.println("     => 规则2命中: 版本所在的事务(Tx" + rowTrxId + ")在我拍快照前已提交，可见！");
            return true;
        }

        // 规则 3：这个版本的事务，是在我拍快照【之后】才开启的，绝对不可见！(它是来自未来的)
        if (rowTrxId >= view.maxTrxId) {
            System.out.println("     => 规则3命中: 版本所在的事务(Tx" + rowTrxId + ")是在我拍快照后才开的，来自未来，不可见！");
            return false;
        }

        // 规则 4：这个版本落在了 [minTrxId, maxTrxId) 这个尴尬区间
        // 意味着，它在拍快照的时候，可能还没提交，也可能提交了！
        if (view.mIds.contains(rowTrxId)) {
            System.out.println("     => 规则4(a)命中: 版本所在的事务(Tx" + rowTrxId + ")在拍快照时还活跃未提交，不可见！");
            return false;
        } else {
            System.out.println("     => 规则4(b)命中: 版本所在的事务(Tx" + rowTrxId + ")在拍快照时已经提交了，可见！");
            return true;
        }
    }

    // 4. 执行基于 MVCC 的查询
    public static String select(UndoLogNode head, ReadView view) {
        UndoLogNode current = head;
        while (current != null) {
            System.out.println("  [MVCC 扫描] 检查版本 -> Data: " + current.data + " ( trx_id = " + current.trxId + " )");

            if (isVisible(current.trxId, view)) {
                return current.data; // 找到了第一个对当前快照可见的版本！
            }

            System.out.println("  [MVCC 扫描] 不可见，顺着 roll_pointer 找上一个版本...\n");
            current = current.next;
        }
        return "记录不存在或完全不可见";
    }

    public static void main(String[] args) {
        System.out.println("=== 场景背景 ===");
        System.out.println("目前有一行数据，经历了三次修改，形成了一条 Undo Log 版本链。");
        // 最老的版本：事务 100 插入的初始数据
        UndoLogNode v1 = new UndoLogNode(100, "V1(初始)", null);
        // 第二个版本：事务 105 修改的
        UndoLogNode v2 = new UndoLogNode(105, "V2(被Tx105修改)", v1);
        // 最新的版本：事务 120 修改的，也是数据页上直接挂着的那个
        UndoLogNode head = new UndoLogNode(120, "V3(被Tx120修改)", v2);

        System.out.println("\n=== 测试 1：RR 隔离级别下的普通查询 ===");
        // 假设当前来查询的事务是 Tx200。
        // 它拍快照时，发现 Tx105 还没提交！Tx120 也还没提交！
        Set<Long> activeTrx = new HashSet<>(Arrays.asList(105L, 120L));
        // maxTrxId 假设是 130（下一个分配的id）
        ReadView rrView = new ReadView(200L, activeTrx, 130L);

        System.out.println("Tx200 拍下快照: Active = [105, 120], Min = 105, Max = 130");
        String result = select(head, rrView);
        System.out.println(">> Tx200 最终读到的数据: " + result);

        System.out.println("\n------------------------------------------------\n");

        System.out.println("=== 测试 2：Read Committed 隔离级别下的特性 ===");
        // 假设时间流逝，Tx105 提交了！此时 Tx200 又发起了第二次查询。
        // 如果是 RC 级别，它会【重新拍一次快照】。此时活跃事务里没了 105。
        Set<Long> rcActiveTrx = new HashSet<>(Arrays.asList(120L)); // 105 已经提交出去了
        ReadView rcView = new ReadView(200L, rcActiveTrx, 130L);

        System.out.println("Tx200 重新拍快照(RC级别): Active = [120], Min = 120, Max = 130");
        String rcResult = select(head, rcView);
        System.out.println(">> Tx200 在 RC 级别第二次读到的数据: " + rcResult);
        System.out.println("（注：这就是为什么 RC 级别会出现不可重复读！因为每次查询都会重新计算 ReadView，导致新提交的版本突然变得可见了。）");

        System.out.println("\n------------------------------------------------\n");

        System.out.println("=== 测试 3：RR 隔离级别复用【同一个】ReadView → 可重复读 ===");
        System.out.println("同样的时间线：Tx105 已经提交了。但 RR 级别【不会重新拍快照】！");
        System.out.println("Tx200 继续拿着第一次的 rrView (Active = [105, 120]) 做第二次查询：");
        String rrResult = select(head, rrView);  // 关键：传入的还是 rrView，不是新视图！
        System.out.println(">> Tx200 在 RR 级别第二次读到的数据: " + rrResult);
        System.out.println("（注：rrView 里 Tx105 依然在 m_ids 中，所以 V2 依旧不可见 → 可重复读！");
        System.out.println("  这就是 RR 与 RC 的唯一区别：快照拍一次还是拍两次。）");
    }
}
