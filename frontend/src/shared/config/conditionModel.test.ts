import { describe, expect, it } from "vitest";

import {
  addChildToGroup,
  countLeaves,
  createGroup,
  createLeaf,
  dslToRootGroup,
  fromLegacyWhen,
  hasUnresolvedFact,
  nodeToDsl,
  removeNodeById,
  treeDepth,
  updateNodeById,
  validateTree,
  type RuleGroup,
} from "./conditionModel";

describe("RULE-01 递归条件模型（P1-1）", () => {
  it("支持 A 且 (B 或 C) 的嵌套并序列化为后端 when 结构", () => {
    const root: RuleGroup = createGroup({
      logic: "all",
      children: [
        createLeaf({ fact: "patient.age", operator: "gte", value: 65, valueKind: "number" }),
        createGroup({
          logic: "any",
          children: [
            createLeaf({ fact: "observations[].valueNumeric", operator: "gt", value: 1.5, valueKind: "number" }),
            createLeaf({ fact: "conditions[].code", operator: "in", value: ["N18"], valueKind: "list" }),
          ],
        }),
      ],
    });

    const dsl = nodeToDsl(root) as { all: unknown[] };
    expect(Array.isArray(dsl.all)).toBe(true);
    expect(dsl.all).toHaveLength(2);
    const nested = dsl.all[1] as { any: unknown[] };
    expect(Array.isArray(nested.any)).toBe(true);
    expect(nested.any).toHaveLength(2);
  });

  it("三层嵌套往返序列化无损", () => {
    const root: RuleGroup = createGroup({
      logic: "all",
      children: [
        createLeaf({ fact: "a", operator: "equals", value: "x", valueKind: "string" }),
        createGroup({
          logic: "any",
          children: [
            createLeaf({ fact: "b", operator: "gt", value: 1, valueKind: "number" }),
            createGroup({
              logic: "all",
              children: [createLeaf({ fact: "c", operator: "exists", valueKind: "empty" })],
            }),
          ],
        }),
      ],
    });

    const restored = dslToRootGroup(nodeToDsl(root));
    expect(treeDepth(restored)).toBe(treeDepth(root));
    expect(countLeaves(restored)).toBe(3);
    // 再次序列化应与首次一致（结构无损）
    expect(nodeToDsl(restored)).toEqual(nodeToDsl(root));
  });

  it("支持 not 取反并能还原", () => {
    const root = createGroup({
      logic: "any",
      negate: true,
      children: [createLeaf({ fact: "x", operator: "equals", value: true, valueKind: "boolean" })],
    });
    const dsl = nodeToDsl(root) as { not: { any: unknown[] } };
    expect(dsl.not).toBeDefined();
    expect(Array.isArray(dsl.not.any)).toBe(true);

    const restored = dslToRootGroup(dsl);
    expect(restored.negate).toBe(true);
    expect(restored.logic).toBe("any");
  });

  it("向后兼容旧扁平 when（单层 all）还原为单层组", () => {
    const legacy = {
      when: {
        all: [
          { fact: "context.scr", operator: "gte", value: 2, ui: { label: "肌酐", valueKind: "number" } },
        ],
      },
    };
    const root = fromLegacyWhen(legacy);
    expect(root.kind).toBe("group");
    expect(root.logic).toBe("all");
    expect(countLeaves(root)).toBe(1);
    expect(treeDepth(root)).toBe(1);
    const leaf = root.children[0];
    expect(leaf.kind).toBe("leaf");
    if (leaf.kind === "leaf") {
      expect(leaf.fact).toBe("context.scr");
      expect(leaf.operator).toBe("gte");
      expect(leaf.label).toBe("肌酐");
    }
  });

  it("顶层为叶子时用单叶 all 组包裹", () => {
    const root = dslToRootGroup({ fact: "x", operator: "exists" });
    expect(root.kind).toBe("group");
    expect(root.logic).toBe("all");
    expect(countLeaves(root)).toBe(1);
  });

  it("exists 算子不序列化比较值", () => {
    const leaf = createLeaf({ fact: "x", operator: "exists", valueKind: "empty" });
    const dsl = nodeToDsl(leaf) as { value?: unknown };
    expect(dsl.value).toBeUndefined();
  });

  it("list 比较值按逗号归一", () => {
    const leaf = createLeaf({ fact: "x", operator: "in", value: "a, b ,c", valueKind: "list" });
    const dsl = nodeToDsl(leaf) as { value: unknown };
    expect(dsl.value).toEqual(["a", "b", "c"]);
  });

  it("校验护栏：深度超限、空字段、空组、超叶子数", () => {
    const okTree = createGroup({
      logic: "all",
      children: [createLeaf({ fact: "x", operator: "exists", valueKind: "empty" })],
    });
    expect(validateTree(okTree).ok).toBe(true);

    const unresolved = createGroup({
      logic: "all",
      children: [createLeaf({ fact: "context.<字段路径>", operator: "exists", valueKind: "empty" })],
    });
    expect(hasUnresolvedFact(unresolved)).toBe(true);
    expect(validateTree(unresolved).ok).toBe(false);

    const emptyGroup = createGroup({ logic: "all", children: [] });
    expect(validateTree(emptyGroup).ok).toBe(false);

    const deep = createGroup({ logic: "all", children: [createLeaf({ fact: "x", operator: "exists", valueKind: "empty" })] });
    expect(validateTree(deep, { maxDepth: 1 }).ok).toBe(true);
    const tooDeep = createGroup({
      logic: "all",
      children: [createGroup({ logic: "any", children: [createLeaf({ fact: "x", operator: "exists", valueKind: "empty" })] })],
    });
    expect(validateTree(tooDeep, { maxDepth: 1 }).ok).toBe(false);
  });

  it("不可变增删改按 id 生效", () => {
    let root = createGroup({ id: "g-root", logic: "all", children: [] });
    const leaf = createLeaf({ id: "c-1", fact: "x", operator: "exists", valueKind: "empty" });
    root = addChildToGroup(root, "g-root", leaf);
    expect(countLeaves(root)).toBe(1);

    root = updateNodeById(root, "c-1", (node) =>
      node.kind === "leaf" ? { ...node, fact: "y" } : node,
    ) as RuleGroup;
    expect((root.children[0] as { fact: string }).fact).toBe("y");

    root = removeNodeById(root, "c-1");
    expect(countLeaves(root)).toBe(0);
  });
});
