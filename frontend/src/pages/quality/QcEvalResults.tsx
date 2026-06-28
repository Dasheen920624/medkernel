import { useMemo, useState, type ReactNode } from "react";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { TableProps } from "antd";
import {
  AuditOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  ReloadOutlined,
  SendOutlined,
  WarningOutlined,
} from "@ant-design/icons";

import { getApiErrorMessage, parseApiError } from "@/shared/api/errors";
import {
  useDispatchRectification,
  useEvaluationResults,
  useOrgUnits,
  useSecurityProfile,
  useQualityFindingDetail,
  useQualityFindings,
} from "@/shared/api/hooks";
import type {
  EvaluationResult,
  EvaluationResultLevel,
  QualityFinding,
  QualityFindingSeverity,
  QualityFindingStatus,
  RectificationTask,
} from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import type { PageStateKind } from "@/shared/ui/PageState.contract";
import { RectificationAssignmentFields } from "@/shared/ui/RectificationAssignmentFields";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";

const { Text } = Typography;

interface DispatchFormValues {
  responsibleDepartmentId: string;
  assigneeUserId?: string;
  dueAt: string;
}

export default function QcEvalResults() {
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const [resultLevel, setResultLevel] = useState<EvaluationResultLevel>("NON_COMPLIANT");
  const [findingStatus, setFindingStatus] = useState<QualityFindingStatus>("NEW");
  const [departmentId, setDepartmentId] = useState("");
  const [selectedFinding, setSelectedFinding] = useState<QualityFinding | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [departmentSearch, setDepartmentSearch] = useState("");
  const [dispatchFeedback, setDispatchFeedback] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [dispatchForm] = Form.useForm<DispatchFormValues>();

  const responsibleDepartmentId = optionalText(departmentId);
  const resultParams = useMemo(
    () => ({
      resultLevel,
      responsibleDepartmentId,
      page: 1,
      size: 20,
      sort: "createdAt,desc",
    }),
    [responsibleDepartmentId, resultLevel],
  );
  const findingParams = useMemo(
    () => ({
      status: findingStatus,
      responsibleDepartmentId,
      page: 1,
      size: 20,
      sort: "createdAt,desc",
    }),
    [findingStatus, responsibleDepartmentId],
  );

  const resultsQuery = useEvaluationResults(resultParams);
  const findingsQuery = useQualityFindings(findingParams);
  const departmentsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: departmentSearch || undefined,
    level: "DEPARTMENT",
    status: "ACTIVE",
  });
  const findingDetailQuery = useQualityFindingDetail(selectedFinding?.findingId ?? "");
  const dispatchMutation = useDispatchRectification();

  const results = useMemo(() => resultsQuery.data?.items ?? [], [resultsQuery.data?.items]);
  const findings = useMemo(() => findingsQuery.data?.items ?? [], [findingsQuery.data?.items]);
  const selectedFindingDetail = findingDetailQuery.data;
  const drawerFinding = selectedFindingDetail?.finding ?? selectedFinding;
  const departmentOptions = useMemo(
    () =>
      (departmentsQuery.data?.items ?? [])
        .filter((unit) => unit.level === "DEPARTMENT" && unit.status !== "ARCHIVED")
        .map((unit) => ({
          value: unit.id ?? unit.code,
          label: unit.name,
        })),
    [departmentsQuery.data?.items],
  );
  const departmentNames = useMemo(
    () =>
      new Map(
        (departmentsQuery.data?.items ?? []).map((unit) => [
          unit.id ?? unit.code,
          unit.name,
        ]),
      ),
    [departmentsQuery.data?.items],
  );
  const error = resultsQuery.error ?? findingsQuery.error;
  const parsedError =
    resultsQuery.isError || findingsQuery.isError ? parseApiError(error, "评价结果读取失败") : null;

  const metrics = useMemo(
    () => ({
      totalResults: resultsQuery.data?.total ?? 0,
      openFindings: findingsQuery.data?.total ?? 0,
      criticalResults: results.filter((item) => item.resultLevel === "CRITICAL").length,
      assignedFindings: findings.filter((item) => item.status === "ASSIGNED").length,
    }),
    [findings, findingsQuery.data?.total, results, resultsQuery.data?.total],
  );

  function refreshAll() {
    resultsQuery.refetch();
    findingsQuery.refetch();
  }

  function openFindingDrawer(finding: QualityFinding) {
    setSelectedFinding(finding);
    setDispatchFeedback(null);
    dispatchForm.setFieldsValue({
      responsibleDepartmentId: finding.responsibleDepartmentId ?? "",
      assigneeUserId: "",
      dueAt: defaultDueAt(finding.dueAt ?? finding.createdAt),
    });
    setDrawerOpen(true);
  }

  async function onDispatchRectification(values: DispatchFormValues) {
    if (!drawerFinding) {
      return;
    }
    const responsibleDepartmentIdValue = values.responsibleDepartmentId.trim();
    const dueAt = values.dueAt.trim();
    try {
      await dispatchMutation.mutateAsync({
        request: {
          findingId: drawerFinding.findingId,
          responsibleDepartmentId: responsibleDepartmentIdValue,
          assigneeUserId: optionalText(values.assigneeUserId),
          dueAt,
        },
        idempotencyKey: buildDispatchIdempotencyKey(
          drawerFinding.findingId,
          responsibleDepartmentIdValue,
          dueAt,
        ),
      });
      setDispatchFeedback({ type: "success", text: "整改任务已派发" });
      refreshAll();
    } catch (error: unknown) {
      setDispatchFeedback({ type: "error", text: getApiErrorMessage(error, "整改任务派发失败") });
    }
  }

  const resultColumns: TableProps<EvaluationResult>["columns"] = [
    {
      title: "指标与版本",
      key: "indicator",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>
            {evidenceText(record.indicatorCode, evidenceDetailsEnabled, "评价指标已关联")}
          </Text>
          <Text type="secondary">
            {versionText(record.indicatorVersion, evidenceDetailsEnabled)}
          </Text>
        </Space>
      ),
    },
    {
      title: "评估对象",
      key: "subject",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Tag>{subjectTypeLabel(record.subjectType)}</Tag>
          <Text type="secondary">
            {evidenceText(record.subjectRefId, evidenceDetailsEnabled, "对象已关联")}
          </Text>
        </Space>
      ),
    },
    {
      title: "级别",
      dataIndex: "resultLevel",
      key: "resultLevel",
      render: (level: EvaluationResultLevel) => renderLevelTag(level),
    },
    {
      title: "得分",
      dataIndex: "scoreValue",
      key: "scoreValue",
      render: (score: number | undefined) => renderScoreTag(score),
    },
    {
      title: "病历证据",
      key: "evidence",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>
            {evidenceDetailsEnabled ? record.evidenceSummary : "病历证据已关联"}
          </Text>
          {evidenceDetailsEnabled && record.sourceRef ? (
            <Text type="secondary">{record.sourceRef}</Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: "责任科室",
      dataIndex: "responsibleDepartmentId",
      key: "responsibleDepartmentId",
      render: (department: string | undefined) => (
        <Tag>{department ? (departmentNames.get(department) ?? department) : "全院"}</Tag>
      ),
    },
    {
      title: "证据",
      dataIndex: "traceId",
      key: "traceId",
      render: (traceId: string | undefined) => (
        <Text type="secondary">
          {evidenceText(traceId, evidenceDetailsEnabled, "证据已记录")}
        </Text>
      ),
    },
  ];

  const findingColumns: TableProps<QualityFinding>["columns"] = [
    {
      title: "问题",
      key: "finding",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.title}</Text>
          <Text type="secondary">
            {evidenceText(record.findingCode, evidenceDetailsEnabled, "问题已登记")}
          </Text>
        </Space>
      ),
    },
    {
      title: "级别 / 状态",
      key: "severity",
      render: (_, record) => (
        <Space wrap>
          {severityTag(record.severity)}
          {findingStatusTag(record.status)}
        </Space>
      ),
    },
    {
      title: "关联指标 / 结果",
      key: "link",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text>
            {evidenceText(record.indicatorId, evidenceDetailsEnabled, "评价指标已关联")}
          </Text>
          <Text type="secondary">
            {evidenceText(record.resultId, evidenceDetailsEnabled, "评价结果已关联")}
          </Text>
        </Space>
      ),
    },
    {
      title: "病历证据",
      dataIndex: "evidenceSummary",
      key: "evidenceSummary",
      render: (evidence: string) => (
        <Text>{evidenceDetailsEnabled ? evidence : "病历证据已关联"}</Text>
      ),
    },
    {
      title: "责任科室",
      dataIndex: "responsibleDepartmentId",
      key: "responsibleDepartmentId",
      render: (department: string | undefined) => (
        <Tag>{department ? (departmentNames.get(department) ?? department) : "未指定"}</Tag>
      ),
    },
    {
      title: "证据",
      dataIndex: "traceId",
      key: "traceId",
      render: (traceId: string | undefined) => (
        <Text type="secondary">
          {evidenceText(traceId, evidenceDetailsEnabled, "证据已记录")}
        </Text>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Button
          aria-label="查看问题详情"
          icon={<AuditOutlined />}
          onClick={() => openFindingDrawer(record)}
        >
          查看问题详情
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageShell
        title="质量问题来源"
        description="按真实评价结果追溯问题证据"
        extras={
          <Space wrap>
            <EvidenceDetailsToggle securityProfile={security.data} />
            <Button aria-label="刷新评价结果" icon={<ReloadOutlined />} onClick={refreshAll}>
              刷新
            </Button>
          </Space>
        }
        state={resolvePageState(
          resultsQuery.isLoading || findingsQuery.isLoading,
          resultsQuery.isError || findingsQuery.isError,
          getResponseStatus(error),
          results,
          findings,
        )}
        stateProps={{
          title: parsedError?.message ?? "当前筛选下暂无真实评价结果",
          description: parsedError
            ? "请稍后重试；若持续失败，请联系信息科核查质量问题来源服务。失败已留痕，可在审计证据中追溯。"
            : "当前没有符合筛选条件的评价结果或问题。",
          traceId: parsedError?.traceId,
          onRetry: refreshAll,
        }}
      >
        <Space direction="vertical" size="large" className="mk-full-width">
          <Card>
            <Space wrap size="middle" align="center">
              <Select
                aria-label="评估级别"
                value={resultLevel}
                className="mk-select-narrow"
                onChange={setResultLevel}
                options={[
                  { value: "NON_COMPLIANT", label: "质控缺陷" },
                  { value: "CRITICAL", label: "严重红线" },
                  { value: "ATTENTION", label: "需关注" },
                  { value: "PASS", label: "达标" },
                ]}
              />
              <Select
                aria-label="问题状态"
                value={findingStatus}
                className="mk-select-narrow"
                onChange={setFindingStatus}
                options={[
                  { value: "NEW", label: "未整改" },
                  { value: "ASSIGNED", label: "已派发" },
                  { value: "REMEDIATING", label: "整改中" },
                  { value: "CLOSED", label: "已闭环" },
                  { value: "WAIVED", label: "已豁免" },
                ]}
              />
              <Select
                aria-label="责任科室筛选"
                className="mk-input-narrow"
                placeholder="责任科室"
                allowClear
                showSearch
                filterOption={false}
                onSearch={setDepartmentSearch}
                value={departmentId}
                onChange={(value) => setDepartmentId(value ?? "")}
                options={departmentOptions}
                loading={departmentsQuery.isLoading}
              />
            </Space>
          </Card>

          <Space wrap size="middle" className="mk-full-width">
            <MetricCard
              icon={<DatabaseOutlined />}
              title="真实评价结果总数"
              value={`${metrics.totalResults} 例`}
            />
            <MetricCard
              icon={<WarningOutlined />}
              title="待整改问题总数"
              value={`${metrics.openFindings} 条`}
              danger={metrics.openFindings > 0}
            />
            <MetricCard
              icon={<WarningOutlined />}
              title="当前页严重红线"
              value={`${metrics.criticalResults} 条`}
              danger={metrics.criticalResults > 0}
            />
            <MetricCard
              icon={<CheckCircleOutlined />}
              title="当前页已派发"
              value={`${metrics.assignedFindings} 条`}
            />
          </Space>

          <Card title="评价结果台账">
            <Table
              dataSource={results}
              columns={resultColumns}
              rowKey={(record) => record.resultId}
              loading={resultsQuery.isLoading}
              locale={{ emptyText: <Empty description="暂无真实评价结果" /> }}
              pagination={{
                total: resultsQuery.data?.total ?? 0,
                pageSize: 20,
                showSizeChanger: false,
              }}
            />
          </Card>

          <Card title="质控问题与整改入口">
            <Table
              dataSource={findings}
              columns={findingColumns}
              rowKey={(record) => record.findingId}
              loading={findingsQuery.isLoading}
              locale={{ emptyText: <Empty description="暂无待整改质控问题" /> }}
              pagination={{
                total: findingsQuery.data?.total ?? 0,
                pageSize: 20,
                showSizeChanger: false,
              }}
            />
          </Card>
        </Space>
      </PageShell>

      <Drawer
        title="问题详情与病历证据"
        open={drawerOpen}
        width={720}
        onClose={() => setDrawerOpen(false)}
      >
        {drawerFinding ? (
          <Space direction="vertical" size="large" className="mk-full-width">
            {findingDetailQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message={getApiErrorMessage(findingDetailQuery.error, "问题详情读取失败")}
              />
            ) : null}

            <Descriptions bordered column={1}>
              <Descriptions.Item label="问题身份">
                {evidenceText(drawerFinding.findingCode, evidenceDetailsEnabled, "问题已登记")}
              </Descriptions.Item>
              <Descriptions.Item label="关联指标">
                {evidenceText(drawerFinding.indicatorId, evidenceDetailsEnabled, "评价指标已关联")}
              </Descriptions.Item>
              <Descriptions.Item label="关联结果">
                {evidenceText(drawerFinding.resultId, evidenceDetailsEnabled, "评价结果已关联")}
              </Descriptions.Item>
              <Descriptions.Item label="评估运行">
                {evidenceText(drawerFinding.runId, evidenceDetailsEnabled, "评估运行已记录")}
              </Descriptions.Item>
              <Descriptions.Item label="级别">
                {severityTag(drawerFinding.severity)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {findingStatusTag(drawerFinding.status)}
              </Descriptions.Item>
              <Descriptions.Item label="责任科室">
                {drawerFinding.responsibleDepartmentId
                  ? (departmentNames.get(drawerFinding.responsibleDepartmentId) ??
                    drawerFinding.responsibleDepartmentId)
                  : "未指定"}
              </Descriptions.Item>
              <Descriptions.Item label="证据">
                {evidenceText(drawerFinding.traceId, evidenceDetailsEnabled, "证据已记录")}
              </Descriptions.Item>
            </Descriptions>

            <Alert
              type="info"
              showIcon
              message="病历证据"
              description={
                evidenceDetailsEnabled ? drawerFinding.evidenceSummary : "病历证据已关联"
              }
            />

            <Card title="整改任务状态">
              {selectedFindingDetail?.task ? (
                <TaskSummary
                  task={selectedFindingDetail.task}
                  departmentNames={departmentNames}
                  evidenceDetailsEnabled={evidenceDetailsEnabled}
                />
              ) : (
                <Text type="secondary">暂无整改任务</Text>
              )}
            </Card>

            {selectedFindingDetail?.reviews.length ? (
              <Card title="复核记录">
                <Space direction="vertical" size="small" className="mk-full-width">
                  {selectedFindingDetail.reviews.map((review) => (
                    <Alert
                      key={review.reviewId}
                      type="info"
                      message={`${review.decision} · ${review.reviewedBy}`}
                      description={review.comments ?? review.evidenceRef ?? "无补充说明"}
                    />
                  ))}
                </Space>
              </Card>
            ) : null}

            {drawerFinding.status === "NEW" ? (
              <Card title="派发整改任务">
                {dispatchFeedback ? (
                  <Alert
                    className="mk-margin-bottom"
                    type={dispatchFeedback.type}
                    showIcon
                    message={dispatchFeedback.text}
                  />
                ) : null}
                <Form
                  form={dispatchForm}
                  layout="vertical"
                  onFinish={onDispatchRectification}
                  preserve={false}
                >
                  <RectificationAssignmentFields />
                  <Form.Item
                    name="dueAt"
                    label="整改截止时间"
                    rules={[{ required: true, message: "请输入整改截止时间" }]}
                  >
                    <Input />
                  </Form.Item>
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
                message="当前问题不支持直接派发"
                description="只有未整改问题可在本页创建整改派发任务。"
              />
            )}
          </Space>
        ) : null}
      </Drawer>
    </>
  );
}

