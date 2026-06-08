package com.medkernel.engine.interop;

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
class InteroperabilityControllerSecurityTest {

    private static final String RULE_EXPORT_BODY = """
        {
          "ruleCode": "RULE-CKD-ACEI",
          "name": "CKD ACEI 开嘱复核",
          "ruleType": "ORDER",
          "authoringMode": "DSL",
          "riskLevel": "HIGH",
          "sourceRef": "CKD-PACKAGE",
          "dsl": {
            "trigger": "order-sign",
            "when": {"all": [{"fact": "order.drugClass", "operator": "equals", "value": "ACEI"}]},
            "then": [{"actionCode": "BLOCK", "atSeverity": "HIGH", "indicator": "critical", "summary": "复核", "detail": "复核", "source": {"label": "测试来源"}, "suggestions": [], "overrideReasons": []}],
            "explain": {"summary": "CKD ACEI 安全用药"}
          }
        }
        """;

    @Autowired
    MockMvc mvc;

    @MockBean
    InteroperabilityMappingService service;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorCanExportRuleMappingButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/interoperability/rules/cds-hooks:export")
                .contentType("application/json")
                .content(RULE_EXPORT_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotExportRuleMapping() throws Exception {
        mvc.perform(post("/api/v1/engine/interoperability/rules/cds-hooks:export")
                .contentType("application/json")
                .content(RULE_EXPORT_BODY))
            .andExpect(status().isForbidden());
    }
}
