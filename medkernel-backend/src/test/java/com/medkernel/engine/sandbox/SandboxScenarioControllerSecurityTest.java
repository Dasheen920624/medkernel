package com.medkernel.engine.sandbox;

import java.time.Instant;
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
    private static final String RUNTIME_BINDING_PATH = "/api/v1/engine/sandbox/runtime-binding";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SandboxOrchestrationService service;

    @MockBean
    private SandboxRuntimeBindingService runtimeBindings;

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
                .with(roleJwt("quality-governor", "ROLE_QUALITY_GOVERNOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void clinicalDecisionUserCanReadBackendOwnedScenarioCatalog() throws Exception {
        mockMvc.perform(get(CATALOG_PATH)
                .with(roleJwt("clinical-decision-user", "ROLE_CLINICAL_DECISION_USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(15))
            .andExpect(jsonPath("$.data[0].id").value("sbx-lab-critical-k"))
            .andExpect(jsonPath("$.data[0].input.kind").value("numeric"))
            .andExpect(jsonPath("$.data[0].input.defaultValue").isNumber())
            .andExpect(jsonPath("$.data[0].input.upperReferenceValue").isNumber());
    }

    @Test
    void clinicalDecisionUserCanRunOrdinarySandboxEntry() throws Exception {
        when(service.run(eq("sbx-lab-critical-k"), any())).thenReturn(new SandboxRunResponse(
            "sbx-lab-critical-k", "trace-1", List.of(), null, null, 0, null, null, "PASS"));

        mockMvc.perform(post(RUN_PATH)
                .with(roleJwt("clinical-decision-user", "ROLE_CLINICAL_DECISION_USER"))
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
    void sandboxUserCanReadHonestRuntimeReadinessButCannotActivateBinding() throws Exception {
        when(runtimeBindings.currentStatus()).thenReturn(SandboxRuntimeStatusResponse.notReady(
            "hospital-1", "SANDBOX_RUNTIME_BASELINE_MISSING", "演练机构未激活沙盘运行绑定"));

        mockMvc.perform(get(RUNTIME_BINDING_PATH)
                .with(roleJwt("clinical-decision-user", "ROLE_CLINICAL_DECISION_USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ready").value(false))
            .andExpect(jsonPath("$.data.reasonCode").value("SANDBOX_RUNTIME_BASELINE_MISSING"))
            .andExpect(jsonPath("$.data.externalSideEffects").value(false));

        mockMvc.perform(post(RUNTIME_BINDING_PATH)
                .with(roleJwt("clinical-decision-user", "ROLE_CLINICAL_DECISION_USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"packageOwnerTenantId":"tenant-1","packageId":"pkg-1"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanActivateRuntimeBinding() throws Exception {
        when(runtimeBindings.activate(any())).thenReturn(new SandboxRuntimeStatusResponse(
            true, null, null, "hospital-1", "binding-1", "tenant-1", "pkg-1",
            "PKG.SANDBOX", "7.2.1", SandboxResolutionSource.TENANT_PACKAGE,
            10, List.of(), Instant.parse("2026-06-19T00:00:00Z"), false));

        mockMvc.perform(post(RUNTIME_BINDING_PATH)
                .with(roleJwt("knowledge-governor", "ROLE_KNOWLEDGE_GOVERNOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"packageOwnerTenantId":"tenant-1","packageId":"pkg-1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ready").value(true))
            .andExpect(jsonPath("$.data.packageVersion").value("7.2.1"));
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
