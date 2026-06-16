import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import Followup from "./Followup";

const followupHookMocks = vi.hoisted(() => ({
  createTemplate: vi.fn(),
  generatePlan: vi.fn(),
  publishTemplate: vi.fn(),
  refetchPlans: vi.fn(),
  reportAbnormal: vi.fn(),
  submitQuestionnaire: vi.fn(),
  useCreateFollowupTemplate: vi.fn(),
  useFollowupStats: vi.fn(),
  useFollowupPlans: vi.fn(),
  useFollowupTemplates: vi.fn(),
  useContextSnapshotDetail: vi.fn(),
  useContextSnapshots: vi.fn(),
  useGenerateFollowupPlan: vi.fn(),
  usePublishFollowupTemplate: vi.fn(),
  useReportFollowupAbnormal: vi.fn(),
  useSubmitFollowupQuestionnaire: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useCreateFollowupTemplate: followupHookMocks.useCreateFollowupTemplate,
  useFollowupStats: followupHookMocks.useFollowupStats,
  useFollowupPlans: followupHookMocks.useFollowupPlans,
  useFollowupTemplates: followupHookMocks.useFollowupTemplates,
  useContextSnapshotDetail: followupHookMocks.useContextSnapshotDetail,
  useContextSnapshots: followupHookMocks.useContextSnapshots,
  useGenerateFollowupPlan: followupHookMocks.useGenerateFollowupPlan,
  usePublishFollowupTemplate: followupHookMocks.usePublishFollowupTemplate,
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
    followupHookMocks.createTemplate.mockResolvedValue({
      templateId: "ftpl-new",
      templateCode: "FUP.COPD.NEW",
      versionNo: 1,
      name: "慢阻肺复诊模板",
      assetStatus: "DRAFT",
      tasks: [],
      traceId: "trace-template-new",
    });
    followupHookMocks.generatePlan.mockResolvedValue({ planId: "plan-real-1" });
    followupHookMocks.publishTemplate.mockResolvedValue({
      templateId: "ftpl-1",
      assetStatus: "PUBLISHED",
      traceId: "trace-template-published",
    });
    followupHookMocks.reportAbnormal.mockResolvedValue({
      eventId: "event-return-1",
      returnTaskId: "return-task-1",
      notificationEventId: "notify-event-1",
      traceId: "trace-followup-1",
    });
    followupHookMocks.submitQuestionnaire.mockResolvedValue({});
    followupHookMocks.useFollowupStats.mockReturnValue({
      data: {
        totalPlans: 12,
        activePlans: 8,
        totalTasks: 34,
        completedTasks: 21,
        abnormalReturnTasks: 5,
        taskCompletionRatePercent: 61.8,
        abnormalReturnRatePercent: 14.7,
        traceId: "trace-followup-stats",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    followupHookMocks.useFollowupPlans.mockReturnValue({
      data: {
        items: [
          {
            planId: "plan-real-1",
            tenantId: "tenant-A",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            diseaseCode: "COPD",
            templateId: "ftpl-1",
            templateVersion: 1,
            status: "ACTIVE",
            tasks: [
              {
                taskId: "task-questionnaire-1",
                taskType: "QUESTIONNAIRE",
                dueDate: "2026-06-08T00:00:00Z",
                status: "PENDING",
                questionnaireTemplateId: "FOLLOWUP_QUESTIONNAIRE_DEFAULT",
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
    followupHookMocks.useFollowupTemplates.mockReturnValue({
      data: {
        items: [
          {
            templateId: "ftpl-1",
            templateCode: "FUP.COPD",
            versionNo: 1,
            name: "慢阻肺出院随访",
            description: "出院后问卷与复诊随访",
            organizationScope: "p5-hospital",
            applicableScope: "COPD",
            questionnaireDefinition: "{}",
            abnormalActionDefinition: "{}",
            assetStatus: "PUBLISHED",
            contentHash: "sm3:published-template",
            tasks: [
              {
                taskType: "QUESTIONNAIRE",
                delayDays: 7,
                questionnaireTemplateId: "FOLLOWUP_QUESTIONNAIRE_DEFAULT",
              },
            ],
            traceId: "trace-template-1",
          },
          {
            templateId: "ftpl-draft",
            templateCode: "FUP.DRAFT",
            versionNo: 1,
            name: "待发布模板",
            organizationScope: "p5-hospital",
            applicableScope: "COPD",
            questionnaireDefinition: "{}",
            abnormalActionDefinition: "{}",
            assetStatus: "DRAFT",
            contentHash: "sm3:draft-template",
            tasks: [],
            traceId: "trace-template-draft",
          },
        ],
        page: 1,
        size: 100,
        total: 2,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    followupHookMocks.useCreateFollowupTemplate.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.createTemplate,
    });
    followupHookMocks.useGenerateFollowupPlan.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.generatePlan,
    });
    followupHookMocks.usePublishFollowupTemplate.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.publishTemplate,
    });
    followupHookMocks.useContextSnapshots.mockReturnValue({
      data: {
        items: [
          {
            snapshotId: "snapshot-followup-1",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            status: "ACTIVE",
            qualityStatus: "VALID",
          },
        ],
      },
      isLoading: false,
      isError: false,
    });
    followupHookMocks.useContextSnapshotDetail.mockImplementation((snapshotId: string) => ({
      data:
        snapshotId === "snapshot-followup-1"
          ? {
              snapshotId,
              status: "ACTIVE",
              packageVersion: "2026.06",
              qualityStatus: "VALID",
              missingFields: [],
              mappingStatus: {},
            }
          : undefined,
      isLoading: false,
      isError: false,
    }));
    followupHookMocks.useReportFollowupAbnormal.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.reportAbnormal,
    });
    followupHookMocks.useSubmitFollowupQuestionnaire.mockReturnValue({
      isPending: false,
      mutateAsync: followupHookMocks.submitQuestionnaire,
    });
  });

  it("renders server-side scoped progress metrics instead of current-page counts", () => {
    renderFollowup();

    expect(screen.getByText("作用域随访计划数")).toBeInTheDocument();
    expect(screen.getByText("作用域执行中计划")).toBeInTheDocument();
    expect(screen.getByText("作用域已完成任务")).toBeInTheDocument();
    expect(screen.getByText("作用域任务完成率")).toBeInTheDocument();
    expect(screen.getByText("作用域异常回院率")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("21")).toBeInTheDocument();
    expect(
      screen.getByText((_content, element) => element?.textContent === "61.8%"),
    ).toBeInTheDocument();
    expect(
      screen.getByText((_content, element) => element?.textContent === "14.7%"),
    ).toBeInTheDocument();
  });

  it("loads follow-up plans through server-side table pagination", async () => {
    const user = userEvent.setup();
    followupHookMocks.useFollowupPlans.mockReturnValue({
      data: {
        items: [
          {
            planId: "plan-real-1",
            tenantId: "tenant-A",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            diseaseCode: "COPD",
            templateId: "ftpl-1",
            templateVersion: 1,
            status: "ACTIVE",
            tasks: [],
          },
        ],
        page: 1,
        size: 20,
        total: 41,
        hasNext: true,
      },
      isError: false,
      isLoading: false,
      refetch: followupHookMocks.refetchPlans,
    });

    renderFollowup();

    expect(followupHookMocks.useFollowupPlans).toHaveBeenCalledWith({
      patientId: undefined,
      page: 1,
      size: 20,
    });

    await user.click(screen.getByTitle("2"));

    await waitFor(() => {
      expect(followupHookMocks.useFollowupPlans).toHaveBeenLastCalledWith({
        patientId: undefined,
        page: 2,
        size: 20,
      });
    });
  });

  it("loads follow-up templates through server-side pagination and published-template search", async () => {
    const user = userEvent.setup();
    followupHookMocks.useFollowupTemplates.mockReturnValue({
      data: {
        items: [
          {
            templateId: "ftpl-1",
            templateCode: "FUP.COPD",
            versionNo: 1,
            name: "慢阻肺出院随访",
            description: "出院后问卷与复诊随访",
            organizationScope: "p5-hospital",
            applicableScope: "COPD",
            questionnaireDefinition: "{}",
            abnormalActionDefinition: "{}",
            assetStatus: "PUBLISHED",
            contentHash: "sm3:published-template",
            tasks: [],
            traceId: "trace-template-1",
          },
        ],
        page: 1,
        size: 20,
        total: 41,
        hasNext: true,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderFollowup();

    expect(followupHookMocks.useFollowupTemplates).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      sort: "updatedAt,desc",
    });
    expect(followupHookMocks.useFollowupTemplates).toHaveBeenCalledWith({
      assetStatus: "PUBLISHED",
      page: 1,
      size: 20,
      sort: "updatedAt,desc",
    });

    await user.click(screen.getByRole("tab", { name: "模板治理" }));
    await user.click(screen.getByTitle("2"));

    await waitFor(() => {
      expect(followupHookMocks.useFollowupTemplates).toHaveBeenCalledWith({
        page: 2,
        size: 20,
        sort: "updatedAt,desc",
      });
    });

    await user.click(screen.getByRole("tab", { name: "计划执行" }));
    await user.click(screen.getByRole("button", { name: /生成随访计划/ }));
    await user.click(screen.getByLabelText("随访模板"));
    await user.type(screen.getByLabelText("随访模板"), "FUP.COPD.2026");

    await waitFor(() => {
      expect(followupHookMocks.useFollowupTemplates).toHaveBeenCalledWith({
        assetStatus: "PUBLISHED",
        keyword: "FUP.COPD.2026",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      });
    });
  });

  it("shows abnormal return task and notification evidence returned by the API", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("button", { name: /查看与办理/ }));
    await screen.findByText("异常事件上报");
    await user.click(screen.getByLabelText("严重性"));
    await user.click(await screen.findByText("高风险"));
    fireEvent.change(screen.getByLabelText("异常表现"), {
      target: { value: "患者随访反馈呼吸困难加重" },
    });
    fireEvent.change(screen.getByLabelText("处理建议"), {
      target: { value: "安排回院复核并通知责任医生" },
    });
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

  it("generates a plan from an ACTIVE context snapshot instead of typed patient facts", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("button", { name: /生成随访计划/ }));
    fireEvent.change(screen.getByLabelText("随访快照患者 ID"), {
      target: { value: "patient-real-1" },
    });
    await user.click(screen.getByRole("button", { name: "选择 snapshot-followup-1" }));
    await user.click(screen.getByLabelText("随访风险分层"));
    await user.click(screen.getByText("高风险"));
    await user.click(screen.getByLabelText("随访模板"));
    await user.click(screen.getByText("慢阻肺出院随访 · v1"));
    await user.click(
      within(screen.getByRole("dialog", { name: "生成随访计划" })).getByRole("button", {
        name: /生 成/,
      }),
    );

    await waitFor(() =>
      expect(followupHookMocks.generatePlan).toHaveBeenCalledWith({
        contextSnapshotId: "snapshot-followup-1",
        templateId: "ftpl-1",
        riskLevel: "HIGH",
        taskTypes: ["QUESTIONNAIRE"],
        idempotencyKey: "followup-plan-snapshot-followup-1-ftpl-1-HIGH-QUESTIONNAIRE",
      }),
    );
    expect(screen.queryByLabelText("患者 ID")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("就诊 ID")).not.toBeInTheDocument();
  });

  it("shows followup template governance and publishes draft templates", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("tab", { name: "模板治理" }));

    expect(screen.getByText("慢阻肺出院随访")).toBeInTheDocument();
    expect(screen.getByText("待发布模板")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /发布模板/ }));

    await waitFor(() =>
      expect(followupHookMocks.publishTemplate).toHaveBeenCalledWith({
        templateId: "ftpl-draft",
        request: {
          impactDigest: "sm3:draft-template",
          reason: "第一阶段随访模板发布",
        },
      }),
    );
  });
});
