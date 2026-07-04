import { roleLabel, type ProductRoleCode } from "./roleCatalog";

export type { ProductRoleCode } from "./roleCatalog";

export type ProductRoleKind = "operations" | "knowledge" | "clinical" | "audit";

export type ProductRoleAction = {
  label: string;
  path: string;
};

export type ProductRoleJourney = {
  roleCode: ProductRoleCode;
  roleName: string;
  title: string;
  summary: string;
  kind: ProductRoleKind;
  showLifecycle: boolean;
  primaryAction: ProductRoleAction;
  highFrequencyActions: ProductRoleAction[];
};

function journey(
  roleCode: ProductRoleCode,
  config: Omit<ProductRoleJourney, "roleCode" | "roleName" | "title">,
): ProductRoleJourney {
  const roleName = roleLabel(roleCode);
  return {
    roleCode,
    roleName,
    title: `${roleName}工作台`,
    ...config,
  };
}

export const PRODUCT_ROLE_JOURNEYS: ProductRoleJourney[] = [
  journey("platform-admin", {
    summary: "开通服务机构、人员账号、系统接入、运行配置和上线验收。",
    kind: "operations",
    showLifecycle: true,
    primaryAction: { label: "维护人员与账号", path: "/admin/users" },
    highFrequencyActions: [
      { label: "安全与配置", path: "/security/baseline" },
      { label: "实施与验收", path: "/onboarding/guide" },
      { label: "系统接入", path: "/adapter/hub" },
    ],
  }),
  journey("engine-operator", {
    summary: "运营医疗知识、规则、路径、知识生产和质量闭环。",
    kind: "knowledge",
    showLifecycle: false,
    primaryAction: { label: "进入知识生产", path: "/knowledge/production" },
    highFrequencyActions: [
      { label: "知识审核发布中心", path: "/knowledge/governance" },
      { label: "质量问题与整改", path: "/qc/alerts" },
      { label: "来源与血缘", path: "/advanced/provenance" },
    ],
  }),
  journey("clinical-user", {
    summary: "处理患者路径、提醒推荐、协同任务和随访事项。",
    kind: "clinical",
    showLifecycle: false,
    primaryAction: { label: "处理协同任务", path: "/workflow/todos" },
    highFrequencyActions: [
      { label: "患者路径", path: "/pathway/patients" },
      { label: "提醒与推荐", path: "/cdss/fatigue" },
      { label: "随访协同", path: "/clinical/followup" },
    ],
  }),
  journey("auditor", {
    summary: "只读核查对象、动作、来源、发布时间和导出证据。",
    kind: "audit",
    showLifecycle: false,
    primaryAction: { label: "查看审计证据", path: "/admin/audit" },
    highFrequencyActions: [
      { label: "查看来源血缘", path: "/advanced/provenance" },
      { label: "查看安全配置", path: "/security/baseline" },
    ],
  }),
];

export function findProductRoleJourney(roleCode?: string | null): ProductRoleJourney | undefined {
  return PRODUCT_ROLE_JOURNEYS.find((journey) => journey.roleCode === roleCode);
}
