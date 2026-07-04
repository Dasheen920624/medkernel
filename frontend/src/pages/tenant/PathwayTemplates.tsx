import { useMemo, useState } from "react";
import {
  Alert,
  App,
  AutoComplete,
  Badge,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Form,
  Grid,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
} from "antd";
import type { BadgeProps, RadioChangeEvent, TableProps, TabsProps } from "antd";
import {
  ApartmentOutlined,
  CopyOutlined,
  DeleteOutlined,
  PlusOutlined,
  PlayCircleOutlined,
  SearchOutlined,
  SwapOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { FieldCatalogManager } from "@/shared/ui/condition/FieldCatalogManager";
import { AuthoringReadablePreview } from "@/shared/ui/condition/AuthoringReadablePreview";
import { StandardTermValueAutoComplete } from "@/shared/ui/condition/StandardTermValueAutoComplete";
import ConditionTreeEditor from "@/shared/ui/condition/ConditionTreeEditor";
import { buildFieldCatalogOptions } from "@/shared/config/contextFieldOptions";
import {
  countLeaves,
  createGroup,
  createLeaf,
  dslToRootGroup,
  hasUnresolvedFact,
  nodeToDsl,
  type RuleGroup,
  type RuleNode,
} from "@/shared/config/conditionModel";
import {
  useContextFieldCatalog,
  useContextSnapshotDetail,
  useContextSnapshots,
  useCreatePathwayTemplate,
  useAuthoringPreviewRun,
  useEvaluationIndicators,
  usePathwayTemplateDetail,
  usePathwayTemplates,
  useRuleDefinitions,
  useSimulatePathway,
} from "@/shared/api/hooks";
import type {
  ContextSnapshotSummary,
  AuthoringPreviewRunEvidence,
  AuthoringPreviewRunResponse,
  EvaluationIndicator,
  PathwayEdge,
  PathwayEdgeType,
  PathwayEntryMode,
  PathwayMilestone,
  PathwayNode,
  PathwayNodeType,
  PathwayOutcomeBinding,
  PathwayOutcomeScope,
  PathwaySimulationResponse,
  PathwaySimulationMode,
  PathwayTemplate,
  PathwayTemplateDetailResponse,
  PathwayTemplateLevel,
  PathwayTemplateStatus,
  RuleDefinition,
  SpecialtyMetricBinding,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { customerDisplayText, customerEnumLabel } from "@/shared/config/customerLabels";
import PathwayGraphEditor from "./PathwayGraphEditor";
import {
  createConnectedEdge,
  removeNodeAtIndexWithEdges,
  writeNodePosition,
  type PathwayGraphPosition,
} from "./pathwayGraphModel";
import styles from "./RulePathwayAuthoring.module.css";

const { TextArea } = Input;
const { Option } = Select;

const PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE = 20;
const PATHWAY_RULE_REFERENCE_PAGE_SIZE = 20;

type PathwayBadgeStatus = Exclude<BadgeProps["status"], undefined>;

const PATHWAY_CONTENT_STATUS: Record<
  PathwayTemplateStatus,
  { status: PathwayBadgeStatus; text: string }
> = {
  DRAFT: { status: "warning", text: "设计中" },
  PUBLISHED: { status: "processing", text: "已发布" },
  OFFLINE: { status: "default", text: "已下线" },
};

const PATHWAY_DEPLOYMENT_STATUS: Record<string, { status: PathwayBadgeStatus; text: string }> = {
  DRAFT: { status: "warning", text: "待提交" },
  IN_REVIEW: { status: "processing", text: "安全复核中" },
  APPROVED: { status: "processing", text: "已验证待激活" },
  PUBLISHED: { status: "success", text: "运行中" },
  DEPRECATED: { status: "default", text: "已弃用" },
  RETIRED: { status: "default", text: "已退役" },
};

function pathwayContentStatus(status: PathwayTemplateStatus) {
  const config = PATHWAY_CONTENT_STATUS[status] ?? {
    status: "default" as PathwayBadgeStatus,
    text: customerEnumLabel(status),
  };
  return <Badge status={config.status} text={config.text} />;
}

function pathwayDeploymentStatus(status: string) {
  const config = PATHWAY_DEPLOYMENT_STATUS[status] ?? {
    status: "default" as PathwayBadgeStatus,
    text: customerEnumLabel(status),
  };
  return <Badge status={config.status} text={config.text} />;
}

function pathwayEntryModeText(mode: PathwayEntryMode | string | undefined) {
  if (mode === "MANUAL_CONFIRM") return "人工确认入径";
  return "自动建议入径";
}

function evidenceText(
  value: string | number | null | undefined,
  evidenceDetailsEnabled: boolean,
  businessText: string,
) {
  if (!evidenceDetailsEnabled) return businessText;
  if (value === undefined || value === null || value === "") return "未返回";
  return String(value);
}

function pathwayIdentityText(
  templateCode: string | null | undefined,
  evidenceDetailsEnabled: boolean,
) {
  return evidenceText(templateCode, evidenceDetailsEnabled, "临床路径已登记");
}

function pathwayVersionText(version: number | null | undefined, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) {
    return version ? `v${version}.0` : "未形成版本";
  }
  return version ? `第 ${version} 版已形成` : "尚未形成版本";
}

function snapshotBusinessLabel(index: number) {
  return `第 ${index + 1} 个临床快照`;
}

function replaySnapshotBusinessLabel(index: number) {
  return `第 ${index + 1} 个回放快照`;
}

function snapshotButtonLabel(
  snapshot: ContextSnapshotSummary,
  index: number,
  evidenceDetailsEnabled: boolean,
) {
  return evidenceDetailsEnabled ? snapshot.snapshotId : snapshotBusinessLabel(index);
}

function snapshotAssociationText(
  snapshot: Pick<ContextSnapshotSummary, "patientId" | "encounterId">,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) {
    return `患者 ${snapshot.patientId || "-"} · 就诊 ${snapshot.encounterId || "-"}`;
  }
  return "患者已关联 · 就诊已关联";
}

function selectedSnapshotText(
  snapshotId: string | null | undefined,
  evidenceDetailsEnabled: boolean,
) {
  return evidenceText(snapshotId, evidenceDetailsEnabled, "临床快照已选择");
}

function nodeEvidenceText(nodeCode: string, index: number, evidenceDetailsEnabled: boolean) {
  return evidenceDetailsEnabled ? nodeCode : `第 ${index + 1} 个路径节点`;
}

type PathwayNodeDraft = {
  nodeCode: string;
  name: string;
  nodeType: PathwayNodeType;
  milestoneCode?: string;
  sortOrder: number;
  responsibleRole?: string;
  accountableRole?: string;
  consultedRoles?: string[];
  informedRoles?: string[];
  timeWindowMinutes?: number;
  terminal: boolean;
  disabled?: boolean;
  config?: unknown;
};

type PathwayMilestoneDraft = {
  phaseCode: string;
  phaseName: string;
  milestoneCode: string;
  name: string;
  dayOffset?: number;
  expectedOffsetMinutes?: number;
  achievementCriteria?: unknown;
  sortOrder: number;
};

type PathwayEdgeDraft = {
  edgeCode: string;
  fromNodeCode: string;
  toNodeCode: string;
  edgeType: PathwayEdgeType;
  condition?: unknown;
  priority: number;
};

type PathwayMetricBindingDraft = {
  nodeCode: string;
  metricCode: string;
  required: boolean;
};

type PathwayOutcomeBindingDraft = {
  scope: PathwayOutcomeScope;
  refCode?: string;
  indicatorCode: string;
};

type PathwayOutcomeBindingInput = {
  scope?: PathwayOutcomeScope;
  refCode?: string | null;
  indicatorCode?: string;
};

type PathwayDslPayload = {
  startNodeCode?: string;
  milestones?: PathwayMilestoneDraft[];
  nodes?: PathwayNodeDraft[];
  edges?: PathwayEdgeDraft[];
  metricBindings?: PathwayMetricBindingDraft[];
  outcomeBindings?: PathwayOutcomeBindingDraft[];
};

type PathwayNodeFormValue = {
  nodeCode?: string;
  name?: string;
  nodeType?: PathwayNodeType;
  milestoneCode?: string;
  sortOrder?: number;
  responsibleRole?: string;
  accountableRole?: string;
  consultedRoles?: string[];
  informedRoles?: string[];
  timeWindowMinutes?: number;
  terminal?: boolean;
  disabled?: boolean;
  metricCode?: string;
  config?: object;
};

type ClockSlaConfigValue = {
  baselineEvent?: "NODE_START" | "PATHWAY_ENTRY" | "ADMISSION";
  minMinutes?: number;
  targetMinutes?: number;
  maxMinutes?: number;
  reportMinutes?: number;
};

type PathwayMilestoneFormValue = {
  phaseCode?: string;
  phaseName?: string;
  milestoneCode?: string;
  name?: string;
  dayOffset?: number;
  expectedOffsetMinutes?: number;
  achievementCriteria?: object;
  sortOrder?: number;
};

type PathwayEdgeFormValue = {
  edgeCode?: string;
  fromNodeCode?: string;
  toNodeCode?: string;
  edgeType?: PathwayEdgeType;
  guardMode?: "INLINE" | "RULE";
  ruleRef?: string;
  ruleAssetId?: string;
  conditionTree?: RuleGroup;
  conditionFact?: string;
  conditionOperator?: "equals" | "not_equals" | "gt" | "gte" | "lt" | "lte";
  conditionValue?: string;
  conditionValueKind?: "string" | "number" | "boolean";
  conditionJson?: string;
  priority?: number;
};

type PathwayCriteriaFormValue = {
  includeTree?: RuleGroup;
  excludeTree?: RuleGroup;
};

type PathwayTemplateFormValue = {
  templateCode: string;
  name: string;
  diseaseCode: string;
  templateLevel: PathwayTemplateLevel;
  entryMode: PathwayEntryMode;
  startNodeCode: string;
  sourceRef: string;
  description?: string;
  entryCriteria?: PathwayCriteriaFormValue;
  exitCriteria?: PathwayCriteriaFormValue;
  milestones?: PathwayMilestoneFormValue[];
  nodes?: PathwayNodeFormValue[];
  edges?: PathwayEdgeFormValue[];
  outcomeBindings?: PathwayOutcomeBindingDraft[];
};

type SnapshotQuery = {
  patientId?: string;
  encounterId?: string;
  status: "ACTIVE";
  page: number;
  size: number;
};

type PathwayPrototypeKey = "blank" | "basic_cycle";

const templateLevelOptions: Array<{ value: PathwayTemplateLevel; label: string }> = [
  { value: "STANDARD", label: "平台标准路径" },
  { value: "HOSPITAL", label: "医院路径" },
  { value: "DEPARTMENT", label: "科室路径" },
  { value: "SPECIALTY", label: "专科路径" },
];

function pathwayTemplateLevelText(level?: PathwayTemplateLevel | string | null) {
  return (
    templateLevelOptions.find((option) => option.value === level)?.label ?? customerEnumLabel(level)
  );
}

const pathwayEntryModeOptions: Array<{ value: PathwayEntryMode; label: string }> = [
  { value: "AUTO_SUGGEST", label: "自动建议入径" },
  { value: "MANUAL_CONFIRM", label: "人工确认入径" },
];

const nodeTypeOptions: Array<{ value: PathwayNodeType; label: string }> = [
  { value: "SCREENING", label: "筛查" },
  { value: "ASSESSMENT", label: "评估" },
  { value: "EXAM", label: "检查" },
  { value: "LAB", label: "检验" },
  { value: "MEDICATION", label: "用药" },
  { value: "SURGERY", label: "手术" },
  { value: "NURSING", label: "护理" },
  { value: "REHAB", label: "康复" },
  { value: "DISCHARGE", label: "出院" },
  { value: "FOLLOWUP", label: "随访" },
  { value: "QUALITY", label: "质控" },
  { value: "DECISION", label: "决策分支" },
  { value: "PARALLEL", label: "并行或汇合" },
  { value: "WAIT_TIMER", label: "等待计时" },
  { value: "MANUAL_GATE", label: "人工确认节点" },
  { value: "ORDER_SET", label: "医嘱套餐" },
];

const clockBaselineEventOptions = [
  { value: "NODE_START", label: "节点开始" },
  { value: "PATHWAY_ENTRY", label: "患者入径" },
  { value: "ADMISSION", label: "入院时间" },
];

function clockBaselineEventText(value?: unknown) {
  if (typeof value !== "string" || value.trim().length === 0) return "节点开始";
  return clockBaselineEventOptions.find((option) => option.value === value)?.label ?? "自定义基准";
}

const edgeTypeOptions: Array<{ value: PathwayEdgeType; label: string }> = [
  { value: "DEFAULT", label: "默认流转" },
  { value: "CONDITION", label: "条件流转" },
  { value: "RISK_STRATIFICATION", label: "风险分层" },
  { value: "PATIENT_CHOICE", label: "患者选择" },
  { value: "RESOURCE_UNAVAILABLE", label: "资源不可用" },
  { value: "PHYSICIAN_DECISION", label: "医师决策" },
  { value: "ROLLBACK", label: "回退" },
  { value: "JOIN", label: "并行汇合" },
];

const outcomeScopeOptions: Array<{ value: PathwayOutcomeScope; label: string }> = [
  { value: "TEMPLATE", label: "全路径" },
  { value: "PHASE", label: "阶段" },
  { value: "MILESTONE", label: "里程碑" },
];

const simulationModeOptions: Array<{ value: PathwaySimulationMode; label: string }> = [
  { value: "SINGLE_SNAPSHOT", label: "单快照" },
  { value: "QUEUE_REPLAY", label: "队列回放" },
  { value: "TIME_MACHINE", label: "时光机" },
];

const pathwayPrototypeOptions: Array<{
  key: PathwayPrototypeKey;
  title: string;
  description: string;
}> = [
  {
    key: "blank",
    title: "空白路径",
    description: "从 L1 基本信息与 L2 节点画布手工配置。",
  },
  {
    key: "basic_cycle",
    title: "基础节点闭环",
    description: "生成评估到处置确认的两节点起始结构，由医院按专科、病种和岗位继续配置。",
  },
];

const pathwayConditionOperatorOptions = [
  { value: "equals", label: "等于" },
  { value: "not_equals", label: "不等于" },
  { value: "gt", label: "大于" },
  { value: "gte", label: "大于等于" },
  { value: "lt", label: "小于" },
  { value: "lte", label: "小于等于" },
];

const pathwayConditionValueKindOptions = [
  { value: "string", label: "文本" },
  { value: "number", label: "数值" },
  { value: "boolean", label: "布尔" },
];

function cleanText(value?: string | null) {
  const normalized = value?.trim();
  return normalized || undefined;
}

function nodeTypeText(type?: PathwayNodeType | string | null) {
  if (!type) return "未设置";
  return nodeTypeOptions.find((option) => option.value === type)?.label ?? customerEnumLabel(type);
}

function edgeTypeText(type?: PathwayEdgeType | string | null) {
  if (!type) return "未设置";
  return edgeTypeOptions.find((option) => option.value === type)?.label ?? customerEnumLabel(type);
}

function parseConditionJson(value?: string) {
  const normalized = cleanText(value);
  if (!normalized) return undefined;
  try {
    return JSON.parse(normalized) as unknown;
  } catch {
    throw new Error("条件配置格式不合法，请检查后再提交。");
  }
}

function parseLooseJson(value?: string | null) {
  const normalized = cleanText(value);
  if (!normalized) return undefined;
  try {
    return JSON.parse(normalized) as unknown;
  } catch {
    return normalized;
  }
}

function normalizeRoleList(roles?: string[]) {
  return Array.from(
    new Set(
      (roles ?? []).map((role) => cleanText(role)).filter((role): role is string => Boolean(role)),
    ),
  );
}

function parseRoleListJson(value?: string | null) {
  const parsed = parseLooseJson(value);
  return Array.isArray(parsed)
    ? normalizeRoleList(parsed.filter((role): role is string => typeof role === "string"))
    : [];
}

function roleListText(roles?: string[]) {
  const normalized = normalizeRoleList(roles);
  return normalized.length > 0 ? normalized.join("、") : "无";
}

function raciSummary(node: {
  responsibleRole?: string;
  accountableRole?: string;
  consultedRoles?: string[];
  informedRoles?: string[];
}) {
  const responsible = cleanText(node.responsibleRole) ?? "未配置";
  const accountable = cleanText(node.accountableRole) ?? responsible;
  return `R ${responsible} / A ${accountable} / C ${roleListText(node.consultedRoles)} / I ${roleListText(node.informedRoles)}`;
}

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2);
}

function normalizePathwayConditionValue(value?: string, kind = "string") {
  const normalized = cleanText(value);
  if (normalized === undefined) return undefined;
  if (kind === "number") {
    const numeric = Number(normalized);
    return Number.isFinite(numeric) ? numeric : normalized;
  }
  if (kind === "boolean") {
    return normalized.toLowerCase() === "true";
  }
  return normalized;
}

function inferPathwayConditionValueKind(
  value: unknown,
): NonNullable<PathwayEdgeFormValue["conditionValueKind"]> {
  if (typeof value === "number") return "number";
  if (typeof value === "boolean") return "boolean";
  return "string";
}

