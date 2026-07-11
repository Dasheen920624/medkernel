package com.medkernel.engine.release;

import com.medkernel.engine.context.ClinicalRuntimeRelease;

/**
 * 机构生效版本离线交付文件恢复执行结果。
 *
 * @param status 恢复状态
 * @param runtimeMutation 是否生成新的机构生效版本，成功恢复必须为 true
 * @param evidenceId 离线交付证据 ID
 * @param sourceReleaseId 离线文件来源机构生效版本 ID
 * @param targetHospitalId 恢复目标医院 ID
 * @param fileDigest 已验签文件摘要
 * @param manifestSha256 恢复快照完整清单摘要
 * @param itemCount 恢复快照物化资产条目数
 * @param restoredRelease 新生成的机构生效版本
 */
public record RuntimeReleaseOfflineRestoreResponse(
    String status,
    boolean runtimeMutation,
    String evidenceId,
    String sourceReleaseId,
    String targetHospitalId,
    String fileDigest,
    String manifestSha256,
    int itemCount,
    ClinicalRuntimeRelease restoredRelease
) {
}
