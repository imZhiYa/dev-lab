package demo14.triage;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * 生产实践（07 篇 Level 4 急诊检查单）：复现"重构引发的 @Transactional 静默失效"
 *
 * 事故场景（教学推演，非真实事故记录）：
 *   重构前：@Transactional public pay() { 逻辑A; 逻辑B; }   —— 事务生效
 *   重构后：public pay() { 逻辑A; applyTx(); }
 *           @Transactional private applyTx() { 逻辑B; }    —— 事务静默失效！
 *
 * 双重失效机制（06 篇 TxVisibilityApp + 05 篇 SelfInvocationApp 推导，此处合流）：
 *   1) private 修饰符：代理（CGLIB 子类）无法覆写 private 方法，任何模式都拦截不了
 *   2) 自调用：pay() 内部 this.applyTx() 走目标对象，不经过代理
 *   → 两个失效叠加，事务拦截器从未介入，异常后数据残留，无任何报错
 *
 * 对照（GoodService.pay()）：public 方法直接标 @Transactional → 事务生效
 *
 * 急诊检查单（生产排查顺序，每项对应 00-06 的一个机制指纹）：
 *   检查1 bean 类名（00 篇代理创建 + 06 篇代理类名）：$$SpringCGLIB$$ → 是代理，排除"没代理"
 *   检查2 事务管理器 bean（03 篇自动装配 + 05 篇）：JdbcTransactionManager → 装配正常
 *   检查3 事务 advisor 数（06 篇 findCandidateAdvisors）：>0 → 基础设施 advisor 在
 *   检查4 事务激活打点（05 篇）：bad 路径 false / good 路径 true → 指纹差异
 *   检查5 afterCompletion 打点：good 路径 ROLLED_BACK（拦截器触发）；bad 路径拦截器从未介入
 *     （事务激活=false 已直接证明；且无事务时 registerSynchronization 会抛 IllegalStateException，
 *       这也是生产知识：同步回调只允许在事务内注册）
 *   检查6 count 残留对比：bad=1（残留！）/ good=0（回滚）→ 后果量化
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + H2 内存库，本机）：
 *   [检查1] bean 类 = demo14.triage.TriageApp$PaymentService$$SpringCGLIB$$0（代理 ✓）
 *   [检查2] 事务管理器 = org.springframework.jdbc.support.JdbcTransactionManager（✓）
 *   [检查3] 事务 advisor 数 = 3（基础设施 advisor；demo14 包无自定义切面）
 *   [检查4-bad] public pay() 调 private @Transactional applyTx()：事务激活=false ← 失效指纹
 *   [检查4-good] public @Transactional payGood()：事务激活=true
 *   [检查6] bad 路径抛异常后 count=1（残留 1 行 → 事务未生效！）
 *   [检查5-good] afterCompletion=ROLLED_BACK（回滚）
 *   [检查6] good 路径抛异常后 count=1（仍是 bad 残留的 1 行，good 自己的插入已回滚 → 事务生效）
 */
public class TriageApp {

    @SpringBootApplication
    static class BootConfig {
    }

    @Service
    static class PaymentService {

        private final JdbcTemplate jdbc;

        PaymentService(DataSource ds) {
            this.jdbc = new JdbcTemplate(ds);
        }

        public int count() {
            Integer n = jdbc.queryForObject("select count(*) from payment", Integer.class);
            return n == null ? 0 : n;
        }

        public void createTable() {
            jdbc.execute("create table if not exists payment (id int primary key, tag varchar(20))");
        }

        /**
         * 事故方法（重构后形态）：事务注解被"搬"进了 private helper
         */
        public void pay(int id) {
            System.out.println("[检查4-bad] public pay() 调 private @Transactional applyTx()：事务激活="
                    + TransactionSynchronizationManager.isActualTransactionActive());
            applyTx(id);
        }

        /**
         * 失效的 private helper：注解被读取却永远不会生效（private 不可覆写 + 自调用双失效）
         * 注意：无事务时 registerSynchronization 会抛 IllegalStateException——这里不做同步打点，
         * 因为事务拦截器根本没介入，插入在 autocommit 下立即生效，随后异常也无事务可回滚
         */
        @Transactional
        private void applyTx(int id) {
            jdbc.update("insert into payment values (?, ?)", id, "bad");
            throw new RuntimeException("模拟业务异常：余额不足");
        }

        /**
         * 对照：事务注解留在 public 方法上 → 正常生效
         */
        @Transactional
        public void payGood(int id) {
            System.out.println("[检查4-good] public @Transactional payGood()：事务激活="
                    + TransactionSynchronizationManager.isActualTransactionActive());
            registerSync("good");
            jdbc.update("insert into payment values (?, ?)", id, "good");
            throw new RuntimeException("模拟业务异常：余额不足");
        }

        private void registerSync(String tag) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    System.out.println("[检查5-" + tag + "] afterCompletion="
                            + (status == TransactionSynchronization.STATUS_COMMITTED
                                    ? "COMMITTED（提交）" : "ROLLED_BACK（回滚）"));
                }
            });
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        System.out.println("[检查1] bean 类 = " + ctx.getBean(PaymentService.class).getClass().getName()
                + "（代理 " + (AopUtils.isCglibProxy(ctx.getBean(PaymentService.class)) ? "✓ CGLIB" : "✗ 无代理") + "）");
        System.out.println("[检查2] 事务管理器 = "
                + ctx.getBean(org.springframework.transaction.PlatformTransactionManager.class).getClass().getName());
        Object creator = ctx.getBean("org.springframework.aop.config.internalAutoProxyCreator");
        try {
            java.lang.reflect.Method m = org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator.class
                    .getDeclaredMethod("findCandidateAdvisors");
            m.setAccessible(true);
            java.util.List<?> advisors = (java.util.List<?>) m.invoke(creator);
            System.out.println("[检查3] 事务 advisor 数 = " + advisors.size() + "（基础设施 advisor）");
        } catch (ReflectiveOperationException e) {
            System.out.println("[检查3] 反射获取 advisor 失败: " + e);
        }

        PaymentService svc = ctx.getBean(PaymentService.class);
        svc.createTable();

        try {
            svc.pay(1);
        } catch (RuntimeException e) {
            System.out.println("[检查6] bad 路径抛异常后 count=" + svc.count()
                    + "（残留 1 行 → 事务未生效！private 注解 + 自调用双重失效）");
        }

        try {
            svc.payGood(2);
        } catch (RuntimeException e) {
            System.out.println("[检查6] good 路径抛异常后 count=" + svc.count()
                    + "（仍是 bad 残留的 1 行，good 自己的插入已回滚 → 事务生效）");
        }
        ctx.close();
    }
}
