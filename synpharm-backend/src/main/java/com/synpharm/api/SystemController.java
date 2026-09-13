package com.synpharm.api;

import com.synpharm.client.FastApiClient;
import com.synpharm.dto.response.AlgorithmHealthResponse;
import com.synpharm.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理控制器（修复方案 5.7）。
 *
 * <p>算法引擎健康检查：Java 侧主动探测 FastAPI {@code GET /health/}，
 * 带连接/响应超时、有限重试与熔断。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Tag(name = "系统管理", description = "系统级接口（健康检查）")
public class SystemController {

    private final FastApiClient fastApiClient;

    @GetMapping("/algorithm-health")
    @Operation(summary = "算法引擎健康检查", description = "探测 FastAPI 算法引擎 /health/，带超时、有限重试与熔断")
    public Result<AlgorithmHealthResponse> algorithmHealth() {
        return Result.success(fastApiClient.health());
    }
}
