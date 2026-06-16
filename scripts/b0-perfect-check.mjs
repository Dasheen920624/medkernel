import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  selectSeedRules,
  validateScenarioRules,
} from "./sandbox/scenario-rules.mjs";

const AUDIT_REPORT =
  "docs/audit/2026-06-15-B0第一阶段全功能核查与完美化改造方案.md";
const HANDOFF = "docs/_HANDOFF.md";
const DEFERRED_ISSUES = "docs/audit/deferred-issues.md";
const KNOWLEDGE_GOVERNANCE =
  "frontend/src/pages/quality/KnowledgeGovernance.tsx";
const KNOWLEDGE_GOVERNANCE_TEST =
  "frontend/src/pages/quality/KnowledgeGovernance.test.tsx";
const API_HOOKS = "frontend/src/shared/api/hooks.ts";
const API_HOOKS_TEST = "frontend/src/shared/api/hooks.test.ts";
const DIAGNOSIS_MAINTENANCE =
  "frontend/src/pages/quality/DiagnosisKnowledgeMaintenance.tsx";
const DIAGNOSIS_PANEL =
  "frontend/src/pages/quality/DiagnosisKnowledgePanel.tsx";
const DIAGNOSIS_PANEL_TEST =
  "frontend/src/pages/quality/DiagnosisKnowledgePanel.test.tsx";
const QC_EVAL_SETS = "frontend/src/pages/quality/QcEvalSets.tsx";
const QC_EVAL_SETS_TEST = "frontend/src/pages/quality/QcEvalSets.test.tsx";
const INSURANCE_AUDIT = "frontend/src/pages/quality/InsuranceAudit.tsx";
const INSURANCE_AUDIT_TEST =
  "frontend/src/pages/quality/InsuranceAudit.test.tsx";
const FOLLOWUP = "frontend/src/pages/clinical/Followup.tsx";
const FOLLOWUP_TEST = "frontend/src/pages/clinical/Followup.test.tsx";
const FOLLOWUP_TEMPLATE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/followup/FollowupTemplateRepository.java";
const FOLLOWUP_TEMPLATE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/followup/FollowupTemplateService.java";
const FOLLOWUP_TEMPLATE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/followup/FollowupTemplateServiceTest.java";
const IDENTITY_BINDING =
  "frontend/src/pages/compliance/IdentityBinding.tsx";
const ADMIN_AUDIT = "frontend/src/pages/compliance/AdminAudit.tsx";
const ADMIN_AUDIT_TEST = "frontend/src/pages/compliance/AdminAudit.test.tsx";
const OPERATIONAL_CONTROL_PAGES_TEST =
  "frontend/src/pages/operationalControlPages.test.tsx";
const IDENTITY_BINDING_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/compliance/identitybinding/IdentityBindingRepository.java";
const IDENTITY_BINDING_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/compliance/identitybinding/IdentityBindingService.java";
const IDENTITY_BINDING_CONTROLLER_TEST =
  "medkernel-backend/src/test/java/com/medkernel/compliance/identitybinding/IdentityBindingControllerTest.java";
const EXPORT_APPROVAL_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalRepository.java";
const EXPORT_APPROVAL_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalService.java";
const EXPORT_APPROVAL_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/compliance/exportapproval/ExportApprovalController.java";
const EXPORT_APPROVAL_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/compliance/exportapproval/ExportApprovalServiceTest.java";
const EXPORT_APPROVAL_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/compliance/exportapproval/ExportApprovalControllerSecurityTest.java";
const KNOWLEDGE_CUSTOMIZATION_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationRepository.java";
const KNOWLEDGE_CUSTOMIZATION_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationService.java";
const KNOWLEDGE_CUSTOMIZATION_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationController.java";
const KNOWLEDGE_CUSTOMIZATION_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeCustomizationServiceTest.java";
const KNOWLEDGE_IDENTITY_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepository.java";
const KNOWLEDGE_IDENTITY_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java";
const KNOWLEDGE_IDENTITY_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityServiceTest.java";
const KNOWLEDGE_IDENTITY_REPOSITORY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepositoryTest.java";
const PATIENT_PATHWAYS = "frontend/src/pages/clinical/PatientPathways.tsx";
const PATIENT_PATHWAYS_TEST =
  "frontend/src/pages/clinical/PatientPathways.test.tsx";
const AUTHORING_ASSETS = "frontend/src/pages/tenant/AuthoringAssets.tsx";
const AUTHORING_ASSETS_TEST =
  "frontend/src/pages/tenant/AuthoringAssets.test.tsx";
const AUTHORING_ASSET_LIBRARY_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringAssetLibraryService.java";
const AUTHORING_ASSET_LIBRARY_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringAssetLibraryServiceTest.java";
const AUTHORING_BATCH_DRAWER =
  "frontend/src/pages/tenant/AuthoringBatchDrawer.tsx";
const AUTHORING_BATCH_DRAWER_TEST =
  "frontend/src/pages/tenant/AuthoringBatchDrawer.test.tsx";
const PATHWAY_TEMPLATES = "frontend/src/pages/tenant/PathwayTemplates.tsx";
const PATHWAY_TEMPLATES_TEST =
  "frontend/src/pages/tenant/PathwayTemplates.test.tsx";
const ADAPTER_HUB = "frontend/src/pages/tenant/AdapterHub.tsx";
const ADAPTER_HUB_TEST = "frontend/src/pages/tenant/AdapterHub.test.tsx";
const RULE_DEFINITIONS = "frontend/src/pages/tenant/RuleDefinitions.tsx";
const RULE_DEFINITIONS_TEST =
  "frontend/src/pages/tenant/RuleDefinitions.test.tsx";
const RULE_DEFINITION_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleDefinitionRepository.java";
const RULE_ENGINE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java";
const RULE_ENGINE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineServiceTest.java";
const RULE_REPOSITORY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleRepositoryTest.java";
const TERMINOLOGY_MAPPING =
  "frontend/src/pages/tenant/TerminologyMapping.tsx";
const TERMINOLOGY_MAPPING_TEST =
  "frontend/src/pages/tenant/TerminologyMapping.test.tsx";
const CONFIG_PACKAGES = "frontend/src/pages/tenant/ConfigPackages.tsx";
const CONFIG_PACKAGES_TEST =
  "frontend/src/pages/tenant/ConfigPackages.test.tsx";
const ROUTES = "frontend/src/shared/config/routes.ts";
const SANDBOX_RULES = "scripts/sandbox/scenario-rules.json";
const PLAYWRIGHT_SCREENSHOT_CHAIN = "frontend/e2e/b0-screenshot-chain.spec.ts";
const LARGE_SCALE_DIALECT_SMOKE =
  "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java";
const KNOWLEDGE_EXPORT_LARGE_SCALE =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeExportServiceLargeScaleTest.java";
const TERMINOLOGY_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/terminology/TerminologyService.java";
const TERMINOLOGY_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/terminology/TerminologyController.java";
const TERMINOLOGY_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyServiceTest.java";
const TERMINOLOGY_API_CONTRACT =
  "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyApiContractTest.java";
const TERMINOLOGY_CANDIDATE_GENERATION_JOB =
  "medkernel-backend/src/main/java/com/medkernel/engine/terminology/TerminologyCandidateGenerationJob.java";
const TERMINOLOGY_LARGE_SCALE =
  "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyRepositoryLargeScaleTest.java";
const CONTEXT_FACT_BRIDGE =
  "medkernel-backend/src/main/java/com/medkernel/engine/context/ContextFactBridge.java";
const PATHWAY_ENGINE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEngineService.java";
const PATHWAY_ENGINE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineServiceTest.java";
const PATHWAY_TEMPLATE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayTemplateRepository.java";
const KNOWLEDGE_PACKAGE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/pkg/KnowledgePackageRepository.java";
const RELEASE_PLAN_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/pkg/ReleasePlanRepository.java";
const TENANT_PACKAGE_REFERENCE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/pkg/TenantPackageReferenceRepository.java";
const ORG_UNIT_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/org/OrgUnitRepository.java";
const PLATFORM_CREDENTIAL_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/security/PlatformCredentialRepository.java";
const USER_ROLE_ASSIGNMENT_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/security/UserRoleAssignmentRepository.java";
const INTEGRATION_ADAPTER_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/IntegrationAdapterRepository.java";
const INTEGRATION_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/service/IntegrationService.java";
const INTEGRATION_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/controller/IntegrationController.java";
const INTEGRATION_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/integration/IntegrationServiceTest.java";
const PACKAGE_ENGINE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java";
const PACKAGE_ENGINE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/pkg/PackageEngineServiceTest.java";
const TENANT_PILOT_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/tenant/TenantPilotService.java";
const TENANT_PILOT_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/tenant/TenantPilotServiceTest.java";
const MPI_MERGE_REVIEW_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiMergeReviewRepository.java";
const MPI_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiService.java";
const MPI_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/mpi/MpiController.java";
const MPI_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/mpi/MpiServiceTest.java";
const MPI_CONTROLLER_CONTRACT_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/mpi/MpiControllerContractTest.java";
const PATHWAY_REPOSITORY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayRepositoryTest.java";
const DIAGNOSIS_KNOWLEDGE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeService.java";
const DIAGNOSIS_REFERENCE_VALIDATOR =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisReferenceValidator.java";
const DIAGNOSIS_KNOWLEDGE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisKnowledgeServiceTest.java";
const DIAGNOSIS_REFERENCE_VALIDATOR_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/diagnosis/DiagnosisReferenceValidatorTest.java";
const CITATION_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/CitationRepository.java";
const TERMINOLOGY_JOB_MIGRATIONS = [
  "h2",
  "postgres",
  "oracle",
  "dm",
  "kingbase",
].map(
  (dialect) =>
    `medkernel-backend/src/main/resources/db/migration/${dialect}/V135__terminology_candidate_generation_job.sql`,
);

function readRequired(root, file, violations) {
  const fullPath = resolve(root, file);
  if (!existsSync(fullPath)) {
    violations.push({
      file,
      line: 1,
      ruleId: "b0.required-file.missing",
      message: "B0 完美化门禁所需文件不存在。",
    });
    return "";
  }
  return readFileSync(fullPath, "utf8");
}

function lineOf(content, pattern) {
  const index =
    typeof pattern === "string"
      ? content.indexOf(pattern)
      : content.search(pattern);
  if (index < 0) return 1;
  return content.slice(0, index).split(/\r?\n/).length;
}

function pushMissing(violations, file, content, needle, ruleId, message) {
  if (!content.includes(needle)) {
    violations.push({ file, line: 1, ruleId, message });
  }
}

