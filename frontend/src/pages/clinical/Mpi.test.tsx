import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCreateMpiPatient,
  useMergeMpiPatients,
  useMpiPatients,
  useMpiStats,
  useSplitMpiPatient,
} from "@/shared/api/hooks";

import Mpi from "./Mpi";

vi.mock("@/shared/api/hooks", () => ({
  useCreateMpiPatient: vi.fn(),
  useMergeMpiPatients: vi.fn(),
  useMpiPatients: vi.fn(),
  useMpiStats: vi.fn(),
  useSplitMpiPatient: vi.fn(),
}));

const mockUseCreateMpiPatient = vi.mocked(useCreateMpiPatient);
const mockUseMergeMpiPatients = vi.mocked(useMergeMpiPatients);
const mockUseMpiPatients = vi.mocked(useMpiPatients);
const mockUseMpiStats = vi.mocked(useMpiStats);
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

describe("Mpi", () => {
  const refetchList = vi.fn();
  const refetchStats = vi.fn();
  const createPatient = vi.fn();
  const splitPatient = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
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
        ],
        total: 2,
      },
      isLoading: false,
      refetch: refetchList,
    } as unknown as ReturnType<typeof useMpiPatients>);
    mockUseMpiStats.mockReturnValue({
      data: {
        activeCount: 1,
        mergedCount: 0,
        activePathwayCount: 2,
        averageAge: 36,
        genderCounts: { M: 1, F: 0, UNKNOWN: 0 },
      },
      isLoading: false,
      refetch: refetchStats,
    } as unknown as ReturnType<typeof useMpiStats>);
    mockUseMergeMpiPatients.mockReturnValue({
      mutateAsync: vi.fn(),
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
    "renders real MPI rows and creates a patient through the backend mutation",
    async () => {
      const user = userEvent.setup();
      renderMpi();

      expect(screen.getAllByText("mpi-real-1").length).toBeGreaterThan(0);
      expect(screen.getByText("张*三")).toBeInTheDocument();
      expect(screen.getByText("mpi-merged-1")).toBeInTheDocument();
      expect(screen.getByText(/在径路径实例 2 个/)).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /新增患者/ }));
      await user.type(screen.getByPlaceholderText("例如：mpi-new"), "mpi-new");
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
          mpiId: "mpi-new",
          maskedName: "李*四",
          gender: "F",
          age: 41,
          idLast4: "9876",
        });
      });
      expect(refetchList).toHaveBeenCalled();
      expect(refetchStats).toHaveBeenCalled();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );

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
        });
      });
      expect(refetchList).toHaveBeenCalled();
      expect(refetchStats).toHaveBeenCalled();
    },
    MPI_INTERACTION_TIMEOUT_MS,
  );
});
