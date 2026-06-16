package com.medkernel.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * B0 10 万级真实方言压测烟测。
 *
 * <p>默认不随普通 {@code mvn test} 执行，避免本地和 CI 被 Oracle 容器与 60 万行造数拖慢。
 * 需要证据时显式设置 {@code B0_100K_DIALECT_SMOKE=true} 后运行本类。
 */
class B0LargeScaleDialectSmokeTest {

    private static final String ENABLE_ENV = "B0_100K_DIALECT_SMOKE";
    private static final String TENANT_ID = "t-b0-large-dialect";
    private static final int TOTAL_ROWS = 100_000;
    private static final int DEEP_OFFSET = 90_000;
    private static final int PAGE_SIZE = 25;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @Tag("docker")
    @Tag("performance")
    void postgresHandlesHundredThousandKnowledgeAndTerminologyRows() throws IOException {
        assumeLargeScaleSmokeEnabled();
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("medkernel")
                .withUsername("medkernel")
                .withPassword("medkernel")) {
            postgres.start();
            runDialectSmoke(
                "PostgreSQL",
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                "org.postgresql.Driver",
                "classpath:db/migration/postgres",
                Dialect.POSTGRES
            );
        }
    }

    @Test
    @Tag("docker")
    @Tag("performance")
    void oracleHandlesHundredThousandKnowledgeAndTerminologyRows() throws IOException {
        assumeLargeScaleSmokeEnabled();
        try (OracleContainer oracle = new OracleContainer(
                DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart"))
                .withDatabaseName("medkernel")
                .withUsername("medkernel")
                .withPassword("medkernel")) {
            oracle.start();
            runDialectSmoke(
                "Oracle",
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword(),
                "oracle.jdbc.OracleDriver",
                "classpath:db/migration/oracle",
                Dialect.ORACLE
            );
        }
    }

    private void assumeLargeScaleSmokeEnabled() {
        Assumptions.assumeTrue(
            "true".equalsIgnoreCase(System.getenv(ENABLE_ENV)),
            ENABLE_ENV + " 未开启，跳过 B0 10 万级真实方言压测烟测"
        );
        Assumptions.assumeTrue(
            DockerClientFactory.instance().isDockerAvailable(),
            "Docker 不可用，跳过 B0 10 万级真实方言压测烟测"
        );
    }

    private void runDialectSmoke(
            String vendor,
            String jdbcUrl,
            String username,
            String password,
            String driver,
            String migrationLocation,
            Dialect dialect) throws IOException {
        try (HikariDataSource dataSource = buildHikari(jdbcUrl, username, password, driver)) {
            migrate(dataSource, migrationLocation);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Instant seedStartedAt = Instant.now();
            seedKnowledgeIdentities(jdbc, dialect);
            seedTerminologyRows(jdbc, dialect);
            Duration seedCost = Duration.between(seedStartedAt, Instant.now());

            Instant queryStartedAt = Instant.now();
            assertKnowledgeQueries(jdbc, vendor);
            assertKnowledgeExportEquivalentScan(jdbc, vendor);
            assertKnowledgeExportEquivalentFile(jdbc, vendor, dialect);
            assertTerminologyQueries(jdbc, vendor);
            assertCandidateAndConflictQueries(jdbc, vendor, dialect);
            Duration queryCost = Duration.between(queryStartedAt, Instant.now());

            assertThat(seedCost).as("%s 60 万行造数不应异常拖长", vendor)
                .isLessThan(Duration.ofMinutes(dialect.seedBudgetMinutes));
            assertThat(queryCost).as("%s 10 万级知识/术语深页查询预算", vendor)
                .isLessThan(Duration.ofSeconds(dialect.queryBudgetSeconds));
        }
    }

    private HikariDataSource buildHikari(String jdbcUrl, String username, String password, String driver) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driver);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    private void migrate(DataSource dataSource, String migrationLocation) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations(migrationLocation)
            .baselineOnMigrate(true)
            .load()
            .migrate();
    }

    private void seedKnowledgeIdentities(JdbcTemplate jdbc, Dialect dialect) {
        jdbc.update(dialect.knowledgeIdentitySeedSql());
    }

    private void seedTerminologyRows(JdbcTemplate jdbc, Dialect dialect) {
        jdbc.update(dialect.standardTermSeedSql());
        jdbc.update(dialect.localTermSeedSql());
        jdbc.execute(dialect.refreshTerminologySourceStatsSql());
        jdbc.update(dialect.termMappingSeedSql());
        jdbc.update(dialect.mappingCandidateSeedSql());
        jdbc.update(dialect.mappingConflictSeedSql());
        jdbc.execute(dialect.refreshTerminologyPlannerStatsSql());
    }

    private void assertKnowledgeQueries(JdbcTemplate jdbc, String vendor) {
        Long total = jdbc.queryForObject("""
            SELECT COUNT(*) FROM knowledge_identity
            WHERE tenant_id = ? AND domain = 'DRUG' AND status = 'ACTIVE'
            """, Long.class, TENANT_ID);
        List<Long> ids = jdbc.queryForList("""
            SELECT id FROM knowledge_identity
            WHERE tenant_id = ? AND domain = 'DRUG' AND status = 'ACTIVE'
            ORDER BY updated_at DESC, id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """, Long.class, TENANT_ID, DEEP_OFFSET, PAGE_SIZE);

        assertThat(total).as("%s 知识身份总数", vendor).isEqualTo(TOTAL_ROWS);
        assertPageIds(ids, vendor + " 知识身份深页");
    }

    private void assertTerminologyQueries(JdbcTemplate jdbc, String vendor) {
        Long standardTotal = jdbc.queryForObject("""
            SELECT COUNT(*) FROM standard_term
            WHERE tenant_id = ? AND standard_system = 'LOINC' AND category = 'LAB' AND status = 'ACTIVE'
            """, Long.class, TENANT_ID);
        List<Long> standardIds = jdbc.queryForList("""
            SELECT id FROM standard_term
            WHERE tenant_id = ? AND standard_system = 'LOINC' AND category = 'LAB' AND status = 'ACTIVE'
            ORDER BY updated_at DESC, id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """, Long.class, TENANT_ID, DEEP_OFFSET, PAGE_SIZE);
        Long localTotal = jdbc.queryForObject("""
            SELECT COUNT(*) FROM local_term
            WHERE tenant_id = ? AND source_system = 'LIS' AND category = 'LAB' AND status = 'MAPPED'
            """, Long.class, TENANT_ID);
        List<Long> localIds = jdbc.queryForList("""
            SELECT id FROM local_term
            WHERE tenant_id = ? AND source_system = 'LIS' AND category = 'LAB' AND status = 'MAPPED'
            ORDER BY updated_at DESC, id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """, Long.class, TENANT_ID, DEEP_OFFSET, PAGE_SIZE);
        Long mappingTotal = jdbc.queryForObject("""
            SELECT COUNT(*) FROM term_mapping
            WHERE tenant_id = ? AND source_system = 'LIS' AND category = 'LAB' AND status = 'CONFIRMED'
            """, Long.class, TENANT_ID);
        List<Long> mappingIds = jdbc.queryForList("""
            SELECT id FROM term_mapping
            WHERE tenant_id = ? AND source_system = 'LIS' AND category = 'LAB' AND status = 'CONFIRMED'
            ORDER BY updated_at DESC, id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """, Long.class, TENANT_ID, DEEP_OFFSET, PAGE_SIZE);

        assertThat(standardTotal).as("%s 标准术语总数", vendor).isEqualTo(TOTAL_ROWS);
        assertThat(localTotal).as("%s 院内术语总数", vendor).isEqualTo(TOTAL_ROWS);
        assertThat(mappingTotal).as("%s 正式映射总数", vendor).isEqualTo(TOTAL_ROWS);
        assertPageIds(standardIds, vendor + " 标准术语深页");
        assertPageIds(localIds, vendor + " 院内术语深页");
        assertPageIds(mappingIds, vendor + " 正式映射深页");
    }

    private void assertKnowledgeExportEquivalentScan(JdbcTemplate jdbc, String vendor) {
        String keyword = "%性能压测知识资产%";
        Long total = jdbc.queryForObject("""
            SELECT COUNT(*) FROM knowledge_identity
            WHERE tenant_id = ?
              AND domain = 'DRUG'
              AND specialty_id = 'SP-7'
              AND status = 'ACTIVE'
              AND LOWER(subject) LIKE ?
            """, Long.class, TENANT_ID, keyword);
        long exported = 0;
        int offset = 0;
        while (true) {
            List<Long> ids = jdbc.queryForList("""
                SELECT id FROM knowledge_identity
                WHERE tenant_id = ?
                  AND domain = 'DRUG'
                  AND specialty_id = 'SP-7'
                  AND status = 'ACTIVE'
                  AND LOWER(subject) LIKE ?
                ORDER BY updated_at DESC, id DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """, Long.class, TENANT_ID, keyword, offset, 500);
            exported += ids.size();
            if (ids.size() < 500) {
                break;
            }
            offset += ids.size();
        }

        assertThat(total).as("%s 知识导出等价筛选总数", vendor).isEqualTo(5_000L);
        assertThat(exported).as("%s 知识导出等价分页扫描数量", vendor).isEqualTo(total);
    }

    private void assertKnowledgeExportEquivalentFile(JdbcTemplate jdbc, String vendor, Dialect dialect)
            throws IOException {
        String keyword = "%性能压测知识资产%";
        Path exportPath = Files.createTempFile("medkernel-b0-knowledge-export-", ".jsonl");
        Instant startedAt = Instant.now();
        List<Duration> pageCosts = new ArrayList<>();
        long exported = 0;
        long fileBytes = 0;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(exportPath)) {
                int offset = 0;
                while (true) {
                    Instant pageStartedAt = Instant.now();
                    List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT id,
                               identity_code AS identityCode,
                               subject,
                               specialty_id AS specialtyId
                        FROM knowledge_identity
                        WHERE tenant_id = ?
                          AND domain = 'DRUG'
                          AND status = 'ACTIVE'
                          AND LOWER(subject) LIKE ?
                        ORDER BY updated_at DESC, id DESC
                        OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                        """, TENANT_ID, keyword, offset, 500);
                    pageCosts.add(Duration.between(pageStartedAt, Instant.now()));
                    if (rows.isEmpty()) {
                        break;
                    }
                    for (Map<String, Object> row : rows) {
                        Map<String, Object> line = new LinkedHashMap<>();
                        line.put("recordType", "knowledge_identity");
                        line.put("payload", row);
                        writer.write(JSON.writeValueAsString(line));
                        writer.newLine();
                    }
                    exported += rows.size();
                    offset += rows.size();
                    if (rows.size() < 500) {
                        break;
                    }
                }
            }
            fileBytes = Files.size(exportPath);
        } finally {
            Files.deleteIfExists(exportPath);
        }
        Duration exportCost = Duration.between(startedAt, Instant.now());
        Duration pageP95 = percentile95(pageCosts);

        assertThat(exported).as("%s 知识导出等价文件行数", vendor).isEqualTo(TOTAL_ROWS);
        assertThat(fileBytes).as("%s 知识导出等价文件大小", vendor)
            .isBetween(1_000_000L, 64L * 1024L * 1024L);
        assertThat(exportCost).as("%s 知识导出等价文件生成预算", vendor)
            .isLessThan(Duration.ofSeconds(dialect.exportBudgetSeconds));
        assertThat(pageP95).as("%s 知识导出等价分页 P95", vendor)
            .isLessThan(Duration.ofMillis(dialect.exportPageP95Millis));
    }

    private void assertCandidateAndConflictQueries(JdbcTemplate jdbc, String vendor, Dialect dialect) {
        List<Duration> candidatePageCosts = new ArrayList<>();
        List<Duration> conflictPageCosts = new ArrayList<>();
        Long candidateTotal = jdbc.queryForObject("""
            SELECT COUNT(*) FROM mapping_candidate
            WHERE tenant_id = ?
              AND status = 'PENDING'
              AND risk_level = 'LOW'
              AND conflict_flag = %s
              AND generation_job_code = 'job-b0-large-dialect'
            """.formatted(dialect.falseLiteral()), Long.class, TENANT_ID);
        String candidatePageSql = """
            SELECT id FROM mapping_candidate
            WHERE tenant_id = ?
              AND status = 'PENDING'
              AND risk_level = 'LOW'
              AND conflict_flag = %s
              AND generation_job_code = 'job-b0-large-dialect'
            ORDER BY updated_at DESC, id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """.formatted(dialect.falseLiteral());
        List<Long> candidateIds = queryLongPage(
            jdbc,
            candidatePageSql,
            candidatePageCosts,
            TENANT_ID,
            DEEP_OFFSET,
            PAGE_SIZE
        );
        Long conflictTotal = jdbc.queryForObject("""
            SELECT COUNT(*) FROM mapping_conflict
            WHERE tenant_id = ?
              AND status = 'OPEN'
              AND risk_level = 'MEDIUM'
              AND conflict_type = 'ONE_TO_MANY'
            """, Long.class, TENANT_ID);
        String conflictPageSql = """
            SELECT id FROM mapping_conflict
            WHERE tenant_id = ?
              AND status = 'OPEN'
              AND risk_level = 'MEDIUM'
              AND conflict_type = 'ONE_TO_MANY'
            ORDER BY updated_at DESC, id DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;
        List<Long> conflictIds = queryLongPage(
            jdbc,
            conflictPageSql,
            conflictPageCosts,
            TENANT_ID,
            DEEP_OFFSET,
            PAGE_SIZE
        );

        assertThat(candidateTotal).as("%s 术语候选总数", vendor).isEqualTo(TOTAL_ROWS);
        assertThat(conflictTotal).as("%s 术语冲突总数", vendor).isEqualTo(TOTAL_ROWS);
        assertPageIds(candidateIds, vendor + " 术语候选深页");
        assertPageIds(conflictIds, vendor + " 术语冲突深页");
        assertCandidateAndConflictPageP95(
            jdbc,
            vendor,
            dialect,
            candidatePageSql,
            conflictPageSql,
            candidatePageCosts,
            conflictPageCosts
        );
    }

    private void assertCandidateAndConflictPageP95(
            JdbcTemplate jdbc,
            String vendor,
            Dialect dialect,
            String candidatePageSql,
            String conflictPageSql,
            List<Duration> candidatePageCosts,
            List<Duration> conflictPageCosts) {
        for (int offset : List.of(0, TOTAL_ROWS / 4, TOTAL_ROWS / 2, TOTAL_ROWS * 3 / 4, DEEP_OFFSET)) {
            assertPageIds(
                queryLongPage(jdbc, candidatePageSql, candidatePageCosts, TENANT_ID, offset, PAGE_SIZE),
                vendor + " 术语候选 P95 样本 " + offset
            );
            assertPageIds(
                queryLongPage(jdbc, conflictPageSql, conflictPageCosts, TENANT_ID, offset, PAGE_SIZE),
                vendor + " 术语冲突 P95 样本 " + offset
            );
        }

        Duration candidateP95 = percentile95(candidatePageCosts);
        Duration conflictP95 = percentile95(conflictPageCosts);
        assertThat(candidateP95).as("%s 术语候选分页 P95", vendor)
            .isLessThan(Duration.ofMillis(dialect.candidateConflictPageP95Millis));
        assertThat(conflictP95).as("%s 术语冲突分页 P95", vendor)
            .isLessThan(Duration.ofMillis(dialect.candidateConflictPageP95Millis));
    }

    private List<Long> queryLongPage(
            JdbcTemplate jdbc,
            String sql,
            List<Duration> pageCosts,
            Object... args) {
        Instant startedAt = Instant.now();
        try {
            return jdbc.queryForList(sql, Long.class, args);
        } finally {
            pageCosts.add(Duration.between(startedAt, Instant.now()));
        }
    }

    private void assertPageIds(List<Long> ids, String label) {
        assertThat(ids).as(label).hasSize(PAGE_SIZE);
        assertThat(ids).as(label + " 不应重复").doesNotHaveDuplicates();
        assertThat(ids).as(label + " 按更新时间和 id 倒序").isSortedAccordingTo(Comparator.reverseOrder());
    }

    private Duration percentile95(List<Duration> samples) {
        assertThat(samples).as("知识导出等价分页样本").isNotEmpty();
        List<Duration> sorted = samples.stream().sorted().toList();
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, index));
    }

    private enum Dialect {
        POSTGRES(5, 30, 30, 1_000, 1_000) {
            @Override
            String timestampExpression(String seedAlias) {
                return "TIMESTAMPTZ '2026-01-01 00:00:00+00' + (" + seedAlias + " || ' seconds')::interval";
            }

            @Override
            String seedSource() {
                return "generate_series(1, " + TOTAL_ROWS + ") AS seed(g)";
            }

            @Override
            String paddedSeedValue(String seedAlias) {
                return "LPAD(" + seedAlias + "::text, 6, '0')";
            }

            @Override
            String refreshTerminologyPlannerStatsSql() {
                return """
                    ANALYZE knowledge_identity;
                    ANALYZE standard_term;
                    ANALYZE local_term;
                    ANALYZE term_mapping;
                    ANALYZE mapping_candidate;
                    ANALYZE mapping_conflict;
                    """;
            }

            @Override
            String refreshTerminologySourceStatsSql() {
                return """
                    ANALYZE standard_term;
                    ANALYZE local_term;
                    """;
            }

            @Override
            String falseLiteral() {
                return "FALSE";
            }
        },
        ORACLE(10, 60, 90, 5_000, 5_000) {
            @Override
            String timestampExpression(String seedAlias) {
                return "FROM_TZ(TIMESTAMP '2026-01-01 00:00:00', 'UTC') + NUMTODSINTERVAL(" + seedAlias + ", 'SECOND')";
            }

            @Override
            String seedSource() {
                return "(SELECT LEVEL AS g FROM dual CONNECT BY LEVEL <= " + TOTAL_ROWS + ") seed";
            }

            @Override
            String paddedSeedValue(String seedAlias) {
                return "LPAD(TO_CHAR(" + seedAlias + "), 6, '0')";
            }

            @Override
            String refreshTerminologyPlannerStatsSql() {
                return """
                    BEGIN
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'KNOWLEDGE_IDENTITY');
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'STANDARD_TERM');
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'LOCAL_TERM');
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'TERM_MAPPING');
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'MAPPING_CANDIDATE');
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'MAPPING_CONFLICT');
                    END;
                    """;
            }

            @Override
            String refreshTerminologySourceStatsSql() {
                return """
                    BEGIN
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'STANDARD_TERM');
                      DBMS_STATS.GATHER_TABLE_STATS(USER, 'LOCAL_TERM');
                    END;
                    """;
            }

            @Override
            String falseLiteral() {
                return "0";
            }
        };

        private final int seedBudgetMinutes;
        private final int queryBudgetSeconds;
        private final int exportBudgetSeconds;
        private final long exportPageP95Millis;
        private final long candidateConflictPageP95Millis;

        Dialect(
                int seedBudgetMinutes,
                int queryBudgetSeconds,
                int exportBudgetSeconds,
                long exportPageP95Millis,
                long candidateConflictPageP95Millis) {
            this.seedBudgetMinutes = seedBudgetMinutes;
            this.queryBudgetSeconds = queryBudgetSeconds;
            this.exportBudgetSeconds = exportBudgetSeconds;
            this.exportPageP95Millis = exportPageP95Millis;
            this.candidateConflictPageP95Millis = candidateConflictPageP95Millis;
        }

        abstract String timestampExpression(String seedAlias);

        abstract String seedSource();

        abstract String paddedSeedValue(String seedAlias);

        abstract String refreshTerminologyPlannerStatsSql();

        abstract String refreshTerminologySourceStatsSql();

        abstract String falseLiteral();

        String knowledgeIdentitySeedSql() {
            String updatedAt = timestampExpression("seed.g");
            return """
                INSERT INTO knowledge_identity (
                    tenant_id, identity_code, domain, subject, specialty_id, description,
                    status, current_version_id, created_at, created_by, updated_at, updated_by
                )
                SELECT '%s',
                       'DRUG.PERF.' || %s,
                       'DRUG',
                       '性能压测知识资产 ' || seed.g,
                       'SP-' || MOD(seed.g, 20),
                       'B0 PostgreSQL/Oracle 10 万级知识身份分页压测数据',
                       'ACTIVE',
                       NULL,
                       %s,
                       'perf-seed',
                       %s,
                       'perf-seed'
                FROM %s
                """.formatted(TENANT_ID, paddedSeedValue("seed.g"), updatedAt, updatedAt, seedSource());
        }

        String standardTermSeedSql() {
            String updatedAt = timestampExpression("seed.g");
            return """
                INSERT INTO standard_term (
                    tenant_id, standard_system, term_code, category, display_name, normalized_name,
                    version_no, status, source_version_id, evidence_text, created_at, created_by, updated_at, updated_by
                )
                SELECT '%s',
                       'LOINC',
                       'LOINC-PERF-' || %s,
                       'LAB',
                       '标准检验术语 ' || seed.g,
                       'standard-lab-' || seed.g,
                       '2.78',
                       'ACTIVE',
                       NULL,
                       'LOINC 10 万级标准术语分页压测数据',
                       %s,
                       'perf-seed',
                       %s,
                       'perf-seed'
                FROM %s
                """.formatted(TENANT_ID, paddedSeedValue("seed.g"), updatedAt, updatedAt, seedSource());
        }

        String localTermSeedSql() {
            String updatedAt = timestampExpression("seed.g");
            return """
                INSERT INTO local_term (
                    tenant_id, source_system, local_code, category, local_name, normalized_name,
                    department_id, status, first_seen_at, last_seen_at, created_at, created_by, updated_at, updated_by
                )
                SELECT '%s',
                       'LIS',
                       'LIS-PERF-' || %s,
                       'LAB',
                       '院内检验术语 ' || seed.g,
                       'local-lab-' || seed.g,
                       'D-' || MOD(seed.g, 50),
                       'MAPPED',
                       %s,
                       %s,
                       %s,
                       'perf-seed',
                       %s,
                       'perf-seed'
                FROM %s
                """.formatted(TENANT_ID, paddedSeedValue("seed.g"), updatedAt, updatedAt, updatedAt, updatedAt, seedSource());
        }

        String termMappingSeedSql() {
            String sequence = "seed.g";
            String updatedAt = timestampExpression(sequence);
            return """
                INSERT INTO term_mapping (
                    tenant_id, local_term_id, standard_term_id, source_system, category, confidence,
                    risk_level, status, evidence_text, confirmed_by, confirmed_at, created_at, created_by, updated_at, updated_by
                )
                SELECT '%s',
                       lt.id,
                       st.id,
                       'LIS',
                       'LAB',
                       0.99,
                       'LOW',
                       'CONFIRMED',
                       '10 万级术语映射分页压测数据 ' || %s,
                       'perf-reviewer',
                       %s,
                       %s,
                       'perf-seed',
                       %s,
                       'perf-seed'
                FROM %s
                JOIN local_term lt
                  ON lt.tenant_id = '%s'
                 AND lt.source_system = 'LIS'
                 AND lt.local_code = 'LIS-PERF-' || %s
                 AND lt.category = 'LAB'
                 AND lt.status = 'MAPPED'
                JOIN standard_term st
                  ON st.tenant_id = '%s'
                 AND st.standard_system = 'LOINC'
                 AND st.term_code = 'LOINC-PERF-' || %s
                 AND st.version_no = '2.78'
                 AND st.category = 'LAB'
                 AND st.status = 'ACTIVE'
                """.formatted(
                    TENANT_ID,
                    sequence,
                    updatedAt,
                    updatedAt,
                    updatedAt,
                    seedSource(),
                    TENANT_ID,
                    paddedSeedValue(sequence),
                    TENANT_ID,
                    paddedSeedValue(sequence)
                );
        }

        String mappingCandidateSeedSql() {
            String sequence = "seed.g";
            String updatedAt = timestampExpression(sequence);
            return """
                INSERT INTO mapping_candidate (
                    tenant_id, local_term_id, standard_term_id, confidence, candidate_source, risk_level,
                    evidence_text, conflict_flag, status, review_note, reviewed_by, reviewed_at,
                    created_at, created_by, updated_at, updated_by, generation_job_code
                )
                SELECT '%s',
                       lt.id,
                       st.id,
                       1.0,
                       'RULE',
                       'LOW',
                       '10 万级术语候选真实方言压测数据 ' || %s,
                       %s,
                       'PENDING',
                       NULL,
                       NULL,
                       NULL,
                       %s,
                       'perf-seed',
                       %s,
                       'perf-seed',
                       'job-b0-large-dialect'
                FROM %s
                JOIN local_term lt
                  ON lt.tenant_id = '%s'
                 AND lt.source_system = 'LIS'
                 AND lt.local_code = 'LIS-PERF-' || %s
                 AND lt.category = 'LAB'
                 AND lt.status = 'MAPPED'
                JOIN standard_term st
                  ON st.tenant_id = '%s'
                 AND st.standard_system = 'LOINC'
                 AND st.term_code = 'LOINC-PERF-' || %s
                 AND st.version_no = '2.78'
                 AND st.category = 'LAB'
                 AND st.status = 'ACTIVE'
                """.formatted(
                    TENANT_ID,
                    sequence,
                    falseLiteral(),
                    updatedAt,
                    updatedAt,
                    seedSource(),
                    TENANT_ID,
                    paddedSeedValue(sequence),
                    TENANT_ID,
                    paddedSeedValue(sequence)
                );
        }

        String mappingConflictSeedSql() {
            String sequence = "seed.g";
            String updatedAt = timestampExpression(sequence);
            return """
                INSERT INTO mapping_conflict (
                    tenant_id, conflict_type, local_term_id, standard_term_id, mapping_id, risk_level,
                    description, status, resolved_by, resolved_at, resolution_note,
                    created_at, created_by, updated_at, updated_by
                )
                SELECT '%s',
                       'ONE_TO_MANY',
                       lt.id,
                       st.id,
                       NULL,
                       'MEDIUM',
                       '10 万级术语冲突真实方言压测数据 ' || %s,
                       'OPEN',
                       NULL,
                       NULL,
                       NULL,
                       %s,
                       'perf-seed',
                       %s,
                       'perf-seed'
                FROM %s
                JOIN local_term lt
                  ON lt.tenant_id = '%s'
                 AND lt.source_system = 'LIS'
                 AND lt.local_code = 'LIS-PERF-' || %s
                 AND lt.category = 'LAB'
                 AND lt.status = 'MAPPED'
                JOIN standard_term st
                  ON st.tenant_id = '%s'
                 AND st.standard_system = 'LOINC'
                 AND st.term_code = 'LOINC-PERF-' || %s
                 AND st.version_no = '2.78'
                 AND st.category = 'LAB'
                 AND st.status = 'ACTIVE'
                """.formatted(
                    TENANT_ID,
                    sequence,
                    updatedAt,
                    updatedAt,
                    seedSource(),
                    TENANT_ID,
                    paddedSeedValue(sequence),
                    TENANT_ID,
                    paddedSeedValue(sequence)
                );
        }
    }
}
