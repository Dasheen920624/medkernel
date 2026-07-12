package com.medkernel.engine.knowledge.authority;

/** 医院由软件清单或独立配置预置的最小平台信任锚。 */
public record TrustedAuthorityAnchor(
    String authorityId,
    String rootFingerprint
) {
}
