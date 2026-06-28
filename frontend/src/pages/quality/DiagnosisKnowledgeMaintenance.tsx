import { PageShell } from "@/shared/ui/PageShell";
import DiagnosisKnowledgePanel from "./DiagnosisKnowledgePanel";

export default function DiagnosisKnowledgeMaintenance() {
  return (
    <PageShell
      title="诊断知识维护"
      description="维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据。"
    >
      <DiagnosisKnowledgePanel />
    </PageShell>
  );
}
