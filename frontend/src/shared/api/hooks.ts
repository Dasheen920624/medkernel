import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiClient } from "./client";
import { isThemeMode, type ThemeMode } from "@/shared/config/theme";
import type { RuleType } from "@/shared/config/ruleTypes";
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
    wardId?: string | null;
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

type StandardApiContextFields = {
  request_id: string;
  trace_id: string;
  tenant_id: string;
  group_id?: string | null;
  hospital_id?: string | null;
  campus_id?: string | null;
  site_id?: string | null;
  department_id?: string | null;
  ward_id?: string | null;
  specialty_id?: string | null;
  user_id: string;
  role_codes: string[];
  package_version: string;
};

function standardApiContext(
  profile: SecurityProfile | undefined,
  packageVersion: string | undefined,
): StandardApiContextFields {
  if (!profile) {
    throw new Error("缺少当前用户安全画像，无法提交标准上下文请求。");
  }
  const tenantId = profile.dataScope?.tenantId;
  const roleCodes = profile.roles.map((role) => role.code).filter(Boolean);
  const version = packageVersion?.trim();
  if (!tenantId || roleCodes.length === 0 || !version) {
    throw new Error("标准上下文缺少服务空间、角色或包版本，请刷新用户状态后重试。");
  }
  const traceId = crypto.randomUUID();
  return {
    request_id: crypto.randomUUID(),
    trace_id: traceId,
    tenant_id: tenantId,
    group_id: profile.dataScope.groupId,
    hospital_id: profile.dataScope.hospitalId,
    campus_id: profile.dataScope.campusId,
    site_id: profile.dataScope.siteId,
    department_id: profile.dataScope.departmentId,
    ward_id: profile.dataScope.wardId,
    specialty_id: profile.dataScope.specialtyId,
    user_id: profile.userId,
    role_codes: roleCodes,
    package_version: version,
  };
}

function withStandardApiContext<T extends object>(
  payload: T,
  profile: SecurityProfile | undefined,
  packageVersion: string | undefined,
): T & StandardApiContextFields {
  return {
    ...payload,
    ...standardApiContext(profile, packageVersion),
  };
}

// ──────────────────────────────────────────
// 合规运维 · 审计日志（BASE-04 已落地）
// ──────────────────────────────────────────
export type AuditEventRow = {
  id: string;
  eventId: string;
  occurredAt: string;
  actorUserId: string | null;
  summary: string;
  actionCode: string;
  resourceType: string;
  resourceId: string;
  traceId: string | null;
  signature: string | null;
  status: string;
  actorRoles?: string | null;
  orgPath?: string | null;
  environmentKey?: string | null;
  outcome?: string | null;
  errorCode?: string | null;
  payloadDigest?: string | null;
  beforeSnapshot?: string | null;
  afterSnapshot?: string | null;
  superAdminAction?: boolean;
};

type AuditEventsEnvelope = {
  code: string;
  data: { items: AuditEventRow[]; nextCursor: string | null; hasNext: boolean };
};

export interface AuditEventListQuery {
  cursor?: string;
  size?: number;
  sort?: string;
  action?: string;
  outcome?: string;
  actorUserId?: string;
  resourceType?: string;
  traceId?: string;
  from?: string;
  to?: string;
}

export interface AuditEventPage {
  items: AuditEventRow[];
  nextCursor: string | null;
  totalEstimate: number;
  totalEstimated: boolean;
  hasMore: boolean;
}

type LargeAuditEventsEnvelope = {
  code: string;
  data: AuditEventPage;
};

type AuditSnapshotEnvelope = {
  code: string;
  data: AuditEventRow;
};

export function useLargeAuditEvents(query: AuditEventListQuery) {
  const params = {
    ...(query.cursor ? { cursor: query.cursor } : {}),
    size: query.size ?? 20,
    sort: query.sort ?? "id,desc",
    ...(query.action ? { action: query.action } : {}),
    ...(query.outcome ? { outcome: query.outcome } : {}),
    ...(query.actorUserId ? { actorUserId: query.actorUserId } : {}),
    ...(query.resourceType ? { resourceType: query.resourceType } : {}),
    ...(query.traceId ? { traceId: query.traceId } : {}),
    ...(query.from ? { from: query.from } : {}),
    ...(query.to ? { to: query.to } : {}),
  };
  return useQuery({
    queryKey: ["audit", "large-events", params],
    queryFn: async () => {
      const resp = await apiClient.get<LargeAuditEventsEnvelope>("/large-lists/audit-events/list", {
        params,
      });
      return resp.data.data;
    },
  });
}

export function useAuditEvents(enabled = true) {
  return useQuery({
    queryKey: ["audit", "events"],
    queryFn: async () => {
      const resp = await apiClient.get<AuditEventsEnvelope>("/compliance/audit/events");
      return resp.data.data?.items ?? [];
    },
    enabled,
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
  drillEvidence: {
    status: "SUCCESS" | "NOT_AVAILABLE" | "INVALID" | string;
    completedAt?: string | null;
    migrationCount?: number | null;
    evidenceReference?: string | null;
    detail: string;
  };
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

export interface RuntimeJvmMetadata {
  javaVersion: string;
  javaVendor: string;
  vmName: string;
  virtualThreadsEnabled: boolean;
  availableProcessors: number;
}

export interface RuntimeOsMetadata {
  name: string;
  version: string;
  arch: string;
}

export type RuntimeDomesticCheckStatus = "PASS" | "WARN" | "FAIL" | "UNKNOWN" | string;

export interface RuntimeDomesticCheckItem {
  key: string;
  category: string;
  displayName: string;
  status: RuntimeDomesticCheckStatus;
  actualValue: string;
  expectedValue: string;
  reason: string;
  recommendation: string;
  evidence: string;
}

export interface RuntimeDomesticCompatibility {
  overallStatus: RuntimeDomesticCheckStatus;
  summary: string;
  items: RuntimeDomesticCheckItem[];
  checkedAt: string;
}

export interface RuntimeOperationsSnapshot {
  serviceName: string;
  environment: string;
  deploymentMode: string;
  databaseDialect: string;
  migrationLocation: string;
  activeProfiles: string[];
  healthStatus: "UP" | "DOWN" | "OUT_OF_SERVICE" | "UNKNOWN" | string;
  jvm: RuntimeJvmMetadata;
  os: RuntimeOsMetadata;
  featureFlags: RuntimeFeatureFlag[];
  dependencies: RuntimeDependencyStatus[];
  backup: RuntimeBackupReadiness;
  domesticProfile: RuntimeDomesticProfile;
  domesticCompatibility: RuntimeDomesticCompatibility;
  generatedAt: string;
}

type RuntimeOperationsEnvelope = {
  data: RuntimeOperationsSnapshot;
};

export function useRuntimeOperations(enabled = true) {
  return useQuery({
    queryKey: ["system", "operations"],
    queryFn: async () => {
      const response = await apiClient.get<RuntimeOperationsEnvelope>("/system/operations");
      return response.data.data;
    },
    enabled,
    refetchInterval: 30_000,
  });
}

export async function downloadDomesticCompatibilityReport() {
  const response = await apiClient.get<Blob>("/system/operations/domestic-report", {
    responseType: "blob",
  });
  return response.data;
}

export function useSystemRuntime() {
  return useQuery({
    queryKey: ["system", "runtime"],
    queryFn: async () => (await apiClient.get("/system/runtime")).data as Record<string, unknown>,
    refetchInterval: 30_000,
  });
}

export interface DeveloperApiPermission {
  code: string;
  dimension: PermissionDimension;
  purpose: string;
}

export interface DeveloperApiAuditPoint {
  action: string;
  targetType: string;
  purpose: string;
}

export interface DeveloperApiContract {
  id: string;
  title: string;
  basePath: string;
  contractVersion?: string | null;
  openApiDocumentUrl?: string | null;
  fieldContractUrl?: string | null;
  openApiPaths: string[];
  permissions: DeveloperApiPermission[];
  auditPoints: DeveloperApiAuditPoint[];
  publicEndpoints: string[];
}

export interface DeveloperApiContractDirectory {
  contracts: DeveloperApiContract[];
}

type DeveloperApiContractDirectoryEnvelope = {
  data: DeveloperApiContractDirectory;
};

export function useDeveloperApiContracts() {
  return useQuery({
    queryKey: ["system", "dev-console", "api-contracts"],
    queryFn: async () => {
      const response = await apiClient.get<DeveloperApiContractDirectoryEnvelope>(
        "/system/dev-console/api-contracts",
      );
      return response.data.data;
    },
  });
}

export interface TraceTransitionError {
  errorCode?: string | null;
  errorClass?: string | null;
  message?: string | null;
  retryCount?: number | null;
  nextRetryAt?: string | null;
}

export interface TraceStateTransition {
  fromStatus?: string | null;
  toStatus?: string | null;
  reason?: string | null;
  actor?: string | null;
  traceId?: string | null;
  error?: TraceTransitionError | null;
  occurredAt?: string | null;
}

export interface TracePayloadSummary {
  digest: string;
  sizeBytes: number;
  contentType: string;
  storageType: string;
  fetchUri?: string | null;
}

export interface TraceDiagnosis {
  traceId: string;
  startedAt?: string | null;
  endedAt?: string | null;
  durationMs?: number | null;
  stateHistory: TraceStateTransition[];
  payloads: TracePayloadSummary[];
}

type TraceDiagnosisEnvelope = {
  data: TraceDiagnosis;
};

export function useTraceDiagnosis(traceId: string, enabled = true) {
  const normalizedTraceId = traceId.trim();
  return useQuery({
    queryKey: ["system", "trace-diagnosis", normalizedTraceId],
    enabled: enabled && normalizedTraceId.length > 0,
    queryFn: async () => {
      const response = await apiClient.get<TraceDiagnosisEnvelope>(
        `/engine/diagnose/traces/${encodeURIComponent(normalizedTraceId)}`,
      );
      return response.data.data;
    },
    retry: false,
  });
}

export type PluginCapabilityType = "READ" | "EXECUTE" | "WRITE";
export type PluginStatus = "PENDING_REVIEW" | "AUTHORIZED" | "DISABLED";
export type PluginAuthorityBoundary = "READ_ONLY" | "CONTROLLED_WRITE";
export type PluginGrantStatus = "AUTHORIZED" | "REVOKED";

export interface PluginCapability {
  capabilityKey: string;
  capabilityType: PluginCapabilityType;
  serviceContractId: string;
  serviceContractTitle: string;
  clinicalData: boolean;
}

export interface PluginItem {
  pluginId: string;
  pluginCode: string;
  displayName: string;
  status: PluginStatus;
  authorityBoundary: PluginAuthorityBoundary;
  capabilities: PluginCapability[];
  version: number;
  updatedAt?: string | null;
}

export interface PluginList {
  items: PluginItem[];
}

export interface PluginRegisterPayload {
  pluginCode: string;
  displayName: string;
  capabilities: Array<{
    capabilityKey: string;
    capabilityType: PluginCapabilityType;
    serviceContractId: string;
    clinicalData: boolean;
  }>;
}

export interface PluginGrantPayload {
  pluginId: string;
  capabilityKeys: string[];
  approvalReason: string;
  clinicalSafetyConfirmed: boolean;
}

export interface PluginGrantItem {
  grantId: string;
  capabilityKey: string;
  capabilityType: PluginCapabilityType;
  serviceContractId: string;
  status: PluginGrantStatus;
  clinicalSafetyConfirmed: boolean;
  grantedAt?: string | null;
}

export interface PluginGrantResult {
  pluginId: string;
  status: PluginGrantStatus;
  grants: PluginGrantItem[];
}

type PluginListEnvelope = {
  data: PluginList;
};

type PluginItemEnvelope = {
  data: PluginItem;
};

type PluginGrantEnvelope = {
  data: PluginGrantResult;
};

export function usePlugins() {
  return useQuery({
    queryKey: ["plugins"],
    queryFn: async () => {
      const response = await apiClient.get<PluginListEnvelope>("/plugins");
      return response.data.data;
    },
  });
}

export function useRegisterPlugin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: PluginRegisterPayload) => {
      const response = await apiClient.post<PluginItemEnvelope>("/plugins/register", payload);
      return response.data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["plugins"] });
    },
  });
}

export function useGrantPlugin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ pluginId, ...payload }: PluginGrantPayload) => {
      const response = await apiClient.post<PluginGrantEnvelope>(
        `/plugins/${encodeURIComponent(pluginId)}/grants`,
        payload,
      );
      return response.data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["plugins"] });
    },
  });
}

export function useDisablePlugin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (pluginId: string) => {
      const response = await apiClient.post<PluginItemEnvelope>(
        `/plugins/${encodeURIComponent(pluginId)}:disable`,
      );
      return response.data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["plugins"] });
    },
  });
}

// ──────────────────────────────────────────
// 高级工具 · 关系库权威源投影查询
// ──────────────────────────────────────────
export type ProjectionTargetType = "CLINICAL_GRAPH" | "KNOWLEDGE_GRAPH" | "KNOWLEDGE_SEARCH";

export type ProjectionSyncStatus = "SUCCESS" | "FAILED" | "NOT_SYNCED" | string;

export interface ProjectionRuntimeStatusResponse {
  targetType: ProjectionTargetType;
  tenantId: string;
  graphProjectionEnabled: boolean;
  difyWorkflowEnabled: boolean;
  clinicalProjectionStatus: string;
  difyExecutionStatus: ProjectionSyncStatus;
  snapshotCount: number;
  message: string;
}

export interface ProjectionDiffItem {
  factKey: string;
  sourceHash?: string | null;
  projectionHash?: string | null;
}

export interface ProjectionConsistencyReport {
  targetType: ProjectionTargetType;
  tenantId: string;
  status: ProjectionSyncStatus;
  message: string;
  consistent: boolean;
  sourceCount: number;
  projectionCount: number;
  sourceHash?: string | null;
  projectionHash?: string | null;
  missing: ProjectionDiffItem[];
  extra: ProjectionDiffItem[];
  changed: ProjectionDiffItem[];
}

export interface ProjectionFactItem {
  factKey: string;
  factKind: "NODE" | "EDGE" | string;
  objectType: string;
  objectId: string;
  subjectKey?: string | null;
  predicate?: string | null;
  objectKey?: string | null;
  contentHash?: string | null;
  sourceUpdatedAt?: string | null;
  syncedAt?: string | null;
  traceId?: string | null;
}

export interface ProjectionRebuildResponse {
  syncId: string;
  targetType: ProjectionTargetType;
  status: ProjectionSyncStatus;
  sourceCount: number;
  projectionCount: number;
  sourceHash?: string | null;
  projectionHash?: string | null;
  traceId?: string | null;
  difyExecutionStatus: ProjectionSyncStatus;
  message: string;
}

export interface ProjectionFactsQuery {
  targetType: ProjectionTargetType;
  keyword?: string;
  page?: number;
  size?: number;
}

type ProjectionEnvelope<T> = {
  data: T;
};

type ProjectionPageEnvelope = {
  data: PageResponse<ProjectionFactItem>;
};

function projectionPath(targetType: ProjectionTargetType) {
  switch (targetType) {
    case "KNOWLEDGE_GRAPH":
      return "knowledge-graph";
    case "KNOWLEDGE_SEARCH":
      return "knowledge-search";
    default:
      return "clinical-graph";
  }
}

export function useProjectionRuntimeStatus(
  targetType: ProjectionTargetType = "CLINICAL_GRAPH",
  enabled = true,
) {
  return useQuery({
    queryKey: ["projections", targetType, "status"],
    queryFn: async () => {
      const { data } = await apiClient.get<ProjectionEnvelope<ProjectionRuntimeStatusResponse>>(
        `/projections/${projectionPath(targetType)}/status`,
      );
      return data.data;
    },
    enabled: enabled && targetType === "CLINICAL_GRAPH",
    refetchInterval: 30_000,
  });
}

export function useProjectionConsistency(targetType: ProjectionTargetType, enabled = true) {
  return useQuery({
    queryKey: ["projections", targetType, "consistency"],
    queryFn: async () => {
      const { data } = await apiClient.get<ProjectionEnvelope<ProjectionConsistencyReport>>(
        `/projections/${projectionPath(targetType)}/consistency`,
      );
      return data.data;
    },
    enabled,
  });
}

export function useProjectionFacts(query: ProjectionFactsQuery, enabled = true) {
  return useQuery({
    queryKey: [
      "projections",
      query.targetType,
      "facts",
      query.keyword ?? "",
      query.page ?? 1,
      query.size ?? 20,
    ],
    queryFn: async () => {
      const { data } = await apiClient.get<ProjectionPageEnvelope>(
        `/projections/${projectionPath(query.targetType)}/facts`,
        {
          params: {
            keyword: query.keyword || undefined,
            page: query.page ?? 1,
            size: query.size ?? 20,
          },
        },
      );
      return data.data;
    },
    enabled,
  });
}

export function useRebuildProjection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (targetType: ProjectionTargetType) => {
      const { data } = await apiClient.post<ProjectionEnvelope<ProjectionRebuildResponse>>(
        `/projections/${projectionPath(targetType)}/rebuild`,
      );
      return data.data;
    },
    onSuccess: (_data, targetType) => {
      void queryClient.invalidateQueries({ queryKey: ["projections", targetType] });
      if (targetType === "CLINICAL_GRAPH") {
        void queryClient.invalidateQueries({
          queryKey: ["projections", "CLINICAL_GRAPH", "status"],
        });
      }
    },
  });
}

// ──────────────────────────────────────────
// 字典映射 · GA-ENG-API-04 已上线（engine/terminology）
// ──────────────────────────────────────────
const TERMINOLOGY_API_ROOT = "/engine/terminology";

type TermCategory =
  | "DIAGNOSIS"
  | "PROCEDURE"
  | "DRUG"
  | "DEVICE"
  | "LAB"
  | "EXAM"
  | "ORDER"
  | "INSURANCE"
  | "DEPARTMENT"
  | "DOCUMENT"
  | "FOLLOWUP"
  | "OTHER";

export interface StandardTerm {
  id: number;
  tenantId: string;
  standardSystem: string;
  termCode: string;
  category: TermCategory;
  displayName: string;
  normalizedName?: string;
  versionNo: string;
  status: "ACTIVE" | "DISABLED";
  sourceVersionId?: number | null;
  evidenceText?: string | null;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface LocalTerm {
  id: number;
  tenantId: string;
  sourceSystem: string;
  localCode: string;
  category: TermCategory;
  localName: string;
  normalizedName?: string;
  departmentId?: string | null;
  status: "UNMAPPED" | "MAPPED" | "DISABLED";
  firstSeenAt?: string;
  lastSeenAt?: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

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

export interface TermMappingCandidate {
  id: number;
  localTermId: number;
  standardTermId: number;
  semanticMatchScore: number;
  highRiskFlag: boolean;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  source: "RULE" | "AI" | "MANUAL" | "IMPORT";
  status: "PENDING" | "CONFIRMED" | "REJECTED" | "EXPIRED";
  evidenceText?: string | null;
  generationJobCode?: string | null;
}

export interface MappingConflict {
  id: number;
  tenantId: string;
  conflictType:
    | "ONE_TO_MANY"
    | "MANY_TO_ONE"
    | "DISABLED_CODE"
    | "CROSS_SYSTEM_INCONSISTENT"
    | "HOMONYM"
    | "SYNONYM_MISMATCH";
  localTermId?: number | null;
  standardTermId?: number | null;
  mappingId?: number | null;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  description: string;
  status: "OPEN" | "RESOLVED" | "IGNORED";
  resolvedBy?: string | null;
  resolvedAt?: string | null;
  resolutionNote?: string | null;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface TerminologyCandidateGenerationJob {
  id?: number;
  tenantId?: string;
  jobCode: string;
  sourceSystem: string;
  minimumScore?: number | null;
  semanticAssistEnabled: boolean;
  packageVersion: string;
  requestedBy: string;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  progress: number;
  generatedCount: number;
  candidatePageUri?: string | null;
  errorMessage?: string | null;
  createdAt?: string;
  startedAt?: string | null;
  completedAt?: string | null;
}

export interface TerminologyBatchConfirmResponse {
  confirmedCount: number;
  confirmedCandidateIds: number[];
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

function emptyPage<T>(): PageResponse<T> {
  return {
    items: [],
    page: 1,
    size: 20,
    total: 0,
    hasNext: false,
    totalEstimated: false,
  };
}

export interface TerminologyMappingsParams {
  page?: number;
  size?: number;
  sort?: string;
  sourceSystem?: string;
  category?: TermCategory;
  status?: TermMapping["status"];
  keyword?: string;
}

function compactParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params as Record<string, unknown>).filter(
      ([, value]) => value !== undefined && value !== "",
    ),
  ) as Partial<T>;
}

function compactOneBasedPageParams<T extends { page?: number }>(params: T): Partial<T> {
  return compactParams(
    params.page !== undefined && params.page < 1 ? { ...params, page: 1 } : params,
  );
}

// ──────────────────────────────────────────
// 知识资产审核 · API-03 / KNOW-02 客户面
// ──────────────────────────────────────────

const KNOWLEDGE_API_ROOT = "/engine/knowledge";
const KNOWLEDGE_PRODUCTION_API_ROOT = "/engine/knowledge-production";
const KNOWLEDGE_ACQUISITION_API_ROOT = "/engine/knowledge/acquisition";

export interface KnowledgeAcquisitionSource {
  id: number;
  tenantId: string;
  sourceCode: string;
  domain: string;
  baseUrl: string;
  sourceType: string;
  authorityLevel: string;
  authorityBasis: string;
  title: string;
  publisher: string;
  license: string;
  licensePolicy: "PERMITTED" | "RESTRICTED" | "FORBIDDEN" | string;
  robotsPolicy: "ALLOW_FETCH" | "MANUAL_APPROVED" | "DISALLOW_FETCH" | string;
  enabledFlag: "Y" | "N" | string;
  approvedBy?: string | null;
  approvedAt?: string | null;
  scheduleEnabledFlag: "Y" | "N" | string;
  scheduleIntervalMinutes?: number | null;
  nextCheckAt?: string | null;
  lastCheckAt?: string | null;
  defaultFormat?: string | null;
  generationPlanJson?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
  version: number;
}

export interface KnowledgeAcquisitionSourceDraftRequest {
  domain: string;
  baseUrl: string;
  sourceType: string;
  authorityLevel: string;
  authorityBasis: string;
  title: string;
  publisher: string;
  license: string;
  licensePolicy: "PERMITTED" | "RESTRICTED" | "FORBIDDEN";
  robotsPolicy: "ALLOW_FETCH" | "MANUAL_APPROVED" | "DISALLOW_FETCH";
  scheduleEnabled: boolean;
  scheduleIntervalMinutes?: number;
  defaultFormat?: string;
  generationPlan?: Record<string, unknown>;
}

export interface KnowledgeAcquisitionSourcesParams {
  page?: number;
  size?: number;
}

export function useKnowledgeAcquisitionSources(params: KnowledgeAcquisitionSourcesParams = {}) {
  const requestParams = compactOneBasedPageParams({
    page: params.page ?? 1,
    size: params.size ?? 20,
  });
  return useQuery({
    queryKey: ["knowledge-acquisition", "sources", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<KnowledgeAcquisitionSource> }>(
        `${KNOWLEDGE_ACQUISITION_API_ROOT}/sources`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export function useSaveKnowledgeAcquisitionSourceDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      sourceCode,
      request,
    }: {
      sourceCode: string;
      request: KnowledgeAcquisitionSourceDraftRequest;
    }) => {
      const { data } = await apiClient.put<{ data: KnowledgeAcquisitionSource }>(
        `${KNOWLEDGE_ACQUISITION_API_ROOT}/sources/${encodeURIComponent(sourceCode)}`,
        request,
      );
      return data.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge-acquisition", "sources"] });
    },
  });
}

function useKnowledgeAcquisitionSourceStatusMutation(action: "approval" | "disable") {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (sourceCode: string) => {
      const { data } = await apiClient.post<{ data: KnowledgeAcquisitionSource }>(
        `${KNOWLEDGE_ACQUISITION_API_ROOT}/sources/${encodeURIComponent(sourceCode)}/${action}`,
      );
      return data.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge-acquisition", "sources"] });
    },
  });
}

export function useApproveKnowledgeAcquisitionSource() {
  return useKnowledgeAcquisitionSourceStatusMutation("approval");
}

export function useDisableKnowledgeAcquisitionSource() {
  return useKnowledgeAcquisitionSourceStatusMutation("disable");
}

export type KnowledgeDomain =
  | "GUIDELINE"
  | "DRUG"
  | "PATHWAY_KNOWLEDGE"
  | "NURSING"
  | "REPORT"
  | "TCM"
  | "PROTOCOL"
  | "POLICY"
  | "LITERATURE"
  | "OTHER"
  | "DIAGNOSIS"
  | string;

export type KnowledgeIdentityStatus = "ACTIVE" | "DEPRECATED" | "WITHDRAWN" | "ARCHIVED" | string;

export type KnowledgeVersionStatus =
  | "DRAFT"
  | "CANDIDATE"
  | "PENDING_REPLACEMENT_REVIEW"
  | "UNDER_REVIEW"
  | "ACTIVE"
  | "SUPERSEDED"
  | "WITHDRAWN"
  | "REJECTED"
  | string;

export type CandidateClassificationType =
  | "NEW_ASSET"
  | "SAME_IDENTITY_NEW_VERSION"
  | "DUPLICATE"
  | "CONFLICT"
  | string;

export type CandidateReviewStatus =
  | "PENDING_REPLACEMENT_REVIEW"
  | "DUPLICATE_SKIPPED"
  | "APPROVED"
  | "REJECTED"
  | "RETURNED"
  | string;

export type KnowledgeCandidateReviewDecision = "APPROVE" | "REJECT" | "RETURN";
export type KnowledgeReviewFeedbackType =
  | "ACCEPTED"
  | "NOT_ADOPTED"
  | "CONTENT_GAP"
  | "SOURCE_BLANK"
  | "FALSE_POSITIVE";
export type KnowledgeReviewFollowupAction =
  | "NONE"
  | "CREATE_REVISION_CANDIDATE"
  | "REQUEST_SOURCE_EVIDENCE"
  | "MARK_FALSE_POSITIVE"
  | "ARCHIVE_REJECTED";

