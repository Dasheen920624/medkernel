package com.medkernel.engine.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.medkernel.shared.context.OrgScope;

/**
 * 计算当前用户有效权限。
 *
 * <p>顺序：JWT 角色 + 范围匹配的用户角色分配 → 默认角色权限 → 租户级 ALLOW/DENY 覆盖。
 * 多角色覆盖冲突时采用 DENY 优先。
 */
@Service
public class EffectivePermissionService {

    private final RolePermissionOverrideRepository rolePermissionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final EmergencyPermissionGrantRepository emergencyGrantRepository;
    private final Clock clock;

    @Autowired
    public EffectivePermissionService(RolePermissionOverrideRepository rolePermissionRepository,
                                      UserRoleAssignmentRepository userRoleAssignmentRepository,
                                      EmergencyPermissionGrantRepository emergencyGrantRepository) {
        this(rolePermissionRepository, userRoleAssignmentRepository, emergencyGrantRepository, Clock.systemUTC());
    }

    EffectivePermissionService(RolePermissionOverrideRepository rolePermissionRepository,
                               UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this(rolePermissionRepository, userRoleAssignmentRepository, null, Clock.systemUTC());
    }

    EffectivePermissionService(RolePermissionOverrideRepository rolePermissionRepository,
                               UserRoleAssignmentRepository userRoleAssignmentRepository,
                               EmergencyPermissionGrantRepository emergencyGrantRepository,
                               Clock clock) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.emergencyGrantRepository = emergencyGrantRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public EffectivePermissionProfile resolve(Authentication auth, OrgScope scope, String userId) {
        LinkedHashMap<String, EffectivePermissionProfile.RoleView> roles = new LinkedHashMap<>();
        collectAuthenticationRoles(auth, roles);
        collectAssignedRoles(scope, userId, roles);

        EnumSet<PermissionCode> permissions = EnumSet.noneOf(PermissionCode.class);
        for (String roleCode : roles.keySet()) {
            RoleCode.fromCode(roleCode)
                .ifPresent(role -> permissions.addAll(DefaultPermissionPolicy.permissionsOf(role)));
        }
        applyTenantOverrides(scope, roles.keySet(), permissions);
        applyActiveEmergencyGrant(scope, userId, permissions);

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
            MfaRequirementPolicy.requiresMfa(roles.keySet()),
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
            assignment.role().ifPresent(role -> roles.putIfAbsent(role.code(),
                new EffectivePermissionProfile.RoleView(
                    role.code(),
                    role.displayName(),
                    "ASSIGNMENT",
                    assignment.scopeLevel(),
                    assignment.scopeCode())));
        }
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
            case "GROUP" -> matches(assignedCode, scope.groupId());
            case "HOSPITAL" -> matches(assignedCode, scope.hospitalId());
            case "CAMPUS" -> matches(assignedCode, scope.campusId());
            case "SITE" -> matches(assignedCode, scope.siteId());
            case "DEPARTMENT" -> matches(assignedCode, scope.departmentId());
            case "SPECIALTY" -> matches(assignedCode, scope.specialtyId());
            default -> false;
        };
    }

    private boolean matches(String assignedCode, String contextCode) {
        return contextCode != null && !contextCode.isBlank() && assignedCode.equals(contextCode);
    }

    private void applyTenantOverrides(OrgScope scope,
                                      Collection<String> roleCodes,
                                      EnumSet<PermissionCode> permissions) {
        if (scope == null || !scope.hasTenant() || roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        List<String> normalizedRoleCodes = roleCodes.stream()
            .filter(Objects::nonNull)
            .filter(code -> RoleCode.fromCode(code).isPresent())
            .distinct()
            .toList();
        if (normalizedRoleCodes.isEmpty()) {
            return;
        }

        List<RolePermissionOverride> overrides =
            rolePermissionRepository.findByTenantIdAndRoleCodes(scope.tenantId(), normalizedRoleCodes);
        LinkedHashSet<String> roleSet = new LinkedHashSet<>(normalizedRoleCodes);
        EnumSet<PermissionCode> allowed = EnumSet.noneOf(PermissionCode.class);
        EnumSet<PermissionCode> denied = EnumSet.noneOf(PermissionCode.class);
        for (RolePermissionOverride override : overrides) {
            if (!roleSet.contains(override.roleCode())) {
                continue;
            }
            override.permission().ifPresent(permission -> {
                if (override.effect() == PermissionEffect.DENY) {
                    denied.add(permission);
                } else if (override.effect() == PermissionEffect.ALLOW) {
                    allowed.add(permission);
                }
            });
        }
        permissions.addAll(allowed);
        permissions.removeAll(denied);
    }

    private void applyActiveEmergencyGrant(OrgScope scope, String userId, EnumSet<PermissionCode> permissions) {
        if (emergencyGrantRepository == null
                || scope == null
                || !scope.hasTenant()
                || userId == null
                || userId.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        List<EmergencyPermissionGrant> grants =
            emergencyGrantRepository.findActiveByTenantIdAndUserIdAndPermissionCode(
                scope.tenantId(), userId, PermissionCode.ENV_EMERGENCY.code(), now);
        if (grants != null && grants.stream().anyMatch(grant -> grant.activeAt(now))) {
            permissions.add(PermissionCode.ENV_EMERGENCY);
        }
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
            safe.specialtyId()
        );
    }
}
