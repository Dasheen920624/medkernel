package com.medkernel.engine.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * MedKernel v1.0 GA · 统一权限编码枚举。
 *
 * <p>命名约定：{@code <域>.<动作>}，小写点号分隔。
 * 域大致对应一级业务模块（org/tenant/release/rule/pathway/knowledge/recommendation/evaluation/followup/audit/system 等）。
 *
 * <p>项目上线前权限目录保持单一有效集合，不保留已淘汰权限别名。
 *
 * <p>风险等级用于产品宪法第 6 条的 6 态体验和"高风险逐条确认"门禁；
 * 高风险动作不允许批量；中风险要二次确认；低风险可批量。
 */
public enum PermissionCode {

    // ─── 组织（GA-ENG-BASE-01）────────────────────────────────────
    ORG_READ("org.read", Risk.LOW, "查看组织树"),
    ORG_WRITE("org.write", Risk.MEDIUM, "新增 / 修改组织单元"),
    ORG_PUBLISH("org.publish", Risk.HIGH, "激活 / 暂停 / 归档组织单元"),

    // ─── 服务机构与发布治理（GA-ENG-API-10）────────────────────────────
    TENANT_READ("tenant.read", Risk.LOW, "查看服务机构与生命周期"),
    TENANT_WRITE("tenant.write", Risk.HIGH, "开通 / 关闭服务机构"),
    RELEASE_READ("release.read", Risk.LOW, "查看平台标准版本与机构生效版本"),
    RELEASE_PUBLISH("release.publish", Risk.HIGH, "发布平台标准版本或激活机构生效版本"),
    RELEASE_ROLLBACK("release.rollback", Risk.HIGH, "从历史机构生效版本生成回退版本"),

    // ─── 通用配置资产 ────────────────────────────────────────────
    ASSET_READ("asset.read", Risk.LOW, "查看值集、计算公式、医嘱套餐与临床提示卡"),
    ASSET_WRITE("asset.write", Risk.MEDIUM, "新增 / 修改值集、计算公式、医嘱套餐与临床提示卡草稿"),

    // ─── 知识资产（GA-ENG-API-03 / GA-ENG-KNOW-01/02）─────────
    KNOWLEDGE_READ("knowledge.read", Risk.LOW, "查看知识资产 / 来源 / 候选"),
    KNOWLEDGE_WRITE("knowledge.write", Risk.MEDIUM, "新增 / 修改知识候选草稿与来源登记"),
    KNOWLEDGE_REVIEW("knowledge.review", Risk.MEDIUM, "审核 AI 候选知识"),
    KNOWLEDGE_PUBLISH("knowledge.publish", Risk.HIGH, "激活新版知识并失效旧版"),
    KNOWLEDGE_WITHDRAW("knowledge.withdraw", Risk.HIGH, "紧急撤回已发布知识版本"),
    KNOWLEDGE_EXPORT("knowledge.export", Risk.MEDIUM, "异步导出知识资产 / 引用 / 历史"),

    // ─── 字典（GA-ENG-TERM-01）─────────────────────────────────
    TERM_READ("term.read", Risk.LOW, "查看标准字典 / 院内映射"),
    TERM_WRITE("term.write", Risk.MEDIUM, "修改字典 / 映射"),
    TERM_PUBLISH("term.publish", Risk.HIGH, "发布字典映射版本"),

    // ─── 规则（GA-ENG-RULE-01）─────────────────────────────────
    RULE_READ("rule.read", Risk.LOW, "查看规则"),
    RULE_WRITE("rule.write", Risk.MEDIUM, "新增 / 修改规则草稿"),
    RULE_PUBLISH("rule.publish", Risk.HIGH, "灰度 / 全量发布规则"),
    RULE_OVERRIDE("rule.override", Risk.HIGH, "记录阻断或强提醒的人工越权理由"),

