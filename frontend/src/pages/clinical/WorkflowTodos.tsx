import { PageShell } from "@/shared/ui/PageShell";

export default function WorkflowTodos() {
  return (
    <PageShell
      title="工作流协同待办中心"
      description="审批、整改、发布与回滚待办"
      state="empty"
      stateProps={{
        title: "待办接口尚未接入",
        description:
          "当前版本不再展示本地待办样例；待 TODO-01 接入真实待办 API 后，再启用办理、审计和 SLA 闭环。",
      }}
    >
      <></>
    </PageShell>
  );
}
