import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

const createMemberMock = vi.fn();
const refetchCredentials = vi.fn();
const provisionTenantMock = vi.fn();
let roleAssignmentsMock: Array<{
  id: number;
  tenantId: string;
  userId: string;
  roleCode: string;
  scopeLevel: string;
  scopeCode: string;
  activeFlag: string;
  createdBy: string;
}> = [];
let credentialsMock: Array<{
  userId: string;
  username: string;
  status: string;
  mustChangePwd: boolean;
  createdAt: string;
}> = [];

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: {
      dataScope: {
        tenantId: "tenant-test",
      },
    },
  }),
  useUserRoleAssignments: () => ({ data: roleAssignmentsMock, isLoading: false, refetch: vi.fn() }),
  useCreateUserRoleAssignment: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useDeleteUserRoleAssignment: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePlatformCredentials: () => ({ data: credentialsMock, refetch: refetchCredentials }),
  useCreateMember: () => ({ mutateAsync: createMemberMock, isPending: false }),
  useResetMemberPassword: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useSetCredentialStatus: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useTenants: () => ({ data: [], refetch: vi.fn() }),
  useProvisionTenant: () => ({ mutateAsync: provisionTenantMock, isPending: false }),
}));

import AdminUsers from "./AdminUsers";

describe("AdminUsers · 成员账号管理", () => {
  beforeEach(() => {
    createMemberMock.mockReset();
    refetchCredentials.mockReset();
    provisionTenantMock.mockReset();
    roleAssignmentsMock = [];
    credentialsMock = [];
  });

  it("开通租户后展示管理员临时密码与登录租户提示", async () => {
    provisionTenantMock.mockResolvedValue({
      tenantId: "t-renmin",
      adminUserId: "renmin-admin",
      adminUsername: "renmin-admin",
      tempPassword: "TenantPwd@9",
    });
    render(<AdminUsers />);

    fireEvent.click(screen.getByRole("button", { name: "开通新租户" }));
    fireEvent.change(screen.getByPlaceholderText("如 t-renmin"), {
      target: { value: "t-renmin" },
    });
    fireEvent.change(screen.getByPlaceholderText("如 人民医院"), {
      target: { value: "人民医院" },
    });
    fireEvent.change(screen.getByPlaceholderText("如 renmin-admin"), {
      target: { value: "renmin-admin" },
    });
    fireEvent.click(screen.getByRole("button", { name: "确认开通租户" }));

    await waitFor(() =>
      expect(provisionTenantMock).toHaveBeenCalledWith({
        tenantId: "t-renmin",
        tenantName: "人民医院",
        adminUsername: "renmin-admin",
      }),
    );
    expect(await screen.findByText(/临时密码：TenantPwd@9/)).toBeInTheDocument();
  });

  it("开通成员后展示一次性临时密码", async () => {
    createMemberMock.mockResolvedValue({
      userId: "drwang",
      username: "drwang",
      tempPassword: "TmpPwd@123",
    });
    render(<AdminUsers />);

    fireEvent.click(screen.getByRole("button", { name: "开通新成员" }));
    fireEvent.change(screen.getByPlaceholderText("如 drwang"), { target: { value: "drwang" } });
    fireEvent.click(screen.getByRole("button", { name: "确认开通成员" }));

    await waitFor(() =>
      expect(createMemberMock).toHaveBeenCalledWith({
        username: "drwang",
        roleCode: "doctor",
        initialPassword: undefined,
      }),
    );
    expect(await screen.findByText(/临时密码：TmpPwd@123/)).toBeInTheDocument();
  });

  it("登录名为空时拦截提交且不调用接口", async () => {
    render(<AdminUsers />);
    fireEvent.click(screen.getByRole("button", { name: "开通新成员" }));
    fireEvent.click(screen.getByRole("button", { name: "确认开通成员" }));
    await waitFor(() => expect(screen.getByText("登录名不能为空")).toBeInTheDocument());
    expect(createMemberMock).not.toHaveBeenCalled();
  });

  it("内置超级管理员在账号和角色台账中展示为系统内置不可编辑", () => {
    roleAssignmentsMock = [
      {
        id: 44,
        tenantId: "t-1",
        userId: "system-superadmin-1",
        roleCode: "system-superadmin",
        scopeLevel: "TENANT",
        scopeCode: "t-1",
        activeFlag: "Y",
        createdBy: "migration-v44",
      },
    ];
    credentialsMock = [
      {
        userId: "system-superadmin-1",
        username: "system-superadmin",
        status: "ACTIVE",
        mustChangePwd: true,
        createdAt: "2026-06-02T00:00:00Z",
      },
    ];

    render(<AdminUsers />);

    expect(screen.getByText("内置超级管理员")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "系统内置" })).toHaveLength(2);
    screen.getAllByRole("button", { name: "系统内置" }).forEach((button) => {
      expect(button).toBeDisabled();
    });
    expect(screen.queryByRole("button", { name: "解除绑定" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "停用" })).not.toBeInTheDocument();
  });
});
