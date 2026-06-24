package com.medkernel.engine.integration.runtime;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;

/**
 * 第三方可读取的当前机构生效版本快照。
 */
public record ThirdPartyRuntimeReleaseResponse(
    String contractVersion,
    String releaseId,
    String tenantId,
    String hospitalId,
    long revisionNo,
    String platformBaselineReleaseId,
    String manifestSha256,
    Instant activatedAt,
    int assetCount,
    List<ClinicalRuntimeReleaseItem> assets
) {
    public ThirdPartyRuntimeReleaseResponse {
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    static ThirdPartyRuntimeReleaseResponse from(
            String contractVersion,
            ClinicalRuntimeReleaseContent content) {
        ClinicalRuntimeRelease release = content.release();
        return new ThirdPartyRuntimeReleaseResponse(
            contractVersion,
            release.releaseId(),
            release.tenantId(),
            release.hospitalId(),
            release.revisionNo(),
            release.platformBaselineReleaseId(),
            release.manifestSha256(),
            release.activatedAt(),
            content.items().size(),
            content.items()
        );
    }
}
