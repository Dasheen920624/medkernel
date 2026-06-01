import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiClient } from "./client";
import { isThemeMode, type ThemeMode } from "@/shared/config/theme";
import type {
  AsyncExportJob,
  AsyncExportRequest,
  ExperienceViewSnapshot,
} from "@/shared/ui/experienceTypes";

/**
 * MedKernel v1.0 GA · React Query hooks（按业务域分组）。
 * 与后端 /api/v1/* 路由一一对应。
 *
 * GA-ENG-BASE-09 净化：删除 W3-W7 旧业务 hook，仅保留 engine/* 真接口、
 * compliance/audit/* 与 /security/me、/system/* 合法运行底座 hook，
 * 以及 GA-ENG-API-04 上线后接入的字典映射 hook（业务包装阶段会渐进新增其它 engine hook）。
 */

// ──────────────────────────────────────────
// 身份安全 · 当前用户权限画像
// ──────────────────────────────────────────
export type PermissionDimension = "MENU" | "ACTION" | "DATA" | "ASSET" | "ENVIRONMENT" | string;

export interface SecurityProfile {
  userId: string;
  username: string;
  roles: Array<{
    code: string;
    displayName: string;
    source: string;
    scopeLevel: string | null;
    scopeCode: string | null;
  }>;
  permissions: Array<{
    code: string;
    dimension: PermissionDimension;
    target: string;
    displayName: string;
    risk: "LOW" | "MEDIUM" | "HIGH" | string;
  }>;
  menuKeys: string[];
  environmentKeys: string[];
  dataScope: {
    tenantId: string | null;
    groupId: string | null;
    hospitalId: string | null;
    campusId: string | null;
    siteId: string | null;
    departmentId: string | null;
    specialtyId: string | null;
  };
  mustChangePwd: boolean;
  mfaRequired: boolean;
  mfaBound: boolean;
}

type SecurityProfileEnvelope = {
  data: SecurityProfile;
};

export function useSecurityProfile() {
  return useQuery({
    queryKey: ["security", "me"],
    queryFn: async () => {
      const response = await apiClient.get<SecurityProfileEnvelope>("/security/me");
      return response.data.data;
    },
    retry: false,
  });
}

// ──────────────────────────────────────────
// 合规运维 · 审计日志（BASE-04 已落地）
// ──────────────────────────────────────────
export type AuditEventRow = {
  id: string;
  eventId: string;
  occurredAt: string;
  user: string | null;
  action: string;
  actionCode: string;
  resourceType: string;
  resourceId: string;
  traceId: string | null;
  signature: string | null;
  status: string;
};

type AuditEventsEnvelope = {
  code: string;
  data: { items: AuditEventRow[]; nextCursor: string | null; hasNext: boolean };
};

type AuditSnapshotEnvelope = {
  code: string;
  data: AuditEventRow;
};

export function useAuditEvents() {
  return useQuery({
    queryKey: ["audit", "events"],
    queryFn: async () => {
      const resp = await apiClient.get<AuditEventsEnvelope>("/compliance/audit/events");
      return resp.data.data?.items ?? [];
    },
  });
}

export function useAuditSnapshot() {
  return useMutation({
    mutationFn: async (reason: string) => {
      const resp = await apiClient.post<AuditSnapshotEnvelope>("/compliance/audit/snapshot", null, {
        params: { reason },
      });
      return resp.data.data;
    },
  });
}

// ──────────────────────────────────────────
// 系统 · Health probe
// ──────────────────────────────────────────
export interface RuntimeFeatureFlag {
  key: string;
  displayName: string;
  enabled: boolean;
  risk: "LOW" | "MEDIUM" | "HIGH" | string;
  owner: string;
  description: string;
  source?: string | null;
  warning?: string | null;
}

export interface RuntimeDependencyStatus {
  key: string;
  displayName: string;
  status: "UP" | "DEGRADED" | "NOT_CONNECTED" | "MODEL_DISABLED" | string;
  detail: string;
}

export interface RuntimeBackupReadiness {
  enabled: boolean;
  rpo: string;
  rto: string;
  backupScript: string;
  restoreScript: string;
  checksumPolicy: string;
  source?: string | null;
  warning?: string | null;
}

export interface RuntimeDomesticProfile {
  targetOs: string;
  targetJdk: string;
  databaseVendors: string[];
  cryptoAlgorithms: string[];
  evidence: string;
}

export interface RuntimeOperationsSnapshot {
  serviceName: string;
  environment: string;
  deploymentMode: string;
  databaseDialect: string;
  migrationLocation: string;
  activeProfiles: string[];
  healthStatus: "UP" | "DOWN" | "OUT_OF_SERVICE" | "UNKNOWN" | string;
  featureFlags: RuntimeFeatureFlag[];
  dependencies: RuntimeDependencyStatus[];
  backup: RuntimeBackupReadiness;
  domesticProfile: RuntimeDomesticProfile;
  generatedAt: string;
}

type RuntimeOperationsEnvelope = {
  data: RuntimeOperationsSnapshot;
};

export function useRuntimeOperations() {
  return useQuery({
    queryKey: ["system", "operations"],
    queryFn: async () => {
      const response = await apiClient.get<RuntimeOperationsEnvelope>("/system/operations");
      return response.data.data;
    },
    refetchInterval: 30_000,
  });
}

export function useSystemRuntime() {
  return useQuery({
    queryKey: ["system", "runtime"],
    queryFn: async () => (await apiClient.get("/system/runtime")).data as Record<string, unknown>,
    refetchInterval: 30_000,
  });
}

// ──────────────────────────────────────────
// 字典映射 · GA-ENG-API-04 已上线（engine/terminology）
// ──────────────────────────────────────────
export interface TermMapping {
  id: number;
  tenantId: string;
  localTermId: number;
  standardTermId: number;
  sourceSystem: string;
  category: string;
  confidence: number;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  status: "DRAFT" | "CONFIRMED" | "SUPERSEDED" | "ROLLED_BACK";
  evidenceText?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  hasNext: boolean;
  totalEstimated: boolean;
  traceId?: string;
  partial?: {
    successCount: number;
    failureCount: number;
    failures: Array<{ key: string; reason: string; retryable: boolean }>;
  };
}

export interface TerminologyMappingsParams {
  page?: number;
  size?: number;
  sort?: string;
  sourceSystem?: string;
  category?: string;
  status?: TermMapping["status"];
  keyword?: string;
}

export function useTerminologyMappings(params?: TerminologyMappingsParams) {
  return useQuery({
    queryKey: ["terminology", "mappings", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<TermMapping> }>(
        "/engine/terminology/mappings",
        { params },
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 产品体验底座 · 保存视图与异步导出（BASE-08）
// ──────────────────────────────────────────
export interface SavedExperienceView {
  savedViewId: string;
  pageKey: string;
  viewName: string;
  definitionJson: string;
  defaultView: boolean;
  version: number;
  updatedAt: string;
  updatedBy: string;
}

export interface SaveExperienceViewPayload {
  pageKey: string;
  viewName: string;
  snapshot: ExperienceViewSnapshot;
  defaultView: boolean;
}

type SavedViewsEnvelope = {
  data: SavedExperienceView[];
};

type SavedViewEnvelope = {
  data: SavedExperienceView;
};

export interface ThemePreferenceResponse {
  mode: ThemeMode;
  version: number;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

type ThemePreferenceEnvelope = {
  data: ThemePreferenceResponse;
};

type LargeListExportSubmitEnvelope = {
  data: {
    jobId: string;
    status: string;
    message: string;
  };
};

type LargeListExportJobEnvelope = {
  data: {
    jobId: string;
    status: string;
    createdAt: string;
    createdBy: string;
    traceId?: string | null;
    auditId?: string | null;
    errorMessage?: string | null;
  };
};

export async function fetchSavedViews(pageKey: string): Promise<SavedExperienceView[]> {
  const { data } = await apiClient.get<SavedViewsEnvelope>("/experience/saved-views", {
    params: { pageKey },
  });
  return data.data ?? [];
}

export async function saveExperienceViewSnapshot(
  payload: SaveExperienceViewPayload,
): Promise<SavedExperienceView> {
  const { data } = await apiClient.put<SavedViewEnvelope>("/experience/saved-views", {
    pageKey: payload.pageKey,
    viewName: payload.viewName,
    definitionJson: JSON.stringify(payload.snapshot),
    defaultView: payload.defaultView,
  });
  return data.data;
}

export async function fetchThemePreference(): Promise<ThemePreferenceResponse> {
  const { data } = await apiClient.get<ThemePreferenceEnvelope>("/experience/theme-preference");
  return data.data;
}

export async function saveThemePreference(mode: ThemeMode): Promise<ThemePreferenceResponse> {
  if (!isThemeMode(mode)) {
    throw new Error("不支持的主题模式");
  }
  const { data } = await apiClient.put<ThemePreferenceEnvelope>("/experience/theme-preference", {
    mode,
  });
  return data.data;
}

export function parseSavedExperienceView(
  view?: SavedExperienceView,
): ExperienceViewSnapshot | null {
  if (!view) return null;
  try {
    return JSON.parse(view.definitionJson) as ExperienceViewSnapshot;
  } catch {
    return null;
  }
}

export function useSavedViews(pageKey: string) {
  return useQuery({
    queryKey: ["experience", "saved-views", pageKey],
    queryFn: () => fetchSavedViews(pageKey),
    enabled: Boolean(pageKey),
    retry: false,
  });
}

export function useSaveView() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: saveExperienceViewSnapshot,
    onSuccess: (view) => {
      void queryClient.invalidateQueries({
        queryKey: ["experience", "saved-views", view.pageKey],
      });
    },
  });
}

export function useThemePreference(enabled = true) {
  return useQuery({
    queryKey: ["experience", "theme-preference"],
    queryFn: fetchThemePreference,
    enabled,
    retry: false,
  });
}

export function useSaveThemePreference() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: saveThemePreference,
    onSuccess: (preference) => {
      queryClient.setQueryData(["experience", "theme-preference"], preference);
    },
  });
}

