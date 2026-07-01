package com.test.qa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置
 */
@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("企业级AI知识库RAG问答与自动化评测平台")
                        .version("1.0.0")
                        .description("提示词工程、RAG检索增强、LLM-as-Judge评测、消融实验、微调服务联动"));
    }
}
