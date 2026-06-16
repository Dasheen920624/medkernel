import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AuthoringBatchDrawer from "./AuthoringBatchDrawer";

const apiMocks = vi.hoisted(() => ({
  generate: vi.fn(),
  analyze: vi.fn(),
  publish: vi.fn(),
  importPackages: vi.fn(),
  exportPackages: vi.fn(),
  distribute: vi.fn(),
  packagesData: {
    items: [
      {
        packageId: "package-rule",
        packageCode: "PKG.RULE",
        packageVersion: "pkg-2026.06",
        name: "临床规则包",
        status: "DRAFT",
      },
    ],
    total: 1,
  },
  packageListParams: [] as unknown[],
  batchJobParams: [] as unknown[],
}));

vi.mock("@/shared/api/hooks", () => ({
  useAuthoringBatchJobs: (params?: unknown) => {
    apiMocks.batchJobParams.push(params ?? {});
    return {
      data: { items: [], page: 1, size: 20, total: 0, totalEstimated: false, hasNext: false },
      isLoading: false,
      refetch: vi.fn(),
    };
  },
  usePackages: (params?: unknown) => {
    apiMocks.packageListParams.push(params ?? {});
    return { data: apiMocks.packagesData, isLoading: false, isError: false };
  },
  useGenerateAuthoringBatchRules: () => ({
    mutateAsync: apiMocks.generate,
    isPending: false,
  }),
  useAnalyzeAuthoringBatchRuleImpacts: () => ({
    mutateAsync: apiMocks.analyze,
    isPending: false,
  }),
  usePublishAuthoringBatchRules: () => ({
    mutateAsync: apiMocks.publish,
    isPending: false,
  }),
  useImportAuthoringBatchPackages: () => ({
    mutateAsync: apiMocks.importPackages,
    isPending: false,
  }),
  useExportAuthoringBatchPackages: () => ({
    mutateAsync: apiMocks.exportPackages,
    isPending: false,
  }),
  useDistributeAuthoringBatchPackages: () => ({
    mutateAsync: apiMocks.distribute,
    isPending: false,
  }),
}));

