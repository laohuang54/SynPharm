package com.synpharm.pipeline.resolve;

import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UniProtResolverTest {

    private static final String VALID_SEQUENCE =
            "MKWVTFISLLFLFSSAYSRGVFRRDTHKSEIAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVADESAENCDKSLHTLFGDKLCTVA";

    private MockWebServer server;
    private UniProtResolver resolver;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);
        RestClient restClient = RestClient.builder().requestFactory(factory).build();

        resolver = new UniProtResolver(restClient, redisTemplate, new ProteinSequenceValidator());
        ReflectionTestUtils.setField(resolver, "uniprotBaseUrl", server.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(resolver, "cacheTtlHours", 24L);
        ReflectionTestUtils.setField(resolver, "negativeCacheTtlMinutes", 10L);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 解析成功_返回FASTA序列并写入缓存() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(">sp|P12345|TEST Protein\n" + VALID_SEQUENCE + "\n"));

        String sequence = resolver.resolve("p12345");

        assertEquals(VALID_SEQUENCE, sequence);
        // 正缓存写入（key 大写）
        verify(valueOperations).set(eq("synpharm:uniprot:P12345"), eq(VALID_SEQUENCE), any(Duration.class));
    }

    @Test
    void 条目不存在404_抛出UNIPROT_NOT_FOUND并写负缓存() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));

        PredictionException e = assertThrows(PredictionException.class, () -> resolver.resolve("P12345"));

        assertEquals(PredictionErrorCode.UNIPROT_NOT_FOUND, e.getErrorCode());
        verify(valueOperations).set(eq("synpharm:uniprot:missing:P12345"), eq("1"), any(Duration.class));
    }

    @Test
    void 正缓存命中_不发网络请求() {
        when(valueOperations.get("synpharm:uniprot:P12345")).thenReturn(VALID_SEQUENCE);

        String sequence = resolver.resolve("P12345");

        assertEquals(VALID_SEQUENCE, sequence);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void 负缓存命中_直接抛UNIPROT_NOT_FOUND() {
        when(valueOperations.get("synpharm:uniprot:P12345")).thenReturn(null);
        when(valueOperations.get("synpharm:uniprot:missing:P12345")).thenReturn("1");

        PredictionException e = assertThrows(PredictionException.class, () -> resolver.resolve("P12345"));

        assertEquals(PredictionErrorCode.UNIPROT_NOT_FOUND, e.getErrorCode());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void 非法ID格式_抛出INPUT_RESOLVE_FAILED() {
        PredictionException e = assertThrows(PredictionException.class, () -> resolver.resolve("ABCDEF"));
        assertEquals(PredictionErrorCode.INPUT_RESOLVE_FAILED, e.getErrorCode());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void 序列过短_抛出SEQUENCE_TOO_SHORT() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(">sp|P12345|X\nMGLGLGQ\n"));

        PredictionException e = assertThrows(PredictionException.class, () -> resolver.resolve("P12345"));
        assertEquals(PredictionErrorCode.SEQUENCE_TOO_SHORT, e.getErrorCode());
    }

    @Test
    void Redis故障时降级直连() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(">sp|P12345|X\n" + VALID_SEQUENCE + "\n"));

        // safeGet/safeSet 内吞掉 Redis 异常，仍能通过直连返回序列
        String sequence = resolver.resolve("P12345");
        assertEquals(VALID_SEQUENCE, sequence);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void 静态判断UniProtID格式() {
        assertTrue(UniProtResolver.isUniProtId("P12345"));
        assertTrue(UniProtResolver.isUniProtId("P0DTC2"));
        assertTrue(UniProtResolver.isUniProtId("Q8N0W4"));
        assertFalse(UniProtResolver.isUniProtId("6M0J"));
        assertFalse(UniProtResolver.isUniProtId("ABCDEF"));
        assertFalse(UniProtResolver.isUniProtId(VALID_SEQUENCE));
        assertFalse(UniProtResolver.isUniProtId(null));
    }
}
