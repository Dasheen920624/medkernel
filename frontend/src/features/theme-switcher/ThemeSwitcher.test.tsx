import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiClient } from "@/shared/api/client";
import { useThemeStore } from "@/shared/lib/themeStore";

import { ThemeSwitcher } from "./ThemeSwitcher";

vi.mock("@/shared/api/client", () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

function renderWithQuery(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("ThemeSwitcher", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useThemeStore.setState({ mode: "default" });
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.put).mockReset();
  });

  it("点击后展开主题菜单并写入选择结果", async () => {
    renderWithQuery(<ThemeSwitcher syncRemote={false} />);

    fireEvent.click(screen.getByRole("button", { name: "主题模式：默认" }));
    fireEvent.click(await screen.findByText("暗黑"));

    await waitFor(() => expect(useThemeStore.getState().mode).toBe("dark"));
    expect(window.localStorage.getItem("medkernel.theme.mode")).toBe("dark");
    expect(apiClient.get).not.toHaveBeenCalled();
    expect(apiClient.put).not.toHaveBeenCalled();
  });

  it("登录后从远端偏好恢复主题", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { mode: "elder", version: 2, updatedAt: "2026-06-01T00:00:00Z" } },
    });

    renderWithQuery(<ThemeSwitcher />);

    await waitFor(() => expect(useThemeStore.getState().mode).toBe("elder"));
    expect(screen.getByRole("button", { name: "主题模式：老年医生" })).toBeInTheDocument();
    expect(apiClient.get).toHaveBeenCalledWith("/experience/theme-preference");
  });

  it("远端保存失败时仍保留本地主题选择", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { mode: "default", version: 1, updatedAt: "2026-06-01T00:00:00Z" } },
    });
    vi.mocked(apiClient.put).mockRejectedValueOnce(new Error("network"));

    renderWithQuery(<ThemeSwitcher />);
    await waitFor(() => expect(apiClient.get).toHaveBeenCalled());

    fireEvent.click(screen.getByRole("button", { name: "主题模式：默认" }));
    fireEvent.click(await screen.findByText("护眼"));

    await waitFor(() => expect(useThemeStore.getState().mode).toBe("eye"));
    expect(window.localStorage.getItem("medkernel.theme.mode")).toBe("eye");
    expect(apiClient.put).toHaveBeenCalledWith("/experience/theme-preference", { mode: "eye" });
  });
});
