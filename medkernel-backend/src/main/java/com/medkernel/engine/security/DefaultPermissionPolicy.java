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
 * 系统责任所需的最小权限。租户可在默认策略上做受控收窄或扩展，但不能登记目录外权限。
 */
public final class DefaultPermissionPolicy {

    private static final Map<RoleCode, Set<PermissionCode>> POLICY;

    static {
        EnumMap<RoleCode, Set<PermissionCode>> map = new EnumMap<>(RoleCode.class);
        map.put(RoleCode.SYSTEM_SUPERADMIN, allRuntimePermissions());
        map.put(RoleCode.PLATFORM_GOVERNANCE_ADMIN, platformGovernancePermissions());
        map.put(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR, platformKnowledgePermissions());
        map.put(RoleCode.ORGANIZATION_ADMIN, organizationAdministrationPermissions());
        map.put(RoleCode.IDENTITY_ACCESS_ADMIN, identityAccessPermissions());
        map.put(RoleCode.KNOWLEDGE_GOVERNOR, institutionKnowledgePermissions());
        map.put(RoleCode.CLINICAL_GOVERNOR, clinicalGovernancePermissions());
        map.put(RoleCode.CLINICAL_DECISION_USER, clinicalDecisionPermissions());
        map.put(RoleCode.NURSING_COLLABORATOR, nursingCollaborationPermissions());
        map.put(RoleCode.MEDICATION_SAFETY_USER, medicationSafetyPermissions());
        map.put(RoleCode.DIAGNOSTIC_SERVICE_USER, diagnosticServicePermissions());
        map.put(RoleCode.QUALITY_GOVERNOR, qualityGovernancePermissions());
        map.put(RoleCode.COMPLIANCE_AUDITOR, complianceAuditPermissions());
        map.put(RoleCode.INTEGRATION_OPERATOR, integrationOperationsPermissions());
        map.put(RoleCode.IMPLEMENTATION_OPERATOR, implementationOperationsPermissions());
        POLICY = Map.copyOf(map);
    }

    private DefaultPermissionPolicy() {
    }

    private static EnumSet<PermissionCode> platformGovernancePermissions() {
        return withOnlyMenus(allNonEmergencyPermissions(),
            MENU_WORKBENCH,
            MENU_TENANT_ONBOARDING,
            MENU_ADMIN_USERS,
            MENU_IDENTITY_BINDINGS,
            MENU_IMPLEMENTATION_GUIDE,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_CONFIG_PACKAGES,
            MENU_QC_DASHBOARD,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_SYSTEM_PROVIDERS,
            MENU_NOTIFICATION_SETTINGS,
            MENU_PROVENANCE,
            MENU_AI_WORKFLOWS,
            MENU_DOMESTIC_CHECK);
    }

