package com.medkernel.engine.datasvc.export;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 引擎数据服务层导出后台执行器配置。
 *
 * <p>单机 {@link ThreadPoolTaskExecutor}（2~5 线程，队列 100）；产线规模上来后切换分布式队列时
 * {@link EngineDataExportService} 不需改动，只替换该 Executor bean。
 */
@Configuration
public class EngineDataExportAsyncConfig {

    @Bean(name = "engineDataExportExecutor")
    public Executor engineDataExportExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(5);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("engine-data-export-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }
}
