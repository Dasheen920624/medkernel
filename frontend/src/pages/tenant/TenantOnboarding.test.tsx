import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import TenantOnboarding from "./TenantOnboarding";
import {
  useBranding,
  useCreateOrgUnit,
  useOnboardingReadiness,
  useOrgUnits,
  useProvisionTenant,
  useSecurityProfile,
  useTenants,
  useUpdateBranding,
  type ImplementationStep,
  type OnboardingReadiness,
} from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

vi.mock("@/shared/api/hooks", () => ({
  useOrgUnits: vi.fn(),
  useCreateOrgUnit: vi.fn(),
  useBranding: vi.fn(),
  useUpdateBranding: vi.fn(),
  useOnboardingReadiness: vi.fn(),
  useProvisionTenant: vi.fn(),
  useSecurityProfile: vi.fn(),
  useTenants: vi.fn(),
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

function clickSelectOption(label: string) {
  const option = screen
    .getAllByText(label)
    .find((element) => element.classList.contains("ant-select-item-option-content"));
  if (!option) {
    throw new Error(`未找到下拉选项：${label}`);
  }
  fireEvent.click(option);
}

const baseOrgUnits = {
  items: [
    {
      id: "org-tenant",
      level: "TENANT",
      code: "T-1",
      name: "平台治理入口",
      parentId: null,
      status: "ACTIVE",
    },
    {
      id: "org-hospital",
      level: "FACILITY",
      facilityType: "HOSPITAL",
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
  blockers: ["组织树缺少服务机构根节点或医院节点", "尚未配置实施用户"],
  checkedAt: "2026-06-03T00:00:00Z",
  steps: [
    {
      key: "organization",
      title: "组织树",
      status: "BLOCKED",
      blockers: ["组织树缺少服务机构根节点或医院节点"],
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
  tenantId?: string;
  provision?: () => Promise<unknown>;
  createOrg?: (payload: unknown) => Promise<unknown>;
}) {
  vi.mocked(useSecurityProfile).mockReturnValue({
    data: {
      userId: "admin-1",
      username: "admin",
      roles: [{ code: "platform-admin" }],
      permissions: [],
      menuKeys: ["tenant-onboarding"],
      environmentKeys: [],
      dataScope: {
        tenantId: overrides?.tenantId ?? "tenant-A",
        groupId: null,
        hospitalId: null,
        campusId: null,
        siteId: null,
        departmentId: null,
        specialtyId: null,
      },
      mustChangePwd: false,
      mfaRequired: false,
      mfaBound: true,
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useTenants).mockReturnValue({
    data: [
      {
        tenantId: "tenant-A",
        name: "人民医院",
        status: "ACTIVE",
        createdAt: "2026-06-06T00:00:00Z",
      },
    ],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useProvisionTenant).mockReturnValue({
    mutateAsync: vi.fn(overrides?.provision ?? (() => Promise.resolve(undefined))),
    isPending: false,
  } as never);
  vi.mocked(useOrgUnits).mockReturnValue({
    data: baseOrgUnits,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useCreateOrgUnit).mockReturnValue({
    mutateAsync: vi.fn(overrides?.createOrg ?? (() => Promise.resolve(undefined))),
    isPending: false,
  } as never);
  vi.mocked(useBranding).mockReturnValue({
    data: {
      tenantId: "tenant-A",
      hospitalName: "人民医院",
      logoUrl: "",
      themeColor: "var(--mk-theme-navy)",
      evidenceDetailsEnabled: false,
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
}

describe("TenantOnboarding", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useEvidenceDetailsStore.getState().setEnabled(false);
    window.localStorage.clear();
    mockHooks();
  });

  it("shows onboarding readiness blockers and keeps activation disabled", () => {
    renderPage(<TenantOnboarding />);

    expect(useOrgUnits).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: "ACTIVE",
    });
    expect(screen.getByRole("heading", { name: "服务机构" })).toBeInTheDocument();
    expect(screen.getByText("实施就绪检查未通过")).toBeInTheDocument();
    expect(screen.getByText("组织树缺少服务机构根节点或医院节点")).toBeInTheDocument();
    expect(screen.getByText("尚未配置实施用户")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /开通租户/ })).not.toBeInTheDocument();
  });

  it("新增医疗机构时提交唯一组织层级和机构类型", async () => {
    const createOrg = vi.fn().mockResolvedValue(undefined);
    mockHooks({ createOrg });
    renderPage(<TenantOnboarding />);

    const panel = screen.getAllByText("新增组织节点")[0].closest(".ant-card");
    expect(panel).not.toBeNull();
    const scope = within(panel as HTMLElement);

    fireEvent.mouseDown(scope.getByRole("combobox", { name: "组织层级" }));
    clickSelectOption("医疗机构");
    await waitFor(() =>
      expect(scope.getByRole("combobox", { name: "机构类型" })).toBeInTheDocument(),
    );
    await userEvent.type(scope.getByRole("textbox", { name: "稳定组织身份" }), "HOSP-NEW");
    await userEvent.type(scope.getByRole("textbox", { name: "组织名称" }), "新建医院");
    fireEvent.mouseDown(scope.getByRole("combobox", { name: "机构类型" }));
    clickSelectOption("综合医院");
    fireEvent.mouseDown(scope.getByRole("combobox", { name: "直接上级" }));
    fireEvent.click(screen.getByText("平台治理入口（服务机构根节点）"));
    await userEvent.click(scope.getByRole("button", { name: /保存组织节点/ }));

    await waitFor(() =>
      expect(createOrg).toHaveBeenCalledWith(
        expect.objectContaining({
          level: "FACILITY",
          facilityType: "HOSPITAL",
          code: "HOSP-NEW",
          name: "新建医院",
          parentId: "org-tenant",
        }),
      ),
    );
  });

  it("renders the live readiness result without a duplicate activation action", () => {
    mockHooks({
      readiness: {
        ...blockedReadiness,
        ready: true,
        blockers: [],
        steps: readySteps(),
      },
    });

    renderPage(<TenantOnboarding />);

    expect(screen.getByText("实施就绪检查已通过")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /开通租户/ })).not.toBeInTheDocument();
  });

  it("uses implementation evidence language for the branding evidence preference", () => {
    renderPage(<TenantOnboarding />);

    fireEvent.click(screen.getByRole("tab", { name: /品牌信息/ }));
    expect(screen.getByRole("switch", { name: "默认展开证据详情" })).toBeInTheDocument();
  });

  it("keeps organization identifiers in evidence details instead of the default organization view", async () => {
    const user = userEvent.setup();

    renderPage(<TenantOnboarding />);

    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getAllByText("组织已登记").length).toBeGreaterThan(0);
    expect(screen.queryByText("H-1")).not.toBeInTheDocument();
    expect(screen.queryByText("org-group")).not.toBeInTheDocument();
    expect(screen.getByText("来自组织与任职台账")).toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("H-1")).toBeInTheDocument();
    expect(screen.getByText("org-group")).toBeInTheDocument();
  });

  it("renders an error state when organization or readiness APIs fail", () => {
    vi.mocked(useOnboardingReadiness).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      refetch: vi.fn(),
    } as never);

    renderPage(<TenantOnboarding />);

    expect(screen.getByText("机构实施状态读取失败")).toBeInTheDocument();
    expect(
      screen.getByText("请重试；若持续失败，请带追踪号联系信息科核查服务机构与组织服务。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
  });

  it("keeps empty readiness guidance in service institution language", () => {
    mockHooks({
      readiness: {
        ...blockedReadiness,
        steps: [],
      },
    });

    renderPage(<TenantOnboarding />);

    expect(screen.getByText("暂无实施就绪步骤")).toBeInTheDocument();
    expect(
      screen.getByText("当前服务机构尚未返回实施就绪步骤，请确认服务机构和组织范围已经建立。"),
    ).toBeInTheDocument();
  });

  it("仅允许平台治理入口开通服务机构", async () => {
    const provision = vi.fn().mockResolvedValue({
      tenantId: "t-renmin",
      adminUserId: "renmin-admin",
      adminUsername: "renmin-admin",
      tempPassword: "TenantPwd@9",
    });
    mockHooks({ tenantId: "t-1", provision });

    renderPage(<TenantOnboarding />);

    expect(screen.getByRole("heading", { name: "服务机构" })).toBeInTheDocument();
    expect(screen.queryByText("组织树")).not.toBeInTheDocument();
    expect(screen.getByText("人民医院")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /开通服务机构/ }));
    await userEvent.type(screen.getByLabelText("稳定服务机构身份"), "t-renmin");
    await userEvent.type(screen.getByLabelText("服务机构名称"), "人民医院");
    await userEvent.type(screen.getByLabelText("首个管理员登录名"), "renmin-admin");
    await userEvent.click(screen.getByRole("button", { name: "确认开通" }));

    await waitFor(() =>
      expect(provision).toHaveBeenCalledWith({
        tenantId: "t-renmin",
        tenantName: "人民医院",
        adminUsername: "renmin-admin",
      }),
    );
    expect(await screen.findByText("TenantPwd@9")).toBeInTheDocument();
    expect(screen.getAllByText(/服务机构/).length).toBeGreaterThan(0);
  });
});