function MetricCard({
  icon,
  title,
  value,
  danger = false,
}: {
  icon: ReactNode;
  title: string;
  value: string;
  danger?: boolean;
}) {
  return (
    <Card className="mk-card-compact">
      <Space size="middle">
        <span className={danger ? "text-rose-600" : "text-slate-600"}>{icon}</span>
        <Space direction="vertical" size={0}>
          <Text type="secondary">{title}</Text>
          <Text strong>{value}</Text>
        </Space>
      </Space>
    </Card>
  );
}

function TaskSummary({
  task,
  departmentNames,
  evidenceDetailsEnabled,
}: {
  task: RectificationTask;
  departmentNames: Map<string, string>;
  evidenceDetailsEnabled: boolean;
}) {
  return (
    <Descriptions bordered column={1}>
      <Descriptions.Item label="整改任务">
        {evidenceText(task.taskId, evidenceDetailsEnabled, "整改任务已生成")}
      </Descriptions.Item>
      <Descriptions.Item label="任务状态">{customerEnumLabel(task.status)}</Descriptions.Item>
      <Descriptions.Item label="责任科室">
        {evidenceDetailsEnabled
          ? task.responsibleDepartmentId
          : (departmentNames.get(task.responsibleDepartmentId) ?? "责任科室已关联")}
      </Descriptions.Item>
      <Descriptions.Item label="责任人">
        {evidenceText(task.assigneeUserId, evidenceDetailsEnabled, "责任人已关联")}
      </Descriptions.Item>
      <Descriptions.Item label="截止时间">{formatTime(task.dueAt)}</Descriptions.Item>
    </Descriptions>
  );
}

