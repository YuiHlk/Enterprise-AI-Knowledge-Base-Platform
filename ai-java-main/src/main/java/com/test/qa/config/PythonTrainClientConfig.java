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
 * Python微调服务 HTTP 客户端配置
 * Java通过此WebClient调用Python FastAPI服务接口
 *
 * 架构说明：
 * - Java负责业务调度（任务发起、状态查询、结果持久化）
 * - Python负责模型训练（数据清洗、QLoRA微调、权重导出）
 * - 两者通过HTTP REST接口解耦，可独立部署和扩展
 */
@Slf4j
@Configuration
public class PythonTrainClientConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 60;
    private static final int CONNECTION_POOL_MAX_CONNECTIONS = 50;
    private static final int CONNECTION_POOL_MAX_IDLE_SECONDS = 60;
    private static final int CONNECTION_POOL_MAX_LIFETIME_SECONDS = 300;

    @Value("${python-train.base-url:http://localhost:8002}")
    private String baseUrl;

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("python-train.base-url must not be empty");
        }
    }

    @Bean
    public WebClient pythonTrainWebClient() {
        log.info("Python Train WebClient initialized: {}", baseUrl);
        ConnectionProvider connectionProvider = ConnectionProvider.builder("python-train-pool")
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
