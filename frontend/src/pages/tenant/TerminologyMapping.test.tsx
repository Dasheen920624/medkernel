import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useLargeListExportJob,
  useSaveView,
  useSavedViews,
  useSecurityProfile,
  useSubmitLargeListExport,
  useTerminologyMappings,
  type SecurityProfile,
  type TermMapping,
} from "@/shared/api/hooks";

import TerminologyMapping from "./TerminologyMapping";

vi.mock("@/shared/api/hooks", () => ({
  parseSavedExperienceView: vi.fn((view) =>
    view?.definitionJson ? JSON.parse(view.definitionJson) : null,
  ),
  useLargeListExportJob: vi.fn(),
  useSaveView: vi.fn(),
  useSavedViews: vi.fn(),
  useSecurityProfile: vi.fn(),
  useSubmitLargeListExport: vi.fn(),
  useTerminologyMappings: vi.fn(),
}));

const mapping: TermMapping = {
  id: 1,
  tenantId: "tenant-1",
  localTermId: 100,
  standardTermId: 200,
  sourceSystem: "HIS",
  category: "ICD-10",
  confidence: 0.96,
  riskLevel: "MEDIUM",
  status: "DRAFT",
  evidenceText: "实施核查证据",
  confirmedBy: "审核员",
  confirmedAt: "2026-05-25T00:00:00.000Z",
  updatedAt: "2026-05-26T00:00:00.000Z",
};

const profile: SecurityProfile = {
  userId: "user-1",
  username: "tenant.viewer",
  roles: [],
  mustChangePwd: false,
  mfaRequired: false,
  mfaBound: false,
  permissions: [
    {
      code: "advanced.read",
      dimension: "ACTION",
      target: "advanced",
      displayName: "高级工具",
      risk: "LOW",
    },
    {
      code: "list.export",
      dimension: "ACTION",
      target: "large-list.export",
      displayName: "列表导出",
      risk: "MEDIUM",
    },
  ],
  menuKeys: ["pilot-setup", "advanced-tools"],
  environmentKeys: ["production"],
  dataScope: {
    tenantId: "tenant-1",
    groupId: null,
    hospitalId: null,
    campusId: null,
    siteId: null,
    departmentId: null,
    specialtyId: null,
  },
};

const defaultData = {
  items: [mapping],
  page: 1,
  size: 20,
  total: 41,
  hasNext: true,
  totalEstimated: false,
  traceId: "trace-list",
};

const defaultSavedView = {
  savedViewId: "sv-default",
  pageKey: "terminology.mapping",
  viewName: "默认视图",
  definitionJson: JSON.stringify({
    viewKey: "terminology.mapping",
    filters: [{ key: "sourceSystem", value: "HIS" }],
    pageRequest: {
      pageNumber: 1,
      pageSize: 20,
      sortBy: "updatedAt",
      sortOrder: "desc",
      filters: { sourceSystem: "HIS" },
    },
    visibleColumnKeys: ["sourceSystem", "status", "updatedAt"],
    expertMode: true,
    capturedAt: "2026-06-01T00:00:00.000Z",
  }),
  defaultView: true,
  version: 3,
  updatedAt: "2026-06-01T00:00:00.000Z",
  updatedBy: "doctor-1",
};

function configureQuery(
  queryOverrides: Record<string, unknown> = {},
  securityProfile: SecurityProfile = profile,
) {
  vi.mocked(useSecurityProfile).mockReturnValue({ data: securityProfile } as never);
  vi.mocked(useSavedViews).mockReturnValue({ data: [] } as never);
  vi.mocked(useSaveView).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useSubmitLargeListExport).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useLargeListExportJob).mockReturnValue({ mutateAsync: vi.fn() } as never);
  vi.mocked(useTerminologyMappings).mockReturnValue({
    data: defaultData,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    ...queryOverrides,
  } as never);
}

function renderPage() {
  return render(
    <ConfigProvider>
      <TerminologyMapping />
    </ConfigProvider>,
  );
}

