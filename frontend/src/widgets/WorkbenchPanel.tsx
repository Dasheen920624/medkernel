import {
  Alert,
  Button,
  Card,
  Col,
  List,
  Row,
  Segmented,
  Select,
  Skeleton,
  Space,
  Tag,
  Typography,
} from "antd";
import { ArrowRightOutlined } from "@ant-design/icons";
import { useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { TenantLifecyclePanel } from "@/features/tenant-lifecycle/TenantLifecyclePanel";
import { WorkbenchTabs } from "@/widgets/WorkbenchTabs";
import {
  useAuditEvents,
  useRuntimeOperations,
  useSecurityProfile,
  useSuccessPlan,
  type AuditEventRow,
  type RuntimeDependencyStatus,
  type RuntimeOperationsSnapshot,
  type SecurityProfile,
} from "@/shared/api/hooks";
import { parseApiError } from "@/shared/api/errors";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";

const { Text } = Typography;

type SourceQuery<T> = {
  data?: T;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
};

type RoleView = {
  title: string;
  description: string;
  kind: "operations" | "clinical" | "governance" | "tenant" | "audit";
  showLifecycle: boolean;
};

type SourceAccess = {
  runtime: boolean;
  audit: boolean;
};

type TimeFilter = "today" | "week" | "month";

type DrilldownTarget = {
  label: string;
  path: string;
};

type DomainEntryAction = {
  label: string;
  path: string;
};

const STATUS_LABEL: Record<string, string> = {
  UP: "正常",
  DEGRADED: "降级",
  NOT_CONNECTED: "未连接",
  MODEL_DISABLED: "智能建议暂不可用",
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

const STAGE_LABEL: Record<string, string> = {
  PREPARATION: "准备",
  PILOT: "临床试点",
  ACCEPTANCE: "验收",
  PROMOTION: "推广",
  RUNNING: "正式运行",
  RENEWAL: "续约",
};

/**
 * 工作台只读组合现有来源 API，不拥有独立业务数据。
 *
 * 已建域读取真实来源；未建域只展示诚实未启用态，不伪造指标。
 */
export function WorkbenchPanel() {
  const navigate = useNavigate();
  const [timeFilter, setTimeFilter] = useState<TimeFilter>("week");
  const security = useSecurityProfile();
  const profile = security.data;
  const view = resolveRoleView(profile);
  const canQueryWorkbench = Boolean(profile && canOpenWorkbench(profile));
  const sourceAccess: SourceAccess = {
    runtime: canQueryWorkbench && hasPermission(profile, "system.read"),
    audit: canQueryWorkbench && hasPermission(profile, "audit.read"),
  };
  const canQuerySuccessPlan =
    canQueryWorkbench && view.showLifecycle && hasPermission(profile, "tenant.read");
  const runtime = useRuntimeOperations(sourceAccess.runtime);
  const audit = useAuditEvents(sourceAccess.audit);
  const successPlan = useSuccessPlan(canQuerySuccessPlan);

  if (security.isLoading) {
    return (
      <PageShell title="工作台" description="正在确认当前角色">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (security.isError) {
    const parsed = parseApiError(security.error, "暂时无法核验当前角色");
    return (
      <PageShell title="工作台" description="角色核验失败">
        <PageState
          state="error"
          title="暂时无法核验权限"
          description={parsed.message}
          traceId={parsed.traceId}
        />
      </PageShell>
    );
  }

  if (profile && !canOpenWorkbench(profile)) {
    return (
      <PageShell title="工作台" description="当前角色不可访问此页面">
        <PageState
          state="forbidden"
          title="当前权限不足"
          description="当前角色未获得工作台菜单权限，请联系信息科调整角色或数据范围。"
        />
      </PageShell>
    );
  }

  const sourceQueries: Array<readonly [string, SourceQuery<unknown>]> = [];
  if (sourceAccess.runtime) sourceQueries.push(["运行状态", runtime]);
  if (sourceAccess.audit) sourceQueries.push(["最近变化", audit]);
  if (canQuerySuccessPlan) sourceQueries.push(["租户生命周期", successPlan]);
  const sourceFailures = collectFailures(sourceQueries);
  const allSourcesFailed =
    sourceFailures.length > 0 &&
    sourceQueries.length > 0 &&
    sourceQueries.every(([, query]) => query.isError);

  if (allSourcesFailed) {
    const firstFailure = sourceFailures[0];
    return (
      <PageShell title={view.title} description={view.description}>
        <PageState
          state="error"
          title="工作台暂时不可用"
          description={firstFailure?.message ?? "请稍后重试，或联系信息科检查来源服务。"}
          traceId={firstFailure?.traceId}
        />
      </PageShell>
    );
  }

  return (
    <PageShell
      title={view.title}
      description={view.description}
      primary={
        <Button
          type="primary"
          icon={<ArrowRightOutlined />}
          onClick={() => navigate("/workflow/todos")}
        >
          继续处理待办
        </Button>
      }
    >
      <Space direction="vertical" size="large" className="mk-full-width">
        <WorkbenchTabs />
        {sourceFailures.length > 0 ? <PartialSourceAlert failures={sourceFailures} /> : null}
        <WorkbenchFilters
          profile={profile}
          timeFilter={timeFilter}
          onTimeFilterChange={setTimeFilter}
        />
        {canQuerySuccessPlan ? <TenantLifecyclePanel /> : null}
        {canQuerySuccessPlan ? (
          <GovernanceSlices successPlan={successPlan} runtime={runtime} />
        ) : null}
        <WorkbenchCards
          view={view}
          sourceAccess={sourceAccess}
          runtime={runtime}
          audit={audit}
          timeFilter={timeFilter}
          onNavigate={navigate}
        />
        <WeeklyActions
          view={view}
          sourceAccess={sourceAccess}
          runtime={runtime}
          onNavigate={navigate}
        />
      </Space>
    </PageShell>
  );
}

function WorkbenchCards({
  view,
  sourceAccess,
  runtime,
  audit,
  timeFilter,
  onNavigate,
}: {
  view: RoleView;
  sourceAccess: SourceAccess;
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  audit: SourceQuery<AuditEventRow[]>;
  timeFilter: TimeFilter;
  onNavigate: (path: string) => void;
}) {
  if (view.kind === "operations") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <SystemHealthCard runtime={runtime} onNavigate={onNavigate} />
        </Col>
        <Col xs={24} lg={12}>
          <ProviderCard runtime={runtime} onNavigate={onNavigate} />
        </Col>
        <Col xs={24} lg={12}>
          <KnowledgeSyncCard runtime={runtime} onNavigate={onNavigate} />
        </Col>
        <Col xs={24} lg={12}>
          <AuditChangesCard audit={audit} timeFilter={timeFilter} onNavigate={onNavigate} />
        </Col>
      </Row>
    );
  }

  if (view.kind === "clinical") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <TodoCard onNavigate={onNavigate} />
        </Col>
        <Col xs={24} lg={12}>
          <DomainEntryCard
            id="clinical"
            title="临床运行入口"
            description="从已上线页面进入患者路径、智能建议、随访与通知；工作台不伪造跨页聚合数量。"
            actions={[
              { label: "患者路径", path: "/pathway/patients" },
              { label: "智能建议", path: "/cdss/fatigue" },
              { label: "通知中心", path: "/notifications" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
      </Row>
    );
  }

  if (view.kind === "governance") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <DomainEntryCard
            id="value"
            title="价值指标"
            marker="价值指标入口"
            description="进入院级质控驾驶舱查看真实指标、风险热力与价值指标；工作台不伪造趋势。"
            actions={[
              { label: "院级质控驾驶舱", path: "/qc/dashboard" },
              { label: "评估指标库", path: "/qc/eval/sets" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
        <Col xs={24} lg={12}>
          <DomainEntryCard
            id="quality"
            title="质控整改"
            marker="质控整改入口"
            description="进入评估结果、质控预警和医保审核，查看真实整改对象、责任范围与证据。"
            actions={[
              { label: "院级质控驾驶舱", path: "/qc/dashboard" },
              { label: "评估结果", path: "/qc/eval/results" },
              { label: "医保审核", path: "/qc/insurance" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
        {sourceAccess.audit ? (
          <Col xs={24} lg={12}>
            <AuditChangesCard audit={audit} timeFilter={timeFilter} onNavigate={onNavigate} />
          </Col>
        ) : null}
        {sourceAccess.runtime ? (
          <Col xs={24} lg={12}>
            <SimpleRuntimeCard runtime={runtime} onNavigate={onNavigate} />
          </Col>
        ) : null}
      </Row>
    );
  }

  if (view.kind === "audit") {
    return (
      <Row gutter={[16, 16]}>
        {sourceAccess.audit ? (
          <Col xs={24} lg={12}>
            <AuditChangesCard audit={audit} timeFilter={timeFilter} onNavigate={onNavigate} />
          </Col>
        ) : null}
        {sourceAccess.runtime ? (
          <Col xs={24} lg={12}>
            <SimpleRuntimeCard runtime={runtime} onNavigate={onNavigate} />
          </Col>
        ) : null}
        <Col xs={24} lg={12}>
          <DomainEntryCard
            id="quality"
            title="合规证据入口"
            description="进入审计、质控和导出审批相关页面复核真实证据；未连接外部系统时保持诚实降级。"
            actions={[
              { label: "审计日志", path: "/admin/audit" },
              { label: "质控预警", path: "/qc/alerts" },
              { label: "评估结果", path: "/qc/eval/results" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
      </Row>
    );
  }

  return (
    <Row gutter={[16, 16]}>
      {sourceAccess.runtime ? (
        <Col xs={24} lg={12}>
          <SystemHealthCard runtime={runtime} onNavigate={onNavigate} />
        </Col>
      ) : null}
      {sourceAccess.audit ? (
        <Col xs={24} lg={12}>
          <AuditChangesCard audit={audit} timeFilter={timeFilter} onNavigate={onNavigate} />
        </Col>
      ) : null}
      <Col xs={24} lg={12}>
        <DomainEntryCard
          id="quality"
          title="质控整改"
          marker="质控整改入口"
          description="进入质控驾驶舱和评估结果查看真实整改对象；工作台不伪造汇总趋势。"
          actions={[
            { label: "院级质控驾驶舱", path: "/qc/dashboard" },
            { label: "评估结果", path: "/qc/eval/results" },
          ]}
          onNavigate={onNavigate}
        />
      </Col>
      <Col xs={24} lg={12}>
        <DomainEntryCard
          id="value"
          title="价值指标"
          marker="价值指标入口"
          description="进入质控驾驶舱查看真实价值指标；暂无工作台独立聚合时只提供入口。"
          actions={[
            { label: "院级质控驾驶舱", path: "/qc/dashboard" },
            { label: "医保审核", path: "/qc/insurance" },
          ]}
          onNavigate={onNavigate}
        />
      </Col>
    </Row>
  );
}

function WorkbenchFilters({
  profile,
  timeFilter,
  onTimeFilterChange,
}: {
  profile?: SecurityProfile;
  timeFilter: TimeFilter;
  onTimeFilterChange: (value: TimeFilter) => void;
}) {
  const scopeLabel = resolveScopeLabel(profile);
  return (
    <Card title="当前视图筛选" data-testid="workbench-default-filters">
      <Space wrap>
        <Space direction="vertical" size={2} data-testid="workbench-filter-org">
          <Text type="secondary">组织范围</Text>
          <Select
            aria-label="组织范围"
            value={scopeLabel}
            options={[{ label: scopeLabel, value: scopeLabel }]}
          />
        </Space>
        <Space direction="vertical" size={2} data-testid="workbench-filter-disease">
          <Text type="secondary">病种</Text>
          <Select
            aria-label="病种"
            value="全部病种"
            options={[{ label: "全部病种", value: "全部病种" }]}
          />
        </Space>
        <Space direction="vertical" size={2} data-testid="workbench-filter-time">
          <Text type="secondary">时间</Text>
          <Segmented
            aria-label="时间"
            value={timeFilter}
            options={[
              { label: "今日", value: "today" },
              { label: "本周", value: "week" },
              { label: "本月", value: "month" },
            ]}
            onChange={(value) => onTimeFilterChange(value as TimeFilter)}
          />
        </Space>
      </Space>
    </Card>
  );
}

function SystemHealthCard({
  runtime,
  onNavigate,
}: {
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  onNavigate: (path: string) => void;
}) {
  return (
    <SourceCard
      id="system"
      title="系统健康"
      query={runtime}
      drilldown={{ label: "查看系统健康", path: "/system/providers" }}
      onNavigate={onNavigate}
    >
      {(data) => (
        <Space direction="vertical" size="small">
          <StatusTag status={data.healthStatus} />
          <Text>当前环境：{data.environment}</Text>
          <Text type="secondary">数据库：{data.databaseDialect}</Text>
        </Space>
      )}
    </SourceCard>
  );
}

function SimpleRuntimeCard({
  runtime,
  onNavigate,
}: {
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  onNavigate: (path: string) => void;
}) {
  return (
    <SourceCard
      id="runtime-simple"
      title="整体运行"
      query={runtime}
      drilldown={{ label: "查看运行状态", path: "/system/providers" }}
      onNavigate={onNavigate}
    >
      {(data) => (
        <Space direction="vertical" size="small">
          <StatusTag status={data.healthStatus} />
          <Text>当前运行状态{data.healthStatus === "UP" ? "稳定" : "需要关注"}。</Text>
          <Text type="secondary">最近更新时间：{formatTime(data.generatedAt)}</Text>
        </Space>
      )}
    </SourceCard>
  );
}

function ProviderCard({
  runtime,
  onNavigate,
}: {
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  onNavigate: (path: string) => void;
}) {
  return (
    <SourceCard
      id="provider"
      title="Provider 连通"
      query={runtime}
      drilldown={{ label: "查看 Provider", path: "/system/providers" }}
      onNavigate={onNavigate}
    >
      {(data) => {
        const total = data.dependencies.length;
        const healthy = data.dependencies.filter((item) => item.status === "UP").length;
        return (
          <Space direction="vertical" size="small" className="mk-full-width">
            <Text strong>
              {healthy}/{total} 项可用
            </Text>
            <DependencyList dependencies={data.dependencies} />
          </Space>
        );
      }}
    </SourceCard>
  );
}

function KnowledgeSyncCard({
  runtime,
  onNavigate,
}: {
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  onNavigate: (path: string) => void;
}) {
  return (
    <SourceCard
      id="knowledge"
      title="知识同步"
      query={runtime}
      drilldown={{ label: "查看知识图谱", path: "/advanced/graph" }}
      onNavigate={onNavigate}
    >
      {(data) => {
        const graph = data.dependencies.find((item) => item.key.includes("graph"));
        if (!graph) {
          return (
            <Space direction="vertical" size="small">
              <Tag>未接入</Tag>
              <Text type="secondary">当前运行快照未返回知识同步来源。</Text>
            </Space>
          );
        }
        return (
          <Space direction="vertical" size="small">
            <StatusTag status={graph.status} />
            <Text>{graph.displayName}</Text>
            <Text type="secondary">{graph.detail}</Text>
          </Space>
        );
      }}
    </SourceCard>
  );
}

function AuditChangesCard({
  audit,
  timeFilter,
  onNavigate,
}: {
  audit: SourceQuery<AuditEventRow[]>;
  timeFilter: TimeFilter;
  onNavigate: (path: string) => void;
}) {
  const auditRows = audit.data;
  const hasAuditData = Array.isArray(auditRows);
  const visibleEvents = hasAuditData ? filterAuditEvents(auditRows, timeFilter) : [];
  return (
    <SourceCard
      id="audit"
      title="最近变化"
      query={audit}
      empty={hasAuditData && visibleEvents.length === 0}
      drilldown={{ label: "查看最近变化", path: "/admin/audit" }}
      onNavigate={onNavigate}
    >
      {() => (
        <List
          size="small"
          dataSource={visibleEvents.slice(0, 3)}
          locale={{ emptyText: "暂无审计事件" }}
          renderItem={(event) => (
            <List.Item>
              <Space direction="vertical" size={0}>
                <Text>{event.summary}</Text>
                <Text type="secondary">
                  {event.actorUserId ?? "系统"} · {formatTime(event.occurredAt)}
                </Text>
              </Space>
            </List.Item>
          )}
        />
      )}
    </SourceCard>
  );
}

function TodoCard({ onNavigate }: { onNavigate: (path: string) => void }) {
  return (
    <Card data-testid="workbench-card-todo" title="我的待办" extra={<Tag>暂无</Tag>}>
      <PageState
        state="empty"
        title="当前组织暂无待办"
        description="当前组织暂无待办，可查看配置包或切换组织。"
        action={
          <Button type="link" onClick={() => onNavigate("/workflow/todos")}>
            查看待办
          </Button>
        }
      />
    </Card>
  );
}

function DomainEntryCard({
  id,
  title,
  marker,
  description,
  actions,
  onNavigate,
}: {
  id: string;
  title: string;
  marker?: string;
  description: string;
  actions: DomainEntryAction[];
  onNavigate: (path: string) => void;
}) {
  return (
    <Card
      data-testid={`workbench-card-${id}`}
      title={title}
      extra={<Tag color="processing">已上线</Tag>}
    >
      <Space direction="vertical" size="small">
        {marker ? <Text strong>{marker}</Text> : null}
        <Text type="secondary">{description}</Text>
        <Space wrap>
          {actions.map((action) => (
            <Button
              key={action.path}
              type="link"
              aria-label={action.label}
              icon={<ArrowRightOutlined />}
              onClick={() => onNavigate(action.path)}
            >
              {action.label}
            </Button>
          ))}
        </Space>
      </Space>
    </Card>
  );
}

function SourceCard<T>({
  id,
  title,
  query,
  empty,
  drilldown,
  onNavigate,
  children,
}: {
  id: string;
  title: string;
  query: SourceQuery<T>;
  empty?: boolean;
  drilldown?: DrilldownTarget;
  onNavigate?: (path: string) => void;
  children: (data: T) => ReactNode;
}) {
  const drilldownAction =
    drilldown && onNavigate ? (
      <Button type="link" icon={<ArrowRightOutlined />} onClick={() => onNavigate(drilldown.path)}>
        {drilldown.label}
      </Button>
    ) : null;

  if (query.isLoading) {
    return (
      <Card data-testid={`workbench-card-${id}`} title={title} extra={<Tag>读取中</Tag>}>
        <Skeleton active paragraph={{ rows: 3 }} />
      </Card>
    );
  }

  if (query.isError) {
    const parsed = parseApiError(query.error, `${title}暂时不可用`);
    return (
      <Card
        data-testid={`workbench-card-${id}`}
        title={title}
        extra={<Tag color="warning">降级</Tag>}
      >
        <Space direction="vertical" size="small">
          <Text>{parsed.message}</Text>
          {parsed.traceId ? <Text type="secondary">{parsed.traceId}</Text> : null}
          {drilldownAction}
        </Space>
      </Card>
    );
  }

  if (!query.data || empty) {
    return (
      <Card data-testid={`workbench-card-${id}`} title={title} extra={<Tag>暂无</Tag>}>
        <PageState
          state="empty"
          title="暂无数据"
          description="当前组织暂无可展示内容，后续来源上线后会自动回灌。"
          action={drilldownAction}
        />
      </Card>
    );
  }

  return (
    <Card
      data-testid={`workbench-card-${id}`}
      title={title}
      extra={<Tag color="success">已读取</Tag>}
    >
      <Space direction="vertical" size="small" className="mk-full-width">
        {children(query.data)}
        {drilldownAction}
      </Space>
    </Card>
  );
}

function GovernanceSlices({
  successPlan,
  runtime,
}: {
  successPlan: SourceQuery<{ currentStage: string; healthScore: number; activatedModules: string }>;
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
}) {
  if (successPlan.isLoading) {
    return (
      <Card title="治理切片">
        <Skeleton active paragraph={{ rows: 2 }} />
      </Card>
    );
  }

  if (successPlan.isError) {
    const parsed = parseApiError(successPlan.error, "治理切片暂时不可用");
    return (
      <Card title="治理切片">
        <PageState
          state="error"
          title="治理切片暂时不可用"
          description={parsed.message}
          traceId={parsed.traceId}
        />
      </Card>
    );
  }

  if (!successPlan.data) {
    return (
      <Card title="治理切片">
        <PageState state="empty" title="暂无治理切片" description="当前租户暂无生命周期证据。" />
      </Card>
    );
  }

  const slices = [
    {
      key: "stage",
      title: "当前阶段",
      value: STAGE_LABEL[successPlan.data.currentStage] ?? successPlan.data.currentStage,
    },
    {
      key: "scope",
      title: "服务范围",
      value: successPlan.data.activatedModules || "暂无启用服务",
    },
    {
      key: "health",
      title: "运行健康",
      value: runtime.data ? `${successPlan.data.healthScore}/100` : "等待运行来源",
    },
  ];

  return (
    <Card title="治理切片">
      <Row gutter={[16, 16]}>
        {slices.map((slice) => (
          <Col xs={24} md={8} key={slice.key}>
            <Space
              direction="vertical"
              size={2}
              className="mk-full-width"
              data-testid={`workbench-governance-slice-${slice.key}`}
            >
              <Text type="secondary">{slice.title}</Text>
              <Text strong>{slice.value}</Text>
            </Space>
          </Col>
        ))}
      </Row>
    </Card>
  );
}

function WeeklyActions({
  view,
  sourceAccess,
  runtime,
  onNavigate,
}: {
  view: RoleView;
  sourceAccess: SourceAccess;
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  onNavigate: (path: string) => void;
}) {
  const actions = resolveWeeklyActions(view, sourceAccess, runtime.data);
  return (
    <Card title="本周建议动作">
      <List
        size="small"
        dataSource={actions}
        renderItem={(action) => (
          <List.Item
            actions={[
              <Button key={action.key} type="link" onClick={() => onNavigate(action.path)}>
                进入
              </Button>,
            ]}
          >
            <List.Item.Meta title={action.title} description={action.description} />
          </List.Item>
        )}
      />
    </Card>
  );
}

function PartialSourceAlert({
  failures,
}: {
  failures: Array<{ name: string; message: string; traceId?: string }>;
}) {
  return (
    <Alert
      type="warning"
      showIcon
      message="部分来源暂时不可用"
      description={
        <Space direction="vertical" size={2}>
          {failures.map((failure) => (
            <Text key={failure.name}>
              {failure.name}：{failure.message}
              {failure.traceId ? `（${failure.traceId}）` : ""}
            </Text>
          ))}
        </Space>
      }
    />
  );
}

function DependencyList({ dependencies }: { dependencies: RuntimeDependencyStatus[] }) {
  return (
    <Space size={[8, 8]} wrap>
      {dependencies.map((dependency) => (
        <Tag key={dependency.key} color={STATUS_COLOR[dependency.status] ?? "default"}>
          {dependency.displayName} · {STATUS_LABEL[dependency.status] ?? dependency.status}
        </Tag>
      ))}
    </Space>
  );
}

function StatusTag({ status }: { status: string }) {
  return <Tag color={STATUS_COLOR[status] ?? "default"}>{STATUS_LABEL[status] ?? status}</Tag>;
}

function collectFailures(
  sources: ReadonlyArray<readonly [string, SourceQuery<unknown> | undefined]>,
) {
  return sources.flatMap(([name, query]) => {
    if (!query?.isError) return [];
    const parsed = parseApiError(query.error, `${name}暂时不可用`);
    return [{ name, message: parsed.message, traceId: parsed.traceId }];
  });
}

function resolveScopeLabel(profile?: SecurityProfile): string {
  const dataScope = profile?.dataScope;
  if (!dataScope) return "当前组织";
  if (dataScope.departmentId) return "当前科室";
  if (dataScope.hospitalId) return "当前医院";
  if (dataScope.groupId) return "当前集团";
  if (dataScope.tenantId) return "当前租户";
  return "当前组织";
}

function filterAuditEvents(events: AuditEventRow[], timeFilter: TimeFilter): AuditEventRow[] {
  const boundary = resolveTimeBoundary(timeFilter);
  return events.filter((event) => {
    const occurredAt = new Date(event.occurredAt);
    return !Number.isNaN(occurredAt.getTime()) && occurredAt >= boundary;
  });
}

function resolveTimeBoundary(timeFilter: TimeFilter): Date {
  const boundary = new Date();
  boundary.setHours(0, 0, 0, 0);

  if (timeFilter === "today") {
    return boundary;
  }

  if (timeFilter === "month") {
    boundary.setDate(1);
    return boundary;
  }

  const mondayOffset = (boundary.getDay() + 6) % 7;
  boundary.setDate(boundary.getDate() - mondayOffset);
  return boundary;
}

function resolveWeeklyActions(
  view: RoleView,
  sourceAccess: SourceAccess,
  runtime?: RuntimeOperationsSnapshot,
) {
  const disconnected = runtime?.dependencies.some((dependency) => dependency.status !== "UP");
  const actions = [
    {
      key: "todos",
      title: "继续处理待办",
      description: "进入真实待办中心查看当前角色范围内的任务。",
      path: "/workflow/todos",
    },
  ];

  if (view.showLifecycle) {
    actions.push(
      {
        key: "implementation",
        title: "核对实施进度",
        description: "查看试点阶段、配置包与上线准备项。",
        path: "/onboarding/guide",
      },
      {
        key: "packages",
        title: "复核配置包",
        description: "确认当前租户已启用的配置包和发布状态。",
        path: "/config/packages",
      },
    );
    return actions;
  }

  if (view.kind === "operations") {
    if (sourceAccess.runtime) {
      actions.push({
        key: "providers",
        title: disconnected ? "核对未连接依赖" : "核对 Provider 连通",
        description: "查看运行底座返回的依赖连通状态。",
        path: "/system/providers",
      });
    }
    if (sourceAccess.audit) {
      actions.push({
        key: "audit",
        title: "查看最近变化",
        description: "按真实审计来源复核近期动作。",
        path: "/admin/audit",
      });
    }
    return actions;
  }

  if (view.kind === "clinical") {
    actions.push(
      {
        key: "pathways",
        title: "查看临床路径",
        description: "进入临床路径页查看真实路径状态。",
        path: "/pathway/patients",
      },
      {
        key: "notifications",
        title: "查看通知",
        description: "确认当前角色范围内的通知是否需要跟进。",
        path: "/notifications",
      },
    );
    return actions;
  }

  if (sourceAccess.audit) {
    actions.push({
      key: "audit",
      title: "查看最近变化",
      description: "按真实审计来源复核近期动作。",
      path: "/admin/audit",
    });
  }
  actions.push({
    key: "quality",
    title: "复核整改入口",
    description: "进入质控改进域查看已上线的真实整改对象。",
    path: "/qc/eval/results",
  });
  return actions;
}

function canOpenWorkbench(profile: SecurityProfile): boolean {
  return (
    profile.menuKeys.includes("workbench") ||
    profile.permissions.some(
      (permission) => permission.code === "menu.workbench" || permission.target === "workbench",
    )
  );
}

function hasPermission(profile: SecurityProfile | undefined, code: string): boolean {
  return profile?.permissions.some((permission) => permission.code === code) ?? false;
}

function resolveRoleView(profile?: SecurityProfile): RoleView {
  const roles = profile?.roles ?? [];
  const codes = new Set(roles.map((role) => role.code));
  const displayName = roles[0]?.displayName;

  if (codes.has("it-ops")) {
    return {
      title: "信息科工作台",
      description: "优先查看系统健康、连通状态和最近变化。",
      kind: "operations",
      showLifecycle: false,
    };
  }

  if (["doctor", "nurse", "specialist", "dept-head", "med-technician", "pharmacist"].some((code) => codes.has(code))) {
    return {
      title: `${displayName}工作台`,
      description: "优先查看我的待办、临床提醒和最近变化。",
      kind: "clinical",
      showLifecycle: false,
    };
  }

  if (["medical-affairs", "qa-manager", "insurance-manager"].some((code) => codes.has(code))) {
    return {
      title: `${displayName}工作台`,
      description: "优先查看价值、整改和最近变化。",
      kind: "governance",
      showLifecycle: false,
    };
  }

  if (codes.has("audit-compliance")) {
    return {
      title: "合规审计工作台",
      description: "优先查看审计变化、运行状态和证据风险。",
      kind: "audit",
      showLifecycle: false,
    };
  }

  return {
    title: displayName ? `${displayName}工作台` : "工作台",
    description: "查看租户阶段、运行状态和需要跟进的事项。",
    kind: "tenant",
    showLifecycle: [
      "platform-admin",
      "group-admin",
      "hospital-admin",
      "implementation-engineer",
    ].some((code) => codes.has(code)),
  };
}

function formatTime(value?: string | null): string {
  if (!value) return "暂无";
  return new Date(value).toLocaleString();
}
