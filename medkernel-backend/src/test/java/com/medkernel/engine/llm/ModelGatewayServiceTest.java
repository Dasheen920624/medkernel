package com.medkernel.engine.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class ModelGatewayServiceTest {

    private ModelCapabilityTaskRepository taskRepo;
    private ModelCapabilityPolicyRepository policyRepo;
    private ModelCapabilityDefinitionRepository definitionRepo;
    private AuditRecorder auditRecorder;
    private IsolatedAuditPublisher isolatedAudit;
    private ModelGatewayService service;

    @BeforeEach
    void setUp() {
        taskRepo = mock(ModelCapabilityTaskRepository.class);
        policyRepo = mock(ModelCapabilityPolicyRepository.class);
        definitionRepo = mock(ModelCapabilityDefinitionRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        service = new ModelGatewayService(
            taskRepo, policyRepo, definitionRepo, auditRecorder, isolatedAudit);

        List<ModelCapabilityDefinition> definitions = List.of(
            definition("knowledge.discovery", "临床知识关联发现", 10, true),
            definition("knowledge.extract", "电子病历语义实体提取", 20, true),
            definition("terminology.map", "标准术语字典匹配映射", 30, true),
            definition("rule.draft", "临床规则草案拟定", 40, true),
            definition("pathway.draft", "临床路径草案拟定", 50, true),
            definition("cdss.explain", "临床决策解释", 60, true),
            definition("quality.semantic-check", "病历内涵质控", 70, true),
            definition("followup.draft", "随访草案拟定", 80, true)
        );
        when(definitionRepo.findAllByOrderBySortOrderAscCapabilityCodeAsc()).thenReturn(definitions);
        when(definitionRepo.findById(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return definitions.stream()
                .filter(definition -> definition.capabilityCode().equals(code))
                .findFirst();
        });

        // 设置当前线程的租户组织上下文
        RequestContext.restore(new RequestContext.Snapshot("trace-123", OrgScope.tenant("tenant-1"), "DOCTOR-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void getStatus_NoConfig_ReturnsDefaultBaselineWithoutPretendingConfigured() {
        when(policyRepo.findByTenantIdAndCapabilityCode(eq("tenant-1"), any())).thenReturn(Optional.empty());

        List<ModelCapabilityStatusResponse> list = service.getStatus();
        assertNotNull(list);
        assertEquals(8, list.size());
        
        ModelCapabilityStatusResponse discovery = list.stream()
            .filter(r -> "knowledge.discovery".equals(r.capabilityCode()))
            .findFirst()
            .orElse(null);
        
        assertNotNull(discovery);
        assertEquals("BASELINE", discovery.routeStrategy());
        assertEquals("临床知识关联发现", discovery.displayName());
        assertFalse(discovery.configured());
        assertNull(discovery.expectedSchema());
        assertTrue(discovery.fallbackAvailable());
    }

    @Test
    void getStatus_UsesDatabaseCatalogInsteadOfHardCodedCapabilityList() {
        ModelCapabilityDefinition custom = definition(
            "custom.summary", "病历摘要", 5, true);
        when(definitionRepo.findAllByOrderBySortOrderAscCapabilityCodeAsc())
            .thenReturn(List.of(custom));
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "custom.summary"))
            .thenReturn(Optional.empty());

        List<ModelCapabilityStatusResponse> list = service.getStatus();

        assertEquals(1, list.size());
        assertEquals("custom.summary", list.getFirst().capabilityCode());
        assertEquals("病历摘要", list.getFirst().displayName());
    }

    @Test
    void saveDefinition_PersistsCatalogMetadataAndAudits() {
        when(definitionRepo.findById("custom.summary")).thenReturn(Optional.empty());
        when(definitionRepo.save(any(ModelCapabilityDefinition.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ModelCapabilityDefinitionResponse response = service.saveDefinition(
            "custom.summary",
            new ModelCapabilityDefinitionUpsertRequest(
                "病历摘要",
                "生成待人工审核的结构化病历摘要。",
                "语义抽取",
                true,
                25
            )
        );

        assertEquals("custom.summary", response.capabilityCode());
        assertEquals("病历摘要", response.displayName());
        assertTrue(response.enabled());
        verify(definitionRepo).save(argThat(definition ->
            "custom.summary".equals(definition.capabilityCode())
                && "Y".equals(definition.enabledFlag())
                && "DOCTOR-001".equals(definition.createdBy())));
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "model_capability_definition",
            "custom.summary",
            "保存模型能力目录 custom.summary"
        );
    }

    @Test
    void submitTask_DisabledStrategy_ThrowsException() {
        ModelCapabilityPolicy disabledPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "DISABLED", "DEFAULT", null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(disabledPolicy));

        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "测试输入", 60);

        ApiException ex = assertThrows(ApiException.class, () -> service.submitTask(req));
        assertEquals("ENG-LLM-001", ex.errorCode().code());
        verify(isolatedAudit).publishInNewTx(any(AuditEvent.class));
    }

    @Test
    void submitTask_DisabledCatalogCapability_ThrowsBeforePolicyLookup() {
        when(definitionRepo.findById("knowledge.extract"))
            .thenReturn(Optional.of(definition(
                "knowledge.extract", "电子病历语义实体提取", 20, false)));

        ApiException ex = assertThrows(ApiException.class, () -> service.submitTask(
            new ModelTaskRequest("knowledge.extract", "测试输入", 60)));

        assertEquals(ErrorCode.ENG_LLM_001, ex.errorCode());
        verify(policyRepo, never())
            .findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract");
    }

    @Test
    void submitTask_BaselineStrategy_ReturnsHonestEmptyB0Result() {
        ModelCapabilityPolicy baselinePolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "BASELINE", "DEFAULT", null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(baselinePolicy));

        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "提取结构化临床文本信息", 60);

        ModelTaskResponse resp = service.submitTask(req);
        assertNotNull(resp);
        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        assertTrue(resp.outputContent().contains("\"status\":\"NO_MODEL_PROVIDER\""));
        assertTrue(resp.outputContent().contains("\"candidates\":[]"));
        assertFalse(resp.outputContent().contains("临床概念A"));
        assertFalse(resp.outputContent().contains("标准术语A"));

        verify(taskRepo).save(any(ModelCapabilityTask.class));
        // 成功路径走 AuditRecorder（同事务）；isolated 仅用于失败留痕（LLM-M-04）
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("model_capability_task"), anyString(), anyString());
        verify(isolatedAudit, never()).publishInNewTx(any(AuditEvent.class));
    }

    @Test
    void savePolicy_ValidRequest_PersistsTenantPolicyAndReturnsConfiguredStatus() {
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.empty());
        when(policyRepo.save(any(ModelCapabilityPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelCapabilityStatusResponse response = service.savePolicy(
            "knowledge.extract",
            new ModelPolicyUpsertRequest(
                "BASELINE",
                "MASK_ALL",
                "{\"required\":[\"status\",\"candidates\"]}"
            )
        );

        assertEquals("knowledge.extract", response.capabilityCode());
        assertEquals("BASELINE", response.routeStrategy());
        assertEquals("MASK_ALL", response.desensitizeStrategy());
        assertEquals("{\"required\":[\"status\",\"candidates\"]}", response.expectedSchema());
        assertTrue(response.configured());

        ArgumentCaptor<ModelCapabilityPolicy> policyCaptor =
            ArgumentCaptor.forClass(ModelCapabilityPolicy.class);
        verify(policyRepo).save(policyCaptor.capture());
        assertEquals("tenant-1", policyCaptor.getValue().tenantId());
        assertEquals("DOCTOR-001", policyCaptor.getValue().createdBy());
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "model_capability_policy",
            "knowledge.extract",
            "保存模型能力策略 knowledge.extract"
        );
    }

    @Test
    void savePolicy_InvalidRoute_DoesNotPersist() {
        assertThrows(ApiException.class, () -> service.savePolicy(
            "knowledge.extract",
            new ModelPolicyUpsertRequest("UNKNOWN", "DEFAULT", null)
        ));

        verify(policyRepo, never()).save(any(ModelCapabilityPolicy.class));
    }

    @Test
    void submitTask_localModelRoute_honestlyDegradesToB0_noFabrication() {
        ModelCapabilityPolicy modelPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "LOCAL_MODEL", "DEFAULT", null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(modelPolicy));

        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "普通脑卒中病历", 60);
        ModelTaskResponse resp = service.submitTask(req);

        assertNotNull(resp);
        // 未接入真实 provider → 诚实降级 B0，绝不伪造 B1 元数据（宪法 #9/#13）
        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        assertEquals("B0-Deterministic-Baseline", resp.modelVersion());
        assertNull(resp.confidence());
        assertEquals("[]", resp.sourceCitations());
        assertTrue(resp.fallbackReason().contains("未接入本地微调模型(B1)"));
        // 反例：杜绝旧实现伪造的本地模型版本与字段
        assertNotEquals("MedKernel-Local-Cognitive-v1", resp.modelVersion());
        assertFalse(resp.outputContent().contains("local_enhanced"));
        verify(taskRepo).save(any(ModelCapabilityTask.class));
    }

    @Test
    void submitTask_externalModelRoute_honestB0_desensitizesInput_neverFabricates() {
        ModelCapabilityPolicy modelPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT", null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(modelPolicy));

        // EXTERNAL_MODEL 配置但无 provider：诚实降级 B0，并验证手机号脱敏写入摘要
        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "张医生的手机是13988888888", 60);

        ModelTaskResponse resp = service.submitTask(req);
        assertNotNull(resp);
        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        assertNull(resp.confidence());
        assertEquals("[]", resp.sourceCitations());
        assertTrue(resp.fallbackReason().contains("未接入外部大模型/Dify(B2)"));

        // 反例（医疗安全红线）：绝不出现伪造的外部模型版本 / 编造引文 / 编造患者
        assertNotEquals("MedKernel-Cognitive-LLM-v2", resp.modelVersion());
        assertFalse(resp.sourceCitations().contains("溶栓指南"));
        assertFalse(resp.outputContent().contains("李建国"));

        // 验证摘要脱敏过滤（手机号掩码）
        verify(taskRepo).save(argThat(task ->
            task.inputSummary().contains("139****8888") && !task.inputSummary().contains("13988888888")));
    }

    @Test
    void submitTask_withSatisfiedSchema_passesRealJsonValidation() {
        ModelCapabilityPolicy modelPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT",
            "{\"required\":[\"status\",\"candidates\"]}",
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(modelPolicy));

        // B0 空候选信封满足标准 JSON Schema，真实 JSON 校验通过
        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "提取要素", 60);

        ModelTaskResponse resp = service.submitTask(req);
        assertNotNull(resp);
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.outputContent().contains("NO_MODEL_PROVIDER"));
        assertTrue(resp.outputContent().contains("\"candidates\":[]"));
        verify(taskRepo).save(any(ModelCapabilityTask.class));
    }

    @Test
    void submitTask_withUnsatisfiableSchema_throwsRealSchemaErrorAndAuditsFailure() {
        ModelCapabilityPolicy modelPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT",
            "{\"required\":[\"nonexistent_field\"]}",
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(modelPolicy));

        // required 字段在 B0 输出中不存在 → 真实 JSON Schema 校验失败抛 ENG-LLM-002
        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "提取要素", 60);

        ApiException ex = assertThrows(ApiException.class, () -> service.submitTask(req));
        assertEquals("ENG-LLM-002", ex.errorCode().code());
        // 失败路径也发 FAILED 审计；且不得落库成功任务
        verify(isolatedAudit).publishInNewTx(any(AuditEvent.class));
        verify(taskRepo, never()).save(any(ModelCapabilityTask.class));
    }

    @Test
    void submitTask_withNonJsonSchema_rejectsRemovedLooseSyntax() {
        ModelCapabilityPolicy modelPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT", "required: [entity]",
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(modelPolicy));

        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "提取要素", 60);

        ApiException ex = assertThrows(ApiException.class, () -> service.submitTask(req));
        assertEquals(ErrorCode.ENG_LLM_002, ex.errorCode());
        verify(isolatedAudit).publishInNewTx(any(AuditEvent.class));
        verify(taskRepo, never()).save(any(ModelCapabilityTask.class));
    }

    @Test
    void validatePolicy_InvalidSchema_ReturnsInvalid() {
        ModelPolicyValidateRequest req = new ModelPolicyValidateRequest(
            "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT", "invalid_non_json_schema"
        );

        ModelPolicyValidateResponse resp = service.validatePolicy(req);
        assertNotNull(resp);
        assertFalse(resp.valid());
    }

    @Test
    void validatePolicy_Valid_ReturnsOk() {
        ModelPolicyValidateRequest req = new ModelPolicyValidateRequest(
            "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT", "{\"required\":[\"entity\"]}"
        );

        ModelPolicyValidateResponse resp = service.validatePolicy(req);
        assertNotNull(resp);
        assertTrue(resp.valid());
    }

    private static ModelCapabilityDefinition definition(
            String capabilityCode,
            String displayName,
            int sortOrder,
            boolean enabled) {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        return new ModelCapabilityDefinition(
            capabilityCode,
            displayName,
            "能力说明",
            "临床智能",
            enabled ? "Y" : "N",
            sortOrder,
            now,
            "system",
            now,
            "system"
        );
    }
}
