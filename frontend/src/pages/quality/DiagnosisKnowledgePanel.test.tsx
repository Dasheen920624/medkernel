import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

const versionPage = (items: unknown[]) => ({
  items,
  page: 1,
  size: 20,
  total: items.length,
  hasNext: false,
  totalEstimated: false,
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

function fillField(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

beforeEach(() => {
  Object.values(hooks).forEach((hook) => hook.mockReset());
  hooks.useKnowledgeIdentities.mockReturnValue(query({ items: [identity] }));
  hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([activeVersion])));
  hooks.useRuleDefinitions.mockReturnValue(query({ items: [] }));
  hooks.usePathwayTemplates.mockReturnValue(query({ items: [] }));
  hooks.useDiagnosisCriteria.mockReturnValue(query([]));
  hooks.useDiagnosisDifferentials.mockReturnValue(query([]));
  hooks.useDiagnosisCarePointers.mockReturnValue(query([]));
  hooks.useDiagnosisTestCases.mockReturnValue(query([]));
  hooks.useSecurityProfile.mockReturnValue(
    query({
      dataScope: { tenantId: "tenant-a" },
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
  it("loads diagnosis reference selectors through small server-side search pages", () => {
    renderPanel();

    const identityCalls = hooks.useKnowledgeIdentities.mock.calls.map(([params]) => params);
    expect(identityCalls).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          domain: "DIAGNOSIS",
          status: "ACTIVE",
          keyword: undefined,
          page: 1,
          size: 20,
        }),
        expect.objectContaining({
          status: "ACTIVE",
          keyword: undefined,
          page: 1,
          size: 20,
        }),
      ]),
    );
    expect(identityCalls.some((params) => params?.size === 100)).toBe(false);
    expect(hooks.useRuleDefinitions).toHaveBeenCalledWith(
      expect.objectContaining({
        status: "PUBLISHED",
        keyword: undefined,
        page: 1,
        size: 20,
      }),
      expect.any(Object),
    );
    expect(hooks.usePathwayTemplates).toHaveBeenCalledWith(
      expect.objectContaining({
        status: "PUBLISHED",
        keyword: undefined,
        page: 1,
        size: 20,
      }),
      expect.any(Object),
    );
  });

  it("loads diagnosis versions through server pagination", () => {
    renderPanel();

    expect(hooks.useKnowledgeVersions).toHaveBeenCalledWith(7, {
      page: 1,
      size: 20,
    });
  });

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
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));

    renderPanel();

    await user.type(screen.getByLabelText("发布说明"), "已核对来源和结构化标准");

    expect(screen.getByText(/至少需要一个回归病例/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /通过门禁并发布/ })).toBeDisabled();
  });

  it("blocks publishing when criteria contain constraints the B0 matcher cannot evaluate", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));
    hooks.useDiagnosisCriteria.mockReturnValue(
      query([
        {
          id: 1,
          findingTermCode: "EGFR_LOW",
          direction: "REQUIRED",
          weight: "MAJOR",
          valueConstraint: '{"operator":"lt","value":60}',
          temporalConstraint: null,
        },
      ]),
    );
    hooks.useDiagnosisTestCases.mockReturnValue(
      query([
        {
          id: 1,
          caseCode: "CASE-1",
          findings: "EGFR_LOW",
          expectedIdentityId: 7,
          expectedConfidence: "STRONG",
        },
      ]),
    );

    renderPanel();
    await user.type(screen.getByLabelText("发布说明"), "已核对来源和结构化标准");

    expect(screen.getByText(/包含数值或时序约束/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /通过门禁并发布/ })).toBeDisabled();
  });

  it("requires real electronic signature evidence for a high-risk diagnosis release", async () => {
    const user = userEvent.setup();
    const publish = mutation();
    hooks.useKnowledgeVersions.mockReturnValue(
      query(versionPage([{ ...editableVersion, riskLevel: "HIGH" }])),
    );
    hooks.useDiagnosisTestCases.mockReturnValue(
      query([
        {
          id: 1,
          caseCode: "CASE-1",
          findings: "FEVER",
          expectedIdentityId: 7,
          expectedConfidence: "STRONG",
        },
      ]),
    );
    hooks.usePublishDiagnosis.mockReturnValue(publish);

    renderPanel();

    fillField("发布说明", "已核对来源和回归病例");
    fillField("签名 ID", "sig-diagnosis-11");
    fillField("签名时间", "2026-06-10T01:00");
    fillField("签名人 ID", "expert-1");
    fillField("签名人姓名", "审核专家");
    fillField("签名摘要", "a".repeat(64));
    await user.click(screen.getByRole("button", { name: /通过门禁并发布/ }));
    await user.click(await screen.findByRole("button", { name: "确认发布" }));

    await waitFor(() => {
      expect(publish.mutateAsync).toHaveBeenCalledWith({
        identityId: 7,
        versionId: 11,
        reason: "已核对来源和回归病例",
        publishEvidence: {
          electronicSignature: {
            signatureId: "sig-diagnosis-11",
            signerId: "expert-1",
            signerName: "审核专家",
            signedAt: new Date("2026-06-10T01:00").toISOString(),
            signatureHash: "a".repeat(64),
          },
        },
      });
    });
  });

  it("hides platform activation from a medical-affairs account without platform publish permission", async () => {
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));
    hooks.useSecurityProfile.mockReturnValue(
      query({
        dataScope: { tenantId: "t-1" },
        permissions: [
          { code: "knowledge.read" },
          { code: "knowledge.write" },
          { code: "knowledge.publish" },
        ],
      }),
    );

    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("2027 候选版 · 待审核")).toBeInTheDocument();
    });
    expect(screen.queryByText("发布诊断知识")).not.toBeInTheDocument();
  });

  it("requires all platform quality gates before activating diagnosis knowledge", async () => {
    const user = userEvent.setup();
    const publish = mutation();
    hooks.useKnowledgeVersions.mockReturnValue(
      query(versionPage([{ ...editableVersion, riskLevel: "HIGH" }])),
    );
    hooks.useDiagnosisTestCases.mockReturnValue(
      query([
        {
          id: 1,
          caseCode: "CASE-1",
          findings: "FEVER",
          expectedIdentityId: 7,
          expectedConfidence: "STRONG",
        },
      ]),
    );
    hooks.useSecurityProfile.mockReturnValue(
      query({
        dataScope: { tenantId: "t-1" },
        permissions: [
          { code: "knowledge.read" },
          { code: "knowledge.write" },
          { code: "knowledge.publish" },
          { code: "platform.publish" },
        ],
      }),
    );
    hooks.usePublishDiagnosis.mockReturnValue(publish);

    renderPanel();

    fillField("发布说明", "平台质量门全部通过");
    fillField("签名 ID", "sig-platform-11");
    fillField("签名时间", "2026-06-10T01:00");
    fillField("签名人 ID", "platform-admin-1");
    fillField("签名人姓名", "平台治理管理员");
    fillField("签名摘要", "b".repeat(64));

    const publishButton = screen.getByRole("button", { name: /通过门禁并发布/ });
    expect(publishButton).toBeDisabled();

    for (const gate of [
      "结构校验",
      "术语绑定",
      "依赖完整性",
      "安全单调性",
      "影响模拟",
      "同行复核",
    ]) {
      fireEvent.click(screen.getByRole("checkbox", { name: gate }));
    }
    fillField("质量门摘要", "结构、术语、依赖、安全、模拟和同行复核均通过");
    expect(publishButton).toBeEnabled();

    await user.click(publishButton);
    await user.click(await screen.findByRole("button", { name: "确认发布" }));

    await waitFor(() => {
      expect(publish.mutateAsync).toHaveBeenCalledWith({
        identityId: 7,
        versionId: 11,
        reason: "平台质量门全部通过",
        publishEvidence: {
          electronicSignature: {
            signatureId: "sig-platform-11",
            signerId: "platform-admin-1",
            signerName: "平台治理管理员",
            signedAt: new Date("2026-06-10T01:00").toISOString(),
            signatureHash: "b".repeat(64),
          },
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            peerReviewSigned: true,
            summary: "结构、术语、依赖、安全、模拟和同行复核均通过",
          },
        },
      });
    });
  });

  it("creates a diagnosis asset exactly once", async () => {
    const user = userEvent.setup();
    const createAsset = mutation();
    createAsset.mutateAsync.mockResolvedValue({
      identity: { ...identity, id: 8 },
      version: { ...editableVersion, id: 12, identityId: 8 },
    });
    hooks.useCreateDiagnosisAsset.mockReturnValue(createAsset);

    renderPanel();
    await user.click(screen.getByRole("button", { name: /新建诊断资产/ }));

    fillField("诊断名称", "验收诊断");
    fillField("身份标识", "acceptance-diagnosis");
    fillField("来源标题", "验收指南");
    fillField("来源编码", "GUIDE.ACCEPTANCE");
    fillField("分级依据", "受控指南来源");
    fillField("来源版本", "2026");
    fillField("受控文件地址", "repository://acceptance");
    fillField("来源原文", "验收诊断依据原文");
    fillField("知识版本", "1");
    fillField("证据锚点路径", "section-1");
    fillField("证据锚点名称", "验收标准");
    fillField("诊断依据原文片段", "验收诊断依据原文");
    await user.click(screen.getByRole("button", { name: "创建草稿" }));

    await waitFor(() => {
      expect(createAsset.mutateAsync).toHaveBeenCalledTimes(1);
    });
  });

  it("presents care pointers only as physician-confirmed suggestions", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));

    renderPanel();
    await user.click(screen.getByRole("tab", { name: /诊疗建议/ }));
    await user.click(screen.getByRole("button", { name: /新增建议/ }));

    expect(screen.getByText(/医师确认后的软建议/)).toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("selects the expected diagnosis from the identity directory instead of typing a database id", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));

    renderPanel();
    await user.click(screen.getByRole("tab", { name: /回归病例/ }));
    await user.click(screen.getByRole("button", { name: /新增病例/ }));

    expect(screen.queryByRole("spinbutton", { name: /期望诊断/ })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "期望诊断" })).toBeInTheDocument();
    expect(screen.getAllByText("慢性肾脏病 · DX.CKD").length).toBeGreaterThan(0);
  });
});