    // ─── 路径（GA-ENG-PATH-01）─────────────────────────────────
    PATHWAY_READ("pathway.read", Risk.LOW, "查看路径模板 / 患者路径"),
    PATHWAY_WRITE("pathway.write", Risk.MEDIUM, "编辑路径模板"),
    PATHWAY_EXECUTE("pathway.execute", Risk.MEDIUM, "患者入径与路径节点推进"),
    PATHWAY_PUBLISH("pathway.publish", Risk.HIGH, "发布路径模板"),

    // ─── CDSS / 推荐（GA-ENG-CDSS-01）──────────────────────────
    RECOMMENDATION_READ("recommendation.read", Risk.LOW, "查看推荐 / 提醒"),
    RECOMMENDATION_ACCEPT("recommendation.accept", Risk.MEDIUM, "采纳或拒绝推荐（医师权限）"),

    // ─── 评估质控（GA-ENG-EVAL-01）─────────────────────────────
    EVALUATION_READ("evaluation.read", Risk.LOW, "查看评估指标和结果"),
    EVALUATION_WRITE("evaluation.write", Risk.MEDIUM, "修改评估指标"),
    EVALUATION_PUBLISH("evaluation.publish", Risk.HIGH, "发布质控指标"),

    // ─── 审计与证据（GA-ENG-EVID-01）──────────────────────────
    AUDIT_READ("audit.read", Risk.LOW, "查看审计日志"),
    AUDIT_EXPORT("audit.export", Risk.MEDIUM, "导出审计快照 / 证据导出"),

    // ─── 标准上下文（GA-ENG-API-01）────────────────────────────
    CONTEXT_READ("context.read", Risk.LOW, "查看标准上下文 snapshot"),
    CONTEXT_WRITE("context.write", Risk.MEDIUM, "创建标准上下文 snapshot"),

    // ─── 临床事件（GA-ENG-API-02）──────────────────────────────
    EVENT_READ("event.read", Risk.LOW, "查看临床事件"),
    EVENT_WRITE("event.write", Risk.MEDIUM, "创建 / 重放临床事件"),

    // ─── 系统运维（GA-ENG-BASE-07）─────────────────────────────
    SYSTEM_READ("system.read", Risk.LOW, "查看系统状态 / 外部连接"),
    SYSTEM_MANAGE("system.manage", Risk.HIGH, "运维操作（重启、密钥轮换、降级开关）"),
    PLATFORM_PUBLISH("platform.publish", Risk.HIGH, "发布 / 激活平台权威资产版本"),
    TENANT_OVERRIDE("tenant.override", Risk.HIGH, "发布服务机构资产覆盖"),

