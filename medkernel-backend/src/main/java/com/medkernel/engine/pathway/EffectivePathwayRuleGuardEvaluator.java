package com.medkernel.engine.pathway;

import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Component;

/**
 * 按医院运行修订锁定的确切规则版本执行路径分支守卫。
 *
 * <p>路径正文只保存规则稳定编码和稳定资产 ID。具体来源层和版本全部来自不可变运行修订，
 * 不读取规则当前编辑指针，也不接受正文手工指定运行版本。
 */
@Component
public class EffectivePathwayRuleGuardEvaluator implements PathwayRuleGuardEvaluator {

    private static final Set<String> ALLOWED_FIELDS = Set.of("ruleRef", "ruleAssetId");

    private final ObjectMapper json;
    private final ClinicalRuntimeReleaseContentResolver releases;
    private final RuleDefinitionRepository definitions;
    private final RuleVersionRepository versions;
    private final RuleDslEvaluator rules;

    public EffectivePathwayRuleGuardEvaluator(
            ObjectMapper json,
            ClinicalRuntimeReleaseContentResolver releases,
            RuleDefinitionRepository definitions,
            RuleVersionRepository versions,
            RuleDslEvaluator rules) {
        this.json = json;
        this.releases = releases;
        this.definitions = definitions;
        this.versions = versions;
        this.rules = rules;
    }

    @Override
    public PathwayRuleGuardEvaluation evaluate(
            JsonNode reference,
            JsonNode context,
            String runtimeReleaseId) {
        validateReferenceShape(reference);
        String ruleCode = requiredText(reference, "ruleRef");
        String ruleAssetId = requiredText(reference, "ruleAssetId");
        String releaseId = requireRuntimeReleaseId(runtimeReleaseId);
        String tenantId = currentTenantId();
        ClinicalRuntimeReleaseContent content = resolveRelease(tenantId, releaseId);
        ClinicalRuntimeReleaseItem item = content.items().stream()
            .filter(candidate -> candidate.entryState() == ReleaseEntryState.ACTIVE)
            .filter(candidate -> candidate.assetType() == VersionedAssetType.RULE)
            .filter(candidate -> ruleCode.equals(candidate.assetIdentity()))
            .findFirst()
            .orElseThrow(() -> invalid(
                "规则未包含在医院运行修订中: " + ruleCode + " (" + ruleAssetId + ")"));
        RuleDefinition rule = definitions
            .findByTenantIdAndRuleCode(item.sourceTenantId(), ruleCode)
            .orElseThrow(() -> invalid("医院运行修订中的规则不存在: " + ruleAssetId));
        if (!ruleAssetId.equals(rule.ruleId())) {
            throw invalid(
                "路径规则 ID 与运行修订资产不一致: 声明 " + ruleAssetId
                    + "，实际 " + rule.ruleId());
        }
        RuleVersion version = versions
            .findByRuleIdAndTenantIdAndVersionNo(
                ruleAssetId,
                item.sourceTenantId(),
                parseVersionNo(item))
            .orElseThrow(() -> invalid(
                "医院运行修订中的规则版本不存在: "
                    + ruleAssetId + "@" + item.versionNo()));
        if (rule.status() != RuleDefinitionStatus.PUBLISHED
                || version.status() != RuleVersionStatus.PUBLISHED) {
            throw invalid("路径只能调用医院运行修订中的已发布规则: " + ruleCode);
        }

        try {
            RuleDslEvaluation evaluation = rules.evaluate(
                json.readTree(version.dslJson()),
                context == null ? json.createObjectNode() : context,
                tenantId,
                releaseId);
            return new PathwayRuleGuardEvaluation(
                evaluation.hit(),
                rule.ruleCode(),
                rule.ruleId(),
                version.versionId(),
                version.versionNo(),
                releaseId,
                item.sourceTenantId(),
                item.sourceLayer());
        } catch (ApiException exception) {
            if (exception.errorCode() == ErrorCode.ENG_PATHWAY_006) {
                throw exception;
            }
            throw invalid("路径规则执行失败: " + ruleCode + "；" + exception.getMessage(), exception);
        } catch (Exception exception) {
            throw invalid("路径规则 DSL 无法解析: " + ruleCode, exception);
        }
    }

    private ClinicalRuntimeReleaseContent resolveRelease(String tenantId, String releaseId) {
        try {
            return releases.resolve(tenantId, releaseId);
        } catch (RuntimeException exception) {
            throw invalid("医院运行修订无法解析: " + releaseId + "；" + exception.getMessage(), exception);
        }
    }

    private int parseVersionNo(ClinicalRuntimeReleaseItem item) {
        try {
            return AssetVersionNumbers.intSequence(item.versionNo(), "规则版本");
        } catch (ApiException exception) {
            throw invalid(
                "医院运行修订中的规则版本号不是整数: "
                    + item.assetIdentity() + "@" + item.versionNo(),
                exception);
        }
    }

    private String currentTenantId() {
        var orgScope = RequestContext.currentOrgScope();
        String tenantId = orgScope == null ? null : orgScope.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw invalid("路径规则守卫缺少租户上下文");
        }
        return tenantId;
    }

    private String requireRuntimeReleaseId(String runtimeReleaseId) {
        if (runtimeReleaseId == null || runtimeReleaseId.isBlank()) {
            throw invalid("路径规则守卫缺少医院运行修订 ID");
        }
        return runtimeReleaseId.trim();
    }

    private void validateReferenceShape(JsonNode reference) {
        if (reference == null || !reference.isObject()) {
            throw invalid("路径规则守卫必须是 JSON 对象");
        }
        reference.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw invalid("路径规则守卫不能混入内嵌条件字段: " + field);
            }
        });
    }

    private String requiredText(JsonNode node, String field) {
        return optionalText(node, field)
            .orElseThrow(() -> invalid("路径规则守卫缺少字段: " + field));
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.asText().trim());
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_PATHWAY_006, message);
    }

    private ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.ENG_PATHWAY_006, message, cause);
    }
}
