package com.test.qa.config;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Ollama 本地 Embedding 服务客户端配置
 *
 * Ollama 是本地部署的大模型推理引擎，运行在 VM 上，
 * 提供免费的 Embedding API（如 bge-m3），替代 DeepSeek 缺失的向量化能力。
 *
 * 超时说明：bge-m3 单次 embedding 通常在 2-5s，但并发场景需留足余量。
 */
@Slf4j
@Configuration
public class OllamaConfig {

    @Value("${ollama.embedding.url:http://localhost:11434}")
    private String embeddingUrl;

    @Bean
    public WebClient ollamaWebClient() {
        log.info("Ollama Embedding WebClient initialized: {}", embeddingUrl);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
                .responseTimeout(Duration.ofSeconds(60));
        return WebClient.builder()
                .baseUrl(embeddingUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
