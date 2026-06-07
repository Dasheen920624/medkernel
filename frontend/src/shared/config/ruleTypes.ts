export const RULE_TYPE_OPTIONS = [
  { value: "DIAGNOSIS", label: "诊断规则" },
  { value: "ORDER", label: "医嘱与合理用药" },
  { value: "LAB", label: "检验规则" },
  { value: "REPORT", label: "报告规则" },
  { value: "DISCHARGE", label: "出院规则" },
  { value: "FOLLOWUP", label: "随访规则" },
  { value: "INSURANCE", label: "医保规则" },
  { value: "QUALITY", label: "临床质控" },
  { value: "RECORD", label: "病历规则" },
  { value: "PATHWAY", label: "路径规则" },
] as const;

export type RuleType = (typeof RULE_TYPE_OPTIONS)[number]["value"];

export const RULE_TYPE_LABELS: Record<RuleType, string> = Object.fromEntries(
  RULE_TYPE_OPTIONS.map(({ value, label }) => [value, label]),
) as Record<RuleType, string>;
