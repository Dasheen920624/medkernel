import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import FieldCatalogManager from "./FieldCatalogManager";

const apiMocks = vi.hoisted(() => ({
  catalog: [] as unknown[],
  create: vi.fn(),
  remove: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useContextFieldCatalog: () => ({ data: apiMocks.catalog, isLoading: false, isError: false }),
  useCreateContextField: () => ({ mutateAsync: apiMocks.create, isPending: false }),
  useDeleteContextField: () => ({ mutateAsync: apiMocks.remove, isPending: false }),
}));

function renderManager() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <FieldCatalogManager open onClose={() => {}} />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("FieldCatalogManager 字段目录维护（P2/P5）", () => {
  beforeEach(() => {
    apiMocks.create.mockReset();
    apiMocks.remove.mockReset();
    apiMocks.catalog = [
      {
        category: "医嘱信息",
        group: "用药医嘱",
        resourceType: "Medication",
        fieldPath: "medications[].code",
        displayName: "药品编码",
        dataType: "code",
        codeSystem: "ATC",
        source: "SYSTEM",
        fieldId: null,
      },
      {
        category: "医嘱信息",
        group: "用药医嘱",
        resourceType: "Medication",
        fieldPath: "medications[].customFlag",
        displayName: "院内自定义",
        dataType: "string",
        source: "TENANT",
        fieldId: "f-1",
      },
    ];
  });

  it("展示系统(只读)与租户(可删)字段并区分来源", () => {
    renderManager();
    expect(screen.getByText("medications[].code")).toBeInTheDocument();
    expect(screen.getByText("medications[].customFlag")).toBeInTheDocument();
    expect(screen.getByText("系统")).toBeInTheDocument();
    expect(screen.getByText("租户")).toBeInTheDocument();
    // 仅租户字段有删除按钮
    expect(screen.getByLabelText("删除字段 medications[].customFlag")).toBeInTheDocument();
    expect(screen.queryByLabelText("删除字段 medications[].code")).toBeNull();
  });

  it("删除租户字段调用删除接口", async () => {
    apiMocks.remove.mockResolvedValue("f-1");
    renderManager();
    fireEvent.click(screen.getByLabelText("删除字段 medications[].customFlag"));
    await waitFor(() => expect(apiMocks.remove).toHaveBeenCalledWith("f-1"));
  });

  it("新增字段校验必填并提交", async () => {
    apiMocks.create.mockResolvedValue({});
    renderManager();
    fireEvent.change(screen.getByLabelText("业务域"), { target: { value: "检验检查" } });
    fireEvent.change(screen.getByLabelText("分组"), { target: { value: "检验结果" } });
    fireEvent.change(screen.getByLabelText("资源类型"), { target: { value: "Observation" } });
    fireEvent.change(screen.getByLabelText("字段路径"), {
      target: { value: "observations[].myFlag" },
    });
    fireEvent.change(screen.getByLabelText("字段名"), { target: { value: "我的标记" } });
    fireEvent.click(screen.getByRole("button", { name: /新增字段/ }));
    await waitFor(() =>
      expect(apiMocks.create).toHaveBeenCalledWith(
        expect.objectContaining({
          category: "检验检查",
          fieldPath: "observations[].myFlag",
          dataType: "string",
        }),
      ),
    );
  });
});
