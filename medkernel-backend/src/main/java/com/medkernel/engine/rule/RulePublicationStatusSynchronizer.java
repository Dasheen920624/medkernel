package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.medkernel.engine.versioning.AssetPublicationStatusSynchronizer;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 规则统一资产版本发布后的规则定义与版本投影状态同步器。
 */
@Component
public class RulePublicationStatusSynchronizer implements AssetPublicationStatusSynchronizer {

    private final RuleDefinitionRepository definitions;
    private final RuleVersionRepository versions;

    public RulePublicationStatusSynchronizer(
            RuleDefinitionRepository definitions,
            RuleVersionRepository versions) {
        this.definitions = definitions;
        this.versions = versions;
    }

    @Override
    public void afterPublished(
            AssetVersion publishedVersion,
            Instant publishedAt,
            String actor,
            String traceId) {
        if (publishedVersion.assetType() != VersionedAssetType.RULE) {
            return;
        }
        if (publishedVersion.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "规则资产同步只接受已发布版本");
        }
        int ruleVersionNo = AssetVersionNumbers.intSequence(
            publishedVersion.versionNo(),
            "规则版本");
        RuleDefinition rule = definitions
            .findByTenantIdAndRuleCode(
                publishedVersion.tenantId(),
                publishedVersion.assetIdentity())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_RULE_002,
                "规则资产版本缺少规则定义投影，禁止发布: "
                    + publishedVersion.assetIdentity() + "@" + publishedVersion.versionNo()));
        RuleVersion version = versions
            .findByRuleIdAndTenantIdAndVersionNo(
                rule.ruleId(),
                publishedVersion.tenantId(),
                ruleVersionNo)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_RULE_003,
                "规则资产版本缺少规则版本投影，禁止发布: "
                    + publishedVersion.assetIdentity() + "@" + publishedVersion.versionNo()));
        if (rule.status() == RuleDefinitionStatus.PUBLISHED
                && version.status() == RuleVersionStatus.PUBLISHED
                && version.versionId().equals(rule.activeVersionId())) {
            return;
        }
        if (rule.status() != RuleDefinitionStatus.DRAFT
                && rule.status() != RuleDefinitionStatus.PUBLISHED) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "规则定义当前状态不允许同步发布: "
                    + rule.ruleCode() + "=" + rule.status());
        }
        if (version.status() != RuleVersionStatus.DRAFT
                && version.status() != RuleVersionStatus.PUBLISHED) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "规则版本当前状态不允许同步发布: "
                    + version.versionId() + "=" + version.status());
        }
        RuleVersion publishedRuleVersion = new RuleVersion(
            version.id(),
            version.versionId(),
            version.tenantId(),
            version.ruleId(),
            version.versionNo(),
            version.sourceRef(),
            version.changeSummary(),
            version.dslJson(),
            version.explanationJson(),
            RuleVersionStatus.PUBLISHED,
            publishedAt,
            actor,
            version.rollbackVersionId(),
            version.createdAt(),
            version.createdBy(),
            publishedAt,
            actor,
            traceId
        );
        RuleDefinition publishedRule = new RuleDefinition(
            rule.id(),
            rule.ruleId(),
            rule.tenantId(),
            rule.ruleCode(),
            rule.name(),
            rule.ruleType(),
            rule.authoringMode(),
            rule.riskLevel(),
            rule.priority(),
            rule.suppressedBy(),
            rule.dedupeWindowSeconds(),
            RuleDefinitionStatus.PUBLISHED,
            version.versionId(),
            rule.applicableOrgUnitId(),
            rule.createdAt(),
            rule.createdBy(),
            publishedAt,
            actor,
            traceId
        );
        versions.save(publishedRuleVersion);
        definitions.save(publishedRule);
    }
}
