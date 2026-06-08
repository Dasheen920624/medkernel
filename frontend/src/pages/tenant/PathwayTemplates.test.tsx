import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import PathwayTemplates from "./PathwayTemplates";
import type {
  EvaluationIndicator,
  PathwayTemplate,
  PathwayTemplateDetailResponse,
  SpecialtyPackage,
} from "@/shared/api/hooks";

const apiMocks = vi.hoisted(() => ({
  templateListData: { items: [], total: 0 } as unknown,
  rollbackTemplateListData: { items: [], total: 0 } as unknown,
  templateDetailData: null as unknown,
  templateImpactData: null as unknown,
  templateInheritanceDiffData: null as unknown,
  evaluationIndicatorsData: { items: [], total: 0 } as unknown,
  packagesData: { items: [], total: 0 } as unknown,
  conditionFragmentsData: { items: [], total: 0 } as unknown,
  snapshotsData: { items: [], total: 0 } as unknown,
  snapshotDetailData: null as unknown,
  refetchList: vi.fn(),
  refetchDetail: vi.fn(),
  refetchPackages: vi.fn(),
  createPackage: vi.fn(),
  createTemplate: vi.fn(),
  publishTemplate: vi.fn(),
  fullRolloutTemplate: vi.fn(),
  rollbackTemplate: vi.fn(),
  simulatePathway: vi.fn(),
  previewRun: vi.fn(),
  templateListParams: [] as unknown[],
  authoringPreviewData: {
    previewText: "路径守卫 E1（从 ASSESS 到 FOLLOWUP）：风险等级 等于 HIGH。",
    lines: ["路径守卫 E1（从 ASSESS 到 FOLLOWUP）：风险等级 等于 HIGH"],
    segments: [],
    warnings: [],
    traceId: "trace-pathway-preview",
  } as unknown,
}));

const PATHWAY_INTERACTION_TIMEOUT_MS = 90_000;

