package com.synpharm.pipeline.resolve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.exception.PredictionErrorCode;
import com.synpharm.exception.PredictionException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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

class PdbResolverTest {

    private static final String VALID_SEQUENCE =
            "MKWVTFISLLFLFSSAYSRGVFRRDTHKSEIAHRFKDLGEENFKALVLIAFAQYLQQCPFEDHVKLVNEVTEFAKTCVADESAENCDKSLHTLFGDKLCTVA";

    private MockWebServer server;
    private PdbResolver resolver;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path == null) {
                    return new MockResponse().setResponseCode(404);
                }
                if (path.startsWith("/rest/v1/core/entry/")) {
                    if (path.contains("9XXX")) {
                        return new MockResponse().setResponseCode(404).setBody("{}");
                    }
                    return new MockResponse().setBody("{}");
                }
                if (path.startsWith("/rest/v1/core/polymer_entity_instance/")) {
                    if (path.endsWith("/B")) {
                        // 链 B 不存在
                        return new MockResponse().setResponseCode(404).setBody("{}");
                    }
                    return new MockResponse().setBody(
                            "{\"rcsb_polymer_entity_instance_container_identifiers\":{\"entity_id\":\"1\"}}");
                }
                if (path.startsWith("/rest/v1/core/polymer_entity/")) {
                    return new MockResponse().setBody(
                            "{\"entity_poly\":{\"pdbx_seq_one_letter_code_can\":\"" + VALID_SEQUENCE + "\"}}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);
        RestClient restClient = RestClient.builder().requestFactory(factory).build();

        resolver = new PdbResolver(restClient, redisTemplate, new ObjectMapper(), new ProteinSequenceValidator());
        ReflectionTestUtils.setField(resolver, "rcsbBaseUrl", server.url("/").toString().replaceAll("/$", ""));
        ReflectionTestUtils.setField(resolver, "cacheTtlHours", 24L);
        ReflectionTestUtils.setField(resolver, "negativeCacheTtlMinutes", 10L);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 解析成功_返回链序列并写缓存() {
        String sequence = resolver.resolve("6M0J:A");
        assertEquals(VALID_SEQUENCE, sequence);
        verify(valueOperations).set(eq("synpharm:pdb:6M0J:A"), eq(VALID_SEQUENCE), any(Duration.class));
    }

    @Test
    void 链省略时默认链A() {
        assertEquals(VALID_SEQUENCE, resolver.resolve("6M0J"));
    }

    @Test
    void 大小写与点号分隔兼容() {
        assertEquals(VALID_SEQUENCE, resolver.resolve("6m0j.a"));
    }

    @Test
    void 多链按顺序拼接() {
        String merged = resolver.resolve("6M0J:A,A");
        assertEquals(VALID_SEQUENCE + VALID_SEQUENCE, merged);
    }

    @Test
    void 结构不存在_抛出PDB_NOT_FOUND() {
        // 9XXX 格式合法（首位数字）但结构不存在
        PredictionException e = assertThrows(PredictionException.class, () -> resolver.resolve("9XXX:A"));
        assertEquals(PredictionErrorCode.PDB_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void 链不存在_抛出CHAIN_NOT_FOUND并写负缓存() {
        PredictionException e = assertThrows(PredictionException.class, () -> resolver.resolve("6M0J:B"));
        assertEquals(PredictionErrorCode.CHAIN_NOT_FOUND, e.getErrorCode());
        verify(valueOperations).set(eq("synpharm:pdb:missing:6M0J:B"), eq("1"), any(Duration.class));
    }

    @Test
    void 非法PDB格式_抛出INPUT_RESOLVE_FAILED() {
        PredictionException e = assertThrows(PredictionException.class, () -> resolver.resolve("ABCD"));
        assertEquals(PredictionErrorCode.INPUT_RESOLVE_FAILED, e.getErrorCode());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void 缓存命中_不发网络请求() {
        when(valueOperations.get("synpharm:pdb:6M0J:A")).thenReturn(VALID_SEQUENCE);
        assertEquals(VALID_SEQUENCE, resolver.resolve("6M0J:A"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void 静态判断PDB引用格式() {
        assertTrue(PdbResolver.looksLikePdbRef("6M0J"));
        assertTrue(PdbResolver.looksLikePdbRef("6M0J:A"));
        assertTrue(PdbResolver.looksLikePdbRef("6m0j.a"));
        assertFalse(PdbResolver.looksLikePdbRef("P12345"));
        assertFalse(PdbResolver.looksLikePdbRef("ABCD"));
        assertFalse(PdbResolver.looksLikePdbRef(VALID_SEQUENCE));
        assertFalse(PdbResolver.looksLikePdbRef(null));
    }
}
