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

/**
 * 模型能力网关核心领域服务实现类 (GA-ENG-API-12)。
 *
 * <p>统一管控模型能力调用：能力阻断、正则数据脱敏、期待结构 Schema 校验，并通过物理子事务强隔离记录审计日志。
 * 当前未接入真实模型 provider（B1/B2 由 GA-ENG-LLM-02 接入），provider 缺位时所有非 DISABLED 能力一律
 * 如实返回 B0（无模型确定性基线），禁止伪造 B2 模型名、置信度或来源引文。
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

    public ModelGatewayService(ModelCapabilityTaskRepository taskRepo,
                               ModelCapabilityPolicyRepository policyRepo,
                               ModelCapabilityDefinitionRepository definitionRepo,
                               AuditRecorder auditRecorder,
                               IsolatedAuditPublisher isolatedAudit) {
        this.taskRepo = taskRepo;
        this.policyRepo = policyRepo;
        this.definitionRepo = definitionRepo;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
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
                Optional<ModelCapabilityPolicy> policyOpt = policyRepo.findByTenantIdAndCapabilityCode(tenantId, code);
                if (policyOpt.isPresent()) {
                    ModelCapabilityPolicy policy = policyOpt.get();
                    return statusOf(definition, policy, true);
                }
                return new ModelCapabilityStatusResponse(
                        code,
                        definition.displayName(),
                        definition.description(),
                        definition.category(),
                        "BASELINE",
                        "DEFAULT",
                        null,
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
        Optional<ModelCapabilityPolicy> existing =
            policyRepo.findByTenantIdAndCapabilityCode(tenantId, normalizedCapability);
        ModelCapabilityPolicy saved = policyRepo.save(new ModelCapabilityPolicy(
            existing.map(ModelCapabilityPolicy::id).orElse(null),
            tenantId,
            normalizedCapability,
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
            "保存模型能力策略 " + normalizedCapability
        );
        return statusOf(definition, saved, true);
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
        ModelCapabilityPolicy policy = policyRepo.findByTenantIdAndCapabilityCode(tenantId, capabilityCode)
            .orElseGet(() -> new ModelCapabilityPolicy(
                null, tenantId, capabilityCode, "BASELINE", "DEFAULT", null,
                Instant.now(), createdBy, Instant.now(), createdBy
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

        // 4. 路由与推理：当前未接入任何真实模型 provider（B1 本地 / B2 外部 / Dify 由 GA-ENG-LLM-02 落地）。
        //    据宪法 #9/#13，provider 缺位时一律诚实降级到 B0 确定性基线，
        //    禁止伪造 B1/B2 模型名、置信度、来源引文或患者数据。
        String strategy = policy.routeStrategy();
        String outputContent = executeB0Fallback(capabilityCode);
        String modelMode = "B0";
        String modelVersion = "B0-Deterministic-Baseline";
        String promptVersion = "baseline";
        String sourceCitations = "[]";
        Double confidence = null;
        String riskLevel = "LOW";
        boolean fallbackUsed = true;
        String fallbackReason = baselineReason(strategy);
        String taskStatus = "DEGRADED";

        // 结构化输出 Schema 校验：真实解析 JSON + required 字段存在性校验（GA-ENG-LLM-01）。
        // 校验对象为本次实际产出（当前恒为 B0 基线），未来接入 provider 后对模型输出复用同一校验。
        String schemaConstraint = policy.expectedSchema();
        if (schemaConstraint != null && !schemaConstraint.isBlank()) {
            try {
                validateSchema(outputContent, schemaConstraint);
            } catch (ApiException schemaError) {
                log.warn("结构化输出 Schema 校验失败 capabilityCode={}：{}",
                    capabilityCode, schemaError.getMessage());
                publishFailureAudit(schemaError.errorCode(),
                    "结构化输出 Schema 校验失败 capabilityCode=" + capabilityCode + "：" + schemaError.getMessage());
                throw schemaError;
            }
        }

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
            boolean configured) {
        boolean fallbackAvailable = !"DISABLED".equalsIgnoreCase(policy.routeStrategy());
        return new ModelCapabilityStatusResponse(
            policy.capabilityCode(),
            definition.displayName(),
            definition.description(),
            definition.category(),
            policy.routeStrategy(),
            policy.desensitizeStrategy(),
            policy.expectedSchema(),
            configured,
            fallbackAvailable,
            fallbackAvailable ? "正常可用" : "已被路由策略禁用"
        );
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

    /** 根据路由策略给出诚实的 B0 基线归因说明（绝不伪造模型推理）。 */
    private String baselineReason(String strategy) {
        if ("BASELINE".equalsIgnoreCase(strategy)) {
            return "组织安全策略显式指定 B0 确定性基线路径";
        }
        if ("LOCAL_MODEL".equalsIgnoreCase(strategy)) {
            return "未接入本地微调模型(B1)，按 B0 确定性基线执行；模型接入由 GA-ENG-LLM-02 落地";
        }
        if ("EXTERNAL_MODEL".equalsIgnoreCase(strategy)) {
            return "未接入外部大模型/Dify(B2)，按 B0 确定性基线执行；模型接入由 GA-ENG-LLM-02 落地";
        }
        return "未识别的路由策略 " + strategy + "，回退 B0 确定性基线";
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
