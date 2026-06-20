import { Alert, Card, Space, Steps } from "antd";
import { useEffect } from "react";

import { KnowledgeProductionWorkspace } from "@/pages/quality/KnowledgeGovernance";
import { PageShell } from "@/shared/ui/PageShell";

import MedicalEvaluationPanel from "./MedicalEvaluationPanel";
import ProductionReadinessPanel from "./ProductionReadinessPanel";
import ProviderSetupPanel from "./ProviderSetupPanel";

const STEP_IDS = ["provider", "evaluation", "review", "readiness", "production"] as const;

export default function ModelProductionConsole() {
  useEffect(() => {
    const step = new URLSearchParams(window.location.search).get("step");
    if (!step || !STEP_IDS.includes(step as (typeof STEP_IDS)[number])) return;
    document.getElementById(step)?.scrollIntoView({ block: "start" });
  }, []);

  return (
    <PageShell
      title="模型生产控制台"
      description="在同一页面完成模型服务配置、医学评测、独立复核、九项放行和大模型知识生产"
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Alert
          type="info"
          showIcon
          message="正式知识只允许大模型生产"
          description="模型生成内容始终先进入候选治理链；缺少任一门禁时不会自动降级为 B0，也不会成为有效知识。"
        />
        <Card>
          <Steps
            responsive
            items={[
              { title: "模型服务与 Key" },
              { title: "医学评测" },
              { title: "独立复核" },
              { title: "九项生产闸" },
              { title: "开始生产" },
            ]}
          />
        </Card>
        <section id="provider" aria-label="模型服务与 Key">
          <ProviderSetupPanel />
        </section>
        <section id="evaluation" aria-label="医学评测与独立复核">
          <MedicalEvaluationPanel />
        </section>
        <section id="readiness" aria-label="九项生产闸">
          <ProductionReadinessPanel />
        </section>
        <section id="production" aria-label="开始生产">
          <Card title="开始生产">
            <KnowledgeProductionWorkspace />
          </Card>
        </section>
      </Space>
    </PageShell>
  );
}
