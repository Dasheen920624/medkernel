package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:effective-term-mapping-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class EffectiveTermMappingRepositoryIntegrationTest {

    @Autowired
    TermMappingPackageRepository packages;

    @Autowired
    TermMappingPackageItemRepository items;

    @Autowired
    AssetVersionRepository versions;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void resolvesOnlyActivePackageAndPrefersMostSpecificOrganizationScope() {
        EffectiveTermMappingResolver resolver = new EffectiveTermMappingResolver(items);
        Instant now = Instant.parse("2026-06-06T08:00:00Z");
        savePackage("TERM.LAB.HOSPITAL", "1", "HOSPITAL", "hospital-1", "718-7", 201L, now);
        versions.save(version(
            "av-hospital", "TERM.LAB.HOSPITAL", "1", "HOSPITAL:hospital-1",
            AssetVersionStatus.ACTIVE, now
        ));
        savePackage("TERM.LAB.DEPARTMENT", "1", "DEPARTMENT", "department-1", "4548-4", 202L, now);
        AssetVersion departmentVersion = versions.save(version(
            "av-department", "TERM.LAB.DEPARTMENT", "1", "DEPARTMENT:department-1",
            AssetVersionStatus.PUBLISHED, now
        ));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, "department-1", null),
            "doctor-1"
        ));

        assertThat(resolver.resolve("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .extracting(EffectiveTermMapping::standardCode)
            .containsExactly("718-7");

        versions.save(departmentVersion.withStatus(
            AssetVersionStatus.ACTIVE,
            "TERM.LAB.DEPARTMENT|DEPARTMENT:department-1|ALL",
            now.plusSeconds(60),
            "admin-1"
        ));

        assertThat(resolver.resolve("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .extracting(EffectiveTermMapping::standardCode)
            .containsExactly("4548-4");
    }

    private void savePackage(
            String packageCode,
            String packageVersion,
            String scopeLevel,
            String scopeCode,
            String standardCode,
            long standardTermId,
            Instant now) {
        TermMappingPackage pack = packages.save(new TermMappingPackage(
            null,
            "tenant-A",
            packageCode,
            packageVersion,
            packageCode,
            scopeLevel,
            scopeCode,
            TermMappingPackageStatus.PUBLISHED,
            1,
            "a".repeat(64),
            null,
            "admin-1",
            now,
            null,
            now,
            "admin-1",
            now,
            "admin-1"
        ));
        TermMappingSnapshot snapshot = new TermMappingSnapshot(
            standardTermId,
            101L,
            standardTermId,
            "LIS",
            "HB",
            "LOINC",
            standardCode,
            "LAB",
            1.0D,
            "LOW",
            "CONFIRMED",
            "人工确认",
            "admin-1",
            now.toString()
        );
        items.save(TermMappingPackageItem.fromSnapshot(
            "tenant-A",
            pack.id(),
            snapshot.mappingId(),
            snapshot,
            TermMappingSnapshotCodec.write(snapshot),
            now,
            "admin-1"
        ));
    }

    private AssetVersion version(
            String versionId,
            String assetIdentity,
            String versionNo,
            String orgScope,
            AssetVersionStatus status,
            Instant now) {
        String activeScopeKey = status == AssetVersionStatus.ACTIVE
            ? assetIdentity + "|" + orgScope + "|ALL"
            : "version:" + versionId;
        return new AssetVersion(
            null,
            versionId,
            "tenant-A",
            VersionedAssetType.TERMINOLOGY,
            assetIdentity,
            versionNo,
            orgScope,
            "ALL",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            activeScopeKey,
            "term-mapping-package:" + assetIdentity + ":" + versionNo,
            status == AssetVersionStatus.ACTIVE ? now : null,
            null,
            now,
            "admin-1",
            now,
            "admin-1",
            "trace"
        );
    }
}
