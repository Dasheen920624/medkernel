package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
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
    private static final String REQUIRED_PERMISSION = "engine-data.read";
    private static final String DESENSITIZATION_POLICY = "D2_DEIDENTIFIED_AGGREGATE";
    private static final String FINGERPRINT_BLANK_MESSAGE = "工具输出指纹不可为空";

    private final RuleUsageStatsService ruleUsageStatsService;
    private final KnowledgeUsageStatsService knowledgeUsageStatsService;
    private final ClinicalSignalsService clinicalSignalsService;
    private final AuditRecorder auditRecorder;

    public ControlledToolService(RuleUsageStatsService ruleUsageStatsService,
            KnowledgeUsageStatsService knowledgeUsageStatsService,
            ClinicalSignalsService clinicalSignalsService,
            AuditRecorder auditRecorder) {
        this.ruleUsageStatsService = ruleUsageStatsService;
        this.knowledgeUsageStatsService = knowledgeUsageStatsService;
        this.clinicalSignalsService = clinicalSignalsService;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 列出已登记的受控工具目录（用途/数据级别/所需权限），供 CLI diagnostics / MCP 工具发现。
     */
    public List<ControlledToolDescriptor> listTools() {
        return List.of(
            new ControlledToolDescriptor(TOOL_QUERY_RULE_USAGE,
                "查询规则使用聚合统计", EngineDataLevel.D2, REQUIRED_PERMISSION),
            new ControlledToolDescriptor(TOOL_SUMMARIZE_ENGINE_SIGNALS,
                "汇总规则、知识、临床信号的聚合信号", EngineDataLevel.D2, REQUIRED_PERMISSION));
    }

    /**
     * 执行受控工具，返回 FR-4 治理信封。未知工具结构化 404（不泄漏内部）。
     */
    @Transactional(readOnly = true)
    public ToolExecutionEnvelope execute(String toolName, ToolExecutionRequest request) {
        return switch (toolName) {
            case TOOL_QUERY_RULE_USAGE -> executeQueryRuleUsage(request);
            case TOOL_SUMMARIZE_ENGINE_SIGNALS -> executeSummarizeEngineSignals(request);
            default -> throw ApiException.notFound("受控工具 " + toolName);
        };
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
        return new ToolExecutionEnvelope(toolName, level, DESENSITIZATION_POLICY,
            generatedAt != null ? generatedAt.toString() : null, true, degraded, degradeReason,
            traceId, outputHash, payload);
    }
}
