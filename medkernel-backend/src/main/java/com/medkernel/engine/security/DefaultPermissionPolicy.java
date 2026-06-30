package com.medkernel.engine.security;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.medkernel.engine.security.PermissionCode.*;

/**
 * 职责角色默认权限策略。
 *
 * <p>机构层级由角色授权的组织范围表达，人员专业岗位由任职表达；本策略只定义完成某类
 * 系统责任所需的固定最小权限。组织与数据访问范围由账号分配单独约束。
 */
public final class DefaultPermissionPolicy {

    private static final Map<RoleCode, Set<PermissionCode>> POLICY;

    static {
        EnumMap<RoleCode, Set<PermissionCode>> map = new EnumMap<>(RoleCode.class);
        map.put(RoleCode.SYSTEM_SUPERADMIN, allRuntimePermissions());
        map.put(RoleCode.PLATFORM_ADMIN, platformAdministrationPermissions());
        map.put(RoleCode.ENGINE_OPERATOR, engineOperationsPermissions());
        map.put(RoleCode.CLINICAL_USER, clinicalUserPermissions());
        map.put(RoleCode.AUDITOR, auditPermissions());
        POLICY = Map.copyOf(map);
    }

    private DefaultPermissionPolicy() {
    }

    private static EnumSet<PermissionCode> platformAdministrationPermissions() {
        return withMenus(EnumSet.of(
            DATA_GROUP, DATA_DESENSITIZED,
            ASSET_RUNTIME_RELEASE, ASSET_DICTIONARY,
            ENV_TEST, ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ, ORG_WRITE, ORG_PUBLISH,
            TENANT_READ, TENANT_WRITE,
            RELEASE_READ,
            CONTEXT_READ, CONTEXT_WRITE,
            EVENT_READ, EVENT_WRITE,
            SYSTEM_READ, SYSTEM_MANAGE,
            AUDIT_READ, AUDIT_EXPORT,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            INTEGRATION_READ, INTEGRATION_WRITE, INTEGRATION_EXECUTE,
            MPI_READ, MPI_CREATE, MPI_WRITE,
            PROJECTION_READ, PROJECTION_REBUILD,
            WORKBENCH_READINESS_VIEW,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_TENANT_ONBOARDING,
            MENU_ADMIN_USERS,
            MENU_IDENTITY_BINDINGS,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_IMPLEMENTATION_GUIDE,
            MENU_ADAPTER_HUB,
            MENU_SYSTEM_PROVIDERS,
            MENU_DOMESTIC_CHECK,
            MENU_RUNTIME_DIAGNOSTICS,
            MENU_NOTIFICATIONS,
            MENU_NOTIFICATION_SETTINGS);
    }

    private static EnumSet<PermissionCode> engineOperationsPermissions() {
        return withMenus(EnumSet.of(
            DATA_GROUP, DATA_HOSPITAL, DATA_DESENSITIZED,
            ASSET_RUNTIME_RELEASE, ASSET_DICTIONARY, ASSET_KNOWLEDGE,
            ASSET_RULE, ASSET_PATHWAY,
            ENV_TEST, ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            RELEASE_READ, RELEASE_PUBLISH, RELEASE_ROLLBACK,
            ASSET_READ, ASSET_WRITE,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW, KNOWLEDGE_PUBLISH,
            KNOWLEDGE_WITHDRAW, KNOWLEDGE_EXPORT,
            TERM_READ, TERM_WRITE, TERM_PUBLISH,
            RULE_READ, RULE_WRITE, RULE_PUBLISH,
            PATHWAY_READ, PATHWAY_WRITE, PATHWAY_PUBLISH,
            PLATFORM_PUBLISH, TENANT_OVERRIDE,
            CONTEXT_READ, CONTEXT_WRITE, EVENT_READ,
            RECOMMENDATION_READ,
            EVALUATION_READ, EVALUATION_WRITE, EVALUATION_PUBLISH,
            EVALUATION_EXECUTE, EVALUATION_REMEDIATE, EVALUATION_REVIEW,
            AUDIT_READ, AUDIT_EXPORT,
            FOLLOWUP_READ, WORKFLOW_READ,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            SANDBOX_RUN, SANDBOX_MANAGE,
            LLM_READ, LLM_EXECUTE, LLM_MANAGE,
            LLM_EGRESS_MANAGE, LLM_PROVIDER_MANAGE, LLM_EVAL_MANAGE,
            LLM_ENHANCEMENT_MANAGE,
            ENGINE_DATA_READ, ENGINE_DATA_EXPORT,
            PROJECTION_READ, PROJECTION_REBUILD,
            MPI_READ,
            FOLLOWUP_WRITE, FOLLOWUP_PUBLISH,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_INSTITUTION_KNOWLEDGE,
            MENU_DIAGNOSIS_KNOWLEDGE,
            MENU_RUNTIME_RELEASES,
            MENU_TERMINOLOGY_MAPPING,
            MENU_RULE_DEFINITIONS,
            MENU_PATHWAY_TEMPLATES,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE,
            MENU_KNOWLEDGE_PRODUCTION,
            MENU_AI_WORKFLOWS,
            MENU_CLINICAL_FOLLOWUP,
            MENU_SANDBOX,
            MENU_QC_DASHBOARD,
            MENU_QC_ALERTS,
            MENU_INSURANCE_AUDIT,
            MENU_QC_EVAL_SETS,
            MENU_ADMIN_AUDIT,
            MENU_NOTIFICATIONS,
            MENU_NOTIFICATION_SETTINGS);
    }

    private static EnumSet<PermissionCode> clinicalUserPermissions() {
        return withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            KNOWLEDGE_READ,
            RULE_READ, RULE_OVERRIDE,
            PATHWAY_READ, PATHWAY_EXECUTE,
            CONTEXT_READ, CONTEXT_WRITE, EVENT_READ,
            RECOMMENDATION_READ, RECOMMENDATION_WRITE, RECOMMENDATION_ACCEPT,
            MPI_READ, MPI_CREATE,
            FOLLOWUP_READ, FOLLOWUP_WRITE,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            SANDBOX_RUN,
            LLM_READ, LLM_EXECUTE),
            MENU_WORKBENCH,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_CDSS_FATIGUE,
            MENU_WORKFLOW_TODOS,
            MENU_CLINICAL_FOLLOWUP,
            MENU_SANDBOX,
            MENU_NOTIFICATIONS,
            MENU_NOTIFICATION_SETTINGS);
    }

