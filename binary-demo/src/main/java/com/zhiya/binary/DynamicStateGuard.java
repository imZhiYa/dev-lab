package com.zhiya.binary;

/**
 * 状态位实战引擎：高并发订单状态与特权管护
 * 演示：如何利用 1 个 int 保存十几种复核状态，实现极速通断
 */
public class DynamicStateGuard {

    // ==========================================
    // 1. 状态标尺定义 (严格遵循 2 的幂次，依次向左进位)
    // ==========================================
    public static final int ORDER_PLACED    = 1 << 0; // 0000 0001 (已下单)
    public static final int ORDER_PAID      = 1 << 1; // 0000 0010 (已支付)
    public static final int ORDER_SHIPPED   = 1 << 2; // 0000 0100 (已发货)
    public static final int ORDER_RECEIVED  = 1 << 3; // 0000 1000 (已签收)
    public static final int INVOICE_ISSUED  = 1 << 4; // 0001 0000 (已开发票)

    // 内部存储容器
    private volatile int orderFlags = 0;

    /**
     * 🔵 追加状态 (使用按位或 |)
     * 口诀：追加权限用 | ，不论重复执行多少次，状态稳固有磐
     */
    public void addState(int stateMask) {
        this.orderFlags |= stateMask;
    }

    /**
     * 🟢 检查状态 (使用按位与 &)
     * 口诀：检查状态用 & ，判断结果是否大于零
     */
    public boolean hasState(int stateMask) {
        return (this.orderFlags & stateMask) != 0;
    }

    /**
     * 🟣 清除状态 (使用按位与非 & ~)
     * 口诀：定向抹除用 & ~ ，剥离指定特权，周围数据毫无影响
     */
    public void removeState(int stateMask) {
        this.orderFlags &= ~stateMask;
    }

    /**
     * 🟡 提取实态快照 (展示底座二进制流)
     */
    public String dumpBinarySnapshot() {
        // 调用我们既有的 BinaryUtils 辅助类查看实际内存排布
        return BinaryUtils.toPrettyBinary(this.orderFlags);
    }

    public static void main(String[] args) {
        DynamicStateGuard order = new DynamicStateGuard();
        System.out.println("==================== [ 订单状态机位线推演 ] ====================");

        // 1. 客户下单并完成了支付
        order.addState(ORDER_PLACED);
        order.addState(ORDER_PAID);
        System.out.println("📦 下单+支付后实态: " + order.dumpBinarySnapshot());
        System.out.println("是否已支付? " + order.hasState(ORDER_PAID));    // true
        System.out.println("是否已发货? " + order.hasState(ORDER_SHIPPED)); // false

        // 2. 商家发货并一并开具了发票
        order.addState(ORDER_SHIPPED);
        order.addState(INVOICE_ISSUED);
        System.out.println("🚚 发货+发票后实态: " + order.dumpBinarySnapshot());

        // 3. 产生客诉，发票被红冲作废 (定向拔除发票位，保留已发货实态)
        order.removeState(INVOICE_ISSUED);
        System.out.println("🛑 发票作废后实态:   " + order.dumpBinarySnapshot());
        System.out.println("发票是否仍在? " + order.hasState(INVOICE_ISSUED)); // false
        System.out.println("货物是否仍在? " + order.hasState(ORDER_SHIPPED));  // true (周围毫发无伤!)
    }
}
