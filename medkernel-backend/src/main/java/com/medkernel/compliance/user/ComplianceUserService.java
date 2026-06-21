package com.medkernel.compliance.user;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.EffectivePermissionProfile;
import com.medkernel.engine.security.EffectivePermissionService;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.SystemSuperAdminGuard;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.auth.CredentialAdminService;
import com.medkernel.engine.security.auth.CredentialCreationResult;
import com.medkernel.engine.security.auth.PasswordResetTokenResponse;
import com.medkernel.engine.security.auth.ResetPasswordResponse;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/**
 * 身份安全服务包的用户管理编排：统一用户主体、凭证、角色、状态和有效权限。
 */
@Service
public class ComplianceUserService {

    private final TenantUserRepository users;
    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final CredentialAdminService credentialAdmin;
    private final EffectivePermissionService effectivePermissions;
    private final AuditRecorder auditRecorder;
    private final OrgUnitRepository organizations;

    public ComplianceUserService(
            TenantUserRepository users,
            PlatformCredentialRepository credentials,
            UserRoleAssignmentRepository roleAssignments,
            CredentialAdminService credentialAdmin,
            EffectivePermissionService effectivePermissions,
            AuditRecorder auditRecorder,
            OrgUnitRepository organizations) {
        this.users = users;
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.credentialAdmin = credentialAdmin;
        this.effectivePermissions = effectivePermissions;
        this.auditRecorder = auditRecorder;
        this.organizations = organizations;
    }

    /**
     * 按当前租户分页列出统一用户目录。
     */
    @Transactional(readOnly = true)
    public PageResponse<ComplianceUserSummary> page(com.medkernel.shared.api.PageRequest request) {
        var safe = request == null ? com.medkernel.shared.api.PageRequest.defaults() : request;
        String tenantId = tenantId();
        List<TenantUser> pageUsers = users
            .findByTenantIdOrderByDisplayNameAsc(
                tenantId,
                PageRequest.of(safe.safePage() - 1, safe.safeSize()));
        if (pageUsers.isEmpty()) {
            return PageResponse.of(List.of(), safe, users.countByTenantId(tenantId));
        }
        List<String> userIds = pageUsers.stream().map(TenantUser::userId).toList();
        Map<String, PlatformCredential> credentialByUserId = credentials
            .findByTenantIdAndUserIdIn(tenantId, userIds)
            .stream()
            .collect(Collectors.toMap(PlatformCredential::userId, Function.identity()));
        Map<String, List<ComplianceUserRole>> rolesByUserId = roleAssignments
            .findActiveByTenantIdAndUserIdIn(tenantId, userIds)
            .stream()
            .collect(Collectors.groupingBy(
                UserRoleAssignment::userId,
                Collectors.mapping(this::toRole, Collectors.toList())));
        List<ComplianceUserSummary> items = pageUsers.stream()
            .map(user -> summary(
                user,
                Optional.ofNullable(credentialByUserId.get(user.userId())),
                rolesByUserId.getOrDefault(user.userId(), List.of())))
            .toList();
        return PageResponse.of(items, safe, users.countByTenantId(tenantId));
    }

    /**
     * 返回用户详情及当前组织上下文的有效权限。
     */
    @Transactional(readOnly = true)
    public ComplianceUserDetail detail(String userId) {
        TenantUser user = findUser(userId);
        Optional<PlatformCredential> credential = credential(user.userId());
        EffectivePermissionProfile profile = effectivePermissions.resolve(
            null,
            RequestContext.currentOrgScope(),
            user.userId());
        return new ComplianceUserDetail(
            user.userId(),
            user.displayName(),
            credential.map(PlatformCredential::username).orElse(null),
            credential.isPresent(),
            credential.map(PlatformCredential::status).orElse(user.status()),
            credential.map(value -> "Y".equalsIgnoreCase(value.mustChangePwd())).orElse(false),
            roles(user.userId()),
            profile.permissions(),
            user.createdAt(),
            user.updatedAt());
    }