function renderDrawer() {
  render(
    <ConfigProvider>
      <AntdApp>
        <AuthoringBatchDrawer open canWrite onClose={vi.fn()} />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("AuthoringBatchDrawer", () => {
  beforeEach(() => {
    Object.values(apiMocks).forEach((mockFn) => {
      if (typeof mockFn === "function" && "mockReset" in mockFn) {
        mockFn.mockReset();
      }
    });
    apiMocks.packageListParams = [];
    apiMocks.batchJobParams = [];
  });

  it("loads batch job records through server pagination", () => {
    renderDrawer();

    expect(apiMocks.batchJobParams).toContainEqual({ page: 1, size: 20, enabled: true });
  });

  it("loads rule package selector through small server-side pages", () => {
    renderDrawer();

    expect(apiMocks.packageListParams).toContainEqual({
      page: 1,
      size: 20,
      assetType: "RULE",
    });
    expect(apiMocks.packageListParams).not.toContainEqual(
      expect.objectContaining({ assetType: "RULE", size: 100 }),
    );
  });

  it("generates one rule draft per pasted parameter row", async () => {
    apiMocks.generate.mockResolvedValue({
      jobId: "abj-generate",
      jobType: "RULE_GENERATE",
      status: "SUCCEEDED",
      totalCount: 2,
      successCount: 2,
      failureCount: 0,
      retryableCount: 0,
      items: [],
    });

    renderDrawer();

    await userEvent.click(screen.getByRole("combobox", { name: "规则包版本" }));
    await userEvent.click(await screen.findByText("临床规则包（pkg-2026.06）"));
    fireEvent.change(screen.getByLabelText("模板规则 ID"), {
      target: { value: "rule-template" },
    });
    fireEvent.change(screen.getByLabelText("参数表"), {
      target: {
        value:
          "ruleCode,name,threshold,enabled\nRULE.CKD.1,CKD 阈值 1,45,true\nRULE.CKD.2,CKD 阈值 2,30,false",
      },
    });
    await userEvent.click(screen.getByRole("button", { name: "生成草稿" }));

    await waitFor(() => {
      expect(apiMocks.generate).toHaveBeenCalledWith({
        templateRuleId: "rule-template",
        rows: [
          {
            rowId: "row-1",
            ruleCode: "RULE.CKD.1",
            name: "CKD 阈值 1",
            packageVersion: "pkg-2026.06",
            parameterBindings: { threshold: 45, enabled: true },
          },
          {
            rowId: "row-2",
            ruleCode: "RULE.CKD.2",
            name: "CKD 阈值 2",
            packageVersion: "pkg-2026.06",
            parameterBindings: { threshold: 30, enabled: false },
          },
        ],
      });
    });
    expect(screen.getByText("批量任务 abj-generate 执行结束")).toBeInTheDocument();
    expect(screen.getByText("成功")).toBeInTheDocument();
  });

  it("rejects mixed package versions in batch rule generation", async () => {
    renderDrawer();

    await userEvent.click(screen.getByRole("combobox", { name: "规则包版本" }));
    await userEvent.click(await screen.findByText("临床规则包（pkg-2026.06）"));
    fireEvent.change(screen.getByLabelText("模板规则 ID"), {
      target: { value: "rule-template" },
    });
    fireEvent.change(screen.getByLabelText("参数表"), {
      target: {
        value: "ruleCode,name,packageVersion\nRULE.CKD.1,CKD 阈值 1,pkg-other",
      },
    });
    await userEvent.click(screen.getByRole("button", { name: "生成草稿" }));

    await waitFor(() => {
      expect(apiMocks.generate).not.toHaveBeenCalled();
      expect(screen.getByText("第 2 行 packageVersion 与统一规则包版本不一致")).toBeInTheDocument();
    });
  });

  it("requires explicit confirmation for every high-risk rule before publish", async () => {
    apiMocks.analyze.mockResolvedValue({
      totalCount: 1,
      highRiskCount: 1,
      criticalRiskCount: 0,
      traceId: "trace-impact",
      items: [
        {
          ruleId: "rule-high",
          versionId: "version-1",
          riskLevel: "HIGH",
          analysisStatus: "COMPLETE",
          impactDigest: "impact-high",
          affectedCount: 3,
          unavailableScopes: [],
        },
      ],
    });
    apiMocks.publish.mockResolvedValue({
      jobId: "abj-publish",
      jobType: "RULE_PUBLISH",
      status: "SUCCEEDED",
      totalCount: 1,
      successCount: 1,
      failureCount: 0,
      retryableCount: 0,
      items: [],
    });

    renderDrawer();
    await userEvent.click(screen.getByRole("tab", { name: "规则发布" }));
    fireEvent.change(screen.getByLabelText("规则 ID"), {
      target: { value: "rule-high" },
    });
    await userEvent.click(screen.getByRole("button", { name: "分析影响" }));

    expect(await screen.findByText("高危")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "批量推进" })).toBeDisabled();

    await userEvent.click(screen.getByRole("checkbox", { name: "确认 rule-high" }));
    fireEvent.change(screen.getByLabelText("推进说明"), {
      target: { value: "委员会已逐条确认" },
    });
    await userEvent.click(screen.getByRole("button", { name: "批量推进" }));

    await waitFor(() => {
      expect(apiMocks.publish).toHaveBeenCalledWith({
        targetState: "PEER_REVIEW",
        reason: "委员会已逐条确认",
        items: [
          {
            itemId: "rule-high",
            ruleId: "rule-high",
            impactDigest: "impact-high",
            highRiskConfirmed: true,
          },
        ],
      });
    });
  });

  it("builds readable multi-target package distribution rows", async () => {
    apiMocks.distribute.mockResolvedValue({
      jobId: "abj-distribute",
      jobType: "PACKAGE_DISTRIBUTE",
      status: "NOT_CONNECTED",
      totalCount: 1,
      successCount: 0,
      failureCount: 0,
      retryableCount: 1,
      items: [],
    });

    renderDrawer();
    await userEvent.click(screen.getByRole("tab", { name: "包分发" }));
    fireEvent.change(screen.getByLabelText("分发目标表"), {
      target: {
        value: "packageId,targetOrgUnitId,adapterIds\npackage-1,hospital-1,fhir;webhook",
      },
    });
    fireEvent.change(screen.getByLabelText("分发说明"), {
      target: { value: "区域批量分发" },
    });
    await userEvent.click(screen.getByRole("button", { name: "开始分发" }));

    await waitFor(() => {
      expect(apiMocks.distribute).toHaveBeenCalledWith({
        items: [
          {
            itemId: "row-1",
            packageId: "package-1",
            targetOrgUnitId: "hospital-1",
            strategy: "GRAYSCALE",
            scopeType: "FACILITY",
            scopeValue: "hospital-1",
            adapterIds: ["fhir", "webhook"],
            reason: "区域批量分发",
          },
        ],
      });
    });
  });
});
