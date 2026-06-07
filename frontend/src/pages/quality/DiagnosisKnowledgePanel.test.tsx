import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import DiagnosisKnowledgePanel from "./DiagnosisKnowledgePanel";

const hooks = vi.hoisted(() => ({
  useKnowledgeIdentities: vi.fn(),
  useKnowledgeVersions: vi.fn(),
  useRuleDefinitions: vi.fn(),
  usePathwayTemplates: vi.fn(),
  useDiagnosisCriteria: vi.fn(),
  useDiagnosisDifferentials: vi.fn(),
  useDiagnosisCarePointers: vi.fn(),
  useDiagnosisTestCases: vi.fn(),
  useSecurityProfile: vi.fn(),
  useCreateDiagnosisAsset: vi.fn(),
  useCreateDiagnosisVersion: vi.fn(),
  useAddDiagnosisCriterion: vi.fn(),
  useAddDiagnosisDifferential: vi.fn(),
  useAddDiagnosisCarePointer: vi.fn(),
  useAddDiagnosisTestCase: vi.fn(),
  usePublishDiagnosis: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => hooks);

const identity = {
  id: 7,
  identityCode: "DX.CKD",
  domain: "DIAGNOSIS",
  subject: "慢性肾脏病",
  status: "ACTIVE",
};

const activeVersion = {
  id: 10,
  identityId: 7,
  versionNo: "2026",
  versionLabel: "2026 版",
  status: "ACTIVE",
};

const editableVersion = {
  ...activeVersion,
  id: 11,
  versionLabel: "2027 候选版",
  status: "PENDING_REPLACEMENT_REVIEW",
};

const query = (data: unknown) => ({
  data,
  isLoading: false,
  isError: false,
  error: undefined,
  refetch: vi.fn(),
});

const mutation = () => ({
  mutateAsync: vi.fn(),
  isPending: false,
});

function renderPanel() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <DiagnosisKnowledgePanel />
      </AntdApp>
    </ConfigProvider>,
  );
}

beforeEach(() => {
  Object.values(hooks).forEach((hook) => hook.mockReset());
  hooks.useKnowledgeIdentities.mockReturnValue(query({ items: [identity] }));
  hooks.useKnowledgeVersions.mockReturnValue(query([activeVersion]));
  hooks.useRuleDefinitions.mockReturnValue(query({ items: [] }));
  hooks.usePathwayTemplates.mockReturnValue(query({ items: [] }));
  hooks.useDiagnosisCriteria.mockReturnValue(query([]));
  hooks.useDiagnosisDifferentials.mockReturnValue(query([]));
  hooks.useDiagnosisCarePointers.mockReturnValue(query([]));
  hooks.useDiagnosisTestCases.mockReturnValue(query([]));
  hooks.useSecurityProfile.mockReturnValue(
    query({
      permissions: [
        { code: "knowledge.read" },
        { code: "knowledge.write" },
        { code: "knowledge.publish" },
      ],
    }),
  );
  hooks.useCreateDiagnosisAsset.mockReturnValue(mutation());
  hooks.useCreateDiagnosisVersion.mockReturnValue(mutation());
  hooks.useAddDiagnosisCriterion.mockReturnValue(mutation());
  hooks.useAddDiagnosisDifferential.mockReturnValue(mutation());
  hooks.useAddDiagnosisCarePointer.mockReturnValue(mutation());
  hooks.useAddDiagnosisTestCase.mockReturnValue(mutation());
  hooks.usePublishDiagnosis.mockReturnValue(mutation());
});

describe("DiagnosisKnowledgePanel", () => {
  it("shows an honest loading state while diagnosis identities are being read", () => {
    hooks.useKnowledgeIdentities.mockReturnValue({
      ...query(undefined),
      isLoading: true,
    });

    renderPanel();

    expect(screen.getByText("正在加载诊断知识")).toBeInTheDocument();
    expect(screen.queryByText("暂无诊断知识")).not.toBeInTheDocument();
  });

  it("keeps active diagnosis versions immutable", async () => {
    const user = userEvent.setup();
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("当前版本只读")).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /新增标准/ })).toBeDisabled();
    expect(screen.queryByText("发布诊断知识")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /新建版本/ }));
    expect(screen.getByText("为慢性肾脏病新建证据完整版本")).toBeInTheDocument();
    expect(screen.queryByLabelText("诊断名称")).not.toBeInTheDocument();
  });

  it("blocks publishing an editable version until at least one regression case exists", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query([editableVersion]));

    renderPanel();

    await user.type(screen.getByLabelText("发布说明"), "已核对来源和结构化标准");

    expect(screen.getByText(/至少需要一个回归病例/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /通过门禁并发布/ })).toBeDisabled();
  });

  it("presents care pointers only as physician-confirmed suggestions", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query([editableVersion]));

    renderPanel();
    await user.click(screen.getByRole("tab", { name: /诊疗建议/ }));
    await user.click(screen.getByRole("button", { name: /新增建议/ }));

    expect(screen.getByText(/医师确认后的软建议/)).toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("selects the expected diagnosis from the identity directory instead of typing a database id", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query([editableVersion]));

    renderPanel();
    await user.click(screen.getByRole("tab", { name: /回归病例/ }));
    await user.click(screen.getByRole("button", { name: /新增病例/ }));

    expect(screen.queryByRole("spinbutton", { name: /期望诊断/ })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "期望诊断" })).toBeInTheDocument();
    expect(screen.getAllByText("慢性肾脏病 · DX.CKD").length).toBeGreaterThan(0);
  });
});
