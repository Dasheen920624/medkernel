package com.medkernel.shared.api.error;

/**
 * 权限不足异常。
 *
 * <p>用于构造六态中的“无权限态”：只暴露缺少的权限范围与申请入口，不暴露目标资源是否存在。
 */
public class PermissionDeniedException extends RuntimeException {

    private static final String DEFAULT_APPLY_URL = "/security/request-access";

    private final String requiredPermission;
    private final String permissionScope;
    private final String applyUrl;

    public PermissionDeniedException(String requiredPermission, String permissionScope, String applyUrl) {
        super("权限不足：" + normalizePermission(requiredPermission));
        this.requiredPermission = normalizePermission(requiredPermission);
        this.permissionScope = normalizeScope(permissionScope);
        this.applyUrl = applyUrl == null || applyUrl.isBlank() ? DEFAULT_APPLY_URL : applyUrl;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public String permissionScope() {
        return permissionScope;
    }

    public String applyUrl() {
        return applyUrl;
    }

    private static String normalizePermission(String permission) {
        return permission == null || permission.isBlank() ? "UNKNOWN" : permission;
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "当前操作" : scope;
    }
}
