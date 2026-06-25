import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCreateMpiPatient,
  useMergeMpiPatients,
  useMpiPatientDetail,
  useMpiPatients,
  useMpiStats,
  useSecurityProfile,
  useSplitMpiPatient,
} from "@/shared/api/hooks";

import Mpi from "./Mpi";

vi.mock("@/shared/api/hooks", () => ({
  useCreateMpiPatient: vi.fn(),
  useMergeMpiPatients: vi.fn(),
  useMpiPatientDetail: vi.fn(),
  useMpiPatients: vi.fn(),
  useMpiStats: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSplitMpiPatient: vi.fn(),
}));

const mockUseCreateMpiPatient = vi.mocked(useCreateMpiPatient);
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

function securityProfile(permissionCodes = ["mpi.read", "mpi.create", "mpi.write"]) {
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
      menuKeys: ["mpi"],
      environmentKeys: ["production"],
      dataScope: { tenantId: "tenant-A" },
    },
  } as unknown as ReturnType<typeof useSecurityProfile>;
}

describe("Mpi", () => {
  const refetchList = vi.fn();
  const refetchStats = vi.fn();
  const createPatient = vi.fn();
  const mergePatient = vi.fn();
  const splitPatient = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
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
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useMpiPatientDetail>);
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
    "opens patient 360 detail from the backend MPI detail hook",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getAllByRole("button", { name: /患者360/ })[0]);

      await waitFor(() => {
        expect(mockUseMpiPatientDetail).toHaveBeenCalledWith("mpi-real-1");
      });
      expect(screen.getByText("患者 360 视图")).toBeInTheDocument();
      expect(screen.getAllByText("snapshot-real-1")).toHaveLength(1);
      expect(screen.getByText("pathway-acute-1")).toBeInTheDocument();
      expect(screen.getByText(/trace-p360-1/)).toBeInTheDocument();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

  it(
    "submits keyword and status as real MPI list filters",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.type(screen.getByPlaceholderText("支持按姓名或 MPI ID 检索..."), "mpi-real-1");
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
    "merges an active MPI row through the backend mutation and refreshes evidence",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      await user.click(screen.getAllByRole("button", { name: /合并患者/ })[0]);
      await user.click(screen.getByRole("combobox", { name: "目标患者" }));
      await user.click(await screen.findByText("王*五 · mpi-target-1 · ***5678"));
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
    "renders real MPI rows and creates a patient through the backend mutation",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      expect(screen.getAllByText("mpi-real-1").length).toBeGreaterThan(0);
      expect(screen.getByText("张*三")).toBeInTheDocument();
      expect(screen.getByText("mpi-merged-1")).toBeInTheDocument();
      expect(screen.getByText(/活跃路径实例 2 个/)).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /新增患者/ }));
      expect(screen.queryByText("患者主索引 ID")).not.toBeInTheDocument();
      await user.type(screen.getByPlaceholderText("例如：李*四"), "李*四");
      await user.click(screen.getByRole("combobox", { name: "性别" }));
      const femaleOptions = await screen.findAllByText("女 (F)");
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
});
