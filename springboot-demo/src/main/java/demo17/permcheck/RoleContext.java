package demo17.permcheck;

/**
 * 模拟"当前登录用户角色"的简化安全上下文（demo17 实证）：
 * ThreadLocal 存储——与事务同步器（TransactionSynchronizationManager）同构：
 * "上下文跟着线程走"。真实系统用 SecurityContextHolder 等，机制一致。
 */
public final class RoleContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String role) {
        CURRENT.set(role);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    private RoleContext() {
    }
}
