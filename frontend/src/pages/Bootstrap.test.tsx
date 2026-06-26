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
  verifyMfa: vi.fn(),
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
  useVerifyMfa: () => ({ mutateAsync: apiMocks.verifyMfa, isPending: false }),
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
    apiMocks.verifyMfa.mockReset();
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
    expect(
      screen.getByText("初始管理员已经建立，请返回登录。后续账号与服务机构统一在工作台内维护。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/服务空间/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText("部署接管码")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "返回登录" }));
    expect(screen.getByText("登录页占位")).toBeInTheDocument();
  });

  it("账号安全设置不受首次部署完成状态影响", () => {
    apiMocks.bootstrapStatus.data = { initialized: true };

    renderBootstrap({
      phase: "change-password",
      login: {
        userId: "platform-admin",
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

  it("通过 token 后创建初始管理员，并提示返回登录完成改密", async () => {
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

    fireEvent.change(screen.getByLabelText("部署接管码"), { target: { value: "raw-init-token" } });
    fireEvent.click(screen.getByRole("button", { name: "继续接管" }));
    expect(await screen.findByRole("heading", { name: "设置初始管理员" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返回登录" })).toBeInTheDocument();
    expect(screen.queryByLabelText("租户标识")).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: /租户/ })).not.toBeInTheDocument();
    expect(screen.getByText("平台治理空间自动绑定")).toBeInTheDocument();
    expect(screen.getByText("集团和医院服务机构进入平台治理后开通。")).toBeInTheDocument();
    expect(screen.queryByText(/服务空间/)).not.toBeInTheDocument();
    expect(container.querySelectorAll(".ant-btn-primary")).toHaveLength(1);

    fireEvent.change(screen.getByLabelText("账号"), { target: { value: "platform-owner" } });
    fireEvent.change(screen.getByLabelText("初始密码"), { target: { value: "Init@2026pw" } });
    fireEvent.change(screen.getByLabelText("确认初始密码"), {
      target: { value: "Init@2026pw" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建初始管理员" }));

    expect(await screen.findByText(/请使用初始账号登录并完成首次改密/)).toBeInTheDocument();
    expect(screen.queryByText(/服务空间/)).not.toBeInTheDocument();
    expect(apiMocks.createAdmin).toHaveBeenCalledWith({
      token: "raw-init-token",
      username: "platform-owner",
      password: "Init@2026pw",
    });
  });

  it("创建初始管理员成功后即使状态刷新为已初始化，也保留返回登录提示", async () => {
    apiMocks.checkInitToken.mockResolvedValue({
      valid: true,
      expiresAt: "2026-06-01T09:00:00Z",
    });
    apiMocks.createAdmin.mockImplementation(async () => {
      apiMocks.bootstrapStatus.data = { initialized: true };
      return {
        userId: "platform-owner",
        tenantId: "t-1",
        username: "platform-owner",
        roles: ["system-superadmin"],
        mustChangePwd: true,
      };
    });
    renderBootstrap();

    fireEvent.change(screen.getByLabelText("部署接管码"), { target: { value: "raw-init-token" } });
    fireEvent.click(screen.getByRole("button", { name: "继续接管" }));
    expect(await screen.findByRole("heading", { name: "设置初始管理员" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("账号"), { target: { value: "platform-owner" } });
    fireEvent.change(screen.getByLabelText("初始密码"), { target: { value: "Init@2026pw" } });
    fireEvent.change(screen.getByLabelText("确认初始密码"), {
      target: { value: "Init@2026pw" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建初始管理员" }));

    expect(await screen.findByText(/请使用初始账号登录并完成首次改密/)).toBeInTheDocument();
    expect(screen.queryByText("系统已完成首次部署")).not.toBeInTheDocument();
  });

  it("客户租户首次登录只展示账号安全设置，不混入平台接管语义", () => {
    const { container } = renderBootstrap({
      phase: "change-password",
      username: "platform-admin",
      login: {
        userId: "platform-admin",
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
    expect(screen.getByText("完成")).toBeInTheDocument();
    expect(screen.getByText("进入机构工作台")).toBeInTheDocument();
    expect(screen.queryByText("多因素认证")).not.toBeInTheDocument();
    expect(screen.queryByText("绑定多因素认证")).not.toBeInTheDocument();
    expect(container).not.toHaveTextContent(/首次部署接管|平台接管|接管码|初始管理员/);
  });

  it("默认接管流程不把多因素认证描述为必经步骤", () => {
    renderBootstrap();

    expect(screen.queryByText("绑定多因素认证")).not.toBeInTheDocument();
    expect(screen.getAllByText(/多因素认证默认关闭/).length).toBeGreaterThan(0);
  });

  it("登录后强制流程先改密，再按密钥生成和验证码校验绑定多因素认证", async () => {
    apiMocks.changePassword.mockResolvedValue(undefined);
    apiMocks.bindMfa
      .mockResolvedValueOnce({
        mfaBound: false,
        secret: "JBSWY3DPEHPK3PXP",
        otpauthUri:
          "otpauth://totp/MedKernel:platform-owner?secret=JBSWY3DPEHPK3PXP&issuer=MedKernel",
      })
      .mockResolvedValueOnce({ mfaBound: true, recoveryCode: "RECOVERY-CODE-ONCE" });
    apiMocks.verifyMfa.mockResolvedValue({ verified: true });
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

    expect(screen.getByText("进入平台治理")).toBeInTheDocument();
    expect(
      screen.getByText("当前部署已开启多因素认证，按平台安全策略完成认证器验证"),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("当前密码"), { target: { value: "Init@2026pw" } });
    fireEvent.change(screen.getByLabelText("新密码"), { target: { value: "Owner@2026pw" } });
    fireEvent.change(screen.getByLabelText("确认新密码"), { target: { value: "Owner@2026pw" } });
    fireEvent.click(screen.getByRole("button", { name: "完成首次改密" }));

    expect(await screen.findByRole("heading", { name: "绑定多因素认证" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "返回登录" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("设备名称"), { target: { value: "值班安全终端" } });
    fireEvent.click(screen.getByRole("button", { name: "生成认证密钥" }));

    expect(await screen.findByText("JBSWY3DPEHPK3PXP")).toBeInTheDocument();
    expect(screen.getByLabelText("离线多因素认证二维码")).toBeInTheDocument();
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
    expect(apiMocks.verifyMfa).toHaveBeenCalledWith({ code: "123456" });
  });

  it("已绑定多因素认证的账号只验证当前动态码，不重复生成密钥", async () => {
    apiMocks.verifyMfa.mockResolvedValue({ verified: true });
    renderBootstrap({
      phase: "mfa",
      login: {
        userId: "engine-operator",
        tenantId: "t-1",
        roles: ["engine-operator"],
        mustChangePwd: false,
        mfaRequired: true,
        mfaBound: true,
      },
    });

    expect(screen.getByRole("heading", { name: "验证多因素认证" })).toBeInTheDocument();
    expect(screen.queryByLabelText("设备名称")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("动态验证码"), { target: { value: "654321" } });
    fireEvent.click(screen.getByRole("button", { name: "验证并进入系统" }));

    expect(await screen.findByText("账号安全设置完成")).toBeInTheDocument();
    expect(apiMocks.verifyMfa).toHaveBeenCalledWith({ code: "654321" });
    expect(apiMocks.bindMfa).not.toHaveBeenCalled();
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
