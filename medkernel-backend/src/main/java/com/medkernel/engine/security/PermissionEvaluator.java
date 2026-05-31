package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.context.OrgScope;

/**
 * MedKernel v1.0 GA · 权限评估器，供 SpEL {@code @PreAuthorize("@perm.has('rule.publish')")} 使用。
 *
 * <p>评估流程：
 * <ol>
 *   <li>从 {@link SecurityContextHolder} 取出当前 {@link Authentication} 的角色</li>
 *   <li>叠加当前租户下有效的用户角色分配</li>
 *   <li>以 {@link DefaultPermissionPolicy} 为基线应用租户级允许或拒绝覆盖</li>
 *   <li>判断指定 {@link PermissionCode} 是否在集合中</li>
 * </ol>
 *
 * <p>Bean 名是 {@code perm}（由 {@link Component} value 指定），让 SpEL 写起来短：
 * <pre>{@code
 * @PreAuthorize("@perm.has('rule.publish')")
 * public ApiResult<Rule> publish(...) { ... }
 * }</pre>
 */
@Component("perm")
public class PermissionEvaluator {

    private final EffectivePermissionService permissionService;

    public PermissionEvaluator(EffectivePermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 当前线程的 Authentication 是否拥有指定权限码。
     */
    public boolean has(String permissionCode) {
        return PermissionCode.fromCode(permissionCode)
            .map(this::currentHas)
            .orElse(false);
    }

    /**
     * 当前线程的 Authentication 是否拥有指定权限码（强类型）。
     */
    public boolean has(PermissionCode permission) {
        return currentHas(permission);
    }

    /**
     * 当前线程的 Authentication 是否拥有某个五维权限目标。
     */
    public boolean can(PermissionDimension dimension, String target) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return can(
            auth,
            dimension,
            target,
            RequestContext.currentOrgScope(),
            RequestContext.currentUserId().orElse(auth == null ? null : auth.getName())
        );
    }

    /**
     * 指定 Authentication 是否拥有某个五维权限目标，供非当前线程场景复用。
     */
    public boolean can(Authentication auth,
                       PermissionDimension dimension,
                       String target,
                       OrgScope scope,
                       String userId) {
        if (auth == null || !auth.isAuthenticated() || dimension == null || target == null || target.isBlank()) {
            return false;
        }
        String normalizedTarget = target.trim().toLowerCase(Locale.ROOT);
        return permissionService.effectivePermissions(auth, scope, userIdOrPrincipal(auth, userId)).stream()
            .anyMatch(permission -> permission.dimension() == dimension
                && permission.target().equalsIgnoreCase(normalizedTarget));
    }

    /**
     * 当前线程的 Authentication 是否拥有列出权限中的任意一个。
     */
    public boolean hasAny(String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        Set<PermissionCode> effective = effectivePermissions();
        for (String code : permissionCodes) {
            if (PermissionCode.fromCode(code).map(effective::contains).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前线程的 Authentication 是否拥有列出的全部权限。
     */
    public boolean hasAll(String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return true;
        }
        Set<PermissionCode> effective = effectivePermissions();
        for (String code : permissionCodes) {
            if (!PermissionCode.fromCode(code).map(effective::contains).orElse(false)) {
                return false;
            }
        }
        return true;
    }

    private boolean currentHas(PermissionCode permission) {
        return effectivePermissions().contains(permission);
    }

    /**
     * 当前线程所有有效权限集合（含角色分配及租户权限覆盖）。
     */
    public Set<PermissionCode> effectivePermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return EnumSet.noneOf(PermissionCode.class);
        }
        return permissionService.effectivePermissions(
            auth,
            RequestContext.currentOrgScope(),
            RequestContext.currentUserId().orElse(auth.getName())
        );
    }

    private String userIdOrPrincipal(Authentication auth, String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return auth.getName();
    }
}
