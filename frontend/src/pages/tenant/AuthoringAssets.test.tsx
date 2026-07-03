import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import AuthoringAssets from "./AuthoringAssets";

const apiMocks = vi.hoisted(() => ({
  useAuthoringAssets: vi.fn(),
  updateProfile: vi.fn(),
  favorite: vi.fn(),
  unfavorite: vi.fn(),
}));

vi.mock("./DeclarativeAssetWorkbench", () => ({
  default: ({ evidenceDetailsEnabled }: { evidenceDetailsEnabled?: boolean }) => (
    <div>独立配置资产维护区：{evidenceDetailsEnabled ? "证据已展开" : "业务视图"}</div>
  ),
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
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
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
    expect(screen.getByText("检索、收藏、维护和复用医疗知识与配置资产")).toBeInTheDocument();
    expect(screen.queryByText(/引擎资产/)).not.toBeInTheDocument();
    expect(screen.getByText("CKD 临床路径")).toBeInTheDocument();
    expect(screen.getByText("路径资产已登记")).toBeInTheDocument();
    expect(screen.queryByText("PATH.CKD")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText("搜索资产名称或证据线索")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("搜索资产名称或证据编码")).not.toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("搜索资产名称或证据线索"), {
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

  it("keeps asset codes behind evidence details while passing the evidence state to configuration assets", async () => {
    renderPage();

    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByText("路径资产已登记")).toBeInTheDocument();
    expect(screen.getByText("规则资产已登记")).toBeInTheDocument();
    expect(screen.queryByText("PATH.CKD")).not.toBeInTheDocument();
    expect(screen.queryByText("RULE.CKD")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "配置资产维护" }));
    expect(screen.getByText("独立配置资产维护区：业务视图")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("PATH.CKD")).toBeInTheDocument();
    expect(screen.getByText("RULE.CKD")).toBeInTheDocument();
    expect(screen.getByText("独立配置资产维护区：证据已展开")).toBeInTheDocument();
  });

  it("默认隐藏随访模板资产的演练批次和运行后缀，证据详情才展示原始标识", async () => {
    apiMocks.useAuthoringAssets.mockReturnValue({
      data: {
        items: [
          {
            assetType: "FOLLOWUP",
            assetId: "followup-proxy",
            assetCode: "FUP.STAKEHOLDER.PATIENT_PROXY-MR28O43Q",
            name: "全角色患者代理随访模板（上线复演 07月02日 21时14分58秒） patient_proxy-mr28o43q",
            category: "随访模板",
            tags: [],
            version: "1",
            status: "PUBLISHED",
            favorite: false,
            updatedAt: "2026-07-02T13:14:58Z",
          },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByText("全角色患者代理随访模板")).toBeInTheDocument();
    expect(screen.queryByText(/上线复演/)).not.toBeInTheDocument();
    expect(screen.queryByText(/patient_proxy-mr28o43q/i)).not.toBeInTheDocument();
    expect(screen.queryByText("FUP.STAKEHOLDER.PATIENT_PROXY-MR28O43Q")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(
      screen.getByText(
        "全角色患者代理随访模板（上线复演 07月02日 21时14分58秒） patient_proxy-mr28o43q",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("FUP.STAKEHOLDER.PATIENT_PROXY-MR28O43Q")).toBeInTheDocument();
  });

  it("surfaces independent maintenance without removing the existing asset library", async () => {
    renderPage();

    expect(screen.getByRole("tab", { name: "专业资产库" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "配置资产维护" })).toBeInTheDocument();
    expect(screen.getByText("CKD 临床路径")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "配置资产维护" }));
    expect(screen.getByText("独立配置资产维护区：业务视图")).toBeInTheDocument();
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
