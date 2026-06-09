package com.medkernel.engine.versioning;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * 灰度观察自动暂停阈值。
 */
public record RolloutThresholds(
    @DecimalMin("0.0") @DecimalMax("1.0") Double maxHitRate,
    @DecimalMin("0.0") @DecimalMax("1.0") Double maxBlockRate,
    @DecimalMin("0.0") @DecimalMax("1.0") Double maxManualRejectionRate,
    @DecimalMin("0.0") @DecimalMax("1.0") Double maxAnomalyRate
) {
}
