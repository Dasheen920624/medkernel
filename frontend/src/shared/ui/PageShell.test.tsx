import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "antd";
import { PageShell } from "./PageShell";
import { PAGE_STATE_KINDS } from "./PageState.contract";

describe("PageShell", () => {
  it("renders one page heading, description, primary action, and extras", () => {
    render(
      <PageShell
        title="配置包与发布"
        description="导入、校验、发布和回滚院内配置"
        primary={<Button type="primary">导入配置包</Button>}
        extras={<Button>保存视图</Button>}
      >
        <div>页面内容</div>
      </PageShell>,
    );

    expect(screen.getByRole("heading", { name: "配置包与发布" })).toBeInTheDocument();
    expect(screen.getByText("导入、校验、发布和回滚院内配置")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "导入配置包" })).toBeInTheDocument();
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
