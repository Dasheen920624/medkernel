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
        "rule-validate",
        "workflow-todos",
        "notifications",
        "clinical-followup",
        "qc-dashboard",
        "qc-alerts",
        "insurance-audit",
        "qc-eval-sets",
        "qc-eval-results",
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
    void catalogContainsExactlyLockedSecondLevelAndAdvancedMenus() {
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
    void menuKeysForReturnsOnlySecondLevelKeysFromMenuPermissions() {
        assertThat(MenuPermissionCatalog.menuKeysFor(EnumSet.of(
            PermissionCode.MENU_WORKBENCH,
            PermissionCode.MENU_RULE_VALIDATE,
            PermissionCode.MENU_QC_EVAL_RESULTS)))
            .containsExactly("workbench", "rule-validate", "qc-eval-results");
    }

    @Test
    void legacySectionPermissionCodesDoNotExist() {
        assertThat(Set.of(
            "menu.pilot-setup",
            "menu.clinical-run",
            "menu.quality-improve",
            "menu.compliance-ops",
            "menu.advanced-tools"
        )).allSatisfy(code -> assertThat(PermissionCode.fromCode(code)).isEmpty());
    }
}
