package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;

/**
 * 知识退役动态调度器，扫描间隔由配置中心热读取。
 */
@Component
public class KnowledgeRetirementScheduler implements SchedulingConfigurer {

    private final KnowledgeRetirementService retirementService;
    private final SystemConfigService configService;

    public KnowledgeRetirementScheduler(
            KnowledgeRetirementService retirementService,
            SystemConfigService configService) {
        this.retirementService = retirementService;
        this.configService = configService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(retirementService::finalizeDueRetirements, this::nextExecution);
    }

    private Instant nextExecution(TriggerContext context) {
        Instant anchor = context.lastCompletion() == null ? Instant.now() : context.lastCompletion();
        return anchor.plusMillis(configService.runtimeKnowledgeRetirementIntervalMs());
    }
}
