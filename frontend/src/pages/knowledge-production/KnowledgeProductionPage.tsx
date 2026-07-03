import { Alert, Card, Space, Steps } from "antd";
import { useEffect } from "react";

import { KnowledgeProductionWorkspace } from "@/pages/quality/KnowledgeGovernance";
import { PageShell } from "@/shared/ui/PageShell";

import MedicalEvaluationPanel from "./MedicalEvaluationPanel";
import ProductionReadinessPanel from "./ProductionReadinessPanel";
import ProviderSetupPanel from "./ProviderSetupPanel";
import styles from "./KnowledgeProductionPage.module.css";

const STEP_IDS = ["provider", "evaluation", "readiness", "production"] as const;

export default function KnowledgeProductionPage() {
  useEffect(() => {
    const step = new URLSearchParams(window.location.search).get("step");
    if (!step || !STEP_IDS.includes(step as (typeof STEP_IDS)[number])) return;
    document.getElementById(step)?.scrollIntoView({ block: "start" });
  }, []);

  return (
    <PageShell
      title="知识生产"
      description="在同一页面完成模型服务配置、医学评测、生产前校验和知识候选治理"
    >
      <Space direction="vertical" size="large" className={styles.consoleStack}>
        <Alert
          type="info"
          showIcon
          message="正式知识不得绕过统一治理链"
          description="人工维护、来源解析和模型生成都只能形成草稿或候选；无模型时仍可完成来源登记、人工维护、确定性校验、审核发布。"
        />
        <Card>
          <Steps
            responsive
            items={[
              { title: "模型服务与密钥" },
              { title: "医学评测" },
              { title: "生产前校验" },
              { title: "开始生产" },
            ]}
          />
        </Card>
        <section id="provider" aria-label="模型服务与密钥" className={styles.section}>
          <ProviderSetupPanel />
        </section>
        <section id="evaluation" aria-label="医学评测" className={styles.section}>
          <MedicalEvaluationPanel />
        </section>
        <section id="readiness" aria-label="生产前校验" className={styles.section}>
          <ProductionReadinessPanel />
        </section>
        <section id="production" aria-label="开始生产" className={styles.section}>
          <KnowledgeProductionWorkspace />
        </section>
      </Space>
    </PageShell>
  );
}