vi.mock("@/shared/api/hooks", () => ({
  usePathwayTemplates: (params?: { templateCode?: string }) => {
    apiMocks.templateListParams.push(params ?? {});
    return {
      data: params?.templateCode ? apiMocks.rollbackTemplateListData : apiMocks.templateListData,
      isLoading: false,
      refetch: apiMocks.refetchList,
    };
  },
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
  usePathwayTemplateDetail: () => ({
    data: apiMocks.templateDetailData,
    isLoading: false,
    refetch: apiMocks.refetchDetail,
  }),
  usePathwayTemplateImpact: () => ({
    data: apiMocks.templateImpactData,
    isLoading: false,
    isError: false,
  }),
  usePathwayTemplateInheritanceDiff: () => ({
    data: apiMocks.templateInheritanceDiffData,
    isLoading: false,
    isError: false,
  }),
  useEvaluationIndicators: () => ({
    data: apiMocks.evaluationIndicatorsData,
    isLoading: false,
    isError: false,
  }),
  useSpecialtyPackages: () => ({
    data: apiMocks.packagesData,
    refetch: apiMocks.refetchPackages,
  }),
  useConditionFragments: () => ({
    data: apiMocks.conditionFragmentsData,
    isLoading: false,
    isError: false,
    error: null,
  }),
  useCreateSpecialtyPackage: () => ({
    mutateAsync: apiMocks.createPackage,
    isPending: false,
  }),
  useCreatePathwayTemplate: () => ({
    mutateAsync: apiMocks.createTemplate,
    isPending: false,
  }),
  usePublishPathwayTemplate: () => ({
    mutateAsync: apiMocks.publishTemplate,
    isPending: false,
  }),
  useFullRolloutPathwayTemplate: () => ({
    mutateAsync: apiMocks.fullRolloutTemplate,
    isPending: false,
  }),
  useRollbackPathwayTemplate: () => ({
    mutateAsync: apiMocks.rollbackTemplate,
    isPending: false,
  }),
  useSimulatePathway: () => ({
    mutateAsync: apiMocks.simulatePathway,
    isPending: false,
  }),
  useContextFieldCatalog: () => ({ data: [], isLoading: false, isError: false }),
  useStandardTerms: () => ({ data: { items: [], total: 0 }, isLoading: false, isError: false }),
  useMappingCoverage: () => ({ data: [], isLoading: false, isError: false }),
  useCreateContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteContextField: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useContextSnapshots: () => ({
    data: apiMocks.snapshotsData,
    isLoading: false,
    isError: false,
  }),
  useContextSnapshotDetail: () => ({
    data: apiMocks.snapshotDetailData,
    isLoading: false,
    isError: false,
  }),
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

const specialtyPackage: SpecialtyPackage = {
  id: 1,
  packageId: "sp-path-1",
  packageCode: "PKG.PATH.CARDIO",
  diseaseCode: "CARDIO",
  name: "心血管专病包",
  packageVersion: "pkg-2026.06",
  status: "DRAFT",
  sourceRef: "院内已审核路径制度",
  description: "院内路径配置包",
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
  packageVersion: "pkg-2026.06",
  status: "ACTIVE",
};

const draftTemplate: PathwayTemplate = {
  id: 1,
  templateId: "pt-path-1",
  packageId: "sp-path-1",
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
    include: {
      all: [{ fact: "patient.dischargeReady", operator: "equals", value: true }],
    },
  }),
};

const publishedTemplate: PathwayTemplate = {
  ...draftTemplate,
  templateId: "pt-path-published",
  templateVersion: 2,
  status: "PUBLISHED",
};

const rollbackTemplate: PathwayTemplate = {
  ...draftTemplate,
  templateId: "pt-path-offline",
  templateVersion: 1,
  status: "OFFLINE",
};

function createTemplateDetail(): PathwayTemplateDetailResponse {
  return {
    template: draftTemplate,
    deploymentStatus: "DRAFT",
    milestones: [
      {
        id: 1,
        milestoneId: "milestone-preop",
        templateId: "pt-path-1",
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
        templateId: "pt-path-1",
        nodeCode: "ASSESS",
        name: "入径评估",
        nodeType: "ASSESSMENT",
        milestoneCode: "M-PREOP-ASSESS",
        sortOrder: 1,
        responsibleRole: "专科医生",
        accountableRole: "科主任",
        consultedRolesJson: '["护理组"]',
        informedRolesJson: '["质控办"]',
        timeWindowMinutes: 60,
        terminalFlag: false,
      },
      {
        id: 2,
        nodeId: "node-followup",
        templateId: "pt-path-1",
        nodeCode: "FOLLOWUP",
        name: "出径随访",
        nodeType: "FOLLOWUP",
        sortOrder: 2,
        responsibleRole: "随访护士",
        accountableRole: "护理组长",
        consultedRolesJson: "[]",
        informedRolesJson: '["专科医生"]',
        timeWindowMinutes: 120,
        terminalFlag: true,
      },
    ],
    edges: [
      {
        id: 1,
        edgeId: "edge-followup",
        templateId: "pt-path-1",
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
        templateId: "pt-path-1",
        nodeCode: "ASSESS",
        metricCode: "PATH.TIME.ASSESS",
      },
    ],
    traceId: "trace-path-detail",
  };
}

async function openPathwayDrawer() {
  const user = userEvent.setup();
  renderPathwayTemplates();
  await screen.findByText("PATH.CARDIO.REVIEW");
  await user.click(screen.getByRole("button", { name: /设计与试运行/ }));
  await screen.findByText("路径配置与真实快照试运行控制台");
  return user;
}

describe("PathwayTemplates 三层路径配置体验", () => {
  beforeEach(() => {
    apiMocks.templateListData = { items: [], total: 0 };
    apiMocks.rollbackTemplateListData = { items: [], total: 0 };
    apiMocks.templateDetailData = null;
    apiMocks.templateImpactData = null;
    apiMocks.templateInheritanceDiffData = null;
    apiMocks.evaluationIndicatorsData = { items: [], total: 0 };
    apiMocks.packagesData = { items: [], total: 0 };
    apiMocks.conditionFragmentsData = { items: [], total: 0 };
    apiMocks.snapshotsData = { items: [], total: 0 };
    apiMocks.snapshotDetailData = null;
    apiMocks.refetchList.mockReset();
    apiMocks.refetchDetail.mockReset();
    apiMocks.refetchPackages.mockReset();
    apiMocks.createPackage.mockReset();
    apiMocks.createTemplate.mockReset();
    apiMocks.publishTemplate.mockReset();
    apiMocks.fullRolloutTemplate.mockReset();
    apiMocks.rollbackTemplate.mockReset();
    apiMocks.simulatePathway.mockReset();
    apiMocks.previewRun.mockReset();
    apiMocks.templateListParams = [];
  });

  it(
    "新建路径模板提供 L1 模板、L2 节点画布与 L3 DSL，不预置固定节点边 JSON",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });

      expect(within(dialog).getByRole("tab", { name: /L1 模板/ })).toBeInTheDocument();
      expect(within(dialog).getByRole("tab", { name: /L2 节点画布/ })).toBeInTheDocument();
      expect(within(dialog).queryByRole("tab", { name: /L3 DSL/ })).not.toBeInTheDocument();
      await user.click(within(dialog).getByRole("switch", { name: "专家模式" }));
      expect(within(dialog).getByRole("tab", { name: /L3 DSL/ })).toBeInTheDocument();
      expect(
        within(dialog).queryByLabelText("生命周期节点配置 (JSON 列表)"),
      ).not.toBeInTheDocument();
      expect(
        within(dialog).queryByLabelText("拓扑流转连线配置 (JSON 列表)"),
      ).not.toBeInTheDocument();

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      fireEvent.change(within(dialog).getByLabelText("节点编码"), {
        target: { value: "ASSESS" },
      });
      fireEvent.change(within(dialog).getByLabelText("节点名称"), {
        target: { value: "入径评估" },
      });
      await user.click(within(dialog).getByRole("button", { name: /添加流转边/ }));
      expect(within(dialog).queryByLabelText("条件 DSL JSON")).not.toBeInTheDocument();
      fireEvent.change(within(dialog).getByLabelText("边编码"), {
        target: { value: "EDGE.ASSESS.FOLLOWUP" },
      });
      fireEvent.change(within(dialog).getByLabelText("源节点"), {
        target: { value: "ASSESS" },
      });
      fireEvent.change(within(dialog).getByLabelText("目标节点"), {
        target: { value: "FOLLOWUP" },
      });
      fireEvent.change(within(dialog).getByLabelText("条件字段路径"), {
        target: { value: "context.readyForFollowup" },
      });
      fireEvent.change(within(dialog).getByLabelText("条件值"), {
        target: { value: "true" },
      });
      expect(within(dialog).getByText("可读预览")).toBeInTheDocument();
      expect(
        within(dialog).getByText("路径守卫 E1（从 ASSESS 到 FOLLOWUP）：风险等级 等于 HIGH。"),
      ).toBeInTheDocument();
      await user.click(within(dialog).getByRole("button", { name: /同步到 DSL/ }));
      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("路径 DSL JSON");
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"nodeCode": "ASSESS"');
      expect((dslEditor as HTMLTextAreaElement).value).toContain(
        '"fact": "context.readyForFollowup"',
      );

      fireEvent.change(dslEditor, {
        target: {
          value: JSON.stringify({
            nodes: [
              {
                nodeCode: "FOLLOWUP",
                name: "随访确认",
                nodeType: "FOLLOWUP",
                sortOrder: 1,
                terminal: true,
              },
            ],
            edges: [],
            metricBindings: [],
          }),
        },
      });
      await user.click(within(dialog).getByRole("button", { name: /回填到 L2/ }));
      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      expect(within(dialog).getByDisplayValue("FOLLOWUP")).toBeInTheDocument();
      expect(within(dialog).getByDisplayValue("随访确认")).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "创建路径草稿时可选择真实快照就地试运行并定位路径边证据",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
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
        packageVersion: "pkg-2026.06",
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
      apiMocks.previewRun.mockResolvedValue({
        subject: "PATHWAY_GUARD",
        snapshotId: "ctx-path-draft-001",
        packageVersion: "pkg-2026.06",
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
      });
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });

      await user.click(within(dialog).getByLabelText("急诊处置路径"));
      await user.click(within(dialog).getByRole("tab", { name: /即配即试/ }));
      await user.type(within(dialog).getByLabelText("患者 ID"), "P-001");
      await user.type(within(dialog).getByLabelText("就诊 ID"), "E-001");
      await user.click(within(dialog).getByRole("button", { name: /读取真实快照/ }));
      await user.click(await within(dialog).findByRole("button", { name: /ctx-path-draft-001/ }));
      await user.click(within(dialog).getByRole("button", { name: "运行草稿试运行" }));

      await waitFor(() =>
        expect(apiMocks.previewRun).toHaveBeenCalledWith(
          expect.objectContaining({
            subject: "PATHWAY_GUARD",
            packageVersion: "pkg-2026.06",
            snapshotId: "ctx-path-draft-001",
            startNodeCode: "ASSESS",
            dsl: expect.objectContaining({
              edges: expect.arrayContaining([
                expect.objectContaining({ edgeCode: "E-ASSESS-DISPOSITION" }),
              ]),
            }),
          }),
        ),
      );
      expect(apiMocks.simulatePathway).not.toHaveBeenCalled();
      expect(await within(dialog).findByText("草稿路径推进到 DISPOSITION")).toBeInTheDocument();
      expect(within(dialog).getByText("E-ASSESS-DISPOSITION")).toBeInTheDocument();
      expect(within(dialog).getByText("路径边条件命中")).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径原型向导生成可提交的默认拓扑草稿",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.createTemplate.mockResolvedValue(createTemplateDetail());
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });

      await user.click(within(dialog).getByLabelText("急诊处置路径"));
      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      await waitFor(() => expect(apiMocks.createTemplate).toHaveBeenCalled());
      const payload = apiMocks.createTemplate.mock.calls[0][0] as {
        packageId: string;
        templateCode: string;
        diseaseCode: string;
        startNodeCode: string;
        milestones: Array<{ milestoneCode: string; dayOffset?: number }>;
        nodes: Array<{ nodeCode: string; terminal?: boolean }>;
        edges: Array<{ edgeCode: string; fromNodeCode: string; toNodeCode: string }>;
      };
      expect(payload.packageId).toBe(specialtyPackage.packageId);
      expect(payload.templateCode).toBe("PATH.ED.DISPOSITION");
      expect(payload.diseaseCode).toBe("ED");
      expect(payload.startNodeCode).toBe("ASSESS");
      expect(payload.milestones).toEqual(
        expect.arrayContaining([expect.objectContaining({ milestoneCode: "M-ED-ASSESS" })]),
      );
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
    "路径边条件复用递归条件树构建器并同步为嵌套 guard",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));

      await user.click(within(dialog).getByRole("button", { name: /添加流转边/ }));
      const factInputs = () =>
        within(dialog).getAllByRole("combobox", {
          name: "上下文字段路径",
        });
      fireEvent.change(factInputs().at(-1) as HTMLElement, {
        target: { value: "context.ready" },
      });
      await user.click(
        within(dialog).getAllByRole("button", { name: "新增子条件组" }).at(-1) as HTMLElement,
      );
      const allergyFieldInput = factInputs().at(-1) as HTMLElement;
      fireEvent.change(allergyFieldInput, {
        target: { value: "allergyIntolerances[].code" },
      });

      await user.click(within(dialog).getByRole("switch", { name: "专家模式" }));
      await user.click(within(dialog).getByRole("button", { name: /同步到 DSL/ }));
      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("路径 DSL JSON") as HTMLTextAreaElement;
      const parsed = JSON.parse(dslEditor.value) as {
        edges: Array<{ condition?: { all?: unknown[] } }>;
      };

      expect(parsed.edges[0].condition?.all).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ fact: "context.ready" }),
          expect.objectContaining({ all: expect.any(Array) }),
        ]),
      );
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径边守卫可按引用复用同包版本条件片段",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.conditionFragmentsData = {
        items: [
          {
            fragmentId: "frag-bleeding-v1",
            tenantId: "tenant-hospital",
            fragmentCode: "FRAG_HIGH_BLEEDING",
            name: "高出血风险",
            category: "抗凝",
            bodyJson: {
              all: [
                {
                  fact: "diagnoses[].code",
                  operator: "in",
                  value: ["I61"],
                  ui: { valueKind: "list" },
                },
              ],
            },
            versionNo: 1,
            status: "ACTIVE",
            packageVersion: "pkg-2026.06",
            createdAt: "2026-06-08T00:00:00Z",
            createdBy: "u-admin",
            updatedAt: "2026-06-08T00:00:00Z",
            updatedBy: "u-admin",
            traceId: "trace-fragment",
          },
        ],
        total: 1,
      };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      fireEvent.mouseDown(within(dialog).getByLabelText("归属专病包"));
      await user.click(await screen.findByText(/心血管专病包/));

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加流转边/ }));
      fireEvent.mouseDown(within(dialog).getByRole("combobox", { name: "选择条件片段" }));
      await user.click(
        await screen.findByText("高出血风险 · FRAG_HIGH_BLEEDING · v1", {
          selector: ".ant-select-item-option-content",
        }),
      );
      await user.click(within(dialog).getByRole("button", { name: /引用/ }));
      expect(await within(dialog).findByTestId("condition-fragment-leaf")).toHaveTextContent(
        "FRAG_HIGH_BLEEDING",
      );

      await user.click(within(dialog).getByRole("button", { name: /同步到 DSL/ }));
      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("路径 DSL JSON") as HTMLTextAreaElement;
      expect(dslEditor.value).toContain('"fragmentRef": "FRAG_HIGH_BLEEDING"');
      expect(dslEditor.value).toContain('"packageVersion": "pkg-2026.06"');
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "新建路径模板提交入径模式与入出径真实条件树",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.createTemplate.mockResolvedValue(createTemplateDetail());
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      fireEvent.mouseDown(within(dialog).getByLabelText("归属专病包"));
      await user.click(await screen.findByText(/心血管专病包/));
      fireEvent.change(within(dialog).getByLabelText("路径模型名称"), {
        target: { value: "心血管路径复核" },
      });
      fireEvent.change(within(dialog).getByLabelText("路径模型代码"), {
        target: { value: "PATH.CARDIO.REVIEW" },
      });
      fireEvent.change(within(dialog).getByLabelText("病种代码"), {
        target: { value: "CARDIO" },
      });
      await user.click(within(dialog).getByText("人工确认入径"));
      fireEvent.change(within(dialog).getByLabelText("临床知识与指南基础"), {
        target: { value: "院内已审核路径制度 2026" },
      });

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      const changeCriteriaLeaf = (index: number, fact: string, value: string) => {
        fireEvent.change(
          within(dialog).getAllByRole("combobox", { name: "上下文字段路径" })[index],
          { target: { value: fact } },
        );
        fireEvent.change(within(dialog).getAllByLabelText("比较值")[index], {
          target: { value },
        });
      };
      changeCriteriaLeaf(0, "patient.mpi", "patient-1");
      changeCriteriaLeaf(1, "observation.HB.value", "50");
      changeCriteriaLeaf(2, "patient.dischargeReady", "true");

      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      fireEvent.change(within(dialog).getByLabelText("节点编码"), {
        target: { value: "ASSESS" },
      });
      fireEvent.change(within(dialog).getByLabelText("节点名称"), {
        target: { value: "入径评估" },
      });
      await user.click(within(dialog).getByRole("switch", { name: "终止节点" }));
      fireEvent.mouseDown(within(dialog).getByLabelText("起始节点"));
      await user.click(await screen.findByText("入径评估（ASSESS）"));

      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      await waitFor(() => expect(apiMocks.createTemplate).toHaveBeenCalled());
      const payload = apiMocks.createTemplate.mock.calls[0][0] as {
        entryMode: string;
        entryCriteria: { include?: { all?: unknown[] }; exclude?: { all?: unknown[] } };
        exitCriteria: { include?: { all?: unknown[] } };
      };
      expect(payload.entryMode).toBe("MANUAL_CONFIRM");
      expect(payload.entryCriteria.include?.all).toEqual(
        expect.arrayContaining([expect.objectContaining({ fact: "patient.mpi" })]),
      );
      expect(payload.entryCriteria.exclude?.all).toEqual(
        expect.arrayContaining([expect.objectContaining({ fact: "observation.HB.value" })]),
      );
      expect(payload.exitCriteria.include?.all).toEqual(
        expect.arrayContaining([expect.objectContaining({ fact: "patient.dischargeReady" })]),
      );
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "新建路径模板提交阶段里程碑、天序与节点里程碑绑定",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.evaluationIndicatorsData = { items: [losOutcomeIndicator], total: 1 };
      apiMocks.createTemplate.mockResolvedValue(createTemplateDetail());
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      fireEvent.mouseDown(within(dialog).getByLabelText("归属专病包"));
      await user.click(await screen.findByText(/心血管专病包/));
      fireEvent.change(within(dialog).getByLabelText("路径模型名称"), {
        target: { value: "围手术期路径" },
      });
      fireEvent.change(within(dialog).getByLabelText("路径模型代码"), {
        target: { value: "PATH.SURGERY" },
      });
      fireEvent.change(within(dialog).getByLabelText("病种代码"), {
        target: { value: "SURGERY" },
      });
      fireEvent.change(within(dialog).getByLabelText("临床知识与指南基础"), {
        target: { value: "围手术期路径制度 2026" },
      });

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加里程碑/ }));
      fireEvent.change(within(dialog).getByLabelText("阶段编码"), {
        target: { value: "PREOP" },
      });
      fireEvent.change(within(dialog).getByLabelText("阶段名称"), {
        target: { value: "术前" },
      });
      fireEvent.change(within(dialog).getByLabelText("里程碑编码"), {
        target: { value: "M-PREOP-ASSESS" },
      });
      fireEvent.change(within(dialog).getByLabelText("里程碑名称"), {
        target: { value: "入径评估" },
      });
      fireEvent.change(within(dialog).getByLabelText("天序"), {
        target: { value: "0" },
      });
      fireEvent.change(within(dialog).getByLabelText("预期分钟"), {
        target: { value: "60" },
      });

      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      fireEvent.change(within(dialog).getByLabelText("节点编码"), {
        target: { value: "ASSESS" },
      });
      fireEvent.change(within(dialog).getByLabelText("节点名称"), {
        target: { value: "入径评估" },
      });
      fireEvent.mouseDown(within(dialog).getByLabelText("所属里程碑"));
      await user.click(await screen.findByText("术前 / 第 0 天 / 入径评估（M-PREOP-ASSESS）"));
      fireEvent.change(within(dialog).getByLabelText("时窗分钟"), {
        target: { value: "60" },
      });
      fireEvent.change(await within(dialog).findByLabelText("时钟指标编码"), {
        target: { value: "STEMI.DOOR_TO_BALLOON" },
      });
      fireEvent.mouseDown(await within(dialog).findByLabelText("SLA基准"));
      await user.click(await screen.findByText("入院时间"));
      fireEvent.change(await within(dialog).findByLabelText("目标分钟"), {
        target: { value: "90" },
      });
      fireEvent.change(await within(dialog).findByLabelText("最晚分钟"), {
        target: { value: "120" },
      });
      fireEvent.change(await within(dialog).findByLabelText("上报分钟"), {
        target: { value: "105" },
      });
      await user.click(within(dialog).getByRole("switch", { name: "终止节点" }));
      fireEvent.mouseDown(within(dialog).getByLabelText("起始节点"));
      await user.click(await screen.findByText("入径评估（ASSESS）"));

      await user.click(within(dialog).getByRole("button", { name: /添加结局指标/ }));
      fireEvent.mouseDown(within(dialog).getByLabelText("评估指标"));
      await user.click(await screen.findByText("平均住院日（PATH.OUTCOME.LOS）"));
      fireEvent.change(within(dialog).getByLabelText("指标包版本"), {
        target: { value: "pkg-2026.06" },
      });

      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      await waitFor(() => expect(apiMocks.createTemplate).toHaveBeenCalled());
      const payload = apiMocks.createTemplate.mock.calls[0][0] as {
        milestones: Array<{ phaseCode: string; milestoneCode: string; dayOffset: number }>;
        nodes: Array<{
          nodeCode: string;
          milestoneCode?: string;
          responsibleRole?: string;
          accountableRole?: string;
          config?: {
            clockSla?: {
              baselineEvent?: string;
              targetMinutes?: number;
              maxMinutes?: number;
              escalations?: Array<{ level: string; afterMinutes: number }>;
            };
          };
        }>;
        metricBindings: Array<{ nodeCode: string; metricCode: string }>;
        outcomeBindings: Array<{
          scope: string;
          refCode?: string;
          indicatorCode: string;
          packageVersion?: string;
        }>;
      };
      expect(payload.milestones).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            phaseCode: "PREOP",
            milestoneCode: "M-PREOP-ASSESS",
            dayOffset: 0,
          }),
        ]),
      );
      expect(payload.nodes).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            nodeCode: "ASSESS",
            milestoneCode: "M-PREOP-ASSESS",
            responsibleRole: "专科医生",
            accountableRole: "专科医生",
            config: expect.objectContaining({
              clockSla: expect.objectContaining({
                baselineEvent: "ADMISSION",
                targetMinutes: 90,
                maxMinutes: 120,
                escalations: expect.arrayContaining([
                  expect.objectContaining({ level: "REMINDER", afterMinutes: 90 }),
                  expect.objectContaining({ level: "REPORT", afterMinutes: 105 }),
                  expect.objectContaining({ level: "QUALITY_RECORD", afterMinutes: 120 }),
                ]),
              }),
            }),
          }),
        ]),
      );
      expect(payload.metricBindings).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            nodeCode: "ASSESS",
            metricCode: "STEMI.DOOR_TO_BALLOON",
          }),
        ]),
      );
      expect(payload.outcomeBindings).toEqual([
        {
          scope: "TEMPLATE",
          indicatorCode: "PATH.OUTCOME.LOS",
          packageVersion: "pkg-2026.06",
        },
      ]);
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "新建路径模板拒绝关键时钟 SLA 最晚分钟早于目标分钟",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      fireEvent.mouseDown(within(dialog).getByLabelText("归属专病包"));
      await user.click(await screen.findByText(/心血管专病包/));
      fireEvent.change(within(dialog).getByLabelText("路径模型名称"), {
        target: { value: "SLA 校验路径" },
      });
      fireEvent.change(within(dialog).getByLabelText("路径模型代码"), {
        target: { value: "PATH.SLA.INVALID" },
      });
      fireEvent.change(within(dialog).getByLabelText("病种代码"), {
        target: { value: "SLA" },
      });
      fireEvent.change(within(dialog).getByLabelText("临床知识与指南基础"), {
        target: { value: "关键时钟 SLA 制度 2026" },
      });

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      fireEvent.change(within(dialog).getByLabelText("节点编码"), {
        target: { value: "CLOCK" },
      });
      fireEvent.change(within(dialog).getByLabelText("节点名称"), {
        target: { value: "关键时钟" },
      });
      fireEvent.change(within(dialog).getByLabelText("时窗分钟"), {
        target: { value: "60" },
      });
      fireEvent.change(await within(dialog).findByLabelText("时钟指标编码"), {
        target: { value: "CLOCK.SLA" },
      });
      fireEvent.change(await within(dialog).findByLabelText("目标分钟"), {
        target: { value: "90" },
      });
      fireEvent.change(await within(dialog).findByLabelText("最晚分钟"), {
        target: { value: "60" },
      });
      await user.click(within(dialog).getByRole("switch", { name: "终止节点" }));
      fireEvent.mouseDown(within(dialog).getByLabelText("起始节点"));
      await user.click(await screen.findByText("关键时钟（CLOCK）"));

      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      expect(await screen.findByText(/SLA 时限必须满足 min <= target <= max/)).toBeInTheDocument();
      expect(apiMocks.createTemplate).not.toHaveBeenCalled();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "富节点类型提供结构化配置并只暴露后端权威边类型",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.createTemplate.mockResolvedValue(createTemplateDetail());
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      fireEvent.mouseDown(within(dialog).getByLabelText("归属专病包"));
      await user.click(await screen.findByText(/心血管专病包/));
      fireEvent.change(within(dialog).getByLabelText("路径模型名称"), {
        target: { value: "富节点路径" },
      });
      fireEvent.change(within(dialog).getByLabelText("路径模型代码"), {
        target: { value: "PATH.RICH.NODE" },
      });
      fireEvent.change(within(dialog).getByLabelText("病种代码"), {
        target: { value: "RICH" },
      });
      fireEvent.change(within(dialog).getByLabelText("临床知识与指南基础"), {
        target: { value: "富节点配置制度 2026" },
      });

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      fireEvent.change(within(dialog).getByLabelText("节点编码"), {
        target: { value: "ORDER" },
      });
      fireEvent.change(within(dialog).getByLabelText("节点名称"), {
        target: { value: "医嘱集确认" },
      });
      fireEvent.mouseDown(within(dialog).getByLabelText("节点类型"));
      await user.click(await screen.findByText("ORDER_SET 医嘱集"));
      fireEvent.change(await within(dialog).findByLabelText("医嘱集引用"), {
        target: { value: "sepsis-order-set" },
      });
      await user.click(within(dialog).getByRole("switch", { name: "终止节点" }));
      fireEvent.mouseDown(within(dialog).getByLabelText("起始节点"));
      await user.click(await screen.findByText("医嘱集确认（ORDER）"));

      await user.click(within(dialog).getByRole("button", { name: /添加流转边/ }));
      fireEvent.mouseDown(within(dialog).getByLabelText("流转类型"));
      expect(await screen.findByText("JOIN 并行汇合")).toBeInTheDocument();
      expect(screen.queryByText("VARIANCE 变异流转")).not.toBeInTheDocument();
      await user.keyboard("{Escape}");
      await user.click(within(dialog).getByRole("button", { name: "删除流转边 1" }));

      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      await waitFor(() => expect(apiMocks.createTemplate).toHaveBeenCalled());
      const payload = apiMocks.createTemplate.mock.calls[0][0] as {
        nodes: Array<{ nodeCode: string; nodeType: string; config?: { orderSetRef?: string } }>;
      };
      expect(payload.nodes).toEqual(
        expect.arrayContaining([
          expect.objectContaining({
            nodeCode: "ORDER",
            nodeType: "ORDER_SET",
            config: expect.objectContaining({ orderSetRef: "sepsis-order-set" }),
          }),
        ]),
      );
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "新建路径模板拒绝没有默认兜底边的决策节点",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      fireEvent.mouseDown(within(dialog).getByLabelText("归属专病包"));
      await user.click(await screen.findByText(/心血管专病包/));
      fireEvent.change(within(dialog).getByLabelText("路径模型名称"), {
        target: { value: "决策守卫路径" },
      });
      fireEvent.change(within(dialog).getByLabelText("路径模型代码"), {
        target: { value: "PATH.DECISION.GUARD" },
      });
      fireEvent.change(within(dialog).getByLabelText("病种代码"), {
        target: { value: "GUARD" },
      });
      fireEvent.change(within(dialog).getByLabelText("临床知识与指南基础"), {
        target: { value: "决策守卫配置规范 2026" },
      });

      await user.click(within(dialog).getByRole("switch", { name: "专家模式" }));
      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("路径 DSL JSON") as HTMLTextAreaElement;
      fireEvent.change(dslEditor, {
        target: {
          value: JSON.stringify({
            nodes: [
              {
                nodeCode: "DECIDE",
                name: "分层决策",
                nodeType: "DECISION",
                sortOrder: 1,
                terminal: false,
              },
              {
                nodeCode: "ICU",
                name: "转入 ICU",
                nodeType: "NURSING",
                sortOrder: 2,
                terminal: true,
              },
              {
                nodeCode: "WARD",
                name: "普通病区",
                nodeType: "NURSING",
                sortOrder: 3,
                terminal: true,
              },
            ],
            edges: [
              {
                edgeCode: "E_HIGH",
                fromNodeCode: "DECIDE",
                toNodeCode: "ICU",
                edgeType: "CONDITION",
                priority: 1,
                condition: {
                  fact: "risk.level",
                  operator: "equals",
                  value: "HIGH",
                },
              },
              {
                edgeCode: "E_LOW",
                fromNodeCode: "DECIDE",
                toNodeCode: "WARD",
                edgeType: "CONDITION",
                priority: 2,
                condition: {
                  fact: "risk.level",
                  operator: "equals",
                  value: "LOW",
                },
              },
            ],
            metricBindings: [],
          }),
        },
      });
      await user.click(within(dialog).getByRole("button", { name: /回填到 L2/ }));

      await user.click(within(dialog).getByRole("button", { name: /OK|确 定|确定/ }));

      expect(await screen.findByText(/决策节点 DECIDE 必须配置默认兜底分支/)).toBeInTheDocument();
      expect(apiMocks.createTemplate).not.toHaveBeenCalled();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径详情展示阶段里程碑天序视图和节点归属",
    async () => {
      apiMocks.templateListData = { items: [draftTemplate], total: 1 };
      apiMocks.templateDetailData = {
        ...createTemplateDetail(),
        milestones: [
          {
            milestoneId: "milestone-preop",
            templateId: "pt-path-1",
            phaseCode: "PREOP",
            phaseName: "术前",
            milestoneCode: "M-PREOP-ASSESS",
            name: "入径评估",
            dayOffset: 0,
            expectedOffsetMinutes: 60,
            sortOrder: 1,
          },
        ],
      } as PathwayTemplateDetailResponse;
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };

      const user = await openPathwayDrawer();
      await user.click(screen.getByRole("tab", { name: /L2 节点画布/ }));

      expect(screen.getByText("阶段与天序里程碑")).toBeInTheDocument();
      expect(screen.getByText("术前 / 第 0 天")).toBeInTheDocument();
      expect(screen.getByText("入径评估（M-PREOP-ASSESS）")).toBeInTheDocument();
      expect(screen.getAllByText("M-PREOP-ASSESS").length).toBeGreaterThan(0);
      expect(screen.getByText("R 专科医生 / A 科主任 / C 护理组 / I 质控办")).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径详情展示继承差异与合并后的有效节点",
    async () => {
      const inheritedTemplate = {
        ...draftTemplate,
        parentTemplateId: "pt-standard",
        templateLevel: "DEPARTMENT",
      };
      apiMocks.templateListData = { items: [inheritedTemplate], total: 1 };
      apiMocks.templateDetailData = {
        ...createTemplateDetail(),
        template: inheritedTemplate,
      } as PathwayTemplateDetailResponse;
      apiMocks.templateInheritanceDiffData = {
        templateId: "pt-path-1",
        parentTemplateId: "pt-standard",
        diffItems: [
          {
            itemType: "NODE",
            itemCode: "ASSESS",
            changeType: "OVERRIDDEN",
            fieldName: "timeWindowMinutes",
            parentValue: "60",
            childValue: "30",
          },
          {
            itemType: "NODE",
            itemCode: "REHAB",
            changeType: "ADDED",
            fieldName: null,
            parentValue: null,
            childValue: "康复指导",
          },
          {
            itemType: "NODE",
            itemCode: "EDU",
            changeType: "DISABLED",
            fieldName: null,
            parentValue: "宣教",
            childValue: null,
          },
        ],
        mergedNodes: [
          {
            nodeCode: "ASSESS",
            name: "入径评估",
            nodeType: "ASSESSMENT",
            sortOrder: 1,
            responsibleRole: "专科医生",
            accountableRole: "科主任",
            timeWindowMinutes: 30,
            terminalFlag: false,
            origin: "OVERRIDDEN",
          },
          {
            nodeCode: "REHAB",
            name: "康复指导",
            nodeType: "REHAB",
            sortOrder: 2,
            responsibleRole: "康复师",
            accountableRole: "康复师",
            timeWindowMinutes: 90,
            terminalFlag: false,
            origin: "ADDED",
          },
          {
            nodeCode: "FOLLOWUP",
            name: "出径随访",
            nodeType: "FOLLOWUP",
            sortOrder: 3,
            responsibleRole: "随访护士",
            accountableRole: "护理组长",
            timeWindowMinutes: 120,
            terminalFlag: true,
            origin: "INHERITED",
          },
        ],
        mergedEdges: [],
        traceId: "trace-inheritance",
      };
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };

      const user = await openPathwayDrawer();
      await user.click(screen.getByRole("tab", { name: /继承差异/ }));

      expect(screen.getByText("父级模板：pt-standard")).toBeInTheDocument();
      expect(screen.getAllByText("覆盖").length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText("新增").length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText("禁用")).toBeInTheDocument();
      expect(screen.getAllByText("30").length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText("康复指导").length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText("继承")).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径关键字段提供简短示例与占位帮助",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });

      expect(within(dialog).getByPlaceholderText("如 PATH.CARDIO.REVIEW")).toBeInTheDocument();
      expect(within(dialog).getByPlaceholderText("如 CARDIO 或 ICD10-I63")).toBeInTheDocument();
      expect(within(dialog).getByPlaceholderText("如 院内已审核路径制度 2026")).toBeInTheDocument();

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加流转边/ }));

      expect(within(dialog).getByPlaceholderText("如 N1，可改为 ASSESS")).toBeInTheDocument();
      expect(within(dialog).getByPlaceholderText("如 入径评估")).toBeInTheDocument();
      expect(within(dialog).getByPlaceholderText("如 专科医生")).toBeInTheDocument();
      expect(within(dialog).getByPlaceholderText("如 PATH.TIME.ASSESS")).toBeInTheDocument();
      expect(
        within(dialog).getByPlaceholderText("如 E1，可改为 EDGE.ASSESS.FOLLOWUP"),
      ).toBeInTheDocument();
      expect(
        within(dialog).getByPlaceholderText("如 observations[].valueNumeric"),
      ).toBeInTheDocument();
      expect(within(dialog).getByPlaceholderText("如 true / 90 / ATC-J01C")).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "从真实 API-01 快照选择试运行路径，并展示后端返回的质量与决策证据",
    async () => {
      apiMocks.templateListData = { items: [draftTemplate], total: 1 };
      apiMocks.templateDetailData = createTemplateDetail();
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.snapshotsData = {
        items: [
          {
            snapshotId: "ctx-path-001",
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
        snapshotId: "ctx-path-001",
        status: "ACTIVE",
        packageVersion: "pkg-2026.06",
        qualityStatus: "PARTIAL",
        missingFields: [{ resourceType: "CONDITION", fieldPath: "*", severity: "WARN" }],
        mappingStatus: { "OBSERVATION:obs-1:code:HB": "MAPPED" },
        resources: { patient: { patientId: "P-001" }, observations: [{ code: "HB", value: 86 }] },
        createdAt: "2026-06-02T08:00:00Z",
        traceId: "trace-ctx",
      };
      apiMocks.simulatePathway.mockResolvedValue({
        templateId: "pt-path-1",
        snapshotId: "ctx-path-001",
        nodeTrajectory: ["ASSESS", "FOLLOWUP"],
        finalStatus: "COMPLETED",
        contextQualityStatus: "PARTIAL",
        missingFields: [{ resourceType: "CONDITION", fieldPath: "*", severity: "WARN" }],
        mappingStatus: { "OBSERVATION:obs-1:code:HB": "MAPPED" },
        contextResourceCounts: { patient: 1, observations: 1 },
        traceId: "trace-sim",
      });

      const user = await openPathwayDrawer();
      await user.click(screen.getByRole("tab", { name: /真实快照试运行/ }));

      await user.type(screen.getByLabelText("患者 ID"), "P-001");
      await user.type(screen.getByLabelText("就诊 ID"), "E-001");
      await user.click(screen.getByRole("button", { name: /读取真实快照/ }));
      await user.click(await screen.findByRole("button", { name: /ctx-path-001/ }));
      await user.click(screen.getByRole("button", { name: /使用该快照试运行/ }));

      await waitFor(() =>
        expect(apiMocks.simulatePathway).toHaveBeenCalledWith({
          packageVersion: "pkg-2026.06",
          snapshotId: "ctx-path-001",
          startNodeCode: "ASSESS",
        }),
      );
      expect(await screen.findByText("快照质量：PARTIAL")).toBeInTheDocument();
      expect(screen.getByText("OBSERVATION:obs-1:code:HB")).toBeInTheDocument();
      expect(screen.getAllByText("ASSESS").length).toBeGreaterThan(0);
      expect(screen.getAllByText("FOLLOWUP").length).toBeGreaterThan(0);
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "队列回放按快照选择顺序发送回放参数，并展示每一步轨迹",
    async () => {
      apiMocks.templateListData = { items: [draftTemplate], total: 1 };
      apiMocks.templateDetailData = createTemplateDetail();
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.snapshotsData = {
        items: [
          {
            snapshotId: "ctx-path-001",
            patientId: "P-001",
            encounterId: "E-001",
            status: "ACTIVE",
            qualityStatus: "COMPLETE",
            createdAt: "2026-06-02T08:00:00Z",
          },
          {
            snapshotId: "ctx-path-002",
            patientId: "P-001",
            encounterId: "E-001",
            status: "ACTIVE",
            qualityStatus: "PARTIAL",
            createdAt: "2026-06-02T09:00:00Z",
          },
        ],
        total: 2,
      };
      apiMocks.snapshotDetailData = {
        snapshotId: "ctx-path-001",
        status: "ACTIVE",
        packageVersion: "pkg-2026.06",
        qualityStatus: "COMPLETE",
        missingFields: [],
        mappingStatus: {},
        resources: { patient: { patientId: "P-001" } },
        createdAt: "2026-06-02T08:00:00Z",
        traceId: "trace-ctx",
      };
      apiMocks.simulatePathway.mockResolvedValue({
        templateId: "pt-path-1",
        simulationMode: "QUEUE_REPLAY",
        nodeTrajectory: ["ASSESS", "FOLLOWUP"],
        finalStatus: "COMPLETED",
        contextQualityStatus: "PARTIAL",
        missingFields: [],
        mappingStatus: {},
        contextResourceCounts: { patient: 1 },
        replaySteps: [
          {
            snapshotId: "ctx-path-001",
            nodeTrajectory: ["ASSESS"],
            finalStatus: "RUNNING",
          },
          {
            snapshotId: "ctx-path-002",
            nodeTrajectory: ["ASSESS", "FOLLOWUP"],
            finalStatus: "COMPLETED",
          },
        ],
        traceId: "trace-replay",
      });

      const user = await openPathwayDrawer();
      await user.click(screen.getByRole("tab", { name: /真实快照试运行/ }));

      await user.type(screen.getByLabelText("患者 ID"), "P-001");
      await user.type(screen.getByLabelText("就诊 ID"), "E-001");
      await user.click(screen.getByRole("button", { name: /读取真实快照/ }));
      await user.click(screen.getByText("队列回放"));
      await user.click(await screen.findByRole("button", { name: /ctx-path-001/ }));
      await user.click(await screen.findByRole("button", { name: /ctx-path-002/ }));
      await user.click(screen.getByRole("button", { name: /执行队列回放/ }));

      await waitFor(() =>
        expect(apiMocks.simulatePathway).toHaveBeenCalledWith({
          packageVersion: "pkg-2026.06",
          replaySnapshotIds: ["ctx-path-001", "ctx-path-002"],
          simulationMode: "QUEUE_REPLAY",
          startNodeCode: "ASSESS",
        }),
      );
      expect(await screen.findByText("模式：QUEUE_REPLAY")).toBeInTheDocument();
      expect(screen.getAllByText("ctx-path-001").length).toBeGreaterThan(0);
      expect(screen.getAllByText("ctx-path-002").length).toBeGreaterThan(0);
      expect(screen.getByText("ASSESS → FOLLOWUP")).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径发布使用 7 步流展示影响摘要，并携带 impactDigest 与审核理由进入灰度发布",
    async () => {
      apiMocks.templateListData = { items: [draftTemplate], total: 1 };
      apiMocks.templateDetailData = createTemplateDetail();
      apiMocks.templateImpactData = {
        templateId: "pt-path-1",
        analysisStatus: "COMPLETE",
        affectedPatientPathways: 2,
        nodeCount: 2,
        edgeCount: 1,
        timedNodeCount: 2,
        terminalNodeCount: 1,
        canaryPercent: 10,
        impactDigest: "sha256:path-impact",
        releaseEvidence: [
          "拓扑节点 2 个，边 1 条，终止节点 1 个",
          "灰度发布默认 10%，全量前必须保留本次 impactDigest，可按审计记录回滚到上一版本",
        ],
        traceId: "trace-impact",
      };
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.publishTemplate.mockResolvedValue({
        templateId: "pt-path-1",
        status: "PUBLISHED",
        releaseStep: "canary_release",
        canaryPercent: 10,
        impactDigest: "sha256:path-impact",
        analysisStatus: "COMPLETE",
        releaseEvidence: [],
        traceId: "trace-publish",
      });

      const user = await openPathwayDrawer();
      await user.click(screen.getByRole("tab", { name: /7 步流发布/ }));

      expect(screen.getByText("sha256:path-impact")).toBeInTheDocument();
      expect(screen.getByText("灰度发布默认 10%")).toBeInTheDocument();
      await user.type(
        screen.getByLabelText("发布审核说明"),
        "已核查影响摘要和随访交接，先灰度 10%。",
      );
      await user.click(screen.getByRole("button", { name: /提交审核并进入灰度发布/ }));

      await waitFor(() =>
        expect(apiMocks.publishTemplate).toHaveBeenCalledWith({
          templateId: "pt-path-1",
          packageVersion: "pkg-2026.06",
          impactDigest: "sha256:path-impact",
          reason: "已核查影响摘要和随访交接，先灰度 10%。",
        }),
      );
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "内容已审核的路径显示待激活状态，并可院级确认全量激活",
    async () => {
      apiMocks.templateListData = { items: [publishedTemplate], total: 1 };
      apiMocks.templateDetailData = {
        ...createTemplateDetail(),
        template: publishedTemplate,
        deploymentStatus: "PUBLISHED",
      };
      apiMocks.templateImpactData = {
        templateId: "pt-path-published",
        analysisStatus: "COMPLETE",
        affectedPatientPathways: 1,
        nodeCount: 2,
        edgeCount: 1,
        timedNodeCount: 2,
        terminalNodeCount: 1,
        canaryPercent: 10,
        impactDigest: "sha256:path-full",
        releaseEvidence: ["GRAY 灰度发布"],
        traceId: "trace-impact",
      };
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.fullRolloutTemplate.mockResolvedValue({
        templateId: "pt-path-published",
        status: "PUBLISHED",
        releaseStep: "full_rollout",
        canaryPercent: 100,
        impactDigest: "sha256:path-full",
        analysisStatus: "COMPLETE",
        releaseEvidence: ["FULL 全量激活"],
        traceId: "trace-full",
      });

      const user = await openPathwayDrawer();
      expect(screen.getAllByText("内容已审核")).not.toHaveLength(0);
      expect(screen.getByText("待全量激活")).toBeInTheDocument();
      await user.click(screen.getByRole("tab", { name: /7 步流发布/ }));
      await user.type(screen.getByLabelText("发布审核说明"), "院级管理员确认全量激活");
      await user.click(screen.getByRole("button", { name: /院级确认全量激活/ }));

      await waitFor(() =>
        expect(apiMocks.fullRolloutTemplate).toHaveBeenCalledWith({
          templateId: "pt-path-published",
          packageVersion: "pkg-2026.06",
          impactDigest: "sha256:path-full",
          reason: "院级管理员确认全量激活",
        }),
      );
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "统一版本已激活时只展示运行状态，不再提供重复全量发布",
    async () => {
      apiMocks.templateListData = { items: [publishedTemplate], total: 1 };
      apiMocks.templateDetailData = {
        ...createTemplateDetail(),
        template: publishedTemplate,
        deploymentStatus: "ACTIVE",
      };

      const user = await openPathwayDrawer();
      expect(screen.getByText("运行中")).toBeInTheDocument();
      await user.click(screen.getByRole("tab", { name: /7 步流发布/ }));
      expect(screen.getByText("当前路径版本已全量生效")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /院级确认全量激活/ })).not.toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径回滚目标来自同编码已下线历史版本查询",
    async () => {
      apiMocks.templateListData = { items: [publishedTemplate], total: 1 };
      apiMocks.rollbackTemplateListData = { items: [rollbackTemplate], total: 1 };
      apiMocks.templateDetailData = {
        ...createTemplateDetail(),
        template: publishedTemplate,
        deploymentStatus: "PUBLISHED",
      };
      apiMocks.templateImpactData = {
        templateId: "pt-path-published",
        analysisStatus: "COMPLETE",
        affectedPatientPathways: 1,
        nodeCount: 2,
        edgeCount: 1,
        timedNodeCount: 2,
        terminalNodeCount: 1,
        canaryPercent: 10,
        impactDigest: "sha256:path-published-impact",
        releaseEvidence: ["灰度发布默认 10%，全量前必须保留本次 impactDigest"],
        traceId: "trace-impact",
      };
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      apiMocks.rollbackTemplate.mockResolvedValue({
        templateId: "pt-path-offline",
        status: "PUBLISHED",
        releaseStep: "evidence_rollback",
        canaryPercent: 0,
        impactDigest: "sha256:path-published-impact",
        analysisStatus: "COMPLETE",
        releaseEvidence: [],
        traceId: "trace-rollback",
      });

      const user = await openPathwayDrawer();
      await user.click(screen.getByRole("tab", { name: /7 步流发布/ }));
      await user.type(screen.getByLabelText("发布审核说明"), "灰度异常，回滚上一版本。");

      await user.click(screen.getByRole("combobox", { name: "回滚目标版本" }));
      await user.click(await screen.findByText("PATH.CARDIO.REVIEW v1.0"));
      await user.click(screen.getByRole("button", { name: /回滚到目标版本/ }));

      await waitFor(() =>
        expect(apiMocks.rollbackTemplate).toHaveBeenCalledWith({
          templateId: "pt-path-published",
          packageVersion: "pkg-2026.06",
          rollbackTargetTemplateId: "pt-path-offline",
          impactDigest: "sha256:path-published-impact",
          reason: "灰度异常，回滚上一版本。",
        }),
      );
      expect(apiMocks.templateListParams).toContainEqual({
        status: "OFFLINE",
        templateCode: "PATH.CARDIO.REVIEW",
        page: 1,
        size: 100,
      });
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "添加节点/边自动生成编码，边与起点从已建节点下拉选择",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));

      // 添加节点 → 节点编码自动填 N1
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      expect((within(dialog).getByLabelText("节点编码") as HTMLInputElement).value).toBe("N1");

      // 添加流转边 → 边编码自动填 E1
      await user.click(within(dialog).getByRole("button", { name: /添加流转边/ }));
      expect((within(dialog).getByLabelText("边编码") as HTMLInputElement).value).toBe("E1");

      // 起点为下拉选择（不是文本框），且能选到已建节点 N1
      const startNodeField = within(dialog).getByLabelText("起始节点");
      expect(startNodeField).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "图画布移动节点后将布局持久化到同一份 L3 DSL",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));

      fireEvent.keyDown(within(dialog).getByLabelText("路径节点 N1"), {
        key: "ArrowRight",
      });
      await user.click(within(dialog).getByRole("switch", { name: "专家模式" }));
      await user.click(within(dialog).getByRole("button", { name: /同步到 DSL/ }));
      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));

      const dsl = JSON.parse(
        (within(dialog).getByLabelText("路径 DSL JSON") as HTMLTextAreaElement).value,
      ) as {
        nodes: Array<{ config?: { authoringLayout?: { x: number; y: number } } }>;
      };
      expect(dsl.nodes[0].config?.authoringLayout).toEqual({ x: 16, y: 0 });
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );

  it(
    "路径画布即时提示重复节点编码和缺少终止节点",
    async () => {
      apiMocks.packagesData = { items: [specialtyPackage], total: 1 };
      const user = userEvent.setup();
      renderPathwayTemplates();

      await user.click(screen.getByRole("button", { name: /新建路径模板/ }));
      const dialog = await screen.findByRole("dialog", { name: "新建路径模板模型" });
      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));

      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      const nodeCodeInputs = within(dialog).getAllByLabelText("节点编码");
      fireEvent.change(nodeCodeInputs[1], { target: { value: "N1" } });

      expect(await screen.findByText("节点编码 N1 重复，请改为唯一编码。")).toBeInTheDocument();
      expect(screen.getByText("至少需要一个终止节点。")).toBeInTheDocument();
    },
    PATHWAY_INTERACTION_TIMEOUT_MS,
  );
});
