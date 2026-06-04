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
  useTransferWorkflowTodo: vi.fn(),
  useWorkflowTodos: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useCompleteWorkflowTodo: workflowHookMocks.useCompleteWorkflowTodo,
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
    expect(screen.getByText("FOLLOWUP_TASK")).toBeInTheDocument();
    expect(screen.queryByText("待办接口尚未接入")).not.toBeInTheDocument();
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
