package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.SystemSuperAdminGuard;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.RequestContext;

/**
 * 平台成员账号（凭证）管理服务：租户管理员开通成员、重置临时密码、启用/停用。
 *
 * <p>仅 dev/test profile（与平台登录一致）；内网 govcloud 走院方 IdP，不在此管理凭证。
 * 所有操作按当前请求租户隔离；成功走 {@code AuditEventPublisher}，失败走 {@code IsolatedAuditPublisher}。
 */
@Service
@Profile({"dev", "test"})
public class CredentialAdminService {

    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;
    private final SystemSuperAdminGuard superAdminGuard;
    private final PasswordPolicyService passwordPolicy;
    private final LoginAttemptService loginAttempts;

    public CredentialAdminService(PlatformCredentialRepository credentials,
                                  UserRoleAssignmentRepository roleAssignments,
                                  PasswordEncoder passwordEncoder,
                                  AuditEventPublisher auditPublisher,
                                  IsolatedAuditPublisher isolatedAudit,
                                  SystemSuperAdminGuard superAdminGuard,
                                  PasswordPolicyService passwordPolicy,
                                  LoginAttemptService loginAttempts) {
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.passwordEncoder = passwordEncoder;
        this.auditPublisher = auditPublisher;
        this.isolatedAudit = isolatedAudit;
        this.superAdminGuard = superAdminGuard;
        this.passwordPolicy = passwordPolicy;
        this.loginAttempts = loginAttempts;
    }

    /** 列出当前租户全部成员账号摘要（不含口令哈希），按登录名升序。 */
    @Transactional(readOnly = true)
    public List<CredentialSummary> list() {
        return credentials.findByTenantIdOrderByUsernameAsc(tenantId()).stream()
            .map(c -> new CredentialSummary(
                c.userId(), c.username(), c.status(), "Y".equalsIgnoreCase(c.mustChangePwd()), c.createdAt()))
            .toList();
    }

    /** 开通成员：登录名租户内唯一；可选授角色；初始密码留空则生成临时密码并一次性返回（须首登改密）。 */
    @Transactional
    public CreateMemberResponse createMember(CreateMemberRequest req) {
        String tenantId = tenantId();
        String actor = actor();
        if (credentials.findByTenantIdAndUsername(tenantId, req.username()).isPresent()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE, "platform_credential", req.username(),
                ErrorCode.ENG_AUTH_006.code(), "开通成员失败：用户名已存在 " + req.username()));
            throw new ApiException(ErrorCode.ENG_AUTH_006);
        }
        String userId = req.userIdOrUsername();
        boolean generated = req.initialPassword() == null || req.initialPassword().isBlank();
        String rawPassword = generated ? passwordPolicy.generateTemporaryPassword() : req.initialPassword();
        passwordPolicy.assertCompliant(rawPassword);
        Instant now = Instant.now();
        credentials.save(new PlatformCredential(
            null, "cred-" + userId, tenantId, userId, req.username(),
            passwordEncoder.encode(rawPassword), "ACTIVE", "Y", null,
            now, actor, now, actor, traceId()));
        String roleCode = normalizedRoleCode(req.roleCode());
        if (roleCode != null && !hasRole(tenantId, userId, roleCode)) {
            roleAssignments.save(new UserRoleAssignment(
                null, tenantId, userId, roleCode, "TENANT", tenantId, "Y", now, actor, now, actor));
        }
        auditPublisher.publish(AuditAction.CREATE, "platform_credential", userId,
            "开通成员 username=" + req.username() + " role=" + roleCode);
        return new CreateMemberResponse(userId, req.username(), generated ? rawPassword : null);
    }

    /** 重置成员密码为新临时密码（须首登改密），一次性返回。 */
    @Transactional
    public ResetPasswordResponse resetPassword(String userId) {
        PlatformCredential cred = find(userId);
        superAdminGuard.assertCredentialMutableByTenantManagement(cred.tenantId(), cred.userId());
        String rawPassword = passwordPolicy.generateTemporaryPassword();
        Instant now = Instant.now();
        loginAttempts.clearStateForCredential(cred, actor());
        credentials.save(rewrite(cred, passwordEncoder.encode(rawPassword), cred.status(), "Y", now, actor()));
        auditPublisher.publish(AuditAction.EXECUTE, "platform_credential", userId, "重置成员密码");
        return new ResetPasswordResponse(rawPassword);
    }

    /** 启用 / 停用 / 锁定成员账号。 */
    @Transactional
    public void setStatus(String userId, String status) {
        PlatformCredential cred = find(userId);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            superAdminGuard.assertCredentialMutableByTenantManagement(cred.tenantId(), cred.userId());
        }
        Instant now = Instant.now();
        loginAttempts.clearStateForCredential(cred, actor());
        credentials.save(rewrite(cred, cred.passwordHash(), status, cred.mustChangePwd(), now, actor()));
        auditPublisher.publish(AuditAction.EXECUTE, "platform_credential", userId, "更新账号状态 status=" + status);
    }

    private String normalizedRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        RoleCode role = RoleCode.fromCode(roleCode)
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "非法的系统角色编码: " + roleCode));
        SystemSuperAdminGuard.assertTenantManagedRole(role.code());
        return role.code();
    }

    private boolean hasRole(String tenantId, String userId, String roleCode) {
        return roleAssignments.findActiveByTenantIdAndUserId(tenantId, userId).stream()
            .anyMatch(a -> roleCode.equals(a.roleCode()));
    }

    private PlatformCredential find(String userId) {
        return credentials.findByTenantIdAndUserId(tenantId(), userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_005));
    }

    private PlatformCredential rewrite(PlatformCredential c, String hash, String status,
                                       String mustChangePwd, Instant now, String actor) {
        return new PlatformCredential(
            c.id(), c.credentialId(), c.tenantId(), c.userId(), c.username(),
            hash, status, mustChangePwd, c.mfaSecret(), c.createdAt(), c.createdBy(), now, actor, c.traceId());
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

    private String traceId() {
        String traceId = RequestContext.currentTraceId();
        return traceId == null ? RequestContext.snapshot().traceId() : traceId;
    }
}
