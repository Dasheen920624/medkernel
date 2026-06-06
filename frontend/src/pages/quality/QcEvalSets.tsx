import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
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
  usePublishEvaluationIndicator,
  useSubmitEvaluationIndicator,
  type ContextSnapshotSummary,
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
import { StepFlow } from "@/shared/ui/StepFlow";
import type { StepKey } from "@/shared/ui/StepFlow.contract";
import ConditionTreeEditor from "@/shared/ui/condition/ConditionTreeEditor";

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
  ACTIVE: "生效中",
  OFFLINE: "已下线",
  ARCHIVED: "已归档",
};

const STATUS_COLORS: Record<EvaluationIndicatorStatus, string> = {
  DRAFT: "default",
  PENDING_REVIEW: "warning",
  PUBLISHED: "processing",
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
  return <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status] ?? status}</Tag>;
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
  const [form] = Form.useForm<IndicatorFormValues>();
  const [status, setStatus] = useState<EvaluationIndicatorStatus | undefined>();
  const [subjectType, setSubjectType] = useState<EvaluationSubjectType | undefined>();
  const [indicatorCode, setIndicatorCode] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
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
      render: (value: EvaluationSubjectType) => SUBJECT_LABELS[value] ?? value,
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
      title: "traceId",
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
      await submitMutation.mutateAsync(indicatorId);
      message.success("指标已提交审核");
      indicatorsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "指标提交审核失败"));
    }
  }

  async function publishIndicator(indicatorId: string) {
    try {
      await publishMutation.mutateAsync(indicatorId);
      message.success("指标已发布");
      indicatorsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "指标发布失败"));
    }
  }

  async function activateIndicator(indicatorId: string) {
    try {
      await activateMutation.mutateAsync(indicatorId);
      message.success("指标已激活，同编码旧版本由后端下线");
      indicatorsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "指标激活失败"));
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
        <Text type="secondary">{visibleIndicator?.traceId ?? "暂无审计 traceId"}</Text>
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
            ? "请稍后重试，或带 traceId 联系信息科核查。"
            : "正在读取 EVAL-01 指标版本台账。",
          traceId: parsedError?.traceId,
          onRetry: () => indicatorsQuery.refetch(),
        }}
      >
        <Space direction="vertical" size="large" className="mk-full-width">
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
              className="mk-select-compact"
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
              className="mk-select-compact"
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
          <Space direction="vertical" size="large" className="mk-full-width">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                <InputNumber min={1} className="mk-full-width" />
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
                rules={[{ required: true, message: "请输入责任科室" }]}
              >
                <Input />
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
          <Space direction="vertical" size="large" className="mk-full-width">
            <Space wrap className="mk-full-width mk-flex-between">
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
                    aria-label="发布指标"
                    type="primary"
                    loading={publishMutation.isPending}
                    onClick={() => publishIndicator(selectedIndicator.indicatorId)}
                  >
                    发布指标
                  </Button>
                )}
                {selectedIndicator.status === "PUBLISHED" && (
                  <Button
                    aria-label="激活指标"
                    type="primary"
                    loading={activateMutation.isPending}
                    onClick={() => activateIndicator(selectedIndicator.indicatorId)}
                  >
                    激活指标
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
              <Descriptions.Item label="traceId">
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

      <Drawer
        title="仿真评估"
        placement="right"
        width={760}
        open={simulationOpen}
        onClose={() => setSimulationOpen(false)}
      >
        <Space direction="vertical" size="large" className="mk-full-width">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Form.Item label="患者 ID" htmlFor="eval-snapshot-patient">
              <Input
                id="eval-snapshot-patient"
                value={snapshotPatientId}
                onChange={(event) => setSnapshotPatientId(event.target.value)}
              />
            </Form.Item>
            <Form.Item label="就诊 ID" htmlFor="eval-snapshot-encounter">
              <Input
                id="eval-snapshot-encounter"
                value={snapshotEncounterId}
                onChange={(event) => setSnapshotEncounterId(event.target.value)}
              />
            </Form.Item>
            <Form.Item label="临床快照 ID" htmlFor="eval-snapshot-id">
              <Input
                id="eval-snapshot-id"
                value={simulationSnapshotId}
                onChange={(event) => setSimulationSnapshotId(event.target.value)}
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

          {renderSnapshotList(
            hasSnapshotFilter,
            snapshotsQuery.isLoading,
            snapshotsQuery.isError,
            snapshotsQuery.data?.items ?? [],
            simulationSnapshotId,
            setSimulationSnapshotId,
          )}

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
              <Descriptions.Item label="运行状态">{simulationResult.status}</Descriptions.Item>
              <Descriptions.Item label="结果数">{simulationResult.resultCount}</Descriptions.Item>
              <Descriptions.Item label="缺陷数">{simulationResult.findingCount}</Descriptions.Item>
              <Descriptions.Item label="整改任务">{simulationResult.taskCount}</Descriptions.Item>
              <Descriptions.Item label="traceId">{simulationResult.traceId}</Descriptions.Item>
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

function renderSnapshotList(
  enabled: boolean,
  loading: boolean,
  error: boolean,
  snapshots: ContextSnapshotSummary[],
  selectedSnapshotId: string,
  onSelect: (snapshotId: string) => void,
) {
  if (!enabled) {
    return <Empty description="输入患者 ID 或就诊 ID 后读取 ACTIVE 临床快照" />;
  }
  if (loading) {
    return <Alert type="info" message="正在读取临床快照" showIcon />;
  }
  if (error) {
    return <Alert type="error" message="临床快照读取失败" showIcon />;
  }
  if (snapshots.length === 0) {
    return <Empty description="当前患者或就诊下暂无 ACTIVE 临床快照" />;
  }
  return (
    <List
      bordered
      dataSource={snapshots}
      renderItem={(snapshot) => (
        <List.Item
          actions={[
            <Button
              key="select"
              aria-label={`选择 ${snapshot.snapshotId}`}
              type={selectedSnapshotId === snapshot.snapshotId ? "primary" : "default"}
              onClick={() => onSelect(snapshot.snapshotId)}
            >
              选择
            </Button>,
          ]}
        >
          <List.Item.Meta
            title={snapshot.snapshotId}
            description={`患者 ${snapshot.patientId} · 就诊 ${snapshot.encounterId} · ${snapshot.qualityStatus}`}
          />
        </List.Item>
      )}
    />
  );
}
