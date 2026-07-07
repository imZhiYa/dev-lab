package com.zhiya.tree;

import java.util.*;

/**
 * 最小堆 · 数组实现
 * <p>
 * 性质：父节点 ≤ 子节点（完全二叉树）
 * 与最大堆唯一区别：比较符号反转
 */
public class MinHeap {

    private int[] heap;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;

    public MinHeap() {
        heap = new int[DEFAULT_CAPACITY];
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
            if (heap[i] >= heap[parent]) break;  // 最小堆：父 ≤ 子
            swap(i, parent);
            i = parent;
        }
    }

    // =====================================
    // 删除堆顶 · 下沉
    // =====================================
    public int extractMin() {
        if (size == 0) throw new NoSuchElementException("堆为空");
        int min = heap[0];
        heap[0] = heap[size - 1];
        size--;
        siftDown(0);
        return min;
    }

    public int peek() {
        if (size == 0) throw new NoSuchElementException("堆为空");
        return heap[0];
    }

    private void siftDown(int i) {
        while (i < size) {
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            int smallest = i;

            if (left < size && heap[left] < heap[smallest]) smallest = left;
            if (right < size && heap[right] < heap[smallest]) smallest = right;

            if (smallest == i) break;
            swap(i, smallest);
            i = smallest;
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

    /*private void swap(int i, int j) {
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
        System.out.print("📊 最小堆（数组）: ");
        System.out.print("[");
        for (int i = 0; i < size; i++) {
            System.out.print(heap[i]);
            if (i < size - 1) System.out.print(", ");
        }
        System.out.println("]");

        System.out.println("🌳 树结构（父 ≤ 子）:");
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

    // =====================================
    // main
    // =====================================
    public static void main(String[] args) {
        System.out.println(repeat("=", 50));

        System.out.println("🌳 最小堆 · 代码验证");
        System.out.println("  插入: 3, 10, 5, 8, 2, 7, 1, 9, 4, 6");
        System.out.println(repeat("=", 50));

        MinHeap minHeap = new MinHeap();
        int[] vals = {3, 10, 5, 8, 2, 7, 1, 9, 4, 6};
        for (int v : vals) minHeap.insert(v);
        minHeap.printHeap();

        boolean valid = verifyMinHeap(minHeap.heap, minHeap.size);
        System.out.println("\n🔍 堆性质验证: 父 ≤ 子? " + (valid ? "✅" : "❌"));

        System.out.println("\n🗑️ 依次提取堆顶:");
        List<Integer> sorted = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            sorted.add(minHeap.extractMin());
        }
        System.out.println("  提取顺序: " + sorted);
        boolean asc = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) < sorted.get(i - 1)) {
                asc = false;
                break;
            }
        }
        System.out.println("  升序输出: " + (asc ? "✅" : "❌"));
    }

    private static boolean verifyMinHeap(int[] arr, int n) {
        for (int i = 0; i < n; i++) {
            int l = 2 * i + 1, r = 2 * i + 2;
            if (l < n && arr[l] < arr[i]) return false;
            if (r < n && arr[r] < arr[i]) return false;
        }
        return true;
    }
}
