package com.synpharm.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量任务明细实体（修复方案 5.6）。
 *
 * <p>映射数据库表 batch_task_item，保存批量 CSV 每一行的
 * 输入、处理状态、错误信息与关联结果 ID。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@TableName("batch_task_item")
public class BatchTaskItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private String batchId;

    @TableField("row_number")
    private Integer rowNumber;

    @TableField("input_value")
    private String inputValue;

    @TableField("status")
    private Integer status;

    @TableField("result_id")
    private Long resultId;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
