package com.medkernel.engine.interop;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdshook.CdsHookCard;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;

/**
 * 规则 DSL 与 CDS Hooks/CQL/Arden 标准互操作映射结果。
 *
 * <p>内部 DSL 仍是唯一权威；标准结构用于外部交换、审计说明和可逆导回。
 */
public record RuleCdsHooksMapping(
    ClinicalEventTriggerPoint hook,
    String ruleCode,
    String name,
    RuleType ruleType,
    RuleAuthoringMode authoringMode,
    RuleRiskLevel riskLevel,
    String sourceRef,
    JsonNode condition,
    ObjectNode cdsService,
    List<CdsHookCard> cards,
    ObjectNode cql,
    ObjectNode arden
) {
    public RuleCdsHooksMapping {
        cards = cards == null ? List.of() : List.copyOf(cards);
        condition = condition == null ? null : condition.deepCopy();
        cdsService = cdsService == null ? null : cdsService.deepCopy();
        cql = cql == null ? null : cql.deepCopy();
        arden = arden == null ? null : arden.deepCopy();
    }
}
