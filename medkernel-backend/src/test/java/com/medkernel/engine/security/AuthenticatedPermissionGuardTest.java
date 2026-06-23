package com.medkernel.engine.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedPermissionGuardTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void engineOperatorUsesFixedPermissionBundle() {
        authenticate("ROLE_ENGINE_OPERATOR");

        assertThat(AuthenticatedPermissionGuard.has(PermissionCode.KNOWLEDGE_PUBLISH)).isTrue();
        assertThat(AuthenticatedPermissionGuard.has(PermissionCode.RULE_PUBLISH)).isTrue();
        assertThat(AuthenticatedPermissionGuard.has(PermissionCode.TENANT_WRITE)).isFalse();
    }

    @Test
    void clinicalUserCannotCrossIntoKnowledgeGovernance() {
        authenticate("ROLE_CLINICAL_USER");

        assertThat(AuthenticatedPermissionGuard.has(PermissionCode.RECOMMENDATION_ACCEPT)).isTrue();
        assertThat(AuthenticatedPermissionGuard.has(PermissionCode.KNOWLEDGE_PUBLISH)).isFalse();
    }

    @Test
    void legacyAuthorityDoesNotRestoreCompatibility() {
        authenticate("ROLE_QUALITY_GOVERNOR");

        assertThat(AuthenticatedPermissionGuard.has(PermissionCode.EVALUATION_PUBLISH)).isFalse();
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "test-user",
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))
            )
        );
    }
}