export interface KnowledgeIdentity {
  id: number;
  tenantId: string;
  identityCode: string;
  domain: KnowledgeDomain;
  subject: string;
  specialtyId?: string | null;
  description?: string | null;
  status: KnowledgeIdentityStatus;
  currentVersionId?: number | null;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface KnowledgeAssetVersion {
  id: number;
  tenantId: string;
  identityId: number;
  versionNo: string;
  versionLabel?: string | null;
  sourceDocumentId?: number | null;
  sourceVersionId?: number | null;
  contentHash?: string | null;
  anchors?: string | null;
  status: KnowledgeVersionStatus;
  riskLevel?: "LOW" | "MEDIUM" | "HIGH" | string | null;
  authorityLevel?: string | null;
  gradeQuality?: string | null;
  gradeStrength?: string | null;
  conflictArbitration?: string | null;
  organizationScope?: string | null;
  applicableScope?: string | null;
  activeScopeKey?: string | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  activatedAt?: string | null;
  supersededAt?: string | null;
  withdrawnAt?: string | null;
  withdrawnReason?: string | null;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
  reviewCycleMonths?: number | null;
  nextReviewAt?: string | null;
}

export interface KnowledgeSupersession {
  id: number;
  tenantId: string;
  identityId: number;
  oldVersionId?: number | null;
  newVersionId?: number | null;
  transitionType: string;
  transitionReason?: string | null;
  transitionedAt?: string | null;
  transitionedBy?: string | null;
  successorIdentityId?: number | null;
  gracePeriodEnd?: string | null;
  migrationGuidance?: string | null;
}

export type KnowledgeReviewStatus = "UPCOMING" | "OVERDUE";

export interface KnowledgeReviewQueueItem {
  identity: KnowledgeIdentity;
  version: KnowledgeAssetVersion;
  status: KnowledgeReviewStatus;
  daysUntilDue: number;
}

export interface KnowledgeReviewQueueQueryParams {
  withinDays?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface KnowledgeRetirementPayload {
  identityId: number;
  successorIdentityId: number;
  gracePeriodEnd: string;
  migrationGuidance: string;
}

export interface KnowledgeSourceEvidence {
  assetVersionId: number;
  citationId: number;
  sourceFragmentId: number;
  sourceDocumentId: number;
  sourceVersionId: number;
  sourceCode: string;
  sourceTitle: string;
  sourceType: string;
  authorityLevel?: string | null;
  authorityLabel: string;
  authorityBasis?: string | null;
  sourceVersionNo?: string | null;
  sourceVersionHash?: string | null;
  anchorPath?: string | null;
  anchorLabel?: string | null;
  textExcerpt?: string | null;
  fragmentHash?: string | null;
  startOffset?: number | null;
  endOffset?: number | null;
  gradeQuality?: string | null;
  gradeStrength?: string | null;
  publishedAt?: string | null;
  relation?: string | null;
  weight?: number | null;
  organizationScope?: string | null;
  applicableScope?: string | null;
  displayRole: string;
  recommendedByDefault: boolean;
  supplementary: boolean;
  displayLabel: string;
  rankingReason: string;
  conflictArbitration?: string | null;
}

export interface KnowledgeProvenanceResponse {
  identity: KnowledgeIdentity;
  currentVersionId?: number | null;
  versions: PageResponse<KnowledgeAssetVersion>;
  supersessions: PageResponse<KnowledgeSupersession>;
  sourceEvidence: KnowledgeSourceEvidence[];
  unresolvedCitationCount: number;
  partial: boolean;
}

export interface KnowledgeProvenanceParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface CandidateClassification {
  id: number;
  tenantId: string;
  orgPath?: string | null;
  identityId: number;
  candidateVersionId: number;
  activeVersionId?: number | null;
  classification: CandidateClassificationType;
  reviewStatus: CandidateReviewStatus;
  contentHash?: string | null;
  basis?: string | null;
  diffSummary?: string | null;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface KnowledgeCandidateResponse {
  identityId: number;
  candidates: PageResponse<KnowledgeAssetVersion>;
  classifications: CandidateClassification[];
  available: boolean;
  reasonCode?: string | null;
  message?: string | null;
}

export interface KnowledgeCandidatesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface KnowledgeIdentityQueryParams {
  domain?: KnowledgeDomain;
  specialtyId?: string;
  status?: KnowledgeIdentityStatus;
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string;
  enabled?: boolean;
}

const EMPTY_KNOWLEDGE_CANDIDATES: KnowledgeCandidateResponse = {
  identityId: 0,
  candidates: emptyPage<KnowledgeAssetVersion>(),
  classifications: [],
  available: false,
  reasonCode: "NO_IDENTITY_SELECTED",
  message: "未选择知识身份。",
};

export function useKnowledgeIdentities(params: KnowledgeIdentityQueryParams = {}) {
  const requestParams = compactParams({
    domain: params.domain,
    specialtyId: params.specialtyId,
    status: params.status,
    keyword: params.keyword,
    page: params.page ?? 1,
    size: params.size ?? 20,
    sort: params.sort ?? "updatedAt,desc",
  });
  return useQuery({
    queryKey: ["knowledge", "identities", requestParams],
    enabled: params.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<KnowledgeIdentity> }>(
        `${KNOWLEDGE_API_ROOT}/identities`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export function useKnowledgeProvenance(
  identityId?: number,
  params: KnowledgeProvenanceParams = {},
) {
  return useQuery({
    queryKey: ["knowledge", "provenance", identityId, params],
    enabled: Boolean(identityId),
    queryFn: async () => {
      if (!identityId) {
        throw new Error("未选择知识身份");
      }
      const { data } = await apiClient.get<{ data: KnowledgeProvenanceResponse }>(
        `${KNOWLEDGE_API_ROOT}/identities/${identityId}/provenance`,
        { params },
      );
      return data.data;
    },
  });
}

export function useKnowledgeReviewQueue(params: KnowledgeReviewQueueQueryParams = {}) {
  const requestParams = compactParams({
    withinDays: params.withinDays ?? 30,
    page: params.page ?? 1,
    size: params.size ?? 20,
    sort: params.sort ?? "nextReviewAt,asc",
  });
  return useQuery({
    queryKey: ["knowledge", "review-queue", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<KnowledgeReviewQueueItem> }>(
        `${KNOWLEDGE_API_ROOT}/review-queue`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export function useDeprecateKnowledgeIdentity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ identityId, ...payload }: KnowledgeRetirementPayload) => {
      const { data } = await apiClient.post<{ data: KnowledgeSupersession }>(
        `${KNOWLEDGE_API_ROOT}/identities/${identityId}/deprecate`,
        payload,
      );
      return data.data;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["knowledge", "identities"] }),
        queryClient.invalidateQueries({ queryKey: ["knowledge", "provenance"] }),
      ]);
    },
  });
}

export function useKnowledgeCandidates(
  identityId?: number,
  params: KnowledgeCandidatesParams = {},
) {
  const requestParams = compactParams({
    page: params.page ?? 1,
    size: params.size ?? 20,
    sort: params.sort,
  });
  return useQuery({
    queryKey: ["knowledge", "candidates", identityId, requestParams],
    enabled: Boolean(identityId),
    queryFn: async () => {
      if (!identityId) return EMPTY_KNOWLEDGE_CANDIDATES;
      const { data } = await apiClient.get<{ data: KnowledgeCandidateResponse }>(
        `${KNOWLEDGE_API_ROOT}/identities/${identityId}/candidates`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export type KnowledgeProducer = "API_MODEL" | "AGENT_TOOL" | "LOCAL_MODEL" | "MANUAL" | string;

/** AIK-STD-12：候选生产来源溯源（aiGenerated=producer≠MANUAL + 归属 job/管道/模型策略）。 */
export interface CandidateProvenanceView {
  candidateRef: string;
  aiGenerated: boolean;
  producer: KnowledgeProducer;
  jobCode: string;
  targetPipeline: "PLATFORM_SOURCE" | "TENANT_OVERLAY" | string;
  domain: string;
  modelStrategy?: string | null;
  riskLevel?: string | null;
  producedAt?: string | null;
  producedBy?: string | null;
  modelTaskId?: string | null;
  modelMode?: string | null;
  modelVersion?: string | null;
  promptVersion?: string | null;
  toolVersion?: string | null;
  sourceCitations?: string | null;
  confidence?: number | null;
  fallbackUsed?: boolean | null;
  fallbackReason?: string | null;
}

/**
 * 审核台批量反查候选 AI 工厂生产来源（AIK-STD-12 PR1 端点）。
 * 传候选版本引用 kv:{identityId}:{versionNo}；无血缘行的候选不返回（诚实「非工厂候选」）。
 */
export function useCandidateProvenance(candidateRefs: string[]) {
  const refs = candidateRefs ?? [];
  return useQuery({
    queryKey: ["knowledge-production", "candidate-provenance", refs],
    enabled: refs.length > 0,
    queryFn: async () => {
      const { data } = await apiClient.post<{ data: CandidateProvenanceView[] }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/candidates/provenance`,
        { candidateRefs: refs },
      );
      return data.data;
    },
  });
}

/** AIK-STD-12 FR-1：专业标准资产模板的结构章节。 */
export interface TemplateSection {
  key: string;
  label: string;
  required: boolean;
  hint: string;
}

/** AIK-STD-12 FR-1：全专业领域标准资产模板（结构骨架，按 assetType+domain 定位）。 */
export interface ProfessionalAssetTemplate {
  professionCode: string;
  displayName: string;
  assetType: string;
  knowledgeDomain: KnowledgeDomain | null;
  sections: TemplateSection[];
}

/**
 * 全专业标准资产模板目录（AIK-STD-12 FR-1）：审核台按候选 assetType+domain 对照核查完整性。
 * 确定性目录，全租户一致；缓存长（不随租户/候选变化）。
 */
export function useAssetTemplates() {
  return useQuery({
    queryKey: ["knowledge-production", "asset-templates"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ProfessionalAssetTemplate[] }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/asset-templates`,
      );
      return data.data;
    },
  });
}

export interface KnowledgeProductionReadinessItem {
  code: string;
  ready: boolean;
  required: boolean;
  message: string;
  evidence?: string | null;
}

export interface KnowledgeProductionReadinessResponse {
  tenantId: string;
  producer: KnowledgeProducer;
  capabilityCode?: string | null;
  providerCode?: string | null;
  deploymentForm?: string | null;
  ready: boolean;
  modelInvocationAllowed: boolean;
  items: KnowledgeProductionReadinessItem[];
}

export interface KnowledgeProductionReadinessParams {
  producer?: KnowledgeProducer;
  capabilityCode?: string;
  providerCode?: string;
}

export type KnowledgeProductionJobStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | string;

export interface KnowledgeProductionJob {
  id?: number;
  tenantId: string;
  jobCode: string;
  sourceScope?: string | null;
  assetType: string;
  producer: KnowledgeProducer;
  targetPipeline: "PLATFORM_SOURCE" | "TENANT_OVERLAY" | string;
  domain: string;
  modelStrategy?: string | null;
  status: KnowledgeProductionJobStatus;
  candidateCount: number;
  lineage?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
  traceId?: string | null;
}

export type KnowledgeProductionJobResponse = Omit<
  KnowledgeProductionJob,
  "id" | "updatedAt" | "updatedBy" | "traceId"
>;

export interface KnowledgeProductionJobsParams {
  page?: number;
  size?: number;
}

export interface CreateKnowledgeProductionJobRequest {
  sourceScope: string;
  assetType: string;
  producer: KnowledgeProducer;
  targetPipeline: KnowledgeProductionJob["targetPipeline"];
  domain: string;
  modelStrategy?: string;
}

export interface ReviewRoutingDecision {
  ownerReviewerRole?: string | null;
  domainReviewerRole?: string | null;
  requiresDualSign: boolean;
  domain?: string | null;
}

export interface KnowledgeProductionCandidateView {
  jobCode: string;
  assetIdentity?: string | null;
  contentHash?: string | null;
  candidateRef: string;
  riskLevel?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
  routing?: ReviewRoutingDecision | null;
}

export interface AikGateResult {
  id?: number;
  tenantId?: string;
  jobCode: string;
  contentHash?: string | null;
  gateCode: string;
  passed: boolean;
  reason?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
}

export interface GenerationTriage {
  id?: number;
  tenantId?: string;
  jobCode: string;
  contentHash?: string | null;
  assetType?: string | null;
  targetIdentityId?: number | null;
  activeVersionId?: number | null;
  matchedVersionId?: number | null;
  triageState: string;
  action: string;
  basis?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
}

export interface KnowledgeShadowRun {
  id?: number;
  tenantId?: string;
  jobCode: string;
  assetType?: string | null;
  targetIdentityId?: number | null;
  contentHash?: string | null;
  capabilityCode?: string | null;
  status: string;
  totalCases: number;
  hitCount: number;
  falsePositiveCount: number;
  missCount: number;
  degradationDetected: boolean;
  readyForReview: boolean;
  basis?: string | null;
  createdAt?: string | null;
  createdBy?: string | null;
}

export interface CandidateCoexistenceVersionSnapshot {
  versionId?: number | null;
  versionNo?: string | null;
  status?: string | null;
  riskLevel?: string | null;
  authorityLevel?: string | null;
  gradeQuality?: string | null;
  gradeStrength?: string | null;
  contentHash?: string | null;
  organizationScope?: string | null;
  applicableScope?: string | null;
  activatedAt?: string | null;
  updatedAt?: string | null;
}

export interface CandidateCoexistenceProductionLineage {
  jobCode?: string | null;
  assetIdentity?: string | null;
  producer?: KnowledgeProducer | null;
  targetPipeline?: "PLATFORM_SOURCE" | "TENANT_OVERLAY" | string | null;
  domain?: string | null;
  modelStrategy?: string | null;
  riskLevel?: string | null;
  createdAt?: string | null;
}

export interface CandidateCoexistenceView {
  candidateRef: string;
  identityId?: number | null;
  candidateVersion?: CandidateCoexistenceVersionSnapshot | null;
  activeVersion?: CandidateCoexistenceVersionSnapshot | null;
  classification?: string | null;
  reviewStatus?: string | null;
  diffSummary?: string | null;
  productionLineage?: CandidateCoexistenceProductionLineage | null;
  candidateExecutable: boolean;
  activeExecutable: boolean;
  approvalOutcome?: string | null;
  replacementReminder: string;
  safetyNotice?: string | null;
}

export function useKnowledgeProductionReadiness(
  params: KnowledgeProductionReadinessParams = {},
  enabled = true,
) {
  const requestParams = compactParams({
    producer: params.producer ?? "API_MODEL",
    capabilityCode: params.capabilityCode,
    providerCode: params.providerCode,
  });
  return useQuery({
    queryKey: ["knowledge-production", "readiness", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: KnowledgeProductionReadinessResponse }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/readiness`,
        { params: requestParams },
      );
      return data.data;
    },
    enabled,
  });
}

export function useKnowledgeProductionJobs(params: KnowledgeProductionJobsParams = {}) {
  const requestParams = compactParams({
    page: params.page ?? 1,
    size: params.size ?? 20,
  });
  return useQuery({
    queryKey: ["knowledge-production", "jobs", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<KnowledgeProductionJob> }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/jobs`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export function useCreateKnowledgeProductionJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: CreateKnowledgeProductionJobRequest) => {
      const { data } = await apiClient.post<{ data: KnowledgeProductionJobResponse }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/jobs`,
        compactParams(request),
      );
      return data.data;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["knowledge-production", "jobs"] }),
        queryClient.invalidateQueries({ queryKey: ["knowledge-production", "readiness"] }),
      ]);
    },
  });
}

export function useCancelKnowledgeProductionJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (jobCode: string) => {
      const { data } = await apiClient.post<{ data: KnowledgeProductionJobResponse }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/jobs/${encodeURIComponent(jobCode)}/cancel`,
      );
      return data.data;
    },
    onSuccess: async (_data, jobCode) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["knowledge-production", "jobs"] }),
        queryClient.invalidateQueries({
          queryKey: ["knowledge-production", "job-candidates", jobCode],
        }),
        queryClient.invalidateQueries({
          queryKey: ["knowledge-production", "gate-results", jobCode],
        }),
        queryClient.invalidateQueries({
          queryKey: ["knowledge-production", "triage-results", jobCode],
        }),
        queryClient.invalidateQueries({
          queryKey: ["knowledge-production", "shadow-runs", jobCode],
        }),
      ]);
    },
  });
}

export function useKnowledgeProductionCandidates(jobCode?: string | null) {
  return useQuery({
    queryKey: ["knowledge-production", "job-candidates", jobCode],
    enabled: Boolean(jobCode),
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: KnowledgeProductionCandidateView[] }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/jobs/${encodeURIComponent(jobCode ?? "")}/candidates`,
      );
      return data.data;
    },
  });
}

export function useKnowledgeProductionGateResults(jobCode?: string | null) {
  return useQuery({
    queryKey: ["knowledge-production", "gate-results", jobCode],
    enabled: Boolean(jobCode),
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: AikGateResult[] }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/jobs/${encodeURIComponent(jobCode ?? "")}/gate-results`,
      );
      return data.data;
    },
  });
}

export function useKnowledgeProductionTriageResults(jobCode?: string | null) {
  return useQuery({
    queryKey: ["knowledge-production", "triage-results", jobCode],
    enabled: Boolean(jobCode),
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: GenerationTriage[] }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/jobs/${encodeURIComponent(jobCode ?? "")}/triage-results`,
      );
      return data.data;
    },
  });
}

export function useKnowledgeProductionShadowRuns(jobCode?: string | null) {
  return useQuery({
    queryKey: ["knowledge-production", "shadow-runs", jobCode],
    enabled: Boolean(jobCode),
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: KnowledgeShadowRun[] }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/jobs/${encodeURIComponent(jobCode ?? "")}/shadow-runs`,
      );
      return data.data;
    },
  });
}

export function useCandidateCoexistence(candidateRef?: string | null) {
  return useQuery({
    queryKey: ["knowledge-production", "candidate-coexistence", candidateRef],
    enabled: Boolean(candidateRef),
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: CandidateCoexistenceView }>(
        `${KNOWLEDGE_PRODUCTION_API_ROOT}/candidates/coexistence`,
        { params: { candidateRef } },
      );
      return data.data;
    },
  });
}

export function useKnowledgeCandidateDiff(candidateId?: number) {
  return useQuery({
    queryKey: ["knowledge", "candidate-diff", candidateId],
    enabled: Boolean(candidateId),
    queryFn: async () => {
      if (!candidateId) return EMPTY_KNOWLEDGE_CANDIDATES;
      const { data } = await apiClient.get<{ data: KnowledgeCandidateResponse }>(
        `${KNOWLEDGE_API_ROOT}/candidates/${candidateId}/diff`,
      );
      return data.data;
    },
  });
}

export function useReviewKnowledgeCandidate() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      candidateId: number;
      packageVersion: string;
      request: {
        decision: KnowledgeCandidateReviewDecision;
        reason?: string;
        publishEvidence?: VersionPublishEvidence;
        feedbackType?: KnowledgeReviewFeedbackType;
        followupAction?: KnowledgeReviewFollowupAction;
      };
      idempotencyKey?: string;
    }) => {
      const headers = payload.idempotencyKey
        ? { "Idempotency-Key": payload.idempotencyKey }
        : undefined;
      const config = headers ? { headers } : undefined;
      const { data } = await apiClient.post<{ data: KnowledgeCandidateResponse }>(
        `${KNOWLEDGE_API_ROOT}/candidates/${payload.candidateId}/review`,
        withStandardApiContext(payload.request, security.data, payload.packageVersion),
        config,
      );
      return data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["knowledge", "identities"] });
      void queryClient.invalidateQueries({ queryKey: ["knowledge", "candidates"] });
      void queryClient.invalidateQueries({ queryKey: ["knowledge", "candidate-diff"] });
    },
  });
}

export type KnowledgeSourceType = "PLATFORM_STANDARD" | "LOCAL_CUSTOMIZATION" | "LOCAL_ORIGINAL";

export type KnowledgeCustomizationStatus = "DRAFT" | "ACTIVE" | "RESTORED";

export interface KnowledgeCustomization {
  customizationId: string;
  sourceType: KnowledgeSourceType;
  status: KnowledgeCustomizationStatus;
  platformIdentityId: number;
  platformVersionId: number;
  platformVersionNo: string;
  localIdentityId: number;
  localVersionId: number;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  targetOrgUnitId: string;
  targetOrganizationName: string;
  targetOrgPath: string;
  applicableScope: string;
  reason: string | null;
  overrideId: string | null;
  platformUpdateAvailable: boolean;
  updatedAt: string;
}

export interface CreateKnowledgeCustomizationPayload {
  platformIdentityId: number;
  targetOrgUnitId: string;
  applicableScope: string;
  reason: string;
}

export interface KnowledgeCustomizationsParams {
  page?: number;
  size?: number;
}

export function useKnowledgeCustomizations(
  params: KnowledgeCustomizationsParams = {},
  enabled = true,
) {
  const queryParams = {
    page: params.page ?? 1,
    size: params.size ?? 20,
  };
  return useQuery({
    queryKey: ["knowledge", "customizations", queryParams],
    enabled,
    queryFn: async () => {
      const response = await apiClient.get<{ data: PageResponse<KnowledgeCustomization> }>(
        `${KNOWLEDGE_API_ROOT}/customizations`,
        { params: queryParams },
      );
      return response.data.data;
    },
  });
}

function useInvalidateKnowledgeCustomizations() {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["knowledge", "customizations"] }),
      queryClient.invalidateQueries({ queryKey: ["knowledge", "identities"] }),
      queryClient.invalidateQueries({ queryKey: ["knowledge", "candidates"] }),
    ]);
  };
}

export function useCreateKnowledgeCustomization() {
  const invalidate = useInvalidateKnowledgeCustomizations();
  return useMutation({
    mutationFn: async (payload: CreateKnowledgeCustomizationPayload) => {
      const response = await apiClient.post<{ data: KnowledgeCustomization }>(
        `${KNOWLEDGE_API_ROOT}/customizations`,
        payload,
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

export function usePublishKnowledgeCustomization() {
  const invalidate = useInvalidateKnowledgeCustomizations();
  return useMutation({
    mutationFn: async (payload: {
      customizationId: string;
      reason: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const response = await apiClient.post<{ data: KnowledgeCustomization }>(
        `${KNOWLEDGE_API_ROOT}/customizations/${encodeURIComponent(
          payload.customizationId,
        )}:publish`,
        { reason: payload.reason, publishEvidence: payload.publishEvidence },
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

export function useRestorePlatformKnowledge() {
  const invalidate = useInvalidateKnowledgeCustomizations();
  return useMutation({
    mutationFn: async (payload: { customizationId: string; reason: string }) => {
      const response = await apiClient.post<{ data: KnowledgeCustomization }>(
        `${KNOWLEDGE_API_ROOT}/customizations/${encodeURIComponent(
          payload.customizationId,
        )}:restore-platform`,
        { reason: payload.reason },
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

// ──────────────────────────────────────────
// 诊断知识治理 · 线3 Spec 1/2/3
// ──────────────────────────────────────────

const DIAGNOSIS_API_ROOT = `${KNOWLEDGE_API_ROOT}/diagnosis`;

export type DiagnosisDirection = "SUPPORTING" | "REFUTING" | "REQUIRED" | "EXCLUSION";
export type DiagnosisWeight = "MAJOR" | "MINOR";
export type DiagnosisConfidence = "STRONG" | "MODERATE" | "WEAK" | "EXCLUDE";
export type DiagnosisCarePointerType = "TREATMENT" | "WORKUP" | "PATHWAY";
export type DiagnosisCareTargetType = "RULE" | "KNOWLEDGE" | "PATHWAY";

export interface DiagnosisCriterion {
  id: number;
  diagnosisVersionId: number;
  findingTermCode: string;
  direction: DiagnosisDirection;
  weight: DiagnosisWeight;
  valueConstraint?: string | null;
  temporalConstraint?: string | null;
  citationId?: number | null;
}

export interface DiagnosisDifferential {
  id: number;
  diagnosisVersionId: number;
  differentialIdentityId: number;
  keyPoint?: string | null;
  suggestedWorkup?: string | null;
}

export interface DiagnosisCarePointer {
  id: number;
  diagnosisVersionId: number;
  pointerType: DiagnosisCarePointerType;
  targetType: DiagnosisCareTargetType;
  targetRef: string;
  isSoft: boolean;
  description?: string | null;
}

export interface DiagnosisTestCase {
  id: number;
  diagnosisVersionId: number;
  caseCode: string;
  findings: string;
  expectedIdentityId: number;
  expectedConfidence: DiagnosisConfidence;
}

export interface DiagnosisAssetCreatePayload {
  identity: {
    identitySlug: string;
    subject: string;
    assetSpecialtyId?: string;
    description?: string;
  };
  source: {
    sourceCode: string;
    sourceType: string;
    authorityLevel: string;
    authorityBasis: string;
    title: string;
    publisher?: string;
    license?: string;
    language?: string;
    versionNo: string;
    publishedAt?: string;
    fileUri: string;
    content: string;
  };
  version: {
    versionNo: string;
    versionLabel?: string;
    riskLevel: string;
    gradeQuality: string;
    gradeStrength?: string;
    reviewCycleMonths: number;
  };
  evidence: {
    anchorPath: string;
    anchorLabel: string;
    textExcerpt: string;
  };
}

export type DiagnosisVersionCreatePayload = Omit<DiagnosisAssetCreatePayload, "identity">;

export interface DiagnosisAssetDraftResponse {
  identity: KnowledgeIdentity;
  version: KnowledgeAssetVersion;
}

export interface KnowledgeVersionsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function useKnowledgeVersions(identityId?: number, params: KnowledgeVersionsParams = {}) {
  return useQuery({
    queryKey: ["knowledge", "versions", identityId, params],
    enabled: Boolean(identityId),
    queryFn: async () => {
      if (!identityId) return emptyPage<KnowledgeAssetVersion>();
      const { data } = await apiClient.get<{ data: PageResponse<KnowledgeAssetVersion> }>(
        `${KNOWLEDGE_API_ROOT}/identities/${identityId}/versions`,
        { params },
      );
      return data.data ?? emptyPage<KnowledgeAssetVersion>();
    },
  });
}

export function useCreateDiagnosisAsset() {
  const profile = useSecurityProfile().data;
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: DiagnosisAssetCreatePayload) => {
      const { data } = await apiClient.post<{ data: DiagnosisAssetDraftResponse }>(
        `${DIAGNOSIS_API_ROOT}/assets`,
        withStandardApiContext(payload, profile, "AUTHORING"),
      );
      return data.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge", "identities"] });
    },
  });
}

export function useCreateDiagnosisVersion() {
  const profile = useSecurityProfile().data;
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      identityId,
      payload,
    }: {
      identityId: number;
      payload: DiagnosisVersionCreatePayload;
    }) => {
      const { data } = await apiClient.post<{ data: DiagnosisAssetDraftResponse }>(
        `${DIAGNOSIS_API_ROOT}/identities/${identityId}/versions`,
        withStandardApiContext(payload, profile, "AUTHORING"),
      );
      return data.data;
    },
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["knowledge", "identities"] }),
        queryClient.invalidateQueries({
          queryKey: ["knowledge", "versions", variables.identityId],
        }),
      ]);
    },
  });
}

function useDiagnosisList<T>(versionId: number | undefined, resource: string) {
  return useQuery({
    queryKey: ["diagnosis", resource, versionId],
    enabled: Boolean(versionId),
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: T[] }>(
        `${DIAGNOSIS_API_ROOT}/versions/${versionId}/${resource}`,
      );
      return data.data;
    },
  });
}

function useDiagnosisCreate<TPayload extends object, TResult>(resource: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ versionId, payload }: { versionId: number; payload: TPayload }) => {
      const { data } = await apiClient.post<{ data: TResult }>(
        `${DIAGNOSIS_API_ROOT}/versions/${versionId}/${resource}`,
        payload,
      );
      return data.data;
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: ["diagnosis", resource, variables.versionId],
      });
    },
  });
}

export function useDiagnosisCriteria(versionId?: number) {
  return useDiagnosisList<DiagnosisCriterion>(versionId, "criteria");
}

export function useDiagnosisDifferentials(versionId?: number) {
  return useDiagnosisList<DiagnosisDifferential>(versionId, "differentials");
}

export function useDiagnosisCarePointers(versionId?: number) {
  return useDiagnosisList<DiagnosisCarePointer>(versionId, "care-pointers");
}

export function useDiagnosisTestCases(versionId?: number) {
  return useDiagnosisList<DiagnosisTestCase>(versionId, "test-cases");
}

export function useAddDiagnosisCriterion() {
  return useDiagnosisCreate<
    Omit<DiagnosisCriterion, "id" | "diagnosisVersionId">,
    DiagnosisCriterion
  >("criteria");
}

export function useAddDiagnosisDifferential() {
  return useDiagnosisCreate<
    Omit<DiagnosisDifferential, "id" | "diagnosisVersionId">,
    DiagnosisDifferential
  >("differentials");
}

export function useAddDiagnosisCarePointer() {
  return useDiagnosisCreate<
    Omit<DiagnosisCarePointer, "id" | "diagnosisVersionId" | "isSoft">,
    DiagnosisCarePointer
  >("care-pointers");
}

export function useAddDiagnosisTestCase() {
  return useDiagnosisCreate<
    Omit<DiagnosisTestCase, "id" | "diagnosisVersionId">,
    DiagnosisTestCase
  >("test-cases");
}

export function usePublishDiagnosis() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      identityId,
      versionId,
      reason,
      publishEvidence,
    }: {
      identityId: number;
      versionId: number;
      reason: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const { data } = await apiClient.post<{ data: KnowledgeAssetVersion }>(
        `${DIAGNOSIS_API_ROOT}/identities/${identityId}/versions/${versionId}/publish`,
        {
          reason,
          ...(publishEvidence ? { publishEvidence } : {}),
        },
      );
      return data.data;
    },
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["knowledge", "identities"] }),
        queryClient.invalidateQueries({
          queryKey: ["knowledge", "versions", variables.identityId],
        }),
      ]);
    },
  });
}

