import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  List,
  Pagination,
  Select,
  Space,
  Tag,
  Typography,
} from "antd";
import { AuditOutlined, ReloadOutlined, SendOutlined, WarningOutlined } from "@ant-design/icons";

import { getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import {
  useAcknowledgeQualityAlert,
  useDispatchRectification,
  useOrgUnits,
  useSecurityProfile,
  useQualityAlerts,
  type QualityAlertsQueryParams,
  type QualityDashboardAlert,
  type QualityDashboardAlertStatus,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { RectificationAssignmentFields } from "@/shared/ui/RectificationAssignmentFields";
import { RectificationDueAtField } from "@/shared/ui/RectificationDueAtField";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import {
  clinicalDateTimeInputToIso,
  formatClinicalDateTimeInputValue,
} from "@/shared/lib/dateTimeText";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";

const { Text } = Typography;

type TimeScope = "TODAY" | "LAST_7_DAYS" | "ALL";
type AlertSeverityScope = "HIGH_RISK" | "P0" | "P1" | "P2" | "P3" | "ALL";
const ALERT_PAGE_SIZE = 20;

interface DispatchFormValues {
  responsibleDepartmentId: string;
  assigneeUserId?: string;
  dueAt: string;
}

export default function QcAlerts() {
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const [status, setStatus] = useState<QualityDashboardAlertStatus>("OPEN");
  const [timeScope, setTimeScope] = useState<TimeScope>("TODAY");
  const [severity, setSeverity] = useState<AlertSeverityScope>("HIGH_RISK");
  const [alertPage, setAlertPage] = useState(1);
  const [selectedAlert, setSelectedAlert] = useState<QualityDashboardAlert | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [dispatchFeedback, setDispatchFeedback] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [dispatchForm] = Form.useForm<DispatchFormValues>();

  const alertsParams = useMemo<QualityAlertsQueryParams>(() => {
    const range = resolveTimeRange(timeScope);
    return {
      ...range,
      status,
      severity,
      page: alertPage,
      size: ALERT_PAGE_SIZE,
    };
  }, [alertPage, severity, status, timeScope]);

  const alertsQuery = useQualityAlerts(alertsParams);
  const departmentsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    level: "DEPARTMENT",
    status: "ACTIVE",
  });
  const dispatchMutation = useDispatchRectification();
  const acknowledgeMutation = useAcknowledgeQualityAlert();
  const alertItems = alertsQuery.data?.items ?? [];
  const alertsTotal = alertsQuery.data?.total ?? 0;
  const alertOffset = alertsQuery.data?.offset ?? (alertPage - 1) * ALERT_PAGE_SIZE;
  const errorStatus = getResponseStatus(alertsQuery.error);
  const parsedError = alertsQuery.isError
    ? parseApiError(alertsQuery.error, "质量问题读取失败")
    : null;
  const departmentNames = useMemo(
    () =>
      new Map(
        (departmentsQuery.data?.items ?? []).map((unit) => [unit.id ?? unit.code, unit.name]),
      ),
    [departmentsQuery.data?.items],
  );

  function openAlertDrawer(alert: QualityDashboardAlert) {
    setSelectedAlert(alert);
    setDispatchFeedback(null);
    dispatchForm.setFieldsValue({
      responsibleDepartmentId: alert.departmentId ?? "",
      assigneeUserId: "",
      dueAt: defaultDueAt(alert.createdAt),
    });
    setDrawerOpen(true);
  }

  async function onDispatchRectification(values: DispatchFormValues) {
    if (!selectedAlert || selectedAlert.sourceType !== "quality_finding") {
      return;
    }
    try {
      const dueAt = normalizeDueAt(values.dueAt);
      const responsibleDepartmentId = values.responsibleDepartmentId.trim();
      await dispatchMutation.mutateAsync({
        request: {
          findingId: selectedAlert.sourceId,
          responsibleDepartmentId,
          assigneeUserId: optionalText(values.assigneeUserId),
          dueAt,
        },
        idempotencyKey: buildDispatchIdempotencyKey(selectedAlert, responsibleDepartmentId, dueAt),
      });
      setDispatchFeedback({
        type: "success",
        text: "整改任务已派发，处置状态将随来源事实闭环刷新。",
      });
      alertsQuery.refetch();
    } catch (error: unknown) {
      setDispatchFeedback({ type: "error", text: getApiErrorMessage(error, "整改任务派发失败") });
    }
  }

  async function onAcknowledgeAlert() {
    if (!selectedAlert) {
      return;
    }
    try {
      const acknowledged = await acknowledgeMutation.mutateAsync(selectedAlert.alertId);
      setSelectedAlert(acknowledged);
      setDispatchFeedback({
        type: "success",
        text: "风险提醒已确认，处置状态继续由来源事实闭环刷新。",
      });
      alertsQuery.refetch();
    } catch (error: unknown) {
      setDispatchFeedback({ type: "error", text: getApiErrorMessage(error, "确认风险提醒失败") });
    }
  }

  return (
    <PageShell
      title="质量问题与整改"
      description="确认质量问题、派发整改、复核并闭环"
      extras={
        <Space wrap>
          <EvidenceDetailsToggle securityProfile={security.data} />
          <Button href="/qc/eval/results">查看评价结果来源</Button>
          <Button
            aria-label="刷新质量问题"
            icon={<ReloadOutlined />}
            onClick={() => alertsQuery.refetch()}
          >
            刷新
          </Button>
        </Space>
      }
      state={resolvePageState(alertsQuery.isLoading, alertsQuery.isError, errorStatus, alertItems)}
      stateProps={{
        title: alertsQuery.isError ? parsedError?.message : "当前筛选下暂无待整改质量问题",
        description: alertsQuery.isError
          ? "请稍后重试；若持续失败，请联系信息科核查质量问题与整改服务。失败已留痕，可在审计证据中追溯。"
          : "当前没有符合筛选条件的质量问题。",
        traceId: parsedError?.traceId,
        onRetry: () => alertsQuery.refetch(),
      }}
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Card>
          <Space wrap size="middle" align="center">
            <Select
              aria-label="处置状态"
              value={status}
              className="mk-select-narrow"
              onChange={(value) => {
                setStatus(value);
                setAlertPage(1);
              }}
              options={[
                { value: "OPEN", label: "未处置" },
                { value: "ACKNOWLEDGED", label: "已确认" },
                { value: "RESOLVED", label: "已闭环" },
              ]}
            />
            <Select
              aria-label="发现时间"
              value={timeScope}
              className="mk-select-narrow"
              onChange={(value) => {
                setTimeScope(value);
                setAlertPage(1);
              }}
              options={[
                { value: "TODAY", label: "今日" },
                { value: "LAST_7_DAYS", label: "近 7 日" },
                { value: "ALL", label: "全量" },
              ]}
            />
            <Select
              aria-label="风险级别"
              value={severity}
              className="mk-select-narrow"
              onChange={(value) => {
                setSeverity(value);
                setAlertPage(1);
              }}
              options={[
                { value: "HIGH_RISK", label: "高风险" },
                { value: "P0", label: "安全红线" },
                { value: "P1", label: "高危" },
                { value: "P2", label: "中危" },
                { value: "P3", label: "低危" },
                { value: "ALL", label: "全部级别" },
              ]}
            />
          </Space>
        </Card>

        <Space wrap size="middle" className="mk-full-width">
          <MetricCard title="当前筛选问题总数" value={`${alertsTotal} 条`} />
          <MetricCard
            title="当前页待处置"
            value={`${countByStatus(alertItems, "OPEN")} 个待处置`}
          />
          <MetricCard title="当前页医疗安全" value={`${countSafetyAlerts(alertItems)} 个安全级`} />
        </Space>

        <Card
          title="质量问题与整改列表"
          extra={
            alertItems.length > 0 ? (
              <Text type="secondary">
                {formatAlertPageSummary(alertsTotal, alertOffset, alertItems.length)}
              </Text>
            ) : null
          }
        >
          <List
            dataSource={alertItems}
            locale={{
              emptyText: <Empty description="当前筛选下暂无待整改质量问题" />,
            }}
            renderItem={(alert) => (
              <List.Item
                actions={[
                  <Button
                    key="evidence"
                    aria-label="查看处置证据"
                    icon={<AuditOutlined />}
                    onClick={() => openAlertDrawer(alert)}
                  >
                    查看处置证据
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={severityTag(alert.severity)}
                  title={
                    <Space wrap>
                      <Text strong>{alert.title}</Text>
                      {statusTag(alert.status)}
                      {alertTypeTag(alert.alertType)}
                    </Space>
                  }
                  description={
                    <Space direction="vertical" size={4}>
                      <Space wrap>
                        <Text type="secondary">科室</Text>
                        <Text>
                          {alert.departmentId
                            ? (departmentNames.get(alert.departmentId) ?? alert.departmentId)
                            : "未指定"}
                        </Text>
                        <Text type="secondary">阈值</Text>
                        <Text>
                          {evidenceText(
                            alert.thresholdCode,
                            evidenceDetailsEnabled,
                            "高风险阈值已关联",
                          )}
                        </Text>
                      </Space>
                      <Text>{`证据摘要：${alertEvidenceSummary(alert, evidenceDetailsEnabled)}`}</Text>
                      <Space wrap>
                        <Text type="secondary">来源</Text>
                        <Text>{sourceTypeLabel(alert.sourceType)}</Text>
                        <Text type="secondary">证据</Text>
                        <Text>
                          {evidenceText(alert.traceId, evidenceDetailsEnabled, "证据已记录")}
                        </Text>
                      </Space>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
          {alertsTotal > ALERT_PAGE_SIZE && (
            <Pagination
              current={alertPage}
              pageSize={ALERT_PAGE_SIZE}
              total={alertsTotal}
              showSizeChanger={false}
              onChange={setAlertPage}
            />
          )}
        </Card>
      </Space>

      <Drawer
        title={
          <Space>
            <AuditOutlined />
            <span>质量风险处置证据</span>
          </Space>
        }
        placement="right"
        width={640}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        {selectedAlert ? (
          <Space direction="vertical" size="large" className="mk-full-width">
            {isSafetyAlert(selectedAlert) && (
              <Alert
                type="warning"
                showIcon
                icon={<WarningOutlined />}
                message="医疗安全问题保持显性处置"
                description="当前风险提醒来自高风险质量问题，未闭环前不会在本页默认静默。"
              />
            )}

            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="质量风险标题">{selectedAlert.title}</Descriptions.Item>
              <Descriptions.Item label="证据摘要">
                {alertEvidenceSummary(selectedAlert, evidenceDetailsEnabled)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">{statusTag(selectedAlert.status)}</Descriptions.Item>
              <Descriptions.Item label="级别">
                {severityTag(selectedAlert.severity)}
              </Descriptions.Item>
              <Descriptions.Item label="责任科室">
                {selectedAlert.departmentId
                  ? (departmentNames.get(selectedAlert.departmentId) ?? selectedAlert.departmentId)
                  : "未指定"}
              </Descriptions.Item>
              <Descriptions.Item label="来源事实">
                {evidenceText(selectedAlert.sourceId, evidenceDetailsEnabled, "来源事实已关联")}
              </Descriptions.Item>
              <Descriptions.Item label="证据">
                {evidenceText(selectedAlert.traceId, evidenceDetailsEnabled, "证据已记录")}
              </Descriptions.Item>
            </Descriptions>

            <Alert
              type="info"
              showIcon
              message="状态闭环口径"
              description="处置状态由来源事实刷新；本页只通过真实整改任务推进闭环，不在前端伪造确认结果。"
            />

            {selectedAlert.status === "OPEN" && (
              <Button
                aria-label="确认风险提醒"
                icon={<AuditOutlined />}
                loading={acknowledgeMutation.isPending}
                onClick={onAcknowledgeAlert}
              >
                确认风险提醒
              </Button>
            )}

            {selectedAlert.sourceType === "quality_finding" && selectedAlert.status === "OPEN" ? (
              <Card title="派发整改任务">
                {dispatchFeedback && (
                  <Alert
                    className="mk-margin-bottom"
                    type={dispatchFeedback.type}
                    showIcon
                    message={dispatchFeedback.text}
                  />
                )}
                <Form
                  form={dispatchForm}
                  layout="vertical"
                  onFinish={onDispatchRectification}
                  preserve={false}
                >
                  <RectificationAssignmentFields />
                  <RectificationDueAtField />
                  <Button
                    aria-label="派发整改任务"
                    type="primary"
                    htmlType="submit"
                    icon={<SendOutlined />}
                    loading={dispatchMutation.isPending}
                  >
                    派发整改任务
                  </Button>
                </Form>
              </Card>
            ) : (
              <Alert
                type="info"
                showIcon
                message="当前质量风险提醒不支持直接派发"
                description="只有未处置的质量问题来源提醒可在本页派发整改任务。"
              />
            )}
          </Space>
        ) : null}
      </Drawer>
    </PageShell>
  );
}

function MetricCard({ title, value }: { title: string; value: string }) {
  return (
    <Card className="mk-card-compact">
      <Space direction="vertical" size={2}>
        <Text type="secondary">{title}</Text>
        <Text strong>{value}</Text>
      </Space>
    </Card>
  );
}

function resolveTimeRange(scope: TimeScope): Pick<QualityAlertsQueryParams, "from" | "to"> {
  if (scope === "ALL") {
    return {};
  }
  const now = new Date();
  const from =
    scope === "TODAY"
      ? new Date(now.getFullYear(), now.getMonth(), now.getDate())
      : new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: now.toISOString() };
}

function resolvePageState(
  isLoading: boolean,
  isError: boolean,
  errorStatus: number | undefined,
  items: QualityDashboardAlert[],
) {
  if (isLoading) return "loading";
  if (isError) return errorStatus === 403 ? "forbidden" : "error";
  if (items.length === 0) return "empty";
  return "ready";
}

function statusTag(status: QualityDashboardAlertStatus) {
  if (status === "OPEN") {
    return <Tag color="warning">未处置</Tag>;
  }
  if (status === "ACKNOWLEDGED") {
    return <Tag color="processing">已确认</Tag>;
  }
  if (status === "RESOLVED") {
    return <Tag color="success">已闭环</Tag>;
  }
  return <Tag>{customerEnumLabel(status)}</Tag>;
}

function severityTag(severity: string) {
  if (severity === "P0") {
    return <Tag color="error">安全红线</Tag>;
  }
  if (severity === "P1") {
    return <Tag color="volcano">高危</Tag>;
  }
  if (severity === "P2") {
    return <Tag color="gold">中危</Tag>;
  }
  return <Tag>{severity ? customerEnumLabel(severity) : "未分级"}</Tag>;
}

function alertTypeTag(type: string) {
  if (type === "HIGH_RISK_FINDING") {
    return <Tag color="red">高风险问题</Tag>;
  }
  if (type === "OVERDUE_RECTIFICATION") {
    return <Tag color="orange">整改逾期</Tag>;
  }
  return <Tag>{customerEnumLabel(type)}</Tag>;
}

function sourceTypeLabel(type: string) {
  if (type === "quality_finding") {
    return "质量问题来源";
  }
  if (type === "rectification_task") {
    return "整改任务来源";
  }
  return customerEnumLabel(type);
}

function alertEvidenceSummary(alert: QualityDashboardAlert, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) {
    return alert.evidenceSummary;
  }
  if (alert.alertType === "HIGH_RISK_FINDING") {
    return "高风险质量问题仍未闭环";
  }
  if (alert.alertType === "OVERDUE_RECTIFICATION") {
    return "整改任务已逾期";
  }
  return "质量证据已记录";
}

function evidenceText(
  value: string | null | undefined,
  evidenceDetailsEnabled: boolean,
  businessText: string,
) {
  if (evidenceDetailsEnabled) {
    return value || "--";
  }
  return businessText;
}

function countByStatus(alerts: QualityDashboardAlert[], targetStatus: QualityDashboardAlertStatus) {
  return alerts.filter((alert) => alert.status === targetStatus).length;
}

function countSafetyAlerts(alerts: QualityDashboardAlert[]) {
  return alerts.filter(isSafetyAlert).length;
}

function formatAlertPageSummary(total: number, offset: number, currentCount: number) {
  const start = currentCount === 0 ? 0 : offset + 1;
  const end = currentCount === 0 ? 0 : Math.min(offset + currentCount, total);
  return `共 ${total} 条质量问题，当前显示 ${start}-${end} 条`;
}

function isSafetyAlert(alert: QualityDashboardAlert) {
  return (
    alert.severity === "P0" || alert.severity === "P1" || alert.alertType === "HIGH_RISK_FINDING"
  );
}

function defaultDueAt(createdAt: string | null | undefined) {
  const base = createdAt ? new Date(createdAt) : new Date();
  if (Number.isNaN(base.getTime())) {
    return formatClinicalDateTimeInputValue(new Date().toISOString());
  }
  base.setDate(base.getDate() + 7);
  return formatClinicalDateTimeInputValue(base.toISOString());
}

function normalizeDueAt(value: string) {
  return clinicalDateTimeInputToIso(value);
}

function optionalText(value: string | undefined) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function buildDispatchIdempotencyKey(
  alert: QualityDashboardAlert,
  responsibleDepartmentId: string,
  dueAt: string,
) {
  return `qc-alert-dispatch-${alert.alertId}-${responsibleDepartmentId}-${dueAt}`.slice(0, 160);
}

function getResponseStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null) return undefined;
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === "number" ? response.status : undefined;
}
