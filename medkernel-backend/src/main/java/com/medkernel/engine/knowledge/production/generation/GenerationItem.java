package com.medkernel.engine.knowledge.production.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 单类候选生成项：产出资产类型 + 物化目标身份（AIK-STD-04）。 */
public record GenerationItem(
    @NotNull VersionedAssetType assetType,
    @NotNull @Valid MaterializationTarget target
) {
}
