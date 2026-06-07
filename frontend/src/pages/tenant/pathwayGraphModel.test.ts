import { describe, expect, it } from "vitest";

import {
  createConnectedEdge,
  removeNodeAtIndexWithEdges,
  resolveNodePosition,
  writeNodePosition,
} from "./pathwayGraphModel";

describe("pathwayGraphModel", () => {
  it("写入布局时保留节点既有业务配置", () => {
    const config = writeNodePosition(
      {
        clinicalGate: {
          requiresDoubleSign: true,
        },
      },
      { x: 240, y: 96 },
    );

    expect(config).toEqual({
      clinicalGate: {
        requiresDoubleSign: true,
      },
      authoringLayout: {
        x: 240,
        y: 96,
      },
    });
    expect(resolveNodePosition(config, 0)).toEqual({ x: 240, y: 96 });
  });

  it("连接两个节点时生成唯一标准边", () => {
    const edge = createConnectedEdge(
      [
        {
          edgeCode: "E1",
          fromNodeCode: "ASSESS",
          toNodeCode: "REVIEW",
          edgeType: "DEFAULT",
          priority: 1,
        },
      ],
      "REVIEW",
      "FOLLOWUP",
    );

    expect(edge).toEqual({
      edgeCode: "E2",
      fromNodeCode: "REVIEW",
      toNodeCode: "FOLLOWUP",
      edgeType: "DEFAULT",
      priority: 2,
    });
  });

  it("删除节点时同步删除引用该节点的边", () => {
    const result = removeNodeAtIndexWithEdges(
      [{ nodeCode: "ASSESS" }, { nodeCode: "REVIEW" }, { nodeCode: "FOLLOWUP" }],
      [
        { edgeCode: "E1", fromNodeCode: "ASSESS", toNodeCode: "REVIEW" },
        { edgeCode: "E2", fromNodeCode: "REVIEW", toNodeCode: "FOLLOWUP" },
        { edgeCode: "E3", fromNodeCode: "ASSESS", toNodeCode: "FOLLOWUP" },
      ],
      1,
    );

    expect(result.nodes.map((node) => node.nodeCode)).toEqual(["ASSESS", "FOLLOWUP"]);
    expect(result.edges.map((edge) => edge.edgeCode)).toEqual(["E3"]);
  });

  it("重复编码的非法中间态只删除所选节点且保留仍可解析的边", () => {
    const result = removeNodeAtIndexWithEdges(
      [{ nodeCode: "ASSESS" }, { nodeCode: "ASSESS" }, { nodeCode: "FOLLOWUP" }],
      [{ edgeCode: "E1", fromNodeCode: "ASSESS", toNodeCode: "FOLLOWUP" }],
      1,
    );

    expect(result.nodes.map((node) => node.nodeCode)).toEqual(["ASSESS", "FOLLOWUP"]);
    expect(result.edges.map((edge) => edge.edgeCode)).toEqual(["E1"]);
  });
});
