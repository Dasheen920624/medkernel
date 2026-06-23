package com.medkernel.engine.cdss.risk;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 从医院运行修订中选择本次推荐评估使用的 CDSS 风险矩阵版本。
 */
@Component
public class RuntimeReleaseCdssRiskMatrixSelector {

    private static final String SOURCE_PREFIX = "cdss-risk-matrix:";

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final AssetVersionRepository assetVersions;
    private final CdssRiskMatrixRepository matrices;

    public RuntimeReleaseCdssRiskMatrixSelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            AssetVersionRepository assetVersions,
            CdssRiskMatrixRepository matrices) {
        this.runtime = runtime;
        this.assetVersions = assetVersions;
        this.matrices = matrices;
    }

    public Optional<CdssRiskMatrixRule> selectRule(
            String tenantId,
            String runtimeReleaseId,
            String triggerPoint,
            RecommendationRiskLevel severityLevel,
            CdssAutomationLevel automationLevel) {
        ClinicalRuntimeReleaseContent content = resolve(tenantId, runtimeReleaseId);
        String trigger = requireText(triggerPoint, "triggerPoint");
        RecommendationRiskLevel severity =
            severityLevel == null ? RecommendationRiskLevel.LOW : severityLevel;
        CdssAutomationLevel automation =
            automationLevel == null ? CdssAutomationLevel.INFORM_ONLY : automationLevel;
        for (ClinicalRuntimeReleaseItem item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.CDSS_RISK) {
                continue;
            }
            AssetVersion version = requireVersion(item);
            String matrixVersion = parseMatrixVersion(version.sourceRef());
            List<CdssRiskMatrixRule> rows = matrices
                .findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                    version.tenantId(), matrixVersion);
            return rows.stream()
                .filter(rule -> trigger.equals(rule.triggerPoint()))
                .filter(rule -> severity == rule.severityLevel())
                .filter(rule -> automation == rule.automationLevel())
                .max(Comparator.comparing(CdssRiskMatrixRule::updatedAt)
                    .thenComparing(rule -> rule.id() == null ? Long.MIN_VALUE : rule.id()));
        }
        return Optional.empty();
    }

    private ClinicalRuntimeReleaseContent resolve(String tenantId, String runtimeReleaseId) {
        try {
            return runtime.resolve(
                requireText(tenantId, "tenantId"),
                requireText(runtimeReleaseId, "runtimeReleaseId"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private AssetVersion requireVersion(ClinicalRuntimeReleaseItem item) {
        AssetVersion version = assetVersions
            .findByVersionIdAndTenantId(
                requireText(item.versionId(), "CDSS 风险矩阵资产版本"),
                requireText(item.sourceTenantId(), "CDSS 风险矩阵来源租户"))
            .orElseThrow(() -> invalid("运行修订锁定 CDSS 风险矩阵资产版本不存在：" + item.versionId()));
        if (version.assetType() != VersionedAssetType.CDSS_RISK
                || !Objects.equals(version.assetIdentity(), item.assetIdentity())
                || !Objects.equals(version.contentHash(), item.contentHash())) {
            throw invalid("运行修订 CDSS 风险矩阵资产与版本正文不一致：" + item.assetIdentity());
        }
        return version;
    }

    private String parseMatrixVersion(String sourceRef) {
        String source = requireText(sourceRef, "CDSS 风险矩阵资产来源");
        if (!source.startsWith(SOURCE_PREFIX)) {
            throw invalid("CDSS 风险矩阵资产来源无效：" + source);
        }
        String matrixVersion = source.substring(SOURCE_PREFIX.length()).trim();
        if (matrixVersion.isBlank()) {
            throw invalid("CDSS 风险矩阵资产来源无效：" + source);
        }
        return matrixVersion;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    private static ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.CONFLICT, message, cause);
    }
}
