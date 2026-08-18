package com.zhiya.ddd.demo;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * EX-03 架构边界守护 —— 把 ddd-02/03/04 的分层与上下文隔离编译进测试：
 *  1. domain 不依赖任何框架与适配器（Spring/JPA/Kafka/Redis 全禁）
 *  2. application 不依赖适配器（只通过 ports 说话）
 *  3. recommendation 上下文不依赖 strategy 上下文（上下文隔离）
 *  4. contracts（发布语言）只依赖 JDK（跨上下文契约必须纯净）
 *
 * 注意：ArchUnit 是构建期/测试期检查，不是运行时守卫 —— 违规代码永远编译不过测试，
 * 这是"纪律变成测试"（ddd-06 决策矩阵里的规则护栏）。
 */
public final class Ex03ArchitectureGuardDemo {

    public static void main(String[] args) {
        Checks c = new Checks();
        JavaClasses classes = new ClassFileImporter().importPackages("com.zhiya.ddd");

        ArchRule r1 = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..",
                        "org.apache.kafka..", "redis.clients..",
                        "com.zhiya.ddd.adapters..");
        c.check("domain 不依赖框架/适配器", rulePasses(r1, classes));

        ArchRule r2 = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapters..");
        c.check("application 不依赖适配器", rulePasses(r2, classes));

        ArchRule r3 = noClasses()
                .that().resideInAPackage("..domain.recommendation..")
                .should().dependOnClassesThat().resideInAPackage("..domain.strategy..");
        c.check("recommendation 上下文不依赖 strategy 上下文", rulePasses(r3, classes));

        ArchRule r4 = classes()
                .that().resideInAPackage("..contracts..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "javax..");
        c.check("contracts 只依赖 JDK", rulePasses(r4, classes));

        // domain.recommendation 依赖 contracts（PublishedStrategyView 是发布语言，允许），
        // 所以只对 domain.strategy 断言"绝对干净"：纯模型不依赖 contracts/ports
        ArchRule r5 = noClasses()
                .that().resideInAPackage("..domain.strategy..")
                .should().dependOnClassesThat().resideInAnyPackage("..contracts..", "..ports..");
        c.check("domain.strategy 不依赖 contracts/ports（纯模型）", rulePasses(r5, classes));

        c.summary("Ex03");
    }

    private static boolean rulePasses(ArchRule rule, JavaClasses classes) {
        try {
            rule.check(classes);
            return true;
        } catch (AssertionError e) {
            System.out.println("  ARCH-VIOLATION: " + firstLine(e.getMessage()));
            return false;
        }
    }

    private static String firstLine(String msg) {
        if (msg == null) {
            return "";
        }
        String[] lines = msg.split("\n");
        return lines.length > 0 ? lines[0] : "";
    }
}