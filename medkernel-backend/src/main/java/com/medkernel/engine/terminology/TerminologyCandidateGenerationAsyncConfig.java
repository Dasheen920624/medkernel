package com.medkernel.engine.terminology;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 术语候选生成后台执行器配置。
 *
 * <p>B0 采用单机线程池承载确定性候选生成；后续接入分布式队列时只替换该 Executor bean。
 */
@Configuration
public class TerminologyCandidateGenerationAsyncConfig {

    @Bean(name = "terminologyCandidateGenerationExecutor")
    public Executor terminologyCandidateGenerationExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(3);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("term-candidate-gen-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }
}
