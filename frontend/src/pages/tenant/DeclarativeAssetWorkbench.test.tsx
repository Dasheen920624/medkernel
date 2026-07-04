import { render, screen, waitFor, within } from "@testing-library/react";
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

function renderWorkbench(evidenceDetailsEnabled = false) {
  render(
    <ConfigProvider>
      <AntdApp>
        <DeclarativeAssetWorkbench canWrite evidenceDetailsEnabled={evidenceDetailsEnabled} />
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

    expect(screen.getByText("配置资产按类型编目")).toBeInTheDocument();
    expect(
      screen.getByText(
        "每类资产按结构校验，版本号自动递增；发布时会选择值集、公式、医嘱套餐和临床提示卡的精确版本。已发布内容不可原地修改。字段目录与完整路径分别由各自工作台管理。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("医疗配置资产独立维护")).not.toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "值集" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "公式与量表" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "医嘱套餐" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "临床提示卡" })).toBeInTheDocument();
    expect(screen.getByText("值集资产已登记")).toBeInTheDocument();
    expect(screen.queryByText("VS.NEPHROTOXIC")).not.toBeInTheDocument();
    expect(screen.getByText("V1")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "新建值集" }));
    expect(screen.queryByLabelText("草稿" + "配置" + "包")).not.toBeInTheDocument();
    expect(screen.getByLabelText("适用范围")).toHaveValue("ALL");
  });

  it("shows raw asset identities only when evidence details are enabled", () => {
    renderWorkbench(true);

    expect(screen.getByText("VS.NEPHROTOXIC")).toBeInTheDocument();
  });

  it("keeps repeated frontdesk-created drafts distinguishable by latest maintenance time", () => {
    apiMocks.useDeclarativeAssets.mockReturnValue({
      data: {
        items: Array.from({ length: 12 }, (_, index) => ({
          versionId: `av-vs-${index + 1}`,
          assetType: "VALUE_SET",
          assetIdentity: `VS.REHEARSAL.${index + 1}`,
          versionNo: "V1",
          status: "DRAFT",
          organizationScope: "tenant:tenant-A",
          applicableScope: "ALL",
          sourceRef: "真实前台演练：院内药品目录脱敏样例",
          updatedAt: new Date(Date.UTC(2026, 5, 30 - index, 15, 18, 0)).toISOString(),
        })),
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderWorkbench();

    const bodyRows = screen.getAllByRole("row").slice(1);
    expect(within(bodyRows[0]).getByText("最新维护 2026年06月30日 23:18")).toBeInTheDocument();
    expect(screen.getByText("共 12 条配置资产，当前显示 1-10 条")).toBeInTheDocument();
  });

  it("creates a typed value set instead of accepting an unstructured metadata shell", async () => {
    apiMocks.create.mockResolvedValue({ versionId: "av-vs-2" });
    renderWorkbench();

    await userEvent.click(screen.getByRole("button", { name: "新建值集" }));
    await userEvent.type(screen.getByLabelText("稳定资产身份"), "VS.RENAL.RISK");
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

    await userEvent.click(screen.getByRole("tab", { name: "临床提示卡" }));
    await userEvent.click(screen.getByRole("button", { name: "新建临床提示卡" }));
    await userEvent.type(screen.getByLabelText("稳定资产身份"), "ACTION.CKD.REVIEW");
    await userEvent.type(screen.getByLabelText("来源依据"), "CKD 用药安全指南");
    await userEvent.type(screen.getByLabelText("标题"), "肾功能异常处置");
    expect(screen.getByLabelText("命中后处理")).toBeInTheDocument();
    expect(screen.getByLabelText("风险等级")).toBeInTheDocument();
    expect(screen.getByLabelText("提醒等级")).toBeInTheDocument();
    expect(screen.queryByLabelText("动作码")).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("摘要"), "复核肾功能并调整方案");
    await userEvent.type(screen.getByLabelText("详细说明"), "命中后提示医生复核，不自动开立医嘱。");
    await userEvent.type(screen.getByLabelText("依据名称"), "CKD 指南");
    await userEvent.type(screen.getByLabelText("可选操作名称"), "记录已人工复核");
    await userEvent.click(screen.getByRole("button", { name: /添加改用方案原因/ }));
    await userEvent.type(screen.getByLabelText("允许改用其他方案的原因"), "医生已确认更优处置方案");
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
          overrideReasons: ["医生已确认更优处置方案"],
          requiresPhysicianConfirmation: false,
        },
      });
    });
    expect(apiMocks.create.mock.calls[0]?.[0].content).not.toHaveProperty("actions");
  });

  it("uses a business target field for suggested orders without exposing JSON parameters by default", async () => {
    apiMocks.create.mockResolvedValue({ versionId: "av-action-order" });
    const user = userEvent.setup();
    renderWorkbench();

    await user.click(screen.getByRole("tab", { name: "临床提示卡" }));
    await user.click(screen.getByRole("button", { name: "新建临床提示卡" }));
    await user.type(screen.getByLabelText("稳定资产身份"), "ACTION.CKD.ORDER");
    await user.type(screen.getByLabelText("来源依据"), "CKD 用药安全指南");
    await user.type(screen.getByLabelText("标题"), "肾功能异常医嘱建议");
    await user.type(screen.getByLabelText("摘要"), "建议医师打开肾功能复核套餐");
    await user.type(screen.getByLabelText("详细说明"), "只生成建议卡片，医嘱必须由医师逐条确认。");
    await user.type(screen.getByLabelText("依据名称"), "CKD 指南");
    await user.type(screen.getByLabelText("可选操作名称"), "打开肾功能复核套餐");
    await user.click(screen.getByRole("combobox", { name: "可选操作类型" }));
    await user.click(screen.getByText("建议医嘱"));
    await user.type(screen.getByLabelText("关联业务对象"), "ORDER.CKD.REVIEW");

    expect(screen.queryByLabelText("操作参数")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "保存草稿" }));

    await waitFor(() => {
      expect(apiMocks.create).toHaveBeenCalledWith({
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION.CKD.ORDER",
        applicableScope: "ALL",
        sourceRef: "CKD 用药安全指南",
        content: {
          schemaVersion: "1.0",
          title: "肾功能异常医嘱建议",
          actionCode: "REMIND",
          atSeverity: "LOW",
          indicator: "info",
          summary: "建议医师打开肾功能复核套餐",
          detail: "只生成建议卡片，医嘱必须由医师逐条确认。",
          source: { label: "CKD 指南" },
          suggestions: [
            {
              label: "打开肾功能复核套餐",
              actionType: "SUGGEST_ORDER",
              payload: { orderSetRef: "ORDER.CKD.REVIEW" },
            },
          ],
          overrideReasons: [],
          requiresPhysicianConfirmation: true,
        },
      });
    });
  });
});
