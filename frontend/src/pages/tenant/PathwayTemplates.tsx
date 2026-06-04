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
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Timeline,
} from "antd";
import type { BadgeProps, TableProps, TabsProps } from "antd";
import {
  ApartmentOutlined,
  CheckCircleOutlined,
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
import { StandardTermValueAutoComplete } from "@/shared/ui/condition/StandardTermValueAutoComplete";
import { buildFieldCatalogOptions } from "@/shared/config/contextFieldOptions";
import {
  useContextFieldCatalog,
  useContextSnapshotDetail,
  useContextSnapshots,
  useCreatePathwayTemplate,
  useCreateSpecialtyPackage,
  useFullRolloutPathwayTemplate,
  usePathwayTemplateDetail,
  usePathwayTemplateImpact,
  usePathwayTemplates,
  usePublishPathwayTemplate,
  useRollbackPathwayTemplate,
  useSimulatePathway,
  useSpecialtyPackages,
} from "@/shared/api/hooks";
import type {
  ContextSnapshotSummary,
  PathwayEdge,
  PathwayEdgeType,
  PathwayNode,
  PathwayNodeType,
  PathwaySimulationResponse,
  PathwayTemplate,
  PathwayTemplateDetailResponse,
  PathwayTemplateImpactResponse,
  PathwayTemplateLevel,
  PathwayTemplateStatus,
  SpecialtyMetricBinding,
  SpecialtyPackage,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";

const { TextArea } = Input;
const { Option } = Select;

type PathwayBadgeStatus = Exclude<BadgeProps["status"], undefined>;

type PathwayNodeDraft = {
  nodeCode: string;
  name: string;
  nodeType: PathwayNodeType;
  sortOrder: number;
  responsibleRole?: string;
  timeWindowMinutes?: number;
  terminal: boolean;
  config?: unknown;
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

type PathwayDslPayload = {
  nodes?: PathwayNodeDraft[];
  edges?: PathwayEdgeDraft[];
  metricBindings?: PathwayMetricBindingDraft[];
};

type PathwayCanvasNode = Pick<
  PathwayNodeDraft,
  "nodeCode" | "name" | "nodeType" | "sortOrder" | "terminal"
>;

type PathwayCanvasEdge = Pick<
  PathwayEdgeDraft,
  "edgeCode" | "fromNodeCode" | "toNodeCode" | "edgeType" | "priority"
>;

type PathwayNodeFormValue = {
  nodeCode?: string;
  name?: string;
  nodeType?: PathwayNodeType;
  sortOrder?: number;
  responsibleRole?: string;
  timeWindowMinutes?: number;
  terminal?: boolean;
  metricCode?: string;
};

type PathwayEdgeFormValue = {
  edgeCode?: string;
  fromNodeCode?: string;
  toNodeCode?: string;
  edgeType?: PathwayEdgeType;
  conditionFact?: string;
  conditionOperator?: "equals" | "not_equals" | "gt" | "gte" | "lt" | "lte";
  conditionValue?: string;
  conditionValueKind?: "string" | "number" | "boolean";
  conditionJson?: string;
  priority?: number;
};

type PathwayTemplateFormValue = {
  packageId: string;
  templateCode: string;
  name: string;
  diseaseCode: string;
  templateLevel: PathwayTemplateLevel;
  templateVersion: number;
  startNodeCode: string;
  sourceRef: string;
  description?: string;
  nodes?: PathwayNodeFormValue[];
  edges?: PathwayEdgeFormValue[];
};

type SnapshotQuery = {
  patientId?: string;
  encounterId?: string;
  status: "ACTIVE";
  page: number;
  size: number;
};

const templateLevelOptions: Array<{ value: PathwayTemplateLevel; label: string }> = [
  { value: "STANDARD", label: "STANDARD 标准模板" },
  { value: "HOSPITAL", label: "HOSPITAL 医院模板" },
  { value: "DEPARTMENT", label: "DEPARTMENT 科室模板" },
  { value: "SPECIALTY", label: "SPECIALTY 专科模板" },
];

const nodeTypeOptions: Array<{ value: PathwayNodeType; label: string }> = [
  { value: "ASSESSMENT", label: "ASSESSMENT 评估" },
  { value: "DIAGNOSIS", label: "DIAGNOSIS 诊断" },
  { value: "TREATMENT", label: "TREATMENT 治疗" },
  { value: "NURSING", label: "NURSING 护理" },
  { value: "CHECK", label: "CHECK 检查" },
  { value: "FOLLOWUP", label: "FOLLOWUP 随访" },
  { value: "QUALITY", label: "QUALITY 质控" },
];

const edgeTypeOptions: Array<{ value: PathwayEdgeType; label: string }> = [
  { value: "DEFAULT", label: "DEFAULT 默认流转" },
  { value: "CONDITION", label: "CONDITION 条件流转" },
  { value: "VARIANCE", label: "VARIANCE 变异流转" },
  { value: "PHYSICIAN_DECISION", label: "PHYSICIAN_DECISION 医师决策" },
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

function normalizeNodes(nodes?: PathwayNodeFormValue[]) {
  return (nodes ?? [])
    .filter((node) => cleanText(node.nodeCode) || cleanText(node.name))
    .map<PathwayNodeDraft>((node, index) => ({
      nodeCode: cleanText(node.nodeCode) ?? "",
      name: cleanText(node.name) ?? "",
      nodeType: node.nodeType ?? "ASSESSMENT",
      sortOrder: Number(node.sortOrder ?? index + 1),
      responsibleRole: cleanText(node.responsibleRole),
      timeWindowMinutes:
        typeof node.timeWindowMinutes === "number" && node.timeWindowMinutes > 0
          ? node.timeWindowMinutes
          : undefined,
      terminal: Boolean(node.terminal),
    }));
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
    .filter((node) => cleanText(node.nodeCode) && cleanText(node.metricCode))
    .map<PathwayMetricBindingDraft>((node) => ({
      nodeCode: cleanText(node.nodeCode) ?? "",
      metricCode: cleanText(node.metricCode) ?? "",
      required: true,
    }));
}

function buildDraftDsl(nodes?: PathwayNodeFormValue[], edges?: PathwayEdgeFormValue[]) {
  return {
    nodes: normalizeNodes(nodes),
    edges: normalizeEdges(edges),
    metricBindings: normalizeMetricBindings(nodes),
  };
}

function buildDraftDslPreview(nodes?: PathwayNodeFormValue[], edges?: PathwayEdgeFormValue[]) {
  let normalizedEdges: PathwayEdgeDraft[] = [];
  try {
    normalizedEdges = normalizeEdges(edges);
  } catch {
    normalizedEdges = [];
  }
  return formatJson({
    nodes: normalizeNodes(nodes),
    edges: normalizedEdges,
    metricBindings: normalizeMetricBindings(nodes),
  });
}

function buildDetailDslPreview(detail: PathwayTemplateDetailResponse) {
  return formatJson({
    template: {
      templateCode: detail.template.templateCode,
      diseaseCode: detail.template.diseaseCode,
      templateLevel: detail.template.templateLevel,
      templateVersion: detail.template.templateVersion,
      startNodeCode: detail.template.startNodeCode,
      sourceRef: detail.template.sourceRef,
    },
    nodes: detail.nodes.map((node) => ({
      nodeCode: node.nodeCode,
      name: node.name,
      nodeType: node.nodeType,
      sortOrder: node.sortOrder,
      responsibleRole: node.responsibleRole,
      timeWindowMinutes: node.timeWindowMinutes,
      terminal: node.terminalFlag,
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
  });
}

function mappingEntries(mapping?: Record<string, string>) {
  return Object.entries(mapping ?? {});
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
      (payload.nodes !== undefined && !Array.isArray(payload.nodes)) ||
      (payload.edges !== undefined && !Array.isArray(payload.edges)) ||
      (payload.metricBindings !== undefined && !Array.isArray(payload.metricBindings))
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
      conditionJson: condition === undefined ? undefined : formatJson(condition),
    };
  });

  return {
    nodes: (payload.nodes ?? []).map<PathwayNodeFormValue>((node, index) => ({
      nodeCode: cleanText(node.nodeCode),
      name: cleanText(node.name),
      nodeType: node.nodeType ?? "ASSESSMENT",
      sortOrder: Number(node.sortOrder ?? index + 1),
      responsibleRole: cleanText(node.responsibleRole),
      timeWindowMinutes:
        typeof node.timeWindowMinutes === "number" && node.timeWindowMinutes > 0
          ? node.timeWindowMinutes
          : undefined,
      terminal: Boolean(node.terminal),
      metricCode: metricByNode.get(node.nodeCode),
    })),
    edges: edgeValues,
  };
}

function PathwayCanvasPreview({
  nodes,
  edges,
  emptyText,
}: {
  nodes: PathwayCanvasNode[];
  edges: PathwayCanvasEdge[];
  emptyText: string;
}) {
  const orderedNodes = [...nodes].sort((left, right) => left.sortOrder - right.sortOrder);
  if (orderedNodes.length === 0) {
    return (
      <div className="border border-slate-200 rounded-lg bg-slate-50 p-6">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
      </div>
    );
  }

  return (
    <div className="border border-slate-200 rounded-lg bg-slate-50 p-4 overflow-x-auto">
      <div className="flex items-stretch gap-3 min-w-max">
        {orderedNodes.map((node, index) => {
          const nextNode = orderedNodes[index + 1];
          const directEdge = edges.find(
            (edge) => edge.fromNodeCode === node.nodeCode && edge.toNodeCode === nextNode?.nodeCode,
          );
          return (
            <div key={node.nodeCode || `${node.name}-${index}`} className="flex items-center gap-3">
              <div className="w-[180px] min-h-[104px] rounded-lg border border-emerald-200 bg-white p-3 shadow-sm">
                <Space direction="vertical" size={4} className="mk-full-width">
                  <Space className="mk-flex-between mk-full-width">
                    <Tag color="blue">{node.nodeCode || "未编码"}</Tag>
                    {node.terminal && <Tag color="green">终止</Tag>}
                  </Space>
                  <div className="text-sm font-semibold text-gray-800">
                    {node.name || "未命名节点"}
                  </div>
                  <div className="text-xs font-normal text-gray-500">{node.nodeType}</div>
                </Space>
              </div>
              {nextNode && (
                <div className="w-[88px] text-center text-xs font-normal text-gray-500">
                  <div className="h-px bg-emerald-300 mb-2" />
                  {directEdge?.edgeCode ?? "顺序流转"}
                </div>
              )}
            </div>
          );
        })}
      </div>
      {edges.length > 0 && (
        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-2">
          {edges.map((edge, index) => (
            <div
              key={edge.edgeCode || `${edge.fromNodeCode}-${edge.toNodeCode}-${index}`}
              className="rounded-md border border-gray-200 bg-white px-3 py-2 text-xs font-normal text-gray-600"
            >
              <span className="font-semibold text-gray-800">
                {edge.fromNodeCode || "未设置源节点"}
              </span>
              <span className="mx-2">→</span>
              <span className="font-semibold text-gray-800">
                {edge.toNodeCode || "未设置目标节点"}
              </span>
              <Tag color="cyan" className="ml-2">
                {edge.edgeType}
              </Tag>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function PathwayTemplates() {
  const { message: messageApi, modal } = App.useApp();
  const [page, setPage] = useState<number>(1);
  const [size] = useState<number>(10);

  const [statusFilter, setStatusFilter] = useState<PathwayTemplateStatus | undefined>(undefined);
  const [diseaseFilter, setDiseaseFilter] = useState<string>("");
  const [packageFilter, setPackageFilter] = useState<string>("");

  const [packageDrawerVisible, setPackageDrawerVisible] = useState<boolean>(false);
  const [createTemplateVisible, setCreateTemplateVisible] = useState<boolean>(false);
  const [fieldManagerOpen, setFieldManagerOpen] = useState<boolean>(false);
  const [createExpertMode, setCreateExpertMode] = useState<boolean>(false);
  const [detailExpertMode, setDetailExpertMode] = useState<boolean>(false);
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
  const [pathwayDslJson, setPathwayDslJson] = useState<string>(() => buildDraftDslPreview([], []));
  const [simulationResponse, setSimulationResponse] = useState<PathwaySimulationResponse | null>(
    null,
  );

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
  const { data: rollbackTargetsData } = usePathwayTemplates(
    {
      status: "OFFLINE",
      templateCode: detailData?.template.templateCode,
      page: 1,
      size: 100,
    },
    {
      enabled: detailData?.template.status === "PUBLISHED" && !!detailData.template.templateCode,
    },
  );

  const { data: packagesData, refetch: refetchPackages } = useSpecialtyPackages({
    page: 1,
    size: 100,
  });

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

  const createPackageMutation = useCreateSpecialtyPackage();
  const createTemplateMutation = useCreatePathwayTemplate();
  const publishTemplateMutation = usePublishPathwayTemplate();
  const fullRolloutMutation = useFullRolloutPathwayTemplate();
  const rollbackMutation = useRollbackPathwayTemplate();
  const simulateMutation = useSimulatePathway(selectedTemplateId || "");

  const [packageForm] = Form.useForm();
  const [templateForm] = Form.useForm<PathwayTemplateFormValue>();
  const watchedNodes = Form.useWatch("nodes", templateForm);
  const watchedEdges = Form.useWatch("edges", templateForm);

  const canvasNodes = useMemo(() => normalizeNodes(watchedNodes), [watchedNodes]);
  const canvasEdges = useMemo(() => normalizeEdgesForCanvas(watchedEdges), [watchedEdges]);

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

  // 自动生成不重复的顺序编码（节点 N1/N2…，边 E1/E2…），可改但默认不必手填。
  const nextSeqCode = (
    listName: "nodes" | "edges",
    field: "nodeCode" | "edgeCode",
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

  const packageVersionFor = (packageId?: string | null) =>
    packagesData?.items?.find((pkg) => pkg.packageId === packageId)?.packageVersion;

  const resetSimulation = () => {
    setSnapshotPatientId("");
    setSnapshotEncounterId("");
    setSnapshotQuery(null);
    setSelectedSnapshotId(null);
    setSimulationResponse(null);
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
      messageApi.success("专病包资产草稿创建成功");
      packageForm.resetFields();
      refetchPackages();
    } catch (error: unknown) {
      if (applyApiFieldErrors(packageForm, error)) return;
      messageApi.error(getApiErrorMessage(error, "创建专病包失败，请检查参数"));
    }
  };

  const handleCreateTemplate = async () => {
    try {
      const values = await templateForm.validateFields();
      const nodes = normalizeNodes(values.nodes);
      const edges = normalizeEdges(values.edges);
      const metricBindings = normalizeMetricBindings(values.nodes);
      const nodeCodes = new Set(nodes.map((node) => node.nodeCode));
      if (nodes.length === 0) {
        messageApi.error("请至少添加一个生命周期节点");
        return;
      }
      if (!nodeCodes.has(values.startNodeCode)) {
        messageApi.error("起始节点编码必须来自 L2 节点画布");
        return;
      }
      const timedNodeWithoutMetric = nodes.find(
        (node) =>
          (node.timeWindowMinutes ?? 0) > 0 &&
          !metricBindings.some((binding) => binding.nodeCode === node.nodeCode),
      );
      if (timedNodeWithoutMetric) {
        messageApi.error(`节点 ${timedNodeWithoutMetric.nodeCode} 设置时窗后必须绑定时钟指标编码`);
        return;
      }

      await createTemplateMutation.mutateAsync({
        packageId: values.packageId,
        templateCode: values.templateCode,
        name: values.name,
        diseaseCode: values.diseaseCode,
        packageVersion: packageVersionFor(values.packageId) ?? String(values.templateVersion),
        templateLevel: values.templateLevel,
        templateVersion: Number(values.templateVersion),
        startNodeCode: values.startNodeCode,
        sourceRef: values.sourceRef,
        description: values.description ?? "",
        entryCriteria: {},
        exitCriteria: {},
        nodes,
        edges,
        metricBindings,
      });

      messageApi.success("专病路径模板草稿创建成功");
      setCreateTemplateVisible(false);
      templateForm.resetFields();
      setPathwayDslJson(buildDraftDslPreview([], []));
      refetchList();
    } catch (error: unknown) {
      if (error instanceof Error && error.message.includes("条件 DSL JSON")) {
        messageApi.error(error.message);
        return;
      }
      if (applyApiFieldErrors(templateForm, error)) return;
      messageApi.error(getApiErrorMessage(error, "创建路径模板失败"));
    }
  };

  const syncCanvasToDsl = () => {
    try {
      const values = templateForm.getFieldsValue();
      setPathwayDslJson(formatJson(buildDraftDsl(values.nodes, values.edges)));
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
      setPathwayDslJson(buildDraftDslPreview(formValues.nodes, formValues.edges));
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
    try {
      await publishTemplateMutation.mutateAsync({
        templateId: selectedTemplateId,
        packageVersion:
          packageVersionFor(detailData?.template.packageId) ??
          String(detailData?.template.templateVersion ?? ""),
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

  const releasePackageVersion = () =>
    packageVersionFor(detailData?.template.packageId) ??
    String(detailData?.template.templateVersion ?? "");

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
    try {
      await fullRolloutMutation.mutateAsync({
        templateId: selectedTemplateId,
        packageVersion: releasePackageVersion(),
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
    try {
      await rollbackMutation.mutateAsync({
        templateId: selectedTemplateId,
        packageVersion: releasePackageVersion(),
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
  };

  const handleSimulate = async () => {
    if (!selectedTemplateId) return;
    if (!selectedSnapshotId) {
      messageApi.error("请先选择一个 ACTIVE 上下文快照");
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
        packageVersion:
          selectedSnapshotDetail?.pathwayPackageVersion ??
          selectedSnapshotDetail?.packageVersion ??
          packageVersionFor(detailData?.template.packageId) ??
          String(detailData?.template.templateVersion ?? ""),
        snapshotId: selectedSnapshotId,
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
      className: "font-semibold text-gray-800",
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
      title: "版本",
      dataIndex: "templateVersion",
      key: "templateVersion",
      render: (value: number) => `v${value}.0`,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: PathwayTemplateStatus) => {
        const config: Record<PathwayTemplateStatus, { status: PathwayBadgeStatus; text: string }> =
          {
            DRAFT: { status: "warning", text: "设计中" },
            PUBLISHED: { status: "success", text: "运行中" },
            OFFLINE: { status: "default", text: "已下线" },
          };
        const fallback = { status: "default" as PathwayBadgeStatus, text: status };
        return (
          <Badge
            status={(config[status] ?? fallback).status}
            text={(config[status] ?? fallback).text}
          />
        );
      },
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
          className="text-emerald-600 hover:text-emerald-900 font-semibold"
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
    { title: "名称", dataIndex: "name", className: "font-semibold" },
    {
      title: "节点类型",
      dataIndex: "nodeType",
      render: (type: PathwayNodeType) => <Tag color="purple">{type}</Tag>,
    },
    {
      title: "时窗",
      dataIndex: "timeWindowMinutes",
      render: (minutes?: number) => (minutes ? `${minutes} 分钟` : "无"),
    },
    { title: "责任角色", dataIndex: "responsibleRole" },
    {
      title: "终止",
      dataIndex: "terminalFlag",
      render: (terminal: boolean) => (terminal ? "是" : "否"),
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
        <span className="font-normal text-xs">{cleanText(condition) ?? "默认流转"}</span>
      ),
    },
    { title: "优先级", dataIndex: "priority" },
  ];

  const metricColumns: TableProps<SpecialtyMetricBinding>["columns"] = [
    { title: "节点代码", dataIndex: "nodeCode" },
    { title: "指标编码", dataIndex: "metricCode" },
  ];

  const createLayerItems: TabsProps["items"] = [
    {
      key: "l1",
      label: "L1 模板",
      children: (
        <div className="pt-3">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="packageId" label="归属专病包" rules={[{ required: true }]}>
                <Select placeholder="选择包">
                  {packagesData?.items?.map((pkg: SpecialtyPackage) => (
                    <Option key={pkg.packageId} value={pkg.packageId}>
                      {pkg.name} (v{pkg.packageVersion})
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="路径模型名称" rules={[{ required: true }]}>
                <Input placeholder="输入路径模型名称" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="templateCode" label="路径模型代码" rules={[{ required: true }]}>
                <Input placeholder="输入路径模型代码" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="diseaseCode" label="病种代码" rules={[{ required: true }]}>
                <Input placeholder="输入真实病种编码" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="templateLevel" label="路径层级" rules={[{ required: true }]}>
                <Select options={templateLevelOptions} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="templateVersion" label="模板版本号" rules={[{ required: true }]}>
                <InputNumber min={1} precision={0} className="w-full" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startNodeCode" label="起始节点" rules={[{ required: true }]}>
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
            <Input placeholder="输入已审核指南、院内制度或配置包来源" />
          </Form.Item>
          <Form.Item name="description" label="收治标准与排除指标">
            <TextArea rows={3} placeholder="输入路径说明" />
          </Form.Item>
        </div>
      ),
    },
    {
      key: "l2",
      label: "L2 节点画布",
      children: (
        <div className="pt-3">
          <Space direction="vertical" size="middle" className="mk-full-width mb-4">
            <Space className="mk-flex-between mk-full-width">
              <div className="text-sm font-semibold text-gray-800">结构化节点画布</div>
              <Space>
                <Button icon={<SwapOutlined />} onClick={syncCanvasToDsl}>
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
            <PathwayCanvasPreview
              nodes={canvasNodes}
              edges={canvasEdges}
              emptyText="添加节点后形成路径画布"
            />
          </Space>

          <Form.List name="nodes">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="middle" className="mk-full-width">
                {fields.length === 0 && <Empty description="尚未添加路径节点" />}
                {fields.map((field) => {
                  const { key, ...fieldProps } = field;
                  return (
                    <div key={key} className="border border-gray-200 rounded-lg p-4">
                      <Space align="start" className="mk-flex-between mk-full-width">
                        <Tag color="blue">节点 {field.name + 1}</Tag>
                        <Button
                          aria-label={`删除节点 ${field.name + 1}`}
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                      <Row gutter={12} className="mt-3">
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "nodeCode"]}
                            label="节点编码"
                            tooltip="新增时自动生成（N1/N2…），可改；用于边连接与起点引用"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="自动生成，可改" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "name"]}
                            label="节点名称"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="输入节点名称" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "nodeType"]}
                            label="节点类型"
                            rules={[{ required: true }]}
                          >
                            <Select options={nodeTypeOptions} />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "sortOrder"]}
                            label="顺序"
                            rules={[{ required: true }]}
                          >
                            <InputNumber min={1} precision={0} className="w-full" />
                          </Form.Item>
                        </Col>
                      </Row>
                      <Row gutter={12}>
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "responsibleRole"]}
                            label="责任角色"
                          >
                            <Input placeholder="输入真实岗位或角色" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "timeWindowMinutes"]}
                            label="时窗分钟"
                          >
                            <InputNumber min={0} precision={0} className="w-full" />
                          </Form.Item>
                        </Col>
                        <Col span={8}>
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
                            <Input placeholder="设置时窗时必填" />
                          </Form.Item>
                        </Col>
                        <Col span={4}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "terminal"]}
                            label="终止节点"
                            valuePropName="checked"
                          >
                            <Switch />
                          </Form.Item>
                        </Col>
                      </Row>
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
                      terminal: false,
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
                  return (
                    <div key={key} className="border border-gray-200 rounded-lg p-4">
                      <Space align="start" className="mk-flex-between mk-full-width">
                        <Tag color="green">流转边 {field.name + 1}</Tag>
                        <Button
                          aria-label={`删除流转边 ${field.name + 1}`}
                          icon={<DeleteOutlined />}
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                      <Row gutter={12} className="mt-3">
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "edgeCode"]}
                            label="边编码"
                            tooltip="新增时自动生成（E1/E2…），可改"
                            rules={[{ required: true }]}
                          >
                            <Input placeholder="自动生成，可改" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
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
                        <Col span={6}>
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
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "edgeType"]}
                            label="流转类型"
                            rules={[{ required: true }]}
                          >
                            <Select options={edgeTypeOptions} />
                          </Form.Item>
                        </Col>
                      </Row>
                      <Row gutter={12}>
                        <Col span={6}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "priority"]}
                            label="优先级"
                            rules={[{ required: true }]}
                          >
                            <InputNumber min={1} precision={0} className="w-full" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
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
                              placeholder="从字段目录选择或输入，如 observations[].valueNumeric"
                            />
                          </Form.Item>
                        </Col>
                        <Col span={5}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "conditionOperator"]}
                            label="条件算子"
                          >
                            <Select options={pathwayConditionOperatorOptions} />
                          </Form.Item>
                        </Col>
                        <Col span={4}>
                          <Form.Item
                            {...fieldProps}
                            name={[field.name, "conditionValueKind"]}
                            label="值类型"
                          >
                            <Select options={pathwayConditionValueKindOptions} />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
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
                                <Input placeholder="如 true" />
                              );
                            })()}
                          </Form.Item>
                        </Col>
                      </Row>
                    </div>
                  );
                })}
                <Button
                  icon={<PlusOutlined />}
                  onClick={() =>
                    add({
                      edgeCode: nextSeqCode("edges", "edgeCode", "E"),
                      edgeType: "DEFAULT",
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
    ...(createExpertMode
      ? [
          {
            key: "l3",
            label: "L3 DSL",
            children: (
              <div className="pt-3">
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <Space className="mk-flex-between mk-full-width">
                    <div className="text-sm font-semibold text-gray-800">路径 DSL JSON</div>
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
                    message="L3 是专家模式，普通路径配置请优先使用 L2 节点画布。"
                  />
                  <Form.Item label="路径 DSL JSON" htmlFor="pathway-dsl-json" className="mb-0">
                    <TextArea
                      id="pathway-dsl-json"
                      value={pathwayDslJson}
                      rows={18}
                      onChange={(event) => setPathwayDslJson(event.target.value)}
                      className="font-normal text-xs"
                    />
                  </Form.Item>
                </Space>
              </div>
            ),
          },
        ]
      : []),
  ];

  const snapshotList = snapshotsData?.items ?? [];
  const selectedStartNode =
    cleanText(simulateStartNode) ??
    cleanText(detailData?.template.startNodeCode) ??
    cleanText(detailData?.nodes[0]?.nodeCode) ??
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
  const releaseCurrentStep =
    detailData?.template.status === "PUBLISHED" ? "full_rollout" : "submit_review";
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
      <Descriptions bordered column={2} size="small">
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
        <Descriptions.Item label="灰度比例">灰度发布默认 10%</Descriptions.Item>
        <Descriptions.Item label="回滚凭据">保留本次 impactDigest</Descriptions.Item>
      </Descriptions>
    );
  }

  const releaseStepPanel = (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Alert
        type="info"
        showIcon
        message="路径发布将先进入 10% 灰度，并保留回滚证据。"
        description="全量发布必须基于本次影响摘要和审计记录继续推进，不能跳过影响核查。"
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
            className="bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
          >
            提交审核并进入灰度发布
          </Button>
        ) : (
          <Space direction="vertical" size="small" className="mk-full-width">
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={handleFullRolloutTemplate}
              loading={fullRolloutMutation.isPending}
              disabled={!releaseImpact?.impactDigest || !cleanText(releaseReason)}
              className="bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
            >
              院级确认全量发布
            </Button>
            <Form.Item label="回滚目标版本" className="mb-0">
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

  const detailLayerItems: TabsProps["items"] = detailData
    ? [
        {
          key: "l1",
          label: "L1 模板",
          children: (
            <Descriptions bordered column={2} className="mt-3">
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
                {detailData.template.templateLevel}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Badge
                  status={detailData.template.status === "PUBLISHED" ? "success" : "warning"}
                  text={detailData.template.status}
                />
              </Descriptions.Item>
              <Descriptions.Item label="起始节点">
                {detailData.template.startNodeCode ?? "未设置"}
              </Descriptions.Item>
              <Descriptions.Item label="知识来源">
                {detailData.template.sourceRef}
              </Descriptions.Item>
              <Descriptions.Item label="说明" span={2}>
                {detailData.template.description || "未填写"}
              </Descriptions.Item>
            </Descriptions>
          ),
        },
        {
          key: "l2",
          label: "L2 节点画布",
          children: (
            <Space direction="vertical" size="large" className="mk-full-width mt-3">
              <PathwayCanvasPreview
                nodes={detailData.nodes.map((node) => ({
                  nodeCode: node.nodeCode,
                  name: node.name,
                  nodeType: node.nodeType,
                  sortOrder: node.sortOrder,
                  terminal: node.terminalFlag,
                }))}
                edges={detailData.edges.map((edge) => ({
                  edgeCode: edge.edgeCode,
                  fromNodeCode: edge.fromNodeCode,
                  toNodeCode: edge.toNodeCode,
                  edgeType: edge.edgeType,
                  priority: edge.priority,
                }))}
                emptyText="该模板尚未返回路径节点"
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
              <Table
                dataSource={detailData.metricBindings}
                rowKey="bindingId"
                pagination={false}
                size="small"
                columns={metricColumns}
                locale={{ emptyText: "暂无时钟指标绑定" }}
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
                  <TextArea
                    value={buildDetailDslPreview(detailData)}
                    rows={22}
                    readOnly
                    className="font-normal text-xs mt-3"
                  />
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
            <Row gutter={16} className="mt-3">
              <Col span={9}>
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <div className="border border-gray-200 rounded-lg p-4">
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

                  <div className="border border-gray-200 rounded-lg p-3 min-h-[160px]">
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
                              setSimulationResponse(null);
                            }}
                            className="mk-full-width text-left"
                          >
                            <span>{snapshot.snapshotId}</span>
                            <Tag className="ml-2">{snapshot.qualityStatus}</Tag>
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
              <Col span={15}>
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <div className="border border-gray-200 rounded-lg p-4">
                    <Row gutter={12}>
                      <Col span={12}>
                        <Form layout="vertical">
                          <Form.Item label="试运行起点节点">
                            <Select value={selectedStartNode} onChange={setSimulateStartNode}>
                              {detailData.nodes.map((node) => (
                                <Option key={node.nodeCode} value={node.nodeCode}>
                                  {node.name} ({node.nodeCode})
                                </Option>
                              ))}
                            </Select>
                          </Form.Item>
                        </Form>
                      </Col>
                      <Col span={12}>
                        <Button
                          type="primary"
                          icon={<PlayCircleOutlined />}
                          onClick={handleSimulate}
                          loading={simulateMutation.isPending || selectedSnapshotLoading}
                          disabled={!selectedSnapshotId}
                          className="mk-full-width mt-7 bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
                        >
                          使用该快照试运行
                        </Button>
                      </Col>
                    </Row>
                    {selectedSnapshotDetail && (
                      <Descriptions bordered size="small" column={2}>
                        <Descriptions.Item label="快照">
                          {selectedSnapshotDetail.snapshotId}
                        </Descriptions.Item>
                        <Descriptions.Item label="状态">
                          {selectedSnapshotDetail.status}
                        </Descriptions.Item>
                        <Descriptions.Item label="质量">
                          {selectedSnapshotDetail.qualityStatus}
                        </Descriptions.Item>
                        <Descriptions.Item label="路径包版本">
                          {selectedSnapshotDetail.pathwayPackageVersion ??
                            selectedSnapshotDetail.packageVersion ??
                            "未返回"}
                        </Descriptions.Item>
                      </Descriptions>
                    )}
                  </div>

                  <div className="border border-gray-200 rounded-lg p-4 min-h-[240px]">
                    {simulationResponse ? (
                      <Space direction="vertical" size="middle" className="mk-full-width">
                        <Tag color={simulationQuality === "COMPLETE" ? "green" : "orange"}>
                          快照质量：{simulationQuality}
                        </Tag>
                        {simulationMapping.length > 0 && (
                          <Descriptions bordered size="small" column={1}>
                            {simulationMapping.map(([key, status]) => (
                              <Descriptions.Item key={key} label={key}>
                                {status}
                              </Descriptions.Item>
                            ))}
                          </Descriptions>
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
                                  <div className="font-semibold text-gray-800 text-xs">
                                    {nodeDetail?.name ?? "未知节点"}
                                  </div>
                                  <div className="text-gray-500 text-xs font-normal mt-0.5">
                                    {nodeCode}
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
            <div className="mt-3">
              <StepFlow
                currentStep={releaseCurrentStep}
                panelByStep={
                  releaseCurrentStep === "full_rollout"
                    ? { full_rollout: releaseStepPanel }
                    : { submit_review: releaseStepPanel }
                }
                status={releaseImpact?.impactDigest ? "process" : "error"}
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
      <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-100 mb-6">
        <Form layout="inline" className="flex flex-wrap gap-4 items-center w-full">
          <Form.Item label="状态">
            <Select
              placeholder="选择状态"
              allowClear
              value={statusFilter}
              onChange={setStatusFilter}
              className="w-[140px]"
            >
              <Option value="DRAFT">设计中</Option>
              <Option value="PUBLISHED">运行中</Option>
              <Option value="OFFLINE">已下线</Option>
            </Select>
          </Form.Item>
          <Form.Item label="病种编码">
            <Input
              placeholder="输入真实病种编码"
              allowClear
              value={diseaseFilter}
              onChange={(event) => setDiseaseFilter(event.target.value)}
              className="w-[140px]"
            />
          </Form.Item>
          <Form.Item label="归属专病包">
            <Select
              placeholder="全部专病包"
              allowClear
              value={packageFilter}
              onChange={setPackageFilter}
              className="w-[200px]"
            >
              {packagesData?.items?.map((pkg: SpecialtyPackage) => (
                <Option key={pkg.packageId} value={pkg.packageId}>
                  {pkg.name} ({pkg.packageVersion})
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item className="ml-auto flex gap-2">
            <Button
              icon={<FolderOpenOutlined />}
              onClick={() => setPackageDrawerVisible(true)}
              className="rounded-lg font-medium border-emerald-500 text-emerald-600 hover:bg-emerald-50"
            >
              管理专病包
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                templateForm.resetFields();
                templateForm.setFieldsValue({
                  templateLevel: "STANDARD",
                  templateVersion: 1,
                  nodes: [],
                  edges: [],
                });
                setPathwayDslJson(buildDraftDslPreview([], []));
                setCreateExpertMode(false);
                setCreateTemplateVisible(true);
              }}
              className="rounded-lg font-medium bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
            >
              新建路径模板
            </Button>
          </Form.Item>
        </Form>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-100 overflow-hidden">
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
        title="租户专病包资产管理"
        width={560}
        onClose={() => setPackageDrawerVisible(false)}
        open={packageDrawerVisible}
        destroyOnClose
      >
        <Alert
          message="专病包是临床路径和质控资产的容器实体，受租户级别数据隔离、版本升级和灰度发布控制。"
          type="info"
          showIcon
          className="mb-6 rounded-lg"
        />

        <Card title="新建专病包草稿" className="mb-6 border-gray-200 shadow-sm rounded-lg">
          <Form form={packageForm} layout="vertical" onFinish={handleCreatePackage}>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name="packageCode" label="专病包编码" rules={[{ required: true }]}>
                  <Input placeholder="输入专病包编码" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="diseaseCode" label="病种代码 (ICD)" rules={[{ required: true }]}>
                  <Input placeholder="输入真实病种代码" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="name" label="专病包名称" rules={[{ required: true }]}>
              <Input placeholder="输入专病包名称" />
            </Form.Item>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name="packageVersion" label="版本" rules={[{ required: true }]}>
                  <Input placeholder="输入版本号" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="sourceRef" label="知识来源" rules={[{ required: true }]}>
                  <Input placeholder="输入已审核指南、院内制度或配置包来源" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="description" label="功能说明与收治摘要">
              <TextArea rows={2} placeholder="输入专病画像说明" />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<PlusOutlined />}
              loading={createPackageMutation.isPending}
              className="w-full bg-emerald-600 border-emerald-600 hover:bg-emerald-700 mt-2"
            >
              提交创建并留痕审计
            </Button>
          </Form>
        </Card>

        <div className="font-semibold text-gray-800 mb-3">已有专病包列表</div>
        <Space direction="vertical" className="mk-full-width overflow-y-auto max-h-[300px]">
          {packagesData?.items?.map((pkg: SpecialtyPackage) => (
            <Card
              key={pkg.packageId}
              size="small"
              className="border-gray-100 bg-gray-50 rounded-lg shadow-sm"
            >
              <Descriptions size="small" column={1} bordered={false}>
                <Descriptions.Item label="名称">
                  <span className="font-semibold text-gray-800">{pkg.name}</span>
                </Descriptions.Item>
                <Descriptions.Item label="包编码">
                  <span className="font-normal text-xs">{pkg.packageCode}</span>
                </Descriptions.Item>
                <Descriptions.Item label="病种/版本">
                  <Tag color="cyan">{pkg.diseaseCode}</Tag>
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
        onCancel={() => setCreateTemplateVisible(false)}
        width={980}
        confirmLoading={createTemplateMutation.isPending}
        destroyOnClose
      >
        <Form form={templateForm} layout="vertical" className="mt-4">
          <Space className="mk-flex-between mk-full-width mb-4">
            <span className="text-sm text-gray-500">
              普通配置只展示 L1/L2；L3 DSL 需显式进入专家模式。
            </span>
            <Space>
              <span>专家模式</span>
              <Switch
                aria-label="专家模式"
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
          <div className="flex items-center justify-between w-full">
            <span>路径配置与真实快照试运行控制台</span>
            {detailData?.template.status === "DRAFT" && (
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => setDetailActiveTab("release")}
                className="mr-6 bg-emerald-600 border-emerald-600 hover:bg-emerald-700"
              >
                进入 7 步流发布
              </Button>
            )}
          </div>
        }
        width={1080}
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
              message={
                detailData.template.status === "PUBLISHED"
                  ? "当前临床路径已上线，拓扑结构写保护。如需修改，请发布新版本包升级。"
                  : "当前临床路径处于设计中，可核查三层模型并使用真实上下文快照试运行后申请发布。"
              }
              type={detailData.template.status === "PUBLISHED" ? "success" : "info"}
              showIcon
              className="mb-6 rounded-lg"
            />
            <Space className="mk-flex-between mk-full-width mb-4">
              <span className="text-sm text-gray-500">
                路径拓扑、试运行和发布为普通主流程；完整 DSL 仅在专家模式显示。
              </span>
              <Space>
                <span>专家模式</span>
                <Switch
                  aria-label="专家模式"
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