export function useTerminologyMappings(params?: TerminologyMappingsParams) {
  const requestParams = compactOneBasedPageParams(params ?? {});
  return useQuery({
    queryKey: ["terminology", "mappings", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<TermMapping> }>(
        "/engine/terminology/mappings",
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export interface StandardTermsParams {
  page?: number;
  size?: number;
  sort?: string;
  standardSystem?: string;
  category?: TermCategory;
  status?: StandardTerm["status"];
  keyword?: string;
}

export function useStandardTerms(params: StandardTermsParams = {}) {
  const requestParams = compactOneBasedPageParams(params);
  return useQuery({
    queryKey: ["terminology", "standard-terms", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<StandardTerm> }>(
        `${TERMINOLOGY_API_ROOT}/terms/standard`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export interface MappingCoverageItem {
  code: string;
  status: "COVERED" | "UNMAPPED" | "NO_STANDARD_TERM";
  mappedLocalCount: number;
}

/** 对照覆盖分析（P5）：给定标准字典与标准编码集合，返回每个编码的院内→标准对照覆盖。 */
export function useMappingCoverage(
  params: { standardSystem?: string; codes: string[] },
  options?: { enabled?: boolean },
) {
  const codes = params.codes ?? [];
  return useQuery({
    queryKey: ["terminology", "coverage", params.standardSystem ?? "", codes],
    enabled: (options?.enabled ?? true) && !!params.standardSystem && codes.length > 0,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: MappingCoverageItem[] }>(
        `${TERMINOLOGY_API_ROOT}/mappings/coverage`,
        { params: { standardSystem: params.standardSystem, codes: codes.join(",") } },
      );
      return data.data;
    },
  });
}

export interface LocalTermsParams {
  page?: number;
  size?: number;
  sort?: string;
  sourceSystem?: string;
  category?: TermCategory;
  status?: LocalTerm["status"];
  keyword?: string;
}

export function useLocalTerms(params: LocalTermsParams = {}) {
  const requestParams = compactOneBasedPageParams(params);
  return useQuery({
    queryKey: ["terminology", "local-terms", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<LocalTerm> }>(
        `${TERMINOLOGY_API_ROOT}/terms/local`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export interface TerminologyCandidatesParams {
  page?: number;
  size?: number;
  sort?: string;
  status?: TermMappingCandidate["status"];
  riskLevel?: TermMappingCandidate["riskLevel"];
  conflictFlag?: boolean;
  generationJobCode?: string;
}

export function useTerminologyCandidates(params: TerminologyCandidatesParams = {}) {
  const requestParams = compactOneBasedPageParams(params);
  return useQuery({
    queryKey: ["terminology", "candidates", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<TermMappingCandidate> }>(
        `${TERMINOLOGY_API_ROOT}/mappings/candidates`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export interface TerminologyConflictsParams {
  page?: number;
  size?: number;
  sort?: string;
  status?: MappingConflict["status"];
  riskLevel?: MappingConflict["riskLevel"];
  conflictType?: MappingConflict["conflictType"];
}

export function useTerminologyConflicts(params: TerminologyConflictsParams = {}) {
  const requestParams = compactOneBasedPageParams(params);
  return useQuery({
    queryKey: ["terminology", "conflicts", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<MappingConflict> }>(
        `${TERMINOLOGY_API_ROOT}/mappings/conflicts`,
        { params: requestParams },
      );
      return data.data;
    },
  });
}

export function useTerminologyCandidateGenerationJob(jobCode?: string) {
  return useQuery({
    queryKey: ["terminology", "candidate-generation-job", jobCode],
    enabled: Boolean(jobCode),
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: TerminologyCandidateGenerationJob }>(
        `${TERMINOLOGY_API_ROOT}/mappings/candidate-generation-jobs/${jobCode}`,
      );
      return data.data;
    },
  });
}

export function useResolveTerminologyConflict() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      conflictId: number;
      request: { packageVersion: string; resolutionNote: string };
    }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: MappingConflict }>(
        `${TERMINOLOGY_API_ROOT}/mappings/conflicts/${payload.conflictId}/resolve`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["terminology", "mappings"] });
      queryClient.invalidateQueries({ queryKey: ["terminology", "candidates"] });
      queryClient.invalidateQueries({ queryKey: ["terminology", "conflicts"] });
    },
  });
}

export function useGenerateTerminologyCandidates() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      packageVersion: string;
      sourceSystem: string;
      minimumScore?: number;
      semanticAssistEnabled?: boolean;
    }) => {
      const { packageVersion, ...requestPayload } = payload;
      const { data } = await apiClient.post<{ data: TerminologyCandidateGenerationJob }>(
        `${TERMINOLOGY_API_ROOT}/mappings/candidates`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["terminology", "candidates"] });
      queryClient.invalidateQueries({ queryKey: ["terminology", "conflicts"] });
    },
  });
}

export function useConfirmTerminologyCandidate() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      candidateId: number;
      request: {
        packageVersion: string;
        reviewNote?: string;
        evidenceOverride?: string;
        highRiskAcknowledged?: boolean;
        highRiskReason?: string;
      };
    }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: TermMapping }>(
        `${TERMINOLOGY_API_ROOT}/mappings/${payload.candidateId}/confirm`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["terminology", "mappings"] });
      queryClient.invalidateQueries({ queryKey: ["terminology", "candidates"] });
      queryClient.invalidateQueries({ queryKey: ["terminology", "conflicts"] });
    },
  });
}

export function useRejectTerminologyCandidate() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      candidateId: number;
      request: { packageVersion: string; reviewNote: string };
    }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: TermMappingCandidate }>(
        `${TERMINOLOGY_API_ROOT}/mappings/${payload.candidateId}/reject`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["terminology", "candidates"] });
      queryClient.invalidateQueries({ queryKey: ["terminology", "conflicts"] });
    },
  });
}

export function useBatchConfirmTerminologyCandidates() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      candidateIds: number[];
      request: { packageVersion: string; reviewNote?: string };
    }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: TerminologyBatchConfirmResponse }>(
        `${TERMINOLOGY_API_ROOT}/mappings/batch-confirm`,
        withStandardApiContext(
          { ...requestPayload, candidateIds: payload.candidateIds },
          security.data,
          packageVersion,
        ),
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["terminology", "mappings"] });
      queryClient.invalidateQueries({ queryKey: ["terminology", "candidates"] });
    },
  });
}

export function useBuildTerminologyKnowledgePackage() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      packageCode: string;
      packageVersion: string;
      scopeLevel: string;
      scopeCode: string;
      name: string;
    }) => {
      const { data } = await apiClient.post<{ data: PackageResponse }>(
        `${PACKAGE_API_ROOT}/terminology`,
        withStandardApiContext(payload, security.data, payload.packageVersion),
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["packages", "list"] });
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

// ──────────────────────────────────────────
// 合规配置与审批中心（CONFIG-01 / SYS-06 / OPT-05）
// ──────────────────────────────────────────

export interface SystemConfigItem {
  key: string;
  value: string;
  valueType: string;
  displayName: string;
  risk: "LOW" | "MEDIUM" | "HIGH" | string;
  owner: string;
  description: string;
  source: string;
  protectedConfig: boolean;
  version: number;
  updatedAt: string;
}

export interface SystemConfigUpdatePayload {
  value: string;
  reason: string;
  expectedVersion?: number;
  confirmedHighRisk: boolean;
}

export type DataPermissionAction = "READ" | "EXPORT";
export type DataPermissionStatus = "ACTIVE" | "DISABLED";

export interface DataPermissionPolicy {
  policyId: string;
  tenantId: string;
  resourceType: string;
  action: DataPermissionAction;
  minDataLevel: string;
  allowedColumns: string[];
  groupId?: string | null;
  hospitalId?: string | null;
  campusId?: string | null;
  siteId?: string | null;
  departmentId?: string | null;
  wardId?: string | null;
  specialtyId?: string | null;
  status: DataPermissionStatus;
  version: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  traceId?: string | null;
}

export interface DataPermissionPolicyPayload {
  resourceType: string;
  action: DataPermissionAction;
  minDataLevel: string;
  allowedColumns: string[];
  groupId?: string;
  hospitalId?: string;
  campusId?: string;
  siteId?: string;
  departmentId?: string;
  wardId?: string;
  specialtyId?: string;
  status: DataPermissionStatus;
  reason: string;
  expectedVersion?: number;
}

export interface DataPermissionCheckPayload {
  resourceType: string;
  action: DataPermissionAction;
  groupId?: string;
  hospitalId?: string;
  campusId?: string;
  siteId?: string;
  departmentId?: string;
  specialtyId?: string;
  requestedColumns: string[];
}

export interface DataPermissionCheckResult {
  policyId?: string | null;
  resourceType: string;
  action: DataPermissionAction;
  requiredLevel: string;
  rowAllowed: boolean;
  allowedColumns: string[];
  deniedColumns: string[];
}

export type MaskingStrategy = "REDACT" | "KEEP_LAST" | "KEEP_FIRST_LAST" | "EMAIL" | "FIXED";
export type MaskingRuleStatus = "ACTIVE" | "INACTIVE";

export interface MaskingRule {
  ruleId: string;
  tenantId: string;
  resourceType: string;
  fieldName: string;
  scenarioCode?: string | null;
  strategy: MaskingStrategy;
  maskChar: string;
  prefixKeep: number;
  suffixKeep: number;
  status: MaskingRuleStatus;
  version: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  traceId?: string | null;
}

export interface MaskingRulePayload {
  resourceType: string;
  fieldName: string;
  scenarioCode?: string;
  strategy: MaskingStrategy;
  maskChar: string;
  prefixKeep: number;
  suffixKeep: number;
  status: MaskingRuleStatus;
  reason: string;
  expectedVersion?: number;
}

export interface DataPermissionPoliciesParams {
  resourceType?: string;
  action?: DataPermissionAction;
  page?: number;
  size?: number;
}

export interface MaskingRulesParams {
  resourceType?: string;
  fieldName?: string;
  page?: number;
  size?: number;
}

export interface MaskingPreviewPayload {
  resourceType: string;
  scenarioCode?: string;
  values: Record<string, unknown>;
  sensitiveFields: string[];
}

export interface MaskingPreviewResult {
  resourceType: string;
  scenarioCode?: string | null;
  values: Record<string, unknown>;
  maskedFields: string[];
  rawAllowed: boolean;
}

export interface InteropEvidence {
  mapId: string;
  sourceType: "EVIDENCE_SNAPSHOT" | "EMR_LEVEL_EVIDENCE_PACKAGE";
  sourceId: string;
  evidenceRef: string;
  evidenceSummary: string;
  fileUri?: string | null;
  payloadDigest?: string | null;
  sharedWithEmrLevel: boolean;
  traceId?: string | null;
}

export interface InteropAssessmentItem {
  itemId: string;
  standardVersion: string;
  dimension: "DATA_RESOURCE" | "STANDARDIZATION" | "INFRASTRUCTURE" | "APPLICATION_EFFECT";
  itemCode: string;
  itemName: string;
  requirementSummary: string;
  status: "SATISFIED" | "GAP" | "MISSING_EVIDENCE";
  evidenceCount: number;
  sharedWithEmrLevel: boolean;
  gapReason?: string | null;
  evidences: InteropEvidence[];
  traceId?: string | null;
}

export interface InteropAssessment {
  standardVersion: string;
  totalItems: number;
  satisfiedItems: number;
  gapItems: number;
  missingEvidenceItems: number;
  satisfactionRate: number;
  items: InteropAssessmentItem[];
  traceId?: string | null;
}

export type ExportApprovalStatus = "REQUESTED" | "APPROVED" | "REJECTED" | "EXPORTED";

export interface ExportApproval {
  approvalId: string;
  resourceType: string;
  exportScopeSnapshot: string;
  idempotencyKey: string;
  requestReason: string;
  status: ExportApprovalStatus;
  requestedBy: string;
  reviewerId?: string | null;
  reviewDecision?: "APPROVE" | "REJECT" | null;
  reviewComment?: string | null;
  approvalEvidenceId?: string | null;
  approvalEvidenceFileUri?: string | null;
  exportUri?: string | null;
  exportDigest?: string | null;
  exportEvidenceId?: string | null;
  exportEvidenceFileUri?: string | null;
  version: number;
  requestedAt: string;
  reviewedAt?: string | null;
}

export interface ExportApprovalsParams {
  resourceType?: string;
  status?: ExportApprovalStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ExportApprovalRequestPayload {
  resourceType: string;
  exportScope: Record<string, unknown>;
  reason: string;
  idempotencyKey: string;
}

export interface ExportApprovalReviewPayload {
  approvalId: string;
  decision: "APPROVE" | "REJECT";
  comment: string;
  expectedVersion: number;
}

export interface ExportApprovalCompletionPayload {
  approvalId: string;
  jobId: string;
  reason: string;
  expectedVersion: number;
}

export async function fetchSystemConfigs(prefix?: string): Promise<SystemConfigItem[]> {
  const { data } = await apiClient.get<{ data: SystemConfigItem[] }>("/system/configs", {
    params: prefix ? { prefix } : {},
  });
  return data.data ?? [];
}

export async function updateSystemConfig(
  key: string,
  payload: SystemConfigUpdatePayload,
): Promise<SystemConfigItem> {
  const { data } = await apiClient.patch<{ data: SystemConfigItem }>(
    `/system/configs/${encodeURIComponent(key)}`,
    payload,
  );
  return data.data;
}

export async function fetchTenantSystemConfigs(
  tenantId: string,
  prefix?: string,
): Promise<SystemConfigItem[]> {
  const { data } = await apiClient.get<{ data: SystemConfigItem[] }>(
    `/system/configs/tenants/${encodeURIComponent(tenantId)}`,
    {
      params: prefix ? { prefix } : {},
    },
  );
  return data.data ?? [];
}

export async function updateTenantSystemConfig(
  tenantId: string,
  key: string,
  payload: SystemConfigUpdatePayload,
): Promise<SystemConfigItem> {
  const { data } = await apiClient.patch<{ data: SystemConfigItem }>(
    `/system/configs/tenants/${encodeURIComponent(tenantId)}/${encodeURIComponent(key)}`,
    payload,
  );
  return data.data;
}

export async function fetchDataPermissionPolicies(
  params: DataPermissionPoliciesParams = {},
): Promise<PageResponse<DataPermissionPolicy>> {
  const { data } = await apiClient.get<{ data: PageResponse<DataPermissionPolicy> }>(
    "/compliance/data-permissions",
    { params },
  );
  return data.data;
}

export async function checkDataPermission(
  payload: DataPermissionCheckPayload,
): Promise<DataPermissionCheckResult> {
  const { data } = await apiClient.post<{ data: DataPermissionCheckResult }>(
    "/compliance/data-permissions:check",
    payload,
  );
  return data.data;
}

export async function upsertDataPermissionPolicy(
  payload: DataPermissionPolicyPayload,
): Promise<DataPermissionPolicy> {
  const { data } = await apiClient.put<{ data: DataPermissionPolicy }>(
    "/compliance/data-permissions",
    payload,
  );
  return data.data;
}

export async function fetchMaskingRules(
  params: MaskingRulesParams = {},
): Promise<PageResponse<MaskingRule>> {
  const { data } = await apiClient.get<{ data: PageResponse<MaskingRule> }>(
    "/compliance/masking-rules",
    {
      params,
    },
  );
  return data.data;
}

export async function previewMasking(
  payload: MaskingPreviewPayload,
): Promise<MaskingPreviewResult> {
  const { data } = await apiClient.post<{ data: MaskingPreviewResult }>(
    "/compliance/masking-rules:preview",
    payload,
  );
  return data.data;
}

export async function upsertMaskingRule(payload: MaskingRulePayload): Promise<MaskingRule> {
  const { data } = await apiClient.put<{ data: MaskingRule }>("/compliance/masking-rules", payload);
  return data.data;
}

export async function fetchInteropAssessment(standardVersion: string): Promise<InteropAssessment> {
  const { data } = await apiClient.get<{ data: InteropAssessment }>(
    "/compliance/interop-assessment",
    { params: { standardVersion } },
  );
  return data.data;
}

export async function fetchExportApprovals(
  params: ExportApprovalsParams = {},
): Promise<PageResponse<ExportApproval>> {
  const { data } = await apiClient.get<{ data: PageResponse<ExportApproval> }>(
    "/compliance/exports",
    {
      params,
    },
  );
  return data.data;
}

export async function requestExportApproval(
  payload: ExportApprovalRequestPayload,
): Promise<ExportApproval> {
  const { data } = await apiClient.post<{ data: ExportApproval }>(
    "/compliance/exports:request",
    payload,
  );
  return data.data;
}

export async function reviewExportApproval(
  payload: ExportApprovalReviewPayload,
): Promise<ExportApproval> {
  const { approvalId, ...request } = payload;
  const { data } = await apiClient.post<{ data: ExportApproval }>(
    `/compliance/exports/${encodeURIComponent(approvalId)}:approve`,
    request,
  );
  return data.data;
}

export async function completeApprovedExportJob(
  payload: ExportApprovalCompletionPayload,
): Promise<ExportApproval> {
  const { approvalId, ...request } = payload;
  const { data } = await apiClient.post<{ data: ExportApproval }>(
    `/compliance/exports/${encodeURIComponent(approvalId)}:complete-from-job`,
    request,
  );
  return data.data;
}

export function useSystemConfigs(prefix?: string) {
  return useQuery({
    queryKey: ["system", "configs", prefix ?? ""],
    queryFn: () => fetchSystemConfigs(prefix),
  });
}

export function useTenantSystemConfigs(tenantId: string, prefix?: string, enabled = true) {
  return useQuery({
    queryKey: ["system", "configs", "tenant", tenantId, prefix ?? ""],
    queryFn: () => fetchTenantSystemConfigs(tenantId, prefix),
    enabled: enabled && tenantId.trim().length > 0,
  });
}

export function useUpdateSystemConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ key, payload }: { key: string; payload: SystemConfigUpdatePayload }) =>
      updateSystemConfig(key, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["system", "configs"] }),
  });
}

export function useUpdateTenantSystemConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      tenantId,
      key,
      payload,
    }: {
      tenantId: string;
      key: string;
      payload: SystemConfigUpdatePayload;
    }) => updateTenantSystemConfig(tenantId, key, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["system", "configs"] }),
  });
}

export function useDataPermissionPolicies(params: DataPermissionPoliciesParams = {}) {
  return useQuery({
    queryKey: ["compliance", "data-permissions", params],
    queryFn: () => fetchDataPermissionPolicies(params),
  });
}

export function useCheckDataPermission() {
  return useMutation({ mutationFn: checkDataPermission });
}

export function useUpsertDataPermissionPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: upsertDataPermissionPolicy,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["compliance", "data-permissions"] }),
  });
}

export function useMaskingRules(params: MaskingRulesParams = {}) {
  return useQuery({
    queryKey: ["compliance", "masking-rules", params],
    queryFn: () => fetchMaskingRules(params),
  });
}

export function usePreviewMasking() {
  return useMutation({ mutationFn: previewMasking });
}

export function useUpsertMaskingRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: upsertMaskingRule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["compliance", "masking-rules"] }),
  });
}

export function useInteropAssessment(standardVersion: string) {
  return useQuery({
    queryKey: ["compliance", "interop-assessment", standardVersion],
    queryFn: () => fetchInteropAssessment(standardVersion),
    enabled: Boolean(standardVersion.trim()),
  });
}

export function useExportApprovals(params: ExportApprovalsParams = {}, enabled = true) {
  return useQuery({
    queryKey: ["compliance", "export-approvals", params],
    queryFn: () => fetchExportApprovals(params),
    enabled,
  });
}

export function useRequestExportApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: requestExportApproval,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["compliance", "export-approvals"] }),
  });
}

export function useReviewExportApproval() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: reviewExportApproval,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["compliance", "export-approvals"] }),
  });
}

export function useCompleteApprovedExportJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: completeApprovedExportJob,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["compliance", "export-approvals"] }),
  });
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
export type RuleRiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type VersionedAssetStatus =
  | "DRAFT"
  | "IN_REVIEW"
  | "APPROVED"
  | "PUBLISHED"
  | "DEPRECATED"
  | "RETIRED";

export interface VersionElectronicSignature {
  signatureId: string;
  signerId: string;
  signerName: string;
  signedAt: string;
  signatureHash: string;
}

export interface VersionPublishQualityGate {
  schemaValid: boolean;
  terminologyBindingComplete: boolean;
  dependencyIntegrityVerified: boolean;
  safetyMonotonicityVerified: boolean;
  impactSimulationPassed: boolean;
  peerReviewSigned: boolean;
  summary?: string;
}

export interface VersionPublishEvidence {
  electronicSignature?: VersionElectronicSignature;
  qualityGate?: VersionPublishQualityGate;
}

export interface RuleDefinition {
  id: number;
  ruleId: string;
  tenantId: string;
  ruleCode: string;
  name: string;
  ruleType: RuleType;
  authoringMode: "DSL" | "VISUAL" | string;
  riskLevel: RuleRiskLevel;
  priority: number;
  suppressedBy?: string | null;
  dedupeWindowSeconds: number;
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
  contextSnapshotId: string;
  inputPayload: string;
  expectedHit: boolean;
  expectedSeverity?: RuleRiskLevel | string | null;
  expectedActionCode?: string | null;
  lastHit?: boolean | null;
  lastStatus?: "NOT_RUN" | "PASS" | "FAIL" | "ERROR" | string;
  lastMessage?: string | null;
  lastRunAt?: string | null;
  createdAt: string;
}

export interface RuleDetailResponse {
  definition: RuleDefinition;
  version: RuleVersion;
  testCases: RuleTestCase[];
  deploymentStatus: VersionedAssetStatus;
  governance: RuleGovernanceResponse;
}

export interface RuleEvaluationItem {
  executionId: string;
  ruleId: string;
  versionId: string;
  hit: boolean;
  severity: RuleRiskLevel | null;
  actions: RuleActionResult[];
  explanation: unknown;
  status: "SUCCESS" | "MISS" | "NOT_APPLICABLE" | "SUPPRESSED" | "DEDUPLICATED" | "FAILED" | string;
  suppressedBy?: string | null;
  deduplicatedFromExecutionId?: string | null;
}

export interface RuleActionResult {
  actionCode: "INFO" | "REMIND" | "STRONG_REMINDER" | "BLOCK" | "SUGGEST_ORDER" | "AUTO_DOCUMENT";
  severity: RuleRiskLevel;
  indicator: "info" | "warning" | "critical";
  summary: string;
  detail: string;
  source: CdsHookSource;
  suggestions: CdsHookSuggestion[];
  overrideReasons: string[];
  requiresPhysicianConfirmation: boolean;
}

export interface CdsHookSource {
  label: string;
  url?: string | null;
  evidenceLevel?: string | null;
}

export interface CdsHookSuggestion {
  label: string;
  actionType: string;
  payload: unknown;
}

export interface CdsHookCard {
  uuid: string;
  summary: string;
  detail: string;
  indicator: "info" | "warning" | "critical";
  source: CdsHookSource;
  suggestions: CdsHookSuggestion[];
  overrideReasons: string[];
  requiresPhysicianConfirmation: boolean;
}

export interface RuleTestCaseResult {
  caseId: string;
  caseType: string;
  expectedHit: boolean;
  actualHit: boolean;
  expectedSeverity?: RuleRiskLevel | string | null;
  actualSeverity?: RuleRiskLevel | string | null;
  status: "PASS" | "FAIL" | "ERROR" | string;
  message: string;
}

export interface RuleTestRunResponse {
  ruleId: string;
  versionId: string;
  allPassed: boolean;
  results: RuleTestCaseResult[];
  traceId: string;
}

export interface RuleImpactObject {
  objectType: string;
  objectId: string;
  displayName: string;
  impactReason: string;
}

export interface RuleImpactResponse {
  ruleId: string;
  versionId: string;
  riskLevel: RuleRiskLevel | string;
  analysisStatus: "COMPLETE" | "PARTIAL" | string;
  impactDigest: string;
  affectedRules: RuleImpactObject[];
  affectedPathways: RuleImpactObject[];
  inPathPatients: RuleImpactObject[];
  integrationAdapters: RuleImpactObject[];
  unavailableScopes: string[];
  traceId: string;
}

export type RuleGovernanceState =
  | "DRAFT"
  | "PEER_REVIEW"
  | "COMMITTEE"
  | "SHADOW"
  | "CANARY"
  | "FULL"
  | "MONITOR"
  | "RETIRED";

export type RuleSignoffStage = "PEER_REVIEW" | "COMMITTEE";
export type RuleSignoffDecision = "APPROVED" | "REJECTED";
export type RuleShadowFeedbackDecision = "TRUE_POSITIVE" | "FALSE_POSITIVE";

export interface RuleSignoff {
  signoffId: string;
  ruleVersionId: string;
  stage: RuleSignoffStage;
  reviewRound: number;
  signerRole: string;
  signerId: string;
  decision: RuleSignoffDecision;
  reason: string;
  signedAt: string;
}

export interface RuleGovernanceResponse {
  ruleId: string;
  versionId: string;
  state: RuleGovernanceState;
  requiredSignoffs: number;
  reviewRound: number;
  committeeApprovalCount: number;
  authorId: string;
  lastReason: string;
  signoffs: RuleSignoff[];
  testResults: RuleTestCaseResult[];
  impactDigest?: string | null;
  impactStatus?: string | null;
  releaseEvidence: string[];
  traceId: string;
}

export interface RuleShadowStatsResponse {
  ruleId: string;
  totalExecutions: number;
  hitCount: number;
  missCount: number;
  falsePositiveCount: number;
  hitRate: number;
  falsePositiveRate: number;
  traceId: string;
}

export interface RuleShadowFeedbackResponse {
  feedbackId: string;
  executionId: string;
  ruleId: string;
  decision: RuleShadowFeedbackDecision;
  reason: string | null;
  assessedBy: string;
  assessedAt: string;
  traceId: string;
}

export interface RuleBacktestResponse {
  backtestId: string;
  ruleId: string;
  versionId: string;
  cohortRef: string | null;
  sampleCount: number;
  truePositiveCount: number;
  falsePositiveCount: number;
  trueNegativeCount: number;
  falseNegativeCount: number;
  sensitivity: number;
  specificity: number;
  accuracy: number;
  fireRate: number;
  falsePositiveCaseIds: string[];
  falseNegativeCaseIds: string[];
  createdAt: string;
  traceId: string;
}

export type RuleDriftStatus = "STABLE" | "WARNING";

export interface RuleDriftSnapshotResponse {
  driftId: string;
  ruleId: string;
  versionId: string;
  baselineBacktestId: string;
  windowStart: string;
  windowEnd: string;
  sampleCount: number;
  hitCount: number;
  baselineFireRate: number;
  currentFireRate: number;
  driftDelta: number;
  threshold: number;
  status: RuleDriftStatus;
  createdAt: string;
  traceId: string;
}

export interface RuleEvaluateResponse {
  requestId: string;
  traceId: string;
  highestSeverity: RuleRiskLevel | string | null;
  items: RuleEvaluationItem[];
  cards: CdsHookCard[];
}

export interface RuleExplanationResponse {
  executionId: string;
  ruleId: string;
  versionId: string;
  triggerPoint: string;
  eventId: string;
  inputDigest: string;
  hit: boolean;
  severity: RuleRiskLevel | string | null;
  actions?: unknown;
  explanation?: unknown;
  status:
    | "SUCCESS"
    | "SHADOW_RECORDED"
    | "MISS"
    | "NOT_APPLICABLE"
    | "SUPPRESSED"
    | "DEDUPLICATED"
    | "FAILED"
    | string;
  traceId: string;
}

export interface RuleExecutionSummary {
  executionId: string;
  ruleId: string;
  versionId: string;
  triggerPoint: string;
  hit: boolean;
  severity: RuleRiskLevel | string | null;
  status:
    | "SUCCESS"
    | "SHADOW_RECORDED"
    | "MISS"
    | "NOT_APPLICABLE"
    | "SUPPRESSED"
    | "DEDUPLICATED"
    | "FAILED"
    | string;
  executedAt: string;
  traceId: string;
}

export interface DiagnoseResponse {
  executionId: string;
  traceId: string;
  ruleId: string;
  inputPayloadSummary: string;
  explanationSnapshot: string;
  confidenceScore?: number;
  riskLevel?: RuleRiskLevel;
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
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export function useRuleDefinitions(
  params?: RuleFilterParams,
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["rules", "definitions", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<RuleDefinition> }>(
        "/engine/rule/rules",
        { params },
      );
      return data.data;
    },
  });
}

export type EngineAssetType =
  | "KNOWLEDGE"
  | "TERMINOLOGY"
  | "RULE"
  | "PATHWAY"
  | "EVALUATION"
  | "FOLLOWUP"
  | "FIELD_CATALOG"
  | "PACKAGE"
  | "RECOMMENDATION"
  | "SAFETY"
  | "CDSS_RISK"
  | "CONDITION_FRAGMENT"
  | "VALUE_SET"
  | "ORDER_SET"
  | "ACTION_CARD"
  | "SUBPATHWAY";

export type AuthoringPreviewSubject = "RULE_CONDITION" | "PATHWAY_GUARD";

export interface AuthoringPreviewSegment {
  kind: string;
  path: string;
  text: string;
}

export interface AuthoringPreviewResponse {
  previewText: string;
  lines: string[];
  segments: AuthoringPreviewSegment[];
  warnings: string[];
  traceId: string;
}

export interface AuthoringPreviewPayload {
  subject: AuthoringPreviewSubject;
  packageVersion: string;
  dsl: unknown;
}

export interface AuthoringPreviewRunEvidence {
  fact: string;
  sourcePath?: string | null;
  operator: string;
  expected?: unknown;
  actual?: unknown;
  matched: boolean;
  missing: boolean;
  value?: unknown;
  unit?: string | null;
  source?: string | null;
  formula?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
}

