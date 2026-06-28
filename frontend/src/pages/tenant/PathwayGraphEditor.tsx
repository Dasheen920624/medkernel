import { useCallback, useEffect, useMemo } from "react";
import { Button, Tag, Tooltip } from "antd";
import { DeleteOutlined } from "@ant-design/icons";
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
} from "@xyflow/react";
import type { Connection, Edge, Node, NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import type { PathwayEdgeType, PathwayNodeType } from "@/shared/api/hooks";
import { resolveNodePosition, type PathwayGraphPosition } from "./pathwayGraphModel";
import styles from "./RulePathwayAuthoring.module.css";

export type PathwayGraphNode = {
  nodeCode: string;
  name: string;
  nodeType: PathwayNodeType;
  terminal: boolean;
  config?: unknown;
};

export type PathwayGraphEdge = {
  edgeCode: string;
  fromNodeCode: string;
  toNodeCode: string;
  edgeType: PathwayEdgeType;
};

type PathwayNodeData = PathwayGraphNode & {
  nodeIndex: number;
  editable: boolean;
  evidenceDetailsEnabled: boolean;
  onDelete?: (nodeIndex: number, nodeCode: string) => void;
  onNudge?: (nodeIndex: number, nodeCode: string, delta: PathwayGraphPosition) => void;
};

type PathwayFlowNode = Node<PathwayNodeData, "pathway">;
type PathwayFlowEdge = Edge<{ edgeCode: string }>;

type PathwayGraphEditorProps = {
  nodes: PathwayGraphNode[];
  edges: PathwayGraphEdge[];
  editable?: boolean;
  onNodePositionChange?: (
    nodeIndex: number,
    nodeCode: string,
    position: PathwayGraphPosition,
  ) => void;
  onConnectNodes?: (sourceNodeCode: string, targetNodeCode: string) => void;
  onDeleteNode?: (nodeIndex: number, nodeCode: string) => void;
  onDeleteEdge?: (edgeCode: string) => void;
  evidenceDetailsEnabled?: boolean;
};

const KEYBOARD_NUDGE = 16;

const GRAPH_NODE_TYPE_TEXT: Partial<Record<PathwayNodeType, string>> = {
  SCREENING: "筛查",
  ASSESSMENT: "评估",
  EXAM: "检查",
  LAB: "检验",
  MEDICATION: "用药",
  SURGERY: "手术",
  NURSING: "护理",
  REHAB: "康复",
  DISCHARGE: "出院",
  FOLLOWUP: "随访",
  QUALITY: "质控",
  DECISION: "决策分支",
  PARALLEL: "并行或汇合",
  WAIT_TIMER: "等待计时",
  MANUAL_GATE: "人工确认节点",
  ORDER_SET: "医嘱套餐",
};

function graphNodeIdentityText(data: PathwayNodeData) {
  if (data.evidenceDetailsEnabled) return data.nodeCode || "未编码";
  return `节点 ${data.nodeIndex + 1}`;
}

function graphNodeTypeText(type: PathwayNodeType) {
  return GRAPH_NODE_TYPE_TEXT[type] ?? type;
}

function graphNodeAriaLabel(data: PathwayNodeData) {
  if (data.evidenceDetailsEnabled) return `路径节点 ${data.nodeCode}`;
  return `路径节点 ${data.nodeIndex + 1}：${data.name || "未命名节点"}`;
}

function PathwayNodeCard({ data, selected }: NodeProps<PathwayFlowNode>) {
  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      event.stopPropagation();
      event.currentTarget.blur();
      return;
    }
    const deltaByKey: Partial<Record<string, PathwayGraphPosition>> = {
      ArrowUp: { x: 0, y: -KEYBOARD_NUDGE },
      ArrowRight: { x: KEYBOARD_NUDGE, y: 0 },
      ArrowDown: { x: 0, y: KEYBOARD_NUDGE },
      ArrowLeft: { x: -KEYBOARD_NUDGE, y: 0 },
    };
    const delta = deltaByKey[event.key];
    if (data.editable && delta) {
      event.preventDefault();
      event.stopPropagation();
      data.onNudge?.(data.nodeIndex, data.nodeCode, delta);
    }
  };

  return (
    <div
      className={`${styles.graphNode} ${selected ? styles.graphNodeSelected : ""}`}
      tabIndex={0}
      aria-label={graphNodeAriaLabel(data)}
      onKeyDown={handleKeyDown}
    >
      <Handle type="target" position={Position.Left} isConnectable={data.editable} />
      <div className={styles.graphNodeHeader}>
        <Tag color="blue">{graphNodeIdentityText(data)}</Tag>
        <div className={styles.graphNodeActions}>
          {data.terminal && <Tag color="green">终止</Tag>}
          {data.editable && (
            <Tooltip title="删除节点">
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                aria-label={`删除路径节点 ${data.nodeCode}`}
                onPointerDown={(event) => event.stopPropagation()}
                onClick={() => data.onDelete?.(data.nodeIndex, data.nodeCode)}
              />
            </Tooltip>
          )}
        </div>
      </div>
      <div className={styles.graphNodeName}>{data.name || "未命名节点"}</div>
      <div className={styles.graphNodeType}>{graphNodeTypeText(data.nodeType)}</div>
      <Handle type="source" position={Position.Right} isConnectable={data.editable} />
    </div>
  );
}

