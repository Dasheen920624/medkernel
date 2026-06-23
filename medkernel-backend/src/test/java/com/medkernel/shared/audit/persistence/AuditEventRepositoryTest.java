package com.medkernel.shared.audit.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.audit.AuditEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库层集成测试：H2 in-memory + Flyway V1+V2+V5。
 *
 * <p>覆盖：
 * <ul>
 *   <li>插入 + 按 event_id 反查</li>
 *   <li>租户隔离：t-1 的查询永远看不到 t-2 的事件</li>
 *   <li>链头锁定与推进（{@code initChainHead} 幂等 + {@code lockChainHead} 返回最新签名）</li>
 *   <li>游标分页：id 降序 + cursor 截断 + 过滤组合</li>
 * </ul>
 */
@JdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(AuditEventRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:audit-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class AuditEventRepositoryTest {

    @Autowired
    AuditEventRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void wipe() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM audit_chain_head");
    }

    @Test
    void initChainHeadIsIdempotentPerTenant() {
        repository.initChainHead("t-1");

        org.springframework.dao.DuplicateKeyException thrown = null;
        try {
            repository.initChainHead("t-1");
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            thrown = ex;
        }
        assertThat(thrown).isNotNull();

        Optional<AuditEventRepository.ChainHead> head = repository.lockChainHead("t-1");
        assertThat(head).isPresent();
        assertThat(head.get().tenantId()).isEqualTo("t-1");
        assertThat(head.get().lastSignature()).isEqualTo("GENESIS");
        assertThat(head.get().lastEventId()).isNull();
    }

    @Test
    void insertAndReadByEventIdReturnsAllColumns() {
        repository.initChainHead("t-1");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuditEventRecord record = sample("t-1", "EXPORT", "audit", "snap-1", now,
            null, "GENESIS", "sig-1");
        repository.insertEvent(record);

        Optional<AuditEventRecord> back = repository.findByEventId("t-1", record.eventId());
        assertThat(back).isPresent();
        AuditEventRecord r = back.get();
        assertThat(r.action()).isEqualTo("EXPORT");
        assertThat(r.resourceType()).isEqualTo("audit");
        assertThat(r.tenantId()).isEqualTo("t-1");
        assertThat(r.prevSignature()).isEqualTo("GENESIS");
        assertThat(r.signature()).isEqualTo("sig-1");
        assertThat(r.status()).isEqualTo("SIGNED");
    }

    @Test
    void findByEventIdHonoursTenantBoundary() {
        repository.initChainHead("t-1");
        repository.initChainHead("t-2");
        AuditEventRecord r1 = sample("t-1", "CREATE", "rule", "r-1",
            Instant.now(), null, "GENESIS", "sig-a");
        repository.insertEvent(r1);

        assertThat(repository.findByEventId("t-1", r1.eventId())).isPresent();
        assertThat(repository.findByEventId("t-2", r1.eventId())).isEmpty();
    }

    @Test
    void advanceChainHeadUpdatesLatestSignature() {
        repository.initChainHead("t-1");
        repository.advanceChainHead("t-1", "evt-1", "sig-1");

        AuditEventRepository.ChainHead head = repository.lockChainHead("t-1").orElseThrow();
        assertThat(head.lastEventId()).isEqualTo("evt-1");
        assertThat(head.lastSignature()).isEqualTo("sig-1");
    }

    @Test
    void findPageReturnsRowsDescendingByIdWithCursorTruncation() {
        repository.initChainHead("t-1");
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 0; i < 5; i++) {
            repository.insertEvent(sample("t-1", "CREATE", "rule", "r-" + i,
                base.plusSeconds(i), null, "GENESIS", "sig-" + i));
        }
        AuditEventQuery query = new AuditEventQuery(null, null, null, null, null, null, 3);
        List<AuditEventRecord> page = repository.findPage("t-1", query);

        // we fetch size + 1 = 4 rows
        assertThat(page).hasSize(4);
        assertThat(page.get(0).resourceId()).isEqualTo("r-4");
        assertThat(page.get(1).resourceId()).isEqualTo("r-3");
    }

    @Test
    void findPageAppliesActionAndTenantFiltersAndIsolatesTenants() {
        repository.initChainHead("t-1");
        repository.initChainHead("t-2");

        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.insertEvent(sample("t-1", "CREATE", "rule", "r-1",
            base, null, "GENESIS", "sig-1"));
        repository.insertEvent(sample("t-1", "PUBLISH", "rule", "r-2",
            base.plusSeconds(1), null, "GENESIS", "sig-2"));
        repository.insertEvent(sample("t-2", "CREATE", "rule", "r-3",
            base.plusSeconds(2), null, "GENESIS", "sig-3"));

        AuditEventQuery publishOnly = new AuditEventQuery(
            "PUBLISH", null, null, null, null, null, 50);
        List<AuditEventRecord> t1Publishes = repository.findPage("t-1", publishOnly);
        assertThat(t1Publishes).hasSize(1);
        assertThat(t1Publishes.get(0).resourceId()).isEqualTo("r-2");

        AuditEventQuery allInTenant = new AuditEventQuery(
            null, null, null, null, null, null, 50);
        List<AuditEventRecord> t2Rows = repository.findPage("t-2", allInTenant);
        assertThat(t2Rows).hasSize(1);
        assertThat(t2Rows.get(0).resourceId()).isEqualTo("r-3");
    }

    @Test
    void findPageAppliesTraceIdFilterWithinTenant() {
        repository.initChainHead("t-1");
        repository.initChainHead("t-2");

        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.insertEvent(sample("t-1", "CREATE", "rule", "r-1",
            base, null, "GENESIS", "sig-1"));
        repository.insertEvent(sample("t-1", "PUBLISH", "rule", "r-2",
            base.plusSeconds(1), null, "GENESIS", "sig-2"));
        repository.insertEvent(sample("t-2", "PUBLISH", "rule", "r-2",
            base.plusSeconds(2), null, "GENESIS", "sig-3"));

        AuditEventQuery traceOnly = new AuditEventQuery(
            null,
            null,
            null,
            "trace-r-2",
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            50);

        List<AuditEventRecord> rows = repository.findPage("t-1", traceOnly);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).tenantId()).isEqualTo("t-1");
        assertThat(rows.get(0).resourceId()).isEqualTo("r-2");
        assertThat(rows.get(0).traceId()).isEqualTo("trace-r-2");
    }

    @Test
    void findPageAppliesOrgEnvironmentOutcomeAndSuperAdminFilters() {
        repository.initChainHead("t-1");
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        repository.insertEvent(sampleWithSpine("t-1", "UPDATE", "audit_config", "cfg-1",
            base, "ROLE_SYSTEM_SUPERADMIN", "tenant:t-1/hospital:h-1", "prod", "FAILED", "sig-a"));
        repository.insertEvent(sampleWithSpine("t-1", "UPDATE", "audit_config", "cfg-2",
            base.plusSeconds(1), "ROLE_PLATFORM_ADMIN", "tenant:t-1/hospital:h-2", "prod", "SUCCESS", "sig-b"));
        repository.insertEvent(sampleWithSpine("t-1", "UPDATE", "audit_config", "cfg-3",
            base.plusSeconds(2), "ROLE_SYSTEM_SUPERADMIN", "tenant:t-1/hospital:h-1", "test", "FAILED", "sig-c"));
        repository.insertEvent(sampleWithSpine("t-1", "UPDATE", "audit_config", "cfg-4",
            base.plusSeconds(3), "ROLE_SUPERADMIN_READONLY", "tenant:t-1/hospital:h-1", "prod", "FAILED", "sig-d"));
        repository.insertEvent(sampleWithSpine("t-1", "UPDATE", "audit_config", "cfg-5",
            base.plusSeconds(4), "ROLE_SYSTEM_SUPERADMIN", "tenant:t-1/hospital:h-10", "prod", "FAILED", "sig-e"));

        AuditEventQuery query = new AuditEventQuery(
            "UPDATE",
            "audit_config",
            null,
            "tenant:t-1/hospital:h-1",
            "prod",
            "FAILED",
            true,
            null,
            null,
            null,
            50);

        List<AuditEventRecord> rows = repository.findPage("t-1", query);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).resourceId()).isEqualTo("cfg-1");
        assertThat(rows.get(0).actorRoles()).contains("ROLE_SYSTEM_SUPERADMIN");
        assertThat(rows.get(0).environmentKey()).isEqualTo("prod");
    }

    @Test
    void findPageWithCursorReturnsRowsStrictlyBeforeCursor() {
        repository.initChainHead("t-1");
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 0; i < 5; i++) {
            repository.insertEvent(sample("t-1", "CREATE", "rule", "r-" + i,
                base.plusSeconds(i), null, "GENESIS", "sig-" + i));
        }
        List<AuditEventRecord> all = repository.findPage("t-1",
            new AuditEventQuery(null, null, null, null, null, null, 50));
        Long mid = all.get(2).id();

        List<AuditEventRecord> before = repository.findPage("t-1",
            new AuditEventQuery(null, null, null, null, null, mid, 50));
        assertThat(before).hasSize(2); // strictly less-than mid
        assertThat(before).allMatch(r -> r.id() < mid);
    }

    private static AuditEventRecord sample(String tenantId,
                                           String action,
                                           String resourceType,
                                           String resourceId,
                                           Instant occurredAt,
                                           String prevEventId,
                                           String prevSignature,
                                           String signature) {
        return new AuditEventRecord(
            null,
            UUID.randomUUID().toString(),
            "trace-" + resourceId,
            occurredAt,
            "user-1",
            action,
            resourceType,
            resourceId,
            "summary " + resourceId,
            "digest-" + resourceId,
            tenantId,
            null,
            null,
            prevEventId,
            prevSignature,
            signature,
            "SIGNED",
            "SUCCESS",
            null,
            null
        );
    }

    private static AuditEventRecord sampleWithSpine(String tenantId,
                                                    String action,
                                                    String resourceType,
                                                    String resourceId,
                                                    Instant occurredAt,
                                                    String actorRoles,
                                                    String orgPath,
                                                    String environmentKey,
                                                    String outcome,
                                                    String signature) {
        return new AuditEventRecord(
            null,
            UUID.randomUUID().toString(),
            "trace-" + resourceId,
            occurredAt,
            "user-1",
            action,
            resourceType,
            resourceId,
            "summary " + resourceId,
            "digest-" + resourceId,
            tenantId,
            "h-1",
            null,
            null,
            "GENESIS",
            signature,
            "SIGNED",
            outcome,
            AuditEvent.OUTCOME_FAILED.equals(outcome) ? "ENG-AUDIT-001" : null,
            null,
            actorRoles,
            orgPath,
            environmentKey,
            "{\"enabled\":true}",
            "{\"enabled\":false}",
            "dedupe-" + resourceId
        );
    }
}
