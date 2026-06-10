import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import KnowledgeGovernance from "./KnowledgeGovernance";

const KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS = 15_000;

const mockUseKnowledgeIdentities = vi.fn();
const mockUseKnowledgeCandidates = vi.fn();
const mockUseKnowledgeCandidateDiff = vi.fn();
const mockUseReviewKnowledgeCandidate = vi.fn();
const mockUseDeprecateKnowledgeIdentity = vi.fn();
const mockUseSecurityProfile = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useKnowledgeIdentities: (params: unknown) => mockUseKnowledgeIdentities(params),
  useKnowledgeCandidates: (identityId?: number) => mockUseKnowledgeCandidates(identityId),
  useKnowledgeCandidateDiff: (candidateId?: number) => mockUseKnowledgeCandidateDiff(candidateId),
  useReviewKnowledgeCandidate: () => mockUseReviewKnowledgeCandidate(),
  useDeprecateKnowledgeIdentity: () => mockUseDeprecateKnowledgeIdentity(),
  useSecurityProfile: () => mockUseSecurityProfile(),
}));

vi.mock("./DiagnosisKnowledgePanel", () => ({
  default: () => <div>诊断知识工作台</div>,
}));

const realIdentity = {
  id: 42,
  tenantId: "tenant-A",
  identityCode: "KNOW.VTE.GUIDE",
  domain: "GUIDELINE",
  subject: "VTE 防治指南",
  specialtyId: "cardiology",
  description: "围手术期 VTE 风险评估与预防建议",
  status: "ACTIVE",
  currentVersionId: 1001,
  createdAt: "2026-06-06T01:00:00Z",
  createdBy: "u-knowledge",
  updatedAt: "2026-06-06T02:00:00Z",
  updatedBy: "u-knowledge",
};

const activeVersion = {
  id: 1001,
  tenantId: "tenant-A",
  identityId: 42,
  versionNo: "2026.05",
  versionLabel: "现行 VTE 指南",
  sourceDocumentId: 3001,
  sourceVersionId: 4001,
  contentHash: "active-real-hash",
  anchors: "source-fragment-active",
  status: "ACTIVE",
  riskLevel: "HIGH",
  authorityLevel: "B_GUIDELINE",
  gradeQuality: "MODERATE",
  gradeStrength: "STRONG",
  conflictArbitration: "保留现行高危条款",
  organizationScope: "hospital:hospital-A",
  applicableScope: "cardiology",
  activeScopeKey: "42|hospital:hospital-A|cardiology",
  effectiveFrom: "2026-05-01T00:00:00Z",
  reviewedBy: "expert-1",
  reviewedAt: "2026-05-01T01:00:00Z",
  activatedAt: "2026-05-01T02:00:00Z",
  createdAt: "2026-05-01T00:00:00Z",
  createdBy: "u-knowledge",
  updatedAt: "2026-05-01T02:00:00Z",
  updatedBy: "expert-1",
};

const candidateVersion = {
  id: 2002,
  tenantId: "tenant-A",
  identityId: 42,
  versionNo: "2026.06",
  versionLabel: "待审 VTE 指南 2026.06",
  sourceDocumentId: 3002,
  sourceVersionId: 4002,
  contentHash: "candidate-real-hash",
  anchors: "source-fragment-candidate",
  status: "PENDING_REPLACEMENT_REVIEW",
  riskLevel: "HIGH",
  authorityLevel: "A_REGULATION",
  gradeQuality: "HIGH",
  gradeStrength: "STRONG",
  conflictArbitration: "新增高危围手术期禁忌",
  organizationScope: "hospital:hospital-A",
  applicableScope: "cardiology",
  activeScopeKey: "version:2002",
  effectiveFrom: "2026-06-01T00:00:00Z",
  reviewedBy: null,
  reviewedAt: null,
  activatedAt: null,
  createdAt: "2026-06-06T01:00:00Z",
  createdBy: "ai-candidate-importer",
  updatedAt: "2026-06-06T01:10:00Z",
  updatedBy: "workflow-know-02",
};

