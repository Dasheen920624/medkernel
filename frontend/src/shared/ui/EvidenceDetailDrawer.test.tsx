import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";

import { EvidenceDetailDrawer } from "./EvidenceDetailDrawer";

const sections = [
  {
    key: "summary",
    title: "摘要",
    items: [
      { label: "映射状态", value: "草稿" },
      { label: "内部字段", value: "mapping-1", advancedOnly: true },
    ],
  },
];

function renderDrawer(props: Partial<ComponentProps<typeof EvidenceDetailDrawer>> = {}) {
  return render(
    <ConfigProvider>
      <EvidenceDetailDrawer
        open
        title="映射详情"
        evidenceDetailsEnabled={false}
        sections={sections}
        traceId="trace-123"
        onClose={vi.fn()}
        {...props}
      />
    </ConfigProvider>,
  );
}

describe("EvidenceDetailDrawer", () => {
  it("keeps advanced evidence hidden until evidence details are enabled", () => {
    const { rerender } = renderDrawer();

    expect(screen.getByText("草稿")).toBeInTheDocument();
    expect(screen.queryByText("mapping-1")).not.toBeInTheDocument();
    expect(screen.queryByText(/trace-123/)).not.toBeInTheDocument();

    rerender(
      <ConfigProvider>
        <EvidenceDetailDrawer
          open
          title="映射详情"
          evidenceDetailsEnabled
          sections={sections}
          traceId="trace-123"
          onClose={vi.fn()}
        />
      </ConfigProvider>,
    );

    expect(screen.getByText("mapping-1")).toBeInTheDocument();
    expect(screen.getByText(/trace-123/)).toBeInTheDocument();
  });

  it("renders detail loading feedback", () => {
    renderDrawer({ loading: true });
    expect(screen.getByText("正在加载详情")).toBeInTheDocument();
  });

  it("keeps detail errors local and allows retry", async () => {
    const onRetry = vi.fn();
    renderDrawer({ error: new Error("请求失败"), onRetry });

    expect(screen.getByText("详情暂时不可用")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
