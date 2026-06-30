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
import {
  CheckCircleOutlined,
  LinkOutlined,
  ReloadOutlined,
  SwapRightOutlined,
} from "@ant-design/icons";

import {
  useCompleteWorkflowTodo,
  useOrgUnits,
  useOrgUsers,
  useSecurityProfile,
  useTransferWorkflowTodo,
  useWorkflowTodos,
} from "@/shared/api/hooks";
import type {
  WorkflowPriority,
  WorkflowTodo,
  WorkflowTodoSourceType,
  WorkflowTodoStatus,
} from "@/shared/api/hooks";
import { getApiErrorMessage } from "@/shared/api/errors";
import {
  SOURCE_LINK_MISSING_TEXT,
  SOURCE_LINK_UNAVAILABLE_TEXT,
  SOURCE_TRACE_MISSING_TEXT,
  resolveSourceDeepLink,
} from "@/shared/lib/sourceLink";
import { findRouteByPath } from "@/shared/config/routes";
import { customerEnumLabel } from "@/shared/config/customerLabels";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";

import styles from "./Clinical.module.css";

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

const priorityText: Record<WorkflowPriority, string> = {
  CRITICAL: "危急",
  HIGH: "高优先",
  MEDIUM: "中优先",
  LOW: "低优先",
};

const priorityRank: Record<WorkflowPriority, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

const sourceRank: Record<WorkflowTodoSourceType, number> = {
  SAFETY_REVIEW: 0,
  REPORT_INTERPRETATION: 1,
  RECOMMENDATION_CARD: 2,
  PATHWAY_NODE: 3,
  NURSING_TASK: 4,
  FOLLOWUP_TASK: 5,
  BEDSIDE_KNOWLEDGE: 6,
};

const sourceText: Record<WorkflowTodoSourceType, string> = {
  FOLLOWUP_TASK: "随访任务",
  SAFETY_REVIEW: "安全复核",
  RECOMMENDATION_CARD: "临床提醒",
  NURSING_TASK: "护理任务",
  REPORT_INTERPRETATION: "报告解读",
  BEDSIDE_KNOWLEDGE: "床旁知识",
  PATHWAY_NODE: "路径节点",
};

const sourceActionText: Record<WorkflowTodoSourceType, string> = {
  FOLLOWUP_TASK: "打开随访记录",
  SAFETY_REVIEW: "打开复核来源",
  RECOMMENDATION_CARD: "打开提醒来源",
  NURSING_TASK: "打开护理任务",
  REPORT_INTERPRETATION: "打开报告上下文",
  BEDSIDE_KNOWLEDGE: "打开知识卡",
  PATHWAY_NODE: "打开路径节点",
};

const ORG_UNIT_REFERENCE_PAGE_SIZE = 20;
const TRANSFER_USER_REFERENCE_PAGE_SIZE = 20;
const route = findRouteByPath("/workflow/todos");
const PAGE_META = {
  title: route?.title ?? "协同任务",
  experience: route?.experience ?? {
    primaryRole: "临床使用者",
    goal: "处理当前岗位待办事项",
    defaultView: "待我处理",
    defaultFilters: [],
    evidenceDetailContent: ["患者上下文", "待办来源", "追踪号", "责任人编号"],
    interruptionLevel: "info" as const,
    evidence: "待办来源、责任流转和完成转交证据保持可回看",
    dataScale: {
      expected: "large" as const,
      pagination: "page" as const,
      exportStrategy: "none" as const,
    },
    riskLevel: "medium" as const,
  },
};

const assigneeRoleText: Record<string, string> = {
  "clinical-user": "临床使用者",
  CLINICAL_USER: "临床使用者",
  DOCTOR: "医生",
  NURSE: "护士",
  NURSING: "护理",
  PHARMACIST: "药师",
  TECHNICIAN: "医技",
  FOLLOWUP: "随访团队",
  QUALITY: "质控",
};

const transferRoleOptions = Object.entries(assigneeRoleText).map(([value, label]) => ({
  value,
  label,
}));

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

function countTodos<T extends string>(
  todos: WorkflowTodo[],
  field: "sourceType" | "priority",
  value: T,
) {
  return todos.filter((todo) => todo[field] === value).length;
}

