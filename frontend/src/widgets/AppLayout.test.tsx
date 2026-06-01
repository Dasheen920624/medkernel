import { fireEvent, render, screen, within } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AppLayout } from "./AppLayout";

const originalInnerWidth = window.innerWidth;
const originalMatchMedia = window.matchMedia;
const securityProfileState = vi.hoisted(() => ({
  value: {
    data: undefined as
      | {
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
          dataScope: Record<string, string | null>;
          mustChangePwd?: boolean;
          mfaRequired?: boolean;
          mfaBound?: boolean;
        }
      | undefined,
  },
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => securityProfileState.value,
  useAuditSnapshot: () => ({ mutate: vi.fn(), isPending: false }),
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

function renderLayout(initialPath = "/terminology/mapping") {
  return render(
    <ConfigProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<div>工作台内容</div>} />
            <Route path="/terminology/mapping" element={<div>字典映射内容</div>} />
            <Route path="/qc/dashboard" element={<div>质控驾驶舱内容</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ConfigProvider>,
  );
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

function permissionProfile(menuKeys: string[]) {
  return {
    menuKeys,
    roles: [],
    permissions: menuKeys.map(menuPermission),
    environmentKeys: ["production"],
    dataScope: {},
  };
}

beforeEach(() => {
  securityProfileState.value = {
    data: permissionProfile([
      "workbench",
      "pilot-setup",
      "clinical-run",
      "quality-improve",
      "compliance-ops",
      "advanced-tools",
    ]),
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
});

describe("AppLayout", () => {
  it("renders route title and metadata-backed side menu", () => {
    mockViewport(1280);
    renderLayout();

    expect(screen.getAllByText("字典映射").length).toBeGreaterThan(0);
    expect(screen.getAllByText("试点准备").length).toBeGreaterThan(0);
    expect(screen.getByText("字典映射内容")).toBeInTheDocument();
  });

  it("renders nested routes as one breadcrumb line in the header", () => {
    mockViewport(1280);
    const { container } = renderLayout();

    const header = container.querySelector(".mk-app-header");

    expect(header).not.toBeNull();
    expect(within(header as HTMLElement).getByText("试点准备")).toBeInTheDocument();
    expect(within(header as HTMLElement).getAllByText("字典映射")).toHaveLength(1);
    expect(header?.querySelector(".mk-route-title")).toBeNull();
  });

  it("uses drawer navigation on mobile width", () => {
    mockViewport(390);
    renderLayout();

    expect(document.querySelector(".ant-layout-sider")).toBeNull();
    expect(screen.getByText("字典映射内容")).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button")[0]);

    expect(screen.getAllByText("试点准备").length).toBeGreaterThan(0);
  });

  it("filters primary menus by granted menu permission codes", () => {
    securityProfileState.value = {
      data: permissionProfile(["quality-improve"]),
    };
    mockViewport(1280);
    renderLayout("/qc/dashboard");

    expect(screen.queryByText("试点准备")).toBeNull();
    expect(screen.getAllByText("质控改进").length).toBeGreaterThan(0);
    expect(screen.getByText("质控驾驶舱内容")).toBeInTheDocument();
  });

  it("does not display a hard-coded identity beside the effective permission profile", () => {
    mockViewport(1280);
    renderLayout();

    expect(screen.queryByText("医务处 · 张三")).toBeNull();
  });

  it("keeps protected menus hidden while the security profile is unavailable", () => {
    securityProfileState.value = { data: undefined };
    mockViewport(1280);
    renderLayout();

    const navigation = document.querySelector(".ant-menu");
    expect(navigation).not.toBeNull();
    expect(within(navigation as HTMLElement).queryByText("试点准备")).toBeNull();
    expect(within(navigation as HTMLElement).queryByText("工作台")).toBeNull();
  });

  it("does not render the workbench before an effective permission profile is available", () => {
    securityProfileState.value = { data: undefined };
    mockViewport(1280);
    renderLayout("/dashboard");

    expect(screen.queryByText("工作台内容")).toBeNull();
    expect(screen.getByText("正在核验权限")).toBeInTheDocument();
  });

  it("blocks direct business route entry until first password and MFA setup are complete", () => {
    securityProfileState.value = {
      data: {
        ...permissionProfile(["workbench"]),
        mustChangePwd: true,
        mfaRequired: true,
        mfaBound: false,
      },
    };
    mockViewport(1280);
    renderLayout("/dashboard");

    expect(screen.queryByText("工作台内容")).toBeNull();
    expect(screen.getByText("需要完成首次安全设置")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "继续设置" })).toBeInTheDocument();
  });

  it("blocks direct entry to a page outside the granted menu scope", () => {
    securityProfileState.value = {
      data: permissionProfile(["clinical-run"]),
    };
    mockViewport(1280);
    renderLayout();

    expect(screen.queryByText("字典映射内容")).toBeNull();
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
  });

  it("uses backend menuKeys to authorize direct entry to the granted menu section", () => {
    securityProfileState.value = {
      data: {
        ...permissionProfile(["pilot-setup"]),
        permissions: [],
      },
    };
    mockViewport(1280);
    renderLayout();

    expect(screen.getByText("字典映射内容")).toBeInTheDocument();
    expect(screen.queryByText("当前权限不足")).toBeNull();
  });

  it("opens the command palette from the global keyboard shortcut", () => {
    mockViewport(1280);
    renderLayout();

    fireEvent.keyDown(window, { key: "k", ctrlKey: true });

    expect(screen.getByPlaceholderText("搜索菜单")).toBeInTheDocument();
  });
});