export interface AuthoringPreviewRunResponse {
  subject: AuthoringPreviewSubject;
  snapshotId: string;
  packageVersion: string;
  matched: boolean;
  hit?: boolean | null;
  outcomeText: string;
  severity?: string | null;
  actions?: unknown[];
  explanation?: unknown;
  conditionEvidence: AuthoringPreviewRunEvidence[];
  contextQualityStatus?: string | null;
  missingFields?: unknown[];
  mappingStatus?: Record<string, string>;
  contextResourceCounts?: Record<string, number>;
  nodeTrajectory?: string[];
  finalStatus?: string | null;
  selectedEdgeCode?: string | null;
  traceId: string;
}

export interface AuthoringPreviewRunPayload {
  subject: AuthoringPreviewSubject;
  packageVersion: string;
  snapshotId: string;
  dsl: unknown;
  startNodeCode?: string;
  requestedNextNodeCodes?: string[];
}

export function useAuthoringPreview(
  payload: AuthoringPreviewPayload | null,
  options?: {
    enabled?: boolean;
  },
) {
  const security = useSecurityProfile();
  const dslKey = payload ? JSON.stringify(payload.dsl) : "";
  return useQuery({
    queryKey: ["authoring", "preview", payload?.subject, payload?.packageVersion, dslKey],
    enabled: (options?.enabled ?? true) && Boolean(payload) && Boolean(security.data),
    queryFn: async () => {
      if (!payload) {
        throw new Error("缺少创作预览请求。");
      }
      const { data } = await apiClient.post<{ data: AuthoringPreviewResponse }>(
        "/engine/authoring/preview",
        withStandardApiContext(
          {
            subject: payload.subject,
            dsl: payload.dsl,
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export function useAuthoringPreviewRun() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: AuthoringPreviewRunPayload) => {
      if (!security.data) {
        throw new Error("缺少安全上下文。");
      }
      const { data } = await apiClient.post<{ data: AuthoringPreviewRunResponse }>(
        "/engine/authoring/preview-run",
        withStandardApiContext(
          {
            subject: payload.subject,
            snapshot_id: payload.snapshotId,
            dsl: payload.dsl,
            startNodeCode: payload.startNodeCode,
            requestedNextNodeCodes: payload.requestedNextNodeCodes ?? [],
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export interface AuthoringAssetLibraryItem {
  assetType: EngineAssetType;
  assetId: string;
  assetCode: string;
  name: string;
  category?: string | null;
  tags: string[];
  version: string;
  status: string;
  packageVersion?: string | null;
  favorite: boolean;
  cloneable: boolean;
  updatedAt?: string | null;
}

export interface AuthoringAssetQueryParams {
  assetType?: EngineAssetType;
  keyword?: string;
  tag?: string;
  favoriteOnly?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface AuthoringAssetProfilePayload {
  category?: string | null;
  tags?: string[];
}

export interface AuthoringAssetProfileResponse {
  assetType: EngineAssetType;
  assetId: string;
  category?: string | null;
  tags: string[];
  traceId?: string | null;
}

export interface AuthoringAssetFavoriteResponse {
  assetType: EngineAssetType;
  assetId: string;
  favorite: boolean;
  traceId?: string | null;
}

export interface AuthoringAssetClonePayload {
  newCode: string;
  newName: string;
  newVersion: number;
  packageVersion: string;
}

export interface AuthoringAssetCloneResponse {
  sourceAssetType: EngineAssetType;
  sourceAssetId: string;
  clonedAssetType: EngineAssetType;
  clonedAssetId: string;
  clonedAssetCode: string;
  status: string;
}

const AUTHORING_ASSET_API_ROOT = "/engine/authoring/assets";
const AUTHORING_BATCH_API_ROOT = "/engine/authoring/batch";

export type AuthoringBatchJobType =
  | "RULE_GENERATE"
  | "RULE_PUBLISH"
  | "PACKAGE_IMPORT"
  | "PACKAGE_EXPORT"
  | "PACKAGE_DISTRIBUTE";

export type AuthoringBatchJobStatus =
  | "RUNNING"
  | "SUCCEEDED"
  | "PARTIAL_SUCCESS"
  | "FAILED"
  | "NOT_CONNECTED";

export type AuthoringBatchItemStatus = "SUCCEEDED" | "FAILED" | "NOT_CONNECTED";

export interface AuthoringBatchItemResponse {
  itemId: string;
  status: AuthoringBatchItemStatus;
  targetType?: string | null;
  targetId?: string | null;
  resultJson?: string | null;
  rollbackRef?: string | null;
  errorCode?: string | null;
  message: string;
  createdAt: string;
}

export interface AuthoringBatchJobResponse {
  jobId: string;
  jobType: AuthoringBatchJobType;
  status: AuthoringBatchJobStatus;
  totalCount: number;
  successCount: number;
  failureCount: number;
  retryableCount: number;
  resultSummaryJson?: string | null;
  items: AuthoringBatchItemResponse[];
  traceId: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuthoringBatchRuleGenerateRow {
  rowId: string;
  ruleCode: string;
  name: string;
  parameterBindings: Record<string, unknown>;
  packageVersion?: string;
  applicableOrgUnitId?: string;
  changeSummary?: string;
}

export interface AuthoringBatchRuleGenerateRequest {
  templateRuleId: string;
  rows: AuthoringBatchRuleGenerateRow[];
}

export interface AuthoringBatchRuleImpactItem {
  ruleId: string;
  versionId: string;
  riskLevel: RuleRiskLevel;
  analysisStatus: "COMPLETE" | "PARTIAL" | string;
  impactDigest: string;
  affectedCount: number;
  unavailableScopes: string[];
}

export interface AuthoringBatchRuleImpactResponse {
  totalCount: number;
  highRiskCount: number;
  criticalRiskCount: number;
  items: AuthoringBatchRuleImpactItem[];
  traceId: string;
}

export interface AuthoringBatchRulePublishRequest {
  targetState: RuleGovernanceState;
  reason: string;
  items: Array<{
    itemId: string;
    ruleId: string;
    impactDigest: string;
    highRiskConfirmed: boolean;
  }>;
}

export type ReleaseScopeType = "ALL" | "REGION" | "FACILITY" | "CAMPUS" | "DEPARTMENT" | "WARD";

export interface AuthoringBatchPackageDistributeRequest {
  items: Array<{
    itemId: string;
    packageId: string;
    targetOrgUnitId: string;
    strategy: "GRAYSCALE" | "FULL";
    scopeType: ReleaseScopeType;
    scopeValue?: string;
    adapterIds: string[];
    reason: string;
  }>;
}

export function useAuthoringAssets(
  params: AuthoringAssetQueryParams = {},
  options?: {
    enabled?: boolean;
  },
) {
  const requestParams = {
    ...(params.assetType ? { assetType: params.assetType } : {}),
    ...(params.keyword ? { keyword: params.keyword } : {}),
    ...(params.tag ? { tag: params.tag } : {}),
    ...(typeof params.favoriteOnly === "boolean" ? { favoriteOnly: params.favoriteOnly } : {}),
    ...(typeof params.page === "number" ? { page: params.page } : {}),
    ...(typeof params.size === "number" ? { size: params.size } : {}),
    ...(params.sort ? { sort: params.sort } : {}),
  };
  return useQuery({
    queryKey: ["authoring", "assets", requestParams],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<AuthoringAssetLibraryItem> }>(
        AUTHORING_ASSET_API_ROOT,
        { params: requestParams },
      );
      return (
        data.data ?? {
          items: [],
          page: (params.page ?? 0) + 1,
          size: params.size ?? 20,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        }
      );
    },
  });
}

export function useUpdateAuthoringAssetProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      assetType: EngineAssetType;
      assetId: string;
      request: AuthoringAssetProfilePayload;
    }) => {
      const { data } = await apiClient.put<{ data: AuthoringAssetProfileResponse }>(
        `${AUTHORING_ASSET_API_ROOT}/${payload.assetType}/${encodeURIComponent(
          payload.assetId,
        )}/profile`,
        payload.request,
      );
      return data.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["authoring", "assets"] }),
  });
}

export function useFavoriteAuthoringAsset() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { assetType: EngineAssetType; assetId: string }) => {
      const { data } = await apiClient.post<{ data: AuthoringAssetFavoriteResponse }>(
        `${AUTHORING_ASSET_API_ROOT}/${payload.assetType}/${encodeURIComponent(
          payload.assetId,
        )}/favorite`,
      );
      return data.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["authoring", "assets"] }),
  });
}

export function useUnfavoriteAuthoringAsset() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { assetType: EngineAssetType; assetId: string }) => {
      const { data } = await apiClient.delete<{ data: AuthoringAssetFavoriteResponse }>(
        `${AUTHORING_ASSET_API_ROOT}/${payload.assetType}/${encodeURIComponent(
          payload.assetId,
        )}/favorite`,
      );
      return data.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["authoring", "assets"] }),
  });
}

export function useCloneAuthoringAsset() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      assetType: EngineAssetType;
      assetId: string;
      request: AuthoringAssetClonePayload;
    }) => {
      const { data } = await apiClient.post<{ data: AuthoringAssetCloneResponse }>(
        `${AUTHORING_ASSET_API_ROOT}/${payload.assetType}/${encodeURIComponent(
          payload.assetId,
        )}/clone`,
        payload.request,
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["authoring", "assets"] });
      queryClient.invalidateQueries({ queryKey: ["authoring", "condition-fragments"] });
    },
  });
}

export function useAuthoringBatchJobs(options?: {
  enabled?: boolean;
  page?: number;
  size?: number;
}) {
  const page = options?.page ?? 1;
  const size = options?.size ?? 20;
  return useQuery({
    queryKey: ["authoring", "batch-jobs", { page, size }],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<AuthoringBatchJobResponse> }>(
        AUTHORING_BATCH_API_ROOT,
        { params: { page, size } },
      );
      return data.data;
    },
  });
}

function useAuthoringBatchMutation<TRequest>(path: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: TRequest) => {
      const { data } = await apiClient.post<{ data: AuthoringBatchJobResponse }>(
        `${AUTHORING_BATCH_API_ROOT}${path}`,
        payload,
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["authoring", "batch-jobs"] });
      queryClient.invalidateQueries({ queryKey: ["authoring", "assets"] });
      queryClient.invalidateQueries({ queryKey: ["rules"] });
      queryClient.invalidateQueries({ queryKey: ["packages"] });
    },
  });
}

export function useGenerateAuthoringBatchRules() {
  return useAuthoringBatchMutation<AuthoringBatchRuleGenerateRequest>("/rules/generate");
}

export function useAnalyzeAuthoringBatchRuleImpacts() {
  return useMutation({
    mutationFn: async (ruleIds: string[]) => {
      const { data } = await apiClient.post<{ data: AuthoringBatchRuleImpactResponse }>(
        `${AUTHORING_BATCH_API_ROOT}/rules/impact`,
        { ruleIds },
      );
      return data.data;
    },
  });
}

export function usePublishAuthoringBatchRules() {
  return useAuthoringBatchMutation<AuthoringBatchRulePublishRequest>("/rules/publish");
}

export function useImportAuthoringBatchPackages() {
  return useAuthoringBatchMutation<{
    items: Array<{ itemId: string; offlinePackageJson: string }>;
  }>("/packages/import");
}

export function useExportAuthoringBatchPackages() {
  return useAuthoringBatchMutation<{
    items: Array<{ itemId: string; packageId: string; targetOrgUnitId: string }>;
  }>("/packages/export");
}

export function useDistributeAuthoringBatchPackages() {
  return useAuthoringBatchMutation<AuthoringBatchPackageDistributeRequest>("/packages/distribute");
}

export type ConditionFragmentStatus = "DRAFT" | "ACTIVE" | "RETIRED";

export interface ConditionFragmentResponse {
  fragmentId: string;
  tenantId: string;
  fragmentCode: string;
  name: string;
  category?: string | null;
  bodyJson: unknown;
  versionNo: number;
  status: ConditionFragmentStatus;
  packageVersion: string;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  traceId?: string | null;
}

export interface ConditionFragmentUpsertPayload {
  fragmentCode: string;
  name: string;
  category?: string;
  bodyJson: unknown;
  versionNo: number;
  packageVersion: string;
  status?: ConditionFragmentStatus;
}

export interface ConditionFragmentAffectedAsset {
  assetType: string;
  assetId: string;
  assetCode: string;
  displayName: string;
  impactReason: string;
}

export interface ConditionFragmentImpactResponse {
  fragmentId: string;
  fragmentCode: string;
  versionNo: number;
  packageVersion: string;
  affectedAssets: PageResponse<ConditionFragmentAffectedAsset>;
  impactDigest: string;
  traceId?: string | null;
}

export function useConditionFragments(
  params?: {
    status?: ConditionFragmentStatus;
    packageVersion?: string;
    keyword?: string;
    page?: number;
    size?: number;
    sort?: string;
  },
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["authoring", "condition-fragments", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<ConditionFragmentResponse> }>(
        "/engine/authoring/fragments",
        { params },
      );
      return data.data;
    },
  });
}

export function useCreateConditionFragment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: ConditionFragmentUpsertPayload) => {
      const { data } = await apiClient.post<{ data: ConditionFragmentResponse }>(
        "/engine/authoring/fragments",
        payload,
      );
      return data.data;
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["authoring", "condition-fragments"] }),
  });
}

export function useUpdateConditionFragment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { fragmentId: string; body: ConditionFragmentUpsertPayload }) => {
      const { data } = await apiClient.put<{ data: ConditionFragmentResponse }>(
        `/engine/authoring/fragments/${payload.fragmentId}`,
        payload.body,
      );
      return data.data;
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["authoring", "condition-fragments"] }),
  });
}

export function useConditionFragmentImpact(
  fragmentId: string,
  params?: {
    page?: number;
    size?: number;
    sort?: string;
  },
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["authoring", "condition-fragment-impact", fragmentId, params ?? {}],
    enabled: (options?.enabled ?? true) && !!fragmentId,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ConditionFragmentImpactResponse }>(
        `/engine/authoring/fragments/${fragmentId}/impact`,
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
      const { data } = await apiClient.get<{ data: RuleDetailResponse }>(
        `/engine/rule/rules/${ruleId}`,
      );
      return data.data;
    },
    enabled: !!ruleId,
  });
}

export function useCreateRule() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      ruleCode: string;
      name: string;
      ruleType: RuleType;
      authoringMode: string;
      riskLevel: string;
      priority: number;
      suppressedBy?: string;
      dedupeWindowSeconds: number;
      packageVersion: string;
      sourceRef: string;
      changeSummary: string;
      dslJson: unknown;
      explanationJson: unknown;
      parameterBindings?: Record<string, unknown>;
    }) => {
      const { packageVersion, dslJson, explanationJson, ...rulePayload } = payload;
      const { data } = await apiClient.post<{ data: { ruleId: string } }>(
        "/engine/rule/rules",
        withStandardApiContext(
          { ...rulePayload, dsl: dslJson, explanation: explanationJson },
          security.data,
          packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export interface RuleOverrideResponse {
  overrideId: string;
  executionId: string;
  ruleId: string;
  actionCode: "BLOCK" | "STRONG_REMINDER";
  reason: string;
  overriddenBy: string;
  overriddenAt: string;
  traceId: string;
}

export function useCaptureRuleOverride() {
  return useMutation({
    mutationFn: async (payload: {
      executionId: string;
      actionCode: "BLOCK" | "STRONG_REMINDER";
      reason: string;
    }) => {
      const { executionId, ...request } = payload;
      const { data } = await apiClient.post<{ data: RuleOverrideResponse }>(
        `/engine/rule/rules/executions/${executionId}/override`,
        request,
      );
      return data.data;
    },
  });
}

export function useAddTestCase(ruleId: string) {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      packageVersion: string;
      caseType: string;
      contextSnapshotId: string;
      expectedHit: boolean;
      expectedSeverity?: string;
      expectedActionCode?: string;
    }) => {
      const { packageVersion, ...casePayload } = payload;
      const { data } = await apiClient.post<{ data: RuleTestCase }>(
        `/engine/rule/rules/${ruleId}/test-cases`,
        withStandardApiContext(casePayload, security.data, packageVersion),
      );
      return data.data;
    },
  });
}

export function useRunRuleTests(ruleId: string) {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: { packageVersion: string }) => {
      const { data } = await apiClient.post<{ data: RuleTestRunResponse }>(
        `/engine/rule/rules/${ruleId}/test`,
        withStandardApiContext({}, security.data, payload.packageVersion),
      );
      return data.data;
    },
  });
}

export function useSimulateRule(ruleId: string) {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: { packageVersion: string; inputPayload: unknown }) => {
      const { packageVersion, inputPayload } = payload;
      const { data } = await apiClient.post<{ data: RuleEvaluationItem }>(
        `/engine/rule/rules/${ruleId}/simulate`,
        withStandardApiContext({ context: inputPayload }, security.data, packageVersion),
      );
      return data.data;
    },
  });
}

export function useRuleImpact(
  ruleId: string,
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["rules", "impact", ruleId],
    enabled: (options?.enabled ?? true) && !!ruleId,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: RuleImpactResponse }>(
        `/engine/rule/rules/${ruleId}/impact`,
      );
      return data.data;
    },
  });
}

export function useSignoffRule() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      ruleId: string;
      packageVersion: string;
      stage: RuleSignoffStage;
      decision: RuleSignoffDecision;
      reason: string;
    }) => {
      const { data } = await apiClient.post<{ data: RuleGovernanceResponse }>(
        `/engine/rule/rules/${payload.ruleId}/governance/signoffs`,
        withStandardApiContext(
          {
            stage: payload.stage,
            decision: payload.decision,
            reason: payload.reason,
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export function useTransitionRuleGovernance() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      ruleId: string;
      packageVersion: string;
      targetState: RuleGovernanceState;
      impactDigest?: string;
      reason: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const { data } = await apiClient.post<{ data: RuleGovernanceResponse }>(
        `/engine/rule/rules/${payload.ruleId}/governance/transitions`,
        withStandardApiContext(
          {
            targetState: payload.targetState,
            impactDigest: payload.impactDigest,
            reason: payload.reason,
            ...(payload.publishEvidence ? { publishEvidence: payload.publishEvidence } : {}),
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export function useEvaluateRules() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      triggerPoint: string;
      packageVersion: string;
      contextSnapshotId: string;
    }) => {
      const { data } = await apiClient.post<{ data: RuleEvaluateResponse }>(
        "/engine/rule/rules/evaluate",
        withStandardApiContext(
          {
            triggerPoint: payload.triggerPoint,
            contextSnapshotId: payload.contextSnapshotId,
            eventId: crypto.randomUUID(),
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export function useRuleExecutionExplain(executionId: string) {
  return useQuery({
    queryKey: ["rules", "explain", executionId],
    queryFn: async () => {
      if (!executionId) return null;
      const { data } = await apiClient.get<{ data: RuleExplanationResponse }>(
        `/engine/rule/rules/executions/${executionId}/explain`,
      );
      return data.data;
    },
    enabled: !!executionId,
  });
}

export function useRuleExecutions(params?: { page?: number; size?: number }) {
  return useQuery({
    queryKey: ["rules", "executions", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<RuleExecutionSummary> }>(
        "/engine/rule/rules/executions",
        { params },
      );
      return data.data;
    },
  });
}

export function useRuleShadowStats(ruleId: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["rules", "shadow-stats", ruleId],
    queryFn: async () => {
      if (!ruleId) return null;
      const { data } = await apiClient.get<{ data: RuleShadowStatsResponse }>(
        `/engine/rule/rules/${ruleId}/shadow-stats`,
      );
      return data.data;
    },
    enabled: Boolean(ruleId) && (options?.enabled ?? true),
  });
}

export function useCaptureRuleShadowFeedback() {
  return useMutation({
    mutationFn: async (payload: {
      executionId: string;
      decision: RuleShadowFeedbackDecision;
      reason?: string;
    }) => {
      const { data } = await apiClient.post<{ data: RuleShadowFeedbackResponse }>(
        `/engine/rule/rules/executions/${payload.executionId}/shadow-feedback`,
        {
          decision: payload.decision,
          reason: payload.reason,
        },
      );
      return data.data;
    },
  });
}

export function useRuleBacktestLatest(ruleId: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["rules", "backtest", "latest", ruleId],
    queryFn: async () => {
      if (!ruleId) return null;
      const { data } = await apiClient.get<{ data: RuleBacktestResponse | null }>(
        `/engine/rule/rules/${ruleId}/backtest/latest`,
      );
      return data.data ?? null;
    },
    enabled: Boolean(ruleId) && (options?.enabled ?? true),
  });
}

export function useRunRuleBacktest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { ruleId: string; cohortRef?: string }) => {
      const { data } = await apiClient.post<{ data: RuleBacktestResponse }>(
        `/engine/rule/rules/${payload.ruleId}/backtest`,
        {
          cohortRef: payload.cohortRef,
        },
      );
      return data.data;
    },
    onSuccess: (_result, payload) => {
      queryClient.invalidateQueries({ queryKey: ["rules", "backtest", "latest", payload.ruleId] });
      queryClient.invalidateQueries({ queryKey: ["rules", "detail", payload.ruleId] });
    },
  });
}

export function useRuleDriftLatest(ruleId: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["rules", "drift", "latest", ruleId],
    queryFn: async () => {
      if (!ruleId) return null;
      const { data } = await apiClient.get<{ data: RuleDriftSnapshotResponse | null }>(
        `/engine/rule/rules/${ruleId}/drift/latest`,
      );
      return data.data ?? null;
    },
    enabled: Boolean(ruleId) && (options?.enabled ?? true),
  });
}

export function useCaptureRuleDriftSnapshot() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      ruleId: string;
      windowStart: string;
      windowEnd: string;
      baselineBacktestId?: string | null;
      threshold?: number;
    }) => {
      const { data } = await apiClient.post<{ data: RuleDriftSnapshotResponse }>(
        `/engine/rule/rules/${payload.ruleId}/drift`,
        {
          windowStart: payload.windowStart,
          windowEnd: payload.windowEnd,
          baselineBacktestId: payload.baselineBacktestId,
          threshold: payload.threshold,
        },
      );
      return data.data;
    },
    onSuccess: (_result, payload) => {
      queryClient.invalidateQueries({ queryKey: ["rules", "drift", "latest", payload.ruleId] });
      queryClient.invalidateQueries({ queryKey: ["rules", "detail", payload.ruleId] });
    },
  });
}

// ==================== Pathway 引擎相关的实体及 DTO 契约 ====================

export type PathwayTemplateStatus = "DRAFT" | "PUBLISHED" | "OFFLINE";
export type PathwayEntryMode = "AUTO_SUGGEST" | "MANUAL_CONFIRM";
export type PathwayTemplateLevel =
  | "STANDARD"
  | "GROUP"
  | "HOSPITAL"
  | "DEPARTMENT"
  | "SPECIALTY"
  | string;
export type PathwayNodeType =
  | "SCREENING"
  | "ASSESSMENT"
  | "EXAM"
  | "LAB"
  | "MEDICATION"
  | "SURGERY"
  | "NURSING"
  | "REHAB"
  | "DISCHARGE"
  | "FOLLOWUP"
  | "QUALITY"
  | "DECISION"
  | "PARALLEL"
  | "WAIT_TIMER"
  | "SUBPATHWAY"
  | "MANUAL_GATE"
  | "ORDER_SET";
export type PathwayEdgeType =
  | "DEFAULT"
  | "CONDITION"
  | "RISK_STRATIFICATION"
  | "PATIENT_CHOICE"
  | "RESOURCE_UNAVAILABLE"
  | "PHYSICIAN_DECISION"
  | "ROLLBACK"
  | "JOIN";
export type PatientPathwayStatus =
  | "ENTERED"
  | "NODE_EXECUTING"
  | "VARIANCE"
  | "COMPLETED"
  | "EXITED"
  | string;
export type ClinicalClockStatus = "RUNNING" | "COMPLETED" | "TIMEOUT" | "MISSING_DATA" | "VARIANCE";
export type ClinicalClockEscalationLevel = "NONE" | "REMINDER" | "REPORT" | "QUALITY_RECORD";
export type PathwayMilestoneStatus = "ACHIEVED" | "CURRENT" | "PENDING" | "OVERDUE" | string;
export type VarianceType = "CLINICAL" | "SYSTEM" | "PATIENT" | "FAMILY";
export type VarianceResolutionDecision = "HOLD" | "REENTER" | "TERMINATE";
export type PathwayAdvanceEventType = "COMPLETE" | "VARIANCE" | "EXIT";
export type PathwayOutcomeScope = "TEMPLATE" | "PHASE" | "MILESTONE";
export type PathwaySimulationMode = "SINGLE_SNAPSHOT" | "QUEUE_REPLAY" | "TIME_MACHINE";
export type PathwayCoordinationWarningType = "ORDER_SET_CONFLICT" | "CLOCK_WINDOW_OVERLAP" | string;

export interface PathwayTemplate {
  id?: number;
  templateId: string;
  packageId: string;
  templateCode: string;
  name: string;
  diseaseCode: string;
  templateVersion: number;
  templateLevel: PathwayTemplateLevel;
  parentTemplateId?: string;
  status: PathwayTemplateStatus;
  entryMode: PathwayEntryMode;
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
  milestoneCode?: string;
  sortOrder: number;
  responsibleRole?: string;
  accountableRole?: string;
  consultedRolesJson?: string;
  informedRolesJson?: string;
  dependencyJson?: string;
  timeWindowMinutes?: number;
  terminalFlag: boolean;
  disabledFlag?: boolean;
  configJson?: string;
  createdAt?: string;
  traceId?: string;
}

export interface PathwayMilestone {
  id?: number;
  milestoneId: string;
  templateId: string;
  phaseCode: string;
  phaseName: string;
  milestoneCode: string;
  name: string;
  dayOffset?: number;
  expectedOffsetMinutes?: number;
  achievementCriteriaJson?: string;
  sortOrder: number;
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

export interface PathwayOutcomeBinding {
  id?: number;
  bindingId: string;
  templateId: string;
  scope: PathwayOutcomeScope;
  refCode: string;
  indicatorCode: string;
  packageVersion?: string | null;
  createdAt?: string;
}

export interface PathwayTemplateDetailResponse {
  template: PathwayTemplate;
  milestones: PathwayMilestone[];
  nodes: PathwayNode[];
  edges: PathwayEdge[];
  metricBindings: SpecialtyMetricBinding[];
  outcomeBindings?: PathwayOutcomeBinding[];
  deploymentStatus: VersionedAssetStatus;
  traceId: string;
}

export interface PathwayTemplatePublishResponse {
  templateId: string;
  status: PathwayTemplateStatus;
  releaseStep: string;
  canaryPercent: number;
  impactDigest?: string | null;
  analysisStatus?: string | null;
  releaseEvidence: string[];
  traceId: string;
}

export interface PathwayTemplateImpactResponse {
  templateId: string;
  analysisStatus: "COMPLETE" | "PARTIAL" | string;
  affectedPatientPathways: number;
  nodeCount: number;
  edgeCount: number;
  timedNodeCount: number;
  terminalNodeCount: number;
  outcomeBindingCount?: number;
  canaryPercent: number;
  impactDigest: string;
  releaseEvidence: string[];
  traceId: string;
}

export type PathwayInheritanceOrigin = "INHERITED" | "OVERRIDDEN" | "ADDED";
export type PathwayInheritanceChangeType = "OVERRIDDEN" | "ADDED" | "DISABLED";

export interface PathwayTemplateInheritanceDiffItem {
  itemType: string;
  itemCode: string;
  changeType: PathwayInheritanceChangeType;
  fieldName?: string | null;
  parentValue?: string | null;
  childValue?: string | null;
}

export interface PathwayMergedNode {
  nodeCode: string;
  name: string;
  nodeType: PathwayNodeType;
  milestoneCode?: string | null;
  sortOrder?: number | null;
  responsibleRole?: string | null;
  accountableRole?: string | null;
  consultedRolesJson?: string | null;
  informedRolesJson?: string | null;
  dependencyJson?: string | null;
  timeWindowMinutes?: number | null;
  terminalFlag?: boolean | null;
  configJson?: string | null;
  origin: PathwayInheritanceOrigin;
}

export interface PathwayTemplateInheritanceDiffResponse {
  templateId: string;
  parentTemplateId?: string | null;
  diffItems: PathwayTemplateInheritanceDiffItem[];
  mergedNodes: PathwayMergedNode[];
  mergedEdges: PathwayEdge[];
  traceId: string;
}

export interface PathwaySimulationResponse {
  templateId: string;
  snapshotId?: string | null;
  nodeTrajectory: string[];
  finalStatus: PatientPathwayStatus;
  contextQualityStatus?: string | null;
  missingFields?: Array<Record<string, unknown>>;
  mappingStatus?: Record<string, string>;
  contextResourceCounts?: Record<string, number>;
  simulationMode?: PathwaySimulationMode;
  replaySteps?: PathwaySimulationReplayStep[];
  timeMachineAt?: string | null;
  traceId: string;
}

export interface PathwaySimulationReplayStep {
  snapshotId?: string | null;
  nodeTrajectory: string[];
  finalStatus: PatientPathwayStatus;
  contextQualityStatus?: string | null;
  missingFields?: Array<Record<string, unknown>>;
  mappingStatus?: Record<string, string>;
  contextResourceCounts?: Record<string, number>;
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
  reasonCode: string;
  reason: string;
  responsibleRole: string;
  resolutionDecision: VarianceResolutionDecision;
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
  dueAt?: string;
  completedAt?: string;
  status: ClinicalClockStatus;
  baselineEvent?: string;
  baselineAt?: string;
  minDueAt?: string;
  targetDueAt?: string;
  maxDueAt?: string;
  escalationLevel?: ClinicalClockEscalationLevel;
  escalationPolicyJson?: string;
  createdAt?: string;
  traceId?: string;
}

export interface PathwayMilestoneRuntimeStatus {
  milestoneId: string;
  phaseCode: string;
  phaseName: string;
  milestoneCode: string;
  name: string;
  dayOffset?: number;
  expectedOffsetMinutes?: number;
  nodeCodes: string[];
  status: PathwayMilestoneStatus;
  expectedAt?: string;
  achievedAt?: string;
}

export interface PatientPathwayDetailResponse {
  patientPathway: PatientPathway;
  milestoneStatuses: PathwayMilestoneRuntimeStatus[];
  variances: PathwayVariance[];
  clocks: ClinicalClock[];
  outcomeBindings?: PathwayOutcomeBinding[];
  coordinationWarnings?: PathwayCoordinationWarning[];
  traceId: string;
}

export interface PathwayCoordinationWarning {
  warningType: PathwayCoordinationWarningType;
  severity: string;
  patientPathwayId?: string | null;
  templateId?: string | null;
  nodeCode?: string | null;
  conflictWithPatientPathwayId?: string | null;
  conflictWithTemplateId?: string | null;
  conflictWithNodeCode?: string | null;
  sharedRef?: string | null;
  message: string;
}

export interface PathwayAdvanceResponse {
  patientPathwayId: string;
  previousNodeCode?: string | null;
  nextNodeCode?: string | null;
  status: PatientPathwayStatus;
  varianceId?: string | null;
  edgeCode?: string | null;
  edgeType?: PathwayEdgeType | null;
  snapshotId?: string | null;
  contextQualityStatus?: string | null;
  missingFields?: Array<Record<string, unknown>>;
  mappingStatus?: Record<string, string>;
  contextResourceCounts?: Record<string, number>;
  decisionEvidence?: Record<string, unknown>;
  followupPlanId?: string | null;
  followupTaskCount?: number;
  followupHandoffStatus?: string | null;
  outcomeBindings?: PathwayOutcomeBinding[];
  coordinationWarnings?: PathwayCoordinationWarning[];
  traceId: string;
}

// 1. PathwayTemplate Hooks
export function usePathwayTemplates(
  params?: {
    status?: PathwayTemplateStatus;
    diseaseCode?: string;
    packageId?: string;
    templateCode?: string;
    keyword?: string;
    page?: number;
    size?: number;
    sort?: string;
  },
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["pathways", "templates", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<PathwayTemplate> }>(
        "/engine/pathway/pathway-templates",
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
        `/engine/pathway/pathway-templates/${templateId}`,
      );
      return data.data;
    },
    enabled: !!templateId,
  });
}

