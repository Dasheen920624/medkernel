import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import PathwayGraphEditor from "./PathwayGraphEditor";
import type { PathwayGraphNode } from "./PathwayGraphEditor";

const nodes: PathwayGraphNode[] = [
  {
    nodeCode: "ASSESS",
    name: "入径评估",
    nodeType: "ASSESSMENT",
    terminal: false,
  },
  {
    nodeCode: "FOLLOWUP",
    name: "出径随访",
    nodeType: "FOLLOWUP",
    terminal: true,
  },
];

describe("PathwayGraphEditor", () => {
  it("支持键盘移动节点并写回布局", () => {
    const onNodePositionChange = vi.fn();

    render(
      <PathwayGraphEditor
        nodes={nodes}
        edges={[]}
        editable
        onNodePositionChange={onNodePositionChange}
      />,
    );

    fireEvent.keyDown(screen.getByLabelText("路径节点 ASSESS"), { key: "ArrowRight" });

    expect(onNodePositionChange).toHaveBeenCalledWith(0, "ASSESS", { x: 16, y: 0 });
  });

  it("编辑态可删除节点，详情态不提供删除动作", () => {
    const onDeleteNode = vi.fn();
    const { rerender } = render(
      <PathwayGraphEditor nodes={nodes} edges={[]} editable onDeleteNode={onDeleteNode} />,
    );

    fireEvent.click(screen.getByLabelText("删除路径节点 ASSESS"));
    expect(onDeleteNode).toHaveBeenCalledWith(0, "ASSESS");

    rerender(<PathwayGraphEditor nodes={nodes} edges={[]} />);
    expect(screen.queryByLabelText("删除路径节点 ASSESS")).not.toBeInTheDocument();
  });

  it("节点按退出键时不把事件冒泡到外层弹窗", () => {
    const onOuterKeyDown = vi.fn();

    render(
      <div onKeyDown={onOuterKeyDown}>
        <PathwayGraphEditor nodes={nodes} edges={[]} editable />
      </div>,
    );

    fireEvent.keyDown(screen.getByLabelText("路径节点 ASSESS"), { key: "Escape" });

    expect(onOuterKeyDown).not.toHaveBeenCalled();
  });
});
