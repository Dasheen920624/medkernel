import {
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
  useConfirmExport,
  useExportConfirmations,
  useLargeAuditEvents,
  useLargeListExportJob,
  useSecurityProfile,
  useSubmitLargeListExport,
  useTraceDiagnosis,
  useVerifyEvidence,
  type AuditEventRow,
  type EvidenceVerifyResult,
  type ExportConfirmation,
  type TracePayloadSummary,
  type TraceStateTransition,
} from "@/shared/api/hooks";
import { canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { useExpertModeStore } from "@/shared/lib/expertModeStore";
import { AsyncExportAction } from "@/shared/ui/AsyncExportAction";
import { canUseExpertMode } from "@/shared/ui/expertModeAccess";
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
const CONFIRMATION_PAGE_SIZE = 20;
const VIEW_KEY = "compliance.audit";
const route = findRouteByPath("/admin/audit");

if (!route?.experience) {
  throw new Error("审计与证据页面缺少体验声明");
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

function confirmationStatusTag(status: ExportConfirmation["status"]) {
  const labels = {
    CONFIRMED: "已确认",
    EXPORTED: "已导出",
  };
  const colors = {
    CONFIRMED: "processing",
    EXPORTED: "success",
  };
  return <Tag color={colors[status]}>{labels[status]}</Tag>;
}

function parseConfirmationScope(confirmation: ExportConfirmation) {
  try {
    const parsed = JSON.parse(confirmation.exportScopeSnapshot) as {
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

function confirmationExportRequest(confirmation: ExportConfirmation): AsyncExportRequest | null {
  const scope = parseConfirmationScope(confirmation);
  if (!scope) return null;
  const selectedScope = scope.selectedScope === "CURRENT_PAGE" ? "currentPage" : "filteredResult";
  return {
    resourceType: scope.resourceType,
    selectedScope,
    reason: confirmation.reason,
    idempotencyKey: confirmation.idempotencyKey,
    confirmationId: confirmation.confirmationId,
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
      capturedAt: confirmation.confirmedAt,
    },
  };
}

function confirmationScopeLabel(confirmation: ExportConfirmation) {
  const scope = parseConfirmationScope(confirmation);
  if (!scope) return "范围快照不可解析";
  const filters = Object.entries(scope.filters);
  if (filters.length === 0) return "全部审计事件";
  return filters.map(([key, value]) => `${key}=${value}`).join(" · ");
}

export default function AdminAudit() {
  const [filters, setFilters] = useState<ExperienceFilterValue[]>([]);
  const [cursorHistory, setCursorHistory] = useState<Array<string | undefined>>([undefined]);
  const [selectedAuditEvent, setSelectedAuditEvent] = useState<AuditEventRow>();
  const [diagnosisTraceId, setDiagnosisTraceId] = useState("");
  const [confirmationOpen, setConfirmationOpen] = useState(false);
  const [evidenceTarget, setEvidenceTarget] = useState<ExportConfirmation>();
  const [verifyTargetId, setVerifyTargetId] = useState<string>();
  const [verifyResult, setVerifyResult] = useState<EvidenceVerifyResult>();
  const [verifyError, setVerifyError] = useState<string>();
  const [confirmationPage, setConfirmationPage] = useState(1);
  const [confirmationForm] = Form.useForm<{ reason: string }>();

  const security = useSecurityProfile();
  const globalExpertMode = useExpertModeStore((state) => state.enabled);
  const expertMode = canUseExpertMode(security.data) && globalExpertMode;
  const canExport = hasPermission(security.data, "list.export");
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
  const confirmations = useExportConfirmations(
    { resourceType: "AUDIT_EVENT", page: confirmationPage, size: CONFIRMATION_PAGE_SIZE },
    canExport,
  );
  const confirmExport = useConfirmExport();
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
            title: "追踪号",
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

  async function submitExportConfirmation() {
    const values = await confirmationForm.validateFields();
    await confirmExport.mutateAsync({
      resourceType: "AUDIT_EVENT",
      exportScope: {
        resourceType: "AUDIT_EVENT",
        filters: auditQuery,
        selectedScope: "FILTERED_RESULT",
      },
      reason: values.reason.trim(),
      idempotencyKey: crypto.randomUUID(),
    });
    setConfirmationOpen(false);
    confirmationForm.resetFields();
  }

  function openEvidence(confirmation: ExportConfirmation) {
    setEvidenceTarget(confirmation);
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

  function evidenceButton(confirmation: ExportConfirmation) {
    if (!confirmation.confirmationEvidenceId && !confirmation.exportEvidenceId) return null;
    return (
      <Button
        aria-label={`查看证据 ${confirmation.confirmationId}`}
        icon={<FileProtectOutlined />}
        onClick={() => openEvidence(confirmation)}
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
          {!eventTraceId && <Text type="secondary">该事件未返回 追踪号</Text>}
        </Space>
        {diagnosisEnabled && traceDiagnosis.isLoading && <PageState state="loading" />}
        {diagnosisEnabled && traceDiagnosis.isError && (
          <PageState state="error" title="诊断链读取失败或无权查看" />
        )}
        {diagnosisEnabled && traceDiagnosis.data && (
          <>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="追踪号">
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
              locale={{ emptyText: "无输入内容摘要" }}
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

  const confirmationColumns = [
    {
      title: "导出",
      render: (_value: unknown, confirmation: ExportConfirmation) => (
        <Space direction="vertical" size={0}>
          <Text strong>{confirmation.reason}</Text>
          <Text type="secondary">{confirmation.confirmationId}</Text>
        </Space>
      ),
    },
    {
      title: "范围",
      render: (_value: unknown, confirmation: ExportConfirmation) =>
        confirmationScopeLabel(confirmation),
    },
    { title: "确认人", dataIndex: "confirmedBy" },
    {
      title: "状态",
      dataIndex: "status",
      render: (status: ExportConfirmation["status"]) => confirmationStatusTag(status),
    },
    {
      title: "操作",
      render: (_value: unknown, confirmation: ExportConfirmation) => {
        if (confirmation.status === "CONFIRMED") {
          const request = confirmationExportRequest(confirmation);
          return (
            <Space wrap>
              <AsyncExportAction
                enabled={Boolean(request)}
                disabledReason="确认范围快照不可解析"
                permissionGranted={canExport}
                request={
                  request ?? {
                    resourceType: "AUDIT_EVENT",
                    requestSnapshot,
                    selectedScope: "filteredResult",
                    reason: confirmation.reason,
                    confirmationId: confirmation.confirmationId,
                  }
                }
                buttonLabel="生成文件"
                buttonAriaLabel={`生成导出文件 ${confirmation.confirmationId}`}
                modalTitle="生成已确认导出文件"
                submitLabel="确认生成导出文件"
                onSubmit={submitExport.mutateAsync}
                onPoll={pollExport.mutateAsync}
              />
              {evidenceButton(confirmation)}
            </Space>
          );
        }
        if (confirmation.status === "EXPORTED") {
          return (
            <Space wrap>
              {confirmation.exportUri && (
                <Button type="link" href={confirmation.exportUri}>
                  下载文件
                </Button>
              )}
              {evidenceButton(confirmation)}
            </Space>
          );
        }
        return <Text type="secondary">无需操作</Text>;
      },
    },
  ];

  function renderConfirmationContent() {
    if (confirmations.isLoading) {
      return <PageState state="loading" />;
    }
    if (confirmations.isError) {
      return (
        <PageState
          state="error"
          title="导出记录读取失败"
          onRetry={() => void confirmations.refetch()}
        />
      );
    }
    return (
      <Table<ExportConfirmation>
        rowKey="confirmationId"
        dataSource={confirmations.data?.items ?? []}
        columns={confirmationColumns}
        pagination={{
          current: confirmations.data?.page ?? confirmationPage,
          pageSize: CONFIRMATION_PAGE_SIZE,
          total: confirmations.data?.total ?? 0,
          hideOnSinglePage: true,
          showSizeChanger: false,
          onChange: (nextPage) => setConfirmationPage(nextPage),
        }}
        scroll={{ x: "max-content" }}
      />
    );
  }

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      extras={
        canExport ? (
          <Button
            aria-label="确认导出范围"
            icon={<ExportOutlined />}
            onClick={() => {
              confirmationForm.setFieldsValue({ reason: "导出当前审计与证据筛选结果" });
              setConfirmationOpen(true);
            }}
          >
            确认导出范围
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
          description="事件按服务空间隔离并保留摘要、签名和追踪号；有权操作者逐次确认准确筛选范围后，由后端异步生成文件并自动登记摘要与证据。"
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
                      aria-label="追踪号 搜索"
                      placeholder="输入 追踪号"
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
            ...(canExport
              ? [
                  {
                    key: "confirmations",
                    label: "导出记录",
                    children: renderConfirmationContent(),
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
              <Descriptions.Item label="追踪号">
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
        title="确认导出范围"
        open={confirmationOpen}
        okText="确认范围"
        okButtonProps={{ "aria-label": "确认范围" }}
        confirmLoading={confirmExport.isPending}
        onOk={() => void submitExportConfirmation()}
        onCancel={() => setConfirmationOpen(false)}
      >
        <Form form={confirmationForm} layout="vertical">
          <Form.Item
            name="reason"
            label="导出原因"
            rules={[{ required: true, whitespace: true, message: "请填写导出原因" }]}
          >
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="导出证据"
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
              <Descriptions.Item label="确认证据">
                {evidenceTarget.confirmationEvidenceId ? (
                  <Space wrap>
                    <Text code copyable>
                      {evidenceTarget.confirmationEvidenceId}
                    </Text>
                    <Button
                      size="small"
                      aria-label="验签确认证据"
                      icon={<SafetyCertificateOutlined />}
                      loading={
                        verifyEvidence.isPending &&
                        verifyTargetId === evidenceTarget.confirmationEvidenceId
                      }
                      onClick={() => {
                        const evidenceId = evidenceTarget.confirmationEvidenceId;
                        if (evidenceId) void verifyEvidenceById(evidenceId);
                      }}
                    >
                      验签确认证据
                    </Button>
                  </Space>
                ) : (
                  "未生成"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="确认证据文件">
                {evidenceTarget.confirmationEvidenceFileUri ? (
                  <Typography.Link href={evidenceTarget.confirmationEvidenceFileUri}>
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
