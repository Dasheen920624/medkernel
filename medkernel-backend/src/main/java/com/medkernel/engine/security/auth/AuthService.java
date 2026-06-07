package com.medkernel.engine.security.auth;

import java.util.List;

import org.springframework.stereotype.Service;

import com.medkernel.engine.security.MfaRequirementPolicy;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.bootstrap.MfaSecretCodec;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.config.SystemConfigService;

/**
 * 平台账号登录服务：校验平台凭证 → 取激活角色 → 签发 JWT；成功/失败均留痕审计。
 * 用户不存在与密码错误统一返回 ENG-AUTH-001（防用户名枚举，含 dummy hash 拉平耗时）。
 */
@Service
public class AuthService {

    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final CredentialPasswordService credentialPasswords;
    private final AuthSessionService sessionService;
    private final IsolatedAuditPublisher isolatedAudit;
    private final AuditRecorder auditRecorder;
    private final SystemConfigService configService;
    private final LoginAttemptService loginAttempts;
    private final PasswordPolicyService passwordPolicy;
    private final MfaSecretCodec mfaSecretCodec;

    public AuthService(PlatformCredentialRepository credentials,
                       UserRoleAssignmentRepository roleAssignments,
                       CredentialPasswordService credentialPasswords,
                       AuthSessionService sessionService,
                       IsolatedAuditPublisher isolatedAudit,
                       AuditRecorder auditRecorder,
                       SystemConfigService configService,
                       LoginAttemptService loginAttempts,
                       PasswordPolicyService passwordPolicy,
                       MfaSecretCodec mfaSecretCodec) {
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.credentialPasswords = credentialPasswords;
        this.sessionService = sessionService;
        this.isolatedAudit = isolatedAudit;
        this.auditRecorder = auditRecorder;
        this.configService = configService;
        this.loginAttempts = loginAttempts;
        this.passwordPolicy = passwordPolicy;
        this.mfaSecretCodec = mfaSecretCodec;
    }

    public AuthResult login(String tenantId, String username, String rawPassword) {
        if (!configService.runtimeAuthMode().allowsPlatformLogin()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.LOGIN, "platform_credential", username,
                ErrorCode.ENG_AUTH_013.code(), "登录失败：当前认证模式不允许平台账号登录 username=" + username));
            throw new ApiException(ErrorCode.ENG_AUTH_013);
        }
        PlatformCredential cred = credentials.findByTenantIdAndUsername(tenantId, username).orElse(null);
        if (cred != null) {
            cred = loginAttempts.unlockExpiredAutoLock(cred);
        }
        // C1: 无论用户是否存在都跑一次口令哈希校验，拉平 timing 防枚举
        boolean passwordMatches = (cred != null)
            ? credentialPasswords.matches(rawPassword, cred.passwordHash())
            : credentialPasswords.matchesDummy(rawPassword);
        if (cred == null || !passwordMatches) {
            LoginAttemptService.FailureOutcome outcome = loginAttempts.recordFailure(tenantId, username, cred);
            ErrorCode errorCode = switch (outcome) {
                case LOCKED -> ErrorCode.ENG_AUTH_002;
                case RATE_LIMITED -> ErrorCode.TOO_MANY_REQUESTS;
                case FAILED -> ErrorCode.ENG_AUTH_001;
            };
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.LOGIN, "platform_credential", username,
                errorCode.code(), "登录失败：用户名或密码不正确 username=" + username));
            throw new ApiException(errorCode);
        }
        if (!cred.active()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.LOGIN, "platform_credential", cred.userId(),
                ErrorCode.ENG_AUTH_002.code(), "登录失败：账号不可用 userId=" + cred.userId()));
            throw new ApiException(ErrorCode.ENG_AUTH_002);
        }
        loginAttempts.resetOnSuccess(tenantId, username);
        List<String> roles = roleAssignments
            .findActiveByTenantIdAndUserId(tenantId, cred.userId())
            .stream().map(UserRoleAssignment::roleCode).distinct().toList();
        JwtIssuer.IssuedJwt jwt = sessionService.issueInitialSession(cred.userId(), tenantId, roles);
        // I3: 成功路径用 AuditRecorder.publish
        auditRecorder.record(AuditAction.LOGIN, "platform_credential", cred.userId(),
            "登录成功 username=" + username + " roles=" + roles);
        return new AuthResult(jwt,
            new LoginResponse(cred.userId(), tenantId, roles, "Y".equalsIgnoreCase(cred.mustChangePwd()),
                MfaRequirementPolicy.requiresMfa(roles), mfaSecretCodec.isTotpBound(cred.mfaSecret())));
    }

    public void logout(String userId) {
        // I3: 登出也用 AuditRecorder.publish
        auditRecorder.record(AuditAction.LOGOUT, "platform_credential",
            userId == null ? "anonymous" : userId, "登出");
    }

    /**
     * 自助改密：校验原密码后设置新密码并清除"首登须改密"标志。
     * 账号不存在抛 {@code ENG_AUTH_005}；原密码错抛 {@code ENG_AUTH_004}（失败留痕）。
     */
    public void changePassword(String tenantId, String userId, String oldPassword, String newPassword) {
        PlatformCredential cred = credentials.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_005));
        if (!credentialPasswords.matches(oldPassword, cred.passwordHash())) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE, "platform_credential", userId,
                ErrorCode.ENG_AUTH_004.code(), "改密失败：原密码不正确 userId=" + userId));
            throw new ApiException(ErrorCode.ENG_AUTH_004);
        }
        passwordPolicy.assertCompliant(newPassword);
        java.time.Instant now = java.time.Instant.now();
        credentials.save(new PlatformCredential(
            cred.id(), cred.credentialId(), cred.tenantId(), cred.userId(), cred.username(),
            credentialPasswords.encode(newPassword), cred.status(), "N", cred.mfaSecret(),
            cred.createdAt(), cred.createdBy(), now, userId, cred.traceId()));
        auditRecorder.record(AuditAction.EXECUTE, "platform_credential", userId, "自助修改密码成功");
    }

    public record AuthResult(JwtIssuer.IssuedJwt issuedJwt, LoginResponse response) {
        public String jwt() {
            return issuedJwt.token();
        }
    }

}
