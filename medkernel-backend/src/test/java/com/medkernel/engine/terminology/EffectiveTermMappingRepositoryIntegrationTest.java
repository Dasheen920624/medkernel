package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageAccessPolicy;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
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
    TermMappingSnapshotRepository items;

    @Autowired
    AssetVersionRepository versions;

    @Autowired
    PackageItemRepository packageItems;

    @Autowired
    KnowledgePackageRepository knowledgePackages;

    @Autowired
    TermMappingRepository mappings;

    @Autowired
    LocalTermRepository localTerms;

    @Autowired
    StandardTermRepository standardTerms;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        items.deleteAll();
        packageItems.deleteAll();
        versions.deleteAll();
        knowledgePackages.deleteAll();
        RequestContext.clear();
    }

    @Test
    void resolvesOnlyPublishedPackageAndPrefersMostSpecificOrganizationScope() {
        EffectiveTermMappingResolver resolver = new EffectiveTermMappingResolver(items);
        Instant now = Instant.parse("2026-06-06T08:00:00Z");
        savePackage("TERM.LAB.FACILITY", "1", "FACILITY", "hospital-1", "718-7", 201L, now);
        versions.save(version(
            "av-facility", "TERM.LAB.FACILITY", "1", "FACILITY:hospital-1",
            AssetVersionStatus.PUBLISHED, now
        ));
        savePackage("TERM.LAB.DEPARTMENT", "1", "DEPARTMENT", "department-1", "4548-4", 202L, now);
        AssetVersion departmentVersion = versions.save(version(
            "av-department", "TERM.LAB.DEPARTMENT", "1", "DEPARTMENT:department-1",
            AssetVersionStatus.APPROVED, now
        ));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, "department-1", null),
            "doctor-1"
        ));

        assertThat(resolver.resolve("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .extracting(EffectiveTermMapping::standardCode)
            .containsExactly("718-7");
        assertThat(resolver.countByStandardCode("tenant-A", "LOINC", "718-7")).isEqualTo(1);

        versions.save(departmentVersion.withStatus(
            AssetVersionStatus.PUBLISHED,
            "TERM.LAB.DEPARTMENT|DEPARTMENT:department-1|ALL",
            now.plusSeconds(60),
            "admin-1"
        ));

        assertThat(resolver.resolve("tenant-A", "LIS", "HB", "LOINC", "LAB"))
            .extracting(EffectiveTermMapping::standardCode)
            .containsExactly("4548-4");
    }

    @Test
    void confirmedMappingsRespectOrganizationAncestorScope() {
        Instant now = Instant.parse("2026-06-06T08:00:00Z");
        insertOrgUnit("facility-1", null, "FACILITY", "/facility-1", now);
        insertOrgUnit("facility-2", null, "FACILITY", "/facility-2", now);
        insertOrgUnit("department-1", "facility-1", "DEPARTMENT", "/facility-1/department-1", now);
        insertOrgUnit("department-2", "facility-2", "DEPARTMENT", "/facility-2/department-2", now);
        jdbc.update(
            "INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth) VALUES (?, ?, ?, ?)",
            "tenant-A", "facility-1", "department-1", 1
        );
        jdbc.update(
            "INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth) VALUES (?, ?, ?, ?)",
            "tenant-A", "facility-2", "department-2", 1
        );

        StandardTerm standard = standardTerms.save(new StandardTerm(
            null, "tenant-A", "LOINC", "718-7", TermCategory.LAB,
            "Hemoglobin", "hemoglobin", "2.78", StandardTermStatus.ACTIVE,
            null, "LOINC 2.78", now, "system", now, "system"
        ));
        LocalTerm first = localTerms.save(localTerm("HB-H1", "department-1", now));
        LocalTerm second = localTerms.save(localTerm("HB-H2", "department-2", now));
        TermMapping firstMapping = mappings.save(mapping(first.id(), standard.id(), now));
        mappings.save(mapping(second.id(), standard.id(), now));

        assertThat(mappings.findConfirmedByTenantIdAndScope(
            "tenant-A", "FACILITY", "facility-1"
        )).extracting(TermMapping::id).containsExactly(firstMapping.id());
        assertThat(mappings.findConfirmedByTenantIdAndScope(
            "tenant-A", "TENANT", "tenant-A"
        )).hasSize(2);
    }

    private void insertOrgUnit(
            String id,
            String parentId,
            String level,
            String orgPath,
            Instant now) {
        jdbc.update("""
            INSERT INTO org_unit (
                id, parent_id, tenant_id, org_path, level_code, code, name, facility_type, status,
                created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, 'system', ?, 'system')
            """, id, parentId, "tenant-A", orgPath, level, id, id,
            "FACILITY".equals(level) ? "HOSPITAL" : null, now, now);
    }

    private LocalTerm localTerm(String code, String departmentId, Instant now) {
        return new LocalTerm(
            null, "tenant-A", "LIS", code, TermCategory.LAB,
            code, code.toLowerCase(), departmentId, LocalTermStatus.MAPPED,
            now, now, now, "system", now, "system"
        );
    }

    private TermMapping mapping(Long localTermId, Long standardTermId, Instant now) {
        return new TermMapping(
            null, "tenant-A", localTermId, standardTermId, "LIS", TermCategory.LAB,
            1.0D, TermRiskLevel.LOW, TermMappingStatus.CONFIRMED, "人工确认",
            "reviewer", now, now, "system", now, "system"
        );
    }

    private void savePackage(
            String packageCode,
            String packageVersion,
            String scopeLevel,
            String scopeCode,
            String standardCode,
            long standardTermId,
            Instant now) {
        String packageId = UUID.randomUUID().toString();
        KnowledgePackage pack = knowledgePackages.save(new KnowledgePackage(
            null,
            packageId,
            "tenant-A",
            packageCode,
            packageVersion,
            packageCode,
            "术语映射快照",
            PackageAccessPolicy.OPEN,
            KnowledgePackageStatus.ACTIVE,
            now,
            "admin-1",
            now,
            "admin-1",
            "trace"
        ));
        PackageItem packageItem = packageItems.save(new PackageItem(
            null,
            UUID.randomUUID().toString(),
            "tenant-A",
            pack.packageId(),
            VersionedAssetType.TERMINOLOGY,
            packageCode + "|" + scopeLevel + "|" + scopeCode,
            packageVersion,
            now,
            "admin-1",
            now,
            "admin-1",
            "trace"
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
        items.save(TermMappingSnapshotEntity.fromSnapshot(
            "tenant-A",
            packageItem.itemId(),
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
        String activeScopeKey = status == AssetVersionStatus.PUBLISHED
            ? assetIdentity + "|" + orgScope + "|ALL"
            : "version:" + versionId;
        return new AssetVersion(
            null,
            versionId,
            "tenant-A",
            VersionedAssetType.PACKAGE,
            assetIdentity,
            versionNo,
            orgScope,
            "ALL",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            activeScopeKey,
            "knowledge-package:" + assetIdentity + ":" + versionNo,
            status == AssetVersionStatus.PUBLISHED ? now : null,
            null,
            now,
            "admin-1",
            now,
            "admin-1",
            "trace"
        );
    }
}
