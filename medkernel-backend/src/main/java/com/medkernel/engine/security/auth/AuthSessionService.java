package com.medkernel.engine.security.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.JwtClaimsResolver;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.security.AuthCookieProperties;
import com.medkernel.shared.security.AuthSessionClaims;
import com.medkernel.shared.security.AuthSessionProperties;

/**
 * 无状态 JWT 会话策略服务。
 *
 * <p>服务端以 JWT 签名、过期时间和配置中心策略为准，不在前端保存令牌或伪造会话状态。
 */
@Service
public class AuthSessionService {

    private final JwtIssuer jwtIssuer;
    private final SystemConfigService configService;
    private final AuthSessionProperties sessionProperties;

    public AuthSessionService(JwtIssuer jwtIssuer,
                              SystemConfigService configService,
                              AuthSessionProperties sessionProperties) {
        this.jwtIssuer = jwtIssuer;
        this.configService = configService;
        this.sessionProperties = sessionProperties;
    }

    public SessionStatusResponse status(Jwt jwt) {
        return status(jwt.getExpiresAt(), sessionStartedAt(jwt), Instant.now(), runtimePolicy());
    }

    public SessionStatusResponse status(JwtIssuer.IssuedJwt issuedJwt) {
        return status(issuedJwt.expiresAt(), issuedJwt.sessionStartedAt(), Instant.now(), runtimePolicy());
    }

    public JwtIssuer.IssuedJwt issueInitialSession(String userId, String tenantId, List<String> roles) {
        return issueInitialSession(userId, tenantId, roles, OrgScope.tenant(tenantId));
    }

    public JwtIssuer.IssuedJwt issueInitialSession(
            String userId,
            String tenantId,
            List<String> roles,
            OrgScope orgScope) {
        return issueInitialSession(userId, tenantId, roles, orgScope, true);
    }

    public JwtIssuer.IssuedJwt issueInitialSession(
            String userId,
            String tenantId,
            List<String> roles,
            OrgScope orgScope,
            boolean mfaVerified) {
        AuthSessionProperties policy = runtimePolicy();
        Instant now = Instant.now();
        return issueWithinPolicy(userId, tenantId, roles, orgScope, now, now, policy, mfaVerified);
    }

    public RenewedSession renew(Jwt jwt) {
        AuthSessionProperties policy = runtimePolicy();
        Instant now = Instant.now();
        Instant sessionStartedAt = sessionStartedAt(jwt);
        String tenantId = jwt.getClaimAsString(JwtClaimsResolver.CLAIM_TENANT_ID);
        List<String> roles = List.copyOf(JwtClaimsResolver.resolveRoles(jwt));
        OrgScope orgScope = JwtClaimsResolver.resolveOrgScope(jwt);
        JwtIssuer.IssuedJwt issued = issueWithinPolicy(
            jwt.getSubject(), tenantId, roles, orgScope, sessionStartedAt, now, policy, mfaVerified(jwt));
        return new RenewedSession(issued, status(issued.expiresAt(), sessionStartedAt, now, policy));
    }

    public RenewedSession completeMfa(Jwt jwt) {
        AuthSessionProperties policy = runtimePolicy();
        Instant now = Instant.now();
        Instant sessionStartedAt = sessionStartedAt(jwt);
        String tenantId = jwt.getClaimAsString(JwtClaimsResolver.CLAIM_TENANT_ID);
        List<String> roles = List.copyOf(JwtClaimsResolver.resolveRoles(jwt));
        OrgScope orgScope = JwtClaimsResolver.resolveOrgScope(jwt);
        JwtIssuer.IssuedJwt issued = issueWithinPolicy(
            jwt.getSubject(), tenantId, roles, orgScope, sessionStartedAt, now, policy, true);
        return new RenewedSession(issued, status(issued.expiresAt(), sessionStartedAt, now, policy));
    }

    public long cookieMaxAgeSeconds(JwtIssuer.IssuedJwt issued, AuthCookieProperties cookieProperties) {
        long tokenWindowSeconds = Math.max(0, Duration.between(issued.issuedAt(), issued.expiresAt()).toSeconds());
        return Math.min(cookieProperties.maxAgeSeconds(), tokenWindowSeconds);
    }

    private AuthSessionProperties runtimePolicy() {
        return configService.runtimeSessionProperties(sessionProperties);
    }

    private JwtIssuer.IssuedJwt issueWithinPolicy(
            String userId,
            String tenantId,
            List<String> roles,
            OrgScope orgScope,
            Instant sessionStartedAt,
            Instant now,
            AuthSessionProperties policy,
            boolean mfaVerified) {
        Instant maxExpiresAt = sessionStartedAt.plusSeconds(policy.maxDurationSeconds());
        if (!maxExpiresAt.isAfter(now)) {
            throw new ApiException(ErrorCode.ENG_AUTH_012);
        }
        Instant expiresAt = now.plusSeconds(policy.idleTimeoutSeconds());
        if (expiresAt.isAfter(maxExpiresAt)) {
            expiresAt = maxExpiresAt;
        }
        if (!expiresAt.isAfter(now)) {
            throw new ApiException(ErrorCode.ENG_AUTH_012);
        }
        return jwtIssuer.issueSession(
            userId, tenantId, roles, orgScope, sessionStartedAt, now, expiresAt, mfaVerified);
    }

    private static SessionStatusResponse status(
            Instant expiresAt,
            Instant sessionStartedAt,
            Instant now,
            AuthSessionProperties policy) {
        Instant maxExpiresAt = sessionStartedAt.plusSeconds(policy.maxDurationSeconds());
        Instant effectiveExpiresAt = expiresAt.isBefore(maxExpiresAt) ? expiresAt : maxExpiresAt;
        if (!effectiveExpiresAt.isAfter(now)) {
            throw new ApiException(ErrorCode.ENG_AUTH_012);
        }
        long remaining = Math.max(0, Duration.between(now, effectiveExpiresAt).toSeconds());
        long maxRemaining = Math.max(0, Duration.between(now, maxExpiresAt).toSeconds());
        return new SessionStatusResponse(
            remaining,
            policy.idleTimeoutSeconds(),
            policy.warningSeconds(),
            policy.maxDurationSeconds(),
            maxRemaining,
            now.toString());
    }

    private static Instant sessionStartedAt(Jwt jwt) {
        Object claim = jwt.getClaims().get(AuthSessionClaims.SESSION_STARTED_AT);
        if (claim instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        if (claim instanceof String text && !text.isBlank()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(text.trim()));
            } catch (NumberFormatException ignored) {
                return issuedAtOrNow(jwt);
            }
        }
        return issuedAtOrNow(jwt);
    }

    private static Instant issuedAtOrNow(Jwt jwt) {
        return jwt.getIssuedAt() == null ? Instant.now() : jwt.getIssuedAt();
    }

    private static boolean mfaVerified(Jwt jwt) {
        return Boolean.TRUE.equals(jwt.getClaim(AuthSessionClaims.MFA_VERIFIED));
    }

    public record RenewedSession(
        JwtIssuer.IssuedJwt issuedJwt,
        SessionStatusResponse status
    ) {}
}
