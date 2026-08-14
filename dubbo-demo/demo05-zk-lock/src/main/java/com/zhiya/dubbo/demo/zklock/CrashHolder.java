package com.zhiya.dubbo.demo.zklock;

import java.time.Duration;

/**
 * session 语义验证：本进程抢到锁后**只持不还**（sleep 占住）。
 * 配套脚本对它 kill -9：ZK session 死亡后临时节点必须自动消失，
 * 等待中的竞争者随后应抢到锁（发现窗口 = session 语义）。
 *
 * 用法：CrashHolder <zkHost:port> <lockName> <holdSeconds>
 */
public class CrashHolder {

    public static void main(String[] args) throws Exception {
        String zkAddr = args.length > 0 ? args[0] : "127.0.0.1:2181";
        String lockName = args.length > 1 ? args[1] : "order-stock";
        int holdSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 120;

        long t0 = System.nanoTime();
        ZkDistributedLock lock = new ZkDistributedLock(zkAddr, lockName, 10000);
        lock.lock();
        long acquireMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        System.out.println("HOLDER: LOCKED in " + acquireMs + "ms node=" + lock.myNodePath()
                + " sessionTimeout=" + lock.sessionTimeoutMs() + "ms"
                + " pid=" + ProcessHandle.current().pid());
        System.out.println("HOLDER: holding without releasing for " + holdSeconds + "s ...");
        System.out.println("HOLDER_READY");
        Thread.sleep(holdSeconds * 1000L);
        lock.unlock();
        lock.close();
        System.out.println("HOLDER: released gracefully");
    }
}
