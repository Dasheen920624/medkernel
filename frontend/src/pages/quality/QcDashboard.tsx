import { PageShell } from "@/shared/ui/PageShell";

export default function QcDashboard() {
  return (
    <PageShell
      title="院级医疗质量控制驾驶舱"
      description="质控指标、风险下钻与整改闭环"
      state="empty"
      stateProps={{
        title: "质控驾驶舱汇总接口尚未接入",
        description:
          "当前版本不展示本地指标和病例样例；待 QCDASH-01 接入真实质控汇总 API 后，再呈现达标率、风险热力和下钻。",
      }}
    >
      <></>
    </PageShell>
  );
}
