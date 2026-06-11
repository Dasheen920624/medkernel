import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";

const apiMocks = vi.hoisted(() => ({
  checkInitToken: vi.fn(),
  createAdmin: vi.fn(),
  changePassword: vi.fn(),
  bindMfa: vi.fn(),
  bootstrapStatus: {
    data: { initialized: false },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
}));

vi.mock("@/shared/api/hooks", () => ({
  useBootstrapStatus: () => apiMocks.bootstrapStatus,
  useCheckBootstrapInitToken: () => ({ mutateAsync: apiMocks.checkInitToken, isPending: false }),
  useCreateBootstrapAdmin: () => ({ mutateAsync: apiMocks.createAdmin, isPending: false }),
  useChangePassword: () => ({ mutateAsync: apiMocks.changePassword, isPending: false }),
  useBindBootstrapMfa: () => ({ mutateAsync: apiMocks.bindMfa, isPending: false }),
  useThemePreference: () => ({ data: undefined }),
  useSaveThemePreference: () => ({ mutateAsync: vi.fn() }),
}));

import Bootstrap from "./Bootstrap";

const readBootstrapCss = () =>
  readFileSync(resolve(process.cwd(), "src/pages/Bootstrap.module.css"), "utf8");

function renderBootstrap(state?: unknown) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <AntdApp>
          <MemoryRouter
            initialEntries={[{ pathname: "/bootstrap", state }]}
            future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
          >
            <Routes>
              <Route path="/bootstrap" element={<Bootstrap />} />
              <Route path="/login" element={<div>登录页占位</div>} />
            </Routes>
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
    apiMocks.bootstrapStatus.data = { initialized: false };
    apiMocks.bootstrapStatus.isLoading = false;
    apiMocks.bootstrapStatus.isError = false;
    apiMocks.bootstrapStatus.refetch.mockReset();
  });

  it("首次接管页使用客户可见话术并可返回登录", () => {
    const { container } = renderBootstrap();

    expect(screen.getByRole("heading", { name: "首次部署接管" })).toBeInTheDocument();
    const shell = screen.getByRole("region", { name: "首次部署接管工作区" });
    expect(shell.className).toContain("bootstrapShell");
    expect(shell.querySelector('[aria-label="首次部署接管说明"]')?.className).toContain("heroCard");
    expect(screen.getByRole("button", { name: /主题模式：默认/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "继续接管" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返回登录" })).toBeInTheDocument();
    expect(screen.getByLabelText("部署接管码")).toBeInTheDocument();
    expect(container).not.toHaveTextContent(/BASE-11|PostgreSQL|Oracle|init token/i);
    expect(container.querySelectorAll(".ant-btn-primary")).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: "返回登录" }));

    expect(screen.getByText("登录页占位")).toBeInTheDocument();
  });

  it("系统已初始化时直接访问接管页只允许返回登录", () => {
    apiMocks.bootstrapStatus.data = { initialized: true };

    renderBootstrap();

    expect(screen.getByText("系统已完成首次部署")).toBeInTheDocument();
    expect(screen.queryByLabelText("部署接管码")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "返回登录" }));
    expect(screen.getByText("登录页占位")).toBeInTheDocument();
  });

  it("账号安全设置不受首次部署完成状态影响", () => {
    apiMocks.bootstrapStatus.data = { initialized: true };

    renderBootstrap({
      phase: "change-password",
      login: {
        userId: "organization-admin",
        tenantId: "t-hospital",
        mustChangePwd: true,
        mfaRequired: false,
        mfaBound: false,
      },
    });

    expect(screen.getByRole("heading", { name: "完成首次改密" })).toBeInTheDocument();
    expect(screen.queryByText("系统已完成首次部署")).not.toBeInTheDocument();
  });

  it("部署接管码错误按字段回显", async () => {
    apiMocks.checkInitToken.mockRejectedValue({
      response: { data: { detail: "部署接管码已过期" } },
    });
    renderBootstrap();

    fireEvent.change(screen.getByLabelText("部署接管码"), { target: { value: "expired-token" } });
    fireEvent.click(screen.getByRole("button", { name: "继续接管" }));

    expect(await screen.findByText("部署接管码已过期")).toBeInTheDocument();
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
      roles: ["platform-governance-admin"],
      mustChangePwd: true,
    });
    const { container } = renderBootstrap();

    fireEvent.change(screen.getByLabelText("部署接管码"), { target: { value: "raw-init-token" } });
    fireEvent.click(screen.getByRole("button", { name: "继续接管" }));
    expect(await screen.findByRole("heading", { name: "设置首发管理员" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返回登录" })).toBeInTheDocument();
    expect(screen.queryByLabelText("租户标识")).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: /租户/ })).not.toBeInTheDocument();
    expect(screen.getByText("平台治理空间自动绑定")).toBeInTheDocument();
    expect(screen.getByText("集团和医院服务空间进入平台治理后开通。")).toBeInTheDocument();
    expect(container.querySelectorAll(".ant-btn-primary")).toHaveLength(1);

    fireEvent.change(screen.getByLabelText("账号"), { target: { value: "platform-owner" } });
    fireEvent.change(screen.getByLabelText("初始密码"), { target: { value: "Init@2026pw" } });
    fireEvent.change(screen.getByLabelText("确认初始密码"), {
      target: { value: "Init@2026pw" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建首发管理员" }));

    expect(await screen.findByText(/请使用首发账号登录并完成首次改密/)).toBeInTheDocument();
    expect(apiMocks.createAdmin).toHaveBeenCalledWith({
      token: "raw-init-token",
      username: "platform-owner",
      password: "Init@2026pw",
    });
  });

  it("客户租户首次登录只展示账号安全设置，不混入平台接管语义", () => {
    const { container } = renderBootstrap({
      phase: "change-password",
      username: "organization-admin",
      login: {
        userId: "organization-admin",
        tenantId: "t-hospital",
        roles: ["tenant-admin"],
        mustChangePwd: true,
        mfaRequired: false,
        mfaBound: false,
      },
    });

    expect(screen.getByRole("region", { name: "账号安全设置工作区" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "完成账号安全设置" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "完成首次改密" })).toBeInTheDocument();
    expect(screen.getByText("改密")).toBeInTheDocument();
    expect(screen.getByText("双因素")).toBeInTheDocument();
    expect(screen.getByText("完成")).toBeInTheDocument();
    expect(container).not.toHaveTextContent(/首次部署接管|平台接管|接管码|首发管理员/);
  });

  it("登录后强制流程先改密，再按密钥生成和验证码校验绑定 MFA", async () => {
    apiMocks.changePassword.mockResolvedValue(undefined);
    apiMocks.bindMfa
      .mockResolvedValueOnce({
        mfaBound: false,
        secret: "JBSWY3DPEHPK3PXP",
        otpauthUri:
          "otpauth://totp/MedKernel:platform-owner?secret=JBSWY3DPEHPK3PXP&issuer=MedKernel",
      })
      .mockResolvedValueOnce({ mfaBound: true, recoveryCode: "RECOVERY-CODE-ONCE" });
    renderBootstrap({
      phase: "change-password",
      login: {
        userId: "platform-owner",
        tenantId: "t-1",
        roles: ["platform-governance-admin"],
        mustChangePwd: true,
        mfaRequired: true,
        mfaBound: false,
      },
    });

    fireEvent.change(screen.getByLabelText("当前密码"), { target: { value: "Init@2026pw" } });
    fireEvent.change(screen.getByLabelText("新密码"), { target: { value: "Owner@2026pw" } });
    fireEvent.change(screen.getByLabelText("确认新密码"), { target: { value: "Owner@2026pw" } });
    fireEvent.click(screen.getByRole("button", { name: "完成首次改密" }));

    expect(await screen.findByRole("heading", { name: "绑定双因素认证" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返回登录" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("设备名称"), { target: { value: "值班安全终端" } });
    fireEvent.click(screen.getByRole("button", { name: "生成认证密钥" }));

    expect(await screen.findByText("JBSWY3DPEHPK3PXP")).toBeInTheDocument();
    expect(screen.getByLabelText("离线双因素认证二维码")).toBeInTheDocument();
    expect(screen.getByText(/二维码由本页面生成，不访问外网/)).toBeInTheDocument();
    expect(screen.getByText(/内网不可扫码时选择“手动输入密钥”/)).toBeInTheDocument();
    expect(screen.getByText(/每 30 秒生成 6 位动态验证码/)).toBeInTheDocument();
    expect(screen.getByText("发行方")).toBeInTheDocument();
    expect(screen.getByText("MedKernel")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("动态验证码"), { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "验证并完成绑定" }));

    expect(await screen.findByText("RECOVERY-CODE-ONCE")).toBeInTheDocument();
    expect(apiMocks.changePassword).toHaveBeenCalledWith({
      oldPassword: "Init@2026pw",
      newPassword: "Owner@2026pw",
    });
    expect(apiMocks.bindMfa).toHaveBeenNthCalledWith(1, { label: "值班安全终端" });
    expect(apiMocks.bindMfa).toHaveBeenNthCalledWith(2, {
      label: "值班安全终端",
      secret: "JBSWY3DPEHPK3PXP",
      code: "123456",
    });
  });

  it("首次改密阶段也提供返回登录入口", () => {
    renderBootstrap({
      phase: "change-password",
      login: {
        userId: "platform-owner",
        tenantId: "t-1",
        mustChangePwd: true,
        mfaRequired: false,
        mfaBound: false,
      },
    });

    expect(screen.getByRole("heading", { name: "完成首次改密" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "返回登录" }));

    expect(screen.getByText("登录页占位")).toBeInTheDocument();
  });

  it("首次部署页布局收紧，避免说明区和表单区中间过空", () => {
    const css = readBootstrapCss();

    expect(css).toContain(".bootstrapShell");
    expect(css).toContain(".heroCard");
    expect(css).toContain("grid-template-columns: minmax(0, calc(var(--mk-unit) * 430))");
    expect(css).toContain("gap: calc(var(--mk-unit) * 20)");
    expect(css).not.toContain("calc(var(--mk-unit) * 560)) minmax");
    expect(css).not.toContain("gap: calc(var(--mk-unit) * 32)");
  });
});
