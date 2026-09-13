package com.synpharm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 外部数据源 HTTP 客户端配置（修复方案 5.4）。
 *
 * <p>为 UniProt / RCSB PDB 解析提供带连接/读取超时的 RestClient，
 * 与 FastAPI 使用的 WebClient 隔离（FastAPI 走内网、外部分辨走公网，超时策略不同）。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Configuration
public class ExternalApiConfig {

    @Value("${prediction.resolver.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${prediction.resolver.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public RestClient externalRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().requestFactory(factory).build();
    }
}
