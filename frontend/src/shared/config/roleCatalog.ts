export const ROLE_OPTIONS = [
  { code: "platform-governance-admin", name: "平台治理管理员" },
  { code: "platform-knowledge-governor", name: "平台知识治理员" },
  { code: "organization-admin", name: "机构管理员" },
  { code: "identity-access-admin", name: "人员与访问管理员" },
  { code: "knowledge-governor", name: "机构知识治理员" },
  { code: "clinical-governor", name: "临床治理负责人" },
  { code: "clinical-decision-user", name: "临床决策使用者" },
  { code: "nursing-collaborator", name: "护理协同人员" },
  { code: "medication-safety-user", name: "药事安全人员" },
  { code: "diagnostic-service-user", name: "医技协同人员" },
  { code: "quality-governor", name: "质量与医保治理员" },
  { code: "compliance-auditor", name: "合规审计员" },
  { code: "integration-operator", name: "集成运维员" },
  { code: "implementation-operator", name: "实施运维员" },
] as const;

export const SCOPE_LEVEL_OPTIONS = [
  { code: "TENANT", name: "服务机构全域" },
  { code: "REGION", name: "集团或区域" },
  { code: "FACILITY", name: "医疗服务机构" },
  { code: "CAMPUS", name: "院区或分院" },
  { code: "DEPARTMENT", name: "科室" },
  { code: "WARD", name: "病区" },
] as const;

export function roleLabel(value?: string | null) {
  if (!value) return "未设置角色";
  const normalized = value
    .replace(/^ROLE_/i, "")
    .toLowerCase()
    .replace(/_/g, "-");
  return ROLE_OPTIONS.find((role) => role.code === normalized)?.name ?? "未识别角色";
}
