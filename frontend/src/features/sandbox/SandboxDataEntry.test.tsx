import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { describe, expect, it, vi } from "vitest";

import type { NumericSandboxScenario } from "./sandboxScenarios";
import SandboxDataEntry from "./SandboxDataEntry";

const numericScenario: NumericSandboxScenario = {
  id: "sbx-lab-critical-k",
  servicePackage: "clinical-collaboration",
  engine: "rule",
  playbook: "RULE_ONLY",
  triggerPoint: "result-review",
  title: "受控数值场景",
  narrative: "后端目录描述。",
  hostSummary: "院内业务系统检验复核",
  expectedRuleCode: "SBX.LAB.CRITICAL.K",
  expectedAction: "STRONG_REMINDER",
  expectedSeverity: "CRITICAL",
  status: "runtime-check",
  statusReason: "可运行",
  inputKind: "numeric",
  patientId: "patient-1",
  patientName: "沙盘患者",
  encounterId: "encounter-1",
  encounterType: "ED",
  observationCode: "2823-3",
  observationName: "检验结果",
  defaultNumericValue: 6.8,
  minValue: 1,
  maxValue: 12,
  step: 0.1,
  unit: "mmol/L",
  referenceRange: "3.5-5.5",
  upperReferenceValue: 5.5,
};

describe("SandboxDataEntry", () => {
  it("lets the host edit the laboratory value before triggering the real engine", async () => {
    const onRun = vi.fn();
    render(
      <ConfigProvider>
        <SandboxDataEntry scenario={numericScenario} running={false} onRun={onRun} />
      </ConfigProvider>,
    );

    expect(screen.getByText(/检验复核/)).toBeInTheDocument();
    const input = screen.getByRole("spinbutton", { name: "检验结果" });
    await userEvent.clear(input);
    await userEvent.type(input, "7.1");
    await userEvent.click(screen.getByRole("button", { name: /触发 MedKernel/ }));

    expect(onRun).toHaveBeenCalledWith(
      expect.objectContaining({
        numericValue: 7.1,
      }),
    );
  });

  it("locks the trigger while a run is pending", () => {
    render(
      <ConfigProvider>
        <SandboxDataEntry scenario={numericScenario} running onRun={vi.fn()} />
      </ConfigProvider>,
    );

    expect(screen.getByRole("button", { name: /运行中/ })).toBeDisabled();
  });
});
