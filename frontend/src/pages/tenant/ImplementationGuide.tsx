import {
  Alert,
  Button,
  Card,
  Col,
  List,
  Progress,
  Row,
  Space,
  Statistic,
  Steps,
  Tag,
  Typography,
} from "antd";
import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import { Link } from "react-router-dom";

import { useImplementationSteps, type ImplementationStep } from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { StepFlow } from "@/shared/ui/StepFlow";
import type { StepKey } from "@/shared/ui/StepFlow.contract";
import styles from "./Tenant.module.css";

const { Text, Title } = Typography;

const targetLabelByPath: Record<string, string> = {
  "/tenant/onboarding": "机构实施配置",
  "/adapter/hub": "系统接入",
  "/config/releases": "运行发布",
  "/terminology/mapping": "术语与字典",
};

function targetLabel(path: string) {
  return targetLabelByPath[path] ?? "对应配置页";
}

function statusLabel(step: ImplementationStep) {
  return step.status === "DONE" ? "已就绪" : "阻塞";
}

function statusTag(step: ImplementationStep) {
  if (step.status === "DONE") {
    return (
      <Tag icon={<CheckCircleOutlined />} color="success">
        已就绪
      </Tag>
    );
  }
  return (
    <Tag icon={<ExclamationCircleOutlined />} color="warning">
      阻塞
    </Tag>
  );
}

function flowStepFor(steps: ImplementationStep[]): StepKey {
  if (steps.some((step) => step.status === "BLOCKED")) {
    return "auto_validate";
  }
  return "canary_release";
}

function firstBlockedStep(steps: ImplementationStep[]) {
  return steps.find((step) => step.status === "BLOCKED") ?? null;
}

export default function ImplementationGuide() {
  const { data: steps = [], isLoading, isError, refetch } = useImplementationSteps();

  if (isLoading) {
    return (
      <PageShell
        title="实施与验收"
        description="读取交付准备真实步骤"
        state="loading"
        stateProps={{
          title: "正在加载实施步骤",
          description: "正在读取当前组织范围内的组织、用户、权限、适配器、资产与灰度就绪状态。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (isError) {
    return (
      <PageShell
        title="实施与验收"
        description="请重试或联系信息科"
        state="error"
        stateProps={{
          title: "实施步骤读取失败",
          description: "请重试；若持续失败，请带追踪标识联系信息科排查服务空间接口。",
          onRetry: () => refetch(),
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (steps.length === 0) {
    return (
      <PageShell
        title="实施与验收"
        description="等待服务机构实施服务返回步骤"
        state="empty"
        stateProps={{
          title: "暂无实施步骤",
          description: "当前服务机构尚未返回实施步骤，请先确认服务空间与组织范围已建立。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  const doneCount = steps.filter((step) => step.status === "DONE").length;
  const blockedCount = steps.length - doneCount;
  const progress = Math.round((doneCount / steps.length) * 100);
  const blockedStep = firstBlockedStep(steps);
  const currentStepIndex = Math.min(
    blockedStep ? steps.findIndex((step) => step.key === blockedStep.key) : steps.length - 1,
    steps.length - 1,
  );
  const primaryTarget = blockedStep ?? steps[steps.length - 1];

  return (
    <PageShell
      title="实施与验收"
      description="按真实就绪状态推进交付准备"
      primary={
        primaryTarget ? (
          <Link to={primaryTarget.targetPath}>
            <Button type="primary" icon={<ArrowRightOutlined />}>
              {blockedStep ? "继续处理阻塞项" : "查看发布准备"}
            </Button>
          </Link>
        ) : undefined
      }
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Card className={styles.readinessSummaryCard}>
              <Statistic title="已就绪步骤" value={doneCount} suffix={`/ ${steps.length}`} />
              <Progress
                percent={progress}
                size="small"
                status={blockedCount > 0 ? "active" : "success"}
              />
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card className={styles.readinessSummaryCard}>
              <Statistic title="阻塞项" value={blockedCount} />
              <Text type={blockedCount > 0 ? "warning" : "secondary"}>
                {blockedStep ? `下一项：${blockedStep.title}` : "当前步骤均已就绪"}
              </Text>
            </Card>
          </Col>
          <Col xs={24} md={8}>
            <Card className={styles.readinessSummaryCard}>
              <Statistic title="下一配置页" value={targetLabel(primaryTarget.targetPath)} />
              <Text type="secondary">所有跳转均来自实施服务返回的目标页面</Text>
            </Card>
          </Col>
        </Row>

        {blockedCount > 0 ? (
          <Alert
            type="warning"
            showIcon
            message="部分步骤未就绪"
            description="阻塞项会保留在本页并指向对应配置页；未完成前不得把交付准备标记为完成。"
          />
        ) : (
          <Alert
            type="success"
            showIcon
            message="交付准备步骤均已就绪"
            description="可以进入灰度发布与验收留证，但仍需按 7 步流完成审核、灰度、全量和回滚证据。"
          />
        )}

        <Card title="实施步骤真实状态">
          <Steps
            direction="vertical"
            current={currentStepIndex}
            status={blockedCount > 0 ? "error" : "finish"}
            items={steps.map((step) => ({
              title: step.title,
              status: step.status === "DONE" ? ("finish" as const) : ("error" as const),
              description: statusLabel(step),
            }))}
          />
        </Card>

        <div className={styles.readinessGrid}>
          {steps.map((step) => (
            <Card
              key={step.key}
              data-testid={`implementation-step-${step.key}`}
              className={styles.readinessStepCard}
            >
              <Space direction="vertical" size="middle" className="mk-full-width">
                <div className={styles.stepTitleRow}>
                  <Space size="small">
                    <SafetyCertificateOutlined />
                    <Title level={5} className={styles.stepTitle}>
                      {step.title}
                    </Title>
                  </Space>
                  {statusTag(step)}
                </div>

                {step.evidence ? (
                  <Text className={styles.stepEvidence}>{step.evidence}</Text>
                ) : (
                  <List
                    size="small"
                    split={false}
                    dataSource={step.blockers}
                    locale={{ emptyText: "后端未返回阻塞原因" }}
                    renderItem={(blocker) => (
                      <List.Item className={styles.blockerItem}>
                        <Text type="warning">{blocker}</Text>
                      </List.Item>
                    )}
                  />
                )}

                <Link to={step.targetPath} className={styles.stepActionLink}>
                  前往{targetLabel(step.targetPath)}
                </Link>
              </Space>
            </Card>
          ))}
        </div>

        <StepFlow
          currentStep={flowStepFor(steps)}
          status={blockedCount > 0 ? "error" : "process"}
          panelByStep={{
            auto_validate: (
              <Text>
                已读取 {steps.length} 个实施就绪步骤，{blockedCount}{" "}
                个仍需处理；请先关闭阻塞项，再进入灰度。
              </Text>
            ),
            canary_release: <Text>就绪检查已完成，下一步按默认 10% 灰度发布并留存证据。</Text>,
          }}
        />
      </Space>
    </PageShell>
  );
}
