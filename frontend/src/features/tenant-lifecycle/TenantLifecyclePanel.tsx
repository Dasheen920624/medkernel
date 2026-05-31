import {
  Card,
  Space,
  Typography,
  Steps,
  Row,
  Col,
  Progress,
  Tag,
  Button,
  Spin,
  Alert,
  message,
  Divider,
} from "antd";
import {
  ArrowRightOutlined,
  MedicineBoxOutlined,
  AppstoreOutlined,
  ClockCircleOutlined,
  UserOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import { useSuccessPlan, useTransitionSuccessStage } from "@/shared/api/hooks";
import { tenantLifecycleStages } from "@/shared/config/tenantLifecycleStages";
import styles from "./TenantLifecyclePanel.module.css";

const { Text, Paragraph } = Typography;

const getThemeStyle = (color: string) => ({
  color,
});

export function TenantLifecyclePanel() {
  const { data, isLoading, error, refetch } = useSuccessPlan();
  const transitionMutation = useTransitionSuccessStage();

  if (isLoading) {
    return (
      <Card
        title={<Text className={styles.title}>租户多维生命周期控制台</Text>}
        className={styles.card}
      >
        <div className={styles.loaderContainer}>
          <Spin size="large" />
          <Text type="secondary" className={styles.loaderText}>
            正在物理加载多维生命周期数据...
          </Text>
        </div>
      </Card>
    );
  }

  if (error || !data) {
    return (
      <Card
        title={<Text className={styles.title}>租户多维生命周期控制台</Text>}
        className={styles.card}
      >
        <Alert
          message="数据加载失败"
          description={
            error instanceof Error ? error.message : "无法与底层租户生命周期服务建立物理连接"
          }
          type="error"
          showIcon
        />
      </Card>
    );
  }

  const currentStep = tenantLifecycleStages.findIndex((s) => s.key === data.currentStage);
  const currentStepSafe = currentStep !== -1 ? currentStep : 0;

  const hasNext = currentStepSafe < tenantLifecycleStages.length - 1;
  const nextStage = hasNext ? tenantLifecycleStages[currentStepSafe + 1] : null;

  const handleTransition = () => {
    if (!nextStage) return;
    transitionMutation.mutate(nextStage.key, {
      onSuccess: () => {
        message.success(`生命周期物理演进成功：已成功推进至【${nextStage.title}】`);
        refetch();
      },
      onError: (err: unknown) => {
        const axiosError = err as { message?: string; response?: { data?: { message?: string } } };
        const errMsg =
          axiosError.response?.data?.message || axiosError.message || "请求物理变迁失败";
        message.error(`推进生命周期失败: ${errMsg}`);
      },
    });
  };

  // 根据健康度得分获取颜色与状态描述
  const getHealthMeta = (score: number) => {
    if (score >= 80) return { color: "var(--ant-color-success)", status: "稳健" };
    if (score >= 60) return { color: "var(--ant-color-warning)", status: "预警" };
    return { color: "var(--ant-color-error)", status: "风险" };
  };

  const healthMeta = getHealthMeta(data.healthScore);

  const activeModules = data.activatedModules
    ? data.activatedModules.split(",").filter(Boolean)
    : [];
  const activePathways = data.activatedPathways
    ? data.activatedPathways.split(",").filter(Boolean)
    : [];

  return (
    <Card
      title={
        <Space direction="vertical" size={2}>
          <Text className={styles.title}>租户多维生命周期控制台</Text>
          <Text className={styles.subtitle}>
            与 docs/CONSTITUTION.md §6 多维并行生命周期物理驱动对齐
          </Text>
        </Space>
      }
      extra={
        <Tag color="cyan" className={styles.stageTag}>
          当前阶段：{tenantLifecycleStages[currentStepSafe]?.title}
        </Tag>
      }
      className={styles.card}
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        {/* 步骤图谱展示 */}
        <div className={styles.stepsContainer}>
          <Steps
            current={currentStepSafe}
            size="small"
            labelPlacement="vertical"
            items={tenantLifecycleStages.map((s, index) => {
              let status: "process" | "finish" | "wait" = "wait";
              if (index === currentStepSafe) {
                status = "process";
              } else if (index < currentStepSafe) {
                status = "finish";
              }
              return {
                title: s.title,
                description: <span className={styles.stepDesc}>{s.description}</span>,
                status,
              };
            })}
          />
        </div>

        {/* 物理切片多维数据 */}
        <Row gutter={[24, 24]} align="stretch">
          {/* 健康度得分切片 */}
          <Col xs={24} md={8}>
            <Card
              size="small"
              title={
                <span className={styles.cardIconTitle}>
                  <SafetyCertificateOutlined /> 租户健康度得分
                </span>
              }
              className={styles.card}
            >
              <div className={styles.progressContainer}>
                <Progress
                  type="circle"
                  percent={data.healthScore}
                  strokeColor={{
                    "0%": healthMeta.color,
                    "100%": "var(--ant-color-primary)",
                  }}
                  size={100}
                  format={(percent) => (
                    <div className={styles.progressFormat}>
                      <span className={styles.progressScore}>{percent}</span>
                      <span
                        style={getThemeStyle(healthMeta.color)}
                        className={styles.progressStatus}
                      >
                        {healthMeta.status}
                      </span>
                    </div>
                  )}
                />
                <Text className={styles.progressDesc}>全量集成证据链及模型成效得出</Text>
              </div>
            </Card>
          </Col>

          {/* 激活的服务模块 */}
          <Col xs={24} md={8}>
            <Card
              size="small"
              title={
                <span className={styles.cardIconTitle}>
                  <AppstoreOutlined /> 已激活服务模块
                </span>
              }
              className={styles.card}
            >
              <div className={styles.cardBody}>
                {activeModules.length > 0 ? (
                  <Space size={[4, 8]} wrap>
                    {activeModules.map((m) => (
                      <Tag color="geekblue" key={m} className={styles.tagGeekblue}>
                        {m}
                      </Tag>
                    ))}
                  </Space>
                ) : (
                  <Text type="secondary" italic>
                    无已激活服务模块
                  </Text>
                )}
                <Paragraph className={styles.cardDesc}>
                  由底座统一认证中心对当前租户注入的安全服务许可范围。
                </Paragraph>
              </div>
            </Card>
          </Col>

          {/* 激活的临床专病包 */}
          <Col xs={24} md={8}>
            <Card
              size="small"
              title={
                <span className={styles.cardIconTitle}>
                  <MedicineBoxOutlined /> 已配置临床专病包
                </span>
              }
              className={styles.card}
            >
              <div className={styles.cardBody}>
                {activePathways.length > 0 ? (
                  <Space size={[4, 8]} wrap>
                    {activePathways.map((p) => (
                      <Tag color="magenta" key={p} className={styles.tagMagenta}>
                        {p}
                      </Tag>
                    ))}
                  </Space>
                ) : (
                  <Text type="secondary" italic>
                    无已配置专病包
                  </Text>
                )}
                <Paragraph className={styles.cardDesc}>
                  当前租户已加载生效的临床路径与医疗规范知识库集合。
                </Paragraph>
              </div>
            </Card>
          </Col>
        </Row>

        {/* 下一阶段演进操作与审计物理留痕 */}
        <Divider className={styles.divider} />

        <Row align="middle" justify="space-between">
          <Col>
            <Space size="middle">
              {data.updatedAt && (
                <span className={styles.auditText}>
                  <ClockCircleOutlined /> 物理更新时间：{new Date(data.updatedAt).toLocaleString()}
                </span>
              )}
              {data.updatedBy && (
                <span className={styles.auditText}>
                  <UserOutlined /> 操作人：{data.updatedBy}
                </span>
              )}
            </Space>
          </Col>
          <Col>
            {hasNext && nextStage ? (
              <Button
                type="primary"
                icon={<ArrowRightOutlined />}
                loading={transitionMutation.isPending}
                onClick={handleTransition}
                className={styles.transitionBtn}
              >
                推进生命阶段至【{nextStage.title}】
              </Button>
            ) : (
              <Button disabled className={styles.transitionBtn}>
                已达生命周期终点【年度续约】
              </Button>
            )}
          </Col>
        </Row>
      </Space>
    </Card>
  );
}
