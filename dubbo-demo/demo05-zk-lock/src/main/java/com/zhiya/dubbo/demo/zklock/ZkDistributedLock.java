package com.zhiya.dubbo.demo.zklock;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * ZooKeeper 分布式锁：临时顺序节点 + 监听前驱。
 *
 * 算法（ZK 锁标准 recipe）：
 *   acquire：
 *     1. 在锁路径下创建 EPHEMERAL_SEQUENTIAL 子节点，如 /e04-locks/my-lock/_c_0000000007
 *     2. list 子节点；若我的序号最小 → 抢锁成功
 *     3. 否则监听前驱（我的序号 - 1），等它消失再抢
 *   release：删除我的节点
 *
 * 关键语义（04 篇 Level 4）：
 *   - EPHEMERAL：节点生命周期 = 客户端 session。客户端死亡（进程被杀、分区超过
 *     session 超时）节点自动消失 → 锁无代码自动释放。
 *   - SEQUENTIAL：计数给出全竞争者的全序（ZAB 全序广播），所以 recipe 能靠
 *     比较序号判断"谁是持有者"。
 *   - 只监听前驱：公平队列（FIFO），无惊群效应。
 */
public class ZkDistributedLock implements AutoCloseable {

    public static final String LOCK_ROOT = "/e04-locks";
    private static final String PREFIX = "_c_";

    private final ZooKeeper zk;
    private final String lockPath;
    private final String myNodePath;
    private final long sessionTimeoutMs;

    private volatile boolean locked = false;

    public ZkDistributedLock(String connectString, String lockName, long sessionTimeoutMs) throws IOException, KeeperException, InterruptedException {
        this.sessionTimeoutMs = sessionTimeoutMs;
        CountDownLatch connected = new CountDownLatch(1);
        this.zk = new ZooKeeper(connectString, (int) sessionTimeoutMs, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        if (!connected.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("ZK connect timeout");
        }
        ensureRoot(lockName);
        this.lockPath = LOCK_ROOT + "/" + lockName;
        String created = zk.create(lockPath + "/" + PREFIX,
                new byte[0],
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
        this.myNodePath = created;
    }

    private void ensureRoot(String lockName) throws KeeperException, InterruptedException {
        try {
            zk.create(LOCK_ROOT, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException ignored) {
        }
        try {
            zk.create(LOCK_ROOT + "/" + lockName, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (KeeperException.NodeExistsException ignored) {
        }
    }

    /** Block until the lock is acquired. */
    public void lock() throws KeeperException, InterruptedException {
        while (true) {
            List<String> children = zk.getChildren(lockPath, false);
            java.util.Collections.sort(children);
            int mySeq = seqOf(myNodePath);
            int myIndex = indexOf(children, mySeq);
            if (myIndex == 0) {
                locked = true;
                return;
            }
            String predecessor = children.get(myIndex - 1);
            CountDownLatch gone = new CountDownLatch(1);
            try {
                zk.exists(lockPath + "/" + predecessor, event -> {
                    if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
                        gone.countDown();
                    }
                });
            } catch (KeeperException.NoNodeException e) {
                // 前驱已消失，重试
                continue;
            }
            gone.await();
        }
    }

    /** Try to acquire without blocking. Returns true if this caller is the current holder. */
    public boolean tryLock() throws KeeperException, InterruptedException {
        List<String> children = zk.getChildren(lockPath, false);
        java.util.Collections.sort(children);
        int myIndex = indexOf(children, seqOf(myNodePath));
        locked = myIndex == 0;
        return locked;
    }

    public void unlock() throws KeeperException, InterruptedException {
        if (locked) {
            zk.delete(myNodePath, -1);
            locked = false;
        }
    }

    public String myNodePath() {
        return myNodePath;
    }

    public long sessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    private static int seqOf(String nodePath) {
        String name = nodePath.substring(nodePath.lastIndexOf('/') + 1);
        return Integer.parseInt(name.substring(PREFIX.length()));
    }

    private static int indexOf(List<String> children, int mySeq) {
        String myName = PREFIX + String.format("%010d", mySeq);
        return children.indexOf(myName);
    }

    @Override
    public void close() throws Exception {
        try {
            unlock();
        } catch (Exception ignored) {
        }
        zk.close();
    }
}
