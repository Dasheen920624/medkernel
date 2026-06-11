/**
 * 路由元数据（single source of truth）。
 * AppLayout + router + 菜单 + 面包屑 + 权限元数据全部读这里。
 */
import type { RouteExperience } from "@/shared/ui/experienceTypes";
import { ROLE_OPTIONS } from "./roleCatalog";

export type RouteSectionKey =
  | "workbench"
  | "pilot-setup"
  | "clinical-run"
  | "quality-improve"
  | "compliance-ops"
  | "advanced-tools";

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
  requiredRoles: string[];
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
  "requiredRoles" | "requiredPermissions" | "requiresSixStates" | "requiresStepFlow"
> &
  Partial<
    Pick<
      RouteMeta,
      "requiredRoles" | "requiredPermissions" | "requiresSixStates" | "requiresStepFlow"
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
  hidden?: boolean;
}

export const routeSections: RouteSectionMeta[] = [
  { key: "workbench", label: "工作台" },
  { key: "pilot-setup", label: "试点准备" },
  { key: "clinical-run", label: "临床运行" },
  { key: "quality-improve", label: "质控改进" },
  { key: "compliance-ops", label: "合规运维" },
  { key: "advanced-tools", label: "高级工具", hidden: true },
];

const CUSTOMER_ROLE_CODES = ROLE_OPTIONS.map((role) => role.code);
const SYSTEM_SUPERADMIN_ROLE = "system-superadmin";
const GOVERNANCE_ADMIN_ROLE_CODES = new Set([
  SYSTEM_SUPERADMIN_ROLE,
  "platform-governance-admin",
  "organization-admin",
]);
const WORKBENCH_LANDING_ROLE_CODES = [...CUSTOMER_ROLE_CODES, SYSTEM_SUPERADMIN_ROLE];

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
    expertContent: ["traceId", "原始字段"],
    interruptionLevel: "info",
    evidence: "保留来源、版本、审计和导出入口",
    dataScale: { expected, pagination: "page", exportStrategy: "disabled" },
    riskLevel: "low",
  };
}

const terminologyMappingExperience: RouteExperience = {
  primaryRole: "信息科 / 专科专家 / 医务处",
  goal: "核查院内码与标准码的映射关系，降低后续规则和路径执行风险",
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
  expertContent: ["映射 ID", "院内编码 ID", "标准编码 ID", "traceId", "接口原始状态"],
  interruptionLevel: "info",
  evidence: "候选、高危确认、冲突处置、发布和回滚均保留审计与证据入口",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "async" },
  riskLevel: "medium",
};

const auditExperience: RouteExperience = {
  primaryRole: "审计人员 / 信息科",
  goal: "按时间、操作人、动作和对象追溯当前服务空间的关键操作证据",
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
      label: "操作编码",
      kind: "search",
      placeholder: "输入操作编码",
    },
  ],
  expertContent: [
    "对象类型筛选",
    "执行结果筛选",
    "事件编号",
    "traceId",
    "签名",
    "载荷摘要",
    "环境标识",
  ],
  interruptionLevel: "info",
  evidence: "审计事件按服务空间隔离，异步导出保留任务、追踪标识与下载证据",
  dataScale: { expected: "massive", pagination: "cursor", exportStrategy: "async" },
  riskLevel: "medium",
};

const securityBaselineExperience: RouteExperience = {
  primaryRole: "信息科 / 安全管理员",
  goal: "统一核查并管理运行配置、数据权限、脱敏规则与互操作测评证据",
  defaultView: "安全基线概览",
  defaultFilters: [],
  expertContent: ["配置键", "版本", "组织范围", "证据 ID", "traceId"],
  interruptionLevel: "weak",
  evidence: "配置、权限、脱敏和测评变更均由后端校验并保留版本与审计证据",
  dataScale: { expected: "small", pagination: "page", exportStrategy: "none" },
  riskLevel: "high",
};

