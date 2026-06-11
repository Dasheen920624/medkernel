import { App as AntdApp } from "antd";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const previewImport = vi.fn();
const commitImport = vi.fn();
const refetchPersonnel = vi.fn();

let personnelQuery = {
  data: {
    items: [
      {
        personId: "person-1",
        employeeNo: "EMP-001",
        displayName: "王医生",
        status: "ACTIVE",
        appointmentType: "INTERNAL",
        organizationId: "hospital-a",
        organizationName: "示范医院",
        departmentId: "dept-cardio",
        departmentName: "心内科",
        wardId: "ward-cardio-1",
        wardName: "心内一病区",
        positionTitle: "主治医师",
        userId: "doctor-1",
        username: "dr.wang",
        accountState: "RESET_REQUIRED",
        identityCount: 1,
      },
    ],
    page: 1,
    size: 20,
    total: 1,
    hasNext: false,
    totalEstimated: false,
  },
  isLoading: false,
  isError: false,
  error: null as Error | null,
  refetch: refetchPersonnel,
};

const detail = {
  person: {
    personId: "person-1",
    employeeNo: "EMP-001",
    displayName: "王医生",
    status: "ACTIVE",
    createdAt: "2026-06-11T00:00:00Z",
    updatedAt: "2026-06-11T00:00:00Z",
  },
  primaryAppointment: {
    appointmentId: "appt-1",
    organizationId: "hospital-a",
    organizationName: "示范医院",
    departmentId: "dept-cardio",
    departmentName: "心内科",
    wardId: "ward-cardio-1",
    wardName: "心内一病区",
    appointmentType: "INTERNAL",
    positionTitle: "主治医师",
    primary: true,
    status: "ACTIVE",
  },
  appointments: [],
  account: { userId: "doctor-1", username: "dr.wang", state: "RESET_REQUIRED" },
  identities: [
    {
      bindingId: "idb-1",
      userId: "doctor-1",
      providerType: "EMPLOYEE_NO",
      subjectHint: "****-001",
      status: "ACTIVE",
      version: 1,
      createdAt: "2026-06-11T00:00:00Z",
      updatedAt: "2026-06-11T00:00:00Z",
    },
  ],
  oneTimeActivation: null,
};

