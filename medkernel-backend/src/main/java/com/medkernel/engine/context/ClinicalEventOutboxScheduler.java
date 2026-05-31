package com.medkernel.engine.context;

import java.time.Instant;

import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;

/**
 * 临床事件 outbox 动态调度器，轮询间隔由配置中心热读取。
 */
@Component
public class ClinicalEventOutboxScheduler implements SchedulingConfigurer {

    private final ClinicalEventOutboxWorker worker;
    private final ClinicalEventProperties properties;
    private final SystemConfigService configService;

    public ClinicalEventOutboxScheduler(ClinicalEventOutboxWorker worker,
                                        ClinicalEventProperties properties,
                                        SystemConfigService configService) {
        this.worker = worker;
        this.properties = properties;
        this.configService = configService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(worker::pollOnce, this::nextExecution);
    }

    private Instant nextExecution(TriggerContext context) {
        Instant anchor = context.lastCompletion() == null ? Instant.now() : context.lastCompletion();
        return anchor.plusMillis(configService.runtimeClinicalEventWorkerPollIntervalMs(properties));
    }
}
