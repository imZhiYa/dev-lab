package com.zhiya.binary;

/**
 * 力扣二进制经典解法与底盘洞察
 * 涵盖：异或防重置、x & (x-1) 汉明重量、寻找丢失数
 */
public class LeetCodeBitwiseClassics {

    /**
     * 🟢 力扣 136：只出现一次的数字 (Single Number)
     * 【题目】：给定一个非空整数数组，除了某个元素只出现一次以外，其余每个元素均出现两次。找出那个只出现了一次的元素。
     * 【位运算神机】：利用异或定律 A ^ A = 0，A ^ 0 = A，以及交换律。所有数异或一遍，同位双双同归于尽，剩下的就是单身狗！
     */
    public static int singleNumber(int[] nums) {
        int single = 0;
        for (int num : nums) {
            single ^= num;
        }
        return single;
    }

    /**
     * 🔵 力扣 191：位 1 的个数 (Number of 1 Bits / 汉明重量)
     * 【题目】：编写一个函数，输入是一个无符号整数，返回其二进制表达式中数字位数为 '1' 的个数。
     * 【位运算神机】：利用我们推导过的终极 Bit Hack —— n & (n - 1) 能强行斩落最低位的那个 1。斩了多少次才归零，就有多少个 1！
     */
    public static int hammingWeight(int n) {
        int count = 0;
        while (n != 0) {
            n &= (n - 1); // 强行消灭最低位的那个 1
            count++;
        }
        return count;
    }

    /**
     * 🟣 力扣 268：丢失的数字 (Missing Number)
     * 【题目】：给定一个包含 [0, n] 中 n 个数的数组 nums ，找出 [0, n] 这个范围内没有出现在数组中的那个数。
     * 【位运算神机】：将数组的下标 [0...n] 和数组里的值混在一起全体异或。依循异或互消原理，出现配对的统统归零，剩下的即为丢失的数！
     */
    public static int missingNumber(int[] nums) {
        int missing = nums.length; // 预置最高位下标 n
        for (int i = 0; i < nums.length; i++) {
            // 下标与实际值深度对撞
            missing ^= i ^ nums[i];
        }
        return missing;
    }

    public static void main(String[] args) {
        System.out.println("==================== [ 力扣经典算法位操演练 ] ====================");

        // 1. 测试单身狗寻找
        int[] duplicates = {4, 1, 2, 1, 2};
        System.out.println("力扣136 数组 [4,1,2,1,2] 中独子为: " + singleNumber(duplicates)); // 预期 4

        // 2. 测试汉明重量计算 (以数字 11 0b1011 为例，含有 3 个 1)
        int num = 11;
        System.out.println("力扣191 数字 11 (" + BinaryUtils.toPrettyBinary(num) + ") 含有 1 的个数: " + hammingWeight(num)); // 预期 3

        // 3. 测试丢失的数字
        int[] missingArr = {9, 6, 4, 2, 3, 5, 7, 0, 1}; // 范围 0~9，缺失了 8
        System.out.println("力扣268 数组 [9,6,4,2,3,5,7,0,1] 丢失数字: " + missingNumber(missingArr)); // 预期 8
    }
}
