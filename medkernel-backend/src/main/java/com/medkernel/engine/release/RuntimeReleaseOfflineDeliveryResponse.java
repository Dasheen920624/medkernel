package com.medkernel.engine.release;

import java.util.List;

/**
 * 机构生效版本离线交付文件导出结果。
 *
 * @param deliveryKind 交付文件类型，固定为机构生效版本完整快照
 * @param evidenceId 可信存证证据 ID
 * @param fileUri 受鉴权保护的真实文件下载 URI
 * @param fileDigest 文件 SM3 摘要
 * @param signatureAlgorithm 证据签名算法
 * @param runtimeMutation 离线交付导出是否改变运行版本，必须为 false
 * @param release 生效版本元数据
 * @param items 完整物化资产清单
 */
public record RuntimeReleaseOfflineDeliveryResponse(
    String deliveryKind,
    String evidenceId,
    String fileUri,
    String fileDigest,
    String signatureAlgorithm,
    boolean runtimeMutation,
    ClinicalRuntimeReleaseOfflineSnapshot release,
    List<ClinicalRuntimeReleaseItemOfflineSnapshot> items
) {
    public RuntimeReleaseOfflineDeliveryResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
