package com.zhiya.tree;

import java.util.*;

/**
 * Trie 字典树（前缀树）
 *
 * 性质：每条边代表一个字符，从根到叶的路径拼成一个单词
 * 应用：搜索自动补全、拼写检查、IP 路由、词频统计
 */
public class Trie {

    static class TrieNode {
        TrieNode[] children = new TrieNode[26]; // 只支持小写 a-z
        boolean isEnd;
        int count; // 经过此节点的单词数（可用于词频/前缀统计）
    }

    private TrieNode root;

    public Trie() {
        root = new TrieNode();
    }

    // =====================================
    // 插入
    // =====================================
    public void insert(String word) {
        TrieNode cur = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (idx < 0 || idx >= 26) continue; // 跳过非小写字母
            if (cur.children[idx] == null) {
                cur.children[idx] = new TrieNode();
            }
            cur = cur.children[idx];
            cur.count++;
        }
        cur.isEnd = true;
    }

    // =====================================
    // 查找完整单词
    // =====================================
    public boolean search(String word) {
        TrieNode node = searchPrefix(word);
        return node != null && node.isEnd;
    }

    // =====================================
    // 查找前缀
    // =====================================
    public boolean startsWith(String prefix) {
        return searchPrefix(prefix) != null;
    }

    private TrieNode searchPrefix(String s) {
        TrieNode cur = root;
        for (char c : s.toCharArray()) {
            int idx = c - 'a';
            if (idx < 0 || idx >= 26) return null;
            if (cur.children[idx] == null) return null;
            cur = cur.children[idx];
        }
        return cur;
    }

    // =====================================
    // 删除
    // =====================================
    public boolean delete(String word) {
        if (!search(word)) return false;
        TrieNode cur = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            cur.children[idx].count--;
            cur = cur.children[idx];
        }
        cur.isEnd = false;
        return true;
    }

    // =====================================
    // 前缀匹配 · 返回所有以 prefix 开头的单词
    // =====================================
    public List<String> suggest(String prefix) {
        List<String> res = new ArrayList<>();
        TrieNode node = searchPrefix(prefix);
        if (node == null) return res;
        collect(node, new StringBuilder(prefix), res);
        return res;
    }

    private void collect(TrieNode node, StringBuilder sb, List<String> res) {
        if (node.isEnd) res.add(sb.toString());
        for (int i = 0; i < 26; i++) {
            if (node.children[i] != null) {
                sb.append((char) (i + 'a'));
                collect(node.children[i], sb, res);
                sb.deleteCharAt(sb.length() - 1);
            }
        }
    }

    // =====================================
    // 统计
    // =====================================
    public int wordCount() {
        return countWords(root);
    }

    private int countWords(TrieNode node) {
        int cnt = node.isEnd ? 1 : 0;
        for (int i = 0; i < 26; i++) {
            if (node.children[i] != null) {
                cnt += countWords(node.children[i]);
            }
        }
        return cnt;
    }

    // =====================================
    // 可视化打印
    // =====================================
    public void printTrie() {
        System.out.println("🌳 Trie 树结构:");
        printTrieRec(root, 0, "");
    }

    private void printTrieRec(TrieNode node, int depth, String path) {
        if (node.isEnd) {
            System.out.println("  " + repeat("   ", depth) + "📌 " + path);
        }
        for (int i = 0; i < 26; i++) {
            if (node.children[i] != null) {
                char c = (char) (i + 'a');
                printTrieRec(node.children[i], depth + 1, path + c);
            }
        }
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
        System.out.println("🌳 Trie 字典树 · 代码验证");
        System.out.println(repeat("=", 50));

        Trie trie = new Trie();
        String[] words = {"cat", "car", "card", "care", "dog", "do", "dot"};

        System.out.println("\n📥 插入: " + Arrays.toString(words));
        for (String w : words) trie.insert(w);

        trie.printTrie();

        System.out.println("\n📊 统计: 单词数 = " + trie.wordCount());

        System.out.println("\n🔍 查找验证:");
        System.out.println("  search(\"cat\"):  " + trie.search("cat"));
        System.out.println("  search(\"can\"):  " + trie.search("can"));
        System.out.println("  startsWith(\"ca\"): " + trie.startsWith("ca"));
        System.out.println("  startsWith(\"xy\"): " + trie.startsWith("xy"));

        System.out.println("\n💡 前缀补全 suggest(\"ca\"): " + trie.suggest("ca"));
        System.out.println("  前缀补全 suggest(\"do\"): " + trie.suggest("do"));

        System.out.println("\n🗑️ 删除 \"car\": " + trie.delete("car"));
        System.out.println("  search(\"car\"):  " + trie.search("car"));
        System.out.println("  search(\"card\"): " + trie.search("card"));
        System.out.println("  search(\"care\"): " + trie.search("care"));
        System.out.println("  前缀补全 suggest(\"ca\"): " + trie.suggest("ca"));
    }
}
