import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { StepFlow } from "./StepFlow";
import { SEVEN_STEPS, STEP_CHANGE_STATUS } from "./StepFlow.contract";

describe("StepFlow", () => {
  it("locks the exact 7-step configuration flow from the constitution", () => {
    expect(SEVEN_STEPS.map((step) => step.title)).toEqual([
      "选模板/导入",
      "自动校验",
      "看影响",
      "技术验证",
      "灰度发布",
      "全量",
      "留证据/可回滚",
    ]);
    expect(STEP_CHANGE_STATUS).toEqual({
      select_template: "pending",
      auto_validate: "pending",
      impact_preview: "pending",
      submit_review: "pending",
      canary_release: "canary",
      full_rollout: "rolled_out",
      evidence_rollback: "rolled_back",
    });

    render(<StepFlow currentStep="impact_preview" />);
    SEVEN_STEPS.forEach((s) => {
      expect(screen.getAllByText(s.title).length).toBeGreaterThan(0);
    });
    expect(screen.getByText("待发布")).toBeInTheDocument();
    expect(screen.getByText("当前授权责任人完成技术验证")).toBeInTheDocument();
    expect(screen.queryByText(/医务处|信息科主任|多人审核/)).not.toBeInTheDocument();
  });

  it("renders the panel for current step", () => {
    render(
      <StepFlow
        currentStep="auto_validate"
        panelByStep={{ auto_validate: <div data-testid="my-panel">校验通过</div> }}
      />,
    );
    expect(screen.getByTestId("my-panel")).toBeInTheDocument();
  });

  it("uses clean generic guidance when no panel is provided", () => {
    render(<StepFlow currentStep="full_rollout" />);
    expect(screen.getByText(/请在当前步骤展示真实/)).toBeInTheDocument();
    expect(screen.queryByText(/GA-/)).toBeNull();
    expect(screen.getAllByText("全量").length).toBeGreaterThan(0);
  });
});
