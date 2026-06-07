import { ConfigProvider } from "antd";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import GraphExplore from "./GraphExplore";

const apiMocks = vi.hoisted(() => ({
  runtimeStatus: {
    data: {
      targetType: "CLINICAL_GRAPH",
      tenantId: "tenant-A",
      graphProjectionEnabled: true,
      difyWorkflowEnabled: false,
      clinicalProjectionStatus: "READY",
      difyExecutionStatus: "NOT_CONNECTED",
      snapshotCount: 2,
      message: "关系库权威源已有临床图投影快照",
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
  consistency: {
    data: {
      targetType: "CLINICAL_GRAPH",
      tenantId: "tenant-A",
      status: "SUCCESS",
      message: "关系库权威源与投影一致",
      consistent: true,
      sourceCount: 2,
      projectionCount: 2,
      sourceHash: "source-hash",
      projectionHash: "projection-hash",
      missing: [],
      extra: [],
      changed: [],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
  facts: {
    data: {
      items: [
        {
          factKey: "NODE:OBSERVATION:obs-1",
          factKind: "NODE",
          objectType: "OBSERVATION",
          objectId: "obs-1",
          subjectKey: null,
          predicate: null,
          objectKey: null,
          contentHash: "f".repeat(64),
          sourceUpdatedAt: "2026-06-01T00:00:00Z",
          syncedAt: "2026-06-01T00:01:00Z",
          traceId: "trace-graph-1",
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
    refetch: vi.fn(),
  },
  rebuild: {
    mutateAsync: vi.fn(),
    isPending: false,
  },
  security: {
    data: {
      userId: "user-1",
      username: "expert",
      roles: [{ code: "specialist" }],
      permissions: [{ code: "projection.read" }],
      menuKeys: ["graph-explore"],
    },
    isLoading: false,
    isError: false,
  },
}));

vi.mock("@/shared/api/hooks", () => ({
  useProjectionRuntimeStatus: () => apiMocks.runtimeStatus,
  useProjectionConsistency: () => apiMocks.consistency,
  useProjectionFacts: () => apiMocks.facts,
  useRebuildProjection: () => apiMocks.rebuild,
  useSecurityProfile: () => apiMocks.security,
}));

describe("GraphExplore", () => {
  beforeEach(() => {
    apiMocks.consistency.isError = false;
    apiMocks.rebuild.mutateAsync.mockReset();
    apiMocks.security.data.permissions = [{ code: "projection.read" }];
  });

  it("renders real projection facts as an explorable graph without exposing rebuild to read-only users", async () => {
    const user = userEvent.setup();
    render(
      <ConfigProvider>
        <GraphExplore />
      </ConfigProvider>,
    );

    expect(screen.getByRole("heading", { name: "图谱查询" })).toBeInTheDocument();
    expect(screen.getByText("关系库权威源已有临床图投影快照")).toBeInTheDocument();
    expect(screen.getAllByText("关系库权威源与投影一致").length).toBeGreaterThan(0);
    expect(screen.getByRole("group", { name: "投影关系图" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /观察记录 obs-1/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重建投影" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /观察记录 obs-1/ }));

    expect(screen.getByText("trace-graph-1")).toBeInTheDocument();
    expect(screen.getAllByText("obs-1").length).toBeGreaterThan(1);

    expect(screen.queryByText(/胸痛|阿司匹林|高级工具骨架|Neo4j 5\.23/)).not.toBeInTheDocument();
  });

  it("shows the high-risk rebuild action only to users with projection rebuild permission", async () => {
    const user = userEvent.setup();
    apiMocks.security.data.permissions = [
      { code: "projection.read" },
      { code: "projection.rebuild" },
    ];

    render(
      <ConfigProvider>
        <GraphExplore />
      </ConfigProvider>,
    );

    await user.click(screen.getByRole("tab", { name: "一致性差异 (0)" }));

    expect(screen.getByRole("button", { name: "重建投影" })).toBeInTheDocument();
  });

  it("keeps real facts usable when consistency status is temporarily unavailable", () => {
    apiMocks.consistency.isError = true;

    render(
      <ConfigProvider>
        <GraphExplore />
      </ConfigProvider>,
    );

    expect(screen.getByText("部分状态暂不可用")).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "投影关系图" })).toBeInTheDocument();
  });
});
