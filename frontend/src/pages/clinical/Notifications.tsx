import { PageShell } from "@/shared/ui/PageShell";

export default function Notifications() {
  return (
    <PageShell
      title="通知中心与低打扰过滤配置"
      description="通知、预警与低打扰策略"
      state="empty"
      stateProps={{
        title: "通知接口尚未接入",
        description:
          "当前版本不展示本地通知示例；待 NOTIFY-01 接入真实通知 API 后，再呈现未读、筛选和已读闭环。",
      }}
    >
      <></>
    </PageShell>
  );
}
