package com.zhiya.innodb;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示 InnoDB 核心机制：B+ 树的寻址逻辑 (只读路标，不存数据)
 * 解决痛点：红黑树/B树在磁盘场景下，单个节点承载数据量小导致"树高"过高，磁盘随机 I/O 次数多。
 *
 * 核心逻辑（因果链）：
 * 1. 最小 I/O 单元是 Page(16KB)。
 * 2. 如果非叶子节点也存数据（B树），16KB 存不了几个节点，导致树高急剧增加。
 * 3. B+ 树做了一场"外科手术"：非叶子节点彻底净化，只存【主键 + 子页的 Page No】。
 * 4. 这让非叶子节点的"扇出(Fan-out)"极大增加，3层树就能容纳千万级数据。
 */
public class BPlusTreeRoutingDemo {

    // --- 模拟磁盘上的 Page ---
    interface Page {
        int getPageNo();
    }

    // 索引页（非叶子节点）：只存路标！(Key + PageNo)
    static class IndexPage implements Page {
        int pageNo;
        // 路标条目：如果是 id >= minKey，就去 childPageNo 找
        List<Router> routers = new ArrayList<>();

        public IndexPage(int pageNo) { this.pageNo = pageNo; }

        @Override
        public int getPageNo() { return pageNo; }

        public void addRouter(int minKey, int childPageNo) {
            routers.add(new Router(minKey, childPageNo));
        }
    }

    static class Router {
        int minKey;
        int childPageNo;
        Router(int minKey, int childPageNo) {
            this.minKey = minKey; this.childPageNo = childPageNo;
        }
    }

    // 数据页（叶子节点）：真正存完整数据行的地方
    static class DataPage implements Page {
        int pageNo;
        // InnoDB 叶子节点是双向链表，此处用 next 模拟单向遍历支持范围查询
        int nextDataPageNo = -1;
        List<Row> rows = new ArrayList<>();

        public DataPage(int pageNo) { this.pageNo = pageNo; }

        @Override
        public int getPageNo() { return pageNo; }

        public void addRow(int id, String data) {
            rows.add(new Row(id, data));
        }
    }

    static class Row {
        int id;
        String data;
        Row(int id, String data) { this.id = id; this.data = data; }
    }

    // --- 模拟磁盘引擎：通过 pageNo 加载 16KB 的 Page ---
    static class DiskEngine {
        private Map<Integer, Page> diskPages = new HashMap<>();
        int ioCount = 0;

        public void formatAndSave(Page page) {
            diskPages.put(page.getPageNo(), page);
        }

        // Root 页常驻 Buffer Pool：命中缓存，0 次磁盘 I/O
        public Page loadRootPage(int pageNo) {
            System.out.println("[Buffer Pool 命中] Root 页常驻内存，0 次磁盘 I/O！");
            return diskPages.get(pageNo);
        }

        public Page loadPage(int pageNo) {
            ioCount++;
            System.out.println("[磁盘 I/O] 加载 Page No: " + pageNo);
            return diskPages.get(pageNo);
        }
    }

