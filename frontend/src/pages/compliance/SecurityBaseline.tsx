import { PageShell } from "@/shared/ui/PageShell";

export default function SecurityBaseline() {
  return (
    <PageShell
      title="合规与安全基线自查台账"
      description="等保、商密与个保基线状态"
      state="empty"
      stateProps={{
        title: "安全基线自查接口尚未接入",
        description:
          "当前版本不展示本地安全评分样例；待 SECBASE-01 接入真实自查 API 后，再呈现风险、证据和复核结果。",
      }}
    >
      <></>
    </PageShell>
  );
}
