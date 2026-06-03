import { useState, useEffect } from "react";
import {
  Table,
  Button,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  Card,
  Badge,
  Alert,
  message,
  Row,
  Col,
  List,
  Tabs,
  Space,
  theme,
  Statistic,
  Descriptions,
} from "antd";
import {
  PlusOutlined,
  PlayCircleOutlined,
  SafetyCertificateOutlined,
  ApiOutlined,
  CodeOutlined,
  FileProtectOutlined,
  CloudSyncOutlined,
  CheckCircleOutlined,
  DisconnectOutlined,
  HeartOutlined,
  ReloadOutlined,
  DeleteOutlined,
  LockOutlined,
  CompassOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useEmbedOrigins,
  useAddEmbedOrigin,
  useGenerateEmbedToken,
  useIntegrationAdapters,
  useCreateAdapter,
  useUpdateAdapter,
  useCheckAdapterHealth,
  useWebhooks,
  useCreateWebhook,
  useTestWebhookSignature,
  useIntegrationLogs,
  useRetryMessage,
  useDeleteMessage,
} from "@/shared/api/hooks";
import type { IntegrationAdapter, WebhookSignatureTestResult } from "@/shared/api/hooks";
import { applyApiFieldErrors, getApiErrorMessage } from "@/shared/api/errors";

const { Option } = Select;

type EmbedMessageData = Record<string, unknown> & {
  source: "MEDKERNEL_CDSS_EMBED";
  action?: string;
  reason?: string;
};

interface QualityDiagnosticReport {
  adapterId: string;
  healthStatus: string;
  rttMs: number | null;
  lastHeartbeatAt?: string;
  errorMessage?: string;
}

function isEmbedMessageData(data: unknown): data is EmbedMessageData {
  return (
    typeof data === "object" &&
    data !== null &&
    (data as { source?: unknown }).source === "MEDKERNEL_CDSS_EMBED"
  );
}

function getHealthTagColor(status: string) {
  if (status === "HEALTHY") return "green";
  if (status === "NOT_CONNECTED") return "default";
  return "red";
}

function isDiagnosticError(report: QualityDiagnosticReport) {
  return report.healthStatus === "MISCONFIGURED" || report.healthStatus === "ERROR";
}

function getDiagnosticCardClassName(report: QualityDiagnosticReport) {
  return `border-slate-200 mt-4 rounded-xl ${
    isDiagnosticError(report) ? "bg-red-50/20 border-red-200" : "bg-slate-50"
  }`;
}

function renderDiagnosticAlert(report: QualityDiagnosticReport) {
  if (report.healthStatus === "ERROR") {
    return (
      <Alert
        type="error"
        showIcon
        message="健康检查请求失败"
        description={report.errorMessage}
        className="mt-3 rounded-lg text-xs"
      />
    );
  }

  if (report.healthStatus === "MISCONFIGURED") {
    return (
      <Alert
        type="error"
        showIcon
        message="适配器配置非法 (MISCONFIGURED)"
        description="configJson 不是合法 JSON，请修正后重试。"
        className="mt-3 rounded-lg text-xs"
      />
    );
  }

  return (
    <Alert
      type="info"
      showIcon
      message="本地配置校验通过，外部连通性未知 (NOT_CONNECTED)"
      description="当前未接入真实外部连接器，无法判定外部可达性；据实不伪造 HEALTHY 或网络 RTT。"
      className="mt-3 rounded-lg text-xs"
    />
  );
}

