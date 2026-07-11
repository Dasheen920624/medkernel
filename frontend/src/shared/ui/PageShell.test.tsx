import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "antd";
import { PageShell } from "./PageShell";
import { PAGE_STATE_KINDS } from "./PageState.contract";

describe("PageShell", () => {
  it("renders one page heading, description, primary action, and extras", () => {
    render(
      <PageShell
        title="机构生效版本"
        description="发布平台标准版本并管理机构生效版本"
        primary={<Button type="primary">发布平台标准版本</Button>}
        extras={<Button>保存视图</Button>}
      >
        <div>页面内容</div>
      </PageShell>,
    );

    expect(screen.getByRole("heading", { name: "机构生效版本" })).toBeInTheDocument();
    expect(screen.getByText("发布平台标准版本并管理机构生效版本")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "发布平台标准版本" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "保存视图" })).toBeInTheDocument();
    expect(screen.getByText("页面内容")).toBeInTheDocument();
  });

  it("can render every required six-state surface without each page re-implementing it", () => {
    PAGE_STATE_KINDS.filter((state) => state !== "ready").forEach((state) => {
      const { unmount } = render(
        <PageShell
          title={`状态页-${state}`}
          state={state}
          stateProps={{
            title: `自定义${state}`,
            successCount: state === "partial" ? 3 : undefined,
            failureCount: state === "partial" ? 1 : undefined,
          }}
        >
          <div>正常内容不应提前渲染</div>
        </PageShell>,
      );

      expect(screen.getByRole("heading", { name: `状态页-${state}` })).toBeInTheDocument();
      expect(screen.getByText(`自定义${state}`)).toBeInTheDocument();
      expect(screen.queryByText("正常内容不应提前渲染")).toBeNull();
      unmount();
    });

    render(
      <PageShell title="正常页" state="ready">
        <div>正常内容</div>
      </PageShell>,
    );
    expect(screen.getByText("正常内容")).toBeInTheDocument();
  });
});
