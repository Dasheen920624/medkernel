package com.medkernel.shared.audit.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 哈希链写入器集成测试。
 *
 * <p>验证：
 * <ul>
 *   <li>首条事件 {@code prev_signature = "GENESIS"}，{@code prev_event_id = null}</li>
 *   <li>后续事件链接到上一条的 event_id / signature</li>
 *   <li>缺租户上下文落到 {@code __SYSTEM__} 合成租户的独立链</li>
 *   <li>验签：未篡改 → true；篡改 action 字段 → false</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:audit-chain-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class AuditChainWriterTest {

    @Autowired
    AuditChainWriter writer;

    @Autowired
    AuditEventRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM audit_chain_head");
        RequestContext.clear();
    }

    @AfterEach
    void teardown() {
        RequestContext.clear();
    }

    @Test
    void firstEventInTenantChainAnchorsOnGenesis() {
        withTenant("t-1", () -> {
            AuditEvent event = AuditEvent.of(AuditAction.CREATE, "rule", "r-1", "新建规则 r-1");
            AuditEventRecord written = writer.persist(event);

            assertThat(written.tenantId()).isEqualTo("t-1");
            assertThat(written.prevEventId()).isNull();
            assertThat(written.prevSignature()).isEqualTo("GENESIS");
            assertThat(written.signature()).isNotBlank();
            assertThat(written.status()).isEqualTo("SIGNED");
            assertThat(writer.verify(written)).isTrue();
        });
    }

    @Test
    void secondEventLinksToFirst() {
        withTenant("t-1", () -> {
            AuditEvent first = AuditEvent.of(AuditAction.CREATE, "rule", "r-1", "a");
            AuditEventRecord r1 = writer.persist(first);

            // ensure occurredAt strictly increases so signature differs
            sleep(2);
            AuditEvent second = AuditEvent.of(AuditAction.PUBLISH, "rule", "r-1", "publish");
            AuditEventRecord r2 = writer.persist(second);

            assertThat(r2.prevEventId()).isEqualTo(r1.eventId());
            assertThat(r2.prevSignature()).isEqualTo(r1.signature());
            assertThat(r2.signature()).isNotEqualTo(r1.signature());
            assertThat(writer.verify(r2)).isTrue();
        });
    }

    @Test
    void sameTraceActionAndTargetIsIdempotentAndDoesNotAdvanceChain() {
        withTenant("t-1", () -> {
            AuditEventRecord r1 = writer.persist(
                AuditEvent.of(AuditAction.UPDATE, "rule", "r-1", "第一次记录"));
            AuditEventRecord replay = writer.persist(
                AuditEvent.of(AuditAction.UPDATE, "rule", "r-1", "重复提交"));

            assertThat(replay.eventId()).isEqualTo(r1.eventId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class)).isEqualTo(1L);

            AuditEventRepository.ChainHead head = repository.lockChainHead("t-1").orElseThrow();
            assertThat(head.lastEventId()).isEqualTo(r1.eventId());
            assertThat(head.lastSignature()).isEqualTo(r1.signature());
        });
    }

    @Test
    void differentTenantsHaveIndependentChains() {
        withTenant("t-1", () -> {
            AuditEventRecord r1 = writer.persist(
                AuditEvent.of(AuditAction.CREATE, "rule", "r-1", "t1 first"));
            assertThat(r1.prevSignature()).isEqualTo("GENESIS");
        });
        withTenant("t-2", () -> {
            AuditEventRecord r2 = writer.persist(
                AuditEvent.of(AuditAction.CREATE, "rule", "r-1", "t2 first"));
            assertThat(r2.prevSignature()).isEqualTo("GENESIS");
            assertThat(r2.tenantId()).isEqualTo("t-2");
        });
    }

    @Test
    void missingTenantFallsBackToSystemTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace-sys", OrgScope.empty(), null));
        AuditEvent event = AuditEvent.of(AuditAction.LOGIN, "user", "u-?", "login pre-jwt");
        AuditEventRecord r = writer.persist(event);

        assertThat(r.tenantId()).isEqualTo("__SYSTEM__");
        assertThat(r.prevSignature()).isEqualTo("GENESIS");
        assertThat(writer.verify(r)).isTrue();
    }

    @Test
    void tamperingActionBreaksVerification() {
        withTenant("t-1", () -> {
            AuditEventRecord r = writer.persist(
                AuditEvent.of(AuditAction.CREATE, "rule", "r-1", "a"));
            assertThat(writer.verify(r)).isTrue();

            AuditEventRecord tampered = new AuditEventRecord(
                r.id(), r.eventId(), r.traceId(), r.occurredAt(), r.actorUserId(),
                "DELETE", // tampered action
                r.resourceType(), r.resourceId(), r.summary(), r.payloadDigest(),
                r.tenantId(), r.hospitalId(), r.departmentId(),
                r.prevEventId(), r.prevSignature(), r.signature(), r.status(),
                r.outcome(), r.errorCode(), r.createdAt()
            );
            assertThat(writer.verify(tampered)).isFalse();
        });
    }

    @Test
    void tamperingSummaryBreaksVerification() {
        withTenant("t-1", () -> {
            AuditEventRecord r = writer.persist(
                AuditEvent.of(AuditAction.PUBLISH, "rule", "r-1", "原始发布摘要"));
            assertThat(writer.verify(r)).isTrue();

            AuditEventRecord tampered = new AuditEventRecord(
                r.id(), r.eventId(), r.traceId(), r.occurredAt(), r.actorUserId(),
                r.action(), r.resourceType(), r.resourceId(), "篡改后的发布摘要", r.payloadDigest(),
                r.tenantId(), r.hospitalId(), r.departmentId(),
                r.prevEventId(), r.prevSignature(), r.signature(), r.status(),
                r.outcome(), r.errorCode(), r.createdAt(), r.actorRoles(), r.orgPath(),
                r.environmentKey(), r.beforeSnapshot(), r.afterSnapshot(), r.dedupeKey()
            );
            assertThat(writer.verify(tampered)).isFalse();
        });
    }

    @Test
    void tamperingTenantBreaksVerification() {
        withTenant("t-1", () -> {
            AuditEventRecord r = writer.persist(
                AuditEvent.of(AuditAction.PUBLISH, "rule", "r-1", "租户内发布"));
            assertThat(writer.verify(r)).isTrue();

            AuditEventRecord tampered = new AuditEventRecord(
                r.id(), r.eventId(), r.traceId(), r.occurredAt(), r.actorUserId(),
                r.action(), r.resourceType(), r.resourceId(), r.summary(), r.payloadDigest(),
                "t-2", r.hospitalId(), r.departmentId(),
                r.prevEventId(), r.prevSignature(), r.signature(), r.status(),
                r.outcome(), r.errorCode(), r.createdAt(), r.actorRoles(), r.orgPath(),
                r.environmentKey(), r.beforeSnapshot(), r.afterSnapshot(), r.dedupeKey()
            );
            assertThat(writer.verify(tampered)).isFalse();
        });
    }

    private static void withTenant(String tenantId, Runnable task) {
        RequestContext.runWith(
            new RequestContext.Snapshot("trace-" + tenantId,
                OrgScope.tenant(tenantId), "user-1"),
            task);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    static {
        // Ensure occurredAt resolution is at least millis; AuditEvent uses Instant.now()
        // and H2's TIMESTAMP can lose nano precision.
    }

    @SuppressWarnings("unused")
    private Instant truncate(Instant i) {
        return i.truncatedTo(ChronoUnit.MILLIS);
    }
}
