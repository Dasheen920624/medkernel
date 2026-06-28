import { useMemo, useState } from "react";
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  BranchesOutlined,
  FileSearchOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from "@ant-design/icons";

import { applyApiFieldErrors, getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import {
  useActivateEvaluationIndicator,
  useContextSnapshots,
  useCreateEvaluationIndicator,
  useEvaluateSnapshot,
  useEvaluationIndicators,
  useGrayEvaluationIndicator,
  useOrgUnits,
  usePublishEvaluationIndicator,
  useSecurityProfile,
  useSubmitEvaluationIndicator,
  type EvaluationIndicator,
  type EvaluationIndicatorStatus,
  type EvaluationRunResponse,
  type EvaluationSubjectType,
} from "@/shared/api/hooks";
import {
  createDefaultTree,
  dslToRootGroup,
  nodeToDsl,
  validateTree,
  type RuleGroup,
} from "@/shared/config/conditionModel";
import { PageShell } from "@/shared/ui/PageShell";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { ContextSnapshotSelector } from "@/shared/ui/ContextSnapshotSelector";
import { StepFlow } from "@/shared/ui/StepFlow";
import type { StepKey } from "@/shared/ui/StepFlow.contract";
import ConditionTreeEditor from "@/shared/ui/condition/ConditionTreeEditor";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";

import styles from "./Quality.module.css";

const { Option } = Select;
const { Text, Title } = Typography;

interface IndicatorFormValues {
  indicatorCode: string;
  name: string;
  subjectType: EvaluationSubjectType;
  timeWindow: string;
  organizationScope: string;
  responsibleDepartmentId: string;
  sourceRef: string;
  scoringDefinition?: string;
}

type IndicatorReleaseAction = "PUBLISH" | "GRAY" | "ACTIVATE";

const RELEASE_ACTION_TITLE: Record<IndicatorReleaseAction, string> = {
  PUBLISH: "确认发布",
  GRAY: "开始 10% 床位灰度",
  ACTIVATE: "全量激活",
};

const RELEASE_ACTION_OK_TEXT: Record<IndicatorReleaseAction, string> = {
  PUBLISH: "确认发布",
  GRAY: "确认灰度",
  ACTIVATE: "确认全量",
};

const RELEASE_ACTION_SUCCESS: Record<IndicatorReleaseAction, string> = {
  PUBLISH: "指标已确认发布",
  GRAY: "指标已进入 10% 床位灰度",
  ACTIVATE: "指标已全量激活",
};

const SUBJECT_LABELS: Record<EvaluationSubjectType, string> = {
  PATIENT: "患者主体",
  MEDICAL_RECORD: "临床病历",
  DEPARTMENT: "科室质控",
  DOCTOR: "医师效能",
  DISEASE: "专病包",
  PATHWAY: "临床路径",
  CLAIM: "医保合规",
  FOLLOWUP: "随访结果",
};

const STATUS_LABELS: Record<EvaluationIndicatorStatus, string> = {
  DRAFT: "草稿",
  PENDING_REVIEW: "待安全复核",
  PUBLISHED: "已发布",
  GRAY: "灰度中",
  ACTIVE: "生效中",
  OFFLINE: "已下线",
  ARCHIVED: "已归档",
};

const STATUS_COLORS: Record<EvaluationIndicatorStatus, string> = {
  DRAFT: "default",
  PENDING_REVIEW: "warning",
  PUBLISHED: "processing",
  GRAY: "cyan",
  ACTIVE: "success",
  OFFLINE: "error",
  ARCHIVED: "default",
};
const DEPARTMENT_REFERENCE_PAGE_SIZE = 20;

function optionalText(value?: string) {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
}

function getResponseStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null || !("response" in error)) return undefined;
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === "number" ? response.status : undefined;
}

function statusTag(status: EvaluationIndicatorStatus) {
  return (
    <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status] ?? customerEnumLabel(status)}</Tag>
  );
}

function resolvePageState(isLoading: boolean, isError: boolean, responseStatus?: number) {
  if (isLoading) return "loading";
  if (!isError) return "ready";
  return responseStatus === 403 ? "forbidden" : "error";
}

