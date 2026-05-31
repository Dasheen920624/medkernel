import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useSecurityProfile,
  useTerminologyMappings,
  type SecurityProfile,
  type TermMapping,
} from "@/shared/api/hooks";

import TerminologyMapping from "./TerminologyMapping";

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: vi.fn(),
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
  roles: [],
  permissions: [
    {
      code: "advanced.read",
      dimension: "ACTION",
      target: "advanced",
      displayName: "高级工具",
      risk: "LOW",
    },
  ],
  menuKeys: ["terminology-mapping", "advanced-tools"],
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

function configureQuery(
  queryOverrides: Record<string, unknown> = {},
  securityProfile: SecurityProfile = profile,
) {
  vi.mocked(useSecurityProfile).mockReturnValue({ data: securityProfile } as never);
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
    expect(screen.getByText("导出任务接口待引擎包发布任务接入")).toBeInTheDocument();

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

  it("saves and restores a non-sensitive view snapshot", async () => {
    const first = renderPage();
    await userEvent.type(screen.getByPlaceholderText("输入来源系统"), "HIS");
    await userEvent.click(screen.getByRole("switch", { name: "专家模式" }));
    await userEvent.click(screen.getByRole("button", { name: "保存视图" }));

    const saved = window.localStorage.getItem("medkernel.view.terminology.mapping") ?? "";
    expect(saved).toContain("HIS");
    expect(saved).toContain('"expertMode":true');
    expect(saved).not.toMatch(/patient|token|身份证|患者/i);

    first.unmount();
    renderPage();
    expect(screen.getByRole("switch", { name: "专家模式" })).toBeChecked();
    expect(screen.getByPlaceholderText("输入来源系统")).toHaveValue("HIS");
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
