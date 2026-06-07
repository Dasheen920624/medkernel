package com.medkernel.engine.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 上下文字段目录服务（P2/P5）：合并平台派生字段（{@link ContextFieldCatalog}）与租户自定义
 * 字段（{@link ContextFieldCatalogRepository}），按当前租户隔离。平台字段优先，租户字段补充。
 */
@Service
public class ContextFieldCatalogService {

    private static final String AUDIT_TARGET_TYPE = "context_field_catalog";

    private final ContextFieldCatalog systemCatalog;
    private final ContextFieldCatalogRepository repository;
    private final AuditRecorder auditRecorder;
    private final PackageVersionPort versions;

    @Autowired
    public ContextFieldCatalogService(
        ContextFieldCatalog systemCatalog,
        ContextFieldCatalogRepository repository,
        AuditRecorder auditRecorder,
        PackageVersionPort versions) {
        this.systemCatalog = systemCatalog;
        this.repository = repository;
        this.auditRecorder = auditRecorder;
        this.versions = versions;
    }

    private static final Set<String> DATA_TYPES =
        Set.of("number", "string", "boolean", "date", "code", "list");

    /** 新增一个租户自定义字段，返回其目录条目。 */
    public ContextFieldDescriptor create(ContextFieldCatalogUpsertRequest request) {
        String tenantId = requireTenant();
        String fieldPath = request.fieldPath() == null ? "" : request.fieldPath().trim();
        ContextFieldDescriptor systemField = requireSystemField(fieldPath);
        if (repository.findByTenantIdAndFieldPath(tenantId, fieldPath).isPresent()) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "字段路径已存在：" + fieldPath);
        }
        ContextFieldCatalogEntry entry = buildEntry(
            request, systemField, tenantId, RequestContext.currentUserId().orElse("system"),
            RequestContext.currentTraceId());
        ContextFieldDescriptor saved = repository.save(entry).toDescriptor();
        recordAudit(AuditAction.CREATE, saved.fieldId(), "创建上下文字段目录覆盖", null, saved);
        return saved;
    }

    /** 更新一个租户字段覆盖项；字段路径/类型必须仍属于平台派生目录。 */
    public ContextFieldDescriptor update(String fieldId, ContextFieldCatalogUpsertRequest request) {
        String tenantId = requireTenant();
        ContextFieldCatalogEntry existing = repository.findByTenantIdAndFieldId(tenantId, fieldId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001, "字段不存在：" + fieldId));
        String fieldPath = request.fieldPath() == null ? "" : request.fieldPath().trim();
        if (!existing.fieldPath().equals(fieldPath)) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "字段路径不能修改：" + existing.fieldPath());
        }
        ContextFieldDescriptor systemField = requireSystemField(fieldPath);
        ContextFieldCatalogEntry entry = buildEntry(
            request, systemField, tenantId, existing.fieldId(), existing.id(), existing.createdAt(), existing.createdBy(),
            RequestContext.currentUserId().orElse("system"), RequestContext.currentTraceId());
        ContextFieldDescriptor before = existing.toDescriptor();
        ContextFieldDescriptor saved = repository.save(entry).toDescriptor();
        recordAudit(AuditAction.UPDATE, saved.fieldId(), "更新上下文字段目录覆盖", before, saved);
        return saved;
    }

    /** 删除（移除）一个租户自定义字段。 */
    public void delete(String fieldId) {
        String tenantId = requireTenant();
        ContextFieldCatalogEntry entry = repository.findByTenantIdAndFieldId(tenantId, fieldId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001, "字段不存在：" + fieldId));
        ContextFieldDescriptor before = entry.toDescriptor();
        repository.delete(entry);
        recordAudit(AuditAction.DELETE, fieldId, "删除上下文字段目录覆盖", before, null);
    }

    private String requireTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING, "缺少租户上下文");
        }
        return scope.tenantId();
    }

    /** 校验并构建租户字段实体（纯函数，便于单测）。 */
    static ContextFieldCatalogEntry buildEntry(
        ContextFieldCatalogUpsertRequest request, ContextFieldDescriptor systemField,
        String tenantId, String userId, String traceId) {
        return buildEntry(request, systemField, tenantId, UUID.randomUUID().toString(), null,
            null, userId, userId, traceId);
    }

    private static ContextFieldCatalogEntry buildEntry(
        ContextFieldCatalogUpsertRequest request, ContextFieldDescriptor systemField, String tenantId,
        String fieldId, Long id, Instant createdAt, String createdBy, String updatedBy, String traceId) {
        String dataType = request.dataType() == null ? "" : request.dataType().trim();
        if (!DATA_TYPES.contains(dataType)) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "数据类型非法：" + request.dataType());
        }
        String fieldPath = request.fieldPath() == null ? "" : request.fieldPath().trim();
        if (fieldPath.isBlank()) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "字段路径不能为空");
        }
        requireSame("字段路径", fieldPath, systemField.fieldPath());
        requireSame("资源类型", request.resourceType(), systemField.resourceType());
        requireSame("字段数据类型", dataType, systemField.dataType());
        Instant now = Instant.now();
        return new ContextFieldCatalogEntry(
            id, fieldId, tenantId, systemField.category(),
            systemField.group(), systemField.resourceType(), systemField.fieldPath(),
            request.displayName().trim(), systemField.dataType(), systemField.unit(),
            firstNonBlank(request.codeSystem(), systemField.codeSystem()),
            blankToNull(request.description()), "ACTIVE",
            createdAt == null ? now : createdAt, createdBy == null ? updatedBy : createdBy,
            now, updatedBy, traceId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 查询当前租户可见的字段目录（平台派生 + 本租户自定义），按资源类型/关键词过滤。 */
    public List<ContextFieldDescriptor> query(String resourceType, String keyword) {
        return query(resourceType, keyword, null);
    }

    /** 查询当前租户可见的字段目录，可选按包版本上下文校验。 */
    public List<ContextFieldDescriptor> query(String resourceType, String keyword, String packageVersion) {
        ensurePackageVersion(packageVersion);
        List<ContextFieldDescriptor> systemFields = systemCatalog.query(null, null);
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            return systemCatalog.query(resourceType, keyword);
        }
        List<ContextFieldCatalogEntry> tenantEntries =
            repository.findAllByTenantIdAndStatus(scope.tenantId(), "ACTIVE");
        return merge(systemFields, tenantEntries, resourceType, keyword);
    }

    private void ensurePackageVersion(String packageVersion) {
        String version = blankToNull(packageVersion);
        if (version == null) {
            return;
        }
        String tenantId = requireTenant();
        if (!versions.exists(tenantId, version)) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_002, "字段目录包版本不存在：" + version);
        }
    }

    /**
     * 合并平台字段与租户自定义字段（纯函数，便于单测）。平台字段优先，租户字段按相同
     * 资源类型/关键词过滤后补充（同 fieldPath 不重复加入）。
     */
    static List<ContextFieldDescriptor> merge(
        List<ContextFieldDescriptor> systemFields,
        List<ContextFieldCatalogEntry> tenantEntries,
        String resourceType,
        String keyword) {
        List<ContextFieldDescriptor> result = new ArrayList<>(systemFields);
        Map<String, Integer> indexByPath = new LinkedHashMap<>();
        for (int i = 0; i < systemFields.size(); i++) {
            indexByPath.put(systemFields.get(i).fieldPath(), i);
        }
        String type = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (ContextFieldCatalogEntry entry : tenantEntries) {
            Integer index = indexByPath.get(entry.fieldPath());
            if (index == null) {
                continue;
            }
            result.set(index, overlay(result.get(index), entry));
        }
        return result.stream()
            .filter(field -> type.isEmpty() || field.resourceType().toLowerCase(Locale.ROOT).equals(type))
            .filter(field -> kw.isEmpty() || matchesKeyword(field, kw))
            .toList();
    }

    private ContextFieldDescriptor requireSystemField(String fieldPath) {
        return systemCatalog.query(null, null).stream()
            .filter(field -> field.fieldPath().equals(fieldPath))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001,
                "字段路径不属于 canonical 字段目录：" + fieldPath));
    }

    private static ContextFieldDescriptor overlay(ContextFieldDescriptor system, ContextFieldCatalogEntry entry) {
        return new ContextFieldDescriptor(
            system.category(),
            system.group(),
            system.resourceType(),
            system.fieldPath(),
            entry.displayName(),
            system.dataType(),
            system.unit(),
            firstNonBlank(entry.codeSystem(), system.codeSystem()),
            firstNonBlank(entry.description(), system.description()),
            "TENANT",
            entry.fieldId(),
            system.derived());
    }

    private static boolean matchesKeyword(ContextFieldDescriptor field, String kw) {
        return field.fieldPath().toLowerCase(Locale.ROOT).contains(kw)
            || field.displayName().toLowerCase(Locale.ROOT).contains(kw)
            || field.category().toLowerCase(Locale.ROOT).contains(kw)
            || field.group().toLowerCase(Locale.ROOT).contains(kw)
            || (field.description() != null && field.description().toLowerCase(Locale.ROOT).contains(kw));
    }

    private static void requireSame(String label, String requested, String expected) {
        String left = requested == null ? "" : requested.trim();
        String right = expected == null ? "" : expected.trim();
        if (!left.equals(right)) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, label + "不能修改：" + expected);
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String value = blankToNull(preferred);
        return value == null ? fallback : value;
    }

    private void recordAudit(
        AuditAction action,
        String targetId,
        String summary,
        Object before,
        Object after) {
        auditRecorder.record(new AuditRecordCommand(
            action,
            AUDIT_TARGET_TYPE,
            targetId,
            summary,
            before,
            after,
            null));
    }
}
