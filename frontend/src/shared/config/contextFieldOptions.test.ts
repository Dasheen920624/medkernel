import { describe, expect, test } from "vitest";

import type { ContextFieldDescriptor } from "@/shared/api/hooks";
import { buildFieldCatalogOptions, fieldOptionLabel } from "@/shared/config/contextFieldOptions";

const baseField: ContextFieldDescriptor = {
  category: "基本信息",
  group: "患者基本信息",
  resourceType: "Patient",
  fieldPath: "patient.age",
  displayName: "年龄",
  dataType: "number",
};

describe("fieldOptionLabel", () => {
  test("派生字段标签追加「派生」标记，提示该字段是算出来的", () => {
    const label = fieldOptionLabel({ ...baseField, derived: true });
    expect(label).toContain("年龄");
    expect(label).toContain("patient.age");
    expect(label).toContain("派生");
  });

  test("原始存储字段标签不含「派生」标记", () => {
    const label = fieldOptionLabel({
      ...baseField,
      fieldPath: "patient.birthDate",
      displayName: "出生日期",
      dataType: "date",
      derived: false,
    });
    expect(label).not.toContain("派生");
  });

  test("编码字段标签追加绑定字典名", () => {
    const label = fieldOptionLabel({
      ...baseField,
      fieldPath: "conditions[].code",
      displayName: "诊断编码",
      dataType: "code",
      codeSystem: "ICD-10",
    });
    expect(label).toContain("字典 ICD-10");
  });
});

describe("buildFieldCatalogOptions", () => {
  test("按一级业务域分组，派生与原始字段同域并列", () => {
    const groups = buildFieldCatalogOptions([
      { ...baseField, fieldPath: "patient.birthDate", displayName: "出生日期", dataType: "date" },
      { ...baseField, derived: true },
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0].label).toBe("基本信息");
    expect(groups[0].options.map((o) => o.value)).toEqual(["patient.birthDate", "patient.age"]);
  });
});
