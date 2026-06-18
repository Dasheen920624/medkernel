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
        assertFalse(discovery.inherited());
        assertEquals("TENANT", discovery.policyScopeType());
        assertEquals("tenant-1", discovery.policyScopeRef());
        assertNull(discovery.expectedSchema());
        assertTrue(discovery.fallbackAvailable());
    }

    @Test
    void getStatus_ResolvesNearestOrgScopedPolicyAndMarksInheritedSource() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-dept",
            new OrgScope("tenant-1", "group-a", "hospital-a", "campus-a", null, "dept-cardiology", null, null),
            "DOCTOR-001"));
        ModelCapabilityPolicy hospitalPolicy = new ModelCapabilityPolicy(
            11L, "tenant-1", "knowledge.extract", "HOSPITAL", "hospital-a",
            "LOCAL_MODEL", "MASK_ALL", "{\"required\":[\"status\",\"candidates\"]}",
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "DEPARTMENT", "dept-cardiology"))
            .thenReturn(Optional.empty());
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "CAMPUS", "campus-a"))
            .thenReturn(Optional.empty());
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "HOSPITAL", "hospital-a"))
            .thenReturn(Optional.of(hospitalPolicy));

        List<ModelCapabilityStatusResponse> list = service.getStatus();

        ModelCapabilityStatusResponse status = list.stream()
            .filter(item -> "knowledge.extract".equals(item.capabilityCode()))
            .findFirst()
            .orElseThrow();
        assertEquals("LOCAL_MODEL", status.routeStrategy());
        assertEquals("HOSPITAL", status.policyScopeType());
        assertEquals("hospital-a", status.policyScopeRef());
        assertTrue(status.inherited());
        assertTrue(status.fallbackReason().contains("继承 HOSPITAL:hospital-a"));
    }

    @Test
    void getStatus_UsesDatabaseCatalogInsteadOfHardCodedCapabilityList() {
        ModelCapabilityDefinition custom = definition(
            "custom.summary", "病历摘要", 5, true);
        when(definitionRepo.findAllByOrderBySortOrderAscCapabilityCodeAsc())
            .thenReturn(List.of(custom));

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
            1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "DISABLED", "DEFAULT", null,
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
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
            .findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void submitTask_BaselineStrategy_ReturnsHonestEmptyB0Result() {
        ModelCapabilityPolicy baselinePolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "BASELINE", "DEFAULT", null,
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
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
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
            .thenReturn(Optional.empty());
        when(policyRepo.save(any(ModelCapabilityPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelCapabilityStatusResponse response = service.savePolicy(
            "knowledge.extract",
            new ModelPolicyUpsertRequest(
                "BASELINE",
                "MASK_ALL",
                "{\"required\":[\"status\",\"candidates\"]}",
                List.of("BASELINE"),
                2_000,
                60
            )
        );

        assertEquals("knowledge.extract", response.capabilityCode());
        assertEquals("BASELINE", response.routeStrategy());
        assertEquals("MASK_ALL", response.desensitizeStrategy());
        assertEquals("{\"required\":[\"status\",\"candidates\"]}", response.expectedSchema());
        assertEquals(List.of("BASELINE"), response.fallbackOrder());
        assertEquals(2_000, response.timeoutMs());
        assertEquals(60, response.rateLimitPerMinute());
        assertTrue(response.configured());
        assertFalse(response.inherited());
        assertEquals("TENANT", response.policyScopeType());
        assertEquals("tenant-1", response.policyScopeRef());

        ArgumentCaptor<ModelCapabilityPolicy> policyCaptor =
            ArgumentCaptor.forClass(ModelCapabilityPolicy.class);
        verify(policyRepo).save(policyCaptor.capture());
        assertEquals("tenant-1", policyCaptor.getValue().tenantId());
        assertEquals("TENANT", policyCaptor.getValue().scopeType());
        assertEquals("tenant-1", policyCaptor.getValue().scopeRef());
        assertEquals("[\"BASELINE\"]", policyCaptor.getValue().fallbackOrderJson());
        assertEquals(2_000, policyCaptor.getValue().timeoutMs());
        assertEquals(60, policyCaptor.getValue().rateLimitPerMinute());
        assertEquals("DOCTOR-001", policyCaptor.getValue().createdBy());
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "model_capability_policy",
            "knowledge.extract",
            "保存模型能力策略 knowledge.extract scope=TENANT:tenant-1"
        );
    }

    @Test
    void savePolicy_InvalidRoute_DoesNotPersist() {
        assertThrows(ApiException.class, () -> service.savePolicy(
            "knowledge.extract",
            new ModelPolicyUpsertRequest("UNKNOWN", "DEFAULT", null, null, null, null)
        ));

        verify(policyRepo, never()).save(any(ModelCapabilityPolicy.class));
    }

    @Test
    void validatePolicy_InvalidFallbackOrderRejectsUnsafeChain() {
        ModelPolicyValidateResponse resp = service.validatePolicy(new ModelPolicyValidateRequest(
            "knowledge.extract",
            "EXTERNAL_MODEL",
            "DEFAULT",
            "{\"required\":[\"status\"]}",
            List.of("EXTERNAL_MODEL", "BASELINE", "LOCAL_MODEL"),
            2_000,
            30
        ));

        assertFalse(resp.valid());
        assertTrue(resp.message().contains("fallback_order"));
    }

    @Test
    void submitTask_localModelRoute_honestlyDegradesToB0_noFabrication() {
        ModelCapabilityPolicy modelPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "LOCAL_MODEL", "DEFAULT", null,
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
            .thenReturn(Optional.of(modelPolicy));

        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "普通脑卒中病历", 60);
        ModelTaskResponse resp = service.submitTask(req);

        assertNotNull(resp);
        // 未发布 ACTIVE 版本包 → 在 provider 解析前诚实降级 B0，绝不伪造 B1 元数据（宪法 #9/#13）
        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        assertEquals("B0-Deterministic-Baseline", resp.modelVersion());
        assertNull(resp.confidence());
        assertEquals("[]", resp.sourceCitations());
        assertTrue(resp.fallbackReason().contains("ACTIVE"));
        assertTrue(resp.fallbackReason().contains("版本包"));
        verify(providerRegistry, never()).resolve(anyString(), anyString());
        // 反例：杜绝旧实现伪造的本地模型版本与字段
        assertNotEquals("MedKernel-Local-Cognitive-v1", resp.modelVersion());
        assertFalse(resp.outputContent().contains("local_enhanced"));
        verify(taskRepo).save(any(ModelCapabilityTask.class));
    }

    @Test
    void submitTask_externalModelRoute_honestB0_desensitizesInput_neverFabricates() {
        ModelCapabilityPolicy modelPolicy = new ModelCapabilityPolicy(
            1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "EXTERNAL_MODEL", "DEFAULT", null,
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
            .thenReturn(Optional.of(modelPolicy));

        // EXTERNAL_MODEL 配置但无 ACTIVE 版本包：诚实降级 B0，并验证手机号脱敏写入摘要
        ModelTaskRequest req = new ModelTaskRequest("knowledge.extract", "张医生的手机是13988888888", 60);

        ModelTaskResponse resp = service.submitTask(req);
        assertNotNull(resp);
        assertEquals("DEGRADED", resp.status());
        assertEquals("B0", resp.modelMode());
        assertTrue(resp.fallbackUsed());
        assertNull(resp.confidence());
        assertEquals("[]", resp.sourceCitations());
        assertTrue(resp.fallbackReason().contains("ACTIVE"));
        assertTrue(resp.fallbackReason().contains("版本包"));
        verify(providerRegistry, never()).resolve(anyString(), anyString());

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
            1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "EXTERNAL_MODEL", "DEFAULT",
            "{\"required\":[\"status\",\"candidates\"]}",
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
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
            1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "EXTERNAL_MODEL", "DEFAULT",
            "{\"required\":[\"nonexistent_field\"]}",
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
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
            1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "EXTERNAL_MODEL", "DEFAULT", "required: [entity]",
            null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
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
            "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT", "invalid_non_json_schema", null, null, null
        );

        ModelPolicyValidateResponse resp = service.validatePolicy(req);
        assertNotNull(resp);
        assertFalse(resp.valid());
    }

    @Test
    void validatePolicy_Valid_ReturnsOk() {
        ModelPolicyValidateRequest req = new ModelPolicyValidateRequest(
            "knowledge.extract", "EXTERNAL_MODEL", "DEFAULT", "{\"required\":[\"entity\"]}", null, null, null
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
        String modelVersion = adapter.type() == com.medkernel.engine.llm.provider.ProviderType.OLLAMA
            ? "qwen2.5:7b"
            : "claude-opus-4-8";
        resolveProvider(strategy, adapter, modelVersion);
    }

    private void resolveProvider(String strategy,
                                 com.medkernel.engine.llm.provider.ModelProvider adapter,
                                 String modelVersion) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        com.medkernel.engine.llm.provider.ModelProviderConfig config =
            new com.medkernel.engine.llm.provider.ModelProviderConfig(1L, "tenant-1", "p1",
                adapter.type().name(), "http://x", null, modelVersion, "Y", "HEALTHY", now, "s", now, "s");
        when(providerRegistry.resolve("tenant-1", strategy)).thenReturn(Optional.of(
            new com.medkernel.engine.llm.provider.ModelProviderRegistry.ResolvedProvider(adapter, config)));
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            "tenant-1", "knowledge.extract", "ACTIVE"))
            .thenReturn(Optional.of(versionBundle(modelVersion)));
    }

    private void policy(String strategy) {
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
            .thenReturn(Optional.of(new ModelCapabilityPolicy(
                1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", strategy, "DEFAULT", null,
                null, null, null,
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
    void submitTask_requiredLocalRouteRejectsExternalPolicyWithoutProviderResolution() {
        policy("EXTERNAL_MODEL");

        ApiException ex = assertThrows(ApiException.class, () -> service.submitTask(
            new ModelTaskRequest("knowledge.extract", "提取病史", 60, "LOCAL_MODEL", "ollama-hospital")));

        assertEquals(ErrorCode.BAD_REQUEST, ex.errorCode());
        assertTrue(ex.getMessage().contains("LOCAL_MODEL"));
        assertTrue(ex.getMessage().contains("EXTERNAL_MODEL"));
        verify(providerRegistry, never()).resolve(anyString(), anyString());
        verify(taskRepo, never()).save(any(ModelCapabilityTask.class));
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
    void getTask_crossTenantRejectsWithTenantForbidden() {
        when(taskRepo.findByTaskId("task-other"))
            .thenReturn(Optional.of(storedTask("task-other", "tenant-2")));

        ApiException ex = assertThrows(ApiException.class, () -> service.getTask("task-other"));

        assertEquals(ErrorCode.TENANT_FORBIDDEN, ex.errorCode());
        verify(taskRepo, never()).save(any(ModelCapabilityTask.class));
        verify(auditRecorder, never()).record(any(), anyString(), anyString(), anyString());
    }

    @Test
    void retryTask_crossTenantRejectsBeforeResubmissionAndAudit() {
        when(taskRepo.findByTaskId("task-other"))
            .thenReturn(Optional.of(storedTask("task-other", "tenant-2")));

        ApiException ex = assertThrows(ApiException.class, () -> service.retryTask("task-other"));

        assertEquals(ErrorCode.TENANT_FORBIDDEN, ex.errorCode());
        verify(policyRepo, never())
            .findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(anyString(), anyString(), anyString(), anyString());
        verify(taskRepo, never()).save(any(ModelCapabilityTask.class));
        verify(auditRecorder, never()).record(any(), anyString(), anyString(), anyString());
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
    void submitTask_externalEmptyMinimizedPayloadNeverFallsBackToOriginalPrompt() {
        policy("EXTERNAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.CLAUDE);
        resolveProvider("EXTERNAL_MODEL", adapter, "claude-opus-4-8");
        when(egressGuard.prepareEgress(any(), any(), anyString(), anyString(), any()))
            .thenReturn(new com.medkernel.engine.llm.egress.ModelEgressGuard.EgressPreparation(
                "{}", java.util.List.of(), "hash-empty"));
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion(
                "不应被调用", "claude-opus-4-8", null, "[]"));

        ModelTaskResponse response = service.submitTask(
            new ModelTaskRequest("knowledge.extract", "不得绕过白名单的原始文本", 60));

        assertEquals("DEGRADED", response.status());
        assertEquals("B0", response.modelMode());
        verify(adapter, never()).complete(any(), any());
    }

    @Test
    void submitTask_providerReturnedModelVersionDriftDegradesToB0() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter, "qwen2.5:7b");
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion(
                "漂移模型输出", "qwen2.5:14b", null, "[]"));

        ModelTaskResponse response = service.submitTask(
            new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", response.status());
        assertEquals("B0", response.modelMode());
        assertTrue(response.fallbackReason().contains("模型版本"));
    }

    @Test
    void submitTask_providerReturnedBlankContentDegradesToB0() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter, "qwen2.5:7b");
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion(
                "  ", "qwen2.5:7b", null, "[]"));

        ModelTaskResponse response = service.submitTask(
            new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", response.status());
        assertEquals("B0", response.modelMode());
        assertTrue(response.fallbackReason().contains("补全内容"));
    }

    @Test
    void submitTask_activeVersionBundleMismatchSkipsProvider() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter, "qwen2.5:7b");
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            "tenant-1", "knowledge.extract", "ACTIVE"))
            .thenReturn(Optional.of(versionBundle("qwen2.5:14b")));
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion(
                "不应被调用", "qwen2.5:7b", null, "[]"));

        ModelTaskResponse response = service.submitTask(
            new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", response.status());
        assertEquals("B0", response.modelMode());
        verify(adapter, never()).complete(any(), any());
    }

    @Test
    void submitTask_withoutActiveVersionBundle_degradesBeforeProviderResolution() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter, "qwen2.5:7b");
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            "tenant-1", "knowledge.extract", "ACTIVE")).thenReturn(Optional.empty());
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion(
                "不应被调用", "qwen2.5:7b", null, "[]"));

        ModelTaskResponse response = service.submitTask(
            new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", response.status());
        assertEquals("B0", response.modelMode());
        assertTrue(response.fallbackReason().contains("ACTIVE"));
        assertTrue(response.fallbackReason().contains("版本包"));
        verify(providerRegistry, never()).resolve(anyString(), anyString());
        verify(adapter, never()).complete(any(), any());
    }

    @Test
    void submitTask_malformedActiveVersionBundle_degradesBeforeProviderResolution() {
        policy("LOCAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter, "qwen2.5:7b");
        ModelVersionBundle malformed = versionBundle("qwen2.5:7b");
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            "tenant-1", "knowledge.extract", "ACTIVE")).thenReturn(Optional.of(new ModelVersionBundle(
                malformed.id(), malformed.tenantId(), malformed.capabilityCode(),
                malformed.promptVersion(), "not-sha256", malformed.toolVersion(), malformed.toolHash(),
                malformed.modelVersion(), malformed.modelHash(), malformed.status(), malformed.activeScopeKey(),
                malformed.effectiveAt(), malformed.retiredAt(), malformed.createdAt(), malformed.createdBy(),
                malformed.updatedAt(), malformed.updatedBy())));
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion(
                "不应被调用", "qwen2.5:7b", null, "[]"));

        ModelTaskResponse response = service.submitTask(
            new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("DEGRADED", response.status());
        assertEquals("B0", response.modelMode());
        assertTrue(response.fallbackReason().contains("ACTIVE 版本包不可执行"));
        verify(providerRegistry, never()).resolve(anyString(), anyString());
        verify(adapter, never()).complete(any(), any());
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
    void submitTask_externalRateLimitedFallsBackToHealthyLocalBeforeB0ByConfiguredOrder() {
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
            .thenReturn(Optional.of(new ModelCapabilityPolicy(
                1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1",
                "EXTERNAL_MODEL", "DEFAULT", null,
                "[\"EXTERNAL_MODEL\",\"LOCAL_MODEL\",\"BASELINE\"]", 1_500, 20,
                Instant.now(), "system", Instant.now(), "system")));
        var external = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.CLAUDE);
        var local = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("EXTERNAL_MODEL", external);
        resolveProvider("LOCAL_MODEL", local, "claude-opus-4-8");
        when(egressGuard.prepareEgress(any(), any(), anyString(), anyString(), any()))
            .thenReturn(new com.medkernel.engine.llm.egress.ModelEgressGuard.EgressPreparation(
                "{\"prompt\":\"已脱敏\"}", java.util.List.of("prompt"), "hash-chain"));
        when(external.complete(any(), any()))
            .thenThrow(new ApiException(ErrorCode.TOO_MANY_REQUESTS, "429"));
        when(local.complete(any(), argThat(request -> request.timeoutMs() == 1_500)))
            .thenReturn(new com.medkernel.engine.llm.provider.ProviderCompletion(
                "本地候选", "claude-opus-4-8", null, "[]"));

        ModelTaskResponse resp = service.submitTask(new ModelTaskRequest("knowledge.extract", "提取病史", 60));

        assertEquals("SUCCEEDED", resp.status());
        assertEquals("B1", resp.modelMode());
        assertEquals("claude-opus-4-8", resp.modelVersion());
        assertTrue(resp.fallbackUsed());
        assertTrue(resp.fallbackReason().contains("PROVIDER_RATE_LIMITED"));
        assertTrue(resp.fallbackReason().contains("B2 -> B1"));
        assertFalse(resp.fallbackReason().contains("B2 -> B0"));
        verify(providerRegistry).resolve("tenant-1", "EXTERNAL_MODEL");
        verify(providerRegistry).resolve("tenant-1", "LOCAL_MODEL");
    }

    @Test
    void submitTask_policyRateLimitExceededSkipsProviderAndFallsBackToB0() {
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
            .thenReturn(Optional.of(new ModelCapabilityPolicy(
                1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1",
                "LOCAL_MODEL", "DEFAULT", null,
                "[\"LOCAL_MODEL\",\"BASELINE\"]", 2_000, 1,
                Instant.now(), "system", Instant.now(), "system")));
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.OLLAMA);
        resolveProvider("LOCAL_MODEL", adapter);
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion("本地候选", "qwen2.5:7b", null, "[]"));

        ModelTaskResponse first = service.submitTask(new ModelTaskRequest("knowledge.extract", "第一次", 60));
        ModelTaskResponse second = service.submitTask(new ModelTaskRequest("knowledge.extract", "第二次", 60));

        assertEquals("SUCCEEDED", first.status());
        assertEquals("B1", first.modelMode());
        assertEquals("DEGRADED", second.status());
        assertEquals("B0", second.modelMode());
        assertTrue(second.fallbackUsed());
        assertTrue(second.fallbackReason().contains("PROVIDER_RATE_LIMITED"));
        assertTrue(second.fallbackReason().contains("B1 -> B0"));
        verify(adapter, times(1)).complete(any(), any());
    }

    @Test
    void submitTask_providerStructuredOutputInvalid_degradesToB0AndPersistsMatrixReason() {
        policy("EXTERNAL_MODEL");
        var adapter = providerAdapter(com.medkernel.engine.llm.provider.ProviderType.CLAUDE);
        resolveProvider("EXTERNAL_MODEL", adapter);
        when(policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            "tenant-1", "knowledge.extract", "TENANT", "tenant-1"))
            .thenReturn(Optional.of(new ModelCapabilityPolicy(
                1L, "tenant-1", "knowledge.extract", "TENANT", "tenant-1", "EXTERNAL_MODEL", "DEFAULT",
                "{\"required\":[\"status\",\"candidates\"]}",
                null, null, null,
                Instant.now(), "system", Instant.now(), "system")));
        when(egressGuard.prepareEgress(any(), any(), anyString(), anyString(), any()))
            .thenReturn(new com.medkernel.engine.llm.egress.ModelEgressGuard.EgressPreparation(
                "{\"prompt\":\"已脱敏\"}", java.util.List.of("prompt"), "hash-schema"));
        when(adapter.complete(any(), any())).thenReturn(
            new com.medkernel.engine.llm.provider.ProviderCompletion(
                "{\"raw\":\"无结构\"}", "claude-opus-4-8", null, "[]"));

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
        return versionBundle("qwen2.5:7b");
    }

    private static ModelVersionBundle versionBundle(String modelVersion) {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        String hash = "a".repeat(64);
        return new ModelVersionBundle(
            1L, "tenant-1", "knowledge.extract",
            "prompt:extract-v2", hash,
            "tool:extract-schema-v3", hash,
            modelVersion, hash,
            "ACTIVE", "tenant-1|knowledge.extract", now, null, now, "ops", now, "ops");
    }

    private static String b0Output(String capabilityCode) {
        return "{\"status\":\"NO_MODEL_PROVIDER\",\"capability\":\"" + capabilityCode
            + "\",\"candidates\":[],\"message\":\"当前未接入可用模型 provider，未生成候选内容\"}";
    }

    private static ModelCapabilityTask storedTask(String taskId, String tenantId) {
        Instant now = Instant.parse("2026-06-16T01:00:00Z");
        return new ModelCapabilityTask(
            41L,
            taskId,
            tenantId,
            "knowledge.extract",
            "hash",
            "已脱敏输入摘要",
            b0Output("knowledge.extract"),
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
            now,
            "ops",
            now,
            "ops"
        );
    }
}
