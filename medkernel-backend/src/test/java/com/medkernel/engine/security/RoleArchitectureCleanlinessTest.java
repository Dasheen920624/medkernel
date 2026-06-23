package com.medkernel.engine.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleArchitectureCleanlinessTest {

    private static final List<String> RETIRED_ROLE_LABELS = List.of(
        "平台治理管理员", "平台知识治理员", "机构知识治理员", "机构管理员",
        "人员与访问管理员", "临床治理负责人", "临床决策使用者", "护理协同人员",
        "药事安全人员", "质量治理专家", "质量治理员", "集成运维员", "实施运维员",
        "审计人员", "安全管理员", "各级管理员", "专科专家", "院级管理员", "医院管理员"
    );

    private static final Set<String> LAUNCH_ROLES = Set.of(
        "platform-admin",
        "engine-operator",
        "clinical-user",
        "auditor"
    );

    private static final Set<String> RETIRED_ROLE_CODES = Set.of(
        "platform-governance-admin",
        "platform-knowledge-governor",
        "organization-admin",
        "identity-access-admin",
        "knowledge-governor",
        "clinical-governor",
        "clinical-decision-user",
        "nursing-collaborator",
        "medication-safety-user",
        "diagnostic-service-user",
        "quality-governor",
        "compliance-auditor",
        "integration-operator",
        "implementation-operator"
    );

    @Test
    void roleCatalogExposesFourCompleteProductResponsibilitiesAndInternalSuperadmin() {
        Set<String> actualCustomerRoles = Stream.of(RoleCode.values())
            .filter(RoleCode::customerAssignable)
            .map(RoleCode::code)
            .collect(Collectors.toSet());

        assertThat(actualCustomerRoles).containsExactlyInAnyOrderElementsOf(LAUNCH_ROLES);
        assertThat(RoleCode.fromCode("system-superadmin")).contains(RoleCode.SYSTEM_SUPERADMIN);
    }

    @Test
    void retiredRoleCodesAreRejectedInsteadOfKeptAsCompatibilityAliases() {
        assertThat(RETIRED_ROLE_CODES)
            .allSatisfy(code -> assertThat(RoleCode.fromCode(code)).as(code).isEmpty());
    }

    @Test
    void dynamicRoleAndPermissionCatalogEntitiesAreRemoved() {
        assertThatThrownBy(() -> Class.forName("com.medkernel.engine.security.SystemRole"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.medkernel.engine.security.SystemPermission"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void productionSourcesDoNotExposeRetiredRoleLabels() throws IOException {
        List<Path> roots = List.of(
            Path.of("src/main/java"),
            Path.of("../frontend/src")
        );

        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().contains(".test."))
                    .toList()) {
                    String content = Files.readString(file);
                    assertThat(RETIRED_ROLE_LABELS)
                        .as(file.toString())
                        .noneMatch(content::contains);
                }
            }
        }
    }
}
