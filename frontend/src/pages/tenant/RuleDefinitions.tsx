import { useMemo, useState, type ReactNode } from "react";
import {
  App as AntdApp,
  Alert,
  AutoComplete,
  Badge,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Steps,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { BadgeProps, RadioChangeEvent, TableProps } from "antd";
import {
  ApartmentOutlined,
  BranchesOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CodeOutlined,
  DeleteOutlined,
  DeploymentUnitOutlined,
  FileSearchOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  PlayCircleOutlined,
  SyncOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useRuleDefinitions,
  useRuleDetail,
  useCreateRule,
  useAddTestCase,
  useRunRuleTests,
  useSimulateRule,
  useSignoffRule,
  useTransitionRuleGovernance,
  useSecurityProfile,
  useContextFieldCatalog,
  useContextSnapshots,
  useContextSnapshotDetail,
  useAuthoringPreviewRun,
  useConditionFragments,
  useCreateConditionFragment,
  useUpdateConditionFragment,
  useConditionFragmentImpact,
  useRuleImpact,
  useRuleShadowStats,
  useRuleBacktestLatest,
  useRunRuleBacktest,
  useRuleDriftLatest,
  useCaptureRuleDriftSnapshot,
  useOrgUnits,
} from "@/shared/api/hooks";
import type {
  OrgUnit,
  RuleDefinition,
  RuleEvaluationItem,
  RuleImpactObject,
  RuleImpactResponse,
  RuleGovernanceState,
  RuleSignoffStage,
  RuleTestCase,
  ContextSnapshotSummary,
  AuthoringPreviewRunEvidence,
  AuthoringPreviewRunResponse,
  ConditionFragmentStatus,
  ConditionFragmentResponse,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { ConditionTreeEditor } from "@/shared/ui/condition/ConditionTreeEditor";
import { AuthoringReadablePreview } from "@/shared/ui/condition/AuthoringReadablePreview";
import { StandardTermValueAutoComplete } from "@/shared/ui/condition/StandardTermValueAutoComplete";
import { buildFieldCatalogOptions } from "@/shared/config/contextFieldOptions";
import { FieldCatalogManager } from "@/shared/ui/condition/FieldCatalogManager";
import {
  createDefaultTree as createPopulationConditionTree,
  dslToRootGroup,
  hasUnresolvedFact as hasUnresolvedPopulationFact,
  nodeToDsl,
  type RuleGroup as PopulationConditionTree,
} from "@/shared/config/conditionModel";
import {
  RULE_OPERATOR_LABELS as OPERATOR_LABELS,
  RULE_VALUE_KIND_LABELS as VALUE_KIND_LABELS,
  DERIVED_FORMULA_OPTIONS,
  DEFAULT_TEMPORAL_MODE,
  RULE_EXPRESSION_SELECT_OPTIONS,
  TEMPORAL_MODE_OPTIONS,
  defaultValueKindForOperator,
  isClinicalRuleOperator,
  normalizeTemporalMode,
  parameterKeysForDerivedFormula,
} from "@/shared/config/ruleOperatorCatalog";
import {
  RULE_LAYER_TEMPLATES,
  DEFAULT_CRITICAL_OBSERVATION_CODE,
  DEFAULT_CRITICAL_RETURN_MINUTES,
  conditionNeedsValue,
  conditionTreeToDsl,
  criticalValueReportDetail,
  createCriticalValueParameterDefinitions,
  createRuleActionDraft,
  createExplanationTemplate,
  dslToConditionNode,
  dslToConditionTree,
  dslWhenToRootGroup,
  flatToRootGroup,
  formatRuleJson,
  instantiateRuleTemplate,
  isConditionGroup,
  parseRuleJson,
  requiresPhysicianConfirmation,
  type RuleActionCode,
  type RuleActionDraft,
  type RuleCondition,
  type RuleConditionGroup,
  type RuleConditionNode,
  type RuleConditionTree,
  type RuleDsl,
  type RuleLogic,
  type RuleIndicator,
  type RuleApplicability,
  type RuleClinicalSetting,
  type RuleOperator,
  type RuleSeverity,
  type RuleTemplateKey,
  type RuleValueKind,
} from "@/shared/config/ruleLayeredEditor";
import {
  MAX_TREE_DEPTH,
  addNodeToGroup,
  createConditionGroup,
  createConditionLeaf,
  mapConditionById,
  mapGroupById,
  removeConditionById,
  rootDepth,
  rootHasUnresolvedFact,
} from "@/shared/config/ruleConditionTreeOps";
import { RULE_TYPE_LABELS, RULE_TYPE_OPTIONS } from "@/shared/config/ruleTypes";
import { RULE_GOVERNANCE_STAGES, ruleGovernanceLabel } from "@/shared/config/ruleGovernance";
import {
  CLINICAL_TRIGGER_POINT_OPTIONS,
  isClinicalTriggerPoint,
  type ClinicalTriggerPoint,
} from "@/shared/config/clinicalTriggerPoints";
import styles from "./RulePathwayAuthoring.module.css";

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;

type RuleStatusBadge = Exclude<BadgeProps["status"], undefined>;
type CreateLayerKey = "l1" | "l2" | "preview" | "l3";
type DetailLayerKey = "l1" | "l2" | "l3" | "cases" | "simulate" | "release";
type ConditionFragmentFormValue = {
  fragmentCode: string;
  fragmentName: string;
  category?: string;
  versionNo: number;
  packageVersion: string;
  status: ConditionFragmentStatus;
};

const DEFAULT_TEMPLATE_KEY: RuleTemplateKey = "clinical_quality_monitor";
const REQUIRED_RELEASE_CASE_TYPES = ["POSITIVE", "NEGATIVE", "BOUNDARY", "CONFLICT"];
const CLINICAL_SETTING_LABELS: Record<RuleClinicalSetting, string> = {
  INPATIENT: "住院",
  OUTPATIENT: "门诊",
  ED: "急诊",
  FOLLOWUP: "随访",
};
const ORG_LEVEL_LABELS: Record<OrgUnit["level"], string> = {
  TENANT: "租户",
  GROUP: "集团",
  HOSPITAL: "医院",
  CAMPUS: "院区",
  SITE: "社区服务点",
  DEPARTMENT: "科室",
};
type RuleOrgLevel = Extract<OrgUnit["level"], "GROUP" | "HOSPITAL" | "DEPARTMENT">;
type OrgSelectOption = { value: string; label: string };

const EMPTY_ORG_SEARCH: Record<RuleOrgLevel, string> = {
  GROUP: "",
  HOSPITAL: "",
  DEPARTMENT: "",
};

const EMPTY_ORG_OPTION_CACHE: Record<RuleOrgLevel, OrgSelectOption[]> = {
  GROUP: [],
  HOSPITAL: [],
  DEPARTMENT: [],
};

const RISK_LABELS: Record<RuleSeverity, string> = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高风险",
  CRITICAL: "红线",
};

function conditionFragmentStatusColor(status: ConditionFragmentStatus) {
  if (status === "ACTIVE") return "green";
  if (status === "DRAFT") return "blue";
  return "default";
}

const numericComparisonChoices = [
  { value: "gt", label: "大于" },
  { value: "gte", label: "大于等于" },
  { value: "lt", label: "小于" },
  { value: "lte", label: "小于等于" },
  { value: "equals", label: "等于" },
];

function isClinicalOperator(operator: RuleOperator) {
  return isClinicalRuleOperator(operator);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function formatEvaluationExplanation(value: unknown) {
  if (typeof value === "string" && value.trim()) return value;
  if (value === null || value === undefined || value === "") return "未命中，无动作输出。";
  if (typeof value === "number" || typeof value === "boolean") return String(value);

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return "解释内容无法序列化。";
  }
}

function conditionValueRecord(condition: RuleCondition) {
  return isRecord(condition.value) ? condition.value : {};
}

function conditionValueString(condition: RuleCondition, key: string) {
  const value = conditionValueRecord(condition)[key];
  return value === undefined || value === null ? "" : String(value);
}

function conditionValueNumber(condition: RuleCondition, key: string) {
  const value = conditionValueRecord(condition)[key];
  return typeof value === "number" ? value : undefined;
}

function conditionValueBoolean(condition: RuleCondition, key: string, fallback = true) {
  const value = conditionValueRecord(condition)[key];
  return typeof value === "boolean" ? value : fallback;
}

function defaultValueForOperator(operator: RuleOperator, currentValue: RuleCondition["value"]) {
  switch (operator) {
    case "between":
    case "not_between":
      if (isRecord(currentValue) && ("min" in currentValue || "max" in currentValue)) {
        return currentValue;
      }
      return { min: "", max: "", includeMin: true, includeMax: true, unit: "" };
    case "unit_compare":
      if (isRecord(currentValue) && ("analyte" in currentValue || "comparison" in currentValue)) {
        return currentValue;
      }
      return { comparison: "gte", value: "", unit: "", analyte: "" };
    case "temporal":
      if (isRecord(currentValue) && ("mode" in currentValue || "window" in currentValue)) {
        return currentValue;
      }
      return {
        mode: DEFAULT_TEMPORAL_MODE,
        window: "PT24H",
        referenceTime: "",
        count: 2,
        condition: { operator: "gt", value: "", unit: "" },
      };
    case "is_critical":
      if (isRecord(currentValue) && "criticalValues" in currentValue) {
        return currentValue;
      }
      return { criticalValues: [] };
    case "is_stale":
      if (isRecord(currentValue) && ("maxAge" in currentValue || "referenceTime" in currentValue)) {
        return currentValue;
      }
      return { maxAge: "PT24H", referenceTime: "" };
    case "derived":
      if (isRecord(currentValue) && ("formula" in currentValue || "parameters" in currentValue)) {
        return currentValue;
      }
      return {
        formula: "CKD_EPI_2021_EGFR",
        comparison: "gte",
        value: "",
        unit: "mL/min/1.73m2",
        parameters: {
          creatinine: "",
          age: "",
          sex: "",
        },
      };
    default:
      return isRecord(currentValue) ? "" : (currentValue ?? "");
  }
}

function parseJsonInput(value: string, errorMessage: string, onError: (message: string) => void) {
  try {
    return parseRuleJson(value);
  } catch {
    onError(errorMessage);
    return null;
  }
}

function parseStoredJson(value?: string | null) {
  if (!value) return null;
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return null;
  }
}

function toStoredConditionTree(value?: string | null) {
  const parsed = parseStoredJson(value);
  if (!parsed) return null;
  try {
    return dslToConditionTree(parsed);
  } catch {
    return null;
  }
}

function toPopulationConditionTree(
  value?: Record<string, unknown>,
): PopulationConditionTree | null {
  if (!value) return null;
  try {
    return dslToRootGroup(value);
  } catch {
    return null;
  }
}

function populationConditionTreeToDsl(tree: PopulationConditionTree): Record<string, unknown> {
  const dsl = nodeToDsl(tree);
  if (!isRecord(dsl)) {
    throw new Error("适用人群条件必须序列化为对象");
  }
  return dsl;
}

function findTemplate(key: RuleTemplateKey) {
  return RULE_LAYER_TEMPLATES.find((item) => item.key === key) ?? RULE_LAYER_TEMPLATES[0];
}

/** 确保条件树带稳定的递归根组（root 的 id 在编辑期保持不变，避免增删按钮丢失目标组）。 */
function withStableRoot(tree: RuleConditionTree): RuleConditionTree {
  return tree.root ? tree : { ...tree, root: flatToRootGroup(tree) };
}

function createDefaultTree() {
  return withStableRoot(instantiateRuleTemplate(DEFAULT_TEMPLATE_KEY));
}

function createDefaultDslText() {
  return formatRuleJson(conditionTreeToDsl(createDefaultTree()));
}

function criticalObservationWhere(observationCode: string): Record<string, unknown> | undefined {
  const trimmed = observationCode.trim();
  if (!trimmed) return undefined;
  return {
    all: [
      {
        expr: { field: "observations[].code" },
        operator: "equals",
        value: { const: trimmed },
      },
    ],
  };
}

function renderRiskTag(level: string) {
  const colors: Record<string, string> = {
    LOW: "green",
    MEDIUM: "orange",
    HIGH: "red",
    CRITICAL: "magenta",
  };
  return (
    <Tag color={colors[level] ?? "default"}>{RISK_LABELS[level as RuleSeverity] ?? level}</Tag>
  );
}

function renderStatus(status: string) {
  const statusMap: Record<string, { text: string; status: RuleStatusBadge }> = {
    DRAFT: { text: "草稿设计中", status: "warning" },
    PUBLISHED: { text: "内容已审核", status: "processing" },
    OFFLINE: { text: "已下线封存", status: "default" },
    ARCHIVED: { text: "已归档历史", status: "default" },
  };
  const config = statusMap[status] || { text: status, status: "processing" };
  return <Badge status={config.status} text={config.text} />;
}

function renderDeploymentStatus(status: string) {
  const statusMap: Record<string, { text: string; status: RuleStatusBadge }> = {
    DRAFT: { text: "待提交", status: "warning" },
    IN_REVIEW: { text: "审核中", status: "processing" },
    APPROVED: { text: "已批准待发布", status: "processing" },
    PUBLISHED: { text: "运行中", status: "success" },
    DEPRECATED: { text: "已弃用", status: "default" },
    RETIRED: { text: "已退役", status: "default" },
  };
  const config = statusMap[status] || { text: status, status: "processing" };
  return <Badge status={config.status} text={config.text} />;
}

function conditionValueText(condition: RuleCondition) {
  if (!conditionNeedsValue(condition.operator)) return "不需要比较值";
  if (Array.isArray(condition.value)) return condition.value.join("、") || "待填写";
  if (isRecord(condition.value)) {
    return "结构化临床算子参数";
  }
  if (condition.value === undefined || condition.value === null || condition.value === "") {
    return "待填写";
  }
  return String(condition.value);
}

function valueKindForOperator(operator: RuleOperator, currentKind: RuleValueKind): RuleValueKind {
  return defaultValueKindForOperator(operator, currentKind);
}

function valueForOperator(operator: RuleOperator, currentValue: RuleCondition["value"]) {
  if (!conditionNeedsValue(operator)) return undefined;
  return defaultValueForOperator(operator, currentValue);
}

function releaseCaseSummary(testCases: RuleTestCase[]) {
  const caseTypes = new Set(testCases.map((item) => item.caseType));
  const missingTypes = REQUIRED_RELEASE_CASE_TYPES.filter((caseType) => !caseTypes.has(caseType));
  const allPassed =
    testCases.length > 0 &&
    missingTypes.length === 0 &&
    testCases.every((item) => item.lastStatus === "PASS");
  return { missingTypes, allPassed };
}

function impactCount(list?: RuleImpactObject[]) {
  return list?.length ?? 0;
}

function releaseImpactStatus(impact?: RuleImpactResponse | null) {
  if (!impact) return "未读取";
  if (impact.analysisStatus === "COMPLETE") return "已完成真实影响分析";
  if (impact.analysisStatus === "PARTIAL") return "部分影响已确认";
  return impact.analysisStatus;
}

function formatShadowRate(rate?: number | null) {
  if (typeof rate !== "number" || !Number.isFinite(rate)) return "0.0%";
  return `${(rate * 100).toFixed(1)}%`;
}

function formatMetricRate(rate?: number | null) {
  if (typeof rate !== "number" || !Number.isFinite(rate)) return "-";
  return `${(rate * 100).toFixed(1)}%`;
}

function formatSignedRate(rate?: number | null) {
  if (typeof rate !== "number" || !Number.isFinite(rate)) return "-";
  const sign = rate > 0 ? "+" : "";
  return `${sign}${(rate * 100).toFixed(1)}%`;
}

function formatDateTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { hour12: false });
}

function renderDriftStatus(status?: string | null) {
  if (status === "WARNING") return <Tag color="red">告警</Tag>;
  if (status === "STABLE") return <Tag color="green">稳定</Tag>;
  return <Tag>未监测</Tag>;
}

