export const ROLE_OPTIONS = [
  { code: "platform-admin", name: "平台管理员" },
  { code: "group-admin", name: "集团管理员" },
  { code: "hospital-admin", name: "医院管理员" },
  { code: "it-ops", name: "信息科" },
  { code: "medical-affairs", name: "医务处" },
  { code: "qa-manager", name: "质控办" },
  { code: "insurance-manager", name: "医保办" },
  { code: "dept-head", name: "科主任" },
  { code: "specialist", name: "专科专家" },
  { code: "doctor", name: "临床医生" },
  { code: "nurse", name: "护理人员" },
  { code: "audit-compliance", name: "合规审计" },
  { code: "implementation-engineer", name: "实施工程师" },
] as const;

export const SCOPE_LEVEL_OPTIONS = [
  { code: "TENANT", name: "租户级 (TENANT)" },
  { code: "HOSPITAL", name: "医院级 (HOSPITAL)" },
  { code: "CAMPUS", name: "院区级 (CAMPUS)" },
  { code: "DEPARTMENT", name: "科室级 (DEPARTMENT)" },
] as const;
