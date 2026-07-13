package com.transfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 事故 AI 后台任务线程池。
 *
 * <p>当前 YOLOv5 使用 CPU 推理，默认只并行处理 1 个事故，
 * 其余任务进入队列，避免多个视频同时推理导致机器负载过高。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("incidentPredictionExecutor")
    public Executor incidentPredictionExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("incident-prediction-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }
}
