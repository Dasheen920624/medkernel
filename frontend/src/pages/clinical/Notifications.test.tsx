import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import Notifications from "./Notifications";

const notificationHookMocks = vi.hoisted(() => ({
  markRead: vi.fn(),
  refetchNotifications: vi.fn(),
  useOrgUnits: vi.fn(),
  useReadWorkflowNotification: vi.fn(),
  useSecurityProfile: vi.fn(),
  useWorkflowNotificationSettings: vi.fn(),
  useWorkflowNotifications: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useOrgUnits: notificationHookMocks.useOrgUnits,
  useReadWorkflowNotification: notificationHookMocks.useReadWorkflowNotification,
  useSecurityProfile: notificationHookMocks.useSecurityProfile,
  useWorkflowNotificationSettings: notificationHookMocks.useWorkflowNotificationSettings,
  useWorkflowNotifications: notificationHookMocks.useWorkflowNotifications,
}));

function renderNotifications() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <Notifications />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("Notifications", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useEvidenceDetailsStore.setState({ enabled: false });
    notificationHookMocks.useSecurityProfile.mockReturnValue({
      data: {
        permissions: [
          { code: "notification.read", dimension: "ACTION", target: "notification" },
          { code: "system.debug", dimension: "ACTION", target: "system" },
        ],
        roles: [{ code: "clinical-user", displayName: "临床使用者", source: "TEST" }],
        menuKeys: ["notifications", "runtime-diagnostics"],
        environmentKeys: ["production"],
        dataScope: { tenantId: "tenant-A" },
      },
    });
    notificationHookMocks.markRead.mockResolvedValue({
      notificationId: "notify-real-1",
      status: "READ",
      readBy: "doctor-real-1",
    });
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-real-1",
            sourceType: "FOLLOWUP_EVENT",
            sourceId: "event-real-1",
            dedupeKey: "followup:event-real-1",
            title: "随访异常通知",
            message: "患者报告呼吸困难，需要处理。",
            level: "HIGH",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            traceId: "trace-notify",
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });
    notificationHookMocks.useReadWorkflowNotification.mockReturnValue({
      isPending: false,
      mutateAsync: notificationHookMocks.markRead,
    });
    notificationHookMocks.useWorkflowNotificationSettings.mockReturnValue({
      data: {
        inAppEnabled: true,
        smsEnabled: false,
        emailEnabled: false,
        pushEnabled: false,
        webhookEnabled: false,
        inHospitalMessageEnabled: false,
        quietHoursEnabled: false,
        quietStart: "22:00",
        quietEnd: "07:00",
        quietBypassLevels: ["CRITICAL", "HIGH"],
        quietActiveNow: false,
        version: 1,
        updatedAt: "2026-06-05T00:00:00Z",
        updatedBy: "doctor-real-1",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    notificationHookMocks.useOrgUnits.mockReturnValue({
      data: {
        items: [
          {
            id: "dept-a",
            parentId: null,
            tenantId: "tenant-A",
            orgPath: "/TENANT-A/DEPT-A",
            level: "DEPARTMENT",
            code: "DEPT-A",
            name: "A 科室",
            status: "ACTIVE",
          },
          {
            id: "spec-a1",
            parentId: "dept-a",
            tenantId: "tenant-A",
            orgPath: "/TENANT-A/DEPT-A/SPEC-A1",
            level: "SPECIALTY",
            code: "SPEC-A1",
            name: "A1 专病",
            status: "ACTIVE",
          },
        ],
        page: 1,
        size: 100,
        total: 2,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
    });
  });

  it("renders real notification rows and removes the previous placeholder", () => {
    renderNotifications();

    expect(notificationHookMocks.useWorkflowNotifications).toHaveBeenCalledWith({
      status: "UNREAD",
      level: undefined,
      page: 1,
      size: 10,
    });
    expect(screen.getByRole("heading", { name: "消息通知" })).toBeInTheDocument();
    expect(screen.getByText("随访异常通知")).toBeInTheDocument();
    expect(screen.getByText("患者报告呼吸困难，需要处理。")).toBeInTheDocument();
    expect(screen.getByText("已关联患者上下文")).toBeInTheDocument();
    expect(screen.queryByText("patient-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("来源编号 event-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("追踪号 trace-notify")).not.toBeInTheDocument();
    expect(screen.queryByText("通知接口尚未接入")).not.toBeInTheDocument();
  });

  it("reveals notification source evidence only after evidence details are enabled", async () => {
    const user = userEvent.setup();
    renderNotifications();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("patient-real-1")).toBeInTheDocument();
    expect(screen.getByText("enc-real-1")).toBeInTheDocument();
    expect(screen.getByText("来源编号 event-real-1")).toBeInTheDocument();
    expect(screen.getByText("追踪号 trace-notify")).toBeInTheDocument();
  });

  it("keeps notification read failures in organization and information-office language", () => {
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();

    expect(screen.getByText("通知读取失败")).toBeInTheDocument();
    expect(
      screen.getByText("请确认登录状态、组织范围；若持续失败，请联系信息科核查通知服务。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/通知服务状态/)).not.toBeInTheDocument();
  });

  it("passes selected organization scope to the server-side notification query", async () => {
    const user = userEvent.setup();
    renderNotifications();

    expect(notificationHookMocks.useOrgUnits).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: "ACTIVE",
    });

    await user.click(screen.getByLabelText("组织范围"));
    await user.click(await screen.findByText("A 科室"));

    await waitFor(() => {
      expect(notificationHookMocks.useWorkflowNotifications).toHaveBeenLastCalledWith({
        status: "UNREAD",
        level: undefined,
        orgUnitId: "dept-a",
        page: 1,
        size: 10,
      });
    });
  });

  it("marks an unread notification as read through the service and refreshes", async () => {
    const user = userEvent.setup();
    renderNotifications();

    await user.click(screen.getByRole("button", { name: "标为已读" }));

    await waitFor(() => {
      expect(notificationHookMocks.markRead).toHaveBeenCalledWith("notify-real-1");
    });
    expect(notificationHookMocks.refetchNotifications).toHaveBeenCalled();
  });

  it("marks all currently loaded unread notifications through service acknowledgements", async () => {
    const user = userEvent.setup();
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-real-1",
            sourceType: "FOLLOWUP_EVENT",
            sourceId: "event-real-1",
            dedupeKey: "followup:event-real-1",
            title: "随访异常通知",
            message: "患者报告呼吸困难，需要处理。",
            level: "HIGH",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
          },
          {
            notificationId: "notify-real-2",
            sourceType: "WORKFLOW_TODO",
            sourceId: "todo-real-2",
            dedupeKey: "todo:todo-real-2",
            title: "安全复核提醒",
            message: "旧版禁忌知识撤回后需要复核。",
            level: "CRITICAL",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
          },
        ],
        page: 0,
        size: 10,
        total: 2,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();
    await user.click(screen.getByRole("button", { name: "全部已读" }));

    await waitFor(() => {
      expect(notificationHookMocks.markRead).toHaveBeenCalledTimes(2);
    });
    expect(notificationHookMocks.markRead).toHaveBeenNthCalledWith(1, "notify-real-1");
    expect(notificationHookMocks.markRead).toHaveBeenNthCalledWith(2, "notify-real-2");
    expect(notificationHookMocks.refetchNotifications).toHaveBeenCalled();
  });

  it("renders workflow todo notifications with a clinical source label instead of raw enum text", () => {
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-real-2",
            sourceType: "WORKFLOW_TODO",
            sourceId: "todo-real-2",
            dedupeKey: "todo:todo-real-2:completed",
            title: "待办已完成",
            message: "安全撤回复核任务已完成。",
            level: "INFO",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();

    expect(screen.getByText("协同待办")).toBeInTheDocument();
    expect(screen.queryByText("WORKFLOW_TODO")).not.toBeInTheDocument();
  });

  it("renders clinical sync event notifications with a clinical source label instead of raw enum text", () => {
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-event-1",
            sourceType: "SYNC_EVENT",
            sourceId: "evt-report-1",
            dedupeKey: "clinical-event:evt-report-1",
            title: "临床同步事件已处理",
            message: "LIS 的报告查看事件已进入临床事件引擎并完成处理。",
            level: "INFO",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();

    expect(screen.getByText("同步事件")).toBeInTheDocument();
    expect(screen.queryByText("SYNC_EVENT")).not.toBeInTheDocument();
  });

  it("does not expose external notification source links as navigable actions", () => {
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-external-link",
            sourceType: "FOLLOWUP_EVENT",
            sourceId: "event-real-1",
            dedupeKey: "followup:event-real-1",
            title: "随访异常通知",
            message: "患者报告呼吸困难，需要处理。",
            level: "HIGH",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
            deepLink: "https://example.invalid/clinical/followup?taskId=return-task-1",
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();

    expect(screen.queryByRole("link", { name: "打开来源" })).not.toBeInTheDocument();
    expect(screen.getByText("来源暂不可跳转")).toBeInTheDocument();
  });

  it("shows an honest source jump status when notifications have no deep link", () => {
    renderNotifications();

    expect(screen.queryByRole("link", { name: "打开来源" })).not.toBeInTheDocument();
    expect(screen.queryByText("来源暂不可跳转")).not.toBeInTheDocument();
    expect(screen.getByText("来源未提供跳转")).toBeInTheDocument();
  });

  it("shows an honest trace status when notifications have no trace id", () => {
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-no-trace",
            sourceType: "FOLLOWUP_EVENT",
            sourceId: "event-no-trace",
            dedupeKey: "followup:event-no-trace",
            title: "随访异常通知",
            message: "患者报告呼吸困难，需要处理。",
            level: "HIGH",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            traceId: null,
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();

    expect(screen.queryByText("来源编号 event-no-trace")).not.toBeInTheDocument();
    expect(screen.queryByText("追踪号未提供")).not.toBeInTheDocument();
    expect(screen.queryByText(/^追踪号 trace-/)).not.toBeInTheDocument();
  });

  it("shows honest external delivery compensation status without claiming delivery", () => {
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-external-status",
            sourceType: "WORKFLOW_TODO",
            sourceId: "todo-followup-1",
            dedupeKey: "todo:todo-followup-1:created",
            title: "待办待处理",
            message: "待办需要处理。",
            level: "HIGH",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
            traceId: "trace-notify",
            externalDeliveries: [
              {
                channelCode: "sms",
                channelName: "短信通知通道",
                status: "NOT_CONNECTED",
                compensationRequired: true,
                retryCount: 0,
                maxRetries: 3,
                errorMessage: "未接入真实外部发送连接器，已登记异步补偿，不阻断主流程",
              },
              {
                channelCode: "webhook",
                channelName: "Webhook 通知通道",
                status: "NOT_CONNECTED",
                compensationRequired: true,
                retryCount: 0,
                maxRetries: 3,
                errorMessage: "未接入真实 Webhook 发送连接器，已登记异步补偿",
              },
              {
                channelCode: "in-hospital",
                channelName: "院内消息通道",
                status: "NOT_CONNECTED",
                compensationRequired: true,
                retryCount: 0,
                maxRetries: 3,
                errorMessage: "未接入真实院内消息发送连接器，已登记异步补偿",
              },
            ],
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();

    expect(screen.getByText("外发状态")).toBeInTheDocument();
    expect(screen.getByText("短信通知通道 未接通")).toBeInTheDocument();
    expect(screen.getByText("Webhook 通知通道 未接通")).toBeInTheDocument();
    expect(screen.getByText("院内消息通道 未接通")).toBeInTheDocument();
    expect(screen.getByText("需补偿")).toBeInTheDocument();
    expect(screen.getByText(/未接入真实外部发送连接器/)).toBeInTheDocument();
    expect(screen.getByText(/未接入真实 Webhook 发送连接器/)).toBeInTheDocument();
    expect(screen.getByText(/未接入真实院内消息发送连接器/)).toBeInTheDocument();
    expect(screen.queryByText("已送达")).not.toBeInTheDocument();
  });

  it("marks quiet-hour muted notifications while keeping safety notifications visible", () => {
    notificationHookMocks.useWorkflowNotificationSettings.mockReturnValue({
      data: {
        inAppEnabled: true,
        smsEnabled: false,
        emailEnabled: false,
        pushEnabled: false,
        webhookEnabled: false,
        inHospitalMessageEnabled: false,
        quietHoursEnabled: true,
        quietStart: "22:00",
        quietEnd: "07:00",
        quietBypassLevels: ["CRITICAL", "HIGH"],
        quietActiveNow: true,
        version: 4,
        updatedAt: "2026-06-05T00:00:00Z",
        updatedBy: "doctor-real-1",
      },
      isError: false,
      isLoading: false,
      refetch: vi.fn(),
    });
    notificationHookMocks.useWorkflowNotifications.mockReturnValue({
      data: {
        items: [
          {
            notificationId: "notify-info-1",
            sourceType: "SYNC_EVENT",
            sourceId: "event-info-1",
            dedupeKey: "clinical-event:event-info-1",
            title: "同步事件已处理",
            message: "报告查看事件已处理。",
            level: "INFO",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
          },
          {
            notificationId: "notify-critical-1",
            sourceType: "SAFETY_REVIEW",
            sourceId: "safety-review-1",
            dedupeKey: "safety:safety-review-1",
            title: "安全复核提醒",
            message: "高危知识撤回后需要立即复核。",
            level: "CRITICAL",
            status: "UNREAD",
            recipientId: "doctor-real-1",
            recipientRole: "DOCTOR",
          },
        ],
        page: 0,
        size: 10,
        total: 2,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: notificationHookMocks.refetchNotifications,
    });

    renderNotifications();

    expect(notificationHookMocks.useWorkflowNotificationSettings).toHaveBeenCalled();
    expect(screen.getByText("当前免打扰生效")).toBeInTheDocument();
    expect(screen.getByText("22:00 - 07:00")).toBeInTheDocument();
    expect(screen.getByText("同步事件已处理")).toBeInTheDocument();
    expect(screen.getByText("免打扰中")).toBeInTheDocument();
    expect(screen.getByText("安全复核提醒")).toBeInTheDocument();
    expect(screen.getByText("安全绕过")).toBeInTheDocument();
  });

  it("shows an honest quiet-hour status when notification settings cannot be loaded", () => {
    notificationHookMocks.useWorkflowNotificationSettings.mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
      refetch: vi.fn(),
    });

    renderNotifications();

    expect(screen.getByText("免打扰状态暂不可确认")).toBeInTheDocument();
    expect(screen.getByText("通知偏好暂时不可用，请刷新或到通知设置页确认。")).toBeInTheDocument();
    expect(screen.getByText("随访异常通知")).toBeInTheDocument();
  });
});
