import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it, vi } from "vitest";

import { TenantLifecyclePanel } from "./TenantLifecyclePanel";

vi.mock("@/shared/api/hooks", () => ({
  useSuccessPlan: () => ({
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
  }),
  useTransitionSuccessStage: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

describe("TenantLifecyclePanel", () => {
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
});
