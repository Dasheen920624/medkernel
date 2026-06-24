package com.medkernel.engine.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtIssuerTest {

    private static final String SECRET = "medkernel-dev-secret-please-change-at-least-32-bytes";

    @Test
    void issuedTokenCarriesSubTenantRolesAndIsVerifiable() {
        JwtIssuer issuer = new JwtIssuer(SECRET, 28800);
        String token = issuer.issue("doctor-1", "t-1", List.of("clinical-user", "engine-operator"));

        JwtDecoder decoder = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(SECRET.getBytes(), "HmacSHA256")).build();
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("doctor-1");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("t-1");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("clinical-user", "engine-operator");
        assertThat(jwt.getExpiresAt()).isNotNull();
    }
}
