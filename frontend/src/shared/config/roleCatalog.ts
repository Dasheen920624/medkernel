export const ROLE_OPTIONS = [
  { code: "platform-admin", name: "平台管理员" },
  { code: "engine-operator", name: "医疗引擎运营员" },
  { code: "clinical-user", name: "临床使用者" },
  { code: "auditor", name: "审计员" },
] as const;

export type ProductRoleCode = (typeof ROLE_OPTIONS)[number]["code"];

export const KNOWN_ROLE_CODES = ROLE_OPTIONS.map((role) => role.code);

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
  return ROLE_OPTIONS.find((role) => role.code === normalized)?.name ?? "角色待确认";
}
