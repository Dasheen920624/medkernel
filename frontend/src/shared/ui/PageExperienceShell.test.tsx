import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { beforeEach, describe, expect, it } from "vitest";

import type { RouteExperience } from "./experienceTypes";
import { PageExperienceShell } from "./PageExperienceShell";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

const experience: RouteExperience = {
  primaryRole: "医疗引擎运营员",
  goal: "核查映射风险",
  defaultView: "最近更新",
  defaultFilters: [],
  evidenceDetailContent: ["traceId"],
  interruptionLevel: "info",
  evidence: "来源和审计",
  dataScale: { expected: "large", pagination: "page", exportStrategy: "disabled" },
  riskLevel: "medium",
};

const multiStakeholderExperience = {
  ...experience,
  stakeholderViews: [
    {
      role: "医生",
      responsibility: "确认高风险提醒并登记采纳或不采纳理由",
      boundary: "不会自动生成医嘱",
    },
    {
      role: "药师",
      responsibility: "复核联合用药风险",
      boundary: "只记录复核意见，不替代医师确认",
    },
  ],
} as RouteExperience;

const evidenceDetailProfile = {
  permissions: [
    {
      code: "advanced.read",
      dimension: "ACTION",
      target: "advanced",
      displayName: "证据详情",
      risk: "LOW",
    },
  ],
  menuKeys: ["provenance"],
};
const normalProfile = { permissions: [], menuKeys: [] };

describe("PageExperienceShell", () => {
  beforeEach(() => {
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
  });

  it("discloses evidence details only for an authorized profile", () => {
    const { rerender } = render(
      <ConfigProvider>
        <PageExperienceShell
          meta={{ title: "字典映射", experience }}
          securityProfile={evidenceDetailProfile}
        >
          内容
        </PageExperienceShell>
      </ConfigProvider>,
    );

    expect(screen.getByText("核查映射风险")).toBeInTheDocument();
    expect(screen.queryByText("主要角色：医疗引擎运营员")).not.toBeInTheDocument();
    expect(screen.queryByText("默认视图：最近更新")).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "证据详情" })).toBeInTheDocument();

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

    expect(screen.queryByRole("switch", { name: "证据详情" })).not.toBeInTheDocument();
  });

  it("uses one shared evidence details preference across page shells", async () => {
    const user = userEvent.setup();

    render(
      <ConfigProvider>
        <PageExperienceShell
          meta={{ title: "审计", experience }}
          securityProfile={evidenceDetailProfile}
        >
          审计内容
        </PageExperienceShell>
        <PageExperienceShell
          meta={{ title: "溯源", experience }}
          securityProfile={evidenceDetailProfile}
        >
          溯源内容
        </PageExperienceShell>
      </ConfigProvider>,
    );

    const switches = screen.getAllByRole("switch", { name: "证据详情" });
    expect(switches[0]).not.toBeChecked();
    expect(switches[1]).not.toBeChecked();

    await user.click(switches[0]);

    expect(switches[0]).toBeChecked();
    expect(switches[1]).toBeChecked();
    expect(window.localStorage.getItem("medkernel.evidence-details.enabled")).toBe("true");
  });

  it("renders stakeholder responsibilities when a page serves multiple frontline roles", () => {
    render(
      <ConfigProvider>
        <PageExperienceShell
          meta={{ title: "提醒与推荐", experience: multiStakeholderExperience }}
          securityProfile={evidenceDetailProfile}
        >
          推荐内容
        </PageExperienceShell>
      </ConfigProvider>,
    );

    expect(screen.getByText("角色视角")).toBeInTheDocument();
    expect(screen.getByText("医生")).toBeInTheDocument();
    expect(screen.getByText("确认高风险提醒并登记采纳或不采纳理由")).toBeInTheDocument();
    expect(screen.getByText("不会自动生成医嘱")).toBeInTheDocument();
    expect(screen.getByText("药师")).toBeInTheDocument();
    expect(screen.getByText("复核联合用药风险")).toBeInTheDocument();
    expect(screen.getByText("只记录复核意见，不替代医师确认")).toBeInTheDocument();
  });
});
