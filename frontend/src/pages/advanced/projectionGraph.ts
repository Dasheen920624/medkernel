import type { ProjectionFactItem } from "@/shared/api/hooks";

export interface ProjectionGraphNode {
  key: string;
  objectType: string;
  objectId: string;
  label: string;
  referenceOnly: boolean;
  fact?: ProjectionFactItem;
  x: number;
  y: number;
}

export interface ProjectionGraphEdge {
  key: string;
  source: string;
  target: string;
  predicate: string;
  label: string;
  fact: ProjectionFactItem;
}

export interface ProjectionGraphModel {
  nodes: ProjectionGraphNode[];
  edges: ProjectionGraphEdge[];
}

const objectTypeLabels: Record<string, string> = {
  PATIENT: "患者",
  ENCOUNTER: "就诊",
  CONDITION: "诊断问题",
  OBSERVATION: "观察记录",
  MEDICATION: "用药记录",
  PROCEDURE: "操作记录",
  DIAGNOSTIC_REPORT: "诊断报告",
  DOCUMENT: "临床文档",
  NURSING_ASSESSMENT: "护理评估",
  CARE_PLAN: "照护计划",
  FOLLOW_UP: "随访记录",
  CLAIM: "费用记录",
  KNOWLEDGE_IDENTITY: "知识身份",
  KNOWLEDGE_VERSION: "知识版本",
  SOURCE_DOCUMENT: "来源文档",
  SOURCE_FRAGMENT: "来源片段",
  KNOWLEDGE_SEARCH_DOCUMENT: "知识检索文档",
  RELATION: "关系",
};

const predicateLabels: Record<string, string> = {
  HAS_RESOURCE: "包含资源",
  HAS_ACTIVE_VERSION: "当前版本",
  CITES_FRAGMENT: "引用片段",
  BELONGS_TO_SOURCE: "归属来源",
};

export function projectionObjectLabel(objectType: string) {
  return objectTypeLabels[objectType] ?? objectType;
}

export function projectionPredicateLabel(predicate?: string | null) {
  if (!predicate) return "关联";
  return predicateLabels[predicate] ?? predicate;
}

function splitObjectKey(value: string): { objectType: string; objectId: string } {
  const separator = value.indexOf(":");
  if (separator < 0) {
    return { objectType: "UNKNOWN", objectId: value };
  }
  return {
    objectType: value.slice(0, separator),
    objectId: value.slice(separator + 1),
  };
}

function ensureNode(
  nodes: Map<string, Omit<ProjectionGraphNode, "x" | "y">>,
  key: string,
  fact?: ProjectionFactItem,
) {
  const object = fact
    ? { objectType: fact.objectType, objectId: fact.objectId }
    : splitObjectKey(key);
  const existing = nodes.get(key);
  if (existing && !existing.referenceOnly) return;
  nodes.set(key, {
    key,
    objectType: object.objectType,
    objectId: object.objectId,
    label: projectionObjectLabel(object.objectType),
    referenceOnly: !fact,
    fact,
  });
}

function withGridPositions(
  nodes: Array<Omit<ProjectionGraphNode, "x" | "y">>,
): ProjectionGraphNode[] {
  const count = nodes.length;
  const columns = Math.max(1, Math.ceil(Math.sqrt(count)));
  const rows = Math.max(1, Math.ceil(count / columns));
  return nodes.map((node, index) => {
    const column = index % columns;
    const row = Math.floor(index / columns);
    return {
      ...node,
      x: columns === 1 ? 50 : 10 + (column * 80) / (columns - 1),
      y: rows === 1 ? 50 : 12 + (row * 76) / (rows - 1),
    };
  });
}

export function buildProjectionGraph(facts: ProjectionFactItem[]): ProjectionGraphModel {
  const nodes = new Map<string, Omit<ProjectionGraphNode, "x" | "y">>();
  const edges: ProjectionGraphEdge[] = [];

  for (const fact of facts) {
    if (fact.factKind === "EDGE" && fact.subjectKey && fact.objectKey) {
      ensureNode(nodes, fact.subjectKey);
      ensureNode(nodes, fact.objectKey);
      edges.push({
        key: fact.factKey,
        source: fact.subjectKey,
        target: fact.objectKey,
        predicate: fact.predicate ?? "RELATED_TO",
        label: projectionPredicateLabel(fact.predicate),
        fact,
      });
      continue;
    }
    ensureNode(nodes, `${fact.objectType}:${fact.objectId}`, fact);
  }

  return {
    nodes: withGridPositions(
      [...nodes.values()].sort((left, right) => left.key.localeCompare(right.key)),
    ),
    edges: edges.sort((left, right) => left.key.localeCompare(right.key)),
  };
}
