import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import {
  ApiOutlined,
  MinusCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StopOutlined,
} from "@ant-design/icons";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useRuntimeDiagnosticsApiContracts,
  useDisablePlugin,
  useGrantPlugin,
  usePlugins,
  useRegisterPlugin,
  useRuntimeOperations,
  useSecurityProfile,
  useSystemRuntime,
  useTraceDiagnosis,
} from "@/shared/api/hooks";
import type {
  PluginCapabilityType,
  PluginItem,
  RuntimeDiagnosticsApiContract,
  RuntimeDependencyStatus,
  TracePayloadSummary,
  TraceStateTransition,
} from "@/shared/api/hooks";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { formatClinicalDateTimeWithSeconds } from "@/shared/lib/dateTimeText";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

import styles from "../advanced/Advanced.module.css";

const { Text } = Typography;
const { Search } = Input;

const STATUS_LABEL: Record<string, string> = {
  UP: "正常",
  DEGRADED: "降级",
  NOT_CONNECTED: "未连接",
  MODEL_DISABLED: "模型未启用",
  DOWN: "异常",
  OUT_OF_SERVICE: "停服",
  UNKNOWN: "未知",
};

const STATUS_COLOR: Record<string, string> = {
  UP: "success",
  DEGRADED: "warning",
  NOT_CONNECTED: "default",
  MODEL_DISABLED: "default",
  DOWN: "error",
  OUT_OF_SERVICE: "error",
  UNKNOWN: "default",
};

const PLUGIN_STATUS_LABEL: Record<string, string> = {
  PENDING_REVIEW: "待审核",
  AUTHORIZED: "已授权",
  DISABLED: "已禁用",
};

const PLUGIN_STATUS_COLOR: Record<string, string> = {
  PENDING_REVIEW: "warning",
  AUTHORIZED: "success",
  DISABLED: "default",
};

const CAPABILITY_TYPE_LABEL: Record<PluginCapabilityType, string> = {
  READ: "读取",
  EXECUTE: "执行",
  WRITE: "写入",
};

const DEPLOYMENT_MODE_LABEL: Record<string, string> = {
  "docker-core": "容器化部署",
  docker: "容器化部署",
  local: "本地部署",
  kubernetes: "集群部署",
};

const DATABASE_DIALECT_LABEL: Record<string, string> = {
  postgres: "PostgreSQL",
  mysql: "MySQL",
  dm: "达梦",
  kingbase: "人大金仓",
  oracle: "Oracle",
};

const TRACE_STATUS_LABEL: Record<string, string> = {
  PENDING: "待处理",
  RUNNING: "处理中",
  SUCCEEDED: "成功",
  SUCCESS: "成功",
  FAILED: "失败",
  CANCELED: "已取消",
};

interface RegisterPluginFormValues {
  pluginCode: string;
  displayName: string;
  capabilities: Array<{
    capabilityKey: string;
    capabilityType: PluginCapabilityType;
    serviceContractId: string;
    clinicalData: boolean;
  }>;
}

interface GrantPluginFormValues {
  capabilityKeys: string[];
  authorizationReason: string;
  clinicalSafetyConfirmed: boolean;
}

function runtimeText(snapshot: Record<string, unknown> | undefined, keys: string[]): string | null {
  if (!snapshot) {
    return null;
  }
  for (const key of keys) {
    const value = snapshot[key];
    if (typeof value === "string" && value.trim()) {
      return value;
    }
    if (Array.isArray(value) && value.length > 0) {
      return value.map(String).join(" / ");
    }
  }
  return null;
}

function formatTime(value?: string | null) {
  if (!value) return "-";
  return formatClinicalDateTimeWithSeconds(value, value);
}

function deploymentModeText(value: string, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return value;
  return DEPLOYMENT_MODE_LABEL[value] ?? "已配置部署模式";
}

function databaseDialectText(value: string, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return value;
  return DATABASE_DIALECT_LABEL[value] ?? "已配置关系数据库";
}

function dependencyDetail(record: RuntimeDependencyStatus, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return record.detail;
  if (record.key === "database") {
    return record.status === "UP" ? "核心数据服务可用" : "核心数据服务需关注";
  }
  return record.detail;
}

function contractServiceText(
  record: RuntimeDiagnosticsApiContract,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) {
    return (
      <Space direction="vertical" size={0}>
        <Text strong>{record.title}</Text>
        <Text type="secondary">{record.id}</Text>
      </Space>
    );
  }
  return <Text strong>{record.title}</Text>;
}

