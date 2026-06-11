import { Select, type SelectProps } from "antd";
import { useMemo, useState } from "react";

import { useOrgUnits, type OrgDirectoryScope, type OrgUnit } from "@/shared/api/hooks";
import { orgLevelLabel } from "@/shared/config/customerLabels";

type OrgUnitSelectProps = Omit<SelectProps<string>, "options" | "filterOption" | "onSearch"> & {
  level?: OrgUnit["level"];
  scope?: OrgDirectoryScope;
  ancestorId?: string;
  valueMode?: "ID" | "PATH";
  onUnitChange?: (unit?: OrgUnit) => void;
};

function matchesScope(unit: OrgUnit, scope?: OrgDirectoryScope) {
  if (!scope) return true;
  if (scope === "BUSINESS_SCOPE") return unit.level !== "PLATFORM";
  return ["TENANT", "REGION", "FACILITY", "CAMPUS"].includes(unit.level);
}

/**
 * 服务端检索的组织选择器，避免把集团完整组织树一次性载入浏览器。
 */
export function OrgUnitSelect({
  level,
  scope,
  ancestorId,
  valueMode = "ID",
  onChange,
  onUnitChange,
  ...selectProps
}: OrgUnitSelectProps) {
  const [keyword, setKeyword] = useState("");
  const query = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
    status: "ACTIVE",
    ...(level ? { level } : {}),
    ...(scope ? { scope } : {}),
    ...(ancestorId ? { ancestorId } : {}),
  });
  const units = useMemo(
    () =>
      (query.data?.items ?? []).filter(
        (unit) =>
          (!level || unit.level === level) &&
          matchesScope(unit, scope) &&
          Boolean(unit.id) &&
          (valueMode === "ID" || Boolean(unit.orgPath)),
      ),
    [level, query.data?.items, scope, valueMode],
  );
  const options = units.flatMap((unit) => {
    const value = valueMode === "PATH" ? unit.orgPath : unit.id;
    return value ? [{ value, label: `${unit.name} · ${orgLevelLabel(unit.level)}` }] : [];
  });

  return (
    <Select
      {...selectProps}
      showSearch
      filterOption={false}
      onSearch={setKeyword}
      options={options}
      loading={query.isLoading}
      notFoundContent={query.isError ? "组织目录读取失败，请稍后重试" : "暂无匹配组织"}
      onChange={(value, option) => {
        const unit = units.find((item) =>
          valueMode === "PATH" ? item.orgPath === value : item.id === value,
        );
        onUnitChange?.(unit);
        onChange?.(value, option);
      }}
    />
  );
}
