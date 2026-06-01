package com.medkernel.engine.security.auth;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.medkernel.engine.security.MfaRequirementPolicy;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;

/**
 * 平台账号登录服务：BCrypt 校验凭证 → 取激活角色 → 签发 JWT；成功/失败均留痕审计。
 * 用户不存在与密码错误统一返回 ENG-AUTH-001（防用户名枚举，含 dummy hash 拉平耗时）。
 */
@Service
public class AuthService {

    private final PlatformCredentialRepository credentials;
    private final UserRoleAssignmentRepository roleAssignments;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService sessionService;
    private final IsolatedAuditPublisher isolatedAudit;
    private final AuditEventPublisher auditPublisher;
    private final String dummyHash;

    public AuthService(PlatformCredentialRepository credentials,
                       UserRoleAssignmentRepository roleAssignments,
                       PasswordEncoder passwordEncoder,
                       AuthSessionService sessionService,
                       IsolatedAuditPublisher isolatedAudit,
                       AuditEventPublisher auditPublisher) {
        this.credentials = credentials;
        this.roleAssignments = roleAssignments;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.isolatedAudit = isolatedAudit;
        this.auditPublisher = auditPublisher;
        this.dummyHash = passwordEncoder.encode("__medkernel_dummy_account__");
    }

    public AuthResult login(String tenantId, String username, String rawPassword) {
        PlatformCredential cred = credentials.findByTenantIdAndUsername(tenantId, username).orElse(null);
        // C1: 无论用户是否存在都跑一次 BCrypt，拉平 timing 防枚举
        String hashToCompare = (cred != null) ? cred.passwordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCompare);
        if (cred == null || !passwordMatches) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.LOGIN, "platform_credential", username,
                ErrorCode.ENG_AUTH_001.code(), "登录失败：用户名或密码不正确 username=" + username));
            throw new ApiException(ErrorCode.ENG_AUTH_001);
        }
        if (!cred.active()) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.LOGIN, "platform_credential", cred.userId(),
                ErrorCode.ENG_AUTH_002.code(), "登录失败：账号禁用/锁定 status=" + cred.status()));
            throw new ApiException(ErrorCode.ENG_AUTH_002);
        }
        List<String> roles = roleAssignments
            .findActiveByTenantIdAndUserId(tenantId, cred.userId())
            .stream().map(UserRoleAssignment::roleCode).distinct().toList();
        JwtIssuer.IssuedJwt jwt = sessionService.issueInitialSession(cred.userId(), tenantId, roles);
        // I3: 成功路径用 AuditEventPublisher.publish
        auditPublisher.publish(AuditAction.LOGIN, "platform_credential", cred.userId(),
            "登录成功 username=" + username + " roles=" + roles);
        return new AuthResult(jwt,
            new LoginResponse(cred.userId(), tenantId, roles, "Y".equalsIgnoreCase(cred.mustChangePwd()),
                MfaRequirementPolicy.requiresMfa(roles), cred.mfaSecret() != null && !cred.mfaSecret().isBlank()));
    }

    public void logout(String userId) {
        // I3: 登出也用 AuditEventPublisher.publish
        auditPublisher.publish(AuditAction.LOGOUT, "platform_credential",
            userId == null ? "anonymous" : userId, "登出");
    }

    /**
     * 自助改密：校验原密码后设置新密码并清除"首登须改密"标志。
     * 账号不存在抛 {@code ENG_AUTH_005}；原密码错抛 {@code ENG_AUTH_004}（失败留痕）。
     */
    public void changePassword(String tenantId, String userId, String oldPassword, String newPassword) {
        PlatformCredential cred = credentials.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_005));
        if (!passwordEncoder.matches(oldPassword, cred.passwordHash())) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE, "platform_credential", userId,
                ErrorCode.ENG_AUTH_004.code(), "改密失败：原密码不正确 userId=" + userId));
            throw new ApiException(ErrorCode.ENG_AUTH_004);
        }
        java.time.Instant now = java.time.Instant.now();
        credentials.save(new PlatformCredential(
            cred.id(), cred.credentialId(), cred.tenantId(), cred.userId(), cred.username(),
            passwordEncoder.encode(newPassword), cred.status(), "N", cred.mfaSecret(),
            cred.createdAt(), cred.createdBy(), now, userId, cred.traceId()));
        auditPublisher.publish(AuditAction.EXECUTE, "platform_credential", userId, "自助修改密码成功");
    }

    public record AuthResult(JwtIssuer.IssuedJwt issuedJwt, LoginResponse response) {
        public String jwt() {
            return issuedJwt.token();
        }
    }

}
