package com.medkernel.engine.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleCreateResponse;
import com.medkernel.engine.rule.RuleGovernanceResponse;
import com.medkernel.engine.rule.RuleGovernanceState;
import com.medkernel.engine.rule.RuleImpactResponse;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;

/**
 * 批量创作对规则域稳定能力的调用端口。
 */
public interface AuthoringBatchRulePort {

    AuthoringBatchRuleTemplate loadTemplate(String ruleId);

    RuleCreateResponse createDraft(AuthoringBatchRuleDraftCommand command);

    RuleImpactResponse impact(String ruleId);

    RuleGovernanceResponse transition(String ruleId, AuthoringBatchRuleTransitionCommand command);
}

record AuthoringBatchRuleTemplate(
    String ruleId,
    RuleType ruleType,
    RuleAuthoringMode authoringMode,
    RuleRiskLevel riskLevel,
    int priority,
    String suppressedBy,
    int dedupeWindowSeconds,
    String packageVersion,
    String applicableOrgUnitId,
    String sourceRef,
    JsonNode dsl,
    JsonNode explanation
) {}

record AuthoringBatchRuleDraftCommand(
    String ruleCode,
    String name,
    AuthoringBatchRuleTemplate template,
    String packageVersion,
    String applicableOrgUnitId,
    String changeSummary,
    JsonNode parameterBindings
) {}

record AuthoringBatchRuleTransitionCommand(
    RuleGovernanceState targetState,
    String impactDigest,
    String reason
) {}
