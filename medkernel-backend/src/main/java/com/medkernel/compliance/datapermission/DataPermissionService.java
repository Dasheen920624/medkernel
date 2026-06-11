package com.medkernel.compliance.datapermission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.security.DataAccessLevel;
import com.medkernel.shared.security.ResolvedDataScope;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.engine.org.OrgAssignmentValidator;
import com.medkernel.shared.context.RequestContext;

/**
 * SYS-06 行列数据权限策略服务。
 */
@Service
public class DataPermissionService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final String TARGET_TYPE = "mk_compliance_data_permission";
    private static final int AUDIT_SUMMARY_MAX_LENGTH = 512;

    private final DataPermissionPolicyRepository repository;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final OrgAssignmentValidator orgAssignments;

    public DataPermissionService(DataPermissionPolicyRepository repository,
                                 AuditRecorder auditRecorder,
                                 ObjectMapper objectMapper,
                                 OrgAssignmentValidator orgAssignments) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.orgAssignments = orgAssignments;
    }

    public List<DataPermissionPolicyResponse> listPolicies(
            String tenantId, String resourceType, DataPermissionAction action) {
        String safeTenant = requireTenant(tenantId);
        String normalizedResource = resourceType == null || resourceType.isBlank()
            ? null : normalizeResourceType(resourceType);
        String actionValue = action == null ? null : action.name();
        return repository.findPolicies(safeTenant, normalizedResource, actionValue).stream()
            .map(policy -> DataPermissionPolicyResponse.from(policy, readColumns(policy.allowedColumnsJson())))
            .toList();
    }

    @Transactional
    public DataPermissionPolicyResponse upsertPolicy(
            String tenantId, DataPermissionPolicyRequest request, String actor) {
        String safeTenant = requireTenant(tenantId);
        if (request.minDataLevel() == DataAccessLevel.NONE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "最小数据范围必须是科室、医院或集团");
        }
        orgAssignments.requireActiveScopeReferences(
            safeTenant,
            request.groupId(),
            request.hospitalId(),
            request.campusId(),
            request.siteId(),
            request.departmentId(),
            request.wardId(),
            request.specialtyId());
        String resourceType = normalizeResourceType(request.resourceType());
        String action = request.action().name();
        List<String> allowedColumns = normalizeColumns(request.allowedColumns());
        String allowedColumnsJson = writeColumns(allowedColumns);
        Instant now = Instant.now();
        String safeActor = safeActor(actor);

        var existing = repository.findByTenantIdAndResourceTypeAndAction(safeTenant, resourceType, action);
        if (existing.isPresent() && request.expectedVersion() != null
                && !request.expectedVersion().equals(existing.get().version())) {
            throw ApiException.conflict("数据权限策略版本冲突");
        }
        if (existing.isEmpty() && request.expectedVersion() != null) {
            throw ApiException.conflict("新建数据权限策略不能携带 expectedVersion");
        }

        DataPermissionPolicy before = existing.orElse(null);
        DataPermissionPolicy saved = repository.save(new DataPermissionPolicy(
            before == null ? null : before.id(),
            before == null ? policyId(resourceType, action) : before.policyId(),
            safeTenant,
            resourceType,
            action,
            request.minDataLevel().name(),
            allowedColumnsJson,
            blankToNull(request.groupId()),
            blankToNull(request.hospitalId()),
            blankToNull(request.campusId()),
            blankToNull(request.siteId()),
            blankToNull(request.departmentId()),
            blankToNull(request.wardId()),
            blankToNull(request.specialtyId()),
            request.status().name(),
            before == null ? 1L : before.version() + 1L,
            before == null ? now : before.createdAt(),
            before == null ? safeActor : before.createdBy(),
            now,
            safeActor,
            RequestContext.currentTraceId()));
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            TARGET_TYPE,
            saved.policyId(),
            auditSummary(saved, request.reason()),
            before,
            saved,
            null));
        return DataPermissionPolicyResponse.from(saved, allowedColumns);
    }

    public DataPermissionDecision assertAccess(ResolvedDataScope resolved, DataPermissionCheck check) {
        DataPermissionDecision decision = evaluate(resolved, check);
        if (!decision.rowAllowed()) {
            throw new ApiException(ErrorCode.DATA_SCOPE_DENIED, "行级数据权限不足");
        }
        if (!decision.deniedColumns().isEmpty()) {
            throw new ApiException(ErrorCode.DATA_SCOPE_DENIED,
                "列级数据权限不足：" + String.join(",", decision.deniedColumns()));
        }
        return decision;
    }

    public DataPermissionDecision evaluate(ResolvedDataScope resolved, DataPermissionCheck check) {
        String safeTenant = requireTenant(check.tenantId());
        String resourceType = normalizeResourceType(check.resourceType());
        DataPermissionAction action = check.action() == null ? DataPermissionAction.READ : check.action();
        DataPermissionPolicy policy = repository.findActivePolicy(safeTenant, resourceType, action.name())
            .orElseThrow(() -> new ApiException(ErrorCode.DATA_SCOPE_DENIED, "数据权限策略未配置"));
        DataAccessLevel requiredLevel = DataAccessLevel.valueOf(policy.minDataLevel());
        List<String> allowedColumns = readColumns(policy.allowedColumnsJson());
        List<String> requestedColumns = normalizeColumns(check.requestedColumns());
        List<String> deniedColumns = deniedColumns(allowedColumns, requestedColumns);
        boolean rowAllowed = resolved != null
            && levelCovers(resolved.level(), requiredLevel)
            && resolved.canAccess(safeTargetScope(safeTenant, check.targetScope()))
            && withinPolicyScope(policy, safeTargetScope(safeTenant, check.targetScope()));
        return new DataPermissionDecision(
            policy.policyId(),
            policy.resourceType(),
            action,
            requiredLevel,
            rowAllowed,
            allowedColumns,
            deniedColumns);
    }

    private String writeColumns(List<String> columns) {
        try {
            return objectMapper.writeValueAsString(columns);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "数据权限列配置序列化失败", ex);
        }
    }

    private List<String> readColumns(String json) {
        try {
            return normalizeColumns(objectMapper.readValue(json, STRING_LIST));
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "数据权限列配置不可解析", ex);
        }
    }

    private List<String> normalizeColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "数据权限策略至少需要一个允许字段");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String column : columns) {
            String value = column == null ? "" : column.trim();
            if (!value.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "非法字段名：" + value);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private List<String> deniedColumns(List<String> allowedColumns, List<String> requestedColumns) {
        Set<String> allowedKeys = allowedColumns.stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<String> denied = new ArrayList<>();
        for (String requested : requestedColumns) {
            if (!allowedKeys.contains(requested.toLowerCase(Locale.ROOT))) {
                denied.add(requested);
            }
        }
        return List.copyOf(denied);
    }

    private boolean withinPolicyScope(DataPermissionPolicy policy, OrgScope target) {
        return matches(policy.groupId(), target.groupId())
            && matches(policy.hospitalId(), target.hospitalId())
            && matches(policy.campusId(), target.campusId())
            && matches(policy.siteId(), target.siteId())
            && matches(policy.departmentId(), target.departmentId())
            && matches(policy.wardId(), target.wardId())
            && matches(policy.specialtyId(), target.specialtyId());
    }

    private boolean levelCovers(DataAccessLevel actual, DataAccessLevel required) {
        return rank(actual) >= rank(required);
    }

    private int rank(DataAccessLevel level) {
        return switch (level == null ? DataAccessLevel.NONE : level) {
            case NONE -> 0;
            case DEPARTMENT -> 1;
            case HOSPITAL -> 2;
            case GROUP -> 3;
        };
    }

    private OrgScope safeTargetScope(String tenantId, OrgScope target) {
        if (target == null) {
            return OrgScope.tenant(tenantId);
        }
        return target;
    }

    private boolean matches(String policyValue, String targetValue) {
        return policyValue == null || policyValue.isBlank()
            || (targetValue != null && policyValue.equals(targetValue));
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "")
            .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "数据权限资源类型不能为空");
        }
        if (normalized.length() > 128) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "数据权限资源类型过长");
        }
        return normalized;
    }

    private String policyId(String resourceType, String action) {
        return "dperm-" + resourceType.replace('_', '-') + "-" + action.toLowerCase(Locale.ROOT);
    }

    private String auditSummary(DataPermissionPolicy policy, String reason) {
        String summary = "更新数据权限策略：" + policy.resourceType() + "/" + policy.action();
        if (reason != null && !reason.isBlank()) {
            summary = summary + "；原因：" + reason.trim().replaceAll("\\s+", " ");
        }
        if (summary.length() <= AUDIT_SUMMARY_MAX_LENGTH) {
            return summary;
        }
        return summary.substring(0, AUDIT_SUMMARY_MAX_LENGTH - 3) + "...";
    }

    private String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId.trim();
    }

    private String safeActor(String actor) {
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        return RequestContext.currentUserId().orElse("system");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
