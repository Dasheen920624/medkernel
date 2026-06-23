package com.medkernel.engine.rule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RuleEngineControllerSecurityTest {

    private static final String CREATE_BODY = """
        {
          "ruleCode": "RULE.ANTICOAG",
          "name": "抗凝风险提示",
          "ruleType": "ORDER",
          "authoringMode": "DSL",
          "riskLevel": "HIGH",
          "sourceRef": "院内抗凝用药管理规范 2026",
          "dsl": {
            "trigger": "order-sign",
            "when": {"all": [{"fact": "patient.age", "operator": "gte", "value": 18}]},
            "then": [{"actionCode": "STRONG_REMINDER", "atSeverity": "HIGH", "indicator": "critical", "summary": "提醒", "detail": "提醒", "source": {"label": "规则测试来源"}, "suggestions": [], "overrideReasons": []}],
            "explain": {"title": "抗凝风险提示", "reason": "测试"}
          },
          "explanation": {"title": "抗凝风险提示"}
        }
        """;

    private static final String TEST_CASE_BODY = """
        {
          "caseType": "POSITIVE",
          "contextSnapshotId": "snapshot-1",
          "expectedHit": true,
          "expectedSeverity": "HIGH",
          "expectedActionCode": "STRONG_REMINDER"
        }
        """;

    private static final String EVALUATE_BODY = """
        {
          "triggerPoint": "order-sign",
          "contextSnapshotId": "snapshot-1",
          "eventId": "evt-1",
          "ruleIds": ["rule-1"]
        }
        """;

    private static final String TRANSITION_BODY =
        "{\"targetState\":\"REVIEWED\",\"impactDigest\":\"sha256:impact\",\"reason\":\"负责人确认技术验证结果\"}";
    private static final String OVERRIDE_BODY =
        "{\"actionCode\":\"BLOCK\",\"reason\":\"已完成临床复核\"}";
    private static final String SHADOW_FEEDBACK_BODY =
        "{\"decision\":\"FALSE_POSITIVE\",\"reason\":\"影子提示与当前处置不匹配\"}";
    private static final String BACKTEST_BODY =
        "{\"cohortRef\":\"ckd-2026-q1\"}";
    private static final String DRIFT_BODY =
        "{\"windowStart\":\"2026-06-01T00:00:00Z\",\"windowEnd\":\"2026-06-07T00:00:00Z\",\"threshold\":0.10}";

    @Autowired
    MockMvc mvc;

    @MockBean
    RuleEngineService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanReadRuleButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/rule-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanEvaluateRulesButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/evaluate")
                .contentType("application/json")
                .content(EVALUATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanDiagnoseExecutionButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/executions/rex-1/explain"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanListExecutionsButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/executions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanReadShadowStatsButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/rule-1/shadow-stats"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanCaptureOverrideButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/executions/rex-1/override")
                .contentType("application/json")
                .content(OVERRIDE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void specialistCanReachCreateButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules")
                .contentType("application/json")
                .content(CREATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void specialistCanReachTestCaseAndSimulateButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/test-cases")
                .contentType("application/json")
                .content(TEST_CASE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/simulate")
                .contentType("application/json")
                .content("{\"context\":{\"patient\":{\"age\":72}}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void engineOperatorCanGovernRuleButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/governance/transitions")
                .contentType("application/json")
                .content(TRANSITION_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void engineOperatorCanReachGovernanceTransitionBeforeDataScopeValidation() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/governance/transitions")
                .contentType("application/json")
                .content(TRANSITION_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void specialistCanReachRuleGovernanceTransitionForDraftSubmission() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/governance/transitions")
                .contentType("application/json")
                .content(TRANSITION_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void specialistCanReachShadowFeedbackButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/executions/rex-1/shadow-feedback")
                .contentType("application/json")
                .content(SHADOW_FEEDBACK_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void specialistCanReachBacktestAndDriftButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/backtest")
                .contentType("application/json")
                .content(BACKTEST_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/drift")
                .contentType("application/json")
                .content(DRIFT_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCannotGovernRules() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/governance/transitions")
                .contentType("application/json")
                .content(TRANSITION_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadRules() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules"))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/rule/rules/executions/rex-1/override")
                .contentType("application/json")
                .content(OVERRIDE_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/rule/rules/executions/rex-1/shadow-feedback")
                .contentType("application/json")
                .content(SHADOW_FEEDBACK_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/backtest")
                .contentType("application/json")
                .content(BACKTEST_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/drift")
                .contentType("application/json")
                .content(DRIFT_BODY))
            .andExpect(status().isForbidden());
    }
}
