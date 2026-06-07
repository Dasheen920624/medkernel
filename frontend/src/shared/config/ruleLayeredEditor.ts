import {
  defaultValueKindForOperator,
  inferRuleValueKind,
  isRuleExpressionSelect,
  isRuleOperator,
  normalizeTemporalMode,
  operatorNeedsValue,
  type RuleExpressionSelect,
  type RuleOperator,
  type RuleValueKind,
} from "./ruleOperatorCatalog";
import type { RuleType } from "./ruleTypes";
import { isClinicalTriggerPoint, type ClinicalTriggerPoint } from "./clinicalTriggerPoints";

export type RuleTemplateKey =
  | "clinical_quality_monitor"
  | "drug_safety_review"
  | "insurance_policy_review"
  | "clinical_operator_review";

export type RuleLogic = "all" | "any";
export type { RuleOperator, RuleValueKind };
export type RuleSeverity = "LOW" | "MEDIUM" | "HIGH";
export type RuleConditionValue =
  | string
  | number
  | boolean
  | Array<string | number | boolean>
  | Record<string, unknown>;

export interface RuleCondition {
  id: string;
  label: string;
  fact: string;
  expr?: RuleExpressionDraft;
  operator: RuleOperator;
  value?: RuleConditionValue;
  valueKind: RuleValueKind;
}

export interface RuleExpressionDraft {
  field: string;
  select?: RuleExpressionSelect;
  where?: Record<string, unknown>;
  over?: string;
  referenceTime?: string;
}

export interface RuleActionDraft {
  actionCode: string;
  severity: RuleSeverity;
  message: string;
  requiresPhysicianConfirmation: boolean;
}

export interface RuleConditionTree {
  triggerPoint: ClinicalTriggerPoint;
  logic: RuleLogic;
  /** L2 叶子索引，供列表、统计与默认模板使用；DSL 权威结构始终由 root 归一化得到。 */
  conditions: RuleCondition[];
  /**
   * 递归条件根组（P1-2 嵌套支持，可选输入）。存在时作为权威条件结构，支持任意深度
   * 「条件组(all/any/可取反)+叶子」；缺失时由 conditions 显式归一化生成。
   */
  root?: RuleConditionGroup;
  action: RuleActionDraft;
  explanationSummary: string;
}

/** 递归条件组：可嵌套的逻辑容器（与后端 RuleDslEvaluator 递归 all/any 对齐）。 */
export interface RuleConditionGroup {
  id: string;
  logic: RuleLogic;
  /** 取反语义，映射为 DSL 的 `not` 包裹。 */
  negate?: boolean;
  children: RuleConditionNode[];
}

/** 条件树节点：丰富叶子（RuleCondition，保留全部临床算子）或嵌套组。 */
export type RuleConditionNode = RuleCondition | RuleConditionGroup;

/** 结构判别：是否为条件组。 */
export function isConditionGroup(node: RuleConditionNode): node is RuleConditionGroup {
  return (node as RuleConditionGroup).children !== undefined;
}

export interface RuleLayerTemplate {
  key: RuleTemplateKey;
  title: string;
  description: string;
  ruleType: RuleType;
  riskLevel: RuleSeverity;
  tree: RuleConditionTree;
}

type RuleDslCondition = {
  fact?: string;
  expr?: RuleExpressionDraft;
  operator: RuleOperator;
  value?: unknown;
  ui?: {
    id?: string;
    label?: string;
    valueKind?: RuleValueKind;
  };
};

export type RuleDsl = {
  trigger: ClinicalTriggerPoint;
  when: Partial<Record<RuleLogic, RuleDslNode[]>> & { not?: RuleDslNode };
  then: RuleActionDraft[];
  explain: {
    summary: string;
    authoring: {
      layer: "L2_VISUAL_TREE";
      conditionCount: number;
    };
  };
};

const DEFAULT_ACTION: RuleActionDraft = {
  actionCode: "REVIEW_REQUIRED",
  severity: "LOW",
  message: "命中后提交人工审核，不自动写入医嘱",
  requiresPhysicianConfirmation: true,
};

