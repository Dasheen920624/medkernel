package com.medkernel.engine.quality.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OPT-08 价值/ROI 指标聚合服务。
 *
 * <p>本服务只读现有关系库权威事实并实时复算，不写快照表、不造默认值；
 * 无明确事实源或无可计算样本时返回 {@link ValueMetricStatus#NOT_AVAILABLE}。
 */
@Service
public class ValueMetricsService {
    static final String FORMULA_VERSION = "OPT-08.v1";

    private final JdbcTemplate jdbc;

    public ValueMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ValueMetricSummaryResponse summary(ValueMetricFilter filter) {
        String tenantId = tenantId();
        ValueMetricFilter f = normalize(filter);
        Instant now = Instant.now();
        return new ValueMetricSummaryResponse(List.of(
            adoptionRate(tenantId, f, now),
            falsePositiveRate(tenantId, f, now),
            missedCaseRetrospective(tenantId, f, now),
            pathwayCompletionRate(tenantId, f, now),
            rectificationClosureRate(tenantId, f, now),
            insuranceViolationReduction(tenantId, f, now)
        ));
    }

    @Transactional(readOnly = true)
    public ValueMetricDrilldownResponse drilldown(
            ValueMetricCode code, ValueMetricFilter filter, int offset, int limit) {
        String tenantId = tenantId();
        ValueMetricFilter f = normalize(filter);
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        ValueMetricResponse metric = metricByCode(tenantId, f, Instant.now(), code);
        if (metric.status() == ValueMetricStatus.NOT_AVAILABLE) {
            return new ValueMetricDrilldownResponse(metric, List.of(), safeOffset, safeLimit, 0, false);
        }
        DrilldownPage page = switch (code) {
            case ADOPTION_RATE -> recommendationDrilldown(tenantId, f, safeOffset, safeLimit);
            case FALSE_POSITIVE_RATE -> falsePositiveDrilldown(tenantId, f, safeOffset, safeLimit);
            case MISSED_CASE_RETROSPECTIVE -> missedCaseDrilldown(tenantId, f, safeOffset, safeLimit);
            case PATHWAY_COMPLETION_RATE -> pathwayDrilldown(tenantId, f, safeOffset, safeLimit);
            case RECTIFICATION_CLOSURE_RATE -> rectificationDrilldown(tenantId, f, safeOffset, safeLimit);
            case INSURANCE_VIOLATION_REDUCTION -> insuranceDrilldown(tenantId, f, safeOffset, safeLimit);
        };
        return new ValueMetricDrilldownResponse(
            metric, page.items(), safeOffset, safeLimit, page.total(), safeOffset + safeLimit < page.total());
    }

    private ValueMetricResponse metricByCode(
            String tenantId, ValueMetricFilter filter, Instant now, ValueMetricCode code) {
        return switch (code) {
            case ADOPTION_RATE -> adoptionRate(tenantId, filter, now);
            case FALSE_POSITIVE_RATE -> falsePositiveRate(tenantId, filter, now);
            case MISSED_CASE_RETROSPECTIVE -> missedCaseRetrospective(tenantId, filter, now);
            case PATHWAY_COMPLETION_RATE -> pathwayCompletionRate(tenantId, filter, now);
            case RECTIFICATION_CLOSURE_RATE -> rectificationClosureRate(tenantId, filter, now);
            case INSURANCE_VIOLATION_REDUCTION -> insuranceViolationReduction(tenantId, filter, now);
        };
    }

    private ValueMetricResponse adoptionRate(String tenantId, ValueMetricFilter filter, Instant now) {
        if (filter.hasUnsupportedOrgScope()) {
            return notAvailableForScope(ValueMetricCode.ADOPTION_RATE, filter, now,
                "recommendation_card", "推荐卡闭环事实");
        }
        if (filter.hasDepartment()) {
            return notAvailable(ValueMetricCode.ADOPTION_RATE, now,
                source("recommendation_card", "推荐卡闭环事实", ValueMetricStatus.NOT_AVAILABLE,
                    "推荐卡当前无责任科室字段，无法按科室复算采纳率"),
                "推荐卡当前无责任科室字段，科室过滤下不填 0");
        }
        long accepted = countRecommendationCards(tenantId, filter, "ACCEPTED");
        long rejected = countRecommendationCards(tenantId, filter, "REJECTED");
        long dismissed = countRecommendationCards(tenantId, filter, "DISMISSED");
        long denominator = accepted + rejected + dismissed;
        return ratioMetric(ValueMetricCode.ADOPTION_RATE, accepted, denominator, now,
            source("recommendation_card", "推荐卡闭环事实", ValueMetricStatus.AVAILABLE,
                "统计 ACCEPTED / REJECTED / DISMISSED 终态推荐卡"),
            "采纳率来自真实推荐卡状态机终态");
    }

    private ValueMetricResponse falsePositiveRate(String tenantId, ValueMetricFilter filter, Instant now) {
        if (filter.hasUnsupportedOrgScope()) {
            return notAvailableForScope(ValueMetricCode.FALSE_POSITIVE_RATE, filter, now,
                "quality_finding", "质控问题闭环事实");
        }
        long waived = countQualityFindings(tenantId, filter, "WAIVED", false);
        long closed = countQualityFindings(tenantId, filter, "CLOSED", false);
        long denominator = waived + closed;
        return ratioMetric(ValueMetricCode.FALSE_POSITIVE_RATE, waived, denominator, now,
            source("quality_finding", "质控问题闭环事实", ValueMetricStatus.AVAILABLE,
                "统计 CLOSED / WAIVED 质控问题，WAIVED 视为误报豁免"),
            "误报率来自质控问题复核闭环结果");
    }

    private ValueMetricResponse missedCaseRetrospective(String tenantId, ValueMetricFilter filter, Instant now) {
        if (filter.hasUnsupportedOrgScope()) {
            return notAvailableForScope(ValueMetricCode.MISSED_CASE_RETROSPECTIVE, filter, now,
                "quality_finding", "漏报回溯质控问题");
        }
        long missed = countQualityFindings(tenantId, filter, null, true);
        BigDecimal value = BigDecimal.valueOf(missed).setScale(4, RoundingMode.HALF_UP);
        return new ValueMetricResponse(
            ValueMetricCode.MISSED_CASE_RETROSPECTIVE.name(),
            ValueMetricCode.MISSED_CASE_RETROSPECTIVE,
            ValueMetricCode.MISSED_CASE_RETROSPECTIVE.displayName(),
            ValueMetricCode.MISSED_CASE_RETROSPECTIVE.formula(),
            FORMULA_VERSION,
            ValueMetricStatus.AVAILABLE,
            BigDecimal.valueOf(missed),
            BigDecimal.valueOf(missed),
            value,
            ValueMetricCode.MISSED_CASE_RETROSPECTIVE.unit(),
            List.of(source("quality_finding", "漏报回溯质控问题", ValueMetricStatus.AVAILABLE,
                "finding_code 以 MISSED. 开头的质控问题")),
            "漏报回溯以质控问题事实编码 MISSED.* 为可追溯来源",
            now);
    }

    private ValueMetricResponse pathwayCompletionRate(String tenantId, ValueMetricFilter filter, Instant now) {
        if (filter.hasUnsupportedOrgScope()) {
            return notAvailableForScope(ValueMetricCode.PATHWAY_COMPLETION_RATE, filter, now,
                "patient_pathway", "患者路径运行事实");
        }
        if (filter.hasDepartment()) {
            return notAvailable(ValueMetricCode.PATHWAY_COMPLETION_RATE, now,
                source("patient_pathway", "患者路径运行事实", ValueMetricStatus.NOT_AVAILABLE,
                    "患者路径当前无责任科室字段，无法按科室复算路径完成率"),
                "患者路径当前无责任科室字段，科室过滤下不填 0");
        }
        long completed = countPatientPathways(tenantId, filter, "COMPLETED");
        long total = countPatientPathways(tenantId, filter, null);
        return ratioMetric(ValueMetricCode.PATHWAY_COMPLETION_RATE, completed, total, now,
            source("patient_pathway", "患者路径运行事实", ValueMetricStatus.AVAILABLE,
                "统计 patient_pathway 运行实例和 COMPLETED 终态"),
            "路径完成率来自患者路径运行状态");
    }

    private ValueMetricResponse rectificationClosureRate(String tenantId, ValueMetricFilter filter, Instant now) {
        if (filter.hasUnsupportedOrgScope()) {
            return notAvailableForScope(ValueMetricCode.RECTIFICATION_CLOSURE_RATE, filter, now,
                "rectification_task", "整改任务闭环事实");
        }
        long closed = countRectificationTasks(tenantId, filter, "CLOSED")
            + countRectificationTasks(tenantId, filter, "WAIVED");
        long total = countRectificationTasks(tenantId, filter, null);
        return ratioMetric(ValueMetricCode.RECTIFICATION_CLOSURE_RATE, closed, total, now,
            source("rectification_task", "整改任务闭环事实", ValueMetricStatus.AVAILABLE,
                "统计 CLOSED / WAIVED 整改任务占全部整改任务比例"),
            "整改闭环率来自整改任务状态机");
    }

    private ValueMetricResponse insuranceViolationReduction(
            String tenantId, ValueMetricFilter filter, Instant now) {
        if (filter.hasUnsupportedOrgScope()) {
            return notAvailableForScope(ValueMetricCode.INSURANCE_VIOLATION_REDUCTION, filter, now,
                "mk_quality_insurance_issue", "医保违规问题事实");
        }
        if (filter.from() == null || filter.to() == null || !filter.from().isBefore(filter.to())) {
            return notAvailable(ValueMetricCode.INSURANCE_VIOLATION_REDUCTION, now,
                source("mk_quality_insurance_issue", "医保违规问题事实", ValueMetricStatus.NOT_AVAILABLE,
                    "需要明确查询起止时间，才能构造等长前置基线期"),
                "医保违规减少率需要完整时间窗，不能用累计问题总量冒充改善");
        }
        Duration period = Duration.between(filter.from(), filter.to());
        ValueMetricFilter baseline = new ValueMetricFilter(
            filter.from().minus(period),
            filter.from(),
            filter.departmentId(),
            null,
            null);
        long current = countInsuranceIssues(tenantId, filter);
        long baselineCount = countInsuranceIssues(tenantId, baseline);
        if (baselineCount == 0) {
            return notAvailable(ValueMetricCode.INSURANCE_VIOLATION_REDUCTION, now,
                source("mk_quality_insurance_issue", "医保违规问题事实", ValueMetricStatus.NOT_AVAILABLE,
                    "等长前置基线期没有医保违规问题样本"),
                "基线期样本为 0，无法计算违规减少率");
        }
        return ratioMetric(
            ValueMetricCode.INSURANCE_VIOLATION_REDUCTION,
            baselineCount - current,
            baselineCount,
            now,
            source("mk_quality_insurance_issue", "医保违规问题事实", ValueMetricStatus.AVAILABLE,
                "比较查询期与等长前置基线期的真实医保审核问题数"),
            "医保违规减少率来自查询期与等长前置基线期的真实问题数比较");
    }

    private ValueMetricResponse ratioMetric(
            ValueMetricCode code,
            long numerator,
            long denominator,
            Instant now,
            ValueMetricDataSource source,
            String explanation) {
        if (denominator <= 0) {
            return notAvailable(code, now,
                new ValueMetricDataSource(source.sourceCode(), source.sourceName(), ValueMetricStatus.NOT_AVAILABLE,
                    source.evidence() + "；当前无可计算样本"),
                explanation + "；当前无可计算样本");
        }
        BigDecimal value = BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
        return new ValueMetricResponse(
            code.name(), code, code.displayName(), code.formula(), FORMULA_VERSION,
            ValueMetricStatus.AVAILABLE,
            BigDecimal.valueOf(numerator), BigDecimal.valueOf(denominator), value, code.unit(),
            List.of(source), explanation, now);
    }

    private ValueMetricResponse notAvailable(
            ValueMetricCode code, Instant now, ValueMetricDataSource source, String explanation) {
        return new ValueMetricResponse(
            code.name(), code, code.displayName(), code.formula(), FORMULA_VERSION,
            ValueMetricStatus.NOT_AVAILABLE, null, null, null, code.unit(), List.of(source), explanation, now);
    }

    private ValueMetricResponse notAvailableForScope(
            ValueMetricCode code, ValueMetricFilter filter, Instant now, String sourceCode, String sourceName) {
        String scopeLabel = filter.unsupportedOrgScopeLabel();
        return notAvailable(code, now,
            source(sourceCode, sourceName, ValueMetricStatus.NOT_AVAILABLE,
                sourceName + "当前无" + scopeLabel + "字段，无法按" + scopeLabel + "复算"),
            sourceName + "当前无" + scopeLabel + "字段，" + scopeLabel + "过滤下不填 0");
    }

    private ValueMetricDataSource source(
            String sourceCode, String sourceName, ValueMetricStatus status, String evidence) {
        return new ValueMetricDataSource(sourceCode, sourceName, status, evidence);
    }

    private long countRecommendationCards(String tenantId, ValueMetricFilter filter, String status) {
        QueryParts query = baseCount("recommendation_card", "created_at", tenantId, filter);
        query.sql().append(" AND status = ?");
        query.params().add(status);
        return count(query);
    }

    private long countPatientPathways(String tenantId, ValueMetricFilter filter, String status) {
        QueryParts query = baseCount("patient_pathway", "entered_at", tenantId, filter);
        if (status != null) {
            query.sql().append(" AND status = ?");
            query.params().add(status);
        }
        return count(query);
    }

    private long countQualityFindings(
            String tenantId, ValueMetricFilter filter, String status, boolean missedOnly) {
        QueryParts query = baseCount("quality_finding", "created_at", tenantId, filter);
        appendDepartment(query, filter, "responsible_department_id");
        if (status != null) {
            query.sql().append(" AND status = ?");
            query.params().add(status);
        }
        if (missedOnly) {
            query.sql().append(" AND finding_code LIKE 'MISSED.%'");
        }
        return count(query);
    }

    private long countRectificationTasks(String tenantId, ValueMetricFilter filter, String status) {
        QueryParts query = baseCount("rectification_task", "created_at", tenantId, filter);
        appendDepartment(query, filter, "responsible_department_id");
        if (status != null) {
            query.sql().append(" AND status = ?");
            query.params().add(status);
        }
        return count(query);
    }

    private long countInsuranceIssues(String tenantId, ValueMetricFilter filter) {
        QueryParts query = baseCount("mk_quality_insurance_issue", "created_at", tenantId, filter);
        appendDepartment(query, filter, "department_id");
        return count(query);
    }

    private QueryParts baseCount(String table, String timeColumn, String tenantId, ValueMetricFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ")
            .append(table)
            .append(" WHERE tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        appendTimeRange(sql, params, timeColumn, filter);
        return new QueryParts(sql, params);
    }

    private void appendTimeRange(
            StringBuilder sql, List<Object> params, String timeColumn, ValueMetricFilter filter) {
        if (filter.from() != null) {
            sql.append(" AND ").append(timeColumn).append(" >= ?");
            params.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND ").append(timeColumn).append(" < ?");
            params.add(Timestamp.from(filter.to()));
        }
    }

    private void appendDepartment(QueryParts query, ValueMetricFilter filter, String column) {
        if (filter.hasDepartment()) {
            query.sql().append(" AND ").append(column).append(" = ?");
            query.params().add(filter.departmentId());
        }
    }

    private long count(QueryParts query) {
        Long value = jdbc.queryForObject(query.sql().toString(), Long.class, query.params().toArray());
        return value == null ? 0L : value;
    }

    private DrilldownPage missedCaseDrilldown(String tenantId, ValueMetricFilter filter, int offset, int limit) {
        QueryParts query = baseDrilldown(
            """
            SELECT finding_id AS source_id,
                   NULL AS patient_id,
                   NULL AS encounter_id,
                   responsible_department_id AS department_id,
                   status,
                   title || '：' || evidence_summary AS reason,
                   created_at AS occurred_at,
                   trace_id
            FROM quality_finding
            WHERE tenant_id = ?
              AND finding_code LIKE 'MISSED.%'
            """,
            tenantId, "created_at", filter);
        appendDepartment(query, filter, "responsible_department_id");
        return queryDrilldown("quality_finding", query, offset, limit);
    }

    private DrilldownPage falsePositiveDrilldown(String tenantId, ValueMetricFilter filter, int offset, int limit) {
        QueryParts query = baseDrilldown(
            """
            SELECT finding_id AS source_id,
                   NULL AS patient_id,
                   NULL AS encounter_id,
                   responsible_department_id AS department_id,
                   status,
                   title || '：' || evidence_summary AS reason,
                   created_at AS occurred_at,
                   trace_id
            FROM quality_finding
            WHERE tenant_id = ?
              AND status = 'WAIVED'
            """,
            tenantId, "created_at", filter);
        appendDepartment(query, filter, "responsible_department_id");
        return queryDrilldown("quality_finding", query, offset, limit);
    }

    private DrilldownPage rectificationDrilldown(String tenantId, ValueMetricFilter filter, int offset, int limit) {
        QueryParts query = baseDrilldown(
            """
            SELECT task_id AS source_id,
                   NULL AS patient_id,
                   NULL AS encounter_id,
                   responsible_department_id AS department_id,
                   status,
                   COALESCE(rectification_summary, '整改任务未提交说明') AS reason,
                   created_at AS occurred_at,
                   trace_id
            FROM rectification_task
            WHERE tenant_id = ?
            """,
            tenantId, "created_at", filter);
        appendDepartment(query, filter, "responsible_department_id");
        return queryDrilldown("rectification_task", query, offset, limit);
    }

    private DrilldownPage recommendationDrilldown(String tenantId, ValueMetricFilter filter, int offset, int limit) {
        QueryParts query = baseDrilldown(
            """
            SELECT c.card_id AS source_id,
                   t.patient_id AS patient_id,
                   t.encounter_id AS encounter_id,
                   NULL AS department_id,
                   c.status AS status,
                   c.title || '：' || c.summary AS reason,
                   c.created_at AS occurred_at,
                   c.trace_id AS trace_id
            FROM recommendation_card c
            JOIN recommendation_trigger t ON t.trigger_id = c.trigger_id AND t.tenant_id = c.tenant_id
            WHERE c.tenant_id = ?
              AND c.status IN ('ACCEPTED','REJECTED','DISMISSED')
            """,
            tenantId, "c.created_at", filter);
        return queryDrilldown("recommendation_card", query, offset, limit);
    }

    private DrilldownPage pathwayDrilldown(String tenantId, ValueMetricFilter filter, int offset, int limit) {
        QueryParts query = baseDrilldown(
            """
            SELECT patient_pathway_id AS source_id,
                   patient_id,
                   encounter_id,
                   NULL AS department_id,
                   status,
                   COALESCE(exit_reason, current_node_code, '路径运行事实') AS reason,
                   entered_at AS occurred_at,
                   trace_id
            FROM patient_pathway
            WHERE tenant_id = ?
            """,
            tenantId, "entered_at", filter);
        return queryDrilldown("patient_pathway", query, offset, limit);
    }

    private DrilldownPage insuranceDrilldown(String tenantId, ValueMetricFilter filter, int offset, int limit) {
        QueryParts query = baseDrilldown(
            """
            SELECT issue_id AS source_id,
                   patient_id,
                   encounter_id,
                   department_id,
                   status,
                   evidence_summary AS reason,
                   created_at AS occurred_at,
                   trace_id
            FROM mk_quality_insurance_issue
            WHERE tenant_id = ?
            """,
            tenantId, "created_at", filter);
        appendDepartment(query, filter, "department_id");
        return queryDrilldown("mk_quality_insurance_issue", query, offset, limit);
    }

    private QueryParts baseDrilldown(
            String sql, String tenantId, String timeColumn, ValueMetricFilter filter) {
        StringBuilder builder = new StringBuilder(sql);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        appendTimeRange(builder, params, timeColumn, filter);
        return new QueryParts(builder, params);
    }

    private DrilldownPage queryDrilldown(String sourceType, QueryParts query, int offset, int limit) {
        String countSql = "SELECT COUNT(*) FROM (" + query.sql() + ") t";
        long total = count(new QueryParts(new StringBuilder(countSql), new ArrayList<>(query.params())));
        query.sql().append(" ORDER BY occurred_at DESC, source_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        query.params().add(offset);
        query.params().add(limit);
        List<ValueMetricDrilldownItem> items = jdbc.query(query.sql().toString(), (rs, rowNum) ->
            new ValueMetricDrilldownItem(
                sourceType,
                rs.getString("source_id"),
                rs.getString("patient_id"),
                rs.getString("encounter_id"),
                rs.getString("department_id"),
                rs.getString("status"),
                rs.getString("reason"),
                toInstant(rs.getTimestamp("occurred_at")),
                rs.getString("trace_id")),
            query.params().toArray());
        return new DrilldownPage(items, total);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String tenantId() {
        var scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private ValueMetricFilter normalize(ValueMetricFilter filter) {
        return filter == null ? new ValueMetricFilter(null, null, null, null, null) : filter;
    }

    private record QueryParts(StringBuilder sql, List<Object> params) {}
    private record DrilldownPage(List<ValueMetricDrilldownItem> items, long total) {}
}
