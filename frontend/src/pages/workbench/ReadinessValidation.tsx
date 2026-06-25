import { Alert, Button, Card, Checkbox, Space, Statistic, Table, Tag, Typography } from "antd";
import { ArrowRightOutlined, ReloadOutlined } from "@ant-design/icons";
import { useState, type HTMLAttributes } from "react";
import { useNavigate } from "react-router-dom";

import { parseApiError } from "@/shared/api/errors";
import {
  useKnowledgeProductionReadiness,
  useRuntimeOperations,
  useSecurityProfile,
  type KnowledgeProductionReadinessItem,
  type RuntimeDependencyStatus,
  type RuntimeFeatureFlag,
  type RuntimeOperationsSnapshot,
} from "@/shared/api/hooks";
import { canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { PageShell } from "@/shared/ui/PageShell";
import { WorkbenchTabs } from "@/widgets/WorkbenchTabs";

type ReadinessStatus = "blocked" | "ready" | "disabled";

type SelfCheckItem = {
  key: string;
  item: string;
  source: string;
  status: ReadinessStatus;
  reason: string;
  repairPath: string;
  partial: boolean;
};

const { Text } = Typography;

const STATUS_LABEL: Record<ReadinessStatus, string> = {
  blocked: "阻塞",
  ready: "通过",
  disabled: "未启用",
};

const STATUS_COLOR: Record<ReadinessStatus, string> = {
  blocked: "error",
  ready: "success",
  disabled: "default",
};

const STATUS_FILTERS: ReadinessStatus[] = ["blocked", "ready", "disabled"];
const READINESS_ROUTE = findRouteByPath("/workbench/readiness-validation");

const KNOWLEDGE_READINESS_LABEL: Record<string, string> = {
  LITERATURE_ROOT: "文献资料库地址",
  DEPLOYMENT_FORM: "部署形态",
  MODEL_PROVIDER: "模型服务",
  REGRESSION_BASELINE: "医学验证用例",
  MODEL_EVALUATION: "医学验证评测",
  EGRESS_GOVERNANCE: "外调允许范围",
  MODEL_POLICY: "能力策略",
  VERSION_TRIPLE: "提示词、工具与模型版本",
};

const KNOWLEDGE_READINESS_REPAIR_PATH: Record<string, string> = {
  LITERATURE_ROOT: "/security/baseline",
  DEPLOYMENT_FORM: "/security/baseline",
  MODEL_PROVIDER: "/system/providers",
  REGRESSION_BASELINE: "/advanced/ai-workflows",
  MODEL_EVALUATION: "/advanced/ai-workflows",
  EGRESS_GOVERNANCE: "/advanced/ai-workflows",
  MODEL_POLICY: "/advanced/ai-workflows",
  VERSION_TRIPLE: "/advanced/ai-workflows",
};

export default function ReadinessValidation() {
  const navigate = useNavigate();
  const [activeStatuses, setActiveStatuses] = useState<ReadinessStatus[]>(STATUS_FILTERS);
  const security = useSecurityProfile();
  const profile = security.data;
  const permissionCodes = new Set(profile?.permissions?.map((permission) => permission.code) ?? []);
  const canQueryRuntime = Boolean(profile && canAccessRoute(READINESS_ROUTE, profile));
  const canQueryKnowledgeReadiness = canQueryRuntime && permissionCodes.has("knowledge.read");
  const runtime = useRuntimeOperations(canQueryRuntime);
  const knowledgeReadiness = useKnowledgeProductionReadiness(
    {
      producer: "API_MODEL",
      capabilityCode: "rule.draft",
    },
    canQueryKnowledgeReadiness,
  );
  const retryButton = canQueryRuntime ? (
    <Button
      type="primary"
      aria-label="重新自检"
      icon={<ReloadOutlined />}
      onClick={() => {
        void runtime.refetch();
        if (canQueryKnowledgeReadiness) {
          void knowledgeReadiness.refetch();
        }
      }}
    >
      重新自检
    </Button>
  ) : undefined;

  if (security.isLoading) {
    return (
      <PageShell title="验收自检" description="正在确认当前角色" state="loading">
        <></>
      </PageShell>
    );
  }

  if (security.isError) {
    const parsed = parseApiError(security.error, "暂时无法核验当前角色");
    return (
      <PageShell
        title="验收自检"
        description="角色核验失败"
        state="error"
        stateProps={{
          title: "暂时无法核验权限",
          description: parsed.message,
          traceId: parsed.traceId,
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (!profile || !canQueryRuntime) {
    return (
      <PageShell
        title="验收自检"
        description="当前角色不可访问此页面"
        state="forbidden"
        stateProps={{
          title: "当前权限不足",
          description: "当前账号缺少验收自检页面所需权限。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  if (runtime.isLoading || (canQueryKnowledgeReadiness && knowledgeReadiness.isLoading)) {
    return (
      <PageShell
        title="验收自检"
        description="正在读取运行环境与知识生产检查"
        primary={retryButton}
        state="loading"
      >
        <></>
      </PageShell>
    );
  }

  if (runtime.isError) {
    const parsed = parseApiError(runtime.error, "暂时无法读取验收自检");
    return (
      <PageShell
        title="验收自检"
        description="验收自检失败"
        primary={retryButton}
        state="error"
        stateProps={{
          title: "暂时无法读取验收自检",
          description: parsed.message,
          traceId: parsed.traceId,
        }}
      >
        <></>
      </PageShell>
    );
  }

  const snapshot = runtime.data;
  const items = snapshot
    ? buildSelfCheckItems(
        snapshot,
        canQueryKnowledgeReadiness ? (knowledgeReadiness.data?.items ?? []) : [],
      )
    : [];
  if (!canQueryKnowledgeReadiness) {
    items.push(buildKnowledgeReadinessUnauthorizedItem());
  } else if (knowledgeReadiness.isError) {
    items.push(buildKnowledgeReadinessUnavailableItem(knowledgeReadiness.error));
  }
  const filteredItems = items.filter((item) => activeStatuses.includes(item.status));
  const summary = summarize(items);
  const partialItems = items.filter((item) => item.partial);

  if (items.length === 0) {
    return (
      <PageShell
        title="验收自检"
        description="运行环境暂无自检项"
        primary={retryButton}
        state="empty"
        stateProps={{
          title: "暂无验收自检项",
          description: "当前运行环境未返回依赖、能力开关或备份恢复状态。",
        }}
      >
        <></>
      </PageShell>
    );
  }

  return (
    <PageShell title="验收自检" description="验收前确认阻塞与去处" primary={retryButton}>
      <Space direction="vertical" size="large" className="mk-full-width">
        <WorkbenchTabs />
        <Alert
          showIcon
          type={summary.blocked > 0 ? "warning" : "success"}
          message={summary.conclusion}
          description={`${summary.ready} 通过 / ${summary.blocked} 阻塞 / ${summary.disabled} 未启用`}
        />
        {partialItems.length > 0 ? (
          <Alert
            showIcon
            type="warning"
            message="部分状态未采集"
            description={
              <Space direction="vertical" size={2}>
                {partialItems.map((item) => (
                  <Text key={item.key} type="secondary">
                    {item.item}未采集，请查看下方原因。
                  </Text>
                ))}
              </Space>
            }
          />
        ) : null}
        <Space wrap>
          <Card>
            <Statistic title="就绪" value={summary.ready} />
          </Card>
          <Card>
            <Statistic title="阻塞" value={summary.blocked} />
          </Card>
          <Card>
            <Statistic title="未启用" value={summary.disabled} />
          </Card>
        </Space>
        <Card title="状态筛选" data-testid="readiness-validation-default-filters">
          <Checkbox.Group
            value={activeStatuses}
            onChange={(values) => setActiveStatuses(values as ReadinessStatus[])}
          >
            <Space wrap>
              {STATUS_FILTERS.map((status) => (
                <Checkbox
                  key={status}
                  value={status}
                  data-testid={`readiness-validation-filter-${status}`}
                >
                  {STATUS_LABEL[status]}
                </Checkbox>
              ))}
            </Space>
          </Checkbox.Group>
        </Card>
        <Card title="就绪度自检项">
          <Table<SelfCheckItem>
            rowKey="key"
            dataSource={filteredItems}
            pagination={false}
            onRow={(record) =>
              ({
                "data-testid": `readiness-validation-item-${record.key}`,
              }) as HTMLAttributes<HTMLElement>
            }
            columns={[
              { title: "自检项", dataIndex: "item" },
              { title: "来源", dataIndex: "source" },
              {
                title: "状态",
                dataIndex: "status",
                render: (status: ReadinessStatus) => (
                  <Tag color={STATUS_COLOR[status]}>{STATUS_LABEL[status]}</Tag>
                ),
              },
              { title: "原因", dataIndex: "reason" },
              {
                title: "去处",
                render: (_, record) => (
                  <Button
                    aria-label="去修复"
                    icon={<ArrowRightOutlined />}
                    onClick={() => navigate(record.repairPath)}
                  >
                    去修复
                  </Button>
                ),
              },
            ]}
          />
        </Card>
      </Space>
    </PageShell>
  );
}

function buildSelfCheckItems(
  snapshot: RuntimeOperationsSnapshot,
  knowledgeReadinessItems: KnowledgeProductionReadinessItem[],
): SelfCheckItem[] {
  return [
    buildRuntimeHealthItem(snapshot),
    ...snapshot.dependencies.map(buildDependencyItem),
    ...snapshot.featureFlags.map(buildFeatureFlagItem),
    buildBackupItem(snapshot),
    ...knowledgeReadinessItems.map(buildKnowledgeReadinessItem),
  ];
}

function buildRuntimeHealthItem(snapshot: RuntimeOperationsSnapshot): SelfCheckItem {
  const status = statusToReadiness(snapshot.healthStatus);
  return {
    key: "runtime-health",
    item: "运行环境",
    source: "运行保障",
    status: status.status,
    reason:
      status.status === "ready"
        ? `${snapshot.serviceName} 当前整体健康`
        : `整体健康状态为 ${snapshot.healthStatus}`,
    repairPath: "/system/providers",
    partial: status.partial,
  };
}

function buildKnowledgeReadinessItem(item: KnowledgeProductionReadinessItem): SelfCheckItem {
  const code = item.code;
  return {
    key: `knowledge-${code}`,
    item: KNOWLEDGE_READINESS_LABEL[code] ?? code,
    source: "知识生产上线准备",
    status: knowledgeReadinessStatus(item),
    reason: item.evidence ? `${item.message}（${item.evidence}）` : item.message,
    repairPath: KNOWLEDGE_READINESS_REPAIR_PATH[code] ?? "/security/baseline",
    partial: false,
  };
}

function knowledgeReadinessStatus(item: KnowledgeProductionReadinessItem): SelfCheckItem["status"] {
  if (item.ready) {
    return "ready";
  }
  return item.required ? "blocked" : "disabled";
}

function buildKnowledgeReadinessUnavailableItem(error: unknown): SelfCheckItem {
  const parsed = parseApiError(error, "知识生产上线准备暂时无法读取");
  return {
    key: "knowledge-readiness",
    item: "知识生产上线准备",
    source: "知识生产上线准备",
    status: "blocked",
    reason: parsed.traceId ? `${parsed.message}（追踪号：${parsed.traceId}）` : parsed.message,
    repairPath: "/security/baseline",
    partial: true,
  };
}

function buildKnowledgeReadinessUnauthorizedItem(): SelfCheckItem {
  return {
    key: "knowledge-readiness-unauthorized",
    item: "知识生产上线准备",
    source: "知识生产上线准备",
    status: "blocked",
    reason:
      "当前角色缺少知识生产读取权限，未采集模型知识生产 readiness；请由医疗引擎运营员或具备 knowledge.read 的角色核查。",
    repairPath: "/knowledge/production",
    partial: true,
  };
}

function buildDependencyItem(dependency: RuntimeDependencyStatus): SelfCheckItem {
  const status = statusToReadiness(dependency.status);
  return {
    key: dependency.key,
    item: dependency.displayName,
    source: "依赖健康",
    status: status.status,
    reason: reasonForDependency(dependency, status.status),
    repairPath: repairPathFor(dependency.key),
    partial: status.partial,
  };
}

function buildFeatureFlagItem(flag: RuntimeFeatureFlag): SelfCheckItem {
  return {
    key: `flag-${flag.key}`,
    item: flag.displayName,
    source: "能力开关",
    status: flag.enabled ? "ready" : "disabled",
    reason: flag.enabled
      ? `${flag.displayName}已开启`
      : `${flag.displayName}未启用：${flag.warning ?? flag.description}`,
    repairPath: repairPathFor(flag.key),
    partial: false,
  };
}

function buildBackupItem(snapshot: RuntimeOperationsSnapshot): SelfCheckItem {
  const enabled = snapshot.backup.enabled;
  const drillPassed = snapshot.backup.drillEvidence.status === "SUCCESS";
  let status: ReadinessStatus = "disabled";
  let reason = "备份恢复未启用，不计入验收项";

  if (enabled) {
    status = drillPassed ? "ready" : "blocked";
    reason = drillPassed
      ? `隔离恢复演练通过；RPO ${snapshot.backup.rpo} / RTO ${snapshot.backup.rto}`
      : snapshot.backup.drillEvidence.detail;
  }

  return {
    key: "backup-readiness",
    item: "备份恢复",
    source: "运行环境",
    status,
    reason,
    repairPath: "/system/providers",
    partial: false,
  };
}

function statusToReadiness(status: string): { status: ReadinessStatus; partial: boolean } {
  if (status === "UP") return { status: "ready", partial: false };
  if (status === "MODEL_DISABLED") return { status: "disabled", partial: false };
  if (status === "UNKNOWN") return { status: "blocked", partial: true };
  return { status: "blocked", partial: false };
}

function reasonForDependency(dependency: RuntimeDependencyStatus, status: ReadinessStatus): string {
  if (status === "ready") return dependency.detail;
  if (status === "disabled") return `${dependency.displayName}未启用：${dependency.detail}`;
  if (dependency.status === "UNKNOWN")
    return `${dependency.displayName}状态未采集：${dependency.detail}`;
  if (dependency.status === "NOT_CONNECTED")
    return `${dependency.displayName}未连接：${dependency.detail}`;
  return `${dependency.displayName}不可验收：${dependency.detail}`;
}

function repairPathFor(key: string): string {
  const normalized = key.toLowerCase();
  if (normalized.includes("terminology")) return "/terminology/mapping";
  if (normalized.includes("graph")) return "/advanced/graph";
  if (normalized.includes("adapter") || normalized.includes("integration")) return "/adapter/hub";
  return "/system/providers";
}

function summarize(items: SelfCheckItem[]) {
  const counts = items.reduce(
    (acc, item) => ({
      ready: acc.ready + (item.status === "ready" ? 1 : 0),
      blocked: acc.blocked + (item.status === "blocked" ? 1 : 0),
      disabled: acc.disabled + (item.status === "disabled" ? 1 : 0),
    }),
    { ready: 0, blocked: 0, disabled: 0 },
  );
  let conclusion = "全部自检项已就绪";
  if (counts.blocked > 0) {
    conclusion = "存在阻塞项，验收前需处理";
  } else if (counts.disabled > 0) {
    conclusion = "核心自检项已就绪，未启用能力不计入通过";
  }
  return { ...counts, conclusion };
}
