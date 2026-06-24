package com.medkernel.engine.versioning;

/**
 * 版本发布治理证据。
 *
 * <p>普通租户发布可为空；平台发布必须提供完整质量门。发布责任由权限、操作人、
 * 发布理由、影响摘要和审计记录共同确认，不再要求额外签名人。
 */
public record VersionPublishEvidence(
    VersionPublishQualityGate qualityGate
) {
    private static final VersionPublishEvidence EMPTY = new VersionPublishEvidence(null);

    public static VersionPublishEvidence empty() {
        return EMPTY;
    }

    public static VersionPublishEvidence orEmpty(VersionPublishEvidence evidence) {
        return evidence == null ? EMPTY : evidence;
    }
}
