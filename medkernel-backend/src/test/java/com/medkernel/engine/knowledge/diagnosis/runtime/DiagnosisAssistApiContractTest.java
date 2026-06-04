package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.medkernel.engine.knowledge.diagnosis.DiagnosisConfidence;
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

/**
 * 鉴别诊断 API 安全/契约：认证、recommendation.write 权限、@DataScope 租户门、候选/空态响应。
 *
 * <p>角色权限取自 DefaultPermissionPolicy：IT_OPS / MEDICAL_AFFAIRS 有 recommendation.write；DOCTOR / GUEST 无。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DiagnosisAssistApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DiagnosisAssistService service;

    private static final String PATH = "/api/v1/engine/recommendations/diagnosis-assist";
    private static final String BODY = "{\"contextSnapshotId\":\"snap-1\"}";

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorForbiddenWithoutRecommendationWrite() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestForbidden() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanReachButDataScopeRejectsMissingTenant() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void itOpsWithTenantGetsRankedCandidates() throws Exception {
        when(service.assist(any())).thenReturn(new DiagnosisAssistResponse(
            List.of(new DiagnosisCandidate(100L, "社区获得性肺炎", "DX.PNEU", DiagnosisConfidence.STRONG,
                List.of("FEVER", "COUGH"), List.of(), List.of(), "A_REGULATION", false, 10L)),
            List.of("LOCALX"), "辅助建议，需医师确认（非自动诊断）。", "trace-dx"));

        mockMvc.perform(post(PATH).with(tenantJwt("ROLE_IT_OPS"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.candidates[0].confidence").value("STRONG"))
            .andExpect(jsonPath("$.data.candidates[0].diagnosisName").value("社区获得性肺炎"))
            .andExpect(jsonPath("$.data.unmappedFindings[0]").value("LOCALX"))
            .andExpect(jsonPath("$.data.advisoryNote").isNotEmpty());
    }

    @Test
    void emptyStateReturnsAdvisoryNotExclusion() throws Exception {
        when(service.assist(any())).thenReturn(new DiagnosisAssistResponse(
            List.of(), List.of(), DiagnosisAssistService.ADVISORY_EMPTY, "trace-dx"));

        mockMvc.perform(post(PATH).with(tenantJwt("ROLE_IT_OPS"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.candidates").isEmpty())
            .andExpect(jsonPath("$.data.advisoryNote").value(org.hamcrest.Matchers.containsString("不是排除诊断")));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor tenantJwt(String role) {
        return jwt().jwt(token -> token.subject("u-1").claim("tenant_id", "t-1"))
            .authorities(new SimpleGrantedAuthority(role));
    }
}
