package com.medkernel.engine.knowledge.production.generation;

import com.medkernel.engine.versioning.VersionedAssetType;

/** 未生成的资产类型与诚实原因（AIK-STD-04，铁律 #1 无源不生成）。 */
public record SkippedType(VersionedAssetType assetType, String reason) {
}
