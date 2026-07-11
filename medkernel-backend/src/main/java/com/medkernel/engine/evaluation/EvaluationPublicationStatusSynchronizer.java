package com.medkernel.engine.evaluation;

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
 * 评价指标统一资产版本发布后的评价指标投影状态同步器。
 */
@Component
public class EvaluationPublicationStatusSynchronizer implements AssetPublicationStatusSynchronizer {

    private final EvaluationIndicatorRepository indicators;

    public EvaluationPublicationStatusSynchronizer(EvaluationIndicatorRepository indicators) {
        this.indicators = indicators;
    }

    @Override
    public void afterPublished(
            AssetVersion publishedVersion,
            Instant publishedAt,
            String actor,
            String traceId) {
        if (publishedVersion.assetType() != VersionedAssetType.EVALUATION) {
            return;
        }
        if (publishedVersion.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "评价指标资产同步只接受已发布版本");
        }
        int indicatorVersionNo = AssetVersionNumbers.intSequence(
            publishedVersion.versionNo(),
            "评价指标版本");
        EvaluationIndicator indicator = indicators
            .findByTenantIdAndIndicatorCodeAndVersionNo(
                publishedVersion.tenantId(),
                publishedVersion.assetIdentity(),
                indicatorVersionNo)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_EVAL_003,
                "评价指标资产版本缺少评价指标投影，禁止发布: "
                    + publishedVersion.assetIdentity() + "@" + publishedVersion.versionNo()));
        if (indicator.status() == EvaluationIndicatorStatus.ACTIVE) {
            return;
        }
        if (indicator.status() != EvaluationIndicatorStatus.DRAFT
                && indicator.status() != EvaluationIndicatorStatus.PENDING_REVIEW
                && indicator.status() != EvaluationIndicatorStatus.PUBLISHED
                && indicator.status() != EvaluationIndicatorStatus.GRAY) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "评价指标当前状态不允许同步发布: "
                    + indicator.indicatorCode() + "@" + indicator.versionNo()
                    + "=" + indicator.status());
        }
        for (EvaluationIndicator old : indicators.findByTenantIdAndIndicatorCodeAndStatus(
                publishedVersion.tenantId(),
                publishedVersion.assetIdentity(),
                EvaluationIndicatorStatus.ACTIVE)) {
            if (old.versionNo() == indicator.versionNo()) {
                continue;
            }
            indicators.save(withStatus(
                old,
                EvaluationIndicatorStatus.OFFLINE,
                old.publishedAt(),
                old.publishedBy(),
                old.activatedAt(),
                publishedAt,
                actor,
                traceId));
        }
        indicators.save(withStatus(
            indicator,
            EvaluationIndicatorStatus.ACTIVE,
            indicator.publishedAt() == null ? publishedAt : indicator.publishedAt(),
            indicator.publishedBy() == null ? actor : indicator.publishedBy(),
            publishedAt,
            publishedAt,
            actor,
            traceId));
    }

    private static EvaluationIndicator withStatus(
            EvaluationIndicator source,
            EvaluationIndicatorStatus status,
            Instant publishedAt,
            String publishedBy,
            Instant activatedAt,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        return new EvaluationIndicator(
            source.id(),
            source.indicatorId(),
            source.tenantId(),
            source.indicatorCode(),
            source.versionNo(),
            source.name(),
            source.subjectType(),
            source.denominatorDefinition(),
            source.numeratorDefinition(),
            source.exclusionDefinition(),
            source.scoringDefinition(),
            source.timeWindow(),
            source.organizationScope(),
            source.responsibleDepartmentId(),
            source.sourceRef(),
            status,
            publishedAt,
            publishedBy,
            activatedAt,
            source.createdAt(),
            source.createdBy(),
            updatedAt,
            updatedBy,
            traceId
        );
    }
}
