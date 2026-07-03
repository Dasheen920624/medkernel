package com.medkernel.engine.authoring;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.rule.RuleCreateRequest;
import com.medkernel.engine.rule.RuleCreateResponse;
import com.medkernel.engine.rule.RuleDetailResponse;
import com.medkernel.engine.rule.RuleEngineService;
import com.medkernel.engine.rule.RuleGovernanceResponse;
import com.medkernel.engine.rule.RuleGovernanceTransitionRequest;
import com.medkernel.engine.rule.RuleImpactResponse;
import com.medkernel.engine.versioning.AssetTriggerBinding;
import com.medkernel.engine.versioning.AssetTriggerBindingInput;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 复用规则引擎既有创建、影响分析与治理状态机的批量适配器。
 */
@Component
public class RuleEngineAuthoringBatchAdapter implements AuthoringBatchRulePort {

    private final ObjectMapper json;
    private final RuleEngineService rules;

    public RuleEngineAuthoringBatchAdapter(ObjectMapper json, RuleEngineService rules) {
        this.json = json;
        this.rules = rules;
    }

    @Override
    public AuthoringBatchRuleTemplate loadTemplate(String ruleId) {
        RuleDetailResponse detail = rules.detail(ruleId);
        return new AuthoringBatchRuleTemplate(
            detail.definition().ruleId(),
            detail.definition().ruleType(),
            detail.definition().authoringMode(),
            detail.definition().riskLevel(),
            detail.definition().priority(),
            detail.definition().suppressedBy(),
            detail.definition().dedupeWindowSeconds(),
            detail.triggerBindings().stream().map(this::triggerInput).toList(),
            detail.definition().applicableOrgUnitId(),
            detail.version().sourceRef(),
            readJson(detail.version().dslJson(), "基准规则 DSL 无法解析"),
            readJson(detail.version().explanationJson(), "基准规则解释无法解析"));
    }

    @Override
    public RuleCreateResponse createDraft(AuthoringBatchRuleDraftCommand command) {
        AuthoringBatchRuleTemplate template = command.template();
        return rules.createRule(new RuleCreateRequest(
            null, null, null, null, null, null, null, null, null, null,
            java.util.List.of(),
            command.triggers() == null || command.triggers().isEmpty()
                ? template.triggers()
                : command.triggers(),
            command.ruleCode(),
            command.name(),
            template.ruleType(),
            template.authoringMode(),
            template.riskLevel(),
            template.priority(),
            template.suppressedBy(),
            template.dedupeWindowSeconds(),
            command.applicableOrgUnitId() == null
                ? template.applicableOrgUnitId()
                : command.applicableOrgUnitId(),
            template.sourceRef(),
            command.changeSummary(),
            template.dsl().deepCopy(),
            template.explanation() == null ? null : template.explanation().deepCopy(),
            command.parameterBindings()));
    }

    private AssetTriggerBindingInput triggerInput(AssetTriggerBinding binding) {
        try {
            List<String> requiredFields = json.readValue(
                binding.requiredFieldsJson(),
                json.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return new AssetTriggerBindingInput(
                binding.triggerPoint(), binding.purpose(), requiredFields);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.INTERNAL_ERROR,
                "基准规则触发绑定必需字段无法解析",
                exception
            );
        }
    }

    @Override
    public RuleImpactResponse impact(String ruleId) {
        return rules.impact(ruleId);
    }

    @Override
    public RuleGovernanceResponse transition(
            String ruleId,
            AuthoringBatchRuleTransitionCommand command) {
        return rules.transitionGovernance(
            ruleId,
            new RuleGovernanceTransitionRequest(
                command.targetState(), command.impactDigest(), command.reason()));
    }

    private JsonNode readJson(String value, String message) {
        if (value == null || value.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.ENG_RULE_001, message, ex);
        }
    }
}
