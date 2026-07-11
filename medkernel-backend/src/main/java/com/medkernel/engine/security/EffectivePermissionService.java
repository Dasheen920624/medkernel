package com.medkernel.engine.security;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.medkernel.shared.context.OrgScope;

/**
 * 计算当前用户有效权限。
 *
 * <p>顺序：JWT 角色 + 范围匹配的用户职责分配 → 固定职责权限包。
 * 租户只配置职责与组织范围，不改写角色权限，避免菜单、动作和数据门禁漂移。
 */
@Service
public class EffectivePermissionService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public EffectivePermissionService(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    public EffectivePermissionProfile resolve(Authentication auth, OrgScope scope, String userId) {
        LinkedHashMap<String, EffectivePermissionProfile.RoleView> roles = new LinkedHashMap<>();
        collectAuthenticationRoles(auth, roles);
        collectAssignedRoles(scope, userId, roles);

        EnumSet<PermissionCode> permissions = EnumSet.noneOf(PermissionCode.class);
        for (EffectivePermissionProfile.RoleView roleView : roles.values()) {
            RoleCode.fromCode(roleView.code())
                .ifPresent(role -> permissions.addAll(DefaultPermissionPolicy.permissionsOf(role)));
        }
        List<EffectivePermissionProfile.PermissionView> permissionViews = permissions.stream()
            .sorted(Comparator.comparing(PermissionCode::code))
            .map(p -> new EffectivePermissionProfile.PermissionView(
                p.code(),
                p.dimension().name(),
                p.target(),
                p.displayName(),
                p.risk().name()))
            .toList();

        return new EffectivePermissionProfile(
            userId,
            userId,
            List.copyOf(roles.values()),
            permissionViews,
            MenuPermissionCatalog.menuKeysFor(permissions),
            environmentKeysFor(permissions),
            dataScope(scope),
            false,
            false,
            false,
            false
        );
    }

    public Set<PermissionCode> effectivePermissions(Authentication auth, OrgScope scope, String userId) {
        return resolve(auth, scope, userId).permissions().stream()
            .map(EffectivePermissionProfile.PermissionView::code)
            .map(PermissionCode::fromCode)
            .flatMap(java.util.Optional::stream)
            .collect(() -> EnumSet.noneOf(PermissionCode.class), EnumSet::add, EnumSet::addAll);
    }

    private void collectAuthenticationRoles(
            Authentication auth,
            LinkedHashMap<String, EffectivePermissionProfile.RoleView> roles) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
            return;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            RoleCode.fromAuthority(authority.getAuthority())
                .ifPresent(role -> roles.putIfAbsent(role.code(),
                    new EffectivePermissionProfile.RoleView(
                        role.code(),
                        role.displayName(),
                        "JWT",
                        null,
                        null)));
        }
    }

    private void collectAssignedRoles(
            OrgScope scope,
            String userId,
            LinkedHashMap<String, EffectivePermissionProfile.RoleView> roles) {
        if (scope == null || !scope.hasTenant() || userId == null || userId.isBlank()) {
            return;
        }
        List<UserRoleAssignment> assignments =
            userRoleAssignmentRepository.findActiveByTenantIdAndUserId(scope.tenantId(), userId);
        for (UserRoleAssignment assignment : assignments) {
            if (!assignment.active() || !assignmentAppliesToScope(assignment, scope)) {
                continue;
            }
            assignment.role().ifPresent(role -> {
                roles.remove(role.code());
                roles.put(
                    roleAssignmentKey(role.code(), assignment.scopeLevel(), assignment.scopeCode()),
                    assignmentRoleView(role, assignment));
            });
        }
    }

    private EffectivePermissionProfile.RoleView assignmentRoleView(RoleCode role, UserRoleAssignment assignment) {
        return new EffectivePermissionProfile.RoleView(
            role.code(),
            role.displayName(),
            "ASSIGNMENT",
            assignment.scopeLevel(),
            assignment.scopeCode());
    }

    private String roleAssignmentKey(String roleCode, String scopeLevel, String scopeCode) {
        return String.join(
            "|",
            roleCode,
            scopeLevel == null ? "" : scopeLevel.trim().toUpperCase(Locale.ROOT),
            scopeCode == null ? "" : scopeCode.trim());
    }

    private boolean assignmentAppliesToScope(UserRoleAssignment assignment, OrgScope scope) {
        if (assignment.scopeLevel() == null || assignment.scopeCode() == null) {
            return false;
        }
        String assignedCode = assignment.scopeCode().trim();
        if (assignedCode.isEmpty()) {
            return false;
        }
        return switch (assignment.scopeLevel().trim().toUpperCase(Locale.ROOT)) {
            case "TENANT" -> matches(assignedCode, scope.tenantId());
            case "REGION" -> matches(assignedCode, scope.groupId());
            case "FACILITY" -> matches(assignedCode, scope.hospitalId());
            case "CAMPUS" -> matches(assignedCode, scope.campusId());
            case "DEPARTMENT" -> matches(assignedCode, scope.departmentId());
            case "WARD" -> matches(assignedCode, scope.wardId());
            case "SPECIALTY" -> matches(assignedCode, scope.specialtyId());
            default -> false;
        };
    }

    private boolean matches(String assignedCode, String contextCode) {
        return contextCode != null && !contextCode.isBlank() && assignedCode.equals(contextCode);
    }

    private List<String> environmentKeysFor(EnumSet<PermissionCode> permissions) {
        return permissions.stream()
            .filter(permission -> permission.dimension() == PermissionDimension.ENVIRONMENT)
            .sorted(Comparator.comparing(PermissionCode::code))
            .map(PermissionCode::target)
            .toList();
    }

    private EffectivePermissionProfile.DataScopeView dataScope(OrgScope scope) {
        OrgScope safe = scope == null ? OrgScope.empty() : scope;
        return new EffectivePermissionProfile.DataScopeView(
            safe.tenantId(),
            safe.groupId(),
            safe.hospitalId(),
            safe.campusId(),
            safe.siteId(),
            safe.departmentId(),
            safe.wardId(),
            safe.specialtyId()
        );
    }
}
