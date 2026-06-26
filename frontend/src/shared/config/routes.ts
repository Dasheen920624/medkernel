/**
 * 路由元数据（single source of truth）。
 * AppLayout + router + 菜单 + 面包屑 + 权限元数据全部读这里。
 */
import type { RouteExperience } from "@/shared/ui/experienceTypes";

export type RouteSectionKey =
  | "workbench"
  | "organization-people"
  | "knowledge-governance"
  | "knowledge-production"
  | "clinical-collaboration"
  | "quality-management"
  | "compliance-security"
  | "system-operations";

export type RoutePlacement = "primary" | "header" | "profile" | "hidden" | "embedded";

export type PageType =
  | "auth"
  | "workbench"
  | "list"
  | "configuration"
  | "dashboard"
  | "review"
  | "advanced"
  | "system";

export interface RouteMeta {
  path: string;
  title: string;
  breadcrumb: string[];
  requireAuth: boolean;
  sectionKey?: RouteSectionKey;
  menuKey?: string;
  menuLabel?: string;
  placement: RoutePlacement;
  navigationOrder: number;
  requiredPermissions: string[];
  hidden?: boolean;
  pageType?: PageType;
  stateMachine?: "config" | "change" | "todo" | "alert";
  requiresSixStates: boolean;
  requiresStepFlow: boolean;
  experience?: RouteExperience;
}

type RouteMetaInput = Omit<
  RouteMeta,
  "requiredPermissions" | "placement" | "navigationOrder" | "requiresSixStates" | "requiresStepFlow"
> &
  Partial<
    Pick<
      RouteMeta,
      | "requiredPermissions"
      | "placement"
      | "navigationOrder"
      | "requiresSixStates"
      | "requiresStepFlow"
    >
  >;

export interface RoutePermissionProfile {
  roles?: Array<{ code: string }>;
  permissions?: Array<{ code: string }>;
  menuKeys?: string[];
}

export interface RouteSectionMeta {
  key: RouteSectionKey;
  label: string;
}

export const routeSections: RouteSectionMeta[] = [
  { key: "workbench", label: "工作台" },
  { key: "organization-people", label: "机构与人员" },
  { key: "knowledge-governance", label: "知识治理" },
  { key: "knowledge-production", label: "知识生产" },
  { key: "clinical-collaboration", label: "临床协同" },
  { key: "quality-management", label: "质量管理" },
  { key: "compliance-security", label: "合规安全" },
  { key: "system-operations", label: "系统运维" },
];

function readonlyExperience(
  primaryRole: string,
  goal: string,
  defaultView: string,
  expected: RouteExperience["dataScale"]["expected"] = "small",
): RouteExperience {
  return {
    primaryRole,
    goal,
    defaultView,
    defaultFilters: [],
    evidenceDetailContent: ["追踪号", "原始字段"],
    interruptionLevel: "info",
    evidence: "保留来源、版本、审计和导出入口",
    dataScale: { expected, pagination: "page", exportStrategy: "disabled" },
    riskLevel: "low",
  };
}

const terminologyMappingExperience: RouteExperience = {
  primaryRole: "医疗引擎运营员",
  goal: "核查院内码与标准码的映射关系，降低规则和路径执行风险",
  defaultView: "最近更新的待确认和高风险映射优先",
  defaultFilters: [
    {
      key: "status",
      label: "映射状态",
      kind: "select",
      placeholder: "请选择映射状态",
      optionSource: "static",
      options: [
        { label: "草稿", value: "DRAFT" },
        { label: "已确认", value: "CONFIRMED" },
        { label: "已替换", value: "SUPERSEDED" },
        { label: "已回滚", value: "ROLLED_BACK" },
      ],
    },
    {
      key: "sourceSystem",
      label: "来源系统",
      kind: "search",
      placeholder: "输入来源系统",
    },
    {
      key: "keyword",
      label: "关键词",
      kind: "search",
      placeholder: "输入院内码或标准码关键词",
    },
  ],
  evidenceDetailContent: ["映射 ID", "院内编码 ID", "标准编码 ID", "追踪号", "来源原始状态"],
  interruptionLevel: "info",
  evidence: "候选、高危确认、冲突处置、发布和回滚均保留审计与证据入口",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "async" },
  riskLevel: "medium",
};

