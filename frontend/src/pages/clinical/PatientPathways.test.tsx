import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useAdvancePatientPathway,
  useEnterPatientPathway,
  usePatientPathwayClocks,
  usePatientPathwayDetail,
  usePatientPathways,
  usePatientPathwayVariances,
  usePathwayTemplateDetail,
  usePathwayTemplates,
  useSpecialtyPackages,
} from "@/shared/api/hooks";

import PatientPathways from "./PatientPathways";

vi.mock("@/shared/api/hooks", () => ({
  useAdvancePatientPathway: vi.fn(),
  useEnterPatientPathway: vi.fn(),
  usePatientPathwayClocks: vi.fn(),
  usePatientPathwayDetail: vi.fn(),
  usePatientPathways: vi.fn(),
  usePatientPathwayVariances: vi.fn(),
  usePathwayTemplateDetail: vi.fn(),
  usePathwayTemplates: vi.fn(),
  useSpecialtyPackages: vi.fn(),
}));

const mockUseAdvancePatientPathway = vi.mocked(useAdvancePatientPathway);
const mockUseEnterPatientPathway = vi.mocked(useEnterPatientPathway);
const mockUsePatientPathwayClocks = vi.mocked(usePatientPathwayClocks);
const mockUsePatientPathwayDetail = vi.mocked(usePatientPathwayDetail);
const mockUsePatientPathways = vi.mocked(usePatientPathways);
const mockUsePatientPathwayVariances = vi.mocked(usePatientPathwayVariances);
const mockUsePathwayTemplateDetail = vi.mocked(usePathwayTemplateDetail);
const mockUsePathwayTemplates = vi.mocked(usePathwayTemplates);
const mockUseSpecialtyPackages = vi.mocked(useSpecialtyPackages);

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
            startNodeCode: "ASSESS",
            sourceRef: "卒中路径指南 2026",
            description: "卒中急诊路径",
          },
        ],
        total: 1,
      },
    } as unknown as ReturnType<typeof usePathwayTemplates>);
    mockUseSpecialtyPackages.mockReturnValue({
      data: {
        items: [
          {
            packageId: "sp-1",
            packageCode: "PKG.STROKE",
            diseaseCode: "STROKE",
            name: "卒中专病包",
            packageVersion: "2026.06",
            status: "PUBLISHED",
            sourceRef: "卒中路径指南 2026",
            description: "卒中专病包",
          },
        ],
        total: 1,
      },
    } as unknown as ReturnType<typeof useSpecialtyPackages>);
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
        variances: [
          {
            varianceId: "var-1",
            patientPathwayId: "pp-real-1",
            nodeCode: "ASSESS",
            varianceType: "MEDICAL",
            reason: "影像检查发现高危指征",
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
            status: "OVERDUE",
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
          startNodeCode: "ASSESS",
          sourceRef: "卒中路径指南 2026",
          description: "卒中急诊路径",
        },
        nodes: [
          {
            nodeId: "node-assess",
            templateId: "pt-1",
            nodeCode: "ASSESS",
            name: "入径评估",
            nodeType: "ASSESSMENT",
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
          status: "OVERDUE",
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
          varianceType: "MEDICAL",
          reason: "影像检查发现高危指征",
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

  it("shows backend clocks and variance evidence in the pathway detail drawer", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));

    expect(mockUsePatientPathwayDetail).toHaveBeenCalledWith("pp-real-1");
    expect(screen.getByText("患者临床路径推进与解释追溯控制台")).toBeInTheDocument();
    expect(screen.getByText("clock-1")).toBeInTheDocument();
    expect(screen.getByText(/STROKE.TIME_TO_CT/)).toBeInTheDocument();

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
    await user.click(screen.getByRole("combobox", { name: "变异偏离类型" }));
    await user.click(await screen.findByText("MEDICAL (医学指征原因)"));
    await user.type(
      screen.getByPlaceholderText("请输入经医师确认的变异事实说明"),
      "患者突发高危指标",
    );
    await user.type(screen.getByPlaceholderText("请输入已确认的处置动作"), "转入专科会诊");
    await user.click(screen.getByRole("button", { name: /提交路径变异并强制推进/ }));

    await waitFor(() => {
      expect(advancePathway).toHaveBeenCalledWith({
        patientPathwayId: "pp-real-1",
        packageVersion: "2026.06",
        eventType: "VARIANCE",
        currentNodeCode: "ASSESS",
        requestedNextNodeCode: undefined,
        varianceType: "MEDICAL",
        varianceReason: "患者突发高危指标",
        resolutionAction: "转入专科会诊",
      });
    });
    expect(refetchDetail).toHaveBeenCalled();
    expect(refetchClocks).toHaveBeenCalled();
    expect(refetchVariances).toHaveBeenCalled();
    expect(refetchPathways).toHaveBeenCalled();
  });

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
