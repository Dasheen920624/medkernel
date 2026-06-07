package com.medkernel.engine.evaluation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EvaluationEngineControllerSecurityTest {

    private static final String INDICATOR_BODY = """
        {
          "indicatorCode": "IND.VTE.PROPHYLAXIS",
          "versionNo": 1,
          "name": "静脉血栓预防完成率",
          "subjectType": "MEDICAL_RECORD",
          "denominatorDefinition": "符合住院风险分层病例",
          "numeratorDefinition": "完成预防评估病例",
          "timeWindow": "DISCHARGE+24H",
          "organizationScope": "全院住院科室",
          "responsibleDepartmentId": "dept-1",
          "sourceRef": "guideline-1"
        }
        """;

    private static final String RUN_BODY = """
        {
          "runCode": "RUN.VTE",
          "runType": "UPSTREAM_RESULT",
          "contextSnapshotId": "snapshot-1",
          "scenarioCode": "DISCHARGE",
          "inputDigest": "sha256:run",
          "results": [
            {
              "indicatorId": "ei-1",
              "subjectType": "MEDICAL_RECORD",
              "subjectRefId": "record-1",
              "resultLevel": "NON_COMPLIANT",
              "hitFlag": true,
              "evidenceSummary": "指标未达标",
              "findings": []
            }
          ]
        }
        """;

    private static final String RECTIFICATION_BODY = """
        {"rectificationSummary": "补录风险评估记录", "evidenceRef": "proof-1"}
        """;

    private static final String REVIEW_BODY = """
        {"decision": "APPROVED", "comment": "证据充分，允许闭环", "evidenceRef": "review-proof-1"}
        """;

    private static final String RELEASE_BODY = """
        {"reason": "质控办审核通过并留存发布证据"}
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    EvaluationEngineService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_QA_MANAGER")
    void qaManagerCanConfigurePublishAndReviewButRequiresTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/evaluation/indicators")
                .contentType("application/json")
                .content(INDICATOR_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/evaluation/indicators/ei-1/publish")
                .contentType("application/json")
                .content(RELEASE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/evaluation/rectifications/qf-1/review")
                .contentType("application/json")
                .content(REVIEW_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_MEDICAL_AFFAIRS")
    void medicalAffairsCanReadButCannotPublishEvaluationIndicator() throws Exception {
        mvc.perform(get("/api/v1/engine/evaluation/indicators"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/evaluation/indicators/ei-1/publish")
                .contentType("application/json")
                .content(RELEASE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_IT_OPS")
    void itOpsCanWriteRunsButCannotReviewFindings() throws Exception {
        mvc.perform(post("/api/v1/engine/evaluation/runs")
                .contentType("application/json")
                .content(RUN_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/evaluation/rectifications/qf-1/review")
                .contentType("application/json")
                .content(REVIEW_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DEPT_HEAD")
    void departmentHeadCanRemediateButCannotReview() throws Exception {
        mvc.perform(post("/api/v1/engine/evaluation/rectifications")
                .queryParam("findingId", "qf-1")
                .contentType("application/json")
                .content(RECTIFICATION_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/evaluation/rectifications/qf-1/review")
                .contentType("application/json")
                .content(REVIEW_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCannotUseEvaluationClosedLoopEndpoints() throws Exception {
        mvc.perform(post("/api/v1/engine/evaluation/runs")
                .contentType("application/json")
                .content(RUN_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/evaluation/rectifications")
                .queryParam("findingId", "qf-1")
                .contentType("application/json")
                .content(RECTIFICATION_BODY))
            .andExpect(status().isForbidden());
    }
}
