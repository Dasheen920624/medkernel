import { describe, expect, it } from "vitest";

import { createGroup, createLeaf, countLeaves } from "./conditionModel";
import {
  RULE_ARCHETYPES,
  buildExplanation,
  buildRuleDsl,
  instantiateArchetype,
  parseRuleDsl,
  type RuleDraft,
} from "./ruleDsl";

describe("RULE-01 规则 DSL 桥接（P1-2）", () => {
  it("装配嵌套规则 DSL：when/then/explain", () => {
    const draft: RuleDraft = {
      triggerPoint: "patient-view",
      root: createGroup({
        logic: "all",
        children: [
          createLeaf({ fact: "patient.age", operator: "gte", value: 65, valueKind: "number" }),
          createGroup({
            logic: "any",
            children: [
              createLeaf({ fact: "a", operator: "gt", value: 1, valueKind: "number" }),
              createLeaf({ fact: "b", operator: "exists", valueKind: "empty" }),
            ],
          }),
        ],
      }),
      action: {
        actionCode: "BLOCK",
        severity: "HIGH",
        message: "阻断",
        requiresPhysicianConfirmation: true,
      },
      explanationSummary: "测试摘要",
    };

    const dsl = buildRuleDsl(draft) as {
      trigger: string;
      when: { all: unknown[] };
      then: Array<{ actionCode: string }>;
      explain: { summary: string; authoring: { conditionCount: number } };
    };
    expect(dsl.trigger).toBe("patient-view");
    expect(dsl.when.all).toHaveLength(2);
    expect(dsl.then[0].actionCode).toBe("BLOCK");
    expect(dsl.explain.summary).toBe("测试摘要");
    expect(dsl.explain.authoring.conditionCount).toBe(3);
  });

  it("DSL 往返还原草稿无损", () => {
    const draft = instantiateArchetype("clinical_quality_monitor");
    draft.root = createGroup({
      logic: "all",
      children: [
        createLeaf({ fact: "x", operator: "gte", value: 5, valueKind: "number" }),
        createGroup({
          logic: "any",
          children: [createLeaf({ fact: "y", operator: "exists", valueKind: "empty" })],
        }),
      ],
    });

    const parsed = parseRuleDsl(buildRuleDsl(draft));
    expect(countLeaves(parsed.root)).toBe(countLeaves(draft.root));
    expect(parsed.action.actionCode).toBe(draft.action.actionCode);
    expect(parsed.explanationSummary).toBe(draft.explanationSummary);
  });

  it("拒绝旧 when 包裹结构", () => {
    const legacy = {
      trigger: "patient-view",
      when: { when: { all: [{ fact: "context.scr", operator: "gte", value: 2 }] } },
      then: [{ actionCode: "REVIEW_REQUIRED", severity: "LOW", message: "复核" }],
      explain: { summary: "旧规则" },
    };
    expect(() => parseRuleDsl(legacy)).toThrow("规则算子不在受控目录内");
  });

  it("缺 when 抛错", () => {
    expect(() => parseRuleDsl({ then: [] })).toThrow();
  });

  it("解释模板包含动作说明与条件数", () => {
    const draft = instantiateArchetype("drug_safety_review");
    const explanation = buildExplanation(draft) as {
      template: string;
      authoring: { conditionCount: number };
    };
    expect(explanation.template).toContain(draft.action.message);
    expect(explanation.authoring.conditionCount).toBe(1);
  });

  it("三个原型均可实例化且不内置具体值", () => {
    expect(RULE_ARCHETYPES).toHaveLength(3);
    for (const archetype of RULE_ARCHETYPES) {
      const draft = archetype.build();
      expect(draft.root.children.length).toBeGreaterThan(0);
      // 占位字段，不得是具体患者/药品/诊断值
      const leaf = draft.root.children[0];
      if (leaf.kind === "leaf") {
        expect(leaf.fact).toContain("context.");
      }
    }
  });
});
