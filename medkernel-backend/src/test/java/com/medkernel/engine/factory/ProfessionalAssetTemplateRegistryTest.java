package com.medkernel.engine.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    void coversEveryIndependentlyProducedStructuralAssetType() {
        Set<VersionedAssetType> structuralTypes = registry.listAll().stream()
            .filter(template -> template.knowledgeDomain() == null)
            .map(ProfessionalAssetTemplate::assetType)
            .collect(Collectors.toSet());
        assertThat(structuralTypes).containsExactlyInAnyOrder(
            java.util.Arrays.stream(VersionedAssetType.values())
                .filter(type -> type != VersionedAssetType.KNOWLEDGE)
                .toArray(VersionedAssetType[]::new));
    }

    @Test
    void structuralAssetTypesHaveExactlyOneAuthoringTemplate() {
        for (VersionedAssetType type : VersionedAssetType.values()) {
            if (type == VersionedAssetType.KNOWLEDGE) {
                continue;
            }
            assertThat(registry.listAll().stream()
                    .filter(template -> template.knowledgeDomain() == null)
                    .filter(template -> template.assetType() == type))
                .as("结构资产 %s 只能有一套维护模板，避免编著入口和自动生成规范分裂", type)
                .hasSize(1);
        }
    }

    @Test
    void coversEveryKnowledgeDomainWithAnExplicitDomainTemplate() {
        for (KnowledgeDomain domain : KnowledgeDomain.values()) {
            assertThat(registry.findByAssetTypeAndDomain(VersionedAssetType.KNOWLEDGE, domain))
                .as("knowledge domain %s", domain)
                .isPresent();
        }
    }

    @Test
    void medicalDomainTemplatesAreKnowledgeTypedAndMatchable() {
        Optional<ProfessionalAssetTemplate> nursing =
            registry.findByAssetTypeAndDomain(VersionedAssetType.KNOWLEDGE, KnowledgeDomain.NURSING);
        assertThat(nursing).isPresent();
        assertThat(nursing.get().sections()).anySatisfy(s -> assertThat(s.label()).contains("护理"));
    }

    @Test
    void diagnosticItemTemplateDescribesReusableItemKnowledgeNotPatientInterpretation() {
        ProfessionalAssetTemplate template = registry.findByAssetTypeAndDomain(
                VersionedAssetType.KNOWLEDGE, KnowledgeDomain.DIAGNOSTIC_ITEM)
            .orElseThrow();

        assertThat(template.professionCode()).isEqualTo("DIAGNOSTIC_ITEM");
        assertThat(template.displayName()).isEqualTo("医技项目说明书");
        assertThat(template.sections())
            .extracting(TemplateSection::key)
            .contains("item_definition", "preparation", "reference_basis", "limitations", "clinical_meaning")
            .doesNotContain("patient_result", "patient_diagnosis");
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
