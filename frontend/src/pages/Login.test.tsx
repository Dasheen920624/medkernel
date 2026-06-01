import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const navigateMock = vi.fn();
const mutateAsyncMock = vi.fn();
let loginPending = false;
let delegatedAuthStatusState: {
  data?: {
    mode: string;
    enabled: boolean;
    status: string;
    providers: string[];
    message: string;
  };
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
};
vi.mock("react-router-dom", () => ({ useNavigate: () => navigateMock }));
vi.mock("@/shared/api/hooks", () => ({
  useLogin: () => ({ mutateAsync: mutateAsyncMock, isPending: loginPending }),
  useDelegatedAuthStatus: () => delegatedAuthStatusState,
  useThemePreference: () => ({ data: undefined }),
  useSaveThemePreference: () => ({ mutateAsync: vi.fn() }),
}));

import Login from "./Login";

const readLoginCss = () =>
  readFileSync(resolve(process.cwd(), "src/pages/Login.module.css"), "utf8");

const cssBlock = (source: string, selector: string) => {
  const escapedSelector = selector.replace(".", "\\.");
  return source.match(new RegExp(`${escapedSelector}\\s*\\{(?<body>[^}]*)\\}`))?.groups?.body ?? "";
};

describe("Login", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mutateAsyncMock.mockReset();
    loginPending = false;
    delegatedAuthStatusState = {
      data: {
        mode: "BOTH",
        enabled: true,
        status: "NOT_CONNECTED",
        providers: ["OIDC", "CAS", "SAML", "国密CA"],
        message: "院方统一身份入口已开放，但当前未配置真实 IdP 连接器。",
      },
      isLoading: false,
      isError: false,
    };
  });

  it("登录成功跳转 /dashboard", async () => {
    mutateAsyncMock.mockResolvedValue({
      userId: "doctor-1",
      tenantId: "t-1",
      roles: ["doctor"],
      mustChangePwd: false,
      mfaRequired: false,
      mfaBound: false,
    });
    render(<Login />);
    fireEvent.change(screen.getByLabelText("工号 / 账号"), { target: { value: "doctor" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "Mk@2026dev" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/dashboard"));
  });

  it("登录成功后若仍需改密或 MFA，强制进入首次部署引导", async () => {
    mutateAsyncMock.mockResolvedValue({
      userId: "platform-owner",
      tenantId: "t-1",
      roles: ["platform-admin"],
      mustChangePwd: true,
      mfaRequired: true,
      mfaBound: false,
    });
    render(<Login />);
    fireEvent.change(screen.getByLabelText("工号 / 账号"), { target: { value: "platform-owner" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "Init@2026pw" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));

    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith(
        "/bootstrap",
        expect.objectContaining({
          state: expect.objectContaining({ phase: "change-password" }),
        }),
      ),
    );
  });

  it("登录失败显示错误且不跳转", async () => {
    mutateAsyncMock.mockRejectedValue({
      response: { data: { detail: "用户名或密码不正确" } },
    });
    render(<Login />);
    fireEvent.change(screen.getByLabelText("工号 / 账号"), { target: { value: "x" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "y" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));
    await waitFor(() => expect(screen.getByText("用户名或密码不正确")).toBeInTheDocument());
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it("登录页提供主题切换入口", () => {
    render(<Login />);

    expect(screen.getByRole("button", { name: /默认/ })).toBeInTheDocument();
  });

  it("登录页提供首次部署接管入口", () => {
    render(<Login />);

    fireEvent.click(screen.getByRole("button", { name: "首次部署接管" }));

    expect(navigateMock).toHaveBeenCalledWith("/bootstrap");
  });

  it("登录页根容器注入主题 token，避免背景和文字失效", () => {
    const { container } = render(<Login />);
    const main = container.querySelector("main");

    expect(main?.style.getPropertyValue("--mk-login-page-bg")).toContain("linear-gradient");
    expect(main?.style.getPropertyValue("--mk-login-text")).not.toBe("");
  });

  it("登录页样式不保留系统色、currentColor 或硬编码颜色兜底", () => {
    const loginCss = readLoginCss();

    expect(loginCss).not.toMatch(/\b(?:Canvas|CanvasText|currentColor)\b/);
    expect(loginCss).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
  });

  it("主题入口和统一身份方式不覆盖或挤压登录卡片", () => {
    const loginCss = readLoginCss();
    const themeSwitcherCss = cssBlock(loginCss, ".themeSwitcher");
    const providerGridCss = cssBlock(loginCss, ".providerGrid");

    expect(loginCss).toMatch(/grid-template-areas:/);
    expect(loginCss).toMatch(/grid-area:\s*theme/);
    expect(themeSwitcherCss).not.toMatch(/position:\s*absolute/);
    expect(providerGridCss).not.toMatch(/repeat\(2/);
  });

  it("登录页控件尺寸由主题 token 驱动，支持老年医生模式放大", () => {
    const { container } = render(<Login />);
    const main = container.querySelector("main");

    expect(main?.style.getPropertyValue("--mk-login-control-font")).not.toBe("");
    expect(main?.style.getPropertyValue("--mk-login-control-height")).not.toBe("");
  });

  it("提交加载时暴露页面级忙碌状态", () => {
    loginPending = true;
    render(<Login />);

    expect(screen.getByRole("main", { name: "登录 MedKernel 工作台" })).toHaveAttribute(
      "aria-busy",
      "true",
    );
  });

  it("统一身份入口折叠展示待配置方式", async () => {
    render(<Login />);

    fireEvent.click(screen.getByRole("button", { name: "院方统一身份认证" }));

    expect(await screen.findByText("统一身份暂未接入")).toBeInTheDocument();
    expect(
      screen.getByText("院方统一身份入口已开放，但当前未配置真实 IdP 连接器。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "OIDC（NOT_CONNECTED）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "CAS（NOT_CONNECTED）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "SAML（NOT_CONNECTED）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "国密CA（NOT_CONNECTED）" })).toBeDisabled();
  });
});