    // ─── 追加权限（保持已发布枚举顺序稳定）──────────────────────────
    RECOMMENDATION_WRITE("recommendation.write", Risk.MEDIUM, "创建推荐触发和候选提醒事实"),
    EVALUATION_EXECUTE("evaluation.execute", Risk.MEDIUM, "接收评估运行和结果事实"),
    EVALUATION_REMEDIATE("evaluation.remediate", Risk.MEDIUM, "提交质控问题整改证据"),
    EVALUATION_REVIEW("evaluation.review", Risk.HIGH, "复核质控整改并关闭问题"),
    FOLLOWUP_READ("followup.read", Risk.LOW, "查看随访计划与任务列表"),
    FOLLOWUP_WRITE("followup.write", Risk.MEDIUM, "智能生成随访计划、触发任务、提交问卷与回传异常事件"),
    FOLLOWUP_PUBLISH("followup.publish", Risk.HIGH, "发布随访模板版本"),
    EMBED_READ("embed.read", Risk.LOW, "验证和查看嵌入上下文"),
    EMBED_WRITE("embed.write", Risk.MEDIUM, "生成嵌入启动凭证和记录反馈"),
    SANDBOX_RUN("sandbox.run", Risk.MEDIUM, "运行全真体验沙盘场景编排"),
    SANDBOX_MANAGE("sandbox.manage", Risk.MEDIUM, "导入或撤销脱敏历史重放清单"),
    LLM_READ("llm.read", Risk.LOW, "查看模型能力状态和调用记录"),
    LLM_EXECUTE("llm.execute", Risk.MEDIUM, "提交和重试模型任务"),
    LLM_MANAGE("llm.manage", Risk.HIGH, "管理机构模型路由、脱敏和输出结构策略"),
    LLM_EGRESS_MANAGE("llm.egress.manage", Risk.HIGH, "管理模型外调允许范围与高敏用途确认"),
    LLM_PROVIDER_MANAGE("llm.provider.manage", Risk.HIGH, "配置模型服务接入（调用地址/加密凭据/启停）"),
    LLM_EVAL_MANAGE("llm.eval.manage", Risk.HIGH, "维护医学验证用例、运行评测并核查证据"),
    LLM_ENHANCEMENT_MANAGE("llm.enhancement.manage", Risk.HIGH, "维护全业务模型增强接入矩阵（业务点、能力码、基础规则路径、接入状态）"),
    ENGINE_DATA_READ("engine-data.read", Risk.LOW, "查询引擎数据服务层只读统计（规则/知识使用聚合，按数据分级与权限脱敏）"),
    ENGINE_DATA_EXPORT("engine-data.export", Risk.MEDIUM, "提交与下载引擎数据服务层异步导出（D2 去标识聚合，审批闸控、字段脱敏、小样本抑制）"),
    LIST_EXPORT("list.export", Risk.MEDIUM, "创建和下载大规模列表异步导出文件"),
    INTEGRATION_READ("integration.read", Risk.LOW, "查看第三方适配器、Webhook 和集成日志"),
    INTEGRATION_WRITE("integration.write", Risk.MEDIUM, "创建或修改第三方适配器与 Webhook"),
    INTEGRATION_EXECUTE("integration.execute", Risk.MEDIUM, "执行适配器健康检查、Webhook 验证、入站验签、出站补偿和死信重放"),
    MPI_READ("mpi.read", Risk.LOW, "查看患者主索引列表与统计"),
    MPI_CREATE("mpi.create", Risk.MEDIUM, "创建脱敏患者主索引"),
    MPI_WRITE("mpi.write", Risk.HIGH, "合并、拆分和确认患者主索引"),
    PROJECTION_READ("projection.read", Risk.LOW, "查看投影状态与一致性报告"),
    PROJECTION_REBUILD("projection.rebuild", Risk.HIGH, "从关系库权威源重建投影"),
    WORKBENCH_READINESS_VIEW("workbench:readiness:view", Risk.LOW, "查看验收自检页面"),
    WORKFLOW_READ("workflow.read", Risk.LOW, "查看临床协同待办"),
    WORKFLOW_WRITE("workflow.write", Risk.MEDIUM, "完成或转交临床协同待办"),
    NOTIFICATION_READ("notification.read", Risk.LOW, "查看通知中心"),
    NOTIFICATION_WRITE("notification.write", Risk.LOW, "标记通知已读和保存通知偏好"),

    // ─── 菜单维度 ────────────────────────────────────────────────
    MENU_WORKBENCH("menu.workbench", PermissionDimension.MENU, Risk.LOW, "查看工作台入口"),

    // ─── 数据维度（BASE-01 orgPath 上的组织范围基线）──────────────────────
    DATA_DEPARTMENT("data.department", PermissionDimension.DATA, Risk.LOW, "访问本科室数据"),
    DATA_HOSPITAL("data.hospital", PermissionDimension.DATA, Risk.MEDIUM, "访问全院数据"),
    DATA_GROUP("data.group", PermissionDimension.DATA, Risk.HIGH, "访问集团跨院数据"),
    DATA_DESENSITIZED("data.desensitized", PermissionDimension.DATA, Risk.LOW, "访问脱敏数据"),