describe("TerminologyMapping experience sample", () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.clearAllMocks();
    configureQuery();
  });

  it("renders a read-only experience using the real paged query contract", async () => {
    renderPage();

    expect(screen.getByText(/目标：核查院内码与标准码的映射关系/)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "映射状态" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入来源系统")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("输入院内码或标准码关键词")).toBeInTheDocument();
    expect(useTerminologyMappings).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: 20, sort: "updatedAt,desc" }),
    );

    expect(screen.queryByRole("button", { name: /导入医院字典/ })).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /确认映射|提交审核|发布|回滚|批量处理/ }),
    ).toBeNull();
    expect(screen.getByRole("button", { name: "导出" })).toBeEnabled();

    await userEvent.click(screen.getByRole("button", { name: "查看 1" }));
    expect(screen.getByText("实施核查证据")).toBeInTheDocument();
    expect(screen.queryByText("院内编码 ID")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch", { name: "专家模式" }));
    expect(screen.getByText("院内编码 ID")).toBeInTheDocument();
    expect(screen.getByText(/trace-list/)).toBeInTheDocument();

    await userEvent.click(screen.getByTitle("2"));
    expect(useTerminologyMappings).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2, size: 20, sort: "updatedAt,desc" }),
    );
  });

  it("loads the backend default view snapshot", async () => {
    vi.mocked(useSavedViews).mockReturnValue({ data: [defaultSavedView] } as never);

    renderPage();

    expect(await screen.findByDisplayValue("HIS")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "专家模式" })).toBeChecked();
  });

  it("saves a non-sensitive view snapshot through the backend", async () => {
    const saveView = vi.fn().mockResolvedValue(defaultSavedView);
    vi.mocked(useSaveView).mockReturnValue({ mutateAsync: saveView } as never);

    renderPage();
    await userEvent.type(screen.getByPlaceholderText("输入来源系统"), "HIS");
    await userEvent.click(screen.getByRole("switch", { name: "专家模式" }));
    await userEvent.click(screen.getByRole("button", { name: "保存视图" }));

    expect(saveView).toHaveBeenCalledWith(
      expect.objectContaining({
        pageKey: "terminology.mapping",
        viewName: "默认视图",
        defaultView: true,
        snapshot: expect.objectContaining({ expertMode: true }),
      }),
    );
    expect(JSON.stringify(saveView.mock.calls[0][0].snapshot)).not.toMatch(
      /patient|token|身份证|患者/i,
    );
  });

  it("submits async export using the current backend view snapshot", async () => {
    const submitExport = vi.fn().mockResolvedValue({
      jobId: "job-1",
      status: "running",
      submittedAt: "2026-06-01T00:00:00.000Z",
      submittedBy: "doctor-1",
      traceId: "trace-export",
    });
    const pollExport = vi.fn().mockResolvedValue({
      jobId: "job-1",
      status: "succeeded",
      submittedAt: "2026-06-01T00:00:00.000Z",
      submittedBy: "doctor-1",
      traceId: "trace-export",
      downloadUrl: "/medkernel/api/v1/large-lists/exports/job-1/download",
    });
    vi.mocked(useSubmitLargeListExport).mockReturnValue({ mutateAsync: submitExport } as never);
    vi.mocked(useLargeListExportJob).mockReturnValue({ mutateAsync: pollExport } as never);

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "导出" }));
    await userEvent.click(screen.getByRole("button", { name: "提交导出任务" }));

    expect(submitExport).toHaveBeenCalledWith(
      expect.objectContaining({
        resourceType: "TERMINOLOGY_MAPPING",
        selectedScope: "currentPage",
        requestSnapshot: expect.objectContaining({ viewKey: "terminology.mapping" }),
      }),
    );
    expect(await screen.findByText("导出已完成")).toBeInTheDocument();
  });

  it("renders loading, empty, error, forbidden and partial states", () => {
    configureQuery({ isLoading: true, data: undefined });
    const view = renderPage();
    expect(screen.getByText("正在加载")).toBeInTheDocument();

    configureQuery({ data: { ...defaultData, items: [], total: 0, hasNext: false } });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText("暂无字典映射条目")).toBeInTheDocument();

    configureQuery({ isError: true, data: undefined });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText("页面暂时不可用")).toBeInTheDocument();

    configureQuery({}, { ...profile, menuKeys: [] });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();

    configureQuery({
      data: {
        ...defaultData,
        partial: {
          successCount: 8,
          failureCount: 1,
          failures: [{ key: "1", reason: "证据补充失败", retryable: true }],
        },
      },
    });
    view.rerender(
      <ConfigProvider>
        <TerminologyMapping />
      </ConfigProvider>,
    );
    expect(screen.getByText(/8 项成功，1 项失败/)).toBeInTheDocument();
    expect(screen.getByText(/证据补充失败/)).toBeInTheDocument();
  });
});
