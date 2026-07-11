package com.medkernel.engine.list;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.IntStream;

import com.medkernel.shared.audit.persistence.AuditEventQuery;
import com.medkernel.shared.audit.persistence.AuditEventRecord;
import com.medkernel.shared.audit.persistence.AuditEventRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API-13 大规模审计列表的 10 万级游标分页合同。
 */
@Tag("performance")
class LargeListAuditEventRepositoryTest {

    @Test
    void findPage_WithLargeAuditTableAndDeepCursor_ReturnsStableKeysetPageWithoutDuplicates() {
        try (HikariDataSource dataSource = new HikariDataSource(hikari())) {
            migrate(dataSource);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedAuditEvents(jdbc, 100_000);

            AuditEventRepository repository = new AuditEventRepository(jdbc);
            AuditEventQuery query = new AuditEventQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                90_000L,
                25,
                0L,
                "id",
                "DESC"
            );

            Instant startedAt = Instant.now();
            var rows = repository.findPage("tenant-1", query);
            Duration cost = Duration.between(startedAt, Instant.now());

            assertThat(rows).hasSize(26);
            assertThat(rows).extracting(AuditEventRecord::id)
                .allMatch(id -> id < 90_000L)
                .doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.reverseOrder());
            assertThat(cost.toMillis()).isLessThan(2_000L);

            AuditEventQuery offsetAsc = new AuditEventQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                5,
                10L,
                "id",
                "ASC"
            );

            var ascendingRows = repository.findPage("tenant-1", offsetAsc);

            assertThat(ascendingRows).hasSize(6);
            assertThat(ascendingRows).extracting(AuditEventRecord::id)
                .containsExactly(11L, 12L, 13L, 14L, 15L, 16L);
        }
    }

    private void migrate(HikariDataSource dataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/h2")
            .baselineOnMigrate(true)
            .load()
            .migrate();
    }

    private void seedAuditEvents(JdbcTemplate jdbc, int total) {
        String sql = """
            INSERT INTO audit_event (
                event_id, trace_id, occurred_at, actor_user_id, action,
                resource_type, resource_id, summary, tenant_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        int batchSize = 5_000;
        for (int start = 1; start <= total; start += batchSize) {
            int from = start;
            int to = Math.min(total, start + batchSize - 1);
            int[] ids = IntStream.rangeClosed(from, to).toArray();
            jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws SQLException {
                    int id = ids[index];
                    ps.setString(1, "evt-" + id);
                    ps.setString(2, "trace-" + id);
                    ps.setTimestamp(3, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(id)));
                    ps.setString(4, "doctor-" + (id % 10));
                    ps.setString(5, id % 2 == 0 ? "LOGIN" : "EXPORT");
                    ps.setString(6, "USER");
                    ps.setString(7, "res-" + id);
                    ps.setString(8, "审计事件 " + id);
                    ps.setString(9, "tenant-1");
                }

                @Override
                public int getBatchSize() {
                    return ids.length;
                }
            });
        }
    }

    private HikariConfig hikari() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:api13-large-list-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setDriverClassName("org.h2.Driver");
        cfg.setMaximumPoolSize(2);
        return cfg;
    }
}
