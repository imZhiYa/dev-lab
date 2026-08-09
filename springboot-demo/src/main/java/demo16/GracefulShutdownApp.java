package demo16;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优雅停机对照实验（04 章/14 章文档语义 vs 3.3.5 实测，2026-08-06 本机）：
 *
 * immediate（默认）：
 *   SIGTERM → ContextClosedEvent → 进程约 1s 内退出；
 *   新请求 000（连接失败）；在途 6s 请求被中断（curl 退出码 52 空回复）。
 *
 * graceful（--server.shutdown=graceful）：
 *   SIGTERM → Tomcat 停止接收新请求（新请求 000）；
 *   在途请求排空完成（SIGTERM 后约 5010ms，即剩余 5s 跑满）；
 *   排空完成后 ContextClosedEvent → 进程退出（约 5229ms）。
 *
 * --server.shutdown-timeout=100ms/2s 均不生效（实测 3.3.5）：
 *   3.3.5 的 Shutdown 枚举只有 GRACEFUL/IMMEDIATE（javap 实证），
 *   Tomcat GracefulShutdown 用 CountDownLatch.await() 无参无限等待（字节码实证），
 *   shutdown-timeout 是 3.4+ 属性（Boot 4.1 文档语义），宽松绑定静默忽略。
 *
 * 用法：
 *   java demo16.GracefulShutdownApp --server.port=18080 [--server.shutdown=graceful]
 */
@SpringBootApplication
public class GracefulShutdownApp {

    @RestController
    static class Ctrl {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }

        @GetMapping("/slow")
        String slow() throws InterruptedException {
            Thread.sleep(6000);
            return "slow-done";
        }
    }

    @EventListener(ContextClosedEvent.class)
    void closing() {
        System.out.println("[CLOSE] ContextClosedEvent 触发，停机开始");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PID=" + ProcessHandle.current().pid());
        SpringApplication.run(GracefulShutdownApp.class, args);
        System.out.println("[MAIN] run() 返回（正常停机路径完成）");
    }
}
