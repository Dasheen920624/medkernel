import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TenantLifecyclePanel } from "./TenantLifecyclePanel";

const hookState = vi.hoisted(() => ({
  successPlan: {} as Record<string, unknown>,
  transitionMutation: { mutate: vi.fn(), isPending: false },
}));

vi.mock("@/shared/api/hooks", () => ({
  useSuccessPlan: () => hookState.successPlan,
  useTransitionSuccessStage: () => hookState.transitionMutation,
}));

describe("TenantLifecyclePanel", () => {
  beforeEach(() => {
    hookState.successPlan = {
      data: {
        tenantId: "platform",
        currentStage: "PILOT",
        healthScore: 82,
        activatedModules: "知识治理",
        activatedPathways: "高血压路径",
      },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    };
    hookState.transitionMutation = { mutate: vi.fn(), isPending: false };
  });

  it("stacks lifecycle steps vertically below the desktop breakpoint", () => {
    render(
      <ConfigProvider>
        <TenantLifecyclePanel />
      </ConfigProvider>,
    );

    const lifecycleTitle = screen.getByText("服务机构生命周期");
    const lifecycleCard = lifecycleTitle.closest(".ant-card");
    const steps = lifecycleCard?.querySelector(".ant-steps");

    expect(steps).toHaveClass("ant-steps-vertical");
  });

  it("keeps lifecycle load failures in hospital language", () => {
    hookState.successPlan = {
      data: undefined,
      isLoading: false,
      error: new Error("GET /api/v1/success-plan failed: ECONNREFUSED 127.0.0.1:8080"),
      refetch: vi.fn(),
    };

    render(
      <ConfigProvider>
        <TenantLifecyclePanel />
      </ConfigProvider>,
    );

    expect(screen.getByText("数据加载失败")).toBeInTheDocument();
    expect(screen.getByText(/暂时无法读取服务机构生命周期服务/)).toBeInTheDocument();
    expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/success-plan/)).not.toBeInTheDocument();
  });
});
