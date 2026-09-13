-- =============================================
-- SynPharm 批量任务明细表 (batch_task_item)
-- 版本：v4.0.0
-- 说明：修复方案 5.6 批量数据闭环。
--       保存批量 CSV 每一行的输入、状态、错误信息和结果 ID，
--       使任务中心/结果中心可以按行查询批量数据。
--       幂等执行（IF NOT EXISTS，重复运行安全）。
-- =============================================

USE synpharm;

CREATE TABLE IF NOT EXISTS batch_task_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    batch_id VARCHAR(64) NOT NULL COMMENT '批次ID（batch_task.batch_id）',
    `row_number` INT NOT NULL COMMENT 'CSV行号（从1开始）',
    input_value TEXT NOT NULL COMMENT '该行原始输入',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0:PENDING, 1:PROCESSING, 2:SUCCESS, 3:FAIL',
    result_id BIGINT DEFAULT NULL COMMENT '成功行关联的预测结果ID（predict_result.id）',
    error_code VARCHAR(64) DEFAULT NULL COMMENT '字符串错误码（如 INVALID_SMILES / SEQUENCE_TOO_SHORT）',
    error_message TEXT DEFAULT NULL COMMENT '行级错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    UNIQUE KEY uk_batch_row (batch_id, `row_number`),
    INDEX idx_batch_id (batch_id),
    INDEX idx_result_id (result_id),
    FOREIGN KEY (batch_id) REFERENCES batch_task(batch_id),
    FOREIGN KEY (result_id) REFERENCES predict_result(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='批量任务明细表';
