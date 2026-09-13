package com.synpharm.exception;

import com.synpharm.utils.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void 预测异常_返回字符串错误码() {
        Result<?> result = handler.handlePredictionException(
                new PredictionException(PredictionErrorCode.SEQUENCE_TOO_SHORT, "蛋白质序列长度不足，至少需要 31 个残基"));

        assertEquals(ErrorCode.PREDICT_ERROR.getCode(), result.getCode());
        assertEquals("SEQUENCE_TOO_SHORT", result.getErrorCode());
        assertTrue(result.getMessage().contains("31"));
    }

    @Test
    void 管道异常_按阶段映射错误码() {
        Result<?> parseResult = handler.handlePipelineException(PipelineException.parse("SMILES输入不能为空"));
        assertEquals("INPUT_RESOLVE_FAILED", parseResult.getErrorCode());
        assertEquals(ErrorCode.PREDICT_ERROR.getCode(), parseResult.getCode());

        Result<?> executeResult = handler.handlePipelineException(PipelineException.execute("连接超时"));
        assertEquals("FASTAPI_UNAVAILABLE", executeResult.getErrorCode());
    }

    @Test
    void 业务异常_保持既有整数错误码() {
        Result<?> result = handler.handleBusinessException(new BusinessException(ErrorCode.TASK_NOT_FOUND));

        assertEquals(ErrorCode.TASK_NOT_FOUND.getCode(), result.getCode());
        assertNull(result.getErrorCode());
    }

    @Test
    void 兜底异常_返回500系统错误() {
        Result<?> result = handler.handleException(new RuntimeException("boom"));

        assertEquals(500, result.getCode());
        assertEquals("系统错误，请稍后重试", result.getMessage());
    }
}
