package com.medkernel.engine.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * MedKernel v1.0 GA · 统一权限编码枚举。
 *
 * <p>命名约定：{@code <域>.<动作>}，小写点号分隔。
 * 域大致对应一级业务模块（org/tenant/rule/pathway/knowledge/recommendation/evaluation/followup/package/audit/system 等）。
 *
 * <p>新增权限只能在末尾追加，不得删除已发布权限（避免 DB 已绑定关系失效）；
 * 废弃权限标 {@code @Deprecated} 但保留枚举值。
 *
 * <p>风险等级用于产品宪法第 6 条的 6 态体验和"高风险逐条确认"门禁；
 * 高风险动作不允许批量；中风险要二次确认；低风险可批量。
 */
public enum PermissionCode {

    // ─── 组织（GA-ENG-BASE-01）────────────────────────────────────
    ORG_READ("org.read", Risk.LOW, "查看组织树"),
    ORG_WRITE("org.write", Risk.MEDIUM, "新增 / 修改组织单元"),
    ORG_PUBLISH("org.publish", Risk.HIGH, "激活 / 暂停 / 归档组织单元"),

    // ─── 租户与配置包（GA-ENG-API-10）────────────────────────────
    TENANT_READ("tenant.read", Risk.LOW, "查看租户与生命周期"),
    TENANT_WRITE("tenant.write", Risk.HIGH, "开通 / 关闭租户"),
    PACKAGE_READ("package.read", Risk.LOW, "查看配置包 / 知识包"),
    PACKAGE_PUBLISH("package.publish", Risk.HIGH, "灰度 / 全量发布配置包"),
    PACKAGE_ROLLBACK("package.rollback", Risk.HIGH, "回滚配置包"),

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
    TERM_PUBLISH("term.publish", Risk.HIGH, "发布字典映射包"),

    // ─── 规则（GA-ENG-RULE-01）─────────────────────────────────
    RULE_READ("rule.read", Risk.LOW, "查看规则"),
    RULE_WRITE("rule.write", Risk.MEDIUM, "新增 / 修改规则草稿"),
    RULE_PUBLISH("rule.publish", Risk.HIGH, "灰度 / 全量发布规则"),

    // ─── 路径（GA-ENG-PATH-01）─────────────────────────────────
    PATHWAY_READ("pathway.read", Risk.LOW, "查看路径模板 / 患者路径"),
    PATHWAY_WRITE("pathway.write", Risk.MEDIUM, "编辑路径模板"),
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
    AUDIT_EXPORT("audit.export", Risk.MEDIUM, "导出审计快照 / 证据包"),

    // ─── 标准上下文（GA-ENG-API-01）────────────────────────────
    CONTEXT_READ("context.read", Risk.LOW, "查看标准上下文 snapshot"),
    CONTEXT_WRITE("context.write", Risk.MEDIUM, "创建标准上下文 snapshot"),

    // ─── 临床事件（GA-ENG-API-02）──────────────────────────────
    EVENT_READ("event.read", Risk.LOW, "查看临床事件"),
    EVENT_WRITE("event.write", Risk.MEDIUM, "创建 / 重放临床事件"),

    // ─── 系统运维（GA-ENG-BASE-07）─────────────────────────────
    SYSTEM_READ("system.read", Risk.LOW, "查看系统状态 / Provider"),
    SYSTEM_MANAGE("system.manage", Risk.HIGH, "运维操作（重启、密钥轮换、降级开关）"),

