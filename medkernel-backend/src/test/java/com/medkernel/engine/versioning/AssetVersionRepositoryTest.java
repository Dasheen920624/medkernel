package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.ids.Ulid;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:asset-version-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class AssetVersionRepositoryTest {

    @Autowired AssetVersionRepository repository;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    @Test
    void persistsAssetVersionAndFindsActiveVersionByEffectiveDomain() {
        AssetVersion saved = repository.save(sample(
            newVersionId(),
            "1.0.0",
            AssetVersionStatus.PUBLISHED,
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient"
        ));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.NORMAL);
        assertThat(repository.findByVersionIdAndTenantId(saved.versionId(), "tenant-A")).contains(saved);
        assertThat(repository.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient",
            AssetVersionStatus.PUBLISHED
        )).containsExactly(saved);
    }

    @Test
    void databaseRejectsSecondActiveVersionInSameEffectiveDomain() {
        String activeScopeKey = "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient";
        repository.save(sample(newVersionId(), "1.0.0", AssetVersionStatus.PUBLISHED, activeScopeKey));

        assertThatThrownBy(() ->
            repository.save(sample(newVersionId(), "1.0.1", AssetVersionStatus.PUBLISHED, activeScopeKey))
        )
            .isInstanceOf(DbActionExecutionException.class)
            .hasRootCauseInstanceOf(JdbcSQLIntegrityConstraintViolationException.class);
    }

    @Test
    void batchIdentityQueryNeverReturnsAnotherTenantRows() {
        AssetVersion tenantA = repository.save(sample(
            "tenant-A",
            newVersionId(),
            "1.0.0",
            AssetVersionStatus.PUBLISHED,
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient"));
        repository.save(sample(
            "tenant-B",
            newVersionId(),
            "1.0.0",
            AssetVersionStatus.PUBLISHED,
            "RULE.VTE.RISK|/GROUP/g-2/HOSPITAL/h-2|adult|inpatient"));

        assertThat(repository.findByTenantIdAndAssetIdentityInAndStatusIn(
            "tenant-A",
            List.of("RULE.VTE.RISK"),
            List.of(AssetVersionStatus.PUBLISHED)))
            .containsExactly(tenantA);
    }

    @Test
    void locksExactTenantVersionRowBeforeReleaseValidation() {
        AssetVersion saved = repository.save(sample(
            newVersionId(),
            "1.0.0",
            AssetVersionStatus.PUBLISHED,
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient"
        ));

        assertThat(repository.lockByVersionIdAndTenantId(
            saved.versionId(), "tenant-A"))
            .containsExactly(saved.id());
        assertThat(repository.lockByVersionIdAndTenantId(
            saved.versionId(), "tenant-B"))
            .isEmpty();
    }

    @Test
    void platformReleaseCandidatesIncludeDraftAndPublishedButExcludeWithdrawn() {
        AssetVersion draft = repository.save(platformSample(
            newVersionId(),
            VersionedAssetType.RULE,
            "RULE.CKD",
            "V2",
            AssetVersionStatus.DRAFT));
        AssetVersion published = repository.save(platformSample(
            newVersionId(),
            VersionedAssetType.KNOWLEDGE,
            "KNOW.REPORT.LAB",
            "V1",
            AssetVersionStatus.PUBLISHED));
        repository.save(platformSample(
            newVersionId(),
            VersionedAssetType.PATHWAY,
            "PATH.OLD",
            "V1",
            AssetVersionStatus.WITHDRAWN));

        assertThat(repository.pagePlatformReleaseCandidates(
            PlatformTenant.ID, null, null, 0, 20))
            .extracting(AssetVersion::versionId)
            .containsExactlyInAnyOrder(draft.versionId(), published.versionId());
        assertThat(repository.countPlatformReleaseCandidates(
            PlatformTenant.ID, null, null))
            .isEqualTo(2L);
    }

    private AssetVersion sample(
            String versionId,
            String versionNo,
            AssetVersionStatus status,
            String activeScopeKey) {
        return sample("tenant-A", versionId, versionNo, status, activeScopeKey);
    }

    private AssetVersion sample(
            String tenantId,
            String versionId,
            String versionNo,
            AssetVersionStatus status,
            String activeScopeKey) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        return new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            activeScopeKey,
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "reviewer-1",
            now,
            "reviewer-1",
            "trace-sys04"
        );
    }

    private AssetVersion platformSample(
            String versionId,
            VersionedAssetType assetType,
            String assetIdentity,
            String versionNo,
            AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        String activeScopeKey = "version:" + versionId;
        return new AssetVersion(
            null,
            versionId,
            PlatformTenant.ID,
            assetType,
            assetIdentity,
            versionNo,
            "/platform",
            "ALL",
            "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            activeScopeKey,
            "platform/" + assetIdentity,
            null,
            null,
            now,
            "engine-operator",
            now,
            "engine-operator",
            "trace-platform"
        );
    }

    private String newVersionId() {
        return "av-" + Ulid.newUlid();
    }
}
