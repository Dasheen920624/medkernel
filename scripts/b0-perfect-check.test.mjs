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
    "export interface IntegrationAdaptersParams { page?: number; size?: number; }",
    "function useKnowledgeCustomizations(params: KnowledgeCustomizationsParams = {}, enabled = true) {",
    "  const queryParams = { page: params.page ?? 1, size: params.size ?? 20 };",
    '  return useQuery({ queryKey: ["knowledge", "customizations", queryParams], enabled, queryFn: () => apiClient.get<{ data: PageResponse<KnowledgeCustomization> }>("/engine/knowledge/customizations", { params: queryParams }) });',
    "}",
    "function fetchExportApprovals(params: ExportApprovalsParams = {}) {",
    '  return apiClient.get<{ data: PageResponse<ExportApproval> }>("/compliance/exports", { params });',
    "}",
    "function useExportApprovals(params: ExportApprovalsParams = {}, enabled = true) {",
    '  return useQuery({ queryKey: ["compliance", "export-approvals", params], enabled, queryFn: () => fetchExportApprovals(params) });',
    "}",
    "function useIntegrationAdapters(params: IntegrationAdaptersParams = {}) {",
    '  return useQuery({ queryKey: ["integration", "adapters", params], queryFn: () => apiClient.get<IntegrationEnvelope<PageResponse<IntegrationAdapter>>>("/engine/integration/adapters", { params }) });',
    "}",
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
    "  it('loads integration adapters through server pagination', () => {",
    "    useIntegrationAdapters({ page: 2, size: 20 });",
    '    expect(apiClient.get).toHaveBeenCalledWith("/engine/integration/adapters", { params: { page: 2, size: 20 } });',
    "  });",
    "});",
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
    "function KnowledgeGovernance() {",
    "  const [reviewPackageSearch, setReviewPackageSearch] = useState('');",
    "  const [customizationPage, setCustomizationPage] = useState(1);",
    '  const knowledgePackagesQuery = usePackages({ page: 1, size: KNOWLEDGE_REVIEW_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "KNOWLEDGE", keyword: reviewPackageSearch || undefined });',
    "  const customizationsQuery = useKnowledgeCustomizations({ page: customizationPage, size: KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE }, true);",
    "  const reviewPackageOptions = knowledgePackagesQuery.data?.items ?? [];",
    "  const customizationItems = useMemo(() => customizationsQuery.data?.items ?? [], [customizationsQuery.data?.items]);",
    "  return <>",
    '    <Select showSearch filterOption={false} onSearch={setReviewPackageSearch} placeholder="选择已存在的知识配置包版本" options={reviewPackageOptions} />',
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

function knowledgeIdentityServiceContent(extra = "") {
  return [
    "class KnowledgeIdentityService {",
    "  PageResponse<KnowledgeIdentity> page(PageRequest request, KnowledgeIdentityFilter filter) {",
    "    identityRepository.countEffectiveByFilter(tenantId, PlatformTenant.ID, domain, specialtyId, status, platformStatus, keyword);",
    "    identityRepository.pageEffectiveByFilter(tenantId, PlatformTenant.ID, domain, specialtyId, status, platformStatus, keyword, offset, size);",
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
    "  record RepositoryAssetPage(List<AuthoringAssetLibraryItem> items, long total) {}",
    "}",
    extra,
  ].join("\n");
}

function authoringAssetLibraryServiceTestContent(extra = "") {
  return [
    "class AuthoringAssetLibraryServiceTest {",
    "  void listsTypedFollowupAssetsThroughRepositoryPagination() {}",
    "}",
    extra,
  ].join("\n");
}

