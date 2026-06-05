import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import NotificationSettings from "./NotificationSettings";

const settingsHookMocks = vi.hoisted(() => ({
  refetchSettings: vi.fn(),
  saveSettings: vi.fn(),
  useSaveWorkflowNotificationSettings: vi.fn(),
  useWorkflowNotificationSettings: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSaveWorkflowNotificationSettings: settingsHookMocks.useSaveWorkflowNotificationSettings,
  useWorkflowNotificationSettings: settingsHookMocks.useWorkflowNotificationSettings,
}));

function renderSettings() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <NotificationSettings />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("NotificationSettings", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    settingsHookMocks.saveSettings.mockResolvedValue({
      version: 4,
      quietHoursEnabled: true,
    });
    settingsHookMocks.useWorkflowNotificationSettings.mockReturnValue({
      data: {
        inAppEnabled: true,
        smsEnabled: false,
        emailEnabled: false,
        pushEnabled: false,
        webhookEnabled: true,
        inHospitalMessageEnabled: false,
        quietHoursEnabled: true,
        quietStart: "22:00",
        quietEnd: "07:00",
        quietBypassLevels: ["CRITICAL", "HIGH"],
        quietActiveNow: false,
        version: 3,
        updatedAt: "2026-06-04T08:00:00Z",
        updatedBy: "doctor-1",
      },
      isError: false,
      isLoading: false,
      refetch: settingsHookMocks.refetchSettings,
    });
    settingsHookMocks.useSaveWorkflowNotificationSettings.mockReturnValue({
      isPending: false,
      mutateAsync: settingsHookMocks.saveSettings,
    });
  });

  it("renders persisted backend settings without the old static defaults", () => {
    renderSettings();

    expect(screen.getByRole("heading", { name: "通知设置" })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "站内信偏好" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByRole("switch", { name: "短信偏好" })).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByRole("switch", { name: "Webhook 偏好" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByRole("switch", { name: "院内消息偏好" })).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByLabelText("免打扰开始时间")).toHaveValue("22:00");
    expect(screen.getByLabelText("免打扰结束时间")).toHaveValue("07:00");
    expect(screen.getByText("危急")).toBeInTheDocument();
    expect(screen.getByText("高")).toBeInTheDocument();
    expect(screen.getByText(/NOT_CONNECTED/)).toHaveTextContent(
      "不声明短信、邮件、移动推送、Webhook 或院内消息已完成投递",
    );
    expect(screen.queryByText("默认夜班医生静默")).not.toBeInTheDocument();
  });

  it("saves quiet-hours preferences through the backend mutation", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("switch", { name: "邮件偏好" }));
    await user.click(screen.getByRole("switch", { name: "Webhook 偏好" }));
    await user.click(screen.getByRole("switch", { name: "院内消息偏好" }));
    await user.clear(screen.getByLabelText("免打扰开始时间"));
    await user.type(screen.getByLabelText("免打扰开始时间"), "21:30");
    await user.clear(screen.getByLabelText("免打扰结束时间"));
    await user.type(screen.getByLabelText("免打扰结束时间"), "06:30");
    await user.click(screen.getByRole("button", { name: "保存通知设置" }));

    await waitFor(() => {
      expect(settingsHookMocks.saveSettings).toHaveBeenCalledWith({
        inAppEnabled: true,
        smsEnabled: false,
        emailEnabled: true,
        pushEnabled: false,
        webhookEnabled: false,
        inHospitalMessageEnabled: true,
        quietHoursEnabled: true,
        quietStart: "21:30",
        quietEnd: "06:30",
        quietBypassLevels: ["CRITICAL", "HIGH"],
      });
    });
    expect(settingsHookMocks.refetchSettings).toHaveBeenCalled();
  });
});
