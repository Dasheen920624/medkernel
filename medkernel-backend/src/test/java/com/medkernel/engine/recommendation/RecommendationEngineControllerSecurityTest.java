package com.medkernel.engine.recommendation;

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
class RecommendationEngineControllerSecurityTest {

    private static final String TRIGGER_BODY = """
        {
          "triggerCode": "TRG.ORDER.1",
          "triggerType": "order-sign",
          "sourceEventId": "event-1",
          "contextSnapshotId": "snapshot-1",
          "patientId": "patient-1",
          "encounterId": "enc-1",
          "scenarioCode": "WARD_ORDER",
          "inputDigest": "sha256:trigger",
          "candidateCards": [
            {
              "cardCode": "CARD.ANTICOAG",
              "cardType": "MEDICATION",
              "title": "抗凝用药风险提醒",
              "summary": "患者当前医嘱满足抗凝风险规则",
              "suggestedAction": "请确认出血风险评估",
              "riskLevel": "HIGH",
              "interruptLevel": "WEAK_INTERRUPTIVE",
              "requiresPhysicianConfirmation": true,
              "aiGenerated": false,
              "sourceSummary": "来源：抗凝用药规则 v1",
              "sources": [
                {"sourceType": "RULE", "sourceRefId": "rule-1", "sourceTitle": "抗凝用药规则"}
              ]
            }
          ]
        }
        """;

    private static final String FEEDBACK_BODY = """
        {
          "feedbackType": "ACCEPT",
          "reasonCode": "CONFIRMED",
          "reasonText": "已确认风险",
          "operatorRole": "DOCTOR"
        }
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    RecommendationEngineService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void doctorCanReadAndFeedbackButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/recommendations/cards/card-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/recommendations/cards/card-1/sources"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/recommendations/cards/card-1/feedback")
                .contentType("application/json")
                .content(FEEDBACK_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void legacyRootRecommendationCardRoutesAreNotMounted() throws Exception {
        mvc.perform(get("/api/v1/engine/recommendations"))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/engine/recommendations/card-1"))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/engine/recommendations/card-1/feedback")
                .contentType("application/json")
                .content(FEEDBACK_BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_USER")
    void clinicalUserCanCreateRecommendationTriggerButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/recommendations:evaluate")
                .contentType("application/json")
                .content(TRIGGER_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/recommendations/triggers")
                .contentType("application/json")
                .content(TRIGGER_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_AUDITOR")
    void auditorCannotCreateRecommendationTrigger() throws Exception {
        mvc.perform(post("/api/v1/engine/recommendations:evaluate")
                .contentType("application/json")
                .content(TRIGGER_BODY))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/engine/recommendations/triggers")
                .contentType("application/json")
                .content(TRIGGER_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ENGINE_OPERATOR")
    void engineOperatorCanQueryFatigueSignalsButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/recommendations/fatigue-signals"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadRecommendationCards() throws Exception {
        mvc.perform(get("/api/v1/engine/recommendations/cards"))
            .andExpect(status().isForbidden());
    }
}
