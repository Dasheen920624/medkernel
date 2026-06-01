package com.medkernel.engine.contract;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionDimension;
import com.medkernel.shared.audit.AuditAction;

/**
 * SYS-02 服务契约目录。
 *
 * <p>所有 {@code /api/v1} 控制器必须在这里登记。目录只用类名字符串引用
 * compliance/shared 控制器，避免 engine 包对业务包产生编译期依赖。
 */
public final class ServiceContractCatalog {
    private static final List<ServiceContract> CONTRACTS = List.of(
        contract("compliance-audit", "合规审计服务",
            "com.medkernel.compliance.audit.AuditController", "/api/v1/compliance/audit",
            permissions("audit.read", "system.manage", "audit.export"),
            audits(
                audit(AuditAction.EXPORT, "audit_event", "导出审计快照"),
                audit(AuditAction.PERMISSION_CHANGE, "audit_config", "校验审计高危配置"))),
        contract("compliance-evidence", "合规证据服务",
            "com.medkernel.compliance.evidence.controller.EvidenceController", "/api/v1/compliance/evidence",
            permissions("audit.read", "audit.export"),
            audits(audit(AuditAction.EXPORT, "evidence_snapshot", "生成、校验和导出合规证据"))),
        contract("clinical-event", "临床事件服务",
            "com.medkernel.engine.context.ClinicalEventController", "/api/v1/engine/events",
            permissions("event.write", "event.read"),
            audits(
                audit(AuditAction.CREATE, "clinical_event", "接收临床事件"),
                audit(AuditAction.EXECUTE, "clinical_event", "异步处理和重放临床事件"))),
        contract("context-snapshot", "标准上下文快照服务",
            "com.medkernel.engine.context.ContextSnapshotController", "/api/v1/engine/context/snapshots",
            permissions("context.write", "context.read"),
            audits(audit(AuditAction.CREATE, "context_snapshot", "创建标准上下文快照"))),
        contract("embed", "嵌入启动服务",
            "com.medkernel.engine.embed.EmbedEngineController", "/api/v1/engine/embed",
            permissions("embed.write", "embed.read"),
            audits(
                audit(AuditAction.CREATE, "embed_launch_token", "生成嵌入启动令牌"),
                audit(AuditAction.FEEDBACK, "embed_feedback", "记录嵌入反馈"))),
        contract("evaluation", "评估质控服务",
            "com.medkernel.engine.evaluation.EvaluationEngineController", "/api/v1/engine/evaluations",
            permissions("evaluation.write", "evaluation.read", "evaluation.publish",
                "evaluation.execute", "evaluation.remediate", "evaluation.review"),
            audits(
                audit(AuditAction.CREATE, "evaluation_indicator", "创建和提交评估指标"),
                audit(AuditAction.PUBLISH, "evaluation_indicator", "发布评估指标"),
                audit(AuditAction.EXECUTE, "evaluation_run", "执行质控评估"),
                audit(AuditAction.REVIEW, "quality_finding", "整改复核"))),
        contract("saved-view", "用户保存视图服务",
            "com.medkernel.engine.experience.SavedViewController", "/api/v1/experience",
            List.of(),
            audits(audit(AuditAction.UPDATE, "saved_view", "保存或更新用户视图"))),
        contract("theme-preference", "主题偏好服务",
            "com.medkernel.engine.experience.ThemePreferenceController", "/api/v1/experience",
            List.of(),
            audits(audit(AuditAction.UPDATE, "theme_preference", "保存用户主题偏好"))),
        contract("followup", "随访服务",
            "com.medkernel.engine.followup.FollowupEngineController", "/api/v1/engine/followup",
            permissions("followup.write", "followup.read"),
            audits(
                audit(AuditAction.CREATE, "followup_plan", "生成随访计划"),
                audit(AuditAction.FEEDBACK, "followup_event", "记录问卷和异常回流"))),
        contract("integration", "第三方集成服务",
            "com.medkernel.engine.integration.controller.IntegrationController", "/api/v1/engine/integration",
            permissions("integration.read", "integration.write", "integration.execute"),
            audits(
                audit(AuditAction.CREATE, "integration_adapter", "创建适配器和 Webhook"),
                audit(AuditAction.UPDATE, "integration_adapter", "更新适配器和 Webhook"),
                audit(AuditAction.EXECUTE, "integration_adapter", "连接自检、Webhook 测试和死信重试"),
                audit(AuditAction.DELETE, "integration_message_log", "归档集成死信日志"))),
        contract("knowledge-export", "知识导出服务",
            "com.medkernel.engine.knowledge.KnowledgeExportController", "/api/v1/engine/knowledge/exports",
            permissions("knowledge.export"),
            audits(audit(AuditAction.EXPORT, "knowledge_export", "创建、取消和下载知识导出任务"))),
        contract("knowledge-identity", "知识身份服务",
            "com.medkernel.engine.knowledge.KnowledgeIdentityController", "/api/v1/engine/knowledge/identities",
            permissions("knowledge.read", "knowledge.write"),
            audits(audit(AuditAction.CREATE, "knowledge_source", "登记知识来源和片段"))),
        contract("knowledge-version", "知识版本服务",
            "com.medkernel.engine.knowledge.KnowledgeVersionController", "/api/v1/engine/knowledge",
            permissions("knowledge.read", "knowledge.publish", "knowledge.withdraw", "knowledge.write"),
            audits(
                audit(AuditAction.CREATE, "knowledge_version", "创建知识版本草稿"),
                audit(AuditAction.PUBLISH, "knowledge_version", "激活或撤回知识版本"))),
        contract("large-list", "大规模列表服务",
            "com.medkernel.engine.list.LargeListController", "/api/v1/large-lists",
            permissions("audit.read", "list.export"),
            audits(audit(AuditAction.EXPORT, "large_list_export", "创建和下载大规模列表导出"))),
        contract("model-gateway", "模型能力网关服务",
            "com.medkernel.engine.llm.ModelGatewayController", "/api/v1/model-capabilities",
            permissions("llm.read", "llm.write"),
            audits(
                audit(AuditAction.EXECUTE, "model_capability_task", "提交和重试模型任务"),
                audit(AuditAction.UPDATE, "model_policy", "校验模型路由策略"))),
        contract("mpi", "患者主索引服务",
            "com.medkernel.engine.mpi.MpiController", "/api/v1/clinical/mpi",
            permissions("mpi.read", "mpi.write"),
            audits(audit(AuditAction.UPDATE, "mpi_patient", "合并患者主索引"))),
        contract("org-unit", "组织单元服务",
            "com.medkernel.engine.org.OrgUnitController", "/api/v1/tenant/org-units",
            permissions("org.read", "org.write"),
            audits(audit(AuditAction.UPDATE, "org_unit", "创建和调整组织单元"))),
        contract("pathway", "路径引擎服务",
            "com.medkernel.engine.pathway.PathwayEngineController", "/api/v1/engine/pathways",
            permissions("pathway.write", "pathway.read", "pathway.publish"),
            audits(
                audit(AuditAction.CREATE, "pathway_template", "创建路径包、模板和患者路径"),
                audit(AuditAction.PUBLISH, "pathway_template", "发布路径模板"),
                audit(AuditAction.EXECUTE, "patient_pathway", "推进患者路径"))),
        contract("package", "配置包服务",
            "com.medkernel.engine.pkg.PackageEngineController", "/api/v1/engine/packages",
            permissions("package.publish", "package.read", "package.rollback"),
            audits(
                audit(AuditAction.CREATE, "knowledge_package", "创建配置包和包条目"),
                audit(AuditAction.EXPORT, "knowledge_package", "导出差异和离线包"),
                audit(AuditAction.IMPORT, "knowledge_package", "导入离线包"),
                audit(AuditAction.PUBLISH, "knowledge_package", "同步发布配置包"),
                audit(AuditAction.ROLLBACK, "knowledge_package", "回滚配置包"))),
        contract("projection", "关系库权威投影服务",
            "com.medkernel.engine.projection.ProjectionController", "/api/v1/projections/clinical-graph",
            permissions("projection.read", "projection.rebuild"),
            audits(audit(AuditAction.EXECUTE, "mk_projection_sync", "从关系库权威源重建临床图投影"))),
        contract("recommendation", "推荐提醒服务",
            "com.medkernel.engine.recommendation.RecommendationEngineController", "/api/v1/engine/recommendations",
            permissions("recommendation.write", "recommendation.read", "recommendation.accept"),
            audits(
                audit(AuditAction.CREATE, "recommendation_trigger", "创建推荐触发"),
                audit(AuditAction.FEEDBACK, "recommendation_card", "记录提醒反馈"))),
        contract("rule", "规则引擎服务",
            "com.medkernel.engine.rule.RuleEngineController", "/api/v1/engine/rules",
            permissions("rule.write", "rule.read", "rule.publish"),
            audits(
                audit(AuditAction.CREATE, "rule_definition", "创建规则和测试用例"),
                audit(AuditAction.PUBLISH, "rule_definition", "发布规则版本"),
                audit(AuditAction.EXECUTE, "rule_execution", "执行规则"))),
        contract("security-me", "当前用户安全画像服务",
            "com.medkernel.engine.security.SecurityMeController", "/api/v1/security",
            List.of(),
            List.of()),
        contract("menu-permission", "二级菜单权限矩阵服务",
            "com.medkernel.engine.security.MenuPermissionController", "/api/v1/security/menu-permissions",
            permissions("org.read", "org.write"),
            audits(audit(AuditAction.PERMISSION_CHANGE, "role_permission", "调整租户级角色菜单权限覆盖"))),
        contract("user-role-assignment", "用户角色绑定服务",
            "com.medkernel.engine.security.UserRoleAssignmentController", "/api/v1/compliance/user-roles",
            permissions("org.read", "org.write"),
            audits(audit(AuditAction.PERMISSION_CHANGE, "user_role_assignment", "分配和删除用户角色"))),
        contract("auth", "账号登录服务",
            "com.medkernel.engine.security.auth.AuthController", "/api/v1/auth",
            List.of(),
            audits(
                audit(AuditAction.LOGIN, "platform_credential", "账号登录"),
                audit(AuditAction.LOGOUT, "platform_credential", "账号登出"),
                audit(AuditAction.PERMISSION_CHANGE, "platform_credential", "自助修改密码")),
            publicEndpoints("POST /api/v1/auth/login", "POST /api/v1/auth/logout")),
        contract("credential-admin", "凭证管理服务",
            "com.medkernel.engine.security.auth.CredentialAdminController", "/api/v1/admin/credentials",
            permissions("org.read", "org.write"),
            audits(audit(AuditAction.PERMISSION_CHANGE, "platform_credential", "创建、重置和锁定凭证"))),
        contract("tenant-provisioning", "租户开通服务",
            "com.medkernel.engine.security.auth.TenantProvisioningController", "/api/v1/admin/tenants",
            permissions("tenant.read", "tenant.write"),
            audits(audit(AuditAction.CREATE, "tenant", "开通和调整租户"))),
        contract("bootstrap", "首次部署引导服务",
            "com.medkernel.engine.security.bootstrap.BootstrapController", "/api/v1/bootstrap",
            List.of(),
            audits(
                audit(AuditAction.CREATE, "bootstrap_init_token", "校验首次部署令牌"),
                audit(AuditAction.PERMISSION_CHANGE, "platform_credential", "创建首发管理员和绑定 MFA")),
            publicEndpoints("POST /api/v1/bootstrap/init-token", "POST /api/v1/bootstrap/password")),
        contract("branding", "平台品牌服务",
            "com.medkernel.engine.tenant.BrandingController", "/api/v1/platform/branding",
            permissions("tenant.read", "tenant.write"),
            audits(audit(AuditAction.UPDATE, "tenant_branding", "更新租户品牌配置"))),
        contract("success-lifecycle", "租户生命周期服务",
            "com.medkernel.engine.tenant.SuccessController", "/api/v1/platform/success/lifecycle",
            permissions("tenant.read", "tenant.write"),
            audits(audit(AuditAction.UPDATE, "tenant_lifecycle", "推进租户生命周期"))),
        contract("terminology", "字典映射服务",
            "com.medkernel.engine.terminology.TerminologyController", "/api/v1/engine/terminology",
            permissions("term.read", "term.write", "term.publish", "package.rollback"),
            audits(
                audit(AuditAction.CREATE, "term_mapping", "确认和解决字典映射"),
                audit(AuditAction.PUBLISH, "term_package", "发布字典包"),
                audit(AuditAction.ROLLBACK, "term_package", "回滚字典包"))),
        contract("system-config", "系统配置服务",
            "com.medkernel.shared.config.SystemConfigController", "/api/v1/system/configs",
            permissions("system.read", "system.manage"),
            audits(
                audit(AuditAction.UPDATE, "mk_config_item", "修改系统配置"),
                audit(AuditAction.ROLLBACK, "mk_config_item", "回滚系统配置"),
                audit(AuditAction.PERMISSION_CHANGE, "mk_config_item", "修改高危安全配置"))),
        contract("observability-diagnose", "可观测性诊断服务",
            "com.medkernel.shared.observability.ObservabilityDiagnoseController", "/api/v1/engine/diagnose",
            permissions("system.read", "audit.read"),
            List.of()),
        contract("runtime-operations", "运行状态服务",
            "com.medkernel.shared.runtime.RuntimeOperationsController", "/api/v1/system",
            permissions("system.read"),
            List.of()),
        contract("runtime-task", "运行任务框架服务",
            "com.medkernel.shared.runtime.task.RuntimeTaskController", "/api/v1/system/tasks",
            permissions("system.read", "system.manage"),
            audits(
                audit(AuditAction.CREATE, "sys_task", "提交在线、异步、批量、离线或死信回放任务"),
                audit(AuditAction.UPDATE, "sys_task", "人工重试或重试耗尽进入死信"),
                audit(AuditAction.EXECUTE, "sys_task", "执行在线、离线、批量或回放运行任务"),
                audit(AuditAction.CREATE, "sys_task_dead_letter", "创建运行任务死信"),
                audit(AuditAction.UPDATE, "sys_task_dead_letter", "记录死信人工回放任务"))),
        contract("health", "系统心跳服务",
            "com.medkernel.shared.web.HealthController", "/api/v1/system",
            List.of(),
            List.of(),
            publicEndpoints("GET /api/v1/system/ping")),
        contract("runtime-probe", "运行时探针服务",
            "com.medkernel.shared.web.RuntimeProbeController", "/api/v1/system",
            permissions("system.read"),
            List.of())
    ).stream()
        .sorted(Comparator.comparing(ServiceContract::id))
        .toList();