    // 根据 Root 路标指引，返回目标叶子页的 PageNo（只做二分路由，不加载）
    static int route(IndexPage root, int targetId) {
        int nextTargetPageNo = -1;
        // 二分查找"能覆盖 targetId 的最大路标"
        int low = 0, high = root.routers.size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            Router cur = root.routers.get(mid);
            if (targetId >= cur.minKey) {
                nextTargetPageNo = cur.childPageNo;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return nextTargetPageNo;
    }

    // 在指定叶子页上精确查找一行
    static Row findRow(DiskEngine disk, int dataPageNo, int targetId) {
        Page page = disk.loadPage(dataPageNo);
        DataPage dataPage = (DataPage) page;
        for (Row row : dataPage.rows) {
            if (row.id == targetId) {
                return row;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        DiskEngine disk = new DiskEngine();

        // 1. 造数据：模拟构建底层的 叶子节点 (Data Pages)
        DataPage dataPage10 = new DataPage(10);
        dataPage10.addRow(1, "Row_Data_1");
        dataPage10.addRow(5, "Row_Data_5");
        dataPage10.nextDataPageNo = 11; // 范围扫描的链表指针

        DataPage dataPage11 = new DataPage(11);
        dataPage11.addRow(12, "Row_Data_12");
        dataPage11.addRow(18, "Row_Data_18");
        dataPage11.nextDataPageNo = 12;

        DataPage dataPage12 = new DataPage(12);
        dataPage12.addRow(25, "Row_Data_25");
        dataPage12.addRow(30, "Row_Data_30");

        disk.formatAndSave(dataPage10);
        disk.formatAndSave(dataPage11);
        disk.formatAndSave(dataPage12);

        // 2. 向上抽取：构建非叶子节点 (Index Pages)，净化！只存路标！
        IndexPage rootPage3 = new IndexPage(3);
        // 如果 id >= 1，去 Page 10 找
        rootPage3.addRouter(1, 10);
        // 如果 id >= 12，去 Page 11 找
        rootPage3.addRouter(12, 11);
        // 如果 id >= 25，去 Page 12 找
        rootPage3.addRouter(25, 12);
        disk.formatAndSave(rootPage3);

        int rootPageNo = 3;

        // --- 场景 A: 走聚簇索引精确查找 id = 18 ---
        System.out.println("=== 场景 A: 聚簇索引精确查找 id = 18（点查，树高 = 2，I/O 数 = 1）===");
        int targetId = 18;
        // Step 1: Root 页常驻 Buffer Pool，不产生磁盘 I/O
        Page page = disk.loadRootPage(rootPageNo);

        // Step 2: 在非叶子节点中二分找路标
        IndexPage idxPage = (IndexPage) page;
        int nextTargetPageNo = route(idxPage, targetId);
        System.out.println("  -> [路标指引] id=" + targetId + " 位于区间，指示前往子节点 Page No: " + nextTargetPageNo);

        // Step 3: 只有叶子页需要真正落盘加载 → 1 次磁盘 I/O
        Row hit = findRow(disk, nextTargetPageNo, targetId);
        System.out.println("  -> [命中数据] 成功拿到完整行数据: " + hit.data);

        System.out.println("\n------------------------------------------------\n");

        // --- 场景 B: 范围查询（B+ 树相对 B 树的第二道分水岭） ---
        System.out.println("=== 场景 B: 范围查询 id >= 12 AND id <= 30 ===");
        int lowKey = 12, highKey = 30;
        int leafStartNo = route(idxPage, lowKey);
        System.out.println("  -> [第一步] 用二分先定位到左边界所在的叶子页 Page No: " + leafStartNo);

        int ioBeforeRange = disk.ioCount;
        DataPage cursor = (DataPage) disk.loadPage(leafStartNo);
        int rangeHitCount = 0;
        while (cursor != null) {
            System.out.println("  [链表遍历] 进入叶子页 Page No: " + cursor.pageNo);
            for (Row row : cursor.rows) {
                if (row.id >= lowKey && row.id <= highKey) {
                    rangeHitCount++;
                    System.out.println("    -> [范围命中] id=" + row.id + " : " + row.data);
                }
            }
            cursor = cursor.nextDataPageNo == -1 ? null : (DataPage) disk.loadPage(cursor.nextDataPageNo);
        }
        System.out.println("  -> [范围查询完成] 共命中 " + rangeHitCount + " 行，沿途顺序读取叶子页 "
                + (disk.ioCount - ioBeforeRange) + " 次 I/O，全部是顺序 I/O！");

        System.out.println("\n------------------------------------------------\n");

        // --- 场景 C: 扇出率对比 + 3 层容量推导（B+ 树"只读路标"值钱的数学证明） ---
        System.out.println("=== 场景 C: 扇出率对比 + 3 层容量推导 ===");
        int pageSize = 16000;                  // InnoDB 页大小 16KB，有效载荷约 16KB
        int keyBytes = 8;                      // 主键 BIGINT = 8B
        int childPointerBytes = 6;             // 子页指针 6B（InnoDB 压缩指针）
        int dataRowBytes = 1000;               // 假设每行完整数据 1KB

        int btreeFanout = pageSize / (keyBytes + dataRowBytes + childPointerBytes);   // B树节点要存完整数据
        int bplusFanout  = pageSize / (keyBytes + childPointerBytes);                 // B+树节点只存路标
        long leafRowsPerPage = pageSize / dataRowBytes;

        System.out.println("  单页有效载荷 16KB：");
        System.out.println("  B  树非叶子节点要存【完整数据行】→ 每节点最多 " + btreeFanout + " 个孩子");
        System.out.println("  B+ 树非叶子节点只存【主键+指针】→ 每节点最多 " + bplusFanout + " 个孩子");
        System.out.println("  → 扇出率提升 ≈ " + String.format("%.0f", bplusFanout * 1.0 / btreeFanout) + " 倍！");

        long rootLevel   = bplusFanout;
        long middleLevel = (long) bplusFanout * bplusFanout;
        long leafLevel   = middleLevel * leafRowsPerPage;
        System.out.println("  3 层 B+ 树容量推导：");
        System.out.println("    第 1 层(根)    可指向 " + String.format("%,d", rootLevel)   + " 个中间页");
        System.out.println("    第 2 层(中间)  可指向 " + String.format("%,d", middleLevel) + " 个叶子页");
        System.out.println("    第 3 层(叶子)  每页 " + leafRowsPerPage + " 行 → 最多 " + String.format("%,d", leafLevel) + " 行");
        System.out.println("    → 这就是为什么千万级数据，点查仍然只要 2~3 次磁盘 I/O。");
    }
}
