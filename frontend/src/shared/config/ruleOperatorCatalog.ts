export const RULE_OPERATOR_OPTIONS = [
  { value: "exists", label: "存在" },
  { value: "equals", label: "等于" },
  { value: "not_equals", label: "不等于" },
  { value: "contains", label: "包含" },
  { value: "gt", label: "大于" },
  { value: "gte", label: "大于等于" },
  { value: "lt", label: "小于" },
  { value: "lte", label: "小于等于" },
  { value: "in", label: "属于集合" },
  { value: "not_in", label: "不属于集合" },
  { value: "between", label: "区间比较" },
  { value: "not_between", label: "不在区间" },
  { value: "within_ref", label: "参考范围内" },
  { value: "above_ref", label: "高于参考上限" },
  { value: "below_ref", label: "低于参考下限" },
  { value: "is_missing", label: "临床缺值" },
  { value: "is_critical", label: "危急值标记" },
  { value: "is_stale", label: "结果陈旧" },
  { value: "unit_compare", label: "单位换算比较" },
  { value: "temporal", label: "时间窗/连续/趋势" },
  { value: "derived", label: "计算公式" },
] as const;

export type RuleOperator = (typeof RULE_OPERATOR_OPTIONS)[number]["value"];

export const TEMPORAL_MODE_OPTIONS = [
  { value: "sustained", label: "持续命中" },
  { value: "trend", label: "趋势判断" },
  { value: "frequency", label: "频次统计" },
  { value: "delta", label: "首末差值" },
] as const;

export type RuleTemporalMode = (typeof TEMPORAL_MODE_OPTIONS)[number]["value"];

export const DEFAULT_TEMPORAL_MODE: RuleTemporalMode = "sustained";

export const DERIVED_FORMULA_OPTIONS = [
  { value: "CKD_EPI_2021_EGFR", label: "eGFR CKD-EPI 2021" },
  { value: "COCKCROFT_GAULT_CRCL", label: "CrCl Cockcroft-Gault" },
  { value: "MOSTELLER_BSA", label: "BSA Mosteller" },
  { value: "BMI", label: "BMI 体重指数" },
] as const;

export type DerivedFormula = (typeof DERIVED_FORMULA_OPTIONS)[number]["value"];

export const RULE_EXPRESSION_SELECT_OPTIONS = [
  { value: "latest", label: "最近一次" },
  { value: "first", label: "最早一次" },
  { value: "max", label: "最大值" },
  { value: "min", label: "最小值" },
  { value: "avg", label: "平均值" },
  { value: "sum", label: "求和" },
  { value: "count", label: "计数" },
] as const;

export type RuleExpressionSelect = (typeof RULE_EXPRESSION_SELECT_OPTIONS)[number]["value"];

export const RULE_OPERATORS = RULE_OPERATOR_OPTIONS.map((option) => option.value) as RuleOperator[];

export const RULE_OPERATOR_LABELS = Object.fromEntries(
  RULE_OPERATOR_OPTIONS.map((option) => [option.value, option.label]),
) as Record<RuleOperator, string>;

export const RULE_VALUE_KIND_OPTIONS = [
  { value: "empty", label: "无比较值" },
  { value: "string", label: "文本" },
  { value: "number", label: "数值" },
  { value: "boolean", label: "布尔" },
  { value: "list", label: "集合" },
  { value: "range", label: "区间" },
  { value: "measurement", label: "带单位阈值" },
  { value: "temporal", label: "时间窗" },
  { value: "derived", label: "计算公式" },
  { value: "critical_flag", label: "危急标记集合" },
  { value: "staleness", label: "结果时效" },
] as const;

export type RuleValueKind = (typeof RULE_VALUE_KIND_OPTIONS)[number]["value"];

export const RULE_VALUE_KIND_LABELS = Object.fromEntries(
  RULE_VALUE_KIND_OPTIONS.map((option) => [option.value, option.label]),
) as Record<RuleValueKind, string>;

export const CLINICAL_RULE_OPERATORS: RuleOperator[] = [
  "between",
  "not_between",
  "within_ref",
  "above_ref",
  "below_ref",
  "is_missing",
  "is_critical",
  "is_stale",
  "unit_compare",
  "temporal",
  "derived",
];

const NO_VALUE_RULE_OPERATORS = new Set<RuleOperator>([
  "exists",
  "within_ref",
  "above_ref",
  "below_ref",
  "is_missing",
]);

const STRUCTURED_VALUE_KINDS = new Set<RuleValueKind>([
  "range",
  "measurement",
  "temporal",
  "derived",
  "critical_flag",
  "staleness",
]);

const TEMPORAL_MODE_VALUES = new Set<string>(TEMPORAL_MODE_OPTIONS.map((option) => option.value));
const EXPRESSION_SELECT_VALUES = new Set<string>(
  RULE_EXPRESSION_SELECT_OPTIONS.map((option) => option.value),
);

export function isRuleOperator(value: string): value is RuleOperator {
  return RULE_OPERATORS.includes(value as RuleOperator);
}

export function normalizeTemporalMode(value: unknown): RuleTemporalMode {
  if (typeof value === "string" && TEMPORAL_MODE_VALUES.has(value)) {
    return value as RuleTemporalMode;
  }
  throw new Error("时间窗模式不在受控选项内");
}

export function isRuleExpressionSelect(value: string): value is RuleExpressionSelect {
  return EXPRESSION_SELECT_VALUES.has(value);
}

export function parameterKeysForDerivedFormula(formula: string): string[] {
  if (formula === "BMI" || formula === "MOSTELLER_BSA") {
    return ["heightCm", "weightKg"];
  }
  if (formula === "COCKCROFT_GAULT_CRCL") {
    return ["creatinine", "age", "sex", "weightKg"];
  }
  return ["creatinine", "age", "sex"];
}

export function isClinicalRuleOperator(operator: RuleOperator): boolean {
  return CLINICAL_RULE_OPERATORS.includes(operator);
}

export function operatorNeedsValue(operator: RuleOperator): boolean {
  return !NO_VALUE_RULE_OPERATORS.has(operator);
}

export function defaultValueKindForOperator(
  operator: RuleOperator,
  currentKind: RuleValueKind,
): RuleValueKind {
  if (!operatorNeedsValue(operator)) return "empty";
  if (operator === "between" || operator === "not_between") return "range";
  if (operator === "unit_compare") return "measurement";
  if (operator === "temporal") return "temporal";
  if (operator === "derived") return "derived";
  if (operator === "is_critical") return "critical_flag";
  if (operator === "is_stale") return "staleness";
  if (currentKind === "empty" || STRUCTURED_VALUE_KINDS.has(currentKind)) return "string";
  return currentKind;
}

export function inferRuleValueKind(value: unknown): RuleValueKind {
  if (typeof value === "number") return "number";
  if (typeof value === "boolean") return "boolean";
  if (Array.isArray(value)) return "list";
  if (value === undefined || value === null) return "empty";
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    if ("criticalValues" in record) return "critical_flag";
    if ("maxAge" in record || "referenceTime" in record) return "staleness";
    if ("formula" in record || "parameters" in record) return "derived";
    if ("mode" in record || "window" in record) return "temporal";
    if ("analyte" in record || "comparison" in record) return "measurement";
    if ("min" in record || "max" in record) return "range";
  }
  return "string";
}
