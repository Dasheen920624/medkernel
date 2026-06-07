import { useMemo, useState } from "react";
import { Button, Empty, Space, Tooltip } from "antd";
import { CompressOutlined, MinusOutlined, PlusOutlined } from "@ant-design/icons";

import type { ProjectionFactItem } from "@/shared/api/hooks";
import { buildProjectionGraph } from "./projectionGraph";

import styles from "./GraphExplore.module.css";

interface ProjectionGraphCanvasProps {
  facts: ProjectionFactItem[];
  selectedKey?: string | null;
  onSelect: (nodeKey: string, fact?: ProjectionFactItem) => void;
}

const ZOOM_CLASSES = [
  styles.graphCanvasZoom100,
  styles.graphCanvasZoom120,
  styles.graphCanvasZoom140,
  styles.graphCanvasZoom160,
  styles.graphCanvasZoom180,
];

function shortNodeId(value: string) {
  if (value.length <= 18) return value;
  return `${value.slice(0, 8)}...${value.slice(-6)}`;
}

export function ProjectionGraphCanvas({
  facts,
  selectedKey,
  onSelect,
}: ProjectionGraphCanvasProps) {
  const graph = useMemo(() => buildProjectionGraph(facts), [facts]);
  const [zoomIndex, setZoomIndex] = useState(0);
  const nodeByKey = useMemo(
    () => new Map(graph.nodes.map((node) => [node.key, node])),
    [graph.nodes],
  );

  if (graph.nodes.length === 0) {
    return <Empty description="当前查询范围没有可展示的投影关系" />;
  }

  return (
    <div className={styles.graphRegion}>
      <Space className={styles.graphControls} size="small">
        <Tooltip title="缩小">
          <Button
            aria-label="缩小图谱"
            icon={<MinusOutlined />}
            disabled={zoomIndex === 0}
            onClick={() => setZoomIndex((value) => Math.max(0, value - 1))}
          />
        </Tooltip>
        <Tooltip title="适应画布">
          <Button
            aria-label="适应画布"
            icon={<CompressOutlined />}
            onClick={() => setZoomIndex(0)}
          />
        </Tooltip>
        <Tooltip title="放大">
          <Button
            aria-label="放大图谱"
            icon={<PlusOutlined />}
            disabled={zoomIndex === ZOOM_CLASSES.length - 1}
            onClick={() => setZoomIndex((value) => Math.min(ZOOM_CLASSES.length - 1, value + 1))}
          />
        </Tooltip>
      </Space>

      <div className={styles.graphViewport}>
        <div className={`${styles.graphCanvas} ${ZOOM_CLASSES[zoomIndex]}`}>
          <svg
            role="group"
            aria-label="投影关系图"
            className={styles.graphEdges}
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
          >
            <defs>
              <marker
                id="projection-arrow"
                markerWidth="6"
                markerHeight="6"
                refX="5"
                refY="3"
                orient="auto"
              >
                <path d="M0,0 L0,6 L6,3 z" className={styles.graphArrow} />
              </marker>
            </defs>
            {graph.edges.map((edge) => {
              const source = nodeByKey.get(edge.source);
              const target = nodeByKey.get(edge.target);
              if (!source || !target) return null;
              return (
                <g key={edge.key}>
                  <line
                    x1={source.x}
                    y1={source.y}
                    x2={target.x}
                    y2={target.y}
                    markerEnd="url(#projection-arrow)"
                  />
                  <text x={(source.x + target.x) / 2} y={(source.y + target.y) / 2}>
                    {edge.label}
                  </text>
                </g>
              );
            })}

            {graph.nodes.map((node) => (
              <g
                key={node.key}
                role="button"
                tabIndex={0}
                aria-label={`${node.label} ${node.objectId}`}
                className={
                  selectedKey === node.key
                    ? `${styles.graphNode} ${styles.graphNodeSelected}`
                    : styles.graphNode
                }
                transform={`translate(${node.x} ${node.y})`}
                onClick={() => onSelect(node.key, node.fact)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onSelect(node.key, node.fact);
                  }
                }}
              >
                <title>{`${node.label} ${node.objectId}`}</title>
                <rect x="-8.5" y="-4.6" width="17" height="9.2" rx="1.4" />
                <text className={styles.graphNodeType} x="0" y="-0.7">
                  {node.label}
                </text>
                <text className={styles.graphNodeId} x="0" y="2.3">
                  {shortNodeId(node.objectId)}
                </text>
                {node.referenceOnly && (
                  <text className={styles.graphNodeHint} x="0" y="4">
                    关系引用
                  </text>
                )}
              </g>
            ))}
          </svg>
        </div>
      </div>
    </div>
  );
}
