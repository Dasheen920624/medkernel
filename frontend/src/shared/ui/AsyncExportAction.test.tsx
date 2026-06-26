import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";

import { AsyncExportAction } from "./AsyncExportAction";
import type { AsyncExportJob, AsyncExportRequest } from "./experienceTypes";

const request: AsyncExportRequest = {
  resourceType: "terminology.mapping",
  requestSnapshot: {
    viewKey: "terminology.mapping",
    filters: [],
    pageRequest: { pageNumber: 1, pageSize: 20, filters: {} },
    visibleColumnKeys: ["status"],
    evidenceDetailsEnabled: false,
    capturedAt: "2026-05-26T00:00:00.000Z",
  },
  selectedScope: "currentPage",
  reason: "实施核查",
};

function renderAction(props: Partial<ComponentProps<typeof AsyncExportAction>> = {}) {
  return render(
    <ConfigProvider>
      <AsyncExportAction enabled permissionGranted request={request} {...props} />
    </ConfigProvider>,
  );
}

async function submitExport() {
  await userEvent.click(screen.getByRole("button", { name: "导出" }));
  await userEvent.click(screen.getByRole("button", { name: "提交导出任务" }));
}

describe("AsyncExportAction", () => {
  it("shows controlled disabled and forbidden states without submitting", async () => {
    const onSubmit = vi.fn();
    const { rerender } = renderAction({
      enabled: false,
      disabledReason: "导出任务暂不可用，请联系信息科确认导出范围。",
      onSubmit,
    });

    expect(screen.getByText("导出任务暂不可用，请联系信息科确认导出范围。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "导出" })).toBeDisabled();

    rerender(
      <ConfigProvider>
        <AsyncExportAction
          enabled
          permissionGranted={false}
          request={request}
          onSubmit={onSubmit}
        />
      </ConfigProvider>,
    );

    expect(screen.getByText("当前权限不足，无法提交导出任务")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("uses business fallback copy when export capability is unavailable", async () => {
    const { rerender } = renderAction({ enabled: false });

    expect(screen.getByText("导出任务暂不可用，请联系信息科确认导出范围。")).toBeInTheDocument();
    expect(screen.queryByText(/接口|接入/)).not.toBeInTheDocument();

    rerender(
      <ConfigProvider>
        <AsyncExportAction enabled permissionGranted request={request} />
      </ConfigProvider>,
    );

    await submitExport();
    expect(screen.getByText("导出服务暂时不可用，请联系信息科确认导出配置。")).toBeInTheDocument();
    expect(screen.queryByText(/尚未接入|接口/)).not.toBeInTheDocument();
  });

  it("polls a pending export until completion and displays audit evidence", async () => {
    const onSubmit = vi.fn().mockResolvedValue({
      jobId: "job-1",
      status: "pending",
      submittedAt: "2026-05-26T01:00:00.000Z",
      submittedBy: "tester",
      traceId: "trace-1",
    });
    const succeededJob: AsyncExportJob = {
      jobId: "job-1",
      status: "succeeded",
      submittedAt: "2026-05-26T01:00:00.000Z",
      submittedBy: "tester",
      traceId: "trace-1",
      auditId: "audit-1",
      downloadUrl: "/exports/job-1",
    };
    const onPoll = vi.fn().mockResolvedValueOnce({
      jobId: "job-1",
      status: "running",
      submittedAt: "2026-05-26T01:00:00.000Z",
      submittedBy: "tester",
      traceId: "trace-1",
    } satisfies AsyncExportJob);
    onPoll.mockResolvedValue(succeededJob);
    renderAction({ onSubmit, onPoll, pollDelayMs: 1 });

    await submitExport();
    await waitFor(() => expect(onPoll.mock.calls.length).toBeGreaterThanOrEqual(2));

    expect(await screen.findByText("导出已完成")).toBeInTheDocument();
    expect(screen.getByText(/job-1/)).toBeInTheDocument();
    expect(screen.getByText(/trace-1/)).toBeInTheDocument();
    expect(screen.getByText(/audit-1/)).toBeInTheDocument();
    expect(onPoll).toHaveBeenCalledWith("job-1");
  });

  it("retries a failed submission using the original snapshot", async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValueOnce(new Error("GET /api/v1/exports failed: ECONNREFUSED 127.0.0.1:8080"))
      .mockResolvedValueOnce({
        jobId: "job-2",
        status: "succeeded",
        submittedAt: "2026-05-26T01:00:00.000Z",
        submittedBy: "tester",
      });
    renderAction({ onSubmit });

    await submitExport();
    expect(await screen.findByText(/导出服务暂时不可用，请重试或联系信息科/)).toBeInTheDocument();
    expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/exports/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "重试导出" }));

    expect(await screen.findByText("导出已完成")).toBeInTheDocument();
    expect(onSubmit).toHaveBeenNthCalledWith(1, expect.objectContaining(request));
    expect(onSubmit).toHaveBeenNthCalledWith(2, expect.objectContaining(request));
    expect(onSubmit.mock.calls[0][0].idempotencyKey).toBeDefined();
    expect(onSubmit.mock.calls[1][0].idempotencyKey).toBe(onSubmit.mock.calls[0][0].idempotencyKey);
  });

  it("keeps failed job reason in hospital language", async () => {
    const onSubmit = vi.fn().mockResolvedValue({
      jobId: "job-3",
      status: "failed",
      submittedAt: "2026-05-26T01:00:00.000Z",
      submittedBy: "tester",
      failureReason: "GET /api/v1/exports/job-3 failed: SQLException stack trace",
    } satisfies AsyncExportJob);
    renderAction({ onSubmit });

    await submitExport();

    expect(await screen.findByText("导出任务失败")).toBeInTheDocument();
    expect(screen.getByText(/导出任务未完成，请重试或联系信息科/)).toBeInTheDocument();
    expect(screen.queryByText(/SQLException/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/exports/)).not.toBeInTheDocument();
  });
});
