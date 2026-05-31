package com.medkernel.engine.security;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;

/**
 * MedKernel v1.0 GA · 数据范围解析器。
 *
 * <p>只根据统一权限画像中的 {@code data.*} 权限计算数据访问层级，不允许角色名或超管分支绕过引擎。
 */
@Service
public class DataScopeResolver {

    private final EffectivePermissionService permissionService;

    public DataScopeResolver(EffectivePermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 解析当前 Authentication 在当前组织上下文下的数据范围。
     */
    public ResolvedDataScope resolve(Authentication auth, OrgScope currentScope, String userId) {
        OrgScope safeScope = currentScope == null ? OrgScope.empty() : currentScope;
        if (auth == null || !auth.isAuthenticated()) {
            return new ResolvedDataScope(DataAccessLevel.NONE, safeScope);
        }
        Set<PermissionCode> permissions = permissionService.effectivePermissions(
            auth,
            safeScope,
            userIdOrPrincipal(auth, userId)
        );
        boolean desensitized = permissions.contains(PermissionCode.DATA_DESENSITIZED);
        if (permissions.contains(PermissionCode.DATA_GROUP)) {
            return new ResolvedDataScope(DataAccessLevel.GROUP, safeScope, desensitized);
        }
        if (permissions.contains(PermissionCode.DATA_HOSPITAL)) {
            return new ResolvedDataScope(DataAccessLevel.HOSPITAL, safeScope, desensitized);
        }
        if (permissions.contains(PermissionCode.DATA_DEPARTMENT)) {
            return new ResolvedDataScope(DataAccessLevel.DEPARTMENT, safeScope, desensitized);
        }
        return new ResolvedDataScope(DataAccessLevel.NONE, safeScope, desensitized);
    }

    /**
     * 判断目标组织范围是否落在当前用户的数据授权内。
     */
    public boolean canAccess(Authentication auth, OrgScope currentScope, OrgScope requestedScope, String userId) {
        return resolve(auth, currentScope, userId).canAccess(requestedScope);
    }

    /**
     * 未授权时抛出标准数据范围错误，供服务层行级校验复用。
     */
    public void assertCanAccess(Authentication auth, OrgScope currentScope, OrgScope requestedScope, String userId) {
        if (!canAccess(auth, currentScope, requestedScope, userId)) {
            throw new ApiException(ErrorCode.DATA_SCOPE_DENIED, "数据范围权限不足");
        }
    }

    private String userIdOrPrincipal(Authentication auth, String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return auth.getName();
    }
}
