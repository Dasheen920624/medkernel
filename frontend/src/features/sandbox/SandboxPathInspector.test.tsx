import { fireEvent, render, screen } from "@testing-library/react";
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
    expect(screen.getAllByText("规则资产未发布").length).toBeGreaterThan(0);
  });

  it("does not expose raw request payloads in the default path evidence view", () => {
    render(
      <ConfigProvider>
        <SandboxPathInspector steps={steps} />
      </ConfigProvider>,
    );

    expect(screen.queryByText("调用地址")).not.toBeInTheDocument();
    expect(screen.queryByText("/context")).not.toBeInTheDocument();
    expect(screen.queryByText((text) => text.includes("P-1"))).not.toBeInTheDocument();
    expect(screen.queryByText((text) => text.includes("patientId"))).not.toBeInTheDocument();
  });

  it("shows controlled technical evidence only after evidence details are enabled", () => {
    render(
      <ConfigProvider>
        <SandboxPathInspector
          steps={[
            {
              stage: "CONTEXT",
              endpoint: "/context",
              request: { patientId: "P-1", ruleName: "高钾规则" },
              response: { embedUrl: "/embed/launch?token=token-1" },
              serverFacts: { patientName: "张三", ruleName: "高钾规则" },
              status: "OK",
            },
          ]}
          evidenceDetailsEnabled
        />
      </ConfigProvider>,
    );

    fireEvent.click(screen.getAllByText("上下文快照").at(-1) as HTMLElement);

    expect(screen.getByText("调用地址")).toBeInTheDocument();
    expect(screen.getByText("/context")).toBeInTheDocument();
    expect(screen.getAllByText((text) => text.includes("高钾规则")).length).toBeGreaterThan(0);
    expect(screen.getAllByText((text) => text.includes("已脱敏")).length).toBeGreaterThan(0);
    expect(screen.queryByText((text) => text.includes("P-1"))).not.toBeInTheDocument();
    expect(screen.queryByText((text) => text.includes("token-1"))).not.toBeInTheDocument();
    expect(screen.queryByText((text) => text.includes("张三"))).not.toBeInTheDocument();
  });
});
