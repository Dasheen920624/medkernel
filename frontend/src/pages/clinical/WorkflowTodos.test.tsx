import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App as AntdApp, ConfigProvider } from "antd";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import WorkflowTodos from "./WorkflowTodos";

const workflowHookMocks = vi.hoisted(() => ({
  completeTodo: vi.fn(),
  refetchTodos: vi.fn(),
  transferTodo: vi.fn(),
  useCompleteWorkflowTodo: vi.fn(),
  useOrgUsers: vi.fn(),
  useOrgUnits: vi.fn(),
  useSecurityProfile: vi.fn(),
  useTransferWorkflowTodo: vi.fn(),
  useWorkflowTodos: vi.fn(),
}));

vi.mock("@/shared/api/hooks", () => ({
  useCompleteWorkflowTodo: workflowHookMocks.useCompleteWorkflowTodo,
  useOrgUsers: workflowHookMocks.useOrgUsers,
  useOrgUnits: workflowHookMocks.useOrgUnits,
  useSecurityProfile: workflowHookMocks.useSecurityProfile,
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
    useEvidenceDetailsStore.setState({ enabled: false });
    workflowHookMocks.useSecurityProfile.mockReturnValue({
      data: {
        permissions: [
          { code: "workflow.read", dimension: "ACTION", target: "workflow" },
          { code: "system.debug", dimension: "ACTION", target: "system" },
        ],
        roles: [{ code: "clinical-user", displayName: "临床使用者", source: "TEST" }],
        menuKeys: ["workflow-todos", "runtime-diagnostics"],
        environmentKeys: ["production"],
        dataScope: { tenantId: "tenant-A" },
      },
    });
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
            assigneeRole: "clinical-user",
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
    workflowHookMocks.useOrgUsers.mockReturnValue({
      data: {
        items: [
          { userId: "doctor-1", displayName: "王医生" },
          { userId: "nurse-2", displayName: "张护士" },
        ],
        page: 1,
        size: 20,
        total: 2,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
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
    expect(screen.getByRole("heading", { name: "协同任务" })).toBeInTheDocument();
    expect(screen.getByText("处理当前岗位待办事项")).toBeInTheDocument();
    expect(screen.getByText("随访异常复核")).toBeInTheDocument();
    expect(screen.getByText("已关联患者")).toBeInTheDocument();
    expect(screen.getByText("随访异常复核").closest("tr")).toHaveTextContent("临床使用者");
    expect(screen.getByText("来源与追踪证据已保留")).toBeInTheDocument();
    expect(screen.queryByText("patient-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("return-task-1")).not.toBeInTheDocument();
    expect(screen.queryByText("trace-workflow")).not.toBeInTheDocument();
    expect(screen.getByText("随访任务")).toBeInTheDocument();
    expect(screen.getByText("高优先")).toBeInTheDocument();
    expect(screen.queryByText("FOLLOWUP_TASK")).not.toBeInTheDocument();
    expect(screen.queryByText("HIGH")).not.toBeInTheDocument();
    expect(screen.queryByText("待办接口尚未接入")).not.toBeInTheDocument();
  });

  it("reveals workflow todo source evidence only after evidence details are enabled", async () => {
    const user = userEvent.setup();
    renderWorkflowTodos();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("patient-real-1")).toBeInTheDocument();
    expect(screen.getByText("enc-real-1")).toBeInTheDocument();
    expect(screen.getByText("来源编号 return-task-1")).toBeInTheDocument();
    expect(screen.getByText("追踪号 trace-workflow")).toBeInTheDocument();
    expect(screen.getByText("doctor-real-1")).toBeInTheDocument();
  });

  it("does not reveal workflow evidence when the role lacks evidence-detail permission", () => {
    useEvidenceDetailsStore.setState({ enabled: true });
    workflowHookMocks.useSecurityProfile.mockReturnValue({
      data: {
        permissions: [{ code: "workflow.read", dimension: "ACTION", target: "workflow" }],
        roles: [{ code: "clinical-user", displayName: "临床使用者", source: "TEST" }],
        menuKeys: ["workflow-todos"],
        environmentKeys: ["production"],
        dataScope: { tenantId: "tenant-A" },
      },
    });

    renderWorkflowTodos();

    expect(screen.queryByRole("switch", { name: "证据详情" })).not.toBeInTheDocument();
    expect(screen.getByText("已关联患者")).toBeInTheDocument();
    expect(screen.queryByText("patient-real-1")).not.toBeInTheDocument();
    expect(screen.queryByText("来源编号 return-task-1")).not.toBeInTheDocument();
    expect(screen.queryByText("追踪号 trace-workflow")).not.toBeInTheDocument();
  });

  it("keeps workflow todo read failures in organization and information-office language", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    expect(screen.getByText("协同待办读取失败")).toBeInTheDocument();
    expect(
      screen.getByText("请确认登录状态、组织范围；若持续失败，请联系信息科核查协同任务服务。"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/协同任务服务状态/)).not.toBeInTheDocument();
  });

  it("summarizes the clinical work queue so doctors and nurses know what to handle first", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
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
          {
            todoId: "todo-nursing-1",
            sourceType: "NURSING_TASK",
            sourceId: "nursing:patient-real-2",
            title: "压疮风险评估",
            summary: "护理评估提示风险升高",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "nurse-real-1",
            assigneeRole: "NURSE",
            patientId: "patient-real-2",
            dueAt: "2026-06-04T09:00:00Z",
          },
          {
            todoId: "todo-completed-1",
            sourceType: "FOLLOWUP_TASK",
            sourceId: "return-task-2",
            title: "已完成随访确认",
            summary: "患者已完成问卷回收",
            priority: "LOW",
            status: "COMPLETED",
            patientId: "patient-real-3",
          },
        ],
        page: 0,
        size: 10,
        total: 3,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    expect(screen.getByText("今日先处理")).toBeInTheDocument();
    expect(screen.getByText("2 项待处理")).toBeInTheDocument();
    expect(screen.getByText("安全复核 1 项")).toBeInTheDocument();
    expect(screen.getByText("护理任务 1 项")).toBeInTheDocument();
    expect(screen.getByText("危急 1 项")).toBeInTheDocument();
    expect(screen.getByText("高优先 1 项")).toBeInTheDocument();
    expect(screen.getByText("先处理安全复核，再处理护理任务")).toBeInTheDocument();
  });

  it("surfaces report interpretation work as a clinical queue focus instead of burying it behind routine tasks", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
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
          },
          {
            todoId: "todo-report-1",
            sourceType: "REPORT_INTERPRETATION",
            sourceId: "report:patient-real-1",
            title: "检验报告解读待办",
            summary: "最新检验结果需要医技完成辅助解读",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "technician-real-1",
            assigneeRole: "TECHNICIAN",
            patientId: "patient-real-1",
          },
          {
            todoId: "todo-nursing-1",
            sourceType: "NURSING_TASK",
            sourceId: "nursing:patient-real-2",
            title: "压疮风险评估",
            summary: "护理评估提示风险升高",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "nurse-real-1",
            assigneeRole: "NURSE",
            patientId: "patient-real-2",
          },
        ],
        page: 0,
        size: 10,
        total: 3,
        hasNext: false,
      },
      isError: false,
      isLoading: false,
      refetch: workflowHookMocks.refetchTodos,
    });

    renderWorkflowTodos();

    expect(screen.getByText("报告解读 1 项")).toBeInTheDocument();
    expect(screen.getByText("先处理安全复核，再处理报告解读")).toBeInTheDocument();
    const reportTitle = screen.getByText("检验报告解读待办");
    const nursingTitle = screen.getByText("压疮风险评估");
    expect(reportTitle.compareDocumentPosition(nursingTitle)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
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

  it("renders clinical collaboration source labels without exposing service enum names", () => {
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

  it("exposes a source jump only when the service provides a deep link", () => {
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

    expect(screen.getByRole("link", { name: "打开随访记录" })).toHaveAttribute(
      "href",
      "/clinical/followup?taskId=return-task-1",
    );
  });

  it("uses a report-context action label for report interpretation todos", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-report-1",
            sourceType: "REPORT_INTERPRETATION",
            sourceId: "report:patient-real-1",
            title: "检验报告解读待办",
            summary: "最新检验结果需要医技完成辅助解读",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "technician-real-1",
            assigneeRole: "TECHNICIAN",
            patientId: "patient-real-1",
            deepLink: "/cdss/fatigue",
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

    expect(screen.getByRole("link", { name: "打开报告上下文" })).toHaveAttribute(
      "href",
      "/cdss/fatigue",
    );
    expect(screen.queryByRole("link", { name: "打开来源" })).not.toBeInTheDocument();
  });

  it("keeps runtime release and trigger identifiers inside evidence details for report todos", async () => {
    const user = userEvent.setup();
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-report-runtime",
            sourceType: "REPORT_INTERPRETATION",
            sourceId: "report:patient-real-1",
            title: "医技报告解读：血钾检验",
            summary:
              "已登记报告「血钾检验」结合当前机构生效版本 runtime-01KWC6Q7ZXG7B4MJQQ3PTYJJYY 中的「检验项目说明书来源与使用边界」生成辅助解读，触发点：result-review",
            priority: "HIGH",
            status: "PENDING",
            assigneeId: "technician-real-1",
            assigneeRole: "TECHNICIAN",
            patientId: "patient-real-1",
            deepLink: "/cdss/fatigue",
            traceId: "trace-report-runtime",
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

    expect(screen.getByText("医技报告解读：血钾检验")).toBeInTheDocument();
    expect(screen.getByText(/报告结果需要结合患者上下文完成辅助解读/)).toBeInTheDocument();
    expect(screen.queryByText(/runtime-01KWC6Q7ZXG7B4MJQQ3PTYJJYY/)).not.toBeInTheDocument();
    expect(screen.queryByText(/result-review/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText(/runtime-01KWC6Q7ZXG7B4MJQQ3PTYJJYY/)).toBeInTheDocument();
    expect(screen.getByText(/触发点：result-review/)).toBeInTheDocument();
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

  it("tells medical technicians when a report interpretation todo still lacks a report entrance", () => {
    workflowHookMocks.useWorkflowTodos.mockReturnValue({
      data: {
        items: [
          {
            todoId: "todo-report-missing-link",
            sourceType: "REPORT_INTERPRETATION",
            sourceId: "report:patient-real-1",
            title: "检验报告解读待办",
            summary: "最新检验结果需要医技完成辅助解读",
            priority: "HIGH",
            status: "PENDING",
            assigneeRole: "TECHNICIAN",
            patientId: "patient-real-1",
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

    expect(screen.getByText("待报告来源补充跳转")).toBeInTheDocument();
    expect(screen.queryByText("来源未提供跳转")).not.toBeInTheDocument();
  });

  it("shows an honest trace status when workflow todos have no trace id", async () => {
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

    await userEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("来源编号 return-task-no-trace")).toBeInTheDocument();
    expect(screen.getByText("追踪号未提供")).toBeInTheDocument();
    expect(screen.queryByText(/^追踪号 trace-/)).not.toBeInTheDocument();
  });

  it("persists completion through the service and refreshes the server-side list", async () => {
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

  it("lets clinical users transfer todos by selecting a person and role in hospital language", async () => {
    const user = userEvent.setup();
    renderWorkflowTodos();

    await user.click(screen.getByRole("button", { name: "转交" }));
    expect(workflowHookMocks.useOrgUsers).toHaveBeenCalledWith({
      page: 1,
      size: 20,
    });
    expect(
      screen.getByText("请按姓名或院内人员身份选择接收人，岗位用于通知与审计留痕。"),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("接收角色")).not.toBeInTheDocument();
    expect(screen.queryByText("NURSING")).not.toBeInTheDocument();

    await user.click(screen.getByLabelText("接收人员"));
    await user.click(await screen.findByText("张护士"));
    await user.click(screen.getByLabelText("接收岗位"));
    await user.click(await screen.findByText("护理"));
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
