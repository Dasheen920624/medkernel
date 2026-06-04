import { render, screen } from "@testing-library/react";
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
  beforeEach(() => {
    mockUsePathwayTemplates.mockReturnValue({
      data: { items: [], total: 0 },
    } as unknown as ReturnType<typeof usePathwayTemplates>);
    mockUseSpecialtyPackages.mockReturnValue({
      data: { items: [], total: 0 },
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
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof usePatientPathways>);
    mockUsePatientPathwayDetail.mockReturnValue({
      data: null,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof usePatientPathwayDetail>);
    mockUsePathwayTemplateDetail.mockReturnValue({
      data: null,
    } as unknown as ReturnType<typeof usePathwayTemplateDetail>);
    mockUsePatientPathwayClocks.mockReturnValue({
      data: [],
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof usePatientPathwayClocks>);
    mockUsePatientPathwayVariances.mockReturnValue({
      data: [],
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof usePatientPathwayVariances>);
    mockUseEnterPatientPathway.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useEnterPatientPathway>);
    mockUseAdvancePatientPathway.mockReturnValue({
      mutateAsync: vi.fn(),
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
});