    // ─── 资产维度（知识、字典、规则、路径和机构生效版本的授权边界）──────────────────
    ASSET_RUNTIME_RELEASE("asset.runtime-release", PermissionDimension.ASSET, Risk.MEDIUM, "访问机构生效版本明细"),
    ASSET_DICTIONARY("asset.dictionary", PermissionDimension.ASSET, Risk.MEDIUM, "访问字典映射资产"),
    ASSET_KNOWLEDGE("asset.knowledge", PermissionDimension.ASSET, Risk.MEDIUM, "访问知识资产"),
    ASSET_RULE("asset.rule", PermissionDimension.ASSET, Risk.MEDIUM, "访问规则资产"),
    ASSET_PATHWAY("asset.pathway", PermissionDimension.ASSET, Risk.MEDIUM, "访问路径资产"),

    // ─── 环境维度（正式应急的细粒度回收在 BASE-02 PR3 承接）───────────────
    ENV_TEST("env.test", PermissionDimension.ENVIRONMENT, Risk.LOW, "访问测试环境"),
    ENV_TRIAL("env.trial", PermissionDimension.ENVIRONMENT, Risk.MEDIUM, "访问试运行环境"),
    ENV_PRODUCTION("env.production", PermissionDimension.ENVIRONMENT, Risk.HIGH, "访问正式环境"),
    ENV_EMERGENCY("env.emergency", PermissionDimension.ENVIRONMENT, Risk.HIGH, "访问应急环境"),

    // ─── INFRA-05 入口维度：32 主导航 + 1 页头 + 1 个人入口 ─────────
    MENU_IMPLEMENTATION_GUIDE("menu.implementation-guide", PermissionDimension.MENU, Risk.LOW, "查看实施与验收"),
    MENU_TENANT_ONBOARDING("menu.tenant-onboarding", PermissionDimension.MENU, Risk.LOW, "查看服务机构"),
    MENU_RUNTIME_RELEASES("menu.runtime-releases", PermissionDimension.MENU, Risk.LOW, "查看发布治理"),
    MENU_PATHWAY_TEMPLATES("menu.pathway-templates", PermissionDimension.MENU, Risk.LOW, "查看路径配置"),
    MENU_RULE_DEFINITIONS("menu.rule-definitions", PermissionDimension.MENU, Risk.LOW, "查看规则配置"),
    MENU_TERMINOLOGY_MAPPING("menu.terminology-mapping", PermissionDimension.MENU, Risk.LOW, "查看术语与字典"),
    MENU_ADAPTER_HUB("menu.adapter-hub", PermissionDimension.MENU, Risk.LOW, "查看系统接入"),
    MENU_MPI("menu.mpi", PermissionDimension.MENU, Risk.LOW, "查看患者索引"),
    MENU_PATIENT_PATHWAYS("menu.patient-pathways", PermissionDimension.MENU, Risk.LOW, "查看患者路径"),
    MENU_CDSS_FATIGUE("menu.cdss-fatigue", PermissionDimension.MENU, Risk.LOW, "查看提醒与推荐"),
    MENU_WORKFLOW_TODOS("menu.workflow-todos", PermissionDimension.MENU, Risk.LOW, "查看协同任务"),
    MENU_NOTIFICATIONS("menu.notifications", PermissionDimension.MENU, Risk.LOW, "查看消息通知"),
    MENU_CLINICAL_FOLLOWUP("menu.clinical-followup", PermissionDimension.MENU, Risk.LOW, "查看随访协同"),
    MENU_SANDBOX("menu.sandbox", PermissionDimension.MENU, Risk.LOW, "查看全真体验沙盘"),
    MENU_QC_DASHBOARD("menu.qc-dashboard", PermissionDimension.MENU, Risk.LOW, "查看质量与运营概览"),
    MENU_QC_ALERTS("menu.qc-alerts", PermissionDimension.MENU, Risk.LOW, "查看质量问题与整改"),
    MENU_INSURANCE_AUDIT("menu.insurance-audit", PermissionDimension.MENU, Risk.LOW, "查看医保审核"),
    MENU_QC_EVAL_SETS("menu.qc-eval-sets", PermissionDimension.MENU, Risk.LOW, "查看评价指标"),
    MENU_KNOWLEDGE_GOVERNANCE(
        "menu.knowledge-governance", PermissionDimension.MENU, Risk.LOW, "查看知识审核与发布"),
    MENU_INSTITUTION_KNOWLEDGE(
        "menu.institution-knowledge", PermissionDimension.MENU, Risk.LOW, "查看机构知识"),
    MENU_DIAGNOSIS_KNOWLEDGE(
        "menu.diagnosis-knowledge", PermissionDimension.MENU, Risk.LOW, "查看诊断知识维护"),
    MENU_KNOWLEDGE_PRODUCTION(
        "menu.knowledge-production", PermissionDimension.MENU, Risk.LOW, "查看知识生产"),
    MENU_ADMIN_USERS("menu.admin-users", PermissionDimension.MENU, Risk.LOW, "查看人员与账号"),
    MENU_IDENTITY_BINDINGS("menu.identity-bindings", PermissionDimension.MENU, Risk.LOW, "查看身份来源"),
    MENU_ADMIN_AUDIT("menu.admin-audit", PermissionDimension.MENU, Risk.LOW, "查看审计与证据"),
    MENU_SECURITY_BASELINE("menu.security-baseline", PermissionDimension.MENU, Risk.LOW, "查看安全与配置"),
    MENU_SYSTEM_PROVIDERS("menu.system-providers", PermissionDimension.MENU, Risk.LOW, "查看运行保障"),
    MENU_NOTIFICATION_SETTINGS("menu.notification-settings", PermissionDimension.MENU, Risk.LOW, "查看通知偏好"),
    MENU_PROVENANCE("menu.provenance", PermissionDimension.MENU, Risk.LOW, "查看来源与血缘"),
    MENU_GRAPH_EXPLORE("menu.graph-explore", PermissionDimension.MENU, Risk.LOW, "查看知识关系"),
    MENU_AI_WORKFLOWS("menu.ai-workflows", PermissionDimension.MENU, Risk.LOW, "查看模型能力"),
    MENU_DOMESTIC_CHECK("menu.domestic-check", PermissionDimension.MENU, Risk.LOW, "查看国产化核验"),
    MENU_DEV_CONSOLE("menu.dev-console", PermissionDimension.MENU, Risk.LOW, "查看诊断工具");

