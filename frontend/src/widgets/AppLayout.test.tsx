import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AUTH_SESSION_EVENT_STORAGE_KEY } from "@/shared/auth/sessionEvents";
import { AppLayout } from "./AppLayout";

const originalInnerWidth = window.innerWidth;
const originalMatchMedia = window.matchMedia;
const securityProfileState = vi.hoisted(() => ({
  value: {
    data: undefined as
      | {
          userId: string;
          username?: string;
          menuKeys: string[];
          roles: Array<{ code: string; displayName: string }>;
          permissions: Array<{
            code: string;
            dimension: string;
            target: string;
            displayName: string;
            risk: string;
          }>;
          environmentKeys: string[];
          dataScope: {
            tenantId: string | null;
            groupId?: string | null;
            hospitalId?: string | null;
            campusId?: string | null;
            siteId?: string | null;
            departmentId?: string | null;
            specialtyId?: string | null;
          };
          mustChangePwd?: boolean;
          mfaRequired?: boolean;
          mfaBound?: boolean;
          mfaVerified?: boolean;
        }
      | undefined,
  },
}));
const authMutationState = vi.hoisted(() => ({
  logout: vi.fn(),
  changePassword: vi.fn(),
  renewSession: vi.fn(),
}));
const sessionStatusState = vi.hoisted(() => ({
  value: {
    data: {
      remainingSeconds: 120,
      idleTimeoutSeconds: 60,
      warningSeconds: 10,
      maxSessionSeconds: 300,
      maxSessionRemainingSeconds: 300,
      serverTime: "2026-06-01T00:00:00Z",
    },
  },
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => securityProfileState.value,
  useAuditSnapshot: () => ({ mutate: vi.fn(), isPending: false }),
  useChangePassword: () => ({ mutateAsync: authMutationState.changePassword, isPending: false }),
  useLogout: () => ({ mutateAsync: authMutationState.logout, isPending: false }),
  useSessionStatus: () => sessionStatusState.value,
  useRenewSession: () => ({ mutateAsync: authMutationState.renewSession, isPending: false }),
  useThemePreference: () => ({ data: undefined }),
  useSaveThemePreference: () => ({ mutateAsync: vi.fn() }),
}));

