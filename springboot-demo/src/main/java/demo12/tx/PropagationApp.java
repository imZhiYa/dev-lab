package demo12.tx;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 事务与数据层（05 篇 Level 3）：传播行为 REQUIRED vs REQUIRES_NEW 实测
 *
 * 机制（spring-tx 6.1.14）：
 *   - REQUIRED（默认）：外层已有事务则加入；没有则新建。
 *     内层加入外层 = 同一事务 → 外层回滚，内层写入一起回滚
 *   - REQUIRES_NEW：无论外层是否已有事务，都挂起外层、新建独立事务。
 *     内层独立提交 → 外层回滚，内层写入保留
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + H2 内存库，本机）：
 *   [场景A REQUIRED] 外层调内层（REQUIRED）→ 同一事务
 *     外层抛运行时异常 → 内层 count=0（内层写入被外层回滚一起撤销）
 *   [场景B REQUIRES_NEW] 外层调内层（REQUIRES_NEW）→ 独立事务
 *     外层抛运行时异常 → 内层 count=1（内层已独立提交，保留）
 *
 * 生产要点：REQUIRES_NEW 用于"无论如何都要落库"的操作（审计日志、
 * 记账流水），代价是挂起/恢复外层事务（连接切换），多一次提交。
 */
public class PropagationApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class LedgerService {

        private final JdbcTemplate jdbc;

        LedgerService(DataSource ds) {
            this.jdbc = new JdbcTemplate(ds);
        }

        public void createTable() {
            jdbc.execute("create table if not exists ledger (id int primary key, name varchar(20))");
        }

        public int count() {
            Integer n = jdbc.queryForObject("select count(*) from ledger", Integer.class);
            return n == null ? 0 : n;
        }

        @Transactional
        public void innerRequired(String tag) {
            jdbc.update("insert into ledger values (?, ?)", 1, tag);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void innerRequiresNew(String tag) {
            jdbc.update("insert into ledger values (?, ?)", 2, tag);
        }
    }

    @Service
    static class OuterService {

        private final LedgerService ledger;

        OuterService(LedgerService ledger) {
            this.ledger = ledger;
        }

        @Transactional
        public void outerRequiredCallsInnerRequired(String tag) {
            ledger.innerRequired(tag);
            throw new RuntimeException("外层（REQUIRED 场景）抛异常");
        }

        @Transactional
        public void outerRequiredCallsInnerRequiresNew(String tag) {
            ledger.innerRequiresNew(tag);
            throw new RuntimeException("外层（REQUIRES_NEW 场景）抛异常");
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();
        LedgerService svc = ctx.getBean(LedgerService.class);
        OuterService outer = ctx.getBean(OuterService.class);
        svc.createTable();

        try {
            outer.outerRequiredCallsInnerRequired("a");
        } catch (RuntimeException e) {
            System.out.println("[场景A REQUIRED] 外层调内层（REQUIRED）→ 同一事务，外层抛运行时异常"
                    + " → 内层 count=" + svc.count() + "（内层写入被外层回滚一起撤销）");
        }

        try {
            outer.outerRequiredCallsInnerRequiresNew("b");
        } catch (RuntimeException e) {
            System.out.println("[场景B REQUIRES_NEW] 外层调内层（REQUIRES_NEW）→ 独立事务，外层抛运行时异常"
                    + " → 内层 count=" + svc.count() + "（内层已独立提交，保留）");
        }
        ctx.close();
    }
}
