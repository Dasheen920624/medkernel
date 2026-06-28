package com.medkernel.engine.security.auth;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
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
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.security.MfaRuntimePolicy;

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
    private final OrgUnitRepository orgUnits;
    private final MfaRuntimePolicy mfaRuntimePolicy;

    public AuthService(PlatformCredentialRepository credentials,
                       UserRoleAssignmentRepository roleAssignments,
                       CredentialPasswordService credentialPasswords,
                       AuthSessionService sessionService,
                       IsolatedAuditPublisher isolatedAudit,
                       AuditRecorder auditRecorder,
                       SystemConfigService configService,
                       LoginAttemptService loginAttempts,
                       PasswordPolicyService passwordPolicy,
                       MfaSecretCodec mfaSecretCodec,
                       OrgUnitRepository orgUnits,
                       MfaRuntimePolicy mfaRuntimePolicy) {
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
        this.orgUnits = orgUnits;
        this.mfaRuntimePolicy = mfaRuntimePolicy;
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
        List<UserRoleAssignment> assignments = roleAssignments
            .findActiveByTenantIdAndUserId(tenantId, cred.userId());
        List<String> roles = assignments.stream().map(UserRoleAssignment::roleCode).distinct().toList();
        boolean mfaRequired = mfaRuntimePolicy.enabled();
        JwtIssuer.IssuedJwt jwt = sessionService.issueInitialSession(
            cred.userId(),
            tenantId,
            roles,
            primaryOrgScope(tenantId, assignments),
            !mfaRequired);
        // I3: 成功路径用 AuditRecorder.publish
        auditRecorder.record(AuditAction.LOGIN, "platform_credential", cred.userId(),
            "登录成功 username=" + username + " roles=" + roles);
        return new AuthResult(jwt,
            new LoginResponse(cred.userId(), tenantId, roles, "Y".equalsIgnoreCase(cred.mustChangePwd()),
                mfaRequired, mfaSecretCodec.isTotpBound(cred.mfaSecret())));
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

    private OrgScope primaryOrgScope(String tenantId, List<UserRoleAssignment> assignments) {
        return assignments.stream()
            .filter(UserRoleAssignment::active)
            .max(Comparator.comparingInt(this::scopeSpecificity)
                .thenComparing(UserRoleAssignment::roleCode)
                .thenComparing(UserRoleAssignment::scopeCode))
            .map(assignment -> orgScopeOf(tenantId, assignment))
            .orElseGet(() -> OrgScope.tenant(tenantId));
    }

    private int scopeSpecificity(UserRoleAssignment assignment) {
        return switch (scopeLevel(assignment)) {
            case "WARD" -> 60;
            case "DEPARTMENT" -> 50;
            case "CAMPUS" -> 40;
            case "FACILITY" -> 30;
            case "REGION" -> 20;
            case "SPECIALTY" -> 10;
            default -> 0;
        };
    }

    private OrgScope orgScopeOf(String tenantId, UserRoleAssignment assignment) {
        String level = scopeLevel(assignment);
        String code = safeCode(assignment.scopeCode(), tenantId);
        if ("TENANT".equals(level)) {
            return OrgScope.tenant(tenantId);
        }
        OrgUnit unit = orgUnits.findByTenantIdAndId(tenantId, code)
            .or(() -> orgUnits.findByTenantIdAndCode(tenantId, code))
            .orElse(null);
        if (unit == null) {
            return fallbackOrgScope(tenantId, level, code);
        }
        return orgScopeFromUnit(tenantId, level, unit);
    }

    private OrgScope orgScopeFromUnit(String tenantId, String assignmentLevel, OrgUnit unit) {
        String groupId = null;
        String hospitalId = null;
        String campusId = null;
        String siteId = null;
        String departmentId = null;
        String wardId = null;
        String specialtyId = null;
        OrgLevel level = unit.level();
        if ("SPECIALTY".equals(assignmentLevel)) {
            specialtyId = firstText(unit.specialtyId(), unit.id());
        } else if (level == OrgLevel.REGION || "REGION".equals(assignmentLevel)) {
            groupId = unit.id();
        } else if (level == OrgLevel.FACILITY || "FACILITY".equals(assignmentLevel)) {
            hospitalId = unit.id();
            groupId = ancestorId(tenantId, unit, OrgLevel.REGION);
        } else if (level == OrgLevel.CAMPUS) {
            campusId = unit.id();
            hospitalId = ancestorId(tenantId, unit, OrgLevel.FACILITY);
            groupId = ancestorId(tenantId, unit, OrgLevel.REGION);
        } else if (level == OrgLevel.DEPARTMENT) {
            departmentId = unit.id();
            campusId = ancestorId(tenantId, unit, OrgLevel.CAMPUS);
            hospitalId = ancestorId(tenantId, unit, OrgLevel.FACILITY);
            groupId = ancestorId(tenantId, unit, OrgLevel.REGION);
        } else if (level == OrgLevel.WARD || "WARD".equals(assignmentLevel)) {
            wardId = unit.id();
            departmentId = ancestorId(tenantId, unit, OrgLevel.DEPARTMENT);
            campusId = ancestorId(tenantId, unit, OrgLevel.CAMPUS);
            hospitalId = ancestorId(tenantId, unit, OrgLevel.FACILITY);
            groupId = ancestorId(tenantId, unit, OrgLevel.REGION);
        }
        return new OrgScope(
            tenantId,
            groupId,
            hospitalId,
            campusId,
            siteId,
            departmentId,
            wardId,
            specialtyId);
    }

    private OrgScope fallbackOrgScope(String tenantId, String level, String code) {
        return switch (level) {
            case "REGION" -> new OrgScope(tenantId, code, null, null, null, null, null, null);
            case "FACILITY" -> new OrgScope(tenantId, null, code, null, null, null, null, null);
            case "CAMPUS" -> new OrgScope(tenantId, null, null, code, null, null, null, null);
            case "DEPARTMENT" -> new OrgScope(tenantId, null, null, null, null, code, null, null);
            case "WARD" -> new OrgScope(
                tenantId, null, null, null, null, null, code, null);
            case "SPECIALTY" -> new OrgScope(tenantId, null, null, null, null, null, null, code);
            default -> OrgScope.tenant(tenantId);
        };
    }

    private String ancestorId(String tenantId, OrgUnit unit, OrgLevel targetLevel) {
        String parentId = unit.parentId();
        while (parentId != null && !parentId.isBlank()) {
            OrgUnit parent = orgUnits.findByTenantIdAndId(tenantId, parentId).orElse(null);
            if (parent == null) {
                return null;
            }
            if (parent.level() == targetLevel) {
                return parent.id();
            }
            parentId = parent.parentId();
        }
        return null;
    }

    private String scopeLevel(UserRoleAssignment assignment) {
        return assignment.scopeLevel() == null ? "TENANT" : assignment.scopeLevel().trim().toUpperCase(Locale.ROOT);
    }

    private String safeCode(String code, String tenantId) {
        return code == null || code.isBlank() ? tenantId : code.trim();
    }

    private String firstText(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

}
