package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.production.CandidateSubmissionRequest;
import com.medkernel.engine.knowledge.production.CandidateSubmissionResponse;
import com.medkernel.engine.knowledge.production.KnowledgeProductionOrchestrationService;
import com.medkernel.engine.knowledge.production.ProductionCandidateView;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 引擎数据服务层 · 受控工具服务（DATASVC-01 PR2，CLI/MCP 共用受控工具执行入口）。
 *
 * <p>把已建的受控读模型服务以「受控工具」形式统一暴露：工具只能经本服务派发到既有受控服务执行，
 * <b>不直连数据库、不绕身份/权限/脱敏/审计/降级</b>（FR-5）。每次执行包裹 FR-4 治理信封
 * （traceId/数据级别/脱敏策略/来源版本/权限结果/降级状态/输出 hash），并留工具调用审计（FR-6）。
 * 上游降级诚实透传不伪装（FR-7/铁律 #1）；未知工具返回结构化 404 不泄漏内部（FR-4）。
 *
 * <p>本切片注册两个 D2 工具：{@code queryRuleUsage}（规则使用聚合）、{@code summarizeEngineSignals}
 * （汇总规则/知识/临床信号分组数）。其余工具（searchKnowledge/explainRule/validatePrivacyPolicy/
 * getClinicalContextExplanation 等）须随其上游读模型落地后续切片登记。
 */
@Service
public class ControlledToolService {

    static final String TOOL_QUERY_RULE_USAGE = "queryRuleUsage";
    static final String TOOL_SUMMARIZE_ENGINE_SIGNALS = "summarizeEngineSignals";
    static final String TOOL_EXPLAIN_RULE = "explainRule";
    static final String TOOL_CHECK_KNOWLEDGE_EXISTENCE = "checkKnowledgeExistence";
    static final String TOOL_SEARCH_KNOWLEDGE = "searchKnowledge";
    static final String TOOL_VALIDATE_PRIVACY_POLICY = "validatePrivacyPolicy";
    static final String TOOL_GET_CLINICAL_CONTEXT_EXPLANATION = "getClinicalContextExplanation";
    static final String TOOL_SUBMIT_PRODUCTION_CANDIDATE = "submitProductionCandidate";
    private static final String REQUIRED_PERMISSION = "engine-data.read";
    private static final String WRITE_PERMISSION = "knowledge.write";
    private static final String FINGERPRINT_BLANK_MESSAGE = "工具输出指纹不可为空";
    private static final String SHA256_HEX = "^[0-9a-f]{64}$";
    private static final Set<String> FORBIDDEN_PATIENT_FIELDS = Set.of(
        "patientid", "patientname", "idcard", "phone", "encounterid", "medicalrecord");

