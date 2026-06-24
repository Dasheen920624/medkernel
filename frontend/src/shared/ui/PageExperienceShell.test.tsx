import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it } from "vitest";

import type { RouteExperience } from "./experienceTypes";
import { PageExperienceShell } from "./PageExperienceShell";
import { useExpertModeStore } from "@/shared/lib/expertModeStore";

const experience: RouteExperience = {
  primaryRole: "医疗引擎运营员",
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
  beforeEach(() => {
    window.localStorage.clear();
    useExpertModeStore.setState({ enabled: false });
  });

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
    expect(screen.queryByText("主要角色：医疗引擎运营员")).not.toBeInTheDocument();
    expect(screen.queryByText("默认视图：最近更新")).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "高级信息" })).toBeInTheDocument();

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

    expect(screen.queryByRole("switch", { name: "高级信息" })).not.toBeInTheDocument();
  });

  it("uses one shared expert mode preference across page shells", async () => {
    const user = userEvent.setup();

    render(
      <ConfigProvider>
        <PageExperienceShell meta={{ title: "审计", experience }} securityProfile={expertProfile}>
          审计内容
        </PageExperienceShell>
        <PageExperienceShell meta={{ title: "溯源", experience }} securityProfile={expertProfile}>
          溯源内容
        </PageExperienceShell>
      </ConfigProvider>,
    );

    const switches = screen.getAllByRole("switch", { name: "高级信息" });
    expect(switches[0]).not.toBeChecked();
    expect(switches[1]).not.toBeChecked();

    await user.click(switches[0]);

    expect(switches[0]).toBeChecked();
    expect(switches[1]).toBeChecked();
    expect(window.localStorage.getItem("medkernel.expert-mode.enabled")).toBe("true");
  });
});