export function usePathwayTemplateImpact(
  templateId: string,
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["pathways", "template-impact", templateId],
    enabled: (options?.enabled ?? true) && !!templateId,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PathwayTemplateImpactResponse }>(
        `/engine/pathway/pathway-templates/${templateId}/impact`,
      );
      return data.data;
    },
  });
}

export function usePathwayTemplateInheritanceDiff(
  templateId: string,
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["pathways", "template-inheritance-diff", templateId],
    enabled: (options?.enabled ?? true) && !!templateId,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PathwayTemplateInheritanceDiffResponse }>(
        `/engine/pathway/pathway-templates/${templateId}/inheritance-diff`,
      );
      return data.data;
    },
  });
}

export function useCreatePathwayTemplate() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      packageId: string;
      templateCode: string;
      name: string;
      diseaseCode: string;
      packageVersion: string;
      templateLevel: PathwayTemplateLevel;
      parentTemplateId?: string;
      templateVersion: number;
      entryMode: PathwayEntryMode;
      startNodeCode: string;
      sourceRef: string;
      description: string;
      entryCriteria?: unknown;
      exitCriteria?: unknown;
      milestones?: Array<{
        phaseCode: string;
        phaseName: string;
        milestoneCode: string;
        name: string;
        dayOffset?: number;
        expectedOffsetMinutes?: number;
        achievementCriteria?: unknown;
        sortOrder: number;
      }>;
      nodes: Array<{
        nodeCode: string;
        name: string;
        nodeType: PathwayNodeType;
        milestoneCode?: string;
        sortOrder: number;
        responsibleRole?: string;
        accountableRole?: string;
        consultedRoles?: string[];
        informedRoles?: string[];
        timeWindowMinutes?: number;
        terminal: boolean;
        disabled?: boolean;
        config?: unknown;
      }>;
      edges: Array<{
        edgeCode: string;
        fromNodeCode: string;
        toNodeCode: string;
        edgeType: PathwayEdgeType;
        condition?: unknown;
        priority: number;
      }>;
      metricBindings?: Array<{
        nodeCode: string;
        metricCode: string;
        required?: boolean;
      }>;
      outcomeBindings?: Array<{
        scope: PathwayOutcomeScope;
        refCode?: string;
        indicatorCode: string;
        packageVersion?: string;
      }>;
    }) => {
      const { packageVersion, ...templatePayload } = payload;
      const { data } = await apiClient.post<{ data: PathwayTemplateDetailResponse }>(
        "/engine/pathway/pathway-templates",
        withStandardApiContext(templatePayload, security.data, packageVersion),
      );
      return data.data;
    },
  });
}

export function usePublishPathwayTemplate() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      templateId: string;
      packageVersion: string;
      impactDigest?: string;
      reason?: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const { data } = await apiClient.post<{ data: PathwayTemplatePublishResponse }>(
        `/engine/pathway/pathway-templates/${payload.templateId}/publish`,
        withStandardApiContext(
          {
            impactDigest: payload.impactDigest,
            reason: payload.reason,
            releaseStep: "submit_review",
            directFullRollout: false,
            ...(payload.publishEvidence ? { publishEvidence: payload.publishEvidence } : {}),
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export function useFullRolloutPathwayTemplate() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      templateId: string;
      packageVersion: string;
      impactDigest?: string;
      reason?: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const { data } = await apiClient.post<{ data: PathwayTemplatePublishResponse }>(
        `/engine/pathway/pathway-templates/${payload.templateId}/rollout/full`,
        withStandardApiContext(
          {
            impactDigest: payload.impactDigest,
            reason: payload.reason,
            releaseStep: "full_rollout",
            directFullRollout: true,
            ...(payload.publishEvidence ? { publishEvidence: payload.publishEvidence } : {}),
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export function useRollbackPathwayTemplate() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      templateId: string;
      packageVersion: string;
      rollbackTargetTemplateId: string;
      impactDigest?: string;
      reason?: string;
    }) => {
      const { data } = await apiClient.post<{ data: PathwayTemplatePublishResponse }>(
        `/engine/pathway/pathway-templates/${payload.templateId}/rollback`,
        withStandardApiContext(
          {
            impactDigest: payload.impactDigest,
            reason: payload.reason,
            releaseStep: "evidence_rollback",
            rollbackTargetTemplateId: payload.rollbackTargetTemplateId,
          },
          security.data,
          payload.packageVersion,
        ),
      );
      return data.data;
    },
  });
}

export function useSimulatePathway(templateId: string) {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      packageVersion: string;
      simulationMode?: PathwaySimulationMode;
      replaySnapshotIds?: string[];
      timeMachineAt?: string;
      snapshotId?: string;
      startNodeCode?: string;
      requestedNextNodeCodes?: string[];
    }) => {
      const { packageVersion, ...simulatePayload } = payload;
      const { data } = await apiClient.post<{ data: PathwaySimulationResponse }>(
        `/engine/pathway/pathway-templates/${templateId}/simulate`,
        withStandardApiContext(simulatePayload, security.data, packageVersion),
      );
      return data.data;
    },
  });
}

// 3. PatientPathway Hooks
export function usePatientPathways(
  params: {
    patientId?: string;
    status?: PatientPathwayStatus;
    page?: number;
    size?: number;
    sort?: string;
  } = {},
) {
  return useQuery({
    queryKey: ["pathways", "patient-pathways", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<PatientPathway> }>(
        "/engine/pathway/patient-pathways",
        { params },
      );
      return data.data;
    },
  });
}

export function useEnterPatientPathway() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      contextSnapshotId: string;
      templateId: string;
      startNodeCode?: string;
      packageVersion: string;
    }) => {
      const { packageVersion, ...enterPayload } = payload;
      const { data } = await apiClient.post<{ data: PatientPathwayDetailResponse }>(
        "/engine/pathway/patient-pathways/enter",
        withStandardApiContext(enterPayload, security.data, packageVersion),
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
        `/engine/pathway/patient-pathways/${patientPathwayId}`,
      );
      return data.data;
    },
    enabled: !!patientPathwayId,
  });
}

export function useAdvancePatientPathway() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: {
      patientPathwayId: string;
      packageVersion: string;
      eventType: PathwayAdvanceEventType;
      currentNodeCode?: string;
      requestedNextNodeCode?: string;
      snapshotId?: string;
      varianceType?: VarianceType;
      varianceReasonCode?: string;
      varianceReason?: string;
      responsibleRole?: string;
      resolutionDecision?: VarianceResolutionDecision;
      resolutionAction?: string;
      exitReason?: string;
      eventId?: string;
    }) => {
      const { patientPathwayId, packageVersion, ...advancePayload } = payload;
      const { data } = await apiClient.post<{ data: PathwayAdvanceResponse }>(
        `/engine/pathway/patient-pathways/${patientPathwayId}/advance`,
        withStandardApiContext(advancePayload, security.data, packageVersion),
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
        `/engine/pathway/patient-pathways/${patientPathwayId}/clocks`,
      );
      return data.data;
    },
    enabled: !!patientPathwayId,
  });
}

export function usePatientPathwayVariances(patientPathwayId: string) {
  return useQuery({
    queryKey: ["pathways", "patient-variances", patientPathwayId],
    queryFn: async () => {
      if (!patientPathwayId) return [];
      const { data } = await apiClient.get<{ data: PathwayVariance[] }>(
        `/engine/pathway/patient-pathways/${patientPathwayId}/variances`,
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
  patientId?: string;
  encounterId?: string;
  scenarioCode?: string;
  cardType: RecommendationCardType;
  cardCode?: string;
  title: string;
  summary: string;
  suggestedAction?: string;
  riskLevel: RecommendationRiskLevel;
  interruptLevel: RecommendationInterruptLevel;
  status: RecommendationCardStatus;
  changeSummary?: string;
  requiresPhysicianConfirmation?: boolean;
  aiGenerated?: boolean;
  sourceSummary?: string;
  explanationJson?: string;
  fatigueKey?: string;
  expiresAt?: string;
  createdAt?: string;
  createdBy?: string;
  traceId?: string;
  // 嵌入与全屏决策终端可选扩展属性
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

export interface RecommendationTrigger {
  triggerId: string;
  triggerCode?: string;
  triggerType?: string;
  sourceEventId?: string;
  contextSnapshotId?: string;
  patientId?: string;
  encounterId?: string;
  patientPathwayId?: string;
  scenarioCode?: string;
  packageVersion?: string;
  occurredAt?: string;
  traceId?: string;
}

export interface ClinicalRecommendationCard extends RecommendationCard {
  patientId: string;
  encounterId?: string;
  patientPathwayId?: string;
  scenarioCode: string;
  triggerType: string;
  contextSnapshotId?: string;
  packageVersion?: string;
  occurredAt?: string;
}

export interface RecommendationCardDetailResponse {
  card: RecommendationCard;
  trigger?: RecommendationTrigger;
  sources: RecommendationSource[];
  feedback: RecommendationFeedback[];
  fatigueSignals: RecommendationFatigueSignal[];
  traceId: string;
}

export interface RecommendationStats {
  totalCount: number;
  pendingCount: number;
  acceptedCount: number;
  rejectedCount: number;
  dismissedCount: number;
  deferredCount: number;
  suppressedCount: number;
  expiredCount: number;
  acceptanceRatePercent: number;
  traceId?: string;
}

export interface RecommendationEvaluationResponse {
  triggerId: string;
  status: string;
  totalCardCount: number;
  visibleCardCount: number;
  suppressedCardCount: number;
  modelStatus: string;
  cards: RecommendationCard[];
  traceId: string;
}

export interface RecommendationFeedbackResponse {
  cardId: string;
  status: RecommendationCardStatus;
  traceId: string;
}

// 标准上下文快照驱动的客户面推荐评估。
export function useEvaluateRecommendations() {
  return useMutation({
    mutationFn: async (payload: {
      triggerCode: string;
      triggerType: string;
      scenarioCode: string;
      contextSnapshotId: string;
      patientId: string;
      encounterId?: string;
      packageVersion: string;
      sourceEventId?: string;
      patientPathwayId?: string;
      occurredAt?: string;
      candidateCards?: unknown[];
    }) => {
      const { data } = await apiClient.post<{ data: RecommendationEvaluationResponse }>(
        "/engine/recommendations:evaluate",
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
    encounterId?: string;
    triggerPoint?: string;
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

export function useClinicalRecommendationCards(
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
    queryKey: ["recommendations", "clinical-cards", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<ClinicalRecommendationCard> }>(
        "/engine/recommendations/clinical-cards",
        { params },
      );
      return data.data;
    },
  });
}

export function useRecommendationStats(
  params?: {
    status?: RecommendationCardStatus;
    riskLevel?: RecommendationRiskLevel;
    scenarioCode?: string;
    patientId?: string;
    triggerPoint?: string;
  },
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["recommendations", "stats", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: RecommendationStats }>(
        "/engine/recommendations/stats",
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
  | "GRAY"
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
  traceId?: string;
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
  traceId?: string;
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
  modelStatus: "MODEL_DISABLED";
  modelDowngradeReason?: string;
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

export interface QualityDashboardSummary {
  totalFindings: number;
  openFindings: number;
  closedFindings: number;
  waivedFindings: number;
  overdueRectificationTasks: number;
  activeAlerts: number;
}

export interface QualityDashboardHeatmapCell {
  departmentId: string;
  totalFindings: number;
  openFindings: number;
  highRiskFindings: number;
  hitRate: number;
  maxSeverity: QualityFindingSeverity | string;
  heatToken: string;
}

export type QualityValueMetricStatus = "AVAILABLE" | "NOT_AVAILABLE" | string;

export interface QualityValueMetric {
  id: string;
  metricCode: string;
  displayName: string;
  formula: string;
  formulaVersion: string;
  status: QualityValueMetricStatus;
  numerator: number;
  denominator: number;
  value: number | null;
  unit: string;
  dataSources: string[];
  explanation: string;
  calculatedAt: string;
}

export interface QualityValueMetricSummary {
  metrics: QualityValueMetric[];
}

export type QualityDashboardAlertStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | string;

export interface QualityDashboardAlert {
  alertId: string;
  alertType: string;
  status: QualityDashboardAlertStatus;
  departmentId: string | null;
  sourceType: string;
  sourceId: string;
  severity: QualityFindingSeverity | string;
  thresholdCode: string;
  thresholdValue: number | null;
  actualValue: number | null;
  title: string;
  evidenceSummary: string;
  createdAt: string;
  updatedAt: string;
  traceId: string | null;
}

export interface QualityDashboardResponse {
  summary: QualityDashboardSummary;
  heatmap: QualityDashboardHeatmapCell[];
  valueMetrics: QualityValueMetricSummary;
  activeAlerts: QualityDashboardAlert[];
  generatedAt: string;
}

export interface QualityDashboardAlertsResponse {
  items: QualityDashboardAlert[];
  offset: number;
  limit: number;
  total: number;
  hasNext: boolean;
}

export type QualityDashboardDrilldownType = "FINDING" | "ALERT" | "RECTIFICATION" | string;

export interface QualityDashboardDrilldownItem {
  sourceType: string;
  sourceId: string;
  departmentId: string | null;
  severity: QualityFindingSeverity | string;
  status: string;
  title: string;
  evidenceSummary: string;
  occurredAt: string;
  traceId: string | null;
}

export interface QualityEvidencePackage {
  packageId: string;
  generatedAt: string;
  scopeDigest: string;
  itemCount: number;
  items: unknown[];
}

export interface QualityDashboardDrilldownResponse {
  type: QualityDashboardDrilldownType;
  items: QualityDashboardDrilldownItem[];
  evidencePackage: QualityEvidencePackage | null;
  offset: number;
  limit: number;
  total: number;
  hasNext: boolean;
}

export type InsuranceIssueType = "CODING" | "FEE" | "DRG" | "CLAIM_STATUS" | string;
export type InsuranceIssueStatus =
  | "OPEN"
  | "RECTIFICATION_CREATED"
  | "RESOLVED"
  | "WAIVED"
  | string;
export type InsuranceAuditStatus = "ISSUE_FOUND" | "NO_ISSUE" | "INSUFFICIENT_DATA" | string;
export type CaseReviewStatus = "PASS" | "NON_COMPLIANT" | string;
export type DrgGroupingStatus = "MATCHED" | "MISMATCHED" | string;

export interface InsuranceIssueResponse {
  issueId: string;
  claimId: string;
  issueType: InsuranceIssueType;
  severity: QualityFindingSeverity | string;
  status: InsuranceIssueStatus;
  ruleCode: string;
  ruleVersion: string;
  claimAmount: number | null;
  thresholdAmount: number | null;
  evidenceSummary: string;
  traceId: string | null;
}

export interface InsuranceIssuePageItem {
  issueId: string;
  claimId: string;
  issueType: InsuranceIssueType;
  severity: QualityFindingSeverity | string;
  status: InsuranceIssueStatus;
  ruleCode: string;
  ruleVersion: string;
  claimAmount: number | null;
  thresholdAmount: number | null;
  evidenceSummary: string;
  departmentId: string | null;
  evaluationRunId: string | null;
  traceId: string | null;
  createdAt: string;
}

export interface InsuranceIssuesQueryParams {
  status?: InsuranceIssueStatus;
  severity?: QualityFindingSeverity | string;
  departmentId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface QualityCaseReviewRequest {
  contextSnapshotId: string;
  scenarioCode: string;
  packageVersion?: string;
  responsibleDepartmentId: string;
}

export interface QualityCaseReviewResponse {
  reviewId: string;
  reviewStatus: CaseReviewStatus;
  evaluationRunId: string;
  resultCount: number;
  findingCount: number;
  taskCount: number;
  modelStatus: string;
  modelDowngradeReason?: string;
  traceId: string;
}

export interface DrgGroupingRequest {
  contextSnapshotId: string;
  grouperVersion: string;
  expectedGroupCode: string;
  actualGroupCode: string;
  responsibleDepartmentId: string;
  explanation: string;
}

export interface DrgGroupingResponse {
  groupingId: string;
  groupingStatus: DrgGroupingStatus;
  expectedGroupCode: string;
  actualGroupCode: string;
  grouperVersion: string;
  explanation: string;
  traceId: string;
}

export interface InsuranceAuditRuleRequest {
  ruleCode: string;
  ruleVersion: string;
  issueType: InsuranceIssueType;
  severity: QualityFindingSeverity | string;
  maxAmount?: number;
  requiredClaimStatus?: string;
  requiredClaimType?: string;
  description: string;
}

export interface InsuranceAuditRequest {
  contextSnapshotId: string;
  scenarioCode: string;
  packageVersion?: string;
  indicatorId: string;
  responsibleDepartmentId: string;
  dueAt: string;
  rules: InsuranceAuditRuleRequest[];
}

export interface InsuranceAuditResponse {
  auditId: string;
  auditStatus: InsuranceAuditStatus;
  issues: InsuranceIssueResponse[];
  evaluationRunId: string | null;
  findingCount: number;
  taskCount: number;
  traceId: string;
}

export interface QualityDashboardQueryParams {
  from?: string;
  to?: string;
  departmentId?: string;
}

export interface QualityDashboardDrilldownQueryParams extends QualityDashboardQueryParams {
  type?: QualityDashboardDrilldownType;
  page?: number;
  size?: number;
}

export interface QualityAlertsQueryParams extends QualityDashboardQueryParams {
  status?: QualityDashboardAlertStatus;
  severity?: string;
  page?: number;
  size?: number;
}

export interface RectificationDispatchRequest {
  findingId: string;
  responsibleDepartmentId: string;
  assigneeUserId?: string;
  dueAt: string;
}

// 1. Indicator Lifecycle Hooks
export function useEvaluationIndicators(
  params?: {
    status?: EvaluationIndicatorStatus;
    subjectType?: EvaluationSubjectType;
    indicatorCode?: string;
    page?: number;
    size?: number;
    sort?: string;
  },
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["evaluations", "indicators", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<EvaluationIndicator> }>(
        "/engine/evaluation/indicators",
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
        "/engine/evaluation/indicators",
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
        `/engine/evaluation/indicators/${indicatorId}/submit`,
      );
      return data.data;
    },
  });
}

export function usePublishEvaluationIndicator() {
  return useMutation({
    mutationFn: async (payload: {
      indicatorId: string;
      reason: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const { data } = await apiClient.post<{ data: EvaluationIndicator }>(
        `/engine/evaluation/indicators/${payload.indicatorId}/publish`,
        {
          reason: payload.reason,
          ...(payload.publishEvidence ? { publishEvidence: payload.publishEvidence } : {}),
        },
      );
      return data.data;
    },
  });
}

export function useGrayEvaluationIndicator() {
  return useMutation({
    mutationFn: async (payload: {
      indicatorId: string;
      reason: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const { data } = await apiClient.post<{ data: EvaluationIndicator }>(
        `/engine/evaluation/indicators/${payload.indicatorId}/gray`,
        {
          reason: payload.reason,
          ...(payload.publishEvidence ? { publishEvidence: payload.publishEvidence } : {}),
        },
      );
      return data.data;
    },
  });
}

export function useActivateEvaluationIndicator() {
  return useMutation({
    mutationFn: async (payload: {
      indicatorId: string;
      reason: string;
      publishEvidence?: VersionPublishEvidence;
    }) => {
      const { data } = await apiClient.post<{ data: EvaluationIndicator }>(
        `/engine/evaluation/indicators/${payload.indicatorId}/activate`,
        {
          reason: payload.reason,
          ...(payload.publishEvidence ? { publishEvidence: payload.publishEvidence } : {}),
        },
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
        "/engine/evaluation/results",
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
        "/engine/evaluation/issues",
        { params },
      );
      return data.data;
    },
  });
}

export function useInsuranceIssues(params?: InsuranceIssuesQueryParams) {
  return useQuery({
    queryKey: ["quality", "insurance-issues", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<InsuranceIssuePageItem> }>(
        "/engine/quality/insurance-issues",
        { params },
      );
      return data.data;
    },
  });
}

export function useRunQualityCaseReview() {
  return useMutation({
    mutationFn: async (request: QualityCaseReviewRequest) => {
      const { data } = await apiClient.post<{ data: QualityCaseReviewResponse }>(
        "/engine/quality/case-review",
        request,
      );
      return data.data;
    },
  });
}

export function useRunDrgGrouping() {
  return useMutation({
    mutationFn: async (request: DrgGroupingRequest) => {
      const { data } = await apiClient.post<{ data: DrgGroupingResponse }>(
        "/engine/quality/drg-grouping",
        request,
      );
      return data.data;
    },
  });
}

export function useRunInsuranceAudit() {
  return useMutation({
    mutationFn: async (request: InsuranceAuditRequest) => {
      const { data } = await apiClient.post<{ data: InsuranceAuditResponse }>(
        "/engine/quality/insurance-audit",
        request,
      );
      return data.data;
    },
  });
}

export function useQualityDashboard(params?: QualityDashboardQueryParams) {
  return useQuery({
    queryKey: ["quality", "dashboard", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: QualityDashboardResponse }>(
        "/engine/quality/dashboard",
        { params },
      );
      return data.data;
    },
  });
}

export function useQualityDashboardDrilldown(params?: QualityDashboardDrilldownQueryParams) {
  return useQuery({
    queryKey: ["quality", "dashboard", "drilldown", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: QualityDashboardDrilldownResponse }>(
        "/engine/quality/dashboard/drilldown",
        { params },
      );
      return data.data;
    },
  });
}

export function useQualityAlerts(params?: QualityAlertsQueryParams) {
  return useQuery({
    queryKey: ["quality", "alerts", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: QualityDashboardAlertsResponse }>(
        "/engine/quality/alerts",
        { params },
      );
      return data.data;
    },
  });
}

export function useAcknowledgeQualityAlert() {
  return useMutation({
    mutationFn: async (alertId: string) => {
      const { data } = await apiClient.post<{ data: QualityDashboardAlert }>(
        `/engine/quality/alerts/${encodeURIComponent(alertId)}/acknowledge`,
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
        `/engine/evaluation/issues/${findingId}`,
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
        "/engine/evaluation/rectifications",
        payload.request,
        { params: { findingId }, headers },
      );
      return data.data;
    },
  });
}

export function useDispatchRectification() {
  return useMutation({
    mutationFn: async (payload: {
      request: RectificationDispatchRequest;
      idempotencyKey?: string;
    }) => {
      const headers = payload.idempotencyKey
        ? { "Idempotency-Key": payload.idempotencyKey }
        : undefined;
      const { data } = await apiClient.post<{ data: RectificationResponse }>(
        "/engine/rectifications",
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
        `/engine/evaluation/rectifications/${findingId}/review`,
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
        "/engine/evaluation:evaluate",
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
        `/engine/evaluation/runs/${runId}/diagnose`,
      );
      return data.data;
    },
    enabled: !!runId,
  });
}

export type ContextSnapshotStatus = "DRAFT" | "ACTIVE" | "SUPERSEDED" | "REJECTED";

export interface ContextSnapshotSummary {
  snapshotId: string;
  patientId: string;
  encounterId: string;
  status: ContextSnapshotStatus;
  qualityStatus: string;
  createdAt?: string;
}

export interface ContextSnapshotResponse {
  snapshotId: string;
  status: ContextSnapshotStatus;
  resources?: Record<string, unknown> | null;
  packageVersion?: string | null;
  qualityStatus: string;
  missingFields: Array<Record<string, unknown>>;
  mappingStatus: Record<string, string>;
  createdAt?: string;
  traceId?: string;
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

export interface ContextFieldDescriptor {
  category: string;
  group: string;
  resourceType: string;
  fieldPath: string;
  displayName: string;
  dataType: string;
  unit?: string | null;
  codeSystem?: string | null;
  description?: string | null;
  source?: string | null;
  fieldId?: string | null;
  /** 是否为求值期计算的派生字段（如 patient.age 由出生日期算得），而非原始存储字段。 */
  derived?: boolean;
  payloadKey?: string | null;
  propertyName?: string | null;
  jsonSchemaType?: string | null;
  externalWritable?: boolean;
}

/** 上下文字段目录（P2）：供规则条件与路径守卫的字段选择器消费，替代手敲字段路径。 */
export function useContextFieldCatalog(
  params?: { resourceType?: string; keyword?: string; packageVersion?: string },
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ["context", "field-catalog", params ?? {}],
    enabled: options?.enabled ?? true,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ContextFieldDescriptor[] }>(
        "/engine/context/field-catalog",
        { params },
      );
      return data.data;
    },
  });
}

export interface ContextFieldUpsertPayload {
  category: string;
  group: string;
  resourceType: string;
  fieldPath: string;
  displayName: string;
  dataType: string;
  unit?: string;
  codeSystem?: string;
  description?: string;
}

/** 保存租户上下文字段元数据覆盖（P2/P5 前台维护）。 */
export function useCreateContextField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: ContextFieldUpsertPayload) => {
      const { data } = await apiClient.post<{ data: ContextFieldDescriptor }>(
        "/engine/context/field-catalog",
        payload,
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["context", "field-catalog"] });
    },
  });
}

/** 更新租户上下文字段元数据覆盖（P2/P5 前台维护）。 */
export function useUpdateContextField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      fieldId,
      payload,
    }: {
      fieldId: string;
      payload: ContextFieldUpsertPayload;
    }) => {
      const { data } = await apiClient.put<{ data: ContextFieldDescriptor }>(
        `/engine/context/field-catalog/${fieldId}`,
        payload,
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["context", "field-catalog"] });
    },
  });
}

/** 删除租户上下文字段元数据覆盖（P2/P5 前台维护）。 */
export function useDeleteContextField() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (fieldId: string) => {
      await apiClient.delete(`/engine/context/field-catalog/${fieldId}`);
      return fieldId;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["context", "field-catalog"] });
    },
  });
}

