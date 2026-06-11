import {
  CheckOutlined,
  CloseOutlined,
  EyeOutlined,
  ExportOutlined,
  FileProtectOutlined,
  LeftOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import { useState } from "react";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  useCompleteApprovedExportJob,
  useExportApprovals,
  useLargeAuditEvents,
  useLargeListExportJob,
  useRequestExportApproval,
  useReviewExportApproval,
  useSecurityProfile,
  useSubmitLargeListExport,
  useTraceDiagnosis,
  useVerifyEvidence,
  type AuditEventRow,
  type EvidenceVerifyResult,
  type ExportApproval,
  type TracePayloadSummary,
  type TraceStateTransition,
} from "@/shared/api/hooks";
import { canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { AsyncExportAction } from "@/shared/ui/AsyncExportAction";
import { ExperienceFilterBar } from "@/shared/ui/ExperienceFilterBar";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { PageState } from "@/shared/ui/PageState";
import type {
  AsyncExportRequest,
  ExperienceFilterValue,
  ExperienceViewSnapshot,
  RouteExperience,
} from "@/shared/ui/experienceTypes";

import { buildAuditEventQuery } from "./auditQuery";

const { Text } = Typography;
const PAGE_SIZE = 20;
const VIEW_KEY = "compliance.audit";
const route = findRouteByPath("/admin/audit");

if (!route?.experience) {
  throw new Error("审计日志页面缺少体验声明");
}

const PAGE_META: { title: string; experience: RouteExperience } = {
  title: route.title,
  experience: route.experience,
};

function filterValue(filters: readonly ExperienceFilterValue[], key: string) {
  const value = filters.find((filter) => filter.key === key)?.value;
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function formatTime(value?: string | null) {
  if (!value) return "-";
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(timestamp);
}

function hasPermission(
  profile: ReturnType<typeof useSecurityProfile>["data"],
  permissionCode: string,
) {
  return profile?.permissions.some((permission) => permission.code === permissionCode) ?? false;
}

function outcomeTag(outcome?: string | null) {
  if (outcome === "SUCCESS") return <Tag color="green">成功</Tag>;
  if (outcome === "FAILURE" || outcome === "FAILED") return <Tag color="red">失败</Tag>;
  return <Tag>未知</Tag>;
}

function approvalStatusTag(status: ExportApproval["status"]) {
  const labels = {
    REQUESTED: "待审批",
    APPROVED: "已批准",
    REJECTED: "已驳回",
    EXPORTED: "已导出",
  };
  const colors = {
    REQUESTED: "warning",
    APPROVED: "processing",
    REJECTED: "error",
    EXPORTED: "success",
  };
  return <Tag color={colors[status]}>{labels[status]}</Tag>;
}

function parseApprovalScope(approval: ExportApproval) {
  try {
    const parsed = JSON.parse(approval.exportScopeSnapshot) as {
      resourceType?: unknown;
      filters?: unknown;
      selectedScope?: unknown;
    };
    if (
      parsed.resourceType !== "AUDIT_EVENT" ||
      (parsed.selectedScope !== "CURRENT_PAGE" && parsed.selectedScope !== "FILTERED_RESULT") ||
      !parsed.filters ||
      typeof parsed.filters !== "object" ||
      Array.isArray(parsed.filters)
    ) {
      return null;
    }
    const filters = Object.fromEntries(
      Object.entries(parsed.filters).filter(
        (entry): entry is [string, string] => typeof entry[1] === "string",
      ),
    );
    return {
      resourceType: parsed.resourceType,
      filters,
      selectedScope: parsed.selectedScope,
    };
  } catch {
    return null;
  }
}

function approvalExportRequest(approval: ExportApproval): AsyncExportRequest | null {
  const scope = parseApprovalScope(approval);
  if (!scope) return null;
  const selectedScope = scope.selectedScope === "CURRENT_PAGE" ? "currentPage" : "filteredResult";
  return {
    resourceType: scope.resourceType,
    selectedScope,
    reason: approval.requestReason,
    idempotencyKey: approval.idempotencyKey,
    requestSnapshot: {
      viewKey: VIEW_KEY,
      filters: Object.entries(scope.filters).map(([key, value]) => ({ key, value })),
      pageRequest: {
        pageSize: PAGE_SIZE,
        sortBy: "id",
        sortOrder: "desc",
        filters: scope.filters,
      },
      visibleColumnKeys: ["occurredAt", "actorUserId", "actionCode", "summary", "outcome"],
      expertMode: false,
      capturedAt: approval.requestedAt,
    },
  };
}

function approvalScopeLabel(approval: ExportApproval) {
  const scope = parseApprovalScope(approval);
  if (!scope) return "范围快照不可解析";
  const filters = Object.entries(scope.filters);
  if (filters.length === 0) return "全部审计事件";
  return filters.map(([key, value]) => `${key}=${value}`).join(" · ");
}

export default function AdminAudit() {
  const [filters, setFilters] = useState<ExperienceFilterValue[]>([]);
  const [cursorHistory, setCursorHistory] = useState<Array<string | undefined>>([undefined]);
  const [expertMode, setExpertMode] = useState(false);
  const [selectedAuditEvent, setSelectedAuditEvent] = useState<AuditEventRow>();
  const [diagnosisTraceId, setDiagnosisTraceId] = useState("");
  const [requestOpen, setRequestOpen] = useState(false);
  const [reviewTarget, setReviewTarget] = useState<{
    approval: ExportApproval;
    decision: "APPROVE" | "REJECT";
  }>();
  const [evidenceTarget, setEvidenceTarget] = useState<ExportApproval>();
  const [verifyTargetId, setVerifyTargetId] = useState<string>();
  const [verifyResult, setVerifyResult] = useState<EvidenceVerifyResult>();
  const [verifyError, setVerifyError] = useState<string>();
  const [requestForm] = Form.useForm<{ reason: string }>();
  const [reviewForm] = Form.useForm<{ comment: string }>();

  const security = useSecurityProfile();
  const canExport = hasPermission(security.data, "list.export");
  const canApproveExport = hasPermission(security.data, "audit.export");
  const currentCursor = cursorHistory[cursorHistory.length - 1];
  const auditQuery = buildAuditEventQuery(filters);
  const events = useLargeAuditEvents({
    cursor: currentCursor,
    size: PAGE_SIZE,
    sort: "id,desc",
    ...auditQuery,
  });
  const submitExport = useSubmitLargeListExport();
  const pollExport = useLargeListExportJob();
  const approvals = useExportApprovals({ resourceType: "AUDIT_EVENT" }, canApproveExport);
  const requestApproval = useRequestExportApproval();
  const reviewApproval = useReviewExportApproval();
  const completeApproval = useCompleteApprovedExportJob();
  const verifyEvidence = useVerifyEvidence();
  const traceDiagnosis = useTraceDiagnosis(diagnosisTraceId, Boolean(diagnosisTraceId));

  const routeAllowed = !security.data || canAccessRoute(route, security.data);
  const rows = events.data?.items ?? [];

  let pageState: "loading" | "empty" | "error" | "forbidden" | "ready" = "ready";
  if (!routeAllowed) pageState = "forbidden";
  else if (security.isLoading || events.isLoading) pageState = "loading";
  else if (events.isError) pageState = "error";
  else if (rows.length === 0) pageState = "empty";

  const requestSnapshot: ExperienceViewSnapshot = {
    viewKey: VIEW_KEY,
    filters,
    pageRequest: {
      pageSize: PAGE_SIZE,
      pageToken: currentCursor,
      sortBy: "id",
      sortOrder: "desc",
      filters: auditQuery,
    },
    visibleColumnKeys: expertMode
      ? ["occurredAt", "actorUserId", "actionCode", "summary", "outcome", "traceId", "signature"]
      : ["occurredAt", "actorUserId", "actionCode", "summary", "outcome"],
    expertMode,
    capturedAt: new Date().toISOString(),
  };

  const columns = [
    {
      title: "时间",
      dataIndex: "occurredAt",
      width: 180,
      render: (value: string) => (value ? new Date(value).toLocaleString() : "-"),
    },
    {
      title: "操作人",
      dataIndex: "actorUserId",
      width: 150,
      render: (value: string | null) => value ?? "系统",
    },
    {
      title: "操作",
      dataIndex: "actionCode",
      width: 140,
      render: (value: string) => <Text code>{value || "-"}</Text>,
    },
    {
      title: "摘要",
      dataIndex: "summary",
      width: 280,
      render: (value: string) => value || "-",
    },
    {
      title: "结果",
      dataIndex: "outcome",
      width: 100,
      render: (value: string | null) => outcomeTag(value),
    },
    {
      title: "详情",
      width: 72,
      render: (_value: unknown, record: AuditEventRow) => (
        <Tooltip title="查看审计详情">
          <Button
            aria-label={`查看详情 ${record.eventId}`}
            icon={<EyeOutlined />}
            onClick={() => {
              setDiagnosisTraceId("");
              setSelectedAuditEvent(record);
            }}
          />
        </Tooltip>
      ),
    },
    ...(expertMode
      ? [
          {
            title: "Trace ID",
            dataIndex: "traceId",
            width: 180,
            render: (value: string | null) => value ?? "-",
          },
          {
            title: "签名",
            dataIndex: "signature",
            width: 180,
            render: (value: string | null) => (value ? `${value.slice(0, 16)}...` : "-"),
          },
        ]
      : []),
  ];

  function updateFilters(nextFilters: ExperienceFilterValue[]) {
    setFilters(nextFilters);
    setCursorHistory([undefined]);
  }

  function updateFilter(key: string, value: string | undefined) {
    updateFilters([
      ...filters.filter((filter) => filter.key !== key),
      ...(value ? [{ key, value }] : []),
    ]);
  }

  function openNextPage() {
    const nextCursor = events.data?.nextCursor;
    if (!nextCursor) return;
    setCursorHistory((history) => [...history, nextCursor]);
  }

  function openPreviousPage() {
    setCursorHistory((history) => (history.length > 1 ? history.slice(0, -1) : history));
  }

  async function submitApprovalRequest() {
    const values = await requestForm.validateFields();
    await requestApproval.mutateAsync({
      resourceType: "AUDIT_EVENT",
      exportScope: {
        resourceType: "AUDIT_EVENT",
        filters: auditQuery,
        selectedScope: "FILTERED_RESULT",
      },
      reason: values.reason.trim(),
      idempotencyKey: crypto.randomUUID(),
    });
    setRequestOpen(false);
    requestForm.resetFields();
  }

  function openReview(approval: ExportApproval, decision: "APPROVE" | "REJECT") {
    setReviewTarget({ approval, decision });
    reviewForm.resetFields();
  }

  async function submitReview() {
    if (!reviewTarget) return;
    const values = await reviewForm.validateFields();
    await reviewApproval.mutateAsync({
      approvalId: reviewTarget.approval.approvalId,
      decision: reviewTarget.decision,
      comment: values.comment.trim(),
      expectedVersion: reviewTarget.approval.version,
    });
    setReviewTarget(undefined);
    reviewForm.resetFields();
  }

  async function finalizeApprovedJob(
    approval: ExportApproval,
    job: Awaited<ReturnType<typeof pollExport.mutateAsync>>,
  ) {
    if (job.status === "succeeded") {
      await completeApproval.mutateAsync({
        approvalId: approval.approvalId,
        jobId: job.jobId,
        reason: approval.requestReason,
        expectedVersion: approval.version,
      });
    }
    return job;
  }

  function openEvidence(approval: ExportApproval) {
    setEvidenceTarget(approval);
    setVerifyTargetId(undefined);
    setVerifyResult(undefined);
    setVerifyError(undefined);
  }

  function closeEvidence() {
    setEvidenceTarget(undefined);
    setVerifyTargetId(undefined);
    setVerifyResult(undefined);
    setVerifyError(undefined);
  }

  async function verifyEvidenceById(evidenceId: string) {
    setVerifyTargetId(evidenceId);
    setVerifyResult(undefined);
    setVerifyError(undefined);
    try {
      setVerifyResult(await verifyEvidence.mutateAsync(evidenceId));
    } catch (error: unknown) {
      setVerifyError(getApiErrorMessage(error, "证据验签失败"));
    }
  }

  function evidenceButton(approval: ExportApproval) {
    if (!approval.approvalEvidenceId && !approval.exportEvidenceId) return null;
    return (
      <Button
        aria-label={`查看证据 ${approval.approvalId}`}
        icon={<FileProtectOutlined />}
        onClick={() => openEvidence(approval)}
      >
        证据
      </Button>
    );
  }

  function renderTraceDiagnosis() {
    if (!selectedAuditEvent) return null;
    const eventTraceId = selectedAuditEvent.traceId?.trim();
    const diagnosisEnabled = Boolean(diagnosisTraceId);

    return (
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Space wrap>
          <Button
            aria-label={`打开诊断链 ${eventTraceId || "未返回"}`}
            icon={<SafetyCertificateOutlined />}
            disabled={!eventTraceId}
            onClick={() => setDiagnosisTraceId(eventTraceId ?? "")}
          >
            打开诊断链
          </Button>
          {!eventTraceId && <Text type="secondary">该事件未返回 Trace ID</Text>}
        </Space>
        {diagnosisEnabled && traceDiagnosis.isLoading && <PageState state="loading" />}
        {diagnosisEnabled && traceDiagnosis.isError && (
          <PageState state="error" title="诊断链读取失败或无权查看" />
        )}
        {diagnosisEnabled && traceDiagnosis.data && (
          <>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="Trace ID">
                <Text code copyable>
                  {traceDiagnosis.data.traceId}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="耗时">
                {traceDiagnosis.data.durationMs === null ||
                traceDiagnosis.data.durationMs === undefined
                  ? "-"
                  : `${traceDiagnosis.data.durationMs} ms`}
              </Descriptions.Item>
              <Descriptions.Item label="开始">
                {formatTime(traceDiagnosis.data.startedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="结束">
                {formatTime(traceDiagnosis.data.endedAt)}
              </Descriptions.Item>
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
              dataSource={traceDiagnosis.data.stateHistory}
              pagination={false}
              locale={{ emptyText: "无状态流转记录" }}
              scroll={{ x: "max-content" }}
              columns={[
                {
                  title: "状态",
                  render: (_value, record) =>
                    `${record.fromStatus ?? "-"} → ${record.toStatus ?? "-"}`,
                },
                { title: "原因", dataIndex: "reason" },
                { title: "执行人", dataIndex: "actor" },
                {
                  title: "时间",
                  dataIndex: "occurredAt",
                  render: (value) => formatTime(value),
                },
                {
                  title: "错误",
                  render: (_value, record) =>
                    record.error ? (
                      <Tag color="error">
                        {record.error.errorCode ?? record.error.errorClass ?? "ERROR"}
                      </Tag>
                    ) : (
                      "-"
                    ),
                },
              ]}
            />
            <Table<TracePayloadSummary>
              rowKey={(record) => record.digest}
              dataSource={traceDiagnosis.data.payloads}
              pagination={false}
              locale={{ emptyText: "无 Payload 摘要" }}
              scroll={{ x: "max-content" }}
              columns={[
                { title: "摘要", dataIndex: "digest" },
                { title: "内容类型", dataIndex: "contentType" },
                { title: "存储", dataIndex: "storageType" },
                {
                  title: "大小",
                  dataIndex: "sizeBytes",
                  render: (value) => `${value} B`,
                },
              ]}
            />
          </>
        )}
      </Space>
    );
  }

  const approvalColumns = [
    {
      title: "申请",
      render: (_value: unknown, approval: ExportApproval) => (
        <Space direction="vertical" size={0}>
          <Text strong>{approval.requestReason}</Text>
          <Text type="secondary">{approval.approvalId}</Text>
        </Space>
      ),
    },
    {
      title: "范围",
      render: (_value: unknown, approval: ExportApproval) => approvalScopeLabel(approval),
    },
    { title: "申请人", dataIndex: "requestedBy" },
    {
      title: "状态",
      dataIndex: "status",
      render: (status: ExportApproval["status"]) => approvalStatusTag(status),
    },
    {
      title: "操作",
      render: (_value: unknown, approval: ExportApproval) => {
        const selfRequested = approval.requestedBy === security.data?.userId;
        if (approval.status === "REQUESTED") {
          return (
            <Space>
              <Button
                aria-label={`批准 ${approval.approvalId}`}
                icon={<CheckOutlined />}
                disabled={!canApproveExport || selfRequested}
                onClick={() => openReview(approval, "APPROVE")}
              />
              <Button
                danger
                aria-label={`驳回 ${approval.approvalId}`}
                icon={<CloseOutlined />}
                disabled={!canApproveExport || selfRequested}
                onClick={() => openReview(approval, "REJECT")}
              />
            </Space>
          );
        }
        if (approval.status === "APPROVED") {
          const request = approvalExportRequest(approval);
          return (
            <Space wrap>
              <AsyncExportAction
                enabled={Boolean(request)}
                disabledReason="审批范围快照不可解析"
                permissionGranted={canExport}
                request={
                  request ?? {
                    resourceType: "AUDIT_EVENT",
                    requestSnapshot,
                    selectedScope: "filteredResult",
                    reason: approval.requestReason,
                  }
                }
                buttonLabel="生成文件"
                buttonAriaLabel={`生成导出文件 ${approval.approvalId}`}
                modalTitle="生成已审批导出文件"
                submitLabel="确认生成导出文件"
                onSubmit={async (payload) =>
                  finalizeApprovedJob(approval, await submitExport.mutateAsync(payload))
                }
                onPoll={async (jobId) =>
                  finalizeApprovedJob(approval, await pollExport.mutateAsync(jobId))
                }
              />
              {evidenceButton(approval)}
            </Space>
          );
        }
        if (approval.status === "EXPORTED") {
          return (
            <Space wrap>
              {approval.exportUri && (
                <Button type="link" href={approval.exportUri}>
                  下载文件
                </Button>
              )}
              {evidenceButton(approval)}
            </Space>
          );
        }
        if (approval.status === "REJECTED") return evidenceButton(approval);
        return <Text type="secondary">无需操作</Text>;
      },
    },
  ];

  function renderApprovalContent() {
    if (approvals.isLoading) {
      return <PageState state="loading" />;
    }
    if (approvals.isError) {
      return (
        <PageState
          state="error"
          title="导出审批读取失败"
          onRetry={() => void approvals.refetch()}
        />
      );
    }
    return (
      <Table<ExportApproval>
        rowKey="approvalId"
        dataSource={approvals.data ?? []}
        columns={approvalColumns}
        pagination={{ pageSize: 20, hideOnSinglePage: true }}
        scroll={{ x: "max-content" }}
      />
    );
  }

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      expertMode={expertMode}
      onExpertModeChange={setExpertMode}
      extras={
        canApproveExport ? (
          <Button
            aria-label="申请导出"
            icon={<ExportOutlined />}
            onClick={() => {
              requestForm.setFieldsValue({ reason: "导出当前审计日志筛选结果" });
              setRequestOpen(true);
            }}
          >
            申请导出
          </Button>
        ) : undefined
      }
    >
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Alert
          type="info"
          showIcon
          icon={<SafetyCertificateOutlined />}
          message="审计链已启用"
          description="事件按服务空间隔离并保留摘要、签名和追踪标识；敏感导出必须先申请、由他人审批，再由后端生成并登记真实文件摘要。"
        />
        <Tabs
          defaultActiveKey="events"
          items={[
            {
              key: "events",
              label: "审计事件",
              children: (
                <Space direction="vertical" size="middle" className="mk-full-width">
                  <ExperienceFilterBar
                    filters={PAGE_META.experience.defaultFilters}
                    value={filters}
                    onChange={updateFilters}
                    advanced={
                      expertMode ? (
                        <Space wrap size="small">
                          <Input.Search
                            aria-label="对象类型"
                            placeholder="输入对象类型"
                            value={filterValue(filters, "resourceType")}
                            allowClear
                            onChange={(event) =>
                              updateFilter("resourceType", event.target.value || undefined)
                            }
                            className="mk-search-sm"
                          />
                          <Select
                            aria-label="执行结果"
                            placeholder="选择执行结果"
                            value={filterValue(filters, "outcome")}
                            options={[
                              { label: "成功", value: "SUCCESS" },
                              { label: "失败", value: "FAILED" },
                            ]}
                            allowClear
                            onChange={(value) => updateFilter("outcome", value)}
                            className="mk-search-sm"
                          />
                        </Space>
                      ) : null
                    }
                  />
                  <Space wrap size="small">
                    <Input.Search
                      aria-label="Trace ID 搜索"
                      placeholder="输入 Trace ID"
                      value={filterValue(filters, "traceId") ?? ""}
                      allowClear
                      enterButton={<SearchOutlined />}
                      onChange={(event) =>
                        updateFilter("traceId", event.target.value.trim() || undefined)
                      }
                      onSearch={(value) => updateFilter("traceId", value.trim() || undefined)}
                      className="mk-search-sm"
                    />
                  </Space>
                  <PageState
                    state={pageState}
                    title="当前筛选下暂无审计事件"
                    onRetry={() => void events.refetch()}
                  >
                    <Table<AuditEventRow>
                      rowKey="id"
                      dataSource={rows}
                      columns={columns}
                      pagination={false}
                      scroll={{ x: "max-content" }}
                    />
                    <Space wrap className="mk-push-inline-start-auto">
                      <Text type="secondary">
                        第 {cursorHistory.length} 页 ·{" "}
                        {events.data?.totalEstimated
                          ? `约 ${events.data.totalEstimate} 条`
                          : `${events.data?.totalEstimate ?? rows.length} 条`}
                      </Text>
                      <Button
                        aria-label="上一页"
                        icon={<LeftOutlined />}
                        disabled={cursorHistory.length === 1}
                        onClick={openPreviousPage}
                      >
                        上一页
                      </Button>
                      <Button
                        aria-label="下一页"
                        icon={<RightOutlined />}
                        disabled={!events.data?.hasMore || !events.data.nextCursor}
                        onClick={openNextPage}
                      >
                        下一页
                      </Button>
                    </Space>
                  </PageState>
                </Space>
              ),
            },
            ...(canApproveExport
              ? [
                  {
                    key: "approvals",
                    label: "导出审批",
                    children: renderApprovalContent(),
                  },
                ]
              : []),
          ]}
        />
      </Space>
      <Drawer
        title="审计事件详情"
        aria-label="审计事件详情"
        width="min(760px, 100vw)"
        open={Boolean(selectedAuditEvent)}
        onClose={() => {
          setDiagnosisTraceId("");
          setSelectedAuditEvent(undefined);
        }}
        destroyOnClose
      >
        {selectedAuditEvent && (
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="摘要">{selectedAuditEvent.summary}</Descriptions.Item>
              <Descriptions.Item label="发生时间">
                {new Date(selectedAuditEvent.occurredAt).toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label="操作人">
                {selectedAuditEvent.actorUserId ?? "系统"}
              </Descriptions.Item>
              <Descriptions.Item label="操作">
                <Text code>{selectedAuditEvent.actionCode}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="业务对象">
                {selectedAuditEvent.resourceType} / {selectedAuditEvent.resourceId}
              </Descriptions.Item>
              <Descriptions.Item label="执行结果">
                {outcomeTag(selectedAuditEvent.outcome)}
              </Descriptions.Item>
              <Descriptions.Item label="错误码">
                {selectedAuditEvent.errorCode ?? "无"}
              </Descriptions.Item>
              <Descriptions.Item label="Trace ID">
                <Text code copyable>
                  {selectedAuditEvent.traceId ?? "未返回"}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="事件 ID">
                <Text code copyable>
                  {selectedAuditEvent.eventId}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="组织范围">
                {selectedAuditEvent.orgPath ?? "当前服务空间"}
              </Descriptions.Item>
              <Descriptions.Item label="环境">
                {selectedAuditEvent.environmentKey ?? "未标记"}
              </Descriptions.Item>
              <Descriptions.Item label="角色">
                {selectedAuditEvent.actorRoles ?? "未返回"}
              </Descriptions.Item>
              <Descriptions.Item label="载荷摘要">
                <Text code copyable>
                  {selectedAuditEvent.payloadDigest ?? "未生成"}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="链签名">
                <Text code copyable>
                  {selectedAuditEvent.signature ?? "未生成"}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="变更前快照">
                <Typography.Paragraph code copyable>
                  {selectedAuditEvent.beforeSnapshot ?? "无变更前快照"}
                </Typography.Paragraph>
              </Descriptions.Item>
              <Descriptions.Item label="变更后快照">
                <Typography.Paragraph code copyable>
                  {selectedAuditEvent.afterSnapshot ?? "无变更后快照"}
                </Typography.Paragraph>
              </Descriptions.Item>
            </Descriptions>
            {renderTraceDiagnosis()}
          </Space>
        )}
      </Drawer>
      <Modal
        title="申请导出审批"
        open={requestOpen}
        okText="提交导出申请"
        okButtonProps={{ "aria-label": "提交导出申请" }}
        confirmLoading={requestApproval.isPending}
        onOk={() => void submitApprovalRequest()}
        onCancel={() => setRequestOpen(false)}
      >
        <Form form={requestForm} layout="vertical">
          <Form.Item
            name="reason"
            label="申请理由"
            rules={[{ required: true, whitespace: true, message: "请填写导出申请理由" }]}
          >
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={reviewTarget?.decision === "REJECT" ? "驳回导出申请" : "批准导出申请"}
        open={Boolean(reviewTarget)}
        okText="确认审批"
        okButtonProps={{ "aria-label": "确认审批", danger: reviewTarget?.decision === "REJECT" }}
        confirmLoading={reviewApproval.isPending}
        onOk={() => void submitReview()}
        onCancel={() => setReviewTarget(undefined)}
      >
        <Form form={reviewForm} layout="vertical">
          <Form.Item
            name="comment"
            label="审批意见"
            rules={[{ required: true, whitespace: true, message: "请填写审批意见" }]}
          >
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="导出审批证据"
        open={Boolean(evidenceTarget)}
        footer={
          <Button type="primary" onClick={closeEvidence}>
            关闭
          </Button>
        }
        onCancel={closeEvidence}
      >
        {evidenceTarget && (
          <Space direction="vertical" size="middle" className="mk-full-width">
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="审批证据">
                {evidenceTarget.approvalEvidenceId ? (
                  <Space wrap>
                    <Text code copyable>
                      {evidenceTarget.approvalEvidenceId}
                    </Text>
                    <Button
                      size="small"
                      aria-label="验签审批证据"
                      icon={<SafetyCertificateOutlined />}
                      loading={
                        verifyEvidence.isPending &&
                        verifyTargetId === evidenceTarget.approvalEvidenceId
                      }
                      onClick={() => {
                        const evidenceId = evidenceTarget.approvalEvidenceId;
                        if (evidenceId) void verifyEvidenceById(evidenceId);
                      }}
                    >
                      验签审批证据
                    </Button>
                  </Space>
                ) : (
                  "未生成"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="审批证据文件">
                {evidenceTarget.approvalEvidenceFileUri ? (
                  <Typography.Link href={evidenceTarget.approvalEvidenceFileUri}>
                    下载证据文件
                  </Typography.Link>
                ) : (
                  "未生成"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="导出证据">
                {evidenceTarget.exportEvidenceId ? (
                  <Space wrap>
                    <Text code copyable>
                      {evidenceTarget.exportEvidenceId}
                    </Text>
                    <Button
                      size="small"
                      aria-label="验签导出证据"
                      icon={<SafetyCertificateOutlined />}
                      loading={
                        verifyEvidence.isPending &&
                        verifyTargetId === evidenceTarget.exportEvidenceId
                      }
                      onClick={() => {
                        const evidenceId = evidenceTarget.exportEvidenceId;
                        if (evidenceId) void verifyEvidenceById(evidenceId);
                      }}
                    >
                      验签导出证据
                    </Button>
                  </Space>
                ) : (
                  "未生成"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="导出证据文件">
                {evidenceTarget.exportEvidenceFileUri ? (
                  <Typography.Link href={evidenceTarget.exportEvidenceFileUri}>
                    下载证据文件
                  </Typography.Link>
                ) : (
                  "未生成"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="导出文件摘要">
                {evidenceTarget.exportDigest ? (
                  <Text code copyable>
                    {evidenceTarget.exportDigest}
                  </Text>
                ) : (
                  "未生成"
                )}
              </Descriptions.Item>
            </Descriptions>

            {verifyError && (
              <Alert type="error" showIcon message="证据验签失败" description={verifyError} />
            )}
            {verifyResult && (
              <>
                <Alert
                  type={verifyResult.isValid ? "success" : "error"}
                  showIcon
                  message={verifyResult.isValid ? "证据验签通过" : "证据验签失败"}
                  description={
                    verifyResult.signatureValid
                      ? "存储指纹、计算指纹与国密签名已由后端核验。"
                      : "国密签名无效，请立即停止使用该导出文件并核查审计事件。"
                  }
                />
                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="证据 ID">{verifyResult.evidenceId}</Descriptions.Item>
                  <Descriptions.Item label="签名算法">
                    {verifyResult.signatureAlgorithm}
                  </Descriptions.Item>
                  <Descriptions.Item label="存储指纹">
                    <Text code copyable>
                      {verifyResult.storedHash}
                    </Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="计算指纹">
                    <Text code copyable>
                      {verifyResult.calculatedHash}
                    </Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="文件指纹">
                    <Text code copyable>
                      {verifyResult.fileDigest || "未返回"}
                    </Text>
                  </Descriptions.Item>
                </Descriptions>
              </>
            )}
          </Space>
        )}
      </Modal>
    </PageExperienceShell>
  );
}