export async function submitLargeListExport(request: AsyncExportRequest): Promise<AsyncExportJob> {
  const idempotencyKey = request.idempotencyKey ?? crypto.randomUUID();
  const { data } = await apiClient.post<LargeListExportSubmitEnvelope>(
    "/large-lists/exports",
    {
      resourceType: request.resourceType,
      filters: exportFilters(request),
      selectedScope: toBackendExportScope(request.selectedScope),
      idempotencyKey,
    },
    { headers: { "Idempotency-Key": idempotencyKey } },
  );
  return {
    jobId: data.data.jobId,
    status: toExportJobStatus(data.data.status),
    submittedAt: new Date().toISOString(),
    submittedBy: "",
  };
}

export async function fetchLargeListExportJob(jobId: string): Promise<AsyncExportJob> {
  const { data } = await apiClient.get<LargeListExportJobEnvelope>(`/large-lists/exports/${jobId}`);
  const job = data.data;
  return {
    jobId: job.jobId,
    status: toExportJobStatus(job.status),
    submittedAt: job.createdAt,
    submittedBy: job.createdBy,
    traceId: job.traceId ?? undefined,
    auditId: job.auditId ?? undefined,
    failureReason: job.errorMessage ?? undefined,
    downloadUrl:
      job.status === "SUCCESS"
        ? `/medkernel/api/v1/large-lists/exports/${job.jobId}/download`
        : undefined,
  };
}

export function useSubmitLargeListExport() {
  return useMutation({ mutationFn: submitLargeListExport });
}

export function useLargeListExportJob() {
  return useMutation({ mutationFn: fetchLargeListExportJob });
}

function exportFilters(request: AsyncExportRequest): Record<string, string> {
  const fromRequest = Object.entries(request.requestSnapshot.pageRequest.filters ?? {})
    .filter(([, value]) => typeof value === "string" && value.length > 0)
    .map(([key, value]) => [key, value as string]);
  const fromFilterBar = request.requestSnapshot.filters
    .filter((filter) => typeof filter.value === "string" && filter.value.length > 0)
    .map((filter) => [filter.key, filter.value as string]);
  return Object.fromEntries([...fromRequest, ...fromFilterBar]);
}

function toBackendExportScope(scope: AsyncExportRequest["selectedScope"]) {
  return scope === "currentPage" ? "CURRENT_PAGE" : "FILTERED_RESULT";
}

function toExportJobStatus(status: string): AsyncExportJob["status"] {
  switch (status) {
    case "PENDING":
      return "pending";
    case "RUNNING":
      return "running";
    case "SUCCESS":
      return "succeeded";
    case "FAILED":
      return "failed";
    case "EXPIRED":
      return "expired";
    default:
      return "failed";
  }
}

// ──────────────────────────────────────────
// 规则引擎 · GA-ENG-API-05 & GA-ENG-RULE-01
// ──────────────────────────────────────────
export interface RuleDefinition {
  id: number;
  ruleId: string;
  tenantId: string;
  ruleCode: string;
  name: string;
  ruleType: "DRUG_SAFETY" | "INSURANCE_AUDIT" | "CLINICAL_QUALITY" | string;
  authoringMode: "DSL" | "VISUAL" | string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  status: "DRAFT" | "PUBLISHED" | "OFFLINE" | "ARCHIVED" | string;
  activeVersionId: string | null;
  packageVersion?: string | null;
  applicableOrgUnitId?: string | null;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
}

export interface RuleVersion {
  id: number;
  versionId: string;
  ruleId: string;
  versionNo: number;
  sourceRef: string;
  changeSummary: string;
  dslJson: string;
  explanationJson: string;
  status: "DRAFT" | "PUBLISHED" | string;
  publishedAt?: string | null;
  publishedBy?: string | null;
  createdAt: string;
}

export interface RuleTestCase {
  id: number;
  caseId: string;
  ruleId: string;
  versionId: string;
  caseType: "POSITIVE" | "NEGATIVE" | "BOUNDARY" | "CONFLICT" | string;
  inputPayload: string;
  expectedHit: boolean;
  expectedSeverity: "LOW" | "MEDIUM" | "HIGH" | string;
  expectedActionCode: string;
  lastHit?: boolean | null;
  lastStatus?: "PASS" | "FAIL" | "PENDING" | string;
  lastMessage?: string | null;
  lastRunAt?: string | null;
  createdAt: string;
}

export interface RuleDetailResponse {
  definition: RuleDefinition;
  version: RuleVersion;
  testCases: RuleTestCase[];
}

export interface RuleEvaluationItem {
  ruleId: string;
  ruleCode: string;
  ruleName: string;
  hit: boolean;
  severity: "LOW" | "MEDIUM" | "HIGH" | string;
  actionCode: string;
  explanation: string;
}

export interface RuleEvaluateResponse {
  traceId: string;
  executionId: string;
  highestSeverity: "LOW" | "MEDIUM" | "HIGH" | string;
  items: RuleEvaluationItem[];
}

export interface DiagnoseResponse {
  executionId: string;
  traceId: string;
  ruleId: string;
  inputPayloadSummary: string;
  explanationSnapshot: string;
  confidenceScore?: number;
  riskLevel?: "LOW" | "MEDIUM" | "HIGH";
  statusHistory: Array<{
    status: string;
    changedAt: string;
    changedBy: string;
    summary: string;
  }>;
}

export interface RuleFilterParams {
  status?: string;
  ruleType?: string;
  riskLevel?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export function useRuleDefinitions(params?: RuleFilterParams) {
  return useQuery({
    queryKey: ["rules", "definitions", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<RuleDefinition> }>(
        "/engine/rules",
        { params },
      );
      return data.data;
    },
  });
}

export function useRuleDetail(ruleId: string) {
  return useQuery({
    queryKey: ["rules", "detail", ruleId],
    queryFn: async () => {
      if (!ruleId) return null;
      const { data } = await apiClient.get<{ data: RuleDetailResponse }>(`/engine/rules/${ruleId}`);
      return data.data;
    },
    enabled: !!ruleId,
  });
}

export function useCreateRule() {
  return useMutation({
    mutationFn: async (payload: {
      ruleCode: string;
      name: string;
      ruleType: string;
      authoringMode: string;
      riskLevel: string;
      sourceRef: string;
      changeSummary: string;
      dslJson: string;
      explanationJson: string;
    }) => {
      const { data } = await apiClient.post<{ data: { ruleId: string } }>("/engine/rules", payload);
      return data.data;
    },
  });
}

export function useAddTestCase(ruleId: string) {
  return useMutation({
    mutationFn: async (payload: {
      caseType: string;
      inputPayload: string;
      expectedHit: boolean;
      expectedSeverity: string;
      expectedActionCode: string;
    }) => {
      const { data } = await apiClient.post<{ data: RuleTestCase }>(
        `/engine/rules/${ruleId}/test-cases`,
        payload,
      );
      return data.data;
    },
  });
}

export function useSimulateRule(ruleId: string) {
  return useMutation({
    mutationFn: async (payload: { inputPayload: string }) => {
      const { data } = await apiClient.post<{ data: RuleEvaluationItem }>(
        `/engine/rules/${ruleId}/simulate`,
        payload,
      );
      return data.data;
    },
  });
}

export function usePublishRule() {
  return useMutation({
    mutationFn: async (ruleId: string) => {
      const { data } = await apiClient.post<{ data: { versionId: string } }>(
        `/engine/rules/${ruleId}/publish`,
      );
      return data.data;
    },
  });
}

export function useEvaluateRules() {
  return useMutation({
    mutationFn: async (payload: {
      triggerPoint: string;
      patientId?: string;
      payloadJson: string;
    }) => {
      const { data } = await apiClient.post<{ data: RuleEvaluateResponse }>(
        "/engine/rules/evaluate",
        payload,
      );
      return data.data;
    },
  });
}

export function useRuleExecutionDiagnose(executionId: string) {
  return useQuery({
    queryKey: ["rules", "diagnose", executionId],
    queryFn: async () => {
      if (!executionId) return null;
      const { data } = await apiClient.get<{ data: DiagnoseResponse }>(
        `/engine/rules/executions/${executionId}/diagnose`,
      );
      return data.data;
    },
    enabled: !!executionId,
  });
}

// ==================== Pathway 引擎相关的实体及 DTO 契约 ====================

export type SpecialtyPackageStatus = "DRAFT" | "PUBLISHED" | "OFFLINE";
export type PathwayTemplateStatus = "DRAFT" | "PUBLISHED" | "OFFLINE";
export type PathwayTemplateLevel = "CLINICAL" | "BUSINESS" | string;
export type PathwayNodeType = "START" | "PROCESS" | "BRANCH" | "STOP" | string;
export type PathwayEdgeType = "STANDARD" | "CONDITIONAL" | "EXCEPTION" | "VARIANCE" | string;
export type PatientPathwayStatus = "ACTIVE" | "COMPLETED" | "EXITED" | string;
export type ClinicalClockStatus = "RUNNING" | "COMPLETED" | "OVERDUE" | string;
export type VarianceType =
  | "MEDICAL"
  | "PATIENT_REASON"
  | "RESOURCE_REASON"
  | "DOCTOR_CHOICE"
  | "SYSTEM_REASON";
export type PathwayAdvanceEventType = "COMPLETE" | "VARIANCE" | "EXIT";

export interface SpecialtyPackage {
  id?: number;
  packageId: string;
  packageCode: string;
  diseaseCode: string;
  name: string;
  packageVersion: string;
  status: SpecialtyPackageStatus;
  sourceRef: string;
  description: string;
  publishedAt?: string;
  publishedBy?: string;
  createdAt?: string;
  createdBy?: string;
  traceId?: string;
}

export interface PathwayTemplate {
  id?: number;
  templateId: string;
  packageId: string;
  templateCode: string;
  name: string;
  diseaseCode: string;
  templateVersion: number;
  templateLevel: PathwayTemplateLevel;
  status: PathwayTemplateStatus;
  startNodeCode?: string;
  sourceRef: string;
  description: string;
  entryCriteriaJson?: string;
  exitCriteriaJson?: string;
  createdAt?: string;
  createdBy?: string;
  traceId?: string;
}

