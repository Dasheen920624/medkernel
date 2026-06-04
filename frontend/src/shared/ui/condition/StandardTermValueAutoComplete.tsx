/**
 * 标准字典比较值选择器（RULE-01 P5-b）。
 *
 * <p>编码类字段（绑定 codeSystem，如 ICD-10/ATC/LOINC）的比较值从标准字典候选选择，
 * 复用 {@link useStandardTerms} 按字典与关键词检索；同时保留手输（AutoComplete 非破坏）。
 */
import { useState } from "react";
import { AutoComplete } from "antd";

import { useStandardTerms } from "@/shared/api/hooks";

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
  return (
    <AutoComplete
      id={id}
      value={value}
      options={options}
      onSearch={setKeyword}
      onChange={(next) => onChange?.(next)}
      filterOption={false}
      placeholder={`从 ${codeSystem} 标准字典选择或输入编码`}
    />
  );
}

export default StandardTermValueAutoComplete;
