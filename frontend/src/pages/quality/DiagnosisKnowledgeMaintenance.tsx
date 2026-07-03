import { PageShell } from "@/shared/ui/PageShell";
import DiagnosisKnowledgePanel from "./DiagnosisKnowledgePanel";

export default function DiagnosisKnowledgeMaintenance() {
  return (
    <PageShell
      title="诊断知识库"
      description="在统一知识治理下维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据；发布后再进入平台标准版本或机构生效版本。"
    >
      <DiagnosisKnowledgePanel />
    </PageShell>
  );
}
