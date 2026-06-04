import { useMemo, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message,
} from "antd";
import type { BadgeProps, TableProps } from "antd";
import { CheckCircleOutlined, LinkOutlined, ReloadOutlined } from "@ant-design/icons";

import { useCompleteWorkflowTodo, useWorkflowTodos } from "@/shared/api/hooks";
import type {
  WorkflowPriority,
  WorkflowTodo,
  WorkflowTodoSourceType,
  WorkflowTodoStatus,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import { PageShell } from "@/shared/ui/PageShell";

const { TextArea } = Input;

const statusText: Record<WorkflowTodoStatus, string> = {
  PENDING: "待处理",
  IN_PROGRESS: "处理中",
  COMPLETED: "已完成",
  TRANSFERRED: "已转交",
  CANCELLED: "已取消",
};

const statusBadge: Record<WorkflowTodoStatus, BadgeProps["status"]> = {
  PENDING: "processing",
  IN_PROGRESS: "warning",
  COMPLETED: "success",
  TRANSFERRED: "default",
  CANCELLED: "error",
};

const priorityColor: Record<WorkflowPriority, string> = {
  CRITICAL: "red",
  HIGH: "volcano",
  MEDIUM: "gold",
  LOW: "blue",
};

const priorityRank: Record<WorkflowPriority, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

const sourceRank: Record<WorkflowTodoSourceType, number> = {
  SAFETY_REVIEW: 0,
  RECOMMENDATION_CARD: 1,
  FOLLOWUP_TASK: 2,
  NURSING_TASK: 3,
  REPORT_INTERPRETATION: 4,
  BEDSIDE_KNOWLEDGE: 5,
};

function formatDateTime(value?: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function compareDateTime(left?: string | null, right?: string | null) {
  if (!left && !right) return 0;
  if (!left) return 1;
  if (!right) return -1;
  return new Date(left).getTime() - new Date(right).getTime();
}

export default function WorkflowTodos() {
  const [status, setStatus] = useState<WorkflowTodoStatus | undefined>("PENDING");
  const [priority, setPriority] = useState<WorkflowPriority | undefined>();
  const [sourceType, setSourceType] = useState<WorkflowTodoSourceType | undefined>();
  const [completingTodo, setCompletingTodo] = useState<WorkflowTodo | null>(null);
  const [completeForm] = Form.useForm<{ completionReason: string }>();

  const queryParams = {
    status,
    priority,
    sourceType,
    page: 1,
    size: 10,
  };
  const { data, isError, isLoading, refetch } = useWorkflowTodos(queryParams);
  const completeMutation = useCompleteWorkflowTodo();
  const visibleTodos = useMemo(
    () =>
      [...(data?.items ?? [])].sort((left, right) => {
        const bySource = sourceRank[left.sourceType] - sourceRank[right.sourceType];
        if (bySource !== 0) return bySource;
        const byPriority = priorityRank[left.priority] - priorityRank[right.priority];
        if (byPriority !== 0) return byPriority;
        return compareDateTime(left.dueAt, right.dueAt);
      }),
    [data?.items],
  );

  const handleComplete = async () => {
    if (!completingTodo) return;
    const values = await completeForm.validateFields();
    try {
      await completeMutation.mutateAsync({
        todoId: completingTodo.todoId,
        request: { completionReason: values.completionReason.trim() },
      });
      message.success("待办已完成");
      setCompletingTodo(null);
      completeForm.resetFields();
      await refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "待办完成失败"));
    }
  };

  const columns: TableProps<WorkflowTodo>["columns"] = [
    {
      title: "待办",
      dataIndex: "title",
      key: "title",
      render: (_value, record) => (
        <Space direction="vertical" size={2}>
          <span className="font-semibold text-slate-800">{record.title}</span>
          <span className="text-xs text-slate-500">{record.summary}</span>
        </Space>
      ),
    },
    {
      title: "来源",
      dataIndex: "sourceType",
      key: "sourceType",
      render: (value: WorkflowTodoSourceType) => <Tag>{value}</Tag>,
    },
    {
      title: "患者",
      dataIndex: "patientId",
      key: "patientId",
      render: (value?: string | null) => value || "-",
    },
    {
      title: "责任人",
      dataIndex: "assigneeId",
      key: "assigneeId",
      render: (value?: string | null) => value || "-",
    },
    {
      title: "截止",
      dataIndex: "dueAt",
      key: "dueAt",
      render: (value?: string | null) => formatDateTime(value),
    },
    {
      title: "优先级",
      dataIndex: "priority",
      key: "priority",
      render: (value: WorkflowPriority) => <Tag color={priorityColor[value]}>{value}</Tag>,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (value: WorkflowTodoStatus) => (
        <Badge status={statusBadge[value]} text={statusText[value] ?? value} />
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_value, record) => (
        <Space size={4}>
          {record.deepLink && (
            <Button
              type="link"
              aria-label="打开来源"
              icon={<LinkOutlined />}
              href={record.deepLink}
              className="px-0 font-semibold"
            >
              打开来源
            </Button>
          )}
          <Button
            type="link"
            aria-label="完成"
            icon={<CheckCircleOutlined />}
            disabled={record.status !== "PENDING" && record.status !== "IN_PROGRESS"}
            onClick={() => {
              setCompletingTodo(record);
              completeForm.setFieldsValue({ completionReason: "" });
            }}
            className="px-0 font-semibold"
          >
            完成
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <PageShell
      title="工作流协同待办中心"
      description="统一查看并办理真实临床协同待办。"
      extras={
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
          刷新
        </Button>
      }
    >
      <Card className="mb-4">
        <Space wrap>
          <Select
            aria-label="待办状态"
            value={status}
            onChange={setStatus}
            allowClear
            className="w-36"
            options={[
              { value: "PENDING", label: "待处理" },
              { value: "IN_PROGRESS", label: "处理中" },
              { value: "COMPLETED", label: "已完成" },
            ]}
          />
          <Select
            aria-label="优先级"
            value={priority}
            onChange={setPriority}
            allowClear
            placeholder="优先级"
            className="w-36"
            options={[
              { value: "CRITICAL", label: "危急" },
              { value: "HIGH", label: "高" },
              { value: "MEDIUM", label: "中" },
              { value: "LOW", label: "低" },
            ]}
          />
          <Select
            aria-label="待办来源"
            value={sourceType}
            onChange={setSourceType}
            allowClear
            placeholder="来源"
            className="w-44"
            options={[
              { value: "FOLLOWUP_TASK", label: "随访任务" },
              { value: "SAFETY_REVIEW", label: "安全复核" },
              { value: "RECOMMENDATION_CARD", label: "临床提醒" },
            ]}
          />
        </Space>
      </Card>

      {isError && (
        <Alert
          type="error"
          showIcon
          className="mb-4"
          message="协同待办读取失败"
          description="请检查登录状态、租户上下文或后端工作流接口。"
        />
      )}

      <Card>
        <Table
          rowKey="todoId"
          columns={columns}
          dataSource={visibleTodos}
          loading={isLoading}
          pagination={{
            pageSize: 10,
            total: data?.total ?? 0,
            showTotal: (total) => `共 ${total} 个待办`,
          }}
          locale={{ emptyText: "当前暂无协同待办" }}
        />
      </Card>

      <Modal
        title="完成待办"
        open={!!completingTodo}
        onOk={handleComplete}
        onCancel={() => setCompletingTodo(null)}
        okText="确认完成"
        cancelText="取消"
        confirmLoading={completeMutation.isPending}
        destroyOnClose
      >
        <Form form={completeForm} layout="vertical" className="mt-4">
          <Form.Item
            name="completionReason"
            label="完成说明"
            rules={[{ required: true, message: "请输入完成说明" }]}
          >
            <TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
}
