import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import { ReloadOutlined } from "@ant-design/icons";

import { useRuntimeOperations, useSystemRuntime } from "@/shared/api/hooks";
import type { RuntimeDependencyStatus, RuntimeFeatureFlag } from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

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

export default function DevConsole() {
  const systemRuntime = useSystemRuntime();
  const runtime = useRuntimeOperations();

  if (systemRuntime.isLoading || runtime.isLoading) {
    return (
      <PageShell title="开发者控制台" description="正在读取系统运行摘要">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (systemRuntime.isError || runtime.isError) {
    return (
      <PageShell title="开发者控制台" description="系统运行摘要读取失败">
        <PageState
          state="error"
          title="暂时无法读取开发者控制台"
          description="请稍后重试，或让 SRE 检查 /api/v1/system/runtime 与 /api/v1/system/operations。"
          action={
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => systemRuntime.refetch()}>
                重读运行摘要
              </Button>
              <Button onClick={() => runtime.refetch()}>重读运行底座</Button>
            </Space>
          }
        />
      </PageShell>
    );
  }

  const operations = runtime.data;
  const rawRuntime = systemRuntime.data;
  if (!operations || !rawRuntime) {
    return (
      <PageShell title="开发者控制台" description="系统运行摘要暂无数据">
        <PageState state="empty" title="暂无系统运行摘要" />
      </PageShell>
    );
  }

  const serviceName =
    runtimeText(rawRuntime, ["service", "serviceName", "name"]) ?? operations.serviceName;
  const runtimeValue = runtimeText(rawRuntime, ["runtime", "javaVersion", "jdk"]) ?? "未返回";
  const version = runtimeText(rawRuntime, ["version", "buildVersion", "commit"]) ?? "未返回";
  const runtimeProfiles =
    runtimeText(rawRuntime, ["activeProfiles", "profiles"]) ??
    operations.activeProfiles.join(" / ") ??
    "default";

  return (
    <PageShell title="开发者控制台" description="架构师 / 信息科主任 / SRE 可见的受控运行摘要">
      <Space direction="vertical" size="large" className="mk-full-width">
        <Alert
          type="info"
          showIcon
          message="仅展示受控诊断摘要"
          description="本页不暴露原始 JSON、密钥、连接串或患者数据；外部依赖未连接时按真实状态展示。"
        />

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
                value={STATUS_LABEL[operations.healthStatus] ?? operations.healthStatus}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="部署模式" value={operations.deploymentMode} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="数据库" value={operations.databaseDialect} />
            </Card>
          </Col>
        </Row>

        <Card title="系统运行快照">
          <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="服务名">{serviceName}</Descriptions.Item>
            <Descriptions.Item label="版本">{version}</Descriptions.Item>
            <Descriptions.Item label="运行时">{runtimeValue}</Descriptions.Item>
            <Descriptions.Item label="Profile">{runtimeProfiles}</Descriptions.Item>
            <Descriptions.Item label="迁移路径">{operations.migrationLocation}</Descriptions.Item>
            <Descriptions.Item label="生成时间">{operations.generatedAt}</Descriptions.Item>
          </Descriptions>
        </Card>

        <Card title="运行依赖" data-testid="developer-dependencies">
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
                    {STATUS_LABEL[status] ?? status}
                  </Tag>
                ),
              },
              { title: "说明", dataIndex: "detail" },
            ]}
          />
        </Card>

        <Card title="功能开关">
          <Table<RuntimeFeatureFlag>
            rowKey="key"
            dataSource={operations.featureFlags}
            pagination={false}
            scroll={{ x: "max-content" }}
            columns={[
              { title: "能力", dataIndex: "displayName" },
              {
                title: "状态",
                dataIndex: "enabled",
                render: (enabled) => (
                  <Tag color={enabled ? "success" : "default"}>{enabled ? "开启" : "关闭"}</Tag>
                ),
              },
              { title: "负责人", dataIndex: "owner" },
              {
                title: "说明",
                render: (_, record) => (
                  <Space direction="vertical" size={2}>
                    <Typography.Text>{record.description}</Typography.Text>
                    {record.warning ? (
                      <Typography.Text type="warning">{record.warning}</Typography.Text>
                    ) : null}
                  </Space>
                ),
              },
            ]}
          />
        </Card>
      </Space>
    </PageShell>
  );
}
