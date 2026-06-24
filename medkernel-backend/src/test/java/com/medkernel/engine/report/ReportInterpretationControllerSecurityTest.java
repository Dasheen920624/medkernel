package com.medkernel.engine.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 医技报告解读 API 安全/契约：认证、recommendation.write 权限、@DataScope 租户门与空态说明。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ReportInterpretationControllerSecurityTest {

    private static final String PATH = "/api/v1/engine/recommendations/report-interpretation";
    private static final String BODY = "{\"contextSnapshotId\":\"snap-report\"}";

    @Autowired
    MockMvc mvc;

    @MockBean
    ReportInterpretationService service;

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void auditorForbiddenWithoutRecommendationWrite() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestForbidden() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void clinicalUserCanReachButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void clinicalUserWithTenantGetsReportInterpretation() throws Exception {
        when(service.interpret(any())).thenReturn(new ReportInterpretationResponse(
            "snap-report",
            "runtime-release-report",
            List.of(new ReportInterpretationItem(
                "report-k-1",
                "LAB.POTASSIUM",
                "血钾 6.3 mmol/L，危急值，已复核",
                "LAB.POTASSIUM",
                "血钾检验说明书",
                21L,
                "v1.0",
                true,
                "已签发报告结合当前机构生效版本生成辅助解读。",
                List.of("血钾升高", "危急值"),
                List.of("请按本机构危急值闭环完成人工确认、回报和记录，系统不自动修改报告。"))),
            ReportInterpretationService.ADVISORY_PRESENT,
            "trace-report"));

        mvc.perform(post(PATH).with(tenantJwt("ROLE_CLINICAL_USER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.runtimeReleaseId").value("runtime-release-report"))
            .andExpect(jsonPath("$.data.interpretations[0].itemName").value("血钾检验说明书"))
            .andExpect(jsonPath("$.data.interpretations[0].criticalRisk").value(true))
            .andExpect(jsonPath("$.data.advisoryNote").value(org.hamcrest.Matchers.containsString("不改写已签发报告")));
    }

    @Test
    void emptyStateReturnsAdvisoryNotRiskExclusion() throws Exception {
        when(service.interpret(any())).thenReturn(new ReportInterpretationResponse(
            "snap-report",
            "runtime-release-report",
            List.of(),
            ReportInterpretationService.ADVISORY_EMPTY,
            "trace-report"));

        mvc.perform(post(PATH).with(tenantJwt("ROLE_CLINICAL_USER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.interpretations").isEmpty())
            .andExpect(jsonPath("$.data.advisoryNote").value(org.hamcrest.Matchers.containsString("不是排除异常或风险")));
    }

    private static RequestPostProcessor tenantJwt(String role) {
        return jwt().jwt(token -> token.subject("doctor-1").claim("tenant_id", "t-1"))
            .authorities(new SimpleGrantedAuthority(role));
    }
}
