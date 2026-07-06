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
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Ollama 本地 Embedding 服务客户端配置
 * 超时说明：bge-m3 单次 embedding 通常在 2-5s，但并发场景需留足余量。
 */
@Slf4j
@Configuration
public class OllamaConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 60;
    private static final int CONNECTION_POOL_MAX_CONNECTIONS = 50;
    private static final int CONNECTION_POOL_MAX_IDLE_SECONDS = 60;
    private static final int CONNECTION_POOL_MAX_LIFETIME_SECONDS = 300;

    @Value("${ollama.embedding.url:http://localhost:11434}")
    private String embeddingUrl;

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(embeddingUrl)) {
            throw new IllegalStateException("ollama.embedding.url must not be empty");
        }
    }

    @Bean
    public WebClient ollamaWebClient() {
        log.info("Ollama Embedding WebClient initialized: {}", embeddingUrl);
        ConnectionProvider connectionProvider = ConnectionProvider.builder("ollama-pool")
                .maxConnections(CONNECTION_POOL_MAX_CONNECTIONS)
                .maxIdleTime(Duration.ofSeconds(CONNECTION_POOL_MAX_IDLE_SECONDS))
                .maxLifeTime(Duration.ofSeconds(CONNECTION_POOL_MAX_LIFETIME_SECONDS))
                .build();
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .protocol(HttpProtocol.HTTP11)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS));
        return WebClient.builder()
                .baseUrl(embeddingUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
