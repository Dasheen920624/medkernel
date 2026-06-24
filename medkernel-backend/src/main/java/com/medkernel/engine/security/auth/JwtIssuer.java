package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.JwtClaimsResolver;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.security.AuthSessionClaims;
import com.medkernel.shared.security.AuthJwtProperties;
import com.medkernel.shared.security.JwtSecretResolver;

/**
 * 平台 JWT 签发器（HS256，复用 medkernel.jwt.dev-secret，与 devJwtDecoder 对称验签）。
 */
@Component
public class JwtIssuer {

    public static final String CLAIM_SESSION_STARTED_AT = AuthSessionClaims.SESSION_STARTED_AT;

    private final byte[] secret;
    private final AuthJwtProperties properties;
    private final SystemConfigService configService;

    @Autowired
    public JwtIssuer(
            JwtSecretResolver secretResolver,
            AuthJwtProperties properties,
            SystemConfigService configService) {
        this.secret = secretResolver.resolve().getBytes(StandardCharsets.UTF_8);
        this.properties = properties;
        this.configService = configService;
    }

    JwtIssuer(String secret, long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.properties = new AuthJwtProperties(ttlSeconds, null);
        this.configService = null;
    }

    public String issue(String userId, String tenantId, List<String> roles) {
        return issueSession(userId, tenantId, roles).token();
    }

    public IssuedJwt issueSession(String userId, String tenantId, List<String> roles) {
        Instant now = Instant.now();
        return issueSession(userId, tenantId, roles, OrgScope.tenant(tenantId), now, now, now.plusSeconds(ttlSeconds()));
    }

    public IssuedJwt issueSession(String userId, String tenantId, List<String> roles, OrgScope orgScope) {
        Instant now = Instant.now();
        return issueSession(userId, tenantId, roles, orgScope, now, now, now.plusSeconds(ttlSeconds()), true);
    }

    public IssuedJwt issueSession(
            String userId,
            String tenantId,
            List<String> roles,
            Instant sessionStartedAt,
            Instant expiresAt) {
        return issueSession(userId, tenantId, roles, OrgScope.tenant(tenantId), sessionStartedAt, Instant.now(), expiresAt);
    }

    public IssuedJwt issueSession(
            String userId,
            String tenantId,
            List<String> roles,
            OrgScope orgScope,
            Instant sessionStartedAt,
            Instant expiresAt) {
        return issueSession(userId, tenantId, roles, orgScope, sessionStartedAt, Instant.now(), expiresAt);
    }

    public IssuedJwt issueSession(
            String userId,
            String tenantId,
            List<String> roles,
            Instant sessionStartedAt,
            Instant issuedAt,
            Instant expiresAt) {
        return issueSession(userId, tenantId, roles, OrgScope.tenant(tenantId), sessionStartedAt, issuedAt, expiresAt);
    }

    public IssuedJwt issueSession(
            String userId,
            String tenantId,
            List<String> roles,
            OrgScope orgScope,
            Instant sessionStartedAt,
            Instant issuedAt,
            Instant expiresAt) {
        return issueSession(
            userId, tenantId, roles, orgScope, sessionStartedAt, issuedAt, expiresAt, true);
    }

    public IssuedJwt issueSession(
            String userId,
            String tenantId,
            List<String> roles,
            OrgScope orgScope,
            Instant sessionStartedAt,
            Instant issuedAt,
            Instant expiresAt,
            boolean mfaVerified) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim(JwtClaimsResolver.CLAIM_ROLES, roles)
                .claim(CLAIM_SESSION_STARTED_AT, sessionStartedAt.getEpochSecond())
                .claim(AuthSessionClaims.MFA_VERIFIED, mfaVerified)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
            addOrgClaims(builder, tenantId, orgScope);
            JWTClaimsSet claims = builder.build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return new IssuedJwt(jwt.serialize(), issuedAt, expiresAt, sessionStartedAt);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 签发失败", e);
        }
    }

    public long ttlSeconds() {
        return configService == null ? properties.ttlSeconds() : configService.runtimeJwtTtlSeconds(properties);
    }

    private void addOrgClaims(JWTClaimsSet.Builder builder, String tenantId, OrgScope orgScope) {
        OrgScope safeScope = orgScope == null ? OrgScope.tenant(tenantId) : orgScope;
        builder.claim(JwtClaimsResolver.CLAIM_TENANT_ID, firstNonBlank(safeScope.tenantId(), tenantId));
        addClaimIfPresent(builder, JwtClaimsResolver.CLAIM_GROUP_ID, safeScope.groupId());
        addClaimIfPresent(builder, JwtClaimsResolver.CLAIM_HOSPITAL_ID, safeScope.hospitalId());
        addClaimIfPresent(builder, JwtClaimsResolver.CLAIM_CAMPUS_ID, safeScope.campusId());
        addClaimIfPresent(builder, JwtClaimsResolver.CLAIM_SITE_ID, safeScope.siteId());
        addClaimIfPresent(builder, JwtClaimsResolver.CLAIM_DEPARTMENT_ID, safeScope.departmentId());
        addClaimIfPresent(builder, JwtClaimsResolver.CLAIM_WARD_ID, safeScope.wardId());
        addClaimIfPresent(builder, JwtClaimsResolver.CLAIM_SPECIALTY_ID, safeScope.specialtyId());
    }

    private void addClaimIfPresent(JWTClaimsSet.Builder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.claim(key, value);
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    public record IssuedJwt(
        String token,
        Instant issuedAt,
        Instant expiresAt,
        Instant sessionStartedAt
    ) {}
}
