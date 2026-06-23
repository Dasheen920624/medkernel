package com.medkernel.engine.sandbox.compare;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
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

/** 按当前冻结医院运行修订中的精确版本执行规则，不再次解析当前状态。 */
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
            ClinicalRuntimeReleaseContent runtimeContent,
            JsonNode immutableContext) {
        if (runtimeContent == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "当前冻结运行修订不能为空");
        }
        List<SandboxComparableRuleResult> results = new ArrayList<>();
        for (ClinicalRuntimeReleaseItem item : runtimeContent.items()) {
            if (item.assetType() != VersionedAssetType.RULE
                    || item.entryState() != ReleaseEntryState.ACTIVE) {
                continue;
            }
            AssetVersion asset = exactAsset(item);
            RuleDefinition rule = definitions.findByTenantIdAndRuleCode(
                    asset.tenantId(), asset.assetIdentity())
                .orElseThrow(() -> conflict("当前冻结规则定义不存在：" + asset.assetIdentity()));
            int versionNo = parseVersion(item.versionNo());
            RuleVersion version = versions.findByRuleIdAndTenantIdAndVersionNo(
                    rule.ruleId(), asset.tenantId(), versionNo)
                .orElseThrow(() -> conflict(
                    "当前冻结规则内容版本不存在：" + rule.ruleCode() + "@" + item.versionNo()));
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
                sourceTier(item.sourceLayer()), asset.tenantId(), asset.contentHash(), evaluation.hit(),
                evaluation.severity() == null ? null : evaluation.severity().name(),
                evaluation.actions(), evaluation.explanation()));
        }
        return List.copyOf(results);
    }

    private AssetVersion exactAsset(ClinicalRuntimeReleaseItem item) {
        if (item.sourceTenantId() == null || item.sourceTenantId().isBlank()
                || item.versionId() == null || item.versionId().isBlank()) {
            throw conflict("当前运行修订规则缺少精确来源版本：" + item.assetIdentity());
        }
        AssetVersion asset = assets.findByVersionIdAndTenantId(
                item.versionId(), item.sourceTenantId())
            .orElseThrow(() -> conflict("当前冻结统一规则版本不存在：" + item.versionId()));
        if (asset.assetType() != VersionedAssetType.RULE
                || !executable(asset.status())
                || !asset.versionNo().equals(item.versionNo())) {
            throw conflict("当前冻结统一规则版本身份或状态漂移：" + item.versionId());
        }
        if (!asset.contentHash().equals(item.contentHash())) {
            throw conflict("当前冻结统一规则版本摘要漂移：" + item.versionId());
        }
        return asset;
    }

    private static boolean executable(AssetVersionStatus status) {
        return status == AssetVersionStatus.PUBLISHED
            || status == AssetVersionStatus.WITHDRAWN;
    }

    private static com.medkernel.engine.versioning.SourceTier sourceTier(
            ReleaseSourceLayer sourceLayer) {
        return sourceLayer == ReleaseSourceLayer.PLATFORM
            ? com.medkernel.engine.versioning.SourceTier.PLATFORM
            : com.medkernel.engine.versioning.SourceTier.ORG;
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
