package com.synpharm.pipeline.impl;

import com.synpharm.dto.ParsedInput;
import com.synpharm.enums.InputType;
import com.synpharm.pipeline.InputParser;
import com.synpharm.pipeline.resolve.PredictionInputResolver;
import com.synpharm.pipeline.resolve.ResolvedPredictionInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * PDB 输入解析器（修复方案 5.3）。
 *
 * <p>显式声明 inputType=pdb 时使用，支持 {@code 6M0J:A} 形式：
 * <ul>
 *   <li>DTI：{@code SMILES,PDB引用} → [SMILES, 序列]</li>
 *   <li>PPI：{@code PDB引用,PDB引用} → [序列A, 序列B]</li>
 * </ul>
 * 解析出的序列经严格校验（字符集 + 最小长度）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdbInputParser implements InputParser {

    private final PredictionInputResolver inputResolver;

    @Override
    public InputType getInputType() {
        return InputType.PDB;
    }

    @Override
    public ParsedInput parse(String inputValue, String fileUrl) {
        // 无算法类型时按 PPI 语义解析（两个 PDB 引用）
        return parse(inputValue, fileUrl, "PPI");
    }

    @Override
    public ParsedInput parse(String inputValue, String fileUrl, String algoType) {
        log.debug("解析PDB输入: algoType={}, input={}", algoType, inputValue);

        ResolvedPredictionInput resolved = inputResolver.resolve(algoType, InputType.PDB.getCode(), inputValue);
        List<String> params = "DTI".equalsIgnoreCase(resolved.getAlgoType())
                ? Arrays.asList(resolved.getLigandSmiles(), resolved.getTargetSequence())
                : Arrays.asList(resolved.getProteinA(), resolved.getProteinB());

        return ParsedInput.builder()
                .params(params)
                .inputType(InputType.PDB.getCode())
                .build();
    }
}