function authoringBatchDrawerContent(extra = "") {
  return [
    "const RULE_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "function AuthoringBatchDrawer() {",
    "  const [rulePackageSearch, setRulePackageSearch] = useState('');",
    '  const rulePackagesQuery = usePackages({ page: 1, size: RULE_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "RULE", keyword: rulePackageSearch || undefined });',
    '  return <Select showSearch filterOption={false} onSearch={setRulePackageSearch} placeholder="选择规则配置包版本" options={rulePackagesQuery.data?.items ?? []} />;',
    "}",
    extra,
  ].join("\n");
}

function authoringBatchDrawerTestContent(extra = "") {
  return [
    "describe('AuthoringBatchDrawer', () => {",
    "  it('loads rule package selector through small server-side pages', () => {});",
    "});",
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
    "const INTEGRATION_CONTRACT_PACKAGE_REFERENCE_PAGE_SIZE = 20;",
    "const TERMINOLOGY_MAPPING_REFERENCE_PAGE_SIZE = 20;",
    "function AdapterHub() {",
    "  const [adapterPage, setAdapterPage] = useState(1);",
    "  const [contractPackageSearch, setContractPackageSearch] = useState('');",
    "  const [terminologyMappingSearch, setTerminologyMappingSearch] = useState('');",
    "  const onPackageSearch = setContractPackageSearch;",
    "  const adaptersQuery = useIntegrationAdapters({ page: adapterPage, size: ADAPTER_PAGE_SIZE });",
    "  const adapters = adaptersQuery.data?.items ?? [];",
    "  const packagesQuery = usePackages({ page: 1, size: INTEGRATION_CONTRACT_PACKAGE_REFERENCE_PAGE_SIZE, keyword: contractPackageSearch || undefined });",
    '  const terminologyMappingsQuery = useTerminologyMappings({ status: "CONFIRMED", page: 1, size: TERMINOLOGY_MAPPING_REFERENCE_PAGE_SIZE, keyword: terminologyMappingSearch || undefined });',
    "  return <><Table dataSource={adapters} pagination={{ current: adapterPage, pageSize: ADAPTER_PAGE_SIZE, total: adaptersQuery.data?.total ?? 0, onChange: setAdapterPage }} /><Select showSearch filterOption={false} onSearch={onPackageSearch} placeholder=\"选择已存在配置包版本\" options={packagesQuery.data?.items ?? []} /><Select showSearch filterOption={false} onSearch={setTerminologyMappingSearch} onClear={() => setTerminologyMappingSearch(\"\")} placeholder=\"可选，选择已确认映射\" options={terminologyMappingsQuery.data?.items ?? []} /></>;",
    "}",
    extra,
  ].join("\n");
}

function adapterHubTestContent(extra = "") {
  return [
    "describe('AdapterHub', () => {",
    "  it('renders the unified adapter workspace without the old launch-token console', () => { expect(useIntegrationAdapters).toHaveBeenCalledWith({ page: 1, size: 20 }); });",
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
    "function RuleDefinitions() {",
    "  const [rulePackageSearch, setRulePackageSearch] = useState('');",
    "  const [fragmentLibraryPage, setFragmentLibraryPage] = useState(1);",
    "  const [fragmentLibrarySearch, setFragmentLibrarySearch] = useState('');",
    "  const fragmentLibraryKeyword = fragmentLibrarySearch.trim();",
    '  const rulePackagesQuery = usePackages({ page: 1, size: RULE_PACKAGE_REFERENCE_PAGE_SIZE, assetType: "RULE", keyword: rulePackageSearch || undefined });',
    '  const fragmentLibraryQuery = useConditionFragments({ packageVersion: currentCreatePackageVersion || undefined, ...(fragmentLibraryKeyword ? { keyword: fragmentLibraryKeyword } : {}), page: fragmentLibraryPage, size: RULE_FRAGMENT_LIBRARY_PAGE_SIZE, sort: "fragmentCode,asc" });',
    "  const fragmentLibraryItems = fragmentLibraryQuery.data?.items ?? [];",
    '  return <><AutoComplete filterOption={false} onSearch={setRulePackageSearch} placeholder="选择当前已审核的标准上下文包版本" options={rulePackagesQuery.data?.items ?? []} /><AutoComplete filterOption={false} onSearch={setRulePackageSearch} placeholder="选择规则配置包版本" options={rulePackagesQuery.data?.items ?? []} /><Input aria-label="检索条件片段" allowClear value={fragmentLibrarySearch} onChange={(event) => { setFragmentLibrarySearch(event.target.value); setFragmentLibraryPage(1); }} /><Table pagination={{ current: fragmentLibraryQuery.data?.page ?? fragmentLibraryPage, pageSize: fragmentLibraryQuery.data?.size ?? RULE_FRAGMENT_LIBRARY_PAGE_SIZE, total: fragmentLibraryQuery.data?.total ?? fragmentLibraryItems.length, showSizeChanger: false, onChange: (page) => setFragmentLibraryPage(page) }} /></>;',
    "}",
    extra,
  ].join("\n");
}

function ruleDefinitionsTestContent(extra = "") {
  return [
    "describe('RuleDefinitions', () => {",
    "  it('规则包版本选择器通过小页服务端搜索加载', () => {});",
    "  it('条件片段库通过小页服务端搜索加载', () => {});",
    "});",
    extra,
  ].join("\n");
}

function ruleDefinitionRepositoryContent(extra = "") {
  return [
    "interface RuleDefinitionRepository {",
    "  long countEffectiveByFilter(String tenantId, String platformTenantId, String tenantStatus, String platformStatus, String ruleType, String riskLevel, String keyword);",
    "  List<RuleDefinition> pageEffectiveByFilter(String tenantId, String platformTenantId, String tenantStatus, String platformStatus, String ruleType, String riskLevel, String keyword, int offset, int limit);",
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
    "});",
    extra,
  ].join("\n");
}

function configPackagesContent(extra = "") {
  return [
    "const PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE = 20;",
    "function ConfigPackages() {",
    "  const [packageItemAssetSearch, setPackageItemAssetSearch] = useState('');",
    "  const packageItemAssetKeyword = packageItemAssetSearch.trim();",
    "  useAuthoringAssets({ page: 1, size: PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE, keyword: packageItemAssetKeyword });",
    "  useEvaluationIndicators({ status: 'ACTIVE', page: 1, size: PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE, indicatorCode: packageItemAssetKeyword });",
    '  usePackages({ page: 1, size: PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE, assetType: "TERMINOLOGY", keyword: packageItemAssetKeyword });',
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
    "  return <Select showSearch filterOption={false} onSearch={setPackageItemAssetSearch} />;",
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
    "function searchKeyword(value) { return value.trim() || undefined; }",
    "function DiagnosisKnowledgePanel() {",
    "  const [identitySearch, setIdentitySearch] = useState('');",
    "  const [diagnosisReferenceSearch, setDiagnosisReferenceSearch] = useState('');",
    "  const [referenceKnowledgeSearch, setReferenceKnowledgeSearch] = useState('');",
    "  const [ruleSearch, setRuleSearch] = useState('');",
    "  const [pathwaySearch, setPathwaySearch] = useState('');",
    "  useKnowledgeIdentities({ domain: 'DIAGNOSIS', keyword: searchKeyword(identitySearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  useKnowledgeIdentities({ domain: 'DIAGNOSIS', keyword: searchKeyword(diagnosisReferenceSearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  useKnowledgeIdentities({ keyword: searchKeyword(referenceKnowledgeSearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  useRuleDefinitions({ keyword: searchKeyword(ruleSearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  usePathwayTemplates({ keyword: searchKeyword(pathwaySearch), size: DIAGNOSIS_REFERENCE_PAGE_SIZE });",
    "  return <>",
    "    <Select showSearch filterOption={false} onSearch={setIdentitySearch} />",
    "    <Select showSearch filterOption={false} onSearch={setDiagnosisReferenceSearch} />",
    "    <Select showSearch filterOption={false} onSearch={searchCareTarget} />",
    "  </>;",
    "}",
    extra,
  ].join("\n");
}

function diagnosisPanelTestContent(extra = "") {
  return [
    "describe('DiagnosisKnowledgePanel', () => {",
    "  it('loads diagnosis reference selectors through small server-side search pages', () => {});",
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
    "}",
    extra,
  ].join("\n");
}

function integrationServiceTestContent(extra = "") {
  return [
    "class IntegrationServiceTest {",
    "  void testAdapterLifecycle() { PageResponse<IntegrationAdapter> page = service.getAdapters(tenantId, PageRequest.defaults()); }",
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
    "}",
    extra,
  ].join("\n");
}

function packageEngineServiceTestContent(extra = "") {
  return [
    "class PackageEngineServiceTest {",
    "  void getAssetReadinessReflectsReleasedPackagesAndGrayscaleEvidence() { planRepository.countByTenantIdAndStrategyAndStatus(null, null, null); }",
    "  void syncPackageDoesNotAffectOtherPackageCodes() { packageRepository.findByTenantIdAndPackageCodeAndStatus(null, null, null); }",
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
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationRepository.java":
      knowledgeCustomizationRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationService.java":
      knowledgeCustomizationServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationController.java":
      knowledgeCustomizationControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeCustomizationServiceTest.java":
      knowledgeCustomizationServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepository.java":
      knowledgeIdentityRepositoryContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java":
      knowledgeIdentityServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityServiceTest.java":
      knowledgeIdentityServiceTestContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepositoryTest.java":
      knowledgeIdentityRepositoryTestContent(),
    "frontend/src/pages/tenant/AuthoringAssets.tsx": authoringAssetsContent(),
    "frontend/src/pages/tenant/AuthoringAssets.test.tsx":
      authoringAssetsTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringAssetLibraryService.java":
      authoringAssetLibraryServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringAssetLibraryServiceTest.java":
      authoringAssetLibraryServiceTestContent(),
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
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/service/IntegrationService.java":
      integrationServiceContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/integration/controller/IntegrationController.java":
      integrationControllerContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/integration/IntegrationServiceTest.java":
      integrationServiceTestContent(),
    "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java":
      packageEngineServiceContent(),
    "medkernel-backend/src/test/java/com/medkernel/engine/pkg/PackageEngineServiceTest.java":
      packageEngineServiceTestContent(),
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
