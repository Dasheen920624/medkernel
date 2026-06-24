import { describe, expect, it } from "vitest";

import {
  ACTION_CARD_ACTION_OPTIONS,
  ACTION_CARD_INDICATOR_OPTIONS,
  ACTION_CARD_RISK_LEVEL_OPTIONS,
  DECLARATIVE_ASSET_TYPE_OPTIONS,
  DECLARATIVE_FORMULA_OPTIONS,
  ORDER_SET_ITEM_TYPE_OPTIONS,
} from "./declarativeAssetAuthoring";

describe("声明式医疗资产编著配置", () => {
  it("只暴露可独立编著的声明式医疗资产", () => {
    expect(DECLARATIVE_ASSET_TYPE_OPTIONS.map((option) => option.label)).toEqual([
      "值集",
      "公式与量表",
      "医嘱套餐",
      "临床提示卡",
    ]);
  });

  it("把运行契约值翻译成前台可理解的医学配置语言", () => {
    expect(DECLARATIVE_FORMULA_OPTIONS).toContainEqual({
      value: "CKD_EPI_2021_EGFR",
      label: "eGFR（CKD-EPI 2021）",
    });
    expect(ORDER_SET_ITEM_TYPE_OPTIONS).toContainEqual({
      value: "LAB",
      label: "检验项目",
    });
    expect(ACTION_CARD_ACTION_OPTIONS).toContainEqual({
      value: "BLOCK",
      label: "红线拦截",
    });
    expect(ACTION_CARD_RISK_LEVEL_OPTIONS).toContainEqual({
      value: "CRITICAL",
      label: "红线风险",
    });
    expect(ACTION_CARD_INDICATOR_OPTIONS).toContainEqual({
      value: "critical",
      label: "必须处理",
    });
  });
});