export function useContextSnapshotDetail(
  snapshotId: string,
  options?: {
    enabled?: boolean;
  },
) {
  return useQuery({
    queryKey: ["context", "snapshot", snapshotId],
    enabled: (options?.enabled ?? true) && !!snapshotId,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ContextSnapshotResponse }>(
        `/engine/context/snapshots/${snapshotId}`,
      );
      return data.data;
    },
  });
}

// ==================== 智能随访引擎相关的实体及 DTO 契约 ====================

export type FollowupPlanStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";
export type FollowupTaskType = "QUESTIONNAIRE" | "EXAM" | "LAB" | "OUTPATIENT" | "RETURN_VISIT";
export type FollowupTaskStatus = "PENDING" | "COMPLETED" | "OVERDUE" | "CANCELLED";
export type FollowupEventType = "ABNORMAL_RETURN" | "RESULT_INFLOW";

export interface FollowupTaskDetailResponse {
  taskId: string;
  taskType: FollowupTaskType;
  dueDate: string;
  status: FollowupTaskStatus;
  questionnaireTemplateId?: string | null;
}

export interface FollowupPlanDetailResponse {
  planId: string;
  tenantId: string;
  patientId: string;
  encounterId: string;
  diseaseCode: string;
  status: FollowupPlanStatus;
  tasks: FollowupTaskDetailResponse[];
  templateId?: string | null;
  templateVersion?: number | null;
}

export interface FollowupPlanGenerateRequest {
  contextSnapshotId: string;
  riskLevel?: string;
  taskTypes: string[];
  idempotencyKey?: string;
  templateId?: string;
}

export type FollowupTemplateAssetStatus =
  | "DRAFT"
  | "IN_REVIEW"
  | "APPROVED"
  | "PUBLISHED"
  | "DEPRECATED"
  | "RETIRED";

export interface FollowupTemplateTaskInput {
  taskType: FollowupTaskType;
  delayDays: number;
  questionnaireTemplateId?: string;
}

export interface FollowupTemplateResponse {
  templateId: string;
  templateCode: string;
  versionNo: number;
  name: string;
  description?: string | null;
  organizationScope: string;
  applicableScope: string;
  tasks: FollowupTemplateTaskInput[];
  questionnaireDefinition: string;
  abnormalActionDefinition: string;
  sourceRef: string;
  assetVersionId: string;
  assetStatus: FollowupTemplateAssetStatus;
  contentHash: string;
  updatedAt: string;
  traceId: string;
}

export interface FollowupTemplateCreateRequest {
  templateCode: string;
  versionNo: number;
  name: string;
  description?: string;
  organizationScope: string;
  applicableScope: string;
  tasks: FollowupTemplateTaskInput[];
  questionnaireDefinition: string;
  abnormalActionDefinition: string;
  sourceRef: string;
}

export interface FollowupQuestionnaireRequest {
  taskId: string;
  questionnaireTemplateId: string;
  formData: string;
  answerData?: string;
  score?: number;
  idempotencyKey: string;
  executorId?: string;
  executorType?: string;
}

export interface FollowupQuestionnaireResponse {
  questionnaireId: string;
  taskId: string;
  questionnaireTemplateId: string;
  status: string;
  traceId?: string | null;
}

export interface FollowupAbnormalReportRequest {
  planId: string;
  eventType: FollowupEventType;
  payload: string; // JSON string or text description
  triggeredBy?: string;
}

export interface FollowupAbnormalReportResponse {
  eventId: string;
  returnTaskId: string;
  notificationEventId: string;
  traceId: string;
}

export interface FollowupStatsResponse {
  totalPlans: number;
  activePlans: number;
  totalTasks: number;
  completedTasks: number;
  abnormalReturnTasks: number;
  taskCompletionRatePercent: number;
  abnormalReturnRatePercent: number;
  traceId: string;
}

export interface FollowupPlansParams {
  patientId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface FollowupStatsParams {
  patientId?: string;
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

// 2. 获取随访作用域统计
export function useFollowupStats(params?: FollowupStatsParams) {
  return useQuery({
    queryKey: ["followup", "stats", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: FollowupStatsResponse }>(
        "/engine/followup/stats",
        { params },
      );
      return data.data;
    },
  });
}

export function useFollowupTemplates(
  params: {
    page?: number;
    size?: number;
    sort?: string;
    assetStatus?: FollowupTemplateAssetStatus;
    keyword?: string;
  } = {},
) {
  return useQuery({
    queryKey: ["followup", "templates", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<FollowupTemplateResponse> }>(
        "/engine/followup/templates",
        { params },
      );
      return data.data;
    },
  });
}

export function useCreateFollowupTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: FollowupTemplateCreateRequest) => {
      const { data } = await apiClient.post<{ data: FollowupTemplateResponse }>(
        "/engine/followup/templates",
        payload,
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["followup", "templates"] });
      queryClient.invalidateQueries({ queryKey: ["authoring", "assets"] });
    },
  });
}

export function usePublishFollowupTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      templateId: string;
      request: { impactDigest: string; reason: string };
    }) => {
      const { data } = await apiClient.post<{ data: FollowupTemplateResponse }>(
        `/engine/followup/templates/${payload.templateId}/publish`,
        payload.request,
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["followup", "templates"] });
      queryClient.invalidateQueries({ queryKey: ["authoring", "assets"] });
    },
  });
}

// 3. 智能生成随访计划
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

// 4. 获取随访计划详情
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

// 5. 提交问卷并完成任务
export function useSubmitFollowupQuestionnaire() {
  return useMutation({
    mutationFn: async (payload: FollowupQuestionnaireRequest) => {
      const { data } = await apiClient.post<{ data: FollowupQuestionnaireResponse }>(
        "/engine/followup/questionnaires",
        payload,
      );
      return data.data;
    },
  });
}

// 6. 上报随访异常事件
export function useReportFollowupAbnormal() {
  return useMutation({
    mutationFn: async (payload: FollowupAbnormalReportRequest) => {
      const { data } = await apiClient.post<{ data: FollowupAbnormalReportResponse }>(
        "/engine/followup/abnormal-reports",
        payload,
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 临床协同 · 统一待办与通知中心（SVC-CLINICAL-03）
// ──────────────────────────────────────────
export type WorkflowTodoSourceType =
  | "FOLLOWUP_TASK"
  | "SAFETY_REVIEW"
  | "RECOMMENDATION_CARD"
  | "NURSING_TASK"
  | "REPORT_INTERPRETATION"
  | "BEDSIDE_KNOWLEDGE"
  | "PATHWAY_NODE";

export type WorkflowPriority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type WorkflowTodoStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "TRANSFERRED"
  | "CANCELLED";
export type WorkflowNotificationSourceType =
  | "FOLLOWUP_EVENT"
  | "SAFETY_REVIEW"
  | "WORKFLOW_TODO"
  | "SYNC_EVENT";
export type WorkflowNotificationLevel = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO";
export type WorkflowNotificationType = "SAFETY" | "FOLLOWUP" | "WORKFLOW" | "SYNC";
export type WorkflowNotificationSettingsSource = "PERSONAL" | "SYSTEM_DEFAULT";
export type WorkflowNotificationStatus = "UNREAD" | "READ";

export interface WorkflowNotificationDelivery {
  channelCode: string;
  channelName: string;
  status: string;
  compensationRequired: boolean;
  retryCount?: number | null;
  maxRetries?: number | null;
  updatedAt?: string | null;
  errorMessage?: string | null;
}

export interface WorkflowNotificationSettings {
  inAppEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  pushEnabled: boolean;
  webhookEnabled: boolean;
  inHospitalMessageEnabled: boolean;
  quietHoursEnabled: boolean;
  quietStart: string;
  quietEnd: string;
  quietBypassLevels: WorkflowNotificationLevel[];
  subscribedTypes: WorkflowNotificationType[];
  mandatoryTypes: WorkflowNotificationType[];
  source: WorkflowNotificationSettingsSource;
  quietActiveNow: boolean;
  version: number;
  systemVersion: number;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface WorkflowNotificationSettingsPayload {
  inAppEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  pushEnabled: boolean;
  webhookEnabled: boolean;
  inHospitalMessageEnabled: boolean;
  quietHoursEnabled: boolean;
  quietStart: string;
  quietEnd: string;
  quietBypassLevels: WorkflowNotificationLevel[];
  subscribedTypes: WorkflowNotificationType[];
}

export interface WorkflowNotificationSystemSettingsPayload {
  settings: WorkflowNotificationSettingsPayload;
  reason: string;
  expectedVersion: number;
}

export interface WorkflowTodo {
  todoId: string;
  orgUnitId?: string | null;
  sourceType: WorkflowTodoSourceType;
  sourceId: string;
  title: string;
  summary: string;
  priority: WorkflowPriority;
  status: WorkflowTodoStatus;
  assigneeId?: string | null;
  assigneeRole?: string | null;
  patientId?: string | null;
  encounterId?: string | null;
  dueAt?: string | null;
  deepLink?: string | null;
  completionReason?: string | null;
  completedAt?: string | null;
  completedBy?: string | null;
  transferredTo?: string | null;
  transferReason?: string | null;
  traceId?: string | null;
}

export interface WorkflowNotification {
  notificationId: string;
  orgUnitId?: string | null;
  sourceType: WorkflowNotificationSourceType;
  sourceId: string;
  dedupeKey: string;
  title: string;
  message: string;
  level: WorkflowNotificationLevel;
  status: WorkflowNotificationStatus;
  recipientId?: string | null;
  recipientRole?: string | null;
  patientId?: string | null;
  encounterId?: string | null;
  deepLink?: string | null;
  readAt?: string | null;
  readBy?: string | null;
  traceId?: string | null;
  externalDeliveries?: WorkflowNotificationDelivery[] | null;
}

export interface WorkflowTodosParams {
  status?: WorkflowTodoStatus;
  priority?: WorkflowPriority;
  sourceType?: WorkflowTodoSourceType;
  assigneeId?: string;
  orgUnitId?: string;
  page?: number;
  size?: number;
}

export interface WorkflowNotificationsParams {
  status?: WorkflowNotificationStatus;
  level?: WorkflowNotificationLevel;
  recipientId?: string;
  orgUnitId?: string;
  page?: number;
  size?: number;
}

export interface WorkflowTodoCompletePayload {
  todoId: string;
  request: {
    completionReason: string;
  };
}

export interface WorkflowTodoTransferPayload {
  todoId: string;
  request: {
    transferTo: string;
    transferRole?: string | null;
    transferReason: string;
  };
}

export function useWorkflowTodos(params?: WorkflowTodosParams) {
  return useQuery({
    queryKey: ["workflow", "todos", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<WorkflowTodo> }>(
        "/engine/workflow/todos",
        { params },
      );
      return data.data;
    },
  });
}

export function useCompleteWorkflowTodo() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ todoId, request }: WorkflowTodoCompletePayload) => {
      const { data } = await apiClient.post<{ data: WorkflowTodo }>(
        `/engine/workflow/todos/${todoId}/complete`,
        request,
      );
      return data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["workflow", "todos"] });
    },
  });
}

export function useTransferWorkflowTodo() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ todoId, request }: WorkflowTodoTransferPayload) => {
      const { data } = await apiClient.post<{ data: WorkflowTodo }>(
        `/engine/workflow/todos/${todoId}/transfer`,
        request,
      );
      return data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["workflow", "todos"] });
    },
  });
}

export function useWorkflowNotifications(params?: WorkflowNotificationsParams) {
  return useQuery({
    queryKey: ["workflow", "notifications", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<WorkflowNotification> }>(
        "/engine/notifications",
        { params },
      );
      return data.data;
    },
  });
}

export function useReadWorkflowNotification() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (notificationId: string) => {
      const { data } = await apiClient.post<{ data: WorkflowNotification }>(
        `/engine/notifications/${notificationId}/read`,
      );
      return data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["workflow", "notifications"] });
    },
  });
}

export function useWorkflowNotificationSettings() {
  return useQuery({
    queryKey: ["workflow", "notification-settings"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: WorkflowNotificationSettings }>(
        "/engine/notifications/settings",
      );
      return data.data;
    },
  });
}

export function useSaveWorkflowNotificationSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: WorkflowNotificationSettingsPayload) => {
      const { data } = await apiClient.put<{ data: WorkflowNotificationSettings }>(
        "/engine/notifications/settings",
        payload,
      );
      return data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["workflow", "notification-settings"] });
    },
  });
}

export function useWorkflowSystemNotificationSettings(enabled: boolean) {
  return useQuery({
    queryKey: ["workflow", "notification-settings", "system"],
    enabled,
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: WorkflowNotificationSettings }>(
        "/engine/notifications/settings/system",
      );
      return data.data;
    },
  });
}

export function useSaveWorkflowSystemNotificationSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: WorkflowNotificationSystemSettingsPayload) => {
      const { data } = await apiClient.put<{ data: WorkflowNotificationSettings }>(
        "/engine/notifications/settings/system",
        payload,
      );
      return data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["workflow", "notification-settings"],
      });
    },
  });
}

// ──────────────────────────────────────────
// 智能包发布与同步引擎 (GA-ENG-PKG-01)
// ──────────────────────────────────────────

const PACKAGE_API_ROOT = "/engine/pkg/packages";

export type PackageAccessPolicy = "OPEN" | "ENTITLED";

export interface PackageReleaseAdapter {
  adapterId: string;
  adapterName: string;
  protocolType: string;
  status: "ACTIVE" | string;
  healthStatus: "HEALTHY" | "NOT_CONNECTED" | "MISCONFIGURED" | string;
  rttMs: number;
  lastHeartbeatAt: string | null;
  connectorAvailable: boolean;
}

export interface PackageCreateRequest {
  packageCode: string;
  packageVersion: string;
  name: string;
  description: string;
  accessPolicy: PackageAccessPolicy;
}

export interface PackageResponse {
  packageId: string;
  packageCode: string;
  packageVersion: string;
  name: string;
  description: string;
  accessPolicy: PackageAccessPolicy;
  status: "DRAFT" | "PUBLISHED" | "ACTIVE" | "OFFLINE" | string;
}

export interface KnowledgePackage {
  id?: number;
  packageId: string;
  tenantId: string;
  packageCode: string;
  packageVersion: string;
  name: string;
  description: string;
  accessPolicy: PackageAccessPolicy;
  status: "DRAFT" | "PUBLISHED" | "ACTIVE" | "OFFLINE" | string;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  traceId: string;
  assetTypes: EngineAssetType[];
  primaryAssetId?: string | null;
  primaryAssetVersion?: string | null;
  itemCount: number;
  organizationScope?: string | null;
  applicableScope?: string | null;
  sourceRef?: string | null;
}

export interface PackageItem {
  id?: number;
  itemId: string;
  tenantId: string;
  packageId: string;
  assetType: EngineAssetType;
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
  accessPolicy: PackageAccessPolicy;
  status: string;
  items: PackageItem[];
}

export type PackageEntitlementStatus = "ACTIVE" | "EXPIRED" | "REVOKED";

export interface PackageEntitlement {
  entitlementId: string;
  tenantId: string;
  platformPackageId: string;
  packageIdentity: string;
  status: PackageEntitlementStatus;
  grantedAt: string;
  expiresAt: string;
  reason: string;
  updatedAt: string;
  updatedBy: string;
}

export interface PackageEntitlementGrantRequest {
  targetTenantId: string;
  expiresAt: string;
  reason: string;
}

export interface PackageItemRequest {
  packageVersion: string;
  assetType: EngineAssetType;
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
  packageVersion: string;
  reason: string;
  targetOrgUnitId: string;
  strategy: "GRAYSCALE" | "FULL" | string;
  scopeType: ReleaseScopeType;
  scopeValue: string;
  adapterIds: string[];
  publishEvidence?: VersionPublishEvidence;
}

export interface PackageRollbackRequest {
  packageVersion: string;
  targetPackageId: string;
  confirmedCurrentVersion: string;
  confirmedTargetVersion: string;
  reason: string;
  confirmedHighRisk: boolean;
}

export interface PackageValidateIssue {
  field: string;
  severity: "BLOCKING" | "WARNING" | "INFO" | string;
  message: string;
}

export interface PackageValidateResponse {
  packageId: string;
  status: KnowledgePackage["status"];
  itemCount: number;
  contentSha256: string;
  valid: boolean;
  issues: PackageValidateIssue[];
}

export interface SyncLogResponse {
  logId: string;
  planId?: string;
  adapterId: string;
  status: "RUNNING" | "SUCCESS" | "FAILED" | "NOT_SYNCED" | string;
  errorCode: string | null;
  errorMessage: string | null;
  retryCount: number;
  syncEvidence: string | null;
}

export interface PackageSyncLogParams {
  page?: number;
  size?: number;
}

export interface PackageSyncResponse {
  planId: string;
  packageId: string;
  status: "EXECUTING" | "SUCCESS" | "FAILED" | "NOT_SYNCED" | string;
  logs: SyncLogResponse[];
}

export interface PilotPackageTemplateItem {
  assetType: PackageItem["assetType"];
  assetId: string;
  assetVersion: string;
  required: boolean;
  sortOrder: number | null;
  dependencyNote: string | null;
}

export interface PilotPackageTemplate {
  templateId: string;
  templateCode: string;
  tenantId: string;
  name: string;
  description: string | null;
  packageCodePrefix: string;
  defaultPackageVersion: string;
  itemCount: number;
  items: PilotPackageTemplateItem[];
}

export interface PackageAssetReadiness {
  tenantId: string;
  ready: boolean;
  templateCount: number;
  draftPackageCount: number;
  releasedPackageCount: number;
  activePackageCount: number;
  activePackageReferenceCount: number;
  grayscaleReady: boolean;
  readyPackageId: string | null;
  blockers: string[];
  checkedAt: string;
}

export interface PilotPackageInitialOverrideRequest {
  asset_type: PackageItem["assetType"];
  asset_identity: string;
  inherited_version_id?: string;
  override_version_id?: string;
  target_org_unit_id: string;
  applicable_scope?: string;
  override_mode: "REPLACE" | "ADD" | "DISABLE" | string;
  propagation: "INHERITABLE" | "EXCLUSIVE" | string;
  diff_summary?: string;
  override_reason?: string;
  impact_scope?: string;
}

export interface PilotPackageTemplateApplyRequest {
  target_org_unit_id: string;
  initial_overrides?: PilotPackageInitialOverrideRequest[];
}

export interface TenantPackageReference {
  referenceId: string;
  tenantId: string;
  platformTenantId: string;
  platformPackageId: string;
  packageCode: string;
  packageVersion: string;
  targetOrgUnitId: string;
  sourceTemplateCode: string;
  status: "ACTIVE" | "INACTIVE" | string;
}

export interface PilotPackageTemplateApplyResult {
  templateCode: string;
  references: TenantPackageReference[];
  initialOverrides: Array<Record<string, unknown>>;
}

export type PackageInheritanceImpactType =
  | "AUTO_INHERITS_UPSTREAM"
  | "REBASE_RECOMMENDED"
  | "DISABLE_REVIEW_RECOMMENDED"
  | "UNAFFECTED"
  | string;

export interface PackageInheritanceImpactTarget {
  orgUnitId: string;
  orgPath: string;
  impactType: PackageInheritanceImpactType;
  effectiveVersionId: string | null;
  effectiveVersionNo: string | null;
  sourceTier: string | null;
  diffSummary: string | null;
  rebasePrompt: string | null;
}

export interface PackageInheritanceImpactResponse {
  tenantId: string;
  assetType: PackageItem["assetType"];
  assetIdentity: string;
  applicableScope: string;
  upstreamBaseVersion: string;
  upstreamTargetVersion: string;
  autoInheritedCount: number;
  rebaseRequiredCount: number;
  upstreamDiff: PackageDiffResponse;
  targets: PackageInheritanceImpactTarget[];
}

export interface PackageInheritanceImpactQuery {
  assetType?: PackageItem["assetType"] | string;
  assetIdentity?: string;
  applicableScope?: string;
  upstreamVersionId?: string;
}

export interface PackageReleaseAdaptersParams {
  page?: number;
  size?: number;
}

// 1. 获取配置包发布可用的统一集成适配器
export function usePackageReleaseAdapters(
  params: PackageReleaseAdaptersParams = {},
  enabled = true,
) {
  return useQuery({
    queryKey: ["packages", "release-adapters", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<PackageReleaseAdapter> }>(
        `${PACKAGE_API_ROOT}/release-adapters`,
        { params },
      );
      return data.data ?? emptyPage<PackageReleaseAdapter>();
    },
    enabled,
  });
}

// 2. 创建知识包草稿
export function useCreatePackage() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: PackageCreateRequest) => {
      const { data } = await apiClient.post<{ data: PackageResponse }>(
        PACKAGE_API_ROOT,
        withStandardApiContext(payload, security.data, payload.packageVersion),
      );
      return data.data;
    },
  });
}

export interface PathwayPackageBuildRequest {
  packageCode: string;
  diseaseCode: string;
  name: string;
  packageVersion: string;
  sourceRef: string;
  description: string;
  profiles?: Array<{
    profileCode: string;
    name: string;
    stratification?: Record<string, unknown>;
    entryCriteria?: Record<string, unknown>;
    exitCriteria?: Record<string, unknown>;
    followupPlan?: Record<string, unknown>;
  }>;
}

export function useBuildPathwayKnowledgePackage() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: PathwayPackageBuildRequest) => {
      const { data } = await apiClient.post<{ data: PackageResponse }>(
        `${PACKAGE_API_ROOT}/pathway`,
        withStandardApiContext(payload, security.data, payload.packageVersion),
      );
      return data.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["packages", "list"] });
    },
  });
}

// 3. 分页查询知识包列表
export interface PackageListParams {
  page?: number;
  size?: number;
  keyword?: string;
  status?: string;
  assetType?: EngineAssetType;
}

export function usePackages(params: PackageListParams = {}) {
  const requestParams = {
    page: Math.max(params.page ?? 1, 1),
    size: Math.max(params.size ?? 10, 1),
    ...(params.keyword ? { keyword: params.keyword } : {}),
    ...(params.status ? { status: params.status } : {}),
    ...(params.assetType ? { assetType: params.assetType } : {}),
  };
  return useQuery({
    queryKey: ["packages", "list", requestParams],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<KnowledgePackage> }>(
        PACKAGE_API_ROOT,
        { params: requestParams },
      );
      return (
        data.data ?? {
          items: [],
          page: requestParams.page + 1,
          size: requestParams.size,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        }
      );
    },
  });
}

export function usePackageEntitlements(packageId: string, enabled = true, page = 1, size = 20) {
  return useQuery({
    queryKey: ["packages", packageId, "entitlements", page, size],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<PackageEntitlement> }>(
        `${PACKAGE_API_ROOT}/${packageId}/entitlements`,
        { params: { page, size } },
      );
      return data.data;
    },
    enabled: enabled && Boolean(packageId),
  });
}

export function useGrantPackageEntitlement() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      packageId: string;
      packageVersion: string;
      request: PackageEntitlementGrantRequest;
    }) => {
      const { data } = await apiClient.post<{ data: PackageEntitlement }>(
        `${PACKAGE_API_ROOT}/${payload.packageId}/entitlements`,
        withStandardApiContext(payload.request, security.data, payload.packageVersion),
      );
      return data.data;
    },
    onSuccess: async (_result, payload) => {
      await queryClient.invalidateQueries({
        queryKey: ["packages", payload.packageId, "entitlements"],
      });
    },
  });
}

export function useRevokePackageEntitlement() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      packageId: string;
      packageVersion: string;
      tenantId: string;
      reason: string;
    }) => {
      const { data } = await apiClient.post<{ data: PackageEntitlement }>(
        `${PACKAGE_API_ROOT}/${payload.packageId}/entitlements/${encodeURIComponent(
          payload.tenantId,
        )}:revoke`,
        withStandardApiContext({ reason: payload.reason }, security.data, payload.packageVersion),
      );
      return data.data;
    },
    onSuccess: async (_result, payload) => {
      await queryClient.invalidateQueries({
        queryKey: ["packages", payload.packageId, "entitlements"],
      });
    },
  });
}

export function usePilotPackageTemplates() {
  return useQuery({
    queryKey: ["packages", "pilot-templates"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PilotPackageTemplate[] }>(
        `${PACKAGE_API_ROOT}/pilot-templates`,
      );
      return data.data ?? [];
    },
  });
}

export function usePackageAssetReadiness() {
  return useQuery({
    queryKey: ["packages", "asset-readiness"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PackageAssetReadiness }>(
        `${PACKAGE_API_ROOT}/asset-readiness`,
      );
      return data.data;
    },
  });
}

export function usePackageInheritanceImpact(
  query: PackageInheritanceImpactQuery,
  options?: { enabled?: boolean },
) {
  const params = {
    assetType: query.assetType?.trim(),
    assetIdentity: query.assetIdentity?.trim(),
    applicableScope: query.applicableScope?.trim(),
    upstreamVersionId: query.upstreamVersionId?.trim(),
  };
  const enabled =
    (options?.enabled ?? true) &&
    Boolean(params.assetType) &&
    Boolean(params.assetIdentity) &&
    Boolean(params.applicableScope) &&
    Boolean(params.upstreamVersionId);
  return useQuery({
    queryKey: ["packages", "inheritance-impact", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PackageInheritanceImpactResponse }>(
        `${PACKAGE_API_ROOT}/inheritance-impact`,
        { params },
      );
      return data.data;
    },
    enabled,
  });
}

export function useApplyPilotTemplateReferences() {
  const security = useSecurityProfile();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      templateCode: string;
      packageVersion: string;
      request: PilotPackageTemplateApplyRequest;
    }) => {
      const requestPayload = {
        ...payload.request,
        initial_overrides: payload.request.initial_overrides ?? [],
      };
      const { data } = await apiClient.post<{ data: PilotPackageTemplateApplyResult }>(
        `${PACKAGE_API_ROOT}/pilot-templates/${encodeURIComponent(payload.templateCode)}/references`,
        withStandardApiContext(requestPayload, security.data, payload.packageVersion),
      );
      return data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["packages", "asset-readiness"] });
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
        `${PACKAGE_API_ROOT}/${packageId}`,
      );
      return data.data;
    },
    enabled: !!packageId,
  });
}

// 5. 添加资产条目到草稿
export function useAddPackageItem() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: { packageId: string; request: PackageItemRequest }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: PackageItemResponse }>(
        `${PACKAGE_API_ROOT}/${payload.packageId}/items`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
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
        `${PACKAGE_API_ROOT}/${packageId}/diff`,
        { params: { basePackageId } },
      );
      return data.data;
    },
    enabled: !!packageId,
  });
}

export async function downloadPackageDiffExport(packageId: string, basePackageId?: string) {
  const { data } = await apiClient.get<Blob>(`${PACKAGE_API_ROOT}/${packageId}/diff/export`, {
    params: { basePackageId },
    responseType: "blob",
  });
  return data;
}

export async function downloadPackageOfflineExport(packageId: string, targetOrgUnitId: string) {
  const { data } = await apiClient.get<Blob>(`${PACKAGE_API_ROOT}/${packageId}/offline/export`, {
    params: { targetOrgUnitId },
    responseType: "blob",
  });
  return data;
}

export async function downloadPackageSyncEvidenceExport(packageId: string) {
  const { data } = await apiClient.get<Blob>(`${PACKAGE_API_ROOT}/${packageId}/sync-logs/export`, {
    responseType: "blob",
  });
  return data;
}

export async function importPackageOfflinePackage(offlinePackageJson: string) {
  const { data } = await apiClient.post<{ data: PackageOfflineImportResponse }>(
    `${PACKAGE_API_ROOT}/offline/import`,
    { offlinePackageJson },
  );
  return data.data;
}

export function useImportOfflinePackage() {
  return useMutation({
    mutationFn: importPackageOfflinePackage,
  });
}

// 7. 触发多通道真实同步发布
export function useSyncPackage() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: { packageId: string; request: PackageSyncRequest }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: PackageSyncResponse }>(
        `${PACKAGE_API_ROOT}/${payload.packageId}/sync`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
      );
      return data.data;
    },
  });
}

