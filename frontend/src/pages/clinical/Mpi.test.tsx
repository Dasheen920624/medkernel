import { readFileSync } from "node:fs";

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCreateContextSnapshot,
  useCreateMpiPatient,
  useMergeMpiPatients,
  useMpiPatientDetail,
  useMpiPatients,
  useMpiStats,
  useSecurityProfile,
  useSplitMpiPatient,
} from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import Mpi from "./Mpi";

const navigateMock = vi.hoisted(() => vi.fn());

vi.mock("react-router-dom", () => ({
  useNavigate: () => navigateMock,
}));

vi.mock("@/shared/api/hooks", () => ({
  useCreateContextSnapshot: vi.fn(),
  useCreateMpiPatient: vi.fn(),
  useMergeMpiPatients: vi.fn(),
  useMpiPatientDetail: vi.fn(),
  useMpiPatients: vi.fn(),
  useMpiStats: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSplitMpiPatient: vi.fn(),
}));

const mockUseCreateMpiPatient = vi.mocked(useCreateMpiPatient);
const mockUseCreateContextSnapshot = vi.mocked(useCreateContextSnapshot);
const mockUseMergeMpiPatients = vi.mocked(useMergeMpiPatients);
const mockUseMpiPatientDetail = vi.mocked(useMpiPatientDetail);
const mockUseMpiPatients = vi.mocked(useMpiPatients);
const mockUseMpiStats = vi.mocked(useMpiStats);
const mockUseSecurityProfile = vi.mocked(useSecurityProfile);
const mockUseSplitMpiPatient = vi.mocked(useSplitMpiPatient);

const MPI_INTERACTION_TIMEOUT_MS = 15_000;

function renderMpi() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <Mpi />
      </AntdApp>
    </ConfigProvider>,
  );
}

function securityProfile(
  permissionCodes = [
    "mpi.read",
    "mpi.create",
    "mpi.write",
    "context.write",
    "menu.cdss-fatigue",
    "system.debug",
  ],
) {
  const menuKeys = ["mpi"];
  if (permissionCodes.includes("menu.cdss-fatigue") || permissionCodes.includes("cdss.read")) {
    menuKeys.push("cdss-fatigue");
  }
  if (permissionCodes.includes("system.debug")) {
    menuKeys.push("runtime-diagnostics");
  }
  return {
    data: {
      permissions: permissionCodes.map((code) => ({
        code,
        dimension: "ACTION",
        target: "mpi",
        displayName: code,
        risk: code === "mpi.write" ? "HIGH" : "MEDIUM",
      })),
      roles: [{ code: "clinical-user", displayName: "临床使用者", source: "TEST" }],
      menuKeys,
      environmentKeys: ["production"],
      dataScope: { tenantId: "tenant-A" },
    },
  } as unknown as ReturnType<typeof useSecurityProfile>;
}

