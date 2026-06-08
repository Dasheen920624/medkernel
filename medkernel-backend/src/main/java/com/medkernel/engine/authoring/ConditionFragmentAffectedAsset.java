package com.medkernel.engine.authoring;

/**
 * 条件片段影响分析命中的资产。
 */
public record ConditionFragmentAffectedAsset(
    String assetType,
    String assetId,
    String assetCode,
    String displayName,
    String impactReason
) {}