    /**
     * 创建统一用户主体；平台凭证和外部身份用户使用同一目录。
     */
    @Transactional
    public ComplianceUserCreateResponse create(
            ComplianceUserCreateRequest request,
            Authentication authentication) {
        boolean credentialManaged = Boolean.TRUE.equals(request.credentialManaged());
        String username = compact(request.username());
        String userId = compact(request.userId());
        String displayName = compact(request.displayName());

        if (credentialManaged) {
            username = requireText(username, "平台凭证用户必须填写登录名");
            userId = userId == null ? username : userId;
            displayName = displayName == null ? username : displayName;
        } else {
            userId = requireText(userId, "外部身份用户必须填写用户标识");
            displayName = requireText(displayName, "外部身份用户必须填写显示名称");
            if (username != null || compact(request.initialPassword()) != null) {
                throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "外部身份用户不能配置平台登录名或初始密码");
            }
        }

        RoleCode initialRole = null;
        if (compact(request.roleCode()) != null) {
            initialRole = requireRole(request.roleCode());
            SystemSuperAdminGuard.assertTenantManagedRole(initialRole.code());
            assertRoleGrantAllowed(authentication, initialRole);
        }

        String tenantId = tenantId();
        if (users.findByTenantIdAndUserId(tenantId, userId).isPresent()) {
            throw ApiException.conflict("当前租户已存在用户标识 " + userId);
        }

        Instant now = Instant.now();
        users.save(new TenantUser(
            null,
            tenantId,
            userId,
            displayName,
            "ACTIVE",
            1L,
            now,
            actor(),
            now,
            actor(),
            traceId()));

        String tempPassword = null;
        if (credentialManaged) {
            CredentialCreationResult result = credentialAdmin.createMember(
                username,
                userId,
                initialRole == null ? null : initialRole.code(),
                request.initialPassword());
            tempPassword = result.tempPassword();
        } else if (initialRole != null) {
            saveRole(userId, initialRole.code(), "TENANT", tenantId);
        }

