import { App as AntdApp } from "antd";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

const refetchSecurity = vi.fn();
const refetchDelegated = vi.fn();
const refetchBindings = vi.fn();
const refetchPersonnel = vi.fn();

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => ({
    data: {
      permissions: [{ code: "org.read" }, { code: "org.write" }],
      roles: [{ code: "platform-admin" }],
      menuKeys: ["identity-bindings"],
    },
    isLoading: false,
    isError: false,
    refetch: refetchSecurity,
  }),
  useDelegatedAuthStatus: () => ({
    data: {
      mode: "BOTH",
      enabled: true,
      status: "READY",
      providers: ["EMPLOYEE_NO", "SM_CA"],
      message: "统一身份服务已接通",
    },
    isLoading: false,
    isError: false,
    refetch: refetchDelegated,
  }),
  useIdentityBindings: () => ({
    data: {
      items: [
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
      page: 1,
      size: 20,
      total: 1,
      hasNext: false,
      totalEstimated: false,
    },
    isLoading: false,
    isError: false,
    refetch: refetchBindings,
  }),
  usePersonnel: () => ({
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
          accountState: "ACTIVE",
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
    refetch: refetchPersonnel,
  }),
  useCreateIdentityBinding: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnbindIdentityBinding: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePreviewPersonnelImport: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useCommitPersonnelImport: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

import IdentityBinding from "./IdentityBinding";

function renderPage() {
  return render(
    <AntdApp>
      <IdentityBinding />
    </AntdApp>,
  );
}

describe("身份来源", () => {
  beforeEach(() => {
    useEvidenceDetailsStore.getState().setEnabled(false);
    window.localStorage.clear();
    refetchSecurity.mockReset();
    refetchDelegated.mockReset();
    refetchBindings.mockReset();
    refetchPersonnel.mockReset();
  });

  it("默认以人员档案和身份关系呈现，不暴露人员编号或身份提示", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "身份来源" })).toBeInTheDocument();
    expect(screen.getByText("王医生")).toBeInTheDocument();
    expect(screen.getByText("人员档案已登记")).toBeInTheDocument();
    expect(screen.getByText("身份已绑定")).toBeInTheDocument();
    expect(screen.queryByText("人员编号：EMP-001")).not.toBeInTheDocument();
    expect(screen.queryByText("****-001")).not.toBeInTheDocument();
  });

  it("证据详情打开后展示人员编号和脱敏身份提示", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();
    expect(screen.queryByText("人员编号：EMP-001")).not.toBeInTheDocument();
    expect(screen.queryByText("****-001")).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("人员编号：EMP-001")).toBeInTheDocument();
    expect(screen.getByText("****-001")).toBeInTheDocument();
  });

  it("单个绑定和批量匹配使用院内人员身份的业务语言", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: /单个绑定/ }));

    expect(screen.getByRole("dialog", { name: "单个绑定身份来源" })).toBeInTheDocument();
    expect(screen.getByText("人员账号")).toBeInTheDocument();
    expect(screen.getByText("院内人员身份")).toBeInTheDocument();
    expect(screen.queryByText("院内身份标识")).not.toBeInTheDocument();
    expect(screen.getByText("按姓名或院内人员身份搜索")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /取\s*消|取消/ }));
    await user.click(screen.getByRole("button", { name: /批量匹配身份/ }));

    expect(screen.getByRole("dialog", { name: "批量匹配身份来源" })).toBeInTheDocument();
    expect(screen.getByText("按院内人员身份批量匹配，先预检后提交")).toBeInTheDocument();
    expect(screen.queryByText("院内身份标识")).not.toBeInTheDocument();
  });
});
