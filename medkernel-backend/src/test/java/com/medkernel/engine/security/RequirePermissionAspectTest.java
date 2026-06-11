package com.medkernel.engine.security;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.shared.api.error.PermissionDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirePermissionAspectTest {

    private RequirePermissionAspect aspect;

    @BeforeEach
    void setUp() {
        var rolePermissionRepository = Mockito.mock(RolePermissionOverrideRepository.class);
        var userRoleAssignmentRepository = Mockito.mock(UserRoleAssignmentRepository.class);
        Mockito.when(rolePermissionRepository.findByTenantIdAndRoleCodes(Mockito.anyString(), Mockito.anyCollection()))
            .thenReturn(List.of());
        Mockito.when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(List.of());
        aspect = new RequirePermissionAspect(
            new PermissionEvaluator(new EffectivePermissionService(rolePermissionRepository, userRoleAssignmentRepository)));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void annotatedMethodIsAllowedOnlyWhenEffectivePermissionExists() throws Exception {
        RequirePermission annotation = annotatedPermission();

        authenticate(RoleCode.CLINICAL_DECISION_USER);
        assertThatThrownBy(() -> aspect.enforce(annotation))
            .isInstanceOf(PermissionDeniedException.class)
            .hasMessageContaining(PermissionCode.RULE_PUBLISH.code());

        authenticate(RoleCode.CLINICAL_GOVERNOR);
        assertThatCode(() -> aspect.enforce(annotation)).doesNotThrowAnyException();
    }

    private RequirePermission annotatedPermission() throws NoSuchMethodException {
        Method method = AnnotatedService.class.getDeclaredMethod("publishRule");
        return method.getAnnotation(RequirePermission.class);
    }

    private void authenticate(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "user-1",
            "n/a",
            List.of(new SimpleGrantedAuthority(role.authority()))
        ));
    }

    private static final class AnnotatedService {

        @RequirePermission(PermissionCode.RULE_PUBLISH)
        void publishRule() {
        }
    }
}
