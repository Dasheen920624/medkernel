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
  InputNumber,
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

import styles from "./Quality.module.css";

const { Option } = Select;
const { Text, Title } = Typography;

interface IndicatorFormValues {
  indicatorCode: string;
  versionNo: number;
  name: string;
  subjectType: EvaluationSubjectType;
  timeWindow: string;
  organizationScope: string;
  responsibleDepartmentId: string;
  sourceRef: string;
  packageVersion?: string;
  scoringDefinition?: string;
}

type IndicatorReleaseAction = "PUBLISH" | "GRAY" | "ACTIVATE";

const RELEASE_ACTION_TITLE: Record<IndicatorReleaseAction, string> = {
  PUBLISH: "审核通过",
  GRAY: "开始 10% 床位灰度",
  ACTIVATE: "全量激活",
};

const RELEASE_ACTION_OK_TEXT: Record<IndicatorReleaseAction, string> = {
  PUBLISH: "确认发布",
  GRAY: "确认灰度",
  ACTIVATE: "确认全量",
};

const RELEASE_ACTION_SUCCESS: Record<IndicatorReleaseAction, string> = {
  PUBLISH: "指标审核发布完成",
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
  PENDING_REVIEW: "待审核",
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
  return `v${indicator.versionNo}${indicator.packageVersion ? ` · ${indicator.packageVersion}` : ""}`;
}

export default function QcEvalSets() {
  const { message } = App.useApp();
  const [form] = Form.useForm<IndicatorFormValues>();
  const departmentsQuery = useOrgUnits({ page: 1, size: 100, sort: "name,asc" });
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
  const [simulationPackageVersion, setSimulationPackageVersion] = useState("");
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
      title: "指标编码",
      dataIndex: "indicatorCode",
      key: "indicatorCode",
      render: (value: string) => <Text strong>{value}</Text>,
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
      title: "追踪号",
      dataIndex: "traceId",
      key: "traceId",
      render: (value?: string) => <Text type="secondary">{value ?? "N/A"}</Text>,
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
        versionNo: Number(values.versionNo),
        name: values.name.trim(),
        subjectType: values.subjectType,
        denominatorDefinition: JSON.stringify(nodeToDsl(denominatorTree)),
        numeratorDefinition: JSON.stringify(nodeToDsl(numeratorTree)),
        scoringDefinition: optionalText(values.scoringDefinition),
        timeWindow: values.timeWindow.trim(),
        organizationScope: values.organizationScope.trim(),
        responsibleDepartmentId: values.responsibleDepartmentId.trim(),
        sourceRef: values.sourceRef.trim(),
        packageVersion: optionalText(values.packageVersion),
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
      message.success("指标已提交审核");
      indicatorsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "指标提交审核失败"));
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
        packageVersion: optionalText(simulationPackageVersion),
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
          <Descriptions.Item label="指标">{visibleIndicator.indicatorCode}</Descriptions.Item>
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
        <Text type="secondary">{STATUS_LABELS[visibleIndicator.status]} · 医务处审核流转。</Text>
      ) : (
        <Text type="secondary">暂无审核对象。</Text>
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
        <Text type="secondary">{visibleIndicator?.traceId ?? "暂无审计追踪号"}</Text>
      ),
    }),
    [total, visibleIndicator],
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
          <>
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
          </>
        }
        state={pageState}
        stateProps={{
          title: parsedError?.message ?? "正在加载评估指标",
          description: parsedError
            ? "请稍后重试，或凭追踪号联系信息科核查。"
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
              aria-label="指标编码筛选"
              placeholder="指标编码"
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
            versionNo: 1,
            subjectType: "MEDICAL_RECORD",
            timeWindow: "DISCHARGE+24H",
            organizationScope: "全院",
          }}
        >
          <Space direction="vertical" size="large" className={styles.fullWidth}>
            <div className={styles.formGrid}>
              <Form.Item
                name="indicatorCode"
                label="指标编码"
                rules={[{ required: true, message: "请输入指标编码" }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                name="versionNo"
                label="版本号"
                rules={[{ required: true, message: "请输入版本号" }]}
              >
                <InputNumber min={1} className={styles.fullWidth} />
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
                  optionFilterProp="label"
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
              <Form.Item name="packageVersion" label="配置包版本">
                <Input />
              </Form.Item>
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
                <Text strong>{selectedIndicator.indicatorCode}</Text>
              </Space>
              <Space wrap>
                {selectedIndicator.status === "DRAFT" && (
                  <Button
                    aria-label="提交审核"
                    type="primary"
                    loading={submitMutation.isPending}
                    onClick={() => submitIndicator(selectedIndicator.indicatorId)}
                  >
                    提交审核
                  </Button>
                )}
                {selectedIndicator.status === "PENDING_REVIEW" && (
                  <Button
                    aria-label="审核通过"
                    type="primary"
                    loading={publishMutation.isPending}
                    onClick={() => openRelease("PUBLISH")}
                  >
                    审核通过
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
              <Descriptions.Item label="追踪号">
                {selectedIndicator.traceId ?? "N/A"}
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
          placeholder="填写审核结论、灰度依据或全量批准说明"
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
            <Form.Item label="患者 ID" htmlFor="eval-snapshot-patient">
              <Input
                id="eval-snapshot-patient"
                value={snapshotPatientId}
                onChange={(event) => {
                  setSnapshotPatientId(event.target.value);
                  setSimulationSnapshotId("");
                }}
              />
            </Form.Item>
            <Form.Item label="就诊 ID" htmlFor="eval-snapshot-encounter">
              <Input
                id="eval-snapshot-encounter"
                value={snapshotEncounterId}
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
            <Form.Item label="配置包版本" htmlFor="eval-package-version">
              <Input
                id="eval-package-version"
                value={simulationPackageVersion}
                onChange={(event) => setSimulationPackageVersion(event.target.value)}
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
              <Descriptions.Item label="运行 ID">{simulationResult.runId}</Descriptions.Item>
              <Descriptions.Item label="运行状态">
                {customerEnumLabel(simulationResult.status)}
              </Descriptions.Item>
              <Descriptions.Item label="结果数">{simulationResult.resultCount}</Descriptions.Item>
              <Descriptions.Item label="缺陷数">{simulationResult.findingCount}</Descriptions.Item>
              <Descriptions.Item label="整改任务">{simulationResult.taskCount}</Descriptions.Item>
              <Descriptions.Item label="追踪号">{simulationResult.traceId}</Descriptions.Item>
            </Descriptions>
          )}
        </Space>
      </Drawer>
    </>
  );
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
