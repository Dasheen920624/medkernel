package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

/**
 * 由已签软件清单或独立认证配置提供的公开信任锚。
 *
 * <p>该值不得从待导入医疗资源包构造；枚举刻意不提供包介质来源，避免首次使用即信任。
 */
public record VerifiedTrustAnchor(
    String authorityId,
    String rootFingerprint,
    String rootCertificatePem,
    TrustAnchorSource source,
    String evidenceDigest,
    Instant verifiedAt
) {
}
