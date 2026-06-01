import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const apiMocks = vi.hoisted(() => ({
  checkInitToken: vi.fn(),
  createAdmin: vi.fn(),
  changePassword: vi.fn(),
  bindMfa: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useCheckBootstrapInitToken: () => ({ mutateAsync: apiMocks.checkInitToken, isPending: false }),
  useCreateBootstrapAdmin: () => ({ mutateAsync: apiMocks.createAdmin, isPending: false }),
  useChangePassword: () => ({ mutateAsync: apiMocks.changePassword, isPending: false }),
  useBindBootstrapMfa: () => ({ mutateAsync: apiMocks.bindMfa, isPending: false }),
  useThemePreference: () => ({ data: undefined }),
  useSaveThemePreference: () => ({ mutateAsync: vi.fn() }),
}));

import Bootstrap from "./Bootstrap";

function renderBootstrap(state?: unknown) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <AntdApp>
          <MemoryRouter initialEntries={[{ pathname: "/bootstrap", state }]}>
            <Bootstrap />
          </MemoryRouter>
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe("Bootstrap", () => {
  beforeEach(() => {
    apiMocks.checkInitToken.mockReset();
    apiMocks.createAdmin.mockReset();
    apiMocks.changePassword.mockReset();
    apiMocks.bindMfa.mockReset();
  });

  it("显示主题切换和单主按钮的 init token 步骤", () => {
    const { container } = renderBootstrap();

    expect(screen.getByRole("heading", { name: "首次部署接管" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /主题模式：默认/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "校验 init token" })).toBeInTheDocument();
    expect(container.querySelectorAll(".ant-btn-primary")).toHaveLength(1);
  });

  it("init token 错误按字段回显", async () => {
    apiMocks.checkInitToken.mockRejectedValue({
      response: { data: { detail: "初始化 token 已过期" } },
    });
    renderBootstrap();

    fireEvent.change(screen.getByLabelText("init token"), { target: { value: "expired-token" } });
    fireEvent.click(screen.getByRole("button", { name: "校验 init token" }));

    expect(await screen.findByText("初始化 token 已过期")).toBeInTheDocument();
  });

  it("通过 token 后创建首发管理员，并提示返回登录完成改密", async () => {
    apiMocks.checkInitToken.mockResolvedValue({
      valid: true,
      expiresAt: "2026-06-01T09:00:00Z",
    });
    apiMocks.createAdmin.mockResolvedValue({
      userId: "platform-owner",
      tenantId: "t-1",
      username: "platform-owner",
      roles: ["platform-admin"],
      mustChangePwd: true,
    });
    const { container } = renderBootstrap();

    fireEvent.change(screen.getByLabelText("init token"), { target: { value: "raw-init-token" } });
    fireEvent.click(screen.getByRole("button", { name: "校验 init token" }));
    expect(await screen.findByRole("heading", { name: "设置首发管理员" })).toBeInTheDocument();
    expect(container.querySelectorAll(".ant-btn-primary")).toHaveLength(1);

    fireEvent.change(screen.getByLabelText("账号"), { target: { value: "platform-owner" } });
    fireEvent.change(screen.getByLabelText("租户标识"), { target: { value: "t-1" } });
    fireEvent.change(screen.getByLabelText("初始密码"), { target: { value: "Init@2026pw" } });
    fireEvent.change(screen.getByLabelText("确认初始密码"), {
      target: { value: "Init@2026pw" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建首发管理员" }));

    expect(await screen.findByText(/请使用首发账号登录并完成首次改密/)).toBeInTheDocument();
    expect(apiMocks.createAdmin).toHaveBeenCalledWith({
      token: "raw-init-token",
      tenantId: "t-1",
      username: "platform-owner",
      password: "Init@2026pw",
    });
  });

  it("登录后强制流程先改密再绑定 MFA，恢复码只在结果页展示", async () => {
    apiMocks.changePassword.mockResolvedValue(undefined);
    apiMocks.bindMfa.mockResolvedValue({ mfaBound: true, recoveryCode: "RECOVERY-CODE-ONCE" });
    renderBootstrap({
      phase: "change-password",
      login: {
        userId: "platform-owner",
        tenantId: "t-1",
        roles: ["platform-admin"],
        mustChangePwd: true,
        mfaRequired: true,
        mfaBound: false,
      },
    });

    fireEvent.change(screen.getByLabelText("当前密码"), { target: { value: "Init@2026pw" } });
    fireEvent.change(screen.getByLabelText("新密码"), { target: { value: "Owner@2026pw" } });
    fireEvent.change(screen.getByLabelText("确认新密码"), { target: { value: "Owner@2026pw" } });
    fireEvent.click(screen.getByRole("button", { name: "完成首次改密" }));

    expect(await screen.findByRole("heading", { name: "绑定 MFA" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("设备名称"), { target: { value: "值班安全终端" } });
    fireEvent.click(screen.getByRole("button", { name: "生成一次性恢复码" }));

    expect(await screen.findByText("RECOVERY-CODE-ONCE")).toBeInTheDocument();
    expect(apiMocks.changePassword).toHaveBeenCalledWith({
      oldPassword: "Init@2026pw",
      newPassword: "Owner@2026pw",
    });
    expect(apiMocks.bindMfa).toHaveBeenCalledWith({ label: "值班安全终端" });
  });
});
