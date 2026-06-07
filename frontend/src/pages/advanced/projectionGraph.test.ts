import { describe, expect, it } from "vitest";

import type { ProjectionFactItem } from "@/shared/api/hooks";
import {
  buildProjectionGraph,
  projectionObjectLabel,
  projectionPredicateLabel,
} from "./projectionGraph";

describe("projection graph model", () => {
  it("builds referenced endpoint nodes and a readable edge from real projection facts", () => {
    const edge: ProjectionFactItem = {
      factKey: "EDGE:PATIENT:pat-1:HAS_RESOURCE:OBSERVATION:obs-1",
      factKind: "EDGE",
      objectType: "RELATION",
      objectId: "PATIENT:pat-1:HAS_RESOURCE:OBSERVATION:obs-1",
      subjectKey: "PATIENT:pat-1",
      predicate: "HAS_RESOURCE",
      objectKey: "OBSERVATION:obs-1",
      contentHash: "hash",
      sourceUpdatedAt: "2026-06-01T00:00:00Z",
      syncedAt: "2026-06-01T00:01:00Z",
      traceId: "trace-edge",
    };

    const graph = buildProjectionGraph([edge]);

    expect(graph.nodes).toHaveLength(2);
    expect(graph.nodes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ key: "PATIENT:pat-1", label: "患者", referenceOnly: true }),
        expect.objectContaining({
          key: "OBSERVATION:obs-1",
          label: "观察记录",
          referenceOnly: true,
        }),
      ]),
    );
    expect(graph.edges).toEqual([
      expect.objectContaining({
        source: "PATIENT:pat-1",
        target: "OBSERVATION:obs-1",
        label: "包含资源",
      }),
    ]);
  });

  it("uses readable labels while keeping unknown projection vocabulary visible", () => {
    expect(projectionObjectLabel("KNOWLEDGE_VERSION")).toBe("知识版本");
    expect(projectionPredicateLabel("CITES_FRAGMENT")).toBe("引用片段");
    expect(projectionObjectLabel("CUSTOM_NODE")).toBe("CUSTOM_NODE");
  });
});
