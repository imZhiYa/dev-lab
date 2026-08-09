package demo13.aspect;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 横切面与 AOP（06 篇 Level 4 补充）：框架注解（@Transactional）在
 * protected/包可见方法上的真实行为实测
 *
 * 背景：自定义 @Aspect 切面（within/execution）可拦截 protected/包可见方法
 * （VisibilityApp 实测，CGLIB 可覆写）；但框架注解（@Transactional 等）是否
 * 同样生效取决于注解处理器对方法可见性的要求。
 *   - javap 反编译 spring-tx 6.1.14 AbstractFallbackTransactionAttributeSource：
 *     computeTransactionAttribute 里 allowPublicMethodsOnly() 为 true 时
 *     非 public 方法直接返回 null（注解被忽略）
 *   - 本实验用行为验证：protected/包可见方法标 @Transactional 抛异常，
 *     看是否回滚（insert 是否残留）
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + H2，本机）：
 *   [bean 类] ...TxService$$SpringCGLIB$$0
 *   [public 事务方法] 抛异常后 count=0（回滚 → 事务生效）
 *   [protected 事务方法] 抛异常后 count=0（回滚 → 事务生效！）
 *   [包可见事务方法] 抛异常后 count=0（回滚 → 事务生效！）
 *
 * 结论（与本 App 开发期预写的"残留 → 未生效"相反）：
 *   - Spring 6.1.14 的 AbstractFallbackTransactionAttributeSource.
 *     allowPublicMethodsOnly() 默认返回 false（javap 实证 iconst_0）
 *     → 事务注解读取不限制方法可见性
 *   - Boot 3 默认 CGLIB 代理可覆写 protected/包可见方法
 *   - 外部经代理调用 protected/包可见 @Transactional 方法 → 事务生效
 *   - 官方文档建议"只标 public"的真实原因：JDK 动态代理模式下非 public
 *     方法不可代理（接口无私有方法）；private/static/final 任何模式都不生效；
 *     且自调用（this.xxx）任何可见性都失效
 */
public class TxVisibilityApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class TxService {

        private final JdbcTemplate jdbc;

        TxService(DataSource ds) {
            this.jdbc = new JdbcTemplate(ds);
        }

        public int count() {
            Integer n = jdbc.queryForObject("select count(*) from tx_account", Integer.class);
            return n == null ? 0 : n;
        }

        public void createTable() {
            jdbc.execute("create table if not exists tx_account (id int primary key, name varchar(20))");
        }

        @Transactional
        public void doPublicTx(int id) {
            jdbc.update("insert into tx_account values (?, ?)", id, "pub-" + id);
            throw new RuntimeException("public 事务方法抛异常");
        }

        @Transactional
        protected void doProtectedTx(int id) {
            jdbc.update("insert into tx_account values (?, ?)", id, "prot-" + id);
            throw new RuntimeException("protected 事务方法抛异常");
        }

        @Transactional
        void doPackageTx(int id) {
            jdbc.update("insert into tx_account values (?, ?)", id, "pkg-" + id);
            throw new RuntimeException("包可见事务方法抛异常");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        TxService svc = ctx.getBean(TxService.class);
        svc.createTable();
        System.out.println("[bean 类] " + svc.getClass().getName());

        try {
            svc.doPublicTx(1);
        } catch (RuntimeException e) {
            System.out.println("[public 事务方法] 抛异常后 count=" + svc.count()
                    + "（回滚 → 事务生效）");
        }
        try {
            svc.doProtectedTx(2);
        } catch (RuntimeException e) {
            System.out.println("[protected 事务方法] 抛异常后 count=" + svc.count()
                    + "（回滚 → 事务生效）");
        }
        try {
            svc.doPackageTx(3);
        } catch (RuntimeException e) {
            System.out.println("[包可见事务方法] 抛异常后 count=" + svc.count()
                    + "（回滚 → 事务生效）");
        }
        ctx.close();
    }
}
