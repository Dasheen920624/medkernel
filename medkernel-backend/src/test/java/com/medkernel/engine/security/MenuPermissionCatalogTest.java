package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.Set;

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
        "qc-dashboard",
        "qc-alerts",
        "insurance-audit",
        "qc-eval-sets",
        "knowledge-governance",
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
            .hasSize(30)
            .allSatisfy(menu -> {
                assertThat(menu.permission().dimension()).isEqualTo(PermissionDimension.MENU);
                assertThat(menu.permission().target()).isEqualTo(menu.menuKey());
                assertThat(PermissionCode.fromCode("menu." + menu.menuKey())).contains(menu.permission());
            });
    }

    @Test
    void catalogLocksPrimaryHeaderProfileAndExpertPlacements() {
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY)
            .hasSize(23);
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.HEADER)
            .extracting(MenuPermissionCatalog.MenuPermission::menuKey)
            .containsExactly("notifications");
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.PROFILE)
            .extracting(MenuPermissionCatalog.MenuPermission::menuKey)
            .containsExactly("notification-settings");
        assertThat(MenuPermissionCatalog.allMenus())
            .filteredOn(menu -> menu.placement() == MenuPermissionCatalog.MenuPlacement.EXPERT)
            .hasSize(5);
    }

    @Test
    void menuKeysForReturnsOnlyCatalogNavigationPermissions() {
        assertThat(MenuPermissionCatalog.menuKeysFor(EnumSet.of(
            PermissionCode.MENU_WORKBENCH,
            PermissionCode.MENU_NOTIFICATIONS,
            PermissionCode.MENU_PROVENANCE)))
            .containsExactly("workbench", "notifications", "provenance");
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
