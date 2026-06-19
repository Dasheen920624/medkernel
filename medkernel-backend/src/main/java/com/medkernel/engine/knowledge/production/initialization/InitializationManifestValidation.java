package com.medkernel.engine.knowledge.production.initialization;

/** 初始化发行清单的三层稳定摘要。 */
public record InitializationManifestValidation(
    String sourceManifestHash,
    String candidateManifestHash,
    String overallHash
) {
}
