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
 * ChromaDB REST API 客户端配置
 * ChromaDB 通过 HTTP REST 接口交互，使用 WebClient 调用
 */
@Slf4j
@Configuration
public class ChromaClientConfig {

    @Value("${chromadb.host:localhost}")
    private String host;

    @Value("${chromadb.port:8001}")
    private int port;

    @Bean
    public WebClient chromaWebClient() {
        String baseUrl = String.format("http://%s:%d", host, port);
        log.info("ChromaDB WebClient initialized: {}", baseUrl);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(30));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
