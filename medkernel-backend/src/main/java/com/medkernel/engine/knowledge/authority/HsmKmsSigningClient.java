package com.medkernel.engine.knowledge.authority;

/**
 * 受控 HSM/KMS 设施的最小驱动合同。
 *
 * <p>驱动只允许创建不可导出密钥、返回公开证书链并按公开 {@code keyId} 完成签名。合同刻意不提供
 * 私钥读取、导出、备份或解封接口；设施级受控备份由设施自身运维边界负责，不能经过应用普通备份。
 */
public interface HsmKmsSigningClient {

    /**
     * 在外置设施内创建不可导出的 SM2 签名密钥并签发公开证书链。
     *
     * @param authorityId 平台知识权威稳定标识
     * @param issuerInstanceId 发布实例稳定标识
     * @return 公开密钥句柄与证书链
     */
    ProvisionedPublicKey provisionNonExportableSigningKey(
        String authorityId,
        String issuerInstanceId
    );

    /**
     * 使用设施内不可导出密钥签署规范化载荷。
     *
     * @param authorityId 平台知识权威稳定标识
     * @param issuerInstanceId 发布实例稳定标识
     * @param keyId 外置密钥公开标识
     * @param canonicalPayload 确定性编码后的待签载荷
     * @return 公开签名值
     */
    byte[] signWithNonExportableKey(
        String authorityId,
        String issuerInstanceId,
        String keyId,
        byte[] canonicalPayload
    );

    /** 外置设施造钥后返回的公开材料；有效期和根指纹由适配器从证书链独立计算。 */
    record ProvisionedPublicKey(String keyId, String certificateChainPem) {
    }
}
