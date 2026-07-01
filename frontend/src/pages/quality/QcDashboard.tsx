import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  List,
  Pagination,
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
import {
  useOrgUnits,
  useQualityDashboard,
  useQualityDashboardDrilldown,
  useSecurityProfile,
} from "@/shared/api/hooks";
import type {
  QualityDashboardAlert,
  QualityDashboardDrilldownItem,
  QualityDashboardDrilldownType,
  QualityDashboardHeatmapCell,
  QualityDashboardResponse,
  QualityValueMetric,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";

import styles from "./Quality.module.css";

const { Text, Title } = Typography;

type TimeScope = "CURRENT_MONTH" | "LAST_30_DAYS" | "ALL";

export default function QcDashboard() {
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const [timeScope, setTimeScope] = useState<TimeScope>("CURRENT_MONTH");
  const [departmentId, setDepartmentId] = useState("");
  const [drilldownType, setDrilldownType] = useState<QualityDashboardDrilldownType>("FINDING");
  const [drilldownPage, setDrilldownPage] = useState(1);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [departmentSearch, setDepartmentSearch] = useState("");
  const departmentsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: departmentSearch || undefined,
    level: "DEPARTMENT",
    status: "ACTIVE",
  });
  const departments = (departmentsQuery.data?.items ?? []).filter(
    (unit) =>
      unit.level === "DEPARTMENT" && (unit.status === undefined || unit.status === "ACTIVE"),
  );
  const departmentOptions = departments.map((unit) => ({
    value: unit.id ?? unit.code,
    label: `${unit.name} · ${unit.code}`,
  }));
  const departmentNames = new Map(departments.map((unit) => [unit.id ?? unit.code, unit.name]));

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
      page: drilldownPage,
      size: 20,
    }),
    [dashboardParams, drilldownPage, drilldownType],
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
      disabled={!drilldownQuery.data?.evidenceExport}
      onClick={() => downloadEvidenceExport(drilldownQuery.data)}
    >
      导出证据
    </Button>
  );
  const extraActions = (
    <Space wrap>
      <EvidenceDetailsToggle securityProfile={security.data} />
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
        title="质量管理概览"
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
        title="质量管理概览"
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
        title="质量管理概览"
        description="真实指标、风险热力与闭环价值"
        primary={primaryAction}
        extras={extraActions}
        state="empty"
        stateProps={{
          title: "当前筛选下暂无真实质控数据",
          description: "质控汇总服务暂未返回问题、预警或质量成效。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  return (
    <PageShell
      title="质量管理概览"
      description="真实指标、风险热力与闭环价值"
      primary={primaryAction}
      extras={extraActions}
    >
      <Space direction="vertical" size="large" className={styles.fullWidth}>
        <Card>
          <Space wrap className={styles.rowBetween}>
            <Space wrap>
              <Select
                aria-label="时间范围"
                className={styles.controlSm}
                value={timeScope}
                onChange={(value) => {
                  setTimeScope(value);
                  setDrilldownPage(1);
                }}
                options={[
                  { value: "CURRENT_MONTH", label: "本月" },
                  { value: "LAST_30_DAYS", label: "近 30 天" },
                  { value: "ALL", label: "全量" },
                ]}
              />
              <Select
                aria-label="科室范围"
                className={styles.controlMd}
                placeholder="科室范围"
                allowClear
                showSearch
                filterOption={false}
                onSearch={setDepartmentSearch}
                value={departmentId}
                options={departmentOptions}
                loading={departmentsQuery.isLoading}
                disabled={departmentsQuery.isError}
                notFoundContent="暂无可选科室"
                onChange={(value) => {
                  setDepartmentId(value ?? "");
                  setDrilldownPage(1);
                }}
              />
              <Select
                aria-label="下钻类型"
                className={styles.controlSm}
                value={drilldownType}
                onChange={(value) => {
                  setDrilldownType(value);
                  setDrilldownPage(1);
                }}
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
            message="部分质量成效暂不可用"
            description={unavailableMetrics.map((metric) => metric.explanation).join("；")}
          />
        )}

        <div className={styles.statsGrid}>
          <MetricCard
            icon={<AuditOutlined className={styles.iconLarge} />}
            label="真实质控问题总数"
            value={formatCount(dashboard.summary.totalFindings)}
          />
          <MetricCard
            icon={<WarningOutlined className={styles.iconLarge} />}
            label="待闭环问题"
            value={formatCount(dashboard.summary.openFindings)}
            danger={dashboard.summary.openFindings > 0}
          />
          <MetricCard
            icon={<FireOutlined className={styles.iconLarge} />}
            label="逾期整改任务"
            value={formatCount(dashboard.summary.overdueRectificationTasks)}
            danger={dashboard.summary.overdueRectificationTasks > 0}
          />
          <MetricCard
            icon={<SearchOutlined className={styles.iconLarge} />}
            label="当前待处置问题"
            value={formatCount(dashboard.summary.activeAlerts)}
            danger={dashboard.summary.activeAlerts > 0}
          />
        </div>

        <div className={styles.contentGrid}>
          <Card title="风险热力">
            <HeatmapList
              cells={dashboard.heatmap}
              departmentNames={departmentNames}
              evidenceDetailsEnabled={evidenceDetailsEnabled}
            />
          </Card>
          <Card title="质量成效">
            <ValueMetricList
              metrics={dashboard.valueMetrics.metrics}
              evidenceDetailsEnabled={evidenceDetailsEnabled}
            />
          </Card>
        </div>

        <Card title="待处置问题">
          <AlertList
            alerts={dashboard.activeAlerts}
            departmentNames={departmentNames}
            evidenceDetailsEnabled={evidenceDetailsEnabled}
          />
        </Card>

        <EvidenceDrawer
          open={drawerOpen}
          drilldownType={drilldownType}
          query={drilldownQuery}
          page={drilldownPage}
          departmentNames={departmentNames}
          evidenceDetailsEnabled={evidenceDetailsEnabled}
          onPageChange={setDrilldownPage}
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
        <span className={danger ? styles.metricIconDanger : styles.metricIcon}>{icon}</span>
        <Space direction="vertical" size={0}>
          <Text type="secondary" className={styles.metricLabel}>
            {label}
          </Text>
          <Title level={3} className={danger ? styles.metricValueDanger : styles.metricValue}>
            {value}
          </Title>
        </Space>
      </Space>
    </Card>
  );
}

