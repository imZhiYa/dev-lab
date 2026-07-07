package com.zhiya.tree;

/**
 * BST (二叉搜索树) 完整实现
 * <p>
 * 特性：
 * - 左子树所有节点值 < 根节点值
 * - 右子树所有节点值 > 根节点值
 * - 左右子树也分别是 BST
 * - 中序遍历得到升序序列
 * <p>
 * 面试重点：
 * 1. 插入/删除/查找的递归与迭代两种写法
 * 2. 删除节点时的三种情况（叶子 / 单子 / 双子）
 * 3. 验证一棵树是否为 BST（中序遍历或区间约束）
 */
public class BST {

    // ===== 节点定义 =====
    static class Node {
        int val;
        Node left;
        Node right;

        Node(int val) {
            this.val = val;
        }
    }

    private Node root;

    // =====================================
    // 1. 插入
    // =====================================

    /**
     * 递归插入
     */
    public void insert(int val) {
        root = insertRec(root, val);
    }

    private Node insertRec(Node node, int val) {
        if (node == null) {
            return new Node(val);
        }
        if (val < node.val) {
            node.left = insertRec(node.left, val);
        } else if (val > node.val) {
            node.right = insertRec(node.right, val);
        } else {
            // 重复值：根据场景可选忽略或计数，这里直接忽略
            System.out.println("⚠️ 重复值 " + val + "，已忽略");
        }
        return node;
    }

    /**
     * 迭代插入
     */
    public void insertIterative(int val) {
        Node newNode = new Node(val);
        if (root == null) {
            root = newNode;
            return;
        }
        Node cur = root;
        while (true) {
            if (val < cur.val) {
                if (cur.left == null) {
                    cur.left = newNode;
                    return;
                }
                cur = cur.left;
            } else if (val > cur.val) {
                if (cur.right == null) {
                    cur.right = newNode;
                    return;
                }
                cur = cur.right;
            } else {
                System.out.println("⚠️ 重复值 " + val + "，已忽略");
                return;
            }
        }
    }

    // =====================================
    // 2. 查找
    // =====================================

    public boolean search(int val) {
        return searchRec(root, val);
    }

    private boolean searchRec(Node node, int val) {
        if (node == null) {
            return false;
        }
        if (val == node.val) {
            return true;
        }
        return val < node.val ? searchRec(node.left, val) : searchRec(node.right, val);
    }

    public boolean searchIterative(int val) {
        Node cur = root;
        while (cur != null) {
            if (val == cur.val) return true;
            cur = val < cur.val ? cur.left : cur.right;
        }
        return false;
    }

    // =====================================
    // 3. 删除（最复杂的操作）
    // =====================================

    public void delete(int val) {
        root = deleteRec(root, val);
    }

    private Node deleteRec(Node node, int val) {
        if (node == null) {
            System.out.println("❌ 未找到 " + val + "，删除失败");
            return null;
        }

        if (val < node.val) {
            node.left = deleteRec(node.left, val);
        } else if (val > node.val) {
            node.right = deleteRec(node.right, val);
        } else {
            // 找到目标节点，三种情况：

            // 情况 1：叶子节点
            if (node.left == null && node.right == null) {
                System.out.println("🗑️ 删除叶子节点 " + val);
                return null;
            }
            // 情况 2：只有右子节点
            if (node.left == null) {
                System.out.println("🗑️ 删除节点 " + val + "（只有右子节点）");
                return node.right;
            }
            // 情况 2：只有左子节点
            if (node.right == null) {
                System.out.println("🗑️ 删除节点 " + val + "（只有左子节点）");
                return node.left;
            }
            // 情况 3：有两个子节点 — 用后继节点（右子树最小值）替换
            Node successor = findMin(node.right);
            System.out.println("🗑️ 删除节点 " + val + "（双子 → 后继 " + successor.val + " 替换）");
            node.val = successor.val;
            node.right = deleteRec(node.right, successor.val);
        }
        return node;
    }