function contractBasePathText(
  record: RuntimeDiagnosticsApiContract,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) return record.basePath;
  return record.publicEndpoints.length > 0 ? "含公开端点" : "登录后按权限访问";
}

function permissionText(
  permission: RuntimeDiagnosticsApiContract["permissions"][number],
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) return permission.code;
  return permission.purpose || customerEnumLabel(permission.code);
}

function traceStatusText(value: string | null | undefined, evidenceDetailsEnabled: boolean) {
  if (!value) return "-";
  if (evidenceDetailsEnabled) return value;
  return TRACE_STATUS_LABEL[value] ?? STATUS_LABEL[value] ?? customerEnumLabel(value);
}

function traceActorText(value: string | null | undefined, evidenceDetailsEnabled: boolean) {
  if (!value) return "系统执行";
  return evidenceDetailsEnabled ? value : "已记录执行人";
}

function tracePayloadDigestText(value: string, evidenceDetailsEnabled: boolean) {
  return evidenceDetailsEnabled ? value : "输入摘要已登记";
}

function pluginCapabilityText(
  capability: PluginItem["capabilities"][number],
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) return capability.capabilityKey;
  return `${capability.serviceContractTitle} · ${CAPABILITY_TYPE_LABEL[capability.capabilityType]}`;
}

