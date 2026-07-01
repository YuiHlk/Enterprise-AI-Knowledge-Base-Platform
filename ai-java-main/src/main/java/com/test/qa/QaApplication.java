package com.test.qa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 企业级AI知识库RAG问答与自动化评测平台 - 主启动类
 */
@SpringBootApplication
@EnableAsync
public class QaApplication {

    public static void main(String[] args) {
        SpringApplication.run(QaApplication.class, args);
    }

}
