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

        assertThat(Arrays.stream(RoleCode.values()).filter(RoleCode::customerAssignable).toList()).hasSize(13);
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
    void systemSuperAdminIsSeparateFromThirteenCustomerAssignableRoles() {
        assertThat(RoleCode.fromCode("system-superadmin"))
            .as("内置超级管理员必须是系统内置角色，而不是手工配置出来的普通角色")
            .isPresent();

        assertThat(Arrays.stream(RoleCode.values())
                .filter(role -> !"system-superadmin".equals(role.code()))
                .toList())
            .as("13 个客户可分配业务角色矩阵不能被超管挤占")
            .hasSize(13);
    }

    @Test
    void platformAndGroupAdminsReceiveEveryNonEmergencyPermissionThroughPolicy() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_ADMIN))
            .containsAll(expected)
            .doesNotContain(PermissionCode.ENV_EMERGENCY);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.GROUP_ADMIN))
            .containsAll(expected)
            .doesNotContain(PermissionCode.ENV_EMERGENCY);
    }

    private String invokeDimension(Method dimensionMethod, PermissionCode permission) {
        try {
            return String.valueOf(dimensionMethod.invoke(permission));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取权限维度: " + permission.code(), exception);
        }
    }
}