export function useReleasePackage() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: { packageId: string; request: PackageSyncRequest }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: PackageSyncResponse }>(
        `${PACKAGE_API_ROOT}/${payload.packageId}/release`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 统一发布治理 · 影响模拟 / 灰度 / 覆盖复用
// ──────────────────────────────────────────
const RELEASE_GOVERNANCE_API_ROOT = "/engine/versioning/releases";

export type RolloutStrategy = "ALL" | "ORG_SUBTREE" | "ORG_LIST" | "CANARY_BED_PERCENT" | "STAGED";

export interface RolloutThresholds {
  maxHitRate?: number;
  maxBlockRate?: number;
  maxManualRejectionRate?: number;
  maxAnomalyRate?: number;
}

export interface RolloutPolicy {
  strategy: RolloutStrategy;
  orgUnitIds: string[];
  bedPercent?: number;
  stages: number[];
  observationMinutes?: number;
  thresholds?: RolloutThresholds;
}

export interface ReleaseSimulationRequest {
  candidateTenantId?: string;
  assetType: EngineAssetType;
  assetIdentity: string;
  candidateVersionId: string;
  targetOrgUnitIds: string[];
  targetOrgPath: string;
  applicableScope: string;
  rolloutPolicy: RolloutPolicy;
  replayDays: number;
  replayLimit: number;
}

export interface ReleaseSimulationResult {
  simulationDigest: string;
  generatedAt: string;
  candidateVersionId: string;
  currentVersionId?: string | null;
  affectedOrganizations: Array<{ orgUnitId: string; orgPath: string; orgName: string }>;
  applicableDimensions: string[];
  diff: {
    changeType: "ADDED" | "UNCHANGED" | "MODIFIED" | string;
    currentVersionNo?: string | null;
    candidateVersionNo: string;
    currentContentHash?: string | null;
    candidateContentHash: string;
  };
  replay: {
    status: "SUPPORTED" | "NO_DATA" | "UNSUPPORTED" | string;
    sampledCases: number;
    changedCases: number;
    triggerIncreases: number;
    triggerDecreases: number;
    severityIncreases: number;
    severityDecreases: number;
    highRiskSnapshotIds: string[];
    reason?: string | null;
  };
  safety: { passed: boolean; issues: string[] };
  dependencies: { passed: boolean; issues: string[] };
  conflicts: Array<{
    overrideId: string;
    orgPath: string;
    overrideMode: string;
    resultingSource: string;
  }>;
  releasable: boolean;
}

export interface VersionReleasePlan {
  planId: string;
  assetType: EngineAssetType;
  assetIdentity: string;
  versionId: string;
  fromVersionId?: string | null;
  rolloutStrategy: RolloutStrategy;
  rolloutStageIndex: number;
  rolloutPausedReason?: string | null;
  status: "GRAY" | "PAUSED" | "PUBLISHED" | "ROLLED_BACK" | string;
  impactDigest: string;
}

export interface VersionRolloutObservationResult {
  plan: VersionReleasePlan;
  paused: boolean;
  readyForFullRelease: boolean;
  currentStagePercent: number;
}

export interface OverrideTemplateItemInput {
  assetType: EngineAssetType;
  assetIdentity: string;
  inheritedVersionId?: string;
  sourceOverrideVersionId?: string;
  overrideMode: "REPLACE" | "DISABLE" | "ADD";
  propagation: "INHERITABLE" | "EXCLUSIVE";
  applicableScope: string;
  diffSummary: string;
  overrideReason: string;
}

export interface OverrideTemplate {
  templateId: string;
  templateName: string;
  description?: string | null;
  applicableScope: string;
  status: "ACTIVE" | "RETIRED" | string;
  updatedAt: string;
}

export interface OverrideTemplatesParams {
  page?: number;
  size?: number;
}

export interface OverrideBatchPreviewRequest {
  templateId?: string;
  sourceOrgUnitId?: string;
  targetOrgUnitIds: string[];
  targetVersionIds: Record<string, string>;
}

export interface OverrideBatchPreviewResult {
  previewDigest: string;
  operationType: "TEMPLATE_APPLY" | "CLONE" | string;
  rows: Array<{
    targetOrgUnitId: string;
    assetType: EngineAssetType;
    assetIdentity: string;
    overrideMode: string;
    propagation: string;
    targetVersionId?: string | null;
    status: string;
    issue?: string | null;
  }>;
  releasable: boolean;
}

export interface OverrideBatchOperationResult {
  operationId: string;
  status: "APPLIED" | "REVOKED" | "FAILED" | string;
  overrideIds: string[];
  previewDigest: string;
}

export function useReleaseSimulation() {
  return useMutation({
    mutationFn: async (request: ReleaseSimulationRequest) => {
      const { data } = await apiClient.post<{ data: ReleaseSimulationResult }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/simulations`,
        request,
      );
      return data.data;
    },
  });
}

export function useStartReleaseRollout() {
  return useMutation({
    mutationFn: async (request: {
      simulation: ReleaseSimulationRequest;
      confirmedSimulationDigest: string;
      reviewConclusion: string;
      electronicSignature?: VersionElectronicSignature;
      qualityGate?: VersionPublishQualityGate;
    }) => {
      const { data } = await apiClient.post<{ data: VersionReleasePlan }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/rollouts`,
        request,
      );
      return data.data;
    },
  });
}

export function useObserveReleaseRollout() {
  return useMutation({
    mutationFn: async (payload: {
      planId: string;
      request: {
        stageIndex: number;
        sampleCount: number;
        hitCount: number;
        blockCount: number;
        manualRejectionCount: number;
        anomalyCount: number;
        observedAt: string;
      };
    }) => {
      const { data } = await apiClient.post<{ data: VersionRolloutObservationResult }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/rollouts/${payload.planId}/observations`,
        payload.request,
      );
      return data.data;
    },
  });
}

export function useRollbackRollout() {
  return useMutation({
    mutationFn: async (request: { planId: string; reason: string; confirmedHighRisk: boolean }) => {
      const { planId, ...body } = request;
      const { data } = await apiClient.post<{ data: VersionReleasePlan }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/rollouts/${planId}:rollback`,
        body,
      );
      return data.data;
    },
  });
}

export function useOverrideTemplates(params: OverrideTemplatesParams = {}) {
  return useQuery({
    queryKey: ["release-governance", "override-templates", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<OverrideTemplate> }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/override-templates`,
        { params },
      );
      return (
        data.data ?? {
          items: [],
          page: params.page ?? 1,
          size: params.size ?? 20,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        }
      );
    },
  });
}

export function useCreateOverrideTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: {
      templateName: string;
      description?: string;
      applicableScope: string;
      items: OverrideTemplateItemInput[];
    }) => {
      const { data } = await apiClient.post<{ data: unknown }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/override-templates`,
        request,
      );
      return data.data;
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["release-governance", "override-templates"] }),
  });
}

export function usePreviewOverrideBatch() {
  return useMutation({
    mutationFn: async (request: OverrideBatchPreviewRequest) => {
      const { data } = await apiClient.post<{ data: OverrideBatchPreviewResult }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/override-batches:preview`,
        request,
      );
      return data.data;
    },
  });
}

export function useApplyOverrideBatch() {
  return useMutation({
    mutationFn: async (request: {
      preview: OverrideBatchPreviewRequest;
      confirmedPreviewDigest: string;
    }) => {
      const { data } = await apiClient.post<{ data: OverrideBatchOperationResult }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/override-batches:apply`,
        request,
      );
      return data.data;
    },
  });
}

export function useRevokeOverrideBatch() {
  return useMutation({
    mutationFn: async (operationId: string) => {
      const { data } = await apiClient.post<{ data: OverrideBatchOperationResult }>(
        `${RELEASE_GOVERNANCE_API_ROOT}/override-batches/${operationId}:revoke`,
      );
      return data.data;
    },
  });
}

export function useValidatePackage() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: { packageId: string; packageVersion: string }) => {
      const { data } = await apiClient.post<{ data: PackageValidateResponse }>(
        `${PACKAGE_API_ROOT}/${payload.packageId}/validate`,
        withStandardApiContext({}, security.data, payload.packageVersion),
      );
      return data.data;
    },
  });
}

export function usePackageSyncLogs(packageId: string, params: PackageSyncLogParams = {}) {
  return useQuery({
    queryKey: ["packages", "sync-logs", packageId, params],
    queryFn: async () => {
      if (!packageId) return emptyPage<SyncLogResponse>();
      const { data } = await apiClient.get<{ data: PageResponse<SyncLogResponse> }>(
        `${PACKAGE_API_ROOT}/${packageId}/sync-logs`,
        { params },
      );
      return data.data ?? emptyPage<SyncLogResponse>();
    },
    enabled: !!packageId,
  });
}

// 8. 一键快速回滚在用包版本至历史点
export function useRollbackPackage() {
  const security = useSecurityProfile();
  return useMutation({
    mutationFn: async (payload: { packageId: string; request: PackageRollbackRequest }) => {
      const { packageVersion, ...requestPayload } = payload.request;
      const { data } = await apiClient.post<{ data: PackageResponse }>(
        `${PACKAGE_API_ROOT}/${payload.packageId}/rollback`,
        withStandardApiContext(requestPayload, security.data, packageVersion),
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 页面嵌入与安全白名单引擎 (GA-ENG-EMBED-01)
// ──────────────────────────────────────────

export interface EmbedLaunchTokenRequest {
  roleCode: string;
  patientId: string;
  encounterId: string;
  triggerPoint: string;
  expireSeconds?: number;
  integrationMode?: "IFRAME" | "SDK" | "API";
  hook?: string;
  hookInstance?: string;
  parentOrigin?: string;
}

export interface EmbedLaunchTokenResponse {
  token: string;
  expiredAt: string;
  embedUrl: string;
  integrationMode: "IFRAME" | "SDK" | "API";
  launchEndpoint: string;
  hook?: string;
  hookInstance?: string;
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
  integrationMode: "IFRAME" | "SDK" | "API";
  hook?: string;
  hookInstance?: string;
  modelStatus: "MODEL_DISABLED";
  connectionStatus: "CONNECTED" | "NOT_CONNECTED";
  cdsHookVersion: string;
  parentOrigin: string;
}

export type EmbedFeedbackAction = "ADOPT" | "REJECT" | "LATER" | "IGNORE" | "CLOSE";

export interface EmbedRecommendationCardResponse {
  cardId: string;
  title: string;
  summary: string;
  suggestedAction: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  interruptLevel: "SILENT" | "SOFT" | "HARD_STOP";
  status: "PENDING" | "VIEWED" | "DEFERRED";
  requiresPhysicianConfirmation: boolean;
  aiGenerated: boolean;
  sourceSummary: string;
  traceId: string;
}

export interface EmbedRecommendationCardsResponse {
  items: EmbedRecommendationCardResponse[];
  traceId: string;
}

export interface EmbedFeedbackRequest {
  token: string;
  cardId: string;
  actionType: EmbedFeedbackAction;
  reason?: string;
}

export interface EmbedFeedbackResponse {
  token: string;
  cardId: string;
  actionType: EmbedFeedbackAction;
  recommendationStatus: string;
  callbackStatus: "CONNECTED" | "NOT_CONNECTED";
  callbackDelivered: boolean;
  degradationReason: string | null;
  traceId: string;
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
      const { data } = await apiClient.post<{ data: EmbedLaunchContextResponse }>(
        "/engine/embed/launch",
        { token, integrationMode: "IFRAME" },
      );
      return data.data;
    },
    enabled: !!token,
    retry: false,
  });
}

// 3. 使用已兑换令牌读取当前就诊范围内的可处置建议
export function useEmbedRecommendationCards(token: string, enabled: boolean) {
  return useQuery({
    queryKey: ["embed", "recommendations", token],
    queryFn: async () => {
      const { data } = await apiClient.post<{ data: EmbedRecommendationCardsResponse }>(
        "/engine/embed/recommendations",
        { token },
      );
      return data.data;
    },
    enabled: Boolean(token) && enabled,
    retry: false,
  });
}

// 4. 回传记录医师的交互反馈审计
export function useSubmitEmbedFeedback() {
  return useMutation({
    mutationFn: async (payload: EmbedFeedbackRequest) => {
      const { data } = await apiClient.post<{ data: EmbedFeedbackResponse }>(
        "/engine/embed/feedback",
        payload,
      );
      return data.data;
    },
  });
}

// 5. 获取当前租户的安全 Origin 域名白名单列表
export function useEmbedOrigins() {
  return useQuery({
    queryKey: ["embed", "origins"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: string[] }>("/engine/embed/origins");
      return data.data ?? [];
    },
  });
}

// 6. 添加跨域嵌入 Origin 安全白名单
export function useAddEmbedOrigin() {
  return useMutation({
    mutationFn: async (payload: EmbedOriginRequest) => {
      await apiClient.post<void>("/engine/embed/origins", payload);
    },
  });
}

// ──────────────────────────────────────────
// 全真体验沙盘
// ──────────────────────────────────────────

export interface SandboxStepTrace {
  stage: "CONTEXT" | "RECOMMENDATION" | "TOKEN" | string;
  endpoint: string;
  request: unknown;
  response: unknown;
  serverFacts: Record<string, unknown>;
  status: "OK" | "FAIL";
  error?: string | null;
}

export interface SandboxRunRequest {
  entryMode?: "SNAPSHOT";
  contextOverride?: unknown;
  occurredAt?: string;
  parentOrigin?: string;
  integrationMode?: "IFRAME" | "SDK" | "API";
}

export interface SandboxRunResponse {
  scenarioId: string;
  traceId: string;
  steps: SandboxStepTrace[];
  snapshotId?: string | null;
  triggerId?: string | null;
  cardCount: number;
  embedToken?: string | null;
  embedUrl?: string | null;
  hookInstance?: string | null;
  patientPathwayId?: string | null;
  followupPlanId?: string | null;
  evaluationRunId?: string | null;
  embedModes: Array<"IFRAME" | "SDK" | "API">;
  result: "PASS" | "FAIL";
}

export interface SandboxScenarioCatalogInput {
  kind: "numeric" | "orchestration" | "unavailable" | string;
  code?: string | null;
  label?: string | null;
  defaultValue?: number | null;
  minValue?: number | null;
  maxValue?: number | null;
  step?: number | null;
  unit?: string | null;
  referenceRange?: string | null;
  upperReferenceValue?: number | null;
  encounterType?: string | null;
}

export interface SandboxScenarioCatalogItem {
  id: string;
  servicePackage: string;
  engine: string;
  playbook: string;
  triggerPoint: string;
  title: string;
  narrative: string;
  hostSummary: string;
  patientId: string;
  encounterId: string;
  expectedRuleCode: string | null;
  expectedAction: string;
  expectedSeverity: string;
  expectedAssetCode?: string | null;
  status: "ready" | "clinical-review-required" | string;
  statusReason: string;
  input: SandboxScenarioCatalogInput;
}

export function useSandboxScenarios() {
  return useQuery({
    queryKey: ["sandbox", "scenarios"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SandboxScenarioCatalogItem[] }>(
        "/engine/sandbox/scenarios",
      );
      return data.data ?? [];
    },
  });
}

export function useRunSandboxScenario() {
  return useMutation({
    mutationFn: async (variables: { scenarioId: string; body?: SandboxRunRequest }) => {
      const { data } = await apiClient.post<{ data: SandboxRunResponse }>(
        `/engine/sandbox/scenarios/${variables.scenarioId}/run`,
        variables.body ?? {},
      );
      return data.data;
    },
  });
}

// ─── 医学回归独立专家复核（LLM-07 / V151）───
export type ModelEvaluationStatus = "PENDING_REVIEW" | "PASSED" | "FAILED";

export interface ModelEvaluationRunSummary {
  runId: number;
  providerCode: string;
  modelVersion: string;
  capabilityCode: string;
  promptVersion: string;
  toolVersion: string;
  totalCases: number;
  passedCases: number;
  failedCases: number;
  fakeCitationDetected: boolean;
  redLineBreach: boolean;
  hallucinationDetected: boolean;
  status: ModelEvaluationStatus;
  reviewer?: string | null;
  signedAt?: string | null;
  reviewComment?: string | null;
  createdAt: string;
  createdBy: string;
}

export interface ModelEvaluationCaseEvidence {
  evidenceId: number;
  regressionCaseId: number;
  caseVersion: string;
  caseInput: string;
  expectedPhrase: string;
  redLineType?: string | null;
  sourceReference: string;
  outputContent: string;
  sourceCitations?: string | null;
  expectedPhraseHit: boolean;
  citationRequired: boolean;
  citationVerified: boolean;
  redLineCase: boolean;
  redLineBreach: boolean;
  passed: boolean;
  failureReasons: string[];
}

export interface ModelEvaluationRunDetail {
  run: ModelEvaluationRunSummary;
  cases: ModelEvaluationCaseEvidence[];
  evidenceComplete: boolean;
  baselineCurrent: boolean;
  reviewable: boolean;
  reviewBlockReason?: string | null;
}

export interface ModelEvaluationRunsParams {
  status: ModelEvaluationStatus;
  page: number;
  size: number;
}

export function useModelEvaluationRuns(params: ModelEvaluationRunsParams, enabled = true) {
  return useQuery({
    queryKey: ["model-evaluations", "runs", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<ModelEvaluationRunSummary> }>(
        "/model-evaluations/runs",
        { params },
      );
      return data.data;
    },
    enabled,
  });
}

export function useModelEvaluationRunDetail(runId?: number | null, enabled = true) {
  return useQuery({
    queryKey: ["model-evaluations", "runs", runId],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ModelEvaluationRunDetail }>(
        `/model-evaluations/runs/${runId}`,
      );
      return data.data;
    },
    enabled: enabled && typeof runId === "number",
  });
}

export function useSignOffModelEvaluation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      runId,
      evidenceAcknowledged,
      reviewComment,
    }: {
      runId: number;
      evidenceAcknowledged: boolean;
      reviewComment: string;
    }) => {
      const { data } = await apiClient.post<{ data: ModelEvaluationRunSummary }>(
        `/model-evaluations/${runId}/sign-off`,
        { evidenceAcknowledged, reviewComment },
      );
      return data.data;
    },
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["model-evaluations", "runs"] }),
        queryClient.invalidateQueries({
          queryKey: ["model-evaluations", "runs", variables.runId],
        }),
      ]);
    },
  });
}

// ─── 大模型能力网关相关的接口定义 (GA-ENG-API-12) ───
export interface ModelCapabilityStatusResponse {
  capabilityCode: string;
  displayName: string;
  description: string;
  category: string;
  routeStrategy: "DISABLED" | "BASELINE" | "LOCAL_MODEL" | "EXTERNAL_MODEL" | string;
  desensitizeStrategy: "DEFAULT" | "MASK_ALL" | "NONE" | string;
  expectedSchema: string | null;
  fallbackOrder: string[];
  timeoutMs: number;
  rateLimitPerMinute: number | null;
  policyScopeType: string;
  policyScopeRef: string;
  inherited: boolean;
  configured: boolean;
  fallbackAvailable: boolean;
  fallbackReason: string;
}

export interface ModelTaskRequest {
  capabilityCode: string;
  inputData: string;
  timeoutSeconds?: number;
  requiredRouteStrategy?: "BASELINE" | "LOCAL_MODEL" | "EXTERNAL_MODEL" | string;
  providerCode?: string | null;
}

export interface ModelTaskResponse {
  taskId: string;
  status: "SUCCESS" | "FAILED" | "DEGRADED" | string;
  outputContent: string;
  modelMode: string;
  modelVersion: string;
  promptVersion: string;
  toolVersion: string;
  sourceCitations: string;
  confidence: number | null;
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
  fallbackOrder?: string[];
  timeoutMs?: number;
  rateLimitPerMinute?: number | null;
}

export interface ModelPolicyValidateResponse {
  valid: boolean;
  message: string;
  fallbackAvailable: boolean;
}

export interface ModelPolicyUpsertRequest {
  routeStrategy: string;
  desensitizeStrategy: string;
  expectedSchema?: string;
  fallbackOrder?: string[];
  timeoutMs?: number;
  rateLimitPerMinute?: number | null;
}

export interface ModelCapabilityDefinition {
  capabilityCode: string;
  displayName: string;
  description: string;
  category: string;
  enabled: boolean;
  sortOrder: number;
}

export interface ModelCapabilityDefinitionUpsertRequest {
  displayName: string;
  description: string;
  category: string;
  enabled: boolean;
  sortOrder: number;
}

// 6. 扫描获取当前租户全部可用模型能力状态与降级指标
export function useModelCapabilitiesStatus(enabled = true) {
  return useQuery({
    queryKey: ["model", "capabilities-status"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ModelCapabilityStatusResponse[] }>(
        "/model-capabilities/status",
      );
      return data.data ?? [];
    },
    enabled,
  });
}

// 7. 读取平台模型能力目录，包括停用项
export function useModelCapabilityCatalog(enabled = true) {
  return useQuery({
    queryKey: ["model", "capability-catalog"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ModelCapabilityDefinition[] }>(
        "/model-capabilities/catalog",
      );
      return data.data ?? [];
    },
    enabled,
  });
}

// 8. 新增或更新平台模型能力目录
export function useSaveModelCapabilityDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      capabilityCode,
      definition,
    }: {
      capabilityCode: string;
      definition: ModelCapabilityDefinitionUpsertRequest;
    }) => {
      const { data } = await apiClient.put<{ data: ModelCapabilityDefinition }>(
        `/model-capabilities/catalog/${encodeURIComponent(capabilityCode)}`,
        definition,
      );
      return data.data;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["model", "capability-catalog"] }),
        queryClient.invalidateQueries({ queryKey: ["model", "capabilities-status"] }),
      ]);
    },
  });
}

// 9. 提交推理或抽取任务，由网关执行路由、数据脱敏与Schema检验
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

// 10. 根据任务ID追溯大模型推理或降级回退任务的详情与审计凭证
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

// 11. 重试失败的任务或改为 B0 基线回退
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

// 12. 按 task_id 重放 B0 确定性任务，供审计复现版本三元组
export function useReplayModelTask() {
  return useMutation({
    mutationFn: async (taskId: string) => {
      const { data } = await apiClient.post<{ data: ModelTaskResponse }>(
        `/model-capabilities/tasks/${taskId}/replay`,
      );
      return data.data;
    },
  });
}

// 13. 发布前校验策略的合法性与可用降级判定
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

// 14. 保存当前租户指定能力的真实路由、脱敏与结构化输出策略
export function useSaveModelPolicy() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      capabilityCode,
      policy,
    }: {
      capabilityCode: string;
      policy: ModelPolicyUpsertRequest;
    }) => {
      const { data } = await apiClient.put<{ data: ModelCapabilityStatusResponse }>(
        `/model-capabilities/policies/${encodeURIComponent(capabilityCode)}`,
        policy,
      );
      return data.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["model", "capabilities-status"] });
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
  healthStatus: "HEALTHY" | "NOT_CONNECTED" | "MISCONFIGURED" | string;
  rttMs: number;
  lastHeartbeatAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdapterHealthItem {
  adapterId: string;
  name: string;
  protocolType: string;
  status: string;
  healthStatus: IntegrationAdapter["healthStatus"];
  rttMs: number;
  lastHeartbeatAt: string | null;
  message: string;
}

export interface AdapterHealthSummary {
  total: number;
  active: number;
  suspended: number;
  healthy: number;
  notConnected: number;
  misconfigured: number;
  checkedAt: string;
  adapters: AdapterHealthItem[];
}

export interface AdapterHubSourceStatus {
  adapterId: string;
  name: string;
  protocolType: string;
  status: string;
  healthStatus: IntegrationAdapter["healthStatus"];
  mappedFieldCount: number;
  lastHeartbeatAt: string | null;
  gaps: string[];
}

export interface AdapterHubRequiredSourceStatus {
  sourceSystem: "HIS" | "EMR" | "LIS" | string;
  label: string;
  adapterId: string | null;
  adapterName: string | null;
  protocolType: string | null;
  status: "MISSING" | "BOUND" | "READY" | string;
  healthStatus: IntegrationAdapter["healthStatus"];
  mappedFieldCount: number;
  lastHeartbeatAt: string | null;
  ready: boolean;
  gaps: string[];
}

export interface AdapterHubStatus {
  totalAdapters: number;
  activeAdapters: number;
  suspendedAdapters: number;
  healthyAdapters: number;
  notConnectedAdapters: number;
  misconfiguredAdapters: number;
  mappedAdapters: number;
  generatedAt: string;
  sources: AdapterHubSourceStatus[];
  requiredSources: AdapterHubRequiredSourceStatus[];
}

export interface MasterDataReconciliation {
  sourceSystem: string;
  lastSuccessfulBatchId: string | null;
  cursor: string | null;
  lastSyncedAt: string | null;
  resources: Array<{
    resourceType: "ORG_UNIT" | "PERSON" | "LOCAL_TERM";
    activeCount: number;
    disabledCount: number;
  }>;
}

export interface IntegrationDataContractFieldSchema {
  type: string;
  description?: string | null;
  unit?: string | null;
  codeSystem?: string | null;
  required?: boolean;
  derived?: boolean;
  externalWritable?: boolean;
}

export interface IntegrationDataContractJsonSchema {
  type: string;
  required: string[];
  properties: Record<string, IntegrationDataContractFieldSchema>;
  additionalProperties?: boolean;
}

export interface IntegrationDataContractResource {
  resourceType: string;
  payloadKey: string;
  array: boolean;
  jsonSchema: IntegrationDataContractJsonSchema;
}

export interface IntegrationDataContractField {
  resourceType: string;
  fieldPath: string;
  payloadKey: string;
  propertyName: string;
  displayName: string;
  dataType: string;
  jsonSchemaType: string;
  unit: string | null;
  codeSystem: string | null;
  required: boolean;
  derived: boolean;
  externalWritable: boolean;
  description: string;
}

export interface IntegrationDataContractResponse {
  contractId: string;
  packageVersion: string;
  schemaVersion: string;
  accessGuide: string[];
  resources: Record<string, IntegrationDataContractResource>;
  fields: IntegrationDataContractField[];
}

export interface DataQualityReport {
  reportId: string;
  tenantId: string;
  generatedAt: string;
  requiredFieldTotal: number;
  requiredFieldPresent: number;
  requiredFieldRate: number;
  adapterTotal: number;
  mappedAdapterCount: number;
  mappingRate: number;
  timelyAdapterCount: number;
  timelinessRate: number;
  notConnectedCount: number;
  misconfiguredCount: number;
  gapSummary: string;
  createdAt: string;
  createdBy: string;
  traceId?: string | null;
}

export interface IntegrationWebhookConfig {
  id: number;
  webhookId: string;
  name: string;
  callbackUrl: string;
  eventsSubscribed: string;
  status: "ACTIVE" | "SUSPENDED" | string;
  createdAt: string;
  updatedAt: string;
}

export interface WebhookCreateResult extends IntegrationWebhookConfig {
  sharedSecret: string;
}

export interface RegionalSource {
  sourceId: string;
  regionalNetworkName: string;
  sourceOrganizationId: string;
  sourceOrganizationName: string;
  trustLevel: string;
  evidenceText: string;
  adapterId: string | null;
  onboardingId: string | null;
  orgPath: string;
  status: string;
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
  status: "SUCCESS" | "FAILED" | "RETRYING" | "NOT_CONNECTED" | "DEAD_LETTER" | string;
  retryCount: number;
  maxRetries: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface IntegrationReplayResult {
  sourceMessageId: string;
  replayMessageId: string;
  traceId: string;
  status: "SUCCESS" | "FAILED" | "RETRYING" | "NOT_CONNECTED" | "DEAD_LETTER" | string;
  blocksMainFlow: boolean;
  message: string;
}

export interface IntegrationOnboarding {
  onboardingId: string;
  name: string;
  status: "REQUESTED" | "AUTH_CONFIGURED" | "MAPPING_CONFIGURED" | "ONLINE" | "OFFLINE" | string;
  routeType: "ADAPTER" | "FHIR" | string;
  routeReference: string;
  healthStatus: IntegrationAdapter["healthStatus"];
  mappedFieldCount: number;
  blockers: string[];
  sourceSystem: string;
  businessScenario: string;
  orgPath: string;
  callbackWebhookId: string | null;
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

export interface RegionalSourceRegisterPayload {
  sourceId: string;
  regionalNetworkName: string;
  sourceOrganizationId: string;
  sourceOrganizationName: string;
  trustLevel: "HIGH" | "MEDIUM" | "LOW";
  evidenceText: string;
  adapterId?: string;
  onboardingId?: string;
  orgPath: string;
}

export interface IntegrationOnboardingCreatePayload {
  onboardingId: string;
  name: string;
  accessMode: "ADAPTER" | "FHIR";
  adapterId?: string;
  fhirVersion?: string;
  sourceSystem: string;
  businessScenario: string;
  orgPath: string;
  callbackWebhookId?: string;
}

export interface IntegrationOnboardingAdvancePayload {
  onboardingId: string;
  targetStatus: "REQUESTED" | "AUTH_CONFIGURED" | "MAPPING_CONFIGURED" | "ONLINE" | "OFFLINE";
  evidenceText: string;
}

export interface WebhookSignatureTestResult {
  webhookId: string;
  callbackUrl: string;
  timestamp: number;
  signature: string;
  status: string;
  connectionStatus: "NOT_TESTED";
  message: string;
}

interface IntegrationEnvelope<T> {
  success: boolean;
  code: string;
  data: T;
}

export interface IntegrationAdaptersParams {
  page?: number;
  size?: number;
}

export interface IntegrationMaintenancePageParams {
  page?: number;
  size?: number;
}

function emptyIntegrationPage<T>(params: IntegrationMaintenancePageParams): PageResponse<T> {
  return {
    items: [],
    page: params.page ?? 1,
    size: params.size ?? 20,
    total: 0,
    hasNext: false,
    totalEstimated: false,
  };
}

// 1. 获取适配器目录
export function useIntegrationAdapters(params: IntegrationAdaptersParams = {}) {
  return useQuery({
    queryKey: ["integration", "adapters", params],
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<PageResponse<IntegrationAdapter>>>(
        "/engine/integration/adapters",
        { params },
      );
      return (
        data.data ?? {
          items: [],
          page: params.page ?? 1,
          size: params.size ?? 20,
          total: 0,
          hasNext: false,
          totalEstimated: false,
        }
      );
    },
  });
}

// 2. 创建适配器
export function useCreateAdapter() {
  return useMutation({
    mutationFn: async (payload: AdapterCreatePayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationAdapter>>(
        "/engine/integration/adapters",
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
        `/engine/integration/adapters/${adapterId}`,
        payload,
      );
      return data.data;
    },
  });
}

export function useIntegrationHealthSummary() {
  return useQuery({
    queryKey: ["integration", "health"],
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<AdapterHealthSummary>>(
        "/engine/integration/health",
      );
      return data.data;
    },
  });
}

