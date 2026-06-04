import { useMemo, useState, type ReactNode } from "react";
import {
  App as AntdApp,
  Alert,
  AutoComplete,
  Badge,
  Button,
  Card,
  Col,
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
  useSimulateRule,
  usePublishRule,
  useContextFieldCatalog,
  useContextSnapshots,
  useContextSnapshotDetail,
  useRuleImpact,
} from "@/shared/api/hooks";
import type {
  RuleDefinition,
  RuleEvaluationItem,
  RuleImpactObject,
  RuleImpactResponse,
  RuleTestCase,
  ContextSnapshotSummary,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { StepFlow } from "@/shared/ui/StepFlow";
import {
  RULE_LAYER_TEMPLATES,
  conditionNeedsValue,
  conditionNodeToDsl,
  conditionTreeToDsl,
  createExplanationTemplate,
  dslToConditionTree,
  dslWhenToRootGroup,
  flatToRootGroup,
  formatRuleJson,
  instantiateRuleTemplate,
  isConditionGroup,
  parseRuleJson,
  type RuleCondition,
  type RuleConditionGroup,
  type RuleConditionNode,
  type RuleConditionTree,
  type RuleLogic,
  type RuleOperator,
  type RuleSeverity,
  type RuleTemplateKey,
  type RuleValueKind,
} from "@/shared/config/ruleLayeredEditor";
import {
  MAX_TREE_DEPTH,
  addNodeToGroup,
  countConditionLeaves,
  createConditionGroup,
  createConditionLeaf,
  mapConditionById,
  mapGroupById,
  removeConditionById,
  rootDepth,
  rootHasUnresolvedFact,
} from "@/shared/config/ruleConditionTreeOps";

const { TextArea } = Input;
const { Option } = Select;
const { Text } = Typography;

type RuleStatusBadge = Exclude<BadgeProps["status"], undefined>;
type CreateLayerKey = "l1" | "l2" | "l3";
type DetailLayerKey = "l1" | "l2" | "l3" | "cases" | "simulate" | "release";

const DEFAULT_TEMPLATE_KEY: RuleTemplateKey = "clinical_quality_monitor";
const REQUIRED_RELEASE_CASE_TYPES = ["POSITIVE", "NEGATIVE", "BOUNDARY", "CONFLICT"];

const RULE_TYPE_LABELS: Record<string, string> = {
  DRUG_SAFETY: "合理用药安全",
  INSURANCE_AUDIT: "医保规范核查",
  CLINICAL_QUALITY: "临床诊疗质控",
};

const RISK_LABELS: Record<RuleSeverity, string> = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高风险",
};

const OPERATOR_LABELS: Record<RuleOperator, string> = {
  exists: "存在",
  equals: "等于",
  not_equals: "不等于",
  contains: "包含",
  gt: "大于",
  gte: "大于等于",
  lt: "小于",
  lte: "小于等于",
  in: "属于集合",
  not_in: "不属于集合",
  between: "区间比较",
  unit_compare: "单位换算比较",
  temporal: "时间窗/连续/趋势",
  derived: "受控公式",
};

const VALUE_KIND_LABELS: Record<RuleValueKind, string> = {
  empty: "无比较值",
  string: "文本",
  number: "数值",
  boolean: "布尔",
  list: "集合",
  range: "区间",
  measurement: "带单位阈值",
  temporal: "时间窗",
  derived: "受控公式",
};

const numericComparisonChoices = [
  { value: "gt", label: "大于" },
  { value: "gte", label: "大于等于" },
  { value: "lt", label: "小于" },
  { value: "lte", label: "小于等于" },
  { value: "equals", label: "等于" },
];

const derivedFormulaChoices = [
  { value: "CKD_EPI_2021_EGFR", label: "eGFR CKD-EPI 2021" },
  { value: "COCKCROFT_GAULT_CRCL", label: "CrCl Cockcroft-Gault" },
  { value: "MOSTELLER_BSA", label: "BSA Mosteller" },
];

const CLINICAL_OPERATOR_SET = new Set<RuleOperator>([
  "between",
  "unit_compare",
  "temporal",
  "derived",
]);

