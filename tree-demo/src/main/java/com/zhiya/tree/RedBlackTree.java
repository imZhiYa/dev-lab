package com.zhiya.tree;

/**
 * 红黑树 · 泛型 JDK 生产风格
 *
 * 五大规则：
 * 1. 每个节点非红即黑
 * 2. 根节点是黑色
 * 3. NIL 叶子节点是黑色
 * 4. 红色节点的两个子节点都是黑色（无连续红）
 * 5. 从根到任意 NIL 的路径上黑色节点数相同（黑高守恒）
 */
public class RedBlackTree<K extends Comparable<K>, V> {

    private static final boolean RED = false, BLACK = true;

    private static class RbNode<K, V> {
        K key;
        V val;
        RbNode<K, V> left, right, parent;
        boolean color = RED;

        RbNode(K k, V v, RbNode<K, V> p) {
            key = k;
            val = v;
            parent = p;
        }
    }

    private RbNode<K, V> root;

    // =====================================
    // 插入
    // =====================================

    public void put(K key, V val) {
        if (root == null) {
            root = new RbNode<>(key, val, null);
            root.color = BLACK;
            return;
        }
        RbNode<K, V> t = root, parent = null;
        int cmp;
        do {
            parent = t;
            cmp = key.compareTo(t.key);
            if (cmp < 0) t = t.left;
            else if (cmp > 0) t = t.right;
            else {
                t.val = val;
                return;
            }
        } while (t != null);

        RbNode<K, V> e = new RbNode<>(key, val, parent);
        if (cmp < 0) parent.left = e;
        else         parent.right = e;
        fixAfterInsertion(e);
    }

