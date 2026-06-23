package com.medkernel.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 为依赖医院运行修订外键的仓储测试建立最小真实父记录。
 */
public final class ClinicalRuntimeReleaseFixture {

    private ClinicalRuntimeReleaseFixture() {
    }

    public static void insert(
            JdbcTemplate jdbc,
            String tenantId,
            String hospitalId,
            String releaseId) {
        String baselineId = baselineId(releaseId);
        Long baselineRevision = jdbc.queryForObject(
            "SELECT COALESCE(MAX(revision_no), 0) + 1 FROM platform_baseline_release",
            Long.class);
        Long runtimeRevision = jdbc.queryForObject("""
            SELECT COALESCE(MAX(revision_no), 0) + 1
            FROM clinical_runtime_release
            WHERE tenant_id = ? AND hospital_id = ?
            """, Long.class, tenantId, hospitalId);
        jdbc.update("""
            INSERT INTO platform_baseline_release
                (baseline_release_id, revision_no, manifest_sha256, published_at,
                 published_by, created_by, trace_id)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'test', 'test', 'test-runtime-release')
            """, baselineId, baselineRevision, "a".repeat(64));
        jdbc.update("""
            INSERT INTO clinical_runtime_release
                (release_id, tenant_id, hospital_id, revision_no, platform_baseline_release_id,
                 manifest_sha256, activated_at, activated_by, created_by, trace_id)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'test', 'test', 'test-runtime-release')
            """, releaseId, tenantId, hospitalId, runtimeRevision, baselineId, "b".repeat(64));
    }

    public static void delete(
            JdbcTemplate jdbc,
            String releaseId) {
        jdbc.update(
            "DELETE FROM clinical_runtime_release WHERE release_id = ?",
            releaseId);
        jdbc.update(
            "DELETE FROM platform_baseline_release WHERE baseline_release_id = ?",
            baselineId(releaseId));
    }

    private static String baselineId(String releaseId) {
        return "baseline-" + releaseId;
    }
}
