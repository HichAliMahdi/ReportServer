package com.reportserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread-pool configuration for asynchronous report generation.
 *
 * Properties (all overridable via application.properties / env vars):
 *   reportserver.async.core-pool-size   (default 2)
 *   reportserver.async.max-pool-size    (default 5)
 *   reportserver.async.queue-capacity   (default 50)
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Value("${reportserver.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${reportserver.async.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${reportserver.async.queue-capacity:50}")
    private int queueCapacity;

    public static final String REPORT_EXECUTOR = "reportGenerationExecutor";

    @Bean(name = REPORT_EXECUTOR)
    public Executor reportGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("report-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
