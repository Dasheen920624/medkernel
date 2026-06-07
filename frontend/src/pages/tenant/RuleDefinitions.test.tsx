import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import RuleDefinitions from "./RuleDefinitions";
import type { RuleDefinition, RuleDetailResponse } from "@/shared/api/hooks";

const apiMocks = vi.hoisted(() => ({
  ruleListData: { items: [], total: 0 } as unknown,
  ruleDetailData: null as unknown,
  snapshotsData: { items: [], total: 0 } as unknown,
  snapshotDetailData: null as unknown,
  impactData: null as unknown,
  refetchList: vi.fn(),
  refetchDetail: vi.fn(),
  createRule: vi.fn(),
  addTestCase: vi.fn(),
  runRuleTests: vi.fn(),
  simulateRule: vi.fn(),
  publishRule: vi.fn(),
  fullRolloutRule: vi.fn(),
}));

const RULE_DEFINITION_INTERACTION_TIMEOUT_MS = 15_000;

vi.mock("@/shared/api/hooks", () => ({
  useRuleDefinitions: () => ({
    data: apiMocks.ruleListData,
    isLoading: false,
    isError: false,
    error: null,
    refetch: apiMocks.refetchList,
  }),
  useRuleDetail: () => ({
    data: apiMocks.ruleDetailData,
    isLoading: false,
    refetch: apiMocks.refetchDetail,
  }),
  useContextSnapshots: () => ({
    data: apiMocks.snapshotsData,
    isLoading: false,
    isError: false,
  }),
  useContextFieldCatalog: () => ({ data: [], isLoading: false, isError: false }),
  useStandardTerms: () => ({ data: { items: [], total: 0 }, isLoading: false, isError: false }),
  useMappingCoverage: () => ({ data: [], isLoading: false, isError: false }),
  useCreateContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useContextSnapshotDetail: () => ({
    data: apiMocks.snapshotDetailData,
    isLoading: false,
    isError: false,
  }),
  useRuleImpact: () => ({
    data: apiMocks.impactData,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useCreateRule: () => ({
    mutateAsync: apiMocks.createRule,
    isPending: false,
  }),
  useAddTestCase: () => ({
    mutateAsync: apiMocks.addTestCase,
    isPending: false,
  }),
  useRunRuleTests: () => ({
    mutateAsync: apiMocks.runRuleTests,
    isPending: false,
  }),
  useSimulateRule: () => ({
    mutateAsync: apiMocks.simulateRule,
    isPending: false,
  }),
  usePublishRule: () => ({
    mutateAsync: apiMocks.publishRule,
    isPending: false,
  }),
  useFullRolloutRule: () => ({
    mutateAsync: apiMocks.fullRolloutRule,
    isPending: false,
  }),
}));

function renderRuleDefinitions() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <RuleDefinitions />
      </AntdApp>
    </ConfigProvider>,
  );
}

const draftRule: RuleDefinition = {
  id: 1,
  ruleId: "rule-1",
  tenantId: "tenant-A",
  ruleCode: "RULE.QC.REVIEW",
  name: "规则发布门禁核查",
  ruleType: "QUALITY",
  authoringMode: "VISUAL",
  riskLevel: "HIGH",
  status: "DRAFT",
  activeVersionId: "ver-1",
  packageVersion: "pkg-2026.06",
  createdAt: "2026-06-02T00:00:00Z",
  createdBy: "u-1",
  updatedAt: "2026-06-02T00:00:00Z",
};

