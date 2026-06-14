package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.config.SystemConfigService;

/**
 * 部署形态服务单元测试（LLM-08 双形态）。
 */
class DeploymentFormServiceTest {

    private SystemConfigService configService;
    private DeploymentFormService service;

    @BeforeEach
    void setUp() {
        configService = mock(SystemConfigService.class);
        service = new DeploymentFormService(configService);
    }

    @Test
    void hospitalRuntimeForbidsExternalProvider() {
        when(configService.runtimeDeploymentForm()).thenReturn("HOSPITAL_RUNTIME");

        assertThat(service.currentForm()).isEqualTo(DeploymentForm.HOSPITAL_RUNTIME);
        assertThat(service.allowsExternalProvider()).isFalse();
    }

    @Test
    void productionCenterAllowsExternalProvider() {
        when(configService.runtimeDeploymentForm()).thenReturn("PRODUCTION_CENTER");

        assertThat(service.currentForm()).isEqualTo(DeploymentForm.PRODUCTION_CENTER);
        assertThat(service.allowsExternalProvider()).isTrue();
    }

    @Test
    void unknownOrBlankFormFallsBackToSafestHospitalRuntime() {
        when(configService.runtimeDeploymentForm()).thenReturn("garbage-value");

        assertThat(service.currentForm()).isEqualTo(DeploymentForm.HOSPITAL_RUNTIME);
        assertThat(service.allowsExternalProvider()).isFalse();
    }
}
