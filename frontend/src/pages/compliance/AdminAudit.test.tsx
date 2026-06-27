import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useConfirmExport,
  useExportConfirmations,
  useLargeAuditEvents,
  useLargeListExportJob,
  useModelEgressConfirmations,
  useSecurityProfile,
  useSubmitLargeListExport,
  useTraceDiagnosis,
  useVerifyEvidence,
} from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import AdminAudit from "./AdminAudit";
import { buildAuditEventQuery } from "./auditQuery";

vi.mock("@/shared/api/hooks", () => ({
  useConfirmExport: vi.fn(),
  useExportConfirmations: vi.fn(),
  useLargeAuditEvents: vi.fn(),
  useLargeListExportJob: vi.fn(),
  useModelEgressConfirmations: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSubmitLargeListExport: vi.fn(),
  useTraceDiagnosis: vi.fn(),
  useVerifyEvidence: vi.fn(),
}));

const firstPage = {
  items: [
    {
      id: "7",
      eventId: "evt-7",
      occurredAt: "2026-06-06T12:00:00Z",
      actorUserId: "auditor-1",
      summary: "导出审计证据",
      actionCode: "EXPORT",
      resourceType: "audit",
      resourceId: "snapshot-7",
      traceId: "trace-7",
      signature: "sm2:signature",
      status: "SIGNED",
      outcome: "SUCCESS",
      beforeSnapshot: '{"enabled":true}',
      afterSnapshot: '{"enabled":false}',
      superAdminAction: false,
    },
  ],
  nextCursor: "Nw==",
  totalEstimate: 101,
  totalEstimated: true,
  hasMore: true,
};

const confirmations = [
  {
    confirmationId: "exp-audit-confirmed",
    resourceType: "audit_event",
    exportScopeSnapshot:
      '{"filters":{"outcome":"FAILED"},"resourceType":"AUDIT_EVENT","selectedScope":"FILTERED_RESULT"}',
    idempotencyKey: "audit-confirmed",
    reason: "复核失败事件",
    status: "CONFIRMED",
    confirmedBy: "auditor-1",
    confirmationEvidenceId: "evd-audit-confirmed",
    confirmationEvidenceFileUri:
      "/medkernel/api/v1/compliance/evidence/snapshots/evd-audit-confirmed/file",
    version: 1,
    confirmedAt: "2026-06-06T12:03:00Z",
  },
  {
    confirmationId: "exp-audit-exported",
    resourceType: "audit_event",
    exportScopeSnapshot:
      '{"filters":{"outcome":"SUCCESS"},"resourceType":"AUDIT_EVENT","selectedScope":"FILTERED_RESULT"}',
    idempotencyKey: "audit-exported",
    reason: "归档成功事件",
    status: "EXPORTED",
    confirmedBy: "auditor-1",
    confirmationEvidenceId: "evd-audit-exported-confirmation",
    confirmationEvidenceFileUri:
      "/medkernel/api/v1/compliance/evidence/snapshots/evd-audit-exported-confirmation/file",
    exportUri: "/medkernel/api/v1/large-lists/exports/job-audit-exported/download",
    exportDigest: "sha256:export-digest",
    exportEvidenceId: "evd-audit-exported-file",
    exportEvidenceFileUri:
      "/medkernel/api/v1/compliance/evidence/snapshots/evd-audit-exported-file/file",
    version: 2,
    confirmedAt: "2026-06-06T12:02:00Z",
  },
];

const modelEgressConfirmations = [
  {
    id: 7,
    capabilityCode: "clinical.explanation",
    payloadHash: "sha256:payload-001",
    purpose: "向患者解释检查结果，仅使用已脱敏字段",
    confirmedBy: "operator-001",
    confirmedAt: "2026-06-25T19:45:00Z",
  },
];

function query(data: unknown) {
  return {
    data,
    isLoading: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  };
}