export interface PathwayNode {
  id?: number;
  nodeId: string;
  templateId: string;
  nodeCode: string;
  name: string;
  nodeType: PathwayNodeType;
  sortOrder: number;
  responsibleRole?: string;
  dependencyJson?: string;
  timeWindowMinutes?: number;
  terminalFlag: boolean;
  configJson?: string;
  createdAt?: string;
  traceId?: string;
}

export interface PathwayEdge {
  id?: number;
  edgeId: string;
  templateId: string;
  edgeCode: string;
  fromNodeCode: string;
  toNodeCode: string;
  edgeType: PathwayEdgeType;
  conditionJson?: string;
  priority: number;
  createdAt?: string;
  traceId?: string;
}

export interface SpecialtyMetricBinding {
  id?: number;
  bindingId: string;
  templateId: string;
  nodeCode: string;
  metricCode: string;
  createdAt?: string;
}

export interface SpecialtyPackageResponse {
  packageId: string;
  status: SpecialtyPackageStatus;
  traceId: string;
}

export interface PathwayTemplateDetailResponse {
  template: PathwayTemplate;
  nodes: PathwayNode[];
  edges: PathwayEdge[];
  metricBindings: SpecialtyMetricBinding[];
  traceId: string;
}

export interface PathwayTemplatePublishResponse {
  templateId: string;
  status: PathwayTemplateStatus;
  traceId: string;
}

export interface PathwaySimulationResponse {
  templateId: string;
  simulatedPath: string[];
  traceId: string;
}

export interface PatientPathway {
  id?: number;
  patientPathwayId: string;
  patientId: string;
  encounterId?: string;
  templateId: string;
  currentNodeCode?: string;
  status: PatientPathwayStatus;
  enteredAt?: string;
  completedAt?: string;
  exitedAt?: string;
  exitReason?: string;
  lastEventId?: string;
  createdAt?: string;
  traceId?: string;
}

export interface PathwayVariance {
  id?: number;
  varianceId: string;
  patientPathwayId: string;
  nodeCode: string;
  varianceType: VarianceType;
  reason: string;
  resolutionAction: string;
  continueNodeCode?: string;
  createdAt?: string;
  traceId?: string;
}

export interface ClinicalClock {
  id?: number;
  clockId: string;
  patientPathwayId: string;
  nodeCode: string;
  metricCode?: string;
  startedAt: string;
  dueAt: string;
  completedAt?: string;
  status: ClinicalClockStatus;
  createdAt?: string;
  traceId?: string;
}

export interface PatientPathwayDetailResponse {
  patientPathway: PatientPathway;
  variances: PathwayVariance[];
  clocks: ClinicalClock[];
  traceId: string;
}

export interface PathwayAdvanceResponse {
  patientPathwayId: string;
  status: PatientPathwayStatus;
  traceId: string;
}

// 1. SpecialtyPackage Hooks
export function useSpecialtyPackages(params?: { page?: number; size?: number; sort?: string }) {
  return useQuery({
    queryKey: ["pathways", "packages", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<SpecialtyPackage> }>(
        "/engine/pathways/packages",
        { params },
      );
      return data.data;
    },
  });
}

export function useCreateSpecialtyPackage() {
  return useMutation({
    mutationFn: async (payload: {
      packageCode: string;
      diseaseCode: string;
      name: string;
      packageVersion: string;
      sourceRef: string;
      description: string;
    }) => {
      const { data } = await apiClient.post<{ data: SpecialtyPackageResponse }>(
        "/engine/pathways/packages",
        payload,
      );
      return data.data;
    },
  });
}

// 2. PathwayTemplate Hooks
export function usePathwayTemplates(params?: {
  status?: PathwayTemplateStatus;
  diseaseCode?: string;
  packageId?: string;
  page?: number;
  size?: number;
  sort?: string;
}) {
  return useQuery({
    queryKey: ["pathways", "templates", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<PathwayTemplate> }>(
        "/engine/pathways/templates",
        { params },
      );
      return data.data;
    },
  });
}

export function usePathwayTemplateDetail(templateId: string) {
  return useQuery({
    queryKey: ["pathways", "template-detail", templateId],
    queryFn: async () => {
      if (!templateId) return null;
      const { data } = await apiClient.get<{ data: PathwayTemplateDetailResponse }>(
        `/engine/pathways/templates/${templateId}`,
      );
      return data.data;
    },
    enabled: !!templateId,
  });
}

export function useCreatePathwayTemplate() {
  return useMutation({
    mutationFn: async (payload: {
      packageId: string;
      templateCode: string;
      name: string;
      diseaseCode: string;
      templateLevel: PathwayTemplateLevel;
      sourceRef: string;
      description: string;
      entryCriteriaJson?: string;
      exitCriteriaJson?: string;
      nodes: Array<{
        nodeCode: string;
        name: string;
        nodeType: PathwayNodeType;
        sortOrder: number;
        responsibleRole?: string;
        timeWindowMinutes?: number;
        terminalFlag: boolean;
        configJson?: string;
      }>;
      edges: Array<{
        edgeCode: string;
        fromNodeCode: string;
        toNodeCode: string;
        edgeType: PathwayEdgeType;
        conditionJson?: string;
        priority: number;
      }>;
      metricBindings?: Array<{
        nodeCode: string;
        metricCode: string;
      }>;
    }) => {
      const { data } = await apiClient.post<{ data: PathwayTemplateDetailResponse }>(
        "/engine/pathways/templates",
        payload,
      );
      return data.data;
    },
  });
}

export function usePublishPathwayTemplate() {
  return useMutation({
    mutationFn: async (templateId: string) => {
      const { data } = await apiClient.post<{ data: PathwayTemplatePublishResponse }>(
        `/engine/pathways/templates/${templateId}/publish`,
      );
      return data.data;
    },
  });
}

export function useSimulatePathway(templateId: string) {
  return useMutation({
    mutationFn: async (payload: { startNodeCode?: string; contextJson?: string }) => {
      const { data } = await apiClient.post<{ data: PathwaySimulationResponse }>(
        `/engine/pathways/templates/${templateId}/simulate`,
        payload,
      );
      return data.data;
    },
  });
}

// 3. PatientPathway Hooks
export function useEnterPatientPathway() {
  return useMutation({
    mutationFn: async (payload: {
      patientId: string;
      encounterId?: string;
      templateId: string;
      startNodeCode?: string;
    }) => {
      const { data } = await apiClient.post<{ data: PatientPathwayDetailResponse }>(
        "/engine/pathways/patients",
        payload,
      );
      return data.data;
    },
  });
}

export function usePatientPathwayDetail(patientPathwayId: string) {
  return useQuery({
    queryKey: ["pathways", "patient-detail", patientPathwayId],
    queryFn: async () => {
      if (!patientPathwayId) return null;
      const { data } = await apiClient.get<{ data: PatientPathwayDetailResponse }>(
        `/engine/pathways/patients/${patientPathwayId}`,
      );
      return data.data;
    },
    enabled: !!patientPathwayId,
  });
}

export function useAdvancePatientPathway() {
  return useMutation({
    mutationFn: async (payload: {
      patientPathwayId: string;
      eventType: PathwayAdvanceEventType;
      currentNodeCode?: string;
      requestedNextNodeCode?: string;
      varianceType?: VarianceType;
      varianceReason?: string;
      resolutionAction?: string;
      exitReason?: string;
      eventId?: string;
    }) => {
      const { data } = await apiClient.post<{ data: PathwayAdvanceResponse }>(
        "/engine/pathways/advance",
        payload,
      );
      return data.data;
    },
  });
}

export function usePatientPathwayClocks(patientPathwayId: string) {
  return useQuery({
    queryKey: ["pathways", "patient-clocks", patientPathwayId],
    queryFn: async () => {
      if (!patientPathwayId) return [];
      const { data } = await apiClient.get<{ data: ClinicalClock[] }>(
        `/engine/pathways/${patientPathwayId}/clocks`,
      );
      return data.data;
    },
    enabled: !!patientPathwayId,
  });
}

export function usePatientPathwayDiagnose(patientPathwayId: string) {
  return useQuery({
    queryKey: ["pathways", "patient-diagnose", patientPathwayId],
    queryFn: async () => {
      if (!patientPathwayId) return null;
      const { data } = await apiClient.get<{ data: DiagnoseResponse }>(
        `/engine/pathways/patients/${patientPathwayId}/diagnose`,
      );
      return data.data;
    },
    enabled: !!patientPathwayId,
  });
}

// ==================== 推荐/CDSS 引擎相关的实体及 DTO 契约 ====================

export type RecommendationCardStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "EXPIRED" | string;
export type RecommendationCardType =
  | "DRUG_SAFETY"
  | "INSURANCE_AUDIT"
  | "CLINICAL_QUALITY"
  | string;
export type RecommendationRiskLevel = "LOW" | "MEDIUM" | "HIGH" | string;
export type RecommendationInterruptLevel = "NONE" | "SOFT" | "HARD" | string;
export type RecommendationSourceType = "GUIDELINE" | "LITERATURE" | "REGULATION" | string;
export type RecommendationTriggerStatus = "SUCCESS" | "FAILED" | string;
export type RecommendationFeedbackType = "ACCEPT" | "REJECT" | string;
export type RecommendationFatigueSignalType = "MUTE" | "WARNING" | "BLOCK" | string;

export interface RecommendationCard {
  id?: number;
  cardId: string;
  tenantId: string;
  triggerId: string;
  patientId: string;
  encounterId?: string;
  scenarioCode: string;
  cardType: RecommendationCardType;
  title: string;
  summary: string;
  riskLevel: RecommendationRiskLevel;
  interruptLevel: RecommendationInterruptLevel;
  status: RecommendationCardStatus;
  changeSummary?: string;
  createdAt?: string;
  createdBy?: string;
  traceId?: string;
  // 嵌入与全屏决策终端可选扩展属性
  cardCode?: string;
  severity?: string;
  recommendations?: Array<{
    actionCode: string;
    actionType: string;
    description: string;
  }>;
  evidenceSummary?: string;
}

