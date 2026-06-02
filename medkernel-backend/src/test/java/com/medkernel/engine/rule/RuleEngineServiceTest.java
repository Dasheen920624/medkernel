package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RuleEngineServiceTest {

    private RuleDefinitionRepository definitions;
    private RuleVersionRepository versions;
    private RuleTestCaseRepository testCases;
    private RuleExecutionLogRepository executions;
    private AuditEventPublisher auditPublisher;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private ObjectMapper json;
    private RuleEngineService service;

    @BeforeEach
    void setUp() {
        definitions = mock(RuleDefinitionRepository.class);
        versions = mock(RuleVersionRepository.class);
        testCases = mock(RuleTestCaseRepository.class);
        executions = mock(RuleExecutionLogRepository.class);
        auditPublisher = mock(AuditEventPublisher.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        json = new ObjectMapper();
        json.findAndRegisterModules();
        service = new RuleEngineService(
            definitions, versions, testCases, executions,
            new RuleDslEvaluator(json), auditPublisher, transitions, diagnoseAssembler, json);

        when(definitions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testCases.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(executions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rule", OrgScope.tenant("tenant-A"), "tester"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void createRulePersistsDefinitionAndDraftVersion() throws Exception {
        RuleCreateResponse response = service.createRule(new RuleCreateRequest(
            "RULE.ANTICOAG", "抗凝风险提示", RuleType.ORDER, RuleAuthoringMode.DSL,
            RuleRiskLevel.HIGH, "rpv-1", "dept-1", "院内抗凝用药管理规范 2026",
            "初始版本", dsl(), dsl().path("explain")));

        assertThat(response.ruleId()).startsWith("rule-");
        assertThat(response.versionId()).startsWith("rv-");
        assertThat(response.status()).isEqualTo(RuleDefinitionStatus.DRAFT);
        assertThat(response.traceId()).isEqualTo("trace-rule");

        ArgumentCaptor<RuleDefinition> ruleCap = ArgumentCaptor.forClass(RuleDefinition.class);
        ArgumentCaptor<RuleVersion> versionCap = ArgumentCaptor.forClass(RuleVersion.class);
        verify(definitions).save(ruleCap.capture());
        verify(versions).save(versionCap.capture());
        assertThat(ruleCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(ruleCap.getValue().activeVersionId()).isEqualTo(response.versionId());
        assertThat(versionCap.getValue().versionNo()).isEqualTo(1);
        assertThat(versionCap.getValue().sourceRef()).isEqualTo("院内抗凝用药管理规范 2026");
        verify(auditPublisher).publish(AuditAction.CREATE, "rule_definition", response.ruleId(), "创建规则 RULE.ANTICOAG");
        verify(transitions).record("rule_definition", response.ruleId(), null, "DRAFT", "CREATE_RULE", null);
    }

    @Test
    void addTestCasePersistsAgainstCurrentVersion() throws Exception {
        RuleDefinition rule = existingRule(RuleDefinitionStatus.DRAFT);
        RuleVersion version = existingVersion(RuleVersionStatus.DRAFT);
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A")).thenReturn(Optional.of(rule));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A")).thenReturn(Optional.of(version));

        RuleTestCaseResponse response = service.addTestCase("rule-1", new RuleTestCaseRequest(
            RuleTestCaseType.POSITIVE, hitContext(), true, RuleRiskLevel.HIGH, "STRONG_REMINDER"));

        assertThat(response.caseId()).startsWith("rtc-");
        assertThat(response.caseType()).isEqualTo(RuleTestCaseType.POSITIVE);
        ArgumentCaptor<RuleTestCase> caseCap = ArgumentCaptor.forClass(RuleTestCase.class);
        verify(testCases).save(caseCap.capture());
        assertThat(caseCap.getValue().versionId()).isEqualTo("version-1");
        assertThat(caseCap.getValue().inputPayload()).contains("ANTICOAGULANT");
    }

    @Test
    void publishFailsWhenRequiredCaseTypeMissing() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(testCase(RuleTestCaseType.POSITIVE, true, hitContext())));

        assertThatThrownBy(() -> service.publish("rule-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);
    }

    @Test
    void publishFailsWhenAnyTestCaseExpectationDiffersAndStoresResult() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, missContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, hitContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        RuleImpactResponse impact = service.impact("rule-1");

        assertThatThrownBy(() -> service.publish("rule-1", new RulePublishRequest(impact.impactDigest(), "已查看影响")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_004);

        ArgumentCaptor<RuleTestCase> caseCap = ArgumentCaptor.forClass(RuleTestCase.class);
        verify(testCases, org.mockito.Mockito.atLeastOnce()).save(caseCap.capture());
        assertThat(caseCap.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.caseType()).isEqualTo(RuleTestCaseType.POSITIVE);
            assertThat(saved.lastStatus()).isEqualTo(RuleTestCaseStatus.FAIL);
        });
    }

    @Test
    void publishSucceedsWhenAllTestCasesPass() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));
        RuleImpactResponse impact = service.impact("rule-1");

        RulePublishResponse response = service.publish("rule-1", new RulePublishRequest(impact.impactDigest(), "已查看影响"));

        assertThat(response.status()).isEqualTo(RuleDefinitionStatus.PUBLISHED);
        assertThat(response.impactDigest()).isEqualTo(impact.impactDigest());
        assertThat(response.impactStatus()).isEqualTo("PARTIAL");
        assertThat(response.results()).hasSize(4).allSatisfy(result ->
            assertThat(result.status()).isEqualTo(RuleTestCaseStatus.PASS));
        ArgumentCaptor<RuleDefinition> ruleCap = ArgumentCaptor.forClass(RuleDefinition.class);
        ArgumentCaptor<RuleVersion> versionCap = ArgumentCaptor.forClass(RuleVersion.class);
        verify(definitions, org.mockito.Mockito.atLeastOnce()).save(ruleCap.capture());
        verify(versions, org.mockito.Mockito.atLeastOnce()).save(versionCap.capture());
        assertThat(ruleCap.getAllValues()).anySatisfy(saved ->
            assertThat(saved.status()).isEqualTo(RuleDefinitionStatus.PUBLISHED));
        assertThat(versionCap.getAllValues()).anySatisfy(saved ->
            assertThat(saved.status()).isEqualTo(RuleVersionStatus.PUBLISHED));
        verify(auditPublisher).publish(AuditAction.PUBLISH, "rule_definition", "rule-1", "发布规则版本 version-1");
    }

    @Test
    void impactReturnsPartialKnownObjectsAndDigest() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));

        RuleImpactResponse response = service.impact("rule-1");

        assertThat(response.analysisStatus()).isEqualTo("PARTIAL");
        assertThat(response.impactDigest()).startsWith("sha256:");
        assertThat(response.affectedRules()).singleElement().satisfies(rule -> {
            assertThat(rule.objectType()).isEqualTo("RULE_DEFINITION");
            assertThat(rule.objectId()).isEqualTo("rule-1");
        });
        assertThat(response.affectedPathways()).isEmpty();
        assertThat(response.inPathPatients()).isEmpty();
        assertThat(response.syncTargets()).isEmpty();
        assertThat(response.unavailableScopes()).hasSize(3);
    }

    @Test
    void highRiskPublishWithoutImpactDigestIsDeniedBeforeTesting() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext()),
                testCase(RuleTestCaseType.BOUNDARY, true, boundaryContext()),
                testCase(RuleTestCaseType.CONFLICT, false, missContext())
            ));

        assertThatThrownBy(() -> service.publish("rule-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RULE_PUBLISH_GATE_DENIED);
    }

    @Test
    void runTestsReturnsAllResultsWithoutPublishing() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.DRAFT)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.DRAFT)));
        when(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc("version-1", "tenant-A"))
            .thenReturn(List.of(
                testCase(RuleTestCaseType.POSITIVE, true, hitContext()),
                testCase(RuleTestCaseType.NEGATIVE, false, missContext())
            ));

        RuleTestRunResponse response = service.runTests("rule-1");

        assertThat(response.allPassed()).isTrue();
        assertThat(response.results()).hasSize(2);
        verify(definitions, org.mockito.Mockito.never()).save(any());
        verify(versions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void evaluatePublishedRulePersistsExecutionLogAndReturnsExplanation() {
        when(definitions.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(existingRule(RuleDefinitionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(existingVersion(RuleVersionStatus.PUBLISHED)));

        RuleEvaluateResponse response = service.evaluate(new RuleEvaluateRequest(
            "ORDER_SIGN", hitContext(), "evt-1", List.of("rule-1")));

        assertThat(response.items()).hasSize(1);
        assertThat(response.highestSeverity()).isEqualTo(RuleRiskLevel.HIGH);
        RuleEvaluationItem item = response.items().getFirst();
        assertThat(item.hit()).isTrue();
        assertThat(item.explanation().get("title").asText()).isEqualTo("抗凝风险提示");
        assertThat(item.explanation().path("conditionEvidence")).hasSize(2);
        assertThat(item.explanation().path("conditionEvidence").get(0).path("fact").asText())
            .isEqualTo("patient.age");
        assertThat(item.explanation().path("conditionEvidence").get(0).path("actual").asInt())
            .isEqualTo(72);

        ArgumentCaptor<RuleExecutionLog> executionCap = ArgumentCaptor.forClass(RuleExecutionLog.class);
        verify(executions).save(executionCap.capture());
        assertThat(executionCap.getValue().inputDigest()).startsWith("sha256:");
        assertThat(executionCap.getValue().actionsJson()).contains("STRONG_REMINDER");
        assertThat(executionCap.getValue().explanationJson())
            .contains("\"conditionEvidence\"")
            .contains("\"sourcePath\":\"$.patient.age\"");
        verify(auditPublisher).publish(AuditAction.EXECUTE, "rule_execution", item.executionId(), "执行规则 rule-1");
    }

    @Test
    void evaluateWithoutExplicitRuleIdsFallsBackToPlatformPublishedRules() {
        RuleDefinition platformRule = existingRule(
            "rule-platform", "t-1", "RULE.PLATFORM.ANTICOAG", "平台抗凝风险提示",
            "version-platform", RuleDefinitionStatus.PUBLISHED);
        RuleVersion platformVersion = existingVersion(
            "version-platform", "t-1", "rule-platform", RuleVersionStatus.PUBLISHED);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of());
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformRule));
        when(versions.findByVersionIdAndTenantId("version-platform", "t-1")).thenReturn(Optional.of(platformVersion));

        RuleEvaluateResponse response = service.evaluate(new RuleEvaluateRequest(
            "ORDER_SIGN", hitContext(), "evt-platform", List.of()));

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-platform");
        ArgumentCaptor<RuleExecutionLog> executionCap = ArgumentCaptor.forClass(RuleExecutionLog.class);
        verify(executions).save(executionCap.capture());
        assertThat(executionCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(executionCap.getValue().ruleId()).isEqualTo("rule-platform");
    }

    @Test
    void evaluateMergesLocalOverridesWithNonOverriddenPlatformRules() {
        RuleDefinition localOverride = existingRule(
            "rule-local", "tenant-A", "RULE.ANTICOAG", "院内抗凝风险提示",
            "version-local", RuleDefinitionStatus.PUBLISHED);
        RuleDefinition platformShadowed = existingRule(
            "rule-platform-shadowed", "t-1", "RULE.ANTICOAG", "平台抗凝风险提示",
            "version-platform-shadowed", RuleDefinitionStatus.PUBLISHED);
        RuleDefinition platformOnly = existingRule(
            "rule-platform-dvt", "t-1", "RULE.DVT", "平台 DVT 风险提示",
            "version-platform-dvt", RuleDefinitionStatus.PUBLISHED);
        when(definitions.findPublishedByTenantId("tenant-A")).thenReturn(List.of(localOverride));
        when(definitions.findPublishedByTenantId("t-1")).thenReturn(List.of(platformShadowed, platformOnly));
        when(versions.findByVersionIdAndTenantId("version-local", "tenant-A"))
            .thenReturn(Optional.of(existingVersion("version-local", "tenant-A", "rule-local", RuleVersionStatus.PUBLISHED)));
        when(versions.findByVersionIdAndTenantId("version-platform-dvt", "t-1"))
            .thenReturn(Optional.of(existingVersion("version-platform-dvt", "t-1", "rule-platform-dvt", RuleVersionStatus.PUBLISHED)));

        RuleEvaluateResponse response = service.evaluate(new RuleEvaluateRequest(
            "ORDER_SIGN", hitContext(), "evt-merge", List.of()));

        assertThat(response.items()).extracting(RuleEvaluationItem::ruleId)
            .containsExactly("rule-local", "rule-platform-dvt");
        verify(versions, org.mockito.Mockito.never())
            .findByVersionIdAndTenantId("version-platform-shadowed", "t-1");
    }

    @Test
    void diagnoseAssemblesFromExecutionLog() {
        RuleExecutionLog execution = new RuleExecutionLog(
            1L, "rex-1", "tenant-A", "rule-1", "version-1", "ORDER_SIGN",
            "evt-1", "tester", "sha256:abc", true, RuleRiskLevel.HIGH,
            "[]", "{\"title\":\"抗凝风险提示\"}", RuleExecutionStatus.SUCCESS,
            null, null, Instant.now(), Instant.now(), "trace-rule");
        DiagnoseResponse expected = new DiagnoseResponse(
            "rule_execution", "rex-1", "tenant-A", "SUCCESS",
            execution, List.of(), List.of(), Map.of(), null, "trace-rule", null);
        when(executions.findByExecutionIdAndTenantId("rex-1", "tenant-A")).thenReturn(Optional.of(execution));
        when(diagnoseAssembler.assemble(eq("rule_execution"), eq("rex-1"), eq("tenant-A"),
            eq("SUCCESS"), eq(execution), eq(List.of()), eq(Map.of()), any(), eq("trace-rule"),
            any(DiagnoseResponse.ExecutionSummary.class)))
            .thenReturn(expected);

        DiagnoseResponse actual = service.diagnose("rex-1");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<DiagnoseResponse.ExecutionSummary> summaryCaptor =
            ArgumentCaptor.forClass(DiagnoseResponse.ExecutionSummary.class);
        verify(diagnoseAssembler).assemble(eq("rule_execution"), eq("rex-1"), eq("tenant-A"),
            eq("SUCCESS"), eq(execution), eq(List.of()), eq(Map.of()), any(), eq("trace-rule"),
            summaryCaptor.capture());
        assertThat(summaryCaptor.getValue().matchedRuleId()).isEqualTo("rule-1");
        assertThat(summaryCaptor.getValue().matchedVersionId()).isEqualTo("version-1");
        assertThat(summaryCaptor.getValue().degradationReason()).isNull();
    }

    @Test
    void explainReturnsHitChainFromExecutionLog() {
        RuleExecutionLog execution = new RuleExecutionLog(
            1L, "rex-1", "tenant-A", "rule-1", "version-1", "ORDER_SIGN",
            "evt-1", "tester", "sha256:abc", true, RuleRiskLevel.HIGH,
            "[{\"actionCode\":\"STRONG_REMINDER\"}]", "{\"title\":\"抗凝风险提示\"}",
            RuleExecutionStatus.SUCCESS, null, null, Instant.now(), Instant.now(), "trace-rule");
        when(executions.findByExecutionIdAndTenantId("rex-1", "tenant-A")).thenReturn(Optional.of(execution));

        RuleExplanationResponse response = service.explain("rex-1");

        assertThat(response.executionId()).isEqualTo("rex-1");
        assertThat(response.ruleId()).isEqualTo("rule-1");
        assertThat(response.actions().get(0).path("actionCode").asText()).isEqualTo("STRONG_REMINDER");
        assertThat(response.explanation().path("title").asText()).isEqualTo("抗凝风险提示");
        assertThat(response.inputDigest()).isEqualTo("sha256:abc");
    }

    private RuleDefinition existingRule(RuleDefinitionStatus status) {
        return existingRule("rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示", "version-1", status);
    }

    private RuleDefinition existingRule(String ruleId, String tenantId, String ruleCode, String name,
                                        String versionId, RuleDefinitionStatus status) {
        Instant now = Instant.now();
        return new RuleDefinition(
            1L, ruleId, tenantId, ruleCode, name, RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, status, versionId,
            "rpv-1", "dept-1", now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion existingVersion(RuleVersionStatus status) {
        return existingVersion("version-1", "tenant-A", "rule-1", status);
    }

    private RuleVersion existingVersion(String versionId, String tenantId, String ruleId, RuleVersionStatus status) {
        Instant now = Instant.now();
        return new RuleVersion(
            1L, versionId, tenantId, ruleId, 1, "院内抗凝用药管理规范 2026",
            "初始版本", dsl().toString(), dsl().path("explain").toString(), status,
            null, null, null, now, "tester", now, "tester", "trace-rule");
    }

    private RuleTestCase testCase(RuleTestCaseType type, boolean expectedHit, JsonNode input) {
        Instant now = Instant.now();
        return new RuleTestCase(
            null, "case-" + type, "tenant-A", "rule-1", "version-1", type,
            input.toString(), expectedHit, expectedHit ? RuleRiskLevel.HIGH : null,
            expectedHit ? "STRONG_REMINDER" : null, null, null, null, null,
            now, "tester", now, "tester", "trace-rule");
    }

    private JsonNode dsl() {
        return read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {
                "all": [
                  {"fact": "patient.age", "operator": "gte", "value": 18},
                  {"fact": "order.drugClass", "operator": "equals", "value": "ANTICOAGULANT"}
                ]
              },
              "then": [
                {
                  "actionCode": "STRONG_REMINDER",
                  "severity": "HIGH",
                  "message": "抗凝用药需确认出血风险",
                  "requiresPhysicianConfirmation": true
                }
              ],
              "explain": {
                "title": "抗凝风险提示",
                "reason": "患者年龄和医嘱类别满足规则条件",
                "sourceRef": "院内抗凝用药管理规范 2026"
              }
            }
            """);
    }

    private JsonNode hitContext() {
        return read("""
            {"patient": {"age": 72}, "order": {"drugClass": "ANTICOAGULANT"}}
            """);
    }

    private JsonNode boundaryContext() {
        return read("""
            {"patient": {"age": 18}, "order": {"drugClass": "ANTICOAGULANT"}}
            """);
    }

    private JsonNode missContext() {
        return read("""
            {"patient": {"age": 12}, "order": {"drugClass": "ANTICOAGULANT"}}
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
