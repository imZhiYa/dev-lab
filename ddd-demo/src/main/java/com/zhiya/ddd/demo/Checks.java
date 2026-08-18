package com.zhiya.ddd.demo;

/**
 * 实验断言助手：所有 EX 统一输出格式，供 scripts/verify-ddd-demos.sh 用 assert_contains 公审。
 * 输出行格式（不要改）：
 *   Ex0X 结果: 通过 N / 失败 0
 */
public final class Checks {

    private int passed = 0;
    private int failed = 0;

    public void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    public void checkEq(String name, Object expected, Object actual) {
        check(name + " (期望=" + expected + ", 实际=" + actual + ")", java.util.Objects.equals(expected, actual));
    }

    /** 实验入口统一收尾：失败非零退出码，让 CI 可以直接把退出码当信号。 */
    public void summary(String exName) {
        System.out.println(exName + " 结果: 通过 " + passed + " / 失败 " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}