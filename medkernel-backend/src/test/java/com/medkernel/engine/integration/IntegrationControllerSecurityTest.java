package com.medkernel.engine.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.integration.dto.IntegrationOnboardingResponse;
import com.medkernel.engine.integration.dto.RegionalSourceResponse;
import com.medkernel.engine.integration.dto.WebhookConfigResponse;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IntegrationControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private IntegrationService service;

    private static final String ADAPTER_BODY = "{\"adapterId\":\"adp-9\",\"name\":\"HIS连接\",\"protocolType\":\"HL7\",\"configJson\":\"{}\"}";
    private static final String WEBHOOK_BODY = "{\"webhookId\":\"whk-9\",\"name\":\"诊断订阅\",\"callbackUrl\":\"http://domain/cb\",\"eventsSubscribed\":\"OUTPATIENT_DIAGNOSIS\"}";
    private static final String INBOUND_BODY = """
        {"messageId":"msg-9","traceId":"trace-9","adapterId":"adp-9","sourceSystem":"HIS","eventType":"DIAGNOSIS","payload":{"patientId":"P-9"}}
        """;
    private static final String OUTBOUND_BODY = """
        {"messageId":"out-9","traceId":"trace-out-9","adapterId":"adp-9","targetSystem":"HIS","protocolType":"REST","payloadSummary":"异步同步 HIS","payload":{"patientId":"P-9"},"maxRetries":3}
        """;
    private static final String ONBOARDING_BODY = """
        {"onboardingId":"onb-9","name":"HIS 业务接口","accessMode":"ADAPTER","adapterId":"adp-9","sourceSystem":"HIS","businessScenario":"S2 院内系统接入","orgPath":"/t-1/hospital-a"}
        """;
    private static final String REGIONAL_BODY = """
        {"sourceId":"regional-9","regionalNetworkName":"医联体平台","sourceOrganizationId":"org-region-9","sourceOrganizationName":"区域影像中心","trustLevel":"HIGH","evidenceText":"OPT-07 已分级证据","orgPath":"/t-1/hospital-a"}
        """;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    // ==========================================
    // 1. 未授权拒绝 (401/403)
    // ==========================================

    @Test
    void anonymousCannotReadAdapters() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/adapters"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadAdapterHealth() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/health"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadAdapterHubStatus() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/adapter-hub/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadDataContract() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/data-contract")
                .param("packageVersion", "pkg-2026.06"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotGenerateDataQualityReport() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/data-quality/reports"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotCreateAdapter() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/adapters")
                .contentType("application/json")
                .content(ADAPTER_BODY))
            .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // 2. DataScope 租户校验阻断 (400 - ENG-BASE-001)
    // ==========================================

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void authenticatedItOpsCanReachGetButFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/adapters"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void healthSummaryFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/health"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void adapterHubStatusFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/adapter-hub/status"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void dataContractFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/data-contract")
                .param("packageVersion", "pkg-2026.06"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void dataQualityReportGenerationFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/data-quality/reports"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IMPLEMENTATION_OPERATOR")
    void authenticatedEngineerCanReachCreateButFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/adapters")
                .contentType("application/json")
                .content(ADAPTER_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void webhookCreationFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/webhooks")
                .contentType("application/json")
                .content(WEBHOOK_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void anonymousCannotIngestWebhook() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/webhooks/whk-9/inbound")
                .contentType("application/json")
                .header("X-MedKernel-Timestamp", "1780456123")
                .header("X-MedKernel-Signature", "bad-signature")
                .content(INBOUND_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void inboundWebhookFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/webhooks/whk-9/inbound")
                .contentType("application/json")
                .header("X-MedKernel-Timestamp", "1780456123")
                .header("X-MedKernel-Signature", "bad-signature")
                .content(INBOUND_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void anonymousCannotEnqueueOutboundMessage() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/messages/outbound")
                .contentType("application/json")
                .content(OUTBOUND_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadOnboardings() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/onboardings"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotCreateOnboarding() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/onboardings")
                .contentType("application/json")
                .content(ONBOARDING_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadRegionalSources() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/regional-sources"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotRegisterRegionalSource() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/regional-sources")
                .contentType("application/json")
                .content(REGIONAL_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void outboundMessageFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/messages/outbound")
                .contentType("application/json")
                .content(OUTBOUND_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IMPLEMENTATION_OPERATOR")
    void onboardingCreationFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/onboardings")
                .contentType("application/json")
                .content(ONBOARDING_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void regionalSourceListFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/regional-sources"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void adapterHubMaintenanceListsReturnPagedContractsForTenantOperators() throws Exception {
        when(service.listIntegrationOnboardings(eq("tenant-1"), any(PageRequest.class)))
            .thenReturn(new PageResponse<IntegrationOnboardingResponse>(
                List.of(), 2, 3, 7, true, false));
        when(service.getWebhooks(eq("tenant-1"), any(PageRequest.class)))
            .thenReturn(new PageResponse<WebhookConfigResponse>(
                List.of(), 3, 4, 9, true, false));
        when(service.listRegionalSources(eq("tenant-1"), any(PageRequest.class)))
            .thenReturn(new PageResponse<RegionalSourceResponse>(
                List.of(), 4, 5, 11, true, false));

        mvc.perform(get("/api/v1/engine/integration/onboardings")
                .param("page", "2")
                .param("size", "3")
                .with(ops("tenant-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.size").value(3))
            .andExpect(jsonPath("$.data.total").value(7));

        mvc.perform(get("/api/v1/engine/integration/webhooks")
                .param("page", "3")
                .param("size", "4")
                .with(ops("tenant-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.page").value(3))
            .andExpect(jsonPath("$.data.size").value(4))
            .andExpect(jsonPath("$.data.total").value(9));

        mvc.perform(get("/api/v1/engine/integration/regional-sources")
                .param("page", "4")
                .param("size", "5")
                .with(ops("tenant-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.page").value(4))
            .andExpect(jsonPath("$.data.size").value(5))
            .andExpect(jsonPath("$.data.total").value(11));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void callbackDeadLetterReplayFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/callbacks/dead-letter/msg-9/replay"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void logsListFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/logs"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void deadLetterListFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/dead-letter"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void deadLetterReplayFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/dead-letter/msg-9/replay"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor ops(String tenantId) {
        return jwt().jwt(token -> token
                .subject("integration-operator")
                .claim("tenant_id", tenantId)
                .claim("roles", List.of("integration-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR"));
    }
}
