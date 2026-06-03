package com.medkernel.engine.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.integration.service.IntegrationService;
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
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void authenticatedItOpsCanReachGetButFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/adapters"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void healthSummaryFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/health"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IMPLEMENTATION_ENGINEER")
    void authenticatedEngineerCanReachCreateButFailsOnMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/adapters")
                .contentType("application/json")
                .content(ADAPTER_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
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
    @WithMockUser(authorities = "ROLE_IT_OPS")
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
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void logsListFailsOnMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/logs"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
