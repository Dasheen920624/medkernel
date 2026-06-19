package com.medkernel.engine.knowledge.production.initialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 初始化发行仓储真实查询测试。 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge-init-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class KnowledgeInitializationRepositoryTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KnowledgeInitializationBatchRepository batches;

    @Autowired
    private KnowledgeInitializationItemRepository items;

    @BeforeEach
    void disableForeignKeysForRepositoryQueryFixture() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    }

    @Test
    void completedHistoryIncludesOnlyCompletedApprovedItemsInNewestFirstOrder() {
        KnowledgeInitializationBatch older = batches.save(batch(
            "foundation-f1-1.0.0",
            "1.0.0",
            KnowledgeInitializationBatchStatus.COMPLETE));
        KnowledgeInitializationItem oldItem = items.save(item(
            older.id(),
            1,
            101L,
            "1.0.0",
            KnowledgeInitializationItemStatus.APPROVED));

        KnowledgeInitializationBatch pending = batches.save(batch(
            "foundation-f1-1.0.1-pending",
            "1.0.1",
            KnowledgeInitializationBatchStatus.IN_REVIEW));
        items.save(item(
            pending.id(),
            1,
            102L,
            "1.0.1",
            KnowledgeInitializationItemStatus.APPROVED));

        KnowledgeInitializationBatch newer = batches.save(batch(
            "foundation-f1-1.0.2",
            "1.0.2",
            KnowledgeInitializationBatchStatus.COMPLETE));
        KnowledgeInitializationItem newItem = items.save(item(
            newer.id(),
            1,
            103L,
            "1.0.2",
            KnowledgeInitializationItemStatus.APPROVED));

        assertThat(items.findCompletedCanonicalIds("t-1"))
            .containsExactly("DATA_ELEMENT.BP");
        assertThat(items.findCompletedHistory("t-1", "DATA_ELEMENT.BP"))
            .extracting(KnowledgeInitializationItem::id)
            .containsExactly(newItem.id(), oldItem.id());
    }

    private KnowledgeInitializationBatch batch(
            String batchCode,
            String releaseVersion,
            KnowledgeInitializationBatchStatus status) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new KnowledgeInitializationBatch(
            null,
            "t-1",
            batchCode,
            InitializationReleaseType.FOUNDATION,
            releaseVersion,
            null,
            InitializationPhase.F1,
            status,
            "a".repeat(64),
            "b".repeat(64),
            "c".repeat(64),
            1,
            1,
            0,
            1,
            0,
            "[]",
            "template-v1",
            null,
            "仓储查询测试",
            "idempotency-" + batchCode,
            null,
            null,
            now,
            "tester",
            now,
            "tester");
    }

    private KnowledgeInitializationItem item(
            Long batchId,
            int sequence,
            Long classificationId,
            String assetVersion,
            KnowledgeInitializationItemStatus status) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new KnowledgeInitializationItem(
            null,
            "t-1",
            batchId,
            sequence,
            "KNOWGEN-26",
            VersionedAssetType.FIELD_CATALOG,
            "DATA_ELEMENT.BP",
            "urn:medkernel:data-element",
            assetVersion,
            901L,
            "d".repeat(64),
            "kv:" + classificationId + ":" + assetVersion,
            classificationId,
            "e".repeat(64),
            KnowledgeRiskLevel.MEDIUM,
            "N",
            "[]",
            "{}",
            InitializationChangeType.NEW,
            null,
            null,
            status,
            now,
            "tester",
            now,
            "tester");
    }
}
