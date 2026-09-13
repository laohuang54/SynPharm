package com.synpharm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 算法引擎健康检查响应（修复方案 5.7）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmHealthResponse {

    /** UP：可达；DOWN：不可达或熔断开启 */
    private String status;

    /** FastAPI /health/ 返回的 status 字段（UP 时有值，如 healthy） */
    private String fastapiStatus;

    /** 探测耗时（毫秒） */
    private Long latencyMs;

    /** 附加说明 */
    private String message;

    /** 探测时间 */
    private LocalDateTime checkedAt;
}
