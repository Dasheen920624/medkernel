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
        }
      | undefined,
  },
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => securityProfileState.value,
  useAuditSnapshot: () => ({ mutate: vi.fn(), isPending: false }),
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

beforeEach(() => {
  securityProfileState.value = {
    data: {
      menuKeys: [
        "workbench",
        "pilot-setup",
        "clinical-run",
        "quality-improve",
        "compliance-ops",
        "advanced-tools",
      ],
      roles: [],
      permissions: [],
      environmentKeys: ["production"],
      dataScope: {},
    },
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

  it("filters primary menus by security profile menu keys", () => {
    securityProfileState.value = {
      data: {
        menuKeys: ["quality-improve"],
        roles: [],
        permissions: [],
        environmentKeys: ["production"],
        dataScope: {},
      },
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
    expect(screen.getAllByText("工作台").length).toBeGreaterThan(0);
  });

  it("does not render the workbench before an effective permission profile is available", () => {
    securityProfileState.value = { data: undefined };
    mockViewport(1280);
    renderLayout("/dashboard");

    expect(screen.queryByText("工作台内容")).toBeNull();
    expect(screen.getByText("正在核验权限")).toBeInTheDocument();
  });

  it("blocks direct entry to a page outside the granted menu scope", () => {
    securityProfileState.value = {
      data: {
        menuKeys: ["clinical-run"],
        roles: [],
        permissions: [],
        environmentKeys: ["production"],
        dataScope: {},
      },
    };
    mockViewport(1280);
    renderLayout();

    expect(screen.queryByText("字典映射内容")).toBeNull();
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
  });
});
