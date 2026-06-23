import { describe, expect, it } from "vitest";

import {
  AUTHORING_ASSET_TYPES,
  ENGINE_ASSET_TYPES,
  KNOWLEDGE_DOMAIN_OPTIONS,
  KNOWLEDGE_DOMAINS,
  RELEASE_ASSET_TYPES,
} from "./assetCatalog";

describe("医疗内容与版本资产单一目录", () => {
  it("知识目录恰好包含十一内容域且不混入患者报告解读", () => {
    expect(KNOWLEDGE_DOMAINS).toEqual([
      "GUIDELINE",
      "DRUG",
      "PATHWAY_KNOWLEDGE",
      "NURSING",
      "DIAGNOSTIC_ITEM",
      "TCM",
      "PROTOCOL",
      "POLICY",
      "LITERATURE",
      "OTHER",
      "DIAGNOSIS",
    ]);
    expect(KNOWLEDGE_DOMAIN_OPTIONS.map((option) => option.value)).toEqual(KNOWLEDGE_DOMAINS);
    expect(KNOWLEDGE_DOMAINS).not.toContain("REPORT");
    expect(KNOWLEDGE_DOMAINS).not.toContain("REPORT_INTERPRETATION");
  });

  it("版本资产目录只保留十三类运行资产且不含旧容器和条件片段", () => {
    expect(ENGINE_ASSET_TYPES).toHaveLength(13);
    expect(ENGINE_ASSET_TYPES).not.toContain("PACKAGE");
    expect(ENGINE_ASSET_TYPES).not.toContain("RECOMMENDATION");
    expect(ENGINE_ASSET_TYPES).toContain("FORMULA");
    expect(ENGINE_ASSET_TYPES).not.toContain("CONDITION_FRAGMENT");
    expect(new Set(RELEASE_ASSET_TYPES)).toEqual(new Set(ENGINE_ASSET_TYPES));
  });

  it("统一编著库只列出已有真实工作台且不重复维护条件语法", () => {
    expect(AUTHORING_ASSET_TYPES).toEqual([
      "RULE",
      "PATHWAY",
      "FOLLOWUP",
    ]);
  });
});
