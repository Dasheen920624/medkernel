package com.medkernel.engine.domainfacade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * X-DOMAIN 17 张领域门面卡的组合目录测试。
 *
 * <p>本测试只约束「复用哪些既有引擎链路」和服务包聚合关系，不允许在 T7.1 预填真实医学内容。
 */
class DomainFacadeCatalogServiceTest {

    private final DomainFacadeCatalogService service = new DomainFacadeCatalogService();

    @Test
    void listDefinitions_coversAllXDomainCardsWithoutClinicalContentSeeds() {
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
                assertThat(definition.dependencyCards()).isNotEmpty();
            });
    }

    @Test
    void definitions_onlyUseSharedEngineCapabilitiesAndKeepServicePackagesAsAggregation() {
        Set<DomainFacadeEngine> allowedSharedEngines = Set.of(
            DomainFacadeEngine.RULE,
            DomainFacadeEngine.PATHWAY,
            DomainFacadeEngine.KNOWLEDGE,
            DomainFacadeEngine.CDSS,
            DomainFacadeEngine.EMBED,
            DomainFacadeEngine.EVALUATION,
            DomainFacadeEngine.FOLLOWUP,
            DomainFacadeEngine.PACKAGE,
            DomainFacadeEngine.INTEGRATION,
            DomainFacadeEngine.DATA_SERVICE,
            DomainFacadeEngine.SAFETY,
            DomainFacadeEngine.ORGANIZATION,
            DomainFacadeEngine.DOSAGE_CALCULATION,
            DomainFacadeEngine.AUTHORING_TEMPLATE);

        assertThat(service.listDefinitions())
            .allSatisfy(definition -> assertThat(definition.engineChain())
                .allMatch(allowedSharedEngines::contains));

        DomainFacadeDefinition diseasePackage = service.requireDefinition("SVC-DOMAIN-01");
        assertThat(diseasePackage.kind()).isEqualTo(DomainFacadeKind.SERVICE_PACKAGE);
        assertThat(diseasePackage.memberFacadeCodes()).containsExactly(
            "CRITICAL-01",
            "PERIOP-01",
            "ONCO-RENAL-01",
            "SPECIAL-POP-01",
            "TCM-HEALTH-01",
            "PRIMARY-CARE-01",
            "INFECTION-PH-01");
        assertThat(diseasePackage.engineChain()).containsExactly(DomainFacadeEngine.PACKAGE);

        DomainFacadeDefinition collaborationPackage = service.requireDefinition("SVC-DOMAIN-02");
        assertThat(collaborationPackage.kind()).isEqualTo(DomainFacadeKind.SERVICE_PACKAGE);
        assertThat(collaborationPackage.memberFacadeCodes()).containsExactly(
            "NURSING-01",
            "PHARMACY-01",
            "REPORT-01",
            "POC-KNOW-01",
            "ALLIED-CARE-01",
            "RWD-01",
            "REGION-COLLAB-01");
        assertThat(collaborationPackage.engineChain()).containsExactly(DomainFacadeEngine.PACKAGE);
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
