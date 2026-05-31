import {
  Card,
  Steps,
  Button,
  Space,
  Typography,
  Row,
  Col,
  Statistic,
  Progress,
  Badge,
  message,
} from "antd";
import {
  RocketOutlined,
  CheckCircleOutlined,
  DashboardOutlined,
  AppstoreOutlined,
  CompassOutlined,
  LoadingOutlined,
} from "@ant-design/icons";
import { PageShell } from "@/shared/ui/PageShell";
import { useSuccessPlan, useTransitionSuccessStage } from "@/shared/api/hooks";
import styles from "./Tenant.module.css";

const { Text } = Typography;

function getStepsConfig() {
  return [
    { title: "签合同 · 建立项目", stage: "PREPARATION", subStep: 1, owner: "销售 + 客户成功" },
    { title: "现场调研 · 出方案", stage: "PREPARATION", subStep: 2, owner: "实施 + 信息科" },
    { title: "租户开通 · 建管理员", stage: "PREPARATION", subStep: 3, owner: "实施工程师" },
    { title: "接入适配器 · HIS/EMR", stage: "PILOT", subStep: 4, owner: "实施 + 信息科" },
    { title: "导入字典映射", stage: "PILOT", subStep: 5, owner: "实施 + 临床专家" },
    { title: "导入配置包 · 路径/规则", stage: "PILOT", subStep: 6, owner: "实施 + 医务处" },
    { title: "试点科室培训", stage: "ACCEPTANCE", subStep: 7, owner: "客户成功 + 临床" },
    { title: "试运行与全院推广", stage: "PROMOTION", subStep: 8, owner: "医院全员" },
    { title: "双签验收 · 稳定运行", stage: "RUNNING", subStep: 9, owner: "院长 + 交付代表" },
  ];
}

function splitDisplayList(value?: string) {
  return value
    ? value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean)
    : [];
}

function getStepBadge(status: "finish" | "process" | "wait") {
  if (status === "finish") return { badgeStatus: "success" as const, text: "已完结" };
  if (status === "process") return { badgeStatus: "processing" as const, text: "演进中" };
  return { badgeStatus: "default" as const, text: "未开始" };
}

