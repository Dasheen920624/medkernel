package com.medkernel.engine.emrlevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

import com.medkernel.engine.evaluation.EmrLevelRectificationBridge;
import com.medkernel.engine.evaluation.RectificationTaskStatus;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    @MockBean AuditRecorder auditRecorder;

    @AfterEach
    void clear() {
        RequestContext.clear();
        jdbc.update("DELETE FROM mk_emr_level_evidence_package");
        jdbc.update("DELETE FROM recommendation_feedback WHERE tenant_id = 'tenant-A'");
        jdbc.update("DELETE FROM recommendation_card WHERE tenant_id = 'tenant-A'");
        jdbc.update("DELETE FROM audit_event WHERE tenant_id = 'tenant-A'");
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

    @Test
    void dataQualityAggregatesRealCoverageClosedLoopAndAuditEvidence() {
        Instant dueAt = Instant.parse("2026-06-15T00:00:00Z");
        EmrLevelTargetResponse target = withTenant("tenant-A", () -> service.upsertTarget(
            new EmrLevelTargetUpsertRequest(
                "hospital-A",
                5,
                "EMR-RATING-2026",
                List.of(
                    item("EMR-4-001", "四级临床闭环", 4, "CDSS_CLOSED_LOOP",
                        EmrLevelCapabilityStatus.SATISFIED, "recommendation:card-1", "CDSS 采纳证据",
                        "dept-it", dueAt),
                    item("EMR-5-002", "五级质控闭环", 5, "QUALITY_RECTIFICATION",
                        EmrLevelCapabilityStatus.SATISFIED, null, "声明满足但缺少证据",
                        "dept-quality", dueAt)))));
        seedCdssClosedLoopEvidence();
        jdbc.update("""
            UPDATE rectification_task
               SET status = ?, evidence_ref = ?, submitted_at = ?, closed_at = ?, updated_at = ?
             WHERE tenant_id = 'tenant-A' AND task_id = ?
            """, RectificationTaskStatus.CLOSED.name(), "rectification:evidence-1",
            java.sql.Timestamp.from(dueAt), java.sql.Timestamp.from(dueAt), java.sql.Timestamp.from(dueAt),
            target.gaps().get(0).rectificationTaskId());
        seedAuditEvent(target.targetId());

        EmrLevelDataQualityResponse response = withTenant("tenant-A",
            () -> service.dataQuality("hospital-A", "EMR-RATING-2026"));

        assertThat(response.targetId()).isEqualTo(target.targetId());
        assertThat(response.applicationCoverageRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(response.completenessRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(response.timelinessRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(response.consistencyRate()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(response.closedLoopEvidence().cdssCardCount()).isEqualTo(1);
        assertThat(response.closedLoopEvidence().cdssAcceptedCount()).isEqualTo(1);
        assertThat(response.closedLoopEvidence().qualityFindingCount()).isEqualTo(1);
        assertThat(response.closedLoopEvidence().rectificationTaskCount()).isEqualTo(1);
        assertThat(response.closedLoopEvidence().rectificationClosedCount()).isEqualTo(1);
        assertThat(response.closedLoopEvidence().auditEventCount()).isEqualTo(1);
        assertThat(response.evidenceSources())
            .extracting(EmrLevelEvidenceSourceResponse::sourceType)
            .containsExactly("CDSS_CLOSED_LOOP", "QUALITY_RECTIFICATION", "AUDIT_CHAIN");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
            .filteredOn(item -> item.itemCode().equals("EMR-5-002"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.capabilityStatus()).isEqualTo(EmrLevelCapabilityStatus.MISSING_EVIDENCE);
                assertThat(item.evidencePresent()).isFalse();
                assertThat(item.consistent()).isTrue();
                assertThat(item.gapReason()).contains("缺少证据");
                assertThat(item.rectificationTaskId()).startsWith("rct-emr-");
            });
    }

    @Test
    void exportEvidencePackagePersistsDeterministicNdjsonAndPublishesAuditOncePerIdempotencyKey() {
        Instant dueAt = Instant.parse("2026-06-15T00:00:00Z");
        EmrLevelTargetResponse target = withTenant("tenant-A", () -> service.upsertTarget(
            new EmrLevelTargetUpsertRequest(
                "hospital-A",
                5,
                "EMR-RATING-2026",
                List.of(
                    item("EMR-4-001", "四级临床闭环", 4, "CDSS_CLOSED_LOOP",
                        EmrLevelCapabilityStatus.SATISFIED, "recommendation:card-1", "CDSS 采纳证据",
                        "dept-it", dueAt),
                    item("EMR-5-002", "五级质控闭环", 5, "QUALITY_RECTIFICATION",
                        EmrLevelCapabilityStatus.GAP, null, "未接入真实质控闭环证据",
                        "dept-quality", dueAt)))));
        seedCdssClosedLoopEvidence();

        EmrLevelEvidencePackageExportRequest request =
            new EmrLevelEvidencePackageExportRequest("hospital-A", "EMR-RATING-2026", "idem-emr-2026-001");
        EmrLevelEvidencePackageExportResponse first = withTenant("tenant-A",
            () -> service.exportEvidencePackage(request));
        EmrLevelEvidencePackageExportResponse second = withTenant("tenant-A",
            () -> service.exportEvidencePackage(request));

        assertThat(first.packageId()).isEqualTo(second.packageId());
        assertThat(first.payloadSha256()).isEqualTo(second.payloadSha256());
        assertThat(first.payload()).isEqualTo(second.payload());
        assertThat(first.targetId()).isEqualTo(target.targetId());
        assertThat(first.contentType()).isEqualTo("application/x-ndjson");
        assertThat(first.fileName()).isEqualTo(target.targetId() + "-evidence-package.ndjson");
        assertThat(first.payload()).contains("\"recordType\":\"EMR_LEVEL_PACKAGE_SUMMARY\"");
        assertThat(first.payload()).contains("\"recordType\":\"EMR_LEVEL_STANDARD_ITEM\"");
        assertThat(first.payload()).contains("\"itemCode\":\"EMR-5-002\"");
        assertThat(first.payload()).contains("\"capabilityStatus\":\"GAP\"");
        assertThat(first.payload()).doesNotContain("memory://", "mock");
        assertThat(first.evidenceLineCount()).isEqualTo(6);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM mk_emr_level_evidence_package
            WHERE tenant_id = 'tenant-A'
              AND target_id = ?
              AND idempotency_key = 'idem-emr-2026-001'
              AND payload_sha256 = ?
            """, Long.class, target.targetId(), first.payloadSha256())).isEqualTo(1L);
        verify(auditRecorder).record(
            eq(AuditAction.EXPORT),
            eq("mk_emr_level_evidence_package"),
            eq(first.packageId()),
            eq("导出电子病历评级证据包 targetId=" + target.targetId()
                + " evidenceLineCount=6 sha256=" + first.payloadSha256()));
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

    private void seedCdssClosedLoopEvidence() {
        jdbc.update("""
            INSERT INTO recommendation_card (
                card_id, tenant_id, trigger_id, card_code, card_type, title, summary,
                suggested_action, risk_level, interrupt_level, status,
                requires_physician_confirmation, ai_generated, source_summary,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, 'tenant-A', ?, ?, 'QUALITY', ?, ?, ?, 'MEDIUM', 'INFO', 'ACCEPTED',
                FALSE, FALSE, ?, CURRENT_TIMESTAMP, 'qa-1', CURRENT_TIMESTAMP, 'qa-1', ?)
            """, "card-1", "trigger-1", "EMR-CDSS-001", "电子病历评级 CDSS 提醒",
            "提醒已被医师采纳", "补齐评级闭环证据", "真实推荐卡证据", "trace-cdss-1");
        jdbc.update("""
            INSERT INTO recommendation_feedback (
                feedback_id, tenant_id, card_id, feedback_type, operator_id,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, 'tenant-A', ?, 'ACCEPT', 'doctor-1',
                CURRENT_TIMESTAMP, 'doctor-1', CURRENT_TIMESTAMP, 'doctor-1', ?)
            """, "feedback-1", "card-1", "trace-cdss-1");
    }

    private void seedAuditEvent(String targetId) {
        jdbc.update("""
            INSERT INTO audit_event (
                event_id, trace_id, occurred_at, actor_user_id, action,
                resource_type, resource_id, summary, tenant_id, status
            ) VALUES (?, ?, CURRENT_TIMESTAMP, 'qa-1', 'EXPORT',
                'mk_emr_level_target', ?, '电子病历评级目标审计证据', 'tenant-A', 'RECORDED')
            """, "audit-emr-1", "trace-audit-1", targetId);
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
