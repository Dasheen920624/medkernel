/**
 * 标准字典比较值选择器（RULE-01 P5-b）。
 *
 * <p>编码类字段（绑定 codeSystem，如 ICD-10/ATC/LOINC）的比较值从标准字典候选选择，
 * 复用 {@link useStandardTerms} 按字典与关键词检索；同时保留手输（AutoComplete 非破坏）。
 */
import { useMemo, useState } from "react";
import { AutoComplete } from "antd";

import { useMappingCoverage, useStandardTerms } from "@/shared/api/hooks";

export interface StandardTermValueAutoCompleteProps {
  /** 绑定的标准字典/编码系统（如 ICD-10）。 */
  codeSystem: string;
  value?: string;
  onChange?: (value: string) => void;
  id?: string;
}

export function StandardTermValueAutoComplete({
  codeSystem,
  value,
  onChange,
  id,
}: StandardTermValueAutoCompleteProps) {
  const [keyword, setKeyword] = useState("");
  const { data } = useStandardTerms({
    standardSystem: codeSystem,
    keyword: keyword || undefined,
    status: "ACTIVE",
    size: 20,
  });
  const options = (data?.items ?? []).map((term) => ({
    value: term.termCode,
    label: `${term.displayName}（${term.termCode}）`,
  }));

  // 对照覆盖提示（advisory）：已填编码若缺院内→标准对照，命中时真实数据可能归一不到。
  const codes = useMemo(() => (value && value.trim() ? [value.trim()] : []), [value]);
  const coverageQuery = useMappingCoverage({ standardSystem: codeSystem, codes });
  const coverageWarning = (coverageQuery.data ?? []).find(
    (item) => item.status === "UNMAPPED" || item.status === "NO_STANDARD_TERM",
  );
  const buildWarning = (): string | null => {
    if (!coverageWarning) return null;
    if (coverageWarning.status === "NO_STANDARD_TERM") {
      return `编码「${coverageWarning.code}」不在 ${codeSystem} 标准字典内`;
    }
    return `编码「${coverageWarning.code}」尚无院内→标准对照，真实数据可能无法命中`;
  };
  const warningText = buildWarning();

  return (
    <div>
      <AutoComplete
        id={id}
        value={value}
        options={options}
        onSearch={setKeyword}
        onChange={(next) => onChange?.(next)}
        filterOption={false}
        placeholder={`从 ${codeSystem} 标准字典选择或输入编码`}
      />
      {warningText && (
        <div role="alert" className="text-xs text-amber-600 mt-1">
          ⚠ {warningText}
        </div>
      )}
    </div>
  );
}

export default StandardTermValueAutoComplete;
