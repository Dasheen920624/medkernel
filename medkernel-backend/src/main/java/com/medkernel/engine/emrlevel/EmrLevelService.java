package com.medkernel.engine.emrlevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.medkernel.engine.evaluation.EmrLevelRectificationBridge;
import com.medkernel.engine.evaluation.EmrLevelRectificationBridge.EmrLevelRectificationCommand;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EMR-LEVEL-01 电子病历评级目标与差距服务。
 *
 * <p>服务只依据关系库中的标准项、证据引用和差距闭环计算进度；缺少证据时不会把能力点计为满足。
 */
@Service
public class EmrLevelService {
    private final JdbcTemplate jdbc;
    private final EmrLevelRectificationBridge rectifications;

    public EmrLevelService(JdbcTemplate jdbc, EmrLevelRectificationBridge rectifications) {
        this.jdbc = jdbc;
        this.rectifications = rectifications;
    }

    /**
     * 保存目标与映射，并为未满足项创建真实整改任务。
     */
    @Transactional
    public EmrLevelTargetResponse upsertTarget(EmrLevelTargetUpsertRequest request) {
        requireTarget(request);
        String tenantId = tenantId();
        String actor = actor();
        String traceId = traceId();
        Instant now = Instant.now();
        String targetId = targetId(tenantId, request.hospitalOrgId(), request.standardVersion());
        List<NormalizedItem> items = normalizeItems(request, targetId);
        int satisfied = (int) items.stream()
            .filter(item -> item.capabilityStatus() == EmrLevelCapabilityStatus.SATISFIED)
            .count();
        int gaps = items.size() - satisfied;
        BigDecimal progressRate = progressRate(satisfied, items.size());

        jdbc.update("DELETE FROM mk_emr_level_gap WHERE tenant_id = ? AND target_id = ?", tenantId, targetId);
        jdbc.update("DELETE FROM mk_emr_level_item WHERE tenant_id = ? AND target_id = ?", tenantId, targetId);
        jdbc.update("DELETE FROM mk_emr_level_target WHERE tenant_id = ? AND target_id = ?", tenantId, targetId);
        jdbc.update("""
            INSERT INTO mk_emr_level_target (
                target_id, tenant_id, hospital_org_id, target_level, standard_version, status,
                total_item_count, satisfied_item_count, gap_item_count, progress_rate,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            targetId, tenantId, request.hospitalOrgId(), request.targetLevel(), request.standardVersion(),
            EmrLevelTargetStatus.ACTIVE.name(), items.size(), satisfied, gaps, progressRate,
            ts(now), actor, ts(now), actor, traceId);

        for (NormalizedItem item : items) {
            insertItem(tenantId, targetId, request.standardVersion(), item, now, actor, traceId);
            if (item.capabilityStatus() != EmrLevelCapabilityStatus.SATISFIED) {
                String findingId = findingId(tenantId, targetId, item.itemCode(), item.capabilityCode());
                String taskId = taskId(tenantId, targetId, item.itemCode(), item.capabilityCode());
                insertGap(tenantId, targetId, item, taskId, now, actor, traceId);
                ensureFindingAndTask(tenantId, targetId, item, findingId, taskId, now, actor, traceId);
            }
        }
        return target(request.hospitalOrgId(), request.standardVersion());
    }

    /**
     * 查询当前目标与差距。
     */
    public EmrLevelTargetResponse target(String hospitalOrgId, String standardVersion) {
        TargetRow row = targetRow(hospitalOrgId, standardVersion);
        return new EmrLevelTargetResponse(
            row.targetId(),
            row.hospitalOrgId(),
            row.targetLevel(),
            row.standardVersion(),
            row.status(),
            row.totalItems(),
            row.satisfiedItems(),
            row.gapItems(),
            row.progressRate(),
            gapsByTarget(row.targetId()),
            row.traceId());
    }

    /**
     * 查询目标差距。
     */
    public List<EmrLevelGapResponse> gaps(String hospitalOrgId, String standardVersion) {
        return gapsByTarget(targetRow(hospitalOrgId, standardVersion).targetId());
    }

    /**
     * 查询进度，进度只由能力证据状态计算，不由整改任务关闭状态反推。
     */
    public EmrLevelProgressResponse progress(String hospitalOrgId, String standardVersion) {
        TargetRow row = targetRow(hospitalOrgId, standardVersion);
        Integer openGaps = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM mk_emr_level_gap
            WHERE tenant_id = ? AND target_id = ? AND gap_status = ?
            """, Integer.class, tenantId(), row.targetId(), EmrLevelGapStatus.OPEN.name());
        return new EmrLevelProgressResponse(
            row.targetId(),
            row.hospitalOrgId(),
            row.targetLevel(),
            row.standardVersion(),
            row.totalItems(),
            row.satisfiedItems(),
            row.gapItems(),
            openGaps == null ? 0 : openGaps,
            row.progressRate(),
            row.traceId());
    }

