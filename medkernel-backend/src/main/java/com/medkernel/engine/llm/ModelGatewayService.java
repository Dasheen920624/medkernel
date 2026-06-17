package com.medkernel.engine.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.engine.llm.egress.ModelEgressGuard;
import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.engine.llm.provider.ProviderCompletion;
import com.medkernel.engine.llm.provider.ProviderRequest;

/**
 * 模型能力网关核心领域服务实现类 (GA-ENG-API-12)。
 *
 * <p>统一管控模型能力调用：能力阻断、正则数据脱敏、期待结构 Schema 校验，并通过物理子事务强隔离记录审计日志。
 * 当前经 provider 注册表解析 B1/B2；provider 缺位、出域阻断、结构化失败或调用失败时按 LLM-02
 * 降级矩阵如实返回 B0（无模型确定性基线），禁止伪造 B2 模型名、置信度或来源引文。
 */
@Service
public class ModelGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ModelGatewayService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern CAPABILITY_CODE_PATTERN =
        Pattern.compile("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+");
    private static final Set<String> ROUTE_STRATEGIES =
        Set.of("DISABLED", "BASELINE", "LOCAL_MODEL", "EXTERNAL_MODEL");
    private static final Set<String> DESENSITIZE_STRATEGIES =
        Set.of("DEFAULT", "MASK_ALL", "NONE");

    private final ModelCapabilityTaskRepository taskRepo;
    private final ModelCapabilityPolicyRepository policyRepo;
    private final ModelCapabilityDefinitionRepository definitionRepo;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final ModelProviderRegistry providerRegistry;
    private final ModelEgressGuard egressGuard;
    private final ModelVersionBundleRepository versionBundleRepository;
    private final ModelFallbackMatrix fallbackMatrix = new ModelFallbackMatrix();

    public ModelGatewayService(ModelCapabilityTaskRepository taskRepo,
                               ModelCapabilityPolicyRepository policyRepo,
                               ModelCapabilityDefinitionRepository definitionRepo,
                               AuditRecorder auditRecorder,
                               IsolatedAuditPublisher isolatedAudit,
                               ModelProviderRegistry providerRegistry,
                               ModelEgressGuard egressGuard,
                               ModelVersionBundleRepository versionBundleRepository) {
        this.taskRepo = taskRepo;
        this.policyRepo = policyRepo;
        this.definitionRepo = definitionRepo;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
        this.providerRegistry = providerRegistry;
        this.egressGuard = egressGuard;
        this.versionBundleRepository = versionBundleRepository;
    }

    /**
     * 扫描获取当前租户全部可用模型能力状态。
     *
     * @return 模型能力可用清单
     */
    @Transactional(readOnly = true)
    public List<ModelCapabilityStatusResponse> getStatus() {
        String tenantId = requireCurrentTenant();
        return definitionRepo.findAllByOrderBySortOrderAscCapabilityCodeAsc().stream()
            .filter(ModelCapabilityDefinition::enabled)
            .map(definition -> {
                String code = definition.capabilityCode();
                PolicyResolution resolution = resolvePolicy(tenantId, code);
                if (resolution.policy().isPresent()) {
                    return statusOf(definition, resolution.policy().get(), true, resolution.inherited());
                }
                ModelPolicyScope scope = resolution.currentScope();
                return new ModelCapabilityStatusResponse(
                        code,
                        definition.displayName(),
                        definition.description(),
                        definition.category(),
                        "BASELINE",
                        "DEFAULT",
                        null,
                        scope.scopeType(),
                        scope.scopeRef(),
                        false,
                        false,
                        true,
                        "未配置专属策略，使用系统 B0 基线"
                    );
            })
            .toList();
    }

    /**
     * 查询平台模型能力目录，包括已停用项。
     */
    @Transactional(readOnly = true)
    public List<ModelCapabilityDefinitionResponse> listDefinitions() {
        requireCurrentTenant();
        return definitionRepo.findAllByOrderBySortOrderAscCapabilityCodeAsc().stream()
            .map(ModelCapabilityDefinitionResponse::from)
            .toList();
    }

    /**
     * 新增或更新平台模型能力目录项。
     */
    @Transactional
    public ModelCapabilityDefinitionResponse saveDefinition(
            String capabilityCode,
            ModelCapabilityDefinitionUpsertRequest request) {
        requireCurrentTenant();
        String normalizedCode = normalizeCapabilityCode(capabilityCode);
        if (!CAPABILITY_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new ApiException(
                ErrorCode.ENG_LLM_002,
                "能力代码必须使用小写点号分段格式，例如 knowledge.extract"
            );
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        Optional<ModelCapabilityDefinition> existing = definitionRepo.findById(normalizedCode);
        ModelCapabilityDefinition saved = definitionRepo.save(new ModelCapabilityDefinition(
            normalizedCode,
            request.displayName().trim(),
            request.description().trim(),
            request.category().trim(),
            Boolean.TRUE.equals(request.enabled()) ? "Y" : "N",
            request.sortOrder(),
            existing.map(ModelCapabilityDefinition::createdAt).orElse(now),
            existing.map(ModelCapabilityDefinition::createdBy).orElse(actor),
            now,
            actor,
            existing.isEmpty()
        ));
        auditRecorder.record(
            AuditAction.UPDATE,
            "model_capability_definition",
            normalizedCode,
            "保存模型能力目录 " + normalizedCode
        );
        return ModelCapabilityDefinitionResponse.from(saved);
    }

    /**
     * 保存当前租户指定能力的路由策略。
     */
    @Transactional
    public ModelCapabilityStatusResponse savePolicy(
            String capabilityCode,
            ModelPolicyUpsertRequest request) {
        String tenantId = requireCurrentTenant();
        String normalizedCapability = normalizeCapabilityCode(capabilityCode);
        ModelCapabilityDefinition definition = requireEnabledDefinition(normalizedCapability);
        String routeStrategy = normalizeCode(request.routeStrategy());
        String desensitizeStrategy = normalizeCode(request.desensitizeStrategy());
        String expectedSchema = normalizeOptional(request.expectedSchema());

        ModelPolicyValidateResponse validation = validatePolicy(new ModelPolicyValidateRequest(
            normalizedCapability,
            routeStrategy,
            desensitizeStrategy,
            expectedSchema
        ));
        if (!validation.valid()) {
            throw new ApiException(ErrorCode.ENG_LLM_002, validation.message());
        }

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ModelPolicyScope scope = currentPolicyScope(tenantId);
        Optional<ModelCapabilityPolicy> existing =
            policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
                tenantId, normalizedCapability, scope.scopeType(), scope.scopeRef());
        ModelCapabilityPolicy saved = policyRepo.save(new ModelCapabilityPolicy(
            existing.map(ModelCapabilityPolicy::id).orElse(null),
            tenantId,
            normalizedCapability,
            scope.scopeType(),
            scope.scopeRef(),
            routeStrategy,
            desensitizeStrategy,
            expectedSchema,
            existing.map(ModelCapabilityPolicy::createdAt).orElse(now),
            existing.map(ModelCapabilityPolicy::createdBy).orElse(actor),
            now,
            actor
        ));
        auditRecorder.record(
            AuditAction.UPDATE,
            "model_capability_policy",
            normalizedCapability,
            "保存模型能力策略 " + normalizedCapability + " scope=" + scope.label()
        );
        return statusOf(definition, saved, true, false);
    }

    /**
     * 提交推理或抽取提取任务，由网关执行路由、脱敏、Schema校验与降级回退。
     *
     * @param req 任务提交流入参数
     * @return 任务推理或降级回退结果
     */
    @Transactional
    public ModelTaskResponse submitTask(ModelTaskRequest req) {
        String tenantId = requireCurrentTenant();
        String createdBy = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        String capabilityCode = normalizeCapabilityCode(req.capabilityCode());
        requireEnabledDefinition(capabilityCode);

        long startTime = System.currentTimeMillis();
        String taskId = "task-" + UUID.randomUUID().toString().replace("-", "");

        // 1. 获取或创建策略配置
        PolicyResolution policyResolution = resolvePolicy(tenantId, capabilityCode);
        ModelPolicyScope defaultScope = policyResolution.currentScope();
        ModelCapabilityPolicy policy = policyResolution.policy()
            .orElseGet(() -> new ModelCapabilityPolicy(
                null, tenantId, capabilityCode, defaultScope.scopeType(), defaultScope.scopeRef(),
                "BASELINE", "DEFAULT", null, Instant.now(), createdBy, Instant.now(), createdBy
            ));

        // 2. 校验策略禁用阻断
        if ("DISABLED".equalsIgnoreCase(policy.routeStrategy())) {
            publishFailureAudit(ErrorCode.ENG_LLM_001, "提交任务失败，能力已被禁用 capabilityCode=" + capabilityCode);
            throw new ApiException(ErrorCode.ENG_LLM_001, "模型能力 " + capabilityCode + " 已经被组织禁用");
        }

        // 3. 敏感数据脱敏过滤与Hash计算
        String desensitizedInput = ModelDataDesensitizer.desensitize(req.inputData(), policy.desensitizeStrategy());
        String inputHash = computeSha256(req.inputData());
        String inputSummary = desensitizedInput.length() > 500 ? desensitizedInput.substring(0, 500) : desensitizedInput;

        // 4. 路由与推理：按策略解析真实 provider（B1 本地 / B2 外部）。有健康 provider 且（B2）过出域闸
        //    → 真实增强产出；缺位/断连/形态禁外部/出域阻断/调用失败 → 诚实降级 B0。
        //    据铁律 #1/#2/#4，绝不伪造 B1/B2 模型名、置信度、来源引文或患者数据。
        String strategy = policy.routeStrategy();
        ModelVersionTriple plannedTriple = activeTripleOrBaseline(tenantId, capabilityCode);
        RouteOutcome outcome = route(tenantId, capabilityCode, strategy, desensitizedInput, taskId, plannedTriple);

        // 结构化输出 Schema 校验：真实解析 JSON + required 字段存在性校验（GA-ENG-LLM-01）。
        // 校验对象为本次实际产出；B1/B2 结构化失败先诚实降级 B0，再校验 B0 信封。
        String schemaConstraint = policy.expectedSchema();
        if (schemaConstraint != null && !schemaConstraint.isBlank()) {
            try {
                validateSchema(outcome.outputContent(), schemaConstraint);
            } catch (ApiException schemaError) {
                if (!"B0".equalsIgnoreCase(outcome.modelMode())) {
                    ModelFallbackDecision decision = fallbackMatrix.decide(
                        strategy, ModelFallbackTrigger.STRUCTURED_OUTPUT_FAILED, schemaError.getMessage());
                    log.warn("模型输出结构化失败，按 LLM-02 降级 B0 capabilityCode={}：{}",
                        capabilityCode, schemaError.getMessage());
                    outcome = b0Outcome(capabilityCode, decision.reason());
                    validateSchema(outcome.outputContent(), schemaConstraint);
                } else {
                    log.warn("结构化输出 Schema 校验失败 capabilityCode={}：{}",
                        capabilityCode, schemaError.getMessage());
                    publishFailureAudit(schemaError.errorCode(),
                        "结构化输出 Schema 校验失败 capabilityCode=" + capabilityCode + "：" + schemaError.getMessage());
                    throw schemaError;
                }
            }
        }

        String outputContent = outcome.outputContent();
        String modelMode = outcome.modelMode();
        String modelVersion = outcome.modelVersion();
        String promptVersion = outcome.promptVersion();
        String toolVersion = outcome.toolVersion();
        String sourceCitations = outcome.sourceCitations();
        Double confidence = outcome.confidence();
        String riskLevel = outcome.riskLevel();
        boolean fallbackUsed = outcome.fallbackUsed();
        String fallbackReason = outcome.fallbackReason();
        String taskStatus = outcome.taskStatus();

        long timeCost = System.currentTimeMillis() - startTime;

        // 5. 持久化记录
        ModelCapabilityTask task = new ModelCapabilityTask(
            null,
            taskId,
            tenantId,
            capabilityCode,
            inputHash,
            inputSummary,
            outputContent,
            modelMode,
            modelVersion,
            promptVersion,
            toolVersion,
            sourceCitations,
            confidence,
            riskLevel,
            fallbackUsed,
            fallbackReason,
            timeCost,
            taskStatus,
            traceId,
            Instant.now(),
            createdBy,
            Instant.now(),
            createdBy
        );
        taskRepo.save(task);

        // 6. 成功留痕：成功路径走 AuditRecorder（AFTER_COMMIT 同事务一致性，符合 IsolatedAuditPublisher
        //    契约——isolated 仅用于失败留痕）；retryTask 亦走 AuditRecorder，模块内统一（LLM-M-04）。
        auditRecorder.record(
            AuditAction.EXECUTE,
            "model_capability_task",
            taskId,
            String.format("推理任务完成 capabilityCode=%s mode=%s fallback=%b cost=%dms",
                capabilityCode, modelMode, fallbackUsed, timeCost)
        );

        return new ModelTaskResponse(
            taskId,
            taskStatus,
            outputContent,
            modelMode,
            modelVersion,
            promptVersion,
            toolVersion,
            sourceCitations,
            confidence,
            riskLevel,
            fallbackUsed,
            fallbackReason,
            timeCost,
            traceId
        );
    }

    /**
     * 根据任务ID追溯模型网关推理任务的流转状况与详情。
     *
     * @param taskId 任务唯一ID
     * @return 任务详情
     */
    @Transactional(readOnly = true)
    public ModelTaskResponse getTask(String taskId) {
        String tenantId = requireCurrentTenant();
        ModelCapabilityTask task = taskRepo.findByTaskId(taskId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_LLM_004, "任务不存在"));

        if (!tenantId.equals(task.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN, "无权访问此任务");
        }

        return new ModelTaskResponse(
            task.taskId(),
            task.status(),
            task.outputContent(),
            task.modelMode(),
            task.modelVersion(),
            task.promptVersion(),
            task.toolVersion(),
            task.sourceCitations(),
            task.confidence(),
            task.riskLevel(),
            task.fallbackUsed(),
            task.fallbackReason(),
            task.timeCostMs(),
            task.traceId()
        );
    }

    /**
     * 重试失败的任务或将失败任务由人工强行走向 B0 基线回退。
     *
     * @param taskId 原任务ID
     * @return 新任务响应
     */
    @Transactional
    public ModelTaskResponse retryTask(String taskId) {
        String tenantId = requireCurrentTenant();
        ModelCapabilityTask task = taskRepo.findByTaskId(taskId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_LLM_004, "任务不存在"));

        if (!tenantId.equals(task.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN, "无权访问此任务");
        }

        // 以原任务输入摘要重新发起一次提交（当前所有能力均走 B0 确定性基线）
        ModelTaskRequest retryReq = new ModelTaskRequest(
            task.capabilityCode(),
            task.inputSummary(),
            60
        );

        auditRecorder.record(AuditAction.EXECUTE, "model_capability_task", taskId, "触发失败任务重试");
        return submitTask(retryReq);
    }

    /**
     * 按任务 ID 做审计重放复现。
     *
     * <p>LLM-04 的可复现重放只对 B0 确定性任务成立；B1/B2 provider 结果受外部模型状态影响，
     * 不能伪装为可逐字复现，必须由真实评测/审计链另行举证。
     *
     * @param taskId 原任务 ID
     * @return 新重放任务响应
     */
    @Transactional
    public ModelTaskResponse replayTask(String taskId) {
        String tenantId = requireCurrentTenant();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        ModelCapabilityTask task = taskRepo.findByTaskId(taskId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_LLM_004, "任务不存在"));

        if (!tenantId.equals(task.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN, "无权访问此任务");
        }
        if (!"B0".equalsIgnoreCase(task.modelMode())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "仅支持 B0 确定性任务按 task_id 重放复现");
        }

        String promptVersion = requireReplayVersion(task.promptVersion(), "prompt_version");
        String toolVersion = requireReplayVersion(task.toolVersion(), "tool_version");
        String modelVersion = requireReplayVersion(task.modelVersion(), "model_version");
        long startTime = System.currentTimeMillis();
        String replayTaskId = "task-replay-" + UUID.randomUUID().toString().replace("-", "");
        String outputContent = executeB0Fallback(task.capabilityCode());
        long timeCost = System.currentTimeMillis() - startTime;
        String fallbackReason = (task.fallbackReason() == null || task.fallbackReason().isBlank())
            ? "[LLM-04_REPLAY_FROM=" + task.taskId() + "] B0 deterministic replay"
            : task.fallbackReason() + "；[LLM-04_REPLAY_FROM=" + task.taskId() + "] B0 deterministic replay";

        ModelCapabilityTask replay = new ModelCapabilityTask(
            null,
            replayTaskId,
            tenantId,
            task.capabilityCode(),
            task.inputHash(),
            task.inputSummary(),
            outputContent,
            "B0",
            modelVersion,
            promptVersion,
            toolVersion,
            task.sourceCitations() == null ? "[]" : task.sourceCitations(),
            null,
            task.riskLevel(),
            true,
            fallbackReason,
            timeCost,
            "REPLAYED",
            traceId,
            Instant.now(),
            actor,
            Instant.now(),
            actor
        );
        taskRepo.save(replay);
        auditRecorder.record(AuditAction.EXECUTE, "model_capability_task", replayTaskId,
            "按 task_id 重放模型版本三元组 " + task.taskId());

        return new ModelTaskResponse(
            replayTaskId,
            "REPLAYED",
            outputContent,
            "B0",
            modelVersion,
            promptVersion,
            toolVersion,
            task.sourceCitations() == null ? "[]" : task.sourceCitations(),
            null,
            task.riskLevel(),
            true,
            fallbackReason,
            timeCost,
            traceId
        );
    }

    /**
     * 发布路由及脱敏策略前的边界合法性校验，验证是否具备合法的 B0 验收通道。
     *
     * @param req 策略发布前校验参数
     * @return 校验判定结果
     */
    @Transactional(readOnly = true)
    public ModelPolicyValidateResponse validatePolicy(ModelPolicyValidateRequest req) {
        String capabilityCode = normalizeCapabilityCode(req.capabilityCode());
        Optional<ModelCapabilityDefinition> definition = definitionRepo.findById(capabilityCode);
        if (definition.isEmpty()) {
            return new ModelPolicyValidateResponse(false, "非法的能力标识代码: " + req.capabilityCode(), false);
        }
        if (!definition.get().enabled()) {
            return new ModelPolicyValidateResponse(false, "模型能力目录已停用: " + capabilityCode, false);
        }

        String routeStrategy = normalizeCode(req.routeStrategy());
        if (!ROUTE_STRATEGIES.contains(routeStrategy)) {
            return new ModelPolicyValidateResponse(false, "不支持的模型路由策略: " + req.routeStrategy(), false);
        }

        String desensitizeStrategy = normalizeCode(req.desensitizeStrategy());
        if (!DESENSITIZE_STRATEGIES.contains(desensitizeStrategy)) {
            return new ModelPolicyValidateResponse(
                false,
                "不支持的数据脱敏策略: " + req.desensitizeStrategy(),
                !"DISABLED".equals(routeStrategy)
            );
        }

        // 运行前与发布前共用同一严格 Schema 契约。
        if (req.expectedSchema() != null && !req.expectedSchema().isBlank()) {
            try {
                extractRequiredFields(req.expectedSchema());
            } catch (ApiException invalidSchema) {
                return new ModelPolicyValidateResponse(false, invalidSchema.getMessage(), true);
            }
        }

        boolean fallbackAvailable = !"DISABLED".equals(routeStrategy);
        return new ModelPolicyValidateResponse(
            true,
            fallbackAvailable ? "模型路由与 B0 降级策略校验通过" : "能力停用策略校验通过",
            fallbackAvailable
        );
    }

    // ─── 私有安全与脱敏控制逻辑 ────────────────────────────────────────────────────────

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private void publishFailureAudit(ErrorCode code, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.EXECUTE, "model_capability_task", null, code.code(), summary));
    }

    private ModelCapabilityStatusResponse statusOf(
            ModelCapabilityDefinition definition,
            ModelCapabilityPolicy policy,
            boolean configured,
            boolean inherited) {
        boolean fallbackAvailable = !"DISABLED".equalsIgnoreCase(policy.routeStrategy());
        String reason = fallbackAvailable ? "正常可用" : "已被路由策略禁用";
        if (configured && inherited) {
            reason = reason + "，继承 " + policy.scopeType() + ":" + policy.scopeRef();
        }
        return new ModelCapabilityStatusResponse(
            policy.capabilityCode(),
            definition.displayName(),
            definition.description(),
            definition.category(),
            policy.routeStrategy(),
            policy.desensitizeStrategy(),
            policy.expectedSchema(),
            policy.scopeType(),
            policy.scopeRef(),
            inherited,
            configured,
            fallbackAvailable,
            reason
        );
    }

    private PolicyResolution resolvePolicy(String tenantId, String capabilityCode) {
        List<ModelPolicyScope> candidates =
            ModelPolicyScope.candidates(RequestContext.currentOrgScope(), tenantId);
        ModelPolicyScope current = candidates.getFirst();
        for (ModelPolicyScope scope : candidates) {
            Optional<ModelCapabilityPolicy> policy =
                policyRepo.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
                    tenantId, capabilityCode, scope.scopeType(), scope.scopeRef());
            if (policy.isPresent()) {
                return new PolicyResolution(policy, current, scope);
            }
        }
        return new PolicyResolution(Optional.empty(), current, null);
    }

    private ModelPolicyScope currentPolicyScope(String tenantId) {
        return ModelPolicyScope.current(RequestContext.currentOrgScope(), tenantId);
    }

    private record PolicyResolution(
        Optional<ModelCapabilityPolicy> policy,
        ModelPolicyScope currentScope,
        ModelPolicyScope matchedScope
    ) {
        boolean inherited() {
            return matchedScope != null && !matchedScope.equals(currentScope);
        }
    }

    private ModelCapabilityDefinition requireEnabledDefinition(String capabilityCode) {
        String normalizedCode = normalizeCapabilityCode(capabilityCode);
        ModelCapabilityDefinition definition = definitionRepo.findById(normalizedCode)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_LLM_001,
                "模型能力未登记: " + normalizedCode
            ));
        if (!definition.enabled()) {
            throw new ApiException(
                ErrorCode.ENG_LLM_001,
                "模型能力目录已停用: " + normalizedCode
            );
        }
        return definition;
    }

    private static String normalizeCapabilityCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireReplayVersion(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "原任务缺少 " + fieldName + "，不能重放复现");
        }
        return value.trim();
    }

    private String computeSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要计算失败", e);
        }
    }

    /**
     * 结构化输出 Schema 校验：用 Jackson 将输出解析为 JSON（对象/数组），
     * 再按 expectedSchema 声明的 required 字段集做存在性校验；任一不满足抛 {@code ENG_LLM_002}。
     *
     * <p>相较旧实现的字符串 {@code contains}，此处对输出做真实 JSON 解析，杜绝"看起来含某关键字即通过"的伪校验。
     */
    private void validateSchema(String content, String schema) {
        JsonNode output;
        try {
            output = OBJECT_MAPPER.readTree(content);
        } catch (Exception parseError) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "模型输出无法解析为合法 JSON，结构化 Schema 校验失败");
        }
        if (output == null || !(output.isObject() || output.isArray())) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "模型输出不是 JSON 对象或数组，无法满足结构化 Schema");
        }
        for (String required : extractRequiredFields(schema)) {
            if (!hasField(output, required)) {
                throw new ApiException(ErrorCode.ENG_LLM_002, "模型输出字段缺失 Schema 指定 required: " + required);
            }
        }
    }

    /**
     * 从标准 JSON Schema 对象提取 {@code required} 字段名。
     */
    private Set<String> extractRequiredFields(String schema) {
        Set<String> fields = new LinkedHashSet<>();
        JsonNode schemaNode;
        try {
            schemaNode = OBJECT_MAPPER.readTree(schema);
        } catch (Exception parseError) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待 Schema 必须是合法 JSON 对象");
        }
        if (schemaNode == null || !schemaNode.isObject()) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待 Schema 必须是合法 JSON 对象");
        }
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode == null || !requiredNode.isArray()) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待 Schema 必须声明 required 字符串数组");
        }
        for (JsonNode node : requiredNode) {
            if (!node.isTextual() || node.asText().isBlank()) {
                throw new ApiException(ErrorCode.ENG_LLM_002, "期待 Schema 的 required 只能包含非空字段名");
            }
            fields.add(node.asText().trim());
        }
        if (fields.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待 Schema 的 required 不能为空");
        }
        return fields;
    }

    /** 判断 JSON 节点是否含某字段：对象看自身键，数组要求每个对象元素均含该字段。 */
    private boolean hasField(JsonNode node, String field) {
        if (node.isObject()) {
            return node.has(field);
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                return false;
            }
            for (JsonNode element : node) {
                if (!element.isObject() || !element.has(field)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** 单次任务的路由产出（真实增强或诚实 B0 降级）。 */
    private record RouteOutcome(
        String outputContent, String modelMode, String modelVersion, String promptVersion, String toolVersion,
        String sourceCitations, Double confidence, String riskLevel,
        boolean fallbackUsed, String fallbackReason, String taskStatus) {}

    /**
     * 按路由策略解析真实 provider 并产出；任一环节不可用一律诚实降级 B0（铁律 #1/#2/#4）。
     */
    private RouteOutcome route(String tenantId, String capabilityCode, String strategy,
                               String desensitizedInput, String taskId, ModelVersionTriple plannedTriple) {
        var resolved = providerRegistry.resolve(tenantId, strategy);
        if (resolved.isEmpty()) {
            // 无配置 / 全不健康 / 运行侧内网形态禁外部 → 诚实 B0。
            ModelFallbackTrigger trigger = "BASELINE".equalsIgnoreCase(strategy)
                ? ModelFallbackTrigger.POLICY_BASELINE
                : ModelFallbackTrigger.PROVIDER_UNAVAILABLE;
            return b0Outcome(capabilityCode, fallbackMatrix.decide(
                strategy, trigger, "未解析到健康 provider 或部署形态不允许").reason());
        }
        ModelProviderRegistry.ResolvedProvider provider = resolved.get();

        String prompt = desensitizedInput;
        if (provider.adapter().type().external()) {
            // B2 外调必先过出域数据最小化闸；越白名单/未审批阻断 → 不出域、诚实降级 B0。
            try {
                String egressJson = OBJECT_MAPPER.createObjectNode().put("prompt", desensitizedInput).toString();
                var prep = egressGuard.prepareEgress(
                    tenantId, capabilityCode, egressJson, taskId, provider.config().providerCode());
                prompt = readPromptField(prep.payload(), desensitizedInput);
            } catch (ApiException egressBlocked) {
                log.warn("外调出域闸阻断 capabilityCode={}：{}", capabilityCode, egressBlocked.getMessage());
                publishFailureAudit(egressBlocked.errorCode(),
                    "外调出域闸阻断 capabilityCode=" + capabilityCode + "：" + egressBlocked.getMessage());
                return b0Outcome(capabilityCode, fallbackMatrix.decide(
                    strategy, ModelFallbackTrigger.EGRESS_BLOCKED, egressBlocked.getMessage()).reason());
            }
        }

        try {
            ProviderCompletion completion = provider.adapter()
                .complete(provider.config(), new ProviderRequest(capabilityCode, prompt));
            // 真实增强产出：真实 model_version；置信度/引文仅由 provider 真实返回，绝不伪造。
            return new RouteOutcome(
                completion.content(), provider.modelMode(), completion.modelVersion(),
                plannedTriple.promptVersion(), plannedTriple.toolVersion(),
                completion.sourceCitations(), completion.confidence(), "LOW", false, null, "SUCCEEDED");
        } catch (ApiException providerFailed) {
            log.warn("provider 调用失败 capabilityCode={}：{}", capabilityCode, providerFailed.getMessage());
            return b0Outcome(capabilityCode, fallbackMatrix.decide(
                strategy, fallbackTrigger(providerFailed), providerFailed.getMessage()).reason());
        }
    }

    private ModelFallbackTrigger fallbackTrigger(ApiException providerFailed) {
        ErrorCode code = providerFailed.errorCode();
        if (code == ErrorCode.ENG_LLM_003) {
            return ModelFallbackTrigger.PROVIDER_TIMEOUT;
        }
        if (code == ErrorCode.TOO_MANY_REQUESTS) {
            return ModelFallbackTrigger.PROVIDER_RATE_LIMITED;
        }
        if (code == ErrorCode.ENG_LLM_002) {
            return ModelFallbackTrigger.STRUCTURED_OUTPUT_FAILED;
        }
        if (code == ErrorCode.DOWNSTREAM_UNAVAILABLE || code == ErrorCode.MODEL_DEGRADED) {
            return ModelFallbackTrigger.PROVIDER_DISCONNECTED;
        }
        return ModelFallbackTrigger.PROVIDER_ERROR;
    }

    private String readPromptField(String json, String fallback) {
        try {
            return OBJECT_MAPPER.readTree(json).path("prompt").asText(fallback);
        } catch (Exception parseFailed) {
            return fallback;
        }
    }

    private RouteOutcome b0Outcome(String capabilityCode, String fallbackReason) {
        ModelVersionTriple baseline = ModelVersionTriple.baseline();
        return new RouteOutcome(
            executeB0Fallback(capabilityCode), "B0", baseline.modelVersion(), baseline.promptVersion(),
            baseline.toolVersion(),
            "[]", null, "LOW", true, fallbackReason, "DEGRADED");
    }

    private ModelVersionTriple activeTripleOrBaseline(String tenantId, String capabilityCode) {
        return versionBundleRepository
            .findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(tenantId, capabilityCode, "ACTIVE")
            .map(bundle -> new ModelVersionTriple(bundle.promptVersion(), bundle.toolVersion(), bundle.modelVersion()))
            .orElseGet(ModelVersionTriple::baseline);
    }

    /**
     * B0 级确定性基线回退处理器（B0 Fallback Processor）。
     *
     * <p>无模型 provider 时只返回统一空候选信封，不生成任何医学事实或业务草案。
     */
    private String executeB0Fallback(String capabilityCode) {
        var output = OBJECT_MAPPER.createObjectNode();
        output.put("status", "NO_MODEL_PROVIDER");
        output.put("capability", capabilityCode);
        output.putArray("candidates");
        output.put("message", "当前未接入可用模型 provider，未生成候选内容");
        return output.toString();
    }
}
