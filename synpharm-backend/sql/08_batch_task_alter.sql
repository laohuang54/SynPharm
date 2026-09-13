-- =============================================
-- SynPharm batch_task 表结构迁移
-- 为批量任务消息队列（RabbitMQ）改造补充列
-- 版本：v3.1.0（v4.0.0 修复：改为真幂等）
-- 说明：06_batch_task.sql 建表时已包含 algo_type/deleted 列，
--       旧写法直接 ADD COLUMN 会在 Docker 全新初始化时报 1060 并中断
--       docker-entrypoint-initdb.d 后续脚本（如 09）执行；
--       改为先查 information_schema 再决定是否加列，旧库/新库均安全。
-- =============================================

USE synpharm;

-- 算法类型（持久化，避免依赖 Redis 缓存）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'synpharm' AND TABLE_NAME = 'batch_task' AND COLUMN_NAME = 'algo_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE batch_task ADD COLUMN algo_type VARCHAR(20) NOT NULL DEFAULT '''' COMMENT ''算法类型 DTI/PPI/DDI'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 逻辑删除（与 Mapper XML 的 deleted=0 查询一致）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'synpharm' AND TABLE_NAME = 'batch_task' AND COLUMN_NAME = 'deleted'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE batch_task ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0未删除 1已删除'' AFTER update_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
