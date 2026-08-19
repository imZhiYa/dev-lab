-- 系列基线：分库分表 4 库 × 2 表（8 个物理分片，库 0~3 各持 2 片）
-- ShardingSphere 表分片模型：每个物理库同构（order_0/order_1）
-- 逻辑片号 = 库号 × 2 + 表号（片 0~7，与知识库 shard 系列基线 order_0~order_7 一一对应）
-- 分片键 order_id：order_id % 8 = 逻辑片号 → 库 = 片号/2、表 = 片号%2
-- 注意：shard_ds0~3 口令 'shard' 仅为本地 lab 沙箱观测用户（general_log 路由目标识别用），非任何真实环境凭据

CREATE DATABASE IF NOT EXISTS ds0 DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS ds1 DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS ds2 DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS ds3 DEFAULT CHARSET utf8mb4;

-- ds0：逻辑片 0（order_0）、1（order_1）
USE ds0;
CREATE TABLE order_0 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_1 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ds1：逻辑片 2、3
USE ds1;
CREATE TABLE order_0 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_1 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ds2：逻辑片 4、5
USE ds2;
CREATE TABLE order_0 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_1 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ds3：逻辑片 6、7
USE ds3;
CREATE TABLE order_0 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE order_1 (
    order_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    amount     DECIMAL(12,2) NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户表（EX-05 跨片 JOIN 用：按 user_id % 2 分片，2 片落在 ds0/ds1，与 order 无绑定关系）
USE ds0;
CREATE TABLE user_0 (
    user_id  BIGINT PRIMARY KEY,
    nickname VARCHAR(64) NOT NULL
);
CREATE TABLE user_1 (
    user_id  BIGINT PRIMARY KEY,
    nickname VARCHAR(64) NOT NULL
);

USE ds1;
CREATE TABLE user_0 (
    user_id  BIGINT PRIMARY KEY,
    nickname VARCHAR(64) NOT NULL
);
CREATE TABLE user_1 (
    user_id  BIGINT PRIMARY KEY,
    nickname VARCHAR(64) NOT NULL
);
-- EX-02/07 观测用：每库一个独立 MySQL 用户（general_log 的 user_host 可区分路由目标库）
CREATE USER 'shard_ds0'@'%' IDENTIFIED BY 'shard';
CREATE USER 'shard_ds1'@'%' IDENTIFIED BY 'shard';
CREATE USER 'shard_ds2'@'%' IDENTIFIED BY 'shard';
CREATE USER 'shard_ds3'@'%' IDENTIFIED BY 'shard';
GRANT ALL PRIVILEGES ON ds0.* TO 'shard_ds0'@'%';
GRANT ALL PRIVILEGES ON ds1.* TO 'shard_ds1'@'%';
GRANT ALL PRIVILEGES ON ds2.* TO 'shard_ds2'@'%';
GRANT ALL PRIVILEGES ON ds3.* TO 'shard_ds3'@'%';
FLUSH PRIVILEGES;
