import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AuthoringBatchDrawer from "./AuthoringBatchDrawer";

const apiMocks = vi.hoisted(() => ({
  generate: vi.fn(),
  analyze: vi.fn(),
  publish: vi.fn(),
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
    apiMocks.batchJobParams = [];
  });

  it("loads batch job records through server pagination", () => {
    renderDrawer();

    expect(apiMocks.batchJobParams).toContainEqual({ page: 1, size: 20, enabled: true });
  });

  it("keeps package exchange and distribution outside the authoring batch drawer", () => {
    renderDrawer();

    expect(screen.queryByRole("tab", { name: "包交换" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "包分发" })).not.toBeInTheDocument();
    expect(screen.getByText("批量生成独立规则草稿")).toBeInTheDocument();
  });

  it("generates one rule draft per pasted parameter row", async () => {
    apiMocks.generate.mockResolvedValue({
      jobId: "abj-generate",
      jobType: "RULE_GENERATE",
      status: "SUCCEEDED",
      totalCount: 2,
      successCount: 2,
      failureCount: 0,
      items: [],
    });

    renderDrawer();

    expect(screen.getByLabelText("模板规则资产")).toBeInTheDocument();
    expect(screen.queryByLabelText("模板规则 ID")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("模板规则资产"), {
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
            parameterBindings: { threshold: 45, enabled: true },
          },
          {
            rowId: "row-2",
            ruleCode: "RULE.CKD.2",
            name: "CKD 阈值 2",
            parameterBindings: { threshold: 30, enabled: false },
          },
        ],
      });
    });
    expect(screen.getByText("批量任务 abj-generate 执行结束")).toBeInTheDocument();
    expect(screen.getByText("成功")).toBeInTheDocument();
  });

  it("keeps batch API failures in hospital language", async () => {
    apiMocks.generate.mockRejectedValue(
      new Error("POST /api/v1/authoring-batch failed: ECONNREFUSED 127.0.0.1:8080"),
    );

    renderDrawer();

    fireEvent.change(screen.getByLabelText("模板规则资产"), {
      target: { value: "rule-template" },
    });
    fireEvent.change(screen.getByLabelText("参数表"), {
      target: { value: "ruleCode,name\nRULE.CKD.1,CKD 阈值 1" },
    });
    await userEvent.click(screen.getByRole("button", { name: "生成草稿" }));

    await waitFor(() => {
      expect(screen.getByText("规则批量生成失败")).toBeInTheDocument();
      expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();
      expect(screen.queryByText(/\/api\/v1\/authoring-batch/)).not.toBeInTheDocument();
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
      items: [],
    });

    renderDrawer();
    await userEvent.click(screen.getByRole("tab", { name: "规则发布" }));
    expect(screen.getByLabelText("待发布规则资产")).toBeInTheDocument();
    expect(screen.queryByLabelText("规则 ID")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("待发布规则资产"), {
      target: { value: "rule-high" },
    });
    await userEvent.click(screen.getByRole("button", { name: "分析影响" }));

    expect(await screen.findByText("高危")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "批量推进" })).toBeDisabled();

    await userEvent.click(screen.getByRole("checkbox", { name: "确认 rule-high" }));
    fireEvent.change(screen.getByLabelText("推进说明"), {
      target: { value: "负责人已逐条确认" },
    });
    await userEvent.click(screen.getByRole("button", { name: "批量推进" }));

    await waitFor(() => {
      expect(apiMocks.publish).toHaveBeenCalledWith({
        targetState: "REVIEWED",
        reason: "负责人已逐条确认",
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
});
