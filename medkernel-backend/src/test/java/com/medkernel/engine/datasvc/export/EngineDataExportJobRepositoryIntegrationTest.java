package com.medkernel.engine.datasvc.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 引擎数据服务层异步导出作业仓储集成测试（DATASVC-01）。
 *
 * <p>对真实 H2 验证 {@code mk_engine_data_export_job} 建表与 Spring Data JDBC 列映射真实可执行：
 * 保存 / 按租户+jobCode 查 / 按租户+幂等键查 / 分页列表 / 租户隔离。
 */
@SpringBootTest
@ActiveProfiles("dev")
class EngineDataExportJobRepositoryIntegrationTest {

    @Autowired
    private EngineDataExportJobRepository repo;

    private static final String TENANT = "tenant-export-it";

    private EngineDataExportJob pending(String idem, Instant createdAt) {
        return new EngineDataExportJob(
            null, TENANT, UUID.randomUUID().toString(), "quality-1",
            EngineDataExportType.RULE_USAGE, ExportJobStatus.PENDING, 0,
            null, null, null,
            "exp-engine-data-rule-usage-" + idem, idem,
            "{\"exportType\":\"RULE_USAGE\",\"windowDays\":90}",
            createdAt, null, null, null);
    }

    @Test
    void savesAndLoadsByTenantAndJobCodeWithEnumMapping() {
        EngineDataExportJob saved = repo.save(pending("idem-it-1", Instant.now()));

        EngineDataExportJob found = repo.findByTenantIdAndJobCode(TENANT, saved.jobCode()).orElseThrow();

        assertThat(found.exportType()).isEqualTo(EngineDataExportType.RULE_USAGE);
        assertThat(found.status()).isEqualTo(ExportJobStatus.PENDING);
        assertThat(found.confirmationId()).isEqualTo("exp-engine-data-rule-usage-idem-it-1");
        assertThat(found.requestSnapshot()).contains("RULE_USAGE");
    }

    @Test
    void findsByTenantAndIdempotencyKeyForResubmitDedup() {
        repo.save(pending("idem-it-dedup", Instant.now()));

        assertThat(repo.findByTenantIdAndIdempotencyKey(TENANT, "idem-it-dedup")).isPresent();
        assertThat(repo.findByTenantIdAndIdempotencyKey("tenant-other", "idem-it-dedup")).isEmpty();
    }

    @Test
    void pagesRecentScopedToTenant() {
        Instant now = Instant.now();
        repo.save(pending("idem-it-r1", now.minusSeconds(200)));
        repo.save(pending("idem-it-r2", now.minusSeconds(100)));
        repo.save(new EngineDataExportJob(
            null, "tenant-export-it-other", UUID.randomUUID().toString(), "quality-1",
            EngineDataExportType.RULE_USAGE, ExportJobStatus.PENDING, 0,
            null, null, null, "exp-other", "idem-other",
            "{\"exportType\":\"RULE_USAGE\",\"windowDays\":90}",
            now, null, null, null));

        assertThat(repo.countByTenantId(TENANT)).isGreaterThanOrEqualTo(2L);
        List<EngineDataExportJob> recent = repo.pageByTenantId(TENANT, 0, 2);

        assertThat(recent).extracting(EngineDataExportJob::tenantId).containsOnly(TENANT);
        assertThat(recent).extracting(EngineDataExportJob::idempotencyKey)
            .contains("idem-it-r1", "idem-it-r2")
            .doesNotContain("idem-other");
    }
}
