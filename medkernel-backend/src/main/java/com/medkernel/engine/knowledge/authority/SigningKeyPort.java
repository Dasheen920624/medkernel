package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

/**
 * 外置 HSM/KMS 签名密钥端口。
 *
 * <p>端口只交换公开标识、证书链、指纹和有效期；任何实现都不得返回私钥、凭证或可导出的
 * 密钥材料。私钥的生成、保存和签名操作始终留在受控密钥设施内。
 */
public interface SigningKeyPort {

    /**
     * 为指定发布实例创建一把独立、不可导出的签名密钥。
     *
     * @param authorityId 平台知识权威稳定标识
     * @param issuerInstanceId 发布实例稳定标识
     * @return 仅含公开元数据的密钥登记结果
     */
    ProvisionedSigningKey provisionSigningKey(String authorityId, String issuerInstanceId);

    /**
     * 从公开证书链计算叶子签名公钥指纹，用于阻止跨发布实例复用同一密钥材料。
     *
     * @param certificateChainPem 公开证书链
     * @return 规范化公钥指纹
     */
    String publicKeyFingerprint(String certificateChainPem);

    /** 外置密钥设施返回的公开登记元数据，不含任何私钥或访问凭证。 */
    record ProvisionedSigningKey(
        String authorityId,
        String issuerInstanceId,
        String keyId,
        String rootFingerprint,
        String certificateChainPem,
        String publicKeyFingerprint,
        Instant notBefore,
        Instant notAfter
    ) {
    }
}
