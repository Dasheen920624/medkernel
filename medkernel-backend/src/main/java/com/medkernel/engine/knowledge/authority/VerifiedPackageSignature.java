package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

/** 固定根验签成功后的不可伪造类型化结果。 */
public record VerifiedPackageSignature(
    String authorityId,
    String issuerInstanceId,
    String keyId,
    String rootFingerprint,
    long releaseSequence,
    String manifestDigest,
    String certificateChainPem,
    Instant keyNotBefore,
    Instant keyNotAfter,
    Instant signedAt,
    Instant verifiedAt
) {
}