export interface RecommendationSource {
  id?: number;
  sourceId: string;
  cardId: string;
  sourceType: RecommendationSourceType;
  title: string;
  content: string;
  evidenceLevel?: string;
  authorityScore?: number;
  sourceRef: string;
  createdAt?: string;
}

export interface RecommendationFeedback {
  id?: number;
  feedbackId: string;
  cardId: string;
  feedbackType: RecommendationFeedbackType;
  reasonCode?: string;
  reasonText?: string;
  // 操作者由后端从 RequestContext 取真实登录用户写入；前端不传，仅展示。
  operatorId: string;
  operatorRole?: string;
  createdAt?: string;
}

export interface RecommendationFatigueSignal {
  id?: number;
  signalId: string;
  tenantId: string;
  fatigueKey: string;
  signalType: RecommendationFatigueSignalType;
  triggerCount: number;
  governanceThreshold: number;
  summary?: string;
  createdAt?: string;
}

export interface RecommendationCardDetailResponse {
  card: RecommendationCard;
  sources: RecommendationSource[];
  feedback?: RecommendationFeedback;
  fatigueSignals: RecommendationFatigueSignal[];
  traceId: string;
}

export interface RecommendationTriggerResponse {
  triggerId: string;
  cardCount: number;
  traceId: string;
}

export interface RecommendationFeedbackResponse {
  cardId: string;
  status: RecommendationCardStatus;
  traceId: string;
}

// 1. Trigger Hooks
// 契约对齐后端 RecommendationTriggerRequest：triggerCode/triggerType/scenarioCode/inputDigest 必填，
// 可选携带候选卡（首版允许上游直接提交）。后端不会凭患者+病种"生成"卡，故沙箱不提交候选卡时 cardCount=0。
export function useCreateRecommendationTrigger() {
  return useMutation({
    mutationFn: async (payload: {
      triggerCode: string;
      triggerType: string;
      scenarioCode: string;
      inputDigest: string;
      patientId?: string;
      encounterId?: string;
      sourceEventId?: string;
      contextSnapshotId?: string;
      patientPathwayId?: string;
      packageVersion?: string;
      occurredAt?: string;
      candidateCards?: unknown[];
    }) => {
      const { data } = await apiClient.post<{ data: RecommendationTriggerResponse }>(
        "/engine/recommendations/triggers",
        payload,
      );
      return data.data;
    },
  });
}

// 2. Card Hooks
export function useRecommendationCards(
  params?: {
    status?: RecommendationCardStatus;
    riskLevel?: RecommendationRiskLevel;
    scenarioCode?: string;
    patientId?: string;
    page?: number;
    size?: number;
    sort?: string;
  },
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["recommendations", "cards", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<RecommendationCard> }>(
        "/engine/recommendations/cards",
        { params },
      );
      return data.data;
    },
  });
}

export function useRecommendationCardDetail(cardId: string) {
  return useQuery({
    queryKey: ["recommendations", "card-detail", cardId],
    queryFn: async () => {
      if (!cardId) return null;
      const { data } = await apiClient.get<{ data: RecommendationCardDetailResponse }>(
        `/engine/recommendations/cards/${cardId}`,
      );
      return data.data;
    },
    enabled: !!cardId,
  });
}

export function useRecommendationCardSources(cardId: string) {
  return useQuery({
    queryKey: ["recommendations", "card-sources", cardId],
    queryFn: async () => {
      if (!cardId) return [];
      const { data } = await apiClient.get<{ data: RecommendationSource[] }>(
        `/engine/recommendations/cards/${cardId}/sources`,
      );
      return data.data;
    },
    enabled: !!cardId,
  });
}

// 3. Feedback Hook
// 契约对齐后端 RecommendationFeedbackRequest：仅 feedbackType / reasonCode / reasonText / operatorRole；
// 操作者 id 由后端从 RequestContext 取真实登录用户，前端不得伪造 physicianId。
export function useSubmitRecommendationFeedback(cardId: string) {
  return useMutation({
    mutationFn: async (payload: {
      feedbackType: RecommendationFeedbackType;
      reasonCode?: string;
      reasonText?: string;
      operatorRole?: string;
    }) => {
      const { data } = await apiClient.post<{ data: RecommendationFeedbackResponse }>(
        `/engine/recommendations/cards/${cardId}/feedback`,
        payload,
      );
      return data.data;
    },
  });
}

// 4. Fatigue Signal Hooks
export function useRecommendationFatigueSignals(params?: {
  fatigueKey?: string;
  signalType?: RecommendationFatigueSignalType;
  page?: number;
  size?: number;
  sort?: string;
}) {
  return useQuery({
    queryKey: ["recommendations", "fatigue-signals", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<RecommendationFatigueSignal> }>(
        "/engine/recommendations/fatigue-signals",
        { params },
      );
      return data.data;
    },
  });
}

// 5. Diagnose Hook
export function useRecommendationTriggerDiagnose(triggerId: string) {
  return useQuery({
    queryKey: ["recommendations", "trigger-diagnose", triggerId],
    queryFn: async () => {
      if (!triggerId) return null;
      const { data } = await apiClient.get<{ data: DiagnoseResponse }>(
        `/engine/recommendations/triggers/${triggerId}/diagnose`,
      );
      return data.data;
    },
    enabled: !!triggerId,
  });
}

// ==================== 评估质控引擎相关的实体及 DTO 契约 ====================

export type EvaluationIndicatorStatus =
  | "DRAFT"
  | "PENDING_REVIEW"
  | "PUBLISHED"
  | "ACTIVE"
  | "OFFLINE"
  | "ARCHIVED";

export type EvaluationSubjectType =
  | "PATIENT"
  | "MEDICAL_RECORD"
  | "DEPARTMENT"
  | "DOCTOR"
  | "DISEASE"
  | "PATHWAY"
  | "CLAIM"
  | "FOLLOWUP";

export type EvaluationResultLevel = "PASS" | "ATTENTION" | "NON_COMPLIANT" | "CRITICAL";

export type QualityFindingSeverity = "P0" | "P1" | "P2" | "P3";

export type QualityFindingStatus = "NEW" | "ASSIGNED" | "REMEDIATING" | "CLOSED" | "WAIVED";

export type RectificationTaskStatus = "ASSIGNED" | "SUBMITTED" | "RETURNED" | "CLOSED" | "WAIVED";

export type RectificationReviewDecision = "APPROVED" | "RETURNED" | "WAIVED";

export interface EvaluationIndicator {
  id?: number;
  indicatorId: string;
  tenantId: string;
  indicatorCode: string;
  versionNo: number;
  name: string;
  subjectType: EvaluationSubjectType;
  denominatorDefinition?: string;
  numeratorDefinition?: string;
  exclusionDefinition?: string;
  scoringDefinition?: string;
  timeWindow: string;
  organizationScope: string;
  responsibleDepartmentId: string;
  sourceRef: string;
  packageVersion?: string;
  status: EvaluationIndicatorStatus;
  publishedAt?: string;
  publishedBy?: string;
  activatedAt?: string;
  createdAt?: string;
  createdBy?: string;
  traceId?: string;
}

export interface EvaluationResult {
  id?: number;
  resultId: string;
  tenantId: string;
  runId: string;
  indicatorId: string;
  indicatorCode: string;
  indicatorVersion: number;
  subjectType: EvaluationSubjectType;
  subjectRefId: string;
  scoreValue?: number;
  resultLevel: EvaluationResultLevel;
  hitFlag: boolean;
  evidenceSummary: string;
  sourceRef?: string;
  responsibleDepartmentId?: string;
  createdAt?: string;
}

export interface QualityFinding {
  id?: number;
  findingId: string;
  tenantId: string;
  runId: string;
  resultId: string;
  indicatorId: string;
  findingCode: string;
  title: string;
  description: string;
  severity: QualityFindingSeverity;
  status: QualityFindingStatus;
  evidenceSummary: string;
  responsibleDepartmentId?: string;
  dueAt?: string;
  createdAt?: string;
}

export interface RectificationTask {
  id?: number;
  taskId: string;
  tenantId: string;
  findingId: string;
  responsibleDepartmentId: string;
  assigneeUserId?: string;
  status: RectificationTaskStatus;
  dueAt: string;
  rectificationSummary?: string;
  evidenceRef?: string;
  submittedAt?: string;
  submittedBy?: string;
  closedAt?: string;
  createdAt?: string;
}

export interface RectificationReview {
  id?: number;
  reviewId: string;
  tenantId: string;
  findingId: string;
  taskId: string;
  decision: RectificationReviewDecision;
  comments?: string;
  evidenceRef?: string;
  reviewedBy: string;
  reviewedAt: string;
}

export interface QualityFindingDetailResponse {
  finding: QualityFinding;
  task?: RectificationTask;
  reviews: RectificationReview[];
}

export interface EvaluationRunResponse {
  runId: string;
  status: string;
  resultCount: number;
  findingCount: number;
  taskCount: number;
  traceId: string;
}

export interface RectificationResponse {
  taskId: string;
  findingStatus: QualityFindingStatus;
  taskStatus: RectificationTaskStatus;
  traceId: string;
}

export interface RectificationReviewResponse {
  reviewId: string;
  findingStatus: QualityFindingStatus;
  taskStatus: RectificationTaskStatus;
  traceId: string;
}

// 1. Indicator Lifecycle Hooks
export function useEvaluationIndicators(params?: {
  status?: EvaluationIndicatorStatus;
  subjectType?: EvaluationSubjectType;
  indicatorCode?: string;
  page?: number;
  size?: number;
  sort?: string;
}) {
  return useQuery({
    queryKey: ["evaluations", "indicators", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<EvaluationIndicator> }>(
        "/engine/evaluations/indicators",
        { params },
      );
      return data.data;
    },
  });
}

