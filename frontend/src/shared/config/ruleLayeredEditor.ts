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
  | "critical_value_report"
  | "drug_safety_review"
  | "insurance_policy_review"
  | "clinical_operator_review";

export type RuleLogic = "all" | "any";
export type { RuleOperator, RuleValueKind };
export type RuleSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type RuleActionCode =
  | "INFO"
  | "REMIND"
  | "STRONG_REMINDER"
  | "BLOCK"
  | "SUGGEST_ORDER"
  | "AUTO_DOCUMENT";
export type RuleIndicator = "info" | "warning" | "critical";
export type RuleClinicalSetting = "INPATIENT" | "OUTPATIENT" | "ED" | "FOLLOWUP";
export type RuleParameterValueType =
  | "CODE"
  | "TEXT"
  | "DECIMAL"
  | "INTEGER"
  | "BOOLEAN"
  | "VALUE_SET"
  | "ORG_SCOPE";
export type RuleConditionValue =
  | string
  | number
  | boolean
  | Array<string | number | boolean>
  | Record<string, unknown>;

export interface RuleParameterDefinition {
  key: string;
  label: string;
  valueType: RuleParameterValueType;
  required: boolean;
  description?: string;
}

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
  actionCode: RuleActionCode;
  atSeverity: RuleSeverity;
  indicator: RuleIndicator;
  summary: string;
  detail: string;
  source: {
    label: string;
    url?: string;
    evidenceLevel?: string;
  };
  suggestions: Array<{
    label: string;
    actionType: string;
    payload?: Record<string, unknown>;
  }>;
  overrideReasons: string[];
  requiresPhysicianConfirmation: boolean;
}

export interface RuleConditionTree {
  triggerPoint: ClinicalTriggerPoint;
  applicability: RuleApplicability;
  logic: RuleLogic;
  /** L2 叶子索引，供列表、统计与默认模板使用；DSL 权威结构始终由 root 归一化得到。 */
  conditions: RuleCondition[];
  /**
   * 递归条件根组（P1-2 嵌套支持，可选输入）。存在时作为权威条件结构，支持任意深度
   * 「条件组(all/any/可取反)+叶子」；缺失时由 conditions 显式归一化生成。
   */
  root?: RuleConditionGroup;
  actions: RuleActionDraft[];
  explanationSummary: string;
}