function renderScoreTag(score: number | undefined) {
  if (score === undefined || score === null) {
    return <Tag color="default">不计分</Tag>;
  }
  return <Tag color={score >= 90 ? "success" : "error"}>{score.toFixed(1)}分</Tag>;
}

function renderLevelTag(level: EvaluationResultLevel) {
  switch (level) {
    case "PASS":
      return <Tag color="success">达标</Tag>;
    case "ATTENTION":
      return <Tag color="warning">需关注</Tag>;
    case "NON_COMPLIANT":
      return <Tag color="error">质控缺陷</Tag>;
    case "CRITICAL":
      return <Tag color="red">严重红线</Tag>;
    default:
      return <Tag>{customerEnumLabel(level)}</Tag>;
  }
}

function severityTag(severity: QualityFindingSeverity) {
  const labels: Record<QualityFindingSeverity, string> = {
    P0: "安全红线",
    P1: "高危",
    P2: "中危",
    P3: "低危",
  };
  const colors: Record<QualityFindingSeverity, string> = {
    P0: "red",
    P1: "volcano",
    P2: "orange",
    P3: "blue",
  };
  return <Tag color={colors[severity]}>{labels[severity] ?? customerEnumLabel(severity)}</Tag>;
}

function findingStatusTag(status: QualityFindingStatus) {
  const labels: Record<QualityFindingStatus, string> = {
    NEW: "未整改",
    ASSIGNED: "已派发",
    REMEDIATING: "整改中",
    CLOSED: "已闭环",
    WAIVED: "已豁免",
  };
  const colors: Record<QualityFindingStatus, string> = {
    NEW: "error",
    ASSIGNED: "processing",
    REMEDIATING: "warning",
    CLOSED: "success",
    WAIVED: "default",
  };
  return <Tag color={colors[status]}>{labels[status] ?? customerEnumLabel(status)}</Tag>;
}