describe("Mpi", () => {
  const refetchList = vi.fn();
  const refetchStats = vi.fn();
  const createPatient = vi.fn();
  const createContextSnapshot = vi.fn();
  const mergePatient = vi.fn();
  const splitPatient = vi.fn();
  const refetchDetail = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    navigateMock.mockClear();
    useEvidenceDetailsStore.setState({ enabled: false });
    mockUseSecurityProfile.mockReturnValue(securityProfile());
    mockUseMpiPatients.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            mpiId: "mpi-real-1",
            tenantId: "tenant-A",
            maskedName: "张*三",
            gender: "M",
            age: 36,
            idLast4: "1234",
            mergedCount: 0,
            status: "ACTIVE",
            mergedIntoMpiId: null,
            createdAt: "2026-06-04T00:00:00Z",
            createdBy: "doctor-a",
            updatedAt: "2026-06-04T00:00:00Z",
            updatedBy: "doctor-a",
          },
          {
            id: 2,
            mpiId: "mpi-merged-1",
            tenantId: "tenant-A",
            maskedName: "李*四",
            gender: "F",
            age: 41,
            idLast4: "9876",
            mergedCount: 0,
            status: "MERGED_INTO",
            mergedIntoMpiId: "mpi-real-1",
            createdAt: "2026-06-04T00:00:00Z",
            createdBy: "doctor-a",
            updatedAt: "2026-06-04T00:00:00Z",
            updatedBy: "doctor-a",
          },
          {
            id: 3,
            mpiId: "mpi-target-1",
            tenantId: "tenant-A",
            maskedName: "王*五",
            gender: "M",
            age: 52,
            idLast4: "5678",
            mergedCount: 0,
            status: "ACTIVE",
            mergedIntoMpiId: null,
            createdAt: "2026-06-04T00:00:00Z",
            createdBy: "doctor-a",
            updatedAt: "2026-06-04T00:00:00Z",
            updatedBy: "doctor-a",
          },
        ],
        total: 3,
      },
      isLoading: false,
      refetch: refetchList,
    } as unknown as ReturnType<typeof useMpiPatients>);
    mockUseMpiStats.mockReturnValue({
      data: {
        activeCount: 2,
        mergedCount: 0,
        activePathwayCount: 2,
        averageAge: 36,
        genderCounts: { M: 1, F: 0, UNKNOWN: 0 },
      },
      isLoading: false,
      refetch: refetchStats,
    } as unknown as ReturnType<typeof useMpiStats>);
    mockUseMpiPatientDetail.mockReturnValue({
      data: {
        patient: {
          id: 1,
          mpiId: "mpi-real-1",
          tenantId: "tenant-A",
          maskedName: "张*三",
          gender: "M",
          age: 36,
          idLast4: "1234",
          mergedCount: 0,
          status: "ACTIVE",
          mergedIntoMpiId: null,
          createdAt: "2026-06-04T00:00:00Z",
          createdBy: "doctor-a",
          updatedAt: "2026-06-04T00:00:00Z",
          updatedBy: "doctor-a",
        },
        latestContextSnapshot: {
          snapshotId: "snapshot-real-1",
          patientId: "mpi-real-1",
          encounterId: "enc-real-1",
          status: "ACTIVE",
          qualityStatus: "COMPLETE",
          createdAt: "2026-06-04T01:00:00Z",
        },
        contextSnapshot: null,
        activePathwayCount: 1,
        activePathways: [
          {
            patientPathwayId: "pathway-acute-1",
            patientId: "mpi-real-1",
            encounterId: "enc-real-1",
            templateId: "tpl-stroke-v1",
            currentNodeCode: "ADMISSION",
            status: "ACTIVE",
            enteredAt: "2026-06-04T02:00:00Z",
            traceId: "trace-pathway-1",
          },
        ],
        traceId: "trace-p360-1",
      },
      isLoading: false,
      isError: false,
      refetch: refetchDetail,
    } as unknown as ReturnType<typeof useMpiPatientDetail>);
    createContextSnapshot.mockResolvedValue({
      snapshotId: "ctx-new-1",
      status: "ACTIVE",
      runtimeReleaseId: "runtime-release-1",
      qualityStatus: "VALID",
      missingFields: [],
      mappingStatus: {},
    });
    mockUseCreateContextSnapshot.mockReturnValue({
      mutateAsync: createContextSnapshot,
      isPending: false,
    } as unknown as ReturnType<typeof useCreateContextSnapshot>);
    mergePatient.mockResolvedValue({
      status: "MERGED",
      sourceMpiId: "mpi-real-1",
      targetMpiId: "mpi-target-1",
      message: "患者主索引合并成功，已记录审计证据",
    });
    mockUseMergeMpiPatients.mockReturnValue({
      mutateAsync: mergePatient,
      isPending: false,
    } as unknown as ReturnType<typeof useMergeMpiPatients>);
    splitPatient.mockResolvedValue({
      status: "SPLIT",
      sourceMpiId: "mpi-merged-1",
      targetMpiId: "mpi-real-1",
      message: "患者主索引合并关系已拆分",
    });
    mockUseSplitMpiPatient.mockReturnValue({
      mutateAsync: splitPatient,
      isPending: false,
    } as unknown as ReturnType<typeof useSplitMpiPatient>);
    createPatient.mockResolvedValue({
      mpiId: "mpi-new",
      maskedName: "李*四",
      gender: "F",
      age: 41,
      idLast4: "9876",
      status: "ACTIVE",
    });
    mockUseCreateMpiPatient.mockReturnValue({
      mutateAsync: createPatient,
      isPending: false,
    } as unknown as ReturnType<typeof useCreateMpiPatient>);
  });

  it(
    "opens patient 360 detail without exposing evidence identifiers by default",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getAllByRole("button", { name: /患者360/ })[0]);

      await waitFor(() => {
        expect(mockUseMpiPatientDetail).toHaveBeenCalledWith("mpi-real-1");
      });
      expect(screen.getByText("患者 360 视图")).toBeInTheDocument();
      expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
      expect(screen.getAllByText("张*三").length).toBeGreaterThan(0);
      expect(screen.getByText("患者身份与就诊上下文已关联")).toBeInTheDocument();
      expect(screen.queryByText("snapshot-real-1")).not.toBeInTheDocument();
      expect(screen.queryByText("pathway-acute-1")).not.toBeInTheDocument();
      expect(screen.queryByText(/trace-p360-1/)).not.toBeInTheDocument();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it("uses Chinese business wording for gender distribution stats", () => {
    renderMpi();

    expect(screen.getByText("群体性别分布")).toBeInTheDocument();
    expect(screen.getByText("男性 1 人 / 女性 0 人")).toBeInTheDocument();
    expect(screen.queryByText(/M\/F/)).not.toBeInTheDocument();
    expect(screen.queryByText(/男: 1 \| 女: 0/)).not.toBeInTheDocument();
  });

  it(
    "opens report interpretation from patient 360 current context without exposing identifiers in the page",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getAllByRole("button", { name: /患者360/ })[0]);
      expect(await screen.findByText("患者身份与就诊上下文已关联")).toBeInTheDocument();
      expect(screen.queryByText("snapshot-real-1")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "生成报告解读" }));

      expect(navigateMock).toHaveBeenCalledWith("/cdss/fatigue", {
        state: {
          reportInterpretation: {
            snapshotId: "snapshot-real-1",
            patientLabel: "张*三",
          },
        },
      });
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "updates current clinical context from patient 360 when a prior snapshot already exists",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getAllByRole("button", { name: /患者360/ })[0]);
      expect(await screen.findByText("患者身份与就诊上下文已关联")).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "更新当前就诊上下文" }));

      const diseaseInput = screen.getByLabelText("诊断/随访病种");
      await user.clear(diseaseInput);
      await user.type(diseaseInput, "上线演练路径入径复核");
      const reasonInput = screen.getByLabelText("建立原因");
      await user.clear(reasonInput);
      await user.type(reasonInput, "真实前台演练：机构生效版本更新后重建当前上下文。");
      await user.click(screen.getByRole("button", { name: "生成上下文快照" }));

      await waitFor(() => {
        expect(createContextSnapshot).toHaveBeenCalledWith(
          expect.objectContaining({
            patient: expect.objectContaining({ mpiId: "mpi-real-1" }),
            diseaseCode: "上线演练路径入径复核",
            diseaseName: "上线演练路径入径复核",
            reason: "真实前台演练：机构生效版本更新后重建当前上下文。",
            idempotencyKey: expect.any(String),
          }),
        );
      });
      expect(refetchDetail).toHaveBeenCalled();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "reveals MPI audit identifiers only after evidence details are enabled",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getByRole("switch", { name: "证据详情" }));
      await user.click(screen.getAllByRole("button", { name: /患者360/ })[0]);

      expect(await screen.findByText("snapshot-real-1")).toBeInTheDocument();
      expect(screen.getByText("pathway-acute-1")).toBeInTheDocument();
      expect(screen.getByText("临床路径版本编号：tpl-stroke-v1")).toBeInTheDocument();
      expect(screen.queryByText("模板：tpl-stroke-v1")).not.toBeInTheDocument();
      expect(screen.getByText(/trace-p360-1/)).toBeInTheDocument();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "does not reveal MPI audit identifiers when the role lacks evidence-detail permission",
    async () => {
      const user = userEvent.setup();
      useEvidenceDetailsStore.setState({ enabled: true });
      mockUseSecurityProfile.mockReturnValue(securityProfile(["mpi.read"]));
      renderMpi();

      expect(screen.queryByRole("switch", { name: "证据详情" })).not.toBeInTheDocument();
      await user.click(screen.getAllByRole("button", { name: /患者360/ })[0]);

      expect(await screen.findByText("患者身份与就诊上下文已关联")).toBeInTheDocument();
      expect(screen.queryByText("snapshot-real-1")).not.toBeInTheDocument();
      expect(screen.queryByText("pathway-acute-1")).not.toBeInTheDocument();
      expect(screen.queryByText(/trace-p360-1/)).not.toBeInTheDocument();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "submits keyword and status as real MPI list filters",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.type(screen.getByPlaceholderText("支持按姓名或院内患者编号检索..."), "mpi-real-1");
      await user.click(screen.getByRole("combobox", { name: "索引状态" }));
      const activeOptions = await screen.findAllByText("当前有效");
      await user.click(activeOptions[activeOptions.length - 1]);
      await user.click(screen.getByRole("button", { name: /检索过滤/ }));

      await waitFor(() => {
        expect(mockUseMpiPatients).toHaveBeenCalledWith({
          keyword: "mpi-real-1",
          status: "ACTIVE",
          page: 1,
          size: 20,
        });
      });
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "merges an active MPI row through the service mutation and refreshes evidence",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getAllByRole("button", { name: /合并患者/ })[0]);
      await user.click(screen.getByRole("combobox", { name: "目标患者" }));
      await user.click(await screen.findByText("王*五 · 男 · 52 岁 · ***5678"));
      await user.click(screen.getByRole("button", { name: "确认合并" }));

      await waitFor(() => {
        expect(mergePatient).toHaveBeenCalledWith({
          sourceMpiId: "mpi-real-1",
          targetMpiId: "mpi-target-1",
          idempotencyKey: expect.any(String),
        });
      });
      expect(refetchList).toHaveBeenCalled();
      expect(refetchStats).toHaveBeenCalled();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "renders real MPI rows and creates a patient through the service mutation",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      expect(screen.getByText("张*三")).toBeInTheDocument();
      expect(screen.getByText("李*四")).toBeInTheDocument();
      expect(screen.getByText(/活跃路径实例 2 个/)).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /新增患者/ }));
      expect(screen.queryByText("患者主索引 ID")).not.toBeInTheDocument();
      await user.type(screen.getByPlaceholderText("例如：李*四"), "李*四");
      await user.click(screen.getByRole("combobox", { name: "性别" }));
      const femaleOptions = await screen.findAllByText("女");
      await user.click(femaleOptions[femaleOptions.length - 1]);
      await user.clear(screen.getByPlaceholderText("例如：36"));
      await user.type(screen.getByPlaceholderText("例如：36"), "41");
      await user.type(screen.getByPlaceholderText("例如：9876"), "9876");
      await user.click(screen.getByRole("button", { name: "保存患者" }));

      await waitFor(() => {
        expect(createPatient).toHaveBeenCalledWith({
          maskedName: "李*四",
          gender: "F",
          age: 41,
          idLast4: "9876",
          idempotencyKey: expect.any(String),
        });
      });
      expect(refetchList).toHaveBeenCalled();
      expect(refetchStats).toHaveBeenCalled();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "creates a current clinical context snapshot from patient 360 before follow-up workflows",
    async () => {
      mockUseMpiPatientDetail.mockReturnValue({
        data: {
          patient: {
            id: 1,
            mpiId: "mpi-real-1",
            tenantId: "tenant-A",
            maskedName: "张*三",
            gender: "M",
            age: 67,
            idLast4: "1234",
            mergedCount: 0,
            status: "ACTIVE",
            mergedIntoMpiId: null,
            createdAt: "2026-06-04T00:00:00Z",
            createdBy: "doctor-a",
            updatedAt: "2026-06-04T00:00:00Z",
            updatedBy: "doctor-a",
          },
          latestContextSnapshot: null,
          contextSnapshot: null,
          activePathwayCount: 0,
          activePathways: [],
          traceId: "trace-p360-1",
        },
        isLoading: false,
        isError: false,
        refetch: refetchDetail,
      } as unknown as ReturnType<typeof useMpiPatientDetail>);
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getAllByRole("button", { name: /患者360/ })[0]);
      expect((await screen.findAllByText("暂无已生效上下文")).length).toBeGreaterThan(0);
      await user.click(screen.getByRole("button", { name: "建立当前就诊上下文" }));
      const diseaseInput = screen.getByLabelText("诊断/随访病种");
      await user.clear(diseaseInput);
      await user.type(diseaseInput, "真实前台慢病随访主题");
      const medicationInput = screen.getByLabelText("当前用药");
      await user.type(medicationInput, "华法林、阿司匹林");
      await user.type(screen.getByLabelText("过敏/不良反应"), "青霉素：皮疹、头孢菌素：呼吸困难");
      await user.type(screen.getByLabelText("监测指标"), "CRP=128 mg/L；PCT=2.4 ng/mL");
      await user.clear(screen.getByLabelText("身高 cm"));
      await user.type(screen.getByLabelText("身高 cm"), "170");
      await user.clear(screen.getByLabelText("体重 kg"));
      await user.type(screen.getByLabelText("体重 kg"), "82");
      await user.type(screen.getByLabelText("医技报告项目"), "血钾检验");
      await user.type(screen.getByLabelText("报告结论"), "血钾 6.3 mmol/L，危急值，已复核");
      await user.type(screen.getByLabelText("异常重点"), "血钾升高、危急值");
      expect(screen.getByText("医保结算事实（可选）")).toBeInTheDocument();
      await user.clear(screen.getByLabelText("DRG/DIP 分组"));
      await user.type(screen.getByLabelText("DRG/DIP 分组"), "DRG-REAL-A");
      await user.clear(screen.getByLabelText("本次结算金额"));
      await user.type(screen.getByLabelText("本次结算金额"), "1280.50");
      await user.clear(screen.getByLabelText("医保支付金额"));
      await user.type(screen.getByLabelText("医保支付金额"), "860.00");
      const reasonInput = screen.getByLabelText("建立原因");
      await user.clear(reasonInput);
      await user.type(reasonInput, "真实前台演练：随访计划生成前由医生确认当前就诊上下文。");
      await user.click(screen.getByRole("button", { name: "生成上下文快照" }));

      await waitFor(() => {
        expect(createContextSnapshot).toHaveBeenCalledWith({
          patient: expect.objectContaining({
            mpiId: "mpi-real-1",
            maskedName: "张*三",
            gender: "M",
            age: 67,
          }),
          encounterType: "OUTPATIENT",
          diseaseCode: "真实前台慢病随访主题",
          diseaseName: "真实前台慢病随访主题",
          riskLevel: "MEDIUM",
          currentMedicationText: "华法林、阿司匹林",
          allergyIntoleranceText: "青霉素：皮疹、头孢菌素：呼吸困难",
          observationText: "CRP=128 mg/L；PCT=2.4 ng/mL",
          heightCm: 170,
          weightKg: 82,
          diagnosticReportType: "血钾检验",
          diagnosticReportConclusion: "血钾 6.3 mmol/L，危急值，已复核",
          diagnosticReportKeyFindingsText: "血钾升高、危急值",
          insuranceClaimDrgCode: "DRG-REAL-A",
          insuranceClaimTotalCost: 1280.5,
          insuranceClaimPaidAmount: 860,
          reason: "真实前台演练：随访计划生成前由医生确认当前就诊上下文。",
          idempotencyKey: expect.any(String),
        });
      });
      expect(refetchDetail).toHaveBeenCalled();
      expect(refetchList).toHaveBeenCalled();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "keeps MPI active patient directory language scoped to the current organization",
    async () => {
      const activeDirectoryRefetch = vi.fn();
      mockUseMpiPatients.mockImplementation((params?: { status?: string; size?: number }) => {
        if (params?.status === "ACTIVE" && params.size === 50) {
          return {
            data: undefined,
            isLoading: false,
            isError: true,
            error: undefined,
            refetch: activeDirectoryRefetch,
          } as unknown as ReturnType<typeof useMpiPatients>;
        }

        return {
          data: {
            items: [
              {
                id: 1,
                mpiId: "mpi-real-1",
                tenantId: "tenant-A",
                maskedName: "张*三",
                gender: "M",
                age: 36,
                idLast4: "1234",
                mergedCount: 0,
                status: "ACTIVE",
                mergedIntoMpiId: null,
                createdAt: "2026-06-04T00:00:00Z",
                createdBy: "doctor-a",
                updatedAt: "2026-06-04T00:00:00Z",
                updatedBy: "doctor-a",
              },
              {
                id: 3,
                mpiId: "mpi-target-1",
                tenantId: "tenant-A",
                maskedName: "王*五",
                gender: "M",
                age: 52,
                idLast4: "5678",
                mergedCount: 0,
                status: "ACTIVE",
                mergedIntoMpiId: null,
                createdAt: "2026-06-04T00:00:00Z",
                createdBy: "doctor-a",
                updatedAt: "2026-06-04T00:00:00Z",
                updatedBy: "doctor-a",
              },
            ],
            total: 2,
          },
          isLoading: false,
          refetch: refetchList,
        } as unknown as ReturnType<typeof useMpiPatients>;
      });
      const user = userEvent.setup();

      renderMpi();

      expect(
        screen.getByText(/当前组织范围内仍作为主记录使用的患者数；活跃路径实例/),
      ).toBeInTheDocument();

      await user.click(screen.getAllByRole("button", { name: /合并患者/ })[0]);

      expect(screen.getByText("活跃患者目录暂时不可用")).toBeInTheDocument();
      expect(
        screen.getByText("无法读取当前组织范围的活跃患者，请重试后再执行合并。"),
      ).toBeInTheDocument();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it("hides create and high-risk merge actions without matching MPI action permissions", () => {
    mockUseSecurityProfile.mockReturnValue(securityProfile(["mpi.read"]));

    renderMpi();

    expect(screen.queryByRole("button", { name: /新增患者/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /快速合并/ })).not.toBeInTheDocument();
    expect(screen.queryAllByRole("button", { name: /合并患者/ })).toHaveLength(0);
    expect(screen.queryAllByRole("button", { name: /拆分归并/ })).toHaveLength(0);
  });

  it(
    "splits a merged MPI row with an explicit review reason",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getByRole("button", { name: /拆分归并/ }));
      await user.type(
        screen.getByPlaceholderText("请输入人工核查结论"),
        "人工核查后确认不是同一患者",
      );
      await user.click(screen.getByRole("button", { name: "确认拆分" }));

      await waitFor(() => {
        expect(splitPatient).toHaveBeenCalledWith({
          sourceMpiId: "mpi-merged-1",
          reviewReason: "人工核查后确认不是同一患者",
          idempotencyKey: expect.any(String),
        });
      });
      expect(refetchList).toHaveBeenCalled();
      expect(refetchStats).toHaveBeenCalled();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it("keeps the MPI patient table constrained instead of widening the mobile root", () => {
    const pageSource = readFileSync("src/pages/clinical/Mpi.tsx", "utf8");
    const cssSource = readFileSync("src/pages/clinical/Mpi.module.css", "utf8");
    const tableCardRule = cssSource.match(/\.tableCard\s*\{[^}]+\}/u)?.[0] ?? "";

    expect(pageSource).toContain('tableLayout="fixed"');
    expect(pageSource).toContain("scroll={{ x: 920 }}");
    expect(tableCardRule).toContain("min-width: 0;");
    expect(tableCardRule).toContain("overflow: hidden;");
  });
});
