import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import DeclarativeAssetWorkbench from "./DeclarativeAssetWorkbench";

const apiMocks = vi.hoisted(() => ({
  useDeclarativeAssets: vi.fn(),
  useDeclarativeAsset: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useDeclarativeAssets: (...args: unknown[]) => apiMocks.useDeclarativeAssets(...args),
  useDeclarativeAsset: (...args: unknown[]) => apiMocks.useDeclarativeAsset(...args),
  useCreateDeclarativeAsset: () => ({ mutateAsync: apiMocks.create, isPending: false }),
  useUpdateDeclarativeAsset: () => ({ mutateAsync: apiMocks.update, isPending: false }),
}));

function renderWorkbench() {
  render(
    <ConfigProvider>
      <AntdApp>
        <DeclarativeAssetWorkbench canWrite />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("DeclarativeAssetWorkbench", () => {
  beforeEach(() => {
    apiMocks.create.mockReset();
    apiMocks.update.mockReset();
    apiMocks.useDeclarativeAssets.mockReset();
    apiMocks.useDeclarativeAsset.mockReset();
    apiMocks.useDeclarativeAssets.mockReturnValue({
      data: {
        items: [
          {
            versionId: "av-vs-1",
            assetType: "VALUE_SET",
            assetIdentity: "VS.NEPHROTOXIC",
            versionNo: "V1",
            status: "DRAFT",
            organizationScope: "tenant:tenant-A",
            applicableScope: "ALL",
            sourceRef: "ATC",
            updatedAt: "2026-06-22T00:00:00Z",
          },
        ],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    apiMocks.useDeclarativeAsset.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
    });
  });

  it("shows four independently versioned asset types without package coupling", async () => {
    renderWorkbench();

    expect(screen.getByRole("tab", { name: "值集" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "公式与量表" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "医嘱套餐" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "动作卡" })).toBeInTheDocument();
    expect(screen.getByText("VS.NEPHROTOXIC")).toBeInTheDocument();
    expect(screen.getByText("V1")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "新建值集" }));
    expect(screen.queryByLabelText("草稿" + "配置" + "包")).not.toBeInTheDocument();
    expect(screen.getByLabelText("适用范围")).toHaveValue("ALL");
  });

  it("creates a typed value set instead of accepting an unstructured metadata shell", async () => {
    apiMocks.create.mockResolvedValue({ versionId: "av-vs-2" });
    renderWorkbench();

    await userEvent.click(screen.getByRole("button", { name: "新建值集" }));
    await userEvent.type(screen.getByLabelText("资产编码"), "VS.RENAL.RISK");
    expect(screen.queryByLabelText("版本号")).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("来源依据"), "国家药典");
    await userEvent.type(screen.getByLabelText("名称"), "肾风险药物");
    await userEvent.type(screen.getByLabelText("编码体系"), "ATC");
    await userEvent.type(screen.getByLabelText("成员编码"), "J01GB03");
    await userEvent.type(screen.getByLabelText("成员名称"), "庆大霉素");
    await userEvent.click(screen.getByRole("button", { name: "保存草稿" }));

    await waitFor(() => {
      expect(apiMocks.create).toHaveBeenCalledWith({
        assetType: "VALUE_SET",
        assetIdentity: "VS.RENAL.RISK",
        applicableScope: "ALL",
        sourceRef: "国家药典",
        content: {
          schemaVersion: "1.0",
          name: "肾风险药物",
          codeSystem: "ATC",
          members: [{ code: "J01GB03", display: "庆大霉素" }],
        },
      });
    });
  });

  it("creates an executable action card instead of the retired generic actions wrapper", async () => {
    apiMocks.create.mockResolvedValue({ versionId: "av-action-1" });
    renderWorkbench();

    await userEvent.click(screen.getByRole("tab", { name: "动作卡" }));
    await userEvent.click(screen.getByRole("button", { name: "新建动作卡" }));
    await userEvent.type(screen.getByLabelText("资产编码"), "ACTION.CKD.REVIEW");
    await userEvent.type(screen.getByLabelText("来源依据"), "CKD 用药安全指南");
    await userEvent.type(screen.getByLabelText("标题"), "肾功能异常处置");
    await userEvent.type(screen.getByLabelText("摘要"), "复核肾功能并调整方案");
    await userEvent.type(screen.getByLabelText("详细说明"), "命中后提示医生复核，不自动开立医嘱。");
    await userEvent.type(screen.getByLabelText("来源标签"), "CKD 指南");
    await userEvent.type(screen.getByLabelText("建议名称"), "记录已人工复核");
    await userEvent.click(screen.getByRole("button", { name: "保存草稿" }));

    await waitFor(() => {
      expect(apiMocks.create).toHaveBeenCalledWith({
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION.CKD.REVIEW",
        applicableScope: "ALL",
        sourceRef: "CKD 用药安全指南",
        content: {
          schemaVersion: "1.0",
          title: "肾功能异常处置",
          actionCode: "REMIND",
          atSeverity: "LOW",
          indicator: "info",
          summary: "复核肾功能并调整方案",
          detail: "命中后提示医生复核，不自动开立医嘱。",
          source: { label: "CKD 指南" },
          suggestions: [{ label: "记录已人工复核", actionType: "ACKNOWLEDGE" }],
          overrideReasons: [],
          requiresPhysicianConfirmation: false,
        },
      });
    });
    expect(apiMocks.create.mock.calls[0]?.[0].content).not.toHaveProperty("actions");
  });
});
