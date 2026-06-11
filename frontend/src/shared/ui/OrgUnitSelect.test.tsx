import { App as AntdApp } from "antd";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockUseOrgUnits = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useOrgUnits: (params: unknown) => mockUseOrgUnits(params),
}));

import { OrgUnitSelect } from "./OrgUnitSelect";

describe("OrgUnitSelect", () => {
  beforeEach(() => {
    mockUseOrgUnits.mockReset();
    mockUseOrgUnits.mockReturnValue({
      data: {
        items: [
          {
            id: "platform-root",
            orgPath: "/platform",
            level: "PLATFORM",
            code: "PLATFORM",
            name: "平台治理空间",
            status: "ACTIVE",
          },
          {
            id: "hospital-a",
            orgPath: "/tenant-a/hospital-a",
            level: "FACILITY",
            code: "HOSP-A",
            name: "示范医院",
            status: "ACTIVE",
          },
        ],
        page: 1,
        size: 50,
        total: 2,
      },
      isLoading: false,
      isError: false,
    });
  });

  it("按服务机构语义和祖先组织执行服务端搜索", async () => {
    render(
      <AntdApp>
        <OrgUnitSelect aria-label="机构范围" scope="SERVICE_ORGANIZATION" ancestorId="group-a" />
      </AntdApp>,
    );

    expect(mockUseOrgUnits).toHaveBeenLastCalledWith({
      page: 1,
      size: 50,
      sort: "name,asc",
      status: "ACTIVE",
      scope: "SERVICE_ORGANIZATION",
      ancestorId: "group-a",
    });

    fireEvent.change(screen.getByRole("combobox", { name: "机构范围" }), {
      target: { value: "示范" },
    });

    await waitFor(() =>
      expect(mockUseOrgUnits).toHaveBeenLastCalledWith({
        page: 1,
        size: 50,
        sort: "name,asc",
        keyword: "示范",
        status: "ACTIVE",
        scope: "SERVICE_ORGANIZATION",
        ancestorId: "group-a",
      }),
    );
  });

  it("隐藏平台治理节点并可提交组织路径", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <AntdApp>
        <OrgUnitSelect
          aria-label="组织范围"
          scope="BUSINESS_SCOPE"
          valueMode="PATH"
          onChange={onChange}
        />
      </AntdApp>,
    );

    await user.click(screen.getByRole("combobox", { name: "组织范围" }));

    expect(screen.queryByText("平台治理空间 · 平台治理层")).not.toBeInTheDocument();
    await user.click(
      await screen.findByText("示范医院 · 医疗服务机构", {
        selector: ".ant-select-item-option-content",
      }),
    );
    expect(onChange).toHaveBeenCalledWith(
      "/tenant-a/hospital-a",
      expect.objectContaining({ value: "/tenant-a/hospital-a" }),
    );
  });
});
