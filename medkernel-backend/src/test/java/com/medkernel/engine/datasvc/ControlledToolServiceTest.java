package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.acquisition.AcquisitionOrchestrationService;
import com.medkernel.engine.knowledge.acquisition.KnowledgeAcquisitionRunRequest;
import com.medkernel.engine.knowledge.acquisition.KnowledgeAcquisitionRunResponse;
import com.medkernel.engine.knowledge.acquisition.KnowledgeAcquisitionRunStatus;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;
import com.medkernel.engine.knowledge.production.CandidateSubmissionRequest;
import com.medkernel.engine.knowledge.production.CandidateSubmissionResponse;
import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.KnowledgeProductionOrchestrationService;
import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.knowledge.production.ProductionCandidateView;
import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 受控工具服务单元测试（DATASVC-01 PR2，CLI/MCP 共用受控工具执行入口）。
 *
 * <p>验证：工具目录登记（FR-3/4）、执行包裹 FR-4 治理信封（traceId/数据级别/脱敏策略/来源版本/权限结果/
 * 降级状态/输出 hash）、不绕治理仅调既有受控服务（FR-5）、每次调用留审计含输出 hash（FR-6）、
 * 上游降级诚实透传不伪装（FR-7/铁律 #1）、未知工具返回结构化原因不泄漏内部（FR-4）。
 */
class ControlledToolServiceTest {

    private RuleUsageStatsService ruleUsageStatsService;
    private KnowledgeUsageStatsService knowledgeUsageStatsService;
    private ClinicalSignalsService clinicalSignalsService;
    private RuleExplanationService ruleExplanationService;
    private KnowledgeExistenceService knowledgeExistenceService;
    private KnowledgeSearchService knowledgeSearchService;
    private PrivacyPolicyService privacyPolicyService;
    private ClinicalContextService clinicalContextService;
    private KnowledgeProductionOrchestrationService productionService;
    private AcquisitionOrchestrationService acquisitionService;
    private PermissionEvaluator permissionEvaluator;
    private AuditRecorder auditRecorder;
    private ControlledToolService service;

