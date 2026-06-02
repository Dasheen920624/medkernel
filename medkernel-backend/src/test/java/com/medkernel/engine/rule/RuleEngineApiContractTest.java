package com.medkernel.engine.rule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>只锁定对外路径、统一入参、旧入口清理和客户面解释证据透传；规则执行细节由服务测试覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RuleEngineApiContractTest {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

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
        when(service.explain("rex-1")).thenReturn(new RuleExplanationResponse(
            "rex-1", "rule-1", "version-1", "ORDER_SIGN", "evt-1", "sha256:abc",
            true, RuleRiskLevel.HIGH, read("[{\"actionCode\":\"STRONG_REMINDER\"}]"),
            evidenceExplanation(), RuleExecutionStatus.SUCCESS, "trace-rule"));

        mvc.perform(get("/api/v1/engine/rule/rules/executions/rex-1/explain")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.explanation.conditionEvidence[0].fact").value("patient.age"))
            .andExpect(jsonPath("$.data.explanation.conditionEvidence[0].actual").value(72));
    }

    @Test
    void evaluateEndpointReturnsConditionEvidenceInExplanation() throws Exception {
        RuleEvaluationItem item = new RuleEvaluationItem(
            "rex-1", "rule-1", "version-1", true, RuleRiskLevel.HIGH, List.of(), evidenceExplanation());
        when(service.evaluate(any(RuleEvaluateRequest.class))).thenReturn(new RuleEvaluateResponse(
            "req-1", List.of(item), RuleRiskLevel.HIGH, "trace-rule"));

        mvc.perform(post("/api/v1/engine/rule/rules/evaluate")
                .with(readJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-1",
                      "trace_id": "trace-rule",
                      "tenant_id": "t-1",
                      "user_id": "api05-doctor",
                      "role_codes": ["doctor"],
                      "package_version": "pkg-1",
                      "triggerPoint": "ORDER_SIGN",
                      "context": {"patient": {"age": 72}},
                      "eventId": "evt-1",
                      "ruleIds": ["rule-1"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].explanation.conditionEvidence[0].sourcePath")
                .value("$.patient.age"));
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

    private JsonNode evidenceExplanation() {
        return read("""
            {
              "title": "抗凝风险提示",
              "conditionEvidence": [
                {
                  "fact": "patient.age",
                  "sourcePath": "$.patient.age",
                  "operator": "gte",
                  "expected": 18,
                  "actual": 72,
                  "matched": true,
                  "missing": false
                }
              ]
            }
            """);
    }

    private JsonNode read(String source) {
        try {
            return json.readTree(source);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