    private final RuleUsageStatsService ruleUsageStatsService;
    private final KnowledgeUsageStatsService knowledgeUsageStatsService;
    private final ClinicalSignalsService clinicalSignalsService;
    private final RuleExplanationService ruleExplanationService;
    private final KnowledgeExistenceService knowledgeExistenceService;
    private final KnowledgeSearchService knowledgeSearchService;
    private final PrivacyPolicyService privacyPolicyService;
    private final ClinicalContextService clinicalContextService;
    private final KnowledgeProductionOrchestrationService productionService;
    private final PermissionEvaluator permissionEvaluator;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ControlledToolService(RuleUsageStatsService ruleUsageStatsService,
            KnowledgeUsageStatsService knowledgeUsageStatsService,
            ClinicalSignalsService clinicalSignalsService,
            RuleExplanationService ruleExplanationService,
            KnowledgeExistenceService knowledgeExistenceService,
            KnowledgeSearchService knowledgeSearchService,
            PrivacyPolicyService privacyPolicyService,
            ClinicalContextService clinicalContextService,
            KnowledgeProductionOrchestrationService productionService,
            PermissionEvaluator permissionEvaluator,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.ruleUsageStatsService = ruleUsageStatsService;
        this.knowledgeUsageStatsService = knowledgeUsageStatsService;
        this.clinicalSignalsService = clinicalSignalsService;
        this.ruleExplanationService = ruleExplanationService;
        this.knowledgeExistenceService = knowledgeExistenceService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.privacyPolicyService = privacyPolicyService;
        this.clinicalContextService = clinicalContextService;
        this.productionService = productionService;
        this.permissionEvaluator = permissionEvaluator;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    /**
     * 列出已登记的受控工具目录（用途/数据级别/所需权限），供 CLI diagnostics / MCP 工具发现。
     */
    public List<ControlledToolDescriptor> listTools() {
        return List.of(
            new ControlledToolDescriptor(TOOL_QUERY_RULE_USAGE,
                "查询规则使用聚合统计", EngineDataLevel.D2, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_SUMMARIZE_ENGINE_SIGNALS,
                "汇总规则、知识、临床信号的聚合信号", EngineDataLevel.D2, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_EXPLAIN_RULE,
                "解释单条规则的已发布资产元数据", EngineDataLevel.D1, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_CHECK_KNOWLEDGE_EXISTENCE,
                "检查知识身份是否存在并附最小元数据", EngineDataLevel.D1, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_SEARCH_KNOWLEDGE,
                "按关键词检索知识身份的已发布资产元数据", EngineDataLevel.D1, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_VALIDATE_PRIVACY_POLICY,
                "判定数据分级是否准入数据服务/CLI/MCP", EngineDataLevel.D0, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_GET_CLINICAL_CONTEXT_EXPLANATION,
                "解释临床 launch 令牌授权的最小会话上下文（患者引用脱敏）", EngineDataLevel.D4, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_SUBMIT_PRODUCTION_CANDIDATE,
                "Agent 受控回写候选到知识生产流水线", EngineDataLevel.D1, WRITE_PERMISSION));
    }

    /**
     * 执行受控工具，返回 FR-4 治理信封。未知工具结构化 404（不泄漏内部）。
     */
    @Transactional
    public ToolExecutionEnvelope execute(String toolName, ToolExecutionRequest request) {
        requireToolPermission(toolName);
        return switch (toolName) {
            case TOOL_QUERY_RULE_USAGE -> executeQueryRuleUsage(request);
            case TOOL_SUMMARIZE_ENGINE_SIGNALS -> executeSummarizeEngineSignals(request);
            case TOOL_EXPLAIN_RULE -> executeExplainRule(request);
            case TOOL_CHECK_KNOWLEDGE_EXISTENCE -> executeCheckKnowledgeExistence(request);
            case TOOL_SEARCH_KNOWLEDGE -> executeSearchKnowledge(request);
            case TOOL_VALIDATE_PRIVACY_POLICY -> executeValidatePrivacyPolicy(request);
            case TOOL_GET_CLINICAL_CONTEXT_EXPLANATION -> executeGetClinicalContextExplanation(request);
            case TOOL_SUBMIT_PRODUCTION_CANDIDATE -> executeSubmitProductionCandidate(request);
            default -> throw ApiException.notFound("受控工具 " + toolName);
        };
    }

    private ToolExecutionEnvelope executeSubmitProductionCandidate(ToolExecutionRequest request) {
        AgentProductionCandidatePayload payload = requireAgentPayload(request);
        validateAgentPayload(payload);
        CandidateSubmissionRequest submission = payload.submission();
        CandidateSubmissionResponse response = findExistingByContentHash(payload.jobCode(), submission.candidate())
            .map(existing -> new CandidateSubmissionResponse(existing.candidateRef(), existing.routing()))
            .orElseGet(() -> productionService.submitCandidate(
                payload.jobCode(), submission.candidate(), submission.target()));
        String fingerprint = TOOL_SUBMIT_PRODUCTION_CANDIDATE + "|job=" + payload.jobCode()
            + "|idem=" + payload.idempotencyKey() + "|asset=" + submission.candidate().assetIdentity()
            + "|hash=" + submission.candidate().contentHash() + "|ref=" + response.candidateRef();
        return envelope(TOOL_SUBMIT_PRODUCTION_CANDIDATE, EngineDataLevel.D1, Instant.now(),
            false, null, fingerprint, response, request.purpose());
    }

    private AgentProductionCandidatePayload requireAgentPayload(ToolExecutionRequest request) {
        Object raw = request.payload();
        if (raw == null) {
            throw schemaInvalid("submitProductionCandidate 必须携带 payload");
        }
        if (raw instanceof AgentProductionCandidatePayload payload) {
            return payload;
        }
        try {
            return objectMapper.convertValue(raw, AgentProductionCandidatePayload.class);
        } catch (IllegalArgumentException invalidPayload) {
            throw schemaInvalid("payload 不能转换为 Agent 候选回写协议");
        }
    }

    private void validateAgentPayload(AgentProductionCandidatePayload payload) {
        if (payload == null) {
            throw schemaInvalid("payload 不能为空");
        }
        if (blank(payload.jobCode())) {
            throw schemaInvalid("jobCode 不能为空");
        }
        if (blank(payload.idempotencyKey())) {
            throw schemaInvalid("idempotencyKey 不能为空");
        }
        guardAgentDataLevel(payload.dataLevel());
        CandidateSubmissionRequest submission = payload.submission();
        if (submission == null || submission.candidate() == null || submission.target() == null) {
            throw schemaInvalid("submission.candidate 与 submission.target 不能为空");
        }
        try {
            submission.target().validate();
        } catch (IllegalArgumentException invalidTarget) {
            throw schemaInvalid(invalidTarget.getMessage());
        }
        validateCandidateEnvelope(submission.candidate());
    }

    private void guardAgentDataLevel(String dataLevel) {
        String normalized = blank(dataLevel) ? "D1" : dataLevel.trim().toUpperCase(Locale.ROOT);
        EngineDataLevel level;
        try {
            level = EngineDataLevel.valueOf(normalized);
        } catch (IllegalArgumentException invalidLevel) {
            throw schemaInvalid("dataLevel 取值无效：" + dataLevel);
        }
        if (level == EngineDataLevel.D3 || level == EngineDataLevel.D4 || level == EngineDataLevel.D5) {
            throw new ApiException(ErrorCode.AGENT_PATIENT_DATA_FORBIDDEN,
                "Agent 回写只能携带公开医学资料或受控来源元数据，禁止 " + level + " 患者相关数据");
        }
    }

    private void validateCandidateEnvelope(KnowledgeAssetEnvelope candidate) {
        if (candidate.sources() == null || candidate.sources().isEmpty()) {
            throw schemaInvalid("候选必须携带至少一个来源锚点");
        }
        for (AssetSourceRef source : candidate.sources()) {
            if (source == null || !anchoredSource(source.sourceRef())) {
                throw schemaInvalid("来源引用必须为 sourceCode:versionNo:anchorPath 形式");
            }
        }
        if (blank(candidate.payload())) {
            throw schemaInvalid("候选 payload 不能为空");
        }
        if (blank(candidate.contentHash()) || !candidate.contentHash().matches(SHA256_HEX)) {
            throw schemaInvalid("contentHash 必须为 64 位小写 SHA-256");
        }
        String realHash = Sha256ContentHash.sha256(candidate.payload(), "资产内容不能为空");
        if (!realHash.equals(candidate.contentHash())) {
            throw schemaInvalid("contentHash 与 payload 不一致");
        }
        JsonNode payload = parseCandidatePayload(candidate.payload());
        if (!payload.path("aiGenerated").asBoolean(false)) {
            throw schemaInvalid("Agent 回写候选必须显式 aiGenerated=true");
        }
        if (containsForbiddenPatientField(payload)) {
            throw new ApiException(ErrorCode.AGENT_PATIENT_DATA_FORBIDDEN,
                "Agent 回写 payload 含患者字段，禁止进入知识生产候选链");
        }
    }

    private JsonNode parseCandidatePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException invalidJson) {
            throw schemaInvalid("候选 payload 必须为 JSON");
        }
    }

    private boolean containsForbiddenPatientField(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (FORBIDDEN_PATIENT_FIELDS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    return true;
                }
                if (containsForbiddenPatientField(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsForbiddenPatientField(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ProductionCandidateView> existingCandidates(String jobCode) {
        // listCandidates 已改为分页（#632）；内容 hash 去重需全量候选，故按最大页逐页拉取至无下一页。
        List<ProductionCandidateView> candidates = new ArrayList<>();
        int page = PageRequest.DEFAULT_PAGE;
        PageResponse<ProductionCandidateView> response;
        do {
            response = productionService.listCandidates(jobCode, page, PageRequest.MAX_SIZE);
            candidates.addAll(response.items());
            page++;
        } while (response.hasNext());
        return candidates;
    }

    private java.util.Optional<ProductionCandidateView> findExistingByContentHash(
            String jobCode, KnowledgeAssetEnvelope candidate) {
        return existingCandidates(jobCode).stream()
            .filter(existing -> candidate.contentHash().equals(existing.contentHash()))
            .findFirst();
    }

    private boolean anchoredSource(String sourceRef) {
        if (blank(sourceRef)) {
            return false;
        }
        String[] parts = sourceRef.split(":", 3);
        return parts.length == 3 && !blank(parts[0]) && !blank(parts[1]) && !blank(parts[2]);
    }

    private ApiException schemaInvalid(String reason) {
        return new ApiException(ErrorCode.AGENT_CANDIDATE_SCHEMA_INVALID,
            "Agent 回写候选不合格：" + reason);
    }

    private void requireToolPermission(String toolName) {
        String required = TOOL_SUBMIT_PRODUCTION_CANDIDATE.equals(toolName) ? WRITE_PERMISSION : REQUIRED_PERMISSION;
        if (!permissionEvaluator.has(required)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "受控工具 " + toolName + " 需要权限 " + required);
        }
    }

    private ToolExecutionEnvelope executeGetClinicalContextExplanation(ToolExecutionRequest request) {
        String launchToken = requireTarget(request, TOOL_GET_CLINICAL_CONTEXT_EXPLANATION);
        ClinicalContextExplanation result = clinicalContextService.explainContext(launchToken, request.purpose());
        // 指纹仅含授权结果与非患者上下文（患者引用已在服务侧脱敏，不入指纹）。
        String fingerprint = TOOL_GET_CLINICAL_CONTEXT_EXPLANATION + "|authorized=" + result.authorized()
            + "|trigger=" + result.triggerPoint() + "|validUntil=" + result.sessionValidUntil();
        return envelope(TOOL_GET_CLINICAL_CONTEXT_EXPLANATION, result.dataLevel(), result.generatedAt(),
            result.degraded(), result.degradeReason(), fingerprint, result, request.purpose());
    }

    private ToolExecutionEnvelope executeSearchKnowledge(ToolExecutionRequest request) {
        String keyword = requireTarget(request, TOOL_SEARCH_KNOWLEDGE);
        KnowledgeSearchResult result = knowledgeSearchService.search(keyword, request.page(), request.size());
        String fingerprint = TOOL_SEARCH_KNOWLEDGE + "|keyword=" + keyword + "|total=" + result.total()
            + "|hits=" + result.hits().stream()
                .map(h -> h.identityCode() + ":" + h.status())
                .collect(Collectors.joining(","));
        return envelope(TOOL_SEARCH_KNOWLEDGE, result.dataLevel(), result.generatedAt(),
            result.degraded(), result.degradeReason(), fingerprint, result, request.purpose());
    }

    private ToolExecutionEnvelope executeValidatePrivacyPolicy(ToolExecutionRequest request) {
        String level = requireTarget(request, TOOL_VALIDATE_PRIVACY_POLICY);
        PrivacyPolicyDecision result = privacyPolicyService.validate(level);
        String fingerprint = TOOL_VALIDATE_PRIVACY_POLICY + "|level=" + result.requestedLevel()
            + "|allowed=" + result.allowed() + "|encryption=" + result.requiresFieldEncryption();
        // 策略判定无上游降级路径：诚实标 degraded=false。
        return envelope(TOOL_VALIDATE_PRIVACY_POLICY, result.dataLevel(), result.generatedAt(),
            false, null, fingerprint, result, request.purpose());
    }

    private ToolExecutionEnvelope executeExplainRule(ToolExecutionRequest request) {
        String ruleId = requireTarget(request, TOOL_EXPLAIN_RULE);
        RuleExplanation result = ruleExplanationService.explainRule(ruleId);
        String fingerprint = TOOL_EXPLAIN_RULE + "|ruleId=" + result.ruleId() + "|code=" + result.ruleCode()
            + "|status=" + result.status() + "|version=" + result.activeVersionId();
        return envelope(TOOL_EXPLAIN_RULE, result.dataLevel(), result.generatedAt(),
            result.degraded(), result.degradeReason(), fingerprint, result, request.purpose());
    }

    private ToolExecutionEnvelope executeCheckKnowledgeExistence(ToolExecutionRequest request) {
        String identityCode = requireTarget(request, TOOL_CHECK_KNOWLEDGE_EXISTENCE);
        KnowledgeExistence result = knowledgeExistenceService.checkExistence(identityCode);
        String fingerprint = TOOL_CHECK_KNOWLEDGE_EXISTENCE + "|code=" + result.identityCode()
            + "|exists=" + result.exists() + "|domain=" + result.domain() + "|status=" + result.status();
        return envelope(TOOL_CHECK_KNOWLEDGE_EXISTENCE, result.dataLevel(), result.generatedAt(),
            result.degraded(), result.degradeReason(), fingerprint, result, request.purpose());
    }

    private String requireTarget(ToolExecutionRequest request, String toolName) {
        String target = request.target();
        if (target == null || target.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "受控工具 " + toolName + " 必须指定目标标识 target");
        }
        return target;
    }

    private ToolExecutionEnvelope executeQueryRuleUsage(ToolExecutionRequest request) {
        RuleUsageStatsResponse result = ruleUsageStatsService.queryRuleUsage(
            request.from(), request.to(), request.page(), request.size());
        String fingerprint = TOOL_QUERY_RULE_USAGE + "|total=" + result.total() + "|rows="
            + result.rows().stream()
                .map(r -> r.ruleId() + ":" + r.totalExecutions() + ":" + r.hitCount() + ":" + r.failedCount())
                .collect(Collectors.joining(","));
        return envelope(TOOL_QUERY_RULE_USAGE, result.dataLevel(), result.generatedAt(),
            result.degraded(), result.degradeReason(), fingerprint, result, request.purpose());
    }

    private ToolExecutionEnvelope executeSummarizeEngineSignals(ToolExecutionRequest request) {
        RuleUsageStatsResponse rule =
            ruleUsageStatsService.queryRuleUsage(request.from(), request.to(), 0, 1);
        KnowledgeUsageStatsResponse knowledge =
            knowledgeUsageStatsService.queryKnowledgeUsage(request.from(), request.to(), 0, 1);
        ClinicalSignalsResponse clinical =
            clinicalSignalsService.queryClinicalSignals(request.from(), request.to(), 0, 1);

        boolean degraded = rule.degraded() || knowledge.degraded() || clinical.degraded();
        String degradeReason = degraded ? "部分上游引擎信号暂不可用，已诚实标降级（未伪装完整）" : null;
        Instant generatedAt = Instant.now();
        EngineSignalsSummary summary = new EngineSignalsSummary(
            rule.total(), knowledge.total(), clinical.total(), generatedAt);
        String fingerprint = TOOL_SUMMARIZE_ENGINE_SIGNALS + "|rule=" + rule.total()
            + "|knowledge=" + knowledge.total() + "|clinical=" + clinical.total();
        return envelope(TOOL_SUMMARIZE_ENGINE_SIGNALS, EngineDataLevel.D2, generatedAt,
            degraded, degradeReason, fingerprint, summary, request.purpose());
    }

    private ToolExecutionEnvelope envelope(String toolName, EngineDataLevel level, Instant generatedAt,
            boolean degraded, String degradeReason, String fingerprint, Object payload, String purpose) {
        String traceId = RequestContext.currentTraceId();
        String outputHash = Sha256ContentHash.sha256(fingerprint, FINGERPRINT_BLANK_MESSAGE);
        // FR-6：工具调用审计含工具名、用途、数据级别、输出 hash；不存完整敏感入参。
        auditRecorder.record(AuditAction.EXECUTE, "engine_data_tool", toolName,
            "执行受控工具 " + toolName + " 级别=" + level + " 用途=" + purpose
            + " 输出hash=" + outputHash + (degraded ? " 降级=true" : ""));
        // permissionGranted 恒 true：执行到此处已过控制器 @PreAuthorize('engine-data.read') 鉴权。
        return new ToolExecutionEnvelope(toolName, level, policyFor(level),
            generatedAt != null ? generatedAt.toString() : null, true, degraded, degradeReason,
            traceId, outputHash, payload);
    }

    /**
     * 按数据级别给出后端脱敏策略标识（FR-2）：D0/D1 为已发布元数据无需脱敏，D2 为去标识聚合，
     * D4 为最小授权上下文（患者引用不可逆脱敏，不输出原始患者字段）；D3/D5 当前工具不暴露，
     * 默认返回最严策略标识（不以宽松策略伪装高敏处理）。
     */
    private static String policyFor(EngineDataLevel level) {
        return switch (level) {
            case D0 -> "D0_RUNTIME_METADATA";
            case D1 -> "D1_PUBLISHED_ASSET_METADATA";
            case D2 -> "D2_DEIDENTIFIED_AGGREGATE";
            case D4 -> "D4_MASKED_MINIMAL_CONTEXT";
            default -> "RESTRICTED_FIELD_ENCRYPTION_REQUIRED";
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
