export type RuleTemplateKey =
  | "clinical_quality_monitor"
  | "drug_safety_review"
  | "insurance_policy_review"
  | "clinical_operator_review";

export type RuleLogic = "all" | "any";
export type RuleOperator =
  | "exists"
  | "equals"
  | "not_equals"
  | "contains"
  | "gt"
  | "gte"
  | "lt"
  | "lte"
  | "in"
  | "not_in"
  | "between"
  | "unit_compare"
  | "temporal"
  | "derived";
export type RuleValueKind =
  | "empty"
  | "string"
  | "number"
  | "boolean"
  | "list"
  | "range"
  | "measurement"
  | "temporal"
  | "derived";
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
  operator: RuleOperator;
  value?: RuleConditionValue;
  valueKind: RuleValueKind;
}

export interface RuleActionDraft {
  actionCode: string;
  severity: RuleSeverity;
  message: string;
  requiresPhysicianConfirmation: boolean;
}

export interface RuleConditionTree {
  logic: RuleLogic;
  conditions: RuleCondition[];
  /**
   * 递归条件根组（P1-2 嵌套支持，可选）。存在时作为权威条件结构，支持任意深度
   * 「条件组(all/any/可取反)+叶子」；不存在时回退到扁平 logic+conditions（向后兼容）。
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
  ruleType: "DRUG_SAFETY" | "INSURANCE_AUDIT" | "CLINICAL_QUALITY";
  riskLevel: RuleSeverity;
  tree: RuleConditionTree;
}

type RuleDslCondition = {
  fact: string;
  operator: RuleOperator;
  value?: unknown;
  ui?: {
    id?: string;
    label?: string;
    valueKind?: RuleValueKind;
  };
};

export type RuleDsl = {
  when: Partial<Record<RuleLogic, RuleDslCondition[]>>;
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
    ruleType: "CLINICAL_QUALITY",
    riskLevel: "MEDIUM",
    tree: {
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
    ruleType: "DRUG_SAFETY",
    riskLevel: "LOW",
    tree: {
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
    ruleType: "INSURANCE_AUDIT",
    riskLevel: "MEDIUM",
    tree: {
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
    ruleType: "CLINICAL_QUALITY",
    riskLevel: "HIGH",
    tree: {
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
  return {
    when: {
      [tree.logic]: tree.conditions.map(conditionToDsl),
    },
    then: [{ ...tree.action }],
    explain: {
      summary: tree.explanationSummary,
      authoring: {
        layer: "L2_VISUAL_TREE",
        conditionCount: tree.conditions.length,
      },
    },
  };
}

export function dslToConditionTree(dsl: unknown): RuleConditionTree {
  if (!isRecord(dsl) || !isRecord(dsl.when)) {
    throw new Error("规则 DSL 缺少 when 条件");
  }

  const logic: RuleLogic = Array.isArray(dsl.when.all) ? "all" : "any";
  const rawConditions = dsl.when[logic];
  if (!Array.isArray(rawConditions) || rawConditions.length === 0) {
    throw new Error("规则 DSL 至少需要一个条件");
  }

  const then = Array.isArray(dsl.then) ? dsl.then[0] : undefined;
  const explain = isRecord(dsl.explain) ? dsl.explain : undefined;

  return {
    logic,
    conditions: rawConditions.map((condition, index) => dslConditionToTree(condition, index)),
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
  for (const condition of tree.conditions) {
    variables[condition.fact] = "由 L2 条件树选择的真实上下文字段";
  }

  return {
    template: `${tree.explanationSummary}。命中后处置动作：${tree.action.message}`,
    variables,
    authoring: {
      layer: "L1/L2/L3",
      conditionCount: tree.conditions.length,
      logic: tree.logic,
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
  return operator !== "exists";
}

export function normalizeConditionValue(value: unknown, kind: RuleValueKind) {
  if (kind === "empty") return undefined;
  if (kind === "range" || kind === "measurement" || kind === "temporal" || kind === "derived") {
    return isRecord(value) ? cloneJsonRecord(value) : {};
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

/** 递归把 DSL 还原为条件节点；兼容 all/any/not 与扁平叶子，叶子复用既有解析。 */
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

/** 把扁平 logic+conditions 提升为递归根组（供页面迁移到嵌套模型）。 */
export function flatToRootGroup(tree: RuleConditionTree): RuleConditionGroup {
  if (tree.root) return tree.root;
  return {
    id: nextNestedId("group"),
    logic: tree.logic,
    children: tree.conditions.map((condition) => ({ ...condition })),
  };
}

function conditionToDsl(condition: RuleCondition): RuleDslCondition {
  const node: RuleDslCondition = {
    fact: condition.fact.trim(),
    operator: condition.operator,
    ui: {
      id: condition.id,
      label: condition.label,
      valueKind: condition.valueKind,
    },
  };

  if (conditionNeedsValue(condition.operator)) {
    node.value = normalizeConditionValue(condition.value, condition.valueKind);
  }

  return node;
}

function dslConditionToTree(condition: unknown, index: number): RuleCondition {
  if (!isRecord(condition)) {
    throw new Error("规则 DSL 条件必须为对象");
  }
  const ui = isRecord(condition.ui) ? condition.ui : undefined;
  const inferredKind = inferValueKind(condition.value);
  return {
    id: readString(ui, "id", `condition-${index + 1}`),
    label: readString(ui, "label", `条件 ${index + 1}`),
    fact: readString(condition, "fact", ""),
    operator: readOperator(condition, "operator"),
    value: condition.value as RuleCondition["value"],
    valueKind: readValueKind(ui, "valueKind", inferredKind),
  };
}

function cloneTree(tree: RuleConditionTree): RuleConditionTree {
  return {
    logic: tree.logic,
    conditions: tree.conditions.map((condition) => ({
      ...condition,
      value: Array.isArray(condition.value) ? [...condition.value] : condition.value,
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
  const value = readString(source, key, "exists");
  const operators: RuleOperator[] = [
    "exists",
    "equals",
    "not_equals",
    "contains",
    "gt",
    "gte",
    "lt",
    "lte",
    "in",
    "not_in",
    "between",
    "unit_compare",
    "temporal",
    "derived",
  ];
  return operators.includes(value as RuleOperator) ? (value as RuleOperator) : "exists";
}

function readValueKind(source: unknown, key: string, fallback: RuleValueKind): RuleValueKind {
  const value = readString(source, key, fallback);
  const kinds: RuleValueKind[] = [
    "empty",
    "string",
    "number",
    "boolean",
    "list",
    "range",
    "measurement",
    "temporal",
    "derived",
  ];
  return kinds.includes(value as RuleValueKind) ? (value as RuleValueKind) : fallback;
}

function inferValueKind(value: unknown): RuleValueKind {
  if (typeof value === "number") return "number";
  if (typeof value === "boolean") return "boolean";
  if (Array.isArray(value)) return "list";
  if (value === undefined || value === null) return "empty";
  if (isRecord(value)) {
    if ("formula" in value || "parameters" in value) return "derived";
    if ("mode" in value || "window" in value || "referenceTime" in value) return "temporal";
    if ("analyte" in value || "comparison" in value) return "measurement";
    if ("min" in value || "max" in value) return "range";
  }
  return "string";
}

function cloneJsonRecord(value: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}
