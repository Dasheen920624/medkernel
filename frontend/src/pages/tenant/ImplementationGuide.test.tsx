import { render, screen, within } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ImplementationGuide from "./ImplementationGuide";

const apiMocks = vi.hoisted(() => ({
  implementationSteps: [] as Array<Record<string, unknown>>,
  implementationStepsLoading: false,
  implementationStepsError: false,
  implementationStepsRefetch: vi.fn(),
  successPlanRefetch: vi.fn(),
  transitionSuccessStage: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useImplementationSteps: () => ({
    data: apiMocks.implementationSteps,
    isLoading: apiMocks.implementationStepsLoading,
    isError: apiMocks.implementationStepsError,
    refetch: apiMocks.implementationStepsRefetch,
  }),
  useSuccessPlan: () => ({
    data: {
      currentStage: "PREPARATION",
      healthScore: 40,
      activatedModules: "",
      activatedPathways: "",
    },
    isLoading: false,
    refetch: apiMocks.successPlanRefetch,
  }),
  useTransitionSuccessStage: () => ({
    mutateAsync: apiMocks.transitionSuccessStage,
    isPending: false,
  }),
}));

function renderGuide() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <ImplementationGuide />
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("ImplementationGuide", () => {
  beforeEach(() => {
    apiMocks.implementationSteps = [
      {
        key: "organization",
        title: "组织树",
        status: "DONE",
        blockers: [],
        targetPath: "/tenant/onboarding",
        evidence: "已存在集团、医院和科室组织",
      },
      {
        key: "users",
        title: "用户与角色",
        status: "BLOCKED",
        blockers: ["尚未创建平台管理员", "医疗引擎运营员未分配医院作用域"],
        targetPath: "/tenant/onboarding",
        evidence: null,
      },
      {
        key: "adapters",
        title: "适配器接入",
        status: "BLOCKED",
        blockers: ["HIS 适配器仍为 NOT_CONNECTED"],
        targetPath: "/adapter/hub",
        evidence: null,
      },
      {
        key: "assets",
        title: "配置资产",
        status: "DONE",
        blockers: [],
        targetPath: "/config/releases",
        evidence: "机构生效版本已启用",
      },
    ];
    apiMocks.implementationStepsLoading = false;
    apiMocks.implementationStepsError = false;
    apiMocks.implementationStepsRefetch.mockReset();
    apiMocks.successPlanRefetch.mockReset();
    apiMocks.transitionSuccessStage.mockReset();
  });

  it("renders real implementation steps with blockers and configuration links", () => {
    renderGuide();

    expect(screen.getByRole("heading", { name: "实施与验收" })).toBeInTheDocument();
    const organizationStep = screen.getByTestId("implementation-step-organization");
    expect(within(organizationStep).getByText("组织树")).toBeInTheDocument();
    expect(within(organizationStep).getByText("已存在集团、医院和科室组织")).toBeInTheDocument();
    expect(screen.getByText("尚未创建平台管理员")).toBeInTheDocument();
    expect(screen.getByText("医疗引擎运营员未分配医院作用域")).toBeInTheDocument();
    expect(screen.getByText("HIS 适配器仍为 NOT_CONNECTED")).toBeInTheDocument();

    const adapterStep = screen.getByTestId("implementation-step-adapters");
    const adapterLink = within(adapterStep).getByRole("link", { name: "前往系统接入" });
    expect(adapterLink).toHaveAttribute("href", "/adapter/hub");
  });

  it("shows an empty state instead of a fake success plan when the service returns no steps", () => {
    apiMocks.implementationSteps = [];

    renderGuide();

    expect(screen.getByText("暂无实施步骤")).toBeInTheDocument();
    expect(
      screen.getByText("当前服务机构尚未返回实施步骤，请先确认服务机构与组织范围已建立。"),
    ).toBeInTheDocument();
    expect(screen.queryByText("跨部门、跨系统的 9 步交付模型")).not.toBeInTheDocument();
  });

  it("shows an error state with retry when implementation steps cannot be loaded", () => {
    apiMocks.implementationStepsError = true;

    renderGuide();

    expect(screen.getByText("实施步骤读取失败")).toBeInTheDocument();
    expect(
      screen.getByText("请重试；若持续失败，请带追踪号联系信息科核查实施服务。"),
    ).toBeInTheDocument();
    screen.getByRole("button", { name: "重试" }).click();
    expect(apiMocks.implementationStepsRefetch).toHaveBeenCalledTimes(1);
  });
});