export default function ImplementationGuide() {
  const { data: plan, isLoading, refetch } = useSuccessPlan();
  const transitionMutation = useTransitionSuccessStage();

  const currentStage = plan?.currentStage ?? "PREPARATION";
  const stepsConfig = getStepsConfig();

  const getStepStatus = (stepStage: string, subStepIndex: number) => {
    const stageOrder = ["PREPARATION", "PILOT", "ACCEPTANCE", "PROMOTION", "RUNNING", "RENEWAL"];
    const currentOrderIndex = stageOrder.indexOf(currentStage);
    const stepOrderIndex = stageOrder.indexOf(stepStage);

    if (currentStage === "RENEWAL") {
      return "finish" as const;
    }

    if (stepOrderIndex < currentOrderIndex) {
      return "finish" as const;
    }

    if (stepOrderIndex > currentOrderIndex) {
      return "wait" as const;
    }

    if (currentStage === "PREPARATION") {
      if (subStepIndex < 3) return "finish" as const;
      return "process" as const;
    }
    if (currentStage === "PILOT") {
      if (subStepIndex < 6) return "finish" as const;
      return "process" as const;
    }

    return "process" as const;
  };

  const getCurrentStepIndex = () => {
    switch (currentStage) {
      case "PREPARATION":
        return 2;
      case "PILOT":
        return 5;
      case "ACCEPTANCE":
        return 6;
      case "PROMOTION":
        return 7;
      case "RUNNING":
        return 8;
      case "RENEWAL":
        return 9;
      default:
        return 0;
    }
  };

  const handleNextStage = async () => {
    let nextStage = "";
    switch (currentStage) {
      case "PREPARATION":
        nextStage = "PILOT";
        break;
      case "PILOT":
        nextStage = "ACCEPTANCE";
        break;
      case "ACCEPTANCE":
        nextStage = "PROMOTION";
        break;
      case "PROMOTION":
        nextStage = "RUNNING";
        break;
      case "RUNNING":
        nextStage = "RENEWAL";
        break;
      default:
        message.success("项目已完成全部生命周期演进，已达最优运行状态！");
        return;
    }

    try {
      await transitionMutation.mutateAsync(nextStage);
      message.success(`生命周期阶段已成功演进至: ${nextStage}`);
      refetch();
    } catch {
      message.error("生命周期演进失败，请检查操作权限。");
    }
  };

  if (isLoading) {
    return (
      <PageShell title="客户实施向导" description="正在加载实施成功计划...">
        <div className={styles.loaderWrap}>
          <LoadingOutlined className={styles.loaderIcon} spin />
          <div className={styles.loaderText}>正在获取实时演进计划...</div>
        </div>
      </PageShell>
    );
  }

  const healthColor =
    (plan?.healthScore ?? 0) >= 80 ? "var(--ant-success-color)" : "var(--ant-warning-color)";
  const activatedModules = splitDisplayList(plan?.activatedModules);
  const activatedPathways = splitDisplayList(plan?.activatedPathways);

  return (
    <PageShell
      title="客户实施向导"
      description="跨部门、跨系统的 9 步交付模型。在此直观推进项目阶段，监控治理健康度与引擎服务的激活快照。"
      primary={
        currentStage !== "RENEWAL" ? (
          <Button
            type="primary"
            icon={<RocketOutlined />}
            onClick={handleNextStage}
            loading={transitionMutation.isPending}
          >
            继续推进下一阶段
          </Button>
        ) : (
          <Button type="primary" disabled icon={<CheckCircleOutlined />} className="mk-btn-success">
            项目已完成双签验收
          </Button>
        )
      }
    >
      <div className={styles.container}>
        {/* 精致驾驶舱指标区 */}
        <Row gutter={[24, 24]}>
          <Col xs={24} sm={12} md={8}>
            <Card hoverable className={styles.statCard}>
              <div className={styles.statFlex}>
                <Statistic
                  title="多维治理健康度评分"
                  value={plan?.healthScore ?? 0}
                  suffix="分"
                  valueStyle={{ color: healthColor, fontWeight: 700 }}
                />
                <Text type="secondary">基于当前生命周期配置自动评估</Text>
              </div>
              <Progress
                type="circle"
                percent={plan?.healthScore ?? 0}
                size={60}
                strokeColor={healthColor}
              />
            </Card>
          </Col>

          <Col xs={24} sm={12} md={8}>
            <Card hoverable className={styles.statCard}>
              <div className={styles.statFlex}>
                <div className={styles.statHeader}>
                  <span className={styles.statTitle}>已激活系统服务模块</span>
                  <AppstoreOutlined className={styles.statIconBlue} />
                </div>
                <div>
                  {activatedModules.length > 0 ? (
                    activatedModules.map((mod) => (
                      <span key={mod} className={styles.badgeModule}>
                        {mod}
                      </span>
                    ))
                  ) : (
                    <Text type="secondary">暂无激活模块</Text>
                  )}
                </div>
              </div>
            </Card>
          </Col>

          <Col xs={24} sm={12} md={8}>
            <Card hoverable className={styles.statCard}>
              <div className={styles.statFlex}>
                <div className={styles.statHeader}>
                  <span className={styles.statTitle}>已导入临床专病包</span>
                  <CompassOutlined className={styles.statIconPurple} />
                </div>
                <div>
                  {activatedPathways.length > 0 ? (
                    activatedPathways.map((path) => (
                      <span key={path} className={styles.badgePathway}>
                        {path}
                      </span>
                    ))
                  ) : (
                    <Text type="secondary">暂无导入专病包</Text>
                  )}
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        {/* 动态步骤卡片 */}
        <Card
          title={
            <Space>
              <DashboardOutlined />
              <span>交付全生命周期步骤推进</span>
            </Space>
          }
        >
          <Steps
            direction="vertical"
            current={getCurrentStepIndex()}
            items={stepsConfig.map((step) => {
              const status = getStepStatus(step.stage, step.subStep);
              const badge = getStepBadge(status);

              return {
                title: step.title,
                status,
                description: (
                  <div className={styles.stepDesc}>
                    <Space size="middle">
                      <Text type="secondary">负责人: {step.owner}</Text>
                      <Badge status={badge.badgeStatus} text={badge.text} />
                    </Space>
                  </div>
                ),
              };
            })}
          />
        </Card>
      </div>
    </PageShell>
  );
}
