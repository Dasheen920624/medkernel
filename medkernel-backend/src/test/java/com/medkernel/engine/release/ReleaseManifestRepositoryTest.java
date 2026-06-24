package com.medkernel.engine.release;

import java.time.Instant;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItemRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * 平台标准版本与机构生效版本物化清单仓储测试。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:release-manifest-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ReleaseManifestRepositoryTest {

    @Autowired PlatformBaselineReleaseRepository baselineReleases;
    @Autowired PlatformBaselineItemRepository baselineItems;
    @Autowired ClinicalRuntimeReleaseRepository runtimeReleases;
    @Autowired ClinicalRuntimeReleaseItemRepository runtimeItems;
    @Autowired AssetIdentityRepository identities;
    @Autowired AssetVersionRepository versions;

    @AfterEach
    void wipe() {
        runtimeItems.deleteAll();
        runtimeReleases.deleteAll();
        baselineItems.deleteAll();
        baselineReleases.deleteAll();
        versions.deleteAll();
        identities.deleteAll();
    }

    @Test
    void persistsExactVersionsInPlatformAndHospitalManifests() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        saveIdentity(VersionedAssetType.RULE, "RULE.RENAL.DOSE", now);
        saveVersion("version-rule-1", "1", "b".repeat(64), now);
        baselineReleases.save(new PlatformBaselineRelease(
            null, "baseline-A1", 1L, "a".repeat(64),
            now, "operator-1", now, "operator-1", "trace-1"));
        saveRuntimeParent(now);
        baselineItems.save(new PlatformBaselineItem(
            null, "baseline-A1", "platform", VersionedAssetType.RULE, "RULE.RENAL.DOSE",
            ReleaseEntryState.ACTIVE, "version-rule-1", "1", "b".repeat(64),
            now, "operator-1", "trace-1"));
        runtimeItems.save(new ClinicalRuntimeReleaseItem(
            null, "runtime-H1", "platform", ReleaseSourceLayer.PLATFORM,
            VersionedAssetType.RULE, "RULE.RENAL.DOSE", ReleaseEntryState.ACTIVE,
            "version-rule-1", "1",
            "b".repeat(64), now, "operator-1", "trace-1"));

        assertThat(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc("baseline-A1"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.versionId()).isEqualTo("version-rule-1");
                assertThat(item.contentHash()).isEqualTo("b".repeat(64));
            });
        assertThat(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc("runtime-H1"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.sourceLayer()).isEqualTo(ReleaseSourceLayer.PLATFORM);
                assertThat(item.versionId()).isEqualTo("version-rule-1");
            });
    }

    @Test
    void rejectsTwoVersionsOfSameStableIdentityInOnePlatformBaseline() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        saveIdentity(VersionedAssetType.RULE, "RULE.RENAL.DOSE", now);
        saveVersion("version-rule-1", "1", "b".repeat(64), now);
        saveVersion("version-rule-2", "2", "c".repeat(64), now);
        baselineReleases.save(new PlatformBaselineRelease(
            null, "baseline-A1", 1L, "a".repeat(64),
            now, "operator-1", now, "operator-1", "trace-1"));
        baselineItems.save(new PlatformBaselineItem(
            null, "baseline-A1", "platform", VersionedAssetType.RULE, "RULE.RENAL.DOSE",
            ReleaseEntryState.ACTIVE, "version-rule-1", "1", "b".repeat(64),
            now, "operator-1", "trace-1"));

        assertThatThrownBy(() -> baselineItems.save(new PlatformBaselineItem(
            null, "baseline-A1", "platform", VersionedAssetType.RULE, "RULE.RENAL.DOSE",
            ReleaseEntryState.ACTIVE, "version-rule-2", "2", "c".repeat(64),
            now, "operator-1", "trace-2")))
            .isInstanceOf(DbActionExecutionException.class)
            .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsDisabledTombstoneWithoutPretendingToOwnAContentVersion() {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        saveIdentity(VersionedAssetType.PATHWAY, "PATH.COPD", now);
        baselineReleases.save(new PlatformBaselineRelease(
            null, "baseline-A1", 1L, "a".repeat(64),
            now, "operator-1", now, "operator-1", "trace-1"));

        baselineItems.save(new PlatformBaselineItem(
            null, "baseline-A1", "platform", VersionedAssetType.PATHWAY, "PATH.COPD",
            ReleaseEntryState.DISABLED, null, null, null,
            now, "operator-1", "trace-1"));

        assertThat(baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            "baseline-A1"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.entryState()).isEqualTo(ReleaseEntryState.DISABLED);
                assertThat(item.versionId()).isNull();
                assertThat(item.contentHash()).isNull();
            });
    }

    private void saveVersion(String versionId, String versionNo, String hash, Instant now) {
        versions.save(new AssetVersion(
            null,
            versionId,
            "platform",
            VersionedAssetType.RULE,
            "RULE.RENAL.DOSE",
            versionNo,
            "/",
            "ALL",
            hash,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            "RULE.RENAL.DOSE|/|ALL|" + versionNo,
            "test",
            now,
            null,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        ));
    }

    private void saveIdentity(
            VersionedAssetType assetType,
            String assetIdentity,
            Instant now) {
        identities.save(new AssetIdentity(
            null,
            "platform",
            assetType,
            assetIdentity,
            AssetIdentityStatus.ACTIVE,
            1L,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        ));
    }

    private void saveRuntimeParent(Instant now) {
        runtimeReleases.save(new ClinicalRuntimeRelease(
            null,
            "runtime-H1",
            "tenant-A",
            "hospital-1",
            1L,
            "baseline-A1",
            "d".repeat(64),
            null,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1"
        ));
    }
}
