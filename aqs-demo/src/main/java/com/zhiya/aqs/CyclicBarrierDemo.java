package com.zhiya.aqs;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * CyclicBarrier 并发协调演示。
 *
 * 演示主题：多方同步屏障模式，所有线程在屏障处等待，全部到达后统一放行。
 * 演示目标：
 * - CyclicBarrier 初始化参与者数（PARTIES）
 * - 每个线程在某个点调用 await() 进行同步
 * - 当所有参与者都到达屏障时，屏障触发，所有线程同时解除阻塞
 * - 屏障可重复使用，不同轮次的线程可继续同步
 * - 屏障破裂时，所有线程抛出 BrokenBarrierException
 */
public class CyclicBarrierDemo {

    public static void main(String[] args) throws Exception {

        final int PARTIES = 3;

        CyclicBarrier barrier = new CyclicBarrier(PARTIES, () -> {
            System.out.println("All parties reached barrier. Barrier action executed.");
        });

        for (int i = 1; i <= PARTIES; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    for (int round = 1; round <= 3; round++) {
                        System.out.println("Thread " + id + " working for round " + round);
                        Thread.sleep(300 + (long) (Math.random() * 400));
                        System.out.println("Thread " + id + " waiting at barrier for round " + round);
                        barrier.await();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    System.out.println("Barrier broken for thread " + id);
                }
            }, "party-" + i).start();
        }
    }
}
