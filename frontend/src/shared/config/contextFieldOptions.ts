/**
 * 上下文字段选择器选项构建（RULE-01 / PATH-01）。
 *
 * <p>把字段目录按资源类型分组（患者 / 检验体征 / 诊断 / 用药 / 就诊…），并生成带字典提示的
 * 选项标签，供规则条件与路径守卫的字段选择器使用，提升「易用 / 简洁」。
 */
import type { ContextFieldDescriptor } from "@/shared/api/hooks";

const RESOURCE_LABELS: Record<string, string> = {
  Patient: "患者",
  Observation: "检验 / 体征",
  Condition: "诊断",
  Medication: "用药",
  Encounter: "就诊",
};

export interface FieldOption {
  value: string;
  label: string;
}

export interface FieldOptionGroup {
  label: string;
  options: FieldOption[];
}

/** 单个字段的选项标签：含字段路径，编码字段追加绑定字典。 */
export function fieldOptionLabel(field: ContextFieldDescriptor): string {
  const base = `${field.displayName}（${field.fieldPath}）`;
  return field.codeSystem ? `${base} · 字典 ${field.codeSystem}` : base;
}

/** 按资源类型分组的字段选项（供 AutoComplete / Select 的分组下拉）。 */
export function buildFieldCatalogOptions(fields: ContextFieldDescriptor[]): FieldOptionGroup[] {
  const groups = new Map<string, FieldOption[]>();
  for (const field of fields) {
    const option: FieldOption = { value: field.fieldPath, label: fieldOptionLabel(field) };
    const existing = groups.get(field.resourceType);
    if (existing) {
      existing.push(option);
    } else {
      groups.set(field.resourceType, [option]);
    }
  }
  return Array.from(groups.entries()).map(([resourceType, options]) => ({
    label: RESOURCE_LABELS[resourceType] ?? resourceType,
    options,
  }));
}
