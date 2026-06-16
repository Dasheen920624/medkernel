package com.medkernel.engine.knowledge.production.generation;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 未生成的资产类型与诚实原因（AIK-STD-04）。
 *
 * <p>来源无锚点片段等情形下，该资产类型不产候选并记录真实原因（铁律 #1 无源不生成、不伪造候选）。
 */
public record SkippedType(VersionedAssetType assetType, String reason) {
}
