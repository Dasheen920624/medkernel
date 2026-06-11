import { useEffect, useState } from "react";

import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
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
  DeleteOutlined,
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
  useCreateWebhook,
  useGenerateDataQualityReport,
  useIntegrationDataContract,
  useIntegrationAdapters,
  useIntegrationLogs,
  useIntegrationOnboardings,
  useRegionalSources,
  useRegisterRegionalSource,
  useReplayDeadLetter,
  useRetryMessage,
  useSecurityProfile,
  useTestWebhookSignature,
  useTerminologyMappings,
  useUpdateAdapter,
  useWebhooks,
  type AdapterHubSourceStatus,
  type AdapterHubRequiredSourceStatus,
  type DataQualityReport,
  type IntegrationDataContractResponse,
  type IntegrationAdapter,
  type IntegrationMessageLog,
  type IntegrationOnboarding,
  type IntegrationWebhookConfig,
  type RegionalSource,
  type SecurityProfile,
  type WebhookCreateResult,
  type WebhookSignatureTestResult,
} from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";
import { ADAPTER_PROTOCOL_OPTIONS, canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { customerEnumLabel, riskLabel } from "@/shared/config/customerLabels";
import { OrgUnitSelect } from "@/shared/ui/OrgUnitSelect";
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

interface AdapterFieldMappingFormValue {
  sourcePath: string;
  targetPath: string;
  termMappingId?: string;
}

interface AdapterFormValue {
  adapterId: string;
  name: string;
  protocolType: string;
  baseUrl?: string;
  healthPath?: string;
  outboundPath?: string;
  connectTimeoutMs?: number;
  requestTimeoutMs?: number;
  fieldMappings?: AdapterFieldMappingFormValue[];
  configJson?: string;
}

const HTTP_PROTOCOLS = new Set(["REST", "FHIR", "WEBHOOK", "WEBSERVICE"]);

const HEALTH_COLOR: Record<string, string> = {
  HEALTHY: "green",
  NOT_CONNECTED: "default",
  MISCONFIGURED: "red",
  UNHEALTHY: "red",
  ERROR: "red",
};

const HEALTH_ALERT_TYPE: Record<string, "success" | "error" | "warning"> = {
  HEALTHY: "success",
  MISCONFIGURED: "error",
  NOT_CONNECTED: "warning",
  UNHEALTHY: "warning",
  ERROR: "warning",
};

const HEALTH_ALERT_MESSAGE: Record<string, string> = {
  HEALTHY: "真实连接正常",
  MISCONFIGURED: "连接配置需要修正",
  NOT_CONNECTED: "外部系统当前不可达",
  UNHEALTHY: "外部系统当前不可达",
  ERROR: "外部系统当前不可达",
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

const TRUST_LEVEL_COLOR: Record<string, string> = {
  HIGH: "green",
  MEDIUM: "orange",
  LOW: "red",
};

function hasPermission(profile: SecurityProfile | undefined, code: string) {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function percent(numerator: number, denominator: number) {
  if (denominator <= 0) return 0;
  return Math.round((numerator / denominator) * 100);
}

function healthTag(status: string) {
  return <Tag color={HEALTH_COLOR[status] ?? "default"}>{customerEnumLabel(status)}</Tag>;
}

function requiredSourceStatusColor(item: AdapterHubRequiredSourceStatus) {
  if (item.ready) return "green";
  if (item.status === "MISSING") return "red";
  return "orange";
}

function adapterEvidenceText(onboarding: IntegrationOnboarding) {
  return `字段映射 ${onboarding.mappedFieldCount} 项，健康状态 ${customerEnumLabel(
    onboarding.healthStatus,
  )}，缺口 ${onboarding.blockers.length} 项。`;
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
}: {
  canAccess: boolean;
  securityLoading: boolean;
  adaptersLoading: boolean;
  adaptersError: boolean;
  partialError: boolean;
}): PageStateKind {
  if (!securityLoading && !canAccess) return "forbidden";
  if (securityLoading || adaptersLoading) return "loading";
  if (adaptersError) return "error";
  if (partialError) return "partial";
  return "ready";
}

export default function AdapterHub() {
  const [logPage, setLogPage] = useState(1);
  const [adapterModalOpen, setAdapterModalOpen] = useState(false);
  const [onboardingModalOpen, setOnboardingModalOpen] = useState(false);
  const [webhookModalOpen, setWebhookModalOpen] = useState(false);
  const [regionalSourceModalOpen, setRegionalSourceModalOpen] = useState(false);
  const [expertMode, setExpertMode] = useState(false);
  const [contractVersionInput, setContractVersionInput] = useState("");
  const [contractVersion, setContractVersion] = useState("");
  const [healthResult, setHealthResult] = useState<IntegrationAdapter | null>(null);
  const [qualityReport, setQualityReport] = useState<DataQualityReport | null>(null);
  const [createdWebhook, setCreatedWebhook] = useState<WebhookCreateResult | null>(null);
  const [signatureResult, setSignatureResult] = useState<WebhookSignatureTestResult | null>(null);
  const [signatureWebhookId, setSignatureWebhookId] = useState<string>();

  const [adapterForm] = Form.useForm();
  const [onboardingForm] = Form.useForm();
  const [webhookForm] = Form.useForm();
  const [signatureForm] = Form.useForm();
  const [regionalSourceForm] = Form.useForm();
  const selectedAdapterProtocol = Form.useWatch("protocolType", adapterForm);
  const usesHttpConnector = HTTP_PROTOCOLS.has(
    String(selectedAdapterProtocol ?? "REST").toUpperCase(),
  );

  const security = useSecurityProfile();
  const adaptersQuery = useIntegrationAdapters();
  const statusQuery = useAdapterHubStatus();
  const dataContractQuery = useIntegrationDataContract(contractVersion, contractVersion.length > 0);
  const logsQuery = useIntegrationLogs(logPage, LOG_PAGE_SIZE);
  const onboardingsQuery = useIntegrationOnboardings();
  const webhooksQuery = useWebhooks();
  const regionalSourcesQuery = useRegionalSources();
  const terminologyMappingsQuery = useTerminologyMappings({
    status: "CONFIRMED",
    page: 1,
    size: 100,
    sort: "updatedAt,desc",
  });
  const createAdapterMutation = useCreateAdapter();
  const updateAdapterMutation = useUpdateAdapter();
  const healthCheckMutation = useCheckAdapterHealth();
  const qualityReportMutation = useGenerateDataQualityReport();
  const retryMessageMutation = useRetryMessage();
  const replayDeadLetterMutation = useReplayDeadLetter();
  const createOnboardingMutation = useCreateIntegrationOnboarding();
  const advanceOnboardingMutation = useAdvanceIntegrationOnboarding();
  const createWebhookMutation = useCreateWebhook();
  const testWebhookSignatureMutation = useTestWebhookSignature();
  const registerRegionalSourceMutation = useRegisterRegionalSource();

  const profile = security.data;
  const canAccess = !!profile && canAccessRoute(route, profile);
  const canWrite = hasPermission(profile, "integration.write");
  const canExecute = hasPermission(profile, "integration.execute");

  const adapters = adaptersQuery.data ?? [];
  const status = statusQuery.data;
  const logs = logsQuery.data?.items ?? [];
  const onboardings = onboardingsQuery.data ?? [];
  const webhooks = webhooksQuery.data ?? [];
  const firstWebhookId = webhooksQuery.data?.[0]?.webhookId;
  const regionalSources = regionalSourcesQuery.data ?? [];
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
    partialError:
      statusQuery.isError ||
      logsQuery.isError ||
      onboardingsQuery.isError ||
      webhooksQuery.isError ||
      regionalSourcesQuery.isError,
  });

  useEffect(() => {
    if (firstWebhookId && !signatureWebhookId) {
      setSignatureWebhookId(firstWebhookId);
    }
  }, [firstWebhookId, signatureWebhookId]);

  async function handleCreateAdapter() {
    try {
      const values = (await adapterForm.validateFields()) as AdapterFormValue;
      const configJson = expertMode
        ? values.configJson?.trim()
        : JSON.stringify({
            ...(usesHttpConnector
              ? {
                  baseUrl: values.baseUrl?.trim(),
                  healthPath: values.healthPath?.trim() || "/",
                  outboundPath: values.outboundPath?.trim() || "/",
                  connectTimeoutMs: values.connectTimeoutMs ?? 2000,
                  requestTimeoutMs: values.requestTimeoutMs ?? 5000,
                }
              : {}),
            fieldMappings: (values.fieldMappings ?? []).map((mapping) => ({
              sourcePath: mapping.sourcePath,
              targetPath: mapping.targetPath,
              ...(mapping.termMappingId ? { termMappingId: Number(mapping.termMappingId) } : {}),
            })),
          });
      await createAdapterMutation.mutateAsync({
        adapterId: values.adapterId,
        name: values.name,
        protocolType: values.protocolType,
        configJson,
      });
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
      } else if (result.healthStatus === "MISCONFIGURED") {
        message.error("连接配置无效，请修正后重试。");
      } else {
        message.warning("外部系统当前不可达。");
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

  async function handleCreateWebhook() {
    try {
      const values = await webhookForm.validateFields();
      const result = await createWebhookMutation.mutateAsync(values);
      setCreatedWebhook(result);
      setWebhookModalOpen(false);
      webhookForm.resetFields();
      void webhooksQuery.refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(webhookForm, error)) return;
      message.error(getApiErrorMessage(error, "创建回调通道失败"));
    }
  }

  async function handleTestWebhookSignature() {
    try {
      const values = await signatureForm.validateFields();
      if (!signatureWebhookId) {
        message.warning("请选择回调通道。");
        return;
      }
      const result = await testWebhookSignatureMutation.mutateAsync({
        webhookId: signatureWebhookId,
        payload: values.payload,
      });
      setSignatureResult(result);
    } catch (error: unknown) {
      if (applyApiFieldErrors(signatureForm, error)) return;
      message.error(getApiErrorMessage(error, "生成签名预览失败"));
    }
  }

  async function handleRegisterRegionalSource() {
    try {
      const values = await regionalSourceForm.validateFields();
      const payload = {
        sourceId: values.sourceId,
        regionalNetworkName: values.regionalNetworkName,
        sourceOrganizationId: values.sourceOrganizationId,
        sourceOrganizationName: values.sourceOrganizationName,
        trustLevel: values.trustLevel,
        evidenceText: values.evidenceText,
        orgPath: values.orgPath,
        ...(values.adapterId?.trim() ? { adapterId: values.adapterId.trim() } : {}),
        ...(values.onboardingId?.trim() ? { onboardingId: values.onboardingId.trim() } : {}),
      };
      await registerRegionalSourceMutation.mutateAsync(payload);
      message.success("区域来源已登记并纳入可信分级。");
      setRegionalSourceModalOpen(false);
      regionalSourceForm.resetFields();
      void regionalSourcesQuery.refetch();
    } catch (error: unknown) {
      if (applyApiFieldErrors(regionalSourceForm, error)) return;
      message.error(getApiErrorMessage(error, "登记区域来源失败"));
    }
  }

  function handleLoadDataContract() {
    const nextVersion = contractVersionInput.trim();
    if (!nextVersion) {
      message.warning("请输入 packageVersion。");
      return;
    }
    setContractVersion(nextVersion);
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

  const webhookColumns: ColumnsType<IntegrationWebhookConfig> = [
    {
      title: "回调通道",
      key: "name",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.name}</Text>
          <Text className={styles.identifier}>{record.webhookId}</Text>
        </Space>
      ),
    },
    { title: "回调地址", dataIndex: "callbackUrl", key: "callbackUrl" },
    { title: "订阅事件", dataIndex: "eventsSubscribed", key: "eventsSubscribed" },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (value) => (
        <Tag color={value === "ACTIVE" ? "green" : "default"}>
          {value === "ACTIVE" ? "启用中" : "已挂起"}
        </Tag>
      ),
    },
    {
      title: "更新时间",
      dataIndex: "updatedAt",
      key: "updatedAt",
      render: (value) => new Date(String(value)).toLocaleString(),
    },
  ];

  const regionalSourceColumns: ColumnsType<RegionalSource> = [
    {
      title: "区域网络与来源",
      key: "source",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.regionalNetworkName}</Text>
          <Text type="secondary">{record.sourceOrganizationName}</Text>
          <Text className={styles.identifier}>{record.sourceId}</Text>
        </Space>
      ),
    },
    {
      title: "可信等级",
      dataIndex: "trustLevel",
      key: "trustLevel",
      render: (value) => <Tag color={TRUST_LEVEL_COLOR[String(value)] ?? "default"}>{value}</Tag>,
    },
    { title: "可信证据", dataIndex: "evidenceText", key: "evidenceText" },
    { title: "组织范围", dataIndex: "orgPath", key: "orgPath" },
    {
      title: "绑定链路",
      key: "binding",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text type="secondary">适配器：{record.adapterId ?? "未绑定"}</Text>
          <Text type="secondary">接入申请：{record.onboardingId ?? "未绑定"}</Text>
        </Space>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (value) => <Tag color={value === "ACTIVE" ? "green" : "default"}>{value}</Tag>,
    },
  ];

  const fieldMappingItems = status?.sources ?? [];
  const requiredSources = status?.requiredSources ?? [];

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
          void webhooksQuery.refetch();
          void regionalSourcesQuery.refetch();
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
                title="未连接"
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

          <div className={styles.operationGrid}>
            <RequiredSourcesPanel items={requiredSources} />
            <DataContractPanel
              versionInput={contractVersionInput}
              onVersionInputChange={setContractVersionInput}
              onLoad={handleLoadDataContract}
              loading={dataContractQuery.isFetching}
              contract={contractVersion ? dataContractQuery.data : undefined}
              error={contractVersion ? dataContractQuery.isError : false}
            />
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
              type={HEALTH_ALERT_TYPE[healthResult.healthStatus] ?? "warning"}
              showIcon
              message={HEALTH_ALERT_MESSAGE[healthResult.healthStatus] ?? "外部系统当前不可达"}
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
                      message="连接状态来自实时探活"
                      description="HTTP、FHIR、Webhook 和 WebService 使用真实连接器；外部不可达显示“未连接”，配置非法显示“配置不完整”。"
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
                        description="点击页面右上角“生成质量报告”，后端会基于当前服务空间的适配器、字段映射和探活事实生成快照。"
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
              {
                key: "webhooks",
                label: "回调通道",
                children: (
                  <div className={styles.sectionStack}>
                    <div className={styles.toolbar}>
                      <Text type="secondary">
                        共享密钥仅在创建成功时显示一次；列表和签名预览不会返回密钥。
                      </Text>
                      <Button
                        icon={<PlusOutlined aria-hidden="true" />}
                        disabled={!canWrite}
                        onClick={() => setWebhookModalOpen(true)}
                      >
                        新增回调通道
                      </Button>
                    </div>
                    <Table
                      rowKey="webhookId"
                      columns={webhookColumns}
                      dataSource={webhooks}
                      loading={webhooksQuery.isLoading}
                      pagination={false}
                      scroll={{ x: 900 }}
                    />
                    <Card title="签名预览" className={styles.sectionCard}>
                      <Form form={signatureForm} layout="vertical">
                        <Form.Item label="回调通道" required>
                          <Select
                            value={signatureWebhookId}
                            onChange={setSignatureWebhookId}
                            options={webhooks.map((item) => ({
                              label: `${item.name}（${item.webhookId}）`,
                              value: item.webhookId,
                            }))}
                            placeholder="选择已登记通道"
                          />
                        </Form.Item>
                        <Form.Item
                          name="payload"
                          label="签名预览载荷"
                          rules={[{ required: true, message: "请输入签名预览载荷" }]}
                        >
                          <Input.TextArea rows={3} placeholder='例如 {"event":"clinical.test"}' />
                        </Form.Item>
                        <Button
                          loading={testWebhookSignatureMutation.isPending}
                          disabled={!canExecute || webhooks.length === 0}
                          onClick={handleTestWebhookSignature}
                        >
                          生成签名预览
                        </Button>
                      </Form>
                      {signatureResult && (
                        <Alert
                          className={styles.resultAlert}
                          type="info"
                          showIcon
                          message={
                            <Space wrap>
                              <Tag color="blue">{customerEnumLabel(signatureResult.status)}</Tag>
                              <Tag>{customerEnumLabel(signatureResult.connectionStatus)}</Tag>
                            </Space>
                          }
                          description={
                            <Space direction="vertical" size="small">
                              <Text>{signatureResult.message}</Text>
                              <Text className={styles.identifier}>{signatureResult.signature}</Text>
                            </Space>
                          }
                        />
                      )}
                    </Card>
                  </div>
                ),
              },
              {
                key: "regional-sources",
                label: "区域来源",
                children: (
                  <div className={styles.sectionStack}>
                    <div className={styles.toolbar}>
                      <Text type="secondary">
                        跨机构来源必须先登记来源机构、可信等级和可核验证据。
                      </Text>
                      <Button
                        icon={<PlusOutlined aria-hidden="true" />}
                        disabled={!canWrite}
                        onClick={() => setRegionalSourceModalOpen(true)}
                      >
                        登记区域来源
                      </Button>
                    </div>
                    <Table
                      rowKey="sourceId"
                      columns={regionalSourceColumns}
                      dataSource={regionalSources}
                      loading={regionalSourcesQuery.isLoading}
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
        title="新增回调通道"
        open={webhookModalOpen}
        onOk={handleCreateWebhook}
        onCancel={() => setWebhookModalOpen(false)}
        okText="创建回调通道"
        cancelText="取消"
        confirmLoading={createWebhookMutation.isPending}
      >
        <Form form={webhookForm} layout="vertical">
          <Form.Item name="webhookId" label="回调标识" rules={[{ required: true }]}>
            <Input placeholder="例如 clinical-events" />
          </Form.Item>
          <Form.Item name="name" label="通道名称" rules={[{ required: true }]}>
            <Input placeholder="例如临床事件回调" />
          </Form.Item>
          <Form.Item
            name="callbackUrl"
            label="回调地址"
            rules={[
              { required: true },
              { type: "url", message: "请输入合法的 HTTP 或 HTTPS 地址" },
            ]}
          >
            <Input placeholder="https://his.example.org/medkernel/events" />
          </Form.Item>
          <Form.Item name="eventsSubscribed" label="订阅事件" rules={[{ required: true }]}>
            <Input placeholder="例如 clinical.event.accepted" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="保存共享密钥"
        open={createdWebhook !== null}
        closable={false}
        maskClosable={false}
        cancelButtonProps={{ style: { display: "none" } }}
        okText="我已安全保存"
        onOk={() => setCreatedWebhook(null)}
      >
        <Alert
          type="warning"
          showIcon
          message="共享密钥仅显示一次"
          description="请立即保存到受控凭证系统。关闭后，列表、测试和日志均不会再次返回该密钥。"
        />
        <Text className={styles.secretValue}>{createdWebhook?.sharedSecret}</Text>
      </Modal>

      <Modal
        title="登记区域来源"
        open={regionalSourceModalOpen}
        onOk={handleRegisterRegionalSource}
        onCancel={() => setRegionalSourceModalOpen(false)}
        okText="保存区域来源"
        cancelText="取消"
        confirmLoading={registerRegionalSourceMutation.isPending}
      >
        <Form form={regionalSourceForm} layout="vertical">
          <Form.Item name="sourceId" label="来源标识" rules={[{ required: true }]}>
            <Input placeholder="例如 regional-lab" />
          </Form.Item>
          <Form.Item name="regionalNetworkName" label="区域网络" rules={[{ required: true }]}>
            <Input placeholder="区域检验互认平台" />
          </Form.Item>
          <Form.Item name="sourceOrganizationId" label="来源机构标识" rules={[{ required: true }]}>
            <Input placeholder="来源机构业务标识" />
          </Form.Item>
          <Form.Item
            name="sourceOrganizationName"
            label="来源机构名称"
            rules={[{ required: true }]}
          >
            <Input placeholder="来源机构全称" />
          </Form.Item>
          <Form.Item name="trustLevel" label="可信等级" rules={[{ required: true }]}>
            <Select
              placeholder="选择可信等级"
              options={[
                { label: riskLabel("HIGH"), value: "HIGH" },
                { label: riskLabel("MEDIUM"), value: "MEDIUM" },
                { label: riskLabel("LOW"), value: "LOW" },
              ]}
            />
          </Form.Item>
          <Form.Item name="evidenceText" label="可信证据" rules={[{ required: true }]}>
            <Input.TextArea rows={3} placeholder="填写协议、验收单或其他可核验证据" />
          </Form.Item>
          <Form.Item name="adapterId" label="绑定适配器">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="可选，按名称选择"
              options={adapters.map((adapter) => ({
                value: adapter.adapterId,
                label: `${adapter.name} · ${adapter.protocolType}`,
              }))}
              notFoundContent="暂无可绑定适配器"
            />
          </Form.Item>
          <Form.Item name="onboardingId" label="绑定接入申请">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="可选，按名称选择"
              options={onboardings.map((item) => ({
                value: item.onboardingId,
                label: `${item.name} · ${customerEnumLabel(item.status)}`,
              }))}
              notFoundContent="暂无可绑定接入申请"
            />
          </Form.Item>
          <Form.Item name="orgPath" label="组织范围" rules={[{ required: true }]}>
            <OrgUnitSelect
              scope="BUSINESS_SCOPE"
              valueMode="PATH"
              placeholder="从组织树选择适用范围"
              notFoundContent="暂无可选组织，请先维护组织架构"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新增适配器"
        open={adapterModalOpen}
        onOk={handleCreateAdapter}
        onCancel={() => setAdapterModalOpen(false)}
        okText="提交适配器"
        cancelText="取消"
        width={760}
      >
        <Form
          form={adapterForm}
          layout="vertical"
          initialValues={{
            protocolType: "REST",
            healthPath: "/health",
            outboundPath: "/messages",
            connectTimeoutMs: 2000,
            requestTimeoutMs: 5000,
            fieldMappings: [{}],
          }}
        >
          <Form.Item name="adapterId" label="适配器标识" rules={[{ required: true }]}>
            <Input placeholder="输入真实适配器标识" />
          </Form.Item>
          <Form.Item name="name" label="系统名称" rules={[{ required: true }]}>
            <Input placeholder="输入院内系统名称" />
          </Form.Item>
          <Form.Item name="protocolType" label="接入协议" rules={[{ required: true }]}>
            <Select options={[...ADAPTER_PROTOCOL_OPTIONS]} />
          </Form.Item>
          {expertMode ? (
            <Form.Item
              name="configJson"
              label="连接与字段映射 JSON"
              rules={[
                {
                  validator: async (_, value?: string) => {
                    if (!value?.trim()) return;
                    try {
                      JSON.parse(value);
                    } catch {
                      throw new Error("请输入合法 JSON");
                    }
                  },
                },
              ]}
            >
              <Input.TextArea rows={6} placeholder="输入后端契约支持的适配器配置 JSON" />
            </Form.Item>
          ) : (
            <>
              {usesHttpConnector && (
                <>
                  <Form.Item
                    name="baseUrl"
                    label="服务地址"
                    rules={[
                      { required: true, message: "请输入服务地址" },
                      { type: "url", message: "请输入合法的 HTTP 或 HTTPS 地址" },
                    ]}
                  >
                    <Input placeholder="https://his.example.org/api" />
                  </Form.Item>
                  <Space align="start" wrap className="mk-full-width">
                    <Form.Item
                      name="healthPath"
                      label="探活路径"
                      rules={[{ pattern: /^\/(?!\/)/, message: "请输入以 / 开头的站内路径" }]}
                    >
                      <Input placeholder="/health" />
                    </Form.Item>
                    <Form.Item
                      name="outboundPath"
                      label="投递路径"
                      rules={[{ pattern: /^\/(?!\/)/, message: "请输入以 / 开头的站内路径" }]}
                    >
                      <Input placeholder="/messages" />
                    </Form.Item>
                    <Form.Item name="connectTimeoutMs" label="连接超时">
                      <InputNumber min={200} max={30000} addonAfter="ms" />
                    </Form.Item>
                    <Form.Item name="requestTimeoutMs" label="请求超时">
                      <InputNumber min={200} max={30000} addonAfter="ms" />
                    </Form.Item>
                  </Space>
                </>
              )}
              <Form.List name="fieldMappings">
                {(fields, { add, remove }) => (
                  <Space direction="vertical" className="mk-full-width">
                    {fields.map(({ key, name, ...field }) => (
                      <Space key={key} align="start" wrap className="mk-full-width">
                        <Form.Item
                          {...field}
                          name={[name, "sourcePath"]}
                          label="来源字段路径"
                          rules={[
                            { required: true, message: "请输入来源字段路径" },
                            { pattern: /^\//, message: "字段路径必须以 / 开头" },
                          ]}
                        >
                          <Input placeholder="/patient/id" />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[name, "targetPath"]}
                          label="标准字段路径"
                          rules={[
                            { required: true, message: "请输入标准字段路径" },
                            { pattern: /^\//, message: "字段路径必须以 / 开头" },
                          ]}
                        >
                          <Input placeholder="/patient/id" />
                        </Form.Item>
                        <Form.Item {...field} name={[name, "termMappingId"]} label="术语映射">
                          <Select
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            placeholder="可选，选择已确认映射"
                            options={(terminologyMappingsQuery.data?.items ?? []).map(
                              (mapping) => ({
                                value: String(mapping.id),
                                label: `${mapping.sourceSystem} · ${mapping.category} · 映射 ${mapping.id}`,
                              }),
                            )}
                            notFoundContent="暂无已确认术语映射"
                          />
                        </Form.Item>
                        {fields.length > 1 && (
                          <Button
                            aria-label="删除字段映射"
                            icon={<DeleteOutlined />}
                            onClick={() => remove(name)}
                          />
                        )}
                      </Space>
                    ))}
                    <Button icon={<PlusOutlined />} onClick={() => add()}>
                      添加字段映射
                    </Button>
                  </Space>
                )}
              </Form.List>
            </>
          )}
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
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="适配器模式下选择"
              options={adapters.map((adapter) => ({
                value: adapter.adapterId,
                label: `${adapter.name} · ${adapter.protocolType}`,
              }))}
              notFoundContent="暂无可用适配器"
            />
          </Form.Item>
          <Form.Item name="fhirVersion" label="FHIR 版本">
            <Select
              allowClear
              placeholder="FHIR 模式下选择"
              options={[
                { value: "R4", label: "FHIR R4" },
                { value: "R5", label: "FHIR R5" },
              ]}
            />
          </Form.Item>
          <Form.Item name="sourceSystem" label="来源系统" rules={[{ required: true }]}>
            <Input placeholder="例如 HIS / EMR / LIS" />
          </Form.Item>
          <Form.Item name="businessScenario" label="业务场景" rules={[{ required: true }]}>
            <Input placeholder="例如门诊患者主数据" />
          </Form.Item>
          <Form.Item name="orgPath" label="组织范围" rules={[{ required: true }]}>
            <OrgUnitSelect
              scope="BUSINESS_SCOPE"
              valueMode="PATH"
              placeholder="从组织树选择适用范围"
              notFoundContent="暂无可选组织，请先维护组织架构"
            />
          </Form.Item>
          <Form.Item name="callbackWebhookId" label="回调通道标识">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="可选，选择已配置回调通道"
              options={webhooks.map((item) => ({
                value: item.webhookId,
                label: `${item.name} · ${customerEnumLabel(item.status)}`,
              }))}
              notFoundContent="暂无回调通道"
            />
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

function RequiredSourcesPanel({ items }: { items: AdapterHubRequiredSourceStatus[] }) {
  return (
    <Card title="必接系统清单" className={styles.sectionCard}>
      {items.length === 0 ? (
        <Text type="secondary">暂无必接系统状态。</Text>
      ) : (
        <div className={styles.requiredSourceGrid}>
          {items.map((item) => (
            <div key={item.sourceSystem} className={styles.requiredSourceItem}>
              <Space direction="vertical" size="small" className="mk-full-width">
                <Space wrap>
                  <Text strong>{item.label}</Text>
                  <Tag color={requiredSourceStatusColor(item)}>
                    {customerEnumLabel(item.status)}
                  </Tag>
                  {healthTag(item.healthStatus)}
                </Space>
                <Text type="secondary">{item.adapterName ?? "未绑定适配器"}</Text>
                <Text type="secondary">字段映射 {item.mappedFieldCount} 项</Text>
                {item.gaps.length > 0 && (
                  <ul className={styles.gapList}>
                    {item.gaps.map((gap) => (
                      <li key={gap}>{gap}</li>
                    ))}
                  </ul>
                )}
              </Space>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

function DataContractPanel({
  versionInput,
  onVersionInputChange,
  onLoad,
  loading,
  contract,
  error,
}: {
  versionInput: string;
  onVersionInputChange: (value: string) => void;
  onLoad: () => void;
  loading: boolean;
  contract?: IntegrationDataContractResponse;
  error: boolean;
}) {
  const resourceCount = contract ? Object.keys(contract.resources).length : 0;
  return (
    <Card title="数据接入契约" className={styles.sectionCard}>
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Space align="end" wrap>
          <Space direction="vertical" size={4}>
            <label htmlFor="integration-contract-version">版本号</label>
            <Input
              id="integration-contract-version"
              value={versionInput}
              onChange={(event) => onVersionInputChange(event.target.value)}
              placeholder="pkg-2026.06"
            />
          </Space>
          <Button loading={loading} onClick={onLoad}>
            读取契约
          </Button>
        </Space>
        {error && <Alert type="warning" showIcon message="数据接入契约暂时不可用" />}
        {contract ? (
          <Space direction="vertical" size="small">
            <Text strong>{contract.contractId}</Text>
            <Space wrap>
              <Tag color="blue">{contract.schemaVersion}</Tag>
              <Text>资源 {resourceCount} 类</Text>
              <Text>字段 {contract.fields.length} 项</Text>
            </Space>
            <Text type="secondary">{contract.accessGuide[0]}</Text>
          </Space>
        ) : (
          <Text type="secondary">输入 packageVersion 后读取字段目录派生的外部接入契约。</Text>
        )}
      </Space>
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