export default function RuntimeDiagnostics() {
  const { message, modal } = App.useApp();
  const security = useSecurityProfile();
  const systemRuntime = useSystemRuntime();
  const runtime = useRuntimeOperations();
  const apiContracts = useRuntimeDiagnosticsApiContracts();
  const plugins = usePlugins();
  const registerPlugin = useRegisterPlugin();
  const grantPlugin = useGrantPlugin();
  const disablePlugin = useDisablePlugin();
  const [contractKeyword, setContractKeyword] = useState("");
  const [traceId, setTraceId] = useState("");
  const [submittedTraceId, setSubmittedTraceId] = useState("");
  const trace = useTraceDiagnosis(submittedTraceId, Boolean(submittedTraceId));
  const [registerOpen, setRegisterOpen] = useState(false);
  const [grantTarget, setGrantTarget] = useState<PluginItem | null>(null);
  const [registerForm] = Form.useForm<RegisterPluginFormValues>();
  const [grantForm] = Form.useForm<GrantPluginFormValues>();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;

  const contractOptions = useMemo(
    () =>
      (apiContracts.data?.contracts ?? []).map((contract) => ({
        value: contract.id,
        label: `${contract.title} · ${contract.id}`,
      })),
    [apiContracts.data],
  );

  const filteredContracts = useMemo(() => {
    const keyword = contractKeyword.trim().toLowerCase();
    const contracts = apiContracts.data?.contracts ?? [];
    if (!keyword) return contracts;
    return contracts.filter((contract) =>
      [
        contract.id,
        contract.title,
        contract.basePath,
        ...contract.permissions.map((permission) => permission.code),
      ].some((value) => value.toLowerCase().includes(keyword)),
    );
  }, [apiContracts.data, contractKeyword]);

  if (systemRuntime.isLoading || runtime.isLoading) {
    return (
      <PageShell title="运行诊断" description="正在读取系统运行摘要">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (systemRuntime.isError || runtime.isError) {
    return (
      <PageShell title="运行诊断" description="系统运行摘要读取失败">
        <PageState
          state="error"
          title="暂时无法读取运行诊断"
          description="请稍后重试，或让信息科检查系统运行服务。"
          action={
            <Button
              icon={<ReloadOutlined />}
              onClick={() => void Promise.all([systemRuntime.refetch(), runtime.refetch()])}
            >
              重读运行摘要
            </Button>
          }
        />
      </PageShell>
    );
  }

  const operations = runtime.data;
  const rawRuntime = systemRuntime.data;
  if (!operations || !rawRuntime) {
    return (
      <PageShell title="运行诊断" description="系统运行摘要暂无数据">
        <PageState state="empty" title="暂无系统运行摘要" />
      </PageShell>
    );
  }

  const serviceName =
    runtimeText(rawRuntime, ["service", "serviceName", "name"]) ?? operations.serviceName;
  const runtimeValue = runtimeText(rawRuntime, ["runtime", "javaVersion", "jdk"]) ?? "未返回";
  const version = runtimeText(rawRuntime, ["version", "buildVersion", "commit"]) ?? "未返回";

  const submitRegister = async (values: RegisterPluginFormValues) => {
    try {
      await registerPlugin.mutateAsync({
        pluginCode: values.pluginCode.trim(),
        displayName: values.displayName.trim(),
        capabilities: values.capabilities.map((capability) => ({
          capabilityKey: capability.capabilityKey.trim(),
          capabilityType: capability.capabilityType,
          serviceContractId: capability.serviceContractId,
          clinicalData: Boolean(capability.clinicalData),
        })),
      });
      message.success("插件已登记，等待授权");
      registerForm.resetFields();
      setRegisterOpen(false);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "插件登记失败"));
    }
  };

  const submitGrant = async (values: GrantPluginFormValues) => {
    if (!grantTarget) return;
    try {
      await grantPlugin.mutateAsync({
        pluginId: grantTarget.pluginId,
        capabilityKeys: values.capabilityKeys,
        authorizationReason: values.authorizationReason?.trim() ?? "",
        clinicalSafetyConfirmed: Boolean(values.clinicalSafetyConfirmed),
      });
      message.success("插件能力已授权");
      grantForm.resetFields();
      setGrantTarget(null);
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "插件授权失败"));
    }
  };

  const confirmDisable = (plugin: PluginItem) => {
    modal.confirm({
      title: `禁用 ${plugin.displayName}`,
      content: "禁用后插件不能继续获得或使用能力授权。",
      okText: "确认禁用",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        try {
          await disablePlugin.mutateAsync(plugin.pluginId);
          message.success("插件已禁用");
        } catch (error: unknown) {
          message.error(getApiErrorMessage(error, "插件禁用失败"));
          throw error;
        }
      },
    });
  };

  let apiDirectory: ReactNode;
  if (apiContracts.isLoading) {
    apiDirectory = <PageState state="loading" />;
  } else if (apiContracts.isError) {
    apiDirectory = (
      <PageState
        state="error"
        title="服务目录读取失败"
        action={
          <Button icon={<ReloadOutlined />} onClick={() => apiContracts.refetch()}>
            重试
          </Button>
        }
      />
    );
  } else {
    apiDirectory = (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="搜索服务、路径或权限"
          value={contractKeyword}
          onChange={(event) => setContractKeyword(event.target.value)}
        />
        <Table<RuntimeDiagnosticsApiContract>
          rowKey="id"
          dataSource={filteredContracts}
          pagination={{ pageSize: 10, showSizeChanger: false }}
          scroll={{ x: "max-content" }}
          columns={[
            {
              title: "服务",
              render: (_, record) => contractServiceText(record, evidenceDetailsEnabled),
            },
            {
              title: "访问边界",
              render: (_, record) => contractBasePathText(record, evidenceDetailsEnabled),
            },
            {
              title: "版本",
              render: (_, record) => <Tag>{record.contractVersion ?? "v1"}</Tag>,
            },
            {
              title: "权限",
              render: (_, record) =>
                record.permissions.length ? (
                  <Space size={[4, 4]} wrap>
                    {record.permissions.map((permission) => (
                      <Tag key={permission.code}>
                        {permissionText(permission, evidenceDetailsEnabled)}
                      </Tag>
                    ))}
                  </Space>
                ) : (
                  <Text type="secondary">公开或登录态能力</Text>
                ),
            },
            {
              title: "审计点",
              render: (_, record) => record.auditPoints.length,
            },
            {
              title: "文档",
              render: (_, record) =>
                record.openApiDocumentUrl || record.fieldContractUrl ? (
                  <Space size={4}>
                    {record.openApiDocumentUrl ? (
                      <Button
                        type="link"
                        size="small"
                        icon={<ApiOutlined />}
                        aria-label="服务契约"
                        href={record.openApiDocumentUrl}
                        target="_blank"
                      >
                        服务契约
                      </Button>
                    ) : null}
                    {record.fieldContractUrl ? (
                      <Text copyable={{ text: record.fieldContractUrl }}>字段契约</Text>
                    ) : null}
                  </Space>
                ) : (
                  <Text type="secondary">-</Text>
                ),
            },
          ]}
        />
      </Space>
    );
  }

  let traceContent: ReactNode = null;
  if (!submittedTraceId) {
    traceContent = <PageState state="empty" title="尚未查询追踪号" />;
  } else if (trace.isLoading) {
    traceContent = <PageState state="loading" />;
  } else if (trace.isError) {
    traceContent = <PageState state="error" title="未找到该追踪号或无权查看" />;
  } else if (trace.data) {
    traceContent = (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
          <Descriptions.Item label="追踪号">{trace.data.traceId}</Descriptions.Item>
          <Descriptions.Item label="耗时">
            {trace.data.durationMs === null || trace.data.durationMs === undefined
              ? "-"
              : `${trace.data.durationMs} ms`}
          </Descriptions.Item>
          <Descriptions.Item label="开始">{formatTime(trace.data.startedAt)}</Descriptions.Item>
          <Descriptions.Item label="结束">{formatTime(trace.data.endedAt)}</Descriptions.Item>
        </Descriptions>
        <Table<TraceStateTransition>
          rowKey={(record) =>
            [
              record.traceId,
              record.occurredAt,
              record.fromStatus,
              record.toStatus,
              record.actor,
              record.reason,
            ]
              .map((value) => value ?? "")
              .join("|")
          }
          dataSource={trace.data.stateHistory}
          pagination={false}
          locale={{ emptyText: "无状态流转记录" }}
          scroll={{ x: "max-content" }}
          columns={[
            {
              title: "状态",
              render: (_, record) =>
                `${traceStatusText(record.fromStatus, evidenceDetailsEnabled)} → ${traceStatusText(
                  record.toStatus,
                  evidenceDetailsEnabled,
                )}`,
            },
            { title: "原因", dataIndex: "reason" },
            {
              title: "执行责任",
              render: (_, record) => traceActorText(record.actor, evidenceDetailsEnabled),
            },
            {
              title: "时间",
              dataIndex: "occurredAt",
              render: (value) => formatTime(value),
            },
            {
              title: "错误",
              render: (_, record) =>
                record.error ? (
                  <Tag color="error">{record.error.errorCode ?? record.error.errorClass}</Tag>
                ) : (
                  "-"
                ),
            },
          ]}
        />
        <Table<TracePayloadSummary>
          rowKey={(record) => record.digest}
          dataSource={trace.data.payloads}
          pagination={false}
          locale={{ emptyText: "无输入内容摘要" }}
          scroll={{ x: "max-content" }}
          columns={[
            {
              title: "摘要",
              dataIndex: "digest",
              render: (value: string) => tracePayloadDigestText(value, evidenceDetailsEnabled),
            },
            { title: "内容类型", dataIndex: "contentType" },
            { title: "存储", dataIndex: "storageType" },
            {
              title: "大小",
              dataIndex: "sizeBytes",
              render: (value) => `${value} B`,
            },
          ]}
        />
      </Space>
    );
  }

  const traceDiagnosis = (
    <Space direction="vertical" size="middle" className="mk-full-width">
      <Search
        enterButton={<SearchOutlined />}
        placeholder="输入 追踪号"
        value={traceId}
        onChange={(event) => setTraceId(event.target.value)}
        onSearch={(value) => setSubmittedTraceId(value.trim())}
      />
      {traceContent}
    </Space>
  );

  let pluginManagement: ReactNode;
  if (plugins.isLoading) {
    pluginManagement = <PageState state="loading" />;
  } else if (plugins.isError) {
    pluginManagement = (
      <PageState
        state="error"
        title="插件列表读取失败"
        action={
          <Button icon={<ReloadOutlined />} onClick={() => plugins.refetch()}>
            重试
          </Button>
        }
      />
    );
  } else {
    pluginManagement = (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Space className={styles.actionsRight}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!contractOptions.length}
            onClick={() => setRegisterOpen(true)}
          >
            注册插件
          </Button>
        </Space>
        <Table<PluginItem>
          rowKey="pluginId"
          dataSource={plugins.data?.items ?? []}
          pagination={false}
          scroll={{ x: "max-content" }}
          locale={{ emptyText: "暂无插件" }}
          columns={[
            {
              title: "插件",
              render: (_, record) => (
                <Space direction="vertical" size={0}>
                  <Text strong>{record.displayName}</Text>
                  {evidenceDetailsEnabled ? (
                    <Text type="secondary">{record.pluginCode}</Text>
                  ) : null}
                </Space>
              ),
            },
            {
              title: "状态",
              dataIndex: "status",
              render: (status) => (
                <Tag color={PLUGIN_STATUS_COLOR[status] ?? "default"}>
                  {PLUGIN_STATUS_LABEL[status] ?? customerEnumLabel(status)}
                </Tag>
              ),
            },
            {
              title: "边界",
              dataIndex: "authorityBoundary",
              render: (boundary) => (
                <Tag color={boundary === "CONTROLLED_WRITE" ? "warning" : "default"}>
                  {boundary === "CONTROLLED_WRITE" ? "受控写入" : "只读优先"}
                </Tag>
              ),
            },
            {
              title: "能力",
              render: (_, record) => (
                <Space size={[4, 4]} wrap>
                  {record.capabilities.map((capability) => (
                    <Tag
                      key={capability.capabilityKey}
                      color={capability.capabilityType === "WRITE" ? "warning" : "default"}
                    >
                      {pluginCapabilityText(capability, evidenceDetailsEnabled)}
                    </Tag>
                  ))}
                </Space>
              ),
            },
            {
              title: "操作",
              fixed: "right",
              render: (_, record) => (
                <Space>
                  <Button
                    type="link"
                    icon={<SafetyCertificateOutlined />}
                    disabled={record.status === "DISABLED"}
                    onClick={() => setGrantTarget(record)}
                  >
                    授权
                  </Button>
                  <Button
                    type="link"
                    danger
                    icon={<StopOutlined />}
                    disabled={record.status === "DISABLED"}
                    onClick={() => confirmDisable(record)}
                  >
                    禁用
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </Space>
    );
  }

  return (
    <PageShell
      title="运行诊断"
      description="服务契约、追踪诊断与插件边界"
      extras={
        <Space wrap>
          <EvidenceDetailsToggle securityProfile={security.data} />
          <Button
            icon={<ReloadOutlined />}
            onClick={() =>
              void Promise.all([
                security.refetch(),
                systemRuntime.refetch(),
                runtime.refetch(),
                apiContracts.refetch(),
                plugins.refetch(),
              ])
            }
          >
            刷新
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="服务" value={serviceName} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="健康"
                value={
                  STATUS_LABEL[operations.healthStatus] ??
                  customerEnumLabel(operations.healthStatus)
                }
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="部署模式"
                value={deploymentModeText(operations.deploymentMode, evidenceDetailsEnabled)}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="版本" value={version} />
            </Card>
          </Col>
        </Row>

        <Card title="系统运行概况">
          <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="服务名">{serviceName}</Descriptions.Item>
            <Descriptions.Item label="运行时">{runtimeValue}</Descriptions.Item>
            <Descriptions.Item label="数据库">
              {databaseDialectText(operations.databaseDialect, evidenceDetailsEnabled)}
            </Descriptions.Item>
            {evidenceDetailsEnabled ? (
              <Descriptions.Item label="迁移路径">{operations.migrationLocation}</Descriptions.Item>
            ) : null}
          </Descriptions>
        </Card>

        <Card data-testid="runtime-dependencies">
          <Table<RuntimeDependencyStatus>
            rowKey="key"
            dataSource={operations.dependencies}
            pagination={false}
            scroll={{ x: "max-content" }}
            columns={[
              { title: "依赖", dataIndex: "displayName" },
              {
                title: "状态",
                dataIndex: "status",
                render: (status) => (
                  <Tag color={STATUS_COLOR[status] ?? "default"}>
                    {STATUS_LABEL[status] ?? customerEnumLabel(status)}
                  </Tag>
                ),
              },
              {
                title: "说明",
                render: (_, record) => dependencyDetail(record, evidenceDetailsEnabled),
              },
            ]}
          />
        </Card>

        <Card>
          <Tabs
            items={[
              {
                key: "api",
                label: (
                  <Space size={6}>
                    <ApiOutlined />
                    服务目录
                  </Space>
                ),
                children: apiDirectory,
              },
              { key: "trace", label: "追踪诊断", children: traceDiagnosis },
              { key: "plugins", label: "插件管理", children: pluginManagement },
            ]}
          />
        </Card>
      </Space>

      <Modal
        title="注册插件"
        open={registerOpen}
        width={760}
        okText="登记"
        cancelText="取消"
        confirmLoading={registerPlugin.isPending}
        onCancel={() => {
          registerForm.resetFields();
          setRegisterOpen(false);
        }}
        onOk={() => registerForm.submit()}
        destroyOnClose
      >
        <Form<RegisterPluginFormValues>
          form={registerForm}
          layout="vertical"
          initialValues={{
            capabilities: [
              {
                capabilityType: "READ",
                clinicalData: false,
              },
            ],
          }}
          onFinish={(values) => void submitRegister(values)}
        >
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                name="pluginCode"
                label="稳定插件能力身份"
                rules={[{ required: true, message: "请输入稳定插件能力身份" }]}
              >
                <Input maxLength={128} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                name="displayName"
                label="插件名称"
                rules={[{ required: true, message: "请输入插件名称" }]}
              >
                <Input maxLength={128} />
              </Form.Item>
            </Col>
          </Row>
          <Form.List name="capabilities">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" className="mk-full-width">
                {fields.map((field) => (
                  <Row gutter={12} key={field.key} align="middle">
                    <Col xs={24} md={7}>
                      <Form.Item
                        name={[field.name, "capabilityKey"]}
                        label="能力键"
                        rules={[{ required: true, message: "请输入能力键" }]}
                      >
                        <Input maxLength={128} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={5}>
                      <Form.Item
                        name={[field.name, "capabilityType"]}
                        label="类型"
                        rules={[{ required: true, message: "请选择类型" }]}
                      >
                        <Select
                          options={Object.entries(CAPABILITY_TYPE_LABEL).map(([value, label]) => ({
                            value,
                            label,
                          }))}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item
                        name={[field.name, "serviceContractId"]}
                        label="服务契约"
                        rules={[{ required: true, message: "请选择服务契约" }]}
                      >
                        <Select showSearch optionFilterProp="label" options={contractOptions} />
                      </Form.Item>
                    </Col>
                    <Col xs={20} md={3}>
                      <Form.Item
                        name={[field.name, "clinicalData"]}
                        label="临床数据"
                        valuePropName="checked"
                      >
                        <Checkbox />
                      </Form.Item>
                    </Col>
                    <Col xs={4} md={1}>
                      <Button
                        type="text"
                        danger
                        icon={<MinusCircleOutlined />}
                        aria-label="移除能力"
                        disabled={fields.length === 1}
                        onClick={() => remove(field.name)}
                      />
                    </Col>
                  </Row>
                ))}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add({ capabilityType: "READ", clinicalData: false })}
                >
                  添加能力
                </Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        title={grantTarget ? `授权 ${grantTarget.displayName}` : "插件授权"}
        open={Boolean(grantTarget)}
        okText="确认授权"
        cancelText="取消"
        confirmLoading={grantPlugin.isPending}
        onCancel={() => {
          grantForm.resetFields();
          setGrantTarget(null);
        }}
        onOk={() => grantForm.submit()}
        afterOpenChange={(open) => {
          if (open && grantTarget) {
            grantForm.setFieldsValue({
              capabilityKeys: grantTarget.capabilities.map(
                (capability) => capability.capabilityKey,
              ),
              authorizationReason: "",
              clinicalSafetyConfirmed: false,
            });
          }
        }}
        destroyOnClose
      >
        <Form<GrantPluginFormValues>
          form={grantForm}
          layout="vertical"
          onFinish={(values) => void submitGrant(values)}
        >
          <Form.Item
            name="capabilityKeys"
            label="授权能力"
            rules={[{ required: true, message: "请选择授权能力" }]}
          >
            <Select
              mode="multiple"
              options={(grantTarget?.capabilities ?? []).map((capability) => ({
                value: capability.capabilityKey,
                label: `${capability.capabilityKey} · ${
                  CAPABILITY_TYPE_LABEL[capability.capabilityType]
                }`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="authorizationReason"
            label="授权原因"
            rules={[
              {
                validator: (_, value) => {
                  const selected = grantForm.getFieldValue("capabilityKeys") ?? [];
                  const requiresReason = grantTarget?.capabilities.some(
                    (capability) =>
                      selected.includes(capability.capabilityKey) &&
                      capability.capabilityType === "WRITE",
                  );
                  return !requiresReason || value?.trim()
                    ? Promise.resolve()
                    : Promise.reject(new Error("写能力授权必须填写授权原因"));
                },
              },
            ]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
          <Form.Item
            name="clinicalSafetyConfirmed"
            valuePropName="checked"
            rules={[
              {
                validator: (_, value) => {
                  const selected = grantForm.getFieldValue("capabilityKeys") ?? [];
                  const requiresConfirmation = grantTarget?.capabilities.some(
                    (capability) =>
                      selected.includes(capability.capabilityKey) &&
                      capability.capabilityType === "WRITE" &&
                      capability.clinicalData,
                  );
                  return !requiresConfirmation || value
                    ? Promise.resolve()
                    : Promise.reject(new Error("临床数据写能力必须完成安全确认"));
                },
              },
            ]}
          >
            <Checkbox>已完成临床安全确认</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
}