    // =====================================
    // 4. 最小值 / 最大值
    // =====================================

    public int findMin() {
        if (root == null) throw new IllegalStateException("树为空");
        return findMin(root).val;
    }

    private Node findMin(Node node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }

    public int findMax() {
        if (root == null) throw new IllegalStateException("树为空");
        Node cur = root;
        while (cur.right != null) {
            cur = cur.right;
        }
        return cur.val;
    }

    // =====================================
    // 5. 树的高度
    // =====================================

    public int height() {
        return heightRec(root);
    }

    private int heightRec(Node node) {
        if (node == null) return -1; // 空树高度 -1，叶子高度 0
        return 1 + Math.max(heightRec(node.left), heightRec(node.right));
    }

    // =====================================
    // 6. 节点总数
    // =====================================

    public int size() {
        return sizeRec(root);
    }

    private int sizeRec(Node node) {
        if (node == null) return 0;
        return 1 + sizeRec(node.left) + sizeRec(node.right);
    }

    // =====================================
    // 7. 四种遍历
    // =====================================

    /**
     * 前序遍历：根 → 左 → 右
     */
    public void preOrder() {
        System.out.print("前序: ");
        preOrderRec(root);
        System.out.println();
    }

    private void preOrderRec(Node node) {
        if (node == null) return;
        System.out.print(node.val + " ");
        preOrderRec(node.left);
        preOrderRec(node.right);
    }

    /**
     * 中序遍历：左 → 根 → 右（BST 下为升序）
     */
    public void inOrder() {
        System.out.print("中序: ");
        inOrderRec(root);
        System.out.println();
    }

    private void inOrderRec(Node node) {
        if (node == null) return;
        inOrderRec(node.left);
        System.out.print(node.val + " ");
        inOrderRec(node.right);
    }

    /**
     * 后序遍历：左 → 右 → 根
     */
    public void postOrder() {
        System.out.print("后序: ");
        postOrderRec(root);
        System.out.println();
    }

    private void postOrderRec(Node node) {
        if (node == null) return;
        postOrderRec(node.left);
        postOrderRec(node.right);
        System.out.print(node.val + " ");
    }

