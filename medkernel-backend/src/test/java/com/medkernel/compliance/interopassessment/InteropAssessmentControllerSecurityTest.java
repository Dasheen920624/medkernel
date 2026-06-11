package com.medkernel.compliance.interopassessment;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.medkernel.shared.context.RequestContext;
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

/**
 * OPT-05 互联互通测评映射控制器安全矩阵。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InteropAssessmentControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean InteropAssessmentService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedCannotReadInteropAssessment() throws Exception {
        mvc.perform(get("/api/v1/compliance/interop-assessment")
                .param("standardVersion", "IOT-2026"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_COMPLIANCE_AUDITOR")
    void auditRoleWithoutTenantIsBlockedByDataScope() throws Exception {
        mvc.perform(get("/api/v1/compliance/interop-assessment")
                .param("standardVersion", "IOT-2026"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void auditRoleWithTenantCanReadAssessmentAndGaps() throws Exception {
        InteropAssessmentItemResponse gap = new InteropAssessmentItemResponse(
            "interop-item-missing", "IOT-2026", InteropAssessmentDimension.STANDARDIZATION,
            "STD-001", "标准化映射缺口", "测评项需映射真实产品证据",
            InteropAssessmentStatus.MISSING_EVIDENCE, 0, false,
            "缺少真实证据映射", List.of(), "trace-interop");
        InteropAssessmentResponse response = new InteropAssessmentResponse(
            "IOT-2026", 1, 0, 1, 1, BigDecimal.ZERO, List.of(gap), "trace-interop");
        when(service.assessment(eq("IOT-2026"))).thenReturn(response);
        when(service.gaps(eq("IOT-2026"))).thenReturn(List.of(gap));

        mvc.perform(get("/api/v1/compliance/interop-assessment")
                .param("standardVersion", "IOT-2026")
                .with(auditJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalItems").value(1))
            .andExpect(jsonPath("$.data.items[0].status").value("MISSING_EVIDENCE"));

        mvc.perform(get("/api/v1/compliance/interop-assessment/gaps")
                .param("standardVersion", "IOT-2026")
                .with(auditJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].itemCode").value("STD-001"));
    }

    @Test
    void doctorCannotReadInteropAssessment() throws Exception {
        mvc.perform(get("/api/v1/compliance/interop-assessment")
                .param("standardVersion", "IOT-2026")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
            .andExpect(status().isForbidden());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auditJwt() {
        return jwt().jwt(token -> token
            .subject("auditor-1")
            .claim("tenant_id", "tenant-A")
            .claim("group_id", "group-A")
            .claim("hospital_id", "hospital-A")
            .claim("department_id", "dept-it")
            .claim("roles", List.of("audit_compliance")))
            .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_AUDITOR"));
    }
}
