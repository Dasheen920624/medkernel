import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { Component, type ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";

import { AppErrorBoundary } from "./AppErrorBoundary";
import { FRONTEND_DIAGNOSTIC_EVENT } from "@/shared/lib/frontendDiagnostics";

class BrokenPage extends Component {
  override render(): ReactNode {
    throw new Error("productionCandidates.find is not a function");
  }
}

afterEach(() => {
  vi.restoreAllMocks();
});

it("replaces an unexpected route render failure with a recoverable Chinese error page", () => {
  vi.spyOn(console, "error").mockImplementation(() => undefined);
  const diagnosticListener = vi.fn();
  window.addEventListener(FRONTEND_DIAGNOSTIC_EVENT, diagnosticListener);

  render(
    <ConfigProvider>
      <AppErrorBoundary>
        <BrokenPage />
      </AppErrorBoundary>
    </ConfigProvider>,
  );

  expect(screen.getByText("页面运行异常")).toBeInTheDocument();
  expect(screen.getByText("系统已保留当前登录状态，请重新加载页面。")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "重新加载" })).toBeInTheDocument();
  expect(screen.queryByText("productionCandidates.find is not a function")).not.toBeInTheDocument();
  expect(diagnosticListener).toHaveBeenCalledWith(
    expect.objectContaining({
      detail: expect.objectContaining({
        category: "RENDER_FAILURE",
        message: "productionCandidates.find is not a function",
      }),
    }),
  );

  window.removeEventListener(FRONTEND_DIAGNOSTIC_EVENT, diagnosticListener);
});
