import {
  Alert,
  Card,
  Row,
  Col,
  Tag,
  Space,
  Typography,
  Table,
  Statistic,
  Button,
  theme,
} from "antd";
import { ReloadOutlined } from "@ant-design/icons";

import { canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import { useRuntimeOperations, useSecurityProfile } from "@/shared/api/hooks";
import type { RuntimeDependencyStatus, RuntimeFeatureFlag } from "@/shared/api/hooks";
import { customerEnumLabel, riskLabel } from "@/shared/config/customerLabels";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";
import { canUseEvidenceDetails } from "@/shared/ui/evidenceDetailsAccess";
import type { RouteExperience } from "@/shared/ui/experienceTypes";

const STATUS_LABEL: Record<string, string> = {
  UP: "正常",
  DEGRADED: "降级",
  NOT_CONNECTED: "未连接",
  MODEL_DISABLED: "模型未启用",
  DOWN: "异常",
  OUT_OF_SERVICE: "停服",
  UNKNOWN: "未知",
};

const STATUS_COLOR: Record<string, string> = {
  UP: "success",
  DEGRADED: "warning",
  NOT_CONNECTED: "default",
  MODEL_DISABLED: "default",
  DOWN: "error",
  OUT_OF_SERVICE: "error",
  UNKNOWN: "default",
};

const RISK_COLOR: Record<string, string> = {
  LOW: "success",
  MEDIUM: "warning",
  HIGH: "error",
};

const route = findRouteByPath("/system/providers");

if (!route?.experience) {
  throw new Error("运行保障页面缺少体验声明");
}

const PAGE_META: { title: string; experience: RouteExperience } = {
  title: route.title,
  experience: route.experience,
};

function dependencySummary(dependency: RuntimeDependencyStatus) {
  if (dependency.key === "database") {
    return dependency.status === "UP" ? "核心数据服务可用" : "核心数据服务需要检查";
  }
  if (dependency.key === "graph-projection") {
    return dependency.status === "UP" ? "图谱辅助能力可用" : "图谱辅助能力未连接";
  }
  if (dependency.key === "dify-workflow") {
    return dependency.status === "UP" ? "智能工作流可用" : "智能工作流未启用";
  }
  return dependency.detail;
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return "未提供";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

function backupReadinessLabel(backup: { enabled: boolean; drillEvidence: { status: string } }) {
  if (!backup.enabled) {
    return "未配置";
  }
  return backup.drillEvidence.status === "SUCCESS" ? "已验证" : "待演练";
}

export default function SystemProviders() {
  const security = useSecurityProfile();
  const routeAllowed = Boolean(security.data && canAccessRoute(route, security.data));
  const runtime = useRuntimeOperations(routeAllowed);
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const evidenceDetailsEnabled = canUseEvidenceDetails(security.data) && globalEvidenceDetails;
  const { token } = theme.useToken();

  if (security.isLoading) {
    return (
      <PageShell title={PAGE_META.title} description="正在核对运行保障权限">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (security.isError) {
    return (
      <PageShell title={PAGE_META.title} description="权限信息读取失败">
        <PageState state="error" onRetry={() => void security.refetch()} />
      </PageShell>
    );
  }

  if (!routeAllowed) {
    return (
      <PageShell title={PAGE_META.title} description="运行保障包含受控运维信息">
        <PageState state="forbidden" />
      </PageShell>
    );
  }

  if (runtime.isLoading) {
    return (
      <PageShell title={PAGE_META.title} description="正在读取运行保障信息">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (runtime.isError) {
    return (
      <PageShell title={PAGE_META.title} description="运行保障信息读取失败">
        <PageState
          state="error"
          title="暂时无法读取运行保障信息"
          description="请稍后重试，或让信息科检查系统运行服务。"
          action={
            <Button icon={<ReloadOutlined />} onClick={() => runtime.refetch()}>
              重试
            </Button>
          }
        />
      </PageShell>
    );
  }

  const data = runtime.data;
  if (!data) {
    return (
      <PageShell title={PAGE_META.title} description="暂无运行保障信息">
        <PageState state="empty" />
      </PageShell>
    );
  }

  const dependencies = [...data.dependencies].sort(
    (left, right) => Number(left.status === "UP") - Number(right.status === "UP"),
  );
  const dependencyIssueCount = dependencies.filter(
    (dependency) => dependency.status !== "UP",
  ).length;

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      extras={
        <Button
          aria-label="重新探测"
          icon={<ReloadOutlined />}
          loading={runtime.isFetching}
          onClick={() => void runtime.refetch()}
        >
          重新探测
        </Button>
      }
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <Alert
          type={dependencyIssueCount > 0 ? "warning" : "success"}
          showIcon
          message={
            dependencyIssueCount > 0 ? `${dependencyIssueCount} 项依赖需关注` : "全部依赖状态正常"
          }
          description={
            dependencyIssueCount > 0
              ? "未连接或未启用的能力不会伪装为正常；核心业务继续走本地确定性主链路。"
              : "核心服务与已配置依赖均通过当前探测。"
          }
        />
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={8}>
            <Card>
              <Statistic
                title="核心服务"
                value={STATUS_LABEL[data.healthStatus] ?? customerEnumLabel(data.healthStatus)}
                valueStyle={{
                  color: data.healthStatus === "UP" ? token.colorSuccess : token.colorWarning,
                }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8}>
            <Card>
              <Statistic
                title="依赖服务"
                value={data.dependencies.filter((item) => item.status === "UP").length}
                suffix={`/ ${data.dependencies.length}`}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8}>
            <Card>
              <Statistic title="备份就绪" value={backupReadinessLabel(data.backup)} />
            </Card>
          </Col>
        </Row>

        <Card title="依赖健康" data-testid="runtime-dependencies">
          <Table<RuntimeDependencyStatus>
            rowKey="key"
            dataSource={dependencies}
            pagination={false}
            scroll={{ x: "max-content" }}
            columns={[
              { title: "依赖", dataIndex: "displayName" },
              {
                title: "状态",
                dataIndex: "status",
                render: (status) => (
                  <Tag color={STATUS_COLOR[status] ?? "default"}>
                    {STATUS_LABEL[status] ?? customerEnumLabel(status)}
                  </Tag>
                ),
              },
              {
                title: "说明",
                key: "summary",
                render: (_, dependency) =>
                  evidenceDetailsEnabled ? dependency.detail : dependencySummary(dependency),
              },
            ]}
          />
        </Card>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title="备份恢复就绪">
              <Space direction="vertical" size="small" className="mk-full-width">
                <Typography.Text>RPO：{data.backup.rpo}</Typography.Text>
                <Typography.Text>RTO：{data.backup.rto}</Typography.Text>
                <Typography.Text>{data.backup.checksumPolicy}</Typography.Text>
                {data.backup.drillEvidence.status === "SUCCESS" ? (
                  <>
                    <Tag color="success">演练通过</Tag>
                    <Typography.Text>
                      最近演练：{formatDateTime(data.backup.drillEvidence.completedAt)}
                    </Typography.Text>
                    <Typography.Text>
                      迁移校验：{data.backup.drillEvidence.migrationCount} 条
                    </Typography.Text>
                  </>
                ) : (
                  <Typography.Text type="warning">
                    {data.backup.drillEvidence.detail}
                  </Typography.Text>
                )}
                {data.backup.warning ? (
                  <Typography.Text type="warning">{data.backup.warning}</Typography.Text>
                ) : null}
              </Space>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="国产化 profile">
              <Space direction="vertical" size="small" className="mk-full-width">
                <Typography.Text>目标操作系统：{data.domesticProfile.targetOs}</Typography.Text>
                <Typography.Text>目标 JDK：{data.domesticProfile.targetJdk}</Typography.Text>
                <Space wrap>
                  {data.domesticProfile.databaseVendors.map((vendor) => (
                    <Tag key={vendor}>{vendor}</Tag>
                  ))}
                  {data.domesticProfile.cryptoAlgorithms.map((algorithm) => (
                    <Tag key={algorithm} color="blue">
                      {algorithm}
                    </Tag>
                  ))}
                </Space>
                <Typography.Text type="secondary">{data.domesticProfile.evidence}</Typography.Text>
              </Space>
            </Card>
          </Col>
        </Row>

        {evidenceDetailsEnabled ? (
          <>
            <Alert
              type={data.healthStatus === "UP" ? "success" : "warning"}
              showIcon
              message={`当前 profile：${data.activeProfiles.join(" / ") || "default"}`}
              description={
                <Space size="small" wrap>
                  <Typography.Text>迁移路径：</Typography.Text>
                  <Typography.Text code>{data.migrationLocation}</Typography.Text>
                </Space>
              }
            />
            <Row gutter={[16, 16]}>
              <Col xs={24} sm={12}>
                <Card>
                  <Statistic title="部署模式" value={data.deploymentMode} />
                </Card>
              </Col>
              <Col xs={24} sm={12}>
                <Card>
                  <Statistic title="数据库方言" value={data.databaseDialect} />
                </Card>
              </Col>
            </Row>
            <Card title="功能开关诊断">
              <Table<RuntimeFeatureFlag>
                rowKey="key"
                dataSource={data.featureFlags}
                pagination={false}
                scroll={{ x: "max-content" }}
                columns={[
                  { title: "能力", dataIndex: "displayName" },
                  {
                    title: "状态",
                    dataIndex: "enabled",
                    render: (enabled) => (
                      <Tag color={enabled ? "success" : "default"}>{enabled ? "开启" : "关闭"}</Tag>
                    ),
                  },
                  {
                    title: "风险",
                    dataIndex: "risk",
                    render: (risk) => (
                      <Tag color={RISK_COLOR[risk] ?? "default"}>{riskLabel(risk)}</Tag>
                    ),
                  },
                  {
                    title: "配置来源",
                    dataIndex: "source",
                    render: (source, record) => (
                      <Space direction="vertical" size={2}>
                        <Tag color={source === "SAFE_DEFAULT" ? "warning" : "processing"}>
                          {source ?? "CONFIG_CENTER"}
                        </Tag>
                        {record.warning ? (
                          <Typography.Text type="warning">{record.warning}</Typography.Text>
                        ) : null}
                      </Space>
                    ),
                  },
                  { title: "负责人", dataIndex: "owner" },
                  { title: "说明", dataIndex: "description" },
                ]}
              />
            </Card>
            <Card title="备份恢复诊断">
              <Space direction="vertical" size="small" className="mk-full-width">
                <Space size="small" wrap>
                  <Typography.Text>备份脚本：</Typography.Text>
                  <Typography.Text code>{data.backup.backupScript}</Typography.Text>
                </Space>
                <Space size="small" wrap>
                  <Typography.Text>恢复脚本：</Typography.Text>
                  <Typography.Text code>{data.backup.restoreScript}</Typography.Text>
                </Space>
                {data.backup.source ? (
                  <Space size="small" wrap>
                    <Typography.Text>配置来源：</Typography.Text>
                    <Typography.Text code>{data.backup.source}</Typography.Text>
                  </Space>
                ) : null}
                {data.backup.drillEvidence.evidenceReference ? (
                  <Space size="small" wrap>
                    <Typography.Text>演练证据：</Typography.Text>
                    <Typography.Text code>
                      {data.backup.drillEvidence.evidenceReference}
                    </Typography.Text>
                  </Space>
                ) : null}
              </Space>
            </Card>
          </>
        ) : null}
      </Space>
    </PageExperienceShell>
  );
}
