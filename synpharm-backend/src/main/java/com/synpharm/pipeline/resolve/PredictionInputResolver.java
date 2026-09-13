package com.synpharm.pipeline.resolve;

import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 预测输入统一解析编排器（修复方案 5.2/5.3）。
 *
 * <p>输入格式沿用管道既有约定：两个输入以逗号分隔（{@code 输入A,输入B}）。
 * 按算法类型解析：
 * <ul>
 *   <li>DTI：输入A = 配体 SMILES，输入B = 靶点（序列 / UniProt ID / PDB 引用）</li>
 *   <li>PPI：输入A、B 各自为序列 / UniProt ID / PDB 引用</li>
 *   <li>DDI：输入A、B 均为 SMILES</li>
 * </ul>
 *
 * <p>输入类型语义：
 * <ul>
 *   <li>{@code uniprot} / {@code pdb}：显式声明，B 侧（PPI 为两侧）必须走对应解析器，DDI 不支持</li>
 *   <li>{@code smiles}（含旧兼容接口）：自动嗅探——匹配 UniProt ID 格式则查 UniProt，
 *       匹配 PDB 引用格式则查 RCSB，否则按蛋白质序列处理（宽松校验，兼容存量输入）</li>
 * </ul>
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictionInputResolver {

    private static final int MAX_SMILES_LENGTH = 2000;

    private final UniProtResolver uniProtResolver;
    private final PdbResolver pdbResolver;
    private final ProteinSequenceValidator sequenceValidator;

    /**
     * 解析原始输入为标准化输入模型。
     *
     * @param algoType   算法类型（DTI/PPI/DDI，大小写不敏感）
     * @param inputType  输入类型（smiles/uniprot/pdb）
     * @param inputValue 原始输入（逗号分隔两个值）
     * @return 解析完成的标准化输入
     */
    public ResolvedPredictionInput resolve(String algoType, String inputType, String inputValue) {
        if (inputValue == null || inputValue.isBlank()) {
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED, "输入不能为空");
        }

        String[] parts = inputValue.split(",", -1);
        if (parts.length < 2) {
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "输入格式错误，需要逗号分隔的两个输入");
        }
        String first = parts[0] == null ? "" : parts[0].trim();
        String second = parts[1] == null ? "" : parts[1].trim();
        if (first.isEmpty() || second.isEmpty()) {
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED, "输入不能为空");
        }

        String algo = algoType == null ? "" : algoType.trim().toUpperCase();
        return switch (algo) {
            case "DTI" -> resolveDTI(first, second, inputType);
            case "PPI" -> resolvePPI(first, second, inputType);
            case "DDI" -> resolveDDI(first, second, inputType);
            default -> throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "未知算法类型: " + algoType);
        };
    }

    private ResolvedPredictionInput resolveDTI(String ligand, String target, String inputType) {
        String smiles = validateSmiles(ligand);
        String sequence = resolveProteinTarget(target, inputType);
        return ResolvedPredictionInput.builder()
                .algoType("DTI")
                .inputType(inputType)
                .ligandSmiles(smiles)
                .targetSequence(sequence)
                .build();
    }

    private ResolvedPredictionInput resolvePPI(String a, String b, String inputType) {
        return ResolvedPredictionInput.builder()
                .algoType("PPI")
                .inputType(inputType)
                .proteinA(resolveProteinTarget(a, inputType))
                .proteinB(resolveProteinTarget(b, inputType))
                .build();
    }

    private ResolvedPredictionInput resolveDDI(String a, String b, String inputType) {
        if ("uniprot".equalsIgnoreCase(inputType) || "pdb".equalsIgnoreCase(inputType)) {
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "DDI 不支持 " + inputType + " 输入类型，请使用 smiles");
        }
        return ResolvedPredictionInput.builder()
                .algoType("DDI")
                .inputType(inputType)
                .drugA(validateSmiles(a))
                .drugB(validateSmiles(b))
                .build();
    }

    /**
     * 解析靶点/蛋白质输入：显式类型强制走对应解析器；
     * smiles 类型下自动嗅探 UniProt ID / PDB 引用，其余按序列宽松校验。
     */
    private String resolveProteinTarget(String raw, String inputType) {
        if ("uniprot".equalsIgnoreCase(inputType)) {
            return uniProtResolver.resolve(raw);
        }
        if ("pdb".equalsIgnoreCase(inputType)) {
            return pdbResolver.resolve(raw);
        }
        if (UniProtResolver.isUniProtId(raw)) {
            log.debug("自动识别为 UniProt ID: {}", raw);
            return uniProtResolver.resolve(raw);
        }
        if (PdbResolver.looksLikePdbRef(raw)) {
            log.debug("自动识别为 PDB 引用: {}", raw);
            return pdbResolver.resolve(raw);
        }
        // 存量兼容：按蛋白质序列处理，仅字符集校验（不强制最小长度）
        return sequenceValidator.validateLenient(raw);
    }

    /**
     * SMILES 基础校验（修复方案 5.5 INVALID_SMILES）：
     * 非空、长度限制、字符集、括号配平。深度化学校验由 FastAPI 侧模型负责。
     */
    private String validateSmiles(String raw) {
        String smiles = raw.trim();
        if (smiles.isEmpty()) {
            throw new PredictionException(PredictionErrorCode.INVALID_SMILES, "SMILES 不能为空");
        }
        if (smiles.length() > MAX_SMILES_LENGTH) {
            throw new PredictionException(PredictionErrorCode.INVALID_SMILES,
                    "SMILES 表达式过长（最大 " + MAX_SMILES_LENGTH + " 字符）");
        }
        int parenDepth = 0;
        int bracketDepth = 0;
        for (char c : smiles.toCharArray()) {
            if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                parenDepth--;
                if (parenDepth < 0) {
                    throw new PredictionException(PredictionErrorCode.INVALID_SMILES, "SMILES 括号不配平");
                }
            } else if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth--;
                if (bracketDepth < 0) {
                    throw new PredictionException(PredictionErrorCode.INVALID_SMILES, "SMILES 方括号不配平");
                }
            } else if (!isSmilesChar(c)) {
                throw new PredictionException(PredictionErrorCode.INVALID_SMILES, "SMILES 含非法字符: " + c);
            }
        }
        if (parenDepth != 0 || bracketDepth != 0) {
            throw new PredictionException(PredictionErrorCode.INVALID_SMILES, "SMILES 括号不配平");
        }
        return smiles;
    }

    private boolean isSmilesChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '(' || c == ')' || c == '[' || c == ']' || c == '=' || c == '#'
                || c == '+' || c == '-' || c == '.' || c == '/' || c == '\\' || c == '@'
                || c == '%' || c == '$' || c == ':' || c == '*';
    }
}
