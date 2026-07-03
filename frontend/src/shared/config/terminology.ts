import type { TermCategory } from "@/shared/api/hooks";

export const TERM_CATEGORY_LABELS = {
  DIAGNOSIS: "诊断",
  PROCEDURE: "处置/手术",
  DRUG: "药品",
  DEVICE: "器械",
  LAB: "检验项目",
  EXAM: "检查项目",
  ORDER: "医嘱",
  INSURANCE: "医保",
  DEPARTMENT: "科室",
  DOCUMENT: "文书",
  FOLLOWUP: "随访",
  OTHER: "其他",
} as const satisfies Readonly<Record<TermCategory, string>>;

export const TERM_CATEGORY_OPTIONS = Object.entries(TERM_CATEGORY_LABELS).map(([value, label]) => ({
  value: value as TermCategory,
  label,
}));