function stepForIndicator(indicator?: EvaluationIndicator | null): StepKey {
  if (!indicator) return "select_template";
  if (indicator.status === "DRAFT") return "auto_validate";
  if (indicator.status === "PENDING_REVIEW") return "submit_review";
  if (indicator.status === "PUBLISHED") return "canary_release";
  if (indicator.status === "GRAY") return "full_rollout";
  if (indicator.status === "ACTIVE") return "full_rollout";
  return "evidence_rollback";
}

function parseDefinitionTree(definition?: string): RuleGroup | null {
  const normalized = definition?.trim();
  if (!normalized) return null;
  try {
    return dslToRootGroup(JSON.parse(normalized));
  } catch {
    return null;
  }
}

function formatVersion(indicator: EvaluationIndicator) {
  return `v${indicator.versionNo}`;
}

export default function QcEvalSets() {
  const { message } = App.useApp();
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const canViewEvidenceDetails = canUseEvidenceDetails(security.data);
  const evidenceDetailsEnabled = canViewEvidenceDetails && globalEvidenceDetails;
  const [form] = Form.useForm<IndicatorFormValues>();
  const [departmentSearch, setDepartmentSearch] = useState("");
  const departmentKeyword = departmentSearch.trim();
  const departmentsQuery = useOrgUnits({
    page: 1,
    size: DEPARTMENT_REFERENCE_PAGE_SIZE,
    sort: "name,asc",
    level: "DEPARTMENT",
    status: "ACTIVE",
    ...(departmentKeyword ? { keyword: departmentKeyword } : {}),
  });
  const departmentOptions = (departmentsQuery.data?.items ?? [])
    .filter((unit) => unit.level === "DEPARTMENT" && unit.status === "ACTIVE" && Boolean(unit.id))
    .map((unit) => ({
      value: unit.id as string,
      label: `${unit.name} · ${unit.code}`,
    }));
  const [status, setStatus] = useState<EvaluationIndicatorStatus | undefined>();
  const [subjectType, setSubjectType] = useState<EvaluationSubjectType | undefined>();
  const [indicatorCode, setIndicatorCode] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [releaseAction, setReleaseAction] = useState<IndicatorReleaseAction | null>(null);
  const [releaseReason, setReleaseReason] = useState("");
  const [simulationOpen, setSimulationOpen] = useState(false);
  const [selectedIndicator, setSelectedIndicator] = useState<EvaluationIndicator | null>(null);
  const [denominatorTree, setDenominatorTree] = useState<RuleGroup>(() => createDefaultTree());
  const [numeratorTree, setNumeratorTree] = useState<RuleGroup>(() => createDefaultTree());
  const [snapshotPatientId, setSnapshotPatientId] = useState("");
  const [snapshotEncounterId, setSnapshotEncounterId] = useState("");
  const [simulationSnapshotId, setSimulationSnapshotId] = useState("");
  const [simulationScenarioCode, setSimulationScenarioCode] = useState("DISCHARGE");
  const [simulationResult, setSimulationResult] = useState<EvaluationRunResponse | null>(null);

  const indicatorParams = useMemo(
    () => ({
      status,
      subjectType,
      indicatorCode: optionalText(indicatorCode),
      page: 1,
      size: 20,
      sort: "updatedAt,desc",
    }),
    [indicatorCode, status, subjectType],
  );
  const indicatorsQuery = useEvaluationIndicators(indicatorParams);
  const createMutation = useCreateEvaluationIndicator();
  const submitMutation = useSubmitEvaluationIndicator();
  const publishMutation = usePublishEvaluationIndicator();
  const grayMutation = useGrayEvaluationIndicator();
  const activateMutation = useActivateEvaluationIndicator();
  const evaluateSnapshotMutation = useEvaluateSnapshot();

  const patientFilter = snapshotPatientId.trim();
  const encounterFilter = snapshotEncounterId.trim();
  const hasSnapshotFilter = Boolean(patientFilter || encounterFilter);
  const snapshotsQuery = useContextSnapshots(
    {
      patientId: patientFilter || undefined,
      encounterId: encounterFilter || undefined,
      status: "ACTIVE",
      page: 1,
      size: 20,
    },
    { enabled: hasSnapshotFilter },
  );

  const indicators = indicatorsQuery.data?.items ?? [];
  const total = indicatorsQuery.data?.total ?? indicators.length;
  const visibleIndicator = selectedIndicator ?? indicators[0] ?? null;
  const parsedError = indicatorsQuery.isError
    ? parseApiError(indicatorsQuery.error, "评估指标读取失败")
    : null;
  const pageState = resolvePageState(
    indicatorsQuery.isLoading,
    indicatorsQuery.isError,
    getResponseStatus(indicatorsQuery.error),
  );

  const columns: ColumnsType<EvaluationIndicator> = [
    {
      title: "指标",
      dataIndex: "indicatorCode",
      key: "indicatorCode",
      render: (value: string) => (
        <Text strong>{evidenceText(value, evidenceDetailsEnabled, "指标已登记")}</Text>
      ),
    },
    {
      title: "指标名称",
      dataIndex: "name",
      key: "name",
    },
    {
      title: "版本",
      key: "version",
      render: (_, record) => formatVersion(record),
    },
    {
      title: "评估主体",
      dataIndex: "subjectType",
      key: "subjectType",
      render: (value: EvaluationSubjectType) => SUBJECT_LABELS[value] ?? customerEnumLabel(value),
    },
    {
      title: "责任科室",
      dataIndex: "responsibleDepartmentId",
      key: "responsibleDepartmentId",
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (value: EvaluationIndicatorStatus) => statusTag(value),
    },
    {
      title: "证据",
      dataIndex: "traceId",
      key: "traceId",
      render: (value?: string) => (
        <Text type="secondary">
          {evidenceText(value, evidenceDetailsEnabled, "指标证据已记录")}
        </Text>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Button
          aria-label="查看指标详情"
          type="link"
          icon={<FileSearchOutlined aria-hidden="true" />}
          onClick={() => {
            setSelectedIndicator(record);
            setDetailOpen(true);
          }}
        >
          查看指标详情
        </Button>
      ),
    },
  ];

  function openCreateModal() {
    form.resetFields();
    setDenominatorTree(createDefaultTree());
    setNumeratorTree(createDefaultTree());
    setCreateOpen(true);
  }

  async function createIndicator(values: IndicatorFormValues) {
    const denominatorValidation = validateTree(denominatorTree);
    const numeratorValidation = validateTree(numeratorTree);
    const errors = [...denominatorValidation.errors, ...numeratorValidation.errors];
    if (errors.length > 0) {
      message.error(errors.join("；"));
      return;
    }

    try {
      await createMutation.mutateAsync({
        indicatorCode: values.indicatorCode.trim(),
        name: values.name.trim(),
        subjectType: values.subjectType,
        denominatorDefinition: JSON.stringify(nodeToDsl(denominatorTree)),
        numeratorDefinition: JSON.stringify(nodeToDsl(numeratorTree)),
        scoringDefinition: optionalText(values.scoringDefinition),
        timeWindow: values.timeWindow.trim(),
        organizationScope: values.organizationScope.trim(),
        responsibleDepartmentId: values.responsibleDepartmentId.trim(),
        sourceRef: values.sourceRef.trim(),
      });
      message.success("评估指标草稿已创建");
      setCreateOpen(false);
      indicatorsQuery.refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(form, error)) return;
      message.error(getApiErrorMessage(error, "评估指标创建失败"));
    }
  }

  async function submitIndicator(indicatorId: string) {
    try {
      const updated = await submitMutation.mutateAsync(indicatorId);
      setSelectedIndicator(updated);
      message.success("指标已提交安全复核");
      indicatorsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "指标提交安全复核失败"));
    }
  }

  function openRelease(action: IndicatorReleaseAction) {
    setReleaseAction(action);
    setReleaseReason("");
  }

  async function confirmRelease() {
    if (!selectedIndicator || !releaseAction) return;
    const reason = releaseReason.trim();
    if (!reason) {
      message.warning("请填写发布说明");
      return;
    }
    try {
      const payload = { indicatorId: selectedIndicator.indicatorId, reason };
      let updated: EvaluationIndicator;
      if (releaseAction === "PUBLISH") {
        updated = await publishMutation.mutateAsync(payload);
      } else if (releaseAction === "GRAY") {
        updated = await grayMutation.mutateAsync(payload);
      } else {
        updated = await activateMutation.mutateAsync(payload);
      }
      setSelectedIndicator(updated);
      setReleaseAction(null);
      setReleaseReason("");
      message.success(RELEASE_ACTION_SUCCESS[releaseAction]);
      indicatorsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "指标发布动作失败"));
    }
  }

  async function runSimulation() {
    const contextSnapshotId = simulationSnapshotId.trim();
    if (!contextSnapshotId) {
      message.warning("请选择真实临床快照");
      return;
    }
    try {
      const response = await evaluateSnapshotMutation.mutateAsync({
        contextSnapshotId,
        scenarioCode: simulationScenarioCode.trim(),
      });
      setSimulationResult(response);
      message.success("仿真评估已完成");
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "仿真评估失败"));
    }
  }

  const stepPanels = useMemo(
    () => ({
      select_template: <Text type="secondary">当前查询返回 {total} 个真实指标版本。</Text>,
      auto_validate: visibleIndicator ? (
        <Descriptions size="small" column={1}>
          <Descriptions.Item label="指标">
            {evidenceText(visibleIndicator.indicatorCode, evidenceDetailsEnabled, "指标已登记")}
          </Descriptions.Item>
          <Descriptions.Item label="分母条件">
            {parseDefinitionTree(visibleIndicator.denominatorDefinition)
              ? "条件树可解析"
              : "未配置"}
          </Descriptions.Item>
          <Descriptions.Item label="分子条件">
            {parseDefinitionTree(visibleIndicator.numeratorDefinition) ? "条件树可解析" : "未配置"}
          </Descriptions.Item>
        </Descriptions>
      ) : (
        <Text type="secondary">暂无指标版本。</Text>
      ),
      impact_preview: visibleIndicator ? (
        <Text type="secondary">
          {visibleIndicator.responsibleDepartmentId} · {visibleIndicator.timeWindow} ·{" "}
          {visibleIndicator.organizationScope}
        </Text>
      ) : (
        <Text type="secondary">暂无影响范围。</Text>
      ),
      submit_review: visibleIndicator ? (
        <Text type="secondary">
          {STATUS_LABELS[visibleIndicator.status]} · 当前授权责任人安全复核。
        </Text>
      ) : (
        <Text type="secondary">暂无待安全复核对象。</Text>
      ),
      canary_release: visibleIndicator ? (
        <Text type="secondary">发布版本 {formatVersion(visibleIndicator)}</Text>
      ) : (
        <Text type="secondary">暂无发布版本。</Text>
      ),
      full_rollout: visibleIndicator ? (
        <Text type="secondary">状态 {STATUS_LABELS[visibleIndicator.status]}</Text>
      ) : (
        <Text type="secondary">暂无生效版本。</Text>
      ),
      evidence_rollback: (
        <Text type="secondary">
          {evidenceText(visibleIndicator?.traceId, evidenceDetailsEnabled, "指标证据已记录")}
        </Text>
      ),
    }),
    [evidenceDetailsEnabled, total, visibleIndicator],
  );

  return (
    <>
      <PageShell
        title="评估指标库"
        description="按真实指标版本维护质控口径"
        primary={
          <Button
            aria-label="新建指标"
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreateModal}
          >
            新建指标
          </Button>
        }
        extras={
          <Space wrap>
            {canViewEvidenceDetails && <EvidenceDetailsToggle securityProfile={security.data} />}
            <Button
              aria-label="仿真评估"
              icon={<PlayCircleOutlined />}
              onClick={() => {
                setSimulationResult(null);
                setSimulationOpen(true);
              }}
            >
              仿真评估
            </Button>
            <Button
              aria-label="刷新指标"
              icon={<ReloadOutlined />}
              onClick={() => indicatorsQuery.refetch()}
            >
              刷新
            </Button>
          </Space>
        }
        state={pageState}
        stateProps={{
          title: parsedError?.message ?? "正在加载评估指标",
          description: parsedError
            ? "请稍后重试；若持续失败，请联系信息科核查评价指标服务。失败已留痕，可在审计证据中追溯。"
            : "正在读取 EVAL-01 指标版本台账。",
          traceId: parsedError?.traceId,
          onRetry: () => indicatorsQuery.refetch(),
        }}
      >
        <Space direction="vertical" size="large" className={styles.fullWidth}>
          <Space wrap>
            <Card size="small">
              <Text type="secondary">真实评估指标总数</Text>
              <Title level={3} className="mk-title-tight">
                {total}
              </Title>
            </Card>
            <Card size="small">
              <Text type="secondary">当前筛选状态</Text>
              <Title level={5} className="mk-title-tight">
                {status ? STATUS_LABELS[status] : "全部状态"}
              </Title>
            </Card>
            <Card size="small">
              <Text type="secondary">当前评估主体</Text>
              <Title level={5} className="mk-title-tight">
                {subjectType ? SUBJECT_LABELS[subjectType] : "全部主体"}
              </Title>
            </Card>
          </Space>

          <Space wrap>
            <Input
              aria-label="评价指标身份筛选"
              placeholder="按指标名称或稳定身份检索"
              value={indicatorCode}
              onChange={(event) => setIndicatorCode(event.target.value)}
            />
            <Select
              aria-label="指标状态"
              allowClear
              placeholder="指标状态"
              value={status}
              onChange={setStatus}
              className={styles.controlSm}
            >
              {(Object.keys(STATUS_LABELS) as EvaluationIndicatorStatus[]).map((item) => (
                <Option key={item} value={item}>
                  {STATUS_LABELS[item]}
                </Option>
              ))}
            </Select>
            <Select
              aria-label="评估主体"
              allowClear
              placeholder="评估主体"
              value={subjectType}
              onChange={setSubjectType}
              className={styles.controlSm}
            >
              {(Object.keys(SUBJECT_LABELS) as EvaluationSubjectType[]).map((item) => (
                <Option key={item} value={item}>
                  {SUBJECT_LABELS[item]}
                </Option>
              ))}
            </Select>
          </Space>

          <Card title="指标台账">
            <Table
              rowKey={(record) => record.indicatorId}
              columns={columns}
              dataSource={indicators}
              loading={indicatorsQuery.isLoading}
              pagination={{ pageSize: 20, total, showSizeChanger: false }}
              locale={{ emptyText: <Empty description="当前筛选下暂无真实评估指标" /> }}
            />
          </Card>

          <StepFlow currentStep={stepForIndicator(visibleIndicator)} panelByStep={stepPanels} />
        </Space>
      </PageShell>

      <Modal
        title="新建评估指标"
        open={createOpen}
        width={980}
        okText="创建指标草稿"
        cancelText="取消"
        confirmLoading={createMutation.isPending}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={createIndicator}
          initialValues={{
            subjectType: "MEDICAL_RECORD",
            timeWindow: "DISCHARGE+24H",
            organizationScope: "全院",
          }}
        >
          <Space direction="vertical" size="large" className={styles.fullWidth}>
            <div className={styles.formGrid}>
              <Form.Item
                name="indicatorCode"
                label="稳定评价指标身份"
                rules={[{ required: true, message: "请输入稳定评价指标身份" }]}
                extra="用于版本发布、质控追溯和跨机构迁移；默认台账仍按指标名称与业务状态展示。"
              >
                <Input placeholder="输入稳定评价指标身份" />
              </Form.Item>
              <Form.Item
                name="name"
                label="指标名称"
                rules={[{ required: true, message: "请输入指标名称" }]}
              >
                <Input />
              </Form.Item>
              <Form.Item name="subjectType" label="评估主体">
                <Select>
                  {(Object.keys(SUBJECT_LABELS) as EvaluationSubjectType[]).map((item) => (
                    <Option key={item} value={item}>
                      {SUBJECT_LABELS[item]}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
              <Form.Item
                name="responsibleDepartmentId"
                label="责任科室"
                rules={[{ required: true, message: "请选择责任科室" }]}
              >
                <Select
                  showSearch
                  filterOption={false}
                  onSearch={setDepartmentSearch}
                  onClear={() => setDepartmentSearch("")}
                  placeholder="选择责任科室"
                  options={departmentOptions}
                  loading={departmentsQuery.isLoading}
                  notFoundContent="暂无可选科室"
                />
              </Form.Item>
              <Form.Item
                name="timeWindow"
                label="时间窗口"
                rules={[{ required: true, message: "请输入时间窗口" }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                name="organizationScope"
                label="组织范围"
                rules={[{ required: true, message: "请输入组织范围" }]}
              >
                <Input />
              </Form.Item>
              <Alert
                type="info"
                showIcon
                message="指标版本独立维护"
                description="创建时只形成指标草稿版本；通过发布治理后，再由机构生效版本确定真正上线的版本。"
              />
            </div>
            <Form.Item
              name="sourceRef"
              label="来源依据"
              rules={[{ required: true, message: "请输入来源依据" }]}
            >
              <Input />
            </Form.Item>
            <Form.Item name="scoringDefinition" label="评分定义">
              <Input />
            </Form.Item>
            <Card title="分母条件树">
              <ConditionTreeEditor value={denominatorTree} onChange={setDenominatorTree} />
            </Card>
            <Card title="分子条件树">
              <ConditionTreeEditor value={numeratorTree} onChange={setNumeratorTree} />
            </Card>
          </Space>
        </Form>
      </Modal>

      <Drawer
        title="指标详情"
        placement="right"
        width={820}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {selectedIndicator ? (
          <Space direction="vertical" size="large" className={styles.fullWidth}>
            <Space wrap className={styles.rowBetween}>
              <Space>
                <BranchesOutlined />
                {statusTag(selectedIndicator.status)}
                <Text strong>
                  {evidenceText(
                    selectedIndicator.indicatorCode,
                    evidenceDetailsEnabled,
                    "指标已登记",
                  )}
                </Text>
              </Space>
              <Space wrap>
                {selectedIndicator.status === "DRAFT" && (
                  <Button
                    aria-label="提交安全复核"
                    type="primary"
                    loading={submitMutation.isPending}
                    onClick={() => submitIndicator(selectedIndicator.indicatorId)}
                  >
                    提交安全复核
                  </Button>
                )}
                {selectedIndicator.status === "PENDING_REVIEW" && (
                  <Button
                    aria-label="确认发布"
                    type="primary"
                    loading={publishMutation.isPending}
                    onClick={() => openRelease("PUBLISH")}
                  >
                    确认发布
                  </Button>
                )}
                {selectedIndicator.status === "PUBLISHED" && (
                  <Button
                    aria-label="开始灰度"
                    type="primary"
                    loading={grayMutation.isPending}
                    onClick={() => openRelease("GRAY")}
                  >
                    开始灰度
                  </Button>
                )}
                {selectedIndicator.status === "GRAY" && (
                  <Button
                    aria-label="全量激活"
                    type="primary"
                    loading={activateMutation.isPending}
                    onClick={() => openRelease("ACTIVATE")}
                  >
                    全量激活
                  </Button>
                )}
              </Space>
            </Space>
            <Descriptions bordered column={1}>
              <Descriptions.Item label="指标证据">
                {evidenceText(
                  selectedIndicator.indicatorId,
                  evidenceDetailsEnabled,
                  "指标已登记",
                )}
              </Descriptions.Item>
              <Descriptions.Item label="指标名称">{selectedIndicator.name}</Descriptions.Item>
              <Descriptions.Item label="版本">{formatVersion(selectedIndicator)}</Descriptions.Item>
              <Descriptions.Item label="评估主体">
                {SUBJECT_LABELS[selectedIndicator.subjectType]}
              </Descriptions.Item>
              <Descriptions.Item label="时间窗口">{selectedIndicator.timeWindow}</Descriptions.Item>
              <Descriptions.Item label="组织范围">
                {selectedIndicator.organizationScope}
              </Descriptions.Item>
              <Descriptions.Item label="责任科室">
                {selectedIndicator.responsibleDepartmentId}
              </Descriptions.Item>
              <Descriptions.Item label="来源依据">{selectedIndicator.sourceRef}</Descriptions.Item>
              <Descriptions.Item label="证据">
                {evidenceText(
                  selectedIndicator.traceId,
                  evidenceDetailsEnabled,
                  "指标证据已记录",
                )}
              </Descriptions.Item>
            </Descriptions>
            <StepFlow currentStep={stepForIndicator(selectedIndicator)} panelByStep={stepPanels} />
            {renderDefinitionCard("分母条件树", selectedIndicator.denominatorDefinition)}
            {renderDefinitionCard("分子条件树", selectedIndicator.numeratorDefinition)}
            {selectedIndicator.scoringDefinition && (
              <Card title="评分定义">
                <Text>{selectedIndicator.scoringDefinition}</Text>
              </Card>
            )}
          </Space>
        ) : (
          <Empty description="未选择指标" />
        )}
      </Drawer>

      <Modal
        title={releaseAction ? RELEASE_ACTION_TITLE[releaseAction] : "发布动作"}
        open={releaseAction !== null}
        okText={releaseAction ? RELEASE_ACTION_OK_TEXT[releaseAction] : "确认"}
        cancelText="取消"
        confirmLoading={
          publishMutation.isPending || grayMutation.isPending || activateMutation.isPending
        }
        onOk={confirmRelease}
        onCancel={() => {
          setReleaseAction(null);
          setReleaseReason("");
        }}
      >
        <Input.TextArea
          aria-label="发布说明"
          value={releaseReason}
          onChange={(event) => setReleaseReason(event.target.value)}
          maxLength={500}
          rows={4}
          placeholder="填写安全复核结论、灰度依据或全量确认说明"
        />
      </Modal>

      <Drawer
        title="仿真评估"
        placement="right"
        width={760}
        open={simulationOpen}
        onClose={() => setSimulationOpen(false)}
      >
        <Space direction="vertical" size="large" className={styles.fullWidth}>
          <div className={styles.formGrid}>
            <Form.Item label="患者信息" htmlFor="eval-snapshot-patient">
              <Input
                id="eval-snapshot-patient"
                value={snapshotPatientId}
                placeholder="输入患者主索引或院内登记号检索临床快照"
                onChange={(event) => {
                  setSnapshotPatientId(event.target.value);
                  setSimulationSnapshotId("");
                }}
              />
            </Form.Item>
            <Form.Item label="就诊信息" htmlFor="eval-snapshot-encounter">
              <Input
                id="eval-snapshot-encounter"
                value={snapshotEncounterId}
                placeholder="可按住院号、门诊号或就诊标识检索"
                onChange={(event) => {
                  setSnapshotEncounterId(event.target.value);
                  setSimulationSnapshotId("");
                }}
              />
            </Form.Item>
            <Form.Item label="触发场景" htmlFor="eval-scenario-code">
              <Input
                id="eval-scenario-code"
                value={simulationScenarioCode}
                onChange={(event) => setSimulationScenarioCode(event.target.value)}
              />
            </Form.Item>
          </div>

          <ContextSnapshotSelector
            enabled={hasSnapshotFilter}
            loading={snapshotsQuery.isLoading}
            error={snapshotsQuery.isError}
            snapshots={snapshotsQuery.data?.items ?? []}
            selectedSnapshotId={simulationSnapshotId}
            onSelect={setSimulationSnapshotId}
          />

          <Button
            aria-label="执行仿真评估"
            type="primary"
            icon={<PlayCircleOutlined />}
            loading={evaluateSnapshotMutation.isPending}
            onClick={runSimulation}
          >
            执行仿真评估
          </Button>

          {simulationResult && (
            <Descriptions bordered column={1}>
              <Descriptions.Item label="评估运行">
                {evidenceText(
                  simulationResult.runId,
                  evidenceDetailsEnabled,
                  "评估运行已记录",
                )}
              </Descriptions.Item>
              <Descriptions.Item label="运行状态">
                {customerEnumLabel(simulationResult.status)}
              </Descriptions.Item>
              <Descriptions.Item label="结果数">{simulationResult.resultCount}</Descriptions.Item>
              <Descriptions.Item label="缺陷数">{simulationResult.findingCount}</Descriptions.Item>
              <Descriptions.Item label="整改任务">{simulationResult.taskCount}</Descriptions.Item>
              <Descriptions.Item label="证据">
                {evidenceText(
                  simulationResult.traceId,
                  evidenceDetailsEnabled,
                  "仿真证据已记录",
                )}
              </Descriptions.Item>
            </Descriptions>
          )}
        </Space>
      </Drawer>
    </>
  );
}

function evidenceText(
  value: string | null | undefined,
  evidenceDetailsEnabled: boolean,
  businessText: string,
) {
  if (evidenceDetailsEnabled) {
    return value || "--";
  }
  return businessText;
}

function renderDefinitionCard(title: string, definition?: string) {
  const tree = parseDefinitionTree(definition);
  if (!tree) {
    return (
      <Card title={title}>
        <Alert type="info" message="未配置可解析条件树" showIcon />
      </Card>
    );
  }
  return (
    <Card title={title}>
      <ConditionTreeEditor value={tree} onChange={() => undefined} readOnly />
    </Card>
  );
}
