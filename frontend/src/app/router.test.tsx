import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AppRouter } from "./router";

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({ data: undefined, isError: true }),
  useAuditSnapshot: () => ({ mutate: vi.fn(), isPending: false }),
  useLogin: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCheckBootstrapInitToken: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCreateBootstrapAdmin: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useChangePassword: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useBindBootstrapMfa: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useThemePreference: () => ({ data: undefined }),
  useSaveThemePreference: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock("@/pages/Dashboard", () => ({
  default: () => <div>本周建议动作</div>,
}));

function renderRouter(initialPath: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <AppRouter />
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

describe("AppRouter", () => {
  it("uses the login page as the default entry instead of opening the workbench", async () => {
    renderRouter("/");

    expect(await screen.findByRole("heading", { name: "集团医疗智能中枢" })).toBeInTheDocument();
    expect(screen.queryByText("本周建议动作")).toBeNull();
  });

  it("registers the first deployment bootstrap route outside the business layout", async () => {
    renderRouter("/bootstrap");

    expect(await screen.findByRole("heading", { name: "首次部署接管" })).toBeInTheDocument();
    expect(screen.queryByText("暂时无法核验权限")).toBeNull();
  });

  it("blocks direct workbench entry before an effective permission profile is available", async () => {
    renderRouter("/dashboard");

    expect(await screen.findByText("暂时无法核验权限")).toBeInTheDocument();
    expect(screen.queryByText("本周建议动作")).toBeNull();
  });
});
