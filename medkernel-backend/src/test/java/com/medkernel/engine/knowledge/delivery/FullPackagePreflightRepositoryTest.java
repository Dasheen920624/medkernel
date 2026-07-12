package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.release.PlatformBaselineRelease;
import com.medkernel.engine.release.PlatformBaselineReleaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

/** 真实 H2 V1 验证预检事实可回读，并允许同一包绑定不同医院状态快照。 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:full-package-preflight-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class FullPackagePreflightRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    @Autowired
    FullPackagePreflightRepository preflights;
    @Autowired
    FullPackageActivationRepository activations;
    @Autowired
    PlatformBaselineReleaseRepository baselines;
    @Autowired
    ClinicalRuntimeReleaseRepository runtimes;

    @Test
    void persistsAndReadsManifestBoundImmutablePreview() {
        FullPackagePreflight saved = preflights.save(fact(
            "preflight-0001", "delivery-0001", 1, "a", "e"));

        assertThat(saved.id()).isNotNull();
        assertThat(preflights.findByTenantIdAndHospitalIdAndPreflightId(
            "tenant-A", "hospital-A", "preflight-0001"))
            .isPresent()
            .get()
            .satisfies(result -> {
                assertThat(result.status()).isEqualTo(FullPackagePreflightStatus.PASSED);
                assertThat(result.manifestDigest()).isEqualTo("sm3:" + "a".repeat(64));
                assertThat(result.previewJson()).isEqualTo("{\"runtimeMutation\":false}");
                assertThat(result.lockVersion()).isZero();
            });
        assertThat(preflights.findByTenantIdAndHospitalIdAndPreflightIdForUpdate(
            "tenant-A", "hospital-A", "preflight-0001")).isPresent();
    }

    @Test
    void persistsOneActivationLedgerForTheLockedPreflightAndRuntimeRevision() {
        FullPackagePreflight preflight = preflights.save(fact(
            "preflight-activation", "delivery-activation", 1, "a", "e"));
        baselines.save(new PlatformBaselineRelease(
            null,
            preflight.platformReleaseIdentity(),
            1L,
            "f".repeat(64),
            NOW,
            "operator",
            NOW,
            "operator",
            "trace-baseline"));
        ClinicalRuntimeRelease runtime = runtimes.save(new ClinicalRuntimeRelease(
            null,
            "runtime-activation",
            "tenant-A",
            "hospital-A",
            1L,
            preflight.platformReleaseIdentity(),
            "f".repeat(64),
            null,
            NOW,
            "operator",
            NOW,
            "operator",
            "trace-runtime"));

        FullPackageActivation saved = activations.save(new FullPackageActivation(
            null,
            "activation-0001",
            preflight.preflightId(),
            preflight.tenantId(),
            preflight.hospitalId(),
            preflight.authorityId(),
            preflight.deliveryId(),
            preflight.previewDigest(),
            null,
            runtime.releaseId(),
            runtime.revisionNo(),
            runtime.platformBaselineReleaseId(),
            NOW,
            "operator",
            NOW,
            "operator",
            NOW,
            "operator",
            "trace-activation"));

        assertThat(saved.id()).isNotNull();
        assertThat(activations.findByTenantIdAndHospitalIdAndPreflightId(
            "tenant-A", "hospital-A", preflight.preflightId()))
            .contains(saved);
    }

    @Test
    void allowsSamePackageToBindDifferentImmutableHospitalSnapshots() {
        preflights.save(fact("preflight-0001", "delivery-0001", 1, "a", "e"));

        FullPackagePreflight second = preflights.save(
            fact("preflight-0002", "delivery-0001", 1, "a", "f"));

        assertThat(second.id()).isNotNull();
        assertThat(second.previewDigest()).isEqualTo("sm3:" + "f".repeat(64));
    }

    @Test
    void rejectsDuplicateStablePreflightIdentity() {
        preflights.save(fact("preflight-0001", "delivery-0001", 1, "a", "e"));

        assertThatThrownBy(() -> preflights.save(
            fact("preflight-0001", "delivery-0001", 1, "a", "f")))
            .isInstanceOf(DbActionExecutionException.class)
            .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    private FullPackagePreflight fact(
            String preflightId,
            String deliveryId,
            long releaseSequence,
            String digestCharacter,
            String previewDigestCharacter) {
        String digest = "sm3:" + digestCharacter.repeat(64);
        return new FullPackagePreflight(
            null,
            preflightId,
            "tenant-A",
            "hospital-A",
            "mka-medkernel-cn-01",
            deliveryId,
            releaseSequence,
            digest,
            "baseline-release-0001",
            "sm3:" + "c".repeat(64),
            1024,
            "objects/cc/" + "c".repeat(64) + ".mkp",
            "issuer-platform-134",
            "key-platform-134",
            "sm3:" + "d".repeat(64),
            FullPackagePreflightStatus.PASSED,
            "sm3:" + previewDigestCharacter.repeat(64),
            "{\"runtimeMutation\":false}",
            null,
            NOW,
            "operator",
            NOW,
            "operator",
            "trace-preflight");
    }
}
