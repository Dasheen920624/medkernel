import assert from "node:assert/strict";
import { mkdir, mkdtemp, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import test from "node:test";

import { hasBlockingViolations, scanRepository } from "./b0-perfect-check.mjs";

async function write(root, file, content) {
  await mkdir(join(root, file, ".."), { recursive: true });
  await writeFile(join(root, file), content);
}

function approvedClinicalContent() {
  return {
    dsl: {
      then: [
        {
          actionCode: "STRONG_REMINDER",
          requiresPhysicianConfirmation: true,
        },
      ],
    },
    testCases: ["POSITIVE", "NEGATIVE", "BOUNDARY", "CONFLICT"].map(
      (caseType) => ({
        caseType,
      }),
    ),
  };
}

function sandboxScenario(index, overrides = {}) {
  const base = {
    id: `sbx-test-${index}`,
    ruleCode: index === 1 ? "SBX.LAB.CRITICAL.K" : `SBX.TEST.${index}`,
    ruleType: index === 1 ? "LAB" : "ORDER",
    triggerPoint: index === 1 ? "result-review" : "order-sign",
    riskLevel: index === 1 ? "CRITICAL" : "HIGH",
    actionCode: index === 1 ? "STRONG_REMINDER" : "REMIND",
    reviewStatus: "CLINICAL_REVIEW_REQUIRED",
    reviewEvidence: null,
    name: `沙盘测试场景 ${index}`,
    sourceRef: null,
    changeSummary: null,
    clinicalContent: null,
  };
  if (index === 1) {
    return {
      ...base,
      reviewStatus: "APPROVED_FOR_SANDBOX",
      reviewEvidence: "工程金样已验证",
      sourceRef: "检验危急值管理制度",
      changeSummary: "仅开放高钾金样",
      clinicalContent: approvedClinicalContent(),
      ...overrides,
    };
  }
  return { ...base, ...overrides };
}

function sandboxManifest(overrides = {}) {
  const scenarios = Array.from({ length: 10 }, (_, index) =>
    sandboxScenario(index + 1),
  );
  for (const [index, scenarioOverride] of Object.entries(overrides)) {
    scenarios[Number(index)] = sandboxScenario(
      Number(index) + 1,
      scenarioOverride,
    );
  }
  return JSON.stringify({ schemaVersion: 1, scenarios }, null, 2);
}

function screenshotChainSpecContent() {
  return [
    "test('B0 截图链', async ({ page }, testInfo) => {",
    "  collectBrowserErrors(page);",
    "  collectServerErrors(page);",
    "  collectNetworkFailures(page);",
    "  await page.goto('/rule/definitions');",
    "  await page.goto('/config/packages');",
    "  await testInfo.attach('b0-login-desktop', { path: 'b0-login-desktop.png' });",
    "  await testInfo.attach('b0-header-user-menu', { path: 'b0-header-user-menu.png' });",
    "  await testInfo.attach('b0-rule-definitions-desktop', { path: 'b0-rule-definitions-desktop.png' });",
    "  await testInfo.attach('b0-rule-definitions-390px', { path: 'b0-rule-definitions-390px.png' });",
    "  await testInfo.attach('b0-config-packages-release-modal', { path: 'b0-config-packages-release-modal.png' });",
    "  await testInfo.attach('b0-screenshot-chain-runtime-records', { path: 'b0-screenshot-chain-runtime-records.json' });",
    "});",
  ].join("\n");
}

function largeScaleDialectSmokeContent() {
  return [
    "class B0LargeScaleDialectSmokeTest {",
    "  PostgreSQLContainer<?> postgres;",
    "  OracleContainer oracle;",
    "  static final int TOTAL_ROWS = 100_000;",
    '  static final String ENABLE_ENV = "B0_100K_DIALECT_SMOKE";',
    "  void postgresHandlesHundredThousandKnowledgeAndTerminologyRows() {}",
    "  void oracleHandlesHundredThousandKnowledgeAndTerminologyRows() {}",
    '  void seedKnowledgeIdentities() { String table = "knowledge_identity"; }',
    '  void seedTerminologyRows() { String tables = "standard_term local_term term_mapping mapping_candidate mapping_conflict"; }',
    "  void assertKnowledgeExportEquivalentScan() {}",
    "  void assertKnowledgeExportEquivalentFile() { Files.newBufferedWriter(null); percentile95(null); }",
    "  void assertCandidateAndConflictQueries() { assertCandidateAndConflictPageP95(null); }",
    "  void refreshTerminologySourceStatsSql() {}",
    "  void refreshTerminologyPlannerStatsSql() {}",
    "}",
  ].join("\n");
}

function knowledgeExportLargeScaleContent() {
  return [
    "/** API-03 知识异步导出的 B0 本地 10 万级链路合同。 */",
    "class KnowledgeExportServiceLargeScaleTest {",
    "  @Transactional(propagation = Propagation.NOT_SUPPORTED)",
    "  void submitPollAndDownloadExportsHundredThousandFilteredIdentities() throws Exception {",
    '    seedKnowledgeIdentities(100_000, "DRUG", "DRUG.PERF.", "ACTIVE");',
    "    KnowledgeExportJob submitted = service.submit(",
    "      ExportType.IDENTITIES,",
    '      """',
    '          {"domain":"DRUG","status":"ACTIVE"}',
    '          """',
    "    );",
    "    KnowledgeExportJob completed = service.get(submitted.jobCode());",
    "    service.downloadFile(submitted.jobCode());",
    "    assertThat(completed.itemCount()).isEqualTo(100_000L);",
    "  }",
    "}",
  ].join("\n");
}

function qcEvalSetsContent() {
  return [
    "const EVALUATION_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "function QcEvalSets() {",
    "  const [evaluationPackageSearch, setEvaluationPackageSearch] = useState('');",
    '  const evaluationPackagesQuery = usePackages({ page: 1, size: EVALUATION_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "EVALUATION", keyword: evaluationPackageSearch || undefined });',
    "  const packageOptions = evaluationPackagesQuery.data?.items ?? [];",
    "  return <>",
    '    <Select showSearch filterOption={false} onSearch={setEvaluationPackageSearch} placeholder="选择已存在的评估配置包版本" options={packageOptions} />',
    '    <Select showSearch filterOption={false} onSearch={setEvaluationPackageSearch} placeholder="选择仿真使用的评估配置包版本" options={packageOptions} />',
    "  </>;",
    "}",
  ].join("\n");
}

function qcEvalSetsTestContent(extra = "") {
  return [
    "describe('QcEvalSets', () => {",
    "  it('loads evaluation package selectors through small server-side search pages', () => {});",
    "});",
    extra,
  ].join("\n");
}

function insuranceAuditContent(extra = "") {
  return [
    "const AUDIT_INDICATOR_REFERENCE_PAGE_SIZE = 20;",
    "function InsuranceAudit() {",
    "  const [indicatorSearch, setIndicatorSearch] = useState('');",
    "  const indicatorKeyword = indicatorSearch.trim();",
    '  const indicatorsQuery = useEvaluationIndicators({ status: "ACTIVE", ...(indicatorKeyword ? { indicatorCode: indicatorKeyword } : {}), page: 1, size: AUDIT_INDICATOR_REFERENCE_PAGE_SIZE, sort: "name,asc" }, { enabled: true });',
    '  return <Select showSearch filterOption={false} onSearch={setIndicatorSearch} onClear={() => setIndicatorSearch("")} options={indicatorsQuery.data?.items ?? []} />;',
    "}",
    extra,
  ].join("\n");
}

function insuranceAuditTestContent(extra = "") {
  return [
    "describe('InsuranceAudit', () => {",
    "  it('loads audit indicator selector through small server-side search pages', () => {});",
    "});",
    extra,
  ].join("\n");
}

function followupContent(extra = "") {
  return [
    "const FOLLOWUP_PLAN_PAGE_SIZE = 20;",
    "const FOLLOWUP_TEMPLATE_PAGE_SIZE = 20;",
    "function Followup() {",
    "  const [planPage, setPlanPage] = useState(1);",
    "  const [templatePage, setTemplatePage] = useState(1);",
    "  const [templateSearch, setTemplateSearch] = useState('');",
    "  const [publishedTemplateSearch, setPublishedTemplateSearch] = useState('');",
    "  const { data: apiPlansData } = useFollowupPlans({ patientId: patientFilter.trim() || undefined, page: planPage, size: FOLLOWUP_PLAN_PAGE_SIZE });",
    '  const templatesQuery = useFollowupTemplates({ page: templatePage, size: FOLLOWUP_TEMPLATE_PAGE_SIZE, sort: "updatedAt,desc", keyword: templateSearch.trim() || undefined });',
    '  const publishedTemplatesQuery = useFollowupTemplates({ assetStatus: "PUBLISHED", page: 1, size: FOLLOWUP_TEMPLATE_PAGE_SIZE, sort: "updatedAt,desc", keyword: publishedTemplateSearch.trim() || undefined });',
    "  return <>",
    "    <Table pagination={{ current: apiPlansData?.page ?? planPage, pageSize: apiPlansData?.size ?? FOLLOWUP_PLAN_PAGE_SIZE, total: apiPlansData?.total ?? displayPlans.length, showSizeChanger: false, onChange: (page) => setPlanPage(page) }} />",
    "    <Table pagination={{ current: templatesQuery.data?.page ?? templatePage, pageSize: templatesQuery.data?.size ?? FOLLOWUP_TEMPLATE_PAGE_SIZE, total: templatesQuery.data?.total ?? templates.length, showSizeChanger: false, onChange: (page) => setTemplatePage(page) }} />",
    '    <Select showSearch filterOption={false} onSearch={setPublishedTemplateSearch} onClear={() => setPublishedTemplateSearch("")} options={publishedTemplatesQuery.data?.items ?? []} />',
    "  </>;",
    "}",
    extra,
  ].join("\n");
}

function followupTestContent(extra = "") {
  return [
    "describe('Followup', () => {",
    "  it('loads follow-up plans through server-side table pagination', () => {});",
    "  it('loads follow-up templates through server-side pagination and published-template search', () => {});",
    "});",
    extra,
  ].join("\n");
}

function followupTemplateRepositoryContent(extra = "") {
  return [
    "interface FollowupTemplateRepository {",
    "  List<FollowupTemplate> pageByFilter(String tenantId, String keyword, String assetStatus, int offset, int limit);",
    "  long countByFilter(String tenantId, String keyword, String assetStatus);",
    '  String sql = "JOIN mk_version_asset_version av ON av.tenant_id = ft.tenant_id AND av.version_id = ft.asset_version_id AND (:assetStatus IS NULL OR av.status = :assetStatus) OR LOWER(ft.template_code) LIKE :keyword OR LOWER(ft.name) LIKE :keyword OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";',
    "}",
    extra,
  ].join("\n");
}

function followupTemplateServiceContent(extra = "") {
  return [
    "class FollowupTemplateService {",
    "  PageResponse<FollowupTemplateResponse> list(FollowupTemplateFilter filter, PageRequest pageRequest) {",
    "    String keyword = normalizeKeyword(filter.keyword());",
    "    String assetStatus = filter.assetStatus().name();",
    "    long total = templates.countByFilter(tenantId, keyword, assetStatus);",
    "    return PageResponse.of(templates.pageByFilter(tenantId, keyword, assetStatus, page.offset(), page.safeSize()), page, total);",
    "  }",
    "  private static String normalizeKeyword(String value) { return value == null ? null : '%' + value.trim().toLowerCase() + '%'; }",
    "}",
    extra,
  ].join("\n");
}

function followupTemplateServiceTestContent(extra = "") {
  return [
    "class FollowupTemplateServiceTest {",
    "  void listTemplatesUsesRepositoryFilterPaginationInsteadOfTenantSnapshot() {}",
    "}",
    extra,
  ].join("\n");
}

function identityBindingContent(extra = "") {
  return [
    "const IDENTITY_BINDING_PAGE_SIZE = 20;",
    "const PERSONNEL_REFERENCE_PAGE_SIZE = 20;",
    "function IdentityBinding() {",
    "  const [bindingPage, setBindingPage] = useState(1);",
    "  useIdentityBindings({ page: bindingPage, size: IDENTITY_BINDING_PAGE_SIZE });",
    "  const [userSearch, setUserSearch] = useState('');",
    "  usePersonnel({ page: 1, size: PERSONNEL_REFERENCE_PAGE_SIZE, keyword: userSearch || undefined });",
    "}",
    extra,
  ].join("\n");
}

function operationalControlPagesTestContent(extra = "") {
  return [
    "describe('operational control pages', () => {",
    "  it('loads identity binding personnel selector through small server-side pages', () => {});",
    "});",
    extra,
  ].join("\n");
}

function identityBindingRepositoryContent(extra = "") {
  return [
    "interface IdentityBindingRepository {",
    "  List<IdentityBinding> pageByTenantId(String tenantId, int offset, int limit);",
    "  long countByTenantId(String tenantId);",
    "}",
    extra,
  ].join("\n");
}

function identityBindingServiceContent(extra = "") {
  return [
    "class IdentityBindingService {",
    "  PageResponse<IdentityBindingResponse> list(String tenantId, PageRequest pageRequest) { return null; }",
    "}",
    extra,
  ].join("\n");
}

function identityBindingControllerTestContent(extra = "") {
  return [
    "class IdentityBindingControllerTest {",
    '  void listsBindingsOnlyInsideCurrentTenant() { String path = "$.data.items"; }',
    "}",
    extra,
  ].join("\n");
}

function apiHooksContent(extra = "") {
  return [
    "interface PageResponse<T> { items: T[]; page: number; size: number; total: number; hasNext: boolean; totalEstimated: boolean; }",
    "interface KnowledgeCustomizationsParams { page?: number; size?: number; }",
    "interface ExportApprovalsParams { resourceType?: string; status?: ExportApprovalStatus; page?: number; size?: number; sort?: string; }",
    "interface DataPermissionPoliciesParams { resourceType?: string; action?: DataPermissionAction; page?: number; size?: number; }",
    "interface MaskingRulesParams { resourceType?: string; fieldName?: string; page?: number; size?: number; }",
    "export interface IntegrationAdaptersParams { page?: number; size?: number; }",
    "export interface IntegrationMaintenancePageParams { page?: number; size?: number; }",
    "export interface OverrideTemplatesParams { page?: number; size?: number; }",
    "export interface PackageReleaseAdaptersParams { page?: number; size?: number; }",
    "export interface PackageSyncLogParams { page?: number; size?: number; }",
    "export interface KnowledgeVersionsParams { page?: number; size?: number; sort?: string; }",
    "export interface KnowledgeProvenanceParams { page?: number; size?: number; sort?: string; }",
    "export interface KnowledgeCandidatesParams { page?: number; size?: number; sort?: string; }",
    "interface KnowledgeCandidateResponse { candidates: PageResponse<KnowledgeAssetVersion>; classifications: CandidateClassification[]; }",
    "interface KnowledgeProvenanceResponse { versions: PageResponse<KnowledgeAssetVersion>; supersessions: PageResponse<KnowledgeSupersession>; }",
    "interface ConditionFragmentAffectedAsset { assetType: string; assetId: string; assetCode: string; displayName: string; impactReason: string; }",
    "interface ConditionFragmentImpactResponse { affectedAssets: PageResponse<ConditionFragmentAffectedAsset>; }",
    "function emptyIntegrationPage<T>(params: IntegrationMaintenancePageParams) { return { items: [], page: params.page ?? 1, size: params.size ?? 20, total: 0, hasNext: false, totalEstimated: false }; }",
    "function compactOneBasedPageParams(params) { return params.page < 1 ? { ...params, page: 1 } : params; }",
    "function useKnowledgeVersions(identityId?: number, params: KnowledgeVersionsParams = {}) {",
    '  return useQuery({ queryKey: ["knowledge", "versions", identityId, params], queryFn: () => apiClient.get<{ data: PageResponse<KnowledgeAssetVersion> }>(`/engine/knowledge/identities/${identityId}/versions`, { params }) });',
    "}",
    "function useKnowledgeProvenance(identityId?: number, params: KnowledgeProvenanceParams = {}) {",
    '  return useQuery({ queryKey: ["knowledge", "provenance", identityId, params], queryFn: () => apiClient.get<{ data: KnowledgeProvenanceResponse }>(`/engine/knowledge/identities/${identityId}/provenance`, { params }) });',
    "}",
    "function useKnowledgeCandidates(identityId?: number, params: KnowledgeCandidatesParams = {}) {",
    "  const requestParams = { page: params.page ?? 1, size: params.size ?? 20, sort: params.sort };",
    '  return useQuery({ queryKey: ["knowledge", "candidates", identityId, requestParams], queryFn: () => apiClient.get<{ data: KnowledgeCandidateResponse }>(`/engine/knowledge/identities/${identityId}/candidates`, { params: requestParams }) });',
    "}",
    "function useKnowledgeCustomizations(params: KnowledgeCustomizationsParams = {}, enabled = true) {",
    "  const queryParams = { page: params.page ?? 1, size: params.size ?? 20 };",
    '  return useQuery({ queryKey: ["knowledge", "customizations", queryParams], enabled, queryFn: () => apiClient.get<{ data: PageResponse<KnowledgeCustomization> }>("/engine/knowledge/customizations", { params: queryParams }) });',
    "}",
    "function fetchExportApprovals(params: ExportApprovalsParams = {}) {",
    '  return apiClient.get<{ data: PageResponse<ExportApproval> }>("/compliance/exports", { params });',
    "}",
    "function fetchDataPermissionPolicies(params: DataPermissionPoliciesParams = {}) {",
    '  return apiClient.get<{ data: PageResponse<DataPermissionPolicy> }>("/compliance/data-permissions", { params });',
    "}",
    "function fetchMaskingRules(params: MaskingRulesParams = {}) {",
    '  return apiClient.get<{ data: PageResponse<MaskingRule> }>("/compliance/masking-rules", { params });',
    "}",
    "function useExportApprovals(params: ExportApprovalsParams = {}, enabled = true) {",
    '  return useQuery({ queryKey: ["compliance", "export-approvals", params], enabled, queryFn: () => fetchExportApprovals(params) });',
    "}",
    "function useIntegrationAdapters(params: IntegrationAdaptersParams = {}) {",
    '  return useQuery({ queryKey: ["integration", "adapters", params], queryFn: () => apiClient.get<IntegrationEnvelope<PageResponse<IntegrationAdapter>>>("/engine/integration/adapters", { params }) });',
    "}",
    "function useIntegrationOnboardings(params: IntegrationMaintenancePageParams = {}) {",
    '  return useQuery({ queryKey: ["integration", "onboardings", params], queryFn: () => apiClient.get<IntegrationEnvelope<PageResponse<IntegrationOnboarding>>>("/engine/integration/onboardings", { params }).then(() => emptyIntegrationPage<IntegrationOnboarding>(params)) });',
    "}",
    "function useWebhooks(params: IntegrationMaintenancePageParams = {}) {",
    '  return useQuery({ queryKey: ["integration", "webhooks", params], queryFn: () => apiClient.get<IntegrationEnvelope<PageResponse<IntegrationWebhookConfig>>>("/engine/integration/webhooks", { params }).then(() => emptyIntegrationPage<IntegrationWebhookConfig>(params)) });',
    "}",
    "function useRegionalSources(params: IntegrationMaintenancePageParams = {}) {",
    '  return useQuery({ queryKey: ["integration", "regional-sources", params], queryFn: () => apiClient.get<IntegrationEnvelope<PageResponse<RegionalSource>>>("/engine/integration/regional-sources", { params }).then(() => emptyIntegrationPage<RegionalSource>(params)) });',
    "}",
    "function useOverrideTemplates(params: OverrideTemplatesParams = {}) {",
    '  return useQuery({ queryKey: ["release-governance", "override-templates", params], queryFn: () => apiClient.get<{ data: PageResponse<OverrideTemplate> }>("/engine/versioning/releases/override-templates", { params }) });',
    "}",
    "function usePackageSyncLogs(packageId: string, params: PackageSyncLogParams = {}) {",
    '  return useQuery({ queryKey: ["packages", "sync-logs", packageId, params], queryFn: () => apiClient.get<{ data: PageResponse<SyncLogResponse> }>(`/engine/pkg/packages/${packageId}/sync-logs`, { params }) });',
    "}",
    "function usePackageReleaseAdapters(params: PackageReleaseAdaptersParams = {}, enabled = true) {",
    '  return useQuery({ queryKey: ["packages", "release-adapters", params], enabled, queryFn: () => apiClient.get<{ data: PageResponse<PackageReleaseAdapter> }>("/engine/pkg/packages/release-adapters", { params }) });',
    "}",
    "function useAuthoringBatchJobs(options = {}) {",
    "  const page = options.page ?? 1;",
    "  const size = options.size ?? 20;",
    '  return useQuery({ queryKey: ["authoring", "batch-jobs", { page, size }], enabled: options.enabled ?? true, queryFn: () => apiClient.get<{ data: PageResponse<AuthoringBatchJobResponse> }>("/engine/authoring/batch", { params: { page, size } }) });',
    "}",
    "function useConditionFragmentImpact(fragmentId: string, params = {}, options = {}) {",
    '  return useQuery({ queryKey: ["authoring", "condition-fragment-impact", fragmentId, params], queryFn: () => apiClient.get<{ data: ConditionFragmentImpactResponse }>(`/engine/authoring/fragments/${fragmentId}/impact`, { params }) });',
    "}",
    "function useTerminologyMappings(params = {}) { const requestParams = compactOneBasedPageParams(params ?? {}); return useQuery({ queryKey: ['terminology', 'mappings', requestParams] }); }",
    "function useStandardTerms(params = {}) { const requestParams = compactOneBasedPageParams(params); return useQuery({ queryKey: ['terminology', 'standard', requestParams] }); }",
    "function useLocalTerms(params = {}) { const requestParams = compactOneBasedPageParams(params); return useQuery({ queryKey: ['terminology', 'local', requestParams] }); }",
    "function useTerminologyCandidates(params = {}) { const requestParams = compactOneBasedPageParams(params); return useQuery({ queryKey: ['terminology', 'candidates', requestParams] }); }",
    "function useTerminologyConflicts(params = {}) { const requestParams = compactOneBasedPageParams(params); return useQuery({ queryKey: ['terminology', 'conflicts', requestParams] }); }",
    extra,
  ].join("\n");
}

function apiHooksTestContent(extra = "") {
  return [
    "describe('knowledge review api helpers', () => {",
    "  it('loads institution knowledge customizations through server pagination', () => {",
    "    useKnowledgeCustomizations({ page: 1, size: 20 }, true);",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/knowledge/customizations", { params: { page: 1, size: 20 } });',
    "  });",
    "  it('loads export approvals through server pagination', () => {",
    '    fetchExportApprovals({ resourceType: "AUDIT_EVENT", status: "REQUESTED", page: 1, size: 20 });',
    '    expect(apiClient.get).toHaveBeenCalledWith("/compliance/exports", { params: { resourceType: "AUDIT_EVENT", status: "REQUESTED", page: 1, size: 20 } });',
    "  });",
    "  it('loads data permissions and masking rules through server pagination', () => {",
    '    fetchDataPermissionPolicies({ resourceType: "clinical_case", action: "READ", page: 2, size: 20 });',
    '    fetchMaskingRules({ resourceType: "clinical_case", page: 3, size: 10 });',
    "  });",
    "  it('normalizes terminology pagination to one-based requests', () => {",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/terminology/terms/standard", { params: { page: 1, size: 20, standardSystem: "LOINC", status: "ACTIVE" } });',
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/terminology/mappings/candidates", { params: { page: 1, size: 10, status: "PENDING", riskLevel: "HIGH" } });',
    "  });",
    "  it('loads integration adapters through server pagination', () => {",
    "    useIntegrationAdapters({ page: 2, size: 20 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/adapters", { params: { page: 2, size: 20 } });',
    "  });",
    "  it('loads override templates through server pagination', () => {",
    "    useOverrideTemplates({ page: 2, size: 10 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/versioning/releases/override-templates", { params: { page: 2, size: 10 } });',
    "  });",
    "  it('loads persisted sync logs from the API-10 sync-log endpoint', () => {",
    '    usePackageSyncLogs("pkg-1", { page: 2, size: 10 });',
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/pkg-1/sync-logs", { params: { page: 2, size: 10 } });',
    "  });",
    "  it('loads package release adapters through server pagination', () => {",
    "    usePackageReleaseAdapters({ page: 2, size: 10 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/pkg/packages/release-adapters", { params: { page: 2, size: 10 } });',
    "  });",
    "  it('loads knowledge versions through server pagination', () => {",
    "    useKnowledgeVersions(42, { page: 2, size: 10 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/knowledge/identities/42/versions", { params: { page: 2, size: 10 } });',
    "  });",
    "  it('loads exact provenance history through server pagination', () => {",
    "    useKnowledgeProvenance(42, { page: 2, size: 10 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/knowledge/identities/42/provenance", { params: { page: 2, size: 10 } });',
    "  });",
    "  it('loads knowledge candidates through server pagination', () => {",
    "    useKnowledgeCandidates(42, { page: 2, size: 10 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/knowledge/identities/42/candidates", { params: { page: 2, size: 10 } });',
    "  });",
    "  it('loads adapter hub maintenance ledgers through server pagination', () => {",
    "    useIntegrationOnboardings({ page: 2, size: 10 });",
    "    useWebhooks({ page: 3, size: 10 });",
    "    useRegionalSources({ page: 4, size: 10 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/onboardings", { params: { page: 2, size: 10 } });',
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/webhooks", { params: { page: 3, size: 10 } });',
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/regional-sources", { params: { page: 4, size: 10 } });',
    "  });",
    "  it('loads authoring batch jobs through server pagination', () => {",
    "    useAuthoringBatchJobs({ page: 2, size: 20 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/authoring/batch", { params: { page: 2, size: 20 } });',
    "  });",
    "  it('loads condition fragment impact through paged authoring endpoint', () => {",
    "    useConditionFragmentImpact('frag-renal-v1', { page: 2, size: 20 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/authoring/fragments/frag-renal-v1/impact", { params: { page: 2, size: 20 } });',
    "  });",
    "});",
    extra,
  ].join("\n");
}

