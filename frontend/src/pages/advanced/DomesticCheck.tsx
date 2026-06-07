import { Alert, Button, Card, Col, Descriptions, Row, Space, Statistic, Table, Tag } from "antd";
import { ReloadOutlined } from "@ant-design/icons";

import { useRuntimeOperations } from "@/shared/api/hooks";
import type { RuntimeDependencyStatus } from "@/shared/api/hooks";
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

export default function DomesticCheck() {
  const runtime = useRuntimeOperations();

  if (runtime.isLoading) {
    return (
      <PageShell
        title="国产化自检"
        description="正在读取 OS / JDK / DB / 中间件 / 国密 Provider 自检快照"
      >
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (runtime.isError) {
    return (
      <PageShell title="国产化自检" description="国产化自检快照读取失败">
        <PageState
          state="error"
          title="暂时无法读取国产化自检"
          description="请稍后重试，或让信息科检查 /api/v1/system/operations。"
          action={
            <Button icon={<ReloadOutlined />} onClick={() => runtime.refetch()}>
              重读自检快照
            </Button>
          }
        />
      </PageShell>
    );
  }

  const data = runtime.data;
  if (!data) {
    return (
      <PageShell title="国产化自检" description="国产化自检暂无数据">
        <PageState state="empty" title="暂无国产化自检快照" />
      </PageShell>
    );
  }

  return (
    <PageShell
      title="国产化自检"
      description="实时检测当前 OS / JDK / DB / 中间件 / 国密 Provider 的国产化等级"
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Row gutter={[16, 16]}>
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
              <Statistic
                title="目标数据库"
                value={data.domesticProfile.databaseVendors.length}
                suffix="类"
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="国密算法"
                value={data.domesticProfile.cryptoAlgorithms.length}
                suffix="项"
              />
            </Card>
          </Col>
        </Row>

        <Alert
          type={data.healthStatus === "UP" ? "success" : "warning"}
          showIcon
          message={`当前运行状态：${STATUS_LABEL[data.healthStatus] ?? data.healthStatus}`}
          description={`自检生成时间：${data.generatedAt}；profile：${data.activeProfiles.join(" / ") || "default"}`}
        />

        <Card title="目标环境">
          <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="目标操作系统">
              {data.domesticProfile.targetOs}
            </Descriptions.Item>
            <Descriptions.Item label="目标 JDK">{data.domesticProfile.targetJdk}</Descriptions.Item>
            <Descriptions.Item label="数据库适配">
              <Space wrap>
                {data.domesticProfile.databaseVendors.map((vendor) => (
                  <Tag key={vendor}>{vendor}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="国密算法">
              <Space wrap>
                {data.domesticProfile.cryptoAlgorithms.map((algorithm) => (
                  <Tag key={algorithm} color="blue">
                    {algorithm}
                  </Tag>
                ))}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="证据" span={2}>
              {data.domesticProfile.evidence}
            </Descriptions.Item>
          </Descriptions>
        </Card>

        <Card title="依赖与降级状态">
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
      </Space>
    </PageShell>
  );
}