function mockViewport(width: number) {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    writable: true,
    value: width,
  });

  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: (query: string) => ({
      matches: matchesMediaQuery(query, width),
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

function matchesMediaQuery(query: string, width: number) {
  const minWidth = query.match(/min-width:\s*(\d+)px/);
  const maxWidth = query.match(/max-width:\s*(\d+)px/);
  if (minWidth && width < Number(minWidth[1])) {
    return false;
  }
  if (maxWidth && width > Number(maxWidth[1])) {
    return false;
  }
  return Boolean(minWidth || maxWidth);
}

async function renderLayout(initialPath = "/terminology/mapping") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const createUi = () => (
    <ConfigProvider theme={{ token: { motion: false } }}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter
            initialEntries={[initialPath]}
            future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
          >
            <Routes>
              <Route path="/login" element={<div>登录页入口</div>} />
              <Route element={<AppLayout />}>
                <Route path="/dashboard" element={<div>工作台内容</div>} />
                <Route path="/terminology/mapping" element={<div>字典映射内容</div>} />
                <Route path="/qc/dashboard" element={<div>质控驾驶舱内容</div>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
  const view = render(createUi());
  await act(async () => {
    await Promise.resolve();
  });
  return {
    ...view,
    queryClient,
    rerenderLayout: async () => {
      view.rerender(createUi());
      await act(async () => {
        await Promise.resolve();
      });
    },
  };
}

function menuPermission(code: string) {
  return {
    code: `menu.${code}`,
    dimension: "MENU",
    target: code,
    displayName: `查看${code}`,
    risk: "LOW",
  };
}

function actionPermission(code: string) {
  return {
    code,
    dimension: "ACTION",
    target: code,
    displayName: `执行${code}`,
    risk: "LOW",
  };
}

function permissionProfile(menuKeys: string[]) {
  const hasTerminologyMapping = menuKeys.includes("terminology-mapping");
  const hasAdapterHub = menuKeys.includes("adapter-hub");
  const hasQualityDashboard = menuKeys.includes("qc-dashboard");
  return {
    userId: "doctor-1",
    username: "chen.ming",
    menuKeys,
    roles: [
      { code: "clinical-user", displayName: "临床医生" },
      ...(hasTerminologyMapping ? [{ code: "platform-admin", displayName: "信息科" }] : []),
      ...(hasAdapterHub ? [{ code: "platform-admin", displayName: "信息科" }] : []),
    ],
    permissions: [
      ...menuKeys.map(menuPermission),
      ...(hasTerminologyMapping
        ? ["term.read", "term.write", "term.publish"].map(actionPermission)
        : []),
      ...(hasAdapterHub
        ? ["integration.read", "integration.write", "integration.execute"].map(actionPermission)
        : []),
      ...(hasQualityDashboard ? [actionPermission("evaluation.read")] : []),
    ],
    environmentKeys: ["production"],
    dataScope: { tenantId: "t-1", hospitalId: "h-1", departmentId: "d-1" },
  };
}

function superAdminProfile() {
  return {
    ...permissionProfile(allMenuKeys),
    userId: "system-superadmin-1",
    username: "medkernel",
    roles: [{ code: "system-superadmin", displayName: "内置超级管理员" }],
    permissions: [
      ...allMenuKeys.map(menuPermission),
      actionPermission("knowledge.read"),
      {
        code: "workbench:readiness:view",
        dimension: "ACTION",
        target: "workbench:readiness:view",
        displayName: "查看验收自检",
        risk: "LOW",
      },
    ],
    dataScope: { tenantId: "t-1", hospitalId: null, departmentId: null },
  };
}

const allMenuKeys = [
  "workbench",
  "implementation-guide",
  "tenant-onboarding",
  "runtime-releases",
  "pathway-templates",
  "rule-definitions",
  "terminology-mapping",
  "adapter-hub",
  "mpi",
  "patient-pathways",
  "cdss-fatigue",
  "workflow-todos",
  "notifications",
  "clinical-followup",
  "qc-dashboard",
  "qc-alerts",
  "insurance-audit",
  "qc-eval-sets",
  "knowledge-governance",
  "institution-knowledge",
  "diagnosis-knowledge",
  "knowledge-production",
  "admin-users",
  "identity-bindings",
  "admin-audit",
  "security-baseline",
  "system-providers",
  "notification-settings",
  "provenance",
  "graph-explore",
  "ai-workflows",
  "domestic-check",
  "dev-console",
];

beforeEach(() => {
  authMutationState.logout.mockReset();
  authMutationState.changePassword.mockReset();
  authMutationState.renewSession.mockReset();
  authMutationState.logout.mockResolvedValue(undefined);
  authMutationState.changePassword.mockResolvedValue(undefined);
  authMutationState.renewSession.mockResolvedValue(sessionStatusState.value.data);
  sessionStatusState.value = {
    data: {
      remainingSeconds: 120,
      idleTimeoutSeconds: 60,
      warningSeconds: 10,
      maxSessionSeconds: 300,
      maxSessionRemainingSeconds: 300,
      serverTime: "2026-06-01T00:00:00Z",
    },
  };
  securityProfileState.value = {
    data: permissionProfile(allMenuKeys),
  };
});

afterEach(() => {
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    writable: true,
    value: originalInnerWidth,
  });
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: originalMatchMedia,
  });
  vi.useRealTimers();
});

