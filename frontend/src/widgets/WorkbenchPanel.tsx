import { Alert, Card, Col, List, Row, Skeleton, Space, Tag, Typography } from "antd";
import type { ReactNode } from "react";
import { TenantLifecyclePanel } from "@/features/tenant-lifecycle/TenantLifecyclePanel";
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

const { Paragraph, Text } = Typography;

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

/**
 * 工作台只读组合现有来源 API，不拥有独立业务数据。
 *
 * 已建域读取真实来源；未建域只展示诚实未启用态，不伪造指标。
 */
export function WorkbenchPanel() {
  const security = useSecurityProfile();
  const profile = security.data;
  const canQuerySources = Boolean(profile && canOpenWorkbench(profile));
  const runtime = useRuntimeOperations(canQuerySources);
  const audit = useAuditEvents(canQuerySources);
  const view = resolveRoleView(profile);
  const successPlan = useSuccessPlan(canQuerySources && view.showLifecycle);

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

  const sourceFailures = collectFailures([
    ["运行状态", runtime],
    ["最近变化", audit],
    ...(view.showLifecycle ? ([["租户生命周期", successPlan]] as const) : []),
  ]);
  const allSourcesFailed =
    sourceFailures.length > 0 &&
    [runtime, audit, view.showLifecycle ? successPlan : undefined]
      .filter(Boolean)
      .every((query) => query?.isError);

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
    <PageShell title={view.title} description={view.description}>
      <Space direction="vertical" size="large" className="mk-full-width">
        {sourceFailures.length > 0 ? <PartialSourceAlert failures={sourceFailures} /> : null}
        {view.showLifecycle ? <TenantLifecyclePanel /> : null}
        <WorkbenchCards view={view} runtime={runtime} audit={audit} />
      </Space>
    </PageShell>
  );
}

function WorkbenchCards({
  view,
  runtime,
  audit,
}: {
  view: RoleView;
  runtime: SourceQuery<RuntimeOperationsSnapshot>;
  audit: SourceQuery<AuditEventRow[]>;
}) {
  if (view.kind === "operations") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <SystemHealthCard runtime={runtime} />
        </Col>
        <Col xs={24} lg={12}>
          <ProviderCard runtime={runtime} />
        </Col>
        <Col xs={24} lg={12}>
          <KnowledgeSyncCard runtime={runtime} />
        </Col>
        <Col xs={24} lg={12}>
          <AuditChangesCard audit={audit} />
        </Col>
      </Row>
    );
  }

  if (view.kind === "clinical") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <TodoCard />
        </Col>
        <Col xs={24} lg={12}>
          <UnavailableDomainCard
            id="clinical"
            title="临床运行"
            description="临床运行摘要将在 D3 域上线后由真实来源回灌。"
          />
        </Col>
        <Col xs={24} lg={12}>
          <AuditChangesCard audit={audit} />
        </Col>
        <Col xs={24} lg={12}>
          <KnowledgeSyncCard runtime={runtime} />
        </Col>
      </Row>
    );
  }

  if (view.kind === "governance") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <UnavailableDomainCard
            id="value"
            title="价值指标"
            description="价值成效摘要将在质控与合规域完成后展示真实趋势。"
          />
        </Col>
        <Col xs={24} lg={12}>
          <UnavailableDomainCard
            id="quality"
            title="质控整改"
            description="质控整改摘要将在 D4 域上线后按责任范围展示。"
          />
        </Col>
        <Col xs={24} lg={12}>
          <AuditChangesCard audit={audit} />
        </Col>
        <Col xs={24} lg={12}>
          <SimpleRuntimeCard runtime={runtime} />
        </Col>
      </Row>
    );
  }

  if (view.kind === "audit") {
    return (
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <AuditChangesCard audit={audit} />
        </Col>
        <Col xs={24} lg={12}>
          <SimpleRuntimeCard runtime={runtime} />
        </Col>
        <Col xs={24} lg={12}>
          <UnavailableDomainCard
            id="quality"
            title="质控整改"
            description="质控整改证据将在 D4/D5 完成后按权限展示。"
          />
        </Col>
      </Row>
    );
  }

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={12}>
        <SystemHealthCard runtime={runtime} />
      </Col>
      <Col xs={24} lg={12}>
        <AuditChangesCard audit={audit} />
      </Col>
      <Col xs={24} lg={12}>
        <UnavailableDomainCard
          id="quality"
          title="质控整改"
          description="质控整改摘要将在 D4 域上线后按责任范围展示。"
        />
      </Col>
      <Col xs={24} lg={12}>
        <UnavailableDomainCard
          id="value"
          title="价值指标"
          description="价值成效摘要将在真实指标域上线后展示。"
        />
      </Col>
    </Row>
  );
}

function SystemHealthCard({ runtime }: { runtime: SourceQuery<RuntimeOperationsSnapshot> }) {
  return (
    <SourceCard id="system" title="系统健康" query={runtime}>
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

function SimpleRuntimeCard({ runtime }: { runtime: SourceQuery<RuntimeOperationsSnapshot> }) {
  return (
    <SourceCard id="runtime-simple" title="整体运行" query={runtime}>
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

function ProviderCard({ runtime }: { runtime: SourceQuery<RuntimeOperationsSnapshot> }) {
  return (
    <SourceCard id="provider" title="Provider 连通" query={runtime}>
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

function KnowledgeSyncCard({ runtime }: { runtime: SourceQuery<RuntimeOperationsSnapshot> }) {
  return (
    <SourceCard id="knowledge" title="知识同步" query={runtime}>
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

function AuditChangesCard({ audit }: { audit: SourceQuery<AuditEventRow[]> }) {
  return (
    <SourceCard id="audit" title="最近变化" query={audit} empty={audit.data?.length === 0}>
      {(events) => (
        <List
          size="small"
          dataSource={events.slice(0, 3)}
          locale={{ emptyText: "暂无审计事件" }}
          renderItem={(event) => (
            <List.Item>
              <Space direction="vertical" size={0}>
                <Text>{event.action}</Text>
                <Text type="secondary">
                  {event.user ?? "系统"} · {formatTime(event.occurredAt)}
                </Text>
              </Space>
            </List.Item>
          )}
        />
      )}
    </SourceCard>
  );
}

function TodoCard() {
  return (
    <Card data-testid="workbench-card-todo" title="我的待办" extra={<Tag>暂无</Tag>}>
      <PageState
        state="empty"
        title="当前组织暂无待办"
        description="当前组织暂无待办，可查看配置包或切换组织。"
      />
    </Card>
  );
}

function UnavailableDomainCard({
  id,
  title,
  description,
}: {
  id: string;
  title: string;
  description: string;
}) {
  return (
    <Card data-testid={`workbench-card-${id}`} title={title} extra={<Tag>该域未启用</Tag>}>
      <Space direction="vertical" size="small">
        <Text strong>该域未启用</Text>
        <Paragraph type="secondary">{description}</Paragraph>
      </Space>
    </Card>
  );
}

function SourceCard<T>({
  id,
  title,
  query,
  empty,
  children,
}: {
  id: string;
  title: string;
  query: SourceQuery<T>;
  empty?: boolean;
  children: (data: T) => ReactNode;
}) {
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
      {children(query.data)}
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

function canOpenWorkbench(profile: SecurityProfile): boolean {
  return (
    profile.menuKeys.includes("workbench") ||
    profile.permissions.some(
      (permission) => permission.code === "menu.workbench" || permission.target === "workbench",
    )
  );
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

  if (["doctor", "nurse", "specialist", "dept-head"].some((code) => codes.has(code))) {
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
