import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useAdvancePatientPathway,
  useContextSnapshotDetail,
  useContextSnapshots,
  useEnterPatientPathway,
  usePatientPathwayClocks,
  usePatientPathwayDetail,
  usePatientPathways,
  usePatientPathwayVariances,
  usePathwayTemplateDetail,
  usePathwayTemplates,
  usePackages,
} from "@/shared/api/hooks";

import PatientPathways from "./PatientPathways";

vi.mock("@/shared/api/hooks", () => ({
  useAdvancePatientPathway: vi.fn(),
  useContextSnapshotDetail: vi.fn(),
  useContextSnapshots: vi.fn(),
  useEnterPatientPathway: vi.fn(),
  usePatientPathwayClocks: vi.fn(),
  usePatientPathwayDetail: vi.fn(),
  usePatientPathways: vi.fn(),
  usePatientPathwayVariances: vi.fn(),
  usePathwayTemplateDetail: vi.fn(),
  usePathwayTemplates: vi.fn(),
  usePackages: vi.fn(),
}));

const mockUseAdvancePatientPathway = vi.mocked(useAdvancePatientPathway);
const mockUseContextSnapshotDetail = vi.mocked(useContextSnapshotDetail);
const mockUseContextSnapshots = vi.mocked(useContextSnapshots);
const mockUseEnterPatientPathway = vi.mocked(useEnterPatientPathway);
const mockUsePatientPathwayClocks = vi.mocked(usePatientPathwayClocks);
const mockUsePatientPathwayDetail = vi.mocked(usePatientPathwayDetail);
const mockUsePatientPathways = vi.mocked(usePatientPathways);
const mockUsePatientPathwayVariances = vi.mocked(usePatientPathwayVariances);
const mockUsePathwayTemplateDetail = vi.mocked(usePathwayTemplateDetail);
const mockUsePathwayTemplates = vi.mocked(usePathwayTemplates);
const mockUsePackages = vi.mocked(usePackages);