function HeatmapList({
  cells,
  departmentNames,
  evidenceDetailsEnabled,
}: {
  cells: QualityDashboardHeatmapCell[];
  departmentNames: Map<string, string>;
  evidenceDetailsEnabled: boolean;
}) {
  if (cells.length === 0) {
    return <Empty description="暂无真实科室风险热力" />;
  }
  return (
    <List
      dataSource={cells}
      renderItem={(cell) => (
        <List.Item>
          <Space direction="vertical" className={styles.fullWidth} size={6}>
            <Space className={styles.rowBetween} wrap>
              <Space wrap>
                <Text strong>
                  {cell.departmentId
                    ? (departmentNames.get(cell.departmentId) ?? cell.departmentId)
                    : "全院"}
                </Text>
                <Tag color={cell.highRiskFindings > 0 ? "error" : "default"}>
                  {cell.maxSeverity || "未分级"}
                </Tag>
                <Tag>{evidenceDetailsEnabled ? cell.heatToken : heatmapBusinessLabel(cell)}</Tag>
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

function ValueMetricList({
  metrics,
  evidenceDetailsEnabled,
}: {
  metrics: QualityValueMetric[];
  evidenceDetailsEnabled: boolean;
}) {
  if (metrics.length === 0) {
    return <Empty description="暂无真实质量成效" />;
  }
  return (
    <List
      dataSource={metrics}
      renderItem={(metric) => (
        <List.Item>
          <Space direction="vertical" size={4} className={styles.fullWidth}>
            <Space className={styles.rowBetween} wrap>
              <Text strong>{metric.displayName}</Text>
              {metric.status === "AVAILABLE" ? (
                <Text strong>{formatMetricValue(metric)}</Text>
              ) : (
                <Tag color="warning">暂不可用</Tag>
              )}
            </Space>
            <Text type="secondary">{metric.explanation}</Text>
            <Space wrap size={4}>
              {evidenceDetailsEnabled ? (
                <>
                  <Tag>{metric.metricCode}</Tag>
                  <Tag>{metric.formulaVersion}</Tag>
                </>
              ) : (
                <Tag>指标口径已记录</Tag>
              )}
              <Text type="secondary">计算时间：{formatDateTime(metric.calculatedAt)}</Text>
            </Space>
          </Space>
        </List.Item>
      )}
    />
  );
}

function AlertList({
  alerts,
  departmentNames,
  evidenceDetailsEnabled,
}: {
  alerts: QualityDashboardAlert[];
  departmentNames: Map<string, string>;
  evidenceDetailsEnabled: boolean;
}) {
  if (alerts.length === 0) {
    return <Empty description="暂无待处置问题" />;
  }
  return (
    <List
      dataSource={alerts}
      renderItem={(alert) => (
        <List.Item>
          <Space direction="vertical" size={4} className={styles.fullWidth}>
            <Space className={styles.rowBetween} wrap>
              <Space wrap>
                <Tag color="error">{customerEnumLabel(alert.severity)}</Tag>
                <Text strong>{alert.title}</Text>
              </Space>
              <Text type="secondary">{formatDateTime(alert.createdAt)}</Text>
            </Space>
            <Text>{alert.evidenceSummary}</Text>
            <Space wrap size={4}>
              <Tag>
                {alert.departmentId
                  ? `科室：${departmentNames.get(alert.departmentId) ?? alert.departmentId}`
                  : "全院"}
              </Tag>
              <Tag>{qualitySourceLabel(alert.sourceType)}</Tag>
              {evidenceDetailsEnabled && alert.traceId ? (
                <Text type="secondary">追踪号：{alert.traceId}</Text>
              ) : null}
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
  page,
  departmentNames,
  evidenceDetailsEnabled,
  onPageChange,
  onClose,
}: {
  open: boolean;
  drilldownType: QualityDashboardDrilldownType;
  query: ReturnType<typeof useQualityDashboardDrilldown>;
  page: number;
  departmentNames: Map<string, string>;
  evidenceDetailsEnabled: boolean;
  onPageChange: (page: number) => void;
  onClose: () => void;
}) {
  const items = query.data?.items ?? [];
  const actionSummary = buildDrilldownActionSummary(items, departmentNames);
  return (
    <Drawer title="真实下钻证据" width={720} open={open} onClose={onClose} destroyOnClose>
      <Space direction="vertical" size="middle" className={styles.fullWidth}>
        <Space className={styles.rowBetween} wrap>
          <Tag color="processing">{customerEnumLabel(drilldownType)}</Tag>
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

        {query.data?.evidenceExport && (
          <Alert
            type="info"
            showIcon
            message={
              evidenceDetailsEnabled
                ? `证据导出编号：${query.data.evidenceExport.exportId}`
                : "证据包已生成"
            }
            description={
              evidenceDetailsEnabled
                ? `生成时间：${formatDateTime(query.data.evidenceExport.generatedAt)}；证据范围摘要：${query.data.evidenceExport.scopeDigest}`
                : `已按当前筛选范围记录 ${query.data.evidenceExport.itemCount} 项证据；生成时间：${formatDateTime(query.data.evidenceExport.generatedAt)}`
            }
          />
        )}

        {items.length > 0 && (
          <Alert
            type={actionSummary.highRiskCount > 0 ? "warning" : "info"}
            showIcon
            message="当前页处理摘要"
            description={actionSummary.description}
          />
        )}

        <List
          loading={query.isLoading}
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无真实下钻证据" /> }}
          renderItem={(item) => (
            <EvidenceItem
              item={item}
              departmentNames={departmentNames}
              evidenceDetailsEnabled={evidenceDetailsEnabled}
            />
          )}
        />
        {(query.data?.total ?? 0) > 20 && (
          <Pagination
            current={page}
            pageSize={20}
            total={query.data?.total ?? 0}
            showSizeChanger={false}
            onChange={onPageChange}
          />
        )}
      </Space>
    </Drawer>
  );
}

function buildDrilldownActionSummary(
  items: QualityDashboardDrilldownItem[],
  departmentNames: Map<string, string>,
) {
  const highRiskCount = items.filter((item) => isHighRiskSeverity(item.severity)).length;
  const openCount = items.filter((item) => isOpenQualityStatus(item.status)).length;
  const departmentCounts = new Map<string, number>();
  for (const item of items) {
    const departmentName = item.departmentId
      ? (departmentNames.get(item.departmentId) ?? item.departmentId)
      : "全院";
    departmentCounts.set(departmentName, (departmentCounts.get(departmentName) ?? 0) + 1);
  }
  const departmentSummary = [...departmentCounts.entries()]
    .slice(0, 3)
    .map(([name, count]) => `${name} ${count} 项`)
    .join("，");
  const priorityText =
    highRiskCount > 0
      ? `先处理 ${highRiskCount} 项高风险证据`
      : `先处理 ${openCount || items.length} 项未闭环证据`;
  const openText = openCount > 0 ? `未闭环 ${openCount} 项` : "当前页均已闭环或豁免";
  return {
    highRiskCount,
    description: `${priorityText}；${departmentSummary || "全院 0 项"}，${openText}。建议进入“质量问题与整改”派发、复核或关闭整改。`,
  };
}

function isHighRiskSeverity(value: string) {
  const normalized = value.toUpperCase();
  return normalized === "P0" || normalized === "P1";
}

function isOpenQualityStatus(value: string) {
  const normalized = value.toUpperCase();
  return !["CLOSED", "WAIVED", "RESOLVED", "DONE", "COMPLETED"].includes(normalized);
}

function EvidenceItem({
  item,
  departmentNames,
  evidenceDetailsEnabled,
}: {
  item: QualityDashboardDrilldownItem;
  departmentNames: Map<string, string>;
  evidenceDetailsEnabled: boolean;
}) {
  return (
    <List.Item>
      <Space direction="vertical" size={4} className={styles.fullWidth}>
        <Space className={styles.rowBetween} wrap>
          <Space wrap>
            <Tag color={item.severity === "P0" || item.severity === "P1" ? "error" : "default"}>
              {customerEnumLabel(item.severity)}
            </Tag>
            <Text strong>{item.title}</Text>
          </Space>
          <Text type="secondary">{formatDateTime(item.occurredAt)}</Text>
        </Space>
        <Text>{item.evidenceSummary}</Text>
        <Space wrap size={4}>
          <Tag>
            {item.departmentId
              ? (departmentNames.get(item.departmentId) ?? item.departmentId)
              : "全院"}
          </Tag>
          <Tag>{customerEnumLabel(item.status)}</Tag>
          <Tag>{qualitySourceLabel(item.sourceType)}</Tag>
          {evidenceDetailsEnabled ? (
            <>
              <Text type="secondary">来源编号：{item.sourceId}</Text>
              {item.traceId ? <Text type="secondary">追踪号：{item.traceId}</Text> : null}
            </>
          ) : (
            <Text type="secondary">来源已关联</Text>
          )}
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
    return "暂不可用";
  }
  const unit = metric.unit?.trim().toUpperCase();
  if (unit === "%" || unit === "RATE" || unit === "PERCENT") {
    const percent = metric.value <= 1 ? metric.value * 100 : metric.value;
    return `${percent.toFixed(1)}%`;
  }
  if (unit === "CASE_COUNT") {
    return `${metric.value.toLocaleString()} 例`;
  }
  if (unit === "COUNT") {
    return `${metric.value.toLocaleString()} 项`;
  }
  return `${metric.value.toLocaleString()}${metric.unit ? ` ${metric.unit}` : ""}`;
}

function toPercent(value: number): number {
  const percent = value <= 1 ? value * 100 : value;
  return Math.max(0, Math.min(100, Math.round(percent * 10) / 10));
}

function heatmapBusinessLabel(cell: QualityDashboardHeatmapCell): string {
  if (cell.highRiskFindings > 0) return "高风险聚集";
  if (cell.openFindings > 0) return "待闭环";
  return "风险平稳";
}

function qualitySourceLabel(sourceType: string): string {
  const normalized = sourceType.toUpperCase();
  if (normalized === "QUALITY_FINDING") return "质控问题";
  if (normalized === "QUALITY_ALERT") return "质控预警";
  if (normalized === "RECTIFICATION_TASK") return "整改任务";
  return customerEnumLabel(normalized);
}

function formatDateTime(value: string | null | undefined): string {
  return formatClinicalDateTime(value, "--");
}

function getResponseStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null) return undefined;
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === "number" ? response.status : undefined;
}

function downloadEvidenceExport(data: ReturnType<typeof useQualityDashboardDrilldown>["data"]) {
  if (!data?.evidenceExport || typeof document === "undefined") {
    return;
  }
  const blob = new Blob([JSON.stringify(data.evidenceExport, null, 2)], {
    type: "application/json",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${data.evidenceExport.exportId}.json`;
  link.click();
  URL.revokeObjectURL(url);
}
