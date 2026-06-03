import { useState } from "react";

import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Progress,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from "antd";
import {
  ApiOutlined,
  CheckCircleOutlined,
  DisconnectOutlined,
  FileProtectOutlined,
  HeartOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";

import {
  useAdapterHubStatus,
  useAdvanceIntegrationOnboarding,
  useCheckAdapterHealth,
  useCreateAdapter,
  useCreateIntegrationOnboarding,
  useGenerateDataQualityReport,
  useIntegrationAdapters,
  useIntegrationLogs,
  useIntegrationOnboardings,
  useReplayDeadLetter,
  useRetryMessage,
  useSecurityProfile,
  useUpdateAdapter,
  type AdapterHubSourceStatus,
  type DataQualityReport,
  type IntegrationAdapter,
  type IntegrationMessageLog,
  type IntegrationOnboarding,
  type SecurityProfile,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { PageState } from "@/shared/ui/PageState";
import type { PageStateKind } from "@/shared/ui/PageState.contract";
import { StepFlow } from "@/shared/ui/StepFlow";
import type { RouteExperience } from "@/shared/ui/experienceTypes";

import styles from "./AdapterHub.module.css";

const { Text } = Typography;
const { Option } = Select;

const route = findRouteByPath("/adapter/hub");

if (!route?.experience) {
  throw new Error("适配器中心页面缺少体验声明");
}

const PAGE_META: { title: string; experience: RouteExperience } = {
  title: route.title,
  experience: route.experience,
};

const LOG_PAGE_SIZE = 10;

const HEALTH_COLOR: Record<string, string> = {
  HEALTHY: "green",
  NOT_CONNECTED: "default",
  MISCONFIGURED: "red",
  UNHEALTHY: "red",
  ERROR: "red",
};

const ADAPTER_STATUS_COLOR: Record<string, string> = {
  ACTIVE: "green",
  SUSPENDED: "orange",
};

const LOG_STATUS_COLOR: Record<string, string> = {
  SUCCESS: "green",
  FAILED: "orange",
  RETRYING: "blue",
  NOT_CONNECTED: "default",
  DEAD_LETTER: "red",
};

const ONBOARDING_STATUS_COLOR: Record<string, string> = {
  REQUESTED: "blue",
  AUTH_CONFIGURED: "cyan",
  MAPPING_CONFIGURED: "orange",
  ONLINE: "green",
  OFFLINE: "default",
};

function hasPermission(profile: SecurityProfile | undefined, code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function percent(numerator: number, denominator: number) {
  if (denominator <= 0) return 0;
  return Math.round((numerator / denominator) * 100);
}

function healthTag(status: string) {
  return <Tag color={HEALTH_COLOR[status] ?? "default"}>{status}</Tag>;
}

function adapterEvidenceText(onboarding: IntegrationOnboarding) {
  return `字段映射 ${onboarding.mappedFieldCount} 项，健康状态 ${onboarding.healthStatus}，缺口 ${onboarding.blockers.length} 项。`;
}

function getErrorTrace(error: unknown) {
  if (error && typeof error === "object" && "traceId" in error) {
    return String((error as { traceId?: unknown }).traceId ?? "");
  }
  return undefined;
}

function pageStateTitle(state: PageStateKind) {
  if (state === "loading") return "正在加载适配器中心";
  if (state === "empty") return "暂无适配器接入记录";
  if (state === "error") return "适配器中心暂时不可用";
  if (state === "partial") return "部分接入需要处理";
  return undefined;
}

function pageStateFor({
  canAccess,
  securityLoading,
  adaptersLoading,
  adaptersError,
  partialError,
  adapters,
  onboardings,
}: {
  canAccess: boolean;
  securityLoading: boolean;
  adaptersLoading: boolean;
  adaptersError: boolean;
  partialError: boolean;
  adapters: IntegrationAdapter[];
  onboardings: IntegrationOnboarding[];
}): PageStateKind {
  if (!securityLoading && !canAccess) return "forbidden";
  if (securityLoading || adaptersLoading) return "loading";
  if (adaptersError) return "error";
  if (partialError) return "partial";
  if (adapters.length === 0 && onboardings.length === 0) return "empty";
  return "ready";
}

export default function AdapterHub() {
  const [logPage, setLogPage] = useState(1);
  const [adapterModalOpen, setAdapterModalOpen] = useState(false);
  const [onboardingModalOpen, setOnboardingModalOpen] = useState(false);
  const [expertMode, setExpertMode] = useState(false);
  const [healthResult, setHealthResult] = useState<IntegrationAdapter | null>(null);
  const [qualityReport, setQualityReport] = useState<DataQualityReport | null>(null);

  const [adapterForm] = Form.useForm();
  const [onboardingForm] = Form.useForm();

  const security = useSecurityProfile();
  const adaptersQuery = useIntegrationAdapters();
  const statusQuery = useAdapterHubStatus();
  const logsQuery = useIntegrationLogs(logPage, LOG_PAGE_SIZE);
  const onboardingsQuery = useIntegrationOnboardings();
  const createAdapterMutation = useCreateAdapter();
  const updateAdapterMutation = useUpdateAdapter();
  const healthCheckMutation = useCheckAdapterHealth();
  const qualityReportMutation = useGenerateDataQualityReport();
  const retryMessageMutation = useRetryMessage();
  const replayDeadLetterMutation = useReplayDeadLetter();
  const createOnboardingMutation = useCreateIntegrationOnboarding();
  const advanceOnboardingMutation = useAdvanceIntegrationOnboarding();

  const profile = security.data;
  const canAccess = !!profile && canAccessRoute(route, profile);
  const canWrite = hasPermission(profile, "integration.write");
  const canExecute = hasPermission(profile, "integration.execute");

  const adapters = adaptersQuery.data ?? [];
  const status = statusQuery.data;
  const logs = logsQuery.data?.items ?? [];
  const onboardings = onboardingsQuery.data ?? [];
  const totalAdapters = status?.totalAdapters ?? adapters.length;
  const healthyAdapters =
    status?.healthyAdapters ??
    adapters.filter((adapter) => adapter.healthStatus === "HEALTHY").length;
  const notConnectedAdapters =
    status?.notConnectedAdapters ??
    adapters.filter((adapter) => adapter.healthStatus === "NOT_CONNECTED").length;
  const mappedAdapters = status?.mappedAdapters ?? 0;
  const deadLetterCount = logs.filter((log) => log.status === "DEAD_LETTER").length;
  const failedCount = logs.filter((log) => log.status === "FAILED").length;
  const mappingRate = percent(mappedAdapters, totalAdapters);
  const healthRate = percent(healthyAdapters, totalAdapters);

  const pageState = pageStateFor({
    canAccess,
    securityLoading: security.isLoading,
    adaptersLoading: adaptersQuery.isLoading,
    adaptersError: adaptersQuery.isError,
    partialError: statusQuery.isError || logsQuery.isError || onboardingsQuery.isError,
    adapters,
    onboardings,
  });

  async function handleCreateAdapter() {
    try {
      const values = await adapterForm.validateFields();
      await createAdapterMutation.mutateAsync(values);
      message.success("适配器已提交到接入总线。");
      setAdapterModalOpen(false);
      adapterForm.resetFields();
      void adaptersQuery.refetch();
      void statusQuery.refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(adapterForm, error)) return;
      message.error(getApiErrorMessage(error, "创建适配器失败，请检查参数"));
    }
  }

  async function handleToggleAdapterStatus(adapter: IntegrationAdapter) {
    try {
      await updateAdapterMutation.mutateAsync({
        adapterId: adapter.adapterId,
        payload: {
          name: adapter.name,
          protocolType: adapter.protocolType,
          configJson: adapter.configJson,
          status: adapter.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE",
        },
      });
      message.success("适配器状态已更新。");
      void adaptersQuery.refetch();
      void statusQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "更新适配器状态失败"));
    }
  }

  async function handleCheckAdapterHealth(adapterId: string) {
    try {
      const result = await healthCheckMutation.mutateAsync(adapterId);
      setHealthResult(result);
      void adaptersQuery.refetch();
      void statusQuery.refetch();
      if (result.healthStatus === "HEALTHY") {
        message.success("健康检查完成。");
      } else {
        message.info("外部连通性未知或不可用，已按真实状态展示。");
      }
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "健康检查失败"));
    }
  }

  async function handleGenerateQualityReport() {
    try {
      const report = await qualityReportMutation.mutateAsync();
      setQualityReport(report);
      void statusQuery.refetch();
      message.success("数据质量报告已生成。");
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "生成质量报告失败"));
    }
  }

  async function handleRetryMessage(messageId: string) {
    try {
      await retryMessageMutation.mutateAsync(messageId);
      message.success("已提交重试请求。");
      void logsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "重试失败"));
    }
  }

  async function handleReplayDeadLetter(messageId: string) {
    try {
      await replayDeadLetterMutation.mutateAsync(messageId);
      message.success("已提交死信重放请求。");
      void logsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "死信重放失败"));
    }
  }

  async function handleCreateOnboarding() {
    try {
      const values = await onboardingForm.validateFields();
      await createOnboardingMutation.mutateAsync(values);
      message.success("接入申请已创建。");
      setOnboardingModalOpen(false);
      onboardingForm.resetFields();
      void onboardingsQuery.refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(onboardingForm, error)) return;
      message.error(getApiErrorMessage(error, "创建接入申请失败"));
    }
  }

  async function handleAdvanceOnboarding(onboarding: IntegrationOnboarding) {
    try {
      await advanceOnboardingMutation.mutateAsync({
        onboardingId: onboarding.onboardingId,
        targetStatus: "ONLINE",
        evidenceText: adapterEvidenceText(onboarding),
      });
      message.success("接入阶段推进请求已提交。");
      void onboardingsQuery.refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "推进接入阶段失败"));
    }
  }

  const adapterColumns: ColumnsType<IntegrationAdapter> = [
    {
      title: "系统与适配器",
      key: "name",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.name}</Text>
          <Text className={styles.identifier}>{record.adapterId}</Text>
        </Space>
      ),
    },
    {
      title: "协议",
      dataIndex: "protocolType",
      key: "protocolType",
      render: (value) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: "健康状态",
      dataIndex: "healthStatus",
      key: "healthStatus",
      render: (value) => healthTag(String(value)),
    },
    {
      title: "RTT",
      dataIndex: "rttMs",
      key: "rttMs",
      render: (value) => <Text>{Number(value) > 0 ? `${value}ms` : "未测量"}</Text>,
    },
    {
      title: "运行状态",
      dataIndex: "status",
      key: "status",
      render: (value) => (
        <Tag color={ADAPTER_STATUS_COLOR[String(value)] ?? "default"}>
          {value === "ACTIVE" ? "启用中" : "已挂起"}
        </Tag>
      ),
    },
    {
      title: "最近探活",
      dataIndex: "lastHeartbeatAt",
      key: "lastHeartbeatAt",
      render: (value) => (
        <Text type="secondary">{value ? new Date(String(value)).toLocaleString() : "暂无"}</Text>
      ),
    },
    {
      title: "操作",
      key: "actions",
      render: (_, record) => (
        <Space wrap>
          <Button
            icon={<HeartOutlined aria-hidden="true" />}
            loading={healthCheckMutation.isPending}
            disabled={!canExecute}
            onClick={() => handleCheckAdapterHealth(record.adapterId)}
          >
            健康诊断
          </Button>
          <Button
            danger={record.status === "ACTIVE"}
            disabled={!canWrite}
            onClick={() => handleToggleAdapterStatus(record)}
          >
            {record.status === "ACTIVE" ? "挂起" : "激活"}
          </Button>
        </Space>
      ),
    },
  ];

  const logColumns: ColumnsType<IntegrationMessageLog> = [
    {
      title: "消息与 trace",
      key: "messageId",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.messageId}</Text>
          <Text className={styles.identifier}>{record.traceId}</Text>
        </Space>
      ),
    },
    { title: "系统", dataIndex: "systemName", key: "systemName" },
    { title: "方向", dataIndex: "direction", key: "direction" },
    { title: "摘要", dataIndex: "payloadSummary", key: "payloadSummary" },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (value) => <Tag color={LOG_STATUS_COLOR[String(value)] ?? "default"}>{value}</Tag>,
    },
    {
      title: "重试",
      key: "retry",
      render: (_, record) => (
        <Text>
          {record.retryCount}/{record.maxRetries}
        </Text>
      ),
    },
    {
      title: "错误",
      dataIndex: "errorMessage",
      key: "errorMessage",
      render: (value) => <Text type="secondary">{value || "无"}</Text>,
    },
    {
      title: "操作",
      key: "actions",
      render: (_, record) => {
        const isDeadLetter = record.status === "DEAD_LETTER";
        const isSuccess = record.status === "SUCCESS";
        return (
          <Space wrap>
            <Button
              icon={<ReloadOutlined aria-hidden="true" />}
              aria-label="重试"
              disabled={!canExecute || isSuccess || isDeadLetter}
              onClick={() => handleRetryMessage(record.messageId)}
            >
              重试
            </Button>
            <Button
              aria-label="重放"
              disabled={!canExecute || !isDeadLetter}
              onClick={() => handleReplayDeadLetter(record.messageId)}
            >
              重放
            </Button>
          </Space>
        );
      },
    },
  ];

  const onboardingColumns: ColumnsType<IntegrationOnboarding> = [
    {
      title: "接入申请",
      key: "name",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.name}</Text>
          <Text className={styles.identifier}>{record.onboardingId}</Text>
        </Space>
      ),
    },
    { title: "来源系统", dataIndex: "sourceSystem", key: "sourceSystem" },
    { title: "业务场景", dataIndex: "businessScenario", key: "businessScenario" },
    {
      title: "阶段",
      dataIndex: "status",
      key: "status",
      render: (value) => (
        <Tag color={ONBOARDING_STATUS_COLOR[String(value)] ?? "default"}>{value}</Tag>
      ),
    },
    {
      title: "健康 / 映射",
      key: "quality",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          {healthTag(record.healthStatus)}
          <Text type="secondary">字段映射 {record.mappedFieldCount} 项</Text>
        </Space>
      ),
    },
    {
      title: "阻塞项",
      key: "blockers",
      render: (_, record) =>
        record.blockers.length === 0 ? (
          <Text type="secondary">无</Text>
        ) : (
          <ul className={styles.gapList}>
            {record.blockers.map((blocker) => (
              <li key={blocker}>{blocker}</li>
            ))}
          </ul>
        ),
    },
    {
      title: "操作",
      key: "actions",
      render: (_, record) => (
        <Button
          disabled={!canExecute || record.status === "ONLINE" || record.blockers.length > 0}
          onClick={() => handleAdvanceOnboarding(record)}
        >
          推进到上线
        </Button>
      ),
    },
  ];

  const fieldMappingItems = status?.sources ?? [];

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={profile}
      expertMode={expertMode}
      onExpertModeChange={setExpertMode}
      primary={
        <Button
          type="primary"
          icon={<PlusOutlined aria-hidden="true" />}
          disabled={!canWrite}
          onClick={() => setAdapterModalOpen(true)}
        >
          新增适配器
        </Button>
      }
      extras={
        <Button
          icon={<FileProtectOutlined aria-hidden="true" />}
          loading={qualityReportMutation.isPending}
          disabled={!canExecute}
          onClick={handleGenerateQualityReport}
        >
          生成质量报告
        </Button>
      }
    >
      <PageState
        state={pageState}
        title={pageStateTitle(pageState)}
        traceId={getErrorTrace(adaptersQuery.error)}
        successCount={pageState === "partial" ? adapters.length : undefined}
        failureCount={pageState === "partial" ? 1 : undefined}
        failureDetails={
          pageState === "partial"
            ? [
                {
                  key: "集成附属数据",
                  reason: "适配器主列表已可用，但健康汇总、日志或接入申请有来源失败",
                  retryable: true,
                },
              ]
            : []
        }
        onRetry={() => {
          void adaptersQuery.refetch();
          void statusQuery.refetch();
          void logsQuery.refetch();
          void onboardingsQuery.refetch();
        }}
      >
        <Space direction="vertical" size="middle" className="mk-full-width">
          <div className={styles.summaryGrid}>
            <Card className={styles.summaryCard}>
              <Statistic title="已登记适配器" value={totalAdapters} prefix={<ApiOutlined />} />
            </Card>
            <Card className={styles.summaryCard}>
              <Statistic
                title="真实连通率"
                value={healthRate}
                suffix="%"
                prefix={<CheckCircleOutlined />}
              />
            </Card>
            <Card className={styles.summaryCard}>
              <Statistic
                title="NOT_CONNECTED"
                value={notConnectedAdapters}
                prefix={<DisconnectOutlined />}
              />
            </Card>
            <Card className={styles.summaryCard}>
              <Statistic
                title="字段映射覆盖"
                value={mappingRate}
                suffix="%"
                prefix={<SafetyCertificateOutlined />}
              />
            </Card>
          </div>

          <StepFlow
            currentStep="impact_preview"
            panelByStep={{
              impact_preview: (
                <Space direction="vertical" size="small">
                  <Text strong>适配器接入 7 步流</Text>
                  <Text type="secondary">
                    适配器属于配置类资产，必须按“选模板/导入 → 自动校验 → 看影响 → 提交审核 →
                    灰度发布 → 全量 → 留证据/可回滚”留证。
                  </Text>
                  <Text type="secondary">
                    当前重点看影响：断连、字段映射缺口、死信和数据质量缺口都会阻止把状态伪装成健康。
                  </Text>
                </Space>
              ),
            }}
          />

          {healthResult && (
            <Alert
              type={healthResult.healthStatus === "HEALTHY" ? "success" : "info"}
              showIcon
              message={healthResult.healthStatus === "HEALTHY" ? "健康检查完成" : "外部连通性未知"}
              description={`适配器 ${healthResult.adapterId} 返回 ${healthResult.healthStatus}，RTT ${
                healthResult.rttMs > 0 ? `${healthResult.rttMs}ms` : "未测量"
              }。页面仅展示后端真实状态。`}
            />
          )}

          {qualityReport && <QualityReportCard report={qualityReport} />}

          <Tabs
            defaultActiveKey="adapters"
            items={[
              {
                key: "adapters",
                label: "适配器目录",
                children: (
                  <div className={styles.sectionStack}>
                    <Alert
                      type="info"
                      showIcon
                      message="断连不伪造"
                      description="外部系统未接入真实连接器时保持 NOT_CONNECTED；配置非法显示 MISCONFIGURED，不用本地规则猜测 HEALTHY。"
                    />
                    <Table
                      rowKey="adapterId"
                      columns={adapterColumns}
                      dataSource={adapters}
                      pagination={false}
                      scroll={{ x: 900 }}
                    />
                    <FieldMappingPanel items={fieldMappingItems} />
                  </div>
                ),
              },
              {
                key: "dead-letter",
                label: "死信重放",
                children: (
                  <div className={styles.sectionStack}>
                    <div className={styles.toolbar}>
                      <Text type="secondary">
                        失败 {failedCount} 条，死信 {deadLetterCount}{" "}
                        条；重放会创建补偿消息，原始证据保留。
                      </Text>
                    </div>
                    <Table
                      rowKey="messageId"
                      columns={logColumns}
                      dataSource={logs}
                      loading={logsQuery.isLoading}
                      pagination={{
                        current: logPage,
                        pageSize: LOG_PAGE_SIZE,
                        total: logsQuery.data?.total ?? 0,
                        onChange: setLogPage,
                      }}
                      scroll={{ x: 900 }}
                    />
                  </div>
                ),
              },
              {
                key: "quality",
                label: "数据质量看板",
                children: (
                  <div className={styles.sectionStack}>
                    {qualityReport ? (
                      <QualityReportCard report={qualityReport} />
                    ) : (
                      <Alert
                        type="info"
                        showIcon
                        message="尚未生成本轮数据质量报告"
                        description="点击页面右上角“生成质量报告”，后端会基于当前租户适配器、字段映射和探活事实生成快照。"
                      />
                    )}
                    <div className={styles.qualityGrid}>
                      <MetricCard title="未连接" value={status?.notConnectedAdapters ?? 0} />
                      <MetricCard title="配置非法" value={status?.misconfiguredAdapters ?? 0} />
                      <MetricCard title="字段映射覆盖" value={mappingRate} suffix="%" />
                    </div>
                  </div>
                ),
              },
              {
                key: "onboarding",
                label: "接入向导",
                children: (
                  <div className={styles.sectionStack}>
                    <div className={styles.toolbar}>
                      <Text type="secondary">
                        接入申请必须先完成鉴权、字段映射和健康检查，再由后端推进状态。
                      </Text>
                      <Button
                        icon={<PlusOutlined aria-hidden="true" />}
                        disabled={!canWrite}
                        onClick={() => setOnboardingModalOpen(true)}
                      >
                        新增接入申请
                      </Button>
                    </div>
                    <Table
                      rowKey="onboardingId"
                      columns={onboardingColumns}
                      dataSource={onboardings}
                      pagination={false}
                      scroll={{ x: 900 }}
                    />
                  </div>
                ),
              },
            ]}
          />
        </Space>
      </PageState>

      <Modal
        title="新增适配器"
        open={adapterModalOpen}
        onOk={handleCreateAdapter}
        onCancel={() => setAdapterModalOpen(false)}
        okText="提交适配器"
        cancelText="取消"
      >
        <Form form={adapterForm} layout="vertical">
          <Form.Item name="adapterId" label="适配器标识" rules={[{ required: true }]}>
            <Input placeholder="输入真实适配器标识" />
          </Form.Item>
          <Form.Item name="name" label="系统名称" rules={[{ required: true }]}>
            <Input placeholder="输入院内系统名称" />
          </Form.Item>
          <Form.Item
            name="protocolType"
            label="接入协议"
            initialValue="REST"
            rules={[{ required: true }]}
          >
            <Select>
              <Option value="HIS">HIS</Option>
              <Option value="EMR">EMR</Option>
              <Option value="LIS">LIS</Option>
              <Option value="PACS">PACS</Option>
              <Option value="FHIR">FHIR</Option>
              <Option value="REST">REST</Option>
            </Select>
          </Form.Item>
          <Form.Item name="configJson" label="连接与字段映射 JSON">
            <Input.TextArea rows={4} placeholder="输入真实连接配置或字段映射 JSON" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增接入申请"
        open={onboardingModalOpen}
        onOk={handleCreateOnboarding}
        onCancel={() => setOnboardingModalOpen(false)}
        okText="提交申请"
        cancelText="取消"
      >
        <Form form={onboardingForm} layout="vertical">
          <Form.Item name="onboardingId" label="接入申请标识" rules={[{ required: true }]}>
            <Input placeholder="输入真实接入申请标识" />
          </Form.Item>
          <Form.Item name="name" label="接入申请名称" rules={[{ required: true }]}>
            <Input placeholder="输入接入申请名称" />
          </Form.Item>
          <Form.Item
            name="accessMode"
            label="接入模式"
            initialValue="ADAPTER"
            rules={[{ required: true }]}
          >
            <Select>
              <Option value="ADAPTER">适配器</Option>
              <Option value="FHIR">FHIR 门面</Option>
            </Select>
          </Form.Item>
          <Form.Item name="adapterId" label="绑定适配器标识">
            <Input placeholder="适配器模式下填写" />
          </Form.Item>
          <Form.Item name="fhirVersion" label="FHIR 版本">
            <Input placeholder="FHIR 模式下填写 R4 或 R5" />
          </Form.Item>
          <Form.Item name="sourceSystem" label="来源系统" rules={[{ required: true }]}>
            <Input placeholder="例如 HIS / EMR / LIS" />
          </Form.Item>
          <Form.Item name="businessScenario" label="业务场景" rules={[{ required: true }]}>
            <Input placeholder="例如门诊患者主数据" />
          </Form.Item>
          <Form.Item name="orgPath" label="组织范围" rules={[{ required: true }]}>
            <Input placeholder="集团/医院/院区/科室" />
          </Form.Item>
          <Form.Item name="callbackWebhookId" label="回调通道标识">
            <Input placeholder="如已配置回调通道，可填写真实标识" />
          </Form.Item>
        </Form>
      </Modal>
    </PageExperienceShell>
  );
}

