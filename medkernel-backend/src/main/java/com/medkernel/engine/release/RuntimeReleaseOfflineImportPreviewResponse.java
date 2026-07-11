package com.medkernel.engine.release;

/**
 * 机构生效版本离线交付文件导入预检结果。
 *
 * @param status 预检状态
 * @param runtimeMutation 预检是否改变运行版本，必须为 false
 * @param signatureValid 国密签名是否有效
 * @param manifestMatched 离线清单摘要是否与当前记录一致
 * @param releaseId 交付文件中的机构生效版本 ID
 * @param hospitalId 交付文件中的医院 ID
 * @param manifestSha256 交付文件中的完整清单 SHA-256
 * @param fileDigest 文件 SM3 摘要
 * @param itemCount 交付文件中的物化资产条目数
 * @param message 预检说明
 */
public record RuntimeReleaseOfflineImportPreviewResponse(
    String status,
    boolean runtimeMutation,
    boolean signatureValid,
    boolean manifestMatched,
    String releaseId,
    String hospitalId,
    String manifestSha256,
    String fileDigest,
    int itemCount,
    String message
) {
}
