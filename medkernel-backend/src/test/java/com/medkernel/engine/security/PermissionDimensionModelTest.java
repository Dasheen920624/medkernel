package com.medkernel.engine.security;

import java.lang.reflect.Method;
import java.util.Arrays;
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
    void permissionCatalogDoesNotExposeLegacyPackageContainerBoundaries() {
        String removedActionPrefix = "pack" + "age.";
        String removedEnumToken = "PACK" + "AGE";
        String removedConfigContainerLabel = "配置" + "包";
        String removedKnowledgeContainerLabel = "知识" + "包";

        assertThat(Arrays.stream(PermissionCode.values()).map(PermissionCode::code))
            .as("全新上线模型只保留运行发布与资产权限，不再暴露旧容器动作权限")
            .noneMatch(code -> code.startsWith(removedActionPrefix));

        assertThat(Arrays.stream(PermissionCode.values()).map(Enum::name))
            .as("权限枚举名不再保留旧容器别名")
            .noneMatch(name -> name.contains(removedEnumToken));

        assertThat(Arrays.stream(PermissionCode.values()).map(PermissionCode::displayName))
            .as("资产边界不再用旧容器标签表达")
            .noneMatch(name -> name.contains(removedConfigContainerLabel)
                || name.contains(removedKnowledgeContainerLabel));
    }

    @Test
    void allFourLaunchRolesReceiveApplicableBaselinePermissions() throws Exception {
        Method dimensionMethod = PermissionCode.class.getMethod("dimension");

        var customerRoles = Arrays.stream(RoleCode.values()).filter(RoleCode::customerAssignable).toList();
        assertThat(customerRoles).hasSize(4);
        for (RoleCode role : customerRoles) {
            Set<String> dimensions = DefaultPermissionPolicy.permissionsOf(role).stream()
                .map(permission -> invokeDimension(dimensionMethod, permission))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

            assertThat(dimensions)
                .as("%s 必须拥有菜单、动作、数据和环境基线权限", role.code())
                .containsAll(Set.of("MENU", "ACTION", "DATA", "ENVIRONMENT"));
        }

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_ADMIN))
            .contains(PermissionCode.ASSET_RUNTIME_RELEASE, PermissionCode.ASSET_DICTIONARY);
    }

    @Test
    void roleCatalogContainsOnlyBuiltInSuperAdminAndFourAssignableResponsibilities() {
        assertThat(RoleCode.fromCode("system-superadmin"))
            .as("内置超级管理员必须是系统内置角色，而不是手工配置出来的普通角色")
            .isPresent();

        assertThat(RoleCode.values())
            .containsExactly(
                RoleCode.SYSTEM_SUPERADMIN,
                RoleCode.PLATFORM_ADMIN,
                RoleCode.ENGINE_OPERATOR,
                RoleCode.CLINICAL_USER,
                RoleCode.AUDITOR);
    }

    @Test
    void fourResponsibilitiesKeepClearPermissionBoundaries() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_ADMIN))
            .contains(PermissionCode.TENANT_WRITE, PermissionCode.SYSTEM_MANAGE, PermissionCode.INTEGRATION_EXECUTE)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.EVALUATION_PUBLISH,
                PermissionCode.RECOMMENDATION_ACCEPT,
                PermissionCode.ENV_EMERGENCY);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.ENGINE_OPERATOR))
            .contains(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.PATHWAY_PUBLISH,
                PermissionCode.EVALUATION_PUBLISH,
                PermissionCode.FOLLOWUP_PUBLISH,
                PermissionCode.RELEASE_PUBLISH)
            .doesNotContain(
                PermissionCode.TENANT_WRITE,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.RECOMMENDATION_ACCEPT,
                PermissionCode.ENV_EMERGENCY);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.CLINICAL_USER))
            .contains(PermissionCode.RECOMMENDATION_ACCEPT, PermissionCode.PATHWAY_EXECUTE)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.ENV_EMERGENCY);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.AUDITOR))
            .contains(PermissionCode.AUDIT_READ, PermissionCode.AUDIT_EXPORT)
            .doesNotContain(
                PermissionCode.ORG_WRITE,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.RECOMMENDATION_ACCEPT,
                PermissionCode.ENV_EMERGENCY,
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
