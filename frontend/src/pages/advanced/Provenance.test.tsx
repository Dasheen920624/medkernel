import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import Provenance from "./Provenance";

const mockUseKnowledgeIdentities = vi.fn();
const mockUseKnowledgeProvenance = vi.fn();
const mockUseKnowledgeReviewQueue = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useKnowledgeIdentities: (params: unknown) => mockUseKnowledgeIdentities(params),
  useKnowledgeProvenance: (identityId?: number, params?: unknown) =>
    mockUseKnowledgeProvenance(identityId, params),
  useKnowledgeReviewQueue: () => mockUseKnowledgeReviewQueue(),
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter
        initialEntries={["/advanced/provenance?identityId=1"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <ConfigProvider>
          <Provenance />
        </ConfigProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("Provenance", () => {
  it("keeps identity loading failures in service institution language", () => {
    mockUseKnowledgeReviewQueue.mockReturnValue({
      data: {
        items: [],
        page: 1,
        size: 20,
        total: 0,
        hasNext: false,
        totalEstimated: false,
      },
      isError: false,
      refetch: vi.fn(),
    });
    mockUseKnowledgeIdentities.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: null,
      refetch: vi.fn(),
    });
    mockUseKnowledgeProvenance.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByText("知识身份读取失败")).toBeInTheDocument();
    expect(screen.getByText("请检查登录权限、服务机构范围或知识服务状态。")).toBeInTheDocument();
  });

  it("renders an exact knowledge source chain instead of the audit snapshot console", () => {
    mockUseKnowledgeReviewQueue.mockReturnValue({
      data: {
        items: [
          {
            identity: { id: 1, subject: "瑞舒伐他汀说明书" },
            version: { id: 22, nextReviewAt: "2026-06-01T00:00:00Z" },
            status: "OVERDUE",
            daysUntilDue: -8,
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      },
      isError: false,
      refetch: vi.fn(),
    });
    mockUseKnowledgeIdentities.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            tenantId: "t-1",
            identityCode: "plat:drug:rosuvastatin-guide",
            domain: "DRUG",
            subject: "瑞舒伐他汀说明书",
            status: "ACTIVE",
            currentVersionId: 22,
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });
    mockUseKnowledgeProvenance.mockReturnValue({
      data: {
        identity: {
          id: 1,
          tenantId: "t-1",
          identityCode: "plat:drug:rosuvastatin-guide",
          domain: "DRUG",
          subject: "瑞舒伐他汀说明书",
          status: "ACTIVE",
          currentVersionId: 22,
        },
        currentVersionId: 22,
        versions: {
          items: [
            {
              id: 22,
              tenantId: "t-1",
              identityId: 1,
              versionNo: "v2026.1",
              versionLabel: "2026 版",
              status: "ACTIVE",
              authorityLevel: "A_REGULATION",
              gradeQuality: "HIGH",
              reviewCycleMonths: 12,
              reviewedAt: "2025-06-01T00:00:00Z",
              nextReviewAt: "2026-06-01T00:00:00Z",
            },
            {
              id: 20,
              tenantId: "t-1",
              identityId: 1,
              versionNo: "v2024.1",
              versionLabel: "2024 版",
              status: "SUPERSEDED",
            },
          ],
          page: 1,
          size: 20,
          total: 22,
          hasNext: true,
          totalEstimated: false,
        },
        supersessions: {
          items: [
            {
              id: 30,
              transitionType: "DEPRECATE",
              successorIdentityId: 2,
              gracePeriodEnd: "2026-07-01T00:00:00Z",
              migrationGuidance: "请迁移到新版用药指南",
            },
          ],
          page: 1,
          size: 20,
          total: 1,
          hasNext: false,
          totalEstimated: false,
        },
        sourceEvidence: [
          {
            assetVersionId: 22,
            citationId: 1,
            sourceFragmentId: 100,
            sourceDocumentId: 7,
            sourceVersionId: 8,
            sourceCode: "SRC.NHC.2026",
            sourceTitle: "国家药品说明书",
            sourceType: "POLICY",
            authorityLevel: "A_REGULATION",
            authorityLabel: "A 法规",
            authorityBasis: "国家卫健委发布文件编号 NHC-2026-01",
            sourceVersionNo: "2026.1",
            sourceVersionHash: "source-version-hash",
            anchorPath: "section-3.2.1",
            anchorLabel: "适应证",
            textExcerpt: "用于符合适应证的患者。",
            fragmentHash: "fragment-hash",
            startOffset: 0,
            endOffset: 12,
            publishedAt: "2026-01-01T00:00:00Z",
            relation: "SUPPORTS",
            weight: 90,
            displayRole: "PRIMARY",
            recommendedByDefault: true,
            supplementary: false,
            displayLabel: "A 法规 · 主证据",
            rankingReason: "按可信分级、来源发布时间和适用域精确度排序",
          },
        ],
        unresolvedCitationCount: 1,
        partial: true,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByRole("heading", { name: "知识来源追溯" })).toBeInTheDocument();
    expect(screen.getAllByText("瑞舒伐他汀说明书").length).toBeGreaterThan(0);
    expect(screen.getByText("国家药品说明书")).toBeInTheDocument();
    expect(screen.getByText("section-3.2.1")).toBeInTheDocument();
    expect(screen.getByText("2024 版")).toBeInTheDocument();
    expect(screen.getByText(/1 条引用未能解析/)).toBeInTheDocument();
    expect(screen.getAllByText("药品说明书").length).toBeGreaterThan(0);
    expect(screen.getByText("有效身份")).toBeInTheDocument();
    expect(screen.getByText(/1 项知识需要复审/)).toBeInTheDocument();
    expect(screen.getByText("证据质量")).toBeInTheDocument();
    expect(screen.getByText(/请迁移到新版用药指南/)).toBeInTheDocument();
    expect(screen.queryByText("DRUG")).not.toBeInTheDocument();
    expect(screen.queryByText("ACTIVE")).not.toBeInTheDocument();
    expect(screen.queryByText("真实证据快照")).not.toBeInTheDocument();
    expect(mockUseKnowledgeProvenance).toHaveBeenCalledWith(1, { page: 1, size: 20 });
  });
});
