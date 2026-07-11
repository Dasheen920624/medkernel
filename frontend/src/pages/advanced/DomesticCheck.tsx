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

import {
  downloadDomesticCompatibilityReport,
  useRuntimeOperations,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type { RuntimeDependencyStatus, RuntimeDomesticCheckItem } from "@/shared/api/hooks";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import {
  appendBrowserCompatibilityEvidence,
  probeBrowserCompatibility,
  type BrowserCompatibilityItem,
} from "@/shared/lib/browserCompatibility";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
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

function statusValueText(status: string, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return status;
  return CHECK_LABEL[status] ?? customerEnumLabel(status);
}

function databaseDialectText(value: string, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return value;
  const normalized = value.toLowerCase();
  if (
    normalized.includes("dm") ||
    normalized.includes("dameng") ||
    normalized.includes("kingbase")
  ) {
    return "国产关系库已匹配";
  }
  if (
    normalized.includes("postgres") ||
    normalized.includes("mysql") ||
    normalized.includes("oracle")
  ) {
    return "数据库适配待复核";
  }
  return "关系数据库已采集";
}

function evidenceText(
  value: string | null | undefined,
  evidenceDetailsEnabled: boolean,
  fallback: string,
) {
  return evidenceDetailsEnabled ? value || "未返回" : fallback;
}

function compatibilityCategoryText(
  item: RuntimeDomesticCheckItem,
  evidenceDetailsEnabled: boolean,
) {
  if (evidenceDetailsEnabled) return item.category;
  if (item.category === "OS") return "系统环境";
  if (item.category === "DATABASE") return "数据库适配";
  if (item.category === "BROWSER") return "浏览器现场";
  return "国产化适配";
}

function dependencyDetail(dependency: RuntimeDependencyStatus, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return dependency.detail;
  if (dependency.key === "database") {
    return dependency.status === "UP" ? "核心数据服务可用" : "核心数据服务需国产化复核";
  }
  return dependency.detail;
}

export default function DomesticCheck() {
  const security = useSecurityProfile();
  const runtime = useRuntimeOperations();
  const [filter, setFilter] = useState<CompatibilityFilter>("ALL");
  const [exporting, setExporting] = useState(false);
  const [browserCompatibility, setBrowserCompatibility] = useState(probeBrowserCompatibility);
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;

  if (runtime.isLoading) {
    return (
      <PageShell
        title="国产化适配自检"
        description="正在读取操作系统、Java 运行环境、数据库、中间件和国密能力自检快照"
      >
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (runtime.isError) {
    return (
      <PageShell title="国产化适配自检" description="国产化适配自检快照读取失败">
        <PageState
          state="error"
          title="暂时无法读取国产化适配自检"
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
      <PageShell title="国产化适配自检" description="国产化适配自检暂无数据">
        <PageState state="empty" title="暂无国产化适配自检快照" />
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
      title="国产化适配自检"
      description="真实探测当前国产化适配状态"
      extras={
        <>
          <EvidenceDetailsToggle securityProfile={security.data} />
          <Button
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={() => void exportReport()}
          >
            导出报告
          </Button>
        </>
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
              <Statistic
                title="整体状态"
                value={statusValueText(compatibility.overallStatus, evidenceDetailsEnabled)}
              />
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
              <Statistic
                title="数据库适配"
                value={databaseDialectText(data.databaseDialect, evidenceDetailsEnabled)}
              />
            </Card>
          </Col>
        </Row>

        <Alert
          type={compatibility.overallStatus === "PASS" ? "success" : "warning"}
          showIcon
          message={`国产化适配自检：${CHECK_LABEL[compatibility.overallStatus] ?? compatibility.overallStatus}`}
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
              {evidenceText(
                data.domesticProfile.targetOs,
                evidenceDetailsEnabled,
                "目标操作系统已登记",
              )}
            </Descriptions.Item>
            <Descriptions.Item label="目标 JDK">
              {evidenceText(
                data.domesticProfile.targetJdk,
                evidenceDetailsEnabled,
                "目标 JDK 已登记",
              )}
            </Descriptions.Item>
            <Descriptions.Item label="当前操作系统">
              {evidenceText(
                `${data.os.name} ${data.os.version} ${data.os.arch}`,
                evidenceDetailsEnabled,
                "当前操作系统已采集",
              )}
            </Descriptions.Item>
            <Descriptions.Item label="当前 JDK">
              {evidenceText(
                `${data.jvm.javaVendor} ${data.jvm.javaVersion}`,
                evidenceDetailsEnabled,
                "Java 运行时已采集",
              )}
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
            <Descriptions.Item label="证据">
              {evidenceText(
                data.domesticProfile.evidence,
                evidenceDetailsEnabled,
                "验收证据已登记",
              )}
            </Descriptions.Item>
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
                    <Text type="secondary">
                      {compatibilityCategoryText(item, evidenceDetailsEnabled)}
                    </Text>
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
              {
                title: "实际",
                render: (_, item) =>
                  evidenceText(item.actualValue, evidenceDetailsEnabled, "实际值已采集"),
              },
              {
                title: "目标",
                render: (_, item) =>
                  evidenceText(item.expectedValue, evidenceDetailsEnabled, "目标值已登记"),
              },
              { title: "原因", dataIndex: "reason" },
              { title: "建议", dataIndex: "recommendation" },
              {
                title: "证据",
                render: (_, item) =>
                  evidenceText(item.evidence, evidenceDetailsEnabled, "现场证据已登记"),
              },
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
              {
                title: "说明",
                render: (_, dependency) => dependencyDetail(dependency, evidenceDetailsEnabled),
              },
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
