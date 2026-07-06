package com.test.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 客户端配置
 * Spring AI 1.0.0-M6 不会自动创建 ChatClient Bean，需手动注册
 *
 * Spring AI M6 的 OpenAiApi 通过注入 RestClient.Builder 来构造请求客户端，
 * 此处提供带超时的 Builder bean 确保聊天调用不会被默认 30s 截断。
 */
@Configuration
public class AIConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 提供带超时的 RestClient.Builder，connect 30s / read 600s。
     * 同时被 Spring AI OpenAiApi 和 Spring Boot 其他 RestClient 使用者共享。
     * read 超时设为 600s 以匹配 AsyncContext 的 10 分钟超时，避免长 LLM 推理被截断。
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(600));
        return RestClient.builder().requestFactory(factory);
    }
}
