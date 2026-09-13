package com.synpharm.pipeline.resolve;

import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * UniProt 解析器（修复方案 5.3/5.4）。
 *
 * <p>通过 UniProt REST API 将 UniProt ID 解析为蛋白质序列：
 * <pre>GET {uniprot-base-url}/uniprotkb/{id}.fasta</pre>
 *
 * <p>Redis 缓存：{@code synpharm:uniprot:{id}} 缓存序列（默认 24h）；
 * 404 用 {@code synpharm:uniprot:missing:{id}} 做短时负缓存（默认 10min）防穿透。
 * Redis 不可用时自动降级直连（读失败当未命中、写失败仅告警）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UniProtResolver {

    /** UniProt 访问号格式：6-10 位字母数字，如 P12345 / Q8N0W4 / P0DTC2 */
    private static final Pattern UNIPROT_ID_PATTERN =
            Pattern.compile("^([OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2})$");

    private static final String CACHE_PREFIX = "synpharm:uniprot:";
    private static final String MISSING_PREFIX = "synpharm:uniprot:missing:";
    private static final String MISSING_MARKER = "1";

    private final RestClient externalRestClient;
    private final StringRedisTemplate redisTemplate;
    private final ProteinSequenceValidator sequenceValidator;

    @Value("${prediction.resolver.uniprot-base-url}")
    private String uniprotBaseUrl;

    @Value("${prediction.resolver.cache-ttl-hours:24}")
    private long cacheTtlHours;

    @Value("${prediction.resolver.negative-cache-ttl-minutes:10}")
    private long negativeCacheTtlMinutes;

    /**
     * 判断输入是否为合法 UniProt ID。
     */
    public static boolean isUniProtId(String input) {
        return input != null && UNIPROT_ID_PATTERN.matcher(input.trim().toUpperCase()).matches();
    }

    /**
     * 解析 UniProt ID 为蛋白质序列（先查缓存，未命中再调外部 API）。
     *
     * @param uniprotId UniProt ID（大小写不敏感）
     * @return 蛋白质序列
     * @throws PredictionException UNIPROT_NOT_FOUND / INPUT_RESOLVE_FAILED / SEQUENCE_TOO_SHORT
     */
    public String resolve(String uniprotId) {
        String id = uniprotId == null ? "" : uniprotId.trim().toUpperCase();
        if (!UNIPROT_ID_PATTERN.matcher(id).matches()) {
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED, "不是合法的 UniProt ID: " + uniprotId);
        }

        String cacheKey = CACHE_PREFIX + id;

        // 1. 正缓存命中
        String cached = safeGet(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 负缓存命中（此前 404 过，短时内不再打外网）
        if (safeGet(MISSING_PREFIX + id) != null) {
            throw new PredictionException(PredictionErrorCode.UNIPROT_NOT_FOUND, "UniProt 条目不存在: " + id);
        }

        // 3. 调用 UniProt REST API
        try {
            String fasta = externalRestClient.get()
                    .uri(uniprotBaseUrl + "/uniprotkb/{id}.fasta", id)
                    .retrieve()
                    .body(String.class);
            String sequence = parseFasta(fasta, id);
            // 严格校验：真实蛋白序列长度必须 ≥ 31 残基
            String validated = sequenceValidator.validate(sequence, true);
            safeSet(cacheKey, validated, Duration.ofHours(cacheTtlHours));
            return validated;
        } catch (PredictionException e) {
            throw e;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                safeSet(MISSING_PREFIX + id, MISSING_MARKER, Duration.ofMinutes(negativeCacheTtlMinutes));
                throw new PredictionException(PredictionErrorCode.UNIPROT_NOT_FOUND, "UniProt 条目不存在: " + id);
            }
            log.warn("查询 UniProt 失败: id={}, status={}", id, e.getStatusCode().value());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 UniProt 失败(HTTP " + e.getStatusCode().value() + "): " + id);
        } catch (Exception e) {
            log.warn("查询 UniProt 失败: id={}, error={}", id, e.getMessage());
            throw new PredictionException(PredictionErrorCode.INPUT_RESOLVE_FAILED,
                    "查询 UniProt 失败: " + e.getMessage());
        }
    }

    /**
     * 解析 FASTA 文本，提取序列（跳过以 {@code >} 开头的描述行）。
     */
    private String parseFasta(String fasta, String id) {
        if (fasta == null || fasta.isBlank()) {
            throw new PredictionException(PredictionErrorCode.UNIPROT_NOT_FOUND, "UniProt 返回内容为空: " + id);
        }
        StringBuilder sb = new StringBuilder();
        for (String line : fasta.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(">")) {
                continue;
            }
            sb.append(trimmed);
        }
        String sequence = sb.toString();
        if (sequence.isEmpty()) {
            throw new PredictionException(PredictionErrorCode.UNIPROT_NOT_FOUND, "UniProt 返回内容不含序列: " + id);
        }
        return sequence;
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
}
