import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AuthoringAssets from "./AuthoringAssets";

const apiMocks = vi.hoisted(() => ({
  useAuthoringAssets: vi.fn(),
  updateProfile: vi.fn(),
  favorite: vi.fn(),
  unfavorite: vi.fn(),
}));

vi.mock("./DeclarativeAssetWorkbench", () => ({
  default: () => <div>独立配置资产维护区</div>,
}));

vi.mock("@/shared/ui/condition/FieldCatalogManager", () => ({
  default: ({ open }: { open: boolean }) => (open ? <div>字段目录维护抽屉</div> : null),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: {
      permissions: [
        { code: "rule.write" },
        { code: "pathway.write" },
        { code: "asset.write" },
        { code: "context.write" },
      ],
      menuKeys: ["authoring-assets"],
    },
  }),
  useAuthoringAssets: (...args: unknown[]) => apiMocks.useAuthoringAssets(...args),
  useUpdateAuthoringAssetProfile: () => ({
    mutateAsync: apiMocks.updateProfile,
    isPending: false,
  }),
  useFavoriteAuthoringAsset: () => ({
    mutateAsync: apiMocks.favorite,
    isPending: false,
  }),
  useUnfavoriteAuthoringAsset: () => ({
    mutateAsync: apiMocks.unfavorite,
    isPending: false,
  }),
}));

function renderPage() {
  render(
    <ConfigProvider>
      <AntdApp>
        <AuthoringAssets />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("AuthoringAssets", () => {
  beforeEach(() => {
    apiMocks.updateProfile.mockReset();
    apiMocks.favorite.mockReset();
    apiMocks.unfavorite.mockReset();
    apiMocks.useAuthoringAssets.mockReset();
    apiMocks.useAuthoringAssets.mockReturnValue({
      data: {
        items: [
          {
            assetType: "PATHWAY",
            assetId: "path-ckd",
            assetCode: "PATH.CKD",
            name: "CKD 临床路径",
            category: "慢病",
            tags: ["肾病"],
            version: "pv-1",
            status: "PUBLISHED",
            favorite: false,
            updatedAt: "2026-06-08T00:00:00Z",
          },
          {
            assetType: "RULE",
            assetId: "rule-ckd",
            assetCode: "RULE.CKD",
            name: "CKD 阻断规则",
            category: "ORDER",
            tags: [],
            version: "rv-1",
            status: "PUBLISHED",
            favorite: true,
            updatedAt: "2026-06-07T00:00:00Z",
          },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
  });

  it("searches unified assets and saves category tags", async () => {
    apiMocks.updateProfile.mockResolvedValue({ assetId: "path-ckd" });

    renderPage();

    expect(screen.getByRole("heading", { name: "统一资产库" })).toBeInTheDocument();
    expect(screen.getByText("CKD 临床路径")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("搜索资产编码或名称"), {
      target: { value: "CKD" },
    });
    expect(apiMocks.useAuthoringAssets).toHaveBeenLastCalledWith(
      { keyword: "CKD", size: 50 },
      { enabled: true },
    );

    await userEvent.click(screen.getAllByRole("button", { name: /编辑标签/ })[0]);
    fireEvent.change(screen.getByLabelText("分类"), { target: { value: "肾病" } });
    fireEvent.change(screen.getByLabelText("标签"), { target: { value: "CKD, 复用" } });
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(apiMocks.updateProfile).toHaveBeenCalledWith({
        assetType: "PATHWAY",
        assetId: "path-ckd",
        request: { category: "肾病", tags: ["CKD", "复用"] },
      });
    });
  });

  it("surfaces independent maintenance without removing the existing asset library", async () => {
    renderPage();

    expect(screen.getByRole("tab", { name: "专业资产库" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "配置资产维护" })).toBeInTheDocument();
    expect(screen.getByText("CKD 临床路径")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "配置资产维护" }));
    expect(screen.getByText("独立配置资产维护区")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "维护字段目录" }));
    expect(screen.getByText("字段目录维护抽屉")).toBeInTheDocument();
  });

  it("favorites and unfavorites existing assets", async () => {
    apiMocks.favorite.mockResolvedValue({ favorite: true });
    apiMocks.unfavorite.mockResolvedValue({ favorite: false });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "收藏" }));
    expect(apiMocks.favorite).toHaveBeenCalledWith({
      assetType: "PATHWAY",
      assetId: "path-ckd",
    });

    await userEvent.click(screen.getByRole("button", { name: "取消收藏" }));
    expect(apiMocks.unfavorite).toHaveBeenCalledWith({
      assetType: "RULE",
      assetId: "rule-ckd",
    });
  });
});
