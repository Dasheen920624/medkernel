import { describe, expect, it } from "vitest";

import {
  DERIVED_FORMULA_OPTIONS,
  DEFAULT_TEMPORAL_MODE,
  RULE_EXPRESSION_SELECT_OPTIONS,
  TEMPORAL_MODE_OPTIONS,
  isRuleExpressionSelect,
  normalizeTemporalMode,
  parameterKeysForDerivedFormula,
} from "./ruleOperatorCatalog";

describe("ruleOperatorCatalog temporal modes", () => {
  it("统一使用 sustained 作为连续命中的主模式，并拒绝旧 consecutive DSL", () => {
    expect(DEFAULT_TEMPORAL_MODE).toBe("sustained");
    expect(TEMPORAL_MODE_OPTIONS.map((option) => option.value)).toEqual([
      "sustained",
      "trend",
      "frequency",
      "delta",
    ]);
    expect(normalizeTemporalMode("sustained")).toBe("sustained");
    expect(() => normalizeTemporalMode("consecutive")).toThrow("时间窗模式不在受控选项内");
    expect(() => normalizeTemporalMode("legacy-mode")).toThrow("时间窗模式不在受控选项内");
  });

  it("声明计算公式允许范围与参数，包含 BMI", () => {
    expect(DERIVED_FORMULA_OPTIONS.map((option) => option.value)).toEqual([
      "CKD_EPI_2021_EGFR",
      "COCKCROFT_GAULT_CRCL",
      "MOSTELLER_BSA",
      "BMI",
    ]);
    expect(parameterKeysForDerivedFormula("BMI")).toEqual(["heightCm", "weightKg"]);
    expect(parameterKeysForDerivedFormula("UNKNOWN")).toEqual(["creatinine", "age", "sex"]);
  });

  it("声明表达式聚合函数允许范围，拒绝未知 select", () => {
    expect(RULE_EXPRESSION_SELECT_OPTIONS.map((option) => option.value)).toEqual([
      "latest",
      "first",
      "max",
      "min",
      "avg",
      "sum",
      "count",
    ]);
    expect(isRuleExpressionSelect("latest")).toBe(true);
    expect(isRuleExpressionSelect("median")).toBe(false);
  });
});
