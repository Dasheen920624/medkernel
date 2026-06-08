import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import RuleDefinitions from "./RuleDefinitions";
import type { RuleDefinition, RuleDetailResponse, SecurityProfile } from "@/shared/api/hooks";

const apiMocks = vi.hoisted(() => ({
  ruleListData: { items: [], total: 0 } as unknown,
  ruleDetailData: null as unknown,
  snapshotsData: { items: [], total: 0 } as unknown,
  snapshotDetailData: null as unknown,
  impactData: null as unknown,
  shadowStatsData: null as unknown,
  backtestData: null as unknown,
  driftData: null as unknown,
  authoringPreviewData: {
    previewText: "当 年龄 大于等于 65。",
    lines: ["当 年龄 大于等于 65"],
    segments: [],
    warnings: [],
    traceId: "trace-authoring-preview",
  } as unknown,
  orgUnitRequests: [] as Array<Record<string, unknown> | undefined>,
  orgUnitsData: {
    items: [
      {
        id: "group-1",
        level: "GROUP",
        code: "GROUP-A",
        name: "华东集团",
        status: "ACTIVE",
      },
      {
        id: "group-2",
        level: "GROUP",
        code: "GROUP-B",
        name: "华南集团",
        status: "ACTIVE",
      },
      {
        id: "hospital-1",
        level: "HOSPITAL",
        code: "HOSP-A",
        name: "第一医院",
        status: "ACTIVE",
      },
      {
        id: "dept-1",
        level: "DEPARTMENT",
        code: "DEPT-A",
        name: "质量管理科",
        status: "ACTIVE",
      },
    ],
    total: 4,
  } as unknown,
  refetchList: vi.fn(),
  refetchDetail: vi.fn(),
  refetchBacktest: vi.fn(),
  refetchDrift: vi.fn(),
  createRule: vi.fn(),
  addTestCase: vi.fn(),
  runRuleTests: vi.fn(),
  simulateRule: vi.fn(),
  signoffRule: vi.fn(),
  transitionRuleGovernance: vi.fn(),
  runRuleBacktest: vi.fn(),
  captureRuleDriftSnapshot: vi.fn(),
  previewRun: vi.fn(),
  securityData: {
    userId: "u-admin",
    username: "admin",
    roles: [
      {
        code: "hospital-admin",
        displayName: "医院管理员",
        source: "DEFAULT",
        scopeLevel: "HOSPITAL",
        scopeCode: "HOSP-A",
      },
      {
        code: "medical-affairs",
        displayName: "医务处",
        source: "DEFAULT",
        scopeLevel: "HOSPITAL",
        scopeCode: "HOSP-A",
      },
    ],
    permissions: [
      {
        code: "rule.read",
        dimension: "ACTION",
        target: "rule.read",
        displayName: "读取规则",
        risk: "LOW",
      },
      {
        code: "rule.write",
        dimension: "ACTION",
        target: "rule.write",
        displayName: "维护规则",
        risk: "MEDIUM",
      },
      {
        code: "rule.publish",
        dimension: "ACTION",
        target: "rule.publish",
        displayName: "发布规则",
        risk: "HIGH",
      },
    ],
    menuKeys: ["rule-definitions"],
    environmentKeys: ["production"],
    dataScope: {
      tenantId: "tenant-A",
      groupId: null,
      hospitalId: "HOSP-A",
      campusId: null,
      siteId: null,
      departmentId: null,
      specialtyId: null,
    },
    mustChangePwd: false,
    mfaRequired: false,
    mfaBound: true,
  } as SecurityProfile,
}));

