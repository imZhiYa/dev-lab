package com.zhiya.aqs;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

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
