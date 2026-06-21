export const ROLE_OPTIONS = [
  { code: "platform-governance-admin", name: "平台管理员" },
  { code: "platform-knowledge-governor", name: "知识运营员" },
  { code: "integration-operator", name: "接入运维员" },
  { code: "compliance-auditor", name: "审计查看员" },
] as const;

const COMPATIBILITY_ROLE_LABELS: Record<string, string> = {
  "organization-admin": "机构管理员",
  "identity-access-admin": "人员与访问管理员",
  "knowledge-governor": "机构知识治理员",
  "clinical-governor": "临床治理负责人",
  "clinical-decision-user": "临床决策使用者",
  "nursing-collaborator": "护理协同人员",
  "medication-safety-user": "药事安全人员",
  "diagnostic-service-user": "医技协同人员",
  "quality-governor": "质量与医保治理员",
  "implementation-operator": "实施运维员",
};

export const KNOWN_ROLE_CODES = [
  ...ROLE_OPTIONS.map((role) => role.code),
  ...Object.keys(COMPATIBILITY_ROLE_LABELS),
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
  return (
    ROLE_OPTIONS.find((role) => role.code === normalized)?.name ??
    COMPATIBILITY_ROLE_LABELS[normalized] ??
    "未识别角色"
  );
}
