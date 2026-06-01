import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

const navigateMock = vi.fn();
const mutateAsyncMock = vi.fn();
vi.mock("react-router-dom", () => ({ useNavigate: () => navigateMock }));
vi.mock("@/shared/api/hooks", () => ({
  useLogin: () => ({ mutateAsync: mutateAsyncMock, isPending: false }),
  useThemePreference: () => ({ data: undefined }),
  useSaveThemePreference: () => ({ mutateAsync: vi.fn() }),
}));

import Login from "./Login";

describe("Login", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mutateAsyncMock.mockReset();
  });

  it("登录成功跳转 /dashboard", async () => {
    mutateAsyncMock.mockResolvedValue({
      userId: "doctor-1",
      tenantId: "t-1",
      roles: ["doctor"],
      mustChangePwd: false,
    });
    render(<Login />);
    fireEvent.change(screen.getByLabelText("工号 / 账号"), { target: { value: "doctor" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "Mk@2026dev" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/dashboard"));
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

  it("登录页根容器注入主题 token，避免背景和文字失效", () => {
    const { container } = render(<Login />);
    const main = container.querySelector("main");

    expect(main?.style.getPropertyValue("--mk-login-page-bg")).toContain("linear-gradient");
    expect(main?.style.getPropertyValue("--mk-login-text")).not.toBe("");
  });

  it("登录页控件尺寸由主题 token 驱动，支持老年医生模式放大", () => {
    const { container } = render(<Login />);
    const main = container.querySelector("main");

    expect(main?.style.getPropertyValue("--mk-login-control-font")).not.toBe("");
    expect(main?.style.getPropertyValue("--mk-login-control-height")).not.toBe("");
  });

  it("统一身份入口折叠展示待配置方式", async () => {
    render(<Login />);

    fireEvent.click(screen.getByRole("button", { name: "院方统一身份认证" }));

    expect(await screen.findByRole("button", { name: "CAS（待院方配置）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "OIDC（待院方配置）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "SAML（待院方配置）" })).toBeDisabled();
  });
});