function securityBaselinePanelsContent(extra = "") {
  return [
    "const SECURITY_RULE_PAGE_SIZE = 20;",
    "function DataPermissionPanel() {",
    "  const [policyPage, setPolicyPage] = useState(1);",
    "  const policies = useDataPermissionPolicies({ page: policyPage, size: SECURITY_RULE_PAGE_SIZE });",
    "  const policyItems = policies.data?.items ?? [];",
    "  const defaultPolicy = policyItems[0];",
    "  return <Table dataSource={policyItems} pagination={{ current: policies.data?.page ?? policyPage, pageSize: policies.data?.size ?? SECURITY_RULE_PAGE_SIZE, total: policies.data?.total ?? 0, onChange: setPolicyPage }} />;",
    "}",
    "function MaskingRulePanel() {",
    "  const [rulePage, setRulePage] = useState(1);",
    "  const rules = useMaskingRules({ page: rulePage, size: SECURITY_RULE_PAGE_SIZE });",
    "  const ruleItems = rules.data?.items ?? [];",
    "  const defaultRule = ruleItems[0];",
    "  return <Table dataSource={ruleItems} pagination={{ current: rules.data?.page ?? rulePage, pageSize: rules.data?.size ?? SECURITY_RULE_PAGE_SIZE, total: rules.data?.total ?? 0, onChange: setRulePage }} />;",
    "}",
    extra,
  ].join("\n");
}

function securityBaselineTestContent(extra = "") {
  return [
    "describe('SecurityBaseline', () => {",
    "  it('loads data permission and masking ledgers through server pagination', () => {",
    "    expect(useDataPermissionPolicies).toHaveBeenCalledWith({ page: 1, size: 20 });",
    "    expect(useMaskingRules).toHaveBeenCalledWith({ page: 1, size: 20 });",
    "  });",
    "});",
    extra,
  ].join("\n");
}

function dataPermissionPolicyRepositoryContent(extra = "") {
  return [
    "interface DataPermissionPolicyRepository {",
    "  long countPolicies(String tenantId, String resourceType, String action);",
    "  List<DataPermissionPolicy> pagePolicies(String tenantId, String resourceType, String action, int offset, int limit);",
    '  String sql = "ORDER BY resource_type ASC, action ASC, id ASC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";',
    "}",
    extra,
  ].join("\n");
}

