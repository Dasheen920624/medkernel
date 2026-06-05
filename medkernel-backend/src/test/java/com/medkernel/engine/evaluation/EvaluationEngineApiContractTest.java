package com.medkernel.engine.evaluation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
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

/**
 * API-08 面向客户面的评估质控契约测试。
 *
 * <p>旧版 {@code /api/v1/engine/evaluations/**} 继续兼容；本测试锁定 D4 卡片要求的
 * 单数资源路径、冒号动作路径和 {@code MODEL_DISABLED} 降级字段。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EvaluationEngineApiContractTest {

    private static final String EVALUATE_BODY = """
        {
          "contextSnapshotId": "snapshot-1",
          "scenarioCode": "DISCHARGE",
          "packageVersion": "1.0.0"
        }
        """;

    private static final String RECTIFICATION_BODY = """
        {"rectificationSummary": "补录风险评估记录", "evidenceRef": "proof-1"}
        """;

    private static final String REVIEW_BODY = """
        {"decision": "APPROVED", "comment": "证据充分，允许闭环", "evidenceRef": "review-proof-1"}
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    EvaluationEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void canonicalIndicatorAndIssueQueriesUseSingleEvaluationResource() throws Exception {
        when(service.listIndicators(any(), any())).thenReturn(PageResponse.empty(PageRequest.defaults()));
        when(service.listFindings(any(), any())).thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/evaluation/indicators")
                .with(jwtUser("medical-affairs", "ROLE_MEDICAL_AFFAIRS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0));

        mvc.perform(get("/api/v1/engine/evaluation/issues")
                .with(jwtUser("medical-affairs", "ROLE_MEDICAL_AFFAIRS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0));

        verify(service).listIndicators(any(EvaluationIndicatorFilter.class), any(PageRequest.class));
        verify(service).listFindings(any(QualityFindingFilter.class), any(PageRequest.class));
    }

    @Test
    void canonicalEvaluateEndpointReturnsDeterministicModelDisabledStatus() throws Exception {
        when(service.evaluateSnapshot(any(EvaluationEvaluateSnapshotRequest.class)))
            .thenReturn(new EvaluationRunResponse(
                "er-1", EvaluationRunStatus.RECORDED, 1, 1, 1, "trace-eval"));

        mvc.perform(post("/api/v1/engine/evaluation:evaluate")
                .with(jwtUser("it-ops", "ROLE_IT_OPS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(EVALUATE_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.runId").value("er-1"))
            .andExpect(jsonPath("$.data.modelStatus").value("MODEL_DISABLED"));

        verify(service).evaluateSnapshot(any(EvaluationEvaluateSnapshotRequest.class));
    }

    @Test
    void canonicalRectificationEndpointsKeepIdempotencyKeyAndReviewContract() throws Exception {
        when(service.submitRectification(
                eq("qf-1"), any(RectificationSubmitRequest.class), eq("idem-rect-1")))
            .thenReturn(new RectificationResponse(
                "rct-1", QualityFindingStatus.REMEDIATING, RectificationTaskStatus.SUBMITTED, "trace-eval"));
        when(service.reviewRectification(
                eq("qf-1"), any(RectificationReviewRequest.class), eq("idem-review-1")))
            .thenReturn(new RectificationReviewResponse(
                "rr-1", QualityFindingStatus.CLOSED, RectificationTaskStatus.CLOSED, "trace-eval"));

        mvc.perform(post("/api/v1/engine/evaluation/rectifications")
                .queryParam("findingId", "qf-1")
                .header("Idempotency-Key", "idem-rect-1")
                .with(jwtUser("dept-head", "ROLE_DEPT_HEAD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(RECTIFICATION_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskId").value("rct-1"))
            .andExpect(jsonPath("$.data.taskStatus").value("SUBMITTED"));

        mvc.perform(post("/api/v1/engine/evaluation/rectifications/qf-1/review")
                .header("Idempotency-Key", "idem-review-1")
                .with(jwtUser("qa-manager", "ROLE_QA_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REVIEW_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reviewId").value("rr-1"))
            .andExpect(jsonPath("$.data.findingStatus").value("CLOSED"));

        verify(service).submitRectification(
            eq("qf-1"), any(RectificationSubmitRequest.class), eq("idem-rect-1"));
        verify(service).reviewRectification(
            eq("qf-1"), any(RectificationReviewRequest.class), eq("idem-review-1"));
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtUser(
                String roleClaim, String authority) {
        return jwt()
            .jwt(token -> token
                .subject("tester")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of(roleClaim)))
            .authorities(new SimpleGrantedAuthority(authority));
    }
}
