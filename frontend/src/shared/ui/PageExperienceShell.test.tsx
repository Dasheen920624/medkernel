import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it } from "vitest";

import type { RouteExperience } from "./experienceTypes";
import { PageExperienceShell } from "./PageExperienceShell";

const experience: RouteExperience = {
  primaryRole: "实施运维员",
  goal: "核查映射风险",
  defaultView: "最近更新",
  defaultFilters: [],
  expertContent: ["traceId"],
  interruptionLevel: "info",
  evidence: "来源和审计",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "disabled" },
  riskLevel: "medium",
};

const expertProfile = {
  permissions: [
    {
      code: "advanced.read",
      dimension: "ACTION",
      target: "advanced",
      displayName: "高级工具",
      risk: "LOW",
    },
  ],
  menuKeys: ["provenance"],
};
const normalProfile = { permissions: [], menuKeys: [] };

describe("PageExperienceShell", () => {
  it("discloses expert mode only for an authorized profile", () => {
    const { rerender } = render(
      <ConfigProvider>
        <PageExperienceShell
          meta={{ title: "字典映射", experience }}
          securityProfile={expertProfile}
        >
          内容
        </PageExperienceShell>
      </ConfigProvider>,
    );

    expect(screen.getByText("核查映射风险")).toBeInTheDocument();
    expect(screen.queryByText("主要角色：实施运维员")).not.toBeInTheDocument();
    expect(screen.queryByText("默认视图：最近更新")).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "专家模式" })).toBeInTheDocument();

    rerender(
      <ConfigProvider>
        <PageExperienceShell
          meta={{ title: "字典映射", experience }}
          securityProfile={normalProfile}
        >
          内容
        </PageExperienceShell>
      </ConfigProvider>,
    );

    expect(screen.queryByRole("switch", { name: "专家模式" })).not.toBeInTheDocument();
  });
});
