package com.synpharm.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量级熔断器（修复方案 5.7，手写实现，不引入第三方依赖）。
 *
 * <p>用于算法引擎健康检查：
 * <ul>
 *   <li>关闭态：连续失败达到阈值 {@code fastapi.health-breaker-threshold} 后开启熔断</li>
 *   <li>开启态：开启时长 {@code fastapi.health-breaker-open-ms} 内拒绝探测，快速返回 DOWN</li>
 *   <li>半开：开启时长过后允许一次探测，成功即恢复关闭态</li>
 * </ul>
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class SimpleCircuitBreaker {

    @Value("${fastapi.health-breaker-threshold:5}")
    private int failureThreshold;

    @Value("${fastapi.health-breaker-open-ms:30000}")
    private long openMillis;

    /** 连续失败次数 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** 熔断开启时间戳（0 表示关闭态） */
    private volatile long openedAt = 0;

    /**
     * 是否允许本次探测。熔断开启期内拒绝；到期后转半开，允许一次探测。
     */
    public synchronized boolean allow() {
        if (openedAt != 0) {
            if (System.currentTimeMillis() - openedAt >= openMillis) {
                openedAt = 0;
                consecutiveFailures.set(0);
                log.info("熔断器转入半开状态，允许一次探测");
                return true;
            }
            return false;
        }
        return true;
    }

    /** 记录成功：重置失败计数，熔断恢复关闭态 */
    public synchronized void recordSuccess() {
        consecutiveFailures.set(0);
        openedAt = 0;
    }

    /** 记录失败：连续失败达到阈值则开启熔断 */
    public synchronized void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            openedAt = System.currentTimeMillis();
            log.warn("熔断器开启: 连续失败 {} 次，开启 {} ms", failures, openMillis);
        }
    }

    /** 当前是否处于熔断开启状态 */
    public boolean isOpen() {
        return openedAt != 0 && System.currentTimeMillis() - openedAt < openMillis;
    }
}
