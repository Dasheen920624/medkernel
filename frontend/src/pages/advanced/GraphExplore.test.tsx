import { ConfigProvider } from "antd";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

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
}));

vi.mock("@/shared/api/hooks", () => ({
  useProjectionRuntimeStatus: () => apiMocks.runtimeStatus,
  useProjectionConsistency: () => apiMocks.consistency,
  useProjectionFacts: () => apiMocks.facts,
  useRebuildProjection: () => apiMocks.rebuild,
}));

describe("GraphExplore", () => {
  it("renders projection runtime facts instead of a static graph demo shell", () => {
    render(
      <ConfigProvider>
        <GraphExplore />
      </ConfigProvider>,
    );

    expect(screen.getByRole("heading", { name: "图谱查询" })).toBeInTheDocument();
    expect(screen.getByText("关系库权威源已有临床图投影快照")).toBeInTheDocument();
    expect(screen.getAllByText("关系库权威源与投影一致").length).toBeGreaterThan(0);
    expect(screen.getByText("NODE:OBSERVATION:obs-1")).toBeInTheDocument();
    expect(screen.getByText("trace-graph-1")).toBeInTheDocument();
    expect(
      within(screen.getByTestId("projection-facts-table")).getByText("OBSERVATION"),
    ).toBeInTheDocument();

    expect(screen.queryByText(/胸痛|阿司匹林|高级工具骨架|Neo4j 5\.23/)).not.toBeInTheDocument();
  });
});
