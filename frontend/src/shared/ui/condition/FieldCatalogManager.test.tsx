import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import FieldCatalogManager from "./FieldCatalogManager";

const apiMocks = vi.hoisted(() => ({
  catalog: [] as unknown[],
  catalogError: false,
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  snapshot: vi.fn(),
  permissions: ["context.write"] as string[],
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: {
      permissions: apiMocks.permissions.map((code) => ({ code })),
    },
  }),
  useContextFieldCatalog: () => ({
    data: apiMocks.catalog,
    isLoading: false,
    isError: apiMocks.catalogError,
  }),
  useCreateContextField: () => ({ mutateAsync: apiMocks.create, isPending: false }),
  useUpdateContextField: () => ({ mutateAsync: apiMocks.update, isPending: false }),
  useDeleteContextField: () => ({ mutateAsync: apiMocks.remove, isPending: false }),
  useSnapshotContextFieldCatalogDraft: () => ({
    mutateAsync: apiMocks.snapshot,
    isPending: false,
  }),
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
    apiMocks.update.mockReset();
    apiMocks.remove.mockReset();
    apiMocks.snapshot.mockReset();
    apiMocks.catalogError = false;
    apiMocks.permissions = ["context.write"];
    apiMocks.catalog = [
      {
        category: "医嘱信息",
        group: "用药医嘱",
        resourceType: "Medication",
        fieldPath: "medications[].code",
        displayName: "药品编码",
        dataType: "code",
        codeSystem: "ATC",
        source: "PLATFORM",
        fieldId: null,
        payloadKey: "medications",
        propertyName: "code",
        jsonSchemaType: "string",
        externalWritable: true,
      },
      {
        category: "检验检查",
        group: "检验/体征结果",
        resourceType: "Observation",
        fieldPath: "observations[].code",
        displayName: "院内检验编码",
        dataType: "code",
        codeSystem: "LOINC-LOCAL",
        source: "TENANT",
        fieldId: "f-1",
        payloadKey: "observations",
        propertyName: "code",
        jsonSchemaType: "string",
        externalWritable: true,
      },
      {
        category: "基本信息",
        group: "患者基本信息",
        resourceType: "Patient",
        fieldPath: "patient.age",
        displayName: "年龄",
        dataType: "number",
        source: "PLATFORM",
        fieldId: null,
        derived: true,
        payloadKey: "patient",
        propertyName: "age",
        jsonSchemaType: "number",
        externalWritable: false,
      },
    ];
  });

  it("展示平台(只读)与租户(可删)字段并区分来源", () => {
    renderManager();
    expect(screen.getByText("medications[].code")).toBeInTheDocument();
    expect(screen.getByText("observations[].code")).toBeInTheDocument();
    expect(screen.getByText("medications.code")).toBeInTheDocument();
    expect(screen.getAllByText("patient.age").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("派生")).toBeInTheDocument();
    expect(screen.getAllByText("平台").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("服务机构")).toBeInTheDocument();
    // 仅租户覆盖有删除按钮
    expect(screen.getByLabelText("删除覆盖 observations[].code")).toBeInTheDocument();
    expect(screen.queryByLabelText("删除字段 medications[].code")).toBeNull();
  });

  it("删除租户覆盖调用删除接口", async () => {
    apiMocks.remove.mockResolvedValue("f-1");
    renderManager();
    fireEvent.click(screen.getByLabelText("删除覆盖 observations[].code"));
    await waitFor(() => expect(apiMocks.remove).toHaveBeenCalledWith("f-1"));
  });

  it("选择平台字段后保存租户元数据覆盖", async () => {
    apiMocks.create.mockResolvedValue({});
    renderManager();
    fireEvent.mouseDown(screen.getByLabelText("选择字段"));
    fireEvent.click(await screen.findByText("药品编码 medications[].code"));
    fireEvent.change(screen.getByLabelText("展示名"), { target: { value: "院内药品编码" } });
    fireEvent.change(screen.getByLabelText("绑定字典"), { target: { value: "ATC-LOCAL" } });
    fireEvent.click(screen.getByRole("button", { name: /保存覆盖/ }));
    await waitFor(() =>
      expect(apiMocks.create).toHaveBeenCalledWith(
        expect.objectContaining({
          category: "医嘱信息",
          fieldPath: "medications[].code",
          dataType: "code",
          displayName: "院内药品编码",
          codeSystem: "ATC-LOCAL",
        }),
      ),
    );
  });

  it("选择已有租户覆盖后走更新接口", async () => {
    apiMocks.update.mockResolvedValue({});
    renderManager();
    fireEvent.mouseDown(screen.getByLabelText("选择字段"));
    fireEvent.click(await screen.findByText("院内检验编码 observations[].code"));
    fireEvent.change(screen.getByLabelText("展示名"), { target: { value: "本院检验编码" } });
    fireEvent.click(screen.getByRole("button", { name: /保存覆盖/ }));
    await waitFor(() =>
      expect(apiMocks.update).toHaveBeenCalledWith(
        expect.objectContaining({
          fieldId: "f-1",
          payload: expect.objectContaining({
            fieldPath: "observations[].code",
            displayName: "本院检验编码",
          }),
        }),
      ),
    );
  });

  it("独立新增有真实运行落点的院内扩展字段", async () => {
    apiMocks.create.mockResolvedValue({});
    renderManager();

    fireEvent.click(screen.getByRole("button", { name: "新增院内扩展字段" }));
    fireEvent.change(screen.getByLabelText("扩展字段键"), {
      target: { value: "dialysis_access_type" },
    });
    fireEvent.change(screen.getByLabelText("业务域"), { target: { value: "院内扩展" } });
    fireEvent.change(screen.getByLabelText("字段分组"), { target: { value: "肾脏替代治疗" } });
    fireEvent.change(screen.getByLabelText("展示名"), { target: { value: "透析通路类型" } });
    fireEvent.mouseDown(screen.getByLabelText("数据类型"));
    fireEvent.click(await screen.findByText("编码"));
    fireEvent.change(screen.getByLabelText("绑定字典"), {
      target: { value: "LOCAL_DIALYSIS_ACCESS" },
    });
    fireEvent.change(screen.getByLabelText("说明"), {
      target: { value: "院内结构化扩展字段" },
    });
    fireEvent.click(screen.getByRole("button", { name: /保存扩展字段/ }));

    await waitFor(() =>
      expect(apiMocks.create).toHaveBeenCalledWith({
        category: "院内扩展",
        group: "肾脏替代治疗",
        resourceType: "Extension",
        fieldPath: "extensions.local.dialysis_access_type",
        displayName: "透析通路类型",
        dataType: "code",
        unit: undefined,
        codeSystem: "LOCAL_DIALYSIS_ACCESS",
        description: "院内结构化扩展字段",
      }),
    );
  });

  it("切换扩展字段类型时清除无效的单位和字典", async () => {
    renderManager();
    fireEvent.click(screen.getByRole("button", { name: "新增院内扩展字段" }));

    expect(screen.getByLabelText("单位")).toBeDisabled();
    expect(screen.getByLabelText("绑定字典")).toBeDisabled();

    fireEvent.mouseDown(screen.getByLabelText("数据类型"));
    fireEvent.click(await screen.findByText("数值"));
    expect(screen.getByLabelText("单位")).not.toBeDisabled();
    fireEvent.change(screen.getByLabelText("单位"), { target: { value: "mg/dL" } });

    fireEvent.mouseDown(screen.getByLabelText("数据类型"));
    fireEvent.click(await screen.findByText("编码"));
    await waitFor(() => expect(screen.getByLabelText("单位")).toHaveValue(""));
    expect(screen.getByLabelText("单位")).toBeDisabled();
    expect(screen.getByLabelText("绑定字典")).not.toBeDisabled();

    fireEvent.change(screen.getByLabelText("绑定字典"), {
      target: { value: "LOCAL_DIALYSIS_ACCESS" },
    });
    fireEvent.mouseDown(screen.getByLabelText("数据类型"));
    fireEvent.click(await screen.findByText("文本"));
    await waitFor(() => expect(screen.getByLabelText("绑定字典")).toHaveValue(""));
    expect(screen.getByLabelText("绑定字典")).toBeDisabled();
  });

  it("字段目录接口不可用时诚实提示并禁止保存", () => {
    apiMocks.catalogError = true;
    renderManager();
    expect(screen.getByText("字段目录暂不可用")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /保存覆盖/ })).toBeDisabled();
  });

  it("将当前工作目录固化为自动编号的统一资产草稿", async () => {
    apiMocks.snapshot.mockResolvedValue({ versionNo: "V2" });
    renderManager();

    fireEvent.click(screen.getByRole("button", { name: "固化为字段目录草稿" }));

    await waitFor(() => expect(apiMocks.snapshot).toHaveBeenCalledTimes(1));
  });

  it("无 context.write 时覆盖、新增和删除三个维护入口统一只读", () => {
    apiMocks.permissions = ["context.read"];

    renderManager();

    expect(screen.getByText("当前账号仅可查看字段目录")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "维护平台字段覆盖" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "新增院内扩展字段" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /保存覆盖/ })).toBeDisabled();
    expect(screen.getByLabelText("删除覆盖 observations[].code")).toBeDisabled();
  });
});
