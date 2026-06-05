package com.medkernel.engine.quality.insurance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.medkernel.engine.clinical.model.ClinicalClaim;
import com.medkernel.engine.clinical.model.ClinicalClaimRepository;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.evaluation.EvaluationEngineService;
import com.medkernel.engine.evaluation.EvaluationEvaluateSnapshotRequest;
import com.medkernel.engine.evaluation.EvaluationModelStatus;
import com.medkernel.engine.evaluation.EvaluationResultLevel;
import com.medkernel.engine.evaluation.EvaluationResultRequest;
import com.medkernel.engine.evaluation.EvaluationRunRequest;
import com.medkernel.engine.evaluation.EvaluationRunResponse;
import com.medkernel.engine.evaluation.EvaluationRunType;
import com.medkernel.engine.evaluation.EvaluationSubjectType;
import com.medkernel.engine.evaluation.QualityFindingRequest;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SVC-QUALITY-02 病案医保服务包。
 *
 * <p>服务读取关系库中的上下文快照与医保结算事实，执行确定性病案内涵、DRG/DIP 与医保审核。
 * 未接入模型时仍返回 {@link EvaluationModelStatus#MODEL_DISABLED} 的真实 B0 结果；无结算事实时返回
 * {@link InsuranceAuditStatus#INSUFFICIENT_DATA}，不臆造违规。
 */
@Service
public class InsuranceQualityService {
    private final JdbcTemplate jdbc;
    private final ContextSnapshotRepository snapshots;
    private final ClinicalClaimRepository claims;
    private final EvaluationEngineService evaluations;

    public InsuranceQualityService(
            JdbcTemplate jdbc,
            ContextSnapshotRepository snapshots,
            ClinicalClaimRepository claims,
            EvaluationEngineService evaluations) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
        this.claims = claims;
        this.evaluations = evaluations;
    }

    /**
     * 查询真实医保病案问题列表，按当前租户作用域服务端分页。
     */
    @Transactional(readOnly = true)
    public PageResponse<InsuranceIssuePageItemResponse> listInsuranceIssues(
            InsuranceIssueFilter filter, PageRequest pageRequest) {
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        QueryParts query = insuranceIssueListQuery(tenantId(), filter);
        long total = count(new QueryParts(
            new StringBuilder("SELECT COUNT(*) FROM (" + query.sql() + ") t"),
            new ArrayList<>(query.params())));
        query.sql().append(" ORDER BY created_at DESC, issue_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        query.params().add(req.offset());
        query.params().add(req.safeSize());
        List<InsuranceIssuePageItemResponse> rows = jdbc.query(
            query.sql().toString(), this::mapIssuePageItem, query.params().toArray());
        return PageResponse.of(rows, req, total);
    }

    /**
     * 复用评估引擎执行病案内涵质控，并保存本服务包审计结果。
     */
    @Transactional
    public QualityCaseReviewResponse caseReview(QualityCaseReviewRequest request) {
        requireCaseReview(request);
        String tenantId = tenantId();
        ContextSnapshot snapshot = snapshot(tenantId, request.contextSnapshotId());
        String reviewId = "case-" + shortDigest(tenantId, request.contextSnapshotId(),
            request.scenarioCode(), request.packageVersion());
        List<QualityCaseReviewResponse> existing = jdbc.query("""
            SELECT * FROM mk_quality_case_review
            WHERE tenant_id = ? AND review_id = ?
            """, this::mapCaseReview, tenantId, reviewId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        EvaluationRunResponse run = evaluations.evaluateSnapshot(new EvaluationEvaluateSnapshotRequest(
            request.contextSnapshotId(), request.scenarioCode(), request.packageVersion()));
        CaseReviewStatus status = run.findingCount() > 0
            ? CaseReviewStatus.NON_COMPLIANT
            : CaseReviewStatus.PASS;
        Instant now = Instant.now();
        jdbc.update("""
            INSERT INTO mk_quality_case_review (
                review_id, tenant_id, context_snapshot_id, patient_id, encounter_id,
                department_id, scenario_code, package_version, review_status,
                evaluation_run_id, result_count, finding_count, task_count,
                model_status, model_downgrade_reason, evidence_summary,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            reviewId, tenantId, snapshot.snapshotId(), snapshot.patientId(), snapshot.encounterId(),
            request.responsibleDepartmentId(), request.scenarioCode(), request.packageVersion(),
            status.name(), run.runId(), run.resultCount(), run.findingCount(), run.taskCount(),
            run.modelStatus().name(), run.modelDowngradeReason(),
            "病案内涵质控复用评估运行 " + run.runId() + "，问题数 " + run.findingCount(),
            Timestamp.from(now), actor(), Timestamp.from(now), actor(), traceId());
        return new QualityCaseReviewResponse(
            reviewId, status, run.runId(), run.resultCount(), run.findingCount(), run.taskCount(),
            run.modelStatus(), run.modelDowngradeReason(), traceId());
    }

    /**
     * 保存 DRG/DIP 入组核对结果。
     */
    @Transactional
    public DrgGroupingResponse drgGrouping(DrgGroupingRequest request) {
        requireDrgGrouping(request);
        String tenantId = tenantId();
        ContextSnapshot snapshot = snapshot(tenantId, request.contextSnapshotId());
        String groupingId = "drg-" + shortDigest(tenantId, request.contextSnapshotId(), request.grouperVersion());
        List<DrgGroupingResponse> existing = jdbc.query("""
            SELECT * FROM mk_quality_drg_grouping
            WHERE tenant_id = ? AND grouping_id = ?
            """, this::mapDrgGrouping, tenantId, groupingId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        DrgGroupingStatus status = request.expectedGroupCode().equals(request.actualGroupCode())
            ? DrgGroupingStatus.MATCHED
            : DrgGroupingStatus.MISMATCHED;
        String explanation = "入组版本 " + request.grouperVersion()
            + "，期望 " + request.expectedGroupCode()
            + "，实际 " + request.actualGroupCode()
            + "；" + request.explanation();
        Instant now = Instant.now();
        jdbc.update("""
            INSERT INTO mk_quality_drg_grouping (
                grouping_id, tenant_id, context_snapshot_id, patient_id, encounter_id,
                department_id, grouper_version, expected_group_code, actual_group_code,
                grouping_status, explanation, evidence_summary,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            groupingId, tenantId, snapshot.snapshotId(), snapshot.patientId(), snapshot.encounterId(),
            request.responsibleDepartmentId(), request.grouperVersion(), request.expectedGroupCode(),
            request.actualGroupCode(), status.name(), explanation,
            "DRG/DIP 入组核对绑定病案快照 " + snapshot.snapshotId(),
            Timestamp.from(now), actor(), Timestamp.from(now), actor(), traceId());
        return new DrgGroupingResponse(groupingId, status, request.expectedGroupCode(),
            request.actualGroupCode(), request.grouperVersion(), explanation, traceId());
    }

    /**
     * 按版本化规则审核真实医保结算事实，命中后联动评估整改闭环。
     */
    @Transactional
    public InsuranceAuditResponse insuranceAudit(InsuranceAuditRequest request) {
        requireInsuranceAudit(request);
        String tenantId = tenantId();
        ContextSnapshot snapshot = snapshot(tenantId, request.contextSnapshotId());
        String auditId = "audit-" + shortDigest(tenantId, request.contextSnapshotId(), rulesDigest(request.rules()));
        List<ClinicalClaim> scopedClaims = claims.findByTenantIdAndPatientId(tenantId, snapshot.patientId()).stream()
            .filter(claim -> !hasText(snapshot.encounterId()) || snapshot.encounterId().equals(claim.encounterId()))
            .toList();
        if (scopedClaims.isEmpty()) {
            return new InsuranceAuditResponse(
                auditId, InsuranceAuditStatus.INSUFFICIENT_DATA, List.of(), null, 0, 0, traceId());
        }

        List<InsuranceIssueResponse> issues = new ArrayList<>();
        List<EvaluationResultRequest> results = new ArrayList<>();
        List<String> newIssueIds = new ArrayList<>();
        Instant now = Instant.now();
        for (ClinicalClaim claim : scopedClaims) {
            for (InsuranceAuditRuleRequest rule : request.rules()) {
                if (!matches(rule, claim)) {
                    continue;
                }
                String issueId = "ins-" + shortDigest(
                    tenantId, snapshot.snapshotId(), claim.claimId(), rule.ruleCode(),
                    rule.ruleVersion(), rule.issueType().name());
                List<InsuranceIssueResponse> existing = jdbc.query("""
                    SELECT * FROM mk_quality_insurance_issue
                    WHERE tenant_id = ? AND issue_id = ?
                    """, this::mapIssue, tenantId, issueId);
                InsuranceIssueResponse issue = existing.isEmpty()
                    ? insertIssue(issueId, tenantId, snapshot, claim, rule, request.responsibleDepartmentId(), now)
                    : existing.get(0);
                issues.add(issue);
                if (existing.isEmpty()) {
                    newIssueIds.add(issueId);
                    results.add(resultForIssue(request, snapshot, claim, issue, rule));
                }
            }
        }

        if (issues.isEmpty()) {
            return new InsuranceAuditResponse(auditId, InsuranceAuditStatus.NO_ISSUE, List.of(), null, 0, 0, traceId());
        }
        if (results.isEmpty()) {
            return new InsuranceAuditResponse(auditId, InsuranceAuditStatus.ISSUE_FOUND, issues, null, 0, 0, traceId());
        }

        EvaluationRunResponse run = evaluations.run(new EvaluationRunRequest(
            "INSURANCE-AUDIT-" + shortDigest(tenantId, snapshot.snapshotId(), rulesDigest(request.rules())),
            EvaluationRunType.UPSTREAM_RESULT,
            null,
            snapshot.snapshotId(),
            snapshot.patientId(),
            snapshot.encounterId(),
            request.scenarioCode(),
            request.packageVersion(),
            "sha256:" + digestHex(tenantId, snapshot.snapshotId(), rulesDigest(request.rules())),
            now,
            results));
        String placeholders = placeholders(newIssueIds.size());
        List<Object> updateArgs = new ArrayList<>();
        updateArgs.add(InsuranceIssueStatus.RECTIFICATION_CREATED.name());
        updateArgs.add(run.runId());
        updateArgs.add(Timestamp.from(now));
        updateArgs.add(actor());
        updateArgs.add(traceId());
        updateArgs.add(tenantId);
        updateArgs.add(InsuranceIssueStatus.OPEN.name());
        updateArgs.addAll(newIssueIds);
        jdbc.update("""
            UPDATE mk_quality_insurance_issue
               SET status = ?,
                   evaluation_run_id = ?,
                   updated_at = ?,
                   updated_by = ?,
                   trace_id = ?
             WHERE tenant_id = ?
               AND status = ?
               AND issue_id IN (%s)
            """.formatted(placeholders), updateArgs.toArray());
        List<Object> selectArgs = new ArrayList<>();
        selectArgs.add(tenantId);
        selectArgs.addAll(newIssueIds);
        List<InsuranceIssueResponse> refreshed = jdbc.query("""
            SELECT * FROM mk_quality_insurance_issue
            WHERE tenant_id = ? AND issue_id IN (%s)
            ORDER BY created_at ASC, issue_id ASC
            """.formatted(placeholders), this::mapIssue, selectArgs.toArray());
        return new InsuranceAuditResponse(
            auditId, InsuranceAuditStatus.ISSUE_FOUND, refreshed, run.runId(),
            run.findingCount(), run.taskCount(), run.traceId());
    }

    private InsuranceIssueResponse insertIssue(
            String issueId,
            String tenantId,
            ContextSnapshot snapshot,
            ClinicalClaim claim,
            InsuranceAuditRuleRequest rule,
            String departmentId,
            Instant now) {
        String evidence = evidenceSummary(claim, rule);
        jdbc.update("""
            INSERT INTO mk_quality_insurance_issue (
                issue_id, tenant_id, context_snapshot_id, claim_id, patient_id, encounter_id,
                department_id, issue_type, severity, status, rule_code, rule_version,
                claim_amount, threshold_amount, evidence_summary,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            issueId, tenantId, snapshot.snapshotId(), claim.claimId(), snapshot.patientId(), snapshot.encounterId(),
            departmentId, rule.issueType().name(), rule.severity().name(),
            InsuranceIssueStatus.OPEN.name(), rule.ruleCode(), rule.ruleVersion(),
            claim.totalAmount(), rule.maxAmount(), evidence,
            Timestamp.from(now), actor(), Timestamp.from(now), actor(), traceId());
        return new InsuranceIssueResponse(issueId, claim.claimId(), rule.issueType(), rule.severity(),
            InsuranceIssueStatus.OPEN, rule.ruleCode(), rule.ruleVersion(),
            claim.totalAmount(), rule.maxAmount(), evidence, traceId());
    }

    private EvaluationResultRequest resultForIssue(
            InsuranceAuditRequest request,
            ContextSnapshot snapshot,
            ClinicalClaim claim,
            InsuranceIssueResponse issue,
            InsuranceAuditRuleRequest rule) {
        QualityFindingRequest finding = new QualityFindingRequest(
            "INSURANCE." + issue.issueId(),
            "医保病案审核问题：" + rule.description(),
            "医保审核命中版本化规则 " + rule.ruleCode() + "@" + rule.ruleVersion(),
            rule.severity(),
            issue.evidenceSummary(),
            request.responsibleDepartmentId(),
            request.dueAt(),
            null);
        return new EvaluationResultRequest(
            request.indicatorId(),
            EvaluationSubjectType.CLAIM,
            claim.claimId(),
            BigDecimal.ZERO,
            EvaluationResultLevel.NON_COMPLIANT,
            false,
            issue.evidenceSummary(),
            rule.ruleCode() + "@" + rule.ruleVersion(),
            request.responsibleDepartmentId(),
            List.of(finding));
    }

    private boolean matches(InsuranceAuditRuleRequest rule, ClinicalClaim claim) {
        if (rule.maxAmount() != null && claim.totalAmount() != null
                && claim.totalAmount().compareTo(rule.maxAmount()) > 0) {
            return true;
        }
        if (hasText(rule.requiredClaimStatus())
                && !rule.requiredClaimStatus().equalsIgnoreCase(nullToBlank(claim.status()))) {
            return true;
        }
        return hasText(rule.requiredClaimType())
            && !rule.requiredClaimType().equalsIgnoreCase(nullToBlank(claim.claimType()));
    }

    private String evidenceSummary(ClinicalClaim claim, InsuranceAuditRuleRequest rule) {
        List<String> parts = new ArrayList<>();
        parts.add("结算事实 " + claim.claimId());
        parts.add("规则 " + rule.ruleCode() + "@" + rule.ruleVersion());
        if (claim.totalAmount() != null) {
            parts.add("金额 " + claim.totalAmount().toPlainString());
        }
        if (rule.maxAmount() != null) {
            parts.add("阈值 " + rule.maxAmount().toPlainString());
        }
        if (hasText(rule.requiredClaimStatus())) {
            parts.add("期望状态 " + rule.requiredClaimStatus() + "，实际 " + nullToBlank(claim.status()));
        }
        if (hasText(rule.requiredClaimType())) {
            parts.add("期望类型 " + rule.requiredClaimType() + "，实际 " + nullToBlank(claim.claimType()));
        }
        parts.add(rule.description());
        return String.join("；", parts);
    }

    private QualityCaseReviewResponse mapCaseReview(ResultSet rs, int rowNum) throws SQLException {
        return new QualityCaseReviewResponse(
            rs.getString("review_id"),
            CaseReviewStatus.valueOf(rs.getString("review_status")),
            rs.getString("evaluation_run_id"),
            rs.getInt("result_count"),
            rs.getInt("finding_count"),
            rs.getInt("task_count"),
            EvaluationModelStatus.valueOf(rs.getString("model_status")),
            rs.getString("model_downgrade_reason"),
            rs.getString("trace_id"));
    }

    private DrgGroupingResponse mapDrgGrouping(ResultSet rs, int rowNum) throws SQLException {
        return new DrgGroupingResponse(
            rs.getString("grouping_id"),
            DrgGroupingStatus.valueOf(rs.getString("grouping_status")),
            rs.getString("expected_group_code"),
            rs.getString("actual_group_code"),
            rs.getString("grouper_version"),
            rs.getString("explanation"),
            rs.getString("trace_id"));
    }

    private InsuranceIssueResponse mapIssue(ResultSet rs, int rowNum) throws SQLException {
        return new InsuranceIssueResponse(
            rs.getString("issue_id"),
            rs.getString("claim_id"),
            InsuranceIssueType.valueOf(rs.getString("issue_type")),
            com.medkernel.engine.evaluation.QualityFindingSeverity.valueOf(rs.getString("severity")),
            InsuranceIssueStatus.valueOf(rs.getString("status")),
            rs.getString("rule_code"),
            rs.getString("rule_version"),
            rs.getBigDecimal("claim_amount"),
            rs.getBigDecimal("threshold_amount"),
            rs.getString("evidence_summary"),
            rs.getString("trace_id"));
    }

    private InsuranceIssuePageItemResponse mapIssuePageItem(ResultSet rs, int rowNum) throws SQLException {
        return new InsuranceIssuePageItemResponse(
            rs.getString("issue_id"),
            rs.getString("claim_id"),
            InsuranceIssueType.valueOf(rs.getString("issue_type")),
            com.medkernel.engine.evaluation.QualityFindingSeverity.valueOf(rs.getString("severity")),
            InsuranceIssueStatus.valueOf(rs.getString("status")),
            rs.getString("rule_code"),
            rs.getString("rule_version"),
            rs.getBigDecimal("claim_amount"),
            rs.getBigDecimal("threshold_amount"),
            rs.getString("evidence_summary"),
            rs.getString("department_id"),
            rs.getString("evaluation_run_id"),
            rs.getString("trace_id"),
            toInstant(rs.getTimestamp("created_at")));
    }

    private QueryParts insuranceIssueListQuery(String tenantId, InsuranceIssueFilter filter) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT issue_id, claim_id, issue_type, severity, status, rule_code, rule_version,
                   claim_amount, threshold_amount, evidence_summary, department_id,
                   evaluation_run_id, trace_id, created_at
            FROM mk_quality_insurance_issue
            WHERE tenant_id = ?
            """), new ArrayList<>(List.of(tenantId)));
        if (filter != null) {
            if (filter.status() != null) {
                query.sql().append(" AND status = ?");
                query.params().add(filter.status().name());
            }
            if (filter.severity() != null) {
                query.sql().append(" AND severity = ?");
                query.params().add(filter.severity().name());
            }
            if (hasText(filter.departmentId())) {
                query.sql().append(" AND department_id = ?");
                query.params().add(filter.departmentId());
            }
            if (filter.from() != null) {
                query.sql().append(" AND created_at >= ?");
                query.params().add(Timestamp.from(filter.from()));
            }
            if (filter.to() != null) {
                query.sql().append(" AND created_at <= ?");
                query.params().add(Timestamp.from(filter.to()));
            }
        }
        return query;
    }

    private long count(QueryParts query) {
        Long value = jdbc.queryForObject(query.sql().toString(), Long.class, query.params().toArray());
        return value == null ? 0L : value;
    }

    private ContextSnapshot snapshot(String tenantId, String snapshotId) {
        return snapshots.findBySnapshotIdAndTenantId(snapshotId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVAL_001, "病案上下文快照不存在"));
    }

    private void requireCaseReview(QualityCaseReviewRequest request) {
        if (request == null || !hasText(request.contextSnapshotId())
                || !hasText(request.scenarioCode()) || !hasText(request.responsibleDepartmentId())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "病案内涵质控请求缺少必要字段");
        }
    }

    private void requireDrgGrouping(DrgGroupingRequest request) {
        if (request == null || !hasText(request.contextSnapshotId()) || !hasText(request.grouperVersion())
                || !hasText(request.expectedGroupCode()) || !hasText(request.actualGroupCode())
                || !hasText(request.responsibleDepartmentId()) || !hasText(request.explanation())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "DRG/DIP 入组请求缺少必要字段");
        }
    }

    private void requireInsuranceAudit(InsuranceAuditRequest request) {
        if (request == null || !hasText(request.contextSnapshotId()) || !hasText(request.scenarioCode())
                || !hasText(request.indicatorId()) || !hasText(request.responsibleDepartmentId())
                || request.dueAt() == null || request.rules() == null || request.rules().isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "医保审核请求缺少必要字段");
        }
        for (InsuranceAuditRuleRequest rule : request.rules()) {
            if (rule == null || !hasText(rule.ruleCode()) || !hasText(rule.ruleVersion())
                    || rule.issueType() == null || rule.severity() == null || !hasText(rule.description())) {
                throw new ApiException(ErrorCode.ENG_EVAL_001, "医保审核规则缺少必要字段");
            }
            if (rule.maxAmount() == null && !hasText(rule.requiredClaimStatus())
                    && !hasText(rule.requiredClaimType())) {
                throw new ApiException(ErrorCode.ENG_EVAL_001, "医保审核规则缺少确定性条件");
            }
        }
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (!hasText(tenantId)) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return hasText(traceId) ? traceId : "trace-missing";
    }

    private String rulesDigest(List<InsuranceAuditRuleRequest> rules) {
        return rules.stream()
            .map(rule -> String.join("|",
                rule.ruleCode(), rule.ruleVersion(), rule.issueType().name(), rule.severity().name(),
                nullToBlank(rule.maxAmount() == null ? null : rule.maxAmount().toPlainString()),
                nullToBlank(rule.requiredClaimStatus()), nullToBlank(rule.requiredClaimType())))
            .sorted()
            .reduce((left, right) -> left + ";" + right)
            .orElse("none");
    }

    private String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String shortDigest(String... parts) {
        return digestHex(parts).substring(0, 24).toLowerCase(Locale.ROOT);
    }

    private String digestHex(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(nullToBlank(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "SHA-256 摘要算法不可用", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record QueryParts(StringBuilder sql, List<Object> params) {}
}
