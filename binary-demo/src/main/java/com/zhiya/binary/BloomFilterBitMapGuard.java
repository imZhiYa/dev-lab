package com.zhiya.binary;

/**
 * 十亿级缓存防穿透卫士：手写微型布隆过滤器
 * 演示：如何利用 long[] 数组配合位移，打造空间复杂度极低的位图检索大闸
 */
public class BloomFilterBitMapGuard {

    // 内部申请 64 个 long 容量的位图 (64 * 64 = 4096 个独立 Bit)
    private final long[] bitMap = new long[64];
    // 用于位操作快速定位的掩码 (2的幂次 - 1)
    private static final int INDEX_MASK = 0x3F; // 63 (定位 long[] 数组下标)
    private static final int BIT_MASK   = 0x3F; // 63 (定位 long 内部 64 位偏移量)

    /**
     * 🔵 注入缓存标志位 (写入位图)
     */
    public void add(String key) {
        int hash1 = hashSeed1(key);
        int hash2 = hashSeed2(key);

        // 1. 定位第一个种子落点
        int index1 = (hash1 >>> 6) & INDEX_MASK; // 高位决断数组房号
        int bitPos1 = hash1 & BIT_MASK;          // 低位决断 Bit 偏移
        bitMap[index1] |= (1L << bitPos1);       // 运用 | 强行提拉至 1

        // 2. 定位第二个种子落点
        int index2 = (hash2 >>> 6) & INDEX_MASK;
        int bitPos2 = hash2 & BIT_MASK;
        bitMap[index2] |= (1L << bitPos2);
    }

    /**
     * 🟢 极速防穿透拦截 (查询位图)
     * 布隆定律：说你在，你不一定在；说你不在，你 100% 绝对不在！
     */
    public boolean mightContain(String key) {
        int hash1 = hashSeed1(key);
        int index1 = (hash1 >>> 6) & INDEX_MASK;
        int bitPos1 = hash1 & BIT_MASK;
        // 运用 & 查问状态，只要任一哈希触线为空，断定绝对不存在
        if ((bitMap[index1] & (1L << bitPos1)) == 0) {
            return false; 
        }

        int hash2 = hashSeed2(key);
        int index2 = (hash2 >>> 6) & INDEX_MASK;
        int bitPos2 = hash2 & BIT_MASK;
        return (bitMap[index2] & (1L << bitPos2)) != 0;
    }

    // 纯粹散列函数一 (采用经典素数 31 乘法计算)
    private int hashSeed1(String key) {
        int h = 0;
        for (char c : key.toCharArray()) {
            h = 31 * h + c;
        }
        return Math.abs(h);
    }

    // 纯粹散列函数二 (采用经典素数 131 乘法计算)
    private int hashSeed2(String key) {
        int h = 0;
        for (char c : key.toCharArray()) {
            h = 131 * h + c;
        }
        return Math.abs(h);
    }

    public static void main(String[] args) {
        BloomFilterBitMapGuard guard = new BloomFilterBitMapGuard();
        System.out.println("==================== [ 缓存防穿透布隆演算 ] ====================");

        // 1. 模拟系统预加载有效黑马用户 ID
        guard.add("VIP-USER-001");
        guard.add("VIP-USER-002");
        guard.add("GOOD-ORDER-999");
        System.out.println("📦 散列大闸部署完毕，合法凭据已就位。");

        // 2. 正规军入场比对
        System.out.println("检测 VIP-USER-001 是否通行: " + guard.mightContain("VIP-USER-001")); // 预期 true
        System.out.println("检测 GOOD-ORDER-999 是否通行: " + guard.mightContain("GOOD-ORDER-999")); // 预期 true

        // 3. 黑客利用未注册脏数据狂轰滥炸
        System.out.println("🛑 防穿透！检测未注册 HACKER-007: " + guard.mightContain("HACKER-007")); // 预期 false (秒拒，守护数据库)
        System.out.println("🛑 防穿透！检测非法查询 FAKE-9999: " + guard.mightContain("FAKE-9999")); // 预期 false (秒拒，守护数据库)
    }
}