function normalizeEdgeCondition(edge: PathwayEdgeFormValue) {
  if (edge.guardMode === "RULE" || cleanText(edge.ruleRef)) {
    const ruleRef = cleanText(edge.ruleRef);
    const ruleAssetId = cleanText(edge.ruleAssetId);
    if (!ruleRef) {
      throw new Error("规则守卫必须选择已发布规则。");
    }
    if (!ruleAssetId) {
      throw new Error("规则守卫缺少稳定规则资产标识。");
    }
    return {
      ruleRef,
      ruleAssetId,
    };
  }
  if (
    edge.conditionTree &&
    countLeaves(edge.conditionTree) > 0 &&
    !hasUnresolvedFact(edge.conditionTree)
  ) {
    return nodeToDsl(edge.conditionTree);
  }
  const fact = cleanText(edge.conditionFact);
  if (fact) {
    return {
      fact,
      operator: edge.conditionOperator ?? "equals",
      value: normalizePathwayConditionValue(edge.conditionValue, edge.conditionValueKind),
    };
  }
  return parseConditionJson(edge.conditionJson);
}

function createDefaultEdgeConditionTree(): RuleGroup {
  return createGroup({
    logic: "all",
    children: [
      createLeaf({
        label: "路径边条件",
        fact: "",
        operator: "equals",
        value: "",
        valueKind: "string",
      }),
    ],
  });
}

function createDefaultPathwayCriteriaTree(label: string): RuleGroup {
  return createGroup({
    logic: "all",
    children: [
      createLeaf({
        label,
        fact: "",
        operator: "equals",
        value: "",
        valueKind: "string",
      }),
    ],
  });
}

function hasConfiguredValue(value: unknown) {
  if (value === undefined || value === null) return false;
  if (typeof value === "string") return cleanText(value) !== undefined;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === "object") return Object.keys(value).length > 0;
  return true;
}

function hasCriteriaInput(node: RuleNode): boolean {
  if (node.kind === "leaf") {
    return cleanText(node.fact) !== undefined || hasConfiguredValue(node.value);
  }
  return node.children.some(hasCriteriaInput);
}

function normalizeCriteriaTree(tree: RuleGroup | undefined, label: string) {
  if (!tree || !hasCriteriaInput(tree)) {
    return undefined;
  }
  if (countLeaves(tree) === 0 || hasUnresolvedFact(tree)) {
    throw new Error(`${label}存在未填写的上下文字段，请补全后再提交。`);
  }
  return nodeToDsl(tree);
}

function normalizePathwayCriteria(criteria: PathwayCriteriaFormValue | undefined, label: string) {
  const include = normalizeCriteriaTree(criteria?.includeTree, `${label}纳入条件`);
  const exclude = normalizeCriteriaTree(criteria?.excludeTree, `${label}排除条件`);
  return {
    ...(include ? { include } : {}),
    ...(exclude ? { exclude } : {}),
  };
}

function normalizeNodes(nodes?: PathwayNodeFormValue[]) {
  return (nodes ?? [])
    .filter((node) => cleanText(node.nodeCode) || cleanText(node.name))
    .map<PathwayNodeDraft>((node, index) => {
      const timeWindowMinutes =
        typeof node.timeWindowMinutes === "number" && node.timeWindowMinutes > 0
          ? node.timeWindowMinutes
          : undefined;
      const responsibleRole = cleanText(node.responsibleRole) ?? "责任医生";
      const accountableRole = cleanText(node.accountableRole) ?? responsibleRole;
      return {
        nodeCode: cleanText(node.nodeCode) ?? "",
        name: cleanText(node.name) ?? "",
        nodeType: node.nodeType ?? "ASSESSMENT",
        milestoneCode: cleanText(node.milestoneCode),
        sortOrder: Number(node.sortOrder ?? index + 1),
        responsibleRole,
        accountableRole,
        consultedRoles: normalizeRoleList(node.consultedRoles),
        informedRoles: normalizeRoleList(node.informedRoles),
        timeWindowMinutes,
        terminal: Boolean(node.terminal),
        disabled: Boolean(node.disabled),
        config: normalizeNodeConfig(node.config, timeWindowMinutes),
      };
    });
}

function normalizeMilestones(milestones?: PathwayMilestoneFormValue[]) {
  return (milestones ?? [])
    .filter(
      (milestone) =>
        cleanText(milestone.phaseCode) ||
        cleanText(milestone.phaseName) ||
        cleanText(milestone.milestoneCode) ||
        cleanText(milestone.name),
    )
    .map<PathwayMilestoneDraft>((milestone, index) => ({
      phaseCode: cleanText(milestone.phaseCode) ?? "",
      phaseName: cleanText(milestone.phaseName) ?? "",
      milestoneCode: cleanText(milestone.milestoneCode) ?? "",
      name: cleanText(milestone.name) ?? "",
      dayOffset:
        typeof milestone.dayOffset === "number" && milestone.dayOffset >= 0
          ? milestone.dayOffset
          : undefined,
      expectedOffsetMinutes:
        typeof milestone.expectedOffsetMinutes === "number" && milestone.expectedOffsetMinutes >= 0
          ? milestone.expectedOffsetMinutes
          : undefined,
      achievementCriteria: normalizeNodeConfig(milestone.achievementCriteria),
      sortOrder: Number(milestone.sortOrder ?? index + 1),
    }));
}

function normalizeNodeConfig(value: unknown, timeWindowMinutes?: number): object | undefined {
  const source =
    typeof value === "object" && value !== null && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {};
  const next: Record<string, unknown> = {};
  for (const [key, item] of Object.entries(source)) {
    if (key === "clockSla") continue;
    if (typeof item === "string") {
      const text = cleanText(item);
      if (text !== undefined) next[key] = text;
      continue;
    }
    if (hasConfiguredValue(item)) next[key] = item;
  }
  const clockSla = normalizeClockSlaConfig(source.clockSla, timeWindowMinutes);
  if (clockSla) next.clockSla = clockSla;
  return Object.keys(next).length > 0 ? next : undefined;
}

function configText(config: unknown, key: string) {
  if (typeof config !== "object" || config === null || Array.isArray(config)) return undefined;
  const value = (config as Record<string, unknown>)[key];
  return typeof value === "string" ? cleanText(value) : undefined;
}

function configObject(config: unknown, key: string) {
  if (typeof config !== "object" || config === null || Array.isArray(config)) return undefined;
  const value = (config as Record<string, unknown>)[key];
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

function normalizeClockSlaConfig(value: unknown, timeWindowMinutes?: number) {
  if (!timeWindowMinutes || timeWindowMinutes <= 0) return undefined;
  const source =
    typeof value === "object" && value !== null && !Array.isArray(value)
      ? (value as ClockSlaConfigValue)
      : {};
  const baselineEvent = source.baselineEvent ?? "NODE_START";
  const minMinutes =
    typeof source.minMinutes === "number" && source.minMinutes >= 0 ? source.minMinutes : 0;
  const targetMinutes =
    typeof source.targetMinutes === "number" && source.targetMinutes > 0
      ? source.targetMinutes
      : timeWindowMinutes;
  const maxMinutes =
    typeof source.maxMinutes === "number" && source.maxMinutes >= 0
      ? source.maxMinutes
      : targetMinutes;
  const reportMinutes =
    typeof source.reportMinutes === "number" && source.reportMinutes >= 0
      ? source.reportMinutes
      : Math.floor((targetMinutes + maxMinutes) / 2);
  return {
    baselineEvent,
    minMinutes,
    targetMinutes,
    maxMinutes,
    escalations: [
      { level: "REMINDER", afterMinutes: targetMinutes },
      { level: "REPORT", afterMinutes: reportMinutes },
      { level: "QUALITY_RECORD", afterMinutes: maxMinutes },
    ],
  };
}

function clockSlaError(node: PathwayNodeDraft) {
  if (!node.timeWindowMinutes || node.timeWindowMinutes <= 0) return undefined;
  const clockSla = configObject(node.config, "clockSla");
  if (!clockSla) return `关键时钟节点 ${node.nodeCode} 必须配置时窗校验规则`;
  const minMinutes = Number(clockSla.minMinutes);
  const targetMinutes = Number(clockSla.targetMinutes);
  const maxMinutes = Number(clockSla.maxMinutes);
  if (
    !Number.isFinite(minMinutes) ||
    !Number.isFinite(targetMinutes) ||
    !Number.isFinite(maxMinutes)
  ) {
    return `关键时钟节点 ${node.nodeCode} 的时窗校验分钟必须完整`;
  }
  if (
    minMinutes < 0 ||
    targetMinutes <= 0 ||
    maxMinutes < targetMinutes ||
    minMinutes > targetMinutes
  ) {
    return `关键时钟节点 ${node.nodeCode} 的时窗校验分钟必须满足最早 <= 目标 <= 最晚`;
  }
  return undefined;
}

function validateRichNodeContracts(nodes: PathwayNodeDraft[], edges: PathwayEdgeDraft[]) {
  const outgoingByNode = new Map<string, PathwayEdgeDraft[]>();
  for (const edge of edges) {
    const list = outgoingByNode.get(edge.fromNodeCode) ?? [];
    list.push(edge);
    outgoingByNode.set(edge.fromNodeCode, list);
  }
  for (const node of nodes) {
    const outgoing = outgoingByNode.get(node.nodeCode) ?? [];
    if (node.nodeType === "DECISION") {
      const conditionEdges = outgoing.filter((edge) => edge.edgeType === "CONDITION");
      if (outgoing.length < 2 || conditionEdges.length === 0) {
        return `决策节点 ${node.nodeCode} 至少需要一个条件分支和一个兜底分支`;
      }
      if (!outgoing.some((edge) => edge.edgeType === "DEFAULT")) {
        return `决策节点 ${node.nodeCode} 必须配置默认兜底分支`;
      }
      if (conditionEdges.some((edge) => edge.condition === undefined)) {
        return `决策节点 ${node.nodeCode} 的条件分支必须配置守卫条件`;
      }
    }
    if (node.nodeType === "PARALLEL") {
      const hasFork = outgoing.length >= 2;
      const hasJoin = outgoing.some((edge) => edge.edgeType === "JOIN");
      if (!hasFork && !hasJoin) {
        return `并行节点 ${node.nodeCode} 缺少并行分支或 JOIN 汇合边`;
      }
    }
    if (node.nodeType === "WAIT_TIMER") {
      const hasClock = configText(node.config, "clock") !== undefined;
      const hasTimerGuard = outgoing.some((edge) => edge.edgeType === "CONDITION");
      if (!hasClock && !node.timeWindowMinutes) {
        return `等待计时节点 ${node.nodeCode} 必须填写计时规则或时窗分钟`;
      }
      if (!hasTimerGuard) {
        return `等待计时节点 ${node.nodeCode} 必须配置计时条件边`;
      }
    }
    const clockError = clockSlaError(node);
    if (clockError) return clockError;
    if (!node.responsibleRole) {
      return `节点 ${node.nodeCode} 必须填写责任角色`;
    }
    if (!node.accountableRole) {
      return `节点 ${node.nodeCode} 必须填写签责角色`;
    }
    if (node.nodeType === "ORDER_SET" && !configText(node.config, "orderSetRef")) {
      return `医嘱套餐节点 ${node.nodeCode} 必须填写医嘱套餐引用`;
    }
  }
  return undefined;
}

function richNodeConfigSummary(node: PathwayNode, evidenceDetailsEnabled = true) {
  const config = parseLooseJson(node.configJson);
  const orderSetRef = configText(config, "orderSetRef");
  if (orderSetRef) {
    return evidenceDetailsEnabled ? `医嘱套餐 ${orderSetRef}` : "医嘱套餐已关联";
  }
  const clock = configText(config, "clock");
  if (clock) return evidenceDetailsEnabled ? `计时规则 ${clock}` : "计时规则已配置";
  const clockSla = configObject(config, "clockSla");
  if (clockSla) {
    const targetMinutes = clockSla.targetMinutes ?? "-";
    return evidenceDetailsEnabled
      ? `时窗校验 ${clockBaselineEventText(clockSla.baselineEvent)} / 目标 ${targetMinutes} 分钟`
      : `时窗校验已配置 / 目标 ${targetMinutes} 分钟`;
  }
  return "无";
}

function normalizeEdges(edges?: PathwayEdgeFormValue[]) {
  return (edges ?? [])
    .filter(
      (edge) =>
        cleanText(edge.edgeCode) || cleanText(edge.fromNodeCode) || cleanText(edge.toNodeCode),
    )
    .map<PathwayEdgeDraft>((edge, index) => ({
      edgeCode: cleanText(edge.edgeCode) ?? "",
      fromNodeCode: cleanText(edge.fromNodeCode) ?? "",
      toNodeCode: cleanText(edge.toNodeCode) ?? "",
      edgeType: edge.edgeType ?? "DEFAULT",
      condition: normalizeEdgeCondition(edge),
      priority: Number(edge.priority ?? index + 1),
    }));
}

function normalizeMetricBindings(nodes?: PathwayNodeFormValue[]) {
  return (nodes ?? [])
    .filter((node) => !node.disabled && cleanText(node.nodeCode) && cleanText(node.metricCode))
    .map<PathwayMetricBindingDraft>((node) => ({
      nodeCode: cleanText(node.nodeCode) ?? "",
      metricCode: cleanText(node.metricCode) ?? "",
      required: true,
    }));
}

function normalizeOutcomeBindings(bindings?: PathwayOutcomeBindingInput[]) {
  return (bindings ?? [])
    .filter((binding) => cleanText(binding.indicatorCode))
    .map<PathwayOutcomeBindingDraft>((binding) => ({
      scope: binding.scope ?? "TEMPLATE",
      refCode: binding.scope === "TEMPLATE" ? undefined : cleanText(binding.refCode),
      indicatorCode: cleanText(binding.indicatorCode) ?? "",
    }));
}

function outcomeScopeText(scope?: PathwayOutcomeScope | string | null) {
  if (scope === "PHASE") return "阶段";
  if (scope === "MILESTONE") return "里程碑";
  return "全路径";
}

function outcomeRefText(
  binding: Pick<PathwayOutcomeBinding, "scope" | "refCode">,
  evidenceDetailsEnabled = true,
) {
  if (binding.scope === "TEMPLATE") return "全路径";
  if (evidenceDetailsEnabled) return binding.refCode || "-";
  return binding.scope === "MILESTONE" ? "里程碑已关联" : "阶段已关联";
}

function outcomeBindingKey(
  binding: Pick<PathwayOutcomeBinding, "scope" | "refCode" | "indicatorCode">,
) {
  return `${binding.scope}:${binding.refCode ?? "TEMPLATE"}:${binding.indicatorCode}`;
}

function buildDraftDsl(
  milestones?: PathwayMilestoneFormValue[],
  nodes?: PathwayNodeFormValue[],
  edges?: PathwayEdgeFormValue[],
  outcomeBindings?: PathwayOutcomeBindingDraft[],
) {
  return {
    milestones: normalizeMilestones(milestones),
    nodes: normalizeNodes(nodes),
    edges: normalizeEdges(edges),
    metricBindings: normalizeMetricBindings(nodes),
    outcomeBindings: normalizeOutcomeBindings(outcomeBindings),
  };
}

function buildDraftDslPreview(
  milestones?: PathwayMilestoneFormValue[],
  nodes?: PathwayNodeFormValue[],
  edges?: PathwayEdgeFormValue[],
  outcomeBindings?: PathwayOutcomeBindingDraft[],
) {
  let normalizedEdges: PathwayEdgeDraft[] = [];
  try {
    normalizedEdges = normalizeEdges(edges);
  } catch {
    normalizedEdges = [];
  }
  return formatJson({
    milestones: normalizeMilestones(milestones),
    nodes: normalizeNodes(nodes),
    edges: normalizedEdges,
    metricBindings: normalizeMetricBindings(nodes),
    outcomeBindings: normalizeOutcomeBindings(outcomeBindings),
  });
}

function buildDetailDslPreview(detail: PathwayTemplateDetailResponse) {
  return formatJson({
    template: {
      templateCode: detail.template.templateCode,
      diseaseCode: detail.template.diseaseCode,
      templateLevel: detail.template.templateLevel,
      templateVersion: detail.template.templateVersion,
      entryMode: detail.template.entryMode,
      startNodeCode: detail.template.startNodeCode,
      sourceRef: detail.template.sourceRef,
      entryCriteria: parseLooseJson(detail.template.entryCriteriaJson),
      exitCriteria: parseLooseJson(detail.template.exitCriteriaJson),
    },
    milestones: detail.milestones.map((milestone) => ({
      phaseCode: milestone.phaseCode,
      phaseName: milestone.phaseName,
      milestoneCode: milestone.milestoneCode,
      name: milestone.name,
      dayOffset: milestone.dayOffset,
      expectedOffsetMinutes: milestone.expectedOffsetMinutes,
      achievementCriteria: parseLooseJson(milestone.achievementCriteriaJson),
      sortOrder: milestone.sortOrder,
    })),
    nodes: detail.nodes.map((node) => ({
      nodeCode: node.nodeCode,
      name: node.name,
      nodeType: node.nodeType,
      milestoneCode: node.milestoneCode,
      sortOrder: node.sortOrder,
      responsibleRole: node.responsibleRole,
      accountableRole: node.accountableRole,
      consultedRoles: parseRoleListJson(node.consultedRolesJson),
      informedRoles: parseRoleListJson(node.informedRolesJson),
      timeWindowMinutes: node.timeWindowMinutes,
      terminal: node.terminalFlag,
      disabled: Boolean(node.disabledFlag),
      config: parseLooseJson(node.configJson),
    })),
    edges: detail.edges.map((edge) => ({
      edgeCode: edge.edgeCode,
      fromNodeCode: edge.fromNodeCode,
      toNodeCode: edge.toNodeCode,
      edgeType: edge.edgeType,
      condition: parseLooseJson(edge.conditionJson),
      priority: edge.priority,
    })),
    metricBindings: detail.metricBindings.map((binding) => ({
      nodeCode: binding.nodeCode,
      metricCode: binding.metricCode,
    })),
    outcomeBindings: (detail.outcomeBindings ?? []).map((binding) => ({
      scope: binding.scope,
      refCode: binding.scope === "TEMPLATE" ? undefined : binding.refCode,
      indicatorCode: binding.indicatorCode,
    })),
  });
}

function mappingEntries(mapping?: Record<string, string>) {
  return Object.entries(mapping ?? {});
}

function milestoneDayText(dayOffset?: number) {
  return typeof dayOffset === "number" ? `第 ${dayOffset} 天` : "未设天序";
}

function milestoneOptionLabel(
  milestone:
    | PathwayMilestoneDraft
    | PathwayMilestoneFormValue
    | Pick<PathwayMilestone, "phaseCode" | "phaseName" | "milestoneCode" | "name" | "dayOffset">,
) {
  const phase = cleanText(milestone.phaseName) ?? cleanText(milestone.phaseCode) ?? "未命名阶段";
  const name = cleanText(milestone.name) ?? "未命名里程碑";
  return `${phase} / ${milestoneDayText(milestone.dayOffset)} / ${name}`;
}

function normalizeEdgesForCanvas(edges?: PathwayEdgeFormValue[]) {
  try {
    return normalizeEdges(edges);
  } catch {
    return [];
  }
}

function parsePathwayDslJson(value: string): PathwayDslPayload {
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error();
    }
    const payload = parsed as PathwayDslPayload;
    if (
      (payload.milestones !== undefined && !Array.isArray(payload.milestones)) ||
      (payload.nodes !== undefined && !Array.isArray(payload.nodes)) ||
      (payload.edges !== undefined && !Array.isArray(payload.edges)) ||
      (payload.metricBindings !== undefined && !Array.isArray(payload.metricBindings)) ||
      (payload.outcomeBindings !== undefined && !Array.isArray(payload.outcomeBindings))
    ) {
      throw new Error();
    }
    return payload;
  } catch {
    throw new Error("受控配置文本格式不合法，请检查节点、连线与指标绑定。");
  }
}

