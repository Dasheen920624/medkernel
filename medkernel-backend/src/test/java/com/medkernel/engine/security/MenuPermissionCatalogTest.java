package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuPermissionCatalogTest {

    private static final Set<String> EXPECTED_MENU_KEYS = Set.of(
        "workbench",
        "implementation-guide",
        "tenant-onboarding",
        "runtime-releases",
        "pathway-templates",
        "rule-definitions",
        "terminology-mapping",
        "adapter-hub",
        "mpi",
        "patient-pathways",
        "cdss-fatigue",
        "workflow-todos",
        "notifications",
        "clinical-followup",
        "sandbox",
        "qc-dashboard",
        "qc-alerts",
        "insurance-audit",
        "qc-eval-sets",
        "knowledge-governance",
        "institution-knowledge",
        "diagnosis-knowledge",
        "knowledge-production",
        "admin-users",
        "identity-bindings",
        "admin-audit",
        "security-baseline",
        "system-providers",
        "notification-settings",
        "provenance",
        "graph-explore",
        "ai-workflows",
        "domestic-check",
        "runtime-diagnostics");

    @Test
    void catalogContainsExactlyLockedNavigationEntries() {
        assertThat(MenuPermissionCatalog.allMenuKeys())
            .containsExactlyInAnyOrderElementsOf(EXPECTED_MENU_KEYS);
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
                org.assertj.core.groups.Tuple.tuple("runtime-releases", "机构生效版本"),
                org.assertj.core.groups.Tuple.tuple("terminology-mapping", "术语字典"),
                org.assertj.core.groups.Tuple.tuple("rule-definitions", "临床规则"),
                org.assertj.core.groups.Tuple.tuple("pathway-templates", "临床路径库"),
                org.assertj.core.groups.Tuple.tuple("knowledge-production", "知识生产工作台"),
                org.assertj.core.groups.Tuple.tuple("ai-workflows", "模型能力"),
                org.assertj.core.groups.Tuple.tuple("domestic-check", "国产化适配自检"),
                org.assertj.core.groups.Tuple.tuple("runtime-diagnostics", "运行诊断"));
    }

    @Test
    void everyCatalogMenuHasRegisteredMenuPermissionCode() {
        assertThat(MenuPermissionCatalog.allMenus())
            .hasSize(34)
            .allSatisfy(menu -> {
                assertThat(menu.permission().dimension()).isEqualTo(PermissionDimension.MENU);
                assertThat(menu.permission().target()).isEqualTo(menu.menuKey());
                assertThat(PermissionCode.fromCode("menu." + menu.menuKey())).contains(menu.permission());
            });
    }

    @Test
    void catalogLocksPrimaryHeaderAndProfilePlacements() {
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY)
            .hasSize(32);
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
        Map<String, Set<String>> menusBySection = MenuPermissionCatalog.allMenus().stream()
            .filter(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY)
            .collect(Collectors.groupingBy(
                MenuPermissionCatalog.MenuPermission::sectionKey,
                Collectors.mapping(MenuPermissionCatalog.MenuPermission::menuKey, Collectors.toSet())));

        assertThat(menusBySection).containsExactlyInAnyOrderEntriesOf(Map.of(
            "workbench", Set.of("workbench"),
            "organization-people", Set.of("tenant-onboarding", "admin-users", "identity-bindings"),
            "knowledge-governance", Set.of(
                "knowledge-governance", "institution-knowledge", "diagnosis-knowledge",
                "runtime-releases", "terminology-mapping", "rule-definitions", "pathway-templates",
                "provenance", "graph-explore"),
            "knowledge-production", Set.of("knowledge-production", "ai-workflows"),
            "clinical-collaboration", Set.of(
                "mpi", "patient-pathways", "cdss-fatigue", "workflow-todos", "clinical-followup",
                "sandbox"),
            "quality-management", Set.of(
                "qc-dashboard", "qc-alerts", "insurance-audit", "qc-eval-sets"),
            "compliance-security", Set.of("admin-audit", "security-baseline"),
            "system-operations", Set.of(
                "implementation-guide", "adapter-hub", "system-providers", "domestic-check",
                "runtime-diagnostics")
        ));
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