export interface RuleApplicability {
  population: {
    include?: Record<string, unknown>;
    exclude?: Record<string, unknown>;
  };
  orgScope: {
    groupIds?: string[];
    hospitalIds?: string[];
    deptIds?: string[];
  };
  settings: RuleClinicalSetting[];
  effective: {
    from?: string;
    to?: string;
    rolloutPercent: number;
  };
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

export const DEFAULT_CRITICAL_RETURN_MINUTES = 15;
export const DEFAULT_CRITICAL_OBSERVATION_CODE = "K";

export function createCriticalValueParameterDefinitions(): RuleParameterDefinition[] {
  return [
    {
      key: "observationCode",
      label: "检验项",
      valueType: "CODE",
      required: true,
      description: "真实检验结果编码，如血钾 K。",
    },
    {
      key: "criticalThreshold",
      label: "危急阈值",
      valueType: "DECIMAL",
      required: true,
      description: "达到或超过该数值后触发危急值回报。",
    },
    {
      key: "returnMinutes",
      label: "回报时限分钟",
      valueType: "INTEGER",
      required: true,
      description: "命中后要求完成回报、确认与记录的时限。",
    },
  ];
}

export function criticalValueReportDetail(minutes = DEFAULT_CRITICAL_RETURN_MINUTES) {
  return `命中后须在 ${minutes} 分钟内完成危急值回报、确认与记录，不自动开立或修改医嘱。`;
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
  applicability: RuleApplicability;
  when: Partial<Record<RuleLogic, RuleDslNode[]>> & { not?: RuleDslNode };
  then: RuleActionDraft[];
  meta?: {
    parameters?: RuleParameterDefinition[];
  };
  explain: {
    summary: string;
    authoring: {
      layer: "L2_VISUAL_TREE";
      conditionCount: number;
    };
  };
};

export function createDefaultRuleApplicability(): RuleApplicability {
  return {
    population: {},
    orgScope: {},
    settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
    effective: {
      rolloutPercent: 100,
    },
  };
}

const DEFAULT_ACTION: RuleActionDraft = {
  actionCode: "REMIND",
  atSeverity: "LOW",
  indicator: "info",
  summary: "规则命中，需人工复核",
  detail: "命中后提交人工审核，不自动写入医嘱。",
  source: {
    label: "规则版本来源",
  },
  suggestions: [],
  overrideReasons: [],
  requiresPhysicianConfirmation: true,
};

export function createRuleActionDraft(): RuleActionDraft {
  return cloneAction(DEFAULT_ACTION);
}

export const RULE_LAYER_TEMPLATES: RuleLayerTemplate[] = [
  {
    key: "clinical_quality_monitor",
    title: "临床质控阈值",
    description: "适合从真实上下文快照取一个数值字段，超过阈值后提交人工复核。",
    ruleType: "QUALITY",
    riskLevel: "MEDIUM",
    tree: {
      triggerPoint: "result-review",
      applicability: createDefaultRuleApplicability(),
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
      actions: [{ ...cloneAction(DEFAULT_ACTION), atSeverity: "MEDIUM", indicator: "warning" }],
      explanationSummary: "依据真实上下文快照中的数值字段进行确定性判断",
    },
  },
  {
    key: "critical_value_report",
    title: "危急值回报",
    description: "按检验结果字段、危急阈值和回报时限生成红线提醒草稿。",
    ruleType: "QUALITY",
    riskLevel: "CRITICAL",
    tree: {
      triggerPoint: "result-review",
      applicability: createDefaultRuleApplicability(),
      logic: "all",
      conditions: [
        {
          id: "condition-1",
          label: "危急检验结果",
          fact: "observations[].valueNumeric",
          expr: {
            field: "observations[].valueNumeric",
            select: "latest",
          },
          operator: "gte",
          value: "",
          valueKind: "number",
        },
      ],
      actions: [
        {
          ...cloneAction(DEFAULT_ACTION),
          actionCode: "STRONG_REMINDER",
          atSeverity: "CRITICAL",
          indicator: "critical",
          summary: "检验结果达到危急值，需立即回报并人工确认",
          detail: criticalValueReportDetail(),
          source: { label: "检验危急值管理制度" },
        },
      ],
      explanationSummary: "依据真实检验结果字段判断是否达到危急值",
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
      applicability: createDefaultRuleApplicability(),
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
      actions: [cloneAction(DEFAULT_ACTION)],
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
      applicability: createDefaultRuleApplicability(),
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
      actions: [{ ...cloneAction(DEFAULT_ACTION), atSeverity: "MEDIUM", indicator: "warning" }],
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
      applicability: createDefaultRuleApplicability(),
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
      actions: [
        {
          ...cloneAction(DEFAULT_ACTION),
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "critical",
        },
      ],
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
    applicability: cloneApplicability(tree.applicability),
    when: conditionNodeToDsl(root) as RuleDsl["when"],
    then: tree.actions.map(cloneAction),
    explain: {
      summary: tree.explanationSummary,
      authoring: {
        layer: "L2_VISUAL_TREE",
        conditionCount: countConditionLeaves(root),
      },
    },
  };
}

export function dslToConditionTree(
  dsl: unknown,
  triggerPoint: ClinicalTriggerPoint,
): RuleConditionTree {
  if (!isRecord(dsl) || !isRecord(dsl.when)) {
    throw new Error("规则 DSL 缺少 when 条件");
  }
  if (!isClinicalTriggerPoint(triggerPoint)) {
    throw new Error("规则缺少或包含不支持的临床触发场景");
  }

  const root = dslWhenToRootGroup(dsl.when);
  const logic = root.logic;
  const rawConditions = dsl.when[logic];
  if ((!Array.isArray(rawConditions) || rawConditions.length === 0) && root.children.length === 0) {
    throw new Error("规则 DSL 至少需要一个条件");
  }

  const then = Array.isArray(dsl.then) ? dsl.then : [];
  if (then.length === 0) {
    throw new Error("规则 DSL 至少需要一个 then 动作");
  }
  const explain = isRecord(dsl.explain) ? dsl.explain : undefined;
  const applicability = parseRuleApplicability(dsl.applicability);

  return {
    triggerPoint,
    applicability,
    logic,
    conditions: collectConditionLeaves(root),
    root,
    actions: then.map(parseAction),
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
    template: `${tree.explanationSummary}。命中后处置动作：${tree.actions
      .map((action) => action.summary)
      .join("；")}`,
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
    applicability: cloneApplicability(tree.applicability),
    logic: tree.logic,
    conditions: tree.conditions.map((condition) => ({
      ...condition,
      value: Array.isArray(condition.value) ? [...condition.value] : condition.value,
      expr: condition.expr ? normalizeConditionExpression(condition.expr) : undefined,
    })),
    root: tree.root ? cloneConditionGroup(tree.root) : undefined,
    actions: tree.actions.map(cloneAction),
    explanationSummary: tree.explanationSummary,
  };
}

export function parseRuleApplicability(value: unknown): RuleApplicability {
  if (!isRecord(value)) {
    throw new Error("规则 DSL 缺少 applicability 适用域");
  }
  if (!isRecord(value.population)) {
    throw new Error("规则 applicability.population 必须为对象");
  }
  const population: RuleApplicability["population"] = {};
  if (value.population.include !== undefined) {
    if (!isRecord(value.population.include)) {
      throw new Error("规则 applicability.population.include 必须为条件对象");
    }
    population.include = cloneJsonRecord(value.population.include);
  }
  if (value.population.exclude !== undefined) {
    if (!isRecord(value.population.exclude)) {
      throw new Error("规则 applicability.population.exclude 必须为条件对象");
    }
    population.exclude = cloneJsonRecord(value.population.exclude);
  }

  if (!isRecord(value.orgScope)) {
    throw new Error("规则 applicability.orgScope 必须为对象");
  }
  const orgScope = {
    groupIds: readStringArray(value.orgScope.groupIds, "groupIds"),
    hospitalIds: readStringArray(value.orgScope.hospitalIds, "hospitalIds"),
    deptIds: readStringArray(value.orgScope.deptIds, "deptIds"),
  };

  if (!Array.isArray(value.settings) || value.settings.length === 0) {
    throw new Error("规则 applicability.settings 至少选择一个临床场景");
  }
  const allowedSettings = ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"] as const;
  const settings = value.settings.map((setting) => {
    if (typeof setting !== "string" || !allowedSettings.includes(setting as RuleClinicalSetting)) {
      throw new Error("规则 applicability.settings 包含不支持的临床场景");
    }
    return setting as RuleClinicalSetting;
  });
  if (new Set(settings).size !== settings.length) {
    throw new Error("规则 applicability.settings 不允许重复值");
  }

  if (!isRecord(value.effective)) {
    throw new Error("规则 applicability.effective 必须为对象");
  }
  const rolloutPercent = value.effective.rolloutPercent;
  if (
    typeof rolloutPercent !== "number" ||
    !Number.isInteger(rolloutPercent) ||
    rolloutPercent < 0 ||
    rolloutPercent > 100
  ) {
    throw new Error("规则 applicability.effective.rolloutPercent 必须是 0 到 100 的整数");
  }
  const from = readIsoDate(value.effective.from, "from");
  const to = readIsoDate(value.effective.to, "to");
  if (from && to && from > to) {
    throw new Error("规则 applicability.effective.from 不能晚于 to");
  }

  return {
    population,
    orgScope: {
      ...(orgScope.groupIds.length > 0 ? { groupIds: orgScope.groupIds } : {}),
      ...(orgScope.hospitalIds.length > 0 ? { hospitalIds: orgScope.hospitalIds } : {}),
      ...(orgScope.deptIds.length > 0 ? { deptIds: orgScope.deptIds } : {}),
    },
    settings,
    effective: {
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
      rolloutPercent,
    },
  };
}

function cloneApplicability(value: RuleApplicability): RuleApplicability {
  return parseRuleApplicability(value);
}

function readStringArray(value: unknown, field: string): string[] {
  if (value === undefined) return [];
  if (!Array.isArray(value)) {
    throw new Error(`规则 applicability.orgScope.${field} 必须是字符串数组`);
  }
  const items = value.map((item) => {
    if (typeof item !== "string" || !item.trim()) {
      throw new Error(`规则 applicability.orgScope.${field} 仅允许非空字符串`);
    }
    return item.trim();
  });
  if (new Set(items).size !== items.length) {
    throw new Error(`规则 applicability.orgScope.${field} 不允许重复值`);
  }
  return items;
}

function readIsoDate(value: unknown, field: string): string | undefined {
  if (value === undefined || value === null || value === "") return undefined;
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error(`规则 applicability.effective.${field} 必须是 ISO 日期`);
  }
  return value;
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
  return value === "LOW" || value === "MEDIUM" || value === "HIGH" || value === "CRITICAL"
    ? value
    : fallback;
}

function parseAction(source: unknown): RuleActionDraft {
  if (!isRecord(source)) {
    throw new Error("规则 DSL then 动作必须为对象");
  }
  const actionCode = readString(source, "actionCode", "");
  const actionCodes: RuleActionCode[] = [
    "INFO",
    "REMIND",
    "STRONG_REMINDER",
    "BLOCK",
    "SUGGEST_ORDER",
    "AUTO_DOCUMENT",
  ];
  if (!actionCodes.includes(actionCode as RuleActionCode)) {
    throw new Error(`不支持的规则动作码: ${actionCode || "未填写"}`);
  }
  const atSeverity = readSeverity(source, "atSeverity", "" as RuleSeverity);
  if (!atSeverity) {
    throw new Error("规则 DSL 动作缺少 atSeverity");
  }
  const indicator = readString(source, "indicator", "");
  if (indicator !== "info" && indicator !== "warning" && indicator !== "critical") {
    throw new Error(`不支持的卡片指示级别: ${indicator || "未填写"}`);
  }
  const summary = readString(source, "summary", "").trim();
  const detail = readString(source, "detail", "").trim();
  const rawSource = isRecord(source.source) ? source.source : undefined;
  const sourceLabel = readString(rawSource, "label", "").trim();
  if (!summary || !detail || !sourceLabel) {
    throw new Error("规则 DSL 动作必须填写 summary、detail 与 source.label");
  }
  if (!Array.isArray(source.suggestions)) {
    throw new Error("规则 DSL 字段 suggestions 必须是数组");
  }
  if (!Array.isArray(source.overrideReasons)) {
    throw new Error("规则 DSL 字段 overrideReasons 必须是数组");
  }
  const rawSuggestions = source.suggestions;
  const rawReasons = source.overrideReasons;
  return {
    actionCode: actionCode as RuleActionCode,
    atSeverity,
    indicator,
    summary,
    detail,
    source: {
      label: sourceLabel,
      url: optionalString(rawSource, "url"),
      evidenceLevel: optionalString(rawSource, "evidenceLevel"),
    },
    suggestions: rawSuggestions.map((suggestion) => {
      if (!isRecord(suggestion)) {
        throw new Error("规则 DSL 建议项必须为对象");
      }
      const label = readString(suggestion, "label", "").trim();
      const actionType = readString(suggestion, "actionType", "").trim();
      if (!label || !actionType) {
        throw new Error("规则 DSL 建议项必须填写 label 与 actionType");
      }
      return {
        label,
        actionType,
        payload: isRecord(suggestion.payload) ? cloneJsonRecord(suggestion.payload) : undefined,
      };
    }),
    overrideReasons: rawReasons.map((reason) => {
      if (typeof reason !== "string" || !reason.trim()) {
        throw new Error("规则 DSL overrideReasons 仅允许非空文本");
      }
      return reason.trim();
    }),
    requiresPhysicianConfirmation:
      readBoolean(source, "requiresPhysicianConfirmation") ??
      requiresPhysicianConfirmation(actionCode as RuleActionCode, atSeverity),
  };
}

function optionalString(source: unknown, key: string): string | undefined {
  const value = readString(source, key, "").trim();
  return value || undefined;
}

export function requiresPhysicianConfirmation(
  actionCode: RuleActionCode,
  severity: RuleSeverity,
): boolean {
  return (
    severity === "HIGH" ||
    severity === "CRITICAL" ||
    actionCode === "BLOCK" ||
    actionCode === "STRONG_REMINDER" ||
    actionCode === "SUGGEST_ORDER"
  );
}

function cloneAction(action: RuleActionDraft): RuleActionDraft {
  return {
    ...action,
    source: { ...action.source },
    suggestions: action.suggestions.map((suggestion) => ({
      ...suggestion,
      payload: suggestion.payload ? cloneJsonRecord(suggestion.payload) : undefined,
    })),
    overrideReasons: [...action.overrideReasons],
  };
}

function cloneConditionValue(value: RuleCondition["value"]): RuleCondition["value"] {
  if (Array.isArray(value)) return [...value];
  if (isRecord(value)) return cloneJsonRecord(value);
  return value;
}

function cloneConditionGroup(group: RuleConditionGroup): RuleConditionGroup {
  return {
    ...group,
    children: group.children.map((child) =>
      isConditionGroup(child)
        ? cloneConditionGroup(child)
        : {
            ...child,
            value: cloneConditionValue(child.value),
            expr: child.expr ? normalizeConditionExpression(child.expr) : undefined,
          },
    ),
  };
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
