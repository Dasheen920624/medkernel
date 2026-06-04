package com.medkernel.engine.context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

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
