package com.medkernel.engine.sandbox.compare;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.EffectivePackageItem;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/** 按当前冻结有效包中的精确统一版本执行规则，不再次解析当前状态。 */
@Component
public class SandboxCurrentRuleExecutor {

    private final ObjectMapper json;
    private final RuleDslEvaluator evaluator;
    private final AssetVersionRepository assets;
    private final RuleDefinitionRepository definitions;
    private final RuleVersionRepository versions;

    public SandboxCurrentRuleExecutor(
            ObjectMapper json,
            RuleDslEvaluator evaluator,
            AssetVersionRepository assets,
            RuleDefinitionRepository definitions,
            RuleVersionRepository versions) {
        this.json = json;
        this.evaluator = evaluator;
        this.assets = assets;
        this.definitions = definitions;
        this.versions = versions;
    }

    public List<SandboxComparableRuleResult> execute(
            EffectiveKnowledgePackageResponse effectivePackage,
            JsonNode immutableContext) {
        if (effectivePackage == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "当前冻结有效包不能为空");
        }
        List<SandboxComparableRuleResult> results = new ArrayList<>();
        for (EffectivePackageItem item : effectivePackage.items()) {
            if (item.assetType() != VersionedAssetType.RULE) {
                continue;
            }
            AssetVersion asset = exactAsset(item);
            RuleDefinition rule = definitions.findByTenantIdAndRuleCode(
                    asset.tenantId(), asset.assetIdentity())
                .orElseThrow(() -> conflict("当前冻结规则定义不存在：" + asset.assetIdentity()));
            int versionNo = parseVersion(item.effectiveVersion());
            RuleVersion version = versions.findByRuleIdAndTenantIdAndVersionNo(
                    rule.ruleId(), asset.tenantId(), versionNo)
                .orElseThrow(() -> conflict(
                    "当前冻结规则内容版本不存在：" + rule.ruleCode() + "@" + item.effectiveVersion()));
            if (version.status() != RuleVersionStatus.PUBLISHED) {
                throw conflict("当前冻结规则内容不是已发布状态：" + rule.ruleCode());
            }
            JsonNode dsl = readDsl(version.dslJson(), rule.ruleCode());
            JsonNode canonicalContext = immutableContext != null
                    && immutableContext.path("resources").isObject()
                ? immutableContext.path("resources")
                : immutableContext;
            RuleDslEvaluation evaluation = evaluator.evaluate(dsl, canonicalContext);
            results.add(new SandboxComparableRuleResult(
                rule.ruleCode(), rule.name(), asset.versionId(), asset.versionNo(),
                item.sourceTier(), asset.tenantId(), asset.contentHash(), evaluation.hit(),
                evaluation.severity() == null ? null : evaluation.severity().name(),
                evaluation.actions(), evaluation.explanation()));
        }
        return List.copyOf(results);
    }

    private AssetVersion exactAsset(EffectivePackageItem item) {
        if (item.sourceTenantId() == null || item.sourceTenantId().isBlank()
                || item.sourceVersionId() == null || item.sourceVersionId().isBlank()) {
            throw conflict("当前有效包规则缺少精确来源版本：" + item.assetId());
        }
        AssetVersion asset = assets.findByVersionIdAndTenantId(
                item.sourceVersionId(), item.sourceTenantId())
            .orElseThrow(() -> conflict("当前冻结统一规则版本不存在：" + item.sourceVersionId()));
        if (asset.assetType() != VersionedAssetType.RULE
                || asset.status() != AssetVersionStatus.PUBLISHED
                || !asset.versionNo().equals(item.effectiveVersion())) {
            throw conflict("当前冻结统一规则版本身份或状态漂移：" + item.sourceVersionId());
        }
        if (!asset.contentHash().equals(item.contentHash())) {
            throw conflict("当前冻结统一规则版本摘要漂移：" + item.sourceVersionId());
        }
        return asset;
    }

    private JsonNode readDsl(String source, String ruleCode) {
        try {
            JsonNode dsl = json.readTree(source);
            if (dsl == null || !dsl.isObject()) {
                throw new ApiException(ErrorCode.ENG_RULE_001, "当前规则 DSL 必须为 JSON 对象：" + ruleCode);
            }
            return dsl;
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "当前规则 DSL 已损坏：" + ruleCode, exception);
        }
    }

    private static int parseVersion(String version) {
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "当前规则版本号不是有效整数：" + version, exception);
        }
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
