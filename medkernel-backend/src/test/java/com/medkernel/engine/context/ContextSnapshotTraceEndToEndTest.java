package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.release.PlatformBaselineRelease;
import com.medkernel.engine.release.PlatformBaselineReleaseRepository;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;

/**
 * MedKernel v1.0 GA · GA-ENG-API-01b 端到端验收测试（spec §6.2）。
 *
 * <p>覆盖 spec 表头六项验收的核心三项（其余三项在单测：失败 audit / 子事务 /
 * 机构生效版本解析器已覆盖）：
 * <ol>
 *   <li>mk_obs_state_transition 写入 INITIAL_CREATE 一行</li>
 *   <li>canonical_resource 持久化当前 trace_id</li>
 *   <li>diagnose 端点装配 entity + state_history + traceId + self link</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:ctx-trace-e2e-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ContextSnapshotTraceEndToEndTest {

    @Autowired ContextSnapshotService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformBaselineReleaseRepository platformBaselines;
    @Autowired ClinicalRuntimeReleaseRepository runtimeReleases;

    @BeforeEach
    void cleanDb() {
        jdbc.update("DELETE FROM mk_obs_state_transition");
        jdbc.update("DELETE FROM canonical_resource");
        jdbc.update("DELETE FROM context_snapshot");
        runtimeReleases.deleteAll();
        platformBaselines.deleteAll();
        Instant now = Instant.parse("2026-06-06T08:00:00Z");
        String emptyManifest = "0".repeat(64);
        platformBaselines.save(new PlatformBaselineRelease(
            null, "baseline-e2e", 1L, emptyManifest,
            now, "e2e-user", now, "e2e-user", "trace-runtime-e2e"));
        runtimeReleases.save(new ClinicalRuntimeRelease(
            null, "runtime-e2e", "tenant-e2e", "hospital-e2e", 1L,
            "baseline-e2e", emptyManifest, null,
            now, "e2e-user", now, "e2e-user", "trace-runtime-e2e"));
        RequestContext.clear();
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void createWritesStateHistoryAndCanonicalTraceIdAndDiagnoseAssemblesAll() {
        String traceId = "trace-e2e-" + System.nanoTime();
        RequestContext.restore(new RequestContext.Snapshot(
            traceId,
            new OrgScope(
                "tenant-e2e", null, "hospital-e2e", null,
                null, null, null, null),
            "e2e-user"));

        ContextSnapshotResponse resp = service.create(ContextSnapshotServiceFixtures.sampleRequest(), null);
        assertThat(resp.snapshotId()).startsWith("ctx-");

        // 验收 1：mk_obs_state_transition 写入一行
        Integer stateRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mk_obs_state_transition WHERE entity_id = ? AND to_status = 'ACTIVE'",
            Integer.class, resp.snapshotId());
        assertThat(stateRows).as("INITIAL_CREATE 状态历史").isEqualTo(1);

        String reason = jdbc.queryForObject(
            "SELECT reason FROM mk_obs_state_transition WHERE entity_id = ?",
            String.class, resp.snapshotId());
        assertThat(reason).isEqualTo("INITIAL_CREATE");

        // 验收 2：canonical_resource 持久化 trace_id
        Integer traceRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM canonical_resource WHERE trace_id = ?",
            Integer.class, traceId);
        assertThat(traceRows).as("canonical_resource trace_id 持久化").isGreaterThan(0);

        // 验收 3：diagnose 返回完整结构
        DiagnoseResponse diag = service.diagnose(resp.snapshotId());
        assertThat(diag.entityType()).isEqualTo("context_snapshot");
        assertThat(diag.currentStatus()).isEqualTo("ACTIVE");
        assertThat(diag.stateHistory()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(diag.traceId()).isEqualTo(traceId);
        assertThat(diag.links().self())
            .isEqualTo("/api/v1/engine/context_snapshot/" + resp.snapshotId() + "/diagnose");
    }
}