const candidateClassification = {
  id: 9001,
  tenantId: "tenant-A",
  orgPath: "tenant-A/group-A/hospital-A",
  identityId: 42,
  candidateVersionId: 2002,
  activeVersionId: 1001,
  classification: "CONFLICT",
  reviewStatus: "PENDING_REPLACEMENT_REVIEW",
  contentHash: "candidate-real-hash",
  basis: "同一 identity 下来源版本更新，GRADE 强度与现行版冲突",
  diffSummary: "新增围手术期高危禁忌条款，需专家确认后替换现行版。",
  createdAt: "2026-06-06T01:12:00Z",
  createdBy: "workflow-know-02",
  updatedAt: "2026-06-06T01:12:00Z",
  updatedBy: "workflow-know-02",
};

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <ConfigProvider>
        <AntdApp>
          <KnowledgeGovernance />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>,
  );
}

let refetchIdentities: ReturnType<typeof vi.fn>;
let refetchCandidates: ReturnType<typeof vi.fn>;
let reviewCandidate: ReturnType<typeof vi.fn>;
let deprecateIdentity: ReturnType<typeof vi.fn>;

beforeEach(() => {
  refetchIdentities = vi.fn();
  refetchCandidates = vi.fn();
  reviewCandidate = vi.fn().mockResolvedValue({
    identityId: 42,
    candidates: [{ ...candidateVersion, status: "ACTIVE" }],
    classifications: [{ ...candidateClassification, reviewStatus: "APPROVED" }],
    available: true,
    reasonCode: "CONFLICT",
    message: "候选已通过审核并交由 SYS-08 原子替换",
  });
  deprecateIdentity = vi.fn().mockResolvedValue({
    identityId: 42,
    successorIdentityId: 43,
    transitionType: "DEPRECATE",
  });

  mockUseKnowledgeIdentities.mockReset();
  mockUseKnowledgeCandidates.mockReset();
  mockUseKnowledgeCandidateDiff.mockReset();
  mockUseReviewKnowledgeCandidate.mockReset();
  mockUseDeprecateKnowledgeIdentity.mockReset();
  mockUseSecurityProfile.mockReset();

  mockUseKnowledgeIdentities.mockReturnValue({
    data: { items: [realIdentity], page: 1, size: 20, total: 1, hasNext: false },
    refetch: refetchIdentities,
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseKnowledgeCandidates.mockReturnValue({
    data: {
      identityId: 42,
      candidates: [candidateVersion],
      classifications: [candidateClassification],
      available: true,
      reasonCode: "CONFLICT",
      message: "存在冲突候选，需人工审核。",
    },
    refetch: refetchCandidates,
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseKnowledgeCandidateDiff.mockReturnValue({
    data: {
      identityId: 42,
      candidates: [candidateVersion, activeVersion],
      classifications: [candidateClassification],
      available: true,
      reasonCode: "CONFLICT",
      message: "候选与现行权威版本存在高危条款冲突。",
    },
    isLoading: false,
    isError: false,
    error: undefined,
  });
  mockUseReviewKnowledgeCandidate.mockReturnValue({
    mutateAsync: reviewCandidate,
    isPending: false,
  });
  mockUseDeprecateKnowledgeIdentity.mockReturnValue({
    mutateAsync: deprecateIdentity,
    isPending: false,
  });
  mockUseSecurityProfile.mockReturnValue({
    data: {
      dataScope: { tenantId: "t-1" },
      permissions: [{ code: "knowledge.publish" }],
    },
  });
});

describe("KnowledgeGovernance", () => {
  it("keeps diagnosis governance available while the candidate queue is loading", async () => {
    const user = userEvent.setup();
    mockUseKnowledgeIdentities.mockReturnValue({
      data: undefined,
      refetch: refetchIdentities,
      isLoading: true,
      isError: false,
      error: undefined,
    });

    renderPage();
    expect(screen.getByText("正在加载知识候选审核")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "诊断知识" }));
    expect(screen.getByText("诊断知识工作台")).toBeInTheDocument();
  });

  it("keeps the retirement successor query disabled until the dialog opens", () => {
    renderPage();

    expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
      expect.objectContaining({
        domain: undefined,
        status: "ACTIVE",
        enabled: false,
      }),
    );
  });

  it("loads the requested server page when the identity ledger is paged", async () => {
    const user = userEvent.setup();
    mockUseKnowledgeIdentities.mockReturnValue({
      data: {
        items: [realIdentity],
        page: 1,
        size: 20,
        total: 41,
        hasNext: true,
      },
      refetch: refetchIdentities,
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();
    await user.click(screen.getByTitle("2"));

    await waitFor(() => {
      expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
        expect.objectContaining({
          domain: "GUIDELINE",
          page: 2,
          size: 20,
        }),
      );
    });
  });

  it("keeps the knowledge filters usable when the current domain is empty", async () => {
    const user = userEvent.setup();
    mockUseKnowledgeIdentities.mockReturnValue({
      data: {
        items: [],
        page: 1,
        size: 20,
        total: 0,
        hasNext: false,
      },
      refetch: refetchIdentities,
      isLoading: false,
      isError: false,
      error: undefined,
    });

    renderPage();

    expect(screen.getByText("当前筛选下暂无待审核知识身份")).toBeInTheDocument();
    await user.click(screen.getByRole("combobox", { name: "知识域" }));
    await user.click(screen.getByText("药品说明书"));

    await waitFor(() => {
      expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
        expect.objectContaining({ domain: "DRUG" }),
      );
    });
  });

  it("loads real knowledge identities and candidate classifications instead of the inactive roadmap placeholder", () => {
    renderPage();

    expect(mockUseKnowledgeIdentities).toHaveBeenCalledWith(
      expect.objectContaining({
        domain: "GUIDELINE",
        status: "ACTIVE",
        page: 1,
        size: 20,
        sort: "updatedAt,desc",
      }),
    );
    expect(mockUseKnowledgeCandidates).toHaveBeenLastCalledWith(42);

    expect(screen.getByRole("heading", { name: "知识治理" })).toBeInTheDocument();
    expect(screen.getByText("待审核候选总数")).toBeInTheDocument();
    expect(screen.getAllByText("冲突候选").length).toBeGreaterThan(0);
    expect(screen.getByText("高风险候选")).toBeInTheDocument();
    expect(screen.getByText("KNOW.VTE.GUIDE")).toBeInTheDocument();
    expect(screen.getByText("VTE 防治指南")).toBeInTheDocument();
    expect(screen.getByText("待审 VTE 指南 2026.06")).toBeInTheDocument();
    expect(
      screen.getByText("同一 identity 下来源版本更新，GRADE 强度与现行版冲突"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("新增围手术期高危禁忌条款，需专家确认后替换现行版。"),
    ).toBeInTheDocument();
    expect(screen.queryByText("入口暂未激活")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /生成|AI 生成|创建候选/ })).not.toBeInTheDocument();
  });

  it("opens the real candidate diff drawer with active and candidate version evidence", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看审核对照" }));

    expect(mockUseKnowledgeCandidateDiff).toHaveBeenLastCalledWith(2002);
    expect(screen.getByText("知识候选审核对照")).toBeInTheDocument();
    expect(screen.getByText("现行 VTE 指南")).toBeInTheDocument();
    expect(screen.getAllByText("active-real-hash").length).toBeGreaterThan(0);
    expect(screen.getByText("source-fragment-active")).toBeInTheDocument();
    expect(screen.getAllByText("candidate-real-hash").length).toBeGreaterThan(0);
    expect(screen.getByText("source-fragment-candidate")).toBeInTheDocument();
    expect(screen.getByText("候选与现行权威版本存在高危条款冲突。")).toBeInTheDocument();
  });

  it(
    "reviews a candidate through the KNOW-02 classification review endpoint instead of mutating local state",
    async () => {
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByRole("button", { name: "查看审核对照" }));
      fireEvent.change(screen.getByLabelText("审核上下文包版本"), {
        target: { value: "PKG.KNOW.2026.06" },
      });
      fireEvent.change(screen.getByLabelText("审核理由"), {
        target: { value: "已核对来源锚点和现行版差异，允许替换。" },
      });
      fireEvent.change(screen.getByLabelText("签名 ID"), {
        target: { value: "sig-knowledge-2002" },
      });
      fireEvent.change(screen.getByLabelText("签名时间"), {
        target: { value: "2026-06-09T16:00" },
      });
      fireEvent.change(screen.getByLabelText("签名人 ID"), {
        target: { value: "expert-1" },
      });
      fireEvent.change(screen.getByLabelText("签名人姓名"), {
        target: { value: "审核专家" },
      });
      fireEvent.change(screen.getByLabelText("签名摘要"), {
        target: { value: "a".repeat(64) },
      });
      for (const label of [
        "结构校验",
        "术语绑定",
        "依赖完整性",
        "安全单调性",
        "影响模拟",
        "同行复核",
      ]) {
        fireEvent.click(screen.getByRole("checkbox", { name: label }));
      }
      fireEvent.click(screen.getByRole("button", { name: "通过并发布" }));

      await waitFor(() => {
        expect(reviewCandidate).toHaveBeenCalledWith({
          candidateId: 9001,
          packageVersion: "PKG.KNOW.2026.06",
          request: {
            decision: "APPROVE",
            reason: "已核对来源锚点和现行版差异，允许替换。",
            publishEvidence: {
              electronicSignature: {
                signatureId: "sig-knowledge-2002",
                signerId: "expert-1",
                signerName: "审核专家",
                signedAt: new Date("2026-06-09T16:00").toISOString(),
                signatureHash: "a".repeat(64),
              },
              qualityGate: {
                schemaValid: true,
                terminologyBindingComplete: true,
                dependencyIntegrityVerified: true,
                safetyMonotonicityVerified: true,
                impactSimulationPassed: true,
                peerReviewSigned: true,
                summary: undefined,
              },
            },
          },
          idempotencyKey: expect.stringContaining("knowledge-review-9001"),
        });
      });
      expect(refetchCandidates).toHaveBeenCalled();
      expect(refetchIdentities).toHaveBeenCalled();
      expect(screen.getByText("候选已通过审核并交由权威替换流程")).toBeInTheDocument();
    },
    KNOWLEDGE_GOVERNANCE_INTERACTION_TIMEOUT_MS,
  );

  it("lets platform governance schedule a successor and grace period", async () => {
    const retiringIdentity = {
      ...realIdentity,
      domain: "DRUG",
    };
    const successor = {
      ...retiringIdentity,
      id: 43,
      identityCode: "plat:drug:vte-guide-2026",
      subject: "VTE 防治指南 2026",
      currentVersionId: 1002,
    };
    mockUseKnowledgeIdentities.mockReturnValue({
      data: {
        items: [retiringIdentity, successor],
        page: 1,
        size: 20,
        total: 2,
        hasNext: false,
      },
      refetch: refetchIdentities,
      isLoading: false,
      isError: false,
      error: undefined,
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "安排弃用：VTE 防治指南" }));
    const successorSelect = screen.getByRole("combobox", { name: "后继知识身份" });
    fireEvent.mouseDown(successorSelect);
    fireEvent.change(successorSelect, { target: { value: "2026" } });
    await waitFor(() => {
      expect(mockUseKnowledgeIdentities).toHaveBeenLastCalledWith(
        expect.objectContaining({
          domain: "DRUG",
          status: "ACTIVE",
          keyword: "2026",
          page: 1,
          size: 20,
        }),
      );
    });
    await user.click(
      await screen.findByText(/VTE 防治指南 2026/, {
        selector: ".ant-select-item-option-content",
      }),
    );
    const graceInput = screen.getByLabelText("宽限期结束时间", { selector: "input" });
    fireEvent.change(graceInput, {
      target: { value: "2099-07-09T08:00" },
    });
    const guidanceInput = screen.getByLabelText("迁移指引", { selector: "textarea" });
    fireEvent.change(guidanceInput, {
      target: { value: "迁移到新版指南并重新核对本地覆盖" },
    });
    expect(graceInput).toHaveValue("2099-07-09T08:00");
    expect(guidanceInput).toHaveValue("迁移到新版指南并重新核对本地覆盖");
    await user.click(screen.getByRole("button", { name: "确认安排弃用" }));

    await waitFor(() => {
      expect(deprecateIdentity).toHaveBeenCalledWith(
        expect.objectContaining({
          identityId: 42,
          successorIdentityId: 43,
          migrationGuidance: "迁移到新版指南并重新核对本地覆盖",
        }),
      );
    });
  });
});
