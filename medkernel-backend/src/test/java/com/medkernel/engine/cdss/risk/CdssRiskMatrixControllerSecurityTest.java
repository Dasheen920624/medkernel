package com.medkernel.engine.cdss.risk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CdssRiskMatrixControllerSecurityTest {

    private static final String UPDATE_BODY = """
        {
          "matrixVersion": "4",
          "changeReason": "更新静默试运行门槛",
          "entries": [
            {
              "triggerPoint": "order-sign",
              "severityLevel": "HIGH",
              "automationLevel": "INTERRUPTIVE",
              "riskLevel": "HIGH",
              "reviewRequirement": "PHYSICIAN_CONFIRMATION",
              "silentRunHours": 72,
              "releaseGate": "OPT04_SILENT_TRIAL",
              "autoExecutionAllowed": false,
              "samdClassification": "NMPA_RESERVED",
              "regulatoryEvidence": "TRACEABLE_EVIDENCE_REQUIRED",
              "explanation": "高危医嘱签署提醒必须人工确认"
            }
          ]
        }
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    CdssRiskMatrixService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_GOVERNOR")
    void medicalAffairsCanReadRiskMatrixButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/cdss/risk-matrix"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void doctorCannotUpdateRiskMatrix() throws Exception {
        mvc.perform(put("/api/v1/engine/cdss/risk-matrix")
                .contentType("application/json")
                .content(UPDATE_BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void itOpsCanUpdateRiskMatrixButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(put("/api/v1/engine/cdss/risk-matrix")
                .contentType("application/json")
                .content(UPDATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
