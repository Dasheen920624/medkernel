package com.medkernel.engine.security;

import com.medkernel.shared.context.OrgScope;

/**
 * MedKernel v1.0 GA · 已解析的数据范围授权。
 *
 * @param level 当前用户有效原始数据访问层级
 * @param scope 当前请求组织上下文
 * @param desensitized 是否允许访问脱敏数据
 */
public record ResolvedDataScope(DataAccessLevel level, OrgScope scope, boolean desensitized) {

    public ResolvedDataScope(DataAccessLevel level, OrgScope scope) {
        this(level, scope, false);
    }

    public ResolvedDataScope {
        if (level == null) {
            level = DataAccessLevel.NONE;
        }
        if (scope == null) {
            scope = OrgScope.empty();
        }
    }

    /**
     * 判断当前授权是否允许访问目标组织范围。
     */
    public boolean canAccess(OrgScope requested) {
        if (requested == null || !same(scope.tenantId(), requested.tenantId())) {
            return false;
        }
        return switch (level) {
            case GROUP -> same(scope.groupId(), requested.groupId());
            case HOSPITAL -> sameKnownParent(scope.groupId(), requested.groupId())
                && same(scope.hospitalId(), requested.hospitalId());
            case DEPARTMENT -> sameKnownParent(scope.groupId(), requested.groupId())
                && sameKnownParent(scope.hospitalId(), requested.hospitalId())
                && same(scope.departmentId(), requested.departmentId());
            case NONE -> false;
        };
    }

    private boolean sameKnownParent(String current, String requested) {
        return isBlank(current) || same(current, requested);
    }

    private boolean same(String current, String requested) {
        return !isBlank(current) && !isBlank(requested) && current.equals(requested);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
