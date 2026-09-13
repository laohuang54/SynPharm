package com.synpharm.pipeline;

import com.synpharm.dto.ParsedInput;
import com.synpharm.enums.InputType;

public interface InputParser {
    InputType getInputType();
    ParsedInput parse(String inputValue, String fileUrl);

    /**
     * 带算法类型的解析（修复方案 5.2 新增）。
     *
     * <p>默认委托给两参版本，既有解析器零改动；
     * 需要按算法类型区分解析逻辑（如 UniProt/PDB 解析）的解析器可覆写此方法。
     *
     * @param inputValue 原始输入
     * @param fileUrl    文件地址（可为 null）
     * @param algoType   算法类型（DTI/PPI/DDI）
     */
    default ParsedInput parse(String inputValue, String fileUrl, String algoType) {
        return parse(inputValue, fileUrl);
    }
}