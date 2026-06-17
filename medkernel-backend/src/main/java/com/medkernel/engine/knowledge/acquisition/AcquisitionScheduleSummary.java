package com.medkernel.engine.knowledge.acquisition;

/**
 * AIK-STD-14 公域资料调度单轮结果。
 *
 * @param scannedCount       本轮扫描到的到期来源数
 * @param claimedCount       原子认领成功的来源数
 * @param submittedItemCount 已提交 SYS-05 批量项数
 * @param skippedCount       未提交来源数
 */
public record AcquisitionScheduleSummary(
    int scannedCount,
    int claimedCount,
    int submittedItemCount,
    int skippedCount
) {
}
