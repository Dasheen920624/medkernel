package com.medkernel.engine.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.medkernel.shared.security.AuthJwtProperties;
import com.medkernel.shared.security.JwtSecretResolver;

class JwtSecretResolverTest {

    @Test
    void devAndTestProfilesMayUseExplicitDevSecretFallback() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "test");
        JwtSecretResolver resolver = new JwtSecretResolver(
            new AuthJwtProperties(28800, null), environment);

        assertThat(resolver.resolve()).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void productionRequiresExplicitAuthJwtSecret() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        JwtSecretResolver resolver = new JwtSecretResolver(
            new AuthJwtProperties(28800, null), environment);

        assertThatThrownBy(resolver::resolve)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MEDKERNEL_AUTH_JWT_SECRET");
    }

    @Test
    void productionAcceptsExplicitAuthJwtSecret() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        JwtSecretResolver resolver = new JwtSecretResolver(
            new AuthJwtProperties(28800, "prod-secret-with-at-least-32-bytes"), environment);

        assertThat(resolver.resolve()).isEqualTo("prod-secret-with-at-least-32-bytes");
    }
}
