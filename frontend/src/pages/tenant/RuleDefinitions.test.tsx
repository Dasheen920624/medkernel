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
        level: "REGION",
        code: "GROUP-A",
        name: "华东集团",
        status: "ACTIVE",
      },
      {
        id: "group-2",
        level: "REGION",
        code: "GROUP-B",
        name: "华南集团",
        status: "ACTIVE",
      },
      {
        id: "hospital-1",
        level: "FACILITY",
        facilityType: "HOSPITAL",
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
  createNextRuleVersion: vi.fn(),
  updateRule: vi.fn(),
  addTestCase: vi.fn(),
  runRuleTests: vi.fn(),
  simulateRule: vi.fn(),
  transitionRuleGovernance: vi.fn(),
  runRuleBacktest: vi.fn(),
  captureRuleDriftSnapshot: vi.fn(),
  previewRun: vi.fn(),
  securityData: {
    userId: "u-admin",
    username: "admin",
    roles: [
      {
        code: "platform-admin",
        displayName: "医院管理员",
        source: "DEFAULT",
        scopeLevel: "HOSPITAL",
        scopeCode: "HOSP-A",
      },
      {
        code: "engine-operator",
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
      {
        code: "runtime-release.read",
        dimension: "ACTION",
        target: "runtime-release.read",
        displayName: "读取生效版本",
        risk: "LOW",
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
  useSnapshotContextFieldCatalogDraft: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
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
  useCreateNextRuleVersion: () => ({
    mutateAsync: apiMocks.createNextRuleVersion,
    isPending: false,
  }),
  useUpdateRule: () => ({
    mutateAsync: apiMocks.updateRule,
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
  name: "规则发布校验核查",
  ruleType: "QUALITY",
  authoringMode: "VISUAL",
  riskLevel: "HIGH",
  priority: 100,
  suppressedBy: null,
  dedupeWindowSeconds: 0,
  status: "DRAFT",
  activeVersionId: "ver-1",
  createdAt: "2026-06-02T00:00:00Z",
  createdBy: "u-1",
  updatedAt: "2026-06-02T00:00:00Z",
};

function createRuleDetail(): RuleDetailResponse {
  return {
    definition: draftRule,
    deploymentStatus: "DRAFT",
    triggerBindings: [
      {
        triggerBindingId: "trigger-rule-1",
        assetType: "RULE",
        assetIdentity: "RULE.QC.REVIEW",
        versionId: "ver-1",
        triggerPoint: "result-review",
        purpose: "RULE_EXECUTION",
        requiredFieldsJson: "[]",
      },
    ],
    version: {
      id: 1,
      versionId: "ver-1",
      ruleId: "rule-1",
      versionNo: 1,
      sourceRef: "院内已审核制度",
      changeSummary: "补齐发布校验",
      dslJson: JSON.stringify({
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
    versions: [],
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
      authorId: "u-1",
      lastReason: "规则草稿已创建",
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
    runtimeReleaseId: "release-runtime-1",
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
  await screen.findByText("规则发布校验核查");
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
    apiMocks.createNextRuleVersion.mockReset();
    apiMocks.updateRule.mockReset();
    apiMocks.addTestCase.mockReset();
    apiMocks.runRuleTests.mockReset();
    apiMocks.simulateRule.mockReset();
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

  it("创建规则不绑定旧上线容器，并允许一个版本绑定多个临床触发场景", async () => {
    renderRuleDefinitions();

    fireEvent.click(screen.getByRole("button", { name: /新建规则模板/ }));

    const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
    expect(within(dialog).queryByLabelText("标准上下文" + "包版本")).not.toBeInTheDocument();
    expect(within(dialog).getByText("规则版本独立维护")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("临床触发场景")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "创建草稿" }));
    expect(
      await within(dialog).findByText("请输入稳定规则资产身份，同一服务机构内不可重复"),
    ).toBeInTheDocument();
  });

  it(
    "创建规则时提供模板、条件树与受控配置文本，并能从条件树同步到受控配置",
    async () => {
      renderRuleDefinitions();

      fireEvent.click(screen.getByRole("button", { name: /新建规则模板/ }));

      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      expect(within(dialog).getByRole("tab", { name: /L1 模板/ })).toBeInTheDocument();
      expect(within(dialog).getByRole("tab", { name: /L2 条件树/ })).toBeInTheDocument();
      expect(within(dialog).queryByRole("tab", { name: /受控配置文本/ })).not.toBeInTheDocument();
      fireEvent.click(within(dialog).getByRole("switch", { name: "受控配置文本模式" }));
      expect(within(dialog).getByRole("tab", { name: /受控配置文本/ })).toBeInTheDocument();

      fireEvent.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      expect(within(dialog).getByText("临床算子")).toBeInTheDocument();
      expect(within(dialog).queryByText("条件片段")).not.toBeInTheDocument();
      expect(within(dialog).queryByRole("button", { name: /片段库/ })).not.toBeInTheDocument();
      expect(within(dialog).getByText("可读预览")).toBeInTheDocument();
      expect(within(dialog).getByText("当 年龄 大于等于 65。")).toBeInTheDocument();
      fireEvent.change(dialog.querySelector("#rule-condition-fact") as HTMLInputElement, {
        target: { value: "observations.0.value" },
      });
      fireEvent.change(dialog.querySelector("#rule-condition-value") as HTMLInputElement, {
        target: { value: "6" },
      });
      fireEvent.click(within(dialog).getByRole("button", { name: "添加提示" }));
      const summaries = within(dialog).getAllByLabelText("提示摘要");
      fireEvent.change(summaries[1], { target: { value: "同步记录规则命中" } });
      fireEvent.click(within(dialog).getByRole("button", { name: "同步到受控配置" }));

      fireEvent.click(within(dialog).getByRole("tab", { name: /受控配置文本/ }));
      const dslEditor = within(dialog).getByLabelText("规则配置文本");
      expect((dslEditor as HTMLTextAreaElement).value).not.toContain('"trigger"');
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
        runtimeReleaseId: "release-runtime-1",
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
      fireEvent.change(within(dialog).getByLabelText("检验结果字段"), {
        target: { value: "observations[].valueNumeric" },
      });
      fireEvent.change(within(dialog).getByLabelText("危急阈值"), {
        target: { value: "6.5" },
      });

      await user.click(within(dialog).getByRole("tab", { name: /即配即试/ }));
      await user.type(within(dialog).getByLabelText("患者信息"), "P-001");
      await user.type(within(dialog).getByLabelText("就诊信息"), "E-001");
      await user.click(within(dialog).getByRole("button", { name: /读取真实快照/ }));
      await user.click(await within(dialog).findByText("第 1 个临床快照"));
      await user.click(within(dialog).getByRole("button", { name: "运行草稿试运行" }));

      await waitFor(() =>
        expect(apiMocks.previewRun).toHaveBeenCalledWith(
          expect.objectContaining({
            subject: "RULE_CONDITION",
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
      expect(within(dialog).getAllByText("试运行快照已关联").length).toBeGreaterThan(0);
      expect(within(dialog).getAllByText("机构生效版本已确认").length).toBeGreaterThan(0);
      expect(within(dialog).getByText("试运行已留痕")).toBeInTheDocument();
      expect(within(dialog).queryByText("ctx-001")).not.toBeInTheDocument();
      expect(within(dialog).queryByText("release-runtime-1")).not.toBeInTheDocument();
      expect(within(dialog).queryByText("trace-preview-run")).not.toBeInTheDocument();
      expect(within(dialog).getByText("observations[].valueNumeric")).toBeInTheDocument();
      expect(within(dialog).getByText("6 mmol/L >= 6.5")).toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "危急值原型向导带出默认动作和补充配置折叠，未展开补充项也可创建草稿",
    async () => {
      apiMocks.createRule.mockResolvedValue({ ruleId: "rule-critical" });
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });

      await user.click(within(dialog).getByLabelText("危急值回报"));
      expect(within(dialog).queryByLabelText("灰度比例")).not.toBeInTheDocument();

      fireEvent.change(within(dialog).getByLabelText("稳定规则资产身份"), {
        target: { value: "RULE.LAB.CRITICAL.K" },
      });
      fireEvent.change(within(dialog).getByLabelText("规则显示名称"), {
        target: { value: "血钾危急值回报" },
      });
      fireEvent.change(within(dialog).getByLabelText("医学依据/来源"), {
        target: { value: "检验危急值管理制度 2026" },
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
        parameterBindings?: Record<string, unknown>;
        triggers: Array<{
          trigger_point: string;
          purpose: string;
          required_fields: string[];
        }>;
        dslJson: {
          meta?: {
            parameters?: Array<{
              key: string;
              label: string;
              valueType: string;
              required: boolean;
            }>;
          };
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
      expect(payload.parameterBindings).toEqual({
        observationCode: "K",
        criticalThreshold: 6.5,
        returnMinutes: 15,
      });
      expect(payload.triggers).toEqual([
        {
          trigger_point: "result-review",
          purpose: "RULE_EXECUTION",
          required_fields: [],
        },
      ]);
      expect(payload.dslJson).not.toHaveProperty("trigger");
      expect(payload.dslJson.meta?.parameters).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            key: "observationCode",
            label: "检验项",
            valueType: "CODE",
            required: true,
          }),
          expect.objectContaining({
            key: "criticalThreshold",
            label: "危急阈值",
            valueType: "DECIMAL",
            required: true,
          }),
          expect.objectContaining({
            key: "returnMinutes",
            label: "回报时限分钟",
            valueType: "INTEGER",
            required: true,
          }),
        ]),
      );
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
      fireEvent.change(within(dialog).getByLabelText("稳定规则资产身份"), {
        target: { value: "RULE.CARDIOLOGY.HR" },
      });
      fireEvent.change(within(dialog).getByLabelText("规则显示名称"), {
        target: { value: "心率质控复核" },
      });
      fireEvent.change(within(dialog).getByLabelText("医学依据/来源"), {
        target: { value: "院内已审核心血管诊疗规范 2026" },
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
      expect(within(dialog).queryByLabelText("纳入条件配置文本")).not.toBeInTheDocument();
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

      fireEvent.click(within(dialog).getByRole("button", { name: "同步到受控配置" }));
      fireEvent.click(within(dialog).getByRole("button", { name: "创建草稿" }));
      await waitFor(() =>
        expect(apiMocks.createRule).toHaveBeenCalledWith(
          expect.objectContaining({
            ruleCode: "RULE.CARDIOLOGY.HR",
            ruleType: "QUALITY",
            priority: 100,
            suppressedBy: undefined,
            dedupeWindowSeconds: 0,
            triggers: [
              {
                trigger_point: "result-review",
                purpose: "RULE_EXECUTION",
                required_fields: [],
              },
            ],
            dslJson: expect.objectContaining({
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
      expect(apiMocks.createRule.mock.calls[0][0].dslJson).not.toHaveProperty("trigger");
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
          level: "REGION",
          status: "ACTIVE",
        }),
      );
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "规则详情用业务语言展示触发条件、动作与治理路径",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = {
        ...createRuleDetail(),
        version: {
          ...createRuleDetail().version,
          dslJson: JSON.stringify({
            applicability: {
              population: {},
              orgScope: { hospitalIds: ["HOSP-A"], deptIds: ["DEPT-A"] },
              settings: ["INPATIENT", "ED"],
              effective: { from: "2026-07-01", rolloutPercent: 25 },
            },
            when: {
              all: [
                {
                  expr: { field: "observations[].valueNumeric", select: "latest" },
                  operator: "gte",
                  value: 6.5,
                  ui: {
                    id: "condition-potassium",
                    label: "血钾最新结果",
                    valueKind: "number",
                  },
                },
              ],
            },
            then: [
              {
                actionCode: "STRONG_REMINDER",
                atSeverity: "CRITICAL",
                indicator: "critical",
                summary: "检验结果达到危急值，需立即回报并人工确认",
                detail: "命中后须在 15 分钟内完成危急值回报、确认与记录，不自动开立或修改医嘱。",
                source: { label: "检验危急值管理制度 2026" },
                suggestions: [],
                overrideReasons: ["已完成危急值回报"],
                requiresPhysicianConfirmation: true,
              },
            ],
            explain: {
              summary: "依据真实检验结果字段判断是否达到危急值",
              authoring: { layer: "L2_VISUAL_TREE", conditionCount: 1 },
            },
          }),
        },
      };

      await openDraftRuleDrawer();

      const readablePath = await screen.findByRole("region", { name: "规则可读路径" });
      expect(
        within(readablePath).getByText(
          "当血钾最新结果大于等于 6.5，规则将在检验结果审核时触发红线处置。",
        ),
      ).toBeInTheDocument();
      expect(within(readablePath).getByText("触发时点")).toBeInTheDocument();
      expect(within(readablePath).getByText("检验结果审核")).toBeInTheDocument();
      expect(within(readablePath).getByText("适用范围")).toBeInTheDocument();
      expect(within(readablePath).getByText(/住院、急诊/)).toBeInTheDocument();
      expect(within(readablePath).getByText("命中条件")).toBeInTheDocument();
      expect(within(readablePath).getByText("血钾最新结果")).toBeInTheDocument();
      expect(within(readablePath).getByText("处置动作")).toBeInTheDocument();
      expect(
        within(readablePath).getByText("检验结果达到危急值，需立即回报并人工确认"),
      ).toBeInTheDocument();
      expect(within(readablePath).getByText("治理与安全")).toBeInTheDocument();
      expect(within(readablePath).getByText(/医师确认/)).toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "规则列表、详情和治理页默认隐藏低频证据，打开证据详情后可追溯原始值",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = {
        ...createRuleDetail(),
        definition: {
          ...draftRule,
          suppressedBy: "RULE.PRIOR.HIGH",
        },
        versions: [
          { ...createRuleDetail().version, versionId: "ver-1", versionNo: 1, status: "DRAFT" },
        ],
      };
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
            displayName: "规则发布校验核查",
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
            impactReason: "机构生效版本 release-H1 同步状态 SUCCESS",
          },
        ],
        unavailableScopes: [],
        traceId: "trace-impact",
      };

      const user = userEvent.setup();
      renderRuleDefinitions();

      expect(await screen.findByText("规则资产已登记")).toBeInTheDocument();
      expect(screen.getByText("当前版本已形成")).toBeInTheDocument();
      expect(screen.queryByText("RULE.QC.REVIEW")).not.toBeInTheDocument();
      expect(screen.queryByText("ver-1")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "查看配置与试运行" }));

      expect(await screen.findByText("上级规则已关联")).toBeInTheDocument();
      expect(screen.getByText("第 1 版已形成")).toBeInTheDocument();
      expect(screen.queryByText("u-1")).not.toBeInTheDocument();
      expect(screen.queryByText("RULE.PRIOR.HIGH")).not.toBeInTheDocument();
      expect(screen.queryByText("ver-1")).not.toBeInTheDocument();
      expect(screen.queryByText("规则编码 RULE.QC.REVIEW")).not.toBeInTheDocument();

      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));

      expect(await screen.findByText("负责人已记录")).toBeInTheDocument();
      expect(screen.getByText("影响证据已记录")).toBeInTheDocument();
      expect(screen.getByText(/在径患者已关联/)).toBeInTheDocument();
      expect(screen.queryByText("sha256:impact-abc")).not.toBeInTheDocument();
      expect(screen.queryByText(/patient-1/)).not.toBeInTheDocument();
      expect(screen.queryByText(/RULE.ANTICOAG/)).not.toBeInTheDocument();

      await user.click(screen.getByRole("switch", { name: "证据详情" }));

      expect((await screen.findAllByText("RULE.QC.REVIEW")).length).toBeGreaterThan(0);
      expect(screen.getByText("u-1")).toBeInTheDocument();
      expect(screen.getByText("RULE.PRIOR.HIGH")).toBeInTheDocument();
      expect(screen.getAllByText(/ver-1/).length).toBeGreaterThan(0);
      expect(screen.getByText("sha256:impact-abc")).toBeInTheDocument();
      expect(screen.getByText(/patient-1/)).toBeInTheDocument();
      expect(screen.getByText(/RULE.ANTICOAG/)).toBeInTheDocument();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "从真实上下文快照详情运行规则仿真，不要求人工粘贴配置文本",
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
            summary: "规则发布校验核查",
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
      expect((await screen.findAllByText(/当前服务机构全部组织/)).length).toBeGreaterThan(0);
      await user.click(screen.getByRole("tab", { name: /真实快照试运行/ }));
      expect(
        screen.getByText("条件树是主视图；证据详情打开后可追溯受控配置和解释模板。"),
      ).toBeInTheDocument();
      await user.click(screen.getByRole("switch", { name: "证据详情" }));

      expect(screen.queryByText("手工配置文本兜底")).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "运行手工配置文本试运行" }),
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText("患者信息"), "P-001");
      await user.type(screen.getByLabelText("就诊信息"), "E-001");
      await user.click(screen.getByRole("button", { name: "读取真实快照" }));
      await user.click(await screen.findByText("ctx-001"));
      await user.click(screen.getByRole("button", { name: "使用该快照试运行" }));

      await waitFor(() =>
        expect(apiMocks.simulateRule).toHaveBeenCalledWith({
          triggerPoint: "result-review",
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
    "规则详情抽屉中的验证用例弹层高于抽屉，且只允许选择已生效快照",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = createRuleDetail();

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /发布验证用例/ }));
      await user.click(screen.getByRole("button", { name: /新增验证用例/ }));

      const title = await screen.findByText("新增验证用例", { selector: ".ant-modal-title" });
      const dialog = title.closest(".ant-modal-content");
      expect(dialog).not.toBeNull();
      expect(dialog?.closest(".ant-modal-wrap")).toHaveStyle({ zIndex: "1100" });
      expect(within(dialog as HTMLElement).getByLabelText("验证用例患者信息")).toBeInTheDocument();
      expect(within(dialog as HTMLElement).getByLabelText("验证用例就诊信息")).toBeInTheDocument();
      expect(within(dialog as HTMLElement).queryByText(/测试输入配置文本/)).not.toBeInTheDocument();
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
      await user.click(screen.getByRole("tab", { name: /发布验证用例/ }));
      await user.click(screen.getByRole("button", { name: /新增验证用例/ }));

      const title = await screen.findByText("新增验证用例", { selector: ".ant-modal-title" });
      const dialog = title.closest(".ant-modal-content");
      expect(dialog).not.toBeNull();
      const caseDialog = within(dialog as HTMLElement);
      await user.type(caseDialog.getByLabelText("验证用例患者信息"), "P-001");
      await user.click(caseDialog.getByRole("button", { name: "读取已生效快照" }));
      await user.click(await caseDialog.findByText("第 1 个临床快照"));
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
      await user.click(screen.getByRole("tab", { name: /发布验证用例/ }));

      expect(screen.getAllByText("待执行")).toHaveLength(4);
      expect(screen.queryByText("FAIL")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /执行全部用例/ }));

      await waitFor(() => expect(apiMocks.runRuleTests).toHaveBeenCalledWith());
      expect(apiMocks.refetchDetail).toHaveBeenCalled();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it(
    "展示七阶段治理与影响摘要，并完成安全复核",
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
            displayName: "规则发布校验核查",
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
            impactReason: "机构生效版本 release-H1 同步状态 SUCCESS",
          },
        ],
        unavailableScopes: [],
        traceId: "trace-impact",
      };
      apiMocks.transitionRuleGovernance.mockResolvedValue({
        ruleId: "rule-1",
        versionId: "ver-1",
        state: "REVIEWED",
        authorId: "u-1",
        lastReason: "负责人确认安全复核",
        testResults: [],
        releaseEvidence: ["REVIEWED 负责人确认"],
        traceId: "trace-governance",
        impactDigest: "sha256:impact-abc",
        impactStatus: "COMPLETE",
      });

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));

      expect(screen.getByText("安全复核")).toBeInTheDocument();
      expect(screen.getByText("影子运行")).toBeInTheDocument();
      expect(screen.getByText("退役")).toBeInTheDocument();
      expect(screen.getByText("影响证据已记录")).toBeInTheDocument();
      expect(screen.queryByText("sha256:impact-abc")).not.toBeInTheDocument();
      expect(screen.getByText("已完成真实影响分析")).toBeInTheDocument();
      expect(screen.getByText(/慢阻肺抗凝路径/)).toBeInTheDocument();
      expect(screen.getByText(/在径患者已关联/)).toBeInTheDocument();
      expect(screen.queryByText(/患者 patient-1/)).not.toBeInTheDocument();
      expect(screen.getByText(/院内规则库/)).toBeInTheDocument();

      await user.type(screen.getByLabelText("治理说明"), "已查看影响摘要并确认安全复核");
      await user.click(screen.getByRole("button", { name: "确认安全复核" }));

      await waitFor(() =>
        expect(apiMocks.transitionRuleGovernance).toHaveBeenCalledWith({
          ruleId: "rule-1",
          targetState: "REVIEWED",
          impactDigest: "sha256:impact-abc",
          reason: "已查看影响摘要并确认安全复核",
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
        deploymentStatus: "APPROVED",
        governance: {
          ...createRuleDetail().governance,
          state: "CANARY",
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
        authorId: "u-1",
        lastReason: "院级管理员确认全量激活",
        testResults: [],
        traceId: "trace-full",
        releaseEvidence: ["FULL 全量激活"],
      });

      const user = await openDraftRuleDrawer();
      expect(screen.getAllByText("已发布").length).toBeGreaterThan(0);
      expect(screen.getByText("已验证待激活")).toBeInTheDocument();
      await user.click(screen.getByRole("tab", { name: /治理与发布/ }));
      await user.type(screen.getByLabelText("治理说明"), "院级管理员确认全量激活");
      await user.click(screen.getByRole("button", { name: /院级全量激活/ }));

      await waitFor(() =>
        expect(apiMocks.transitionRuleGovernance).toHaveBeenCalledWith({
          ruleId: "rule-1",
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
        deploymentStatus: "PUBLISHED",
        governance: {
          ...createRuleDetail().governance,
          state: "MONITOR",
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
    "已全量运行规则可复制为同编码下一版草稿且已生效版本继续运行",
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
          state: "MONITOR",
        },
      };
      apiMocks.createNextRuleVersion.mockResolvedValue({
        ruleId: "rule-1",
        versionId: "ver-2",
        versionNo: 2,
        status: "DRAFT",
      });

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("button", { name: /复制为新版本/ }));

      await waitFor(() =>
        expect(apiMocks.createNextRuleVersion).toHaveBeenCalledWith({
          ruleId: "rule-1",
        }),
      );
      expect(await screen.findByText("已复制为 V2 草稿，已生效版本继续运行")).toBeInTheDocument();
      expect(apiMocks.refetchDetail).toHaveBeenCalled();
      expect(apiMocks.refetchList).toHaveBeenCalled();
    },
    RULE_DEFINITION_INTERACTION_TIMEOUT_MS,
  );

  it("规则详情展示同一稳定编码的版本历史", async () => {
    const current = createRuleDetail();
    apiMocks.ruleListData = { items: [draftRule], total: 1 };
    apiMocks.ruleDetailData = {
      ...current,
      version: { ...current.version, versionId: "ver-2", versionNo: 2 },
      versions: [
        { ...current.version, versionId: "ver-2", versionNo: 2, status: "DRAFT" },
        { ...current.version, versionId: "ver-1", versionNo: 1, status: "PUBLISHED" },
      ],
    };

    await openDraftRuleDrawer();

    expect(screen.getByText("第 2 版已形成 · 草稿设计中")).toBeInTheDocument();
    expect(screen.getByText("第 1 版已形成 · 已发布")).toBeInTheDocument();
  });

  it(
    "复制出的下一版草稿可在三层编辑器修改并保存",
    async () => {
      const publishedRule = {
        ...draftRule,
        status: "PUBLISHED" as const,
        activeVersionId: "ver-2",
      };
      apiMocks.ruleListData = { items: [publishedRule], total: 1 };
      apiMocks.ruleDetailData = {
        ...createRuleDetail(),
        definition: publishedRule,
        version: {
          ...createRuleDetail().version,
          versionId: "ver-2",
          versionNo: 2,
          sourceRef: "院内已审核制度 V1",
          changeSummary: "复制 V1 创建 V2",
        },
        deploymentStatus: "DRAFT",
        governance: {
          ...createRuleDetail().governance,
          versionId: "ver-2",
          state: "DRAFT",
        },
      };
      apiMocks.updateRule.mockResolvedValue(apiMocks.ruleDetailData);

      const user = await openDraftRuleDrawer();
      await user.click(screen.getByRole("button", { name: /编辑当前草稿/ }));

      const dialog = await screen.findByRole("dialog", { name: "编辑 V2 规则草稿" });
      expect(within(dialog).getByLabelText("稳定规则资产身份")).toBeDisabled();
      expect(within(dialog).queryByLabelText("标准上下文" + "包版本")).not.toBeInTheDocument();
      expect(within(dialog).getByLabelText("临床触发场景")).toBeInTheDocument();
      expect(within(dialog).getByLabelText("医学依据/来源")).toHaveValue("院内已审核制度 V1");

      await user.clear(within(dialog).getByLabelText("医学依据/来源"));
      await user.type(within(dialog).getByLabelText("医学依据/来源"), "院内已审核制度 V2");
      await user.clear(within(dialog).getByLabelText("本版变更内容说明"));
      await user.type(within(dialog).getByLabelText("本版变更内容说明"), "调整判断阈值");
      await user.click(within(dialog).getByRole("button", { name: "保存草稿" }));

      await waitFor(() =>
        expect(apiMocks.updateRule).toHaveBeenCalledWith(
          expect.objectContaining({
            ruleId: "rule-1",
            ruleCode: "RULE.QC.REVIEW",
            sourceRef: "院内已审核制度 V2",
            changeSummary: "调整判断阈值",
            triggers: [
              {
                trigger_point: "result-review",
                purpose: "RULE_EXECUTION",
                required_fields: [],
              },
            ],
            dslJson: expect.objectContaining({
              when: expect.any(Object),
            }),
          }),
        ),
      );
      expect(apiMocks.updateRule.mock.calls[0][0].dslJson).not.toHaveProperty("trigger");
      expect(await screen.findByText("V2 规则草稿已保存，已生效版本不受影响")).toBeInTheDocument();
      expect(apiMocks.refetchDetail).toHaveBeenCalled();
      expect(apiMocks.refetchList).toHaveBeenCalled();
    },
    RULE_DEFINITION_SUBMISSION_TIMEOUT_MS,
  );

  it(
    "只读账号仅查看治理证据且不显示不可执行动作",
    async () => {
      apiMocks.ruleListData = { items: [draftRule], total: 1 };
      apiMocks.ruleDetailData = createRuleDetail();
      apiMocks.securityData = {
        ...DEFAULT_SECURITY_DATA,
        userId: "u-doctor",
        username: "clinical-user",
        roles: [
          {
            code: "clinical-user",
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
      expect(screen.queryByRole("button", { name: "确认安全复核" })).not.toBeInTheDocument();
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
        deploymentStatus: "PUBLISHED",
        governance: {
          ...createRuleDetail().governance,
          state: "MONITOR",
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
    "L2 支持新增子条件组实现任意层级嵌套，并同步为嵌套受控配置",
    async () => {
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      expect(within(dialog).getByText("条件根组 · 第 1 层")).toBeInTheDocument();
      expect(within(dialog).getAllByText("具体条件").length).toBeGreaterThan(0);

      // 新增子条件组（初始仅根组，存在唯一「新增子条件组」按钮）
      await user.click(within(dialog).getByRole("button", { name: "新增子条件组" }));
      expect(within(dialog).getByText("子条件组 · 第 2 层")).toBeInTheDocument();
      expect(within(dialog).getAllByText("具体条件").length).toBeGreaterThan(1);

      // 进入 受控配置文本模式并同步，断言配置文本为嵌套结构（顶层 all 内含子组）
      await user.click(within(dialog).getByRole("switch", { name: "受控配置文本模式" }));
      await user.click(within(dialog).getByRole("button", { name: "同步到受控配置" }));
      await user.click(within(dialog).getByRole("tab", { name: /受控配置文本/ }));
      const dslEditor = within(dialog).getByLabelText("规则配置文本") as HTMLTextAreaElement;
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
    "普通模式同步到受控配置后仍停留在条件树且不强制显示受控配置页",
    async () => {
      const user = userEvent.setup();
      renderRuleDefinitions();

      await user.click(screen.getByRole("button", { name: /新建规则模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "创建新临床规则" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 条件树/ }));
      await user.click(within(dialog).getByRole("button", { name: "同步到受控配置" }));

      expect(within(dialog).queryByRole("tab", { name: /受控配置文本/ })).not.toBeInTheDocument();
      expect(within(dialog).getByRole("tabpanel")).toHaveTextContent("条件根组 · 第 1 层");
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

      fireEvent.change(within(dialog).getByLabelText("稳定规则资产身份"), {
        target: { value: "RULE.NESTED.PLACEHOLDER" },
      });
      fireEvent.change(within(dialog).getByLabelText("规则显示名称"), {
        target: { value: "嵌套占位字段拦截" },
      });
      fireEvent.change(within(dialog).getByLabelText("医学依据/来源"), {
        target: { value: "院内已审核制度" },
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
