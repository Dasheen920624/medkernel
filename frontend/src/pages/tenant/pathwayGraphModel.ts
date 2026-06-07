export type PathwayGraphPosition = {
  x: number;
  y: number;
};

type NodeWithCode = {
  nodeCode?: string;
};

type EdgeWithEndpoints = {
  edgeCode?: string;
  fromNodeCode?: string;
  toNodeCode?: string;
};

type ConnectedEdge = {
  edgeCode: string;
  fromNodeCode: string;
  toNodeCode: string;
  edgeType: "DEFAULT";
  priority: number;
};

const GRAPH_COLUMNS = 4;
const GRAPH_COLUMN_GAP = 240;
const GRAPH_ROW_GAP = 144;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

export function defaultNodePosition(index: number): PathwayGraphPosition {
  return {
    x: (index % GRAPH_COLUMNS) * GRAPH_COLUMN_GAP,
    y: Math.floor(index / GRAPH_COLUMNS) * GRAPH_ROW_GAP,
  };
}

export function resolveNodePosition(config: unknown, index: number): PathwayGraphPosition {
  if (isRecord(config) && isRecord(config.authoringLayout)) {
    const { x, y } = config.authoringLayout;
    if (isFiniteNumber(x) && isFiniteNumber(y)) {
      return { x, y };
    }
  }
  return defaultNodePosition(index);
}

export function writeNodePosition(
  config: unknown,
  position: PathwayGraphPosition,
): Record<string, unknown> {
  return {
    ...(isRecord(config) ? config : {}),
    authoringLayout: {
      x: position.x,
      y: position.y,
    },
  };
}

export function createConnectedEdge<T extends EdgeWithEndpoints>(
  edges: T[],
  sourceNodeCode: string,
  targetNodeCode: string,
): ConnectedEdge {
  const usedCodes = new Set(edges.map((edge) => edge.edgeCode).filter(Boolean));
  let sequence = 1;
  while (usedCodes.has(`E${sequence}`)) sequence += 1;

  const maxPriority = edges.reduce((current, edge, index) => {
    const priority =
      "priority" in edge && typeof edge.priority === "number" ? edge.priority : index + 1;
    return Math.max(current, priority);
  }, 0);

  return {
    edgeCode: `E${sequence}`,
    fromNodeCode: sourceNodeCode,
    toNodeCode: targetNodeCode,
    edgeType: "DEFAULT",
    priority: maxPriority + 1,
  };
}

export function removeNodeAtIndexWithEdges<
  TNode extends NodeWithCode,
  TEdge extends EdgeWithEndpoints,
>(nodes: TNode[], edges: TEdge[], nodeIndex: number) {
  const nodeCode = nodes[nodeIndex]?.nodeCode;
  const nextNodes = nodes.filter((_, index) => index !== nodeIndex);
  const codeStillExists = Boolean(nodeCode) && nextNodes.some((node) => node.nodeCode === nodeCode);

  return {
    nodes: nextNodes,
    edges:
      !nodeCode || codeStillExists
        ? edges
        : edges.filter((edge) => edge.fromNodeCode !== nodeCode && edge.toNodeCode !== nodeCode),
  };
}
