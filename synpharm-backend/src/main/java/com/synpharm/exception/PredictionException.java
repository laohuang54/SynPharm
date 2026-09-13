package com.synpharm.exception;

import lombok.Getter;

/**
 * 预测模块专用业务异常（修复方案 5.5）。
 *
 * <p>携带字符串错误码 {@link PredictionErrorCode}，由
 * {@link GlobalExceptionHandler} 统一转换为带 errorCode 字段的响应。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Getter
public class PredictionException extends RuntimeException {

    private final PredictionErrorCode errorCode;

    public PredictionException(PredictionErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public PredictionException(PredictionErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PredictionException(PredictionErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
