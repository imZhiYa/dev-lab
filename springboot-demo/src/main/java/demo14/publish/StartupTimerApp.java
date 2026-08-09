package demo14.publish;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/**
 * 生产实践（07 篇 Level 7 发布性能）：启动耗时指纹测量（本机演示，非生产 benchmark）
 *
 * 背景：发布/回滚慢（如 10 分钟）的本质 = 发布流程节奏 × 应用启动速度。
 * 启动慢先测后改：这一节的"体检"手段（04 篇 RunTraceApp 的启动序列方法延伸）：
 *   - 跑法1 默认：总耗时 + refresh 耗时
 *   - 跑法2 lazy：-Dspring.main.lazy-initialization=true → 初始化推迟，启动快；
 *     代价：首次 getBean 触发初始化（首请求慢 + 错误延迟暴露）——trade-off 实测
 *   - 跑法3 timeline：-Dspring.main.startup=timeline → ApplicationStartup 步骤树
 *     （Boot 3.0+ 内置；actuator 的 /actuator/startup 端点可输出同一数据）
 *
 * 慢 bean 设计：SlowInitService 构造器 sleep 500ms 模拟"重初始化"
 * （连接池建连/RPC 客户端握手/线程池启动等），用于放大 lazy 差异。
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + H2 内存库，本机多次运行，
 * 含 SlowInitService 500ms 初始化；仅机制演示，非生产 benchmark）：
 *   跑法1 默认（-Dspring.main.lazy-initialization 未设）：
 *     [计时] 应用对象构建=66~79ms；run（启动）=2197~2380ms
 *     [计时] 首次 getBean(SlowInitService)=0ms（启动期已初始化）
 *   跑法2 lazy（-Dspring.main.lazy-initialization=true）：
 *     [计时] 应用对象构建=66~102ms；run（启动）=1453~1646ms
 *     [计时] 首次 getBean(SlowInitService)=506~507ms（推迟初始化在首次访问时触发）
 *   跑法3 timeline（-Dspring.main.startup=timeline，程序化读取 BufferingApplicationStartup）：
 *     spring.boot.application.starting=17ms / environment-prepared=87ms /
 *     context.config-classes.parse=376ms / context.beandef-registry.post-process=382ms（前 10 步，
 *     注意并行步骤，耗时不可简单相加）
 *
 * 结论（机制演示，仅本机数据）：lazy 把初始化成本从"启动期"移到"首次访问期"——
 * 换来了启动快（本例省掉约 700ms 中的大头是 SlowInitService 的 500ms），
 * 代价是首请求慢与错误延迟暴露；是否使用取决于发布节奏 vs 首请求时延的取舍。
 */
public class StartupTimerApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class SlowInitService {
        SlowInitService() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        boolean trace = "timeline".equals(System.getProperty("spring.main.startup"));
        org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup buf = null;

        long t0 = System.currentTimeMillis();
        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        if (trace) {
            buf = new org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup(2048);
            app.setApplicationStartup(buf);
        }
        long t1 = System.currentTimeMillis();
        ConfigurableApplicationContext ctx = app.run();
        long t2 = System.currentTimeMillis();
        System.out.println("[计时] 应用对象构建=" + (t1 - t0) + "ms；run（启动）=" + (t2 - t1) + "ms");

        if (buf != null) {
            System.out.println("=== [startup] ApplicationStartup 步骤树（前 10 步）===");
            org.springframework.boot.context.metrics.buffering.StartupTimeline timeline = buf.getBufferedTimeline();
            int n = 0;
            for (org.springframework.boot.context.metrics.buffering.StartupTimeline.TimelineEvent e : timeline.getEvents()) {
                if (n++ >= 10) {
                    break;
                }
                System.out.println("[startup] " + e.getStartupStep().getName() + " = "
                        + e.getDuration().toMillis() + "ms");
            }
        }

        long t3 = System.currentTimeMillis();
        ctx.getBean(SlowInitService.class);
        long t4 = System.currentTimeMillis();
        System.out.println("[计时] 首次 getBean(SlowInitService)=" + (t4 - t3) + "ms"
                + (Boolean.parseBoolean(ctx.getEnvironment().getProperty("spring.main.lazy-initialization", "false"))
                        ? "（推迟初始化在此触发）" : "（启动期已初始化）"));

        System.out.println("[启动模式] lazy-initialization="
                + ctx.getEnvironment().getProperty("spring.main.lazy-initialization", "false"));
        ctx.close();
    }
}