function buildClinicalQueueFocus(pendingTodos: WorkflowTodo[]) {
  if (pendingTodos.length === 0) return "暂无待处理任务";

  const sourceFocus = (Object.keys(sourceRank) as WorkflowTodoSourceType[])
    .sort((left, right) => sourceRank[left] - sourceRank[right])
    .map((source) => ({
      source,
      count: countTodos(pendingTodos, "sourceType", source),
    }))
    .filter((item) => item.count > 0)
    .slice(0, 2)
    .map((item) => sourceText[item.source]);

  if (sourceFocus.length === 0) return "按到期时间处理";
  if (sourceFocus.length === 1) return `先处理${sourceFocus[0]}`;
  return `先处理${sourceFocus[0]}，再处理${sourceFocus[1]}`;
}

function assigneeDisplay(todo: WorkflowTodo, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) return todo.assigneeId || "-";
  if (todo.assigneeRole)
    return assigneeRoleText[todo.assigneeRole] ?? customerEnumLabel(todo.assigneeRole);
  return todo.assigneeId ? "已指定责任人" : "-";
}

function patientContextDisplay(todo: WorkflowTodo, evidenceDetailsEnabled: boolean) {
  if (evidenceDetailsEnabled) {
    return (
      <Space direction="vertical" size={0}>
        <span>{todo.patientId || "-"}</span>
        {todo.encounterId && <span className={styles.textSmall}>{todo.encounterId}</span>}
      </Space>
    );
  }
  return todo.patientId ? "已关联患者" : "-";
}

function sourceLinkUnavailableText(sourceType: WorkflowTodoSourceType) {
  return sourceType === "REPORT_INTERPRETATION"
    ? "报告入口暂不可跳转"
    : SOURCE_LINK_UNAVAILABLE_TEXT;
}

function sourceLinkMissingText(sourceType: WorkflowTodoSourceType) {
  return sourceType === "REPORT_INTERPRETATION" ? "待报告来源补充跳转" : SOURCE_LINK_MISSING_TEXT;
}

