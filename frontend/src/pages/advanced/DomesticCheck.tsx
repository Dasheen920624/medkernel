import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { RoadmapLink } from "@/shared/ui/RoadmapLink";

export default function DomesticCheck() {
  return (
    <PageShell
      title="国产化自检"
      description="实时检测当前 OS / JDK / DB / 中间件 / 国密 Provider 的国产化等级"
    >
      <PageState
        state="empty"
        title="入口暂未激活"
        description="国产化自检已并入 /system/operations 运行底座快照；本入口待运维域整合方案完成后激活。"
        action={<RoadmapLink taskIds={["GA-ENG-BASE-07"]} />}
      />
    </PageShell>
  );
}
