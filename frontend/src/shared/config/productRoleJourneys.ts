import { ROLE_OPTIONS, roleLabel } from "./roleCatalog";

export type ProductRoleCode = (typeof ROLE_OPTIONS)[number]["code"];

export type ProductRoleKind =
  | "operations"
  | "knowledge"
  | "access"
  | "clinical"
  | "clinical-governance"
  | "medication"
  | "diagnostic"
  | "quality"
  | "tenant"
  | "audit";

export type ProductRoleAction = {
  label: string;
  path: string;
};

export type ProductRoleJourney = {
  roleCode: string;
  roleName: string;
  title: string;
  summary: string;
  kind: ProductRoleKind;
  showLifecycle: boolean;
  primaryAction: ProductRoleAction;
  highFrequencyActions: ProductRoleAction[];
};

function roleName(roleCode: string): string {
  return roleLabel(roleCode);
}

function journey(
  roleCode: string,
  config: Omit<ProductRoleJourney, "roleCode" | "roleName" | "title">,
): ProductRoleJourney {
  const name = roleName(roleCode);
  return {
    roleCode,
    roleName: name,
    title: `${name}工作台`,
    ...config,
  };
}

export const PRODUCT_ROLE_JOURNEYS: ProductRoleJourney[] = [
  journey("platform-governance-admin", {
    summary: "管理中枢账号、运行配置、部署验收和关键证据。",
    kind: "tenant",
    showLifecycle: true,
    primaryAction: { label: "管理账号", path: "/admin/users" },
    highFrequencyActions: [
      { label: "安全与运行配置", path: "/security/baseline" },
      { label: "实施与验收", path: "/onboarding/guide" },
      { label: "审计与证据", path: "/admin/audit" },
    ],
  }),
  journey("platform-knowledge-governor", {
    summary: "维护受控来源和医疗知识，完成低中风险审核、发布与追溯。",
    kind: "knowledge",
    showLifecycle: false,
    primaryAction: { label: "审核发布知识", path: "/knowledge/governance" },
    highFrequencyActions: [
      { label: "知识生产与发布", path: "/knowledge/production" },
      { label: "配置包与发布", path: "/config/packages" },
      { label: "来源与血缘", path: "/advanced/provenance" },
    ],
  }),
  journey("integration-operator", {
    summary: "维护系统接入、运行依赖、模型增强和失败恢复。",
    kind: "operations",
    showLifecycle: false,
    primaryAction: { label: "维护接入", path: "/adapter/hub" },
    highFrequencyActions: [
      { label: "运行保障", path: "/system/providers" },
      { label: "安全与运行配置", path: "/security/baseline" },
      { label: "实施与验收", path: "/onboarding/guide" },
    ],
  }),
  journey("compliance-auditor", {
    summary: "只读复核对象、动作、来源、发布时间和导出证据。",
    kind: "audit",
    showLifecycle: false,
    primaryAction: { label: "查看审计证据", path: "/admin/audit" },
    highFrequencyActions: [{ label: "查看来源血缘", path: "/advanced/provenance" }],
  }),
];

