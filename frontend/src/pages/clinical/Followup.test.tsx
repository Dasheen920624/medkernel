import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import Followup from "./Followup";

const followupHookMocks = vi.hoisted(() => ({
  generatePlan: vi.fn(),
  refetchPlans: vi.fn(),
  reportAbnormal: vi.fn(),
  submitQuestionnaire: vi.fn(),
  useFollowupPlans: vi.fn(),
  useGenerateFollowupPlan: vi.fn(),
  useReportFollowupAbnormal: vi.fn(),
  useSubmitFollowupQuestionnaire: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useFollowupPlans: followupHookMocks.useFollowupPlans,
  useGenerateFollowupPlan: followupHookMocks.useGenerateFollowupPlan,
  useReportFollowupAbnormal: followupHookMocks.useReportFollowupAbnormal,
  useSubmitFollowupQuestionnaire: followupHookMocks.useSubmitFollowupQuestionnaire,
}));

function renderFollowup() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <Followup />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("Followup", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    followupHookMocks.generatePlan.mockResolvedValue({ planId: "plan-real-1" });
    followupHookMocks.reportAbnormal.mockResolvedValue({
      eventId: "event-return-1",
      returnTaskId: "return-task-1",
      notificationEventId: "notify-event-1",
      traceId: "trace-followup-1",
    });
    followupHookMocks.submitQuestionnaire.mockResolvedValue({});
    followupHookMocks.useFollowupPlans.mockReturnValue({
      data: {
        items: [
          {
            planId: "plan-real-1",
            tenantId: "tenant-A",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            diseaseCode: "COPD",
            status: "ACTIVE",
            tasks: [
              {
                taskId: "task-questionnaire-1",
                taskType: "QUESTIONNAIRE",
                dueDate: "2026-06-08T00:00:00Z",
                status: "PENDING",
              },
              {
                taskId: "task-lab-1",
                taskType: "LAB",
                dueDate: "2026-06-09T00:00:00Z",
                status: "COMPLETED",
              },
            ],
          },
        ],
        page: 1,
        size: 100,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: followupHookMocks.refetchPlans,
    });
    followupHookMocks.useGenerateFollowupPlan.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.generatePlan,
    });
    followupHookMocks.useReportFollowupAbnormal.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.reportAbnormal,
    });
    followupHookMocks.useSubmitFollowupQuestionnaire.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.submitQuestionnaire,
    });
  });

  it("labels progress metrics as current-page statistics", () => {
    renderFollowup();

    expect(screen.getByText("当前页随访计划数")).toBeInTheDocument();
    expect(screen.getByText("当前页执行中计划")).toBeInTheDocument();
    expect(screen.getByText("当前页已完成任务")).toBeInTheDocument();
    expect(screen.getByText("当前页任务完成率")).toBeInTheDocument();
  });

  it("shows abnormal return task and notification evidence returned by the API", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("button", { name: /查看与办理/ }));
    await screen.findByText("异常事件上报");
    await user.click(screen.getByLabelText("严重性"));
    await user.click(await screen.findByText("高风险"));
    await user.type(screen.getByLabelText("异常表现"), "患者随访反馈呼吸困难加重");
    await user.type(screen.getByLabelText("处理建议"), "安排回院复核并通知责任医生");
    await user.click(screen.getByRole("button", { name: /上报异常事件/ }));

    await waitFor(() => expect(followupHookMocks.reportAbnormal).toHaveBeenCalledTimes(1));
    const request = followupHookMocks.reportAbnormal.mock.calls[0][0];
    const payload = JSON.parse(request.payload);
    expect(request).toMatchObject({
      planId: "plan-real-1",
      eventType: "ABNORMAL_RETURN",
    });
    expect(payload).toMatchObject({
      severity: "HIGH",
      symptoms: "患者随访反馈呼吸困难加重",
      remark: "安排回院复核并通知责任医生",
    });

    expect(await screen.findByText("回院任务 return-task-1")).toBeInTheDocument();
    expect(screen.getByText("通知事件 notify-event-1")).toBeInTheDocument();
    expect(screen.getByText("追踪链路 trace-followup-1")).toBeInTheDocument();
    expect(screen.getByText("异常事件 event-return-1")).toBeInTheDocument();
  });
});
