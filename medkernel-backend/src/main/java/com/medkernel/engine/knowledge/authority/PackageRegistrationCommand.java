package com.medkernel.engine.knowledge.authority;

/**
 * 医疗资源包登记命令。
 *
 * @param deliveryId 与传输介质、文件路径和宿主无关的交付标识
 * @param packageType 包类型；首发闭环仅接受完整包
 * @param parentDeliveryId 差量包直接父交付标识
 * @param parentManifestDigest 差量包直接父 manifest 摘要
 * @param baseManifestDigest 差量包基线 manifest 摘要
 */
public record PackageRegistrationCommand(
    String deliveryId,
    MedicalPackageType packageType,
    String parentDeliveryId,
    String parentManifestDigest,
    String baseManifestDigest
) {
}
