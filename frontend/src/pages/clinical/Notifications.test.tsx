import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import Notifications from "./Notifications";

const notificationHookMocks = vi.hoisted(() => ({
  markRead: vi.fn(),
  refetchNotifications: vi.fn(),
  useOrgUnits: vi.fn(),
  useReadWorkflowNotification: vi.fn(),
  useWorkflowNotifications: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useOrgUnits: notificationHookMocks.useOrgUnits,
  useReadWorkflowNotification: notificationHookMocks.useReadWorkflowNotification,
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
    expect(screen.getByRole("heading", { name: "通知中心" })).toBeInTheDocument();
    expect(screen.getByText("随访异常通知")).toBeInTheDocument();
    expect(screen.getByText("患者报告呼吸困难，需要处理。")).toBeInTheDocument();
    expect(screen.getByText("patient-real-1")).toBeInTheDocument();
    expect(screen.queryByText("通知接口尚未接入")).not.toBeInTheDocument();
  });

  it("passes selected organization scope to the server-side notification query", async () => {
    const user = userEvent.setup();
    renderNotifications();

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

  it("marks an unread notification as read through the backend and refreshes", async () => {
    const user = userEvent.setup();
    renderNotifications();

    await user.click(screen.getByRole("button", { name: "标为已读" }));

    await waitFor(() => {
      expect(notificationHookMocks.markRead).toHaveBeenCalledWith("notify-real-1");
    });
    expect(notificationHookMocks.refetchNotifications).toHaveBeenCalled();
  });

  it("marks all currently loaded unread notifications through backend acknowledgements", async () => {
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
});