const auditExperience: RouteExperience = {
  primaryRole: "审计员",
  goal: "按时间、操作人、动作和对象追溯当前服务机构与组织范围内的关键操作证据",
  defaultView: "最近发生的事件优先",
  defaultFilters: [
    {
      key: "occurredAt",
      label: "发生日期",
      kind: "dateRange",
    },
    {
      key: "actorUserId",
      label: "操作人",
      kind: "search",
      placeholder: "输入操作人标识",
    },
    {
      key: "action",
      label: "操作事项",
      kind: "search",
      placeholder: "输入操作名称或编号",
    },
  ],
  evidenceDetailContent: ["事件编号", "环境标识", "输入内容摘要", "原始变更快照"],
  interruptionLevel: "info",
  evidence: "审计事件按服务机构与组织范围隔离，异步导出保留任务、追踪号与下载证据",
  dataScale: { expected: "massive", pagination: "cursor", exportStrategy: "async" },
  riskLevel: "medium",
};

const securityBaselineExperience: RouteExperience = {
  primaryRole: "平台管理员 / 审计员",
  goal: "统一核查并管理运行配置、数据权限、脱敏规则与互操作测评证据",
  defaultView: "安全基线概览",
  defaultFilters: [],
  evidenceDetailContent: ["配置键", "版本", "组织范围", "证据 ID", "追踪号"],
  interruptionLevel: "weak",
  evidence: "配置、权限、脱敏和测评变更均由平台校验并保留版本与审计证据",
  dataScale: { expected: "small", pagination: "page", exportStrategy: "none" },
  riskLevel: "high",
};

const identityBindingExperience: RouteExperience = {
  primaryRole: "平台管理员",
  goal: "管理系统用户与员工号、统一身份和国密证书的唯一绑定关系",
  defaultView: "当前服务机构与组织范围的有效绑定和解绑历史",
  defaultFilters: [],
  evidenceDetailContent: ["绑定 ID", "身份源类型", "版本", "追踪号"],
  interruptionLevel: "strong",
  evidence: "绑定与解绑均校验唯一性和版本，并保留服务机构与组织范围隔离的审计证据",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "none" },
  riskLevel: "high",
};

export const ADAPTER_PROTOCOL_OPTIONS = [
  { label: "HL7 v2", value: "HL7" },
  { label: "FHIR", value: "FHIR" },
  { label: "Webhook", value: "Webhook" },
  { label: "REST", value: "REST" },
  { label: "WebService", value: "WebService" },
] as const;

const adapterHubExperience: RouteExperience = {
  primaryRole: "平台管理员",
  goal: "查看院内系统接入、健康、字段映射、死信和数据质量，确保断连诚实暴露",
  defaultView: "异常连接、字段映射缺口和待上线接入申请优先",
  defaultFilters: [
    {
      key: "protocolType",
      label: "接入协议",
      kind: "select",
      placeholder: "请选择接入协议",
      optionSource: "static",
      options: [...ADAPTER_PROTOCOL_OPTIONS],
    },
    {
      key: "healthStatus",
      label: "健康状态",
      kind: "select",
      placeholder: "请选择健康状态",
      optionSource: "static",
      options: [
        { label: "健康", value: "HEALTHY" },
        { label: "未连接", value: "NOT_CONNECTED" },
        { label: "配置非法", value: "MISCONFIGURED" },
        { label: "异常", value: "UNHEALTHY" },
      ],
    },
    {
      key: "orgPath",
      label: "组织范围",
      kind: "search",
      placeholder: "输入院区或科室",
    },
  ],
  evidenceDetailContent: ["接入标识", "追踪号", "受控配置", "对接路线引用", "消息编号"],
  interruptionLevel: "strong",
  evidence: "适配器启停、健康检查、死信重放、数据质量报告均保留审计证据",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "async" },
  riskLevel: "medium",
};

