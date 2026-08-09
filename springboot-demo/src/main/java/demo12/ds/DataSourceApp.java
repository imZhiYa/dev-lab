package demo12.ds;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;

/**
 * 事务与数据层（05 篇 Level 1）：DataSource 自动配置的分支选择实测
 *
 * 机制（Boot 3.3.5，spring-jdbc 在 classpath 时）：
 *   - DataSourceAutoConfiguration 是条件链驱动的自动配置：
 *       a) classpath 有连接池（HikariCP）→ PooledDataSourceConfiguration
 *          → HikariDataSource（默认连接池）
 *       b) 无连接池，但有内嵌库（h2/derby/hsqldb）→ EmbeddedDataSourceConfiguration
 *          → 内嵌数据源
 *   - 双跑法：加/去 HikariCP 一个 jar，DataSource 类型就变（与 04 篇 WebFlux
 *     双跑法同一思想：classpath 决定自动配置分支）
 *
 * 双跑法命令：
 *   1) 全 lib（含 HikariCP）→ HikariDataSource：
 *      java -cp "out:$(find lib -name '*.jar' | tr '\n' ':')" demo12.ds.DataSourceApp
 *   2) 去掉 HikariCP → 内嵌数据源（EmbeddedDataSource）：
 *      java -cp "out:$(find lib -name '*.jar' ! -name 'HikariCP*' | tr '\n' ':')" demo12.ds.DataSourceApp
 *
 * 真实输出 1（全 lib，Hikari 分支，JDK 21.0.11 + spring-boot 3.3.5）：
 *   [数据源] com.zaxxer.hikari.HikariDataSource
 *   [查询] count=0（H2 内存库，启动时为空）
 *
 * 真实输出 2（无 Hikari，内嵌分支）：
 *   [数据源] org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseFactory$EmbeddedDataSourceProxy
 *   （内嵌数据源代理，JdbcTemplate 同样可用）
 */
public class DataSourceApp {

    @SpringBootApplication
    static class BootConfig {
    }

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.springframework").setLevel(java.util.logging.Level.OFF);

        SpringApplication app = new SpringApplication(BootConfig.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext ctx = app.run();

        DataSource ds = ctx.getBean(DataSource.class);
        System.out.println("[数据源] " + ds.getClass().getName());
        ctx.close();
    }
}
