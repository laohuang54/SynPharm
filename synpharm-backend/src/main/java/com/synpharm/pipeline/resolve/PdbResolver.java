package com.synpharm.pipeline.resolve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PDB 解析器（修复方案 5.3/5.4）。
 *
 * <p>通过 RCSB 数据 API 将 PDB 结构引用解析为蛋白质序列，支持 {@code 6M0J:A} 形式：
 * <ol>
 *   <li>{@code GET {rcsb-base-url}/rest/v1/core/entry/{pdbId}} —— 校验结构存在（404 → PDB_NOT_FOUND）</li>
 *   <li>{@code GET .../rest/v1/core/polymer_entity_instance/{pdbId}/{chain}} —— 链 → 实体映射（404 → CHAIN_NOT_FOUND）</li>
 *   <li>{@code GET .../rest/v1/core/polymer_entity/{pdbId}/{entityId}} —— 取链的 FASTA 序列</li>
 * </ol>
 *
 * <p>语法：链可省略（默认 A）；支持 {@code :} 或 {@code .} 分隔、大小写不敏感；
 * 多链用逗号分隔（如 {@code 6M0J:A,B}），序列按给定顺序拼接。
 *
 * <p>Redis 缓存：{@code synpharm:pdb:{pdbId}:{chain}}；负缓存与 UniProt 同策略；
 * Redis 不可用时自动降级直连。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdbResolver {

    /** PDB 结构 ID：4 位字母数字且首位为数字，如 6M0J */
    private static final Pattern PDB_ID_PATTERN = Pattern.compile("^[0-9][A-Za-z0-9]{3}$");

    /** 链标识：1-3 位字母数字，如 A / AA */
    private static final Pattern CHAIN_PATTERN = Pattern.compile("^[A-Z0-9]{1,3}$");

    private static final String DEFAULT_CHAIN = "A";

    private static final String CACHE_PREFIX = "synpharm:pdb:";
    private static final String MISSING_PREFIX = "synpharm:pdb:missing:";
    private static final String MISSING_MARKER = "1";

    private final RestClient externalRestClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProteinSequenceValidator sequenceValidator;

    @Value("${prediction.resolver.rcsb-base-url}")
    private String rcsbBaseUrl;

    @Value("${prediction.resolver.cache-ttl-hours:24}")
    private long cacheTtlHours;

    @Value("${prediction.resolver.negative-cache-ttl-minutes:10}")
    private long negativeCacheTtlMinutes;

    /**
     * 判断输入是否形如 PDB 引用（4 位结构 ID，可带 :链 / .链 后缀）。
     */
    public static boolean looksLikePdbRef(String input) {
        if (input == null) {
            return false;
        }
        String head = input.trim();
        int colon = head.indexOf(':');
        int dot = head.indexOf('.');
        int sep = colon >= 0 ? colon : dot;
        if (sep >= 0) {
            head = head.substring(0, sep);
        }
        return PDB_ID_PATTERN.matcher(head).matches();
    }

    /**
     * 解析 PDB 引用（如 {@code 6M0J} / {@code 6M0J:A} / {@code 6m0j.a}）为蛋白质序列。
     *
     * @param pdbRef PDB 引用
     * @return 蛋白质序列（多链按顺序拼接）
     * @throws PredictionException PDB_NOT_FOUND / CHAIN_NOT_FOUND / INPUT_RESOLVE_FAILED
     */
    public String resolve(String pdbRef) {
        PdbRef ref = parseRef(pdbRef);

        List<String> sequences = new ArrayList<>();
        for (String chain : ref.getChains()) {
            String cacheKey = CACHE_PREFIX + ref.getPdbId() + ":" + chain;

            String cached = safeGet(cacheKey);
            if (cached != null) {
                sequences.add(cached);
                continue;
            }
            if (safeGet(MISSING_PREFIX + ref.getPdbId() + ":" + chain) != null) {
                throw new PredictionException(PredictionErrorCode.CHAIN_NOT_FOUND,
                        "PDB 结构中不存在链 " + chain + "（" + ref.getPdbId() + "）");
            }

            try {
                String sequence = fetchChainSequence(ref.getPdbId(), chain);
                String validated = sequenceValidator.validate(sequence, true);
                safeSet(cacheKey, validated, Duration.ofHours(cacheTtlHours));
                sequences.add(validated);
            } catch (PredictionException e) {
                // 结构/链不存在时写短时负缓存，防穿透
                if (e.getErrorCode() == PredictionErrorCode.PDB_NOT_FOUND
                        || e.getErrorCode() == PredictionErrorCode.CHAIN_NOT_FOUND) {
                    safeSet(MISSING_PREFIX + ref.getPdbId() + ":" + chain, MISSING_MARKER,
                            Duration.ofMinutes(negativeCacheTtlMinutes));
                }
                throw e;
            }
        }

        return String.join("", sequences);
    }

    /**
     * 三步 RCSB 调用：结构校验 → 链/实体映射 → 序列提取。
     * 各步骤独立处理 404 语义：结构 404 → PDB_NOT_FOUND；链/实体 404 → CHAIN_NOT_FOUND。
     */
    private String fetchChainSequence(String pdbId, String chain) {
        // 1. 结构存在性校验
        try {
            String entryJson = externalRestClient.get()
                    .uri(rcsbBaseUrl + "/rest/v1/core/entry/{pdbId}", pdbId)
                    .retrieve()
                    .body(String.class);
            if (entryJson == null || entryJson.isBlank()) {
                throw new PredictionException(PredictionErrorCode.PDB_NOT_FOUND, "PDB 结构不存在: " + pdbId);
            }
        } catch (PredictionException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new PredictionException(PredictionErrorCode.PDB_NOT_FOUND, "PDB 结构不存在: " + pdbId);
            }
            log.warn("查询 RCSB 结构失败: pdbId={}, status={}", pdbId, e.getStatusCode().value());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 RCSB 失败(HTTP " + e.getStatusCode().value() + "): " + pdbId);
        } catch (Exception e) {
            log.warn("查询 RCSB 结构失败: pdbId={}, error={}", pdbId, e.getMessage());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 RCSB 失败: " + e.getMessage());
        }

        // 2. 链 → 实体 ID 映射（404 → CHAIN_NOT_FOUND）
        String entityId;
        try {
            String instanceJson = externalRestClient.get()
                    .uri(rcsbBaseUrl + "/rest/v1/core/polymer_entity_instance/{pdbId}/{chain}", pdbId, chain)
                    .retrieve()
                    .body(String.class);
            JsonNode instanceNode = objectMapper.readTree(instanceJson);
            JsonNode entityIdNode = instanceNode.path("rcsb_polymer_entity_instance_container_identifiers").path("entity_id");
            if (entityIdNode.isMissingNode() || entityIdNode.asText().isBlank()) {
                throw new PredictionException(PredictionErrorCode.CHAIN_NOT_FOUND,
                        "PDB 结构中不存在链 " + chain + "（" + pdbId + "）");
            }
            entityId = entityIdNode.asText();
        } catch (PredictionException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new PredictionException(PredictionErrorCode.CHAIN_NOT_FOUND,
                        "PDB 结构中不存在链 " + chain + "（" + pdbId + "）");
            }
            log.warn("查询 RCSB 链映射失败: pdbId={}, chain={}, status={}", pdbId, chain, e.getStatusCode().value());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 RCSB 失败(HTTP " + e.getStatusCode().value() + "): " + pdbId);
        } catch (Exception e) {
            log.warn("查询 RCSB 链映射失败: pdbId={}, chain={}, error={}", pdbId, chain, e.getMessage());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 RCSB 失败: " + e.getMessage());
        }

        // 3. 实体 → FASTA 序列
        try {
            String entityJson = externalRestClient.get()
                    .uri(rcsbBaseUrl + "/rest/v1/core/polymer_entity/{pdbId}/{entityId}", pdbId, entityId)
                    .retrieve()
                    .body(String.class);
            JsonNode entityNode = objectMapper.readTree(entityJson);
            String sequence = entityNode.path("entity_poly").path("pdbx_seq_one_letter_code_can").asText(null);
            if (sequence == null || sequence.isBlank()) {
                throw new PredictionException(PredictionErrorCode.CHAIN_NOT_FOUND,
                        "链 " + chain + " 无蛋白质序列数据（" + pdbId + "）");
            }
            return sequence;
        } catch (PredictionException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new PredictionException(PredictionErrorCode.CHAIN_NOT_FOUND,
                        "PDB 结构中不存在链 " + chain + "（" + pdbId + "）");
            }
            log.warn("查询 RCSB 实体失败: pdbId={}, entityId={}, status={}", pdbId, entityId, e.getStatusCode().value());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 RCSB 失败(HTTP " + e.getStatusCode().value() + "): " + pdbId);
        } catch (Exception e) {
            log.warn("查询 RCSB 实体失败: pdbId={}, entityId={}, error={}", pdbId, entityId, e.getMessage());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 RCSB 失败: " + e.getMessage());
        }
    }

    /**
     * 解析 PDB 引用语法：结构 ID 必填，链可选（默认 A），多链逗号分隔。
     */
    private PdbRef parseRef(String input) {
        if (input == null || input.isBlank()) {
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED, "PDB 输入不能为空");
        }
        String text = input.trim();
        String pdbIdPart = text;
        String chainPart = null;
        int colon = text.indexOf(':');
        int dot = text.indexOf('.');
        int sep = colon >= 0 ? colon : dot;
        if (sep >= 0) {
            pdbIdPart = text.substring(0, sep);
            chainPart = text.substring(sep + 1);
        }

        String pdbId = pdbIdPart.toUpperCase();
        if (!PDB_ID_PATTERN.matcher(pdbId).matches()) {
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED, "不是合法的 PDB ID: " + input);
        }

        List<String> chains = new ArrayList<>();
        if (chainPart == null || chainPart.isBlank()) {
            chains.add(DEFAULT_CHAIN);
        } else {
            for (String chain : chainPart.split(",")) {
                String normalized = chain.trim().toUpperCase();
                if (!CHAIN_PATTERN.matcher(normalized).matches()) {
                    throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                            "PDB 链格式错误: " + chain);
                }
                chains.add(normalized);
            }
        }
        return new PdbRef(pdbId, chains);
    }

    /** Redis 读失败降级：当未命中处理 */
    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级直连外部 API: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /** Redis 写失败仅告警，不影响主流程 */
    private void safeSet(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis 写入失败，跳过缓存: key={}, error={}", key, e.getMessage());
        }
    }

    /** PDB 引用（结构 ID + 链列表） */
    @Getter
    @AllArgsConstructor
    private static class PdbRef {
        private final String pdbId;
        private final List<String> chains;
    }
}
