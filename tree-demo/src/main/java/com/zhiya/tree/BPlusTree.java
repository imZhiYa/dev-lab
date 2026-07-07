package com.zhiya.tree;

import java.util.*;

/**
 * B+ 树（阶=3）
 *
 * 与 B 树区别：
 *  1. 数据只在叶子节点
 *  2. 叶子链表串联，支持范围查询
 *  3. 内部节点 key 是路由标记
 */
public class BPlusTree {

    private static final int ORDER = 3;
    private static final int MAX_KEYS = ORDER - 1; // 2

    static class Node {
        List<Integer> keys = new ArrayList<>();
        List<Node> children = new ArrayList<>();
        boolean leaf = true;
        Node next; // 叶子链表指针
    }

    private Node root;

    public BPlusTree() {
        root = new Node();
        root.leaf = true;
    }

    // =====================================
    // 插入
    // =====================================
    public void insert(int key) {
        Result res = insertRec(root, key);
        if (res != null) {
            Node newRoot = new Node();
            newRoot.leaf = false;
            newRoot.keys.add(res.key);
            newRoot.children.add(res.left);
            newRoot.children.add(res.right);
            root = newRoot;
        }
    }

    private Result insertRec(Node node, int key) {
        int i = 0;
        while (i < node.keys.size() && key >= node.keys.get(i)) i++;

        if (node.leaf) {
            node.keys.add(i, key);
            if (node.keys.size() > MAX_KEYS) return splitLeaf(node);
            return null;
        }

        Result res = insertRec(node.children.get(i), key);
        if (res == null) return null;

        node.keys.add(i, res.key);
        node.children.set(i, res.left);
        node.children.add(i + 1, res.right);

        if (node.keys.size() > MAX_KEYS) return splitInternal(node);
        return null;
    }

    private Result splitLeaf(Node node) {
        int mid = 1;
        int midKey = node.keys.get(mid);

        Node right = new Node();
        right.leaf = true;
        // 右节点取 [mid..end)
        for (int j = mid; j < node.keys.size(); j++)
            right.keys.add(node.keys.get(j));
        // 左节点保留 [0..mid)
        for (int j = node.keys.size() - 1; j >= mid; j--)
            node.keys.remove(j);

        right.next = node.next;
        node.next = right;

        return new Result(midKey, node, right);
    }

    private Result splitInternal(Node node) {
        int mid = 1;
        int midKey = node.keys.get(mid);

        Node right = new Node();
        right.leaf = false;

        // 右节点取 [mid+1..end)
        for (int j = mid + 1; j < node.keys.size(); j++)
            right.keys.add(node.keys.get(j));
        for (int j = mid + 1; j < node.children.size(); j++)
            right.children.add(node.children.get(j));

        // 左节点保留 [0..mid)
        for (int j = node.keys.size() - 1; j >= mid; j--)
            node.keys.remove(j);
        for (int j = node.children.size() - 1; j >= mid + 1; j--)
            node.children.remove(j);

        return new Result(midKey, node, right);
    }

    private static class Result {
        int key; Node left, right;
        Result(int k, Node l, Node r) { key = k; left = l; right = r; }
    }

    // =====================================
    // 查找
    // =====================================
    public boolean search(int key) {
        Node leaf = findLeaf(root, key);
        return leaf.keys.contains(key);
    }

    private Node findLeaf(Node node, int key) {
        if (node.leaf) return node;
        int i = 0;
        while (i < node.keys.size() && key >= node.keys.get(i)) i++;
        return findLeaf(node.children.get(i), key);
    }

    // =====================================
    // 范围查询
    // =====================================
    public List<Integer> rangeQuery(int start, int end) {
        List<Integer> res = new ArrayList<>();
        Node leaf = findLeaf(root, start);
        while (leaf != null) {
            for (int k : leaf.keys) {
                if (k >= start && k <= end) res.add(k);
                if (k > end) return res;
            }
            leaf = leaf.next;
        }
        return res;
    }

    // =====================================
    // 中序遍历
    // =====================================
    public void inOrder() {
        System.out.print("中序: ");
        Node cur = root;
        while (!cur.leaf) cur = cur.children.get(0);
        while (cur != null) {
            for (int k : cur.keys) System.out.print(k + " ");
            cur = cur.next;
        }
        System.out.println();
    }

    // =====================================
    // 打印
    // =====================================
    public void printTree() {
        System.out.println("🌳 B+ 树（阶=" + ORDER + "）:");
        printRec(root, 0);

        System.out.print("\n🔗 叶子链表: ");
        Node cur = root;
        while (!cur.leaf) cur = cur.children.get(0);
        while (cur != null) {
            System.out.print(cur.keys + " → ");
            cur = cur.next;
        }
        System.out.println("null");
    }

    private void printRec(Node node, int d) {
        if (node == null) return;
        // 从右到左递遍历子树
        for (int i = node.children.size() - 1; i >= 0; i--) {
            if (!node.leaf) printRec(node.children.get(i), d + 1);
        }
        // 打印当前节点
        String tag = node.leaf ? "📄" : "📁";
        System.out.println(repeat("   ", d) + "── " + tag + " " + node.keys);
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
        System.out.println("🌳 B+ 树（阶=3） · 代码验证");
        System.out.println("  插入: 1~9");
        System.out.println(repeat("=", 50));

        BPlusTree tree = new BPlusTree();
        for (int i = 1; i <= 9; i++) tree.insert(i);

        tree.printTree();
        tree.inOrder();

        System.out.println("\n🔍 查找:");
        for (int i = 1; i <= 9; i++)
            System.out.println("  " + i + ": " + (tree.search(i) ? "✅" : "❌"));
        System.out.println("  0: " + (tree.search(0) ? "❌" : "✅"));
        System.out.println("  10: " + (tree.search(10) ? "❌" : "✅"));

        System.out.println("\n📊 范围查询 [3,7]: " + tree.rangeQuery(3, 7));
        System.out.println("  范围查询 [5,9]: " + tree.rangeQuery(5, 9));
    }
}
