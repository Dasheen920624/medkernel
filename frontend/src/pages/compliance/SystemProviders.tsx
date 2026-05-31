import {
  Alert,
  Card,
  Row,
  Col,
  Tag,
  Space,
  Typography,
  Table,
  Statistic,
  Button,
  theme,
} from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { useRuntimeOperations } from "@/shared/api/hooks";
import type { RuntimeDependencyStatus, RuntimeFeatureFlag } from "@/shared/api/hooks";

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

const RISK_COLOR: Record<string, string> = {
  LOW: "success",
  MEDIUM: "warning",
  HIGH: "error",
};

export default function SystemProviders() {
  const runtime = useRuntimeOperations();
  const { token } = theme.useToken();

  if (runtime.isLoading) {
    return (
      <PageShell title="Provider 状态" description="正在读取运行底座合同">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (runtime.isError) {
    return (
      <PageShell title="Provider 状态" description="运行底座合同读取失败">
        <PageState
          state="error"
          title="暂时无法读取运行状态"
          description="请稍后重试，或让信息科检查后端 /api/v1/system/operations 接口。"
          action={
            <Button icon={<ReloadOutlined />} onClick={() => runtime.refetch()}>
              重试
            </Button>
          }
        />
      </PageShell>
    );
  }

  const data = runtime.data;
  if (!data) {
    return (
      <PageShell title="Provider 状态" description="运行底座合同暂无数据">
        <PageState state="empty" />
      </PageShell>
    );
  }

  return (
    <PageShell
      title="Provider 状态"
      description="运行配置 / Feature Flag / 依赖健康 / 备份恢复 / 国产化 profile"
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="整体健康"
                value={STATUS_LABEL[data.healthStatus] ?? data.healthStatus}
                valueStyle={{
                  color: data.healthStatus === "UP" ? token.colorSuccess : token.colorWarning,
                }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="部署模式" value={data.deploymentMode} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="数据库方言" value={data.databaseDialect} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="备份状态" value={data.backup.enabled ? "已启用" : "未启用"} />
            </Card>
          </Col>
        </Row>

        <Alert
          type={data.healthStatus === "UP" ? "success" : "warning"}
          showIcon
          message={`当前 profile：${data.activeProfiles.join(" / ") || "default"}`}
          description={`迁移路径：${data.migrationLocation}；国产化目标：${data.domesticProfile.targetOs}`}
        />

        <Card title="依赖健康" data-testid="runtime-dependencies">
          <Table<RuntimeDependencyStatus>
            rowKey="key"
            dataSource={data.dependencies}
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

        <Card title="Feature Flag">
          <Table<RuntimeFeatureFlag>
            rowKey="key"
            dataSource={data.featureFlags}
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
              {
                title: "风险",
                dataIndex: "risk",
                render: (risk) => <Tag color={RISK_COLOR[risk] ?? "default"}>{risk}</Tag>,
              },
              {
                title: "配置来源",
                dataIndex: "source",
                render: (source, record) => (
                  <Space direction="vertical" size={2}>
                    <Tag color={source === "SAFE_DEFAULT" ? "warning" : "processing"}>
                      {source ?? "CONFIG_CENTER"}
                    </Tag>
                    {record.warning ? (
                      <Typography.Text type="warning">{record.warning}</Typography.Text>
                    ) : null}
                  </Space>
                ),
              },
              { title: "负责人", dataIndex: "owner" },
              { title: "说明", dataIndex: "description" },
            ]}
          />
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title="备份恢复">
              <Space direction="vertical" size="small" className="mk-full-width">
                <Typography.Text>RPO：{data.backup.rpo}</Typography.Text>
                <Typography.Text>RTO：{data.backup.rto}</Typography.Text>
                <Typography.Text>备份脚本：{data.backup.backupScript}</Typography.Text>
                <Typography.Text>恢复脚本：{data.backup.restoreScript}</Typography.Text>
                <Typography.Text>{data.backup.checksumPolicy}</Typography.Text>
                {data.backup.source ? (
                  <Typography.Text>配置来源：{data.backup.source}</Typography.Text>
                ) : null}
                {data.backup.warning ? (
                  <Typography.Text type="warning">{data.backup.warning}</Typography.Text>
                ) : null}
              </Space>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="国产化 profile">
              <Space direction="vertical" size="small" className="mk-full-width">
                <Typography.Text>目标操作系统：{data.domesticProfile.targetOs}</Typography.Text>
                <Typography.Text>目标 JDK：{data.domesticProfile.targetJdk}</Typography.Text>
                <Space wrap>
                  {data.domesticProfile.databaseVendors.map((vendor) => (
                    <Tag key={vendor}>{vendor}</Tag>
                  ))}
                  {data.domesticProfile.cryptoAlgorithms.map((algorithm) => (
                    <Tag key={algorithm} color="blue">
                      {algorithm}
                    </Tag>
                  ))}
                </Space>
                <Typography.Text type="secondary">{data.domesticProfile.evidence}</Typography.Text>
              </Space>
            </Card>
          </Col>
        </Row>
      </Space>
    </PageShell>
  );
}