function createRuleDetail(): RuleDetailResponse {
  return {
    definition: draftRule,
    deploymentStatus: "DRAFT",
    version: {
      id: 1,
      versionId: "ver-1",
      ruleId: "rule-1",
      versionNo: 1,
      sourceRef: "院内已审核制度",
      changeSummary: "补齐门禁",
      dslJson: JSON.stringify({
        trigger: "result-review",
        when: {
          all: [{ fact: "observations.0.value", operator: "gte", value: 6 }],
        },
        then: [
          {
            actionCode: "STRONG_REMINDER",
            atSeverity: "HIGH",
            indicator: "critical",
            summary: "需人工复核",
            detail: "命中后提交人工复核。",
            source: { label: "院内已审核制度" },
            suggestions: [],
            overrideReasons: [],
            requiresPhysicianConfirmation: true,
          },
        ],
        explain: { summary: "命中已审核字段" },
      }),
      explanationJson: JSON.stringify({ summary: "命中已审核字段" }),
      status: "DRAFT",
      createdAt: "2026-06-02T00:00:00Z",
    },
    testCases: ["POSITIVE", "NEGATIVE", "BOUNDARY", "CONFLICT"].map((caseType) => ({
      id: caseType.length,
      caseId: `case-${caseType}`,
      ruleId: "rule-1",
      versionId: "ver-1",
      caseType,
      contextSnapshotId: `ctx-${caseType}`,
      inputPayload: "{}",
      expectedHit: caseType !== "NEGATIVE",
      expectedSeverity: "HIGH",
      expectedActionCode: "STRONG_REMINDER",
      lastHit: caseType !== "NEGATIVE",
      lastStatus: "PASS",
      lastMessage: "通过",
      lastRunAt: "2026-06-02T00:00:00Z",
      createdAt: "2026-06-02T00:00:00Z",
    })),
  };
}

function setActiveSnapshotFixture() {
  apiMocks.snapshotsData = {
    items: [
      {
        snapshotId: "ctx-001",
        patientId: "P-001",
        encounterId: "E-001",
        status: "ACTIVE",
        qualityStatus: "COMPLETE",
        createdAt: "2026-06-02T08:00:00Z",
      },
    ],
    total: 1,
  };
  apiMocks.snapshotDetailData = {
    snapshotId: "ctx-001",
    status: "ACTIVE",
    packageVersion: "pkg-2026.06",
    qualityStatus: "COMPLETE",
    missingFields: [],
    mappingStatus: {},
    resources: {
      patient: { patientId: "P-001" },
      encounters: [{ encounterId: "E-001" }],
      observations: [{ code: "OBS.TEST", value: 6 }],
      conditions: [],
      nursingAssessments: [],
      diagnosticReports: [],
      medications: [],
      procedures: [],
      documents: [],
      carePlans: [],
      followUps: [],
      claims: [],
    },
    createdAt: "2026-06-02T08:00:00Z",
    traceId: "trace-ctx",
  };
}

async function openDraftRuleDrawer() {
  const user = userEvent.setup();
  renderRuleDefinitions();
  await screen.findByText("RULE.QC.REVIEW");
  await user.click(screen.getByRole("button", { name: "查看配置与试运行" }));
  await screen.findByText("规则配置详情与试运行");
  return user;
}

