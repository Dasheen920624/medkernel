import { render, screen } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AppRouter } from "./router";

const securityProfileState = vi.hoisted(() => ({
  value: {
    data: undefined as
      | {
          userId: string;
          username: string;
          roles: Array<{
            code: string;
            displayName: string;
            source: string;
            scopeLevel: string | null;
            scopeCode: string | null;
          }>;
          permissions: Array<{
            code: string;
            dimension: string;
            target: string;
            displayName: string;
            risk: string;
          }>;
          menuKeys: string[];
          environmentKeys: string[];
          dataScope: {
            tenantId: string | null;
            groupId: string | null;
            hospitalId: string | null;
            campusId: string | null;
            siteId: string | null;
            departmentId: string | null;
            specialtyId: string | null;
          };
          mustChangePwd: boolean;
          mfaRequired: boolean;
          mfaBound: boolean;
        }
      | undefined,
    isError: true,
  },
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => securityProfileState.value,
  useAuditSnapshot: () => ({ mutate: vi.fn(), isPending: false }),
  useLogin: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDelegatedAuthStatus: () => ({
    data: {
      mode: "BOTH",
      enabled: true,
      status: "NOT_CONNECTED",
      providers: ["OIDC", "CAS", "SAML", "国密CA"],
      message: "院方统一身份入口已开放，但当前未配置真实 IdP 连接器。",
    },
    isLoading: false,
    isError: false,
  }),
  useLoginTenantDirectory: () => ({
    data: {
      primaryTenants: [{ tenantId: "t-1", name: "平台治理空间（唯一内置）", kind: "PLATFORM" }],
      platformTenant: { tenantId: "t-1", name: "平台治理空间（唯一内置）", kind: "PLATFORM" },
      hasCustomerTenants: false,
    },
    isLoading: false,
    isError: false,
  }),
  useCheckBootstrapInitToken: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCreateBootstrapAdmin: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useChangePassword: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useBindBootstrapMfa: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useLogout: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSessionStatus: () => ({ data: undefined }),
  useRenewSession: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useThemePreference: () => ({ data: undefined }),
  useSaveThemePreference: () => ({ mutateAsync: vi.fn() }),
}));

function authenticatedProfile() {
  return {
    userId: "implementation-1",
    username: "implementation.engineer",
    roles: [
      {
        code: "platform-admin",
        displayName: "医疗引擎运营员",
        source: "DEFAULT",
        scopeLevel: null,
        scopeCode: null,
      },
    ],
    permissions: [
      {
        code: "menu.workbench",
        dimension: "MENU",
        target: "workbench",
        displayName: "查看工作台",
        risk: "LOW",
      },
      {
        code: "workbench:readiness:view",
        dimension: "ACTION",
        target: "workbench:readiness:view",
        displayName: "查看验收自检",
        risk: "LOW",
      },
    ],
    menuKeys: ["workbench"],
    environmentKeys: ["production"],
    dataScope: {
      tenantId: "t-1",
      groupId: null,
      hospitalId: "h-1",
      campusId: null,
      siteId: null,
      departmentId: null,
      specialtyId: null,
    },
    mustChangePwd: false,
    mfaRequired: false,
    mfaBound: true,
  };
}

function runtimeReleaseProfile() {
  const profile = authenticatedProfile();
  return {
    ...profile,
    permissions: [
      ...profile.permissions,
      {
        code: "asset.read",
        dimension: "ACTION",
        target: "asset.read",
        displayName: "查看资产",
        risk: "LOW",
      },
    ],
    menuKeys: [...profile.menuKeys, "runtime-releases"],
  };
}

vi.mock("@/pages/Dashboard", () => ({
  default: () => <div>本周建议动作</div>,
}));

vi.mock("@/pages/Login", () => ({
  default: () => (
    <main>
      <h1>登录工作台</h1>
      <p>使用医院账号或统一身份继续</p>
    </main>
  ),
}));

vi.mock("@/pages/Bootstrap", () => ({
  default: () => (
    <main>
      <h1>首次部署接管</h1>
    </main>
  ),
}));

vi.mock("@/pages/workbench/ReadinessValidation", () => ({
  default: () => <div>运行验收自检</div>,
}));

vi.mock("@/pages/tenant/ReleaseGovernance", () => ({
  default: () => <div>发布治理</div>,
}));

function renderRouter(initialPath: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <AntdApp>
          <MemoryRouter
            initialEntries={[initialPath]}
            future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
          >
            <AppRouter />
          </MemoryRouter>
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe("AppRouter", () => {
  it("uses the login page as the default entry instead of opening the workbench", async () => {
    renderRouter("/");

    expect(await screen.findByRole("heading", { name: "登录工作台" })).toBeInTheDocument();
    expect(screen.getByText("使用医院账号或统一身份继续")).toBeInTheDocument();
    expect(screen.queryByText("本周建议动作")).toBeNull();
  });

  it("registers the first deployment bootstrap route outside the business layout", async () => {
    renderRouter("/bootstrap");

    expect(await screen.findByRole("heading", { name: "首次部署接管" })).toBeInTheDocument();
    expect(screen.queryByText("暂时无法核验权限")).toBeNull();
  });

  it("blocks direct workbench entry before an effective permission profile is available", async () => {
    securityProfileState.value = { data: undefined, isError: true };
    renderRouter("/dashboard");

    expect(await screen.findByText("暂时无法核验权限")).toBeInTheDocument();
    expect(screen.queryByText("本周建议动作")).toBeNull();
  });

  it("routes a removed StepFlow demo URL to the 404 fallback for authenticated users", async () => {
    securityProfileState.value = { data: authenticatedProfile(), isError: false };
    renderRouter("/demo/step-flow");

    expect(await screen.findByText("此功能待 W3 业务域任务实装")).toBeInTheDocument();
    expect(screen.queryByText("暂时无法核验权限")).toBeNull();
  });

  it("exposes only the canonical release governance route", async () => {
    securityProfileState.value = { data: runtimeReleaseProfile(), isError: false };
    renderRouter("/config/releases");

    expect((await screen.findAllByText("发布治理")).length).toBeGreaterThanOrEqual(2);
  });

  it("routes the WORKBENCH-02 readiness validation page through the protected layout", async () => {
    securityProfileState.value = { data: authenticatedProfile(), isError: false };
    renderRouter("/workbench/readiness-validation");

    expect(await screen.findByText("运行验收自检")).toBeInTheDocument();
    expect(screen.queryByText("此功能待 W3 业务域任务实装")).toBeNull();
  });
});
