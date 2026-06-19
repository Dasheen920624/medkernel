package com.medkernel.engine.knowledge.production.initialization;

/** KNOWGEN 初始化发行目录项。 */
public record KnowledgeInitializationCatalogItem(
    String catalogCode,
    String title,
    InitializationReleaseType releaseType,
    InitializationPhase phase
) {
}
