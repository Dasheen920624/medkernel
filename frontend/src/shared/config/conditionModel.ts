import {
  defaultValueKindForOperator,
  inferRuleValueKind,
  isRuleOperator,
  operatorNeedsValue as catalogOperatorNeedsValue,
  type RuleOperator,
  type RuleValueKind,
} from "./ruleOperatorCatalog";

/**
 * 递归条件模型（RULE-01 / PATH-01 可视化创作内核，OpenSpec 变更 pathway-rule-authoring-overhaul P1-1）。
 *
 * <p>本模型用可嵌套的「条件组（all/any/not）+ 叶子」表达任意深度临床判断
 * （如 `A 且 (B 或 C)`），并与后端
 * {@code RuleDslEvaluator.evaluateConditionNode} 的递归 `all`/`any` 求值无损双向映射。
 *
 * <p>规则 `when` 与路径边 `guard` 共用本模型（统一条件内核）。叶子承载字段路径
 * `fact` 与统一规则算子目录，确保规则页、质控页与路径边守卫不再各自维护算子白名单。
 */

/** 条件组逻辑：全部满足 / 任一满足。 */
export type RuleLogic = "all" | "any";

export type { RuleOperator, RuleValueKind };

/** 叶子比较值的允许类型。 */
export type RuleLeafValue =
  | string
  | number
  | boolean
  | Array<string | number | boolean>
  | Record<string, unknown>;

/** 叶子条件：最小判定单元（字段 + 算子 + 比较值）。 */
export interface RuleLeaf {
  kind: "leaf";
  id: string;
  label: string;
  /** 上下文字段路径（后续由字段目录选择器约束；现为文本）。 */
  fact: string;
  operator: RuleOperator;
  value?: RuleLeafValue;
  valueKind: RuleValueKind;
  fragment?: {
    fragmentId?: string;
    fragmentCode: string;
    name?: string;
    version: number;
    packageVersion: string;
  };
}

/** 条件组：可嵌套的逻辑容器，支持 all/any 与可选取反。 */
export interface RuleGroup {
  kind: "group";
  id: string;
  logic: RuleLogic;
  /** 取反语义（映射为 DSL 的 `not` 包裹）。 */
  negate?: boolean;
  children: RuleNode[];
}

/** 条件树节点：组或叶子。 */
export type RuleNode = RuleGroup | RuleLeaf;

/** 创作护栏默认值（可由调用方覆盖）。 */
export const MAX_TREE_DEPTH = 5;
export const MAX_LEAF_COUNT = 50;

/** DSL 叶子序列化形态（后端忽略 `ui` 旁注）。 */
type DslUi = { id?: string; label?: string; valueKind?: RuleValueKind; fragmentId?: string };

interface DslLeaf {
  fact: string;
  operator: RuleOperator;
  value?: unknown;
  ui?: DslUi;
}

interface DslFragment {
  fragmentRef: string;
  version: number;
  packageVersion: string;
  ui?: DslUi;
}

/** DSL 节点：`{all:[...]}` | `{any:[...]}` | `{not:节点}` | 叶子。 */
export type DslNode =
  | { all: DslNode[] }
  | { any: DslNode[] }
  | { not: DslNode }
  | DslFragment
  | DslLeaf;

let idSeq = 0;

/** 生成稳定的本地节点 id（仅前端编辑态使用）。 */
export function nextNodeId(prefix: "group" | "cond" = "cond"): string {
  idSeq += 1;
  return `${prefix}-${idSeq}`;
}

/** 算子是否需要比较值（`exists` 不需要）。 */
export function operatorNeedsValue(operator: RuleOperator): boolean {
  return catalogOperatorNeedsValue(operator);
}

/** 新建空叶子。 */
export function createLeaf(partial: Partial<RuleLeaf> = {}): RuleLeaf {
  const leaf: RuleLeaf = {
    kind: "leaf",
    id: partial.id ?? nextNodeId("cond"),
    label: partial.label ?? "条件",
    fact: partial.fact ?? "",
    operator: partial.operator ?? "equals",
    value: partial.value,
    valueKind: partial.valueKind ?? "string",
  };
  if (partial.fragment) leaf.fragment = partial.fragment;
  return leaf;
}

/** 新建条件组。 */
export function createGroup(partial: Partial<RuleGroup> = {}): RuleGroup {
  return {
    kind: "group",
    id: partial.id ?? nextNodeId("group"),
    logic: partial.logic ?? "all",
    negate: partial.negate,
    children: partial.children ?? [],
  };
}

