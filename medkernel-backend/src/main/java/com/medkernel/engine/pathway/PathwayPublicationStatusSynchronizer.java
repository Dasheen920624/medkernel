package com.medkernel.engine.pathway;

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
 * 路径统一资产版本发布后的临床路径投影状态同步器。
 */
@Component
public class PathwayPublicationStatusSynchronizer implements AssetPublicationStatusSynchronizer {

    private final PathwayTemplateRepository templates;

    public PathwayPublicationStatusSynchronizer(PathwayTemplateRepository templates) {
        this.templates = templates;
    }

    @Override
    public void afterPublished(
            AssetVersion publishedVersion,
            Instant publishedAt,
            String actor,
            String traceId) {
        if (publishedVersion.assetType() != VersionedAssetType.PATHWAY) {
            return;
        }
        if (publishedVersion.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "路径资产同步只接受已发布版本");
        }
        int templateVersion = AssetVersionNumbers.intSequence(
            publishedVersion.versionNo(),
            "临床路径版本");
        PathwayTemplate template = templates
            .findByTenantIdAndTemplateCodeAndTemplateVersion(
                publishedVersion.tenantId(),
                publishedVersion.assetIdentity(),
                templateVersion)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_004,
                "路径资产版本缺少临床路径投影，禁止发布: "
                    + publishedVersion.assetIdentity() + "@" + publishedVersion.versionNo()));
        if (template.status() == PathwayTemplateStatus.PUBLISHED) {
            return;
        }
        if (template.status() != PathwayTemplateStatus.DRAFT) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "临床路径当前状态不允许同步发布: "
                    + template.templateCode() + "@" + template.templateVersion()
                    + "=" + template.status());
        }
        templates.save(template.withStatus(
            PathwayTemplateStatus.PUBLISHED,
            publishedAt,
            actor,
            traceId));
    }
}