function dataPermissionServiceContent(extra = "") {
  return [
    "class DataPermissionService {",
    "  PageResponse<DataPermissionPolicyResponse> listPolicies(String tenantId, String resourceType, DataPermissionAction action, PageRequest request) {",
    "    repository.countPolicies(tenantId, resourceType, action.name());",
    "    repository.pagePolicies(tenantId, resourceType, action.name(), request.offset(), request.safeSize());",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function dataPermissionControllerContent(extra = "") {
  return [
    "class DataPermissionController {",
    "  ApiResult<PageResponse<DataPermissionPolicyResponse>> listPolicies(String resourceType, DataPermissionAction action, Integer page, Integer size) {",
    "    return ApiResult.ok(service.listPolicies(tenantId, resourceType, action, new PageRequest(page, size, null)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function dataPermissionServiceTestContent(extra = "") {
  return [
    "class DataPermissionServiceTest {",
    "  void listPoliciesReturnsTenantScopedPageInsteadOfUnboundedList() {",
    "    verify(repository).pagePolicies(null, null, null, 0, 20);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function dataPermissionControllerSecurityTestContent(extra = "") {
  return [
    "class DataPermissionControllerSecurityTest {",
    '  void listPolicies_auditRoleWithTenant_returns200() { jsonPath("$.data.items").isArray(); }',
    "}",
    extra,
  ].join("\n");
}

function maskingRuleRepositoryContent(extra = "") {
  return [
    "interface MaskingRuleRepository {",
    "  long countRules(String tenantId, String resourceType, String fieldName);",
    "  List<MaskingRule> pageRules(String tenantId, String resourceType, String fieldName, int offset, int limit);",
    '  String sql = "ORDER BY resource_type ASC, field_name ASC, scenario_code ASC, id ASC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";',
    "}",
    extra,
  ].join("\n");
}

function maskingServiceContent(extra = "") {
  return [
    "class MaskingService {",
    "  PageResponse<MaskingRuleResponse> listRules(String tenantId, String resourceType, String fieldName, PageRequest request) {",
    "    repository.countRules(tenantId, resourceType, fieldName);",
    "    repository.pageRules(tenantId, resourceType, fieldName, request.offset(), request.safeSize());",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function maskingRuleControllerContent(extra = "") {
  return [
    "class MaskingRuleController {",
    "  ApiResult<PageResponse<MaskingRuleResponse>> listRules(String resourceType, String fieldName, Integer page, Integer size) {",
    "    return ApiResult.ok(service.listRules(tenantId, resourceType, fieldName, new PageRequest(page, size, null)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function maskingServiceTestContent(extra = "") {
  return [
    "class MaskingServiceTest {",
    "  void listRulesReturnsTenantScopedPageInsteadOfUnboundedList() {",
    "    verify(repository).pageRules(null, null, null, 0, 20);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function maskingRuleControllerSecurityTestContent(extra = "") {
  return [
    "class MaskingRuleControllerSecurityTest {",
    '  void listRules_auditRoleWithTenant_returns200() { jsonPath("$.data.items").isArray(); }',
    "}",
    extra,
  ].join("\n");
}

function adminAuditContent(extra = "") {
  return [
    "const APPROVAL_PAGE_SIZE = 20;",
    "function AdminAudit() {",
    "  const [approvalPage, setApprovalPage] = useState(1);",
    '  const approvals = useExportApprovals({ resourceType: "AUDIT_EVENT", page: approvalPage, size: APPROVAL_PAGE_SIZE }, canApproveExport);',
    "  return <Table dataSource={approvals.data?.items ?? []} pagination={{ current: approvals.data?.page ?? approvalPage, pageSize: APPROVAL_PAGE_SIZE, total: approvals.data?.total ?? 0, onChange: setApprovalPage }} />;",
    "}",
    extra,
  ].join("\n");
}

function adminAuditTestContent(extra = "") {
  return [
    "describe('AdminAudit', () => {",
    '  it("keeps audit readers out of export approval queries and controls", () => {',
    '    expect(useExportApprovals).toHaveBeenCalledWith({ resourceType: "AUDIT_EVENT", page: 1, size: 20 }, false);',
    "  });",
    "});",
    extra,
  ].join("\n");
}

function exportApprovalRepositoryContent(extra = "") {
  return [
    "interface ExportApprovalRepository {",
    "  long countByFilter(String tenantId, String resourceType, String status);",
    "  List<ExportApproval> pageByFilter(String tenantId, String resourceType, String status, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function exportApprovalServiceContent(extra = "") {
  return [
    "class ExportApprovalService {",
    "  PageResponse<ExportApprovalResponse> listApprovals(String tenantId, String resourceType, ExportApprovalStatus status, PageRequest pageRequest) {",
    "    repository.countByFilter(tenantId, resourceType, statusName);",
    "    repository.pageByFilter(tenantId, resourceType, statusName, page.offset(), page.safeSize());",
    "    return PageResponse.empty(page);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function exportApprovalControllerContent(extra = "") {
  return [
    "class ExportApprovalController {",
    "  ApiResult<PageResponse<ExportApprovalResponse>> listExports(String resourceType, ExportApprovalStatus status, Integer page, Integer size, String sort) {",
    "    return ApiResult.ok(service.listApprovals(tenantId, resourceType, status, new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function exportApprovalServiceTestContent(extra = "") {
  return [
    "class ExportApprovalServiceTest {",
    "  void listApprovalsReturnsTenantScopedRowsFilteredByStatusAndResource() {",
    "    repository.countByFilter(\"t-1\", \"audit_event\", \"REQUESTED\");",
    "    repository.pageByFilter(\"t-1\", \"audit_event\", \"REQUESTED\", 0, 20);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function exportApprovalControllerSecurityTestContent(extra = "") {
  return [
    "class ExportApprovalControllerSecurityTest {",
    '  void listExports_auditRoleWithTenant_returns200() { String path = "$.data.items[0].status"; }',
    "}",
    extra,
  ].join("\n");
}

function knowledgeGovernanceContent(extra = "") {
  return [
    "const KNOWLEDGE_REVIEW_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "const KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE = 20;",
    "const KNOWLEDGE_CANDIDATE_PAGE_SIZE = 20;",
    "function KnowledgeGovernance() {",
    "  const [reviewPackageSearch, setReviewPackageSearch] = useState('');",
    "  const [candidatePage, setCandidatePage] = useState(1);",
    "  const [customizationPage, setCustomizationPage] = useState(1);",
    '  const knowledgePackagesQuery = usePackages({ page: 1, size: KNOWLEDGE_REVIEW_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "KNOWLEDGE", keyword: reviewPackageSearch || undefined });',
    "  const candidatesQuery = useKnowledgeCandidates(selectedIdentityId, { page: candidatePage, size: KNOWLEDGE_CANDIDATE_PAGE_SIZE });",
    "  const candidatePageData = candidatesQuery.data?.candidates;",
    "  const candidates = useMemo(() => candidatePageData?.items ?? [], [candidatePageData?.items]);",
    "  const customizationsQuery = useKnowledgeCustomizations({ page: customizationPage, size: KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE }, true);",
    "  const reviewPackageOptions = knowledgePackagesQuery.data?.items ?? [];",
    "  const customizationItems = useMemo(() => customizationsQuery.data?.items ?? [], [customizationsQuery.data?.items]);",
    "  return <>",
    '    <Select showSearch filterOption={false} onSearch={setReviewPackageSearch} placeholder="选择已存在的知识配置包版本" options={reviewPackageOptions} />',
    "    <Table dataSource={candidates} pagination={{ current: candidatePageData?.page ?? candidatePage, pageSize: candidatePageData?.size ?? KNOWLEDGE_CANDIDATE_PAGE_SIZE, total: candidatePageData?.total ?? 0, showSizeChanger: false, onChange: setCandidatePage }} />",
    "    <Table dataSource={customizationItems} pagination={{ current: customizationsQuery.data?.page ?? customizationPage, pageSize: customizationsQuery.data?.size ?? KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE, total: customizationsQuery.data?.total ?? 0, showSizeChanger: false, onChange: (page) => setCustomizationPage(page) }} />",
    "  </>;",
    "}",
    extra,
  ].join("\n");
}

function knowledgeGovernanceTestContent(extra = "") {
  return [
    "describe('KnowledgeGovernance', () => {",
    "  it('loads knowledge review package selector through small server-side search pages', () => {});",
    "  it('loads institution knowledge customizations through small server-side pages', () => {",
    "    expect(mockUseKnowledgeCustomizations).toHaveBeenCalledWith({ page: 1, size: 20 }, true);",
    "  });",
    "  it('loads candidate review queue through server-side pages', () => {",
    "    expect(mockUseKnowledgeCandidates).toHaveBeenLastCalledWith(42, { page: 1, size: 20 });",
    "  });",
    "});",
    extra,
  ].join("\n");
}

function knowledgeCustomizationRepositoryContent(extra = "") {
  return [
    "interface KnowledgeCustomizationRepository {",
    "  List<KnowledgeCustomization> pageByTenantId(String tenantId, int offset, int limit);",
    "  long countByTenantId(String tenantId);",
    "}",
    extra,
  ].join("\n");
}

function knowledgeCustomizationServiceContent(extra = "") {
  return [
    "class KnowledgeCustomizationService {",
    "  PageResponse<KnowledgeCustomizationResponse> list(PageRequest pageRequest) {",
    "    customizations.pageByTenantId(tenantId, page.offset(), page.safeSize());",
    "    return PageResponse.of(List.of(), page, customizations.countByTenantId(tenantId));",
    "  }",
    "  String nextLocalVersionNo(String tenantId, Long identityId, String platformVersionNo) {",
    "    long next = versions.countByTenantIdAndIdentityId(tenantId, identityId) + 1L;",
    "    return platformVersionNo + \"-local-\" + next;",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeCustomizationControllerContent(extra = "") {
  return [
    "class KnowledgeCustomizationController {",
    "  ApiResult<PageResponse<KnowledgeCustomizationResponse>> list(Integer page, Integer size, String sort) {",
    "    return ApiResult.ok(service.list(new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeCustomizationServiceTestContent(extra = "") {
  return [
    "class KnowledgeCustomizationServiceTest {",
    "  void listsLocalDerivativesThroughRepositoryPaginationInsteadOfTenantSnapshot() {}",
    "}",
    extra,
  ].join("\n");
}

function knowledgeIdentityRepositoryContent(extra = "") {
  return [
    "interface KnowledgeIdentityRepository {",
    "  long countEffectiveByFilter(String tenantId, String platformTenantId, String domain, String specialtyId, String tenantStatus, String platformStatus, String keyword);",
    "  List<KnowledgeIdentity> pageEffectiveByFilter(String tenantId, String platformTenantId, String domain, String specialtyId, String tenantStatus, String platformStatus, String keyword, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function knowledgeIdentityControllerContent(extra = "") {
  return [
    "class KnowledgeIdentityController {",
    "  ApiResult<KnowledgeLineage> getLineage(Long id, Integer page, Integer size, String sort) {",
    "    return ApiResult.ok(service.getLineage(id, new PageRequest(page, size, sort)));",
    "  }",
    "  ApiResult<KnowledgeProvenanceResponse> getProvenance(Long id, Integer page, Integer size, String sort) {",
    "    return ApiResult.ok(service.getProvenance(id, new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeIdentityServiceContent(extra = "") {
  return [
    "class KnowledgeIdentityService {",
    "  PageResponse<KnowledgeIdentity> page(PageRequest request, KnowledgeIdentityFilter filter) {",
    "    identityRepository.countEffectiveByFilter(tenantId, PlatformTenant.ID, domain, specialtyId, status, platformStatus, keyword);",
    "    identityRepository.pageEffectiveByFilter(tenantId, PlatformTenant.ID, domain, specialtyId, status, platformStatus, keyword, offset, size);",
    "    return PageResponse.empty(request);",
    "  }",
    "  KnowledgeLineage getLineage(Long identityId, PageRequest request) {",
    "    PageRequest safeRequest = request == null ? PageRequest.defaults() : request;",
    "    return new KnowledgeLineage(identity, versionHistoryPage(effective, safeRequest), supersessionPage(effective, safeRequest));",
    "  }",
    "  KnowledgeProvenanceResponse getProvenance(Long identityId, PageRequest request) {",
    "    PageRequest safeRequest = request == null ? PageRequest.defaults() : request;",
    "    return new KnowledgeProvenanceResponse(identity, currentVersionId, versionHistoryPage(effective, safeRequest), supersessionPage(effective, safeRequest), sourceEvidence, 0, false);",
    "  }",
    "  PageResponse<KnowledgeAssetVersion> versionHistoryPage(EffectiveKnowledgeIdentity effective, PageRequest request) {",
    "    versionRepository.countByTenantIdAndIdentityId(effective.sourceTenantId(), effective.identity().id());",
    "    versionRepository.pageByTenantIdAndIdentityId(effective.sourceTenantId(), effective.identity().id(), request.offset(), request.safeSize());",
    "    return PageResponse.empty(request);",
    "  }",
    "  PageResponse<KnowledgeSupersession> supersessionPage(EffectiveKnowledgeIdentity effective, PageRequest request) {",
    "    supersessionRepository.countByTenantIdAndIdentityId(effective.sourceTenantId(), effective.identity().id());",
    "    supersessionRepository.pageByTenantIdAndIdentityId(effective.sourceTenantId(), effective.identity().id(), request.offset(), request.safeSize());",
    "    return PageResponse.empty(request);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeIdentityServiceTestContent(extra = "") {
  return [
    "class KnowledgeIdentityServiceTest {",
    "  void pageMergesCustomerLocalOverridesWithPlatformActiveIdentities() { Mockito.verify(identityRepo, Mockito.never()).listByFilter(any(), any(), any(), any(), any()); }",
    "  void lineageBundlesIdentityVersionsAndSupersessions() {",
    "    versionRepo.countByTenantIdAndIdentityId(\"t-1\", 1L);",
    "    supersessionRepo.countByTenantIdAndIdentityId(\"t-1\", 1L);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeIdentityRepositoryTestContent(extra = "") {
  return [
    "class KnowledgeIdentityRepositoryTest {",
    "  void pagesEffectiveTenantIdentitiesWithoutMaterializingTenantAndPlatformSnapshots() {}",
    "}",
    extra,
  ].join("\n");
}

function knowledgeAssetVersionRepositoryContent(extra = "") {
  return [
    "interface KnowledgeAssetVersionRepository {",
    "  long countByTenantIdAndIdentityIdAndVersionNoIgnoreCase(String tenantId, Long identityId, String versionNo);",
    "  boolean existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase(String tenantId, Long identityId, String versionNo);",
    "  Optional<KnowledgeAssetVersion> findByTenantIdAndIdentityIdAndContentHash(String tenantId, Long identityId, String contentHash);",
    "  Optional<KnowledgeAssetVersion> findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(String tenantId, Long identityId, KnowledgeVersionStatus status);",
    "  long countByTenantIdAndIdentityId(String tenantId, Long identityId);",
    "  List<KnowledgeAssetVersion> pageByTenantIdAndIdentityId(String tenantId, Long identityId, int offset, int limit);",
    "  long countPendingReplacementCandidatesByTenantIdAndIdentityId(String tenantId, Long identityId);",
    "  List<KnowledgeAssetVersion> pagePendingReplacementCandidatesByTenantIdAndIdentityId(String tenantId, Long identityId, int offset, int limit);",
    '  String sql = "ORDER BY created_at DESC, id DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";',
    "}",
    extra,
  ].join("\n");
}

function candidateClassificationRepositoryContent(extra = "") {
  return [
    "interface CandidateClassificationRepository {",
    "  List<CandidateClassification> findByTenantIdAndCandidateVersionIdIn(String tenantId, List<Long> candidateVersionIds);",
    "}",
    extra,
  ].join("\n");
}

function knowledgeCandidateResponseContent(extra = "") {
  return [
    "record KnowledgeCandidateResponse(",
    "  Long identityId,",
    "  PageResponse<KnowledgeAssetVersion> candidates,",
    "  List<CandidateClassification> classifications,",
    "  boolean available,",
    "  String reasonCode,",
    "  String message",
    ") {}",
    extra,
  ].join("\n");
}

function knowledgeVersionServiceContent(extra = "") {
  return [
    "class KnowledgeVersionService {",
    "  PageResponse<KnowledgeAssetVersion> listByIdentity(Long identityId, PageRequest request) {",
    "    long total = versionRepository.countByTenantIdAndIdentityId(effective.sourceTenantId(), effective.identity().id());",
    "    return PageResponse.of(versionRepository.pageByTenantIdAndIdentityId(effective.sourceTenantId(), effective.identity().id(), request.offset(), request.safeSize()), request, total);",
    "  }",
    "  KnowledgeCandidateResponse listCandidates(Long identityId, PageRequest request) {",
    "    long total = versionRepository.countPendingReplacementCandidatesByTenantIdAndIdentityId(tenantId, identityId);",
    "    List<KnowledgeAssetVersion> candidates = versionRepository.pagePendingReplacementCandidatesByTenantIdAndIdentityId(tenantId, identityId, request.offset(), request.safeSize());",
    "    List<CandidateClassification> classifications = candidateClassificationRepository.findByTenantIdAndCandidateVersionIdIn(tenantId, List.of(22L));",
    "    return new KnowledgeCandidateResponse(identityId, PageResponse.of(candidates, safeRequest, total), classifications, true, \"OK\", \"知识候选审核工作流已可用\");",
    "  }",
    "  KnowledgeCandidateResponse classifyCandidate(Long identityId, KnowledgeVersionCreateRequest request) {",
    "    versionRepository.existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase(tenantId, identityId, request.versionNo());",
    "    versionRepository.findByTenantIdAndIdentityIdAndContentHash(tenantId, identityId, contentHash);",
    "    versionRepository.findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(tenantId, identityId, KnowledgeVersionStatus.ACTIVE);",
    "    return null;",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeVersionControllerContent(extra = "") {
  return [
    "class KnowledgeVersionController {",
    "  ApiResult<PageResponse<KnowledgeAssetVersion>> listByIdentity(Long identityId, Integer page, Integer size, String sort) {",
    "    return ApiResult.ok(versionService.listByIdentity(identityId, new PageRequest(page, size, sort)));",
    "  }",
    "  ApiResult<KnowledgeCandidateResponse> candidates(Long identityId, Integer page, Integer size, String sort) {",
    "    return ApiResult.ok(versionService.listCandidates(identityId, new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeVersionServiceTestContent(extra = "") {
  return [
    "class KnowledgeVersionServiceTest {",
    "  void listByIdentityFallsBackToPlatformIdentityWhenCustomerHasNoLocalOverride() {",
    "    versionRepo.pageByTenantIdAndIdentityId(\"t-1\", 100L, 1, 1);",
    "  }",
    "  void classifyCandidateUsesPointLookupsInsteadOfLoadingAllIdentityVersions() {}",
    "  void listCandidatesPagesPendingCandidatesAndLoadsOnlyCurrentPageClassifications() {",
    "    versionRepo.countPendingReplacementCandidatesByTenantIdAndIdentityId(\"t-1\", 1L);",
    "    versionRepo.pagePendingReplacementCandidatesByTenantIdAndIdentityId(\"t-1\", 1L, 5, 5);",
    "    candidateClassificationRepo.findByTenantIdAndCandidateVersionIdIn(\"t-1\", List.of(22L));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeProvenanceResponseContent(extra = "") {
  return [
    "record KnowledgeProvenanceResponse(",
    "  KnowledgeIdentity identity,",
    "  Long currentVersionId,",
    "  PageResponse<KnowledgeAssetVersion> versions,",
    "  PageResponse<KnowledgeSupersession> supersessions",
    ") {}",
    extra,
  ].join("\n");
}

function knowledgeLineageContent(extra = "") {
  return [
    "record KnowledgeLineage(",
    "  KnowledgeIdentity identity,",
    "  PageResponse<KnowledgeAssetVersion> versions,",
    "  PageResponse<KnowledgeSupersession> supersessions",
    ") {}",
    extra,
  ].join("\n");
}

function knowledgeSupersessionRepositoryContent(extra = "") {
  return [
    "interface KnowledgeSupersessionRepository {",
    "  long countByTenantIdAndIdentityId(String tenantId, Long identityId);",
    "  List<KnowledgeSupersession> pageByTenantIdAndIdentityId(String tenantId, Long identityId, int offset, int limit);",
    '  String sql = "ORDER BY transitioned_at DESC, id DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";',
    "}",
    extra,
  ].join("\n");
}

function knowledgeAssetApiContractTestContent(extra = "") {
  return [
    "class KnowledgeAssetApiContractTest {",
    "  void provenanceRouteReturnsExactSourceChainInsteadOfAuditSnapshot() {",
    "    identityService.getProvenance(eq(1L), any());",
    '    String path = "$.data.versions.items[0].id";',
    "  }",
    "  void candidatesRouteReturnsClassificationWorkflowContract() {",
    '    String path = "$.data.candidates.items[0].id";',
    "  }",
    "}",
    extra,
  ].join("\n");
}

function advancedProvenanceContent(extra = "") {
  return [
    "const PROVENANCE_HISTORY_PAGE_SIZE = 20;",
    "function Provenance() {",
    "  const [historyPage, setHistoryPage] = useState(1);",
    "  const provenanceQuery = useKnowledgeProvenance(selectedIdentityId, {",
    "    page: historyPage,",
    "    size: PROVENANCE_HISTORY_PAGE_SIZE,",
    "  });",
    "  const versionItems = provenance.versions.items ?? [];",
    "  return <Table dataSource={versionItems} pagination={{ current: provenance.versions.page ?? historyPage, pageSize: provenance.versions.size ?? PROVENANCE_HISTORY_PAGE_SIZE, total: provenance.versions.total ?? 0, onChange: setHistoryPage }} />;",
    "}",
    extra,
  ].join("\n");
}

function advancedProvenanceTestContent(extra = "") {
  return [
    "describe('Provenance', () => {",
    "  it('renders an exact knowledge source chain instead of the audit snapshot console', () => {",
    "    expect(mockUseKnowledgeProvenance).toHaveBeenCalledWith(1, { page: 1, size: 20 });",
    "  });",
    "});",
    extra,
  ].join("\n");
}

function authoringAssetsContent(extra = "") {
  return [
    "const CLONE_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "function AuthoringAssets() {",
    "  const [clonePackageSearch, setClonePackageSearch] = useState('');",
    "  const clonePackagesQuery = usePackages({ page: 1, size: CLONE_PACKAGE_REFERENCE_PAGE_SIZE, keyword: clonePackageSearch || cloneAsset?.packageVersion || undefined, assetType: cloneAsset.assetType });",
    "  const clonePackageOptions = clonePackagesQuery.data?.items ?? [];",
    "  const knownClonePackageVersions = new Set(clonePackageOptions.map((option) => option.value.trim()));",
    "  const clonePackageVersionRules = [",
    "    { validator: () => {",
    "      if (clonePackagesQuery.isError) throw new Error('配置包列表不可用，暂不能克隆资产。');",
    "      if (!knownClonePackageVersions.has(packageVersion)) throw new Error('请选择已存在的配置包版本。');",
    "    } },",
    "  ];",
    '  return <Select showSearch filterOption={false} onSearch={setClonePackageSearch} placeholder="选择克隆草稿所属配置包版本" options={clonePackageOptions} />;',
    "}",
    extra,
  ].join("\n");
}

function authoringAssetsTestContent(extra = "") {
  return [
    "describe('AuthoringAssets', () => {",
    "  it('blocks cloning when the selected package version is not loaded from package selector', () => {});",
    "});",
    extra,
  ].join("\n");
}

function authoringAssetLibraryServiceContent(extra = "") {
  return [
    "class AuthoringAssetLibraryService {",
    "  PageResponse<AuthoringAssetLibraryItem> listRepositoryPage(PageRequest page) {",
    "    RepositoryAssetPage source = loadRepositoryPage();",
    "    return PageResponse.of(source.items(), page, source.total());",
    "  }",
    "  PageResponse<AuthoringAssetLibraryItem> listWithProfileFilters(PageRequest page) {",
    "    String favoriteUserId = favoriteOnly ? userId : null;",
    "    RepositoryAssetPage source = loadProfileFilteredRepositoryPage(tagPattern(tag), favoriteUserId);",
    "    return PageResponse.of(source.items(), page, source.total());",
    "  }",
    "  RepositoryAssetPage loadRepositoryPage() {",
    "    rules.countByFilter(null, null, null, null, null);",
    "    rules.pageByFilter(null, null, null, null, null, page.offset(), page.safeSize());",
    "    pathways.countByFilter(null, null, null, null, null, null);",
    "    pathways.pageByFilter(null, null, null, null, null, null, page.offset(), page.safeSize());",
    "    fragments.countByFilter(null, null, null, null);",
    "    fragments.pageByFilter(null, null, null, null, page.offset(), page.safeSize());",
    "    followupTemplates.countByFilter(null, null, null);",
    "    followupTemplates.pageByFilter(null, null, null, page.offset(), page.safeSize());",
    "    return new RepositoryAssetPage(List.of(), 0);",
    "  }",
    "  RepositoryAssetPage loadProfileFilteredRepositoryPage(String tagPattern, String favoriteUserId) {",
    "    rules.countForAuthoringLibrary(null, null, tagPattern, favoriteUserId);",
    "    rules.pageForAuthoringLibrary(null, null, tagPattern, favoriteUserId, page.offset(), page.safeSize());",
    "    pathways.countForAuthoringLibrary(null, null, tagPattern, favoriteUserId);",
    "    pathways.pageForAuthoringLibrary(null, null, tagPattern, favoriteUserId, page.offset(), page.safeSize());",
    "    fragments.countForAuthoringLibrary(null, null, tagPattern, favoriteUserId);",
    "    fragments.pageForAuthoringLibrary(null, null, tagPattern, favoriteUserId, page.offset(), page.safeSize());",
    "    followupTemplates.countForAuthoringLibrary(null, null, tagPattern, favoriteUserId);",
    "    followupTemplates.pageForAuthoringLibrary(null, null, tagPattern, favoriteUserId, page.offset(), page.safeSize());",
    "    return new RepositoryAssetPage(List.of(), 0);",
    "  }",
    "  record RepositoryAssetPage(List<AuthoringAssetLibraryItem> items, long total) {}",
    "}",
    extra,
  ].join("\n");
}

function authoringAssetLibraryServiceTestContent(extra = "") {
  return [
    "class AuthoringAssetLibraryServiceTest {",
    "  void listsTypedFollowupAssetsThroughRepositoryPagination() {}",
    "  void listsRulesPathwaysAndFragmentsWithTagsAndFavorites() {}",
    "}",
    extra,
  ].join("\n");
}

function authoringAssetLibraryRepositoryTestContent(extra = "") {
  return [
    "class AuthoringAssetLibraryRepositoryTest {",
    "  void fragmentImpactPrefilterQueriesExecuteThroughRepositoryPagination() {}",
    "}",
    extra,
  ].join("\n");
}

function conditionFragmentServiceContent(extra = "") {
  return [
    "class ConditionFragmentService {",
    "  ConditionFragmentImpactResponse impact(String fragmentId, PageRequest pageRequest) {",
    "    ruleDefinitions.countActiveRuleImpactsByFragmentPattern(tenantId, fragmentPattern);",
    "    ruleDefinitions.pageActiveRuleImpactsByFragmentPattern(tenantId, fragmentPattern, page.offset(), page.safeSize());",
    "    pathwayTemplates.countTemplateImpactsByFragmentPattern(tenantId, fragmentPattern);",
    "    pathwayTemplates.pageTemplateImpactsByFragmentPattern(tenantId, fragmentPattern, page.offset(), page.safeSize());",
    "    return new ConditionFragmentImpactResponse(fragmentId, fragmentCode, 1, packageVersion, PageResponse.empty(pageRequest), digest, traceId);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function conditionFragmentImpactResponseContent(extra = "") {
  return [
    "record ConditionFragmentImpactResponse(",
    "  String fragmentId,",
    "  String fragmentCode,",
    "  Integer versionNo,",
    "  String packageVersion,",
    "  PageResponse<ConditionFragmentAffectedAsset> affectedAssets,",
    "  String impactDigest",
    ") {}",
    extra,
  ].join("\n");
}

function conditionFragmentControllerContent(extra = "") {
  return [
    "class ConditionFragmentController {",
    "  ApiResult<ConditionFragmentImpactResponse> impact(String fragmentId, Integer page, Integer size, String sort) {",
    "    return ApiResult.ok(service.impact(fragmentId, new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function conditionFragmentServiceTestContent(extra = "") {
  return [
    "class ConditionFragmentServiceTest {",
    "  void impactFindsRulesAndPathwaysReferencingSameFragment() {",
    "    verify(ruleDefinitions, never()).listByFilter(\"tenant-A\", null, null, null, null);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function conditionFragmentControllerTestContent(extra = "") {
  return [
    "class ConditionFragmentControllerTest {",
    '  void impactEndpointReturnsAffectedRulesAndPathways() { String path = "$.data.affectedAssets.items[0].assetType"; }',
    "}",
    extra,
  ].join("\n");
}

function authoringBatchDrawerContent(extra = "") {
  return [
    "const RULE_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "const AUTHORING_BATCH_JOB_PAGE_SIZE = 20;",
    "function AuthoringBatchDrawer() {",
    "  const [jobPage, setJobPage] = useState(1);",
    "  const [rulePackageSearch, setRulePackageSearch] = useState('');",
    "  const jobsQuery = useAuthoringBatchJobs({ page: jobPage, size: AUTHORING_BATCH_JOB_PAGE_SIZE, enabled: open });",
    '  const rulePackagesQuery = usePackages({ page: 1, size: RULE_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "RULE", keyword: rulePackageSearch || undefined });',
    "  return <>",
    '    <Select showSearch filterOption={false} onSearch={setRulePackageSearch} placeholder="选择规则配置包版本" options={rulePackagesQuery.data?.items ?? []} />',
    "    <Table dataSource={jobsQuery.data?.items ?? []} pagination={{ current: jobsQuery.data?.page ?? jobPage, pageSize: jobsQuery.data?.size ?? AUTHORING_BATCH_JOB_PAGE_SIZE, total: jobsQuery.data?.total ?? 0, showSizeChanger: false, onChange: (page) => setJobPage(page) }} />",
    "  </>;",
    "}",
    extra,
  ].join("\n");
}

function authoringBatchDrawerTestContent(extra = "") {
  return [
    "describe('AuthoringBatchDrawer', () => {",
    "  it('loads batch job records through server pagination', () => {});",
    "  it('loads rule package selector through small server-side pages', () => {});",
    "});",
    extra,
  ].join("\n");
}

function authoringBatchJobRepositoryContent(extra = "") {
  return [
    "interface AuthoringBatchJobRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<AuthoringBatchJob> pageByTenantId(String tenantId, int offset, int limit);",
    "  String sql = \"ORDER BY created_at DESC, id DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY\";",
    "}",
    extra,
  ].join("\n");
}

function authoringBatchJobServiceContent(extra = "") {
  return [
    "class AuthoringBatchJobService {",
    "  PageResponse<AuthoringBatchJobResponse> listRecent(PageRequest request) {",
    "    long total = jobs.countByTenantId(tenantId);",
    "    var rows = jobs.pageByTenantId(tenantId, page.offset(), page.safeSize());",
    "    return PageResponse.of(responses, page, total);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function authoringBatchJobControllerContent(extra = "") {
  return [
    "class AuthoringBatchJobController {",
    "  ApiResult<PageResponse<AuthoringBatchJobResponse>> listRecent(int page, int size) {",
    "    return ApiResult.ok(service.listRecent(new PageRequest(page, size, null)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function authoringBatchJobServiceTestContent(extra = "") {
  return [
    "class AuthoringBatchJobServiceTest {",
    "  void listRecentReturnsTenantScopedPageInsteadOfTop50Snapshot() {}",
    "}",
    extra,
  ].join("\n");
}

function authoringBatchJobControllerTestContent(extra = "") {
  return [
    "class AuthoringBatchJobControllerTest {",
    '  void recentEndpointReturnsServerPage() { jsonPath("$.data.items[0].jobId").value("abj-1"); }',
    "}",
    extra,
  ].join("\n");
}

function pathwayTemplatesContent(extra = "") {
  return [
    "const PATHWAY_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "const PATHWAY_ROLLBACK_TARGET_PAGE_SIZE = 20;",
    "const PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE = 20;",
    "function PathwayTemplates() {",
    "  const [packageSearch, setPackageSearch] = useState('');",
    "  const [outcomeIndicatorSearch, setOutcomeIndicatorSearch] = useState('');",
    "  const [outcomePackageSearch, setOutcomePackageSearch] = useState('');",
    "  const outcomePackageKeyword = outcomePackageSearch.trim();",
    "  const outcomeIndicatorKeyword = outcomeIndicatorSearch.trim();",
    '  const packagesData = usePackages({ page: 1, size: PATHWAY_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "PATHWAY" });',
    '  const rollbackTargetsData = usePathwayTemplates({ status: "OFFLINE", templateCode: detailData?.template.templateCode, page: 1, size: PATHWAY_ROLLBACK_TARGET_PAGE_SIZE });',
    '  const evaluationPackagesData = usePackages({ page: 1, size: PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE, assetType: "EVALUATION", keyword: outcomePackageKeyword });',
    '  const evaluationIndicatorsData = useEvaluationIndicators({ status: "ACTIVE", page: 1, size: PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE, indicatorCode: outcomeIndicatorKeyword });',
    "  const packageVersionFor = () => 'pkg-2026.06';",
    '  const createTemplatePackageVersion = selectedTemplatePackageVersion ?? "";',
    "  const requirePathwayPackageVersion = () => {",
    "    message.error('无法确认路径模板所属的配置包版本，暂不能创建或复制路径。');",
    "    message.error('无法确认当前路径模板所属的配置包版本，暂不能发布路径。');",
    "    message.error('无法确认当前路径模板所属的配置包版本，暂不能试运行路径。');",
    "  };",
    "  const outcomeIndicatorByCode = new Map();",
    "  const outcomeIndicatorPackageOptions = evaluationPackagesData.data?.items ?? [];",
    '  templateForm.setFieldValue(["outcomeBindings", field.name, "packageVersion"], indicator.packageVersion);',
    '  return <><Select showSearch filterOption={false} onSearch={setPackageSearch} placeholder="选择路径知识包" options={packagesData.data?.items ?? []} /><Select showSearch filterOption={false} onSearch={setOutcomeIndicatorSearch} options={evaluationIndicatorsData.data?.items ?? []} /><Select showSearch filterOption={false} onSearch={setOutcomePackageSearch} placeholder="选择评估指标所属配置包版本" options={outcomeIndicatorPackageOptions} /></>;',
    "}",
    extra,
  ].join("\n");
}

function pathwayTemplatesTestContent(extra = "") {
  return [
    "describe('PathwayTemplates', () => {",
    "  it('创建路径草稿缺少真实配置包版本时阻断提交', () => {});",
    "  it('路径模板配置包引用使用小页服务端搜索', () => {});",
    "  it('路径结局指标和回滚目标使用小页服务端查询，不能加载 100 条快照', () => {});",
    "  it('路径试运行缺少真实配置包版本时阻断', () => {});",
    "  it('路径发布缺少真实配置包版本时阻断灰度发布', () => {});",
    "  it('全量激活缺少真实配置包版本时阻断', () => {});",
    "});",
    extra,
  ].join("\n");
}

function adapterHubContent(extra = "") {
  return [
    "const ADAPTER_PAGE_SIZE = 20;",
    "const INTEGRATION_MAINTENANCE_PAGE_SIZE = 20;",
    "const INTEGRATION_CONTRACT_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "const TERMINOLOGY_MAPPING_REFERENCE_PAGE_SIZE = 20;",
    "function AdapterHub() {",
    "  const [adapterPage, setAdapterPage] = useState(1);",
    "  const [onboardingPage, setOnboardingPage] = useState(1);",
    "  const [webhookPage, setWebhookPage] = useState(1);",
    "  const [regionalSourcePage, setRegionalSourcePage] = useState(1);",
    "  const [contractPackageSearch, setContractPackageSearch] = useState('');",
    "  const [terminologyMappingSearch, setTerminologyMappingSearch] = useState('');",
    "  const onPackageSearch = setContractPackageSearch;",
    "  const adaptersQuery = useIntegrationAdapters({ page: adapterPage, size: ADAPTER_PAGE_SIZE });",
    "  const onboardingsQuery = useIntegrationOnboardings({ page: onboardingPage, size: INTEGRATION_MAINTENANCE_PAGE_SIZE });",
    "  const webhooksQuery = useWebhooks({ page: webhookPage, size: INTEGRATION_MAINTENANCE_PAGE_SIZE });",
    "  const regionalSourcesQuery = useRegionalSources({ page: regionalSourcePage, size: INTEGRATION_MAINTENANCE_PAGE_SIZE });",
    "  const adapters = adaptersQuery.data?.items ?? [];",
    "  const onboardings = onboardingsQuery.data?.items ?? [];",
    "  const webhooks = webhooksQuery.data?.items ?? [];",
    "  const regionalSources = regionalSourcesQuery.data?.items ?? [];",
    "  const packagesQuery = usePackages({ page: 1, size: INTEGRATION_CONTRACT_PACKAGE_REFERENCE_PAGE_SIZE, keyword: contractPackageSearch || undefined });",
    '  const terminologyMappingsQuery = useTerminologyMappings({ status: "CONFIRMED", page: 1, size: TERMINOLOGY_MAPPING_REFERENCE_PAGE_SIZE, keyword: terminologyMappingSearch || undefined });',
    "  return <><Table dataSource={adapters} pagination={{ current: adapterPage, pageSize: ADAPTER_PAGE_SIZE, total: adaptersQuery.data?.total ?? 0, onChange: setAdapterPage }} /><Table dataSource={onboardings} pagination={{ current: onboardingPage, pageSize: INTEGRATION_MAINTENANCE_PAGE_SIZE, total: onboardingsQuery.data?.total ?? 0, onChange: setOnboardingPage }} /><Table dataSource={webhooks} pagination={{ current: webhookPage, pageSize: INTEGRATION_MAINTENANCE_PAGE_SIZE, total: webhooksQuery.data?.total ?? 0, onChange: setWebhookPage }} /><Table dataSource={regionalSources} pagination={{ current: regionalSourcePage, pageSize: INTEGRATION_MAINTENANCE_PAGE_SIZE, total: regionalSourcesQuery.data?.total ?? 0, onChange: setRegionalSourcePage }} /><Select showSearch filterOption={false} onSearch={onPackageSearch} placeholder=\"选择已存在配置包版本\" options={packagesQuery.data?.items ?? []} /><Select showSearch filterOption={false} onSearch={setTerminologyMappingSearch} onClear={() => setTerminologyMappingSearch(\"\")} placeholder=\"可选，选择已确认映射\" options={terminologyMappingsQuery.data?.items ?? []} /></>;",
    "}",
    extra,
  ].join("\n");
}

function adapterHubTestContent(extra = "") {
  return [
    "describe('AdapterHub', () => {",
    "  it('renders the unified adapter workspace without the old launch-token console', () => { expect(useIntegrationAdapters).toHaveBeenCalledWith({ page: 1, size: 20 }); });",
    "  it('loads adapter hub maintenance ledgers through small server-side pages', () => { expect(useIntegrationOnboardings).toHaveBeenCalledWith({ page: 1, size: 20 }); expect(useWebhooks).toHaveBeenCalledWith({ page: 1, size: 20 }); expect(useRegionalSources).toHaveBeenCalledWith({ page: 1, size: 20 }); });",
    "  it('loads data contract package selector through small server-side pages', () => {});",
    "  it('loads terminology mapping selector through small server-side pages', () => {});",
    "});",
    extra,
  ].join("\n");
}

function ruleDefinitionsContent(extra = "") {
  return [
    "const RULE_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "const RULE_FRAGMENT_LIBRARY_PAGE_SIZE = 20;",
    "const CONDITION_FRAGMENT_IMPACT_PAGE_SIZE = 20;",
    "function RuleDefinitions() {",
    "  const [rulePackageSearch, setRulePackageSearch] = useState('');",
    "  const [fragmentLibraryPage, setFragmentLibraryPage] = useState(1);",
    "  const [fragmentLibrarySearch, setFragmentLibrarySearch] = useState('');",
    "  const [impactPage, setImpactPage] = useState(1);",
    "  const impactFragmentId = 'frag-1';",
    "  const fragmentLibraryKeyword = fragmentLibrarySearch.trim();",
    '  const rulePackagesQuery = usePackages({ page: 1, size: RULE_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "RULE", keyword: rulePackageSearch || undefined });',
    '  const fragmentLibraryQuery = useConditionFragments({ packageVersion: currentCreatePackageVersion || undefined, ...(fragmentLibraryKeyword ? { keyword: fragmentLibraryKeyword } : {}), page: fragmentLibraryPage, size: RULE_FRAGMENT_LIBRARY_PAGE_SIZE, sort: "fragmentCode,asc" });',
    "  const conditionFragmentImpactQuery = useConditionFragmentImpact(impactFragmentId, { page: impactPage, size: CONDITION_FRAGMENT_IMPACT_PAGE_SIZE });",
    "  const fragmentLibraryItems = fragmentLibraryQuery.data?.items ?? [];",
    '  return <><AutoComplete filterOption={false} onSearch={setRulePackageSearch} placeholder="选择当前已审核的标准上下文包版本" options={rulePackagesQuery.data?.items ?? []} /><AutoComplete filterOption={false} onSearch={setRulePackageSearch} placeholder="选择规则配置包版本" options={rulePackagesQuery.data?.items ?? []} /><Input aria-label="检索条件片段" allowClear value={fragmentLibrarySearch} onChange={(event) => { setFragmentLibrarySearch(event.target.value); setFragmentLibraryPage(1); }} /><Table pagination={{ current: fragmentLibraryQuery.data?.page ?? fragmentLibraryPage, pageSize: fragmentLibraryQuery.data?.size ?? RULE_FRAGMENT_LIBRARY_PAGE_SIZE, total: fragmentLibraryQuery.data?.total ?? fragmentLibraryItems.length, showSizeChanger: false, onChange: (page) => setFragmentLibraryPage(page) }} /><Table dataSource={conditionFragmentImpactQuery.data?.affectedAssets.items ?? []} pagination={{ current: conditionFragmentImpactQuery.data?.affectedAssets.page ?? impactPage, pageSize: conditionFragmentImpactQuery.data?.affectedAssets.size ?? CONDITION_FRAGMENT_IMPACT_PAGE_SIZE, total: conditionFragmentImpactQuery.data?.affectedAssets.total ?? 0, showSizeChanger: false, onChange: (page) => setImpactPage(page) }} /></>;',
    "}",
    extra,
  ].join("\n");
}

function ruleDefinitionsTestContent(extra = "") {
  return [
    "describe('RuleDefinitions', () => {",
    "  it('规则包版本选择器通过小页服务端搜索加载', () => {});",
    "  it('条件片段库通过小页服务端搜索加载', () => {});",
    "  it('条件片段影响分析通过小页服务端分页加载受影响资产', () => {});",
    "});",
    extra,
  ].join("\n");
}

function ruleDefinitionRepositoryContent(extra = "") {
  return [
    "interface RuleDefinitionRepository {",
    "  long countEffectiveByFilter(String tenantId, String platformTenantId, String tenantStatus, String platformStatus, String ruleType, String riskLevel, String keyword);",
    "  List<RuleDefinition> pageEffectiveByFilter(String tenantId, String platformTenantId, String tenantStatus, String platformStatus, String ruleType, String riskLevel, String keyword, int offset, int limit);",
    "  List<RuleDefinition> pageActiveRuleImpactsByFragmentPattern(String tenantId, String fragmentPattern, int offset, int limit);",
    "  long countActiveRuleImpactsByFragmentPattern(String tenantId, String fragmentPattern);",
    "}",
    extra,
  ].join("\n");
}

function ruleEngineServiceContent(extra = "") {
  return [
    "class RuleEngineService {",
    "  PageResponse<RuleDefinition> list(RuleFilter filter, PageRequest page) {",
    "    definitions.countEffectiveByFilter(tenantId, PlatformTenant.ID, status, platformStatus, type, risk, keyword);",
    "    definitions.pageEffectiveByFilter(tenantId, PlatformTenant.ID, status, platformStatus, type, risk, keyword, page.offset(), page.safeSize());",
    "    return PageResponse.empty(page);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function ruleEngineServiceTestContent(extra = "") {
  return [
    "class RuleEngineServiceTest {",
    "  void listUsesEffectiveRepositoryPagingForCustomerTenantWithoutLoadingSnapshots() { verify(definitions, never()).listByFilter(any(), any(), any(), any(), any()); }",
    "}",
    extra,
  ].join("\n");
}

function ruleRepositoryTestContent(extra = "") {
  return [
    "class RuleRepositoryTest {",
    "  void pagesEffectiveRulesWithoutMaterializingTenantAndPlatformSnapshots() {}",
    "}",
    extra,
  ].join("\n");
}

function terminologyMappingContent(extra = "") {
  return [
    "const TERMINOLOGY_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "function TerminologyMapping() {",
    '  useStandardTerms({ page: 1, size: 20, status: "ACTIVE" });',
    '  useLocalTerms({ page: 1, size: 20, status: "UNMAPPED" });',
    '  useTerminologyCandidates({ page: 1, size: 20, status: "PENDING" });',
    '  useTerminologyConflicts({ page: 1, size: 10, status: "OPEN" });',
    "  const [packageSearch, setPackageSearch] = useState('');",
    "  const [selectedPackageId, setSelectedPackageId] = useState();",
    '  const packages = usePackages({ page: 1, size: TERMINOLOGY_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "TERMINOLOGY", keyword: packageSearch || undefined });',
    "  const packageItems = packages.data?.items ?? [];",
    "  const selectedPackage = packageItems.find((item) => item.packageId === selectedPackageId) ?? packageItems[0];",
    '  return <Select aria-label="选择映射包" showSearch filterOption={false} onSearch={setPackageSearch} onChange={setSelectedPackageId} options={packageItems} />;',
    "}",
    extra,
  ].join("\n");
}

function terminologyMappingTestContent(extra = "") {
  return [
    "describe('TerminologyMapping', () => {",
    "  it('loads terminology release packages through small server-side search pages and publishes the selected package', () => {});",
    "  it('loads terminology reference queues through one-based pages', () => {",
    "    expect(useStandardTerms).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }));",
    "    expect(useTerminologyConflicts).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 10, status: \"OPEN\" }));",
    "  });",
    "});",
    extra,
  ].join("\n");
}

function releaseGovernanceContent(extra = "") {
  return [
    "const OVERRIDE_TEMPLATE_PAGE_SIZE = 20;",
    "function ReleaseGovernance() {",
    "  const [templatePage, setTemplatePage] = useState(1);",
    "  const templatesQuery = useOverrideTemplates({",
    "    page: templatePage,",
    "    size: OVERRIDE_TEMPLATE_PAGE_SIZE,",
    "  });",
    "  const templateItems = templatesQuery.data?.items ?? [];",
    "  const templateOptions = templateItems.map((template) => ({ value: template.templateId, label: template.templateName }));",
    "  return <Table dataSource={templateItems} pagination={{ current: templatesQuery.data?.page ?? templatePage, pageSize: templatesQuery.data?.size ?? OVERRIDE_TEMPLATE_PAGE_SIZE, total: templatesQuery.data?.total ?? 0, onChange: setTemplatePage }} />;",
    "}",
    extra,
  ].join("\n");
}

function releaseGovernanceTestContent(extra = "") {
  return [
    "describe('ReleaseGovernance', () => {",
    "  it('loads override templates through bounded server pagination', () => { expect(useOverrideTemplatesMock).toHaveBeenLastCalledWith({ page: 1, size: 20 }); });",
    "});",
    extra,
  ].join("\n");
}

function configPackagesContent(extra = "") {
  return [
    "const PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE = 20;",
    "const PACKAGE_RELEASE_ADAPTER_PAGE_SIZE = 20;",
    "const PACKAGE_SYNC_LOG_PAGE_SIZE = 20;",
    "function ConfigPackages() {",
    "  const [packageItemAssetSearch, setPackageItemAssetSearch] = useState('');",
    "  const [releaseAdapterPage, setReleaseAdapterPage] = useState(1);",
    "  const [syncLogPage, setSyncLogPage] = useState(1);",
    "  const packageItemAssetKeyword = packageItemAssetSearch.trim();",
    "  useAuthoringAssets({ page: 1, size: PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE, keyword: packageItemAssetKeyword });",
    "  useEvaluationIndicators({ status: 'ACTIVE', page: 1, size: PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE, indicatorCode: packageItemAssetKeyword });",
    '  usePackages({ page: 1, size: PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE, assetType: "TERMINOLOGY", keyword: packageItemAssetKeyword });',
    '  const { data: persistedSyncLogs } = usePackageSyncLogs(effectivePackageId || "", {',
    "    page: syncLogPage,",
    "    size: PACKAGE_SYNC_LOG_PAGE_SIZE,",
    "  });",
    "  const { data: releaseAdapters } = usePackageReleaseAdapters({",
    "    page: releaseAdapterPage,",
    "    size: PACKAGE_RELEASE_ADAPTER_PAGE_SIZE,",
    "  });",
    "  const displayAdapters = releaseAdapters?.items ?? [];",
    "  const persistedSyncLogItems = persistedSyncLogs?.items ?? [];",
    "  const visibleSyncLogs = syncLogs.length > 0 ? syncLogs : persistedSyncLogItems;",
    "  const requireSelectedPackageVersion = () => {",
    "    message.error('当前配置包缺少版本，暂不能添加资产条目。');",
    "    message.error('当前配置包缺少版本，暂不能同步发布。');",
    "    return selectedPackage.packageVersion;",
    "  };",
    "  const handleApplyPilotTemplateReferences = () => {",
    "    const packageVersion = template.defaultPackageVersion.trim();",
    "    message.error('首发模板缺少默认配置包版本，暂不能应用平台引用。');",
    "    applyPilotTemplateReferences({ packageVersion });",
    "  };",
    "  const handleAddItem = () => addPackageItem({ packageVersion: requireSelectedPackageVersion() });",
    "  const handleSyncPackage = () => releasePackage({ packageVersion: requireSelectedPackageVersion() });",
    "  return <>",
    "    <Select showSearch filterOption={false} onSearch={setPackageItemAssetSearch} />",
    "    <Pagination current={releaseAdapters?.page ?? releaseAdapterPage} pageSize={releaseAdapters?.size ?? PACKAGE_RELEASE_ADAPTER_PAGE_SIZE} total={releaseAdapters?.total ?? 0} onChange={setReleaseAdapterPage} />",
    "    <Pagination current={persistedSyncLogs?.page ?? syncLogPage} pageSize={persistedSyncLogs?.size ?? PACKAGE_SYNC_LOG_PAGE_SIZE} total={persistedSyncLogs?.total ?? 0} onChange={setSyncLogPage} />",
    "  </>;",
    "}",
    extra,
  ].join("\n");
}

function configPackagesTestContent(extra = "") {
  return [
    "describe('ConfigPackages', () => {",
    "  it('blocks first-run template references when the template lacks a default package version', () => {});",
    "  it('blocks release when the selected package version is missing', () => {});",
    "  it('blocks package item creation when the selected package version is missing', () => {});",
    "  it('loads package sync evidence logs through server pagination', () => {});",
    "  it('loads package release adapters through server pagination', () => {});",
    "});",
    extra,
  ].join("\n");
}

function patientPathwaysContent(extra = "") {
  return [
    "const PATHWAY_REFERENCE_PAGE_SIZE = 20;",
    "function searchKeyword(value) { return value.trim() || undefined; }",
    "function PatientPathways() {",
    "  const [enterTemplateSearch, setEnterTemplateSearch] = useState('');",
    "  usePathwayTemplates({ status: 'PUBLISHED', keyword: searchKeyword(enterTemplateSearch), page: 1, size: PATHWAY_REFERENCE_PAGE_SIZE });",
    "  usePackages({ page: 1, size: PATHWAY_REFERENCE_PAGE_SIZE, assetType: 'PATHWAY', status: 'PUBLISHED', keyword: templateDetail?.template.packageId || undefined });",
    "  const requireSelectedTemplatePackageVersion = () => {",
    "    message.error('无法确认当前路径模板所属的配置包版本，暂不能推进路径。');",
    "  };",
    "  return <Select showSearch filterOption={false} onSearch={setEnterTemplateSearch} />;",
    "}",
    extra,
  ].join("\n");
}

function patientPathwaysTestContent(extra = "") {
  return [
    "describe('PatientPathways', () => {",
    "  it('loads published pathway references through small server-side search pages', () => {});",
    "  it('blocks pathway advancement when the template package version cannot be resolved', () => {});",
    "});",
    extra,
  ].join("\n");
}

function diagnosisPanelContent(extra = "") {
  return [
    "const DIAGNOSIS_REFERENCE_PAGE_SIZE = 20;",
    "const DIAGNOSIS_VERSION_PAGE_SIZE = 20;",
    "function searchKeyword(value) { return value.trim() || undefined; }",
    "function DiagnosisKnowledgePanel() {",
    "  const [identitySearch, setIdentitySearch] = useState('');",
    "  const [diagnosisReferenceSearch, setDiagnosisReferenceSearch] = useState('');",
    "  const [referenceKnowledgeSearch, setReferenceKnowledgeSearch] = useState('');",
    "  const [ruleSearch, setRuleSearch] = useState('');",
    "  const [pathwaySearch, setPathwaySearch] = useState('');",
    "  const [versionPage, setVersionPage] = useState(1);",
    "  useKnowledgeIdentities({ domain: 'DIAGNOSIS', keyword: searchKeyword(identitySearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  useKnowledgeIdentities({ domain: 'DIAGNOSIS', keyword: searchKeyword(diagnosisReferenceSearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  useKnowledgeIdentities({ keyword: searchKeyword(referenceKnowledgeSearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  useRuleDefinitions({ keyword: searchKeyword(ruleSearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  usePathwayTemplates({ keyword: searchKeyword(pathwaySearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  const versionsQuery = useKnowledgeVersions(identityId, { page: versionPage, size: DIAGNOSIS_VERSION_PAGE_SIZE });",
    "  const versions = versionsQuery.data?.items ?? [];",
    "  return <>",
    "    <Select showSearch filterOption={false} onSearch={setIdentitySearch} />",
    "    <Select showSearch filterOption={false} onSearch={setDiagnosisReferenceSearch} />",
    "    <Select showSearch filterOption={false} onSearch={searchCareTarget} />",
    "    <Pagination current={versionsQuery.data?.page ?? versionPage} pageSize={versionsQuery.data?.size ?? DIAGNOSIS_VERSION_PAGE_SIZE} total={versionsQuery.data?.total ?? 0} onChange={setVersionPage} />",
    "  </>;",
    "}",
    extra,
  ].join("\n");
}

function diagnosisPanelTestContent(extra = "") {
  return [
    "describe('DiagnosisKnowledgePanel', () => {",
    "  it('loads diagnosis reference selectors through small server-side search pages', () => {});",
    "  it('loads diagnosis versions through server pagination', () => {});",
    "});",
    extra,
  ].join("\n");
}

function terminologyServiceContent() {
  return [
    "class TerminologyService {",
    "  void generateCandidates() {",
    "    TerminologyCandidateGenerationJob job;",
    "    TerminologyCandidateGenerationJobStatus status = TerminologyCandidateGenerationJobStatus.PENDING;",
    "    dispatchCandidateGenerationAfterCommit(null, null);",
    "    TransactionSynchronizationManager.registerSynchronization(null);",
    "    terminologyCandidateGenerationExecutor.execute(null);",
    "    executeCandidateGenerationJob(null);",
    "    generateCandidateRowsForJob(null, null, null, null, null, null, null);",
    "    String candidatePageUri = null;",
    "    localTermRepository.pageByTenantIdAndSourceSystemAndStatus();",
    "    standardTermRepository.pageByTenantIdsAndStatus();",
    "    StandardTermGenerationIndex index = new StandardTermGenerationIndex();",
    "    index.candidatesFor(null, true, java.util.List.of());",
    "  }",
    "}",
  ].join("\n");
}

function terminologyControllerContent() {
  return [
    "class TerminologyController {",
    '  @GetMapping("/mappings/candidates")',
    "  Object candidates(String generationJobCode) { return null; }",
    "  ApiResult<TerminologyCandidateGenerationJob> generateCandidates() { return null; }",
    '  @GetMapping("/mappings/candidate-generation-jobs/{jobCode}")',
    "  Object candidateGenerationJob(String jobCode) { return service.getCandidateGenerationJob(jobCode); }",
    "}",
  ].join("\n");
}

function terminologyServiceTestContent() {
  return [
    "class TerminologyServiceTest {",
    "  void generateCandidatesSubmitsAsyncJobAndDoesNotReturnCandidateRows() {}",
    "  void generateCandidatesDefersWorkerDispatchUntilCommit() {}",
    "  void executeCandidateGenerationJobMarksSucceededAndLinksPagedCandidates() {",
    "    String generationJobCode = null;",
    "  }",
    "}",
  ].join("\n");
}

function terminologyApiContractContent() {
  return [
    "class TerminologyApiContractTest {",
    "  void generateCandidatesReturnsAsyncJobInsteadOfCandidateRows() {}",
    "  void candidateGenerationJobStatusUsesDedicatedApi04Route() {",
    '    String candidatePageUri = "/api/v1/engine/terminology/mappings/candidates?generationJobCode=term-job-1";',
    '    String generationJobCode = "term-job-1";',
    "  }",
    "}",
  ].join("\n");
}

function terminologyCandidateGenerationJobContent() {
  return [
    '@Table("mk_term_candidate_generation_job")',
    "record TerminologyCandidateGenerationJob(",
    "  String generation_job_code,",
    "  String candidatePageUri",
    ") {",
    "  // 避免同步响应返回大批量明细",
    "}",
  ].join("\n");
}

function terminologyCandidateGenerationMigrationContent() {
  return [
    "CREATE TABLE mk_term_candidate_generation_job (",
    "  candidate_page_uri VARCHAR(512)",
    ");",
    "ALTER TABLE mapping_candidate ADD COLUMN generation_job_code VARCHAR(64);",
    "CREATE INDEX idx_mk_term_candidate_generation_job_tenant ON mk_term_candidate_generation_job (tenant_id, created_at);",
    "CREATE INDEX idx_mapping_candidate_generation_job ON mapping_candidate (tenant_id, generation_job_code, status);",
  ].join("\n");
}

function terminologyLargeScaleContent() {
  return [
    "class TerminologyRepositoryLargeScaleTest {",
    "  void candidateAndConflictRepositoriesHandleHundredThousandRowsWithinLocalBudget() {",
    "    seedMappingCandidates(100_000);",
    "    seedMappingConflicts(100_000);",
    "    service.pageCandidates(null, null);",
    "    service.pageConflicts(null, null);",
    "    assertThat(candidatePage.total()).isEqualTo(100_000L);",
    "    assertThat(conflictPage.total()).isEqualTo(100_000L);",
    "  }",
    "}",
  ].join("\n");
}

function contextFactBridgeContent() {
  return [
    "class ContextFactBridge {",
    "  void conditionContext(ContextSnapshotResources resources) {}",
    "  void facts() {",
    '    String canonical = "observations";',
    '    String dotted = "observation.";',
    "  }",
    "}",
  ].join("\n");
}

function pathwayEngineServiceContent() {
  return [
    "class PathwayEngineService {",
    "  void criteriaContext() { ContextFactBridge.conditionContext(null, null); }",
    "  void contextFacts() { ContextFactBridge.facts(null); }",
    "  PageResponse<PathwayTemplate> listTemplates(PathwayTemplateFilter filter, PageRequest page) {",
    "    templates.countEffectiveByFilter(tenantId, PlatformTenant.ID, status, platformStatus, diseaseCode, packageId, templateCode, keyword);",
    "    templates.pageEffectiveByFilter(tenantId, PlatformTenant.ID, status, platformStatus, diseaseCode, packageId, templateCode, keyword, page.offset(), page.safeSize());",
    "    return PageResponse.empty(page);",
    "  }",
    "}",
  ].join("\n");
}

function pathwayEngineServiceTestContent() {
  return [
    "class PathwayEngineServiceTest {",
    '  String fact = "observations[].valueNumeric";',
    "  void enterPatientPathwayUsesCanonicalObservationPathForEntryIncludeCriteria() {}",
    "  void enterPatientPathwayUsesCanonicalObservationPathForEntryExcludeCriteria() {}",
    "  void exitAllowsCanonicalObservationPathForExitIncludeCriteria() {}",
    "  void exitRejectsCanonicalObservationPathForExitExcludeCriteria() {}",
    "  void listTemplatesUsesEffectiveRepositoryPagingForCustomerTenantWithoutLoadingSnapshots() { verify(templates, never()).listByFilter(any(), any(), any(), any(), any(), any()); }",
    "}",
  ].join("\n");
}

function pathwayTemplateRepositoryContent(extra = "") {
  return [
    "interface PathwayTemplateRepository {",
    "  long countEffectiveByFilter(String tenantId, String platformTenantId, String tenantStatus, String platformStatus, String diseaseCode, String packageId, String templateCode, String keyword);",
    "  List<PathwayTemplate> pageEffectiveByFilter(String tenantId, String platformTenantId, String tenantStatus, String platformStatus, String diseaseCode, String packageId, String templateCode, String keyword, int offset, int limit);",
    "  List<PathwayTemplate> pageTemplateImpactsByFragmentPattern(String tenantId, String fragmentPattern, int offset, int limit);",
    "  long countTemplateImpactsByFragmentPattern(String tenantId, String fragmentPattern);",
    "}",
    extra,
  ].join("\n");
}

function diagnosisKnowledgeServiceContent() {
  return [
    "class DiagnosisKnowledgeService {",
    "  void publishGate() {",
    "    rejectUnsupportedConstraintCriteria(versionCriteria);",
    "    Set<String> findings = parseFindings(raw);",
    "  }",
    "  void addCriterion() {",
    "    references.validateCriterion(version, findingTermCode, citationId);",
    "  }",
    "  void rejectUnsupportedConstraintCriteria(List<DiagnosisCriterion> versionCriteria) {",
    "    if (hasText(criterion.valueConstraint()) || hasText(criterion.temporalConstraint())) {",
    '      throw new ApiException(ErrorCode.ENG_DX_006, "暂不可发布");',
    "    }",
    "  }",
    "  Set<String> parseFindings(String raw) {",
    "    LinkedHashSet<String> findings = new LinkedHashSet<>();",
    "    findings.add(normalized);",
    "    return Set.copyOf(findings);",
    "  }",
    "}",
  ].join("\n");
}

function diagnosisReferenceValidatorContent() {
  return [
    "class DiagnosisReferenceValidator {",
    '  static final List<String> FINDING_DICTIONARIES = List.of("TERM.DIAGNOSIS", "TERM.LAB", "TERM.DRUG", "TERM.PROCEDURE");',
    "  void validateCriterion(KnowledgeAssetVersion diagnosisVersion, String findingTermCode, Long citationId) {",
    "    validateFindingTerm(tenantId, findingTermCode);",
    "    validateCitation(tenantId, diagnosisVersion, citationId);",
    "  }",
    "  void validateFindingTerm(String tenantId, String findingTermCode) {",
    "    standardTerms.findFirstActiveByTenantIdsAndStandardSystemAndTermCode(null, tenantId, dictionary, findingTermCode);",
    "  }",
    "  void validateCitation(String tenantId, KnowledgeAssetVersion diagnosisVersion, Long citationId) {",
    "    citations.findByTenantIdAndId(tenantId, citationId);",
    "    citation.assetVersionId();",
    "  }",
    "}",
  ].join("\n");
}

function diagnosisKnowledgeServiceTestContent() {
  return [
    "class DiagnosisKnowledgeServiceTest {",
    "  void addCriterionRejectsInvalidFindingOrCitationBeforePersisting() {}",
    "  void publishGateRejectsCriteriaWithUnevaluatedValueOrTemporalConstraint() {",
    '    String valueConstraint = "{\\"operator\\":\\"lt\\"}";',
    '    String temporalConstraint = "{\\"operator\\":\\"gte\\"}";',
    '    String message = "暂不可发布";',
    "  }",
    "  void publishGateParsesThirdPartyFindingListsWithoutDuplicateOrBlankNoise() {",
    '    String caseCode = "CASE-THIRD-PARTY";',
    "  }",
    "}",
  ].join("\n");
}

function diagnosisReferenceValidatorTestContent() {
  return [
    "class DiagnosisReferenceValidatorTest {",
    "  void criterionRequiresActiveStandardFindingTermFromRuntimeDictionaries() {",
    '    String dictionary = "TERM.LAB";',
    '    String finding = "LOINC-EGFR";',
    "  }",
    "  void criterionCitationMustBelongToCurrentDiagnosisVersion() {",
    '    String citation = "findByTenantIdAndId";',
    "  }",
    "}",
  ].join("\n");
}

function citationRepositoryContent() {
  return [
    "interface CitationRepository {",
    "  Optional<Citation> findByTenantIdAndId(String tenantId, Long id);",
    "}",
  ].join("\n");
}

function knowledgePackageRepositoryContent(extra = "") {
  return [
    "interface KnowledgePackageRepository {",
    "  List<KnowledgePackage> findByTenantIdAndPackageCodeAndStatus(String tenantId, String packageCode, KnowledgePackageStatus status);",
    "  long countReleasedByTenantId(String tenantId);",
    '  String sql = "LOWER(kp.package_id) LIKE :keyword OR LOWER(kp.package_code) LIKE :keyword";',
    "}",
    extra,
  ].join("\n");
}

function releasePlanRepositoryContent(extra = "") {
  return [
    "interface ReleasePlanRepository {",
    "  long countByTenantIdAndStrategyAndStatus(String tenantId, ReleaseStrategy strategy, ReleasePlanStatus status);",
    "}",
    extra,
  ].join("\n");
}

function tenantPackageReferenceRepositoryContent(extra = "") {
  return [
    "interface TenantPackageReferenceRepository {",
    "  long countByTenantIdAndStatus(String tenantId, TenantPackageReferenceStatus status);",
    "}",
    extra,
  ].join("\n");
}

function orgUnitRepositoryContent(extra = "") {
  return [
    "interface OrgUnitRepository {",
    "  long countByTenantIdAndLevelAndStatus(String tenantId, OrgLevel level, OrgUnitStatus status);",
    "}",
    extra,
  ].join("\n");
}

function platformCredentialRepositoryContent(extra = "") {
  return [
    "interface PlatformCredentialRepository {",
    "  long countByTenantIdAndStatus(String tenantId, String status);",
    "}",
    extra,
  ].join("\n");
}

function userRoleAssignmentRepositoryContent(extra = "") {
  return [
    "interface UserRoleAssignmentRepository {",
    "  boolean existsByTenantIdAndActiveFlag(String tenantId, String activeFlag);",
    "}",
    extra,
  ].join("\n");
}

function integrationAdapterRepositoryContent(extra = "") {
  return [
    "interface IntegrationAdapterRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<IntegrationAdapter> pageByTenantId(String tenantId, int offset, int limit);",
    "  long countByTenantIdAndStatus(String tenantId, String status);",
    "  List<IntegrationAdapter> pageByTenantIdAndStatus(String tenantId, String status, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function integrationOnboardingRepositoryContent(extra = "") {
  return [
    "interface IntegrationOnboardingRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<IntegrationOnboarding> pageByTenantId(String tenantId, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function integrationWebhookConfigRepositoryContent(extra = "") {
  return [
    "interface IntegrationWebhookConfigRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<IntegrationWebhookConfig> pageByTenantId(String tenantId, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function regionalSourceRepositoryContent(extra = "") {
  return [
    "interface RegionalSourceRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<RegionalSource> pageByTenantId(String tenantId, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function integrationServiceContent(extra = "") {
  return [
    "class IntegrationService {",
    "  PageResponse<IntegrationAdapter> getAdapters(String tenantId, PageRequest pageReq) {",
    "    adapterRepository.countByTenantId(tenantId);",
    "    adapterRepository.pageByTenantId(tenantId, req.offset(), req.safeSize());",
    "  }",
    "  PageResponse<IntegrationOnboardingResponse> listIntegrationOnboardings(String tenantId, PageRequest pageReq) {",
    "    onboardingRepository.countByTenantId(tenantId);",
    "    onboardingRepository.pageByTenantId(tenantId, pageReq.offset(), pageReq.safeSize());",
    "  }",
    "  PageResponse<WebhookConfigResponse> getWebhooks(String tenantId, PageRequest pageReq) {",
    "    webhookRepository.countByTenantId(tenantId);",
    "    webhookRepository.pageByTenantId(tenantId, pageReq.offset(), pageReq.safeSize());",
    "  }",
    "  PageResponse<RegionalSourceResponse> listRegionalSources(String tenantId, PageRequest pageReq) {",
    "    regionalSourceRepository.countByTenantId(tenantId);",
    "    regionalSourceRepository.pageByTenantId(tenantId, pageReq.offset(), pageReq.safeSize());",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function integrationControllerContent(extra = "") {
  return [
    "class IntegrationController {",
    "  ApiResult<PageResponse<IntegrationAdapter>> getAdapters(int page, int size, String sort) {",
    "    return ApiResult.ok(integrationService.getAdapters(tenantId, new PageRequest(page, size, sort)));",
    "  }",
    "  ApiResult<PageResponse<IntegrationOnboardingResponse>> listIntegrationOnboardings(int page, int size, String sort) {",
    "    return ApiResult.ok(integrationService.listIntegrationOnboardings(tenantId, new PageRequest(page, size, sort)));",
    "  }",
    "  ApiResult<PageResponse<WebhookConfigResponse>> getWebhooks(int page, int size, String sort) {",
    "    return ApiResult.ok(integrationService.getWebhooks(tenantId, new PageRequest(page, size, sort)));",
    "  }",
    "  ApiResult<PageResponse<RegionalSourceResponse>> listRegionalSources(int page, int size, String sort) {",
    "    return ApiResult.ok(integrationService.listRegionalSources(tenantId, new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function integrationServiceTestContent(extra = "") {
  return [
    "class IntegrationServiceTest {",
    "  void testAdapterLifecycle() { PageResponse<IntegrationAdapter> page = service.getAdapters(tenantId, PageRequest.defaults()); }",
    "  void adapterHubMaintenanceListsUseTenantScopedPagesInsteadOfArraySnapshots() {}",
    "}",
    extra,
  ].join("\n");
}

function integrationControllerSecurityTestContent(extra = "") {
  return [
    "class IntegrationControllerSecurityTest {",
    "  void adapterHubMaintenanceListsReturnPagedContractsForTenantOperators() {}",
    "}",
    extra,
  ].join("\n");
}

function overrideTemplateRepositoryContent(extra = "") {
  return [
    "interface OverrideTemplateRepository {",
    "  long countByTenantIdAndStatus(String tenantId, OverrideTemplateStatus status);",
    "  List<OverrideTemplate> pageByTenantIdAndStatus(String tenantId, OverrideTemplateStatus status, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function overrideTemplateServiceContent(extra = "") {
  return [
    "class OverrideTemplateService {",
    "  PageResponse<OverrideTemplate> listTemplates(String tenantId, PageRequest pageRequest) {",
    "    templates.countByTenantIdAndStatus(tenantId, OverrideTemplateStatus.ACTIVE);",
    "    templates.pageByTenantIdAndStatus(tenantId, OverrideTemplateStatus.ACTIVE, pageRequest.offset(), pageRequest.safeSize());",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function releaseGovernanceControllerContent(extra = "") {
  return [
    "class ReleaseGovernanceController {",
    "  ApiResult<PageResponse<OverrideTemplate>> listTemplates(int page, int size, String sort) {",
    "    return ApiResult.ok(overrideTemplates.listTemplates(tenantId(), new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function overrideTemplateServiceTestContent(extra = "") {
  return [
    "class OverrideTemplateServiceTest {",
    "  void listTemplatesReturnsTenantScopedPageInsteadOfArraySnapshot() {}",
    "}",
    extra,
  ].join("\n");
}

function releaseGovernanceControllerTestContent(extra = "") {
  return [
    "class ReleaseGovernanceControllerTest {",
    "  void listsOverrideTemplatesAsPagedTenantScopedContract() {}",
    "}",
    extra,
  ].join("\n");
}

function syncLogRepositoryContent(extra = "") {
  return [
    "interface SyncLogRepository {",
    "  long countByTenantIdAndPackageId(String tenantId, String packageId);",
    "  List<SyncLog> pageByTenantIdAndPackageId(String tenantId, String packageId, int offset, int limit);",
    '  String sql = "JOIN release_plan rp ON rp.plan_id = sl.plan_id AND rp.tenant_id = sl.tenant_id OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";',
    "}",
    extra,
  ].join("\n");
}

function packageEngineServiceContent(extra = "") {
  return [
    "class PackageEngineService {",
    "  void getAssetReadiness() {",
    "    packageRepository.countByFilter(tenantId, null, KnowledgePackageStatus.DRAFT.name(), null);",
    "    packageRepository.countByFilter(tenantId, null, KnowledgePackageStatus.PUBLISHED.name(), null);",
    "    packageRepository.countByFilter(tenantId, null, KnowledgePackageStatus.ACTIVE.name(), null);",
    "    packageRepository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, KnowledgePackageStatus.ACTIVE);",
    "    planRepository.countByTenantIdAndStrategyAndStatus(tenantId, ReleaseStrategy.GRAYSCALE, ReleasePlanStatus.SUCCESS);",
    "  }",
    "  void syncPackage(KnowledgePackage pack) {",
    "    packageRepository.findByTenantIdAndPackageCodeAndStatus(tenantId, pack.packageCode(), KnowledgePackageStatus.ACTIVE);",
    "  }",
    "  PageResponse<SyncLogResponse> listSyncLogs(String packageId, PageRequest page) {",
    "    long total = logRepository.countByTenantIdAndPackageId(tenantId, packageId);",
    "    return PageResponse.of(logRepository.pageByTenantIdAndPackageId(tenantId, packageId, page.offset(), page.safeSize()), page, total);",
    "  }",
    "  PageResponse<PackageReleaseAdapterResponse> listReleaseAdapters(PageRequest page) {",
    "    long total = adapterRepository.countByTenantIdAndStatus(tenantId, \"ACTIVE\");",
    "    return PageResponse.of(adapterRepository.pageByTenantIdAndStatus(tenantId, \"ACTIVE\", page.offset(), page.safeSize()), page, total);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function packageEngineControllerContent(extra = "") {
  return [
    "class PackageEngineController {",
    "  ApiResult<PageResponse<SyncLogResponse>> listSyncLogs(String packageId, Integer page, Integer size) {",
    "    return ApiResult.ok(service.listSyncLogs(packageId, new PageRequest(page, size, null)));",
    "  }",
    "  ApiResult<PageResponse<PackageReleaseAdapterResponse>> listReleaseAdapters(Integer page, Integer size) {",
    "    return ApiResult.ok(service.listReleaseAdapters(new PageRequest(page, size, null)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function packageEngineServiceTestContent(extra = "") {
  return [
    "class PackageEngineServiceTest {",
    "  void getAssetReadinessReflectsReleasedPackagesAndGrayscaleEvidence() { planRepository.countByTenantIdAndStrategyAndStatus(null, null, null); }",
    "  void syncPackageDoesNotAffectOtherPackageCodes() { packageRepository.findByTenantIdAndPackageCodeAndStatus(null, null, null); }",
    "  void listSyncLogsReturnsServerPageForPackageReleaseEvidence() {",
    "    logRepository.countByTenantIdAndPackageId(null, null);",
    "    logRepository.pageByTenantIdAndPackageId(null, null, 0, 20);",
    "  }",
    "  void listReleaseAdaptersReturnsActivePageInsteadOfTenantSnapshot() {",
    "    adapterRepository.countByTenantIdAndStatus(null, \"ACTIVE\");",
    "    adapterRepository.pageByTenantIdAndStatus(null, \"ACTIVE\", 0, 20);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function packageEngineControllerSecurityTestContent(extra = "") {
  return [
    "class PackageEngineControllerSecurityTest {",
    '  void authorizedUserCanValidateReleaseAndReadPersistedSyncLogs() { String path = "/pkg-1/sync-logs $.data.items[0].status"; }',
    '  void requestParams() { mvc.perform(get(PKG_ROOT + "/pkg-1/sync-logs").param("page", "1").param("size", "20")); }',
    '  void releaseAdaptersPage() { mvc.perform(get(PKG_ROOT + "/release-adapters").param("page", "1").param("size", "20")); String path = "$.data.items[0].adapterId"; }',
    "}",
    extra,
  ].join("\n");
}

function thirdPartyPackageReconciliationResponseContent(extra = "") {
  return [
    "record ThirdPartyPackageReconciliationResponse(",
    "  String contractVersion,",
    "  String packageId,",
    "  ThirdPartyReconciliationStatus status,",
    "  PageResponse<SyncLogResponse> logs",
    ") {}",
    extra,
  ].join("\n");
}

function thirdPartyKnowledgeRuntimeServiceContent(extra = "") {
  return [
    "class ThirdPartyKnowledgeRuntimeService {",
    "  ThirdPartyPackageReconciliationResponse reconcilePackage(String packageId, PageRequest page) {",
    "    PageResponse<SyncLogResponse> logs = packages.listSyncLogs(normalizedPackageId, page);",
    "    return new ThirdPartyPackageReconciliationResponse(CONTRACT_VERSION, packageId, reconciliationStatus(logs.items()), logs);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function thirdPartyKnowledgeRuntimeControllerContent(extra = "") {
  return [
    "class ThirdPartyKnowledgeRuntimeController {",
    "  ApiResult<ThirdPartyPackageReconciliationResponse> reconcilePackage(String packageId, Integer page, Integer size) {",
    "    return ApiResult.ok(service.reconcilePackage(packageId, new PageRequest(page, size, null)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function thirdPartyKnowledgeRuntimeServiceTestContent(extra = "") {
  return [
    "class ThirdPartyKnowledgeRuntimeServiceTest {",
    "  void reconciliationReportsHonestNotSyncedState() { PageResponse.of(logs, page, logs.size()); }",
    "}",
    extra,
  ].join("\n");
}

function tenantPilotServiceContent(extra = "") {
  return [
    "class TenantPilotService {",
    "  void organizationStep(String tenantId) {",
    "    orgUnitRepository.countByTenantIdAndLevelAndStatus(tenantId, OrgLevel.FACILITY, OrgUnitStatus.ACTIVE);",
    "  }",
    "  void usersStep(String tenantId) {",
    '    credentialRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");',
    "  }",
    "  void permissionsStep(String tenantId) {",
    '    roleAssignmentRepository.existsByTenantIdAndActiveFlag(tenantId, "Y");',
    "  }",
    "  void adaptersStep(String tenantId) {",
    '    adapterRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");',
    "  }",
    "  void assetsStep(String tenantId) {",
    "    packageReferenceRepository.countByTenantIdAndStatus(tenantId, TenantPackageReferenceStatus.ACTIVE);",
    "    packageRepository.countReleasedByTenantId(tenantId);",
    "  }",
    "  void grayscaleStep(String tenantId) {",
    "    releasePlanRepository.countByTenantIdAndStrategyAndStatus(tenantId, ReleaseStrategy.GRAYSCALE, ReleasePlanStatus.SUCCESS);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function tenantPilotServiceTestContent(extra = "") {
  return [
    "class TenantPilotServiceTest {",
    "  void onboardingReadinessAllowsOpeningWhenTenantReleasedPackageExistsWithoutTenantSnapshot() {}",
    "}",
    extra,
  ].join("\n");
}

function mpiMergeReviewRepositoryContent(extra = "") {
  return [
    "interface MpiMergeReviewRepository {",
    "  List<MpiMergeReview> findAllByTenantIdAndStatus(String tenantId, String status);",
    "  long countByTenantIdAndStatus(String tenantId, String status);",
    "  List<MpiMergeReview> pageByTenantIdAndStatus(String tenantId, String status, int offset, int limit);",
    "}",
    extra,
  ].join("\n");
}

function mpiServiceContent(extra = "") {
  return [
    "class MpiService {",
    "  PageResponse<MpiMergeReview> getMergeReviews(String status, PageRequest pageReq) {",
    "    reviewRepository.countByTenantIdAndStatus(tenantId, normalizedStatus);",
    "    reviewRepository.pageByTenantIdAndStatus(tenantId, normalizedStatus, req.offset(), req.safeSize());",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function mpiControllerContent(extra = "") {
  return [
    "class MpiController {",
    "  ApiResult<PageResponse<MpiMergeReview>> getMergeReviews(String status, int page, int size, String sort) {",
    "    return ApiResult.ok(service.getMergeReviews(status, new PageRequest(page, size, sort)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function mpiServiceTestContent(extra = "") {
  return [
    "class MpiServiceTest {",
    "  void shouldReturnMergeReviewsPageWithoutMaterializingTenantStatusSnapshot() {}",
    "}",
    extra,
  ].join("\n");
}

function mpiControllerContractTestContent(extra = "") {
  return [
    "class MpiControllerContractTest {",
    '  String json = "$.data.items[0].reviewId";',
    "  PageResponse<MpiMergeReview> page;",
    "}",
    extra,
  ].join("\n");
}

function knowledgeProductionCandidateRepositoryContent(extra = "") {
  return [
    "interface KnowledgeProductionCandidateRepository {",
    "  List<KnowledgeProductionCandidate> findByTenantIdAndJobCode(String tenantId, String jobCode);",
    "  long countByTenantIdAndJobCode(String tenantId, String jobCode);",
    "  List<KnowledgeProductionCandidate> pageByTenantIdAndJobCode(String tenantId, String jobCode, int offset, int limit);",
    "  String sql = \"ORDER BY created_at ASC, id ASC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY\";",
    "}",
    extra,
  ].join("\n");
}

function knowledgeProductionServiceContent(extra = "") {
  return [
    "class KnowledgeProductionOrchestrationService {",
    "  PageResponse<ProductionCandidateView> listCandidates(String jobCode, int page, int size) {",
    "    PageRequest pageRequest = new PageRequest(page, size, null);",
    "    long total = candidateRepository.countByTenantIdAndJobCode(tenantId, jobCode);",
    "    var rows = candidateRepository.pageByTenantIdAndJobCode(tenantId, jobCode, pageRequest.offset(), pageRequest.safeSize());",
    "    return PageResponse.of(rows, pageRequest, total);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeProductionControllerContent(extra = "") {
  return [
    "class KnowledgeProductionController {",
    "  ApiResult<PageResponse<ProductionCandidateView>> listCandidates(",
    "      String jobCode,",
    '      @RequestParam(required = false, defaultValue = "1") int page,',
    '      @RequestParam(required = false, defaultValue = "20") int size) {',
    "    return ApiResult.ok(service.listCandidates(jobCode, page, size));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeProductionServiceTestContent(extra = "") {
  return [
    "class KnowledgeProductionOrchestrationServiceTest {",
    "  void listCandidatesReturnsTenantScopedPageWithRouting() {}",
    "}",
    extra,
  ].join("\n");
}

function knowledgeProductionCandidateRepositoryTestContent(extra = "") {
  return [
    "class KnowledgeProductionCandidateRepositoryIntegrationTest {",
    "  void pagesLineageByJobWithoutLoadingAllRows() {}",
    "}",
    extra,
  ].join("\n");
}

function knowledgeProductionControllerSecurityTestContent(extra = "") {
  return [
    "class KnowledgeProductionControllerSecurityTest {",
    '  void knowledgeGovernorCanListCandidates() { jsonPath("$.data.items").isArray(); }',
    "  void oversizedProvenanceRefsRejectedBeforeService() {",
    "    verify(provenanceService, never()).resolve(any());",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function candidateProvenanceRequestContent(extra = "") {
  return [
    "record CandidateProvenanceRequest(",
    "  @NotEmpty",
    "  @Size(max = CandidateProvenanceRequest.MAX_CANDIDATE_REFS)",
    "  List<@NotBlank @Size(max = 128) String> candidateRefs",
    ") {",
    "  static final int MAX_CANDIDATE_REFS = 200;",
    "}",
    extra,
  ].join("\n");
}

function candidateProvenanceServiceContent(extra = "") {
  return [
    "class CandidateProvenanceService {",
    "  List<CandidateProvenanceView> resolve(Collection<String> candidateRefs) {",
    "    if (candidateRefs.size() > CandidateProvenanceRequest.MAX_CANDIDATE_REFS) {",
    "      throw new ApiException(ErrorCode.VALIDATION_FAILED, \"候选来源溯源一次最多查询 200 条\");",
    "    }",
    "    List<String> normalizedRefs = candidateRefs.stream()",
    "      .map(String::trim)",
    "      .distinct()",
    "      .toList();",
    "    return candidateRepository.findByTenantIdAndCandidateRefIn(tenantId, normalizedRefs);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function candidateProvenanceServiceTestContent(extra = "") {
  return [
    "class CandidateProvenanceServiceTest {",
    "  void rejectsOversizedProvenanceRefBatchBeforeRepositoryLookup() {}",
    "}",
    extra,
  ].join("\n");
}

function docParseJobRepositoryContent(extra = "") {
  return [
    "interface DocParseJobRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<DocParseJob> pageByTenantId(String tenantId, int offset, int limit);",
    "  String sql = \"ORDER BY created_at DESC, id DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY\";",
    "}",
    extra,
  ].join("\n");
}

function documentParseServiceContent(extra = "") {
  return [
    "class DocumentParseOrchestrationService {",
    "  PageResponse<DocParseJob> listJobs(int page, int size) {",
    "    PageRequest pageRequest = new PageRequest(page, size, null);",
    "    long total = jobRepository.countByTenantId(tenantId);",
    "    var rows = jobRepository.pageByTenantId(tenantId, pageRequest.offset(), pageRequest.safeSize());",
    "    return PageResponse.of(rows, pageRequest, total);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function documentParseControllerContent(extra = "") {
  return [
    "class DocumentParseController {",
    "  ApiResult<PageResponse<DocParseJob>> listJobs(",
    '      @RequestParam(required = false, defaultValue = "1") int page,',
    "      int size) {",
    "    return ApiResult.ok(service.listJobs(page, size));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function documentParseServiceTestContent(extra = "") {
  return [
    "class DocumentParseOrchestrationServiceTest {",
    "  void listJobsReturnsTenantScopedPageWithTotal() {}",
    "}",
    extra,
  ].join("\n");
}

function documentParseControllerTestContent(extra = "") {
  return [
    "class DocumentParseControllerSecurityTest {",
    '  void knowledgeGovernorCanListJobs() { jsonPath("$.data.items[0].jobCode").value("dpj:x"); }',
    "}",
    extra,
  ].join("\n");
}

function knowledgeExportJobRepositoryContent(extra = "") {
  return [
    "interface KnowledgeExportJobRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<KnowledgeExportJob> pageByTenantId(String tenantId, int offset, int limit);",
    "  String sql = \"ORDER BY created_at DESC, id DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY\";",
    "}",
    extra,
  ].join("\n");
}

function knowledgeExportServiceContent(extra = "") {
  return [
    "class KnowledgeExportService {",
    "  PageResponse<KnowledgeExportJob> listRecent(PageRequest request) {",
    "    long total = jobRepository.countByTenantId(tenantId);",
    "    var items = jobRepository.pageByTenantId(tenantId, page.offset(), page.safeSize());",
    "    return PageResponse.of(items, page, total);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeExportControllerContent(extra = "") {
  return [
    "class KnowledgeExportController {",
    "  ApiResult<PageResponse<KnowledgeExportJob>> listRecent(int page, int size) {",
    "    return ApiResult.ok(exportService.listRecent(new PageRequest(page, size, null)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeExportServiceTestContent(extra = "") {
  return [
    "class KnowledgeExportServiceTest {",
    "  void listRecentReturnsTenantScopedPageInsteadOfTop100Snapshot() {",
    "    Mockito.verify(jobRepo).pageByTenantId(\"t-1\", 20, 20);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function knowledgeIdentityControllerSecurityTestContent(extra = "") {
  return [
    "class KnowledgeIdentityControllerSecurityTest {",
    '  void auditComplianceCanListExportsAsPage() { jsonPath("$.data.items[0].jobCode").value("job-1"); }',
    '  void readRoleListsKnowledgeVersionsAsPagedContract() { jsonPath("$.data.items[0].id").value(10); }',
    "  void doctorCanReachProvenanceButDataScopeRejectsMissingTenant() {}",
    "}",
    extra,
  ].join("\n");
}

function engineDataExportJobRepositoryContent(extra = "") {
  return [
    "interface EngineDataExportJobRepository {",
    "  long countByTenantId(String tenantId);",
    "  List<EngineDataExportJob> pageByTenantId(String tenantId, int offset, int limit);",
    "  String sql = \"ORDER BY created_at DESC, id DESC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY\";",
    "}",
    extra,
  ].join("\n");
}

function engineDataExportServiceContent(extra = "") {
  return [
    "class EngineDataExportService {",
    "  PageResponse<EngineDataExportJob> listRecent(PageRequest request) {",
    "    long total = jobRepository.countByTenantId(tenantId);",
    "    var items = jobRepository.pageByTenantId(tenantId, page.offset(), page.safeSize());",
    "    return PageResponse.of(items, page, total);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function engineDataControllerContent(extra = "") {
  return [
    "class EngineDataController {",
    "  ApiResult<PageResponse<EngineDataExportJob>> listExports(int page, int size) {",
    "    return ApiResult.ok(engineDataExportService.listRecent(new PageRequest(page, size, null)));",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function engineDataExportServiceTestContent(extra = "") {
  return [
    "class EngineDataExportServiceTest {",
    "  void listRecentReturnsTenantScopedPageInsteadOfTop100Snapshot() {",
    "    Mockito.verify(jobRepo).pageByTenantId(\"t-1\", 20, 20);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function engineDataExportJobRepositoryTestContent(extra = "") {
  return [
    "class EngineDataExportJobRepositoryIntegrationTest {",
    "  void pagesRecentScopedToTenant() {",
    "    repo.countByTenantId(TENANT);",
    "    repo.pageByTenantId(TENANT, 0, 2);",
    "  }",
    "}",
    extra,
  ].join("\n");
}

function engineDataControllerSecurityTestContent(extra = "") {
  return [
    "class EngineDataControllerSecurityTest {",
    '  void qualityGovernorCanListExportsAsPage() { jsonPath("$.data.items[0].jobCode").value("job-1"); }',
    "}",
    extra,
  ].join("\n");
}

function pathwayRepositoryTestContent(extra = "") {
  return [
    "class PathwayRepositoryTest {",
    "  void packageFilterFindsPathwayPackageByPackageIdKeyword() {}",
    "  void pagesEffectiveTemplatesWithoutMaterializingTenantAndPlatformSnapshots() {}",
    "}",
    extra,
  ].join("\n");
}

async function fixtureRoot(overrides = {}) {
  const root = await mkdtemp(join(tmpdir(), "medkernel-b0-perfect-"));
  const files = {
    "frontend/src/pages/quality/KnowledgeGovernance.tsx":
      knowledgeGovernanceContent(),
    "frontend/src/pages/quality/KnowledgeGovernance.test.tsx":
      knowledgeGovernanceTestContent(),
    "frontend/src/shared/api/hooks.ts": apiHooksContent(),
    "frontend/src/shared/api/hooks.test.ts": apiHooksTestContent(),
    "frontend/src/pages/compliance/SecurityBaselinePanels.tsx":
      securityBaselinePanelsContent(),
    "frontend/src/pages/compliance/SecurityBaseline.test.tsx":
      securityBaselineTestContent(),
    "frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.tsx":
      "export default function DiagnosisKnowledgeMaintenance() { return <div>诊断知识维护</div>; }",
    "frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx":
      diagnosisPanelContent(),
    "frontend/src/pages/quality/DiagnosisKnowledgePanel.test.tsx":
      diagnosisPanelTestContent(),
    "frontend/src/pages/quality/QcEvalSets.tsx": qcEvalSetsContent(),
    "frontend/src/pages/quality/QcEvalSets.test.tsx": qcEvalSetsTestContent(),
    "frontend/src/pages/quality/InsuranceAudit.tsx":
      insuranceAuditContent(),
    "frontend/src/pages/quality/InsuranceAudit.test.tsx":
      insuranceAuditTestContent(),
    "frontend/src/pages/clinical/Followup.tsx": followupContent(),
    "frontend/src/pages/clinical/Followup.test.tsx": followupTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/followup/FollowupTemplateRepository.java":
      followupTemplateRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/followup/FollowupTemplateService.java":
      followupTemplateServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/followup/FollowupTemplateServiceTest.java":
      followupTemplateServiceTestContent(),
    "frontend/src/pages/compliance/IdentityBinding.tsx":
      identityBindingContent(),
    "frontend/src/pages/compliance/AdminAudit.tsx": adminAuditContent(),
    "frontend/src/pages/compliance/AdminAudit.test.tsx":
      adminAuditTestContent(),
    "frontend/src/pages/operationalControlPages.test.tsx":
      operationalControlPagesTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/identitybinding/IdentityBindingRepository.java":
      identityBindingRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/identitybinding/IdentityBindingService.java":
      identityBindingServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/compliance/identitybinding/IdentityBindingControllerTest.java":
      identityBindingControllerTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalRepository.java":
      exportApprovalRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalService.java":
      exportApprovalServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalController.java":
      exportApprovalControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/compliance/exportapproval/ExportApprovalServiceTest.java":
      exportApprovalServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/compliance/exportapproval/ExportApprovalControllerSecurityTest.java":
      exportApprovalControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionPolicyRepository.java":
      dataPermissionPolicyRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionService.java":
      dataPermissionServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionController.java":
      dataPermissionControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/compliance/datapermission/DataPermissionServiceTest.java":
      dataPermissionServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/compliance/datapermission/DataPermissionControllerSecurityTest.java":
      dataPermissionControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/masking/MaskingRuleRepository.java":
      maskingRuleRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/masking/MaskingService.java":
      maskingServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/compliance/masking/MaskingRuleController.java":
      maskingRuleControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/compliance/masking/MaskingServiceTest.java":
      maskingServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/compliance/masking/MaskingRuleControllerSecurityTest.java":
      maskingRuleControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationRepository.java":
      knowledgeCustomizationRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationService.java":
      knowledgeCustomizationServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationController.java":
      knowledgeCustomizationControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeCustomizationServiceTest.java":
      knowledgeCustomizationServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionCandidateRepository.java":
      knowledgeProductionCandidateRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationService.java":
      knowledgeProductionServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionController.java":
      knowledgeProductionControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationServiceTest.java":
      knowledgeProductionServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionCandidateRepositoryIntegrationTest.java":
      knowledgeProductionCandidateRepositoryTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionControllerSecurityTest.java":
      knowledgeProductionControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateProvenanceRequest.java":
      candidateProvenanceRequestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateProvenanceService.java":
      candidateProvenanceServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/CandidateProvenanceServiceTest.java":
      candidateProvenanceServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocParseJobRepository.java":
      docParseJobRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationService.java":
      documentParseServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocumentParseController.java":
      documentParseControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationServiceTest.java":
      documentParseServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseControllerSecurityTest.java":
      documentParseControllerTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportJobRepository.java":
      knowledgeExportJobRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportService.java":
      knowledgeExportServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportController.java":
      knowledgeExportControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeExportServiceTest.java":
      knowledgeExportServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityControllerSecurityTest.java":
      knowledgeIdentityControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportJobRepository.java":
      engineDataExportJobRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportService.java":
      engineDataExportServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/EngineDataController.java":
      engineDataControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/datasvc/export/EngineDataExportServiceTest.java":
      engineDataExportServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/datasvc/export/EngineDataExportJobRepositoryIntegrationTest.java":
      engineDataExportJobRepositoryTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/datasvc/EngineDataControllerSecurityTest.java":
      engineDataControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepository.java":
      knowledgeIdentityRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityController.java":
      knowledgeIdentityControllerContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java":
      knowledgeIdentityServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityServiceTest.java":
      knowledgeIdentityServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepositoryTest.java":
      knowledgeIdentityRepositoryTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeAssetVersionRepository.java":
      knowledgeAssetVersionRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/CandidateClassificationRepository.java":
      candidateClassificationRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateResponse.java":
      knowledgeCandidateResponseContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java":
      knowledgeVersionServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionController.java":
      knowledgeVersionControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java":
      knowledgeVersionServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeProvenanceResponse.java":
      knowledgeProvenanceResponseContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeLineage.java":
      knowledgeLineageContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeSupersessionRepository.java":
      knowledgeSupersessionRepositoryContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeAssetApiContractTest.java":
      knowledgeAssetApiContractTestContent(),
    "frontend/src/pages/advanced/Provenance.tsx": advancedProvenanceContent(),
    "frontend/src/pages/advanced/Provenance.test.tsx":
      advancedProvenanceTestContent(),
    "frontend/src/pages/tenant/AuthoringAssets.tsx": authoringAssetsContent(),
    "frontend/src/pages/tenant/AuthoringAssets.test.tsx":
      authoringAssetsTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringAssetLibraryService.java":
      authoringAssetLibraryServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringAssetLibraryServiceTest.java":
      authoringAssetLibraryServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringAssetLibraryRepositoryTest.java":
      authoringAssetLibraryRepositoryTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/ConditionFragmentService.java":
      conditionFragmentServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/ConditionFragmentImpactResponse.java":
      conditionFragmentImpactResponseContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/ConditionFragmentController.java":
      conditionFragmentControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/authoring/ConditionFragmentServiceTest.java":
      conditionFragmentServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/authoring/ConditionFragmentControllerTest.java":
      conditionFragmentControllerTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobRepository.java":
      authoringBatchJobRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobService.java":
      authoringBatchJobServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobController.java":
      authoringBatchJobControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringBatchJobServiceTest.java":
      authoringBatchJobServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringBatchJobControllerTest.java":
      authoringBatchJobControllerTestContent(),
    "frontend/src/pages/tenant/AuthoringBatchDrawer.tsx":
      authoringBatchDrawerContent(),
    "frontend/src/pages/tenant/AuthoringBatchDrawer.test.tsx":
      authoringBatchDrawerTestContent(),
    "frontend/src/pages/tenant/PathwayTemplates.tsx": pathwayTemplatesContent(),
    "frontend/src/pages/tenant/PathwayTemplates.test.tsx":
      pathwayTemplatesTestContent(),
    "frontend/src/pages/tenant/AdapterHub.tsx": adapterHubContent(),
    "frontend/src/pages/tenant/AdapterHub.test.tsx": adapterHubTestContent(),
    "frontend/src/pages/tenant/RuleDefinitions.tsx": ruleDefinitionsContent(),
    "frontend/src/pages/tenant/RuleDefinitions.test.tsx":
      ruleDefinitionsTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleDefinitionRepository.java":
      ruleDefinitionRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java":
      ruleEngineServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineServiceTest.java":
      ruleEngineServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleRepositoryTest.java":
      ruleRepositoryTestContent(),
    "frontend/src/pages/tenant/TerminologyMapping.tsx":
      terminologyMappingContent(),
    "frontend/src/pages/tenant/TerminologyMapping.test.tsx":
      terminologyMappingTestContent(),
    "frontend/src/pages/tenant/ReleaseGovernance.tsx":
      releaseGovernanceContent(),
    "frontend/src/pages/tenant/ReleaseGovernance.test.tsx":
      releaseGovernanceTestContent(),
    "frontend/src/pages/tenant/ConfigPackages.tsx": configPackagesContent(),
    "frontend/src/pages/tenant/ConfigPackages.test.tsx":
      configPackagesTestContent(),
    "frontend/src/pages/clinical/PatientPathways.tsx": patientPathwaysContent(),
    "frontend/src/pages/clinical/PatientPathways.test.tsx":
      patientPathwaysTestContent(),
    "frontend/src/shared/config/routes.ts":
      "export const routes = [{ path: '/knowledge/diagnosis', title: '诊断知识维护' }];",
    "docs/_HANDOFF.md":
      "# 会话接力\n\n## 2026-06-15 B0 第一阶段全功能核查与完美化整改进行中\n\n国产化真实环境本轮暂不处理，后续全面验收再处理。\n",
    "docs/audit/2026-06-15-B0第一阶段全功能核查与完美化改造方案.md":
      "# 2026-06-15 B0 第一阶段全功能核查与完美化改造方案\n\n## 当前整改状态\n\n## 本轮国产化边界\n\n国产化真实环境本轮暂不处理，后续全面验收再处理。\n",
    "docs/audit/deferred-issues.md":
      "# 待处理问题清单\n\nDEFER-010 当前迁移口径为 V135，真实方言 P95 与资源占用仍 open。\n",
    "scripts/sandbox/scenario-rules.json": sandboxManifest(),
    "frontend/e2e/b0-screenshot-chain.spec.ts": screenshotChainSpecContent(),
    "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java":
      largeScaleDialectSmokeContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeExportServiceLargeScaleTest.java":
      knowledgeExportLargeScaleContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/terminology/TerminologyService.java":
      terminologyServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/terminology/TerminologyController.java":
      terminologyControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyServiceTest.java":
      terminologyServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyApiContractTest.java":
      terminologyApiContractContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/terminology/TerminologyCandidateGenerationJob.java":
      terminologyCandidateGenerationJobContent(),
    "medkernel-backend/src/main/resources/db/migration/h2/V135__terminology_candidate_generation_job.sql":
      terminologyCandidateGenerationMigrationContent(),
    "medkernel-backend/src/main/resources/db/migration/postgres/V135__terminology_candidate_generation_job.sql":
      terminologyCandidateGenerationMigrationContent(),
    "medkernel-backend/src/main/resources/db/migration/oracle/V135__terminology_candidate_generation_job.sql":
      terminologyCandidateGenerationMigrationContent(),
    "medkernel-backend/src/main/resources/db/migration/dm/V135__terminology_candidate_generation_job.sql":
      terminologyCandidateGenerationMigrationContent(),
    "medkernel-backend/src/main/resources/db/migration/kingbase/V135__terminology_candidate_generation_job.sql":
      terminologyCandidateGenerationMigrationContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyRepositoryLargeScaleTest.java":
      terminologyLargeScaleContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFactBridge.java":
      contextFactBridgeContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEngineService.java":
      pathwayEngineServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineServiceTest.java":
      pathwayEngineServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayTemplateRepository.java":
      pathwayTemplateRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeService.java":
      diagnosisKnowledgeServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisReferenceValidator.java":
      diagnosisReferenceValidatorContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeServiceTest.java":
      diagnosisKnowledgeServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisReferenceValidatorTest.java":
      diagnosisReferenceValidatorTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/CitationRepository.java":
      citationRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/KnowledgePackageRepository.java":
      knowledgePackageRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/ReleasePlanRepository.java":
      releasePlanRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/TenantPackageReferenceRepository.java":
      tenantPackageReferenceRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/org/OrgUnitRepository.java":
      orgUnitRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/security/PlatformCredentialRepository.java":
      platformCredentialRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/security/UserRoleAssignmentRepository.java":
      userRoleAssignmentRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/IntegrationAdapterRepository.java":
      integrationAdapterRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/IntegrationOnboardingRepository.java":
      integrationOnboardingRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/IntegrationWebhookConfigRepository.java":
      integrationWebhookConfigRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/RegionalSourceRepository.java":
      regionalSourceRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/service/IntegrationService.java":
      integrationServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/controller/IntegrationController.java":
      integrationControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/integration/IntegrationServiceTest.java":
      integrationServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/integration/IntegrationControllerSecurityTest.java":
      integrationControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/versioning/OverrideTemplateRepository.java":
      overrideTemplateRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/versioning/OverrideTemplateService.java":
      overrideTemplateServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/versioning/ReleaseGovernanceController.java":
      releaseGovernanceControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/versioning/OverrideTemplateServiceTest.java":
      overrideTemplateServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/versioning/ReleaseGovernanceControllerTest.java":
      releaseGovernanceControllerTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/SyncLogRepository.java":
      syncLogRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java":
      packageEngineServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineController.java":
      packageEngineControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/pkg/PackageEngineServiceTest.java":
      packageEngineServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/pkg/PackageEngineControllerSecurityTest.java":
      packageEngineControllerSecurityTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/runtime/ThirdPartyPackageReconciliationResponse.java":
      thirdPartyPackageReconciliationResponseContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/runtime/ThirdPartyKnowledgeRuntimeService.java":
      thirdPartyKnowledgeRuntimeServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/runtime/ThirdPartyKnowledgeRuntimeController.java":
      thirdPartyKnowledgeRuntimeControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/integration/runtime/ThirdPartyKnowledgeRuntimeServiceTest.java":
      thirdPartyKnowledgeRuntimeServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java":
      tenantPilotServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/tenant/TenantPilotServiceTest.java":
      tenantPilotServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiMergeReviewRepository.java":
      mpiMergeReviewRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiService.java":
      mpiServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiController.java":
      mpiControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/mpi/MpiServiceTest.java":
      mpiServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/mpi/MpiControllerContractTest.java":
      mpiControllerContractTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayRepositoryTest.java":
      pathwayRepositoryTestContent(),
    ...overrides,
  };
  await Promise.all(
    Object.entries(files).map(([file, content]) => write(root, file, content)),
  );
  return root;
}

test("B0 门禁阻断审核页重新混入诊断维护", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/KnowledgeGovernance.tsx":
      knowledgeGovernanceContent(
        "import { DiagnosisKnowledgePanel } from './DiagnosisKnowledgePanel';\nexport default function MixedKnowledgeGovernance() { return <DiagnosisKnowledgePanel />; }",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.deepEqual(
    report.violations.map((item) => item.ruleId),
    ["b0.knowledge-governance.mixed-diagnosis-maintenance"],
  );
});

test("B0 门禁阻断审核页文案重新宣称承载诊断维护", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/KnowledgeGovernance.tsx":
      knowledgeGovernanceContent(
        "const description = '统一审核知识候选并维护结构化诊断知识';",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.deepEqual(
    report.violations.map((item) => item.ruleId),
    ["b0.knowledge-governance.mixed-diagnosis-maintenance-copy"],
  );
});

test("B0 门禁阻断诊断维护引用选择器回退固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx":
      diagnosisPanelContent("const staleReferenceSnapshot = { size: 100 };"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) => item.ruleId === "b0.diagnosis-reference-search.fixed-page-size",
    ),
  );
});

test("B0 门禁阻断患者路径模板引用固定快照或包版本回退", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/clinical/PatientPathways.tsx": patientPathwaysContent(
      "const staleClinicalPathwayFallback = { size: 100, fallback: String(template.templateVersion) };",
    ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.patient-pathway-reference-search.fixed-page-size",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.patient-pathway-package-version.unsafe-fallback",
    ),
  );
});

test("B0 门禁阻断路径包列表 keyword 漏掉 package_id", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/KnowledgePackageRepository.java":
      knowledgePackageRepositoryContent(
        'const String missing = "LOWER(kp.package_code) LIKE :keyword";',
      ).replace("LOWER(kp.package_id) LIKE :keyword OR ", ""),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.package-reference-search.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断配置包服务退回租户全量包扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java": [
      "class PackageEngineService {",
      "  void getAssetReadiness() { packageRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.package-engine-service.tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断租户开通资产就绪退回租户全量包扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java": [
      "class TenantPilotService {",
      "  void assetsStep(String tenantId) {",
      "    packageRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream().anyMatch(this::releasedPackage);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.tenant-pilot-readiness.package-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断租户开通资产就绪退回全量平台包引用扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java": [
      "class TenantPilotService {",
      "  void assetsStep(String tenantId) {",
      "    boolean hasActiveReference = packageReferenceRepository.findByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, TenantPackageReferenceStatus.ACTIVE).stream().findAny().isPresent();",
      "    packageRepository.countReleasedByTenantId(tenantId);",
      "  }",
      "  void grayscaleStep(String tenantId) { releasePlanRepository.countByTenantIdAndStrategyAndStatus(tenantId, ReleaseStrategy.GRAYSCALE, ReleasePlanStatus.SUCCESS); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.tenant-pilot-readiness.package-reference-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断租户开通组织就绪退回机构全量扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java": [
      "class TenantPilotService {",
      "  void organizationStep(String tenantId) {",
      "    boolean hasFacility = orgUnitRepository.findByTenantIdAndLevelOrderByCodeAsc(tenantId, OrgLevel.FACILITY).stream().anyMatch(OrgUnit::isActive);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.tenant-pilot-readiness.organization-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断租户开通用户就绪退回用户全量扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java": [
      "class TenantPilotService {",
      "  void usersStep(String tenantId) {",
      "    boolean hasActiveUser = credentialRepository.findByTenantIdOrderByUsernameAsc(tenantId).stream().anyMatch(credential -> credential.active());",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.tenant-pilot-readiness.user-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断租户开通权限就绪退回角色全量扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java": [
      "class TenantPilotService {",
      "  void permissionsStep(String tenantId) {",
      "    boolean hasActiveAssignment = roleAssignmentRepository.findByTenantId(tenantId).stream().anyMatch(assignment -> assignment.active());",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.tenant-pilot-readiness.permission-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断租户开通适配器就绪退回适配器全量扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java": [
      "class TenantPilotService {",
      "  void adaptersStep(String tenantId) {",
      "    boolean hasActiveAdapter = adapterRepository.findAllByTenantId(tenantId).stream().anyMatch(this::activeAdapter);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.tenant-pilot-readiness.adapter-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断 MPI 合并审核列表退回租户状态全量扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiService.java": [
      "class MpiService {",
      "  List<MpiMergeReview> getMergeReviews(String status) {",
      '    return reviewRepository.findAllByTenantIdAndStatus(tenantId, "PENDING");',
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) => item.ruleId === "b0.mpi-merge-review-list.tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断 MPI 合并审核控制器退回数组响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiController.java": [
      "class MpiController {",
      "  ApiResult<java.util.List<MpiMergeReview>> getMergeReviews(String status) {",
      "    return ApiResult.ok(service.getMergeReviews(status));",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) => item.ruleId === "b0.mpi-merge-review-list.controller-array-forbidden",
    ),
  );
});

test("B0 门禁阻断配置包 readiness 灰度证据退回全量发布计划扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java": [
      "class PackageEngineService {",
      "  void getAssetReadiness() {",
      "    boolean grayscaleReady = planRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().anyMatch(plan -> plan.strategy() == ReleaseStrategy.GRAYSCALE && plan.status() == ReleasePlanStatus.SUCCESS);",
      "  }",
      "  void syncPackage(KnowledgePackage pack) { packageRepository.findByTenantIdAndPackageCodeAndStatus(tenantId, pack.packageCode(), KnowledgePackageStatus.ACTIVE); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.release-plan-grayscale-readiness.tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断租户开通灰度步骤退回全量发布计划扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java": [
      "class TenantPilotService {",
      "  void assetsStep(String tenantId) { packageRepository.countReleasedByTenantId(tenantId); }",
      "  void grayscaleStep(String tenantId) {",
      "    boolean hasSuccessfulGrayscale = releasePlanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().anyMatch(plan -> plan.status() == ReleasePlanStatus.SUCCESS);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.release-plan-grayscale-readiness.tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断 deferred 清单保留术语候选任务旧迁移版本", async () => {
  const root = await fixtureRoot({
    "docs/audit/deferred-issues.md":
      "# 待处理问题清单\n\n术语候选生成证明 V133 任务表已存在。\n",
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.deepEqual(
    report.violations.map((item) => item.ruleId),
    ["b0.deferred-issues.stale-terminology-migration-version"],
  );
});

test("B0 门禁通过当前完美化整改所需的最小文档和路由约束", async () => {
  const root = await fixtureRoot();

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), false);
  assert.deepEqual(report.violations, []);
});

test("B0 门禁识别 Prettier 多行分页 hook 签名", async () => {
  const root = await fixtureRoot({
    "frontend/src/shared/api/hooks.ts": apiHooksContent()
      .replace(
        "function useKnowledgeCandidates(identityId?: number, params: KnowledgeCandidatesParams = {}) {",
        [
          "function useKnowledgeCandidates(",
          "  identityId?: number,",
          "  params: KnowledgeCandidatesParams = {},",
          ") {",
        ].join("\n"),
      )
      .replace(
        "function usePackageReleaseAdapters(params: PackageReleaseAdaptersParams = {}, enabled = true) {",
        [
          "function usePackageReleaseAdapters(",
          "  params: PackageReleaseAdaptersParams = {},",
          "  enabled = true,",
          ") {",
        ].join("\n"),
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), false);
  assert.deepEqual(report.violations, []);
});

test("B0 门禁阻断未评审沙盘场景提前开放", async () => {
  const root = await fixtureRoot({
    "scripts/sandbox/scenario-rules.json": sandboxManifest({
      1: {
        reviewStatus: "APPROVED_FOR_SANDBOX",
        reviewEvidence: "尚未纳入 B0 当前验收口径的临床评审",
        sourceRef: "测试来源",
        changeSummary: "测试提前开放第二条场景",
        clinicalContent: approvedClinicalContent(),
      },
    }),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.deepEqual(
    report.violations.map((item) => item.ruleId),
    ["b0.sandbox.approved-scope-drift"],
  );
});

test("B0 门禁阻断项目 Playwright 截图链路缺失", async () => {
  const root = await fixtureRoot({
    "frontend/e2e/b0-screenshot-chain.spec.ts": "",
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.playwright-screenshot-chain.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断 PostgreSQL 和 Oracle 10 万级压测规格缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java":
      "",
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.large-scale-dialect-smoke.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断真实方言候选冲突和导出筛选覆盖缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java":
      largeScaleDialectSmokeContent()
        .replace("mapping_candidate ", "")
        .replace("mapping_conflict", "")
        .replace("  void assertKnowledgeExportEquivalentScan() {}\n", "")
        .replace("  void assertCandidateAndConflictQueries() {}\n", "")
        .replace("  void refreshTerminologySourceStatsSql() {}\n", ""),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.large-scale-dialect-smoke.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断真实方言知识导出等价文件覆盖缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java":
      largeScaleDialectSmokeContent().replace(
        "  void assertKnowledgeExportEquivalentFile() { Files.newBufferedWriter(null); percentile95(null); }\n",
        "",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.large-scale-dialect-smoke.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断真实方言知识导出等价分页 P95 覆盖缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java":
      largeScaleDialectSmokeContent().replace(" percentile95(null);", ""),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.large-scale-dialect-smoke.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断真实方言候选冲突分页 P95 覆盖缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java":
      largeScaleDialectSmokeContent().replace(
        " assertCandidateAndConflictPageP95(null);",
        "",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.large-scale-dialect-smoke.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断知识 10 万级异步导出链路合同缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeExportServiceLargeScaleTest.java":
      "",
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-export-large-scale.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断术语候选和冲突 10 万级合同缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyRepositoryLargeScaleTest.java":
      "",
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.terminology-large-scale.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断路径字段目录 canonical path 桥接回归缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineServiceTest.java":
      pathwayEngineServiceTestContent().replace(
        "  void enterPatientPathwayUsesCanonicalObservationPathForEntryIncludeCriteria() {}\n",
        "",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.pathway-context-fact-bridge.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断诊断 value/time 发布阻断与 findings 去重回归缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeService.java":
      diagnosisKnowledgeServiceContent().replace(
        "    rejectUnsupportedConstraintCriteria(versionCriteria);\n",
        "",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.diagnosis-publish-safety.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断诊断发布安全回归测试缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeServiceTest.java":
      diagnosisKnowledgeServiceTestContent().replace(
        '  void publishGateParsesThirdPartyFindingListsWithoutDuplicateOrBlankNoise() {\n    String caseCode = "CASE-THIRD-PARTY";\n  }\n',
        "",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.diagnosis-publish-safety-test.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断诊断标准术语和证据引用校验缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeService.java":
      diagnosisKnowledgeServiceContent().replace(
        "  void addCriterion() {\n    references.validateCriterion(version, findingTermCode, citationId);\n  }\n",
        "",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.diagnosis-criterion-reference.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断诊断标准引用校验回归测试缺失", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisReferenceValidatorTest.java":
      diagnosisReferenceValidatorTestContent().replace(
        '  void criterionCitationMustBelongToCurrentDiagnosisVersion() {\n    String citation = "findByTenantIdAndId";\n  }\n',
        "",
      ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.diagnosis-criterion-reference-test.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断术语候选生成退回同步明细响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/terminology/TerminologyService.java":
      [
        "class TerminologyService {",
        "  void generateCandidates() {",
        "    localTermRepository.pageByTenantIdAndSourceSystemAndStatus();",
        "    standardTermRepository.pageByTenantIdsAndStatus();",
        "    StandardTermGenerationIndex index = new StandardTermGenerationIndex();",
        "    index.candidatesFor(null, true, java.util.List.of());",
        "    return candidates;",
        "  }",
        "}",
      ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.terminology-generation-async.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断质控指标配置包版本退回手写输入", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/QcEvalSets.tsx": [
      "function QcEvalSets() {",
      '  return <Input aria-label="配置包版本" />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.quality-evaluation-package-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断质控评估配置包固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/QcEvalSets.tsx": [
      "function QcEvalSets() {",
      '  const evaluationPackagesQuery = usePackages({ page: 1, size: 100, assetType: "EVALUATION" });',
      "  const packageOptions = evaluationPackagesQuery.data?.items ?? [];",
      "  return <>",
      '    <Select showSearch optionFilterProp="label" placeholder="选择已存在的评估配置包版本" options={packageOptions} />',
      '    <Select showSearch optionFilterProp="label" placeholder="选择仿真使用的评估配置包版本" options={packageOptions} />',
      "  </>;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.quality-evaluation-package-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断医保审核质控指标固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/InsuranceAudit.tsx": [
      "function InsuranceAudit() {",
      '  const indicatorsQuery = useEvaluationIndicators({ status: "ACTIVE", page: 1, size: 100, sort: "name,asc" }, { enabled: true });',
      "  const indicatorOptions = indicatorsQuery.data?.items ?? [];",
      '  return <Select showSearch optionFilterProp="label" placeholder="选择已生效指标" options={indicatorOptions} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.insurance-audit-indicator-reference.fixed-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断随访计划列表退回固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/clinical/Followup.tsx": [
      "function Followup() {",
      "  const { data: apiPlansData } = useFollowupPlans({ patientId: patientFilter.trim() || undefined, page: 1, size: 100 });",
      "  const displayPlans = apiPlansData?.items ?? [];",
      "  return <Table dataSource={displayPlans} pagination={{ pageSize: 10 }} />;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.followup-plan-list.fixed-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断随访模板选择退回固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/clinical/Followup.tsx": [
      "function Followup() {",
      '  const templatesQuery = useFollowupTemplates({ page: 1, size: 100, sort: "updatedAt,desc" });',
      "  const templates = templatesQuery.data?.items ?? [];",
      "  const publishedTemplates = templates.filter((template) => template.assetStatus === 'PUBLISHED');",
      "  return <Select options={publishedTemplates} />;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.followup-template-reference.fixed-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断随访模板后端退回租户全量内存分页", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/followup/FollowupTemplateService.java":
      [
        "class FollowupTemplateService {",
        "  PageResponse<FollowupTemplateResponse> list(PageRequest pageRequest) {",
        "    List<FollowupTemplate> all = templates.findByTenantIdOrderByUpdatedAtDesc(tenantId());",
        "    return PageResponse.of(all.subList(0, pageRequest.safeSize()), pageRequest, all.size());",
        "  }",
        "}",
      ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.followup-template-reference.backend-tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断身份来源绑定人员选择退回固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/compliance/IdentityBinding.tsx": [
      "function IdentityBinding() {",
      "  usePersonnel({ page: 1, size: 100, keyword: userSearch || undefined });",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.identity-binding-personnel-reference.fixed-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断身份来源绑定列表退回租户全量快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/compliance/identitybinding/IdentityBindingService.java": [
      "class IdentityBindingService {",
      "  List<IdentityBindingResponse> list(String tenantId) {",
      "    return repository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream().map(IdentityBindingResponse::from).toList();",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.identity-binding-list.tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断导出审批列表退回租户全量快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalService.java": [
      "class ExportApprovalService {",
      "  List<ExportApprovalResponse> listApprovals(String tenantId, String resourceType, ExportApprovalStatus status) {",
      "    return repository.findByTenantIdOrderByRequestedAtDesc(tenantId).stream().map(ExportApprovalResponse::from).toList();",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.export-approval-list.tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断导出审批前端退回数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/compliance/AdminAudit.tsx": [
      "function AdminAudit() {",
      '  const approvals = useExportApprovals({ resourceType: "AUDIT_EVENT" }, canApproveExport);',
      "  return <Table dataSource={approvals.data ?? []} pagination={{ pageSize: 20 }} />;",
      "}",
    ].join("\n"),
    "frontend/src/shared/api/hooks.ts": [
      "function fetchExportApprovals(params = {}) {",
      '  return apiClient.get<{ data: ExportApproval[] }>("/compliance/exports", { params });',
      "}",
      "function useExportApprovals(params = {}, enabled = true) { return useQuery({ enabled, queryFn: () => fetchExportApprovals(params) }); }",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.export-approval-list.frontend-array-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断数据权限策略后端退回数组快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionController.java": [
      "class DataPermissionController {",
      "  ApiResult<List<DataPermissionPolicyResponse>> listPolicies(String resourceType, DataPermissionAction action) {",
      "    return ApiResult.ok(service.listPolicies(tenantId, resourceType, action));",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionService.java": [
      "class DataPermissionService {",
      "  List<DataPermissionPolicyResponse> listPolicies(String tenantId, String resourceType, DataPermissionAction action) {",
      "    return repository.findPolicies(tenantId, resourceType, action.name()).stream().map(DataPermissionPolicyResponse::from).toList();",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.security-baseline-policy-ledger.array-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断数据权限与脱敏规则前端退回数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/compliance/SecurityBaselinePanels.tsx": [
      "function DataPermissionPanel() {",
      "  const policies = useDataPermissionPolicies();",
      "  const defaultPolicy = policies.data?.[0];",
      "  return <Table dataSource={policies.data ?? []} pagination={false} />;",
      "}",
      "function MaskingRulePanel() {",
      "  const rules = useMaskingRules();",
      "  const defaultRule = rules.data?.[0];",
      "  return <Table dataSource={rules.data ?? []} pagination={false} />;",
      "}",
    ].join("\n"),
    "frontend/src/shared/api/hooks.ts": [
      "function fetchDataPermissionPolicies(params = {}): Promise<DataPermissionPolicy[]> { return apiClient.get('/compliance/data-permissions'); }",
      "function fetchMaskingRules(params = {}): Promise<MaskingRule[]> { return apiClient.get('/compliance/masking-rules'); }",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.security-baseline-policy-ledger.frontend-array-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断机构知识定制列表退回租户全量快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationService.java": [
      "class KnowledgeCustomizationService {",
      "  List<KnowledgeCustomizationResponse> list() {",
      "    return customizations.findByTenantIdOrderByUpdatedAtDesc(tenantId()).stream().map(this::response).toList();",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-customization-list.tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断机构知识定制本地版本号退回全量历史 size", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationService.java": [
      "class KnowledgeCustomizationService {",
      "  String nextLocalVersionNo(String tenantId, Long identityId, String platformVersionNo) {",
      "    int next = versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId)",
      "      .size() + 1;",
      "    return platformVersionNo + \"-local-\" + next;",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-customization-local-version.identity-version-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-customization-local-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断知识生产候选血缘列表退回 job 全量快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationService.java": [
      "class KnowledgeProductionOrchestrationService {",
      "  List<ProductionCandidateView> listCandidates(String jobCode) {",
      "    return candidateRepository.findByTenantIdAndJobCode(tenantId, jobCode).stream().toList();",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionController.java": [
      "class KnowledgeProductionController {",
      "  ApiResult<List<ProductionCandidateView>> listCandidates(String jobCode) {",
      "    return ApiResult.ok(service.listCandidates(jobCode));",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-production-candidates-list.tenant-job-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-production-candidates-list.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断候选来源溯源请求退回无上限 ref 数组", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateProvenanceRequest.java": [
      "record CandidateProvenanceRequest(",
      "  @NotEmpty",
      "  List<String> candidateRefs",
      ") {}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.candidate-provenance-batch-limit.unbounded-ref-batch-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.candidate-provenance-batch-limit.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断候选来源溯源服务绕过批量上限", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateProvenanceService.java": [
      "class CandidateProvenanceService {",
      "  List<CandidateProvenanceView> resolve(Collection<String> candidateRefs) {",
      "    return candidateRepository.findByTenantIdAndCandidateRefIn(tenantId, candidateRefs);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.candidate-provenance-batch-limit.unbounded-ref-batch-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.candidate-provenance-batch-limit.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断文档解析 job 台账退回数组分页", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocParseJobRepository.java": [
      "interface DocParseJobRepository {",
      "  List<DocParseJob> pageByTenantId(String tenantId, int offset, int limit);",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationService.java": [
      "class DocumentParseOrchestrationService {",
      "  List<DocParseJob> listJobs(int page, int size) {",
      "    return jobRepository.pageByTenantId(tenantId, page * size, size);",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocumentParseController.java": [
      "class DocumentParseController {",
      "  ApiResult<List<DocParseJob>> listJobs(int page, int size) {",
      "    return ApiResult.ok(service.listJobs(page, size));",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.document-parse-job-ledger.array-page-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.document-parse-job-ledger.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断知识异步导出台账退回最近 100 条快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportJobRepository.java": [
      "interface KnowledgeExportJobRepository {",
      "  List<KnowledgeExportJob> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportService.java": [
      "class KnowledgeExportService {",
      "  List<KnowledgeExportJob> listRecent() {",
      "    return jobRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportController.java": [
      "class KnowledgeExportController {",
      "  ApiResult<List<KnowledgeExportJob>> listRecent() {",
      "    return ApiResult.ok(exportService.listRecent());",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-export-job-ledger.top100-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.knowledge-export-job-ledger.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断引擎数据导出台账退回最近 100 条快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportJobRepository.java": [
      "interface EngineDataExportJobRepository {",
      "  List<EngineDataExportJob> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportService.java": [
      "class EngineDataExportService {",
      "  List<EngineDataExportJob> listRecent() {",
      "    return jobRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/EngineDataController.java": [
      "class EngineDataController {",
      "  ApiResult<List<EngineDataExportJob>> listExports() {",
      "    return ApiResult.ok(engineDataExportService.listRecent());",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.engine-data-export-job-ledger.top100-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.engine-data-export-job-ledger.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断知识身份有效分页退回租户与平台全量合并", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java": [
      "class KnowledgeIdentityService {",
      "  PageResponse<KnowledgeIdentity> page(PageRequest request, KnowledgeIdentityFilter filter) {",
      "    List<KnowledgeIdentity> effectiveRows = effectiveIdentitiesByFilter(tenantId, domain, specialtyId, status, keyword);",
      "    List<KnowledgeIdentity> items = slice(effectiveRows, request.offset(), request.safeSize());",
      "    return PageResponse.of(items, request, effectiveRows.size());",
      "  }",
      "  private List<KnowledgeIdentity> slice(List<KnowledgeIdentity> rows, int offset, int limit) { return rows.subList(offset, offset + limit); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-identity-effective-list.tenant-platform-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断规则定义有效分页退回租户与平台全量合并", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java": [
      "class RuleEngineService {",
      "  PageResponse<RuleDefinition> list(RuleFilter filter, PageRequest page) {",
      "    List<RuleDefinition> effectiveRows = effectiveRulesByFilter(tenantId, status, type, risk, keyword);",
      "    List<RuleDefinition> rows = slice(effectiveRows, page.offset(), page.safeSize());",
      "    return PageResponse.of(rows, page, effectiveRows.size());",
      "  }",
      "  private List<RuleDefinition> slice(List<RuleDefinition> rows, int offset, int limit) { return rows.subList(offset, offset + limit); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.rule-definition-effective-list.tenant-platform-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断路径模板有效分页退回租户与平台全量合并", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEngineService.java": [
      "class PathwayEngineService {",
      "  void criteriaContext() { ContextFactBridge.conditionContext(null, null); }",
      "  void contextFacts() { ContextFactBridge.facts(null); }",
      "  PageResponse<PathwayTemplate> listTemplates(PathwayTemplateFilter filter, PageRequest page) {",
      "    List<PathwayTemplate> effectiveRows = effectiveTemplatesByFilter(tenantId, status, diseaseCode, packageId, templateCode, keyword);",
      "    List<PathwayTemplate> rows = slice(effectiveRows, page.offset(), page.safeSize());",
      "    return PageResponse.of(rows, page, effectiveRows.size());",
      "  }",
      "  private List<PathwayTemplate> slice(List<PathwayTemplate> rows, int offset, int limit) { return rows.subList(offset, offset + limit); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.pathway-template-effective-list.tenant-platform-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断知识审核上下文包版本退回候选版本号或手写输入", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/KnowledgeGovernance.tsx": [
      "function KnowledgeGovernance() {",
      "  reviewForm.setFieldsValue({ packageVersion: candidate.versionLabel || candidate.versionNo });",
      '  return <Input aria-label="审核上下文包版本" />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-review-package-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断知识审核上下文配置包固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/quality/KnowledgeGovernance.tsx": [
      "function KnowledgeGovernance() {",
      '  const knowledgePackagesQuery = usePackages({ page: 1, size: 100, assetType: "KNOWLEDGE" });',
      "  const reviewPackageOptions = knowledgePackagesQuery.data?.items ?? [];",
      '  return <Select showSearch optionFilterProp="label" placeholder="选择已存在的知识配置包版本" options={reviewPackageOptions} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-review-package-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断统一资产克隆包版本退回手写输入", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/AuthoringAssets.tsx": [
      "function AuthoringAssets() {",
      '  return <Input aria-label="克隆包版本" />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.authoring-clone-package-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断统一资产克隆配置包固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/AuthoringAssets.tsx": [
      "function AuthoringAssets() {",
      "  const clonePackagesQuery = usePackages({ page: 1, size: 100, assetType: cloneAsset.assetType });",
      "  const clonePackageOptions = clonePackagesQuery.data?.items ?? [];",
      '  return <Select showSearch optionFilterProp="label" placeholder="选择克隆草稿所属配置包版本" options={clonePackageOptions} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.authoring-clone-package-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断统一资产库后端退回租户全量内存分页", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringAssetLibraryService.java": [
      "class AuthoringAssetLibraryService {",
      "  PageResponse<AuthoringAssetLibraryItem> list(PageRequest page) {",
      "    List<FollowupTemplate> rows = followupTemplates.findByTenantIdOrderByUpdatedAtDesc(tenantId);",
      "    return PageResponse.of(rows.subList(0, page.safeSize()), page, rows.size());",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.authoring-asset-library.backend-tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断统一资产库标签收藏过滤退回规则路径全量快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringAssetLibraryService.java": [
      "class AuthoringAssetLibraryService {",
      "  PageResponse<AuthoringAssetLibraryItem> listWithProfileFilters(PageRequest page) {",
      "    rules.listByFilter(tenantId, null, null, null, null);",
      "    pathways.listByFilter(tenantId, null, null, null, null, null);",
      "    return PageResponse.of(List.of(), page, 0);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.authoring-asset-library.backend-tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断批量规则生成规则包固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/AuthoringBatchDrawer.tsx": [
      "function AuthoringBatchDrawer() {",
      '  const rulePackagesQuery = usePackages({ page: 1, size: 100, assetType: "RULE" });',
      '  return <Select placeholder="选择规则配置包版本" options={rulePackagesQuery.data?.items ?? []} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.authoring-batch-rule-package-reference.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断批量任务记录退回最近 50 条数组快照", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobRepository.java": [
      "interface AuthoringBatchJobRepository {",
      "  List<AuthoringBatchJob> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobService.java": [
      "class AuthoringBatchJobService {",
      "  List<AuthoringBatchJobResponse> listRecent() {",
      "    return jobs.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId).stream().toList();",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobController.java": [
      "class AuthoringBatchJobController {",
      "  ApiResult<List<AuthoringBatchJobResponse>> listRecent() {",
      "    return ApiResult.ok(service.listRecent());",
      "  }",
      "}",
    ].join("\n"),
    "frontend/src/shared/api/hooks.ts": apiHooksContent([
      "export function useAuthoringBatchJobs(options?: { enabled?: boolean }) {",
      '  return useQuery({ queryKey: ["authoring", "batch-jobs"], queryFn: async () => {',
      '    const { data } = await apiClient.get<{ data: AuthoringBatchJobResponse[] }>("/engine/authoring/batch");',
      "    return data.data ?? [];",
      "  }});",
      "}",
    ].join("\n")),
    "frontend/src/pages/tenant/AuthoringBatchDrawer.tsx": [
      "const RULE_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
      "function AuthoringBatchDrawer() {",
      "  const jobsQuery = useAuthoringBatchJobs({ enabled: open });",
      "  const [rulePackageSearch, setRulePackageSearch] = useState('');",
      '  const rulePackagesQuery = usePackages({ page: 1, size: RULE_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "RULE" });',
      '  return <Table dataSource={jobsQuery.data ?? []} pagination={false} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.authoring-batch-job-ledger.top50-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.authoring-batch-job-ledger.frontend-array-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.authoring-batch-job-ledger.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断路径结局指标包版本退回路径包或手写输入", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/PathwayTemplates.tsx": [
      "function PathwayTemplates() {",
      '  return <Input aria-label="指标包版本" placeholder="默认使用路径知识包版本" />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.pathway-outcome-package-version.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断路径回滚和结局指标引用固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/PathwayTemplates.tsx": [
      "function PathwayTemplates() {",
      '  usePathwayTemplates({ status: "OFFLINE", templateCode: detailData.template.templateCode, page: 1, size: 100 });',
      '  usePackages({ page: 1, size: 100, assetType: "EVALUATION" });',
      '  useEvaluationIndicators({ status: "ACTIVE", page: 1, size: 100 });',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.pathway-outcome-reference.fixed-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断路径模板维护用模板版本冒充配置包版本", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/PathwayTemplates.tsx": pathwayTemplatesContent(
      "const unsafeFallback = String(detailData?.template.templateVersion ?? values.templateVersion);",
    ),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.pathway-template-package-version.fallback-forbidden",
    ),
  );
});

test("B0 门禁阻断路径模板维护路径包固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/PathwayTemplates.tsx": [
      "function PathwayTemplates() {",
      '  const packagesData = usePackages({ page: 1, size: 100, assetType: "PATHWAY" });',
      '  return <Select placeholder="选择路径知识包" options={packagesData.data?.items ?? []} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.pathway-template-package-reference.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断第三方数据契约配置包固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/AdapterHub.tsx": [
      "function AdapterHub() {",
      "  const packagesQuery = usePackages({ page: 1, size: 100 });",
      '  return <Select showSearch optionFilterProp="label" placeholder="选择已存在配置包版本" options={packagesQuery.data?.items ?? []} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.integration-contract-package-reference.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断第三方接入术语映射固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/AdapterHub.tsx": [
      "function AdapterHub() {",
      '  const terminologyMappingsQuery = useTerminologyMappings({ status: "CONFIRMED", page: 1, size: 100 });',
      '  return <Select showSearch optionFilterProp="label" placeholder="可选，选择已确认映射" options={terminologyMappingsQuery.data?.items ?? []} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.integration-terminology-mapping-reference.fixed-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断第三方适配器目录控制器退回数组响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/controller/IntegrationController.java": [
      "class IntegrationController {",
      "  ApiResult<List<IntegrationAdapter>> getAdapters() {",
      "    return ApiResult.ok(integrationService.getAdapters(tenantId));",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.integration-adapter-list.controller-array-forbidden",
    ),
  );
});

test("B0 门禁阻断第三方适配器目录前端退回全量数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/AdapterHub.tsx": [
      "function AdapterHub() {",
      "  const adaptersQuery = useIntegrationAdapters();",
      "  const adapters = adaptersQuery.data ?? [];",
      "  return <Table dataSource={adapters} pagination={false} />;",
      "}",
    ].join("\n"),
    "frontend/src/shared/api/hooks.ts": [
      "export function useIntegrationAdapters() {",
      "  return useQuery({ queryFn: async () => apiClient.get('/engine/integration/adapters') });",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.integration-adapter-list.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断 AdapterHub 维护台账后端退回数组响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/controller/IntegrationController.java": [
      "class IntegrationController {",
      "  ApiResult<List<IntegrationOnboardingResponse>> listIntegrationOnboardings() { return ApiResult.ok(integrationService.listIntegrationOnboardings(tenantId)); }",
      "  ApiResult<List<WebhookConfigResponse>> getWebhooks() { return ApiResult.ok(integrationService.getWebhooks(tenantId)); }",
      "  ApiResult<List<RegionalSourceResponse>> listRegionalSources() { return ApiResult.ok(integrationService.listRegionalSources(tenantId)); }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/service/IntegrationService.java": [
      "class IntegrationService {",
      "  public List<IntegrationOnboardingResponse> listIntegrationOnboardings(String tenantId) { return List.of(); }",
      "  public List<WebhookConfigResponse> getWebhooks(String tenantId) { return List.of(); }",
      "  public List<RegionalSourceResponse> listRegionalSources(String tenantId) { return List.of(); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.integration-maintenance-ledger.backend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断 AdapterHub 维护台账前端退回数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/AdapterHub.tsx": [
      "function AdapterHub() {",
      "  const onboardingsQuery = useIntegrationOnboardings();",
      "  const webhooksQuery = useWebhooks();",
      "  const regionalSourcesQuery = useRegionalSources();",
      "  const onboardings = onboardingsQuery.data ?? [];",
      "  const webhooks = webhooksQuery.data ?? [];",
      "  const regionalSources = regionalSourcesQuery.data ?? [];",
      "  return <><Table dataSource={onboardings} pagination={false} /><Table dataSource={webhooks} pagination={false} /><Table dataSource={regionalSources} pagination={false} /></>;",
      "}",
    ].join("\n"),
    "frontend/src/shared/api/hooks.ts": [
      "export function useIntegrationOnboardings() {",
      '  return useQuery({ queryFn: async () => apiClient.get<IntegrationEnvelope<IntegrationOnboarding[]>>("/engine/integration/onboardings") });',
      "}",
      "export function useWebhooks() {",
      '  return useQuery({ queryFn: async () => apiClient.get<IntegrationEnvelope<IntegrationWebhookConfig[]>>("/engine/integration/webhooks") });',
      "}",
      "export function useRegionalSources() {",
      '  return useQuery({ queryFn: async () => apiClient.get<IntegrationEnvelope<RegionalSource[]>>("/engine/integration/regional-sources") });',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.integration-maintenance-ledger.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断发布治理覆盖模板后端退回数组响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/versioning/ReleaseGovernanceController.java": [
      "class ReleaseGovernanceController {",
      "  ApiResult<List<OverrideTemplate>> listTemplates() {",
      "    return ApiResult.ok(overrideTemplates.listTemplates(tenantId()));",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/versioning/OverrideTemplateService.java": [
      "class OverrideTemplateService {",
      "  public List<OverrideTemplate> listTemplates(String tenantId) { return List.of(); }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.release-override-template-ledger.backend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断发布治理覆盖模板前端退回数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/ReleaseGovernance.tsx": [
      "function ReleaseGovernance() {",
      "  const templatesQuery = useOverrideTemplates();",
      "  const templateOptions = (templatesQuery.data ?? []).map((template) => template.templateName);",
      "  return <Table dataSource={templatesQuery.data ?? []} pagination={false} />;",
      "}",
    ].join("\n"),
    "frontend/src/shared/api/hooks.ts": [
      "export function useOverrideTemplates() {",
      '  return useQuery({ queryFn: async () => apiClient.get<{ data: OverrideTemplate[] }>("/engine/versioning/releases/override-templates") });',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.release-override-template-ledger.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断知识版本历史后端退回无界数组响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionController.java": [
      "class KnowledgeVersionController {",
      "  ApiResult<List<KnowledgeAssetVersion>> listByIdentity(Long identityId) {",
      "    return ApiResult.ok(versionService.listByIdentity(identityId));",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java": [
      "class KnowledgeVersionService {",
      "  public List<KnowledgeAssetVersion> listByIdentity(Long identityId) {",
      "    return versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-version-history.backend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断知识版本历史前端退回数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/shared/api/hooks.ts": [
      "export function useKnowledgeVersions(identityId?: number) {",
      "  return useQuery({",
      "    queryFn: async () => apiClient.get<{ data: KnowledgeAssetVersion[] }>(`/engine/knowledge/identities/${identityId}/versions`),",
      "  });",
      "}",
    ].join("\n"),
    "frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx": [
      "function DiagnosisKnowledgePanel() {",
      "  const versionsQuery = useKnowledgeVersions(identityId);",
      "  const versions = useMemo(() => versionsQuery.data ?? [], [versionsQuery.data]);",
      "  return <Select options={versions.map((item) => ({ value: item.id }))} />;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-version-history.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断知识来源追溯后端退回全量沿革数组", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeProvenanceResponse.java": [
      "record KnowledgeProvenanceResponse(",
      "  KnowledgeIdentity identity,",
      "  Long currentVersionId,",
      "  List<KnowledgeAssetVersion> versions,",
      "  List<KnowledgeSupersession> supersessions",
      ") {}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeLineage.java": [
      "record KnowledgeLineage(",
      "  KnowledgeIdentity identity,",
      "  List<KnowledgeAssetVersion> versions,",
      "  List<KnowledgeSupersession> supersessions",
      ") {}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java": [
      "class KnowledgeIdentityService {",
      "  KnowledgeProvenanceResponse getProvenance(Long identityId) {",
      "    List<KnowledgeAssetVersion> versions = versionRepository.listByIdentity(effective.sourceTenantId(), identity.id());",
      "    List<KnowledgeSupersession> supersessions = supersessionRepository.findByTenantIdAndIdentityIdOrderByTransitionedAtAsc(effective.sourceTenantId(), identity.id());",
      "    return new KnowledgeProvenanceResponse(identity, currentVersionId, versions, supersessions);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-provenance-history.backend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断知识来源追溯前端退回数组沿革表格", async () => {
  const root = await fixtureRoot({
    "frontend/src/shared/api/hooks.ts": [
      "interface KnowledgeProvenanceResponse {",
      "  versions: KnowledgeAssetVersion[];",
      "  supersessions: KnowledgeSupersession[];",
      "}",
      "function useKnowledgeProvenance(identityId?: number) {",
      '  return useQuery({ queryFn: () => apiClient.get<{ data: KnowledgeProvenanceResponse }>(`/engine/knowledge/identities/${identityId}/provenance`) });',
      "}",
    ].join("\n"),
    "frontend/src/pages/advanced/Provenance.tsx": [
      "function Provenance() {",
      "  const provenanceQuery = useKnowledgeProvenance(selectedIdentityId);",
      "  const activeVersion = provenance.versions.find((version) => version.id === currentVersionId);",
      "  return <Table dataSource={provenance.versions} pagination={false} />;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-provenance-history.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断知识候选审核后端退回全量身份候选数组", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateResponse.java": [
      "record KnowledgeCandidateResponse(",
      "  Long identityId,",
      "  List<KnowledgeAssetVersion> candidates,",
      "  List<CandidateClassification> classifications",
      ") {}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java": [
      "class KnowledgeVersionService {",
      "  KnowledgeCandidateResponse listCandidates(Long identityId) {",
      "    List<CandidateClassification> classifications = candidateClassificationRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(tenantId, identityId);",
      "    List<KnowledgeAssetVersion> candidates = versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId).stream()",
      "      .filter(version -> version.status() == KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW)",
      "      .toList();",
      "    return new KnowledgeCandidateResponse(identityId, candidates, classifications);",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-candidate-review.backend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断知识候选分类退回全量身份版本扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java": [
      "class KnowledgeVersionService {",
      "  KnowledgeCandidateResponse classifyCandidate(Long identityId, KnowledgeVersionCreateRequest request) {",
      "    List<KnowledgeAssetVersion> existingVersions =",
      "      versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId);",
      "    Optional<KnowledgeAssetVersion> duplicate = existingVersions.stream()",
      "      .filter(version -> contentHash.equals(version.contentHash()))",
      "      .findFirst();",
      "    return null;",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-candidate-classification.identity-version-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断知识候选审核前端退回全量数组表格", async () => {
  const root = await fixtureRoot({
    "frontend/src/shared/api/hooks.ts": [
      "interface KnowledgeCandidateResponse {",
      "  candidates: KnowledgeAssetVersion[];",
      "  classifications: CandidateClassification[];",
      "}",
      "function useKnowledgeCandidates(identityId?: number) {",
      '  return useQuery({ queryFn: () => apiClient.get<{ data: KnowledgeCandidateResponse }>(`/engine/knowledge/identities/${identityId}/candidates`) });',
      "}",
    ].join("\n"),
    "frontend/src/pages/quality/KnowledgeGovernance.tsx": [
      "function KnowledgeGovernance() {",
      "  const candidatesQuery = useKnowledgeCandidates(selectedIdentityId);",
      "  const candidates = useMemo(() => candidateResponse?.candidates ?? [], [candidateResponse]);",
      "  return <Table dataSource={candidates} pagination={false} />;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.knowledge-candidate-review.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断配置包同步日志后端退回无界数组响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineController.java": [
      "class PackageEngineController {",
      "  ApiResult<List<SyncLogResponse>> listSyncLogs(String packageId) {",
      "    return ApiResult.ok(service.listSyncLogs(packageId));",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java": [
      "class PackageEngineService {",
      "  public List<SyncLogResponse> listSyncLogs(String packageId) {",
      "    return planRepository.findByTenantIdAndPackageIdOrderByCreatedAtDesc(tenantId, packageId).stream()",
      "      .flatMap(plan -> logRepository.findByTenantIdAndPlanId(tenantId, plan.planId()).stream())",
      "      .map(SyncLogResponse::from)",
      "      .toList();",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/runtime/ThirdPartyPackageReconciliationResponse.java": [
      "record ThirdPartyPackageReconciliationResponse(",
      "  String contractVersion,",
      "  String packageId,",
      "  ThirdPartyReconciliationStatus status,",
      "  List<SyncLogResponse> logs",
      ") {}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.package-sync-log-ledger.backend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断配置包同步日志前端退回数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/shared/api/hooks.ts": [
      "export function usePackageSyncLogs(packageId: string) {",
      "  return useQuery({",
      "    queryFn: async () => apiClient.get<{ data: SyncLogResponse[] }>(`/engine/pkg/packages/${packageId}/sync-logs`),",
      "  });",
      "}",
    ].join("\n"),
    "frontend/src/pages/tenant/ConfigPackages.tsx": [
      "function ConfigPackages() {",
      "  const { data: persistedSyncLogs } = usePackageSyncLogs(effectivePackageId || \"\");",
      "  const visibleSyncLogs = syncLogs.length > 0 ? syncLogs : (persistedSyncLogs ?? []);",
      "  return <Timeline items={visibleSyncLogs.map((log) => ({ key: log.logId }))} />;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.package-sync-log-ledger.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断配置包发布适配器后端退回无界数组响应", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineController.java": [
      "class PackageEngineController {",
      "  ApiResult<List<PackageReleaseAdapterResponse>> listReleaseAdapters() {",
      "    return ApiResult.ok(service.listReleaseAdapters());",
      "  }",
      "}",
    ].join("\n"),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java": [
      "class PackageEngineService {",
      "  public List<PackageReleaseAdapterResponse> listReleaseAdapters() {",
      "    return adapterRepository.findAllByTenantId(tenantId).stream()",
      "      .filter(adapter -> \"ACTIVE\".equalsIgnoreCase(adapter.status()))",
      "      .map(adapter -> PackageReleaseAdapterResponse.from(adapter, syncPort.supports(adapter)))",
      "      .toList();",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.package-release-adapter-list.backend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断配置包发布适配器前端退回数组快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/shared/api/hooks.ts": [
      "export function usePackageReleaseAdapters(enabled = true) {",
      "  return useQuery({",
      "    queryFn: async () => apiClient.get<{ data: PackageReleaseAdapter[] }>(`/engine/pkg/packages/release-adapters`),",
      "  });",
      "}",
    ].join("\n"),
    "frontend/src/pages/tenant/ConfigPackages.tsx": [
      "function ConfigPackages() {",
      "  const { data: releaseAdapters } = usePackageReleaseAdapters();",
      "  const displayAdapters = releaseAdapters ?? [];",
      "  return <Select>{displayAdapters.map((adapter) => <Option key={adapter.adapterId} />)}</Select>;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.package-release-adapter-list.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断规则维护配置包固定快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/RuleDefinitions.tsx": [
      "function RuleDefinitions() {",
      '  const rulePackagesQuery = usePackages({ page: 1, size: 100, assetType: "RULE" });',
      '  return <AutoComplete placeholder="选择规则配置包版本" options={rulePackagesQuery.data?.items ?? []} />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.rule-definition-package-reference.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断规则条件片段库固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/RuleDefinitions.tsx": [
      "function RuleDefinitions() {",
      "  const fragmentLibraryQuery = useConditionFragments({",
      "    packageVersion: currentCreatePackageVersion || undefined,",
      "    page: 1,",
      "    size: 100,",
      '    sort: "fragmentCode,asc",',
      "  });",
      "  return <Table dataSource={fragmentLibraryQuery.data?.items ?? []} pagination={false} />;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
      "b0.rule-condition-fragment-library.fixed-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断条件片段影响分析退回规则路径全量扫描", async () => {
  const root = await fixtureRoot({
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/ConditionFragmentService.java": [
      "class ConditionFragmentService {",
      "  ConditionFragmentImpactResponse impact(String fragmentId) {",
      "    ruleDefinitions.listByFilter(tenantId, null, null, null, null);",
      "    pathwayTemplates.listByFilter(tenantId, null, null, null, null, null);",
      "    return null;",
      "  }",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.condition-fragment-impact.backend-tenant-snapshot-forbidden",
    ),
  );
});

test("B0 门禁阻断条件片段影响抽屉退回数组无分页", async () => {
  const root = await fixtureRoot({
    "frontend/src/shared/api/hooks.ts": apiHooksContent([
      "interface ConditionFragmentImpactResponse { affectedAssets: ConditionFragmentAffectedAsset[]; }",
    ].join("\n")),
    "frontend/src/pages/tenant/RuleDefinitions.tsx": [
      "function RuleDefinitions() {",
      '  return <Modal title="条件片段影响分析"><Table dataSource={conditionFragmentImpactQuery.data?.affectedAssets ?? []} pagination={false} /></Modal>;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.condition-fragment-impact.frontend-array-forbidden",
    ),
  );
});

test("B0 门禁阻断术语映射包发布固定快照和默认首条", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/TerminologyMapping.tsx": [
      "function TerminologyMapping() {",
      '  const packages = usePackages({ page: 0, size: 10, assetType: "TERMINOLOGY" });',
      "  const packageItems = packages.data?.items ?? [];",
      "  const selectedPackage = packageItems[0];",
      "  return <Card title=\"映射包发布\">{selectedPackage?.packageVersion}</Card>;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.terminology-release-package-reference.required-snippet-missing",
    ),
  );
});

test("B0 门禁阻断术语映射维护退回 0 基分页请求", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/TerminologyMapping.tsx": [
      "function TerminologyMapping() {",
      '  const standardTerms = useStandardTerms({ page: 0, size: 20, status: "ACTIVE" });',
      '  const candidates = useTerminologyCandidates({ page: 0, size: 20, status: "PENDING" });',
      "  return <>{standardTerms.data?.total}{candidates.data?.total}</>;",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) => item.ruleId === "b0.terminology-page.one-based-page-required",
    ),
  );
});

test("B0 门禁阻断配置包用默认值或资产版本冒充 packageVersion", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/ConfigPackages.tsx": [
      "function ConfigPackages() {",
      "  const handleApplyPilotTemplateReferences = () => applyPilotTemplateReferences({",
      '    packageVersion: template?.defaultPackageVersion ?? "ONBOARDING",',
      "  });",
      "  const handleAddItem = () => addPackageItem({",
      "    request: { packageVersion: selectedPackage?.packageVersion || values.assetVersion },",
      "  });",
      "  const handleSyncPackage = () => releasePackage({",
      '    request: { packageVersion: selectedPackage?.packageVersion || "" },',
      "  });",
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId === "b0.config-package-version.template-default-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.config-package-version.asset-version-fallback-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.config-package-version.empty-release-fallback-forbidden",
    ),
  );
});

test("B0 门禁阻断配置包包内资产选择退回固定 100 条快照", async () => {
  const root = await fixtureRoot({
    "frontend/src/pages/tenant/ConfigPackages.tsx": [
      "function ConfigPackages() {",
      "  useAuthoringAssets({ assetType: selectedAssetType, size: 100 });",
      "  useEvaluationIndicators({ size: 100 });",
      '  usePackages({ size: 100, assetType: "TERMINOLOGY" });',
      '  return <Select showSearch optionFilterProp="label" placeholder="请选择已发布资产" />;',
      "}",
    ].join("\n"),
  });

  const report = await scanRepository(root);

  assert.equal(hasBlockingViolations(report), true);
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.config-package-item-asset-reference.fixed-authoring-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.config-package-item-asset-reference.fixed-evaluation-snapshot-forbidden",
    ),
  );
  assert.ok(
    report.violations.some(
      (item) =>
        item.ruleId ===
        "b0.config-package-item-asset-reference.fixed-terminology-snapshot-forbidden",
    ),
  );
});
