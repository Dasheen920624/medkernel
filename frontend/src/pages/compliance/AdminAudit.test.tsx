import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCompleteApprovedExportJob,
  useExportApprovals,
  useLargeAuditEvents,
  useLargeListExportJob,
  useRequestExportApproval,
  useReviewExportApproval,
  useSecurityProfile,
  useSubmitLargeListExport,
  useVerifyEvidence,
} from "@/shared/api/hooks";

import AdminAudit from "./AdminAudit";
import { buildAuditEventQuery } from "./auditQuery";

vi.mock("@/shared/api/hooks", () => ({
  useCompleteApprovedExportJob: vi.fn(),
  useExportApprovals: vi.fn(),
  useLargeAuditEvents: vi.fn(),
  useLargeListExportJob: vi.fn(),
  useRequestExportApproval: vi.fn(),
  useReviewExportApproval: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSubmitLargeListExport: vi.fn(),
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
  const submitExport = vi.fn();
  const pollExport = vi.fn();
  const requestApproval = vi.fn();
  const reviewApproval = vi.fn();
  const completeApproval = vi.fn();
  const verifyEvidence = vi.fn();
  const approvals = [
    {
      approvalId: "exp-audit-other",
      resourceType: "audit_event",
      exportScopeSnapshot:
        '{"filters":{"action":"EXPORT"},"resourceType":"AUDIT_EVENT","selectedScope":"FILTERED_RESULT"}',
      idempotencyKey: "audit-other",
      requestReason: "复核导出操作",
      status: "REQUESTED",
      requestedBy: "requester-2",
      version: 1,
      requestedAt: "2026-06-06T12:05:00Z",
    },
    {
      approvalId: "exp-audit-self",
      resourceType: "audit_event",
      exportScopeSnapshot:
        '{"filters":{},"resourceType":"AUDIT_EVENT","selectedScope":"FILTERED_RESULT"}',
      idempotencyKey: "audit-self",
      requestReason: "本人申请",
      status: "REQUESTED",
      requestedBy: "auditor-1",
      version: 1,
      requestedAt: "2026-06-06T12:04:00Z",
    },
    {
      approvalId: "exp-audit-approved",
      resourceType: "audit_event",
      exportScopeSnapshot:
        '{"filters":{"outcome":"FAILED"},"resourceType":"AUDIT_EVENT","selectedScope":"FILTERED_RESULT"}',
      idempotencyKey: "audit-approved",
      requestReason: "复核失败事件",
      status: "APPROVED",
      requestedBy: "requester-3",
      reviewerId: "auditor-1",
      reviewDecision: "APPROVE",
      reviewComment: "批准导出",
      version: 2,
      requestedAt: "2026-06-06T12:03:00Z",
      reviewedAt: "2026-06-06T12:06:00Z",
    },
    {
      approvalId: "exp-audit-exported",
      resourceType: "audit_event",
      exportScopeSnapshot:
        '{"filters":{"outcome":"SUCCESS"},"resourceType":"AUDIT_EVENT","selectedScope":"FILTERED_RESULT"}',
      idempotencyKey: "audit-exported",
      requestReason: "归档成功事件",
      status: "EXPORTED",
      requestedBy: "requester-4",
      reviewerId: "auditor-1",
      reviewDecision: "APPROVE",
      reviewComment: "批准归档",
      approvalEvidenceId: "evd-audit-exported-approval",
      approvalEvidenceFileUri:
        "/medkernel/api/v1/compliance/evidence/snapshots/evd-audit-exported-approval/file",
      exportUri: "/medkernel/api/v1/large-lists/exports/job-audit-exported/download",
      exportDigest: "sha256:export-digest",
      exportEvidenceId: "evd-audit-exported-file",
      exportEvidenceFileUri:
        "/medkernel/api/v1/compliance/evidence/snapshots/evd-audit-exported-file/file",
      version: 3,
      requestedAt: "2026-06-06T12:02:00Z",
      reviewedAt: "2026-06-06T12:06:00Z",
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    submitExport.mockResolvedValue({
      jobId: "job-audit-1",
      status: "succeeded",
      submittedAt: "2026-06-06T12:01:00Z",
      submittedBy: "auditor-1",
      downloadUrl: "/download/job-audit-1",
    });
    pollExport.mockResolvedValue(undefined);
    requestApproval.mockResolvedValue({ approvalId: "exp-new", status: "REQUESTED" });
    reviewApproval.mockResolvedValue({ approvalId: "exp-audit-other", status: "APPROVED" });
    completeApproval.mockResolvedValue({ approvalId: "exp-audit-approved", status: "EXPORTED" });
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
    vi.mocked(useExportApprovals).mockReturnValue(query(approvals) as never);
    vi.mocked(useRequestExportApproval).mockReturnValue({
      mutateAsync: requestApproval,
      isPending: false,
    } as never);
    vi.mocked(useReviewExportApproval).mockReturnValue({
      mutateAsync: reviewApproval,
      isPending: false,
    } as never);
    vi.mocked(useCompleteApprovedExportJob).mockReturnValue({
      mutateAsync: completeApproval,
      isPending: false,
    } as never);
    vi.mocked(useLargeListExportJob).mockReturnValue({ mutateAsync: pollExport } as never);
    vi.mocked(useSubmitLargeListExport).mockReturnValue({ mutateAsync: submitExport } as never);
    vi.mocked(useVerifyEvidence).mockReturnValue({
      mutateAsync: verifyEvidence,
      isPending: false,
    } as never);
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

  it("uses backend cursors and public audit fields instead of client-side pagination", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    expect(screen.getByText("auditor-1")).toBeInTheDocument();
    expect(screen.getByText("EXPORT")).toBeInTheDocument();
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
      ]),
    ).toEqual({
      action: "EXPORT",
      actorUserId: "auditor-1",
      resourceType: "audit",
      outcome: "FAILED",
      from,
      to,
    });

    const user = userEvent.setup();
    render(<AdminAudit />);
    expect(screen.getAllByLabelText("发生日期")).toHaveLength(2);
    expect(screen.queryByLabelText("对象类型")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "专家模式" }));
    fireEvent.change(screen.getByLabelText("对象类型"), {
      target: { value: "audit" },
    });
    await user.click(screen.getByRole("combobox", { name: "执行结果" }));
    await user.click(await screen.findByText("失败"));

    await waitFor(() =>
      expect(useLargeAuditEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ resourceType: "audit", outcome: "FAILED" }),
      ),
    );
  });

  it("opens the persisted audit detail with resource, trace and redacted snapshots", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    await user.click(screen.getByRole("button", { name: "查看详情 evt-7" }));

    expect(screen.getByRole("dialog", { name: "审计事件详情" })).toBeInTheDocument();
    expect(screen.getByText("audit / snapshot-7")).toBeInTheDocument();
    expect(screen.getByText("trace-7")).toBeInTheDocument();
    expect(screen.getByText('{"enabled":true}')).toBeInTheDocument();
    expect(screen.getByText('{"enabled":false}')).toBeInTheDocument();
  });

  it("requests approval for the current filtered audit scope", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    fireEvent.change(screen.getByLabelText("操作编码"), {
      target: { value: "EXPORT" },
    });
    await waitFor(() =>
      expect(useLargeAuditEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ action: "EXPORT" }),
      ),
    );

    await user.click(screen.getByRole("button", { name: "申请导出" }));
    await user.clear(screen.getByLabelText("申请理由"));
    await user.type(screen.getByLabelText("申请理由"), "复核当前导出操作");
    await user.click(screen.getByRole("button", { name: "提交导出申请" }));

    await waitFor(() =>
      expect(requestApproval).toHaveBeenCalledWith(
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
    expect(submitExport).not.toHaveBeenCalled();
  });

  it("allows another auditor to review while blocking self approval", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    await user.click(screen.getByRole("tab", { name: "导出审批" }));
    expect(screen.getByRole("button", { name: "批准 exp-audit-self" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "批准 exp-audit-other" }));
    fireEvent.change(screen.getByLabelText("审批意见"), {
      target: { value: "审批通过，允许生成文件" },
    });
    await user.click(screen.getByRole("button", { name: "确认审批" }));

    await waitFor(() =>
      expect(reviewApproval).toHaveBeenCalledWith({
        approvalId: "exp-audit-other",
        decision: "APPROVE",
        comment: "审批通过，允许生成文件",
        expectedVersion: 1,
      }),
    );
  });

  it("generates files only for approved requests and completes from the backend job", async () => {
    const user = userEvent.setup();
    submitExport.mockResolvedValue({
      jobId: "job-audit-approved",
      status: "succeeded",
      submittedAt: "2026-06-06T12:07:00Z",
      submittedBy: "auditor-1",
      downloadUrl: "/medkernel/api/v1/large-lists/exports/job-audit-approved/download",
    });
    render(<AdminAudit />);

    await user.click(screen.getByRole("tab", { name: "导出审批" }));
    await user.click(screen.getByRole("button", { name: "生成导出文件 exp-audit-approved" }));
    await user.click(screen.getByRole("button", { name: "确认生成导出文件" }));

    await waitFor(() =>
      expect(submitExport).toHaveBeenCalledWith(
        expect.objectContaining({
          resourceType: "AUDIT_EVENT",
          selectedScope: "filteredResult",
          idempotencyKey: "audit-approved",
          requestSnapshot: expect.objectContaining({
            pageRequest: expect.objectContaining({
              filters: { outcome: "FAILED" },
            }),
          }),
        }),
      ),
    );
    await waitFor(() =>
      expect(completeApproval).toHaveBeenCalledWith({
        approvalId: "exp-audit-approved",
        jobId: "job-audit-approved",
        reason: "复核失败事件",
        expectedVersion: 2,
      }),
    );
  });

  it("shows approval and export evidence and verifies the selected backend evidence", async () => {
    const user = userEvent.setup();
    render(<AdminAudit />);

    await user.click(screen.getByRole("tab", { name: "导出审批" }));
    await user.click(screen.getByRole("button", { name: "查看证据 exp-audit-exported" }));

    expect(screen.getByText("evd-audit-exported-approval")).toBeInTheDocument();
    expect(screen.getByText("evd-audit-exported-file")).toBeInTheDocument();
    expect(screen.getByText("sha256:export-digest")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "验签导出证据" }));

    await waitFor(() => expect(verifyEvidence).toHaveBeenCalledWith("evd-audit-exported-file"));
    expect(await screen.findByText("证据验签通过")).toBeInTheDocument();
    expect(screen.getByText("SM3_WITH_SM2")).toBeInTheDocument();
    expect(screen.getByText("sm3:calculated")).toBeInTheDocument();
    expect(screen.getByText("sm3:stored")).toBeInTheDocument();
  });
});
