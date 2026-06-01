package com.medkernel.engine.security;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionDimensionModelTest {

    @Test
    void permissionCatalogDeclaresAllFiveDimensions() throws Exception {
        Class<?> dimensionClass = Class.forName("com.medkernel.engine.security.PermissionDimension");

        Set<String> dimensionNames = Arrays.stream(dimensionClass.getEnumConstants())
            .map(Object::toString)
            .collect(Collectors.toSet());

        assertThat(dimensionNames)
            .containsExactlyInAnyOrder("MENU", "ACTION", "DATA", "ASSET", "ENVIRONMENT");
    }

    @Test
    void everyPermissionCodeBelongsToOneOfTheFiveDimensions() throws Exception {
        Method dimensionMethod = PermissionCode.class.getMethod("dimension");

        Set<String> dimensions = Arrays.stream(PermissionCode.values())
            .map(permission -> invokeDimension(dimensionMethod, permission))
            .collect(Collectors.toSet());

        assertThat(dimensions)
            .containsExactlyInAnyOrder("MENU", "ACTION", "DATA", "ASSET", "ENVIRONMENT");
    }

    @Test
    void allThirteenRolesReceiveFiveDimensionalBaselinePermissions() throws Exception {
        Method dimensionMethod = PermissionCode.class.getMethod("dimension");

        assertThat(RoleCode.values()).hasSize(13);
        for (RoleCode role : RoleCode.values()) {
            Set<String> dimensions = DefaultPermissionPolicy.permissionsOf(role).stream()
                .map(permission -> invokeDimension(dimensionMethod, permission))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

            assertThat(dimensions)
                .as("%s 必须拥有菜单、动作、数据、资产、环境五维基线权限", role.code())
                .containsAll(Set.of("MENU", "ACTION", "DATA", "ASSET", "ENVIRONMENT"));
        }
    }

    @Test
    void platformAndGroupAdminsReceiveEveryNonEmergencyPermissionThroughPolicy() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);
        expected.removeAll(MenuPermissionCatalog.legacySectionPermissions());

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_ADMIN))
            .containsAll(expected)
            .doesNotContain(PermissionCode.ENV_EMERGENCY)
            .doesNotContainAnyElementsOf(MenuPermissionCatalog.legacySectionPermissions());
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.GROUP_ADMIN))
            .containsAll(expected)
            .doesNotContain(PermissionCode.ENV_EMERGENCY)
            .doesNotContainAnyElementsOf(MenuPermissionCatalog.legacySectionPermissions());
    }

    private String invokeDimension(Method dimensionMethod, PermissionCode permission) {
        try {
            return String.valueOf(dimensionMethod.invoke(permission));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取权限维度: " + permission.code(), exception);
        }
    }
}
