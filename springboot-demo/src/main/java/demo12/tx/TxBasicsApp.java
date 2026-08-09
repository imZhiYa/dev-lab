package demo12.tx;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

/**
 * 事务与数据层（05 篇 Level 3）：@Transactional 的提交/回滚语义实测
 *
 * 机制（spring-tx 6.1.14 + spring-boot 3.3.5）：
 *   - 声明式事务 = AOP 代理（00 篇的四层创建链）：@Transactional 方法通过代理
 *     进入事务；默认回滚规则 = 只回滚 RuntimeException/Error（不回滚检查异常）
 *   - 三种场景实测：
 *     1) 正常运行 → 提交
 *     2) 抛 RuntimeException → 回滚（默认规则）
 *     3) 抛检查异常（Exception）→ 默认不回滚！
 *     4) rollbackFor = Exception.class → 检查异常也回滚
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + H2 内存库，本机）：
 *   [场景1 正常运行] insert 2 行 → count=2（提交）
 *   [场景2 运行时异常] insert 2 行后抛 RuntimeException → count=2（回滚，只留场景1数据）
 *   [场景3 检查异常] insert 2 行后抛 Exception → count=4（默认不回滚，场景3数据保留）
 *   [场景4 rollbackFor] insert 2 行后抛 Exception → count=4（rollbackFor 生效，回滚）
 *
 * 意外发现（设计测试时的真实踩坑）：固定主键 1/2 会在场景 1 提交后撞主键，
 * 抛 DuplicateKeyException（DataAccessException 体系 = 运行时异常）→ 默认回滚——
 * 这本身是生产知识点：唯一键冲突会让整个事务回滚（不是只有业务异常才回滚）
 *
 * 为什么默认只回滚运行时异常：检查异常通常表示"业务可以处理的预期错误"
 * （如余额不足），此时业务可能希望保留部分写入；运行时异常表示"程序错误"，
 * 必须原子回滚。规范如此（Specification），需要改变时显式 rollbackFor。
 */
public class TxBasicsApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class AccountService {

        private final JdbcTemplate jdbc;

        AccountService(DataSource ds) {
            this.jdbc = new JdbcTemplate(ds);
        }

        public int count() {
            Integer n = jdbc.queryForObject("select count(*) from account", Integer.class);
            return n == null ? 0 : n;
        }

        public void createTable() {
            jdbc.execute("create table if not exists account (id int primary key, name varchar(20))");
        }

        @Transactional
        public void insertTwo(String tag, int baseId) {
            System.out.println("[事务] insertTwo 事务激活="
                    + org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            System.out.println("[连接] DataSource 类=" + jdbc.getDataSource().getClass().getName());
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            System.out.println("[同步] afterCompletion="
                                    + (status == org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED
                                            ? "COMMITTED（提交）" : "ROLLED_BACK（回滚）"));
                        }
                    });
            jdbc.update("insert into account values (?, ?)", baseId, tag + "-a");
            jdbc.update("insert into account values (?, ?)", baseId + 1, tag + "-b");
        }

        @Transactional
        public void insertTwoThenRuntimeException(String tag, int baseId) {
            insertTwo(tag, baseId);
            throw new RuntimeException("模拟运行时异常");
        }

        @Transactional
        public void insertTwoThenCheckedException(String tag, int baseId) throws Exception {
            insertTwo(tag, baseId);
            throw new Exception("模拟检查异常");
        }

        @Transactional(rollbackFor = Exception.class)
        public void insertTwoThenCheckedExceptionRollback(String tag, int baseId) throws Exception {
            insertTwo(tag, baseId);
            throw new Exception("模拟检查异常（rollbackFor 覆盖）");
        }
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();
        System.out.println("[诊断] PlatformTransactionManager bean = "
                + ctx.getBean(org.springframework.transaction.PlatformTransactionManager.class).getClass().getName());
        System.out.println("[诊断] AccountService 是否代理 = "
                + org.springframework.aop.support.AopUtils.isAopProxy(ctx.getBean(AccountService.class)));
        AccountService svc = ctx.getBean(AccountService.class);
        svc.createTable();

        svc.insertTwo("c1", 100);
        System.out.println("[场景1 正常运行] insert 2 行 → count=" + svc.count() + "（提交）");
        svc.count();

        try {
            svc.insertTwoThenRuntimeException("c2", 200);
        } catch (RuntimeException e) {
            System.out.println("[场景2 运行时异常] insert 2 行后抛 RuntimeException → count="
                    + svc.count() + "（回滚）");
        }

        try {
            svc.insertTwoThenCheckedException("c3", 300);
        } catch (Exception e) {
            System.out.println("[场景3 检查异常] main 捕获到: " + e.getClass().getName() + " → count="
                    + svc.count() + "（默认不回滚，数据保留）");
        }

        try {
            svc.insertTwoThenCheckedExceptionRollback("c4", 400);
        } catch (Exception e) {
            System.out.println("[场景4 rollbackFor] insert 2 行后抛 Exception → count="
                    + svc.count() + "（rollbackFor 生效，回滚）");
        }
        ctx.close();
    }
}
