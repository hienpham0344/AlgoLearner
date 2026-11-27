package com.algolearner.platform.judge;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class JudgeQueueConfiguration {
    @Bean(name = "judgeTaskExecutor")
    Executor judgeTaskExecutor(
            @Value("${app.judge.worker.core-pool-size:2}") int corePoolSize,
            @Value("${app.judge.worker.max-pool-size:4}") int maxPoolSize,
            @Value("${app.judge.worker.queue-capacity:100}") int queueCapacity) {
        if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 1) {
            throw new IllegalArgumentException("Invalid Judge worker pool configuration");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("judge-worker-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
