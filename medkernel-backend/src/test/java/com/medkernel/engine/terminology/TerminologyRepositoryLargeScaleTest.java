package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * API-04 字典/映射列表的 B0 本地 10 万级分页合同。
 *
 * <p>该测试不替代 PostgreSQL / Oracle 真实压测，只作为本地回归护栏，防止字典列表退回前端全量或仓储全量加载。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:terminology-large-scale-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
@Tag("performance")
class TerminologyRepositoryLargeScaleTest {

    private static final String TENANT_ID = "t-large-terminology";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StandardTermRepository standardTerms;

    @Autowired
    LocalTermRepository localTerms;

    @Autowired
    TermMappingRepository mappings;
    @Autowired
    MappingCandidateRepository candidates;
    @Autowired
    MappingConflictRepository conflicts;

    @AfterEach
    void wipe() {
        RequestContext.clear();
        jdbc.update("DELETE FROM mapping_conflict WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM mapping_candidate WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM term_mapping WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM local_term WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM standard_term WHERE tenant_id = ?", TENANT_ID);
    }

    @Test
    void terminologyAndMappingRepositoriesHandleHundredThousandRowsWithinLocalBudget() {
        seedStandardTerms(100_000);
        seedLocalTerms(100_000);
        seedMappings(100_000);

        Instant startedAt = Instant.now();
        long standardTotal = standardTerms.countByTenantIdsFilter(
            List.of(TENANT_ID), "LOINC", "LAB", "ACTIVE", null);
        List<StandardTerm> standardRows = standardTerms.pageByTenantIdsFilter(
            List.of(TENANT_ID), TENANT_ID, "LOINC", "LAB", "ACTIVE", null, 90_000, 25);
        long localTotal = localTerms.countByFilter(TENANT_ID, "LIS", "LAB", "MAPPED", null);
        List<LocalTerm> localRows = localTerms.pageByFilter(TENANT_ID, "LIS", "LAB", "MAPPED", null, 90_000, 25);
        long mappingTotal = mappings.countByFilter(TENANT_ID, "LIS", "LAB", "CONFIRMED", null);
        List<TermMapping> mappingRows = mappings.pageByFilter(TENANT_ID, "LIS", "LAB", "CONFIRMED", null, 90_000, 25);
        Duration cost = Duration.between(startedAt, Instant.now());

        assertThat(standardTotal).isEqualTo(100_000L);
        assertThat(localTotal).isEqualTo(100_000L);
        assertThat(mappingTotal).isEqualTo(100_000L);
        assertThat(standardRows).hasSize(25);
        assertThat(localRows).hasSize(25);
        assertThat(mappingRows).hasSize(25);
        assertThat(standardRows).extracting(StandardTerm::id)
            .doesNotHaveDuplicates()
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(localRows).extracting(LocalTerm::id)
            .doesNotHaveDuplicates()
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(mappingRows).extracting(TermMapping::id)
            .doesNotHaveDuplicates()
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(cost.toMillis()).isLessThan(5_000L);
    }

    @Test
    void candidateAndConflictRepositoriesHandleHundredThousandRowsWithinLocalBudget() {
        seedStandardTerms(100_000);
        seedUnmappedLocalTermsWithStandardCodes(100_000);
        seedMappingCandidates(100_000);
        seedMappingConflicts(100_000);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-terminology-large", OrgScope.tenant(TENANT_ID), "u-terminology"));

        Instant startedAt = Instant.now();
        TerminologyService service = service();
        var candidatePage = service.pageCandidates(
            new PageRequest(3_601, 25, null),
            new CandidateFilter(MappingCandidateStatus.PENDING, TermRiskLevel.LOW, false));
        var conflictPage = service.pageConflicts(
            new PageRequest(3_601, 25, null),
            new ConflictFilter(MappingConflictStatus.OPEN, TermRiskLevel.MEDIUM, MappingConflictType.ONE_TO_MANY));
        Duration cost = Duration.between(startedAt, Instant.now());

        assertThat(candidatePage.total()).isEqualTo(100_000L);
        assertThat(candidatePage.items()).hasSize(25);
        assertThat(candidatePage.items()).extracting(MappingCandidate::id)
            .doesNotHaveDuplicates()
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(conflictPage.total()).isEqualTo(100_000L);
        assertThat(conflictPage.items()).hasSize(25);
        assertThat(conflictPage.items()).extracting(MappingConflict::id)
            .doesNotHaveDuplicates()
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(cost.toSeconds()).isLessThan(30L);
    }

    private TerminologyService service() {
        return new TerminologyService(
            standardTerms,
            localTerms,
            mappings,
            null,
            null,
            candidates,
            conflicts,
            null,
            null,
            null
        );
    }

    private void seedStandardTerms(int total) {
        String sql = """
            INSERT INTO standard_term (
                tenant_id, standard_system, term_code, category, display_name, normalized_name,
                version_no, status, source_version_id, evidence_text, created_at, created_by, updated_at, updated_by
            ) VALUES (?, 'LOINC', ?, 'LAB', ?, ?, '2.78', 'ACTIVE', NULL, ?, ?, 'perf-seed', ?, 'perf-seed')
            """;
        batch(total, (ps, id, updatedAt) -> {
            ps.setString(1, TENANT_ID);
            ps.setString(2, "LOINC-PERF-" + String.format("%06d", id));
            ps.setString(3, "标准检验术语 " + id);
            ps.setString(4, "standard-lab-" + id);
            ps.setString(5, "LOINC 10 万级标准术语分页合同数据");
            ps.setTimestamp(6, updatedAt);
            ps.setTimestamp(7, updatedAt);
        }, sql);
    }

