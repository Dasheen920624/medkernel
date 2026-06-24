package com.medkernel.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * IsolatedAuditPublisher 集成测试。
 *
 * <p>核心断言：业务事务回滚后，{@code outcome=FAILED} 的审计事件仍能落库。
 * 用真实 H2 + TransactionTemplate 构造外层事务，{@link IsolatedAuditPublisher}
 * 用 PROPAGATION_REQUIRES_NEW 子事务发 audit；外层 throw 触发回滚 →
 * 内层 audit 子事务已 commit，验签链头已推进 → audit_event 仍可按 trace_id 查到。
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:isolated-audit-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class IsolatedAuditPublisherTest {

    @Autowired IsolatedAuditPublisher isolated;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txm;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM audit_chain_head");
        RequestContext.clear();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void publishInNewTxSurvivesOuterRollback() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-iso", OrgScope.tenant("tenant-iso"), "tester"));
        TransactionTemplate outerTx = new TransactionTemplate(txm);

        try {
            outerTx.executeWithoutResult(status -> {
                isolated.publishInNewTx(AuditEvent.failure(
                    AuditAction.EXECUTE, "context_snapshot", "ctx-iso-1",
                    "ENG-CONTEXT-003", "INVALID quality 被拒绝"));
                throw new RuntimeException("主业务失败，触发回滚");
            });
        } catch (RuntimeException expected) {
            // 主事务回滚预期发生
        }

        Integer found = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE trace_id = ? AND outcome = 'FAILED'",
            Integer.class, "trace-iso");
        assertThat(found).as("失败 audit 必须落库，即使主事务回滚").isEqualTo(1);

        String errorCode = jdbc.queryForObject(
            "SELECT error_code FROM audit_event WHERE trace_id = ?",
            String.class, "trace-iso");
        assertThat(errorCode).isEqualTo("ENG-CONTEXT-003");
    }

    @Test
    void publishInNewTxAlsoWorksOutsideOuterTransaction() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-iso-no-outer", OrgScope.tenant("tenant-iso"), "tester"));

        isolated.publishInNewTx(AuditEvent.failure(
            AuditAction.EXECUTE, "context_snapshot", "ctx-iso-2",
            "ENG-CONTEXT-002", "机构生效版本不存在"));

        Integer found = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE trace_id = ? AND outcome = 'FAILED'",
            Integer.class, "trace-iso-no-outer");
        assertThat(found).isEqualTo(1);
    }
}
