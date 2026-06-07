package com.medkernel.engine.security;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.medkernel.engine.security.PermissionCode.*;

/**
 * 默认角色与动作权限的规则绑定策略类（Default Permission Policy）。
 *
 * <p>MedKernel v1.0 GA 默认角色与权限的关联规则基线映射。
 * 这是代码侧的默认权限授权策略。当前租户可通过 {@code role_permission} 表在默认策略基础上做局部增减，
 * 但任何医院不得授予未在 {@link PermissionCode} 中登记的权限。
 *
 * <p>默认设计原则包括：
 * <ol>
 *   <li><b>最小授权</b>：临床医生只看本人病例和提醒，不能改规则；护理只看护理资产</li>
 *   <li><b>分权审核</b>：发布类（{@code *.publish}）严格限定给医务处 / 质控办 / 医院管理员</li>
 *   <li><b>审计独立</b>：合规审计仅有读 + 导出，不允许业务写</li>
 *   <li><b>平台 / 集团兜底</b>：平台和集团管理员拥有全部常规权限；应急权限必须另行时限授予</li>
 * </ol>
 */
public final class DefaultPermissionPolicy {

    private static final Map<RoleCode, Set<PermissionCode>> POLICY;

    static {
        EnumMap<RoleCode, Set<PermissionCode>> map = new EnumMap<>(RoleCode.class);

        // 内置超级管理员：经 RBAC 拥有所有运行时权限，不通过 if 分支旁路。
        map.put(RoleCode.SYSTEM_SUPERADMIN, allRuntimePermissions());

        // 平台 / 集团管理员：除 break-glass 外的全部常规权限；应急权限必须走时限授予。
        map.put(RoleCode.PLATFORM_ADMIN, allNonEmergencyPermissions());
        map.put(RoleCode.GROUP_ADMIN, allNonEmergencyPermissions());

        // 医院管理员：除平台运维和 break-glass 外的全部常规权限
        EnumSet<PermissionCode> hospitalAdmin = allNonEmergencyPermissions();
        hospitalAdmin.remove(SYSTEM_MANAGE);
        map.put(RoleCode.HOSPITAL_ADMIN, hospitalAdmin);

        // 信息科：基础设施读写 + 运维 + 字典 + 配置包 + 临床上下文接入
        map.put(RoleCode.IT_OPS, withMenus(EnumSet.of(
            DATA_GROUP,
            ASSET_CONFIG_PACKAGE, ASSET_DICTIONARY,
            ENV_TEST, ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ, ORG_WRITE,
            TENANT_READ, TENANT_WRITE,
            PACKAGE_READ, PACKAGE_PUBLISH, PACKAGE_ROLLBACK,
            TERM_READ, TERM_WRITE, TERM_PUBLISH,
            CONTEXT_READ, CONTEXT_WRITE,
            EVENT_READ, EVENT_WRITE,
            RECOMMENDATION_READ, RECOMMENDATION_WRITE,
            EVALUATION_EXECUTE,
            SYSTEM_READ, SYSTEM_MANAGE,
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
            MENU_IMPLEMENTATION_GUIDE,
            MENU_TENANT_ONBOARDING,
            MENU_CONFIG_PACKAGES,
            MENU_TERMINOLOGY_MAPPING,
            MENU_ADAPTER_HUB,
            MENU_QC_DASHBOARD,
            MENU_QC_EVAL_RESULTS,
            MENU_IDENTITY_BINDINGS,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_SYSTEM_PROVIDERS,
            MENU_NOTIFICATION_SETTINGS,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE,
            MENU_AI_WORKFLOWS,
            MENU_DOMESTIC_CHECK,
            MENU_DEV_CONSOLE));

        // 医务处：知识/规则/路径审核与发布 + 上下文只读
        map.put(RoleCode.MEDICAL_AFFAIRS, withMenus(EnumSet.of(
            DATA_HOSPITAL,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW, KNOWLEDGE_PUBLISH, KNOWLEDGE_WITHDRAW, KNOWLEDGE_EXPORT,
            RULE_READ, RULE_WRITE, RULE_PUBLISH,
            PATHWAY_READ, PATHWAY_WRITE, PATHWAY_PUBLISH,
            TERM_READ,
            CONTEXT_READ, EVENT_READ,
            EVALUATION_READ,
            RECOMMENDATION_READ, RECOMMENDATION_WRITE,
            PROJECTION_READ,
            MPI_READ, MPI_WRITE,
            AUDIT_READ, AUDIT_EXPORT,
            FOLLOWUP_READ, FOLLOWUP_WRITE,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_EXECUTE,
            LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_PATHWAY_TEMPLATES,
            MENU_RULE_DEFINITIONS,
            MENU_TERMINOLOGY_MAPPING,
            MENU_PATIENT_PATHWAYS,
            MENU_CDSS_FATIGUE,
            MENU_RULE_VALIDATE,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP,
            MENU_QC_DASHBOARD,
            MENU_QC_ALERTS,
            MENU_QC_EVAL_RESULTS,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE,
            MENU_AI_WORKFLOWS));

        // 质控办：评估指标审核发布 + 质控发现 + 上下文只读
        map.put(RoleCode.QA_MANAGER, withMenus(EnumSet.of(
            DATA_HOSPITAL,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_TRIAL, ENV_PRODUCTION,
            ORG_READ,
            EVALUATION_READ, EVALUATION_WRITE, EVALUATION_PUBLISH, EVALUATION_EXECUTE,
            EVALUATION_REMEDIATE, EVALUATION_REVIEW,
            KNOWLEDGE_READ, KNOWLEDGE_EXPORT,
            RULE_READ,
            PATHWAY_READ,
            CONTEXT_READ, EVENT_READ,
            RECOMMENDATION_READ,
            PROJECTION_READ,
            MPI_READ,
            AUDIT_READ, AUDIT_EXPORT,
            FOLLOWUP_READ, WORKFLOW_READ, NOTIFICATION_READ, EMBED_READ,
            LLM_READ, LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_QC_DASHBOARD,
            MENU_QC_ALERTS,
            MENU_QC_EVAL_SETS,
            MENU_QC_EVAL_RESULTS,
            MENU_KNOWLEDGE_GOVERNANCE,
            MENU_ADMIN_AUDIT,
            MENU_PROVENANCE));

        // 医保办：医保规则维护（属于规则一类）+ 评估
        map.put(RoleCode.INSURANCE_MANAGER, withMenus(EnumSet.of(
            DATA_HOSPITAL, DATA_DESENSITIZED,
            ASSET_RULE,
            ENV_PRODUCTION,
            ORG_READ,
            RULE_READ, RULE_WRITE,
            EVALUATION_READ,
            KNOWLEDGE_READ,
            AUDIT_READ),
            MENU_WORKBENCH,
            MENU_INSURANCE_AUDIT,
            MENU_QC_DASHBOARD,
            MENU_QC_EVAL_RESULTS));

        // 科主任：本科室路径/规则审核 + 评估查看 + 上下文只读
        map.put(RoleCode.DEPT_HEAD, withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            PATHWAY_READ, PATHWAY_WRITE,
            RULE_READ, RULE_WRITE,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW,
            CONTEXT_READ, EVENT_READ,
            EVALUATION_READ, EVALUATION_REMEDIATE,
            RECOMMENDATION_READ,
            MPI_READ,
            FOLLOWUP_READ,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE),
            MENU_WORKBENCH,
            MENU_PATHWAY_TEMPLATES,
            MENU_RULE_DEFINITIONS,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_CDSS_FATIGUE,
            MENU_RULE_VALIDATE,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP,
            MENU_QC_DASHBOARD,
            MENU_QC_ALERTS,
            MENU_QC_EVAL_RESULTS,
            MENU_KNOWLEDGE_GOVERNANCE));

        // 专科专家：知识/路径审核 + 上下文只读
        map.put(RoleCode.SPECIALIST, withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_DICTIONARY, ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_TEST, ENV_PRODUCTION,
            ORG_READ,
            KNOWLEDGE_READ, KNOWLEDGE_WRITE, KNOWLEDGE_REVIEW,
            PATHWAY_READ, PATHWAY_WRITE,
            RULE_READ, RULE_WRITE,
            TERM_READ, TERM_WRITE,
            CONTEXT_READ, EVENT_READ,
            RECOMMENDATION_READ,
            MPI_READ,
            FOLLOWUP_READ,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE),
            MENU_WORKBENCH,
            MENU_PATHWAY_TEMPLATES,
            MENU_RULE_DEFINITIONS,
            MENU_TERMINOLOGY_MAPPING,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_RULE_VALIDATE,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP,
            MENU_KNOWLEDGE_GOVERNANCE));