    private static EnumSet<PermissionCode> auditPermissions() {
        return withMenus(EnumSet.of(
            DATA_GROUP, DATA_DESENSITIZED,
            ASSET_RUNTIME_RELEASE, ASSET_DICTIONARY, ASSET_KNOWLEDGE,
            ASSET_RULE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ, TENANT_READ, RELEASE_READ,
            ASSET_READ,
            KNOWLEDGE_READ, KNOWLEDGE_EXPORT,
            TERM_READ, RULE_READ, PATHWAY_READ,
            RECOMMENDATION_READ, EVALUATION_READ,
            AUDIT_READ, AUDIT_EXPORT,
            CONTEXT_READ, EVENT_READ, SYSTEM_READ,
            FOLLOWUP_READ, WORKFLOW_READ,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            LLM_READ,
            ENGINE_DATA_READ, ENGINE_DATA_EXPORT,
            INTEGRATION_READ, MPI_READ, PROJECTION_READ,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_PROVENANCE,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_NOTIFICATIONS,
            MENU_NOTIFICATION_SETTINGS);
    }

    private static EnumSet<PermissionCode> allRuntimePermissions() {
        return EnumSet.allOf(PermissionCode.class);
    }

    private static EnumSet<PermissionCode> withMenus(
            EnumSet<PermissionCode> permissions,
            PermissionCode... menuPermissions) {
        permissions.addAll(List.of(menuPermissions));
        return permissions;
    }

    public static Set<PermissionCode> permissionsOf(RoleCode role) {
        return POLICY.getOrDefault(role, EnumSet.noneOf(PermissionCode.class));
    }

    public static boolean has(RoleCode role, PermissionCode permission) {
        return permissionsOf(role).contains(permission);
    }
}
