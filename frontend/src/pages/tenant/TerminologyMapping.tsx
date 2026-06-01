import type { Key } from "react";
import { useEffect, useRef, useState } from "react";

import { Tag } from "antd";

import {
  parseSavedExperienceView,
  useLargeListExportJob,
  useSaveView,
  useSavedViews,
  useSecurityProfile,
  useSubmitLargeListExport,
  useTerminologyMappings,
  type TermMapping,
} from "@/shared/api/hooks";
import { canAccessRoute, findRouteByPath } from "@/shared/config/routes";
import { AsyncExportAction } from "@/shared/ui/AsyncExportAction";
import { EvidenceDetailDrawer, type EvidenceDetailSection } from "@/shared/ui/EvidenceDetailDrawer";
import { ExperienceFilterBar } from "@/shared/ui/ExperienceFilterBar";
import { PageExperienceShell } from "@/shared/ui/PageExperienceShell";
import { PageState } from "@/shared/ui/PageState";
import type { PageStateKind } from "@/shared/ui/PageState.contract";
import { ServerDataTable } from "@/shared/ui/ServerDataTable";
import type {
  ExperienceColumn,
  ExperienceFilterValue,
  ExperiencePageRequest,
  ExperienceViewSnapshot,
  RouteExperience,
} from "@/shared/ui/experienceTypes";
import { buildAsyncExportRequest, normalizePageResponse } from "@/shared/ui/experienceView";

const VIEW_KEY = "terminology.mapping";
const PAGE_SIZE = 20;
const route = findRouteByPath("/terminology/mapping");

if (!route?.experience) {
  throw new Error("字典映射页面缺少体验声明");
}

const PAGE_META: { title: string; experience: RouteExperience } = {
  title: route.title,
  experience: route.experience,
};

const DEFAULT_REQUEST: ExperiencePageRequest = {
  pageNumber: 1,
  pageSize: PAGE_SIZE,
  sortBy: "updatedAt",
  sortOrder: "desc",
  filters: {},
};

const STATUS_COLOR: Record<TermMapping["status"], string> = {
  CONFIRMED: "green",
  DRAFT: "orange",
  SUPERSEDED: "blue",
  ROLLED_BACK: "red",
};

const STATUS_LABEL: Record<TermMapping["status"], string> = {
  CONFIRMED: "已确认",
  DRAFT: "草稿",
  SUPERSEDED: "已替换",
  ROLLED_BACK: "已回滚",
};

const RISK_COLOR: Record<TermMapping["riskLevel"], string> = {
  HIGH: "red",
  MEDIUM: "orange",
  LOW: "blue",
};

const RISK_LABEL: Record<TermMapping["riskLevel"], string> = {
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
};

const tableColumns: Array<ExperienceColumn<TermMapping>> = [
  { key: "sourceSystem", title: "来源系统", dataIndex: "sourceSystem", always: true },
  { key: "category", title: "类别", dataIndex: "category" },
  {
    key: "riskLevel",
    title: "风险等级",
    dataIndex: "riskLevel",
    render: (value) => {
      const risk = value as TermMapping["riskLevel"];
      return <Tag color={RISK_COLOR[risk]}>{RISK_LABEL[risk]}</Tag>;
    },
  },
  {
    key: "confidence",
    title: "置信度",
    dataIndex: "confidence",
    render: (value) => `${((value as number) * 100).toFixed(1)}%`,
  },
  {
    key: "status",
    title: "状态",
    dataIndex: "status",
    render: (value) => {
      const status = value as TermMapping["status"];
      return <Tag color={STATUS_COLOR[status]}>{STATUS_LABEL[status]}</Tag>;
    },
  },
  { key: "updatedAt", title: "更新时间", dataIndex: "updatedAt" },
];
const DEFAULT_VISIBLE_COLUMNS = tableColumns.map((column) => column.key);

function getFilterValue(
  filters: readonly ExperienceFilterValue[],
  key: string,
): string | undefined {
  const value = filters.find((filter) => filter.key === key)?.value;
  return typeof value === "string" ? value : undefined;
}

function buildFilterRecord(filters: readonly ExperienceFilterValue[]): Record<string, unknown> {
  return Object.fromEntries(
    filters
      .filter((filter) => filter.value !== undefined)
      .map((filter) => [filter.key, filter.value]),
  );
}

function detailSections(mapping?: TermMapping): EvidenceDetailSection[] {
  if (!mapping) return [];

  return [
    {
      key: "summary",
      title: "映射摘要",
      items: [
        { label: "状态", value: STATUS_LABEL[mapping.status] },
        { label: "风险等级", value: RISK_LABEL[mapping.riskLevel] },
        { label: "置信度", value: `${(mapping.confidence * 100).toFixed(1)}%` },
      ],
    },
    {
      key: "source",
      title: "来源与证据",
      items: [
        { label: "来源系统", value: mapping.sourceSystem },
        { label: "类别", value: mapping.category },
        { label: "证据", value: mapping.evidenceText ?? "暂无补充证据" },
        { label: "确认人", value: mapping.confirmedBy ?? "尚未确认" },
        { label: "确认时间", value: mapping.confirmedAt ?? "尚未确认" },
      ],
    },
    {
      key: "expert",
      title: "技术字段",
      items: [
        { label: "映射 ID", value: mapping.id, expertOnly: true },
        { label: "院内编码 ID", value: mapping.localTermId, expertOnly: true },
        { label: "标准编码 ID", value: mapping.standardTermId, expertOnly: true },
      ],
    },
  ];
}

