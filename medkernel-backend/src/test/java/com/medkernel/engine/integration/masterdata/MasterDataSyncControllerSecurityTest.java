package com.medkernel.engine.integration.masterdata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MasterDataSyncControllerSecurityTest {

    private static final String SYNC_BODY = """
        {
          "batchId": "batch-1",
          "adapterId": "his-adapter",
          "sourceSystem": "HIS",
          "mode": "INCREMENTAL",
          "cursor": "cursor-1",
          "items": []
        }
        """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MasterDataSyncService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void signedSyncEndpointDoesNotRequirePlatformJwt() throws Exception {
        when(service.sync(
            eq("tenant-1"), eq("his-master-data"), eq("1780456123"),
            eq("sha256=test"), any()))
            .thenReturn(new MasterDataSyncResponse(
                "batch-1", "HIS", "cursor-1", MasterDataSyncStatus.SUCCESS,
                0, 0, 0, false, Instant.now(), "trace-sync", List.of()));

        mvc.perform(post("/api/v1/engine/integration/master-data/{webhookId}/sync",
                "his-master-data")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-MedKernel-Tenant", "tenant-1")
                .header("X-MedKernel-Timestamp", "1780456123")
                .header("X-MedKernel-Signature", "sha256=test")
                .content(SYNC_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void reconciliationRejectsAnonymousCaller() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/master-data/reconciliation")
                .param("sourceSystem", "HIS"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void integrationOperatorCanReadTenantReconciliation() throws Exception {
        when(service.reconciliation("HIS")).thenReturn(
            new MasterDataReconciliationResponse(
                "HIS", "batch-1", "cursor-1", Instant.now(), List.of()));

        mvc.perform(get("/api/v1/engine/integration/master-data/reconciliation")
                .param("sourceSystem", "HIS")
                .with(jwt().jwt(token -> token
                    .subject("integration-operator")
                    .claim("tenant_id", "tenant-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_INTEGRATION_OPERATOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sourceSystem").value("HIS"));
    }
}