    private void insertItem(
            String tenantId,
            String targetId,
            String standardVersion,
            NormalizedItem item,
            Instant now,
            String actor,
            String traceId) {
        jdbc.update("""
            INSERT INTO mk_emr_level_item (
                item_id, tenant_id, target_id, standard_version, item_code, item_name,
                required_level, capability_code, capability_name, capability_status,
                evidence_ref, evidence_summary, responsible_department_id, due_at,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            item.itemId(), tenantId, targetId, standardVersion, item.itemCode(), item.itemName(),
            item.requiredLevel(), item.capabilityCode(), item.capabilityName(), item.capabilityStatus().name(),
            item.evidenceRef(), item.evidenceSummary(), item.responsibleDepartmentId(), nullableTs(item.dueAt()),
            ts(now), actor, ts(now), actor, traceId);
    }

    private void insertGap(
            String tenantId,
            String targetId,
            NormalizedItem item,
            String taskId,
            Instant now,
            String actor,
            String traceId) {
        jdbc.update("""
            INSERT INTO mk_emr_level_gap (
                gap_id, tenant_id, target_id, item_id, item_code, capability_code,
                capability_status, gap_status, gap_reason, responsible_department_id,
                due_at, rectification_task_id, evidence_ref, closed_at,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            item.gapId(), tenantId, targetId, item.itemId(), item.itemCode(), item.capabilityCode(),
            item.capabilityStatus().name(), EmrLevelGapStatus.OPEN.name(), item.gapReason(),
            item.responsibleDepartmentId(), nullableTs(item.dueAt()), taskId, item.evidenceRef(), null,
            ts(now), actor, ts(now), actor, traceId);
    }

    private void ensureFindingAndTask(
            String tenantId,
            String targetId,
            NormalizedItem item,
            String findingId,
            String taskId,
            Instant now,
            String actor,
            String traceId) {
        rectifications.ensureTask(new EmrLevelRectificationCommand(
            tenantId,
            findingId,
            taskId,
            targetId,
            item.gapId(),
            item.itemId(),
            findingCode(item),
            truncate("电子病历评级差距：" + item.itemName(), 256),
            truncate("标准项 " + item.itemCode() + " 的能力点 " + item.capabilityCode()
                + " 未满足电子病历评级目标；" + item.gapReason(), 2048),
            truncate(item.gapReason(), 2048),
            item.responsibleDepartmentId(),
            item.dueAt(),
            now,
            actor,
            traceId));
    }

    private TargetRow targetRow(String hospitalOrgId, String standardVersion) {
        if (!hasText(hospitalOrgId) || !hasText(standardVersion)) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "电子病历评级目标查询缺少机构或标准版本");
        }
        List<TargetRow> rows = jdbc.query("""
            SELECT *
            FROM mk_emr_level_target
            WHERE tenant_id = ? AND hospital_org_id = ? AND standard_version = ?
            """, this::mapTarget, tenantId(), hospitalOrgId, standardVersion);
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVAL_005, "电子病历评级目标不存在");
        }
        return rows.get(0);
    }

    private List<EmrLevelGapResponse> gapsByTarget(String targetId) {
        return jdbc.query("""
            SELECT g.gap_id, g.item_code, i.item_name, g.capability_code,
                   g.capability_status, g.gap_reason, g.rectification_task_id, g.trace_id
            FROM mk_emr_level_gap g
            JOIN mk_emr_level_item i
              ON i.tenant_id = g.tenant_id AND i.item_id = g.item_id
            WHERE g.tenant_id = ? AND g.target_id = ?
            ORDER BY i.required_level ASC, g.item_code ASC, g.capability_code ASC
            """, this::mapGap, tenantId(), targetId);
    }

    private TargetRow mapTarget(ResultSet rs, int rowNum) throws SQLException {
        return new TargetRow(
            rs.getString("target_id"),
            rs.getString("hospital_org_id"),
            rs.getInt("target_level"),
            rs.getString("standard_version"),
            EmrLevelTargetStatus.valueOf(rs.getString("status")),
            rs.getInt("total_item_count"),
            rs.getInt("satisfied_item_count"),
            rs.getInt("gap_item_count"),
            rs.getBigDecimal("progress_rate"),
            rs.getString("trace_id"));
    }

    private EmrLevelGapResponse mapGap(ResultSet rs, int rowNum) throws SQLException {
        return new EmrLevelGapResponse(
            rs.getString("gap_id"),
            rs.getString("item_code"),
            rs.getString("item_name"),
            rs.getString("capability_code"),
            EmrLevelCapabilityStatus.valueOf(rs.getString("capability_status")),
            rs.getString("gap_reason"),
            rs.getString("rectification_task_id"),
            rs.getString("trace_id"));
    }

    private List<NormalizedItem> normalizeItems(EmrLevelTargetUpsertRequest request, String targetId) {
        List<NormalizedItem> items = new ArrayList<>();
        for (EmrLevelItemAssessmentRequest item : request.items()) {
            requireItem(item);
            if (item.requiredLevel() > request.targetLevel()) {
                continue;
            }
            EmrLevelCapabilityStatus status = normalizeStatus(item);
            String tenantId = tenantId();
            String itemId = itemId(tenantId, targetId, item.itemCode(), item.capabilityCode());
            String gapId = gapId(tenantId, targetId, item.itemCode(), item.capabilityCode());
            String gapReason = gapReason(item, status);
            NormalizedItem normalized = new NormalizedItem(
                itemId,
                gapId,
                item.itemCode(),
                item.itemName(),
                item.requiredLevel(),
                item.capabilityCode(),
                item.capabilityName(),
                status,
                blankToNull(item.evidenceRef()),
                nullToDefault(item.evidenceSummary(), gapReason),
                item.responsibleDepartmentId(),
                item.dueAt(),
                gapReason);
            if (status != EmrLevelCapabilityStatus.SATISFIED) {
                requireGapClosure(normalized);
            }
            items.add(normalized);
        }
        if (items.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "电子病历评级目标缺少目标级别内标准项");
        }
        items.sort(Comparator.comparingInt(NormalizedItem::requiredLevel)
            .thenComparing(NormalizedItem::itemCode)
            .thenComparing(NormalizedItem::capabilityCode));
        return items;
    }

    private EmrLevelCapabilityStatus normalizeStatus(EmrLevelItemAssessmentRequest item) {
        if (item.capabilityStatus() == EmrLevelCapabilityStatus.SATISFIED && !hasText(item.evidenceRef())) {
            return EmrLevelCapabilityStatus.MISSING_EVIDENCE;
        }
        return item.capabilityStatus();
    }

    private String gapReason(EmrLevelItemAssessmentRequest item, EmrLevelCapabilityStatus status) {
        if (status == EmrLevelCapabilityStatus.MISSING_EVIDENCE) {
            return "标准项 " + item.itemCode() + " 声明满足但缺少证据，不能计入电子病历评级进度";
        }
        if (hasText(item.evidenceSummary())) {
            return item.evidenceSummary();
        }
        return "标准项 " + item.itemCode() + " 的能力点 " + item.capabilityCode() + " 尚未满足";
    }

    private void requireTarget(EmrLevelTargetUpsertRequest request) {
        if (request == null || !hasText(request.hospitalOrgId()) || !hasText(request.standardVersion())
                || request.targetLevel() == null || request.items() == null || request.items().isEmpty()) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "电子病历评级目标请求缺少必要字段");
        }
        if (request.targetLevel() < 4 || request.targetLevel() > 6) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "电子病历评级目标级别必须为 4、5 或 6");
        }
    }

    private void requireItem(EmrLevelItemAssessmentRequest item) {
        if (item == null || !hasText(item.itemCode()) || !hasText(item.itemName())
                || item.requiredLevel() == null || item.requiredLevel() < 4 || item.requiredLevel() > 6
                || !hasText(item.capabilityCode()) || !hasText(item.capabilityName())
                || item.capabilityStatus() == null) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "电子病历评级标准项缺少必要字段");
        }
    }

    private void requireGapClosure(NormalizedItem item) {
        if (!hasText(item.responsibleDepartmentId()) || item.dueAt() == null) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "电子病历评级差距必须指定责任部门和整改期限");
        }
    }

    private BigDecimal progressRate(int satisfied, int total) {
        if (total == 0) {
            return new BigDecimal("0.0000");
        }
        return BigDecimal.valueOf(satisfied)
            .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private String findingCode(NormalizedItem item) {
        return "EMR_LEVEL." + item.itemCode() + "." + item.capabilityCode();
    }

    private String targetId(String tenantId, String hospitalOrgId, String standardVersion) {
        return "emr-target-" + shortDigest(tenantId, hospitalOrgId, standardVersion);
    }

    private String itemId(String tenantId, String targetId, String itemCode, String capabilityCode) {
        return "emr-item-" + shortDigest(tenantId, targetId, itemCode, capabilityCode);
    }

    private String gapId(String tenantId, String targetId, String itemCode, String capabilityCode) {
        return "emr-gap-" + shortDigest(tenantId, targetId, itemCode, capabilityCode);
    }

    private String findingId(String tenantId, String targetId, String itemCode, String capabilityCode) {
        return "qf-emr-" + shortDigest(tenantId, targetId, itemCode, capabilityCode);
    }

    private String taskId(String tenantId, String targetId, String itemCode, String capabilityCode) {
        return "rct-emr-" + shortDigest(tenantId, targetId, itemCode, capabilityCode);
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

    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private Timestamp nullableTs(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String nullToDefault(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record TargetRow(
        String targetId,
        String hospitalOrgId,
        int targetLevel,
        String standardVersion,
        EmrLevelTargetStatus status,
        int totalItems,
        int satisfiedItems,
        int gapItems,
        BigDecimal progressRate,
        String traceId
    ) {
    }

    private record NormalizedItem(
        String itemId,
        String gapId,
        String itemCode,
        String itemName,
        int requiredLevel,
        String capabilityCode,
        String capabilityName,
        EmrLevelCapabilityStatus capabilityStatus,
        String evidenceRef,
        String evidenceSummary,
        String responsibleDepartmentId,
        Instant dueAt,
        String gapReason
    ) {
    }
}
