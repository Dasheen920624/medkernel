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
        contract("compliance-data-permission", "数据权限与列级检查服务",
            "com.medkernel.compliance.datapermission.DataPermissionController",
            "/api/v1/compliance/data-permissions",
            permissions("audit.read", "system.manage"),
            audits(audit(AuditAction.PERMISSION_CHANGE, "mk_compliance_data_permission", "维护行列数据权限策略"))),
        contract("compliance-data-permission-check", "数据权限决策检查服务",
            "com.medkernel.compliance.datapermission.DataPermissionCheckController",
            "/api/v1/compliance",
            permissions("context.read", "audit.read", "audit.export"),
            audits(audit(AuditAction.EXECUTE, "mk_compliance_data_permission", "执行数据权限决策检查"))),
        contract("compliance-masking-rule", "脱敏规则服务",
            "com.medkernel.compliance.masking.MaskingRuleController",
            "/api/v1/compliance/masking-rules",
            permissions("audit.read", "system.manage"),
            audits(audit(AuditAction.PERMISSION_CHANGE, "mk_compliance_masking_rule", "维护脱敏规则"))),
        contract("compliance-masking-preview", "脱敏预览服务",
            "com.medkernel.compliance.masking.MaskingPreviewController",
            "/api/v1/compliance",
            permissions("audit.read"),
            audits(audit(AuditAction.EXECUTE, "mk_compliance_masking_rule", "执行脱敏预览"))),
        contract("compliance-export-confirmation", "敏感数据导出确认服务",
            "com.medkernel.compliance.exportconfirmation.ExportConfirmationController",
            "/api/v1/compliance",
            permissions("list.export"),
            audits(
                audit(AuditAction.CREATE, "mk_compliance_export_confirmation", "确认敏感数据导出范围"),
                audit(AuditAction.EXPORT, "mk_compliance_export_confirmation", "登记敏感数据真实导出完成"))),
        contract("compliance-interop-assessment", "互联互通测评映射服务",
            "com.medkernel.compliance.interopassessment.InteropAssessmentController",
            "/api/v1/compliance/interop-assessment",
            permissions("audit.read"),
            List.of()),
        contract("compliance-personnel", "人员主数据与批量导入服务",
            "com.medkernel.compliance.personnel.PersonnelController",
            "/api/v1/compliance/personnel",
            permissions("org.read", "org.write"),
            audits(
                audit(AuditAction.CREATE, "mk_identity_person", "创建人员、任职、平台账号和身份来源关联"),
                audit(AuditAction.CREATE, "mk_identity_person_import_job", "预检并提交人员批量导入"))),
        contract("authoring-preview", "规则路径编排预览服务",
            "com.medkernel.engine.authoring.AuthoringPreviewController",
            "/api/v1/engine/authoring",
            permissions("rule.read", "pathway.read"),
            audits(
                audit(AuditAction.EXECUTE, "authoring_preview", "生成规则和路径编排的自然语言预览"),
                audit(AuditAction.EXECUTE, "authoring_preview_run", "基于真实上下文快照试运行草稿规则和路径"))),
        contract("authoring-asset-library", "统一创作资产库服务",
            "com.medkernel.engine.authoring.AuthoringAssetLibraryController",
            "/api/v1/engine/authoring/assets",
            permissions("rule.read", "pathway.read", "rule.write", "pathway.write"),
            audits(
                audit(AuditAction.UPDATE, "mk_engine_authoring_asset_profile", "更新统一资产库分类与标签"),
                audit(AuditAction.UPDATE, "mk_engine_authoring_asset_favorite", "更新统一资产库个人收藏"))),
        contract("authoring-declarative-assets", "声明式配置资产服务",
            "com.medkernel.engine.authoring.DeclarativeAssetController",
            "/api/v1/engine/authoring/declarative-assets",
            permissions("asset.read", "asset.write"),
            audits(
                audit(AuditAction.CREATE, "mk_version_asset_version", "创建声明式配置资产草稿版本"),
                audit(AuditAction.UPDATE, "mk_version_asset_version", "更新声明式配置资产草稿正文"))),
        contract("followup-template", "随访模板配置资产服务",
            "com.medkernel.engine.followup.FollowupTemplateController",
            "/api/v1/engine/followup/templates",
            permissions("followup.read", "followup.write", "followup.publish"),
            audits(
                audit(AuditAction.CREATE, "mk_followup_template", "创建随访模板不可变草稿版本"),
                audit(AuditAction.PUBLISH, "mk_followup_template", "发布随访模板统一资产版本"))),
        contract("authoring-batch", "创作批量任务服务",
            "com.medkernel.engine.authoring.AuthoringBatchJobController",
            "/api/v1/engine/authoring/batch",
            permissions("rule.read", "pathway.read", "release.read", "rule.write", "rule.publish", "release.publish"),
            audits(
                audit(AuditAction.CREATE, "mk_engine_authoring_batch_job", "创建创作批量任务"),
                audit(AuditAction.EXECUTE, "mk_engine_authoring_batch_job", "执行规则生成、规则发布和机构生效版本批量任务"),
                audit(AuditAction.IMPORT, "mk_engine_authoring_batch_item", "记录运行资产批量离线导入逐项结果"),
                audit(AuditAction.EXPORT, "mk_engine_authoring_batch_item", "记录运行资产批量离线导出逐项结果"))),
        contract("interoperability-mapping", "标准互操作映射服务",
            "com.medkernel.engine.interop.InteroperabilityController",
            "/api/v1/engine/interoperability",
            permissions("rule.read", "rule.write", "pathway.read", "pathway.write"),
            audits(
                audit(AuditAction.EXPORT, "interop_mapping", "导出带内容指纹和溯源的规则或路径标准互操作映射"),
                audit(AuditAction.IMPORT, "interop_mapping", "从标准互操作映射或受控 CQL 回导规则/路径草稿"))),
        contract("clinical-event", "临床事件服务",
            "com.medkernel.engine.context.ClinicalEventController", "/api/v1/engine/clinical-events",
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
                audit(AuditAction.CREATE, "mk_diagnosis_test_case", "新增诊断验证病例"))),
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
            permissions("recommendation.read", "rule.write"),
            audits(audit(AuditAction.UPDATE, "mk_engine_cdss_risk_matrix", "更新 CDSS 风险分级矩阵"))),
        contract("embed", "嵌入启动服务",
            "com.medkernel.engine.embed.EmbedEngineController", "/api/v1/engine/embed",
            permissions("embed.write", "embed.read"),
            audits(
                audit(AuditAction.CREATE, "embed_launch_token", "生成嵌入启动凭证"),
                audit(AuditAction.EXECUTE, "embed_launch_token", "一次性兑换嵌入会话并读取凭证绑定建议"),
                audit(AuditAction.FEEDBACK, "embed_feedback", "记录嵌入反馈")),
            publicEndpoints(
                "POST /api/v1/engine/embed/launch",
                "POST /api/v1/engine/embed/recommendations",
                "POST /api/v1/engine/embed/feedback")),
        contract("evaluation", "评估质控服务",
            "com.medkernel.engine.evaluation.EvaluationEngineCanonicalController", "/api/v1/engine/evaluation",
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
        contract("rectification", "整改闭环服务",
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
            permissions("evaluation.read", "evaluation.review"),
            audits(
                audit(AuditAction.CREATE, "mk_quality_dashboard_alert", "生成质控驾驶舱预警 read-model"),
                audit(AuditAction.UPDATE, "mk_quality_dashboard_alert", "刷新、确认或关闭质控驾驶舱预警"))),
        contract("insurance-quality", "病案医保服务",
            "com.medkernel.engine.quality.insurance.InsuranceQualityController",
            "/api/v1/engine/quality",
            permissions("evaluation.read", "evaluation.execute"),
            audits(
                audit(AuditAction.CREATE, "mk_quality_case_review", "生成病案内涵质控结果"),
                audit(AuditAction.CREATE, "mk_quality_drg_grouping", "生成 DRG/DIP 入组核对结果"),
                audit(AuditAction.CREATE, "mk_quality_insurance_issue", "生成医保病案问题并联动整改"))),
        contract("emr-level", "电子病历评级目标服务",
            "com.medkernel.engine.emrlevel.EmrLevelController",
            "/api/v1/engine/emr-level",
            permissions("evaluation.read", "evaluation.write", "audit.export"),
            audits(
                audit(AuditAction.CREATE, "mk_emr_level_target", "保存电子病历评级目标"),
                audit(AuditAction.CREATE, "mk_emr_level_gap", "生成评级差距并联动整改任务"),
                audit(AuditAction.EXPORT, "mk_emr_level_evidence_export", "导出电子病历评级证据"))),
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
            permissions("notification.read", "notification.write", "system.read", "system.manage"),
            audits(
                audit(AuditAction.UPDATE, "mk_engine_notification", "标记通知已读和保存通知偏好"))),
        contract("integration", "第三方集成服务",
            "com.medkernel.engine.integration.controller.IntegrationController", "/api/v1/engine/integration",
            permissions("integration.read", "integration.write", "integration.execute"),
            audits(
                audit(AuditAction.CREATE, "integration_adapter", "创建适配器和 Webhook"),
                audit(AuditAction.UPDATE, "integration_adapter", "更新适配器和 Webhook"),
                audit(AuditAction.EXECUTE, "integration_adapter", "健康检查、Webhook 验证 / 入站验签 / 出站补偿 / 死信重放"),
                audit(AuditAction.CREATE, "mk_integration_onboarding", "创建第三方业务接口接入申请"),
                audit(AuditAction.UPDATE, "mk_integration_onboarding", "推进第三方业务接口接入阶段"),
                audit(AuditAction.CREATE, "mk_integration_regional_source", "登记区域协同来源可信分级"),
                audit(AuditAction.EXECUTE, "mk_integration_data_quality_report", "生成数据质量报告快照"))),
        contract("master-data-sync", "院内主数据同步与对账服务",
            "com.medkernel.engine.integration.masterdata.MasterDataSyncController",
            "/api/v1/engine/integration/master-data",
            permissions("integration.read"),
            audits(
                audit(AuditAction.EXECUTE, "mk_integration_master_data_sync_batch", "验签并原子同步院内组织、人员用户和本地字典")),
            publicEndpoints(
                "POST /api/v1/engine/integration/master-data/{webhookId}/sync")),
        contract("third-party-knowledge-runtime", "第三方知识运行时服务",
            "com.medkernel.engine.integration.runtime.ThirdPartyKnowledgeRuntimeController",
            "/api/v1/engine/integration/knowledge-runtime",
            permissions("asset.read", "context.write"),
            audits(
                audit(AuditAction.CREATE, "context_snapshot", "按平台标准字段目录写入第三方临床上下文"))),
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
        contract("knowledge-customization", "机构知识定制服务",
            "com.medkernel.engine.knowledge.KnowledgeCustomizationController",
            "/api/v1/engine/knowledge/customizations",
            permissions("knowledge.read", "knowledge.write", "knowledge.publish", "knowledge.withdraw",
                "tenant.override"),
            audits(
                audit(AuditAction.CREATE, "mk_knowledge_customization", "从平台知识创建机构定制草稿"),
                audit(AuditAction.PUBLISH, "mk_knowledge_customization", "发布机构知识定制并接管目标组织"),
                audit(AuditAction.EXECUTE, "mk_knowledge_customization", "停止机构定制并恢复平台标准"))),
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
        contract("knowledge-retirement", "知识弃用与后继迁移服务",
            "com.medkernel.engine.knowledge.KnowledgeRetirementController", "/api/v1/engine/knowledge",
            permissions("knowledge.publish"),
            audits(audit(AuditAction.UPDATE, "knowledge_identity", "安排弃用、后继迁移与到期退役"))),
        contract("knowledge-discovery", "可信来源探索服务",
            "com.medkernel.engine.knowledge.discovery.DiscoveryController", "/api/v1/engine/knowledge",
            permissions("knowledge.read", "knowledge.write"),
            audits(audit(AuditAction.EXECUTE, "mk_knowledge_discovery_run",
                "运行可信来源探索并记录检索时点存证"))),
        contract("knowledge-acquisition", "公域资料获取服务",
            "com.medkernel.engine.knowledge.acquisition.AcquisitionController",
            "/api/v1/engine/knowledge/acquisition",
            permissions("knowledge.read", "knowledge.write"),
            audits(
                audit(AuditAction.CREATE, "mk_knowledge_acquisition_source",
                    "登记停用的公域资料来源草稿"),
                audit(AuditAction.UPDATE, "mk_knowledge_acquisition_source",
                    "更新配置时停用来源，或停用来源与自动调度"),
                audit(AuditAction.UPDATE, "mk_knowledge_acquisition_source",
                    "许可和 robots 安全校验通过后显式启用公域资料来源"),
                audit(AuditAction.EXECUTE, "mk_knowledge_acquisition_run",
                    "运行手动或调度公域资料获取并记录 URL、许可、robots 策略、原文指纹和解析结果"))),
        contract("knowledge-doc-parse", "文档解析服务",
            "com.medkernel.engine.knowledge.parsing.DocumentParseController", "/api/v1/engine/knowledge",
            permissions("knowledge.read", "knowledge.write"),
            audits(
                audit(AuditAction.EXECUTE, "mk_doc_parse_job",
                    "解析或院内上传受控来源文档并物化带锚点片段"),
                audit(AuditAction.EXPORT, "mk_knowledge_material_object",
                    "读取受管资料库文档原件并复核指纹"))),
        contract("knowledge-production", "知识生产编排服务",
            "com.medkernel.engine.knowledge.production.KnowledgeProductionController",
            "/api/v1/engine/knowledge-production",
            permissions("knowledge.read", "knowledge.write", "knowledge.publish"),
            audits(
                audit(AuditAction.CREATE, "mk_knowledge_production_job", "建知识生产编排 job"),
                audit(AuditAction.UPDATE, "mk_knowledge_production_job", "job 生命周期流转（完成/中止）"),
                audit(AuditAction.EXECUTE, "mk_knowledge_production_job",
                    "提交候选并记双形态隔离血缘"),
                audit(AuditAction.EXECUTE, "mk_knowledge_production_candidate",
                    "记录候选生产血缘"),
                audit(AuditAction.REVIEW, "mk_publication_quality_record",
                    "记录候选进入机构生效版本前的质量结论"))),
        contract("knowledge-initialization", "生产知识初始化发行服务",
            "com.medkernel.engine.knowledge.production.initialization.KnowledgeInitializationController",
            "/api/v1/engine/knowledge-production/initialization",
            permissions("knowledge.read", "knowledge.review"),
            audits(
                audit(AuditAction.CREATE, "mk_knowledge_initialization_batch",
                    "创建固定候选集合和稳定摘要的初始化发行批次"),
                audit(AuditAction.REVIEW, "mk_knowledge_initialization_item",
                    "按 LOW 批审或同步 MEDIUM/HIGH 既有审核结果"))),
        contract("domain-facade", "X-DOMAIN 领域门面组合目录服务",
            "com.medkernel.engine.domainfacade.DomainFacadeController",
            "/api/v1/engine/domain-facades",
            permissions("knowledge.read"),
            List.of()),
        contract("large-list", "大规模列表服务",
            "com.medkernel.engine.list.LargeListController", "/api/v1/large-lists",
            permissions("audit.read", "list.export"),
            audits(audit(AuditAction.EXPORT, "large_list_export", "创建和下载大规模列表导出"))),
        contract("model-gateway", "模型能力网关服务",
            "com.medkernel.engine.llm.ModelGatewayController", "/api/v1/model-capabilities",
            permissions("llm.read", "llm.execute", "llm.manage", "system.manage"),
            audits(
                audit(AuditAction.EXECUTE, "model_capability_task", "提交和重试模型任务"),
                audit(AuditAction.UPDATE, "model_capability_policy", "校验和保存机构模型治理策略"),
                audit(AuditAction.UPDATE, "model_capability_definition", "维护平台模型能力目录"))),
        contract("model-versions", "提示词、工具和模型版本治理服务",
            "com.medkernel.engine.llm.ModelVersionGovernanceController", "/api/v1/model-versions",
            permissions("llm.read", "llm.manage"),
            audits(audit(AuditAction.UPDATE, "mk_llm_model_version_bundle",
                "发布、回滚和导出提示词、工具和模型版本组合"))),
        contract("model-egress", "模型外调治理服务",
            "com.medkernel.engine.llm.egress.ModelEgressController", "/api/v1/model-egress",
            permissions("llm.egress.manage", "knowledge.write"),
            audits(
                audit(AuditAction.UPDATE, "mk_llm_egress_whitelist", "维护模型外调允许范围"),
                audit(AuditAction.UPDATE, "mk_llm_egress_confirmation", "确认高敏外调用途"))),
        contract("data-minimization-policies", "数据最小化策略服务",
            "com.medkernel.engine.llm.egress.DataMinimizationPolicyController",
            "/api/v1/data-minimization/policies",
            permissions("llm.egress.manage", "knowledge.write"),
            audits(
                audit(AuditAction.UPDATE, "mk_llm_egress_whitelist", "维护数据最小化允许范围、脱敏规则和确认阈值"),
                audit(AuditAction.UPDATE, "mk_llm_egress_confirmation", "确认模型高敏外调用途"))),
        contract("model-providers", "模型服务接入治理服务",
            "com.medkernel.engine.llm.provider.ModelProviderController", "/api/v1/model-providers",
            permissions("llm.provider.manage", "llm.eval.manage"),
            audits(audit(AuditAction.UPDATE, "mk_llm_provider", "配置模型服务接入并执行健康探测"))),
        contract("model-evaluations", "模型医学验证评测服务",
            "com.medkernel.engine.llm.eval.ModelEvalController", "/api/v1/model-evaluations",
            permissions("llm.eval.manage"),
            audits(
                audit(AuditAction.CREATE, "mk_llm_regression_case", "新增或批量导入医学验证用例"),
                audit(AuditAction.UPDATE, "mk_llm_regression_case", "启用或停用医学验证用例"),
                audit(AuditAction.EXECUTE, "mk_llm_eval_run", "运行医学验证评测并固化逐例证据"),
                audit(AuditAction.UPDATE, "mk_llm_eval_run", "医疗引擎运营员核对逐例证据后记录评测结论"))),
        contract("ai-quality-evaluations", "AI 质量评测中心服务",
            "com.medkernel.engine.llm.eval.AiQualityEvalController", "/api/v1/ai-eval",
            permissions("llm.eval.manage"),
            audits(audit(AuditAction.EXECUTE, "mk_llm_eval_run",
                "运行 AI 质量评测并查询模型版本质量趋势"))),
        contract("model-enhancement-matrix", "全业务模型赋能覆盖矩阵服务",
            "com.medkernel.engine.llm.ModelEnhancementMatrixController", "/api/v1/model-enhancement-matrix",
            permissions("llm.read", "llm.enhancement.manage"),
            audits(audit(AuditAction.UPDATE, "mk_llm_enhancement_matrix", "维护模型赋能覆盖矩阵业务点"))),
        contract("engine-data", "引擎数据服务层只读统计与异步导出服务",
            "com.medkernel.engine.datasvc.EngineDataController", "/api/v1/engine-data",
            permissions("engine-data.read", "engine-data.export"),
            audits(
                audit(AuditAction.EXECUTE, "rule_execution_log", "查询规则使用统计（D2 去标识聚合）"),
                audit(AuditAction.EXECUTE, "recommendation_source", "查询知识使用统计（D2 去标识聚合）"),
                audit(AuditAction.EXECUTE, "recommendation_card", "查询临床信号统计（D2 去标识聚合）"),
                audit(AuditAction.EXECUTE, "rule_definition", "解释规则已发布资产元数据（D1）"),
                audit(AuditAction.EXECUTE, "knowledge_identity", "检查知识身份存在性/检索（D1）"),
                audit(AuditAction.EXECUTE, "embed_launch_token", "解释临床 launch 授权会话最小上下文（D4，患者引用脱敏）"),
                audit(AuditAction.EXECUTE, "engine_data_tool", "执行受控工具（CLI/MCP 共用入口）"),
                audit(AuditAction.EXPORT, "mk_engine_data_export_job", "提交与下载引擎数据异步导出（D2 去标识聚合 CSV，SYS-06 审批闸控 + 小样本抑制）"))),
        contract("mpi", "患者主索引服务",
            "com.medkernel.engine.mpi.MpiController", "/api/v1/engine/mpi",
            permissions("mpi.read", "mpi.create", "mpi.write"),
            audits(
                audit(AuditAction.CREATE, "mpi_patient", "创建患者主索引"),
                audit(AuditAction.UPDATE, "mpi_patient", "合并患者主索引"),
                audit(AuditAction.UPDATE, "mpi_patient", "拆分患者主索引合并关系"),
                audit(AuditAction.REVIEW, "mk_mpi_merge_review", "确认高危 MPI 合并审核单"))),
        contract("org-unit", "组织单元服务",
            "com.medkernel.engine.org.OrgUnitController", "/api/v1/engine/org/org-units",
            permissions("org.read", "org.write"),
            audits(audit(AuditAction.UPDATE, "org_unit", "创建和调整组织单元"))),
        contract("pathway", "路径引擎服务",
            "com.medkernel.engine.pathway.PathwayEngineController", "/api/v1/engine/pathway",
            permissions("pathway.write", "pathway.read", "pathway.execute", "pathway.publish"),
            audits(
                audit(AuditAction.CREATE, "pathway_template", "创建路径包、模板和患者路径"),
                audit(AuditAction.PUBLISH, "pathway_template", "发布路径模板"),
                audit(AuditAction.EXECUTE, "patient_pathway", "推进患者路径"))),
        contract("runtime-release", "平台标准版本与机构生效版本服务",
            "com.medkernel.engine.release.RuntimeReleaseController", "/api/v1/engine/releases",
            permissions("asset.read", "platform.publish", "tenant.override"),
            audits(
                audit(AuditAction.PUBLISH, "mk_platform_baseline_release", "发布不可变平台标准版本"),
                audit(AuditAction.PUBLISH, "mk_clinical_runtime_release", "激活医院完整机构生效版本"),
                audit(AuditAction.ROLLBACK, "mk_clinical_runtime_release", "从历史清单生成医院回滚修订"))),
        contract("release-governance", "发布影响评估与灰度治理服务",
            "com.medkernel.engine.versioning.ReleaseGovernanceController",
            "/api/v1/engine/versioning/releases",
            permissions("release.read", "release.publish", "release.rollback", "tenant.override"),
            audits(
                audit(AuditAction.EXECUTE, "mk_version_rollout_observation", "记录发布前影响评估与灰度观测"),
                audit(AuditAction.PUBLISH, "mk_version_release_plan", "启动灰度放量"),
                audit(AuditAction.CREATE, "mk_version_override_template", "创建覆盖模板并批量生效"),
                audit(AuditAction.ROLLBACK, "mk_version_override_operation", "撤销批量覆盖或回滚发布"))),
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
        contract("report-interpretation", "医技报告解读服务",
            "com.medkernel.engine.report.ReportInterpretationController",
            "/api/v1/engine/recommendations",
            permissions("recommendation.write"),
            audits(audit(AuditAction.EXECUTE, "recommendation_trigger", "医技报告解读经触发落库为推荐卡"))),
        contract("recommendation-evaluate-suffix", "推荐评估 suffix 服务",
            "com.medkernel.engine.recommendation.RecommendationEvaluateSuffixController",
            "/api/v1/engine/recommendations:evaluate",
            permissions("recommendation.write"),
            audits(audit(AuditAction.EXECUTE, "recommendation_trigger", "客户面评估推荐触发"))),
        contract("sandbox", "全真体验沙盘编排服务",
            "com.medkernel.engine.sandbox.SandboxScenarioController",
            "/api/v1/engine/sandbox",
            permissions("sandbox.run"),
            audits(audit(
                AuditAction.EXECUTE, "sandbox_scenario", "按当前机构生效版本编排真实引擎链路并记录复演轨迹"))),
        contract("sandbox-replay", "沙盘历史原样重放清单服务",
            "com.medkernel.engine.sandbox.replay.SandboxReplayController",
            "/api/v1/engine/sandbox/replay-cases",
            permissions("sandbox.run", "sandbox.manage"),
            audits(
                audit(AuditAction.IMPORT, "mk_sandbox_replay_case", "导入不可变脱敏现场重放清单"),
                audit(AuditAction.UPDATE, "mk_sandbox_replay_case", "撤销整案历史重放清单"))),
        contract("realtime-cds-hook", "实时 CDS Hook 评估服务",
            "com.medkernel.engine.cdshook.RealtimeCdsHookController",
            "/api/v1/engine/cds-hooks:evaluate",
            permissions("recommendation.accept"),
            audits(audit(AuditAction.EXECUTE, "recommendation_trigger", "开医嘱实时 CDS 求值"))),
        contract("rule", "规则引擎服务",
            "com.medkernel.engine.rule.RuleEngineController", "/api/v1/engine/rule",
            permissions("rule.write", "rule.read", "rule.publish", "rule.override"),
            audits(
                audit(AuditAction.CREATE, "rule_definition", "创建规则和验证用例"),
                audit(AuditAction.PUBLISH, "rule_definition", "发布规则版本"),
                audit(AuditAction.EXECUTE, "rule_execution", "执行规则"),
                audit(AuditAction.FEEDBACK, "rule_override_log", "记录规则动作人工越权"))),
        contract("security-me", "当前用户安全画像服务",
            "com.medkernel.engine.security.SecurityMeController", "/api/v1/security",
            List.of(),
            List.of()),
        contract("menu-permission", "固定职责菜单目录服务",
            "com.medkernel.engine.security.MenuPermissionController", "/api/v1/security/menu-permissions",
            permissions("org.read"),
            List.of()),
        contract("compliance-user", "统一用户管理服务",
            "com.medkernel.compliance.user.ComplianceUserController", "/api/v1/compliance/users",
            permissions("org.read", "org.write"),
            audits(
                audit(AuditAction.CREATE, "platform_credential", "创建租户用户"),
                audit(AuditAction.EXECUTE, "platform_credential", "重置密码和调整账号状态"),
                audit(AuditAction.CREATE, "user_role_assignment", "分配用户角色范围"),
                audit(AuditAction.DELETE, "user_role_assignment", "停用用户角色范围"))),
        contract("identity-binding", "外部身份绑定管理服务",
            "com.medkernel.compliance.identitybinding.IdentityBindingController",
            "/api/v1/compliance/identity-bindings",
            permissions("org.read", "org.write"),
            audits(audit(AuditAction.PERMISSION_CHANGE, "mk_compliance_identity_binding", "绑定和解绑外部身份"))),
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
        contract("tenant-provisioning", "服务机构开通服务",
            "com.medkernel.engine.security.auth.TenantProvisioningController", "/api/v1/admin/tenants",
            permissions("tenant.read", "tenant.write"),
            audits(audit(AuditAction.CREATE, "tenant", "开通和调整服务机构"))),
        contract("bootstrap", "首次部署引导服务",
            "com.medkernel.engine.security.bootstrap.BootstrapController", "/api/v1/bootstrap",
            List.of(),
            audits(
                audit(AuditAction.CREATE, "bootstrap_init_token", "校验首次部署凭证"),
                audit(AuditAction.PERMISSION_CHANGE, "platform_credential", "创建初始管理员和绑定多因素认证")),
            publicEndpoints(
                "GET /api/v1/bootstrap/status",
                "POST /api/v1/bootstrap/init-token",
                "POST /api/v1/bootstrap/password")),
        contract("tenant-engine", "服务机构开通与实施服务",
            "com.medkernel.engine.tenant.TenantEngineController", "/api/v1/engine/tenant",
            permissions("tenant.read", "tenant.write"),
            audits(
                audit(AuditAction.UPDATE, "tenant_branding", "更新服务机构品牌配置"),
                audit(AuditAction.UPDATE, "tenant_lifecycle", "推进服务机构生命周期"))),
        contract("terminology", "字典映射服务",
            "com.medkernel.engine.terminology.TerminologyController", "/api/v1/engine/terminology",
            permissions("term.read", "term.write"),
            audits(
                audit(AuditAction.CREATE, "mk_term_candidate_generation_job", "提交术语候选生成异步任务"),
                audit(AuditAction.CREATE, "mapping_candidate", "生成待审核术语映射候选"),
                audit(AuditAction.CREATE, "term_mapping", "确认字典映射"),
                audit(AuditAction.REVIEW, "mapping_conflict", "处置术语映射冲突"))),
        contract("developer-console", "诊断工具服务",
            "com.medkernel.engine.developer.DeveloperConsoleController", "/api/v1/system/dev-console",
            permissions("system.read"),
            List.of()),
        contract("plugin-security", "插件安全边界服务",
            "com.medkernel.engine.plugin.PluginSecurityController", "/api/v1/plugins",
            permissions("system.read", "system.manage"),
            audits(
                audit(AuditAction.CREATE, "mk_plugin_registry", "注册插件能力声明"),
                audit(AuditAction.PERMISSION_CHANGE, "mk_plugin_grant", "授权插件声明能力"),
                audit(AuditAction.UPDATE, "mk_plugin_registry", "禁用插件"))),
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
