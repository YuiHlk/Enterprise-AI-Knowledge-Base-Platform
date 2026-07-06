package com.test.qa.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * 用于评测任务异步执行、消融实验批量任务等
 */
@Configuration
public class AsyncConfig {

    @Value("${async.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.executor.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${async.executor.queue-capacity:100}")
    private int queueCapacity;

    @Value("${async.executor.thread-name-prefix:qa-async-}")
    private String threadNamePrefix;

    @PostConstruct
    public void validate() {
        if (corePoolSize < 1 || maxPoolSize < 1) {
            throw new IllegalStateException(
                    "async.executor pool sizes must be positive: core=" + corePoolSize + " max=" + maxPoolSize);
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalStateException(
                    "async.executor max-pool-size(" + maxPoolSize + ") must be >= core-pool-size(" + corePoolSize + ")");
        }
    }

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        //设置非核心线程空闲销毁时间
        executor.setKeepAliveSeconds(30);
        //指定拒绝策略(池子满了交给 Tomcat 请求线程同步执行)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