    private void fixAfterInsertion(RbNode<K, V> x) {
        x.color = RED;
        while (x != null && x != root && x.parent.color == RED) {
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                RbNode<K, V> y = rightOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == rightOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateLeft(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                RbNode<K, V> y = leftOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == leftOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateRight(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        root.color = BLACK;
    }

    // =====================================
    // 旋转（JDK 规范实现）
    // =====================================

    private void rotateLeft(RbNode<K, V> p) {
        if (p == null) return;
        RbNode<K, V> r = p.right;
        p.right = r.left;
        if (r.left != null) r.left.parent = p;
        r.parent = p.parent;
        if (p.parent == null) root = r;
        else if (p.parent.left == p) p.parent.left = r;
        else p.parent.right = r;
        r.left = p;
        p.parent = r;
    }

    private void rotateRight(RbNode<K, V> p) {
        if (p == null) return;
        RbNode<K, V> l = p.left;
        p.left = l.right;
        if (l.right != null) l.right.parent = p;
        l.parent = p.parent;
        if (p.parent == null) root = l;
        else if (p.parent.right == p) p.parent.right = l;
        else p.parent.left = l;
        l.right = p;
        p.parent = l;
    }

    // =====================================
    // 工具方法
    // =====================================

    private boolean colorOf(RbNode<K, V> p) { return p == null ? BLACK : p.color; }
    private void setColor(RbNode<K, V> p, boolean c) { if (p != null) p.color = c; }
    private RbNode<K, V> parentOf(RbNode<K, V> p) { return p == null ? null : p.parent; }
    private RbNode<K, V> leftOf(RbNode<K, V> p)   { return p == null ? null : p.left; }
    private RbNode<K, V> rightOf(RbNode<K, V> p)  { return p == null ? null : p.right; }

    // =====================================
    // 红黑树性质验证
    // =====================================

    public boolean isValidRedBlackTree() {
        return !hasRedRedViolation(root) && verifyBlackHeightInvariant() && rootIsBlack();
    }

    public boolean rootIsBlack() {
        return root != null && root.color == BLACK;
    }

    public boolean verifyBlackHeightInvariant() {
        return blackHeight(root) != -1;
    }

    private int blackHeight(RbNode<K, V> node) {
        if (node == null) return 1;
        int leftBh = blackHeight(node.left);
        int rightBh = blackHeight(node.right);
        if (leftBh == -1 || rightBh == -1 || leftBh != rightBh) return -1;
        return leftBh + (node.color == BLACK ? 1 : 0);
    }

    private boolean hasRedRedViolation(RbNode<K, V> node) {
        if (node == null) return false;
        if (node.color == RED) {
            if ((node.left != null && node.left.color == RED) ||
                    (node.right != null && node.right.color == RED)) {
                return true;
            }
        }
        return hasRedRedViolation(node.left) || hasRedRedViolation(node.right);
    }

    // =====================================
    // 中序遍历（升序打印）
    // =====================================

    public void inOrder() {
        inOrder(root);
        System.out.println();
    }

    private void inOrder(RbNode<K, V> node) {
        if (node == null) return;
        inOrder(node.left);
        System.out.print(node.key + "(" + (node.color == RED ? "R" : "B") + ") ");
        inOrder(node.right);
    }

    // =====================================
    // 可视化打印（🔴/⚫ 符号版）
    // =====================================

    public void printTree() {
        System.out.println("\n🌳 红黑树结构（右在上，左在下）:");
        if (root == null) {
            System.out.println("  （空树）");
            return;
        }
        printTreeRec(root, 0);
        System.out.println("  🔴=红节点  ⚫=黑节点");
    }

    private void printTreeRec(RbNode<K, V> node, int depth) {
        if (node == null) return;
        printTreeRec(node.right, depth + 1);
        String dot = node.color == RED ? "🔴" : "⚫";
        String marker = depth == 0 ? "── " : "── ";
        System.out.println(repeat("   ", depth) + marker + node.key + dot);
        printTreeRec(node.left, depth + 1);
    }

    /**
     * 打印节点对照表（与 cs-visual-tools 逐一对齐）
     */
    public void printVerifyTable() {
        if (root == null) {
            System.out.println("  （空树）");
            return;
        }
        System.out.println("\n🔍 节点对照表（L=左孩子, R=右孩子, 颜色）:");
        System.out.println("  ┌──────┬──────┬──────┬──────┐");
        System.out.println("  │ Key  │  L   │  R   │ 颜色 │");
        System.out.println("  ├──────┼──────┼──────┼──────┤");
        printTableRec(root);
        System.out.println("  └──────┴──────┴──────┴──────┘");
        System.out.println("  在 cs-visual-tools 中插入相同序列逐一对照 L/R");
    }

    private void printTableRec(RbNode<K, V> node) {
        if (node == null) return;
        printTableRec(node.left);
        String l = node.left  != null ? String.valueOf(node.left.key)  : "—";
        String r = node.right != null ? String.valueOf(node.right.key) : "—";
        String c = node.color == RED ? "🔴R" : "⚫B";
        System.out.println("  │ " + padRight(node.key.toString(), 3)
                + " │  " + padRight(l, 2)
                + " │  " + padRight(r, 2)
                + " │ " + c + " │");
        printTableRec(node.right);
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        for (int i = s.length(); i < width; i++) sb.append(' ');
        return sb.toString();
    }


    private static String repeat(String s, int count) {
        if (count <= 0) return "";
        char[] chars = new char[s.length() * count];
        for (int i = 0; i < count; i++)
            for (int j = 0; j < s.length(); j++)
                chars[i * s.length() + j] = s.charAt(j);
        return new String(chars);
    }


    public static void main(String[] args) {
        System.out.println(repeat("=", 60));
        System.out.println("🌳 红黑树 · 泛型版 · 代码验证");
        System.out.println("  插入序列: 8, 3, 10, 1, 6, 14, 4, 7, 13");
        System.out.println(repeat("=", 60));

        RedBlackTree<Integer, String> rb = new RedBlackTree<>();
        int[] keys = {8, 3, 10, 1, 6, 14, 4, 7, 13};

        for (int k : keys) {
            rb.put(k, "val" + k);
        }

        rb.printTree();
        rb.printVerifyTable();

        System.out.println("\n🔄 中序遍历（升序）:");
        rb.inOrder();

        System.out.println("\n🔍 五条性质验证:");
        System.out.println("  根为黑色:                " + rb.rootIsBlack());
        System.out.println("  无连续红:                " + !rb.hasRedRedViolation(rb.root));
        System.out.println("  黑高守恒:                " + rb.verifyBlackHeightInvariant());
        System.out.println("  整体合法:                " + rb.isValidRedBlackTree());

        // 额外验证：按层打印
        System.out.println("\n📊 按层对照（与 cs-visual-tools 层序遍历比对）:");
        printByLevel(rb.root);

        System.out.println("\n" + repeat("=", 60));
        System.out.println("✅ 红黑树验证完成");
        System.out.println(repeat("=", 60));
    }

    private static <K extends Comparable<K>, V> void printByLevel(RbNode<K, V> root) {
        if (root == null) return;
        java.util.Queue<RbNode<K, V>> queue = new java.util.LinkedList<>();
        queue.offer(root);
        int level = 0;
        while (!queue.isEmpty()) {
            int size = queue.size();
            System.out.print("  Lv" + level + ": ");
            for (int i = 0; i < size; i++) {
                RbNode<K, V> cur = queue.poll();
                String dot = cur.color == RED ? "🔴" : "⚫";
                System.out.print(cur.key + dot + " ");
                if (cur.left != null) queue.offer(cur.left);
                if (cur.right != null) queue.offer(cur.right);
            }
            System.out.println();
            level++;
        }
    }
}
