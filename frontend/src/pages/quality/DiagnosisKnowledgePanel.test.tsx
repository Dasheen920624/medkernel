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
  useStandardTerms: vi.fn(),
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

function renderPanel(evidenceDetailsEnabled = false) {
  return render(
    <ConfigProvider>
      <AntdApp>
        <DiagnosisKnowledgePanel evidenceDetailsEnabled={evidenceDetailsEnabled} />
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
  hooks.useStandardTerms.mockReturnValue(
    query({
      items: [
        {
          id: 88,
          tenantId: "tenant-1",
          standardSystem: "TERM.LAB",
          termCode: "TERM.LAB.FRONTDESK.K",
          category: "LAB",
          displayName: "前台演练血钾",
          normalizedName: "前台演练血钾",
          versionNo: "2026.07",
          status: "ACTIVE",
        },
      ],
      total: 1,
    }),
  );
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

  it("uses clinical diagnosis wording by default and hides low-frequency identifiers", async () => {
    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("知识身份已关联")).toBeInTheDocument();
    });
    expect(screen.getAllByText("慢性肾脏病").length).toBeGreaterThan(0);
    expect(screen.queryByText("身份编码")).not.toBeInTheDocument();
    expect(screen.queryByText(/DX\.CKD/)).not.toBeInTheDocument();
  });

  it("uses business wording for diagnosis criterion weight by default", async () => {
    hooks.useDiagnosisCriteria.mockReturnValue(
      query([
        {
          id: 1,
          findingTermCode: "EGFR_LOW",
          direction: "SUPPORTING",
          weight: "MAJOR",
          valueConstraint: null,
          temporalConstraint: null,
        },
      ]),
    );

    renderPanel();

    await waitFor(() => {
      expect(screen.getByText("发现项已登记")).toBeInTheDocument();
    });
    expect(screen.getByText("主要")).toBeInTheDocument();
    expect(screen.queryByText("MAJOR")).not.toBeInTheDocument();
  });

  it("默认隐藏模型生产任务版本标识，仅在证据细节中披露", async () => {
    const technicalVersion = {
      ...activeVersion,
      versionNo: "3",
      versionLabel: "ai-draft-task-b6d43db7078b4445a08dadcaf1",
    };
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([technicalVersion])));

    const { unmount } = renderPanel();

    await waitFor(() => {
      expect(screen.getByText("第 3 版 · 已生效")).toBeInTheDocument();
    });
    expect(screen.queryByText(/ai-draft-task/)).not.toBeInTheDocument();

    unmount();
    renderPanel(true);

    await waitFor(() => {
      expect(
        screen.getByText("第 3 版 · ai-draft-task-b6d43db7078b4445a08dadcaf1 · 已生效"),
      ).toBeInTheDocument();
    });
  });

  it("没有业务版次时默认用状态版名替代模型任务标识", async () => {
    const technicalVersion = {
      ...activeVersion,
      versionNo: "ai-draft-task-b6d43db7078b4445a08dadcaf1",
      versionLabel: "ai-draft-task-b6d43db7078b4445a08dadcaf1",
    };
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([technicalVersion])));

    const { unmount } = renderPanel();

    await waitFor(() => {
      expect(screen.getByText("当前生效版本 · 已生效")).toBeInTheDocument();
    });
    expect(screen.queryByText(/ai-draft-task/)).not.toBeInTheDocument();

    unmount();
    renderPanel(true);

    await waitFor(() => {
      expect(
        screen.getByText("当前生效版本 · ai-draft-task-b6d43db7078b4445a08dadcaf1 · 已生效"),
      ).toBeInTheDocument();
    });
  });

  it("reveals diagnosis identity evidence only when evidence details are enabled", async () => {
    renderPanel(true);

    await waitFor(() => {
      expect(screen.getByText("身份编码")).toBeInTheDocument();
    });
    expect(screen.getByText("DX.CKD")).toBeInTheDocument();
  });

  it("blocks publishing an editable version until at least one validation case exists", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));

    renderPanel();

    await user.type(screen.getByLabelText("发布说明"), "已核对来源和结构化标准");

    expect(screen.getByText(/至少需要一个验证病例/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /通过校验并发布/ })).toBeDisabled();
  });

  it("blocks publishing when criteria contain constraints the basic rule check cannot evaluate", async () => {
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
    expect(screen.getByRole("button", { name: /通过校验并发布/ })).toBeDisabled();
  });

  it("allows the responsible operator to release high-risk diagnosis knowledge", async () => {
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

    fillField("发布说明", "已核对来源和验证病例");
    await user.click(screen.getByRole("button", { name: /通过校验并发布/ }));
    await user.click(await screen.findByRole("button", { name: "确认发布" }));

    await waitFor(() => {
      expect(publish.mutateAsync).toHaveBeenCalledWith({
        identityId: 7,
        versionId: 11,
        reason: "已核对来源和验证病例",
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

    fillField("发布说明", "平台发布质量校验全部通过");

    const publishButton = screen.getByRole("button", { name: /通过校验并发布/ });
    expect(publishButton).toBeDisabled();

    for (const gate of ["结构校验", "术语绑定", "依赖完整性", "安全单调性", "影响评估"]) {
      fireEvent.click(screen.getByRole("checkbox", { name: gate }));
    }
    fillField("校验说明", "结构、术语、依赖、安全和评估均通过");
    expect(publishButton).toBeEnabled();

    await user.click(publishButton);
    await user.click(await screen.findByRole("button", { name: "确认发布" }));

    await waitFor(() => {
      expect(publish.mutateAsync).toHaveBeenCalledWith({
        identityId: 7,
        versionId: 11,
        reason: "平台发布质量校验全部通过",
        publishEvidence: {
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            summary: "结构、术语、依赖、安全和评估均通过",
          },
        },
      });
    });
  });

  it("creates a diagnosis asset exactly once", async () => {
    const createAsset = mutation();
    createAsset.mutateAsync.mockResolvedValue({
      identity: { ...identity, id: 8 },
      version: { ...editableVersion, id: 12, identityId: 8 },
    });
    hooks.useCreateDiagnosisAsset.mockReturnValue(createAsset);

    renderPanel();
    fireEvent.click(screen.getByRole("button", { name: /新建诊断资产/ }));

    expect(screen.getByPlaceholderText("例如 manxing-shenbing")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("粘贴受控资料库地址或院内文档链接")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("例如 chronic-kidney-disease")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("repository://...")).not.toBeInTheDocument();

    fillField("诊断名称", "验收诊断");
    fillField("稳定诊断身份", "acceptance-diagnosis");
    fillField("来源标题", "验收指南");
    fillField("稳定来源身份", "GUIDE.ACCEPTANCE");
    fillField("分级依据", "受控指南来源");
    fillField("来源版本", "2026");
    fillField("受控文件地址", "repository://acceptance");
    fillField("来源原文", "验收诊断依据原文");
    fillField("知识版本", "1");
    fillField("证据锚点路径", "section-1");
    fillField("证据锚点名称", "验收标准");
    fillField("诊断依据原文片段", "验收诊断依据原文");
    fireEvent.click(screen.getByRole("button", { name: "创建草稿" }));

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

  it("shows care pointer targets as business assets by default", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));
    hooks.useRuleDefinitions.mockReturnValue(
      query({ items: [{ id: 21, name: "慢病检查规则", ruleCode: "RULE.CKD.WORKUP" }] }),
    );
    hooks.useDiagnosisCarePointers.mockReturnValue(
      query([
        {
          id: 1,
          pointerType: "WORKUP",
          targetType: "RULE",
          targetRef: "RULE.CKD.WORKUP",
          description: "建议复查肾功能",
        },
      ]),
    );

    renderPanel();
    await user.click(screen.getByRole("tab", { name: /诊疗建议/ }));

    expect(screen.getByText("检查建议")).toBeInTheDocument();
    expect(screen.getByText("规则资产")).toBeInTheDocument();
    expect(screen.getByText("慢病检查规则")).toBeInTheDocument();
    expect(screen.queryByText("WORKUP")).not.toBeInTheDocument();
    expect(screen.queryByText("RULE")).not.toBeInTheDocument();
    expect(screen.queryByText("RULE.CKD.WORKUP")).not.toBeInTheDocument();
  });

  it("shows validation cases as business evidence by default", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));
    hooks.useDiagnosisTestCases.mockReturnValue(
      query([
        {
          id: 1,
          caseCode: "CASE-CKD-001",
          findings: "EGFR_LOW,ALBUMINURIA",
          expectedIdentityId: 7,
          expectedConfidence: "STRONG",
        },
      ]),
    );

    renderPanel();
    await user.click(screen.getByRole("tab", { name: /验证病例/ }));

    expect(screen.getByText("验证病例已登记")).toBeInTheDocument();
    expect(screen.getByText("发现项证据已记录")).toBeInTheDocument();
    expect(screen.getAllByText("慢性肾脏病").length).toBeGreaterThan(0);
    expect(screen.getByText("强支持")).toBeInTheDocument();
    expect(screen.queryByText("CASE-CKD-001")).not.toBeInTheDocument();
    expect(screen.queryByText("EGFR_LOW,ALBUMINURIA")).not.toBeInTheDocument();
    expect(screen.queryByText(/DX\.CKD/)).not.toBeInTheDocument();
  });

  it("selects the expected diagnosis from the identity directory instead of typing a database id", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));

    renderPanel();
    await user.click(screen.getByRole("tab", { name: /验证病例/ }));
    await user.click(screen.getByRole("button", { name: /新增病例/ }));

    expect(screen.queryByRole("spinbutton", { name: /期望诊断/ })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "期望诊断" })).toBeInTheDocument();
    expect(screen.getAllByText("慢性肾脏病").length).toBeGreaterThan(0);
    expect(screen.queryByText(/DX\.CKD/)).not.toBeInTheDocument();
  });

  it("uses stable business identity wording when operators add criteria and validation cases", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));

    renderPanel();

    await user.click(screen.getByRole("button", { name: /新增标准/ }));
    expect(screen.getByLabelText("标准发现项身份")).toBeInTheDocument();
    expect(screen.queryByText("标准发现项编码")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await user.click(screen.getByRole("tab", { name: /验证病例/ }));
    await user.click(screen.getByRole("button", { name: /新增病例/ }));

    expect(screen.getByLabelText("稳定验证病例身份")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("如 manxing-shenbing-case-001，用于复算与验收追溯"),
    ).toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText("如 CKD-CASE-001，用于复算与验收追溯"),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("发现项身份")).toBeInTheDocument();
    expect(screen.getByText("多个标准发现项身份使用英文逗号分隔")).toBeInTheDocument();
    expect(screen.queryByText("病例编码")).not.toBeInTheDocument();
    expect(screen.queryByText("发现项编码")).not.toBeInTheDocument();
  });

  it("offers active standard terminology choices when operators add criteria", async () => {
    const user = userEvent.setup();
    hooks.useKnowledgeVersions.mockReturnValue(query(versionPage([editableVersion])));

    renderPanel();
    await user.click(screen.getByRole("button", { name: /新增标准/ }));

    expect(hooks.useStandardTerms).toHaveBeenCalledWith(
      expect.objectContaining({
        status: "ACTIVE",
        keyword: undefined,
        page: 1,
        size: 50,
      }),
    );
    await user.click(screen.getByRole("combobox", { name: "标准发现项身份" }));
    expect(screen.getByText("前台演练血钾")).toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText("如 EGFR_LOW，用于稳定追溯该发现项"),
    ).not.toBeInTheDocument();
  });
});