const routeMetaInputs: RouteMetaInput[] = [
  {
    path: "/login",
    title: "登录",
    breadcrumb: ["登录"],
    requireAuth: false,
    hidden: true,
    pageType: "auth",
  },
  {
    path: "/bootstrap",
    title: "首次部署接管",
    breadcrumb: ["首次部署接管"],
    requireAuth: false,
    hidden: true,
    pageType: "auth",
  },
  {
    path: "/",
    title: "工作台",
    breadcrumb: ["工作台"],
    requireAuth: true,
    hidden: true,
    pageType: "workbench",
  },
  {
    path: "/dashboard",
    title: "工作台",
    breadcrumb: ["工作台"],
    requireAuth: true,
    sectionKey: "workbench",
    menuKey: "workbench",
    menuLabel: "工作台",
    placement: "primary",
    navigationOrder: 1,
    experience: readonlyExperience(
      "平台管理员 / 医疗引擎运营员 / 临床使用者 / 审计员",
      "查看当前运行状态和需要跟进的事项",
      "当前重点事项",
    ),
    pageType: "workbench",
  },
  {
    path: "/workbench/readiness-validation",
    title: "验收自检",
    breadcrumb: ["工作台", "验收自检"],
    requireAuth: true,
    sectionKey: "workbench",
    placement: "hidden",
    requiredPermissions: ["menu.workbench", "workbench:readiness:view"],
    hidden: true,
    experience: {
      primaryRole: "平台管理员",
      goal: "确认当前服务机构运行验收状态和阻塞修复去处",
      defaultView: "阻塞项优先",
      defaultFilters: [
        {
          key: "blocked",
          label: "阻塞",
          kind: "select",
          placeholder: "阻塞",
          optionSource: "static",
          options: [{ label: "阻塞", value: "blocked" }],
        },
        {
          key: "ready",
          label: "就绪",
          kind: "select",
          placeholder: "就绪",
          optionSource: "static",
          options: [{ label: "就绪", value: "ready" }],
        },
        {
          key: "disabled",
          label: "未启用",
          kind: "select",
          placeholder: "未启用",
          optionSource: "static",
          options: [{ label: "未启用", value: "disabled" }],
        },
      ],
      evidenceDetailContent: ["追踪号", "系统运行原始状态"],
      interruptionLevel: "info",
      evidence: "复用运行环境快照和权限画像，不新增工作台专属服务",
      dataScale: { expected: "small", pagination: "page", exportStrategy: "disabled" },
      riskLevel: "low",
    },
    pageType: "workbench",
    requiresStepFlow: false,
  },
  {
    path: "/onboarding/guide",
    title: "实施与验收",
    breadcrumb: ["系统运维", "实施与验收"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "implementation-guide",
    menuLabel: "实施与验收",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.implementation-guide", "tenant.read"],
    experience: readonlyExperience("平台管理员", "按步骤完成机构开通、联调和验收", "待完成步骤"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/tenant/onboarding",
    title: "服务机构",
    breadcrumb: ["机构与人员", "服务机构"],
    requireAuth: true,
    sectionKey: "organization-people",
    menuKey: "tenant-onboarding",
    menuLabel: "服务机构",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.tenant-onboarding", "tenant.read"],
    experience: readonlyExperience("平台管理员", "开通服务机构或配置当前服务机构", "待配置组织"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/config/releases",
    title: "发布治理",
    breadcrumb: ["知识治理", "发布治理"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "runtime-releases",
    menuLabel: "发布治理",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.runtime-releases", "asset.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "发布平台标准版本并为机构生成精确、可追溯的生效版本",
      "平台标准版本与机构生效版本",
      "large",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/authoring/assets",
    title: "知识资产",
    breadcrumb: ["知识治理", "知识资产"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    placement: "hidden",
    hidden: true,
    requiredPermissions: ["rule.read", "pathway.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "编目、收藏和复用规则路径资产",
      "最近更新资产",
      "large",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/pathway/templates",
    title: "路径配置",
    breadcrumb: ["知识治理", "路径配置"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "pathway-templates",
    menuLabel: "路径配置",
    placement: "primary",
    navigationOrder: 5,
    requiredPermissions: ["menu.pathway-templates", "pathway.read"],
    experience: readonlyExperience("医疗引擎运营员", "核查路径模板准备状态", "待处理路径", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/rule/definitions",
    title: "规则配置",
    breadcrumb: ["知识治理", "规则配置"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "rule-definitions",
    menuLabel: "规则配置",
    placement: "primary",
    navigationOrder: 4,
    requiredPermissions: ["menu.rule-definitions", "rule.read"],
    experience: readonlyExperience("医疗引擎运营员", "核查规则资产准备状态", "待处理规则", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/terminology/mapping",
    title: "术语与字典",
    breadcrumb: ["知识治理", "术语与字典"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "terminology-mapping",
    menuLabel: "术语与字典",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.terminology-mapping", "term.read"],
    experience: terminologyMappingExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/adapter/hub",
    title: "系统接入",
    breadcrumb: ["系统运维", "系统接入"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "adapter-hub",
    menuLabel: "系统接入",
    placement: "primary",
    navigationOrder: 1.5,
    requiredPermissions: [
      "menu.adapter-hub",
      "integration.read",
      "integration.write",
      "integration.execute",
    ],
    experience: adapterHubExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/mpi",
    title: "患者索引",
    breadcrumb: ["临床协同", "患者索引"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "mpi",
    menuLabel: "患者索引",
    placement: "primary",
    navigationOrder: 1,
    experience: readonlyExperience(
      "临床使用者",
      "查阅授权范围内的患者索引状态",
      "待核查记录",
      "large",
    ),
    pageType: "list",
  },
  {
    path: "/pathway/patients",
    title: "患者路径",
    breadcrumb: ["临床协同", "患者路径"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "patient-pathways",
    menuLabel: "患者路径",
    placement: "primary",
    navigationOrder: 2,
    experience: readonlyExperience("临床使用者", "查看患者路径运行事项", "待处理节点", "large"),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/cdss/fatigue",
    title: "提醒与推荐",
    breadcrumb: ["临床协同", "提醒与推荐"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "cdss-fatigue",
    menuLabel: "提醒与推荐",
    placement: "primary",
    navigationOrder: 3,
    experience: readonlyExperience(
      "临床使用者",
      "查看临床提醒负担和治理线索",
      "需关注提醒",
      "large",
    ),
    pageType: "list",
    stateMachine: "alert",
  },
  {
    path: "/rule/validate",
    title: "规则试运行",
    breadcrumb: ["知识治理", "规则配置", "试运行"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    placement: "hidden",
    hidden: true,
    // 临床执行侧（医师人工确认危急值提醒），只需 rule.read；不要求 menu.rule-definitions（治理侧菜单），
    // 否则临床使用者无法完成医师确认闭环。
    requiredPermissions: ["rule.read"],
    experience: readonlyExperience("临床使用者", "核查规则提示的依据和状态", "最近提示", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/workflow/todos",
    title: "协同任务",
    breadcrumb: ["临床协同", "协同任务"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "workflow-todos",
    menuLabel: "协同任务",
    placement: "primary",
    navigationOrder: 4,
    experience: readonlyExperience("临床使用者", "处理当前岗位待办事项", "待我处理", "large"),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/notifications",
    title: "消息通知",
    breadcrumb: ["工作台", "消息通知"],
    requireAuth: true,
    sectionKey: "workbench",
    menuKey: "notifications",
    menuLabel: "消息通知",
    placement: "header",
    navigationOrder: 1,
    experience: readonlyExperience(
      "平台管理员 / 医疗引擎运营员 / 临床使用者 / 审计员",
      "查看需要关注的通知",
      "未读通知",
      "large",
    ),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/clinical/followup",
    title: "随访协同",
    breadcrumb: ["临床协同", "随访协同"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "clinical-followup",
    menuLabel: "随访协同",
    placement: "primary",
    navigationOrder: 5,
    experience: readonlyExperience(
      "临床使用者",
      "生成专病随访计划并跟进分期任务与异常回院事件",
      "计划台账列表",
      "large",
    ),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/sandbox",
    title: "全真体验沙盘",
    breadcrumb: ["临床协同", "全真体验沙盘"],
    requireAuth: true,
    sectionKey: "clinical-collaboration",
    menuKey: "sandbox",
    menuLabel: "全真体验沙盘",
    placement: "primary",
    navigationOrder: 6,
    requiredPermissions: ["menu.sandbox", "sandbox.run"],
    experience: {
      primaryRole: "临床使用者 / 医疗引擎运营员",
      goal: "以院内业务系统视角验证真实引擎调用、嵌入终端和反馈闭环",
      defaultView: "可运行场景与最近一次路径证据",
      defaultFilters: [],
      evidenceDetailContent: ["调用明细", "服务端事实", "追踪号"],
      interruptionLevel: "strong",
      evidence: "每次运行保留上下文、推荐、访问凭证与宿主反馈追踪号",
      dataScale: { expected: "small", pagination: "page", exportStrategy: "none" },
      riskLevel: "high",
    },
    pageType: "review",
  },
  {
    path: "/qc/dashboard",
    title: "质量管理概览",
    breadcrumb: ["质量管理", "质量管理概览"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "qc-dashboard",
    menuLabel: "质量管理概览",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.qc-dashboard", "evaluation.read"],
    experience: readonlyExperience("医疗引擎运营员", "查看质量风险与改进进展", "本期风险概览"),
    pageType: "dashboard",
  },
  {
    path: "/qc/alerts",
    title: "质量问题与整改",
    breadcrumb: ["质量管理", "质量问题与整改"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "qc-alerts",
    menuLabel: "质量问题与整改",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.qc-alerts", "evaluation.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "处理质量问题与整改事项",
      "高风险待处理",
      "large",
    ),
    pageType: "list",
    stateMachine: "alert",
  },
  {
    path: "/qc/insurance",
    title: "医保审核",
    breadcrumb: ["质量管理", "医保审核"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "insurance-audit",
    menuLabel: "医保审核",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.insurance-audit", "evaluation.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "核查医保审核问题与依据",
      "待审核问题",
      "large",
    ),
    pageType: "review",
    stateMachine: "config",
  },
  {
    path: "/qc/eval/sets",
    title: "评价指标",
    breadcrumb: ["质量管理", "评价指标"],
    requireAuth: true,
    sectionKey: "quality-management",
    menuKey: "qc-eval-sets",
    menuLabel: "评价指标",
    placement: "primary",
    navigationOrder: 4,
    requiredPermissions: ["menu.qc-eval-sets", "evaluation.read"],
    experience: readonlyExperience("医疗引擎运营员", "核查评价指标配置状态", "待维护指标", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/qc/eval/results",
    title: "质量问题来源",
    breadcrumb: ["质量管理", "质量问题与整改", "发现来源"],
    requireAuth: true,
    sectionKey: "quality-management",
    placement: "hidden",
    hidden: true,
    requiredPermissions: ["menu.qc-alerts", "evaluation.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "查看评价结果来源和待改进事项",
      "近期评价",
      "large",
    ),
    pageType: "list",
  },
  {
    path: "/knowledge/governance",
    title: "知识审核与发布",
    breadcrumb: ["知识治理", "知识审核与发布"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "knowledge-governance",
    menuLabel: "知识审核与发布",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.knowledge-governance", "knowledge.review"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "审核知识候选并完成发布、驳回、替换或恢复",
      "待治理知识",
      "large",
    ),
    pageType: "review",
    stateMachine: "config",
  },
  {
    path: "/knowledge/institution",
    title: "机构知识",
    breadcrumb: ["知识治理", "机构知识"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "institution-knowledge",
    menuLabel: "机构知识",
    placement: "primary",
    navigationOrder: 1.25,
    requiredPermissions: ["menu.institution-knowledge", "knowledge.write"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "维护院内覆盖、机构定制、换基线和恢复平台标准",
      "机构知识血缘",
      "large",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/knowledge/diagnosis",
    title: "诊断知识维护",
    breadcrumb: ["知识治理", "诊断知识维护"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "diagnosis-knowledge",
    menuLabel: "诊断知识维护",
    placement: "primary",
    navigationOrder: 1.5,
    requiredPermissions: ["menu.diagnosis-knowledge", "knowledge.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据",
      "诊断知识台账",
      "large",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/knowledge/production",
    title: "知识生产",
    breadcrumb: ["知识生产"],
    requireAuth: true,
    sectionKey: "knowledge-production",
    menuKey: "knowledge-production",
    menuLabel: "知识生产",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.knowledge-production", "knowledge.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "在同一页面完成模型服务、医学评测、安全门和大模型知识候选生成",
      "知识生产步骤",
      "large",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/admin/users",
    title: "人员与账号",
    breadcrumb: ["机构与人员", "人员与账号"],
    requireAuth: true,
    sectionKey: "organization-people",
    menuKey: "admin-users",
    menuLabel: "人员与账号",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.admin-users", "org.read"],
    experience: readonlyExperience(
      "平台管理员",
      "维护人员、任职、账号与组织范围",
      "有效人员",
      "large",
    ),
    pageType: "list",
  },
  {
    path: "/security/identity-binding",
    title: "身份来源",
    breadcrumb: ["机构与人员", "身份来源"],
    requireAuth: true,
    sectionKey: "organization-people",
    menuKey: "identity-bindings",
    menuLabel: "身份来源",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.identity-bindings", "org.read"],
    experience: identityBindingExperience,
    pageType: "system",
    stateMachine: "change",
  },
  {
    path: "/admin/audit",
    title: "审计与证据",
    breadcrumb: ["合规安全", "审计与证据"],
    requireAuth: true,
    sectionKey: "compliance-security",
    menuKey: "admin-audit",
    menuLabel: "审计与证据",
    placement: "primary",
    navigationOrder: 1,
    requiredPermissions: ["menu.admin-audit", "audit.read"],
    experience: auditExperience,
    pageType: "list",
  },
  {
    path: "/security/baseline",
    title: "安全与配置",
    breadcrumb: ["合规安全", "安全与配置"],
    requireAuth: true,
    sectionKey: "compliance-security",
    menuKey: "security-baseline",
    menuLabel: "安全与配置",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.security-baseline", "system.read"],
    experience: securityBaselineExperience,
    pageType: "system",
  },
  {
    path: "/system/providers",
    title: "运行保障",
    breadcrumb: ["系统运维", "运行保障"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "system-providers",
    menuLabel: "运行保障",
    placement: "primary",
    navigationOrder: 3,
    requiredPermissions: ["menu.system-providers", "system.read"],
    experience: readonlyExperience(
      "平台管理员",
      "核查依赖服务、备份恢复与国产化运行状态",
      "异常优先",
    ),
    pageType: "system",
  },
  {
    path: "/notifications/settings",
    title: "通知偏好",
    breadcrumb: ["工作台", "通知偏好"],
    requireAuth: true,
    sectionKey: "workbench",
    menuKey: "notification-settings",
    menuLabel: "通知偏好",
    placement: "profile",
    navigationOrder: 1,
    experience: readonlyExperience(
      "平台管理员 / 医疗引擎运营员 / 临床使用者 / 审计员",
      "配置个人通知偏好与服务机构默认策略",
      "当前配置",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/advanced/provenance",
    title: "来源与血缘",
    breadcrumb: ["知识治理", "来源与血缘"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "provenance",
    menuLabel: "来源与血缘",
    placement: "primary",
    navigationOrder: 6,
    requiredPermissions: ["menu.provenance", "knowledge.read"],
    experience: readonlyExperience(
      "医疗引擎运营员 / 审计员",
      "追溯来源与运行证据",
      "最近来源",
      "large",
    ),
    pageType: "advanced",
  },
  {
    path: "/advanced/graph",
    title: "知识关系",
    breadcrumb: ["知识治理", "知识关系"],
    requireAuth: true,
    sectionKey: "knowledge-governance",
    menuKey: "graph-explore",
    menuLabel: "知识关系",
    placement: "primary",
    navigationOrder: 7,
    requiredPermissions: ["menu.graph-explore", "projection.read"],
    experience: readonlyExperience("医疗引擎运营员", "核查知识关系查询结果", "最近查询", "large"),
    pageType: "advanced",
  },
  {
    path: "/advanced/ai-workflows",
    title: "模型能力",
    breadcrumb: ["知识生产", "模型能力"],
    requireAuth: true,
    sectionKey: "knowledge-production",
    menuKey: "ai-workflows",
    menuLabel: "模型能力",
    placement: "primary",
    navigationOrder: 2,
    requiredPermissions: ["menu.ai-workflows", "llm.read"],
    experience: readonlyExperience(
      "医疗引擎运营员",
      "核查当前组织 AI 能力与降级状态",
      "能力状态",
      "large",
    ),
    pageType: "advanced",
  },
  {
    path: "/advanced/domestic",
    title: "国产化核验",
    breadcrumb: ["系统运维", "国产化核验"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "domestic-check",
    menuLabel: "国产化核验",
    placement: "primary",
    navigationOrder: 4,
    experience: readonlyExperience("平台管理员", "核查国产化适配准备状态", "待检查项"),
    pageType: "advanced",
  },
  {
    path: "/advanced/dev-console",
    title: "诊断工具",
    breadcrumb: ["系统运维", "诊断工具"],
    requireAuth: true,
    sectionKey: "system-operations",
    menuKey: "dev-console",
    menuLabel: "诊断工具",
    placement: "primary",
    navigationOrder: 5,
    experience: readonlyExperience("平台管理员", "核查运行诊断信息", "最近诊断", "large"),
    pageType: "advanced",
  },
  {
    path: "/embed/launch",
    title: "临床嵌入式终端",
    breadcrumb: ["临床协同", "临床嵌入式终端"],
    requireAuth: false,
    sectionKey: "clinical-collaboration",
    placement: "embedded",
    hidden: true,
    pageType: "system",
  },
  {
    path: "*",
    title: "未找到页面",
    breadcrumb: ["未找到页面"],
    requireAuth: false,
    hidden: true,
    pageType: "system",
  },
];

export function menuPermissionCode(menuKey: string): string {
  return `menu.${menuKey}`;
}

function normalizeRouteMeta(route: RouteMetaInput): RouteMeta {
  const placement = route.placement ?? (route.menuKey ? "primary" : "hidden");
  const requiredPermissions =
    route.requiredPermissions ??
    (route.requireAuth
      ? [menuPermissionCode(route.menuKey ?? route.sectionKey ?? "workbench")]
      : []);

  return {
    ...route,
    placement,
    navigationOrder: route.navigationOrder ?? Number.MAX_SAFE_INTEGER,
    hidden: route.hidden ?? placement !== "primary",
    requiredPermissions,
    requiresSixStates: route.requiresSixStates ?? route.requireAuth,
    requiresStepFlow: route.requiresStepFlow ?? route.pageType === "configuration",
  };
}

export const routeMetas: RouteMeta[] = routeMetaInputs.map(normalizeRouteMeta);

export const customerRouteMetas = routeMetas.filter(
  (route) =>
    route.requireAuth &&
    (route.placement === "primary" ||
      route.placement === "header" ||
      route.placement === "profile"),
);

export function findRouteByPath(path: string): RouteMeta | undefined {
  return routeMetas.find((route) => route.path === path);
}

export function canAccessRoute(
  route: RouteMeta | undefined,
  profile: RoutePermissionProfile | undefined,
): boolean {
  if (!route?.requireAuth) {
    return true;
  }
  if (!profile) {
    return false;
  }

  const grantedPermissions = new Set(
    profile.permissions?.map((permission) => permission.code) ?? [],
  );
  const grantedMenuKeys = new Set(profile.menuKeys ?? []);
  const hasRequiredPermissions = route.requiredPermissions.every(
    (permission) =>
      grantedPermissions.has(permission) ||
      (permission.startsWith("menu.") && grantedMenuKeys.has(permission.slice("menu.".length))),
  );
  return hasRequiredPermissions;
}

export function getRouteBreadcrumb(path: string): string[] {
  return findRouteByPath(path)?.breadcrumb ?? ["未找到页面"];
}

export function getRouteTitle(path: string): string {
  return findRouteByPath(path)?.title ?? "未找到页面";
}
