package com.medkernel.engine.versioning;

/**
 * 版本发布治理证据。
 *
 * <p>普通租户低风险发布可为空；平台发布必须同时提供电子签名与质量门，
 * 高风险租户发布至少提供电子签名。
 */
public record VersionPublishEvidence(
    VersionElectronicSignature electronicSignature,
    VersionPublishQualityGate qualityGate
) {
    private static final VersionPublishEvidence EMPTY = new VersionPublishEvidence(null, null);

    public static VersionPublishEvidence empty() {
        return EMPTY;
    }

    public static VersionPublishEvidence orEmpty(VersionPublishEvidence evidence) {
        return evidence == null ? EMPTY : evidence;
    }
}
