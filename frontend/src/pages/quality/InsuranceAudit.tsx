import { PageShell } from "@/shared/ui/PageShell";

export default function InsuranceAudit() {
  return (
    <PageShell
      title="医保智能审核与控费管理"
      description="医保审核、控费预警与申诉闭环"
      state="empty"
      stateProps={{
        title: "医保审核接口尚未接入",
        description:
          "当前版本不展示本地违规病例样例；待 INSAUDIT-01 接入真实医保审核 API 后，再呈现疑点、申诉和复核闭环。",
      }}
    >
      <></>
    </PageShell>
  );
}
