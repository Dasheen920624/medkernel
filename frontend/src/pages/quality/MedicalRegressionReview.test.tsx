import { ConfigProvider } from "antd";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useModelEvaluationRunDetail,
  useModelEvaluationRuns,
  useSecurityProfile,
  useSignOffModelEvaluation,
} from "@/shared/api/hooks";
import { useExpertModeStore } from "@/shared/lib/expertModeStore";

import MedicalRegressionReview from "./MedicalRegressionReview";

vi.mock("@/shared/api/hooks", () => ({
  useModelEvaluationRunDetail: vi.fn(),
  useModelEvaluationRuns: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSignOffModelEvaluation: vi.fn(),
}));

const run = {
  runId: 42,
  providerCode: "mimo-external",
  modelVersion: "mimo-v2.5",
  capabilityCode: "rule.draft",
  promptVersion: "prompt:v2",
  toolVersion: "tool:v3",
  totalCases: 1,
  passedCases: 1,
  failedCases: 0,
  fakeCitationDetected: false,
  redLineBreach: false,
  hallucinationDetected: false,
  status: "PENDING_REVIEW" as const,
  reviewer: null,
  signedAt: null,
  reviewComment: null,
  createdAt: "2026-06-18T08:00:00Z",
  createdBy: "quality-author",
};

const detail = {
  run,
  evidenceComplete: true,
  baselineCurrent: true,
  releaseCurrent: true,
  reviewable: true,
  reviewBlockReason: null,
  cases: [
    {
      evidenceId: 501,
      regressionCaseId: 101,
      caseVersion: "2026.1",
      caseInput: "活动性出血患者是否可使用该药？",
      expectedPhrase: "活动性出血禁用",
      redLineType: "CONTRAINDICATION",
      sourceReference: "source-version:88#contraindication",
      outputContent: "活动性出血禁用。来源：source-version:88#contraindication",
      sourceCitations: '["source-version:88#contraindication"]',
      expectedPhraseHit: true,
      citationRequired: true,
      citationVerified: true,
      redLineCase: true,
      redLineBreach: false,
      passed: true,
      failureReasons: [],
    },
  ],
};

const signOff = vi.fn();

function query<T>(data: T) {
  return {
    data,
    isLoading: false,
    isError: false,
    isFetching: false,
    refetch: vi.fn(),
  };
}