function FieldMappingPanel({ items }: { items: AdapterHubSourceStatus[] }) {
  return (
    <Card title="字段映射与缺口" className={styles.sectionCard}>
      {items.length === 0 ? (
        <Text type="secondary">暂无字段映射来源状态。</Text>
      ) : (
        <Space direction="vertical" size="middle" className="mk-full-width">
          {items.map((item) => (
            <Descriptions key={item.adapterId} bordered size="small" column={2}>
              <Descriptions.Item label="适配器">{item.name}</Descriptions.Item>
              <Descriptions.Item label="状态">{healthTag(item.healthStatus)}</Descriptions.Item>
              <Descriptions.Item label="映射字段">{item.mappedFieldCount}</Descriptions.Item>
              <Descriptions.Item label="最近探活">
                {item.lastHeartbeatAt ? new Date(item.lastHeartbeatAt).toLocaleString() : "暂无"}
              </Descriptions.Item>
              <Descriptions.Item label="缺口" span={2}>
                {item.gaps.length === 0 ? (
                  <Text type="secondary">无</Text>
                ) : (
                  <ul className={styles.gapList}>
                    {item.gaps.map((gap) => (
                      <li key={gap}>{gap}</li>
                    ))}
                  </ul>
                )}
              </Descriptions.Item>
            </Descriptions>
          ))}
        </Space>
      )}
    </Card>
  );
}

