package com.medkernel.engine.quality.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.medkernel.engine.quality.value.ValueMetricFilter;
import com.medkernel.engine.quality.value.ValueMetricsService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SVC-QUALITY-01 质控驾驶舱聚合服务。
 *
 * <p>本服务只读 EVAL/OPT-08/整改事实形成聚合与下钻，并幂等刷新质控预警 read-model。
 */
@Service
public class QualityDashboardService {
    private static final List<String> OPEN_FINDING_STATUSES = List.of("NEW", "ASSIGNED", "REMEDIATING");

    private final JdbcTemplate jdbc;
    private final ValueMetricsService valueMetricsService;

    public QualityDashboardService(JdbcTemplate jdbc, ValueMetricsService valueMetricsService) {
        this.jdbc = jdbc;
        this.valueMetricsService = valueMetricsService;
    }

    @Transactional
    public QualityDashboardResponse dashboard(QualityDashboardFilter filter) {
        String tenantId = tenantId();
        QualityDashboardFilter f = normalize(filter);
        Instant now = Instant.now();
        refreshAlerts(tenantId, f, now);
        QualityDashboardSummary summary = summary(tenantId, f, now);
        return new QualityDashboardResponse(
            summary,
            heatmap(tenantId, f),
            valueMetricsService.summary(new ValueMetricFilter(f.from(), f.to(), f.departmentId())),
            activeAlerts(tenantId, f, 0, 20).items(),
            now);
    }

    @Transactional
    public QualityDashboardAlertsResponse alerts(QualityDashboardAlertFilter filter, int offset, int limit) {
        String tenantId = tenantId();
        QualityDashboardAlertFilter f = filter == null
            ? new QualityDashboardAlertFilter(null, null, null, null)
            : filter;
        int safeOffset = Math.max(0, offset);
        int safeLimit = safeLimit(limit);
        refreshAlerts(tenantId, f.toDashboardFilter(), Instant.now());
        return queryAlerts(tenantId, f, safeOffset, safeLimit);
    }

    @Transactional(readOnly = true)
    public QualityDashboardDrilldownResponse drilldown(
            QualityDashboardFilter filter, QualityDashboardDrilldownType type, int offset, int limit) {
        String tenantId = tenantId();
        QualityDashboardFilter f = normalize(filter);
        int safeOffset = Math.max(0, offset);
        int safeLimit = safeLimit(limit);
        QueryParts query = switch (type) {
            case FINDING -> findingDrilldown(tenantId, f);
            case RECTIFICATION -> rectificationDrilldown(tenantId, f);
            case ALERT -> alertDrilldown(tenantId, f);
        };
        DrilldownPage page = queryDrilldown(type, query, safeOffset, safeLimit);
        QualityEvidencePackage evidencePackage = new QualityEvidencePackage(
            "SVC-QUALITY-01." + type.name() + "." + safeOffset + "." + safeLimit,
            Instant.now(), page.items());
        return new QualityDashboardDrilldownResponse(
            type, page.items(), evidencePackage, safeOffset, safeLimit, page.total(),
            safeOffset + safeLimit < page.total());
    }

    private QualityDashboardSummary summary(String tenantId, QualityDashboardFilter filter, Instant now) {
        long totalFindings = countFindings(tenantId, filter, null);
        long openFindings = countFindings(tenantId, filter, OPEN_FINDING_STATUSES);
        long closedFindings = countFindings(tenantId, filter, List.of("CLOSED"));
        long waivedFindings = countFindings(tenantId, filter, List.of("WAIVED"));
        long overdueTasks = countOverdueTasks(tenantId, filter, now);
        long activeAlerts = countAlerts(tenantId, filter, QualityDashboardAlertStatus.OPEN);
        return new QualityDashboardSummary(
            totalFindings, openFindings, closedFindings, waivedFindings, overdueTasks, activeAlerts);
    }

