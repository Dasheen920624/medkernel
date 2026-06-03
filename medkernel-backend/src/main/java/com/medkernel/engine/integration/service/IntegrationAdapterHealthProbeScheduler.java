package com.medkernel.engine.integration.service;

import java.time.Instant;

import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;

/**
 * 第三方适配器周期健康探测动态调度器，探测间隔由配置中心热读取。
 */
@Component
public class IntegrationAdapterHealthProbeScheduler implements SchedulingConfigurer {

    private final IntegrationAdapterHealthProbeWorker worker;
    private final IntegrationProperties properties;
    private final SystemConfigService configService;

    public IntegrationAdapterHealthProbeScheduler(IntegrationAdapterHealthProbeWorker worker,
                                                  IntegrationProperties properties,
                                                  SystemConfigService configService) {
        this.worker = worker;
        this.properties = properties;
        this.configService = configService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(worker::probeOnce, this::nextExecution);
    }

    private Instant nextExecution(TriggerContext context) {
        Instant anchor = context.lastCompletion() == null ? Instant.now() : context.lastCompletion();
        return anchor.plusMillis(configService.runtimeIntegrationHealthProbeIntervalMs(properties));
    }
}