function formValuesFromDsl(payload: PathwayDslPayload) {
  const metricByNode = new Map(
    (payload.metricBindings ?? [])
      .filter((binding) => cleanText(binding.nodeCode) && cleanText(binding.metricCode))
      .map((binding) => [binding.nodeCode, binding.metricCode]),
  );

  const edgeValues = (payload.edges ?? []).map<PathwayEdgeFormValue>((edge, index) => {
    const condition = edge.condition;
    const base: PathwayEdgeFormValue = {
      edgeCode: cleanText(edge.edgeCode),
      fromNodeCode: cleanText(edge.fromNodeCode),
      toNodeCode: cleanText(edge.toNodeCode),
      edgeType: edge.edgeType ?? "DEFAULT",
      guardMode: "INLINE",
      priority: Number(edge.priority ?? index + 1),
    };
    if (
      condition &&
      typeof condition === "object" &&
      !Array.isArray(condition) &&
      typeof (condition as Record<string, unknown>).ruleRef === "string"
    ) {
      const conditionRecord = condition as Record<string, unknown>;
      return {
        ...base,
        guardMode: "RULE",
        ruleRef: cleanText(conditionRecord.ruleRef as string),
        ruleAssetId: cleanText(conditionRecord.ruleAssetId as string),
      };
    }
    if (
      condition &&
      typeof condition === "object" &&
      !Array.isArray(condition) &&
      typeof (condition as Record<string, unknown>).fact === "string"
    ) {
      const conditionRecord = condition as Record<string, unknown>;
      const value = conditionRecord.value;
      return {
        ...base,
        conditionTree: dslToRootGroup(condition),
        conditionFact: cleanText(conditionRecord.fact as string),
        conditionOperator:
          typeof conditionRecord.operator === "string"
            ? (conditionRecord.operator as PathwayEdgeFormValue["conditionOperator"])
            : "equals",
        conditionValue: value === undefined || value === null ? undefined : String(value),
        conditionValueKind: inferPathwayConditionValueKind(value),
      };
    }
    return {
      ...base,
      conditionTree:
        condition === undefined ? createDefaultEdgeConditionTree() : dslToRootGroup(condition),
      conditionJson: condition === undefined ? undefined : formatJson(condition),
    };
  });

  const firstNodeCode = cleanText(payload.nodes?.[0]?.nodeCode);
  return {
    startNodeCode: cleanText(payload.startNodeCode) ?? firstNodeCode,
    milestones: (payload.milestones ?? []).map<PathwayMilestoneFormValue>((milestone, index) => ({
      phaseCode: cleanText(milestone.phaseCode),
      phaseName: cleanText(milestone.phaseName),
      milestoneCode: cleanText(milestone.milestoneCode),
      name: cleanText(milestone.name),
      dayOffset:
        typeof milestone.dayOffset === "number" && milestone.dayOffset >= 0
          ? milestone.dayOffset
          : undefined,
      expectedOffsetMinutes:
        typeof milestone.expectedOffsetMinutes === "number" && milestone.expectedOffsetMinutes >= 0
          ? milestone.expectedOffsetMinutes
          : undefined,
      achievementCriteria: normalizeNodeConfig(milestone.achievementCriteria),
      sortOrder: Number(milestone.sortOrder ?? index + 1),
    })),
    outcomeBindings: normalizeOutcomeBindings(payload.outcomeBindings),
    nodes: (payload.nodes ?? []).map<PathwayNodeFormValue>((node, index) => ({
      nodeCode: cleanText(node.nodeCode),
      name: cleanText(node.name),
      nodeType: node.nodeType ?? "ASSESSMENT",
      milestoneCode: cleanText(node.milestoneCode),
      sortOrder: Number(node.sortOrder ?? index + 1),
      responsibleRole: cleanText(node.responsibleRole),
      accountableRole: cleanText(node.accountableRole),
      consultedRoles: normalizeRoleList(node.consultedRoles),
      informedRoles: normalizeRoleList(node.informedRoles),
      timeWindowMinutes:
        typeof node.timeWindowMinutes === "number" && node.timeWindowMinutes > 0
          ? node.timeWindowMinutes
          : undefined,
      terminal: Boolean(node.terminal),
      disabled: Boolean(node.disabled),
      metricCode: metricByNode.get(node.nodeCode),
      config: normalizeNodeConfig(node.config),
    })),
    edges: edgeValues,
  };
}

function criteriaFormValueFromJson(value: string | undefined, label: "入径" | "出径") {
  const parsed = parseLooseJson(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return undefined;
  const record = parsed as Record<string, unknown>;
  const next: PathwayCriteriaFormValue = {};
  try {
    if (record.include) {
      next.includeTree = dslToRootGroup(record.include);
    }
  } catch {
    next.includeTree = createDefaultPathwayCriteriaTree(`${label}纳入条件`);
  }
  try {
    if (record.exclude) {
      next.excludeTree = dslToRootGroup(record.exclude);
    }
  } catch {
    next.excludeTree = createDefaultPathwayCriteriaTree(`${label}排除条件`);
  }
  return next.includeTree || next.excludeTree ? next : undefined;
}

function edgeFormValueFromDetail(edge: PathwayEdge): PathwayEdgeFormValue {
  const base: PathwayEdgeFormValue = {
    edgeCode: edge.edgeCode,
    fromNodeCode: edge.fromNodeCode,
    toNodeCode: edge.toNodeCode,
    edgeType: edge.edgeType,
    guardMode: "INLINE",
    priority: edge.priority,
  };
  const condition = parseLooseJson(edge.conditionJson);
  if (!condition) {
    return base;
  }
  if (
    typeof condition === "object" &&
    !Array.isArray(condition) &&
    typeof (condition as Record<string, unknown>).ruleRef === "string"
  ) {
    const record = condition as Record<string, unknown>;
    return {
      ...base,
      guardMode: "RULE",
      ruleRef: cleanText(record.ruleRef as string),
      ruleAssetId: cleanText(record.ruleAssetId as string),
    };
  }
  try {
    const conditionTree = dslToRootGroup(condition);
    if (typeof condition === "object" && !Array.isArray(condition)) {
      const record = condition as Record<string, unknown>;
      if (typeof record.fact === "string") {
        const value = record.value;
        return {
          ...base,
          conditionTree,
          conditionFact: record.fact,
          conditionOperator:
            typeof record.operator === "string"
              ? (record.operator as PathwayEdgeFormValue["conditionOperator"])
              : "equals",
          conditionValue: value === undefined || value === null ? undefined : String(value),
          conditionValueKind: inferPathwayConditionValueKind(value),
        };
      }
    }
    return {
      ...base,
      conditionTree,
      conditionJson: formatJson(condition),
    };
  } catch {
    return {
      ...base,
      conditionJson: edge.conditionJson,
    };
  }
}

function formValuesFromDetailCopy(detail: PathwayTemplateDetailResponse): PathwayTemplateFormValue {
  const metricByNode = new Map(
    detail.metricBindings
      .filter((binding) => cleanText(binding.nodeCode) && cleanText(binding.metricCode))
      .map((binding) => [binding.nodeCode, binding.metricCode]),
  );
  return {
    templateCode: detail.template.templateCode,
    name: detail.template.name,
    diseaseCode: detail.template.diseaseCode,
    templateLevel: detail.template.templateLevel,
    entryMode: detail.template.entryMode,
    startNodeCode: detail.template.startNodeCode ?? detail.nodes[0]?.nodeCode ?? "",
    sourceRef: detail.template.sourceRef,
    description: detail.template.description,
    entryCriteria: criteriaFormValueFromJson(detail.template.entryCriteriaJson, "入径"),
    exitCriteria: criteriaFormValueFromJson(detail.template.exitCriteriaJson, "出径"),
    milestones: detail.milestones.map<PathwayMilestoneFormValue>((milestone) => ({
      phaseCode: milestone.phaseCode,
      phaseName: milestone.phaseName,
      milestoneCode: milestone.milestoneCode,
      name: milestone.name,
      dayOffset: milestone.dayOffset,
      expectedOffsetMinutes: milestone.expectedOffsetMinutes,
      achievementCriteria: normalizeNodeConfig(parseLooseJson(milestone.achievementCriteriaJson)),
      sortOrder: milestone.sortOrder,
    })),
    nodes: detail.nodes.map<PathwayNodeFormValue>((node) => ({
      nodeCode: node.nodeCode,
      name: node.name,
      nodeType: node.nodeType,
      milestoneCode: node.milestoneCode,
      sortOrder: node.sortOrder,
      responsibleRole: node.responsibleRole,
      accountableRole: node.accountableRole,
      consultedRoles: parseRoleListJson(node.consultedRolesJson),
      informedRoles: parseRoleListJson(node.informedRolesJson),
      timeWindowMinutes: node.timeWindowMinutes,
      terminal: node.terminalFlag,
      disabled: Boolean(node.disabledFlag),
      metricCode: metricByNode.get(node.nodeCode),
      config: normalizeNodeConfig(parseLooseJson(node.configJson)),
    })),
    edges: detail.edges.map(edgeFormValueFromDetail),
    outcomeBindings: normalizeOutcomeBindings(detail.outcomeBindings),
  };
}

function duplicatedCodes(values: Array<string | undefined>) {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const value of values) {
    const code = cleanText(value);
    if (!code) continue;
    if (seen.has(code)) {
      duplicates.add(code);
      continue;
    }
    seen.add(code);
  }
  return [...duplicates];
}

function findPathwayTopologyIssues(
  nodes: PathwayNodeDraft[],
  edges: PathwayEdgeDraft[],
  startNodeCode?: string,
) {
  if (nodes.length === 0) {
    return [];
  }

  const issues: string[] = [];
  for (const code of duplicatedCodes(nodes.map((node) => node.nodeCode))) {
    issues.push(`节点身份 ${code} 重复，请保持唯一。`);
  }
  for (const code of duplicatedCodes(edges.map((edge) => edge.edgeCode))) {
    issues.push(`流转身份 ${code} 重复，请保持唯一。`);
  }
  if (!nodes.some((node) => node.terminal)) {
    issues.push("至少需要一个终止节点。");
  }

  const nodeCodes = new Set(nodes.map((node) => node.nodeCode).filter(Boolean));
  for (const edge of edges) {
    if (!nodeCodes.has(edge.fromNodeCode) || !nodeCodes.has(edge.toNodeCode)) {
      issues.push(`流转 ${edge.edgeCode || "未设置身份"} 引用不存在节点，请从已建节点中选择。`);
    }
  }

  const start = cleanText(startNodeCode) ?? nodes[0]?.nodeCode;
  if (start && nodeCodes.has(start) && edges.length > 0) {
    const reached = new Set<string>([start]);
    let changed = true;
    while (changed) {
      changed = false;
      for (const edge of edges) {
        if (reached.has(edge.fromNodeCode) && !reached.has(edge.toNodeCode)) {
          reached.add(edge.toNodeCode);
          changed = true;
        }
      }
    }
    const unreachable = nodes
      .map((node) => node.nodeCode)
      .filter((code) => code && !reached.has(code));
    if (unreachable.length > 0) {
      issues.push(`存在未从起始节点可达的节点：${unreachable.join("、")}。`);
    }
  }

  return issues;
}