describe("RuleDefinitions 三层规则编辑体验", () => {
  beforeEach(() => {
    apiMocks.ruleListData = { items: [], total: 0 };
    apiMocks.ruleDetailData = null;
    apiMocks.snapshotsData = { items: [], total: 0 };
    apiMocks.snapshotDetailData = null;
    apiMocks.impactData = null;
    apiMocks.refetchList.mockReset();
    apiMocks.refetchDetail.mockReset();
    apiMocks.createRule.mockReset();
    apiMocks.addTestCase.mockReset();
    apiMocks.runRuleTests.mockReset();
    apiMocks.simulateRule.mockReset();
    apiMocks.publishRule.mockReset();
    apiMocks.fullRolloutRule.mockReset();
  });

  it(
    "创建规则时提供 L1 模板、L2 条件树与 L3 DSL，并能从 L2 同步到 L3",
    async () => {
      const user = userEvent.setup();
      apiMocks.createRule.mockResolvedValue({ ruleId: "rule-new" });
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));

      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      fireEvent.change(within(dialog).getByLabelText("规则唯一业务编码"), {
        target: { value: "RULE.CARDIOLOGY.HR" },
      });
      fireEvent.change(within(dialog).getByLabelText("规则显示名称"), {
        target: { value: "心率质控复核" },
      });
      fireEvent.change(within(dialog).getByLabelText("医学依据/来源"), {
        target: { value: "院内已审核心血管诊疗规范 2026" },
      });
      fireEvent.change(within(dialog).getByLabelText("标准上下文包版本"), {
        target: { value: "1.0.0" },
      });
      expect(within(dialog).getByRole("tab", { name: /L1 模板/ })).toBeInTheDocument();
      expect(within(dialog).getByRole("tab", { name: /L2 条件树/ })).toBeInTheDocument();
      expect(within(dialog).queryByRole("tab", { name: /L3 DSL/ })).not.toBeInTheDocument();
      await user.click(within(dialog).getByRole("switch", { name: "专家模式" }));
      expect(within(dialog).getByRole("tab", { name: /L3 DSL/ })).toBeInTheDocument();

      await user.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      expect(within(dialog).getByText("临床算子")).toBeInTheDocument();
      fireEvent.change(within(dialog).getByLabelText("上下文字段路径"), {
        target: { value: "observations.0.value" },
      });
      fireEvent.change(within(dialog).getByLabelText("比较值"), { target: { value: "6" } });
      await user.click(within(dialog).getByRole("button", { name: "添加动作" }));
      const summaries = within(dialog).getAllByLabelText("卡片摘要");
      fireEvent.change(summaries[1], { target: { value: "同步记录规则命中" } });
      await user.click(within(dialog).getByRole("button", { name: "同步到 DSL" }));

      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("规则 DSL JSON");
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"trigger": "result-review"');
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"fact": "observations.0.value"');
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"value": 6');
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"summary": "同步记录规则命中"');

      await user.click(within(dialog).getByRole("button", { name: "创建草稿" }));
      await waitFor(() =>
        expect(apiMocks.createRule).toHaveBeenCalledWith(
          expect.objectContaining({
            ruleCode: "RULE.CARDIOLOGY.HR",
            ruleType: "QUALITY",
            packageVersion: "1.0.0",
            dslJson: expect.objectContaining({
              trigger: "result-review",
              then: expect.arrayContaining([
                expect.objectContaining({ summary: "同步记录规则命中" }),
              ]),
            }),
          }),
        ),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "从真实上下文快照详情运行规则仿真，不要求人工粘贴 JSON",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = createRuleDetail();
      setActiveSnapshotFixture();
      apiMocks.simulateRule.mockResolvedValue({
        executionId: "exec-simulate-1",
        ruleId: "rule-1",
        versionId: "ver-1",
        hit: true,
        severity: "HIGH",
        actions: [
          {
            actionCode: "STRONG_REMINDER",
            severity: "HIGH",
            indicator: "critical",
            summary: "规则发布门禁核查",
            detail: "命中真实快照字段",
            source: { label: "院内已审核制度" },
            suggestions: [],
            overrideReasons: [],
            requiresPhysicianConfirmation: true,
          },
        ],
        explanation: { summary: "命中真实快照字段", evidence: ["OBS.TEST"] },
      });

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /真实快照试运行/ }));
      await user.click(screen.getByRole("switch", { name: "专家模式" }));

      expect(screen.queryByText("专家手工 JSON 兜底")).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "运行手工 JSON 试运行" }),
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText("患者 ID"), "P-001");
      await user.type(screen.getByLabelText("就诊 ID"), "E-001");
      await user.click(screen.getByRole("button", { name: "读取真实快照" }));
      await user.click(await screen.findByText("ctx-001"));
      await user.click(screen.getByRole("button", { name: "使用该快照试运行" }));

      await waitFor(() =>
        expect(apiMocks.simulateRule).toHaveBeenCalledWith({
          packageVersion: "pkg-2026.06",
          inputPayload: expect.objectContaining({
            observations: [expect.objectContaining({ code: "OBS.TEST", value: 6 })],
          }),
        }),
      );
      expect(await screen.findByText(/命中真实快照字段/)).toBeInTheDocument();
      expect(screen.getByText("必须医师确认")).toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "规则详情抽屉中的测试用例弹层高于抽屉，且只允许选择 ACTIVE 快照",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = createRuleDetail();

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /发布门禁测试用例/ }));
      await user.click(screen.getByRole("button", { name: /新增测试用例/ }));

      const title = await screen.findByText("新增测试用例", { selector: ".ant-modal-title" });
      const dialog = title.closest(".ant-modal-content");
      expect(dialog).not.toBeNull();
      expect(dialog?.closest(".ant-modal-wrap")).toHaveStyle({ zIndex: "1100" });
      expect(within(dialog as HTMLElement).getByLabelText("测试用例患者 ID")).toBeInTheDocument();
      expect(within(dialog as HTMLElement).getByLabelText("测试用例就诊 ID")).toBeInTheDocument();
      expect(within(dialog as HTMLElement).queryByText(/测试输入 JSON/)).not.toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "阴性用例不要求填写仅命中场景才有意义的严重度和动作",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = { ...createRuleDetail(), testCases: [] };
      apiMocks.addTestCase.mockResolvedValue({ caseId: "case-negative" });
      setActiveSnapshotFixture();

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /发布门禁测试用例/ }));
      await user.click(screen.getByRole("button", { name: /新增测试用例/ }));

      const title = await screen.findByText("新增测试用例", { selector: ".ant-modal-title" });
      const dialog = title.closest(".ant-modal-content");
      expect(dialog).not.toBeNull();
      const caseDialog = within(dialog as HTMLElement);
      await user.type(caseDialog.getByLabelText("测试用例患者 ID"), "P-001");
      await user.click(caseDialog.getByRole("button", { name: "读取 ACTIVE 快照" }));
      await user.click(await caseDialog.findByText("ctx-001"));
      await user.click(caseDialog.getByRole("combobox", { name: "用例类别" }));
      await user.click(
        await screen.findByText("阴性不命中用例", {
          selector: ".ant-select-item-option-content",
        }),
      );
      await user.click(caseDialog.getByRole("combobox", { name: "期望求值结果" }));
      await user.click(
        await screen.findByText("不应当命中", {
          selector: ".ant-select-item-option-content",
        }),
      );

      await waitFor(() => {
        expect(caseDialog.queryByLabelText("期望动作严重度")).not.toBeInTheDocument();
        expect(caseDialog.queryByLabelText("期望动作代码")).not.toBeInTheDocument();
      });

      await user.click(caseDialog.getByRole("button", { name: "保存用例" }));

      await waitFor(() =>
        expect(apiMocks.addTestCase).toHaveBeenCalledWith(
          expect.objectContaining({
            caseType: "NEGATIVE",
            contextSnapshotId: "ctx-001",
            expectedHit: false,
            expectedSeverity: undefined,
            expectedActionCode: undefined,
          }),
        ),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "未执行用例显示待执行并可从详情页执行全部用例",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = {
        ...createRuleDetail(),
        testCases: createRuleDetail().testCases.map((testCase) => ({
          ...testCase,
          lastHit: null,
          lastStatus: "NOT_RUN",
          lastMessage: null,
          lastRunAt: null,
        })),
      };
      apiMocks.runRuleTests.mockResolvedValue({ allPassed: true, results: [] });

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /发布门禁测试用例/ }));

      expect(screen.getAllByText("待执行")).toHaveLength(4);
      expect(screen.queryByText("FAIL")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /执行全部用例/ }));

      await waitFor(() =>
        expect(apiMocks.runRuleTests).toHaveBeenCalledWith({
          packageVersion: "pkg-2026.06",
        }),
      );
      expect(apiMocks.refetchDetail).toHaveBeenCalled();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "发布前展示 7 步流与影响摘要，并携带 impactDigest 与审核理由发布",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = createRuleDetail();
      apiMocks.impactData = {
        ruleId: "rule-1",
        versionId: "ver-1",
        riskLevel: "HIGH",
        analysisStatus: "COMPLETE",
        impactDigest: "sha256:impact-abc",
        affectedRules: [
          {
            objectType: "RULE",
            objectId: "rule-1",
            displayName: "规则发布门禁核查",
            impactReason: "当前草稿版本",
          },
        ],
        affectedPathways: [
          {
            objectType: "PATHWAY_TEMPLATE",
            objectId: "pt-1",
            displayName: "慢阻肺抗凝路径",
            impactReason: "路径模板节点引用规则 RULE.ANTICOAG",
          },
        ],
        inPathPatients: [
          {
            objectType: "PATIENT_PATHWAY",
            objectId: "ppath-active",
            displayName: "患者 patient-1 / 就诊 enc-1",
            impactReason: "当前节点 ASSESS",
          },
        ],
        integrationAdapters: [
          {
            objectType: "INTEGRATION_ADAPTER",
            objectId: "target-clinical",
            displayName: "院内规则库",
            impactReason: "配置包 pkg-1 同步状态 SUCCESS",
          },
        ],
        unavailableScopes: [],
        traceId: "trace-impact",
      };
      apiMocks.publishRule.mockResolvedValue({
        ruleId: "rule-1",
        versionId: "ver-1",
        status: "PUBLISHED",
        traceId: "trace-publish",
        results: [],
        impactDigest: "sha256:impact-abc",
        impactStatus: "COMPLETE",
      });

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /7 步流发布/ }));

      expect(screen.getByText("选模板/导入")).toBeInTheDocument();
      expect(screen.getByText("自动校验")).toBeInTheDocument();
      expect(screen.getByText("看影响")).toBeInTheDocument();
      expect(screen.getByText("sha256:impact-abc")).toBeInTheDocument();
      expect(screen.getByText("已完成真实影响分析")).toBeInTheDocument();
      expect(screen.getByText(/慢阻肺抗凝路径/)).toBeInTheDocument();
      expect(screen.getByText(/患者 patient-1/)).toBeInTheDocument();
      expect(screen.getByText(/院内规则库/)).toBeInTheDocument();

      await user.type(screen.getByLabelText("发布审核说明"), "已查看影响摘要并确认灰度发布");
      await user.click(screen.getByRole("button", { name: "提交审核并进入灰度发布" }));

      await waitFor(() =>
        expect(apiMocks.publishRule).toHaveBeenCalledWith({
          ruleId: "rule-1",
          packageVersion: "pkg-2026.06",
          impactDigest: "sha256:impact-abc",
          reason: "已查看影响摘要并确认灰度发布",
        }),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "内容已审核的规则显示统一部署状态，并可确认全量激活",
    async () => {
      const publishedRule = {
        ...draftRule,
        status: "PUBLISHED" as const,
      };
      apiMocks.ruleListData = { items: [publishedRule], total: 1 };
      apiMocks.ruleDetailData = {
        ...createRuleDetail(),
        definition: publishedRule,
        deploymentStatus: "PUBLISHED",
      };
      apiMocks.impactData = {
        ruleId: "rule-1",
        versionId: "ver-1",
        riskLevel: "HIGH",
        analysisStatus: "COMPLETE",
        impactDigest: "sha256:impact-full",
        affectedRules: [],
        affectedPathways: [],
        inPathPatients: [],
        integrationAdapters: [],
        unavailableScopes: [],
        traceId: "trace-impact",
      };
      apiMocks.fullRolloutRule.mockResolvedValue({
        ruleId: "rule-1",
        versionId: "ver-1",
        status: "PUBLISHED",
        traceId: "trace-full",
        results: [],
        releaseEvidence: ["FULL 全量激活"],
      });

      const user = await openDraftRuleDrawer();
      expect(screen.getAllByText("内容已审核").length).toBeGreaterThan(0);
      expect(screen.getByText("待全量激活")).toBeInTheDocument();
      await user.click(screen.getByRole("tab", { name: /7 步流发布/ }));
      await user.type(screen.getByLabelText("发布审核说明"), "院级管理员确认全量激活");
      await user.click(screen.getByRole("button", { name: /院级确认全量激活/ }));

      await waitFor(() =>
        expect(apiMocks.fullRolloutRule).toHaveBeenCalledWith({
          ruleId: "rule-1",
          packageVersion: "pkg-2026.06",
          impactDigest: "sha256:impact-full",
          reason: "院级管理员确认全量激活",
        }),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "统一版本已激活时只展示生效结果，不再提供重复发布动作",
    async () => {
      const publishedRule = {
        ...draftRule,
        status: "PUBLISHED" as const,
      };
      apiMocks.ruleListData = { items: [publishedRule], total: 1 };
      apiMocks.ruleDetailData = {
        ...createRuleDetail(),
        definition: publishedRule,
        deploymentStatus: "ACTIVE",
      };

      const user = await openDraftRuleDrawer();
      expect(screen.getByText("运行中")).toBeInTheDocument();
      await user.click(screen.getByRole("tab", { name: /7 步流发布/ }));
      expect(screen.getByText("当前版本已全量生效")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "院级确认全量激活" })).not.toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "L2 支持新增子条件组实现任意层级嵌套，并同步为嵌套 DSL",
    async () => {
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));

      // 新增子条件组（初始仅根组，存在唯一「新增子条件组」按钮）
      await user.click(within(dialog).getByRole("button", { name: "新增子条件组" }));

      // 进入专家模式并同步，断言 DSL 为嵌套结构（顶层 all 内含子组）
      await user.click(within(dialog).getByRole("switch", { name: "专家模式" }));
      await user.click(within(dialog).getByRole("button", { name: "同步到 DSL" }));
      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("规则 DSL JSON") as HTMLTextAreaElement;
      const parsed = JSON.parse(dslEditor.value) as { when: { all: unknown[] } };
      expect(Array.isArray(parsed.when.all)).toBe(true);
      // 顶层数组里存在一个本身带 all/any 的子组（即嵌套）
      const hasNestedGroup = parsed.when.all.some(
        (node) =>
          typeof node === "object" &&
          node !== null &&
          ("all" in (node as object) || "any" in (node as object)),
      );
      expect(hasNestedGroup).toBe(true);
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "普通模式同步到 DSL 后仍停留在 L2 且不强制显示专家页",
    async () => {
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      await user.click(within(dialog).getByRole("button", { name: "同步到 DSL" }));

      expect(within(dialog).queryByRole("tab", { name: /L3 DSL/ })).not.toBeInTheDocument();
      expect(within(dialog).getByRole("tabpanel")).toHaveTextContent("条件根组");
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "嵌套子条件组仍有未解析字段时阻断创建草稿",
    async () => {
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });

      fireEvent.change(within(dialog).getByLabelText("规则唯一业务编码"), {
        target: { value: "RULE.NESTED.PLACEHOLDER" },
      });
      fireEvent.change(within(dialog).getByLabelText("规则显示名称"), {
        target: { value: "嵌套占位字段拦截" },
      });
      fireEvent.change(within(dialog).getByLabelText("医学依据/来源"), {
        target: { value: "院内已审核制度" },
      });
      fireEvent.change(within(dialog).getByLabelText("标准上下文包版本"), {
        target: { value: "pkg-2026.06" },
      });
      fireEvent.change(within(dialog).getByLabelText("初始化变更内容说明"), {
        target: { value: "验证嵌套占位字段" },
      });
      await user.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      fireEvent.change(within(dialog).getAllByLabelText("上下文字段路径")[0], {
        target: { value: "observations.0.value" },
      });
      await user.click(within(dialog).getByRole("button", { name: "新增子条件组" }));
      await user.click(within(dialog).getByRole("button", { name: "创建草稿" }));

      expect(
        await screen.findByText("请在 L2 条件树填写真实上下文字段路径，不能提交模板占位符。"),
      ).toBeInTheDocument();
      expect(apiMocks.createRule).not.toHaveBeenCalled();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );
});
