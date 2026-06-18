package com.medkernel.engine.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.medkernel.shared.context.OrgScope;

/**
 * 模型能力策略作用域。
 *
 * <p>作用域解析顺序遵循组织树由近到远回退，最后落到租户级基线；专病为横切维度，
 * 不混入组织继承链，避免同一请求出现两个不同父链。
 */
public record ModelPolicyScope(String scopeType, String scopeRef) {

    public static final String TENANT = "TENANT";
    public static final String GROUP = "GROUP";
    public static final String HOSPITAL = "HOSPITAL";
    public static final String CAMPUS = "CAMPUS";
    public static final String SITE = "SITE";
    public static final String DEPARTMENT = "DEPARTMENT";
    public static final String WARD = "WARD";

    public ModelPolicyScope {
        scopeType = normalizeType(scopeType);
        scopeRef = requireRef(scopeRef);
    }

    public static List<ModelPolicyScope> candidates(OrgScope scope, String tenantId) {
        String requiredTenant = requireRef(tenantId);
        OrgScope safe = scope == null ? OrgScope.tenant(requiredTenant) : scope;
        List<ModelPolicyScope> scopes = new ArrayList<>();
        add(scopes, WARD, safe.wardId());
        add(scopes, DEPARTMENT, safe.departmentId());
        add(scopes, SITE, safe.siteId());
        add(scopes, CAMPUS, safe.campusId());
        add(scopes, HOSPITAL, safe.hospitalId());
        add(scopes, GROUP, safe.groupId());
        scopes.add(new ModelPolicyScope(TENANT, requiredTenant));
        return scopes;
    }

    public static ModelPolicyScope current(OrgScope scope, String tenantId) {
        return candidates(scope, tenantId).getFirst();
    }

    public String label() {
        return scopeType + ":" + scopeRef;
    }

    private static void add(List<ModelPolicyScope> scopes, String scopeType, String scopeRef) {
        if (scopeRef != null && !scopeRef.isBlank()) {
            scopes.add(new ModelPolicyScope(scopeType, scopeRef));
        }
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型策略作用域类型不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireRef(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型策略作用域引用不能为空");
        }
        return value.trim();
    }
}
