import { Alert, Button, Card, Descriptions, Space, Tag, Typography } from "antd";

import { getApiErrorMessage } from "@/shared/api/errors";
import {
  type KnowledgeProductionReadinessItem,
  useKnowledgeProductionReadiness,
  useSecurityProfile,
} from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import { EvidenceDetailsToggle } from "@/shared/ui/EvidenceDetailsToggle";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;

const GATES = [
  { code: "LITERATURE_ROOT", label: "文献资料库", step: "readiness", owner: "医疗引擎运营员" },
  { code: "DEPLOYMENT_FORM", label: "部署形态", step: "readiness", owner: "医疗引擎运营员" },
  { code: "MODEL_PROVIDER", label: "模型服务", step: "provider", owner: "医疗引擎运营员" },
  {
    code: "REGRESSION_BASELINE",
    label: "医学验证用例",
    step: "evaluation",
    owner: "医疗引擎运营员",
  },
  {
    code: "MODEL_EVALUATION",
    label: "医学评测",
    step: "evaluation",
    owner: "医疗引擎运营员",
  },
  { code: "EGRESS_GOVERNANCE", label: "模型使用边界", step: "readiness", owner: "医疗引擎运营员" },
  { code: "MODEL_POLICY", label: "模型生产策略", step: "readiness", owner: "医疗引擎运营员" },
  {
    code: "VERSION_TRIPLE",
    label: "提示词、工具与模型版本",
    step: "readiness",
    owner: "医疗引擎运营员",
  },
] as const;

function evidenceSummary(
  item: KnowledgeProductionReadinessItem | undefined,
  evidenceDetailsEnabled: boolean,
) {
  if (!item) return "证据待补齐";
  if (evidenceDetailsEnabled) return `证据：${item.evidence || "未返回"}`;
  return item.evidence ? "证据已记录" : "证据待补齐";
}

export default function ProductionReadinessPanel() {
  const security = useSecurityProfile();
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const readiness = useKnowledgeProductionReadiness({ producer: "API_MODEL" });
  const itemByCode = new Map(readiness.data?.items.map((item) => [item.code, item]) ?? []);

  let content;
  if (readiness.isLoading) {
    content = <PageState state="loading" title="正在核查生产前校验条件" />;
  } else if (readiness.isError) {
    content = (
      <PageState
        state="error"
        title="生产前校验读取失败"
        description={getApiErrorMessage(
          readiness.error,
          "请重试；若持续失败，请联系信息科核查知识生产准备服务。失败已留痕，可在审计证据中追溯。",
        )}
        onRetry={() => void readiness.refetch()}
      />
    );
  } else {
    content = (
      <Descriptions bordered column={1} size="small">
        {GATES.map((gate, index) => {
          const item = itemByCode.get(gate.code);
          const ready = Boolean(item?.ready);
          return (
            <Descriptions.Item key={gate.code} label={`${index + 1}. ${gate.label}`}>
              <Space direction="vertical" size={0}>
                <Space>
                  <Tag color={ready ? "success" : "error"}>{ready ? "满足" : "阻断"}</Tag>
                  <Text>{item?.message ?? "服务端尚未返回该项状态"}</Text>
                  {!ready ? (
                    <Button type="link" href={`/knowledge/production?step=${gate.step}`}>
                      前往处理
                    </Button>
                  ) : null}
                </Space>
                <Text type="secondary">
                  责任角色：{gate.owner} · {evidenceSummary(item, evidenceDetailsEnabled)}
                </Text>
              </Space>
            </Descriptions.Item>
          );
        })}
      </Descriptions>
    );
  }

  return (
    <Card title="生产前校验" extra={<EvidenceDetailsToggle securityProfile={security.data} />}>
      <Space direction="vertical" size="middle" className="mk-full-width">
        <Alert
          type={readiness.data?.ready ? "success" : "warning"}
          showIcon
          message={readiness.data?.ready ? "生产前校验已满足" : "正式生产仍有阻断项"}
          description="所有校验项均读取关系库真实状态；历史评测、仅有前端勾选或脚本输出不能代替当前证据。"
        />
        {content}
      </Space>
    </Card>
  );
}
