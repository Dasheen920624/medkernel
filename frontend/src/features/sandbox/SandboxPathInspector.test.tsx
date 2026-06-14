import { render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it } from "vitest";

import type { SandboxStepTrace } from "@/shared/api/hooks";
import SandboxPathInspector from "./SandboxPathInspector";

const steps: SandboxStepTrace[] = [
  {
    stage: "CONTEXT",
    endpoint: "/context",
    request: { patientId: "P-1" },
    response: { snapshotId: "ctx-1" },
    serverFacts: { qualityStatus: "VALID" },
    status: "OK",
  },
  {
    stage: "RECOMMENDATION",
    endpoint: "/recommendation",
    request: {},
    response: null,
    serverFacts: {},
    status: "FAIL",
    error: "规则资产未发布",
  },
];

describe("SandboxPathInspector", () => {
  it("shows an honest empty state before the first run", () => {
    render(
      <ConfigProvider>
        <SandboxPathInspector steps={[]} />
      </ConfigProvider>,
    );

    expect(screen.getByText("尚无运行轨迹")).toBeInTheDocument();
  });

  it("renders completed facts and the exact failing step", () => {
    render(
      <ConfigProvider>
        <SandboxPathInspector steps={steps} />
      </ConfigProvider>,
    );

    expect(screen.getAllByText("上下文快照")).not.toHaveLength(0);
    expect(screen.getAllByText("推荐评估")).not.toHaveLength(0);
    expect(screen.getByText("规则资产未发布")).toBeInTheDocument();
  });
});