    /**
     * 层序遍历（BFS）
     */
    public void levelOrder() {
        System.out.print("层序: ");
        if (root == null) {
            System.out.println("（空树）");
            return;
        }
        java.util.Queue<Node> queue = new java.util.LinkedList<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            System.out.print(cur.val + " ");
            if (cur.left != null) queue.offer(cur.left);
            if (cur.right != null) queue.offer(cur.right);
        }
        System.out.println();
    }


    /**
     * 通过中序遍历验证：BST 的中序一定是升序
     */
    public boolean isValidByInorder() {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        inorderCollect(root, list);
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) <= list.get(i - 1)) return false;
        }
        return true;
    }

    private void inorderCollect(Node node, java.util.List<Integer> list) {
        if (node == null) return;
        inorderCollect(node.left, list);
        list.add(node.val);
        inorderCollect(node.right, list);
    }

    /**
     * 通过区间约束验证（更优，O(n) 且不用额外空间）
     */
    public boolean isValidByRange() {
        return isValidRangeRec(root, null, null);
    }

    private boolean isValidRangeRec(Node node, Integer min, Integer max) {
        if (node == null) return true;
        // 当前节点必须在 (min, max) 区间内
        if (min != null && node.val <= min) return false;
        if (max != null && node.val >= max) return false;
        // 左子树所有值 < node.val，右子树所有值 > node.val
        return isValidRangeRec(node.left, min, node.val)
                && isValidRangeRec(node.right, node.val, max);
    }


    /**
     * 打印树结构（R=右子树在上，L=左子树在下）
     * 配合 cs-visual-tools 对照验证:
     * 在工具上插入同样的序列，对比每个节点的位置
     */
    public void printTree() {
        if (root == null) {
            System.out.println("（空树）");
            return;
        }
        System.out.println("🌳 树结构对照图（R 在上, L 在下）:");
        printTreeRec(root, 0);
        System.out.println("  提示：用 cs-visual-tools/tree-lab.html 插入相同序列验证");
    }

    private void printTreeRec(Node node, int depth) {
        if (node == null) return;
        // 先打印右子树（显示在上方）
        printTreeRec(node.right, depth + 1);
        // 缩进 + 节点值 + 方向标记
        System.out.print(repeat("   ", depth));
        if (depth == 0) {
            System.out.println("── " + node.val + " (根)");
        } else {
            System.out.println("── " + node.val);
        }
        // 再打印左子树（显示在下方）
        printTreeRec(node.left, depth + 1);
    }

    /**
     * 打印树验证对照表 — 跟 cs-visual-tools 逐节点对照
     * <p>
     * 输出格式：
     * 节点 → 左孩子(L) / 右孩子(R)
     * ─────────────────────────
     * 5 → L:3  R:7
     * 3 → L:2  R:4
     * 7 → L:6  R:8
     * 2 → L:1  R:null
     * ...
     */
    public void printVerifyTable() {
        if (root == null) {
            System.out.println("（空树）");
            return;
        }
        System.out.println("🔍 节点对照表（与可视化工具逐一比对）:");
        System.out.println("  ┌──────┬──────┬──────┐");
        System.out.println("  │ 节点  │  L(左) │  R(右) │");
        System.out.println("  ├──────┼──────┼──────┤");
        printTableRec(root);
        System.out.println("  └──────┴──────┴──────┘");
        System.out.println("  说明：在 tree-lab.html 中插入相同序列，逐节点对照 L/R");
    }

    private void printTableRec(Node node) {
        if (node == null) return;
        printTableRec(node.left);
        String l = node.left != null ? String.valueOf(node.left.val) : "—";
        String r = node.right != null ? String.valueOf(node.right.val) : "—";
        System.out.println("  │  " + center(node.val, 3) + "  │   " + l + "    │   " + r + "    │");
        printTableRec(node.right);
    }

    private static String center(int val, int width) {
        String s = String.valueOf(val);
        int pad = width - s.length();
        if (pad <= 0) return s;
        int leftPad = pad / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leftPad; i++) sb.append(' ');
        sb.append(s);
        for (int i = 0; i < pad - leftPad; i++) sb.append(' ');
        return sb.toString();
    }

    /**
     * 按层打印（BFS）— 方便与可视化工具逐层对照
     * 输出格式: 第0层: 5 | 第1层: 3 7 | 第2层: 2 4 6 8 | ...
     */
    public void printTreeByLevel() {
        if (root == null) {
            System.out.println("（空树）");
            return;
        }
        System.out.print("📊 按层对照: ");
        java.util.Queue<Node> queue = new java.util.LinkedList<>();
        queue.offer(root);
        int level = 0;
        while (!queue.isEmpty()) {
            int size = queue.size();
            System.out.print("Lv" + level + ": ");
            for (int i = 0; i < size; i++) {
                Node cur = queue.poll();
                System.out.print(cur.val + " ");
                if (cur.left != null) queue.offer(cur.left);
                if (cur.right != null) queue.offer(cur.right);
            }
            System.out.print("| ");
            level++;
        }
        System.out.println();
    }


    private static String repeat(String s, int count) {
        if (count <= 0) return "";
        // 底层原理：预先分配 char 数组，避免字符串拼接的 O(n²)
        char[] chars = new char[s.length() * count];
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < s.length(); j++) {
                chars[i * s.length() + j] = s.charAt(j);
            }
        }
        return new String(chars);
    }
    
    public void clear() {
        root = null;
    }


    public static void main(String[] args) {
        System.out.println(repeat("=", 50));
        System.out.println("🌳 BST 二叉搜索树 · 代码验证");
        System.out.println(repeat("=", 50));

        BST bst = new BST();

        // ---- 插入 ----
        System.out.println("\n📥 插入序列: 5, 3, 7, 2, 4, 8, 6, 1, 9, 10");
        int[] values = {5, 3, 7, 2, 4, 8, 6, 1, 9, 10};
        for (int v : values) {
            bst.insert(v);
        }

        System.out.println("\n📐 树结构:");
        bst.printTree();
        bst.printTreeByLevel();
        bst.printVerifyTable();

        // ---- 遍历 ----
        System.out.println("\n🔄 四种遍历:");
        bst.preOrder();
        bst.inOrder();
        bst.postOrder();
        bst.levelOrder();

        // ---- 属性 ----
        System.out.println("\n📊 树属性:");
        System.out.println("  节点数: " + bst.size());
        System.out.println("  高度:   " + bst.height());
        System.out.println("  最小值: " + bst.findMin());
        System.out.println("  最大值: " + bst.findMax());

        // ---- 查找 ----
        System.out.println("\n🔍 查找验证:");
        System.out.println("  查找 6: " + (bst.search(6) ? "✅ 找到" : "❌ 未找到"));
        System.out.println("  查找 11: " + (bst.search(11) ? "✅ 找到" : "❌ 未找到"));

        // ---- 删除 ----
        System.out.println("\n🗑️ 删除验证:");
        bst.delete(1);  // 叶子
        bst.delete(8);  // 单子
        bst.delete(5);  // 双子（根）

        System.out.println("\n📐 删除后的树结构:");
        bst.printTree();

        System.out.println("\n🔄 删除后中序遍历:");
        bst.inOrder();

        // ---- 验证 BST ----
        System.out.println("\n✅ BST 性质验证:");
        System.out.println("  isValidByInorder: " + (bst.isValidByInorder() ? "✅" : "❌"));
        System.out.println("  isValidByRange:   " + (bst.isValidByRange() ? "✅" : "❌"));

        // ---- 重复值测试 ----
        System.out.println("\n🔁 重复值插入:");
        bst.insert(3);

        // ---- 空树/边界测试 ----
        System.out.println("\n🧪 边界测试:");
        BST empty = new BST();
        System.out.println("  空树高度: " + empty.height());
        System.out.println("  空树大小: " + empty.size());
        System.out.println("  空树搜索 1: " + empty.search(1));

        // ---- 迭代插入测试 ----
        System.out.println("\n🔄 迭代插入测试:");
        BST bst2 = new BST();
        int[] vals2 = {10, 5, 15, 3, 7};
        for (int v : vals2) {
            bst2.insertIterative(v);
        }
        bst2.inOrder();

        // ---- 验证未通过的树 ----
        System.out.println("\n🚫 非法 BST 检测:");
        // 手动构造一个违反 BST 性质的树
        Node badRoot = new Node(10);
        badRoot.left = new Node(5);
        badRoot.right = new Node(15);
        badRoot.left.right = new Node(12); // 12 > 10，违反 BST！
        BST badBst = new BST();
        badBst.root = badRoot;
        System.out.print("  中序遍历: ");
        badBst.inOrder();
        System.out.println("  isValidByInorder: " + (badBst.isValidByInorder() ? "✅" : "❌"));
        System.out.println("  isValidByRange:   " + (badBst.isValidByRange() ? "✅" : "❌"));

        // ---- 迭代查找 ----
        System.out.println("\n🔍 迭代查找验证:");
        System.out.println("  查找 7: " + (bst2.searchIterative(7) ? "✅ 找到" : "❌ 未找到"));
        System.out.println("  查找 20: " + (bst2.searchIterative(20) ? "✅ 找到" : "❌ 未找到"));

        System.out.println("\n" + repeat("=", 50));
        System.out.println("✅ BST 验证完成");
        System.out.println(repeat("=", 50));
    }


}
