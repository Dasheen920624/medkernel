import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import NotificationSettings from "./NotificationSettings";

const settingsHookMocks = vi.hoisted(() => ({
  refetchSettings: vi.fn(),
  refetchSystemSettings: vi.fn(),
  saveSettings: vi.fn(),
  saveSystemSettings: vi.fn(),
  useSaveWorkflowNotificationSettings: vi.fn(),
  useSaveWorkflowSystemNotificationSettings: vi.fn(),
  useSecurityProfile: vi.fn(),
  useWorkflowNotificationSettings: vi.fn(),
  useWorkflowSystemNotificationSettings: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSaveWorkflowNotificationSettings: settingsHookMocks.useSaveWorkflowNotificationSettings,
  useSaveWorkflowSystemNotificationSettings:
    settingsHookMocks.useSaveWorkflowSystemNotificationSettings,
  useSecurityProfile: settingsHookMocks.useSecurityProfile,
  useWorkflowNotificationSettings: settingsHookMocks.useWorkflowNotificationSettings,
  useWorkflowSystemNotificationSettings: settingsHookMocks.useWorkflowSystemNotificationSettings,
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
    settingsHookMocks.saveSystemSettings.mockResolvedValue({
      systemVersion: 4,
      quietHoursEnabled: true,
    });
    settingsHookMocks.useSecurityProfile.mockReturnValue({
      data: {
        permissions: [{ code: "notification.write", risk: "LOW" }],
      },
      isLoading: false,
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
        subscribedTypes: ["SAFETY", "FOLLOWUP", "WORKFLOW"],
        mandatoryTypes: ["SAFETY"],
        source: "PERSONAL",
        quietActiveNow: false,
        version: 3,
        systemVersion: 2,
        updatedAt: "2026-06-04T08:00:00Z",
        updatedBy: "doctor-1",
      },
      isError: false,
      isLoading: false,
      refetch: settingsHookMocks.refetchSettings,
    });
    settingsHookMocks.useWorkflowSystemNotificationSettings.mockReturnValue({
      data: undefined,
      isError: false,
      isLoading: false,
      refetch: settingsHookMocks.refetchSystemSettings,
    });
    settingsHookMocks.useSaveWorkflowNotificationSettings.mockReturnValue({
      isPending: false,
      mutateAsync: settingsHookMocks.saveSettings,
    });
    settingsHookMocks.useSaveWorkflowSystemNotificationSettings.mockReturnValue({
      isPending: false,
      mutateAsync: settingsHookMocks.saveSystemSettings,
    });
  });

  it("renders persisted notification settings without the old static defaults", () => {
    renderSettings();

    expect(screen.getByRole("heading", { name: "通知偏好" })).toBeInTheDocument();
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
    expect(screen.getByRole("checkbox", { name: "安全与危急" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "安全与危急" })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "同步与接入" })).not.toBeChecked();
    expect(screen.getByText(/未连接/)).toHaveTextContent(
      "不声明短信、邮件、移动推送、Webhook 或院内消息已完成投递",
    );
    expect(screen.queryByText("默认夜班医生静默")).not.toBeInTheDocument();
  });

  it("saves quiet-hours preferences through the service mutation", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("switch", { name: "邮件偏好" }));
    await user.click(screen.getByRole("switch", { name: "Webhook 偏好" }));
    await user.click(screen.getByRole("switch", { name: "院内消息偏好" }));
    await user.clear(screen.getByLabelText("免打扰开始时间"));
    await user.type(screen.getByLabelText("免打扰开始时间"), "21:30");
    await user.clear(screen.getByLabelText("免打扰结束时间"));
    await user.type(screen.getByLabelText("免打扰结束时间"), "06:30");
    await user.click(screen.getByRole("button", { name: "保存通知偏好" }));

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
        subscribedTypes: ["SAFETY", "FOLLOWUP", "WORKFLOW"],
      });
    });
    expect(settingsHookMocks.refetchSettings).toHaveBeenCalled();
  });

  it("keeps safety subscription mandatory when saving personal preferences", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("checkbox", { name: "协作待办" }));
    await user.click(screen.getByRole("button", { name: "保存通知偏好" }));

    await waitFor(() => {
      expect(settingsHookMocks.saveSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          subscribedTypes: ["SAFETY", "FOLLOWUP"],
        }),
      );
    });
  });

  it("allows an authorized administrator to update tenant defaults with a reason", async () => {
    const user = userEvent.setup();
    settingsHookMocks.useSecurityProfile.mockReturnValue({
      data: {
        permissions: [
          { code: "notification.write", risk: "LOW" },
          { code: "system.read", risk: "LOW" },
          { code: "system.manage", risk: "HIGH" },
        ],
      },
      isLoading: false,
    });
    settingsHookMocks.useWorkflowSystemNotificationSettings.mockReturnValue({
      data: {
        inAppEnabled: true,
        smsEnabled: false,
        emailEnabled: false,
        pushEnabled: false,
        webhookEnabled: false,
        inHospitalMessageEnabled: true,
        quietHoursEnabled: false,
        quietStart: "22:00",
        quietEnd: "07:00",
        quietBypassLevels: ["CRITICAL", "HIGH"],
        subscribedTypes: ["SAFETY", "WORKFLOW"],
        mandatoryTypes: ["SAFETY"],
        source: "SYSTEM_DEFAULT",
        quietActiveNow: false,
        version: 0,
        systemVersion: 7,
        updatedAt: "2026-06-04T08:00:00Z",
        updatedBy: "system-admin-1",
      },
      isError: false,
      isLoading: false,
      refetch: settingsHookMocks.refetchSystemSettings,
    });
    renderSettings();

    await user.click(screen.getByText("系统默认"));
    await user.type(screen.getByLabelText("系统通知策略变更原因"), "统一院内通知策略");
    await user.click(screen.getByRole("button", { name: "保存通知偏好" }));

    await waitFor(() => {
      expect(settingsHookMocks.saveSystemSettings).toHaveBeenCalledWith({
        settings: expect.objectContaining({
          subscribedTypes: ["SAFETY", "WORKFLOW"],
          inHospitalMessageEnabled: true,
        }),
        reason: "统一院内通知策略",
        expectedVersion: 7,
      });
    });
    expect(settingsHookMocks.refetchSystemSettings).toHaveBeenCalled();
  });
});
