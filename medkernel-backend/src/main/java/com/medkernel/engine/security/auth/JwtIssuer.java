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

import com.medkernel.shared.context.JwtClaimsResolver;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.security.AuthJwtProperties;

/**
 * 平台 JWT 签发器（HS256，复用 medkernel.jwt.dev-secret，与 devJwtDecoder 对称验签）。
 */
@Component
public class JwtIssuer {

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
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim(JwtClaimsResolver.CLAIM_TENANT_ID, tenantId)
                .claim(JwtClaimsResolver.CLAIM_ROLES, roles)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds())))
                .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 签发失败", e);
        }
    }

    public long ttlSeconds() {
        return configService == null ? properties.ttlSeconds() : configService.runtimeJwtTtlSeconds(properties);
    }
}
