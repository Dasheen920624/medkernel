package com.medkernel.engine.knowledge.production.initialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** 初始化发行内置目录测试。 */
class KnowledgeInitializationCatalogTest {

    private final KnowledgeInitializationCatalog catalog = new KnowledgeInitializationCatalog();

    @Test
    void coversAllKnowgenCardsExactlyOnceInDependencyOrder() {
        assertThat(catalog.listAll())
            .extracting(KnowledgeInitializationCatalogItem::catalogCode)
            .containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, 35)
                    .mapToObj(number -> "KNOWGEN-%02d".formatted(number))
                    .toList());
        assertThat(catalog.listAll()).hasSize(35);
        assertThat(catalog.listAll())
            .extracting(item -> item.phase().order())
            .isSorted();
    }

    @Test
    void exposesStableFoundationCoverageContract() {
        assertThat(catalog.requiredFoundationCoverage())
            .containsExactlyInAnyOrder(FoundationCoverageDimension.values());
        assertThat(catalog.requiredFoundationCoverage()).hasSize(12);
    }

    @Test
    void catalogTitlesDoNotExposeCompatibilityAsLaunchProductLanguage() {
        assertThat(catalog.listAll())
            .extracting(KnowledgeInitializationCatalogItem::title)
            .noneMatch(title -> title.contains("兼容"));
    }
}