export async function scanRepository(root = process.cwd()) {
  const violations = [];
  const governance = readRequired(root, KNOWLEDGE_GOVERNANCE, violations);
  const governanceTest = readRequired(
    root,
    KNOWLEDGE_GOVERNANCE_TEST,
    violations,
  );
  const apiHooks = readRequired(root, API_HOOKS, violations);
  const apiHooksTest = readRequired(root, API_HOOKS_TEST, violations);
  const diagnosisPage = readRequired(root, DIAGNOSIS_MAINTENANCE, violations);
  const diagnosisPanel = readRequired(root, DIAGNOSIS_PANEL, violations);
  const diagnosisPanelTest = readRequired(
    root,
    DIAGNOSIS_PANEL_TEST,
    violations,
  );
  const qcEvalSets = readRequired(root, QC_EVAL_SETS, violations);
  const qcEvalSetsTest = readRequired(root, QC_EVAL_SETS_TEST, violations);
  const insuranceAudit = readRequired(root, INSURANCE_AUDIT, violations);
  const insuranceAuditTest = readRequired(
    root,
    INSURANCE_AUDIT_TEST,
    violations,
  );
  const followup = readRequired(root, FOLLOWUP, violations);
  const followupTest = readRequired(root, FOLLOWUP_TEST, violations);
  const followupTemplateRepository = readRequired(
    root,
    FOLLOWUP_TEMPLATE_REPOSITORY,
    violations,
  );
  const followupTemplateService = readRequired(
    root,
    FOLLOWUP_TEMPLATE_SERVICE,
    violations,
  );
  const followupTemplateServiceTest = readRequired(
    root,
    FOLLOWUP_TEMPLATE_SERVICE_TEST,
    violations,
  );
  const identityBinding = readRequired(root, IDENTITY_BINDING, violations);
  const adminAudit = readRequired(root, ADMIN_AUDIT, violations);
  const adminAuditTest = readRequired(root, ADMIN_AUDIT_TEST, violations);
  const operationalControlPagesTest = readRequired(
    root,
    OPERATIONAL_CONTROL_PAGES_TEST,
    violations,
  );
  const identityBindingRepository = readRequired(
    root,
    IDENTITY_BINDING_REPOSITORY,
    violations,
  );
  const identityBindingService = readRequired(
    root,
    IDENTITY_BINDING_SERVICE,
    violations,
  );
  const identityBindingControllerTest = readRequired(
    root,
    IDENTITY_BINDING_CONTROLLER_TEST,
    violations,
  );
  const exportApprovalRepository = readRequired(
    root,
    EXPORT_APPROVAL_REPOSITORY,
    violations,
  );
  const exportApprovalService = readRequired(
    root,
    EXPORT_APPROVAL_SERVICE,
    violations,
  );
  const exportApprovalController = readRequired(
    root,
    EXPORT_APPROVAL_CONTROLLER,
    violations,
  );
  const exportApprovalServiceTest = readRequired(
    root,
    EXPORT_APPROVAL_SERVICE_TEST,
    violations,
  );
  const exportApprovalControllerSecurityTest = readRequired(
    root,
    EXPORT_APPROVAL_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const knowledgeCustomizationRepository = readRequired(
    root,
    KNOWLEDGE_CUSTOMIZATION_REPOSITORY,
    violations,
  );
  const knowledgeCustomizationService = readRequired(
    root,
    KNOWLEDGE_CUSTOMIZATION_SERVICE,
    violations,
  );
  const knowledgeCustomizationController = readRequired(
    root,
    KNOWLEDGE_CUSTOMIZATION_CONTROLLER,
    violations,
  );
  const knowledgeCustomizationServiceTest = readRequired(
    root,
    KNOWLEDGE_CUSTOMIZATION_SERVICE_TEST,
    violations,
  );
  const knowledgeIdentityRepository = readRequired(
    root,
    KNOWLEDGE_IDENTITY_REPOSITORY,
    violations,
  );
  const knowledgeIdentityService = readRequired(
    root,
    KNOWLEDGE_IDENTITY_SERVICE,
    violations,
  );
  const knowledgeIdentityServiceTest = readRequired(
    root,
    KNOWLEDGE_IDENTITY_SERVICE_TEST,
    violations,
  );
  const knowledgeIdentityRepositoryTest = readRequired(
    root,
    KNOWLEDGE_IDENTITY_REPOSITORY_TEST,
    violations,
  );
  const patientPathways = readRequired(root, PATIENT_PATHWAYS, violations);
  const patientPathwaysTest = readRequired(
    root,
    PATIENT_PATHWAYS_TEST,
    violations,
  );
  const authoringAssets = readRequired(root, AUTHORING_ASSETS, violations);
  const authoringAssetsTest = readRequired(
    root,
    AUTHORING_ASSETS_TEST,
    violations,
  );
  const authoringAssetLibraryService = readRequired(
    root,
    AUTHORING_ASSET_LIBRARY_SERVICE,
    violations,
  );
  const authoringAssetLibraryServiceTest = readRequired(
    root,
    AUTHORING_ASSET_LIBRARY_SERVICE_TEST,
    violations,
  );
  const authoringBatchDrawer = readRequired(
    root,
    AUTHORING_BATCH_DRAWER,
    violations,
  );
  const authoringBatchDrawerTest = readRequired(
    root,
    AUTHORING_BATCH_DRAWER_TEST,
    violations,
  );
  const pathwayTemplates = readRequired(root, PATHWAY_TEMPLATES, violations);
  const pathwayTemplatesTest = readRequired(
    root,
    PATHWAY_TEMPLATES_TEST,
    violations,
  );
  const adapterHub = readRequired(root, ADAPTER_HUB, violations);
  const adapterHubTest = readRequired(root, ADAPTER_HUB_TEST, violations);
  const ruleDefinitions = readRequired(root, RULE_DEFINITIONS, violations);
  const ruleDefinitionsTest = readRequired(
    root,
    RULE_DEFINITIONS_TEST,
    violations,
  );
  const ruleDefinitionRepository = readRequired(
    root,
    RULE_DEFINITION_REPOSITORY,
    violations,
  );
  const ruleEngineService = readRequired(root, RULE_ENGINE_SERVICE, violations);
  const ruleEngineServiceTest = readRequired(
    root,
    RULE_ENGINE_SERVICE_TEST,
    violations,
  );
  const ruleRepositoryTest = readRequired(
    root,
    RULE_REPOSITORY_TEST,
    violations,
  );
  const terminologyMapping = readRequired(
    root,
    TERMINOLOGY_MAPPING,
    violations,
  );
  const terminologyMappingTest = readRequired(
    root,
    TERMINOLOGY_MAPPING_TEST,
    violations,
  );
  const configPackages = readRequired(root, CONFIG_PACKAGES, violations);
  const configPackagesTest = readRequired(
    root,
    CONFIG_PACKAGES_TEST,
    violations,
  );
  const routes = readRequired(root, ROUTES, violations);
  const handoff = readRequired(root, HANDOFF, violations);
  const deferredIssues = readRequired(root, DEFERRED_ISSUES, violations);
  const report = readRequired(root, AUDIT_REPORT, violations);
  const sandboxRules = readRequired(root, SANDBOX_RULES, violations);
  const screenshotChain = readRequired(
    root,
    PLAYWRIGHT_SCREENSHOT_CHAIN,
    violations,
  );
  const largeScaleDialectSmoke = readRequired(
    root,
    LARGE_SCALE_DIALECT_SMOKE,
    violations,
  );
  const knowledgeExportLargeScale = readRequired(
    root,
    KNOWLEDGE_EXPORT_LARGE_SCALE,
    violations,
  );
  const terminologyService = readRequired(
    root,
    TERMINOLOGY_SERVICE,
    violations,
  );
  const terminologyController = readRequired(
    root,
    TERMINOLOGY_CONTROLLER,
    violations,
  );
  const terminologyServiceTest = readRequired(
    root,
    TERMINOLOGY_SERVICE_TEST,
    violations,
  );
  const terminologyApiContract = readRequired(
    root,
    TERMINOLOGY_API_CONTRACT,
    violations,
  );
  const terminologyCandidateGenerationJob = readRequired(
    root,
    TERMINOLOGY_CANDIDATE_GENERATION_JOB,
    violations,
  );
  const terminologyLargeScale = readRequired(
    root,
    TERMINOLOGY_LARGE_SCALE,
    violations,
  );
  const contextFactBridge = readRequired(root, CONTEXT_FACT_BRIDGE, violations);
  const pathwayEngineService = readRequired(
    root,
    PATHWAY_ENGINE_SERVICE,
    violations,
  );
  const pathwayEngineServiceTest = readRequired(
    root,
    PATHWAY_ENGINE_SERVICE_TEST,
    violations,
  );
  const pathwayTemplateRepository = readRequired(
    root,
    PATHWAY_TEMPLATE_REPOSITORY,
    violations,
  );
  const knowledgePackageRepository = readRequired(
    root,
    KNOWLEDGE_PACKAGE_REPOSITORY,
    violations,
  );
  const releasePlanRepository = readRequired(
    root,
    RELEASE_PLAN_REPOSITORY,
    violations,
  );
  const tenantPackageReferenceRepository = readRequired(
    root,
    TENANT_PACKAGE_REFERENCE_REPOSITORY,
    violations,
  );
  const orgUnitRepository = readRequired(root, ORG_UNIT_REPOSITORY, violations);
  const platformCredentialRepository = readRequired(
    root,
    PLATFORM_CREDENTIAL_REPOSITORY,
    violations,
  );
  const userRoleAssignmentRepository = readRequired(
    root,
    USER_ROLE_ASSIGNMENT_REPOSITORY,
    violations,
  );
  const integrationAdapterRepository = readRequired(
    root,
    INTEGRATION_ADAPTER_REPOSITORY,
    violations,
  );
  const integrationService = readRequired(root, INTEGRATION_SERVICE, violations);
  const integrationController = readRequired(
    root,
    INTEGRATION_CONTROLLER,
    violations,
  );
  const integrationServiceTest = readRequired(
    root,
    INTEGRATION_SERVICE_TEST,
    violations,
  );
  const packageEngineService = readRequired(
    root,
    PACKAGE_ENGINE_SERVICE,
    violations,
  );
  const packageEngineServiceTest = readRequired(
    root,
    PACKAGE_ENGINE_SERVICE_TEST,
    violations,
  );
  const tenantPilotService = readRequired(root, TENANT_PILOT_SERVICE, violations);
  const tenantPilotServiceTest = readRequired(
    root,
    TENANT_PILOT_SERVICE_TEST,
    violations,
  );
  const mpiMergeReviewRepository = readRequired(
    root,
    MPI_MERGE_REVIEW_REPOSITORY,
    violations,
  );
  const mpiService = readRequired(root, MPI_SERVICE, violations);
  const mpiController = readRequired(root, MPI_CONTROLLER, violations);
  const mpiServiceTest = readRequired(root, MPI_SERVICE_TEST, violations);
  const mpiControllerContractTest = readRequired(
    root,
    MPI_CONTROLLER_CONTRACT_TEST,
    violations,
  );
  const pathwayRepositoryTest = readRequired(
    root,
    PATHWAY_REPOSITORY_TEST,
    violations,
  );
  const diagnosisKnowledgeService = readRequired(
    root,
    DIAGNOSIS_KNOWLEDGE_SERVICE,
    violations,
  );
  const diagnosisReferenceValidator = readRequired(
    root,
    DIAGNOSIS_REFERENCE_VALIDATOR,
    violations,
  );
  const diagnosisKnowledgeServiceTest = readRequired(
    root,
    DIAGNOSIS_KNOWLEDGE_SERVICE_TEST,
    violations,
  );
  const diagnosisReferenceValidatorTest = readRequired(
    root,
    DIAGNOSIS_REFERENCE_VALIDATOR_TEST,
    violations,
  );
  const citationRepository = readRequired(
    root,
    CITATION_REPOSITORY,
    violations,
  );
  const terminologyJobMigrations = TERMINOLOGY_JOB_MIGRATIONS.map((file) => ({
    file,
    content: readRequired(root, file, violations),
  }));

  if (/\bDiagnosisKnowledgePanel\b/.test(governance)) {
    violations.push({
      file: KNOWLEDGE_GOVERNANCE,
      line: lineOf(governance, "DiagnosisKnowledgePanel"),
      ruleId: "b0.knowledge-governance.mixed-diagnosis-maintenance",
      message: "知识审核与发布页禁止重新混入诊断知识维护面板。",
    });
  }
  if (governance.includes("维护结构化诊断知识")) {
    violations.push({
      file: KNOWLEDGE_GOVERNANCE,
      line: lineOf(governance, "维护结构化诊断知识"),
      ruleId: "b0.knowledge-governance.mixed-diagnosis-maintenance-copy",
      message: "知识审核与发布页文案禁止宣称承载诊断知识维护。",
    });
  }
  if (/\bsize:\s*100\b/.test(diagnosisPanel)) {
    violations.push({
      file: DIAGNOSIS_PANEL,
      line: lineOf(diagnosisPanel, /\bsize:\s*100\b/),
      ruleId: "b0.diagnosis-reference-search.fixed-page-size",
      message:
        "诊断维护引用选择器不得回退为 100 条固定快照，必须使用可搜索服务端分页。",
    });
  }
  for (const snippet of [
    "DIAGNOSIS_REFERENCE_PAGE_SIZE = 20",
    "keyword: searchKeyword(identitySearch)",
    "keyword: searchKeyword(diagnosisReferenceSearch)",
    "keyword: searchKeyword(referenceKnowledgeSearch)",
    "keyword: searchKeyword(ruleSearch)",
    "keyword: searchKeyword(pathwaySearch)",
    "filterOption={false}",
    "onSearch={setIdentitySearch}",
    "onSearch={setDiagnosisReferenceSearch}",
    "onSearch={searchCareTarget}",
  ]) {
    if (!diagnosisPanel.includes(snippet)) {
      violations.push({
        file: DIAGNOSIS_PANEL,
        line: 1,
        ruleId: "b0.diagnosis-reference-search.required-snippet-missing",
        message: `诊断维护引用选择器必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !diagnosisPanelTest.includes(
      "loads diagnosis reference selectors through small server-side search pages",
    )
  ) {
    violations.push({
      file: DIAGNOSIS_PANEL_TEST,
      line: 1,
      ruleId: "b0.diagnosis-reference-search-test.required-snippet-missing",
      message: "诊断维护引用选择器必须保留小页服务端搜索回归测试。",
    });
  }
  if (/\bsize:\s*100\b/.test(patientPathways)) {
    violations.push({
      file: PATIENT_PATHWAYS,
      line: lineOf(patientPathways, /\bsize:\s*100\b/),
      ruleId: "b0.patient-pathway-reference-search.fixed-page-size",
      message: "患者路径入径模板和路径包引用不得回退为 100 条固定快照。",
    });
  }
  if (patientPathways.includes("String(template.templateVersion)")) {
    violations.push({
      file: PATIENT_PATHWAYS,
      line: lineOf(patientPathways, "String(template.templateVersion)"),
      ruleId: "b0.patient-pathway-package-version.unsafe-fallback",
      message:
        "患者路径推进不得用模板版本冒充配置包版本，缺真实包版本时必须阻断。",
    });
  }
  for (const snippet of [
    "PATHWAY_REFERENCE_PAGE_SIZE = 20",
    "keyword: searchKeyword(enterTemplateSearch)",
    "keyword: templateDetail?.template.packageId || undefined",
    "requireSelectedTemplatePackageVersion",
    "无法确认当前路径模板所属的配置包版本",
    "filterOption={false}",
    "onSearch={setEnterTemplateSearch}",
  ]) {
    if (!patientPathways.includes(snippet)) {
      violations.push({
        file: PATIENT_PATHWAYS,
        line: 1,
        ruleId: "b0.patient-pathway-reference-search.required-snippet-missing",
        message: `患者路径模板引用和推进包版本必须保持小页服务端搜索与安全阻断：${snippet}`,
      });
    }
  }
  for (const snippet of [
    "loads published pathway references through small server-side search pages",
    "blocks pathway advancement when the template package version cannot be resolved",
  ]) {
    if (!patientPathwaysTest.includes(snippet)) {
      violations.push({
        file: PATIENT_PATHWAYS_TEST,
        line: 1,
        ruleId:
          "b0.patient-pathway-reference-search-test.required-snippet-missing",
        message: `患者路径模板引用和包版本安全阻断缺少回归测试：${snippet}`,
      });
    }
  }
  if (
    !knowledgePackageRepository.includes("LOWER(kp.package_id) LIKE :keyword")
  ) {
    violations.push({
      file: KNOWLEDGE_PACKAGE_REPOSITORY,
      line: 1,
      ruleId: "b0.package-reference-search.required-snippet-missing",
      message:
        "配置包 keyword 搜索必须覆盖 package_id，供运行侧按模板 packageId 精准解析包版本。",
    });
  }
  if (
    !knowledgePackageRepository.includes("findByTenantIdAndPackageCodeAndStatus")
  ) {
    violations.push({
      file: KNOWLEDGE_PACKAGE_REPOSITORY,
      line: 1,
      ruleId: "b0.package-engine-service.repository-required-snippet-missing",
      message:
        "配置包发布切换必须支持按租户、packageCode 和状态精确查询 ACTIVE 包。",
    });
  }
  if (packageEngineService.includes("findByTenantIdOrderByUpdatedAtDesc(")) {
    violations.push({
      file: PACKAGE_ENGINE_SERVICE,
      line: lineOf(packageEngineService, "findByTenantIdOrderByUpdatedAtDesc("),
      ruleId: "b0.package-engine-service.tenant-snapshot-forbidden",
      message:
        "配置包资产准备和发布切换不得全量读取租户配置包后内存过滤，必须使用状态计数和同编码精确查询。",
    });
  }
  for (const snippet of [
    "KnowledgePackageStatus.DRAFT.name()",
    "KnowledgePackageStatus.PUBLISHED.name()",
    "KnowledgePackageStatus.ACTIVE.name()",
    "findFirstByTenantIdAndStatusOrderByUpdatedAtDesc",
    "findByTenantIdAndPackageCodeAndStatus",
  ]) {
    if (!packageEngineService.includes(snippet)) {
      violations.push({
        file: PACKAGE_ENGINE_SERVICE,
        line: 1,
        ruleId: "b0.package-engine-service.required-snippet-missing",
        message: `配置包服务必须保持状态计数和同编码 ACTIVE 精确查询：${snippet}`,
      });
    }
  }
  for (const snippet of [
    "getAssetReadinessReflectsReleasedPackagesAndGrayscaleEvidence",
    "syncPackageDoesNotAffectOtherPackageCodes",
    "findByTenantIdAndPackageCodeAndStatus",
  ]) {
    if (!packageEngineServiceTest.includes(snippet)) {
      violations.push({
        file: PACKAGE_ENGINE_SERVICE_TEST,
        line: 1,
        ruleId: "b0.package-engine-service-test.required-snippet-missing",
        message: `配置包服务状态计数/同编码发布切换缺少回归测试：${snippet}`,
      });
    }
  }
  if (
    packageEngineService.includes("findByTenantIdOrderByCreatedAtDesc(") ||
    tenantPilotService.includes("findByTenantIdOrderByCreatedAtDesc(")
  ) {
    const file = packageEngineService.includes("findByTenantIdOrderByCreatedAtDesc(")
      ? PACKAGE_ENGINE_SERVICE
      : TENANT_PILOT_SERVICE;
    const content = file === PACKAGE_ENGINE_SERVICE ? packageEngineService : tenantPilotService;
    violations.push({
      file,
      line: lineOf(content, "findByTenantIdOrderByCreatedAtDesc("),
      ruleId: "b0.release-plan-grayscale-readiness.tenant-snapshot-forbidden",
      message:
        "灰度就绪判断不得全量读取租户发布计划后内存 anyMatch，必须按租户、策略和状态做计数/存在式查询。",
    });
  }
  for (const [file, content, snippet] of [
    [
      RELEASE_PLAN_REPOSITORY,
      releasePlanRepository,
      "countByTenantIdAndStrategyAndStatus",
    ],
    [
      PACKAGE_ENGINE_SERVICE,
      packageEngineService,
      "planRepository.countByTenantIdAndStrategyAndStatus",
    ],
    [
      TENANT_PILOT_SERVICE,
      tenantPilotService,
      "releasePlanRepository.countByTenantIdAndStrategyAndStatus",
    ],
    [
      PACKAGE_ENGINE_SERVICE_TEST,
      packageEngineServiceTest,
      "countByTenantIdAndStrategyAndStatus",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.release-plan-grayscale-readiness.required-snippet-missing",
        message: `灰度就绪判断缺少计数查询或回归测试片段：${snippet}`,
      });
    }
  }
  if (
    tenantPilotService.includes("packageRepository.findByTenantIdOrderByUpdatedAtDesc")
  ) {
    violations.push({
      file: TENANT_PILOT_SERVICE,
      line: lineOf(
        tenantPilotService,
        "packageRepository.findByTenantIdOrderByUpdatedAtDesc",
      ),
      ruleId: "b0.tenant-pilot-readiness.package-snapshot-forbidden",
      message:
        "租户开通就绪检查不得全量读取租户配置包后内存判断发布资产，必须使用仓储计数/存在式查询。",
    });
  }
  if (
    tenantPilotService.includes(
      "packageReferenceRepository.findByTenantIdAndStatusOrderByUpdatedAtDesc",
    )
  ) {
    violations.push({
      file: TENANT_PILOT_SERVICE,
      line: lineOf(
        tenantPilotService,
        "packageReferenceRepository.findByTenantIdAndStatusOrderByUpdatedAtDesc",
      ),
      ruleId:
        "b0.tenant-pilot-readiness.package-reference-snapshot-forbidden",
      message:
        "租户开通就绪检查不得全量读取平台包引用后判断是否存在 ACTIVE 引用，必须使用仓储计数查询。",
    });
  }
  if (tenantPilotService.includes("findByTenantIdAndLevelOrderByCodeAsc")) {
    violations.push({
      file: TENANT_PILOT_SERVICE,
      line: lineOf(tenantPilotService, "findByTenantIdAndLevelOrderByCodeAsc"),
      ruleId: "b0.tenant-pilot-readiness.organization-snapshot-forbidden",
      message:
        "租户开通组织就绪检查不得全量读取机构节点后内存判断 ACTIVE 机构，必须使用仓储计数/存在式查询。",
    });
  }
  if (tenantPilotService.includes("credentialRepository.findByTenantIdOrderByUsernameAsc")) {
    violations.push({
      file: TENANT_PILOT_SERVICE,
      line: lineOf(tenantPilotService, "credentialRepository.findByTenantIdOrderByUsernameAsc"),
      ruleId: "b0.tenant-pilot-readiness.user-snapshot-forbidden",
      message:
        "租户开通用户就绪检查不得全量读取用户凭证后内存判断 ACTIVE 用户，必须使用仓储计数/存在式查询。",
    });
  }
  if (tenantPilotService.includes("roleAssignmentRepository.findByTenantId(")) {
    violations.push({
      file: TENANT_PILOT_SERVICE,
      line: lineOf(tenantPilotService, "roleAssignmentRepository.findByTenantId("),
      ruleId: "b0.tenant-pilot-readiness.permission-snapshot-forbidden",
      message:
        "租户开通权限就绪检查不得全量读取角色分配后内存判断启用分配，必须使用仓储存在式查询。",
    });
  }
  if (tenantPilotService.includes("adapterRepository.findAllByTenantId(")) {
    violations.push({
      file: TENANT_PILOT_SERVICE,
      line: lineOf(tenantPilotService, "adapterRepository.findAllByTenantId("),
      ruleId: "b0.tenant-pilot-readiness.adapter-snapshot-forbidden",
      message:
        "租户开通适配器就绪检查不得全量读取适配器后内存判断 ACTIVE 适配器，必须使用仓储计数/存在式查询。",
    });
  }
  for (const [file, content, snippet] of [
    [
      ORG_UNIT_REPOSITORY,
      orgUnitRepository,
      "countByTenantIdAndLevelAndStatus",
    ],
    [
      TENANT_PILOT_SERVICE,
      tenantPilotService,
      "orgUnitRepository.countByTenantIdAndLevelAndStatus",
    ],
    [
      PLATFORM_CREDENTIAL_REPOSITORY,
      platformCredentialRepository,
      "countByTenantIdAndStatus",
    ],
    [
      TENANT_PILOT_SERVICE,
      tenantPilotService,
      "credentialRepository.countByTenantIdAndStatus",
    ],
    [
      USER_ROLE_ASSIGNMENT_REPOSITORY,
      userRoleAssignmentRepository,
      "existsByTenantIdAndActiveFlag",
    ],
    [
      TENANT_PILOT_SERVICE,
      tenantPilotService,
      "roleAssignmentRepository.existsByTenantIdAndActiveFlag",
    ],
    [
      INTEGRATION_ADAPTER_REPOSITORY,
      integrationAdapterRepository,
      "countByTenantIdAndStatus",
    ],
    [
      TENANT_PILOT_SERVICE,
      tenantPilotService,
      "adapterRepository.countByTenantIdAndStatus",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.tenant-pilot-readiness.core-step-required-snippet-missing",
        message: `租户开通核心步骤就绪检查缺少计数/存在式查询：${snippet}`,
      });
    }
  }
  for (const snippet of [
    "countReleasedByTenantId",
    "countByTenantIdAndStatus",
    "onboardingReadinessAllowsOpeningWhenTenantReleasedPackageExistsWithoutTenantSnapshot",
  ]) {
    const target =
      snippet === "countReleasedByTenantId"
        ? knowledgePackageRepository + tenantPilotService
        : snippet === "countByTenantIdAndStatus"
          ? tenantPackageReferenceRepository + tenantPilotService
        : tenantPilotServiceTest;
    if (!target.includes(snippet)) {
      violations.push({
        file:
          snippet === "countReleasedByTenantId"
            ? TENANT_PILOT_SERVICE
            : snippet === "countByTenantIdAndStatus"
              ? TENANT_PILOT_SERVICE
            : TENANT_PILOT_SERVICE_TEST,
        line: 1,
        ruleId: "b0.tenant-pilot-readiness.required-snippet-missing",
        message: `租户开通资产就绪检查缺少计数查询或回归测试：${snippet}`,
      });
    }
  }
  if (mpiService.includes("reviewRepository.findAllByTenantIdAndStatus")) {
    violations.push({
      file: MPI_SERVICE,
      line: lineOf(mpiService, "reviewRepository.findAllByTenantIdAndStatus"),
      ruleId: "b0.mpi-merge-review-list.tenant-snapshot-forbidden",
      message:
        "MPI 高危合并审核列表不得全量读取租户状态审核单，必须使用服务端分页响应。",
    });
  }
  if (
    mpiController.includes("ApiResult<java.util.List<MpiMergeReview>>") ||
    mpiController.includes("ApiResult<List<MpiMergeReview>>")
  ) {
    violations.push({
      file: MPI_CONTROLLER,
      line: lineOf(mpiController, /ApiResult<.*List<MpiMergeReview>>/),
      ruleId: "b0.mpi-merge-review-list.controller-array-forbidden",
      message:
        "MPI 高危合并审核控制器不得返回数组响应，必须返回 PageResponse 并暴露 page/size。",
    });
  }
  for (const [file, content, snippet] of [
    [
      MPI_MERGE_REVIEW_REPOSITORY,
      mpiMergeReviewRepository,
      "countByTenantIdAndStatus",
    ],
    [
      MPI_MERGE_REVIEW_REPOSITORY,
      mpiMergeReviewRepository,
      "pageByTenantIdAndStatus",
    ],
    [
      MPI_SERVICE,
      mpiService,
      "PageResponse<MpiMergeReview> getMergeReviews",
    ],
    [
      MPI_SERVICE,
      mpiService,
      "reviewRepository.countByTenantIdAndStatus",
    ],
    [
      MPI_SERVICE,
      mpiService,
      "reviewRepository.pageByTenantIdAndStatus",
    ],
    [
      MPI_CONTROLLER,
      mpiController,
      "ApiResult<PageResponse<MpiMergeReview>> getMergeReviews",
    ],
    [
      MPI_CONTROLLER,
      mpiController,
      "new PageRequest(page, size, sort)",
    ],
    [
      MPI_SERVICE_TEST,
      mpiServiceTest,
      "shouldReturnMergeReviewsPageWithoutMaterializingTenantStatusSnapshot",
    ],
    [
      MPI_CONTROLLER_CONTRACT_TEST,
      mpiControllerContractTest,
      "$.data.items[0].reviewId",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.mpi-merge-review-list.required-snippet-missing",
        message: `MPI 高危合并审核分页缺少仓储、服务、控制器或测试片段：${snippet}`,
      });
    }
  }
  if (
    !pathwayRepositoryTest.includes(
      "packageFilterFindsPathwayPackageByPackageIdKeyword",
    )
  ) {
    violations.push({
      file: PATHWAY_REPOSITORY_TEST,
      line: 1,
      ruleId: "b0.package-reference-search-test.required-snippet-missing",
      message: "路径仓储测试必须覆盖配置包 keyword 按 packageId 命中。",
    });
  }
  const knowledgeReviewPackageFixedSnapshotPattern =
    /knowledgePackagesQuery\s*=\s*usePackages\(\{[^}]*size:\s*100[^}]*assetType:\s*"KNOWLEDGE"[^}]*\}\)|knowledgePackagesQuery\s*=\s*usePackages\(\{[^}]*assetType:\s*"KNOWLEDGE"[^}]*size:\s*100[^}]*\}\)/s;
  if (knowledgeReviewPackageFixedSnapshotPattern.test(governance)) {
    violations.push({
      file: KNOWLEDGE_GOVERNANCE,
      line: lineOf(governance, knowledgeReviewPackageFixedSnapshotPattern),
      ruleId: "b0.knowledge-review-package-version.fixed-snapshot-forbidden",
      message:
        "知识审核不得用 100 条 KNOWLEDGE 配置包快照解析审核上下文包版本，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "KNOWLEDGE_REVIEW_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "size: KNOWLEDGE_REVIEW_PACKAGE_REFERENCE_PAGE_SIZE",
    'assetType: "KNOWLEDGE"',
    "keyword: reviewPackageSearch || undefined",
    "reviewPackageOptions",
    "filterOption={false}",
    "onSearch={setReviewPackageSearch}",
    "选择已存在的知识配置包版本",
  ]) {
    if (!governance.includes(snippet)) {
      violations.push({
        file: KNOWLEDGE_GOVERNANCE,
        line: 1,
        ruleId: "b0.knowledge-review-package-version.required-snippet-missing",
        message: `B0 知识审核发布必须从受控知识配置包选择审核上下文版本，禁止手写候选版本号：${snippet}`,
      });
    }
  }
  if (
    !governanceTest.includes(
      "loads knowledge review package selector through small server-side search pages",
    )
  ) {
    violations.push({
      file: KNOWLEDGE_GOVERNANCE_TEST,
      line: 1,
      ruleId:
        "b0.knowledge-review-package-version-test.required-snippet-missing",
      message: "知识审核上下文包版本小页服务端搜索缺少回归测试。",
    });
  }
  if (knowledgeCustomizationService.includes("findByTenantIdOrderByUpdatedAtDesc(")) {
    violations.push({
      file: KNOWLEDGE_CUSTOMIZATION_SERVICE,
      line: lineOf(
        knowledgeCustomizationService,
        "findByTenantIdOrderByUpdatedAtDesc(",
      ),
      ruleId: "b0.knowledge-customization-list.tenant-snapshot-forbidden",
      message:
        "机构知识定制列表不得全量读取租户定制血缘后由前端分页，必须使用后端 PageResponse 与仓储分页。",
    });
  }
  if (governance.includes("customizationsQuery.data?.some")) {
    violations.push({
      file: KNOWLEDGE_GOVERNANCE,
      line: lineOf(governance, "customizationsQuery.data?.some"),
      ruleId: "b0.knowledge-customization-list.frontend-array-snapshot-forbidden",
      message:
        "机构知识定制列表前端不得继续按数组快照消费，必须读取 PageResponse.items。",
    });
  }
  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_CUSTOMIZATION_REPOSITORY,
      knowledgeCustomizationRepository,
      "pageByTenantId",
    ],
    [
      KNOWLEDGE_CUSTOMIZATION_REPOSITORY,
      knowledgeCustomizationRepository,
      "countByTenantId",
    ],
    [
      KNOWLEDGE_CUSTOMIZATION_SERVICE,
      knowledgeCustomizationService,
      "PageResponse<KnowledgeCustomizationResponse> list(PageRequest pageRequest)",
    ],
    [
      KNOWLEDGE_CUSTOMIZATION_CONTROLLER,
      knowledgeCustomizationController,
      "ApiResult<PageResponse<KnowledgeCustomizationResponse>> list(",
    ],
    [
      KNOWLEDGE_CUSTOMIZATION_SERVICE_TEST,
      knowledgeCustomizationServiceTest,
      "listsLocalDerivativesThroughRepositoryPaginationInsteadOfTenantSnapshot",
    ],
    [
      KNOWLEDGE_GOVERNANCE,
      governance,
      "KNOWLEDGE_CUSTOMIZATION_PAGE_SIZE = 20",
    ],
    [
      KNOWLEDGE_GOVERNANCE,
      governance,
      "useKnowledgeCustomizations(",
    ],
    [
      KNOWLEDGE_GOVERNANCE,
      governance,
      "customizationItems = useMemo",
    ],
    [
      KNOWLEDGE_GOVERNANCE,
      governance,
      "dataSource={customizationItems}",
    ],
    [
      API_HOOKS,
      apiHooks,
      "interface KnowledgeCustomizationsParams",
    ],
    [API_HOOKS, apiHooks, "PageResponse<KnowledgeCustomization>"],
    [API_HOOKS, apiHooks, "{ params: queryParams }"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "loads institution knowledge customizations through server pagination",
    ],
    [
      KNOWLEDGE_GOVERNANCE_TEST,
      governanceTest,
      "expect(mockUseKnowledgeCustomizations).toHaveBeenCalledWith({ page: 1, size: 20 }, true)",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.knowledge-customization-list.required-snippet-missing",
        message: `机构知识定制列表分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  const knowledgeIdentitySnapshotPattern =
    /effectiveRows\s*=|slice\(effectiveRows|rows\.subList\(/;
  if (knowledgeIdentitySnapshotPattern.test(knowledgeIdentityService)) {
    violations.push({
      file: KNOWLEDGE_IDENTITY_SERVICE,
      line: lineOf(knowledgeIdentityService, knowledgeIdentitySnapshotPattern),
      ruleId:
        "b0.knowledge-identity-effective-list.tenant-platform-snapshot-forbidden",
      message:
        "知识身份有效列表不得全量拉取租户与平台身份后内存合并分页，必须使用仓储有效分页和计数查询。",
    });
  }
  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_IDENTITY_REPOSITORY,
      knowledgeIdentityRepository,
      "countEffectiveByFilter",
    ],
    [
      KNOWLEDGE_IDENTITY_REPOSITORY,
      knowledgeIdentityRepository,
      "pageEffectiveByFilter",
    ],
    [KNOWLEDGE_IDENTITY_SERVICE, knowledgeIdentityService, "countEffectiveByFilter("],
    [KNOWLEDGE_IDENTITY_SERVICE, knowledgeIdentityService, "pageEffectiveByFilter("],
    [
      KNOWLEDGE_IDENTITY_SERVICE_TEST,
      knowledgeIdentityServiceTest,
      "pageMergesCustomerLocalOverridesWithPlatformActiveIdentities",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE_TEST,
      knowledgeIdentityServiceTest,
      "never()).listByFilter",
    ],
    [
      KNOWLEDGE_IDENTITY_REPOSITORY_TEST,
      knowledgeIdentityRepositoryTest,
      "pagesEffectiveTenantIdentitiesWithoutMaterializingTenantAndPlatformSnapshots",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.knowledge-identity-effective-list.required-snippet-missing",
        message: `知识身份有效分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  const ruleDefinitionSnapshotPattern =
    /effectiveRows\s*=|slice\(effectiveRows|rows\.subList\(/;
  if (ruleDefinitionSnapshotPattern.test(ruleEngineService)) {
    violations.push({
      file: RULE_ENGINE_SERVICE,
      line: lineOf(ruleEngineService, ruleDefinitionSnapshotPattern),
      ruleId:
        "b0.rule-definition-effective-list.tenant-platform-snapshot-forbidden",
      message:
        "规则定义有效列表不得全量拉取租户与平台规则后内存合并分页，必须使用仓储有效分页和计数查询。",
    });
  }
  for (const [file, content, snippet] of [
    [RULE_DEFINITION_REPOSITORY, ruleDefinitionRepository, "countEffectiveByFilter"],
    [RULE_DEFINITION_REPOSITORY, ruleDefinitionRepository, "pageEffectiveByFilter"],
    [RULE_ENGINE_SERVICE, ruleEngineService, "countEffectiveByFilter("],
    [RULE_ENGINE_SERVICE, ruleEngineService, "pageEffectiveByFilter("],
    [
      RULE_ENGINE_SERVICE_TEST,
      ruleEngineServiceTest,
      "listUsesEffectiveRepositoryPagingForCustomerTenantWithoutLoadingSnapshots",
    ],
    [RULE_ENGINE_SERVICE_TEST, ruleEngineServiceTest, "never()).listByFilter"],
    [
      RULE_REPOSITORY_TEST,
      ruleRepositoryTest,
      "pagesEffectiveRulesWithoutMaterializingTenantAndPlatformSnapshots",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.rule-definition-effective-list.required-snippet-missing",
        message: `规则定义有效分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  const pathwayTemplateSnapshotPattern =
    /effectiveRows\s*=|slice\(effectiveRows|rows\.subList\(/;
  if (pathwayTemplateSnapshotPattern.test(pathwayEngineService)) {
    violations.push({
      file: PATHWAY_ENGINE_SERVICE,
      line: lineOf(pathwayEngineService, pathwayTemplateSnapshotPattern),
      ruleId:
        "b0.pathway-template-effective-list.tenant-platform-snapshot-forbidden",
      message:
        "路径模板有效列表不得全量拉取租户与平台模板后内存合并分页，必须使用仓储有效分页和计数查询。",
    });
  }
  for (const [file, content, snippet] of [
    [
      PATHWAY_TEMPLATE_REPOSITORY,
      pathwayTemplateRepository,
      "countEffectiveByFilter",
    ],
    [
      PATHWAY_TEMPLATE_REPOSITORY,
      pathwayTemplateRepository,
      "pageEffectiveByFilter",
    ],
    [PATHWAY_ENGINE_SERVICE, pathwayEngineService, "countEffectiveByFilter("],
    [PATHWAY_ENGINE_SERVICE, pathwayEngineService, "pageEffectiveByFilter("],
    [
      PATHWAY_ENGINE_SERVICE_TEST,
      pathwayEngineServiceTest,
      "listTemplatesUsesEffectiveRepositoryPagingForCustomerTenantWithoutLoadingSnapshots",
    ],
    [PATHWAY_ENGINE_SERVICE_TEST, pathwayEngineServiceTest, "never()).listByFilter"],
    [
      PATHWAY_REPOSITORY_TEST,
      pathwayRepositoryTest,
      "pagesEffectiveTemplatesWithoutMaterializingTenantAndPlatformSnapshots",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.pathway-template-effective-list.required-snippet-missing",
        message: `路径模板有效分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }

  pushMissing(
    violations,
    DIAGNOSIS_MAINTENANCE,
    diagnosisPage,
    "诊断知识维护",
    "b0.diagnosis-maintenance.title-missing",
    "诊断知识维护必须保留独立页面标题。",
  );
  pushMissing(
    violations,
    ROUTES,
    routes,
    "/knowledge/diagnosis",
    "b0.diagnosis-route.missing",
    "必须保留独立诊断知识维护路由 /knowledge/diagnosis。",
  );

  const qualityEvaluationPackageFixedSnapshotPattern =
    /evaluationPackagesQuery\s*=\s*usePackages\(\{[^}]*size:\s*100[^}]*assetType:\s*"EVALUATION"[^}]*\}\)|evaluationPackagesQuery\s*=\s*usePackages\(\{[^}]*assetType:\s*"EVALUATION"[^}]*size:\s*100[^}]*\}\)/s;
  if (qualityEvaluationPackageFixedSnapshotPattern.test(qcEvalSets)) {
    violations.push({
      file: QC_EVAL_SETS,
      line: lineOf(qcEvalSets, qualityEvaluationPackageFixedSnapshotPattern),
      ruleId:
        "b0.quality-evaluation-package-version.fixed-snapshot-forbidden",
      message:
        "质控评估不得用 100 条 EVALUATION 配置包快照解析包版本，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "EVALUATION_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "size: EVALUATION_PACKAGE_REFERENCE_PAGE_SIZE",
    'assetType: "EVALUATION"',
    "keyword: evaluationPackageSearch || undefined",
    "filterOption={false}",
    "onSearch={setEvaluationPackageSearch}",
    "选择已存在的评估配置包版本",
    "选择仿真使用的评估配置包版本",
  ]) {
    if (!qcEvalSets.includes(snippet)) {
      violations.push({
        file: QC_EVAL_SETS,
        line: 1,
        ruleId:
          "b0.quality-evaluation-package-version.required-snippet-missing",
        message: `B0 质控指标维护必须从受控评估配置包选择版本，禁止手写未知版本：${snippet}`,
      });
    }
  }
  if (
    !qcEvalSetsTest.includes(
      "loads evaluation package selectors through small server-side search pages",
    )
  ) {
    violations.push({
      file: QC_EVAL_SETS_TEST,
      line: 1,
      ruleId:
        "b0.quality-evaluation-package-version-test.required-snippet-missing",
      message: "质控评估配置包小页服务端搜索缺少回归测试。",
    });
  }

  const insuranceAuditIndicatorFixedSnapshotPattern =
    /useEvaluationIndicators\(\s*\{[^}]*status:\s*"ACTIVE"[^}]*size:\s*100[^}]*sort:\s*"name,asc"[^}]*\}/s;
  if (insuranceAuditIndicatorFixedSnapshotPattern.test(insuranceAudit)) {
    violations.push({
      file: INSURANCE_AUDIT,
      line: lineOf(insuranceAudit, insuranceAuditIndicatorFixedSnapshotPattern),
      ruleId:
        "b0.insurance-audit-indicator-reference.fixed-snapshot-forbidden",
      message:
        "医保审核质控指标选择不得用 100 条 ACTIVE 指标快照，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "AUDIT_INDICATOR_REFERENCE_PAGE_SIZE = 20",
    "indicatorSearch",
    "indicatorKeyword",
    "indicatorCode: indicatorKeyword",
    "size: AUDIT_INDICATOR_REFERENCE_PAGE_SIZE",
    "filterOption={false}",
    "onSearch={setIndicatorSearch}",
    'onClear={() => setIndicatorSearch("")}',
  ]) {
    if (!insuranceAudit.includes(snippet)) {
      violations.push({
        file: INSURANCE_AUDIT,
        line: 1,
        ruleId:
          "b0.insurance-audit-indicator-reference.required-snippet-missing",
        message: `医保审核指标选择必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !insuranceAuditTest.includes(
      "loads audit indicator selector through small server-side search pages",
    )
  ) {
    violations.push({
      file: INSURANCE_AUDIT_TEST,
      line: 1,
      ruleId:
        "b0.insurance-audit-indicator-reference-test.required-snippet-missing",
      message: "医保审核指标小页服务端搜索缺少回归测试。",
    });
  }

  const followupPlanFixedSnapshotPattern =
    /useFollowupPlans\(\s*\{[^}]*page:\s*1[^}]*size:\s*100[^}]*\}/s;
  if (followupPlanFixedSnapshotPattern.test(followup)) {
    violations.push({
      file: FOLLOWUP,
      line: lineOf(followup, followupPlanFixedSnapshotPattern),
      ruleId: "b0.followup-plan-list.fixed-snapshot-forbidden",
      message:
        "随访计划列表不得固定读取第一页 100 条后前端分页，必须使用服务端表格分页。",
    });
  }
  for (const snippet of [
    "FOLLOWUP_PLAN_PAGE_SIZE = 20",
    "planPage",
    "setPlanPage",
    "page: planPage",
    "size: FOLLOWUP_PLAN_PAGE_SIZE",
    "current: apiPlansData?.page ?? planPage",
    "pageSize: apiPlansData?.size ?? FOLLOWUP_PLAN_PAGE_SIZE",
    "total: apiPlansData?.total ?? displayPlans.length",
    "onChange: (page) => setPlanPage(page)",
  ]) {
    if (!followup.includes(snippet)) {
      violations.push({
        file: FOLLOWUP,
        line: 1,
        ruleId: "b0.followup-plan-list.required-snippet-missing",
        message: `随访计划列表必须保持服务端表格分页：${snippet}`,
      });
    }
  }
  if (
    !followupTest.includes(
      "loads follow-up plans through server-side table pagination",
    )
  ) {
    violations.push({
      file: FOLLOWUP_TEST,
      line: 1,
      ruleId: "b0.followup-plan-list-test.required-snippet-missing",
      message: "随访计划列表服务端分页缺少回归测试。",
    });
  }
  const followupTemplateFixedSnapshotPattern =
    /useFollowupTemplates\(\s*\{[^}]*page:\s*1[^}]*size:\s*100[^}]*\}/s;
  if (followupTemplateFixedSnapshotPattern.test(followup)) {
    violations.push({
      file: FOLLOWUP,
      line: lineOf(followup, followupTemplateFixedSnapshotPattern),
      ruleId: "b0.followup-template-reference.fixed-snapshot-forbidden",
      message:
        "随访模板治理和生成计划模板选择不得固定读取 100 条快照，必须使用小页服务端分页和已发布模板搜索。",
    });
  }
  for (const snippet of [
    "FOLLOWUP_TEMPLATE_PAGE_SIZE = 20",
    "templatePage",
    "setTemplatePage",
    "publishedTemplateSearch",
    "setPublishedTemplateSearch",
    "page: templatePage",
    "size: FOLLOWUP_TEMPLATE_PAGE_SIZE",
    'assetStatus: "PUBLISHED"',
    "current: templatesQuery.data?.page ?? templatePage",
    "pageSize: templatesQuery.data?.size ?? FOLLOWUP_TEMPLATE_PAGE_SIZE",
    "total: templatesQuery.data?.total ?? templates.length",
    "onChange: (page) => setTemplatePage(page)",
    "filterOption={false}",
    "onSearch={setPublishedTemplateSearch}",
    'onClear={() => setPublishedTemplateSearch("")}',
  ]) {
    if (!followup.includes(snippet)) {
      violations.push({
        file: FOLLOWUP,
        line: 1,
        ruleId: "b0.followup-template-reference.required-snippet-missing",
        message: `随访模板治理和生成计划模板选择必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !followupTest.includes(
      "loads follow-up templates through server-side pagination and published-template search",
    )
  ) {
    violations.push({
      file: FOLLOWUP_TEST,
      line: 1,
      ruleId: "b0.followup-template-reference-test.required-snippet-missing",
      message: "随访模板小页服务端分页和已发布模板搜索缺少回归测试。",
    });
  }
  const followupTemplateBackendSnapshotPattern =
    /findByTenantIdOrderByUpdatedAtDesc\(|\.subList\(/;
  if (followupTemplateBackendSnapshotPattern.test(followupTemplateService)) {
    violations.push({
      file: FOLLOWUP_TEMPLATE_SERVICE,
      line: lineOf(followupTemplateService, followupTemplateBackendSnapshotPattern),
      ruleId:
        "b0.followup-template-reference.backend-tenant-snapshot-forbidden",
      message:
        "随访模板后端不得全量读取租户模板后内存分页，必须使用仓储过滤分页。",
    });
  }
  for (const snippet of [
    "FollowupTemplateFilter",
    "normalizeKeyword",
    "templates.countByFilter",
    "templates.pageByFilter",
    "page.offset()",
    "page.safeSize()",
  ]) {
    if (!followupTemplateService.includes(snippet)) {
      violations.push({
        file: FOLLOWUP_TEMPLATE_SERVICE,
        line: 1,
        ruleId: "b0.followup-template-reference.backend-required-snippet-missing",
        message: `随访模板服务层必须保持数据库过滤分页：${snippet}`,
      });
    }
  }
  for (const snippet of [
    "pageByFilter",
    "countByFilter",
    "JOIN mk_version_asset_version av",
    "av.status = :assetStatus",
    "LOWER(ft.template_code) LIKE :keyword",
    "LOWER(ft.name) LIKE :keyword",
    "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
  ]) {
    if (!followupTemplateRepository.includes(snippet)) {
      violations.push({
        file: FOLLOWUP_TEMPLATE_REPOSITORY,
        line: 1,
        ruleId:
          "b0.followup-template-reference.repository-required-snippet-missing",
        message: `随访模板仓储必须保持状态/关键词服务端分页：${snippet}`,
      });
    }
  }
  if (
    !followupTemplateServiceTest.includes(
      "listTemplatesUsesRepositoryFilterPaginationInsteadOfTenantSnapshot",
    )
  ) {
    violations.push({
      file: FOLLOWUP_TEMPLATE_SERVICE_TEST,
      line: 1,
      ruleId:
        "b0.followup-template-reference-backend-test.required-snippet-missing",
      message: "随访模板后端过滤分页缺少回归测试。",
    });
  }
  const identityBindingPersonnelFixedSnapshotPattern =
    /usePersonnel\(\s*\{[^}]*\bsize:\s*100\b[^}]*\}/s;
  if (identityBindingPersonnelFixedSnapshotPattern.test(identityBinding)) {
    violations.push({
      file: IDENTITY_BINDING,
      line: lineOf(identityBinding, identityBindingPersonnelFixedSnapshotPattern),
      ruleId:
        "b0.identity-binding-personnel-reference.fixed-snapshot-forbidden",
      message:
        "身份来源绑定不得固定读取 100 条人员快照，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "PERSONNEL_REFERENCE_PAGE_SIZE = 20",
    "size: PERSONNEL_REFERENCE_PAGE_SIZE",
    "keyword: userSearch || undefined",
  ]) {
    if (!identityBinding.includes(snippet)) {
      violations.push({
        file: IDENTITY_BINDING,
        line: 1,
        ruleId:
          "b0.identity-binding-personnel-reference.required-snippet-missing",
        message: `身份来源绑定人员选择必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !operationalControlPagesTest.includes(
      "loads identity binding personnel selector through small server-side pages",
    )
  ) {
    violations.push({
      file: OPERATIONAL_CONTROL_PAGES_TEST,
      line: 1,
      ruleId:
        "b0.identity-binding-personnel-reference-test.required-snippet-missing",
      message: "身份来源绑定人员选择小页服务端搜索缺少回归测试。",
    });
  }
  if (identityBindingService.includes("findByTenantIdOrderByUpdatedAtDesc(")) {
    violations.push({
      file: IDENTITY_BINDING_SERVICE,
      line: lineOf(identityBindingService, "findByTenantIdOrderByUpdatedAtDesc("),
      ruleId: "b0.identity-binding-list.tenant-snapshot-forbidden",
      message:
        "身份来源绑定列表不得全量读取租户绑定关系后由前端分页，必须使用后端 PageResponse 与仓储分页。",
    });
  }
  for (const [file, content, snippet] of [
    [IDENTITY_BINDING_REPOSITORY, identityBindingRepository, "pageByTenantId"],
    [IDENTITY_BINDING_REPOSITORY, identityBindingRepository, "countByTenantId"],
    [IDENTITY_BINDING_SERVICE, identityBindingService, "PageResponse<IdentityBindingResponse>"],
    [IDENTITY_BINDING_CONTROLLER_TEST, identityBindingControllerTest, "$.data.items"],
    [IDENTITY_BINDING, identityBinding, "IDENTITY_BINDING_PAGE_SIZE = 20"],
    [
      IDENTITY_BINDING,
      identityBinding,
      "useIdentityBindings({ page: bindingPage, size: IDENTITY_BINDING_PAGE_SIZE })",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.identity-binding-list.required-snippet-missing",
        message: `身份来源绑定列表分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  if (
    exportApprovalService.includes("findByTenantIdOrderByRequestedAtDesc(") ||
    exportApprovalService.includes("List<ExportApprovalResponse> listApprovals(")
  ) {
    const pattern = exportApprovalService.includes("findByTenantIdOrderByRequestedAtDesc(")
      ? "findByTenantIdOrderByRequestedAtDesc("
      : "List<ExportApprovalResponse> listApprovals(";
    violations.push({
      file: EXPORT_APPROVAL_SERVICE,
      line: lineOf(exportApprovalService, pattern),
      ruleId: "b0.export-approval-list.tenant-snapshot-forbidden",
      message:
        "导出审批列表不得全量读取租户审批记录后由前端分页，必须使用后端 PageResponse 与仓储分页。",
    });
  }
  const exportApprovalFrontendSnapshotPattern =
    /data:\s*ExportApproval\[\]|dataSource=\{approvals\.data\s*\?\?\s*\[\]\}|useExportApprovals\(\s*\{\s*resourceType:\s*"AUDIT_EVENT"\s*\}/s;
  if (
    exportApprovalFrontendSnapshotPattern.test(apiHooks) ||
    exportApprovalFrontendSnapshotPattern.test(adminAudit)
  ) {
    const file = exportApprovalFrontendSnapshotPattern.test(adminAudit)
      ? ADMIN_AUDIT
      : API_HOOKS;
    const content = file === ADMIN_AUDIT ? adminAudit : apiHooks;
    violations.push({
      file,
      line: lineOf(content, exportApprovalFrontendSnapshotPattern),
      ruleId: "b0.export-approval-list.frontend-array-snapshot-forbidden",
      message:
        "导出审批前端不得按数组快照消费审批列表，必须传 page/size 并读取 PageResponse.items。",
    });
  }
  for (const [file, content, snippet] of [
    [EXPORT_APPROVAL_REPOSITORY, exportApprovalRepository, "countByFilter"],
    [EXPORT_APPROVAL_REPOSITORY, exportApprovalRepository, "pageByFilter"],
    [
      EXPORT_APPROVAL_SERVICE,
      exportApprovalService,
      "PageResponse<ExportApprovalResponse> listApprovals(",
    ],
    [EXPORT_APPROVAL_SERVICE, exportApprovalService, "repository.countByFilter"],
    [EXPORT_APPROVAL_SERVICE, exportApprovalService, "repository.pageByFilter"],
    [
      EXPORT_APPROVAL_CONTROLLER,
      exportApprovalController,
      "ApiResult<PageResponse<ExportApprovalResponse>> listExports(",
    ],
    [
      EXPORT_APPROVAL_CONTROLLER,
      exportApprovalController,
      "new PageRequest(page, size, sort)",
    ],
    [
      EXPORT_APPROVAL_SERVICE_TEST,
      exportApprovalServiceTest,
      "repository.countByFilter",
    ],
    [
      EXPORT_APPROVAL_SERVICE_TEST,
      exportApprovalServiceTest,
      "repository.pageByFilter",
    ],
    [
      EXPORT_APPROVAL_CONTROLLER_SECURITY_TEST,
      exportApprovalControllerSecurityTest,
      "$.data.items[0].status",
    ],
    [API_HOOKS, apiHooks, "interface ExportApprovalsParams"],
    [API_HOOKS, apiHooks, "PageResponse<ExportApproval>"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "loads export approvals through server pagination",
    ],
    [ADMIN_AUDIT, adminAudit, "APPROVAL_PAGE_SIZE = 20"],
    [ADMIN_AUDIT, adminAudit, "dataSource={approvals.data?.items ?? []}"],
    [
      ADMIN_AUDIT_TEST,
      adminAuditTest,
      'resourceType: "AUDIT_EVENT", page: 1, size: 20',
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.export-approval-list.required-snippet-missing",
        message: `导出审批列表分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }

  const authoringClonePackageFixedSnapshotPattern =
    /clonePackagesQuery\s*=\s*usePackages\(\{[^}]*size:\s*100[^}]*assetType:\s*cloneAsset\.assetType[^}]*\}\)|clonePackagesQuery\s*=\s*usePackages\(\{[^}]*assetType:\s*cloneAsset\.assetType[^}]*size:\s*100[^}]*\}\)/s;
  if (authoringClonePackageFixedSnapshotPattern.test(authoringAssets)) {
    violations.push({
      file: AUTHORING_ASSETS,
      line: lineOf(authoringAssets, authoringClonePackageFixedSnapshotPattern),
      ruleId: "b0.authoring-clone-package-version.fixed-snapshot-forbidden",
      message:
        "统一资产克隆不得用 100 条配置包快照解析草稿包版本，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "CLONE_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "clonePackagesQuery = usePackages({",
    "size: CLONE_PACKAGE_REFERENCE_PAGE_SIZE",
    "keyword: clonePackageSearch || cloneAsset?.packageVersion || undefined",
    "assetType: cloneAsset.assetType",
    "clonePackageOptions",
    "knownClonePackageVersions",
    "clonePackageVersionRules",
    "配置包列表不可用，暂不能克隆资产。",
    "请选择已存在的配置包版本。",
    "filterOption={false}",
    "onSearch={setClonePackageSearch}",
    "选择克隆草稿所属配置包版本",
  ]) {
    if (!authoringAssets.includes(snippet)) {
      violations.push({
        file: AUTHORING_ASSETS,
        line: 1,
        ruleId: "b0.authoring-clone-package-version.required-snippet-missing",
        message: `B0 统一资产克隆必须从受控资产配置包选择版本，禁止手写未知包版本：${snippet}`,
      });
    }
  }
  if (
    !authoringAssetsTest.includes(
      "blocks cloning when the selected package version is not loaded from package selector",
    )
  ) {
    violations.push({
      file: AUTHORING_ASSETS_TEST,
      line: 1,
      ruleId: "b0.authoring-clone-package-version-test.required-snippet-missing",
      message: "统一资产克隆缺少当前包版本必须来自配置包选择器的回归测试。",
    });
  }
  const authoringAssetBackendSnapshotPattern =
    /findByTenantIdOrderByUpdatedAtDesc\(|\.subList\(/;
  if (authoringAssetBackendSnapshotPattern.test(authoringAssetLibraryService)) {
    violations.push({
      file: AUTHORING_ASSET_LIBRARY_SERVICE,
      line: lineOf(authoringAssetLibraryService, authoringAssetBackendSnapshotPattern),
      ruleId: "b0.authoring-asset-library.backend-tenant-snapshot-forbidden",
      message:
        "统一创作资产库后端不得全量读取租户资产后内存分页，必须使用各资产仓储过滤分页。",
    });
  }
  for (const snippet of [
    "listRepositoryPage",
    "loadRepositoryPage",
    "RepositoryAssetPage",
    "rules.countByFilter",
    "rules.pageByFilter",
    "pathways.countByFilter",
    "pathways.pageByFilter",
    "fragments.countByFilter",
    "fragments.pageByFilter",
    "followupTemplates.countByFilter",
    "followupTemplates.pageByFilter",
    "page.offset()",
    "page.safeSize()",
  ]) {
    if (!authoringAssetLibraryService.includes(snippet)) {
      violations.push({
        file: AUTHORING_ASSET_LIBRARY_SERVICE,
        line: 1,
        ruleId:
          "b0.authoring-asset-library.backend-required-snippet-missing",
        message: `统一创作资产库必须保持各资产仓储分页：${snippet}`,
      });
    }
  }
  if (
    !authoringAssetLibraryServiceTest.includes(
      "listsTypedFollowupAssetsThroughRepositoryPagination",
    )
  ) {
    violations.push({
      file: AUTHORING_ASSET_LIBRARY_SERVICE_TEST,
      line: 1,
      ruleId:
        "b0.authoring-asset-library-backend-test.required-snippet-missing",
      message: "统一创作资产库后端仓储分页缺少回归测试。",
    });
  }
  const authoringBatchRulePackageFixedSnapshotPattern =
    /usePackages\(\{[^}]*size:\s*100[^}]*assetType:\s*"RULE"[^}]*\}\)|usePackages\(\{[^}]*assetType:\s*"RULE"[^}]*size:\s*100[^}]*\}\)/s;
  if (
    authoringBatchRulePackageFixedSnapshotPattern.test(authoringBatchDrawer)
  ) {
    violations.push({
      file: AUTHORING_BATCH_DRAWER,
      line: lineOf(
        authoringBatchDrawer,
        authoringBatchRulePackageFixedSnapshotPattern,
      ),
      ruleId:
        "b0.authoring-batch-rule-package-reference.fixed-snapshot-forbidden",
      message:
        "批量规则生成不得用 100 条 RULE 配置包快照解析统一包版本，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "RULE_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "size: RULE_PACKAGE_REFERENCE_PAGE_SIZE",
    'assetType: "RULE"',
    "filterOption={false}",
    "onSearch={setRulePackageSearch}",
    "选择规则配置包版本",
  ]) {
    if (!authoringBatchDrawer.includes(snippet)) {
      violations.push({
        file: AUTHORING_BATCH_DRAWER,
        line: 1,
        ruleId:
          "b0.authoring-batch-rule-package-reference.required-snippet-missing",
        message: `B0 批量规则生成规则包选择必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !authoringBatchDrawerTest.includes(
      "loads rule package selector through small server-side pages",
    )
  ) {
    violations.push({
      file: AUTHORING_BATCH_DRAWER_TEST,
      line: 1,
      ruleId:
        "b0.authoring-batch-rule-package-reference-test.required-snippet-missing",
      message: "批量规则生成规则包小页服务端搜索缺少回归测试。",
    });
  }

  if (
    integrationController.includes("ApiResult<List<IntegrationAdapter>>") ||
    integrationController.includes("ApiResult<java.util.List<IntegrationAdapter>>")
  ) {
    violations.push({
      file: INTEGRATION_CONTROLLER,
      line: lineOf(integrationController, /ApiResult<.*List<IntegrationAdapter>>/),
      ruleId: "b0.integration-adapter-list.controller-array-forbidden",
      message:
        "第三方适配器目录不得返回数组响应，必须返回 PageResponse 并暴露 page/size。",
    });
  }
  if (integrationService.includes("public List<IntegrationAdapter> getAdapters")) {
    violations.push({
      file: INTEGRATION_SERVICE,
      line: lineOf(integrationService, "getAdapters"),
      ruleId: "b0.integration-adapter-list.tenant-snapshot-forbidden",
      message:
        "第三方适配器目录不得全量读取租户适配器后返回，必须使用服务端分页。",
    });
  }
  if (
    apiHooks.includes("IntegrationEnvelope<IntegrationAdapter[]>") ||
    adapterHub.includes("const adapters = adaptersQuery.data ?? []") ||
    adapterHub.includes("useIntegrationAdapters();") ||
    adapterHub.includes("dataSource={adapters}\n                      pagination={false}")
  ) {
    violations.push({
      file: ADAPTER_HUB,
      line: lineOf(adapterHub + apiHooks, "useIntegrationAdapters"),
      ruleId: "b0.integration-adapter-list.frontend-array-forbidden",
      message:
        "第三方适配器目录前端不得按数组快照消费，必须使用 PageResponse.items 和服务端分页。",
    });
  }
  for (const [file, content, snippet] of [
    [
      INTEGRATION_ADAPTER_REPOSITORY,
      integrationAdapterRepository,
      "countByTenantId(String tenantId)",
    ],
    [
      INTEGRATION_ADAPTER_REPOSITORY,
      integrationAdapterRepository,
      "pageByTenantId",
    ],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "PageResponse<IntegrationAdapter> getAdapters",
    ],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "adapterRepository.countByTenantId",
    ],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "adapterRepository.pageByTenantId",
    ],
    [
      INTEGRATION_CONTROLLER,
      integrationController,
      "ApiResult<PageResponse<IntegrationAdapter>> getAdapters",
    ],
    [
      INTEGRATION_CONTROLLER,
      integrationController,
      "new PageRequest(page, size, sort)",
    ],
    [API_HOOKS, apiHooks, "export interface IntegrationAdaptersParams"],
    [
      API_HOOKS,
      apiHooks,
      "IntegrationEnvelope<PageResponse<IntegrationAdapter>>",
    ],
    [API_HOOKS, apiHooks, "{ params }"],
    [ADAPTER_HUB, adapterHub, "ADAPTER_PAGE_SIZE = 20"],
    [
      ADAPTER_HUB,
      adapterHub,
      "useIntegrationAdapters({ page: adapterPage, size: ADAPTER_PAGE_SIZE })",
    ],
    [ADAPTER_HUB, adapterHub, "adaptersQuery.data?.items ?? []"],
    [ADAPTER_HUB, adapterHub, "total: adaptersQuery.data?.total ?? 0"],
    [ADAPTER_HUB, adapterHub, "onChange: setAdapterPage"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "loads integration adapters through server pagination",
    ],
    [
      ADAPTER_HUB_TEST,
      adapterHubTest,
      "useIntegrationAdapters).toHaveBeenCalledWith({ page: 1, size: 20 })",
    ],
    [
      INTEGRATION_SERVICE_TEST,
      integrationServiceTest,
      "PageResponse<IntegrationAdapter> page = service.getAdapters",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.integration-adapter-list.required-snippet-missing",
        message: `第三方适配器目录分页缺少仓储、服务、控制器、前端或测试片段：${snippet}`,
      });
    }
  }

  const integrationContractPackageFixedSnapshotPattern =
    /packagesQuery\s*=\s*usePackages\(\{[^}]*size:\s*100[^}]*\}\)/s;
  if (integrationContractPackageFixedSnapshotPattern.test(adapterHub)) {
    violations.push({
      file: ADAPTER_HUB,
      line: lineOf(adapterHub, integrationContractPackageFixedSnapshotPattern),
      ruleId:
        "b0.integration-contract-package-reference.fixed-snapshot-forbidden",
      message:
        "第三方数据契约不得用 100 条配置包快照解析契约版本，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "INTEGRATION_CONTRACT_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "size: INTEGRATION_CONTRACT_PACKAGE_REFERENCE_PAGE_SIZE",
    "keyword: contractPackageSearch || undefined",
    "filterOption={false}",
    "onSearch={onPackageSearch}",
    "选择已存在配置包版本",
  ]) {
    if (!adapterHub.includes(snippet)) {
      violations.push({
        file: ADAPTER_HUB,
        line: 1,
        ruleId:
          "b0.integration-contract-package-reference.required-snippet-missing",
        message: `B0 第三方数据契约配置包选择必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !adapterHubTest.includes(
      "loads data contract package selector through small server-side pages",
    )
  ) {
    violations.push({
      file: ADAPTER_HUB_TEST,
      line: 1,
      ruleId:
        "b0.integration-contract-package-reference-test.required-snippet-missing",
      message: "第三方数据契约配置包小页服务端搜索缺少回归测试。",
    });
  }
  const adapterTerminologyMappingFixedSnapshotPattern =
    /useTerminologyMappings\(\s*\{[\s\S]*?\bsize:\s*100\b/;
  if (adapterTerminologyMappingFixedSnapshotPattern.test(adapterHub)) {
    violations.push({
      file: ADAPTER_HUB,
      line: lineOf(adapterHub, adapterTerminologyMappingFixedSnapshotPattern),
      ruleId:
        "b0.integration-terminology-mapping-reference.fixed-snapshot-forbidden",
      message:
        "第三方接入字段映射不得固定读取 100 条术语映射，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "TERMINOLOGY_MAPPING_REFERENCE_PAGE_SIZE = 20",
    "terminologyMappingSearch",
    "setTerminologyMappingSearch",
    "size: TERMINOLOGY_MAPPING_REFERENCE_PAGE_SIZE",
    "keyword: terminologyMappingSearch",
    "filterOption={false}",
    "onSearch={setTerminologyMappingSearch}",
    'onClear={() => setTerminologyMappingSearch("")}',
    "可选，选择已确认映射",
  ]) {
    if (!adapterHub.includes(snippet)) {
      violations.push({
        file: ADAPTER_HUB,
        line: 1,
        ruleId:
          "b0.integration-terminology-mapping-reference.required-snippet-missing",
        message: `B0 第三方接入术语映射选择必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !adapterHubTest.includes(
      "loads terminology mapping selector through small server-side pages",
    )
  ) {
    violations.push({
      file: ADAPTER_HUB_TEST,
      line: 1,
      ruleId:
        "b0.integration-terminology-mapping-reference-test.required-snippet-missing",
      message: "第三方接入术语映射小页服务端搜索缺少回归测试。",
    });
  }

  const ruleDefinitionPackageFixedSnapshotPattern =
    /rulePackagesQuery\s*=\s*usePackages\(\{[^}]*size:\s*100[^}]*assetType:\s*"RULE"[^}]*\}\)|rulePackagesQuery\s*=\s*usePackages\(\{[^}]*assetType:\s*"RULE"[^}]*size:\s*100[^}]*\}\)/s;
  if (ruleDefinitionPackageFixedSnapshotPattern.test(ruleDefinitions)) {
    violations.push({
      file: RULE_DEFINITIONS,
      line: lineOf(ruleDefinitions, ruleDefinitionPackageFixedSnapshotPattern),
      ruleId:
        "b0.rule-definition-package-reference.fixed-snapshot-forbidden",
      message:
        "规则维护不得用 100 条 RULE 配置包快照解析包版本，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "RULE_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "size: RULE_PACKAGE_REFERENCE_PAGE_SIZE",
    'assetType: "RULE"',
    "keyword: rulePackageSearch || undefined",
    "filterOption={false}",
    "onSearch={setRulePackageSearch}",
    "选择当前已审核的标准上下文包版本",
    "选择规则配置包版本",
  ]) {
    if (!ruleDefinitions.includes(snippet)) {
      violations.push({
        file: RULE_DEFINITIONS,
        line: 1,
        ruleId: "b0.rule-definition-package-reference.required-snippet-missing",
        message: `B0 规则维护配置包选择必须保持小页服务端搜索：${snippet}`,
      });
    }
  }
  if (
    !ruleDefinitionsTest.includes(
      "规则包版本选择器通过小页服务端搜索加载",
    )
  ) {
    violations.push({
      file: RULE_DEFINITIONS_TEST,
      line: 1,
      ruleId: "b0.rule-definition-package-reference-test.required-snippet-missing",
      message: "规则维护配置包小页服务端搜索缺少回归测试。",
    });
  }
  const ruleConditionFragmentFixedSnapshotPattern =
    /fragmentLibraryQuery\s*=\s*useConditionFragments\(\s*\{[\s\S]*?\bsize:\s*100\b[\s\S]*?\bsort:\s*"fragmentCode,asc"/;
  if (ruleConditionFragmentFixedSnapshotPattern.test(ruleDefinitions)) {
    violations.push({
      file: RULE_DEFINITIONS,
      line: lineOf(ruleDefinitions, ruleConditionFragmentFixedSnapshotPattern),
      ruleId:
        "b0.rule-condition-fragment-library.fixed-snapshot-forbidden",
      message:
        "规则条件片段库不得用 100 条固定快照，必须使用小页服务端搜索和服务端分页。",
    });
  }
  for (const snippet of [
    "RULE_FRAGMENT_LIBRARY_PAGE_SIZE = 20",
    "fragmentLibraryPage",
    "setFragmentLibraryPage",
    "fragmentLibrarySearch",
    "setFragmentLibrarySearch",
    "fragmentLibraryKeyword",
    "keyword: fragmentLibraryKeyword",
    "page: fragmentLibraryPage",
    "size: RULE_FRAGMENT_LIBRARY_PAGE_SIZE",
    'aria-label="检索条件片段"',
    "current: fragmentLibraryQuery.data?.page ?? fragmentLibraryPage",
    "pageSize: fragmentLibraryQuery.data?.size ?? RULE_FRAGMENT_LIBRARY_PAGE_SIZE",
    "total: fragmentLibraryQuery.data?.total ?? fragmentLibraryItems.length",
    "showSizeChanger: false",
    "onChange: (page) => setFragmentLibraryPage(page)",
  ]) {
    if (!ruleDefinitions.includes(snippet)) {
      violations.push({
        file: RULE_DEFINITIONS,
        line: 1,
        ruleId:
          "b0.rule-condition-fragment-library.required-snippet-missing",
        message: `B0 规则条件片段库必须保持小页服务端搜索和服务端分页：${snippet}`,
      });
    }
  }
  if (
    !ruleDefinitionsTest.includes(
      "条件片段库通过小页服务端搜索加载",
    )
  ) {
    violations.push({
      file: RULE_DEFINITIONS_TEST,
      line: 1,
      ruleId:
        "b0.rule-condition-fragment-library-test.required-snippet-missing",
      message: "规则条件片段库小页服务端搜索缺少回归测试。",
    });
  }

  const terminologyReleasePackageFixedSnapshotPattern =
    /packages\s*=\s*usePackages\(\{[^}]*page:\s*0[^}]*size:\s*10[^}]*assetType:\s*"TERMINOLOGY"[^}]*\}\)|packages\s*=\s*usePackages\(\{[^}]*assetType:\s*"TERMINOLOGY"[^}]*page:\s*0[^}]*size:\s*10[^}]*\}\)/s;
  if (
    terminologyReleasePackageFixedSnapshotPattern.test(terminologyMapping) ||
    /selectedPackage\s*=\s*packageItems\[0\]/.test(terminologyMapping)
  ) {
    violations.push({
      file: TERMINOLOGY_MAPPING,
      line: lineOf(
        terminologyMapping,
        terminologyReleasePackageFixedSnapshotPattern.test(terminologyMapping)
          ? terminologyReleasePackageFixedSnapshotPattern
          : /selectedPackage\s*=\s*packageItems\[0\]/,
      ),
      ruleId:
        "b0.terminology-release-package-reference.fixed-snapshot-forbidden",
      message:
        "术语映射包发布不得固定 10 条包快照并默认首条，必须使用小页服务端搜索并由用户选择发布包。",
    });
  }
  for (const snippet of [
    "TERMINOLOGY_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "size: TERMINOLOGY_PACKAGE_REFERENCE_PAGE_SIZE",
    'assetType: "TERMINOLOGY"',
    "keyword: packageSearch || undefined",
    "selectedPackageId",
    'aria-label="选择映射包"',
    "filterOption={false}",
    "onSearch={setPackageSearch}",
    "onChange={setSelectedPackageId}",
  ]) {
    if (!terminologyMapping.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_MAPPING,
        line: 1,
        ruleId:
          "b0.terminology-release-package-reference.required-snippet-missing",
        message: `B0 术语映射包发布必须保持小页服务端搜索和显式选包：${snippet}`,
      });
    }
  }
  if (
    !terminologyMappingTest.includes(
      "loads terminology release packages through small server-side search pages and publishes the selected package",
    )
  ) {
    violations.push({
      file: TERMINOLOGY_MAPPING_TEST,
      line: 1,
      ruleId:
        "b0.terminology-release-package-reference-test.required-snippet-missing",
      message: "术语映射包发布小页搜索和显式选包缺少回归测试。",
    });
  }

  const configPackageUnsafeFallbacks = [
    {
      pattern: /defaultPackageVersion\s*(?:\?\?|\|\|)\s*["']ONBOARDING["']/,
      ruleId: "b0.config-package-version.template-default-forbidden",
      message:
        "配置包首发模板不得用 ONBOARDING 冒充缺失的默认配置包版本，必须阻断。",
    },
    {
      pattern:
        /packageVersion:\s*selectedPackage\?\.packageVersion\s*\|\|\s*values\.assetVersion/,
      ruleId: "b0.config-package-version.asset-version-fallback-forbidden",
      message:
        "配置包资产关联不得用资产版本冒充配置包版本，缺包版本时必须阻断。",
    },
    {
      pattern:
        /packageVersion:\s*selectedPackage\?\.packageVersion\s*\|\|\s*["']["']/,
      ruleId: "b0.config-package-version.empty-release-fallback-forbidden",
      message:
        "配置包同步发布不得用空字符串兜底配置包版本，缺包版本时必须阻断。",
    },
  ];
  for (const { pattern, ruleId, message } of configPackageUnsafeFallbacks) {
    if (pattern.test(configPackages)) {
      violations.push({
        file: CONFIG_PACKAGES,
        line: lineOf(configPackages, pattern),
        ruleId,
        message,
      });
    }
  }
  const configPackageItemFixedSnapshotPatterns = [
    {
      pattern: /useAuthoringAssets\(\s*\{[^}]*size:\s*100/s,
      ruleId: "b0.config-package-item-asset-reference.fixed-authoring-snapshot-forbidden",
      message:
        "配置包包内资产选择不得用 100 条统一资产快照，必须使用小页服务端搜索。",
    },
    {
      pattern: /useEvaluationIndicators\(\s*\{[^}]*size:\s*100/s,
      ruleId: "b0.config-package-item-asset-reference.fixed-evaluation-snapshot-forbidden",
      message:
        "配置包包内质控指标选择不得用 100 条评估指标快照，必须使用小页服务端过滤。",
    },
    {
      pattern:
        /usePackages\(\s*\{[^}]*size:\s*100[^}]*assetType:\s*"TERMINOLOGY"[^}]*\}/s,
      ruleId: "b0.config-package-item-asset-reference.fixed-terminology-snapshot-forbidden",
      message:
        "配置包包内术语资产选择不得用 100 条术语包快照，必须使用小页服务端搜索。",
    },
  ];
  for (const { pattern, ruleId, message } of configPackageItemFixedSnapshotPatterns) {
    if (pattern.test(configPackages)) {
      violations.push({
        file: CONFIG_PACKAGES,
        line: lineOf(configPackages, pattern),
        ruleId,
        message,
      });
    }
  }
  for (const snippet of [
    "requireSelectedPackageVersion",
    "首发模板缺少默认配置包版本，暂不能应用平台引用。",
    "当前配置包缺少版本，暂不能添加资产条目。",
    "当前配置包缺少版本，暂不能同步发布。",
    "PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE = 20",
    "packageItemAssetSearch",
    "size: PACKAGE_ITEM_ASSET_REFERENCE_PAGE_SIZE",
    "keyword: packageItemAssetKeyword",
    "indicatorCode: packageItemAssetKeyword",
    'assetType: "TERMINOLOGY"',
    "filterOption={false}",
    "onSearch={setPackageItemAssetSearch}",
  ]) {
    if (!configPackages.includes(snippet)) {
      violations.push({
        file: CONFIG_PACKAGES,
        line: 1,
        ruleId: "b0.config-package-version.required-snippet-missing",
        message: `配置包发布和包内资产关联必须保留真实 packageVersion 前置阻断：${snippet}`,
      });
    }
  }
  for (const snippet of [
    "blocks first-run template references when the template lacks a default package version",
    "blocks release when the selected package version is missing",
    "blocks package item creation when the selected package version is missing",
  ]) {
    if (!configPackagesTest.includes(snippet)) {
      violations.push({
        file: CONFIG_PACKAGES_TEST,
        line: 1,
        ruleId: "b0.config-package-version-test.required-snippet-missing",
        message: `配置包真实 packageVersion 阻断缺少回归测试：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "PATHWAY_ROLLBACK_TARGET_PAGE_SIZE = 20",
    "PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE = 20",
    'assetType: "EVALUATION"',
    "outcomeIndicatorSearch",
    "setOutcomeIndicatorSearch",
    "outcomePackageSearch",
    "setOutcomePackageSearch",
    "size: PATHWAY_ROLLBACK_TARGET_PAGE_SIZE",
    "size: PATHWAY_OUTCOME_REFERENCE_PAGE_SIZE",
    "keyword: outcomePackageKeyword",
    "indicatorCode: outcomeIndicatorKeyword",
    "outcomeIndicatorByCode",
    "outcomeIndicatorPackageOptions",
    "onSearch={setOutcomeIndicatorSearch}",
    "onSearch={setOutcomePackageSearch}",
    "选择评估指标所属配置包版本",
    '["outcomeBindings", field.name, "packageVersion"]',
  ]) {
    if (!pathwayTemplates.includes(snippet)) {
      violations.push({
        file: PATHWAY_TEMPLATES,
        line: 1,
        ruleId: "b0.pathway-outcome-package-version.required-snippet-missing",
        message: `B0 路径结局指标必须绑定受控评估配置包版本，禁止默认路径包版本或手写未知版本：${snippet}`,
      });
    }
  }
  const pathwayOutcomeFixedSnapshotPattern =
    /usePathwayTemplates\(\s*\{[\s\S]*?status:\s*"OFFLINE"[\s\S]*?\bsize:\s*100\b|usePackages\(\s*\{[\s\S]*?\bsize:\s*100\b[\s\S]*?assetType:\s*"EVALUATION"|useEvaluationIndicators\(\s*\{[\s\S]*?\bsize:\s*100\b/s;
  if (pathwayOutcomeFixedSnapshotPattern.test(pathwayTemplates)) {
    violations.push({
      file: PATHWAY_TEMPLATES,
      line: lineOf(pathwayTemplates, pathwayOutcomeFixedSnapshotPattern),
      ruleId:
        "b0.pathway-outcome-reference.fixed-snapshot-forbidden",
      message:
        "路径回滚目标、结局指标包和结局指标不得固定读取 100 条快照，必须使用小页服务端查询。",
    });
  }
  if (
    !pathwayTemplatesTest.includes(
      "路径结局指标和回滚目标使用小页服务端查询",
    )
  ) {
    violations.push({
      file: PATHWAY_TEMPLATES_TEST,
      line: 1,
      ruleId:
        "b0.pathway-outcome-reference-test.required-snippet-missing",
      message: "路径回滚目标与结局指标小页服务端查询缺少回归测试。",
    });
  }
  if (/String\([^)]*templateVersion[^)]*\)/.test(pathwayTemplates)) {
    violations.push({
      file: PATHWAY_TEMPLATES,
      line: lineOf(pathwayTemplates, /String\([^)]*templateVersion[^)]*\)/),
      ruleId: "b0.pathway-template-package-version.fallback-forbidden",
      message: "路径模板维护/发布/仿真不得用模板版本冒充配置包版本。",
    });
  }
  const pathwayPackageFixedSnapshotPattern =
    /usePackages\(\{[^}]*size:\s*100[^}]*assetType:\s*"PATHWAY"[^}]*\}\)|usePackages\(\{[^}]*assetType:\s*"PATHWAY"[^}]*size:\s*100[^}]*\}\)/s;
  if (pathwayPackageFixedSnapshotPattern.test(pathwayTemplates)) {
    violations.push({
      file: PATHWAY_TEMPLATES,
      line: lineOf(pathwayTemplates, pathwayPackageFixedSnapshotPattern),
      ruleId: "b0.pathway-template-package-reference.fixed-snapshot-forbidden",
      message:
        "路径模板维护页不得用 100 条 PATHWAY 配置包快照解析版本，必须使用小页服务端搜索。",
    });
  }
  for (const snippet of [
    "PATHWAY_PACKAGE_REFERENCE_PAGE_SIZE = 20",
    "size: PATHWAY_PACKAGE_REFERENCE_PAGE_SIZE",
    'assetType: "PATHWAY"',
    "filterOption={false}",
    "onSearch={setPackageSearch}",
    'createTemplatePackageVersion = selectedTemplatePackageVersion ?? ""',
    "requirePathwayPackageVersion",
    "无法确认路径模板所属的配置包版本，暂不能创建或复制路径。",
    "无法确认当前路径模板所属的配置包版本，暂不能发布路径。",
    "无法确认当前路径模板所属的配置包版本，暂不能试运行路径。",
  ]) {
    if (!pathwayTemplates.includes(snippet)) {
      violations.push({
        file: PATHWAY_TEMPLATES,
        line: 1,
        ruleId:
          "b0.pathway-template-package-reference.required-snippet-missing",
        message: `B0 路径模板维护必须保持小页服务端搜索与包版本安全阻断：${snippet}`,
      });
    }
  }
  for (const snippet of [
    "创建路径草稿缺少真实配置包版本时阻断提交",
    "路径模板配置包引用使用小页服务端搜索",
    "路径试运行缺少真实配置包版本时阻断",
    "路径发布缺少真实配置包版本时阻断灰度发布",
    "全量激活缺少真实配置包版本时阻断",
  ]) {
    if (!pathwayTemplatesTest.includes(snippet)) {
      violations.push({
        file: PATHWAY_TEMPLATES_TEST,
        line: 1,
        ruleId:
          "b0.pathway-template-package-reference-test.required-snippet-missing",
        message: `路径模板维护包版本安全缺少回归测试：${snippet}`,
      });
    }
  }

  pushMissing(
    violations,
    HANDOFF,
    handoff,
    "B0 第一阶段全功能核查与完美化",
    "b0.handoff.mainline-missing",
    "_HANDOFF 必须明确当前主线是 B0 第一阶段全功能核查与完美化。",
  );
  pushMissing(
    violations,
    HANDOFF,
    handoff,
    "国产化真实环境本轮暂不处理",
    "b0.handoff.domestic-scope-missing",
    "_HANDOFF 必须明确国产化真实环境不属于本轮完成口径。",
  );
  for (const stalePhrase of ["待拆卡整改", "已实现待合"]) {
    if (handoff.includes(stalePhrase)) {
      violations.push({
        file: HANDOFF,
        line: lineOf(handoff, stalePhrase),
        ruleId: "b0.handoff.stale-status",
        message: `B0 接力状态不能保留过期口径：${stalePhrase}`,
      });
    }
  }
  for (const stalePhrase of [
    "V133 改为持久化异步任务",
    "V133 任务表",
    "迁移到 133",
  ]) {
    if (deferredIssues.includes(stalePhrase)) {
      violations.push({
        file: DEFERRED_ISSUES,
        line: lineOf(deferredIssues, stalePhrase),
        ruleId: "b0.deferred-issues.stale-terminology-migration-version",
        message: `待处理问题清单中的术语候选任务迁移版本必须同步为 V135：${stalePhrase}`,
      });
    }
  }

  for (const section of ["## 当前整改状态", "## 本轮国产化边界"]) {
    pushMissing(
      violations,
      AUDIT_REPORT,
      report,
      section,
      "b0.audit-report.required-section-missing",
      `B0 审计报告缺少章节：${section}`,
    );
  }
  pushMissing(
    violations,
    AUDIT_REPORT,
    report,
    "国产化真实环境本轮暂不处理",
    "b0.audit-report.domestic-scope-missing",
    "B0 审计报告必须明确国产化真实环境本轮暂不处理。",
  );

  if (sandboxRules.trim()) {
    try {
      const manifest = JSON.parse(sandboxRules);
      validateScenarioRules(manifest);
      const seedSelection = selectSeedRules(manifest);
      const approvedCodes = seedSelection.runnable.map((item) => item.ruleCode);
      const expectedApprovedCodes = ["SBX.LAB.CRITICAL.K"];
      if (
        approvedCodes.length !== expectedApprovedCodes.length ||
        approvedCodes.some(
          (code, index) => code !== expectedApprovedCodes[index],
        ) ||
        seedSelection.blocked.length !== 9
      ) {
        violations.push({
          file: SANDBOX_RULES,
          line: lineOf(sandboxRules, "APPROVED_FOR_SANDBOX"),
          ruleId: "b0.sandbox.approved-scope-drift",
          message:
            "B0 当前只允许开放高钾危急值金样，其余 9 个沙盘场景必须保持临床评审阻断。",
        });
      }
    } catch (error) {
      violations.push({
        file: SANDBOX_RULES,
        line: 1,
        ruleId: "b0.sandbox.scenario-rules-invalid",
        message: `沙盘规则清单未通过 B0 门禁校验：${error instanceof Error ? error.message : String(error)}`,
      });
    }
  }

  for (const snippet of [
    "b0-login-desktop",
    "b0-header-user-menu",
    "b0-rule-definitions-desktop",
    "b0-rule-definitions-390px",
    "b0-config-packages-release-modal",
    "b0-screenshot-chain-runtime-records",
    "collectBrowserErrors",
    "collectServerErrors",
    "collectNetworkFailures",
    "testInfo.attach",
    "/rule/definitions",
    "/config/packages",
  ]) {
    if (!screenshotChain.includes(snippet)) {
      violations.push({
        file: PLAYWRIGHT_SCREENSHOT_CHAIN,
        line: 1,
        ruleId: "b0.playwright-screenshot-chain.required-snippet-missing",
        message: `B0 Playwright 截图链缺少必需证据点：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "PostgreSQLContainer",
    "OracleContainer",
    "TOTAL_ROWS = 100_000",
    "B0_100K_DIALECT_SMOKE",
    "postgresHandlesHundredThousandKnowledgeAndTerminologyRows",
    "oracleHandlesHundredThousandKnowledgeAndTerminologyRows",
    "seedKnowledgeIdentities",
    "seedTerminologyRows",
    "refreshTerminologyPlannerStatsSql",
    "refreshTerminologySourceStatsSql",
    "assertKnowledgeExportEquivalentScan",
    "assertKnowledgeExportEquivalentFile",
    "Files.newBufferedWriter",
    "percentile95",
    "assertCandidateAndConflictQueries",
    "assertCandidateAndConflictPageP95",
    "knowledge_identity",
    "standard_term",
    "local_term",
    "term_mapping",
    "mapping_candidate",
    "mapping_conflict",
  ]) {
    if (!largeScaleDialectSmoke.includes(snippet)) {
      violations.push({
        file: LARGE_SCALE_DIALECT_SMOKE,
        line: 1,
        ruleId: "b0.large-scale-dialect-smoke.required-snippet-missing",
        message: `B0 10 万级真实方言压测规格缺少必需证据点：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "API-03 知识异步导出的 B0 本地 10 万级链路合同",
    "submitPollAndDownloadExportsHundredThousandFilteredIdentities",
    "seedKnowledgeIdentities(100_000",
    "service.submit",
    "service.get",
    "service.downloadFile",
    "ExportType.IDENTITIES",
    '{"domain":"DRUG","status":"ACTIVE"}',
    "100_000L",
    "Propagation.NOT_SUPPORTED",
  ]) {
    if (!knowledgeExportLargeScale.includes(snippet)) {
      violations.push({
        file: KNOWLEDGE_EXPORT_LARGE_SCALE,
        line: 1,
        ruleId: "b0.knowledge-export-large-scale.required-snippet-missing",
        message: `B0 知识 10 万级异步导出链路合同缺少必需证据点：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "pageByTenantIdAndSourceSystemAndStatus",
    "pageByTenantIdsAndStatus",
    "StandardTermGenerationIndex",
    "candidatesFor",
  ]) {
    if (!terminologyService.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_SERVICE,
        line: 1,
        ruleId: "b0.terminology-generation.required-snippet-missing",
        message: `B0 术语候选生成必须保持分页读取和索引召回：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "TerminologyCandidateGenerationJob",
    "TerminologyCandidateGenerationJobStatus.PENDING",
    "dispatchCandidateGenerationAfterCommit",
    "TransactionSynchronizationManager.registerSynchronization",
    "terminologyCandidateGenerationExecutor.execute",
    "executeCandidateGenerationJob",
    "generateCandidateRowsForJob",
    "candidatePageUri",
  ]) {
    if (!terminologyService.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_SERVICE,
        line: 1,
        ruleId: "b0.terminology-generation-async.required-snippet-missing",
        message: `B0 术语候选生成必须保持异步任务形态，禁止同步返回大批候选：${snippet}`,
      });
    }
  }

  for (const snippet of [
    '@GetMapping("/mappings/candidates")',
    "generationJobCode",
    "ApiResult<TerminologyCandidateGenerationJob>",
    '@GetMapping("/mappings/candidate-generation-jobs/{jobCode}")',
    "getCandidateGenerationJob",
  ]) {
    if (!terminologyController.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_CONTROLLER,
        line: 1,
        ruleId: "b0.terminology-generation-api.required-snippet-missing",
        message: `B0 术语候选生成 API 必须保留任务状态查询和按任务分页取候选：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "generateCandidatesSubmitsAsyncJobAndDoesNotReturnCandidateRows",
    "generateCandidatesDefersWorkerDispatchUntilCommit",
    "executeCandidateGenerationJobMarksSucceededAndLinksPagedCandidates",
    "generationJobCode",
  ]) {
    if (!terminologyServiceTest.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_SERVICE_TEST,
        line: 1,
        ruleId:
          "b0.terminology-generation-service-test.required-snippet-missing",
        message: `B0 术语候选异步任务缺少服务层合同测试：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "generateCandidatesReturnsAsyncJobInsteadOfCandidateRows",
    "candidateGenerationJobStatusUsesDedicatedApi04Route",
    "candidatePageUri",
    "generationJobCode",
  ]) {
    if (!terminologyApiContract.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_API_CONTRACT,
        line: 1,
        ruleId: "b0.terminology-generation-api-test.required-snippet-missing",
        message: `B0 术语候选异步任务缺少 API 合同测试：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "mk_term_candidate_generation_job",
    "generation_job_code",
    "candidatePageUri",
    "避免同步响应返回大批量明细",
  ]) {
    if (!terminologyCandidateGenerationJob.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_CANDIDATE_GENERATION_JOB,
        line: 1,
        ruleId: "b0.terminology-generation-job-entity.required-snippet-missing",
        message: `B0 术语候选异步任务实体缺少必需契约：${snippet}`,
      });
    }
  }

  for (const { file, content } of terminologyJobMigrations) {
    for (const snippet of [
      "mk_term_candidate_generation_job",
      "generation_job_code",
      "candidate_page_uri",
      "idx_mk_term_candidate_generation_job_tenant",
      "idx_mapping_candidate_generation_job",
    ]) {
      if (!content.includes(snippet)) {
        violations.push({
          file,
          line: 1,
          ruleId:
            "b0.terminology-generation-migration.required-snippet-missing",
          message: `B0 术语候选异步任务五方言迁移缺少必需契约：${snippet}`,
        });
      }
    }
  }

  for (const snippet of [
    "candidateAndConflictRepositoriesHandleHundredThousandRowsWithinLocalBudget",
    "seedMappingCandidates(100_000)",
    "seedMappingConflicts(100_000)",
    "pageCandidates",
    "pageConflicts",
    "100_000L",
  ]) {
    if (!terminologyLargeScale.includes(snippet)) {
      violations.push({
        file: TERMINOLOGY_LARGE_SCALE,
        line: 1,
        ruleId: "b0.terminology-large-scale.required-snippet-missing",
        message: `B0 术语候选/冲突 10 万级合同缺少必需证据点：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "ContextFactBridge",
    "ContextSnapshotResources",
    "conditionContext",
    "observations",
    "observation.",
  ]) {
    if (!contextFactBridge.includes(snippet)) {
      violations.push({
        file: CONTEXT_FACT_BRIDGE,
        line: 1,
        ruleId: "b0.pathway-context-fact-bridge.required-snippet-missing",
        message: `B0 路径字段目录 canonical path 桥接缺少必需契约：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "ContextFactBridge.conditionContext",
    "ContextFactBridge.facts",
  ]) {
    if (!pathwayEngineService.includes(snippet)) {
      violations.push({
        file: PATHWAY_ENGINE_SERVICE,
        line: 1,
        ruleId: "b0.pathway-context-fact-bridge.required-snippet-missing",
        message: `B0 路径运行必须消费统一上下文字段桥接：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "enterPatientPathwayUsesCanonicalObservationPathForEntryIncludeCriteria",
    "enterPatientPathwayUsesCanonicalObservationPathForEntryExcludeCriteria",
    "exitAllowsCanonicalObservationPathForExitIncludeCriteria",
    "exitRejectsCanonicalObservationPathForExitExcludeCriteria",
    "observations[].valueNumeric",
  ]) {
    if (!pathwayEngineServiceTest.includes(snippet)) {
      violations.push({
        file: PATHWAY_ENGINE_SERVICE_TEST,
        line: 1,
        ruleId: "b0.pathway-context-fact-bridge.required-snippet-missing",
        message: `B0 路径字段目录 canonical path 回归测试缺少必需证据点：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "rejectUnsupportedConstraintCriteria(versionCriteria)",
    "hasText(criterion.valueConstraint()) || hasText(criterion.temporalConstraint())",
    "ErrorCode.ENG_DX_006",
    "暂不可发布",
    "parseFindings",
    "LinkedHashSet<String> findings",
    "findings.add(normalized)",
    "Set.copyOf(findings)",
  ]) {
    if (!diagnosisKnowledgeService.includes(snippet)) {
      violations.push({
        file: DIAGNOSIS_KNOWLEDGE_SERVICE,
        line: 1,
        ruleId: "b0.diagnosis-publish-safety.required-snippet-missing",
        message: `B0 诊断发布安全必须保持 value/time 阻断和 findings 去重：${snippet}`,
      });
    }
  }

  if (!diagnosisKnowledgeService.includes("references.validateCriterion")) {
    violations.push({
      file: DIAGNOSIS_KNOWLEDGE_SERVICE,
      line: 1,
      ruleId: "b0.diagnosis-criterion-reference.required-snippet-missing",
      message:
        "B0 诊断标准新增必须先调用引用校验器 references.validateCriterion。",
    });
  }

  for (const snippet of [
    "FINDING_DICTIONARIES",
    "TERM.DIAGNOSIS",
    "TERM.LAB",
    "TERM.DRUG",
    "TERM.PROCEDURE",
    "validateCriterion",
    "validateFindingTerm",
    "findFirstActiveByTenantIdsAndStandardSystemAndTermCode",
    "validateCitation",
    "citations.findByTenantIdAndId",
    "citation.assetVersionId()",
  ]) {
    if (!diagnosisReferenceValidator.includes(snippet)) {
      violations.push({
        file: DIAGNOSIS_REFERENCE_VALIDATOR,
        line: 1,
        ruleId: "b0.diagnosis-criterion-reference.required-snippet-missing",
        message: `B0 诊断标准新增必须校验 TERM-01 发现项和当前版本证据引用：${snippet}`,
      });
    }
  }

  if (!citationRepository.includes("findByTenantIdAndId")) {
    violations.push({
      file: CITATION_REPOSITORY,
      line: 1,
      ruleId: "b0.diagnosis-criterion-reference.required-snippet-missing",
      message:
        "B0 诊断标准证据引用校验必须使用租户隔离 citation 查询 findByTenantIdAndId。",
    });
  }

  for (const snippet of [
    "addCriterionRejectsInvalidFindingOrCitationBeforePersisting",
    "publishGateRejectsCriteriaWithUnevaluatedValueOrTemporalConstraint",
    "publishGateParsesThirdPartyFindingListsWithoutDuplicateOrBlankNoise",
    "valueConstraint",
    "temporalConstraint",
    "CASE-THIRD-PARTY",
    "暂不可发布",
  ]) {
    if (!diagnosisKnowledgeServiceTest.includes(snippet)) {
      violations.push({
        file: DIAGNOSIS_KNOWLEDGE_SERVICE_TEST,
        line: 1,
        ruleId: "b0.diagnosis-publish-safety-test.required-snippet-missing",
        message: `B0 诊断发布安全缺少 value/time 阻断或 findings 去重回归测试：${snippet}`,
      });
    }
  }

  for (const snippet of [
    "criterionRequiresActiveStandardFindingTermFromRuntimeDictionaries",
    "criterionCitationMustBelongToCurrentDiagnosisVersion",
    "TERM.LAB",
    "LOINC-EGFR",
    "findByTenantIdAndId",
  ]) {
    if (!diagnosisReferenceValidatorTest.includes(snippet)) {
      violations.push({
        file: DIAGNOSIS_REFERENCE_VALIDATOR_TEST,
        line: 1,
        ruleId:
          "b0.diagnosis-criterion-reference-test.required-snippet-missing",
        message: `B0 诊断标准引用校验缺少术语或 citation 归属回归测试：${snippet}`,
      });
    }
  }

  return { violations };
}

export function hasBlockingViolations(report) {
  return report.violations.length > 0;
}

function printReport(report) {
  console.log(`B0 完美化门禁扫描完成，阻断项 ${report.violations.length} 个。`);
  for (const violation of report.violations) {
    console.log(
      `${violation.file}:${violation.line} [${violation.ruleId}] ${violation.message}`,
    );
  }
  if (!report.violations.length) {
    console.log("B0 完美化门禁通过。");
  }
}

async function main() {
  const report = await scanRepository(process.cwd());
  printReport(report);
  if (hasBlockingViolations(report)) {
    process.exit(1);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await main();
}
