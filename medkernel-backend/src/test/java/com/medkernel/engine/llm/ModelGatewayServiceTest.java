package com.medkernel.engine.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private com.medkernel.engine.llm.provider.ModelProviderRegistry providerRegistry;
    private com.medkernel.engine.llm.egress.ModelEgressGuard egressGuard;
    private ModelVersionBundleRepository versionBundleRepository;
    private ModelGatewayService service;

    @BeforeEach
    void setUp() {
        taskRepo = mock(ModelCapabilityTaskRepository.class);
        policyRepo = mock(ModelCapabilityPolicyRepository.class);
        definitionRepo = mock(ModelCapabilityDefinitionRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        providerRegistry = mock(com.medkernel.engine.llm.provider.ModelProviderRegistry.class);
        egressGuard = mock(com.medkernel.engine.llm.egress.ModelEgressGuard.class);
        versionBundleRepository = mock(ModelVersionBundleRepository.class);
        service = new ModelGatewayService(
            taskRepo, policyRepo, definitionRepo, auditRecorder, isolatedAudit,
            providerRegistry, egressGuard, versionBundleRepository);

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
        assertTrue(resp.fallbackReason().contains("PROVIDER_UNAVAILABLE"));
        assertTrue(resp.fallbackReason().contains("B1"));
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
        assertTrue(resp.fallbackReason().contains("PROVIDER_UNAVAILABLE"));
        assertTrue(resp.fallbackReason().contains("B2"));

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

    // ── LLM-08 真实 provider 接入路径（registry 返回健康 provider 时产出真实结果）─────────────

    private com.medkernel.engine.llm.provider.ModelProvider providerAdapter(
            com.medkernel.engine.llm.provider.ProviderType type) {
        com.medkernel.engine.llm.provider.ModelProvider adapter =
            mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        when(adapter.type()).thenReturn(type);
        return adapter;
    }

    private void resolveProvider(String strategy, com.medkernel.engine.llm.provider.ModelProvider adapter) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        com.medkernel.engine.llm.provider.ModelProviderConfig config =
            new com.medkernel.engine.llm.provider.ModelProviderConfig(1L, "tenant-1", "p1",
                adapter.type().name(), "http://x", null, "v1", "Y", "HEALTHY", now, "s", now, "s");
        when(providerRegistry.resolve("tenant-1", strategy)).thenReturn(Optional.of(
            new com.medkernel.engine.llm.provider.ModelProviderRegistry.ResolvedProvider(adapter, config)));
    }

    private void policy(String strategy) {
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(new ModelCapabilityPolicy(
                1L, "tenant-1", "knowledge.extract", strategy, "DEFAULT", null,
                Instant.now(), "system", Instant.now(), "system")));
    }

    @Test
    void submitTask_withHealthyLocalProvider_producesRealB1Output() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter);
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion("候选：高血压病史", "qwen2.5:7b", null, "[]"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("SUCCEEDED", resp.status());
        assertEquals("B1", resp.modelMode());
        assertEquals("qwen2.5:7b", resp.modelVersion());
        assertFalse(resp.fallbackUsed());
        assertTrue(resp.outputContent().contains("候选：高血压病史"));
    }

    @Test
    void submitTask_withActiveVersionBundle_recordsPromptToolModelTriple() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter);
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            "tenant-1", "knowledge.extract", "ACTIVE")).thenReturn(Optional.of(versionBundle()));
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion("结构化候选", "qwen2.5:7b", null, "[]"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("prompt:extract-v2", resp.promptVersion());
        assertEquals("tool:extract-schema-v3", resp.toolVersion());
        assertEquals("qwen2.5:7b", resp.modelVersion());
        verify(taskRepo).save(argThat(task ->
            "prompt:extract-v2".equals(task.promptVersion())
                && "tool:extract-schema-v3".equals(task.toolVersion())
                && "qwen2.5:7b".equals(task.modelVersion())));
    }

    @Test
    void replayTask_usesStoredB0InputSummaryAndVersionTripleWithoutActiveVersionDrift() {
        String originalOutput = b0Output("knowledge.extract");
        when(taskRepo.findByTaskId("task-original")).thenReturn(Optional.of(new ModelCapabilityTask(
            31L,
            "task-original",
            "tenant-1",
            "knowledge.extract",
            "original-input-hash",
            "已脱敏输入摘要",
            originalOutput,
            "B0",
            "B0-Deterministic-Baseline",
            "prompt:extract-v1",
            "tool:extract-schema-v1",
            "[]",
            null,
            "LOW",
            true,
            "[LLM-02:POLICY_BASELINE] B0 -> B0：策略显式指定 B0 基线",
            12L,
            "DEGRADED",
            "trace-original",
            Instant.parse("2026-06-16T01:00:00Z"),
            "ops",
            Instant.parse("2026-06-16T01:00:00Z"),
            "ops")));
        when(taskRepo.save(any(ModelCapabilityTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelTaskResponse replay = service.replayTask("task-original");

        assertEquals("REPLAYED", replay.status());
        assertEquals(originalOutput, replay.outputContent());
        assertEquals("B0", replay.modelMode());
        assertEquals("B0-Deterministic-Baseline", replay.modelVersion());
        assertEquals("prompt:extract-v1", replay.promptVersion());
        assertEquals("tool:extract-schema-v1", replay.toolVersion());
        assertTrue(replay.fallbackReason().contains("LLM-04_REPLAY_FROM=task-original"));
        verify(providerRegistry, never()).resolve(anyString(), anyString());
        verify(versionBundleRepository, never())
            .findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(anyString(), anyString(), anyString());
        verify(taskRepo).save(argThat(task ->
            "已脱敏输入摘要".equals(task.inputSummary())
                && "prompt:extract-v1".equals(task.promptVersion())
                && "tool:extract-schema-v1".equals(task.toolVersion())
                && "B0-Deterministic-Baseline".equals(task.modelVersion())
                && task.fallbackReason().contains("LLM-04_REPLAY_FROM=task-original")));
    }

    @Test
    void replayTask_rejectsProviderTaskInsteadOfPretendingDeterministicReproduction() {
        when(taskRepo.findByTaskId("task-provider")).thenReturn(Optional.of(new ModelCapabilityTask(
            32L,
            "task-provider",
            "tenant-1",
            "knowledge.extract",
            "hash",
            "已脱敏输入摘要",
            "{\"entity\":\"高血压\"}",
            "B2",
            "claude-opus-4",
            "prompt:extract-v2",
            "tool:extract-schema-v2",
            "[]",
            0.92,
            "LOW",
            false,
            null,
            120L,
            "SUCCEEDED",
            "trace-provider",
            Instant.parse("2026-06-16T01:00:00Z"),
            "ops",
            Instant.parse("2026-06-16T01:00:00Z"),
            "ops")));

        ApiException ex = assertThrows(ApiException.class, () -> service.replayTask("task-provider"));

        assertEquals(ErrorCode.BAD_REQUEST, ex.errorCode());
        assertTrue(ex.getMessage().contains("仅支持 B0"));
        verify(taskRepo, never()).save(any(ModelCapabilityTask.class));
    }

    @Test
    void submitTask_withHealthyExternalProvider_passesEgressThenProducesRealB2Output() {
        policy("EXTERNAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.CLAUDE);
        resolveProvider("EXTERNAL_MODEL", adapter);
        when(egressGuard.prepareEgress(eq("tenant-1"), eq("knowledge.extract"), anyString(), anyString(), any()))
            .thenReturn(new com.medkernel.engine.llm.egress.ModelEgressGuard.EgressPreparation(
                "{\"prompt\":\"已脱敏\"}", java.util.List.of("prompt"), "hash-1"));
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion("外部候选", "claude-opus-4-8", null, "[]"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("SUCCEEDED", resp.status());
        assertEquals("B2", resp.modelMode());
        assertEquals("claude-opus-4-8", resp.modelVersion());
        assertFalse(resp.fallbackUsed());
        // B2 外调必先过出域闸
        verify(egressGuard).prepareEgress(eq("tenant-1"), eq("knowledge.extract"), anyString(), anyString(), any());
    }

    @Test
    void submitTask_externalEgressBlocked_degradesToB0WithoutCallingProvider() {
        policy("EXTERNAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.CLAUDE);
        resolveProvider("EXTERNAL_MODEL", adapter);
        when(egressGuard.prepareEgress(any(), any(), anyString(), anyString(), any()))
            .thenThrow(new ApiException(ErrorCode.ENG_LLM_006, "未配置白名单"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        verify(adapter, never()).complete(any(), any());
    }

    @Test
    void submitTask_providerCallFails_degradesToB0() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter);
        when(adapter.complete(any(), any())).thenThrow(new ApiException(ErrorCode.ENG_LLM_003, "断连"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
    }

    @Test
    void submitTask_providerRateLimited_degradesToB0WithMatrixReason() {
        policy("EXTERNAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.CLAUDE);
        resolveProvider("EXTERNAL_MODEL", adapter);
        when(egressGuard.prepareEgress(any(), any(), anyString(), anyString(), any()))
            .thenReturn(new com.medkernel.engine.llm.egress.ModelEgressGuard.EgressPreparation(
                "{\"prompt\":\"已脱敏\"}", java.util.List.of("prompt"), "hash-429"));
        when(adapter.complete(any(), any()))
            .thenThrow(new ApiException(ErrorCode.TOO_MANY_REQUESTS, "429"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        assertTrue(resp.fallbackReason().contains("PROVIDER_RATE_LIMITED"));
    }

    @Test
    void submitTask_providerStructuredOutputInvalid_degradesToB0AndPersistsMatrixReason() {
        policy("EXTERNAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.CLAUDE);
        resolveProvider("EXTERNAL_MODEL", adapter);
        when(policyRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(new ModelCapabilityPolicy(
                1L, "tenant-1", "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT",
                "{\"required\":[\"status\",\"candidates\"]}",
                Instant.now(), "system", Instant.now(), "system")));
        when(egressGuard.prepareEgress(any(), any(), anyString(), anyString(), any()))
            .thenReturn(new com.medkernel.engine.llm.egress.ModelEgressGuard.EgressPreparation(
                "{\"prompt\":\"已脱敏\"}", java.util.List.of("prompt"), "hash-schema"));
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion("{\"raw\":\"无结构\"}", "claude-opus-4", null, "[]"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        assertTrue(resp.fallbackReason().contains("STRUCTURED_OUTPUT_FAILED"));
        verify(taskRepo).save(argThat(task -> task.fallbackReason().contains("STRUCTURED_OUTPUT_FAILED")));
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

    private static ModelVersionBundle versionBundle() {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        return new ModelVersionBundle(
            1L, "tenant-1", "knowledge.extract",
            "prompt:extract-v2", "p-hash",
            "tool:extract-schema-v3", "t-hash",
            "model:qwen2.5:7b", "m-hash",
            "ACTIVE", now, null, now, "ops", now, "ops");
    }

    private static String b0Output(String capabilityCode) {
        return "{\"status\":\"NO_MODEL_PROVIDER\",\"capability\":\"" + capabilityCode
            + "\",\"candidates\":[],\"message\":\"当前未接入可用模型 provider，未生成候选内容\"}";
    }
}