function subjectTypeLabel(subjectType: string) {
  const labels: Record<string, string> = {
    MEDICAL_RECORD: "病历",
    PATIENT: "患者",
    ENCOUNTER: "就诊",
    CLAIM: "医保结算",
    PATHWAY: "路径",
    FOLLOWUP: "随访",
  };
  return labels[subjectType] ?? customerEnumLabel(subjectType);
}

function resolvePageState(
  loading: boolean,
  error: boolean,
  status: number | undefined,
  results: EvaluationResult[],
  findings: QualityFinding[],
): PageStateKind {
  if (loading) return "loading";
  if (status === 401 || status === 403) return "forbidden";
  if (error) return "error";
  if (results.length === 0 && findings.length === 0) return "empty";
  return "ready";
}

function getResponseStatus(error: unknown): number | undefined {
  if (typeof error !== "object" || error === null) {
    return undefined;
  }
  const response = (error as { response?: { status?: unknown } }).response;
  return typeof response?.status === "number" ? response.status : undefined;
}

function optionalText(value: string | undefined) {
  const text = value?.trim();
  return text ? text : undefined;
}

function defaultDueAt(value: string | undefined) {
  if (!value) {
    return "";
  }
  return value;
}

function formatTime(value: string | undefined) {
  return value ? value.replace("T", " ").slice(0, 16) : "--";
}

function evidenceText(
  value: string | null | undefined,
  evidenceDetailsEnabled: boolean,
  businessText: string,
) {
  if (evidenceDetailsEnabled) return value || "--";
  return businessText;
}

function versionText(version: number | undefined, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return version === undefined ? "--" : `v${version}`;
  return version === undefined ? "评价口径已关联" : `第 ${version} 版评价口径`;
}

function buildDispatchIdempotencyKey(
  findingId: string,
  responsibleDepartmentId: string,
  dueAt: string,
) {
  return `qc-eval-result-dispatch-${findingId}-${responsibleDepartmentId}-${dueAt}`.slice(0, 160);
}