export function useCreateEvaluationIndicator() {
  return useMutation({
    mutationFn: async (payload: {
      indicatorCode: string;
      versionNo: number;
      name: string;
      subjectType: EvaluationSubjectType;
      denominatorDefinition: string;
      numeratorDefinition: string;
      exclusionDefinition?: string;
      scoringDefinition?: string;
      timeWindow: string;
      organizationScope: string;
      responsibleDepartmentId: string;
      sourceRef: string;
      packageVersion?: string;
    }) => {
      const { data } = await apiClient.post<{ data: EvaluationIndicator }>(
        "/engine/evaluations/indicators",
        payload,
      );
      return data.data;
    },
  });
}

export function useSubmitEvaluationIndicator() {
  return useMutation({
    mutationFn: async (indicatorId: string) => {
      const { data } = await apiClient.post<{ data: EvaluationIndicator }>(
        `/engine/evaluations/indicators/${indicatorId}/submit`,
      );
      return data.data;
    },
  });
}

export function usePublishEvaluationIndicator() {
  return useMutation({
    mutationFn: async (indicatorId: string) => {
      const { data } = await apiClient.post<{ data: EvaluationIndicator }>(
        `/engine/evaluations/indicators/${indicatorId}/publish`,
      );
      return data.data;
    },
  });
}

export function useActivateEvaluationIndicator() {
  return useMutation({
    mutationFn: async (indicatorId: string) => {
      const { data } = await apiClient.post<{ data: EvaluationIndicator }>(
        `/engine/evaluations/indicators/${indicatorId}/activate`,
      );
      return data.data;
    },
  });
}

// 2. Evaluation Results Hooks
export function useEvaluationResults(params?: {
  indicatorCode?: string;
  resultLevel?: EvaluationResultLevel;
  responsibleDepartmentId?: string;
  page?: number;
  size?: number;
  sort?: string;
}) {
  return useQuery({
    queryKey: ["evaluations", "results", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<EvaluationResult> }>(
        "/engine/evaluations/results",
        { params },
      );
      return data.data;
    },
  });
}

// 3. Quality Findings & PDCA Rectification Hooks
export function useQualityFindings(params?: {
  severity?: QualityFindingSeverity;
  status?: QualityFindingStatus;
  responsibleDepartmentId?: string;
  page?: number;
  size?: number;
  sort?: string;
}) {
  return useQuery({
    queryKey: ["evaluations", "findings", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<QualityFinding> }>(
        "/engine/evaluations/findings",
        { params },
      );
      return data.data;
    },
  });
}

export function useQualityFindingDetail(findingId: string) {
  return useQuery({
    queryKey: ["evaluations", "finding-detail", findingId],
    queryFn: async () => {
      if (!findingId) return null;
      const { data } = await apiClient.get<{ data: QualityFindingDetailResponse }>(
        `/engine/evaluations/findings/${findingId}`,
      );
      return data.data;
    },
    enabled: !!findingId,
  });
}

export function useSubmitRectification(findingId: string) {
  return useMutation({
    mutationFn: async (payload: {
      request: { rectificationSummary: string; evidenceRef: string };
      idempotencyKey?: string;
    }) => {
      const headers = payload.idempotencyKey
        ? { "Idempotency-Key": payload.idempotencyKey }
        : undefined;
      const { data } = await apiClient.post<{ data: RectificationResponse }>(
        `/engine/evaluations/findings/${findingId}/rectification`,
        payload.request,
        { headers },
      );
      return data.data;
    },
  });
}

export function useReviewRectification(findingId: string) {
  return useMutation({
    mutationFn: async (payload: {
      request: { decision: RectificationReviewDecision; comment: string; evidenceRef?: string };
      idempotencyKey?: string;
    }) => {
      const headers = payload.idempotencyKey
        ? { "Idempotency-Key": payload.idempotencyKey }
        : undefined;
      const { data } = await apiClient.post<{ data: RectificationReviewResponse }>(
        `/engine/evaluations/findings/${findingId}/review`,
        payload.request,
        { headers },
      );
      return data.data;
    },
  });
}

// 4. Quality Audit Run & Sandbox calculations
export function useEvaluateSnapshot() {
  return useMutation({
    mutationFn: async (payload: {
      contextSnapshotId: string;
      scenarioCode: string;
      packageVersion?: string;
    }) => {
      const { data } = await apiClient.post<{ data: EvaluationRunResponse }>(
        "/engine/evaluations/evaluate-snapshot",
        payload,
      );
      return data.data;
    },
  });
}

export function useEvaluationRunDiagnose(runId: string) {
  return useQuery({
    queryKey: ["evaluations", "run-diagnose", runId],
    queryFn: async () => {
      if (!runId) return null;
      const { data } = await apiClient.get<{ data: DiagnoseResponse }>(
        `/engine/evaluations/runs/${runId}/diagnose`,
      );
      return data.data;
    },
    enabled: !!runId,
  });
}

export type ContextSnapshotStatus = "ACTIVE" | "SUPERSEDED" | "REVOKED" | string;

export interface ContextSnapshotSummary {
  snapshotId: string;
  patientId: string;
  encounterId: string;
  status: ContextSnapshotStatus;
  qualityStatus: string;
  createdAt?: string;
}

export function useContextSnapshots(
  params?: {
    patientId?: string;
    encounterId?: string;
    status?: ContextSnapshotStatus;
    page?: number;
    size?: number;
    sort?: string;
  },
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["context", "snapshots", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<ContextSnapshotSummary> }>(
        "/engine/context/snapshots",
        { params },
      );
      return data.data;
    },
  });
}

// ==================== 智能随访引擎相关的实体及 DTO 契约 ====================

export type FollowupPlanStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";
export type FollowupTaskType = "QUESTIONNAIRE" | "EXAM" | "LAB" | "OUTPATIENT";
export type FollowupTaskStatus = "PENDING" | "COMPLETED" | "OVERDUE" | "CANCELLED";
export type FollowupEventType = "ABNORMAL_RETURN" | "RESULT_INFLOW";

export interface FollowupTaskDetailResponse {
  taskId: string;
  taskType: FollowupTaskType;
  dueDate: string;
  status: FollowupTaskStatus;
}

export interface FollowupPlanDetailResponse {
  planId: string;
  tenantId: string;
  patientId: string;
  encounterId: string;
  diseaseCode: string;
  status: FollowupPlanStatus;
  tasks: FollowupTaskDetailResponse[];
}

export interface FollowupPlanGenerateRequest {
  patientId: string;
  encounterId: string;
  pathwayId?: string;
  diseaseCode?: string;
  riskLevel?: string;
  taskTypes: string[];
}

export interface FollowupQuestionnaireSubmitRequest {
  taskId: string;
  formData: string; // JSON string
  executorId?: string;
  executorType?: string;
}

export interface FollowupAbnormalReportRequest {
  planId: string;
  eventType: FollowupEventType;
  payload: string; // JSON string or text description
  triggeredBy?: string;
}

export interface FollowupPlansParams {
  patientId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

// 1. 获取随访计划分页
export function useFollowupPlans(params?: FollowupPlansParams) {
  return useQuery({
    queryKey: ["followup", "plans", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<FollowupPlanDetailResponse> }>(
        "/engine/followup/plans",
        { params },
      );
      return data.data;
    },
  });
}

// 2. 智能生成随访计划
export function useGenerateFollowupPlan() {
  return useMutation({
    mutationFn: async (payload: FollowupPlanGenerateRequest) => {
      const { data } = await apiClient.post<{ data: FollowupPlanDetailResponse }>(
        "/engine/followup/plans/generate",
        payload,
      );
      return data.data;
    },
  });
}

// 3. 获取随访计划详情
export function useFollowupPlanDetail(planId: string) {
  return useQuery({
    queryKey: ["followup", "plan-detail", planId],
    queryFn: async () => {
      if (!planId) return null;
      const { data } = await apiClient.get<{ data: FollowupPlanDetailResponse }>(
        `/engine/followup/plans/${planId}`,
      );
      return data.data;
    },
    enabled: !!planId,
  });
}

// 4. 提交问卷并完成任务
export function useSubmitFollowupQuestionnaire() {
  return useMutation({
    mutationFn: async (payload: {
      taskId: string;
      request: FollowupQuestionnaireSubmitRequest;
    }) => {
      const { data } = await apiClient.post<void>(
        `/engine/followup/tasks/${payload.taskId}/questionnaires`,
        payload.request,
      );
      return data;
    },
  });
}

// 5. 上报随访异常事件
export function useReportFollowupAbnormal() {
  return useMutation({
    mutationFn: async (payload: FollowupAbnormalReportRequest) => {
      const { data } = await apiClient.post<void>(
        "/engine/followup/events/report-abnormal",
        payload,
      );
      return data;
    },
  });
}

// ──────────────────────────────────────────
// 智能包发布与同步引擎 (GA-ENG-PKG-01)
// ──────────────────────────────────────────

export interface SyncTarget {
  id: number;
  targetId: string;
  tenantId: string;
  targetName: string;
  targetType: "DIFY" | "NEO4J" | "BUSINESS_DB" | string;
  connectionConfig: string;
  status: "ACTIVE" | "DISABLED" | string;
  createdAt: string;
  createdBy: string;
}

export interface PackageCreateRequest {
  packageCode: string;
  packageVersion: string;
  name: string;
  description: string;
}

export interface PackageResponse {
  packageId: string;
  packageCode: string;
  packageVersion: string;
  name: string;
  status: "DRAFT" | "PUBLISHED" | "ACTIVE" | "OFFLINE" | string;
  createdAt: string;
  createdBy: string;
}

export interface KnowledgePackage {
  id?: number;
  packageId: string;
  tenantId: string;
  packageCode: string;
  packageVersion: string;
  name: string;
  description: string;
  status: "DRAFT" | "PUBLISHED" | "ACTIVE" | "OFFLINE" | string;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  traceId: string;
}

export interface PackageItem {
  id?: number;
  itemId: string;
  tenantId: string;
  packageId: string;
  assetType: "RULE" | "PATHWAY" | "EVALUATION" | "TERMINOLOGY" | "KNOWLEDGE" | "FOLLOWUP" | string;
  assetId: string;
  assetVersion: string;
  createdAt: string;
  createdBy: string;
}