export default function RuleDefinitions() {
  const { message, modal } = AntdApp.useApp();
  const securityQuery = useSecurityProfile();
  const permissionCodes = useMemo(
    () => new Set(securityQuery.data?.permissions.map((permission) => permission.code) ?? []),
    [securityQuery.data?.permissions],
  );
  const roleCodes = useMemo(
    () => new Set(securityQuery.data?.roles.map((role) => role.code) ?? []),
    [securityQuery.data?.roles],
  );
  const canWriteRule = permissionCodes.has("rule.write");
  const canPublishRule = permissionCodes.has("rule.publish");
  const canSignRule =
    (canWriteRule || canPublishRule || permissionCodes.has("evaluation.publish")) &&
    ["medical-affairs", "qa-manager", "insurance-manager", "dept-head", "specialist"].some((role) =>
      roleCodes.has(role),
    );
  const canCoordinateRelease =
    canPublishRule && (roleCodes.has("medical-affairs") || roleCodes.has("hospital-admin"));
  const canActivateFull = canPublishRule && roleCodes.has("hospital-admin");
  const [page, setPage] = useState(1);
  const [size] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined);
  const [riskFilter, setRiskFilter] = useState<string | undefined>(undefined);
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null);
  const [activeDetailLayer, setActiveDetailLayer] = useState<DetailLayerKey>("l2");
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [fieldManagerOpen, setFieldManagerOpen] = useState(false);
  const [activeCreateLayer, setActiveCreateLayer] = useState<CreateLayerKey>("l1");
  const [createExpertMode, setCreateExpertMode] = useState(false);
  const [detailExpertMode, setDetailExpertMode] = useState(false);
  const [selectedTemplateKey, setSelectedTemplateKey] =
    useState<RuleTemplateKey>(DEFAULT_TEMPLATE_KEY);
  const [criticalReturnMinutes, setCriticalReturnMinutes] = useState<number>(
    DEFAULT_CRITICAL_RETURN_MINUTES,
  );
  const [criticalObservationCode, setCriticalObservationCode] = useState(
    DEFAULT_CRITICAL_OBSERVATION_CODE,
  );
  const [conditionTree, setConditionTree] = useState<RuleConditionTree>(createDefaultTree);
  const [populationIncludeTree, setPopulationIncludeTree] =
    useState<PopulationConditionTree | null>(null);
  const [populationExcludeTree, setPopulationExcludeTree] =
    useState<PopulationConditionTree | null>(null);
  const [orgSearch, setOrgSearch] = useState<Record<RuleOrgLevel, string>>(EMPTY_ORG_SEARCH);
  const [selectedOrgOptions, setSelectedOrgOptions] =
    useState<Record<RuleOrgLevel, OrgSelectOption[]>>(EMPTY_ORG_OPTION_CACHE);
  const [dslEditorValue, setDslEditorValue] = useState(createDefaultDslText);
  const [createForm] = Form.useForm();
  const createPackageVersion = Form.useWatch("packageVersion", createForm);
  const currentCreatePackageVersion =
    typeof createPackageVersion === "string" ? createPackageVersion.trim() : "";
  const [selectedFragmentId, setSelectedFragmentId] = useState<string | undefined>();
  const [fragmentLibraryOpen, setFragmentLibraryOpen] = useState(false);
  const [editingFragmentId, setEditingFragmentId] = useState<string | null>(null);
  const [fragmentBodyJson, setFragmentBodyJson] = useState<unknown | null>(null);
  const [impactFragmentId, setImpactFragmentId] = useState("");
  const [fragmentForm] = Form.useForm<ConditionFragmentFormValue>();
  const [caseModalVisible, setCaseModalVisible] = useState(false);
  const [caseForm] = Form.useForm();
  const caseExpectedHit = Form.useWatch("expectedHit", caseForm) ?? true;
  const [simulateResult, setSimulateResult] = useState<RuleEvaluationItem | null>(null);
  const [createPreviewRunResult, setCreatePreviewRunResult] =
    useState<AuthoringPreviewRunResponse | null>(null);
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [snapshotSearchParams, setSnapshotSearchParams] = useState<{
    patientId?: string;
    encounterId?: string;
  } | null>(null);
  const [selectedSnapshotId, setSelectedSnapshotId] = useState("");
  const [releaseReason, setReleaseReason] = useState("");
  const [driftWindowDays, setDriftWindowDays] = useState(7);
  const [driftThreshold, setDriftThreshold] = useState(0.1);

  const {
    data: listData,
    isLoading: listLoading,
    isError: listIsError,
    error: listError,
    refetch: refetchList,
  } = useRuleDefinitions({
    page,
    size,
    status: statusFilter,
    ruleType: typeFilter,
    riskLevel: riskFilter,
  });
  const groupOrgUnitsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: orgSearch.GROUP || undefined,
    level: "GROUP",
    status: "ACTIVE",
  });
  const hospitalOrgUnitsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: orgSearch.HOSPITAL || undefined,
    level: "HOSPITAL",
    status: "ACTIVE",
  });
  const departmentOrgUnitsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: orgSearch.DEPARTMENT || undefined,
    level: "DEPARTMENT",
    status: "ACTIVE",
  });
  const activeConditionFragmentsQuery = useConditionFragments(
    {
      status: "ACTIVE",
      packageVersion: currentCreatePackageVersion,
      page: 1,
      size: 50,
    },
    { enabled: createModalVisible && Boolean(currentCreatePackageVersion) },
  );
  const activeConditionFragments = useMemo(
    () => activeConditionFragmentsQuery.data?.items ?? [],
    [activeConditionFragmentsQuery.data?.items],
  );
  const fragmentById = useMemo(
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
  const selectedConditionFragment = selectedFragmentId
    ? fragmentById.get(selectedFragmentId)
    : undefined;
  const fragmentLibraryQuery = useConditionFragments(
    {
      packageVersion: currentCreatePackageVersion || undefined,
      page: 1,
      size: 100,
      sort: "fragmentCode,asc",
    },
    { enabled: fragmentLibraryOpen },
  );
  const fragmentLibraryItems = fragmentLibraryQuery.data?.items ?? [];

  const {
    data: detailData,
    isLoading: detailLoading,
    refetch: refetchDetail,
  } = useRuleDetail(selectedRuleId || "");
  const selectedRulePackageVersion = detailData?.definition.packageVersion ?? "";
  const detailDsl = useMemo(
    () => parseStoredJson(detailData?.version?.dslJson),
    [detailData?.version?.dslJson],
  );
  const detailTree = useMemo(
    () => toStoredConditionTree(detailData?.version?.dslJson),
    [detailData?.version?.dslJson],
  );
  const detailRoot = useMemo(() => {
    const parsed = parseStoredJson(detailData?.version?.dslJson);
    if (!isRecord(parsed) || !("when" in parsed)) return null;
    try {
      return dslWhenToRootGroup((parsed as { when: unknown }).when);
    } catch {
      return null;
    }
  }, [detailData?.version?.dslJson]);
  const detailExplanation = useMemo(
    () => parseStoredJson(detailData?.version?.explanationJson),
    [detailData?.version?.explanationJson],
  );

  const createRuleMutation = useCreateRule();
  const addTestCaseMutation = useAddTestCase(selectedRuleId || "");
  const runRuleTestsMutation = useRunRuleTests(selectedRuleId || "");
  const simulateMutation = useSimulateRule(selectedRuleId || "");
  const signoffMutation = useSignoffRule();
  const governanceTransitionMutation = useTransitionRuleGovernance();
  const previewRunMutation = useAuthoringPreviewRun();
  const createConditionFragmentMutation = useCreateConditionFragment();
  const updateConditionFragmentMutation = useUpdateConditionFragment();
  const conditionFragmentImpactQuery = useConditionFragmentImpact(impactFragmentId, {
    enabled: Boolean(impactFragmentId),
  });
  const runBacktestMutation = useRunRuleBacktest();
  const captureDriftMutation = useCaptureRuleDriftSnapshot();
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: snapshotSearchParams?.patientId,
      encounterId: snapshotSearchParams?.encounterId,
      status: "ACTIVE",
      page: 1,
      size: 20,
    },
    {
      enabled: Boolean(
        (selectedRuleId || createModalVisible || caseModalVisible) && snapshotSearchParams,
      ),
    },
  );
  const snapshotDetailQuery = useContextSnapshotDetail(selectedSnapshotId, {
    enabled: Boolean(
      (selectedRuleId || createModalVisible || caseModalVisible) && selectedSnapshotId,
    ),
  });
  const impactQuery = useRuleImpact(selectedRuleId || "", {
    enabled: Boolean(
      selectedRuleId &&
        detailData?.governance.state &&
        ["DRAFT", "COMMITTEE", "SHADOW", "CANARY"].includes(detailData.governance.state),
    ),
  });
  const shadowStatsQuery = useRuleShadowStats(selectedRuleId || "", {
    enabled: Boolean(selectedRuleId && detailData?.governance.state === "SHADOW"),
  });
  const backtestQuery = useRuleBacktestLatest(selectedRuleId || "", {
    enabled: Boolean(selectedRuleId),
  });
  const driftQuery = useRuleDriftLatest(selectedRuleId || "", {
    enabled: Boolean(selectedRuleId),
  });
  const snapshots = snapshotsQuery.data?.items ?? [];
  const releaseGate = useMemo(
    () => releaseCaseSummary(detailData?.testCases ?? []),
    [detailData?.testCases],
  );

  const resetLayeredAuthoring = (templateKey: RuleTemplateKey = DEFAULT_TEMPLATE_KEY) => {
    const template = findTemplate(templateKey);
    const nextTree = withStableRoot(instantiateRuleTemplate(templateKey));
    setSelectedTemplateKey(templateKey);
    setConditionTree(nextTree);
    setPopulationIncludeTree(toPopulationConditionTree(nextTree.applicability.population.include));
    setPopulationExcludeTree(toPopulationConditionTree(nextTree.applicability.population.exclude));
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(nextTree)));
    setCriticalReturnMinutes(DEFAULT_CRITICAL_RETURN_MINUTES);
    setCriticalObservationCode(DEFAULT_CRITICAL_OBSERVATION_CODE);
    setSelectedFragmentId(undefined);
    setActiveCreateLayer("l1");
    createForm.setFieldsValue({
      ruleType: template.ruleType,
      triggerPoint: nextTree.triggerPoint,
      riskLevel: template.riskLevel,
      priority: 100,
      suppressedBy: undefined,
      dedupeWindowMinutes: 0,
      changeSummary: "初始化创建草稿版本",
    });
  };

  const openCreateModal = () => {
    createForm.resetFields();
    setCreateExpertMode(false);
    setOrgSearch(EMPTY_ORG_SEARCH);
    setSelectedOrgOptions(EMPTY_ORG_OPTION_CACHE);
    setSnapshotPatientId("");
    setSnapshotEncounterId("");
    setSnapshotSearchParams(null);
    setSelectedSnapshotId("");
    setCreatePreviewRunResult(null);
    resetLayeredAuthoring();
    setCreateModalVisible(true);
  };

  const toggleCreateExpertMode = (checked: boolean) => {
    setCreateExpertMode(checked);
    if (!checked && activeCreateLayer === "l3") {
      setActiveCreateLayer("l2");
    }
  };

  const toggleDetailExpertMode = (checked: boolean) => {
    setDetailExpertMode(checked);
    if (!checked && activeDetailLayer === "l3") {
      setActiveDetailLayer("l2");
    }
  };

  const applyTemplate = (templateKey: RuleTemplateKey) => {
    const template = findTemplate(templateKey);
    const nextTree = withStableRoot(instantiateRuleTemplate(templateKey));
    setSelectedTemplateKey(templateKey);
    setConditionTree(nextTree);
    setPopulationIncludeTree(toPopulationConditionTree(nextTree.applicability.population.include));
    setPopulationExcludeTree(toPopulationConditionTree(nextTree.applicability.population.exclude));
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(nextTree)));
    setCriticalReturnMinutes(DEFAULT_CRITICAL_RETURN_MINUTES);
    setCriticalObservationCode(DEFAULT_CRITICAL_OBSERVATION_CODE);
    setSelectedFragmentId(undefined);
    createForm.setFieldsValue({
      ruleType: template.ruleType,
      triggerPoint: nextTree.triggerPoint,
      riskLevel: template.riskLevel,
    });
    setActiveCreateLayer(templateKey === "critical_value_report" ? "l1" : "l2");
  };

  const buildRuleDslFromRoot = (
    root: RuleConditionGroup,
    triggerPoint: ClinicalTriggerPoint,
    applicability: RuleApplicability,
    actions: RuleConditionTree["actions"],
    explanationSummary: string,
    meta?: RuleDsl["meta"],
  ) => {
    const dsl = conditionTreeToDsl({
      triggerPoint,
      applicability,
      logic: root.logic,
      conditions: [],
      root,
      actions,
      explanationSummary,
    });
    return meta ? { ...dsl, meta } : dsl;
  };

  const createRuleMeta = useMemo<RuleDsl["meta"] | undefined>(
    () =>
      selectedTemplateKey === "critical_value_report"
        ? { parameters: createCriticalValueParameterDefinitions() }
        : undefined,
    [selectedTemplateKey],
  );

  const createActionsWithSource = (sourceRef?: string) =>
    conditionTree.actions.map((action) => ({
      ...action,
      source: {
        ...action.source,
        label:
          action.source.label === "规则版本来源" || !action.source.label.trim()
            ? sourceRef?.trim() || action.source.label
            : action.source.label,
      },
    }));

  const buildCurrentCreateRuleDsl = (sourceRef?: string) =>
    buildRuleDslFromRoot(
      conditionRoot,
      conditionTree.triggerPoint,
      conditionTree.applicability,
      createActionsWithSource(sourceRef),
      conditionTree.explanationSummary,
      createRuleMeta,
    );

  const syncTreeToDsl = () => {
    setDslEditorValue(
      formatRuleJson(
        buildRuleDslFromRoot(
          conditionRoot,
          conditionTree.triggerPoint,
          conditionTree.applicability,
          conditionTree.actions,
          conditionTree.explanationSummary,
          createRuleMeta,
        ),
      ),
    );
    // 仅静默同步，不强制切到 L3 / 不强开专家模式（修复「同步即跳专家模式」）。
    message.success("已从 L2 条件树同步到 L3 DSL");
  };

  const syncDslToTree = () => {
    try {
      const parsed = parseRuleJson(dslEditorValue);
      if (!isRecord(parsed) || !("when" in parsed)) {
        throw new Error("缺少 when");
      }
      const nextTree = dslToConditionTree(parsed);
      const root = nextTree.root ?? dslWhenToRootGroup((parsed as { when: unknown }).when);
      setConditionTree({ ...nextTree, root, logic: root.logic });
      setPopulationIncludeTree(
        toPopulationConditionTree(nextTree.applicability.population.include),
      );
      setPopulationExcludeTree(
        toPopulationConditionTree(nextTree.applicability.population.exclude),
      );
      createForm.setFieldValue("triggerPoint", nextTree.triggerPoint);
      setActiveCreateLayer("l2");
      message.success("已从 L3 DSL 回填到 L2 条件树");
    } catch {
      message.error("L3 DSL JSON 不合法，无法回填到条件树。");
    }
  };

  const conditionRoot = conditionTree.root ?? flatToRootGroup(conditionTree);
  const createRulePreviewDsl = useMemo(
    () =>
      buildRuleDslFromRoot(
        conditionRoot,
        conditionTree.triggerPoint,
        conditionTree.applicability,
        conditionTree.actions,
        conditionTree.explanationSummary,
        createRuleMeta,
      ),
    [
      conditionRoot,
      conditionTree.actions,
      conditionTree.applicability,
      conditionTree.explanationSummary,
      conditionTree.triggerPoint,
      createRuleMeta,
    ],
  );
  const createRulePreviewDslFromL3 = useMemo(() => {
    try {
      return parseRuleJson(dslEditorValue);
    } catch {
      return null;
    }
  }, [dslEditorValue]);
  const currentFragmentBodyJson = createRulePreviewDsl.when;

  const resetFragmentForm = () => {
    const packageVersion = String(
      createForm.getFieldValue("packageVersion") ?? currentCreatePackageVersion,
    ).trim();
    setEditingFragmentId(null);
    setFragmentBodyJson(currentFragmentBodyJson);
    fragmentForm.setFieldsValue({
      fragmentCode: "",
      fragmentName: "",
      category: "",
      versionNo: 1,
      packageVersion,
      status: "ACTIVE",
    });
  };

  const openFragmentLibrary = () => {
    setEditingFragmentId(null);
    setFragmentBodyJson(currentFragmentBodyJson);
    setFragmentLibraryOpen(true);
  };

  const refreshFragmentBodyFromCurrentTree = () => {
    setFragmentBodyJson(currentFragmentBodyJson);
    message.success("已使用当前条件树更新片段正文");
  };

  const editConditionFragment = (fragment: ConditionFragmentResponse) => {
    setEditingFragmentId(fragment.fragmentId);
    setFragmentBodyJson(fragment.bodyJson);
    fragmentForm.setFieldsValue({
      fragmentCode: fragment.fragmentCode,
      fragmentName: fragment.name,
      category: fragment.category ?? "",
      versionNo: fragment.versionNo,
      packageVersion: fragment.packageVersion,
      status: fragment.status,
    });
  };

  const saveConditionFragment = async () => {
    const values = fragmentForm.getFieldsValue(true) as Partial<ConditionFragmentFormValue>;
    const fragmentCode = values.fragmentCode?.trim() ?? "";
    const name = values.fragmentName?.trim() ?? "";
    const packageVersion =
      values.packageVersion?.trim() ||
      String(createForm.getFieldValue("packageVersion") ?? currentCreatePackageVersion).trim();
    if (!fragmentCode || !name || !packageVersion) {
      message.error("请填写片段编码、名称和包版本。");
      return;
    }
    const payload = {
      fragmentCode,
      name,
      category: values.category?.trim() || undefined,
      versionNo: Number(values.versionNo ?? 1),
      packageVersion,
      status: values.status ?? "ACTIVE",
      bodyJson: fragmentBodyJson ?? currentFragmentBodyJson,
    };
    try {
      if (editingFragmentId) {
        await updateConditionFragmentMutation.mutateAsync({
          fragmentId: editingFragmentId,
          body: payload,
        });
        message.success("条件片段已更新");
      } else {
        await createConditionFragmentMutation.mutateAsync(payload);
        message.success("条件片段已保存");
      }
      resetFragmentForm();
    } catch (error: unknown) {
      if (applyApiFieldErrors(fragmentForm, error)) return;
      message.error(getApiErrorMessage(error, "条件片段保存失败"));
    }
  };

  const updateTriggerPoint = (triggerPoint: ClinicalTriggerPoint) => {
    const nextTree = { ...conditionTree, triggerPoint };
    setConditionTree(nextTree);
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(nextTree)));
  };

  const updateRoot = (updater: (root: RuleConditionGroup) => RuleConditionGroup) => {
    setConditionTree((current) => {
      const root = current.root ?? flatToRootGroup(current);
      return { ...current, root: updater(root) };
    });
  };

  const updateApplicability = (updater: (current: RuleApplicability) => RuleApplicability) => {
    setConditionTree((current) => ({
      ...current,
      applicability: updater(current.applicability),
    }));
  };

  const updatePopulationCondition = (
    field: "include" | "exclude",
    tree: PopulationConditionTree | null,
  ) => {
    if (field === "include") {
      setPopulationIncludeTree(tree);
    } else {
      setPopulationExcludeTree(tree);
    }
    updateApplicability((current) => ({
      ...current,
      population: (() => {
        const population = { ...current.population };
        if (tree) {
          population[field] = populationConditionTreeToDsl(tree);
        } else {
          delete population[field];
        }
        return population;
      })(),
    }));
  };

  const updateCondition = (id: string, patch: Partial<RuleCondition>) => {
    updateRoot(
      (root) =>
        mapConditionById(root, id, (condition) => ({
          ...condition,
          ...patch,
        })) as RuleConditionGroup,
    );
  };

  const updateGroup = (id: string, patch: Partial<RuleConditionGroup>) => {
    updateRoot(
      (root) => mapGroupById(root, id, (group) => ({ ...group, ...patch })) as RuleConditionGroup,
    );
  };

  const addLeafToGroup = (groupId: string) =>
    updateRoot((root) => addNodeToGroup(root, groupId, createConditionLeaf()));

  const addGroupToGroup = (groupId: string) =>
    updateRoot((root) => addNodeToGroup(root, groupId, createConditionGroup()));

  const removeNode = (id: string) => updateRoot((root) => removeConditionById(root, id));

  const fragmentToRuleCondition = (fragment: ConditionFragmentResponse): RuleCondition =>
    createConditionLeaf({
      label: fragment.name,
      fact: "",
      operator: "exists",
      valueKind: "empty",
      fragment: {
        fragmentId: fragment.fragmentId,
        fragmentCode: fragment.fragmentCode,
        name: fragment.name,
        version: fragment.versionNo,
        packageVersion: fragment.packageVersion,
      },
    });

  const applyConditionFragment = (mode: "reference" | "copy") => {
    const fragment = selectedConditionFragment;
    if (!fragment) {
      message.warning("请选择条件片段。");
      return;
    }
    try {
      const node =
        mode === "reference"
          ? fragmentToRuleCondition(fragment)
          : dslToConditionNode(fragment.bodyJson);
      updateRoot((root) => addNodeToGroup(root, root.id, node));
      message.success(mode === "reference" ? "已引用条件片段" : "已拷贝条件片段正文");
    } catch {
      message.error("条件片段正文无法转成当前条件树。");
    }
  };

  const updateConditionValue = (condition: RuleCondition, patch: Record<string, unknown>) => {
    updateCondition(condition.id, {
      value: {
        ...conditionValueRecord(condition),
        ...patch,
      },
    });
  };

  const updateTemporalCondition = (condition: RuleCondition, patch: Record<string, unknown>) => {
    const value = conditionValueRecord(condition);
    const nested = isRecord(value.condition) ? value.condition : {};
    updateConditionValue(condition, {
      condition: {
        ...nested,
        ...patch,
      },
    });
  };

  const updateDerivedParameter = (condition: RuleCondition, key: string, value: string) => {
    const current = conditionValueRecord(condition);
    const parameters = isRecord(current.parameters) ? current.parameters : {};
    updateConditionValue(condition, {
      parameters: {
        ...parameters,
        [key]: value,
      },
    });
  };

  const updateAction = (index: number, patch: Partial<RuleActionDraft>) => {
    setConditionTree((current) => ({
      ...current,
      actions: current.actions.map((action, actionIndex) => {
        if (actionIndex !== index) return action;
        const next = { ...action, ...patch };
        return {
          ...next,
          requiresPhysicianConfirmation:
            next.requiresPhysicianConfirmation ||
            requiresPhysicianConfirmation(next.actionCode, next.atSeverity),
        };
      }),
    }));
  };

  const addAction = () => {
    setConditionTree((current) => ({
      ...current,
      actions: [...current.actions, createRuleActionDraft()],
    }));
  };

  const removeAction = (index: number) => {
    setConditionTree((current) => ({
      ...current,
      actions: current.actions.filter((_, actionIndex) => actionIndex !== index),
    }));
  };

  const updateSuggestion = (
    actionIndex: number,
    suggestionIndex: number,
    patch: Partial<RuleActionDraft["suggestions"][number]>,
  ) => {
    setConditionTree((current) => ({
      ...current,
      actions: current.actions.map((action, currentActionIndex) =>
        currentActionIndex === actionIndex
          ? {
              ...action,
              suggestions: action.suggestions.map((suggestion, currentSuggestionIndex) =>
                currentSuggestionIndex === suggestionIndex
                  ? { ...suggestion, ...patch }
                  : suggestion,
              ),
            }
          : action,
      ),
    }));
  };

  const addSuggestion = (actionIndex: number) => {
    setConditionTree((current) => ({
      ...current,
      actions: current.actions.map((action, currentActionIndex) =>
        currentActionIndex === actionIndex
          ? {
              ...action,
              suggestions: [
                ...action.suggestions,
                { label: "", actionType: "REMIND", payload: {} },
              ],
            }
          : action,
      ),
    }));
  };

  const removeSuggestion = (actionIndex: number, suggestionIndex: number) => {
    setConditionTree((current) => ({
      ...current,
      actions: current.actions.map((action, currentActionIndex) =>
        currentActionIndex === actionIndex
          ? {
              ...action,
              suggestions: action.suggestions.filter(
                (_, currentSuggestionIndex) => currentSuggestionIndex !== suggestionIndex,
              ),
            }
          : action,
      ),
    }));
  };

  const updateSuggestionPayload = (
    actionIndex: number,
    suggestionIndex: number,
    oldKey: string,
    nextKey: string,
    nextValue: string,
  ) => {
    const action = conditionTree.actions[actionIndex];
    const suggestion = action?.suggestions[suggestionIndex];
    if (!suggestion) return;
    const payload = { ...(suggestion.payload ?? {}) };
    if (oldKey && oldKey !== nextKey) delete payload[oldKey];
    if (nextKey.trim()) payload[nextKey.trim()] = nextValue;
    updateSuggestion(actionIndex, suggestionIndex, { payload });
  };

  const addSuggestionPayload = (actionIndex: number, suggestionIndex: number) => {
    const suggestion = conditionTree.actions[actionIndex]?.suggestions[suggestionIndex];
    if (!suggestion) return;
    const payload = { ...(suggestion.payload ?? {}) };
    let sequence = Object.keys(payload).length + 1;
    while (`参数${sequence}` in payload) sequence += 1;
    payload[`参数${sequence}`] = "";
    updateSuggestion(actionIndex, suggestionIndex, { payload });
  };

  const removeSuggestionPayload = (actionIndex: number, suggestionIndex: number, key: string) => {
    const suggestion = conditionTree.actions[actionIndex]?.suggestions[suggestionIndex];
    if (!suggestion) return;
    const payload = { ...(suggestion.payload ?? {}) };
    delete payload[key];
    updateSuggestion(actionIndex, suggestionIndex, { payload });
  };

  const updateConditionExpression = (
    condition: RuleCondition,
    patch: Partial<NonNullable<RuleCondition["expr"]>>,
  ) => {
    updateCondition(condition.id, {
      expr: {
        field: condition.expr?.field ?? condition.fact,
        ...condition.expr,
        ...patch,
      },
    });
  };

  const clearConditionExpression = (condition: RuleCondition) => {
    updateCondition(condition.id, { expr: undefined });
  };

  const updateExpressionWhere = (condition: RuleCondition, text: string) => {
    if (!text.trim()) {
      updateConditionExpression(condition, { where: undefined });
      return;
    }
    const parsed = parseJsonInput(text, "where 条件 JSON 不合法。", message.error);
    if (isRecord(parsed)) {
      updateConditionExpression(condition, { where: parsed });
    }
  };

  const renderConditionValueEditor = (
    condition: RuleCondition,
    needsValue: boolean,
    isFirstLeaf: boolean,
  ) => {
    if (!needsValue) {
      return (
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item label="比较值类型">
              <Select value={condition.valueKind} disabled>
                <Option value="empty">无比较值</Option>
              </Select>
            </Form.Item>
          </Col>
          <Col span={16}>
            <Form.Item label="比较值">
              <Input value="该算子不需要比较值" disabled />
            </Form.Item>
          </Col>
        </Row>
      );
    }

    if (condition.operator === "between" || condition.operator === "not_between") {
      return (
        <Row gutter={16}>
          <Col span={5}>
            <Form.Item label="最小值">
              <InputNumber
                value={conditionValueNumber(condition, "min")}
                onChange={(value) => updateConditionValue(condition, { min: value ?? "" })}
                className="mk-full-width"
              />
            </Form.Item>
          </Col>
          <Col span={5}>
            <Form.Item label="最大值">
              <InputNumber
                value={conditionValueNumber(condition, "max")}
                onChange={(value) => updateConditionValue(condition, { max: value ?? "" })}
                className="mk-full-width"
              />
            </Form.Item>
          </Col>
          <Col span={5}>
            <Form.Item label="单位">
              <Input
                value={conditionValueString(condition, "unit")}
                onChange={(event) => updateConditionValue(condition, { unit: event.target.value })}
                placeholder="如 mmol/L"
              />
            </Form.Item>
          </Col>
          <Col span={4}>
            <Form.Item label="含最小值">
              <Switch
                checked={conditionValueBoolean(condition, "includeMin")}
                onChange={(checked) => updateConditionValue(condition, { includeMin: checked })}
              />
            </Form.Item>
          </Col>
          <Col span={5}>
            <Form.Item label="含最大值">
              <Switch
                checked={conditionValueBoolean(condition, "includeMax")}
                onChange={(checked) => updateConditionValue(condition, { includeMax: checked })}
              />
            </Form.Item>
          </Col>
        </Row>
      );
    }

    if (condition.operator === "unit_compare") {
      return (
        <Row gutter={16}>
          <Col span={6}>
            <Form.Item label="换算比较符">
              <Select
                value={conditionValueString(condition, "comparison") || "gte"}
                onChange={(value) => updateConditionValue(condition, { comparison: value })}
                options={numericComparisonChoices}
              />
            </Form.Item>
          </Col>
          <Col span={5}>
            <Form.Item label="换算阈值">
              <InputNumber
                value={conditionValueNumber(condition, "value")}
                onChange={(value) => updateConditionValue(condition, { value: value ?? "" })}
                className="mk-full-width"
              />
            </Form.Item>
          </Col>
          <Col span={5}>
            <Form.Item label="目标单位">
              <Input
                value={conditionValueString(condition, "unit")}
                onChange={(event) => updateConditionValue(condition, { unit: event.target.value })}
                placeholder="如 mmol/L"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="检验项目">
              <Input
                value={conditionValueString(condition, "analyte")}
                onChange={(event) =>
                  updateConditionValue(condition, { analyte: event.target.value })
                }
                placeholder="如 glucose"
              />
            </Form.Item>
          </Col>
        </Row>
      );
    }

    if (condition.operator === "temporal") {
      const value = conditionValueRecord(condition);
      const nested = isRecord(value.condition) ? value.condition : {};
      const mode = normalizeTemporalMode(conditionValueString(condition, "mode"));
      const temporalDetailEditor = (() => {
        if (mode === "delta") {
          return (
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item label="差值方向">
                  <Select
                    value={conditionValueString(condition, "direction") || "increase"}
                    onChange={(direction) => updateConditionValue(condition, { direction })}
                  >
                    <Option value="increase">上升</Option>
                    <Option value="decrease">下降</Option>
                    <Option value="change">绝对变化</Option>
                  </Select>
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="差值阈值">
                  <InputNumber
                    min={0}
                    value={conditionValueNumber(condition, "delta")}
                    onChange={(delta) => updateConditionValue(condition, { delta: delta ?? "" })}
                    className="mk-full-width"
                  />
                </Form.Item>
              </Col>
            </Row>
          );
        }

        if (mode === "trend") {
          return (
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item label="趋势方向">
                  <Select
                    value={conditionValueString(condition, "direction") || "up"}
                    onChange={(direction) => updateConditionValue(condition, { direction })}
                  >
                    <Option value="up">上升</Option>
                    <Option value="down">下降</Option>
                  </Select>
                </Form.Item>
              </Col>
            </Row>
          );
        }

        return (
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item label={mode === "frequency" ? "频次条件比较符" : "持续条件比较符"}>
                <Select
                  value={String(nested.operator ?? "gt")}
                  onChange={(operator) => updateTemporalCondition(condition, { operator })}
                  options={numericComparisonChoices}
                />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item label={mode === "frequency" ? "频次条件阈值" : "持续条件阈值"}>
                <InputNumber
                  value={typeof nested.value === "number" ? nested.value : undefined}
                  onChange={(value) => updateTemporalCondition(condition, { value: value ?? "" })}
                  className="mk-full-width"
                />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item label={mode === "frequency" ? "频次条件单位" : "持续条件单位"}>
                <Input
                  value={String(nested.unit ?? "")}
                  onChange={(event) =>
                    updateTemporalCondition(condition, { unit: event.target.value })
                  }
                />
              </Form.Item>
            </Col>
          </Row>
        );
      })();
      return (
        <Space direction="vertical" size="small" className="mk-full-width">
          <Row gutter={16}>
            <Col span={5}>
              <Form.Item label="时间窗模式">
                <Select
                  value={mode}
                  onChange={(nextMode) => updateConditionValue(condition, { mode: nextMode })}
                >
                  {TEMPORAL_MODE_OPTIONS.map((option) => (
                    <Option key={option.value} value={option.value}>
                      {option.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item label="窗口">
                <Input
                  value={conditionValueString(condition, "window")}
                  onChange={(event) =>
                    updateConditionValue(condition, { window: event.target.value })
                  }
                  placeholder="PT48H"
                />
              </Form.Item>
            </Col>
            <Col span={7}>
              <Form.Item label="参考时间">
                <Input
                  value={conditionValueString(condition, "referenceTime")}
                  onChange={(event) =>
                    updateConditionValue(condition, { referenceTime: event.target.value })
                  }
                  placeholder="2026-06-03T00:00:00Z"
                />
              </Form.Item>
            </Col>
            {mode !== "delta" && (
              <Col span={4}>
                <Form.Item label="次数">
                  <InputNumber
                    min={1}
                    precision={0}
                    value={conditionValueNumber(condition, "count")}
                    onChange={(count) => updateConditionValue(condition, { count: count ?? 1 })}
                    className="mk-full-width"
                  />
                </Form.Item>
              </Col>
            )}
            <Col span={3}>
              <Form.Item label="单位">
                <Input
                  value={conditionValueString(condition, "unit")}
                  onChange={(event) =>
                    updateConditionValue(condition, { unit: event.target.value })
                  }
                />
              </Form.Item>
            </Col>
          </Row>
          {temporalDetailEditor}
        </Space>
      );
    }

    if (condition.operator === "is_critical") {
      const rawValues = conditionValueRecord(condition).criticalValues;
      const criticalValues = Array.isArray(rawValues)
        ? rawValues.map((item) => String(item)).join(",")
        : String(rawValues ?? "");
      return (
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item label="比较值类型">
              <Select value={condition.valueKind} disabled>
                <Option value="critical_flag">危急标记集合</Option>
              </Select>
            </Form.Item>
          </Col>
          <Col span={16}>
            <Form.Item label="危急标记">
              <Input
                value={criticalValues}
                onChange={(event) =>
                  updateConditionValue(condition, {
                    criticalValues: event.target.value
                      .split(",")
                      .map((item) => item.trim())
                      .filter(Boolean),
                  })
                }
                placeholder="如 HH,LL；留空时按来源系统任意非空危急标记判定"
              />
            </Form.Item>
          </Col>
        </Row>
      );
    }

    if (condition.operator === "is_stale") {
      return (
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item label="最大时效">
              <Input
                value={conditionValueString(condition, "maxAge")}
                onChange={(event) =>
                  updateConditionValue(condition, { maxAge: event.target.value })
                }
                placeholder="PT24H"
              />
            </Form.Item>
          </Col>
          <Col span={16}>
            <Form.Item label="参考时间">
              <Input
                value={conditionValueString(condition, "referenceTime")}
                onChange={(event) =>
                  updateConditionValue(condition, { referenceTime: event.target.value })
                }
                placeholder="2026-06-06T00:00:00Z"
              />
            </Form.Item>
          </Col>
        </Row>
      );
    }

    if (condition.operator === "derived") {
      const formula = conditionValueString(condition, "formula") || "CKD_EPI_2021_EGFR";
      const comparison = conditionValueString(condition, "comparison") || "gte";
      const parameterKeys = parameterKeysForDerivedFormula(formula);
      const parameters = isRecord(conditionValueRecord(condition).parameters)
        ? (conditionValueRecord(condition).parameters as Record<string, unknown>)
        : {};
      return (
        <Space direction="vertical" size="small" className="mk-full-width">
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="白名单公式">
                <Select
                  value={formula}
                  options={[...DERIVED_FORMULA_OPTIONS]}
                  onChange={(nextFormula) =>
                    updateConditionValue(condition, { formula: nextFormula })
                  }
                />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item label="公式比较符">
                <Select
                  value={comparison}
                  onChange={(nextComparison) =>
                    updateConditionValue(condition, { comparison: nextComparison })
                  }
                >
                  {numericComparisonChoices.map((option) => (
                    <Option key={option.value} value={option.value}>
                      {option.label}
                    </Option>
                  ))}
                  <Option value="between">区间</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item label={comparison === "between" ? "区间下限" : "公式阈值"}>
                <InputNumber
                  value={
                    comparison === "between"
                      ? conditionValueNumber(condition, "min")
                      : conditionValueNumber(condition, "value")
                  }
                  onChange={(value) =>
                    updateConditionValue(condition, {
                      [comparison === "between" ? "min" : "value"]: value ?? "",
                    })
                  }
                  className="mk-full-width"
                />
              </Form.Item>
            </Col>
            {comparison === "between" && (
              <Col span={4}>
                <Form.Item label="区间上限">
                  <InputNumber
                    value={conditionValueNumber(condition, "max")}
                    onChange={(value) => updateConditionValue(condition, { max: value ?? "" })}
                    className="mk-full-width"
                  />
                </Form.Item>
              </Col>
            )}
            <Col span={comparison === "between" ? 2 : 6}>
              <Form.Item label="单位">
                <Input
                  value={conditionValueString(condition, "unit")}
                  onChange={(event) =>
                    updateConditionValue(condition, { unit: event.target.value })
                  }
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            {parameterKeys.map((key) => (
              <Col span={6} key={key}>
                <Form.Item label={`${key} 字段路径`}>
                  <Input
                    value={String(parameters[key] ?? "")}
                    onChange={(event) => updateDerivedParameter(condition, key, event.target.value)}
                    placeholder={`如 patient.${key}`}
                  />
                </Form.Item>
              </Col>
            ))}
          </Row>
        </Space>
      );
    }

    return (
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item label="比较值类型">
            <Select
              value={condition.valueKind}
              onChange={(value: RuleValueKind) =>
                updateCondition(condition.id, { valueKind: value })
              }
            >
              {Object.entries(VALUE_KIND_LABELS)
                .filter(
                  ([value]) => !["range", "measurement", "temporal", "derived"].includes(value),
                )
                .map(([value, label]) => (
                  <Option key={value} value={value}>
                    {label}
                  </Option>
                ))}
            </Select>
          </Form.Item>
        </Col>
        <Col span={16}>
          <Form.Item label="比较值" htmlFor={isFirstLeaf ? "rule-condition-value" : undefined}>
            {fieldByPath.get(condition.fact)?.codeSystem && condition.valueKind !== "list" ? (
              <StandardTermValueAutoComplete
                id={isFirstLeaf ? "rule-condition-value" : undefined}
                codeSystem={fieldByPath.get(condition.fact)?.codeSystem ?? ""}
                value={String(condition.value ?? "")}
                onChange={(next) => updateCondition(condition.id, { value: next })}
              />
            ) : (
              <Input
                id={isFirstLeaf ? "rule-condition-value" : undefined}
                value={String(condition.value ?? "")}
                onChange={(event) => updateCondition(condition.id, { value: event.target.value })}
                placeholder={
                  condition.valueKind === "list"
                    ? "多个值用英文逗号分隔"
                    : "输入来自已审核规则来源的比较值"
                }
              />
            )}
          </Form.Item>
        </Col>
      </Row>
    );
  };

  const fieldCatalogQuery = useContextFieldCatalog();
  const fieldCatalogList = fieldCatalogQuery.data ?? [];
  const fieldCatalogOptions = buildFieldCatalogOptions(fieldCatalogList);
  const fieldByPath = new Map(fieldCatalogList.map((field) => [field.fieldPath, field]));
  const orgOptions = (level: RuleOrgLevel, units: OrgUnit[] = []) => {
    const options = units
      .filter((unit) => unit.level === level && Boolean(unit.id))
      .map((unit) => ({
        value: unit.id as string,
        label: `${unit.name} · ${ORG_LEVEL_LABELS[unit.level]} · ${unit.code}`,
      }));
    return Array.from(
      new Map(
        [...selectedOrgOptions[level], ...options].map((option) => [option.value, option]),
      ).values(),
    );
  };

  const updateOrgScope = (
    level: RuleOrgLevel,
    field: "groupIds" | "hospitalIds" | "deptIds",
    values: string[],
    units: OrgUnit[] | undefined,
  ) => {
    const options = orgOptions(level, units);
    setSelectedOrgOptions((current) => ({
      ...current,
      [level]: options.filter((option) => values.includes(option.value)),
    }));
    updateApplicability((current) => ({
      ...current,
      orgScope: { ...current.orgScope, [field]: values },
    }));
  };

  const renderPopulationConditionEditor = (
    field: "include" | "exclude",
    tree: PopulationConditionTree | null,
  ) => {
    const included = field === "include";
    const title = included ? "纳入人群" : "排除人群";
    return (
      <section className={styles.populationSection} aria-label={`${title}条件`}>
        <div className={styles.populationHeader}>
          <div className={styles.populationCopy}>
            <Text strong>{title}</Text>
            <Text type="secondary">
              {included ? "仅对满足条件的患者生效" : "命中条件的患者不执行本规则"}
            </Text>
          </div>
          <Switch
            aria-label={`启用${included ? "纳入" : "排除"}条件`}
            checked={Boolean(tree)}
            onChange={(checked) =>
              updatePopulationCondition(field, checked ? createPopulationConditionTree() : null)
            }
          />
        </div>
        {tree ? (
          <ConditionTreeEditor
            value={tree}
            onChange={(next) => updatePopulationCondition(field, next)}
            fieldCatalog={fieldCatalogList}
            fieldCatalogError={fieldCatalogQuery.isError}
          />
        ) : null}
      </section>
    );
  };

  // 选中字段时按目录 dataType 自动带出比较值类型，降低手填出错。
  const dataTypeToValueKind = (dataType?: string): RuleValueKind => {
    switch (dataType) {
      case "number":
        return "number";
      case "boolean":
        return "boolean";
      case "list":
        return "list";
      default:
        return "string";
    }
  };
  const handleFactSelect = (conditionId: string, fieldPath: string) => {
    const descriptor = fieldByPath.get(fieldPath);
    if (!descriptor) {
      updateCondition(conditionId, { fact: fieldPath });
      return;
    }
    updateCondition(conditionId, {
      fact: fieldPath,
      valueKind: dataTypeToValueKind(descriptor.dataType),
    });
  };

  const firstLeafId = ((): string | undefined => {
    const find = (node: RuleConditionNode): string | undefined => {
      if (!isConditionGroup(node)) return node.id;
      for (const child of node.children) {
        const found = find(child);
        if (found) return found;
      }
      return undefined;
    };
    return find(conditionRoot);
  })();
  const firstCondition = ((): RuleCondition | undefined => {
    const find = (node: RuleConditionNode): RuleCondition | undefined => {
      if (!isConditionGroup(node)) return node;
      for (const child of node.children) {
        const found = find(child);
        if (found) return found;
      }
      return undefined;
    };
    return find(conditionRoot);
  })();

  const updateCriticalValueField = (fieldPath: string) => {
    if (!firstLeafId) return;
    const trimmed = fieldPath.trim();
    updateCondition(firstLeafId, {
      label: "危急检验结果",
      fact: fieldPath,
      expr: trimmed.includes("[]")
        ? {
            field: trimmed,
            select: "latest",
            where: criticalObservationWhere(criticalObservationCode),
          }
        : undefined,
      operator: "gte",
      valueKind: "number",
    });
  };

  const updateCriticalObservationCode = (value: string) => {
    setCriticalObservationCode(value);
    if (!firstLeafId || !firstCondition) return;
    const fieldPath = (firstCondition.expr?.field ?? firstCondition.fact).trim();
    updateCondition(firstLeafId, {
      expr: fieldPath.includes("[]")
        ? {
            field: fieldPath,
            select: "latest",
            where: criticalObservationWhere(value),
          }
        : undefined,
    });
  };

  const updateCriticalThreshold = (value: number | null) => {
    if (!firstLeafId) return;
    updateCondition(firstLeafId, {
      operator: "gte",
      value: value ?? "",
      valueKind: "number",
    });
  };

  const updateCriticalReturnMinutes = (value: number | null) => {
    const minutes = Math.max(1, Math.round(value ?? DEFAULT_CRITICAL_RETURN_MINUTES));
    setCriticalReturnMinutes(minutes);
    updateAction(0, {
      detail: criticalValueReportDetail(minutes),
    });
  };
  const criticalThresholdValue = (() => {
    if (typeof firstCondition?.value === "number") return firstCondition.value;
    const numeric = Number(firstCondition?.value);
    return Number.isFinite(numeric) ? numeric : undefined;
  })();

  const buildCreateRuleParameterBindings = (): Record<string, unknown> | null | undefined => {
    if (selectedTemplateKey !== "critical_value_report") return undefined;
    const observationCode = criticalObservationCode.trim();
    if (!observationCode) {
      message.error("请填写检验项编码。");
      setActiveCreateLayer("l1");
      return null;
    }
    if (typeof criticalThresholdValue !== "number" || !Number.isFinite(criticalThresholdValue)) {
      message.error("请填写危急阈值。");
      setActiveCreateLayer("l1");
      return null;
    }
    return {
      observationCode,
      criticalThreshold: criticalThresholdValue,
      returnMinutes: criticalReturnMinutes,
    };
  };

  const renderConditionLeaf = (condition: RuleCondition) => {
    if (condition.fragment) {
      return (
        <div key={condition.id} className={styles.conditionCard}>
          <div className={styles.conditionHeader}>
            <Space>
              <Tag color="green">条件片段</Tag>
              <Text strong>
                {condition.label || condition.fragment.name || condition.fragment.fragmentCode}
              </Text>
              <Text type="secondary">
                {condition.fragment.fragmentCode} · v{condition.fragment.version} ·{" "}
                {condition.fragment.packageVersion}
              </Text>
            </Space>
            <Button
              size="small"
              aria-label="移除条件"
              icon={<DeleteOutlined />}
              onClick={() => removeNode(condition.id)}
            />
          </div>
        </div>
      );
    }
    const needsValue = conditionNeedsValue(condition.operator);
    const isFirstLeaf = condition.id === firstLeafId;
    const expressionEnabled = Boolean(condition.expr);
    const expressionField = condition.expr?.field ?? condition.fact;
    const expressionWhereText = condition.expr?.where ? formatRuleJson(condition.expr.where) : "";
    return (
      <div key={condition.id} className={styles.conditionCard}>
        <div className={styles.conditionHeader}>
          <Space>
            <Text strong>{condition.label || "条件"}</Text>
            {isClinicalOperator(condition.operator) && <Tag color="volcano">临床算子</Tag>}
          </Space>
          <Button
            size="small"
            aria-label="移除条件"
            icon={<DeleteOutlined />}
            onClick={() => removeNode(condition.id)}
          />
        </div>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item label="条件标签">
              <Input
                value={condition.label}
                onChange={(event) => updateCondition(condition.id, { label: event.target.value })}
              />
            </Form.Item>
          </Col>
          <Col span={10}>
            <Form.Item
              label="上下文字段路径"
              htmlFor={isFirstLeaf ? "rule-condition-fact" : undefined}
            >
              <AutoComplete
                id={isFirstLeaf ? "rule-condition-fact" : undefined}
                value={condition.fact}
                options={fieldCatalogOptions}
                filterOption={(input, option) => {
                  const leaf = option as { value?: string; label?: string } | undefined;
                  const haystack = `${leaf?.value ?? ""} ${leaf?.label ?? ""}`.toLowerCase();
                  return haystack.includes(input.toLowerCase());
                }}
                onSelect={(value) => handleFactSelect(condition.id, value)}
                onChange={(value) => updateCondition(condition.id, { fact: value })}
                placeholder="从字段目录选择或输入，如 observations[].valueNumeric"
              />
            </Form.Item>
          </Col>
          <Col span={6}>
            <Form.Item label="算子">
              <Select
                value={condition.operator}
                onChange={(value: RuleOperator) =>
                  updateCondition(condition.id, {
                    operator: value,
                    valueKind: valueKindForOperator(value, condition.valueKind),
                    value: valueForOperator(value, condition.value),
                  })
                }
              >
                {Object.entries(OPERATOR_LABELS).map(([value, label]) => (
                  <Option key={value} value={value}>
                    {label}
                  </Option>
                ))}
              </Select>
            </Form.Item>
          </Col>
        </Row>
        <div className={styles.expressionPanel}>
          <Space direction="vertical" size="small" className="mk-full-width">
            <Switch
              checked={expressionEnabled}
              checkedChildren="聚合表达式"
              unCheckedChildren="普通字段"
              onChange={(checked) => {
                if (checked) {
                  updateConditionExpression(condition, {
                    field: expressionField,
                    select: condition.expr?.select ?? "latest",
                  });
                } else {
                  clearConditionExpression(condition);
                }
              }}
            />
            {expressionEnabled && (
              <>
                <Row gutter={16}>
                  <Col span={8}>
                    <Form.Item label="表达式字段">
                      <AutoComplete
                        value={expressionField}
                        options={fieldCatalogOptions}
                        filterOption={(input, option) => {
                          const leaf = option as { value?: string; label?: string } | undefined;
                          const haystack =
                            `${leaf?.value ?? ""} ${leaf?.label ?? ""}`.toLowerCase();
                          return haystack.includes(input.toLowerCase());
                        }}
                        onSelect={(value) =>
                          updateCondition(condition.id, {
                            fact: value,
                            expr: { ...condition.expr, field: value },
                          })
                        }
                        onChange={(value) =>
                          updateCondition(condition.id, {
                            fact: value,
                            expr: { ...condition.expr, field: value },
                          })
                        }
                        placeholder="如 observations[].value"
                      />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item label="聚合函数">
                      <Select
                        value={condition.expr?.select ?? "latest"}
                        options={[...RULE_EXPRESSION_SELECT_OPTIONS]}
                        onChange={(select) => updateConditionExpression(condition, { select })}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item label="窗口">
                      <Input
                        value={condition.expr?.over ?? ""}
                        onChange={(event) =>
                          updateConditionExpression(condition, { over: event.target.value })
                        }
                        placeholder="PT48H"
                      />
                    </Form.Item>
                  </Col>
                  <Col span={8}>
                    <Form.Item label="参考时间">
                      <Input
                        value={condition.expr?.referenceTime ?? ""}
                        onChange={(event) =>
                          updateConditionExpression(condition, {
                            referenceTime: event.target.value,
                          })
                        }
                        placeholder="2026-06-03T00:00:00Z"
                      />
                    </Form.Item>
                  </Col>
                </Row>
                <Form.Item label="where 过滤条件">
                  <TextArea
                    key={`${condition.id}-${expressionWhereText}`}
                    defaultValue={expressionWhereText}
                    autoSize={{ minRows: 2, maxRows: 5 }}
                    onBlur={(event) => updateExpressionWhere(condition, event.target.value)}
                    placeholder='{"all":[{"expr":{"field":"observations[].code"},"operator":"equals","value":{"const":"CREATININE"}}]}'
                  />
                </Form.Item>
              </>
            )}
          </Space>
        </div>
        {renderConditionValueEditor(condition, needsValue, isFirstLeaf)}
      </div>
    );
  };

  const renderConditionGroup = (group: RuleConditionGroup, depth = 0) => {
    const isRoot = depth === 0;
    const depthReached = rootDepth(conditionRoot) >= MAX_TREE_DEPTH;
    // 根组用中性白底；子组按深度加淡绿底 + 左边线 + 缩进，使嵌套层级清晰可读。
    const groupClassName = isRoot ? styles.rootGroup : styles.nestedGroup;
    return (
      <div key={group.id} className={groupClassName}>
        <div className={styles.groupHeader}>
          <Tag color="green">{isRoot ? "条件根组" : "子条件组"}</Tag>
          <Select
            aria-label="条件组关系"
            size="small"
            value={group.logic}
            className={styles.controlSm}
            onChange={(value: RuleLogic) => updateGroup(group.id, { logic: value })}
          >
            <Option value="all">全部条件满足</Option>
            <Option value="any">任一条件满足</Option>
          </Select>
          <Space size={4}>
            <Text type="secondary" className={styles.textSmall}>
              取反
            </Text>
            <Switch
              aria-label="取反"
              size="small"
              checked={Boolean(group.negate)}
              onChange={(checked) => updateGroup(group.id, { negate: checked })}
            />
          </Space>
          {!isRoot && (
            <Button
              size="small"
              aria-label="删除条件组"
              icon={<DeleteOutlined />}
              className={styles.toolbarActions}
              onClick={() => removeNode(group.id)}
            />
          )}
        </div>
        <Space direction="vertical" size="small" className="mk-full-width">
          {group.children.map((child) =>
            isConditionGroup(child)
              ? renderConditionGroup(child, depth + 1)
              : renderConditionLeaf(child),
          )}
          <Space wrap>
            <Button
              size="small"
              icon={<PlusOutlined />}
              aria-label="新增条件"
              onClick={() => addLeafToGroup(group.id)}
            >
              条件
            </Button>
            <Tooltip title={depthReached ? `已达最大嵌套深度 ${MAX_TREE_DEPTH}` : ""}>
              <Button
                size="small"
                icon={<BranchesOutlined />}
                aria-label="新增子条件组"
                disabled={depthReached}
                onClick={() => addGroupToGroup(group.id)}
              >
                子条件组
              </Button>
            </Tooltip>
          </Space>
        </Space>
      </div>
    );
  };

  const renderReadonlyNode = (node: RuleConditionNode): ReactNode => {
    if (isConditionGroup(node)) {
      return (
        <div key={node.id} className={styles.readonlyGroup}>
          <Space className={styles.marginBottomSm}>
            <Tag color="green">{node.negate ? "非（NOT）" : "组"}</Tag>
            <Text type="secondary">{node.logic === "all" ? "全部条件满足" : "任一条件满足"}</Text>
          </Space>
          <Space direction="vertical" size="small" className="mk-full-width">
            {node.children.map((child) => renderReadonlyNode(child))}
          </Space>
        </div>
      );
    }
    if (node.fragment) {
      return (
        <Descriptions key={node.id} bordered column={2} size="small">
          <Descriptions.Item label="类型">
            <Tag color="green">条件片段</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="片段名称">
            {node.label || node.fragment.name || node.fragment.fragmentCode}
          </Descriptions.Item>
          <Descriptions.Item label="片段编码">{node.fragment.fragmentCode}</Descriptions.Item>
          <Descriptions.Item label="片段版本">v{node.fragment.version}</Descriptions.Item>
          <Descriptions.Item label="包版本">{node.fragment.packageVersion}</Descriptions.Item>
        </Descriptions>
      );
    }
    return (
      <Descriptions key={node.id} bordered column={2} size="small">
        <Descriptions.Item label="条件标签">{node.label}</Descriptions.Item>
        <Descriptions.Item label="字段路径">{node.expr?.field ?? node.fact}</Descriptions.Item>
        {node.expr && (
          <Descriptions.Item label="表达式">
            {node.expr.select ?? "字段"} {node.expr.over ? `· ${node.expr.over}` : ""}
          </Descriptions.Item>
        )}
        <Descriptions.Item label="算子">{OPERATOR_LABELS[node.operator]}</Descriptions.Item>
        <Descriptions.Item label="比较值">{conditionValueText(node)}</Descriptions.Item>
      </Descriptions>
    );
  };

  const handleCreateRule = async () => {
    try {
      const values = await createForm.validateFields();
      let parsedDsl: unknown;
      let submitRoot: RuleConditionGroup;
      let submitTree: RuleConditionTree;
      try {
        // 专家模式以 L3 JSON 为准；普通模式以 L2 递归条件树为准（避免未点同步而提交过期 DSL）。
        parsedDsl = createExpertMode
          ? parseRuleJson(dslEditorValue)
          : buildCurrentCreateRuleDsl(values.sourceRef);
        if (
          !isRecord(parsedDsl) ||
          !("when" in parsedDsl) ||
          !isClinicalTriggerPoint(parsedDsl.trigger)
        ) {
          throw new Error("缺少 trigger 或 when");
        }
        submitTree = dslToConditionTree(parsedDsl);
        submitRoot = submitTree.root ?? dslWhenToRootGroup((parsedDsl as { when: unknown }).when);
      } catch {
        message.error("L3 DSL JSON 不合法，请先从 L2 同步或修正后再提交。");
        setCreateExpertMode(true);
        setActiveCreateLayer("l3");
        return;
      }
      if (rootDepth(submitRoot) > MAX_TREE_DEPTH) {
        message.error(`条件嵌套深度超过上限 ${MAX_TREE_DEPTH}，请拆分规则。`);
        setActiveCreateLayer("l2");
        return;
      }
      if (rootHasUnresolvedFact(submitRoot)) {
        message.error("请在 L2 条件树填写真实上下文字段路径，不能提交模板占位符。");
        setActiveCreateLayer("l2");
        return;
      }
      if (
        (!createExpertMode && populationIncludeTree
          ? hasUnresolvedPopulationFact(populationIncludeTree)
          : false) ||
        (!createExpertMode && populationExcludeTree
          ? hasUnresolvedPopulationFact(populationExcludeTree)
          : false)
      ) {
        message.error("请补全适用人群条件字段，或关闭对应人群条件。");
        setActiveCreateLayer("l2");
        return;
      }

      const parameterBindings = buildCreateRuleParameterBindings();
      if (parameterBindings === null) return;

      await createRuleMutation.mutateAsync({
        ruleCode: values.ruleCode,
        name: values.name,
        ruleType: values.ruleType,
        authoringMode: "VISUAL",
        riskLevel: values.riskLevel,
        priority: values.priority ?? 100,
        suppressedBy: values.suppressedBy || undefined,
        dedupeWindowSeconds: Math.round((values.dedupeWindowMinutes ?? 0) * 60),
        packageVersion: values.packageVersion,
        sourceRef: values.sourceRef,
        changeSummary: values.changeSummary,
        dslJson: parsedDsl,
        explanationJson: createExplanationTemplate({
          ...submitTree,
          root: submitRoot,
        }),
        ...(parameterBindings ? { parameterBindings } : {}),
      });

      message.success("新规则创建成功，状态为草稿");
      setCreateModalVisible(false);
      createForm.resetFields();
      setCreatePreviewRunResult(null);
      resetLayeredAuthoring();
      refetchList();
    } catch (error: unknown) {
      if (applyApiFieldErrors(createForm, error)) return;
      message.error(getApiErrorMessage(error, "创建规则失败"));
    }
  };

  const handleAddTestCase = async () => {
    try {
      const values = await caseForm.validateFields();
      const snapshot = snapshotDetailQuery.data;
      if (!selectedSnapshotId || !snapshot) {
        message.error("请先检索并选择一份 ACTIVE 标准上下文快照。");
        return;
      }
      if (snapshot.status !== "ACTIVE" || !snapshot.resources) {
        message.error("所选快照不是可用的 ACTIVE 标准上下文快照，请重新选择。");
        return;
      }

      await addTestCaseMutation.mutateAsync({
        packageVersion: selectedRulePackageVersion,
        caseType: values.caseType,
        contextSnapshotId: snapshot.snapshotId,
        expectedHit: values.expectedHit,
        expectedSeverity: values.expectedHit ? values.expectedSeverity : undefined,
        expectedActionCode: values.expectedHit ? values.expectedActionCode : undefined,
      });

      message.success("成功新增测试用例");
      setCaseModalVisible(false);
      caseForm.resetFields();
      refetchDetail();
    } catch (error: unknown) {
      if (applyApiFieldErrors(caseForm, error)) return;
      message.error(getApiErrorMessage(error, "添加用例失败"));
    }
  };

  const runSimulation = async (inputPayload: unknown) => {
    try {
      const result = await simulateMutation.mutateAsync({
        packageVersion: selectedRulePackageVersion,
        inputPayload,
      });
      setSimulateResult(result);
      message.success("规则试运行成功，已返回求值结果");
      refetchDetail();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "规则试运行失败"));
    }
  };

  const handleRunRuleTests = async () => {
    try {
      const result = await runRuleTestsMutation.mutateAsync({
        packageVersion: selectedRulePackageVersion,
      });
      if (result.allPassed) {
        message.success("全部发布门禁用例执行通过");
      } else {
        message.warning("用例执行完成，存在未通过项，请核对期望与规则配置。");
      }
      refetchDetail();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "执行规则测试用例失败"));
    }
  };

  const handleSnapshotSearch = () => {
    const patientId = snapshotPatientId.trim();
    const encounterId = snapshotEncounterId.trim();
    if (!patientId && !encounterId) {
      message.warning("请输入患者 ID 或就诊 ID 后再读取真实快照。");
      return;
    }
    setSnapshotSearchParams({
      patientId: patientId || undefined,
      encounterId: encounterId || undefined,
    });
    setSelectedSnapshotId("");
    setSimulateResult(null);
    setCreatePreviewRunResult(null);
  };

  const handleRunCreatePreview = async () => {
    const packageVersion = String(createForm.getFieldValue("packageVersion") ?? "").trim();
    if (!packageVersion) {
      message.warning("请先填写标准上下文包版本。");
      return;
    }
    if (!selectedSnapshotId) {
      message.warning("请先选择一个 ACTIVE 快照。");
      return;
    }
    const snapshot = snapshotDetailQuery.data;
    if (!snapshot || snapshot.status !== "ACTIVE" || !snapshot.resources) {
      message.error("所选快照不是可用的 ACTIVE 标准上下文快照，请重新选择。");
      return;
    }
    let draftDsl: unknown;
    try {
      draftDsl = createExpertMode ? parseRuleJson(dslEditorValue) : buildCurrentCreateRuleDsl();
      if (!isRecord(draftDsl) || !("when" in draftDsl)) {
        throw new Error("缺少 when");
      }
      const root = createExpertMode
        ? dslWhenToRootGroup((draftDsl as { when: unknown }).when)
        : conditionRoot;
      if (rootDepth(root) > MAX_TREE_DEPTH) {
        message.error(`条件嵌套深度超过上限 ${MAX_TREE_DEPTH}，请拆分规则。`);
        setActiveCreateLayer("l2");
        return;
      }
      if (rootHasUnresolvedFact(root)) {
        message.error("请先补全真实上下文字段路径，再运行草稿试运行。");
        setActiveCreateLayer("l2");
        return;
      }
    } catch {
      message.error("草稿 DSL 不合法，请先修正 L2 条件树或 L3 JSON。");
      return;
    }

    try {
      const result = await previewRunMutation.mutateAsync({
        subject: "RULE_CONDITION",
        packageVersion,
        snapshotId: selectedSnapshotId,
        dsl: draftDsl,
      });
      setCreatePreviewRunResult(result);
      message.success("草稿试运行完成，已返回真实快照证据");
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "草稿规则试运行失败"));
    }
  };

  const handleSimulateSelectedSnapshot = async () => {
    if (!selectedSnapshotId) {
      message.warning("请先选择一个 ACTIVE 快照。");
      return;
    }
    const snapshot = snapshotDetailQuery.data;
    if (!snapshot?.resources) {
      message.error("快照详情未返回标准资源，不能进行规则试运行。");
      return;
    }
    await runSimulation(snapshot.resources);
  };

  const refreshGovernance = () => {
    setReleaseReason("");
    refetchDetail();
    refetchList();
  };

  const refreshRuleMetrics = () => {
    backtestQuery.refetch();
    driftQuery.refetch();
    refetchDetail();
  };

  const handleRunBacktest = async () => {
    if (!selectedRuleId || !detailData) return;
    if (detailData.testCases.length === 0) {
      message.error("请先配置真实脱敏金标准样本。");
      return;
    }
    try {
      await runBacktestMutation.mutateAsync({
        ruleId: selectedRuleId,
        cohortRef: `test-cases:${detailData.version.versionId}`,
      });
      message.success("历史回测完成");
      refreshRuleMetrics();
    } catch (error: unknown) {
      modal.error({
        title: "历史回测未完成",
        content: getApiErrorMessage(error, "请核查金标准样本和规则 DSL 后重试。"),
      });
    }
  };

  const handleCaptureDriftSnapshot = async () => {
    if (!selectedRuleId || !backtestQuery.data) return;
    const safeDays = Math.max(1, Math.min(90, Math.round(driftWindowDays || 7)));
    const safeThreshold = Math.max(0, Math.min(1, driftThreshold || 0.1));
    const windowEnd = new Date();
    const windowStart = new Date(windowEnd.getTime() - safeDays * 86_400_000);
    try {
      await captureDriftMutation.mutateAsync({
        ruleId: selectedRuleId,
        windowStart: windowStart.toISOString(),
        windowEnd: windowEnd.toISOString(),
        baselineBacktestId: backtestQuery.data.backtestId,
        threshold: safeThreshold,
      });
      message.success("漂移快照已记录");
      refreshRuleMetrics();
    } catch (error: unknown) {
      modal.error({
        title: "漂移监测未完成",
        content: getApiErrorMessage(error, "请核查生产执行样本、回测基线和治理阶段后重试。"),
      });
    }
  };

  const handleGovernanceTransition = async (
    targetState: RuleGovernanceState,
    successMessage: string,
  ) => {
    if (!selectedRuleId) return;
    const impactDigest = impactQuery.data?.impactDigest;
    const reason = releaseReason.trim();
    const impactRequired = ["PEER_REVIEW", "SHADOW", "CANARY", "FULL"].includes(targetState);
    if (impactRequired && !impactDigest) {
      setActiveDetailLayer("release");
      message.error("请先读取当前影响摘要，再推进治理状态。");
      return;
    }
    if (!reason) {
      setActiveDetailLayer("release");
      message.error("请填写本次治理说明。");
      return;
    }
    try {
      await governanceTransitionMutation.mutateAsync({
        ruleId: selectedRuleId,
        packageVersion: selectedRulePackageVersion,
        targetState,
        impactDigest,
        reason,
      });
      message.success(successMessage);
      refreshGovernance();
    } catch (error: unknown) {
      modal.error({
        title: "规则治理推进被拒绝",
        content: getApiErrorMessage(error, "当前阶段门禁未满足，请核查页面中的真实证据。"),
      });
    }
  };

  const handleGovernanceSignoff = async (
    stage: RuleSignoffStage,
    decision: "APPROVED" | "REJECTED",
  ) => {
    if (!selectedRuleId) return;
    const reason = releaseReason.trim();
    if (!reason) {
      message.error("请填写评审或会签说明。");
      return;
    }
    try {
      await signoffMutation.mutateAsync({
        ruleId: selectedRuleId,
        packageVersion: selectedRulePackageVersion,
        stage,
        decision,
        reason,
      });
      message.success(decision === "APPROVED" ? "签署意见已记录" : "规则已退回草稿");
      refreshGovernance();
    } catch (error: unknown) {
      modal.error({
        title: "规则签署被拒绝",
        content: getApiErrorMessage(error, "当前登录角色或治理阶段不允许本次签署。"),
      });
    }
  };

  const conditionFragmentColumns: TableProps<ConditionFragmentResponse>["columns"] = [
    {
      title: "片段编码",
      dataIndex: "fragmentCode",
      key: "fragmentCode",
      render: (text: string) => <Tag color="green">{text}</Tag>,
    },
    {
      title: "片段名称",
      dataIndex: "name",
      key: "name",
      className: styles.textStrong,
    },
    {
      title: "版本",
      dataIndex: "versionNo",
      key: "versionNo",
      render: (version: number) => `v${version}`,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: ConditionFragmentStatus) => (
        <Tag color={conditionFragmentStatusColor(status)}>{status}</Tag>
      ),
    },
    {
      title: "包版本",
      dataIndex: "packageVersion",
      key: "packageVersion",
    },
    {
      title: "操作",
      key: "actions",
      render: (_, fragment) => (
        <Space>
          <Button size="small" onClick={() => editConditionFragment(fragment)}>
            编辑
          </Button>
          <Button
            size="small"
            icon={<FileSearchOutlined />}
            onClick={() => setImpactFragmentId(fragment.fragmentId)}
          >
            影响
          </Button>
        </Space>
      ),
    },
  ];

  const columns: TableProps<RuleDefinition>["columns"] = [
    {
      title: "规则编码",
      dataIndex: "ruleCode",
      key: "ruleCode",
      render: (text: string) => <Tag color="blue">{text}</Tag>,
    },
    {
      title: "规则名称",
      dataIndex: "name",
      key: "name",
      className: styles.textStrong,
    },
    {
      title: "规则类别",
      dataIndex: "ruleType",
      key: "ruleType",
      render: (type: RuleDefinition["ruleType"]) => RULE_TYPE_LABELS[type],
    },
    {
      title: "风险评级",
      dataIndex: "riskLevel",
      key: "riskLevel",
      render: renderRiskTag,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: renderStatus,
    },
    {
      title: "当前包版本",
      dataIndex: "packageVersion",
      key: "packageVersion",
      render: (val: string) => val || <span className={styles.textMuted}>未锁定</span>,
    },
    {
      title: "操作",
      key: "action",
      render: (_value: unknown, record: RuleDefinition) => (
        <Button
          type="link"
          onClick={() => {
            setSelectedRuleId(record.ruleId);
            setActiveDetailLayer("l2");
            setSelectedSnapshotId("");
            setSnapshotSearchParams(null);
            setSimulateResult(null);
            setReleaseReason("");
            setDetailExpertMode(false);
          }}
        >
          查看配置与试运行
        </Button>
      ),
    },
  ];

  const renderSnapshotChoice = (snapshot: ContextSnapshotSummary) => {
    const selected = selectedSnapshotId === snapshot.snapshotId;
    return (
      <Card
        key={snapshot.snapshotId}
        size="small"
        hoverable
        onClick={() => {
          setSelectedSnapshotId(snapshot.snapshotId);
          setSimulateResult(null);
          setCreatePreviewRunResult(null);
        }}
      >
        <Space direction="vertical" size={2} className="mk-full-width">
          <Space>
            <Badge status={selected ? "processing" : "default"} />
            <Text strong>{snapshot.snapshotId}</Text>
            <Tag color={snapshot.status === "ACTIVE" ? "green" : "default"}>{snapshot.status}</Tag>
          </Space>
          <Text type="secondary">
            患者 {snapshot.patientId || "-"} · 就诊 {snapshot.encounterId || "-"} · 质量{" "}
            {snapshot.qualityStatus}
          </Text>
          {snapshot.createdAt && <Text type="secondary">创建时间 {snapshot.createdAt}</Text>}
        </Space>
      </Card>
    );
  };

  const renderSnapshotChoices = () => {
    if (!snapshotSearchParams) {
      return <Empty description="输入患者 ID 或就诊 ID 后读取 ACTIVE 快照" />;
    }
    if (snapshotsQuery.isLoading) {
      return <Alert type="info" showIcon message="正在读取真实上下文快照列表..." />;
    }
    if (snapshotsQuery.isError) {
      return (
        <Alert
          type="error"
          showIcon
          message="上下文快照接口读取失败"
          description="请稍后重试；页面不会用演示病例替代真实快照。"
        />
      );
    }
    if (snapshots.length === 0) {
      return <Empty description="当前患者或就诊下暂无 ACTIVE 临床快照" />;
    }
    return (
      <Space direction="vertical" size="small" className="mk-full-width">
        {snapshots.map(renderSnapshotChoice)}
      </Space>
    );
  };

  const renderSelectedSnapshotDetail = () => {
    if (!selectedSnapshotId) {
      return <Empty description="请选择一个快照，系统会读取该快照详情用于规则试运行" />;
    }
    if (snapshotDetailQuery.isLoading) {
      return <Alert type="info" showIcon message="正在读取快照详情..." />;
    }
    if (snapshotDetailQuery.isError) {
      return (
        <Alert
          type="error"
          showIcon
          message="快照详情读取失败"
          description="不能在无真实详情的情况下试运行。"
        />
      );
    }
    const snapshot = snapshotDetailQuery.data;
    if (!snapshot?.resources) {
      return (
        <Alert
          type="warning"
          showIcon
          message="该快照未返回标准资源"
          description="请更换快照或检查上下文快照采集链路。"
        />
      );
    }
    return (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="快照 ID">{snapshot.snapshotId}</Descriptions.Item>
          <Descriptions.Item label="质量状态">{snapshot.qualityStatus}</Descriptions.Item>
          <Descriptions.Item label="绑定包版本">
            {snapshot.packageVersion || selectedRulePackageVersion || "-"}
          </Descriptions.Item>
          <Descriptions.Item label="缺失字段">
            {snapshot.missingFields?.length ? `${snapshot.missingFields.length} 项` : "无"}
          </Descriptions.Item>
          <Descriptions.Item label="Trace">{snapshot.traceId || "-"}</Descriptions.Item>
        </Descriptions>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          aria-label="使用该快照试运行"
          onClick={handleSimulateSelectedSnapshot}
          loading={simulateMutation.isPending}
          block
        >
          使用该快照试运行
        </Button>
      </Space>
    );
  };

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
          render: (fact: string) => <Text code>{fact}</Text>,
        },
        {
          title: "算子",
          dataIndex: "operator",
        },
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

  const renderPreviewRunResult = (result: AuthoringPreviewRunResponse | null) => {
    if (!result) {
      return <Empty description="选择真实快照后运行草稿，结果会在这里返回" />;
    }
    return (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Space wrap>
          <Tag color={result.matched ? "green" : "default"}>
            {result.matched ? "命中" : "未命中"}
          </Tag>
          {result.severity && renderRiskTag(result.severity)}
          {result.contextQualityStatus && (
            <Tag color={result.contextQualityStatus === "COMPLETE" ? "green" : "orange"}>
              快照质量：{result.contextQualityStatus}
            </Tag>
          )}
        </Space>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="试运行结果">{result.outcomeText}</Descriptions.Item>
          <Descriptions.Item label="快照 ID">{result.snapshotId}</Descriptions.Item>
          <Descriptions.Item label="包版本">{result.packageVersion}</Descriptions.Item>
          <Descriptions.Item label="Trace">{result.traceId || "-"}</Descriptions.Item>
        </Descriptions>
        {renderPreviewRunEvidence(result.conditionEvidence ?? [])}
      </Space>
    );
  };

  const renderSelectedCreateSnapshot = () => {
    if (!selectedSnapshotId) {
      return <Empty description="请选择一个快照用于草稿试运行" />;
    }
    if (snapshotDetailQuery.isLoading) {
      return <Alert type="info" showIcon message="正在读取快照详情..." />;
    }
    if (snapshotDetailQuery.isError || !snapshotDetailQuery.data?.resources) {
      return <Alert type="error" showIcon message="所选快照详情不可用，请重新选择。" />;
    }
    return (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="已选快照">
            {snapshotDetailQuery.data.snapshotId}
          </Descriptions.Item>
          <Descriptions.Item label="质量状态">
            {snapshotDetailQuery.data.qualityStatus}
          </Descriptions.Item>
          <Descriptions.Item label="绑定包版本">
            {snapshotDetailQuery.data.packageVersion || createPackageVersion || "-"}
          </Descriptions.Item>
          <Descriptions.Item label="缺失字段">
            {snapshotDetailQuery.data.missingFields?.length
              ? `${snapshotDetailQuery.data.missingFields.length} 项`
              : "无"}
          </Descriptions.Item>
        </Descriptions>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          aria-label="运行草稿试运行"
          onClick={handleRunCreatePreview}
          loading={previewRunMutation.isPending || snapshotDetailQuery.isLoading}
          block
        >
          运行草稿试运行
        </Button>
      </Space>
    );
  };

  const renderSelectedCaseSnapshot = () => {
    if (snapshotDetailQuery.isLoading) {
      return <Alert type="info" showIcon message="正在校验所选快照..." />;
    }
    if (snapshotDetailQuery.isError || !snapshotDetailQuery.data?.resources) {
      return <Alert type="error" showIcon message="所选快照详情不可用，请重新选择。" />;
    }
    return (
      <Descriptions bordered column={2} size="small">
        <Descriptions.Item label="已选快照">
          {snapshotDetailQuery.data.snapshotId}
        </Descriptions.Item>
        <Descriptions.Item label="质量状态">
          {snapshotDetailQuery.data.qualityStatus}
        </Descriptions.Item>
        <Descriptions.Item label="绑定包版本">
          {snapshotDetailQuery.data.packageVersion || selectedRulePackageVersion || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="Trace">
          {snapshotDetailQuery.data.traceId || "-"}
        </Descriptions.Item>
      </Descriptions>
    );
  };

  const renderImpactObjectList = (title: string, objects: RuleImpactObject[]) => (
    <Descriptions.Item label={title}>
      {objects.length === 0 ? (
        <Text type="secondary">暂无真实对象</Text>
      ) : (
        <Space direction="vertical" size={2}>
          {objects.map((item) => (
            <Text key={`${item.objectType}-${item.objectId}`}>
              {item.displayName} · {item.impactReason}
            </Text>
          ))}
        </Space>
      )}
    </Descriptions.Item>
  );

  let impactSummaryPanel = (
    <Descriptions bordered column={2} size="small">
      <Descriptions.Item label="影响分析状态">
        {releaseImpactStatus(impactQuery.data)}
      </Descriptions.Item>
      <Descriptions.Item label="影响摘要">
        {impactQuery.data?.impactDigest || <Text type="secondary">未返回</Text>}
      </Descriptions.Item>
      <Descriptions.Item label="规则对象">
        {impactCount(impactQuery.data?.affectedRules)}
      </Descriptions.Item>
      <Descriptions.Item label="路径模板">
        {impactCount(impactQuery.data?.affectedPathways)}
      </Descriptions.Item>
      <Descriptions.Item label="在径患者">
        {impactCount(impactQuery.data?.inPathPatients)}
      </Descriptions.Item>
      <Descriptions.Item label="集成适配器">
        {impactCount(impactQuery.data?.integrationAdapters)}
      </Descriptions.Item>
      {renderImpactObjectList("已定位规则", impactQuery.data?.affectedRules ?? [])}
      {renderImpactObjectList("受影响路径", impactQuery.data?.affectedPathways ?? [])}
      {renderImpactObjectList("在径患者", impactQuery.data?.inPathPatients ?? [])}
      {renderImpactObjectList("集成适配器", impactQuery.data?.integrationAdapters ?? [])}
      <Descriptions.Item label="不可用范围">
        {impactQuery.data?.unavailableScopes?.length ? (
          <Space wrap>
            {impactQuery.data.unavailableScopes.map((scope) => (
              <Tag key={scope}>{scope}</Tag>
            ))}
          </Space>
        ) : (
          <Text type="secondary">无</Text>
        )}
      </Descriptions.Item>
    </Descriptions>
  );
  if (impactQuery.isLoading) {
    impactSummaryPanel = <Alert type="info" showIcon message="正在读取发布影响摘要..." />;
  }
  if (impactQuery.isError) {
    impactSummaryPanel = (
      <Alert
        type="error"
        showIcon
        message="影响摘要读取失败"
        description="发布门禁需要真实影响摘要，请稍后重试。"
      />
    );
  }

  const governance = detailData?.governance;
  const governanceState = governance?.state ?? "DRAFT";
  const governanceStep = Math.max(
    0,
    RULE_GOVERNANCE_STAGES.findIndex((stage) => stage.key === governanceState),
  );
  const governanceNeedsImpact = ["DRAFT", "COMMITTEE", "SHADOW", "CANARY"].includes(
    governanceState,
  );
  let detailAlertMessage = "当前规则处于草稿阶段，可补测试用例和试运行。";
  let detailAlertType: "success" | "warning" | "info" = "info";
  if (governanceState === "RETIRED") {
    detailAlertMessage = "当前规则已退役封存，定义、版本与全部签署证据仅供审计追溯。";
    detailAlertType = "warning";
  } else if (governanceState === "MONITOR") {
    detailAlertMessage = "当前规则已进入运行监测，可查看真实运行证据或执行退役封存。";
    detailAlertType = "success";
  } else if (governanceState !== "DRAFT") {
    detailAlertMessage = `当前治理阶段：${ruleGovernanceLabel(
      governanceState,
    )}。每次操作只推进一个阶段。`;
  }

  const renderGovernanceAction = () => {
    if (!governance) return null;
    const transitionPending = governanceTransitionMutation.isPending;
    const signoffPending = signoffMutation.isPending;
    switch (governance.state) {
      case "DRAFT":
        if (!canWriteRule && !canPublishRule) return null;
        return (
          <Button
            type="primary"
            onClick={() => handleGovernanceTransition("PEER_REVIEW", "规则已提交同行评审")}
            loading={transitionPending}
            disabled={!releaseGate.allPassed || !impactQuery.data?.impactDigest}
          >
            提交同行评审
          </Button>
        );
      case "PEER_REVIEW":
        if (!canSignRule) return null;
        return (
          <Space wrap>
            <Button
              type="primary"
              onClick={() => handleGovernanceSignoff("PEER_REVIEW", "APPROVED")}
              loading={signoffPending}
            >
              同行评审通过
            </Button>
            <Button
              danger
              onClick={() => handleGovernanceSignoff("PEER_REVIEW", "REJECTED")}
              loading={signoffPending}
            >
              退回草稿
            </Button>
          </Space>
        );
      case "COMMITTEE": {
        if (!canSignRule && !canCoordinateRelease) return null;
        const readyForShadow = governance.committeeApprovalCount >= governance.requiredSignoffs;
        return (
          <Space wrap>
            {readyForShadow && canCoordinateRelease && (
              <Button
                type="primary"
                onClick={() => handleGovernanceTransition("SHADOW", "规则已进入影子运行")}
                loading={transitionPending}
                disabled={!impactQuery.data?.impactDigest}
              >
                进入影子运行
              </Button>
            )}
            {!readyForShadow && canSignRule && (
              <Button
                type="primary"
                onClick={() => handleGovernanceSignoff("COMMITTEE", "APPROVED")}
                loading={signoffPending}
              >
                委员会会签通过
              </Button>
            )}
            {canSignRule && (
              <Button
                danger
                onClick={() => handleGovernanceSignoff("COMMITTEE", "REJECTED")}
                loading={signoffPending}
              >
                退回草稿
              </Button>
            )}
          </Space>
        );
      }
      case "SHADOW":
        if (!canCoordinateRelease) return null;
        return (
          <Button
            type="primary"
            onClick={() => handleGovernanceTransition("CANARY", "规则已进入灰度验证")}
            loading={transitionPending}
            disabled={!impactQuery.data?.impactDigest}
          >
            进入灰度验证
          </Button>
        );
      case "CANARY":
        if (!canActivateFull) return null;
        return (
          <Button
            type="primary"
            icon={<CheckCircleOutlined />}
            onClick={() => handleGovernanceTransition("FULL", "规则已完成院级全量激活")}
            loading={transitionPending}
            disabled={!impactQuery.data?.impactDigest}
          >
            院级全量激活
          </Button>
        );
      case "FULL":
        if (!canCoordinateRelease) return null;
        return (
          <Button
            type="primary"
            onClick={() => handleGovernanceTransition("MONITOR", "规则已进入运行监测")}
            loading={transitionPending}
          >
            进入运行监测
          </Button>
        );
      case "MONITOR":
        if (!canCoordinateRelease) return null;
        return (
          <Button
            type="primary"
            danger
            onClick={() => handleGovernanceTransition("RETIRED", "规则已退役封存")}
            loading={transitionPending}
          >
            退役并封存
          </Button>
        );
      case "RETIRED":
        return null;
    }
  };
  const governanceAction = renderGovernanceAction();

  const releaseStepPanel = (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Steps
        current={governanceStep}
        status={governanceState === "RETIRED" ? "finish" : "process"}
        responsive
        items={RULE_GOVERNANCE_STAGES.map((stage) => ({ title: stage.title }))}
      />
      <Descriptions bordered size="small" column={{ xs: 1, md: 3 }}>
        <Descriptions.Item label="当前阶段">
          {ruleGovernanceLabel(governanceState)}
        </Descriptions.Item>
        <Descriptions.Item label="评审轮次">第 {governance?.reviewRound ?? 1} 轮</Descriptions.Item>
        <Descriptions.Item label="委员会会签">
          {governance?.committeeApprovalCount ?? 0} / {governance?.requiredSignoffs ?? 1}
        </Descriptions.Item>
        <Descriptions.Item label="最近说明">{governance?.lastReason || "暂无"}</Descriptions.Item>
      </Descriptions>
      {governanceState === "DRAFT" && (
        <Alert
          type={releaseGate.allPassed ? "success" : "warning"}
          showIcon
          message={
            releaseGate.allPassed
              ? "阳性、阴性、边界、冲突四类测试用例已全绿。"
              : `同行评审门禁未满足：缺少 ${releaseGate.missingTypes.join("、") || "通过结果"}。`
          }
        />
      )}
      {["HIGH", "CRITICAL"].includes(detailData?.definition.riskLevel ?? "") &&
        ["PEER_REVIEW", "COMMITTEE"].includes(governanceState) && (
          <Alert
            type="warning"
            showIcon
            message="高危或红线规则要求两名独立委员会成员会签。"
            description="影响摘要来自规则影响分析接口；路径、在径患者和同步目标只展示后端从关系库定位到的真实对象。"
          />
        )}
      {governanceNeedsImpact && impactSummaryPanel}
      {governanceState === "SHADOW" && (
        <Descriptions bordered size="small" column={{ xs: 1, md: 3 }} title="影子运行统计">
          <Descriptions.Item label="执行总数">
            {shadowStatsQuery.data?.totalExecutions ?? 0}
          </Descriptions.Item>
          <Descriptions.Item label="命中">{shadowStatsQuery.data?.hitCount ?? 0}</Descriptions.Item>
          <Descriptions.Item label="未命中">
            {shadowStatsQuery.data?.missCount ?? 0}
          </Descriptions.Item>
          <Descriptions.Item label="命中率">
            {formatShadowRate(shadowStatsQuery.data?.hitRate)}
          </Descriptions.Item>
          <Descriptions.Item label="误报">
            {shadowStatsQuery.data?.falsePositiveCount ?? 0}
          </Descriptions.Item>
          <Descriptions.Item label="误报率">
            {formatShadowRate(shadowStatsQuery.data?.falsePositiveRate)}
          </Descriptions.Item>
        </Descriptions>
      )}
      {detailData && (
        <Space direction="vertical" size="small" className="mk-full-width">
          <div className={styles.toolbar}>
            <Text strong>历史回测与漂移监测</Text>
            <Space wrap>
              <Button
                aria-label="运行历史回测"
                icon={<FileSearchOutlined />}
                loading={runBacktestMutation.isPending}
                onClick={handleRunBacktest}
                disabled={!canWriteRule || governanceState === "RETIRED"}
              >
                运行历史回测
              </Button>
              <InputNumber
                aria-label="漂移窗口天数"
                min={1}
                max={90}
                value={driftWindowDays}
                onChange={(value) => setDriftWindowDays(Number(value) || 7)}
                addonAfter="天"
              />
              <InputNumber
                aria-label="漂移阈值"
                min={1}
                max={100}
                value={Math.round(driftThreshold * 100)}
                onChange={(value) => setDriftThreshold((Number(value) || 10) / 100)}
                addonAfter="%"
              />
              <Button
                aria-label="记录漂移快照"
                icon={<SyncOutlined />}
                loading={captureDriftMutation.isPending}
                onClick={handleCaptureDriftSnapshot}
                disabled={
                  !canWriteRule ||
                  !backtestQuery.data ||
                  !["FULL", "MONITOR"].includes(governanceState)
                }
              >
                记录漂移快照
              </Button>
            </Space>
          </div>
          <Descriptions bordered size="small" column={{ xs: 1, md: 4 }}>
            <Descriptions.Item label="回测样本">
              {backtestQuery.data?.sampleCount ?? "-"}
            </Descriptions.Item>
            <Descriptions.Item label="灵敏度">
              {formatMetricRate(backtestQuery.data?.sensitivity)}
            </Descriptions.Item>
            <Descriptions.Item label="特异度">
              {formatMetricRate(backtestQuery.data?.specificity)}
            </Descriptions.Item>
            <Descriptions.Item label="准确率">
              {formatMetricRate(backtestQuery.data?.accuracy)}
            </Descriptions.Item>
            <Descriptions.Item label="触发率">
              {formatMetricRate(backtestQuery.data?.fireRate)}
            </Descriptions.Item>
            <Descriptions.Item label="误报样本">
              {backtestQuery.data?.falsePositiveCaseIds?.join("、") || "无"}
            </Descriptions.Item>
            <Descriptions.Item label="漏报样本">
              {backtestQuery.data?.falseNegativeCaseIds?.join("、") || "无"}
            </Descriptions.Item>
            <Descriptions.Item label="漂移状态">
              {renderDriftStatus(driftQuery.data?.status)}
            </Descriptions.Item>
            <Descriptions.Item label="当前命中率">
              {formatMetricRate(driftQuery.data?.currentFireRate)}
            </Descriptions.Item>
            <Descriptions.Item label="基线命中率">
              {formatMetricRate(driftQuery.data?.baselineFireRate)}
            </Descriptions.Item>
            <Descriptions.Item label="漂移差值">
              {formatSignedRate(driftQuery.data?.driftDelta)}
            </Descriptions.Item>
            <Descriptions.Item label="最近回测">
              {formatDateTime(backtestQuery.data?.createdAt)}
            </Descriptions.Item>
            <Descriptions.Item label="最近监测">
              {formatDateTime(driftQuery.data?.createdAt)}
            </Descriptions.Item>
          </Descriptions>
        </Space>
      )}
      {(governance?.signoffs.length ?? 0) > 0 && (
        <Descriptions bordered size="small" column={1} title="签署证据">
          {governance?.signoffs.map((signoff) => (
            <Descriptions.Item
              key={signoff.signoffId}
              label={`${signoff.stage === "PEER_REVIEW" ? "同行评审" : "委员会"} · 第 ${
                signoff.reviewRound
              } 轮`}
            >
              <Space wrap>
                <Tag color={signoff.decision === "APPROVED" ? "green" : "red"}>
                  {signoff.decision === "APPROVED" ? "通过" : "驳回"}
                </Tag>
                <Text>{signoff.signerId}</Text>
                <Text type="secondary">{signoff.reason}</Text>
              </Space>
            </Descriptions.Item>
          ))}
        </Descriptions>
      )}
      {governanceState === "RETIRED" ? (
        <Alert
          type="info"
          showIcon
          message="规则已封存"
          description="内容、版本、评审轮次与签署证据均保留，不提供删除或重新发布入口。"
        />
      ) : (
        <Form layout="vertical">
          <Form.Item label="治理说明" htmlFor="rule-release-reason">
            <TextArea
              id="rule-release-reason"
              rows={3}
              value={releaseReason}
              onChange={(event) => setReleaseReason(event.target.value)}
              placeholder="填写本次评审、会签、发布、监测或退役依据。"
            />
          </Form.Item>
          {governanceAction ?? (
            <Alert
              type="info"
              showIcon
              message="当前账号仅可查看本阶段证据"
              description="可执行动作由规则写入权限、签署角色和发布职责共同决定。"
            />
          )}
        </Form>
      )}
    </Space>
  );

  const detailLayerItems = detailData
    ? [
        {
          key: "l1",
          label: (
            <span>
              <ApartmentOutlined /> L1 模板
            </span>
          ),
          children: (
            <Descriptions bordered column={1}>
              <Descriptions.Item label="创作方式">
                {detailData.definition.authoringMode === "VISUAL"
                  ? "三层编辑器"
                  : detailData.definition.authoringMode}
              </Descriptions.Item>
              <Descriptions.Item label="来源依据">
                {detailData.version?.sourceRef}
              </Descriptions.Item>
              <Descriptions.Item label="变更说明">
                {detailData.version?.changeSummary || "未填写"}
              </Descriptions.Item>
            </Descriptions>
          ),
        },
        {
          key: "l2",
          label: (
            <span>
              <BranchesOutlined /> L2 条件树
            </span>
          ),
          children: detailTree ? (
            <Space direction="vertical" className="mk-full-width">
              <Alert
                type="info"
                showIcon
                message={`当前条件根组为「${(detailRoot?.logic ?? detailTree.logic) === "all" ? "全部满足" : "任一满足"}」，支持任意层级嵌套。`}
              />
              {detailRoot ? (
                renderReadonlyNode(detailRoot)
              ) : (
                <Alert type="warning" showIcon message="条件结构无法解析，请在 L3 专家视图核查。" />
              )}
              {Boolean(detailDsl) && (
                <AuthoringReadablePreview
                  subject="RULE_CONDITION"
                  packageVersion={selectedRulePackageVersion}
                  dsl={detailDsl}
                />
              )}
              <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="适用场景">
                  <Space wrap>
                    {detailTree.applicability.settings.map((setting) => (
                      <Tag key={setting}>{CLINICAL_SETTING_LABELS[setting]}</Tag>
                    ))}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="组织范围">
                  {[
                    ...(detailTree.applicability.orgScope.groupIds ?? []).map(
                      (value) => `集团 ${value}`,
                    ),
                    ...(detailTree.applicability.orgScope.hospitalIds ?? []).map(
                      (value) => `医院 ${value}`,
                    ),
                    ...(detailTree.applicability.orgScope.deptIds ?? []).map(
                      (value) => `科室 ${value}`,
                    ),
                  ].join("、") || "当前租户全部组织"}
                </Descriptions.Item>
                <Descriptions.Item label="生效范围">
                  {detailTree.applicability.effective.from ?? "立即生效"} 至{" "}
                  {detailTree.applicability.effective.to ?? "长期有效"} · 灰度{" "}
                  {detailTree.applicability.effective.rolloutPercent}%
                </Descriptions.Item>
                <Descriptions.Item label="人群条件">
                  {detailTree.applicability.population.include ? "已配置纳入条件" : "全人群"}
                  {detailTree.applicability.population.exclude ? " · 已配置排除条件" : ""}
                </Descriptions.Item>
                <Descriptions.Item label="命中动作">
                  <Space wrap>
                    {detailTree.actions.map((action, index) => (
                      <Tag key={`${action.actionCode}-${index}`} color={action.indicator}>
                        {action.summary} · {RISK_LABELS[action.atSeverity]}
                      </Tag>
                    ))}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="解释摘要">
                  {detailTree.explanationSummary}
                </Descriptions.Item>
              </Descriptions>
            </Space>
          ) : (
            <Alert
              type="warning"
              showIcon
              message="该版本 DSL 无法无损还原为当前 L2 条件树，请在 L3 专家视图核查。"
            />
          ),
        },
        ...(detailExpertMode
          ? [
              {
                key: "l3",
                label: (
                  <span>
                    <CodeOutlined /> L3 DSL
                  </span>
                ),
                children: (
                  <Space direction="vertical" className="mk-full-width">
                    <Row gutter={16}>
                      <Col span={12}>
                        <div className={`${styles.codePanel} ${styles.codeText}`}>
                          {detailDsl ? formatRuleJson(detailDsl) : "当前版本 DSL 无法解析"}
                        </div>
                      </Col>
                      <Col span={12}>
                        <div className={`${styles.codePanel} ${styles.codeText}`}>
                          {detailExplanation
                            ? formatRuleJson(detailExplanation)
                            : "当前版本解释模板为空或无法解析"}
                        </div>
                      </Col>
                    </Row>
                    {Boolean(detailDsl) && (
                      <AuthoringReadablePreview
                        subject="RULE_CONDITION"
                        packageVersion={selectedRulePackageVersion}
                        dsl={detailDsl}
                      />
                    )}
                  </Space>
                ),
              },
            ]
          : []),
        {
          key: "cases",
          label: (
            <span>
              <InfoCircleOutlined /> 发布门禁测试用例 ({detailData.testCases.length})
            </span>
          ),
          children: (
            <>
              <div className={`${styles.toolbar} ${styles.marginBottomMd}`}>
                <Text type="secondary">
                  发布前必须覆盖阳性、阴性、边界和冲突用例，且所有用例通过。
                </Text>
                <Space>
                  {detailData.testCases.length > 0 && detailData.governance.state !== "RETIRED" && (
                    <Button
                      icon={<PlayCircleOutlined />}
                      loading={runRuleTestsMutation.isPending}
                      onClick={handleRunRuleTests}
                    >
                      执行全部用例
                    </Button>
                  )}
                  {detailData.governance.state === "DRAFT" && (
                    <Button
                      icon={<PlusOutlined />}
                      onClick={() => {
                        caseForm.setFieldsValue({
                          expectedHit: true,
                          expectedSeverity: "LOW",
                          expectedActionCode: "REMIND",
                          caseType: "POSITIVE",
                        });
                        setCaseModalVisible(true);
                      }}
                    >
                      新增测试用例
                    </Button>
                  )}
                </Space>
              </div>

              <Table
                dataSource={detailData.testCases}
                rowKey="caseId"
                pagination={false}
                columns={[
                  {
                    title: "用例类别",
                    dataIndex: "caseType",
                    key: "caseType",
                    render: (t) => <Tag color="blue">{t}</Tag>,
                  },
                  {
                    title: "来源快照",
                    dataIndex: "contextSnapshotId",
                    key: "contextSnapshotId",
                    render: (value: string) => <Text code>{value}</Text>,
                  },
                  {
                    title: "期望命中",
                    dataIndex: "expectedHit",
                    key: "expectedHit",
                    render: (val: boolean) => (val ? "应该触发" : "不该触发"),
                  },
                  {
                    title: "期望严重度",
                    dataIndex: "expectedSeverity",
                    key: "expectedSeverity",
                  },
                  {
                    title: "最新执行结果",
                    key: "lastStatus",
                    render: (_, row) => {
                      if (!row.lastStatus || row.lastStatus === "NOT_RUN") {
                        return <Badge status="default" text="待执行" />;
                      }
                      return row.lastStatus === "PASS" ? (
                        <span className={styles.successText}>
                          <CheckCircleOutlined className={styles.iconGap} /> 通过
                        </span>
                      ) : (
                        <Tooltip title={row.lastMessage}>
                          <span className={styles.errorText}>
                            <CloseCircleOutlined className={styles.iconGap} />
                            {row.lastStatus === "ERROR" ? "执行错误" : "未通过"}
                          </span>
                        </Tooltip>
                      );
                    },
                  },
                  {
                    title: "执行时间",
                    dataIndex: "lastRunAt",
                    key: "lastRunAt",
                    render: (val) => (val ? new Date(val).toLocaleTimeString() : "-"),
                  },
                ]}
              />
            </>
          ),
        },
        {
          key: "simulate",
          label: (
            <span>
              <PlayCircleOutlined /> 真实快照试运行
            </span>
          ),
          children: (
            <Space direction="vertical" size="large" className="mk-full-width">
              <Alert
                type="info"
                showIcon
                message="默认从标准上下文快照接口读取真实脱敏快照；页面不内置示例病例。"
              />
              <Row gutter={16}>
                <Col span={12}>
                  <Form layout="vertical">
                    <Row gutter={12}>
                      <Col span={12}>
                        <Form.Item label="患者 ID" htmlFor="rule-snapshot-patient-id">
                          <Input
                            id="rule-snapshot-patient-id"
                            value={snapshotPatientId}
                            onChange={(event) => setSnapshotPatientId(event.target.value)}
                            placeholder="输入患者主索引"
                          />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item label="就诊 ID" htmlFor="rule-snapshot-encounter-id">
                          <Input
                            id="rule-snapshot-encounter-id"
                            value={snapshotEncounterId}
                            onChange={(event) => setSnapshotEncounterId(event.target.value)}
                            placeholder="输入就诊号"
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Button
                      icon={<FileSearchOutlined />}
                      aria-label="读取真实快照"
                      onClick={handleSnapshotSearch}
                    >
                      读取真实快照
                    </Button>
                  </Form>
                  <div className={styles.marginTopMd}>{renderSnapshotChoices()}</div>
                </Col>
                <Col span={12}>{renderSelectedSnapshotDetail()}</Col>
              </Row>

              {simulateResult ? (
                <div className={styles.resultPanel}>
                  <Descriptions column={1} size="small" bordered className={styles.marginBottomMd}>
                    <Descriptions.Item label="规则是否命中">
                      {simulateResult.hit ? (
                        <Tag color="red">命中</Tag>
                      ) : (
                        <Tag color="green">未命中</Tag>
                      )}
                    </Descriptions.Item>
                    {simulateResult.hit && (
                      <>
                        <Descriptions.Item label="动作卡">
                          <Space wrap>
                            {simulateResult.actions.map((action, index) => (
                              <Tag key={`${action.actionCode}-${index}`} color={action.indicator}>
                                {action.summary} · {action.actionCode}
                              </Tag>
                            ))}
                          </Space>
                        </Descriptions.Item>
                        <Descriptions.Item label="最高严重等级">
                          {simulateResult.severity}
                        </Descriptions.Item>
                        <Descriptions.Item label="确认要求">
                          {simulateResult.actions.some(
                            (action) => action.requiresPhysicianConfirmation,
                          ) ? (
                            <Tag color="red">必须医师确认</Tag>
                          ) : (
                            <Tag>无需额外确认</Tag>
                          )}
                        </Descriptions.Item>
                      </>
                    )}
                  </Descriptions>
                  <Text strong>详细决策动作说明</Text>
                  <div className={styles.explanation}>
                    {formatEvaluationExplanation(simulateResult.explanation)}
                  </div>
                </div>
              ) : (
                <Empty description="选择真实快照并试运行后展示命中与解释" />
              )}
            </Space>
          ),
        },
        {
          key: "release",
          label: (
            <span>
              <DeploymentUnitOutlined /> 治理与发布
            </span>
          ),
          children: releaseStepPanel,
        },
      ]
    : [];

  const createLayerItems = [
    {
      key: "l1",
      label: (
        <span>
          <ApartmentOutlined /> L1 模板
        </span>
      ),
      children: (
        <>
          <Alert
            type="info"
            showIcon
            message="模板只提供规则结构，不内置患者、药品、诊断或剂量常量；提交前必须在 L2 填写真实上下文字段。"
            className={styles.marginBottomMd}
          />
          <Radio.Group
            value={selectedTemplateKey}
            onChange={(event: RadioChangeEvent) =>
              applyTemplate(event.target.value as RuleTemplateKey)
            }
          >
            <Space direction="vertical" className="mk-full-width">
              {RULE_LAYER_TEMPLATES.map((template) => (
                <Radio key={template.key} value={template.key} aria-label={template.title}>
                  <Space direction="vertical" size={0}>
                    <Text strong>{template.title}</Text>
                    <Text type="secondary">{template.description}</Text>
                  </Space>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
          {selectedTemplateKey === "critical_value_report" && (
            <div className={styles.editorSection}>
              <Row gutter={16}>
                <Col xs={24} md={6}>
                  <Form.Item label="检验项编码" htmlFor="critical-observation-code">
                    <Input
                      id="critical-observation-code"
                      value={criticalObservationCode}
                      onChange={(event) => updateCriticalObservationCode(event.target.value)}
                      placeholder="如 K"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item label="检验结果字段" htmlFor="critical-value-field">
                    <AutoComplete
                      id="critical-value-field"
                      value={firstCondition?.fact ?? ""}
                      options={fieldCatalogOptions}
                      filterOption={(input, option) => {
                        const leaf = option as { value?: string; label?: string } | undefined;
                        const haystack = `${leaf?.value ?? ""} ${leaf?.label ?? ""}`.toLowerCase();
                        return haystack.includes(input.toLowerCase());
                      }}
                      onSelect={updateCriticalValueField}
                      onChange={updateCriticalValueField}
                      placeholder="如 observations[].valueNumeric"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item label="危急阈值" htmlFor="critical-threshold">
                    <InputNumber
                      id="critical-threshold"
                      value={criticalThresholdValue}
                      onChange={updateCriticalThreshold}
                      className="mk-full-width"
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} md={6}>
                  <Form.Item label="回报时限分钟" htmlFor="critical-return-minutes">
                    <InputNumber
                      id="critical-return-minutes"
                      min={1}
                      max={1440}
                      precision={0}
                      value={criticalReturnMinutes}
                      onChange={updateCriticalReturnMinutes}
                      className="mk-full-width"
                    />
                  </Form.Item>
                </Col>
              </Row>
            </div>
          )}
        </>
      ),
    },
    {
      key: "l2",
      label: (
        <span>
          <BranchesOutlined /> L2 条件树
        </span>
      ),
      children: (
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Form.Item label="解释摘要">
            <Input
              value={conditionTree.explanationSummary}
              onChange={(event) =>
                setConditionTree((current) => ({
                  ...current,
                  explanationSummary: event.target.value,
                }))
              }
            />
          </Form.Item>

          <Collapse
            items={[
              {
                key: "applicability",
                label: "适用域与生效",
                children: (
                  <Space direction="vertical" size="small" className="mk-full-width">
                    <Row gutter={12}>
                      <Col xs={24} md={12}>
                        <Form.Item label="临床场景" htmlFor="rule-applicability-settings" required>
                          <Select
                            id="rule-applicability-settings"
                            mode="multiple"
                            value={conditionTree.applicability.settings}
                            options={Object.entries(CLINICAL_SETTING_LABELS).map(
                              ([value, label]) => ({
                                value: value as RuleClinicalSetting,
                                label,
                              }),
                            )}
                            onChange={(settings: RuleClinicalSetting[]) => {
                              if (settings.length === 0) {
                                message.error("至少保留一个临床场景。");
                                return;
                              }
                              updateApplicability((current) => ({ ...current, settings }));
                            }}
                            className="mk-full-width"
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="灰度比例" htmlFor="rule-applicability-rollout">
                          <InputNumber
                            id="rule-applicability-rollout"
                            min={0}
                            max={100}
                            precision={0}
                            addonAfter="%"
                            value={conditionTree.applicability.effective.rolloutPercent}
                            onChange={(value) =>
                              updateApplicability((current) => ({
                                ...current,
                                effective: {
                                  ...current.effective,
                                  rolloutPercent: value ?? 0,
                                },
                              }))
                            }
                            className="mk-full-width"
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={12}>
                      <Col xs={24} md={8}>
                        <Form.Item label="集团范围" htmlFor="rule-applicability-groups">
                          <Select
                            id="rule-applicability-groups"
                            mode="multiple"
                            showSearch
                            filterOption={false}
                            allowClear
                            maxTagCount={1}
                            placeholder="选择适用集团"
                            value={conditionTree.applicability.orgScope.groupIds ?? []}
                            options={orgOptions("GROUP", groupOrgUnitsQuery.data?.items)}
                            loading={groupOrgUnitsQuery.isLoading}
                            notFoundContent={
                              groupOrgUnitsQuery.isError ? "组织目录读取失败" : "暂无可用集团"
                            }
                            onSearch={(value) =>
                              setOrgSearch((current) => ({ ...current, GROUP: value.trim() }))
                            }
                            onChange={(groupIds: string[]) =>
                              updateOrgScope(
                                "GROUP",
                                "groupIds",
                                groupIds,
                                groupOrgUnitsQuery.data?.items,
                              )
                            }
                            className="mk-full-width"
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={8}>
                        <Form.Item label="医院范围" htmlFor="rule-applicability-hospitals">
                          <Select
                            id="rule-applicability-hospitals"
                            mode="multiple"
                            showSearch
                            filterOption={false}
                            allowClear
                            maxTagCount={1}
                            placeholder="选择适用医院"
                            value={conditionTree.applicability.orgScope.hospitalIds ?? []}
                            options={orgOptions("HOSPITAL", hospitalOrgUnitsQuery.data?.items)}
                            loading={hospitalOrgUnitsQuery.isLoading}
                            notFoundContent={
                              hospitalOrgUnitsQuery.isError ? "组织目录读取失败" : "暂无可用医院"
                            }
                            onSearch={(value) =>
                              setOrgSearch((current) => ({ ...current, HOSPITAL: value.trim() }))
                            }
                            onChange={(hospitalIds: string[]) =>
                              updateOrgScope(
                                "HOSPITAL",
                                "hospitalIds",
                                hospitalIds,
                                hospitalOrgUnitsQuery.data?.items,
                              )
                            }
                            className="mk-full-width"
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={8}>
                        <Form.Item label="科室范围" htmlFor="rule-applicability-depts">
                          <Select
                            id="rule-applicability-depts"
                            mode="multiple"
                            showSearch
                            filterOption={false}
                            allowClear
                            maxTagCount={1}
                            placeholder="选择适用科室"
                            value={conditionTree.applicability.orgScope.deptIds ?? []}
                            options={orgOptions("DEPARTMENT", departmentOrgUnitsQuery.data?.items)}
                            loading={departmentOrgUnitsQuery.isLoading}
                            notFoundContent={
                              departmentOrgUnitsQuery.isError ? "组织目录读取失败" : "暂无可用科室"
                            }
                            onSearch={(value) =>
                              setOrgSearch((current) => ({ ...current, DEPARTMENT: value.trim() }))
                            }
                            onChange={(deptIds: string[]) =>
                              updateOrgScope(
                                "DEPARTMENT",
                                "deptIds",
                                deptIds,
                                departmentOrgUnitsQuery.data?.items,
                              )
                            }
                            className="mk-full-width"
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={12}>
                      <Col xs={24} md={12}>
                        <Form.Item label="生效日期" htmlFor="rule-applicability-from">
                          <Input
                            id="rule-applicability-from"
                            type="date"
                            value={conditionTree.applicability.effective.from ?? ""}
                            onChange={(event) =>
                              updateApplicability((current) => ({
                                ...current,
                                effective: {
                                  ...current.effective,
                                  from: event.target.value || undefined,
                                },
                              }))
                            }
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} md={12}>
                        <Form.Item label="失效日期" htmlFor="rule-applicability-to">
                          <Input
                            id="rule-applicability-to"
                            type="date"
                            value={conditionTree.applicability.effective.to ?? ""}
                            onChange={(event) =>
                              updateApplicability((current) => ({
                                ...current,
                                effective: {
                                  ...current.effective,
                                  to: event.target.value || undefined,
                                },
                              }))
                            }
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                    {renderPopulationConditionEditor("include", populationIncludeTree)}
                    {renderPopulationConditionEditor("exclude", populationExcludeTree)}
                  </Space>
                ),
              },
            ]}
          />

          <Alert
            type="info"
            showIcon
            message="临床算子"
            description="区间比较、单位换算、时间窗持续/趋势和 eGFR/CrCl/BSA 受控公式均可在 L2 结构化配置；支持任意层级「条件组 + 子条件组」嵌套；L3 JSON 仅保留给专家核查。"
          />

          <div className={styles.conditionCard}>
            <div className={styles.conditionHeader}>
              <Space direction="vertical" size={0}>
                <Text strong>条件片段</Text>
                <Text type="secondary">
                  选择同包版本的 ACTIVE 片段，可引用联动或拷贝为本地条件。
                </Text>
              </Space>
              <Tag color={currentCreatePackageVersion ? "green" : "default"}>
                {currentCreatePackageVersion || "待填写包版本"}
              </Tag>
            </div>
            <Space wrap className="mk-full-width">
              <Select
                aria-label="选择条件片段"
                showSearch
                allowClear
                value={selectedConditionFragment ? selectedFragmentId : undefined}
                loading={activeConditionFragmentsQuery.isLoading}
                disabled={!currentCreatePackageVersion || activeConditionFragmentsQuery.isError}
                options={conditionFragmentOptions}
                optionFilterProp="label"
                placeholder={
                  currentCreatePackageVersion ? "选择可复用条件片段" : "先填写标准上下文包版本"
                }
                className={styles.fragmentSelect}
                onChange={(value) => setSelectedFragmentId(value)}
              />
              <Button
                icon={<BranchesOutlined />}
                disabled={!selectedConditionFragment}
                onClick={() => applyConditionFragment("reference")}
              >
                引用
              </Button>
              <Button
                icon={<PlusOutlined />}
                disabled={!selectedConditionFragment}
                onClick={() => applyConditionFragment("copy")}
              >
                拷贝
              </Button>
              <Button icon={<FileSearchOutlined />} onClick={openFragmentLibrary}>
                片段库
              </Button>
            </Space>
            {!currentCreatePackageVersion && (
              <Text type="secondary">标准上下文包版本用于约束片段、规则与运行期求值一致。</Text>
            )}
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
            {currentCreatePackageVersion &&
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

          {renderConditionGroup(conditionRoot, 0)}

          <Space direction="vertical" size="middle" className="mk-full-width">
            <div className={styles.conditionHeader}>
              <Space direction="vertical" size={0}>
                <Text strong>命中动作卡</Text>
                <Text type="secondary">按风险级别输出可审阅卡片，不直接执行医嘱。</Text>
              </Space>
              <Button icon={<PlusOutlined />} aria-label="添加动作" onClick={addAction}>
                添加动作
              </Button>
            </div>

            {conditionTree.actions.map((action, actionIndex) => (
              <div
                key={`action-${actionIndex}`}
                className={styles.formSection}
                data-testid={`rule-action-${actionIndex}`}
              >
                <div className={styles.conditionHeader}>
                  <Space>
                    <Tag color={action.indicator}>动作 {actionIndex + 1}</Tag>
                    <Text strong>{action.summary || "待填写动作摘要"}</Text>
                  </Space>
                  <Tooltip
                    title={conditionTree.actions.length === 1 ? "至少保留一个动作" : "删除动作"}
                  >
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`删除动作 ${actionIndex + 1}`}
                      disabled={conditionTree.actions.length === 1}
                      onClick={() => removeAction(actionIndex)}
                    />
                  </Tooltip>
                </div>

                <Row gutter={12}>
                  <Col xs={24} md={8}>
                    <Form.Item label="动作类型" htmlFor={`action-code-${actionIndex}`}>
                      <Select
                        id={`action-code-${actionIndex}`}
                        value={action.actionCode}
                        onChange={(value: RuleActionCode) =>
                          updateAction(actionIndex, { actionCode: value })
                        }
                        className="mk-full-width"
                      >
                        <Option value="INFO">信息提示</Option>
                        <Option value="REMIND">一般提醒</Option>
                        <Option value="STRONG_REMINDER">强提醒</Option>
                        <Option value="BLOCK">阻断确认</Option>
                        <Option value="SUGGEST_ORDER">建议医嘱</Option>
                        <Option value="AUTO_DOCUMENT">自动留痕</Option>
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item label="风险级别" htmlFor={`action-severity-${actionIndex}`}>
                      <Select
                        id={`action-severity-${actionIndex}`}
                        value={action.atSeverity}
                        onChange={(value: RuleSeverity) =>
                          updateAction(actionIndex, { atSeverity: value })
                        }
                        className="mk-full-width"
                      >
                        <Option value="LOW">低风险</Option>
                        <Option value="MEDIUM">中风险</Option>
                        <Option value="HIGH">高风险</Option>
                        <Option value="CRITICAL">红线</Option>
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item label="卡片指示" htmlFor={`action-indicator-${actionIndex}`}>
                      <Select
                        id={`action-indicator-${actionIndex}`}
                        value={action.indicator}
                        onChange={(value: RuleIndicator) =>
                          updateAction(actionIndex, { indicator: value })
                        }
                        className="mk-full-width"
                      >
                        <Option value="info">信息</Option>
                        <Option value="warning">警示</Option>
                        <Option value="critical">严重</Option>
                      </Select>
                    </Form.Item>
                  </Col>
                </Row>

                <Row gutter={12}>
                  <Col xs={24} md={10}>
                    <Form.Item label="卡片摘要" htmlFor={`action-summary-${actionIndex}`}>
                      <Input
                        id={`action-summary-${actionIndex}`}
                        value={action.summary}
                        onChange={(event) =>
                          updateAction(actionIndex, { summary: event.target.value })
                        }
                      />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={14}>
                    <Form.Item label="详细说明" htmlFor={`action-detail-${actionIndex}`}>
                      <Input
                        id={`action-detail-${actionIndex}`}
                        value={action.detail}
                        onChange={(event) =>
                          updateAction(actionIndex, { detail: event.target.value })
                        }
                      />
                    </Form.Item>
                  </Col>
                </Row>

                <Row gutter={12}>
                  <Col xs={24} md={10}>
                    <Form.Item label="依据名称" htmlFor={`action-source-${actionIndex}`}>
                      <Input
                        id={`action-source-${actionIndex}`}
                        value={action.source.label}
                        onChange={(event) =>
                          updateAction(actionIndex, {
                            source: { ...action.source, label: event.target.value },
                          })
                        }
                      />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item label="依据链接" htmlFor={`action-source-url-${actionIndex}`}>
                      <Input
                        id={`action-source-url-${actionIndex}`}
                        value={action.source.url}
                        onChange={(event) =>
                          updateAction(actionIndex, {
                            source: { ...action.source, url: event.target.value || undefined },
                          })
                        }
                      />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={6}>
                    <Form.Item label="证据等级" htmlFor={`action-evidence-${actionIndex}`}>
                      <Input
                        id={`action-evidence-${actionIndex}`}
                        value={action.source.evidenceLevel}
                        onChange={(event) =>
                          updateAction(actionIndex, {
                            source: {
                              ...action.source,
                              evidenceLevel: event.target.value || undefined,
                            },
                          })
                        }
                      />
                    </Form.Item>
                  </Col>
                </Row>

                <Row gutter={12} align="middle">
                  <Col xs={24} md={18}>
                    <Form.Item label="可覆盖理由" htmlFor={`action-reasons-${actionIndex}`}>
                      <Select
                        id={`action-reasons-${actionIndex}`}
                        mode="tags"
                        value={action.overrideReasons}
                        onChange={(value: string[]) =>
                          updateAction(actionIndex, { overrideReasons: value })
                        }
                        tokenSeparators={[",", "，"]}
                        className="mk-full-width"
                      />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={6}>
                    <Form.Item label="要求医师确认">
                      <Switch
                        checked={action.requiresPhysicianConfirmation}
                        disabled={requiresPhysicianConfirmation(
                          action.actionCode,
                          action.atSeverity,
                        )}
                        onChange={(checked) =>
                          updateAction(actionIndex, {
                            requiresPhysicianConfirmation: checked,
                          })
                        }
                      />
                    </Form.Item>
                  </Col>
                </Row>

                <div className={styles.conditionHeader}>
                  <Text strong>建议项</Text>
                  <Button
                    size="small"
                    icon={<PlusOutlined />}
                    aria-label={`为动作 ${actionIndex + 1} 添加建议`}
                    onClick={() => addSuggestion(actionIndex)}
                  >
                    添加建议
                  </Button>
                </div>
                {action.suggestions.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无建议项" />
                ) : (
                  <Space direction="vertical" size="small" className="mk-full-width">
                    {action.suggestions.map((suggestion, suggestionIndex) => (
                      <div
                        key={`suggestion-${suggestionIndex}`}
                        className={styles.suggestionSection}
                      >
                        <div className={styles.conditionHeader}>
                          <Text strong>建议 {suggestionIndex + 1}</Text>
                          <Tooltip title="删除建议">
                            <Button
                              type="text"
                              danger
                              icon={<DeleteOutlined />}
                              aria-label={`删除建议 ${suggestionIndex + 1}`}
                              onClick={() => removeSuggestion(actionIndex, suggestionIndex)}
                            />
                          </Tooltip>
                        </div>
                        <Row gutter={12}>
                          <Col xs={24} md={12}>
                            <Form.Item label="建议名称">
                              <Input
                                value={suggestion.label}
                                onChange={(event) =>
                                  updateSuggestion(actionIndex, suggestionIndex, {
                                    label: event.target.value,
                                  })
                                }
                              />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="建议类型">
                              <Input
                                value={suggestion.actionType}
                                onChange={(event) =>
                                  updateSuggestion(actionIndex, suggestionIndex, {
                                    actionType: event.target.value,
                                  })
                                }
                              />
                            </Form.Item>
                          </Col>
                        </Row>
                        <div className={styles.conditionHeader}>
                          <Text type="secondary">建议参数</Text>
                          <Button
                            type="text"
                            size="small"
                            icon={<PlusOutlined />}
                            onClick={() => addSuggestionPayload(actionIndex, suggestionIndex)}
                          >
                            添加参数
                          </Button>
                        </div>
                        <Space direction="vertical" size="small" className="mk-full-width">
                          {Object.entries(suggestion.payload ?? {}).map(([key, value]) => (
                            <Space key={key} className="mk-full-width" align="start">
                              <Input
                                aria-label="参数键"
                                value={key}
                                onChange={(event) =>
                                  updateSuggestionPayload(
                                    actionIndex,
                                    suggestionIndex,
                                    key,
                                    event.target.value,
                                    String(value ?? ""),
                                  )
                                }
                              />
                              <Input
                                aria-label="参数值"
                                value={String(value ?? "")}
                                onChange={(event) =>
                                  updateSuggestionPayload(
                                    actionIndex,
                                    suggestionIndex,
                                    key,
                                    key,
                                    event.target.value,
                                  )
                                }
                              />
                              <Button
                                type="text"
                                danger
                                icon={<DeleteOutlined />}
                                aria-label={`删除参数 ${key}`}
                                onClick={() =>
                                  removeSuggestionPayload(actionIndex, suggestionIndex, key)
                                }
                              />
                            </Space>
                          ))}
                        </Space>
                      </div>
                    ))}
                  </Space>
                )}
              </div>
            ))}
          </Space>

          <AuthoringReadablePreview
            subject="RULE_CONDITION"
            packageVersion={createPackageVersion}
            dsl={createRulePreviewDsl}
          />

          <Space>
            <Button
              type="primary"
              icon={<SyncOutlined />}
              aria-label="同步到 DSL"
              onClick={syncTreeToDsl}
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
          </Space>
        </Space>
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
        <Row gutter={16}>
          <Col xs={24} lg={10}>
            <Space direction="vertical" size="middle" className="mk-full-width">
              <div className={styles.formSection}>
                <Row gutter={12}>
                  <Col xs={24} md={12}>
                    <Form.Item label="患者 ID" htmlFor="rule-create-snapshot-patient-id">
                      <Input
                        id="rule-create-snapshot-patient-id"
                        value={snapshotPatientId}
                        onChange={(event) => setSnapshotPatientId(event.target.value)}
                        placeholder="输入患者主索引"
                      />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={12}>
                    <Form.Item label="就诊 ID" htmlFor="rule-create-snapshot-encounter-id">
                      <Input
                        id="rule-create-snapshot-encounter-id"
                        value={snapshotEncounterId}
                        onChange={(event) => setSnapshotEncounterId(event.target.value)}
                        placeholder="输入就诊号"
                      />
                    </Form.Item>
                  </Col>
                </Row>
                <Button
                  icon={<FileSearchOutlined />}
                  aria-label="读取真实快照"
                  onClick={handleSnapshotSearch}
                  loading={snapshotsQuery.isLoading}
                  block
                >
                  读取真实快照
                </Button>
              </div>
              {renderSnapshotChoices()}
            </Space>
          </Col>
          <Col xs={24} lg={14}>
            <Space direction="vertical" size="middle" className="mk-full-width">
              {renderSelectedCreateSnapshot()}
              {renderPreviewRunResult(createPreviewRunResult)}
            </Space>
          </Col>
        </Row>
      ),
    },
    ...(createExpertMode
      ? [
          {
            key: "l3",
            label: (
              <span>
                <CodeOutlined /> L3 DSL
              </span>
            ),
            children: (
              <>
                <Alert
                  type="warning"
                  showIcon
                  message="L3 是专家模式，保存前必须能回填到 L2 条件树；不允许提交无法解释的裸 DSL。"
                  className={styles.marginBottomMd}
                />
                <Form.Item label="规则 DSL JSON" htmlFor="ruleDslJson">
                  <TextArea
                    id="ruleDslJson"
                    rows={16}
                    value={dslEditorValue}
                    onChange={(event) => setDslEditorValue(event.target.value)}
                    className={styles.codeText}
                  />
                </Form.Item>
                <AuthoringReadablePreview
                  subject="RULE_CONDITION"
                  packageVersion={createPackageVersion}
                  dsl={createRulePreviewDslFromL3}
                />
                <Space>
                  <Button
                    icon={<BranchesOutlined />}
                    aria-label="回填到条件树"
                    onClick={syncDslToTree}
                  >
                    回填到条件树
                  </Button>
                  <Button
                    icon={<FileSearchOutlined />}
                    aria-label="重新生成 DSL"
                    onClick={syncTreeToDsl}
                  >
                    重新生成 DSL
                  </Button>
                </Space>
              </>
            ),
          },
        ]
      : []),
  ];

  const pageState = listIsError ? "error" : "ready";

  return (
    <PageShell
      title="规则中枢"
      description="配置规则资产，完成测试、解释和临床治理。"
      primary={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          新建规则模板
        </Button>
      }
      state={pageState}
      stateProps={{
        title: "规则列表读取失败",
        description: getApiErrorMessage(listError, "请稍后重试，或联系信息科核查规则 API。"),
        onRetry: refetchList,
      }}
    >
      <div className={`${styles.surface} ${styles.filterSurface}`}>
        <Form layout="inline" className={styles.inlineForm}>
          <Form.Item label="状态">
            <Select
              placeholder="全部状态"
              allowClear
              value={statusFilter}
              onChange={setStatusFilter}
              className={styles.controlSm}
            >
              <Option value="DRAFT">草稿设计中</Option>
              <Option value="PUBLISHED">内容已审核</Option>
              <Option value="OFFLINE">已下线封存</Option>
            </Select>
          </Form.Item>
          <Form.Item label="类别">
            <Select
              placeholder="全部类别"
              allowClear
              value={typeFilter}
              onChange={setTypeFilter}
              className={styles.controlMd}
            >
              {RULE_TYPE_OPTIONS.map((option) => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label="风险评级">
            <Select
              placeholder="全部评级"
              allowClear
              value={riskFilter}
              onChange={setRiskFilter}
              className={styles.controlXs}
            >
              <Option value="LOW">低风险</Option>
              <Option value="MEDIUM">中风险</Option>
              <Option value="HIGH">高风险</Option>
              <Option value="CRITICAL">红线规则</Option>
            </Select>
          </Form.Item>
        </Form>
      </div>

      <div className={styles.surface}>
        <Table
          columns={columns}
          dataSource={listData?.items || []}
          rowKey="ruleId"
          loading={listLoading}
          locale={{ emptyText: "当前筛选条件下暂无规则资产，可从右上角创建草稿。" }}
          pagination={{
            current: page,
            pageSize: size,
            total: listData?.total || 0,
            onChange: (p) => setPage(p),
            showTotal: (t) => `共 ${t} 个受控规则实体`,
          }}
          className="medkernel-table"
        />
      </div>

      <Drawer
        title={
          <div className={styles.drawerTitle}>
            <span>规则配置详情与试运行</span>
            {detailData && detailData.governance.state !== "RETIRED" && (
              <Button type="primary" onClick={() => setActiveDetailLayer("release")}>
                进入治理流
              </Button>
            )}
          </div>
        }
        width="min(61.25rem, 100vw)"
        onClose={() => {
          setSelectedRuleId(null);
          setActiveDetailLayer("l2");
          setSelectedSnapshotId("");
          setSnapshotSearchParams(null);
          setSimulateResult(null);
          setReleaseReason("");
          setDetailExpertMode(false);
        }}
        open={!!selectedRuleId}
        loading={detailLoading}
        destroyOnClose
      >
        {detailData && (
          <Space direction="vertical" size="large" className="mk-full-width">
            <Alert message={detailAlertMessage} type={detailAlertType} showIcon />

            <Descriptions title="基本元数据" bordered column={2}>
              <Descriptions.Item label="规则编码">
                {detailData.definition.ruleCode}
              </Descriptions.Item>
              <Descriptions.Item label="名称">{detailData.definition.name}</Descriptions.Item>
              <Descriptions.Item label="类型">
                {RULE_TYPE_LABELS[detailData.definition.ruleType]}
              </Descriptions.Item>
              <Descriptions.Item label="风险级别">
                {renderRiskTag(detailData.definition.riskLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {renderStatus(detailData.definition.status)}
              </Descriptions.Item>
              <Descriptions.Item label="部署状态">
                {renderDeploymentStatus(detailData.deploymentStatus)}
              </Descriptions.Item>
              <Descriptions.Item label="执行优先级">
                {detailData.definition.priority}
              </Descriptions.Item>
              <Descriptions.Item label="显式抑制来源">
                {detailData.definition.suppressedBy || "未配置"}
              </Descriptions.Item>
              <Descriptions.Item label="同患者去重窗口">
                {detailData.definition.dedupeWindowSeconds > 0
                  ? `${Math.round(detailData.definition.dedupeWindowSeconds / 60)} 分钟`
                  : "不去重"}
              </Descriptions.Item>
              <Descriptions.Item label="当前版本">
                {detailData.version?.versionNo || 0} 版
              </Descriptions.Item>
            </Descriptions>

            <Space className="mk-flex-between mk-full-width">
              <Text type="secondary">技术 DSL 与解释模板默认隐藏。</Text>
              <Space>
                <Text>专家模式</Text>
                <Switch
                  aria-label="专家模式"
                  checked={detailExpertMode}
                  onChange={toggleDetailExpertMode}
                />
              </Space>
            </Space>

            <Tabs
              activeKey={activeDetailLayer}
              onChange={(key) => setActiveDetailLayer(key as DetailLayerKey)}
              items={detailLayerItems}
            />
          </Space>
        )}
      </Drawer>

      <Modal
        title="创建新临床规则"
        open={createModalVisible}
        onOk={handleCreateRule}
        onCancel={() => {
          setCreateModalVisible(false);
          setCreatePreviewRunResult(null);
        }}
        width="min(920px, calc(100vw - 32px))"
        okText="创建草稿"
        cancelText="取消"
        confirmLoading={createRuleMutation.isPending}
        forceRender
      >
        <Form form={createForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="ruleCode"
                label="规则唯一业务编码"
                rules={[{ required: true, message: "请输入编码，同租户下不可重复" }]}
              >
                <Input placeholder="输入规则业务编码" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="name"
                label="规则显示名称"
                rules={[{ required: true, message: "请输入规则名称" }]}
              >
                <Input placeholder="输入规则显示名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="ruleType" label="规则门类" rules={[{ required: true }]}>
                <Select>
                  {RULE_TYPE_OPTIONS.map((option) => (
                    <Option key={option.value} value={option.value}>
                      {option.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="riskLevel" label="风险严重等级" rules={[{ required: true }]}>
                <Select>
                  <Option value="LOW">低风险</Option>
                  <Option value="MEDIUM">中风险</Option>
                  <Option value="HIGH">高风险</Option>
                </Select>
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="triggerPoint" label="触发时点" rules={[{ required: true }]}>
                <Select onChange={updateTriggerPoint}>
                  {CLINICAL_TRIGGER_POINT_OPTIONS.map((option) => (
                    <Option key={option.value} value={option.value}>
                      {option.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="sourceRef"
                label="医学依据/来源"
                rules={[{ required: true, message: "请输入依据来源" }]}
              >
                <Input
                  placeholder="输入已审核医学依据、院内制度或配置包来源"
                  onChange={(event) => {
                    const label = event.target.value;
                    setConditionTree((current) => ({
                      ...current,
                      actions: current.actions.map((action) =>
                        action.source.label === "规则版本来源" || !action.source.label.trim()
                          ? { ...action, source: { ...action.source, label } }
                          : action,
                      ),
                    }));
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="packageVersion"
                label="标准上下文包版本"
                rules={[{ required: true, message: "请输入本规则绑定的配置包版本" }]}
              >
                <Input placeholder="输入当前已审核的标准上下文包版本" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="changeSummary" label="初始化变更内容说明" rules={[{ required: true }]}>
            <Input placeholder="本次创建版本的修改概述" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="priority"
                label="执行优先级"
                tooltip="数值越大越先执行，用于确定显式抑制的先后关系。"
                rules={[{ required: true, message: "请输入执行优先级" }]}
              >
                <InputNumber min={0} max={1000} precision={0} className="mk-full-width" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="suppressedBy"
                label="命中后由以下规则抑制"
                tooltip="仅当所选规则先成功命中时，本规则才不再产生临床动作。"
              >
                <AutoComplete
                  allowClear
                  filterOption={(input, option) =>
                    String(option?.label ?? option?.value ?? "")
                      .toLowerCase()
                      .includes(input.toLowerCase())
                  }
                  placeholder="输入或选择高优先级规则编码"
                  options={(listData?.items ?? []).map((rule) => ({
                    value: rule.ruleCode,
                    label: `${rule.ruleCode} · P${rule.priority} · ${rule.name}`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="dedupeWindowMinutes"
                label="同患者去重窗口（分钟）"
                tooltip="窗口内相同规则与动作只保留首次成功命中，0 表示不去重。"
                rules={[{ required: true, message: "请输入去重窗口" }]}
              >
                <InputNumber min={0} max={1440} precision={0} className="mk-full-width" />
              </Form.Item>
            </Col>
          </Row>

          <Space className={`mk-flex-between mk-full-width ${styles.marginBottomMd}`}>
            <Text type="secondary">普通配置只展示 L1/L2；L3 DSL 需显式进入专家模式。</Text>
            <Space>
              <Text>专家模式</Text>
              <Switch
                aria-label="专家模式"
                checked={createExpertMode}
                onChange={toggleCreateExpertMode}
              />
            </Space>
          </Space>

          <Tabs
            activeKey={activeCreateLayer}
            items={createLayerItems}
            onChange={(key) => setActiveCreateLayer(key as CreateLayerKey)}
          />
        </Form>
      </Modal>

      <Drawer
        title="条件片段库"
        width={980}
        zIndex={1200}
        open={fragmentLibraryOpen}
        afterOpenChange={(open) => {
          if (open && !editingFragmentId) resetFragmentForm();
        }}
        onClose={() => setFragmentLibraryOpen(false)}
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={10}>
            <div className={styles.formSection}>
              <Form form={fragmentForm} layout="vertical">
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item
                      name="fragmentCode"
                      label="片段编码"
                      rules={[{ required: true, message: "请输入片段编码" }]}
                    >
                      <Input placeholder="FRAG_RENAL_IMPAIRED" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      name="fragmentName"
                      label="片段名称"
                      rules={[{ required: true, message: "请输入片段名称" }]}
                    >
                      <Input placeholder="肾功能受限" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item name="category" label="分类">
                      <Input placeholder="如 肾病 / 抗凝" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      name="versionNo"
                      label="片段版本"
                      initialValue={1}
                      rules={[{ required: true, message: "请输入片段版本" }]}
                    >
                      <InputNumber min={1} precision={0} className="mk-full-width" />
                    </Form.Item>
                  </Col>
                </Row>
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item
                      name="packageVersion"
                      label="包版本"
                      initialValue={currentCreatePackageVersion}
                      rules={[{ required: true, message: "请输入包版本" }]}
                    >
                      <Input placeholder="pkg-2026.06" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      name="status"
                      label="状态"
                      initialValue="ACTIVE"
                      rules={[{ required: true, message: "请选择状态" }]}
                    >
                      <Select>
                        <Option value="DRAFT">DRAFT 草稿</Option>
                        <Option value="ACTIVE">ACTIVE 可复用</Option>
                        <Option value="RETIRED">RETIRED 退役</Option>
                      </Select>
                    </Form.Item>
                  </Col>
                </Row>
                <Form.Item label="片段正文">
                  <TextArea
                    readOnly
                    value={formatRuleJson(fragmentBodyJson ?? currentFragmentBodyJson)}
                    autoSize={{ minRows: 5, maxRows: 10 }}
                  />
                </Form.Item>
                <Space wrap>
                  <Button icon={<SyncOutlined />} onClick={refreshFragmentBodyFromCurrentTree}>
                    使用当前条件树
                  </Button>
                  <Button onClick={resetFragmentForm}>新建</Button>
                  <Button
                    type="primary"
                    loading={
                      createConditionFragmentMutation.isPending ||
                      updateConditionFragmentMutation.isPending
                    }
                    onClick={saveConditionFragment}
                  >
                    {editingFragmentId ? "更新片段" : "保存片段"}
                  </Button>
                </Space>
              </Form>
            </div>
          </Col>
          <Col xs={24} lg={14}>
            <Table
              rowKey="fragmentId"
              size="small"
              loading={fragmentLibraryQuery.isLoading}
              dataSource={fragmentLibraryItems}
              columns={conditionFragmentColumns}
              pagination={false}
              locale={{ emptyText: "当前包版本暂无条件片段" }}
            />
          </Col>
        </Row>
      </Drawer>

      <Modal
        title="条件片段影响分析"
        open={Boolean(impactFragmentId)}
        footer={null}
        onCancel={() => setImpactFragmentId("")}
        width={760}
        zIndex={1300}
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="片段编码">
              {conditionFragmentImpactQuery.data?.fragmentCode ?? "-"}
            </Descriptions.Item>
            <Descriptions.Item label="片段版本">
              {conditionFragmentImpactQuery.data
                ? `v${conditionFragmentImpactQuery.data.versionNo}`
                : "-"}
            </Descriptions.Item>
            <Descriptions.Item label="包版本">
              {conditionFragmentImpactQuery.data?.packageVersion ?? "-"}
            </Descriptions.Item>
            <Descriptions.Item label="影响摘要">
              {conditionFragmentImpactQuery.data?.impactDigest ?? "-"}
            </Descriptions.Item>
          </Descriptions>
          <Table
            rowKey={(item) => `${item.assetType}-${item.assetId}`}
            size="small"
            loading={conditionFragmentImpactQuery.isLoading}
            dataSource={conditionFragmentImpactQuery.data?.affectedAssets ?? []}
            pagination={false}
            columns={[
              { title: "资产类型", dataIndex: "assetType", key: "assetType" },
              { title: "资产编码", dataIndex: "assetCode", key: "assetCode" },
              { title: "名称", dataIndex: "displayName", key: "displayName" },
              { title: "影响原因", dataIndex: "impactReason", key: "impactReason" },
            ]}
          />
        </Space>
      </Modal>

      <Modal
        title="新增测试用例"
        zIndex={1100}
        open={caseModalVisible}
        onOk={handleAddTestCase}
        onCancel={() => setCaseModalVisible(false)}
        okText="保存用例"
        cancelText="取消"
        confirmLoading={addTestCaseMutation.isPending}
        width={760}
      >
        <Form form={caseForm} layout="vertical">
          <Form.Item name="caseType" label="用例类别" rules={[{ required: true }]}>
            <Select>
              <Option value="POSITIVE">阳性命中用例</Option>
              <Option value="NEGATIVE">阴性不命中用例</Option>
              <Option value="BOUNDARY">边界条件用例</Option>
              <Option value="CONFLICT">规则冲突校验用例</Option>
            </Select>
          </Form.Item>
          <Alert
            type="info"
            showIcon
            className={styles.marginBottomMd}
            message="从 ACTIVE 标准上下文快照固化回归用例"
            description="服务端校验快照状态并保存来源 ID 与资源副本，不接受人工粘贴的上下文 JSON。"
          />
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item label="患者 ID" htmlFor="rule-case-snapshot-patient-id">
                <Input
                  id="rule-case-snapshot-patient-id"
                  aria-label="测试用例患者 ID"
                  value={snapshotPatientId}
                  onChange={(event) => setSnapshotPatientId(event.target.value)}
                  placeholder="输入患者主索引"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="就诊 ID" htmlFor="rule-case-snapshot-encounter-id">
                <Input
                  id="rule-case-snapshot-encounter-id"
                  aria-label="测试用例就诊 ID"
                  value={snapshotEncounterId}
                  onChange={(event) => setSnapshotEncounterId(event.target.value)}
                  placeholder="输入就诊号"
                />
              </Form.Item>
            </Col>
          </Row>
          <Button
            icon={<FileSearchOutlined />}
            aria-label="读取 ACTIVE 快照"
            onClick={handleSnapshotSearch}
          >
            读取 ACTIVE 快照
          </Button>
          <div className={styles.marginTopMd}>{renderSnapshotChoices()}</div>
          {selectedSnapshotId && (
            <div className={styles.marginTopMd}>{renderSelectedCaseSnapshot()}</div>
          )}
          <Form.Item name="expectedHit" label="期望求值结果">
            <Select
              onChange={(expectedHit) => {
                if (!expectedHit) {
                  caseForm.setFieldsValue({
                    expectedSeverity: undefined,
                    expectedActionCode: undefined,
                  });
                }
              }}
            >
              <Option value={true}>应当触发规则命中</Option>
              <Option value={false}>不应当命中</Option>
            </Select>
          </Form.Item>
          {caseExpectedHit && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="expectedSeverity"
                  label="期望动作严重度"
                  rules={[{ required: true }]}
                >
                  <Select>
                    <Option value="LOW">低风险</Option>
                    <Option value="MEDIUM">中风险</Option>
                    <Option value="HIGH">高风险</Option>
                    <Option value="CRITICAL">红线</Option>
                  </Select>
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="expectedActionCode"
                  label="期望动作代码"
                  rules={[{ required: true }]}
                >
                  <Select>
                    <Option value="INFO">信息提示</Option>
                    <Option value="REMIND">一般提醒</Option>
                    <Option value="STRONG_REMINDER">强提醒</Option>
                    <Option value="BLOCK">阻断确认</Option>
                    <Option value="SUGGEST_ORDER">建议医嘱</Option>
                    <Option value="AUTO_DOCUMENT">自动留痕</Option>
                  </Select>
                </Form.Item>
              </Col>
            </Row>
          )}
        </Form>
      </Modal>

      <FieldCatalogManager open={fieldManagerOpen} onClose={() => setFieldManagerOpen(false)} />
    </PageShell>
  );
}
