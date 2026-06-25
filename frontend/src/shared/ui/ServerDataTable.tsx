import { SettingOutlined } from "@ant-design/icons";
import { Alert, Button, Checkbox, Popover, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { Key } from "react";
import { useState } from "react";

import { customerSafeDisplayText } from "@/shared/config/customerLabels";

import { PageState } from "./PageState";
import type {
  ExperienceColumn,
  ExperienceFilterValue,
  ExperiencePageRequest,
  ExperiencePageResponse,
  ExperiencePageSize,
  ExperiencePartialResult,
  ExperienceViewSnapshot,
} from "./experienceTypes";
import { EXPERIENCE_PAGE_SIZE_OPTIONS, MAX_EXPERIENCE_PAGE_SIZE } from "./experienceTypes";

const { Text } = Typography;

const TABLE_ERROR_TITLE = "列表数据读取失败";
const TABLE_ERROR_DESCRIPTION = "请重试；若持续失败，请联系信息科排查数据读取服务。";
const PARTIAL_FAILURE_FALLBACK = "当前项目读取失败，请重试或转人工处理。";

interface ServerDataTableProps<T extends object> {
  viewKey: string;
  rowKey: keyof T | ((record: T) => Key);
  columns: Array<ExperienceColumn<T>>;
  query: ExperiencePageResponse<T>;
  request: ExperiencePageRequest;
  loading: boolean;
  error?: Error;
  partial?: ExperiencePartialResult;
  evidenceDetailsEnabled?: boolean;
  initialVisibleColumnKeys?: readonly string[];
  onRequestChange: (request: ExperiencePageRequest) => void;
  onOpenDetail: (record: T) => void;
  onViewSnapshotChange?: (snapshot: ExperienceViewSnapshot) => void;
  onSelectionSnapshotChange?: (snapshot: { selectedRowKeys: Key[]; rowCount: number }) => void;
}

function snapshotFilters(filters: Record<string, unknown>): ExperienceFilterValue[] {
  const snapshot: ExperienceFilterValue[] = [];
  Object.entries(filters).forEach(([key, value]) => {
    if (typeof value === "string") {
      snapshot.push({ key, value });
    }
    if (
      Array.isArray(value) &&
      value.length === 2 &&
      value.every((item) => typeof item === "string")
    ) {
      snapshot.push({ key, value: [value[0], value[1]] as [string, string] });
    }
  });
  return snapshot;
}

function customerSafeFailureReason(reason: string): string {
  return customerSafeDisplayText(reason, PARTIAL_FAILURE_FALLBACK);
}

export function ServerDataTable<T extends object>({
  viewKey,
  rowKey,
  columns,
  query,
  request,
  loading,
  error,
  partial,
  evidenceDetailsEnabled = false,
  initialVisibleColumnKeys,
  onRequestChange,
  onOpenDetail,
  onViewSnapshotChange,
  onSelectionSnapshotChange,
}: ServerDataTableProps<T>) {
  if (request.pageSize > MAX_EXPERIENCE_PAGE_SIZE || query.pageSize > MAX_EXPERIENCE_PAGE_SIZE) {
    throw new Error(`服务端分页每页最多 ${MAX_EXPERIENCE_PAGE_SIZE} 条`);
  }

  const ordinaryColumns = columns.filter((column) => !column.advancedOnly);
  if (ordinaryColumns.length + 1 > 8) {
    throw new Error("普通视图表格最多 8 列，技术字段应进入详情或证据详情");
  }

  const defaultVisibleColumnKeys = ordinaryColumns.map((column) => column.key);
  const [visibleColumnKeys, setVisibleColumnKeys] = useState<string[]>(
    initialVisibleColumnKeys ? [...initialVisibleColumnKeys] : defaultVisibleColumnKeys,
  );
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const selectableColumns = columns.filter(
    (column) => evidenceDetailsEnabled || !column.advancedOnly,
  );
  const effectiveVisibleKeys = visibleColumnKeys.filter((key) =>
    selectableColumns.some((column) => column.key === key),
  );
  const alwaysVisibleKeys = selectableColumns
    .filter((column) => column.always)
    .map((column) => column.key);

  function identifyRow(record: T): Key {
    return typeof rowKey === "function" ? rowKey(record) : (record[rowKey] as Key);
  }

  function updateVisibleColumns(nextValues: Array<string | number | boolean>) {
    const selectedKeys = nextValues.map(String);
    const nextKeys = Array.from(new Set([...selectedKeys, ...alwaysVisibleKeys]));
    setVisibleColumnKeys(nextKeys);
    onViewSnapshotChange?.({
      viewKey,
      filters: snapshotFilters(request.filters),
      pageRequest: request,
      visibleColumnKeys: nextKeys,
      evidenceDetailsEnabled,
      capturedAt: new Date().toISOString(),
    });
  }

  if (loading) {
    return <PageState state="loading" />;
  }

  if (error) {
    return (
      <PageState state="error" title={TABLE_ERROR_TITLE} description={TABLE_ERROR_DESCRIPTION} />
    );
  }

  const tableColumns: ColumnsType<T> = columns
    .filter((column) => effectiveVisibleKeys.includes(column.key))
    .map((column) => ({
      key: column.key,
      title: column.title,
      dataIndex: column.dataIndex as string | undefined,
      width: column.width,
      render: column.render
        ? (value: unknown, record: T) => column.render?.(value, record)
        : undefined,
    }));

  tableColumns.push({
    key: "actions",
    title: "操作",
    render: (_, record) => (
      <Button
        type="link"
        size="small"
        aria-label={`查看 ${String(identifyRow(record))}`}
        onClick={() => onOpenDetail(record)}
      >
        查看
      </Button>
    ),
  });

  const columnControls = (
    <Checkbox.Group value={effectiveVisibleKeys} onChange={updateVisibleColumns}>
      <Space direction="vertical">
        {selectableColumns.map((column) => (
          <Checkbox key={column.key} value={column.key} disabled={column.always}>
            {column.title}
          </Checkbox>
        ))}
      </Space>
    </Checkbox.Group>
  );

  return (
    <Space direction="vertical" size="middle" className="mk-full-width">
      {partial && (
        <Alert
          type="warning"
          showIcon
          message={`${partial.successCount} 项成功，${partial.failureCount} 项失败`}
          description={
            <Space direction="vertical" size={0}>
              {partial.failures.map((failure) => (
                <Text key={failure.key}>
                  {`${failure.key}：${customerSafeFailureReason(failure.reason)}`}
                </Text>
              ))}
              {partial.onRetryFailures && (
                <Button size="small" onClick={partial.onRetryFailures}>
                  重试失败项
                </Button>
              )}
            </Space>
          }
        />
      )}
      <Space className="mk-push-inline-start-auto">
        <Popover content={columnControls} trigger="click" placement="bottomRight">
          <Button icon={<SettingOutlined />} aria-label="列管理">
            列管理
          </Button>
        </Popover>
      </Space>
      <Table<T>
        rowKey={identifyRow}
        dataSource={query.items}
        columns={tableColumns}
        loading={false}
        scroll={{ x: "max-content" }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => {
            setSelectedRowKeys(keys);
            onSelectionSnapshotChange?.({ selectedRowKeys: keys, rowCount: keys.length });
          },
        }}
        pagination={{
          current: query.pageNumber ?? request.pageNumber ?? 1,
          pageSize: query.pageSize,
          total: query.totalEstimate,
          showSizeChanger: true,
          pageSizeOptions: [...EXPERIENCE_PAGE_SIZE_OPTIONS],
          onChange: (pageNumber, pageSize) =>
            onRequestChange({
              ...request,
              pageNumber,
              pageSize: pageSize as ExperiencePageSize,
            }),
        }}
      />
    </Space>
  );
}
