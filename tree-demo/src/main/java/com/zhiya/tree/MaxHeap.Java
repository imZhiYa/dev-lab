package com.zhiya.tree;

import java.util.*;

/**
 * 最大堆 · 数组实现
 * <p>
 * 性质：父节点 ≥ 子节点（完全二叉树）
 * 操作：上浮（插入）、下沉（删除堆顶）
 * 应用：优先队列、Top-K、堆排序
 */
public class MaxHeap {

    private int[] heap;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;

    public MaxHeap() {
        heap = new int[DEFAULT_CAPACITY];
        size = 0;
    }

    public MaxHeap(int capacity) {
        heap = new int[capacity];
        size = 0;
    }

    // =====================================
    // 插入 · 上浮
    // =====================================
    public void insert(int val) {
        ensureCapacity();
        heap[size] = val;
        siftUp(size);
        size++;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (heap[i] <= heap[parent]) break;
            swap(i, parent);
            i = parent;
        }
    }

    // =====================================
    // 删除堆顶 · 下沉
    // =====================================
    public int extractMax() {
        if (size == 0) throw new NoSuchElementException("堆为空");
        int max = heap[0];
        heap[0] = heap[size - 1];
        size--;
        siftDown(0);
        return max;
    }

    public int peek() {
        if (size == 0) throw new NoSuchElementException("堆为空");
        return heap[0];
    }

    private void siftDown(int i) {
        while (i < size) {
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            int largest = i;

            if (left < size && heap[left] > heap[largest]) largest = left;
            if (right < size && heap[right] > heap[largest]) largest = right;

            if (largest == i) break;
            swap(i, largest);
            i = largest;
        }
    }

    // =====================================
    // 工具
    // =====================================
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void ensureCapacity() {
        if (size == heap.length) {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }
    }

  /*  private void swap(int i, int j) {
        int tmp = heap[i];
        heap[i] = heap[j];
        heap[j] = tmp;
    }*/

    // 异或交换
    private void swap(int i, int j) {
        heap[i] ^= heap[j];
        heap[j] ^= heap[i];
        heap[i] ^= heap[j];
    }

    // =====================================
    // 可视化打印
    // =====================================
    public void printHeap() {
        System.out.print("📊 最大堆（数组）: ");
        System.out.print("[");
        for (int i = 0; i < size; i++) {
            System.out.print(heap[i]);
            if (i < size - 1) System.out.print(", ");
        }
        System.out.println("]");

        System.out.println("🌳 树结构（父 ≥ 子）:");
        printTreeRec(0, 0);
    }

    private void printTreeRec(int i, int depth) {
        if (i >= size) return;
        printTreeRec(2 * i + 2, depth + 1);
        System.out.println(repeat("   ", depth) + "── " + heap[i]);
        printTreeRec(2 * i + 1, depth + 1);
    }

    private static String repeat(String s, int count) {
        if (count <= 0) return "";
        char[] chars = new char[s.length() * count];
        for (int k = 0; k < count; k++)
            for (int j = 0; j < s.length(); j++)
                chars[k * s.length() + j] = s.charAt(j);
        return new String(chars);
    }

    public static void main(String[] args) {
        System.out.println(repeat("=", 50));
        System.out.println("🌳 最大堆 · 代码验证");
        System.out.println("  插入: 3, 10, 5, 8, 2, 7, 1, 9, 4, 6");
        System.out.println(repeat("=", 50));

        MaxHeap maxHeap = new MaxHeap();
        int[] vals = {3, 10, 5, 8, 2, 7, 1, 9, 4, 6};
        for (int v : vals) maxHeap.insert(v);
        maxHeap.printHeap();

        // 验证堆性质
        boolean valid = verifyMaxHeap(maxHeap.heap, maxHeap.size);
        System.out.println("\n🔍 堆性质验证: 父 ≥ 子? " + (valid ? "✅" : "❌"));

        // 提取验证
        System.out.println("\n🗑️ 依次提取堆顶:");
        List<Integer> sorted = new ArrayList<>();
        while (!maxHeap.isEmpty()) {
            sorted.add(maxHeap.extractMax());
        }
        System.out.println("  提取顺序: " + sorted);
        boolean desc = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) > sorted.get(i - 1)) {
                desc = false;
                break;
            }
        }
        System.out.println("  降序输出: " + (desc ? "✅" : "❌"));
    }

    private static boolean verifyMaxHeap(int[] arr, int n) {
        for (int i = 0; i < n; i++) {
            int l = 2 * i + 1, r = 2 * i + 2;
            if (l < n && arr[l] > arr[i]) return false;
            if (r < n && arr[r] > arr[i]) return false;
        }
        return true;
    }
}