export default function AdapterHub() {
  const { token } = theme.useToken();

  // ==========================================
  // 1. API 接口数据绑定 (React Query)
  // ==========================================
  const { data: apiOrigins, refetch: refetchOrigins } = useEmbedOrigins();
  const addOriginMutation = useAddEmbedOrigin();
  const generateTokenMutation = useGenerateEmbedToken();

  // 集成总线接口 hooks
  const {
    data: apiAdapters,
    refetch: refetchAdapters,
    isLoading: loadingAdapters,
  } = useIntegrationAdapters();
  const createAdapterMutation = useCreateAdapter();
  const updateAdapterMutation = useUpdateAdapter();
  const healthCheckMutation = useCheckAdapterHealth();

  const { data: apiWebhooks, refetch: refetchWebhooks } = useWebhooks();
  const createWebhookMutation = useCreateWebhook();
  const testWebhookSigMutation = useTestWebhookSignature();

  const [logPage, setLogPage] = useState(1);
  const {
    data: apiLogsData,
    refetch: refetchLogs,
    isLoading: loadingLogs,
  } = useIntegrationLogs(logPage, 5);
  const retryMessageMutation = useRetryMessage();
  const deleteMessageMutation = useDeleteMessage();

  // ==========================================
  // 2. 嵌入交互状态
  // ==========================================
  const [localOrigins] = useState<string[]>([]);
  const [originFormVisible, setOriginFormVisible] = useState<boolean>(false);
  const [sandboxVisible, setSandboxVisible] = useState<boolean>(false);

  // 嵌入沙箱所用的 token & URL 状态
  const [sandboxToken, setSandboxToken] = useState<string>("");
  const [sandboxUrl, setSandboxUrl] = useState<string>("");
  const [hisOrders, setHisOrders] = useState<string[]>([]);
  const [postMessageLogs, setPostMessageLogs] = useState<EmbedMessageData[]>([]);

  // 适配器 & Webhook 控制表单状态
  const [adapterModalVisible, setAdapterModalVisible] = useState(false);
  const [webhookModalVisible, setWebhookModalVisible] = useState(false);
  const [healthCheckLoadingMap, setHealthCheckLoadingMap] = useState<Record<string, boolean>>({});
  const [qualityDiagnosticReport, setQualityDiagnosticReport] =
    useState<QualityDiagnosticReport | null>(null);

  // Webhook 签名测试状态
  const [selectedWebhookId, setSelectedWebhookId] = useState<string>("");
  const [webhookTestPayload, setWebhookTestPayload] = useState<string>("");
  const [testResultSummary, setTestResultSummary] = useState<WebhookSignatureTestResult | null>(
    null,
  );
  const [testLogLoading, setTestLogLoading] = useState<boolean>(false);

  // 表单 Form 定义
  const [originForm] = Form.useForm();
  const [tokenForm] = Form.useForm();
  const [adapterForm] = Form.useForm();
  const [webhookForm] = Form.useForm();

  // 仅展示后端真实数据（含本会话内本地新增的跨域白名单）；无数据走空态，不再用写死兜底冒充已接入。
  const displayOrigins = apiOrigins ?? localOrigins;
  const displayAdapters = apiAdapters ?? [];
  const displayWebhooks = apiWebhooks ?? [];
  const displayLogs = apiLogsData?.items ?? [];
  const displayLogsTotal = apiLogsData?.total ?? 0;

  // 3. 监听跨域通信
  useEffect(() => {
    const handleIframeMessage = (event: MessageEvent) => {
      if (isEmbedMessageData(event.data)) {
        const payload = event.data;
        setPostMessageLogs((prev) => [payload, ...prev]);

        if (payload.action === "ADOPT") {
          message.success("【HIS系统成功收到跨域指令】已登记采纳反馈。");
          setHisOrders((prev) => [...prev, "已接收采纳反馈，医嘱由 HIS/EMR 真实系统处理。"]);
        } else if (payload.action === "REJECT") {
          const reason = payload.reason || "未提供拒绝理由";
          message.warning(`【HIS系统收到拒绝事件】反馈理由已存档：${reason}`);
          setHisOrders((prev) => [...prev, `临床拒绝 CDSS 建议：${reason}`]);
        }
      }
    };

    window.addEventListener("message", handleIframeMessage);
    return () => {
      window.removeEventListener("message", handleIframeMessage);
    };
  }, []);

  // 4. 动作执行 (跨域白名单 & Token 发生)
  const handleAddOrigin = async () => {
    let values;
    try {
      values = await originForm.validateFields();
    } catch {
      return; // 表单校验错误已在控件上提示
    }
    try {
      await addOriginMutation.mutateAsync({ origin: values.origin });
      message.success("跨域安全域名配置成功！");
      setOriginFormVisible(false);
      originForm.resetFields();
      refetchOrigins();
    } catch (error: unknown) {
      if (applyApiFieldErrors(originForm, error)) return;
      message.error(getApiErrorMessage(error, "跨域安全域名配置失败，请稍后重试"));
    }
  };

  const handleGenerateToken = async () => {
    try {
      const values = await tokenForm.validateFields();
      const res = await generateTokenMutation.mutateAsync({
        userId: values.userId,
        roleCode: values.roleCode,
        patientId: values.patientId,
        encounterId: values.encounterId,
        triggerPoint: values.triggerPoint,
        expireSeconds: 600,
      });

      if (res?.token) {
        setSandboxToken(res.token);
        setSandboxUrl(`${window.location.origin}${res.embedUrl}`);
        setSandboxVisible(true);
        setPostMessageLogs([]);
      }
    } catch (error: unknown) {
      if (applyApiFieldErrors(tokenForm, error)) return;
      message.error(
        getApiErrorMessage(error, "生成 Launch Token 失败，请稍后重试或确认嵌入服务已就绪"),
      );
    }
  };

  // 5. 适配器生命周期操作
  const handleCreateAdapter = async () => {
    try {
      const values = await adapterForm.validateFields();
      await createAdapterMutation.mutateAsync(values);
      message.success("异构适配器配置成功！");
      setAdapterModalVisible(false);
      adapterForm.resetFields();
      refetchAdapters();
    } catch (error: unknown) {
      if (applyApiFieldErrors(adapterForm, error)) return;
      message.error(getApiErrorMessage(error, "配置适配器失败，请检查参数"));
    }
  };

  const handleCheckAdapterHealth = async (adapterId: string) => {
    setHealthCheckLoadingMap((prev) => ({ ...prev, [adapterId]: true }));
    try {
      const res = await healthCheckMutation.mutateAsync(adapterId);
      // 仅展示后端真实健康检查结果，不从 configJson 反解伪造体检报告。
      setQualityDiagnosticReport({
        adapterId: res.adapterId,
        healthStatus: res.healthStatus,
        rttMs: res.rttMs,
        lastHeartbeatAt: res.lastHeartbeatAt,
      });
      if (res.healthStatus === "MISCONFIGURED") {
        message.error(`适配器 [${adapterId}] 配置非法 (MISCONFIGURED)，请修正 configJson 后重试`);
      } else {
        message.info(
          `适配器 [${adapterId}] 本地配置校验通过；外部连通性未知 (NOT_CONNECTED)，待接入真实连接器`,
        );
      }
      refetchAdapters();
    } catch (error: unknown) {
      const errMsg = getApiErrorMessage(error, "健康检查请求失败，请稍后重试");
      setQualityDiagnosticReport({
        adapterId,
        healthStatus: "ERROR",
        rttMs: null,
        errorMessage: errMsg,
      });
      message.error(`[健康检查失败] ${adapterId}：${errMsg}`);
    } finally {
      setHealthCheckLoadingMap((prev) => ({ ...prev, [adapterId]: false }));
    }
  };

  const handleToggleAdapterStatus = async (adapter: IntegrationAdapter) => {
    try {
      const newStatus = adapter.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
      await updateAdapterMutation.mutateAsync({
        adapterId: adapter.adapterId,
        payload: {
          name: adapter.name,
          protocolType: adapter.protocolType,
          configJson: adapter.configJson,
          status: newStatus,
        },
      });
      message.success(`适配器状态已变更为: ${newStatus}`);
      refetchAdapters();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "切换适配器状态失败，请稍后重试"));
    }
  };

  // 6. Webhook 配置与签名测试
  const handleCreateWebhook = async () => {
    try {
      const values = await webhookForm.validateFields();
      await createWebhookMutation.mutateAsync(values);
      message.success("Webhook 订阅成功，已强随机生成 128 位数字签名密钥！");
      setWebhookModalVisible(false);
      webhookForm.resetFields();
      refetchWebhooks();
    } catch (error: unknown) {
      if (applyApiFieldErrors(webhookForm, error)) return;
      message.error(getApiErrorMessage(error, "创建 Webhook 订阅失败"));
    }
  };

  const handleTestWebhookSignature = async () => {
    if (!selectedWebhookId) {
      message.warning("请先在左侧选择需要测试的 Webhook 订阅通道！");
      return;
    }
    const payload = webhookTestPayload.trim();
    if (!payload) {
      message.warning("请粘贴真实脱敏 Webhook 报文后再发起签名测试。");
      return;
    }
    try {
      JSON.parse(payload);
    } catch {
      message.error("Webhook 报文必须是合法 JSON。");
      return;
    }
    setTestLogLoading(true);
    try {
      const res = await testWebhookSigMutation.mutateAsync({
        webhookId: selectedWebhookId,
        payload,
      });
      setTestResultSummary(res);
      message.success("HMAC-SHA256 签名双向安全校准握手测试成功！");
    } catch (error: unknown) {
      message.error(
        getApiErrorMessage(error, "Webhook 签名测试失败，请确认订阅通道存在且报文为合法 JSON"),
      );
    } finally {
      setTestLogLoading(false);
    }
  };

  // 7. 死信重试操作
  const handleRetryMessage = async (messageId: string) => {
    try {
      const res = await retryMessageMutation.mutateAsync(messageId);
      if (res.status === "SUCCESS") {
        message.success(`消息 [${messageId}] 手动重投成功！`);
      } else {
        message.warning(`重新投递失败，状态: ${res.status}`);
      }
      refetchLogs();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, `消息 [${messageId}] 重试失败，请稍后重试`));
    }
  };

  const handleDeleteMessage = async (messageId: string) => {
    try {
      await deleteMessageMutation.mutateAsync(messageId);
      message.success("成功删除该重试死信项并标记已解决");
      refetchLogs();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "删除消息失败，请稍后重试"));
    }
  };

  // 客户端导出当前页交易流水快照（真实数据，无伪造哈希）；带后端签名的防伪存证包需 INTEG 导出接口支持。
  const handleExportLogsCertificate = () => {
    if (displayLogs.length === 0) {
      message.info("当前没有可导出的交易流水记录。");
      return;
    }
    const blob = new Blob([JSON.stringify(displayLogs, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `integration-message-logs-${Date.now()}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
    message.success(
      "已导出当前页交易流水快照（客户端 JSON）。带后端签名、可用于互联互通测评的防伪存证包需 INTEG 导出接口支持（待接入）。",
    );
  };

  return (
    <PageShell
      title="第三方对接总线与页面集成"
      description="管理院内各异构系统的底层物理连接。支持适配器生命周期、Webhook HMAC-SHA256 安全签名自校准、死信重试队列以及 HIS 免登 Launch 仿真沙盒。"
    >
      {/* 顶部简易 Metric 状态大盘 */}
      <Row gutter={16} className="mb-6">
        <Col span={6}>
          <Card bordered={false} className="shadow-sm hover:shadow transition-shadow">
            <Statistic
              title="已注册适配器数"
              value={displayAdapters.length}
              prefix={<ApiOutlined className="text-sky-500 mr-1.5" />}
              valueStyle={{ color: token.colorPrimary }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card bordered={false} className="shadow-sm hover:shadow transition-shadow">
            <Statistic
              title="真实连通率 (HEALTHY)"
              value={
                displayAdapters.length === 0
                  ? 0
                  : Math.round(
                      (displayAdapters.filter((a) => a.healthStatus === "HEALTHY").length /
                        displayAdapters.length) *
                        100,
                    )
              }
              suffix="%"
              prefix={<CheckCircleOutlined className="text-emerald-500 mr-1.5" />}
              valueStyle={{ color: token.colorSuccess }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card bordered={false} className="shadow-sm hover:shadow transition-shadow">
            <Statistic
              title="重试死信失败项"
              value={displayLogs.filter((l) => l.status !== "SUCCESS").length}
              prefix={<DisconnectOutlined className="text-amber-500 mr-1.5" />}
              valueStyle={{ color: token.colorWarning }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card bordered={false} className="shadow-sm hover:shadow transition-shadow">
            <Statistic
              title="安全域名防护数"
              value={displayOrigins.length}
              prefix={<SafetyCertificateOutlined className="text-purple-500 mr-1.5" />}
              valueStyle={{ color: "var(--ant-purple-6)" }}
            />
          </Card>
        </Col>
      </Row>

      {/* 核心多 Tab 功能页签 */}
      <Card bordered={false} className="shadow-sm rounded-2xl">
        <Tabs
          defaultActiveKey="adapters"
          items={[
            {
              key: "adapters",
              label: (
                <span className="flex items-center gap-1.5 text-xs">
                  <ApiOutlined />
                  <span>适配器生命周期管理</span>
                </span>
              ),
              children: (
                <div className="flex flex-col gap-4">
                  <div className="flex justify-between items-center">
                    <Alert
                      type="info"
                      showIcon
                      message="系统级适配器连接诊断"
                      description="通过配置各异构适配器接入 HIS/EMR 等实时数据，支持一键健康检查与诚实断连标记。"
                      className="w-3/4 rounded-lg text-xs"
                    />
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => setAdapterModalVisible(true)}
                      className="bg-sky-600 border-sky-600 hover:bg-sky-700 rounded-md"
                    >
                      新建适配器
                    </Button>
                  </div>

                  <Table
                    dataSource={displayAdapters.map((a) => ({ ...a, key: a.adapterId }))}
                    loading={loadingAdapters}
                    pagination={false}
                    columns={[
                      {
                        title: "适配器标识/系统名",
                        key: "name",
                        render: (_, record) => (
                          <div className="flex flex-col">
                            <span className="font-semibold text-slate-800 text-xs">
                              {record.name}
                            </span>
                            <span className="text-[10px] text-slate-400 font-normal">
                              {record.adapterId}
                            </span>
                          </div>
                        ),
                      },
                      {
                        title: "接入协议",
                        dataIndex: "protocolType",
                        key: "protocolType",
                        render: (type) => (
                          <Tag color="blue" className="font-normal m-0 text-[10px]">
                            {type}
                          </Tag>
                        ),
                      },
                      {
                        title: "健康状态",
                        key: "healthStatus",
                        render: (_, record) => {
                          const statusMap: Record<
                            string,
                            "success" | "error" | "default" | "warning"
                          > = {
                            HEALTHY: "success",
                            NOT_CONNECTED: "default",
                            MISCONFIGURED: "error",
                            UNHEALTHY: "error",
                          };
                          return (
                            <Badge
                              status={statusMap[record.healthStatus] ?? "default"}
                              text={record.healthStatus}
                              className="font-normal text-xs"
                            />
                          );
                        },
                      },
                      {
                        title: "真实探活延迟",
                        dataIndex: "rttMs",
                        key: "rttMs",
                        render: (rtt) => (
                          <span className="font-normal text-xs">{rtt ? `${rtt}ms` : "—"}</span>
                        ),
                      },
                      {
                        title: "运行状态",
                        dataIndex: "status",
                        key: "status",
                        render: (status) => (
                          <Tag color={status === "ACTIVE" ? "green" : "red"}>
                            {status === "ACTIVE" ? "启用中" : "已挂起"}
                          </Tag>
                        ),
                      },
                      {
                        title: "最近健康检查",
                        dataIndex: "lastHeartbeatAt",
                        key: "lastHeartbeatAt",
                        render: (t) => (
                          <span className="text-[10px] text-slate-400 font-normal">
                            {t ? new Date(t).toLocaleString() : "暂无"}
                          </span>
                        ),
                      },
                      {
                        title: "健康操作",
                        key: "actions",
                        render: (_, record) => (
                          <Space size="middle">
                            <Button
                              size="small"
                              icon={<HeartOutlined />}
                              loading={healthCheckLoadingMap[record.adapterId]}
                              onClick={() => handleCheckAdapterHealth(record.adapterId)}
                              className="hover:border-sky-500 hover:text-sky-500"
                            >
                              健康诊断
                            </Button>
                            <Button
                              size="small"
                              danger={record.status === "ACTIVE"}
                              onClick={() => handleToggleAdapterStatus(record)}
                              className="text-xs"
                            >
                              {record.status === "ACTIVE" ? "挂起" : "激活"}
                            </Button>
                          </Space>
                        ),
                      },
                    ]}
                  />

                  {/* 适配器健康检查结果（仅展示后端真实返回，不伪造体检指标） */}
                  {qualityDiagnosticReport && (
                    <Card
                      title={
                        <span className="flex items-center gap-1.5 text-xs text-sky-600 font-semibold">
                          <CompassOutlined />
                          <span>适配器健康检查结果</span>
                        </span>
                      }
                      className={getDiagnosticCardClassName(qualityDiagnosticReport)}
                      size="small"
                    >
                      <Descriptions size="small" column={3}>
                        <Descriptions.Item label="适配器">
                          <span className="font-normal text-xs">
                            {qualityDiagnosticReport.adapterId}
                          </span>
                        </Descriptions.Item>
                        <Descriptions.Item label="健康状态">
                          <Tag
                            color={getHealthTagColor(qualityDiagnosticReport.healthStatus)}
                            className="font-bold"
                          >
                            {qualityDiagnosticReport.healthStatus}
                          </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="网络 RTT">
                          <span className="font-normal text-xs">
                            {qualityDiagnosticReport.rttMs
                              ? `${qualityDiagnosticReport.rttMs}ms`
                              : "未测量"}
                          </span>
                        </Descriptions.Item>
                      </Descriptions>
                      {renderDiagnosticAlert(qualityDiagnosticReport)}
                    </Card>
                  )}
                </div>
              ),
            },

            {
              key: "webhooks",
              label: (
                <span className="flex items-center gap-1.5 text-xs">
                  <LockOutlined />
                  <span>Webhook 回调订阅安全自研沙箱</span>
                </span>
              ),
              children: (
                <Row gutter={16}>
                  <Col span={10}>
                    <Card
                      title={
                        <div className="flex justify-between items-center w-full">
                          <span className="text-xs font-semibold">Webhook 接收端订阅管理</span>
                          <Button
                            type="primary"
                            size="small"
                            icon={<PlusOutlined />}
                            onClick={() => setWebhookModalVisible(true)}
                            className="bg-sky-600 border-sky-600 hover:bg-sky-700"
                          >
                            新增回调订阅
                          </Button>
                        </div>
                      }
                      className="rounded-xl border-slate-200"
                      size="small"
                    >
                      <List
                        dataSource={displayWebhooks}
                        renderItem={(item) => (
                          <List.Item
                            actions={[
                              <Button
                                type="link"
                                size="small"
                                onClick={() => setSelectedWebhookId(item.webhookId)}
                                className="text-xs"
                              >
                                测试握手
                              </Button>,
                            ]}
                          >
                            <List.Item.Meta
                              title={
                                <span className="text-xs font-bold text-slate-800">
                                  {item.name}
                                </span>
                              }
                              description={
                                <div className="flex flex-col gap-1 text-[10px] text-slate-400">
                                  <span className="font-normal select-all">
                                    地址: {item.callbackUrl}
                                  </span>
                                  <span>
                                    事件:{" "}
                                    <Tag className="m-0 py-0 px-1 text-[9px]" color="purple">
                                      {item.eventsSubscribed}
                                    </Tag>
                                  </span>
                                  <span className="font-normal select-all text-amber-600">
                                    密钥: {item.secretKey}
                                  </span>
                                </div>
                              }
                            />
                          </List.Item>
                        )}
                      />
                    </Card>
                  </Col>

                  <Col span={14}>
                    <Card
                      title={
                        <span className="flex items-center gap-1.5 text-xs font-semibold text-sky-600">
                          <CodeOutlined />
                          <span>HMAC-SHA256 签名双向自校准测试终端</span>
                        </span>
                      }
                      className="rounded-xl border-slate-200 bg-slate-50"
                      size="small"
                    >
                      <Space direction="vertical" className="w-full" size="middle">
                        <div>
                          <span className="text-slate-600 text-xs mr-2 font-medium">
                            选择目标配置:
                          </span>
                          <Select
                            className="w-56"
                            placeholder="请选择订阅通道"
                            value={selectedWebhookId}
                            onChange={(val) => setSelectedWebhookId(val)}
                            size="small"
                          >
                            {displayWebhooks.map((item) => (
                              <Option key={item.webhookId} value={item.webhookId}>
                                {item.name}
                              </Option>
                            ))}
                          </Select>
                        </div>

                        <div>
                          <span className="block text-slate-600 text-xs mb-1 font-medium">
                            输入真实脱敏通知报文 Payload (JSON):
                          </span>
                          <Input.TextArea
                            rows={4}
                            value={webhookTestPayload}
                            onChange={(e) => setWebhookTestPayload(e.target.value)}
                            placeholder="粘贴真实脱敏 Webhook 报文 JSON，不在页面内预置患者或就诊示例。"
                            className="font-normal text-xs rounded-lg"
                          />
                        </div>

                        <Button
                          type="primary"
                          icon={<CloudSyncOutlined />}
                          loading={testLogLoading}
                          onClick={handleTestWebhookSignature}
                          className="bg-sky-600 border-sky-600 hover:bg-sky-700 rounded-md text-xs"
                        >
                          发送通知并生成 HMAC-SHA256 安全签名
                        </Button>

                        {testResultSummary && (
                          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-inner flex flex-col gap-2">
                            <span className="font-semibold text-xs text-emerald-600 flex items-center gap-1">
                              <CheckCircleOutlined />
                              <span>签名生成与验证过程 (HMAC-SHA256 Pipeline)</span>
                            </span>
                            <div className="text-[10px] font-normal flex flex-col gap-1 text-slate-600">
                              <div>
                                <span className="font-bold text-slate-400">
                                  共享密钥 (Secret Key):
                                </span>{" "}
                                {testResultSummary.secretKey}
                              </div>
                              <div>
                                <span className="font-bold text-slate-400">
                                  测试时间戳 (Timestamp Header):
                                </span>{" "}
                                {testResultSummary.timestamp}
                              </div>
                              <div>
                                <span className="font-bold text-slate-400">
                                  串联签名原文字段 (Timestamp + Payload):
                                </span>
                              </div>
                              <div className="bg-slate-50 p-2 rounded border border-slate-100 text-[9px] overflow-auto select-all max-h-20">
                                {testResultSummary.payloadSigned}
                              </div>
                              <div>
                                <span className="font-bold text-slate-400">
                                  生成签名哈希结果 (X-MedKernel-Signature):
                                </span>
                              </div>
                              <Tag
                                color="cyan"
                                className="text-[10px] font-normal select-all w-fit py-0.5 px-2"
                              >
                                {testResultSummary.signature}
                              </Tag>
                            </div>
                            <Alert
                              type="success"
                              showIcon
                              message="防伪签名验证成功"
                              description="经 HMAC-SHA256 算法对称哈希校验，接收端与发送端数据内容指纹 100% 对齐。防篡改、防重放保护防御已生效！"
                              className="mt-1 rounded-lg text-[10px] py-1"
                            />
                          </div>
                        )}
                      </Space>
                    </Card>
                  </Col>
                </Row>
              ),
            },

            {
              key: "logs",
              label: (
                <span className="flex items-center gap-1.5 text-xs">
                  <ReloadOutlined />
                  <span>重试死信与接口存证队列</span>
                </span>
              ),
              children: (
                <div className="flex flex-col gap-4">
                  <div className="flex justify-between items-center">
                    <Alert
                      type="warning"
                      showIcon
                      message="死信队列与失败重试规则"
                      description="数据总线交换发生失败时，最多自动重试投递 3 次。超限仍失败将降级挂起归档为 DEAD_LETTER。支持手动补录后一键重试或删除项。"
                      className="w-3/4 rounded-lg text-xs"
                    />
                    <Button
                      icon={<FileProtectOutlined />}
                      onClick={handleExportLogsCertificate}
                      className="hover:border-sky-500 hover:text-sky-500"
                    >
                      导出接口数据交换存证
                    </Button>
                  </div>

                  <Table
                    dataSource={displayLogs.map((l) => ({ ...l, key: l.messageId }))}
                    loading={loadingLogs}
                    pagination={{
                      current: logPage,
                      pageSize: 5,
                      total: displayLogsTotal,
                      onChange: (p) => setLogPage(p),
                    }}
                    columns={[
                      {
                        title: "消息ID/追踪 traceId",
                        key: "messageId",
                        render: (_, record) => (
                          <div className="flex flex-col">
                            <span className="font-semibold text-slate-800 text-xs">
                              {record.messageId}
                            </span>
                            <span className="text-[10px] text-slate-400 font-normal">
                              {record.traceId}
                            </span>
                          </div>
                        ),
                      },
                      {
                        title: "业务流向",
                        dataIndex: "direction",
                        key: "direction",
                        render: (dir) => (
                          <Tag color={dir === "INBOUND" ? "blue" : "purple"}>
                            {dir === "INBOUND" ? "← Inbound (接收)" : "→ Outbound (发送)"}
                          </Tag>
                        ),
                      },
                      {
                        title: "对接系统",
                        dataIndex: "systemName",
                        key: "systemName",
                        render: (name) => (
                          <span className="font-medium text-xs text-slate-700">{name}</span>
                        ),
                      },
                      {
                        title: "报文摘要",
                        dataIndex: "payloadSummary",
                        key: "payloadSummary",
                        className: "font-normal text-xs",
                      },
                      {
                        title: "重试控制门槛",
                        key: "retry",
                        render: (_, record) => (
                          <span className="font-normal text-xs">
                            {record.retryCount} / {record.maxRetries} 次
                          </span>
                        ),
                      },
                      {
                        title: "总线诊断结果",
                        key: "status",
                        render: (_, record) => {
                          let color = "green";
                          if (record.status === "FAILED") color = "orange";
                          if (record.status === "DEAD_LETTER") color = "red";
                          return (
                            <Tag color={color} className="font-normal">
                              {record.status}
                            </Tag>
                          );
                        },
                      },
                      {
                        title: "失败最近故障诊断",
                        dataIndex: "errorMessage",
                        key: "errorMessage",
                        render: (err) => (
                          <span
                            className="text-[10px] text-red-500 font-sans block max-w-[200px] truncate"
                            title={err || ""}
                          >
                            {err || "—"}
                          </span>
                        ),
                      },
                      {
                        title: "存证操作项",
                        key: "actions",
                        render: (_, record) => {
                          const isSuccess = record.status === "SUCCESS";
                          return (
                            <Space size="middle">
                              <Button
                                size="small"
                                type="primary"
                                icon={<ReloadOutlined />}
                                disabled={isSuccess}
                                onClick={() => handleRetryMessage(record.messageId)}
                                className="bg-sky-600 border-sky-600 hover:bg-sky-700 text-xs rounded-md"
                              >
                                重试
                              </Button>
                              <Button
                                size="small"
                                danger
                                icon={<DeleteOutlined />}
                                onClick={() => handleDeleteMessage(record.messageId)}
                              >
                                已补
                              </Button>
                            </Space>
                          );
                        },
                      },
                    ]}
                  />
                </div>
              ),
            },

            {
              key: "sandbox",
              label: (
                <span className="flex items-center gap-1.5 text-xs">
                  <PlayCircleOutlined />
                  <span>HIS/EMR 免登嵌入沙箱 (Launch Token)</span>
                </span>
              ),
              children: (
                <Row gutter={16}>
                  {/* 左侧配置 */}
                  <Col span={9}>
                    <Card
                      title={
                        <span className="text-xs font-semibold">
                          一次性免登嵌入 Launch Token 发生器
                        </span>
                      }
                      className="rounded-xl border-slate-200 bg-slate-50"
                      size="small"
                    >
                      <Form form={tokenForm} layout="vertical">
                        <Row gutter={12}>
                          <Col span={12}>
                            <Form.Item
                              name="userId"
                              label="医生工号 (User ID)"
                              rules={[{ required: true }]}
                              initialValue="doc-chao-009"
                            >
                              <Input className="rounded-lg text-xs" />
                            </Form.Item>
                          </Col>
                          <Col span={12}>
                            <Form.Item
                              name="roleCode"
                              label="岗位角色"
                              rules={[{ required: true }]}
                              initialValue="PHYSICIAN"
                            >
                              <Select className="rounded-lg text-xs">
                                <Option value="PHYSICIAN">临床医生</Option>
                                <Option value="NURSE">临床护士</Option>
                              </Select>
                            </Form.Item>
                          </Col>
                        </Row>

                        <Row gutter={12}>
                          <Col span={12}>
                            <Form.Item
                              name="patientId"
                              label="测试目标患者 ID"
                              rules={[{ required: true }]}
                            >
                              <Input placeholder="输入真实患者 ID" className="rounded-lg text-xs" />
                            </Form.Item>
                          </Col>
                          <Col span={12}>
                            <Form.Item
                              name="encounterId"
                              label="就诊流水号"
                              rules={[{ required: true }]}
                            >
                              <Input
                                placeholder="输入真实就诊流水号"
                                className="rounded-lg text-xs font-normal"
                              />
                            </Form.Item>
                          </Col>
                        </Row>

                        <Form.Item
                          name="triggerPoint"
                          label="页面嵌入触发时机"
                          rules={[{ required: true }]}
                          initialValue="OUTPATIENT_DIAGNOSIS"
                        >
                          <Select className="rounded-lg text-xs">
                            <Option value="OUTPATIENT_DIAGNOSIS">
                              门诊诊断下达时 (OUTPATIENT_DIAGNOSIS)
                            </Option>
                            <Option value="ADMISSION_CHECK">
                              患者办完住院登记时 (ADMISSION_CHECK)
                            </Option>
                            <Option value="DISCHARGE_PLAN">
                              开具出院小结随访时 (DISCHARGE_PLAN)
                            </Option>
                          </Select>
                        </Form.Item>

                        <Button
                          type="primary"
                          onClick={handleGenerateToken}
                          icon={<PlayCircleOutlined />}
                          className="bg-sky-600 border-sky-600 hover:bg-sky-700 rounded-md w-full text-xs"
                        >
                          生成 60s 令牌并发射集成 iframe
                        </Button>
                      </Form>
                    </Card>

                    {/* 跨域 Origin 域名管理 */}
                    <Card
                      title={
                        <div className="flex justify-between items-center w-full">
                          <span className="text-xs font-semibold">跨域 Origin 安全防卫</span>
                          <Button
                            type="link"
                            size="small"
                            icon={<PlusOutlined />}
                            onClick={() => setOriginFormVisible(true)}
                            className="text-xs"
                          >
                            安全防卫
                          </Button>
                        </div>
                      }
                      className="rounded-xl border-slate-200 mt-4"
                      size="small"
                    >
                      <Table
                        dataSource={displayOrigins.map((orig, i) => ({ key: i, origin: orig }))}
                        size="small"
                        pagination={false}
                        columns={[
                          {
                            title: "允许的跨域 Origin 域名地址",
                            dataIndex: "origin",
                            key: "origin",
                            className: "font-normal text-[10px] text-slate-700",
                          },
                          {
                            title: "防护状态",
                            key: "status",
                            render: () => (
                              <Tag color="green" className="text-[9px]">
                                防护中
                              </Tag>
                            ),
                          },
                        ]}
                      />
                    </Card>
                  </Col>

                  {/* 右侧沙箱展示 */}
                  <Col span={15}>
                    {sandboxVisible ? (
                      <Card
                        title={
                          <span className="flex items-center gap-1.5 text-xs text-amber-600 font-semibold">
                            <LockOutlined />
                            <span>核心住院医生工作站 (HIS) 仿真沙箱界面</span>
                          </span>
                        }
                        className="border-amber-200 rounded-xl"
                        size="small"
                      >
                        <Row gutter={16}>
                          <Col span={12}>
                            <Card
                              className="bg-amber-50/30 border-amber-100 mb-4 rounded-xl"
                              size="small"
                            >
                              <span className="font-bold text-xs text-slate-700 block mb-2">
                                医生工作站处方开立区:
                              </span>
                              <List
                                size="small"
                                dataSource={hisOrders}
                                renderItem={(item) => (
                                  <List.Item className="py-1 px-0 text-[10px] text-slate-600">
                                    {item}
                                  </List.Item>
                                )}
                              />
                            </Card>
                          </Col>

                          <Col span={12}>
                            <Card
                              className="bg-slate-900 border-slate-800 text-slate-200 mb-4 rounded-xl"
                              size="small"
                            >
                              <span className="font-bold text-xs text-slate-400 block mb-2">
                                postMessage 双向跨域通信审计{" "}
                                {sandboxToken ? `(Token: ${sandboxToken})` : ""}:
                              </span>
                              <div className="max-h-24 overflow-y-auto font-normal text-[9px]">
                                {postMessageLogs.length === 0 ? (
                                  <span className="text-slate-500">
                                    等待 CDSS 交互反馈指令事件回传...
                                  </span>
                                ) : (
                                  postMessageLogs.map((log, i) => (
                                    <div key={i} className="mb-1 border-b border-slate-800 pb-1">
                                      <div>[接收事件] action={log.action}</div>
                                      <div className="text-[8px] text-slate-400">
                                        payload: {JSON.stringify(log)}
                                      </div>
                                    </div>
                                  ))
                                )}
                              </div>
                            </Card>
                          </Col>
                        </Row>

                        <iframe
                          src={sandboxUrl}
                          title="HIS CDSS Sandbox"
                          className="w-full h-80 rounded-xl border border-slate-200 shadow"
                        />
                      </Card>
                    ) : (
                      <Card className="bg-slate-50 border-slate-200 rounded-xl h-full min-h-[400px] flex items-center justify-center">
                        <span className="text-slate-400 text-xs text-center block">
                          请先在左侧输入就诊环境参数，
                          <br />
                          并点击“生成 60s 令牌”来拉起 HIS 嵌入仿真沙盒。
                        </span>
                      </Card>
                    )}
                  </Col>
                </Row>
              ),
            },
          ]}
        />
      </Card>

      {/* 新建适配器 Modal */}
      <Modal
        title="在线配置异构接入适配器 (JPA / SOAP / REST / FHIR)"
        open={adapterModalVisible}
        onOk={handleCreateAdapter}
        onCancel={() => setAdapterModalVisible(false)}
        okText="确认创建"
        cancelText="取消"
        className="rounded-2xl"
      >
        <Form form={adapterForm} layout="vertical" className="mt-4">
          <Form.Item
            name="adapterId"
            label="适配器系统唯一标识 (Adapter ID)"
            rules={[{ required: true, message: "请输入适配器系统标识" }]}
          >
            <Input placeholder="请输入真实适配器系统标识" className="rounded-lg" />
          </Form.Item>

          <Form.Item
            name="name"
            label="系统对接中文名称"
            rules={[{ required: true, message: "请输入中文名称" }]}
          >
            <Input placeholder="请输入真实系统对接名称" className="rounded-lg" />
          </Form.Item>

          <Form.Item
            name="protocolType"
            label="接入协议通信类型"
            rules={[{ required: true }]}
            initialValue="REST"
          >
            <Select className="rounded-lg">
              <Option value="HL7">HL7 / SOAP 协议</Option>
              <Option value="FHIR">HL7 FHIR 标准资源</Option>
              <Option value="Webhook">Webhook 回调拉取</Option>
              <Option value="REST">REST API 主动检索</Option>
              <Option value="WebService">WebService 传统接口</Option>
            </Select>
          </Form.Item>

          <Form.Item name="configJson" label="连接字段映射规则配制 JSON (Config JSON)">
            <Input.TextArea
              rows={4}
              placeholder="请输入真实连接配置 JSON"
              className="font-normal text-xs rounded-lg"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 新建 Webhook Modal */}
      <Modal
        title="新增外部 Webhook 事件回调订阅"
        open={webhookModalVisible}
        onOk={handleCreateWebhook}
        onCancel={() => setWebhookModalVisible(false)}
        okText="确认订阅"
        cancelText="取消"
        className="rounded-2xl"
      >
        <Form form={webhookForm} layout="vertical" className="mt-4">
          <Form.Item
            name="webhookId"
            label="Webhook 订阅唯一标识"
            rules={[{ required: true, message: "请输入订阅标识" }]}
          >
            <Input placeholder="请输入真实订阅标识" className="rounded-lg" />
          </Form.Item>

          <Form.Item
            name="name"
            label="订阅通道中文名称"
            rules={[{ required: true, message: "请输入订阅中文名称" }]}
          >
            <Input placeholder="请输入真实订阅通道名称" className="rounded-lg" />
          </Form.Item>

          <Form.Item
            name="callbackUrl"
            label="第三方回调 URL (Callback URL)"
            rules={[
              { required: true, message: "请输入回调 URL" },
              {
                pattern: /^https?:\/\/.*$/,
                message: "请输入以 http:// 或 https:// 开头的合法地址",
              },
            ]}
          >
            <Input placeholder="请输入真实回调地址" className="rounded-lg font-normal" />
          </Form.Item>

          <Form.Item
            name="eventsSubscribed"
            label="订阅的临床场景触发点 (Events Subscribed)"
            rules={[{ required: true }]}
            initialValue="OUTPATIENT_DIAGNOSIS"
          >
            <Select className="rounded-lg">
              <Option value="OUTPATIENT_DIAGNOSIS">门诊诊断下达时 (OUTPATIENT_DIAGNOSIS)</Option>
              <Option value="ADMISSION_CHECK">患者办完住院登记时 (ADMISSION_CHECK)</Option>
              <Option value="DISCHARGE_PLAN">开具出院小结随访时 (DISCHARGE_PLAN)</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* 跨域 Origin 域名添加 Modal */}
      <Modal
        title="添加跨域安全域名防卫"
        open={originFormVisible}
        onOk={handleAddOrigin}
        onCancel={() => setOriginFormVisible(false)}
        okText="加入防护"
        cancelText="取消"
        className="rounded-2xl"
      >
        <Form form={originForm} layout="vertical" className="mt-4">
          <Form.Item
            name="origin"
            label="允许跨域的 Origin 域名地址"
            rules={[
              { required: true, message: "域名地址必填" },
              {
                pattern: /^https?:\/\/[a-zA-Z0-9.-]+(:\d+)?$/,
                message: "请输入合法的 http(s)://origin 格式，且末尾不带斜杠",
              },
            ]}
            initialValue="http://test.hospital.local:9000"
          >
            <Input
              placeholder="如 http://his-system.local:8080"
              className="rounded-lg font-normal"
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
}
