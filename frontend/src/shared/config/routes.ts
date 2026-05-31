/**
 * 路由元数据（single source of truth）。
 * AppLayout + router + 菜单 + 面包屑 + 权限元数据全部读这里。
 */
import type { RouteExperience } from "@/shared/ui/experienceTypes";

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
  primaryRole: "实施工程师 / 信息科 / 医务处",
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
  evidence: "详情抽屉展示证据文本、确认人、确认时间和审计入口",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "disabled" },
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
    experience: readonlyExperience(
      "医院管理者",
      "查看当前运行状态和需要跟进的事项",
      "当前重点事项",
    ),
    pageType: "workbench",
  },
  {
    path: "/onboarding/guide",
    title: "客户实施向导",
    breadcrumb: ["试点准备", "客户实施向导"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "implementation-guide",
    menuLabel: "客户实施向导",
    experience: readonlyExperience("实施工程师", "按步骤完成试点准备核查", "待完成步骤"),
    pageType: "configuration",
    stateMachine: "config",
  },
  {
    path: "/tenant/onboarding",
    title: "租户开通",
    breadcrumb: ["试点准备", "租户开通"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "tenant-onboarding",
    menuLabel: "租户开通",
    experience: readonlyExperience("实施工程师", "核查租户开通准备状态", "待配置组织"),
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
    path: "/pathway/templates",
    title: "路径配置",
    breadcrumb: ["试点准备", "路径配置"],
    requireAuth: true,
    sectionKey: "pilot-setup",
    menuKey: "pathway-templates",
    menuLabel: "路径配置",
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
    experience: readonlyExperience("信息科", "核查院内系统适配状态", "异常连接"),
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
    experience: readonlyExperience("质控办", "查看评估结果和待改进事项", "近期结果", "large"),
    pageType: "list",
  },
  {
    path: "/aik/review",
    title: "AI 知识审核",
    breadcrumb: ["质控改进", "AI 知识审核"],
    requireAuth: true,
    sectionKey: "quality-improve",
    menuKey: "aik-review",
    menuLabel: "AI 知识审核",
    experience: readonlyExperience("医务处", "审核知识候选及其依据", "待我审核", "large"),
    pageType: "review",
    stateMachine: "config",
  },
  {
    path: "/admin/users",
    title: "用户管理",
    breadcrumb: ["合规运维", "用户管理"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "admin-users",
    menuLabel: "用户管理",
    experience: readonlyExperience("信息科", "核查系统用户与权限状态", "有效用户", "large"),
    pageType: "list",
  },
  {
    path: "/security/identity-binding",
    title: "身份绑定",
    breadcrumb: ["合规运维", "身份绑定"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "identity-bindings",
    menuLabel: "身份绑定",
    experience: readonlyExperience("信息科", "核查身份绑定运行状态", "待确认绑定"),
    pageType: "system",
  },
  {
    path: "/admin/audit",
    title: "审计日志",
    breadcrumb: ["合规运维", "审计日志"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "admin-audit",
    menuLabel: "审计日志",
    experience: readonlyExperience("审计人员", "追溯关键操作证据", "最近事件", "massive"),
    pageType: "list",
  },
  {
    path: "/security/baseline",
    title: "安全基线",
    breadcrumb: ["合规运维", "安全基线"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "security-baseline",
    menuLabel: "安全基线",
    experience: readonlyExperience("信息科", "查看安全配置基线状态", "当前基线"),
    pageType: "dashboard",
  },
  {
    path: "/system/providers",
    title: "Provider 状态",
    breadcrumb: ["合规运维", "Provider 状态"],
    requireAuth: true,
    sectionKey: "compliance-ops",
    menuKey: "system-providers",
    menuLabel: "Provider 状态",
    experience: readonlyExperience("运维人员", "核查依赖服务运行状态", "异常优先"),
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
    experience: readonlyExperience("信息科", "核查通知策略配置状态", "当前配置"),
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
    experience: readonlyExperience("高级实施人员", "核查知识关系查询结果", "最近查询", "large"),
    hidden: true,
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
    experience: readonlyExperience("AI 团队", "核查工作流配置和运行状态", "最近运行", "large"),
    hidden: true,
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

export function sectionPermissionCode(sectionKey: RouteSectionKey): string {
  return `menu.${sectionKey}`;
}

function normalizeRouteMeta(route: RouteMetaInput): RouteMeta {
  const requiredPermissions =
    route.requiredPermissions ??
    (route.requireAuth ? [sectionPermissionCode(route.sectionKey ?? "workbench")] : []);

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
  const grantedRoles = new Set(profile.roles?.map((role) => role.code) ?? []);
  const hasRequiredPermissions = route.requiredPermissions.every((permission) =>
    grantedPermissions.has(permission),
  );
  const hasRequiredRole =
    route.requiredRoles.length === 0 || route.requiredRoles.some((role) => grantedRoles.has(role));

  return hasRequiredPermissions && hasRequiredRole;
}

export function getRouteBreadcrumb(path: string): string[] {
  return findRouteByPath(path)?.breadcrumb ?? ["未找到页面"];
}

export function getRouteTitle(path: string): string {
  return findRouteByPath(path)?.title ?? "未找到页面";
}
