import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AuthoringAssets from "./AuthoringAssets";

const apiMocks = vi.hoisted(() => ({
  useAuthoringAssets: vi.fn(),
  usePackages: vi.fn(),
  updateProfile: vi.fn(),
  favorite: vi.fn(),
  unfavorite: vi.fn(),
  clone: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: {
      permissions: [{ code: "rule.write" }, { code: "pathway.write" }],
      menuKeys: ["authoring-assets"],
    },
  }),
  useAuthoringAssets: (...args: unknown[]) => apiMocks.useAuthoringAssets(...args),
  usePackages: (...args: unknown[]) => apiMocks.usePackages(...args),
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
  useCloneAuthoringAsset: () => ({
    mutateAsync: apiMocks.clone,
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
    apiMocks.clone.mockReset();
    apiMocks.useAuthoringAssets.mockReset();
    apiMocks.usePackages.mockReset();
    apiMocks.useAuthoringAssets.mockReturnValue({
      data: {
        items: [
          {
            assetType: "CONDITION_FRAGMENT",
            assetId: "frag-ckd",
            assetCode: "FRAG.CKD",
            name: "CKD 条件片段",
            category: "慢病",
            tags: ["复用"],
            version: "1",
            status: "ACTIVE",
            packageVersion: "pkg-2026.06",
            favorite: false,
            cloneable: true,
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
            packageVersion: "pkg-2026.06",
            favorite: true,
            cloneable: false,
            updatedAt: "2026-06-07T00:00:00Z",
          },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    apiMocks.usePackages.mockReturnValue({
      data: {
        items: [
          {
            packageId: "pkg-rule-1",
            tenantId: "t-1",
            packageCode: "PKG.RULE",
            packageVersion: "pkg-2026.06",
            name: "规则配置包",
            description: "",
            accessPolicy: "OPEN",
            status: "ACTIVE",
            createdAt: "2026-06-01T00:00:00Z",
            createdBy: "tester",
            updatedAt: "2026-06-01T00:00:00Z",
            updatedBy: "tester",
            traceId: "trace-pkg",
            assetTypes: ["CONDITION_FRAGMENT", "RULE"],
            itemCount: 2,
          },
        ],
      },
      isLoading: false,
      isError: false,
    });
  });

  it("searches unified assets and saves category tags", async () => {
    apiMocks.updateProfile.mockResolvedValue({ assetId: "frag-ckd" });

    renderPage();

    expect(screen.getByRole("heading", { name: "统一资产库" })).toBeInTheDocument();
    expect(screen.getByText("CKD 条件片段")).toBeInTheDocument();

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
        assetType: "CONDITION_FRAGMENT",
        assetId: "frag-ckd",
        request: { category: "肾病", tags: ["CKD", "复用"] },
      });
    });
  });

  it("favorites and clones only cloneable assets", async () => {
    apiMocks.favorite.mockResolvedValue({ favorite: true });
    apiMocks.unfavorite.mockResolvedValue({ favorite: false });
    apiMocks.clone.mockResolvedValue({ clonedAssetId: "frag-copy" });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "收藏" }));
    expect(apiMocks.favorite).toHaveBeenCalledWith({
      assetType: "CONDITION_FRAGMENT",
      assetId: "frag-ckd",
    });

    await userEvent.click(screen.getByRole("button", { name: "取消收藏" }));
    expect(apiMocks.unfavorite).toHaveBeenCalledWith({
      assetType: "RULE",
      assetId: "rule-ckd",
    });

    expect(screen.getByRole("button", { name: "克隆不可用" })).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "克隆" }));
    fireEvent.change(screen.getByLabelText("新编码"), {
      target: { value: "FRAG.CKD.COPY" },
    });
    fireEvent.change(screen.getByLabelText("新名称"), {
      target: { value: "CKD 条件片段副本" },
    });
    fireEvent.change(screen.getByLabelText("新版本"), { target: { value: "1" } });
    expect(screen.getByText("pkg-2026.06 · 规则配置包")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "另存为草稿" }));

    await waitFor(() => {
      expect(apiMocks.usePackages).toHaveBeenCalledWith({
        page: 1,
        size: 20,
        assetType: "CONDITION_FRAGMENT",
        keyword: "pkg-2026.06",
      });
      expect(apiMocks.usePackages).not.toHaveBeenCalledWith({
        page: 1,
        size: 100,
        assetType: "CONDITION_FRAGMENT",
      });
      expect(apiMocks.clone).toHaveBeenCalledWith({
        assetType: "CONDITION_FRAGMENT",
        assetId: "frag-ckd",
        request: {
          newCode: "FRAG.CKD.COPY",
          newName: "CKD 条件片段副本",
          newVersion: 1,
          packageVersion: "pkg-2026.06",
        },
      });
    });
  });

  it("blocks cloning when the selected package version is not loaded from package selector", async () => {
    apiMocks.usePackages.mockReturnValue({
      data: { items: [] },
      isLoading: false,
      isError: false,
    });

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "克隆" }));
    fireEvent.change(screen.getByLabelText("新编码"), {
      target: { value: "FRAG.CKD.COPY" },
    });
    fireEvent.change(screen.getByLabelText("新名称"), {
      target: { value: "CKD 条件片段副本" },
    });
    fireEvent.change(screen.getByLabelText("新版本"), { target: { value: "1" } });
    await userEvent.click(screen.getByRole("button", { name: "另存为草稿" }));

    expect(await screen.findByText("请选择已存在的配置包版本。")).toBeInTheDocument();
    expect(apiMocks.clone).not.toHaveBeenCalled();
  });
});
