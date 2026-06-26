package com.medkernel.engine.security;

import java.util.List;
import java.util.Set;

import static com.medkernel.engine.security.PermissionCode.MENU_ADMIN_AUDIT;
import static com.medkernel.engine.security.PermissionCode.MENU_ADMIN_USERS;
import static com.medkernel.engine.security.PermissionCode.MENU_AI_WORKFLOWS;
import static com.medkernel.engine.security.PermissionCode.MENU_KNOWLEDGE_GOVERNANCE;
import static com.medkernel.engine.security.PermissionCode.MENU_ADAPTER_HUB;
import static com.medkernel.engine.security.PermissionCode.MENU_CDSS_FATIGUE;
import static com.medkernel.engine.security.PermissionCode.MENU_CLINICAL_FOLLOWUP;
import static com.medkernel.engine.security.PermissionCode.MENU_RUNTIME_RELEASES;
import static com.medkernel.engine.security.PermissionCode.MENU_DEV_CONSOLE;
import static com.medkernel.engine.security.PermissionCode.MENU_DIAGNOSIS_KNOWLEDGE;
import static com.medkernel.engine.security.PermissionCode.MENU_DOMESTIC_CHECK;
import static com.medkernel.engine.security.PermissionCode.MENU_GRAPH_EXPLORE;
import static com.medkernel.engine.security.PermissionCode.MENU_IDENTITY_BINDINGS;
import static com.medkernel.engine.security.PermissionCode.MENU_IMPLEMENTATION_GUIDE;
import static com.medkernel.engine.security.PermissionCode.MENU_INSTITUTION_KNOWLEDGE;
import static com.medkernel.engine.security.PermissionCode.MENU_INSURANCE_AUDIT;
import static com.medkernel.engine.security.PermissionCode.MENU_KNOWLEDGE_PRODUCTION;
import static com.medkernel.engine.security.PermissionCode.MENU_MPI;
import static com.medkernel.engine.security.PermissionCode.MENU_NOTIFICATIONS;
import static com.medkernel.engine.security.PermissionCode.MENU_NOTIFICATION_SETTINGS;
import static com.medkernel.engine.security.PermissionCode.MENU_PATIENT_PATHWAYS;
import static com.medkernel.engine.security.PermissionCode.MENU_PATHWAY_TEMPLATES;
import static com.medkernel.engine.security.PermissionCode.MENU_PROVENANCE;
import static com.medkernel.engine.security.PermissionCode.MENU_QC_ALERTS;
import static com.medkernel.engine.security.PermissionCode.MENU_QC_DASHBOARD;
import static com.medkernel.engine.security.PermissionCode.MENU_QC_EVAL_SETS;
import static com.medkernel.engine.security.PermissionCode.MENU_RULE_DEFINITIONS;
import static com.medkernel.engine.security.PermissionCode.MENU_SANDBOX;
import static com.medkernel.engine.security.PermissionCode.MENU_SECURITY_BASELINE;
import static com.medkernel.engine.security.PermissionCode.MENU_SYSTEM_PROVIDERS;
import static com.medkernel.engine.security.PermissionCode.MENU_TENANT_ONBOARDING;
import static com.medkernel.engine.security.PermissionCode.MENU_TERMINOLOGY_MAPPING;
import static com.medkernel.engine.security.PermissionCode.MENU_WORKBENCH;
import static com.medkernel.engine.security.PermissionCode.MENU_WORKFLOW_TODOS;

/**
 * 后端菜单权限目录（Menu Permission Catalog）。
 *
 * <p>入口权限只承认 32 个按业务域分类的主导航、1 个页头和 1 个个人入口。
 */
public final class MenuPermissionCatalog {

