package com.medkernel.engine.safety;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.medkernel.shared.context.RequestContext;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SafetyWithdrawalControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SafetyWithdrawalService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void withdrawWithoutAuthReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/engine/safety/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityId\":1,\"versionId\":5,\"reason\":\"上游召回\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void nurseCannotWithdrawSafetyVersion() throws Exception {
        mockMvc.perform(post("/api/v1/engine/safety/withdrawals")
                .with(jwt().jwt(token -> token
                    .subject("nurse-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityId\":1,\"versionId\":5,\"reason\":\"上游召回\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void engineOperatorCanWithdrawWithTenantContext() throws Exception {
        when(service.withdraw(any())).thenReturn(withdrawalResponse());

        mockMvc.perform(post("/api/v1/engine/safety/withdrawals")
                .with(jwt().jwt(token -> token
                    .subject("ma-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityId\":1,\"versionId\":5,\"reason\":\"上游召回\",\"scope\":\"tenant:tenant-A\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.withdrawalId").value(90))
            .andExpect(jsonPath("$.data.versionStatus").value("WITHDRAWN"));
    }

    @Test
    void withdrawRejectsBlankReasonBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/engine/safety/withdrawals")
                .with(jwt().jwt(token -> token
                    .subject("ma-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityId\":1,\"versionId\":5,\"reason\":\"   \"}"))
            .andExpect(status().isBadRequest());

        verify(service, never()).withdraw(any());
    }

    @Test
    void doctorCanReadImpactWithTenantContext() throws Exception {
        when(service.impact(eq(90L))).thenReturn(impactResponse());

        mockMvc.perform(get("/api/v1/engine/safety/withdrawals/90/impact")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.withdrawalId").value(90))
            .andExpect(jsonPath("$.data.patientCaseCount").value(1));
    }

    @Test
    void doctorCanExportImpactEvidenceWithTenantContext() throws Exception {
        when(service.exportImpactEvidence(eq(90L))).thenReturn("{\"recordType\":\"summary\"}\n");

        mockMvc.perform(get("/api/v1/engine/safety/withdrawals/90/impact/export")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"recordType\":\"summary\"}\n"));
    }

    @Test
    void doctorWithoutTenantCannotReadImpact() throws Exception {
        mockMvc.perform(get("/api/v1/engine/safety/withdrawals/90/impact")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    private SafetyWithdrawalResponse withdrawalResponse() {
        SafetyImpactResponse impact = impactResponse();
        return new SafetyWithdrawalResponse(
            90L, 1L, 5L, "WITHDRAWN", impact, "trace-safety");
    }

    private SafetyImpactResponse impactResponse() {
        return new SafetyImpactResponse(
            90L, 1L, 5L, 1, 1, 1, 3, "sha256:impact", List.of(), "trace-safety");
    }
}
