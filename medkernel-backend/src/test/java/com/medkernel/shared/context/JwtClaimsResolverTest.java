package com.medkernel.shared.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class JwtClaimsResolverTest {

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            claims
        );
    }

    @Test
    void resolvesFullOrgScope() {
        Jwt token = jwt(Map.of(
            "sub", "u-7",
            "tenant_id", "t-1",
            "group_id", "g-1",
            "hospital_id", "h-1",
            "campus_id", "c-1",
            "site_id", "s-1",
            "department_id", "d-1",
            "ward_id", "w-1",
            "specialty_id", "sp-cardio"
        ));

        OrgScope scope = JwtClaimsResolver.resolveOrgScope(token);
        assertThat(scope.tenantId()).isEqualTo("t-1");
        assertThat(scope.groupId()).isEqualTo("g-1");
        assertThat(scope.hospitalId()).isEqualTo("h-1");
        assertThat(scope.campusId()).isEqualTo("c-1");
        assertThat(scope.siteId()).isEqualTo("s-1");
        assertThat(scope.departmentId()).isEqualTo("d-1");
        assertThat(scope.wardId()).isEqualTo("w-1");
        assertThat(scope.specialtyId()).isEqualTo("sp-cardio");
        assertThat(JwtClaimsResolver.resolveUserId(token)).isEqualTo("u-7");
    }

    @Test
    void missingClaimsAreNullSafe() {
        Jwt token = jwt(Map.of("sub", "u-9", "tenant_id", "t-2"));
        OrgScope scope = JwtClaimsResolver.resolveOrgScope(token);
        assertThat(scope.tenantId()).isEqualTo("t-2");
        assertThat(scope.hospitalId()).isNull();
        assertThat(scope.departmentId()).isNull();
        assertThat(scope.hasTenant()).isTrue();
    }

    @Test
    void rolesClaimSupportsListForm() {
        Jwt token = jwt(Map.of(
            "sub", "u", "tenant_id", "t",
            "roles", List.of("clinical-user", "engine-operator")
        ));
        assertThat(JwtClaimsResolver.resolveRoles(token))
            .containsExactlyInAnyOrder("clinical-user", "engine-operator");
    }

    @Test
    void rolesClaimSupportsCommaSeparatedString() {
        Jwt token = jwt(Map.of(
            "sub", "u", "tenant_id", "t",
            "roles", "clinical-user, engine-operator ,platform-admin"
        ));
        assertThat(JwtClaimsResolver.resolveRoles(token))
            .containsExactlyInAnyOrder("clinical-user", "engine-operator", "platform-admin");
    }

    @Test
    void rolesAbsentReturnsEmpty() {
        Jwt token = jwt(Map.of("sub", "u", "tenant_id", "t"));
        assertThat(JwtClaimsResolver.resolveRoles(token)).isEmpty();
    }

    @Test
    void nullJwtReturnsEmptyScope() {
        OrgScope scope = JwtClaimsResolver.resolveOrgScope(null);
        assertThat(scope.hasTenant()).isFalse();
        assertThat(JwtClaimsResolver.resolveUserId(null)).isNull();
        assertThat(JwtClaimsResolver.resolveRoles(null)).isEmpty();
    }
}
