package com.test.qa.config;

import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * ChromaDB REST API 客户端配置
 * ChromaDB 通过 HTTP REST 接口交互，使用 WebClient 调用
 */
@Slf4j
@Configuration
public class ChromaClientConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 30;
    private static final int CONNECTION_POOL_MAX_CONNECTIONS = 50;
    private static final int CONNECTION_POOL_MAX_IDLE_SECONDS = 60;
    private static final int CONNECTION_POOL_MAX_LIFETIME_SECONDS = 300;

    @Value("${chromadb.host:localhost}")
    private String host;

    @Value("${chromadb.port:8000}")
    private int port;

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("chromadb.host must not be empty");
        }
    }

    @Bean
    public WebClient chromaWebClient() {
        String baseUrl = String.format("http://%s:%d", host, port);
        log.info("ChromaDB WebClient initialized: {}", baseUrl);
        ConnectionProvider connectionProvider = ConnectionProvider.builder("chroma-pool")
                .maxConnections(CONNECTION_POOL_MAX_CONNECTIONS)
                .maxIdleTime(Duration.ofSeconds(CONNECTION_POOL_MAX_IDLE_SECONDS))
                .maxLifeTime(Duration.ofSeconds(CONNECTION_POOL_MAX_LIFETIME_SECONDS))
                .build();
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
