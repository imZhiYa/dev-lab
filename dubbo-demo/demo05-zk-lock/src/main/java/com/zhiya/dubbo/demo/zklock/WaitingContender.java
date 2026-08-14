package com.zhiya.dubbo.demo.zklock;

import java.time.Duration;

/**
 * 等待被"强杀持有者"占住的锁：记录持锁进程被 kill 后自己抢到锁的耗时
 * （持有者 ZK session 死亡 → 临时节点消失 → 竞争者抢到，即 session 语义发现窗口）。
 *
 * 用法：WaitingContender <zkHost:port> <lockName>
 */
public class WaitingContender {

    public static void main(String[] args) throws Exception {
        String zkAddr = args.length > 0 ? args[0] : "127.0.0.1:2181";
        String lockName = args.length > 1 ? args[1] : "order-stock";

        long t0 = System.nanoTime();
        System.out.println("CONTENDER: waiting for lock " + lockName + " ...");
        ZkDistributedLock lock = new ZkDistributedLock(zkAddr, lockName, 10000);
        System.out.println("CONTENDER: connection ready, node=" + lock.myNodePath());
        lock.lock();
        long waitedMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        System.out.println("CONTENDER: ACQUIRED after " + waitedMs + "ms of waiting");
        lock.unlock();
        lock.close();
    }
}
