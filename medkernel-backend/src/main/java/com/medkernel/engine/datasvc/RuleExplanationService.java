package com.medkernel.engine.datasvc;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 规则解释服务（DATASVC-01 PR2-b，受控工具 {@code explainRule} 上游）。
 *
 * <p>把单条规则的已发布资产元数据沉淀为 D1 解释读模型，只读现有 {@code rule_definition}（engine-rule 所属，
 * 仅读不写、不违域归属）并强制租户隔离。落点：FR-1 统一数据服务读模型、FR-2 数据分级（D1 已发布资产元数据，
 * 无患者字段落地）、FR-6 全审计、FR-7 诚实降级。不存在规则返回结构化 not-found（不泄漏内部）；上游不可用诚实标
 * {@code degraded}，元数据字段留空不以伪造元数据伪装（铁律 #1）。
 */
@Service
public class RuleExplanationService {

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final AuditRecorder auditRecorder;

    public RuleExplanationService(RuleDefinitionRepository ruleDefinitionRepository,
            AuditRecorder auditRecorder) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 解释单条规则的 D1 已发布资产元数据。规则不存在抛结构化 not-found；上游不可用诚实降级。
     */
    @Transactional(readOnly = true)
    public RuleExplanation explainRule(String ruleId) {
        String tenantId = requireCurrentTenant();

        RuleDefinition rule;
        try {
            rule = ruleDefinitionRepository.findByRuleIdAndTenantId(ruleId, tenantId).orElse(null);
        } catch (RuntimeException upstreamDown) {
            // 上游规则定义库不可用：诚实降级，不以伪造元数据伪装（铁律 #1）。
            auditRecorder.record(AuditAction.EXECUTE, "rule_definition", "explain-rule",
                "规则解释上游不可用，已诚实降级：" + upstreamDown.getMessage());
            return new RuleExplanation(ruleId, null, null, null, null, null, null, null,
                EngineDataLevel.D1, Instant.now(), true, "上游规则定义暂不可用，未返回解释");
        }

        if (rule == null) {
            throw ApiException.notFound("规则 " + ruleId);
        }

        auditRecorder.record(AuditAction.EXECUTE, "rule_definition", "explain-rule",
            "解释规则 D1 元数据 tenant=" + tenantId + " ruleId=" + ruleId);
        return new RuleExplanation(rule.ruleId(), rule.ruleCode(), rule.name(),
            nameOf(rule.ruleType()), nameOf(rule.riskLevel()), nameOf(rule.status()),
            rule.activeVersionId(), rule.packageVersion(),
            EngineDataLevel.D1, Instant.now(), false, null);
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
