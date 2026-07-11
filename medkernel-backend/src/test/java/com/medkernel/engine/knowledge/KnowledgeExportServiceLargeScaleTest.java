package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * API-03 知识异步导出的 B0 本地 10 万级链路合同。
 *
 * <p>验证提交、后台完成、轮询状态和下载文件全链路可执行；真实 P95、资源占用和远端方言证据仍归专项压测。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge-export-large-scale-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
@Tag("performance")
class KnowledgeExportServiceLargeScaleTest {

    private static final String TENANT_ID = "t-large-export";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    KnowledgeExportJobRepository jobRepository;
    @Autowired
    KnowledgeIdentityRepository identityRepository;
    @Autowired
    KnowledgeAssetVersionRepository versionRepository;
    @Autowired
    KnowledgeSupersessionRepository supersessionRepository;
    @Autowired
    CitationRepository citationRepository;
    @Autowired
    KnowledgeInvalidationRepository invalidationRepository;
    @Autowired
    AffectedCaseTaskRepository affectedCaseTaskRepository;

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void submitPollAndDownloadExportsHundredThousandFilteredIdentities() throws Exception {
        seedKnowledgeIdentities(100_000, "DRUG", "DRUG.PERF.", "ACTIVE");
        seedKnowledgeIdentities(50, "GUIDELINE", "GUIDE.PERF.", "ACTIVE");
        KnowledgeExportService service = service();
        RequestContext.restore(new RequestContext.Snapshot("trace-export", OrgScope.tenant(TENANT_ID), "u-export"));

        Instant startedAt = Instant.now();
        KnowledgeExportJob submitted = service.submit(
            ExportType.IDENTITIES,
            """
                {"domain":"DRUG","status":"ACTIVE"}
                """
        );
        KnowledgeExportJob completed = service.get(submitted.jobCode());
        long downloadedLines;
        try (InputStream input = service.downloadFile(submitted.jobCode());
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            downloadedLines = reader.lines().count();
        } finally {
            Files.deleteIfExists(service.physicalExportPathForTest(submitted.jobCode()));
        }
        Duration cost = Duration.between(startedAt, Instant.now());

        assertThat(completed.status()).isEqualTo(ExportStatus.SUCCEEDED);
        assertThat(completed.itemCount()).isEqualTo(100_000L);
        assertThat(completed.resultUri()).isEqualTo(
            "/api/v1/engine/knowledge/exports/" + submitted.jobCode() + "/download");
        assertThat(downloadedLines).isEqualTo(100_000L);
        assertThat(cost.toSeconds()).isLessThan(30L);
    }

    private KnowledgeExportService service() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return new KnowledgeExportService(
            jobRepository,
            identityRepository,
            versionRepository,
            supersessionRepository,
            citationRepository,
            invalidationRepository,
            affectedCaseTaskRepository,
            mapper,
            Runnable::run
        );
    }

    private void seedKnowledgeIdentities(int total, String domain, String codePrefix, String status) {
        String sql = """
            INSERT INTO knowledge_identity (
                tenant_id, identity_code, domain, subject, specialty_id, description,
                status, current_version_id, created_at, created_by, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, 'perf-seed', ?, 'perf-seed')
            """;
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
                    Timestamp updatedAt = Timestamp.from(base.plusSeconds(id));
                    ps.setString(1, TENANT_ID);
                    ps.setString(2, codePrefix + String.format("%06d", id));
                    ps.setString(3, domain);
                    ps.setString(4, "导出压测知识资产 " + domain + " " + id);
                    ps.setString(5, "SP-" + (id % 20));
                    ps.setString(6, "B0 10 万级知识异步导出合同数据");
                    ps.setString(7, status);
                    ps.setTimestamp(8, updatedAt);
                    ps.setTimestamp(9, updatedAt);
                }

                @Override
                public int getBatchSize() {
                    return ids.length;
                }
            });
        }
    }
}
