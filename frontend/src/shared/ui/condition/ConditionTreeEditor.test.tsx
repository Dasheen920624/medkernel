import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import ConditionTreeEditor from "./ConditionTreeEditor";
import {
  createGroup,
  createLeaf,
  countLeaves,
  type RuleGroup,
} from "@/shared/config/conditionModel";
import type { ContextFieldDescriptor } from "@/shared/api/hooks";

vi.mock("@/shared/api/hooks", () => ({
  useStandardTerms: () => ({ data: { items: [], total: 0 }, isLoading: false }),
  useMappingCoverage: () => ({ data: [], isLoading: false }),
}));

const fieldCatalog: ContextFieldDescriptor[] = [
  {
    category: "检验检查",
    group: "检验/体征结果",
    resourceType: "Observation",
    fieldPath: "observations[].code",
    displayName: "检验编码",
    dataType: "code",
    unit: null,
    codeSystem: "LOINC",
    description: "标准检验项目编码",
    source: "PLATFORM",
    fieldId: null,
    derived: false,
  },
];

function Harness({ initial, fields }: { initial: RuleGroup; fields?: ContextFieldDescriptor[] }) {
  const [tree, setTree] = useState<RuleGroup>(initial);
  return (
    <>
      <div data-testid="leaf-count">{countLeaves(tree)}</div>
      <div data-testid="root-negate">{String(Boolean(tree.negate))}</div>
      <div data-testid="tree-json">{JSON.stringify(tree)}</div>
      <ConditionTreeEditor value={tree} onChange={setTree} fieldCatalog={fields} />
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

  it("字段路径从目录选择后自动带出值类型与字典比较值选择器", async () => {
    const initial = createGroup({ logic: "all", children: [createLeaf()] });
    render(<Harness initial={initial} fields={fieldCatalog} />);

    fireEvent.change(screen.getByRole("combobox", { name: "上下文字段路径" }), {
      target: { value: "检验编码" },
    });
    fireEvent.click(await screen.findByText(/检验编码（observations\[\]\.code）/));

    expect(screen.getByTestId("tree-json").textContent).toContain('"fact":"observations[].code"');
    expect(screen.getByTestId("tree-json").textContent).toContain('"valueKind":"string"');
    expect(screen.getByRole("combobox", { name: "比较值" })).toBeInTheDocument();
  });
});
