import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const navigateMock = vi.fn();
const mutateAsyncMock = vi.fn();
let loginPending = false;
let bootstrapStatusState: {
  data?: { initialized: boolean };
  isLoading: boolean;
  isError: boolean;
};
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
let loginTenantDirectoryState: {
  data?: {
    primaryTenants: Array<{ tenantId: string; name: string; kind: string }>;
    platformTenant: { tenantId: string; name: string; kind: string };
    hasCustomerTenants: boolean;
  };
  isLoading: boolean;
  isError: boolean;
};
vi.mock("react-router-dom", () => ({ useNavigate: () => navigateMock }));
vi.mock("@/shared/api/hooks", () => ({
  useLogin: () => ({ mutateAsync: mutateAsyncMock, isPending: loginPending }),
  useBootstrapStatus: () => bootstrapStatusState,
  useDelegatedAuthStatus: () => delegatedAuthStatusState,
  useLoginTenantDirectory: () => loginTenantDirectoryState,
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

const legacyDelegatedUnavailableCopy = ["统一身份", "暂未接入"].join("");
const legacyDelegatedProviderPattern = new RegExp(`${["Id", "P"].join("")}|连接器|未接通`);

describe("Login", () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mutateAsyncMock.mockReset();
    loginPending = false;
    bootstrapStatusState = {
      data: { initialized: false },
      isLoading: false,
      isError: false,
    };
    delegatedAuthStatusState = {
      data: {
        mode: "BOTH",
        enabled: true,
        status: "NOT_CONNECTED",
        providers: ["OIDC", "CAS", "SAML", "SM_CA"],
        message: "院方统一身份入口已开放，请由信息科在身份来源完成配置后启用。",
      },
      isLoading: false,
      isError: false,
    };
    loginTenantDirectoryState = {
      data: {
        primaryTenants: [{ tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" }],
        platformTenant: { tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" },
        hasCustomerTenants: false,
      },
      isLoading: false,
      isError: false,
    };
  });

  it("登录成功跳转 /dashboard", async () => {
    mutateAsyncMock.mockResolvedValue({
      userId: "doctor-1",
      tenantId: "t-1",
      roles: ["clinical-user"],
      mustChangePwd: false,
      mfaRequired: false,
      mfaBound: false,
    });
    render(<Login />);
    fireEvent.change(screen.getByLabelText("工号 / 账号"), {
      target: { value: "clinical-user" },
    });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "Mk@2026dev" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/dashboard"));
    expect(mutateAsyncMock).toHaveBeenCalledWith({
      username: "clinical-user",
      password: "Mk@2026dev",
      tenantId: "t-1",
    });
  });

  it("登录成功后若仍需改密或多因素认证，强制进入首次部署引导", async () => {
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

  it("多因素认证已绑定但本次尚未验证时进入验证码步骤", async () => {
    mutateAsyncMock.mockResolvedValue({
      userId: "engine-operator",
      tenantId: "t-1",
      roles: ["engine-operator"],
      mustChangePwd: false,
      mfaRequired: true,
      mfaBound: true,
    });
    render(<Login />);
    fireEvent.change(screen.getByLabelText("工号 / 账号"), {
      target: { value: "engine-operator" },
    });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "Mk@2026dev" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));

    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith(
        "/bootstrap",
        expect.objectContaining({
          state: expect.objectContaining({ phase: "mfa" }),
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

  it("系统完成首次部署后不再展示接管入口", () => {
    bootstrapStatusState = {
      data: { initialized: true },
      isLoading: false,
      isError: false,
    };

    render(<Login />);

    expect(screen.queryByRole("button", { name: "首次部署接管" })).not.toBeInTheDocument();
  });

  it("登录页以登录卡片居中为主，平台状态默认隐藏", () => {
    render(<Login />);

    expect(screen.getByRole("heading", { name: "登录平台治理" })).toBeInTheDocument();
    expect(screen.queryByLabelText("登录上下文")).not.toBeInTheDocument();
    expect(screen.queryByText("平台管理入口")).not.toBeInTheDocument();
    expect(screen.queryByText("安全审计已开启")).not.toBeInTheDocument();
  });

  it("平台唯一租户登录恢复 MedKernel 品牌，不显示租户下拉和院方入口", () => {
    render(<Login />);

    expect(screen.getByText("MedKernel")).toBeInTheDocument();
    expect(screen.getByText("集团医疗智能中枢")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("请输入平台治理账号")).toBeInTheDocument();
    expect(screen.queryByLabelText("租户标识")).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: /租户/ })).not.toBeInTheDocument();
    expect(screen.getByText("平台治理入口")).toBeInTheDocument();
    expect(screen.getByText("平台标准与全局治理入口")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "院方统一身份认证" })).not.toBeInTheDocument();
  });

  it("租户目录服务未返回时不使用本地租户兜底", () => {
    loginTenantDirectoryState = {
      isLoading: false,
      isError: false,
    };
    render(<Login />);

    expect(screen.getByText("没有可登录机构")).toBeInTheDocument();
    expect(screen.getByText("机构目录未就绪")).toBeInTheDocument();
    expect(screen.getByText("无可登录机构")).toBeInTheDocument();
    expect(
      screen.getByText("服务端未返回平台治理入口或医疗服务机构，登录入口已暂停提交。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /进入工作台/ })).toBeDisabled();
  });

  it("已有客户租户时优先显示客户或集团租户，平台主租户退居第二层", async () => {
    loginTenantDirectoryState = {
      data: {
        primaryTenants: [{ tenantId: "t-hospital", name: "集团总院", kind: "CUSTOMER" }],
        platformTenant: { tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" },
        hasCustomerTenants: true,
      },
      isLoading: false,
      isError: false,
    };
    mutateAsyncMock.mockResolvedValue({
      userId: "hosp-admin",
      tenantId: "t-hospital",
      roles: ["platform-admin"],
      mustChangePwd: false,
      mfaRequired: false,
      mfaBound: false,
    });
    render(<Login />);

    const modeSwitch = screen.getByLabelText("登录类型切换");
    expect(modeSwitch).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "机构用户" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "平台治理" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(await screen.findByRole("button", { name: /集团总院/ })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.queryByRole("combobox", { name: /客户|集团|租户/ })).not.toBeInTheDocument();
    expect(screen.queryByText("平台治理入口（唯一内置）")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "平台治理" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "院方统一身份认证" })).toBeInTheDocument();
    expect(screen.queryByText(legacyDelegatedUnavailableCopy)).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("请输入工号或机构账号")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("工号 / 账号"), { target: { value: "hosp-admin" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "Mk@2026dev" } });
    fireEvent.click(screen.getByRole("button", { name: /进入工作台/ }));

    await waitFor(() =>
      expect(mutateAsyncMock).toHaveBeenCalledWith(
        expect.objectContaining({ tenantId: "t-hospital" }),
      ),
    );
  });

  it("客户租户存在时可展开第二层切换平台主租户登录", async () => {
    loginTenantDirectoryState = {
      data: {
        primaryTenants: [{ tenantId: "t-hospital", name: "集团总院", kind: "CUSTOMER" }],
        platformTenant: { tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" },
        hasCustomerTenants: true,
      },
      isLoading: false,
      isError: false,
    };
    render(<Login />);

    fireEvent.click(screen.getByRole("button", { name: "平台治理" }));

    expect(screen.getByText("平台治理入口")).toBeInTheDocument();
    expect(
      screen.getByText("仅供平台治理、知识标准治理和系统运维人员使用；机构差异不会改写平台标准。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/知识标准维护|机构定制不会回写平台标准/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "平台治理" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.queryByRole("button", { name: "院方统一身份认证" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "机构用户" }));

    expect(await screen.findByRole("button", { name: /集团总院/ })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "院方统一身份认证" })).toBeInTheDocument();
  });

  it("登录帮助默认收起，避免登录卡片首屏过长", () => {
    render(<Login />);

    expect(screen.getByRole("button", { name: "登录帮助" })).toBeInTheDocument();
    expect(screen.queryByText("首次登录")).not.toBeInTheDocument();
    expect(screen.queryByText("忘记密码")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "登录帮助" }));

    expect(screen.getByText("首次登录")).toBeInTheDocument();
    expect(screen.getByText("忘记密码")).toBeInTheDocument();
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

    expect(loginCss).toMatch(/place-items:\s*center/);
    expect(loginCss).toMatch(
      /grid-template-columns:\s*minmax\(0,\s*calc\(var\(--mk-unit\) \* 480\)\)/,
    );
    expect(themeSwitcherCss).toMatch(/position:\s*absolute/);
    expect(providerGridCss).not.toMatch(/repeat\(2/);
  });

  it("登录页默认隐藏帮助列表样式，不让右侧卡片拖出首屏", () => {
    const loginCss = readLoginCss();
    const cardStackCss = cssBlock(loginCss, ".cardStack");

    expect(cardStackCss).toContain("gap: calc(var(--mk-unit) * 12)");
    expect(loginCss).toContain(".helpToggle");
    expect(loginCss).toContain(".compactFooter");
  });

  it("登录页布局居中收紧，不让两栏中间留大空白", () => {
    const loginCss = readLoginCss();
    const pageCss = cssBlock(loginCss, ".page");

    expect(pageCss).toContain("place-items: center");
    expect(pageCss).toContain("justify-content: center");
    expect(pageCss).toContain("grid-template-columns: minmax(0, calc(var(--mk-unit) * 480))");
    expect(pageCss).not.toContain("grid-template-columns: minmax(0, 1fr)");
    expect(loginCss).toContain(".secondaryEntry");
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
    loginTenantDirectoryState = {
      data: {
        primaryTenants: [{ tenantId: "t-hospital", name: "集团总院", kind: "CUSTOMER" }],
        platformTenant: { tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" },
        hasCustomerTenants: true,
      },
      isLoading: false,
      isError: false,
    };
    render(<Login />);

    fireEvent.click(screen.getByRole("button", { name: "院方统一身份认证" }));

    expect(await screen.findByText("统一身份服务待配置")).toBeInTheDocument();
    expect(
      screen.getByText("院方统一身份入口已开放，请由信息科在身份来源完成配置后启用。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(legacyDelegatedUnavailableCopy)).not.toBeInTheDocument();
    expect(screen.queryByText(legacyDelegatedProviderPattern)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "开放式身份认证（OIDC）（待配置）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "统一认证服务（CAS）（待配置）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "安全断言认证（SAML）（待配置）" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "国密数字证书（待配置）" })).toBeDisabled();
  });

  it("统一身份状态不返回提供方时不展示本地伪造方式", async () => {
    loginTenantDirectoryState = {
      data: {
        primaryTenants: [{ tenantId: "t-hospital", name: "集团总院", kind: "CUSTOMER" }],
        platformTenant: { tenantId: "t-1", name: "平台治理入口（唯一内置）", kind: "PLATFORM" },
        hasCustomerTenants: true,
      },
      isLoading: false,
      isError: false,
    };
    delegatedAuthStatusState = {
      data: {
        mode: "BOTH",
        enabled: true,
        status: "NOT_CONNECTED",
        providers: [],
        message: "院方统一身份入口已开放，请由信息科在身份来源完成配置后启用。",
      },
      isLoading: false,
      isError: false,
    };
    render(<Login />);

    fireEvent.click(screen.getByRole("button", { name: "院方统一身份认证" }));

    expect(
      await screen.findByText("当前未返回统一身份方式，暂不展示登录跳转入口。"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "开放式身份认证（OIDC）（待配置）" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "统一认证服务（CAS）（待配置）" }),
    ).not.toBeInTheDocument();
  });
});
