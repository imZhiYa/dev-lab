package com.zhiya.sharding.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * ShardingSphere-JDBC 5.4.1 Java API 数据源工厂
 *
 * 系列基线（与知识库 shard 系列一致）：
 *   4 库（ds0~ds3）× 2 表（order_0~order_1）= 8 个物理分片 order_0~order_7
 *   分片键 order_id：order_id % 8 = 片号，库 = 片号 / 2，表 = 片号 % 2
 *   连接按库算（4 个数据源）、路由按片算（8 个物理表）
 *
 * 另配 user 表（user_db.user_0~1，按 user_id % 2 分表）——仅用于 EX-05 跨片 JOIN
 * （order 按 order_id 分片、user 按 user_id 分片，无绑定关系，JOIN 必然跨片）
 *
 * 版本行为（按 5.4.1 实测）：
 *   - 策略类在 ...api.config.strategy.sharding 包（Standard/Complex/Hint/None）
 *   - INLINE 是"算法"而非"策略"：AlgorithmConfiguration("INLINE", props(algorithm-expression))
 */
public final class ShardingDataSource {

    /** 连接参数均为本地 lab 沙箱凭据（scripts/compose-mysql.yml 启动，端口 3307 仅绑定本机），非任何真实环境 */
    public static final String JDBC_URL = "jdbc:mysql://127.0.0.1:3307";
    public static final String USER = "root";
    public static final String PASSWORD = "root";

    /** 每个物理库一个连接池；池大小固定为 4，便于 EX-07 观测连接预算 = 数据源数 × 池大小 */
    public static final int POOL_SIZE = 4;

    private ShardingDataSource() {
    }

    public static DataSource create() throws SQLException {
        return ShardingSphereDataSourceFactory.createDataSource(
                createDataSourceMap(),
                List.of(createShardingRule()),
                new Properties());
    }

    /** 4 个物理库 → 4 个 Hikari 连接池（连接按库算，不按片算） */
    private static Map<String, DataSource> createDataSourceMap() {
        Map<String, DataSource> map = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            map.put("ds" + i, hikari("ds" + i));
        }
        return map;
    }

    private static HikariDataSource hikari(String db) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(JDBC_URL + "/" + db);
        // 每库独立 MySQL 用户（general_log 的 user_host 可区分路由目标库，EX-02/07 观测用）
        cfg.setUsername("shard_" + db);
        cfg.setPassword("shard");
        cfg.setMaximumPoolSize(POOL_SIZE);
        cfg.setMinimumIdle(0); // 懒初始化，EX-07 便于观察"池大小是上限而非即时占用"
        cfg.setPoolName("pool-" + db);
        return new HikariDataSource(cfg);
    }

    private static ShardingRuleConfiguration createShardingRule() {
        ShardingRuleConfiguration rule = new ShardingRuleConfiguration();

        // order：8 个物理分片，库分片 = (order_id % 8) / 2，表分片 = order_id % 8 % 2
        ShardingTableRuleConfiguration order = new ShardingTableRuleConfiguration("order", "ds${0..3}.order_${0..1}");
        order.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "order_db"));
        order.setTableShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "order_tbl"));
        rule.getTables().add(order);

        // user：2 片落在 ds0/ds1（user_id 偶 → ds0.user_0，奇 → ds1.user_1），
        // 按 user_id 分片、与 order 无绑定关系（EX-05 跨片 JOIN 用）
        ShardingTableRuleConfiguration user = new ShardingTableRuleConfiguration("user", "ds${0..1}.user_${0..1}");
        user.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("user_id", "user_db_algo"));
        user.setTableShardingStrategy(new StandardShardingStrategyConfiguration("user_id", "user_tbl"));
        rule.getTables().add(user);

        // INLINE 算法（Groovy 表达式；MOD 语义 = order_id % 8 定位片号）
        rule.getShardingAlgorithms().put("order_db", inline("ds${(order_id % 8).intdiv(2)}"));
        rule.getShardingAlgorithms().put("order_tbl", inline("order_${order_id % 8 % 2}"));
        rule.getShardingAlgorithms().put("user_db_algo", inline("ds${user_id % 2}"));
        rule.getShardingAlgorithms().put("user_tbl", inline("user_${user_id % 2}"));

        return rule;
    }

    private static AlgorithmConfiguration inline(String expression) {
        Properties props = new Properties();
        props.setProperty("algorithm-expression", expression);
        // INLINE 默认禁止范围查询（allow-range-query-with-inline-sharding=false 时
        // WHERE order_id > ? 直接报 Unsupported SQL operation）——
        // EX-04 keyset 翻页需要范围路由，显式开启（开启后范围查询广播到全部分片过滤）
        props.setProperty("allow-range-query-with-inline-sharding", "true");
        return new AlgorithmConfiguration("INLINE", props);
    }
}