    @BeforeEach
    void setUp() {
        ruleUsageStatsService = mock(RuleUsageStatsService.class);
        knowledgeUsageStatsService = mock(KnowledgeUsageStatsService.class);
        clinicalSignalsService = mock(ClinicalSignalsService.class);
        ruleExplanationService = mock(RuleExplanationService.class);
        knowledgeExistenceService = mock(KnowledgeExistenceService.class);
        knowledgeSearchService = mock(KnowledgeSearchService.class);
        privacyPolicyService = mock(PrivacyPolicyService.class);
        clinicalContextService = mock(ClinicalContextService.class);
        productionService = mock(KnowledgeProductionOrchestrationService.class);
        acquisitionService = mock(AcquisitionOrchestrationService.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ControlledToolService(ruleUsageStatsService, knowledgeUsageStatsService,
            clinicalSignalsService, ruleExplanationService, knowledgeExistenceService,
            knowledgeSearchService, privacyPolicyService, clinicalContextService, productionService,
            acquisitionService, permissionEvaluator, auditRecorder, new ObjectMapper());
        when(permissionEvaluator.has("engine-data.read")).thenReturn(true);
        RequestContext.restore(new RequestContext.Snapshot("trace-xyz", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private ToolExecutionRequest req() {
        return new ToolExecutionRequest("AI Agent 解释规则使用", null, null, null, 0, 20);
    }

    private ToolExecutionRequest reqTarget(String target) {
        return new ToolExecutionRequest("AI Agent 解释规则使用", target, null, null, 0, 20);
    }

    private RuleUsageStatsResponse ruleResponse(long total, boolean degraded) {
        return new RuleUsageStatsResponse(EngineDataLevel.D2, total, 0, 20, List.of(),
            Instant.parse("2026-06-14T00:00:00Z"), degraded, degraded ? "上游不可用" : null);
    }

    private ToolExecutionRequest agentReq(AgentProductionCandidatePayload payload) {
        return new ToolExecutionRequest("AI Agent 回写生产候选", null, null, null, 0, 20, payload);
    }

    private AgentProductionCandidatePayload agentPayload(CandidateSubmissionRequest submission) {
        return new AgentProductionCandidatePayload("job-agent", "idem-agent-1", "D1", submission);
    }

    private ToolExecutionRequest fetchReq(Object payload) {
        return new ToolExecutionRequest("Agent 受控获取公域资料", null, null, null, 0, 20, payload);
    }

    private Map<String, Object> publicMaterialPayload(String dataLevel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceCode", "NHC-HTN");
        payload.put("url", "https://guideline.example.org/htn.txt");
        payload.put("versionNo", "v2026");
        payload.put("format", "STRUCTURED_TEXT");
        if (dataLevel != null) {
            payload.put("dataLevel", dataLevel);
        }
        return payload;
    }

    private CandidateSubmissionRequest validSubmission() {
        String payload = "{\"aiGenerated\":true,\"sections\":{\"summary\":\"仅作为待审候选\"}}";
        KnowledgeAssetEnvelope envelope = new KnowledgeAssetEnvelope(
            VersionedAssetType.RULE,
            "rule:agent:1",
            "Agent 回写规则候选",
            "agent-draft-v1",
            List.of(new AssetSourceRef("GL-HTN-2024:v1:section-1", SourceAuthorityLevel.B_GUIDELINE)),
            SourceAuthorityLevel.B_GUIDELINE,
            null,
            null,
            KnowledgeRiskLevel.MEDIUM,
            "tenant-1",
            Sha256ContentHash.sha256(payload, "资产内容不能为空"),
            payload,
            AssetVersionStatus.DRAFT);
        return new CandidateSubmissionRequest(envelope, new MaterializationTarget(77L, null));
    }

    @Test
    void listTools_registersControlledToolsUnderEngineDataRead() {
        List<ControlledToolDescriptor> tools = service.listTools();

        assertThat(tools).extracting(ControlledToolDescriptor::name)
            .contains("queryRuleUsage", "summarizeEngineSignals");
        // 读工具统一受 engine-data.read 管控；写候选工具单独受 knowledge.write 管控。
        assertThat(tools)
            .filteredOn(t -> !List.of("submitProductionCandidate", "fetchPublicMaterial").contains(t.name()))
            .allSatisfy(t ->
            assertThat(t.requiredPermission()).isEqualTo("engine-data.read"));
        // D2 聚合类工具数据级别为 D2。
        assertThat(tools).filteredOn(t -> t.name().equals("queryRuleUsage"))
            .singleElement()
            .satisfies(t -> assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D2));
    }

    @Test
    void listTools_registersSubmitProductionCandidateAsWriteTool() {
        List<ControlledToolDescriptor> tools = service.listTools();

        assertThat(tools).filteredOn(t -> t.name().equals("submitProductionCandidate"))
            .singleElement()
            .satisfies(t -> {
                assertThat(t.requiredPermission()).isEqualTo("knowledge.write");
                assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D1);
                assertThat(t.purpose()).contains("回写候选");
            });
    }

    @Test
    void listTools_registersFetchPublicMaterialAsWriteTool() {
        List<ControlledToolDescriptor> tools = service.listTools();

        assertThat(tools).filteredOn(t -> t.name().equals("fetchPublicMaterial"))
            .singleElement()
            .satisfies(t -> {
                assertThat(t.requiredPermission()).isEqualTo("knowledge.write");
                assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D1);
                assertThat(t.purpose()).contains("公域资料");
            });
    }

    @Test
    void execute_fetchPublicMaterial_requiresKnowledgeWrite() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(false);

        assertThatThrownBy(() -> service.execute("fetchPublicMaterial", fetchReq(publicMaterialPayload("D1"))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(acquisitionService, never()).run(any());
    }

    @Test
    void execute_fetchPublicMaterial_rejectsD5PatientDataBeforeAcquisition() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);

        assertThatThrownBy(() -> service.execute("fetchPublicMaterial", fetchReq(publicMaterialPayload("D5"))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.AGENT_PATIENT_DATA_FORBIDDEN);
        verify(acquisitionService, never()).run(any());
    }

    @Test
    void execute_fetchPublicMaterial_usesPublicMaterialErrorForInvalidDataLevel() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);

