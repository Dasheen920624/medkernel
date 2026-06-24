import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Button } from "antd";
import { PageState } from "./PageState";
import { PAGE_STATE_KINDS } from "./PageState.contract";

const originalClipboard = navigator.clipboard;

afterEach(() => {
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: originalClipboard,
  });
});

describe("PageState", () => {
  it("locks the exact six page states from the experience contract", () => {
    expect(PAGE_STATE_KINDS).toEqual([
      "loading",
      "empty",
      "error",
      "forbidden",
      "partial",
      "ready",
    ]);
  });

  it("renders loading state", () => {
    render(<PageState state="loading" />);
    expect(screen.getByText("正在加载")).toBeInTheDocument();
  });

  it("renders empty state with action", () => {
    render(<PageState state="empty" title="暂无发布记录" action={<Button>导入离线交付文件</Button>} />);
    expect(screen.getByText("暂无发布记录")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "导入离线交付文件" })).toBeInTheDocument();
  });

  it("renders error tracking number and retry action", () => {
    const retry = vi.fn();
    const writeText = vi.fn();
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: writeText.mockResolvedValue(undefined) },
    });

    render(<PageState state="error" traceId="trace-001" onRetry={retry} />);
    expect(screen.getByText(/trace-001/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /复制追踪号/ }));
    expect(writeText).toHaveBeenCalledWith("trace-001");
    screen.getByRole("button", { name: "重试" }).click();
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("renders forbidden state without sensitive details", () => {
    render(<PageState state="forbidden" />);
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
    expect(screen.getByText(/联系平台管理员/)).toBeInTheDocument();
    expect(screen.queryByText(/信息科主任/)).not.toBeInTheDocument();
  });

  it("renders partial success counts", () => {
    render(
      <PageState
        state="partial"
        successCount={18}
        failureCount={2}
        failureDetails={[
          { key: "pkg-2", reason: "缺少发布审核证据", retryable: true },
          {
            key: "pkg-3",
            reason: "GET /api/v1/package failed: ECONNREFUSED 127.0.0.1:8080",
            retryable: true,
          },
        ]}
      />,
    );
    expect(screen.getByText(/18 项成功/)).toBeInTheDocument();
    expect(screen.getByText(/2 项需处理/)).toBeInTheDocument();
    expect(screen.getByText(/pkg-2/)).toBeInTheDocument();
    expect(screen.getByText(/缺少发布审核证据/)).toBeInTheDocument();
    expect(screen.getByText(/当前项目读取失败，请重试或转人工处理/)).toBeInTheDocument();
    expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/package/)).not.toBeInTheDocument();
  });

  it("renders ready children", () => {
    render(
      <PageState state="ready">
        <div>正常内容</div>
      </PageState>,
    );
    expect(screen.getByText("正常内容")).toBeInTheDocument();
  });
});
