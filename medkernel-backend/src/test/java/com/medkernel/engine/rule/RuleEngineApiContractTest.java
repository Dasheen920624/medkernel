package com.medkernel.engine.rule;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.medkernel.shared.context.RequestContext;

/**
 * API-05 规则引擎客户面合同测试。
 *
 * <p>只锁定对外路径、统一入参和旧入口清理；规则执行细节由服务测试覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RuleEngineApiContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RuleEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createRequiresUnifiedContextFields() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ruleCode": "RULE.ANTICOAG",
                      "name": "抗凝风险提示",
                      "ruleType": "ORDER",
                      "sourceRef": "院内抗凝用药管理规范 2026",
                      "dsl": {
                        "trigger": "ORDER_SIGN",
                        "when": {"all": []},
                        "then": [],
                        "explain": {"title": "抗凝风险提示"}
                      },
                      "explanation": {"title": "抗凝风险提示"}
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));
    }

    @Test
    void oldPluralRootIsRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/rules")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void impactEndpointUsesCustomerRouteAndTenantScope() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/rule-1/impact")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void explainEndpointUsesCustomerRouteAndTenantScope() throws Exception {
        mvc.perform(get("/api/v1/engine/rule/rules/executions/rex-1/explain")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api05-doctor")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("doctor")))
            .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("api05-specialist")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("specialist")))
            .authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST"));
    }
}
