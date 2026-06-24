package com.medkernel.engine.knowledge.production.gate;

import java.time.Instant;

/**
 * 服务端生成的不可变发布质量记录。
 */
public record PublicationQualityRecord(
    Long id,
    String jobCode,
    String candidateRef,
    Long identityId,
    Long versionId,
    String contentHash,
    Instant createdAt
) {
}
