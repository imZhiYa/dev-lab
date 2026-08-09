package demo08;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事务事件（02 篇 Level 5）：@TransactionalEventListener 为什么"提交后才执行"
 *
 * 机制（Framework 6.1.14，spring-tx）：
 *   发布时适配器检查 TransactionSynchronizationManager.isSynchronizationActive()
 *   ① 无事务：fallbackExecution=false 直接丢弃；=true 立即执行
 *   ② 有事务：注册一个 TransactionSynchronization（提交回调），不立即执行
 *   ③ 提交时（afterCommit 回调）→ 移除同步器 → 执行监听器
 *
 * 本 demo 不引入数据库：用 TransactionSynchronizationManager.initSynchronization()
 * 激活"事务同步上下文"，再手动触发注册的 afterCommit 回调——精确还原提交时机的机制。
 *
 * 真实输出（JDK 21.0.11 + spring-context 6.1.14 + spring-tx 6.1.14）：
 *   [无事务] 发布 OrderPaidEvent（无事务边界）
 *   [fallback] 监听器执行（fallback=true）
 *   [无事务] AFTER_COMMIT(fallback=false) 执行=false；fallback=true 立即执行=true
 *   [有事务] 模拟真实事务边界（同步上下文 + 真实事务激活）
 *   [有事务] 发布后立即检查：两个监听器都未执行（提交前不触发）
 *   [有事务] 注册的事务同步器数量=2
 *   [提交] 手动触发 afterCompletion(STATUS_COMMITTED)
 *   [提交] AFTER_COMMIT(fallback=false) 监听器执行
 *   [提交] fallback 监听器执行
 *   [有事务] 提交后 AFTER_COMMIT 执行=true；fallback 执行=true
 */
public class TransactionalEventApp {

    static class OrderPaidEvent extends ApplicationEvent {
        OrderPaidEvent(Object source) { super(source); }
    }

    static final AtomicBoolean commitRan = new AtomicBoolean(false);
    static final AtomicBoolean fallbackRan = new AtomicBoolean(false);
    static final AtomicBoolean commitRanInTx = new AtomicBoolean(false);
    static final AtomicBoolean fallbackRanInTx = new AtomicBoolean(false);

    @Component
    static class CommitListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(OrderPaidEvent e) {
            commitRan.set(true);
            System.out.println("[提交] AFTER_COMMIT(fallback=false) 监听器执行");
        }
    }

    @Component
    static class FallbackListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
        public void on(OrderPaidEvent e) {
            fallbackRan.set(true);
            System.out.println("[fallback] 监听器执行（fallback=true）");
        }
    }

    @Configuration
    @Import({CommitListener.class, FallbackListener.class})
    static class AppConfig {
        @Bean
        TransactionalEventListenerFactory transactionalEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            // ===== 场景 1：无事务发布 =====
            System.out.println("[无事务] 发布 OrderPaidEvent（无事务边界）");
            ctx.publishEvent(new OrderPaidEvent(ctx));
            System.out.println("[无事务] AFTER_COMMIT(fallback=false) 执行=" + commitRan.get()
                    + "；fallback=true 立即执行=" + fallbackRan.get());

            // ===== 场景 2：有事务（模拟真实事务边界）=====
            commitRan.set(false);
            fallbackRan.set(false);
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                System.out.println("[有事务] 模拟真实事务边界（同步上下文 + 真实事务激活）");
                ctx.publishEvent(new OrderPaidEvent(ctx));
                System.out.println("[有事务] 发布后立即检查：两个监听器都未执行（提交前不触发）");

                List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
                System.out.println("[有事务] 注册的事务同步器数量=" + syncs.size());

                System.out.println("[提交] 手动触发 afterCompletion(STATUS_COMMITTED)");
                List<TransactionSynchronization> snapshot =
                        new java.util.ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
                for (TransactionSynchronization s : snapshot) {
                    s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                }
                System.out.println("[有事务] 提交后 AFTER_COMMIT 执行=" + commitRan.get()
                        + "；fallback 执行=" + fallbackRan.get());
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }
}
