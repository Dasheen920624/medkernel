package com.medkernel.engine.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
     * <p>统一管控模型能力调用：能力阻断、正则数据脱敏、期待输出结构校验，并通过物理子事务强隔离记录审计日志。
     * 当前经模型服务注册表解析 B1/B2；模型服务缺位、出域阻断、结构化失败或调用失败时按 LLM-02
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
    private static final int DEFAULT_PROVIDER_TIMEOUT_MS = 60_000;
    private static final int MIN_PROVIDER_TIMEOUT_MS = 1_000;
    private static final int MAX_PROVIDER_TIMEOUT_MS = 120_000;
    private static final int MAX_RATE_LIMIT_PER_MINUTE = 600;
    private static final long PROVIDER_RATE_LIMIT_WINDOW_MS = 60_000L;

    private final ModelCapabilityTaskRepository taskRepo;
    private final ModelCapabilityPolicyRepository policyRepo;
    private final ModelCapabilityDefinitionRepository definitionRepo;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final ModelProviderRegistry providerRegistry;
    private final ModelEgressGuard egressGuard;
    private final ModelVersionBundleRepository versionBundleRepository;
    private final ModelFallbackMatrix fallbackMatrix = new ModelFallbackMatrix();
    private final Map<String, Deque<Long>> providerCallWindows = new ConcurrentHashMap<>();

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
                        fallbackMatrix.defaultFallbackOrder("BASELINE"),
                        DEFAULT_PROVIDER_TIMEOUT_MS,
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
        List<String> fallbackOrder = normalizeFallbackOrder(routeStrategy, request.fallbackOrder());
        Integer timeoutMs = normalizeTimeoutMs(request.timeoutMs());
        Integer rateLimitPerMinute = normalizeRateLimitPerMinute(request.rateLimitPerMinute());

        ModelPolicyValidateResponse validation = validatePolicy(new ModelPolicyValidateRequest(
            normalizedCapability,
            routeStrategy,
            desensitizeStrategy,
            expectedSchema,
            fallbackOrder,
            timeoutMs,
            rateLimitPerMinute
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
            serializeFallbackOrder(fallbackOrder),
            timeoutMs,
            rateLimitPerMinute,
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
                "BASELINE", "DEFAULT", null, serializeFallbackOrder(fallbackMatrix.defaultFallbackOrder("BASELINE")),
                DEFAULT_PROVIDER_TIMEOUT_MS, null, Instant.now(), createdBy, Instant.now(), createdBy
            ));
        String strategy = policy.routeStrategy();

        // 2. 校验策略禁用阻断
        if ("DISABLED".equalsIgnoreCase(strategy)) {
            publishFailureAudit(ErrorCode.ENG_LLM_001, "提交任务失败，能力已被禁用 capabilityCode=" + capabilityCode);
            throw new ApiException(ErrorCode.ENG_LLM_001, "模型能力 " + capabilityCode + " 已经被组织禁用");
        }
        guardRequiredRoute(req.requiredRouteStrategy(), strategy);

        // 3. 敏感数据脱敏过滤与Hash计算
        String desensitizedInput = ModelDataDesensitizer.desensitize(req.inputData(), policy.desensitizeStrategy());
        String inputHash = computeSha256(req.inputData());
        String inputSummary = desensitizedInput.length() > 500 ? desensitizedInput.substring(0, 500) : desensitizedInput;

        // 4. 路由与推理：按策略解析真实模型服务（B1 本地 / B2 外部）。有健康模型服务且（B2）过出域闸
        //    → 真实增强产出；缺位/断连/形态禁外部/出域阻断/调用失败 → 诚实降级 B0。
        //    据铁律 #1/#2/#4，绝不伪造 B1/B2 模型名、置信度、来源引文或患者数据。
        ModelFallbackConfig fallbackConfig = fallbackConfig(policy);
        ActiveVersionPlan versionPlan = activeVersionPlan(tenantId, capabilityCode);
        String schemaConstraint = policy.expectedSchema();
        RouteOutcome outcome = route(
            tenantId, capabilityCode, strategy, fallbackConfig, desensitizedInput, taskId, versionPlan,
            schemaConstraint, req.providerCode());

        // 结构化输出规则校验：真实解析结构化文本并确认必填字段存在（GA-ENG-LLM-01）。
        // 校验对象为本次实际产出；B1/B2 结构化失败先诚实降级 B0，再校验 B0 信封。
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
                    log.warn("结构化输出规则校验失败 capabilityCode={}：{}",
                        capabilityCode, schemaError.getMessage());
                    publishFailureAudit(schemaError.errorCode(),
                        "结构化输出规则校验失败 capabilityCode=" + capabilityCode + "：" + schemaError.getMessage());
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
         * <p>LLM-04 的可复现重放只对 B0 确定性任务成立；B1/B2 模型服务结果受外部模型状态影响，
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
            throw new ApiException(ErrorCode.BAD_REQUEST, "仅支持 B0 确定性任务按任务号重放复现");
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
            "按任务号重放提示词、工具和模型版本 " + task.taskId());

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
        String fallbackError = validateFallbackSettings(
            routeStrategy, req.fallbackOrder(), req.timeoutMs(), req.rateLimitPerMinute());
        if (fallbackError != null) {
            return new ModelPolicyValidateResponse(false, fallbackError, !"DISABLED".equals(routeStrategy));
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
            fallbackConfig(policy).fallbackOrder(),
            normalizeTimeoutMs(policy.timeoutMs()),
            normalizeRateLimitPerMinute(policy.rateLimitPerMinute()),
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

    private static void guardRequiredRoute(String requiredRouteStrategy, String actualRouteStrategy) {
        String required = normalizeCode(requiredRouteStrategy);
        if (required.isEmpty()) {
            return;
        }
        String actual = normalizeCode(actualRouteStrategy);
        if (!ROUTE_STRATEGIES.contains(required)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不支持的必需模型路由策略: " + requiredRouteStrategy);
        }
        if (!required.equals(actual)) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                "模型任务要求路由 " + required + "，但当前能力策略为 " + actual + "，禁止越界调用");
        }
    }

    private String validateFallbackSettings(
            String routeStrategy,
            List<String> fallbackOrder,
            Integer timeoutMs,
            Integer rateLimitPerMinute) {
        String orderError = fallbackMatrix.validateFallbackOrder(routeStrategy, fallbackOrder);
        if (orderError != null) {
            return orderError;
        }
        if (timeoutMs != null && (timeoutMs < MIN_PROVIDER_TIMEOUT_MS || timeoutMs > MAX_PROVIDER_TIMEOUT_MS)) {
            return "timeout_ms：必须在 1000 到 120000 毫秒之间";
        }
        if (rateLimitPerMinute != null
                && (rateLimitPerMinute < 1 || rateLimitPerMinute > MAX_RATE_LIMIT_PER_MINUTE)) {
            return "rate_limit_per_minute：必须在 1 到 600 之间";
        }
        return null;
    }

    private List<String> normalizeFallbackOrder(String routeStrategy, List<String> fallbackOrder) {
        try {
            return fallbackMatrix.normalizeFallbackOrder(routeStrategy, fallbackOrder);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.ENG_LLM_002, invalid.getMessage());
        }
    }

    private Integer normalizeTimeoutMs(Integer timeoutMs) {
        return timeoutMs == null ? DEFAULT_PROVIDER_TIMEOUT_MS : timeoutMs;
    }

    private Integer normalizeRateLimitPerMinute(Integer rateLimitPerMinute) {
        return rateLimitPerMinute;
    }

    private String serializeFallbackOrder(List<String> fallbackOrder) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fallbackOrder);
        } catch (Exception cannotSerialize) {
            throw new IllegalStateException("fallback_order 序列化失败", cannotSerialize);
        }
    }

    private ModelFallbackConfig fallbackConfig(ModelCapabilityPolicy policy) {
        return new ModelFallbackConfig(
            parseFallbackOrder(policy.routeStrategy(), policy.fallbackOrderJson()),
            normalizeTimeoutMs(policy.timeoutMs()),
            normalizeRateLimitPerMinute(policy.rateLimitPerMinute()),
            policy.scopeType(),
            policy.scopeRef()
        );
    }

    private List<String> parseFallbackOrder(String routeStrategy, String fallbackOrderJson) {
        if (fallbackOrderJson == null || fallbackOrderJson.isBlank()) {
            return fallbackMatrix.defaultFallbackOrder(routeStrategy);
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(fallbackOrderJson);
            if (!node.isArray()) {
                throw new ApiException(ErrorCode.ENG_LLM_002, "模型兜底顺序必须是文本列表");
            }
            List<String> order = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    throw new ApiException(ErrorCode.ENG_LLM_002, "模型兜底顺序必须是文本列表");
                }
                order.add(item.asText());
            }
            return normalizeFallbackOrder(routeStrategy, order);
        } catch (ApiException invalid) {
            throw invalid;
        } catch (Exception invalidJson) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "模型兜底顺序配置文本不合法");
        }
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
     * 结构化输出规则校验：用 Jackson 将输出解析为对象/数组，
     * 再按 expectedSchema 声明的必填字段集做存在性校验；任一不满足抛 {@code ENG_LLM_002}。
     *
     * <p>相较旧实现的字符串 {@code contains}，此处对输出做真实结构解析，杜绝"看起来含某关键字即通过"的伪校验。
     */
    private void validateSchema(String content, String schema) {
        JsonNode output;
        try {
            output = OBJECT_MAPPER.readTree(content);
        } catch (Exception parseError) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "模型输出结构不合法，无法完成结构化输出校验");
        }
        if (output == null || !(output.isObject() || output.isArray())) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "模型输出不是可校验的对象或列表，无法满足结构化输出规则");
        }
        for (String required : extractRequiredFields(schema)) {
            if (!hasField(output, required)) {
                throw new ApiException(ErrorCode.ENG_LLM_002, "模型输出缺少必填字段：" + required);
            }
        }
    }

    /**
     * 从标准输出规则对象提取必填字段名。
     */
    private Set<String> extractRequiredFields(String schema) {
        Set<String> fields = new LinkedHashSet<>();
        JsonNode schemaNode;
        try {
            schemaNode = OBJECT_MAPPER.readTree(schema);
        } catch (Exception parseError) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待输出规则配置文本不合法");
        }
        if (schemaNode == null || !schemaNode.isObject()) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待输出规则必须是配置对象");
        }
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode == null || !requiredNode.isArray()) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待输出规则必须声明必填字段列表");
        }
        for (JsonNode node : requiredNode) {
            if (!node.isTextual() || node.asText().isBlank()) {
                throw new ApiException(ErrorCode.ENG_LLM_002, "期待输出规则的必填字段只能包含非空字段名");
            }
            fields.add(node.asText().trim());
        }
        if (fields.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_LLM_002, "期待输出规则的必填字段不能为空");
        }
        return fields;
    }

    /** 判断结构节点是否含某字段：对象看自身键，数组要求每个对象元素均含该字段。 */
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

    private record ModelFallbackConfig(
        List<String> fallbackOrder,
        int timeoutMs,
        Integer rateLimitPerMinute,
        String scopeType,
        String scopeRef
    ) {}

    private record ProviderAttempt(
        Optional<RouteOutcome> outcome,
        ModelFallbackTrigger trigger,
        String detail
    ) {
        static ProviderAttempt success(RouteOutcome outcome) {
            return new ProviderAttempt(Optional.of(outcome), null, null);
        }

        static ProviderAttempt failure(ModelFallbackTrigger trigger, String detail) {
            return new ProviderAttempt(Optional.empty(), trigger, detail);
        }
    }

    /**
     * 按模型兜底顺序逐级解析真实模型服务并产出；所有失败均先记录稳定归因，最终必须可落 B0。
     */
    private RouteOutcome route(String tenantId, String capabilityCode, String strategy,
                               ModelFallbackConfig fallbackConfig, String desensitizedInput,
                               String taskId, ActiveVersionPlan versionPlan, String expectedSchema,
                               String providerCode) {
        if (!versionPlan.executable() && !"BASELINE".equalsIgnoreCase(strategy)) {
            return b0Outcome(capabilityCode, versionPlan.reason());
        }
        ModelVersionTriple plannedTriple = versionPlan.triple();
        List<String> reasons = new ArrayList<>();
        List<String> order = fallbackConfig.fallbackOrder();
        for (int i = 0; i < order.size(); i++) {
            String attemptStrategy = order.get(i);
            if ("BASELINE".equals(attemptStrategy)) {
                String reason = reasons.isEmpty()
                    ? fallbackMatrix.decide(strategy, ModelFallbackTrigger.POLICY_BASELINE, "策略指定或前置层级均不可用").reason()
                    : String.join("；", reasons);
                return b0Outcome(capabilityCode, reason);
            }

            String attemptProviderCode = attemptStrategy.equalsIgnoreCase(strategy) ? providerCode : null;
            ProviderAttempt attempt = tryProvider(
                tenantId, capabilityCode, attemptStrategy, desensitizedInput,
                taskId, plannedTriple, expectedSchema, fallbackConfig, attemptProviderCode);
            if (attempt.outcome().isPresent()) {
                RouteOutcome successful = attempt.outcome().get();
                if (reasons.isEmpty()) {
                    return successful;
                }
                return new RouteOutcome(
                    successful.outputContent(),
                    successful.modelMode(),
                    successful.modelVersion(),
                    successful.promptVersion(),
                    successful.toolVersion(),
                    successful.sourceCitations(),
                    successful.confidence(),
                    successful.riskLevel(),
                    true,
                    String.join("；", reasons),
                    successful.taskStatus()
                );
            }

            String nextStrategy = i + 1 < order.size() ? order.get(i + 1) : "BASELINE";
            ModelFallbackDecision decision = fallbackMatrix.decide(
                attemptStrategy, nextStrategy, attempt.trigger(), attempt.detail());
            reasons.add(decision.reason());
        }
        return b0Outcome(capabilityCode, String.join("；", reasons));
    }

    private ProviderAttempt tryProvider(String tenantId, String capabilityCode, String strategy,
                                        String desensitizedInput, String taskId,
                                        ModelVersionTriple plannedTriple, String expectedSchema,
                                        ModelFallbackConfig fallbackConfig, String providerCode) {
        var resolved = providerCode == null || providerCode.isBlank()
            ? providerRegistry.resolve(tenantId, strategy)
            : providerRegistry.resolve(tenantId, strategy, providerCode);
        if (resolved.isEmpty()) {
            return ProviderAttempt.failure(ModelFallbackTrigger.PROVIDER_UNAVAILABLE,
                "未找到可用模型服务，或当前部署形态不允许调用");
        }
        ModelProviderRegistry.ResolvedProvider provider = resolved.get();
        String configuredModelVersion = normalizeOptional(provider.config().modelVersion());
        if (configuredModelVersion == null) {
            return ProviderAttempt.failure(ModelFallbackTrigger.PROVIDER_ERROR,
                "模型服务未配置模型版本");
        }
        if (!configuredModelVersion.equals(plannedTriple.modelVersion())) {
            return ProviderAttempt.failure(ModelFallbackTrigger.PROVIDER_ERROR,
                "已生效模型版本与模型服务配置不一致：生效版本=" + plannedTriple.modelVersion()
                    + "，服务配置=" + configuredModelVersion);
        }
        if (!reserveProviderBudget(
                tenantId, capabilityCode, strategy, fallbackConfig, provider.config().providerCode())) {
            return ProviderAttempt.failure(
                ModelFallbackTrigger.PROVIDER_RATE_LIMITED,
                "每分钟模型调用上限已达到：" + fallbackConfig.rateLimitPerMinute());
        }

        String prompt = desensitizedInput;
        if (provider.adapter().type().external()) {
            try {
                String egressJson = OBJECT_MAPPER.createObjectNode().put("prompt", desensitizedInput).toString();
                var prep = egressGuard.prepareEgress(
                    tenantId, capabilityCode, egressJson, taskId, provider.config().providerCode());
                if (prep.egressFields() == null || !prep.egressFields().contains("prompt")) {
                    throw new ApiException(ErrorCode.ENG_LLM_006,
                        "外调最小化结果未包含允许的提示内容");
                }
                prompt = readPromptField(prep.payload());
            } catch (ApiException egressBlocked) {
                log.warn("模型外调安全闸阻断 capabilityCode={}：{}", capabilityCode, egressBlocked.getMessage());
                publishFailureAudit(egressBlocked.errorCode(),
                    "模型外调安全闸阻断，能力=" + capabilityCode + "：" + egressBlocked.getMessage());
                return ProviderAttempt.failure(ModelFallbackTrigger.EGRESS_BLOCKED, egressBlocked.getMessage());
            }
        }

        try {
            ProviderCompletion completion = provider.adapter()
                .complete(provider.config(), new ProviderRequest(capabilityCode, prompt, fallbackConfig.timeoutMs()));
            if (completion == null
                || completion.modelVersion() == null
                || !configuredModelVersion.equals(completion.modelVersion().trim())) {
                return ProviderAttempt.failure(ModelFallbackTrigger.PROVIDER_ERROR,
                    "模型服务返回版本与已配置版本不一致：期望=" + configuredModelVersion
                        + "，实际=" + (completion == null ? "未返回" : completion.modelVersion()));
            }
            if (completion.content() == null || completion.content().isBlank()) {
                return ProviderAttempt.failure(ModelFallbackTrigger.PROVIDER_ERROR,
                    "模型服务返回内容为空");
            }
            if (expectedSchema != null && !expectedSchema.isBlank()) {
                validateSchema(completion.content(), expectedSchema);
            }
            return ProviderAttempt.success(new RouteOutcome(
                completion.content(), provider.modelMode(), configuredModelVersion,
                plannedTriple.promptVersion(), plannedTriple.toolVersion(),
                completion.sourceCitations(), completion.confidence(), "LOW", false, null, "SUCCEEDED"));
        } catch (ApiException providerFailed) {
            log.warn("模型服务调用失败 capabilityCode={}：{}", capabilityCode, providerFailed.getMessage());
            return ProviderAttempt.failure(fallbackTrigger(providerFailed), providerFailed.getMessage());
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

    private boolean reserveProviderBudget(
            String tenantId,
            String capabilityCode,
            String strategy,
            ModelFallbackConfig fallbackConfig,
            String providerCode) {
        Integer limit = fallbackConfig.rateLimitPerMinute();
        if (limit == null) {
            return true;
        }
        String key = String.join("|",
            tenantId,
            capabilityCode,
            nullToEmpty(fallbackConfig.scopeType()),
            nullToEmpty(fallbackConfig.scopeRef()),
            strategy,
            nullToEmpty(providerCode));
        long now = System.currentTimeMillis();
        long cutoff = now - PROVIDER_RATE_LIMIT_WINDOW_MS;
        Deque<Long> window = providerCallWindows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() <= cutoff) {
                window.removeFirst();
            }
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String readPromptField(String json) {
        try {
            JsonNode prompt = OBJECT_MAPPER.readTree(json).get("prompt");
            if (prompt == null || !prompt.isTextual() || prompt.asText().isBlank()) {
                throw new ApiException(ErrorCode.ENG_LLM_006,
                    "外调最小化后的提示内容为空或非文本，禁止回退原始内容");
            }
            return prompt.asText();
        } catch (ApiException blocked) {
            throw blocked;
        } catch (Exception parseFailed) {
            throw new ApiException(ErrorCode.ENG_LLM_006,
                "外调最小化结果结构不合法，禁止回退原始内容");
        }
    }

    private RouteOutcome b0Outcome(String capabilityCode, String fallbackReason) {
        ModelVersionTriple baseline = ModelVersionTriple.baseline();
        return new RouteOutcome(
            executeB0Fallback(capabilityCode), "B0", baseline.modelVersion(), baseline.promptVersion(),
            baseline.toolVersion(),
            "[]", null, "LOW", true, fallbackReason, "DEGRADED");
    }

    private ActiveVersionPlan activeVersionPlan(String tenantId, String capabilityCode) {
        ModelVersionBundle bundle = versionBundleRepository
            .findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(tenantId, capabilityCode, "ACTIVE")
            .orElse(null);
        ModelVersionBundleValidator.Validation validation =
            ModelVersionBundleValidator.validateActive(bundle, tenantId, capabilityCode);
        if (!validation.valid()) {
            String prefix = bundle == null ? "" : "已生效模型版本组合不可执行：";
            return new ActiveVersionPlan(ModelVersionTriple.baseline(), false, prefix + validation.reason());
        }
        ModelVersionBundle active = validation.bundle();
        return new ActiveVersionPlan(
            new ModelVersionTriple(active.promptVersion(), active.toolVersion(), active.modelVersion()),
            true,
            null);
    }

    private record ActiveVersionPlan(ModelVersionTriple triple, boolean executable, String reason) {}

    /**
     * B0 级确定性基线回退处理器（B0 Fallback Processor）。
     *
     * <p>无模型服务时只返回统一空候选信封，不生成任何医学事实或业务草案。
     */
    private String executeB0Fallback(String capabilityCode) {
        var output = OBJECT_MAPPER.createObjectNode();
        output.put("status", "NO_MODEL_PROVIDER");
        output.put("capability", capabilityCode);
        output.putArray("candidates");
        output.put("message", "当前未接入可用模型服务，未生成候选内容");
        return output.toString();
    }
}