    private void seedLocalTerms(int total) {
        String sql = """
            INSERT INTO local_term (
                tenant_id, source_system, local_code, category, local_name, normalized_name,
                department_id, status, first_seen_at, last_seen_at, created_at, created_by, updated_at, updated_by
            ) VALUES (?, 'LIS', ?, 'LAB', ?, ?, ?, 'MAPPED', ?, ?, ?, 'perf-seed', ?, 'perf-seed')
            """;
        batch(total, (ps, id, updatedAt) -> {
            ps.setString(1, TENANT_ID);
            ps.setString(2, "LIS-PERF-" + String.format("%06d", id));
            ps.setString(3, "院内检验术语 " + id);
            ps.setString(4, "local-lab-" + id);
            ps.setString(5, "D-" + (id % 50));
            ps.setTimestamp(6, updatedAt);
            ps.setTimestamp(7, updatedAt);
            ps.setTimestamp(8, updatedAt);
            ps.setTimestamp(9, updatedAt);
        }, sql);
    }

    private void seedUnmappedLocalTermsWithStandardCodes(int total) {
        String sql = """
            INSERT INTO local_term (
                tenant_id, source_system, local_code, category, local_name, normalized_name,
                department_id, status, first_seen_at, last_seen_at, created_at, created_by, updated_at, updated_by
            ) VALUES (?, 'LIS', ?, 'LAB', ?, ?, ?, 'UNMAPPED', ?, ?, ?, 'perf-seed', ?, 'perf-seed')
            """;
        batch(total, (ps, id, updatedAt) -> {
            ps.setString(1, TENANT_ID);
            ps.setString(2, "LOINC-PERF-" + String.format("%06d", id));
            ps.setString(3, "待映射院内检验术语 " + id);
            ps.setString(4, "local-candidate-" + id);
            ps.setString(5, "D-" + (id % 50));
            ps.setTimestamp(6, updatedAt);
            ps.setTimestamp(7, updatedAt);
            ps.setTimestamp(8, updatedAt);
            ps.setTimestamp(9, updatedAt);
        }, sql);
    }

    private void seedMappingCandidates(int total) {
        String sql = """
            INSERT INTO mapping_candidate (
                tenant_id, local_term_id, standard_term_id, confidence, candidate_source, risk_level,
                evidence_text, conflict_flag, status, review_note, reviewed_by, reviewed_at,
                created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, ?, 1.0, 'RULE', 'LOW', ?, FALSE, 'PENDING', NULL, NULL, NULL, ?, 'perf-seed', ?, 'perf-seed')
            """;
        batch(total, (ps, id, updatedAt) -> {
            ps.setString(1, TENANT_ID);
            ps.setLong(2, id);
            ps.setLong(3, id);
            ps.setString(4, "10 万级候选映射分页合同数据 " + id);
            ps.setTimestamp(5, updatedAt);
            ps.setTimestamp(6, updatedAt);
        }, sql);
    }

    private void seedMappings(int total) {
        String sql = """
            INSERT INTO term_mapping (
                tenant_id, local_term_id, standard_term_id, source_system, category, confidence,
                risk_level, status, evidence_text, confirmed_by, confirmed_at, created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, ?, 'LIS', 'LAB', 0.99, 'LOW', 'CONFIRMED', ?, 'perf-reviewer', ?, ?, 'perf-seed', ?, 'perf-seed')
            """;
        batch(total, (ps, id, updatedAt) -> {
            ps.setString(1, TENANT_ID);
            ps.setLong(2, id);
            ps.setLong(3, id);
            ps.setString(4, "10 万级术语映射分页合同数据 " + id);
            ps.setTimestamp(5, updatedAt);
            ps.setTimestamp(6, updatedAt);
            ps.setTimestamp(7, updatedAt);
        }, sql);
    }

    private void seedMappingConflicts(int total) {
        String sql = """
            INSERT INTO mapping_conflict (
                tenant_id, conflict_type, local_term_id, standard_term_id, mapping_id, risk_level,
                description, status, resolved_by, resolved_at, resolution_note,
                created_at, created_by, updated_at, updated_by
            ) VALUES (?, 'ONE_TO_MANY', ?, ?, NULL, 'MEDIUM', ?, 'OPEN', NULL, NULL, NULL, ?, 'perf-seed', ?, 'perf-seed')
            """;
        batch(total, (ps, id, updatedAt) -> {
            ps.setString(1, TENANT_ID);
            ps.setLong(2, id);
            ps.setLong(3, id);
            ps.setString(4, "10 万级候选冲突分页合同数据 " + id);
            ps.setTimestamp(5, updatedAt);
            ps.setTimestamp(6, updatedAt);
        }, sql);
    }

    private void batch(int total, BatchBinder binder, String sql) {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        int batchSize = 5_000;
        for (int start = 1; start <= total; start += batchSize) {
            int from = start;
            int to = Math.min(total, start + batchSize - 1);
            int[] ids = IntStream.rangeClosed(from, to).toArray();
            jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws SQLException {
                    int id = ids[index];
                    binder.bind(ps, id, Timestamp.from(base.plusSeconds(id)));
                }

                @Override
                public int getBatchSize() {
                    return ids.length;
                }
            });
        }
    }

    @FunctionalInterface
    private interface BatchBinder {
        void bind(PreparedStatement ps, int id, Timestamp updatedAt) throws SQLException;
    }
}
