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
  Typography,
} from "antd";
import type { BadgeProps, RadioChangeEvent, TableProps, TabsProps } from "antd";
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  DeploymentUnitOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  PlayCircleOutlined,
  SearchOutlined,
  SwapOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { StepFlow } from "@/shared/ui/StepFlow";
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
  useBuildPathwayKnowledgePackage,
  useConditionFragments,
  useAuthoringPreviewRun,
  useEvaluationIndicators,
  useFullRolloutPathwayTemplate,
  usePathwayTemplateDetail,
  usePathwayTemplateInheritanceDiff,
  usePathwayTemplateImpact,
  usePathwayTemplates,
  usePublishPathwayTemplate,
  useRollbackPathwayTemplate,
  useSimulatePathway,
  usePackages,
} from "@/shared/api/hooks";
import type {
  ContextSnapshotSummary,
  AuthoringPreviewRunEvidence,
  AuthoringPreviewRunResponse,
  ConditionFragmentResponse,
  EvaluationIndicator,
  PathwayEdge,
  PathwayEdgeType,
  PathwayEntryMode,
  PathwayInheritanceChangeType,
  PathwayMilestone,
  PathwayMergedNode,
  PathwayNode,
  PathwayNodeType,
  PathwayOutcomeBinding,
  PathwayOutcomeScope,
  PathwaySimulationResponse,
  PathwaySimulationMode,
  PathwayTemplate,
  PathwayTemplateDetailResponse,
  PathwayTemplateImpactResponse,
  PathwayTemplateInheritanceDiffItem,
  PathwayTemplateLevel,
  PathwayTemplateStatus,
  KnowledgePackage,
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
const { Text } = Typography;

const PATHWAY_PACKAGE_REFERENCE_PAGE_SIZE = 20;
const PATHWAY_ROLLBACK_TARGET_PAGE_SIZE = 20;
const PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE = 20;

type PathwayBadgeStatus = Exclude<BadgeProps["status"], undefined>;

const PATHWAY_CONTENT_STATUS: Record<
  PathwayTemplateStatus,
  { status: PathwayBadgeStatus; text: string }
> = {
  DRAFT: { status: "warning", text: "设计中" },
  PUBLISHED: { status: "processing", text: "内容已审核" },
  OFFLINE: { status: "default", text: "已下线" },
};

const PATHWAY_DEPLOYMENT_STATUS: Record<string, { status: PathwayBadgeStatus; text: string }> = {
  DRAFT: { status: "warning", text: "待提交" },
  IN_REVIEW: { status: "processing", text: "审核中" },
  APPROVED: { status: "processing", text: "已批准待发布" },
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

function inheritanceChangeText(type: PathwayInheritanceChangeType | string | undefined) {
  if (type === "OVERRIDDEN") return "覆盖";
  if (type === "ADDED") return "新增";
  if (type === "DISABLED") return "禁用";
  return type ? customerEnumLabel(type) : "-";
}

function inheritanceChangeColor(type: PathwayInheritanceChangeType | string | undefined) {
  if (type === "DISABLED") return "red";
  if (type === "ADDED") return "green";
  return "orange";
}

function inheritanceOriginText(origin: string | undefined) {
  if (origin === "INHERITED") return "继承";
  if (origin === "OVERRIDDEN") return "覆盖";
  if (origin === "ADDED") return "新增";
  return origin ?? "-";
}

function inheritanceOriginColor(origin: string | undefined) {
  if (origin === "INHERITED") return "blue";
  if (origin === "ADDED") return "green";
  return "orange";
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
  packageVersion?: string;
};

type PathwayOutcomeBindingInput = {
  scope?: PathwayOutcomeScope;
  refCode?: string | null;
  indicatorCode?: string;
  packageVersion?: string | null;
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
  conditionFragmentId?: string;
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
  packageId: string;
  templateCode: string;
  name: string;
  diseaseCode: string;
  templateLevel: PathwayTemplateLevel;
  parentTemplateId?: string;
  templateVersion: number;
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

type PathwayPrototypeKey = "blank" | "ed_disposition";

const templateLevelOptions: Array<{ value: PathwayTemplateLevel; label: string }> = [
  { value: "STANDARD", label: "平台标准模板" },
  { value: "HOSPITAL", label: "医院模板" },
  { value: "DEPARTMENT", label: "科室模板" },
  { value: "SPECIALTY", label: "专科模板" },
];

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
  { value: "SUBPATHWAY", label: "子路径" },
  { value: "MANUAL_GATE", label: "人工确认节点" },
  { value: "ORDER_SET", label: "医嘱集" },
];

const clockBaselineEventOptions = [
  { value: "NODE_START", label: "节点开始" },
  { value: "PATHWAY_ENTRY", label: "患者入径" },
  { value: "ADMISSION", label: "入院时间" },
];

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
  { value: "TEMPLATE", label: "模板" },
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
    key: "ed_disposition",
    title: "急诊处置路径",
    description: "默认生成急诊评估到处置安排的两节点安全骨架。",
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

function pathwayPackageDiseaseCode(pack: KnowledgePackage) {
  const scope = pack.applicableScope?.trim();
  return scope?.startsWith("disease:") ? scope.slice("disease:".length) : "全部病种";
}

function parseConditionJson(value?: string) {
  const normalized = cleanText(value);
  if (!normalized) return undefined;
  try {
    return JSON.parse(normalized) as unknown;
  } catch {
    throw new Error("条件 DSL JSON 格式不合法，请检查后再提交。");
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

function conditionFragmentToPathwayTree(
  fragment: ConditionFragmentResponse,
  mode: "reference" | "copy",
): RuleGroup {
  if (mode === "copy") {
    return dslToRootGroup(fragment.bodyJson);
  }
  return dslToRootGroup({
    fragmentRef: fragment.fragmentCode,
    version: fragment.versionNo,
    packageVersion: fragment.packageVersion,
    ui: {
      label: fragment.name,
      fragmentId: fragment.fragmentId,
    },
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
      const responsibleRole = cleanText(node.responsibleRole) ?? "专科医生";
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
  if (!clockSla) return `关键时钟节点 ${node.nodeCode} 必须配置临床时钟 SLA`;
  const minMinutes = Number(clockSla.minMinutes);
  const targetMinutes = Number(clockSla.targetMinutes);
  const maxMinutes = Number(clockSla.maxMinutes);
  if (
    !Number.isFinite(minMinutes) ||
    !Number.isFinite(targetMinutes) ||
    !Number.isFinite(maxMinutes)
  ) {
    return `关键时钟节点 ${node.nodeCode} 的 SLA 时限必须完整`;
  }
  if (
    minMinutes < 0 ||
    targetMinutes <= 0 ||
    maxMinutes < targetMinutes ||
    minMinutes > targetMinutes
  ) {
    return `关键时钟节点 ${node.nodeCode} 的 SLA 时限必须满足 min <= target <= max`;
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
        return `等待计时节点 ${node.nodeCode} 必须填写 clock 或时窗分钟`;
      }
      if (!hasTimerGuard) {
        return `等待计时节点 ${node.nodeCode} 必须配置计时条件边`;
      }
    }
    const clockError = clockSlaError(node);
    if (clockError) return clockError;
    if (node.nodeType === "SUBPATHWAY" && !configText(node.config, "subPathwayRef")) {
      return `子路径节点 ${node.nodeCode} 必须填写子路径引用`;
    }
    if (!node.responsibleRole) {
      return `节点 ${node.nodeCode} 必须填写责任角色`;
    }
    if (!node.accountableRole) {
      return `节点 ${node.nodeCode} 必须填写签责角色`;
    }
    if (node.nodeType === "ORDER_SET" && !configText(node.config, "orderSetRef")) {
      return `医嘱集节点 ${node.nodeCode} 必须填写医嘱集引用`;
    }
  }
  return undefined;
}

function richNodeConfigSummary(node: PathwayNode) {
  const config = parseLooseJson(node.configJson);
  const orderSetRef = configText(config, "orderSetRef");
  if (orderSetRef) return `医嘱集 ${orderSetRef}`;
  const subPathwayRef = configText(config, "subPathwayRef");
  if (subPathwayRef) return `子路径 ${subPathwayRef}`;
  const clock = configText(config, "clock");
  if (clock) return `计时 ${clock}`;
  const clockSla = configObject(config, "clockSla");
  if (clockSla) {
    return `SLA ${clockSla.baselineEvent ?? "NODE_START"} / ${clockSla.targetMinutes ?? "-"} 分钟`;
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
      packageVersion: cleanText(binding.packageVersion),
    }));
}

function outcomeScopeText(scope?: PathwayOutcomeScope | string | null) {
  if (scope === "PHASE") return "阶段";
  if (scope === "MILESTONE") return "里程碑";
  return "模板";
}

function outcomeRefText(binding: Pick<PathwayOutcomeBinding, "scope" | "refCode">) {
  return binding.scope === "TEMPLATE" ? "全模板" : binding.refCode || "-";
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
      parentTemplateId: detail.template.parentTemplateId,
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
      packageVersion: binding.packageVersion,
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
  const code = cleanText(milestone.milestoneCode) ?? "未设置编码";
  return `${phase} / ${milestoneDayText(milestone.dayOffset)} / ${name}（${code}）`;
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
    throw new Error("L3 DSL JSON 格式不合法，请检查 nodes、edges 与 metricBindings。");
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
      priority: Number(edge.priority ?? index + 1),
    };
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
    priority: edge.priority,
  };
  const condition = parseLooseJson(edge.conditionJson);
  if (!condition) {
    return base;
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
    packageId: detail.template.packageId,
    templateCode: detail.template.templateCode,
    name: detail.template.name,
    diseaseCode: detail.template.diseaseCode,
    templateLevel: detail.template.templateLevel,
    parentTemplateId: detail.template.templateId,
    templateVersion: Number(detail.template.templateVersion ?? 0) + 1,
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
    issues.push(`节点编码 ${code} 重复，请改为唯一编码。`);
  }
  for (const code of duplicatedCodes(edges.map((edge) => edge.edgeCode))) {
    issues.push(`边编码 ${code} 重复，请改为唯一编码。`);
  }
  if (!nodes.some((node) => node.terminal)) {
    issues.push("至少需要一个终止节点。");
  }

  const nodeCodes = new Set(nodes.map((node) => node.nodeCode).filter(Boolean));
  for (const edge of edges) {
    if (!nodeCodes.has(edge.fromNodeCode) || !nodeCodes.has(edge.toNodeCode)) {
      issues.push(`边 ${edge.edgeCode || "未编码"} 引用不存在节点，请从已建节点中选择。`);
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
  const { message: messageApi, modal } = App.useApp();
  const screens = Grid.useBreakpoint();
  const isWideViewport =
    screens.md ?? (typeof window === "undefined" ? true : window.innerWidth >= 768);
  const detailDescriptionColumn = isWideViewport ? 2 : 1;
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);

  const [statusFilter, setStatusFilter] = useState<PathwayTemplateStatus | undefined>(undefined);
  const [diseaseFilter, setDiseaseFilter] = useState<string>("");
  const [packageFilter, setPackageFilter] = useState<string>("");
  const [packageSearch, setPackageSearch] = useState<string>("");
  const [outcomeIndicatorSearch, setOutcomeIndicatorSearch] = useState<string>("");
  const [outcomePackageSearch, setOutcomePackageSearch] = useState<string>("");

  const [packageDrawerVisible, setPackageDrawerVisible] = useState<boolean>(false);
  const [createTemplateVisible, setCreateTemplateVisible] = useState<boolean>(false);
  const [fieldManagerOpen, setFieldManagerOpen] = useState<boolean>(false);
  const [createExpertMode, setCreateExpertMode] = useState<boolean>(false);
  const [detailExpertMode, setDetailExpertMode] = useState<boolean>(false);
  const [selectedPathwayPrototype, setSelectedPathwayPrototype] =
    useState<PathwayPrototypeKey>("blank");
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  const [detailActiveTab, setDetailActiveTab] = useState<string>("l1");
  const [releaseReason, setReleaseReason] = useState<string>("");
  const [rollbackTargetTemplateId, setRollbackTargetTemplateId] = useState<string | undefined>(
    undefined,
  );

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
    packageId: packageFilter || undefined,
    page,
    size,
  });

  const {
    data: detailData,
    isLoading: detailLoading,
    refetch: refetchDetail,
  } = usePathwayTemplateDetail(selectedTemplateId || "");

  const impactQuery = usePathwayTemplateImpact(selectedTemplateId || "", {
    enabled: !!selectedTemplateId,
  });
  const inheritanceDiffQuery = usePathwayTemplateInheritanceDiff(selectedTemplateId || "", {
    enabled: !!selectedTemplateId,
  });
  const { data: rollbackTargetsData } = usePathwayTemplates(
    {
      status: "OFFLINE",
      templateCode: detailData?.template.templateCode,
      page: 1,
      size: PATHWAY_ROLLBACK_TARGET_PAGE_SIZE,
    },
    {
      enabled: detailData?.template.status === "PUBLISHED" && !!detailData.template.templateCode,
    },
  );

  const packageSearchKeyword =
    cleanText(packageSearch) ?? cleanText(detailData?.template.packageId) ?? undefined;
  const { data: packagesData, refetch: refetchPackages } = usePackages({
    page: 1,
    size: PATHWAY_PACKAGE_REFERENCE_PAGE_SIZE,
    assetType: "PATHWAY",
    ...(packageSearchKeyword ? { keyword: packageSearchKeyword } : {}),
  });
  const outcomePackageKeyword = cleanText(outcomePackageSearch);
  const {
    data: evaluationPackagesData,
    isLoading: evaluationPackagesLoading,
    isError: evaluationPackagesError,
  } = usePackages({
    page: 1,
    size: PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE,
    assetType: "EVALUATION",
    ...(outcomePackageKeyword ? { keyword: outcomePackageKeyword } : {}),
  });
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

  const fieldCatalogQuery = useContextFieldCatalog();
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
  const { data: snapshotsData, isLoading: snapshotsLoading } = useContextSnapshots(
    snapshotQuery ?? undefined,
    { enabled: !!snapshotQuery },
  );

  const { data: selectedSnapshotDetail, isLoading: selectedSnapshotLoading } =
    useContextSnapshotDetail(selectedSnapshotId || "", { enabled: !!selectedSnapshotId });
  const snapshotList = snapshotsData?.items ?? [];

  const createPackageMutation = useBuildPathwayKnowledgePackage();
  const createTemplateMutation = useCreatePathwayTemplate();
  const publishTemplateMutation = usePublishPathwayTemplate();
  const fullRolloutMutation = useFullRolloutPathwayTemplate();
  const rollbackMutation = useRollbackPathwayTemplate();
  const simulateMutation = useSimulatePathway(selectedTemplateId || "");
  const previewRunMutation = useAuthoringPreviewRun();

  const [packageForm] = Form.useForm();
  const [templateForm] = Form.useForm<PathwayTemplateFormValue>();
  const watchedMilestones = Form.useWatch("milestones", templateForm);
  const watchedNodes = Form.useWatch("nodes", templateForm);
  const watchedEdges = Form.useWatch("edges", templateForm);
  const watchedOutcomeBindings = Form.useWatch("outcomeBindings", templateForm);
  const watchedStartNodeCode = Form.useWatch("startNodeCode", templateForm);
  const watchedPackageId = Form.useWatch("packageId", templateForm);

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
        .map((node) => ({
          value: node.nodeCode,
          label: `${cleanText(node.name) ?? "未命名节点"}（${node.nodeCode}）`,
        })),
    [canvasNodes],
  );

  const parentTemplateOptions = useMemo(
    () =>
      (listData?.items ?? [])
        .filter((template: PathwayTemplate) => template.status !== "OFFLINE")
        .map((template: PathwayTemplate) => ({
          value: template.templateId,
          label: `${template.templateCode} v${template.templateVersion}.0 / ${template.templateLevel}`,
        })),
    [listData?.items],
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
      label: `${label}（${value}）`,
    }));
  }, [watchedMilestones]);

  const outcomeIndicatorOptions = useMemo(
    () =>
      (evaluationIndicatorsData?.items ?? []).map((indicator: EvaluationIndicator) => ({
        value: indicator.indicatorCode,
        label: `${indicator.name}（${indicator.indicatorCode}）`,
      })),
    [evaluationIndicatorsData?.items],
  );
  const outcomeIndicatorByCode = useMemo(
    () =>
      new Map(
        (evaluationIndicatorsData?.items ?? []).map((indicator: EvaluationIndicator) => [
          indicator.indicatorCode,
          indicator,
        ]),
      ),
    [evaluationIndicatorsData?.items],
  );
  const outcomeIndicatorPackageOptions = useMemo(() => {
    const byVersion = new Map<string, string>();
    for (const item of evaluationPackagesData?.items ?? []) {
      if (item.status === "OFFLINE" || item.status === "ARCHIVED") continue;
      byVersion.set(item.packageVersion, `${item.packageVersion} · ${item.name}`);
    }
    for (const indicator of evaluationIndicatorsData?.items ?? []) {
      if (indicator.packageVersion && !byVersion.has(indicator.packageVersion)) {
        byVersion.set(indicator.packageVersion, `${indicator.packageVersion} · 指标来源包`);
      }
    }
    return [...byVersion.entries()].map(([value, label]) => ({ value, label }));
  }, [evaluationIndicatorsData?.items, evaluationPackagesData?.items]);
  const packageVersionFor = (packageId?: string | null) =>
    cleanText(packagesData?.items?.find((pkg) => pkg.packageId === packageId)?.packageVersion);
  const selectedTemplatePackageVersion =
    typeof watchedPackageId === "string" ? packageVersionFor(watchedPackageId) : undefined;
  const createTemplatePackageVersion = selectedTemplatePackageVersion ?? "";
  const conditionFragmentPackageVersion = selectedTemplatePackageVersion ?? "";
  const requirePathwayPackageVersion = (
    packageId: string | null | undefined,
    errorMessage: string,
  ) => {
    const packageVersion = packageVersionFor(packageId);
    if (!packageVersion) {
      messageApi.error(errorMessage);
      return undefined;
    }
    return packageVersion;
  };
  const activeConditionFragmentsQuery = useConditionFragments(
    {
      status: "ACTIVE",
      packageVersion: conditionFragmentPackageVersion,
      page: 1,
      size: 50,
    },
    {
      enabled: createTemplateVisible && Boolean(conditionFragmentPackageVersion),
    },
  );
  const activeConditionFragments = useMemo(
    () => activeConditionFragmentsQuery.data?.items ?? [],
    [activeConditionFragmentsQuery.data?.items],
  );
  const conditionFragmentById = useMemo(
    () => new Map(activeConditionFragments.map((fragment) => [fragment.fragmentId, fragment])),
    [activeConditionFragments],
  );
  const conditionFragmentOptions = useMemo(
    () =>
      activeConditionFragments.map((fragment) => ({
        value: fragment.fragmentId,
        label: `${fragment.name} · ${fragment.fragmentCode} · v${fragment.versionNo}`,
      })),
    [activeConditionFragments],
  );
  const applyEdgeConditionFragment = (edgeIndex: number, mode: "reference" | "copy") => {
    const fragmentId = templateForm.getFieldValue(["edges", edgeIndex, "conditionFragmentId"]);
    const fragment =
      typeof fragmentId === "string" ? conditionFragmentById.get(fragmentId) : undefined;
    if (!fragment) {
      messageApi.warning("请选择条件片段。");
      return;
    }
    try {
      templateForm.setFieldValue(
        ["edges", edgeIndex, "conditionTree"],
        conditionFragmentToPathwayTree(fragment, mode),
      );
      messageApi.success(mode === "reference" ? "已引用条件片段" : "已拷贝条件片段正文");
    } catch {
      messageApi.error("条件片段正文无法转成当前路径边条件。");
    }
  };

  // 自动生成不重复的顺序编码（节点 N1/N2…，边 E1/E2…），可改但默认不必手填。
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
      templateVersion: 1,
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
    setCreateExpertMode(false);
    setCreateTemplateVisible(true);
    messageApi.success(`已复制为 v${nextValues.templateVersion}.0 草稿，请核查后提交。`);
  };

  const applyPathwayPrototype = (prototypeKey: PathwayPrototypeKey) => {
    setSelectedPathwayPrototype(prototypeKey);
    if (prototypeKey === "blank") {
      resetCreateTemplateDraft();
      return;
    }

    const packageId = packagesData?.items?.[0]?.packageId;
    const milestones: PathwayMilestoneFormValue[] = [
      {
        phaseCode: "ED",
        phaseName: "急诊处置",
        milestoneCode: "M-ED-ASSESS",
        name: "完成急诊评估",
        dayOffset: 0,
        expectedOffsetMinutes: 30,
        sortOrder: 1,
      },
    ];
    const nodes: PathwayNodeFormValue[] = [
      {
        nodeCode: "ASSESS",
        name: "急诊评估",
        nodeType: "ASSESSMENT",
        milestoneCode: "M-ED-ASSESS",
        sortOrder: 1,
        responsibleRole: "急诊医生",
        accountableRole: "急诊医生",
      },
      {
        nodeCode: "DISPOSITION",
        name: "处置安排",
        nodeType: "DISCHARGE",
        milestoneCode: "M-ED-ASSESS",
        sortOrder: 2,
        responsibleRole: "急诊医生",
        accountableRole: "急诊医生",
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
      packageId,
      name: "急诊处置路径",
      templateCode: "PATH.ED.DISPOSITION",
      diseaseCode: "ED",
      templateLevel: "STANDARD",
      parentTemplateId: undefined,
      templateVersion: 1,
      entryMode: "AUTO_SUGGEST",
      startNodeCode: "ASSESS",
      sourceRef: "院内已审核急诊处置制度",
      description: "急诊评估后进入处置或离院安排。",
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
        packageVersion={createTemplatePackageVersion}
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

  const toggleCreateExpertMode = (checked: boolean) => {
    setCreateExpertMode(checked);
  };

  const toggleDetailExpertMode = (checked: boolean) => {
    setDetailExpertMode(checked);
    if (!checked && detailActiveTab === "l3") {
      setDetailActiveTab("l2");
    }
  };

  const handleCreatePackage = async () => {
    try {
      const values = await packageForm.validateFields();
      await createPackageMutation.mutateAsync(values);
      messageApi.success("路径知识包草稿创建成功");
      packageForm.resetFields();
      refetchPackages();
    } catch (error: unknown) {
      if (applyApiFieldErrors(packageForm, error)) return;
      messageApi.error(getApiErrorMessage(error, "创建路径知识包失败，请检查参数"));
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
          messageApi.error("阶段里程碑必须填写阶段编码、阶段名称、里程碑编码和名称");
          return;
        }
        if (milestoneCodes.has(milestone.milestoneCode)) {
          messageApi.error(`里程碑编码 ${milestone.milestoneCode} 重复，请改为唯一编码。`);
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
        messageApi.error("起始节点编码必须来自 L2 节点画布");
        return;
      }
      const timedNodeWithoutMetric = activeNodes.find(
        (node) =>
          (node.timeWindowMinutes ?? 0) > 0 &&
          !metricBindings.some((binding) => binding.nodeCode === node.nodeCode),
      );
      if (timedNodeWithoutMetric) {
        messageApi.error(`节点 ${timedNodeWithoutMetric.nodeCode} 设置时窗后必须绑定时钟指标编码`);
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

      const packageVersion = requirePathwayPackageVersion(
        values.packageId,
        "无法确认路径模板所属的配置包版本，暂不能创建或复制路径。",
      );
      if (!packageVersion) return;

      await createTemplateMutation.mutateAsync({
        packageId: values.packageId,
        templateCode: values.templateCode,
        name: values.name,
        diseaseCode: values.diseaseCode,
        packageVersion,
        templateLevel: values.templateLevel,
        parentTemplateId: cleanText(values.parentTemplateId),
        templateVersion: Number(values.templateVersion),
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

      messageApi.success("专病路径模板草稿创建成功");
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
      messageApi.error(getApiErrorMessage(error, "创建路径模板失败"));
    }
  };

  const syncCanvasToDsl = () => {
    if (fieldCatalogQuery.isError) {
      messageApi.error("字段目录暂不可用，路径条件不能同步到 DSL。");
      return;
    }
    try {
      const values = templateForm.getFieldsValue(true);
      setPathwayDslJson(
        formatJson(
          buildDraftDsl(values.milestones, values.nodes, values.edges, values.outcomeBindings),
        ),
      );
      setCreateExpertMode(true);
      messageApi.success("已从 L2 节点画布同步到 L3 DSL");
    } catch (error: unknown) {
      messageApi.error(error instanceof Error ? error.message : "L2 节点画布无法生成 DSL");
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
      messageApi.success("已将 L3 DSL 回填到 L2 节点画布");
    } catch (error: unknown) {
      messageApi.error(error instanceof Error ? error.message : "L3 DSL 回填失败");
    }
  };

  const handlePublishTemplate = async () => {
    if (!selectedTemplateId) return;
    const impactDigest = impactQuery.data?.impactDigest;
    const reason = cleanText(releaseReason);
    if (!impactDigest) {
      messageApi.error("请先读取发布影响摘要后再提交审核。");
      return;
    }
    if (!reason) {
      messageApi.error("请填写发布审核说明。");
      return;
    }
    const packageVersion = requirePathwayPackageVersion(
      detailData?.template.packageId,
      "无法确认当前路径模板所属的配置包版本，暂不能发布路径。",
    );
    if (!packageVersion) return;
    try {
      await publishTemplateMutation.mutateAsync({
        templateId: selectedTemplateId,
        packageVersion,
        impactDigest,
        reason,
      });
      messageApi.success("路径模板已通过门禁，进入 10% 灰度发布并保留回滚证据");
      setReleaseReason("");
      refetchDetail();
      refetchList();
    } catch (error: unknown) {
      modal.error({
        title: "路径发布门禁拒绝",
        content: getApiErrorMessage(error, "未通过路径闭环或时窗门禁核查，禁止上线。"),
      });
    }
  };

  const releasePackageVersion = () => packageVersionFor(detailData?.template.packageId) ?? "";

  const handleFullRolloutTemplate = async () => {
    if (!selectedTemplateId) return;
    const impactDigest = impactQuery.data?.impactDigest;
    const reason = cleanText(releaseReason);
    if (!impactDigest) {
      messageApi.error("请先读取发布影响摘要后再全量确认。");
      return;
    }
    if (!reason) {
      messageApi.error("请填写全量确认说明。");
      return;
    }
    const packageVersion = requirePathwayPackageVersion(
      detailData?.template.packageId,
      "无法确认当前路径模板所属的配置包版本，暂不能发布路径。",
    );
    if (!packageVersion) return;
    try {
      await fullRolloutMutation.mutateAsync({
        templateId: selectedTemplateId,
        packageVersion,
        impactDigest,
        reason,
      });
      messageApi.success("路径模板已完成院级全量确认");
      setReleaseReason("");
      refetchDetail();
      refetchList();
    } catch (error: unknown) {
      modal.error({
        title: "全量发布门禁拒绝",
        content: getApiErrorMessage(error, "未通过院级管理员确认或影响摘要核查。"),
      });
    }
  };

  const handleRollbackTemplate = async () => {
    if (!selectedTemplateId) return;
    const impactDigest = impactQuery.data?.impactDigest;
    const reason = cleanText(releaseReason);
    if (!impactDigest) {
      messageApi.error("请先读取发布影响摘要后再回滚。");
      return;
    }
    if (!reason) {
      messageApi.error("请填写回滚说明。");
      return;
    }
    if (!rollbackTargetTemplateId) {
      messageApi.error("请选择回滚目标模板版本。");
      return;
    }
    const packageVersion = requirePathwayPackageVersion(
      detailData?.template.packageId,
      "无法确认当前路径模板所属的配置包版本，暂不能回滚路径。",
    );
    if (!packageVersion) return;
    try {
      await rollbackMutation.mutateAsync({
        templateId: selectedTemplateId,
        packageVersion,
        rollbackTargetTemplateId,
        impactDigest,
        reason,
      });
      messageApi.success("路径模板已回滚到目标版本并保留审计证据");
      setReleaseReason("");
      setRollbackTargetTemplateId(undefined);
      refetchDetail();
      refetchList();
    } catch (error: unknown) {
      modal.error({
        title: "路径回滚门禁拒绝",
        content: getApiErrorMessage(error, "未通过回滚目标、院级确认或影响摘要核查。"),
      });
    }
  };

  const handleSnapshotSearch = () => {
    const patientId = cleanText(snapshotPatientId);
    const encounterId = cleanText(snapshotEncounterId);
    if (!patientId && !encounterId) {
      messageApi.error("请输入患者 ID 或就诊 ID 后读取快照");
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
    const packageVersion = cleanText(createTemplatePackageVersion);
    if (!packageVersion) {
      messageApi.error("请先选择可解析配置包版本的路径知识包。");
      return;
    }
    if (!selectedSnapshotId) {
      messageApi.error("请先选择一个 ACTIVE 上下文快照。");
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
        packageVersion,
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
          ? "请选择至少一个 ACTIVE 上下文快照用于队列回放"
          : "请先选择一个 ACTIVE 上下文快照",
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
    const simulationPackageVersion =
      cleanText(selectedSnapshotDetail?.packageVersion) ??
      packageVersionFor(detailData?.template.packageId);
    if (!simulationPackageVersion) {
      messageApi.error("无法确认当前路径模板所属的配置包版本，暂不能试运行路径。");
      return;
    }
    try {
      const result = await simulateMutation.mutateAsync({
        packageVersion: simulationPackageVersion,
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
      title: "模板代码",
      dataIndex: "templateCode",
      key: "templateCode",
      render: (text: string) => <Tag color="geekblue">{text}</Tag>,
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
      render: (value: number) => `v${value}.0`,
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
            setDetailExpertMode(false);
            setReleaseReason("");
            setRollbackTargetTemplateId(undefined);
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
      title: "节点代码",
      dataIndex: "nodeCode",
      render: (code: string) => <Tag color="blue">{code}</Tag>,
    },
    { title: "名称", dataIndex: "name", className: styles.textStrong },
    {
      title: "节点类型",
      dataIndex: "nodeType",
      render: (type: PathwayNodeType) => <Tag color="purple">{type}</Tag>,
    },
    {
      title: "里程碑",
      dataIndex: "milestoneCode",
      render: (code?: string) => (code ? <Tag color="geekblue">{code}</Tag> : "未绑定"),
    },
    {
      title: "时窗",
      dataIndex: "timeWindowMinutes",
      render: (minutes?: number) => (minutes ? `${minutes} 分钟` : "无"),
    },
    {
      title: "配置引用",
      key: "config",
      render: (_value, node) => richNodeConfigSummary(node),
    },
    {
      title: "RACI",
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
    { title: "边代码", dataIndex: "edgeCode" },
    {
      title: "源节点",
      dataIndex: "fromNodeCode",
      render: (code: string) => <Tag color="orange">{code}</Tag>,
    },
    {
      title: "目标节点",
      dataIndex: "toNodeCode",
      render: (code: string) => <Tag color="green">{code}</Tag>,
    },
    {
      title: "流转类型",
      dataIndex: "edgeType",
      render: (type: PathwayEdgeType) => <Tag color="cyan">{type}</Tag>,
    },
    {
      title: "条件 DSL",
      dataIndex: "conditionJson",
      render: (condition?: string) => (
        <span className={styles.codeText}>{cleanText(condition) ?? "默认流转"}</span>
      ),
    },
    { title: "优先级", dataIndex: "priority" },
  ];

  const metricColumns: TableProps<SpecialtyMetricBinding>["columns"] = [
    { title: "节点代码", dataIndex: "nodeCode" },
    { title: "指标编码", dataIndex: "metricCode" },
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
      render: (_value, binding) => outcomeRefText(binding),
    },
    { title: "指标编码", dataIndex: "indicatorCode" },
    {
      title: "包版本",
      dataIndex: "packageVersion",
      render: (value?: string | null) => value ?? "-",
    },
  ];

  const inheritanceDiffColumns: TableProps<PathwayTemplateInheritanceDiffItem>["columns"] = [
    { title: "对象", dataIndex: "itemCode", render: (code: string) => <Tag>{code}</Tag> },
    {
      title: "类型",
      dataIndex: "changeType",
      render: (type: PathwayInheritanceChangeType) => {
        const color = inheritanceChangeColor(type);
        return <Tag color={color}>{inheritanceChangeText(type)}</Tag>;
      },
    },
    { title: "字段", dataIndex: "fieldName", render: (value?: string | null) => value ?? "-" },
    { title: "父级值", dataIndex: "parentValue", render: (value?: string | null) => value ?? "-" },
    { title: "当前值", dataIndex: "childValue", render: (value?: string | null) => value ?? "-" },
  ];

  const mergedNodeColumns: TableProps<PathwayMergedNode>["columns"] = [
    { title: "节点代码", dataIndex: "nodeCode", render: (code: string) => <Tag>{code}</Tag> },
    { title: "名称", dataIndex: "name", className: styles.textStrong },
    { title: "类型", dataIndex: "nodeType" },
    {
      title: "来源",
      dataIndex: "origin",
      render: (origin: string) => {
        const color = inheritanceOriginColor(origin);
        return <Tag color={color}>{inheritanceOriginText(origin)}</Tag>;
      },
    },
    {
      title: "时窗",
      dataIndex: "timeWindowMinutes",
      render: (minutes?: number | null) => (typeof minutes === "number" ? minutes : "-"),
    },
    {
      title: "终止",
      dataIndex: "terminalFlag",
      render: (terminal?: boolean | null) => (terminal ? "是" : "否"),
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
          <Descriptions.Item label="已选快照">
            {selectedSnapshotDetail.snapshotId}
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            {customerEnumLabel(selectedSnapshotDetail.status)}
          </Descriptions.Item>
          <Descriptions.Item label="质量">
            {customerDisplayText(selectedSnapshotDetail.qualityStatus)}
          </Descriptions.Item>
          <Descriptions.Item label="路径包版本">
            {selectedSnapshotDetail.packageVersion || createTemplatePackageVersion || "-"}
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
          <Descriptions.Item label="选中路径边">{result.selectedEdgeCode || "-"}</Descriptions.Item>
          <Descriptions.Item label="节点轨迹">
            {result.nodeTrajectory?.join(" → ") || "-"}
          </Descriptions.Item>
          <Descriptions.Item label="Trace">{result.traceId || "-"}</Descriptions.Item>
        </Descriptions>
        {renderPreviewRunEvidence(result.conditionEvidence ?? [])}
      </Space>
    );
  };

  const createLayerItems: TabsProps["items"] = [
    {
      key: "l1",
      label: "基础模板",
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
            <Col xs={24} md={12}>
              <Form.Item name="packageId" label="归属路径知识包" rules={[{ required: true }]}>
                <Select
                  showSearch
                  filterOption={false}
                  onSearch={setPackageSearch}
                  placeholder="选择路径知识包"
                >
                  {packagesData?.items?.map((pkg: KnowledgePackage) => (
                    <Option key={pkg.packageId} value={pkg.packageId}>
                      {pkg.name} (v{pkg.packageVersion})
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="name" label="路径模型名称" rules={[{ required: true }]}>
                <Input placeholder="如 心血管路径复核" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item
                name="templateCode"
                label="路径模型代码"
                tooltip="稳定业务编码，发布后用于包内引用与版本追踪"
                rules={[{ required: true }]}
              >
                <Input placeholder="如 PATH.CARDIO.REVIEW" />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item
                name="diseaseCode"
                label="病种代码"
                tooltip="填写真实病种或诊断分组编码，不写临时中文别名"
                rules={[{ required: true }]}
              >
                <Input placeholder="如 CARDIO 或 ICD10-I63" />
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
                name="templateVersion"
                label="模板版本号"
                tooltip="同一路径模型代码下递增，用于发布、回滚和影响分析"
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
            <Col xs={24} sm={12} lg={6}>
              <Form.Item name="parentTemplateId" label="父级模板">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="不继承"
                  options={parentTemplateOptions}
                />
              </Form.Item>
            </Col>
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
                  同步到 DSL
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
                message="字段目录暂不可用，路径条件不能同步到 DSL。"
                description="路径纳入、排除和流转条件必须绑定 canonical 字段目录；恢复字段目录接口后再同步或保存。"
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
                            label="阶段编码"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 PREOP" />
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
                            label="里程碑编码"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 M-PREOP-ASSESS" />
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
                        <Col xs={24} sm={12} lg={currentScope === "TEMPLATE" ? 10 : 6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "indicatorCode"]}
                            label="评估指标"
                            rules={[{ required: true }]}
                          >
                            <Select
                              showSearch
                              allowClear
                              filterOption={false}
                              placeholder="选择 ACTIVE 评估指标"
                              options={outcomeIndicatorOptions}
                              onSearch={setOutcomeIndicatorSearch}
                              onClear={() => setOutcomeIndicatorSearch("")}
                              onChange={(indicatorCode) => {
                                const indicator = outcomeIndicatorByCode.get(indicatorCode);
                                if (indicator?.packageVersion) {
                                  templateForm.setFieldValue(
                                    ["outcomeBindings", field.name, "packageVersion"],
                                    indicator.packageVersion,
                                  );
                                }
                              }}
                              notFoundContent="暂无 ACTIVE 评估指标"
                            />
                          </Form.Item>
                        </Col>
                        <Col xs={24} sm={12} lg={currentScope === "TEMPLATE" ? 8 : 4}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "packageVersion"]}
                            label="指标包版本"
                            rules={[{ required: true, message: "请选择评估指标所属包版本" }]}
                          >
                            <Select
                              showSearch
                              allowClear
                              loading={evaluationPackagesLoading}
                              filterOption={false}
                              options={outcomeIndicatorPackageOptions}
                              onSearch={setOutcomePackageSearch}
                              onClear={() => setOutcomePackageSearch("")}
                              placeholder="选择评估指标所属配置包版本"
                              notFoundContent={
                                evaluationPackagesError
                                  ? "评估指标包版本读取失败"
                                  : "暂无评估指标包版本"
                              }
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
                            label="节点编码"
                            tooltip="新增时自动生成（N1/N2…），可改；用于边连接与起点引用"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 N1，可改为 ASSESS" />
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
                            <Input placeholder="如 专科医生" />
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
                            label="时钟指标编码"
                            tooltip="设置时窗分钟后必填，用于时窗门禁与质控时钟"
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
                                      new Error("已设置时窗，请填写时钟指标编码"),
                                    );
                                  }
                                  return Promise.resolve();
                                },
                              }),
                            ]}
                          >
                            <Input placeholder="如 PATH.TIME.ASSESS" />
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
                              label="SLA基准"
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
                      {["ORDER_SET", "SUBPATHWAY", "WAIT_TIMER"].includes(
                        currentNodeType ?? "",
                      ) && (
                        <Row gutter={12}>
                          {currentNodeType === "ORDER_SET" && (
                            <Col xs={24} sm={12} lg={8}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "config", "orderSetRef"]}
                                label="医嘱集引用"
                                rules={[{ required: true, message: "请填写医嘱集引用" }]}
                              >
                                <Input placeholder="如 sepsis-order-set" />
                              </Form.Item>
                            </Col>
                          )}
                          {currentNodeType === "SUBPATHWAY" && (
                            <Col xs={24} sm={12} lg={8}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "config", "subPathwayRef"]}
                                label="子路径引用"
                                rules={[{ required: true, message: "请填写子路径引用" }]}
                              >
                                <Input placeholder="如 icu-transfer" />
                              </Form.Item>
                            </Col>
                          )}
                          {currentNodeType === "WAIT_TIMER" && (
                            <Col xs={24} sm={12} lg={8}>
                              <Form.Item
                                {...fieldProps}
                                name={[field.name, "config", "clock"]}
                                label="计时 clock"
                              >
                                <Input placeholder="如 AFTER_24H" />
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
                      responsibleRole: "专科医生",
                      accountableRole: "专科医生",
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
                  const selectedEdgeFragment =
                    typeof edgeValue?.conditionFragmentId === "string"
                      ? conditionFragmentById.get(edgeValue.conditionFragmentId)
                      : undefined;
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
                            label="边编码"
                            tooltip="新增时自动生成（E1/E2…），可改"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="如 E1，可改为 EDGE.ASSESS.FOLLOWUP" />
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
                        <Col xs={24} sm={12} lg={5}>
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
                        <Col xs={24} sm={12} lg={4}>
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
                        <Col xs={24} sm={12} lg={3}>
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
                      <div className={styles.conditionCard}>
                        <div className={styles.conditionHeader}>
                          <Space direction="vertical" size={0}>
                            <Text strong>条件片段</Text>
                            <Text type="secondary">
                              使用同包版本片段作为路径守卫，可引用联动或拷贝后细调。
                            </Text>
                          </Space>
                          <Tag color={conditionFragmentPackageVersion ? "green" : "default"}>
                            {conditionFragmentPackageVersion || "待选择路径知识包"}
                          </Tag>
                        </div>
                        <Space wrap className="mk-full-width">
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "conditionFragmentId"]}
                            className={styles.zeroBottom}
                          >
                            <Select
                              aria-label="选择条件片段"
                              showSearch
                              allowClear
                              loading={activeConditionFragmentsQuery.isLoading}
                              disabled={
                                !conditionFragmentPackageVersion ||
                                activeConditionFragmentsQuery.isError
                              }
                              options={conditionFragmentOptions}
                              optionFilterProp="label"
                              placeholder={
                                conditionFragmentPackageVersion
                                  ? "选择可复用条件片段"
                                  : "先选择路径知识包"
                              }
                              className={styles.fragmentSelect}
                            />
                          </Form.Item>
                          <Button
                            icon={<SwapOutlined />}
                            disabled={!selectedEdgeFragment}
                            onClick={() => applyEdgeConditionFragment(field.name, "reference")}
                          >
                            引用
                          </Button>
                          <Button
                            icon={<PlusOutlined />}
                            disabled={!selectedEdgeFragment}
                            onClick={() => applyEdgeConditionFragment(field.name, "copy")}
                          >
                            拷贝
                          </Button>
                        </Space>
                        {activeConditionFragmentsQuery.isError && (
                          <Alert
                            className={styles.marginTopSm}
                            type="error"
                            showIcon
                            message="条件片段暂不可用"
                            description={getApiErrorMessage(
                              activeConditionFragmentsQuery.error,
                              "条件片段列表加载失败，请稍后重试。",
                            )}
                          />
                        )}
                        {conditionFragmentPackageVersion &&
                          !activeConditionFragmentsQuery.isLoading &&
                          !activeConditionFragmentsQuery.isError &&
                          conditionFragmentOptions.length === 0 && (
                            <Empty
                              className={styles.marginTopSm}
                              image={Empty.PRESENTED_IMAGE_SIMPLE}
                              description="当前包版本暂无可用条件片段"
                            />
                          )}
                      </div>
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
                <Form.Item label="患者 ID" htmlFor="pathway-create-snapshot-patient-id">
                  <Input
                    id="pathway-create-snapshot-patient-id"
                    value={snapshotPatientId}
                    onChange={(event) => setSnapshotPatientId(event.target.value)}
                  />
                </Form.Item>
                <Form.Item label="就诊 ID" htmlFor="pathway-create-snapshot-encounter-id">
                  <Input
                    id="pathway-create-snapshot-encounter-id"
                    value={snapshotEncounterId}
                    onChange={(event) => setSnapshotEncounterId(event.target.value)}
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
                    {snapshotList.map((snapshot: ContextSnapshotSummary) => (
                      <Button
                        key={snapshot.snapshotId}
                        type={selectedSnapshotId === snapshot.snapshotId ? "primary" : "default"}
                        onClick={() => {
                          setSelectedSnapshotId(snapshot.snapshotId);
                          setCreatePreviewRunResult(null);
                        }}
                        className={styles.snapshotButton}
                      >
                        <span>{snapshot.snapshotId}</span>
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
                      snapshotQuery
                        ? "未读取到 ACTIVE 快照"
                        : "请输入患者 ID 或就诊 ID 读取真实快照"
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
    ...(createExpertMode
      ? [
          {
            key: "l3",
            label: "L3 DSL",
            children: (
              <div className={styles.editorSection}>
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <Space className="mk-flex-between mk-full-width">
                    <div className={styles.textStrong}>路径 DSL JSON</div>
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
                    message="L3 是受控 DSL 编辑层，普通路径配置请优先使用 L2 节点画布。"
                  />
                  <Form.Item
                    label="路径 DSL JSON"
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
                        packageVersion={createTemplatePackageVersion}
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
  const releaseImpact: PathwayTemplateImpactResponse | null = impactQuery.data ?? null;
  const releaseEvidenceItems = releaseImpact?.releaseEvidence ?? [];
  const rollbackTargetOptions =
    rollbackTargetsData?.items?.filter(
      (item: PathwayTemplate) =>
        item.templateId !== selectedTemplateId &&
        item.templateCode === detailData?.template.templateCode &&
        item.status === "OFFLINE",
    ) ?? [];
  const activeDeployment = detailData?.deploymentStatus === "PUBLISHED";
  const reviewedContent = detailData?.template.status === "PUBLISHED";
  const releaseCurrentStep = activeDeployment || reviewedContent ? "full_rollout" : "submit_review";
  let releaseFlowStatus: "process" | "finish" | "error" = "error";
  if (activeDeployment) {
    releaseFlowStatus = "finish";
  } else if (releaseImpact?.impactDigest) {
    releaseFlowStatus = "process";
  }
  let detailAlertMessage =
    "当前临床路径处于设计中，可核查三层模型并使用真实上下文快照试运行后申请发布。";
  let detailAlertType: "success" | "warning" | "info" = "info";
  if (activeDeployment) {
    detailAlertMessage = "当前路径版本已全量生效，拓扑结构写保护；修改需创建新版本。";
    detailAlertType = "success";
  } else if (reviewedContent) {
    detailAlertMessage = "路径内容已审核并完成灰度，需由医院管理员确认后全量激活。";
    detailAlertType = "warning";
  }
  let releaseImpactSummary = <Alert type="warning" showIcon message="尚未返回路径发布影响摘要。" />;
  if (impactQuery.isLoading) {
    releaseImpactSummary = <Alert type="info" showIcon message="正在读取路径发布影响摘要..." />;
  } else if (impactQuery.isError) {
    releaseImpactSummary = (
      <Alert
        type="error"
        showIcon
        message="影响摘要读取失败"
        description="发布门禁需要真实影响摘要，请稍后重试。"
      />
    );
  } else if (releaseImpact) {
    releaseImpactSummary = (
      <Descriptions bordered column={detailDescriptionColumn} size="small">
        <Descriptions.Item label="影响分析状态">
          <Tag color={releaseImpact.analysisStatus === "COMPLETE" ? "green" : "orange"}>
            {releaseImpact.analysisStatus}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="影响摘要">{releaseImpact.impactDigest}</Descriptions.Item>
        <Descriptions.Item label="关联患者路径">
          {releaseImpact.affectedPatientPathways}
        </Descriptions.Item>
        <Descriptions.Item label="拓扑规模">
          {releaseImpact.nodeCount} 节点 / {releaseImpact.edgeCount} 边
        </Descriptions.Item>
        <Descriptions.Item label="关键时钟节点">{releaseImpact.timedNodeCount}</Descriptions.Item>
        <Descriptions.Item label="终止节点">{releaseImpact.terminalNodeCount}</Descriptions.Item>
        <Descriptions.Item label="结局指标绑定">
          {releaseImpact.outcomeBindingCount ?? 0}
        </Descriptions.Item>
        <Descriptions.Item label="灰度比例">灰度发布默认 10%</Descriptions.Item>
        <Descriptions.Item label="回滚凭据">保留本次 impactDigest</Descriptions.Item>
      </Descriptions>
    );
  }

  const releaseStepPanel = (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Alert
        type={activeDeployment ? "success" : "info"}
        showIcon
        message={
          activeDeployment
            ? "当前路径版本已全量生效"
            : "路径发布将先进入 10% 灰度，并保留回滚证据。"
        }
        description={
          activeDeployment
            ? "运行态来自统一版本底座；仍可基于影响摘要和审计证据执行受控回滚。"
            : "全量发布必须基于本次影响摘要和审计记录继续推进，不能跳过影响核查。"
        }
      />
      {releaseImpactSummary}
      {releaseEvidenceItems.length > 0 && (
        <Timeline
          items={releaseEvidenceItems.map((evidence, index) => ({
            key: `${index}-${evidence}`,
            color: index === releaseEvidenceItems.length - 1 ? "green" : "blue",
            children: evidence,
          }))}
        />
      )}
      <Form layout="vertical">
        <Form.Item label="发布审核说明" htmlFor="pathway-release-reason">
          <TextArea
            id="pathway-release-reason"
            rows={3}
            value={releaseReason}
            onChange={(event) => setReleaseReason(event.target.value)}
            placeholder="填写已核查影响摘要、灰度范围、随访交接和回滚安排。"
          />
        </Form.Item>
        {detailData?.template.status === "DRAFT" ? (
          <Button
            type="primary"
            icon={<CheckCircleOutlined />}
            onClick={handlePublishTemplate}
            loading={publishTemplateMutation.isPending}
            disabled={!releaseImpact?.impactDigest || !cleanText(releaseReason)}
          >
            提交审核并进入灰度发布
          </Button>
        ) : (
          <Space direction="vertical" size="small" className="mk-full-width">
            {!activeDeployment && (
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={handleFullRolloutTemplate}
                loading={fullRolloutMutation.isPending}
                disabled={!releaseImpact?.impactDigest || !cleanText(releaseReason)}
              >
                院级确认全量激活
              </Button>
            )}
            <Form.Item label="回滚目标版本" className={styles.zeroBottom}>
              <Select
                aria-label="回滚目标版本"
                placeholder="选择已下线历史版本"
                value={rollbackTargetTemplateId}
                onChange={setRollbackTargetTemplateId}
                options={rollbackTargetOptions.map((item: PathwayTemplate) => ({
                  value: item.templateId,
                  label: `${item.templateCode} v${item.templateVersion}.0`,
                }))}
              />
            </Form.Item>
            <Button
              danger
              onClick={handleRollbackTemplate}
              loading={rollbackMutation.isPending}
              disabled={
                !releaseImpact?.impactDigest ||
                !cleanText(releaseReason) ||
                !rollbackTargetTemplateId
              }
            >
              回滚到目标版本
            </Button>
          </Space>
        )}
      </Form>
    </Space>
  );

  const inheritanceDiff = inheritanceDiffQuery.data;
  const inheritanceSummaryMessage = inheritanceDiff?.parentTemplateId
    ? `父级模板：${inheritanceDiff.parentTemplateId}`
    : "未继承父级模板";
  const inheritancePanelContent = (() => {
    if (inheritanceDiffQuery.isLoading) {
      return <Alert type="info" showIcon message="正在读取继承差异..." />;
    }
    if (inheritanceDiffQuery.isError) {
      return <Alert type="error" showIcon message="继承差异读取失败" />;
    }
    return (
      <>
        <Alert
          type={inheritanceDiff?.parentTemplateId ? "info" : "success"}
          showIcon
          message={inheritanceSummaryMessage}
        />
        <Table
          title={() => "差异项"}
          dataSource={inheritanceDiff?.diffItems ?? []}
          rowKey={(item) =>
            `${item.itemType}-${item.itemCode}-${item.changeType}-${item.fieldName ?? ""}`
          }
          pagination={false}
          size="small"
          columns={inheritanceDiffColumns}
          locale={{ emptyText: "暂无差异" }}
          className="medkernel-table"
        />
        <Table
          title={() => "有效节点"}
          dataSource={inheritanceDiff?.mergedNodes ?? []}
          rowKey="nodeCode"
          pagination={false}
          size="small"
          columns={mergedNodeColumns}
          locale={{ emptyText: "暂无有效节点" }}
          className="medkernel-table"
        />
      </>
    );
  })();
  const inheritancePanel = (
    <Space direction="vertical" size="middle" className={`mk-full-width ${styles.marginTopMd}`}>
      {inheritancePanelContent}
    </Space>
  );

  const detailLayerItems: TabsProps["items"] = detailData
    ? [
        {
          key: "l1",
          label: "基础模板",
          children: (
            <Descriptions bordered column={detailDescriptionColumn} className={styles.marginTopMd}>
              <Descriptions.Item label="名称">{detailData.template.name}</Descriptions.Item>
              <Descriptions.Item label="模板代码">
                {detailData.template.templateCode}
              </Descriptions.Item>
              <Descriptions.Item label="相关病种">
                {detailData.template.diseaseCode}
              </Descriptions.Item>
              <Descriptions.Item label="版本">
                v{detailData.template.templateVersion}.0
              </Descriptions.Item>
              <Descriptions.Item label="层级">
                {customerEnumLabel(detailData.template.templateLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="父级模板">
                {detailData.template.parentTemplateId ?? "无"}
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
                {detailData.template.startNodeCode ?? "未设置"}
              </Descriptions.Item>
              <Descriptions.Item label="入径条件" span={detailDescriptionColumn}>
                <span className={styles.codeText}>
                  {cleanText(detailData.template.entryCriteriaJson) ?? "未配置"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="出径条件" span={detailDescriptionColumn}>
                <span className={styles.codeText}>
                  {cleanText(detailData.template.exitCriteriaJson) ?? "未配置"}
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
          key: "inheritance",
          label: "继承差异",
          children: inheritancePanel,
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
              {detailData.edges.map((edge) => {
                const guard = parseLooseJson(edge.conditionJson);
                if (!guard || typeof guard !== "object" || Array.isArray(guard)) {
                  return null;
                }
                return (
                  <AuthoringReadablePreview
                    key={`edge-preview-${edge.edgeId}`}
                    subject="PATHWAY_GUARD"
                    packageVersion={releasePackageVersion()}
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
        ...(detailExpertMode
          ? [
              {
                key: "l3",
                label: "L3 DSL",
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
                          packageVersion={releasePackageVersion()}
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
                      <Form.Item label="患者 ID" htmlFor="pathway-snapshot-patient-id">
                        <Input
                          id="pathway-snapshot-patient-id"
                          value={snapshotPatientId}
                          onChange={(event) => setSnapshotPatientId(event.target.value)}
                        />
                      </Form.Item>
                      <Form.Item label="就诊 ID" htmlFor="pathway-snapshot-encounter-id">
                        <Input
                          id="pathway-snapshot-encounter-id"
                          value={snapshotEncounterId}
                          onChange={(event) => setSnapshotEncounterId(event.target.value)}
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
                        {snapshotList.map((snapshot: ContextSnapshotSummary) => (
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
                            <span>{snapshot.snapshotId}</span>
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
                          snapshotQuery
                            ? "未读取到 ACTIVE 快照"
                            : "请输入患者 ID 或就诊 ID 读取真实快照"
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
                                  {node.name} ({node.nodeCode})
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
                                options={snapshotList.map((snapshot) => ({
                                  value: snapshot.snapshotId,
                                  label: `${snapshot.snapshotId} / ${customerDisplayText(
                                    snapshot.qualityStatus,
                                  )}`,
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
                          {selectedSnapshotDetail.snapshotId}
                        </Descriptions.Item>
                        <Descriptions.Item label="状态">
                          {customerEnumLabel(selectedSnapshotDetail.status)}
                        </Descriptions.Item>
                        <Descriptions.Item label="质量">
                          {customerDisplayText(selectedSnapshotDetail.qualityStatus)}
                        </Descriptions.Item>
                        <Descriptions.Item label="路径包版本">
                          {selectedSnapshotDetail.packageVersion ?? "未返回"}
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
                              { title: "快照", dataIndex: "snapshotId" },
                              {
                                title: "轨迹",
                                dataIndex: "nodeTrajectory",
                                render: (trajectory: string[]) => trajectory.join(" → "),
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
                                  <div className={styles.timelineMeta}>{nodeCode}</div>
                                </>
                              ),
                            };
                          })}
                        />
                      </Space>
                    ) : (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="选择 ACTIVE 快照后可试运行路径"
                      />
                    )}
                  </div>
                </Space>
              </Col>
            </Row>
          ),
        },
        {
          key: "release",
          label: (
            <span>
              <DeploymentUnitOutlined /> 7 步流发布
            </span>
          ),
          children: (
            <div className={styles.marginTopMd}>
              <StepFlow
                currentStep={releaseCurrentStep}
                panelByStep={
                  releaseCurrentStep === "full_rollout"
                    ? { full_rollout: releaseStepPanel }
                    : { submit_review: releaseStepPanel }
                }
                status={releaseFlowStatus}
              />
            </div>
          ),
        },
      ]
    : [];

  return (
    <PageShell
      title="路径中枢"
      description="配置并维护专病临床路径标准，设定生命周期节点与变异流转边拓扑，提供真实快照试运行与时窗门禁发布验证。"
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
              <Option value="PUBLISHED">内容已审核</Option>
              <Option value="OFFLINE">已下线</Option>
            </Select>
          </Form.Item>
          <Form.Item label="病种编码">
            <Input
              placeholder="输入真实病种编码"
              allowClear
              value={diseaseFilter}
              onChange={(event) => setDiseaseFilter(event.target.value)}
              className={styles.controlSm}
            />
          </Form.Item>
          <Form.Item label="归属路径知识包">
            <Select
              showSearch
              filterOption={false}
              onSearch={setPackageSearch}
              placeholder="全部路径知识包"
              allowClear
              value={packageFilter}
              onChange={setPackageFilter}
              className={styles.controlLg}
            >
              {packagesData?.items?.map((pkg: KnowledgePackage) => (
                <Option key={pkg.packageId} value={pkg.packageId}>
                  {pkg.name} ({pkg.packageVersion})
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item className={styles.toolbarActions}>
            <Button icon={<FolderOpenOutlined />} onClick={() => setPackageDrawerVisible(true)}>
              管理路径知识包
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setSelectedPathwayPrototype("blank");
                resetCreateTemplateDraft();
                resetSimulation();
                setCreateExpertMode(false);
                setCreateTemplateVisible(true);
              }}
            >
              新建路径模板
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
            showTotal: (total) => `共 ${total} 个临床受控路径模型`,
          }}
          className="medkernel-table"
        />
      </div>

      <Drawer
        title="路径知识包"
        width="min(560px, 100vw)"
        onClose={() => setPackageDrawerVisible(false)}
        open={packageDrawerVisible}
        destroyOnClose
      >
        <Card title="新建路径知识包" className={styles.marginBottomLg}>
          <Form form={packageForm} layout="vertical" onFinish={handleCreatePackage}>
            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Form.Item name="packageCode" label="包编码" rules={[{ required: true }]}>
                  <Input placeholder="输入包编码" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="diseaseCode" label="病种代码 (ICD)" rules={[{ required: true }]}>
                  <Input placeholder="输入真实病种代码" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="name" label="名称" rules={[{ required: true }]}>
              <Input placeholder="输入路径知识包名称" />
            </Form.Item>
            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Form.Item name="packageVersion" label="版本" rules={[{ required: true }]}>
                  <Input placeholder="输入版本号" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="sourceRef" label="知识来源" rules={[{ required: true }]}>
                  <Input placeholder="输入已审核指南、院内制度或配置包来源" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="description" label="功能说明与收治摘要">
              <TextArea rows={2} placeholder="输入路径知识包功能说明与收治摘要" />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<PlusOutlined />}
              loading={createPackageMutation.isPending}
              className={`${styles.fullWidth} ${styles.marginTopSm}`}
            >
              创建草稿
            </Button>
          </Form>
        </Card>

        <div className={`${styles.textStrong} ${styles.marginBottomMd}`}>已有路径知识包</div>
        <Space direction="vertical" className={`mk-full-width ${styles.packageList}`}>
          {packagesData?.items?.map((pkg: KnowledgePackage) => (
            <Card key={pkg.packageId} size="small" className={styles.packageCard}>
              <Descriptions size="small" column={1} bordered={false}>
                <Descriptions.Item label="名称">
                  <span className={styles.textStrong}>{pkg.name}</span>
                </Descriptions.Item>
                <Descriptions.Item label="包编码">
                  <span className={styles.codeText}>{pkg.packageCode}</span>
                </Descriptions.Item>
                <Descriptions.Item label="病种/版本">
                  <Tag color="cyan">{pathwayPackageDiseaseCode(pkg)}</Tag>
                  <Tag color="purple">{pkg.packageVersion}</Tag>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          ))}
        </Space>
      </Drawer>

      <Modal
        title="新建路径模板模型"
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
              普通配置只展示 L1/L2；L3 DSL 需显式进入 L3 DSL 编辑模式。
            </span>
            <Space>
              <span>L3 DSL 编辑模式</span>
              <Switch
                aria-label="L3 DSL 编辑模式"
                checked={createExpertMode}
                onChange={toggleCreateExpertMode}
              />
            </Space>
          </Space>
          <Tabs defaultActiveKey="l1" items={createLayerItems} />
        </Form>
      </Modal>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <span>路径配置与真实快照试运行控制台</span>
            {detailData &&
              detailData.deploymentStatus !== "PUBLISHED" &&
              (detailData.template.status === "DRAFT" ||
                detailData.template.status === "PUBLISHED") && (
                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  onClick={() => setDetailActiveTab("release")}
                  className={styles.marginRightLg}
                >
                  进入 7 步流发布
                </Button>
              )}
          </div>
        }
        width="min(1080px, 100vw)"
        onClose={() => {
          setSelectedTemplateId(null);
          setDetailActiveTab("l1");
          setDetailExpertMode(false);
          setReleaseReason("");
          setRollbackTargetTemplateId(undefined);
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
            {activeDeployment && (
              <Space className={`mk-flex-between mk-full-width ${styles.marginBottomMd}`} wrap>
                <span className={`${styles.textSmall} ${styles.textSecondary}`}>
                  维护已全量生效拓扑时先复制为下一版草稿，再走影响预览、灰度发布和回滚证据。
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
                路径拓扑、试运行和发布为普通主流程；完整 DSL 仅在 L3 技术视图显示。
              </span>
              <Space>
                <span>L3 技术视图</span>
                <Switch
                  aria-label="L3 技术视图"
                  checked={detailExpertMode}
                  onChange={toggleDetailExpertMode}
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