        assertThatThrownBy(() -> service.execute("fetchPublicMaterial", fetchReq(publicMaterialPayload("UNKNOWN"))))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(error).hasMessageContaining("Agent 公域资料获取载荷不合格"))
            .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
        verify(acquisitionService, never()).run(any());
    }

    @Test
    void execute_fetchPublicMaterial_runsAcquisitionThroughControlledServiceAndAuditsOutputHash() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);
        when(acquisitionService.run(any())).thenReturn(new KnowledgeAcquisitionRunResponse(
            "acq:agent:1",
            KnowledgeAcquisitionRunStatus.SUCCEEDED,
            "NHC-HTN",
            "https://guideline.example.org/htn.txt",
            "guideline.example.org",
            "b".repeat(64),
            128L,
            "text/plain",
            "file:///var/medkernel/materials/NHC-HTN/v2026.txt",
            7L,
            8L,
            "parse:1",
            null,
            null));

        ToolExecutionEnvelope envelope =
            service.execute("fetchPublicMaterial", fetchReq(publicMaterialPayload("D1")));

        assertThat(envelope.toolName()).isEqualTo("fetchPublicMaterial");
        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(envelope.outputHash()).matches("[0-9a-f]{64}");
        assertThat(envelope.payload()).isInstanceOf(KnowledgeAcquisitionRunResponse.class);
        assertThat(((KnowledgeAcquisitionRunResponse) envelope.payload()).materialFileUri()).startsWith("file://");
        ArgumentCaptor<KnowledgeAcquisitionRunRequest> request =
            ArgumentCaptor.forClass(KnowledgeAcquisitionRunRequest.class);
        verify(acquisitionService).run(request.capture());
        assertThat(request.getValue().sourceCode()).isEqualTo("NHC-HTN");
        assertThat(request.getValue().url()).isEqualTo("https://guideline.example.org/htn.txt");
        assertThat(request.getValue().versionNo()).isEqualTo("v2026");
        assertThat(request.getValue().format()).isEqualTo(DocumentFormat.STRUCTURED_TEXT);
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("engine_data_tool"),
            eq("fetchPublicMaterial"), contains("输出hash="));
    }

    @Test
    void execute_submitProductionCandidate_requiresKnowledgeWrite() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(false);

        assertThatThrownBy(() -> service.execute("submitProductionCandidate", agentReq(agentPayload(validSubmission()))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(productionService, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void execute_submitProductionCandidate_rejectsD5PatientDataBeforeSubmit() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);
        AgentProductionCandidatePayload payload =
            new AgentProductionCandidatePayload("job-agent", "idem-agent-1", "D5", validSubmission());

        assertThatThrownBy(() -> service.execute("submitProductionCandidate", agentReq(payload)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.AGENT_PATIENT_DATA_FORBIDDEN);
        verify(productionService, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void execute_submitProductionCandidate_rejectsD4PatientDataBeforeSubmit() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);
        AgentProductionCandidatePayload payload =
            new AgentProductionCandidatePayload("job-agent", "idem-agent-1", "D4", validSubmission());

        assertThatThrownBy(() -> service.execute("submitProductionCandidate", agentReq(payload)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.AGENT_PATIENT_DATA_FORBIDDEN);
        verify(productionService, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void execute_submitProductionCandidate_requiresAnchoredSourceAndAiHash() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);
        CandidateSubmissionRequest invalid = new CandidateSubmissionRequest(
            new KnowledgeAssetEnvelope(
                VersionedAssetType.RULE, "rule:agent:1", "Agent 回写规则候选", "agent-draft-v1",
                List.of(new AssetSourceRef("GL-HTN-2024", SourceAuthorityLevel.B_GUIDELINE)),
                SourceAuthorityLevel.B_GUIDELINE, null, null, KnowledgeRiskLevel.MEDIUM, "tenant-1",
                "bad-hash", "{\"aiGenerated\":false}", AssetVersionStatus.DRAFT),
            new MaterializationTarget(77L, null));

        assertThatThrownBy(() -> service.execute("submitProductionCandidate", agentReq(agentPayload(invalid))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.AGENT_CANDIDATE_SCHEMA_INVALID);
        verify(productionService, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void execute_submitProductionCandidate_submitsViaProductionServiceAndAuditsOutputHash() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);
        when(productionService.listCandidates(eq("job-agent"), anyInt(), anyInt()))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));
        when(productionService.submitCandidate(eq("job-agent"), any(), any())).thenReturn(
            new CandidateSubmissionResponse("candidate:agent:1",
                new ReviewRoutingDecision(RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.CLINICAL_GOVERNOR,
                    false, KnowledgeDomain.CLINICAL)));

        ToolExecutionEnvelope envelope =
            service.execute("submitProductionCandidate", agentReq(agentPayload(validSubmission())));

        assertThat(envelope.toolName()).isEqualTo("submitProductionCandidate");
        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(envelope.outputHash()).matches("[0-9a-f]{64}");
        assertThat(envelope.payload()).isInstanceOf(CandidateSubmissionResponse.class);
        assertThat(((CandidateSubmissionResponse) envelope.payload()).candidateRef()).isEqualTo("candidate:agent:1");
        verify(productionService).submitCandidate(eq("job-agent"), any(), any());
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("engine_data_tool"),
            eq("submitProductionCandidate"), contains("输出hash="));
    }

    @Test
    void execute_submitProductionCandidate_isIdempotentWhenContentHashAlreadySubmitted() {
        when(permissionEvaluator.has("knowledge.write")).thenReturn(true);
        CandidateSubmissionRequest submission = validSubmission();
        when(productionService.listCandidates(eq("job-agent"), anyInt(), anyInt()))
            .thenReturn(PageResponse.of(List.of(new ProductionCandidateView(
                "job-agent",
                submission.candidate().assetIdentity(),
                submission.candidate().contentHash(),
                "candidate:existing",
                KnowledgeRiskLevel.MEDIUM,
                Instant.parse("2026-06-14T00:00:00Z"),
                "agent",
                new ReviewRoutingDecision(RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.CLINICAL_GOVERNOR,
                    false, KnowledgeDomain.CLINICAL))), PageRequest.defaults(), 1L));

        ToolExecutionEnvelope envelope =
            service.execute("submitProductionCandidate", agentReq(agentPayload(submission)));

        assertThat(((CandidateSubmissionResponse) envelope.payload()).candidateRef()).isEqualTo("candidate:existing");
        verify(productionService, never()).submitCandidate(any(), any(), any());
    }

    @Test
    void listTools_registersExplainRuleAndCheckKnowledgeExistenceAtD1() {
        List<ControlledToolDescriptor> tools = service.listTools();

        assertThat(tools).extracting(ControlledToolDescriptor::name)
            .contains("explainRule", "checkKnowledgeExistence");
        // 单对象解释/存在性工具为 D1 已发布资产元数据。
        assertThat(tools).filteredOn(t -> t.name().equals("explainRule"))
            .singleElement()
            .satisfies(t -> assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D1));
        assertThat(tools).filteredOn(t -> t.name().equals("checkKnowledgeExistence"))
            .singleElement()
            .satisfies(t -> assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D1));
    }

    @Test
    void execute_explainRule_wrapsRuleMetadataInEnvelopeAtD1() {
        when(ruleExplanationService.explainRule("R-7")).thenReturn(new RuleExplanation(
            "R-7", "CODE-7", "高危药物配伍规则", "ORDER", "HIGH", "PUBLISHED", "v9", "pkg-1.0",
            EngineDataLevel.D1, Instant.parse("2026-06-14T00:00:00Z"), false, null));

        ToolExecutionEnvelope envelope = service.execute("explainRule", reqTarget("R-7"));

        assertThat(envelope.toolName()).isEqualTo("explainRule");
        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(envelope.permissionGranted()).isTrue();
        assertThat(envelope.degraded()).isFalse();
        assertThat(envelope.traceId()).isEqualTo("trace-xyz");
        assertThat(envelope.outputHash()).matches("[0-9a-f]{64}");
        assertThat(envelope.desensitizationPolicy()).isNotBlank();
        assertThat(envelope.payload()).isInstanceOf(RuleExplanation.class);
        assertThat(((RuleExplanation) envelope.payload()).ruleCode()).isEqualTo("CODE-7");
    }

    @Test
    void execute_explainRule_missingTarget_throwsStructuredErrorNotLeakingInternals() {
        // explainRule 必须指定目标规则；缺 target 结构化拒绝，不泄漏内部。
        assertThatThrownBy(() -> service.execute("explainRule", req()))
            .isInstanceOf(ApiException.class)
            .hasMessageNotContaining("SQL")
            .hasMessageNotContaining("Exception");
    }

    @Test
    void execute_checkKnowledgeExistence_returnsExistsFalseHonestlyNotError() {
        when(knowledgeExistenceService.checkExistence("GONE")).thenReturn(new KnowledgeExistence(
            "GONE", false, null, null, EngineDataLevel.D1,
            Instant.parse("2026-06-14T00:00:00Z"), false, null));

        ToolExecutionEnvelope envelope = service.execute("checkKnowledgeExistence", reqTarget("GONE"));

        // 真实不存在＝诚实回答（非降级非报错，铁律 #1）。
        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(envelope.degraded()).isFalse();
        assertThat(envelope.payload()).isInstanceOf(KnowledgeExistence.class);
        assertThat(((KnowledgeExistence) envelope.payload()).exists()).isFalse();
    }

    @Test
    void listTools_registersSearchKnowledgeAndValidatePrivacyPolicy() {
        List<ControlledToolDescriptor> tools = service.listTools();

        assertThat(tools).extracting(ControlledToolDescriptor::name)
            .contains("searchKnowledge", "validatePrivacyPolicy");
        assertThat(tools).filteredOn(t -> t.name().equals("searchKnowledge"))
            .singleElement()
            .satisfies(t -> assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D1));
        // 策略判定结果为 D0 策略元数据。
        assertThat(tools).filteredOn(t -> t.name().equals("validatePrivacyPolicy"))
            .singleElement()
            .satisfies(t -> assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D0));
    }

    @Test
    void execute_searchKnowledge_wrapsHitsInEnvelopeAtD1() {
        when(knowledgeSearchService.search("糖尿病", 0, 20)).thenReturn(new KnowledgeSearchResult(
            EngineDataLevel.D1, 1L, 0, 20,
            List.of(new KnowledgeSearchHit("K-DM", "糖尿病诊疗指南", "GUIDELINE", "ACTIVE")),
            Instant.parse("2026-06-14T00:00:00Z"), false, null));

        ToolExecutionEnvelope envelope = service.execute("searchKnowledge", reqTarget("糖尿病"));

        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(envelope.outputHash()).matches("[0-9a-f]{64}");
        assertThat(envelope.payload()).isInstanceOf(KnowledgeSearchResult.class);
        assertThat(((KnowledgeSearchResult) envelope.payload()).total()).isEqualTo(1L);
    }

    @Test
    void execute_validatePrivacyPolicy_deniesD5AtD0Envelope() {
        when(privacyPolicyService.validate("D5")).thenReturn(new PrivacyPolicyDecision(
            "D5", false, false, "重要个人信息禁入数据服务/CLI/MCP/模型输入（FR-2）",
            EngineDataLevel.D0, Instant.parse("2026-06-14T00:00:00Z")));

        ToolExecutionEnvelope envelope = service.execute("validatePrivacyPolicy", reqTarget("D5"));

        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D0);
        assertThat(envelope.degraded()).isFalse();
        assertThat(envelope.payload()).isInstanceOf(PrivacyPolicyDecision.class);
        assertThat(((PrivacyPolicyDecision) envelope.payload()).allowed()).isFalse();
    }

    @Test
    void listTools_registersGetClinicalContextExplanationAtD4() {
        List<ControlledToolDescriptor> tools = service.listTools();

        assertThat(tools).extracting(ControlledToolDescriptor::name).contains("getClinicalContextExplanation");
        assertThat(tools).filteredOn(t -> t.name().equals("getClinicalContextExplanation"))
            .singleElement()
            .satisfies(t -> assertThat(t.dataLevel()).isEqualTo(EngineDataLevel.D4));
    }

    @Test
    void execute_getClinicalContextExplanation_wrapsMaskedContextAtD4WithMaskedPolicy() {
        when(clinicalContextService.explainContext("tok-1", "AI 解释临床会话授权范围"))
            .thenReturn(new ClinicalContextExplanation(true, "已校验", "order-sign", "clinical-decision-user",
                "IFRAME", "ref:abc123def456", "ref:999000aaa111",
                Instant.parse("2026-06-14T01:00:00Z"), EngineDataLevel.D4,
                Instant.parse("2026-06-14T00:00:00Z"), false, null));

        ToolExecutionEnvelope envelope = service.execute("getClinicalContextExplanation",
            new ToolExecutionRequest("AI 解释临床会话授权范围", "tok-1", null, null, 0, 20));

        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D4);
        assertThat(envelope.desensitizationPolicy()).isEqualTo("D4_MASKED_MINIMAL_CONTEXT");
        assertThat(envelope.outputHash()).matches("[0-9a-f]{64}");
        assertThat(envelope.payload()).isInstanceOf(ClinicalContextExplanation.class);
        ClinicalContextExplanation ctx = (ClinicalContextExplanation) envelope.payload();
        assertThat(ctx.authorized()).isTrue();
        assertThat(ctx.patientRef()).startsWith("ref:");
    }

    @Test
    void execute_getClinicalContextExplanation_missingLaunchToken_throwsStructured() {
        assertThatThrownBy(() -> service.execute("getClinicalContextExplanation", req()))
            .isInstanceOf(ApiException.class)
            .hasMessageNotContaining("SQL")
            .hasMessageNotContaining("Exception");
    }

    @Test
    void execute_queryRuleUsage_wrapsResultInGovernanceEnvelope() {
        when(ruleUsageStatsService.queryRuleUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(ruleResponse(7L, false));

        ToolExecutionEnvelope envelope = service.execute("queryRuleUsage", req());

        assertThat(envelope.toolName()).isEqualTo("queryRuleUsage");
        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D2);
        assertThat(envelope.permissionGranted()).isTrue();
        assertThat(envelope.degraded()).isFalse();
        assertThat(envelope.traceId()).isEqualTo("trace-xyz");
        // 输出 hash 必须是真实 SHA-256 指纹（FR-6 审计输出 hash）
        assertThat(envelope.outputHash()).matches("[0-9a-f]{64}");
        assertThat(envelope.desensitizationPolicy()).isNotBlank();
        assertThat(envelope.payload()).isInstanceOf(RuleUsageStatsResponse.class);
        assertThat(((RuleUsageStatsResponse) envelope.payload()).total()).isEqualTo(7L);
    }

    @Test
    void execute_summarizeEngineSignals_aggregatesThreeReadModels() {
        when(ruleUsageStatsService.queryRuleUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(ruleResponse(5L, false));
        when(knowledgeUsageStatsService.queryKnowledgeUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(new KnowledgeUsageStatsResponse(EngineDataLevel.D2, 3L, 0, 20, List.of(),
                Instant.parse("2026-06-14T00:00:00Z"), false, null));
        when(clinicalSignalsService.queryClinicalSignals(any(), any(), anyInt(), anyInt()))
            .thenReturn(new ClinicalSignalsResponse(EngineDataLevel.D2, 2L, 0, 20, List.of(),
                Instant.parse("2026-06-14T00:00:00Z"), false, null));

        ToolExecutionEnvelope envelope = service.execute("summarizeEngineSignals", req());

        assertThat(envelope.dataLevel()).isEqualTo(EngineDataLevel.D2);
        assertThat(envelope.payload()).isInstanceOf(EngineSignalsSummary.class);
        EngineSignalsSummary summary = (EngineSignalsSummary) envelope.payload();
        assertThat(summary.ruleGroups()).isEqualTo(5L);
        assertThat(summary.knowledgeGroups()).isEqualTo(3L);
        assertThat(summary.clinicalSignalGroups()).isEqualTo(2L);
    }

    @Test
    void execute_summarizeEngineSignals_propagatesDegradedHonestlyWhenAnyUpstreamDegraded() {
        when(ruleUsageStatsService.queryRuleUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(ruleResponse(0L, true));
        when(knowledgeUsageStatsService.queryKnowledgeUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(new KnowledgeUsageStatsResponse(EngineDataLevel.D2, 0L, 0, 20, List.of(),
                Instant.parse("2026-06-14T00:00:00Z"), false, null));
        when(clinicalSignalsService.queryClinicalSignals(any(), any(), anyInt(), anyInt()))
            .thenReturn(new ClinicalSignalsResponse(EngineDataLevel.D2, 0L, 0, 20, List.of(),
                Instant.parse("2026-06-14T00:00:00Z"), false, null));

        ToolExecutionEnvelope envelope = service.execute("summarizeEngineSignals", req());

        // 任一上游降级则汇总诚实标降级，不伪装完整（铁律 #1 / FR-7）。
        assertThat(envelope.degraded()).isTrue();
        assertThat(envelope.degradeReason()).isNotBlank();
    }

    @Test
    void execute_unknownTool_throwsStructuredErrorNotLeakingInternals() {
        assertThatThrownBy(() -> service.execute("dropAllTables", req()))
            .isInstanceOf(ApiException.class)
            .hasMessageNotContaining("SQL")
            .hasMessageNotContaining("Exception");
    }

    @Test
    void execute_recordsToolCallAuditWithPurposeAndLevel() {
        when(ruleUsageStatsService.queryRuleUsage(any(), any(), anyInt(), anyInt()))
            .thenReturn(ruleResponse(1L, false));

        service.execute("queryRuleUsage", req());

        verify(auditRecorder, times(1)).record(eq(AuditAction.EXECUTE), eq("engine_data_tool"),
            eq("queryRuleUsage"), contains("AI Agent 解释规则使用"));
    }
}
