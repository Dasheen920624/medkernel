package com.medkernel.engine.emrlevel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EmrLevelControllerSecurityTest {

    private static final String TARGET_BODY = """
        {
          "hospitalOrgId": "hospital-A",
          "targetLevel": 5,
          "standardVersion": "EMR-RATING-2026",
          "items": [
            {
              "itemCode": "EMR-5-002",
              "itemName": "五级质控闭环",
              "requiredLevel": 5,
              "capabilityCode": "QUALITY_RECTIFICATION",
              "capabilityName": "质控整改闭环能力",
              "capabilityStatus": "GAP",
              "evidenceSummary": "未接入真实质控闭环证据",
              "responsibleDepartmentId": "dept-quality",
              "dueAt": "2026-06-15T00:00:00Z"
            }
          ]
        }
        """;

    @Autowired MockMvc mvc;

    @MockBean EmrLevelService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedCannotReadEmrLevelTargets() throws Exception {
        mvc.perform(get("/api/v1/engine/emr-level/targets")
                .param("hospitalOrgId", "hospital-A")
                .param("standardVersion", "EMR-RATING-2026"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void qualityManagerCanUpsertReadGapsAndProgress() throws Exception {
        EmrLevelGapResponse gap = new EmrLevelGapResponse(
            "gap-1", "EMR-5-002", "五级质控闭环", "QUALITY_RECTIFICATION",
            EmrLevelCapabilityStatus.GAP, "未接入真实质控闭环证据", "rct-emr-1", "trace-emr");
        EmrLevelTargetResponse target = new EmrLevelTargetResponse(
            "target-1", "hospital-A", 5, "EMR-RATING-2026",
            EmrLevelTargetStatus.ACTIVE, 1, 0, 1, new BigDecimal("0.0000"),
            List.of(gap), "trace-emr");
        EmrLevelProgressResponse progress = new EmrLevelProgressResponse(
            "target-1", "hospital-A", 5, "EMR-RATING-2026", 1, 0, 1, 1,
            new BigDecimal("0.0000"), "trace-emr");
        when(service.upsertTarget(any())).thenReturn(target);
        when(service.target("hospital-A", "EMR-RATING-2026")).thenReturn(target);
        when(service.gaps("hospital-A", "EMR-RATING-2026")).thenReturn(List.of(gap));
        when(service.progress(eq("hospital-A"), eq("EMR-RATING-2026"))).thenReturn(progress);

        mvc.perform(put("/api/v1/engine/emr-level/targets")
                .contentType("application/json")
                .content(TARGET_BODY)
                .with(qaJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetId").value("target-1"))
            .andExpect(jsonPath("$.data.gapItems").value(1));

        mvc.perform(get("/api/v1/engine/emr-level/targets")
                .param("hospitalOrgId", "hospital-A")
                .param("standardVersion", "EMR-RATING-2026")
                .with(qaJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetLevel").value(5));

        mvc.perform(get("/api/v1/engine/emr-level/gaps")
                .param("hospitalOrgId", "hospital-A")
                .param("standardVersion", "EMR-RATING-2026")
                .with(qaJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].rectificationTaskId").value("rct-emr-1"));

        mvc.perform(get("/api/v1/engine/emr-level/progress")
                .param("hospitalOrgId", "hospital-A")
                .param("standardVersion", "EMR-RATING-2026")
                .with(qaJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.openGapItems").value(1));
    }

    @Test
    void doctorCannotModifyEmrLevelTarget() throws Exception {
        mvc.perform(put("/api/v1/engine/emr-level/targets")
                .contentType("application/json")
                .content(TARGET_BODY)
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isForbidden());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor qaJwt() {
        return jwt().jwt(token -> token
            .subject("qa-1")
            .claim("tenant_id", "tenant-A")
            .claim("roles", List.of("qa-manager")))
            .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"));
    }
}
