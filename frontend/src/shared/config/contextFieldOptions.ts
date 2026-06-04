/**
 * 上下文字段选择器选项构建（RULE-01 / PATH-01）。
 *
 * <p>按一级业务域分组（基本信息 / 诊断信息 / 医嘱信息 / 检验检查…），二级分组与字典提示
 * 体现在选项标签，供规则条件与路径守卫的字段选择器使用，贴合临床认知、提升易用简洁。
 */
import type { ContextFieldDescriptor } from "@/shared/api/hooks";

export interface FieldOption {
  value: string;
  label: string;
}

export interface FieldOptionGroup {
  label: string;
  options: FieldOption[];
}

/**
 * 单个字段的选项标签：二级业务分组 + 中文名 + 字段路径，编码字段追加绑定字典。
 * 例：「用药医嘱 · 药品编码（medications[].code）· 字典 ATC」。
 */
export function fieldOptionLabel(field: ContextFieldDescriptor): string {
  const base = `${field.group} · ${field.displayName}（${field.fieldPath}）`;
  return field.codeSystem ? `${base} · 字典 ${field.codeSystem}` : base;
}

/**
 * 按一级业务域（category）分组的字段选项，二级分组（group）体现在选项标签里，
 * 形成「业务域 → 分组 → 字段」层级，贴合临床认知；保留手输（AutoComplete）。
 */
export function buildFieldCatalogOptions(fields: ContextFieldDescriptor[]): FieldOptionGroup[] {
  const groups = new Map<string, FieldOption[]>();
  for (const field of fields) {
    const option: FieldOption = { value: field.fieldPath, label: fieldOptionLabel(field) };
    const existing = groups.get(field.category);
    if (existing) {
      existing.push(option);
    } else {
      groups.set(field.category, [option]);
    }
  }
  return Array.from(groups.entries()).map(([category, options]) => ({
    label: category,
    options,
  }));
}