export const RULE_LAYER_TEMPLATES: RuleLayerTemplate[] = [
  {
    key: "clinical_quality_monitor",
    title: "临床质控阈值",
    description: "适合从真实上下文快照取一个数值字段，超过阈值后提交人工复核。",
    ruleType: "QUALITY",
    riskLevel: "MEDIUM",
    tree: {
      triggerPoint: "result-review",
      logic: "all",
      conditions: [
        {
          id: "condition-1",
          label: "真实快照数值字段",
          fact: "context.<字段路径>",
          operator: "gte",
          value: "",
          valueKind: "number",
        },
      ],
      action: {
        ...DEFAULT_ACTION,
        severity: "MEDIUM",
      },
      explanationSummary: "依据真实上下文快照中的数值字段进行确定性判断",
    },
  },
  {
    key: "drug_safety_review",
    title: "合理用药复核",
    description: "适合检查真实上下文中是否存在受控字段，命中后要求人工复核。",
    ruleType: "ORDER",
    riskLevel: "LOW",
    tree: {
      triggerPoint: "medication-prescribe",
      logic: "all",
      conditions: [
        {
          id: "condition-1",
          label: "待复核上下文字段",
          fact: "context.<字段路径>",
          operator: "exists",
          valueKind: "empty",
        },
      ],
      action: DEFAULT_ACTION,
      explanationSummary: "依据真实上下文快照中的受控字段进行确定性判断",
    },
  },
  {
    key: "insurance_policy_review",
    title: "医保规范核查",
    description: "适合检查真实上下文编码或状态字段是否进入受控集合。",
    ruleType: "INSURANCE",
    riskLevel: "MEDIUM",
    tree: {
      triggerPoint: "order-sign",
      logic: "any",
      conditions: [
        {
          id: "condition-1",
          label: "受控编码或状态字段",
          fact: "context.<字段路径>",
          operator: "in",
          value: "",
          valueKind: "list",
        },
      ],
      action: {
        ...DEFAULT_ACTION,
        severity: "MEDIUM",
      },
      explanationSummary: "依据真实上下文快照中的编码或状态集合进行确定性判断",
    },
  },
  {
    key: "clinical_operator_review",
    title: "临床算子复核",
    description: "适合配置 MED-C2 已实现的区间、单位换算、时间窗或受控公式判断。",
    ruleType: "QUALITY",
    riskLevel: "HIGH",
    tree: {
      triggerPoint: "result-review",
      logic: "all",
      conditions: [
        {
          id: "condition-1",
          label: "临床算子字段",
          fact: "context.<字段路径>",
          operator: "between",
          value: {
            min: "",
            max: "",
            includeMin: true,
            includeMax: true,
            unit: "",
          },
          valueKind: "range",
        },
      ],
      action: {
        ...DEFAULT_ACTION,
        severity: "HIGH",
      },
      explanationSummary: "依据 MED-C2 临床算子对真实上下文快照进行确定性判断",
    },
  },
];

export function instantiateRuleTemplate(key: RuleTemplateKey): RuleConditionTree {
  const template = RULE_LAYER_TEMPLATES.find((item) => item.key === key) ?? RULE_LAYER_TEMPLATES[0];
  return cloneTree(template.tree);
}

export function conditionTreeToDsl(tree: RuleConditionTree): RuleDsl {
  const root = tree.root ?? flatToRootGroup(tree);
  return {
    trigger: tree.triggerPoint,
    when: conditionNodeToDsl(root) as RuleDsl["when"],
    then: [{ ...tree.action }],
    explain: {
      summary: tree.explanationSummary,
      authoring: {
        layer: "L2_VISUAL_TREE",
        conditionCount: countConditionLeaves(root),
      },
    },
  };
}

export function dslToConditionTree(dsl: unknown): RuleConditionTree {
  if (!isRecord(dsl) || !isRecord(dsl.when)) {
    throw new Error("规则 DSL 缺少 when 条件");
  }
  if (!isClinicalTriggerPoint(dsl.trigger)) {
    throw new Error("规则 DSL 缺少或包含不支持的 trigger");
  }

  const root = dslWhenToRootGroup(dsl.when);
  const logic = root.logic;
  const rawConditions = dsl.when[logic];
  if ((!Array.isArray(rawConditions) || rawConditions.length === 0) && root.children.length === 0) {
    throw new Error("规则 DSL 至少需要一个条件");
  }

  const then = Array.isArray(dsl.then) ? dsl.then[0] : undefined;
  const explain = isRecord(dsl.explain) ? dsl.explain : undefined;

  return {
    triggerPoint: dsl.trigger,
    logic,
    conditions: collectConditionLeaves(root),
    root,
    action: {
      actionCode: readString(then, "actionCode", DEFAULT_ACTION.actionCode),
      severity: readSeverity(then, "severity", DEFAULT_ACTION.severity),
      message: readString(then, "message", DEFAULT_ACTION.message),
      requiresPhysicianConfirmation:
        readBoolean(then, "requiresPhysicianConfirmation") ??
        DEFAULT_ACTION.requiresPhysicianConfirmation,
    },
    explanationSummary:
      typeof explain?.summary === "string" && explain.summary.trim()
        ? explain.summary
        : "依据真实上下文快照进行确定性判断",
  };
}