    private ServiceContractCatalog() {
    }

    public static List<ServiceContract> contracts() {
        return CONTRACTS;
    }

    public static Optional<ServiceContract> contractOfController(String controllerClassName) {
        return CONTRACTS.stream()
            .filter(contract -> contract.controllerClassName().equals(controllerClassName))
            .findFirst();
    }

    public static List<String> openApiPaths() {
        return CONTRACTS.stream()
            .flatMap(contract -> contract.openApiPaths().stream())
            .distinct()
            .sorted()
            .toList();
    }

    private static ServiceContract contract(String id,
                                            String title,
                                            String controllerClassName,
                                            String basePath,
                                            List<ServicePermissionDeclaration> permissions,
                                            List<ServiceAuditDeclaration> auditPoints,
                                            List<String> publicEndpoints) {
        return new ServiceContract(id, title, controllerClassName, basePath,
            List.of(basePath + "/**"), permissions, auditPoints, publicEndpoints);
    }

    private static ServiceContract contract(String id,
                                            String title,
                                            String controllerClassName,
                                            String basePath,
                                            List<ServicePermissionDeclaration> permissions,
                                            List<ServiceAuditDeclaration> auditPoints) {
        return contract(id, title, controllerClassName, basePath, permissions, auditPoints, List.of());
    }

    private static List<ServicePermissionDeclaration> permissions(String... codes) {
        return java.util.Arrays.stream(codes)
            .map(code -> permission(code, PermissionCode.fromCode(code)
                .map(PermissionCode::dimension)
                .orElseThrow(() -> new IllegalArgumentException("未登记权限码 " + code))))
            .toList();
    }

    private static ServicePermissionDeclaration permission(String code, PermissionDimension dimension) {
        return new ServicePermissionDeclaration(code, dimension, PermissionCode.fromCode(code)
            .map(PermissionCode::displayName)
            .orElse(code));
    }

    private static List<ServiceAuditDeclaration> audits(ServiceAuditDeclaration... declarations) {
        return List.of(declarations);
    }

    private static ServiceAuditDeclaration audit(AuditAction action, String targetType, String purpose) {
        return new ServiceAuditDeclaration(action, targetType, purpose);
    }

    private static List<String> publicEndpoints(String... endpoints) {
        return List.of(endpoints);
    }
}