    // ─── 追加权限（保持已发布枚举顺序稳定）──────────────────────────
    RECOMMENDATION_WRITE("recommendation.write", Risk.MEDIUM, "创建推荐触发和候选提醒事实"),
    EVALUATION_EXECUTE("evaluation.execute", Risk.MEDIUM, "接收评估运行和结果事实"),
    EVALUATION_REMEDIATE("evaluation.remediate", Risk.MEDIUM, "提交质控问题整改证据"),
    EVALUATION_REVIEW("evaluation.review", Risk.HIGH, "复核质控整改并关闭问题"),
    FOLLOWUP_READ("followup.read", Risk.LOW, "查看随访计划与任务列表"),
    FOLLOWUP_WRITE("followup.write", Risk.MEDIUM, "智能生成随访计划、触发任务、提交问卷与回传异常事件"),
    EMBED_READ("embed.read", Risk.LOW, "验证和查看嵌入上下文"),
    EMBED_WRITE("embed.write", Risk.MEDIUM, "生成嵌入启动令牌和记录反馈"),
    LLM_READ("llm.read", Risk.LOW, "查看模型能力状态和调用记录"),
    LLM_WRITE("llm.write", Risk.MEDIUM, "管理配置路由策略和提交模型任务"),
    LIST_EXPORT("list.export", Risk.MEDIUM, "创建和下载大规模列表异步导出文件"),

    // ─── 菜单维度（INFRA-05 细化到 27 二级菜单前的一级入口基线）──────────────
    MENU_WORKBENCH("menu.workbench", PermissionDimension.MENU, Risk.LOW, "查看工作台入口"),
    MENU_PILOT_SETUP("menu.pilot-setup", PermissionDimension.MENU, Risk.LOW, "查看试点准备入口"),
    MENU_CLINICAL_RUN("menu.clinical-run", PermissionDimension.MENU, Risk.LOW, "查看临床运行入口"),
    MENU_QUALITY_IMPROVE("menu.quality-improve", PermissionDimension.MENU, Risk.LOW, "查看质控改进入口"),
    MENU_COMPLIANCE_OPS("menu.compliance-ops", PermissionDimension.MENU, Risk.LOW, "查看合规运维入口"),
    MENU_ADVANCED_TOOLS("menu.advanced-tools", PermissionDimension.MENU, Risk.LOW, "查看高级工具入口"),

    // ─── 数据维度（BASE-01 orgPath 上的组织范围基线）──────────────────────
    DATA_DEPARTMENT("data.department", PermissionDimension.DATA, Risk.LOW, "访问本科室数据"),
    DATA_HOSPITAL("data.hospital", PermissionDimension.DATA, Risk.MEDIUM, "访问全院数据"),
    DATA_GROUP("data.group", PermissionDimension.DATA, Risk.HIGH, "访问集团跨院数据"),
    DATA_DESENSITIZED("data.desensitized", PermissionDimension.DATA, Risk.LOW, "访问脱敏数据"),

    // ─── 资产维度（知识、字典、规则、路径、配置包的授权边界）──────────────────
    ASSET_CONFIG_PACKAGE("asset.config-package", PermissionDimension.ASSET, Risk.MEDIUM, "访问配置包资产"),
    ASSET_DICTIONARY("asset.dictionary", PermissionDimension.ASSET, Risk.MEDIUM, "访问字典映射资产"),
    ASSET_KNOWLEDGE_PACKAGE("asset.knowledge-package", PermissionDimension.ASSET, Risk.MEDIUM, "访问知识包资产"),
    ASSET_RULE("asset.rule", PermissionDimension.ASSET, Risk.MEDIUM, "访问规则资产"),
    ASSET_PATHWAY("asset.pathway", PermissionDimension.ASSET, Risk.MEDIUM, "访问路径资产"),

    // ─── 环境维度（正式应急的细粒度回收在 BASE-02 PR3 承接）───────────────
    ENV_TEST("env.test", PermissionDimension.ENVIRONMENT, Risk.LOW, "访问测试环境"),
    ENV_TRIAL("env.trial", PermissionDimension.ENVIRONMENT, Risk.MEDIUM, "访问试运行环境"),
    ENV_PRODUCTION("env.production", PermissionDimension.ENVIRONMENT, Risk.HIGH, "访问正式环境"),
    ENV_EMERGENCY("env.emergency", PermissionDimension.ENVIRONMENT, Risk.HIGH, "访问应急环境");

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
        int separator = code.indexOf('.');
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
