import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  List,
  Progress,
  Select,
  Space,
  Tag,
  Typography,
} from "antd";
import {
  AuditOutlined,
  ExportOutlined,
  FireOutlined,
  ReloadOutlined,
  SearchOutlined,
  WarningOutlined,
} from "@ant-design/icons";

import { getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import { useQualityDashboard, useQualityDashboardDrilldown } from "@/shared/api/hooks";
import type {
  QualityDashboardAlert,
  QualityDashboardDrilldownItem,
  QualityDashboardDrilldownType,
  QualityDashboardHeatmapCell,
  QualityDashboardResponse,
  QualityValueMetric,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";

const { Text, Title } = Typography;

type TimeScope = "CURRENT_MONTH" | "LAST_30_DAYS" | "ALL";

export default function QcDashboard() {
  const [timeScope, setTimeScope] = useState<TimeScope>("CURRENT_MONTH");
  const [departmentId, setDepartmentId] = useState("");
  const [drilldownType, setDrilldownType] = useState<QualityDashboardDrilldownType>("FINDING");
  const [drawerOpen, setDrawerOpen] = useState(false);

  const dashboardParams = useMemo(() => {
    const range = resolveTimeRange(timeScope);
    return {
      ...range,
      departmentId: departmentId.trim() || undefined,
    };
  }, [departmentId, timeScope]);

  const dashboardQuery = useQualityDashboard(dashboardParams);
  const drilldownParams = useMemo(
    () => ({
      ...dashboardParams,
      type: drilldownType,
      page: 1,
      size: 20,
    }),
    [dashboardParams, drilldownType],
  );
  const drilldownQuery = useQualityDashboardDrilldown(drilldownParams);

  const dashboard = dashboardQuery.data;
  const unavailableMetrics =
    dashboard?.valueMetrics.metrics.filter((metric) => metric.status !== "AVAILABLE") ?? [];
  const isEmpty = Boolean(dashboard && isDashboardEmpty(dashboard));
  const errorStatus = getResponseStatus(dashboardQuery.error);
  const errorDetail = dashboardQuery.error
    ? parseApiError(dashboardQuery.error, "质控驾驶舱读取失败")
    : undefined;

  const primaryAction = (
    <Button
      type="primary"
      icon={<ExportOutlined />}
      disabled={!drilldownQuery.data?.evidencePackage}
      onClick={() => downloadEvidencePackage(drilldownQuery.data)}
    >
      导出证据
    </Button>
  );
  const extraActions = (
    <Space wrap>
      <Button icon={<ReloadOutlined />} onClick={() => dashboardQuery.refetch()}>
        刷新
      </Button>
      <Button
        aria-label="下钻问题证据"
        icon={<SearchOutlined />}
        onClick={() => setDrawerOpen(true)}
      >
        下钻问题证据
      </Button>
    </Space>
  );

  if (dashboardQuery.isLoading) {
    return (
      <PageShell
        title="院级质控驾驶舱"
        description="真实指标、风险热力与闭环价值"
        primary={primaryAction}
        extras={extraActions}
        state="loading"
      >
        <></>
      </PageShell>
    );
  }

  if (dashboardQuery.isError) {
    return (
      <PageShell
        title="院级质控驾驶舱"
        description="真实指标、风险热力与闭环价值"
        primary={primaryAction}
        extras={extraActions}
        state={errorStatus === 403 ? "forbidden" : "error"}
        stateProps={{
          title: errorStatus === 403 ? "当前权限不足" : "质控驾驶舱读取失败",
          description: getApiErrorMessage(
            dashboardQuery.error,
            "请检查登录权限、组织范围或质控服务状态。",
          ),
          traceId: errorDetail?.traceId,
          onRetry: () => dashboardQuery.refetch(),
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (!dashboard || isEmpty) {
    return (
      <PageShell
        title="院级质控驾驶舱"
        description="真实指标、风险热力与闭环价值"
        primary={primaryAction}
        extras={extraActions}
        state="empty"
        stateProps={{
          title: "当前筛选下暂无真实质控数据",
          description: "未从质控汇总接口读取到问题、预警或价值指标。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  return (
    <PageShell
      title="院级质控驾驶舱"
      description="真实指标、风险热力与闭环价值"
      primary={primaryAction}
      extras={extraActions}
    >
      <Space direction="vertical" size="large" className="w-full">
        <Card>
          <Space wrap className="w-full justify-between">
            <Space wrap>
              <Select
                aria-label="时间范围"
                className="w-36"
                value={timeScope}
                onChange={setTimeScope}
                options={[
                  { value: "CURRENT_MONTH", label: "本月" },
                  { value: "LAST_30_DAYS", label: "近 30 天" },
                  { value: "ALL", label: "全量" },
                ]}
              />
              <Input
                aria-label="科室范围"
                className="w-48"
                placeholder="科室范围"
                value={departmentId}
                onChange={(event) => setDepartmentId(event.target.value)}
                onPressEnter={() => dashboardQuery.refetch()}
              />
              <Select
                aria-label="下钻类型"
                className="w-36"
                value={drilldownType}
                onChange={setDrilldownType}
                options={[
                  { value: "FINDING", label: "问题证据" },
                  { value: "ALERT", label: "预警证据" },
                  { value: "RECTIFICATION", label: "整改证据" },
                ]}
              />
            </Space>
            <Text type="secondary">生成时间：{formatDateTime(dashboard.generatedAt)}</Text>
          </Space>
        </Card>

        {unavailableMetrics.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message="部分价值指标暂不可用"
            description={unavailableMetrics.map((metric) => metric.explanation).join("；")}
          />
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard
            icon={<AuditOutlined className="text-xl" />}
            label="真实质控问题总数"
            value={formatCount(dashboard.summary.totalFindings)}
          />
          <MetricCard
            icon={<WarningOutlined className="text-xl" />}
            label="待闭环问题"
            value={formatCount(dashboard.summary.openFindings)}
            danger={dashboard.summary.openFindings > 0}
          />
          <MetricCard
            icon={<FireOutlined className="text-xl" />}
            label="逾期整改任务"
            value={formatCount(dashboard.summary.overdueRectificationTasks)}
            danger={dashboard.summary.overdueRectificationTasks > 0}
          />
          <MetricCard
            icon={<SearchOutlined className="text-xl" />}
            label="当前打开预警"
            value={formatCount(dashboard.summary.activeAlerts)}
            danger={dashboard.summary.activeAlerts > 0}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <Card title="风险热力">
            <HeatmapList cells={dashboard.heatmap} />
          </Card>
          <Card title="价值指标">
            <ValueMetricList metrics={dashboard.valueMetrics.metrics} />
          </Card>
        </div>

        <Card title="打开预警">
          <AlertList alerts={dashboard.activeAlerts} />
        </Card>

        <EvidenceDrawer
          open={drawerOpen}
          drilldownType={drilldownType}
          query={drilldownQuery}
          onClose={() => setDrawerOpen(false)}
        />
      </Space>
    </PageShell>
  );
}

function MetricCard({
  icon,
  label,
  value,
  danger = false,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  danger?: boolean;
}) {
  return (
    <Card>
      <Space align="center">
        <span className={danger ? "text-rose-600" : "text-slate-600"}>{icon}</span>
        <Space direction="vertical" size={0}>
          <Text type="secondary" className="text-xs font-semibold">
            {label}
          </Text>
          <Title level={3} className={danger ? "m-0 text-rose-600" : "m-0 text-slate-800"}>
            {value}
          </Title>
        </Space>
      </Space>
    </Card>
  );
}

function HeatmapList({ cells }: { cells: QualityDashboardHeatmapCell[] }) {
  if (cells.length === 0) {
    return <Empty description="暂无真实科室风险热力" />;
  }
  return (
    <List
      dataSource={cells}
      renderItem={(cell) => (
        <List.Item>
          <Space direction="vertical" className="w-full" size={6}>
            <Space className="w-full justify-between" wrap>
              <Space wrap>
                <Text strong>{cell.departmentId || "全院"}</Text>
                <Tag color={cell.highRiskFindings > 0 ? "error" : "default"}>
                  {cell.maxSeverity || "未分级"}
                </Tag>
                <Tag>{cell.heatToken}</Tag>
              </Space>
              <Text type="secondary">
                问题 {cell.totalFindings} · 待闭环 {cell.openFindings} · 高危{" "}
                {cell.highRiskFindings}
              </Text>
            </Space>
            <Progress
              percent={toPercent(cell.hitRate)}
              size="small"
              showInfo={false}
              status={cell.highRiskFindings > 0 ? "exception" : "active"}
            />
          </Space>
        </List.Item>
      )}
    />
  );
}

function ValueMetricList({ metrics }: { metrics: QualityValueMetric[] }) {
  if (metrics.length === 0) {
    return <Empty description="暂无真实价值指标" />;
  }
  return (
    <List
      dataSource={metrics}
      renderItem={(metric) => (
        <List.Item>
          <Space direction="vertical" size={4} className="w-full">
            <Space className="w-full justify-between" wrap>
              <Text strong>{metric.displayName}</Text>
              {metric.status === "AVAILABLE" ? (
                <Text strong>{formatMetricValue(metric)}</Text>
              ) : (
                <Tag color="warning">NOT_AVAILABLE</Tag>
              )}
            </Space>
            <Text type="secondary">{metric.explanation}</Text>
            <Space wrap size={4}>
              <Tag>{metric.metricCode}</Tag>
              <Tag>{metric.formulaVersion}</Tag>
              <Text type="secondary">计算时间：{formatDateTime(metric.calculatedAt)}</Text>
            </Space>
          </Space>
        </List.Item>
      )}
    />
  );
}

function AlertList({ alerts }: { alerts: QualityDashboardAlert[] }) {
  if (alerts.length === 0) {
    return <Empty description="暂无打开预警" />;
  }
  return (
    <List
      dataSource={alerts}
      renderItem={(alert) => (
        <List.Item>
          <Space direction="vertical" size={4} className="w-full">
            <Space className="w-full justify-between" wrap>
              <Space wrap>
                <Tag color="error">{alert.severity}</Tag>
                <Text strong>{alert.title}</Text>
              </Space>
              <Text type="secondary">{formatDateTime(alert.createdAt)}</Text>
            </Space>
            <Text>{alert.evidenceSummary}</Text>
            <Space wrap size={4}>
              <Tag>{alert.departmentId ? `科室：${alert.departmentId}` : "全院"}</Tag>
              <Tag>{alert.sourceType}</Tag>
              {alert.traceId && <Text type="secondary">traceId: {alert.traceId}</Text>}
            </Space>
          </Space>
        </List.Item>
      )}
    />
  );
}

function EvidenceDrawer({
  open,
  drilldownType,
  query,
  onClose,
}: {
  open: boolean;
  drilldownType: QualityDashboardDrilldownType;
  query: ReturnType<typeof useQualityDashboardDrilldown>;
  onClose: () => void;
}) {
  const items = query.data?.items ?? [];
  return (
    <Drawer title="真实下钻证据" width={720} open={open} onClose={onClose} destroyOnClose>
      <Space direction="vertical" size="middle" className="w-full">
        <Space className="w-full justify-between" wrap>
          <Tag color="processing">{drilldownType}</Tag>
          <Text type="secondary">
            {query.data ? `共 ${query.data.total} 项，当前 ${items.length} 项` : "等待读取"}
          </Text>
        </Space>

        {query.isError && (
          <Alert
            type="error"
            showIcon
            message="下钻证据读取失败"
            description={getApiErrorMessage(query.error, "请检查权限、组织范围或质控服务状态。")}
          />
        )}

        {query.data?.evidencePackage && (
          <Alert
            type="info"
            showIcon
            message={`证据包 ${query.data.evidencePackage.packageId}`}
            description={`生成时间：${formatDateTime(query.data.evidencePackage.generatedAt)}；scopeDigest：${query.data.evidencePackage.scopeDigest}`}
          />
        )}

        <List
          loading={query.isLoading}
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无真实下钻证据" /> }}
          renderItem={(item) => <EvidenceItem item={item} />}
        />
      </Space>
    </Drawer>
  );
}

function EvidenceItem({ item }: { item: QualityDashboardDrilldownItem }) {
  return (
    <List.Item>
      <Space direction="vertical" size={4} className="w-full">
        <Space className="w-full justify-between" wrap>
          <Space wrap>
            <Tag color={item.severity === "P0" || item.severity === "P1" ? "error" : "default"}>
              {item.severity}
            </Tag>
            <Text strong>{item.title}</Text>
          </Space>
          <Text type="secondary">{formatDateTime(item.occurredAt)}</Text>
        </Space>
        <Text>{item.evidenceSummary}</Text>
        <Space wrap size={4}>
          <Tag>{item.departmentId || "全院"}</Tag>
          <Tag>{item.status}</Tag>
          <Tag>{item.sourceType}</Tag>
          <Text type="secondary">sourceId: {item.sourceId}</Text>
          {item.traceId && <Text type="secondary">{item.traceId}</Text>}
        </Space>
      </Space>
    </List.Item>
  );
}

function isDashboardEmpty(dashboard: QualityDashboardResponse): boolean {
  return (
    dashboard.summary.totalFindings === 0 &&
    dashboard.summary.activeAlerts === 0 &&
    dashboard.heatmap.length === 0 &&
    dashboard.valueMetrics.metrics.length === 0 &&
    dashboard.activeAlerts.length === 0
  );
}

function resolveTimeRange(scope: TimeScope): { from?: string; to?: string } {
  if (scope === "ALL") {
    return {};
  }
  const now = new Date();
  if (scope === "LAST_30_DAYS") {
    const from = new Date(now);
    from.setDate(from.getDate() - 30);
    return { from: from.toISOString(), to: now.toISOString() };
  }
  const from = new Date(now.getFullYear(), now.getMonth(), 1);
  return { from: from.toISOString(), to: now.toISOString() };
}

function formatCount(value: number): string {
  return `${value} 项`;
}

function formatMetricValue(metric: QualityValueMetric): string {
  if (metric.status !== "AVAILABLE" || metric.value === null) {
    return "NOT_AVAILABLE";
  }
  if (metric.unit === "%") {
    const percent = metric.value <= 1 ? metric.value * 100 : metric.value;
    return `${percent.toFixed(1)}%`;
  }
  return `${metric.value.toLocaleString()}${metric.unit ? ` ${metric.unit}` : ""}`;
}

function toPercent(value: number): number {
  const percent = value <= 1 ? value * 100 : value;
  return Math.max(0, Math.min(100, Math.round(percent * 10) / 10));
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "--";
  return value.replace("T", " ").slice(0, 16);
}

function getResponseStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null) return undefined;
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === "number" ? response.status : undefined;
}

function downloadEvidencePackage(data: ReturnType<typeof useQualityDashboardDrilldown>["data"]) {
  if (!data?.evidencePackage || typeof document === "undefined") {
    return;
  }
  const blob = new Blob([JSON.stringify(data.evidencePackage, null, 2)], {
    type: "application/json",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${data.evidencePackage.packageId}.json`;
  link.click();
  URL.revokeObjectURL(url);
}
