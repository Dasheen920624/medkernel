package com.medkernel.engine.domainfacade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 全医疗领域门面组合目录测试。
 *
 * <p>本测试只约束「复用哪些既有引擎链路」和业务组合聚合关系，不预填虚构医学内容。
 */
class DomainFacadeCatalogServiceTest {

    private final DomainFacadeCatalogService service = new DomainFacadeCatalogService();

    @Test
    void definitionDoesNotExposeDeletedConstructionCardDependencies() {
        assertThat(Stream.of(DomainFacadeDefinition.class.getRecordComponents())
            .map(component -> component.getName()))
            .doesNotContain("dependencyCards");
    }

    @Test
    void listDefinitions_coversAllMedicalDomainsWithoutClinicalContentSeeds() {
        List<DomainFacadeDefinition> definitions = service.listDefinitions();

        assertThat(definitions)
            .extracting(DomainFacadeDefinition::code)
            .containsExactly(
                "NURSING-01",
                "REPORT-01",
                "POC-KNOW-01",
                "PHARMACY-01",
                "CRITICAL-01",
                "SPECIAL-POP-01",
                "PERIOP-01",
                "ONCO-RENAL-01",
                "ALLIED-CARE-01",
                "TCM-HEALTH-01",
                "INFECTION-PH-01",
                "PRIMARY-CARE-01",
                "REGION-COLLAB-01",
                "SPECIALTY-EXT-01",
                "RWD-01",
                "SVC-DOMAIN-01",
                "SVC-DOMAIN-02");
        assertThat(definitions)
            .allSatisfy(definition -> {
                assertThat(definition.b0Ready()).isTrue();
                assertThat(definition.modelEnhancementOptional()).isTrue();
                assertThat(definition.clinicalContentSeeded()).isFalse();
                assertThat(definition.newBusinessEngineRequired()).isFalse();
                assertThat(definition.engineChain()).isNotEmpty();
            });
    }

    @Test
    void definitions_onlyUseSharedEngineCapabilitiesAndKeepServiceCombinationsAsAggregation() {
        Set<DomainFacadeEngine> allowedSharedEngines = Set.of(
            DomainFacadeEngine.RULE,
            DomainFacadeEngine.PATHWAY,
            DomainFacadeEngine.KNOWLEDGE,
            DomainFacadeEngine.CDSS,
            DomainFacadeEngine.EMBED,
            DomainFacadeEngine.EVALUATION,
            DomainFacadeEngine.FOLLOWUP,
            DomainFacadeEngine.RELEASE,
            DomainFacadeEngine.INTEGRATION,
            DomainFacadeEngine.DATA_SERVICE,
            DomainFacadeEngine.SAFETY,
            DomainFacadeEngine.ORGANIZATION,
            DomainFacadeEngine.DOSAGE_CALCULATION,
            DomainFacadeEngine.AUTHORING_TEMPLATE);

        assertThat(service.listDefinitions())
            .allSatisfy(definition -> assertThat(definition.engineChain())
                .allMatch(allowedSharedEngines::contains));

        DomainFacadeDefinition diseaseCombination = service.requireDefinition("SVC-DOMAIN-01");
        assertThat(diseaseCombination.kind()).isEqualTo(DomainFacadeKind.SERVICE_COMBINATION);
        assertThat(diseaseCombination.memberFacadeCodes()).containsExactly(
            "CRITICAL-01",
            "PERIOP-01",
            "ONCO-RENAL-01",
            "SPECIAL-POP-01",
            "TCM-HEALTH-01",
            "PRIMARY-CARE-01",
            "INFECTION-PH-01");
        assertThat(diseaseCombination.engineChain()).containsExactly(DomainFacadeEngine.RELEASE);

        DomainFacadeDefinition collaborationCombination = service.requireDefinition("SVC-DOMAIN-02");
        assertThat(collaborationCombination.kind()).isEqualTo(DomainFacadeKind.SERVICE_COMBINATION);
        assertThat(collaborationCombination.memberFacadeCodes()).containsExactly(
            "NURSING-01",
            "PHARMACY-01",
            "REPORT-01",
            "POC-KNOW-01",
            "ALLIED-CARE-01",
            "RWD-01",
            "REGION-COLLAB-01");
        assertThat(collaborationCombination.engineChain()).containsExactly(DomainFacadeEngine.RELEASE);
    }

    @Test
    void specialtyExtensionDeclaresHonestEmptyWhenAssetsMissing() {
        DomainFacadeDefinition specialty = service.requireDefinition("SPECIALTY-EXT-01");

        assertThat(specialty.honestEmptyWhenAssetsMissing()).isTrue();
        assertThat(specialty.engineChain())
            .contains(DomainFacadeEngine.RULE, DomainFacadeEngine.PATHWAY, DomainFacadeEngine.CDSS,
                DomainFacadeEngine.AUTHORING_TEMPLATE);
    }
}