const nodeTypes = {
  pathway: PathwayNodeCard,
};

function PathwayGraphEditorCanvas({
  nodes,
  edges,
  editable = false,
  onNodePositionChange,
  onConnectNodes,
  onDeleteNode,
  onDeleteEdge,
  evidenceDetailsEnabled = true,
}: PathwayGraphEditorProps) {
  const handleDeleteNode = useCallback(
    (nodeIndex: number, nodeCode: string) => {
      onDeleteNode?.(nodeIndex, nodeCode);
    },
    [onDeleteNode],
  );

  const initialNodes = useMemo<PathwayFlowNode[]>(
    () =>
      nodes.map((node, index) => ({
        id: `pathway-node-${index}`,
        type: "pathway",
        position: resolveNodePosition(node.config, index),
        data: {
          ...node,
          nodeIndex: index,
          editable,
          evidenceDetailsEnabled,
        },
      })),
    [editable, evidenceDetailsEnabled, nodes],
  );

  const initialEdges = useMemo<PathwayFlowEdge[]>(() => {
    const nodeIdByCode = new Map<string, string>();
    const nodeNameByCode = new Map<string, string>();
    initialNodes.forEach((node) => {
      if (!nodeIdByCode.has(node.data.nodeCode)) {
        nodeIdByCode.set(node.data.nodeCode, node.id);
      }
      if (!nodeNameByCode.has(node.data.nodeCode)) {
        nodeNameByCode.set(node.data.nodeCode, node.data.name || `节点 ${node.data.nodeIndex + 1}`);
      }
    });
    return edges.flatMap((edge, index) => {
      const source = nodeIdByCode.get(edge.fromNodeCode);
      const target = nodeIdByCode.get(edge.toNodeCode);
      if (!source || !target) return [];
      const sourceLabel = nodeNameByCode.get(edge.fromNodeCode) ?? "源节点";
      const targetLabel = nodeNameByCode.get(edge.toNodeCode) ?? "目标节点";
      return [
        {
          id: `${edge.edgeCode || "EDGE"}::${index}`,
          source,
          target,
          label: evidenceDetailsEnabled ? edge.edgeCode : `流转 ${index + 1}`,
          ariaLabel: evidenceDetailsEnabled
            ? `流转边 ${edge.edgeCode}：${edge.fromNodeCode} 到 ${edge.toNodeCode}`
            : `路径流转 ${index + 1}：${sourceLabel} 到 ${targetLabel}`,
          markerEnd: { type: MarkerType.ArrowClosed },
          className: styles.graphEdge,
          data: { edgeCode: edge.edgeCode },
        },
      ];
    });
  }, [edges, evidenceDetailsEnabled, initialNodes]);

  const nodeCodeByFlowId = useMemo(
    () => new Map(initialNodes.map((node) => [node.id, node.data.nodeCode])),
    [initialNodes],
  );

  const [flowNodes, setFlowNodes, onNodesChange] = useNodesState(initialNodes);
  const [flowEdges, setFlowEdges, onEdgesChange] = useEdgesState<PathwayFlowEdge>(initialEdges);

  const handleNudge = useCallback(
    (nodeIndex: number, nodeCode: string, delta: PathwayGraphPosition) => {
      setFlowNodes((current) =>
        current.map((node) => {
          if (node.data.nodeIndex !== nodeIndex) return node;
          const position = {
            x: node.position.x + delta.x,
            y: node.position.y + delta.y,
          };
          onNodePositionChange?.(nodeIndex, nodeCode, position);
          return { ...node, position };
        }),
      );
    },
    [onNodePositionChange, setFlowNodes],
  );

  useEffect(() => {
    setFlowNodes(
      initialNodes.map((node) => ({
        ...node,
        data: {
          ...node.data,
          onDelete: handleDeleteNode,
          onNudge: handleNudge,
        },
      })),
    );
  }, [handleDeleteNode, handleNudge, initialNodes, setFlowNodes]);

  useEffect(() => {
    setFlowEdges(initialEdges);
  }, [initialEdges, setFlowEdges]);

  const handleConnect = useCallback(
    (connection: Connection) => {
      const sourceNodeCode = connection.source
        ? nodeCodeByFlowId.get(connection.source)
        : undefined;
      const targetNodeCode = connection.target
        ? nodeCodeByFlowId.get(connection.target)
        : undefined;
      if (sourceNodeCode && targetNodeCode && sourceNodeCode !== targetNodeCode) {
        onConnectNodes?.(sourceNodeCode, targetNodeCode);
      }
    },
    [nodeCodeByFlowId, onConnectNodes],
  );

  const handleNodesDelete = useCallback(
    (deletedNodes: PathwayFlowNode[]) => {
      [...deletedNodes]
        .sort((left, right) => right.data.nodeIndex - left.data.nodeIndex)
        .forEach((node) => onDeleteNode?.(node.data.nodeIndex, node.data.nodeCode));
    },
    [onDeleteNode],
  );

  const handleEdgesDelete = useCallback(
    (deletedEdges: PathwayFlowEdge[]) => {
      deletedEdges.forEach((edge) => onDeleteEdge?.(edge.data?.edgeCode ?? edge.id));
    },
    [onDeleteEdge],
  );

  return (
    <div className={styles.graphCanvas} aria-label="路径图编辑器">
      <ReactFlow
        nodes={flowNodes}
        edges={flowEdges}
        nodeTypes={nodeTypes}
        onNodesChange={editable ? onNodesChange : undefined}
        onEdgesChange={editable ? onEdgesChange : undefined}
        onNodeDragStop={(_, node) =>
          onNodePositionChange?.(node.data.nodeIndex, node.data.nodeCode, node.position)
        }
        onConnect={editable ? handleConnect : undefined}
        onNodesDelete={editable ? handleNodesDelete : undefined}
        onEdgesDelete={editable ? handleEdgesDelete : undefined}
        nodesDraggable={editable}
        nodesConnectable={editable}
        elementsSelectable
        deleteKeyCode={editable ? ["Backspace", "Delete"] : null}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.35}
        maxZoom={1.8}
        proOptions={{ hideAttribution: true }}
        ariaLabelConfig={{
          "node.a11yDescription.default":
            "按回车或空格选择节点，按方向键移动，按删除键移除，按退出键取消。",
          "node.a11yDescription.keyboardDisabled":
            "按回车或空格选择节点，按删除键移除，按退出键取消。",
          "node.a11yDescription.ariaLiveMessage": ({ direction, x, y }) =>
            `节点已向${direction}移动，新位置为横坐标 ${x}、纵坐标 ${y}。`,
          "edge.a11yDescription.default": "按回车或空格选择流转边，按删除键移除，按退出键取消。",
          "controls.ariaLabel": "路径画布控制",
          "controls.zoomIn.ariaLabel": "放大画布",
          "controls.zoomOut.ariaLabel": "缩小画布",
          "controls.fitView.ariaLabel": "适应画布",
          "controls.interactive.ariaLabel": "切换画布交互",
          "minimap.ariaLabel": "路径缩略图",
          "handle.ariaLabel": "节点连接点",
        }}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
        <Controls showInteractive={editable} />
        <MiniMap pannable zoomable className={styles.graphMiniMap} />
      </ReactFlow>
    </div>
  );
}

export default function PathwayGraphEditor(props: PathwayGraphEditorProps) {
  return (
    <ReactFlowProvider>
      <PathwayGraphEditorCanvas {...props} />
    </ReactFlowProvider>
  );
}
