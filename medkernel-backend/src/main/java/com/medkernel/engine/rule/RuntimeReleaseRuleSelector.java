package com.medkernel.engine.rule;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetTriggerBinding;
import com.medkernel.engine.versioning.AssetTriggerBindingRepository;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 按机构生效版本与临床触发点选择确切规则版本。
 */
@Component
public class RuntimeReleaseRuleSelector {

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final RuleDefinitionRepository definitions;
    private final RuleVersionRepository versions;
    private final AssetTriggerBindingRepository triggers;

    public RuntimeReleaseRuleSelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            RuleDefinitionRepository definitions,
            RuleVersionRepository versions,
            AssetTriggerBindingRepository triggers) {
        this.runtime = runtime;
        this.definitions = definitions;
        this.versions = versions;
        this.triggers = triggers;
    }

    public RuntimeRuleSelection select(
            String tenantId,
            String runtimeReleaseId,
            String triggerPoint) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        String normalizedReleaseId = requireText(runtimeReleaseId, "runtimeReleaseId");
        String normalizedTrigger = requireText(triggerPoint, "triggerPoint");
        ClinicalRuntimeReleaseContent content;
        try {
            content = runtime.resolve(normalizedTenantId, normalizedReleaseId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_006, exception.getMessage(), exception);
        }

        List<RuntimeRuleReference> selectedRules = new ArrayList<>();
        for (var item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.RULE) {
                continue;
            }
            List<AssetTriggerBinding> matchingTriggers = triggers
                .findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
                    item.sourceTenantId(),
                    item.versionId(),
                    AssetTriggerPurpose.RULE_EXECUTION,
                    normalizedTrigger);
            if (matchingTriggers.isEmpty()) {
                continue;
            }
            if (matchingTriggers.stream().anyMatch(binding ->
                    binding.assetType() != VersionedAssetType.RULE
                        || !item.assetIdentity().equals(binding.assetIdentity()))) {
                throw new ApiException(
                    ErrorCode.ENG_RULE_006,
                    "机构生效版本规则触发绑定与资产身份不一致：" + item.assetIdentity());
            }
            RuleDefinition rule = definitions
                .findByTenantIdAndRuleCode(item.sourceTenantId(), item.assetIdentity())
                .orElseThrow(() -> new ApiException(
                    ErrorCode.ENG_RULE_006,
                    "机构生效版本内规则不存在：" + item.assetIdentity()
                ));
            RuleVersion pinnedVersion = versions
                .findByRuleIdAndTenantIdAndVersionNo(
                    rule.ruleId(), rule.tenantId(),
                    parseVersionNo(item.versionNo(), item.assetIdentity()))
                .orElseThrow(() -> new ApiException(
                    ErrorCode.ENG_RULE_006,
                    "机构生效版本锁定规则版本不存在："
                        + item.assetIdentity() + "@" + item.versionNo()
                ));
            if (rule.status() != RuleDefinitionStatus.PUBLISHED
                    || pinnedVersion.status() != RuleVersionStatus.PUBLISHED) {
                throw new ApiException(
                    ErrorCode.ENG_RULE_006,
                    "机构生效版本锁定规则版本未发布："
                        + item.assetIdentity() + "@" + item.versionNo()
                );
            }
            selectedRules.add(new RuntimeRuleReference(
                rule.tenantId(), rule.ruleId(), pinnedVersion.versionId(),
                item.versionId(), item.versionNo(), item.contentHash(), item.sourceLayer().name()));
        }
        ClinicalRuntimeRelease release = content.release();
        return new RuntimeRuleSelection(
            release.releaseId(),
            release.platformBaselineReleaseId(),
            selectedRules
        );
    }

    private int parseVersionNo(String value, String ruleId) {
        try {
            return AssetVersionNumbers.intSequence(value, "规则版本");
        } catch (ApiException exception) {
            throw new ApiException(
                ErrorCode.ENG_RULE_006, "运行规则版本号无效：" + ruleId + "@" + value, exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.ENG_RULE_006, field + " 不能为空");
        }
        return value.trim();
    }
}
