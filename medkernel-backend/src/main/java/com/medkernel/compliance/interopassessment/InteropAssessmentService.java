package com.medkernel.compliance.interopassessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * OPT-05 互联互通测评映射服务。
 *
 * <p>服务只把已落库且可追溯的 EVID-01 快照或 EMR-LEVEL-02 证据导出计为达标证据；
 * 仅有映射记录但源证据不存在时，测评项保持缺证据差距。
 */
@Service
public class InteropAssessmentService {

    private final JdbcTemplate jdbc;

    public InteropAssessmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InteropAssessmentResponse assessment(String standardVersion) {
        String safeVersion = requireStandardVersion(standardVersion);
        String tenantId = tenantId();
        List<InteropAssessmentItemResponse> items = jdbc.query("""
            SELECT item_id, standard_version, dimension, item_code, item_name,
                   requirement_summary, trace_id
            FROM mk_compliance_interop_assessment_item
            WHERE tenant_id = ? AND standard_version = ? AND status = 'ACTIVE'
            ORDER BY dimension ASC, item_code ASC
            """, (rs, rowNum) -> itemResponse(tenantId, rs), tenantId, safeVersion);
        int satisfied = (int) items.stream()
            .filter(item -> item.status() == InteropAssessmentStatus.SATISFIED)
            .count();
        int missing = (int) items.stream()
            .filter(item -> item.status() == InteropAssessmentStatus.MISSING_EVIDENCE)
            .count();
        return new InteropAssessmentResponse(
            safeVersion,
            items.size(),
            satisfied,
            items.size() - satisfied,
            missing,
            rate(satisfied, items.size()),
            items,
            RequestContext.currentTraceId());
    }

    public List<InteropAssessmentItemResponse> gaps(String standardVersion) {
        return assessment(standardVersion).items().stream()
            .filter(item -> item.status() != InteropAssessmentStatus.SATISFIED)
            .toList();
    }

    private InteropAssessmentItemResponse itemResponse(String tenantId, ResultSet rs) throws SQLException {
        String itemId = rs.getString("item_id");
        List<InteropEvidenceResponse> evidences = evidences(tenantId, itemId);
        boolean sharedWithEmrLevel = evidences.stream().anyMatch(InteropEvidenceResponse::sharedWithEmrLevel);
        InteropAssessmentStatus status = evidences.isEmpty()
            ? InteropAssessmentStatus.MISSING_EVIDENCE
            : InteropAssessmentStatus.SATISFIED;
        return new InteropAssessmentItemResponse(
            itemId,
            rs.getString("standard_version"),
            InteropAssessmentDimension.valueOf(rs.getString("dimension")),
            rs.getString("item_code"),
            rs.getString("item_name"),
            rs.getString("requirement_summary"),
            status,
            evidences.size(),
            sharedWithEmrLevel,
            status == InteropAssessmentStatus.SATISFIED ? null : "缺少真实证据映射",
            evidences,
            rs.getString("trace_id"));
    }

    private List<InteropEvidenceResponse> evidences(String tenantId, String itemId) {
        return jdbc.query("""
            SELECT map_id, evidence_source_type, source_id, evidence_ref, evidence_summary, trace_id
            FROM mk_compliance_interop_evidence_map
            WHERE tenant_id = ? AND item_id = ? AND status = 'ACTIVE'
            ORDER BY evidence_source_type ASC, source_id ASC
            """, (rs, rowNum) -> evidence(tenantId, rs), tenantId, itemId)
            .stream()
            .flatMap(Optional::stream)
            .toList();
    }

    private Optional<InteropEvidenceResponse> evidence(String tenantId, ResultSet rs) throws SQLException {
        InteropEvidenceSourceType sourceType =
            InteropEvidenceSourceType.valueOf(rs.getString("evidence_source_type"));
        String mapId = rs.getString("map_id");
        String sourceId = rs.getString("source_id");
        String evidenceRef = rs.getString("evidence_ref");
        String evidenceSummary = rs.getString("evidence_summary");
        return switch (sourceType) {
            case EVIDENCE_SNAPSHOT ->
                evidenceSnapshot(tenantId, mapId, sourceId, evidenceRef, evidenceSummary);
            case EMR_LEVEL_EVIDENCE_EXPORT ->
                emrLevelExport(tenantId, mapId, sourceId, evidenceRef, evidenceSummary);
        };
    }

    private Optional<InteropEvidenceResponse> evidenceSnapshot(
            String tenantId,
            String mapId,
            String sourceId,
            String evidenceRef,
            String evidenceSummary) {
        List<InteropEvidenceResponse> rows = jdbc.query("""
            SELECT file_uri, file_digest, trace_id
            FROM evidence_snapshot
            WHERE tenant_id = ?
              AND evidence_id = ?
              AND file_uri IS NOT NULL
              AND file_digest IS NOT NULL
            """, (rs, rowNum) -> new InteropEvidenceResponse(
            mapId,
            InteropEvidenceSourceType.EVIDENCE_SNAPSHOT,
            sourceId,
            evidenceRef,
            evidenceSummary,
            rs.getString("file_uri"),
            rs.getString("file_digest"),
            false,
            rs.getString("trace_id")), tenantId, sourceId);
        return rows.stream().findFirst();
    }

    private Optional<InteropEvidenceResponse> emrLevelExport(
            String tenantId,
            String mapId,
            String sourceId,
            String evidenceRef,
            String evidenceSummary) {
        List<InteropEvidenceResponse> rows = jdbc.query("""
            SELECT payload_sha256, trace_id
            FROM mk_emr_level_evidence_export
            WHERE tenant_id = ?
              AND export_id = ?
              AND status = 'EXPORTED'
            """, (rs, rowNum) -> new InteropEvidenceResponse(
            mapId,
            InteropEvidenceSourceType.EMR_LEVEL_EVIDENCE_EXPORT,
            sourceId,
            evidenceRef,
            evidenceSummary,
            null,
            rs.getString("payload_sha256"),
            true,
            rs.getString("trace_id")), tenantId, sourceId);
        return rows.stream().findFirst();
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private String requireStandardVersion(String standardVersion) {
        if (standardVersion == null || standardVersion.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "互联互通测评标准版本不能为空");
        }
        return standardVersion.trim();
    }

    private String tenantId() {
        return RequestContext.currentOrgScope().tenantId();
    }
}
