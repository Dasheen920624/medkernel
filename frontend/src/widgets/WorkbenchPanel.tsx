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
import {
  customerDisplayText,
  customerEnumLabel,
  customerSafeDisplayText,
} from "@/shared/config/customerLabels";
import {
  findProductRoleJourney,
  type ProductRoleAction,
  type ProductRoleKind,
} from "@/shared/config/productRoleJourneys";
import { formatClinicalDateTime } from "@/shared/lib/dateTimeText";
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
  kind: ProductRoleKind;
  showLifecycle: boolean;
  primaryAction: DomainEntryAction;
  highFrequencyActions: DomainEntryAction[];
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

type DomainEntryAction = ProductRoleAction;

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
  PILOT: "试运行",
  ACCEPTANCE: "验收",
  PROMOTION: "推广",
  RUNNING: "正式运行",
  RENEWAL: "续约",
};

const WORKBENCH_EVIDENCE_HINT = "失败已留痕，可在审计证据中追溯。";
const KNOWLEDGE_RELATION_SYNC_LABEL = "知识关系同步";

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
          description={workbenchSafeErrorMessage(
            parsed.message,
            "暂时无法核验当前角色，请重试或联系信息科。",
            parsed.traceId,
          )}
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
  if (canQuerySuccessPlan) sourceQueries.push(["服务机构生命周期", successPlan]);
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
          description={
            firstFailure
              ? workbenchErrorMessage(firstFailure.message, firstFailure.traceId)
              : "请稍后重试，或联系信息科检查来源服务。"
          }
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
          onClick={() => navigate(view.primaryAction.path)}
        >
          {view.primaryAction.label}
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
        {view.highFrequencyActions.length > 0 ? (
          <RoleTaskCard actions={view.highFrequencyActions} onNavigate={navigate} />
        ) : null}
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
          <DependencyConnectionCard runtime={runtime} onNavigate={onNavigate} />
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
            title="临床协同入口"
            description="进入患者路径、提醒与推荐、随访协同与消息通知；各页面展示对应真实数据和处理入口。"
            actions={[
              { label: "患者路径", path: "/pathway/patients" },
              { label: "提醒与推荐", path: "/cdss/fatigue" },
              { label: "消息通知", path: "/notifications" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
      </Row>
    );
  }

  if (view.kind === "knowledge") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <DomainEntryCard
            id="knowledge-governance"
            title="知识审核发布中心"
            marker="平台主源与机构派生"
            description="治理平台主源、机构派生、版本差异、审核发布和恢复平台标准，全程保留不可变血缘。"
            actions={[
              { label: "知识审核发布中心", path: "/knowledge/governance" },
              { label: "机构生效版本", path: "/config/releases" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
        <Col xs={24} lg={12}>
          <DomainEntryCard
            id="knowledge-lineage"
            title="来源、差异与发布"
            description="追溯知识来源和派生关系，复核术语映射与发布影响；汇总数据以各治理页面为准。"
            actions={[
              { label: "来源与血缘", path: "/advanced/provenance" },
              { label: "术语字典", path: "/terminology/mapping" },
              { label: "知识关系", path: "/advanced/graph" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
        <Col xs={24} lg={12}>
          <DomainEntryCard
            id="engine-quality"
            title="质量问题与整改"
            description="进入质量管理概览核查指标口径、责任对象、整改进度和医保审核入口。"
            actions={[
              { label: "质量问题与整改", path: "/qc/alerts" },
              { label: "医保审核", path: "/qc/insurance" },
              { label: "评价指标", path: "/qc/eval/sets" },
            ]}
            onNavigate={onNavigate}
          />
        </Col>
        {sourceAccess.audit ? (
          <Col xs={24} lg={12}>
            <AuditChangesCard audit={audit} timeFilter={timeFilter} onNavigate={onNavigate} />
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
            id="audit-evidence"
            title="合规证据入口"
            description="进入审计、来源血缘和安全配置页面复核真实证据；未连接外部系统时保持诚实降级。"
            actions={[
              { label: "审计与证据", path: "/admin/audit" },
              { label: "来源与血缘", path: "/advanced/provenance" },
              { label: "安全与配置", path: "/security/baseline" },
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
          title="质量问题与整改"
          marker="质量整改入口"
          description="进入质量管理概览和整改页面查看责任对象、整改进度和复核入口。"
          actions={[
            { label: "质量管理概览", path: "/qc/dashboard" },
            { label: "质量问题与整改", path: "/qc/alerts" },
          ]}
          onNavigate={onNavigate}
        />
      </Col>
      <Col xs={24} lg={12}>
        <DomainEntryCard
          id="value"
          title="质量管理"
          marker="质量管理入口"
          description="进入质量管理概览核查指标口径、责任对象、整改进度和医保审核入口。"
          actions={[
            { label: "质量管理概览", path: "/qc/dashboard" },
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

function RoleTaskCard({
  actions,
  onNavigate,
}: {
  actions: DomainEntryAction[];
  onNavigate: (path: string) => void;
}) {
  return (
    <Card title="当前职责高频任务">
      <Space wrap>
        {actions.map((action) => (
          <Button
            key={action.path}
            icon={<ArrowRightOutlined />}
            onClick={() => onNavigate(action.path)}
          >
            {action.label}
          </Button>
        ))}
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
      drilldown={{ label: "查看运行保障", path: "/system/providers" }}
      onNavigate={onNavigate}
    >
      {(data) => (
        <Space direction="vertical" size="small">
          <StatusTag status={data.healthStatus} />
          <Text>当前环境：{customerDisplayText(data.environment)}</Text>
          <Text type="secondary">运行保障可查看数据库和依赖明细</Text>
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
      drilldown={{ label: "查看运行保障", path: "/system/providers" }}
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

function DependencyConnectionCard({
  runtime,
  onNavigate,
}: {
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  onNavigate: (path: string) => void;
}) {
  return (
    <SourceCard
      id="provider"
      title="外部依赖连通"
      query={runtime}
      drilldown={{ label: "查看依赖状态", path: "/system/providers" }}
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
      title={KNOWLEDGE_RELATION_SYNC_LABEL}
      query={runtime}
      drilldown={{ label: "查看知识关系", path: "/advanced/graph" }}
      onNavigate={onNavigate}
    >
      {(data) => {
        const graph = data.dependencies.find(isKnowledgeRelationDependency);
        if (!graph) {
          return (
            <Space direction="vertical" size="small">
              <Tag>知识关系同步来源待配置</Tag>
              <Text type="secondary">
                当前运行状态未返回知识关系同步来源，请在运行保障中核查知识关系同步配置。
              </Text>
            </Space>
          );
        }
        return (
          <Space direction="vertical" size="small">
            <StatusTag status={graph.status} />
            <Text>{workbenchDependencyDisplayName(graph)}</Text>
            <Text type="secondary">{workbenchDependencyDetail(graph)}</Text>
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
                  {auditActorSummary(event.actorUserId)} · {formatTime(event.occurredAt)}
                </Text>
              </Space>
            </List.Item>
          )}
        />
      )}
    </SourceCard>
  );
}

function auditActorSummary(actorUserId?: string | null): string {
  return actorUserId ? "操作人已登记" : "系统自动处理";
}

function TodoCard({ onNavigate }: { onNavigate: (path: string) => void }) {
  return (
    <Card data-testid="workbench-card-todo" title="我的待办" extra={<Tag>无待办</Tag>}>
      <PageState
        state="empty"
        title="当前组织暂无待办"
        description="当前组织暂无待办；可进入患者路径、提醒与推荐、随访协同或消息通知查看实时事项。"
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
          <Text>
            {customerSafeDisplayText(parsed.message, `${title}暂时不可用，请重试或联系信息科。`)}
          </Text>
          {parsed.traceId ? <Text type="secondary">{WORKBENCH_EVIDENCE_HINT}</Text> : null}
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
          description="当前组织暂无可展示内容，请确认组织范围或进入对应页面处理。"
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
      <Card title="治理概览">
        <Skeleton active paragraph={{ rows: 2 }} />
      </Card>
    );
  }

  if (successPlan.isError) {
    const parsed = parseApiError(successPlan.error, "治理概览暂时不可用");
    return (
      <Card title="治理概览">
        <PageState
          state="error"
          title="治理概览暂时不可用"
          description={workbenchSafeErrorMessage(
            parsed.message,
            "治理概览暂时不可用，请重试或联系信息科。",
            parsed.traceId,
          )}
        />
      </Card>
    );
  }

  if (!successPlan.data) {
    return (
      <Card title="治理概览">
        <PageState
          state="empty"
          title="暂无治理概览"
          description="当前服务机构暂无可展示的生命周期证据。"
        />
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
    <Card title="治理概览">
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
              {failure.name}：
              {customerSafeDisplayText(
                failure.message,
                `${failure.name}暂时不可用，请重试或联系信息科。`,
              )}
              {failure.traceId ? `（${WORKBENCH_EVIDENCE_HINT}）` : ""}
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
          {workbenchDependencyDisplayName(dependency)} ·{" "}
          {STATUS_LABEL[dependency.status] ?? customerEnumLabel(dependency.status)}
        </Tag>
      ))}
    </Space>
  );
}

function isKnowledgeRelationDependency(dependency: RuntimeDependencyStatus) {
  return dependency.key.includes("graph");
}

function isDatabaseDependency(dependency: RuntimeDependencyStatus) {
  return dependency.key.includes("database") || dependency.displayName.includes("数据库");
}

function workbenchDependencyDisplayName(dependency: RuntimeDependencyStatus) {
  if (isKnowledgeRelationDependency(dependency)) {
    return KNOWLEDGE_RELATION_SYNC_LABEL;
  }
  if (isDatabaseDependency(dependency)) {
    return "运行数据服务";
  }
  return customerDisplayText(dependency.displayName);
}

function workbenchDependencyDetail(dependency: RuntimeDependencyStatus) {
  if (isKnowledgeRelationDependency(dependency)) {
    return dependency.status === "UP"
      ? "知识关系同步可用，可进入知识关系复核来源、适应证、禁忌和相互作用。"
      : "知识关系同步未连接；核心业务继续使用关系库权威数据。";
  }
  return customerSafeDisplayText(dependency.detail, "依赖状态待确认，请在运行保障中核查。");
}

function StatusTag({ status }: { status: string }) {
  return (
    <Tag color={STATUS_COLOR[status] ?? "default"}>
      {STATUS_LABEL[status] ?? customerEnumLabel(status)}
    </Tag>
  );
}

function collectFailures(
  sources: ReadonlyArray<readonly [string, SourceQuery<unknown> | undefined]>,
) {
  return sources.flatMap(([name, query]) => {
    if (!query?.isError) return [];
    const parsed = parseApiError(query.error, `${name}暂时不可用`);
    return [
      {
        name,
        message: customerSafeDisplayText(parsed.message, `${name}暂时不可用，请重试或联系信息科。`),
        traceId: parsed.traceId,
      },
    ];
  });
}

function workbenchErrorMessage(message: string, traceId?: string): string {
  return traceId ? `${message}；${WORKBENCH_EVIDENCE_HINT}` : message;
}

function workbenchSafeErrorMessage(message: string, fallback: string, traceId?: string): string {
  return workbenchErrorMessage(customerSafeDisplayText(message, fallback), traceId);
}

function resolveScopeLabel(profile?: SecurityProfile): string {
  const dataScope = profile?.dataScope;
  if (!dataScope) return "当前组织";
  if (dataScope.wardId) return "当前病区";
  if (dataScope.departmentId) return "当前科室";
  if (dataScope.hospitalId) return "当前医院";
  if (dataScope.groupId) return "当前集团";
  if (dataScope.tenantId) return "当前服务机构";
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
  const actions: Array<{ key: string; title: string; description: string; path: string }> = [];

  if (view.showLifecycle) {
    actions.push(
      {
        key: "implementation",
        title: "核对实施进度",
        description: "查看实施阶段、机构生效版本与上线准备项。",
        path: "/onboarding/guide",
      },
      {
        key: "runtime-releases",
        title: "复核生效版本",
        description: "确认当前机构启用的平台标准版本和完整内容组合。",
        path: "/config/releases",
      },
    );
    return actions;
  }

  if (view.kind === "operations") {
    if (sourceAccess.runtime) {
      actions.push({
        key: "providers",
        title: disconnected ? "核对未连接依赖" : "核对外部依赖",
        description: "查看运行环境返回的依赖连通状态。",
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

  view.highFrequencyActions.forEach((action, index) => {
    actions.push({
      key: `role-task-${index + 1}`,
      title: action.label,
      description: `进入${action.label}，继续当前职责范围内的真实任务。`,
      path: action.path,
    });
  });
  if (actions.length === 0 && sourceAccess.audit) {
    actions.push({
      key: "audit",
      title: "查看审计与证据",
      description: "按真实审计来源复核近期动作与证据。",
      path: "/admin/audit",
    });
  }
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
  const journey = findProductRoleJourney(roles[0]?.code);
  if (journey) {
    return {
      title: journey.title,
      description: journey.summary,
      kind: journey.kind,
      showLifecycle: journey.showLifecycle,
      primaryAction: journey.primaryAction,
      highFrequencyActions: journey.highFrequencyActions,
    };
  }

  return {
    title: roles[0]?.displayName ? `${roles[0].displayName}工作台` : "工作台",
    description: "查看服务机构阶段、运行状态和需要跟进的事项。",
    kind: "operations",
    showLifecycle: false,
    primaryAction: { label: "管理服务机构", path: "/tenant/onboarding" },
    highFrequencyActions: [],
  };
}

function formatTime(value?: string | null): string {
  if (!value) return "暂无";
  return formatClinicalDateTime(value, value);
}