export function useAdapterHubStatus() {
  return useQuery({
    queryKey: ["integration", "adapter-hub", "status"],
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<AdapterHubStatus>>(
        "/engine/integration/adapter-hub/status",
      );
      return data.data;
    },
  });
}

export function useMasterDataReconciliation(sourceSystem: string, enabled = false) {
  const normalizedSource = sourceSystem.trim().toUpperCase();
  return useQuery({
    queryKey: ["integration", "master-data", "reconciliation", normalizedSource],
    enabled: enabled && normalizedSource.length > 0,
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<MasterDataReconciliation>>(
        "/engine/integration/master-data/reconciliation",
        { params: { sourceSystem: normalizedSource } },
      );
      return data.data;
    },
  });
}

export function useIntegrationDataContract(packageVersion: string, enabled = false) {
  const normalizedVersion = packageVersion.trim();
  return useQuery({
    queryKey: ["integration", "data-contract", normalizedVersion],
    enabled: enabled && normalizedVersion.length > 0,
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<IntegrationDataContractResponse>>(
        "/engine/integration/data-contract",
        { params: { packageVersion: normalizedVersion } },
      );
      return data.data;
    },
  });
}

export function useGenerateDataQualityReport() {
  return useMutation({
    mutationFn: async () => {
      const { data } = await apiClient.post<IntegrationEnvelope<DataQualityReport>>(
        "/engine/integration/data-quality/reports",
      );
      return data.data;
    },
  });
}

export function useIntegrationOnboardings(params: IntegrationMaintenancePageParams = {}) {
  return useQuery({
    queryKey: ["integration", "onboardings", params],
    queryFn: async () => {
      const { data } = await apiClient.get<
        IntegrationEnvelope<PageResponse<IntegrationOnboarding>>
      >("/engine/integration/onboardings", { params });
      return data.data ?? emptyIntegrationPage<IntegrationOnboarding>(params);
    },
  });
}

export function useCreateIntegrationOnboarding() {
  return useMutation({
    mutationFn: async (payload: IntegrationOnboardingCreatePayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationOnboarding>>(
        "/engine/integration/onboardings",
        payload,
      );
      return data.data;
    },
  });
}

export function useAdvanceIntegrationOnboarding() {
  return useMutation({
    mutationFn: async ({
      onboardingId,
      targetStatus,
      evidenceText,
    }: IntegrationOnboardingAdvancePayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationOnboarding>>(
        `/engine/integration/onboardings/${onboardingId}/advance`,
        { targetStatus, evidenceText },
      );
      return data.data;
    },
  });
}

// 4. 健康检查：无真实连接器时只返回 NOT_CONNECTED/MISCONFIGURED，不伪造 HEALTHY。
export function useCheckAdapterHealth() {
  return useMutation({
    mutationFn: async (adapterId: string) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationAdapter>>(
        `/engine/integration/adapters/${adapterId}/health-check`,
      );
      return data.data;
    },
  });
}

// 5. 获取 Webhook 订阅配置
export function useWebhooks(params: IntegrationMaintenancePageParams = {}) {
  return useQuery({
    queryKey: ["integration", "webhooks", params],
    queryFn: async () => {
      const { data } = await apiClient.get<
        IntegrationEnvelope<PageResponse<IntegrationWebhookConfig>>
      >("/engine/integration/webhooks", { params });
      return data.data ?? emptyIntegrationPage<IntegrationWebhookConfig>(params);
    },
  });
}

// 6. 创建 Webhook
export function useCreateWebhook() {
  return useMutation({
    mutationFn: async (payload: WebhookCreatePayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<WebhookCreateResult>>(
        "/engine/integration/webhooks",
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
        "/engine/integration/webhooks/test",
        payload,
      );
      return data.data;
    },
  });
}

export function useRegionalSources(params: IntegrationMaintenancePageParams = {}) {
  return useQuery({
    queryKey: ["integration", "regional-sources", params],
    queryFn: async () => {
      const { data } = await apiClient.get<IntegrationEnvelope<PageResponse<RegionalSource>>>(
        "/engine/integration/regional-sources",
        { params },
      );
      return data.data ?? emptyIntegrationPage<RegionalSource>(params);
    },
  });
}

export function useRegisterRegionalSource() {
  return useMutation({
    mutationFn: async (payload: RegionalSourceRegisterPayload) => {
      const { data } = await apiClient.post<IntegrationEnvelope<RegionalSource>>(
        "/engine/integration/regional-sources",
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
      >("/engine/integration/logs", {
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
        `/engine/integration/logs/${messageId}/retry`,
      );
      return data.data;
    },
  });
}

// 10. 人工重放死信，原始证据保留
export function useReplayDeadLetter() {
  return useMutation({
    mutationFn: async (messageId: string) => {
      const { data } = await apiClient.post<IntegrationEnvelope<IntegrationReplayResult>>(
        `/engine/integration/dead-letter/${messageId}/replay`,
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
  fileUri: string;
  fileDigest: string;
  signatureAlgorithm: string;
  signatureValue: string;
  signerPublicKey: string;
  isValid: boolean;
  createdAt: string;
  createdBy: string;
}

export interface EvidenceVerifyResult {
  evidenceId: string;
  isValid: boolean;
  calculatedHash: string;
  storedHash: string;
  signatureAlgorithm: string;
  signatureValid: boolean;
  fileUri: string;
  fileDigest: string;
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
  archiveUri: string;
  contentType: string;
  itemCount: number;
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

// 4. 国密防篡改验签
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

// 5. 打包导出证据链真实文件
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

export interface ImplementationStep {
  key: string;
  title: string;
  status: "DONE" | "BLOCKED";
  blockers: string[];
  targetPath: string;
  evidence: string | null;
}

export interface OnboardingReadiness {
  tenantId: string;
  ready: boolean;
  steps: ImplementationStep[];
  blockers: string[];
  checkedAt: string;
}

export function useBranding() {
  return useQuery({
    queryKey: ["engine", "tenant", "branding"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Branding }>("/engine/tenant/branding");
      return data.data;
    },
  });
}

export function useUpdateBranding() {
  return useMutation({
    mutationFn: async (payload: Partial<Branding>) => {
      const { data } = await apiClient.post<{ data: Branding }>("/engine/tenant/branding", payload);
      return data.data;
    },
  });
}

export function useSuccessPlan(enabled = true) {
  return useQuery({
    queryKey: ["engine", "tenant", "success-plan"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SuccessPlan }>("/engine/tenant/success-plan");
      return data.data;
    },
    enabled,
  });
}

export function useImplementationSteps(enabled = true) {
  return useQuery({
    queryKey: ["engine", "tenant", "implementation-steps"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ImplementationStep[] }>(
        "/engine/tenant/implementation-steps",
      );
      return data.data;
    },
    enabled,
  });
}

export function useOnboardingReadiness(enabled = true) {
  return useQuery({
    queryKey: ["engine", "tenant", "onboarding-readiness"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: OnboardingReadiness }>(
        "/engine/tenant/onboarding-readiness",
      );
      return data.data;
    },
    enabled,
  });
}

export function useTransitionSuccessStage() {
  return useMutation({
    mutationFn: async (nextStage: string) => {
      const { data } = await apiClient.post<{ data: SuccessPlan }>(
        "/engine/tenant/success-plan/transition",
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
  level: "PLATFORM" | "TENANT" | "REGION" | "FACILITY" | "CAMPUS" | "DEPARTMENT" | "WARD";
  code: string;
  name: string;
  namePinyin?: string | null;
  facilityType?:
    | "HOSPITAL"
    | "SPECIALTY_HOSPITAL"
    | "BRANCH_HOSPITAL"
    | "COMMUNITY_HEALTH_CENTER"
    | "TOWNSHIP_CLINIC"
    | "VILLAGE_CLINIC"
    | "OUTPATIENT_CLINIC"
    | "STATION"
    | "OTHER"
    | null;
  specialtyId?: string | null;
  status?: "ACTIVE" | "SUSPENDED" | "ARCHIVED";
  createdAt?: string;
  createdBy?: string;
}

export interface OrgUserDirectoryItem {
  userId: string;
  displayName: string;
}

export type OrgDirectoryScope = "SERVICE_ORGANIZATION" | "BUSINESS_SCOPE";

export function useOrgUnits(params?: {
  page?: number;
  size?: number;
  sort?: string;
  keyword?: string;
  level?: OrgUnit["level"];
  status?: OrgUnit["status"];
  scope?: OrgDirectoryScope;
  ancestorId?: string;
}) {
  return useQuery({
    queryKey: ["engine", "org", "org-units", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<OrgUnit> }>(
        "/engine/org/org-units",
        {
          params,
        },
      );
      return data.data;
    },
  });
}

export function useOrgUsers(params?: { page?: number; size?: number; keyword?: string }) {
  return useQuery({
    queryKey: ["engine", "org", "org-units", "users", params ?? {}],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PageResponse<OrgUserDirectoryItem> }>(
        "/engine/org/org-units/users",
        { params },
      );
      return data.data;
    },
  });
}

export function useOrgUnitsByLevel(level: string) {
  return useQuery({
    queryKey: ["engine", "org", "org-units", "by-level", level],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: OrgUnit[] }>("/engine/org/org-units/by-level", {
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
      const { data } = await apiClient.post<{ data: OrgUnit }>("/engine/org/org-units", payload);
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
  activePathwayCount: number;
  averageAge: number;
  genderCounts: Record<string, number>;
}

export interface MpiPatientDetailResponse {
  patient: MpiPatient;
  latestContextSnapshot?: ContextSnapshotSummary | null;
  contextSnapshot?: ContextSnapshotResponse | null;
  activePathwayCount: number;
  activePathways: PatientPathway[];
  traceId: string;
}

export interface MpiPatientCreatePayload {
  maskedName: string;
  gender: "M" | "F" | "UNKNOWN";
  age: number;
  idLast4: string;
  idempotencyKey: string;
}

export interface MpiMergeResult {
  status: "MERGED" | string;
  sourceMpiId: string;
  targetMpiId: string;
  reviewId?: string | null;
  riskLevel?: string | null;
  message: string;
}

export interface MpiSplitPayload {
  sourceMpiId: string;
  reviewReason: string;
  idempotencyKey: string;
}

export interface MpiSplitResult {
  status: "SPLIT" | string;
  sourceMpiId: string;
  targetMpiId: string;
  message: string;
}

export function useMpiPatients(
  params: { keyword?: string; status?: string; page?: number; size?: number } = {},
) {
  return useQuery({
    queryKey: ["engine", "mpi", "patients", params],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { items: MpiPatient[]; total: number } }>(
        "/engine/mpi/patients",
        { params },
      );
      return data.data;
    },
  });
}

export function useCreateMpiPatient() {
  return useMutation({
    mutationFn: async (payload: MpiPatientCreatePayload) => {
      const { idempotencyKey, ...body } = payload;
      const { data } = await apiClient.post<{ data: MpiPatient }>("/engine/mpi/patients", body, {
        headers: { "Idempotency-Key": idempotencyKey },
      });
      return data.data;
    },
  });
}

export function useMpiStats() {
  return useQuery({
    queryKey: ["engine", "mpi", "stats"],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: MpiStatsResponse }>("/engine/mpi/stats");
      return data.data;
    },
  });
}

export function useMpiPatientDetail(mpiId?: string) {
  return useQuery({
    queryKey: ["engine", "mpi", "patients", mpiId],
    queryFn: async () => {
      if (!mpiId) return null;
      const { data } = await apiClient.get<{ data: MpiPatientDetailResponse }>(
        `/engine/mpi/patients/${mpiId}`,
      );
      return data.data;
    },
    enabled: !!mpiId,
  });
}

export interface MergeMpiPayload {
  sourceMpiId: string;
  targetMpiId: string;
  idempotencyKey: string;
}

export function useMergeMpiPatients() {
  return useMutation({
    mutationFn: async (payload: MergeMpiPayload) => {
      const { idempotencyKey, ...body } = payload;
      const { data } = await apiClient.post<{ data: MpiMergeResult }>(
        "/engine/mpi/patients:merge",
        body,
        { headers: { "Idempotency-Key": idempotencyKey } },
      );
      return data.data;
    },
  });
}

export function useSplitMpiPatient() {
  return useMutation({
    mutationFn: async (payload: MpiSplitPayload) => {
      const { idempotencyKey, sourceMpiId, ...body } = payload;
      const { data } = await apiClient.post<{ data: MpiSplitResult }>(
        `/engine/mpi/patients/${encodeURIComponent(sourceMpiId)}:split`,
        body,
        { headers: { "Idempotency-Key": idempotencyKey } },
      );
      return data.data;
    },
  });
}

// ──────────────────────────────────────────
// 身份安全服务包 · 统一用户管理（SVC-COMPLIANCE-01）
// ──────────────────────────────────────────
export interface ComplianceUserRole {
  code: string;
  displayName: string;
  scopeLevel: string;
  scopeCode: string;
  scopeName: string;
}

export interface ComplianceUserSummary {
  userId: string;
  displayName: string;
  username: string | null;
  credentialManaged: boolean;
  status: "ACTIVE" | "DISABLED" | "LOCKED" | string;
  mustChangePwd: boolean;
  roles: ComplianceUserRole[];
  createdAt: string;
}

export interface ComplianceUserDetail extends ComplianceUserSummary {
  effectivePermissions: SecurityProfile["permissions"];
  updatedAt: string;
}

export interface CreateComplianceUserPayload {
  credentialManaged: boolean;
  userId?: string;
  displayName?: string;
  username?: string;
  roleCode?: string;
  initialPassword?: string;
}

export interface CreateComplianceUserResult {
  user: ComplianceUserDetail;
  tempPassword: string | null;
}

export interface AssignComplianceUserRolePayload {
  userId: string;
  roleCode: string;
  scopeLevel: string;
  scopeCode: string;
}

export function useComplianceUsers(params: { page: number; size: number }) {
  return useQuery({
    queryKey: ["compliance", "users", params],
    queryFn: async () => {
      const response = await apiClient.get<{ data: PageResponse<ComplianceUserSummary> }>(
        "/compliance/users",
        { params },
      );
      return response.data.data;
    },
  });
}

export function useComplianceUserDetail(userId: string | null) {
  return useQuery({
    queryKey: ["compliance", "users", userId],
    queryFn: async () => {
      const response = await apiClient.get<{ data: ComplianceUserDetail }>(
        `/compliance/users/${encodeURIComponent(userId ?? "")}`,
      );
      return response.data.data;
    },
    enabled: Boolean(userId),
  });
}

function useInvalidateComplianceUsers() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["compliance", "users"] });
}

export function useCreateComplianceUser() {
  const invalidate = useInvalidateComplianceUsers();
  return useMutation({
    mutationFn: async (payload: CreateComplianceUserPayload) => {
      const response = await apiClient.post<{ data: CreateComplianceUserResult }>(
        "/compliance/users",
        payload,
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

export function useAssignComplianceUserRole() {
  const invalidate = useInvalidateComplianceUsers();
  return useMutation({
    mutationFn: async ({ userId, ...payload }: AssignComplianceUserRolePayload) => {
      const response = await apiClient.post<{ data: ComplianceUserDetail }>(
        `/compliance/users/${encodeURIComponent(userId)}/roles`,
        payload,
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

export function useRemoveComplianceUserRole() {
  const invalidate = useInvalidateComplianceUsers();
  return useMutation({
    mutationFn: async (payload: AssignComplianceUserRolePayload) => {
      const params = new URLSearchParams({
        scopeLevel: payload.scopeLevel,
        scopeCode: payload.scopeCode,
      });
      const response = await apiClient.delete<{ data: ComplianceUserDetail }>(
        `/compliance/users/${encodeURIComponent(payload.userId)}/roles/${encodeURIComponent(payload.roleCode)}?${params.toString()}`,
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

export function useResetComplianceUserPassword() {
  const invalidate = useInvalidateComplianceUsers();
  return useMutation({
    mutationFn: async (userId: string) => {
      const response = await apiClient.post<{ data: { tempPassword: string } }>(
        `/compliance/users/${encodeURIComponent(userId)}:reset-password`,
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

export function useSetComplianceUserStatus() {
  const invalidate = useInvalidateComplianceUsers();
  return useMutation({
    mutationFn: async (payload: { userId: string; status: "ACTIVE" | "DISABLED" }) => {
      const response = await apiClient.patch<{ data: ComplianceUserDetail }>(
        `/compliance/users/${encodeURIComponent(payload.userId)}/status`,
        { status: payload.status },
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

// ──────────────────────────────────────────
// 人员主数据 · 人员 / 任职 / 账号 / 身份来源
// ──────────────────────────────────────────
export type AppointmentType =
  | "INTERNAL"
  | "GROUP_SHARED"
  | "EXTERNAL_COLLABORATOR"
  | "IMPLEMENTATION";

export interface PersonnelSummary {
  personId: string;
  employeeNo: string;
  displayName: string;
  status: "ACTIVE" | "INACTIVE" | "LEFT" | string;
  appointmentType: AppointmentType | null;
  organizationId: string | null;
  organizationName: string | null;
  departmentId: string | null;
  departmentName: string | null;
  wardId: string | null;
  wardName: string | null;
  positionTitle: string | null;
  userId: string | null;
  username: string | null;
  accountState: string;
  identityCount: number;
}

export interface PersonnelAppointment {
  appointmentId: string;
  organizationId: string;
  organizationName: string;
  departmentId: string | null;
  departmentName: string | null;
  wardId: string | null;
  wardName: string | null;
  appointmentType: AppointmentType;
  positionTitle: string | null;
  primary: boolean;
  status: "ACTIVE" | "ENDED" | string;
}

export interface PersonnelDetail {
  person: {
    personId: string;
    employeeNo: string;
    displayName: string;
    status: string;
    createdAt: string;
    updatedAt: string;
  };
  primaryAppointment: PersonnelAppointment | null;
  appointments: PersonnelAppointment[];
  account: { userId: string; username: string | null; state: string } | null;
  identities: IdentityBinding[];
  oneTimeActivation: { username: string; temporaryPassword: string } | null;
}

export interface CreatePersonnelPayload {
  employeeNo: string;
  displayName: string;
  appointment: {
    organizationId: string;
    departmentId?: string;
    wardId?: string;
    appointmentType: AppointmentType;
    positionTitle?: string;
    primary: boolean;
  };
  account?: {
    loginName: string;
    roleCode?: string;
  };
  identity?: {
    providerType: IdentityProviderType;
    externalSubject: string;
  };
}

export interface PersonnelImportRow {
  rowNo: number;
  employeeNo: string | null;
  displayName: string | null;
  action: string;
  status: string;
  message: string | null;
  resultPersonId: string | null;
}

export interface PersonnelImportResponse {
  jobId: string;
  fileName: string;
  status: string;
  totalRows: number;
  validRows: number;
  conflictRows: number;
  successRows: number;
  failureRows: number;
  rows: PersonnelImportRow[];
  oneTimeActivations: Array<{ username: string; temporaryPassword: string }>;
}

export function usePersonnel(
  params: { page: number; size: number; keyword?: string },
  options: { enabled?: boolean } = {},
) {
  return useQuery({
    queryKey: ["compliance", "personnel", params],
    queryFn: async () => {
      const response = await apiClient.get<{ data: PageResponse<PersonnelSummary> }>(
        "/compliance/personnel",
        { params },
      );
      return response.data.data;
    },
    enabled: options.enabled ?? true,
  });
}

export function usePersonnelDetail(personId: string | null) {
  return useQuery({
    queryKey: ["compliance", "personnel", personId],
    queryFn: async () => {
      const response = await apiClient.get<{ data: PersonnelDetail }>(
        `/compliance/personnel/${encodeURIComponent(personId ?? "")}`,
      );
      return response.data.data;
    },
    enabled: Boolean(personId),
  });
}

function useInvalidatePersonnel() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["compliance", "personnel"] });
}

export function useCreatePersonnel() {
  const invalidate = useInvalidatePersonnel();
  return useMutation({
    mutationFn: async (payload: CreatePersonnelPayload) => {
      const response = await apiClient.post<{ data: PersonnelDetail }>(
        "/compliance/personnel",
        payload,
      );
      return response.data.data;
    },
    onSuccess: invalidate,
  });
}

export function usePreviewPersonnelImport() {
  return useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append("file", file);
      const response = await apiClient.post<{ data: PersonnelImportResponse }>(
        "/compliance/personnel/imports:preview",
        form,
        { headers: { "Content-Type": "multipart/form-data" } },
      );
      return response.data.data;
    },
  });
}

export function useCommitPersonnelImport() {
  const invalidate = useInvalidatePersonnel();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (jobId: string) => {
      const response = await apiClient.post<{ data: PersonnelImportResponse }>(
        `/compliance/personnel/imports/${encodeURIComponent(jobId)}:commit`,
      );
      return response.data.data;
    },
    onSuccess: async () => {
      await Promise.all([
        invalidate(),
        queryClient.invalidateQueries({ queryKey: ["compliance", "identity-bindings"] }),
      ]);
    },
  });
}

export async function downloadPersonnelImportTemplate() {
  const response = await apiClient.get<Blob>("/compliance/personnel/import-template", {
    responseType: "blob",
  });
  return response.data;
}

// ──────────────────────────────────────────
// 身份安全服务包 · 外部身份绑定（SVC-COMPLIANCE-01）
// ──────────────────────────────────────────
export type IdentityProviderType = "OIDC" | "CAS" | "SAML" | "EMPLOYEE_NO" | "SM_CA";

export interface IdentityBinding {
  bindingId: string;
  userId: string;
  providerType: IdentityProviderType;
  subjectHint: string;
  status: "ACTIVE" | "UNBOUND";
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateIdentityBindingPayload {
  userId: string;
  providerType: IdentityProviderType;
  externalSubject: string;
  reason: string;
}

export interface UnbindIdentityBindingPayload {
  bindingId: string;
  reason: string;
  expectedVersion: number;
}

export interface IdentityBindingsParams {
  page?: number;
  size?: number;
}

export async function fetchIdentityBindings(
  params: IdentityBindingsParams = {},
): Promise<PageResponse<IdentityBinding>> {
  const requestParams = {
    page: params.page ?? 1,
    size: params.size ?? 20,
  };
  const response = await apiClient.get<{ data: PageResponse<IdentityBinding> }>(
    "/compliance/identity-bindings",
    { params: requestParams },
  );
  return response.data.data;
}

export async function createIdentityBinding(
  payload: CreateIdentityBindingPayload,
): Promise<IdentityBinding> {
  const response = await apiClient.post<{ data: IdentityBinding }>(
    "/compliance/identity-bindings",
    payload,
  );
  return response.data.data;
}

export async function unbindIdentityBinding(
  payload: UnbindIdentityBindingPayload,
): Promise<IdentityBinding> {
  const response = await apiClient.post<{ data: IdentityBinding }>(
    `/compliance/identity-bindings/${encodeURIComponent(payload.bindingId)}:unbind`,
    {
      reason: payload.reason,
      expectedVersion: payload.expectedVersion,
    },
  );
  return response.data.data;
}

export function useIdentityBindings(params: IdentityBindingsParams = {}) {
  const queryParams = {
    page: params.page ?? 1,
    size: params.size ?? 20,
  };
  return useQuery({
    queryKey: ["compliance", "identity-bindings", queryParams],
    queryFn: () => fetchIdentityBindings(queryParams),
  });
}

export function useCreateIdentityBinding() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createIdentityBinding,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["compliance", "identity-bindings"] }),
  });
}

export function useUnbindIdentityBinding() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: unbindIdentityBinding,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["compliance", "identity-bindings"] }),
  });
}

// ──────────────────────────────────────────
// 首次部署 · 平台种子身份
// ──────────────────────────────────────────
export interface BootstrapStartResult {
  valid: boolean;
  expiresAt: string;
}

export interface BootstrapStatus {
  initialized: boolean;
}

export interface BootstrapAdminPayload {
  token: string;
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
  secret?: string;
  code?: string;
}

export interface BootstrapMfaResult {
  mfaBound: boolean;
  secret?: string;
  otpauthUri?: string;
  recoveryCode?: string;
}

export async function fetchBootstrapStatus() {
  const resp = await apiClient.get<{ data: BootstrapStatus }>("/bootstrap/status");
  return resp.data.data;
}

export function useBootstrapStatus(enabled = true) {
  return useQuery({
    queryKey: ["bootstrap", "status"],
    queryFn: fetchBootstrapStatus,
    enabled,
    retry: false,
    staleTime: 30_000,
  });
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
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createBootstrapAdmin,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["bootstrap", "status"] }),
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
  session?: SessionStatus;
}

export interface SessionStatus {
  remainingSeconds: number;
  idleTimeoutSeconds: number;
  warningSeconds: number;
  maxSessionSeconds: number;
  maxSessionRemainingSeconds: number;
  serverTime: string;
}

export interface DelegatedAuthStatus {
  mode: "PLATFORM" | "DELEGATED" | "BOTH" | string;
  enabled: boolean;
  status: "READY" | "NOT_CONNECTED" | "DISABLED" | string;
  providers: string[];
  message: string;
}

export interface LoginTenantOption {
  tenantId: string;
  name: string;
  kind: "PLATFORM" | "CUSTOMER" | string;
}

export interface LoginTenantDirectory {
  primaryTenants: LoginTenantOption[];
  platformTenant: LoginTenantOption;
  hasCustomerTenants: boolean;
}

type DelegatedAuthStatusEnvelope = {
  data: DelegatedAuthStatus;
};

export async function fetchDelegatedAuthStatus(): Promise<DelegatedAuthStatus> {
  const resp = await apiClient.get<DelegatedAuthStatusEnvelope>("/auth/delegated/status");
  return resp.data.data;
}

export async function fetchLoginTenantDirectory(): Promise<LoginTenantDirectory> {
  const resp = await apiClient.get<{ data: LoginTenantDirectory }>("/auth/login-tenants");
  return resp.data.data;
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

export function useSessionStatus() {
  return useQuery({
    queryKey: ["auth", "session"],
    queryFn: async () => {
      const resp = await apiClient.get<{ data: SessionStatus }>("/auth/session");
      return resp.data.data;
    },
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
}

export function useDelegatedAuthStatus(enabled = true) {
  return useQuery({
    queryKey: ["auth", "delegated", "status"],
    queryFn: fetchDelegatedAuthStatus,
    enabled,
    retry: false,
    staleTime: 60_000,
  });
}

export function useLoginTenantDirectory() {
  return useQuery({
    queryKey: ["auth", "login-tenants"],
    queryFn: fetchLoginTenantDirectory,
    retry: false,
    staleTime: 60_000,
  });
}

export function useRenewSession() {
  return useMutation({
    mutationFn: async () => {
      const resp = await apiClient.post<{ data: SessionStatus }>("/auth/session/renew");
      return resp.data.data;
    },
  });
}

// ──────────────────────────────────────────
// 鉴权 · 平台租户开通（平台治理管理员）
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

export function useTenants(enabled = true) {
  return useQuery({
    queryKey: ["platform-tenants"],
    queryFn: async () => {
      const resp = await apiClient.get<{ data: TenantSummary[] }>("/admin/tenants");
      return resp.data.data;
    },
    enabled,
  });
}

export function useProvisionTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: ProvisionTenantPayload) => {
      const resp = await apiClient.post<{ data: ProvisionTenantResult }>("/admin/tenants", payload);
      return resp.data.data;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["platform-tenants"] }),
        queryClient.invalidateQueries({ queryKey: ["auth", "login-tenants"] }),
      ]);
    },
  });
}