export interface PackageDetailResponse {
  packageId: string;
  packageCode: string;
  packageVersion: string;
  name: string;
  description: string;
  status: string;
  items: PackageItem[];
}

export interface PackageItemRequest {
  assetType: "RULE" | "PATHWAY" | "EVALUATION" | "TERMINOLOGY" | "KNOWLEDGE" | "FOLLOWUP" | string;
  assetId: string;
  assetVersion: string;
}

export interface PackageItemResponse {
  itemId: string;
  packageId: string;
  assetType: string;
  assetId: string;
  assetVersion: string;
}

export interface PackageDiffResponse {
  packageId: string;
  baseVersion: string;
  targetVersion: string;
  addedCount: number;
  updatedCount: number;
  removedCount: number;
  affectedDepartments: string[];
  changes: PackageDiffChange[];
}

export interface PackageDiffChange {
  changeType: "ADDED" | "UPDATED" | "REMOVED" | string;
  assetType: PackageItem["assetType"];
  assetId: string;
  baseVersion: string | null;
  targetVersion: string | null;
}

export interface PackageOfflineImportResponse {
  packageId: string;
  packageCode: string;
  packageVersion: string;
  status: "DRAFT" | "PUBLISHED" | "ACTIVE" | "OFFLINE" | string;
  itemCount: number;
  payloadSha256: string;
}

export interface PackageSyncRequest {
  targetOrgUnitId: string;
  strategy: "GRAYSCALE" | "FULL" | string;
  scopeType: "ALL" | "GROUP" | "HOSPITAL" | "CAMPUS" | "SITE" | "DEPARTMENT" | "SPECIALTY" | string;
  scopeValue: string;
  targetIds: string[];
}

export interface PackageRollbackRequest {
  targetPackageId: string;
  confirmedCurrentVersion: string;
  confirmedTargetVersion: string;
  reason: string;
  confirmedHighRisk: boolean;
}

export interface SyncLogResponse {
  logId: string;
  targetId: string;
  status: "RUNNING" | "SUCCESS" | "FAILED" | string;
  errorCode: string | null;
  errorMessage: string | null;
  retryCount: number;
  syncEvidence: string | null;
}

export interface PackageSyncResponse {
  planId: string;
  packageId: string;
  status: "EXECUTING" | "SUCCESS" | "FAILED" | string;
  logs: SyncLogResponse[];
}

// 1. 动态获取激活同步通道目标列表
export function useSyncTargets() {
  return useQuery({
    queryKey: ["packages", "sync-targets"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SyncTarget[] }>("/engine/packages/sync-targets");
      return data.data ?? [];
    },
  });
}

// 2. 创建知识包草稿
export function useCreatePackage() {
  return useMutation({
    mutationFn: async (payload: PackageCreateRequest) => {
      const { data } = await apiClient.post<{ data: PackageResponse }>("/engine/packages", payload);
      return data.data;
    },
  });
}

// 3. 分页查询知识包列表
export function usePackages(page = 0, size = 10) {
  return useQuery({
    queryKey: ["packages", "list", page, size],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<KnowledgePackage> }>(
        "/engine/packages",
        { params: { page, size } },
      );
      return (
        data.data ?? {
          items: [],
          page: page + 1,
          size,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        }
      );
    },
  });
}

// 4. 获取包详细条目
export function usePackageDetail(packageId: string) {
  return useQuery({
    queryKey: ["packages", "detail", packageId],
    queryFn: async () => {
      if (!packageId) return null;
      const { data } = await apiClient.get<{ data: PackageDetailResponse }>(
        `/engine/packages/${packageId}`,
      );
      return data.data;
    },
    enabled: !!packageId,
  });
}

// 5. 添加资产条目到草稿
export function useAddPackageItem() {
  return useMutation({
    mutationFn: async (payload: { packageId: string; request: PackageItemRequest }) => {
      const { data } = await apiClient.post<{ data: PackageItemResponse }>(
        `/engine/packages/${payload.packageId}/items`,
        payload.request,
      );
      return data.data;
    },
  });
}

// 6. 计算变动差异与临床影响分析
export function useCalculateDiff(packageId: string, basePackageId?: string) {
  return useQuery({
    queryKey: ["packages", "diff", packageId, basePackageId],
    queryFn: async () => {
      if (!packageId) return null;
      const { data } = await apiClient.get<{ data: PackageDiffResponse }>(
        `/engine/packages/${packageId}/diff`,
        { params: { basePackageId } },
      );
      return data.data;
    },
    enabled: !!packageId,
  });
}

export async function downloadPackageDiffExport(packageId: string, basePackageId?: string) {
  const { data } = await apiClient.get<Blob>(`/engine/packages/${packageId}/diff/export`, {
    params: { basePackageId },
    responseType: "blob",
  });
  return data;
}

export async function downloadPackageOfflineExport(packageId: string) {
  const { data } = await apiClient.get<Blob>(`/engine/packages/${packageId}/offline/export`, {
    responseType: "blob",
  });
  return data;
}

export async function importPackageOfflinePackage(offlinePackageJson: string) {
  const { data } = await apiClient.post<{ data: PackageOfflineImportResponse }>(
    "/engine/packages/offline/import",
    { offlinePackageJson },
  );
  return data.data;
}

export function useImportOfflinePackage() {
  return useMutation({
    mutationFn: importPackageOfflinePackage,
  });
}

// 7. 触发多通道物理投影同步发布
export function useSyncPackage() {
  return useMutation({
    mutationFn: async (payload: { packageId: string; request: PackageSyncRequest }) => {
      const { data } = await apiClient.post<{ data: PackageSyncResponse }>(
        `/engine/packages/${payload.packageId}/sync`,
        payload.request,
      );
      return data.data;
    },
  });
}

