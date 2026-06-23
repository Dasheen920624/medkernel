package com.medkernel.engine.rule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.cdshook.CdsHookCard;
import com.medkernel.engine.cdshook.CdsHookSource;
import com.medkernel.engine.cdshook.CdsHookSuggestion;
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
                        "trigger": "order-sign",
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
    void createAcceptsCanonicalQualityRuleType() throws Exception {
        when(service.createRule(any(RuleCreateRequest.class))).thenReturn(new RuleCreateResponse(
            "rule-quality", "version-1", RuleDefinitionStatus.DRAFT, "trace-rule"));

        mvc.perform(post("/api/v1/engine/rule/rules")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-quality",
                      "trace_id": "trace-rule",
                      "tenant_id": "t-1",
                      "user_id": "api05-specialist",
                      "role_codes": ["engine-operator"],
                      "ruleCode": "RULE.CARDIOLOGY.HR",
                      "name": "心率质控复核",
                      "ruleType": "QUALITY",
                      "authoringMode": "VISUAL",
                      "riskLevel": "MEDIUM",
                      "sourceRef": "院内已审核心血管诊疗规范 2026",
                      "changeSummary": "初始化创建草稿版本",
                      "dsl": {
                        "when": {
                          "all": [
                            {"fact": "observations.0.value", "operator": "gte", "value": 100}
                          ]
                        },
                        "then": [],
                        "explain": {"summary": "心率阈值质控"}
                      },
                      "explanation": {"summary": "心率阈值质控"}
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ruleId").value("rule-quality"));
    }

    @Test
    void createRejectsLegacyPackageSelectionFieldsInsteadOfSilentlyIgnoringThem() throws Exception {
        mvc.perform(post("/api/v1/engine/rule/rules")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-quality",
                      "trace_id": "trace-rule",
                      "tenant_id": "t-1",
                      "user_id": "api05-specialist",
                      "role_codes": ["engine-operator"],
                      "package_version": "1.0.0",
                      "packageCode": "PKG.LEGACY",
                      "ruleCode": "RULE.CARDIOLOGY.HR",
                      "name": "心率质控复核",
                      "ruleType": "QUALITY",
                      "sourceRef": "院内心血管诊疗规范 2026",
                      "dsl": {
                        "when": {"all": []},
                        "then": [],
                        "explain": {"summary": "心率阈值质控"}
                      }
                    }
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createNextVersionUsesStableRuleIdentityRoute() throws Exception {
        when(service.createNextVersion("rule-1")).thenReturn(new RuleVersionCreateResponse(
            "rule-1", "version-2", 2, RuleVersionStatus.DRAFT, "trace-rule"));

        mvc.perform(post("/api/v1/engine/rule/rules/rule-1/versions")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-v2",
                      "trace_id": "trace-rule",
                      "tenant_id": "t-1",
                      "user_id": "api05-specialist",
                      "role_codes": ["engine-operator"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ruleId").value("rule-1"))
            .andExpect(jsonPath("$.data.versionId").value("version-2"))
            .andExpect(jsonPath("$.data.versionNo").value(2))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void operationRequestDoesNotCarryManualPackageVersion() {
        org.assertj.core.api.Assertions.assertThat(
            List.of(RuleOperationRequest.class.getRecordComponents()).stream()
                .map(component -> component.getName())
                .toList())
            .doesNotContain("packageVersion");
    }

    @Test
    void testCaseRequestDoesNotCarryManualPackageVersion() {
        org.assertj.core.api.Assertions.assertThat(
            List.of(RuleTestCaseRequest.class.getRecordComponents()).stream()
                .map(component -> component.getName())
                .toList())
            .doesNotContain("packageVersion");
    }

    @Test
    void governanceTransitionRequestDoesNotCarryManualPackageVersion() {
        org.assertj.core.api.Assertions.assertThat(
            List.of(RuleGovernanceTransitionRequest.class.getRecordComponents()).stream()
                .map(component -> component.getName())
                .toList())
            .doesNotContain("packageVersion");
    }

    @Test
    void simulationRequiresExplicitTriggerAndDoesNotCarryPackageVersion() {
        org.assertj.core.api.Assertions.assertThat(
            List.of(RuleSimulateRequest.class.getRecordComponents()).stream()
                .map(component -> component.getName())
                .toList())
            .contains("triggerPoint", "context")
            .doesNotContain("packageVersion");
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
            "rex-1", "rule-1", "version-1", "order-sign", "evt-1", "sha256:abc",
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
            "rex-1", "rule-1", "version-1", true, RuleRiskLevel.HIGH, List.of(),
            evidenceExplanation(), RuleExecutionStatus.SUCCESS, null, null);
        CdsHookCard card = new CdsHookCard(
            "rex-1-action-1",
            "高风险规则命中",
            "确认依据后方可继续。",
            "critical",
            new CdsHookSource("院内高风险规则", null, "A"),
            List.of(new CdsHookSuggestion(
                "补充专科复核",
                "REMIND",
                json.createObjectNode().put("department", "CARDIOLOGY"))),
            List.of("紧急处置"),
            true);
        when(service.evaluate(any(RuleEvaluateRequest.class))).thenReturn(new RuleEvaluateResponse(
            "req-1", List.of(item), RuleRiskLevel.HIGH, List.of(card), "trace-rule"));

        mvc.perform(post("/api/v1/engine/rule/rules/evaluate")
                .with(readJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-1",
                      "trace_id": "trace-rule",
                      "tenant_id": "t-1",
                      "user_id": "api05-doctor",
                      "role_codes": ["clinical-user"],
                      "triggerPoint": "order-sign",
                      "contextSnapshotId": "snapshot-1",
                      "eventId": "evt-1",
                      "ruleIds": ["rule-1"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].explanation.conditionEvidence[0].sourcePath")
                .value("$.patient.age"))
            .andExpect(jsonPath("$.data.cards[0].summary").value("高风险规则命中"))
            .andExpect(jsonPath("$.data.cards[0].source.label").value("院内高风险规则"))
            .andExpect(jsonPath("$.data.cards[0].suggestions[0].payload.department")
                .value("CARDIOLOGY"))
            .andExpect(jsonPath("$.data.cards[0].requiresPhysicianConfirmation").value(true));
    }

    @Test
    void evaluateEndpointDoesNotRequireClientSuppliedPackageVersion() throws Exception {
        when(service.evaluate(any(RuleEvaluateRequest.class))).thenReturn(new RuleEvaluateResponse(
            "req-runtime", List.of(), RuleRiskLevel.LOW, List.of(), "trace-rule"));

        mvc.perform(post("/api/v1/engine/rule/rules/evaluate")
                .with(readJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "request_id": "req-runtime",
                      "trace_id": "trace-rule",
                      "tenant_id": "t-1",
                      "user_id": "api05-doctor",
                      "role_codes": ["clinical-user"],
                      "triggerPoint": "order-sign",
                      "contextSnapshotId": "snapshot-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void overrideEndpointReturnsPersistedClinicalReason() throws Exception {
        when(service.captureOverride(eq("rex-1"), any(RuleOverrideRequest.class)))
            .thenReturn(new RuleOverrideResponse(
                "rov-1", "rex-1", "rule-1", RuleActionCode.BLOCK,
                "已完成临床复核", "doctor-1", java.time.Instant.parse("2026-06-07T08:00:00Z"),
                "trace-rule"));

        mvc.perform(post("/api/v1/engine/rule/rules/executions/rex-1/override")
                .with(readJwt())
                .contentType("application/json")
                .content("{\"actionCode\":\"BLOCK\",\"reason\":\"已完成临床复核\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.overrideId").value("rov-1"))
            .andExpect(jsonPath("$.data.reason").value("已完成临床复核"));
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api05-doctor")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("clinical-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("api05-specialist")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("engine-operator")))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"));
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
