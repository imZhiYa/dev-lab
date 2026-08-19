-- 分布式事务系列基线：下单请求 B 的三库拓扑（order_db 订单 / inventory_db 库存 / payment_db 支付）
-- 金额单位：分（BIGINT，避免浮点误差）；库存单位：件（BIGINT）
-- 表设计对应 articles/distributed-tx-02 L2~L3（冻结表 + 事务控制表）与 L4（Saga 状态机 + 补偿表）
-- 注意：root/root 仅为本地 lab 沙箱使用，非任何真实环境凭据

-- ============================================================
-- 订单库（发起方）：orders + outbox + saga 编排器状态机
-- ============================================================
CREATE DATABASE IF NOT EXISTS order_db DEFAULT CHARSET utf8mb4;
USE order_db;

CREATE TABLE orders (
    order_id   BIGINT PRIMARY KEY,
    status     VARCHAR(16) NOT NULL,          -- CREATED / CANCELLED
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Outbox：状态与待发事件同本地事务（articles/distributed-tx-00 L4 / DC-05，机制细节回链 ddd-05）
CREATE TABLE outbox (
    event_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    payload      VARCHAR(512) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING / PUBLISHED
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Saga 编排器状态机（articles/distributed-tx-02 L4：持久化 + 状态机 + 补偿表）
CREATE TABLE saga (
    saga_id    VARCHAR(64) PRIMARY KEY,
    state      VARCHAR(16) NOT NULL,          -- PENDING/DONE/FAILED/COMPENSATING/COMPENSATED/UNKNOWN
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE saga_step (
    saga_id   VARCHAR(64) NOT NULL,
    step_id   INT NOT NULL,
    step_name VARCHAR(64) NOT NULL,
    status    VARCHAR(16) NOT NULL,           -- PENDING/DONE/FAILED/COMPENSATED/UNKNOWN
    PRIMARY KEY (saga_id, step_id)
);

-- ============================================================
-- 库存库（资源方）：resource + freeze 冻结表 + tcc_fence 控制表
-- ============================================================
CREATE DATABASE IF NOT EXISTS inventory_db DEFAULT CHARSET utf8mb4;
USE inventory_db;

-- 可用量 = total - frozen（articles/distributed-tx-02 L2 stock 表）
CREATE TABLE resource (
    resource_id VARCHAR(64) PRIMARY KEY,
    total       BIGINT NOT NULL,
    frozen      BIGINT NOT NULL DEFAULT 0
);

-- 冻结表（freeze_id 主键，教学级结构，非框架内建）
CREATE TABLE freeze (
    freeze_id     VARCHAR(64) PRIMARY KEY,
    global_tx_id  VARCHAR(64) NOT NULL,
    branch_id     VARCHAR(64) NOT NULL,
    resource_id   VARCHAR(64) NOT NULL,
    freeze_amount BIGINT NOT NULL,
    status        VARCHAR(16) NOT NULL,       -- FROZEN -> CONFIRMED / CANCELLED（终态）
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_status_time (status, created_at)  -- 悬挂扫描：WHERE status='FROZEN' AND created_at < ?
);

-- 事务控制表（tcc_fence，唯一键 global_tx_id+branch_id，防悬挂核心）
CREATE TABLE tcc_fence (
    global_tx_id VARCHAR(64) NOT NULL,
    branch_id    VARCHAR(64) NOT NULL,
    status       VARCHAR(16) NOT NULL,        -- TRIED / COMMITTED / ROLLBACKED / SUSPENDED
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (global_tx_id, branch_id)
);

INSERT INTO resource (resource_id, total, frozen) VALUES ('SKU1', 100, 0);

-- ============================================================
-- 支付库（资源方）：resource + freeze + tcc_fence（与库存库同构，资源语义不同）
-- ============================================================
CREATE DATABASE IF NOT EXISTS payment_db DEFAULT CHARSET utf8mb4;
USE payment_db;

CREATE TABLE resource (
    resource_id VARCHAR(64) PRIMARY KEY,
    total       BIGINT NOT NULL,               -- 余额（分）
    frozen      BIGINT NOT NULL DEFAULT 0      -- 冻结金额（分）
);

CREATE TABLE freeze (
    freeze_id     VARCHAR(64) PRIMARY KEY,
    global_tx_id  VARCHAR(64) NOT NULL,
    branch_id     VARCHAR(64) NOT NULL,
    resource_id   VARCHAR(64) NOT NULL,
    freeze_amount BIGINT NOT NULL,
    status        VARCHAR(16) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_status_time (status, created_at)
);

CREATE TABLE tcc_fence (
    global_tx_id VARCHAR(64) NOT NULL,
    branch_id    VARCHAR(64) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (global_tx_id, branch_id)
);

INSERT INTO resource (resource_id, total, frozen) VALUES ('U1', 10000, 0);
