package com.medkernel.engine.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 全专业资产模板注册表测试（AIK-STD-12 FR-1）。
 */
class ProfessionalAssetTemplateRegistryTest {

    private final ProfessionalAssetTemplateRegistry registry = new ProfessionalAssetTemplateRegistry();

    @Test
    void coversAllFr1ProfessionsWithNonEmptySections() {
        List<ProfessionalAssetTemplate> all = registry.listAll();
        // FR-1 列举十类（术语/规则/路径/推荐/指标/随访/护理/报告/中医/医保）+ 指南/药品/诊断 = 13 专业模板
        assertThat(all).hasSize(13);
        assertThat(all).allSatisfy(t -> {
            assertThat(t.professionCode()).isNotBlank();
            assertThat(t.displayName()).isNotBlank();
            assertThat(t.assetType()).isNotNull();
            assertThat(t.sections()).isNotEmpty();
            assertThat(t.sections()).allSatisfy(s -> {
                assertThat(s.key()).isNotBlank();
                assertThat(s.label()).isNotBlank();
            });
        });
    }

    @Test
    void medicalDomainTemplatesAreKnowledgeTypedAndMatchable() {
        Optional<ProfessionalAssetTemplate> nursing =
            registry.findByAssetTypeAndDomain(VersionedAssetType.KNOWLEDGE, KnowledgeDomain.NURSING);
        assertThat(nursing).isPresent();
        assertThat(nursing.get().sections()).anySatisfy(s -> assertThat(s.label()).contains("护理"));
    }

    @Test
    void structuralTemplatesHaveNullDomain() {
        Optional<ProfessionalAssetTemplate> rule =
            registry.findByAssetTypeAndDomain(VersionedAssetType.RULE, null);
        assertThat(rule).isPresent();
        assertThat(rule.get().knowledgeDomain()).isNull();
    }

    @Test
    void listIsImmutable() {
        List<ProfessionalAssetTemplate> all = registry.listAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(null));
    }
}
