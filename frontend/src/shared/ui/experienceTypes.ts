import type { Key, ReactNode } from "react";

export type InterruptionLevel = "none" | "info" | "weak" | "strong";
export type PageRiskLevel = "low" | "medium" | "high";
export type ExperiencePageSize = 20 | 50 | 100;
export const EXPERIENCE_PAGE_SIZE_OPTIONS = [20, 50, 100] as const;
export const MAX_EXPERIENCE_PAGE_SIZE = 100;

export interface ExperienceFilterOption {
  label: string;
  value: string;
}

export interface ExperienceFilterDefinition {
  key: string;
  label: string;
  kind: "select" | "dateRange" | "search";
  placeholder?: string;
  options?: ExperienceFilterOption[];
  optionSource?: "static" | "api" | "routeMeta";
  apiPath?: string;
}

export interface ExperienceFilterValue {
  key: string;
  value: string | readonly [string, string] | undefined;
}

export interface StakeholderViewHint {
  role: string;
  responsibility: string;
  boundary: string;
}

export interface RouteExperience {
  primaryRole: string;
  goal: string;
  defaultView: string;
  defaultFilters: ExperienceFilterDefinition[];
  evidenceDetailContent: string[];
  interruptionLevel: InterruptionLevel;
  evidence: string;
  dataScale: {
    expected: "small" | "large" | "massive";
    pagination: "page" | "cursor";
    exportStrategy: "none" | "disabled" | "async";
  };
  riskLevel: PageRiskLevel;
  stakeholderViews?: StakeholderViewHint[];
}

export interface ExperiencePageRequest {
  pageNumber?: number;
  pageSize: ExperiencePageSize;
  pageToken?: string;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
  filters: Record<string, unknown>;
}

export interface ExperiencePageResponse<T> {
  items: T[];
  pageNumber?: number;
  pageSize: number;
  nextPageToken?: string | null;
  totalEstimate: number;
  hasMore: boolean;
  traceId?: string;
}

export interface ExperienceColumn<T> {
  key: string;
  title: string;
  dataIndex?: keyof T;
  width?: number;
  always?: boolean;
  advancedOnly?: boolean;
  render?: (value: unknown, record: T) => ReactNode;
}

export interface ExperiencePartialResult {
  successCount: number;
  failureCount: number;
  failures: Array<{ key: string; reason: string; retryable: boolean }>;
  onRetryFailures?: () => void;
}

export interface ExperienceViewSnapshot {
  viewKey: string;
  filters: readonly ExperienceFilterValue[];
  pageRequest: ExperiencePageRequest;
  visibleColumnKeys: readonly string[];
  evidenceDetailsEnabled: boolean;
  capturedAt: string;
}

export interface AsyncExportRequest {
  resourceType: string;
  requestSnapshot: ExperienceViewSnapshot;
  selectedScope: "currentPage" | "filteredResult";
  selectionSnapshot?: { selectedRowKeys: readonly Key[]; rowCount: number };
  reason: string;
  idempotencyKey?: string;
  confirmationId?: string;
}

export type ExportJobStatus =
  | "pending"
  | "running"
  | "succeeded"
  | "failed"
  | "expired"
  | "disabled"
  | "forbidden";

export interface AsyncExportJob {
  jobId: string;
  status: ExportJobStatus;
  submittedAt: string;
  submittedBy: string;
  traceId?: string;
  auditId?: string;
  downloadUrl?: string;
  failureReason?: string;
}

export interface AsyncExportActionProps {
  enabled: boolean;
  disabledReason?: string;
  permissionGranted: boolean;
  request: AsyncExportRequest;
  onSubmit?: (request: AsyncExportRequest) => Promise<AsyncExportJob>;
  onPoll?: (jobId: string) => Promise<AsyncExportJob>;
  pollDelayMs?: number;
  buttonLabel?: string;
  buttonAriaLabel?: string;
  modalTitle?: string;
  submitLabel?: string;
}
