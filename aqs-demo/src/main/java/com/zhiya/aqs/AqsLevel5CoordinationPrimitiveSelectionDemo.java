package com.zhiya.aqs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AQS 演示。
 *
 * 对应层级：Level 5。
 * 演示主题：CountDownLatch、CyclicBarrier 与 Phaser 的生命周期边界。
 * 验证目标：一次性汇聚选 CountDownLatch，固定参与者的重复会合选 CyclicBarrier，动态参与者选 Phaser。
 * 实现边界：它们不都以 AQS 作为公开实现合同；本类关注的是与 AQS 同步器选型相关的等待语义，而非私有实现细节。
 */
public final class AqsLevel5CoordinationPrimitiveSelectionDemo {
    private static final int WORKER_COUNT = 2;

    private AqsLevel5CoordinationPrimitiveSelectionDemo() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        demonstrateCountDownLatchOneShot();
        demonstrateCyclicBarrierReuse();
        demonstratePhaserDynamicRegistration();
    }

    private static void demonstrateCountDownLatchOneShot() throws Exception {
        System.out.println("\n===== Level 5 演示：CountDownLatch 用于一次性汇聚 =====");
        CountDownLatch prerequisites = new CountDownLatch(WORKER_COUNT);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        Thread first = AqsDemoSupport.start("latch-worker-0", () -> {
            // 真实业务中这里可以是预热、配置加载或多个前置任务之一。
            prerequisites.countDown();
        }, firstFailure);
        Thread second = AqsDemoSupport.start("latch-worker-1", () -> prerequisites.countDown(), secondFailure);

        AqsDemoSupport.await(prerequisites, "全部前置任务完成");
        AqsDemoSupport.join(first, firstFailure);
        AqsDemoSupport.join(second, secondFailure);
        AqsDemoSupport.require(prerequisites.getCount() == 0, "CountDownLatch 未归零");
        System.out.println("CountDownLatch 已归零；它没有 reset API，适合一次性完成信号。");
    }

    private static void demonstrateCyclicBarrierReuse() throws Exception {
        System.out.println("\n===== Level 5 演示：CyclicBarrier 用于固定参与者的重复会合 =====");
        AtomicInteger completedPhases = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(WORKER_COUNT + 1, () -> {
            int phase = completedPhases.incrementAndGet();
            AqsDemoSupport.log("barrier-action", "第 " + phase + " 个会合阶段完成");
        });
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        Thread first = startBarrierWorker("barrier-worker-0", barrier, firstFailure);
        Thread second = startBarrierWorker("barrier-worker-1", barrier, secondFailure);

        // main 也是固定参与者。每一轮三方都 arrive 后，barrier 自动复位供下一轮继续使用。
        barrier.await();
        barrier.await();
        AqsDemoSupport.join(first, firstFailure);
        AqsDemoSupport.join(second, secondFailure);
        AqsDemoSupport.require(completedPhases.get() == 2, "CyclicBarrier 没有完成两次循环会合");
    }

    private static Thread startBarrierWorker(String name, CyclicBarrier barrier,
                                             AtomicReference<Throwable> failure) {
        return AqsDemoSupport.start(name, () -> {
            for (int phase = 0; phase < 2; phase++) {
                AqsDemoSupport.log(name, "到达第 " + (phase + 1) + " 个会合点");
                barrier.await();
            }
        }, failure);
    }

    private static void demonstratePhaserDynamicRegistration() throws Exception {
        System.out.println("\n===== Level 5 演示：Phaser 用于动态注册与注销参与者 =====");
        Phaser phaser = new Phaser(1); // coordinator 先注册为第一个 party。
        CountDownLatch registered = new CountDownLatch(WORKER_COUNT);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();

        Thread first = startPhaserWorker("phaser-worker-0", phaser, registered, firstFailure);
        Thread second = startPhaserWorker("phaser-worker-1", phaser, registered, secondFailure);
        AqsDemoSupport.await(registered, "动态 worker 注册到 Phaser");
        AqsDemoSupport.require(phaser.getRegisteredParties() == WORKER_COUNT + 1,
                "Phaser 注册参与者数量错误");

        int currentPhase = phaser.getPhase();
        int advancedPhase = phaser.arriveAndAwaitAdvance();
        AqsDemoSupport.require(advancedPhase == currentPhase + 1, "Phaser 没有在全部 party 到达后推进 phase");

        AqsDemoSupport.join(first, firstFailure);
        AqsDemoSupport.join(second, secondFailure);
        AqsDemoSupport.require(phaser.getRegisteredParties() == 1,
                "worker arriveAndDeregister 后只应保留 coordinator");
        phaser.arriveAndDeregister();
        AqsDemoSupport.require(phaser.isTerminated(), "最后一个 party 注销后 Phaser 应终止");
        System.out.println("Phaser 支持动态 register / arriveAndDeregister，适合参与者数量随阶段变化的工作流。");
    }

    private static Thread startPhaserWorker(String name, Phaser phaser, CountDownLatch registered,
                                            AtomicReference<Throwable> failure) {
        return AqsDemoSupport.start(name, () -> {
            phaser.register();
            registered.countDown();
            try {
                phaser.arriveAndAwaitAdvance();
            } finally {
                // worker 完成本阶段后退出工作流，不再影响后续 phase 的参与者数量。
                phaser.arriveAndDeregister();
            }
        }, failure);
    }
}