vi.mock("@/shared/api/hooks", () => ({
  downloadPersonnelImportTemplate: vi.fn(),
  useSecurityProfile: () => ({
    data: {
      permissions: [{ code: "org.read" }, { code: "org.write" }],
      roles: [{ code: "organization-admin" }],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  usePersonnel: () => personnelQuery,
  usePersonnelDetail: (personId: string | null) => ({
    data: personId ? detail : undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useComplianceUserDetail: (userId: string | null) => ({
    data: userId
      ? {
          userId,
          displayName: "王医生",
          username: "dr.wang",
          credentialManaged: true,
          status: "ACTIVE",
          mustChangePwd: true,
          roles: [
            {
              code: "clinical-decision-user",
              displayName: "临床医生",
              scopeLevel: "DEPARTMENT",
              scopeCode: "dept-cardio",
              scopeName: "心内科",
            },
          ],
          effectivePermissions: [
            {
              code: "context.read",
              dimension: "ACTION",
              target: "context",
              displayName: "查看临床上下文",
              risk: "LOW",
            },
          ],
        }
      : undefined,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useOrgUnits: () => ({
    data: {
      items: [
        {
          id: "hospital-a",
          orgPath: "/tenant-a/hospital-a",
          level: "FACILITY",
          code: "HOSP-A",
          name: "示范医院",
          status: "ACTIVE",
        },
        {
          id: "dept-cardio",
          orgPath: "/tenant-a/hospital-a/dept-cardio",
          level: "DEPARTMENT",
          code: "CARDIO",
          name: "心内科",
          status: "ACTIVE",
        },
        {
          id: "ward-cardio-1",
          orgPath: "/tenant-a/hospital-a/dept-cardio/ward-cardio-1",
          level: "WARD",
          code: "CARDIO-W1",
          name: "心内一病区",
          status: "ACTIVE",
        },
      ],
    },
    refetch: vi.fn(),
  }),
  useCreatePersonnel: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePreviewPersonnelImport: () => ({ mutateAsync: previewImport, isPending: false }),
  useCommitPersonnelImport: () => ({ mutateAsync: commitImport, isPending: false }),
  useAssignComplianceUserRole: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useRemoveComplianceUserRole: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useResetComplianceUserPassword: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSetComplianceUserStatus: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

import AdminUsers from "./AdminUsers";

function renderPage() {
  return render(
    <AntdApp>
      <AdminUsers />
    </AntdApp>,
  );
}

describe("人员与账号", () => {
  beforeEach(() => {
    previewImport.mockReset();
    commitImport.mockReset();
    refetchPersonnel.mockReset();
    personnelQuery = {
      ...personnelQuery,
      isLoading: false,
      isError: false,
      error: null,
      data: { ...personnelQuery.data, total: 1 },
    };
  });

  it("以人员、任职、账号和身份来源呈现机构主数据", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "人员与账号" })).toBeInTheDocument();
    expect(screen.getByText("王医生")).toBeInTheDocument();
    expect(screen.getByText("示范医院")).toBeInTheDocument();
    expect(screen.getByText("本机构员工")).toBeInTheDocument();
    expect(screen.getByText("待首次设置密码")).toBeInTheDocument();
    expect(screen.getByText("已绑定 1 个")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /批量导入人员/ })).toBeInTheDocument();
  });

  it("新增人员时组织范围来自组织树，不要求手填范围编码", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: /新增人员/ }));

    expect(screen.getByRole("dialog", { name: "新增人员" })).toBeInTheDocument();
    expect(screen.getByLabelText("所属机构")).toBeInTheDocument();
    expect(screen.getByLabelText("所属病区")).toBeInTheDocument();
    expect(screen.queryByLabelText("范围编码")).not.toBeInTheDocument();
    expect(screen.getByText("同时开通登录账号")).toBeInTheDocument();
    expect(screen.getByText("同时绑定院内身份来源")).toBeInTheDocument();
  });

  it("批量导入先预检并展示冲突，不直接写入", async () => {
    previewImport.mockResolvedValue({
      jobId: "job-1",
      fileName: "people.csv",
      status: "HAS_ISSUES",
      totalRows: 2,
      validRows: 1,
      conflictRows: 1,
      successRows: 0,
      failureRows: 0,
      rows: [
        {
          rowNo: 1,
          employeeNo: "EMP-001",
          displayName: "王医生",
          action: "UPDATE",
          status: "VALID",
          message: null,
          resultPersonId: null,
        },
        {
          rowNo: 2,
          employeeNo: "EMP-002",
          displayName: "李医生",
          action: "CONFLICT",
          status: "INVALID",
          message: "登录名已被其他账号使用",
          resultPersonId: null,
        },
      ],
      oneTimeActivations: [],
    });
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: /批量导入人员/ }));
    const fileInput = document.querySelector<HTMLInputElement>('input[type="file"]');
    if (!fileInput) throw new Error("未找到人员导入文件输入框");
    const file = new File(["employeeNo,displayName"], "people.csv", { type: "text/csv" });
    fireEvent.change(fileInput, { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: "开始预检" }));

    await waitFor(() => expect(previewImport).toHaveBeenCalledWith(file));
    expect(await screen.findByText("登录名已被其他账号使用")).toBeInTheDocument();
    expect(screen.getByText("请先修正冲突行再重新上传")).toBeInTheDocument();
    expect(commitImport).not.toHaveBeenCalled();
  });

  it("批量导入弹窗可取消并返回人员列表", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: /批量导入人员/ }));
    expect(screen.getByRole("dialog", { name: "批量导入人员" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "取消" }));

    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "批量导入人员" })).not.toBeInTheDocument(),
    );
  });

  it("详情使用中文层级和权限维度，不暴露英文枚举", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "查看" }));

    expect(await screen.findByText("角色与组织范围")).toBeInTheDocument();
    expect(screen.getByText("科室")).toBeInTheDocument();
    expect(screen.getAllByText("操作").length).toBeGreaterThan(0);
    expect(screen.queryByText("DEPARTMENT")).not.toBeInTheDocument();
    expect(screen.queryByText("ACTION")).not.toBeInTheDocument();
  });

  it("读取失败时提供真实重试入口", () => {
    personnelQuery = { ...personnelQuery, isError: true, error: new Error("unavailable") };
    renderPage();

    expect(screen.getByText("人员与账号读取失败")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /重\s*试/ }));
    expect(refetchPersonnel).toHaveBeenCalled();
  });
});
