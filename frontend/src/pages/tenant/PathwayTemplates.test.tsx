import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import PathwayTemplates from "./PathwayTemplates";
import type {
  PathwayTemplate,
  PathwayTemplateDetailResponse,
  SpecialtyPackage,
} from "@/shared/api/hooks";

const apiMocks = vi.hoisted(() => ({
  templateListData: { items: [], total: 0 } as unknown,
  templateDetailData: null as unknown,
  packagesData: { items: [], total: 0 } as unknown,
  snapshotsData: { items: [], total: 0 } as unknown,
  snapshotDetailData: null as unknown,
  refetchList: vi.fn(),
  refetchDetail: vi.fn(),
  refetchPackages: vi.fn(),
  createPackage: vi.fn(),
  createTemplate: vi.fn(),
  publishTemplate: vi.fn(),
  simulatePathway: vi.fn(),
}));

const PATHWAY_INTERACTION_TIMEOUT_MS = 15_000;

vi.mock("@/shared/api/hooks", () => ({
  usePathwayTemplates: () => ({
    data: apiMocks.templateListData,
    isLoading: false,
    refetch: apiMocks.refetchList,
  }),
  usePathwayTemplateDetail: () => ({
    data: apiMocks.templateDetailData,
    isLoading: false,
    refetch: apiMocks.refetchDetail,
  }),
  useSpecialtyPackages: () => ({
    data: apiMocks.packagesData,
    refetch: apiMocks.refetchPackages,
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
  useSimulatePathway: () => ({
    mutateAsync: apiMocks.simulatePathway,
    isPending: false,
  }),
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
  startNodeCode: "ASSESS",
  sourceRef: "院内已审核路径制度",
  description: "路径复核配置",
};

function createTemplateDetail(): PathwayTemplateDetailResponse {
  return {
    template: draftTemplate,
    nodes: [
      {
        id: 1,
        nodeId: "node-assess",
        templateId: "pt-path-1",
        nodeCode: "ASSESS",
        name: "入径评估",
        nodeType: "ASSESSMENT",
        sortOrder: 1,
        responsibleRole: "专科医生",
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
    apiMocks.templateDetailData = null;
    apiMocks.packagesData = { items: [], total: 0 };
    apiMocks.snapshotsData = { items: [], total: 0 };
    apiMocks.snapshotDetailData = null;
    apiMocks.refetchList.mockReset();
    apiMocks.refetchDetail.mockReset();
    apiMocks.refetchPackages.mockReset();
    apiMocks.createPackage.mockReset();
    apiMocks.createTemplate.mockReset();
    apiMocks.publishTemplate.mockReset();
    apiMocks.simulatePathway.mockReset();
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
      expect(within(dialog).getByRole("tab", { name: /L3 DSL/ })).toBeInTheDocument();
      expect(
        within(dialog).queryByLabelText("生命周期节点配置 (JSON 列表)"),
      ).not.toBeInTheDocument();
      expect(
        within(dialog).queryByLabelText("拓扑流转连线配置 (JSON 列表)"),
      ).not.toBeInTheDocument();

      await user.click(within(dialog).getByRole("tab", { name: /L2 节点画布/ }));
      await user.click(within(dialog).getByRole("button", { name: /添加节点/ }));
      await user.type(within(dialog).getByLabelText("节点编码"), "ASSESS");
      await user.type(within(dialog).getByLabelText("节点名称"), "入径评估");
      await user.click(within(dialog).getByRole("button", { name: /同步到 DSL/ }));
      await user.click(within(dialog).getByRole("tab", { name: /L3 DSL/ }));
      const dslEditor = within(dialog).getByLabelText("路径 DSL JSON");
      expect((dslEditor as HTMLTextAreaElement).value).toContain('"nodeCode": "ASSESS"');

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
        pathwayPackageVersion: "pkg-2026.06",
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
});
