import { useState } from "react";
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Row,
  Segmented,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from "antd";
import { DownloadOutlined, ReloadOutlined } from "@ant-design/icons";

import { downloadDomesticCompatibilityReport, useRuntimeOperations } from "@/shared/api/hooks";
import type { RuntimeDependencyStatus, RuntimeDomesticCheckItem } from "@/shared/api/hooks";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import {
  appendBrowserCompatibilityEvidence,
  probeBrowserCompatibility,
  type BrowserCompatibilityItem,
} from "@/shared/lib/browserCompatibility";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;

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

const CHECK_LABEL: Record<string, string> = {
  PASS: "通过",
  WARN: "警告",
  FAIL: "不通过",
  UNKNOWN: "待现场确认",
};

const CHECK_COLOR: Record<string, string> = {
  PASS: "success",
  WARN: "warning",
  FAIL: "error",
  UNKNOWN: "default",
};

type CompatibilityFilter = "ALL" | "ISSUES" | "UNKNOWN";

function browserAlertType(status: string): "success" | "warning" | "error" {
  if (status === "PASS") {
    return "success";
  }
  return status === "WARN" ? "warning" : "error";
}

export default function DomesticCheck() {
  const runtime = useRuntimeOperations();
  const [filter, setFilter] = useState<CompatibilityFilter>("ALL");
  const [exporting, setExporting] = useState(false);
  const [browserCompatibility, setBrowserCompatibility] = useState(probeBrowserCompatibility);

  if (runtime.isLoading) {
    return (
      <PageShell
        title="国产化自检"
        description="正在读取操作系统、Java 运行环境、数据库、中间件和国密能力自检快照"
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
          description="请稍后重试，或让信息科检查系统运行服务。"
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

  const compatibility = data.domesticCompatibility;
  const issueCount = compatibility.items.filter((item) =>
    ["WARN", "FAIL"].includes(item.status),
  ).length;
  const unknownCount = compatibility.items.filter((item) => item.status === "UNKNOWN").length;
  const filteredItems = (() => {
    if (filter === "ISSUES") {
      return compatibility.items.filter((item) => ["WARN", "FAIL"].includes(item.status));
    }
    if (filter === "UNKNOWN") {
      return compatibility.items.filter((item) => item.status === "UNKNOWN");
    }
    return compatibility.items;
  })();

  const exportReport = async () => {
    setExporting(true);
    try {
      const serverReport = await downloadDomesticCompatibilityReport();
      const report = await appendBrowserCompatibilityEvidence(serverReport, browserCompatibility);
      triggerBlobDownload(report, "medkernel-domestic-check.txt");
    } finally {
      setExporting(false);
    }
  };

  const refreshChecks = () => {
    setBrowserCompatibility(probeBrowserCompatibility());
    void runtime.refetch();
  };

  return (
    <PageShell
      title="国产化自检"
      description="真实探测当前国产化适配状态"
      extras={
        <Button icon={<DownloadOutlined />} loading={exporting} onClick={() => void exportReport()}>
          导出报告
        </Button>
      }
      primary={
        <Button type="primary" icon={<ReloadOutlined />} onClick={refreshChecks}>
          重新自检
        </Button>
      }
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="整体状态" value={compatibility.overallStatus} />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="警告/不通过" value={issueCount} suffix="项" />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="待现场确认" value={unknownCount} suffix="项" />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic title="数据库方言" value={data.databaseDialect} />
            </Card>
          </Col>
        </Row>

        <Alert
          type={compatibility.overallStatus === "PASS" ? "success" : "warning"}
          showIcon
          message={`国产化自检：${CHECK_LABEL[compatibility.overallStatus] ?? compatibility.overallStatus}`}
          description={`${compatibility.summary}；自检时间：${compatibility.checkedAt || data.generatedAt}`}
        />

        <Card title="当前浏览器能力预检">
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Alert
              type={browserAlertType(browserCompatibility.overallStatus)}
              showIcon
              message={browserCompatibility.summary}
              description={browserCompatibility.disclaimer}
            />
            <Table<BrowserCompatibilityItem>
              rowKey="key"
              dataSource={browserCompatibility.items}
              pagination={false}
              scroll={{ x: "max-content" }}
              columns={[
                { title: "浏览器能力", dataIndex: "displayName" },
                {
                  title: "状态",
                  dataIndex: "status",
                  render: (status) => (
                    <Tag color={CHECK_COLOR[status] ?? "default"}>
                      {CHECK_LABEL[status] ?? customerEnumLabel(status)}
                    </Tag>
                  ),
                },
                {
                  title: "重要性",
                  dataIndex: "required",
                  render: (required) => (required ? "关键能力" : "增强能力"),
                },
                {
                  title: "建议",
                  render: (_, item) => (item.supported ? "无需处理" : item.recommendation),
                },
              ]}
            />
          </Space>
        </Card>

        <Card title="目标环境">
          <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="目标操作系统">
              {data.domesticProfile.targetOs}
            </Descriptions.Item>
            <Descriptions.Item label="目标 JDK">{data.domesticProfile.targetJdk}</Descriptions.Item>
            <Descriptions.Item label="当前操作系统">
              {data.os.name} {data.os.version} {data.os.arch}
            </Descriptions.Item>
            <Descriptions.Item label="当前 JDK">
              {data.jvm.javaVendor} {data.jvm.javaVersion}
            </Descriptions.Item>
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
            <Descriptions.Item label="证据">{data.domesticProfile.evidence}</Descriptions.Item>
          </Descriptions>
        </Card>

        <Card
          title="逐项自检"
          extra={
            <Segmented<CompatibilityFilter>
              value={filter}
              onChange={setFilter}
              options={[
                { label: "全部", value: "ALL" },
                { label: "不兼容", value: "ISSUES" },
                { label: "待确认", value: "UNKNOWN" },
              ]}
            />
          }
        >
          <Table<RuntimeDomesticCheckItem>
            rowKey="key"
            dataSource={filteredItems}
            pagination={false}
            scroll={{ x: "max-content" }}
            columns={[
              {
                title: "项目",
                dataIndex: "displayName",
                render: (value, item) => (
                  <Space direction="vertical" size={0}>
                    <Text strong>{value}</Text>
                    <Text type="secondary">{item.category}</Text>
                  </Space>
                ),
              },
              {
                title: "状态",
                dataIndex: "status",
                render: (status) => (
                  <Tag color={CHECK_COLOR[status] ?? "default"}>
                    {CHECK_LABEL[status] ?? customerEnumLabel(status)}
                  </Tag>
                ),
              },
              { title: "实际", dataIndex: "actualValue" },
              { title: "目标", dataIndex: "expectedValue" },
              { title: "原因", dataIndex: "reason" },
              { title: "建议", dataIndex: "recommendation" },
              { title: "证据", dataIndex: "evidence" },
            ]}
          />
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
                    {STATUS_LABEL[status] ?? customerEnumLabel(status)}
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

function triggerBlobDownload(blob: Blob, filename: string) {
  if (typeof window.URL.createObjectURL !== "function") return;
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
