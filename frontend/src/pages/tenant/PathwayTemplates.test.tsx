import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import PathwayTemplates from "./PathwayTemplates";
import type {
  AuthoringPreviewRunResponse,
  EvaluationIndicator,
  PathwayTemplate,
  PathwayTemplateDetailResponse,
  RuleDefinition,
  SecurityProfile,
} from "@/shared/api/hooks";

const apiMocks = vi.hoisted(() => ({
  templateListData: { items: [], total: 0 } as unknown,
  templateDetailData: null as unknown,
  evaluationIndicatorsData: { items: [], total: 0 } as unknown,
  rulesData: { items: [], total: 0 } as unknown,
  snapshotsData: { items: [], total: 0 } as unknown,
  snapshotDetailData: null as unknown,
  refetchList: vi.fn(),
  refetchDetail: vi.fn(),
  createTemplate: vi.fn(),
  simulatePathway: vi.fn(),
  previewRun: vi.fn(),
  templateListParams: [] as unknown[],
  evaluationIndicatorParams: [] as unknown[],
  ruleListParams: [] as unknown[],
  snapshotQueryParams: [] as unknown[],
  contextFieldCatalogData: [] as unknown[],
  contextFieldCatalogError: false,
  authoringPreviewData: {
    previewText: "路径守卫 E1（从 ASSESS 到 FOLLOWUP）：风险等级 等于 HIGH。",
    lines: ["路径守卫 E1（从 ASSESS 到 FOLLOWUP）：风险等级 等于 HIGH。"],
    segments: [],
    warnings: [],
    traceId: "trace-pathway-preview",
  } as unknown,
  securityData: {
    userId: "u-admin",
    username: "admin",
    roles: [
      {
        code: "engine-operator",
        displayName: "医疗引擎运营员",
        source: "DEFAULT",
        scopeLevel: "HOSPITAL",
        scopeCode: "HOSP-A",
      },
    ],
    permissions: [
      {
        code: "context.write",
        dimension: "ACTION",
        target: "context.write",
        displayName: "维护字段目录",
        risk: "MEDIUM",
      },
    ],
    menuKeys: ["pathway-templates"],
    environmentKeys: ["production"],
    dataScope: {
      tenantId: "tenant-hospital",
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

const PATHWAY_INTERACTION_TIMEOUT_MS = 90_000;

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: apiMocks.securityData,
    isLoading: false,
    isError: false,
  }),
  usePathwayTemplates: (params?: unknown) => {
    apiMocks.templateListParams.push(params ?? {});
    return {
      data: apiMocks.templateListData,
      isLoading: false,
      refetch: apiMocks.refetchList,
    };
  },
  usePathwayTemplateDetail: () => ({
    data: apiMocks.templateDetailData,
    isLoading: false,
    refetch: apiMocks.refetchDetail,
  }),
  useEvaluationIndicators: (params?: unknown) => {
    apiMocks.evaluationIndicatorParams.push(params ?? {});
    return {
      data: apiMocks.evaluationIndicatorsData,
      isLoading: false,
      isError: false,
    };
  },
  useRuleDefinitions: (params?: unknown) => {
    apiMocks.ruleListParams.push(params ?? {});
    return {
      data: apiMocks.rulesData,
      isLoading: false,
      isError: false,
      error: null,
    };
  },
  useContextSnapshots: (params?: unknown) => {
    apiMocks.snapshotQueryParams.push(params ?? {});
    return {
      data: apiMocks.snapshotsData,
      isLoading: false,
      isError: false,
    };
  },
  useContextSnapshotDetail: () => ({
    data: apiMocks.snapshotDetailData,
    isLoading: false,
    isError: false,
  }),
  useCreatePathwayTemplate: () => ({
    mutateAsync: apiMocks.createTemplate,
    isPending: false,
  }),
  useSimulatePathway: () => ({
    mutateAsync: apiMocks.simulatePathway,
    isPending: false,
  }),
  useAuthoringPreviewRun: () => ({
    mutateAsync: apiMocks.previewRun,
    isPending: false,
  }),
  useAuthoringPreview: () => ({
    data: apiMocks.authoringPreviewData,
    isLoading: false,
    isFetching: false,
    isError: false,
    error: null,
  }),
  useContextFieldCatalog: () => ({
    data: apiMocks.contextFieldCatalogData,
    isLoading: false,
    isError: apiMocks.contextFieldCatalogError,
  }),
  useSnapshotContextFieldCatalogDraft: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useStandardTerms: () => ({ data: { items: [], total: 0 }, isLoading: false, isError: false }),
  useMappingCoverage: () => ({ data: [], isLoading: false, isError: false }),
  useCreateContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

function renderPathwayTemplates() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <PathwayTemplates />
      </AntdApp>
    </ConfigProvider>,
  );
}

const draftTemplate: PathwayTemplate = {
  id: 1,
  templateId: "pt-path-1",
  templateCode: "PATH.CARDIO.REVIEW",
  name: "心血管路径复核",
  diseaseCode: "CARDIO",
  templateVersion: 1,
  templateLevel: "STANDARD",
  status: "DRAFT",
  entryMode: "AUTO_SUGGEST",
  startNodeCode: "ASSESS",
  sourceRef: "院内已审核路径制度",
  description: "路径复核配置",
  entryCriteriaJson: JSON.stringify({
    include: { all: [{ fact: "patient.mpi", operator: "exists" }] },
  }),
  exitCriteriaJson: JSON.stringify({
    include: { all: [{ fact: "patient.dischargeReady", operator: "equals", value: true }] },
  }),
};

const publishedTemplate: PathwayTemplate = {
  ...draftTemplate,
  templateId: "pt-path-published",
  templateVersion: 2,
  status: "PUBLISHED",
};

const losOutcomeIndicator: EvaluationIndicator = {
  indicatorId: "indicator-los",
  tenantId: "tenant-hospital",
  indicatorCode: "PATH.OUTCOME.LOS",
  versionNo: 1,
  name: "平均住院日",
  subjectType: "PATIENT",
  timeWindow: "出院后 30 天",
  organizationScope: "院级",
  responsibleDepartmentId: "quality-office",
  sourceRef: "院内路径结局指标制度 2026",
  status: "ACTIVE",
};

const publishedRule: RuleDefinition = {
  id: 10,
  ruleId: "rule-asset-hypotension",
  tenantId: "tenant-hospital",
  ruleCode: "RULE.PATH.HYPOTENSION",
  name: "低血压路径分流",
  ruleType: "PATHWAY",
  authoringMode: "DSL",
  riskLevel: "MEDIUM",
  priority: 10,
  activeVersionId: "rule-version-hypotension-v2",
  dedupeWindowSeconds: 300,
  status: "PUBLISHED",
  createdAt: "2026-06-01T00:00:00Z",
  createdBy: "tester",
  updatedAt: "2026-06-01T00:00:00Z",
};

function createTemplateDetail(
  template: PathwayTemplate = draftTemplate,
  deploymentStatus: PathwayTemplateDetailResponse["deploymentStatus"] = "DRAFT",
): PathwayTemplateDetailResponse {
  return {
    template,
    nextVersionNo: template.templateVersion + 1,
    deploymentStatus,
    milestones: [
      {
        id: 1,
        milestoneId: "milestone-preop",
        templateId: template.templateId,
        phaseCode: "PREOP",
        phaseName: "术前",
        milestoneCode: "M-PREOP-ASSESS",
        name: "入径评估",
        dayOffset: 0,
        expectedOffsetMinutes: 60,
        sortOrder: 1,
      },
    ],
    nodes: [
      {
        id: 1,
        nodeId: "node-assess",
        templateId: template.templateId,
        nodeCode: "ASSESS",
        name: "入径评估",
        nodeType: "ASSESSMENT",
        milestoneCode: "M-PREOP-ASSESS",
        sortOrder: 1,
        responsibleRole: "责任医生",
        accountableRole: "科主任",
        consultedRolesJson: '["护理组"]',
        informedRolesJson: '["质控办"]',
        timeWindowMinutes: 60,
        terminalFlag: false,
      },
      {
        id: 2,
        nodeId: "node-followup",
        templateId: template.templateId,
        nodeCode: "FOLLOWUP",
        name: "出径随访",
        nodeType: "FOLLOWUP",
        sortOrder: 2,
        responsibleRole: "随访护士",
        accountableRole: "护理组长",
        consultedRolesJson: "[]",
        informedRolesJson: '["责任医生"]',
        timeWindowMinutes: 0,
        terminalFlag: true,
      },
    ],
    edges: [
      {
        id: 1,
        edgeId: "edge-followup",
        templateId: template.templateId,
        edgeCode: "EDGE.ASSESS.FOLLOWUP",
        fromNodeCode: "ASSESS",
        toNodeCode: "FOLLOWUP",
        edgeType: "CONDITION",
        conditionJson: JSON.stringify({
          fact: "observation.HB.value",
          operator: "gte",
          value: 90,
        }),
        priority: 1,
      },
    ],
    metricBindings: [
      {
        id: 1,
        bindingId: "bind-assess",
        templateId: template.templateId,
        nodeCode: "ASSESS",
        metricCode: "PATH.TIME.ASSESS",
      },
    ],
    outcomeBindings: [
      {
        id: 1,
        bindingId: "outcome-los",
        templateId: template.templateId,
        scope: "TEMPLATE",
        refCode: template.templateCode,
        indicatorCode: "PATH.OUTCOME.LOS",
      },
    ],
    traceId: "trace-path-detail",
  };
}

async function openCreateDialog() {
  const user = userEvent.setup();
  renderPathwayTemplates();
  await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
  const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
  return { user, dialog };
}

async function openDetailDrawer(
  detail: PathwayTemplateDetailResponse = createTemplateDetail(publishedTemplate, "PUBLISHED"),
) {
  apiMocks.templateListData = { items: [detail.template], total: 1 };
  apiMocks.templateDetailData = detail;
  const user = userEvent.setup();
  renderPathwayTemplates();
  await screen.findByText(detail.template.templateCode);
  await user.click(screen.getByRole("button", { name: /设计与试运行/ }));
  await screen.findByText("路径配置与真实快照试运行");
  return user;
}

async function selectAntdOption(
  user: ReturnType<typeof userEvent.setup>,
  container: HTMLElement,
  label: string,
  optionName: RegExp | string,
) {
  fireEvent.mouseDown(within(container).getByLabelText(label));
  await user.click(await screen.findByText(optionName));
}

function expectNoLegacyPackageKeys(payload: Record<string, unknown>) {
  expect(payload).not.toHaveProperty("packageId");
  expect(payload).not.toHaveProperty("packageVersion");
  expect(payload).not.toHaveProperty("package_version");
}

describe("PathwayTemplates 上线路径维护契约", () => {
  beforeEach(() => {
    apiMocks.templateListData = { items: [], total: 0 };
    apiMocks.templateDetailData = null;
    apiMocks.evaluationIndicatorsData = { items: [losOutcomeIndicator], total: 1 };
    apiMocks.rulesData = { items: [], total: 0 };
    apiMocks.snapshotsData = { items: [], total: 0 };
    apiMocks.snapshotDetailData = null;
    apiMocks.contextFieldCatalogData = [
      {
        fieldPath: "context.readyForFollowup",
        displayName: "可随访",
        dataType: "boolean",
      },
      {
        fieldPath: "observation.systolicBp",
        displayName: "收缩压",
        dataType: "number",
      },
    ];
    apiMocks.contextFieldCatalogError = false;
    apiMocks.refetchList.mockReset();
    apiMocks.refetchDetail.mockReset();
    apiMocks.createTemplate.mockReset();
    apiMocks.createTemplate.mockResolvedValue(createTemplateDetail());
    apiMocks.simulatePathway.mockReset();
    apiMocks.previewRun.mockReset();
    apiMocks.templateListParams = [];
    apiMocks.evaluationIndicatorParams = [];
    apiMocks.ruleListParams = [];
    apiMocks.snapshotQueryParams = [];
  });

  it("路径维护不再展示旧归属、手工版本与路径专属发布入口", async () => {
    const { user, dialog } = await openCreateDialog();

    expect(
      screen.queryByRole("button", { name: new RegExp(`管理路径知识${"包"}`) }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/7 步流发布/)).not.toBeInTheDocument();
    expect(screen.queryByText(/灰度发布|全量激活|回滚目标/)).not.toBeInTheDocument();
    expect(within(dialog).queryByLabelText("归属路径知识" + "包")).not.toBeInTheDocument();
    expect(
      within(dialog).queryByPlaceholderText("默认使用路径知识" + "包版本"),
    ).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));
    await waitFor(() => expect(apiMocks.createTemplate).not.toHaveBeenCalled());
  });

  it(
    "路径原型提交由系统自动生成下一草稿版本，不提交旧容器归属",
    async () => {
      const { user, dialog } = await openCreateDialog();

      await user.click(within(dialog).getByLabelText("基础节点闭环"));
      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      await waitFor(() => expect(apiMocks.createTemplate).toHaveBeenCalled());
      const payload = apiMocks.createTemplate.mock.calls[0][0] as Record<string, unknown> & {
        templateCode: string;
        diseaseCode: string;
        startNodeCode: string;
        nodes: Array<{ nodeCode: string; terminal?: boolean }>;
        edges: Array<{ edgeCode: string; fromNodeCode: string; toNodeCode: string }>;
      };
      expectNoLegacyPackageKeys(payload);
      expect(payload.templateCode).toBe("PATH.CLINICAL.CYCLE");
      expect(payload.diseaseCode).toBe("GENERAL");
      expect(payload.startNodeCode).toBe("ASSESS");
      expect(payload.nodes).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ nodeCode: "ASSESS" }),
          expect.objectContaining({ nodeCode: "DISPOSITION", terminal: true }),
        ]),
      );
      expect(payload.edges).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            edgeCode: "E-ASSESS-DISPOSITION",
            fromNodeCode: "ASSESS",
            toNodeCode: "DISPOSITION",
          }),
        ]),
      );
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径编辑器保留 L1/L2/L3 三层模型，条件树同步不产生片段、子路径或手工生效版本引用",
    async () => {
      const { user, dialog } = await openCreateDialog();

      expect(within(dialog).getByRole("tab", { name: /基础模板/ })).toBeInTheDocument();
      expect(within(dialog).getByRole("tab", { name: /节点画布/ })).toBeInTheDocument();
      expect(within(dialog).queryByRole("tab", { name: /受控配置文本/ })).not.toBeInTheDocument();

      await user.click(within(dialog).getByLabelText("基础节点闭环"));
      await user.click(within(dialog).getByRole("tab", { name: /节点画布/ }));
      expect(within(dialog).queryByText("条件片段")).not.toBeInTheDocument();
      expect(within(dialog).queryByText("子路径")).not.toBeInTheDocument();

      fireEvent.change(within(dialog).getByLabelText("条件字段路径"), {
        target: { value: "context.readyForFollowup" },
      });
      fireEvent.change(within(dialog).getByLabelText("条件值"), {
        target: { value: "true" },
      });
      await user.click(within(dialog).getByRole("button", { name: /同步到受控配置/ }));
      await user.click(within(dialog).getByRole("tab", { name: /受控配置文本/ }));

      const dslEditor = within(dialog).getByLabelText("路径配置文本") as HTMLTextAreaElement;
      const parsed = JSON.parse(dslEditor.value) as {
        edges: Array<{ condition?: Record<string, unknown> }>;
      };
      expect(dslEditor.value).toContain('"fact": "context.readyForFollowup"');
      expect(dslEditor.value).not.toContain("packageVersion");
      expect(dslEditor.value).not.toContain("fragmentRef");
      expect(dslEditor.value).not.toContain("subPathwayRef");
      expect(parsed.edges[0].condition).not.toHaveProperty("packageCode");
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径可单向引用已发布规则，受控配置只保存规则稳定身份并由机构生效版本确认精确版本",
    async () => {
      apiMocks.rulesData = { items: [publishedRule], total: 1 };
      const { user, dialog } = await openCreateDialog();

      await user.click(within(dialog).getByLabelText("基础节点闭环"));
      await user.click(within(dialog).getByRole("tab", { name: /节点画布/ }));
      await selectAntdOption(user, dialog, "守卫来源", "引用已发布规则");
      await selectAntdOption(user, dialog, "已发布规则", /低血压路径分流/);

      expect(within(dialog).getByText(/运行时由同一机构生效版本确认规则版本/)).toBeInTheDocument();

      await user.click(within(dialog).getByRole("button", { name: /同步到受控配置/ }));
      await user.click(within(dialog).getByRole("tab", { name: /受控配置文本/ }));
      const dslEditor = within(dialog).getByLabelText("路径配置文本") as HTMLTextAreaElement;
      const parsed = JSON.parse(dslEditor.value) as {
        edges: Array<{ condition?: Record<string, unknown> }>;
      };

      expect(parsed.edges[0].condition).toEqual({
        ruleRef: "RULE.PATH.HYPOTENSION",
        ruleAssetId: "rule-asset-hypotension",
      });
      expect(dslEditor.value).not.toContain("packageVersion");
      expect(dslEditor.value).not.toContain("fragmentRef");
      expect(dslEditor.value).not.toContain("subPathwayRef");

      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));
      await waitFor(() => expect(apiMocks.createTemplate).toHaveBeenCalled());
      const payload = apiMocks.createTemplate.mock.calls[0][0] as Record<string, unknown> & {
        edges: Array<{ condition?: Record<string, unknown> }>;
      };
      expectNoLegacyPackageKeys(payload);
      expect(payload.edges[0].condition).toEqual({
        ruleRef: "RULE.PATH.HYPOTENSION",
        ruleAssetId: "rule-asset-hypotension",
      });
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "创建路径草稿可用真实患者快照试运行，预览请求不携带旧容器版本",
    async () => {
      apiMocks.snapshotsData = {
        items: [
          {
            snapshotId: "ctx-path-draft-001",
            patientId: "P-001",
            encounterId: "E-001",
            status: "ACTIVE",
            qualityStatus: "PARTIAL",
            createdAt: "2026-06-02T08:00:00Z",
          },
        ],
        total: 1,
      };
      apiMocks.snapshotDetailData = {
        snapshotId: "ctx-path-draft-001",
        status: "ACTIVE",
        qualityStatus: "PARTIAL",
        missingFields: [{ resourceType: "OBSERVATION", field: "code", level: "WARN" }],
        mappingStatus: {},
        resources: {
          patient: { patientId: "P-001" },
          observations: [{ code: "OBS.TEST", valueNumeric: 7.1 }],
        },
        createdAt: "2026-06-02T08:00:00Z",
        traceId: "trace-path-draft",
      };
      const previewResult: AuthoringPreviewRunResponse = {
        subject: "PATHWAY_GUARD",
        snapshotId: "ctx-path-draft-001",
        runtimeReleaseId: "runtime-release-path-1",
        matched: true,
        outcomeText: "草稿路径推进到 DISPOSITION",
        nodeTrajectory: ["ASSESS", "DISPOSITION"],
        finalStatus: "NODE_EXECUTING",
        selectedEdgeCode: "E-ASSESS-DISPOSITION",
        conditionEvidence: [
          {
            fact: "observations[].valueNumeric",
            operator: "gte",
            matched: true,
            missing: false,
            formula: "路径边条件命中",
          },
        ],
        contextQualityStatus: "PARTIAL",
        missingFields: [{ resourceType: "OBSERVATION", field: "code", level: "WARN" }],
        mappingStatus: {},
        contextResourceCounts: { observations: 1 },
        traceId: "trace-path-preview-run",
      };
      apiMocks.previewRun.mockResolvedValue(previewResult);
      const { user, dialog } = await openCreateDialog();

      await user.click(within(dialog).getByLabelText("基础节点闭环"));
      await user.click(within(dialog).getByRole("tab", { name: /即配即试/ }));
      await user.type(within(dialog).getByLabelText("患者 ID"), "P-001");
      await user.type(within(dialog).getByLabelText("就诊 ID"), "E-001");
      await user.click(within(dialog).getByRole("button", { name: /读取真实快照/ }));
      await user.click(await within(dialog).findByRole("button", { name: /ctx-path-draft-001/ }));
      await user.click(within(dialog).getByRole("button", { name: "运行草稿试运行" }));

      await waitFor(() => expect(apiMocks.previewRun).toHaveBeenCalled());
      const payload = apiMocks.previewRun.mock.calls[0][0] as Record<string, unknown>;
      expect(payload).toEqual(
        expect.objectContaining({
          subject: "PATHWAY_GUARD",
          snapshotId: "ctx-path-draft-001",
          startNodeCode: "ASSESS",
        }),
      );
      expectNoLegacyPackageKeys(payload);
      expect(await within(dialog).findByText("草稿路径推进到 DISPOSITION")).toBeInTheDocument();
      expect(within(dialog).getByText("E-ASSESS-DISPOSITION")).toBeInTheDocument();
      expect(within(dialog).getByText("路径边条件命中")).toBeInTheDocument();
      expect(apiMocks.simulatePathway).not.toHaveBeenCalled();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "已发布路径复制为下一版草稿时复用内容但不要求重新选择旧上线容器",
    async () => {
      apiMocks.createTemplate.mockResolvedValue(createTemplateDetail(publishedTemplate, "DRAFT"));
      const user = await openDetailDrawer(createTemplateDetail(publishedTemplate, "PUBLISHED"));

      expect(screen.getByText(/当前路径版本已纳入机构生效版本/)).toBeInTheDocument();
      expect(screen.queryByRole("tab", { name: /7 步流发布/ })).not.toBeInTheDocument();
      expect(
        screen.getByText("路径拓扑与真实快照试运行是主视图；配置明细用于核查受控配置。"),
      ).toBeInTheDocument();
      expect(screen.getByRole("switch", { name: "配置明细" })).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /复制为新版本/ }));

      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      expect(within(dialog).queryByLabelText("归属路径知识" + "包")).not.toBeInTheDocument();
      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      await waitFor(() => expect(apiMocks.createTemplate).toHaveBeenCalled());
      const payload = apiMocks.createTemplate.mock.calls[0][0] as Record<string, unknown> & {
        templateCode: string;
        nodes: Array<{ nodeCode: string }>;
        outcomeBindings: Array<{ indicatorCode: string }>;
      };
      expectNoLegacyPackageKeys(payload);
      expect(payload.templateCode).toBe("PATH.CARDIO.REVIEW");
      expect(payload.nodes).toEqual(
        expect.arrayContaining([expect.objectContaining({ nodeCode: "ASSESS" })]),
      );
      expect(payload.outcomeBindings).toEqual(
        expect.arrayContaining([expect.objectContaining({ indicatorCode: "PATH.OUTCOME.LOS" })]),
      );
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "字段目录不可用时阻断路径条件同步到受控配置",
    async () => {
      apiMocks.contextFieldCatalogError = true;
      const { user, dialog } = await openCreateDialog();

      await user.click(within(dialog).getByLabelText("基础节点闭环"));
      await user.click(within(dialog).getByRole("tab", { name: /节点画布/ }));

      expect(
        within(dialog).getByText("字段目录暂不可用，路径条件不能同步到受控配置。"),
      ).toBeInTheDocument();
      expect(within(dialog).getByRole("button", { name: /同步到受控配置/ })).toBeDisabled();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );
});
