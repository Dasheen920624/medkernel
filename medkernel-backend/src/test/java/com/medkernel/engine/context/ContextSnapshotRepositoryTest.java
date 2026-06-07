package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.context.canonical.ClinicalSetting;

/**
 * 上下文模块仓储集成测试：H2 + Flyway V1..V7 + Spring Data JDBC。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:context-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ContextSnapshotRepositoryTest {

    @Autowired ContextSnapshotRepository snapshots;
    @Autowired CanonicalResourceRepository resources;
    @Autowired ClinicalEventRepository events;
    @Autowired ContextIdempotencyKeyRepository idem;

    @AfterEach
    void wipe() {
        idem.deleteAll();
        events.deleteAll();
        resources.deleteAll();
        snapshots.deleteAll();
    }

    @Test
    void shouldPersistAndFindSnapshotBySnapshotId() {
        String snapshotId = "ctx-" + UUID.randomUUID();
        ContextSnapshot saved = snapshots.save(newSnapshot(snapshotId, "tenant-A", "MPI-100", "ENC-1",
            ContextSnapshotStatus.ACTIVE, QualityStatus.VALID));
        assertThat(saved.id()).isNotNull();

        Optional<ContextSnapshot> found = snapshots.findBySnapshotIdAndTenantId(snapshotId, "tenant-A");
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ContextSnapshotStatus.ACTIVE);
        assertThat(found.get().qualityStatus()).isEqualTo(QualityStatus.VALID);
    }

    @Test
    void shouldPersistStandardContractSnapshotFields() {
        String snapshotId = "ctx-" + UUID.randomUUID();
        ContextSnapshot saved = snapshots.save(new ContextSnapshot(
            null, snapshotId, "tenant-A", "ORG-1",
            "req-ctx-1", "group-1/hospital-1/campus-1/site-1/DEPT-A/stroke", "pkg-2026.06",
            "MPI-100", "ENC-1",
            ContextSnapshotStatus.ACTIVE, "[]", "{}",
            QualityStatus.VALID, "trace", null, Instant.now(), "tester"
        ));

        Optional<ContextSnapshot> found = snapshots.findBySnapshotIdAndTenantId(saved.snapshotId(), "tenant-A");

        assertThat(found).isPresent();
        assertThat(found.get().requestId()).isEqualTo("req-ctx-1");
        assertThat(found.get().orgPath()).isEqualTo("group-1/hospital-1/campus-1/site-1/DEPT-A/stroke");
        assertThat(found.get().packageVersion()).isEqualTo("pkg-2026.06");
    }

    @Test
    void shouldNotLeakSnapshotAcrossTenants() {
        String snapshotId = "ctx-" + UUID.randomUUID();
        snapshots.save(newSnapshot(snapshotId, "tenant-A", "MPI-1", null,
            ContextSnapshotStatus.ACTIVE, QualityStatus.VALID));

        assertThat(snapshots.findBySnapshotIdAndTenantId(snapshotId, "tenant-B")).isEmpty();
    }

    @Test
    void shouldPageSnapshotsByPatientNewestFirst() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 5; i++) {
            snapshots.save(newSnapshotAt("ctx-p-" + i, "tenant-A", "MPI-200", null,
                ContextSnapshotStatus.ACTIVE, QualityStatus.VALID, base.plusSeconds(i * 60)));
        }

        List<ContextSnapshot> firstPage = snapshots.pageByTenantIdAndPatientIdOrderByCreatedAtDesc(
            "tenant-A", "MPI-200", 0, 3);
        assertThat(firstPage).hasSize(3);
        assertThat(firstPage.get(0).snapshotId()).isEqualTo("ctx-p-4");
        assertThat(firstPage.get(2).snapshotId()).isEqualTo("ctx-p-2");
        assertThat(snapshots.countByTenantIdAndPatientId("tenant-A", "MPI-200")).isEqualTo(5);
    }

    @Test
    void shouldPageSnapshotsByEncounter() {
        Instant base = Instant.parse("2026-02-01T00:00:00Z");
        snapshots.save(newSnapshotAt("ctx-e-1", "tenant-A", "MPI-300", "ENC-Z",
            ContextSnapshotStatus.ACTIVE, QualityStatus.VALID, base));
        snapshots.save(newSnapshotAt("ctx-e-2", "tenant-A", "MPI-300", "ENC-Z",
            ContextSnapshotStatus.SUPERSEDED, QualityStatus.PARTIAL, base.plusSeconds(60)));

        List<ContextSnapshot> rows = snapshots.pageByTenantIdAndEncounterIdOrderByCreatedAtDesc(
            "tenant-A", "ENC-Z", 0, 10);
        assertThat(rows).extracting(ContextSnapshot::snapshotId).containsExactly("ctx-e-2", "ctx-e-1");
        assertThat(snapshots.countByTenantIdAndEncounterId("tenant-A", "ENC-Z")).isEqualTo(2);
    }

    @Test
    void shouldListCanonicalResourcesBySnapshotInSeqOrder() {
        String snapshotId = "ctx-" + UUID.randomUUID();
        snapshots.save(newSnapshot(snapshotId, "tenant-A", "MPI-400", "ENC-A",
            ContextSnapshotStatus.ACTIVE, QualityStatus.VALID));
        resources.save(new CanonicalResource(null, "res-b", snapshotId, "tenant-A",
            CanonicalResourceType.MEDICATION, "{}", "HIS", "REC-2", "v1",
            Instant.now(), Instant.now(), QualityStatus.PARTIAL, 1, null));
        resources.save(new CanonicalResource(null, "res-a", snapshotId, "tenant-A",
            CanonicalResourceType.PATIENT, "{}", "HIS", "REC-1", "v1",
            Instant.now(), Instant.now(), QualityStatus.VALID, 0, null));

        List<CanonicalResource> list = resources.findBySnapshotIdOrderBySeqNoAsc(snapshotId);
        assertThat(list).extracting(CanonicalResource::resourceType)
            .containsExactly(CanonicalResourceType.PATIENT, CanonicalResourceType.MEDICATION);

        List<CanonicalResource> tenantScoped = resources.findBySnapshotIdAndTenantIdOrderBySeqNoAsc(
            snapshotId, "tenant-A");
        assertThat(tenantScoped).extracting(CanonicalResource::resourceType)
            .containsExactly(CanonicalResourceType.PATIENT, CanonicalResourceType.MEDICATION);
    }

    @Test
    void shouldEnforceTenantScopedIdempotencyKeyUniqueness() {
        String key = "idem-" + UUID.randomUUID();
        idem.save(new ContextIdempotencyKey(
            null, "tenant-A", key, "ctx-1", "digest-1",
            Instant.now().plusSeconds(86400), Instant.now()
        ));
        Optional<ContextIdempotencyKey> sameTenant = idem.findByTenantIdAndIdempotencyKey("tenant-A", key);
        assertThat(sameTenant).isPresent();

        idem.save(new ContextIdempotencyKey(
            null, "tenant-B", key, "ctx-2", "digest-2",
            Instant.now().plusSeconds(86400), Instant.now()
        ));
        Optional<ContextIdempotencyKey> otherTenant = idem.findByTenantIdAndIdempotencyKey("tenant-B", key);
        assertThat(otherTenant).isPresent();
        assertThat(otherTenant.get().snapshotId()).isEqualTo("ctx-2");
    }

    @Test
    void shouldFindClinicalEventByEventIdAndTenant() {
        ClinicalEvent saved = events.save(new ClinicalEvent(
            null, "evt-1", "tenant-A", ClinicalEventType.DIAGNOSIS,
            ClinicalEventTriggerPoint.PATIENT_VIEW, "idem-evt-1", null,
            "{\"tenantId\":\"tenant-A\"}",
            "patient-1", "enc-1", ClinicalSetting.INPATIENT,
            "HIS", "kpv-1", "digest-x", Instant.now(), Instant.now(), null,
            ClinicalEventStatus.RECEIVED, null, null, 0, null, "trace-1"));
        assertThat(saved.id()).isNotNull();

        assertThat(events.findByEventIdAndTenantId("evt-1", "tenant-A")).isPresent();
        assertThat(events.findByEventIdAndTenantId("evt-1", "tenant-B")).isEmpty();
    }

    private ContextSnapshot newSnapshot(String snapshotId, String tenantId, String patientId,
                                        String encounterId, ContextSnapshotStatus status,
                                        QualityStatus quality) {
        return newSnapshotAt(snapshotId, tenantId, patientId, encounterId, status, quality, Instant.now());
    }

    private ContextSnapshot newSnapshotAt(String snapshotId, String tenantId, String patientId,
                                          String encounterId, ContextSnapshotStatus status,
                                          QualityStatus quality, Instant createdAt) {
        return new ContextSnapshot(
            null, snapshotId, tenantId, "ORG-1", null, null, "pkg-1", patientId, encounterId,
            status, "[]", "{}",
            quality, "trace", null, createdAt, "tester"
        );
    }
}
