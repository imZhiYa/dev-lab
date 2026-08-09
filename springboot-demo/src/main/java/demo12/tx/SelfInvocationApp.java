package demo12.tx;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 事务与数据层（05 篇 Level 3）：自调用失效实测 + 修复
 *
 * 机制（spring-tx 6.1.14 + AOP 代理）：
 *   - 声明式事务靠代理：代理在方法调用前开事务。代理只拦截"从代理对象发出的调用"
 *   - this.method() 自调用 = 直接调目标对象方法，绕过代理 → @Transactional 不生效
 *     （本项目 PropagationApp 开发期真实踩坑：this.innerRequiresNew() 实测变成加入外层事务）
 *   - 修复本质：让"事务方法调用"显式经过代理对象。四种拿到代理的途径：
 *       ① @Autowired @Lazy self 注入自身代理（本类默认演示）
 *       ② context.getBean(Class) —— 从容器取出的 bean 本来就是代理（本类补充演示）
 *       ③ @EnableAspectJAutoProxy(exposeProxy=true) + AopContext.currentProxy()
 *          —— 在代理调用链内取"当前代理"（本类补充演示）
 *       ④ 接口化/跨 bean 调用（事务方法放到另一个 bean，天然走代理）
 *
 * 真实输出（JDK 21.0.11 + spring-boot 3.3.5 + H2 内存库，本机）：
 *   [失败版 this.save()] 自调用 @Transactional 方法（内部抛运行时异常）→ count=1（事务没生效！）
 *   [修复版 self.save()] 注入代理调用（内部抛运行时异常）→ count=1（回滚生效，失败版数据仍在）
 *   [修复版 ctx.getBean()] 容器取代理调用（内部抛运行时异常）→ count=1（回滚生效）
 *   [修复版 AopContext] 调用链内取当前代理（内部抛运行时异常）→ count=1（回滚生效）
 *   [链外 AopContext.currentProxy()] 抛 IllegalStateException（实测：调用链外 ThreadLocal 为空）
 *   [断言] ctx.getBean 两次 == true（单例：容器每次返回同一代理实例）
 *   [断言] 方法在 target 上执行：this 类 = ...UserService（纯目标类，字段注入就在它身上）
 *   [断言] 返回 this 却被换成代理 true（CglibAopProxy.processReturnType：retVal==target
 *          时替换为 proxy——"返回自身"的方法，调用方拿到的是代理，AOP 能力不丢；
 *          声明类实现 RawTargetAccess 可绕过）
 *   [断言] 代理运行时类型 != 目标类 true（proxy.getClass()=$$SpringCGLIB$$ 子类；
 *          原始类型只靠 AopUtils.getTargetClass）
 *
 * 代理结构（与 UnwrapApp 一致）：代理 $$1 内部持有独立 target（纯 UserService 实例）；
 * 字段注入发生在 target 上（代理实例字段为 null）；方法调用被拦截器转发到 target 执行。
 *
 * 自调用为什么"没生效"而不是"报错"：代理是运行时装配的，方法仍能执行，
 * 只是少了事务环绕——这类失效是静默的，必须靠测试/回滚验证才能发现。
 */
public class SelfInvocationApp {

    @SpringBootApplication
    @EnableAspectJAutoProxy(exposeProxy = true)
    static class BootConfig {
    }

    @Service
    static class UserService {

        private final JdbcTemplate jdbc;
        @Autowired
        @Lazy
        private UserService self;
        @Autowired
        private org.springframework.context.ApplicationContext context;

        UserService(DataSource ds) {
            this.jdbc = new JdbcTemplate(ds);
        }

        public void createTable() {
            jdbc.execute("create table if not exists user_account (id int primary key, name varchar(20))");
        }

        public int count() {
            Integer n = jdbc.queryForObject("select count(*) from user_account", Integer.class);
            return n == null ? 0 : n;
        }

