package com.medkernel.engine.context;

import java.util.List;

import com.medkernel.engine.release.ClinicalRuntimeReleaseItemOfflineSnapshot;

/**
 * 机构生效版本离线交付文件恢复命令。
 *
 * @param tenantId 租户 ID
 * @param hospitalId 目标医院 ID
 * @param expectedCurrentReleaseId 预期当前机构生效版本 ID
 * @param sourceReleaseId 离线文件来源机构生效版本 ID
 * @param platformBaselineReleaseId 离线文件来源平台标准版本 ID
 * @param manifestSha256 离线文件完整清单摘要
 * @param items 离线文件中的完整物化资产清单
 * @param actor 操作人
 * @param traceId 链路追踪 ID
 */
public record ClinicalRuntimeReleaseOfflineRestoreCommand(
    String tenantId,
    String hospitalId,
    String expectedCurrentReleaseId,
    String sourceReleaseId,
    String platformBaselineReleaseId,
    String manifestSha256,
    List<ClinicalRuntimeReleaseItemOfflineSnapshot> items,
    String actor,
    String traceId
) {
    public ClinicalRuntimeReleaseOfflineRestoreCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
