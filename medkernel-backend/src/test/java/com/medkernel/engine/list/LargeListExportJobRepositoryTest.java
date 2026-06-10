package com.medkernel.engine.list;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 异步导出任务仓储测试：覆盖空库首次插入与状态更新路径。
 *
 * <p>回归 2026-06-10 首次部署缺陷族：业务指派主键实体未声明新建语义时，
 * Spring Data JDBC 把首次保存误判为 UPDATE，空库首次提交导出任务即失败（同 UserPreference）。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:export-job-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true"
})
class LargeListExportJobRepositoryTest {

    @Autowired
    private LargeListExportJobRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM mk_experience_export_task");
    }

    @Test
    void save_insertsBrandNewPendingJobIntoEmptyTable() {
        LargeListExportJob fresh = LargeListExportJob.createPending(
            "job-fresh-001", "t-1", "knowledge-assets",
            "{\"filters\":{}}", "FILTERED_RESULT", "trace-001", "idem-001", "medkernel");

        LargeListExportJob saved = repository.save(fresh);

        assertThat(saved.jobId()).isEqualTo("job-fresh-001");
        assertThat(repository.findByJobId("job-fresh-001"))
            .isPresent()
            .get()
            .extracting(LargeListExportJob::status)
            .isEqualTo("PENDING");
    }

    @Test
    void save_updatesExistingJobReconstructedWithKnownId() {
        repository.save(LargeListExportJob.createPending(
            "job-update-001", "t-1", "knowledge-assets",
            "{\"filters\":{}}", "FILTERED_RESULT", "trace-002", "idem-002", "medkernel"));

        LargeListExportJob loaded = repository.findByJobId("job-update-001").orElseThrow();
        repository.save(new LargeListExportJob(
            loaded.jobId(), loaded.tenantId(), loaded.resourceType(), loaded.requestSnapshot(),
            loaded.selectedScope(), "FAILED", null, null, 0L, "测试失败原因",
            loaded.timeCostMs(), loaded.traceId(), loaded.auditId(), loaded.idempotencyKey(),
            loaded.createdAt(), loaded.createdBy(), loaded.updatedAt(), loaded.updatedBy()));

        assertThat(repository.findByJobId("job-update-001"))
            .get()
            .extracting(LargeListExportJob::status, LargeListExportJob::errorMessage)
            .containsExactly("FAILED", "测试失败原因");
    }
}
