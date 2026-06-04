package com.medkernel.engine.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 上下文字段目录服务（P2/P5）：合并系统派生字段（{@link ContextFieldCatalog}）与租户自定义
 * 字段（{@link ContextFieldCatalogRepository}），按当前租户隔离。系统字段优先，租户字段补充。
 */
@Service
public class ContextFieldCatalogService {

    private final ContextFieldCatalog systemCatalog;
    private final ContextFieldCatalogRepository repository;

    public ContextFieldCatalogService(
        ContextFieldCatalog systemCatalog, ContextFieldCatalogRepository repository) {
        this.systemCatalog = systemCatalog;
        this.repository = repository;
    }

    private static final Set<String> DATA_TYPES =
        Set.of("number", "string", "boolean", "date", "code", "list");

    /** 新增一个租户自定义字段，返回其目录条目。 */
    public ContextFieldDescriptor create(ContextFieldCatalogUpsertRequest request) {
        String tenantId = requireTenant();
        String fieldPath = request.fieldPath() == null ? "" : request.fieldPath().trim();
        if (repository.findByTenantIdAndFieldPath(tenantId, fieldPath).isPresent()) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "字段路径已存在：" + fieldPath);
        }
        ContextFieldCatalogEntry entry = buildEntry(
            request, tenantId, RequestContext.currentUserId().orElse("system"),
            RequestContext.currentTraceId());
        return repository.save(entry).toDescriptor();
    }

    /** 删除（移除）一个租户自定义字段。 */
    public void delete(String fieldId) {
        String tenantId = requireTenant();
        ContextFieldCatalogEntry entry = repository.findByTenantIdAndFieldId(tenantId, fieldId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001, "字段不存在：" + fieldId));
        repository.delete(entry);
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
        ContextFieldCatalogUpsertRequest request, String tenantId, String userId, String traceId) {
        String dataType = request.dataType() == null ? "" : request.dataType().trim();
        if (!DATA_TYPES.contains(dataType)) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "数据类型非法：" + request.dataType());
        }
        String fieldPath = request.fieldPath() == null ? "" : request.fieldPath().trim();
        if (fieldPath.isBlank()) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_001, "字段路径不能为空");
        }
        Instant now = Instant.now();
        return new ContextFieldCatalogEntry(
            null, UUID.randomUUID().toString(), tenantId, request.category().trim(),
            request.group().trim(), request.resourceType().trim(), fieldPath,
            request.displayName().trim(), dataType, blankToNull(request.unit()),
            blankToNull(request.codeSystem()), blankToNull(request.description()), "ACTIVE",
            now, userId, now, userId, traceId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 查询当前租户可见的字段目录（系统派生 + 本租户自定义），按资源类型/关键词过滤。 */
    public List<ContextFieldDescriptor> query(String resourceType, String keyword) {
        List<ContextFieldDescriptor> systemFields = systemCatalog.query(resourceType, keyword);
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            return systemFields;
        }
        List<ContextFieldCatalogEntry> tenantEntries =
            repository.findAllByTenantIdAndStatus(scope.tenantId(), "ACTIVE");
        return merge(systemFields, tenantEntries, resourceType, keyword);
    }

    /**
     * 合并系统字段与租户自定义字段（纯函数，便于单测）。系统字段优先，租户字段按相同
     * 资源类型/关键词过滤后补充（同 fieldPath 不重复加入）。
     */
    static List<ContextFieldDescriptor> merge(
        List<ContextFieldDescriptor> systemFields,
        List<ContextFieldCatalogEntry> tenantEntries,
        String resourceType,
        String keyword) {
        List<ContextFieldDescriptor> result = new ArrayList<>(systemFields);
        Set<String> seen = new LinkedHashSet<>();
        for (ContextFieldDescriptor field : systemFields) {
            seen.add(field.fieldPath());
        }
        String type = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (ContextFieldCatalogEntry entry : tenantEntries) {
            if (seen.contains(entry.fieldPath())) {
                continue;
            }
            if (!type.isEmpty() && !entry.resourceType().toLowerCase(Locale.ROOT).equals(type)) {
                continue;
            }
            if (!kw.isEmpty() && !matchesKeyword(entry, kw)) {
                continue;
            }
            result.add(entry.toDescriptor());
            seen.add(entry.fieldPath());
        }
        return result;
    }

    private static boolean matchesKeyword(ContextFieldCatalogEntry entry, String kw) {
        return entry.fieldPath().toLowerCase(Locale.ROOT).contains(kw)
            || entry.displayName().toLowerCase(Locale.ROOT).contains(kw)
            || entry.category().toLowerCase(Locale.ROOT).contains(kw)
            || entry.groupName().toLowerCase(Locale.ROOT).contains(kw);
    }
}