        // 临床医生：看提醒、采纳/拒绝、查看路径与规则 + 临床上下文只读
        map.put(RoleCode.DOCTOR, withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            RECOMMENDATION_READ, RECOMMENDATION_ACCEPT,
            PATHWAY_READ,
            RULE_READ,
            CONTEXT_READ, EVENT_READ,
            KNOWLEDGE_READ,
            MPI_READ,
            FOLLOWUP_READ,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_EXECUTE),
            MENU_WORKBENCH,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_CDSS_FATIGUE,
            MENU_RULE_VALIDATE,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP));

        // 护理人员：护理决策与提醒 + 临床上下文只读
        map.put(RoleCode.NURSE, withMenus(EnumSet.of(
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            RECOMMENDATION_READ, RECOMMENDATION_ACCEPT,
            PATHWAY_READ,
            CONTEXT_READ, EVENT_READ,
            KNOWLEDGE_READ,
            MPI_READ,
            FOLLOWUP_READ,
            WORKFLOW_READ, WORKFLOW_WRITE,
            NOTIFICATION_READ, NOTIFICATION_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_EXECUTE),
            MENU_WORKBENCH,
            MENU_MPI,
            MENU_PATIENT_PATHWAYS,
            MENU_WORKFLOW_TODOS,
            MENU_NOTIFICATIONS,
            MENU_CLINICAL_FOLLOWUP));

        // 合规审计：只读 + 导出，禁所有写
        map.put(RoleCode.AUDIT_COMPLIANCE, withMenus(EnumSet.of(
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
            FOLLOWUP_READ, WORKFLOW_READ, NOTIFICATION_READ, INTEGRATION_READ, MPI_READ, LIST_EXPORT),
            MENU_WORKBENCH,
            MENU_IDENTITY_BINDINGS,
            MENU_ADMIN_AUDIT,
            MENU_SECURITY_BASELINE,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE));

        // 实施工程师：试点准备阶段的接入与配置 + 临床上下文接入
        map.put(RoleCode.IMPLEMENTATION_ENGINEER, withMenus(EnumSet.of(
            DATA_HOSPITAL,
            ASSET_CONFIG_PACKAGE, ASSET_DICTIONARY,
            ENV_TEST, ENV_TRIAL,
            ORG_READ, ORG_WRITE,
            TENANT_READ,
            PACKAGE_READ, PACKAGE_PUBLISH,
            TERM_READ, TERM_WRITE,
            CONTEXT_READ, CONTEXT_WRITE,
            EVENT_READ, EVENT_WRITE,
            SYSTEM_READ,
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
            MENU_IMPLEMENTATION_GUIDE,
            MENU_TENANT_ONBOARDING,
            MENU_CONFIG_PACKAGES,
            MENU_PATHWAY_TEMPLATES,
            MENU_RULE_DEFINITIONS,
            MENU_TERMINOLOGY_MAPPING,
            MENU_ADAPTER_HUB,
            MENU_ADMIN_AUDIT,
            MENU_SYSTEM_PROVIDERS,
            MENU_NOTIFICATION_SETTINGS,
            MENU_PROVENANCE,
            MENU_GRAPH_EXPLORE,
            MENU_AI_WORKFLOWS,
            MENU_DOMESTIC_CHECK,
            MENU_DEV_CONSOLE));

        POLICY = Map.copyOf(map);
    }

    private DefaultPermissionPolicy() {
    }

    private static EnumSet<PermissionCode> allNonEmergencyPermissions() {
        EnumSet<PermissionCode> permissions = allRuntimePermissions();
        permissions.remove(PermissionCode.ENV_EMERGENCY);
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

    public static Set<PermissionCode> permissionsOf(RoleCode role) {
        return POLICY.getOrDefault(role, EnumSet.noneOf(PermissionCode.class));
    }

    public static boolean has(RoleCode role, PermissionCode permission) {
        return permissionsOf(role).contains(permission);
    }
}
