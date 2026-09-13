package com.synpharm.pipeline.resolve;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标准化输入模型（修复方案 5.2）。
 *
 * <p>输入解析完成后的统一载体，字段按算法类型填充：
 * <ul>
 *   <li>DTI：ligandSmiles + targetSequence</li>
 *   <li>PPI：proteinA + proteinB（均为解析后的氨基酸序列）</li>
 *   <li>DDI：drugA + drugB（均为校验后的 SMILES）</li>
 * </ul>
 * 说明：targetId / targetName 为结果侧字段（FastAPI 返回），不属于输入模型，
 * 落库时由 {@link com.synpharm.dto.response.PredictResultResponse} 携带。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedPredictionInput {

    /** 算法类型：DTI / PPI / DDI */
    private String algoType;

    /** 输入类型：smiles / uniprot / pdb */
    private String inputType;

    /** DTI：配体 SMILES（已校验） */
    private String ligandSmiles;

    /** DTI：靶点蛋白质序列（已解析/校验） */
    private String targetSequence;

    /** PPI：蛋白质 A 序列（已解析/校验） */
    private String proteinA;

    /** PPI：蛋白质 B 序列（已解析/校验） */
    private String proteinB;

    /** DDI：药物 A SMILES（已校验） */
    private String drugA;

    /** DDI：药物 B SMILES（已校验） */
    private String drugB;
}
