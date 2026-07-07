package com.zhiya.tree;

import java.util.*;

/**
 * 二叉树非递归遍历 · 纯栈/队列实现
 */
public class NonRecursiveTraversal {
    // ===== 内部树节点 =====
    static class TreeNode {
        char val;
        TreeNode left;
        TreeNode right;
        TreeNode(char val) { this.val = val; }
    }

    // =====================================
    // 1. 前序 · 根 → 左 → 右
    // =====================================
    public static List<Character> preorder(TreeNode root) {
        List<Character> res = new ArrayList<>();
        if (root == null) return res;
        Deque<TreeNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeNode cur = stack.pop();
            res.add(cur.val);
            if (cur.right != null) stack.push(cur.right);
            if (cur.left  != null) stack.push(cur.left);
        }
        return res;
    }

    // =====================================
    // 2. 中序 · 左 → 根 → 右
    // =====================================
    public static List<Character> inorder(TreeNode root) {
        List<Character> res = new ArrayList<>();
        Deque<TreeNode> stack = new ArrayDeque<>();
        TreeNode cur = root;
        while (cur != null || !stack.isEmpty()) {
            while (cur != null) {
                stack.push(cur);
                cur = cur.left;
            }
            cur = stack.pop();
            res.add(cur.val);
            cur = cur.right;
        }
        return res;
    }

    // =====================================
    // 3. 后序 · 左 → 右 → 根（单栈 + prev）
    // =====================================
    public static List<Character> postorder(TreeNode root) {
        List<Character> res = new ArrayList<>();
        if (root == null) return res;
        Deque<TreeNode> stack = new ArrayDeque<>();
        TreeNode prev = null;
        TreeNode cur = root;
        while (cur != null || !stack.isEmpty()) {
            while (cur != null) {
                stack.push(cur);
                cur = cur.left;
            }
            cur = stack.peek();
            if (cur.right == null || cur.right == prev) {
                res.add(cur.val);
                stack.pop();
                prev = cur;
                cur = null;
            } else {
                cur = cur.right;
            }
        }
        return res;
    }

    // =====================================
    // 4. 后序 · 双栈法
    // =====================================
    public static List<Character> postorderTwoStacks(TreeNode root) {
        List<Character> res = new ArrayList<>();
        if (root == null) return res;
        Deque<TreeNode> s1 = new ArrayDeque<>();
        Deque<TreeNode> s2 = new ArrayDeque<>();
        s1.push(root);
        while (!s1.isEmpty()) {
            TreeNode cur = s1.pop();
            s2.push(cur);
            if (cur.left  != null) s1.push(cur.left);
            if (cur.right != null) s1.push(cur.right);
        }
        while (!s2.isEmpty()) {
            res.add(s2.pop().val);
        }
        return res;
    }

    // =====================================
    // 5. 层序 · BFS
    // =====================================
    public static List<Character> levelorder(TreeNode root) {
        List<Character> res = new ArrayList<>();
        if (root == null) return res;
        Queue<TreeNode> q = new LinkedList<>();
        q.offer(root);
        while (!q.isEmpty()) {
            TreeNode cur = q.poll();
            res.add(cur.val);
            if (cur.left  != null) q.offer(cur.left);
            if (cur.right != null) q.offer(cur.right);
        }
        return res;
    }

    // =====================================
    // 6. 创建样例树 & 可视化打印
    // =====================================
    public static TreeNode buildSampleTree() {
        TreeNode a = new TreeNode('A');
        TreeNode b = new TreeNode('B');
        TreeNode c = new TreeNode('C');
        TreeNode d = new TreeNode('D');
        TreeNode e = new TreeNode('E');
        TreeNode f = new TreeNode('F');
        TreeNode g = new TreeNode('G');

        a.left = b;   a.right = c;
        b.left = d;   b.right = e;
        c.left = f;   c.right = g;

        return a;
    }

    public static void printTree(TreeNode root) {
        printTreeRec(root, 0);
    }

    private static void printTreeRec(TreeNode node, int depth) {
        if (node == null) return;
        printTreeRec(node.right, depth + 1);
        System.out.println(repeat("   ", depth) + "── " + node.val);
        printTreeRec(node.left, depth + 1);
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
        TreeNode root = buildSampleTree();

        System.out.println(repeat("=", 50));
        System.out.println("🌳 二叉树非递归遍历 · 代码验证");
        System.out.println("  树结构:");
        printTree(root);
        System.out.println(repeat("=", 50));

        System.out.println("\n📊 遍历结果:");
        System.out.println("  前序(根左右):    " + preorder(root));
        System.out.println("  中序(左根右):    " + inorder(root));
        System.out.println("  后序(左右根):    " + postorder(root));
        System.out.println("  后序(双栈法):    " + postorderTwoStacks(root));
        System.out.println("  层序(BFS):       " + levelorder(root));

        System.out.println("\n🔍 预期:");
        System.out.println("  前序: [A, B, D, E, C, F, G]");
        System.out.println("  中序: [D, B, E, A, F, C, G]");
        System.out.println("  后序: [D, E, B, F, G, C, A]");
        System.out.println("  层序: [A, B, C, D, E, F, G]");

        // 自动验证
        boolean preOk = preorder(root).toString().equals("[A, B, D, E, C, F, G]");
        boolean inOk  = inorder(root).toString().equals("[D, B, E, A, F, C, G]");
        boolean postOk= postorder(root).toString().equals("[D, E, B, F, G, C, A]");
        boolean p2Ok  = postorderTwoStacks(root).toString().equals("[D, E, B, F, G, C, A]");
        boolean lvOk  = levelorder(root).toString().equals("[A, B, C, D, E, F, G]");

        System.out.println("\n✅ 正确性判定:");
        System.out.println("  前序: " + (preOk ? "✅" : "❌"));
        System.out.println("  中序: " + (inOk  ? "✅" : "❌"));
        System.out.println("  后序(单栈): " + (postOk ? "✅" : "❌"));
        System.out.println("  后序(双栈): " + (p2Ok   ? "✅" : "❌"));
        System.out.println("  层序: " + (lvOk  ? "✅" : "❌"));
    }
}
