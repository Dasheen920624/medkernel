package com.medkernel.engine.security;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 当前用户有效权限画像，供前端菜单、按钮和专家模式使用。
 */
public record EffectivePermissionProfile(
    String userId,
    String username,
    List<RoleView> roles,
    List<PermissionView> permissions,
    List<String> menuKeys,
    List<String> environmentKeys,
    DataScopeView dataScope,
    boolean mustChangePwd,
    boolean mfaRequired,
    boolean mfaBound
) {

    @JsonIgnore
    public List<String> roleCodes() {
        return roles.stream().map(RoleView::code).toList();
    }

    public EffectivePermissionProfile withIdentity(String username) {
        return new EffectivePermissionProfile(
            userId,
            username == null || username.isBlank() ? userId : username,
            roles,
            permissions,
            menuKeys,
            environmentKeys,
            dataScope,
            mustChangePwd,
            mfaRequired,
            mfaBound);
    }

    @JsonIgnore
    public List<String> permissionCodes() {
        return permissions.stream().map(PermissionView::code).toList();
    }

    public EffectivePermissionProfile withBootstrapSecurity(
            boolean mustChangePwd,
            boolean mfaRequired,
            boolean mfaBound) {
        return new EffectivePermissionProfile(
            userId,
            username,
            roles,
            permissions,
            menuKeys,
            environmentKeys,
            dataScope,
            mustChangePwd,
            mfaRequired,
            mfaBound);
    }

    public record RoleView(
        String code,
        String displayName,
        String source,
        String scopeLevel,
        String scopeCode
    ) {
    }

    public record PermissionView(
        String code,
        String dimension,
        String target,
        String displayName,
        String risk
    ) {
    }

    public record DataScopeView(
        String tenantId,
        String groupId,
        String hospitalId,
        String campusId,
        String siteId,
        String departmentId,
        String specialtyId
    ) {
    }
}
