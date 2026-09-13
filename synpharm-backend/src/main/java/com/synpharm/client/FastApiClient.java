package com.synpharm.client;

import com.synpharm.dto.request.PredictRequest;
import com.synpharm.dto.response.AlgoResponse;
import com.synpharm.dto.response.AlgorithmHealthResponse;
import com.synpharm.dto.response.BatchPredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class FastApiClient {

    private final WebClient fastApiWebClient;

    private final Duration singleTimeout;

    private final Duration batchTimeout;

    /** 熔断器（算法引擎健康检查用） */
    private final SimpleCircuitBreaker circuitBreaker;

    @Value("${fastapi.health-timeout:5000}")
    private long healthTimeoutMs;

    @Value("${fastapi.health-retries:2}")
    private int healthRetries;

    public AlgoResponse predictSingle(PredictRequest request) {
        log.info("调用FastAPI单条预测: algoType={}", request.getAlgoType());
        try {
            return fastApiWebClient.post()
                    .uri("/v1/predict/single")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AlgoResponse.class)
                    .timeout(singleTimeout)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI单条预测HTTP错误: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("预测服务调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("FastAPI单条预测调用失败", e);
            throw new RuntimeException("预测服务调用失败");
        }
    }

    public BatchPredictionResponse predictBatch(List<PredictRequest> requestList, String algoType) {
        log.info("调用FastAPI批量预测: {}条数据, algoType={}", requestList.size(), algoType);
        try {
            return fastApiWebClient.post()
                    .uri("/v1/predict/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("data_list", requestList, "algo_type", algoType))
                    .retrieve()
                    .bodyToMono(BatchPredictionResponse.class)
                    .timeout(batchTimeout)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("FastAPI批量预测HTTP错误: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("批量预测服务调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("FastAPI批量预测调用失败", e);
            throw new RuntimeException("批量预测服务调用失败");
        }
    }

    /**
     * 算法引擎健康检查（修复方案 5.7）。
     *
     * <p>探测 FastAPI {@code GET /health/}（无鉴权），带响应超时、
     * 有限重试（默认 2 次）与熔断：连续失败达到阈值后短期内直接返回 DOWN，
     * 避免每次请求都等待超时。
     */
    public AlgorithmHealthResponse health() {
        long start = System.currentTimeMillis();

        if (!circuitBreaker.allow()) {
            log.warn("熔断器开启，跳过算法引擎探测");
            return AlgorithmHealthResponse.builder()
                    .status("DOWN")
                    .message("熔断器开启，暂不探测算法引擎")
                    .checkedAt(LocalDateTime.now())
                    .build();
        }

        Exception lastError = null;
        int attempts = 0;
        while (attempts <= healthRetries) {
            attempts++;
            try {
                Map<String, Object> body = fastApiWebClient.get()
                        .uri("/health/")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                        })
                        .timeout(Duration.ofMillis(healthTimeoutMs))
                        .block();

                circuitBreaker.recordSuccess();
                long latency = System.currentTimeMillis() - start;
                log.info("算法引擎健康检查成功: latencyMs={}", latency);
                return AlgorithmHealthResponse.builder()
                        .status("UP")
                        .fastapiStatus(body == null ? null : String.valueOf(body.get("status")))
                        .latencyMs(latency)
                        .message("算法引擎健康")
                        .checkedAt(LocalDateTime.now())
                        .build();
            } catch (Exception e) {
                lastError = e;
                log.warn("算法引擎健康检查失败(第{}次): {}", attempts, e.getMessage());
                if (attempts <= healthRetries) {
                    try {
                        Thread.sleep(100L * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        circuitBreaker.recordFailure();
        return AlgorithmHealthResponse.builder()
                .status("DOWN")
                .latencyMs(System.currentTimeMillis() - start)
                .message("算法引擎不可用: " + (lastError == null ? "未知错误" : lastError.getMessage()))
                .checkedAt(LocalDateTime.now())
                .build();
    }
}