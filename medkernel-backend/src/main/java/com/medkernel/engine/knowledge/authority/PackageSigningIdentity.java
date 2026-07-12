package com.medkernel.engine.knowledge.authority;

/**
 * 下一次医疗资源包签发所绑定的公开身份。
 *
 * @param authorityId 平台知识权威稳定标识
 * @param issuerInstanceId 当前活动签发实例标识
 * @param keyId 当前唯一活动签名密钥公开标识
 * @param rootFingerprint 包外预置可信根指纹
 * @param releaseSequence 下一次必须使用的连续发布序号
 */
public record PackageSigningIdentity(
    String authorityId,
    String issuerInstanceId,
    String keyId,
    String rootFingerprint,
    long releaseSequence
) {
}