export default function WorkflowTodos() {
  const [status, setStatus] = useState<WorkflowTodoStatus | undefined>("PENDING");
  const [priority, setPriority] = useState<WorkflowPriority | undefined>();
  const [sourceType, setSourceType] = useState<WorkflowTodoSourceType | undefined>();
  const [orgUnitId, setOrgUnitId] = useState<string | undefined>();
  const [orgUnitSearch, setOrgUnitSearch] = useState("");
  const [transferUserSearch, setTransferUserSearch] = useState("");
  const [completingTodo, setCompletingTodo] = useState<WorkflowTodo | null>(null);
  const [transferringTodo, setTransferringTodo] = useState<WorkflowTodo | null>(null);
  const [completeForm] = Form.useForm<{ completionReason: string }>();
  const [transferForm] = Form.useForm<{
    transferTo: string;
    transferRole?: string;
    transferReason: string;
  }>();
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;

  const queryParams = {
    status,
    priority,
    sourceType,
    orgUnitId,
    page: 1,
    size: 10,
  };
  const { data, isError, isLoading, refetch } = useWorkflowTodos(queryParams);
  const orgUnitKeyword = orgUnitSearch.trim();
  const transferUserKeyword = transferUserSearch.trim();
  const { data: orgUnits, isLoading: orgUnitsLoading } = useOrgUnits({
    page: 1,
    size: ORG_UNIT_REFERENCE_PAGE_SIZE,
    status: "ACTIVE",
    ...(orgUnitKeyword ? { keyword: orgUnitKeyword } : {}),
  });
  const { data: transferUsers, isLoading: transferUsersLoading } = useOrgUsers({
    page: 1,
    size: TRANSFER_USER_REFERENCE_PAGE_SIZE,
    ...(transferUserKeyword ? { keyword: transferUserKeyword } : {}),
  });
  const completeMutation = useCompleteWorkflowTodo();
  const transferMutation = useTransferWorkflowTodo();
  const orgUnitOptions = useMemo(
    () =>
      (orgUnits?.items ?? [])
        .filter((unit) => unit.id)
        .map((unit) => ({ value: unit.id ?? "", label: unit.name })),
    [orgUnits?.items],
  );
  const transferUserOptions = useMemo(
    () =>
      (transferUsers?.items ?? []).map((user) => ({
        value: user.userId,
        label: user.displayName,
      })),
    [transferUsers?.items],
  );
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
  const pendingTodos = useMemo(
    () => visibleTodos.filter((todo) => todo.status === "PENDING" || todo.status === "IN_PROGRESS"),
    [visibleTodos],
  );
  const safetyReviewCount = countTodos(pendingTodos, "sourceType", "SAFETY_REVIEW");
  const nursingTaskCount = countTodos(pendingTodos, "sourceType", "NURSING_TASK");
  const reportInterpretationCount = countTodos(pendingTodos, "sourceType", "REPORT_INTERPRETATION");
  const criticalCount = countTodos(pendingTodos, "priority", "CRITICAL");
  const highPriorityCount = countTodos(pendingTodos, "priority", "HIGH");
  const queueFocus = buildClinicalQueueFocus(pendingTodos);

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

  const handleTransfer = async () => {
    if (!transferringTodo) return;
    const values = await transferForm.validateFields();
    try {
      await transferMutation.mutateAsync({
        todoId: transferringTodo.todoId,
        request: {
          transferTo: values.transferTo.trim(),
          transferRole: values.transferRole?.trim() || null,
          transferReason: values.transferReason.trim(),
        },
      });
      message.success("待办已转交");
      setTransferringTodo(null);
      setTransferUserSearch("");
      transferForm.resetFields();
      await refetch();
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, "待办转交失败"));
    }
  };

  const columns: TableProps<WorkflowTodo>["columns"] = [
    {
      title: "待办",
      dataIndex: "title",
      key: "title",
      render: (_value, record) => (
        <Space direction="vertical" size={2}>
          <span className={styles.textStrong}>{record.title}</span>
          <span className={styles.textSmall}>{record.summary}</span>
          <Space wrap size={8} className={styles.textSmall}>
            {evidenceDetailsEnabled ? (
              <>
                <span>来源编号 {record.sourceId}</span>
                <span>
                  {record.traceId ? `追踪号 ${record.traceId}` : SOURCE_TRACE_MISSING_TEXT}
                </span>
              </>
            ) : (
              <span>来源与追踪证据已保留</span>
            )}
          </Space>
        </Space>
      ),
    },
    {
      title: "来源",
      dataIndex: "sourceType",
      key: "sourceType",
      render: (value: WorkflowTodoSourceType) => <Tag>{sourceText[value]}</Tag>,
    },
    {
      title: "患者",
      dataIndex: "patientId",
      key: "patientId",
      render: (_value: string | null | undefined, record) =>
        patientContextDisplay(record, evidenceDetailsEnabled),
    },
    {
      title: evidenceDetailsEnabled ? "责任人" : "责任岗位",
      dataIndex: "assigneeId",
      key: "assigneeId",
      render: (_value: string | null | undefined, record) =>
        assigneeDisplay(record, evidenceDetailsEnabled),
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
      render: (value: WorkflowPriority) => (
        <Tag color={priorityColor[value]}>{priorityText[value] ?? customerEnumLabel(value)}</Tag>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (value: WorkflowTodoStatus) => (
        <Badge status={statusBadge[value]} text={statusText[value] ?? customerEnumLabel(value)} />
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_value, record) => {
        const sourceLink = resolveSourceDeepLink(record.deepLink);
        const sourceActionLabel = sourceActionText[record.sourceType];
        return (
          <Space size={4}>
            {sourceLink && (
              <Button
                type="link"
                aria-label={sourceActionLabel}
                icon={<LinkOutlined />}
                href={sourceLink}
                className={styles.buttonLink}
              >
                {sourceActionLabel}
              </Button>
            )}
            {!sourceLink && record.deepLink && (
              <Tag color="default">{sourceLinkUnavailableText(record.sourceType)}</Tag>
            )}
            {!sourceLink && !record.deepLink && (
              <Tag color="default">{sourceLinkMissingText(record.sourceType)}</Tag>
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
              className={styles.buttonLink}
            >
              完成
            </Button>
            <Button
              type="link"
              aria-label="转交"
              icon={<SwapRightOutlined />}
              disabled={record.status !== "PENDING" && record.status !== "IN_PROGRESS"}
              onClick={() => {
                setTransferringTodo(record);
                setTransferUserSearch("");
                transferForm.setFieldsValue({
                  transferTo: "",
                  transferRole: "",
                  transferReason: "",
                });
              }}
              className={styles.buttonLink}
            >
              转交
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      extras={
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
          刷新
        </Button>
      }
    >
      <div className={`${styles.surface} ${styles.queueSummary}`}>
        <div className={styles.sectionTitle}>今日先处理</div>
        <Space wrap size={[8, 8]}>
          <Tag color="blue">{pendingTodos.length} 项待处理</Tag>
          <Tag color="red">安全复核 {safetyReviewCount} 项</Tag>
          <Tag color="cyan">报告解读 {reportInterpretationCount} 项</Tag>
          <Tag color="purple">护理任务 {nursingTaskCount} 项</Tag>
          <Tag color="red">危急 {criticalCount} 项</Tag>
          <Tag color="volcano">高优先 {highPriorityCount} 项</Tag>
        </Space>
        <div className={styles.textSmall}>{queueFocus}</div>
      </div>

      <Card className={styles.sectionGap}>
        <Space wrap>
          <Select
            aria-label="待办状态"
            value={status}
            onChange={setStatus}
            allowClear
            className={styles.controlSm}
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
            className={styles.controlSm}
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
            className={styles.controlMd}
            options={[
              { value: "FOLLOWUP_TASK", label: "随访任务" },
              { value: "SAFETY_REVIEW", label: "安全复核" },
              { value: "PATHWAY_NODE", label: "路径节点" },
              { value: "RECOMMENDATION_CARD", label: "临床提醒" },
              { value: "NURSING_TASK", label: "护理任务" },
              { value: "REPORT_INTERPRETATION", label: "报告解读" },
              { value: "BEDSIDE_KNOWLEDGE", label: "床旁知识" },
            ]}
          />
          <Select
            id="workflow-todos-org-unit"
            value={orgUnitId}
            onChange={setOrgUnitId}
            allowClear
            showSearch
            filterOption={false}
            onSearch={setOrgUnitSearch}
            onClear={() => setOrgUnitSearch("")}
            loading={orgUnitsLoading}
            placeholder="组织范围"
            className={styles.controlMd}
            options={orgUnitOptions}
          />
          <label className="mk-sr-only" htmlFor="workflow-todos-org-unit">
            组织范围
          </label>
        </Space>
      </Card>

      {isError && (
        <Alert
          type="error"
          showIcon
          className={styles.sectionGap}
          message="协同待办读取失败"
          description="请确认登录状态、组织范围；若持续失败，请联系信息科核查协同任务服务。"
        />
      )}

      <Card className={styles.tablePanel}>
        <Table
          rowKey="todoId"
          columns={columns}
          dataSource={visibleTodos}
          loading={isLoading}
          tableLayout="fixed"
          scroll={{ x: 760 }}
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
        <Form form={completeForm} layout="vertical" className={styles.formGap}>
          <Form.Item
            name="completionReason"
            label="完成说明"
            rules={[{ required: true, message: "请输入完成说明" }]}
          >
            <TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="转交待办"
        open={!!transferringTodo}
        onOk={handleTransfer}
        onCancel={() => {
          setTransferringTodo(null);
          setTransferUserSearch("");
        }}
        okText="确认转交"
        cancelText="取消"
        confirmLoading={transferMutation.isPending}
        destroyOnClose
      >
        <Form form={transferForm} layout="vertical" className={styles.formGap}>
          <Alert
            type="info"
            showIcon
            message="请按姓名或院内人员身份选择接收人，岗位用于通知与审计留痕。"
          />
          <Form.Item
            name="transferTo"
            label="接收人员"
            rules={[{ required: true, message: "请选择接收人员" }]}
          >
            <Select
              showSearch
              filterOption={false}
              onSearch={setTransferUserSearch}
              placeholder="按姓名或院内人员身份搜索"
              options={transferUserOptions}
              loading={transferUsersLoading}
              notFoundContent="暂无可选接收人"
            />
          </Form.Item>
          <Form.Item name="transferRole" label="接收岗位">
            <Select allowClear placeholder="选择接收岗位" options={transferRoleOptions} />
          </Form.Item>
          <Form.Item
            name="transferReason"
            label="转交说明"
            rules={[{ required: true, message: "请输入转交说明" }]}
          >
            <TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </PageExperienceShell>
  );
}
