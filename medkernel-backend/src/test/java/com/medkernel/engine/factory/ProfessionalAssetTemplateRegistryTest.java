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
        // FR-1 13 专业模板 + T7.2 评分/计算器 FORMULA 骨架 = 14 个代码态模板。
        assertThat(all).hasSize(14);
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
        assertThat(rule.get().sections())
            .anySatisfy(section -> assertThat(section.key()).isEqualTo("test_cases"));
    }

    @Test
    void formulaTemplateSupportsKnowgen16CalculatorSkeletonWithoutMedicalConstants() {
        Optional<ProfessionalAssetTemplate> formula =
            registry.findByAssetTypeAndDomain(VersionedAssetType.FORMULA, null);

        assertThat(formula).isPresent();
        assertThat(formula.get().professionCode()).isEqualTo("FORMULA");
        assertThat(formula.get().sections())
            .extracting(TemplateSection::key)
            .contains("inputs", "algorithm", "thresholds", "test_vectors", "source");
    }

    @Test
    void listIsImmutable() {
        List<ProfessionalAssetTemplate> all = registry.listAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(null));
    }
}
