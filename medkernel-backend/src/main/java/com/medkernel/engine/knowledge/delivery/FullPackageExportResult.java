package com.medkernel.engine.knowledge.delivery;

import java.time.Instant;

/** 可回读、可下载的完整医疗资源包公开事实。 */
public record FullPackageExportResult(
    String deliveryId,
    String platformReleaseIdentity,
    String authorityId,
    String issuerInstanceId,
    String keyId,
    long releaseSequence,
    String manifestDigest,
    String packageFileDigest,
    long packageFileSize,
    String downloadUri,
    Instant signedAt,
    Instant registeredAt
) {
}
