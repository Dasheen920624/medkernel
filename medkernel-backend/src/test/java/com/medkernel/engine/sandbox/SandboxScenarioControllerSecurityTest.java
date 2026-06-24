package com.medkernel.engine.sandbox;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SandboxScenarioControllerSecurityTest {

    private static final String RUN_PATH =
        "/api/v1/engine/sandbox/scenarios/sbx-lab-critical-k/run";
    private static final String CATALOG_PATH = "/api/v1/engine/sandbox/scenarios";
    private static final String RUNTIME_STATUS_PATH = "/api/v1/engine/sandbox/runtime-status";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SandboxOrchestrationService service;

    @MockBean
    private SandboxRuntimeStatusService runtimeStatus;

    @Test
    void unauthenticatedRunReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(RUN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void roleWithoutSandboxPermissionReturnsForbidden() throws Exception {
        mockMvc.perform(post(RUN_PATH)
                .with(roleJwt("auditor", "ROLE_AUDITOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void clinicalUserCanReadBackendOwnedScenarioCatalog() throws Exception {
        mockMvc.perform(get(CATALOG_PATH)
                .with(roleJwt("clinical-user", "ROLE_CLINICAL_USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(15))
            .andExpect(jsonPath("$.data[0].id").value("sbx-lab-critical-k"))
            .andExpect(jsonPath("$.data[0].input.kind").value("numeric"))
            .andExpect(jsonPath("$.data[0].input.defaultValue").isNumber())
            .andExpect(jsonPath("$.data[0].input.upperReferenceValue").isNumber());
    }

    @Test
    void clinicalUserCanRunOrdinarySandboxEntry() throws Exception {
        when(service.run(eq("sbx-lab-critical-k"), any())).thenReturn(new SandboxRunResponse(
            "sbx-lab-critical-k", "trace-1", List.of(), null, null, 0, null, null, "PASS"));

        mockMvc.perform(post(RUN_PATH)
                .with(roleJwt("clinical-user", "ROLE_CLINICAL_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "entryMode": "SNAPSHOT",
                      "parentOrigin": "https://his.hospital.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scenarioId").value("sbx-lab-critical-k"))
            .andExpect(jsonPath("$.data.result").value("PASS"));
    }

    @Test
    void sandboxUserCanReadHonestRuntimeReadinessFromCurrentHospitalRevision() throws Exception {
        when(runtimeStatus.currentStatus()).thenReturn(SandboxRuntimeStatusResponse.notReady(
            "hospital-1", "SANDBOX_RUNTIME_RELEASE_MISSING", "医院尚未发布机构生效版本"));

        mockMvc.perform(get(RUNTIME_STATUS_PATH)
                .with(roleJwt("clinical-user", "ROLE_CLINICAL_USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ready").value(false))
            .andExpect(jsonPath("$.data.reasonCode").value("SANDBOX_RUNTIME_RELEASE_MISSING"))
            .andExpect(jsonPath("$.data.externalSideEffects").value(false));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor roleJwt(
            String roleCode,
            String authority) {
        return jwt().jwt(token -> token
                .subject("sandbox-user")
                .claim("tenant_id", "tenant-1")
                .claim("hospital_id", "hospital-1")
                .claim("roles", List.of(roleCode)))
            .authorities(new SimpleGrantedAuthority(authority));
    }
}
