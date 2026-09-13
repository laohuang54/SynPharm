package com.synpharm.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一响应结果
 * 
 * <p>用于封装API接口的统一响应格式。
 * 
 * @param <T> 响应数据类型
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 响应状态码 */
    private Integer code;

    /**
     * 字符串错误码（修复方案 5.5 新增）
     *
     * <p>与整数 code 并存：整数 code 保持全站既有契约不变（前端依赖 code!=200 判断），
     * 字符串错误码仅预测模块等新功能使用（如 SEQUENCE_TOO_SHORT），成功时为 null。
     */
    private String errorCode;

    /** 响应消息 */
    private String message;

    /** 响应数据 */
    private T data;

    /**
     * 成功响应（无数据）
     * 
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return Result.<T>builder()
                .code(200)
                .message("成功")
                .build();
    }

    /**
     * 成功响应（带数据）
     * 
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .message("成功")
                .data(data)
                .build();
    }

    /**
     * 失败响应
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 失败响应（带字符串错误码）
     *
     * @param code 整数错误码
     * @param errorCode 字符串错误码（如 SEQUENCE_TOO_SHORT）
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(Integer code, String errorCode, String message) {
        return Result.<T>builder()
                .code(code)
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    /**
     * 失败响应（默认错误码）
     *
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}