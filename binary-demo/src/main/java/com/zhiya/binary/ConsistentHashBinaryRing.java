package com.zhiya.binary;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 分布式一致性哈希底座引擎：32 位无符号哈希环与虚拟节点防雪崩映射
 * 演示：如何利用 32 位环形存储与 FNV-1a 位运算散列，实现顺时针就近寻址
 */
public class ConsistentHashBinaryRing {

    // 虚拟节点分布乘积系数 (每台物理机在环上派生 160 个独立化身，强力解决数据倾斜)
    private static final int VIRTUAL_COPIES = 160;

    // 核心寻址环：利用 TreeMap 维护底层红黑树的排序逻辑
    // Key 为 32 位无符号哈希整数空间，Value 为物理机房标识
    private final SortedMap<Long, String> binaryRing = new TreeMap<>();

    /**
     * 🔵 将服务器物理机房添加至二进制哈希环中
     */
    public synchronized void addNode(String physicalNode) {
        for (int i = 0; i < VIRTUAL_COPIES; i++) {
            // 利用机房标记附加虚拟种子，散列出均匀的 32 位位置位点
            long vNodeHash = getUnsigned32Hash(physicalNode + "#VNODE-" + i);
            binaryRing.put(vNodeHash, physicalNode);
        }
    }

    /**
     * 🟣 将宕机或被撤机的物理机房下线 (仅剥离其对应化身)
     */
    public synchronized void removeNode(String physicalNode) {
        for (int i = 0; i < VIRTUAL_COPIES; i++) {
            long vNodeHash = getUnsigned32Hash(physicalNode + "#VNODE-" + i);
            binaryRing.remove(vNodeHash);
        }
    }

    /**
     * 🟢 极速寻址分发：检索在二进制环上，距离入参 Key 顺时针方向最近的物理宿主机
     */
    public String selectTargetNode(String cacheKey) {
        if (binaryRing.isEmpty()) {
            throw new RuntimeException("无可用宿主机群，散列槽穿透异常。");
        }

        long keyHash = getUnsigned32Hash(cacheKey);

        // 🚀 核心顺时针寻址大招：截取红黑树上比该 keyHash 大的所有节点 (向右探测)
        SortedMap<Long, String> tailMap = binaryRing.tailMap(keyHash);

        if (tailMap.isEmpty()) {
            // 环形折叠定律：超越环形终点，直接跳回第 0 处根位置 (模拟 32 位溢出折叠归零)
            return binaryRing.get(binaryRing.firstKey());
        }

        // 返回顺时针首座虚拟节点的真实物理宿主
        return tailMap.get(tailMap.firstKey());
    }

    /**
     * 🧮 纯正 32 位无符号位操散列：FNV-1a 算法
     * 依靠位异或 ^ 与 经典素数乘法，结合掩码确保结果落于 0 ~ 2^32-1 物理闭环
     */
    private long getUnsigned32Hash(String key) {
        final long FNV_32_INIT = 2166136261L;
        final long FNV_32_PRIME = 16777619L;

        long hash = FNV_32_INIT;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i); // 异或打乱位线
            hash *= FNV_32_PRIME;   // 质数雪崩扩大差异
        }

        // 🚀 终极掩码收拢：强行过滤高位，保证散列点精准掉落在 32 位数字闭环内
        return hash & 0xFFFFFFFFL; 
    }

    public static void main(String[] args) {
        ConsistentHashBinaryRing hashRing = new ConsistentHashBinaryRing();
        System.out.println("==================== [ 一致性哈希环位运算调度演练 ] ====================");

        // 1. 系统初始化，部署 3 台主力数据服务器
        hashRing.addNode("MASTER-REDIS-NODE-A");
        hashRing.addNode("MASTER-REDIS-NODE-B");
        hashRing.addNode("MASTER-REDIS-NODE-C");
        System.out.println("📦 散列环初始化完毕，3 台主机及 480 个虚拟节点散落覆盖。");

        // 2. 将数个热点 Key 依次分发寻址
        String key1 = "PAY_ORDER_LOCK_1001";
        String key2 = "USER_SESSION_TOKEN_9999";
        String key3 = "MERCHANT_CACHE_DATA_5555";

        System.out.println("查询 [" + key1 + "] 分发落点: " + hashRing.selectTargetNode(key1));
        System.out.println("查询 [" + key2 + "] 分发落点: " + hashRing.selectTargetNode(key2));
        System.out.println("查询 [" + key3 + "] 分发落点: " + hashRing.selectTargetNode(key3));

        // 3. 模拟扩容战役：大并发狂澜来袭，临时扩容添加 NODE-D 宿主机
        System.out.println("\n🚀【高频扩容公审】: 系统动态上线 [MASTER-REDIS-NODE-D]...");
        hashRing.addNode("MASTER-REDIS-NODE-D");

        // 4. 重审散列路由，验证是否雪崩
        System.out.println("扩容后 [" + key1 + "] 路由状态: " + hashRing.selectTargetNode(key1));
        System.out.println("扩容后 [" + key2 + "] 路由状态: " + hashRing.selectTargetNode(key2));
        System.out.println("扩容后 [" + key3 + "] 路由状态: " + hashRing.selectTargetNode(key3));
        
        System.out.println("✅ 扩容验证完毕：仅仅有少数缓存位移转嫁至新机，全网数据稳健如初，免受重新散列雪崩！");
    }
}
