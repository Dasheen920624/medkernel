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
const SECURITY_BASELINE_PANELS =
  "frontend/src/pages/compliance/SecurityBaselinePanels.tsx";
const SECURITY_BASELINE_TEST =
  "frontend/src/pages/compliance/SecurityBaseline.test.tsx";
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
const DATA_PERMISSION_POLICY_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionPolicyRepository.java";
const DATA_PERMISSION_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionService.java";
const DATA_PERMISSION_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/compliance/datapermission/DataPermissionController.java";
const DATA_PERMISSION_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/compliance/datapermission/DataPermissionServiceTest.java";
const DATA_PERMISSION_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/compliance/datapermission/DataPermissionControllerSecurityTest.java";
const MASKING_RULE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/compliance/masking/MaskingRuleRepository.java";
const MASKING_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/compliance/masking/MaskingService.java";
const MASKING_RULE_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/compliance/masking/MaskingRuleController.java";
const MASKING_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/compliance/masking/MaskingServiceTest.java";
const MASKING_RULE_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/compliance/masking/MaskingRuleControllerSecurityTest.java";
const KNOWLEDGE_CUSTOMIZATION_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationRepository.java";
const KNOWLEDGE_CUSTOMIZATION_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationService.java";
const KNOWLEDGE_CUSTOMIZATION_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCustomizationController.java";
const KNOWLEDGE_CUSTOMIZATION_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeCustomizationServiceTest.java";
const KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionCandidateRepository.java";
const KNOWLEDGE_PRODUCTION_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationService.java";
const KNOWLEDGE_PRODUCTION_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionController.java";
const KNOWLEDGE_PRODUCTION_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationServiceTest.java";
const KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionCandidateRepositoryIntegrationTest.java";
const KNOWLEDGE_PRODUCTION_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionControllerSecurityTest.java";
const CANDIDATE_PROVENANCE_REQUEST =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateProvenanceRequest.java";
const CANDIDATE_PROVENANCE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/CandidateProvenanceService.java";
const CANDIDATE_PROVENANCE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/CandidateProvenanceServiceTest.java";
const DOC_PARSE_JOB_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocParseJobRepository.java";
const DOCUMENT_PARSE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationService.java";
const DOCUMENT_PARSE_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocumentParseController.java";
const DOCUMENT_PARSE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationServiceTest.java";
const DOCUMENT_PARSE_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseControllerSecurityTest.java";
const KNOWLEDGE_EXPORT_JOB_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportJobRepository.java";
const KNOWLEDGE_EXPORT_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportService.java";
const KNOWLEDGE_EXPORT_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportController.java";
const KNOWLEDGE_EXPORT_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeExportServiceTest.java";
const KNOWLEDGE_IDENTITY_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityControllerSecurityTest.java";
const ENGINE_DATA_EXPORT_JOB_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportJobRepository.java";
const ENGINE_DATA_EXPORT_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/export/EngineDataExportService.java";
const ENGINE_DATA_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/datasvc/EngineDataController.java";
const ENGINE_DATA_EXPORT_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/datasvc/export/EngineDataExportServiceTest.java";
const ENGINE_DATA_EXPORT_JOB_REPOSITORY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/datasvc/export/EngineDataExportJobRepositoryIntegrationTest.java";
const ENGINE_DATA_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/datasvc/EngineDataControllerSecurityTest.java";
const KNOWLEDGE_IDENTITY_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepository.java";
const KNOWLEDGE_IDENTITY_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityController.java";
const KNOWLEDGE_IDENTITY_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java";
const KNOWLEDGE_IDENTITY_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityServiceTest.java";
const KNOWLEDGE_IDENTITY_REPOSITORY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepositoryTest.java";
const KNOWLEDGE_ASSET_VERSION_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeAssetVersionRepository.java";
const CANDIDATE_CLASSIFICATION_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/CandidateClassificationRepository.java";
const KNOWLEDGE_CANDIDATE_RESPONSE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateResponse.java";
const KNOWLEDGE_VERSION_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java";
const KNOWLEDGE_VERSION_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionController.java";
const KNOWLEDGE_VERSION_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java";
const KNOWLEDGE_PROVENANCE_RESPONSE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeProvenanceResponse.java";
const KNOWLEDGE_LINEAGE =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeLineage.java";
const KNOWLEDGE_SUPERSESSION_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeSupersessionRepository.java";
const KNOWLEDGE_ASSET_API_CONTRACT_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeAssetApiContractTest.java";
const ADVANCED_PROVENANCE =
  "frontend/src/pages/advanced/Provenance.tsx";
const ADVANCED_PROVENANCE_TEST =
  "frontend/src/pages/advanced/Provenance.test.tsx";
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
const AUTHORING_ASSET_LIBRARY_REPOSITORY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringAssetLibraryRepositoryTest.java";
const CONDITION_FRAGMENT_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/authoring/ConditionFragmentService.java";
const CONDITION_FRAGMENT_IMPACT_RESPONSE =
  "medkernel-backend/src/main/java/com/medkernel/engine/authoring/ConditionFragmentImpactResponse.java";
const CONDITION_FRAGMENT_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/authoring/ConditionFragmentController.java";
const CONDITION_FRAGMENT_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/authoring/ConditionFragmentServiceTest.java";
const CONDITION_FRAGMENT_CONTROLLER_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/authoring/ConditionFragmentControllerTest.java";
const AUTHORING_BATCH_JOB_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobRepository.java";
const AUTHORING_BATCH_JOB_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobService.java";
const AUTHORING_BATCH_JOB_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/authoring/AuthoringBatchJobController.java";
const AUTHORING_BATCH_JOB_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringBatchJobServiceTest.java";
const AUTHORING_BATCH_JOB_CONTROLLER_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/authoring/AuthoringBatchJobControllerTest.java";
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
const RELEASE_GOVERNANCE = "frontend/src/pages/tenant/ReleaseGovernance.tsx";
const RELEASE_GOVERNANCE_TEST =
  "frontend/src/pages/tenant/ReleaseGovernance.test.tsx";
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
const INTEGRATION_ONBOARDING_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/IntegrationOnboardingRepository.java";
const INTEGRATION_WEBHOOK_CONFIG_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/IntegrationWebhookConfigRepository.java";
const REGIONAL_SOURCE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/repository/RegionalSourceRepository.java";
const INTEGRATION_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/service/IntegrationService.java";
const INTEGRATION_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/controller/IntegrationController.java";
const INTEGRATION_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/integration/IntegrationServiceTest.java";
const INTEGRATION_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/integration/IntegrationControllerSecurityTest.java";
const OVERRIDE_TEMPLATE_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/versioning/OverrideTemplateRepository.java";
const OVERRIDE_TEMPLATE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/versioning/OverrideTemplateService.java";
const RELEASE_GOVERNANCE_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/versioning/ReleaseGovernanceController.java";
const OVERRIDE_TEMPLATE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/versioning/OverrideTemplateServiceTest.java";
const RELEASE_GOVERNANCE_CONTROLLER_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/versioning/ReleaseGovernanceControllerTest.java";
const PACKAGE_ENGINE_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java";
const PACKAGE_ENGINE_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineController.java";
const SYNC_LOG_REPOSITORY =
  "medkernel-backend/src/main/java/com/medkernel/engine/pkg/SyncLogRepository.java";
const PACKAGE_ENGINE_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/pkg/PackageEngineServiceTest.java";
const PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/pkg/PackageEngineControllerSecurityTest.java";
const THIRD_PARTY_PACKAGE_RECONCILIATION_RESPONSE =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/runtime/ThirdPartyPackageReconciliationResponse.java";
const THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/runtime/ThirdPartyKnowledgeRuntimeService.java";
const THIRD_PARTY_KNOWLEDGE_RUNTIME_CONTROLLER =
  "medkernel-backend/src/main/java/com/medkernel/engine/integration/runtime/ThirdPartyKnowledgeRuntimeController.java";
const THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE_TEST =
  "medkernel-backend/src/test/java/com/medkernel/engine/integration/runtime/ThirdPartyKnowledgeRuntimeServiceTest.java";
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
  const securityBaselinePanels = readRequired(
    root,
    SECURITY_BASELINE_PANELS,
    violations,
  );
  const securityBaselineTest = readRequired(
    root,
    SECURITY_BASELINE_TEST,
    violations,
  );
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
  const dataPermissionPolicyRepository = readRequired(
    root,
    DATA_PERMISSION_POLICY_REPOSITORY,
    violations,
  );
  const dataPermissionService = readRequired(
    root,
    DATA_PERMISSION_SERVICE,
    violations,
  );
  const dataPermissionController = readRequired(
    root,
    DATA_PERMISSION_CONTROLLER,
    violations,
  );
  const dataPermissionServiceTest = readRequired(
    root,
    DATA_PERMISSION_SERVICE_TEST,
    violations,
  );
  const dataPermissionControllerSecurityTest = readRequired(
    root,
    DATA_PERMISSION_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const maskingRuleRepository = readRequired(
    root,
    MASKING_RULE_REPOSITORY,
    violations,
  );
  const maskingService = readRequired(root, MASKING_SERVICE, violations);
  const maskingRuleController = readRequired(
    root,
    MASKING_RULE_CONTROLLER,
    violations,
  );
  const maskingServiceTest = readRequired(
    root,
    MASKING_SERVICE_TEST,
    violations,
  );
  const maskingRuleControllerSecurityTest = readRequired(
    root,
    MASKING_RULE_CONTROLLER_SECURITY_TEST,
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
  const knowledgeProductionCandidateRepository = readRequired(
    root,
    KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY,
    violations,
  );
  const knowledgeProductionService = readRequired(
    root,
    KNOWLEDGE_PRODUCTION_SERVICE,
    violations,
  );
  const knowledgeProductionController = readRequired(
    root,
    KNOWLEDGE_PRODUCTION_CONTROLLER,
    violations,
  );
  const knowledgeProductionServiceTest = readRequired(
    root,
    KNOWLEDGE_PRODUCTION_SERVICE_TEST,
    violations,
  );
  const knowledgeProductionCandidateRepositoryTest = readRequired(
    root,
    KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY_TEST,
    violations,
  );
  const knowledgeProductionControllerSecurityTest = readRequired(
    root,
    KNOWLEDGE_PRODUCTION_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const candidateProvenanceRequest = readRequired(
    root,
    CANDIDATE_PROVENANCE_REQUEST,
    violations,
  );
  const candidateProvenanceService = readRequired(
    root,
    CANDIDATE_PROVENANCE_SERVICE,
    violations,
  );
  const candidateProvenanceServiceTest = readRequired(
    root,
    CANDIDATE_PROVENANCE_SERVICE_TEST,
    violations,
  );
  const docParseJobRepository = readRequired(
    root,
    DOC_PARSE_JOB_REPOSITORY,
    violations,
  );
  const documentParseService = readRequired(
    root,
    DOCUMENT_PARSE_SERVICE,
    violations,
  );
  const documentParseController = readRequired(
    root,
    DOCUMENT_PARSE_CONTROLLER,
    violations,
  );
  const documentParseServiceTest = readRequired(
    root,
    DOCUMENT_PARSE_SERVICE_TEST,
    violations,
  );
  const documentParseControllerSecurityTest = readRequired(
    root,
    DOCUMENT_PARSE_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const knowledgeExportJobRepository = readRequired(
    root,
    KNOWLEDGE_EXPORT_JOB_REPOSITORY,
    violations,
  );
  const knowledgeExportService = readRequired(
    root,
    KNOWLEDGE_EXPORT_SERVICE,
    violations,
  );
  const knowledgeExportController = readRequired(
    root,
    KNOWLEDGE_EXPORT_CONTROLLER,
    violations,
  );
  const knowledgeExportServiceTest = readRequired(
    root,
    KNOWLEDGE_EXPORT_SERVICE_TEST,
    violations,
  );
  const knowledgeIdentityControllerSecurityTest = readRequired(
    root,
    KNOWLEDGE_IDENTITY_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const engineDataExportJobRepository = readRequired(
    root,
    ENGINE_DATA_EXPORT_JOB_REPOSITORY,
    violations,
  );
  const engineDataExportService = readRequired(
    root,
    ENGINE_DATA_EXPORT_SERVICE,
    violations,
  );
  const engineDataController = readRequired(
    root,
    ENGINE_DATA_CONTROLLER,
    violations,
  );
  const engineDataExportServiceTest = readRequired(
    root,
    ENGINE_DATA_EXPORT_SERVICE_TEST,
    violations,
  );
  const engineDataExportJobRepositoryTest = readRequired(
    root,
    ENGINE_DATA_EXPORT_JOB_REPOSITORY_TEST,
    violations,
  );
  const engineDataControllerSecurityTest = readRequired(
    root,
    ENGINE_DATA_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const knowledgeIdentityRepository = readRequired(
    root,
    KNOWLEDGE_IDENTITY_REPOSITORY,
    violations,
  );
  const knowledgeIdentityController = readRequired(
    root,
    KNOWLEDGE_IDENTITY_CONTROLLER,
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
  const knowledgeAssetVersionRepository = readRequired(
    root,
    KNOWLEDGE_ASSET_VERSION_REPOSITORY,
    violations,
  );
  const candidateClassificationRepository = readRequired(
    root,
    CANDIDATE_CLASSIFICATION_REPOSITORY,
    violations,
  );
  const knowledgeCandidateResponse = readRequired(
    root,
    KNOWLEDGE_CANDIDATE_RESPONSE,
    violations,
  );
  const knowledgeVersionService = readRequired(
    root,
    KNOWLEDGE_VERSION_SERVICE,
    violations,
  );
  const knowledgeVersionController = readRequired(
    root,
    KNOWLEDGE_VERSION_CONTROLLER,
    violations,
  );
  const knowledgeVersionServiceTest = readRequired(
    root,
    KNOWLEDGE_VERSION_SERVICE_TEST,
    violations,
  );
  const knowledgeProvenanceResponse = readRequired(
    root,
    KNOWLEDGE_PROVENANCE_RESPONSE,
    violations,
  );
  const knowledgeLineage = readRequired(root, KNOWLEDGE_LINEAGE, violations);
  const knowledgeSupersessionRepository = readRequired(
    root,
    KNOWLEDGE_SUPERSESSION_REPOSITORY,
    violations,
  );
  const knowledgeAssetApiContractTest = readRequired(
    root,
    KNOWLEDGE_ASSET_API_CONTRACT_TEST,
    violations,
  );
  const advancedProvenance = readRequired(
    root,
    ADVANCED_PROVENANCE,
    violations,
  );
  const advancedProvenanceTest = readRequired(
    root,
    ADVANCED_PROVENANCE_TEST,
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
  const authoringAssetLibraryRepositoryTest = readRequired(
    root,
    AUTHORING_ASSET_LIBRARY_REPOSITORY_TEST,
    violations,
  );
  const conditionFragmentService = readRequired(
    root,
    CONDITION_FRAGMENT_SERVICE,
    violations,
  );
  const conditionFragmentImpactResponse = readRequired(
    root,
    CONDITION_FRAGMENT_IMPACT_RESPONSE,
    violations,
  );
  const conditionFragmentController = readRequired(
    root,
    CONDITION_FRAGMENT_CONTROLLER,
    violations,
  );
  const conditionFragmentServiceTest = readRequired(
    root,
    CONDITION_FRAGMENT_SERVICE_TEST,
    violations,
  );
  const conditionFragmentControllerTest = readRequired(
    root,
    CONDITION_FRAGMENT_CONTROLLER_TEST,
    violations,
  );
  const authoringBatchJobRepository = readRequired(
    root,
    AUTHORING_BATCH_JOB_REPOSITORY,
    violations,
  );
  const authoringBatchJobService = readRequired(
    root,
    AUTHORING_BATCH_JOB_SERVICE,
    violations,
  );
  const authoringBatchJobController = readRequired(
    root,
    AUTHORING_BATCH_JOB_CONTROLLER,
    violations,
  );
  const authoringBatchJobServiceTest = readRequired(
    root,
    AUTHORING_BATCH_JOB_SERVICE_TEST,
    violations,
  );
  const authoringBatchJobControllerTest = readRequired(
    root,
    AUTHORING_BATCH_JOB_CONTROLLER_TEST,
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
  const releaseGovernance = readRequired(root, RELEASE_GOVERNANCE, violations);
  const releaseGovernanceTest = readRequired(
    root,
    RELEASE_GOVERNANCE_TEST,
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
  const integrationOnboardingRepository = readRequired(
    root,
    INTEGRATION_ONBOARDING_REPOSITORY,
    violations,
  );
  const integrationWebhookConfigRepository = readRequired(
    root,
    INTEGRATION_WEBHOOK_CONFIG_REPOSITORY,
    violations,
  );
  const regionalSourceRepository = readRequired(
    root,
    REGIONAL_SOURCE_REPOSITORY,
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
  const integrationControllerSecurityTest = readRequired(
    root,
    INTEGRATION_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const overrideTemplateRepository = readRequired(
    root,
    OVERRIDE_TEMPLATE_REPOSITORY,
    violations,
  );
  const overrideTemplateService = readRequired(
    root,
    OVERRIDE_TEMPLATE_SERVICE,
    violations,
  );
  const releaseGovernanceController = readRequired(
    root,
    RELEASE_GOVERNANCE_CONTROLLER,
    violations,
  );
  const overrideTemplateServiceTest = readRequired(
    root,
    OVERRIDE_TEMPLATE_SERVICE_TEST,
    violations,
  );
  const releaseGovernanceControllerTest = readRequired(
    root,
    RELEASE_GOVERNANCE_CONTROLLER_TEST,
    violations,
  );
  const packageEngineService = readRequired(
    root,
    PACKAGE_ENGINE_SERVICE,
    violations,
  );
  const packageEngineController = readRequired(
    root,
    PACKAGE_ENGINE_CONTROLLER,
    violations,
  );
  const syncLogRepository = readRequired(root, SYNC_LOG_REPOSITORY, violations);
  const packageEngineServiceTest = readRequired(
    root,
    PACKAGE_ENGINE_SERVICE_TEST,
    violations,
  );
  const packageEngineControllerSecurityTest = readRequired(
    root,
    PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST,
    violations,
  );
  const thirdPartyPackageReconciliationResponse = readRequired(
    root,
    THIRD_PARTY_PACKAGE_RECONCILIATION_RESPONSE,
    violations,
  );
  const thirdPartyKnowledgeRuntimeService = readRequired(
    root,
    THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE,
    violations,
  );
  const thirdPartyKnowledgeRuntimeController = readRequired(
    root,
    THIRD_PARTY_KNOWLEDGE_RUNTIME_CONTROLLER,
    violations,
  );
  const thirdPartyKnowledgeRuntimeServiceTest = readRequired(
    root,
    THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE_TEST,
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
  const knowledgeVersionControllerArrayPattern =
    /ApiResult<\s*(?:java\.util\.)?List<KnowledgeAssetVersion>>/;
  const knowledgeVersionServiceListPattern =
    /public\s+List<KnowledgeAssetVersion>\s+listByIdentity\s*\([^)]*\)\s*\{[\s\S]*?findByTenantIdAndIdentityIdOrderByCreatedAtDesc/;
  if (
    knowledgeVersionControllerArrayPattern.test(knowledgeVersionController) ||
    knowledgeVersionServiceListPattern.test(knowledgeVersionService)
  ) {
    violations.push({
      file: KNOWLEDGE_VERSION_CONTROLLER,
      line: lineOf(knowledgeVersionController, knowledgeVersionControllerArrayPattern),
      ruleId: "b0.knowledge-version-history.backend-array-forbidden",
      message:
        "知识版本历史对外接口不得返回数组或全量版本快照，必须返回 PageResponse 并使用服务端分页。",
    });
  }

  if (
    apiHooks.includes("data: KnowledgeAssetVersion[]") ||
    apiHooks.includes("function useKnowledgeVersions(identityId?: number)") ||
    diagnosisPanel.includes("versionsQuery.data ?? []")
  ) {
    violations.push({
      file: DIAGNOSIS_PANEL,
      line: lineOf(diagnosisPanel, "useKnowledgeVersions"),
      ruleId: "b0.knowledge-version-history.frontend-array-forbidden",
      message:
        "诊断知识版本历史前端不得按数组快照消费，必须使用 PageResponse.items 和服务端分页。",
    });
  }

  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "countByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "pageByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "PageResponse<KnowledgeAssetVersion> listByIdentity",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "versionRepository.countByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "versionRepository.pageByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_VERSION_CONTROLLER,
      knowledgeVersionController,
      "ApiResult<PageResponse<KnowledgeAssetVersion>> listByIdentity",
    ],
    [
      KNOWLEDGE_VERSION_CONTROLLER,
      knowledgeVersionController,
      "new PageRequest(page, size, sort)",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE_TEST,
      knowledgeVersionServiceTest,
      "listByIdentityFallsBackToPlatformIdentityWhenCustomerHasNoLocalOverride",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE_TEST,
      knowledgeVersionServiceTest,
      "pageByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_IDENTITY_CONTROLLER_SECURITY_TEST,
      knowledgeIdentityControllerSecurityTest,
      "readRoleListsKnowledgeVersionsAsPagedContract",
    ],
    [
      KNOWLEDGE_IDENTITY_CONTROLLER_SECURITY_TEST,
      knowledgeIdentityControllerSecurityTest,
      "$.data.items[0].id",
    ],
    [API_HOOKS, apiHooks, "export interface KnowledgeVersionsParams"],
    [API_HOOKS, apiHooks, "PageResponse<KnowledgeAssetVersion>"],
    [
      API_HOOKS,
      apiHooks,
      "useKnowledgeVersions(identityId?: number, params: KnowledgeVersionsParams = {})",
    ],
    [API_HOOKS, apiHooks, "{ params }"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "useKnowledgeVersions(42, { page: 2, size: 10 })",
    ],
    [DIAGNOSIS_PANEL, diagnosisPanel, "DIAGNOSIS_VERSION_PAGE_SIZE = 20"],
    [DIAGNOSIS_PANEL, diagnosisPanel, "versionPage"],
    [DIAGNOSIS_PANEL, diagnosisPanel, "useKnowledgeVersions(identityId, {"],
    [DIAGNOSIS_PANEL, diagnosisPanel, "versionsQuery.data?.items ?? []"],
    [DIAGNOSIS_PANEL, diagnosisPanel, "Pagination"],
    [DIAGNOSIS_PANEL, diagnosisPanel, "onChange={setVersionPage}"],
    [
      DIAGNOSIS_PANEL_TEST,
      diagnosisPanelTest,
      "loads diagnosis versions through server pagination",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.knowledge-version-history.required-snippet-missing",
        message: `知识版本历史分页缺少仓储、服务、控制器、前端或测试片段：${snippet}`,
      });
    }
  }
  if (
    knowledgeVersionService.includes("List<KnowledgeAssetVersion> existingVersions =") ||
    knowledgeVersionService.includes(
      "versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId)",
    )
  ) {
    violations.push({
      file: KNOWLEDGE_VERSION_SERVICE,
      line: lineOf(knowledgeVersionService, "classifyCandidate"),
      ruleId: "b0.knowledge-candidate-classification.identity-version-snapshot-forbidden",
      message:
        "知识候选分类不得拉取身份全量版本后内存判断版本号、content_hash 或 ACTIVE，必须用仓储点查询。",
    });
  }
  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "countByTenantIdAndIdentityIdAndVersionNoIgnoreCase",
    ],
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase",
    ],
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "findByTenantIdAndIdentityIdAndContentHash",
    ],
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "versionRepository.existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "versionRepository.findByTenantIdAndIdentityIdAndContentHash",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "versionRepository.findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE_TEST,
      knowledgeVersionServiceTest,
      "classifyCandidateUsesPointLookupsInsteadOfLoadingAllIdentityVersions",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.knowledge-candidate-classification.required-snippet-missing",
        message: `知识候选分类点查询缺少仓储、服务或测试片段：${snippet}`,
      });
    }
  }
  if (
    /record\s+KnowledgeCandidateResponse\s*\([^)]*List<KnowledgeAssetVersion>\s+candidates/s.test(
      knowledgeCandidateResponse,
    ) ||
    knowledgeVersionService.includes(
      "candidateClassificationRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(tenantId, identityId)",
    ) ||
    knowledgeVersionService.includes(
      ".filter(version -> version.status() == KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW)",
    )
  ) {
    violations.push({
      file: KNOWLEDGE_VERSION_SERVICE,
      line: lineOf(knowledgeVersionService, "listCandidates"),
      ruleId: "b0.knowledge-candidate-review.backend-array-forbidden",
      message:
        "知识审核候选队列不得按身份全量拉取版本/分类后内存过滤，必须返回 PageResponse 并只补当前页候选分类。",
    });
  }

  if (
    apiHooks.includes("candidates: KnowledgeAssetVersion[]") ||
    apiHooks.includes("function useKnowledgeCandidates(identityId?: number)") ||
    governance.includes("candidateResponse?.candidates ?? []") ||
    governance.includes("dataSource={candidates}\n        pagination={false}")
  ) {
    violations.push({
      file: KNOWLEDGE_GOVERNANCE,
      line: lineOf(governance, "useKnowledgeCandidates"),
      ruleId: "b0.knowledge-candidate-review.frontend-array-forbidden",
      message:
        "知识审核台不得消费全量候选数组或关闭候选表分页，必须用 PageResponse.items 和服务端分页。",
    });
  }

  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_CANDIDATE_RESPONSE,
      knowledgeCandidateResponse,
      "PageResponse<KnowledgeAssetVersion> candidates",
    ],
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "countPendingReplacementCandidatesByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_ASSET_VERSION_REPOSITORY,
      knowledgeAssetVersionRepository,
      "pagePendingReplacementCandidatesByTenantIdAndIdentityId",
    ],
    [
      CANDIDATE_CLASSIFICATION_REPOSITORY,
      candidateClassificationRepository,
      "findByTenantIdAndCandidateVersionIdIn",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "listCandidates(Long identityId, PageRequest request)",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE,
      knowledgeVersionService,
      "PageResponse.of(candidates, safeRequest, total)",
    ],
    [
      KNOWLEDGE_VERSION_CONTROLLER,
      knowledgeVersionController,
      "versionService.listCandidates(identityId, new PageRequest(page, size, sort))",
    ],
    [API_HOOKS, apiHooks, "export interface KnowledgeCandidatesParams"],
    [
      API_HOOKS,
      apiHooks,
      "useKnowledgeCandidates(identityId?: number, params: KnowledgeCandidatesParams = {})",
    ],
    [API_HOOKS, apiHooks, "{ params: requestParams }"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "useKnowledgeCandidates(42, { page: 2, size: 10 })",
    ],
    [KNOWLEDGE_GOVERNANCE, governance, "KNOWLEDGE_CANDIDATE_PAGE_SIZE = 20"],
    [KNOWLEDGE_GOVERNANCE, governance, "candidatePage"],
    [KNOWLEDGE_GOVERNANCE, governance, "candidatePageData?.items ?? []"],
    [KNOWLEDGE_GOVERNANCE, governance, "useKnowledgeCandidates(selectedIdentityId, {"],
    [KNOWLEDGE_GOVERNANCE, governance, "onChange: setCandidatePage"],
    [
      KNOWLEDGE_GOVERNANCE_TEST,
      governanceTest,
      "expect(mockUseKnowledgeCandidates).toHaveBeenLastCalledWith(42, { page: 1, size: 20 })",
    ],
    [
      KNOWLEDGE_VERSION_SERVICE_TEST,
      knowledgeVersionServiceTest,
      "listCandidatesPagesPendingCandidatesAndLoadsOnlyCurrentPageClassifications",
    ],
    [
      KNOWLEDGE_ASSET_API_CONTRACT_TEST,
      knowledgeAssetApiContractTest,
      "$.data.candidates.items[0].id",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.knowledge-candidate-review.required-snippet-missing",
        message: `知识候选审核分页缺少仓储、服务、控制器、前端或测试片段：${snippet}`,
      });
    }
  }
  if (
    knowledgeProvenanceResponse.includes("List<KnowledgeAssetVersion> versions") ||
    knowledgeProvenanceResponse.includes("List<KnowledgeSupersession> supersessions") ||
    knowledgeLineage.includes("List<KnowledgeAssetVersion> versions") ||
    knowledgeLineage.includes("List<KnowledgeSupersession> supersessions") ||
    knowledgeIdentityService.includes(
      "versionRepository.listByIdentity(effective.sourceTenantId(), identity.id())",
    ) ||
    knowledgeIdentityService.includes(
      "supersessionRepository.findByTenantIdAndIdentityIdOrderByTransitionedAtAsc",
    )
  ) {
    violations.push({
      file: KNOWLEDGE_PROVENANCE_RESPONSE,
      line: lineOf(knowledgeProvenanceResponse, "List<KnowledgeAssetVersion> versions"),
      ruleId: "b0.knowledge-provenance-history.backend-array-forbidden",
      message:
        "知识来源追溯和 lineage 不得返回全量版本/替代历史数组，必须使用 PageResponse 分页沿革。",
    });
  }

  if (
    apiHooks.includes("versions: KnowledgeAssetVersion[]") ||
    apiHooks.includes("supersessions: KnowledgeSupersession[]") ||
    apiHooks.includes("function useKnowledgeProvenance(identityId?: number)") ||
    advancedProvenance.includes("provenance.versions.find(") ||
    advancedProvenance.includes("dataSource={provenance.versions}") ||
    advancedProvenance.includes("pagination={false}")
  ) {
    violations.push({
      file: ADVANCED_PROVENANCE,
      line: lineOf(advancedProvenance, "useKnowledgeProvenance"),
      ruleId: "b0.knowledge-provenance-history.frontend-array-forbidden",
      message:
        "知识来源追溯页不得按数组快照消费版本沿革，必须使用 PageResponse.items 和服务端分页。",
    });
  }

  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_PROVENANCE_RESPONSE,
      knowledgeProvenanceResponse,
      "PageResponse<KnowledgeAssetVersion> versions",
    ],
    [
      KNOWLEDGE_PROVENANCE_RESPONSE,
      knowledgeProvenanceResponse,
      "PageResponse<KnowledgeSupersession> supersessions",
    ],
    [
      KNOWLEDGE_LINEAGE,
      knowledgeLineage,
      "PageResponse<KnowledgeAssetVersion> versions",
    ],
    [
      KNOWLEDGE_LINEAGE,
      knowledgeLineage,
      "PageResponse<KnowledgeSupersession> supersessions",
    ],
    [
      KNOWLEDGE_SUPERSESSION_REPOSITORY,
      knowledgeSupersessionRepository,
      "countByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_SUPERSESSION_REPOSITORY,
      knowledgeSupersessionRepository,
      "pageByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_SUPERSESSION_REPOSITORY,
      knowledgeSupersessionRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE,
      knowledgeIdentityService,
      "getProvenance(Long identityId, PageRequest request)",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE,
      knowledgeIdentityService,
      "versionHistoryPage(effective, safeRequest)",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE,
      knowledgeIdentityService,
      "supersessionPage(effective, safeRequest)",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE,
      knowledgeIdentityService,
      "versionRepository.pageByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE,
      knowledgeIdentityService,
      "supersessionRepository.pageByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE,
      knowledgeIdentityService,
      "getLineage(Long identityId, PageRequest request)",
    ],
    [
      KNOWLEDGE_IDENTITY_CONTROLLER,
      knowledgeIdentityController,
      "service.getProvenance(id, new PageRequest(page, size, sort))",
    ],
    [
      KNOWLEDGE_IDENTITY_CONTROLLER,
      knowledgeIdentityController,
      "service.getLineage(id, new PageRequest(page, size, sort))",
    ],
    [
      KNOWLEDGE_IDENTITY_CONTROLLER_SECURITY_TEST,
      knowledgeIdentityControllerSecurityTest,
      "doctorCanReachProvenanceButDataScopeRejectsMissingTenant",
    ],
    [
      KNOWLEDGE_ASSET_API_CONTRACT_TEST,
      knowledgeAssetApiContractTest,
      "getProvenance(eq(1L), any())",
    ],
    [
      KNOWLEDGE_ASSET_API_CONTRACT_TEST,
      knowledgeAssetApiContractTest,
      "$.data.versions.items[0].id",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE_TEST,
      knowledgeIdentityServiceTest,
      "lineageBundlesIdentityVersionsAndSupersessions",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE_TEST,
      knowledgeIdentityServiceTest,
      "versionRepo.countByTenantIdAndIdentityId",
    ],
    [
      KNOWLEDGE_IDENTITY_SERVICE_TEST,
      knowledgeIdentityServiceTest,
      "supersessionRepo.countByTenantIdAndIdentityId",
    ],
    [API_HOOKS, apiHooks, "export interface KnowledgeProvenanceParams"],
    [
      API_HOOKS,
      apiHooks,
      "versions: PageResponse<KnowledgeAssetVersion>",
    ],
    [
      API_HOOKS,
      apiHooks,
      "supersessions: PageResponse<KnowledgeSupersession>",
    ],
    [API_HOOKS, apiHooks, "params: KnowledgeProvenanceParams = {}"],
    [API_HOOKS, apiHooks, "{ params }"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "useKnowledgeProvenance(42, { page: 2, size: 10 })",
    ],
    [
      ADVANCED_PROVENANCE,
      advancedProvenance,
      "PROVENANCE_HISTORY_PAGE_SIZE = 20",
    ],
    [
      ADVANCED_PROVENANCE,
      advancedProvenance,
      "const [historyPage, setHistoryPage]",
    ],
    [
      ADVANCED_PROVENANCE,
      advancedProvenance,
      "useKnowledgeProvenance(selectedIdentityId, {",
    ],
    [ADVANCED_PROVENANCE, advancedProvenance, "provenance.versions.items"],
    [ADVANCED_PROVENANCE, advancedProvenance, "onChange: setHistoryPage"],
    [
      ADVANCED_PROVENANCE_TEST,
      advancedProvenanceTest,
      "mockUseKnowledgeProvenance).toHaveBeenCalledWith(1, { page: 1, size: 20 })",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.knowledge-provenance-history.required-snippet-missing",
        message: `知识来源追溯版本沿革分页缺少后端、前端或测试片段：${snippet}`,
      });
    }
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
  if (
    knowledgeCustomizationService.includes(
      "versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId)",
    )
  ) {
    violations.push({
      file: KNOWLEDGE_CUSTOMIZATION_SERVICE,
      line: lineOf(knowledgeCustomizationService, "nextLocalVersionNo"),
      ruleId: "b0.knowledge-customization-local-version.identity-version-snapshot-forbidden",
      message:
        "机构知识定制生成本地版本号不得拉取身份全量版本后 size，必须使用 count 查询。",
    });
  }
  if (
    !knowledgeCustomizationService.includes(
      "versions.countByTenantIdAndIdentityId(tenantId, identityId)",
    )
  ) {
    violations.push({
      file: KNOWLEDGE_CUSTOMIZATION_SERVICE,
      line: lineOf(knowledgeCustomizationService, "nextLocalVersionNo"),
      ruleId: "b0.knowledge-customization-local-version.required-snippet-missing",
      message: "机构知识定制本地版本号缺少 countByTenantIdAndIdentityId 点查询。",
    });
  }
  if (
    knowledgeProductionService.includes(
      "candidateRepository.findByTenantIdAndJobCode(tenantId, jobCode).stream()",
    )
  ) {
    violations.push({
      file: KNOWLEDGE_PRODUCTION_SERVICE,
      line: lineOf(
        knowledgeProductionService,
        "candidateRepository.findByTenantIdAndJobCode(tenantId, jobCode).stream()",
      ),
      ruleId:
        "b0.knowledge-production-candidates-list.tenant-job-snapshot-forbidden",
      message:
        "知识生产候选血缘列表不得按 job 全量读取后返回数组，必须使用后端 PageResponse 与仓储 count/page。",
    });
  }
  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY,
      knowledgeProductionCandidateRepository,
      "countByTenantIdAndJobCode",
    ],
    [
      KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY,
      knowledgeProductionCandidateRepository,
      "pageByTenantIdAndJobCode",
    ],
    [
      KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY,
      knowledgeProductionCandidateRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      KNOWLEDGE_PRODUCTION_SERVICE,
      knowledgeProductionService,
      "PageResponse<ProductionCandidateView> listCandidates(String jobCode, int page, int size)",
    ],
    [
      KNOWLEDGE_PRODUCTION_SERVICE,
      knowledgeProductionService,
      "candidateRepository.countByTenantIdAndJobCode",
    ],
    [
      KNOWLEDGE_PRODUCTION_SERVICE,
      knowledgeProductionService,
      ".pageByTenantIdAndJobCode",
    ],
    [
      KNOWLEDGE_PRODUCTION_CONTROLLER,
      knowledgeProductionController,
      "ApiResult<PageResponse<ProductionCandidateView>> listCandidates(",
    ],
    [
      KNOWLEDGE_PRODUCTION_CONTROLLER,
      knowledgeProductionController,
      "@RequestParam(required = false, defaultValue = \"1\") int page",
    ],
    [
      KNOWLEDGE_PRODUCTION_SERVICE_TEST,
      knowledgeProductionServiceTest,
      "listCandidatesReturnsTenantScopedPageWithRouting",
    ],
    [
      KNOWLEDGE_PRODUCTION_CANDIDATE_REPOSITORY_TEST,
      knowledgeProductionCandidateRepositoryTest,
      "pagesLineageByJobWithoutLoadingAllRows",
    ],
    [
      KNOWLEDGE_PRODUCTION_CONTROLLER_SECURITY_TEST,
      knowledgeProductionControllerSecurityTest,
      "jsonPath(\"$.data.items\").isArray()",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId:
          "b0.knowledge-production-candidates-list.required-snippet-missing",
        message: `知识生产候选血缘列表分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  if (
    /record\s+CandidateProvenanceRequest\s*\([^)]*List<String>\s+candidateRefs/s.test(
      candidateProvenanceRequest,
    ) ||
    candidateProvenanceService.includes(
      "findByTenantIdAndCandidateRefIn(tenantId, candidateRefs)",
    )
  ) {
    violations.push({
      file: CANDIDATE_PROVENANCE_SERVICE,
      line: lineOf(candidateProvenanceService, "findByTenantIdAndCandidateRefIn"),
      ruleId: "b0.candidate-provenance-batch-limit.unbounded-ref-batch-forbidden",
      message:
        "候选来源溯源不得接收或查询无上限 ref 数组，必须限制批量、校验空值并仅用归一化后的候选引用查询。",
    });
  }
  for (const [file, content, snippet] of [
    [
      CANDIDATE_PROVENANCE_REQUEST,
      candidateProvenanceRequest,
      "MAX_CANDIDATE_REFS = 200",
    ],
    [
      CANDIDATE_PROVENANCE_REQUEST,
      candidateProvenanceRequest,
      "@Size(max = CandidateProvenanceRequest.MAX_CANDIDATE_REFS)",
    ],
    [
      CANDIDATE_PROVENANCE_REQUEST,
      candidateProvenanceRequest,
      "List<@NotBlank @Size(max = 128) String> candidateRefs",
    ],
    [
      CANDIDATE_PROVENANCE_SERVICE,
      candidateProvenanceService,
      "candidateRefs.size() > CandidateProvenanceRequest.MAX_CANDIDATE_REFS",
    ],
    [
      CANDIDATE_PROVENANCE_SERVICE,
      candidateProvenanceService,
      "ErrorCode.VALIDATION_FAILED",
    ],
    [CANDIDATE_PROVENANCE_SERVICE, candidateProvenanceService, ".map(String::trim)"],
    [CANDIDATE_PROVENANCE_SERVICE, candidateProvenanceService, ".distinct()"],
    [
      CANDIDATE_PROVENANCE_SERVICE,
      candidateProvenanceService,
      "findByTenantIdAndCandidateRefIn(tenantId, normalizedRefs)",
    ],
    [
      CANDIDATE_PROVENANCE_SERVICE_TEST,
      candidateProvenanceServiceTest,
      "rejectsOversizedProvenanceRefBatchBeforeRepositoryLookup",
    ],
    [
      KNOWLEDGE_PRODUCTION_CONTROLLER_SECURITY_TEST,
      knowledgeProductionControllerSecurityTest,
      "oversizedProvenanceRefsRejectedBeforeService",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.candidate-provenance-batch-limit.required-snippet-missing",
        message: `候选来源溯源批量上限缺少请求、服务或测试片段：${snippet}`,
      });
    }
  }
  if (
    documentParseService.includes("List<DocParseJob> listJobs(") ||
    documentParseController.includes("ApiResult<List<DocParseJob>> listJobs")
  ) {
    const file = documentParseController.includes("ApiResult<List<DocParseJob>> listJobs")
      ? DOCUMENT_PARSE_CONTROLLER
      : DOCUMENT_PARSE_SERVICE;
    const content = file === DOCUMENT_PARSE_CONTROLLER
      ? documentParseController
      : documentParseService;
    const pattern = file === DOCUMENT_PARSE_CONTROLLER
      ? "ApiResult<List<DocParseJob>> listJobs"
      : "List<DocParseJob> listJobs(";
    violations.push({
      file,
      line: lineOf(content, pattern),
      ruleId: "b0.document-parse-job-ledger.array-page-forbidden",
      message:
        "文档解析 job 台账不得返回数组分页，必须返回 PageResponse 并提供 total/hasNext 证据链。",
    });
  }
  for (const [file, content, snippet] of [
    [DOC_PARSE_JOB_REPOSITORY, docParseJobRepository, "long countByTenantId(String tenantId)"],
    [DOC_PARSE_JOB_REPOSITORY, docParseJobRepository, "pageByTenantId"],
    [
      DOC_PARSE_JOB_REPOSITORY,
      docParseJobRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      DOCUMENT_PARSE_SERVICE,
      documentParseService,
      "PageResponse<DocParseJob> listJobs(int page, int size)",
    ],
    [DOCUMENT_PARSE_SERVICE, documentParseService, "new PageRequest(page"],
    [DOCUMENT_PARSE_SERVICE, documentParseService, "jobRepository.countByTenantId"],
    [DOCUMENT_PARSE_SERVICE, documentParseService, "PageResponse.of"],
    [
      DOCUMENT_PARSE_CONTROLLER,
      documentParseController,
      "ApiResult<PageResponse<DocParseJob>> listJobs(",
    ],
    [
      DOCUMENT_PARSE_CONTROLLER,
      documentParseController,
      '@RequestParam(required = false, defaultValue = "1") int page',
    ],
    [
      DOCUMENT_PARSE_SERVICE_TEST,
      documentParseServiceTest,
      "listJobsReturnsTenantScopedPageWithTotal",
    ],
    [
      DOCUMENT_PARSE_CONTROLLER_SECURITY_TEST,
      documentParseControllerSecurityTest,
      "$.data.items[0].jobCode",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.document-parse-job-ledger.required-snippet-missing",
        message: `文档解析 job 台账分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  for (const [file, content, pattern] of [
    [
      KNOWLEDGE_EXPORT_JOB_REPOSITORY,
      knowledgeExportJobRepository,
      "findTop100ByTenantIdOrderByCreatedAtDesc",
    ],
    [
      KNOWLEDGE_EXPORT_SERVICE,
      knowledgeExportService,
      "findTop100ByTenantIdOrderByCreatedAtDesc",
    ],
    [
      KNOWLEDGE_EXPORT_SERVICE,
      knowledgeExportService,
      "List<KnowledgeExportJob> listRecent()",
    ],
    [
      KNOWLEDGE_EXPORT_CONTROLLER,
      knowledgeExportController,
      "ApiResult<List<KnowledgeExportJob>> listRecent",
    ],
  ]) {
    if (content.includes(pattern)) {
      violations.push({
        file,
        line: lineOf(content, pattern),
        ruleId: "b0.knowledge-export-job-ledger.top100-snapshot-forbidden",
        message:
          "知识异步导出作业台账不得只返回最近 100 条快照，必须使用后端 PageResponse 与仓储 count/page。",
      });
    }
  }
  for (const [file, content, snippet] of [
    [
      KNOWLEDGE_EXPORT_JOB_REPOSITORY,
      knowledgeExportJobRepository,
      "long countByTenantId(String tenantId)",
    ],
    [
      KNOWLEDGE_EXPORT_JOB_REPOSITORY,
      knowledgeExportJobRepository,
      "List<KnowledgeExportJob> pageByTenantId(String tenantId, int offset, int limit)",
    ],
    [
      KNOWLEDGE_EXPORT_JOB_REPOSITORY,
      knowledgeExportJobRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      KNOWLEDGE_EXPORT_SERVICE,
      knowledgeExportService,
      "PageResponse<KnowledgeExportJob> listRecent(PageRequest request)",
    ],
    [KNOWLEDGE_EXPORT_SERVICE, knowledgeExportService, "jobRepository.countByTenantId"],
    [KNOWLEDGE_EXPORT_SERVICE, knowledgeExportService, "jobRepository.pageByTenantId"],
    [
      KNOWLEDGE_EXPORT_CONTROLLER,
      knowledgeExportController,
      "ApiResult<PageResponse<KnowledgeExportJob>> listRecent(",
    ],
    [
      KNOWLEDGE_EXPORT_CONTROLLER,
      knowledgeExportController,
      "new PageRequest(page, size, null)",
    ],
    [
      KNOWLEDGE_EXPORT_SERVICE_TEST,
      knowledgeExportServiceTest,
      "listRecentReturnsTenantScopedPageInsteadOfTop100Snapshot",
    ],
    [
      KNOWLEDGE_EXPORT_SERVICE_TEST,
      knowledgeExportServiceTest,
      "Mockito.verify(jobRepo).pageByTenantId",
    ],
    [
      KNOWLEDGE_IDENTITY_CONTROLLER_SECURITY_TEST,
      knowledgeIdentityControllerSecurityTest,
      "auditComplianceCanListExportsAsPage",
    ],
    [
      KNOWLEDGE_IDENTITY_CONTROLLER_SECURITY_TEST,
      knowledgeIdentityControllerSecurityTest,
      "$.data.items[0].jobCode",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.knowledge-export-job-ledger.required-snippet-missing",
        message: `知识异步导出作业台账分页缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  for (const [file, content, pattern] of [
    [
      ENGINE_DATA_EXPORT_JOB_REPOSITORY,
      engineDataExportJobRepository,
      "findTop100ByTenantIdOrderByCreatedAtDesc",
    ],
    [
      ENGINE_DATA_EXPORT_SERVICE,
      engineDataExportService,
      "findTop100ByTenantIdOrderByCreatedAtDesc",
    ],
    [
      ENGINE_DATA_EXPORT_SERVICE,
      engineDataExportService,
      "List<EngineDataExportJob> listRecent()",
    ],
    [
      ENGINE_DATA_CONTROLLER,
      engineDataController,
      "ApiResult<List<EngineDataExportJob>> listExports",
    ],
  ]) {
    if (content.includes(pattern)) {
      violations.push({
        file,
        line: lineOf(content, pattern),
        ruleId: "b0.engine-data-export-job-ledger.top100-snapshot-forbidden",
        message:
          "引擎数据导出作业台账不得只返回最近 100 条快照，必须使用后端 PageResponse 与仓储 count/page。",
      });
    }
  }
  for (const [file, content, snippet] of [
    [
      ENGINE_DATA_EXPORT_JOB_REPOSITORY,
      engineDataExportJobRepository,
      "long countByTenantId(String tenantId)",
    ],
    [
      ENGINE_DATA_EXPORT_JOB_REPOSITORY,
      engineDataExportJobRepository,
      "List<EngineDataExportJob> pageByTenantId(String tenantId, int offset, int limit)",
    ],
    [
      ENGINE_DATA_EXPORT_JOB_REPOSITORY,
      engineDataExportJobRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      ENGINE_DATA_EXPORT_SERVICE,
      engineDataExportService,
      "PageResponse<EngineDataExportJob> listRecent(PageRequest request)",
    ],
    [ENGINE_DATA_EXPORT_SERVICE, engineDataExportService, "jobRepository.countByTenantId"],
    [ENGINE_DATA_EXPORT_SERVICE, engineDataExportService, "jobRepository.pageByTenantId"],
    [
      ENGINE_DATA_CONTROLLER,
      engineDataController,
      "ApiResult<PageResponse<EngineDataExportJob>> listExports(",
    ],
    [ENGINE_DATA_CONTROLLER, engineDataController, "new PageRequest(page, size, null)"],
    [
      ENGINE_DATA_EXPORT_SERVICE_TEST,
      engineDataExportServiceTest,
      "listRecentReturnsTenantScopedPageInsteadOfTop100Snapshot",
    ],
    [
      ENGINE_DATA_EXPORT_SERVICE_TEST,
      engineDataExportServiceTest,
      "Mockito.verify(jobRepo).pageByTenantId",
    ],
    [
      ENGINE_DATA_EXPORT_JOB_REPOSITORY_TEST,
      engineDataExportJobRepositoryTest,
      "pagesRecentScopedToTenant",
    ],
    [
      ENGINE_DATA_EXPORT_JOB_REPOSITORY_TEST,
      engineDataExportJobRepositoryTest,
      "repo.pageByTenantId(TENANT, 0, 2)",
    ],
    [
      ENGINE_DATA_CONTROLLER_SECURITY_TEST,
      engineDataControllerSecurityTest,
      "qualityGovernorCanListExportsAsPage",
    ],
    [
      ENGINE_DATA_CONTROLLER_SECURITY_TEST,
      engineDataControllerSecurityTest,
      "$.data.items[0].jobCode",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.engine-data-export-job-ledger.required-snippet-missing",
        message: `引擎数据导出作业台账分页缺少必要实现或测试片段：${snippet}`,
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

  for (const [file, content, pattern, message] of [
    [
      DATA_PERMISSION_POLICY_REPOSITORY,
      dataPermissionPolicyRepository,
      "findPolicies(",
      "数据权限策略仓储不得保留无界 findPolicies 快照查询，必须使用 count/page。",
    ],
    [
      DATA_PERMISSION_SERVICE,
      dataPermissionService,
      "List<DataPermissionPolicyResponse> listPolicies(",
      "数据权限策略服务不得返回数组快照，必须返回 PageResponse。",
    ],
    [
      DATA_PERMISSION_CONTROLLER,
      dataPermissionController,
      "ApiResult<List<DataPermissionPolicyResponse>> listPolicies",
      "数据权限策略控制器不得返回数组响应，必须返回 PageResponse。",
    ],
    [
      MASKING_RULE_REPOSITORY,
      maskingRuleRepository,
      "findRules(",
      "脱敏规则仓储不得保留无界 findRules 快照查询，必须使用 count/page。",
    ],
    [
      MASKING_SERVICE,
      maskingService,
      "List<MaskingRuleResponse> listRules(",
      "脱敏规则服务不得返回数组快照，必须返回 PageResponse。",
    ],
    [
      MASKING_RULE_CONTROLLER,
      maskingRuleController,
      "ApiResult<List<MaskingRuleResponse>> listRules",
      "脱敏规则控制器不得返回数组响应，必须返回 PageResponse。",
    ],
  ]) {
    if (content.includes(pattern)) {
      violations.push({
        file,
        line: lineOf(content, pattern),
        ruleId: "b0.security-baseline-policy-ledger.array-snapshot-forbidden",
        message,
      });
    }
  }
  const securityBaselineFrontendSnapshotPattern =
    /Promise<DataPermissionPolicy\[\]>|Promise<MaskingRule\[\]>|dataSource=\{policies\.data\s*\?\?\s*\[\]\}|dataSource=\{rules\.data\s*\?\?\s*\[\]\}|defaultPolicy\s*=\s*policies\.data\?\.\[0\]|defaultRule\s*=\s*rules\.data\?\.\[0\]|useDataPermissionPolicies\(\s*\)|useMaskingRules\(\s*\)/s;
  if (
    securityBaselineFrontendSnapshotPattern.test(apiHooks) ||
    securityBaselineFrontendSnapshotPattern.test(securityBaselinePanels)
  ) {
    const file = securityBaselineFrontendSnapshotPattern.test(securityBaselinePanels)
      ? SECURITY_BASELINE_PANELS
      : API_HOOKS;
    const content = file === SECURITY_BASELINE_PANELS ? securityBaselinePanels : apiHooks;
    violations.push({
      file,
      line: lineOf(content, securityBaselineFrontendSnapshotPattern),
      ruleId: "b0.security-baseline-policy-ledger.frontend-array-snapshot-forbidden",
      message:
        "数据权限/脱敏规则前端不得按数组快照消费，必须传 page/size 并读取 PageResponse.items。",
    });
  }
  for (const [file, content, snippet] of [
    [DATA_PERMISSION_POLICY_REPOSITORY, dataPermissionPolicyRepository, "long countPolicies("],
    [DATA_PERMISSION_POLICY_REPOSITORY, dataPermissionPolicyRepository, "List<DataPermissionPolicy> pagePolicies("],
    [
      DATA_PERMISSION_POLICY_REPOSITORY,
      dataPermissionPolicyRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      DATA_PERMISSION_SERVICE,
      dataPermissionService,
      "PageResponse<DataPermissionPolicyResponse> listPolicies(",
    ],
    [DATA_PERMISSION_SERVICE, dataPermissionService, "repository.countPolicies"],
    [DATA_PERMISSION_SERVICE, dataPermissionService, "repository.pagePolicies"],
    [
      DATA_PERMISSION_CONTROLLER,
      dataPermissionController,
      "ApiResult<PageResponse<DataPermissionPolicyResponse>> listPolicies(",
    ],
    [DATA_PERMISSION_CONTROLLER, dataPermissionController, "new PageRequest(page, size, null)"],
    [
      DATA_PERMISSION_SERVICE_TEST,
      dataPermissionServiceTest,
      "listPoliciesReturnsTenantScopedPageInsteadOfUnboundedList",
    ],
    [DATA_PERMISSION_SERVICE_TEST, dataPermissionServiceTest, "verify(repository).pagePolicies"],
    [
      DATA_PERMISSION_CONTROLLER_SECURITY_TEST,
      dataPermissionControllerSecurityTest,
      'jsonPath("$.data.items").isArray()',
    ],
    [MASKING_RULE_REPOSITORY, maskingRuleRepository, "long countRules("],
    [MASKING_RULE_REPOSITORY, maskingRuleRepository, "List<MaskingRule> pageRules("],
    [
      MASKING_RULE_REPOSITORY,
      maskingRuleRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [MASKING_SERVICE, maskingService, "PageResponse<MaskingRuleResponse> listRules("],
    [MASKING_SERVICE, maskingService, "repository.countRules"],
    [MASKING_SERVICE, maskingService, "repository.pageRules"],
    [
      MASKING_RULE_CONTROLLER,
      maskingRuleController,
      "ApiResult<PageResponse<MaskingRuleResponse>> listRules(",
    ],
    [MASKING_RULE_CONTROLLER, maskingRuleController, "new PageRequest(page, size, null)"],
    [
      MASKING_SERVICE_TEST,
      maskingServiceTest,
      "listRulesReturnsTenantScopedPageInsteadOfUnboundedList",
    ],
    [MASKING_SERVICE_TEST, maskingServiceTest, "verify(repository).pageRules"],
    [
      MASKING_RULE_CONTROLLER_SECURITY_TEST,
      maskingRuleControllerSecurityTest,
      'jsonPath("$.data.items").isArray()',
    ],
    [API_HOOKS, apiHooks, "interface DataPermissionPoliciesParams"],
    [API_HOOKS, apiHooks, "interface MaskingRulesParams"],
    [API_HOOKS, apiHooks, "PageResponse<DataPermissionPolicy>"],
    [API_HOOKS, apiHooks, "PageResponse<MaskingRule>"],
    [API_HOOKS_TEST, apiHooksTest, "fetchDataPermissionPolicies({"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      'fetchMaskingRules({ resourceType: "clinical_case", page: 3, size: 10 })',
    ],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "SECURITY_RULE_PAGE_SIZE = 20"],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "policyPage"],
    [
      SECURITY_BASELINE_PANELS,
      securityBaselinePanels,
      "useDataPermissionPolicies({ page: policyPage, size: SECURITY_RULE_PAGE_SIZE })",
    ],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "policyItems = policies.data?.items ?? []"],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "dataSource={policyItems}"],
    [
      SECURITY_BASELINE_PANELS,
      securityBaselinePanels,
      "current: policies.data?.page ?? policyPage",
    ],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "onChange: setPolicyPage"],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "rulePage"],
    [
      SECURITY_BASELINE_PANELS,
      securityBaselinePanels,
      "useMaskingRules({ page: rulePage, size: SECURITY_RULE_PAGE_SIZE })",
    ],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "ruleItems = rules.data?.items ?? []"],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "dataSource={ruleItems}"],
    [
      SECURITY_BASELINE_PANELS,
      securityBaselinePanels,
      "current: rules.data?.page ?? rulePage",
    ],
    [SECURITY_BASELINE_PANELS, securityBaselinePanels, "onChange: setRulePage"],
    [
      SECURITY_BASELINE_TEST,
      securityBaselineTest,
      "expect(useDataPermissionPolicies).toHaveBeenCalledWith({ page: 1, size: 20 })",
    ],
    [
      SECURITY_BASELINE_TEST,
      securityBaselineTest,
      "expect(useMaskingRules).toHaveBeenCalledWith({ page: 1, size: 20 })",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.security-baseline-policy-ledger.required-snippet-missing",
        message: `数据权限/脱敏规则维护分页缺少必要实现或测试片段：${snippet}`,
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
    /findByTenantIdOrderByUpdatedAtDesc\(|\.subList\(|SOURCE_SCAN_LIMIT|rules\.listByFilter\(tenantId,\s*null,\s*null,\s*null,\s*null\)|pathways\.listByFilter\(tenantId,\s*null,\s*null,\s*null,\s*null,\s*null\)/;
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
    "listWithProfileFilters",
    "loadProfileFilteredRepositoryPage",
    "RepositoryAssetPage",
    "rules.countByFilter",
    "rules.pageByFilter",
    "rules.countForAuthoringLibrary",
    "rules.pageForAuthoringLibrary",
    "pathways.countByFilter",
    "pathways.pageByFilter",
    "pathways.countForAuthoringLibrary",
    "pathways.pageForAuthoringLibrary",
    "fragments.countByFilter",
    "fragments.pageByFilter",
    "fragments.countForAuthoringLibrary",
    "fragments.pageForAuthoringLibrary",
    "followupTemplates.countByFilter",
    "followupTemplates.pageByFilter",
    "followupTemplates.countForAuthoringLibrary",
    "followupTemplates.pageForAuthoringLibrary",
    "tagPattern(tag)",
    "favoriteUserId",
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
    || !authoringAssetLibraryServiceTest.includes(
      "listsRulesPathwaysAndFragmentsWithTagsAndFavorites",
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
  for (const [file, content, pattern] of [
    [
      AUTHORING_BATCH_JOB_REPOSITORY,
      authoringBatchJobRepository,
      "findTop50ByTenantIdOrderByCreatedAtDesc",
    ],
    [
      AUTHORING_BATCH_JOB_SERVICE,
      authoringBatchJobService,
      "findTop50ByTenantIdOrderByCreatedAtDesc",
    ],
    [
      AUTHORING_BATCH_JOB_SERVICE,
      authoringBatchJobService,
      "List<AuthoringBatchJobResponse> listRecent()",
    ],
    [
      AUTHORING_BATCH_JOB_CONTROLLER,
      authoringBatchJobController,
      "ApiResult<List<AuthoringBatchJobResponse>> listRecent",
    ],
  ]) {
    if (content.includes(pattern)) {
      violations.push({
        file,
        line: lineOf(content, pattern),
        ruleId: "b0.authoring-batch-job-ledger.top50-snapshot-forbidden",
        message:
          "批量维护任务记录不得只返回最近 50 条快照，必须使用后端 PageResponse 与仓储 count/page。",
      });
    }
  }
  const authoringBatchJobFrontendSnapshotPattern =
    /data:\s*AuthoringBatchJobResponse\[\]|dataSource=\{jobsQuery\.data\s*\?\?\s*\[\]\}|useAuthoringBatchJobs\(\s*\{\s*enabled:\s*open\s*\}\s*\)/s;
  if (
    authoringBatchJobFrontendSnapshotPattern.test(apiHooks) ||
    authoringBatchJobFrontendSnapshotPattern.test(authoringBatchDrawer)
  ) {
    const file = authoringBatchJobFrontendSnapshotPattern.test(authoringBatchDrawer)
      ? AUTHORING_BATCH_DRAWER
      : API_HOOKS;
    const content = file === AUTHORING_BATCH_DRAWER ? authoringBatchDrawer : apiHooks;
    violations.push({
      file,
      line: lineOf(content, authoringBatchJobFrontendSnapshotPattern),
      ruleId: "b0.authoring-batch-job-ledger.frontend-array-snapshot-forbidden",
      message:
        "批量维护任务记录前端不得按数组快照消费，必须传 page/size 并读取 PageResponse.items。",
    });
  }
  for (const [file, content, snippet] of [
    [
      AUTHORING_BATCH_JOB_REPOSITORY,
      authoringBatchJobRepository,
      "long countByTenantId(String tenantId)",
    ],
    [
      AUTHORING_BATCH_JOB_REPOSITORY,
      authoringBatchJobRepository,
      "List<AuthoringBatchJob> pageByTenantId(String tenantId, int offset, int limit)",
    ],
    [
      AUTHORING_BATCH_JOB_REPOSITORY,
      authoringBatchJobRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      AUTHORING_BATCH_JOB_SERVICE,
      authoringBatchJobService,
      "PageResponse<AuthoringBatchJobResponse> listRecent(PageRequest request)",
    ],
    [AUTHORING_BATCH_JOB_SERVICE, authoringBatchJobService, "jobs.countByTenantId"],
    [AUTHORING_BATCH_JOB_SERVICE, authoringBatchJobService, "jobs.pageByTenantId"],
    [
      AUTHORING_BATCH_JOB_CONTROLLER,
      authoringBatchJobController,
      "ApiResult<PageResponse<AuthoringBatchJobResponse>> listRecent(",
    ],
    [
      AUTHORING_BATCH_JOB_CONTROLLER,
      authoringBatchJobController,
      "new PageRequest(page, size, null)",
    ],
    [
      AUTHORING_BATCH_JOB_SERVICE_TEST,
      authoringBatchJobServiceTest,
      "listRecentReturnsTenantScopedPageInsteadOfTop50Snapshot",
    ],
    [
      AUTHORING_BATCH_JOB_CONTROLLER_TEST,
      authoringBatchJobControllerTest,
      "recentEndpointReturnsServerPage",
    ],
    [
      AUTHORING_BATCH_JOB_CONTROLLER_TEST,
      authoringBatchJobControllerTest,
      "$.data.items[0].jobId",
    ],
    [API_HOOKS, apiHooks, "PageResponse<AuthoringBatchJobResponse>"],
    [API_HOOKS, apiHooks, "{ params: { page, size } }"],
    [API_HOOKS_TEST, apiHooksTest, "useAuthoringBatchJobs({ page: 2, size: 20 })"],
    [
      AUTHORING_BATCH_DRAWER,
      authoringBatchDrawer,
      "AUTHORING_BATCH_JOB_PAGE_SIZE = 20",
    ],
    [AUTHORING_BATCH_DRAWER, authoringBatchDrawer, "jobPage"],
    [
      AUTHORING_BATCH_DRAWER,
      authoringBatchDrawer,
      "useAuthoringBatchJobs({",
    ],
    [AUTHORING_BATCH_DRAWER, authoringBatchDrawer, "page: jobPage"],
    [
      AUTHORING_BATCH_DRAWER,
      authoringBatchDrawer,
      "size: AUTHORING_BATCH_JOB_PAGE_SIZE",
    ],
    [AUTHORING_BATCH_DRAWER, authoringBatchDrawer, "dataSource={jobsQuery.data?.items ?? []}"],
    [
      AUTHORING_BATCH_DRAWER,
      authoringBatchDrawer,
      "current: jobsQuery.data?.page ?? jobPage",
    ],
    [
      AUTHORING_BATCH_DRAWER,
      authoringBatchDrawer,
      "onChange: (page) => setJobPage(page)",
    ],
    [
      AUTHORING_BATCH_DRAWER_TEST,
      authoringBatchDrawerTest,
      "loads batch job records through server pagination",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.authoring-batch-job-ledger.required-snippet-missing",
        message: `批量维护任务记录分页缺少必要实现或测试片段：${snippet}`,
      });
    }
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

  const maintenanceBackendArrayPattern =
    /ApiResult<\s*(?:java\.util\.)?List<(?:IntegrationOnboardingResponse|WebhookConfigResponse|RegionalSourceResponse)>>/;
  if (
    maintenanceBackendArrayPattern.test(integrationController) ||
    integrationService.includes(
      "public List<IntegrationOnboardingResponse> listIntegrationOnboardings",
    ) ||
    integrationService.includes("public List<WebhookConfigResponse> getWebhooks") ||
    integrationService.includes(
      "public List<RegionalSourceResponse> listRegionalSources",
    )
  ) {
    violations.push({
      file: INTEGRATION_CONTROLLER,
      line: lineOf(integrationController, maintenanceBackendArrayPattern),
      ruleId: "b0.integration-maintenance-ledger.backend-array-forbidden",
      message:
        "AdapterHub 维护台账不得返回数组响应或全量租户快照，必须返回 PageResponse 并使用服务端分页。",
    });
  }

  const maintenanceFrontendArrayPattern =
    /dataSource=\{(?:onboardings|webhooks|regionalSources)\}[\s\S]{0,160}pagination=\{false\}/;
  if (
    apiHooks.includes("IntegrationEnvelope<IntegrationOnboarding[]>") ||
    apiHooks.includes("IntegrationEnvelope<IntegrationWebhookConfig[]>") ||
    apiHooks.includes("IntegrationEnvelope<RegionalSource[]>") ||
    adapterHub.includes("useIntegrationOnboardings();") ||
    adapterHub.includes("useWebhooks();") ||
    adapterHub.includes("useRegionalSources();") ||
    adapterHub.includes("const onboardings = onboardingsQuery.data ?? []") ||
    adapterHub.includes("const webhooks = webhooksQuery.data ?? []") ||
    adapterHub.includes("const regionalSources = regionalSourcesQuery.data ?? []") ||
    maintenanceFrontendArrayPattern.test(adapterHub)
  ) {
    violations.push({
      file: ADAPTER_HUB,
      line: lineOf(adapterHub, maintenanceFrontendArrayPattern),
      ruleId: "b0.integration-maintenance-ledger.frontend-array-forbidden",
      message:
        "AdapterHub 维护台账前端不得按数组快照消费，必须使用 PageResponse.items 和服务端分页。",
    });
  }

  for (const [file, content, snippet] of [
    [
      INTEGRATION_ONBOARDING_REPOSITORY,
      integrationOnboardingRepository,
      "countByTenantId(String tenantId)",
    ],
    [
      INTEGRATION_ONBOARDING_REPOSITORY,
      integrationOnboardingRepository,
      "pageByTenantId",
    ],
    [
      INTEGRATION_WEBHOOK_CONFIG_REPOSITORY,
      integrationWebhookConfigRepository,
      "countByTenantId(String tenantId)",
    ],
    [
      INTEGRATION_WEBHOOK_CONFIG_REPOSITORY,
      integrationWebhookConfigRepository,
      "pageByTenantId",
    ],
    [
      REGIONAL_SOURCE_REPOSITORY,
      regionalSourceRepository,
      "countByTenantId(String tenantId)",
    ],
    [REGIONAL_SOURCE_REPOSITORY, regionalSourceRepository, "pageByTenantId"],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "PageResponse<IntegrationOnboardingResponse> listIntegrationOnboardings",
    ],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "PageResponse<WebhookConfigResponse> getWebhooks",
    ],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "PageResponse<RegionalSourceResponse> listRegionalSources",
    ],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "onboardingRepository.countByTenantId",
    ],
    [INTEGRATION_SERVICE, integrationService, "webhookRepository.countByTenantId"],
    [
      INTEGRATION_SERVICE,
      integrationService,
      "regionalSourceRepository.countByTenantId",
    ],
    [INTEGRATION_SERVICE, integrationService, ".pageByTenantId"],
    [
      INTEGRATION_CONTROLLER,
      integrationController,
      "ApiResult<PageResponse<IntegrationOnboardingResponse>>",
    ],
    [
      INTEGRATION_CONTROLLER,
      integrationController,
      "ApiResult<PageResponse<WebhookConfigResponse>>",
    ],
    [
      INTEGRATION_CONTROLLER,
      integrationController,
      "ApiResult<PageResponse<RegionalSourceResponse>>",
    ],
    [API_HOOKS, apiHooks, "export interface IntegrationMaintenancePageParams"],
    [
      API_HOOKS,
      apiHooks,
      "IntegrationEnvelope<PageResponse<IntegrationOnboarding>>",
    ],
    [
      API_HOOKS,
      apiHooks,
      "IntegrationEnvelope<PageResponse<IntegrationWebhookConfig>>",
    ],
    [
      API_HOOKS,
      apiHooks,
      "IntegrationEnvelope<PageResponse<RegionalSource>>",
    ],
    [API_HOOKS, apiHooks, "emptyIntegrationPage"],
    [ADAPTER_HUB, adapterHub, "INTEGRATION_MAINTENANCE_PAGE_SIZE = 20"],
    [ADAPTER_HUB, adapterHub, "const [onboardingPage, setOnboardingPage]"],
    [ADAPTER_HUB, adapterHub, "const [webhookPage, setWebhookPage]"],
    [ADAPTER_HUB, adapterHub, "const [regionalSourcePage, setRegionalSourcePage]"],
    [ADAPTER_HUB, adapterHub, "useIntegrationOnboardings({"],
    [ADAPTER_HUB, adapterHub, "useWebhooks({"],
    [ADAPTER_HUB, adapterHub, "useRegionalSources({"],
    [ADAPTER_HUB, adapterHub, "page: onboardingPage"],
    [ADAPTER_HUB, adapterHub, "page: webhookPage"],
    [ADAPTER_HUB, adapterHub, "page: regionalSourcePage"],
    [ADAPTER_HUB, adapterHub, "size: INTEGRATION_MAINTENANCE_PAGE_SIZE"],
    [ADAPTER_HUB, adapterHub, "onboardingsQuery.data?.items ?? []"],
    [ADAPTER_HUB, adapterHub, "webhooksQuery.data?.items ?? []"],
    [ADAPTER_HUB, adapterHub, "regionalSourcesQuery.data?.items ?? []"],
    [ADAPTER_HUB, adapterHub, "total: onboardingsQuery.data?.total ?? 0"],
    [ADAPTER_HUB, adapterHub, "total: webhooksQuery.data?.total ?? 0"],
    [ADAPTER_HUB, adapterHub, "total: regionalSourcesQuery.data?.total ?? 0"],
    [ADAPTER_HUB, adapterHub, "onChange: setOnboardingPage"],
    [ADAPTER_HUB, adapterHub, "onChange: setWebhookPage"],
    [ADAPTER_HUB, adapterHub, "onChange: setRegionalSourcePage"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "useIntegrationOnboardings({ page: 2, size: 10 })",
    ],
    [API_HOOKS_TEST, apiHooksTest, "useWebhooks({ page: 3, size: 10 })"],
    [API_HOOKS_TEST, apiHooksTest, "useRegionalSources({ page: 4, size: 10 })"],
    [
      ADAPTER_HUB_TEST,
      adapterHubTest,
      "loads adapter hub maintenance ledgers through small server-side pages",
    ],
    [
      INTEGRATION_SERVICE_TEST,
      integrationServiceTest,
      "adapterHubMaintenanceListsUseTenantScopedPagesInsteadOfArraySnapshots",
    ],
    [
      INTEGRATION_CONTROLLER_SECURITY_TEST,
      integrationControllerSecurityTest,
      "adapterHubMaintenanceListsReturnPagedContractsForTenantOperators",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.integration-maintenance-ledger.required-snippet-missing",
        message: `AdapterHub 维护台账分页缺少仓储、服务、控制器、前端或测试片段：${snippet}`,
      });
    }
  }

  if (
    releaseGovernanceController.includes("ApiResult<List<OverrideTemplate>>") ||
    releaseGovernanceController.includes("ApiResult<java.util.List<OverrideTemplate>>") ||
    overrideTemplateService.includes("public List<OverrideTemplate> listTemplates")
  ) {
    violations.push({
      file: RELEASE_GOVERNANCE_CONTROLLER,
      line: lineOf(releaseGovernanceController, /ApiResult<.*List<OverrideTemplate>>/),
      ruleId: "b0.release-override-template-ledger.backend-array-forbidden",
      message:
        "发布治理覆盖模板不得返回数组响应或租户全量快照，必须返回 PageResponse 并使用服务端分页。",
    });
  }

  if (
    apiHooks.includes("data: OverrideTemplate[]") ||
    releaseGovernance.includes("useOverrideTemplates();") ||
    releaseGovernance.includes("(templatesQuery.data ?? []).map") ||
    releaseGovernance.includes("dataSource={templatesQuery.data ?? []}") ||
    /dataSource=\{[^}]*template[^}]*\}[\s\S]{0,160}pagination=\{false\}/i.test(
      releaseGovernance,
    )
  ) {
    violations.push({
      file: RELEASE_GOVERNANCE,
      line: lineOf(releaseGovernance, "useOverrideTemplates"),
      ruleId: "b0.release-override-template-ledger.frontend-array-forbidden",
      message:
        "发布治理覆盖模板前端不得按数组快照消费，必须使用 PageResponse.items 和服务端分页。",
    });
  }

  for (const [file, content, snippet] of [
    [
      OVERRIDE_TEMPLATE_REPOSITORY,
      overrideTemplateRepository,
      "countByTenantIdAndStatus",
    ],
    [OVERRIDE_TEMPLATE_REPOSITORY, overrideTemplateRepository, "pageByTenantIdAndStatus"],
    [
      OVERRIDE_TEMPLATE_SERVICE,
      overrideTemplateService,
      "PageResponse<OverrideTemplate> listTemplates",
    ],
    [
      OVERRIDE_TEMPLATE_SERVICE,
      overrideTemplateService,
      "templates.countByTenantIdAndStatus",
    ],
    [
      OVERRIDE_TEMPLATE_SERVICE,
      overrideTemplateService,
      "templates.pageByTenantIdAndStatus",
    ],
    [
      RELEASE_GOVERNANCE_CONTROLLER,
      releaseGovernanceController,
      "ApiResult<PageResponse<OverrideTemplate>> listTemplates",
    ],
    [
      RELEASE_GOVERNANCE_CONTROLLER,
      releaseGovernanceController,
      "new PageRequest(page, size, sort)",
    ],
    [API_HOOKS, apiHooks, "export interface OverrideTemplatesParams"],
    [API_HOOKS, apiHooks, "PageResponse<OverrideTemplate>"],
    [
      API_HOOKS,
      apiHooks,
      'queryKey: ["release-governance", "override-templates", params]',
    ],
    [API_HOOKS, apiHooks, "{ params }"],
    [RELEASE_GOVERNANCE, releaseGovernance, "OVERRIDE_TEMPLATE_PAGE_SIZE = 20"],
    [RELEASE_GOVERNANCE, releaseGovernance, "const [templatePage, setTemplatePage]"],
    [
      RELEASE_GOVERNANCE,
      releaseGovernance,
      "useOverrideTemplates({",
    ],
    [RELEASE_GOVERNANCE, releaseGovernance, "page: templatePage"],
    [
      RELEASE_GOVERNANCE,
      releaseGovernance,
      "size: OVERRIDE_TEMPLATE_PAGE_SIZE",
    ],
    [RELEASE_GOVERNANCE, releaseGovernance, "templatesQuery.data?.items ?? []"],
    [RELEASE_GOVERNANCE, releaseGovernance, "total: templatesQuery.data?.total ?? 0"],
    [RELEASE_GOVERNANCE, releaseGovernance, "onChange: setTemplatePage"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "useOverrideTemplates({ page: 2, size: 10 })",
    ],
    [
      RELEASE_GOVERNANCE_TEST,
      releaseGovernanceTest,
      "loads override templates through bounded server pagination",
    ],
    [
      OVERRIDE_TEMPLATE_SERVICE_TEST,
      overrideTemplateServiceTest,
      "listTemplatesReturnsTenantScopedPageInsteadOfArraySnapshot",
    ],
    [
      RELEASE_GOVERNANCE_CONTROLLER_TEST,
      releaseGovernanceControllerTest,
      "listsOverrideTemplatesAsPagedTenantScopedContract",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId:
          "b0.release-override-template-ledger.required-snippet-missing",
        message: `发布治理覆盖模板分页缺少仓储、服务、控制器、前端或测试片段：${snippet}`,
      });
    }
  }

  const packageSyncLogBackendArrayPattern =
    /ApiResult<\s*(?:java\.util\.)?List<SyncLogResponse>>/;
  if (
    packageSyncLogBackendArrayPattern.test(packageEngineController) ||
    packageEngineService.includes("public List<SyncLogResponse> listSyncLogs") ||
    packageEngineService.includes(
      "findByTenantIdAndPackageIdOrderByCreatedAtDesc(tenantId, packageId).stream()",
    ) ||
    thirdPartyPackageReconciliationResponse.includes("List<SyncLogResponse> logs") ||
    thirdPartyKnowledgeRuntimeService.includes("packages.listSyncLogs(normalizedPackageId)")
  ) {
    violations.push({
      file: PACKAGE_ENGINE_CONTROLLER,
      line: lineOf(packageEngineController, packageSyncLogBackendArrayPattern),
      ruleId: "b0.package-sync-log-ledger.backend-array-forbidden",
      message:
        "配置包同步日志和第三方对账不得返回数组响应或全量发布计划快照，必须返回 PageResponse 并使用服务端分页。",
    });
  }

  if (
    apiHooks.includes("data: SyncLogResponse[]") ||
    apiHooks.includes("function usePackageSyncLogs(packageId: string)") ||
    configPackages.includes('usePackageSyncLogs(effectivePackageId || "");') ||
    configPackages.includes("persistedSyncLogs ?? []") ||
    configPackages.includes(
      "const visibleSyncLogs = syncLogs.length > 0 ? syncLogs : (persistedSyncLogs ?? [])",
    )
  ) {
    violations.push({
      file: CONFIG_PACKAGES,
      line: lineOf(configPackages, "usePackageSyncLogs"),
      ruleId: "b0.package-sync-log-ledger.frontend-array-forbidden",
      message:
        "配置包同步日志前端不得按数组快照消费，必须使用 PageResponse.items 和服务端分页。",
    });
  }

  for (const [file, content, snippet] of [
    [SYNC_LOG_REPOSITORY, syncLogRepository, "countByTenantIdAndPackageId"],
    [SYNC_LOG_REPOSITORY, syncLogRepository, "pageByTenantIdAndPackageId"],
    [SYNC_LOG_REPOSITORY, syncLogRepository, "JOIN release_plan"],
    [
      SYNC_LOG_REPOSITORY,
      syncLogRepository,
      "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY",
    ],
    [
      PACKAGE_ENGINE_SERVICE,
      packageEngineService,
      "PageResponse<SyncLogResponse> listSyncLogs",
    ],
    [
      PACKAGE_ENGINE_SERVICE,
      packageEngineService,
      "logRepository.countByTenantIdAndPackageId",
    ],
    [
      PACKAGE_ENGINE_SERVICE,
      packageEngineService,
      ".pageByTenantIdAndPackageId",
    ],
    [
      PACKAGE_ENGINE_CONTROLLER,
      packageEngineController,
      "ApiResult<PageResponse<SyncLogResponse>> listSyncLogs",
    ],
    [
      PACKAGE_ENGINE_CONTROLLER,
      packageEngineController,
      "new PageRequest(page, size, null)",
    ],
    [
      THIRD_PARTY_PACKAGE_RECONCILIATION_RESPONSE,
      thirdPartyPackageReconciliationResponse,
      "PageResponse<SyncLogResponse> logs",
    ],
    [
      THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE,
      thirdPartyKnowledgeRuntimeService,
      "ThirdPartyPackageReconciliationResponse reconcilePackage(String packageId, PageRequest page)",
    ],
    [
      THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE,
      thirdPartyKnowledgeRuntimeService,
      "packages.listSyncLogs(normalizedPackageId, page)",
    ],
    [
      THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE,
      thirdPartyKnowledgeRuntimeService,
      "reconciliationStatus(logs.items())",
    ],
    [
      THIRD_PARTY_KNOWLEDGE_RUNTIME_CONTROLLER,
      thirdPartyKnowledgeRuntimeController,
      "new PageRequest(page, size, null)",
    ],
    [
      PACKAGE_ENGINE_SERVICE_TEST,
      packageEngineServiceTest,
      "listSyncLogsReturnsServerPageForPackageReleaseEvidence",
    ],
    [
      PACKAGE_ENGINE_SERVICE_TEST,
      packageEngineServiceTest,
      "countByTenantIdAndPackageId",
    ],
    [
      PACKAGE_ENGINE_SERVICE_TEST,
      packageEngineServiceTest,
      "pageByTenantIdAndPackageId",
    ],
    [
      PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST,
      packageEngineControllerSecurityTest,
      '"/pkg-1/sync-logs"',
    ],
    [
      PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST,
      packageEngineControllerSecurityTest,
      '.param("page", "1")',
    ],
    [
      PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST,
      packageEngineControllerSecurityTest,
      '.param("size", "20")',
    ],
    [
      PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST,
      packageEngineControllerSecurityTest,
      "$.data.items[0].status",
    ],
    [
      THIRD_PARTY_KNOWLEDGE_RUNTIME_SERVICE_TEST,
      thirdPartyKnowledgeRuntimeServiceTest,
      "PageResponse.of(logs, page, logs.size())",
    ],
    [API_HOOKS, apiHooks, "export interface PackageSyncLogParams"],
    [API_HOOKS, apiHooks, "PageResponse<SyncLogResponse>"],
    [
      API_HOOKS,
      apiHooks,
      "usePackageSyncLogs(packageId: string, params: PackageSyncLogParams = {})",
    ],
    [API_HOOKS, apiHooks, "{ params }"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      'usePackageSyncLogs("pkg-1", { page: 2, size: 10 })',
    ],
    [CONFIG_PACKAGES, configPackages, "PACKAGE_SYNC_LOG_PAGE_SIZE = 20"],
    [CONFIG_PACKAGES, configPackages, "const [syncLogPage, setSyncLogPage]"],
    [
      CONFIG_PACKAGES,
      configPackages,
      'usePackageSyncLogs(effectivePackageId || "", {',
    ],
    [CONFIG_PACKAGES, configPackages, "page: syncLogPage"],
    [CONFIG_PACKAGES, configPackages, "persistedSyncLogs?.items ?? []"],
    [CONFIG_PACKAGES, configPackages, "Pagination"],
    [CONFIG_PACKAGES, configPackages, "onChange={setSyncLogPage}"],
    [
      CONFIG_PACKAGES_TEST,
      configPackagesTest,
      "loads package sync evidence logs through server pagination",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.package-sync-log-ledger.required-snippet-missing",
        message: `配置包同步日志分页缺少仓储、服务、控制器、第三方门面、前端或测试片段：${snippet}`,
      });
    }
  }

  const packageReleaseAdapterBackendArrayPattern =
    /ApiResult<\s*(?:java\.util\.)?List<PackageReleaseAdapterResponse>>/;
  if (
    packageReleaseAdapterBackendArrayPattern.test(packageEngineController) ||
    packageEngineService.includes(
      "public List<PackageReleaseAdapterResponse> listReleaseAdapters",
    ) ||
    packageEngineService.includes("adapterRepository.findAllByTenantId(tenantId).stream()")
  ) {
    violations.push({
      file: PACKAGE_ENGINE_CONTROLLER,
      line: lineOf(packageEngineController, packageReleaseAdapterBackendArrayPattern),
      ruleId: "b0.package-release-adapter-list.backend-array-forbidden",
      message:
        "配置包发布适配器目录不得返回数组响应或租户全量快照，必须返回 PageResponse 并按 ACTIVE 状态服务端分页。",
    });
  }

  if (
    apiHooks.includes("data: PackageReleaseAdapter[]") ||
    apiHooks.includes("function usePackageReleaseAdapters(enabled = true)") ||
    configPackages.includes("usePackageReleaseAdapters();") ||
    configPackages.includes("const displayAdapters = releaseAdapters ?? []")
  ) {
    violations.push({
      file: CONFIG_PACKAGES,
      line: lineOf(configPackages, "usePackageReleaseAdapters"),
      ruleId: "b0.package-release-adapter-list.frontend-array-forbidden",
      message:
        "配置包发布适配器前端不得按数组快照消费，必须使用 PageResponse.items 和服务端分页。",
    });
  }

  for (const [file, content, snippet] of [
    [INTEGRATION_ADAPTER_REPOSITORY, integrationAdapterRepository, "countByTenantIdAndStatus"],
    [INTEGRATION_ADAPTER_REPOSITORY, integrationAdapterRepository, "pageByTenantIdAndStatus"],
    [
      PACKAGE_ENGINE_SERVICE,
      packageEngineService,
      "PageResponse<PackageReleaseAdapterResponse> listReleaseAdapters",
    ],
    [
      PACKAGE_ENGINE_SERVICE,
      packageEngineService,
      "adapterRepository.countByTenantIdAndStatus",
    ],
    [
      PACKAGE_ENGINE_SERVICE,
      packageEngineService,
      ".pageByTenantIdAndStatus",
    ],
    [
      PACKAGE_ENGINE_CONTROLLER,
      packageEngineController,
      "ApiResult<PageResponse<PackageReleaseAdapterResponse>> listReleaseAdapters",
    ],
    [
      PACKAGE_ENGINE_CONTROLLER,
      packageEngineController,
      "new PageRequest(page, size, null)",
    ],
    [
      PACKAGE_ENGINE_SERVICE_TEST,
      packageEngineServiceTest,
      "listReleaseAdaptersReturnsActivePageInsteadOfTenantSnapshot",
    ],
    [
      PACKAGE_ENGINE_SERVICE_TEST,
      packageEngineServiceTest,
      "pageByTenantIdAndStatus",
    ],
    [PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST, packageEngineControllerSecurityTest, "release-adapters"],
    [
      PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST,
      packageEngineControllerSecurityTest,
      '.param("page", "1")',
    ],
    [
      PACKAGE_ENGINE_CONTROLLER_SECURITY_TEST,
      packageEngineControllerSecurityTest,
      "$.data.items[0].adapterId",
    ],
    [API_HOOKS, apiHooks, "export interface PackageReleaseAdaptersParams"],
    [API_HOOKS, apiHooks, "PageResponse<PackageReleaseAdapter>"],
    [
      API_HOOKS,
      apiHooks,
      "usePackageReleaseAdapters(params: PackageReleaseAdaptersParams = {}, enabled = true)",
    ],
    [API_HOOKS, apiHooks, "{ params }"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "usePackageReleaseAdapters({ page: 2, size: 10 })",
    ],
    [CONFIG_PACKAGES, configPackages, "PACKAGE_RELEASE_ADAPTER_PAGE_SIZE = 20"],
    [CONFIG_PACKAGES, configPackages, "const [releaseAdapterPage, setReleaseAdapterPage]"],
    [CONFIG_PACKAGES, configPackages, "usePackageReleaseAdapters({"],
    [CONFIG_PACKAGES, configPackages, "page: releaseAdapterPage"],
    [CONFIG_PACKAGES, configPackages, "releaseAdapters?.items ?? []"],
    [CONFIG_PACKAGES, configPackages, "onChange={setReleaseAdapterPage}"],
    [
      CONFIG_PACKAGES_TEST,
      configPackagesTest,
      "loads package release adapters through server pagination",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.package-release-adapter-list.required-snippet-missing",
        message: `配置包发布适配器分页缺少仓储、服务、控制器、前端或测试片段：${snippet}`,
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
  const conditionFragmentImpactBackendSnapshotPattern =
    /ruleDefinitions\.listByFilter\(tenantId,\s*null,\s*null,\s*null,\s*null\)|pathwayTemplates\.listByFilter\(tenantId,\s*null,\s*null,\s*null,\s*null,\s*null\)/;
  if (conditionFragmentImpactBackendSnapshotPattern.test(conditionFragmentService)) {
    violations.push({
      file: CONDITION_FRAGMENT_SERVICE,
      line: lineOf(
        conditionFragmentService,
        conditionFragmentImpactBackendSnapshotPattern,
      ),
      ruleId:
        "b0.condition-fragment-impact.backend-tenant-snapshot-forbidden",
      message:
        "条件片段影响分析不得全量读取规则/路径后内存扫描，必须使用规则版本和路径边仓储预过滤分页。",
    });
  }
  if (
    apiHooks.includes("affectedAssets: ConditionFragmentAffectedAsset[]") ||
    ruleDefinitions.includes(
      "dataSource={conditionFragmentImpactQuery.data?.affectedAssets ?? []}",
    ) ||
    /title="条件片段影响分析"[\s\S]*?pagination=\{false\}/.test(ruleDefinitions)
  ) {
    violations.push({
      file: RULE_DEFINITIONS,
      line: lineOf(ruleDefinitions, "条件片段影响分析"),
      ruleId:
        "b0.condition-fragment-impact.frontend-array-forbidden",
      message:
        "条件片段影响分析前端不得消费全量 affectedAssets 数组或关闭分页，必须使用 PageResponse.items 和服务端分页。",
    });
  }
  for (const [file, content, snippet] of [
    [
      CONDITION_FRAGMENT_IMPACT_RESPONSE,
      conditionFragmentImpactResponse,
      "PageResponse<ConditionFragmentAffectedAsset> affectedAssets",
    ],
    [
      CONDITION_FRAGMENT_SERVICE,
      conditionFragmentService,
      "impact(String fragmentId, PageRequest pageRequest)",
    ],
    [
      CONDITION_FRAGMENT_SERVICE,
      conditionFragmentService,
      "countActiveRuleImpactsByFragmentPattern",
    ],
    [
      CONDITION_FRAGMENT_SERVICE,
      conditionFragmentService,
      "pageActiveRuleImpactsByFragmentPattern",
    ],
    [
      CONDITION_FRAGMENT_SERVICE,
      conditionFragmentService,
      "countTemplateImpactsByFragmentPattern",
    ],
    [
      CONDITION_FRAGMENT_SERVICE,
      conditionFragmentService,
      "pageTemplateImpactsByFragmentPattern",
    ],
    [
      RULE_DEFINITION_REPOSITORY,
      ruleDefinitionRepository,
      "pageActiveRuleImpactsByFragmentPattern",
    ],
    [
      RULE_DEFINITION_REPOSITORY,
      ruleDefinitionRepository,
      "countActiveRuleImpactsByFragmentPattern",
    ],
    [
      PATHWAY_TEMPLATE_REPOSITORY,
      pathwayTemplateRepository,
      "pageTemplateImpactsByFragmentPattern",
    ],
    [
      PATHWAY_TEMPLATE_REPOSITORY,
      pathwayTemplateRepository,
      "countTemplateImpactsByFragmentPattern",
    ],
    [
      CONDITION_FRAGMENT_CONTROLLER,
      conditionFragmentController,
      "service.impact(fragmentId, new PageRequest(page, size, sort))",
    ],
    [
      API_HOOKS,
      apiHooks,
      "affectedAssets: PageResponse<ConditionFragmentAffectedAsset>",
    ],
    [API_HOOKS, apiHooks, "useConditionFragmentImpact("],
    [API_HOOKS, apiHooks, "{ params }"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      "loads condition fragment impact through paged authoring endpoint",
    ],
    [
      RULE_DEFINITIONS,
      ruleDefinitions,
      "CONDITION_FRAGMENT_IMPACT_PAGE_SIZE = 20",
    ],
    [RULE_DEFINITIONS, ruleDefinitions, "impactPage"],
    [
      RULE_DEFINITIONS,
      ruleDefinitions,
      "conditionFragmentImpactQuery.data?.affectedAssets.items ?? []",
    ],
    [
      RULE_DEFINITIONS,
      ruleDefinitions,
      "total: conditionFragmentImpactQuery.data?.affectedAssets.total ?? 0",
    ],
    [
      RULE_DEFINITIONS_TEST,
      ruleDefinitionsTest,
      "条件片段影响分析通过小页服务端分页加载受影响资产",
    ],
    [
      CONDITION_FRAGMENT_SERVICE_TEST,
      conditionFragmentServiceTest,
      "impactFindsRulesAndPathwaysReferencingSameFragment",
    ],
    [
      CONDITION_FRAGMENT_SERVICE_TEST,
      conditionFragmentServiceTest,
      "verify(ruleDefinitions, never()).listByFilter",
    ],
    [
      CONDITION_FRAGMENT_CONTROLLER_TEST,
      conditionFragmentControllerTest,
      "$.data.affectedAssets.items[0].assetType",
    ],
    [
      AUTHORING_ASSET_LIBRARY_REPOSITORY_TEST,
      authoringAssetLibraryRepositoryTest,
      "fragmentImpactPrefilterQueriesExecuteThroughRepositoryPagination",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId:
          "b0.condition-fragment-impact.required-snippet-missing",
        message: `B0 条件片段影响分析必须保持仓储预过滤与服务端分页：${snippet}`,
      });
    }
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
  const terminologyZeroBasedPagePattern =
    /use(?:TerminologyMappings|StandardTerms|LocalTerms|TerminologyCandidates|TerminologyConflicts)\(\s*\{[^}]*page:\s*0/s;
  if (terminologyZeroBasedPagePattern.test(terminologyMapping)) {
    violations.push({
      file: TERMINOLOGY_MAPPING,
      line: lineOf(terminologyMapping, terminologyZeroBasedPagePattern),
      ruleId: "b0.terminology-page.one-based-page-required",
      message:
        "术语映射维护不得向 1 基 PageRequest 端点发送 page: 0，必须统一使用 page: 1 起始页。",
    });
  }
  const oneBasedTerminologyHookUsages =
    (apiHooks.match(/compactOneBasedPageParams\(params\)/g) ?? []).length;
  for (const [file, content, snippet] of [
    [API_HOOKS, apiHooks, "function compactOneBasedPageParams"],
    [API_HOOKS, apiHooks, "compactOneBasedPageParams(params ?? {})"],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      'params: { page: 1, size: 20, standardSystem: "LOINC", status: "ACTIVE" }',
    ],
    [
      API_HOOKS_TEST,
      apiHooksTest,
      'params: { page: 1, size: 10, status: "PENDING", riskLevel: "HIGH" }',
    ],
    [
      TERMINOLOGY_MAPPING_TEST,
      terminologyMappingTest,
      "expect.objectContaining({ page: 1, size: 20 })",
    ],
    [
      TERMINOLOGY_MAPPING_TEST,
      terminologyMappingTest,
      "expect.objectContaining({ page: 1, size: 10, status: \"OPEN\" })",
    ],
  ]) {
    if (!content.includes(snippet)) {
      violations.push({
        file,
        line: 1,
        ruleId: "b0.terminology-page.required-snippet-missing",
        message: `术语分页 1 基合同缺少必要实现或测试片段：${snippet}`,
      });
    }
  }
  if (oneBasedTerminologyHookUsages < 4) {
    violations.push({
      file: API_HOOKS,
      line: lineOf(apiHooks, "compactOneBasedPageParams"),
      ruleId: "b0.terminology-page.required-snippet-missing",
      message:
        "术语标准词、本地词、候选和冲突 Hook 都必须使用 compactOneBasedPageParams 归一化 page。",
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