export function createExplanationTemplate(tree: RuleConditionTree) {
  const variables: Record<string, string> = {};
  const root = tree.root ?? flatToRootGroup(tree);
  for (const condition of collectConditionLeaves(root)) {
    variables[condition.expr?.field ?? condition.fact] = "由 L2 条件树选择的真实上下文字段";
  }

  return {
    template: `${tree.explanationSummary}。命中后处置动作：${tree.action.message}`,
    variables,
    authoring: {
      layer: "L1/L2/L3",
      conditionCount: countConditionLeaves(root),
      logic: root.logic,
    },
  };
}

export function formatRuleJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

export function parseRuleJson(text: string): unknown {
  const normalized = text.trim();
  if (!normalized) {
    throw new Error("JSON 内容为空");
  }
  return JSON.parse(normalized) as unknown;
}

export function conditionNeedsValue(operator: RuleOperator): boolean {
  return operatorNeedsValue(operator);
}

export function normalizeConditionValue(value: unknown, kind: RuleValueKind) {
  if (kind === "empty") return undefined;
  if (kind === "temporal") {
    const record = isRecord(value) ? cloneJsonRecord(value) : {};
    return { ...record, mode: normalizeTemporalMode(record.mode) };
  }
  if (kind === "range" || kind === "measurement" || kind === "derived") {
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
    return Number.isFinite(numeric) ? numeric : value;
  }
  if (kind === "boolean") {
    if (typeof value === "boolean") return value;
    return String(value).trim().toLowerCase() === "true";
  }
  if (kind === "list") {
    if (Array.isArray(value)) return value;
    return String(value ?? "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return String(value ?? "");
}

/** 递归 DSL 节点：`{all:[...]}` | `{any:[...]}` | `{not:节点}` | 叶子。 */
export type RuleDslNode =
  | { all: RuleDslNode[] }
  | { any: RuleDslNode[] }
  | { not: RuleDslNode }
  | RuleDslCondition;

let nestedNodeSeq = 0;

function nextNestedId(prefix: "group" | "condition"): string {
  nestedNodeSeq += 1;
  return `${prefix}-${prefix === "group" ? "g" : "c"}${nestedNodeSeq}`;
}

/** 递归把条件节点（组或叶子）序列化为 DSL，复用既有叶子序列化以保留临床算子。 */
export function conditionNodeToDsl(node: RuleConditionNode): RuleDslNode {
  if (isConditionGroup(node)) {
    const children = node.children.map(conditionNodeToDsl);
    const grouped: RuleDslNode = node.logic === "any" ? { any: children } : { all: children };
    return node.negate ? { not: grouped } : grouped;
  }
  return conditionToDsl(node);
}

/** 递归把 DSL 还原为条件节点；支持 all/any/not 与当前叶子节点，叶子复用既有解析。 */
export function dslToConditionNode(dsl: unknown, index = 0): RuleConditionNode {
  if (!isRecord(dsl)) {
    throw new Error("规则 DSL 条件节点必须为对象");
  }
  if (Array.isArray(dsl.all)) {
    return {
      id: nextNestedId("group"),
      logic: "all",
      children: dsl.all.map((child, childIndex) => dslToConditionNode(child, childIndex)),
    };
  }
  if (Array.isArray(dsl.any)) {
    return {
      id: nextNestedId("group"),
      logic: "any",
      children: dsl.any.map((child, childIndex) => dslToConditionNode(child, childIndex)),
    };
  }
  if (isRecord(dsl.not)) {
    const inner = dslToConditionNode(dsl.not, index);
    if (isConditionGroup(inner)) {
      return { ...inner, negate: true };
    }
    return { id: nextNestedId("group"), logic: "all", negate: true, children: [inner] };
  }
  return dslConditionToTree(dsl, index);
}

/** 把 DSL 的 when 还原为递归根组（顶层为叶子时用单叶 all 组包裹）。 */
export function dslWhenToRootGroup(when: unknown): RuleConditionGroup {
  const node = dslToConditionNode(when, 0);
  return isConditionGroup(node)
    ? node
    : { id: nextNestedId("group"), logic: "all", children: [node] };
}

/** 把 L2 叶子索引提升为递归根组，保证序列化时只有一棵权威条件树。 */
export function flatToRootGroup(tree: RuleConditionTree): RuleConditionGroup {
  if (tree.root) return tree.root;
  return {
    id: nextNestedId("group"),
    logic: tree.logic,
    children: tree.conditions.map((condition) => ({ ...condition })),
  };
}

export function countConditionLeaves(node: RuleConditionNode): number {
  if (!isConditionGroup(node)) {
    return 1;
  }
  return node.children.reduce((sum, child) => sum + countConditionLeaves(child), 0);
}

function collectConditionLeaves(node: RuleConditionNode): RuleCondition[] {
  if (!isConditionGroup(node)) {
    return [node];
  }
  return node.children.flatMap((child) => collectConditionLeaves(child));
}

function conditionToDsl(condition: RuleCondition): RuleDslCondition {
  const expr = normalizeConditionExpression(condition.expr);
  const node: RuleDslCondition = {
    operator: condition.operator,
    ui: {
      id: condition.id,
      label: condition.label,
      valueKind: condition.valueKind,
    },
  };
  if (expr) {
    node.expr = expr;
  } else {
    node.fact = condition.fact.trim();
  }

  if (conditionNeedsValue(condition.operator)) {
    const normalizedValue = normalizeConditionValue(condition.value, condition.valueKind);
    if (normalizedValue !== undefined) {
      node.value = normalizedValue;
    }
  }

  return node;
}

function dslConditionToTree(condition: unknown, index: number): RuleCondition {
  if (!isRecord(condition)) {
    throw new Error("规则 DSL 条件必须为对象");
  }
  const ui = isRecord(condition.ui) ? condition.ui : undefined;
  const operator = readOperator(condition, "operator");
  const inferredKind = inferRuleValueKind(condition.value);
  const expr = readExpression(condition.expr);
  return {
    id: readString(ui, "id", `condition-${index + 1}`),
    label: readString(ui, "label", `条件 ${index + 1}`),
    fact: readString(condition, "fact", expr?.field ?? ""),
    expr,
    operator,
    value: condition.value as RuleCondition["value"],
    valueKind: readValueKind(ui, "valueKind", defaultValueKindForOperator(operator, inferredKind)),
  };
}

function cloneTree(tree: RuleConditionTree): RuleConditionTree {
  return {
    triggerPoint: tree.triggerPoint,
    logic: tree.logic,
    conditions: tree.conditions.map((condition) => ({
      ...condition,
      value: Array.isArray(condition.value) ? [...condition.value] : condition.value,
      expr: condition.expr ? normalizeConditionExpression(condition.expr) : undefined,
    })),
    action: { ...tree.action },
    explanationSummary: tree.explanationSummary,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readString(source: unknown, key: string, fallback: string): string {
  return isRecord(source) && typeof source[key] === "string" ? source[key] : fallback;
}

function readBoolean(source: unknown, key: string): boolean | undefined {
  return isRecord(source) && typeof source[key] === "boolean" ? source[key] : undefined;
}

function readSeverity(source: unknown, key: string, fallback: RuleSeverity): RuleSeverity {
  const value = readString(source, key, fallback);
  return value === "LOW" || value === "MEDIUM" || value === "HIGH" ? value : fallback;
}

function readOperator(source: unknown, key: string): RuleOperator {
  const value = readString(source, key, "");
  if (!value) {
    throw new Error("规则 DSL 条件缺少 operator");
  }
  if (isRuleOperator(value)) {
    return value;
  }
  throw new Error(`不支持的规则算子: ${value}`);
}

function readValueKind(source: unknown, key: string, fallback: RuleValueKind): RuleValueKind {
  const value = readString(source, key, fallback);
  const kinds = [
    "empty",
    "string",
    "number",
    "boolean",
    "list",
    "range",
    "measurement",
    "temporal",
    "derived",
    "critical_flag",
    "staleness",
  ] satisfies RuleValueKind[];
  return kinds.includes(value as RuleValueKind) ? (value as RuleValueKind) : fallback;
}

function cloneJsonRecord(value: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}

function normalizeConditionExpression(expr?: RuleExpressionDraft): RuleExpressionDraft | undefined {
  const field = expr?.field?.trim();
  if (!field) return undefined;
  const normalized: RuleExpressionDraft = { field };
  if (expr?.select) normalized.select = expr.select;
  if (expr?.where && isRecord(expr.where)) normalized.where = cloneJsonRecord(expr.where);
  if (expr?.over?.trim()) normalized.over = expr.over.trim();
  if (expr?.referenceTime?.trim()) normalized.referenceTime = expr.referenceTime.trim();
  return normalized;
}

function readExpression(value: unknown): RuleExpressionDraft | undefined {
  if (!isRecord(value)) return undefined;
  const field = readString(value, "field", "").trim();
  if (!field) return undefined;
  const select = readString(value, "select", "");
  if (select && !isRuleExpressionSelect(select)) {
    throw new Error(`不支持的表达式聚合函数: ${select}`);
  }
  const expressionSelect = select && isRuleExpressionSelect(select) ? select : undefined;
  return normalizeConditionExpression({
    field,
    select: expressionSelect,
    where: isRecord(value.where) ? cloneJsonRecord(value.where) : undefined,
    over: readString(value, "over", ""),
    referenceTime: readString(value, "referenceTime", ""),
  });
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
