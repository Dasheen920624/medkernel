package com.medkernel.engine.pathway;

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
class PathwayEngineControllerSecurityTest {

    private static final String PACKAGE_BODY = """
        {
          "packageCode": "PKG.COPD",
          "diseaseCode": "COPD",
          "name": "慢阻肺专病包",
          "packageVersion": "1.0.0",
          "sourceRef": "专病路径专家共识 2026",
          "profiles": [
            {
              "profileCode": "DEFAULT",
              "name": "默认画像",
              "stratification": {"risk": "medium"}
            }
          ]
        }
        """;

    private static final String TEMPLATE_BODY = """
        {
          "packageId": "sp-1",
          "templateCode": "TPL.COPD",
          "name": "稳定期随访路径",
          "diseaseCode": "COPD",
          "templateVersion": 1,
          "templateLevel": "STANDARD",
          "startNodeCode": "ASSESS",
          "sourceRef": "专病路径专家共识 2026",
          "nodes": [
            {"nodeCode": "ASSESS", "name": "入径评估", "nodeType": "ASSESSMENT", "sortOrder": 10},
            {"nodeCode": "FOLLOWUP", "name": "随访", "nodeType": "FOLLOWUP", "sortOrder": 20, "terminal": true}
          ],
          "edges": [
            {"edgeCode": "EDGE.ASSESS.FOLLOWUP", "fromNodeCode": "ASSESS", "toNodeCode": "FOLLOWUP", "edgeType": "DEFAULT", "priority": 10}
          ]
        }
        """;

    private static final String ENTER_BODY = """
        {
          "patientId": "patient-1",
          "encounterId": "enc-1",
          "templateId": "pt-1"
        }
        """;

    private static final String ADVANCE_BODY = """
        {
          "eventType": "COMPLETE",
          "eventId": "evt-1"
        }
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    PathwayEngineService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanReadPathwayButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/engine/pathway/pathway-templates/pt-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1/variances"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1/clocks"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SPECIALIST")
    void specialistCanWritePathwayButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/pathway/specialty-packages")
                .contentType("application/json")
                .content(PACKAGE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/pathway/pathway-templates")
                .contentType("application/json")
                .content(TEMPLATE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/pathway/patient-pathways/enter")
                .contentType("application/json")
                .content(ENTER_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));

        mvc.perform(post("/api/v1/engine/pathway/patient-pathways/pp-1/advance")
                .contentType("application/json")
                .content(ADVANCE_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_MEDICAL_AFFAIRS")
    void medicalAffairsCanPublishButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/pathway/pathway-templates/pt-1/publish")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCannotPublishPathwayTemplate() throws Exception {
        mvc.perform(post("/api/v1/engine/pathway/pathway-templates/pt-1/publish")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotReadPathways() throws Exception {
        mvc.perform(get("/api/v1/engine/pathway/pathway-templates"))
            .andExpect(status().isForbidden());
    }
}
