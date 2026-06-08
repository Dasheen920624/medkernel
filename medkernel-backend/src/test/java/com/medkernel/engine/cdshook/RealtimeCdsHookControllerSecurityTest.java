package com.medkernel.engine.cdshook;

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
class RealtimeCdsHookControllerSecurityTest {

    private static final String ORDER_SIGN_BODY = """
        {
          "hook": "order-sign",
          "hookInstance": "hook-order-001",
          "patientId": "MPI-1",
          "encounterId": "ENC-1",
          "packageVersion": "pkg-2026.06",
          "sourceSystem": "HIS",
          "context": {
            "patientId": "MPI-1",
            "encounterId": "ENC-1",
            "packageVersion": "pkg-2026.06",
            "contextSnapshotId": "ctx-active-001",
            "orders": [
              {"orderCode": "ORDER.ACEI", "display": "ACEI 药物医嘱"}
            ]
          }
        }
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    RealtimeCdsHookService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanEvaluateOrderSignCdsButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/cds-hooks:evaluate")
                .contentType("application/json")
                .content(ORDER_SIGN_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotEvaluateRealtimeCds() throws Exception {
        mvc.perform(post("/api/v1/engine/cds-hooks:evaluate")
                .contentType("application/json")
                .content(ORDER_SIGN_BODY))
            .andExpect(status().isForbidden());
    }
}
