package com.zhiya.tree;

import java.util.*;

/**
 * B 树（阶=3，2-3 树）
 *
 * 性质：
 *  - 每个节点最多 2 个 key、3 个子节点
 *  - 所有叶子在同一层
 *  - 插入：先插入到叶子，满则分裂向上提中间 key
 */
public class BTree {

    private static final int ORDER = 3;
    private static final int MAX_KEYS = ORDER - 1; // 2

    static class BNode {
        List<Integer> keys = new ArrayList<>();
        List<BNode> children = new ArrayList<>();
    }

    private BNode root;

    public BTree() {
        root = new BNode();
    }

    // =====================================
    // 插入（递归 + 自底向上分裂）
    // =====================================
    public void insert(int key) {
        Result res = insertRec(root, key);
        if (res != null) {
            // 根分裂了，创建新根
            BNode newRoot = new BNode();
            newRoot.keys.add(res.key);
            newRoot.children.add(res.left);
            newRoot.children.add(res.right);
            root = newRoot;
        }
    }

    /**
     * 递归插入，返回分裂结果（null 表示无需分裂）
     */
    private Result insertRec(BNode node, int key) {
        // 1. 找到插入位置
        int i = 0;
        while (i < node.keys.size() && key > node.keys.get(i)) i++;

        // 2. 如果 key 已存在，直接忽略（B 树不存重复值）
        if (i < node.keys.size() && key == node.keys.get(i)) {
            System.out.println("  ⚠️ 重复 key " + key + "，忽略");
            return null;
        }

        // 3. 如果是叶子节点，直接插入
        if (node.children.isEmpty()) {
            node.keys.add(i, key);
            // 检查是否需要分裂
            if (node.keys.size() > MAX_KEYS) {
                return split(node);
            }
            return null;
        }

        // 4. 递归到子节点
        Result res = insertRec(node.children.get(i), key);
        if (res == null) return null;

        // 5. 子节点分裂了，把中间 key 插入当前节点
        node.keys.add(i, res.key);
        node.children.set(i, res.left);
        node.children.add(i + 1, res.right);

        // 如果当前节点也满了，继续向上分裂
        if (node.keys.size() > MAX_KEYS) {
            return split(node);
        }
        return null;
    }

    /** 分裂一个已满的节点（3个key → 左1 + 中上提 + 右1） */
    private Result split(BNode node) {
        int mid = 1; // 中间 key 的索引 (0,1,2)
        int midKey = node.keys.get(mid);

        BNode left = new BNode();
        left.keys.add(node.keys.get(0));

        BNode right = new BNode();
        right.keys.add(node.keys.get(2));

        // 如果不是叶子，转移子节点
        if (!node.children.isEmpty()) {
            left.children.add(node.children.get(0));
            left.children.add(node.children.get(1));
            right.children.add(node.children.get(2));
            right.children.add(node.children.get(3));
        }

        return new Result(midKey, left, right);
    }

    /** 分裂结果 */
    private static class Result {
        int key;
        BNode left, right;
        Result(int key, BNode left, BNode right) {
            this.key = key; this.left = left; this.right = right;
        }
    }

    // =====================================
    // 查找
    // =====================================
    public boolean search(int key) {
        return searchRec(root, key);
    }

    private boolean searchRec(BNode node, int key) {
        int i = 0;
        while (i < node.keys.size() && key > node.keys.get(i)) i++;
        if (i < node.keys.size() && key == node.keys.get(i)) return true;
        if (node.children.isEmpty()) return false;
        return searchRec(node.children.get(i), key);
    }

    // =====================================
    // 中序遍历（升序）
    // =====================================
    public void inOrder() {
        System.out.print("中序: ");
        inOrderRec(root);
        System.out.println();
    }

    private void inOrderRec(BNode node) {
        if (node == null) return;
        for (int i = 0; i < node.keys.size(); i++) {
            if (!node.children.isEmpty()) inOrderRec(node.children.get(i));
            System.out.print(node.keys.get(i) + " ");
        }
        if (!node.children.isEmpty()) inOrderRec(node.children.get(node.keys.size()));
    }

    // =====================================
    // 可视化打印
    // =====================================
    public void printTree() {
        System.out.println("🌳 B 树（阶=" + ORDER + "）:");
        printTreeRec(root, 0);
    }

    private void printTreeRec(BNode node, int depth) {
        if (node == null) return;
        // 从右到左递归遍历子树
        for (int i = node.children.size() - 1; i >= 0; i--) {
            printTreeRec(node.children.get(i), depth + 1);
        }
        // 打印当前节点
        System.out.println(repeat("   ", depth) + "── " + node.keys);
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
        System.out.println("🌳 B 树（阶=3） · 代码验证");
        System.out.println("  插入: 1~9");
        System.out.println(repeat("=", 50));

        BTree tree = new BTree();
        for (int i = 1; i <= 9; i++) tree.insert(i);

        tree.printTree();
        tree.inOrder();

        System.out.println("\n🔍 查找验证:");
        for (int i = 1; i <= 9; i++)
            System.out.println("  查找 " + i + ": " + (tree.search(i) ? "✅" : "❌"));
        System.out.println("  查找 0: " + (tree.search(0) ? "❌" : "✅"));
        System.out.println("  查找 10: " + (tree.search(10) ? "❌" : "✅"));
    }
}