    private static final List<MenuPermission> ALL_MENUS = List.of(
        menu("workbench", "workbench", "工作台", MENU_WORKBENCH, MenuPlacement.PRIMARY),
        menu("organization-people", "tenant-onboarding", "服务机构",
            MENU_TENANT_ONBOARDING, MenuPlacement.PRIMARY),
        menu("organization-people", "admin-users", "人员与账号",
            MENU_ADMIN_USERS, MenuPlacement.PRIMARY),
        menu("organization-people", "identity-bindings", "身份来源",
            MENU_IDENTITY_BINDINGS, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "knowledge-governance", "知识审核与发布",
            MENU_KNOWLEDGE_GOVERNANCE, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "institution-knowledge", "机构知识",
            MENU_INSTITUTION_KNOWLEDGE, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "diagnosis-knowledge", "诊断知识维护",
            MENU_DIAGNOSIS_KNOWLEDGE, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "runtime-releases", "发布治理",
            MENU_RUNTIME_RELEASES, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "terminology-mapping", "术语与字典",
            MENU_TERMINOLOGY_MAPPING, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "rule-definitions", "规则配置",
            MENU_RULE_DEFINITIONS, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "pathway-templates", "路径配置",
            MENU_PATHWAY_TEMPLATES, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "provenance", "来源与血缘",
            MENU_PROVENANCE, MenuPlacement.PRIMARY),
        menu("knowledge-governance", "graph-explore", "知识关系",
            MENU_GRAPH_EXPLORE, MenuPlacement.PRIMARY),
        menu("knowledge-production", "knowledge-production", "知识生产",
            MENU_KNOWLEDGE_PRODUCTION, MenuPlacement.PRIMARY),
        menu("knowledge-production", "ai-workflows", "模型能力",
            MENU_AI_WORKFLOWS, MenuPlacement.PRIMARY),
        menu("clinical-collaboration", "mpi", "患者索引", MENU_MPI, MenuPlacement.PRIMARY),
        menu("clinical-collaboration", "patient-pathways", "患者路径",
            MENU_PATIENT_PATHWAYS, MenuPlacement.PRIMARY),
        menu("clinical-collaboration", "cdss-fatigue", "提醒与推荐",
            MENU_CDSS_FATIGUE, MenuPlacement.PRIMARY),
        menu("clinical-collaboration", "workflow-todos", "协同任务",
            MENU_WORKFLOW_TODOS, MenuPlacement.PRIMARY),
        menu("clinical-collaboration", "clinical-followup", "随访协同",
            MENU_CLINICAL_FOLLOWUP, MenuPlacement.PRIMARY),
        menu("clinical-collaboration", "sandbox", "全真体验沙盘",
            MENU_SANDBOX, MenuPlacement.PRIMARY),
        menu("quality-management", "qc-dashboard", "质量管理概览",
            MENU_QC_DASHBOARD, MenuPlacement.PRIMARY),
        menu("quality-management", "qc-alerts", "质量问题与整改",
            MENU_QC_ALERTS, MenuPlacement.PRIMARY),
        menu("quality-management", "insurance-audit", "医保审核",
            MENU_INSURANCE_AUDIT, MenuPlacement.PRIMARY),
        menu("quality-management", "qc-eval-sets", "评价指标",
            MENU_QC_EVAL_SETS, MenuPlacement.PRIMARY),
        menu("compliance-security", "admin-audit", "审计与证据",
            MENU_ADMIN_AUDIT, MenuPlacement.PRIMARY),
        menu("compliance-security", "security-baseline", "安全与配置",
            MENU_SECURITY_BASELINE, MenuPlacement.PRIMARY),
        menu("system-operations", "implementation-guide", "实施与验收",
            MENU_IMPLEMENTATION_GUIDE, MenuPlacement.PRIMARY),
        menu("system-operations", "adapter-hub", "系统接入",
            MENU_ADAPTER_HUB, MenuPlacement.PRIMARY),
        menu("system-operations", "system-providers", "运行保障",
            MENU_SYSTEM_PROVIDERS, MenuPlacement.PRIMARY),
        menu("system-operations", "domestic-check", "国产化核验",
            MENU_DOMESTIC_CHECK, MenuPlacement.PRIMARY),
        menu("system-operations", "dev-console", "诊断工具",
            MENU_DEV_CONSOLE, MenuPlacement.PRIMARY),
        menu("workbench", "notifications", "消息通知", MENU_NOTIFICATIONS, MenuPlacement.HEADER),
        menu("workbench", "notification-settings", "通知偏好",
            MENU_NOTIFICATION_SETTINGS, MenuPlacement.PROFILE));

    private MenuPermissionCatalog() {
    }

    public static List<MenuPermission> allMenus() {
        return ALL_MENUS;
    }

    public static List<String> allMenuKeys() {
        return ALL_MENUS.stream().map(MenuPermission::menuKey).toList();
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
            PermissionCode permission,
            MenuPlacement placement) {
        return new MenuPermission(sectionKey, menuKey, displayName, permission, placement);
    }

    public enum MenuPlacement {
        PRIMARY,
        HEADER,
        PROFILE
    }

    public record MenuPermission(
        String sectionKey,
        String menuKey,
        String displayName,
        PermissionCode permission,
        MenuPlacement placement
    ) {}
}
