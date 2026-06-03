package com.medkernel.engine.integration.service;

import org.springframework.stereotype.Component;

import com.medkernel.engine.integration.dto.IntegrationHealthProbeResultDto;

/**
 * 第三方适配器健康探测 worker，供周期调度器触发单轮探测。
 */
@Component
public class IntegrationAdapterHealthProbeWorker {

    private final IntegrationService integrationService;

    public IntegrationAdapterHealthProbeWorker(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    public IntegrationHealthProbeResultDto probeOnce() {
        return integrationService.probeActiveAdapterHealth();
    }
}
