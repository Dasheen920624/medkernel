package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.medkernel.engine.security.PermissionCode.MENU_ADMIN_AUDIT;
import static com.medkernel.engine.security.PermissionCode.MENU_ADMIN_USERS;
import static com.medkernel.engine.security.PermissionCode.MENU_ADVANCED_TOOLS;
import static com.medkernel.engine.security.PermissionCode.MENU_AI_WORKFLOWS;
import static com.medkernel.engine.security.PermissionCode.MENU_AIK_REVIEW;
import static com.medkernel.engine.security.PermissionCode.MENU_ADAPTER_HUB;
import static com.medkernel.engine.security.PermissionCode.MENU_CDSS_FATIGUE;
import static com.medkernel.engine.security.PermissionCode.MENU_CLINICAL_FOLLOWUP;
import static com.medkernel.engine.security.PermissionCode.MENU_CLINICAL_RUN;
import static com.medkernel.engine.security.PermissionCode.MENU_COMPLIANCE_OPS;
import static com.medkernel.engine.security.PermissionCode.MENU_CONFIG_PACKAGES;
import static com.medkernel.engine.security.PermissionCode.MENU_DEV_CONSOLE;
import static com.medkernel.engine.security.PermissionCode.MENU_DOMESTIC_CHECK;
import static com.medkernel.engine.security.PermissionCode.MENU_GRAPH_EXPLORE;
import static com.medkernel.engine.security.PermissionCode.MENU_IDENTITY_BINDINGS;
import static com.medkernel.engine.security.PermissionCode.MENU_IMPLEMENTATION_GUIDE;
import static com.medkernel.engine.security.PermissionCode.MENU_INSURANCE_AUDIT;
import static com.medkernel.engine.security.PermissionCode.MENU_MPI;
import static com.medkernel.engine.security.PermissionCode.MENU_NOTIFICATIONS;
import static com.medkernel.engine.security.PermissionCode.MENU_NOTIFICATION_SETTINGS;
import static com.medkernel.engine.security.PermissionCode.MENU_PATIENT_PATHWAYS;
import static com.medkernel.engine.security.PermissionCode.MENU_PATHWAY_TEMPLATES;
import static com.medkernel.engine.security.PermissionCode.MENU_PILOT_SETUP;
import static com.medkernel.engine.security.PermissionCode.MENU_PROVENANCE;
import static com.medkernel.engine.security.PermissionCode.MENU_QC_ALERTS;
import static com.medkernel.engine.security.PermissionCode.MENU_QC_DASHBOARD;
import static com.medkernel.engine.security.PermissionCode.MENU_QC_EVAL_RESULTS;
import static com.medkernel.engine.security.PermissionCode.MENU_QC_EVAL_SETS;
import static com.medkernel.engine.security.PermissionCode.MENU_QUALITY_IMPROVE;
import static com.medkernel.engine.security.PermissionCode.MENU_RULE_DEFINITIONS;
import static com.medkernel.engine.security.PermissionCode.MENU_RULE_VALIDATE;
import static com.medkernel.engine.security.PermissionCode.MENU_SECURITY_BASELINE;
import static com.medkernel.engine.security.PermissionCode.MENU_SYSTEM_PROVIDERS;
import static com.medkernel.engine.security.PermissionCode.MENU_TENANT_ONBOARDING;
import static com.medkernel.engine.security.PermissionCode.MENU_TERMINOLOGY_MAPPING;
import static com.medkernel.engine.security.PermissionCode.MENU_WORKBENCH;
import static com.medkernel.engine.security.PermissionCode.MENU_WORKFLOW_TODOS;

/**
 * 后端菜单权限目录（Menu Permission Catalog）。
 *
 * <p>INFRA-05 后，菜单维权限只承认 27 个二级菜单 + 5 个高级工具的细粒度 key。
 * 旧一级 section 权限仅保留枚举兼容，不能再用于可见菜单或路由授权。
 */
public final class MenuPermissionCatalog {

    private static final Set<PermissionCode> LEGACY_SECTION_PERMISSIONS = EnumSet.of(
        MENU_PILOT_SETUP,
        MENU_CLINICAL_RUN,
        MENU_QUALITY_IMPROVE,
        MENU_COMPLIANCE_OPS,
        MENU_ADVANCED_TOOLS);

