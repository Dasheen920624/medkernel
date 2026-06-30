import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

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
  useCurrentHospitalRuntime: vi.fn(),
  useGenerateFollowupPlan: vi.fn(),
  usePublishFollowupTemplate: vi.fn(),
  useReportFollowupAbnormal: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSubmitFollowupQuestionnaire: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useCreateFollowupTemplate: followupHookMocks.useCreateFollowupTemplate,
  useFollowupStats: followupHookMocks.useFollowupStats,
  useFollowupPlans: followupHookMocks.useFollowupPlans,
  useFollowupTemplates: followupHookMocks.useFollowupTemplates,
  useContextSnapshotDetail: followupHookMocks.useContextSnapshotDetail,
  useContextSnapshots: followupHookMocks.useContextSnapshots,
  useCurrentHospitalRuntime: followupHookMocks.useCurrentHospitalRuntime,
  useGenerateFollowupPlan: followupHookMocks.useGenerateFollowupPlan,
  usePublishFollowupTemplate: followupHookMocks.usePublishFollowupTemplate,
  useReportFollowupAbnormal: followupHookMocks.useReportFollowupAbnormal,
  useSecurityProfile: followupHookMocks.useSecurityProfile,
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

function grantFollowupPublishPermission() {
  const current = followupHookMocks.useSecurityProfile();
  followupHookMocks.useSecurityProfile.mockReturnValue({
    ...current,
    data: {
      ...current.data,
      roles: [
        {
          code: "engine-operator",
          displayName: "医疗引擎运营员",
          source: "PLATFORM_SEED",
          scopeLevel: "HOSPITAL",
          scopeCode: "hospital-A",
        },
      ],
      permissions: [
        ...current.data.permissions,
        {
          code: "followup.publish",
          dimension: "ACTION",
          target: "FOLLOWUP",
          displayName: "发布随访模板版本",
          risk: "HIGH",
        },
      ],
    },
  });
}

function grantRuntimeReadPermission() {
  const current = followupHookMocks.useSecurityProfile();
  followupHookMocks.useSecurityProfile.mockReturnValue({
    ...current,
    data: {
      ...current.data,
      permissions: [
        ...current.data.permissions,
        {
          code: "asset.read",
          dimension: "ACTION",
          target: "ASSET",
          displayName: "查看值集、计算公式、医嘱套餐与临床提示卡",
          risk: "LOW",
        },
      ],
    },
  });
}

