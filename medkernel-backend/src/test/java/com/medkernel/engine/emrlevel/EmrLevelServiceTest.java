package com.medkernel.engine.emrlevel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

import com.medkernel.engine.evaluation.EmrLevelRectificationBridge;
import com.medkernel.engine.evaluation.RectificationTaskStatus;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@Import({EmrLevelService.class, EmrLevelRectificationBridge.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:emr-level-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class EmrLevelServiceTest {

    @Autowired EmrLevelService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        RequestContext.clear();
        jdbc.update("DELETE FROM mk_emr_level_gap");
        jdbc.update("DELETE FROM mk_emr_level_item");
        jdbc.update("DELETE FROM mk_emr_level_target");
        jdbc.update("DELETE FROM rectification_task WHERE finding_id LIKE 'qf-emr-%'");
        jdbc.update("DELETE FROM quality_finding WHERE finding_id LIKE 'qf-emr-%'");
    }

    @Test
    void targetMappingRequiresEvidenceAndCreatesRealRectificationTaskForGap() {
        Instant dueAt = Instant.parse("2026-06-15T00:00:00Z");

        EmrLevelTargetResponse response = withTenant("tenant-A", () -> service.upsertTarget(
            new EmrLevelTargetUpsertRequest(
                "hospital-A",
                5,
                "EMR-RATING-2026",
                List.of(
                    item("EMR-4-001", "四级临床闭环", 4, "CDSS_CLOSED_LOOP",
                        EmrLevelCapabilityStatus.SATISFIED, "audit:cdss-closed-loop", "CDSS 采纳审计可追溯",
                        "dept-it", dueAt),
                    item("EMR-5-002", "五级质控闭环", 5, "QUALITY_RECTIFICATION",
                        EmrLevelCapabilityStatus.SATISFIED, null, "前端声明满足但缺少证据",
                        "dept-quality", dueAt),
                    item("EMR-6-001", "六级区域协同", 6, "REGIONAL_INTEROP",
                        EmrLevelCapabilityStatus.GAP, null, "目标五级不应纳入六级项",
                        "dept-it", dueAt)))));

        assertThat(response.targetLevel()).isEqualTo(5);
        assertThat(response.standardVersion()).isEqualTo("EMR-RATING-2026");
        assertThat(response.totalItems()).isEqualTo(2);
        assertThat(response.satisfiedItems()).isEqualTo(1);
        assertThat(response.gapItems()).isEqualTo(1);
        assertThat(response.progressRate()).isEqualByComparingTo("0.5000");
        assertThat(response.gaps()).singleElement().satisfies(gap -> {
            assertThat(gap.itemCode()).isEqualTo("EMR-5-002");
            assertThat(gap.capabilityStatus()).isEqualTo(EmrLevelCapabilityStatus.MISSING_EVIDENCE);
            assertThat(gap.rectificationTaskId()).startsWith("rct-emr-");
            assertThat(gap.gapReason()).contains("缺少证据");
        });

        String taskId = response.gaps().get(0).rectificationTaskId();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM rectification_task
            WHERE tenant_id = 'tenant-A'
              AND task_id = ?
              AND responsible_department_id = 'dept-quality'
              AND status = 'ASSIGNED'
            """, Long.class, taskId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM quality_finding
            WHERE tenant_id = 'tenant-A'
              AND finding_id LIKE 'qf-emr-%'
              AND finding_code = 'EMR_LEVEL.EMR-5-002.QUALITY_RECTIFICATION'
              AND status = 'ASSIGNED'
            """, Long.class)).isEqualTo(1L);
    }

    @Test
    void progressRecomputesFromCapabilityEvidenceInsteadOfRectificationTaskStatus() {
        Instant dueAt = Instant.parse("2026-06-15T00:00:00Z");
        EmrLevelTargetResponse first = withTenant("tenant-A", () -> service.upsertTarget(
            new EmrLevelTargetUpsertRequest(
                "hospital-A",
                4,
                "EMR-RATING-2026",
                List.of(
                    item("EMR-4-001", "临床闭环", 4, "CDSS_CLOSED_LOOP",
                        EmrLevelCapabilityStatus.SATISFIED, "audit:cdss", "CDSS 证据",
                        "dept-it", dueAt),
                    item("EMR-4-002", "质控闭环", 4, "QUALITY_RECTIFICATION",
                        EmrLevelCapabilityStatus.GAP, null, "未接入真实质控闭环证据",
                        "dept-quality", dueAt)))));

        jdbc.update("""
            UPDATE rectification_task
               SET status = ?, closed_at = ?, updated_at = ?
             WHERE tenant_id = 'tenant-A' AND task_id = ?
            """, RectificationTaskStatus.CLOSED.name(),
            java.sql.Timestamp.from(dueAt), java.sql.Timestamp.from(dueAt),
            first.gaps().get(0).rectificationTaskId());

        EmrLevelProgressResponse progress = withTenant("tenant-A",
            () -> service.progress("hospital-A", "EMR-RATING-2026"));

        assertThat(progress.totalItems()).isEqualTo(2);
        assertThat(progress.satisfiedItems()).isEqualTo(1);
        assertThat(progress.gapItems()).isEqualTo(1);
        assertThat(progress.progressRate()).isEqualByComparingTo("0.5000");
        assertThat(progress.openGapItems()).isEqualTo(1);
    }

    private static EmrLevelItemAssessmentRequest item(
            String itemCode,
            String itemName,
            int requiredLevel,
            String capabilityCode,
            EmrLevelCapabilityStatus status,
            String evidenceRef,
            String evidenceSummary,
            String departmentId,
            Instant dueAt) {
        return new EmrLevelItemAssessmentRequest(
            itemCode,
            itemName,
            requiredLevel,
            capabilityCode,
            itemName + "能力点",
            status,
            evidenceRef,
            evidenceSummary,
            departmentId,
            dueAt);
    }

    private <T> T withTenant(String tenantId, Callable<T> action) {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-emr-level", OrgScope.tenant(tenantId), "qa-1"));
        try {
            return action.call();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