        auditRecorder.record(
            AuditAction.CREATE,
            "tenant_user",
            userId,
            "创建租户用户 credentialManaged=" + credentialManaged);
        return new ComplianceUserCreateResponse(detail(userId), tempPassword);
    }

    /**
     * 为真实存在的当前租户用户增加角色范围；重复请求保持幂等。
     */
    @Transactional
    public ComplianceUserDetail assignRole(
            String userId,
            ComplianceUserRoleRequest request,
            Authentication authentication) {
        findUser(userId);
        RoleCode role = requireRole(request.roleCode());
        SystemSuperAdminGuard.assertTenantManagedRole(role.code());
        assertRoleGrantAllowed(authentication, role);

        String scopeLevel = normalizeScopeLevel(request.scopeLevel());
        String scopeCode = request.scopeCode().trim();
        assertScopeAllowed(scopeLevel, scopeCode);
        saveRole(userId, role.code(), scopeLevel, scopeCode);
        return detail(userId);
    }

    /**
     * 停用一条用户角色范围，保留审计历史。
     */
    @Transactional
    public ComplianceUserDetail removeRole(
            String userId,
            String roleCode,
            String scopeLevel,
            String scopeCode) {
        findUser(userId);
        String normalizedLevel = normalizeScopeLevel(scopeLevel);
        UserRoleAssignment existing = roleAssignments
            .findByTenantIdAndUserIdAndRoleCodeAndScopeLevelAndScopeCode(
                tenantId(), userId, roleCode, normalizedLevel, scopeCode.trim())
            .filter(UserRoleAssignment::active)
            .orElseThrow(() -> ApiException.notFound("用户角色分配"));
        SystemSuperAdminGuard.assertAssignmentMutable(existing);
        Instant now = Instant.now();
        roleAssignments.save(new UserRoleAssignment(
            existing.id(),
            existing.tenantId(),
            existing.userId(),
            existing.roleCode(),
            existing.scopeLevel(),
            existing.scopeCode(),
            "N",
            existing.createdAt(),
            existing.createdBy(),
            now,
            actor()));
        auditRecorder.record(
            AuditAction.DELETE,
            "user_role_assignment",
            userId,
            "停用用户角色 role=" + roleCode + " scope=" + normalizedLevel + ":" + scopeCode.trim());
        return detail(userId);
    }

    public ResetPasswordResponse resetPassword(String userId) {
        requireManagedCredential(userId);
        return credentialAdmin.resetPassword(userId);
    }

    public PasswordResetTokenResponse issueResetToken(String userId) {
        requireManagedCredential(userId);
        return credentialAdmin.issueResetToken(userId);
    }

    @Transactional
    public ComplianceUserDetail setStatus(String userId, String status) {
        TenantUser user = findUser(userId);
        Optional<PlatformCredential> credential = credential(userId);
        if (credential.isPresent()) {
            credentialAdmin.setStatus(userId, status);
        } else {
            if ("LOCKED".equalsIgnoreCase(status)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "外部身份用户不支持密码锁定状态");
            }
            roleAssignments.findActiveByTenantIdAndUserId(tenantId(), userId)
                .forEach(SystemSuperAdminGuard::assertAssignmentMutable);
        }
        Instant now = Instant.now();
        users.save(new TenantUser(
            user.id(),
            user.tenantId(),
            user.userId(),
            user.displayName(),
            status,
            user.version() + 1L,
            user.createdAt(),
            user.createdBy(),
            now,
            actor(),
            traceId()));
        auditRecorder.record(
            AuditAction.EXECUTE,
            "tenant_user",
            userId,
            "更新用户状态 status=" + status);
        return detail(userId);
    }

    /**
     * 幂等同步由院内身份源权威维护的用户主体，不创建或回传平台口令。
     */
    @Transactional
    public ComplianceUserDetail syncExternalUser(String userId, String displayName, String status) {
        String safeUserId = requireText(compact(userId), "院内用户标识不能为空");
        String safeDisplayName = requireText(compact(displayName), "院内用户姓名不能为空");
        String safeStatus = "DISABLED".equalsIgnoreCase(status) ? "DISABLED" : "ACTIVE";
        String tenantId = tenantId();
        String sourceActor = requireExternalSourceActor();
        TenantUser current = users.findByTenantIdAndUserId(tenantId, safeUserId).orElse(null);
        Instant now = Instant.now();
        if (current == null) {
            users.save(new TenantUser(
                null, tenantId, safeUserId, safeDisplayName, safeStatus, 1L,
                now, actor(), now, actor(), traceId()));
            auditRecorder.record(
                AuditAction.CREATE, "tenant_user", safeUserId, "同步院内身份源用户");
            return detail(safeUserId);
        }
        if (credentials.findByTenantIdAndUserId(tenantId, safeUserId).isPresent()) {
            throw ApiException.conflict("平台凭证用户不能被院内身份源同步接管");
        }
        if (!sourceActor.equals(current.createdBy())) {
            throw ApiException.conflict("院内用户已由人工或其他来源维护，不能被当前来源接管");
        }
        if (!current.status().equalsIgnoreCase(safeStatus)) {
            setStatus(safeUserId, safeStatus);
            current = users.findByTenantIdAndUserId(tenantId, safeUserId).orElseThrow();
        }
        if (!current.displayName().equals(safeDisplayName)) {
            users.save(new TenantUser(
                current.id(), current.tenantId(), current.userId(), safeDisplayName,
                current.status(), current.version() + 1L, current.createdAt(), current.createdBy(),
                now, actor(), traceId()));
            auditRecorder.record(
                AuditAction.UPDATE, "tenant_user", safeUserId, "同步院内用户显示名称");
        }
        return detail(safeUserId);
    }

    /**
     * 用当前院内来源的期望职责替换该来源此前创建的职责，不影响人工或其他来源分配。
     */
    @Transactional
    public void syncExternalRole(
            String userId,
            ComplianceUserRoleRequest desired,
            Authentication authentication) {
        TenantUser user = findUser(userId);
        String sourceActor = requireExternalSourceActor();
        if (!sourceActor.equals(user.createdBy())) {
            throw ApiException.conflict("院内用户已由人工或其他来源维护，不能同步其职责");
        }

        RoleCode desiredRole = null;
        String desiredScopeLevel = null;
        String desiredScopeCode = null;
        if (desired != null) {
            desiredRole = requireRole(desired.roleCode());
            SystemSuperAdminGuard.assertTenantManagedRole(desiredRole.code());
            assertRoleGrantAllowed(authentication, desiredRole);
            desiredScopeLevel = normalizeScopeLevel(desired.scopeLevel());
            desiredScopeCode = desired.scopeCode().trim();
            assertScopeAllowed(desiredScopeLevel, desiredScopeCode);
        }

        Instant now = Instant.now();
        for (UserRoleAssignment assignment :
                roleAssignments.findActiveByTenantIdAndUserId(tenantId(), userId)) {
            boolean sameDesired = desiredRole != null
                && assignment.roleCode().equals(desiredRole.code())
                && assignment.scopeLevel().equals(desiredScopeLevel)
                && assignment.scopeCode().equals(desiredScopeCode);
            if (!sourceActor.equals(assignment.createdBy()) || sameDesired) {
                continue;
            }
            SystemSuperAdminGuard.assertAssignmentMutable(assignment);
            roleAssignments.save(new UserRoleAssignment(
                assignment.id(),
                assignment.tenantId(),
                assignment.userId(),
                assignment.roleCode(),
                assignment.scopeLevel(),
                assignment.scopeCode(),
                "N",
                assignment.createdAt(),
                assignment.createdBy(),
                now,
                sourceActor));
            auditRecorder.record(
                AuditAction.UPDATE,
                "user_role_assignment",
                userId,
                "同步撤销来源职责 role=" + assignment.roleCode()
                    + " scope=" + assignment.scopeLevel() + ":" + assignment.scopeCode());
        }
        if (desiredRole != null) {
            saveRole(userId, desiredRole.code(), desiredScopeLevel, desiredScopeCode);
        }
    }

    private ComplianceUserSummary summary(
            TenantUser user,
            Optional<PlatformCredential> credential,
            List<ComplianceUserRole> userRoles) {
        return new ComplianceUserSummary(
            user.userId(),
            user.displayName(),
            credential.map(PlatformCredential::username).orElse(null),
            credential.isPresent(),
            credential.map(PlatformCredential::status).orElse(user.status()),
            credential.map(value -> "Y".equalsIgnoreCase(value.mustChangePwd())).orElse(false),
            userRoles,
            user.createdAt());
    }

    private List<ComplianceUserRole> roles(String userId) {
        return roleAssignments.findActiveByTenantIdAndUserId(tenantId(), userId).stream()
            .filter(UserRoleAssignment::active)
            .sorted(Comparator.comparing(UserRoleAssignment::roleCode)
                .thenComparing(UserRoleAssignment::scopeLevel)
                .thenComparing(UserRoleAssignment::scopeCode))
            .map(this::toRole)
            .toList();
    }

    private ComplianceUserRole toRole(UserRoleAssignment assignment) {
        return new ComplianceUserRole(
            assignment.roleCode(),
            assignment.role().map(RoleCode::displayName).orElse(assignment.roleCode()),
            assignment.scopeLevel(),
            assignment.scopeCode(),
            scopeName(assignment));
    }

    private String scopeName(UserRoleAssignment assignment) {
        return organizations.findByTenantIdAndId(tenantId(), assignment.scopeCode())
            .map(unit -> unit.name())
            .orElseGet(() -> "TENANT".equals(assignment.scopeLevel())
                ? tenantScopeName()
                : "组织已停用");
    }

    private String tenantScopeName() {
        return PlatformTenant.isPlatformTenant(tenantId())
            ? PlatformTenant.SCOPE_DISPLAY_NAME
            : "当前服务机构";
    }

    private void saveRole(String userId, String roleCode, String scopeLevel, String scopeCode) {
        String tenantId = tenantId();
        Instant now = Instant.now();
        UserRoleAssignment assignment = roleAssignments
            .findByTenantIdAndUserIdAndRoleCodeAndScopeLevelAndScopeCode(
                tenantId, userId, roleCode, scopeLevel, scopeCode)
            .map(existing -> existing.active()
                ? existing
                : new UserRoleAssignment(
                    existing.id(),
                    existing.tenantId(),
                    existing.userId(),
                    existing.roleCode(),
                    existing.scopeLevel(),
                    existing.scopeCode(),
                    "Y",
                    existing.createdAt(),
                    existing.createdBy(),
                    now,
                    actor()))
            .orElseGet(() -> new UserRoleAssignment(
                null,
                tenantId,
                userId,
                roleCode,
                scopeLevel,
                scopeCode,
                "Y",
                now,
                actor(),
                now,
                actor()));
        roleAssignments.save(assignment);
        auditRecorder.record(
            AuditAction.CREATE,
            "user_role_assignment",
            userId,
            "分配用户角色 role=" + roleCode + " scope=" + scopeLevel + ":" + scopeCode);
    }

    private TenantUser findUser(String userId) {
        return users.findByTenantIdAndUserId(tenantId(), userId)
            .orElseThrow(() -> ApiException.notFound("用户 " + userId));
    }

    private Optional<PlatformCredential> credential(String userId) {
        return credentials.findByTenantIdAndUserId(tenantId(), userId);
    }

    private PlatformCredential requireManagedCredential(String userId) {
        findUser(userId);
        return credential(userId)
            .orElseThrow(() -> ApiException.conflict("该用户不由平台管理密码"));
    }

    private RoleCode requireRole(String roleCode) {
        RoleCode role = RoleCode.fromCode(roleCode)
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "非法的系统角色编码: " + roleCode));
        SystemSuperAdminGuard.assertTenantManagedRole(role.code());
        if (!role.customerAssignable()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "该角色仅用于兼容，不属于首发职责: " + role.code());
        }
        return role;
    }

    private void assertRoleGrantAllowed(Authentication authentication, RoleCode targetRole) {
        int actorLevel = authentication == null || authentication.getAuthorities() == null
            ? -1
            : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(RoleCode::fromAuthority)
                .flatMap(java.util.Optional::stream)
                .mapToInt(this::managementLevel)
                .max()
                .orElse(-1);
        if (actorLevel < managementLevel(targetRole)) {
            throw ApiException.forbidden("不能授予高于当前管理员层级的角色");
        }
    }

    private int managementLevel(RoleCode role) {
        return switch (role) {
            case SYSTEM_SUPERADMIN -> 4;
            case PLATFORM_GOVERNANCE_ADMIN, PLATFORM_KNOWLEDGE_GOVERNOR -> 3;
            case ORGANIZATION_ADMIN -> 2;
            case IDENTITY_ACCESS_ADMIN -> 1;
            default -> 0;
        };
    }

    private String normalizeScopeLevel(String scopeLevel) {
        String normalized = scopeLevel == null ? "" : scopeLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TENANT", "REGION", "FACILITY", "CAMPUS", "DEPARTMENT", "WARD", "SPECIALTY" -> normalized;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST, "非法的作用域级别: " + scopeLevel);
        };
    }

    private void assertScopeAllowed(String scopeLevel, String scopeCode) {
        if (scopeCode.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "作用域编码不能为空");
        }
        if ("TENANT".equals(scopeLevel) && !tenantId().equals(scopeCode)) {
            throw ApiException.forbidden("租户级角色只能绑定当前租户");
        }
    }

    private String requireText(String value, String message) {
        if (value == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return value;
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String requireExternalSourceActor() {
        String sourceActor = actor();
        if (!sourceActor.startsWith("integration:")) {
            throw ApiException.forbidden("院内身份同步必须在受信集成来源上下文中执行");
        }
        return sourceActor;
    }

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