export default function PathwayTemplates() {
  const { message: messageApi } = App.useApp();
  const screens = Grid.useBreakpoint();
  const isWideViewport =
    screens.md ?? (typeof window === "undefined" ? true : window.innerWidth >= 768);
  const detailDescriptionColumn = isWideViewport ? 2 : 1;
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);

  const [statusFilter, setStatusFilter] = useState<PathwayTemplateStatus | undefined>(undefined);
  const [diseaseFilter, setDiseaseFilter] = useState<string>("");
  const [outcomeIndicatorSearch, setOutcomeIndicatorSearch] = useState<string>("");

  const [createTemplateVisible, setCreateTemplateVisible] = useState<boolean>(false);
  const [fieldManagerOpen, setFieldManagerOpen] = useState<boolean>(false);
  const [createAdvancedConfigEnabled, setCreateAdvancedConfigEnabled] = useState<boolean>(false);
  const [detailAdvancedViewEnabled, setDetailAdvancedViewEnabled] = useState<boolean>(false);
  const evidenceDetailsEnabled = detailAdvancedViewEnabled;
  const [selectedPathwayPrototype, setSelectedPathwayPrototype] =
    useState<PathwayPrototypeKey>("blank");
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [detailActiveTab, setDetailActiveTab] = useState<string>("l1");

  const [simulateStartNode, setSimulateStartNode] = useState<string>("");
  const [snapshotPatientId, setSnapshotPatientId] = useState<string>("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState<string>("");
  const [snapshotQuery, setSnapshotQuery] = useState<SnapshotQuery | null>(null);
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string | null>(null);
  const [simulationMode, setSimulationMode] = useState<PathwaySimulationMode>("SINGLE_SNAPSHOT");
  const [replaySnapshotIds, setReplaySnapshotIds] = useState<string[]>([]);
  const [pathwayDslJson, setPathwayDslJson] = useState<string>(() =>
    buildDraftDslPreview([], [], []),
  );
  const [simulationResponse, setSimulationResponse] = useState<PathwaySimulationResponse | null>(
    null,
  );
  const [createPreviewRunResult, setCreatePreviewRunResult] =
    useState<AuthoringPreviewRunResponse | null>(null);

  const {
    data: listData,
    isLoading: listLoading,
    refetch: refetchList,
  } = usePathwayTemplates({
    status: statusFilter,
    diseaseCode: diseaseFilter || undefined,
    page,
    size,
  });

  const { data: detailData, isLoading: detailLoading } = usePathwayTemplateDetail(
    selectedTemplateId || "",
  );

  const outcomeIndicatorKeyword = cleanText(outcomeIndicatorSearch);
  const { data: evaluationIndicatorsData } = useEvaluationIndicators(
    {
      status: "ACTIVE",
      page: 1,
      size: PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE,
      ...(outcomeIndicatorKeyword ? { indicatorCode: outcomeIndicatorKeyword } : {}),
    },
    { enabled: createTemplateVisible || !!selectedTemplateId },
  );

  const { data: snapshotsData, isLoading: snapshotsLoading } = useContextSnapshots(
    snapshotQuery ?? undefined,
    { enabled: !!snapshotQuery },
  );

  const { data: selectedSnapshotDetail, isLoading: selectedSnapshotLoading } =
    useContextSnapshotDetail(selectedSnapshotId || "", { enabled: !!selectedSnapshotId });
  const snapshotList = snapshotsData?.items ?? [];

  const createTemplateMutation = useCreatePathwayTemplate();
  const simulateMutation = useSimulatePathway(selectedTemplateId || "");
  const previewRunMutation = useAuthoringPreviewRun();

  const [templateForm] = Form.useForm<PathwayTemplateFormValue>();
  const watchedMilestones = Form.useWatch("milestones", templateForm);
  const watchedNodes = Form.useWatch("nodes", templateForm);
  const watchedEdges = Form.useWatch("edges", templateForm);
  const watchedOutcomeBindings = Form.useWatch("outcomeBindings", templateForm);
  const watchedStartNodeCode = Form.useWatch("startNodeCode", templateForm);

  const canvasNodes = useMemo(
    () => normalizeNodes(watchedNodes).filter((node) => !node.disabled),
    [watchedNodes],
  );
  const canvasEdges = useMemo(() => normalizeEdgesForCanvas(watchedEdges), [watchedEdges]);
  const topologyIssues = useMemo(
    () => findPathwayTopologyIssues(canvasNodes, canvasEdges, watchedStartNodeCode),
    [canvasEdges, canvasNodes, watchedStartNodeCode],
  );
  const createPathwayDslFromL3 = useMemo(() => {
    try {
      return parsePathwayDslJson(pathwayDslJson);
    } catch {
      return null;
    }
  }, [pathwayDslJson]);

  const handleGraphNodePositionChange = (
    nodeIndex: number,
    _nodeCode: string,
    position: PathwayGraphPosition,
  ) => {
    const nodes = (templateForm.getFieldValue("nodes") as PathwayNodeFormValue[] | undefined) ?? [];
    templateForm.setFieldValue(
      "nodes",
      nodes.map((node, index) =>
        index === nodeIndex
          ? {
              ...node,
              config: writeNodePosition(node.config, position),
            }
          : node,
      ),
    );
  };

  const handleGraphConnect = (sourceNodeCode: string, targetNodeCode: string) => {
    const edges = (templateForm.getFieldValue("edges") as PathwayEdgeFormValue[] | undefined) ?? [];
    const duplicated = edges.some(
      (edge) => edge.fromNodeCode === sourceNodeCode && edge.toNodeCode === targetNodeCode,
    );
    if (duplicated) {
      messageApi.warning("该节点流转已存在");
      return;
    }
    templateForm.setFieldValue("edges", [
      ...edges,
      {
        ...createConnectedEdge(edges, sourceNodeCode, targetNodeCode),
        guardMode: "INLINE",
        conditionTree: createDefaultEdgeConditionTree(),
        conditionOperator: "equals",
        conditionValueKind: "string",
      },
    ]);
  };

  const handleGraphDeleteNode = (nodeIndex: number, nodeCode: string) => {
    const nodes = (templateForm.getFieldValue("nodes") as PathwayNodeFormValue[] | undefined) ?? [];
    const edges = (templateForm.getFieldValue("edges") as PathwayEdgeFormValue[] | undefined) ?? [];
    const next = removeNodeAtIndexWithEdges(nodes, edges, nodeIndex);
    templateForm.setFieldsValue({
      nodes: next.nodes,
      edges: next.edges,
      startNodeCode:
        templateForm.getFieldValue("startNodeCode") === nodeCode
          ? undefined
          : templateForm.getFieldValue("startNodeCode"),
    });
  };

  const handleGraphDeleteEdge = (edgeCode: string) => {
    const edges = (templateForm.getFieldValue("edges") as PathwayEdgeFormValue[] | undefined) ?? [];
    templateForm.setFieldValue(
      "edges",
      edges.filter((edge) => edge.edgeCode !== edgeCode),
    );
  };

  // 已建节点下拉选项：边的源/目标与起始节点从此选择，杜绝手敲断链。
  const nodeSelectOptions = useMemo(
    () =>
      canvasNodes
        .filter((node) => cleanText(node.nodeCode))
        .map((node, index) => ({
          value: node.nodeCode,
          label: `${cleanText(node.name) ?? "未命名节点"} · 节点 ${index + 1}`,
        })),
    [canvasNodes],
  );

  const milestoneSelectOptions = useMemo(
    () =>
      normalizeMilestones(watchedMilestones)
        .filter((milestone) => cleanText(milestone.milestoneCode))
        .map((milestone) => ({
          value: milestone.milestoneCode,
          label: milestoneOptionLabel(milestone),
        })),
    [watchedMilestones],
  );

  const phaseSelectOptions = useMemo(() => {
    const byCode = new Map<string, string>();
    for (const milestone of normalizeMilestones(watchedMilestones)) {
      if (cleanText(milestone.phaseCode)) {
        byCode.set(milestone.phaseCode, milestone.phaseName || milestone.phaseCode);
      }
    }
    return [...byCode.entries()].map(([value, label]) => ({
      value,
      label,
    }));
  }, [watchedMilestones]);

  const outcomeIndicatorOptions = useMemo(
    () =>
      (evaluationIndicatorsData?.items ?? []).map((indicator: EvaluationIndicator) => ({
        value: indicator.indicatorCode,
        label: `${indicator.name} · 第 ${indicator.versionNo} 版`,
      })),
    [evaluationIndicatorsData?.items],
  );
  const detailNodeByCode = useMemo(
    () => new Map((detailData?.nodes ?? []).map((node) => [node.nodeCode, node])),
    [detailData?.nodes],
  );
  const detailNodeIndexByCode = useMemo(
    () => new Map((detailData?.nodes ?? []).map((node, index) => [node.nodeCode, index])),
    [detailData?.nodes],
  );
  const detailMilestoneByCode = useMemo(
    () =>
      new Map(
        (detailData?.milestones ?? []).map((milestone) => [milestone.milestoneCode, milestone]),
      ),
    [detailData?.milestones],
  );
  const outcomeIndicatorByCode = useMemo(
    () =>
      new Map(
        (evaluationIndicatorsData?.items ?? []).map((indicator: EvaluationIndicator) => [
          indicator.indicatorCode,
          indicator.name,
        ]),
      ),
    [evaluationIndicatorsData?.items],
  );
  const detailNodeIdentityText = (nodeCode: string, rowIndex: number) =>
    nodeEvidenceText(nodeCode, rowIndex, evidenceDetailsEnabled);
  const detailEdgeIdentityText = (edgeCode: string, rowIndex: number) =>
    evidenceDetailsEnabled ? edgeCode : `第 ${rowIndex + 1} 条路径流转`;
  const detailNodeReferenceText = (nodeCode?: string | null, fallbackIndex?: number) => {
    if (evidenceDetailsEnabled) return nodeCode || "未返回";
    const node = nodeCode ? detailNodeByCode.get(nodeCode) : undefined;
    const nodeName = cleanText(node?.name);
    if (nodeName) return nodeName;
    const nodeIndex = nodeCode ? detailNodeIndexByCode.get(nodeCode) : undefined;
    if (nodeIndex !== undefined) return nodeEvidenceText(nodeCode ?? "", nodeIndex, false);
    if (fallbackIndex !== undefined) return nodeEvidenceText(nodeCode ?? "", fallbackIndex, false);
    return "路径节点已关联";
  };
  const detailMilestoneReferenceText = (milestoneCode?: string | null) => {
    const normalizedCode = cleanText(milestoneCode);
    if (!normalizedCode) return "未绑定";
    if (evidenceDetailsEnabled) return normalizedCode;
    const milestone = detailMilestoneByCode.get(normalizedCode);
    return milestone?.name ? `里程碑：${milestone.name}` : "里程碑已关联";
  };
  const detailMetricReferenceText = (metricCode?: string | null) => {
    if (evidenceDetailsEnabled) return metricCode || "未返回";
    return metricCode ? "时钟指标已绑定" : "未绑定";
  };
  const detailEdgeConditionText = (condition?: string | null) => {
    const normalized = cleanText(condition);
    if (evidenceDetailsEnabled) return normalized ?? "默认流转";
    return normalized ? "流转条件已配置" : "默认流转";
  };
  const detailOutcomeIndicatorText = (indicatorCode?: string | null) => {
    if (evidenceDetailsEnabled) return indicatorCode || "未返回";
    const indicatorName = indicatorCode ? outcomeIndicatorByCode.get(indicatorCode) : undefined;
    return indicatorName ?? (indicatorCode ? "结局指标已绑定" : "未绑定");
  };
  const detailTrajectoryText = (trajectory: string[] = []) =>
    evidenceDetailsEnabled
      ? trajectory.join(" → ")
      : trajectory.map((nodeCode, index) => detailNodeReferenceText(nodeCode, index)).join(" → ");
  const { data: publishedRulesData, isLoading: publishedRulesLoading } = useRuleDefinitions(
    {
      status: "PUBLISHED",
      ruleType: "PATHWAY",
      page: 1,
      size: PATHWAY_RULE_REFERENCE_PAGE_SIZE,
    },
    { enabled: createTemplateVisible },
  );
  const pathwayRuleOptions = useMemo(
    () =>
      (publishedRulesData?.items ?? []).map((rule: RuleDefinition) => ({
        value: rule.ruleCode,
        label: rule.name,
        ruleAssetId: rule.ruleId,
      })),
    [publishedRulesData?.items],
  );
  const fieldCatalogQuery = useContextFieldCatalog(undefined, {
    enabled: createTemplateVisible || Boolean(selectedTemplateId),
  });
  const fieldCatalogList = fieldCatalogQuery.data ?? [];
  const fieldCatalogOptions = buildFieldCatalogOptions(fieldCatalogList);
  const fieldByPath = new Map(fieldCatalogList.map((field) => [field.fieldPath, field]));
  // 选中字段时按目录 dataType 自动带出边条件值类型（路径仅 string/number/boolean）。
  const pathwayValueKindFor = (dataType?: string) => {
    if (dataType === "number") return "number";
    if (dataType === "boolean") return "boolean";
    return "string";
  };
  const handleEdgeFactSelect = (edgeIndex: number, fieldPath: string) => {
    const descriptor = fieldByPath.get(fieldPath);
    templateForm.setFieldValue(["edges", edgeIndex, "conditionFact"], fieldPath);
    if (descriptor) {
      templateForm.setFieldValue(
        ["edges", edgeIndex, "conditionValueKind"],
        pathwayValueKindFor(descriptor.dataType),
      );
    }
  };
  // 自动生成不重复的顺序身份（节点 N1/N2…，边 E1/E2…），可改但默认不必手填。
  const nextSeqCode = (
    listName: "nodes" | "edges" | "milestones",
    field: "nodeCode" | "edgeCode" | "milestoneCode",
    prefix: string,
  ) => {
    const list =
      (templateForm.getFieldValue(listName) as Array<Record<string, unknown>> | undefined) ?? [];
    const used = new Set(
      list.map((item) => (item && typeof item[field] === "string" ? (item[field] as string) : "")),
    );
    let index = 1;
    while (used.has(`${prefix}${index}`)) index += 1;
    return `${prefix}${index}`;
  };

  const renderPathwayCriteriaEditor = (fieldPath: string[], label: string) => (
    <div className={styles.editorList}>
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Tag color={label.includes("排除") ? "red" : "blue"}>{label}</Tag>
        <Form.Item noStyle shouldUpdate>
          {({ getFieldValue, setFieldValue }) => {
            const tree =
              (getFieldValue(fieldPath) as RuleGroup | undefined) ??
              createDefaultPathwayCriteriaTree(label);
            return (
              <ConditionTreeEditor
                value={tree}
                fieldCatalog={fieldCatalogList}
                fieldCatalogError={fieldCatalogQuery.isError}
                onChange={(next) => setFieldValue(fieldPath, next)}
              />
            );
          }}
        </Form.Item>
      </Space>
    </div>
  );

  const resetCreateTemplateDraft = () => {
    templateForm.resetFields();
    templateForm.setFieldsValue({
      templateLevel: "STANDARD",
      entryMode: "AUTO_SUGGEST",
      nodes: [],
      edges: [],
      milestones: [],
      outcomeBindings: [],
    });
    setPathwayDslJson(buildDraftDslPreview([], [], []));
  };

  const handleCopyTemplateAsNewVersion = () => {
    if (!detailData) return;
    const nextValues = formValuesFromDetailCopy(detailData);
    templateForm.resetFields();
    templateForm.setFieldsValue(nextValues);
    setSelectedPathwayPrototype("blank");
    setPathwayDslJson(
      buildDraftDslPreview(
        nextValues.milestones,
        nextValues.nodes,
        nextValues.edges,
        nextValues.outcomeBindings,
      ),
    );
    resetSimulation();
    setCreateAdvancedConfigEnabled(false);
    setCreateTemplateVisible(true);
    messageApi.success(
      `已复制当前内容，提交时系统将自动创建 v${detailData.nextVersionNo}.0 草稿。`,
    );
  };

  const applyPathwayPrototype = (prototypeKey: PathwayPrototypeKey) => {
    setSelectedPathwayPrototype(prototypeKey);
    if (prototypeKey === "blank") {
      resetCreateTemplateDraft();
      return;
    }

    const milestones: PathwayMilestoneFormValue[] = [
      {
        phaseCode: "ENTRY",
        phaseName: "入径评估",
        milestoneCode: "M-ENTRY-ASSESS",
        name: "完成入径评估",
        dayOffset: 0,
        expectedOffsetMinutes: 30,
        sortOrder: 1,
      },
    ];
    const nodes: PathwayNodeFormValue[] = [
      {
        nodeCode: "ASSESS",
        name: "入径评估",
        nodeType: "ASSESSMENT",
        milestoneCode: "M-ENTRY-ASSESS",
        sortOrder: 1,
        responsibleRole: "责任医生",
        accountableRole: "责任医生",
      },
      {
        nodeCode: "DISPOSITION",
        name: "处置确认",
        nodeType: "MANUAL_GATE",
        milestoneCode: "M-ENTRY-ASSESS",
        sortOrder: 2,
        responsibleRole: "责任医生",
        accountableRole: "责任医生",
        terminal: true,
      },
    ];
    const edges: PathwayEdgeFormValue[] = [
      {
        edgeCode: "E-ASSESS-DISPOSITION",
        fromNodeCode: "ASSESS",
        toNodeCode: "DISPOSITION",
        edgeType: "DEFAULT",
        priority: 1,
      },
    ];

    templateForm.setFieldsValue({
      name: "基础节点闭环",
      templateCode: "PATH.CLINICAL.CYCLE",
      diseaseCode: "GENERAL",
      templateLevel: "STANDARD",
      entryMode: "AUTO_SUGGEST",
      startNodeCode: "ASSESS",
      sourceRef: "院内已审核路径制度",
      description: "完成入径评估后进入处置确认或闭环安排。",
      milestones,
      nodes,
      edges,
      outcomeBindings: [],
    });
    setPathwayDslJson(buildDraftDslPreview(milestones, nodes, edges, []));
  };

  const renderEdgeReadablePreview = (edge: PathwayEdgeFormValue | undefined, edgeIndex: number) => {
    if (!edge) return null;
    let guard: unknown;
    try {
      guard = normalizeEdgeCondition(edge);
    } catch {
      return null;
    }
    if (!guard) return null;
    return (
      <AuthoringReadablePreview
        subject="PATHWAY_GUARD"
        dsl={{
          guard,
          edgeCode: cleanText(edge.edgeCode) ?? `E${edgeIndex + 1}`,
          fromNodeCode: cleanText(edge.fromNodeCode),
          toNodeCode: cleanText(edge.toNodeCode),
        }}
      />
    );
  };

  const resetSimulation = () => {
    setSnapshotPatientId("");
    setSnapshotEncounterId("");
    setSnapshotQuery(null);
    setSelectedSnapshotId(null);
    setReplaySnapshotIds([]);
    setSimulationMode("SINGLE_SNAPSHOT");
    setSimulationResponse(null);
    setCreatePreviewRunResult(null);
  };

  const toggleCreateAdvancedConfigEnabled = (checked: boolean) => {
    setCreateAdvancedConfigEnabled(checked);
  };

  const toggleDetailAdvancedViewEnabled = (checked: boolean) => {
    setDetailAdvancedViewEnabled(checked);
    if (!checked && detailActiveTab === "l3") {
      setDetailActiveTab("l2");
    }
  };

  const handleCreateTemplate = async () => {
    try {
      await templateForm.validateFields();
      const values = templateForm.getFieldsValue(true);
      const milestones = normalizeMilestones(values.milestones);
      const nodes = normalizeNodes(values.nodes);
      const edges = normalizeEdges(values.edges);
      const metricBindings = normalizeMetricBindings(values.nodes);
      const outcomeBindings = normalizeOutcomeBindings(values.outcomeBindings);
      const activeNodes = nodes.filter((node) => !node.disabled);
      const activeNodeCodes = new Set(activeNodes.map((node) => node.nodeCode));
      const activeEdges = edges.filter(
        (edge) => activeNodeCodes.has(edge.fromNodeCode) && activeNodeCodes.has(edge.toNodeCode),
      );
      if (nodes.length === 0) {
        messageApi.error("请至少添加一个生命周期节点");
        return;
      }
      const milestoneCodes = new Set<string>();
      for (const milestone of milestones) {
        if (
          !cleanText(milestone.phaseCode) ||
          !cleanText(milestone.phaseName) ||
          !cleanText(milestone.milestoneCode) ||
          !cleanText(milestone.name)
        ) {
          messageApi.error("阶段里程碑必须填写阶段身份、阶段名称、里程碑身份和名称");
          return;
        }
        if (milestoneCodes.has(milestone.milestoneCode)) {
          messageApi.error(`里程碑身份 ${milestone.milestoneCode} 重复，请保持唯一。`);
          return;
        }
        milestoneCodes.add(milestone.milestoneCode);
      }
      const phaseCodes = new Set(milestones.map((milestone) => milestone.phaseCode));
      const outcomeKeys = new Set<string>();
      for (const binding of outcomeBindings) {
        const key = `${binding.scope}:${binding.refCode ?? "TEMPLATE"}:${binding.indicatorCode}`;
        if (outcomeKeys.has(key)) {
          messageApi.error(`结局指标绑定 ${binding.indicatorCode} 重复，请保留一条。`);
          return;
        }
        outcomeKeys.add(key);
        if (binding.scope === "PHASE" && !phaseCodes.has(binding.refCode ?? "")) {
          messageApi.error(`结局指标绑定引用了不存在的阶段：${binding.refCode ?? ""}`);
          return;
        }
        if (binding.scope === "MILESTONE" && !milestoneCodes.has(binding.refCode ?? "")) {
          messageApi.error(`结局指标绑定引用了不存在的里程碑：${binding.refCode ?? ""}`);
          return;
        }
      }
      const invalidMilestoneNode = nodes.find(
        (node) => !node.disabled && node.milestoneCode && !milestoneCodes.has(node.milestoneCode),
      );
      if (invalidMilestoneNode) {
        messageApi.error(`节点 ${invalidMilestoneNode.nodeCode} 引用了不存在的里程碑`);
        return;
      }
      if (!activeNodeCodes.has(values.startNodeCode)) {
        messageApi.error("起始节点必须来自 L2 节点画布");
        return;
      }
      const timedNodeWithoutMetric = activeNodes.find(
        (node) =>
          (node.timeWindowMinutes ?? 0) > 0 &&
          !metricBindings.some((binding) => binding.nodeCode === node.nodeCode),
      );
      if (timedNodeWithoutMetric) {
        messageApi.error(`节点 ${timedNodeWithoutMetric.nodeCode} 设置时窗后必须绑定时钟指标身份`);
        return;
      }
      const topologyIssuesForSubmit = findPathwayTopologyIssues(
        activeNodes,
        activeEdges,
        values.startNodeCode,
      );
      if (topologyIssuesForSubmit.length > 0) {
        messageApi.error(topologyIssuesForSubmit[0]);
        return;
      }
      const richNodeIssue = validateRichNodeContracts(activeNodes, activeEdges);
      if (richNodeIssue) {
        messageApi.error(richNodeIssue);
        return;
      }
      const entryCriteria = normalizePathwayCriteria(values.entryCriteria, "入径");
      const exitCriteria = normalizePathwayCriteria(values.exitCriteria, "出径");

      await createTemplateMutation.mutateAsync({
        templateCode: values.templateCode,
        name: values.name,
        diseaseCode: values.diseaseCode,
        templateLevel: values.templateLevel,
        entryMode: values.entryMode,
        startNodeCode: values.startNodeCode,
        sourceRef: values.sourceRef,
        description: values.description ?? "",
        entryCriteria,
        exitCriteria,
        milestones,
        nodes,
        edges,
        metricBindings,
        outcomeBindings,
      });

      messageApi.success("专病临床路径草稿创建成功");
      setCreateTemplateVisible(false);
      templateForm.resetFields();
      setSelectedPathwayPrototype("blank");
      setPathwayDslJson(buildDraftDslPreview([], [], []));
      setCreatePreviewRunResult(null);
      refetchList();
    } catch (error: unknown) {
      if (error instanceof Error && error.message.includes("条件")) {
        messageApi.error(error.message);
        return;
      }
      if (applyApiFieldErrors(templateForm, error)) return;
      messageApi.error(getApiErrorMessage(error, "创建临床路径失败"));
    }
  };

  const syncCanvasToDsl = () => {
    if (fieldCatalogQuery.isError) {
      messageApi.error("字段目录暂不可用，路径条件不能同步到受控配置。");
      return;
    }
    try {
      const values = templateForm.getFieldsValue(true);
      setPathwayDslJson(
        formatJson(
          buildDraftDsl(values.milestones, values.nodes, values.edges, values.outcomeBindings),
        ),
      );
      setCreateAdvancedConfigEnabled(true);
      messageApi.success("已从节点画布同步到受控配置文本");
    } catch (error: unknown) {
      messageApi.error(error instanceof Error ? error.message : "L2 节点画布无法生成受控配置");
    }
  };

  const syncDslToCanvas = () => {
    try {
      const formValues = formValuesFromDsl(parsePathwayDslJson(pathwayDslJson));
      templateForm.setFieldsValue(formValues);
      setPathwayDslJson(
        buildDraftDslPreview(
          formValues.milestones,
          formValues.nodes,
          formValues.edges,
          formValues.outcomeBindings,
        ),
      );
      messageApi.success("已将受控配置文本回填到节点画布");
    } catch (error: unknown) {
      messageApi.error(error instanceof Error ? error.message : "受控配置文本回填失败");
    }
  };

  const handleSnapshotSearch = () => {
    const patientId = cleanText(snapshotPatientId);
    const encounterId = cleanText(snapshotEncounterId);
    if (!patientId && !encounterId) {
      messageApi.error("请输入患者信息或就诊信息后读取快照");
      return;
    }
    setSnapshotQuery({
      patientId,
      encounterId,
      status: "ACTIVE",
      page: 1,
      size: 10,
    });
    setSelectedSnapshotId(null);
    setSimulationResponse(null);
    setCreatePreviewRunResult(null);
  };

  const handleRunCreatePreview = async () => {
    if (!selectedSnapshotId) {
      messageApi.error("请先选择一个已生效上下文。");
      return;
    }
    if (
      !selectedSnapshotDetail ||
      selectedSnapshotDetail.status !== "ACTIVE" ||
      !selectedSnapshotDetail.resources
    ) {
      messageApi.error("所选快照详情不可用，不能运行草稿路径。");
      return;
    }

    try {
      const values = templateForm.getFieldsValue(true);
      const draftDsl = buildDraftDsl(
        values.milestones,
        values.nodes,
        values.edges,
        values.outcomeBindings,
      );
      const activeNodes = draftDsl.nodes.filter((node) => !node.disabled);
      const startNodeCode =
        cleanText(values.startNodeCode) ?? cleanText(activeNodes[0]?.nodeCode) ?? "";
      if (!startNodeCode) {
        messageApi.error("请先配置路径起始节点。");
        return;
      }
      const result = await previewRunMutation.mutateAsync({
        subject: "PATHWAY_GUARD",
        snapshotId: selectedSnapshotId,
        startNodeCode,
        dsl: {
          ...draftDsl,
          startNodeCode,
        },
      });
      setCreatePreviewRunResult(result);
      messageApi.success("路径草稿试运行完成，已返回真实快照证据");
    } catch (error: unknown) {
      messageApi.error(getApiErrorMessage(error, "路径草稿试运行失败"));
    }
  };

  const handleSimulate = async () => {
    if (!selectedTemplateId) return;
    let replayIds: string[] = [];
    if (simulationMode === "QUEUE_REPLAY") {
      replayIds = replaySnapshotIds;
    } else if (selectedSnapshotId) {
      replayIds = [selectedSnapshotId];
    }
    if (replayIds.length === 0) {
      messageApi.error(
        simulationMode === "QUEUE_REPLAY"
          ? "请选择至少一个已生效上下文用于队列回放"
          : "请先选择一个已生效上下文",
      );
      return;
    }
    const effectiveStartNode =
      cleanText(simulateStartNode) ??
      cleanText(detailData?.template.startNodeCode) ??
      cleanText(detailData?.nodes[0]?.nodeCode);
    if (!effectiveStartNode) {
      messageApi.error("请先选择路径试运行起点节点");
      return;
    }
    try {
      const result = await simulateMutation.mutateAsync({
        ...(simulationMode === "SINGLE_SNAPSHOT" ? {} : { simulationMode }),
        ...(simulationMode === "QUEUE_REPLAY"
          ? { replaySnapshotIds: replayIds }
          : { snapshotId: replayIds[0] }),
        startNodeCode: effectiveStartNode,
      });
      setSimulationResponse(result);
      messageApi.success("路径轨迹试运行成功");
    } catch (error: unknown) {
      messageApi.error(getApiErrorMessage(error, "路径试运行失败"));
    }
  };

  const columns: TableProps<PathwayTemplate>["columns"] = [
    {
      title: "路径身份",
      dataIndex: "templateCode",
      key: "templateCode",
      render: (text: string) => (
        <Tag color="geekblue">{pathwayIdentityText(text, evidenceDetailsEnabled)}</Tag>
      ),
    },
    {
      title: "路径名称",
      dataIndex: "name",
      key: "name",
      className: styles.textStrong,
    },
    {
      title: "关联病种",
      dataIndex: "diseaseCode",
      key: "diseaseCode",
      render: (text: string) => <Tag color="cyan">{text}</Tag>,
    },
    {
      title: "层级",
      dataIndex: "templateLevel",
      key: "templateLevel",
      render: pathwayTemplateLevelText,
    },
    {
      title: "入径",
      dataIndex: "entryMode",
      key: "entryMode",
      render: pathwayEntryModeText,
    },
    {
      title: "版本",
      dataIndex: "templateVersion",
      key: "templateVersion",
      render: (value: number) => pathwayVersionText(value, evidenceDetailsEnabled),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: pathwayContentStatus,
    },
    {
      title: "管理动作",
      key: "action",
      render: (record: PathwayTemplate) => (
        <Button
          type="link"
          icon={<ApartmentOutlined />}
          onClick={() => {
            setSelectedTemplateId(record.templateId);
            setDetailActiveTab("l1");
            setDetailAdvancedViewEnabled(false);
            setSimulateStartNode(record.startNodeCode ?? "");
            resetSimulation();
          }}
          className={styles.linkButton}
        >
          设计与试运行
        </Button>
      ),
    },
  ];

  const nodeColumns: TableProps<PathwayNode>["columns"] = [
    {
      title: evidenceDetailsEnabled ? "节点身份" : "路径节点",
      dataIndex: "nodeCode",
      render: (code: string, _node, index) => (
        <Tag color="blue">{detailNodeIdentityText(code, index)}</Tag>
      ),
    },
    { title: "名称", dataIndex: "name", className: styles.textStrong },
    {
      title: "节点类型",
      dataIndex: "nodeType",
      render: (type: PathwayNodeType) => <Tag color="purple">{nodeTypeText(type)}</Tag>,
    },
    {
      title: "里程碑",
      dataIndex: "milestoneCode",
      render: (code?: string) => <Tag color="geekblue">{detailMilestoneReferenceText(code)}</Tag>,
    },
    {
      title: "时窗",
      dataIndex: "timeWindowMinutes",
      render: (minutes?: number) => (minutes ? `${minutes} 分钟` : "无"),
    },
    {
      title: "配置引用",
      key: "config",
      render: (_value, node) => richNodeConfigSummary(node, evidenceDetailsEnabled),
    },
    {
      title: "责任分工",
      key: "raci",
      render: (_value, node) =>
        raciSummary({
          responsibleRole: node.responsibleRole,
          accountableRole: node.accountableRole,
          consultedRoles: parseRoleListJson(node.consultedRolesJson),
          informedRoles: parseRoleListJson(node.informedRolesJson),
        }),
    },
    {
      title: "终止",
      dataIndex: "terminalFlag",
      render: (terminal: boolean) => (terminal ? "是" : "否"),
    },
    {
      title: "启用",
      dataIndex: "disabledFlag",
      render: (disabled?: boolean) => (disabled ? "禁用" : "启用"),
    },
  ];

  const milestoneColumns: TableProps<PathwayMilestone>["columns"] = [
    {
      title: "阶段天序",
      key: "phase",
      render: (_value, milestone) =>
        `${milestone.phaseName || milestone.phaseCode} / ${milestoneDayText(milestone.dayOffset)}`,
    },
    {
      title: "里程碑",
      key: "milestone",
      render: (_value, milestone) => `${milestone.name}（${milestone.milestoneCode}）`,
    },
    {
      title: "预期完成",
      dataIndex: "expectedOffsetMinutes",
      render: (minutes?: number) => (typeof minutes === "number" ? `${minutes} 分钟` : "未设置"),
    },
  ];

  const edgeColumns: TableProps<PathwayEdge>["columns"] = [
    {
      title: evidenceDetailsEnabled ? "流转身份" : "路径流转",
      dataIndex: "edgeCode",
      render: (code: string, _edge, index) => detailEdgeIdentityText(code, index),
    },
    {
      title: "源节点",
      dataIndex: "fromNodeCode",
      render: (code: string) => <Tag color="orange">{detailNodeReferenceText(code)}</Tag>,
    },
    {
      title: "目标节点",
      dataIndex: "toNodeCode",
      render: (code: string) => <Tag color="green">{detailNodeReferenceText(code)}</Tag>,
    },
    {
      title: "流转类型",
      dataIndex: "edgeType",
      render: (type: PathwayEdgeType) => <Tag color="cyan">{edgeTypeText(type)}</Tag>,
    },
    {
      title: "流转条件配置",
      dataIndex: "conditionJson",
      render: (condition?: string) => (
        <span className={evidenceDetailsEnabled ? styles.codeText : undefined}>
          {detailEdgeConditionText(condition)}
        </span>
      ),
    },
    { title: "优先级", dataIndex: "priority" },
  ];

  const metricColumns: TableProps<SpecialtyMetricBinding>["columns"] = [
    {
      title: evidenceDetailsEnabled ? "节点身份" : "路径节点",
      dataIndex: "nodeCode",
      render: (code: string) => detailNodeReferenceText(code),
    },
    {
      title: evidenceDetailsEnabled ? "指标身份" : "时钟指标",
      dataIndex: "metricCode",
      render: (code: string) => detailMetricReferenceText(code),
    },
  ];

  const outcomeColumns: TableProps<PathwayOutcomeBinding>["columns"] = [
    {
      title: "作用域",
      dataIndex: "scope",
      render: (scope: PathwayOutcomeScope) => <Tag color="blue">{outcomeScopeText(scope)}</Tag>,
    },
    {
      title: "引用对象",
      key: "refCode",
      render: (_value, binding) => outcomeRefText(binding, evidenceDetailsEnabled),
    },
    {
      title: evidenceDetailsEnabled ? "指标身份" : "结局指标",
      dataIndex: "indicatorCode",
      render: (code: string) => detailOutcomeIndicatorText(code),
    },
  ];

  const renderPreviewRunEvidence = (evidence: AuthoringPreviewRunEvidence[]) => (
    <Table
      dataSource={evidence}
      rowKey={(item) =>
        `${item.fact}-${item.operator}-${item.sourcePath ?? item.formula ?? item.errorCode ?? item.matched}`
      }
      pagination={false}
      size="small"
      columns={[
        {
          title: "字段",
          dataIndex: "fact",
          render: (fact: string) => <span className={styles.codeText}>{fact}</span>,
        },
        { title: "算子", dataIndex: "operator" },
        {
          title: "证据",
          key: "formula",
          render: (_value, item) => item.formula || item.errorMessage || "-",
        },
        {
          title: "结果",
          dataIndex: "matched",
          render: (matched: boolean, item) => {
            if (matched) return <Tag color="green">命中</Tag>;
            if (item.missing) return <Tag color="orange">缺失</Tag>;
            return <Tag>未命中</Tag>;
          },
        },
      ]}
      className="medkernel-table"
    />
  );

  const renderCreatePreviewSnapshot = () => {
    if (!selectedSnapshotId) {
      return <Empty description="请选择一个快照用于路径草稿试运行" />;
    }
    if (selectedSnapshotLoading) {
      return <Alert type="info" showIcon message="正在读取快照详情..." />;
    }
    if (!selectedSnapshotDetail?.resources) {
      return <Alert type="error" showIcon message="所选快照详情不可用，请重新选择。" />;
    }
    return (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="快照证据">
            {evidenceText(selectedSnapshotDetail.snapshotId, false, "试运行快照已关联")}
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            {customerEnumLabel(selectedSnapshotDetail.status)}
          </Descriptions.Item>
          <Descriptions.Item label="质量">
            {customerDisplayText(selectedSnapshotDetail.qualityStatus)}
          </Descriptions.Item>
        </Descriptions>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          aria-label="运行草稿试运行"
          onClick={handleRunCreatePreview}
          loading={previewRunMutation.isPending || selectedSnapshotLoading}
          block
        >
          运行草稿试运行
        </Button>
      </Space>
    );
  };

  const renderCreatePreviewResult = (result: AuthoringPreviewRunResponse | null) => {
    if (!result) {
      return <Empty description="运行草稿路径后展示节点轨迹与边证据" />;
    }
    return (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Space wrap>
          <Tag color={result.matched ? "green" : "default"}>
            {result.matched ? "已流转" : "未流转"}
          </Tag>
          {result.contextQualityStatus && (
            <Tag color={result.contextQualityStatus === "COMPLETE" ? "green" : "orange"}>
              快照质量：{customerDisplayText(result.contextQualityStatus)}
            </Tag>
          )}
          {result.finalStatus && (
            <Tag color="purple">最终状态：{customerEnumLabel(result.finalStatus)}</Tag>
          )}
        </Space>
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="试运行结果">{result.outcomeText}</Descriptions.Item>
          <Descriptions.Item label="快照证据">
            {evidenceText(result.snapshotId, false, "试运行快照已关联")}
          </Descriptions.Item>
          <Descriptions.Item label="机构生效版本">
            {evidenceText(result.runtimeReleaseId, false, "机构生效版本已确认")}
          </Descriptions.Item>
          <Descriptions.Item label="选中路径边">
            {evidenceText(result.selectedEdgeCode, false, "路径边已选择")}
          </Descriptions.Item>
          <Descriptions.Item label="节点轨迹">
            {result.nodeTrajectory?.length ? "节点轨迹已记录" : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="追踪证据">
            {evidenceText(result.traceId, false, "试运行已留痕")}
          </Descriptions.Item>
        </Descriptions>
        {renderPreviewRunEvidence(result.conditionEvidence ?? [])}
      </Space>
    );
  };

  const createLayerItems: TabsProps["items"] = [
    {
      key: "l1",
      label: "基础信息",
      children: (
        <div className={styles.editorSection}>
          <Form.Item label="路径原型">
            <Radio.Group
              value={selectedPathwayPrototype}
              onChange={(event: RadioChangeEvent) =>
                applyPathwayPrototype(event.target.value as PathwayPrototypeKey)
              }
            >
              <Space direction="vertical" className="mk-full-width">
                {pathwayPrototypeOptions.map((prototype) => (
                  <Radio key={prototype.key} value={prototype.key} aria-label={prototype.title}>
                    <Space direction="vertical" size={0}>
                      <span className={styles.textStrong}>{prototype.title}</span>
                      <span className={styles.textSecondary}>{prototype.description}</span>
                    </Space>
                  </Radio>
                ))}
              </Space>
            </Radio.Group>
          </Form.Item>
          <Row gutter={16}>
            <Col xs={24}>
              <Form.Item name="name" label="路径名称" rules={[{ required: true }]}>
                <Input placeholder="如 心血管路径复核" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item
                name="templateCode"
                label="稳定临床路径身份"
                tooltip="用于跨版本、机构生效版本和审计追溯；同身份修改时由系统自动创建下一版本"
                rules={[{ required: true }]}
              >
                <Input placeholder="如 xinxueguan-lujing-fuhe" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item
                name="diseaseCode"
                label="适用病种身份"
                tooltip="填写真实病种、诊断分组或院内路径病种身份，不写临时中文别名"
                rules={[{ required: true }]}
              >
                <Input placeholder="如 xinxueguanbing 或 ICD10-I63" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item name="templateLevel" label="路径层级" rules={[{ required: true }]}>
                <Select options={templateLevelOptions} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} sm={12} lg={6}>
              <Form.Item
                name="entryMode"
                label="入径模式"
                rules={[{ required: true, message: "请选择入径模式" }]}
              >
                <Segmented block options={pathwayEntryModeOptions} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Form.Item
                name="startNodeCode"
                label="起始节点"
                tooltip="从 L2 已建节点选择，必须能连通到终止节点"
                rules={[{ required: true }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="从 L2 已建节点中选择"
                  options={nodeSelectOptions}
                  notFoundContent="请先在 L2 节点画布添加节点"
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="sourceRef" label="临床知识与指南基础" rules={[{ required: true }]}>
            <Input placeholder="如 院内已审核路径制度 2026" />
          </Form.Item>
          <Form.Item name="description" label="收治标准与排除指标">
            <TextArea rows={3} placeholder="如 入径标准、排除条件、出径原则" />
          </Form.Item>
        </div>
      ),
    },
    {
      key: "l2",
      label: "节点画布",
      children: (
        <div className={styles.editorSection}>
          <Space
            direction="vertical"
            size="middle"
            className={`mk-full-width ${styles.marginBottomMd}`}
          >
            <div className={styles.graphToolbar}>
              <div className={`${styles.textStrong} ${styles.graphToolbarTitle}`}>
                结构化节点画布
              </div>
              <div className={styles.graphToolbarActions}>
                <Button
                  icon={<SwapOutlined />}
                  disabled={fieldCatalogQuery.isError}
                  onClick={syncCanvasToDsl}
                >
                  同步到受控配置
                </Button>
                <Button
                  icon={<ApartmentOutlined />}
                  aria-label="管理字段目录"
                  onClick={() => setFieldManagerOpen(true)}
                >
                  管理字段目录
                </Button>
              </div>
            </div>
            {fieldCatalogQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="字段目录暂不可用，路径条件不能同步到受控配置。"
                description="路径纳入、排除和流转条件必须绑定标准字段目录；恢复字段目录服务后再同步或保存。"
              />
            ) : null}
            <Row gutter={[16, 16]} className="mk-full-width">
              <Col xs={24} lg={12}>
                {renderPathwayCriteriaEditor(["entryCriteria", "includeTree"], "入径纳入条件")}
              </Col>
              <Col xs={24} lg={12}>
                {renderPathwayCriteriaEditor(["entryCriteria", "excludeTree"], "入径排除条件")}
              </Col>
              <Col xs={24} lg={12}>
                {renderPathwayCriteriaEditor(["exitCriteria", "includeTree"], "出径纳入条件")}
              </Col>
              <Col xs={24} lg={12}>
                {renderPathwayCriteriaEditor(["exitCriteria", "excludeTree"], "出径排除条件")}
              </Col>
            </Row>
            {canvasNodes.length === 0 ? (
              <div className={styles.canvasEmpty}>
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="添加节点后形成路径画布" />
              </div>
            ) : (
              <PathwayGraphEditor
                nodes={canvasNodes}
                edges={canvasEdges}
                editable
                onNodePositionChange={handleGraphNodePositionChange}
                onConnectNodes={handleGraphConnect}
                onDeleteNode={handleGraphDeleteNode}
                onDeleteEdge={handleGraphDeleteEdge}
              />
            )}
            {topologyIssues.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message="路径拓扑待完善"
                description={
                  <ul className={styles.issueList}>
                    {topologyIssues.map((issue) => (
                      <li key={issue}>{issue}</li>
                    ))}
                  </ul>
                }
              />
            )}
          </Space>

          <Form.List name="milestones">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="middle" className="mk-full-width">
                <Space align="center" className="mk-flex-between mk-full-width">
                  <div className={styles.textStrong}>阶段与天序里程碑</div>
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() =>
                      add({
                        phaseCode: "PHASE",
                        phaseName: "阶段",
                        milestoneCode: nextSeqCode("milestones", "milestoneCode", "M"),
                        sortOrder: fields.length + 1,
                      })
                    }
                  >
                    添加里程碑
                  </Button>
                </Space>
                {fields.length === 0 && <Empty description="尚未添加阶段里程碑" />}
                {fields.map((field) => {
                  const { key, ...fieldProps } = field;
                  return (
                    <div key={key} className={styles.editorList}>
                      <Space align="start" className="mk-flex-between mk-full-width">
                        <Tag color="geekblue">里程碑 {field.name + 1}</Tag>
                        <Button
                          aria-label={`删除里程碑 ${field.name + 1}`}
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                      <Row gutter={12} className={styles.marginTopMd}>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "phaseCode"]}
                            label="阶段身份"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 shuqian" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "phaseName"]}
                            label="阶段名称"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 术前" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "milestoneCode"]}
                            label="里程碑身份"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 shuqian-rujing-pinggu" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "name"]}
                            label="里程碑名称"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 入径评估" />
                          </Form.Item>
                        </Col>
                      </Row>
                      <Row gutter={12}>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item {...fieldProps} name={[field.name, "dayOffset"]} label="天序">
                            <InputNumber
                              min={0}
                              precision={0}
                              placeholder="如 0"
                              className={styles.fullWidth}
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "expectedOffsetMinutes"]}
                            label="预期分钟"
                          >
                            <InputNumber
                              min={0}
                              precision={0}
                              placeholder="如 60"
                              className={styles.fullWidth}
                            />
                          </Form.Item>
                        </Col>
                      </Row>
                    </div>
                  );
                })}
              </Space>
            )}
          </Form.List>

          <Divider />

          <Form.List name="outcomeBindings">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="middle" className="mk-full-width">
                <Space align="center" className="mk-flex-between mk-full-width">
                  <div className={styles.textStrong}>结局指标绑定</div>
                  <Button icon={<PlusOutlined />} onClick={() => add({ scope: "TEMPLATE" })}>
                    添加结局指标
                  </Button>
                </Space>
                {fields.length === 0 && (
                  <Empty description="尚未绑定 LOS、再入院、并发症或成本指标" />
                )}
                {fields.map((field) => {
                  const { key, ...fieldProps } = field;
                  const currentScope = watchedOutcomeBindings?.[field.name]?.scope ?? "TEMPLATE";
                  const refOptions =
                    currentScope === "PHASE" ? phaseSelectOptions : milestoneSelectOptions;
                  return (
                    <div key={key} className={styles.editorList}>
                      <Space align="start" className="mk-flex-between mk-full-width">
                        <Tag color="cyan">指标 {field.name + 1}</Tag>
                        <Button
                          aria-label={`删除结局指标 ${field.name + 1}`}
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                      <Row gutter={12} className={styles.marginTopMd}>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "scope"]}
                            label="作用域"
                            rules={[{ required: true }]}
                          >
                            <Select options={outcomeScopeOptions} />
                          </Form.Item>
                        </Col>
                        {currentScope !== "TEMPLATE" && (
                          <Col xs={24} sm={12} lg={8}>
                            <Form.Item
                              {...fieldProps}
                              name={[field.name, "refCode"]}
                              label="引用对象"
                              rules={[{ required: true, message: "请选择引用对象" }]}
                            >
                              <Select
                                showSearch
                                optionFilterProp="label"
                                placeholder={currentScope === "PHASE" ? "选择阶段" : "选择里程碑"}
                                options={refOptions}
                              />
                            </Form.Item>
                          </Col>
                        )}
                        <Col xs={24} sm={12} lg={currentScope === "TEMPLATE" ? 18 : 10}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "indicatorCode"]}
                            label="评价指标"
                            rules={[{ required: true }]}
                          >
                            <Select
                              showSearch
                              allowClear
                              filterOption={false}
                              placeholder="选择已生效评价指标"
                              options={outcomeIndicatorOptions}
                              onSearch={setOutcomeIndicatorSearch}
                              onClear={() => setOutcomeIndicatorSearch("")}
                              notFoundContent="暂无已生效评价指标"
                            />
                          </Form.Item>
                        </Col>
                      </Row>
                    </div>
                  );
                })}
              </Space>
            )}
          </Form.List>

          <Divider />

          <Form.List name="nodes">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="middle" className="mk-full-width">
                {fields.length === 0 && <Empty description="尚未添加路径节点" />}
                {fields.map((field) => {
                  const { key, ...fieldProps } = field;
                  const currentNodeType = watchedNodes?.[field.name]?.nodeType;
                  const currentTimeWindow = Number(
                    watchedNodes?.[field.name]?.timeWindowMinutes ?? 0,
                  );
                  return (
                    <div key={key} className={styles.editorList}>
                      <Space align="start" className="mk-flex-between mk-full-width">
                        <Tag color="blue">节点 {field.name + 1}</Tag>
                        <Button
                          aria-label={`删除节点 ${field.name + 1}`}
                          icon={<DeleteOutlined />}
                          onClick={() => {
                            const nodeCode = watchedNodes?.[field.name]?.nodeCode;
                            if (nodeCode) {
                              handleGraphDeleteNode(field.name, nodeCode);
                              return;
                            }
                            remove(field.name);
                          }}
                        />
                      </Space>
                      <Row gutter={12} className={styles.marginTopMd}>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "nodeCode"]}
                            label="节点身份"
                            tooltip="新增时自动生成（N1/N2…），可改；用于流转连接与起点引用"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 N1，可改为 rujing-pinggu" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "name"]}
                            label="节点名称"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 入径评估" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "nodeType"]}
                            label="节点类型"
                            rules={[{ required: true }]}
                          >
                            <Select
                              placeholder="选择节点类型"
                              options={nodeTypeOptions}
                              optionFilterProp="label"
                              virtual={false}
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "sortOrder"]}
                            label="顺序"
                            rules={[{ required: true }]}
                          >
                            <InputNumber
                              min={1}
                              precision={0}
                              placeholder="如 1"
                              className={styles.fullWidth}
                            />
                          </Form.Item>
                        </Col>
                      </Row>
                      <Row gutter={12}>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "responsibleRole"]}
                            label="责任角色"
                            rules={[{ required: true, message: "请填写责任角色" }]}
                          >
                            <Input placeholder="如 责任医生 / 责任护士" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "accountableRole"]}
                            label="签责角色"
                          >
                            <Input placeholder="默认同责任角色" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "milestoneCode"]}
                            label="所属里程碑"
                          >
                            <Select
                              allowClear
                              showSearch
                              optionFilterProp="label"
                              placeholder="选择阶段里程碑"
                              options={milestoneSelectOptions}
                              notFoundContent="请先添加里程碑"
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "timeWindowMinutes"]}
                            label="时窗分钟"
                            tooltip="节点必须在该分钟数内完成；留空表示不启用节点时钟"
                          >
                            <InputNumber
                              min={0}
                              precision={0}
                              placeholder="如 60"
                              className={styles.fullWidth}
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "consultedRoles"]}
                            label="会诊角色"
                          >
                            <Select
                              mode="tags"
                              tokenSeparators={[",", "，", "、"]}
                              placeholder="输入后回车"
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "informedRoles"]}
                            label="知会角色"
                          >
                            <Select
                              mode="tags"
                              tokenSeparators={[",", "，", "、"]}
                              placeholder="输入后回车"
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={8}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "metricCode"]}
                            label="时钟指标身份"
                            tooltip="设置时窗分钟后必填，用于时窗校验与质控时钟"
                            rules={[
                              ({ getFieldValue }) => ({
                                validator(_rule, value) {
                                  const minutes = getFieldValue([
                                    "nodes",
                                    field.name,
                                    "timeWindowMinutes",
                                  ]);
                                  if (Number(minutes) > 0 && !cleanText(value)) {
                                    return Promise.reject(
                                      new Error("已设置时窗，请填写时钟指标身份"),
                                    );
                                  }
                                  return Promise.resolve();
                                },
                              }),
                            ]}
                          >
                            <Input placeholder="如 rujing-pinggu-shichuang" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={4}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "terminal"]}
                            label="终止节点"
                            valuePropName="checked"
                          >
                            <Switch />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={4}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "disabled"]}
                            label="禁用继承节点"
                            valuePropName="checked"
                          >
                            <Switch />
                          </Form.Item>
                        </Col>
                      </Row>
                      {currentTimeWindow > 0 && (
                        <Row gutter={12}>
                          <Col xs={24} sm={12} lg={6}>
                            <Form.Item
                              {...fieldProps}
                              name={[field.name, "config", "clockSla", "baselineEvent"]}
                              label="时窗校验基准"
                            >
                              <Select
                                allowClear
                                placeholder="默认节点开始"
                                options={clockBaselineEventOptions}
                                virtual={false}
                              />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={12} lg={4}>
                            <Form.Item
                              {...fieldProps}
                              name={[field.name, "config", "clockSla", "minMinutes"]}
                              label="最早分钟"
                            >
                              <InputNumber
                                min={0}
                                precision={0}
                                placeholder="默认 0"
                                className={styles.fullWidth}
                              />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={12} lg={4}>
                            <Form.Item
                              {...fieldProps}
                              name={[field.name, "config", "clockSla", "targetMinutes"]}
                              label="目标分钟"
                            >
                              <InputNumber
                                min={1}
                                precision={0}
                                placeholder={`默认 ${currentTimeWindow}`}
                                className={styles.fullWidth}
                              />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={12} lg={4}>
                            <Form.Item
                              {...fieldProps}
                              name={[field.name, "config", "clockSla", "maxMinutes"]}
                              label="最晚分钟"
                            >
                              <InputNumber
                                min={1}
                                precision={0}
                                placeholder={`默认 ${currentTimeWindow}`}
                                className={styles.fullWidth}
                              />
                            </Form.Item>
                          </Col>
                          <Col xs={24} sm={12} lg={6}>
                            <Form.Item
                              {...fieldProps}
                              name={[field.name, "config", "clockSla", "reportMinutes"]}
                              label="上报分钟"
                            >
                              <InputNumber
                                min={1}
                                precision={0}
                                placeholder="默认取目标与最晚中点"
                                className={styles.fullWidth}
                              />
                            </Form.Item>
                          </Col>
                        </Row>
                      )}
                      {["ORDER_SET", "WAIT_TIMER"].includes(currentNodeType ?? "") && (
                        <Row gutter={12}>
                          {currentNodeType === "ORDER_SET" && (
                            <Col xs={24} sm={12} lg={8}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "config", "orderSetRef"]}
                                label="医嘱套餐引用"
                                rules={[{ required: true, message: "请填写医嘱套餐引用" }]}
                              >
                                <Input placeholder="如 ganranxing-xiuke-yizhu-taocan" />
                              </Form.Item>
                            </Col>
                          )}
                          {currentNodeType === "WAIT_TIMER" && (
                            <Col xs={24} sm={12} lg={8}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "config", "clock"]}
                                label="计时规则"
                              >
                                <Input placeholder="如 24 小时后提醒" />
                              </Form.Item>
                            </Col>
                          )}
                        </Row>
                      )}
                    </div>
                  );
                })}
                <Button
                  icon={<PlusOutlined />}
                  onClick={() =>
                    add({
                      nodeCode: nextSeqCode("nodes", "nodeCode", "N"),
                      nodeType: "ASSESSMENT",
                      sortOrder: fields.length + 1,
                      responsibleRole: "责任医生",
                      accountableRole: "责任医生",
                      consultedRoles: [],
                      informedRoles: [],
                      terminal: false,
                      disabled: false,
                    })
                  }
                >
                  添加节点
                </Button>
              </Space>
            )}
          </Form.List>

          <Divider />

          <Form.List name="edges">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="middle" className="mk-full-width">
                {fields.length === 0 && <Empty description="尚未添加流转边" />}
                {fields.map((field) => {
                  const { key, ...fieldProps } = field;
                  const edgeValue = watchedEdges?.[field.name];
                  const guardMode = edgeValue?.guardMode ?? "INLINE";
                  return (
                    <div key={key} className={styles.editorList}>
                      <Space align="start" className="mk-flex-between mk-full-width">
                        <Tag color="green">流转边 {field.name + 1}</Tag>
                        <Button
                          aria-label={`删除流转边 ${field.name + 1}`}
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                      <Row gutter={12} className={styles.marginTopMd}>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "edgeCode"]}
                            label="流转身份"
                            tooltip="新增时自动生成（E1/E2…），可改；用于路径流转追溯"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 E1，可改为 rujing-daosuifang" />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "fromNodeCode"]}
                            label="源节点"
                            rules={[{ required: true }]}
                          >
                            <Select
                              showSearch
                              optionFilterProp="label"
                              placeholder="选择源节点"
                              options={nodeSelectOptions}
                              notFoundContent="请先添加节点"
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "toNodeCode"]}
                            label="目标节点"
                            rules={[{ required: true }]}
                          >
                            <Select
                              showSearch
                              optionFilterProp="label"
                              placeholder="选择目标节点"
                              options={nodeSelectOptions}
                              notFoundContent="请先添加节点"
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "edgeType"]}
                            label="流转类型"
                            rules={[{ required: true }]}
                          >
                            <Select
                              placeholder="选择流转类型"
                              options={edgeTypeOptions}
                              optionFilterProp="label"
                              virtual={false}
                            />
                          </Form.Item>
                        </Col>
                      </Row>
                      <Row gutter={12}>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "priority"]}
                            label="优先级"
                            rules={[{ required: true }]}
                          >
                            <InputNumber
                              min={1}
                              precision={0}
                              placeholder="如 10"
                              className={styles.fullWidth}
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "guardMode"]}
                            label="守卫来源"
                            rules={[{ required: true }]}
                          >
                            <Select
                              options={[
                                { value: "INLINE", label: "内嵌条件树" },
                                { value: "RULE", label: "引用已发布规则" },
                              ]}
                              onChange={(value: "INLINE" | "RULE") => {
                                templateForm.setFieldValue(
                                  ["edges", field.name, "guardMode"],
                                  value,
                                );
                                if (value === "RULE") {
                                  templateForm.setFieldValue(
                                    ["edges", field.name, "conditionTree"],
                                    undefined,
                                  );
                                  templateForm.setFieldValue(
                                    ["edges", field.name, "conditionFact"],
                                    undefined,
                                  );
                                } else {
                                  templateForm.setFieldValue(
                                    ["edges", field.name, "ruleRef"],
                                    undefined,
                                  );
                                  templateForm.setFieldValue(
                                    ["edges", field.name, "ruleAssetId"],
                                    undefined,
                                  );
                                  templateForm.setFieldValue(
                                    ["edges", field.name, "conditionTree"],
                                    createDefaultEdgeConditionTree(),
                                  );
                                }
                              }}
                            />
                          </Form.Item>
                        </Col>
                        {guardMode === "RULE" && (
                          <Col xs={24} lg={12}>
                            <Form.Item
                              {...fieldProps}
                              name={[field.name, "ruleRef"]}
                              label="已发布规则"
                              rules={[{ required: true, message: "请选择已发布规则" }]}
                            >
                              <Select
                                showSearch
                                optionFilterProp="label"
                                loading={publishedRulesLoading}
                                placeholder="选择已发布规则"
                                options={pathwayRuleOptions}
                                notFoundContent="暂无可引用的已发布路径规则"
                                onChange={(ruleCode) => {
                                  const selectedRule = pathwayRuleOptions.find(
                                    (option) => option.value === ruleCode,
                                  );
                                  templateForm.setFieldValue(
                                    ["edges", field.name, "ruleAssetId"],
                                    selectedRule?.ruleAssetId || undefined,
                                  );
                                }}
                              />
                            </Form.Item>
                            <Form.Item {...fieldProps} name={[field.name, "ruleAssetId"]} hidden>
                              <Input />
                            </Form.Item>
                          </Col>
                        )}
                      </Row>
                      {guardMode === "RULE" ? (
                        <Alert
                          type="info"
                          showIcon
                          message="路径仅读取规则命中结果；运行时由同一机构生效版本确认规则版本，规则不能反向调用路径。"
                        />
                      ) : (
                        <>
                          <Row gutter={12}>
                            <Col xs={24} sm={12} lg={7}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "conditionFact"]}
                                label="条件字段路径"
                              >
                                <AutoComplete
                                  options={fieldCatalogOptions}
                                  onSelect={(value) => handleEdgeFactSelect(field.name, value)}
                                  filterOption={(input, option) => {
                                    const leaf = option as
                                      | { value?: string; label?: string }
                                      | undefined;
                                    return `${leaf?.value ?? ""} ${leaf?.label ?? ""}`
                                      .toLowerCase()
                                      .includes(input.toLowerCase());
                                  }}
                                >
                                  <Input placeholder="如 observations[].valueNumeric" />
                                </AutoComplete>
                              </Form.Item>
                            </Col>
                            <Col xs={24} sm={12} lg={6}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "conditionOperator"]}
                                label="条件算子"
                              >
                                <Select
                                  placeholder="选择算子"
                                  options={pathwayConditionOperatorOptions}
                                />
                              </Form.Item>
                            </Col>
                            <Col xs={24} sm={12} lg={5}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "conditionValueKind"]}
                                label="值类型"
                              >
                                <Select
                                  placeholder="选择值类型"
                                  options={pathwayConditionValueKindOptions}
                                />
                              </Form.Item>
                            </Col>
                            <Col xs={24} sm={12} lg={6}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "conditionValue"]}
                                label="条件值"
                              >
                                {(() => {
                                  const edgeFact = watchedEdges?.[field.name]?.conditionFact;
                                  const edgeCodeSystem = edgeFact
                                    ? fieldByPath.get(edgeFact)?.codeSystem
                                    : undefined;
                                  return edgeCodeSystem ? (
                                    <StandardTermValueAutoComplete codeSystem={edgeCodeSystem} />
                                  ) : (
                                    <Input placeholder="如 true / 90 / ATC-J01C" />
                                  );
                                })()}
                              </Form.Item>
                            </Col>
                          </Row>
                          <Card size="small" className={styles.marginTopMd} title="条件树构建器">
                            <Form.Item noStyle shouldUpdate>
                              {({ getFieldValue, setFieldValue }) => {
                                const tree =
                                  (getFieldValue(["edges", field.name, "conditionTree"]) as
                                    | RuleGroup
                                    | undefined) ?? createDefaultEdgeConditionTree();
                                return (
                                  <ConditionTreeEditor
                                    value={tree}
                                    fieldCatalog={fieldCatalogList}
                                    fieldCatalogError={fieldCatalogQuery.isError}
                                    onChange={(next) =>
                                      setFieldValue(["edges", field.name, "conditionTree"], next)
                                    }
                                  />
                                );
                              }}
                            </Form.Item>
                          </Card>
                        </>
                      )}
                      {renderEdgeReadablePreview(edgeValue, field.name)}
                    </div>
                  );
                })}
                <Button
                  icon={<PlusOutlined />}
                  onClick={() =>
                    add({
                      edgeCode: nextSeqCode("edges", "edgeCode", "E"),
                      edgeType: "DEFAULT",
                      guardMode: "INLINE",
                      conditionTree: createDefaultEdgeConditionTree(),
                      conditionOperator: "equals",
                      conditionValueKind: "string",
                      priority: fields.length + 1,
                    })
                  }
                >
                  添加流转边
                </Button>
              </Space>
            )}
          </Form.List>
        </div>
      ),
    },
    {
      key: "preview",
      label: (
        <span>
          <PlayCircleOutlined /> 即配即试
        </span>
      ),
      children: (
        <Row gutter={16} className={styles.marginTopMd}>
          <Col xs={24} lg={9}>
            <Space direction="vertical" size="middle" className="mk-full-width">
              <div className={styles.formSection}>
                <Form.Item label="患者信息" htmlFor="pathway-create-snapshot-patient-id">
                  <Input
                    id="pathway-create-snapshot-patient-id"
                    value={snapshotPatientId}
                    onChange={(event) => setSnapshotPatientId(event.target.value)}
                    placeholder="输入患者信息检索快照"
                  />
                </Form.Item>
                <Form.Item label="就诊信息" htmlFor="pathway-create-snapshot-encounter-id">
                  <Input
                    id="pathway-create-snapshot-encounter-id"
                    value={snapshotEncounterId}
                    onChange={(event) => setSnapshotEncounterId(event.target.value)}
                    placeholder="输入就诊信息检索快照"
                  />
                </Form.Item>
                <Button
                  icon={<SearchOutlined />}
                  aria-label="读取真实快照"
                  onClick={handleSnapshotSearch}
                  loading={snapshotsLoading}
                  className="mk-full-width"
                >
                  读取真实快照
                </Button>
              </div>
              <div className={styles.snapshotList}>
                {snapshotList.length > 0 ? (
                  <Space direction="vertical" className="mk-full-width">
                    {snapshotList.map((snapshot: ContextSnapshotSummary, index) => (
                      <Button
                        key={snapshot.snapshotId}
                        type={selectedSnapshotId === snapshot.snapshotId ? "primary" : "default"}
                        onClick={() => {
                          setSelectedSnapshotId(snapshot.snapshotId);
                          setCreatePreviewRunResult(null);
                        }}
                        className={styles.snapshotButton}
                      >
                        <span>{snapshotBusinessLabel(index)}</span>
                        <Tag className={styles.tagGap}>
                          {customerDisplayText(snapshot.qualityStatus)}
                        </Tag>
                      </Button>
                    ))}
                  </Space>
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      snapshotQuery ? "未读取到已生效快照" : "请输入患者信息或就诊信息读取真实快照"
                    }
                  />
                )}
              </div>
            </Space>
          </Col>
          <Col xs={24} lg={15}>
            <Space direction="vertical" size="middle" className="mk-full-width">
              {renderCreatePreviewSnapshot()}
              {renderCreatePreviewResult(createPreviewRunResult)}
            </Space>
          </Col>
        </Row>
      ),
    },
    ...(createAdvancedConfigEnabled
      ? [
          {
            key: "l3",
            label: "受控配置文本",
            children: (
              <div className={styles.editorSection}>
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <Space className="mk-flex-between mk-full-width">
                    <div className={styles.textStrong}>路径配置文本</div>
                    <Space>
                      <Button icon={<SwapOutlined />} onClick={syncCanvasToDsl}>
                        重新从 L2 生成
                      </Button>
                      <Button type="primary" icon={<SwapOutlined />} onClick={syncDslToCanvas}>
                        回填到 L2
                      </Button>
                    </Space>
                  </Space>
                  <Alert
                    type="warning"
                    showIcon
                    message="受控配置文本用于承载精确执行结构，普通路径配置请优先使用节点画布。"
                  />
                  <Form.Item
                    label="路径配置文本"
                    htmlFor="pathway-dsl-json"
                    className={styles.zeroBottom}
                  >
                    <TextArea
                      id="pathway-dsl-json"
                      value={pathwayDslJson}
                      rows={18}
                      onChange={(event) => setPathwayDslJson(event.target.value)}
                      className={styles.codeText}
                    />
                  </Form.Item>
                  {createPathwayDslFromL3?.edges
                    ?.filter(
                      (edge) =>
                        edge.condition &&
                        typeof edge.condition === "object" &&
                        !Array.isArray(edge.condition),
                    )
                    .map((edge, index) => (
                      <AuthoringReadablePreview
                        key={`create-l3-edge-preview-${edge.edgeCode || index}`}
                        subject="PATHWAY_GUARD"
                        dsl={{
                          guard: edge.condition,
                          edgeCode: edge.edgeCode || `E${index + 1}`,
                          fromNodeCode: edge.fromNodeCode,
                          toNodeCode: edge.toNodeCode,
                        }}
                      />
                    ))}
                </Space>
              </div>
            ),
          },
        ]
      : []),
  ];

  const detailExecutableNodes = (detailData?.nodes ?? []).filter((node) => !node.disabledFlag);
  const detailExecutableNodeCodes = new Set(detailExecutableNodes.map((node) => node.nodeCode));
  const detailExecutableEdges = (detailData?.edges ?? []).filter(
    (edge) =>
      detailExecutableNodeCodes.has(edge.fromNodeCode) &&
      detailExecutableNodeCodes.has(edge.toNodeCode),
  );
  const selectedStartNode =
    cleanText(simulateStartNode) ??
    cleanText(detailData?.template.startNodeCode) ??
    cleanText(detailExecutableNodes[0]?.nodeCode) ??
    "";

  const simulationQuality =
    simulationResponse?.contextQualityStatus ?? selectedSnapshotDetail?.qualityStatus ?? "UNKNOWN";
  const simulationMapping = mappingEntries(
    simulationResponse?.mappingStatus ?? selectedSnapshotDetail?.mappingStatus,
  );
  const activeInRuntime = detailData?.deploymentStatus === "PUBLISHED";
  const immutableVersion = detailData?.template.status !== "DRAFT";
  let detailAlertMessage = "当前路径处于草稿状态，可继续完善三层模型并使用真实上下文快照试运行。";
  let detailAlertType: "success" | "warning" | "info" = "info";
  if (activeInRuntime) {
    detailAlertMessage =
      "当前路径版本已纳入机构生效版本；内容不可原地修改，调整请复制为下一版本草稿。";
    detailAlertType = "success";
  } else if (immutableVersion) {
    detailAlertMessage =
      "当前内容版本已发布但未必正在机构生效；启停、升级和回滚统一在机构生效版本页面完成。";
    detailAlertType = "warning";
  }

  const detailLayerItems: TabsProps["items"] = detailData
    ? [
        {
          key: "l1",
          label: "基础信息",
          children: (
            <Descriptions bordered column={detailDescriptionColumn} className={styles.marginTopMd}>
              <Descriptions.Item label="名称">{detailData.template.name}</Descriptions.Item>
              <Descriptions.Item label="路径身份">
                {pathwayIdentityText(detailData.template.templateCode, evidenceDetailsEnabled)}
              </Descriptions.Item>
              <Descriptions.Item label="相关病种">
                {detailData.template.diseaseCode}
              </Descriptions.Item>
              <Descriptions.Item label="版本">
                {pathwayVersionText(detailData.template.templateVersion, evidenceDetailsEnabled)}
              </Descriptions.Item>
              <Descriptions.Item label="层级">
                {pathwayTemplateLevelText(detailData.template.templateLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {pathwayContentStatus(detailData.template.status)}
              </Descriptions.Item>
              <Descriptions.Item label="部署状态">
                {pathwayDeploymentStatus(detailData.deploymentStatus)}
              </Descriptions.Item>
              <Descriptions.Item label="入径模式">
                {pathwayEntryModeText(detailData.template.entryMode)}
              </Descriptions.Item>
              <Descriptions.Item label="起始节点">
                {detailData.template.startNodeCode
                  ? evidenceText(
                      detailData.template.startNodeCode,
                      evidenceDetailsEnabled,
                      "起始节点已配置",
                    )
                  : "未设置"}
              </Descriptions.Item>
              <Descriptions.Item label="入径条件" span={detailDescriptionColumn}>
                <span className={styles.codeText}>
                  {cleanText(detailData.template.entryCriteriaJson)
                    ? evidenceText(
                        detailData.template.entryCriteriaJson,
                        evidenceDetailsEnabled,
                        "入径条件已配置",
                      )
                    : "未配置"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="出径条件" span={detailDescriptionColumn}>
                <span className={styles.codeText}>
                  {cleanText(detailData.template.exitCriteriaJson)
                    ? evidenceText(
                        detailData.template.exitCriteriaJson,
                        evidenceDetailsEnabled,
                        "出径条件已配置",
                      )
                    : "未配置"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="知识来源" span={detailDescriptionColumn}>
                {detailData.template.sourceRef}
              </Descriptions.Item>
              <Descriptions.Item label="说明" span={detailDescriptionColumn}>
                {detailData.template.description || "未填写"}
              </Descriptions.Item>
            </Descriptions>
          ),
        },
        {
          key: "l2",
          label: "节点画布",
          children: (
            <Space
              direction="vertical"
              size="large"
              className={`mk-full-width ${styles.marginTopMd}`}
            >
              <PathwayGraphEditor
                nodes={detailExecutableNodes.map((node) => ({
                  nodeCode: node.nodeCode,
                  name: node.name,
                  nodeType: node.nodeType,
                  milestoneCode: node.milestoneCode,
                  sortOrder: node.sortOrder,
                  terminal: node.terminalFlag,
                  config: normalizeNodeConfig(parseLooseJson(node.configJson)),
                }))}
                edges={detailExecutableEdges.map((edge) => ({
                  edgeCode: edge.edgeCode,
                  fromNodeCode: edge.fromNodeCode,
                  toNodeCode: edge.toNodeCode,
                  edgeType: edge.edgeType,
                  priority: edge.priority,
                }))}
                evidenceDetailsEnabled={evidenceDetailsEnabled}
              />
              <Table
                title={() => "阶段与天序里程碑"}
                dataSource={detailData.milestones}
                rowKey="milestoneId"
                pagination={false}
                size="small"
                columns={milestoneColumns}
                className="medkernel-table"
              />
              <Table
                dataSource={detailData.nodes}
                rowKey="nodeId"
                pagination={false}
                size="small"
                columns={nodeColumns}
                className="medkernel-table"
              />
              <Table
                dataSource={detailData.edges}
                rowKey="edgeId"
                pagination={false}
                size="small"
                columns={edgeColumns}
                className="medkernel-table"
              />
              {evidenceDetailsEnabled &&
                detailData.edges.map((edge) => {
                  const guard = parseLooseJson(edge.conditionJson);
                  if (!guard || typeof guard !== "object" || Array.isArray(guard)) {
                    return null;
                  }
                  return (
                    <AuthoringReadablePreview
                      key={`edge-preview-${edge.edgeId}`}
                      subject="PATHWAY_GUARD"
                      dsl={{
                        guard,
                        edgeCode: edge.edgeCode,
                        fromNodeCode: edge.fromNodeCode,
                        toNodeCode: edge.toNodeCode,
                      }}
                    />
                  );
                })}
              <Table
                dataSource={detailData.metricBindings}
                rowKey="bindingId"
                pagination={false}
                size="small"
                columns={metricColumns}
                locale={{ emptyText: "暂无时钟指标绑定" }}
                className="medkernel-table"
              />
              <Table
                dataSource={detailData.outcomeBindings ?? []}
                rowKey={outcomeBindingKey}
                pagination={false}
                size="small"
                columns={outcomeColumns}
                locale={{ emptyText: "暂无结局指标绑定" }}
                className="medkernel-table"
              />
            </Space>
          ),
        },
        ...(detailAdvancedViewEnabled
          ? [
              {
                key: "l3",
                label: "受控配置文本",
                children: (
                  <Space direction="vertical" className={`mk-full-width ${styles.marginTopMd}`}>
                    <TextArea
                      value={buildDetailDslPreview(detailData)}
                      rows={22}
                      readOnly
                      className={styles.codeText}
                    />
                    {detailData.edges.map((edge) => {
                      const guard = parseLooseJson(edge.conditionJson);
                      if (!guard || typeof guard !== "object" || Array.isArray(guard)) {
                        return null;
                      }
                      return (
                        <AuthoringReadablePreview
                          key={`edge-l3-preview-${edge.edgeId}`}
                          subject="PATHWAY_GUARD"
                          dsl={{
                            guard,
                            edgeCode: edge.edgeCode,
                            fromNodeCode: edge.fromNodeCode,
                            toNodeCode: edge.toNodeCode,
                          }}
                        />
                      );
                    })}
                  </Space>
                ),
              },
            ]
          : []),
        {
          key: "simulate",
          label: (
            <span>
              <PlayCircleOutlined /> 真实快照试运行
            </span>
          ),
          children: (
            <Row gutter={16} className={styles.marginTopMd}>
              <Col xs={24} lg={9}>
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <div className={styles.formSection}>
                    <Form layout="vertical">
                      <Form.Item label="患者信息" htmlFor="pathway-snapshot-patient-id">
                        <Input
                          id="pathway-snapshot-patient-id"
                          value={snapshotPatientId}
                          onChange={(event) => setSnapshotPatientId(event.target.value)}
                          placeholder="输入患者信息检索快照"
                        />
                      </Form.Item>
                      <Form.Item label="就诊信息" htmlFor="pathway-snapshot-encounter-id">
                        <Input
                          id="pathway-snapshot-encounter-id"
                          value={snapshotEncounterId}
                          onChange={(event) => setSnapshotEncounterId(event.target.value)}
                          placeholder="输入就诊信息检索快照"
                        />
                      </Form.Item>
                      <Button
                        icon={<SearchOutlined />}
                        onClick={handleSnapshotSearch}
                        loading={snapshotsLoading}
                        className="mk-full-width"
                      >
                        读取真实快照
                      </Button>
                    </Form>
                  </div>

                  <div className={styles.snapshotList}>
                    {snapshotList.length > 0 ? (
                      <Space direction="vertical" className="mk-full-width">
                        {snapshotList.map((snapshot: ContextSnapshotSummary, index) => (
                          <Button
                            key={snapshot.snapshotId}
                            type={
                              selectedSnapshotId === snapshot.snapshotId ? "primary" : "default"
                            }
                            onClick={() => {
                              setSelectedSnapshotId(snapshot.snapshotId);
                              if (simulationMode === "QUEUE_REPLAY") {
                                setReplaySnapshotIds((current) =>
                                  current.includes(snapshot.snapshotId)
                                    ? current
                                    : [...current, snapshot.snapshotId],
                                );
                              }
                              setSimulationResponse(null);
                              setCreatePreviewRunResult(null);
                            }}
                            className={styles.snapshotButton}
                          >
                            <span>
                              {snapshotButtonLabel(snapshot, index, evidenceDetailsEnabled)}
                            </span>
                            <Tag className={styles.tagGap}>
                              {customerDisplayText(snapshot.qualityStatus)}
                            </Tag>
                            <span className={styles.textSmall}>
                              {snapshotAssociationText(snapshot, evidenceDetailsEnabled)}
                            </span>
                          </Button>
                        ))}
                      </Space>
                    ) : (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                          snapshotQuery
                            ? "未读取到已生效快照"
                            : "请输入患者信息或就诊信息读取真实快照"
                        }
                      />
                    )}
                  </div>
                </Space>
              </Col>
              <Col xs={24} lg={15}>
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <div className={styles.simulationPanel}>
                    <Row gutter={12}>
                      <Col xs={24} md={12}>
                        <Form layout="vertical">
                          <Form.Item label="试运行起点节点">
                            <Select value={selectedStartNode} onChange={setSimulateStartNode}>
                              {detailExecutableNodes.map((node) => (
                                <Option key={node.nodeCode} value={node.nodeCode}>
                                  {evidenceDetailsEnabled
                                    ? `${node.name} (${node.nodeCode})`
                                    : node.name}
                                </Option>
                              ))}
                            </Select>
                          </Form.Item>
                        </Form>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form layout="vertical">
                          <Form.Item label="试运行模式">
                            <Segmented
                              block
                              options={simulationModeOptions}
                              value={simulationMode}
                              onChange={(value) => {
                                setSimulationMode(value as PathwaySimulationMode);
                                setSimulationResponse(null);
                              }}
                            />
                          </Form.Item>
                          {simulationMode === "QUEUE_REPLAY" && (
                            <Form.Item label="回放快照队列">
                              <Select
                                mode="multiple"
                                value={replaySnapshotIds}
                                onChange={setReplaySnapshotIds}
                                placeholder="按顺序选择快照"
                                options={snapshotList.map((snapshot, index) => ({
                                  value: snapshot.snapshotId,
                                  label: `${snapshotButtonLabel(
                                    snapshot,
                                    index,
                                    evidenceDetailsEnabled,
                                  )} / ${customerDisplayText(snapshot.qualityStatus)}`,
                                }))}
                              />
                            </Form.Item>
                          )}
                          <Button
                            type="primary"
                            icon={<PlayCircleOutlined />}
                            onClick={handleSimulate}
                            loading={simulateMutation.isPending || selectedSnapshotLoading}
                            disabled={
                              simulationMode === "QUEUE_REPLAY"
                                ? replaySnapshotIds.length === 0
                                : !selectedSnapshotId
                            }
                            className={styles.primaryAction}
                          >
                            {simulationMode === "QUEUE_REPLAY"
                              ? "执行队列回放"
                              : "使用该快照试运行"}
                          </Button>
                        </Form>
                      </Col>
                    </Row>
                    {selectedSnapshotDetail && (
                      <Descriptions bordered size="small" column={detailDescriptionColumn}>
                        <Descriptions.Item label="快照">
                          {selectedSnapshotText(
                            selectedSnapshotDetail.snapshotId,
                            evidenceDetailsEnabled,
                          )}
                        </Descriptions.Item>
                        <Descriptions.Item label="状态">
                          {customerEnumLabel(selectedSnapshotDetail.status)}
                        </Descriptions.Item>
                        <Descriptions.Item label="质量">
                          {customerDisplayText(selectedSnapshotDetail.qualityStatus)}
                        </Descriptions.Item>
                        <Descriptions.Item label="追踪证据">
                          {evidenceText(
                            selectedSnapshotDetail.traceId,
                            evidenceDetailsEnabled,
                            "快照追踪已记录",
                          )}
                        </Descriptions.Item>
                      </Descriptions>
                    )}
                  </div>

                  <div className={styles.simulationResult}>
                    {simulationResponse ? (
                      <Space direction="vertical" size="middle" className="mk-full-width">
                        <Space wrap>
                          <Tag color="blue">
                            模式：
                            {customerEnumLabel(
                              simulationResponse.simulationMode ?? "SINGLE_SNAPSHOT",
                            )}
                          </Tag>
                          <Tag color={simulationQuality === "COMPLETE" ? "green" : "orange"}>
                            快照质量：{customerDisplayText(simulationQuality)}
                          </Tag>
                          <Tag color="purple">
                            最终状态：{customerEnumLabel(simulationResponse.finalStatus)}
                          </Tag>
                        </Space>
                        {simulationMapping.length > 0 && (
                          <Descriptions bordered size="small" column={1}>
                            {simulationMapping.map(([key, status]) => (
                              <Descriptions.Item key={key} label={key}>
                                {customerDisplayText(status)}
                              </Descriptions.Item>
                            ))}
                          </Descriptions>
                        )}
                        {(simulationResponse.replaySteps ?? []).length > 0 && (
                          <Table
                            dataSource={simulationResponse.replaySteps}
                            rowKey={(step) => step.snapshotId ?? step.nodeTrajectory.join("-")}
                            pagination={false}
                            size="small"
                            columns={[
                              {
                                title: "回放快照",
                                dataIndex: "snapshotId",
                                render: (snapshotId: string | null | undefined, _step, index) =>
                                  evidenceDetailsEnabled
                                    ? snapshotId || "未返回"
                                    : replaySnapshotBusinessLabel(index),
                              },
                              {
                                title: "轨迹",
                                dataIndex: "nodeTrajectory",
                                render: (trajectory: string[]) => detailTrajectoryText(trajectory),
                              },
                              {
                                title: "最终状态",
                                dataIndex: "finalStatus",
                                render: customerEnumLabel,
                              },
                            ]}
                            className="medkernel-table"
                          />
                        )}
                        <Timeline
                          items={simulationResponse.nodeTrajectory.map((nodeCode, index) => {
                            const nodeDetail = detailData.nodes.find(
                              (node) => node.nodeCode === nodeCode,
                            );
                            return {
                              key: `${nodeCode}-${index}`,
                              color: index === 0 ? "blue" : "green",
                              children: (
                                <>
                                  <div className={styles.timelineTitle}>
                                    {nodeDetail?.name ?? "未知节点"}
                                  </div>
                                  <div className={styles.timelineMeta}>
                                    {nodeEvidenceText(nodeCode, index, evidenceDetailsEnabled)}
                                  </div>
                                </>
                              ),
                            };
                          })}
                        />
                      </Space>
                    ) : (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="选择已生效快照后可试运行路径"
                      />
                    )}
                  </div>
                </Space>
              </Col>
            </Row>
          ),
        },
      ]
    : [];

  return (
    <PageShell
      title="临床路径库"
      description="编排专病临床路径，使用统一条件树、规则引用和真实快照试运行；上线生效由机构生效版本统一管理。"
    >
      <div className={`${styles.surface} ${styles.filterSurface}`}>
        <Form layout="inline" className={styles.inlineForm}>
          <Form.Item label="状态">
            <Select
              placeholder="选择状态"
              allowClear
              value={statusFilter}
              onChange={setStatusFilter}
              className={styles.controlSm}
            >
              <Option value="DRAFT">设计中</Option>
              <Option value="PUBLISHED">已发布</Option>
              <Option value="OFFLINE">已下线</Option>
            </Select>
          </Form.Item>
          <Form.Item label="适用病种身份">
            <Input
              placeholder="输入真实病种或诊断分组身份"
              allowClear
              value={diseaseFilter}
              onChange={(event) => setDiseaseFilter(event.target.value)}
              className={styles.controlSm}
            />
          </Form.Item>
          <Form.Item className={styles.toolbarActions}>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setSelectedPathwayPrototype("blank");
                resetCreateTemplateDraft();
                resetSimulation();
                setCreateAdvancedConfigEnabled(false);
                setCreateTemplateVisible(true);
              }}
            >
              新建临床路径
            </Button>
          </Form.Item>
        </Form>
      </div>

      <div className={styles.surface}>
        <Table
          columns={columns}
          dataSource={listData?.items || []}
          rowKey="id"
          loading={listLoading}
          pagination={{
            current: page,
            pageSize: size,
            total: listData?.total || 0,
            onChange: (nextPage) => setPage(nextPage),
            showTotal: (total) => `共 ${total} 条临床路径`,
          }}
          className="medkernel-table"
        />
      </div>

      <Modal
        title="新建临床路径"
        open={createTemplateVisible}
        onOk={handleCreateTemplate}
        onCancel={() => {
          setCreateTemplateVisible(false);
          setCreatePreviewRunResult(null);
        }}
        width="min(980px, calc(100vw - 32px))"
        confirmLoading={createTemplateMutation.isPending}
        destroyOnClose
      >
        <Form form={templateForm} layout="vertical" className={styles.marginTopMd}>
          <Space className={`mk-flex-between mk-full-width ${styles.marginBottomMd}`}>
            <span className={`${styles.textSmall} ${styles.textSecondary}`}>
              普通配置只展示 L1/L2；受控配置文本需显式进入受控配置模式。
            </span>
            <Space>
              <span>受控配置文本模式</span>
              <Switch
                aria-label="受控配置文本模式"
                checked={createAdvancedConfigEnabled}
                onChange={toggleCreateAdvancedConfigEnabled}
              />
            </Space>
          </Space>
          <Tabs defaultActiveKey="l1" items={createLayerItems} />
        </Form>
      </Modal>

      <Drawer
        title="临床路径详情与真实快照试运行"
        width="min(1080px, 100vw)"
        onClose={() => {
          setSelectedTemplateId(null);
          setDetailActiveTab("l1");
          setDetailAdvancedViewEnabled(false);
          setSimulateStartNode("");
          resetSimulation();
        }}
        open={!!selectedTemplateId}
        loading={detailLoading}
        destroyOnClose
      >
        {detailData && (
          <div>
            <Alert
              message={detailAlertMessage}
              type={detailAlertType}
              showIcon
              className={styles.marginBottomLg}
            />
            {immutableVersion && (
              <Space className={`mk-flex-between mk-full-width ${styles.marginBottomMd}`} wrap>
                <span className={`${styles.textSmall} ${styles.textSecondary}`}>
                  已发布内容版本不可原地修改；复制后由系统自动分配下一版本号。
                </span>
                <Button
                  type="primary"
                  icon={<CopyOutlined />}
                  onClick={handleCopyTemplateAsNewVersion}
                >
                  复制为新版本
                </Button>
              </Space>
            )}
            <Space className={`mk-flex-between mk-full-width ${styles.marginBottomMd}`}>
              <span className={`${styles.textSmall} ${styles.textSecondary}`}>
                路径拓扑与真实快照试运行是主视图；证据详情打开后可追溯受控配置。
              </span>
              <Space>
                <Tooltip title="展开审计追溯、原始标识和受控诊断字段">
                  <span>追溯证据</span>
                </Tooltip>
                <Switch
                  aria-label="证据详情"
                  checked={detailAdvancedViewEnabled}
                  onChange={toggleDetailAdvancedViewEnabled}
                />
              </Space>
            </Space>
            <Tabs
              activeKey={detailActiveTab}
              onChange={setDetailActiveTab}
              items={detailLayerItems}
            />
          </div>
        )}
      </Drawer>

      <FieldCatalogManager open={fieldManagerOpen} onClose={() => setFieldManagerOpen(false)} />
    </PageShell>
  );
}
