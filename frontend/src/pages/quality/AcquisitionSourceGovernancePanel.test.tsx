import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AcquisitionSourceGovernancePanel from "./AcquisitionSourceGovernancePanel";

const mockUseKnowledgeAcquisitionSources = vi.fn();
const mockUseSaveKnowledgeAcquisitionSourceDraft = vi.fn();
const mockUseEnableKnowledgeAcquisitionSource = vi.fn();
const mockUseDisableKnowledgeAcquisitionSource = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useKnowledgeAcquisitionSources: (params: unknown) => mockUseKnowledgeAcquisitionSources(params),
  useSaveKnowledgeAcquisitionSourceDraft: () => mockUseSaveKnowledgeAcquisitionSourceDraft(),
  useEnableKnowledgeAcquisitionSource: () => mockUseEnableKnowledgeAcquisitionSource(),
  useDisableKnowledgeAcquisitionSource: () => mockUseDisableKnowledgeAcquisitionSource(),
}));

const source = {
  id: 17,
  tenantId: "tenant-A",
  sourceCode: "NHC-GUIDELINE",
  domain: "www.nhc.gov.cn",
  baseUrl: "https://www.nhc.gov.cn/wjw/index.shtml",
  sourceType: "GUIDELINE",
  authorityLevel: "B_GUIDELINE",
  authorityBasis: "国家卫生健康委官网",
  title: "国家卫生健康委指南来源",
  publisher: "国家卫生健康委",
  license: "公开发布页面，逐条核验使用范围",
  licensePolicy: "PERMITTED",
  robotsPolicy: "ALLOW_FETCH",
  enabledFlag: "N",
  scheduleEnabledFlag: "N",
  updatedBy: "operator",
  updatedAt: "2026-06-22T01:00:00Z",
  version: 0,
};

function renderPanel(canWrite = true, evidenceDetailsEnabled = false) {
  return render(
    <ConfigProvider>
      <AntdApp>
        <AcquisitionSourceGovernancePanel
          canWrite={canWrite}
          evidenceDetailsEnabled={evidenceDetailsEnabled}
        />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("AcquisitionSourceGovernancePanel", () => {
  const saveDraft = vi.fn();
  const enable = vi.fn();
  const disable = vi.fn();

  beforeEach(() => {
    saveDraft.mockReset();
    enable.mockReset();
    disable.mockReset();
    enable.mockResolvedValue({ ...source, enabledFlag: "Y" });
    disable.mockResolvedValue(source);
    mockUseKnowledgeAcquisitionSources.mockReturnValue({
      data: {
        items: [source],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isLoading: false,
      isError: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseSaveKnowledgeAcquisitionSourceDraft.mockReturnValue({
      mutateAsync: saveDraft,
      isPending: false,
    });
    mockUseEnableKnowledgeAcquisitionSource.mockReturnValue({
      mutateAsync: enable,
      isPending: false,
    });
    mockUseDisableKnowledgeAcquisitionSource.mockReturnValue({
      mutateAsync: disable,
      isPending: false,
    });
  });

  it("uses business source wording by default and performs an explicit enable confirmation", async () => {
    renderPanel();

    expect(screen.getByText("国家卫生健康委指南来源")).toBeInTheDocument();
    expect(screen.getByText("国家卫生健康委")).toBeInTheDocument();
    expect(screen.getByText("来源身份已登记")).toBeInTheDocument();
    expect(screen.getByText("入口地址已登记")).toBeInTheDocument();
    expect(screen.getByText("维护人已记录")).toBeInTheDocument();
    expect(screen.getByText("已停用")).toBeInTheDocument();
    expect(screen.queryByText("NHC-GUIDELINE")).not.toBeInTheDocument();
    expect(screen.queryByText("https://www.nhc.gov.cn/wjw/index.shtml")).not.toBeInTheDocument();
    expect(screen.queryByText("operator")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /NHC-GUIDELINE/ })).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "启用来源 国家卫生健康委指南来源" }),
    );
    expect(screen.getByText("确认启用来源？")).toBeInTheDocument();
    expect(screen.getByText(/将启用「国家卫生健康委指南来源」/)).toBeInTheDocument();
    expect(screen.queryByText(/NHC-GUIDELINE/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "确认启用" }));

    await waitFor(() => expect(enable).toHaveBeenCalledWith("NHC-GUIDELINE"));
  });

  it("shows source identifiers and maintenance operator only in evidence details", () => {
    renderPanel(true, true);

    expect(screen.getByText("NHC-GUIDELINE")).toBeInTheDocument();
    expect(screen.getByText("https://www.nhc.gov.cn/wjw/index.shtml")).toBeInTheDocument();
    expect(screen.getByText("operator")).toBeInTheDocument();
  });

  it("opens a disabled draft form while keeping legal and robots decisions explicit", async () => {
    renderPanel();

    await userEvent.click(screen.getByRole("button", { name: "登记来源草稿" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("登记公域来源草稿")).toBeInTheDocument();
    expect(screen.getByLabelText("稳定来源身份")).toBeRequired();
    expect(screen.getByLabelText("许可裁决")).toHaveValue("");
    expect(screen.getByLabelText("robots 策略")).toHaveValue("");
  });

  it("does not expose mutation actions to read-only users", () => {
    renderPanel(false);

    expect(screen.queryByRole("button", { name: "登记来源草稿" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /启用来源/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /编辑草稿/ })).not.toBeInTheDocument();
  });

  it("renders a retryable error state", () => {
    mockUseKnowledgeAcquisitionSources.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("网络不可用"),
      refetch: vi.fn(),
    });

    renderPanel();

    expect(screen.getByText("来源允许清单读取失败")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
  });
});
