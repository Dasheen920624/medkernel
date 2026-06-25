import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";

import { ServerDataTable } from "./ServerDataTable";
import type {
  ExperienceColumn,
  ExperiencePageRequest,
  ExperiencePageResponse,
} from "./experienceTypes";

type Row = { id: string; sourceSystem: string; status: string };

const columns: Array<ExperienceColumn<Row>> = [
  { key: "sourceSystem", title: "来源系统", dataIndex: "sourceSystem", always: true },
  { key: "status", title: "状态", dataIndex: "status" },
];
const request: ExperiencePageRequest = {
  pageNumber: 1,
  pageSize: 20,
  sortBy: "updatedAt",
  sortOrder: "desc",
  filters: { status: "DRAFT" },
};
const query: ExperiencePageResponse<Row> = {
  items: [{ id: "row-1", sourceSystem: "HIS", status: "草稿" }],
  pageNumber: 1,
  pageSize: 20,
  totalEstimate: 41,
  hasMore: true,
};

function renderTable(props: Partial<ComponentProps<typeof ServerDataTable<Row>>> = {}) {
  return render(
    <ConfigProvider>
      <ServerDataTable<Row>
        viewKey="terminology.mapping"
        rowKey="id"
        columns={columns}
        query={query}
        request={request}
        loading={false}
        onRequestChange={vi.fn()}
        onOpenDetail={vi.fn()}
        {...props}
      />
    </ConfigProvider>,
  );
}

describe("ServerDataTable", () => {
  it("rejects pages that expose more than eight ordinary columns", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const crowded = Array.from({ length: 8 }, (_, index) => ({
      key: `column-${index}`,
      title: `列 ${index}`,
      dataIndex: "status" as const,
    }));

    expect(() => renderTable({ columns: crowded })).toThrow(/技术字段应进入详情或证据详情/);
    vi.restoreAllMocks();
  });

  it("requests server pagination and opens detail without requerying", async () => {
    const onRequestChange = vi.fn();
    const onOpenDetail = vi.fn();
    renderTable({ onRequestChange, onOpenDetail });

    await userEvent.click(screen.getByTitle("2"));
    expect(onRequestChange).toHaveBeenCalledWith({ ...request, pageNumber: 2, pageSize: 20 });

    onRequestChange.mockClear();
    await userEvent.click(screen.getByRole("button", { name: "查看 row-1" }));
    expect(onOpenDetail).toHaveBeenCalledWith(query.items[0]);
    expect(onRequestChange).not.toHaveBeenCalled();
  });

  it("rejects oversized page size before rendering", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);

    expect(() =>
      renderTable({
        request: { ...request, pageSize: 500 as never },
        query: { ...query, pageSize: 500 },
      }),
    ).toThrow(/每页最多 100 条/);
    vi.restoreAllMocks();
  });

  it("keeps table error state in hospital language without exposing raw technical errors", () => {
    renderTable({
      error: new Error("GET /api/v1/terminology failed: ECONNREFUSED 127.0.0.1:8080"),
    });

    expect(screen.getByText("列表数据读取失败")).toBeInTheDocument();
    expect(screen.getByText(/联系信息科排查数据读取服务/)).toBeInTheDocument();
    expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();
    expect(screen.queryByText(/127\.0\.0\.1/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\/api\/v1\/terminology/)).not.toBeInTheDocument();
  });

  it("reports partial failures, column snapshots, and current-page selection", async () => {
    const onViewSnapshotChange = vi.fn();
    const onSelectionSnapshotChange = vi.fn();
    renderTable({
      partial: {
        successCount: 8,
        failureCount: 2,
        failures: [
          { key: "row-2", reason: "详情读取失败", retryable: true },
          {
            key: "row-3",
            reason: "GET /api/v1/terminology failed: ECONNREFUSED 127.0.0.1:8080",
            retryable: true,
          },
        ],
        onRetryFailures: vi.fn(),
      },
      onViewSnapshotChange,
      onSelectionSnapshotChange,
    });

    expect(screen.getByText(/8 项成功，2 项失败/)).toBeInTheDocument();
    expect(screen.getByText(/详情读取失败/)).toBeInTheDocument();
    expect(screen.getByText(/当前项目读取失败，请重试或转人工处理/)).toBeInTheDocument();
    expect(screen.queryByText(/ECONNREFUSED/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "列管理" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "状态" }));
    expect(onViewSnapshotChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ visibleColumnKeys: ["sourceSystem"] }),
    );

    await userEvent.click(screen.getAllByRole("checkbox")[1]);
    expect(onSelectionSnapshotChange).toHaveBeenCalledWith({
      selectedRowKeys: ["row-1"],
      rowCount: 1,
    });
  });
});