// 8. 一键快速回滚在用包版本至历史点
export function useRollbackPackage() {
  return useMutation({
    mutationFn: async (payload: { packageId: string; request: PackageRollbackRequest }) => {
      const { data } = await apiClient.post<{ data: PackageResponse }>(
        `/engine/packages/${payload.packageId}/rollback`,
        payload.request,
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 页面嵌入与安全白名单引擎 (GA-ENG-EMBED-01)
// ──────────────────────────────────────────

export interface EmbedLaunchTokenRequest {
  userId: string;
  roleCode: string;
  patientId: string;
  encounterId: string;
  triggerPoint: string;
  expireSeconds?: number;
}

export interface EmbedLaunchTokenResponse {
  token: string;
  expiredAt: string;
  embedUrl: string;
}

export interface EmbedLaunchContextResponse {
  userId: string;
  roleCode: string;
  tenantId: string;
  patientId: string;
  encounterId: string;
  triggerPoint: string;
  active: boolean;
  traceId: string;
}

export interface EmbedFeedbackRequest {
  token: string;
  actionType: "ADOPT" | "REJECT" | string;
  reason?: string;
}

export interface EmbedOriginRequest {
  origin: string;
}

// 1. 生成嵌入一次性启动令牌
export function useGenerateEmbedToken() {
  return useMutation({
    mutationFn: async (payload: EmbedLaunchTokenRequest) => {
      const { data } = await apiClient.post<{ data: EmbedLaunchTokenResponse }>(
        "/engine/embed/launch-tokens",
        payload,
      );
      return data.data;
    },
  });
}

// 2. 兑换启动令牌获取就诊上下文事实
export function useEmbedLaunch(token: string) {
  return useQuery({
    queryKey: ["embed", "launch", token],
    queryFn: async () => {
      if (!token) return null;
      const { data } = await apiClient.get<{ data: EmbedLaunchContextResponse }>(
        "/engine/embed/launch",
        { params: { token } },
      );
      return data.data;
    },
    enabled: !!token,
    retry: false,
  });
}

// 3. 回传记录医师的交互反馈审计
export function useSubmitEmbedFeedback() {
  return useMutation({
    mutationFn: async (payload: EmbedFeedbackRequest) => {
      await apiClient.post<void>("/engine/embed/feedback", payload);
    },
  });
}

// 4. 获取当前租户的安全 Origin 域名白名单列表
export function useEmbedOrigins() {
  return useQuery({
    queryKey: ["embed", "origins"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: string[] }>("/engine/embed/origins");
      return data.data ?? [];
    },
  });
}

// 5. 添加入侵安全防护跨域 Origin 域名白名单
export function useAddEmbedOrigin() {
  return useMutation({
    mutationFn: async (payload: EmbedOriginRequest) => {
      await apiClient.post<void>("/engine/embed/origins", payload);
    },
  });
}

// ─── 大模型能力网关相关的接口定义 (GA-ENG-API-12) ───
export interface ModelCapabilityStatusResponse {
  capabilityCode: string;
  routeStrategy: "DISABLED" | "BASEPLAY" | "LOCAL_MODEL" | "EXTERNAL_MODEL" | string;
  desensitizeStrategy: "DEFAULT" | "MASK_ALL" | "NONE" | string;
  fallbackAvailable: boolean;
  fallbackReason: string;
}

export interface ModelTaskRequest {
  capabilityCode: string;
  inputData: string;
  desensitizeStrategy?: string;
  expectedSchema?: string;
  timeoutSeconds?: number;
}

export interface ModelTaskResponse {
  taskId: string;
  status: "SUCCESS" | "FAILED" | "DEGRADED" | string;
  outputContent: string;
  modelMode: string;
  modelVersion: string;
  promptVersion: string;
  sourceCitations: string;
  confidence: number;
  riskLevel: string;
  fallbackUsed: boolean;
  fallbackReason: string;
  timeCostMs: number;
  traceId: string;
}

export interface ModelPolicyValidateRequest {
  capabilityCode: string;
  routeStrategy: string;
  desensitizeStrategy?: string;
  expectedSchema?: string;
}

export interface ModelPolicyValidateResponse {
  valid: boolean;
  message: string;
  fallbackAvailable: boolean;
}

// 6. 扫描获取当前租户全部可用模型能力状态与降级指标
export function useModelCapabilitiesStatus() {
  return useQuery({
    queryKey: ["model", "capabilities-status"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ModelCapabilityStatusResponse[] }>(
        "/model-capabilities/status",
      );
      return data.data ?? [];
    },
  });
}

// 7. 提交推理或抽取任务，由网关执行路由、数据脱敏与Schema检验
export function useSubmitModelTask() {
  return useMutation({
    mutationFn: async (payload: ModelTaskRequest) => {
      const { data } = await apiClient.post<{ data: ModelTaskResponse }>(
        "/model-capabilities/tasks",
        payload,
      );
      return data.data;
    },
  });
}

// 8. 根据任务ID追溯大模型推理或降级回退任务的详情与审计凭证
export function useModelTask(taskId: string) {
  return useQuery({
    queryKey: ["model", "task", taskId],
    queryFn: async () => {
      if (!taskId) return null;
      const { data } = await apiClient.get<{ data: ModelTaskResponse }>(
        `/model-capabilities/tasks/${taskId}`,
      );
      return data.data;
    },
    enabled: !!taskId,
  });
}

// 9. 重试失败的任务或改为 B0 基线回退
export function useRetryModelTask() {
  return useMutation({
    mutationFn: async (taskId: string) => {
      const { data } = await apiClient.post<{ data: ModelTaskResponse }>(
        `/model-capabilities/tasks/${taskId}/retry`,
      );
      return data.data;
    },
  });
}

// 10. 发布前校验策略的合法性与可用降级判定
export function useValidateModelPolicy() {
  return useMutation({
    mutationFn: async (payload: ModelPolicyValidateRequest) => {
      const { data } = await apiClient.post<{ data: ModelPolicyValidateResponse }>(
        "/model-capabilities/policies/validate",
        payload,
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 第三方对接总线 (GA-ENG-INTEG-01) 核心接口
// ──────────────────────────────────────────

export interface IntegrationAdapter {
  id: number;
  adapterId: string;
  tenantId: string;
  name: string;
  protocolType: "HL7" | "FHIR" | "Webhook" | "REST" | "WebService" | string;
  status: "ACTIVE" | "SUSPENDED" | string;
  configJson: string;
  healthStatus: "HEALTHY" | "UNHEALTHY" | string;
  rttMs: number;
  lastHeartbeatAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface IntegrationWebhookConfig {
  id: number;
  webhookId: string;
  tenantId: string;
  name: string;
  callbackUrl: string;
  secretKey: string;
  eventsSubscribed: string;
  status: "ACTIVE" | "SUSPENDED" | string;
  createdAt: string;
  updatedAt: string;
}

export interface IntegrationMessageLog {
  id: number;
  messageId: string;
  tenantId: string;
  traceId: string;
  direction: "INBOUND" | "OUTBOUND" | string;
  systemName: string;
  protocolType: string;
  payloadSummary: string;
  payload: string;
  status: "SUCCESS" | "FAILED" | "RETRYING" | "DEAD_LETTER" | string;
  retryCount: number;
  maxRetries: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdapterCreatePayload {
  adapterId: string;
  name: string;
  protocolType: string;
  configJson?: string;
}

export interface AdapterUpdatePayload {
  name: string;
  protocolType: string;
  configJson: string;
  status: string;
}

export interface WebhookCreatePayload {
  webhookId: string;
  name: string;
  callbackUrl: string;
  eventsSubscribed: string;
}

export interface WebhookTestPayload {
  webhookId: string;
  payload: string;
}

export interface WebhookSignatureTestResult {
  webhookId: string;
  callbackUrl: string;
  secretKey: string;
  timestamp: number;
  payloadSigned: string;
  signature: string;
  status: string;
}

interface IntegrationEnvelope<T> {
  success: boolean;
  code: string;
  data: T;
}

// 1. 获取适配器目录
export function useIntegrationAdapters() {
  return useQuery({
    queryKey: ["integration", "adapters"],
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<IntegrationAdapter[]>>(
        "/api/v1/engine/integration/adapters",
      );
      return data.data ?? [];
    },
  });
}

// 2. 创建适配器
export function useCreateAdapter() {
  return useMutation({
    mutationFn: async (payload: AdapterCreatePayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationAdapter>>(
        "/api/v1/engine/integration/adapters",
        payload,
      );
      return data.data;
    },
  });
}

// 3. 更新适配器
export function useUpdateAdapter() {
  return useMutation({
    mutationFn: async ({
      adapterId,
      payload,
    }: {
      adapterId: string;
      payload: AdapterUpdatePayload;
    }) => {
      const { data } = await apiClient.put<IntegrationEnvelope<IntegrationAdapter>>(
        `/api/v1/engine/integration/adapters/${adapterId}`,
        payload,
      );
      return data.data;
    },
  });
}

// 4. 自检测心跳自检体检
export function usePingAdapter() {
  return useMutation({
    mutationFn: async (adapterId: string) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationAdapter>>(
        `/api/v1/engine/integration/adapters/${adapterId}/ping`,
      );
      return data.data;
    },
  });
}

// 5. 获取 Webhook 订阅配置
export function useWebhooks() {
  return useQuery({
    queryKey: ["integration", "webhooks"],
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<IntegrationWebhookConfig[]>>(
        "/api/v1/engine/integration/webhooks",
      );
      return data.data ?? [];
    },
  });
}

// 6. 创建 Webhook
export function useCreateWebhook() {
  return useMutation({
    mutationFn: async (payload: WebhookCreatePayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationWebhookConfig>>(
        "/api/v1/engine/integration/webhooks",
        payload,
      );
      return data.data;
    },
  });
}

// 7. Webhook 签名生成与双向测试
export function useTestWebhookSignature() {
  return useMutation({
    mutationFn: async (payload: WebhookTestPayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<WebhookSignatureTestResult>>(
        "/api/v1/engine/integration/webhooks/test",
        payload,
      );
      return data.data;
    },
  });
}

// 8. 获取重试死信队列流日志 (服务端分页)
export function useIntegrationLogs(page: number, size: number) {
  return useQuery({
    queryKey: ["integration", "logs", page, size],
    queryFn: async () => {
      const { data } = await apiClient.get<
        IntegrationEnvelope<{ items: IntegrationMessageLog[]; total: number }>
      >("/api/v1/engine/integration/logs", {
        params: { page, size },
      });
      return data.data ?? { items: [], total: 0 };
    },
  });
}

// 9. 触发一键重试消息发送
export function useRetryMessage() {
  return useMutation({
    mutationFn: async (messageId: string) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationMessageLog>>(
        `/api/v1/engine/integration/logs/${messageId}/retry`,
      );
      return data.data;
    },
  });
}

// 10. 删除日志记录 (已解决 / 已补偿)
export function useDeleteMessage() {
  return useMutation({
    mutationFn: async (messageId: string) => {
      const { data } = await apiClient.delete<IntegrationEnvelope<void>>(
        `/api/v1/engine/integration/logs/${messageId}`,
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 合规可信证据链引擎 (GA-ENG-EVID-01) 核心接口
// ──────────────────────────────────────────

export interface EvidenceSnapshot {
  id: number;
  evidenceId: string;
  tenantId: string;
  traceId: string;
  evidenceType: string;
  action: string;
  subjectType: string;
  subjectId: string;
  evidenceSummary: string;
  payloadSnapshot: string;
  payloadHash: string;
  isValid: boolean;
  createdAt: string;
  createdBy: string;
}

export interface EvidenceVerifyResult {
  evidenceId: string;
  isValid: boolean;
  calculatedHash: string;
  storedHash: string;
}

export interface EvidenceCreatePayload {
  evidenceId: string;
  traceId?: string;
  evidenceType: string;
  action: string;
  subjectType: string;
  subjectId: string;
  evidenceSummary: string;
  payloadSnapshot: string;
}

export interface EvidenceExportResult {
  archiveHash: string;
  status: "COMPLETED" | "PROCESSING" | string;
}

// 1. 分页检索证据快照列表
export function useEvidences(params: {
  keyword?: string;
  evidenceType?: string;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ["evidence", "snapshots", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<EvidenceSnapshot> }>(
        "/compliance/evidence/snapshots",
        { params },
      );
      return data.data ?? { items: [], total: 0 };
    },
  });
}

// 2. 根据全局唯一证据 ID 查询快照详情
export function useEvidenceById(evidenceId: string) {
  return useQuery({
    queryKey: ["evidence", "snapshot", evidenceId],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: EvidenceSnapshot }>(
        `/compliance/evidence/snapshots/${evidenceId}`,
      );
      return data.data;
    },
    enabled: !!evidenceId,
  });
}

// 3. 创建证据快照
export function useCreateEvidence() {
  return useMutation({
    mutationFn: async (payload: EvidenceCreatePayload) => {
      const { data } = await apiClient.post<{ data: EvidenceSnapshot }>(
        "/compliance/evidence/snapshots",
        payload,
      );
      return data.data;
    },
  });
}

// 4. 哈希防篡改验签
export function useVerifyEvidence() {
  return useMutation({
    mutationFn: async (evidenceId: string) => {
      const { data } = await apiClient.post<{ data: EvidenceVerifyResult }>(
        `/compliance/evidence/snapshots/${evidenceId}/verify`,
      );
      return data.data;
    },
  });
}

// 5. 异步打包导出证据链
export function useExportEvidences() {
  return useMutation({
    mutationFn: async (evidenceType?: string) => {
      const { data } = await apiClient.post<{ data: EvidenceExportResult }>(
        "/compliance/evidence/snapshots/export",
        null,
        { params: { evidenceType } },
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 租户品牌个性化定制与生命周期管理 · GA-SVC-PILOT-01
// ──────────────────────────────────────────
export interface Branding {
  id?: number;
  tenantId: string;
  hospitalName: string;
  logoUrl: string;
  themeColor: string;
  expertMode: boolean;
  customBrandingJson: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface SuccessPlan {
  id?: number;
  tenantId: string;
  currentStage: "PREPARATION" | "PILOT" | "ACCEPTANCE" | "PROMOTION" | "RUNNING" | "RENEWAL";
  healthScore: number;
  activatedModules: string;
  activatedPathways: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export function useBranding() {
  return useQuery({
    queryKey: ["platform", "branding"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Branding }>("/platform/branding");
      return data.data;
    },
  });
}

export function useUpdateBranding() {
  return useMutation({
    mutationFn: async (payload: Partial<Branding>) => {
      const { data } = await apiClient.post<{ data: Branding }>("/platform/branding", payload);
      return data.data;
    },
  });
}

export function useSuccessPlan() {
  return useQuery({
    queryKey: ["platform", "success", "lifecycle"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SuccessPlan }>("/platform/success/lifecycle");
      return data.data;
    },
  });
}

export function useTransitionSuccessStage() {
  return useMutation({
    mutationFn: async (nextStage: string) => {
      const { data } = await apiClient.post<{ data: SuccessPlan }>(
        "/platform/success/lifecycle/transition",
        { nextStage },
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 组织单元 · 试点准备（GA-SVC-PILOT-01）
// ──────────────────────────────────────────
export interface OrgUnit {
  id?: string;
  parentId?: string | null;
  tenantId?: string;
  orgPath?: string | null;
  level: "TENANT" | "GROUP" | "HOSPITAL" | "CAMPUS" | "SITE" | "DEPARTMENT" | "SPECIALTY";
  code: string;
  name: string;
  namePinyin?: string | null;
  specialtyId?: string | null;
  status?: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
  createdAt?: string;
  createdBy?: string;
}

export function useOrgUnits(params?: { page?: number; size?: number; sort?: string }) {
  return useQuery({
    queryKey: ["tenant", "org-units", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<OrgUnit> }>("/tenant/org-units", {
        params,
      });
      return data.data;
    },
  });
}

export function useOrgUnitsByLevel(level: string) {
  return useQuery({
    queryKey: ["tenant", "org-units", "by-level", level],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: OrgUnit[] }>("/tenant/org-units/by-level", {
        params: { level },
      });
      return data.data;
    },
    enabled: !!level,
  });
}

export function useCreateOrgUnit() {
  return useMutation({
    mutationFn: async (payload: Partial<OrgUnit>) => {
      const { data } = await apiClient.post<{ data: OrgUnit }>("/tenant/org-units", payload);
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 临床服务包装 · 患者主索引 MPI（GA-SVC-CLINICAL-01）
// ──────────────────────────────────────────
export interface MpiPatient {
  id?: number;
  mpiId: string;
  tenantId: string;
  maskedName: string;
  gender: string;
  age: number;
  idLast4: string;
  mergedCount: number;
  status: "ACTIVE" | "MERGED_INTO" | string;
  mergedIntoMpiId?: string | null;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
}

export interface MpiStatsResponse {
  activeCount: number;
  mergedCount: number;
  averageAge: number;
  genderCounts: Record<string, number>;
}

export function useMpiPatients(
  params: { keyword?: string; status?: string; page?: number; size?: number } = {},
) {
  return useQuery({
    queryKey: ["clinical", "mpi", "patients", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { items: MpiPatient[]; total: number } }>(
        "/clinical/mpi/patients",
        { params },
      );
      return data.data;
    },
  });
}

export function useMpiStats() {
  return useQuery({
    queryKey: ["clinical", "mpi", "stats"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: MpiStatsResponse }>("/clinical/mpi/stats");
      return data.data;
    },
  });
}

export interface MergeMpiPayload {
  sourceMpiId: string;
  targetMpiId: string;
}

export function useMergeMpiPatients() {
  return useMutation({
    mutationFn: async (payload: MergeMpiPayload) => {
      const { data } = await apiClient.post<{ data: void }>(
        "/clinical/mpi/patients/merge",
        payload,
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 身份安全服务包 · 用户角色分配（GA-SVC-COMPLIANCE-01）
// ──────────────────────────────────────────
export interface UserRoleAssignment {
  id?: number;
  tenantId?: string;
  userId: string;
  roleCode: string;
  scopeLevel?: string;
  scopeCode?: string;
  activeFlag?: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export function useUserRoleAssignments() {
  return useQuery({
    queryKey: ["compliance", "user-roles"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: UserRoleAssignment[] }>(
        "/compliance/user-roles",
      );
      return data.data;
    },
  });
}

export function useCreateUserRoleAssignment() {
  return useMutation({
    mutationFn: async (payload: {
      userId: string;
      roleCode: string;
      scopeLevel: string;
      scopeCode: string;
    }) => {
      const { data } = await apiClient.post<{ data: UserRoleAssignment }>(
        "/compliance/user-roles",
        payload,
      );
      return data.data;
    },
  });
}

export function useDeleteUserRoleAssignment() {
  return useMutation({
    mutationFn: async (id: number) => {
      const { data } = await apiClient.delete<{ data: void }>(`/compliance/user-roles/${id}`);
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 首次部署 · 平台种子身份
// ──────────────────────────────────────────
export interface BootstrapStartResult {
  valid: boolean;
  expiresAt: string;
}

export interface BootstrapAdminPayload {
  token: string;
  tenantId?: string;
  username: string;
  password: string;
}

export interface BootstrapAdminResult {
  userId: string;
  tenantId: string;
  username: string;
  roles: string[];
  mustChangePwd: boolean;
}

export interface ChangePasswordPayload {
  oldPassword: string;
  newPassword: string;
}

export interface BootstrapMfaPayload {
  label: string;
}

export interface BootstrapMfaResult {
  mfaBound: boolean;
  recoveryCode: string;
}

export async function checkBootstrapInitToken(token: string) {
  const resp = await apiClient.post<{ data: BootstrapStartResult }>("/bootstrap/init-token", {
    token,
  });
  return resp.data.data;
}

export function useCheckBootstrapInitToken() {
  return useMutation({
    mutationFn: checkBootstrapInitToken,
  });
}

export async function createBootstrapAdmin(payload: BootstrapAdminPayload) {
  const resp = await apiClient.post<{ data: BootstrapAdminResult }>("/bootstrap/password", payload);
  return resp.data.data;
}

export function useCreateBootstrapAdmin() {
  return useMutation({
    mutationFn: createBootstrapAdmin,
  });
}

export async function changePassword(payload: ChangePasswordPayload) {
  await apiClient.post<{ data: void }>("/auth/change-password", payload);
}

export function useChangePassword() {
  return useMutation({
    mutationFn: changePassword,
  });
}

export async function bindBootstrapMfa(payload: BootstrapMfaPayload) {
  const resp = await apiClient.post<{ data: BootstrapMfaResult }>("/bootstrap/mfa", payload);
  return resp.data.data;
}

export function useBindBootstrapMfa() {
  return useMutation({
    mutationFn: bindBootstrapMfa,
  });
}

// ──────────────────────────────────────────
// 鉴权 · 登录 / 登出
// ──────────────────────────────────────────
export interface LoginPayload {
  username: string;
  password: string;
  tenantId?: string;
}

export interface LoginResult {
  userId: string;
  tenantId: string;
  roles: string[];
  mustChangePwd: boolean;
  mfaRequired: boolean;
  mfaBound: boolean;
}

export function useLogin() {
  return useMutation({
    mutationFn: async (payload: LoginPayload) => {
      const resp = await apiClient.post<{ data: LoginResult }>("/auth/login", payload);
      return resp.data.data;
    },
  });
}

export function useLogout() {
  return useMutation({
    mutationFn: async () => {
      await apiClient.post("/auth/logout");
    },
  });
}

// ──────────────────────────────────────────
// 鉴权 · 成员账号（凭证）管理
// ──────────────────────────────────────────
export interface CredentialSummary {
  userId: string;
  username: string;
  status: string;
  mustChangePwd: boolean;
  createdAt: string;
}

export interface CreateMemberPayload {
  username: string;
  userId?: string;
  roleCode?: string;
  initialPassword?: string;
}

export interface CreateMemberResult {
  userId: string;
  username: string;
  tempPassword: string | null;
}

export function usePlatformCredentials() {
  return useQuery({
    queryKey: ["platform-credentials"],
    queryFn: async () => {
      const resp = await apiClient.get<{ data: CredentialSummary[] }>("/admin/credentials");
      return resp.data.data;
    },
  });
}

export function useCreateMember() {
  return useMutation({
    mutationFn: async (payload: CreateMemberPayload) => {
      const resp = await apiClient.post<{ data: CreateMemberResult }>(
        "/admin/credentials",
        payload,
      );
      return resp.data.data;
    },
  });
}

export function useResetMemberPassword() {
  return useMutation({
    mutationFn: async (userId: string) => {
      const resp = await apiClient.post<{ data: { tempPassword: string } }>(
        `/admin/credentials/${userId}/reset-password`,
      );
      return resp.data.data;
    },
  });
}

export function useSetCredentialStatus() {
  return useMutation({
    mutationFn: async (vars: { userId: string; status: string }) => {
      await apiClient.patch(`/admin/credentials/${vars.userId}/status`, { status: vars.status });
    },
  });
}

// ──────────────────────────────────────────
// 鉴权 · 平台租户开通（平台管理员）
// ──────────────────────────────────────────
export interface TenantSummary {
  tenantId: string;
  name: string;
  status: string;
  createdAt: string;
}

export interface ProvisionTenantPayload {
  tenantId: string;
  tenantName: string;
  adminUsername: string;
  adminInitialPassword?: string;
}

export interface ProvisionTenantResult {
  tenantId: string;
  adminUserId: string;
  adminUsername: string;
  tempPassword: string | null;
}

export function useTenants() {
  return useQuery({
    queryKey: ["platform-tenants"],
    queryFn: async () => {
      const resp = await apiClient.get<{ data: TenantSummary[] }>("/admin/tenants");
      return resp.data.data;
    },
  });
}

export function useProvisionTenant() {
  return useMutation({
    mutationFn: async (payload: ProvisionTenantPayload) => {
      const resp = await apiClient.post<{ data: ProvisionTenantResult }>("/admin/tenants", payload);
      return resp.data.data;
    },
  });
}
