import {
  CopyOutlined,
  ExperimentOutlined,
  PlusOutlined,
  ReloadOutlined,
  RocketOutlined,
  RollbackOutlined,
} from "@ant-design/icons";
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Descriptions,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import { useMemo, useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useApplyOverrideBatch,
  useCreateOverrideTemplate,
  useObserveReleaseRollout,
  useOrgUnits,
  useOverrideTemplates,
  usePreviewOverrideBatch,
  useReleaseSimulation,
  useRevokeOverrideBatch,
  useRollbackRollout,
  useStartReleaseRollout,
  type EngineAssetType,
  type OverrideBatchOperationResult,
  type OverrideBatchPreviewRequest,
  type OverrideBatchPreviewResult,
  type ReleaseSimulationRequest,
  type ReleaseSimulationResult,
  type RolloutPolicy,
  type RolloutStrategy,
  type VersionReleasePlan,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import styles from "./ReleaseGovernance.module.css";

const { Text, Title } = Typography;
const { TextArea } = Input;
const OVERRIDE_TEMPLATE_PAGE_SIZE = 20;

const assetTypes: Array<{ value: EngineAssetType; label: string }> = [
  { value: "RULE", label: "规则" },
  { value: "PATHWAY", label: "路径" },
  { value: "TERMINOLOGY", label: "术语" },
  { value: "KNOWLEDGE", label: "知识" },
  { value: "EVALUATION", label: "评估" },
  { value: "FOLLOWUP", label: "随访" },
  { value: "FIELD_CATALOG", label: "字段目录" },
  { value: "PACKAGE", label: "配置包" },
];

const strategyOptions: Array<{ value: RolloutStrategy; label: string }> = [
  { value: "CANARY_BED_PERCENT", label: "床位比例" },
  { value: "ORG_LIST", label: "机构清单" },
  { value: "ORG_SUBTREE", label: "组织子树" },
  { value: "STAGED", label: "分批放量" },
];

type SimulationFormValues = {
  assetType: EngineAssetType;
  assetIdentity: string;
  candidateVersionId: string;
  applicableScope: string;
  targetOrgUnitIds: string[];
  strategy: RolloutStrategy;
  bedPercent: number;
  stages: string;
  observationMinutes: number;
  maxBlockRate: number;
  maxManualRejectionRate: number;
  maxAnomalyRate: number;
  replayDays: number;
  replayLimit: number;
};

type StartRolloutValues = {
  reviewConclusion: string;
};

type ObservationValues = {
  sampleCount: number;
  hitCount: number;
  blockCount: number;
  manualRejectionCount: number;
  anomalyCount: number;
};

type RollbackValues = {
  reason: string;
  confirmedHighRisk: boolean;
};

type BatchValues = {
  mode: "TEMPLATE" | "CLONE";
  templateId?: string;
  sourceOrgUnitId?: string;
  targetOrgUnitIds: string[];
};

function rolloutPolicy(values: SimulationFormValues): RolloutPolicy {
  if (values.strategy === "CANARY_BED_PERCENT") {
    return {
      strategy: values.strategy,
      orgUnitIds: [],
      bedPercent: values.bedPercent,
      stages: [],
    };
  }
  if (values.strategy === "ORG_LIST" || values.strategy === "ORG_SUBTREE") {
    return {
      strategy: values.strategy,
      orgUnitIds:
        values.strategy === "ORG_SUBTREE"
          ? values.targetOrgUnitIds.slice(0, 1)
          : values.targetOrgUnitIds,
      stages: [],
    };
  }
  return {
    strategy: "STAGED",
    orgUnitIds: values.targetOrgUnitIds,
    stages: values.stages
      .split(",")
      .map((value) => Number(value.trim()))
      .filter((value) => Number.isInteger(value)),
    observationMinutes: values.observationMinutes,
    thresholds: {
      maxBlockRate: values.maxBlockRate,
      maxManualRejectionRate: values.maxManualRejectionRate,
      maxAnomalyRate: values.maxAnomalyRate,
    },
  };
}

function checkTag(passed: boolean) {
  return <Tag color={passed ? "success" : "error"}>{passed ? "通过" : "阻断"}</Tag>;
}

export default function ReleaseGovernance() {
  const { message } = AntdApp.useApp();
  const [orgKeyword, setOrgKeyword] = useState("");
  const [templatePage, setTemplatePage] = useState(1);
  const orgUnitsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: orgKeyword || undefined,
    status: "ACTIVE",
  });
  const templatesQuery = useOverrideTemplates({
    page: templatePage,
    size: OVERRIDE_TEMPLATE_PAGE_SIZE,
  });
  const simulateMutation = useReleaseSimulation();
  const startMutation = useStartReleaseRollout();
  const observeMutation = useObserveReleaseRollout();
  const rollbackMutation = useRollbackRollout();
  const createTemplateMutation = useCreateOverrideTemplate();
  const previewMutation = usePreviewOverrideBatch();
  const applyMutation = useApplyOverrideBatch();
  const revokeMutation = useRevokeOverrideBatch();
  const [simulationForm] = Form.useForm<SimulationFormValues>();
  const [startForm] = Form.useForm<StartRolloutValues>();
  const [observationForm] = Form.useForm<ObservationValues>();
  const [rollbackForm] = Form.useForm<RollbackValues>();
  const [templateForm] = Form.useForm();
  const [batchForm] = Form.useForm<BatchValues>();
  const [simulation, setSimulation] = useState<ReleaseSimulationResult>();
  const [simulationRequest, setSimulationRequest] = useState<ReleaseSimulationRequest>();
  const [releasePlan, setReleasePlan] = useState<VersionReleasePlan>();
  const [startOpen, setStartOpen] = useState(false);
  const [templateOpen, setTemplateOpen] = useState(false);
  const [rollbackOpen, setRollbackOpen] = useState(false);
  const [batchMode, setBatchMode] = useState<BatchValues["mode"]>("TEMPLATE");
  const [batchPreview, setBatchPreview] = useState<OverrideBatchPreviewResult>();
  const [batchRequest, setBatchRequest] = useState<OverrideBatchPreviewRequest>();
  const [batchOperation, setBatchOperation] = useState<OverrideBatchOperationResult>();
  const strategy = Form.useWatch("strategy", simulationForm) ?? "CANARY_BED_PERCENT";
  const canObserve = releasePlan?.status === "GRAY";

  const orgUnits = useMemo(() => orgUnitsQuery.data?.items ?? [], [orgUnitsQuery.data?.items]);
  const orgOptions = useMemo(
    () =>
      orgUnits
        .filter((unit) => unit.id)
        .map((unit) => ({
          value: unit.id as string,
          label: `${unit.name} · ${unit.code}`,
        })),
    [orgUnits],
  );
  const templateItems = templatesQuery.data?.items ?? [];
  const templateOptions = templateItems.map((template) => ({
    value: template.templateId,
    label: template.templateName,
  }));

  async function runSimulation(values: SimulationFormValues) {
    const primaryUnit = orgUnits.find((unit) => unit.id === values.targetOrgUnitIds[0]);
    if (!primaryUnit?.id || !primaryUnit.orgPath) {
      message.error("目标组织缺少组织路径，无法模拟");
      return;
    }
    const request: ReleaseSimulationRequest = {
      assetType: values.assetType,
      assetIdentity: values.assetIdentity.trim(),
      candidateVersionId: values.candidateVersionId.trim(),
      targetOrgUnitIds: values.targetOrgUnitIds,
      targetOrgPath: primaryUnit.orgPath,
      applicableScope: values.applicableScope.trim(),
      rolloutPolicy: rolloutPolicy(values),
      replayDays: values.replayDays,
      replayLimit: values.replayLimit,
    };
    try {
      const result = await simulateMutation.mutateAsync(request);
      setSimulationRequest(request);
      setSimulation(result);
      setReleasePlan(undefined);
    } catch (error) {
      message.error(getApiErrorMessage(error, "影响模拟失败"));
    }
  }

  async function startRollout(values: StartRolloutValues) {
    if (!simulation || !simulationRequest) return;
    try {
      const plan = await startMutation.mutateAsync({
        simulation: simulationRequest,
        confirmedSimulationDigest: simulation.simulationDigest,
        reviewConclusion: values.reviewConclusion.trim(),
      });
      setReleasePlan(plan);
      setStartOpen(false);
      message.success("灰度计划已启动");
    } catch (error) {
      message.error(getApiErrorMessage(error, "灰度启动失败"));
    }
  }

  async function observe(values: ObservationValues) {
    if (!releasePlan) return;
    try {
      const result = await observeMutation.mutateAsync({
        planId: releasePlan.planId,
        request: {
          stageIndex: releasePlan.rolloutStageIndex,
          ...values,
          observedAt: new Date().toISOString(),
        },
      });
      setReleasePlan(result.plan);
      if (result.paused) {
        message.warning("观测数据已记录，灰度计划已自动暂停");
      } else if (result.readyForFullRelease) {
        message.success("观测数据已记录，当前计划已满足全量发布条件");
      } else {
        message.success("观测数据已记录");
      }
      observationForm.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, "灰度观测提交失败"));
    }
  }

  async function rollback(values: RollbackValues) {
    if (!releasePlan) return;
    try {
      const plan = await rollbackMutation.mutateAsync({
        planId: releasePlan.planId,
        reason: values.reason.trim(),
        confirmedHighRisk: values.confirmedHighRisk,
      });
      setReleasePlan(plan);
      setRollbackOpen(false);
      rollbackForm.resetFields();
      message.success("灰度计划已回退到上一钉点");
    } catch (error) {
      message.error(getApiErrorMessage(error, "版本回退失败"));
    }
  }

  async function createTemplate(values: {
    templateName: string;
    description?: string;
    applicableScope: string;
    items: Array<{
      assetType: EngineAssetType;
      assetIdentity: string;
      overrideMode: "REPLACE" | "DISABLE" | "ADD";
      propagation: "INHERITABLE" | "EXCLUSIVE";
      inheritedVersionId?: string;
      sourceOverrideVersionId?: string;
      diffSummary: string;
      overrideReason: string;
    }>;
  }) {
    try {
      await createTemplateMutation.mutateAsync({
        ...values,
        applicableScope: values.applicableScope.trim(),
        items: values.items.map((item) => ({
          ...item,
          assetIdentity: item.assetIdentity.trim(),
          inheritedVersionId: item.inheritedVersionId?.trim() || undefined,
          sourceOverrideVersionId: item.sourceOverrideVersionId?.trim() || undefined,
          applicableScope: values.applicableScope.trim(),
          diffSummary: item.diffSummary.trim(),
          overrideReason: item.overrideReason.trim(),
        })),
      });
      setTemplateOpen(false);
      templateForm.resetFields();
      message.success("覆盖模板已创建");
    } catch (error) {
      message.error(getApiErrorMessage(error, "覆盖模板创建失败"));
    }
  }

  async function previewBatch(values: BatchValues) {
    const request: OverrideBatchPreviewRequest = {
      templateId: values.mode === "TEMPLATE" ? values.templateId : undefined,
      sourceOrgUnitId: values.mode === "CLONE" ? values.sourceOrgUnitId : undefined,
      targetOrgUnitIds: values.targetOrgUnitIds,
      targetVersionIds: {},
    };
    try {
      const result = await previewMutation.mutateAsync(request);
      setBatchRequest(request);
      setBatchPreview(result);
      setBatchOperation(undefined);
    } catch (error) {
      message.error(getApiErrorMessage(error, "批量预演失败"));
    }
  }

  async function applyBatch() {
    if (!batchRequest || !batchPreview) return;
    try {
      const result = await applyMutation.mutateAsync({
        preview: batchRequest,
        confirmedPreviewDigest: batchPreview.previewDigest,
      });
      setBatchOperation(result);
      message.success("批量覆盖已生效");
    } catch (error) {
      message.error(getApiErrorMessage(error, "批量覆盖生效失败"));
    }
  }

  async function revokeBatch() {
    if (!batchOperation) return;
    try {
      const result = await revokeMutation.mutateAsync(batchOperation.operationId);
      setBatchOperation(result);
      message.success("本次批量覆盖已按操作证据撤销");
    } catch (error) {
      message.error(getApiErrorMessage(error, "批量覆盖撤销失败"));
    }
  }

  const simulationWorkspace = (
    <div className={styles.workspace}>
      <Card size="small" title="发布前影响模拟">
        <Form
          form={simulationForm}
          layout="vertical"
          initialValues={{
            assetType: "RULE",
            applicableScope: "ALL",
            strategy: "CANARY_BED_PERCENT",
            bedPercent: 10,
            stages: "10,30,60,100",
            observationMinutes: 30,
            maxBlockRate: 0.05,
            maxManualRejectionRate: 0.1,
            maxAnomalyRate: 0.02,
            replayDays: 30,
            replayLimit: 100,
          }}
          onFinish={(values) => void runSimulation(values)}
        >
          <div className={styles.formGrid}>
            <Form.Item name="assetType" label="资产类型" rules={[{ required: true }]}>
              <Select options={assetTypes} />
            </Form.Item>
            <Form.Item name="assetIdentity" label="资产身份" rules={[{ required: true }]}>
              <Input placeholder="例如 RULE.VTE.RISK" />
            </Form.Item>
            <Form.Item name="candidateVersionId" label="候选版本 ID" rules={[{ required: true }]}>
              <Input placeholder="统一资产版本 ID" />
            </Form.Item>
            <Form.Item name="targetOrgUnitIds" label="目标组织" rules={[{ required: true }]}>
              <Select
                mode="multiple"
                showSearch
                filterOption={false}
                onSearch={(value) => setOrgKeyword(value.trim())}
                options={orgOptions}
                loading={orgUnitsQuery.isLoading}
                placeholder="输入组织名称或编码检索"
                notFoundContent={orgUnitsQuery.isError ? "组织目录读取失败" : "暂无可用组织"}
              />
            </Form.Item>
            <Form.Item name="applicableScope" label="适用维度" rules={[{ required: true }]}>
              <Input placeholder="ALL 或 specialty=AF" />
            </Form.Item>
            <Form.Item name="strategy" label="放量策略" rules={[{ required: true }]}>
              <Select options={strategyOptions} />
            </Form.Item>
            {strategy === "CANARY_BED_PERCENT" ? (
              <Form.Item name="bedPercent" label="灰度比例" rules={[{ required: true }]}>
                <InputNumber min={1} max={99} addonAfter="%" className={styles.fullWidth} />
              </Form.Item>
            ) : null}
            {strategy === "STAGED" ? (
              <>
                <Form.Item name="stages" label="分批比例" rules={[{ required: true }]}>
                  <Input placeholder="10,30,60,100" />
                </Form.Item>
                <Form.Item name="observationMinutes" label="观察窗">
                  <InputNumber min={1} addonAfter="分钟" className={styles.fullWidth} />
                </Form.Item>
                <Form.Item name="maxBlockRate" label="最大阻断率">
                  <InputNumber min={0} max={1} step={0.01} className={styles.fullWidth} />
                </Form.Item>
                <Form.Item name="maxManualRejectionRate" label="最大人工拒绝率">
                  <InputNumber min={0} max={1} step={0.01} className={styles.fullWidth} />
                </Form.Item>
                <Form.Item name="maxAnomalyRate" label="最大异常率">
                  <InputNumber min={0} max={1} step={0.01} className={styles.fullWidth} />
                </Form.Item>
              </>
            ) : null}
            <Form.Item name="replayDays" label="历史回放范围">
              <InputNumber min={1} max={365} addonAfter="天" className={styles.fullWidth} />
            </Form.Item>
            <Form.Item name="replayLimit" label="病例样本上限">
              <InputNumber min={1} max={1000} className={styles.fullWidth} />
            </Form.Item>
          </div>
          <Button
            type="primary"
            htmlType="submit"
            icon={<ExperimentOutlined />}
            loading={simulateMutation.isPending}
          >
            运行影响模拟
          </Button>
        </Form>
      </Card>

      {simulation ? (
        <section className={styles.resultBand}>
          <div className={styles.toolbar}>
            <div>
              <Title level={4}>模拟证据</Title>
              <Text type="secondary" className={styles.digest}>
                {simulation.simulationDigest}
              </Text>
            </div>
            <Space wrap>
              {checkTag(simulation.releasable)}
              <Button
                type="primary"
                icon={<RocketOutlined />}
                disabled={!simulation.releasable}
                onClick={() => setStartOpen(true)}
              >
                确认并启动灰度
              </Button>
            </Space>
          </div>
          <Divider />
          <div className={styles.metricGrid}>
            <div className={styles.metric}>
              <Text type="secondary">影响范围</Text>
              <span className={styles.metricValue}>
                影响 {simulation.affectedOrganizations.length} 个组织
              </span>
            </div>
            <div className={styles.metric}>
              <Text type="secondary">历史回放</Text>
              <span className={styles.metricValue}>
                {simulation.replay.sampledCases} 个病例样本
              </span>
            </div>
            <div className={styles.metric}>
              <Text type="secondary">决策变化</Text>
              <span className={styles.metricValue}>{simulation.replay.changedCases}</span>
            </div>
            <div className={styles.metric}>
              <Text type="secondary">下游覆盖冲突</Text>
              <span className={styles.metricValue}>{simulation.conflicts.length}</span>
            </div>
          </div>
          <Divider />
          <div className={styles.evidenceGrid}>
            <div className={styles.evidencePanel}>
              <Title level={5} className={styles.sectionTitle}>
                版本差异
              </Title>
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="变化类型">
                  {customerEnumLabel(simulation.diff.changeType)}
                </Descriptions.Item>
                <Descriptions.Item label="当前版本">
                  {simulation.diff.currentVersionNo ?? "首次发布"}
                </Descriptions.Item>
                <Descriptions.Item label="候选版本">
                  {simulation.diff.candidateVersionNo}
                </Descriptions.Item>
              </Descriptions>
            </div>
            <div className={styles.evidencePanel}>
              <Title level={5} className={styles.sectionTitle}>
                发布门禁
              </Title>
              <Space direction="vertical">
                <Space>安全单调性 {checkTag(simulation.safety.passed)}</Space>
                <Space>依赖完整性 {checkTag(simulation.dependencies.passed)}</Space>
                <Space>
                  回放执行
                  <Tag color={simulation.replay.status === "UNSUPPORTED" ? "error" : "processing"}>
                    {customerEnumLabel(simulation.replay.status)}
                  </Tag>
                </Space>
              </Space>
            </div>
          </div>
        </section>
      ) : (
        <Alert
          type="info"
          showIcon
          message="尚未生成发布证据"
          description="提交候选版本与目标组织后，系统只读计算差异、历史病例变化、安全与依赖门禁。"
        />
      )}

      {releasePlan ? (
        <Card size="small" title={`灰度计划 ${releasePlan.planId}`}>
          <div className={styles.toolbar}>
            <Space wrap>
              <Tag color={releasePlan.status === "PAUSED" ? "error" : "processing"}>
                {customerEnumLabel(releasePlan.status)}
              </Tag>
              <Text>当前阶段 {releasePlan.rolloutStageIndex + 1}</Text>
            </Space>
            <Button
              aria-label="回退版本"
              icon={<RollbackOutlined />}
              disabled={!["GRAY", "PAUSED"].includes(releasePlan.status)}
              onClick={() => setRollbackOpen(true)}
            >
              回退版本
            </Button>
          </div>
          {releasePlan.rolloutPausedReason ? (
            <Alert
              className={styles.statusAlert}
              type="error"
              showIcon
              message="灰度计划已暂停"
              description={releasePlan.rolloutPausedReason}
            />
          ) : null}
          <Divider />
          <Form
            form={observationForm}
            layout="vertical"
            initialValues={{
              sampleCount: 100,
              hitCount: 0,
              blockCount: 0,
              manualRejectionCount: 0,
              anomalyCount: 0,
            }}
            onFinish={(values) => void observe(values)}
          >
            <div className={styles.formGrid}>
              {[
                ["sampleCount", "样本数"],
                ["hitCount", "命中数"],
                ["blockCount", "阻断数"],
                ["manualRejectionCount", "人工拒绝数"],
                ["anomalyCount", "异常数"],
              ].map(([name, label]) => (
                <Form.Item key={name} name={name} label={label} rules={[{ required: true }]}>
                  <InputNumber
                    min={name === "sampleCount" ? 1 : 0}
                    disabled={!canObserve}
                    className={styles.fullWidth}
                  />
                </Form.Item>
              ))}
            </div>
            <Button htmlType="submit" disabled={!canObserve} loading={observeMutation.isPending}>
              提交观察窗数据
            </Button>
          </Form>
        </Card>
      ) : null}
    </div>
  );

  const templateWorkspace = (
    <div className={styles.workspace}>
      <div className={styles.toolbar}>
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void templatesQuery.refetch?.()}
            loading={templatesQuery.isLoading}
          >
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setTemplateOpen(true)}>
            新建模板
          </Button>
        </Space>
      </div>
      <Table
        rowKey="templateId"
        loading={templatesQuery.isLoading}
        dataSource={templateItems}
        pagination={{
          current: templatesQuery.data?.page ?? templatePage,
          pageSize: templatesQuery.data?.size ?? OVERRIDE_TEMPLATE_PAGE_SIZE,
          total: templatesQuery.data?.total ?? 0,
          onChange: setTemplatePage,
        }}
        columns={[
          { title: "模板", dataIndex: "templateName" },
          { title: "适用维度", dataIndex: "applicableScope" },
          { title: "说明", dataIndex: "description", render: (value) => value || "未填写" },
          {
            title: "状态",
            dataIndex: "status",
            render: (value) => <Tag>{customerEnumLabel(value)}</Tag>,
          },
        ]}
      />
      <Card size="small" title="批量预演与生效">
        <Form
          form={batchForm}
          layout="vertical"
          initialValues={{ mode: "TEMPLATE" }}
          onValuesChange={(changedValues) => {
            if (changedValues.mode) setBatchMode(changedValues.mode as BatchValues["mode"]);
          }}
          onFinish={(values) => void previewBatch(values)}
        >
          <Form.Item name="mode" label="复用方式">
            <Radio.Group optionType="button" buttonStyle="solid">
              <Radio.Button value="TEMPLATE">模板应用</Radio.Button>
              <Radio.Button value="CLONE">跨机构克隆</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <div className={styles.formGrid}>
            {batchMode === "TEMPLATE" ? (
              <Form.Item name="templateId" label="覆盖模板" rules={[{ required: true }]}>
                <Select options={templateOptions} />
              </Form.Item>
            ) : (
              <Form.Item name="sourceOrgUnitId" label="源机构" rules={[{ required: true }]}>
                <Select options={orgOptions} />
              </Form.Item>
            )}
            <Form.Item name="targetOrgUnitIds" label="目标机构" rules={[{ required: true }]}>
              <Select mode="multiple" options={orgOptions} />
            </Form.Item>
          </div>
          <Button htmlType="submit" icon={<CopyOutlined />} loading={previewMutation.isPending}>
            生成批量预演
          </Button>
        </Form>
      </Card>
      {batchPreview ? (
        <section className={styles.resultBand}>
          <div className={styles.toolbar}>
            <Space wrap>
              <Tag color={batchPreview.releasable ? "success" : "error"}>
                {batchPreview.releasable ? "可生效" : "存在阻断"}
              </Tag>
              <Text className={styles.digest}>{batchPreview.previewDigest}</Text>
            </Space>
            <Button
              type="primary"
              disabled={!batchPreview.releasable}
              loading={applyMutation.isPending}
              onClick={() => void applyBatch()}
            >
              确认并生效
            </Button>
          </div>
          <Table
            rowKey={(row) => `${row.targetOrgUnitId}-${row.assetType}-${row.assetIdentity}`}
            dataSource={batchPreview.rows}
            pagination={false}
            columns={[
              { title: "目标机构", dataIndex: "targetOrgUnitId" },
              { title: "资产类型", dataIndex: "assetType" },
              { title: "资产身份", dataIndex: "assetIdentity" },
              { title: "覆盖方式", dataIndex: "overrideMode" },
              { title: "传播", dataIndex: "propagation" },
              {
                title: "状态",
                dataIndex: "status",
                render: (value) => <Tag>{customerEnumLabel(value)}</Tag>,
              },
              { title: "校验说明", dataIndex: "issue", render: (value) => value || "通过" },
            ]}
          />
        </section>
      ) : null}
      {batchOperation && batchOperation.status !== "REVOKED" ? (
        <Alert
          type="success"
          showIcon
          message={`操作 ${batchOperation.operationId} 已完成`}
          description={
            <Popconfirm
              title="确认仅撤销本次批量操作生成的覆盖？"
              onConfirm={() => void revokeBatch()}
            >
              <Button danger loading={revokeMutation.isPending}>
                撤销本次操作
              </Button>
            </Popconfirm>
          }
        />
      ) : null}
    </div>
  );

  return (
    <>
      <PageShell title="发布治理" description="先模拟影响，再灰度放量；模板和克隆均需预演确认">
        <Tabs
          items={[
            { key: "simulation", label: "影响模拟与灰度", children: simulationWorkspace },
            { key: "templates", label: "覆盖模板与批量复用", children: templateWorkspace },
          ]}
        />
      </PageShell>

      <Modal
        title="确认灰度计划"
        open={startOpen}
        onCancel={() => setStartOpen(false)}
        onOk={() => startForm.submit()}
        okText="启动灰度"
        confirmLoading={startMutation.isPending}
      >
        <Form form={startForm} layout="vertical" onFinish={(values) => void startRollout(values)}>
          <Form.Item name="reviewConclusion" label="发布说明" rules={[{ required: true }]}>
            <TextArea rows={4} placeholder="记录临床、安全、依赖和回放复核结论" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新建覆盖模板"
        open={templateOpen}
        width={760}
        onCancel={() => setTemplateOpen(false)}
        onOk={() => templateForm.submit()}
        okText="创建模板"
        confirmLoading={createTemplateMutation.isPending}
        destroyOnClose
      >
        <Form
          form={templateForm}
          layout="vertical"
          initialValues={{
            applicableScope: "ALL",
            items: [
              {
                assetType: "RULE",
                overrideMode: "REPLACE",
                propagation: "INHERITABLE",
              },
            ],
          }}
          onFinish={(values) => void createTemplate(values)}
        >
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input />
          </Form.Item>
          <Form.Item name="applicableScope" label="适用维度" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <Space direction="vertical" className={styles.fullWidth}>
                {fields.map((field) => (
                  <div key={field.key} className={styles.evidencePanel}>
                    <div className={styles.formGrid}>
                      <Form.Item
                        name={[field.name, "assetType"]}
                        label="资产类型"
                        rules={[{ required: true }]}
                      >
                        <Select options={assetTypes} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, "assetIdentity"]}
                        label="资产身份"
                        rules={[{ required: true }]}
                      >
                        <Input />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, "overrideMode"]}
                        label="覆盖方式"
                        rules={[{ required: true }]}
                      >
                        <Select
                          options={[
                            { value: "REPLACE", label: "替换" },
                            { value: "DISABLE", label: "停用" },
                            { value: "ADD", label: "新增" },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, "propagation"]}
                        label="传播方式"
                        rules={[{ required: true }]}
                      >
                        <Select
                          options={[
                            { value: "INHERITABLE", label: "下级复用" },
                            { value: "EXCLUSIVE", label: "仅本级" },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item name={[field.name, "inheritedVersionId"]} label="继承版本 ID">
                        <Input />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, "sourceOverrideVersionId"]}
                        label="源覆盖版本 ID"
                      >
                        <Input />
                      </Form.Item>
                      <Form.Item
                        className={styles.wide}
                        name={[field.name, "diffSummary"]}
                        label="差异摘要"
                        rules={[{ required: true }]}
                      >
                        <TextArea rows={2} />
                      </Form.Item>
                      <Form.Item
                        className={styles.wide}
                        name={[field.name, "overrideReason"]}
                        label="覆盖原因"
                        rules={[{ required: true }]}
                      >
                        <TextArea rows={2} />
                      </Form.Item>
                    </div>
                    {fields.length > 1 ? (
                      <Button danger onClick={() => remove(field.name)}>
                        删除条目
                      </Button>
                    ) : null}
                  </div>
                ))}
                <Button onClick={() => add()} icon={<PlusOutlined />}>
                  添加条目
                </Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title="回退灰度计划"
        open={rollbackOpen}
        onCancel={() => setRollbackOpen(false)}
        onOk={() => rollbackForm.submit()}
        okText="确认回退"
        confirmLoading={rollbackMutation.isPending}
      >
        <Form
          form={rollbackForm}
          layout="vertical"
          initialValues={{
            confirmedHighRisk: false,
          }}
          onFinish={(values) => void rollback(values)}
        >
          <Descriptions size="small" column={1} bordered className={styles.rollbackSummary}>
            <Descriptions.Item label="本次候选版本">
              {releasePlan?.versionId ?? "未生成"}
            </Descriptions.Item>
            <Descriptions.Item label="恢复钉点">
              {releasePlan?.fromVersionId ?? "无上一钉点，仅停止本次灰度"}
            </Descriptions.Item>
          </Descriptions>
          <Form.Item name="reason" label="回退原因" rules={[{ required: true }]}>
            <TextArea rows={3} />
          </Form.Item>
          <Form.Item
            name="confirmedHighRisk"
            valuePropName="checked"
            rules={[
              {
                validator: (_, checked) =>
                  checked ? Promise.resolve() : Promise.reject(new Error("请完成高风险确认")),
              },
            ]}
          >
            <Checkbox>已确认停止本次灰度并恢复上一钉点</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
