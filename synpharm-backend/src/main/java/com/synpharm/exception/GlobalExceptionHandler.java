package com.synpharm.exception;

import com.synpharm.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * <p>统一捕获Controller层抛出的异常，转换成统一的响应格式。
 * 不用在每个Controller里 try-catch。
 *
 * @author SynPharm Team
 * @version 2.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * <p>业务中主动抛出的异常，通常是用户操作不当导致的。
     * HTTP状态码还是200，因为请求成功到达了，只是业务逻辑不满足。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        // 业务异常用warn级别，不是系统错误
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     * <p>@Valid 校验失败时抛出。HTTP状态码返回400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        // 收集所有字段的校验错误
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("参数校验失败: {}", errors);

        // 返回结构化的错误信息，前端可以按字段展示
        return Result.<Map<String, String>>builder()
                .code(400)
                .message("参数校验失败")
                .data(errors)
                .build();
    }

    /**
     * 处理预测模块业务异常（修复方案 5.5 新增）
     * <p>携带字符串错误码的预测异常（如 SEQUENCE_TOO_SHORT）。
     * HTTP 状态码沿用业务异常惯例返回 200，错误码通过 errorCode 字段返回。
     */
    @ExceptionHandler(PredictionException.class)
    public Result<?> handlePredictionException(PredictionException e) {
        log.warn("预测异常: errorCode={}, message={}", e.getErrorCode().name(), e.getMessage());
        return Result.error(ErrorCode.PREDICT_ERROR.getCode(), e.getErrorCode().name(), e.getMessage());
    }

    /**
     * 处理管道执行异常（修复方案 5.5 新增）
     * <p>管道分阶段抛出的异常映射为字符串错误码：
     * parse/format → INPUT_RESOLVE_FAILED，execute → FASTAPI_UNAVAILABLE。
     */
    @ExceptionHandler(PipelineException.class)
    public Result<?> handlePipelineException(PipelineException e) {
        PredictionErrorCode errorCode = switch (e.getStage()) {
            case "parse", "format" -> PredictionErrorCode.INPUT_RESOLVE_FAILED;
            case "execute" -> PredictionErrorCode.FASTAPI_UNAVAILABLE;
            default -> PredictionErrorCode.INPUT_RESOLVE_FAILED;
        };
        log.warn("管道异常: stage={}, errorCode={}, message={}", e.getStage(), errorCode.name(), e.getMessage());
        return Result.error(ErrorCode.PREDICT_ERROR.getCode(), errorCode.name(), e.getMessage());
    }

    /**
     * 处理所有未捕获的异常
     * <p>兜底处理，防止异常直接抛给前端。HTTP状态码返回500。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        // 系统异常用error级别，需要关注
        log.error("系统异常: ", e);
        // 不把具体错误信息返回给前端（安全考虑，也不友好）
        return Result.error(500, "系统错误，请稍后重试");
    }
}
