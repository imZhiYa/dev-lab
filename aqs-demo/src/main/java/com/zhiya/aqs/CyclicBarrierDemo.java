package com.zhiya.aqs;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CyclicBarrier demo
 *
 * 与 CountDownLatch 的关键区别：
 *   1) 可循环复用：每当"参与方数"都到达栅栏后，barrier 自动重置，可以进入下一轮。
 *   2) 等的是"一组同伴互相到齐"，不是"一批任务完成"；参与者自己也是"最后一个到达者"，
 *      会触发一个可选的 barrierAction（在所有参与者都被释放之前，由最后一个到达的线程执行）。
 *   3) 只要有一个参与者因超时/中断/异常"掉队"，整个栅栏会被打破（BrokenBarrierException），
 *      其余等待者会立即收到异常，不会傻等——对应"废号不能挡住活号，但栅栏比锁更严格：一人失败全组失败"。
 *
 * 场景：多线程分阶段计算，每个阶段结束都要互相等待，等齐了再进入下一阶段。
 *
 * 运行： java CyclicBarrierDemo
 */
public class CyclicBarrierDemo {

    static void demoMultiRoundRendezvous() throws InterruptedException {
        System.out.println("=== [1] 多轮次栅栏：3 个线程，每轮都要等彼此到齐，再一起进入下一轮 ===");

        int parties = 3;
        int rounds = 3;
        AtomicInteger roundCounter = new AtomicInteger(0);

        // barrierAction：由"最后一个到达栅栏的线程"负责执行，且在所有人被释放之前执行完
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
            int round = roundCounter.incrementAndGet();
            System.out.println("  >>> 第 " + round + " 轮所有线程都已到齐，barrierAction 执行汇总，栅栏即将重置 <<<");
        });

        Thread[] workers = new Thread[parties];
        for (int i = 0; i < parties; i++) {
            final int id = i;
            workers[i] = new Thread(() -> {
                try {
                    for (int r = 1; r <= rounds; r++) {
                        Thread.sleep(20L * (id + 1) * r); // 每个线程速度不同，模拟各阶段耗时不均
                        System.out.println("  [worker-" + id + "] 完成第 " + r + " 轮工作，抵达栅栏，等待其他人");
                        int arrivalIndex = barrier.await(); // 返回值：到达顺序索引（parties-1 表示最后一个到达）
                        System.out.println("  [worker-" + id + "] 第 " + r + " 轮通过栅栏，到达序号=" + arrivalIndex);
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    System.out.println("  [worker-" + id + "] 栅栏异常：" + e.getClass().getSimpleName());
                }
            }, "worker-" + id);
            workers[i].start();
        }
        for (Thread w : workers) w.join();
        System.out.println("  结果：栅栏被复用了 " + rounds + " 轮，无需重新创建对象。\n");
    }

    static void demoBrokenBarrierPropagates() throws InterruptedException {
        System.out.println("=== [2] 一人掉队（超时），栅栏被打破，其余等待者立即收到异常 ===");

        int parties = 3; // 故意需要 3 方，但全程只有 2 个线程真正参与 —— 第三方永远不会到达
        CyclicBarrier barrier = new CyclicBarrier(parties);
        CountDownLatch done = new CountDownLatch(2);

        // patient：等待时间长，预期会因为同伴先超时而被连带打破（BrokenBarrierException）
        new Thread(() -> {
            try {
                System.out.println("  [patient] 到达栅栏，等待其余同伴（timeout=2000ms）...");
                barrier.await(2000, TimeUnit.MILLISECONDS);
                System.out.println("  [patient] 不应该走到这里！");
            } catch (InterruptedException e) {
                System.out.println("  [patient] 被中断");
            } catch (TimeoutException e) {
                System.out.println("  [patient] 自己等待超时");
            } catch (BrokenBarrierException e) {
                System.out.println("  [patient] 收到 BrokenBarrierException（同伴先超时，栅栏被打破，我被连带唤醒，没有傻等满 2000ms）");
            } finally {
                done.countDown();
            }
        }, "patient").start();

        // impatient：等待时间短，会先超时，触发栅栏 broken
        new Thread(() -> {
            try {
                System.out.println("  [impatient] 到达栅栏，等待其余同伴（timeout=300ms）...");
                barrier.await(300, TimeUnit.MILLISECONDS);
                System.out.println("  [impatient] 不应该走到这里！");
            } catch (InterruptedException e) {
                System.out.println("  [impatient] 被中断");
            } catch (TimeoutException e) {
                System.out.println("  [impatient] 自己先等待超时（第三方从未到达），栅栏将被标记为 broken");
            } catch (BrokenBarrierException e) {
                System.out.println("  [impatient] 收到 BrokenBarrierException");
            } finally {
                done.countDown();
            }
        }, "impatient").start();

        done.await();
        System.out.println("  barrier.isBroken() = " + barrier.isBroken()
                + "（一旦打破，必须显式 reset() 才能继续使用；patient 应远早于 2000ms 就被唤醒，而不是傻等满超时）\n");
    }


    public static void main(String[] args) throws InterruptedException{
        demoMultiRoundRendezvous();
        demoBrokenBarrierPropagates();
    }
}
