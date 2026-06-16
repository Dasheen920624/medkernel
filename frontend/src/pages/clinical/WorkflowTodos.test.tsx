import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import WorkflowTodos from "./WorkflowTodos";

const workflowHookMocks = vi.hoisted(() => ({
  completeTodo: vi.fn(),
  refetchTodos: vi.fn(),
  transferTodo: vi.fn(),
  useCompleteWorkflowTodo: vi.fn(),
  useOrgUnits: vi.fn(),
  useTransferWorkflowTodo: vi.fn(),
  useWorkflowTodos: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useCompleteWorkflowTodo: workflowHookMocks.useCompleteWorkflowTodo,
  useOrgUnits: workflowHookMocks.useOrgUnits,
  useTransferWorkflowTodo: workflowHookMocks.useTransferWorkflowTodo,
  useWorkflowTodos: workflowHookMocks.useWorkflowTodos,
}));

function renderWorkflowTodos() {
  return render(
    <ConfigProvider>
      <AntdApp>
        <WorkflowTodos />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe("WorkflowTodos", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    workflowHookMocks.completeTodo.mockResolvedValue({
      todoId: "todo-real-1",
      status: "COMPLETED",
      completedBy: "doctor-real-1",
      completionReason: "已完成真实随访处理",
    });
    workflowHookMocks.transferTodo.mockResolvedValue({
      todoId: "todo-real-1",
      status: "TRANSFERRED",
      transferredTo: "nurse-2",
      transferReason: "交由护理站安排回院确认",
    });
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-real-1",
            sourceType: "FOLLOWUP_TASK",
            sourceId: "return-task-1",
            title: "随访异常复核",
            summary: "患者上报呼吸困难，需要医师复核",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "doctor-real-1",
            assigneeRole: "DOCTOR",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            dueAt: "2026-06-04T08:00:00Z",
            traceId: "trace-workflow",
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });
    workflowHookMocks.useCompleteWorkflowTodo.mockReturnValue({
      isPending: false,
      mutateAsync: workflowHookMocks.completeTodo,
    });
    workflowHookMocks.useTransferWorkflowTodo.mockReturnValue({
      isPending: false,
      mutateAsync: workflowHookMocks.transferTodo,
    });
    workflowHookMocks.useOrgUnits.mockReturnValue({
      data: {
        items: [
          {
            id: "dept-a",
            parentId: null,
            tenantId: "tenant-A",
            orgPath: "/TENANT-A/DEPT-A",
            level: "DEPARTMENT",
            code: "DEPT-A",
            name: "A 科室",
            status: "ACTIVE",
          },
          {
            id: "spec-a1",
            parentId: "dept-a",
            tenantId: "tenant-A",
            orgPath: "/TENANT-A/DEPT-A/SPEC-A1",
            level: "SPECIALTY",
            code: "SPEC-A1",
            name: "A1 专病",
            status: "ACTIVE",
          },
        ],
        page: 1,
        size: 100,
        total: 2,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
    });
  });

  it("keeps the workflow todo table contained on mobile viewports", () => {
    const source = readFileSync(resolve(process.cwd(), "src/pages/clinical/WorkflowTodos.tsx"), {
      encoding: "utf8",
    });
    const styles = readFileSync(resolve(process.cwd(), "src/pages/clinical/Clinical.module.css"), {
      encoding: "utf8",
    });

    expect(source).toContain("className={styles.tablePanel}");
    expect(source).toContain('tableLayout="fixed"');
    expect(source).toContain("scroll={{ x: 760 }}");
    expect(styles).toMatch(/\.tablePanel\s*\{[^}]*min-width:\s*0;[^}]*overflow:\s*hidden;/s);
  });

  it("renders real workflow todos from the unified API instead of the old placeholder", () => {
    renderWorkflowTodos();

    expect(workflowHookMocks.useWorkflowTodos).toHaveBeenCalledWith({
      status: "PENDING",
      priority: undefined,
      sourceType: undefined,
      page: 1,
      size: 10,
    });
    expect(screen.getByRole("heading", { name: "工作流协同待办中心" })).toBeInTheDocument();
    expect(screen.getByText("随访异常复核")).toBeInTheDocument();
    expect(screen.getByText("patient-real-1")).toBeInTheDocument();
    expect(screen.getByText("来源对象 return-task-1")).toBeInTheDocument();
    expect(screen.getByText("追踪链路 trace-workflow")).toBeInTheDocument();
    expect(screen.getByText("随访任务")).toBeInTheDocument();
    expect(screen.queryByText("FOLLOWUP_TASK")).not.toBeInTheDocument();
    expect(screen.queryByText("待办接口尚未接入")).not.toBeInTheDocument();
  });

  it("passes selected organization scope to the server-side todo query", async () => {
    const user = userEvent.setup();
    renderWorkflowTodos();

    expect(workflowHookMocks.useOrgUnits).toHaveBeenCalledWith({
      page: 1,
      size: 20,
      status: "ACTIVE",
    });

    await user.click(screen.getByLabelText("组织范围"));
    await user.click(await screen.findByText("A 科室"));

    await waitFor(() => {
      expect(workflowHookMocks.useWorkflowTodos).toHaveBeenLastCalledWith({
        status: "PENDING",
        priority: undefined,
        sourceType: undefined,
        orgUnitId: "dept-a",
        page: 1,
        size: 10,
      });
    });
  });

  it("renders clinical collaboration source labels without exposing backend enum names", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-nursing-1",
            sourceType: "NURSING_TASK",
            sourceId: "card-nursing-1",
            title: "压疮风险评估",
            summary: "护理评估提示风险升高",
            priority: "HIGH",
            status: "PENDING",
            patientId: "patient-real-1",
          },
          {
            todoId: "todo-report-1",
            sourceType: "REPORT_INTERPRETATION",
            sourceId: "card-report-1",
            title: "检验结果复核",
            summary: "报告结果触发复核建议",
            priority: "MEDIUM",
            status: "PENDING",
            patientId: "patient-real-1",
          },
          {
            todoId: "todo-knowledge-1",
            sourceType: "BEDSIDE_KNOWLEDGE",
            sourceId: "card-knowledge-1",
            title: "知识卡复核",
            summary: "当前诊疗上下文命中知识卡",
            priority: "LOW",
            status: "PENDING",
            patientId: "patient-real-1",
          },
          {
            todoId: "todo-card-1",
            sourceType: "RECOMMENDATION_CARD",
            sourceId: "card-risk-1",
            title: "用药风险提醒",
            summary: "医嘱触发风险规则",
            priority: "HIGH",
            status: "PENDING",
            patientId: "patient-real-1",
          },
          {
            todoId: "todo-pathway-1",
            sourceType: "PATHWAY_NODE",
            sourceId: "pp-1:ASSESS:clock-1",
            title: "路径节点待处理：入径评估",
            summary: "责任：专科医生；签责：科主任",
            priority: "MEDIUM",
            status: "PENDING",
            patientId: "patient-real-1",
          },
        ],
        page: 0,
        size: 10,
        total: 5,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    expect(screen.getByText("护理任务")).toBeInTheDocument();
    expect(screen.getByText("报告解读")).toBeInTheDocument();
    expect(screen.getByText("床旁知识")).toBeInTheDocument();
    expect(screen.getByText("临床提醒")).toBeInTheDocument();
    expect(screen.getByText("路径节点")).toBeInTheDocument();
    expect(screen.queryByText("NURSING_TASK")).not.toBeInTheDocument();
    expect(screen.queryByText("REPORT_INTERPRETATION")).not.toBeInTheDocument();
    expect(screen.queryByText("BEDSIDE_KNOWLEDGE")).not.toBeInTheDocument();
    expect(screen.queryByText("RECOMMENDATION_CARD")).not.toBeInTheDocument();
    expect(screen.queryByText("PATHWAY_NODE")).not.toBeInTheDocument();
  });

  it("keeps safety review todos ahead of lower-risk follow-up rows", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-followup-1",
            sourceType: "FOLLOWUP_TASK",
            sourceId: "return-task-1",
            title: "随访异常复核",
            summary: "患者上报呼吸困难，需要医师复核",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "doctor-real-1",
            assigneeRole: "DOCTOR",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            dueAt: "2026-06-04T08:00:00Z",
          },
          {
            todoId: "todo-safety-1",
            sourceType: "SAFETY_REVIEW",
            sourceId: "withdrawal:patient-real-1",
            title: "安全撤回复核任务",
            summary: "旧版禁忌知识撤回后需要复核患者病例",
            priority: "CRITICAL",
            status: "PENDING",
            assigneeId: "doctor-real-1",
            assigneeRole: "DOCTOR",
            patientId: "patient-real-1",
            dueAt: "2026-06-04T10:00:00Z",
          },
        ],
        page: 0,
        size: 10,
        total: 2,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    const safetyTitle = screen.getByText("安全撤回复核任务");
    const followupTitle = screen.getByText("随访异常复核");
    expect(safetyTitle.compareDocumentPosition(followupTitle)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it("exposes a source jump only when the backend provides a deep link", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-real-1",
            sourceType: "FOLLOWUP_TASK",
            sourceId: "return-task-1",
            title: "随访异常复核",
            summary: "患者上报呼吸困难，需要医师复核",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "doctor-real-1",
            assigneeRole: "DOCTOR",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            dueAt: "2026-06-04T08:00:00Z",
            deepLink: "/clinical/followup?taskId=return-task-1",
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    expect(screen.getByRole("link", { name: "打开来源" })).toHaveAttribute(
      "href",
      "/clinical/followup?taskId=return-task-1",
    );
  });

  it("does not expose unsafe source jumps from workflow todos", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-followup-unsafe",
            sourceType: "FOLLOWUP_TASK",
            sourceId: "return-task-unsafe",
            title: "随访异常复核",
            summary: "患者上报呼吸困难，需要医师复核",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "doctor-real-1",
            assigneeRole: "DOCTOR",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            dueAt: "2026-06-04T08:00:00Z",
            deepLink: "javascript:alert('unsafe')",
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    expect(screen.queryByRole("link", { name: "打开来源" })).not.toBeInTheDocument();
    expect(screen.getByText("来源暂不可跳转")).toBeInTheDocument();
  });

  it("shows an honest source jump status when workflow todos have no deep link", () => {
    renderWorkflowTodos();

    expect(screen.queryByRole("link", { name: "打开来源" })).not.toBeInTheDocument();
    expect(screen.queryByText("来源暂不可跳转")).not.toBeInTheDocument();
    expect(screen.getByText("来源未提供跳转")).toBeInTheDocument();
  });

  it("shows an honest trace status when workflow todos have no trace id", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-no-trace",
            sourceType: "FOLLOWUP_TASK",
            sourceId: "return-task-no-trace",
            title: "随访异常复核",
            summary: "患者上报呼吸困难，需要医师复核",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "doctor-real-1",
            assigneeRole: "DOCTOR",
            patientId: "patient-real-1",
            encounterId: "enc-real-1",
            dueAt: "2026-06-04T08:00:00Z",
            traceId: null,
          },
        ],
        page: 0,
        size: 10,
        total: 1,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    expect(screen.getByText("来源对象 return-task-no-trace")).toBeInTheDocument();
    expect(screen.getByText("追踪链路未提供")).toBeInTheDocument();
    expect(screen.queryByText(/^追踪链路 trace-/)).not.toBeInTheDocument();
  });

  it("persists completion through the backend and refreshes the server-side list", async () => {
    const user = userEvent.setup();
    renderWorkflowTodos();

    await user.click(screen.getByRole("button", { name: "完成" }));
    await user.type(screen.getByLabelText("完成说明"), "已完成真实随访处理");
    await user.click(screen.getByRole("button", { name: "确认完成" }));

    await waitFor(() => {
      expect(workflowHookMocks.completeTodo).toHaveBeenCalledWith({
        todoId: "todo-real-1",
        request: { completionReason: "已完成真实随访处理" },
      });
    });
    expect(workflowHookMocks.refetchTodos).toHaveBeenCalled();
  });

  it("persists transfer through the backend instead of changing browser-only state", async () => {
    const user = userEvent.setup();
    renderWorkflowTodos();

    await user.click(screen.getByRole("button", { name: "转交" }));
    await user.type(screen.getByLabelText("接收人"), "nurse-2");
    await user.type(screen.getByLabelText("接收角色"), "NURSING");
    await user.type(screen.getByLabelText("转交说明"), "交由护理站安排回院确认");
    await user.click(screen.getByRole("button", { name: "确认转交" }));

    await waitFor(() => {
      expect(workflowHookMocks.transferTodo).toHaveBeenCalledWith({
        todoId: "todo-real-1",
        request: {
          transferTo: "nurse-2",
          transferRole: "NURSING",
          transferReason: "交由护理站安排回院确认",
        },
      });
    });
    expect(workflowHookMocks.refetchTodos).toHaveBeenCalled();
  });
});