    private static final List<MenuPermission> ALL_MENUS = List.of(
        menu("workbench", "workbench", "工作台", MENU_WORKBENCH),
        menu("pilot-setup", "implementation-guide", "客户实施向导", MENU_IMPLEMENTATION_GUIDE),
        menu("pilot-setup", "tenant-onboarding", "租户开通", MENU_TENANT_ONBOARDING),
        menu("pilot-setup", "config-packages", "配置包中心", MENU_CONFIG_PACKAGES),
        menu("pilot-setup", "pathway-templates", "路径配置", MENU_PATHWAY_TEMPLATES),
        menu("pilot-setup", "rule-definitions", "规则库", MENU_RULE_DEFINITIONS),
        menu("pilot-setup", "terminology-mapping", "字典映射", MENU_TERMINOLOGY_MAPPING),
        menu("pilot-setup", "adapter-hub", "适配器中心", MENU_ADAPTER_HUB),
        menu("clinical-run", "mpi", "患者主索引", MENU_MPI),
        menu("clinical-run", "patient-pathways", "患者路径", MENU_PATIENT_PATHWAYS),
        menu("clinical-run", "cdss-fatigue", "临床提醒治理", MENU_CDSS_FATIGUE),
        menu("clinical-run", "rule-validate", "规则校验", MENU_RULE_VALIDATE),
        menu("clinical-run", "workflow-todos", "待办中心", MENU_WORKFLOW_TODOS),
        menu("clinical-run", "notifications", "通知中心", MENU_NOTIFICATIONS),
        menu("clinical-run", "clinical-followup", "智能随访", MENU_CLINICAL_FOLLOWUP),
        menu("quality-improve", "qc-dashboard", "院级质控驾驶舱", MENU_QC_DASHBOARD),
        menu("quality-improve", "qc-alerts", "质控预警", MENU_QC_ALERTS),
        menu("quality-improve", "insurance-audit", "医保智能审核", MENU_INSURANCE_AUDIT),
        menu("quality-improve", "qc-eval-sets", "评估指标库", MENU_QC_EVAL_SETS),
        menu("quality-improve", "qc-eval-results", "评估结果", MENU_QC_EVAL_RESULTS),
        menu("quality-improve", "aik-review", "AI 知识审核", MENU_AIK_REVIEW),
        menu("compliance-ops", "admin-users", "用户管理", MENU_ADMIN_USERS),
        menu("compliance-ops", "identity-bindings", "身份绑定", MENU_IDENTITY_BINDINGS),
        menu("compliance-ops", "admin-audit", "审计日志", MENU_ADMIN_AUDIT),
        menu("compliance-ops", "security-baseline", "安全基线与系统配置", MENU_SECURITY_BASELINE),
        menu("compliance-ops", "system-providers", "Provider 状态", MENU_SYSTEM_PROVIDERS),
        menu("compliance-ops", "notification-settings", "通知设置", MENU_NOTIFICATION_SETTINGS),
        menu("advanced-tools", "provenance", "来源追溯", MENU_PROVENANCE),
        menu("advanced-tools", "graph-explore", "图谱查询", MENU_GRAPH_EXPLORE),
        menu("advanced-tools", "ai-workflows", "AI 工作流", MENU_AI_WORKFLOWS),
        menu("advanced-tools", "domestic-check", "国产化自检", MENU_DOMESTIC_CHECK),
        menu("advanced-tools", "dev-console", "开发者控制台", MENU_DEV_CONSOLE));

    private MenuPermissionCatalog() {
    }

    public static List<MenuPermission> allMenus() {
        return ALL_MENUS;
    }

    public static List<String> allMenuKeys() {
        return ALL_MENUS.stream().map(MenuPermission::menuKey).toList();
    }

    public static Set<PermissionCode> legacySectionPermissions() {
        return EnumSet.copyOf(LEGACY_SECTION_PERMISSIONS);
    }

    public static boolean isCatalogMenuPermission(PermissionCode permission) {
        return permission != null && ALL_MENUS.stream().anyMatch(menu -> menu.permission() == permission);
    }

    public static PermissionCode permissionForMenuKey(String menuKey) {
        return ALL_MENUS.stream()
            .filter(menu -> menu.menuKey().equals(menuKey))
            .findFirst()
            .map(MenuPermission::permission)
            .orElseThrow(() -> new IllegalArgumentException("未登记菜单 key: " + menuKey));
    }

    static List<String> menuKeysFor(Set<PermissionCode> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return ALL_MENUS.stream()
            .filter(menu -> permissions.contains(menu.permission()))
            .map(MenuPermission::menuKey)
            .toList();
    }

    private static MenuPermission menu(
            String sectionKey,
            String menuKey,
            String displayName,
            PermissionCode permission) {
        return new MenuPermission(sectionKey, menuKey, displayName, permission);
    }

    public record MenuPermission(
        String sectionKey,
        String menuKey,
        String displayName,
        PermissionCode permission
    ) {}
}
