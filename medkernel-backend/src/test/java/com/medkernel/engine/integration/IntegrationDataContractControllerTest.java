package com.medkernel.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import com.medkernel.engine.integration.controller.IntegrationController;
import com.medkernel.engine.integration.dto.IntegrationDataContractResponse;
import com.medkernel.engine.integration.service.IntegrationDataContractService;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;

class IntegrationDataContractControllerTest {

    private final IntegrationService integrationService = mock(IntegrationService.class);
    private final AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
    private final IsolatedAuditPublisher isolatedAuditPublisher = mock(IsolatedAuditPublisher.class);
    private final IntegrationDataContractService dataContractService = mock(IntegrationDataContractService.class);
    private final IntegrationController controller = new IntegrationController(
        integrationService, auditEventPublisher, isolatedAuditPublisher, dataContractService);

    @Test
    void dataContractRouteDelegatesToVersionedContractService() {
        var response = new IntegrationDataContractResponse(
            "context-field-contract:pkg-2026.06",
            "pkg-2026.06",
            "medkernel.context-field-contract.v1",
            List.of("接入说明"),
            Map.of(),
            List.of());
        when(dataContractService.generate("pkg-2026.06")).thenReturn(response);

        assertThat(controller.getDataContract("pkg-2026.06").data()).isEqualTo(response);

        verify(dataContractService).generate("pkg-2026.06");
    }

    @Test
    void declaresIntegrationDataContractRoute() throws Exception {
        Method method = IntegrationController.class.getMethod("getDataContract", String.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/data-contract");
    }
}