describe("AdminAudit", () => {
  const confirmExport = vi.fn();
  const submitExport = vi.fn();
  const pollExport = vi.fn();
  const verifyEvidence = vi.fn();
  const traceDiagnosis = {
    traceId: "trace-7",
    startedAt: "2026-06-06T12:00:00Z",
    endedAt: "2026-06-06T12:00:03Z",
    durationMs: 3000,
    stateHistory: [
      {
        traceId: "trace-7",
        fromStatus: "PENDING",
        toStatus: "SIGNED",
        reason: "审计链签名完成",
        actor: "audit-service",
        occurredAt: "2026-06-06T12:00:02Z",
      },
    ],
    payloads: [
      {
        digest: "sm3:payload-7",
        contentType: "application/json",
        storageType: "audit_event",
        sizeBytes: 256,
      },
    ],
  };

  beforeEach(() => {
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
    vi.clearAllMocks();
    confirmExport.mockResolvedValue({
      confirmationId: "exp-audit-new",
      status: "CONFIRMED",
      version: 1,
    });
    submitExport.mockResolvedValue({
      jobId: "job-audit-1",
      status: "succeeded",
      submittedAt: "2026-06-06T12:01:00Z",
      submittedBy: "auditor-1",
      downloadUrl: "/download/job-audit-1",
    });
    pollExport.mockResolvedValue(undefined);
    verifyEvidence.mockResolvedValue({
      evidenceId: "evd-audit-exported-file",
      isValid: true,
      calculatedHash: "sm3:calculated",
      storedHash: "sm3:stored",
      signatureAlgorithm: "SM3_WITH_SM2",
      signatureValid: true,
      fileUri: "/medkernel/api/v1/compliance/evidence/snapshots/evd-audit-exported-file/file",
      fileDigest: "sm3:file-digest",
    });
    vi.mocked(useSecurityProfile).mockReturnValue(
      query({
        userId: "auditor-1",
        username: "auditor",
        permissions: [
          { code: "list.export" },
          { code: "audit.export" },
          { code: "audit.read" },
          { code: "advanced.read" },
        ],
        menuKeys: ["admin-audit"],
      }) as never,
    );
    vi.mocked(useExportConfirmations).mockReturnValue(
      query({
        items: confirmations,
        page: 1,
        size: 20,
        total: confirmations.length,
        hasNext: false,
        totalEstimated: false,
      }) as never,
    );
    vi.mocked(useModelEgressConfirmations).mockReturnValue(
      query({
        items: modelEgressConfirmations,
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      }) as never,
    );
    vi.mocked(useConfirmExport).mockReturnValue({
      mutateAsync: confirmExport,
      isPending: false,
    } as never);
    vi.mocked(useLargeListExportJob).mockReturnValue({ mutateAsync: pollExport } as never);
    vi.mocked(useSubmitLargeListExport).mockReturnValue({ mutateAsync: submitExport } as never);
    vi.mocked(useVerifyEvidence).mockReturnValue({
      mutateAsync: verifyEvidence,
      isPending: false,
    } as never);
    vi.mocked(useTraceDiagnosis).mockImplementation(
      (traceId: string, enabled = true) =>
        query(enabled && traceId === "trace-7" ? traceDiagnosis : undefined) as never,
    );
    vi.mocked(useLargeAuditEvents).mockImplementation(
      (request) =>
        query(
          request.cursor
            ? {
                ...firstPage,
                items: [{ ...firstPage.items[0], id: "6", eventId: "evt-6" }],
                nextCursor: null,
                hasMore: false,
              }
            : firstPage,
        ) as never,
    );
  });

  it("uses service cursors and public audit fields instead of client-side pagination", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    expect(screen.getByText("导出")).toBeInTheDocument();
    expect(screen.queryByText("auditor-1")).not.toBeInTheDocument();
    expect(screen.queryByText("追踪号 trace-7")).not.toBeInTheDocument();
    expect(screen.queryByText("审计记录 snapshot-7")).not.toBeInTheDocument();
    expect(screen.getByText("链签名已登记")).toBeInTheDocument();
    expect(screen.getByText(/事件按服务机构与组织范围隔离/)).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByText(/约 101 条/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() =>
      expect(useLargeAuditEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ cursor: "Nw==", size: 20, sort: "id,desc" }),
      ),
    );

    await user.click(screen.getByRole("button", { name: "上一页" }));
    await waitFor(() =>
      expect(useLargeAuditEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ cursor: undefined }),
      ),
    );
  });

  it("keeps audit readers out of export confirmation queries and controls", () => {
    vi.mocked(useSecurityProfile).mockReturnValue(
      query({
        userId: "clinical-user-1",
        username: "clinical-user",
        permissions: [{ code: "audit.read" }],
        menuKeys: ["admin-audit"],
      }) as never,
    );

    render(<AdminAudit />);

    expect(useExportConfirmations).toHaveBeenCalledWith(
      { resourceType: "AUDIT_EVENT", page: 1, size: 20 },
      false,
    );
    expect(screen.queryByRole("button", { name: "确认导出范围" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "导出记录" })).not.toBeInTheDocument();
  });

  it("maps time, actor, action, object and failure filters to the audit query contract", async () => {
    const from = new Date("2026-06-01T00:00:00").toISOString();
    const to = new Date("2026-07-01T00:00:00").toISOString();

    expect(
      buildAuditEventQuery([
        { key: "occurredAt", value: ["2026-06-01", "2026-06-30"] },
        { key: "action", value: "EXPORT" },
        { key: "actorUserId", value: "auditor-1" },
        { key: "resourceType", value: "audit" },
        { key: "outcome", value: "FAILED" },
        { key: "traceId", value: "trace-7" },
      ]),
    ).toEqual({
      action: "EXPORT",
      actorUserId: "auditor-1",
      resourceType: "audit",
      outcome: "FAILED",
      traceId: "trace-7",
      from,
      to,
    });

    const user = userEvent.setup();
    render(<AdminAudit />);
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("对象类型"), { target: { value: "audit" } });
    await user.click(screen.getByRole("combobox", { name: "执行结果" }));
    await user.click(await screen.findByText("失败"));

    await waitFor(() =>
      expect(useLargeAuditEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ resourceType: "audit", outcome: "FAILED" }),
      ),
    );
  });

  it("searches audit events by traceId without entering evidence details", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    await user.type(screen.getByLabelText("追踪号 搜索"), " trace-7 ");

    await waitFor(() =>
      expect(useLargeAuditEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ cursor: undefined, traceId: "trace-7" }),
      ),
    );
  });

  it("shows model egress confirmations as audit evidence without evidence details", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    await user.click(screen.getByRole("tab", { name: "模型外调确认" }));

    expect(useModelEgressConfirmations).toHaveBeenCalledWith({ page: 1, size: 20 }, true);
    expect(screen.getByText("临床解释与患者沟通")).toBeInTheDocument();
    expect(screen.getByText("向患者解释检查结果，仅使用已脱敏字段")).toBeInTheDocument();
    expect(screen.getByText("脱敏载荷摘要已生成")).toBeInTheDocument();
    expect(screen.getByText("已记录确认人")).toBeInTheDocument();
    expect(screen.queryByText("clinical.explanation")).not.toBeInTheDocument();
    expect(screen.queryByText("sha256:payload-001")).not.toBeInTheDocument();
    expect(screen.queryByText("operator-001")).not.toBeInTheDocument();
    expect(screen.queryByText("事件 evt-7")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("clinical.explanation")).toBeInTheDocument();
    expect(screen.getByText("sha256:payload-001")).toBeInTheDocument();
    expect(screen.getByText("operator-001")).toBeInTheDocument();
  });

  it("uses evidence details only for low-frequency evidence fields", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    expect(screen.queryByText("事件 evt-7")).not.toBeInTheDocument();
    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("auditor-1")).toBeInTheDocument();
    expect(screen.getByText("追踪号 trace-7")).toBeInTheDocument();
    expect(screen.getByText("审计记录 snapshot-7")).toBeInTheDocument();
    expect(screen.getByText("事件 evt-7")).toBeInTheDocument();
    expect(screen.getByText("环境未标记")).toBeInTheDocument();
    expect(screen.getByText("载荷未生成")).toBeInTheDocument();
  });

  it("opens persisted audit detail, diagnosis chain and redacted snapshots", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    await user.click(screen.getByRole("button", { name: "查看详情 导出审计证据" }));
    await user.click(screen.getByRole("button", { name: "打开诊断链 trace-7" }));

    expect(screen.getByRole("dialog", { name: "审计事件详情" })).toBeInTheDocument();
    expect(screen.getByText("audit / snapshot-7")).toBeInTheDocument();
    expect(screen.getByText("当前组织范围")).toBeInTheDocument();
    expect(screen.getByText("审计链签名完成")).toBeInTheDocument();
    expect(screen.getByText("sm3:payload-7")).toBeInTheDocument();
    expect(screen.getByText('{"enabled":true}')).toBeInTheDocument();
    expect(screen.getByText('{"enabled":false}')).toBeInTheDocument();
  });

  it("confirms the exact current filtered scope without a second reviewer", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    fireEvent.change(screen.getByLabelText("操作事项"), { target: { value: "EXPORT" } });
    await waitFor(() =>
      expect(useLargeAuditEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ action: "EXPORT" }),
      ),
    );

    await user.click(screen.getByRole("button", { name: "确认导出范围" }));
    await user.clear(screen.getByLabelText("导出原因"));
    await user.type(screen.getByLabelText("导出原因"), "复核当前导出操作");
    await user.click(screen.getByRole("button", { name: "确认范围" }));

    await waitFor(() =>
      expect(confirmExport).toHaveBeenCalledWith(
        expect.objectContaining({
          resourceType: "AUDIT_EVENT",
          exportScope: {
            resourceType: "AUDIT_EVENT",
            selectedScope: "FILTERED_RESULT",
            filters: { action: "EXPORT" },
          },
          reason: "复核当前导出操作",
          idempotencyKey: expect.any(String),
        }),
      ),
    );
    expect(screen.queryByText(/批准|驳回|审批意见/)).not.toBeInTheDocument();
  });

  it("generates a file from a confirmed scope and carries its confirmation id", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    await user.click(screen.getByRole("tab", { name: "导出记录" }));
    await user.click(screen.getByRole("button", { name: "生成导出文件 exp-audit-confirmed" }));
    await user.click(screen.getByRole("button", { name: "确认生成导出文件" }));

    await waitFor(() =>
      expect(submitExport).toHaveBeenCalledWith(
        expect.objectContaining({
          resourceType: "AUDIT_EVENT",
          selectedScope: "filteredResult",
          idempotencyKey: "audit-confirmed",
          confirmationId: "exp-audit-confirmed",
          requestSnapshot: expect.objectContaining({
            pageRequest: expect.objectContaining({ filters: { outcome: "FAILED" } }),
          }),
        }),
      ),
    );
  });

  it("shows confirmation and export evidence and verifies the selected evidence", async () => {
    render(<AdminAudit />);

    fireEvent.click(screen.getByRole("tab", { name: "导出记录" }));
    fireEvent.click(screen.getByRole("button", { name: "查看证据 exp-audit-exported" }));

    expect(await screen.findByText("evd-audit-exported-confirmation")).toBeInTheDocument();
    expect(screen.getByText("evd-audit-exported-file")).toBeInTheDocument();
    expect(screen.getByText("sha256:export-digest")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "验签导出证据" }));

    await waitFor(() => expect(verifyEvidence).toHaveBeenCalledWith("evd-audit-exported-file"));
    expect(await screen.findByText("证据验签通过")).toBeInTheDocument();
    expect(screen.getByText("SM3_WITH_SM2")).toBeInTheDocument();
  });
});
