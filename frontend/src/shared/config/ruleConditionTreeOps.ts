/**
 * 规则递归条件树编辑操作（RULE-01 P1-2 页面接入辅助）。
 *
 * <p>在 {@link ./ruleLayeredEditor} 的递归类型之上提供前端编辑态所需的工厂与
 * 不可变增删改/遍历/校验工具，供 RuleDefinitions 的 L2 递归编辑器使用。
 * 保留全部已上线临床算子（叶子仍是 RuleCondition）。
 */
import {
  isConditionGroup,
  type RuleCondition,
  type RuleConditionGroup,
  type RuleConditionNode,
  type RuleLogic,
} from "./ruleLayeredEditor";

/** 创作护栏：最大嵌套深度（顶层组为深度 1）。 */
export const MAX_TREE_DEPTH = 5;

let opsSeq = 0;

function nextId(prefix: "group" | "condition"): string {
  opsSeq += 1;
  return `${prefix === "group" ? "grp" : "cond"}-${Date.now().toString(36)}-${opsSeq}`;
}

/** 新建叶子条件（默认等于算子、文本比较值）。 */
export function createConditionLeaf(partial: Partial<RuleCondition> = {}): RuleCondition {
  return {
    id: partial.id ?? nextId("condition"),
    label: partial.label ?? "条件",
    fact: partial.fact ?? "",
    operator: partial.operator ?? "equals",
    value: partial.value ?? "",
    valueKind: partial.valueKind ?? "string",
  };
}

/** 新建条件组（默认全部满足，含一个叶子）。 */
export function createConditionGroup(partial: Partial<RuleConditionGroup> = {}): RuleConditionGroup {
  return {
    id: partial.id ?? nextId("group"),
    logic: partial.logic ?? "all",
    negate: partial.negate,
    children: partial.children ?? [createConditionLeaf()],
  };
}

/** 不可变：按 id 更新某个叶子条件。 */
export function mapConditionById(
  node: RuleConditionNode,
  id: string,
  fn: (condition: RuleCondition) => RuleCondition,
): RuleConditionNode {
  if (isConditionGroup(node)) {
    return { ...node, children: node.children.map((child) => mapConditionById(child, id, fn)) };
  }
  return node.id === id ? fn(node) : node;
}

/** 不可变：按 id 更新某个条件组（如切换 logic / 取反）。 */
export function mapGroupById(
  node: RuleConditionNode,
  id: string,
  fn: (group: RuleConditionGroup) => RuleConditionGroup,
): RuleConditionNode {
  if (!isConditionGroup(node)) return node;
  const next = node.id === id ? fn(node) : node;
  return { ...next, children: next.children.map((child) => mapGroupById(child, id, fn)) };
}

/** 不可变：按 id 删除节点（不删根）。 */
export function removeConditionById(root: RuleConditionGroup, id: string): RuleConditionGroup {
  const walk = (group: RuleConditionGroup): RuleConditionGroup => ({
    ...group,
    children: group.children
      .filter((child) => child.id !== id)
      .map((child) => (isConditionGroup(child) ? walk(child) : child)),
  });
  return walk(root);
}

/** 不可变：向指定组 id 追加子节点。 */
export function addNodeToGroup(
  root: RuleConditionGroup,
  groupId: string,
  node: RuleConditionNode,
): RuleConditionGroup {
  return mapGroupById(root, groupId, (group) => ({
    ...group,
    children: [...group.children, node],
  })) as RuleConditionGroup;
}

/** 叶子总数（含嵌套）。 */
export function countConditionLeaves(node: RuleConditionNode): number {
  if (!isConditionGroup(node)) return 1;
  return node.children.reduce((sum, child) => sum + countConditionLeaves(child), 0);
}

/** 最大嵌套深度（顶层组为深度 1）。 */
export function rootDepth(node: RuleConditionNode): number {
  if (!isConditionGroup(node)) return 0;
  const childDepth = node.children.reduce((max, child) => Math.max(max, rootDepth(child)), 0);
  return 1 + childDepth;
}

/** 是否存在未解析字段（空或仍含模板占位符）。 */
export function rootHasUnresolvedFact(node: RuleConditionNode): boolean {
  if (!isConditionGroup(node)) {
    const fact = node.fact.trim();
    return fact.length === 0 || fact.includes("<字段路径>");
  }
  return node.children.some(rootHasUnresolvedFact);
}

/** 组逻辑中文文案。 */
export function logicLabel(logic: RuleLogic): string {
  return logic === "all" ? "全部条件满足" : "任一条件满足";
}