describe("MedicalRegressionReview", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    useExpertModeStore.setState({ enabled: false });
    vi.mocked(useSecurityProfile).mockReturnValue(
      query({
        userId: "quality-reviewer",
        username: "质量复核员",
        roles: [{ code: "quality-governor" }],
        permissions: [
          { code: "llm.eval.manage", dimension: "ACTION", target: "llm.eval", risk: "HIGH" },
          {
            code: "menu.model-evaluation-review",
            dimension: "MENU",
            target: "model-evaluation-review",
            risk: "LOW",
          },
        ],
        menuKeys: ["model-evaluation-review"],
        environmentKeys: ["production"],
        dataScope: { tenantId: "tenant-1" },
        mustChangePwd: false,
        mfaRequired: true,
        mfaBound: true,
      }) as never,
    );
    vi.mocked(useModelEvaluationRuns).mockReturnValue(
      query({
        items: [run],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
        totalEstimated: false,
      }) as never,
    );
    vi.mocked(useModelEvaluationRunDetail).mockReturnValue(query(detail) as never);
    signOff.mockResolvedValue(undefined);
    vi.mocked(useSignOffModelEvaluation).mockReturnValue({
      mutateAsync: signOff,
      isPending: false,
    } as never);
  });

  it("shows paged pending runs and reveals immutable evidence before technical fields", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <MedicalRegressionReview />
      </ConfigProvider>,
    );

    expect(useModelEvaluationRuns).toHaveBeenCalledWith(
      { status: "PENDING_REVIEW", page: 1, size: 20 },
      true,
    );
    expect(screen.getByRole("heading", { name: "医学回归复核" })).toBeInTheDocument();
    expect(screen.getByText("mimo-v2.5")).toBeInTheDocument();
    expect(screen.getByText("临床规则草案拟定")).toBeInTheDocument();
    expect(screen.queryByText("mimo-external")).not.toBeInTheDocument();
    expect(screen.queryByText("rule.draft")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "核查证据" }));
    expect(await screen.findByText("活动性出血患者是否可使用该药？")).toBeInTheDocument();
    expect(
      screen.getByText("活动性出血禁用。来源：source-version:88#contraindication"),
    ).toBeInTheDocument();
    expect(screen.getByText("source-version:88#contraindication")).toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "专家模式" }));
    expect(screen.getByText("mimo-external")).toBeInTheDocument();
    expect(screen.getByText("prompt:v2")).toBeInTheDocument();
    expect(screen.getByText("rule.draft")).toBeInTheDocument();
  });

  it("requires explicit acknowledgement and a substantive review comment before signing", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <MedicalRegressionReview />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("button", { name: "核查证据" }));
    await user.click(await screen.findByRole("button", { name: "专家复核签字" }));
    const confirm = screen.getByRole("button", { name: "确认专家复核" });
    expect(confirm).toBeDisabled();

    await user.click(
      screen.getByRole("checkbox", { name: "我已逐例核对模型输出、来源引用与医学红线判定" }),
    );
    await user.type(
      screen.getByLabelText("复核意见"),
      "逐例证据已核查，来源可靠且未突破医学红线。 ",
    );
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    expect(signOff).toHaveBeenCalledWith({
      runId: 42,
      evidenceAcknowledged: true,
      reviewComment: "逐例证据已核查，来源可靠且未突破医学红线。",
    });
  });

  it("blocks sign-off honestly when per-case evidence is incomplete", async () => {
    const user = userEvent.setup();
    vi.mocked(useModelEvaluationRunDetail).mockReturnValue(
      query({
        ...detail,
        evidenceComplete: false,
        reviewable: false,
        reviewBlockReason: "逐例证据不完整，请重新运行评测",
      }) as never,
    );

    render(
      <ConfigProvider>
        <MedicalRegressionReview />
      </ConfigProvider>,
    );
    await user.click(screen.getByRole("button", { name: "核查证据" }));

    expect(await screen.findByText("逐例证据不完整，请重新运行评测")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "专家复核签字" })).not.toBeInTheDocument();
  });

  it("shows a successful expert sign-off instead of treating PASSED as an error", async () => {
    const user = userEvent.setup();
    vi.mocked(useModelEvaluationRunDetail).mockReturnValue(
      query({
        ...detail,
        run: {
          ...run,
          status: "PASSED",
          reviewer: "quality-governor",
          signedAt: "2026-06-19T14:34:42Z",
          reviewComment: "逐例核验完成，来源与红线结论均认可。",
        },
        reviewable: false,
        reviewBlockReason: "该运行当前不是待复核状态",
      }) as never,
    );

    render(
      <ConfigProvider>
        <MedicalRegressionReview />
      </ConfigProvider>,
    );
    await user.click(screen.getByRole("button", { name: "核查证据" }));

    expect(await screen.findByText("已由独立专家签署放行")).toBeInTheDocument();
    expect(screen.getByText("quality-governor")).toBeInTheDocument();
    expect(screen.getAllByText("逐例核验完成，来源与红线结论均认可。").length).toBeGreaterThan(0);
    expect(screen.queryByText("当前运行不可签字")).not.toBeInTheDocument();
  });

  it("warns when a signed run belongs to a historical release", async () => {
    const user = userEvent.setup();
    vi.mocked(useModelEvaluationRunDetail).mockReturnValue(
      query({
        ...detail,
        run: {
          ...run,
          status: "PASSED",
          reviewer: "quality-governor",
          signedAt: "2026-06-19T14:34:42Z",
          reviewComment: "逐例核验完成，来源与红线结论均认可。",
        },
        releaseCurrent: false,
        reviewable: false,
        reviewBlockReason: "该评测属于历史运行制品，必须在当前制品重新运行",
      }) as never,
    );

    render(
      <ConfigProvider>
        <MedicalRegressionReview />
      </ConfigProvider>,
    );
    await user.click(screen.getByRole("button", { name: "核查证据" }));

    expect(await screen.findByText("历史制品签署仅保留审计，不可用于当前放行")).toBeInTheDocument();
    expect(screen.getByText("该评测属于历史运行制品，必须在当前制品重新运行")).toBeInTheDocument();
  });
});
