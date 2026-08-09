package demo19.jfr;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 启动慢排查 · 方法 4：FlightRecorderApplicationStartup（深度剖析）实测。
 *
 * 与 StartupProfilerApp 的差别：
 *   - 不暴露 actuator 端点，改为把启动步骤写进 JFR 事件流
 *   - ApplicationStartup 实现用 org.springframework.core.metrics.jfr.FlightRecorderApplicationStartup
 *     （spring-core 6.1.14 jar 实证：无参构造，start() 提交 jfr 事件）
 *   - 录制用 JDK 自带 jdk.jfr API（Recording），免 -XX:StartFlightRecording 参数
 *   - 启动完成后 stop → dump 到 out/startup-recording.jfr → 用 jdk.jfr.consumer.RecordingFile
 *     读回事件打印：事件类型名（实测确认）+ 耗时 Top 步骤
 *
 * 实测事实（6.1.14，本机）：
 *   - Spring 提交的事件类型名 = org.springframework.core.metrics.jfr.FlightRecorderStartupEvent
 *     （不是 jdk.ApplicationStartup；jfr print 用全限定名查询）
 *   - 事件带 stackTrace（记录 start() 发出点）+ tags（beanName= 等，逗号分隔字符串）
 *   - 与 JVM 事件同流：GC/类加载/NativeLibrary 事件混在一个 .jfr 里 → 交叉分析
 *     （jdk.GCPhaseParallel x1535、jdk.NativeLibrary x1049，与启动步骤同一时间轴）
 *
 * 深度剖析的增益（vs 端点快照）：
 *   - JFR 是 JDK 内建可观测设施，与 CPU/内存/IO 事件在同一时间轴，可交叉分析
 *   - 启动步骤事件与 GC/JIT/类加载事件叠加 → 回答"慢是业务 bean 还是 JVM 层面"
 *   - 生产可全程低开销录制，事后回放
 */
@SpringBootApplication
public class JfrStartupApp {

    public static void main(String[] args) throws Exception {
        Path jfrFile = Path.of("out/startup-recording.jfr");
        Files.deleteIfExists(jfrFile);

        // 方法 4：注册 FlightRecorderApplicationStartup（JFR 事件由 JDK 侧 Recording 承接）
        SpringApplication app = new SpringApplication(JfrStartupApp.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setApplicationStartup(new org.springframework.core.metrics.jfr.FlightRecorderApplicationStartup());
        app.setDefaultProperties(java.util.Map.of("server.port", "18091"));

        // JFR 录制：JDK API，包住整个启动过程
        try (Recording recording = new Recording(Configuration.getConfiguration("default"))) {
            recording.setName("spring-startup");
            recording.setDestination(jfrFile);
            recording.start();
            long t0 = System.currentTimeMillis();
            app.run(args);
            System.out.println("[总耗时] 启动完成: " + (System.currentTimeMillis() - t0) + " ms");
            recording.stop();
        }
        System.out.println("[JFR] 已落盘: " + jfrFile.toAbsolutePath());

        // 读回事件：先打印全部事件类型名（实测确认 Spring 提交了什么），再按 duration 排 Top
        List<RecordedEvent> all = new ArrayList<>();
        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                all.add(rf.readEvent());
            }
        }
        System.out.println("[JFR] 事件总数: " + all.size());
        System.out.println("[JFR] 事件类型分布（前 12）：");
        all.stream().map(e -> e.getEventType().getName())
                .collect(java.util.stream.Collectors.groupingBy(n -> n, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(12)
                .forEach(e -> System.out.println("  " + e.getKey() + " x" + e.getValue()));

        // 找出 Spring 启动步骤事件（类型名含 startup 或 ApplicationStartup），按 duration 排序
        List<RecordedEvent> startupEvents = all.stream()
                .filter(e -> e.getEventType().getName().toLowerCase().contains("startup"))
                .sorted(Comparator.comparingLong(e -> -e.getDuration().toMillis()))
                .limit(15)
                .toList();
        System.out.println("[JFR] Spring 启动步骤 Top 15（duration 单位毫秒）：");
        for (RecordedEvent e : startupEvents) {
            Object tags = e.getValue("tags");
            String tagStr = tags == null ? "" : String.valueOf(tags);
            System.out.printf("  %-45s %6d ms   %s%n",
                    e.getEventType().getName(),
                    e.getDuration().toMillis(),
                    tagStr.length() > 90 ? tagStr.substring(0, 90) + "..." : tagStr);
        }
        // web server 非 daemon 线程，跑完即退（demo 自包含）
        System.exit(0);
    }

    @Bean
    ApplicationRunner noteRunner() {
        return args -> System.out.println("[提示] 深度剖析交叉分析（事件名实测）："
                + "jfr print --events org.springframework.core.metrics.jfr.FlightRecorderStartupEvent out/startup-recording.jfr");
    }

    @Component
    static class FastBean {
    }

    /** 慢 bean：与 StartupProfilerApp 同款，验证两种 ApplicationStartup 都捕获到它 */
    @Component
    static class SlowBean {
        SlowBean() {
            long t0 = System.currentTimeMillis();
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[SlowBean] 构造完成，耗时 " + (System.currentTimeMillis() - t0) + " ms（模拟连接池初始化）");
        }
    }
}
