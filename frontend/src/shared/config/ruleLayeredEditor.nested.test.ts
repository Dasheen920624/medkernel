import { describe, expect, it } from "vitest";

import {
  conditionNodeToDsl,
  createDefaultRuleApplicability,
  dslToConditionNode,
  dslWhenToRootGroup,
  flatToRootGroup,
  isConditionGroup,
  type RuleConditionGroup,
  type RuleConditionTree,
} from "./ruleLayeredEditor";

function leaf(fact: string, operator = "exists") {
  return {
    id: `c-${fact}`,
    label: fact,
    fact,
    operator: operator as never,
    valueKind: "empty" as never,
  };
}

describe("ruleLayeredEditor 嵌套条件（P1-2 原地扩展）", () => {
  it("递归序列化 A 且 (B 或 C) 为 when 结构", () => {
    const root: RuleConditionGroup = {
      id: "g-root",
      logic: "all",
      children: [
        leaf("a", "equals"),
        { id: "g-1", logic: "any", children: [leaf("b"), leaf("c")] },
      ],
    };
    const dsl = conditionNodeToDsl(root) as { all: unknown[] };
    expect(dsl.all).toHaveLength(2);
    const nested = dsl.all[1] as { any: unknown[] };
    expect(nested.any).toHaveLength(2);
  });

  it("往返：节点序列化后能还原为等价结构", () => {
    const root: RuleConditionGroup = {
      id: "g-root",
      logic: "all",
      children: [
        leaf("a", "equals"),
        {
          id: "g-1",
          logic: "any",
          children: [leaf("b"), { id: "g-2", logic: "all", children: [leaf("c")] }],
        },
      ],
    };
    const restored = dslToConditionNode(conditionNodeToDsl(root));
    expect(isConditionGroup(restored)).toBe(true);
    // 再次序列化应与首次一致
    expect(conditionNodeToDsl(restored)).toEqual(conditionNodeToDsl(root));
  });

  it("支持 not 取反并能还原", () => {
    const root: RuleConditionGroup = {
      id: "g",
      logic: "any",
      negate: true,
      children: [leaf("x", "equals")],
    };
    const dsl = conditionNodeToDsl(root) as { not: { any: unknown[] } };
    expect(dsl.not).toBeDefined();
    const restored = dslWhenToRootGroup(dsl);
    expect(restored.negate).toBe(true);
    expect(restored.logic).toBe("any");
  });

  it("dslWhenToRootGroup 接受当前单层 all/any DSL", () => {
    const when = { all: [{ fact: "context.scr", operator: "gte", value: 2 }] };
    const root = dslWhenToRootGroup(when);
    expect(root.logic).toBe("all");
    expect(root.children).toHaveLength(1);
    expect(isConditionGroup(root.children[0])).toBe(false);
  });

  it("条件片段引用往返保留 fragmentRef、version 与 packageVersion", () => {
    const root: RuleConditionGroup = {
      id: "g-root",
      logic: "all",
      children: [
        {
          id: "fragment-1",
          label: "肾功能受限",
          fact: "",
          operator: "exists",
          valueKind: "empty",
          fragment: {
            fragmentId: "cf-renal",
            fragmentCode: "FRAG_RENAL",
            version: 1,
            packageVersion: "pkg-2026.06",
          },
        },
      ],
    };

    const dsl = conditionNodeToDsl(root) as { all: Array<Record<string, unknown>> };
    expect(dsl.all[0]).toMatchObject({
      fragmentRef: "FRAG_RENAL",
      version: 1,
      packageVersion: "pkg-2026.06",
    });
    expect(conditionNodeToDsl(dslToConditionNode(dsl))).toEqual(dsl);
  });

  it("flatToRootGroup 把扁平树提升为根组", () => {
    const tree: RuleConditionTree = {
      triggerPoint: "patient-view",
      applicability: createDefaultRuleApplicability(),
      logic: "any",
      conditions: [leaf("a"), leaf("b")],
      actions: [
        {
          actionCode: "REMIND",
          atSeverity: "LOW",
          indicator: "info",
          summary: "复核",
          detail: "复核",
          source: { label: "规则依据" },
          suggestions: [],
          overrideReasons: [],
          requiresPhysicianConfirmation: true,
        },
      ],
      explanationSummary: "摘要",
    };
    const root = flatToRootGroup(tree);
    expect(root.logic).toBe("any");
    expect(root.children).toHaveLength(2);
    expect(root.children.every((c) => !isConditionGroup(c))).toBe(true);
  });
});