function QualityReportCard({ report }: { report: DataQualityReport }) {
  return (
    <Card title="数据质量报告" className={styles.sectionCard}>
      <div className={styles.qualityGrid}>
        <MetricCard title="必填率" value={report.requiredFieldRate} suffix="%" />
        <MetricCard title="映射率" value={report.mappingRate} suffix="%" />
        <MetricCard title="时效率" value={report.timelinessRate} suffix="%" />
      </div>
      <Descriptions size="small" column={2}>
        <Descriptions.Item label="报告 ID">{report.reportId}</Descriptions.Item>
        <Descriptions.Item label="traceId">{report.traceId ?? "暂无"}</Descriptions.Item>
        <Descriptions.Item label="断连数量">{report.notConnectedCount}</Descriptions.Item>
        <Descriptions.Item label="配置非法">{report.misconfiguredCount}</Descriptions.Item>
        <Descriptions.Item label="缺口摘要" span={2}>
          {report.gapSummary}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}

function MetricCard({ title, value, suffix }: { title: string; value: number; suffix?: string }) {
  return (
    <Card className={styles.qualityCard}>
      <Space direction="vertical" size="small" className="mk-full-width">
        <Text type="secondary">{title}</Text>
        <Progress percent={suffix === "%" ? value : undefined} showInfo={false} />
        <Text strong>
          {value}
          {suffix}
        </Text>
      </Space>
    </Card>
  );
}
