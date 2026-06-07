import { App as AntdApp } from "antd";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const createUserMock = vi.fn();
const assignRoleMock = vi.fn();
const resetPasswordMock = vi.fn();
const setStatusMock = vi.fn();
const refetchUsersMock = vi.fn();
const refetchDetailMock = vi.fn();

let usersQuery = {
  data: {
    items: [
      {
        userId: "doctor-1",
        displayName: "王医生",
        username: "dr.wang",
        credentialManaged: true,
        status: "ACTIVE",
        mustChangePwd: false,
        roles: [
          {
            code: "doctor",
            displayName: "临床医生",
            scopeLevel: "TENANT",
            scopeCode: "tenant-test",
          },
        ],
        createdAt: "2026-06-06T00:00:00Z",
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
  refetch: refetchUsersMock,
};

const detailQuery = {
  data: {
    userId: "doctor-1",
    displayName: "王医生",
    username: "dr.wang",
    credentialManaged: true,
    status: "ACTIVE",
    mustChangePwd: false,
    roles: [
      {
        code: "doctor",
        displayName: "临床医生",
        scopeLevel: "TENANT",
        scopeCode: "tenant-test",
      },
    ],
    effectivePermissions: [
      {
        code: "context.read",
        dimension: "ACTION",
        target: "context",
        displayName: "查看标准上下文",
        risk: "LOW",
      },
    ],
    createdAt: "2026-06-06T00:00:00Z",
    updatedAt: "2026-06-06T00:00:00Z",
  },
  isLoading: false,
  isError: false,
  refetch: refetchDetailMock,
};

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: {
      permissions: [{ code: "org.read" }, { code: "org.write" }],
      roles: [{ code: "hospital-admin" }],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useComplianceUsers: () => usersQuery,
  useComplianceUserDetail: (userId: string | null) => ({
    ...detailQuery,
    data: userId ? detailQuery.data : undefined,
  }),
  useCreateComplianceUser: () => ({ mutateAsync: createUserMock, isPending: false }),
  useAssignComplianceUserRole: () => ({ mutateAsync: assignRoleMock, isPending: false }),
  useRemoveComplianceUserRole: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useResetComplianceUserPassword: () => ({
    mutateAsync: resetPasswordMock,
    isPending: false,
  }),
  useSetComplianceUserStatus: () => ({ mutateAsync: setStatusMock, isPending: false }),
}));

import AdminUsers from "./AdminUsers";

function renderPage() {
  return render(
    <AntdApp>
      <AdminUsers />
    </AntdApp>,
  );
}

describe("AdminUsers · 统一用户管理", () => {
  beforeEach(() => {
    createUserMock.mockReset();
    assignRoleMock.mockReset();
    resetPasswordMock.mockReset();
    setStatusMock.mockReset();
    refetchUsersMock.mockReset();
    refetchDetailMock.mockReset();
    usersQuery = {
      ...usersQuery,
      data: {
        ...usersQuery.data,
        items: [
          {
            userId: "doctor-1",
            displayName: "王医生",
            username: "dr.wang",
            credentialManaged: true,
            status: "ACTIVE",
            mustChangePwd: false,
            roles: [
              {
                code: "doctor",
                displayName: "临床医生",
                scopeLevel: "TENANT",
                scopeCode: "tenant-test",
              },
            ],
            createdAt: "2026-06-06T00:00:00Z",
          },
        ],
        total: 1,
      },
      isLoading: false,
      isError: false,
      error: null,
    };
  });

  it("用一个服务端分页主表呈现账号、状态和真实角色", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "用户管理" })).toBeInTheDocument();
    expect(screen.getByText("dr.wang")).toBeInTheDocument();
    expect(screen.getByText("临床医生")).toBeInTheDocument();
    expect(screen.getByText("正常")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "新建用户" })).toBeInTheDocument();
    expect(screen.queryByText(/物理入库|角色分配台账|GA-SVC/)).not.toBeInTheDocument();
  });

  it("创建用户后一次性展示临时密码并刷新主表", async () => {
    createUserMock.mockResolvedValue({
      user: {
        ...detailQuery.data,
        userId: "nurse-1",
        displayName: "护士一",
        username: "nurse.one",
        credentialManaged: true,
      },
      tempPassword: "TmpPwd@2026!",
    });
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "新建用户" }));
    fireEvent.change(screen.getByLabelText("登录名"), { target: { value: "nurse.one" } });
    fireEvent.change(screen.getByLabelText("用户标识"), { target: { value: "nurse-1" } });
    fireEvent.click(screen.getByRole("button", { name: "创建" }));

    await waitFor(() =>
      expect(createUserMock).toHaveBeenCalledWith({
        credentialManaged: true,
        username: "nurse.one",
        userId: "nurse-1",
        displayName: undefined,
        roleCode: "doctor",
        initialPassword: undefined,
      }),
    );
    expect(await screen.findByText("TmpPwd@2026!")).toBeInTheDocument();
    expect(refetchUsersMock).toHaveBeenCalled();
  });

  it("创建外部身份用户时不要求登录名且不展示密码操作", async () => {
    createUserMock.mockResolvedValue({
      user: {
        ...detailQuery.data,
        userId: "external-1",
        displayName: "外部医生",
        username: null,
        credentialManaged: false,
      },
      tempPassword: null,
    });
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "新建用户" }));
    fireEvent.click(screen.getByText("外部身份"));
    fireEvent.change(screen.getByLabelText("显示名称"), { target: { value: "外部医生" } });
    fireEvent.change(screen.getByLabelText("用户标识"), { target: { value: "external-1" } });
    fireEvent.click(screen.getByRole("button", { name: "创建" }));

    await waitFor(() =>
      expect(createUserMock).toHaveBeenCalledWith({
        credentialManaged: false,
        username: undefined,
        userId: "external-1",
        displayName: "外部医生",
        roleCode: "doctor",
        initialPassword: undefined,
      }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "新建用户" })).not.toBeInTheDocument(),
    );
    expect(screen.queryByText("临时密码仅显示一次")).not.toBeInTheDocument();
  });

  it("在详情抽屉查看角色范围和后端计算的有效权限", async () => {
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "查看 doctor-1" }));

    expect(await screen.findByText("查看标准上下文")).toBeInTheDocument();
    expect(screen.getByText("tenant-test")).toBeInTheDocument();
    expect(screen.getByText("context.read")).toBeInTheDocument();
  });

  it("无用户时展示可操作的真实空状态", () => {
    usersQuery = {
      ...usersQuery,
      data: { ...usersQuery.data, items: [], total: 0 },
    };
    renderPage();

    expect(screen.getByText("暂无用户")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "新建用户" })).toBeInTheDocument();
  });

  it("读取失败时展示错误状态与重试动作", () => {
    usersQuery = {
      ...usersQuery,
      isError: true,
      error: new Error("service unavailable"),
    };
    renderPage();

    expect(screen.getByText("用户列表读取失败")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(refetchUsersMock).toHaveBeenCalled();
  });
});
