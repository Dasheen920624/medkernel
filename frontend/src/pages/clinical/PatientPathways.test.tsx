import { render, screen, waitFor, within } from "@testing-library/react";
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
  usePathwayEntryCandidates,
  usePathwayTemplateDetail,
  useSecurityProfile,
} from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

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
  usePathwayEntryCandidates: vi.fn(),
  usePathwayTemplateDetail: vi.fn(),
  useSecurityProfile: vi.fn(),
}));

const mockUseAdvancePatientPathway = vi.mocked(useAdvancePatientPathway);
const mockUseContextSnapshotDetail = vi.mocked(useContextSnapshotDetail);
const mockUseContextSnapshots = vi.mocked(useContextSnapshots);
const mockUseEnterPatientPathway = vi.mocked(useEnterPatientPathway);
const mockUsePatientPathwayClocks = vi.mocked(usePatientPathwayClocks);
const mockUsePatientPathwayDetail = vi.mocked(usePatientPathwayDetail);
const mockUsePatientPathways = vi.mocked(usePatientPathways);
const mockUsePatientPathwayVariances = vi.mocked(usePatientPathwayVariances);
const mockUsePathwayEntryCandidates = vi.mocked(usePathwayEntryCandidates);
const mockUsePathwayTemplateDetail = vi.mocked(usePathwayTemplateDetail);
const mockUseSecurityProfile = vi.mocked(useSecurityProfile);

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
    useEvidenceDetailsStore.setState({ enabled: false });
    mockUseSecurityProfile.mockReturnValue({
      data: {
        permissions: [
          { code: "pathway.read", dimension: "ACTION", target: "pathway", displayName: "查看路径" },
          {
            code: "system.debug",
            dimension: "ACTION",
            target: "system",
            displayName: "证据详情",
          },
        ],
        roles: [{ code: "clinical-user", displayName: "临床使用者", source: "TEST" }],
        menuKeys: ["patient-pathways", "runtime-diagnostics"],
        environmentKeys: ["production"],
        dataScope: { tenantId: "tenant-A" },
      },
    } as unknown as ReturnType<typeof useSecurityProfile>);
    mockUsePathwayEntryCandidates.mockReturnValue({
      data: {
        contextSnapshotId: "ctx-active-1",
        triggerPoint: "patient-view",
        candidates: [
          {
            templateId: "pt-1",
            templateCode: "TPL.STROKE",
            name: "卒中急诊路径",
            diseaseCode: "STROKE",
          },
        ],
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof usePathwayEntryCandidates>);
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
        runtimeReleaseId: "runtime-release-2026-06",
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
        outcomeBindings: [
          {
            bindingId: "outcome-1",
            templateId: "pt-1",
            scope: "TEMPLATE",
            refCode: "TEMPLATE",
            indicatorCode: "STROKE.OUTCOME.DOOR_TO_CT",
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

  it("renders patient pathway rows without exposing runtime identifiers by default", () => {
    renderPatientPathways();

    expect(mockUsePatientPathways).toHaveBeenCalledWith({
      patientId: undefined,
      page: 1,
      size: 10,
    });
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.getByText("已关联患者与就诊")).toBeInTheDocument();
    expect(screen.queryByText("pp-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("mpi-1")).not.toBeInTheDocument();
    expect(screen.queryByText("enc-1")).not.toBeInTheDocument();
    expect(screen.queryByText("暂无患者路径实例")).not.toBeInTheDocument();
  });

  it("reveals patient pathway identifiers only after evidence details are enabled", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("pp-real-1")).toBeInTheDocument();
    expect(screen.getByText("mpi-1")).toBeInTheDocument();
    expect(screen.getByText("enc-1")).toBeInTheDocument();
  });

  it("does not reveal patient pathway identifiers when the role lacks evidence-detail permission", () => {
    useEvidenceDetailsStore.setState({ enabled: true });
    mockUseSecurityProfile.mockReturnValue({
      data: {
        permissions: [
          { code: "pathway.read", dimension: "ACTION", target: "pathway", displayName: "查看路径" },
        ],
        roles: [{ code: "clinical-user", displayName: "临床使用者", source: "TEST" }],
        menuKeys: ["patient-pathways"],
        environmentKeys: ["production"],
        dataScope: { tenantId: "tenant-A" },
      },
    } as unknown as ReturnType<typeof useSecurityProfile>);

    renderPatientPathways();

    expect(screen.queryByRole("switch", { name: "证据详情" })).not.toBeInTheDocument();
    expect(screen.getByText("已关联患者与就诊")).toBeInTheDocument();
    expect(screen.queryByText("pp-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("mpi-1")).not.toBeInTheDocument();
    expect(screen.queryByText("enc-1")).not.toBeInTheDocument();
  });

  it("associates the patient pathway list filter with its visible label", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.type(screen.getByLabelText("患者检索"), "mpi-1");

    expect(screen.getByLabelText("患者检索")).toHaveValue("mpi-1");
  });

  it("loads only runtime-release pathway candidates for the selected snapshot", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理患者入径/ }));
    const patientInputs = screen.getAllByPlaceholderText("输入姓名、门急诊号或院内患者编号");
    await user.type(patientInputs[patientInputs.length - 1], "mpi-1");
    await user.click(screen.getByRole("button", { name: "选择第 1 个临床快照" }));

    expect(mockUsePathwayEntryCandidates).toHaveBeenLastCalledWith("ctx-active-1", "patient-view");
    expect(
      await screen.findByText("已读取 1 条当前机构生效候选路径，确认后才会入径。"),
    ).toBeInTheDocument();
  });

  it("associates entry modal patient and encounter controls with visible labels", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理患者入径/ }));
    const dialog = screen.getByRole("dialog", { name: "办理患者临床路径准入" });

    await user.type(within(dialog).getByLabelText("患者信息"), "mpi-1");
    await user.type(within(dialog).getByLabelText("就诊信息"), "enc-1");

    expect(within(dialog).getByLabelText("患者信息")).toHaveValue("mpi-1");
    expect(within(dialog).getByLabelText("就诊信息")).toHaveValue("enc-1");
  });

  it("enters a pathway from an ACTIVE context snapshot without manual patient identifiers", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));
    await user.click(screen.getByRole("button", { name: /办理患者入径/ }));
    expect(screen.queryByLabelText(/选择患者 ID/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/关联就诊 ID/)).not.toBeInTheDocument();

    const patientInputs = screen.getAllByPlaceholderText("输入姓名、门急诊号或院内患者编号");
    await user.type(patientInputs[patientInputs.length - 1], "mpi-1");
    await user.click(screen.getByRole("button", { name: "选择 ctx-active-1" }));
    await user.click(screen.getByRole("combobox", { name: "选择当前运行候选路径" }));
    expect(
      await screen.findByRole("option", { name: "卒中急诊路径 · STROKE" }),
    ).toBeInTheDocument();
    await user.click(await screen.findByText("卒中急诊路径 · STROKE"));
    expect(
      screen.getByText("已读取 1 条当前机构生效候选路径，确认后才会入径。"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "OK" }));

    await waitFor(() => {
      expect(enterPathway).toHaveBeenCalledWith({
        contextSnapshotId: "ctx-active-1",
        triggerPoint: "patient-view",
        templateId: "pt-1",
        startNodeCode: undefined,
      });
    });
    expect(await screen.findByText("患者已入径，路径列表已刷新")).toBeInTheDocument();
    expect(screen.queryByText(/患者 mpi-1 入径成功/)).not.toBeInTheDocument();
    expect(screen.queryByText(/trace-enter-1/)).not.toBeInTheDocument();
  }, 15_000);

  it("shows pathway clocks and variance evidence in the pathway detail drawer", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));

    expect(mockUsePatientPathwayDetail).toHaveBeenCalledWith("pp-real-1");
    expect(screen.getByRole("dialog", { name: "患者路径推进与解释追溯" })).toBeInTheDocument();
    expect(screen.queryByText("clock-1")).not.toBeInTheDocument();
    expect(screen.queryByText(/STROKE.TIME_TO_CT/)).not.toBeInTheDocument();
    expect(screen.getByText("急诊 / 第 0 天")).toBeInTheDocument();
    expect(screen.getByText("当前里程碑")).toBeInTheDocument();
    expect(screen.getByText("已绑定 1 个路径环节")).toBeInTheDocument();
    expect(screen.getAllByText("已超时").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/质控记录/).length).toBeGreaterThan(0);
    expect(screen.getByRole("columnheader", { name: "结局指标身份" })).toBeInTheDocument();
    expect(screen.getAllByText("全路径").length).toBeGreaterThan(0);
    expect(screen.queryByText("全模板")).not.toBeInTheDocument();
    expect(screen.getByText(/系统会按临床路径出边计算下一步/)).toBeInTheDocument();
    expect(screen.queryByText(/系统会按模板出边/)).not.toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "指标编码" })).not.toBeInTheDocument();
    expect(screen.getAllByText("2026年06月04日 08:00").length).toBeGreaterThan(0);
    expect(screen.queryByText(/6\/4\/2026/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /查看变异事实与审计线索/ }));

    expect(refetchVariances).toHaveBeenCalled();
    expect(screen.queryByText("var-1")).not.toBeInTheDocument();
    expect(screen.getByText("影像检查发现高危指征")).toBeInTheDocument();
    expect(screen.getByText("2026年06月04日 09:00")).toBeInTheDocument();
  });

  it("uses evaluation indicator wording for pathway clock evidence", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));
    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));

    expect(screen.getByText("关联评价指标: STROKE.TIME_TO_CT")).toBeInTheDocument();
    expect(screen.queryByText(/关联质控指标/)).not.toBeInTheDocument();
  });

  it("shows the full pathway graph as a doctor read-only view with the current node highlighted", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));

    const graph = screen.getByRole("region", { name: "医生只读路径图" });
    expect(within(graph).getByText("卒中急诊路径")).toBeInTheDocument();
    expect(within(graph).getByText("当前患者位置")).toBeInTheDocument();
    expect(within(graph).getByText("入径评估")).toBeInTheDocument();
    expect(within(graph).getByText("随访复评")).toBeInTheDocument();
    expect(within(graph).getByText("标准流转：入径评估 → 随访复评")).toBeInTheDocument();
    expect(within(graph).getByLabelText("路径节点 入径评估 当前节点")).toBeInTheDocument();
    expect(
      within(graph).getByText("已完成/当前/待执行只读展示，不自动开立或修改医嘱。"),
    ).toBeInTheDocument();
    expect(within(graph).queryByRole("button", { name: /删除/ })).not.toBeInTheDocument();
  });

  it("advances the current node through the service mutation and refreshes facts", async () => {
    const user = userEvent.setup();
    renderPatientPathways();

    await user.click(screen.getByRole("button", { name: /办理推进与解释追溯/ }));
    await user.click(screen.getByRole("combobox", { name: "指定流转目标节点" }));
    const nextNodeOptions = await screen.findAllByText("随访复评");
    await user.click(nextNodeOptions[nextNodeOptions.length - 1]);
    await user.click(screen.getByRole("button", { name: /完成当前节点并推进/ }));

    await waitFor(() => {
      expect(advancePathway).toHaveBeenCalledWith({
        patientPathwayId: "pp-real-1",
        triggerPoint: "patient-view",
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

  it("records a variance reason through the service mutation", async () => {
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
    const continueNodeOptions = await screen.findAllByText("随访复评");
    await user.click(continueNodeOptions[continueNodeOptions.length - 1]);
    await user.type(
      screen.getByPlaceholderText("请输入经医师确认的变异事实说明"),
      "患者突发高危指标",
    );
    await user.type(screen.getByPlaceholderText("请输入已确认的处置动作"), "转入专科会诊");
    await user.click(screen.getByRole("button", { name: /提交变异决策/ }));

    await waitFor(() => {
      expect(advancePathway).toHaveBeenCalledWith({
        patientPathwayId: "pp-real-1",
        triggerPoint: "patient-view",
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
    expect(screen.getByText("失败已留痕，可在审计证据中追溯。")).toBeInTheDocument();
    expect(screen.queryByText(/trace-pathway-error/)).not.toBeInTheDocument();
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
    expect(screen.getByText("失败已留痕，可在审计证据中追溯。")).toBeInTheDocument();
    expect(screen.queryByText(/trace-scope-denied/)).not.toBeInTheDocument();
  });
});
