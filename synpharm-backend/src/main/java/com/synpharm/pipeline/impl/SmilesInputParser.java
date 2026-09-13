package com.synpharm.pipeline.impl;

import com.synpharm.dto.ParsedInput;
import com.synpharm.enums.InputType;
import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import com.synpharm.pipeline.InputParser;
import com.synpharm.pipeline.resolve.PredictionInputResolver;
import com.synpharm.pipeline.resolve.ResolvedPredictionInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmilesInputParser implements InputParser {

    private final PredictionInputResolver inputResolver;

    @Override
    public InputType getInputType() {
        return InputType.SMILES;
    }

    @Override
    public ParsedInput parse(String inputValue, String fileUrl) {
        log.debug("解析SMILES输入: {}", inputValue);

        if (inputValue == null || inputValue.trim().isEmpty()) {
            throw new IllegalArgumentException("SMILES输入不能为空");
        }

        String[] parts = inputValue.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("SMILES输入格式错误，需要逗号分隔的两个参数");
        }

        List<String> params = Arrays.asList(parts[0].trim(), parts[1].trim());

        return ParsedInput.builder()
                .params(params)
                .inputType(InputType.SMILES.getCode())
                .build();
    }

    /**
     * 带算法类型的解析（修复方案 5.3 新增）。
     *
     * <p>smiles 输入类型下的自动嗅探：DTI 的靶点位置 / PPI 的两个位置若匹配
     * UniProt ID 或 PDB 引用格式，自动解析为序列；否则按蛋白质序列宽松校验
     * （兼容存量输入，仅字符集校验、不强制最小长度）。
     */
    @Override
    public ParsedInput parse(String inputValue, String fileUrl, String algoType) {
        if (algoType == null || algoType.isBlank()) {
            // 无算法类型信息时保持旧行为
            return parse(inputValue, fileUrl);
        }

        ResolvedPredictionInput resolved = inputResolver.resolve(algoType, InputType.SMILES.getCode(), inputValue);
        List<String> params = switch (algoType.trim().toUpperCase()) {
            case "DTI" -> Arrays.asList(resolved.getLigandSmiles(), resolved.getTargetSequence());
            case "PPI" -> Arrays.asList(resolved.getProteinA(), resolved.getProteinB());
            case "DDI" -> Arrays.asList(resolved.getDrugA(), resolved.getDrugB());
            default -> throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "未知算法类型: " + algoType);
        };

        return ParsedInput.builder()
                .params(params)
                .inputType(InputType.SMILES.getCode())
                .build();
    }
}