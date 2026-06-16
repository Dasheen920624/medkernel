package com.medkernel.engine.knowledge.production.generation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 单类候选生成项（AIK-STD-04）。
 *
 * <p>声明一类候选的产出资产类型与物化目标知识身份（现有身份 异或 新建身份壳，由生产方显式申报）。
 */
public record GenerationItem(
    @NotNull VersionedAssetType assetType,
    @NotNull @Valid MaterializationTarget target
) {
}
