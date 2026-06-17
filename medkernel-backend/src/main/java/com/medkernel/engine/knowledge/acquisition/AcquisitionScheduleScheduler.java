package com.medkernel.engine.knowledge.acquisition;

import java.time.Instant;

import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;

/**
 * AIK-STD-14 公域资料自动获取动态调度器，扫描间隔由配置中心热读取。
 */
@Component
public class AcquisitionScheduleScheduler implements SchedulingConfigurer {

    private final AcquisitionScheduleWorker worker;
    private final SystemConfigService configService;

    public AcquisitionScheduleScheduler(AcquisitionScheduleWorker worker,
                                        SystemConfigService configService) {
        this.worker = worker;
        this.configService = configService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(worker::pollOnce, this::nextExecution);
    }

    private Instant nextExecution(TriggerContext context) {
        Instant anchor = context.lastCompletion() == null ? Instant.now() : context.lastCompletion();
        return anchor.plusMillis(configService.runtimeKnowledgeAcquisitionScheduleIntervalMs());
    }
}