function isClinicalOperator(operator: RuleOperator) {
  return CLINICAL_OPERATOR_SET.has(operator);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
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
  if (isRecord(currentValue)) return currentValue;
  switch (operator) {
    case "between":
      return { min: "", max: "", includeMin: true, includeMax: true, unit: "" };
    case "unit_compare":
      return { comparison: "gte", value: "", unit: "", analyte: "" };
    case "temporal":
      return {
        mode: "consecutive",
        window: "PT24H",
        referenceTime: "",
        count: 2,
        condition: { operator: "gt", value: "", unit: "" },
      };
    case "derived":
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
      return currentValue ?? "";
  }
}

function derivedParameterKeys(formula: string) {
  if (formula === "MOSTELLER_BSA") {
    return ["heightCm", "weightKg"];
  }
  if (formula === "COCKCROFT_GAULT_CRCL") {
    return ["creatinine", "age", "sex", "weightKg"];
  }
  return ["creatinine", "age", "sex"];
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

function renderRiskTag(level: string) {
  const colors: Record<string, string> = {
    LOW: "green",
    MEDIUM: "orange",
    HIGH: "red",
  };
  return (
    <Tag color={colors[level] ?? "default"}>{RISK_LABELS[level as RuleSeverity] ?? level}</Tag>
  );
}

function renderStatus(status: string) {
  const statusMap: Record<string, { text: string; status: RuleStatusBadge }> = {
    DRAFT: { text: "草稿设计中", status: "warning" },
    PUBLISHED: { text: "已上线运行", status: "success" },
    OFFLINE: { text: "已下线封存", status: "default" },
    ARCHIVED: { text: "已归档历史", status: "default" },
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
  if (!conditionNeedsValue(operator)) return "empty";
  if (operator === "between") return "range";
  if (operator === "unit_compare") return "measurement";
  if (operator === "temporal") return "temporal";
  if (operator === "derived") return "derived";
  return currentKind === "empty" ? "string" : currentKind;
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

export default function RuleDefinitions() {
  const { message, modal } = AntdApp.useApp();
  const [page, setPage] = useState(1);
  const [size] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined);
  const [riskFilter, setRiskFilter] = useState<string | undefined>(undefined);
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null);
  const [activeDetailLayer, setActiveDetailLayer] = useState<DetailLayerKey>("l2");
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [activeCreateLayer, setActiveCreateLayer] = useState<CreateLayerKey>("l1");
  const [createExpertMode, setCreateExpertMode] = useState(false);
  const [detailExpertMode, setDetailExpertMode] = useState(false);
  const [selectedTemplateKey, setSelectedTemplateKey] =
    useState<RuleTemplateKey>(DEFAULT_TEMPLATE_KEY);
  const [conditionTree, setConditionTree] = useState<RuleConditionTree>(createDefaultTree);
  const [dslEditorValue, setDslEditorValue] = useState(createDefaultDslText);
  const [createForm] = Form.useForm();
  const [caseModalVisible, setCaseModalVisible] = useState(false);
  const [caseForm] = Form.useForm();
  const [simulatePayload, setSimulatePayload] = useState<string>("");
  const [simulateResult, setSimulateResult] = useState<RuleEvaluationItem | null>(null);
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [snapshotSearchParams, setSnapshotSearchParams] = useState<{
    patientId?: string;
    encounterId?: string;
  } | null>(null);
  const [selectedSnapshotId, setSelectedSnapshotId] = useState("");
  const [releaseReason, setReleaseReason] = useState("");

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
  const simulateMutation = useSimulateRule(selectedRuleId || "");
  const publishMutation = usePublishRule();
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: snapshotSearchParams?.patientId,
      encounterId: snapshotSearchParams?.encounterId,
      status: "ACTIVE",
      page: 1,
      size: 20,
    },
    { enabled: Boolean(selectedRuleId && snapshotSearchParams) },
  );
  const snapshotDetailQuery = useContextSnapshotDetail(selectedSnapshotId, {
    enabled: Boolean(selectedRuleId && selectedSnapshotId),
  });
  const impactQuery = useRuleImpact(selectedRuleId || "", {
    enabled: Boolean(selectedRuleId && detailData?.definition.status === "DRAFT"),
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
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(nextTree)));
    setActiveCreateLayer("l1");
    createForm.setFieldsValue({
      ruleType: template.ruleType,
      riskLevel: template.riskLevel,
      changeSummary: "初始化创建草稿版本",
    });
  };

  const openCreateModal = () => {
    createForm.resetFields();
    setCreateExpertMode(false);
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
    setDslEditorValue(formatRuleJson(conditionTreeToDsl(nextTree)));
    createForm.setFieldsValue({
      ruleType: template.ruleType,
      riskLevel: template.riskLevel,
    });
    setActiveCreateLayer("l2");
  };

  const buildRuleDslFromRoot = (
    root: RuleConditionGroup,
    action: RuleConditionTree["action"],
    explanationSummary: string,
  ) => ({
    when: conditionNodeToDsl(root),
    then: [{ ...action }],
    explain: {
      summary: explanationSummary,
      authoring: {
        layer: "L2_VISUAL_TREE" as const,
        conditionCount: countConditionLeaves(root),
      },
    },
  });

  const syncTreeToDsl = () => {
    setDslEditorValue(
      formatRuleJson(
        buildRuleDslFromRoot(conditionRoot, conditionTree.action, conditionTree.explanationSummary),
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
      const root = dslWhenToRootGroup((parsed as { when: unknown }).when);
      setConditionTree({ ...nextTree, root, logic: root.logic, conditions: [] });
      setActiveCreateLayer("l2");
      message.success("已从 L3 DSL 回填到 L2 条件树");
    } catch {
      message.error("L3 DSL JSON 不合法，无法回填到条件树。");
    }
  };

  const conditionRoot = conditionTree.root ?? flatToRootGroup(conditionTree);

  const updateRoot = (updater: (root: RuleConditionGroup) => RuleConditionGroup) => {
    setConditionTree((current) => {
      const root = current.root ?? flatToRootGroup(current);
      return { ...current, root: updater(root) };
    });
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
              <Input value="存在性判断不需要比较值" disabled />
            </Form.Item>
          </Col>
        </Row>
      );
    }

    if (condition.operator === "between") {
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
      const mode = conditionValueString(condition, "mode") || "consecutive";
      return (
        <Space direction="vertical" size="small" className="mk-full-width">
          <Row gutter={16}>
            <Col span={5}>
              <Form.Item label="时间窗模式">
                <Select
                  value={mode}
                  onChange={(nextMode) => updateConditionValue(condition, { mode: nextMode })}
                >
                  <Option value="consecutive">连续命中</Option>
                  <Option value="trend">趋势判断</Option>
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
          {mode === "trend" ? (
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
          ) : (
            <Row gutter={16}>
              <Col span={6}>
                <Form.Item label="连续条件比较符">
                  <Select
                    value={String(nested.operator ?? "gt")}
                    onChange={(operator) => updateTemporalCondition(condition, { operator })}
                    options={numericComparisonChoices}
                  />
                </Form.Item>
              </Col>
              <Col span={5}>
                <Form.Item label="连续条件阈值">
                  <InputNumber
                    value={typeof nested.value === "number" ? nested.value : undefined}
                    onChange={(value) => updateTemporalCondition(condition, { value: value ?? "" })}
                    className="mk-full-width"
                  />
                </Form.Item>
              </Col>
              <Col span={5}>
                <Form.Item label="连续条件单位">
                  <Input
                    value={String(nested.unit ?? "")}
                    onChange={(event) =>
                      updateTemporalCondition(condition, { unit: event.target.value })
                    }
                  />
                </Form.Item>
              </Col>
            </Row>
          )}
        </Space>
      );
    }

    if (condition.operator === "derived") {
      const formula = conditionValueString(condition, "formula") || "CKD_EPI_2021_EGFR";
      const comparison = conditionValueString(condition, "comparison") || "gte";
      const parameterKeys = derivedParameterKeys(formula);
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
                  options={derivedFormulaChoices}
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
          </Form.Item>
        </Col>
      </Row>
    );
  };

  const fieldCatalogQuery = useContextFieldCatalog();
  const fieldCatalogList = fieldCatalogQuery.data ?? [];
  const fieldCatalogOptions = fieldCatalogList.map((field) => ({
    value: field.fieldPath,
    label: field.codeSystem
      ? `${field.displayName}（${field.fieldPath}）· 字典 ${field.codeSystem}`
      : `${field.displayName}（${field.fieldPath}）`,
  }));
  const fieldByPath = new Map(fieldCatalogList.map((field) => [field.fieldPath, field]));
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

  const renderConditionLeaf = (condition: RuleCondition) => {
    const needsValue = conditionNeedsValue(condition.operator);
    const isFirstLeaf = condition.id === firstLeafId;
    return (
      <div key={condition.id} className="rounded-lg border border-gray-200 bg-white p-4">
        <div className="flex items-center justify-between mb-3">
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
                filterOption={(input, option) =>
                  String(option?.value ?? "")
                    .toLowerCase()
                    .includes(input.toLowerCase()) ||
                  String(option?.label ?? "")
                    .toLowerCase()
                    .includes(input.toLowerCase())
                }
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
        {renderConditionValueEditor(condition, needsValue, isFirstLeaf)}
      </div>
    );
  };

  const renderConditionGroup = (group: RuleConditionGroup, depth = 0) => {
    const isRoot = depth === 0;
    const depthReached = rootDepth(conditionRoot) >= MAX_TREE_DEPTH;
    // 根组用中性白底；子组按深度加淡绿底 + 左边线 + 缩进，使嵌套层级清晰可读。
    const groupClassName = isRoot
      ? "rounded-lg border border-gray-200 bg-white p-4"
      : "rounded-lg border border-emerald-100 bg-emerald-50/30 p-3 ml-4 border-l-4 border-l-emerald-300";
    return (
      <div key={group.id} className={groupClassName}>
        <div className="flex flex-wrap items-center gap-3 mb-3">
          <Tag color="green">{isRoot ? "条件根组" : "子条件组"}</Tag>
          <Select
            aria-label="条件组关系"
            size="small"
            value={group.logic}
            className="w-[140px]"
            onChange={(value: RuleLogic) => updateGroup(group.id, { logic: value })}
          >
            <Option value="all">全部条件满足</Option>
            <Option value="any">任一条件满足</Option>
          </Select>
          <Space size={4}>
            <Text type="secondary" className="text-xs">
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
              className="ml-auto"
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
        <div
          key={node.id}
          className="rounded-lg border border-emerald-200 bg-emerald-50/40 p-3 ml-2 border-l-4 border-l-emerald-300"
        >
          <Space className="mb-2">
            <Tag color="green">{node.negate ? "非（NOT）" : "组"}</Tag>
            <Text type="secondary">{node.logic === "all" ? "全部条件满足" : "任一条件满足"}</Text>
          </Space>
          <Space direction="vertical" size="small" className="mk-full-width">
            {node.children.map((child) => renderReadonlyNode(child))}
          </Space>
        </div>
      );
    }
    return (
      <Descriptions key={node.id} bordered column={2} size="small">
        <Descriptions.Item label="条件标签">{node.label}</Descriptions.Item>
        <Descriptions.Item label="字段路径">{node.fact}</Descriptions.Item>
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
      try {
        // 专家模式以 L3 JSON 为准；普通模式以 L2 递归条件树为准（避免未点同步而提交过期 DSL）。
        parsedDsl = createExpertMode
          ? parseRuleJson(dslEditorValue)
          : buildRuleDslFromRoot(
              conditionRoot,
              conditionTree.action,
              conditionTree.explanationSummary,
            );
        if (!isRecord(parsedDsl) || !("when" in parsedDsl)) {
          throw new Error("缺少 when");
        }
        submitRoot = dslWhenToRootGroup((parsedDsl as { when: unknown }).when);
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

      await createRuleMutation.mutateAsync({
        ruleCode: values.ruleCode,
        name: values.name,
        ruleType: values.ruleType,
        authoringMode: "VISUAL",
        riskLevel: values.riskLevel,
        packageVersion: values.packageVersion,
        sourceRef: values.sourceRef,
        changeSummary: values.changeSummary,
        dslJson: parsedDsl,
        explanationJson: createExplanationTemplate({
          ...conditionTree,
          root: submitRoot,
        }),
      });

      message.success("新规则创建成功，状态为草稿");
      setCreateModalVisible(false);
      createForm.resetFields();
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
      const inputPayload = parseJsonInput(
        values.inputPayload,
        "请粘贴真实脱敏测试输入载荷 JSON",
        message.error,
      );
      if (!inputPayload) return;

      await addTestCaseMutation.mutateAsync({
        packageVersion: selectedRulePackageVersion,
        caseType: values.caseType,
        inputPayload,
        expectedHit: values.expectedHit,
        expectedSeverity: values.expectedSeverity,
        expectedActionCode: values.expectedActionCode,
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

  const handleManualSimulate = async () => {
    const inputPayload = parseJsonInput(
      simulatePayload,
      "请先粘贴真实脱敏上下文快照 JSON",
      message.error,
    );
    if (!inputPayload) return;
    await runSimulation(inputPayload);
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

  const handlePublish = async () => {
    if (!selectedRuleId) return;
    const impactDigest = impactQuery.data?.impactDigest;
    const reason = releaseReason.trim();
    if (!impactDigest) {
      setActiveDetailLayer("release");
      message.error("请先读取发布影响摘要，再提交发布门禁。");
      return;
    }
    if (!reason) {
      setActiveDetailLayer("release");
      message.error("请填写发布审核说明。");
      return;
    }
    try {
      await publishMutation.mutateAsync({
        ruleId: selectedRuleId,
        packageVersion: selectedRulePackageVersion,
        impactDigest,
        reason,
      });
      message.success("发布成功，门禁测试通过");
      refetchDetail();
      refetchList();
    } catch (error: unknown) {
      modal.error({
        title: "规则发布门禁拒绝",
        content: getApiErrorMessage(
          error,
          "发布门禁校验未通过，请检查测试用例是否齐全且全部通过。",
        ),
      });
    }
  };

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
      className: "font-semibold text-gray-800",
    },
    {
      title: "规则类别",
      dataIndex: "ruleType",
      key: "ruleType",
      render: (type: string) => RULE_TYPE_LABELS[type] || type,
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
      render: (val: string) => val || <span className="text-gray-400">未锁定</span>,
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
            setSimulatePayload("");
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
      <Descriptions.Item label="同步目标">
        {impactCount(impactQuery.data?.syncTargets)}
      </Descriptions.Item>
      {renderImpactObjectList("已定位规则", impactQuery.data?.affectedRules ?? [])}
      {renderImpactObjectList("受影响路径", impactQuery.data?.affectedPathways ?? [])}
      {renderImpactObjectList("在径患者", impactQuery.data?.inPathPatients ?? [])}
      {renderImpactObjectList("同步目标", impactQuery.data?.syncTargets ?? [])}
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

  const releaseStepPanel = (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Alert
        type={releaseGate.allPassed ? "success" : "warning"}
        showIcon
        message={
          releaseGate.allPassed
            ? "阳性、阴性、边界、冲突四类测试用例已全绿。"
            : `发布门禁未满足：缺少 ${releaseGate.missingTypes.join("、") || "通过结果"}。`
        }
      />
      {detailData?.definition.riskLevel === "HIGH" && (
        <Alert
          type="warning"
          showIcon
          message="高危规则必须携带当前影响摘要和审核说明。"
          description="影响摘要来自规则影响分析接口；路径、在径患者和同步目标只展示后端从关系库定位到的真实对象。"
        />
      )}
      {impactSummaryPanel}
      <Form layout="vertical">
        <Form.Item label="发布审核说明" htmlFor="rule-release-reason">
          <TextArea
            id="rule-release-reason"
            rows={3}
            value={releaseReason}
            onChange={(event) => setReleaseReason(event.target.value)}
            placeholder="填写已核查影响摘要、测试结果和灰度发布安排。"
          />
        </Form.Item>
        <Button
          type="primary"
          onClick={handlePublish}
          loading={publishMutation.isPending}
          disabled={!releaseGate.allPassed || !impactQuery.data?.impactDigest}
        >
          提交发布门禁
        </Button>
      </Form>
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
              <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="命中动作">
                  {detailTree.action.actionCode} · {RISK_LABELS[detailTree.action.severity]}
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
                  <Row gutter={16}>
                    <Col span={12}>
                      <div className="bg-gray-50 p-4 rounded-lg overflow-auto max-h-96 text-xs font-normal">
                        {detailDsl ? formatRuleJson(detailDsl) : "当前版本 DSL 无法解析"}
                      </div>
                    </Col>
                    <Col span={12}>
                      <div className="bg-gray-50 p-4 rounded-lg overflow-auto max-h-96 text-xs font-normal">
                        {detailExplanation
                          ? formatRuleJson(detailExplanation)
                          : "当前版本解释模板为空或无法解析"}
                      </div>
                    </Col>
                  </Row>
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
              <div className="flex justify-between items-center mb-4">
                <Text type="secondary">
                  发布前必须覆盖阳性、阴性、边界和冲突用例，且所有用例通过。
                </Text>
                {detailData.definition.status === "DRAFT" && (
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => {
                      caseForm.setFieldsValue({
                        inputPayload: "",
                        expectedHit: true,
                        expectedSeverity: "LOW",
                        expectedActionCode: "REVIEW_REQUIRED",
                        caseType: "POSITIVE",
                      });
                      setCaseModalVisible(true);
                    }}
                  >
                    新增测试用例
                  </Button>
                )}
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
                      if (!row.lastStatus || row.lastStatus === "PENDING") {
                        return <Badge status="default" text="待执行" />;
                      }
                      return row.lastStatus === "PASS" ? (
                        <span className="text-green-600 font-medium">
                          <CheckCircleOutlined className="mr-1" /> PASS
                        </span>
                      ) : (
                        <Tooltip title={row.lastMessage}>
                          <span className="text-red-600 font-medium cursor-help">
                            <CloseCircleOutlined className="mr-1" /> FAIL
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
                  <div className="mt-4">{renderSnapshotChoices()}</div>
                </Col>
                <Col span={12}>{renderSelectedSnapshotDetail()}</Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  {detailExpertMode ? (
                    <Card size="small" title="专家手工 JSON 兜底">
                      <Form layout="vertical">
                        <Form.Item label="真实脱敏上下文快照 JSON">
                          <TextArea
                            rows={8}
                            value={simulatePayload}
                            onChange={(e) => setSimulatePayload(e.target.value)}
                            placeholder="仅在已知快照 ID 无法检索时使用；仍必须来自真实脱敏上下文。"
                            className="font-normal text-xs"
                          />
                        </Form.Item>
                        <Button
                          icon={<PlayCircleOutlined />}
                          onClick={handleManualSimulate}
                          loading={simulateMutation.isPending}
                          block
                        >
                          运行手工 JSON 试运行
                        </Button>
                      </Form>
                    </Card>
                  ) : (
                    <Alert
                      type="info"
                      showIcon
                      message="手工 JSON 试运行已收纳到专家模式"
                      description="普通流程请先读取标准上下文快照，再使用所选快照试运行。"
                    />
                  )}
                </Col>
                <Col span={12}>
                  {simulateResult ? (
                    <div className="bg-gray-50 p-4 rounded-lg min-h-64 overflow-auto max-h-96">
                      <Descriptions column={1} size="small" bordered className="mb-4">
                        <Descriptions.Item label="规则是否命中">
                          {simulateResult.hit ? (
                            <Tag color="red">命中</Tag>
                          ) : (
                            <Tag color="green">未命中</Tag>
                          )}
                        </Descriptions.Item>
                        {simulateResult.hit && (
                          <>
                            <Descriptions.Item label="动作代码">
                              {simulateResult.actionCode}
                            </Descriptions.Item>
                            <Descriptions.Item label="最高严重等级">
                              {simulateResult.severity}
                            </Descriptions.Item>
                          </>
                        )}
                      </Descriptions>
                      <Text strong>详细决策动作说明</Text>
                      <div className="text-xs text-gray-600 bg-white p-3 rounded border border-gray-200 font-normal mt-2">
                        {simulateResult.explanation || "未命中，无动作输出。"}
                      </div>
                    </div>
                  ) : (
                    <Empty description="选择真实快照并试运行后展示命中与解释" />
                  )}
                </Col>
              </Row>
            </Space>
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
            <StepFlow
              currentStep="submit_review"
              panelByStep={{ submit_review: releaseStepPanel }}
              status={releaseGate.allPassed ? "process" : "error"}
            />
          ),
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
            className="mb-4"
          />
          <Radio.Group
            value={selectedTemplateKey}
            onChange={(event: RadioChangeEvent) =>
              applyTemplate(event.target.value as RuleTemplateKey)
            }
          >
            <Space direction="vertical" className="mk-full-width">
              {RULE_LAYER_TEMPLATES.map((template) => (
                <Radio key={template.key} value={template.key}>
                  <Space direction="vertical" size={0}>
                    <Text strong>{template.title}</Text>
                    <Text type="secondary">{template.description}</Text>
                  </Space>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
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

          <Alert
            type="info"
            showIcon
            message="临床算子"
            description="区间比较、单位换算、时间窗连续/趋势和 eGFR/CrCl/BSA 受控公式均可在 L2 结构化配置；支持任意层级「条件组 + 子条件组」嵌套；L3 JSON 仅保留给专家核查。"
          />

          {renderConditionGroup(conditionRoot, 0)}

          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="动作代码">
              <Input
                value={conditionTree.action.actionCode}
                onChange={(event) =>
                  setConditionTree((current) => ({
                    ...current,
                    action: { ...current.action, actionCode: event.target.value },
                  }))
                }
              />
            </Descriptions.Item>
            <Descriptions.Item label="动作风险">
              <Select
                value={conditionTree.action.severity}
                onChange={(value: RuleSeverity) =>
                  setConditionTree((current) => ({
                    ...current,
                    action: { ...current.action, severity: value },
                  }))
                }
                className="mk-full-width"
              >
                <Option value="LOW">低风险</Option>
                <Option value="MEDIUM">中风险</Option>
                <Option value="HIGH">高风险</Option>
              </Select>
            </Descriptions.Item>
            <Descriptions.Item label="动作说明" span={2}>
              <Input
                value={conditionTree.action.message}
                onChange={(event) =>
                  setConditionTree((current) => ({
                    ...current,
                    action: { ...current.action, message: event.target.value },
                  }))
                }
              />
            </Descriptions.Item>
          </Descriptions>

          <Space>
            <Button
              type="primary"
              icon={<SyncOutlined />}
              aria-label="同步到 DSL"
              onClick={syncTreeToDsl}
            >
              同步到 DSL
            </Button>
          </Space>
        </Space>
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
                  className="mb-4"
                />
                <Form.Item label="规则 DSL JSON" htmlFor="ruleDslJson">
                  <TextArea
                    id="ruleDslJson"
                    rows={16}
                    value={dslEditorValue}
                    onChange={(event) => setDslEditorValue(event.target.value)}
                    className="font-normal text-xs"
                  />
                </Form.Item>
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
      description="配置规则资产，完成测试、解释和发布门禁。"
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
      <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-100 mb-6">
        <Form layout="inline" className="flex flex-wrap gap-4 items-center">
          <Form.Item label="状态">
            <Select
              placeholder="全部状态"
              allowClear
              value={statusFilter}
              onChange={setStatusFilter}
              className="w-[140px]"
            >
              <Option value="DRAFT">草稿设计中</Option>
              <Option value="PUBLISHED">已上线运行</Option>
              <Option value="OFFLINE">已下线封存</Option>
            </Select>
          </Form.Item>
          <Form.Item label="类别">
            <Select
              placeholder="全部类别"
              allowClear
              value={typeFilter}
              onChange={setTypeFilter}
              className="w-[150px]"
            >
              <Option value="DRUG_SAFETY">合理用药安全</Option>
              <Option value="INSURANCE_AUDIT">医保规范核查</Option>
              <Option value="CLINICAL_QUALITY">临床诊疗质控</Option>
            </Select>
          </Form.Item>
          <Form.Item label="风险评级">
            <Select
              placeholder="全部评级"
              allowClear
              value={riskFilter}
              onChange={setRiskFilter}
              className="w-[120px]"
            >
              <Option value="LOW">低风险</Option>
              <Option value="MEDIUM">中风险</Option>
              <Option value="HIGH">高风险</Option>
            </Select>
          </Form.Item>
        </Form>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-100 overflow-hidden">
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
          <div className="flex items-center justify-between w-full">
            <span>规则配置详情与试运行</span>
            {detailData?.definition.status === "DRAFT" && (
              <Button type="primary" onClick={() => setActiveDetailLayer("release")}>
                进入发布流
              </Button>
            )}
          </div>
        }
        width={980}
        onClose={() => {
          setSelectedRuleId(null);
          setActiveDetailLayer("l2");
          setSelectedSnapshotId("");
          setSnapshotSearchParams(null);
          setSimulatePayload("");
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
            <Alert
              message={
                detailData.definition.status === "PUBLISHED"
                  ? "当前规则已上线，修改需新建草稿版本并重新走发布门禁。"
                  : "当前规则为草稿，可补测试用例、试运行，并在全绿后提交发布。"
              }
              type={detailData.definition.status === "PUBLISHED" ? "success" : "info"}
              showIcon
            />

            <Descriptions title="基本元数据" bordered column={2}>
              <Descriptions.Item label="规则编码">
                {detailData.definition.ruleCode}
              </Descriptions.Item>
              <Descriptions.Item label="名称">{detailData.definition.name}</Descriptions.Item>
              <Descriptions.Item label="类型">
                {RULE_TYPE_LABELS[detailData.definition.ruleType] ?? detailData.definition.ruleType}
              </Descriptions.Item>
              <Descriptions.Item label="风险级别">
                {renderRiskTag(detailData.definition.riskLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {renderStatus(detailData.definition.status)}
              </Descriptions.Item>
              <Descriptions.Item label="当前版本">
                {detailData.version?.versionNo || 0} 版
              </Descriptions.Item>
            </Descriptions>

            <Space className="mk-flex-between mk-full-width">
              <Text type="secondary">技术 DSL、解释模板与手工 JSON 兜底默认隐藏。</Text>
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
        onCancel={() => setCreateModalVisible(false)}
        width={920}
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
                  <Option value="DRUG_SAFETY">合理用药安全</Option>
                  <Option value="INSURANCE_AUDIT">医保规范核查</Option>
                  <Option value="CLINICAL_QUALITY">临床诊疗质控</Option>
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
              <Form.Item
                name="sourceRef"
                label="医学依据/来源"
                rules={[{ required: true, message: "请输入依据来源" }]}
              >
                <Input placeholder="输入已审核医学依据、院内制度或配置包来源" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="packageVersion"
            label="标准上下文包版本"
            rules={[{ required: true, message: "请输入本规则绑定的配置包版本" }]}
          >
            <Input placeholder="输入当前已审核的标准上下文包版本" />
          </Form.Item>
          <Form.Item name="changeSummary" label="初始化变更内容说明" rules={[{ required: true }]}>
            <Input placeholder="本次创建版本的修改概述" />
          </Form.Item>

          <Space className="mk-flex-between mk-full-width mb-4">
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

      <Modal
        title="新增测试用例"
        open={caseModalVisible}
        onOk={handleAddTestCase}
        onCancel={() => setCaseModalVisible(false)}
        okText="保存用例"
        cancelText="取消"
        confirmLoading={addTestCaseMutation.isPending}
        forceRender
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
          <Form.Item
            name="inputPayload"
            label="真实脱敏测试输入 JSON 快照"
            rules={[{ required: true, message: "请输入快照" }]}
          >
            <TextArea
              rows={8}
              placeholder="粘贴真实脱敏上下文快照 JSON"
              className="font-normal text-xs"
            />
          </Form.Item>
          <Form.Item name="expectedHit" label="期望求值结果">
            <Select>
              <Option value={true}>应当触发规则命中</Option>
              <Option value={false}>不应当命中</Option>
            </Select>
          </Form.Item>
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
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="expectedActionCode"
                label="期望动作代码"
                rules={[{ required: true }]}
              >
                <Input placeholder="如 REVIEW_REQUIRED / BLOCK" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </PageShell>
  );
}
