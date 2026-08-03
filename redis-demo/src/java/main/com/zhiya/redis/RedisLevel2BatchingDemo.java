package com.zhiya.redis.demo;

import com.zhiya.redis.support.RedisSupport;


import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Level 2 补充：Pipeline / MULTI / Lua 三种“打包”的区别。
 * <p>
 * 对应层级：Level 2。
 * 演示主题：三种批量执行的原子性与分支能力边界。
 * 验证目标：Pipeline 批内允许他客插队（无原子性）；MULTI/EXEC 执行屏障不插队但无分支；
 *           Lua 不插队且能做服务端分支——能力边界不互通（坑 9 的正面演示）。
 */
public final class RedisLevel2BatchingDemo {

    private RedisLevel2BatchingDemo() {
    }

    /** 一条命令的极小模型 */
    record Cmd(String client, String name, String key, int delta, boolean slow) {}

    /** 主线程事件循环：按到达顺序执行；slow 命令模拟耗时 */
    static Deque<Cmd> queue = new ArrayDeque<>();
    static int clock = 0;

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() {
        RedisSupport.banner("Level 2 补充 · 三种“打包”：Pipeline / MULTI / Lua",
                "它们不是三种写法，是三件不同的事");

        RedisSupport.sec("① Pipeline：省 RTT，不打包原子性");
        RedisSupport.print("  客户端 A 一口气发 4 条 INCR；客户端 B 同时发 1 条 INCR。");
        RedisSupport.print("  Pipeline 的语义 = 依次入队依次执行 → 中间完全可能被 B 插队：");
        queue.clear();
        queue.add(new Cmd("A", "INCR", "c", 1, false));
        queue.add(new Cmd("B", "INCR", "other", 1, false));   // B 插在中间
        queue.add(new Cmd("A", "INCR", "c", 1, false));
        queue.add(new Cmd("A", "INCR", "c", 1, false));
        queue.add(new Cmd("A", "INCR", "c", 1, false));
        drain("Pipeline（A 的批 4 条 + B 的 1 条）");

        RedisSupport.sec("② MULTI/EXEC：执行屏障，批内不插队；但没有分支");
        queue.clear();
        queue.add(new Cmd("A", "MULTI", null, 0, false));
        queue.add(new Cmd("A", "INCR", "c", 1, false));   // QUEUED
        queue.add(new Cmd("A", "INCR", "c", 1, false));   // QUEUED
        queue.add(new Cmd("B", "INCR", "c", 1, false));   // 想插队？
        queue.add(new Cmd("A", "EXEC", null, 0, false));
        drain("MULTI + 2×QUEUED + EXEC（B 的命令要等 EXEC 整批结束后才轮得到）");

        RedisSupport.print("  代价：QUEUED 阶段命令互相看不见执行结果 → 写不出“如果上一条返回 X 就跳过”");
        RedisSupport.print("  的分支；也没有回滚——执行期某条失败，其余照跑（自测 #11 / 勘误 #4）。");

        RedisSupport.sec("③ Lua / FUNCTION：不可插队 + 服务端分支");
        RedisSupport.print("  脚本：if GET stock >= n then DECRBY stock n return 1 else return 0");
        queue.clear();
        queue.add(new Cmd("A", "EVAL", "stock", 0, true));  // 脚本整体是一段“长命令”
        queue.add(new Cmd("B", "GET", "stock", 0, false));
        drain("EVAL(整段脚本) + B 的 GET —— 脚本执行期间 B 全程等待");

        RedisSupport.sec("④ 怎么选（决策表）");
        RedisSupport.table(
                new String[]{"需求", "选它", "注意"},
                List.of(new String[][]{
                        {"纯省 RTT", "Pipeline", "批内无原子性，集群下按槽分批"},
                        {"不插队、无分支", "MULTI/EXEC", "无回滚；入队期语法错=整组拒执"},
                        {"不插队 + 看中间结果定分支", "Lua / FUNCTION", "循环上界必须可声明可压测"},
                        {"“没人动过才提交”", "WATCH + MULTI", "冲突后整段重试（决策卡 6）"},
                        {"要 ACID 回滚", "回关系库", "Redis 事务不保证回滚（勘误 #4）"},
                }));
        RedisSupport.mantra("Pipeline 求原子、MULTI 求分支，都是张冠李戴");
    }

    private static void drain(String title) {
        System.out.println("  ◆ " + title);
        Deque<String> execBatch = new ArrayDeque<>();   // EXEC 批内命令
        String batchClient = null;                       // 正在排队/执行事务的客户端
        Deque<Cmd> holding = new ArrayDeque<>();         // EXEC 期间到达的他客命令，等批完再处理
        while (!queue.isEmpty() || !holding.isEmpty()) {
            Cmd c = !queue.isEmpty() ? queue.poll() : holding.poll();
            String prefix = String.format("    [t=%d]", clock++);
            if (batchClient != null) {
                if (c.client().equals(batchClient)) {
                    if ("EXEC".equals(c.name())) {
                        String batch = execBatch.stream()
                                .reduce("", (a, b) -> a.isEmpty() ? b : a + " → " + b);
                        System.out.println(prefix + " EXEC ── 整批连续执行（中途无人插队）：" + batch);
                        batchClient = null;
                    } else {
                        execBatch.add(c.name() + "(" + c.key() + "," + c.delta() + ")");   // QUEUED
                    }
                } else {
                    holding.addLast(c);   // 他客命令：被执行屏障挡在批外，等 EXEC 结束才轮到
                }
                continue;
            }
            switch (c.name()) {
                case "MULTI" -> {
                    batchClient = c.client();
                    execBatch.clear();
                    System.out.println(prefix + " MULTI  ← 开始排队（此后命令只 QUEUED）");
                }
                case "INCR" -> System.out.println(prefix + " INCR " + c.key() + "  +=1   [" + c.client() + "] 线性化点 #" + clock);
                case "GET"  -> System.out.println(prefix + " GET  " + c.key() + "   → 42");
                case "EVAL" -> System.out.println(prefix + " EVAL " + c.key() + "  [整段执行：读 stock → 判断 → 写回，B 在外面等着]");
                default -> System.out.println(prefix + " ?    " + c.name());
            }
        }
        System.out.println();
    }
}