function renderPatientPathways() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <PatientPathways />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("PatientPathways", () => {
  const refetchPathways = vi.fn();
  const refetchDetail = vi.fn();
  const refetchClocks = vi.fn();
  const refetchVariances = vi.fn();
  const enterPathway = vi.fn();
  const advancePathway = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUsePathwayTemplates.mockReturnValue({
      data: {
        items: [
          {
            templateId: "pt-1",
            packageId: "sp-1",
            templateCode: "TPL.STROKE",
            name: "卒中急诊路径",
            diseaseCode: "STROKE",
            templateVersion: 1,
            templateLevel: "STANDARD",
            status: "PUBLISHED",
            entryMode: "MANUAL_CONFIRM",
            startNodeCode: "ASSESS",
            sourceRef: "卒中路径指南 2026",
            description: "卒中急诊路径",
          },
        ],
        total: 1,
      },
    } as unknown as ReturnType<typeof usePathwayTemplates>);
    mockUsePackages.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            packageId: "sp-1",
            tenantId: "tenant-A",
            packageCode: "PKG.STROKE",
            name: "卒中路径知识包",
            packageVersion: "2026.06",
            status: "PUBLISHED",
            description: "卒中路径知识包",
            accessPolicy: "OPEN",
            createdAt: "2026-06-01T00:00:00Z",
            createdBy: "tester",
            updatedAt: "2026-06-01T00:00:00Z",
            updatedBy: "tester",
            traceId: "trace-package",
            assetTypes: ["PATHWAY"],
            primaryAssetId: "PKG.STROKE",
            primaryAssetVersion: "2026.06",
            itemCount: 1,
            organizationScope: "tenant:tenant-A",
            applicableScope: "disease:STROKE",
            sourceRef: "卒中路径指南 2026",
          },
        ],
        total: 1,
      },
    } as unknown as ReturnType<typeof usePackages>);
    mockUseContextSnapshots.mockReturnValue({
      data: {
        items: [
          {
            snapshotId: "ctx-active-1",
            patientId: "mpi-1",
            encounterId: "enc-1",
            status: "ACTIVE",
            qualityStatus: "VALID",
          },
        ],
        page: 1,
        size: 20,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useContextSnapshots>);
    mockUseContextSnapshotDetail.mockReturnValue({
      data: {
        snapshotId: "ctx-active-1",
        status: "ACTIVE",
        packageVersion: "2026.06",
        qualityStatus: "VALID",
        missingFields: [],
        mappingStatus: {},
      },
    } as unknown as ReturnType<typeof useContextSnapshotDetail>);
    mockUsePatientPathways.mockReturnValue({
      data: {
        items: [
          {
            patientPathwayId: "pp-real-1",
            tenantId: "tenant-A",
            patientId: "mpi-1",
            encounterId: "enc-1",
            templateId: "pt-1",
            currentNodeCode: "ASSESS",
            status: "NODE_EXECUTING",
            enteredAt: "2026-06-04T00:00:00Z",
            createdAt: "2026-06-04T00:00:00Z",
            createdBy: "doctor-1",
            updatedAt: "2026-06-04T00:00:00Z",
            updatedBy: "doctor-1",
            traceId: "trace-pathway",
          },
        ],
        page: 1,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isLoading: false,
      refetch: refetchPathways,
    } as unknown as ReturnType<typeof usePatientPathways>);
    mockUsePatientPathwayDetail.mockReturnValue({
      data: {
        patientPathway: {
          patientPathwayId: "pp-real-1",
          tenantId: "tenant-A",
          patientId: "mpi-1",
          encounterId: "enc-1",
          templateId: "pt-1",
          currentNodeCode: "ASSESS",
          status: "NODE_EXECUTING",
          enteredAt: "2026-06-04T00:00:00Z",
          traceId: "trace-pathway",
        },
        milestoneStatuses: [
          {
            milestoneId: "milestone-assess",
            phaseCode: "EMERGENCY",
            phaseName: "急诊",
            milestoneCode: "M-ASSESS",
            name: "入径评估",
            dayOffset: 0,
            expectedOffsetMinutes: 30,
            nodeCodes: ["ASSESS"],
            status: "CURRENT",
            expectedAt: "2026-06-04T00:30:00Z",
          },
        ],
        variances: [
          {
            varianceId: "var-1",
            patientPathwayId: "pp-real-1",
            nodeCode: "ASSESS",
            varianceType: "CLINICAL",
            reasonCode: "CLINICAL_ESCALATION",
            reason: "影像检查发现高危指征",
            responsibleRole: "主管医师",
            resolutionDecision: "REENTER",
            resolutionAction: "转入卒中绿色通道",
            continueNodeCode: "FOLLOWUP",
            createdAt: "2026-06-04T01:00:00Z",
            traceId: "trace-var-1",
          },
        ],
        clocks: [
          {
            clockId: "clock-1",
            patientPathwayId: "pp-real-1",
            nodeCode: "ASSESS",
            metricCode: "STROKE.TIME_TO_CT",
            startedAt: "2026-06-04T00:00:00Z",
            dueAt: "2026-06-04T00:30:00Z",
            baselineEvent: "ADMISSION",
            baselineAt: "2026-06-04T00:00:00Z",
            minDueAt: "2026-06-04T00:10:00Z",
            targetDueAt: "2026-06-04T00:30:00Z",
            maxDueAt: "2026-06-04T01:00:00Z",
            escalationLevel: "QUALITY_RECORD",
            status: "TIMEOUT",
            traceId: "trace-clock-1",
          },
        ],
        traceId: "trace-detail-1",
      },
      refetch: refetchDetail,
    } as unknown as ReturnType<typeof usePatientPathwayDetail>);
    mockUsePathwayTemplateDetail.mockReturnValue({
      data: {
        template: {
          templateId: "pt-1",
          packageId: "sp-1",
          templateCode: "TPL.STROKE",
          name: "卒中急诊路径",
          diseaseCode: "STROKE",
          templateVersion: 1,
          templateLevel: "STANDARD",
          status: "PUBLISHED",
          entryMode: "MANUAL_CONFIRM",
          startNodeCode: "ASSESS",
          sourceRef: "卒中路径指南 2026",
          description: "卒中急诊路径",
        },
        milestones: [
          {
            milestoneId: "milestone-assess",
            templateId: "pt-1",
            phaseCode: "EMERGENCY",
            phaseName: "急诊",
            milestoneCode: "M-ASSESS",
            name: "入径评估",
            dayOffset: 0,
            expectedOffsetMinutes: 30,
            sortOrder: 1,
          },
        ],
        nodes: [
          {
            nodeId: "node-assess",
            templateId: "pt-1",
            nodeCode: "ASSESS",
            name: "入径评估",
            nodeType: "ASSESSMENT",
            milestoneCode: "M-ASSESS",
            sortOrder: 10,
            terminalFlag: false,
          },
          {
            nodeId: "node-followup",
            templateId: "pt-1",
            nodeCode: "FOLLOWUP",
            name: "随访复评",
            nodeType: "FOLLOWUP",
            sortOrder: 20,
            terminalFlag: true,
          },
        ],
        edges: [
          {
            edgeId: "edge-assess-followup",
            templateId: "pt-1",
            edgeCode: "EDGE.ASSESS.FOLLOWUP",
            fromNodeCode: "ASSESS",
            toNodeCode: "FOLLOWUP",
            edgeType: "DEFAULT",
            priority: 10,
          },
        ],
        metricBindings: [],
        traceId: "trace-template-1",
      },
    } as unknown as ReturnType<typeof usePathwayTemplateDetail>);
    mockUsePatientPathwayClocks.mockReturnValue({
      data: [
        {
          clockId: "clock-1",
          patientPathwayId: "pp-real-1",
          nodeCode: "ASSESS",
          metricCode: "STROKE.TIME_TO_CT",
          startedAt: "2026-06-04T00:00:00Z",
          dueAt: "2026-06-04T00:30:00Z",
          baselineEvent: "ADMISSION",
          baselineAt: "2026-06-04T00:00:00Z",
          minDueAt: "2026-06-04T00:10:00Z",
          targetDueAt: "2026-06-04T00:30:00Z",
          maxDueAt: "2026-06-04T01:00:00Z",
          escalationLevel: "QUALITY_RECORD",
          status: "TIMEOUT",
          traceId: "trace-clock-1",
        },
      ],
      refetch: refetchClocks,
    } as unknown as ReturnType<typeof usePatientPathwayClocks>);
    mockUsePatientPathwayVariances.mockReturnValue({
      data: [
        {
          varianceId: "var-1",
          patientPathwayId: "pp-real-1",
          nodeCode: "ASSESS",
          varianceType: "CLINICAL",
          reasonCode: "CLINICAL_ESCALATION",
          reason: "影像检查发现高危指征",
          responsibleRole: "主管医师",
          resolutionDecision: "REENTER",
          resolutionAction: "转入卒中绿色通道",
          continueNodeCode: "FOLLOWUP",
          createdAt: "2026-06-04T01:00:00Z",
          traceId: "trace-var-1",
        },
      ],
      refetch: refetchVariances,
    } as unknown as ReturnType<typeof usePatientPathwayVariances>);
    enterPathway.mockResolvedValue({ traceId: "trace-enter-1" });
    mockUseEnterPatientPathway.mockReturnValue({
      mutateAsync: enterPathway,
      isPending: false,
    } as unknown as ReturnType<typeof useEnterPatientPathway>);
    advancePathway.mockResolvedValue({
      patientPathwayId: "pp-real-1",
      previousNodeCode: "ASSESS",
      nextNodeCode: "FOLLOWUP",
      status: "NODE_EXECUTING",
      traceId: "trace-advance-1",
    });
    mockUseAdvancePatientPathway.mockReturnValue({
      mutateAsync: advancePathway,
      isPending: false,
    } as unknown as ReturnType<typeof useAdvancePatientPathway>);
  });

  it("renders real patient pathway rows from the backend page instead of session-only state", () => {
    renderPatientPathways();

    expect(mockUsePatientPathways).toHaveBeenCalledWith({
      patientId: undefined,
      page: 1,
      size: 10,
    });
    expect(screen.getByText("pp-real-1")).toBeInTheDocument();
    expect(screen.getByText("mpi-1")).toBeInTheDocument();
    expect(screen.queryByText("暂无患者路径实例")).not.toBeInTheDocument();
  });

  it("enters a pathway from an ACTIVE context snapshot without manual patient identifiers", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理患者入径/ }));
    expect(screen.queryByLabelText(/选择患者 ID/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/关联就诊 ID/)).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("按患者 ID 查询快照"), "mpi-1");
    await user.click(screen.getByRole("button", { name: "选择 ctx-active-1" }));
    await user.click(screen.getByRole("combobox", { name: "选择受控专病路径模板" }));
    await user.click(await screen.findByText("卒中急诊路径 (v1.0) · 人工确认入径"));
    expect(screen.getByText("入径模式：人工确认入径")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "OK" }));

    await waitFor(() => {
      expect(enterPathway).toHaveBeenCalledWith({
        contextSnapshotId: "ctx-active-1",
        templateId: "pt-1",
        startNodeCode: undefined,
        packageVersion: "2026.06",
      });
    });
  }, 15_000);

  it("shows backend clocks and variance evidence in the pathway detail drawer", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));

    expect(mockUsePatientPathwayDetail).toHaveBeenCalledWith("pp-real-1");
    expect(screen.getByText("患者临床路径推进与解释追溯控制台")).toBeInTheDocument();
    expect(screen.getByText("clock-1")).toBeInTheDocument();
    expect(screen.getByText(/STROKE.TIME_TO_CT/)).toBeInTheDocument();
    expect(screen.getByText("急诊 / 第 0 天 / M-ASSESS")).toBeInTheDocument();
    expect(screen.getByText("当前里程碑")).toBeInTheDocument();
    expect(screen.getByText("节点: ASSESS")).toBeInTheDocument();
    expect(screen.getAllByText("已超时").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/质控记录/).length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: /查看变异事实与审计线索/ }));

    expect(refetchVariances).toHaveBeenCalled();
    expect(screen.getByText("var-1")).toBeInTheDocument();
    expect(screen.getByText("影像检查发现高危指征")).toBeInTheDocument();
  });

  it("advances the current node through the backend mutation and refreshes facts", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));
    await user.click(screen.getByRole("combobox", { name: "指定流转目标节点" }));
    await user.click(await screen.findByText("随访复评 (FOLLOWUP)"));
    await user.click(screen.getByRole("button", { name: /完成当前节点并推进/ }));

    await waitFor(() => {
      expect(advancePathway).toHaveBeenCalledWith({
        patientPathwayId: "pp-real-1",
        packageVersion: "2026.06",
        eventType: "COMPLETE",
        currentNodeCode: "ASSESS",
        requestedNextNodeCode: "FOLLOWUP",
      });
    });
    expect(refetchDetail).toHaveBeenCalled();
    expect(refetchClocks).toHaveBeenCalled();
    expect(refetchVariances).toHaveBeenCalled();
    expect(refetchPathways).toHaveBeenCalled();
  });

  it("records a variance reason through the backend mutation", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));
    await user.click(screen.getByRole("tab", { name: /登记变异/ }));
    await user.click(screen.getByRole("combobox", { name: "变异分类" }));
    await user.click(await screen.findByText("临床原因"));
    await user.type(screen.getByPlaceholderText("如 CLINICAL_ESCALATION"), "CLINICAL_ESCALATION");
    await user.type(screen.getByPlaceholderText("如 主管医师"), "主管医师");
    await user.click(screen.getByRole("combobox", { name: "处置决策" }));
    await user.click(await screen.findByText("再次进入路径"));
    await user.click(screen.getByRole("combobox", { name: "再入径节点" }));
    await user.click(await screen.findByText("随访复评 (FOLLOWUP)"));
    await user.type(
      screen.getByPlaceholderText("请输入经医师确认的变异事实说明"),
      "患者突发高危指标",
    );
    await user.type(screen.getByPlaceholderText("请输入已确认的处置动作"), "转入专科会诊");
    await user.click(screen.getByRole("button", { name: /提交变异决策/ }));

    await waitFor(() => {
      expect(advancePathway).toHaveBeenCalledWith({
        patientPathwayId: "pp-real-1",
        packageVersion: "2026.06",
        eventType: "VARIANCE",
        currentNodeCode: "ASSESS",
        requestedNextNodeCode: "FOLLOWUP",
        varianceType: "CLINICAL",
        varianceReasonCode: "CLINICAL_ESCALATION",
        varianceReason: "患者突发高危指标",
        responsibleRole: "主管医师",
        resolutionDecision: "REENTER",
        resolutionAction: "转入专科会诊",
      });
    });
    expect(refetchDetail).toHaveBeenCalled();
    expect(refetchClocks).toHaveBeenCalled();
    expect(refetchVariances).toHaveBeenCalled();
    expect(refetchPathways).toHaveBeenCalled();
  }, 15_000);

  it("shows an honest error state when the patient pathway list cannot be read", () => {
    mockUsePatientPathways.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          data: {
            code: "ENG-PATHWAY-READ",
            detail: "患者路径列表读取失败",
            traceId: "trace-pathway-error",
          },
        },
      },
      refetch: refetchPathways,
    } as unknown as ReturnType<typeof usePatientPathways>);

    renderPatientPathways();

    expect(screen.getByText("患者路径读取失败")).toBeInTheDocument();
    expect(screen.getByText(/患者路径列表读取失败/)).toBeInTheDocument();
    expect(screen.getByText(/trace-pathway-error/)).toBeInTheDocument();
  });

  it("shows a forbidden state when org data scope denies patient pathways", () => {
    mockUsePatientPathways.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: {
        response: {
          data: {
            code: "ENG-BASE-003",
            detail: "数据范围权限不足",
            traceId: "trace-scope-denied",
          },
        },
      },
      refetch: refetchPathways,
    } as unknown as ReturnType<typeof usePatientPathways>);

    renderPatientPathways();

    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
    expect(screen.getByText(/数据范围权限不足/)).toBeInTheDocument();
    expect(screen.getByText(/trace-scope-denied/)).toBeInTheDocument();
  });
});
