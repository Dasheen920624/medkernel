package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItemRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.testsupport.ClinicalRuntimeReleaseFixture;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(OrgHierarchyRepository.class)
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
    ClinicalRuntimeReleaseItemRepository runtimeItems;

    @Autowired
    ClinicalRuntimeReleaseRepository runtimeReleases;

    @Autowired
    AssetIdentityRepository identities;

    @Autowired
    TermMappingRepository mappings;

    @Autowired
    LocalTermRepository localTerms;

    @Autowired
    StandardTermRepository standardTerms;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OrgHierarchyRepository orgHierarchy;

    @Autowired
    OrgUnitRepository orgUnits;

    private final List<String> releaseIds = new ArrayList<>();

    @AfterEach
    void clearContext() {
        items.deleteAll();
        runtimeItems.deleteAll();
        versions.deleteAll();
        identities.deleteAll();
        mappings.deleteAll();
        localTerms.deleteAll();
        standardTerms.deleteAll();
        releaseIds.forEach(releaseId -> ClinicalRuntimeReleaseFixture.delete(jdbc, releaseId));
        releaseIds.clear();
        jdbc.update("DELETE FROM org_closure");
        jdbc.update("DELETE FROM org_unit");
        RequestContext.clear();
    }

    @Test
    void resolvesOnlyVersionsInLockedRuntimeReleaseAndPrefersMostSpecificScope() {
        EffectiveTermMappingResolver resolver = resolver();
        Instant now = Instant.parse("2026-06-06T08:00:00Z");
        insertOrgTree(now);
        String releaseId = insertRuntimeRelease("release-scope", "hospital-1");
        saveTerminologyVersion(
            releaseId, "av-region", "TERM.LAB.REGION", "V1",
            "/tenant-A/region-1", "718-7", 201L, now);
        saveTerminologyVersion(
            releaseId, "av-facility", "TERM.LAB.FACILITY", "V1",
            "/tenant-A/region-1/hospital-1", "4548-4", 202L, now);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, "department-1", null, null),
            "doctor-1"
        ));

        assertThat(resolver.resolve(
            "tenant-A", releaseId, "LIS", "HB", "LOINC", "LAB"))
            .extracting(EffectiveTermMapping::standardCode)
            .containsExactly("4548-4");
        assertThat(resolver.countByStandardCode(
            "tenant-A", releaseId, "LOINC", "4548-4")).isEqualTo(1);
    }

    @Test
    void resolvesRuntimeTerminologyVersionStoredWithCanonicalOrganizationPath() {
        EffectiveTermMappingResolver resolver = resolver();
        Instant now = Instant.parse("2026-07-07T08:00:00Z");
        insertOrgTree(now);
        String releaseId = insertRuntimeRelease("release-canonical-org-path", "hospital-1");
        saveTerminologyVersion(
            releaseId, "av-canonical-lab", "TERM.LAB.CANONICAL", "V1",
            "/tenant-A/region-1/hospital-1", "718-7", 401L, now);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, null, null, null),
            "operator-1"
        ));

        assertThat(resolver.resolve(
            "tenant-A", releaseId, "LIS", "HB", "LOINC", "LAB"
        )).extracting(EffectiveTermMapping::standardCode).containsExactly("718-7");
    }

    @Test
    void sameAssetIdentityCanResolveDifferentImmutableVersionsAcrossRuntimeReleases() {
        EffectiveTermMappingResolver resolver = resolver();
        Instant now = Instant.parse("2026-06-22T08:00:00Z");
        insertOrgTree(now);
        String firstRelease = insertRuntimeRelease("release-v1", "hospital-1");
        String secondRelease = insertRuntimeRelease("release-v2", "hospital-1");
        saveTerminologyVersion(
            firstRelease, "av-v1", "TERM.LAB", "V1",
            "/tenant-A", "718-7", 301L, now);
        saveTerminologyVersion(
            secondRelease, "av-v2", "TERM.LAB", "V2",
            "/tenant-A", "4548-4", 302L, now.plusSeconds(60));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, null, null, null),
            "operator-1"
        ));

        assertThat(resolver.resolve(
            "tenant-A", firstRelease, "LIS", "HB", "LOINC", "LAB"
        )).extracting(EffectiveTermMapping::standardCode).containsExactly("718-7");
        assertThat(resolver.resolve(
            "tenant-A", secondRelease, "LIS", "HB", "LOINC", "LAB"
        )).extracting(EffectiveTermMapping::standardCode).containsExactly("4548-4");
    }

    @Test
    void rejectsRuntimeReleaseFromAnotherHospitalBeforeResolvingTenantLevelTerminology() {
        EffectiveTermMappingResolver resolver = resolver();
        Instant now = Instant.parse("2026-07-07T09:00:00Z");
        insertOrgTree(now);
        insertOrgUnit("hospital-2", "region-1", "FACILITY", "/tenant-A/region-1/hospital-2", now);
        insertClosure("tenant-root", "hospital-2", 2);
        insertClosure("region-1", "hospital-2", 1);
        insertClosure("hospital-2", "hospital-2", 0);
        String otherHospitalRelease = insertRuntimeRelease("release-other-hospital", "hospital-2");
        saveTerminologyVersion(
            otherHospitalRelease, "av-other-tenant", "TERM.LAB.OTHER", "V1",
            "/tenant-A", "777-7", 501L, now);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, null, null, null),
            "operator-1"
        ));

        assertThatThrownBy(() -> resolver.resolve(
            "tenant-A", otherHospitalRelease, "LIS", "HB", "LOINC", "LAB"
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);
    }

    @Test
    void rejectsOrganizationContextWithoutActiveVersionOwnerScopeBeforeSqlInClause() {
        EffectiveTermMappingResolver resolver = resolver();
        Instant now = Instant.parse("2026-07-07T09:30:00Z");
        insertOrgTree(now);
        String releaseId = insertRuntimeRelease("release-inactive-owner-scope", "hospital-1");
        saveTerminologyVersion(
            releaseId, "av-owner-missing", "TERM.LAB.OWNER.MISSING", "V1",
            "/tenant-A", "777-7", 502L, now);
        jdbc.update("UPDATE org_unit SET status = 'SUSPENDED' WHERE id IN ('tenant-root', 'region-1', 'hospital-1')");
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, "department-1", null, null),
            "operator-1"
        ));

        assertThatThrownBy(() -> resolver.resolve(
            "tenant-A", releaseId, "LIS", "HB", "LOINC", "LAB"
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);
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
        LocalTerm facilityWide = localTerms.save(localTerm("HB-FACILITY", null, now));
        TermMapping firstMapping = mappings.save(mapping(first.id(), standard.id(), now));
        mappings.save(mapping(second.id(), standard.id(), now));
        TermMapping facilityWideMapping = mappings.save(mapping(facilityWide.id(), standard.id(), now));

        assertThat(mappings.findConfirmedByTenantIdAndScope(
            "tenant-A", "FACILITY", "facility-1"
        )).extracting(TermMapping::id)
            .containsExactlyInAnyOrder(firstMapping.id(), facilityWideMapping.id());
        assertThat(mappings.findConfirmedByTenantIdAndScope(
            "tenant-A", "DEPARTMENT", "department-1"
        )).extracting(TermMapping::id).containsExactly(firstMapping.id());
        assertThat(mappings.findConfirmedByTenantIdAndScope(
            "tenant-A", "TENANT", "tenant-A"
        )).hasSize(3);
    }

    private EffectiveTermMappingResolver resolver() {
        return new EffectiveTermMappingResolver(items, orgHierarchy, orgUnits, runtimeReleases);
    }

    private void insertOrgTree(Instant now) {
        insertOrgUnit("tenant-root", null, "TENANT", "/tenant-A", now);
        insertOrgUnit("region-1", "tenant-root", "REGION", "/tenant-A/region-1", now);
        insertOrgUnit("hospital-1", "region-1", "FACILITY", "/tenant-A/region-1/hospital-1", now);
        insertOrgUnit(
            "department-1",
            "hospital-1",
            "DEPARTMENT",
            "/tenant-A/region-1/hospital-1/department-1",
            now
        );
        insertClosure("tenant-root", "tenant-root", 0);
        insertClosure("tenant-root", "region-1", 1);
        insertClosure("tenant-root", "hospital-1", 2);
        insertClosure("tenant-root", "department-1", 3);
        insertClosure("region-1", "region-1", 0);
        insertClosure("region-1", "hospital-1", 1);
        insertClosure("region-1", "department-1", 2);
        insertClosure("hospital-1", "hospital-1", 0);
        insertClosure("hospital-1", "department-1", 1);
        insertClosure("department-1", "department-1", 0);
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

    private void insertClosure(String ancestorId, String descendantId, int depth) {
        jdbc.update(
            "INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth) VALUES (?, ?, ?, ?)",
            "tenant-A", ancestorId, descendantId, depth
        );
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

    private String insertRuntimeRelease(String releaseId, String hospitalId) {
        ClinicalRuntimeReleaseFixture.insert(jdbc, "tenant-A", hospitalId, releaseId);
        releaseIds.add(releaseId);
        return releaseId;
    }

    private void saveTerminologyVersion(
            String releaseId,
            String versionId,
            String assetIdentity,
            String versionNo,
            String organizationScope,
            String standardCode,
            long standardTermId,
            Instant now) {
        identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.TERMINOLOGY, assetIdentity
        ).orElseGet(() -> identities.save(new AssetIdentity(
            null,
            "tenant-A",
            VersionedAssetType.TERMINOLOGY,
            assetIdentity,
            AssetIdentityStatus.ACTIVE,
            Long.parseLong(versionNo.substring(1)),
            now,
            "admin-1",
            now,
            "admin-1",
            "trace"
        )));
        AssetVersion version = versions.save(version(
            versionId, assetIdentity, versionNo, organizationScope,
            AssetVersionStatus.PUBLISHED, now));
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
            version.versionId(),
            snapshot.mappingId(),
            snapshot,
            TermMappingSnapshotCodec.write(snapshot),
            now,
            "admin-1"
        ));
        runtimeItems.save(new ClinicalRuntimeReleaseItem(
            null,
            releaseId,
            "tenant-A",
            ReleaseSourceLayer.HOSPITAL,
            VersionedAssetType.TERMINOLOGY,
            assetIdentity,
            ReleaseEntryState.ACTIVE,
            version.versionId(),
            version.versionNo(),
            version.contentHash(),
            now,
            "admin-1",
            "trace"
        ));
    }

    private AssetVersion version(
            String versionId,
            String assetIdentity,
            String versionNo,
            String orgScope,
            AssetVersionStatus status,
            Instant now) {
        String activeScopeKey = "version:" + versionId;
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
            "terminology:" + assetIdentity + ":" + versionNo,
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
