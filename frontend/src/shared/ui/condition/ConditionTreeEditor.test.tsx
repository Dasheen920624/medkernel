import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import ConditionTreeEditor from "./ConditionTreeEditor";
import {
  createGroup,
  createLeaf,
  countLeaves,
  type RuleGroup,
} from "@/shared/config/conditionModel";

function Harness({ initial }: { initial: RuleGroup }) {
  const [tree, setTree] = useState<RuleGroup>(initial);
  return (
    <>
      <div data-testid="leaf-count">{countLeaves(tree)}</div>
      <div data-testid="root-negate">{String(Boolean(tree.negate))}</div>
      <ConditionTreeEditor value={tree} onChange={setTree} />
    </>
  );
}

describe("ConditionTreeEditor（P1-2 递归条件树组件）", () => {
  it("渲染嵌套结构：A 且 (B 或 C)", () => {
    const initial = createGroup({
      logic: "all",
      children: [
        createLeaf({ fact: "patient.age", operator: "gte" }),
        createGroup({
          logic: "any",
          children: [createLeaf({ fact: "b" }), createLeaf({ fact: "c" })],
        }),
      ],
    });
    render(<Harness initial={initial} />);
    expect(screen.getAllByTestId("condition-group")).toHaveLength(2);
    expect(screen.getAllByTestId("condition-leaf")).toHaveLength(3);
    expect(screen.getByTestId("leaf-count").textContent).toBe("3");
  });

  it("新增条件后叶子数增加", () => {
    const initial = createGroup({ logic: "all", children: [createLeaf({ fact: "x" })] });
    render(<Harness initial={initial} />);
    expect(screen.getByTestId("leaf-count").textContent).toBe("1");
    fireEvent.click(screen.getAllByLabelText("新增条件")[0]);
    expect(screen.getByTestId("leaf-count").textContent).toBe("2");
  });

  it("新增子条件组后出现嵌套组", () => {
    const initial = createGroup({ logic: "all", children: [createLeaf({ fact: "x" })] });
    render(<Harness initial={initial} />);
    expect(screen.getAllByTestId("condition-group")).toHaveLength(1);
    fireEvent.click(screen.getAllByLabelText("新增子条件组")[0]);
    expect(screen.getAllByTestId("condition-group")).toHaveLength(2);
  });

  it("删除条件生效", () => {
    const initial = createGroup({
      logic: "all",
      children: [createLeaf({ fact: "x" }), createLeaf({ fact: "y" })],
    });
    render(<Harness initial={initial} />);
    expect(screen.getByTestId("leaf-count").textContent).toBe("2");
    fireEvent.click(screen.getAllByLabelText("删除条件")[0]);
    expect(screen.getByTestId("leaf-count").textContent).toBe("1");
  });

  it("根组取反开关联动模型", () => {
    const initial = createGroup({ logic: "all", children: [createLeaf({ fact: "x" })] });
    render(<Harness initial={initial} />);
    expect(screen.getByTestId("root-negate").textContent).toBe("false");
    fireEvent.click(screen.getAllByLabelText("取反")[0]);
    expect(screen.getByTestId("root-negate").textContent).toBe("true");
  });

  it("根组不显示删除组按钮", () => {
    const initial = createGroup({ logic: "all", children: [createLeaf({ fact: "x" })] });
    render(<Harness initial={initial} />);
    expect(screen.queryByLabelText("删除条件组")).toBeNull();
  });
});
