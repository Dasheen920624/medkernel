package com.medkernel.compliance.interopassessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

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

/**
 * OPT-05 互联互通测评映射服务契约。
 */
@DataJdbcTest
@Import(InteropAssessmentService.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:interop-assessment-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class InteropAssessmentServiceTest {

    @Autowired InteropAssessmentService service;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        RequestContext.clear();
        jdbc.update("DELETE FROM mk_compliance_interop_evidence_map");
        jdbc.update("DELETE FROM mk_compliance_interop_assessment_item");
        jdbc.update("DELETE FROM mk_emr_level_evidence_export WHERE tenant_id = 'tenant-A'");
        jdbc.update("DELETE FROM evidence_snapshot WHERE tenant_id = 'tenant-A'");
    }

    @Test
    void assessmentUsesOnlyRealEvidenceAndMarksMissingItemsAsGap() {
        seedAssessmentItem("interop-item-data", "DATA_RESOURCE", "DR-001", "数据资源覆盖");
        seedAssessmentItem("interop-item-rating", "APPLICATION_EFFECT", "APP-001", "评级证据复用");
        seedAssessmentItem("interop-item-missing", "STANDARDIZATION", "STD-001", "标准化映射缺口");
        seedEvidenceSnapshot("evd-interop-data");
        seedEmrEvidenceExport("emr-export-001");
        seedEvidenceMap("map-data", "interop-item-data", "EVIDENCE_SNAPSHOT", "evd-interop-data",
            "evidence_snapshot:evd-interop-data", "EVID-01 真实存证快照");
        seedEvidenceMap("map-rating", "interop-item-rating", "EMR_LEVEL_EVIDENCE_EXPORT", "emr-export-001",
            "emr_level_export:emr-export-001", "复用 EMR-LEVEL-02 评级证据导出");

        InteropAssessmentResponse response = withTenant("tenant-A",
            () -> service.assessment("IOT-2026"));

        assertThat(response.standardVersion()).isEqualTo("IOT-2026");
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.satisfiedItems()).isEqualTo(2);
        assertThat(response.gapItems()).isEqualTo(1);
        assertThat(response.missingEvidenceItems()).isEqualTo(1);
        assertThat(response.satisfactionRate()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(response.items()).hasSize(3);
        assertThat(response.items())
            .filteredOn(item -> item.itemCode().equals("DR-001"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo(InteropAssessmentStatus.SATISFIED);
                assertThat(item.evidenceCount()).isEqualTo(1);
                assertThat(item.sharedWithEmrLevel()).isFalse();
                assertThat(item.gapReason()).isNull();
                assertThat(item.evidences()).singleElement()
                    .satisfies(evidence -> {
                        assertThat(evidence.sourceType()).isEqualTo(InteropEvidenceSourceType.EVIDENCE_SNAPSHOT);
                        assertThat(evidence.fileUri()).isEqualTo(
                            "/api/v1/compliance/evidence/snapshots/evd-interop-data/file");
                    });
            });
        assertThat(response.items())
            .filteredOn(item -> item.itemCode().equals("APP-001"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo(InteropAssessmentStatus.SATISFIED);
                assertThat(item.sharedWithEmrLevel()).isTrue();
                assertThat(item.evidences()).singleElement()
                    .satisfies(evidence -> {
                        assertThat(evidence.sourceType())
                            .isEqualTo(InteropEvidenceSourceType.EMR_LEVEL_EVIDENCE_EXPORT);
                        assertThat(evidence.payloadDigest()).isEqualTo(
                            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
                    });
            });
        assertThat(response.items())
            .filteredOn(item -> item.itemCode().equals("STD-001"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.status()).isEqualTo(InteropAssessmentStatus.MISSING_EVIDENCE);
                assertThat(item.evidenceCount()).isZero();
                assertThat(item.gapReason()).contains("缺少真实证据映射");
            });
    }

    @Test
    void gapsReturnsOnlyMissingOrUnresolvedAssessmentItems() {
        seedAssessmentItem("interop-item-data", "DATA_RESOURCE", "DR-001", "数据资源覆盖");
        seedAssessmentItem("interop-item-missing", "STANDARDIZATION", "STD-001", "标准化映射缺口");
        seedEvidenceSnapshot("evd-interop-data");
        seedEvidenceMap("map-data", "interop-item-data", "EVIDENCE_SNAPSHOT", "evd-interop-data",
            "evidence_snapshot:evd-interop-data", "EVID-01 真实存证快照");

        List<InteropAssessmentItemResponse> gaps = withTenant("tenant-A",
            () -> service.gaps("IOT-2026"));

        assertThat(gaps).singleElement().satisfies(item -> {
            assertThat(item.itemCode()).isEqualTo("STD-001");
            assertThat(item.status()).isEqualTo(InteropAssessmentStatus.MISSING_EVIDENCE);
            assertThat(item.evidences()).isEmpty();
        });
    }

    private void seedAssessmentItem(String itemId, String dimension, String itemCode, String itemName) {
        jdbc.update("""
            INSERT INTO mk_compliance_interop_assessment_item (
                item_id, tenant_id, standard_version, dimension, item_code, item_name,
                requirement_summary, owner_department_id, status, version,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, 'tenant-A', 'IOT-2026', ?, ?, ?, ?, 'dept-it', 'ACTIVE', 1,
                ?, 'auditor-1', ?, 'auditor-1', 'trace-interop')
            """, itemId, dimension, itemCode, itemName,
            "互联互通测评项 " + itemCode + " 要求映射到真实产品证据",
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")),
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")));
    }

    private void seedEvidenceMap(
            String mapId,
            String itemId,
            String sourceType,
            String sourceId,
            String evidenceRef,
            String summary) {
        jdbc.update("""
            INSERT INTO mk_compliance_interop_evidence_map (
                map_id, tenant_id, item_id, evidence_source_type, source_id,
                evidence_ref, evidence_summary, status,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, 'tenant-A', ?, ?, ?, ?, ?, 'ACTIVE',
                ?, 'auditor-1', ?, 'auditor-1', 'trace-interop-map')
            """, mapId, itemId, sourceType, sourceId, evidenceRef, summary,
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")),
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")));
    }

    private void seedEvidenceSnapshot(String evidenceId) {
        jdbc.update("""
            INSERT INTO evidence_snapshot (
                evidence_id, tenant_id, trace_id, evidence_type, action, subject_type,
                subject_id, evidence_summary, payload_snapshot, payload_hash, file_uri,
                file_digest, signature_algorithm, signature_value, signer_public_key,
                created_at, created_by, updated_at, updated_by
            ) VALUES (?, 'tenant-A', 'trace-evidence', 'COMPLIANCE_EXPORT', 'EXPORT',
                'mk_compliance_export_confirmation', 'exp-interop-001',
                '互联互通测评真实证据快照', '{"evidence":"real"}',
                'sm3:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                ?, 'sm3:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                'SM3_WITH_SM2', 'sig', 'pub',
                ?, 'auditor-1', ?, 'auditor-1')
            """, evidenceId, "/api/v1/compliance/evidence/snapshots/" + evidenceId + "/file",
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")),
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")));
    }

    private void seedEmrEvidenceExport(String exportId) {
        jdbc.update("""
            INSERT INTO mk_emr_level_evidence_export (
                export_id, tenant_id, target_id, hospital_org_id, standard_version,
                idempotency_key, status, evidence_line_count, payload_sha256, payload_ndjson,
                requested_by, created_at, created_by, updated_at, updated_by, completed_at, trace_id
            ) VALUES (?, 'tenant-A', 'emr-target-001', 'hospital-A', 'EMR-RATING-2026',
                'idem-emr-001', 'EXPORTED', 2,
                'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                '{"recordType":"EMR_LEVEL_EXPORT_SUMMARY"}',
                'qa-1', ?, 'qa-1', ?, 'qa-1', ?, 'trace-emr-export')
            """, exportId,
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")),
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")),
            Timestamp.from(Instant.parse("2026-06-05T00:00:00Z")));
    }

    private <T> T withTenant(String tenantId, Callable<T> action) {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-interop", OrgScope.tenant(tenantId), "auditor-1"));
        try {
            return action.call();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
