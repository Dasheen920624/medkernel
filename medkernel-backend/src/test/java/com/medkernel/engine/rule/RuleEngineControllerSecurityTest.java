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
            "trigger": "ORDER_SIGN",
            "when": {"all": [{"fact": "patient.age", "operator": "gte", "value": 18}]},
            "then": [{"actionCode": "STRONG_REMINDER", "severity": "HIGH", "message": "提醒"}],
            "explain": {"title": "抗凝风险提示", "reason": "测试"}
          },
          "explanation": {"title": "抗凝风险提示"}
        }
        """;

    private static final String TEST_CASE_BODY = """
        {
          "caseType": "POSITIVE",
          "inputPayload": {"patient": {"age": 72}},
          "expectedHit": true,
          "expectedSeverity": "HIGH",
          "expectedActionCode": "STRONG_REMINDER"
        }
        """;

    private static final String EVALUATE_BODY = """
        {
          "triggerPoint": "ORDER_SIGN",
          "context": {"patient": {"age": 72}},
          "eventId": "evt-1",
          "ruleIds": ["rule-1"]
        }
        """;

    private static final String PUBLISH_BODY = "{}";

    @Autowired
    MockMvc mvc;

    @MockBean
    RuleEngineService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanReadRuleButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/rule-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanEvaluateRulesButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/evaluate")
                .contentType("application/json")
                .content(EVALUATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanDiagnoseExecutionButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/executions/rex-1/explain"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SPECIALIST")
    void specialistCanReachCreateButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules")
                .contentType("application/json")
                .content(CREATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SPECIALIST")
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
    @WithMockUser(authorities = "ROLE_MEDICAL_AFFAIRS")
    void medicalAffairsCanPublishButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/publish")
                .contentType("application/json")
                .content(PUBLISH_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCannotPublishRules() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/publish")
                .contentType("application/json")
                .content(PUBLISH_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadRules() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules"))
            .andExpect(status().isForbidden());
    }
}