    private final String code;
    private final PermissionDimension dimension;
    private final String target;
    private final Risk risk;
    private final String displayName;

    PermissionCode(String code, Risk risk, String displayName) {
        this(code, PermissionDimension.ACTION, risk, displayName);
    }

    PermissionCode(String code, PermissionDimension dimension, Risk risk, String displayName) {
        this.code = code;
        this.dimension = dimension;
        this.target = targetFrom(code, dimension);
        this.risk = risk;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    /** 权限所属五维之一。 */
    public PermissionDimension dimension() {
        return dimension;
    }

    /** 权限目标标识，通常是编码点号后的业务对象。 */
    public String target() {
        return target;
    }

    public Risk risk() {
        return risk;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<PermissionCode> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim();
        return Arrays.stream(values())
            .filter(p -> p.code.equalsIgnoreCase(normalized))
            .findFirst();
    }

    private static String targetFrom(String code, PermissionDimension dimension) {
        int dotSeparator = code.indexOf('.');
        int colonSeparator = code.indexOf(':');
        int separator = dotSeparator >= 0 ? dotSeparator : colonSeparator;
        if (separator < 0) {
            return code;
        }
        if (dimension == PermissionDimension.ACTION) {
            return code.substring(0, separator);
        }
        return code.substring(separator + 1);
    }

    /** 风险级别。配合产品宪法第 6 条 / §10.2 体验门禁使用。 */
    public enum Risk {
        /** 低风险 — 可批量，无需二次确认 */
        LOW,
        /** 中风险 — 单条二次确认 */
        MEDIUM,
        /** 高风险 — 强提醒 + 灰度 + 留证 */
        HIGH
    }
}
