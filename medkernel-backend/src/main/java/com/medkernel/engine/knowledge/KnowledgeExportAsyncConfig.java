package com.medkernel.engine.knowledge;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 知识导出后台执行器配置。
 *
 * <p>当前运行形态使用单机 {@link ThreadPoolTaskExecutor}（5 线程，队列 100）；
 * 客户产线规模上来后由 GA-ENG-API-13（大规模列表/导出 API）切换到分布式队列，
 * 切换时 {@link KnowledgeExportService} 不需要改动，只替换该 Executor bean 即可。
 */
@Configuration
public class KnowledgeExportAsyncConfig {

    @Bean(name = "knowledgeExportExecutor")
    public Executor knowledgeExportExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(5);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("knowledge-export-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();
        return exec;
    }
}
