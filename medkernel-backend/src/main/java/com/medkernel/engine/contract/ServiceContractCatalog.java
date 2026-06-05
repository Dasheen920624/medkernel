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
        contractWithOpenApiPaths("clinical-event", "临床事件服务",
            "com.medkernel.engine.context.ClinicalEventController", "/api/v1/engine/events",
            openApiPaths("/api/v1/engine/events/**", "/api/v1/engine/clinical-events/**"),
            permissions("event.write", "event.read"),
            audits(
                audit(AuditAction.CREATE, "clinical_event", "接收临床事件"),
                audit(AuditAction.EXECUTE, "clinical_event", "异步处理、重放、死信回放和客户回调出站登记临床事件"))),
        contract("clinical-event-async-suffix", "临床事件异步受理 suffix 服务",
            "com.medkernel.engine.context.ClinicalEventAsyncSuffixController",
            "/api/v1/engine/clinical-events:async",
            permissions("event.write"),
            audits(audit(AuditAction.CREATE, "clinical_event", "客户面 suffix 异步接收临床事件"))),
        contract("clinical-event-batch-suffix", "临床事件批量受理 suffix 服务",
            "com.medkernel.engine.context.ClinicalEventBatchSuffixController",
            "/api/v1/engine/clinical-events:batch",
            permissions("event.write"),
            audits(audit(AuditAction.CREATE, "clinical_event", "客户面 suffix 批量接收临床事件"))),
        contract("clinical-event-replay-suffix", "临床事件回放 suffix 服务",
            "com.medkernel.engine.context.ClinicalEventReplaySuffixController",
            "/api/v1/engine/clinical-events:replay",
            permissions("event.write"),
            audits(audit(AuditAction.EXECUTE, "clinical_event", "客户面 suffix 回放临床事件"))),
        contract("clinical-safety", "临床安全撤回服务",
            "com.medkernel.engine.safety.SafetyWithdrawalController", "/api/v1/engine/safety",
            permissions("knowledge.read", "knowledge.withdraw"),
            audits(
                audit(AuditAction.PUBLISH, "safety_withdrawal", "发起安全撤回并重算影响集合"),
                audit(AuditAction.EXPORT, "safety_withdrawal", "导出安全撤回影响证据"),
                audit(AuditAction.CREATE, "mk_knowledge_affected_case_task", "生成受影响病例 / 路径复核任务"))),
        contract("clinical-redline", "临床安全红线目录服务",
            "com.medkernel.engine.safety.ClinicalRedlineController", "/api/v1/engine/safety",
            permissions("knowledge.read", "knowledge.write", "knowledge.publish"),
            audits(
                audit(AuditAction.EXECUTE, "mk_engine_clinical_redline_trial", "记录红线静默试运行证据"),
                audit(AuditAction.PUBLISH, "mk_engine_clinical_redline", "红线静默试运行达标后上线"))),
        contract("diagnosis-knowledge", "诊断知识维护服务",
            "com.medkernel.engine.knowledge.diagnosis.DiagnosisKnowledgeController",
            "/api/v1/engine/knowledge/diagnosis",
            permissions("knowledge.read", "knowledge.write", "knowledge.publish"),
            audits(audit(AuditAction.CREATE, "mk_diagnosis_criterion", "新增诊断标准"),
                audit(AuditAction.CREATE, "mk_diagnosis_differential", "新增鉴别清单"),
                audit(AuditAction.CREATE, "mk_diagnosis_care_pointer", "新增诊疗指针"),
                audit(AuditAction.CREATE, "mk_diagnosis_test_case", "新增诊断测试病例"))),
        contract("context-snapshot", "标准上下文快照服务",
            "com.medkernel.engine.context.ContextSnapshotController", "/api/v1/engine/context/snapshots",
            permissions("context.write", "context.read"),
            audits(audit(AuditAction.CREATE, "context_snapshot", "创建标准上下文快照"))),
        contract("context-field-catalog", "上下文字段目录服务",
            "com.medkernel.engine.context.ContextFieldCatalogController",
            "/api/v1/engine/context/field-catalog",
            permissions("context.read", "context.write"),
            audits(audit(AuditAction.CREATE, "mk_context_field_catalog", "维护租户自定义上下文字段"))),
        contract("cdss-risk-matrix", "CDSS 风险分级矩阵服务",
            "com.medkernel.engine.cdss.risk.CdssRiskMatrixController",
            "/api/v1/engine/cdss/risk-matrix",
            permissions("recommendation.read", "recommendation.write"),
            audits(audit(AuditAction.UPDATE, "mk_engine_cdss_risk_matrix", "更新 CDSS 风险分级矩阵"))),
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
        contract("evaluation-canonical", "评估质控 canonical 服务",
            "com.medkernel.engine.evaluation.EvaluationEngineCanonicalController",
            "/api/v1/engine/evaluation",
            permissions("evaluation.write", "evaluation.read", "evaluation.publish",
                "evaluation.execute", "evaluation.remediate", "evaluation.review"),
            audits(
                audit(AuditAction.CREATE, "evaluation_indicator", "创建评估指标 canonical 草稿"),
                audit(AuditAction.PUBLISH, "evaluation_indicator", "发布或激活 canonical 评估指标"),
                audit(AuditAction.EXECUTE, "evaluation_run", "执行 canonical 质控评估"),
                audit(AuditAction.REVIEW, "quality_finding", "canonical 整改复核"))),
        contract("evaluation-evaluate-suffix", "评估质控执行 suffix 服务",
            "com.medkernel.engine.evaluation.EvaluationEngineEvaluateSuffixController",
            "/api/v1/engine/evaluation:evaluate",
            permissions("evaluation.execute"),
            audits(audit(AuditAction.EXECUTE, "evaluation_run", "客户面 suffix 执行质控评估"))),
        contract("rectification", "整改闭环服务包",
            "com.medkernel.engine.evaluation.RectificationController",
            "/api/v1/engine/rectifications",
            permissions("evaluation.read", "evaluation.remediate", "evaluation.review"),
            audits(
                audit(AuditAction.CREATE, "rectification_task", "派发质控整改任务"),
                audit(AuditAction.UPDATE, "rectification_task", "提交整改说明和证据"),
                audit(AuditAction.REVIEW, "quality_finding", "复核或豁免整改任务"))),
        contract("value-metrics", "价值指标与 ROI 服务",
            "com.medkernel.engine.quality.value.ValueMetricsController",
            "/api/v1/engine/value-metrics",
            permissions("evaluation.read"),
            List.of()),
        contract("quality-dashboard", "质控驾驶舱服务",
            "com.medkernel.engine.quality.dashboard.QualityDashboardController",
            "/api/v1/engine/quality",
            permissions("evaluation.read"),
            audits(
                audit(AuditAction.CREATE, "mk_quality_dashboard_alert", "生成质控驾驶舱预警 read-model"),
                audit(AuditAction.UPDATE, "mk_quality_dashboard_alert", "刷新或关闭质控驾驶舱预警"))),
        contract("insurance-quality", "病案医保服务包",
            "com.medkernel.engine.quality.insurance.InsuranceQualityController",
            "/api/v1/engine/quality",
            permissions("evaluation.execute"),
            audits(
                audit(AuditAction.CREATE, "mk_quality_case_review", "生成病案内涵质控结果"),
                audit(AuditAction.CREATE, "mk_quality_drg_grouping", "生成 DRG/DIP 入组核对结果"),
                audit(AuditAction.CREATE, "mk_quality_insurance_issue", "生成医保病案问题并联动整改"))),
        contract("emr-level", "电子病历评级目标服务",
            "com.medkernel.engine.emrlevel.EmrLevelController",
            "/api/v1/engine/emr-level",
            permissions("evaluation.read", "evaluation.write"),
            audits(
                audit(AuditAction.CREATE, "mk_emr_level_target", "保存电子病历评级目标"),
                audit(AuditAction.CREATE, "mk_emr_level_gap", "生成评级差距并联动整改任务"))),
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
        contract("workflow-todo", "临床协同待办服务",
            "com.medkernel.engine.workflow.WorkflowTodoController",
            "/api/v1/engine/workflow/todos",
            permissions("workflow.read", "workflow.write"),
            audits(
                audit(AuditAction.UPDATE, "mk_engine_workflow_todo", "完成或转交临床协同待办"))),
        contract("workflow-notification", "临床通知中心服务",
            "com.medkernel.engine.workflow.WorkflowNotificationController",
            "/api/v1/engine/notifications",
            permissions("notification.read", "notification.write"),
            audits(
                audit(AuditAction.UPDATE, "mk_engine_notification", "标记通知已读和保存通知偏好"))),
        contract("integration", "第三方集成服务",
            "com.medkernel.engine.integration.controller.IntegrationController", "/api/v1/engine/integration",
            permissions("integration.read", "integration.write", "integration.execute"),
            audits(
                audit(AuditAction.CREATE, "integration_adapter", "创建适配器和 Webhook"),
                audit(AuditAction.UPDATE, "integration_adapter", "更新适配器和 Webhook"),
                audit(AuditAction.EXECUTE, "integration_adapter", "健康检查、Webhook 测试 / 入站验签 / 出站补偿 / 死信重放"),
                audit(AuditAction.CREATE, "mk_integration_onboarding", "创建第三方业务接口接入申请"),
                audit(AuditAction.UPDATE, "mk_integration_onboarding", "推进第三方业务接口接入阶段"),
                audit(AuditAction.CREATE, "mk_integration_regional_source", "登记区域协同来源可信分级"),
                audit(AuditAction.EXECUTE, "mk_integration_data_quality_report", "生成数据质量报告快照"))),
        contract("fhir-facade", "FHIR R4/R5 运行门面服务",
            "com.medkernel.engine.integration.fhir.FhirFacadeController", "/api/v1/engine/integration/fhir",
            permissions("integration.read", "integration.execute"),
            audits(
                audit(AuditAction.CREATE, "mk_fhir_resource_mapping", "FHIR Observation create 映射为标准临床资源"),
                audit(AuditAction.CREATE, "clinical_event", "FHIR create 回流临床事件入口"),
                audit(AuditAction.CREATE, "sys_task", "高风险 FHIR 写入登记医师确认任务"))),
        contract("knowledge-export", "知识导出服务",
            "com.medkernel.engine.knowledge.KnowledgeExportController", "/api/v1/engine/knowledge/exports",
            permissions("knowledge.export"),
            audits(audit(AuditAction.EXPORT, "knowledge_export", "创建、取消和下载知识导出任务"))),
        contract("knowledge-identity", "知识身份与来源服务",
            "com.medkernel.engine.knowledge.KnowledgeIdentityController", "/api/v1/engine/knowledge",
            permissions("knowledge.read", "knowledge.write"),
            audits(audit(AuditAction.CREATE, "knowledge_source", "登记知识来源和片段"))),
        contract("knowledge-version", "知识版本服务",
            "com.medkernel.engine.knowledge.KnowledgeVersionController", "/api/v1/engine/knowledge",
            permissions("knowledge.read", "knowledge.publish", "knowledge.withdraw", "knowledge.write", "knowledge.review"),
            audits(
                audit(AuditAction.CREATE, "knowledge_candidate", "创建知识版本候选并进入新旧识别"),
                audit(AuditAction.REVIEW, "knowledge_candidate", "提交版本审核或处理候选知识"),
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
        contractWithOpenApiPaths("mpi", "患者主索引服务",
            "com.medkernel.engine.mpi.MpiController", "/api/v1/engine/mpi",
            openApiPaths("/api/v1/engine/mpi/**", "/api/v1/clinical/mpi/**"),
            permissions("mpi.read", "mpi.write"),
            audits(
                audit(AuditAction.CREATE, "mpi_patient", "创建患者主索引"),
                audit(AuditAction.UPDATE, "mpi_patient", "合并患者主索引"),
                audit(AuditAction.UPDATE, "mpi_patient", "拆分患者主索引合并关系"),
                audit(AuditAction.REVIEW, "mk_mpi_merge_review", "确认高危 MPI 合并审核单"))),
        contractWithOpenApiPaths("org-unit", "组织单元服务",
            "com.medkernel.engine.org.OrgUnitController", "/api/v1/engine/org/org-units",
            openApiPaths("/api/v1/engine/org/org-units/**", "/api/v1/tenant/org-units/**"),
            permissions("org.read", "org.write"),
            audits(audit(AuditAction.UPDATE, "org_unit", "创建和调整组织单元"))),
        contract("pathway", "路径引擎服务",
            "com.medkernel.engine.pathway.PathwayEngineController", "/api/v1/engine/pathway",
            permissions("pathway.write", "pathway.read", "pathway.publish"),
            audits(
                audit(AuditAction.CREATE, "pathway_template", "创建路径包、模板和患者路径"),
                audit(AuditAction.PUBLISH, "pathway_template", "发布路径模板"),
                audit(AuditAction.EXECUTE, "patient_pathway", "推进患者路径"))),
        contract("package", "配置包服务",
            "com.medkernel.engine.pkg.PackageEngineController", "/api/v1/engine/pkg/packages",
            permissions("package.publish", "package.read", "package.rollback"),
            audits(
                audit(AuditAction.CREATE, "knowledge_package", "创建配置包和包条目"),
                audit(AuditAction.EXPORT, "knowledge_package", "导出差异和离线包"),
                audit(AuditAction.IMPORT, "knowledge_package", "导入离线包"),
                audit(AuditAction.PUBLISH, "knowledge_package", "同步发布配置包"),
                audit(AuditAction.ROLLBACK, "knowledge_package", "回滚配置包"))),
        contract("projection", "关系库权威投影服务",
            "com.medkernel.engine.projection.ProjectionController", "/api/v1/projections",
            permissions("projection.read", "projection.rebuild"),
            audits(audit(AuditAction.EXECUTE, "mk_projection_sync", "从关系库权威源重建临床图、知识图与搜索投影"))),
        contract("recommendation", "推荐提醒服务",
            "com.medkernel.engine.recommendation.RecommendationEngineController", "/api/v1/engine/recommendations",
            permissions("recommendation.write", "recommendation.read", "recommendation.accept"),
            audits(
                audit(AuditAction.EXECUTE, "recommendation_trigger", "创建或评估推荐触发"),
                audit(AuditAction.FEEDBACK, "recommendation_card", "记录提醒反馈"))),
        contract("diagnosis-assist", "运行时鉴别诊断服务",
            "com.medkernel.engine.knowledge.diagnosis.runtime.DiagnosisAssistController",
            "/api/v1/engine/recommendations",
            permissions("recommendation.write"),
            audits(audit(AuditAction.EXECUTE, "recommendation_trigger", "鉴别诊断候选经触发落库为推荐卡"))),
        contract("recommendation-evaluate-suffix", "推荐评估 suffix 服务",
            "com.medkernel.engine.recommendation.RecommendationEvaluateSuffixController",
            "/api/v1/engine/recommendations:evaluate",
            permissions("recommendation.write"),
            audits(audit(AuditAction.EXECUTE, "recommendation_trigger", "客户面评估推荐触发"))),
        contract("rule", "规则引擎服务",
            "com.medkernel.engine.rule.RuleEngineController", "/api/v1/engine/rule",
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
                audit(AuditAction.PERMISSION_CHANGE, "platform_credential", "自助修改密码"),
                audit(AuditAction.PERMISSION_CHANGE, "sys_password_reset_token", "消费一次性密码重置 token")),
            publicEndpoints(
                "POST /api/v1/auth/login",
                "GET /api/v1/auth/login-tenants",
                "GET /api/v1/auth/delegated/status",
                "POST /api/v1/auth/delegated/callback",
                "POST /api/v1/auth/logout",
                "POST /api/v1/auth/password-reset")),
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
        contract("tenant-engine", "租户开通与实施服务包",
            "com.medkernel.engine.tenant.TenantEngineController", "/api/v1/engine/tenant",
            permissions("tenant.read", "tenant.write"),
            audits(
                audit(AuditAction.UPDATE, "tenant_branding", "更新租户品牌配置"),
                audit(AuditAction.UPDATE, "tenant_lifecycle", "推进租户生命周期或执行开通门禁"))),
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

    private static ServiceContract contractWithOpenApiPaths(String id,
                                                            String title,
                                                            String controllerClassName,
                                                            String basePath,
                                                            List<String> openApiPaths,
                                                            List<ServicePermissionDeclaration> permissions,
                                                            List<ServiceAuditDeclaration> auditPoints) {
        return new ServiceContract(id, title, controllerClassName, basePath,
            openApiPaths, permissions, auditPoints, List.of());
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

    private static List<String> openApiPaths(String... paths) {
        return List.of(paths);
    }

    private static ServiceAuditDeclaration audit(AuditAction action, String targetType, String purpose) {
        return new ServiceAuditDeclaration(action, targetType, purpose);
    }

    private static List<String> publicEndpoints(String... endpoints) {
        return List.of(endpoints);
    }
}
