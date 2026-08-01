package com.zhiya.innodb;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示 InnoDB 核心机制：Page Directory (页内稀疏目录二分查找)
 * 解决痛点：B+树定位到了 16KB 的数据页，但页内可能有上千条记录（单向链表）。
 * 难道在内存中进行 O(N) 的线性遍历？绝对不行。
 *
 * 核心逻辑（因果链）：
 * 1. InnoDB 将页内的记录分组（通常 4~8 条为一组）。
 * 2. 提取每组最后一条记录（最大主键），放入一个连续的数组中，这个数组叫 Page Directory（页目录），每个元素叫 Slot（槽）。
 * 3. 查询时，先对连续数组（Slots）进行 O(logN) 的二分查找，定位到目标所在的组。
 * 4. 然后顺着组内的单向链表，进行极小范围的线性遍历（最多几条记录），达到极其极致的性能。
 *
 */
public class PageDirectoryDemo {

    // 页内单行记录（逻辑上是一个单向链表）
    static class Record {
        int id;
        String data;
        Record next; // 页内的下一条记录

        public Record(int id, String data) {
            this.id = id;
            this.data = data;
        }
    }

    // 16KB 数据页
    static class DataPage {
        Record head; // 记录链表头

        // 【核心】页目录：存放稀疏的路标（每组最大id记录的引用）
        List<Record> slots = new ArrayList<>();

        // 模拟插入数据并构建页目录 (简化版：假设按顺序插入，每 4~8 条打一个槽，模拟真实波动)
        public void buildPage(int[] ids) {
            Record current = null;
            int groupSize = 0;

            for (int i = 0; i < ids.length; i++) {
                Record rec = new Record(ids[i], "Data_" + ids[i]);
                if (head == null) {
                    head = rec;
                    current = rec;
                } else {
                    current.next = rec;
                    current = rec;
                }

                groupSize++;
                // 模拟 InnoDB：组大小在 4~8 条之间波动（真实是随插入/删除动态调整），
                // 将组内最后一条(最大值)放入 Slot
                int maxGroupSize = (i / 8) % 2 == 0 ? 4 : 8;
                if (groupSize == maxGroupSize || i == ids.length - 1) {
                    slots.add(rec);
                    groupSize = 0;
                }
            }
            // 2 字节/slot（真实 InnoDB 约定）
            int pageDirBytes = slots.size() * 2;
            System.out.println("[构建完成] 页内共 " + ids.length + " 条记录，生成了 " + slots.size()
                    + " 个 Slot 槽位（每组 4~8 条波动），Page Directory 仅占 " + pageDirBytes + " 字节（16KB 页的 "
                    + String.format("%.2f", pageDirBytes * 100.0 / 16384) + "%）");
        }

        // 核心查找算法：二分查找 Slot + 局部线性扫描
        public void search(int targetId) {
            System.out.println("\n>>> 开始在 16KB 页内查找 id = " + targetId);

            // 1. 二分查找定位 Slot
            int left = 0;
            int right = slots.size() - 1;
            int targetSlotIndex = -1;

            while (left <= right) {
                int mid = left + (right - left) / 2;
                Record midSlot = slots.get(mid);

                System.out.println("  [Slot二分查找] left=" + left + ", right=" + right + ", mid=" + mid + " (Slot最大id: " + midSlot.id + ")");

                if (midSlot.id >= targetId) {
                    targetSlotIndex = mid; // 可能在当前组
                    right = mid - 1;       // 尝试往左逼近寻找更精确的组
                } else {
                    left = mid + 1;        // 在右边的组
                }
            }

            if (targetSlotIndex == -1) {
                System.out.println("  -> 目标值大于所有 Slot 的最大值，不在本页！");
                return;
            }

            System.out.println("  [定位完毕] 锁定 Slot[" + targetSlotIndex + "]，该组最大值为 " + slots.get(targetSlotIndex).id);

            // 2. 找到上一个 Slot 作为起跑线（上一个组的最大值，就是当前组的前驱）
            Record startRecord;
            if (targetSlotIndex == 0) {
                startRecord = head; // 如果是第一个组，从页头开始扫描
            } else {
                startRecord = slots.get(targetSlotIndex - 1).next;
            }

            // 3. 极小范围的线性扫描 (最多只扫 4 次！)
            System.out.println("  [链表局部扫描] 从起点 id=" + startRecord.id + " 开始逐个核对...");
            Record current = startRecord;
            int scanCount = 0;
            while (current != null && current.id <= slots.get(targetSlotIndex).id) {
                scanCount++;
                if (current.id == targetId) {
                    System.out.println("  -> [命中数据] 扫了 " + scanCount + " 次，成功找到: " + current.data);
                    return;
                }
                current = current.next;
            }
            System.out.println("  -> [未命中] 扫了 " + scanCount + " 次，说明数据在这页的缝隙里不存在。");
        }

        // ---- 只统计比较次数，不打印过程（用于量化对比） ----
        int countBlockSearchCompares(int targetId) {
            int compares = 0;
            int left = 0, right = slots.size() - 1, targetSlotIndex = -1;
            while (left <= right) {
                int mid = left + (right - left) / 2;
                compares++;
                if (slots.get(mid).id >= targetId) { targetSlotIndex = mid; right = mid - 1; }
                else left = mid + 1;
            }
            if (targetSlotIndex == -1) return compares;
            Record start = (targetSlotIndex == 0) ? head : slots.get(targetSlotIndex - 1).next;
            Record cur = start;
            while (cur != null && cur.id <= slots.get(targetSlotIndex).id) {
                compares++;
                if (cur.id == targetId) return compares;
                cur = cur.next;
            }
            return compares;
        }

        int countLinearCompares(int targetId) {
            int compares = 0;
            Record cur = head;
            while (cur != null) {
                compares++;
                if (cur.id >= targetId) return compares;
                cur = cur.next;
            }
            return compares;
        }
    }

    public static void main(String[] args) {
        DataPage page = new DataPage();
        // 模拟页内有 15 条有序的主键记录
        int[] ids = {10, 25, 30, 42, 51, 66, 70, 77, 85, 99, 102, 115, 120, 134, 150};
        page.buildPage(ids);

        // 演示 1：查找存在的值
        page.search(77);

        // 演示 2：查找不存在的值 (比如找缝隙里的 50)
        page.search(50);

        // 演示 3：量化对比 —— 分块查找 vs 线性扫描
        System.out.println("\n>>> 量化对比：Page Directory 分块查找 vs 全链表线性扫描");
        DataPage qPage = new DataPage();
        int[] bigIds = new int[2000];
        for (int i = 0; i < 2000; i++) bigIds[i] = i * 2; // 0,2,4...3998
        qPage.buildPage(bigIds);   // 2000 条 → 500 个 slot

        int target = 1333;         // 介于 1332 与 1334 之间 → 不存在
        int blockCompares = qPage.countBlockSearchCompares(target);
        int linearCompares = qPage.countLinearCompares(target);
        System.out.println("  2000 条记录中找 " + target + "（不存在）：");
        System.out.println("    分块查找(二分 slot + 组内≤4次) = " + blockCompares + " 次比较");
        System.out.println("    线性扫描(全链表)             = " + linearCompares + " 次比较");
        System.out.println("    提升倍数 ≈ " + String.format("%.1f", linearCompares * 1.0 / blockCompares) + "x");
        System.out.println("    → 结论：每组 4~8 条 + slot 二分 = 分块查找（Block Search），");
        System.out.println("      比全链表线性扫描快一个数量级，这也是组内不用二分的原因（组太小，扫描更快）");
    }
}
