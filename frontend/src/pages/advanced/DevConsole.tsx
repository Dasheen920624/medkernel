import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { RoadmapLink } from "@/shared/ui/RoadmapLink";

export default function DevConsole() {
  return (
    <PageShell title="开发者控制台" description="技术快捷入口 · 仅架构师 / 信息科主任 / SRE 可见">
      <PageState
        state="empty"
        title="入口暂未激活"
        description="本页依赖 GA-ENG-LLM-01，引擎完成后激活。"
        action={<RoadmapLink taskIds={["GA-ENG-LLM-01"]} />}
      />
    </PageShell>
  );
}
