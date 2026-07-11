package com.medkernel.engine.release;

import java.time.Instant;

import com.medkernel.engine.context.ClinicalRuntimeRelease;

/**
 * 离线交付文件中的机构生效版本元数据快照。
 */
public record ClinicalRuntimeReleaseOfflineSnapshot(
    String releaseId,
    String tenantId,
    String hospitalId,
    Long revisionNo,
    String platformBaselineReleaseId,
    String manifestSha256,
    String rollbackFromReleaseId,
    Instant activatedAt,
    String activatedBy
) {
    public static ClinicalRuntimeReleaseOfflineSnapshot from(ClinicalRuntimeRelease release) {
        return new ClinicalRuntimeReleaseOfflineSnapshot(
            release.releaseId(),
            release.tenantId(),
            release.hospitalId(),
            release.revisionNo(),
            release.platformBaselineReleaseId(),
            release.manifestSha256(),
            release.rollbackFromReleaseId(),
            release.activatedAt(),
            release.activatedBy()
        );
    }
}
