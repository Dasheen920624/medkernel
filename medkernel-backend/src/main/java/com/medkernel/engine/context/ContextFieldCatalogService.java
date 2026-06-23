package com.medkernel.engine.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 上下文字段目录服务：合并平台派生字段（{@link ContextFieldCatalog}）与租户自定义
 * 字段（{@link ContextFieldCatalogRepository}），按当前租户隔离。平台字段优先，租户字段补充。
 */
@Service
public class ContextFieldCatalogService {

    private static final String AUDIT_TARGET_TYPE = "context_field_catalog";
    private static final String EXTENSION_RESOURCE_TYPE = "Extension";
    private static final Pattern EXTENSION_FIELD_PATH = Pattern.compile(
        "^extensions\\.local\\.[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){0,2}$");
    private final ContextFieldCatalog systemCatalog;
    private final ContextFieldCatalogRepository repository;
    private final AuditRecorder auditRecorder;

    public ContextFieldCatalogService(
        ContextFieldCatalog systemCatalog,
        ContextFieldCatalogRepository repository,
        AuditRecorder auditRecorder) {
        this.systemCatalog = systemCatalog;
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    private static final Set<String> DATA_TYPES =
        Set.of("number", "string", "boolean", "date", "code", "list");

    /** 新增一个租户自定义字段，返回其目录条目。 */
    public ContextFieldDescriptor create(ContextFieldCatalogUpsertRequest request) {
        String tenantId = requireTenant();
        String fieldPath = request.fieldPath() == null ? "" : request.fieldPath().trim();
        ContextFieldDescriptor systemField = catalogBase(fieldPath);
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
        ContextFieldDescriptor systemField = catalogBase(fieldPath);
        if (systemField == null) {
            systemField = existing.toDescriptor();
        }
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
        Instant now = Instant.now();
        if (isExtensionPath(fieldPath)) {
            return buildExtensionEntry(
                request, systemField, tenantId, fieldId, id, createdAt, createdBy, updatedBy, traceId, now, dataType);
        }
        if (systemField == null) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001,
                "字段路径不属于 canonical 或 extensions.local 字段目录：" + fieldPath);
        }
        requireSame("字段路径", fieldPath, systemField.fieldPath());
        requireSame("资源类型", request.resourceType(), systemField.resourceType());
        requireSame("字段数据类型", dataType, systemField.dataType());
        return new ContextFieldCatalogEntry(
            id, fieldId, tenantId, systemField.category(),
            systemField.group(), systemField.resourceType(), systemField.fieldPath(),
            request.displayName().trim(), systemField.dataType(), systemField.unit(),
            firstNonBlank(request.codeSystem(), systemField.codeSystem()),
            blankToNull(request.description()), "ACTIVE",
            createdAt == null ? now : createdAt, createdBy == null ? updatedBy : createdBy,
            now, updatedBy, traceId);
    }

    private static ContextFieldCatalogEntry buildExtensionEntry(
            ContextFieldCatalogUpsertRequest request,
            ContextFieldDescriptor existing,
            String tenantId,
            String fieldId,
            Long id,
            Instant createdAt,
            String createdBy,
            String updatedBy,
            String traceId,
            Instant now,
            String dataType) {
        requireSame("扩展字段资源类型", request.resourceType(), EXTENSION_RESOURCE_TYPE);
        if (existing != null) {
            requireSame("字段路径", request.fieldPath().trim(), existing.fieldPath());
            requireSame("字段数据类型", dataType, existing.dataType());
        }
        String category = requireValue(request.category(), "扩展字段业务域不能为空");
        String group = requireValue(request.group(), "扩展字段分组不能为空");
        String displayName = requireValue(request.displayName(), "扩展字段展示名不能为空");
        String unit = blankToNull(request.unit());
        String codeSystem = blankToNull(request.codeSystem());
        if (!"number".equals(dataType) && unit != null) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "只有 number 扩展字段可以配置单位");
        }
        if ("code".equals(dataType) && codeSystem == null) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "code 扩展字段必须绑定标准字典");
        }
        if (!"code".equals(dataType) && codeSystem != null) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "只有 code 扩展字段可以绑定标准字典");
        }
        return new ContextFieldCatalogEntry(
            id,
            fieldId,
            tenantId,
            category,
            group,
            EXTENSION_RESOURCE_TYPE,
            request.fieldPath().trim(),
            displayName,
            dataType,
            unit,
            codeSystem,
            blankToNull(request.description()),
            "ACTIVE",
            createdAt == null ? now : createdAt,
            createdBy == null ? updatedBy : createdBy,
            now,
            updatedBy,
            traceId
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 查询当前租户可见的字段目录（平台派生 + 本租户自定义），按资源类型/关键词过滤。 */
    public List<ContextFieldDescriptor> query(String resourceType, String keyword) {
        List<ContextFieldDescriptor> systemFields = systemCatalog.query(null, null);
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            return filter(systemFields, resourceType, keyword);
        }
        List<ContextFieldCatalogEntry> tenantEntries =
            repository.findAllByTenantIdAndStatus(scope.tenantId(), "ACTIVE");
        return merge(systemFields, tenantEntries, resourceType, keyword);
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
                if (isExtensionEntry(entry)) {
                    indexByPath.put(entry.fieldPath(), result.size());
                    result.add(entry.toDescriptor());
                }
                continue;
            }
            result.set(index, overlay(result.get(index), entry));
        }
        return result.stream()
            .filter(field -> type.isEmpty() || field.resourceType().toLowerCase(Locale.ROOT).equals(type))
            .filter(field -> kw.isEmpty() || matchesKeyword(field, kw))
            .toList();
    }

    private static List<ContextFieldDescriptor> filter(
            List<ContextFieldDescriptor> fields,
            String resourceType,
            String keyword) {
        String type = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return fields.stream()
            .filter(field -> type.isEmpty() || field.resourceType().toLowerCase(Locale.ROOT).equals(type))
            .filter(field -> kw.isEmpty() || matchesKeyword(field, kw))
            .toList();
    }

    private ContextFieldDescriptor catalogBase(String fieldPath) {
        if (isExtensionPath(fieldPath)) {
            return null;
        }
        return systemCatalog.query(null, null).stream()
            .filter(field -> field.fieldPath().equals(fieldPath))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001,
                "字段路径不属于 canonical 或 extensions.local 字段目录：" + fieldPath));
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

    private static boolean isExtensionEntry(ContextFieldCatalogEntry entry) {
        return entry != null
            && EXTENSION_RESOURCE_TYPE.equals(entry.resourceType())
            && isExtensionPath(entry.fieldPath())
            && DATA_TYPES.contains(entry.dataType());
    }

    private static boolean isExtensionPath(String fieldPath) {
        return fieldPath != null && EXTENSION_FIELD_PATH.matcher(fieldPath.trim()).matches();
    }

    private static String requireValue(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, message);
        }
        return normalized;
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
