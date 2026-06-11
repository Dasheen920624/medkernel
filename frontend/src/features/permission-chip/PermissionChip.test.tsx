import { fireEvent, render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PermissionChip } from "./PermissionChip";

const securityProfileState = vi.hoisted(() => ({
  value: {
    data: {
      userId: "auditor-01",
      roles: [
        {
          code: "compliance-auditor",
          displayName: "合规审计",
          source: "ASSIGNMENT",
          scopeLevel: "DEPARTMENT",
          scopeCode: "quality",
        },
      ],
      permissions: [
        {
          code: "audit.read",
          dimension: "ACTION",
          target: "audit",
          displayName: "查看审计日志",
          risk: "LOW",
        },
        {
          code: "audit.export",
          dimension: "ACTION",
          target: "audit",
          displayName: "导出审计日志",
          risk: "HIGH",
        },
        {
          code: "data.desensitized",
          dimension: "DATA",
          target: "desensitized",
          displayName: "访问脱敏数据",
          risk: "LOW",
        },
        {
          code: "env.production",
          dimension: "ENVIRONMENT",
          target: "production",
          displayName: "访问正式环境",
          risk: "HIGH",
        },
      ],
      menuKeys: ["admin-audit"],
      environmentKeys: ["production", "emergency"],
      dataScope: {
        tenantId: "tenant-a",
        hospitalId: "hospital-a",
        departmentId: "quality",
      },
    },
  },
}));

vi.mock("@/shared/api/hooks", () => ({
  useSecurityProfile: () => securityProfileState.value,
}));

afterEach(() => {
  securityProfileState.value.data.roles = [
    {
      code: "compliance-auditor",
      displayName: "合规审计",
      source: "ASSIGNMENT",
      scopeLevel: "DEPARTMENT",
      scopeCode: "quality",
    },
  ];
});

describe("PermissionChip", () => {
  it("shows effective role, action permissions and data scope from the security profile", () => {
    render(
      <ConfigProvider>
        <PermissionChip />
      </ConfigProvider>,
    );

    expect(screen.getByText("合规审计")).toBeInTheDocument();
    fireEvent.click(screen.getByText("合规审计"));

    expect(screen.getByText("导出审计日志")).toBeInTheDocument();
    expect(screen.getByText("菜单与范围")).toBeInTheDocument();
    expect(screen.getByText("访问脱敏数据")).toBeInTheDocument();
    expect(screen.getByText("访问正式环境")).toBeInTheDocument();
    expect(screen.getByText(/可用环境：正式环境 \/ 应急环境/)).toBeInTheDocument();
    expect(screen.getByText(/科室 quality/)).toBeInTheDocument();
  });

  it("distinguishes loaded profile without an assigned role from loading state", () => {
    securityProfileState.value.data.roles = [];

    render(
      <ConfigProvider>
        <PermissionChip />
      </ConfigProvider>,
    );

    expect(screen.getByText("未分配角色")).toBeInTheDocument();
    expect(screen.queryByText("权限读取中")).toBeNull();
  });
});