describe("AppLayout", () => {
  it("renders route title and metadata-backed side menu", async () => {
    mockViewport(1280);
    await renderLayout();

    expect(screen.getAllByText("术语与字典").length).toBeGreaterThan(0);
    expect(screen.getAllByText("知识治理").length).toBeGreaterThan(0);
    expect(screen.getByText("字典映射内容")).toBeInTheDocument();
  });

  it("renders nested routes as one breadcrumb line in the header", async () => {
    mockViewport(1280);
    const { container } = await renderLayout();

    const header = container.querySelector(".mk-app-header");

    expect(header).not.toBeNull();
    expect(within(header as HTMLElement).getByText("知识治理")).toBeInTheDocument();
    expect(within(header as HTMLElement).getAllByText("术语与字典")).toHaveLength(1);
    expect(header?.querySelector(".mk-route-title")).toBeNull();
  });

  it("uses drawer navigation on mobile width", async () => {
    mockViewport(390);
    await renderLayout();

    expect(document.querySelector(".ant-layout-sider")).toBeNull();
    expect(screen.getByText("字典映射内容")).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(screen.getAllByRole("button")[0]);
      await Promise.resolve();
    });

    expect(screen.getAllByText("知识治理").length).toBeGreaterThan(0);
  });

  it("filters primary menus by granted menu permission codes", async () => {
    securityProfileState.value = {
      data: permissionProfile(["qc-dashboard"]),
    };
    mockViewport(1280);
    await renderLayout("/qc/dashboard");

    expect(screen.queryByText("知识治理")).toBeNull();
    expect(screen.getAllByText("质量管理").length).toBeGreaterThan(0);
    expect(screen.getByText("质控驾驶舱内容")).toBeInTheDocument();
  });

  it("does not display a hard-coded identity beside the effective permission profile", async () => {
    mockViewport(1280);
    await renderLayout();

    expect(screen.queryByText("医务处 · 张三")).toBeNull();
  });

  it("keeps protected menus hidden while the security profile is unavailable", async () => {
    securityProfileState.value = { data: undefined };
    mockViewport(1280);
    await renderLayout();

    const navigation = document.querySelector(".ant-menu");
    expect(navigation).not.toBeNull();
    expect(within(navigation as HTMLElement).queryByText("知识治理")).toBeNull();
    expect(within(navigation as HTMLElement).queryByText("工作台")).toBeNull();
  });

  it("expands second-level menus after the security profile arrives asynchronously", async () => {
    securityProfileState.value = { data: undefined };
    mockViewport(1280);
    const { rerenderLayout } = await renderLayout("/dashboard");

    expect(screen.queryByText("术语与字典")).toBeNull();

    securityProfileState.value = {
      data: permissionProfile(["workbench", "terminology-mapping"]),
    };
    await rerenderLayout();

    const navigation = document.querySelector(".ant-layout-sider .ant-menu");
    expect(navigation).not.toBeNull();
    await waitFor(() =>
      expect(within(navigation as HTMLElement).getByText("知识治理")).toBeInTheDocument(),
    );
    expect(within(navigation as HTMLElement).getByText("术语与字典")).toBeInTheDocument();
  });

  it("does not render the workbench before an effective permission profile is available", async () => {
    securityProfileState.value = { data: undefined };
    mockViewport(1280);
    await renderLayout("/dashboard");

    expect(screen.queryByText("工作台内容")).toBeNull();
    expect(screen.getByText("正在核验权限")).toBeInTheDocument();
  });

  it("blocks direct business route entry until first password and MFA setup are complete", async () => {
    securityProfileState.value = {
      data: {
        ...permissionProfile(["workbench"]),
        mustChangePwd: true,
        mfaRequired: true,
        mfaBound: false,
      },
    };
    mockViewport(1280);
    await renderLayout("/dashboard");

    expect(screen.queryByText("工作台内容")).toBeNull();
    expect(screen.getByText("需要完成首次安全设置")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "继续设置" })).toBeInTheDocument();
  });

  it("blocks direct business route entry when MFA is bound but this session is unverified", async () => {
    securityProfileState.value = {
      data: {
        ...permissionProfile(["workbench"]),
        mustChangePwd: false,
        mfaRequired: true,
        mfaBound: true,
        mfaVerified: false,
      },
    };
    mockViewport(1280);
    await renderLayout("/dashboard");

    expect(screen.queryByText("工作台内容")).toBeNull();
    expect(screen.getByText("需要完成首次安全设置")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "继续设置" })).toBeInTheDocument();
  });

  it("blocks direct entry to a page outside the granted menu scope", async () => {
    securityProfileState.value = {
      data: permissionProfile(["workbench", "mpi", "patient-pathways"]),
    };
    mockViewport(1280);
    await renderLayout();

    expect(screen.queryByText("字典映射内容")).toBeNull();
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
  });

  it("requires terminology action permissions beyond the backend menu key", async () => {
    securityProfileState.value = {
      data: {
        ...permissionProfile(["terminology-mapping"]),
        permissions: [],
      },
    };
    mockViewport(1280);
    await renderLayout();

    expect(screen.queryByText("字典映射内容")).toBeNull();
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
  });

  it("lets the built-in superadmin open the dashboard through RBAC permissions", async () => {
    securityProfileState.value = { data: superAdminProfile() };
    mockViewport(1280);

    await renderLayout("/dashboard");

    expect(screen.getByText("工作台内容")).toBeInTheDocument();
    expect(screen.queryByText("当前权限不足")).toBeNull();
  });

  it("opens the command palette from the global keyboard shortcut", async () => {
    mockViewport(1280);
    await renderLayout();

    fireEvent.keyDown(window, { key: "k", ctrlKey: true });

    expect(screen.getByPlaceholderText("搜索菜单")).toBeInTheDocument();
  });

  it("classifies advanced capabilities in the normal sidebar and command palette", async () => {
    securityProfileState.value = { data: superAdminProfile() };
    mockViewport(1280);
    await renderLayout();

    const navigation = document.querySelector(".ant-layout-sider");
    expect(navigation).not.toBeNull();
    expect(within(navigation as HTMLElement).queryByText("高级工具")).toBeNull();
    expect(within(navigation as HTMLElement).getByText("来源与血缘")).toBeInTheDocument();

    fireEvent.keyDown(window, { key: "k", ctrlKey: true });
    fireEvent.change(screen.getByPlaceholderText("搜索菜单"), {
      target: { value: "来源" },
    });

    expect((await screen.findAllByText("来源与血缘")).length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText("高级工具")).toBeNull();
  });

  it("places message notifications in the header and notification preferences in the user menu", async () => {
    mockViewport(1280);
    await renderLayout();

    expect(screen.getByRole("button", { name: "消息通知" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "当前用户菜单" }));

    expect(await screen.findByRole("menuitem", { name: /通知偏好/ })).toBeInTheDocument();
  });

  it("shows the authenticated user, role and organization in a header menu", async () => {
    mockViewport(1280);
    await renderLayout();

    fireEvent.click(screen.getByRole("button", { name: "当前用户菜单" }));

    await screen.findByRole("menuitem", { name: /修改密码/ });
    expect(screen.getAllByText("chen.ming").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("临床医生").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("服务空间 t-1 / 医院 h-1 / 科室 d-1")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /修改密码/ })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /退出登录/ })).toBeInTheDocument();
  });

  it("changes password from the user menu through the authenticated self-service endpoint", async () => {
    mockViewport(1280);
    await renderLayout();

    fireEvent.click(screen.getByRole("button", { name: "当前用户菜单" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: /修改密码/ }));
    fireEvent.change(screen.getByPlaceholderText("请输入当前密码"), {
      target: { value: "Old@2026pw" },
    });
    fireEvent.change(screen.getByPlaceholderText("请输入新密码"), {
      target: { value: "New@2026pw" },
    });
    fireEvent.change(screen.getByPlaceholderText("再次输入新密码"), {
      target: { value: "New@2026pw" },
    });
    fireEvent.click(screen.getByRole("button", { name: "保存修改" }));

    await waitFor(() =>
      expect(authMutationState.changePassword).toHaveBeenCalledWith({
        oldPassword: "Old@2026pw",
        newPassword: "New@2026pw",
      }),
    );
  });

  it("confirms logout, calls the backend logout endpoint, clears cached state and returns to login", async () => {
    mockViewport(1280);
    const { queryClient } = await renderLayout("/dashboard");
    queryClient.setQueryData(["security", "me"], { userId: "doctor-1" });

    fireEvent.click(screen.getByRole("button", { name: "当前用户菜单" }));
    fireEvent.click(await screen.findByRole("menuitem", { name: /退出登录/ }));
    fireEvent.click(await screen.findByRole("button", { name: "确认退出" }));

    await waitFor(() => expect(authMutationState.logout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText("登录页入口")).toBeInTheDocument());
    expect(queryClient.getQueryData(["security", "me"])).toBeUndefined();
  });

  it("redirects to login and clears cached state when any API request reports 401", async () => {
    mockViewport(1280);
    const { queryClient } = await renderLayout("/dashboard");
    queryClient.setQueryData(["security", "me"], { userId: "doctor-1" });

    act(() => {
      window.dispatchEvent(new CustomEvent("medkernel:auth-required"));
    });

    await waitFor(() => expect(screen.getByText("登录页入口")).toBeInTheDocument());
    expect(queryClient.getQueryData(["security", "me"])).toBeUndefined();
  });

  it("synchronizes logout from another tab through the approved storage event", async () => {
    mockViewport(1280);
    const { queryClient } = await renderLayout("/dashboard");
    queryClient.setQueryData(["security", "me"], { userId: "doctor-1" });

    act(() => {
      window.dispatchEvent(
        new StorageEvent("storage", {
          key: AUTH_SESSION_EVENT_STORAGE_KEY,
          newValue: JSON.stringify({ reason: "logout", at: Date.now(), nonce: "tab-2" }),
        }),
      );
    });

    await waitFor(() => expect(screen.getByText("登录页入口")).toBeInTheDocument());
    expect(queryClient.getQueryData(["security", "me"])).toBeUndefined();
  });

  it("shows an idle warning before timeout and renews the backend session", async () => {
    vi.useFakeTimers();
    mockViewport(1280);
    await renderLayout("/dashboard");

    await act(async () => {
      vi.advanceTimersByTime(50_000);
    });

    expect(screen.getByText("会话即将超时")).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(screen.getByText("继续使用"));
      await Promise.resolve();
    });
    expect(authMutationState.renewSession).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("dialog", { name: "会话即将超时" })).toBeNull();
  });

  it("lets the user actively exit from the idle warning", async () => {
    vi.useFakeTimers();
    mockViewport(1280);
    const { queryClient } = await renderLayout("/dashboard");
    queryClient.setQueryData(["security", "me"], { userId: "doctor-1" });

    await act(async () => {
      vi.advanceTimersByTime(50_000);
    });

    expect(screen.getByText("会话即将超时")).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(screen.getByText("退出登录"));
      await Promise.resolve();
    });

    expect(authMutationState.logout).toHaveBeenCalledTimes(1);
    expect(screen.getByText("登录页入口")).toBeInTheDocument();
    expect(queryClient.getQueryData(["security", "me"])).toBeUndefined();
  });

  it("automatically logs out when the configured idle timeout is reached", async () => {
    vi.useFakeTimers();
    mockViewport(1280);
    const { queryClient } = await renderLayout("/dashboard");
    queryClient.setQueryData(["security", "me"], { userId: "doctor-1" });

    await act(async () => {
      vi.advanceTimersByTime(60_000);
      await Promise.resolve();
    });

    expect(authMutationState.logout).toHaveBeenCalledTimes(1);
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByText("登录页入口")).toBeInTheDocument();
    expect(queryClient.getQueryData(["security", "me"])).toBeUndefined();
  });
});