    private static EnumSet<PermissionCode> platformKnowledgePermissions() {
        return withMenus(EnumSet.of(
            DATA_GROUP,
            ASSET_CONFIG_PACKAGE, ASSET_DICTIONARY, ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_TEST, ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            PACKAGE_READ, PACKAGE_PUBLISH, PACKAGE_ROLLBACK,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW, KNOWLEDGE_PUBLISH,
            KNOWLEDGE_WITHDRAW, KNOWLEDGE_EXPORT,
            TERM_READ, TERM_WRITE, TERM_PUBLISH,
            RULE_READ, RULE_WRITE, RULE_PUBLISH,
            PATHWAY_READ, PATHWAY_WRITE, PATHWAY_PUBLISH,
            PLATFORM_PUBLISH, TENANT_OVERRIDE,
            CONTEXT_READ, EVENT_READ,
            AUDIT_READ, AUDIT_EXPORT,
            PROJECTION_READ, PROJECTION_REBUILD,
            LLM_READ, LLM_EXECUTE,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_CONFIG_PACKAGES,
            MENU_PATHWAY_TEMPLATES,
            MENU_RULE_DEFINITIONS,
            MENU_TERMINOLOGY_MAPPING,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_ADMIN_AUDIT,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE,
            MENU_AI_WORKFLOWS);
    }

    private static EnumSet<PermissionCode> organizationAdministrationPermissions() {
        EnumSet<PermissionCode> permissions = allNonEmergencyPermissions();
        permissions.remove(PLATFORM_PUBLISH);
        permissions.remove(SYSTEM_MANAGE);
        return withOnlyMenus(permissions,
            MENU_WORKBENCH,
            MENU_TENANT_ONBOARDING,
            MENU_ADMIN_USERS,
            MENU_IDENTITY_BINDINGS,
            MENU_IMPLEMENTATION_GUIDE,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_CONFIG_PACKAGES,
            MENU_QC_DASHBOARD,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_SYSTEM_PROVIDERS,
            MENU_NOTIFICATION_SETTINGS,
            MENU_PROVENANCE,
            MENU_DOMESTIC_CHECK);
    }

    private static EnumSet<PermissionCode> identityAccessPermissions() {
        return withMenus(EnumSet.of(
            DATA_DESENSITIZED,
            ENV_PRODUCTION,
            ORG_READ, ORG_WRITE,
            TENANT_READ,
            AUDIT_READ,
            SYSTEM_READ,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_ADMIN_USERS,
            MENU_IDENTITY_BINDINGS,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE);
    }

    private static EnumSet<PermissionCode> institutionKnowledgePermissions() {
        return withMenus(EnumSet.of(
            DATA_HOSPITAL,
            ASSET_CONFIG_PACKAGE, ASSET_DICTIONARY, ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_TEST, ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            PACKAGE_READ, PACKAGE_PUBLISH, PACKAGE_ROLLBACK,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW, KNOWLEDGE_PUBLISH,
            KNOWLEDGE_WITHDRAW, KNOWLEDGE_EXPORT,
            TERM_READ, TERM_WRITE, TERM_PUBLISH,
            RULE_READ, RULE_WRITE, RULE_PUBLISH,
            PATHWAY_READ, PATHWAY_WRITE, PATHWAY_PUBLISH,
            TENANT_OVERRIDE,
            CONTEXT_READ, EVENT_READ,
            RECOMMENDATION_READ,
            AUDIT_READ, AUDIT_EXPORT,
            PROJECTION_READ,
            LLM_READ, LLM_EXECUTE,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_CONFIG_PACKAGES,
            MENU_PATHWAY_TEMPLATES,
            MENU_RULE_DEFINITIONS,
            MENU_TERMINOLOGY_MAPPING,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_ADMIN_AUDIT,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE,
            MENU_AI_WORKFLOWS);
    }

    private static EnumSet<PermissionCode> clinicalGovernancePermissions() {
        return withMenus(EnumSet.of(
            DATA_HOSPITAL,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW,
            RULE_READ, RULE_WRITE, RULE_PUBLISH, RULE_OVERRIDE,
            PATHWAY_READ, PATHWAY_WRITE, PATHWAY_PUBLISH,
            TENANT_OVERRIDE,
            CONTEXT_READ, EVENT_READ,
            RECOMMENDATION_READ, RECOMMENDATION_WRITE,
            EVALUATION_READ, EVALUATION_REMEDIATE,
            MPI_READ, MPI_WRITE,
            FOLLOWUP_READ, FOLLOWUP_WRITE,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            AUDIT_READ,
            LLM_READ, LLM_EXECUTE),
            MENU_WORKBENCH,
            MENU_PATHWAY_TEMPLATES,
            MENU_RULE_DEFINITIONS,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_CDSS_FATIGUE,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP,
            MENU_QC_DASHBOARD,
            MENU_QC_ALERTS,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_PROVENANCE);
    }

    private static EnumSet<PermissionCode> clinicalDecisionPermissions() {
        return withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            RECOMMENDATION_READ, RECOMMENDATION_ACCEPT,
            PATHWAY_READ,
            RULE_READ, RULE_OVERRIDE,
            CONTEXT_READ, EVENT_READ,
            KNOWLEDGE_READ,
            MPI_READ,
            FOLLOWUP_READ, FOLLOWUP_WRITE,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_EXECUTE),
            MENU_WORKBENCH,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_CDSS_FATIGUE,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP);
    }

    private static EnumSet<PermissionCode> nursingCollaborationPermissions() {
        return withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            RECOMMENDATION_READ, RECOMMENDATION_ACCEPT,
            PATHWAY_READ,
            CONTEXT_READ, EVENT_READ,
            KNOWLEDGE_READ,
            MPI_READ,
            FOLLOWUP_READ, FOLLOWUP_WRITE,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_EXECUTE),
            MENU_WORKBENCH,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP);
    }

    private static EnumSet<PermissionCode> medicationSafetyPermissions() {
        return withMenus(EnumSet.of(
            DATA_HOSPITAL,
            ASSET_RULE, ASSET_KNOWLEDGE_PACKAGE,
            ENV_PRODUCTION,
            ORG_READ,
            RULE_READ, RULE_WRITE,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW,
            RECOMMENDATION_READ,
            PATHWAY_READ,
            CONTEXT_READ, EVENT_READ,
            MPI_READ,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE),
            MENU_WORKBENCH,
            MENU_RULE_DEFINITIONS,
            MENU_CDSS_FATIGUE,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_PROVENANCE,
            MENU_PATIENT_PATHWAYS,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS);
    }

    private static EnumSet<PermissionCode> diagnosticServicePermissions() {
        return withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_DICTIONARY,
            ENV_PRODUCTION,
            ORG_READ,
            TERM_READ, TERM_WRITE,
            CONTEXT_READ,
            EVENT_READ, EVENT_WRITE,
            KNOWLEDGE_READ,
            MPI_READ,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE),
            MENU_WORKBENCH,
            MENU_TERMINOLOGY_MAPPING,
            MENU_MPI,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS);
    }

    private static EnumSet<PermissionCode> qualityGovernancePermissions() {
        return withMenus(EnumSet.of(
            DATA_HOSPITAL, DATA_DESENSITIZED,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            EVALUATION_READ, EVALUATION_WRITE, EVALUATION_PUBLISH,
            EVALUATION_EXECUTE, EVALUATION_REMEDIATE, EVALUATION_REVIEW,
            TENANT_OVERRIDE,
            KNOWLEDGE_READ, KNOWLEDGE_EXPORT,
            RULE_READ, RULE_WRITE,
            PATHWAY_READ,
            CONTEXT_READ, EVENT_READ,
            RECOMMENDATION_READ,
            PROJECTION_READ,
            MPI_READ,
            AUDIT_READ, AUDIT_EXPORT,
            FOLLOWUP_READ,
            WORKFLOW_READ,
            NOTIFICATION_READ,
            LLM_READ,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_QC_DASHBOARD,
            MENU_QC_ALERTS,
            MENU_INSURANCE_AUDIT,
            MENU_QC_EVAL_SETS,
            MENU_KNOWLEDGE_GOVERNANCE,
            // 质量治理员是红线规则法定第二名独立委员（作者被排除，客户租户内仅临床/质量治理员可双签），
            // 必须能进入规则配置页完成会签，否则红线规则治理走不完院级全量。见 P5-ACT4-01。
            MENU_RULE_DEFINITIONS,
            MENU_ADMIN_AUDIT,
            MENU_PROVENANCE);
    }

    private static EnumSet<PermissionCode> complianceAuditPermissions() {
        return withMenus(EnumSet.of(
            DATA_GROUP, DATA_DESENSITIZED,
            ASSET_KNOWLEDGE_PACKAGE,
            ENV_PRODUCTION,
            ORG_READ,
            AUDIT_READ, AUDIT_EXPORT,
            KNOWLEDGE_READ, KNOWLEDGE_EXPORT,
            RULE_READ,
            PATHWAY_READ,
            CONTEXT_READ, EVENT_READ,
            EVALUATION_READ,
            PROJECTION_READ,
            FOLLOWUP_READ,
            WORKFLOW_READ,
            NOTIFICATION_READ,
            INTEGRATION_READ,
            MPI_READ,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_ADMIN_AUDIT,
            MENU_PROVENANCE);
    }

    private static EnumSet<PermissionCode> integrationOperationsPermissions() {
        return withMenus(EnumSet.of(
            DATA_GROUP,
            ASSET_CONFIG_PACKAGE, ASSET_DICTIONARY,
            ENV_TEST, ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            PACKAGE_READ,
            TERM_READ, TERM_WRITE,
            KNOWLEDGE_READ,
            CONTEXT_READ, CONTEXT_WRITE,
            EVENT_READ, EVENT_WRITE,
            RECOMMENDATION_READ, RECOMMENDATION_WRITE,
            EVALUATION_READ, EVALUATION_EXECUTE,
            SYSTEM_READ, SYSTEM_MANAGE,
            AUDIT_READ,
            FOLLOWUP_READ,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_EXECUTE, LLM_MANAGE,
            INTEGRATION_READ, INTEGRATION_WRITE, INTEGRATION_EXECUTE,
            PROJECTION_READ, PROJECTION_REBUILD,
            MPI_READ,
            WORKBENCH_READINESS_VIEW,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_TERMINOLOGY_MAPPING,
            MENU_ADAPTER_HUB,
            MENU_IDENTITY_BINDINGS,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_SYSTEM_PROVIDERS,
            MENU_NOTIFICATION_SETTINGS,
            MENU_GRAPH_EXPLORE,
            MENU_AI_WORKFLOWS,
            MENU_DOMESTIC_CHECK,
            MENU_DEV_CONSOLE);
    }

    private static EnumSet<PermissionCode> implementationOperationsPermissions() {
        return withMenus(EnumSet.of(
            DATA_HOSPITAL,
            ASSET_CONFIG_PACKAGE, ASSET_DICTIONARY,
            ENV_TEST, ENV_TRIAL,
            ORG_READ, ORG_WRITE,
            TENANT_READ,
            PACKAGE_READ, PACKAGE_PUBLISH,
            TERM_READ, TERM_WRITE,
            KNOWLEDGE_READ,
            CONTEXT_READ, CONTEXT_WRITE,
            EVENT_READ, EVENT_WRITE,
            SYSTEM_READ, TENANT_OVERRIDE,
            AUDIT_READ,
            FOLLOWUP_READ, FOLLOWUP_WRITE,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_EXECUTE, LLM_MANAGE,
            INTEGRATION_READ, INTEGRATION_WRITE, INTEGRATION_EXECUTE,
            PROJECTION_READ, PROJECTION_REBUILD,
            MPI_READ,
            WORKBENCH_READINESS_VIEW,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_TENANT_ONBOARDING,
            MENU_ADMIN_USERS,
            MENU_IDENTITY_BINDINGS,
            MENU_IMPLEMENTATION_GUIDE,
            MENU_ADAPTER_HUB,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_CONFIG_PACKAGES,
            MENU_TERMINOLOGY_MAPPING,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_SYSTEM_PROVIDERS,
            MENU_NOTIFICATION_SETTINGS,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE,
            MENU_AI_WORKFLOWS,
            MENU_DOMESTIC_CHECK,
            MENU_DEV_CONSOLE);
    }

    private static EnumSet<PermissionCode> allNonEmergencyPermissions() {
        EnumSet<PermissionCode> permissions = allRuntimePermissions();
        permissions.remove(ENV_EMERGENCY);
        return permissions;
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

    private static EnumSet<PermissionCode> withOnlyMenus(
            EnumSet<PermissionCode> permissions,
            PermissionCode... menuPermissions) {
        permissions.removeIf(permission -> permission.dimension() == PermissionDimension.MENU);
        return withMenus(permissions, menuPermissions);
    }

    public static Set<PermissionCode> permissionsOf(RoleCode role) {
        return POLICY.getOrDefault(role, EnumSet.noneOf(PermissionCode.class));
    }

    public static boolean has(RoleCode role, PermissionCode permission) {
        return permissionsOf(role).contains(permission);
    }
}
