package com.synpharm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 批量任务明细行响应（修复方案 5.6）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchItemResponse {

    private Long id;

    private String batchId;

    /** CSV 行号（从 1 开始） */
    private Integer rowNumber;

    /** 该行原始输入 */
    private String inputValue;

    /** 状态：0:PENDING, 1:PROCESSING, 2:SUCCESS, 3:FAIL */
    private Integer status;

    private String statusText;

    /** 成功行关联的预测结果 ID */
    private Long resultId;

    /** 字符串错误码（如 INVALID_SMILES） */
    private String errorCode;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
