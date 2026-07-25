package com.zhiya.aqs;

import java.util.concurrent.Phaser;

/**
 * Phaser 并发协调演示。
 *
 * 演示主题：可重用的多阶段同步屏障，支持动态注册和注销参与者。
 * 演示目标：
 * - Phaser 通过阶段(phase)管理多轮同步，每个阶段对应一个屏障
 * - 线程调用 arriveAndAwaitAdvance() 在当前阶段阻塞，等待所有参与者到达
 * - 参与者可动态注册(register)或注销(arriveAndDeregister)
 * - 所有参与者到达屏障后，阶段自动递进，继续下一阶段
 * - 当没有参与者时 Phaser 终止(terminated)，避免无限等待
 */
public class PhaserDemo {

    public static void main(String[] args) {
        Phaser phaser = new Phaser(1); // 注册主线程为一个参与者
        // 启动三个参与者线程
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            phaser.register(); // 动态注册
            new Thread(() -> {
                System.out.println("Thread " + id + " started, phase: " + phaser.getPhase());
                try {
                    // 阶段 0 工作
                    Thread.sleep(200 + (long) (Math.random() * 400));
                    System.out.println("Thread " + id + " arrived at phase 0");
                    phaser.arriveAndAwaitAdvance();

                    // 阶段 1 工作
                    Thread.sleep(200 + (long) (Math.random() * 400));
                    System.out.println("Thread " + id + " arrived at phase 1");
                    // 假设线程 3 在完成第一阶段后退出，不参与第二阶段
                    if (id == 3) {
                        phaser.arriveAndDeregister();
                        System.out.println("Thread " + id + " deregistered after phase 1");
                    } else {
                        phaser.arriveAndAwaitAdvance();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "phaser-worker-" + i).start();
        }

        // 主线程等待所有阶段完成
        // 主线程完成自己的 arrive 操作以推进阶段
        System.out.println("Main thread waiting for phase 0 completion");
        phaser.arriveAndAwaitAdvance();

        System.out.println("Main thread waiting for phase 1 completion");
        phaser.arriveAndAwaitAdvance();

        // 结束时注销主线程
        phaser.arriveAndDeregister();
        System.out.println("Phaser terminated: " + phaser.isTerminated());
    }
