package com.medkernel.engine.integration.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:master-data-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class MasterDataSyncRepositoryTest {

    @Autowired
    private MasterDataSyncBatchRepository batches;

    @Autowired
    private MasterDataSyncRecordRepository records;

    @AfterEach
    void wipe() {
        records.deleteAll();
        batches.deleteAll();
    }

    @Test
    void persistsLatestCursorAndTenantIsolatedReconciliationCounts() {
        Instant first = Instant.parse("2026-06-13T01:00:00Z");
        Instant second = Instant.parse("2026-06-13T02:00:00Z");
        batches.save(batch("tenant-1", "batch-1", "cursor-1", first));
        batches.save(batch("tenant-1", "batch-2", "cursor-2", second));
        batches.save(batch("tenant-2", "batch-other", "cursor-other", second.plusSeconds(60)));

        records.save(record(
            "tenant-1", "org-1", "internal-org-1",
            MasterDataResourceType.ORG_UNIT, MasterDataRecordStatus.ACTIVE));
        records.save(record(
            "tenant-1", "person-1", "internal-person-1",
            MasterDataResourceType.PERSON, MasterDataRecordStatus.DISABLED));
        records.save(record(
            "tenant-2", "org-2", "internal-org-2",
            MasterDataResourceType.ORG_UNIT, MasterDataRecordStatus.ACTIVE));

        assertThat(batches.findLatestSuccessful("tenant-1", "HIS"))
            .hasValueSatisfying(latest -> {
                assertThat(latest.batchId()).isEqualTo("batch-2");
                assertThat(latest.cursor()).isEqualTo("cursor-2");
            });
        assertThat(records.countByStatus(
            "tenant-1", "HIS", "ORG_UNIT", "ACTIVE")).isEqualTo(1);
        assertThat(records.countByStatus(
            "tenant-1", "HIS", "PERSON", "DISABLED")).isEqualTo(1);
        assertThat(records.findByTenantIdAndSourceSystemAndResourceTypeAndStatus(
            "tenant-1", "HIS", MasterDataResourceType.ORG_UNIT,
            MasterDataRecordStatus.ACTIVE))
            .extracting(MasterDataSyncRecord::sourceRecordId)
            .containsExactly("org-1");
    }

    private MasterDataSyncBatch batch(
            String tenantId,
            String batchId,
            String cursor,
            Instant processedAt) {
        return new MasterDataSyncBatch(
            null, batchId, tenantId, "his-master-data", "his-adapter", "HIS",
            MasterDataSyncMode.INCREMENTAL, null, cursor, "a".repeat(64),
            MasterDataSyncStatus.SUCCESS, 1, 1, 0, null,
            processedAt.minusSeconds(1), processedAt, "trace-" + batchId);
    }

    private MasterDataSyncRecord record(
            String tenantId,
            String sourceRecordId,
            String internalId,
            MasterDataResourceType resourceType,
            MasterDataRecordStatus status) {
        Instant now = Instant.parse("2026-06-13T02:00:00Z");
        return new MasterDataSyncRecord(
            null, tenantId, "HIS", resourceType, sourceRecordId, internalId,
            1L, "b".repeat(64), status, "batch-2", now, now);
    }
}
