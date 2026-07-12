package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuPermissionCatalogTest {

    @Test
    void generatedCatalogKeysAndRoutesAreUnique() {
        assertThat(MenuPermissionCatalog.allMenus()).isNotEmpty();
        assertThat(MenuPermissionCatalog.allMenuKeys()).doesNotHaveDuplicates();
        assertThat(MenuPermissionCatalog.allMenus())
            .extracting(MenuPermissionCatalog.MenuPermission::route)
            .doesNotHaveDuplicates();
    }

    @Test
    void customerVisibleMenuLabelsUseMedicalTaskNames() {
        assertThat(MenuPermissionCatalog.allMenus())
            .extracting(
                MenuPermissionCatalog.MenuPermission::menuKey,
                MenuPermissionCatalog.MenuPermission::displayName)
            .contains(
                org.assertj.core.groups.Tuple.tuple("knowledge-governance", "知识审核发布中心"),
                org.assertj.core.groups.Tuple.tuple("institution-knowledge", "机构知识库"),
                org.assertj.core.groups.Tuple.tuple("diagnosis-knowledge", "诊断知识库"),
                org.assertj.core.groups.Tuple.tuple("domain-facade-b0-evidence", "领域门面无模型证据"),
                org.assertj.core.groups.Tuple.tuple("runtime-releases", "机构生效版本"),
                org.assertj.core.groups.Tuple.tuple("terminology-mapping", "术语字典"),
                org.assertj.core.groups.Tuple.tuple("rule-definitions", "临床规则"),
                org.assertj.core.groups.Tuple.tuple("pathway-templates", "临床路径库"),
                org.assertj.core.groups.Tuple.tuple("knowledge-production", "知识生产工作台"),
                org.assertj.core.groups.Tuple.tuple("ai-workflows", "模型能力与安全"),
                org.assertj.core.groups.Tuple.tuple("qc-dashboard", "质量风险概览"),
                org.assertj.core.groups.Tuple.tuple("system-providers", "服务运行保障"),
                org.assertj.core.groups.Tuple.tuple("domestic-check", "国产化适配自检"),
                org.assertj.core.groups.Tuple.tuple("runtime-diagnostics", "运行诊断"));
    }

    @Test
    void everyCatalogMenuHasRegisteredMenuPermissionCode() {
        assertThat(MenuPermissionCatalog.allMenus())
            .allSatisfy(menu -> {
                assertThat(menu.permission().dimension()).isEqualTo(PermissionDimension.MENU);
                assertThat(menu.permission().target()).isEqualTo(menu.menuKey());
                assertThat(PermissionCode.fromCode("menu." + menu.menuKey())).contains(menu.permission());
            });
    }

    @Test
    void catalogEntriesExposeGeneratedRouteAndResponsibilityRoles() {
        assertThat(MenuPermissionCatalog.allMenus())
            .allSatisfy(menu -> {
                assertThat(menu.route()).startsWith("/");
                assertThat(menu.responsibilityRoles())
                    .isNotEmpty()
                    .allMatch(RoleCode::customerAssignable);
            });
    }

    @Test
    void catalogLocksPrimaryHeaderAndProfilePlacements() {
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY)
            .isNotEmpty();
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.HEADER)
            .extracting(MenuPermissionCatalog.MenuPermission::menuKey)
            .containsExactly("notifications");
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PROFILE)
            .extracting(MenuPermissionCatalog.MenuPermission::menuKey)
            .containsExactly("notification-settings");
    }

    @Test
    void catalogLocksEightDomainOwnership() {
        Set<String> primarySections = MenuPermissionCatalog.allMenus().stream()
            .filter(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY)
            .map(MenuPermissionCatalog.MenuPermission::sectionKey)
            .collect(Collectors.toSet());

        assertThat(primarySections).containsExactlyInAnyOrder(
            "workbench",
            "organization-people",
            "knowledge-governance",
            "knowledge-production",
            "clinical-collaboration",
            "quality-management",
            "compliance-security",
            "system-operations");
    }

    @Test
    void menuKeysForReturnsOnlyCatalogNavigationPermissions() {
        assertThat(MenuPermissionCatalog.menuKeysFor(EnumSet.of(
            PermissionCode.MENU_WORKBENCH,
            PermissionCode.MENU_NOTIFICATIONS,
            PermissionCode.MENU_PROVENANCE)))
            .containsExactly("workbench", "provenance", "notifications");
    }

    @Test
    void legacySectionPermissionCodesDoNotExist() {
        assertThat(Set.of(
            "menu.pilot-setup",
            "menu.clinical-run",
            "menu.quality-improve",
            "menu.compliance-ops",
            "menu.advanced-tools",
            "menu.rule-validate",
            "menu.qc-eval-results"
        )).allSatisfy(code -> assertThat(PermissionCode.fromCode(code)).isEmpty());
    }
}