export default function TerminologyMapping() {
  const savedViews = useSavedViews(VIEW_KEY);
  const saveView = useSaveView();
  const submitExport = useSubmitLargeListExport();
  const pollExport = useLargeListExportJob();
  const savedViewApplied = useRef(false);
  const [filters, setFilters] = useState<ExperienceFilterValue[]>([]);
  const [request, setRequest] = useState<ExperiencePageRequest>(DEFAULT_REQUEST);
  const [visibleColumnKeys, setVisibleColumnKeys] =
    useState<readonly string[]>(DEFAULT_VISIBLE_COLUMNS);
  const [expertMode, setExpertMode] = useState(false);
  const [selectionSnapshot, setSelectionSnapshot] = useState<{
    selectedRowKeys: Key[];
    rowCount: number;
  }>();
  const [selectedMapping, setSelectedMapping] = useState<TermMapping>();

  const security = useSecurityProfile();
  const query = useTerminologyMappings({
    page: request.pageNumber,
    size: request.pageSize,
    sort:
      request.sortBy && request.sortOrder ? `${request.sortBy},${request.sortOrder}` : undefined,
    status: getFilterValue(filters, "status") as TermMapping["status"] | undefined,
    sourceSystem: getFilterValue(filters, "sourceSystem"),
    keyword: getFilterValue(filters, "keyword"),
  });

  useEffect(() => {
    if (savedViewApplied.current || !savedViews.data || savedViews.data.length === 0) {
      return;
    }
    const savedSnapshot = parseSavedExperienceView(
      savedViews.data.find((view) => view.defaultView) ?? savedViews.data[0],
    );
    if (!savedSnapshot) {
      savedViewApplied.current = true;
      return;
    }
    setFilters([...savedSnapshot.filters]);
    setRequest(savedSnapshot.pageRequest);
    setVisibleColumnKeys(savedSnapshot.visibleColumnKeys);
    setExpertMode(savedSnapshot.expertMode);
    savedViewApplied.current = true;
  }, [savedViews.data]);

  function snapshot(
    nextFilters = filters,
    nextRequest = request,
    nextColumns = visibleColumnKeys,
    nextExpertMode = expertMode,
  ): ExperienceViewSnapshot {
    return {
      viewKey: VIEW_KEY,
      filters: nextFilters,
      pageRequest: nextRequest,
      visibleColumnKeys: nextColumns,
      expertMode: nextExpertMode,
      capturedAt: new Date().toISOString(),
    };
  }

  function updateFilters(nextFilters: ExperienceFilterValue[]) {
    const nextRequest = {
      ...request,
      pageNumber: 1,
      filters: buildFilterRecord(nextFilters),
    };
    setFilters(nextFilters);
    setRequest(nextRequest);
  }

  function updateRequest(nextRequest: ExperiencePageRequest) {
    setRequest(nextRequest);
  }

  function updateExpertMode(enabled: boolean) {
    setExpertMode(enabled);
  }

  function updateColumns(nextSnapshot: ExperienceViewSnapshot) {
    setVisibleColumnKeys(nextSnapshot.visibleColumnKeys);
  }

  function saveCurrentView() {
    void saveView.mutateAsync({
      pageKey: VIEW_KEY,
      viewName: "默认视图",
      snapshot: snapshot(),
      defaultView: true,
    });
  }

  const hasPermission = !security.data || canAccessRoute(route, security.data);
  const canExport =
    security.data?.permissions.some((permission) => permission.code === "list.export") ?? false;
  const items = query.data?.items ?? [];
  let pageState: PageStateKind = "ready";
  if (!hasPermission) pageState = "forbidden";
  else if (query.isLoading) pageState = "loading";
  else if (query.isError) pageState = "error";
  else if (items.length === 0) pageState = "empty";

  const exportRequest = buildAsyncExportRequest({
    resourceType: "TERMINOLOGY_MAPPING",
    requestSnapshot: snapshot(),
    selectedScope: "currentPage",
    selectionSnapshot,
    reason: "导出字典映射核查结果",
  });

  return (
    <PageExperienceShell
      meta={PAGE_META}
      securityProfile={security.data}
      expertMode={expertMode}
      onExpertModeChange={updateExpertMode}
      extras={
        <AsyncExportAction
          enabled
          permissionGranted={canExport}
          request={exportRequest}
          onSubmit={submitExport.mutateAsync}
          onPoll={pollExport.mutateAsync}
        />
      }
    >
      <ExperienceFilterBar
        filters={PAGE_META.experience.defaultFilters}
        value={filters}
        onChange={updateFilters}
        onSaveView={saveCurrentView}
      />
      <PageState
        state={pageState}
        title={pageState === "empty" ? "暂无字典映射条目" : undefined}
        description={pageState === "empty" ? "当前筛选范围内没有可核查的映射条目。" : undefined}
        traceId={query.data?.traceId}
        onRetry={query.refetch}
      >
        {query.data && (
          <ServerDataTable<TermMapping>
            viewKey={VIEW_KEY}
            rowKey="id"
            columns={tableColumns}
            query={normalizePageResponse(query.data)}
            request={request}
            loading={false}
            partial={
              query.data.partial
                ? { ...query.data.partial, onRetryFailures: () => void query.refetch() }
                : undefined
            }
            expertMode={expertMode}
            initialVisibleColumnKeys={visibleColumnKeys}
            onRequestChange={updateRequest}
            onOpenDetail={setSelectedMapping}
            onViewSnapshotChange={updateColumns}
            onSelectionSnapshotChange={setSelectionSnapshot}
          />
        )}
      </PageState>
      <EvidenceDetailDrawer
        open={!!selectedMapping}
        title="字典映射详情"
        expertMode={expertMode}
        sections={detailSections(selectedMapping)}
        traceId={query.data?.traceId}
        onClose={() => setSelectedMapping(undefined)}
      />
    </PageExperienceShell>
  );
}
