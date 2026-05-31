package com.medkernel.engine.security;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.medkernel.engine.security.PermissionCode.AUDIT_EXPORT;
import static com.medkernel.engine.security.PermissionCode.AUDIT_READ;
import static com.medkernel.engine.security.PermissionCode.ASSET_CONFIG_PACKAGE;
import static com.medkernel.engine.security.PermissionCode.ASSET_DICTIONARY;
import static com.medkernel.engine.security.PermissionCode.ASSET_KNOWLEDGE_PACKAGE;
import static com.medkernel.engine.security.PermissionCode.ASSET_PATHWAY;
import static com.medkernel.engine.security.PermissionCode.ASSET_RULE;
import static com.medkernel.engine.security.PermissionCode.CONTEXT_READ;
import static com.medkernel.engine.security.PermissionCode.CONTEXT_WRITE;
import static com.medkernel.engine.security.PermissionCode.DATA_DEPARTMENT;
import static com.medkernel.engine.security.PermissionCode.DATA_DESENSITIZED;
import static com.medkernel.engine.security.PermissionCode.DATA_GROUP;
import static com.medkernel.engine.security.PermissionCode.DATA_HOSPITAL;
import static com.medkernel.engine.security.PermissionCode.EVENT_READ;
import static com.medkernel.engine.security.PermissionCode.EVENT_WRITE;
import static com.medkernel.engine.security.PermissionCode.ENV_PRODUCTION;
import static com.medkernel.engine.security.PermissionCode.ENV_TEST;
import static com.medkernel.engine.security.PermissionCode.ENV_TRIAL;
import static com.medkernel.engine.security.PermissionCode.EVALUATION_EXECUTE;
import static com.medkernel.engine.security.PermissionCode.EVALUATION_PUBLISH;
import static com.medkernel.engine.security.PermissionCode.EVALUATION_READ;
import static com.medkernel.engine.security.PermissionCode.EVALUATION_REMEDIATE;
import static com.medkernel.engine.security.PermissionCode.EVALUATION_REVIEW;
import static com.medkernel.engine.security.PermissionCode.EVALUATION_WRITE;
import static com.medkernel.engine.security.PermissionCode.KNOWLEDGE_EXPORT;
import static com.medkernel.engine.security.PermissionCode.KNOWLEDGE_PUBLISH;
import static com.medkernel.engine.security.PermissionCode.KNOWLEDGE_READ;
import static com.medkernel.engine.security.PermissionCode.KNOWLEDGE_REVIEW;
import static com.medkernel.engine.security.PermissionCode.KNOWLEDGE_WITHDRAW;
import static com.medkernel.engine.security.PermissionCode.KNOWLEDGE_WRITE;
import static com.medkernel.engine.security.PermissionCode.ORG_PUBLISH;
import static com.medkernel.engine.security.PermissionCode.ORG_READ;
import static com.medkernel.engine.security.PermissionCode.ORG_WRITE;
import static com.medkernel.engine.security.PermissionCode.PACKAGE_PUBLISH;
import static com.medkernel.engine.security.PermissionCode.PACKAGE_READ;
import static com.medkernel.engine.security.PermissionCode.PACKAGE_ROLLBACK;
import static com.medkernel.engine.security.PermissionCode.PATHWAY_PUBLISH;
import static com.medkernel.engine.security.PermissionCode.PATHWAY_READ;
import static com.medkernel.engine.security.PermissionCode.PATHWAY_WRITE;
import static com.medkernel.engine.security.PermissionCode.RECOMMENDATION_ACCEPT;
import static com.medkernel.engine.security.PermissionCode.RECOMMENDATION_READ;
import static com.medkernel.engine.security.PermissionCode.RECOMMENDATION_WRITE;
import static com.medkernel.engine.security.PermissionCode.RULE_PUBLISH;
import static com.medkernel.engine.security.PermissionCode.RULE_READ;
import static com.medkernel.engine.security.PermissionCode.RULE_WRITE;
import static com.medkernel.engine.security.PermissionCode.SYSTEM_MANAGE;
import static com.medkernel.engine.security.PermissionCode.SYSTEM_READ;
import static com.medkernel.engine.security.PermissionCode.TENANT_READ;
import static com.medkernel.engine.security.PermissionCode.TENANT_WRITE;
import static com.medkernel.engine.security.PermissionCode.TERM_PUBLISH;
import static com.medkernel.engine.security.PermissionCode.TERM_READ;
import static com.medkernel.engine.security.PermissionCode.TERM_WRITE;
import static com.medkernel.engine.security.PermissionCode.FOLLOWUP_READ;
import static com.medkernel.engine.security.PermissionCode.FOLLOWUP_WRITE;
import static com.medkernel.engine.security.PermissionCode.EMBED_READ;
import static com.medkernel.engine.security.PermissionCode.EMBED_WRITE;
import static com.medkernel.engine.security.PermissionCode.LLM_READ;
import static com.medkernel.engine.security.PermissionCode.LLM_WRITE;
import static com.medkernel.engine.security.PermissionCode.LIST_EXPORT;
import static com.medkernel.engine.security.PermissionCode.MENU_ADVANCED_TOOLS;
import static com.medkernel.engine.security.PermissionCode.MENU_CLINICAL_RUN;
import static com.medkernel.engine.security.PermissionCode.MENU_COMPLIANCE_OPS;
import static com.medkernel.engine.security.PermissionCode.MENU_PILOT_SETUP;
import static com.medkernel.engine.security.PermissionCode.MENU_QUALITY_IMPROVE;
import static com.medkernel.engine.security.PermissionCode.MENU_WORKBENCH;

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

        // 平台 / 集团管理员：除 break-glass 外的全部常规权限；应急权限必须走时限授予。
        map.put(RoleCode.PLATFORM_ADMIN, allNonEmergencyPermissions());
        map.put(RoleCode.GROUP_ADMIN, allNonEmergencyPermissions());

        // 医院管理员：除平台运维和 break-glass 外的全部常规权限
        EnumSet<PermissionCode> hospitalAdmin = allNonEmergencyPermissions();
        hospitalAdmin.remove(SYSTEM_MANAGE);
        map.put(RoleCode.HOSPITAL_ADMIN, hospitalAdmin);

        // 信息科：基础设施读写 + 运维 + 字典 + 配置包 + 临床上下文接入
        map.put(RoleCode.IT_OPS, EnumSet.of(
            MENU_WORKBENCH, MENU_PILOT_SETUP, MENU_QUALITY_IMPROVE, MENU_COMPLIANCE_OPS, MENU_ADVANCED_TOOLS,
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
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_WRITE,
            LIST_EXPORT));

        // 医务处：知识/规则/路径审核与发布 + 上下文只读
        map.put(RoleCode.MEDICAL_AFFAIRS, EnumSet.of(
            MENU_WORKBENCH, MENU_PILOT_SETUP, MENU_CLINICAL_RUN, MENU_QUALITY_IMPROVE,
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
            AUDIT_READ, AUDIT_EXPORT,
            FOLLOWUP_READ, FOLLOWUP_WRITE,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_WRITE,
            LIST_EXPORT));

        // 质控办：评估指标审核发布 + 质控发现 + 上下文只读
        map.put(RoleCode.QA_MANAGER, EnumSet.of(
            MENU_WORKBENCH, MENU_QUALITY_IMPROVE, MENU_COMPLIANCE_OPS,
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
            AUDIT_READ, AUDIT_EXPORT,
            FOLLOWUP_READ, EMBED_READ,
            LLM_READ, LIST_EXPORT));

        // 医保办：医保规则维护（属于规则一类）+ 评估
        map.put(RoleCode.INSURANCE_MANAGER, EnumSet.of(
            MENU_WORKBENCH, MENU_QUALITY_IMPROVE,
            DATA_HOSPITAL, DATA_DESENSITIZED,
            ASSET_RULE,
            ENV_PRODUCTION,
            ORG_READ,
            RULE_READ, RULE_WRITE,
            EVALUATION_READ,
            KNOWLEDGE_READ,
            AUDIT_READ));

        // 科主任：本科室路径/规则审核 + 评估查看 + 上下文只读
        map.put(RoleCode.DEPT_HEAD, EnumSet.of(
            MENU_WORKBENCH, MENU_CLINICAL_RUN, MENU_QUALITY_IMPROVE,
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
            FOLLOWUP_READ));

        // 专科专家：知识/路径审核 + 上下文只读
        map.put(RoleCode.SPECIALIST, EnumSet.of(
            MENU_WORKBENCH, MENU_PILOT_SETUP, MENU_CLINICAL_RUN,
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
            FOLLOWUP_READ));

        // 临床医生：看提醒、采纳/拒绝、查看路径与规则 + 临床上下文只读
        map.put(RoleCode.DOCTOR, EnumSet.of(
            MENU_WORKBENCH, MENU_CLINICAL_RUN,
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_RULE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            RECOMMENDATION_READ, RECOMMENDATION_ACCEPT,
            PATHWAY_READ,
            RULE_READ,
            CONTEXT_READ, EVENT_READ,
            KNOWLEDGE_READ,
            FOLLOWUP_READ,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_WRITE));

        // 护理人员：护理决策与提醒 + 临床上下文只读
        map.put(RoleCode.NURSE, EnumSet.of(
            MENU_WORKBENCH, MENU_CLINICAL_RUN,
            DATA_DEPARTMENT,
            ASSET_KNOWLEDGE_PACKAGE, ASSET_PATHWAY,
            ENV_PRODUCTION,
            ORG_READ,
            RECOMMENDATION_READ, RECOMMENDATION_ACCEPT,
            PATHWAY_READ,
            CONTEXT_READ, EVENT_READ,
            KNOWLEDGE_READ,
            FOLLOWUP_READ,
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_WRITE));

        // 合规审计：只读 + 导出，禁所有写
        map.put(RoleCode.AUDIT_COMPLIANCE, EnumSet.of(
            MENU_WORKBENCH, MENU_COMPLIANCE_OPS, MENU_ADVANCED_TOOLS,
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
            FOLLOWUP_READ, LIST_EXPORT));

        // 实施工程师：试点准备阶段的接入与配置 + 临床上下文接入
        map.put(RoleCode.IMPLEMENTATION_ENGINEER, EnumSet.of(
            MENU_WORKBENCH, MENU_PILOT_SETUP, MENU_COMPLIANCE_OPS,
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
            EMBED_READ, EMBED_WRITE,
            LLM_READ, LLM_WRITE,
            LIST_EXPORT));

        POLICY = Map.copyOf(map);
    }

    private DefaultPermissionPolicy() {
    }

    private static EnumSet<PermissionCode> allNonEmergencyPermissions() {
        EnumSet<PermissionCode> permissions = EnumSet.allOf(PermissionCode.class);
        permissions.remove(PermissionCode.ENV_EMERGENCY);
        return permissions;
    }

    public static Set<PermissionCode> permissionsOf(RoleCode role) {
        return POLICY.getOrDefault(role, EnumSet.noneOf(PermissionCode.class));
    }

    public static boolean has(RoleCode role, PermissionCode permission) {
        return permissionsOf(role).contains(permission);
    }
}