describe("Followup", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useEvidenceDetailsStore.getState().setEnabled(false);
    followupHookMocks.useSecurityProfile.mockReturnValue({
      data: {
        userId: "doctor-1",
        username: "doctor",
        roles: [
          {
            code: "clinical-user",
            displayName: "临床使用者",
            source: "PLATFORM_SEED",
            scopeLevel: "DEPARTMENT",
            scopeCode: "respiratory",
          },
        ],
        permissions: [],
        menuKeys: ["clinical-followup"],
        environmentKeys: ["prod"],
        dataScope: {
          tenantId: "tenant-A",
          groupId: null,
          hospitalId: "hospital-A",
          campusId: null,
          siteId: null,
          departmentId: "respiratory",
          specialtyId: null,
        },
        mustChangePwd: false,
        mfaRequired: false,
        mfaBound: false,
        mfaVerified: true,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
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
              runtimeReleaseId: "runtime-release-followup",
              qualityStatus: "VALID",
              missingFields: [],
              mappingStatus: {},
            }
          : undefined,
      isLoading: false,
      isError: false,
    }));
    followupHookMocks.useCurrentHospitalRuntime.mockReturnValue({
      data: {
        release: {
          releaseId: "runtime-release-followup",
          tenantId: "tenant-A",
          hospitalId: "hospital-A",
          revisionNo: 1,
          platformBaselineReleaseId: "baseline-A8",
          manifestSha256: "a".repeat(64),
          activatedAt: "2026-06-30T00:00:00Z",
          activatedBy: "engine-operator",
        },
        items: [],
      },
      isLoading: false,
      isError: false,
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

  it("renders current-organization progress metrics instead of current-page counts", () => {
    renderFollowup();

    expect(screen.getByRole("heading", { name: "随访协同" })).toBeInTheDocument();
    expect(
      screen.getByText(/按当前组织范围查看随访计划、患者问卷回收、护士代填和异常回院处理/),
    ).toBeInTheDocument();
    expect(screen.getByText("随访办理边界")).toBeInTheDocument();
    expect(screen.getByText("护士代填办理")).toBeInTheDocument();
    expect(screen.getByText("患者自填")).toBeInTheDocument();
    expect(screen.getByText(/患者报告/)).toBeInTheDocument();
    expect(screen.getByText("异常回院处理")).toBeInTheDocument();
    expect(screen.getByText("当前范围随访计划")).toBeInTheDocument();
    expect(screen.getByText("当前范围执行中计划")).toBeInTheDocument();
    expect(screen.getByText("当前范围已完成任务")).toBeInTheDocument();
    expect(screen.getByText("当前范围任务完成率")).toBeInTheDocument();
    expect(screen.getByText("当前范围异常回院率")).toBeInTheDocument();
    expect(screen.queryByText(/作用域/)).not.toBeInTheDocument();
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

  it("普通临床用户未进入生成计划时不请求发布治理当前版本", () => {
    renderFollowup();

    expect(followupHookMocks.useCurrentHospitalRuntime).toHaveBeenCalledWith(undefined);
  });

  it("默认用临床业务语言展示随访计划并收起低频证据", async () => {
    const user = userEvent.setup();
    renderFollowup();

    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByText("慢阻肺")).toBeInTheDocument();
    expect(screen.getByText("慢阻肺出院随访 · v1")).toBeInTheDocument();
    expect(screen.getByText("患者已关联")).toBeInTheDocument();
    expect(screen.getByText("就诊已关联")).toBeInTheDocument();
    expect(screen.queryByText("plan-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("patient-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("enc-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("ftpl-1")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /查看与办理/ }));
    await screen.findByText("随访任务");
    expect(screen.getByRole("dialog", { name: "随访计划办理" })).toBeInTheDocument();
    expect(screen.getAllByText("患者已关联").length).toBeGreaterThan(0);
    expect(screen.getAllByText("就诊已关联").length).toBeGreaterThan(0);
    expect(screen.getAllByText("慢阻肺").length).toBeGreaterThan(0);
    expect(screen.getAllByText("慢阻肺出院随访 · v1").length).toBeGreaterThan(0);
    expect(screen.getByText("第 1 项")).toBeInTheDocument();
    expect(screen.getByText("问卷回收")).toBeInTheDocument();
    expect(screen.queryByText("task-questionnaire-1")).not.toBeInTheDocument();
    expect(screen.queryByText("FOLLOWUP_QUESTIONNAIRE_DEFAULT")).not.toBeInTheDocument();
  });

  it("证据详情打开后可追溯随访计划、患者、模板和任务原始标识", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("plan-real-1")).toBeInTheDocument();
    expect(screen.getByText("patient-real-1")).toBeInTheDocument();
    expect(screen.getByText("enc-real-1")).toBeInTheDocument();
    expect(screen.getByText("ftpl-1 · v1")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /查看与办理/ }));
    await screen.findByText("随访任务");
    expect(screen.getByText("task-questionnaire-1")).toBeInTheDocument();
    expect(screen.getByText("FOLLOWUP_QUESTIONNAIRE_DEFAULT")).toBeInTheDocument();
  });

  it("keeps follow-up read failures in hospital language", () => {
    followupHookMocks.useFollowupPlans.mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
      refetch: followupHookMocks.refetchPlans,
    });

    renderFollowup();

    expect(screen.getByText("随访计划读取失败")).toBeInTheDocument();
    expect(
      screen.getByText("请确认登录状态、组织范围；若持续失败，请联系信息科核查随访服务。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/数据读取服务/)).not.toBeInTheDocument();
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

    await user.click(screen.getByRole("tab", { name: "随访模板" }));
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
    const generateDialog = screen.getByRole("dialog", { name: "生成随访计划" });
    await user.click(within(generateDialog).getByLabelText("随访模板"));
    await user.type(within(generateDialog).getByLabelText("随访模板"), "FUP.COPD.2026");

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

  it("默认用业务结果展示异常回院登记证据", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("button", { name: /查看与办理/ }));
    await screen.findByText("异常回院登记");
    await user.click(screen.getByLabelText("回院风险等级"));
    await user.click(await screen.findByText("高风险"));
    fireEvent.change(screen.getByLabelText("异常症状或情况"), {
      target: { value: "患者随访反馈呼吸困难加重" },
    });
    fireEvent.change(screen.getByLabelText("医护处理建议"), {
      target: { value: "安排回院复核并通知责任医生" },
    });
    await user.click(screen.getByRole("button", { name: /登记异常回院/ }));

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

    expect(await screen.findByText("异常记录已登记")).toBeInTheDocument();
    expect(screen.getByText("回院任务已生成")).toBeInTheDocument();
    expect(screen.getByText("通知已发送")).toBeInTheDocument();
    expect(screen.getByText("追踪已记录")).toBeInTheDocument();
    expect(screen.queryByText("return-task-1")).not.toBeInTheDocument();
    expect(screen.queryByText("notify-event-1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-followup-1")).not.toBeInTheDocument();
    expect(screen.queryByText("event-return-1")).not.toBeInTheDocument();
    expect(screen.queryByText(/异常事件|上报/)).not.toBeInTheDocument();
  });

  it("证据详情打开后展示异常回院登记的完整追溯编号", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));
    await user.click(screen.getByRole("button", { name: /查看与办理/ }));
    await screen.findByText("异常回院登记");
    await user.click(screen.getByLabelText("回院风险等级"));
    await user.click(await screen.findByText("高风险"));
    fireEvent.change(screen.getByLabelText("异常症状或情况"), {
      target: { value: "患者随访反馈呼吸困难加重" },
    });
    fireEvent.change(screen.getByLabelText("医护处理建议"), {
      target: { value: "安排回院复核并通知责任医生" },
    });
    await user.click(screen.getByRole("button", { name: /登记异常回院/ }));

    expect(await screen.findByText("回院任务 return-task-1")).toBeInTheDocument();
    expect(screen.getByText("通知记录 notify-event-1")).toBeInTheDocument();
    expect(screen.getByText("追踪号 trace-followup-1")).toBeInTheDocument();
    expect(screen.getByText("异常记录 event-return-1")).toBeInTheDocument();
  });

  it("records who submitted the follow-up questionnaire instead of assuming a physician", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("button", { name: /查看与办理/ }));
    await screen.findByText("问卷回收");
    await user.click(screen.getByRole("button", { name: /填\s*报/ }));
    await user.click(screen.getByLabelText("提交来源"));
    await user.click(
      await screen.findByText("护士代填", { selector: ".ant-select-item-option-content" }),
    );
    fireEvent.change(screen.getByLabelText("问卷回收内容"), {
      target: { value: "患者电话随访反馈今日咳嗽减轻，护士代为录入。" },
    });
    await user.click(screen.getByRole("button", { name: /提交问卷/ }));

    await waitFor(() =>
      expect(followupHookMocks.submitQuestionnaire).toHaveBeenCalledWith(
        expect.objectContaining({
          taskId: "task-questionnaire-1",
          questionnaireTemplateId: "FOLLOWUP_QUESTIONNAIRE_DEFAULT",
          executorType: "NURSE",
        }),
      ),
    );
    expect(followupHookMocks.submitQuestionnaire).not.toHaveBeenCalledWith(
      expect.objectContaining({ executorType: "PHYSICIAN" }),
    );
  });

  it("generates a plan from an ACTIVE context snapshot instead of typed patient facts", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("button", { name: /生成随访计划/ }));
    expect(screen.getByLabelText("随访快照患者信息")).toBeInTheDocument();
    expect(screen.getByLabelText("随访快照就诊信息")).toBeInTheDocument();
    expect(screen.queryByLabelText("随访快照患者标识")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("随访快照就诊标识")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入患者信息检索已生效快照")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("可单独按就诊信息检索")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("输入患者标识检索已生效快照")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("可单独按就诊标识检索")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("随访快照患者信息"), {
      target: { value: "patient-real-1" },
    });
    await user.click(screen.getByRole("button", { name: "选择第 1 个随访上下文快照" }));
    await user.click(screen.getByLabelText("随访风险分层"));
    await user.click(screen.getByText("高风险"));
    await user.click(screen.getByLabelText("随访模板"));
    await user.click(
      await screen.findByText("慢阻肺出院随访 · v1", {
        selector: ".ant-select-item-option-content",
      }),
    );
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
    expect(screen.queryByLabelText("随访快照患者信息")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("随访快照就诊信息")).not.toBeInTheDocument();
  });

  it("提醒旧机构生效版本快照不会自动套用新发布模板", async () => {
    const user = userEvent.setup();
    grantRuntimeReadPermission();
    followupHookMocks.useCurrentHospitalRuntime.mockReturnValue({
      data: {
        release: {
          releaseId: "runtime-release-current",
          tenantId: "tenant-A",
          hospitalId: "hospital-A",
          revisionNo: 2,
          platformBaselineReleaseId: "baseline-A8",
          manifestSha256: "b".repeat(64),
          activatedAt: "2026-06-30T01:00:00Z",
          activatedBy: "engine-operator",
        },
        items: [],
      },
      isLoading: false,
      isError: false,
    });
    renderFollowup();

    await user.click(screen.getByRole("button", { name: /生成随访计划/ }));
    fireEvent.change(screen.getByLabelText("随访快照患者信息"), {
      target: { value: "patient-real-1" },
    });
    await user.click(screen.getByRole("button", { name: "选择第 1 个随访上下文快照" }));

    expect(await screen.findByText("所选快照不是当前机构生效版本")).toBeInTheDocument();
    expect(screen.getByText(/新发布的随访模板不会自动套用到旧快照/)).toBeInTheDocument();
  });

  it("shows followup templates and publishes templates with current product wording", async () => {
    const user = userEvent.setup();
    grantFollowupPublishPermission();
    renderFollowup();

    await user.click(screen.getByRole("tab", { name: "随访模板" }));

    expect(screen.getByText("慢阻肺出院随访")).toBeInTheDocument();
    expect(screen.getAllByText("待发布模板").length).toBeGreaterThan(0);
    expect(screen.getAllByText("随访模板").length).toBeGreaterThan(0);
    expect(
      screen.getByText("模板发布后才能用于生成随访计划，已生成计划继续保留原模板版本。"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /发布模板/ }));

    await waitFor(() =>
      expect(followupHookMocks.publishTemplate).toHaveBeenCalledWith({
        templateId: "ftpl-draft",
        request: {
          impactDigest: "仅影响新生成随访计划：FUP.DRAFT@v1",
          reason: "随访模板发布",
        },
      }),
    );
    expect(screen.queryByText(/第一阶段|模板治理|随访模板资产|运行期/)).not.toBeInTheDocument();
  });

  it("临床使用者只能创建随访模板草稿，不能看到会触发权限失败的发布动作", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("tab", { name: "随访模板" }));

    const row = screen.getByRole("row", { name: /待发布模板/ });
    expect(within(row).queryByRole("button", { name: /发布模板/ })).not.toBeInTheDocument();
    expect(within(row).getByText("需运营发布")).toBeInTheDocument();
    expect(within(row).getByText("医疗引擎运营员复核后用于新计划")).toBeInTheDocument();
    expect(followupHookMocks.publishTemplate).not.toHaveBeenCalled();
  });

  it("创建随访模板时使用业务选项生成可审计的标准契约", async () => {
    const user = userEvent.setup();
    renderFollowup();

    await user.click(screen.getByRole("tab", { name: "随访模板" }));
    await user.click(screen.getByRole("button", { name: /新建模板/ }));
    const dialog = screen.getByRole("dialog", { name: "新建随访模板" });

    expect(screen.queryByLabelText("版本")).not.toBeInTheDocument();
    expect(within(dialog).getByText("方案与适用范围")).toBeInTheDocument();
    expect(within(dialog).getByText("问卷与异常处理")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("院内随访方案身份")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("适用机构范围")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("随访病种")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("问卷内容模板")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("核心随访问题")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("院内依据")).toBeInTheDocument();
    expect(screen.queryByLabelText("模板编码")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("组织范围")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("适用范围")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("问卷模板标识")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("问题标识")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("院内方案编号")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("问卷模板 ID")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("问题编码")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("来源引用")).not.toBeInTheDocument();
    expect(screen.queryByText(/标准编码/)).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/FUP\\.COPD/)).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("例如 慢阻肺出院随访-2026")).toBeInTheDocument();
    expect(screen.queryByText("p5-hospital")).not.toBeInTheDocument();
    expect(screen.queryByText("FOLLOWUP_QUESTIONNAIRE_DEFAULT")).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue("FIRST_PHASE_FOLLOWUP_TEMPLATE")).not.toBeInTheDocument();

    await user.type(within(dialog).getByLabelText("院内随访方案身份"), "FUP.COPD.REAL");
    await user.type(within(dialog).getByLabelText("模板名称"), "慢阻肺真实随访方案");
    await user.type(
      within(dialog).getByLabelText("模板说明"),
      "面向出院后慢阻肺患者的护士回收与医生复核流程",
    );
    await user.click(within(dialog).getByLabelText("问卷内容模板"));
    await user.click(await screen.findByText("真实前台慢病随访问卷"));
    await user.click(within(dialog).getByLabelText("院内依据"));
    await user.click(await screen.findByText("真实前台演练随访制度"));

    await user.click(within(dialog).getByRole("button", { name: /创\s*建/ }));

    await waitFor(() => expect(followupHookMocks.createTemplate).toHaveBeenCalledTimes(1));
    expect(followupHookMocks.createTemplate).toHaveBeenCalledWith(
      expect.objectContaining({
        templateCode: "FUP.COPD.REAL",
        name: "慢阻肺真实随访方案",
        description: "面向出院后慢阻肺患者的护士回收与医生复核流程",
        organizationScope: "p5-hospital",
        applicableScope: "COPD",
        sourceRef: "REAL_FRONTDESK_FOLLOWUP_TEMPLATE",
      }),
    );
    const request = followupHookMocks.createTemplate.mock.calls[0][0];
    expect(request.tasks).toEqual([
      {
        taskType: "QUESTIONNAIRE",
        delayDays: 7,
        questionnaireTemplateId: "FOLLOWUP_QUESTIONNAIRE_REAL_FRONTDESK",
      },
      {
        taskType: "OUTPATIENT",
        delayDays: 14,
      },
    ]);
    expect(JSON.parse(request.questionnaireDefinition)).toEqual({
      questions: [
        {
          code: "dyspnea",
          type: "TEXT",
          required: true,
        },
      ],
    });
  });
});