const identityBindingExperience: RouteExperience = {
  primaryRole: "信息科 / 各级管理员",
  goal: "管理系统用户与员工号、统一身份和国密证书的唯一绑定关系",
  defaultView: "当前服务空间的有效绑定和解绑历史",
  defaultFilters: [],
  expertContent: ["绑定 ID", "身份源类型", "版本", "traceId"],
  interruptionLevel: "strong",
  evidence: "绑定与解绑均校验唯一性和版本，并保留服务空间隔离的审计证据",
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
  primaryRole: "信息科 / 实施工程师",
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
  expertContent: ["adapterId", "traceId", "configJson", "routeReference", "messageId"],
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
    requiredRoles: WORKBENCH_LANDING_ROLE_CODES,
    experience: readonlyExperience(
      "医院管理者",
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
    menuKey: "readiness-validation",
    menuLabel: "验收自检",
    requiredPermissions: ["menu.workbench", "workbench:readiness:view"],
    requiredRoles: [
      "implementation-operator",
      "integration-operator",
      "organization-admin",
      "platform-governance-admin",
      SYSTEM_SUPERADMIN_ROLE,
    ],
    hidden: true,
    experience: {
      primaryRole: "实施工程师 / 信息科 / 医院管理员 / 平台管理员",
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
      expertContent: ["traceId", "运行底座原始状态"],
      interruptionLevel: "info",
      evidence: "复用运行底座快照和权限画像，不新增工作台专属接口",
      dataScale: { expected: "small", pagination: "page", exportStrategy: "disabled" },
      riskLevel: "low",
    },
    pageType: "workbench",
    requiresStepFlow: false,
  },
  {
    path: "/onboarding/guide",
    title: "客户实施向导",
    breadcrumb: ["试点准备", "客户实施向导"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "implementation-guide",
    menuLabel: "客户实施向导",
    requiredPermissions: ["menu.implementation-guide", "tenant.read"],
    requiredRoles: ["implementation-operator", "platform-governance-admin", "organization-admin"],
    experience: readonlyExperience("实施工程师", "按步骤完成试点准备核查", "待完成步骤"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/tenant/onboarding",
    title: "服务机构管理",
    breadcrumb: ["试点准备", "服务机构管理"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "tenant-onboarding",
    menuLabel: "服务机构管理",
    requiredPermissions: ["menu.tenant-onboarding", "tenant.read"],
    requiredRoles: ["implementation-operator", "platform-governance-admin", "organization-admin"],
    experience: readonlyExperience(
      "实施工程师",
      "开通服务空间或配置当前服务机构",
      "待配置组织",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/config/packages",
    title: "配置包中心",
    breadcrumb: ["试点准备", "配置包中心"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "config-packages",
    menuLabel: "配置包中心",
    requiredPermissions: ["menu.config-packages", "package.read", "package.publish"],
    requiredRoles: [
      "implementation-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "platform-governance-admin",
      "organization-admin",
    ],
    experience: readonlyExperience(
      "实施工程师",
      "核查配置包准备和发布状态",
      "待处理配置包",
      "large",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/config/releases",
    title: "发布治理",
    breadcrumb: ["试点准备", "配置包中心", "发布治理"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    hidden: true,
    requiredPermissions: ["menu.config-packages", "package.read", "package.publish"],
    requiredRoles: [
      "implementation-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "platform-governance-admin",
      "organization-admin",
    ],
    experience: readonlyExperience(
      "发布管理员",
      "先模拟影响，再灰度放量或批量复用覆盖",
      "影响模拟",
      "large",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/authoring/assets",
    title: "统一资产库",
    breadcrumb: ["试点准备", "统一资产库"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    hidden: true,
    requiredPermissions: ["rule.read", "pathway.read"],
    requiredRoles: [
      "implementation-operator",
      "integration-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "clinical-governor",
      "medication-safety-user",
      "platform-governance-admin",
      "organization-admin",
    ],
    experience: readonlyExperience(
      "实施工程师",
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
    breadcrumb: ["试点准备", "路径配置"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "pathway-templates",
    menuLabel: "路径配置",
    requiredPermissions: ["menu.pathway-templates", "pathway.read"],
    experience: readonlyExperience("专科专家", "核查路径模板准备状态", "待处理路径", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/rule/definitions",
    title: "规则库",
    breadcrumb: ["试点准备", "规则库"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "rule-definitions",
    menuLabel: "规则库",
    requiredPermissions: ["menu.rule-definitions", "rule.read"],
    experience: readonlyExperience("医务处", "核查规则资产准备状态", "待处理规则", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/terminology/mapping",
    title: "字典映射",
    breadcrumb: ["试点准备", "字典映射"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "terminology-mapping",
    menuLabel: "字典映射",
    requiredPermissions: ["menu.terminology-mapping", "term.read"],
    requiredRoles: [
      "integration-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "diagnostic-service-user",
    ],
    experience: terminologyMappingExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/adapter/hub",
    title: "适配器中心",
    breadcrumb: ["试点准备", "适配器中心"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "adapter-hub",
    menuLabel: "适配器中心",
    requiredPermissions: [
      "menu.adapter-hub",
      "integration.read",
      "integration.write",
      "integration.execute",
    ],
    requiredRoles: ["integration-operator", "implementation-operator"],
    experience: adapterHubExperience,
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/mpi",
    title: "患者主索引",
    breadcrumb: ["临床运行", "患者主索引"],
    requireAuth: true,
    sectionKey: "clinical-run",
    menuKey: "mpi",
    menuLabel: "患者主索引",
    experience: readonlyExperience(
      "临床医生",
      "查阅授权范围内的患者索引状态",
      "待核查记录",
      "large",
    ),
    pageType: "list",
  },
  {
    path: "/pathway/patients",
    title: "患者路径",
    breadcrumb: ["临床运行", "患者路径"],
    requireAuth: true,
    sectionKey: "clinical-run",
    menuKey: "patient-pathways",
    menuLabel: "患者路径",
    experience: readonlyExperience("临床医生", "查看患者路径运行事项", "待处理节点", "large"),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/cdss/fatigue",
    title: "临床提醒治理",
    breadcrumb: ["临床运行", "临床提醒治理"],
    requireAuth: true,
    sectionKey: "clinical-run",
    menuKey: "cdss-fatigue",
    menuLabel: "临床提醒治理",
    experience: readonlyExperience("医务处", "查看临床提醒负担和治理线索", "需关注提醒", "large"),
    pageType: "list",
    stateMachine: "alert",
  },
  {
    path: "/rule/validate",
    title: "规则校验",
    breadcrumb: ["临床运行", "规则校验"],
    requireAuth: true,
    sectionKey: "clinical-run",
    menuKey: "rule-validate",
    menuLabel: "规则校验",
    experience: readonlyExperience("临床医生", "核查规则提示的依据和状态", "最近提示", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/workflow/todos",
    title: "待办中心",
    breadcrumb: ["临床运行", "待办中心"],
    requireAuth: true,
    sectionKey: "clinical-run",
    menuKey: "workflow-todos",
    menuLabel: "待办中心",
    experience: readonlyExperience("临床医生", "处理当前岗位待办事项", "待我处理", "large"),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/notifications",
    title: "通知中心",
    breadcrumb: ["临床运行", "通知中心"],
    requireAuth: true,
    sectionKey: "clinical-run",
    menuKey: "notifications",
    menuLabel: "通知中心",
    experience: readonlyExperience("临床医生", "查看需要关注的通知", "未读通知", "large"),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/clinical/followup",
    title: "智能随访",
    breadcrumb: ["临床运行", "智能随访"],
    requireAuth: true,
    sectionKey: "clinical-run",
    menuKey: "clinical-followup",
    menuLabel: "智能随访",
    experience: readonlyExperience(
      "随访专员 / 临床医生",
      "智能生成专病随访计划并跟进分期随访任务与异常回院事件",
      "计划台账列表",
      "large",
    ),
    pageType: "list",
    stateMachine: "todo",
  },
  {
    path: "/qc/dashboard",
    title: "院级质控驾驶舱",
    breadcrumb: ["质控改进", "院级质控驾驶舱"],
    requireAuth: true,
    sectionKey: "quality-improve",
    menuKey: "qc-dashboard",
    menuLabel: "院级质控驾驶舱",
    requiredPermissions: ["menu.qc-dashboard", "evaluation.read"],
    experience: readonlyExperience("质控办", "查看质控风险与改进进展", "本期风险概览"),
    pageType: "dashboard",
  },
  {
    path: "/qc/alerts",
    title: "质控预警",
    breadcrumb: ["质控改进", "质控预警"],
    requireAuth: true,
    sectionKey: "quality-improve",
    menuKey: "qc-alerts",
    menuLabel: "质控预警",
    requiredPermissions: ["menu.qc-alerts", "evaluation.read"],
    experience: readonlyExperience("质控办", "处理质控预警事项", "高风险待处理", "large"),
    pageType: "list",
    stateMachine: "alert",
  },
  {
    path: "/qc/insurance",
    title: "医保智能审核",
    breadcrumb: ["质控改进", "医保智能审核"],
    requireAuth: true,
    sectionKey: "quality-improve",
    menuKey: "insurance-audit",
    menuLabel: "医保智能审核",
    requiredPermissions: ["menu.insurance-audit", "evaluation.read"],
    experience: readonlyExperience("医保办", "核查医保审核问题与依据", "待审核问题", "large"),
    pageType: "review",
    stateMachine: "config",
  },
  {
    path: "/qc/eval/sets",
    title: "评估指标库",
    breadcrumb: ["质控改进", "评估指标库"],
    requireAuth: true,
    sectionKey: "quality-improve",
    menuKey: "qc-eval-sets",
    menuLabel: "评估指标库",
    requiredPermissions: ["menu.qc-eval-sets", "evaluation.read"],
    experience: readonlyExperience("质控办", "核查评估指标配置状态", "待维护指标", "large"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/qc/eval/results",
    title: "评估结果",
    breadcrumb: ["质控改进", "评估结果"],
    requireAuth: true,
    sectionKey: "quality-improve",
    menuKey: "qc-eval-results",
    menuLabel: "评估结果",
    requiredPermissions: ["menu.qc-eval-results", "evaluation.read"],
    experience: readonlyExperience("质控办", "查看评估结果和待改进事项", "近期结果", "large"),
    pageType: "list",
  },
  {
    path: "/knowledge/governance",
    title: "知识治理",
    breadcrumb: ["质控改进", "知识治理"],
    requireAuth: true,
    sectionKey: "quality-improve",
    menuKey: "knowledge-governance",
    menuLabel: "知识治理",
    requiredPermissions: ["menu.knowledge-governance", "knowledge.read"],
    requiredRoles: [
      "platform-governance-admin",
      "platform-knowledge-governor",
      "knowledge-governor",
      "clinical-governor",
      "quality-governor",
      "medication-safety-user",
    ],
    experience: readonlyExperience(
      "医务处 / 专科专家",
      "审核知识候选并维护结构化诊断知识",
      "待治理知识",
      "large",
    ),
    pageType: "review",
    stateMachine: "config",
  },
  {
    path: "/admin/users",
    title: "人员与账号",
    breadcrumb: ["合规运维", "人员与账号"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "admin-users",
    menuLabel: "人员与账号",
    requiredPermissions: ["menu.admin-users", "org.read"],
    requiredRoles: [
      "system-superadmin",
      "platform-governance-admin",
      "organization-admin",
      "identity-access-admin",
    ],
    experience: readonlyExperience(
      "机构管理员",
      "维护人员、任职、账号与组织范围",
      "有效人员",
      "large",
    ),
    pageType: "list",
  },
  {
    path: "/security/identity-binding",
    title: "身份来源",
    breadcrumb: ["合规运维", "身份来源"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "identity-bindings",
    menuLabel: "身份来源",
    requiredPermissions: ["menu.identity-bindings", "org.read"],
    requiredRoles: [
      "identity-access-admin",
      "integration-operator",
      "platform-governance-admin",
      "organization-admin",
    ],
    experience: identityBindingExperience,
    pageType: "system",
    stateMachine: "change",
  },
  {
    path: "/admin/audit",
    title: "审计日志",
    breadcrumb: ["合规运维", "审计日志"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "admin-audit",
    menuLabel: "审计日志",
    requiredPermissions: ["menu.admin-audit", "audit.read"],
    experience: auditExperience,
    pageType: "list",
  },
  {
    path: "/security/baseline",
    title: "安全基线与系统配置",
    breadcrumb: ["合规运维", "安全基线与系统配置"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "security-baseline",
    menuLabel: "安全与配置",
    requiredPermissions: ["menu.security-baseline", "system.read"],
    experience: securityBaselineExperience,
    pageType: "system",
  },
  {
    path: "/system/providers",
    title: "运行状态",
    breadcrumb: ["合规运维", "运行状态"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "system-providers",
    menuLabel: "运行状态",
    requiredPermissions: ["menu.system-providers", "system.read"],
    requiredRoles: [
      "implementation-operator",
      "integration-operator",
      "platform-governance-admin",
      "organization-admin",
    ],
    experience: readonlyExperience(
      "运维人员",
      "核查依赖服务、备份恢复与国产化运行状态",
      "异常优先",
    ),
    pageType: "system",
  },
  {
    path: "/notifications/settings",
    title: "通知设置",
    breadcrumb: ["合规运维", "通知设置"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "notification-settings",
    menuLabel: "通知设置",
    experience: readonlyExperience(
      "当前用户",
      "配置个人通知偏好与服务机构默认策略",
      "当前配置",
    ),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/advanced/provenance",
    title: "来源追溯",
    breadcrumb: ["高级工具", "来源追溯"],
    requireAuth: true,
    sectionKey: "advanced-tools",
    menuKey: "provenance",
    menuLabel: "来源追溯",
    requiredPermissions: ["menu.provenance", "knowledge.read"],
    experience: readonlyExperience("高级实施人员", "追溯来源与运行证据", "最近来源", "large"),
    hidden: false,
    pageType: "advanced",
  },
  {
    path: "/advanced/graph",
    title: "图谱查询",
    breadcrumb: ["高级工具", "图谱查询"],
    requireAuth: true,
    sectionKey: "advanced-tools",
    menuKey: "graph-explore",
    menuLabel: "图谱查询",
    requiredPermissions: ["menu.graph-explore", "projection.read"],
    requiredRoles: [
      "implementation-operator",
      "integration-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "platform-governance-admin",
      "organization-admin",
    ],
    experience: readonlyExperience("高级实施人员", "核查知识关系查询结果", "最近查询", "large"),
    hidden: false,
    pageType: "advanced",
  },
  {
    path: "/advanced/ai-workflows",
    title: "AI 工作流",
    breadcrumb: ["高级工具", "AI 工作流"],
    requireAuth: true,
    sectionKey: "advanced-tools",
    menuKey: "ai-workflows",
    menuLabel: "AI 工作流",
    requiredPermissions: ["menu.ai-workflows", "llm.read"],
    requiredRoles: [
      "implementation-operator",
      "integration-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "clinical-governor",
      "platform-governance-admin",
      "organization-admin",
    ],
    experience: readonlyExperience(
      "实施与治理人员",
      "核查当前组织 AI 能力与降级状态",
      "能力状态",
      "large",
    ),
    hidden: false,
    pageType: "advanced",
  },
  {
    path: "/advanced/domestic",
    title: "国产化自检",
    breadcrumb: ["高级工具", "国产化自检"],
    requireAuth: true,
    sectionKey: "advanced-tools",
    menuKey: "domestic-check",
    menuLabel: "国产化自检",
    experience: readonlyExperience("运维人员", "核查国产化适配准备状态", "待检查项"),
    hidden: true,
    pageType: "advanced",
  },
  {
    path: "/advanced/dev-console",
    title: "开发者控制台",
    breadcrumb: ["高级工具", "开发者控制台"],
    requireAuth: true,
    sectionKey: "advanced-tools",
    menuKey: "dev-console",
    menuLabel: "开发者控制台",
    experience: readonlyExperience("开发人员", "核查受控调试信息", "最近诊断", "large"),
    hidden: true,
    pageType: "advanced",
  },
  {
    path: "/embed/launch",
    title: "临床嵌入式终端",
    breadcrumb: ["临床运行", "临床嵌入式终端"],
    requireAuth: false,
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
  const requiredPermissions =
    route.requiredPermissions ??
    (route.requireAuth
      ? [menuPermissionCode(route.menuKey ?? route.sectionKey ?? "workbench")]
      : []);

  return {
    ...route,
    requiredPermissions,
    requiredRoles: route.requiredRoles ?? [],
    requiresSixStates: route.requiresSixStates ?? route.requireAuth,
    requiresStepFlow: route.requiresStepFlow ?? route.pageType === "configuration",
  };
}

export const routeMetas: RouteMeta[] = routeMetaInputs.map(normalizeRouteMeta);

export const customerRouteMetas = routeMetas.filter(
  (route) => route.requireAuth && !route.hidden && route.sectionKey !== "advanced-tools",
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
  const grantedRoles = new Set(profile.roles?.map((role) => role.code) ?? []);
  const hasRequiredPermissions = route.requiredPermissions.every(
    (permission) =>
      grantedPermissions.has(permission) ||
      (permission.startsWith("menu.") && grantedMenuKeys.has(permission.slice("menu.".length))),
  );
  const hasGovernanceAdminRole = [...grantedRoles].some((role) =>
    GOVERNANCE_ADMIN_ROLE_CODES.has(role),
  );
  const hasRequiredRole =
    hasGovernanceAdminRole ||
    route.requiredRoles.length === 0 ||
    route.requiredRoles.some((role) => grantedRoles.has(role));

  return hasRequiredPermissions && hasRequiredRole;
}

export function getRouteBreadcrumb(path: string): string[] {
  return findRouteByPath(path)?.breadcrumb ?? ["未找到页面"];
}

export function getRouteTitle(path: string): string {
  return findRouteByPath(path)?.title ?? "未找到页面";
}
