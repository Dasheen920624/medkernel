package com.medkernel.engine.domainfacade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * X-DOMAIN 领域门面 B0 fixture 证据测试。
 *
 * <p>本测试只证明门面组合复用已有共享引擎链路，且关模型、缺真实资产时不预填医学内容。
 */
class DomainFacadeB0FixtureServiceTest {

    private final DomainFacadeB0FixtureService service =
        new DomainFacadeB0FixtureService(new DomainFacadeCatalogService());

    @Test
    void listFixtureEvidence_coversAllFacadesWithDeterministicB0Handlers() {
        List<DomainFacadeB0FixtureEvidence> evidence = service.listFixtureEvidence();

        assertThat(evidence)
            .extracting(DomainFacadeB0FixtureEvidence::code)
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
        assertThat(evidence)
            .allSatisfy(item -> {
                assertThat(item.status()).isEqualTo(DomainFacadeB0FixtureStatus.PASS);
                assertThat(item.b0Executable()).isTrue();
                assertThat(item.modelRequired()).isFalse();
                assertThat(item.clinicalContentSeeded()).isFalse();
                assertThat(item.newBusinessEngineRequired()).isFalse();
                assertThat(item.servicePackageMembersResolvable()).isTrue();
                assertThat(item.fixtureId()).startsWith("DOMAIN-B0-");
                assertThat(item.engineFixtures()).isNotEmpty();
                assertThat(item.engineFixtures()).allSatisfy(engine -> {
                    assertThat(engine.deterministic()).isTrue();
                    assertThat(engine.handlerPresent()).isTrue();
                    assertThat(engine.clinicalContentSeeded()).isFalse();
                    assertThat(engine.sharedHandlerClass()).startsWith("com.medkernel.engine.");
                    assertThat(engine.b0Route()).startsWith("/api/v1/");
                });
            });
    }

    @Test
    void servicePackagesResolveOnlyDeclaredMemberFacades() {
        DomainFacadeB0FixtureEvidence diseasePackage = service.requireFixtureEvidence("SVC-DOMAIN-01");
        DomainFacadeB0FixtureEvidence collaborationPackage = service.requireFixtureEvidence("SVC-DOMAIN-02");

        assertThat(diseasePackage.kind()).isEqualTo(DomainFacadeKind.SERVICE_PACKAGE);
        assertThat(diseasePackage.verifiedMemberFacadeCodes()).containsExactly(
            "CRITICAL-01",
            "PERIOP-01",
            "ONCO-RENAL-01",
            "SPECIAL-POP-01",
            "TCM-HEALTH-01",
            "PRIMARY-CARE-01",
            "INFECTION-PH-01");
        assertThat(collaborationPackage.verifiedMemberFacadeCodes()).containsExactly(
            "NURSING-01",
            "PHARMACY-01",
            "REPORT-01",
            "POC-KNOW-01",
            "ALLIED-CARE-01",
            "RWD-01",
            "REGION-COLLAB-01");
    }

    @Test
    void specialtyExtensionUsesHonestEmptyFixtureUntilRealAssetsExist() {
        DomainFacadeB0FixtureEvidence specialty = service.requireFixtureEvidence("SPECIALTY-EXT-01");

        assertThat(specialty.honestEmptyWhenAssetsMissing()).isTrue();
        assertThat(specialty.assetSeedPolicy()).isEqualTo("NO_SEED_HONEST_EMPTY");
        assertThat(specialty.status()).isEqualTo(DomainFacadeB0FixtureStatus.PASS);
    }
}