const COMPATIBILITY_ROLE_JOURNEYS: ProductRoleJourney[] = [
  journey("organization-admin", {
    summary: "治理本机构、组织、人员与机构级安全策略。",
    kind: "tenant",
    showLifecycle: true,
    primaryAction: { label: "管理服务机构", path: "/tenant/onboarding" },
    highFrequencyActions: [
      { label: "人员与账号", path: "/admin/users" },
      { label: "身份来源", path: "/security/identity-binding" },
      { label: "安全与配置", path: "/security/baseline" },
    ],
  }),
  journey("identity-access-admin", {
    summary: "维护自然人、任职、账号、身份来源、职责角色与组织范围。",
    kind: "access",
    showLifecycle: false,
    primaryAction: { label: "管理人员与账号", path: "/admin/users" },
    highFrequencyActions: [
      { label: "身份来源", path: "/security/identity-binding" },
      { label: "审计与证据", path: "/admin/audit" },
      { label: "安全与配置", path: "/security/baseline" },
    ],
  }),
  journey("knowledge-governor", {
    summary: "创建机构派生知识，审阅差异并发布或恢复平台标准。",
    kind: "knowledge",
    showLifecycle: false,
    primaryAction: { label: "维护机构知识", path: "/knowledge/institution" },
    highFrequencyActions: [
      { label: "知识审核与发布", path: "/knowledge/governance" },
      { label: "配置包与发布", path: "/config/packages" },
      { label: "知识生产", path: "/knowledge/production" },
    ],
  }),
  journey("clinical-governor", {
    summary: "治理规则、路径、高风险提醒和临床协同闭环。",
    kind: "clinical-governance",
    showLifecycle: false,
    primaryAction: { label: "审阅提醒与推荐", path: "/cdss/fatigue" },
    highFrequencyActions: [
      { label: "规则配置", path: "/rule/definitions" },
      { label: "路径配置", path: "/pathway/templates" },
      { label: "协同任务", path: "/workflow/todos" },
    ],
  }),
  journey("clinical-decision-user", {
    summary: "处理患者路径、提醒推荐、协同任务和随访事项。",
    kind: "clinical",
    showLifecycle: false,
    primaryAction: { label: "继续处理协同任务", path: "/workflow/todos" },
    highFrequencyActions: [
      { label: "提醒与推荐", path: "/cdss/fatigue" },
      { label: "患者路径", path: "/pathway/patients" },
      { label: "随访协同", path: "/clinical/followup" },
    ],
  }),
  journey("nursing-collaborator", {
    summary: "推进护理协同任务、患者路径节点和随访异常闭环。",
    kind: "clinical",
    showLifecycle: false,
    primaryAction: { label: "继续处理协同任务", path: "/workflow/todos" },
    highFrequencyActions: [
      { label: "患者路径", path: "/pathway/patients" },
      { label: "随访协同", path: "/clinical/followup" },
      { label: "消息通知", path: "/notifications" },
    ],
  }),
  journey("medication-safety-user", {
    summary: "复核药事高风险规则、提醒依据和人工处置证据。",
    kind: "medication",
    showLifecycle: false,
    primaryAction: { label: "审阅提醒与推荐", path: "/cdss/fatigue" },
    highFrequencyActions: [
      { label: "规则配置", path: "/rule/definitions" },
      { label: "知识审核与发布", path: "/knowledge/governance" },
      { label: "患者路径", path: "/pathway/patients" },
    ],
  }),
  journey("diagnostic-service-user", {
    summary: "维护医技术语映射并处理患者与协同任务。",
    kind: "diagnostic",
    showLifecycle: false,
    primaryAction: { label: "维护术语与字典", path: "/terminology/mapping" },
    highFrequencyActions: [
      { label: "患者索引", path: "/mpi" },
      { label: "协同任务", path: "/workflow/todos" },
      { label: "消息通知", path: "/notifications" },
    ],
  }),
  journey("quality-governor", {
    summary: "发现质量与医保问题，推进整改、复核与评价闭环。",
    kind: "quality",
    showLifecycle: false,
    primaryAction: { label: "处理质量问题与整改", path: "/qc/alerts" },
    highFrequencyActions: [
      { label: "模型生产控制台", path: "/knowledge/production" },
      { label: "医保审核", path: "/qc/insurance" },
      { label: "评价指标", path: "/qc/eval/sets" },
    ],
  }),
  journey("implementation-operator", {
    summary: "完成机构开通、批量初始化、联调和交付验收。",
    kind: "tenant",
    showLifecycle: true,
    primaryAction: { label: "继续实施与验收", path: "/onboarding/guide" },
    highFrequencyActions: [
      { label: "服务机构", path: "/tenant/onboarding" },
      { label: "模型生产控制台", path: "/knowledge/production" },
      { label: "系统接入", path: "/adapter/hub" },
    ],
  }),
];

export function findProductRoleJourney(roleCode?: string | null): ProductRoleJourney | undefined {
  return [...PRODUCT_ROLE_JOURNEYS, ...COMPATIBILITY_ROLE_JOURNEYS].find(
    (journey) => journey.roleCode === roleCode,
  );
}
