import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import TenantOnboarding from "./TenantOnboarding";
import {
  useActivateOnboardingReadiness,
  useBranding,
  useCreateOrgUnit,
  useOnboardingReadiness,
  useOrgUnits,
  useUpdateBranding,
  type ImplementationStep,
  type OnboardingReadiness,
} from "@/shared/api/hooks";

vi.mock("@/shared/api/hooks", () => ({
  useOrgUnits: vi.fn(),
  useCreateOrgUnit: vi.fn(),
  useBranding: vi.fn(),
  useUpdateBranding: vi.fn(),
  useOnboardingReadiness: vi.fn(),
  useActivateOnboardingReadiness: vi.fn(),
}));

function renderPage(page: ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <AntdApp>{page}</AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

const baseOrgUnits = {
  items: [
    {
      id: "org-tenant",
      level: "TENANT",
      code: "T-1",
      name: "平台主租户",
      parentId: null,
      status: "ACTIVE",
    },
    {
      id: "org-hospital",
      level: "HOSPITAL",
      code: "H-1",
      name: "人民医院",
      parentId: "org-group",
      status: "ACTIVE",
    },
  ],
  page: 1,
  size: 100,
  total: 2,
};

const blockedReadiness: OnboardingReadiness = {
  tenantId: "tenant-A",
  ready: false,
  blockers: ["组织树缺少租户根或医院节点", "尚未配置实施用户"],
  checkedAt: "2026-06-03T00:00:00Z",
  steps: [
    {
      key: "organization",
      title: "组织树",
      status: "BLOCKED",
      blockers: ["组织树缺少租户根或医院节点"],
      targetPath: "/tenant/onboarding",
      evidence: null,
    },
    {
      key: "users",
      title: "实施用户",
      status: "BLOCKED",
      blockers: ["尚未配置实施用户"],
      targetPath: "/admin/users",
      evidence: null,
    },
  ],
};

function readySteps(): ImplementationStep[] {
  return blockedReadiness.steps.map((step) => ({
    ...step,
    status: "DONE",
    blockers: [],
    evidence: "已完成",
  }));
}

function mockHooks(overrides?: {
  readiness?: OnboardingReadiness;
  activate?: () => Promise<unknown>;
}) {
  vi.mocked(useOrgUnits).mockReturnValue({
    data: baseOrgUnits,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useCreateOrgUnit).mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as never);
  vi.mocked(useBranding).mockReturnValue({
    data: {
      tenantId: "tenant-A",
      hospitalName: "人民医院",
      logoUrl: "",
      themeColor: "var(--mk-theme-navy)",
      expertMode: false,
      customBrandingJson: "{}",
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useUpdateBranding).mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useOnboardingReadiness).mockReturnValue({
    data: overrides?.readiness ?? blockedReadiness,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useActivateOnboardingReadiness).mockReturnValue({
    mutateAsync: vi.fn(overrides?.activate ?? (() => Promise.resolve(overrides?.readiness))),
    isPending: false,
  } as never);
}

describe("TenantOnboarding", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHooks();
  });

  it("shows backend onboarding readiness blockers and keeps activation disabled", () => {
    renderPage(<TenantOnboarding />);

    expect(screen.getByRole("heading", { name: "租户开通" })).toBeInTheDocument();
    expect(screen.getByText("开通就绪门未通过")).toBeInTheDocument();
    expect(screen.getByText("组织树缺少租户根或医院节点")).toBeInTheDocument();
    expect(screen.getByText("尚未配置实施用户")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /开通租户/ })).toBeDisabled();
  });

  it("activates only after the backend readiness gate is ready", async () => {
    const activate = vi.fn().mockResolvedValue({
      ...blockedReadiness,
      ready: true,
      blockers: [],
      steps: blockedReadiness.steps.map((step) => ({
        ...step,
        status: "DONE",
        blockers: [],
        evidence: "已完成",
      })),
    });
    mockHooks({
      readiness: {
        ...blockedReadiness,
        ready: true,
        blockers: [],
        steps: readySteps(),
      },
      activate,
    });

    renderPage(<TenantOnboarding />);
    await userEvent.click(screen.getByRole("button", { name: /开通租户/ }));

    await waitFor(() => expect(activate).toHaveBeenCalledTimes(1));
  });

  it("renders an error state when organization or readiness APIs fail", () => {
    vi.mocked(useOnboardingReadiness).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: vi.fn(),
    } as never);

    renderPage(<TenantOnboarding />);

    expect(screen.getByText("租户开通状态读取失败")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
  });
});
