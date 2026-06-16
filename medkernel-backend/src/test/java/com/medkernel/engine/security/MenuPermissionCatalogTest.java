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
        "config-packages",
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
        "diagnosis-knowledge",
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
        "dev-console");

    @Test
    void catalogContainsExactlyLockedNavigationEntries() {
        assertThat(MenuPermissionCatalog.allMenuKeys())
            .containsExactlyInAnyOrderElementsOf(EXPECTED_MENU_KEYS);
    }

    @Test
    void everyCatalogMenuHasRegisteredMenuPermissionCode() {
        assertThat(MenuPermissionCatalog.allMenus())
            .hasSize(32)
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
            .hasSize(30);
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
    void catalogLocksSevenDomainOwnership() {
        Map<String, Set<String>> menusBySection = MenuPermissionCatalog.allMenus().stream()
            .filter(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY)
            .collect(Collectors.groupingBy(
                MenuPermissionCatalog.MenuPermission::sectionKey,
                Collectors.mapping(MenuPermissionCatalog.MenuPermission::menuKey, Collectors.toSet())));

        assertThat(menusBySection).containsExactlyInAnyOrderEntriesOf(Map.of(
            "workbench", Set.of("workbench"),
            "organization-people", Set.of("tenant-onboarding", "admin-users", "identity-bindings"),
            "knowledge-governance", Set.of(
                "knowledge-governance", "diagnosis-knowledge", "config-packages", "terminology-mapping",
                "rule-definitions", "pathway-templates", "provenance", "graph-explore", "ai-workflows"),
            "clinical-collaboration", Set.of(
                "mpi", "patient-pathways", "cdss-fatigue", "workflow-todos", "clinical-followup",
                "sandbox"),
            "quality-management", Set.of(
                "qc-dashboard", "qc-alerts", "insurance-audit", "qc-eval-sets"),
            "compliance-security", Set.of("admin-audit", "security-baseline"),
            "system-operations", Set.of(
                "implementation-guide", "adapter-hub", "system-providers", "domestic-check", "dev-console")
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
