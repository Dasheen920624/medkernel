package com.medkernel.engine.security.bootstrap;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.auth.CredentialPasswordService;
import com.medkernel.engine.security.auth.PasswordPolicyService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;

/**
 * 首次部署身份接管服务：使用一次性 init token 创建首发内置超级管理员。
 */
@Service
public class BootstrapIdentityService {

    private static final String ACTOR = "bootstrap";
    private static final String TRACE_ID = "bootstrap-password";

    private final BootstrapInitTokenService tokenService;
    private final PlatformCredentialRepository credentials;
    private final TenantUserRepository users;
    private final UserRoleAssignmentRepository roleAssignments;
    private final CredentialPasswordService credentialPasswords;
    private final AuditRecorder auditRecorder;
    private final IsolatedAuditPublisher isolatedAudit;
    private final PasswordPolicyService passwordPolicy;

    public BootstrapIdentityService(BootstrapInitTokenService tokenService,
                                    PlatformCredentialRepository credentials,
                                    TenantUserRepository users,
                                    UserRoleAssignmentRepository roleAssignments,
                                    CredentialPasswordService credentialPasswords,
                                    AuditRecorder auditRecorder,
                                    IsolatedAuditPublisher isolatedAudit,
                                    PasswordPolicyService passwordPolicy) {
        this.tokenService = tokenService;
        this.credentials = credentials;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.credentialPasswords = credentialPasswords;
        this.auditRecorder = auditRecorder;
        this.isolatedAudit = isolatedAudit;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional(readOnly = true)
    public BootstrapStartResponse check(BootstrapStartRequest request) {
        BootstrapInitToken token = tokenService.validate(request.token());
        return new BootstrapStartResponse(true, token.expiresAt());
    }

    @Transactional
    public BootstrapPasswordResponse createFirstAdmin(BootstrapPasswordRequest request) {
        String tenantId = request.tenantOrDefault();
        String username = request.usernameNormalized();
        if (credentials.findByTenantIdAndUsername(tenantId, username).isPresent()
                || users.findByTenantIdAndUserId(tenantId, username).isPresent()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE, "platform_credential", username,
                ErrorCode.ENG_AUTH_006.code(), "首次接管失败：用户名已存在 " + username));
            throw new ApiException(ErrorCode.ENG_AUTH_006);
        }
        passwordPolicy.assertCompliant(request.password());

        tokenService.consume(request.token(), username, TRACE_ID);
        Instant now = Instant.now();
        users.save(new TenantUser(
            null, tenantId, username, username, "ACTIVE", 1L,
            now, ACTOR, now, ACTOR, TRACE_ID));
        credentials.save(new PlatformCredential(
            null, "cred-" + username, tenantId, username, username,
            credentialPasswords.encode(request.password()), "ACTIVE", "Y", null,
            now, ACTOR, now, ACTOR, TRACE_ID));
        if (roleAssignments.findActiveByTenantIdAndUserId(tenantId, username).stream()
                .noneMatch(a -> RoleCode.SYSTEM_SUPERADMIN.code().equals(a.roleCode()))) {
            roleAssignments.save(new UserRoleAssignment(
                null, tenantId, username, RoleCode.SYSTEM_SUPERADMIN.code(), "TENANT", tenantId, "Y",
                now, ACTOR, now, ACTOR));
        }
        auditRecorder.record(AuditAction.CREATE, "platform_credential", username,
            "首次部署创建内置超级管理员 username=" + username);
        return new BootstrapPasswordResponse(
            username, tenantId, username, List.of(RoleCode.SYSTEM_SUPERADMIN.code()), true);
    }
}