/** 顶层条件树默认形态：单个 all 组含一个叶子。 */
export function createDefaultTree(): RuleGroup {
  return createGroup({ logic: "all", children: [createLeaf()] });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isDslFragment(dsl: DslLeaf | DslFragment): dsl is DslFragment {
  return typeof (dsl as DslFragment).fragmentRef === "string";
}

/** 归一比较值，使其与 valueKind 一致（用于序列化）。 */
export function normalizeLeafValue(value: unknown, kind: RuleValueKind): RuleLeafValue | undefined {
  if (kind === "empty") return undefined;
  if (kind === "range" || kind === "measurement" || kind === "temporal" || kind === "derived") {
    return isRecord(value) ? cloneJsonRecord(value) : {};
  }
  if (kind === "critical_flag") {
    return normalizeCriticalFlagValue(value);
  }
  if (kind === "staleness") {
    return isRecord(value) ? cloneJsonRecord(value) : { maxAge: "PT24H", referenceTime: "" };
  }
  if (kind === "number") {
    if (typeof value === "number") return value;
    const numeric = Number(String(value ?? "").trim());
    return Number.isFinite(numeric) ? numeric : 0;
  }
  if (kind === "boolean") {
    if (typeof value === "boolean") return value;
    return (
      String(value ?? "")
        .trim()
        .toLowerCase() === "true"
    );
  }
  if (kind === "list") {
    if (Array.isArray(value)) return value as RuleLeafValue;
    return String(value ?? "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return String(value ?? "");
}

function inferValueKind(value: unknown): RuleValueKind {
  return inferRuleValueKind(value);
}

function leafToDsl(leaf: RuleLeaf): DslLeaf | DslFragment {
  if (leaf.fragment) {
    return {
      fragmentRef: leaf.fragment.fragmentCode,
      version: leaf.fragment.version,
      packageVersion: leaf.fragment.packageVersion,
      ui: { id: leaf.id, label: leaf.label, fragmentId: leaf.fragment.fragmentId },
    };
  }
  const node: DslLeaf = {
    fact: leaf.fact.trim(),
    operator: leaf.operator,
    ui: { id: leaf.id, label: leaf.label, valueKind: leaf.valueKind },
  };
  if (operatorNeedsValue(leaf.operator)) {
    node.value = normalizeLeafValue(leaf.value, leaf.valueKind);
  }
  return node;
}

/** 递归把条件节点序列化为 DSL（组→all/any[/not 包裹]，叶子→{fact,operator,value,ui}）。 */
export function nodeToDsl(node: RuleNode): DslNode {
  if (node.kind === "leaf") {
    return leafToDsl(node);
  }
  const children = node.children.map(nodeToDsl);
  const grouped: DslNode = node.logic === "any" ? { any: children } : { all: children };
  return node.negate ? { not: grouped } : grouped;
}

function dslLeafToNode(dsl: DslLeaf | DslFragment, index: number): RuleLeaf {
  const ui = isRecord(dsl.ui) ? dsl.ui : undefined;
  if (isDslFragment(dsl)) {
    const fragment = dsl;
    if (
      !fragment.fragmentRef.trim() ||
      !fragment.packageVersion.trim() ||
      !Number.isInteger(fragment.version)
    ) {
      throw new Error("条件片段引用必须包含 fragmentRef、version 与 packageVersion");
    }
    const label = (ui?.label as string | undefined) ?? `片段 ${fragment.fragmentRef}`;
    return {
      kind: "leaf",
      id: (ui?.id as string | undefined) ?? nextNodeId("cond"),
      label,
      fact: "",
      operator: "exists",
      valueKind: "empty",
      fragment: {
        fragmentId: ui?.fragmentId as string | undefined,
        fragmentCode: fragment.fragmentRef,
        name: label,
        version: fragment.version,
        packageVersion: fragment.packageVersion,
      },
    };
  }
  if (!isRuleOperator(dsl.operator)) {
    throw new Error("规则算子不在受控目录内");
  }
  if (typeof dsl.fact !== "string" || dsl.fact.trim().length === 0) {
    throw new Error("规则条件字段不能为空");
  }
  const operator = dsl.operator;
  const valueKind: RuleValueKind =
    (ui?.valueKind as RuleValueKind | undefined) ??
    defaultValueKindForOperator(operator, inferValueKind(dsl.value));
  return {
    kind: "leaf",
    id: (ui?.id as string | undefined) ?? nextNodeId("cond"),
    label: (ui?.label as string | undefined) ?? `条件 ${index + 1}`,
    fact: dsl.fact.trim(),
    operator,
    value: operatorNeedsValue(operator) ? (dsl.value as RuleLeafValue) : undefined,
    valueKind,
  };
}

/** 递归把当前 DSL 还原为条件节点。 */
export function dslToNode(dsl: unknown, index = 0): RuleNode {
  if (!isRecord(dsl)) {
    throw new Error("条件 DSL 节点必须为对象");
  }
  if (Array.isArray((dsl as { all?: unknown }).all)) {
    const children = (dsl as { all: unknown[] }).all.map((child, i) => dslToNode(child, i));
    return createGroup({ logic: "all", children });
  }
  if (Array.isArray((dsl as { any?: unknown }).any)) {
    const children = (dsl as { any: unknown[] }).any.map((child, i) => dslToNode(child, i));
    return createGroup({ logic: "any", children });
  }
  if (isRecord((dsl as { not?: unknown }).not)) {
    const inner = dslToNode((dsl as { not: unknown }).not, index);
    if (inner.kind === "group") {
      return { ...inner, negate: true };
    }
    // not 包裹叶子：用单叶 all 组承载取反语义
    return createGroup({ logic: "all", negate: true, children: [inner] });
  }
  return dslLeafToNode(dsl as unknown as DslLeaf, index);
}

/** 顶层始终返回组：DSL 顶层是叶子时用单叶 all 组包裹。 */
export function dslToRootGroup(dsl: unknown): RuleGroup {
  const node = dslToNode(dsl, 0);
  return node.kind === "group" ? node : createGroup({ logic: "all", children: [node] });
}

/** 统计叶子总数（含嵌套）。 */
export function countLeaves(node: RuleNode): number {
  if (node.kind === "leaf") return 1;
  return node.children.reduce((sum, child) => sum + countLeaves(child), 0);
}

/** 计算最大嵌套深度（顶层组为深度 1）。 */
export function treeDepth(node: RuleNode): number {
  if (node.kind === "leaf") return 0;
  const childDepth = node.children.reduce((max, child) => Math.max(max, treeDepth(child)), 0);
  return 1 + childDepth;
}

/** 是否存在未解析字段（空或仍含模板占位符），用于提交前拦截。 */
export function hasUnresolvedFact(node: RuleNode): boolean {
  if (node.kind === "leaf") {
    if (node.fragment) return false;
    const fact = node.fact.trim();
    return fact.length === 0 || fact.includes("<字段路径>");
  }
  return node.children.some(hasUnresolvedFact);
}

export interface TreeValidationResult {
  ok: boolean;
  errors: string[];
}

/** 创作护栏校验：深度、叶子数、未解析字段、空组。 */
export function validateTree(
  root: RuleGroup,
  options: { maxDepth?: number; maxLeaves?: number } = {},
): TreeValidationResult {
  const maxDepth = options.maxDepth ?? MAX_TREE_DEPTH;
  const maxLeaves = options.maxLeaves ?? MAX_LEAF_COUNT;
  const errors: string[] = [];

  const depth = treeDepth(root);
  if (depth > maxDepth) {
    errors.push(`条件嵌套深度 ${depth} 超过上限 ${maxDepth}，请拆分规则。`);
  }
  const leaves = countLeaves(root);
  if (leaves === 0) {
    errors.push("条件树至少需要一个叶子条件。");
  }
  if (leaves > maxLeaves) {
    errors.push(`叶子条件数 ${leaves} 超过上限 ${maxLeaves}，请拆分规则。`);
  }
  if (hasUnresolvedFact(root)) {
    errors.push("存在未填写的上下文字段，请补全后再提交。");
  }
  const hasEmptyGroup = (node: RuleNode): boolean =>
    node.kind === "group" && (node.children.length === 0 || node.children.some(hasEmptyGroup));
  if (hasEmptyGroup(root)) {
    errors.push("存在空条件组，请删除或补充条件。");
  }

  return { ok: errors.length === 0, errors };
}

/** 不可变更新：按 id 替换节点。返回新根。 */
export function updateNodeById(
  root: RuleGroup,
  id: string,
  updater: (node: RuleNode) => RuleNode,
): RuleGroup {
  const walk = (node: RuleNode): RuleNode => {
    if (node.id === id) return updater(node);
    if (node.kind === "group") {
      return { ...node, children: node.children.map(walk) };
    }
    return node;
  };
  return walk(root) as RuleGroup;
}

/** 不可变更新：按 id 删除节点（不删根）。 */
export function removeNodeById(root: RuleGroup, id: string): RuleGroup {
  const walk = (group: RuleGroup): RuleGroup => ({
    ...group,
    children: group.children
      .filter((child) => child.id !== id)
      .map((child) => (child.kind === "group" ? walk(child) : child)),
  });
  return walk(root);
}

/** 不可变新增：向指定组 id 追加子节点。 */
export function addChildToGroup(root: RuleGroup, groupId: string, child: RuleNode): RuleGroup {
  return updateNodeById(root, groupId, (node) =>
    node.kind === "group" ? { ...node, children: [...node.children, child] } : node,
  ) as RuleGroup;
}

function cloneJsonRecord(value: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}

function normalizeCriticalFlagValue(value: unknown) {
  const rawValues = isRecord(value) ? value.criticalValues : value;
  const criticalValues = Array.isArray(rawValues)
    ? rawValues.map((item) => String(item).trim()).filter(Boolean)
    : String(rawValues ?? "")
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean);

  return criticalValues.length > 0 ? { criticalValues } : undefined;
}
