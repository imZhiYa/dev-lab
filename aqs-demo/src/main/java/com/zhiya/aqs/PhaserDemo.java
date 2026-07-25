package com.zhiya.aqs;

import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phaser 演示。
 * <p>
 * 与 CyclicBarrier 的关键差异：
 * 1) 参与方数量可以动态增减（register/bulkRegister/arriveAndDeregister），
 * CyclicBarrier 的 parties 在构造时就固定死了。
 * 2) 天然支持多阶段（phase 0,1,2,...自增），每完成一轮所有已注册方到齐，
 * phase 号自动 +1；可以重写 onAdvance() 决定"哪一轮之后终止"。
 * 3) 一个参与方可以在中途退出（arriveAndDeregister），不会像 CyclicBarrier
 * 那样一人掉队就 Broken 整组；Phaser 会自动用剩余的注册方数继续推进。
 * <p>
 * 场景：模拟一个"分阶段任务"，工作线程数量在运行过程中会动态增减
 * （比如一部分线程提前完成所有阶段就退出，不再参与后续同步）。
 * <p>
 */
public class PhaserDemo {

    static void demoDynamicPartiesAcrossPhases() throws InterruptedException {
        System.out.println("=== [1] 动态参与方 + 多阶段：部分线程提前退出，不影响其余线程继续推进 ===");

        int totalPhases = 4;

        // 重写 onAdvance：每完成一个 phase 打印一次汇总；返回 true 表示终止 phaser
        final Phaser phaser = new Phaser() {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("  >>> phase " + phase + " 结束，下一阶段注册方数=" + registeredParties
                        + (registeredParties == 0 ? "，无人注册，phaser 即将终止" : "") + " <<<");
                // 只有当所有参与方都注销（registeredParties==0）时才终止 phaser，
                // 不根据"阶段数够了"提前终止——避免 arriveAndAwaitAdvance 在终止瞬间
                // 返回编码为负数的 phase，干扰演示输出。
                return registeredParties == 0;
            }
        };

        int workerCount = 4;
        final Thread[] workers = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            phaser.register(); // 每个线程开始工作前先注册为一个参与方
            // 线程 3 只参与前 2 个阶段就提前退出（模拟"部分任务提前完成"）
            final int myPhases = (id == 3) ? 2 : totalPhases;
            workers[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int p = 0; p < myPhases; p++) {
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(20, 80));
                        } catch (InterruptedException ignored) {
                        }
                        System.out.println("  [worker-" + id + "] 完成 phase " + phaser.getPhase() + " 的工作，到达同步点");
                        if (p == myPhases - 1 && myPhases < totalPhases) {
                            // 最后一次参与：到达 + 立即注销，之后不再计入同步
                            int reachedPhase = phaser.arriveAndAwaitAdvance();
                            System.out.println("  [worker-" + id + "] 提前退出，通过 phase " + reachedPhase + " 后不再参与后续阶段");
                            phaser.arriveAndDeregister(); // 额外注销掉自己（因为上面已经到达一次，这里模拟"做完自己那份就走"）
                            return;
                        } else {
                            int reachedPhase = phaser.arriveAndAwaitAdvance(); // 到达并等待本阶段所有人到齐
                            System.out.println("  [worker-" + id + "] 通过 phase " + reachedPhase + " 的同步点");
                        }
                    }
                    phaser.arriveAndDeregister(); // 正常做完全部阶段后注销
                }
            }, "worker-" + id);
            workers[i].start();
        }

        for (Thread w : workers) {
            w.join();
        }
        System.out.println("  结果：worker-3 在 phase 1 后就退出了，但 worker-0/1/2 依然顺利完成了全部 "
                + totalPhases + " 个阶段，phaser 没有像 CyclicBarrier 那样因为参与方变化而 Broken。\n");
    }

    static void demoRegisterMidFlight() throws InterruptedException {
        System.out.println("=== [2] 运行中动态新增参与方：register() 可以在任意时刻插入新成员 ===");

        final Phaser phaser = new Phaser(1); // 主线程自己先占一个 party
        System.out.println("  初始注册方数 = " + phaser.getRegisteredParties());

        Thread late = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                phaser.register(); // 中途动态加入
                System.out.println("  [late-comer] 中途 register() 加入，当前注册方数="
                        + phaser.getRegisteredParties());
                phaser.arriveAndDeregister();
            }
        }, "late-comer");
        late.start();

        Thread.sleep(50);
        System.out.println("  主线程 register() 之前的注册方数 = " + phaser.getRegisteredParties());
        phaser.arriveAndAwaitAdvance(); // 主线程等待 late-comer 加入并一起完成这一阶段
        late.join();
        System.out.println("  phase 推进后当前 phase = " + phaser.getPhase() + "\n");
    }

    public static void main(String[] args) throws InterruptedException {
        demoDynamicPartiesAcrossPhases();
        demoRegisterMidFlight();
    }
}