const DEFAULT_SECURITY_DATA = structuredClone(apiMocks.securityData);
const RULE_DEFINITION_INTERACTION_TIMEOUT_MS = 15_000;
const RULE_DEFINITION_SUBMISSION_TIMEOUT_MS = 30_000;

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: apiMocks.securityData,
    isLoading: false,
    isError: false,
  }),
  useRuleDefinitions: () => ({
    data: apiMocks.ruleListData,
    isLoading: false,
    isError: false,
    error: null,
    refetch: apiMocks.refetchList,
  }),
  useAuthoringPreview: () => ({
    data: apiMocks.authoringPreviewData,
    isLoading: false,
    isFetching: false,
    isError: false,
    error: null,
  }),
  useAuthoringPreviewRun: () => ({
    mutateAsync: apiMocks.previewRun,
    isPending: false,
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
  useOrgUnits: (params?: Record<string, unknown>) => {
    apiMocks.orgUnitRequests.push(params);
    return { data: apiMocks.orgUnitsData, isLoading: false, isError: false };
  },
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
  useRuleShadowStats: () => ({
    data: apiMocks.shadowStatsData,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useRuleBacktestLatest: () => ({
    data: apiMocks.backtestData,
    isLoading: false,
    isError: false,
    refetch: apiMocks.refetchBacktest,
  }),
  useRuleDriftLatest: () => ({
    data: apiMocks.driftData,
    isLoading: false,
    isError: false,
    refetch: apiMocks.refetchDrift,
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
  useSignoffRule: () => ({
    mutateAsync: apiMocks.signoffRule,
    isPending: false,
  }),
  useTransitionRuleGovernance: () => ({
    mutateAsync: apiMocks.transitionRuleGovernance,
    isPending: false,
  }),
  useRunRuleBacktest: () => ({
    mutateAsync: apiMocks.runRuleBacktest,
    isPending: false,
  }),
  useCaptureRuleDriftSnapshot: () => ({
    mutateAsync: apiMocks.captureRuleDriftSnapshot,
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
  priority: 100,
  suppressedBy: null,
  dedupeWindowSeconds: 0,
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
        applicability: {
          population: {},
          orgScope: {},
          settings: ["INPATIENT"],
          effective: { rolloutPercent: 100 },
        },
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
    governance: {
      ruleId: "rule-1",
      versionId: "ver-1",
      state: "DRAFT",
      requiredSignoffs: 2,
      reviewRound: 1,
      committeeApprovalCount: 0,
      authorId: "u-1",
      lastReason: "规则草稿已创建",
      signoffs: [],
      testResults: [],
      releaseEvidence: [],
      traceId: "trace-governance",
    },
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
      encounters: [{ encounterId: "E-001", encounterType: "INPATIENT" }],
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
    apiMocks.shadowStatsData = null;
    apiMocks.backtestData = null;
    apiMocks.driftData = null;
    apiMocks.securityData = structuredClone(DEFAULT_SECURITY_DATA);
    apiMocks.orgUnitRequests = [];
    apiMocks.refetchList.mockReset();
    apiMocks.refetchDetail.mockReset();
    apiMocks.refetchBacktest.mockReset();
    apiMocks.refetchDrift.mockReset();
    apiMocks.createRule.mockReset();
    apiMocks.addTestCase.mockReset();
    apiMocks.runRuleTests.mockReset();
    apiMocks.simulateRule.mockReset();
    apiMocks.signoffRule.mockReset();
    apiMocks.transitionRuleGovernance.mockReset();
    apiMocks.runRuleBacktest.mockReset();
    apiMocks.captureRuleDriftSnapshot.mockReset();
    apiMocks.previewRun.mockReset();
  });

  it("创建规则弹窗宽度受窄屏视口约束", async () => {
    renderRuleDefinitions();

    fireEvent.click(screen.getByRole("button", { name: /新建规则模板/ }));

    const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
    expect(dialog.closest(".ant-modal")).toHaveStyle({
      width: "min(920px, calc(100vw - 32px))",
    });
  });

  it(
    "创建规则时提供 L1 模板、L2 条件树与 L3 DSL，并能从 L2 同步到 L3",
    async () => {
      renderRuleDefinitions();

      fireEvent.click(screen.getByRole("button", { name: /新建规则模板/ }));

      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      expect(within(dialog).getByRole("tab", { name: /L1 模板/ })).toBeInTheDocument();
      expect(within(dialog).getByRole("tab", { name: /L2 条件树/ })).toBeInTheDocument();
      expect(within(dialog).queryByRole("tab", { name: /L3 DSL/ })).not.toBeInTheDocument();
      fireEvent.change(within(dialog).getByLabelText("标准上下文包版本"), {
        target: { value: "pkg-2026.06" },
      });
      fireEvent.click(within(dialog).getByRole("switch", { name: "专家模式" }));
      expect(within(dialog).getByRole("tab", { name: /L3 DSL/ })).toBeInTheDocument();

      fireEvent.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      expect(within(dialog).getByText("临床算子")).toBeInTheDocument();
      expect(within(dialog).getByText("可读预览")).toBeInTheDocument();
      expect(within(dialog).getByText("当 年龄 大于等于 65。")).toBeInTheDocument();
      fireEvent.change(dialog.querySelector("#rule-condition-fact") as HTMLInputElement, {
        target: { value: "observations.0.value" },
      });
      fireEvent.change(dialog.querySelector("#rule-condition-value") as HTMLInputElement, {
        target: { value: "6" },
      });
      fireEvent.click(within(dialog).getByRole("button", { name: "添加动作" }));
      const summaries = within(dialog).getAllByLabelText("卡片摘要");
      fireEvent.change(summaries[1], { target: { value: "同步记录规则命中" } });
      fireEvent.click(within(dialog).getByRole("button", { name: "同步到 DSL" }));

      fireEvent.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("规则 DSL JSON");
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"trigger": "result-review"');
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"fact": "observations.0.value"');
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"value": 6');
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"summary": "同步记录规则命中"');
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "创建规则草稿时可选择真实快照就地试运行并返回证据",
    async () => {
      setActiveSnapshotFixture();
      apiMocks.previewRun.mockResolvedValue({
        subject: "RULE_CONDITION",
        snapshotId: "ctx-001",
        packageVersion: "pkg-2026.06",
        matched: true,
        hit: true,
        outcomeText: "草稿规则命中真实快照",
        severity: "CRITICAL",
        actions: [],
        explanation: {
          conditionEvidence: [
            {
              fact: "observations[].valueNumeric",
              operator: "gte",
              matched: true,
              missing: false,
              formula: "6 mmol/L >= 6.5",
            },
          ],
        },
        conditionEvidence: [
          {
            fact: "observations[].valueNumeric",
            operator: "gte",
            matched: true,
            missing: false,
            formula: "6 mmol/L >= 6.5",
          },
        ],
        contextQualityStatus: "COMPLETE",
        missingFields: [],
        mappingStatus: {},
        contextResourceCounts: { observations: 1 },
        traceId: "trace-preview-run",
      });
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });

      await user.click(within(dialog).getByLabelText("危急值回报"));
      fireEvent.change(within(dialog).getByLabelText("标准上下文包版本"), {
        target: { value: "pkg-2026.06" },
      });
      fireEvent.change(within(dialog).getByLabelText("检验结果字段"), {
        target: { value: "observations[].valueNumeric" },
      });
      fireEvent.change(within(dialog).getByLabelText("危急阈值"), {
        target: { value: "6.5" },
      });

      await user.click(within(dialog).getByRole("tab", { name: /即配即试/ }));
      await user.type(within(dialog).getByLabelText("患者 ID"), "P-001");
      await user.type(within(dialog).getByLabelText("就诊 ID"), "E-001");
      await user.click(within(dialog).getByRole("button", { name: /读取真实快照/ }));
      await user.click(await within(dialog).findByText("ctx-001"));
      await user.click(within(dialog).getByRole("button", { name: "运行草稿试运行" }));

      await waitFor(() =>
        expect(apiMocks.previewRun).toHaveBeenCalledWith(
          expect.objectContaining({
            subject: "RULE_CONDITION",
            packageVersion: "pkg-2026.06",
            snapshotId: "ctx-001",
            dsl: expect.objectContaining({
              when: expect.objectContaining({
                all: expect.arrayContaining([
                  expect.objectContaining({
                    expr: expect.objectContaining({
                      field: "observations[].valueNumeric",
                      select: "latest",
                    }),
                    operator: "gte",
                    value: 6.5,
                  }),
                ]),
              }),
            }),
          }),
        ),
      );
      expect(apiMocks.simulateRule).not.toHaveBeenCalled();
      expect(await within(dialog).findByText("草稿规则命中真实快照")).toBeInTheDocument();
      expect(within(dialog).getByText("observations[].valueNumeric")).toBeInTheDocument();
      expect(within(dialog).getByText("6 mmol/L >= 6.5")).toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "危急值原型向导带出默认动作和高级折叠，未展开高级项也可创建草稿",
    async () => {
      apiMocks.createRule.mockResolvedValue({ ruleId: "rule-critical" });
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });

      await user.click(within(dialog).getByLabelText("危急值回报"));
      expect(within(dialog).queryByLabelText("灰度比例")).not.toBeInTheDocument();

      fireEvent.change(within(dialog).getByLabelText("规则唯一业务编码"), {
        target: { value: "RULE.LAB.CRITICAL.K" },
      });
      fireEvent.change(within(dialog).getByLabelText("规则显示名称"), {
        target: { value: "血钾危急值回报" },
      });
      fireEvent.change(within(dialog).getByLabelText("医学依据/来源"), {
        target: { value: "检验危急值管理制度 2026" },
      });
      fireEvent.change(within(dialog).getByLabelText("标准上下文包版本"), {
        target: { value: "pkg-2026.06" },
      });
      fireEvent.change(within(dialog).getByLabelText("检验结果字段"), {
        target: { value: "observations[].valueNumeric" },
      });
      fireEvent.change(within(dialog).getByLabelText("危急阈值"), {
        target: { value: "6.5" },
      });
      fireEvent.change(within(dialog).getByLabelText("回报时限分钟"), {
        target: { value: "15" },
      });

      await user.click(within(dialog).getByRole("button", { name: "创建草稿" }));

      await waitFor(() => expect(apiMocks.createRule).toHaveBeenCalled());
      const payload = apiMocks.createRule.mock.calls[0][0] as {
        riskLevel: string;
        dslJson: {
          trigger: string;
          when: {
            all?: Array<{
              expr?: { field?: string; select?: string };
              operator?: string;
              value?: unknown;
            }>;
          };
          then: Array<{ actionCode: string; atSeverity: string; detail: string }>;
        };
      };
      expect(payload.riskLevel).toBe("CRITICAL");
      expect(payload.dslJson.trigger).toBe("result-review");
      expect(payload.dslJson.when.all).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            expr: expect.objectContaining({
              field: "observations[].valueNumeric",
              select: "latest",
            }),
            operator: "gte",
            value: 6.5,
          }),
        ]),
      );
      expect(payload.dslJson.then).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            actionCode: "STRONG_REMINDER",
            atSeverity: "CRITICAL",
            detail: expect.stringContaining("15 分钟"),
          }),
        ]),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "创建规则草稿时提交完整适用域",
    async () => {
      apiMocks.createRule.mockResolvedValue({ ruleId: "rule-new" });
      renderRuleDefinitions();

      fireEvent.click(screen.getByRole("button", { name: /新建规则模板/ }));

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
      fireEvent.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      fireEvent.click(within(dialog).getByText("适用域与生效"));
      fireEvent.change(within(dialog).getByLabelText("生效日期"), {
        target: { value: "2026-07-01" },
      });
      fireEvent.change(within(dialog).getByLabelText("失效日期"), {
        target: { value: "2026-12-31" },
      });
      fireEvent.change(within(dialog).getByLabelText("灰度比例"), {
        target: { value: "25" },
      });
      fireEvent.mouseDown(within(dialog).getByRole("combobox", { name: "集团范围" }));
      fireEvent.click(
        await screen.findByText("华东集团 · 集团 · GROUP-A", {
          selector: ".ant-select-item-option-content",
        }),
      );
      expect(within(dialog).queryByLabelText("纳入条件 JSON")).not.toBeInTheDocument();
      fireEvent.click(within(dialog).getByRole("switch", { name: "启用纳入条件" }));
      const inclusionEditor = within(dialog).getByRole("region", { name: "纳入人群条件" });
      fireEvent.change(
        await within(inclusionEditor).findByRole("combobox", { name: "上下文字段路径" }),
        {
          target: { value: "patient.specialPopulations" },
        },
      );
      fireEvent.mouseDown(within(inclusionEditor).getByRole("combobox", { name: "算子" }));
      fireEvent.click(
        await screen.findByText("包含", { selector: ".ant-select-item-option-content" }),
      );
      fireEvent.change(within(inclusionEditor).getByLabelText("比较值"), {
        target: { value: "ELDERLY" },
      });
      fireEvent.change(dialog.querySelector("#rule-condition-fact") as HTMLInputElement, {
        target: { value: "observations.0.value" },
      });
      fireEvent.change(dialog.querySelector("#rule-condition-value") as HTMLInputElement, {
        target: { value: "6" },
      });

      fireEvent.click(within(dialog).getByRole("button", { name: "同步到 DSL" }));
      fireEvent.click(within(dialog).getByRole("button", { name: "创建草稿" }));
      await waitFor(() =>
        expect(apiMocks.createRule).toHaveBeenCalledWith(
          expect.objectContaining({
            ruleCode: "RULE.CARDIOLOGY.HR",
            ruleType: "QUALITY",
            priority: 100,
            suppressedBy: undefined,
            dedupeWindowSeconds: 0,
            packageVersion: "1.0.0",
            dslJson: expect.objectContaining({
              trigger: "result-review",
              applicability: {
                population: {
                  include: {
                    all: [
                      expect.objectContaining({
                        fact: "patient.specialPopulations",
                        operator: "contains",
                        value: "ELDERLY",
                      }),
                    ],
                  },
                },
                orgScope: {
                  groupIds: ["group-1"],
                },
                settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
                effective: {
                  from: "2026-07-01",
                  to: "2026-12-31",
                  rolloutPercent: 25,
                },
              },
              then: expect.arrayContaining([expect.objectContaining({ actionCode: "REMIND" })]),
            }),
          }),
        ),
      );
    },
    RULE_DEFINITION_SUBMISSION_TIMEOUT_MS,
  );

  it(
    "组织范围多选始终保留首个可读名称，并汇总其余选择",
    async () => {
      const user = userEvent.setup();
      renderRuleDefinitions();

      fireEvent.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      fireEvent.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      fireEvent.click(within(dialog).getByText("适用域与生效"));

      const groupCombobox = within(dialog).getByRole("combobox", { name: "集团范围" });
      fireEvent.mouseDown(groupCombobox);
      await user.click(
        await screen.findByText("华东集团 · 集团 · GROUP-A", {
          selector: ".ant-select-item-option-content",
        }),
      );
      await user.click(
        await screen.findByText("华南集团 · 集团 · GROUP-B", {
          selector: ".ant-select-item-option-content",
        }),
      );

      const groupSelect = groupCombobox.closest(".ant-select");
      expect(groupSelect).toHaveTextContent("华东集团 · 集团 · GROUP-A");
      expect(groupSelect).toHaveTextContent("+ 1 ...");
      expect(groupSelect).not.toHaveTextContent("+ 2 ...");
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "组织范围按层级和关键词从服务端检索",
    async () => {
      renderRuleDefinitions();

      fireEvent.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      fireEvent.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      fireEvent.click(within(dialog).getByText("适用域与生效"));

      const groupCombobox = within(dialog).getByRole("combobox", { name: "集团范围" });
      fireEvent.change(groupCombobox, { target: { value: "华北" } });

      await waitFor(() =>
        expect(apiMocks.orgUnitRequests).toContainEqual({
          page: 1,
          size: 50,
          sort: "name,asc",
          keyword: "华北",
          level: "GROUP",
          status: "ACTIVE",
        }),
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
    "展示八阶段治理与影响摘要，并只推进到同行评审",
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
      apiMocks.transitionRuleGovernance.mockResolvedValue({
        ruleId: "rule-1",
        versionId: "ver-1",
        state: "PEER_REVIEW",
        requiredSignoffs: 2,
        reviewRound: 1,
        committeeApprovalCount: 0,
        authorId: "u-1",
        lastReason: "提交同行评审",
        signoffs: [],
        testResults: [],
        releaseEvidence: ["PENDING_REVIEW 提交审核"],
        traceId: "trace-governance",
        impactDigest: "sha256:impact-abc",
        impactStatus: "COMPLETE",
      });

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));

      expect(screen.getByText("同行评审")).toBeInTheDocument();
      expect(screen.getAllByText("委员会会签").length).toBeGreaterThan(0);
      expect(screen.getByText("影子运行")).toBeInTheDocument();
      expect(screen.getByText("退役")).toBeInTheDocument();
      expect(screen.getByText("sha256:impact-abc")).toBeInTheDocument();
      expect(screen.getByText("已完成真实影响分析")).toBeInTheDocument();
      expect(screen.getByText(/慢阻肺抗凝路径/)).toBeInTheDocument();
      expect(screen.getByText(/患者 patient-1/)).toBeInTheDocument();
      expect(screen.getByText(/院内规则库/)).toBeInTheDocument();

      await user.type(screen.getByLabelText("治理说明"), "已查看影响摘要并提交同行评审");
      await user.click(screen.getByRole("button", { name: "提交同行评审" }));

      await waitFor(() =>
        expect(apiMocks.transitionRuleGovernance).toHaveBeenCalledWith({
          ruleId: "rule-1",
          packageVersion: "pkg-2026.06",
          targetState: "PEER_REVIEW",
          impactDigest: "sha256:impact-abc",
          reason: "已查看影响摘要并提交同行评审",
        }),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "灰度阶段只显示院级全量激活动作",
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
        governance: {
          ...createRuleDetail().governance,
          state: "CANARY",
          committeeApprovalCount: 2,
          lastReason: "影子验证达标，进入灰度",
        },
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
      apiMocks.transitionRuleGovernance.mockResolvedValue({
        ruleId: "rule-1",
        versionId: "ver-1",
        state: "FULL",
        requiredSignoffs: 2,
        reviewRound: 1,
        committeeApprovalCount: 2,
        authorId: "u-1",
        lastReason: "院级管理员确认全量激活",
        signoffs: [],
        testResults: [],
        traceId: "trace-full",
        releaseEvidence: ["FULL 全量激活"],
      });

      const user = await openDraftRuleDrawer();
      expect(screen.getAllByText("内容已审核").length).toBeGreaterThan(0);
      expect(screen.getByText("待全量激活")).toBeInTheDocument();
      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));
      await user.type(screen.getByLabelText("治理说明"), "院级管理员确认全量激活");
      await user.click(screen.getByRole("button", { name: /院级全量激活/ }));

      await waitFor(() =>
        expect(apiMocks.transitionRuleGovernance).toHaveBeenCalledWith({
          ruleId: "rule-1",
          packageVersion: "pkg-2026.06",
          targetState: "FULL",
          impactDigest: "sha256:impact-full",
          reason: "院级管理员确认全量激活",
        }),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "监测阶段不再提供重复发布，只允许退役封存",
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
        governance: {
          ...createRuleDetail().governance,
          state: "MONITOR",
          committeeApprovalCount: 2,
          lastReason: "全量运行进入监测",
        },
      };

      const user = await openDraftRuleDrawer();
      expect(screen.getByText("运行中")).toBeInTheDocument();
      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));
      expect(screen.getByRole("button", { name: "退役并封存" })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "院级全量激活" })).not.toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "只读账号仅查看治理证据且不显示不可执行动作",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = createRuleDetail();
      apiMocks.securityData = {
        ...DEFAULT_SECURITY_DATA,
        userId: "u-doctor",
        username: "doctor",
        roles: [
          {
            code: "doctor",
            displayName: "临床医生",
            source: "DEFAULT",
            scopeLevel: "DEPARTMENT",
            scopeCode: "DEPT-A",
          },
        ],
        permissions: [
          {
            code: "rule.read",
            dimension: "ACTION",
            target: "rule.read",
            displayName: "读取规则",
            risk: "LOW",
          },
        ],
      };
      const user = await openDraftRuleDrawer();

      await user.click(screen.getByText("治理与发布"));

      expect(screen.getByText("当前账号仅可查看本阶段证据")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: "提交同行评审" })).not.toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "影子阶段展示真实运行命中与误报统计",
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
        governance: {
          ...createRuleDetail().governance,
          state: "SHADOW",
          committeeApprovalCount: 2,
          lastReason: "进入影子运行",
        },
      };
      apiMocks.impactData = {
        ruleId: "rule-1",
        versionId: "ver-1",
        riskLevel: "HIGH",
        analysisStatus: "COMPLETE",
        impactDigest: "sha256:impact-shadow",
        affectedRules: [],
        affectedPathways: [],
        inPathPatients: [],
        integrationAdapters: [],
        unavailableScopes: [],
        traceId: "trace-impact",
      };
      apiMocks.shadowStatsData = {
        ruleId: "rule-1",
        totalExecutions: 5,
        hitCount: 3,
        missCount: 2,
        falsePositiveCount: 1,
        hitRate: 0.6,
        falsePositiveRate: 1 / 3,
        traceId: "trace-shadow-stats",
      };

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));

      expect(screen.getByText("影子运行统计")).toBeInTheDocument();
      expect(screen.getByText("执行总数")).toBeInTheDocument();
      expect(screen.getByText("命中率")).toBeInTheDocument();
      expect(screen.getByText("60.0%")).toBeInTheDocument();
      expect(screen.getByText("误报率")).toBeInTheDocument();
      expect(screen.getByText("33.3%")).toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "监测阶段展示回测与漂移证据并可触发新快照",
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
        governance: {
          ...createRuleDetail().governance,
          state: "MONITOR",
          committeeApprovalCount: 2,
          lastReason: "进入运行监测",
        },
      };
      apiMocks.backtestData = {
        backtestId: "rbt-1",
        ruleId: "rule-1",
        versionId: "ver-1",
        cohortRef: "ckd-2026-q1",
        sampleCount: 4,
        truePositiveCount: 1,
        falsePositiveCount: 1,
        trueNegativeCount: 1,
        falseNegativeCount: 1,
        sensitivity: 0.5,
        specificity: 0.5,
        accuracy: 0.5,
        fireRate: 0.5,
        falsePositiveCaseIds: ["case-CONFLICT"],
        falseNegativeCaseIds: ["case-BOUNDARY"],
        createdAt: "2026-06-07T08:00:00Z",
        traceId: "trace-backtest",
      };
      apiMocks.driftData = {
        driftId: "rds-1",
        ruleId: "rule-1",
        versionId: "ver-1",
        baselineBacktestId: "rbt-1",
        windowStart: "2026-06-01T00:00:00Z",
        windowEnd: "2026-06-07T00:00:00Z",
        sampleCount: 10,
        hitCount: 8,
        baselineFireRate: 0.5,
        currentFireRate: 0.8,
        driftDelta: 0.3,
        threshold: 0.1,
        status: "WARNING",
        createdAt: "2026-06-07T08:30:00Z",
        traceId: "trace-drift",
      };

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));

      expect(screen.getByText("历史回测与漂移监测")).toBeInTheDocument();
      expect(screen.getByText("回测样本")).toBeInTheDocument();
      expect(screen.getAllByText("50.0%").length).toBeGreaterThanOrEqual(3);
      expect(screen.getByText("case-CONFLICT")).toBeInTheDocument();
      expect(screen.getByText("case-BOUNDARY")).toBeInTheDocument();
      expect(screen.getByText("告警")).toBeInTheDocument();
      expect(screen.getByText("80.0%")).toBeInTheDocument();
      expect(screen.getByText("+30.0%")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "运行历史回测" }));
      expect(apiMocks.runRuleBacktest).toHaveBeenCalledWith({
        ruleId: "rule-1",
        cohortRef: "test-cases:ver-1",
      });

      await user.click(screen.getByRole("button", { name: "记录漂移快照" }));
      expect(apiMocks.captureRuleDriftSnapshot).toHaveBeenCalledWith(
        expect.objectContaining({
          ruleId: "rule-1",
          baselineBacktestId: "rbt-1",
          threshold: 0.1,
        }),
      );
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
