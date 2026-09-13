package com.synpharm.pipeline.resolve;

import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 蛋白质序列校验器（修复方案 5.2）。
 *
 * <p>提供两种校验强度：
 * <ul>
 *   <li>宽松（lenient）：仅校验字符集（字母），用于 smiles 输入类型下的存量兼容
 *       ——旧接口允许任意短序列直传算法引擎，收紧会破坏原有功能。</li>
 *   <li>严格（strict）：额外要求长度 ≥ {@link #MIN_SEQUENCE_LENGTH} 个残基，
 *      用于 UniProt / PDB 解析得到的真实序列（真实蛋白长度远超该阈值）。</li>
 * </ul>
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Component
public class ProteinSequenceValidator {

    /** 严格模式下的最小序列长度（残基数） */
    public static final int MIN_SEQUENCE_LENGTH = 31;

    /** 20 种标准氨基酸字母（大小写均可），内部空白字符会被移除 */
    private static final Pattern AMINO_ACID_PATTERN = Pattern.compile("^[A-Za-z]+$");

    /**
     * 校验序列（严格模式：字符集 + 最小长度）。
     *
     * @param sequence 原始序列（可含空白字符，校验前会移除）
     * @return 规范化后的序列（去空白）
     */
    public String validateStrict(String sequence) {
        String normalized = normalize(sequence);
        if (normalized.length() < MIN_SEQUENCE_LENGTH) {
            throw new PredictionException(PredictionErrorCode.SEQUENCE_TOO_SHORT,
                    "蛋白质序列长度不足，至少需要 " + MIN_SEQUENCE_LENGTH + " 个残基");
        }
        return normalized;
    }

    /**
     * 校验序列（宽松模式：仅字符集校验，兼容存量输入）。
     *
     * @param sequence 原始序列（可含空白字符，校验前会移除）
     * @return 规范化后的序列（去空白）
     */
    public String validateLenient(String sequence) {
        return normalize(sequence);
    }

    /**
     * 统一入口：strict 时执行严格校验。
     */
    public String validate(String sequence, boolean strict) {
        return strict ? validateStrict(sequence) : validateLenient(sequence);
    }

    private String normalize(String sequence) {
        if (sequence == null || sequence.isBlank()) {
            throw new PredictionException(PredictionErrorCode.INVALID_SEQUENCE, "蛋白质序列不能为空");
        }
        // 移除所有空白字符（空格/制表/换行），用户粘贴序列时常带换行
        String normalized = sequence.replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            throw new PredictionException(PredictionErrorCode.INVALID_SEQUENCE, "蛋白质序列不能为空");
        }
        if (!AMINO_ACID_PATTERN.matcher(normalized).matches()) {
            throw new PredictionException(PredictionErrorCode.INVALID_SEQUENCE,
                    "蛋白质序列包含非法字符，仅支持 20 种标准氨基酸字母");
        }
        return normalized;
    }
}