    private List<QualityDashboardHeatmapCell> heatmap(String tenantId, QualityDashboardFilter filter) {
        long total = countFindings(tenantId, filter, null);
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT responsible_department_id AS department_id,
                   COUNT(*) AS total_findings,
                   SUM(CASE WHEN status IN ('NEW','ASSIGNED','REMEDIATING') THEN 1 ELSE 0 END) AS open_findings,
                   SUM(CASE WHEN severity IN ('P0','P1') THEN 1 ELSE 0 END) AS high_risk_findings,
                   SUM(CASE WHEN severity = 'P0' THEN 1 ELSE 0 END) AS p0_count,
                   SUM(CASE WHEN severity = 'P1' THEN 1 ELSE 0 END) AS p1_count,
                   SUM(CASE WHEN severity = 'P2' THEN 1 ELSE 0 END) AS p2_count
            FROM quality_finding
            WHERE tenant_id = ?
            """), new ArrayList<>(List.of(tenantId)));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "responsible_department_id");
        query.sql().append(" GROUP BY responsible_department_id ORDER BY total_findings DESC, department_id ASC");
        return jdbc.query(query.sql().toString(), (rs, rowNum) -> {
            long cellTotal = rs.getLong("total_findings");
            String maxSeverity = maxSeverity(rs.getLong("p0_count"), rs.getLong("p1_count"), rs.getLong("p2_count"));
            return new QualityDashboardHeatmapCell(
                rs.getString("department_id"),
                cellTotal,
                rs.getLong("open_findings"),
                rs.getLong("high_risk_findings"),
                total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(cellTotal)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP),
                maxSeverity,
                heatToken(maxSeverity));
        }, query.params().toArray());
    }

    private QualityDashboardAlertsResponse activeAlerts(
            String tenantId, QualityDashboardFilter filter, int offset, int limit) {
        return queryAlerts(tenantId,
            new QualityDashboardAlertFilter(filter.from(), filter.to(), filter.departmentId(),
                QualityDashboardAlertStatus.OPEN),
            offset, limit);
    }

    private void refreshAlerts(String tenantId, QualityDashboardFilter filter, Instant now) {
        resolveInactiveAlerts(tenantId, now);
        refreshHighRiskFindingAlerts(tenantId, filter, now);
        refreshOverdueTaskAlerts(tenantId, filter, now);
    }

    private void refreshHighRiskFindingAlerts(String tenantId, QualityDashboardFilter filter, Instant now) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT finding_id AS source_id,
                   responsible_department_id AS department_id,
                   severity,
                   title,
                   evidence_summary,
                   created_at,
                   trace_id
            FROM quality_finding
            WHERE tenant_id = ?
              AND severity IN ('P0','P1')
              AND status IN ('NEW','ASSIGNED','REMEDIATING')
            """), new ArrayList<>(List.of(tenantId)));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "responsible_department_id");
        jdbc.query(query.sql().toString(), rs -> {
            String severity = rs.getString("severity");
            upsertAlert(tenantId, QualityDashboardAlertType.HIGH_RISK_FINDING,
                "quality_finding", rs.getString("source_id"), rs.getString("department_id"), severity,
                "OPEN_P0_P1_FINDING", BigDecimal.ZERO, BigDecimal.ONE,
                "高风险质控问题待闭环：" + rs.getString("title"),
                rs.getString("evidence_summary"), toInstant(rs.getTimestamp("created_at")),
                now, rs.getString("trace_id"));
        }, query.params().toArray());
    }

    private void refreshOverdueTaskAlerts(String tenantId, QualityDashboardFilter filter, Instant now) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT task_id AS source_id,
                   responsible_department_id AS department_id,
                   status AS severity,
                   rectification_summary,
                   evidence_ref,
                   created_at,
                   trace_id
            FROM rectification_task
            WHERE tenant_id = ?
              AND status IN ('ASSIGNED','SUBMITTED','RETURNED')
              AND due_at < ?
            """), new ArrayList<>(List.of(tenantId, Timestamp.from(now))));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "responsible_department_id");
        jdbc.query(query.sql().toString(), rs -> {
            upsertAlert(tenantId, QualityDashboardAlertType.OVERDUE_RECTIFICATION,
                "rectification_task", rs.getString("source_id"), rs.getString("department_id"),
                rs.getString("severity"), "RECTIFICATION_DUE_AT", BigDecimal.ZERO, BigDecimal.ONE,
                "整改任务逾期未闭环：" + rs.getString("source_id"),
                coalesce(rs.getString("rectification_summary"), rs.getString("evidence_ref")),
                toInstant(rs.getTimestamp("created_at")), now, rs.getString("trace_id"));
        },
            query.params().toArray());
    }

    private void upsertAlert(
            String tenantId,
            QualityDashboardAlertType type,
            String sourceType,
            String sourceId,
            String departmentId,
            String severity,
            String thresholdCode,
            BigDecimal thresholdValue,
            BigDecimal actualValue,
            String title,
            String evidenceSummary,
            Instant occurredAt,
            Instant now,
            String traceId) {
        String alertId = type.name() + ":" + sourceType + ":" + sourceId;
        int updated = jdbc.update("""
            UPDATE mk_quality_dashboard_alert
               SET department_id = ?,
                   severity = ?,
                   status = 'OPEN',
                   threshold_value = ?,
                   actual_value = ?,
                   title = ?,
                   evidence_summary = ?,
                   updated_at = ?,
                   updated_by = ?,
                   trace_id = ?
             WHERE tenant_id = ?
               AND alert_type = ?
               AND source_type = ?
               AND source_id = ?
            """,
            departmentId, severity, thresholdValue, actualValue, title, evidenceSummary,
            Timestamp.from(now), actor(), traceId, tenantId, type.name(), sourceType, sourceId);
        if (updated > 0) {
            return;
        }
        jdbc.update("""
            INSERT INTO mk_quality_dashboard_alert (
                alert_id, tenant_id, department_id, alert_type, source_type, source_id, severity, status,
                threshold_code, threshold_value, actual_value, title, evidence_summary,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            alertId, tenantId, departmentId, type.name(), sourceType, sourceId, severity,
            thresholdCode, thresholdValue, actualValue, title, evidenceSummary,
            Timestamp.from(occurredAt == null ? now : occurredAt), actor(), Timestamp.from(now), actor(), traceId);
    }

    private void resolveInactiveAlerts(String tenantId, Instant now) {
        jdbc.update("""
            UPDATE mk_quality_dashboard_alert a
               SET status = 'RESOLVED',
                   updated_at = ?,
                   updated_by = ?,
                   evidence_summary = COALESCE(a.evidence_summary, '') || '；来源问题已闭环'
             WHERE a.tenant_id = ?
               AND a.alert_type = 'HIGH_RISK_FINDING'
               AND a.status <> 'RESOLVED'
               AND NOT EXISTS (
                   SELECT 1
                     FROM quality_finding f
                    WHERE f.tenant_id = a.tenant_id
                      AND f.finding_id = a.source_id
                      AND f.severity IN ('P0','P1')
                      AND f.status IN ('NEW','ASSIGNED','REMEDIATING')
               )
            """, Timestamp.from(now), actor(), tenantId);
        jdbc.update("""
            UPDATE mk_quality_dashboard_alert a
               SET status = 'RESOLVED',
                   updated_at = ?,
                   updated_by = ?,
                   evidence_summary = COALESCE(a.evidence_summary, '') || '；来源整改已闭环'
             WHERE a.tenant_id = ?
               AND a.alert_type = 'OVERDUE_RECTIFICATION'
               AND a.status <> 'RESOLVED'
               AND NOT EXISTS (
                   SELECT 1
                     FROM rectification_task t
                    WHERE t.tenant_id = a.tenant_id
                      AND t.task_id = a.source_id
                      AND t.status IN ('ASSIGNED','SUBMITTED','RETURNED')
                      AND t.due_at < ?
               )
            """, Timestamp.from(now), actor(), tenantId, Timestamp.from(now));
    }

    private long countFindings(String tenantId, QualityDashboardFilter filter, List<String> statuses) {
        QueryParts query = new QueryParts(new StringBuilder(
            "SELECT COUNT(*) FROM quality_finding WHERE tenant_id = ?"), new ArrayList<>(List.of(tenantId)));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "responsible_department_id");
        if (statuses != null && !statuses.isEmpty()) {
            query.sql().append(" AND status IN (").append(placeholders(statuses.size())).append(")");
            query.params().addAll(statuses);
        }
        return count(query);
    }

    private long countOverdueTasks(String tenantId, QualityDashboardFilter filter, Instant now) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT COUNT(*)
            FROM rectification_task
            WHERE tenant_id = ?
              AND status IN ('ASSIGNED','SUBMITTED','RETURNED')
              AND due_at < ?
            """), new ArrayList<>(List.of(tenantId, Timestamp.from(now))));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "responsible_department_id");
        return count(query);
    }

    private long countAlerts(
            String tenantId, QualityDashboardFilter filter, QualityDashboardAlertStatus status) {
        QueryParts query = alertListQuery(tenantId,
            new QualityDashboardAlertFilter(filter.from(), filter.to(), filter.departmentId(), status));
        return count(new QueryParts(new StringBuilder("SELECT COUNT(*) FROM (" + query.sql() + ") t"), query.params()));
    }

    private QualityDashboardAlertsResponse queryAlerts(
            String tenantId, QualityDashboardAlertFilter filter, int offset, int limit) {
        QueryParts query = alertListQuery(tenantId, filter);
        long total = count(new QueryParts(new StringBuilder("SELECT COUNT(*) FROM (" + query.sql() + ") t"),
            new ArrayList<>(query.params())));
        query.sql().append(" ORDER BY updated_at DESC, alert_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        query.params().add(offset);
        query.params().add(limit);
        List<QualityDashboardAlertResponse> items = jdbc.query(query.sql().toString(), (rs, rowNum) -> alertResponse(rs),
            query.params().toArray());
        return new QualityDashboardAlertsResponse(items, offset, limit, total, offset + limit < total);
    }

    private QueryParts alertListQuery(String tenantId, QualityDashboardAlertFilter filter) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT alert_id, alert_type, status, department_id, source_type, source_id, severity,
                   threshold_code, threshold_value, actual_value, title, evidence_summary,
                   created_at, updated_at, trace_id
            FROM mk_quality_dashboard_alert
            WHERE tenant_id = ?
            """), new ArrayList<>(List.of(tenantId)));
        appendTimeRange(query.sql(), query.params(), "created_at",
            new QualityDashboardFilter(filter.from(), filter.to(), filter.departmentId()));
        appendDepartment(query.sql(), query.params(), filter.toDashboardFilter(), "department_id");
        if (filter.status() != null) {
            query.sql().append(" AND status = ?");
            query.params().add(filter.status().name());
        }
        return query;
    }

    private QueryParts findingDrilldown(String tenantId, QualityDashboardFilter filter) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT 'quality_finding' AS source_type,
                   finding_id AS source_id,
                   responsible_department_id AS department_id,
                   severity,
                   status,
                   title,
                   evidence_summary,
                   created_at AS occurred_at,
                   trace_id
            FROM quality_finding
            WHERE tenant_id = ?
            """), new ArrayList<>(List.of(tenantId)));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "responsible_department_id");
        return query;
    }

    private QueryParts rectificationDrilldown(String tenantId, QualityDashboardFilter filter) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT 'rectification_task' AS source_type,
                   task_id AS source_id,
                   responsible_department_id AS department_id,
                   status AS severity,
                   status,
                   '整改任务 ' || task_id AS title,
                   COALESCE(rectification_summary, evidence_ref) AS evidence_summary,
                   created_at AS occurred_at,
                   trace_id
            FROM rectification_task
            WHERE tenant_id = ?
            """), new ArrayList<>(List.of(tenantId)));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "responsible_department_id");
        return query;
    }

    private QueryParts alertDrilldown(String tenantId, QualityDashboardFilter filter) {
        QueryParts query = new QueryParts(new StringBuilder("""
            SELECT 'mk_quality_dashboard_alert' AS source_type,
                   alert_id AS source_id,
                   department_id,
                   severity,
                   status,
                   title,
                   evidence_summary,
                   created_at AS occurred_at,
                   trace_id
            FROM mk_quality_dashboard_alert
            WHERE tenant_id = ?
            """), new ArrayList<>(List.of(tenantId)));
        appendTimeRange(query.sql(), query.params(), "created_at", filter);
        appendDepartment(query.sql(), query.params(), filter, "department_id");
        return query;
    }

    private DrilldownPage queryDrilldown(
            QualityDashboardDrilldownType type, QueryParts query, int offset, int limit) {
        long total = count(new QueryParts(new StringBuilder("SELECT COUNT(*) FROM (" + query.sql() + ") t"),
            new ArrayList<>(query.params())));
        query.sql().append(" ORDER BY occurred_at DESC, source_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        query.params().add(offset);
        query.params().add(limit);
        List<QualityDashboardDrilldownItem> items = jdbc.query(query.sql().toString(), (rs, rowNum) ->
            new QualityDashboardDrilldownItem(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("department_id"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("title"),
                rs.getString("evidence_summary"),
                toInstant(rs.getTimestamp("occurred_at")),
                rs.getString("trace_id")),
            query.params().toArray());
        return new DrilldownPage(type, items, total);
    }

    private void appendTimeRange(
            StringBuilder sql, List<Object> params, String timeColumn, QualityDashboardFilter filter) {
        if (filter.from() != null) {
            sql.append(" AND ").append(timeColumn).append(" >= ?");
            params.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND ").append(timeColumn).append(" < ?");
            params.add(Timestamp.from(filter.to()));
        }
    }

    private void appendDepartment(
            StringBuilder sql, List<Object> params, QualityDashboardFilter filter, String column) {
        if (filter.hasDepartment()) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(filter.departmentId());
        }
    }

    private long count(QueryParts query) {
        Long value = jdbc.queryForObject(query.sql().toString(), Long.class, query.params().toArray());
        return value == null ? 0L : value;
    }

    private String maxSeverity(long p0, long p1, long p2) {
        if (p0 > 0) {
            return "P0";
        }
        if (p1 > 0) {
            return "P1";
        }
        if (p2 > 0) {
            return "P2";
        }
        return "P3";
    }

    private String heatToken(String severity) {
        return switch (severity) {
            case "P0" -> "QUALITY_HEAT_CRITICAL";
            case "P1" -> "QUALITY_HEAT_HIGH";
            case "P2" -> "QUALITY_HEAT_MEDIUM";
            default -> "QUALITY_HEAT_LOW";
        };
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : Math.min(limit, 200);
    }

    private QualityDashboardAlertResponse alertResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QualityDashboardAlertResponse(
            rs.getString("alert_id"),
            QualityDashboardAlertType.valueOf(rs.getString("alert_type")),
            QualityDashboardAlertStatus.valueOf(rs.getString("status")),
            rs.getString("department_id"),
            rs.getString("source_type"),
            rs.getString("source_id"),
            rs.getString("severity"),
            rs.getString("threshold_code"),
            rs.getBigDecimal("threshold_value"),
            rs.getBigDecimal("actual_value"),
            rs.getString("title"),
            rs.getString("evidence_summary"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            rs.getString("trace_id"));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private String coalesce(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String tenantId() {
        var scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private QualityDashboardFilter normalize(QualityDashboardFilter filter) {
        return filter == null ? new QualityDashboardFilter(null, null, null) : filter;
    }

    private record QueryParts(StringBuilder sql, List<Object> params) {}
    private record DrilldownPage(
        QualityDashboardDrilldownType type,
        List<QualityDashboardDrilldownItem> items,
        long total
    ) {}
}
