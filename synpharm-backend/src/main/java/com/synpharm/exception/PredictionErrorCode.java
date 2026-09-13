package com.synpharm.exception;

/**
 * 预测模块字符串错误码（修复方案 5.5）。
 *
 * <p>与既有整数错误码 {@link ErrorCode} 并存：整数 code 保持全站契约不变，
 * 字符串错误码通过 {@link com.synpharm.utils.Result#errorCode} 字段返回，
 * 供前端/调用方做精细化错误处理。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
public enum PredictionErrorCode {

    /** 输入解析失败（格式错误、无法识别的输入等） */
    INPUT_RESOLVE_FAILED("输入解析失败"),

    /** UniProt 条目不存在 */
    UNIPROT_NOT_FOUND("UniProt 条目不存在"),

    /** PDB 结构不存在 */
    PDB_NOT_FOUND("PDB 结构不存在"),

    /** PDB 结构中不存在指定链 */
    CHAIN_NOT_FOUND("PDB 结构中不存在指定链"),

    /** 蛋白质序列包含非法字符 */
    INVALID_SEQUENCE("蛋白质序列不合法"),

    /** 蛋白质序列长度不足 */
    SEQUENCE_TOO_SHORT("蛋白质序列长度不足"),

    /** SMILES 表达式不合法 */
    INVALID_SMILES("SMILES 表达式不合法"),

    /** 算法引擎（FastAPI）不可用 */
    FASTAPI_UNAVAILABLE("算法引擎不可用"),

    /** 模型服务不可用（引擎可达但推理失败） */
    MODEL_UNAVAILABLE("模型服务不可用");

    private final String defaultMessage;

    PredictionErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
