package com.synpharm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.dto.response.AlgorithmHealthResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FastApiClientHealthTest {

    private MockWebServer server;
    private FastApiClient client;
    private SimpleCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        circuitBreaker = new SimpleCircuitBreaker();
        ReflectionTestUtils.setField(circuitBreaker, "failureThreshold", 2);
        ReflectionTestUtils.setField(circuitBreaker, "openMillis", 60000L);

        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        client = new FastApiClient(webClient, Duration.ofSeconds(5), Duration.ofSeconds(5), circuitBreaker, new ObjectMapper());
        ReflectionTestUtils.setField(client, "healthTimeoutMs", 2000L);
        ReflectionTestUtils.setField(client, "healthRetries", 2);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 引擎健康_返回UP() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"healthy\",\"service\":\"SynPharm AI Prediction Engine\"}"));

        AlgorithmHealthResponse response = client.health();

        assertEquals("UP", response.getStatus());
        assertEquals("healthy", response.getFastapiStatus());
        assertNotNull(response.getLatencyMs());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void 引擎不可用_有限重试后返回DOWN() {
        // 首次 + 2 次重试 = 3 次请求全部 500
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        AlgorithmHealthResponse response = client.health();

        assertEquals("DOWN", response.getStatus());
        assertEquals(3, server.getRequestCount());
        // 失败已计入熔断器
        assertFalse(circuitBreaker.isOpen()); // 阈值 2 只记录了一次探测周期，未达连续失败阈值
    }

    @Test
    void 连续失败达到阈值_熔断开启后直接返回DOWN且不探测() {
        // 第一次探测：3 次请求全失败 → 记 1 次失败
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        client.health();

        // 第二次探测：记第 2 次连续失败 → 达到阈值，熔断开启
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        client.health();

        assertTrue(circuitBreaker.isOpen());

        // 第三次探测：熔断开启，直接返回 DOWN，不再发请求
        int requestsBefore = server.getRequestCount();
        AlgorithmHealthResponse response = client.health();
        assertEquals("DOWN", response.getStatus());
        assertEquals(requestsBefore, server.getRequestCount());
    }

    @Test
    void 熔断开启期内快速返回_不计入重试() {
        // 直接开启熔断
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.isOpen());

        AlgorithmHealthResponse response = client.health();

        assertEquals("DOWN", response.getStatus());
        assertEquals(0, server.getRequestCount());
    }
}