        @Transactional
        public void transactionalSaveAndThrow(int id) {
            System.out.println("[打点] transactionalSaveAndThrow 事务激活="
                    + org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("insert into user_account values (?, ?)", id, "user-" + id);
            throw new RuntimeException("事务方法内部抛异常");
        }

        public void selfCallFailure(int id) {
            this.transactionalSaveAndThrow(id);
        }

        public void selfCallFixed(int id) {
            System.out.println("[诊断] 目标实例内 this.self 类 = " + this.self.getClass().getName()
                    + "；AOP 代理 = " + org.springframework.aop.support.AopUtils.isAopProxy(this.self));
            self.transactionalSaveAndThrow(id);
        }

        public void selfCallFixedByContext(int id) {
            UserService proxy = context.getBean(UserService.class);
            System.out.println("[诊断] context.getBean 类 = " + proxy.getClass().getName()
                    + "；AOP 代理 = " + AopUtils.isAopProxy(proxy));
            proxy.transactionalSaveAndThrow(id);
        }

        public void selfCallFixedByAopContext(int id) {
            UserService proxy = (UserService) org.springframework.aop.framework.AopContext.currentProxy();
            System.out.println("[诊断] AopContext.currentProxy 类 = " + proxy.getClass().getName()
                    + "；AOP 代理 = " + AopUtils.isAopProxy(proxy));
            proxy.transactionalSaveAndThrow(id);
        }

        public UserService rawSelf() {
            System.out.println("[断言] 方法内 this 类 = " + this.getClass().getName()
                    + "（纯目标类：方法在 target 上执行，字段注入就在它身上）");
            return this;
        }
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();
        UserService svc = ctx.getBean(UserService.class);
        svc.createTable();

        try {
            svc.selfCallFailure(1);
        } catch (RuntimeException e) {
            System.out.println("[失败版 this.save()] 自调用 @Transactional 方法（内部抛异常）→ count="
                    + svc.count() + "（事务没生效！数据残留）");
        }

        try {
            svc.selfCallFixed(2);
        } catch (RuntimeException e) {
            System.out.println("[修复版 self.save()] 注入代理调用（内部抛异常）→ count="
                    + svc.count() + "（回滚生效）");
        }

        try {
            svc.selfCallFixedByContext(3);
        } catch (RuntimeException e) {
            System.out.println("[修复版 ctx.getBean()] 容器取代理调用（内部抛异常）→ count="
                    + svc.count() + "（回滚生效）");
        }
        try {
            svc.selfCallFixedByAopContext(4);
        } catch (RuntimeException e) {
            System.out.println("[修复版 AopContext] 调用链内取当前代理（内部抛异常）→ count="
                    + svc.count() + "（回滚生效）");
        }

        UserService proxy1 = ctx.getBean(UserService.class);
        UserService proxy2 = ctx.getBean(UserService.class);
        System.out.println("[断言] ctx.getBean 两次 == : " + (proxy1 == proxy2) + "（单例：容器每次返回同一代理实例）");
        UserService raw = proxy1.rawSelf();
        System.out.println("[断言] 返回 this 却被换成代理 : " + (raw == proxy1)
                + "（processReturnType：retVal==target 时替换为 proxy——调用方拿到的仍是代理，AOP 能力不丢）");
        System.out.println("[断言] 代理运行时类型 != 目标类 : " + (proxy1.getClass() != UserService.class)
                + "（proxy.getClass() = " + proxy1.getClass().getSimpleName()
                + "，原始类型只靠 AopUtils.getTargetClass 获取 = " + AopUtils.getTargetClass(proxy1).getSimpleName() + "）");

        try {
            org.springframework.aop.framework.AopContext.currentProxy();
            System.out.println("[链外 AopContext.currentProxy()] 竟然没抛异常");
        } catch (IllegalStateException e) {
            System.out.println("[链外 AopContext.currentProxy()] 抛 IllegalStateException：" + e.getMessage());
        }
        ctx.close();
    }
}
