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
    void allFourteenCustomerRolesReceiveApplicableBaselinePermissions() throws Exception {
        Method dimensionMethod = PermissionCode.class.getMethod("dimension");

        var customerRoles = Arrays.stream(RoleCode.values()).filter(RoleCode::customerAssignable).toList();
        assertThat(customerRoles).hasSize(14);
        for (RoleCode role : customerRoles) {
            Set<String> dimensions = DefaultPermissionPolicy.permissionsOf(role).stream()
                .map(permission -> invokeDimension(dimensionMethod, permission))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

            assertThat(dimensions)
                .as("%s 必须拥有菜单、动作、数据和环境基线权限", role.code())
                .containsAll(Set.of("MENU", "ACTION", "DATA", "ENVIRONMENT"));
        }

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IDENTITY_ACCESS_ADMIN))
            .as("人员与访问管理员不应因五维模型被强制授予无关知识资产权限")
            .noneMatch(permission -> permission.dimension() == PermissionDimension.ASSET);
    }

    @Test
    void systemSuperAdminIsSeparateFromFourteenCustomerAssignableRoles() {
        assertThat(RoleCode.fromCode("system-superadmin"))
            .as("内置超级管理员必须是系统内置角色，而不是手工配置出来的普通角色")
            .isPresent();

        assertThat(Arrays.stream(RoleCode.values())
                .filter(role -> !"system-superadmin".equals(role.code()))
                .toList())
            .as("14 个客户可分配职责角色矩阵不能被超管挤占")
            .hasSize(14);
    }

    @Test
    void platformGovernanceAdminReceivesAllNonMenuGovernancePermissionsThroughPolicy() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);
        expected.removeIf(permission -> permission.dimension() == PermissionDimension.MENU);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_GOVERNANCE_ADMIN))
            .filteredOn(permission -> permission.dimension() != PermissionDimension.MENU)
            .containsExactlyInAnyOrderElementsOf(expected);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_GOVERNANCE_ADMIN))
            .filteredOn(permission -> permission.dimension() == PermissionDimension.MENU)
            .containsExactlyInAnyOrder(
                PermissionCode.MENU_WORKBENCH,
                PermissionCode.MENU_TENANT_ONBOARDING,
                PermissionCode.MENU_ADMIN_USERS,
                PermissionCode.MENU_IDENTITY_BINDINGS,
                PermissionCode.MENU_IMPLEMENTATION_GUIDE,
                PermissionCode.MENU_KNOWLEDGE_GOVERNANCE,
                PermissionCode.MENU_DIAGNOSIS_KNOWLEDGE,
                PermissionCode.MENU_CONFIG_PACKAGES,
                PermissionCode.MENU_QC_DASHBOARD,
                PermissionCode.MENU_ADMIN_AUDIT,
                PermissionCode.MENU_SECURITY_BASELINE,
                PermissionCode.MENU_SYSTEM_PROVIDERS,
                PermissionCode.MENU_NOTIFICATION_SETTINGS,
                PermissionCode.MENU_PROVENANCE,
                PermissionCode.MENU_AI_WORKFLOWS,
                PermissionCode.MENU_DOMESTIC_CHECK);
    }

    @Test
    void organizationAdminReceivesTenantGovernanceWithoutPlatformOrSystemOperations() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);
        expected.remove(PermissionCode.PLATFORM_PUBLISH);
        expected.remove(PermissionCode.SYSTEM_MANAGE);
        expected.removeIf(permission -> permission.dimension() == PermissionDimension.MENU);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.ORGANIZATION_ADMIN))
            .filteredOn(permission -> permission.dimension() != PermissionDimension.MENU)
            .containsExactlyInAnyOrderElementsOf(expected);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.ORGANIZATION_ADMIN))
            .filteredOn(permission -> permission.dimension() == PermissionDimension.MENU)
            .containsExactlyInAnyOrder(
                PermissionCode.MENU_WORKBENCH,
                PermissionCode.MENU_TENANT_ONBOARDING,
                PermissionCode.MENU_ADMIN_USERS,
                PermissionCode.MENU_IDENTITY_BINDINGS,
                PermissionCode.MENU_IMPLEMENTATION_GUIDE,
                PermissionCode.MENU_KNOWLEDGE_GOVERNANCE,
                PermissionCode.MENU_CONFIG_PACKAGES,
                // 机构管理员是红线规则唯一职责分离合规发布人，需进入规则配置页推进发布。见 P5-ACT4-02。
                PermissionCode.MENU_RULE_DEFINITIONS,
                // 机构管理员持 pathway.publish，是路径模板合法院级全量协调角色，需进入路径配置页。见 P5-ACT5-01。
                PermissionCode.MENU_PATHWAY_TEMPLATES,
                PermissionCode.MENU_QC_DASHBOARD,
                PermissionCode.MENU_ADMIN_AUDIT,
                PermissionCode.MENU_SECURITY_BASELINE,
                PermissionCode.MENU_SYSTEM_PROVIDERS,
                PermissionCode.MENU_NOTIFICATION_SETTINGS,
                PermissionCode.MENU_PROVENANCE,
                PermissionCode.MENU_DOMESTIC_CHECK);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.ORGANIZATION_ADMIN))
            .contains(PermissionCode.TENANT_OVERRIDE)
            .doesNotContain(
                PermissionCode.ENV_EMERGENCY,
                PermissionCode.PLATFORM_PUBLISH,
                PermissionCode.SYSTEM_MANAGE);
    }

    private String invokeDimension(Method dimensionMethod, PermissionCode permission) {
        try {
            return String.valueOf(dimensionMethod.invoke(permission));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取权限维度: " + permission.code(), exception);
        }
    }
}
