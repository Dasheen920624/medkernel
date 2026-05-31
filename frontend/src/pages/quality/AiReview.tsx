import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { RoadmapLink } from "@/shared/ui/RoadmapLink";

export default function AiReview() {
  return (
    <PageShell
      title="AI 知识审核"
      description="AI 推荐 + 证据来源 + 不采纳归因；只在专家模式开放规则全文"
    >
      <PageState
        state="empty"
        title="入口暂未激活"
        description="本页依赖 GA-ENG-CDSS-01、GA-ENG-EVAL-01，引擎完成后激活。"
        action={<RoadmapLink taskIds={["GA-ENG-CDSS-01", "GA-ENG-EVAL-01"]} />}
      />
    </PageShell>
  );
}
