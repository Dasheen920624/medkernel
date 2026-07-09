import path from "node:path";

export type BrowserE2eRunStats = {
  startTime?: string;
  duration?: number;
  expected: number;
  unexpected: number;
  flaky: number;
  skipped?: number;
};

export type BrowserE2eTestResult = {
  file: string;
  title: string;
  status: "passed" | "failed" | "timedOut" | "skipped" | "interrupted";
  outcome?: "skipped" | "expected" | "unexpected" | "flaky";
  attachments?: BrowserE2eAttachment[];
};

export type BrowserE2eAttachment = {
  name: string;
  contentType?: string;
  body?: string;
};

export type LaunchCoverageRow = {
  code: string;
  status: "PASSED";
  evidenceKey: string;
  observedAt: string;
};

export type BrowserE2eLaunchEvidence = {
  schemaVersion: "1.0.0";
  stage: "BROWSER_E2E";
  status: "PASSED" | "FAILED";
  generatedAt: string;
  stats: BrowserE2eRunStats;
  tests: BrowserE2eTestResult[];
  launchCoverage: Record<string, LaunchCoverageRow[]>;
};

type CoverageProof = {
  file: string;
  titleIncludes?: string;
  claims: string[];
  requiresSystemFamilyAttachment?: boolean;
  requiresRealFrontdeskScenarioAttachment?: boolean;
  requiresServiceOrganizationScenarioAttachment?: boolean;
  requiresMfaLoginScenarioAttachment?: boolean;
  requiresDiagnosisKnowledgeScenarioAttachment?: boolean;
  requiresRuntimeReleaseAttachment?: boolean;
  requiresSourceLineageAttachment?: boolean;
  requiresEmbedBusinessHostAttachment?: boolean;
  requiresPathwayLifecycleAttachment?: boolean;
  requiresSystemProvidersAttachment?: boolean;
  requiresIdentityBindingAttachment?: boolean;
  requiresS2S4RuntimeMappingAttachment?: boolean;
  requiresCdssDeclarativeRuntimeAssetAttachment?: boolean;
  requiresMedicationSafetyFrontdeskAttachment?: boolean;
  requiresDiagnosticCriticalValueFrontdeskAttachment?: boolean;
  requiresDiagnosticFamilyConsumerSliceAttachment?: boolean;
  requiresDiagnosticReportFamilyMatrixAttachment?: boolean;
  requiresNursingContinuityFrontdeskAttachment?: boolean;
  requiresRegionalDiagnosticMutualRecognitionFrontdeskAttachment?: boolean;
  requiresRegionalRemoteConsumerSliceAttachment?: boolean;
  requiresPharmacyReviewAntimicrobialFrontdeskAttachment?: boolean;
  requiresPharmacyReviewConsumerSliceAttachment?: boolean;
  requiresInfectionPublicHealthSafetyFrontdeskAttachment?: boolean;
  requiresPublicHealthInfectionRegulatoryConsumerSliceAttachment?: boolean;
  requiresSurgeryAnesthesiaTransfusionFrontdeskAttachment?: boolean;
  requiresSurgeryAnesthesiaTransfusionConsumerSliceAttachment?: boolean;
  requiresCriticalEmergencyIcuFrontdeskAttachment?: boolean;
  requiresLisMonitoringCriticalConsumerSliceAttachment?: boolean;
  requiresReportInterpretationScenarioAttachment?: boolean;
  requiresRuntimeReleasePartialSelectionAttachment?: boolean;
  requiresFourRoleCoreActionsAttachment?: boolean;
  requiresSixEntryCoreActionsAttachment?: boolean;
  requiresPlatformAdminEntryCoreActionsAttachment?: boolean;
  requiresDomainFacadeB0EvidenceAttachment?: boolean;
  requiresFollowupPatientServiceConsumerSliceAttachment?: boolean;
  requiresHisEmrCdrConsumerSliceAttachment?: boolean;
};

const stakeholderClaims = [
  "stakeholderViews:PHYSICIAN",
  "stakeholderViews:NURSE",
  "stakeholderViews:PHARMACIST",
  "stakeholderViews:MEDICAL_TECHNICIAN",
  "stakeholderViews:QUALITY_CONTROLLER",
  "stakeholderViews:PATIENT_PROXY",
  "stakeholderViews:PLATFORM_ADMIN",
  "stakeholderViews:ENGINE_OPERATOR",
  "stakeholderViews:AUDITOR",
  "stakeholderViews:IT_MANAGER",
  "stakeholderViews:IMPLEMENTATION_ENGINEER",
  "stakeholderViews:HOSPITAL_EXECUTIVE",
];

const runtimeReleaseClaims = [
  "productLayers:RELEASE_GOVERNANCE",
  "versionedAssets:KNOWLEDGE",
  "versionedAssets:TERMINOLOGY",
  "versionedAssets:RULE",
  "versionedAssets:PATHWAY",
  "versionedAssets:EVALUATION",
  "versionedAssets:FOLLOWUP",
  "versionedAssets:FIELD_CATALOG",
  "versionedAssets:SAFETY",
  "versionedAssets:CDSS_RISK",
  "versionedAssets:VALUE_SET",
  "versionedAssets:FORMULA",
  "versionedAssets:ORDER_SET",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:MANAGEMENT_WORKSPACE",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
];

const runtimeReleasePartialSelectionClaims = ["scenarios:S13"];
const multiHospitalRuntimeIsolationClaims = [
  "multiHospitalRuntimeIsolationRows:TWO_HOSPITAL_RUNTIME_RELEASE_ISOLATION",
];
const runtimeReleaseScenarioConditionRows = [
  {
    code: "S13__NORMAL",
    scenarioCode: "S13",
    condition: "NORMAL",
    source: "RUNTIME_RELEASE_ACTIVATION_ROLLBACK_CONTRACT_READBACK",
  },
  {
    code: "S13__DEGRADATION",
    scenarioCode: "S13",
    condition: "DEGRADATION",
    source: "RUNTIME_RELEASE_ROLLBACK_DEGRADATION_RECOVERY",
  },
] as const;

const thirdPartySystemFamilyClaims = [
  "productLayers:DATA_INTEROPERABILITY",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "thirdPartySystemFamilies:HIS_EMR_CDR",
  "thirdPartySystemFamilies:LIS_MONITORING_CRITICAL",
  "thirdPartySystemFamilies:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
  "thirdPartySystemFamilies:PHARMACY_REVIEW",
  "thirdPartySystemFamilies:NURSING_ANESTHESIA_TRANSFUSION_ICU",
  "thirdPartySystemFamilies:MEDICAL_RECORD_INSURANCE_PAYMENT",
  "thirdPartySystemFamilies:PUBLIC_HEALTH_INFECTION_REGULATORY",
  "thirdPartySystemFamilies:FOLLOWUP_PATIENT_SERVICE",
  "thirdPartySystemFamilies:CA_OIDC_SSO_HR",
  "thirdPartySystemFamilies:REGIONAL_REMOTE",
  "thirdPartySystemFamilies:SPD_UDI_DEVICE",
  "thirdPartySystemFamilies:RESEARCH_ETHICS_DATA",
  "thirdPartySystemFamilies:MODEL_DIFY_AGENT",
];

const thirdPartySystemFamilyDegradationClaims = thirdPartySystemFamilyClaims
  .filter((claim) => claim.startsWith("thirdPartySystemFamilies:"))
  .map((claim) =>
    claim.replace("thirdPartySystemFamilies:", "thirdPartySystemFamilyDegradationRows:"),
  );
const thirdPartySystemFamilyScenarioConditionRows = [
  {
    code: "S33__MISSING_DATA",
    scenarioCode: "S33",
    condition: "MISSING_DATA",
    source: "SPD_UDI_DEVICE_CONSUMER_AND_STANDARD_RESOURCE_MISSING",
  },
  {
    code: "S34__MISSING_DATA",
    scenarioCode: "S34",
    condition: "MISSING_DATA",
    source: "RESEARCH_ETHICS_DATA_CONSUMER_AND_STANDARD_RESOURCE_MISSING",
  },
] as const;

const s2s4RuntimeMappingClaims = [
  "scenarios:S2",
  "scenarios:S4",
  "productLayers:DATA_INTEROPERABILITY",
  "productLayers:MEDICAL_ASSET",
  "versionedAssets:TERMINOLOGY",
  "deliveryShapes:MANAGEMENT_WORKSPACE",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
];

const cdssDeclarativeRuntimeAssetClaims = [
  "scenarios:S5",
  "productLayers:CLINICAL_EXECUTION",
  "versionedAssets:VALUE_SET",
  "versionedAssets:FORMULA",
  "versionedAssets:ACTION_CARD",
  "serviceCombinations:CLINICAL_RUNTIME",
];
const cdssDeclarativeRuntimeAssetScenarioConditionRows = [
  {
    code: "S5__NORMAL",
    scenarioCode: "S5",
    condition: "NORMAL",
    source: "CDSS_DECLARATIVE_RUNTIME_ASSET_CONSUMPTION",
  },
  {
    code: "S5__DEGRADATION",
    scenarioCode: "S5",
    condition: "DEGRADATION",
    source: "CDSS_MODEL_DISABLED_DECLARATIVE_RUNTIME_CONTINUES",
  },
] as const;

const medicationSafetyFrontdeskClaims = [
  "scenarios:S5",
  "productLayers:CLINICAL_EXECUTION",
  "versionedAssets:SAFETY",
  "versionedAssets:CDSS_RISK",
  "versionedAssets:RULE",
  "serviceCombinations:CLINICAL_RUNTIME",
];

const diagnosticCriticalValueFrontdeskClaims = [
  "scenarios:S36",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "versionedAssets:KNOWLEDGE",
  "versionedAssets:FIELD_CATALOG",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
];

const reportInterpretationScenarioClaims = [
  "scenarios:S17",
  "productLayers:CLINICAL_EXECUTION",
  "versionedAssets:KNOWLEDGE",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:PROFESSIONAL_COLLABORATION",
];

const diagnosticFamilyConsumerSliceClaims = [
  "thirdPartySystemFamilyConsumerSlices:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
];

const diagnosticReportFamilyMatrixClaims = [
  "diagnosticReportFamilyConsumerMatrix:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
];

const requiredStandardPatientResourceTypes = [
  "Patient",
  "AllergyIntolerance",
  "Encounter",
  "Condition",
  "NursingAssessment",
  "Observation",
  "DiagnosticReport",
  "Medication",
  "Procedure",
  "Document",
  "CarePlan",
  "FollowUp",
  "Claim",
];

const standardPatientResourceConsumerMatrixClaims = [
  "standardPatientResourceConsumerMatrix:THIRTEEN_STANDARD_RESOURCES_REPRESENTATIVE",
];

const standardPatientResourceRepresentativeRowClaims = requiredStandardPatientResourceTypes.map(
  (code) => `standardPatientResourceRepresentativeRows:${code}`,
);

const versionedAssetSupplyChainMatrixClaims = [
  "versionedAssetSupplyChainMatrix:THIRTEEN_VERSIONED_ASSETS_GAP_AWARE_REPRESENTATIVE",
];

const versionedAssetRollbackRepresentativeMatrixClaims = [
  "versionedAssetRollbackRepresentativeMatrix:GAP_AWARE_RUNTIME_CONSUMER_NEGATIVE_REPRESENTATIVE",
];

const versionedAssetDedicatedReleaseContractMatrixClaims = [
  "versionedAssetDedicatedReleaseContractMatrix:TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
];

const requiredVersionedAssetDedicatedReleaseContractAssets = [
  "TERMINOLOGY",
  "FIELD_CATALOG",
  "PATHWAY",
];

const requiredVersionedAssetRollbackRepresentativeAssets = [
  "SAFETY",
  "CDSS_RISK",
  "VALUE_SET",
  "FORMULA",
  "PATHWAY",
  "ORDER_SET",
  "EVALUATION",
];

const standardPatientResourceMatrixAttachmentNames: Record<string, string> = {
  "medication-safety-frontdesk.spec.ts": "medication-safety-frontdesk-codes",
  "pharmacy-review-antimicrobial-frontdesk.spec.ts":
    "pharmacy-review-antimicrobial-frontdesk-codes",
  "diagnostic-critical-value-frontdesk.spec.ts": "diagnostic-critical-value-frontdesk-codes",
  "nursing-continuity-frontdesk.spec.ts": "nursing-continuity-frontdesk-codes",
  "surgery-anesthesia-transfusion-frontdesk.spec.ts":
    "surgery-anesthesia-transfusion-frontdesk-codes",
  "real-frontdesk-rehearsal.spec.ts": "real-frontdesk-scenario-codes",
};

const standardPatientResourcePathPrefixes: Record<string, string> = {
  Patient: "clinicalContext.resources.patient",
  AllergyIntolerance: "clinicalContext.resources.allergyIntolerances[",
  Encounter: "clinicalContext.resources.encounters[",
  Condition: "clinicalContext.resources.conditions[",
  NursingAssessment: "clinicalContext.resources.nursingAssessments[",
  Observation: "clinicalContext.resources.observations[",
  DiagnosticReport: "clinicalContext.resources.diagnosticReports[",
  Medication: "clinicalContext.resources.medications[",
  Procedure: "clinicalContext.resources.procedures[",
  Document: "clinicalContext.resources.documents[",
  CarePlan: "clinicalContext.resources.carePlans[",
  FollowUp: "backflowContext.resources.followUps[",
  Claim: "clinicalContext.resources.claims[",
};

const regionalDiagnosticMutualRecognitionFrontdeskClaims = [
  "scenarios:S40",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "versionedAssets:KNOWLEDGE",
  "versionedAssets:FIELD_CATALOG",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:PROFESSIONAL_COLLABORATION",
];

const regionalRemoteConsumerSliceClaims = ["thirdPartySystemFamilyConsumerSlices:REGIONAL_REMOTE"];

const nursingContinuityFrontdeskClaims = [
  "scenarios:S20",
  "scenarios:S35",
  "productLayers:CLINICAL_EXECUTION",
  "versionedAssets:FOLLOWUP",
  "serviceCombinations:CLINICAL_RUNTIME",
];

const pharmacyReviewAntimicrobialFrontdeskClaims = [
  "scenarios:S18",
  "scenarios:S31",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "versionedAssets:TERMINOLOGY",
  "versionedAssets:SAFETY",
  "versionedAssets:CDSS_RISK",
  "versionedAssets:RULE",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:PROFESSIONAL_COLLABORATION",
  "serviceCombinations:QUALITY_IMPROVEMENT",
];

const pharmacyReviewConsumerSliceClaims = ["thirdPartySystemFamilyConsumerSlices:PHARMACY_REVIEW"];

const infectionPublicHealthSafetyFrontdeskClaims = [
  "scenarios:S21",
  "scenarios:S32",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "versionedAssets:TERMINOLOGY",
  "versionedAssets:RULE",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:PROFESSIONAL_COLLABORATION",
  "serviceCombinations:QUALITY_IMPROVEMENT",
];

const publicHealthInfectionRegulatoryConsumerSliceClaims = [
  "thirdPartySystemFamilyConsumerSlices:PUBLIC_HEALTH_INFECTION_REGULATORY",
];

const surgeryAnesthesiaTransfusionFrontdeskClaims = [
  "scenarios:S26",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "versionedAssets:TERMINOLOGY",
  "versionedAssets:SAFETY",
  "versionedAssets:CDSS_RISK",
  "versionedAssets:RULE",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:PROFESSIONAL_COLLABORATION",
  "serviceCombinations:QUALITY_IMPROVEMENT",
];

const nursingAnesthesiaTransfusionIcuConsumerSliceClaims = [
  "thirdPartySystemFamilyConsumerSlices:NURSING_ANESTHESIA_TRANSFUSION_ICU",
];

const criticalEmergencyIcuFrontdeskClaims = [
  "scenarios:S19",
  "scenarios:S24",
  "scenarios:S27",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "versionedAssets:TERMINOLOGY",
  "versionedAssets:CDSS_RISK",
  "versionedAssets:RULE",
  "versionedAssets:PATHWAY",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:PROFESSIONAL_COLLABORATION",
];

const lisMonitoringCriticalConsumerSliceClaims = [
  "thirdPartySystemFamilyConsumerSlices:LIS_MONITORING_CRITICAL",
];

const hisEmrCdrConsumerSliceClaims = ["thirdPartySystemFamilyConsumerSlices:HIS_EMR_CDR"];

const realFrontdeskScenarioClaims = ["scenarios:S10", "scenarios:S11", "scenarios:S12"];

const followupPatientServiceConsumerSliceClaims = [
  "thirdPartySystemFamilyConsumerSlices:FOLLOWUP_PATIENT_SERVICE",
];

const serviceOrganizationClaims = [
  "scenarios:S1",
  "scenarios:S14",
  "organizationLevels:HOSPITAL",
  "organizationLevels:CAMPUS_OR_MEMBER",
  "organizationLevels:DEPARTMENT",
  "organizationLevels:WARD",
  "serviceCombinations:ONBOARDING_INTEGRATION",
  "serviceCombinations:COMPLIANCE_OPERATIONS",
];

const mfaLoginClaims = [
  "scenarios:S14",
  "productLayers:FOUNDATION_GOVERNANCE",
  "serviceCombinations:COMPLIANCE_OPERATIONS",
];

const diagnosisKnowledgeClaims = [
  "scenarios:S3",
  "productLayers:MEDICAL_ASSET",
  "semanticFamilies:DISEASE_DIAGNOSIS",
  "specialtyDomains:CLINICAL_SPECIALTIES",
];

const requiredDomainFacadeB0Codes = [
  "NURSING-01",
  "REPORT-01",
  "POC-KNOW-01",
  "PHARMACY-01",
  "CRITICAL-01",
  "SPECIAL-POP-01",
  "PERIOP-01",
  "ONCO-RENAL-01",
  "ALLIED-CARE-01",
  "TCM-HEALTH-01",
  "INFECTION-PH-01",
  "PRIMARY-CARE-01",
  "REGION-COLLAB-01",
  "SPECIALTY-EXT-01",
  "RWD-01",
  "SVC-DOMAIN-01",
  "SVC-DOMAIN-02",
] as const;

const domainFacadeB0Claims = ["domainFacadeB0Coverage:CLINICAL_SPECIALTY_DOMAIN_B0_FACADE_CATALOG"];

const sourceLineageClaims = ["scenarios:S7", "semanticFamilies:SOURCE_VALIDITY"];

const embedBusinessHostClaims = [
  "scenarios:S8",
  "productLayers:DELIVERY_FEEDBACK",
  "deliveryShapes:EMBEDDED_COMPONENT",
];

const pathwayLifecycleClaims = [
  "scenarios:S6",
  "productLayers:CLINICAL_EXECUTION",
  "versionedAssets:ORDER_SET",
  "serviceCombinations:SPECIAL_DISEASE_PATHWAY",
];

const systemProvidersClaims = [
  "deliveryShapes:MANAGEMENT_WORKSPACE",
  "serviceCombinations:COMPLIANCE_OPERATIONS",
];

const identityBindingClaims = [
  "scenarios:S14",
  "productLayers:FOUNDATION_GOVERNANCE",
  "serviceCombinations:COMPLIANCE_OPERATIONS",
];

const fourRoleCoreActionsClaims = ["roleRepresentativeCoreActions:FOUR_ROLE_PRIMARY_ACTIONS"];
const sixEntryCoreActionsClaims = [
  "entryRepresentativeCoreActions:SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE",
];
const complianceWorkbenchPersonalEntryMatrixClaims = [
  "complianceWorkbenchPersonalEntryMatrix:COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
];
const requiredComplianceWorkbenchPersonalEntryRows = [
  {
    row: "SECURITY_BASELINE_CONFIG_CHANGE",
    source: "entry",
    role: "platform-admin",
    path: "/security/baseline",
    serviceOperation: "PATCH /api/v1/system/configs/{key}",
  },
  {
    row: "AUDIT_EVIDENCE_EXPORT_VERIFY",
    source: "role",
    role: "auditor",
    path: "/admin/audit",
    serviceOperation: "POST /api/v1/compliance/evidence/snapshots/{evidenceId}/verify",
  },
  {
    row: "NOTIFICATION_READBACK",
    source: "entry",
    role: "clinical-user",
    path: "/notifications",
    serviceOperation: "POST /api/v1/engine/notifications/{notificationId}/read",
  },
  {
    row: "NOTIFICATION_SETTINGS_SAVE",
    source: "entry",
    role: "clinical-user",
    path: "/notifications/settings",
    serviceOperation: "PUT /api/v1/engine/notifications/settings",
  },
  {
    row: "SOURCE_LINEAGE_PROVENANCE_READBACK",
    source: "entry",
    role: "auditor",
    path: "/advanced/provenance",
    serviceOperation: "GET /api/v1/engine/knowledge/identities/{id}/provenance",
  },
] as const;
const platformAdminEntryCoreActionsClaims = [
  "platformAdminEntryCoreActions:FOUR_PLATFORM_ADMIN_P0_ENTRY_ACTIONS",
];
const platformAdminP1EntryCoreActionsClaims = [
  "platformAdminP1EntryCoreActions:RUNTIME_DIAGNOSTICS_DOMESTIC_CHECK",
];
const launchReadinessStakeholderMatrixClaims = [
  "launchReadinessStakeholderMatrix:IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE",
];
const implementationGuideEntryCoreActionsClaims = [
  "implementationGuideEntryCoreActions:IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
];
const implementationGuideEntryCoreActionRowClaims = [
  "implementationGuideEntryCoreActionRows:IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY",
];
const dashboardWorkbenchCoreActionsClaims = [
  "dashboardWorkbenchCoreActions:FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS",
];
const roleScopeFrontdeskActionRepresentativeSliceClaims = [
  "roleScopeFrontdeskActionRepresentativeSlice:FOUR_ROLE_SCOPE_FRONTDESK_ACTION_REPRESENTATIVE",
];
const requiredDashboardWorkbenchRows = [
  {
    role: "platform-admin",
    row: "PLATFORM_ADMIN",
    title: "平台管理员工作台",
    primaryActionLabel: "维护人员与账号",
    primaryActionPath: "/admin/users",
    highFrequencyPaths: ["/security/baseline", "/onboarding/guide", "/adapter/hub"],
    serviceOperations: [
      "GET /api/v1/security/me",
      "GET /api/v1/system/operations",
      "GET /api/v1/compliance/audit/events",
      "GET /api/v1/large-lists/audit-events/list",
      "GET /api/v1/engine/tenant/success-plan",
    ],
  },
  {
    role: "engine-operator",
    row: "ENGINE_OPERATOR",
    title: "医疗引擎运营员工作台",
    primaryActionLabel: "进入知识生产",
    primaryActionPath: "/knowledge/production",
    highFrequencyPaths: ["/knowledge/governance", "/qc/alerts", "/advanced/provenance"],
    serviceOperations: [
      "GET /api/v1/security/me",
      "GET /api/v1/compliance/audit/events",
      "GET /api/v1/large-lists/audit-events/list",
    ],
  },
  {
    role: "clinical-user",
    row: "CLINICAL_USER",
    title: "临床使用者工作台",
    primaryActionLabel: "处理协同任务",
    primaryActionPath: "/workflow/todos",
    highFrequencyPaths: ["/pathway/patients", "/cdss/fatigue", "/clinical/followup"],
    serviceOperations: ["GET /api/v1/security/me"],
  },
  {
    role: "auditor",
    row: "AUDITOR",
    title: "审计员工作台",
    primaryActionLabel: "查看审计证据",
    primaryActionPath: "/admin/audit",
    highFrequencyPaths: ["/advanced/provenance", "/security/baseline"],
    serviceOperations: [
      "GET /api/v1/security/me",
      "GET /api/v1/system/operations",
      "GET /api/v1/compliance/audit/events",
      "GET /api/v1/large-lists/audit-events/list",
    ],
  },
] as const;
const requiredLaunchReadinessStakeholders = [
  {
    row: "IT_MANAGER_RUNTIME_DIAGNOSTICS",
    code: "IT_MANAGER",
    role: "platform-admin",
    path: "/system/runtime-diagnostics",
    actionTerms: ["运行诊断", "数据质量报告"],
  },
  {
    row: "IMPLEMENTATION_ENGINEER_ONBOARDING_GUIDE",
    code: "IMPLEMENTATION_ENGINEER",
    role: "platform-admin",
    path: "/onboarding/guide",
    actionTerms: ["数据质量报告"],
  },
  {
    row: "HOSPITAL_EXECUTIVE_QUALITY_OVERVIEW",
    code: "HOSPITAL_EXECUTIVE",
    role: "engine-operator",
    path: "/qc/dashboard",
    actionTerms: ["质量下钻", "整改证据"],
  },
];
const clinicalEntryCoreActionsClaims = [
  "clinicalEntryCoreActions:CLINICAL_COLLABORATION_CORE_ACTIONS_REPRESENTATIVE",
];
const qualityManagementEntryCoreActionsClaims = [
  "qualityManagementEntryCoreActions:QUALITY_MANAGEMENT_CORE_ACTIONS_REPRESENTATIVE",
];
const medicalRecordInsurancePaymentConsumerSliceClaims = [
  "thirdPartySystemFamilyConsumerSlices:MEDICAL_RECORD_INSURANCE_PAYMENT",
];
const knowledgeOperationsAssetEntryCoreActionsClaims = [
  "knowledgeOperationsAssetEntryCoreActions:KNOWLEDGE_OPERATIONS_ASSET_ENTRY_FAMILY_REPRESENTATIVE",
];
const menuEntryCoreActionsClaims = ["menuEntryCoreActions:ALL_34_MENU_ENTRY_CORE_ACTIONS"];
const requiredMenuEntryCoreActionRows = [
  "workbench",
  "tenant-onboarding",
  "admin-users",
  "identity-bindings",
  "admin-audit",
  "security-baseline",
  "implementation-guide",
  "adapter-hub",
  "system-providers",
  "runtime-diagnostics",
  "domestic-check",
  "notifications",
  "notification-settings",
  "knowledge-governance",
  "runtime-releases",
  "institution-knowledge",
  "diagnosis-knowledge",
  "terminology-mapping",
  "rule-definitions",
  "pathway-templates",
  "provenance",
  "graph-explore",
  "knowledge-production",
  "ai-workflows",
  "clinical-followup",
  "sandbox",
  "qc-dashboard",
  "qc-alerts",
  "insurance-audit",
  "qc-eval-sets",
  "mpi",
  "patient-pathways",
  "cdss-fatigue",
  "workflow-todos",
] as const;

const knowledgeSupplyChainEvidenceMatrixClaims = [
  "knowledgeSupplyChainEvidenceMatrix:CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE",
];

const requiredKnowledgeSupplyChainEvidenceRows = [
  "SOURCE_CONTROL",
  "HUMAN_GOVERNANCE",
  "TERMINOLOGY_SYNC",
  "RUNTIME_LIFECYCLE",
  "LINEAGE_CONSUMERS",
  "SAFETY_BOUNDARY",
];

const requiredThirdPartySystemFamilyCodes = thirdPartySystemFamilyClaims
  .filter((claim) => claim.startsWith("thirdPartySystemFamilies:"))
  .map((claim) => claim.split(":")[1]);

const serviceOrganizationScenarioConditionRows = [
  {
    code: "S1__NORMAL",
    scenarioCode: "S1",
    condition: "NORMAL",
    source: "SERVICE_ORGANIZATION_ONBOARDING_ORG_TREE_READBACK",
  },
] as const;
const identityBindingScenarioConditionRows = [
  {
    code: "S14__NORMAL",
    scenarioCode: "S14",
    condition: "NORMAL",
    source: "IDENTITY_BINDING_LIFECYCLE_PLAINTEXT_SAFETY",
  },
] as const;
const mfaLoginScenarioConditionRows = [
  {
    code: "S14__HIGH_RISK",
    scenarioCode: "S14",
    condition: "HIGH_RISK",
    source: "MFA_TOTP_REQUIRED_VERIFIED_AND_RECOVERED",
  },
] as const;
const diagnosisKnowledgeScenarioConditionRows = [
  {
    code: "S3__NORMAL",
    scenarioCode: "S3",
    condition: "NORMAL",
    source: "DIAGNOSIS_KNOWLEDGE_ASSET_STANDARD_CASE_MAINTENANCE",
  },
  {
    code: "S3__ABNORMAL",
    scenarioCode: "S3",
    condition: "ABNORMAL",
    source: "DIAGNOSIS_KNOWLEDGE_EVIDENCE_EXCERPT_REJECTED_NO_ASSET_CREATED",
  },
] as const;
const diagnosisAssistRuntimeScenarioConditionRows = [
  {
    code: "S16__NORMAL",
    scenarioCode: "S16",
    condition: "NORMAL",
    source: "DIAGNOSIS_ASSIST_ACTIVE_RUNTIME_DIAGNOSIS_CONSUMPTION",
  },
] as const;
const implementationGuideScenarioConditionRows = [
  {
    code: "S23__ABNORMAL",
    scenarioCode: "S23",
    condition: "ABNORMAL",
    source: "IMPLEMENTATION_GUIDE_DATA_QUALITY_GAP_EVIDENCE",
  },
] as const;
const sourceLineageScenarioConditionRows = [
  {
    code: "S7__NORMAL",
    scenarioCode: "S7",
    condition: "NORMAL",
    source: "SOURCE_LINEAGE_GRAPH_PROVENANCE_READBACK",
  },
] as const;
const embedBusinessHostScenarioConditionRows = [
  {
    code: "S8__DEGRADATION",
    scenarioCode: "S8",
    condition: "DEGRADATION",
    source: "EMBEDDED_HOST_CALLBACK_NOT_CONNECTED_LOCAL_FEEDBACK_CONTINUES",
  },
] as const;
const pathwayLifecycleScenarioConditionRows = [
  {
    code: "S6__NORMAL",
    scenarioCode: "S6",
    condition: "NORMAL",
    source: "SPECIAL_DISEASE_PATHWAY_ORDER_SET_RUNTIME_CONSUMPTION",
  },
] as const;
const requiredS2S4RuntimeMappingScenarioCodes = ["S2", "S4"];
const dashboardWorkbenchScenarioConditionRows = [
  {
    code: "S0__NORMAL",
    scenarioCode: "S0",
    condition: "NORMAL",
    source: "DASHBOARD_WORKBENCH_FOUR_ROLE_SERVICE_READBACK",
  },
] as const;
const s2s4ScenarioConditionRows = [
  {
    code: "S2__NORMAL",
    scenarioCode: "S2",
    condition: "NORMAL",
    source: "SIGNED_WEBHOOK_INBOUND_NORMALIZATION",
  },
  {
    code: "S2__ABNORMAL",
    scenarioCode: "S2",
    condition: "ABNORMAL",
    source: "INVALID_INBOUND_WEBHOOK_SIGNATURE_REJECTED",
  },
  {
    code: "S4__NORMAL",
    scenarioCode: "S4",
    condition: "NORMAL",
    source: "TERMINOLOGY_RUNTIME_CONTRACT",
  },
  {
    code: "S4__ABNORMAL",
    scenarioCode: "S4",
    condition: "ABNORMAL",
    source: "INVALID_MASTER_DATA_SIGNATURE_REJECTED",
  },
] as const;
const medicationSafetyScenarioConditionRows = [
  {
    code: "S5__HIGH_RISK",
    scenarioCode: "S5",
    condition: "HIGH_RISK",
    source: "MEDICATION_SAFETY_CRITICAL_REDLINE_PHYSICIAN_CONFIRMATION",
  },
  {
    code: "S28__HIGH_RISK",
    scenarioCode: "S28",
    condition: "HIGH_RISK",
    source: "SPECIAL_POPULATION_MEDICATION_CONTRAINDICATION_PHYSICIAN_CONFIRMATION",
  },
] as const;
const qualityManagementScenarioConditionRows = [
  {
    code: "S9__ABNORMAL",
    scenarioCode: "S9",
    condition: "ABNORMAL",
    source: "MEDICAL_RECORD_CASE_REVIEW_ISSUE_FOUND",
  },
  {
    code: "S10__NORMAL",
    scenarioCode: "S10",
    condition: "NORMAL",
    source: "INSURANCE_AUDIT_SERVICE_READBACK",
  },
  {
    code: "S11__NORMAL",
    scenarioCode: "S11",
    condition: "NORMAL",
    source: "QUALITY_ALERT_RECTIFICATION_REVIEW",
  },
] as const;
const clinicalEntryScenarioConditionRows = [
  {
    code: "S11__NORMAL",
    scenarioCode: "S11",
    condition: "NORMAL",
    source: "CLINICAL_WORKFLOW_TODO_COMPLETION",
  },
] as const;
const realFrontdeskScenarioConditionRows = [
  {
    code: "S12__NORMAL",
    scenarioCode: "S12",
    condition: "NORMAL",
    source: "FOLLOWUP_TEMPLATE_PLAN_QUESTIONNAIRE_ABNORMAL_RETURN",
  },
  {
    code: "S12__ABNORMAL",
    scenarioCode: "S12",
    condition: "ABNORMAL",
    source: "FOLLOWUP_TEMPLATE_PLAN_QUESTIONNAIRE_ABNORMAL_RETURN",
  },
] as const;
const diagnosticCriticalValueScenarioConditionRows = [
  {
    code: "S36__HIGH_RISK",
    scenarioCode: "S36",
    condition: "HIGH_RISK",
    source: "DIAGNOSTIC_CRITICAL_VALUE_HUMAN_CLOSURE",
  },
  {
    code: "S36__DEGRADATION",
    scenarioCode: "S36",
    condition: "DEGRADATION",
    source: "FHIR_LIS_NOT_CONNECTED_COMPENSATION",
  },
] as const;
const regionalDiagnosticMutualRecognitionScenarioConditionRows = [
  {
    code: "S40__DEGRADATION",
    scenarioCode: "S40",
    condition: "DEGRADATION",
    source: "REGIONAL_DIAGNOSTIC_MUTUAL_RECOGNITION_NOT_CONNECTED_COMPENSATION",
  },
] as const;
const nursingContinuityScenarioConditionRows = [
  {
    code: "S20__NORMAL",
    scenarioCode: "S20",
    condition: "NORMAL",
    source: "NURSING_CONTINUITY_FOLLOWUP_PLAN_RESULT_BACKFLOW",
  },
  {
    code: "S35__ABNORMAL",
    scenarioCode: "S35",
    condition: "ABNORMAL",
    source: "NURSING_HIGH_RISK_ASSESSMENT_ABNORMAL_RETURN",
  },
] as const;
const surgeryAnesthesiaTransfusionScenarioConditionRows = [
  {
    code: "S26__HIGH_RISK",
    scenarioCode: "S26",
    condition: "HIGH_RISK",
    source: "SURGERY_ANESTHESIA_TRANSFUSION_CRITICAL_MANUAL_CONFIRMATION",
  },
  {
    code: "S26__DEGRADATION",
    scenarioCode: "S26",
    condition: "DEGRADATION",
    source: "SURGERY_ANESTHESIA_TRANSFUSION_OUTBOUND_NOT_CONNECTED",
  },
  {
    code: "S26__ABNORMAL",
    scenarioCode: "S26",
    condition: "ABNORMAL",
    source: "SURGERY_TIMELINE_RECTIFICATION_REVIEW",
  },
] as const;
const pharmacyReviewAntimicrobialScenarioConditionRows = [
  {
    code: "S18__HIGH_RISK",
    scenarioCode: "S18",
    condition: "HIGH_RISK",
    source: "PHARMACY_REVIEW_ANTIMICROBIAL_CRITICAL_MANUAL_CONFIRMATION",
  },
  {
    code: "S31__DEGRADATION",
    scenarioCode: "S31",
    condition: "DEGRADATION",
    source: "PHARMACY_REVIEW_OUTBOUND_NOT_CONNECTED",
  },
  {
    code: "S31__ABNORMAL",
    scenarioCode: "S31",
    condition: "ABNORMAL",
    source: "PHARMACY_REVIEW_RECTIFICATION_REVIEW",
  },
] as const;
const infectionPublicHealthSafetyScenarioConditionRows = [
  {
    code: "S21__HIGH_RISK",
    scenarioCode: "S21",
    condition: "HIGH_RISK",
    source: "INFECTION_PUBLIC_HEALTH_MANUAL_REPORT_CONFIRMATION",
  },
  {
    code: "S21__DEGRADATION",
    scenarioCode: "S21",
    condition: "DEGRADATION",
    source: "PUBLIC_HEALTH_OUTBOUND_NOT_CONNECTED",
  },
  {
    code: "S32__ABNORMAL",
    scenarioCode: "S32",
    condition: "ABNORMAL",
    source: "PUBLIC_HEALTH_SAFETY_EVENT_RECTIFICATION_REVIEW",
  },
] as const;
const criticalEmergencyIcuScenarioConditionRows = [
  {
    code: "S19__HIGH_RISK",
    scenarioCode: "S19",
    condition: "HIGH_RISK",
    source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
  },
  {
    code: "S24__HIGH_RISK",
    scenarioCode: "S24",
    condition: "HIGH_RISK",
    source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
  },
  {
    code: "S27__HIGH_RISK",
    scenarioCode: "S27",
    condition: "HIGH_RISK",
    source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
  },
] as const;
const reportInterpretationScenarioConditionRows = [
  {
    code: "S17__NORMAL",
    scenarioCode: "S17",
    condition: "NORMAL",
    source: "REPORT_INTERPRETATION_RUNTIME_KNOWLEDGE_TODO_CLOSED",
  },
] as const;
const requiredCdssDeclarativeRuntimeAssetScenarioCodes = ["S5"];
const requiredMedicationSafetyFrontdeskScenarioCodes = ["S5"];
const allowedMedicationSafetyFrontdeskScenarioCodes = ["S5", "S28"];
const requiredDiagnosticCriticalValueFrontdeskScenarioCodes = ["S36"];
const requiredRegionalDiagnosticMutualRecognitionScenarioCodes = ["S40"];
const requiredNursingContinuityFrontdeskScenarioCodes = ["S20", "S35"];
const requiredPharmacyReviewAntimicrobialFrontdeskScenarioCodes = ["S18", "S31"];
const requiredInfectionPublicHealthSafetyFrontdeskScenarioCodes = ["S21", "S32"];
const requiredSurgeryAnesthesiaTransfusionFrontdeskScenarioCodes = ["S26"];
const requiredCriticalEmergencyIcuFrontdeskScenarioCodes = ["S19", "S24", "S27"];

const requiredS2S4RuntimeMappingScenarioEvidence: Record<string, string[]> = {
  S2: [
    "平台管理员前台创建 LIS Webhook 适配器并配置字段映射",
    "平台管理员前台创建回调通道并完成签名预览",
    "真实 Webhook 入站通过验签并生成标准临床事件",
    "入站字段映射按当前机构生效版本完成术语归一",
  ],
  S4: [
    "前台登记标准术语",
    "签名主数据同步登记院内术语",
    "前台生成并确认术语映射候选",
    "前台生成不可变术语资产版本",
    "当前机构生效版本和第三方运行契约读回同一术语资产",
  ],
};

const requiredCdssDeclarativeRuntimeAssetScenarioEvidence: Record<string, string[]> = {
  S5: [
    "前台创建 VALUE_SET 值集资产草稿",
    "前台创建 FORMULA 公式资产草稿",
    "前台创建 ACTION_CARD 临床提示卡资产草稿",
    "临床规则引用三类运行资产",
    "当前机构生效版本包含三类本轮运行资产",
    "临床用户从真实前台触发 CDSS 推荐评估",
    "推荐卡解释证明三类资产按当前机构生效版本物化消费",
  ],
};

const requiredMedicationSafetyFrontdeskScenarioEvidence: Record<string, string[]> = {
  S5: [
    "运营员创建真实 CDSS_RISK 风险矩阵",
    "运营员创建药物过敏禁忌 SAFETY 红线草稿",
    "运营员提交静默试运行并上线 SAFETY 资产",
    "运营员补齐 ATC:J01C 术语映射并激活到当前机构生效版本",
    "运营员创建 medication-prescribe 规则资产",
    "当前机构生效版本包含 SAFETY、CDSS_RISK 与 RULE",
    "临床用户从患者 360 建立 Medication 与 AllergyIntolerance 上下文",
    "临床用户从真实前台开立用药触发推荐评估",
    "药师登记红线复核且不关闭医生确认链路",
    "医生逐条确认采纳，系统不自动开嘱",
  ],
  S28: [
    "临床用户从患者 360 建立特殊人群用药上下文",
    "当前机构生效版本包含特殊人群用药禁忌 SAFETY 红线",
    "临床用户触发特殊人群用药高风险推荐评估",
    "药师登记红线复核且不关闭医生确认链路",
    "医生逐条确认采纳，系统不自动开嘱",
  ],
};

const requiredDiagnosticCriticalValueFrontdeskScenarioEvidence: Record<string, string[]> = {
  S36: [
    "外部 FHIR/LIS 入站 Observation 危急值并落标准资源",
    "外部 FHIR/LIS 入站已签发 DiagnosticReport 并落标准资源",
    "当前上下文回读 Observation 与 DiagnosticReport 均绑定同一机构生效版本",
    "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
    "临床用户从真实前台生成医技报告解读",
    "报告解读推荐卡证明危急风险、字段目录和提示卡按当前机构生效版本消费",
    "医技或医生人工完成报告解读待办，系统不改写报告且不自动开嘱",
  ],
};

const requiredRegionalDiagnosticMutualRecognitionScenarioEvidence: Record<string, string[]> = {
  S40: [
    "平台管理员登记 REGIONAL_REMOTE FHIR 接入申请并保持断连诚实状态",
    "平台管理员登记区域来源可信分级并回读跨机构证据",
    "外部区域 FHIR 入站已签发 DiagnosticReport 并落标准资源",
    "当前上下文回读跨机构 DiagnosticReport 并绑定同一机构生效版本",
    "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
    "临床用户从真实前台生成区域报告互认解读",
    "推荐卡证明互认理由、重复检查提示、字段目录和提示卡按当前机构生效版本消费",
    "医生人工完成互认协同待办，系统不自动互认、不改写报告且不自动开嘱",
  ],
};

const requiredNursingContinuityFrontdeskScenarioEvidence: Record<string, string[]> = {
  S20: [
    "运营员发布 FOLLOWUP 随访方案并激活到当前机构生效版本",
    "临床用户从真实前台基于护理上下文生成随访计划",
    "临床用户提交随访问卷并登记异常回院",
    "随访结果回流生成 FollowUp 标准资源并绑定同一机构生效版本",
  ],
  S35: [
    "临床用户从患者 360 建立护理高风险评估标准上下文",
    "标准上下文回读 NursingAssessment 与 CarePlan 护理事实",
    "随访计划解释消费 NursingAssessment 风险等级与护理计划节点",
  ],
};

const requiredPharmacyReviewAntimicrobialFrontdeskScenarioEvidence: Record<string, string[]> = {
  S18: [
    "运营员发布抗菌药物术语、红线、风险矩阵、规则和动作卡资产",
    "当前机构生效版本包含抗菌药物五类运行资产",
    "临床用户从患者 360 建立 Medication、AllergyIntolerance、Condition 与 Observation 上下文",
    "临床用户从真实前台触发 medication-prescribe 推荐评估",
    "推荐卡证明抗菌药物红线、规则和动作卡按当前机构生效版本消费",
    "药师登记审方复核且不关闭医生确认链路",
    "医生逐条确认采纳，系统不自动开嘱",
  ],
  S31: [
    "平台管理员访问真实前台并经真实服务创建 PHARMACY_REVIEW 适配器、回调通道和签名预览",
    "系统向 PHARMACY_REVIEW 发出审方请求并诚实断连降级",
    "PHARMACY_REVIEW 签名回传审方结果并生成标准临床事件",
    "药事治理问题形成整改任务",
    "固定四职责账号提交并复核关闭本轮整改任务",
  ],
};

const requiredInfectionPublicHealthSafetyFrontdeskScenarioEvidence: Record<string, string[]> = {
  S21: [
    "平台管理员访问真实前台并经真实服务创建 PUBLIC_HEALTH_INFECTION_REGULATORY 适配器、回调通道和签名预览",
    "运营员发布院感公卫术语、上报预填规则和动作卡资产",
    "当前机构生效版本包含院感公卫三类运行资产",
    "临床用户从患者 360 建立脱敏患者，签名入站事件生成感染诊断、检验结果和上报预填上下文",
    "系统向 PUBLIC_HEALTH_INFECTION_REGULATORY 发出上报预填回传并诚实断连降级",
    "PUBLIC_HEALTH_INFECTION_REGULATORY 签名回传感染监测结果并生成标准临床事件",
    "临床用户从真实前台触发 result-review 推荐评估",
    "推荐卡证明上报预填规则和动作卡按当前机构生效版本消费",
    "临床用户人工确认上报预填，系统不替代法定上报",
  ],
  S32: [
    "入站安全事件保留风险、原因和整改要求扩展证据",
    "医疗安全事件形成整改任务",
    "固定四职责账号提交并复核关闭本轮安全事件整改任务",
  ],
};

const requiredSurgeryAnesthesiaTransfusionFrontdeskScenarioEvidence: Record<string, string[]> = {
  S26: [
    "平台管理员访问真实前台并经真实服务创建 NURSING_ANESTHESIA_TRANSFUSION_ICU 适配器、回调通道和签名预览",
    "运营员发布手术操作术语、高危安全红线、麻醉用血风险矩阵、术前核查规则和动作卡资产",
    "当前机构生效版本包含围手术期五类运行资产",
    "签名入站事件生成 Procedure、Observation、Medication、Document 和手麻输血本地扩展上下文",
    "系统向 NURSING_ANESTHESIA_TRANSFUSION_ICU 发出核查确认回传并诚实断连降级",
    "临床用户从真实前台触发 order-sign 推荐评估",
    "推荐卡证明术前核查规则、安全红线和动作卡按当前机构生效版本消费",
    "临床用户人工确认围手术期风险，系统不自动输血、不自动开嘱、不自动手术",
    "围手术期时序质控形成整改任务并由固定职责账号复核关闭",
  ],
};

const requiredCriticalEmergencyIcuFrontdeskScenarioEvidence: Record<string, string[]> = {
  S19: [
    "平台管理员登记 LIS_MONITORING_CRITICAL 监护入站适配器、回调通道和签名预览",
    "运营员发布乳酸术语、急危重症风险矩阵、预警规则、升级路径和动作卡资产",
    "当前机构生效版本包含急危重症五类运行资产",
    "签名入站监护事件生成生命体征和检验 Observation 并处理到 PROCESSED",
    "临床用户从真实前台触发 patient-view 急危重症预警评估",
    "推荐卡证明风险规则和动作卡按当前机构生效版本消费",
  ],
  S24: [
    "临床用户从患者 360 建立急诊分诊上下文和去向候选",
    "推荐卡证明分诊等级和留观或入 ICU 候选仅为人工确认建议",
    "医生人工确认升级候选，系统不自动转科、不自动开嘱",
  ],
  S27: [
    "入站上下文保留生命支持模式、升压药运行和不控制设备证据",
    "推荐卡证明 ICU 生命支持风险与升级路径按当前机构生效版本消费",
    "临床用户从真实待办完成升级协同，系统不控制呼吸机或生命支持设备",
  ],
};

const requiredRealFrontdeskScenarioEvidence: Record<string, string[]> = {
  S10: ["前台执行医保审核并联动质量整改"],
  S11: ["前台创建发布并激活 CLAIM 评价指标", "前台提交并复核关闭质量整改任务"],
  S12: ["前台创建随访方案", "前台发布随访方案", "前台生成随访计划并完成问卷与异常回院登记"],
};

const requiredRealFrontdeskScenarioCodes = Object.keys(requiredRealFrontdeskScenarioEvidence);

const requiredServiceOrganizationScenarioEvidence: Record<string, string[]> = {
  S1: [
    "前台开通服务机构",
    "机构管理员首次登录并改密",
    "前台创建医疗机构、院区、科室与病区",
    "前台回读服务机构组织树",
  ],
  S14: ["前台创建临床账号并绑定科室职责范围", "临床账号首次登录后读取权限画像", "前台停用演练账号"],
};

const requiredServiceOrganizationScenarioCodes = Object.keys(
  requiredServiceOrganizationScenarioEvidence,
);

const requiredMfaLoginScenarioEvidence: Record<string, string[]> = {
  S14: [
    "配置中心读取上线默认 MFA 关闭",
    "创建 MFA 临时平台管理员账号",
    "临时账号完成首次改密并绑定 TOTP",
    "配置中心临时开启 MFA",
    "登录页要求已绑定账号完成 MFA 验证",
    "前台提交真实 TOTP 验证并进入工作台",
    "验证后回读权限画像与 MFA 状态",
    "恢复 MFA 上线默认关闭状态",
    "停用 MFA 演练临时管理员账号",
  ],
};

const requiredMfaLoginScenarioCodes = Object.keys(requiredMfaLoginScenarioEvidence);

const requiredDiagnosisKnowledgeScenarioEvidence: Record<string, string[]> = {
  S3: [
    "前台登记标准发现项术语",
    "前台创建证据完整诊断资产草稿",
    "前台登记诊断标准",
    "前台登记验证病例",
  ],
};

const requiredDiagnosisKnowledgeScenarioCodes = Object.keys(
  requiredDiagnosisKnowledgeScenarioEvidence,
);

const requiredRuntimeReleaseVersionedAssets = runtimeReleaseClaims
  .filter((claim) => claim.startsWith("versionedAssets:"))
  .map((claim) => claim.split(":")[1]);

const requiredRuntimeReleaseScenarioEvidence = [
  "前台展示并勾选 13 类平台标准资产",
  "前台评估机构生效版本发布影响",
  "前台生成携带 13 类资产闭包的机构生效版本",
  "后端回读当前机构生效版本资产闭包",
  "第三方运行契约读取同一机构生效版本",
  "前台从历史机构生效版本回滚",
  "回滚后后端和第三方运行契约读取同一修订",
];

const requiredRuntimeReleasePartialSelectionScenarioEvidence = [
  "前台只选择本轮部分本院内容进入机构生效版本",
  "前台为第二家医院选择不同本院内容生成机构生效版本",
  "两家医院后端与第三方运行契约读回互不串用",
  "前台完成平台升级差异与冲突分析",
  "前台导出机构生效版本离线交付文件",
  "下载离线交付文件并校验完整快照",
  "离线交付导入预检验签且不改写当前机构生效版本",
  "离线交付恢复执行生成新机构生效版本",
  "恢复后后端和第三方运行契约读取同一机构生效版本",
];

const requiredSourceLineageScenarioEvidence: Record<string, string[]> = {
  S7: [
    "真实登记受控来源、版本和锚点",
    "真实提交并审核激活带来源引用的知识候选",
    "真实绑定来源引用并回读血缘证据",
    "真实重建知识关系投影",
    "前台探索知识关系图并查看追踪证据",
  ],
};

const requiredSourceLineageScenarioCodes = Object.keys(requiredSourceLineageScenarioEvidence);

const requiredEmbedBusinessHostScenarioEvidence: Record<string, string[]> = {
  S8: [
    "真实签发一次性嵌入启动凭证",
    "独立业务系统宿主加载真实 iframe 启动地址",
    "嵌入终端真实兑换启动凭证并读取当前就诊上下文",
    "嵌入终端真实读取当前就诊推荐卡",
    "医师在嵌入终端提交采纳反馈",
    "独立业务系统宿主收到医师反馈 postMessage",
  ],
};

const requiredEmbedBusinessHostScenarioCodes = Object.keys(
  requiredEmbedBusinessHostScenarioEvidence,
);

const requiredPathwayLifecycleScenarioEvidence: Record<string, string[]> = {
  S6: [
    "前台创建专病路径草稿并保存节点边时钟",
    "后端回读路径节点边时钟与十阶段里程碑",
    "前台使用真实 ACTIVE 快照完成草稿试运行",
    "真实服务链路对已保存路径执行仿真",
    "临床用户基于当前机构生效版本读取入径候选",
    "临床用户办理患者入径并生成首个关键时钟",
    "临床用户完成当前节点并标准推进",
    "临床用户推进到医嘱套餐节点并消费当前机构生效版本 ORDER_SET",
    "真实后端登记路径变异与处置决策",
    "真实后端完成随访接续终点节点",
    "后端回读关键时钟和变异事实",
    "路径完成后生成随访接续证据",
  ],
};

const requiredPathwayLifecycleScenarioCodes = Object.keys(requiredPathwayLifecycleScenarioEvidence);

const requiredPathwayMilestoneStages = [
  "SCREENING_TRIAGE",
  "DIAGNOSIS_DIFFERENTIAL",
  "RISK_STRATIFICATION",
  "TREATMENT_DECISION",
  "EXECUTION_CANDIDATE",
  "MONITORING_WARNING",
  "DISCHARGE_REFERRAL",
  "REHAB_EDUCATION_FOLLOWUP",
  "OUTCOME_EVALUATION",
  "QUALITY_ITERATION",
];
const pathwayLifecycleSpecialDiseaseStageClaims = requiredPathwayMilestoneStages.map(
  (stage) => `specialDiseaseStages:${stage}`,
);

const systemProvidersScenarioConditionRows = [
  {
    code: "S15__NORMAL",
    scenarioCode: "S15",
    condition: "NORMAL",
    source: "SYSTEM_OPERATIONS_RESTORE_CONTINUITY",
  },
  {
    code: "S15__DEGRADATION",
    scenarioCode: "S15",
    condition: "DEGRADATION",
    source: "SYSTEM_DEPENDENCY_HONEST_DEGRADATION",
  },
  {
    code: "S15__MISSING_DATA",
    scenarioCode: "S15",
    condition: "MISSING_DATA",
    source: "SYSTEM_BACKUP_RESTORE_DRILL_EVIDENCE_NOT_AVAILABLE",
  },
  {
    code: "S14__ABNORMAL",
    scenarioCode: "S14",
    condition: "ABNORMAL",
    source: "CLINICAL_SYSTEM_OPERATIONS_FORBIDDEN",
  },
] as const;

const platformAdminP1ScenarioConditionRows = [
  {
    code: "S14__ABNORMAL",
    scenarioCode: "S14",
    condition: "ABNORMAL",
    source: "P1_SYSTEM_OPERATIONS_FORBIDDEN",
  },
] as const;

const requiredSystemProvidersScenarioEvidence = [
  "平台管理员读取真实服务运行保障快照",
  "前台展示备份恢复 RPO、RTO 与 SHA-256 校验策略",
  "前台展示依赖诚实降级并保留本地主链路提示",
  "证据详情展示部署档案、迁移路径和备份恢复诊断",
  "恢复后后端当前机构生效版本与第三方运行契约读回一致",
  "临床账号恢复后完成患者主索引和上下文主链路冒烟",
  "临床账号无法读取或展示服务运行保障快照",
];

const requiredIdentityBindingScenarioEvidence: Record<string, string[]> = {
  S14: [
    "前台创建身份来源演练人员账号",
    "前台绑定院内身份来源",
    "列表回读只展示脱敏身份提示",
    "后端拒绝重复外部身份绑定",
    "前台解绑身份来源并保留历史证据",
    "停用身份来源演练账号",
  ],
};

const requiredIdentityBindingScenarioCodes = Object.keys(requiredIdentityBindingScenarioEvidence);

const coverageProofs: CoverageProof[] = [
  {
    file: "stakeholder-view-rehearsal.spec.ts",
    titleIncludes: "十二类业务视角",
    claims: stakeholderClaims,
  },
  {
    file: "stakeholder-view-rehearsal.spec.ts",
    titleIncludes: "十二类业务视角",
    claims: reportInterpretationScenarioClaims,
    requiresReportInterpretationScenarioAttachment: true,
  },
  {
    file: "runtime-release-frontdesk.spec.ts",
    titleIncludes: "生成新生效版本并从历史版本回滚",
    claims: runtimeReleaseClaims,
    requiresRuntimeReleaseAttachment: true,
  },
  {
    file: "runtime-release-frontdesk.spec.ts",
    titleIncludes: "生成新生效版本并从历史版本回滚",
    claims: runtimeReleasePartialSelectionClaims,
    requiresRuntimeReleaseAttachment: true,
    requiresRuntimeReleasePartialSelectionAttachment: true,
  },
  {
    file: "third-party-system-families-rehearsal.spec.ts",
    titleIncludes: "逐类登记第三方系统族接入并验证断连诚实降级",
    claims: thirdPartySystemFamilyClaims,
    requiresSystemFamilyAttachment: true,
  },
  {
    file: "s2-s4-terminology-integration-rehearsal.spec.ts",
    titleIncludes: "平台管理员完成系统接入且运营员完成术语映射后真实入站消息按当前机构生效版本归一",
    claims: s2s4RuntimeMappingClaims,
    requiresS2S4RuntimeMappingAttachment: true,
  },
  {
    file: "cdss-runtime-declarative-assets.spec.ts",
    titleIncludes: "临床用户从真实前台触发 CDSS 推荐并消费当前机构生效版本声明式运行资产",
    claims: cdssDeclarativeRuntimeAssetClaims,
    requiresCdssDeclarativeRuntimeAssetAttachment: true,
  },
  {
    file: "medication-safety-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员围绕药物过敏红线完成当前机构生效版本推荐与人工确认闭环",
    claims: medicationSafetyFrontdeskClaims,
    requiresMedicationSafetyFrontdeskAttachment: true,
  },
  {
    file: "diagnostic-critical-value-frontdesk.spec.ts",
    titleIncludes: "临床用户与医技人员围绕危急值报告完成外部入站、报告解读与人工闭环",
    claims: diagnosticCriticalValueFrontdeskClaims,
    requiresDiagnosticCriticalValueFrontdeskAttachment: true,
  },
  {
    file: "diagnostic-critical-value-frontdesk.spec.ts",
    titleIncludes: "临床用户与医技人员围绕危急值报告完成外部入站、报告解读与人工闭环",
    claims: diagnosticFamilyConsumerSliceClaims,
    requiresDiagnosticCriticalValueFrontdeskAttachment: true,
    requiresDiagnosticFamilyConsumerSliceAttachment: true,
  },
  {
    file: "diagnostic-critical-value-frontdesk.spec.ts",
    titleIncludes: "临床用户与医技人员围绕危急值报告完成外部入站、报告解读与人工闭环",
    claims: diagnosticReportFamilyMatrixClaims,
    requiresDiagnosticCriticalValueFrontdeskAttachment: true,
    requiresDiagnosticReportFamilyMatrixAttachment: true,
  },
  {
    file: "regional-diagnostic-mutual-recognition-frontdesk.spec.ts",
    titleIncludes: "临床用户与平台管理员完成区域医技报告互认代表闭环",
    claims: regionalDiagnosticMutualRecognitionFrontdeskClaims,
    requiresRegionalDiagnosticMutualRecognitionFrontdeskAttachment: true,
  },
  {
    file: "regional-diagnostic-mutual-recognition-frontdesk.spec.ts",
    titleIncludes: "临床用户与平台管理员完成区域医技报告互认代表闭环",
    claims: regionalRemoteConsumerSliceClaims,
    requiresRegionalDiagnosticMutualRecognitionFrontdeskAttachment: true,
    requiresRegionalRemoteConsumerSliceAttachment: true,
  },
  {
    file: "nursing-continuity-frontdesk.spec.ts",
    titleIncludes: "临床用户围绕护理高风险评估完成随访计划、异常回院与结果回流闭环",
    claims: nursingContinuityFrontdeskClaims,
    requiresNursingContinuityFrontdeskAttachment: true,
  },
  {
    file: "pharmacy-review-antimicrobial-frontdesk.spec.ts",
    titleIncludes:
      "临床用户按医生和药师业务任职与运营员、平台管理员完成本轮抗菌药物审方代表切片回传、推荐确认和整改闭环",
    claims: pharmacyReviewAntimicrobialFrontdeskClaims,
    requiresPharmacyReviewAntimicrobialFrontdeskAttachment: true,
  },
  {
    file: "pharmacy-review-antimicrobial-frontdesk.spec.ts",
    titleIncludes:
      "临床用户按医生和药师业务任职与运营员、平台管理员完成本轮抗菌药物审方代表切片回传、推荐确认和整改闭环",
    claims: pharmacyReviewConsumerSliceClaims,
    requiresPharmacyReviewAntimicrobialFrontdeskAttachment: true,
    requiresPharmacyReviewConsumerSliceAttachment: true,
  },
  {
    file: "infection-public-health-safety-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成院感公卫上报预填和医疗安全事件整改代表闭环",
    claims: infectionPublicHealthSafetyFrontdeskClaims,
    requiresInfectionPublicHealthSafetyFrontdeskAttachment: true,
  },
  {
    file: "infection-public-health-safety-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成院感公卫上报预填和医疗安全事件整改代表闭环",
    claims: publicHealthInfectionRegulatoryConsumerSliceClaims,
    requiresInfectionPublicHealthSafetyFrontdeskAttachment: true,
    requiresPublicHealthInfectionRegulatoryConsumerSliceAttachment: true,
  },
  {
    file: "surgery-anesthesia-transfusion-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成围手术期麻醉输血核查代表闭环",
    claims: surgeryAnesthesiaTransfusionFrontdeskClaims,
    requiresSurgeryAnesthesiaTransfusionFrontdeskAttachment: true,
  },
  {
    file: "surgery-anesthesia-transfusion-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成围手术期麻醉输血核查代表闭环",
    claims: nursingAnesthesiaTransfusionIcuConsumerSliceClaims,
    requiresSurgeryAnesthesiaTransfusionFrontdeskAttachment: true,
    requiresSurgeryAnesthesiaTransfusionConsumerSliceAttachment: true,
  },
  {
    file: "critical-emergency-icu-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成急诊分诊与 ICU 生命支持风险代表闭环",
    claims: criticalEmergencyIcuFrontdeskClaims,
    requiresCriticalEmergencyIcuFrontdeskAttachment: true,
  },
  {
    file: "critical-emergency-icu-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成急诊分诊与 ICU 生命支持风险代表闭环",
    claims: lisMonitoringCriticalConsumerSliceClaims,
    requiresCriticalEmergencyIcuFrontdeskAttachment: true,
    requiresLisMonitoringCriticalConsumerSliceAttachment: true,
  },
  {
    file: "critical-emergency-icu-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成急诊分诊与 ICU 生命支持风险代表闭环",
    claims: hisEmrCdrConsumerSliceClaims,
    requiresCriticalEmergencyIcuFrontdeskAttachment: true,
    requiresHisEmrCdrConsumerSliceAttachment: true,
  },
  {
    file: "real-frontdesk-rehearsal.spec.ts",
    titleIncludes:
      "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
    claims: realFrontdeskScenarioClaims,
    requiresRealFrontdeskScenarioAttachment: true,
  },
  {
    file: "real-frontdesk-rehearsal.spec.ts",
    titleIncludes:
      "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
    claims: followupPatientServiceConsumerSliceClaims,
    requiresRealFrontdeskScenarioAttachment: true,
    requiresFollowupPatientServiceConsumerSliceAttachment: true,
  },
  {
    file: "service-organization-frontdesk.spec.ts",
    titleIncludes: "平台开通服务机构后，新机构可完成组织树、科室账号、职责范围和登录画像闭环",
    claims: serviceOrganizationClaims,
    requiresServiceOrganizationScenarioAttachment: true,
  },
  {
    file: "mfa-login-frontdesk.spec.ts",
    titleIncludes: "开启 MFA 后已绑定账号必须在登录页完成真实 TOTP 验证",
    claims: mfaLoginClaims,
    requiresMfaLoginScenarioAttachment: true,
  },
  {
    file: "diagnosis-knowledge-maintenance.spec.ts",
    titleIncludes: "运营员从前台创建证据完整诊断资产并登记标准与验证病例",
    claims: diagnosisKnowledgeClaims,
    requiresDiagnosisKnowledgeScenarioAttachment: true,
  },
  {
    file: "domain-facade-b0-evidence.spec.ts",
    titleIncludes: "运营员从前台回读全专业领域门面 B0 复用链路证据",
    claims: domainFacadeB0Claims,
    requiresDomainFacadeB0EvidenceAttachment: true,
  },
  {
    file: "d6-graph-explore.spec.ts",
    titleIncludes: "医疗引擎运营员可重建并探索真实知识投影",
    claims: sourceLineageClaims,
    requiresSourceLineageAttachment: true,
  },
  {
    file: "embed-business-host.spec.ts",
    titleIncludes: "独立业务系统宿主通过真实嵌入凭证完成 iframe 启动并接收医师反馈",
    claims: embedBusinessHostClaims,
    requiresEmbedBusinessHostAttachment: true,
  },
  {
    file: "pathway-lifecycle-frontdesk.spec.ts",
    titleIncludes:
      "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片",
    claims: [...pathwayLifecycleClaims, ...pathwayLifecycleSpecialDiseaseStageClaims],
    requiresPathwayLifecycleAttachment: true,
  },
  {
    file: "system-providers-frontdesk.spec.ts",
    titleIncludes: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
    claims: systemProvidersClaims,
    requiresSystemProvidersAttachment: true,
  },
  {
    file: "identity-binding-frontdesk.spec.ts",
    titleIncludes: "平台管理员可前台绑定和解绑院内身份来源且身份原文不落库",
    claims: identityBindingClaims,
    requiresIdentityBindingAttachment: true,
  },
  {
    file: "four-role-core-actions-rehearsal.spec.ts",
    titleIncludes: "四职责主动作均完成真实前台操作与服务回读闭环",
    claims: fourRoleCoreActionsClaims,
    requiresFourRoleCoreActionsAttachment: true,
  },
  {
    file: "entry-core-actions-rehearsal.spec.ts",
    titleIncludes: "七个路由覆盖六类入口族完成真实前台核心动作代表闭环",
    claims: sixEntryCoreActionsClaims,
    requiresSixEntryCoreActionsAttachment: true,
  },
];

export function buildBrowserE2eLaunchEvidence(input: {
  stats: BrowserE2eRunStats;
  tests: BrowserE2eTestResult[];
  generatedAt?: string;
}): BrowserE2eLaunchEvidence {
  const generatedAt = input.generatedAt ?? new Date().toISOString();
  const evidence: BrowserE2eLaunchEvidence = {
    schemaVersion: "1.0.0",
    stage: "BROWSER_E2E",
    status: input.stats.unexpected === 0 && input.stats.flaky === 0 ? "PASSED" : "FAILED",
    generatedAt,
    stats: input.stats,
    tests: input.tests,
    launchCoverage: {},
  };
  if (evidence.status !== "PASSED") return evidence;

  for (const proof of coverageProofs) {
    if (hasPassingProof(input.tests, proof)) {
      mergeClaims(evidence.launchCoverage, proof.claims, generatedAt);
    }
  }
  if (hasCompleteStandardPatientResourceConsumerMatrix(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      [
        ...standardPatientResourceConsumerMatrixClaims,
        ...standardPatientResourceRepresentativeRowClaims,
      ],
      generatedAt,
    );
  }
  if (hasRequiredPlatformAdminEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(evidence.launchCoverage, platformAdminEntryCoreActionsClaims, generatedAt);
  }
  if (hasRequiredPlatformAdminP1EntryCoreActionsAttachment(input.tests)) {
    mergeClaims(evidence.launchCoverage, platformAdminP1EntryCoreActionsClaims, generatedAt);
  }
  if (hasRequiredLaunchReadinessStakeholderAttachment(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      [
        ...launchReadinessStakeholderMatrixClaims,
        ...requiredLaunchReadinessStakeholders.map(
          (stakeholder) => `launchReadinessStakeholderRows:${stakeholder.row}`,
        ),
      ],
      generatedAt,
    );
  }
  if (hasRequiredImplementationGuideEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      [
        ...implementationGuideEntryCoreActionsClaims,
        ...implementationGuideEntryCoreActionRowClaims,
      ],
      generatedAt,
    );
  }
  if (hasRequiredDashboardWorkbenchCoreActionsAttachment(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      [
        ...dashboardWorkbenchCoreActionsClaims,
        ...requiredDashboardWorkbenchRows.map(
          (row) => `dashboardWorkbenchCoreActionRows:${row.row}`,
        ),
      ],
      generatedAt,
    );
  }
  const roleScopeFrontdeskActionRepresentativeSliceClaims =
    collectRoleScopeFrontdeskActionRepresentativeSliceClaims(input.tests);
  if (roleScopeFrontdeskActionRepresentativeSliceClaims.length > 0) {
    mergeClaims(
      evidence.launchCoverage,
      roleScopeFrontdeskActionRepresentativeSliceClaims,
      generatedAt,
    );
  }
  if (hasRequiredComplianceWorkbenchPersonalEntryEvidence(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      [
        ...complianceWorkbenchPersonalEntryMatrixClaims,
        ...requiredComplianceWorkbenchPersonalEntryRows.map(
          (entry) => `complianceWorkbenchPersonalEntryRows:${entry.row}`,
        ),
      ],
      generatedAt,
    );
  }
  if (hasRequiredClinicalEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(evidence.launchCoverage, clinicalEntryCoreActionsClaims, generatedAt);
  }
  if (hasRequiredQualityManagementEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(evidence.launchCoverage, qualityManagementEntryCoreActionsClaims, generatedAt);
  }
  if (hasRequiredMedicalRecordInsurancePaymentConsumerSliceAttachment(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      medicalRecordInsurancePaymentConsumerSliceClaims,
      generatedAt,
    );
  }
  if (hasRequiredKnowledgeOperationsAssetEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      knowledgeOperationsAssetEntryCoreActionsClaims,
      generatedAt,
    );
  }
  if (hasRequiredMenuEntryCoreActionEvidence(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      [
        ...menuEntryCoreActionsClaims,
        ...requiredMenuEntryCoreActionRows.map((row) => `menuEntryCoreActionRows:${row}`),
      ],
      generatedAt,
    );
  }
  if (hasRequiredKnowledgeSupplyChainEvidenceAttachment(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      [
        ...knowledgeSupplyChainEvidenceMatrixClaims,
        ...requiredKnowledgeSupplyChainEvidenceRows.map(
          (row) => `knowledgeSupplyChainEvidenceRows:${row}`,
        ),
      ],
      generatedAt,
    );
  }
  const versionedAssetSupplyChainClaims = collectVersionedAssetSupplyChainMatrixClaims(input.tests);
  if (versionedAssetSupplyChainClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, versionedAssetSupplyChainClaims, generatedAt);
  }
  const versionedAssetRollbackClaims = collectVersionedAssetRollbackRepresentativeClaims(
    input.tests,
  );
  if (versionedAssetRollbackClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, versionedAssetRollbackClaims, generatedAt);
  }
  const versionedAssetDedicatedReleaseContractClaims =
    collectVersionedAssetDedicatedReleaseContractClaims(input.tests);
  if (versionedAssetDedicatedReleaseContractClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, versionedAssetDedicatedReleaseContractClaims, generatedAt);
  }
  const thirdPartyDegradationClaims = collectThirdPartySystemFamilyDegradationClaims(input.tests);
  if (thirdPartyDegradationClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, thirdPartyDegradationClaims, generatedAt);
  }
  const thirdPartyScenarioConditionClaims = collectThirdPartySystemFamilyScenarioConditionClaims(
    input.tests,
  );
  if (thirdPartyScenarioConditionClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, thirdPartyScenarioConditionClaims, generatedAt);
  }
  const multiHospitalRuntimeIsolationClaims = collectMultiHospitalRuntimeIsolationClaims(
    input.tests,
  );
  if (multiHospitalRuntimeIsolationClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, multiHospitalRuntimeIsolationClaims, generatedAt);
  }
  const s2s4ScenarioConditionClaims = collectS2S4ScenarioConditionClaims(input.tests);
  if (s2s4ScenarioConditionClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, s2s4ScenarioConditionClaims, generatedAt);
  }
  const frontdeskScenarioConditionClaims = collectFrontdeskScenarioConditionClaims(input.tests);
  if (frontdeskScenarioConditionClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, frontdeskScenarioConditionClaims, generatedAt);
  }
  const systemProvidersScenarioConditionClaims = collectSystemProvidersScenarioConditionClaims(
    input.tests,
  );
  if (systemProvidersScenarioConditionClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, systemProvidersScenarioConditionClaims, generatedAt);
  }
  const platformAdminP1ScenarioConditionClaims = collectPlatformAdminP1ScenarioConditionClaims(
    input.tests,
  );
  if (platformAdminP1ScenarioConditionClaims.length > 0) {
    mergeClaims(evidence.launchCoverage, platformAdminP1ScenarioConditionClaims, generatedAt);
  }
  return evidence;
}

function hasPassingProof(tests: BrowserE2eTestResult[], proof: CoverageProof) {
  return tests.some((test) => {
    const fileName = path.basename(test.file);
    return (
      fileName === proof.file &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      (!proof.titleIncludes || test.title.includes(proof.titleIncludes)) &&
      (!proof.requiresSystemFamilyAttachment || hasRequiredSystemFamilyAttachment(test)) &&
      (!proof.requiresRealFrontdeskScenarioAttachment ||
        hasRequiredRealFrontdeskScenarioAttachment(test)) &&
      (!proof.requiresServiceOrganizationScenarioAttachment ||
        hasRequiredServiceOrganizationScenarioAttachment(test)) &&
      (!proof.requiresMfaLoginScenarioAttachment || hasRequiredMfaLoginScenarioAttachment(test)) &&
      (!proof.requiresDiagnosisKnowledgeScenarioAttachment ||
        hasRequiredDiagnosisKnowledgeScenarioAttachment(test)) &&
      (!proof.requiresRuntimeReleaseAttachment || hasRequiredRuntimeReleaseAttachment(test)) &&
      (!proof.requiresSourceLineageAttachment || hasRequiredSourceLineageAttachment(test)) &&
      (!proof.requiresEmbedBusinessHostAttachment ||
        hasRequiredEmbedBusinessHostAttachment(test)) &&
      (!proof.requiresPathwayLifecycleAttachment || hasRequiredPathwayLifecycleAttachment(test)) &&
      (!proof.requiresSystemProvidersAttachment || hasRequiredSystemProvidersAttachment(test)) &&
      (!proof.requiresIdentityBindingAttachment || hasRequiredIdentityBindingAttachment(test)) &&
      (!proof.requiresS2S4RuntimeMappingAttachment ||
        hasRequiredS2S4RuntimeMappingAttachment(test)) &&
      (!proof.requiresCdssDeclarativeRuntimeAssetAttachment ||
        hasRequiredCdssDeclarativeRuntimeAssetAttachment(test)) &&
      (!proof.requiresMedicationSafetyFrontdeskAttachment ||
        hasRequiredMedicationSafetyFrontdeskAttachment(test)) &&
      (!proof.requiresDiagnosticCriticalValueFrontdeskAttachment ||
        hasRequiredDiagnosticCriticalValueFrontdeskAttachment(test)) &&
      (!proof.requiresDiagnosticFamilyConsumerSliceAttachment ||
        hasRequiredDiagnosticFamilyConsumerSliceAttachment(test)) &&
      (!proof.requiresDiagnosticReportFamilyMatrixAttachment ||
        hasRequiredDiagnosticReportFamilyMatrixAttachment(test)) &&
      (!proof.requiresRegionalDiagnosticMutualRecognitionFrontdeskAttachment ||
        hasRequiredRegionalDiagnosticMutualRecognitionFrontdeskAttachment(test)) &&
      (!proof.requiresRegionalRemoteConsumerSliceAttachment ||
        hasRequiredRegionalRemoteConsumerSliceAttachment(test)) &&
      (!proof.requiresNursingContinuityFrontdeskAttachment ||
        hasRequiredNursingContinuityFrontdeskAttachment(test)) &&
      (!proof.requiresPharmacyReviewAntimicrobialFrontdeskAttachment ||
        hasRequiredPharmacyReviewAntimicrobialFrontdeskAttachment(test)) &&
      (!proof.requiresPharmacyReviewConsumerSliceAttachment ||
        hasRequiredPharmacyReviewConsumerSliceAttachment(test)) &&
      (!proof.requiresInfectionPublicHealthSafetyFrontdeskAttachment ||
        hasRequiredInfectionPublicHealthSafetyFrontdeskAttachment(test)) &&
      (!proof.requiresPublicHealthInfectionRegulatoryConsumerSliceAttachment ||
        hasRequiredPublicHealthInfectionRegulatoryConsumerSliceAttachment(test)) &&
      (!proof.requiresSurgeryAnesthesiaTransfusionFrontdeskAttachment ||
        hasRequiredSurgeryAnesthesiaTransfusionFrontdeskAttachment(test)) &&
      (!proof.requiresSurgeryAnesthesiaTransfusionConsumerSliceAttachment ||
        hasRequiredSurgeryAnesthesiaTransfusionConsumerSliceAttachment(test)) &&
      (!proof.requiresCriticalEmergencyIcuFrontdeskAttachment ||
        hasRequiredCriticalEmergencyIcuFrontdeskAttachment(test)) &&
      (!proof.requiresLisMonitoringCriticalConsumerSliceAttachment ||
        hasRequiredLisMonitoringCriticalConsumerSliceAttachment(test)) &&
      (!proof.requiresReportInterpretationScenarioAttachment ||
        hasRequiredReportInterpretationScenarioAttachment(test)) &&
      (!proof.requiresRuntimeReleasePartialSelectionAttachment ||
        hasRequiredRuntimeReleasePartialSelectionAttachment(test)) &&
      (!proof.requiresFourRoleCoreActionsAttachment ||
        hasRequiredFourRoleCoreActionsAttachment(test)) &&
      (!proof.requiresSixEntryCoreActionsAttachment ||
        hasRequiredSixEntryCoreActionsAttachment(test)) &&
      (!proof.requiresPlatformAdminEntryCoreActionsAttachment ||
        hasRequiredPlatformAdminEntryCoreActionsAttachment([test])) &&
      (!proof.requiresDomainFacadeB0EvidenceAttachment ||
        hasRequiredDomainFacadeB0EvidenceAttachment(test)) &&
      (!proof.requiresFollowupPatientServiceConsumerSliceAttachment ||
        hasRequiredFollowupPatientServiceConsumerSliceAttachment(test)) &&
      (!proof.requiresHisEmrCdrConsumerSliceAttachment ||
        hasRequiredHisEmrCdrConsumerSliceAttachment(test))
    );
  });
}

function hasCompleteStandardPatientResourceConsumerMatrix(tests: BrowserE2eTestResult[]) {
  const observedResources = new Map<string, { file: string; row: Record<string, unknown> }>();
  for (const test of tests) {
    if (
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    const fileName = path.basename(test.file);
    const expectedAttachmentName = standardPatientResourceMatrixAttachmentNames[fileName];
    if (!expectedAttachmentName) continue;
    for (const attachment of test.attachments) {
      if (
        attachment.name !== expectedAttachmentName ||
        !attachment.body ||
        attachment.contentType !== "application/json"
      ) {
        continue;
      }
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const matrix = recordValue(recordValue(parsed)?.standardPatientResourceConsumerMatrix);
      if (!matrix) continue;
      if (!hasValidStandardPatientResourceMatrixHeader(matrix)) return false;
      const rows = Array.isArray(matrix.resources) ? matrix.resources : [];
      for (const item of rows) {
        const row = recordValue(item);
        if (!row) return false;
        const resourceType = textValue(row.resourceType);
        if (
          !resourceType ||
          !requiredStandardPatientResourceTypes.includes(resourceType) ||
          observedResources.has(resourceType) ||
          !hasCompleteStandardPatientResourceRow(parsed, row, resourceType)
        ) {
          return false;
        }
        observedResources.set(resourceType, { file: test.file, row });
      }
    }
  }
  return requiredStandardPatientResourceTypes.every((type) => observedResources.has(type));
}

function collectVersionedAssetSupplyChainMatrixClaims(tests: BrowserE2eTestResult[]) {
  const hasRuntimeRelease = hasPassingAttachmentTest(
    tests,
    "runtime-release-frontdesk.spec.ts",
    (test) => hasRequiredRuntimeReleaseAttachment(test),
  );
  const hasKnowledgeOperations =
    hasRequiredKnowledgeOperationsAssetEntryCoreActionsAttachment(tests);
  if (!hasRuntimeRelease || !hasKnowledgeOperations) return [];

  const representativeAssets = new Set<string>();
  const knownGaps = new Set<string>();

  if (
    hasPassingAttachmentTest(tests, "s2-s4-terminology-integration-rehearsal.spec.ts", (test) =>
      hasRequiredS2S4RuntimeMappingAttachment(test),
    )
  ) {
    representativeAssets.add("TERMINOLOGY");
  }
  if (
    hasPassingAttachmentTest(tests, "cdss-runtime-declarative-assets.spec.ts", (test) =>
      hasRequiredCdssDeclarativeRuntimeAssetAttachment(test),
    )
  ) {
    ["VALUE_SET", "FORMULA", "ACTION_CARD"].forEach((asset) => representativeAssets.add(asset));
  }
  if (
    hasPassingAttachmentTest(tests, "medication-safety-frontdesk.spec.ts", (test) =>
      hasRequiredMedicationSafetyFrontdeskAttachment(test),
    )
  ) {
    ["SAFETY", "CDSS_RISK", "RULE"].forEach((asset) => representativeAssets.add(asset));
  }
  if (
    hasPassingAttachmentTest(tests, "diagnostic-critical-value-frontdesk.spec.ts", (test) =>
      hasRequiredDiagnosticCriticalValueFrontdeskAttachment(test),
    )
  ) {
    ["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"].forEach((asset) =>
      representativeAssets.add(asset),
    );
  }
  if (
    hasPassingAttachmentTest(tests, "nursing-continuity-frontdesk.spec.ts", (test) =>
      hasRequiredNursingContinuityFrontdeskAttachment(test),
    )
  ) {
    representativeAssets.add("FOLLOWUP");
  }
  if (
    hasPassingAttachmentTest(tests, "critical-emergency-icu-frontdesk.spec.ts", (test) =>
      hasRequiredCriticalEmergencyIcuFrontdeskAttachment(test),
    )
  ) {
    ["TERMINOLOGY", "CDSS_RISK", "RULE", "PATHWAY", "ACTION_CARD"].forEach((asset) =>
      representativeAssets.add(asset),
    );
  }
  if (
    hasPassingAttachmentTest(tests, "pathway-lifecycle-frontdesk.spec.ts", (test) =>
      hasRequiredPathwayLifecycleAttachment(test),
    )
  ) {
    representativeAssets.add("ORDER_SET");
  }
  if (hasCompleteEvaluationAssetSupplyChainEvidence(tests)) {
    representativeAssets.add("EVALUATION");
  } else if (hasRequiredQualityManagementEntryCoreActionsAttachment(tests)) {
    knownGaps.add("EVALUATION");
  }

  for (const asset of representativeAssets) {
    knownGaps.delete(asset);
  }
  if (
    !requiredRuntimeReleaseVersionedAssets.every(
      (asset) => representativeAssets.has(asset) || knownGaps.has(asset),
    )
  ) {
    return [];
  }
  return [
    ...versionedAssetSupplyChainMatrixClaims,
    ...requiredRuntimeReleaseVersionedAssets
      .filter((asset) => representativeAssets.has(asset))
      .map((asset) => `versionedAssetRepresentativeRows:${asset}`),
    ...requiredRuntimeReleaseVersionedAssets
      .filter((asset) => knownGaps.has(asset))
      .map((asset) => `versionedAssetKnownGaps:${asset}`),
  ];
}

function collectVersionedAssetRollbackRepresentativeClaims(tests: BrowserE2eTestResult[]) {
  const representativeAssets = new Set<string>();

  collectRollbackNegativeAssetsFromAttachment(
    tests,
    "cdss-runtime-declarative-assets.spec.ts",
    "cdss-runtime-declarative-assets-codes",
  )
    .filter((asset) => ["VALUE_SET", "FORMULA"].includes(asset))
    .forEach((asset) => representativeAssets.add(asset));
  collectRollbackNegativeAssetsFromAttachment(
    tests,
    "medication-safety-frontdesk.spec.ts",
    "medication-safety-frontdesk-codes",
  )
    .filter((asset) => ["SAFETY", "CDSS_RISK"].includes(asset))
    .forEach((asset) => representativeAssets.add(asset));
  collectRollbackNegativeAssetsFromAttachment(
    tests,
    "critical-emergency-icu-frontdesk.spec.ts",
    "critical-emergency-icu-frontdesk-codes",
  )
    .filter((asset) => asset === "PATHWAY")
    .forEach((asset) => representativeAssets.add(asset));
  collectRollbackNegativeAssetsFromAttachment(
    tests,
    "pathway-lifecycle-frontdesk.spec.ts",
    "pathway-lifecycle-scenario-codes",
  )
    .filter((asset) => asset === "ORDER_SET")
    .forEach((asset) => representativeAssets.add(asset));
  collectRollbackNegativeAssetsFromAttachment(
    tests,
    "quality-management-entry-core-actions-rehearsal.spec.ts",
    "quality-management-entry-core-actions-codes",
  )
    .filter((asset) => asset === "EVALUATION")
    .forEach((asset) => representativeAssets.add(asset));

  if (
    !requiredVersionedAssetRollbackRepresentativeAssets.every((asset) =>
      representativeAssets.has(asset),
    )
  ) {
    return [];
  }
  return [
    ...versionedAssetRollbackRepresentativeMatrixClaims,
    ...requiredVersionedAssetRollbackRepresentativeAssets.map(
      (asset) => `versionedAssetRollbackRepresentativeRows:${asset}`,
    ),
  ];
}

function collectVersionedAssetDedicatedReleaseContractClaims(tests: BrowserE2eTestResult[]) {
  const representativeAssets = new Set<string>();
  collectDedicatedReleaseContractAssetsFromAttachment(
    tests,
    "s2-s4-terminology-integration-rehearsal.spec.ts",
    "s2-s4-runtime-mapping-codes",
    hasRequiredS2S4RuntimeMappingAttachment,
  )
    .filter((asset) => asset === "TERMINOLOGY")
    .forEach((asset) => representativeAssets.add(asset));
  collectDedicatedReleaseContractAssetsFromAttachment(
    tests,
    "diagnostic-critical-value-frontdesk.spec.ts",
    "diagnostic-critical-value-frontdesk-codes",
    hasRequiredDiagnosticCriticalValueFrontdeskAttachment,
  )
    .filter((asset) => asset === "FIELD_CATALOG")
    .forEach((asset) => representativeAssets.add(asset));
  collectDedicatedReleaseContractAssetsFromAttachment(
    tests,
    "pathway-lifecycle-frontdesk.spec.ts",
    "pathway-lifecycle-scenario-codes",
    hasRequiredPathwayLifecycleAttachment,
  )
    .filter((asset) => asset === "PATHWAY")
    .forEach((asset) => representativeAssets.add(asset));
  if (
    !requiredVersionedAssetDedicatedReleaseContractAssets.every((asset) =>
      representativeAssets.has(asset),
    )
  ) {
    return [];
  }
  return [
    ...versionedAssetDedicatedReleaseContractMatrixClaims,
    ...requiredVersionedAssetDedicatedReleaseContractAssets.map(
      (asset) => `versionedAssetDedicatedReleaseContractRows:${asset}`,
    ),
  ];
}

function collectDedicatedReleaseContractAssetsFromAttachment(
  tests: BrowserE2eTestResult[],
  fileName: string,
  attachmentName: string,
  attachmentValidator: (test: BrowserE2eTestResult) => boolean,
) {
  const assets = new Set<string>();
  for (const test of tests) {
    if (
      path.basename(test.file) !== fileName ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments) ||
      !attachmentValidator(test)
    ) {
      continue;
    }
    const attachment = test.attachments.find((item) => item.name === attachmentName);
    if (!attachment?.body) continue;
    try {
      const parsed = JSON.parse(attachment.body);
      const evidence = parseDedicatedReleaseContractEvidence(parsed);
      if (evidence) assets.add(evidence.assetType);
    } catch {
      continue;
    }
  }
  return Array.from(assets);
}

function parseDedicatedReleaseContractEvidence(parsed: unknown) {
  const body = recordValue(parsed);
  const evidence = recordValue(body?.dedicatedReleaseContractEvidence);
  if (
    !body ||
    !evidence ||
    !hasText(evidence.assetType) ||
    !hasText(evidence.assetIdentity) ||
    !hasText(evidence.versionId) ||
    !hasText(evidence.productionRoute) ||
    !hasText(evidence.releaseContract) ||
    !hasText(evidence.consumer) ||
    evidence.runtimeConsumerReadbackVerified !== true
  ) {
    return null;
  }
  const asset = {
    assetType: String(evidence.assetType),
    assetIdentity: String(evidence.assetIdentity),
    versionId: String(evidence.versionId),
  };
  if (!requiredVersionedAssetDedicatedReleaseContractAssets.includes(asset.assetType)) return null;
  if (
    asset.assetType === "TERMINOLOGY" &&
    hasCompleteTerminologyDedicatedReleaseContract(body, evidence, asset)
  ) {
    return asset;
  }
  if (
    asset.assetType === "FIELD_CATALOG" &&
    hasCompleteFieldCatalogDedicatedReleaseContract(body, evidence, asset)
  ) {
    return asset;
  }
  if (
    asset.assetType === "PATHWAY" &&
    hasCompletePathwayDedicatedReleaseContract(body, evidence, asset)
  ) {
    return asset;
  }
  return null;
}

function hasCompleteTerminologyDedicatedReleaseContract(
  body: Record<string, unknown>,
  evidence: Record<string, unknown>,
  asset: { assetType: string; assetIdentity: string; versionId: string },
) {
  const terminology = recordValue(body.terminology);
  const inboundResult = recordValue(body.inboundResult);
  return (
    evidence.productionRoute === "STANDARD_AND_LOCAL_TERMINOLOGY_MAPPING" &&
    evidence.releaseContract === "S2_S4_TERMINOLOGY_MAPPING_RUNTIME_CONTRACT" &&
    evidence.producerVerified === true &&
    evidence.reviewerVerified === true &&
    evidence.activationVerified === true &&
    evidence.inboundNormalizationVerified === true &&
    arrayEquals(evidence.sourceSystems, ["LIS"]) &&
    evidence.consumer === "SIGNED_WEBHOOK_INBOUND_NORMALIZATION" &&
    terminology?.assetType === asset.assetType &&
    terminology.assetIdentity === asset.assetIdentity &&
    terminology.versionId === asset.versionId &&
    runtimeReleasePayloadContainsCandidate(body.activationRequest, "activeAssets", asset) &&
    runtimeReleasePayloadContainsCandidate(body.runtime, "assets", asset, {
      requireActive: true,
    }) &&
    runtimeReleasePayloadContainsCandidate(body.runtimeConsumerReadback, "assets", asset, {
      requireActive: true,
    }) &&
    inboundResult?.status === "SUCCESS" &&
    inboundResult.normalizedCodeCount === 1 &&
    String(
      valueAtEvidencePath(body, "inboundResult.mappedPayload.observations[0].runtimeReleaseId") ??
        "",
    ) === textValue(recordValue(body.runtime)?.releaseId) &&
    valueAtEvidencePath(body, "inboundResult.mappedPayload.observations[0].mappingId") ===
      terminology.mappingId
  );
}

function hasCompleteFieldCatalogDedicatedReleaseContract(
  body: Record<string, unknown>,
  evidence: Record<string, unknown>,
  asset: { assetType: string; assetIdentity: string; versionId: string },
) {
  const runtime = recordValue(body.runtime);
  const fieldCatalogAsset = recordValue(runtime?.fieldCatalogAsset);
  return (
    evidence.productionRoute === "DIAGNOSTIC_FIELD_CATALOG_RUNTIME_BASELINE" &&
    evidence.releaseContract === "DIAGNOSTIC_REPORT_INTERPRETATION_FIELD_CONTRACT" &&
    evidence.platformBaselineVerified === true &&
    evidence.activationVerified === true &&
    evidence.reportInterpretationVerified === true &&
    evidence.consumer === "REPORT_INTERPRETATION" &&
    evidencePathsResolve(body, evidence.fieldEvidencePaths) &&
    fieldCatalogAsset?.assetType === asset.assetType &&
    fieldCatalogAsset.assetIdentity === asset.assetIdentity &&
    fieldCatalogAsset.versionId === asset.versionId &&
    runtimeReleasePayloadContainsPlatformSelection(body.activationRequest, "activeAssets", asset) &&
    runtimeReleasePayloadContainsCandidate(runtime, "assets", asset, { requireActive: true }) &&
    diagnosticCriticalValueRuntimeAssetEvidenceMatches(
      Array.isArray(valueAtEvidencePath(body, "recommendation.explanation.runtimeAssetEvidence"))
        ? (valueAtEvidencePath(
            body,
            "recommendation.explanation.runtimeAssetEvidence",
          ) as unknown[])
        : [],
      {
        assetType: "FIELD_CATALOG",
        assetIdentity: asset.assetIdentity,
        versionId: asset.versionId,
        versionNo: String(fieldCatalogAsset.versionNo),
        contentHash: String(fieldCatalogAsset.contentHash),
        sourceLayer: "PLATFORM",
      },
    )
  );
}

function hasCompletePathwayDedicatedReleaseContract(
  body: Record<string, unknown>,
  evidence: Record<string, unknown>,
  asset: { assetType: string; assetIdentity: string; versionId: string },
) {
  const context = recordValue(body.context);
  const orderSetConsumer = recordValue(body.orderSetRuntimeConsumer);
  const rollbackEvidence = recordValue(body.rollbackNegativeEvidence);
  const removedAssets = Array.isArray(rollbackEvidence?.removedAssets)
    ? rollbackEvidence.removedAssets
    : [];
  const removedPathwayAsset = removedAssets
    .map((item) => parseRuntimeReleaseCandidate(item))
    .find((item) => item?.assetType === "PATHWAY");
  const rollbackAssets = parseRollbackNegativeEvidence(body.rollbackNegativeEvidence);
  return (
    evidence.productionRoute === "SPECIAL_DISEASE_PATHWAY_TEMPLATE_LIFECYCLE" &&
    evidence.releaseContract === "SPECIAL_DISEASE_PATHWAY_ENTRY_AND_ADVANCE_CONTRACT" &&
    evidence.templateLifecycleVerified === true &&
    evidence.activationVerified === true &&
    evidence.pathwayEntryVerified === true &&
    evidence.pathwayAdvanceVerified === true &&
    evidence.orderSetConsumerVerified === true &&
    evidence.consumer === "SPECIAL_DISEASE_PATHWAY" &&
    context?.templateCode === asset.assetIdentity &&
    hasText(context.patientPathwayId) &&
    rollbackAssets.includes("PATHWAY") &&
    removedPathwayAsset?.assetIdentity === asset.assetIdentity &&
    removedPathwayAsset.versionId === asset.versionId &&
    orderSetConsumer !== null &&
    hasText(recordValue(orderSetConsumer.asset)?.assetIdentity) &&
    hasText(recordValue(orderSetConsumer.asset)?.versionId) &&
    recordValue(orderSetConsumer.patientPathway)?.patientPathwayId === context.patientPathwayId &&
    recordValue(orderSetConsumer.patientPathway)?.runtimeReleaseId ===
      recordValue(orderSetConsumer.runtimeRelease)?.releaseId &&
    recordValue(orderSetConsumer.runtimeRelease)?.assetPresent === true &&
    recordValue(orderSetConsumer.advanceResponse)?.status === "NODE_EXECUTING" &&
    recordValue(recordValue(orderSetConsumer.advanceResponse)?.decisionEvidence)?.[
      "pathway.orderSetRequiresPhysicianConfirmation"
    ] === true
  );
}

function collectRollbackNegativeAssetsFromAttachment(
  tests: BrowserE2eTestResult[],
  fileName: string,
  attachmentName: string,
) {
  const assets = new Set<string>();
  for (const test of tests) {
    if (
      path.basename(test.file) !== fileName ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    const attachment = test.attachments.find((item) => item.name === attachmentName);
    if (!attachment?.body) continue;
    try {
      const parsed = JSON.parse(attachment.body) as { rollbackNegativeEvidence?: unknown };
      parseRollbackNegativeEvidence(parsed.rollbackNegativeEvidence).forEach((asset) =>
        assets.add(asset),
      );
    } catch {
      continue;
    }
  }
  return Array.from(assets);
}

function hasPassingAttachmentTest(
  tests: BrowserE2eTestResult[],
  fileName: string,
  validator: (test: BrowserE2eTestResult) => boolean,
) {
  return tests.some(
    (test) =>
      path.basename(test.file) === fileName &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      Array.isArray(test.attachments) &&
      validator(test),
  );
}

function hasValidStandardPatientResourceMatrixHeader(matrix: Record<string, unknown>) {
  const scope = textValue(matrix.scopeStatement);
  return (
    matrix.matrixCode === "THIRTEEN_STANDARD_RESOURCES_REPRESENTATIVE" &&
    Boolean(scope) &&
    String(scope).includes("代表矩阵") &&
    String(scope).includes("不代表每类字段目录全量落地") &&
    String(scope).includes("不代表完整 S0-S40") &&
    String(scope).includes("不代表完整上线验收") &&
    !hasUnnegatedStandardPatientResourceScopeClaim(String(scope))
  );
}

function hasCompleteStandardPatientResourceRow(
  attachmentBody: unknown,
  row: Record<string, unknown>,
  resourceType: string,
) {
  const resourcePath = textValue(row.resourcePath);
  const sourceSystem = textValue(row.sourceSystem);
  const sourceId = textValue(row.sourceId);
  const sourceIdPath = textValue(row.sourceIdPath);
  const consumer = textValue(row.consumer);
  if (!resourcePath || !sourceSystem || (!sourceId && !sourceIdPath) || !consumer) return false;
  if (!resourcePathMatchesStandardPatientResourceType(resourcePath, resourceType)) return false;
  const resource = recordValue(valueAtEvidencePath(attachmentBody, resourcePath));
  if (!resource) return false;
  const resolvedSourceId = sourceIdPath
    ? textValue(valueAtEvidencePath(attachmentBody, sourceIdPath))
    : sourceId;
  if (!resolvedSourceId) return false;
  if (
    !resourceMatchesStandardPatientResourceRow(
      resource,
      resourceType,
      sourceSystem,
      resolvedSourceId,
    )
  ) {
    return false;
  }
  if (
    row.patientVerified !== true ||
    row.snapshotReadbackVerified !== true ||
    row.consumerVerified !== true ||
    row.auditVerified !== true ||
    row.dataQualityVerified !== true
  ) {
    return false;
  }
  if (resourceType !== "Patient" && row.encounterVerified !== true) return false;
  if (!evidencePathsResolve(attachmentBody, row.consumerEvidencePaths)) return false;
  if (!evidencePathsResolve(attachmentBody, row.auditEvidencePaths)) return false;
  if (resourceType === "Claim") {
    return (
      consumer === "INSURANCE_AUDIT" &&
      row.evaluationRunVerified === true &&
      row.qualityRectificationVerified === true &&
      hasText(valueAtEvidencePath(attachmentBody, "insuranceAudit.evaluationRunId")) &&
      hasText(valueAtEvidencePath(attachmentBody, "insuranceAudit.issueId")) &&
      hasText(valueAtEvidencePath(attachmentBody, "qualityRectification.taskId"))
    );
  }
  return true;
}

function resourcePathMatchesStandardPatientResourceType(
  resourcePath: string,
  resourceType: string,
) {
  const prefix = standardPatientResourcePathPrefixes[resourceType];
  return Boolean(prefix) && resourcePath.startsWith(prefix);
}

function resourceMatchesStandardPatientResourceRow(
  resource: Record<string, unknown>,
  resourceType: string,
  sourceSystem: string,
  sourceId: string,
) {
  const candidateIds = [
    textValue(resource.sourceRecordId),
    textValue(resource.sourceId),
    textValue(resource.fhirId),
    textValue(resource.mpi),
    textValue(resource.encounterId),
    textValue(resource.observationId),
    textValue(resource.reportId),
    textValue(resource.medicationId),
    textValue(resource.allergyIntoleranceId),
    textValue(resource.conditionId),
    textValue(resource.assessmentId),
    textValue(resource.procedureId),
    textValue(resource.documentId),
    textValue(resource.planId),
    textValue(resource.followUpId),
    textValue(resource.claimId),
  ].filter((value): value is string => Boolean(value));
  return (
    resource.sourceSystem === sourceSystem &&
    resource.qualityStatus === "VALID" &&
    candidateIds.includes(sourceId) &&
    resourceHasStandardPatientResourceShape(resource, resourceType)
  );
}

function resourceHasStandardPatientResourceShape(
  resource: Record<string, unknown>,
  resourceType: string,
) {
  switch (resourceType) {
    case "Patient":
      return hasText(resource.mpi) && hasText(resource.name);
    case "AllergyIntolerance":
      return hasText(resource.allergyIntoleranceId) || hasText(resource.code);
    case "Encounter":
      return hasText(resource.encounterId) && hasText(resource.encounterType);
    case "Condition":
      return hasText(resource.conditionId) || hasText(resource.code);
    case "NursingAssessment":
      return hasText(resource.assessmentId) || hasText(resource.assessmentType);
    case "Observation":
      return hasText(resource.observationId) || hasText(resource.code);
    case "DiagnosticReport":
      return hasText(resource.reportId) || hasText(resource.reportType);
    case "Medication":
      return (
        hasText(resource.medicationId) || hasText(resource.code) || hasText(resource.standardCode)
      );
    case "Procedure":
      return hasText(resource.procedureId) || hasText(resource.code);
    case "Document":
      return hasText(resource.documentId) || hasText(resource.documentType);
    case "CarePlan":
      return hasText(resource.planId) || hasText(resource.planType);
    case "FollowUp":
      return (
        hasText(resource.followUpId) ||
        hasText(resource.planId) ||
        hasText(resource.questionnaireId)
      );
    case "Claim":
      return hasText(resource.claimId) && hasText(resource.drgCode);
    default:
      return false;
  }
}

function evidencePathsResolve(root: unknown, paths: unknown) {
  if (!Array.isArray(paths)) return false;
  const resolvedPaths = paths.filter((path): path is string => hasText(path));
  return (
    resolvedPaths.length > 0 &&
    resolvedPaths.every((path) => {
      const value = valueAtEvidencePath(root, path);
      if (Array.isArray(value)) return value.length > 0;
      if (typeof value === "string") return value.trim().length > 0;
      return value !== null && value !== undefined;
    })
  );
}

function valueAtEvidencePath(root: unknown, pathExpression: string) {
  let current = root;
  for (const segment of pathExpression.split(".")) {
    if (!segment) return undefined;
    const match = /^([^\[\]]+)(?:\[(\d+)\])?$/u.exec(segment);
    if (!match) return undefined;
    const [, key, indexText] = match;
    const currentRecord = recordValue(current);
    if (!currentRecord) return undefined;
    current = currentRecord[key];
    if (indexText !== undefined) {
      if (!Array.isArray(current)) return undefined;
      current = current[Number(indexText)];
    }
  }
  return current;
}

function hasUnnegatedStandardPatientResourceScopeClaim(statement: string) {
  return ["13 类标准患者资源", "标准患者资源", "每类字段目录", "完整 S0-S40", "完整上线验收"].some(
    (term) => hasScopeCompletionClaimWithoutNegation(statement, term),
  );
}

const requiredFourRoleCoreActionRoles = [
  "platform-admin",
  "engine-operator",
  "clinical-user",
  "auditor",
];

const requiredFourRoleCoreActionPaths: Record<string, string> = {
  "platform-admin": "/admin/users",
  "engine-operator": "/knowledge/production",
  "clinical-user": "/workflow/todos",
  auditor: "/admin/audit",
};

const fourRoleCoreActionDetailKeys: Record<string, string> = {
  "platform-admin": "platformAdmin",
  "engine-operator": "engineOperator",
  "clinical-user": "clinicalUser",
  auditor: "auditor",
};

function hasRequiredFourRoleCoreActionsAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find((item) => item.name === "four-role-core-actions-codes");
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scopeStatement?: unknown;
      roleActions?: unknown;
      platformAdmin?: unknown;
      engineOperator?: unknown;
      clinicalUser?: unknown;
      auditor?: unknown;
    };
    if (!hasFourRoleCoreActionScopeBoundary(parsed.scopeStatement)) return false;
    if (!Array.isArray(parsed.roleActions)) return false;
    const actionsByRole = new Map<string, Record<string, unknown>>();
    for (const item of parsed.roleActions) {
      const action = recordValue(item);
      const role = textValue(action?.role);
      if (action && role) actionsByRole.set(role, action);
    }
    return requiredFourRoleCoreActionRoles.every((role) => {
      const action = actionsByRole.get(role);
      const detail = recordValue(parsed[fourRoleCoreActionDetailKeys[role] as keyof typeof parsed]);
      return (
        hasCompleteFourRoleCoreAction(action, role) && hasCompleteFourRoleCoreAction(detail, role)
      );
    });
  } catch {
    return false;
  }
}

function hasFourRoleCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("四职责主动作代表闭环") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositiveFourRoleCoreActionCompleteScopeClaim(statement)
  );
}

function hasPositiveFourRoleCoreActionCompleteScopeClaim(statement: string) {
  return /(?:34\s*个入口全部业务动作闭环|完整上线验收|完整上线|全量验收|上线验收|上线级验收)(?:已上线|完整上线|完成上线|已完成|完成|完整覆盖|全面覆盖|通过)/u.test(
    statement,
  );
}

function hasCompleteFourRoleCoreAction(value: unknown, expectedRole: string) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  return (
    action.role === expectedRole &&
    action.path === requiredFourRoleCoreActionPaths[expectedRole] &&
    hasText(action.frontdeskAction) &&
    hasText(action.serviceOperation) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true
  );
}

const requiredSixEntryCoreActionPaths = [
  "/security/baseline",
  "/knowledge/governance",
  "/rule/definitions",
  "/notifications",
  "/notifications/settings",
  "/sandbox",
  "/advanced/provenance",
];

const requiredSixEntryCoreActionRoles = [
  "platform-admin",
  "engine-operator",
  "clinical-user",
  "auditor",
];

function hasRequiredSixEntryCoreActionsAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find((item) => item.name === "entry-core-actions-codes");
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      matrixCode?: unknown;
      scopeStatement?: unknown;
      entryActions?: unknown;
    };
    if (parsed.matrixCode !== "SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE") return false;
    if (!hasSixEntryCoreActionScopeBoundary(parsed.scopeStatement)) return false;
    if (!Array.isArray(parsed.entryActions)) return false;
    const paths = new Set<string>();
    const roles = new Set<string>();
    for (const item of parsed.entryActions) {
      const action = recordValue(item);
      if (!action) return false;
      if (!hasCompleteSixEntryCoreAction(action)) return false;
      const pathValue = textValue(action.path);
      const roleValue = textValue(action.role);
      if (!pathValue || !roleValue) return false;
      if (!requiredSixEntryCoreActionPaths.includes(pathValue)) return false;
      if (!requiredSixEntryCoreActionRoles.includes(roleValue)) return false;
      paths.add(pathValue);
      roles.add(roleValue);
    }
    return (
      requiredSixEntryCoreActionPaths.every((pathValue) => paths.has(pathValue)) &&
      requiredSixEntryCoreActionRoles.every((roleValue) => roles.has(roleValue))
    );
  } catch {
    return false;
  }
}

function hasSixEntryCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("六入口核心动作代表闭环") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositiveFourRoleCoreActionCompleteScopeClaim(statement)
  );
}

function hasCompleteSixEntryCoreAction(value: unknown) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  return (
    hasText(action.role) &&
    hasText(action.path) &&
    hasText(action.frontdeskAction) &&
    hasText(action.serviceOperation) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true
  );
}

function hasRequiredComplianceWorkbenchPersonalEntryEvidence(tests: BrowserE2eTestResult[]) {
  const roleActions = collectCompleteFourRoleCoreActionsFromTargetSpec(tests);
  const entryActions = collectCompleteSixEntryCoreActionsFromTargetSpec(tests);
  if (!roleActions || !entryActions) return false;
  return requiredComplianceWorkbenchPersonalEntryRows.every((entry) => {
    const actions = entry.source === "role" ? roleActions : entryActions;
    return actions.some((action) =>
      hasCompleteComplianceWorkbenchPersonalEntryAction(action, entry),
    );
  });
}

function hasRequiredMenuEntryCoreActionEvidence(tests: BrowserE2eTestResult[]) {
  const observed = new Set<string>();
  addMenuRowsIf(observed, hasRequiredDashboardWorkbenchCoreActionsAttachment(tests), ["workbench"]);
  addMenuRowsIf(observed, hasRequiredFourRoleCoreActionsFromTargetSpec(tests), [
    "admin-users",
    "knowledge-production",
    "workflow-todos",
    "admin-audit",
  ]);
  addMenuRowsIf(observed, hasRequiredSixEntryCoreActionsFromTargetSpec(tests), [
    "security-baseline",
    "knowledge-governance",
    "rule-definitions",
    "notifications",
    "notification-settings",
    "sandbox",
    "provenance",
  ]);
  addMenuRowsIf(observed, hasRequiredPlatformAdminEntryCoreActionsAttachment(tests), [
    "tenant-onboarding",
    "identity-bindings",
    "adapter-hub",
    "system-providers",
  ]);
  addMenuRowsIf(observed, hasRequiredPlatformAdminP1EntryCoreActionsAttachment(tests), [
    "runtime-diagnostics",
    "domestic-check",
  ]);
  addMenuRowsIf(observed, hasRequiredImplementationGuideEntryCoreActionsAttachment(tests), [
    "implementation-guide",
  ]);
  addMenuRowsIf(observed, hasRequiredComplianceWorkbenchPersonalEntryEvidence(tests), [
    "security-baseline",
    "admin-audit",
    "notifications",
    "notification-settings",
    "provenance",
  ]);
  addMenuRowsIf(observed, hasRequiredClinicalEntryCoreActionsAttachment(tests), [
    "mpi",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "clinical-followup",
  ]);
  addMenuRowsIf(observed, hasRequiredQualityManagementEntryCoreActionsAttachment(tests), [
    "qc-dashboard",
    "qc-alerts",
    "insurance-audit",
    "qc-eval-sets",
  ]);
  addMenuRowsIf(observed, hasRequiredKnowledgeOperationsAssetEntryCoreActionsAttachment(tests), [
    "knowledge-production",
    "knowledge-governance",
    "runtime-releases",
    "institution-knowledge",
    "diagnosis-knowledge",
    "terminology-mapping",
    "rule-definitions",
    "pathway-templates",
    "provenance",
    "graph-explore",
    "ai-workflows",
  ]);
  return requiredMenuEntryCoreActionRows.every((row) => observed.has(row));
}

function addMenuRowsIf(target: Set<string>, condition: boolean, rows: readonly string[]) {
  if (!condition) return;
  for (const row of rows) target.add(row);
}

function hasRequiredFourRoleCoreActionsFromTargetSpec(tests: BrowserE2eTestResult[]) {
  return tests.some(
    (test) =>
      path.basename(test.file) === "four-role-core-actions-rehearsal.spec.ts" &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      hasRequiredFourRoleCoreActionsAttachment(test),
  );
}

function hasRequiredSixEntryCoreActionsFromTargetSpec(tests: BrowserE2eTestResult[]) {
  return tests.some(
    (test) =>
      path.basename(test.file) === "entry-core-actions-rehearsal.spec.ts" &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      hasRequiredSixEntryCoreActionsAttachment(test),
  );
}

function collectCompleteFourRoleCoreActionsFromTargetSpec(tests: BrowserE2eTestResult[]) {
  for (const test of tests) {
    if (
      path.basename(test.file) !== "four-role-core-actions-rehearsal.spec.ts" ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !hasRequiredFourRoleCoreActionsAttachment(test)
    ) {
      continue;
    }
    const parsed = parseJsonAttachment(test, "four-role-core-actions-codes");
    const roleActions = recordValue(parsed)?.roleActions;
    if (Array.isArray(roleActions)) return roleActions;
  }
  return null;
}

function collectCompleteSixEntryCoreActionsFromTargetSpec(tests: BrowserE2eTestResult[]) {
  for (const test of tests) {
    if (
      path.basename(test.file) !== "entry-core-actions-rehearsal.spec.ts" ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !hasRequiredSixEntryCoreActionsAttachment(test)
    ) {
      continue;
    }
    const parsed = parseJsonAttachment(test, "entry-core-actions-codes");
    const entryActions = recordValue(parsed)?.entryActions;
    if (Array.isArray(entryActions)) return entryActions;
  }
  return null;
}

function parseJsonAttachment(test: BrowserE2eTestResult, attachmentName: string) {
  const attachment = test.attachments?.find((item) => item.name === attachmentName);
  if (!attachment?.body) return undefined;
  try {
    return JSON.parse(attachment.body) as unknown;
  } catch {
    return undefined;
  }
}

function hasCompleteComplianceWorkbenchPersonalEntryAction(
  value: unknown,
  expected: (typeof requiredComplianceWorkbenchPersonalEntryRows)[number],
) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  return (
    action.role === expected.role &&
    action.path === expected.path &&
    action.serviceOperation === expected.serviceOperation &&
    hasText(action.frontdeskAction) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true
  );
}

const requiredPlatformAdminEntryCoreActionPaths: Record<string, string> = {
  "tenant-onboarding": "/tenant/onboarding",
  "identity-bindings": "/security/identity-binding",
  "adapter-hub": "/adapter/hub",
  "system-providers": "/system/providers",
};

const requiredPlatformAdminEntryCoreActionMenuKeys = Object.keys(
  requiredPlatformAdminEntryCoreActionPaths,
);
const requiredPlatformAdminEntryCoreActionServiceOperations: Record<string, string[]> = {
  "tenant-onboarding": ["POST /api/v1/admin/tenants"],
  "identity-bindings": ["POST /api/v1/compliance/identity-bindings"],
  "adapter-hub": ["POST /api/v1/engine/integration/data-quality/reports"],
  "system-providers": ["GET /api/v1/system/operations"],
};

const requiredPlatformAdminP1EntryCoreActionPaths: Record<string, string> = {
  "runtime-diagnostics": "/system/runtime-diagnostics",
  "domestic-check": "/advanced/domestic",
};

const requiredPlatformAdminP1EntryCoreActionMenuKeys = Object.keys(
  requiredPlatformAdminP1EntryCoreActionPaths,
);
const requiredPlatformAdminP1EntryCoreActionServiceOperations: Record<string, string[]> = {
  "runtime-diagnostics": [
    "GET /api/v1/system/runtime",
    "GET /api/v1/system/runtime-diagnostics/api-contracts",
  ],
  "domestic-check": [
    "GET /api/v1/system/operations",
    "GET /api/v1/system/operations/domestic-report",
  ],
};

const requiredClinicalEntryCoreActionPaths: Record<string, string> = {
  mpi: "/mpi",
  "patient-pathways": "/pathway/patients",
  "cdss-fatigue": "/cdss/fatigue",
  "workflow-todos": "/workflow/todos",
  "clinical-followup": "/clinical/followup",
};

const requiredClinicalEntryCoreActionMenuKeys = Object.keys(requiredClinicalEntryCoreActionPaths);
const clinicalEntryCoreActionSpecFile = "clinical-entry-core-actions-rehearsal.spec.ts";
const requiredClinicalEntryCoreActionServiceOperations: Record<string, string[]> = {
  mpi: ["/api/v1/engine/mpi/patients", "/api/v1/engine/context/snapshots"],
  "patient-pathways": ["/api/v1/engine/pathway/patient-pathways/enter"],
  "cdss-fatigue": ["/api/v1/engine/recommendations:evaluate"],
  "workflow-todos": ["/api/v1/engine/workflow/todos/{todoId}/complete"],
  "clinical-followup": ["/api/v1/engine/followup/plans/generate"],
};
const requiredQualityManagementEntryCoreActionPaths: Record<string, string> = {
  "qc-dashboard": "/qc/dashboard",
  "qc-alerts": "/qc/alerts",
  "insurance-audit": "/qc/insurance",
  "qc-eval-sets": "/qc/eval/sets",
};
const requiredQualityManagementEntryCoreActionMenuKeys = Object.keys(
  requiredQualityManagementEntryCoreActionPaths,
);
const qualityManagementEntryCoreActionSpecFile =
  "quality-management-entry-core-actions-rehearsal.spec.ts";
const requiredQualityManagementEntryCoreActionServiceOperations: Record<string, string[]> = {
  "qc-dashboard": [
    "/api/v1/engine/quality/dashboard",
    "/api/v1/engine/quality/dashboard/drilldown",
  ],
  "qc-alerts": [
    "/api/v1/engine/rectifications/{taskId}/submit",
    "/api/v1/engine/rectifications/{taskId}/review",
  ],
  "insurance-audit": [
    "/api/v1/engine/quality/case-review",
    "/api/v1/engine/quality/drg-grouping",
    "/api/v1/engine/quality/insurance-audit",
  ],
  "qc-eval-sets": [
    "/api/v1/engine/evaluation/indicators",
    "/api/v1/engine/evaluation/indicators/{indicatorId}/activate",
  ],
};
const requiredKnowledgeOperationsAssetEntryActionPaths: Record<string, string> = {
  "knowledge-production": "/knowledge/production",
  "knowledge-governance": "/knowledge/governance",
  "runtime-releases": "/config/releases",
  "terminology-mapping": "/terminology/mapping",
  "rule-definitions": "/rule/definitions",
  "pathway-templates": "/pathway/templates",
  "institution-knowledge": "/knowledge/institution",
  "diagnosis-knowledge": "/knowledge/diagnosis",
  provenance: "/advanced/provenance",
  "graph-explore": "/advanced/graph",
  "ai-workflows": "/advanced/ai-workflows",
};
const requiredKnowledgeOperationsAssetEntryActionMenuKeys = Object.keys(
  requiredKnowledgeOperationsAssetEntryActionPaths,
);
const knowledgeOperationsAssetEntryActionSpecFile =
  "knowledge-operations-asset-entry-core-actions-rehearsal.spec.ts";
const requiredKnowledgeOperationsAssetEntryActionServiceOperations: Record<string, string[]> = {
  "knowledge-production": [
    "/api/v1/engine/knowledge/documents:upload-parse",
    "/api/v1/engine/knowledge-production/generate",
  ],
  "knowledge-governance": [
    "/api/v1/engine/knowledge/candidates/{candidateId}/review",
    "/api/v1/engine/knowledge/review-queue",
  ],
  "runtime-releases": [
    "/api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases",
    "/api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases:rollback",
  ],
  "terminology-mapping": [
    "/api/v1/engine/terminology/terms/standard",
    "/api/v1/engine/terminology/mappings/candidates/confirm",
  ],
  "rule-definitions": ["/api/v1/engine/rule/rules", "/api/v1/engine/rule/rules/{ruleId}/simulate"],
  "pathway-templates": [
    "/api/v1/engine/pathway/pathway-templates",
    "/api/v1/engine/pathway/pathway-templates/{templateId}/simulate",
  ],
  "institution-knowledge": [
    "/api/v1/engine/knowledge/customizations",
    "/api/v1/engine/knowledge/customizations/{customizationId}:restore-platform",
  ],
  "diagnosis-knowledge": [
    "/api/v1/engine/knowledge/diagnosis/assets",
    "/api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria",
    "/api/v1/engine/knowledge/diagnosis/versions/{versionId}/test-cases",
  ],
  provenance: ["/api/v1/engine/knowledge/identities/{identityId}/provenance"],
  "graph-explore": [
    "/api/v1/projections/knowledge-graph/rebuild",
    "/api/v1/projections/knowledge-graph/facts",
  ],
  "ai-workflows": ["/api/v1/engine/knowledge-production/readiness"],
};

function hasRequiredPlatformAdminEntryCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
  const actionsByMenuKey = new Map<string, Record<string, unknown>>();
  let sawAttachment = false;
  for (const test of tests) {
    if (
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    for (const attachment of test.attachments) {
      if (attachment.name !== "platform-admin-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const body = recordValue(parsed);
      if (!body) return false;
      if (body.matrixCode !== "PLATFORM_ADMIN_P0_ENTRY_CORE_ACTIONS") continue;
      sawAttachment = true;
      if (!hasPlatformAdminEntryCoreActionScopeBoundary(body.scopeStatement)) return false;
      if (!Array.isArray(body.entryActions)) return false;
      for (const item of body.entryActions) {
        const action = recordValue(item);
        const menuKey = textValue(action?.menuKey);
        if (
          !action ||
          !menuKey ||
          !hasCompletePlatformAdminEntryCoreAction(action, menuKey) ||
          actionsByMenuKey.has(menuKey)
        ) {
          return false;
        }
        actionsByMenuKey.set(menuKey, action);
      }
    }
  }
  return (
    sawAttachment &&
    requiredPlatformAdminEntryCoreActionMenuKeys.every((menuKey) => actionsByMenuKey.has(menuKey))
  );
}

function hasPlatformAdminEntryCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("平台管理员 P0 入口核心动作代表矩阵") &&
    hasNegatedScopeTerm(statement, "6 个平台管理员入口全部闭环") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositivePlatformAdminEntryCompleteScopeClaim(statement)
  );
}

function hasRequiredPlatformAdminP1EntryCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
  const actionsByMenuKey = new Map<string, Record<string, unknown>>();
  let sawAttachment = false;
  for (const test of tests) {
    if (
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    for (const attachment of test.attachments) {
      if (attachment.name !== "platform-admin-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const body = recordValue(parsed);
      if (!body || body.matrixCode !== "PLATFORM_ADMIN_P1_ENTRY_CORE_ACTIONS") continue;
      sawAttachment = true;
      if (!hasPlatformAdminP1EntryCoreActionScopeBoundary(body.scopeStatement)) return false;
      if (!Array.isArray(body.entryActions)) return false;
      for (const item of body.entryActions) {
        const action = recordValue(item);
        const menuKey = textValue(action?.menuKey);
        if (
          !action ||
          !menuKey ||
          !hasCompletePlatformAdminEntryCoreAction(
            action,
            menuKey,
            requiredPlatformAdminP1EntryCoreActionPaths,
            requiredPlatformAdminP1EntryCoreActionServiceOperations,
          ) ||
          actionsByMenuKey.has(menuKey)
        ) {
          return false;
        }
        actionsByMenuKey.set(menuKey, action);
      }
    }
  }
  return (
    sawAttachment &&
    requiredPlatformAdminP1EntryCoreActionMenuKeys.every((menuKey) => actionsByMenuKey.has(menuKey))
  );
}

function hasPlatformAdminP1EntryCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("平台管理员 P1 系统运维入口核心动作代表矩阵") &&
    hasNegatedScopeTerm(statement, "6 个平台管理员入口全部闭环") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositivePlatformAdminEntryCompleteScopeClaim(statement)
  );
}

function hasRequiredImplementationGuideEntryCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
  for (const test of tests) {
    if (
      path.basename(test.file) !== "stakeholder-view-rehearsal.spec.ts" ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    for (const attachment of test.attachments) {
      if (attachment.name !== "implementation-guide-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const body = recordValue(parsed);
      if (!body || body.matrixCode !== "IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS") {
        continue;
      }
      if (!hasImplementationGuideEntryCoreActionScopeBoundary(body.scopeStatement)) return false;
      if (!Array.isArray(body.entryActions) || body.entryActions.length !== 1) return false;
      return hasCompleteImplementationGuideEntryCoreAction(body.entryActions[0]);
    }
  }
  return false;
}

function hasImplementationGuideEntryCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("实施与验收入口代表动作矩阵") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "第三方系统族全部真实消费者完成") &&
    hasNegatedScopeTerm(statement, "134 清库重部署") &&
    hasNegatedScopeTerm(statement, "完整交付验收") &&
    !hasPositiveImplementationGuideCompleteScopeClaim(statement)
  );
}

function hasPositiveImplementationGuideCompleteScopeClaim(statement: string) {
  return [
    "34 个入口全部业务动作闭环",
    "第三方系统族全部真实消费者完成",
    "134 清库重部署",
    "134清库重部署",
    "完整交付验收",
    "交付验收完成",
    "完整上线",
    "完整上线验收",
    "上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompleteImplementationGuideEntryCoreAction(value: unknown) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  const serviceOperation = textValue(action.serviceOperation);
  return (
    action.menuKey === "implementation-guide" &&
    action.role === "platform-admin" &&
    action.path === "/onboarding/guide" &&
    hasText(action.frontdeskAction) &&
    hasExpectedImplementationGuideEntryCoreActionServiceOperation(serviceOperation) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true &&
    action.implementationStepsReadbackVerified === true &&
    action.onboardingReadinessReadbackVerified === true &&
    action.dataQualityReportVerified === true
  );
}

function hasCompleteImplementationGuideDataQualityGapEvidence(value: unknown) {
  const body = recordValue(value);
  if (!body || body.matrixCode !== "IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS") {
    return false;
  }
  if (!hasImplementationGuideEntryCoreActionScopeBoundary(body.scopeStatement)) return false;
  if (!Array.isArray(body.entryActions) || body.entryActions.length !== 1) return false;
  const action = recordValue(body.entryActions[0]);
  if (!action || !hasCompleteImplementationGuideEntryCoreAction(action)) return false;
  const report = recordValue(action.dataQualityReport);
  if (!report) return false;
  const gapSummary = textValue(report.gapSummary);
  return (
    hasText(report.reportId) &&
    hasText(report.traceId) &&
    hasText(gapSummary) &&
    /适配器|NOT_CONNECTED|MISCONFIGURED|缺口|未接通|未登记/u.test(gapSummary ?? "") &&
    report.auditVerified === true
  );
}

function hasExpectedImplementationGuideEntryCoreActionServiceOperation(
  serviceOperation: string | null,
) {
  if (!serviceOperation) return false;
  return [
    "GET /api/v1/engine/tenant/implementation-steps",
    "GET /api/v1/engine/tenant/onboarding-readiness",
    "POST /api/v1/engine/integration/data-quality/reports",
  ].every((expected) => serviceOperation.includes(expected));
}

function hasRequiredDashboardWorkbenchCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
  for (const test of tests) {
    if (
      path.basename(test.file) !== "product-role-journeys.spec.ts" ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    for (const attachment of test.attachments) {
      if (!attachment.name.startsWith("dashboard-workbench-core-actions-codes-")) continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const body = recordValue(parsed);
      if (!body || body.matrixCode !== "DASHBOARD_WORKBENCH_CORE_ACTIONS") continue;
      if (!hasDashboardWorkbenchCoreActionScopeBoundary(body.scopeStatement)) return false;
      if (!Array.isArray(body.roleActions)) return false;
      const rowsByRole = new Map<string, Record<string, unknown>>();
      for (const item of body.roleActions) {
        const action = recordValue(item);
        const role = textValue(action?.role);
        if (
          !action ||
          !role ||
          !hasCompleteDashboardWorkbenchCoreAction(action, role) ||
          rowsByRole.has(role)
        ) {
          return false;
        }
        rowsByRole.set(role, action);
      }
      return requiredDashboardWorkbenchRows.every((row) => rowsByRole.has(row.role));
    }
  }
  return false;
}

function collectRoleScopeFrontdeskActionRepresentativeSliceClaims(
  tests: BrowserE2eTestResult[],
) {
  const dashboardTest = tests.find(
    (test) =>
      path.basename(test.file) === "product-role-journeys.spec.ts" &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      hasRequiredDashboardWorkbenchCoreActionsAttachment([test]),
  );
  if (!dashboardTest || !hasRequiredFourRoleCoreActionsFromTargetSpec(tests)) return [];
  const attachment = dashboardTest.attachments?.find((item) =>
    item.name.startsWith("dashboard-workbench-core-actions-codes-"),
  );
  if (!attachment?.body || attachment.contentType !== "application/json") return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    return parsed &&
      hasCompleteDashboardWorkbenchPermissionBoundaryEvidence(
        parsed.permissionBoundaryEvidence,
      ) &&
      hasCompleteDashboardWorkbenchSixStateEvidence(parsed.sixStateEvidence)
      ? roleScopeFrontdeskActionRepresentativeSliceClaims
      : [];
  } catch {
    return [];
  }
}

function hasDashboardWorkbenchCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("四职责工作台核心动作代表矩阵") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "每个入口的完整业务流程") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositiveDashboardWorkbenchCompleteScopeClaim(statement)
  );
}

function hasPositiveDashboardWorkbenchCompleteScopeClaim(statement: string) {
  return [
    "34 个入口全部业务动作闭环",
    "每个入口的完整业务流程",
    "完整上线",
    "完整上线验收",
    "上线验收",
    "全角色上线完成",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompleteDashboardWorkbenchCoreAction(value: unknown, role: string) {
  const action = recordValue(value);
  const expected = requiredDashboardWorkbenchRows.find((row) => row.role === role);
  const serviceOperation = textValue(action?.serviceOperation);
  const serviceStatus = action?.serviceStatus;
  if (!action || !expected || !serviceOperation) return false;
  return (
    action.row === expected.row &&
    action.title === expected.title &&
    action.path === "/dashboard" &&
    action.primaryActionLabel === expected.primaryActionLabel &&
    action.primaryActionPath === expected.primaryActionPath &&
    arrayEquals(action.highFrequencyPaths, expected.highFrequencyPaths) &&
    expected.serviceOperations.every((operation) => serviceOperation.includes(operation)) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.primaryActionVerified === true &&
    action.highFrequencyTasksVerified === true &&
    action.sourceStatusVerified === true &&
    action.noBrowserErrors === true &&
    action.noServerErrors === true &&
    action.noNetworkFailures === true
  );
}

function hasCompleteDashboardWorkbenchPermissionBoundaryEvidence(value: unknown) {
  const evidence = recordValue(value);
  return (
    evidence !== null &&
    evidence.menuSnapshotVerified === true &&
    evidence.forbiddenStateAbsent === true &&
    evidence.roleScopeReadbackVerified === true
  );
}

function hasCompleteDashboardWorkbenchSixStateEvidence(value: unknown) {
  const evidence = recordValue(value);
  return (
    evidence !== null &&
    evidence.normalStateVerified === true &&
    evidence.emptyStateNotUsedAsSuccess === true &&
    evidence.loadingStateSettled === true &&
    evidence.errorStateAbsent === true &&
    evidence.forbiddenStateAbsent === true &&
    evidence.sourceStatusVisible === true
  );
}

function hasRequiredClinicalEntryCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
  let sawAttachment = false;
  for (const test of tests) {
    if (
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    if (path.basename(test.file) !== clinicalEntryCoreActionSpecFile) continue;
    for (const attachment of test.attachments) {
      if (attachment.name !== "clinical-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const body = recordValue(parsed);
      if (!body || body.matrixCode !== "CLINICAL_COLLABORATION_ENTRY_CORE_ACTIONS") continue;
      sawAttachment = true;
      if (!hasCompleteClinicalEntryCoreActionMatrix(body)) return false;
    }
  }
  return sawAttachment;
}

function hasCompleteClinicalEntryCoreActionMatrix(body: Record<string, unknown>) {
  const actionsByMenuKey = new Map<string, Record<string, unknown>>();
  if (!hasClinicalEntryCoreActionScopeBoundary(body.scopeStatement)) return false;
  if (!Array.isArray(body.entryActions)) return false;
  for (const item of body.entryActions) {
    const action = recordValue(item);
    const menuKey = textValue(action?.menuKey);
    if (
      !action ||
      !menuKey ||
      !hasCompleteClinicalEntryCoreAction(action, menuKey) ||
      actionsByMenuKey.has(menuKey)
    ) {
      return false;
    }
    actionsByMenuKey.set(menuKey, action);
  }
  return requiredClinicalEntryCoreActionMenuKeys.every((menuKey) => actionsByMenuKey.has(menuKey));
}

function hasRequiredQualityManagementEntryCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
  return qualityManagementEntryCoreActionAttachmentBodies(tests).some((body) =>
    hasCompleteQualityManagementEntryCoreActionMatrix(body),
  );
}

function qualityManagementEntryCoreActionAttachmentBodies(tests: BrowserE2eTestResult[]) {
  const bodies: Array<Record<string, unknown>> = [];
  for (const test of tests) {
    if (
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    if (path.basename(test.file) !== qualityManagementEntryCoreActionSpecFile) continue;
    for (const attachment of test.attachments) {
      if (attachment.name !== "quality-management-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return [];
      try {
        const body = recordValue(JSON.parse(attachment.body));
        if (body?.matrixCode === "QUALITY_MANAGEMENT_ENTRY_CORE_ACTIONS") {
          bodies.push(body);
        }
      } catch {
        return [];
      }
    }
  }
  return bodies;
}

function hasCompleteQualityManagementEntryCoreActionMatrix(body: Record<string, unknown>) {
  const actionsByMenuKey = new Map<string, Record<string, unknown>>();
  if (!hasQualityManagementEntryCoreActionScopeBoundary(body.scopeStatement)) return false;
  if (!Array.isArray(body.entryActions)) return false;
  for (const item of body.entryActions) {
    const action = recordValue(item);
    const menuKey = textValue(action?.menuKey);
    if (
      !action ||
      !menuKey ||
      !hasCompleteQualityManagementEntryCoreAction(action, menuKey) ||
      actionsByMenuKey.has(menuKey)
    ) {
      return false;
    }
    actionsByMenuKey.set(menuKey, action);
  }
  return requiredQualityManagementEntryCoreActionMenuKeys.every((menuKey) =>
    actionsByMenuKey.has(menuKey),
  );
}

function hasCompleteEvaluationAssetSupplyChainEvidence(tests: BrowserE2eTestResult[]) {
  return qualityManagementEntryCoreActionAttachmentBodies(tests).some(
    (body) =>
      hasCompleteQualityManagementEntryCoreActionMatrix(body) &&
      hasCompleteEvaluationAssetEvidence(body.evaluationAssetSupplyChainEvidence),
  );
}

function hasCompleteEvaluationAssetEvidence(value: unknown) {
  const evidence = recordValue(value);
  const candidate = parseRuntimeReleaseCandidate(evidence);
  if (
    !evidence ||
    !candidate ||
    candidate.assetType !== "EVALUATION" ||
    !hasText(evidence.indicatorId) ||
    evidence.indicatorPublished !== true ||
    evidence.indicatorActivated !== true ||
    evidence.runtimeActivationVerified !== true ||
    evidence.runtimeConsumerReadbackVerified !== true ||
    evidence.insuranceAuditEvaluationRunVerified !== true ||
    evidence.findingBoundToIndicatorVerified !== true ||
    evidence.auditVerified !== true
  ) {
    return false;
  }
  const runtimeReadback = recordValue(evidence.runtimeReadback);
  const runtimeConsumer = recordValue(evidence.runtimeConsumer);
  return (
    runtimeReleasePayloadContainsCandidate(evidence.activationRequest, "activeAssets", candidate) &&
    hasRuntimeReadbackCandidate(runtimeReadback, candidate) &&
    hasRuntimeReadbackCandidate(runtimeConsumer, candidate) &&
    textValue(runtimeConsumer?.contractVersion) === "v1" &&
    textValue(runtimeReadback?.releaseId) === textValue(runtimeConsumer?.releaseId) &&
    numberValue(runtimeReadback?.revisionNo) === numberValue(runtimeConsumer?.revisionNo) &&
    textValue(runtimeReadback?.manifestSha256) === textValue(runtimeConsumer?.manifestSha256)
  );
}

function hasRequiredMedicalRecordInsurancePaymentConsumerSliceAttachment(
  tests: BrowserE2eTestResult[],
) {
  return qualityManagementEntryCoreActionAttachmentBodies(tests).some(
    (body) =>
      hasCompleteQualityManagementEntryCoreActionMatrix(body) &&
      hasCompleteMedicalRecordInsurancePaymentConsumerSlice(body),
  );
}

function hasCompleteMedicalRecordInsurancePaymentConsumerSlice(body: Record<string, unknown>) {
  const slice = recordValue(body.medicalRecordInsurancePaymentConsumerSlice);
  const issue = recordValue(body.medicalRecordQualityIssueEvidence);
  const context = recordValue(body.clinicalContext);
  const resources = recordValue(context?.resources);
  const claims = Array.isArray(resources?.claims) ? resources.claims.map(recordValue) : [];
  const claim = claims.find((item) => {
    if (!item) return false;
    return (
      item.sourceSystem === "MEDKERNEL_FRONTDESK" &&
      item.qualityStatus === "VALID" &&
      hasText(item.claimId) &&
      resourceHasStandardPatientResourceShape(item, "Claim")
    );
  });
  return (
    slice !== null &&
    issue !== null &&
    context !== null &&
    claim !== undefined &&
    slice.systemFamilyCode === "MEDICAL_RECORD_INSURANCE_PAYMENT" &&
    hasText(slice.familyName) &&
    String(slice.familyName).includes("医保") &&
    arrayEquals(slice.canonicalResources, ["Claim"]) &&
    arrayEquals(slice.sourceSystems, ["MEDKERNEL_FRONTDESK"]) &&
    slice.consumer === "INSURANCE_AUDIT" &&
    slice.consumerVerified === true &&
    slice.standardResourceVerified === true &&
    slice.evaluationRunVerified === true &&
    slice.rectificationClosedVerified === true &&
    slice.auditVerified === true &&
    slice.noAutoPaymentDecision === true &&
    slice.claimResourcePath === "clinicalContext.resources.claims[0]" &&
    slice.issueIdPath === "medicalRecordQualityIssueEvidence.issueId" &&
    slice.evaluationRunIdPath === "medicalRecordQualityIssueEvidence.evaluationRunId" &&
    hasMedicalRecordInsurancePaymentConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasCompleteMedicalRecordQualityIssueEvidence(issue) &&
    evidencePathsResolve(body, [
      slice.claimResourcePath,
      slice.issueIdPath,
      slice.evaluationRunIdPath,
    ]) &&
    hasCompleteQualityManagementEntryCoreActionForBody(body, "insurance-audit") &&
    hasCompleteQualityManagementEntryCoreActionForBody(body, "qc-alerts")
  );
}

function hasRequiredFollowupPatientServiceConsumerSliceAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "real-frontdesk-scenario-codes",
  );
  if (!attachment?.body || attachment.contentType !== "application/json") return false;
  try {
    const body = recordValue(JSON.parse(attachment.body));
    return body !== null && hasCompleteFollowupPatientServiceConsumerSlice(body);
  } catch {
    return false;
  }
}

function hasCompleteFollowupPatientServiceConsumerSlice(body: Record<string, unknown>) {
  if (!hasCompleteFollowupS12NormalEvidence(body)) return false;
  const slice = recordValue(body.followupPatientServiceConsumerSlice);
  const template = recordValue(body.followupTemplate);
  const runtime = recordValue(body.followupRuntime);
  const plan = recordValue(body.followupPlan);
  const questionnaire = recordValue(body.questionnaire);
  const abnormal = recordValue(body.abnormalReturn);
  const result = recordValue(body.resultBackflow);
  const backflowContext = recordValue(body.backflowContext);
  const resources = recordValue(backflowContext?.resources);
  const followUps = Array.isArray(resources?.followUps) ? resources.followUps.map(recordValue) : [];
  const followUp = followUps.find((item) => {
    if (!item || !questionnaire) return false;
    return (
      item.followUpId === questionnaire.questionnaireId &&
      item.sourceSystem === "FOLLOWUP" &&
      item.mappedVersion === "FOLLOWUP_RESULT" &&
      item.qualityStatus === "VALID" &&
      hasText(item.sourceRecordId) &&
      resourceHasStandardPatientResourceShape(item, "FollowUp")
    );
  });
  return (
    slice !== null &&
    template !== null &&
    runtime !== null &&
    plan !== null &&
    questionnaire !== null &&
    abnormal !== null &&
    result !== null &&
    backflowContext !== null &&
    followUp !== undefined &&
    slice.systemFamilyCode === "FOLLOWUP_PATIENT_SERVICE" &&
    hasText(slice.familyName) &&
    String(slice.familyName).includes("随访") &&
    arrayEquals(slice.canonicalResources, ["Patient", "Encounter", "FollowUp"]) &&
    arrayEquals(slice.sourceSystems, ["MEDKERNEL_FRONTDESK", "FOLLOWUP"]) &&
    slice.consumer === "FOLLOWUP_RESULT_BACKFLOW" &&
    slice.consumerVerified === true &&
    slice.standardResourceVerified === true &&
    slice.runtimeConsumerVerified === true &&
    slice.questionnaireVerified === true &&
    slice.abnormalReturnVerified === true &&
    slice.resultBackflowVerified === true &&
    slice.auditVerified === true &&
    slice.noAutoOrder === true &&
    slice.followUpResourcePath === "backflowContext.resources.followUps[0]" &&
    slice.resultBackflowContextPath === "resultBackflow.contextSnapshotId" &&
    slice.auditEventPath === "resultBackflow.eventId" &&
    hasFollowupPatientServiceConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasText(result.eventId) &&
    hasText(result.contextSnapshotId) &&
    result.contextSnapshotId === backflowContext.contextSnapshotId &&
    result.sourceQuestionnaireId === questionnaire.questionnaireId &&
    result.abnormalFlag === "Y" &&
    backflowContext.runtimeReleaseId === plan.runtimeReleaseId &&
    backflowContext.runtimeReleaseId === runtime.runtimeReleaseId &&
    plan.templateId === template.templateId &&
    plan.templateCode === template.templateCode &&
    evidencePathsResolve(body, [
      slice.followUpResourcePath,
      slice.resultBackflowContextPath,
      slice.auditEventPath,
    ])
  );
}

function hasRequiredHisEmrCdrConsumerSliceAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "critical-emergency-icu-frontdesk-codes",
  );
  if (!attachment?.body || attachment.contentType !== "application/json") return false;
  try {
    const body = recordValue(JSON.parse(attachment.body));
    return body !== null && hasCompleteHisEmrCdrConsumerSlice(body);
  } catch {
    return false;
  }
}

function hasRequiredPharmacyReviewConsumerSliceAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "pharmacy-review-antimicrobial-frontdesk-codes",
  );
  if (!attachment?.body || attachment.contentType !== "application/json") return false;
  try {
    const body = recordValue(JSON.parse(attachment.body));
    return body !== null && hasCompletePharmacyReviewConsumerSlice(body);
  } catch {
    return false;
  }
}

function hasCompleteHisEmrCdrConsumerSlice(body: Record<string, unknown>) {
  const runtime = parseCriticalEmergencyIcuRuntimeEvidence(body.runtime);
  if (!runtime) return false;
  const slice = recordValue(body.hisEmrCdrConsumerSlice);
  const context = recordValue(body.clinicalContext);
  const resources = recordValue(context?.resources);
  const encounters = Array.isArray(resources?.encounters) ? resources.encounters : [];
  const conditions = Array.isArray(resources?.conditions) ? resources.conditions : [];
  const observations = Array.isArray(resources?.observations) ? resources.observations : [];
  const procedures = Array.isArray(resources?.procedures) ? resources.procedures : [];
  return (
    slice !== null &&
    context !== null &&
    slice.systemFamilyCode === "HIS_EMR_CDR" &&
    hasText(slice.familyName) &&
    String(slice.familyName).includes("HIS") &&
    slice.consumer === "CRITICAL_EMERGENCY_ICU_TRIAGE_CONTEXT" &&
    arrayEquals(slice.canonicalResources, [
      "Patient",
      "Encounter",
      "Condition",
      "Observation",
      "Procedure",
    ]) &&
    slice.frontdeskPatientCreated === true &&
    slice.contextSnapshotReadbackVerified === true &&
    slice.recommendationConsumerVerified === true &&
    slice.manualEscalationVerified === true &&
    slice.todoCompletionVerified === true &&
    slice.auditVerified === true &&
    slice.permissionVerified === true &&
    slice.sixStateBoundaryVerified === true &&
    slice.requiresPhysicianConfirmation === true &&
    slice.noAutoOrder === true &&
    slice.noAutoTransfer === true &&
    slice.noDeviceControl === true &&
    slice.noAutoVentilatorChange === true &&
    slice.patientId === context.patientId &&
    slice.encounterId === context.encounterId &&
    slice.contextSnapshotId === context.contextSnapshotId &&
    slice.runtimeReleaseId === runtime.releaseId &&
    hasText(slice.cardId) &&
    hasText(slice.feedbackId) &&
    hasText(slice.todoId) &&
    slice.contextResourcePath === "clinicalContext.resources" &&
    slice.recommendationPath === "recommendation" &&
    slice.manualEscalationPath === "manualEscalation" &&
    slice.todoPath === "escalationTodo" &&
    hasHisEmrCdrConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasCompleteCriticalEmergencyIcuApiEvidence(body.apiEvidence) &&
    hasCompleteCriticalEmergencyIcuClinicalContext(body.clinicalContext, runtime.releaseId) &&
    hasCompleteCriticalEmergencyIcuRecommendation(
      body.recommendation,
      runtime,
      body.clinicalTrigger,
      body.ruleAsset,
    ) &&
    hasCompleteCriticalEmergencyIcuManualEscalation(
      body.manualEscalation,
      runtime.actionCardAsset,
      body.recommendation,
    ) &&
    hasCompleteCriticalEmergencyIcuTodo(
      body.escalationTodo,
      body.recommendation,
      body.clinicalContext,
    ) &&
    recordValue(body.recommendation)?.cardId === slice.cardId &&
    recordValue(body.manualEscalation)?.feedbackId === slice.feedbackId &&
    recordValue(body.escalationTodo)?.todoId === slice.todoId &&
    encounters.length > 0 &&
    conditions.length > 0 &&
    observations.length >= 2 &&
    procedures.length > 0 &&
    evidencePathsResolve(body, [
      slice.contextResourcePath,
      slice.recommendationPath,
      slice.manualEscalationPath,
      slice.todoPath,
    ])
  );
}

function hasHisEmrCdrConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedHisEmrCdrConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整 HIS/EMR/CDR 系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整急诊系统覆盖") &&
    hasNegatedScopeTerm(statement, "完整 ICU 系统覆盖") &&
    hasNegatedScopeTerm(statement, "完整病历归档") &&
    hasNegatedScopeTerm(statement, "医嘱闭环") &&
    hasNegatedScopeTerm(statement, "费用病案 CDR 全量同步") &&
    hasNegatedScopeTerm(statement, "生命支持设备控制") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S19/S24/S27") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedHisEmrCdrConsumerSliceScopeClaim(statement: string) {
  return [
    "完整 HIS/EMR/CDR 系统族覆盖",
    "完整HIS/EMR/CDR系统族覆盖",
    "完整急诊系统覆盖",
    "完整急诊系统",
    "完整 ICU 系统覆盖",
    "完整 ICU 系统",
    "完整ICU系统",
    "完整病历归档",
    "医嘱闭环",
    "费用病案 CDR 全量同步",
    "费用病案CDR全量同步",
    "生命支持设备控制",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整 S19/S24/S27",
    "完整S19/S24/S27",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasFollowupPatientServiceConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表消费者切片") &&
    !hasUnnegatedFollowupPatientServiceConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整随访系统覆盖") &&
    hasNegatedScopeTerm(statement, "完整患者服务系统覆盖") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S12") &&
    hasNegatedScopeTerm(statement, "完整 S30") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedFollowupPatientServiceConsumerSliceScopeClaim(statement: string) {
  return [
    "完整随访系统覆盖",
    "完整患者服务系统覆盖",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整 S12",
    "完整S12",
    "完整 S30",
    "完整S30",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompleteQualityManagementEntryCoreActionForBody(
  body: Record<string, unknown>,
  menuKey: string,
) {
  if (!Array.isArray(body.entryActions)) return false;
  return body.entryActions.some((action) =>
    hasCompleteQualityManagementEntryCoreAction(action, menuKey),
  );
}

function hasMedicalRecordInsurancePaymentConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表消费者切片") &&
    !hasUnnegatedMedicalRecordInsurancePaymentConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整病案医保支付系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 DRG/DIP") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S10") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedMedicalRecordInsurancePaymentConsumerSliceScopeClaim(statement: string) {
  return [
    "完整病案医保支付系统族覆盖",
    "完整 DRG/DIP",
    "完整DRG/DIP",
    "完整医保支付审核",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整 S10",
    "完整S10",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasRuntimeReadbackCandidate(
  value: Record<string, unknown> | null,
  candidate: { assetType: string; assetIdentity: string; versionId: string },
) {
  return Boolean(
    value &&
      hasText(value.releaseId) &&
      typeof value.revisionNo === "number" &&
      value.revisionNo > 0 &&
      isSha256(value.manifestSha256) &&
      Array.isArray(value.assets) &&
      runtimeReleasePayloadContainsCandidate(value, "assets", candidate, { requireActive: true }),
  );
}

function numberValue(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return null;
}

function hasRequiredKnowledgeOperationsAssetEntryCoreActionsAttachment(
  tests: BrowserE2eTestResult[],
) {
  const actionsByMenuKey = new Map<string, Record<string, unknown>>();
  let sawAttachment = false;
  for (const test of tests) {
    if (
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    if (path.basename(test.file) !== knowledgeOperationsAssetEntryActionSpecFile) continue;
    for (const attachment of test.attachments) {
      if (attachment.name !== "knowledge-operations-asset-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const body = recordValue(parsed);
      if (!body || body.matrixCode !== "KNOWLEDGE_OPERATIONS_ASSET_ENTRY_CORE_ACTIONS") continue;
      sawAttachment = true;
      if (
        !hasKnowledgeOperationsAssetEntryCoreActionScopeBoundary(body.scopeStatement) ||
        !hasCompleteKnowledgeOperationsFormalChain(body.formalChain) ||
        !arrayEquals(body.assetTypesCovered, requiredRuntimeReleaseVersionedAssets) ||
        !hasCompleteKnowledgeOperationsSupplyChainGates(body.supplyChainGates) ||
        !Array.isArray(body.entryActions)
      ) {
        return false;
      }
      for (const item of body.entryActions) {
        const action = recordValue(item);
        const menuKey = textValue(action?.menuKey);
        if (
          !action ||
          !menuKey ||
          !hasCompleteKnowledgeOperationsAssetEntryCoreAction(action, menuKey) ||
          actionsByMenuKey.has(menuKey)
        ) {
          return false;
        }
        actionsByMenuKey.set(menuKey, action);
      }
    }
  }
  return (
    sawAttachment &&
    requiredKnowledgeOperationsAssetEntryActionMenuKeys.every((menuKey) =>
      actionsByMenuKey.has(menuKey),
    )
  );
}

function hasRequiredKnowledgeSupplyChainEvidenceAttachment(tests: BrowserE2eTestResult[]) {
  for (const test of tests) {
    if (
      path.basename(test.file) !== knowledgeOperationsAssetEntryActionSpecFile ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    if (!hasRequiredKnowledgeOperationsAssetEntryCoreActionsAttachment([test])) continue;
    for (const attachment of test.attachments) {
      if (attachment.name !== "knowledge-operations-asset-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      try {
        const parsed = recordValue(JSON.parse(attachment.body));
        if (hasCompleteKnowledgeSupplyChainEvidence(parsed?.knowledgeSupplyChainEvidence)) {
          return true;
        }
      } catch {
        return false;
      }
    }
  }
  return false;
}

function hasRequiredLaunchReadinessStakeholderAttachment(tests: BrowserE2eTestResult[]) {
  for (const test of tests) {
    if (
      path.basename(test.file) !== "stakeholder-view-rehearsal.spec.ts" ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !Array.isArray(test.attachments)
    ) {
      continue;
    }
    for (const attachment of test.attachments) {
      if (attachment.name !== "stakeholder-view-runtime-records") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      try {
        const parsed = JSON.parse(attachment.body);
        if (hasCompleteLaunchReadinessStakeholderRecords(parsed)) return true;
      } catch {
        return false;
      }
    }
  }
  return false;
}

function hasCompleteLaunchReadinessStakeholderRecords(value: unknown) {
  if (!Array.isArray(value)) return false;
  const recordsByCode = new Map<string, Record<string, unknown>>();
  for (const item of value) {
    const record = recordValue(item);
    const code = textValue(record?.code);
    if (!record || !code) return false;
    if (recordsByCode.has(code)) return false;
    recordsByCode.set(code, record);
  }
  return requiredLaunchReadinessStakeholders.every((expected) => {
    const record = recordsByCode.get(expected.code);
    return !!record && hasCompleteLaunchReadinessStakeholderRecord(record, expected);
  });
}

function hasCompleteLaunchReadinessStakeholderRecord(
  record: Record<string, unknown>,
  expected: (typeof requiredLaunchReadinessStakeholders)[number],
) {
  const actions = Array.isArray(record.actions)
    ? record.actions.filter((item): item is string => hasText(item))
    : [];
  return (
    textValue(record.role) === expected.role &&
    textValue(record.path) === expected.path &&
    actions.length > 0 &&
    expected.actionTerms.every((term) => actions.some((action) => action.includes(term))) &&
    hasEmptyRuntimeIssueList(record.browserErrors) &&
    hasEmptyRuntimeIssueList(record.serverErrors) &&
    hasEmptyRuntimeIssueList(record.networkFailures)
  );
}

function hasEmptyRuntimeIssueList(value: unknown) {
  return Array.isArray(value) && value.length === 0;
}

function hasClinicalEntryCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("临床协同入口核心动作代表矩阵") &&
    hasNegatedScopeTerm(statement, "完整临床流程") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositiveClinicalEntryCompleteScopeClaim(statement)
  );
}

function hasQualityManagementEntryCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("质量管理入口核心动作代表矩阵") &&
    hasNegatedScopeTerm(statement, "质量管理 4 个入口全部完整上线") &&
    hasNegatedScopeTerm(statement, "完整 DRG/DIP") &&
    hasNegatedScopeTerm(statement, "完整 S9-S11") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositiveQualityManagementEntryCompleteScopeClaim(statement)
  );
}

function hasKnowledgeOperationsAssetEntryCoreActionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("知识运营资产入口族供给链代表矩阵") &&
    hasNegatedScopeTerm(statement, "全知识供给链完整上线") &&
    hasNegatedScopeTerm(statement, "13 类医学资产全部生产闭环") &&
    hasNegatedScopeTerm(statement, "所有医学知识和术语体系已收集完成") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "34 个入口全部业务动作闭环") &&
    hasNegatedScopeTerm(statement, "完整上线验收") &&
    !hasPositiveKnowledgeOperationsAssetEntryCompleteScopeClaim(statement)
  );
}

function hasPositiveClinicalEntryCompleteScopeClaim(statement: string) {
  return [
    "完整临床流程",
    "34 个入口全部业务动作闭环",
    "完整S0-S40",
    "完整 S0-S40",
    "完整上线",
    "完整上线验收",
    "上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasPositiveQualityManagementEntryCompleteScopeClaim(statement: string) {
  return [
    "质量管理 4 个入口全部完整上线",
    "质量管理四个入口全部完整上线",
    "完整 DRG/DIP",
    "完整DRG/DIP",
    "完整医保支付审核",
    "完整 S9-S11",
    "完整S9-S11",
    "34 个入口全部业务动作闭环",
    "完整上线",
    "完整上线验收",
    "上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasPositiveKnowledgeOperationsAssetEntryCompleteScopeClaim(statement: string) {
  return [
    "全知识供给链完整上线",
    "完整全知识供给链",
    "完整 134",
    "134 完整上线",
    "13 类医学资产全部生产闭环",
    "十三类医学资产全部生产闭环",
    "所有医学知识和术语体系已收集完成",
    "完整 S0-S40",
    "完整S0-S40",
    "34 个入口全部业务动作闭环",
    "完整上线",
    "完整上线验收",
    "上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasPositivePlatformAdminEntryCompleteScopeClaim(statement: string) {
  return [
    "6 个平台管理员入口全部闭环",
    "六个平台管理员入口全部闭环",
    "34 个入口全部业务动作闭环",
    "完整上线",
    "完整上线验收",
    "上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompletePlatformAdminEntryCoreAction(
  value: unknown,
  expectedMenuKey: string,
  pathByMenuKey: Record<string, string> = requiredPlatformAdminEntryCoreActionPaths,
  serviceOperationsByMenuKey: Record<
    string,
    string[]
  > = requiredPlatformAdminEntryCoreActionServiceOperations,
) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  return (
    action.menuKey === expectedMenuKey &&
    action.role === "platform-admin" &&
    action.path === pathByMenuKey[expectedMenuKey] &&
    hasText(action.frontdeskAction) &&
    hasExpectedPlatformAdminEntryCoreActionServiceOperation(
      expectedMenuKey,
      textValue(action.serviceOperation),
      serviceOperationsByMenuKey,
    ) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true
  );
}

function hasExpectedPlatformAdminEntryCoreActionServiceOperation(
  expectedMenuKey: string,
  serviceOperation: string | null | undefined,
  serviceOperationsByMenuKey: Record<string, string[]>,
) {
  const requiredOperations = serviceOperationsByMenuKey[expectedMenuKey];
  return Boolean(
    serviceOperation &&
      requiredOperations?.every((operation) => serviceOperation.includes(operation)),
  );
}

function hasCompleteClinicalEntryCoreAction(value: unknown, expectedMenuKey: string) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  const serviceOperation = textValue(action.serviceOperation);
  return (
    action.menuKey === expectedMenuKey &&
    action.role === "clinical-user" &&
    action.path === requiredClinicalEntryCoreActionPaths[expectedMenuKey] &&
    hasText(action.frontdeskAction) &&
    hasExpectedClinicalEntryCoreActionServiceOperation(expectedMenuKey, serviceOperation) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true
  );
}

function hasCompleteQualityManagementEntryCoreAction(value: unknown, expectedMenuKey: string) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  const serviceOperation = textValue(action.serviceOperation);
  return (
    action.menuKey === expectedMenuKey &&
    action.role === "engine-operator" &&
    action.path === requiredQualityManagementEntryCoreActionPaths[expectedMenuKey] &&
    hasText(action.frontdeskAction) &&
    hasExpectedQualityManagementEntryCoreActionServiceOperation(
      expectedMenuKey,
      serviceOperation,
    ) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true &&
    (expectedMenuKey !== "qc-dashboard" || action.sourceAuditVerified === true)
  );
}

function hasCompleteKnowledgeOperationsAssetEntryCoreAction(
  value: unknown,
  expectedMenuKey: string,
) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  const serviceOperation = textValue(action.serviceOperation);
  return (
    action.menuKey === expectedMenuKey &&
    action.role === "engine-operator" &&
    action.path === requiredKnowledgeOperationsAssetEntryActionPaths[expectedMenuKey] &&
    hasText(action.frontdeskAction) &&
    hasExpectedKnowledgeOperationsAssetEntryActionServiceOperation(
      expectedMenuKey,
      serviceOperation,
    ) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true &&
    hasRequiredKnowledgeOperationsActionGateFlags(action, expectedMenuKey)
  );
}

function hasExpectedClinicalEntryCoreActionServiceOperation(
  expectedMenuKey: string,
  serviceOperation: string | null,
) {
  if (!serviceOperation) return false;
  return (
    requiredClinicalEntryCoreActionServiceOperations[expectedMenuKey]?.every((expected) =>
      serviceOperation.includes(expected),
    ) === true
  );
}

function hasExpectedQualityManagementEntryCoreActionServiceOperation(
  expectedMenuKey: string,
  serviceOperation: string | null,
) {
  if (!serviceOperation) return false;
  return (
    requiredQualityManagementEntryCoreActionServiceOperations[expectedMenuKey]?.every((expected) =>
      serviceOperation.includes(expected),
    ) === true
  );
}

function hasExpectedKnowledgeOperationsAssetEntryActionServiceOperation(
  expectedMenuKey: string,
  serviceOperation: string | null,
) {
  if (!serviceOperation) return false;
  return (
    requiredKnowledgeOperationsAssetEntryActionServiceOperations[expectedMenuKey]?.every(
      (expected) => serviceOperation.includes(expected),
    ) === true
  );
}

function hasCompleteKnowledgeOperationsFormalChain(value: unknown) {
  const chain = recordValue(value);
  return (
    chain?.officialProductionInside134 === true &&
    chain.externalSourcesPreparatoryOnly === true &&
    chain.modelDirectPublishBlocked === true
  );
}

function hasCompleteKnowledgeOperationsSupplyChainGates(value: unknown) {
  const gates = recordValue(value);
  return (
    gates?.standardPackageImportVerified === true &&
    gates.hospitalDictionarySyncVerified === true &&
    gates.declarativeMaintenanceVerified === true &&
    gates.humanReviewVerified === true &&
    gates.institutionEffectiveRuntimeVerified === true &&
    gates.runtimeConsumerReadbackVerified === true &&
    gates.rollbackReadbackVerified === true
  );
}

function hasCompleteKnowledgeSupplyChainEvidence(value: unknown) {
  const evidence = recordValue(value);
  const sourceControl = recordValue(evidence?.sourceControl);
  const humanGovernance = recordValue(evidence?.humanGovernance);
  const terminologySync = recordValue(evidence?.terminologySync);
  const runtimeLifecycle = recordValue(evidence?.runtimeLifecycle);
  const lineageConsumers = recordValue(evidence?.lineageConsumers);
  const safetyBoundary = recordValue(evidence?.safetyBoundary);
  return (
    sourceControl?.sourceRegistered === true &&
    sourceControl.sourceVersionRegistered === true &&
    sourceControl.sourceFragmentRegistered === true &&
    sourceControl.uploadParseJobSucceeded === true &&
    isPositiveFiniteNumber(sourceControl.parseResultSourceVersionId) &&
    isPositiveFiniteNumber(sourceControl.parsedFragmentCount) &&
    hasPositiveFiniteNumberArray(sourceControl.sourceFragmentIds) &&
    parsedFragmentCountCoversIds(
      sourceControl.parsedFragmentCount,
      sourceControl.sourceFragmentIds,
    ) &&
    sourceControl.citationBound === true &&
    sourceControl.textExcerptVerified === true &&
    sourceControl.qualityGateRecordCreated === true &&
    humanGovernance?.reviewQueueRead === true &&
    humanGovernance.candidateApproved === true &&
    humanGovernance.noDirectPublishVerified === true &&
    terminologySync?.standardTermRegistered === true &&
    terminologySync.localTermRegistered === true &&
    terminologySync.candidateGenerated === true &&
    terminologySync.mappingConfirmed === true &&
    terminologySync.terminologyAssetVersionCreated === true &&
    runtimeLifecycle?.baselineAssetsPreserved === true &&
    runtimeLifecycle.hospitalRuntimeActivated === true &&
    runtimeLifecycle.runtimeConsumerReadbackVerified === true &&
    runtimeLifecycle.rollbackReadbackVerified === true &&
    lineageConsumers?.provenanceReadbackVerified === true &&
    lineageConsumers.graphProjectionVerified === true &&
    lineageConsumers.sourceAuditVerified === true &&
    safetyBoundary?.externalSourcesPreparatoryOnly === true &&
    safetyBoundary.modelDirectPublishBlocked === true &&
    safetyBoundary.noAutoClinicalAction === true
  );
}

function isPositiveFiniteNumber(value: unknown) {
  const parsed = numberValue(value);
  return typeof parsed === "number" && parsed > 0;
}

function hasPositiveFiniteNumberArray(value: unknown) {
  return (
    Array.isArray(value) && value.length > 0 && value.every((item) => isPositiveFiniteNumber(item))
  );
}

function parsedFragmentCountCoversIds(count: unknown, ids: unknown) {
  const parsed = numberValue(count);
  if (
    typeof parsed !== "number" ||
    !Number.isInteger(parsed) ||
    parsed <= 0 ||
    !Array.isArray(ids)
  ) {
    return false;
  }
  const uniqueIds = new Set(ids.map((item) => numberValue(item)));
  return uniqueIds.size === ids.length && uniqueIds.size === parsed;
}

function hasRequiredKnowledgeOperationsActionGateFlags(
  action: Record<string, unknown>,
  expectedMenuKey: string,
) {
  switch (expectedMenuKey) {
    case "knowledge-production":
      return action.sourceLineageVerified === true;
    case "knowledge-governance":
      return action.humanReviewVerified === true && action.noDirectPublishVerified === true;
    case "runtime-releases":
      return (
        action.runtimeActivationVerified === true &&
        action.runtimeConsumerReadbackVerified === true &&
        action.rollbackReadbackVerified === true
      );
    case "terminology-mapping":
      return action.localDictionarySyncVerified === true && action.assetVersionVerified === true;
    case "rule-definitions":
    case "pathway-templates":
      return action.declarativeMaintenanceVerified === true;
    case "institution-knowledge":
      return action.institutionScopeVerified === true && action.platformRestoreVerified === true;
    case "diagnosis-knowledge":
      return action.humanReviewVerified === true && action.sourceEvidenceVerified === true;
    case "provenance":
      return action.sourceAuditVerified === true && action.sourceLineageVerified === true;
    case "graph-explore":
      return action.graphProjectionVerified === true && action.sourceLineageVerified === true;
    case "ai-workflows":
      return action.modelSafetyBoundaryVerified === true && action.noDirectPublishVerified === true;
    default:
      return false;
  }
}

function hasRequiredSystemFamilyAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "third-party-system-family-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      systemFamilyCodes?: unknown;
      consumerEvidence?: unknown;
    };
    if (!Array.isArray(parsed.systemFamilyCodes)) return false;
    const observed = parsed.systemFamilyCodes
      .filter((code): code is string => typeof code === "string")
      .sort();
    return (
      JSON.stringify(observed) ===
        JSON.stringify([...requiredThirdPartySystemFamilyCodes].sort()) &&
      hasCompleteThirdPartySystemFamilyConsumerEvidence(parsed.consumerEvidence)
    );
  } catch {
    return false;
  }
}

function collectThirdPartySystemFamilyDegradationClaims(tests: BrowserE2eTestResult[]) {
  return tests.some(
    (test) =>
      path.basename(test.file) === "third-party-system-families-rehearsal.spec.ts" &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      test.title.includes("逐类登记第三方系统族接入并验证断连诚实降级") &&
      hasCompleteThirdPartySystemFamilyDegradationEvidence(test),
  )
    ? thirdPartySystemFamilyDegradationClaims
    : [];
}

function hasCompleteThirdPartySystemFamilyDegradationEvidence(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "third-party-system-family-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      systemFamilyCodes?: unknown;
      consumerEvidence?: unknown;
      registrationEvidence?: unknown;
      scopeStatement?: unknown;
    };
    if (!Array.isArray(parsed.systemFamilyCodes)) return false;
    const observed = parsed.systemFamilyCodes
      .filter((code): code is string => typeof code === "string")
      .sort();
    return (
      JSON.stringify(observed) ===
        JSON.stringify([...requiredThirdPartySystemFamilyCodes].sort()) &&
      hasNegatedScopeTerm(textValue(parsed.scopeStatement) ?? "", "完整断连降级") &&
      hasCompleteThirdPartySystemFamilyDegradationRows(parsed.consumerEvidence) &&
      hasCompleteThirdPartySystemFamilyRegistrationEvidence(parsed.registrationEvidence)
    );
  } catch {
    return false;
  }
}

function hasCompleteThirdPartySystemFamilyRegistrationEvidence(value: unknown) {
  const evidence = recordValue(value);
  if (!evidence) return false;
  return (
    Number(evidence.adapterTotal) >= requiredThirdPartySystemFamilyCodes.length &&
    Number(evidence.notConnectedCount) >= 1 &&
    hasText(evidence.gapSummary) &&
    /适配器|NOT_CONNECTED|MISCONFIGURED|缺口|未接通/u.test(String(evidence.gapSummary)) &&
    ["NOT_CONNECTED", "MISCONFIGURED", "UNHEALTHY", "HEALTHY"].includes(
      textValue(evidence.sampledHealthStatus) ?? "",
    )
  );
}

function hasCompleteThirdPartySystemFamilyDegradationRows(value: unknown) {
  if (!Array.isArray(value)) return false;
  const evidenceByFamily = new Map<string, Record<string, unknown>>();
  for (const item of value) {
    const evidence = recordValue(item);
    const code = textValue(evidence?.systemFamilyCode);
    if (evidence && code) {
      evidenceByFamily.set(code, evidence);
    }
  }
  return requiredThirdPartySystemFamilyCodes.every((code) => {
    const evidence = evidenceByFamily.get(code);
    const status = textValue(evidence?.healthStatus);
    return (
      evidence !== undefined &&
      hasText(evidence.onboardingId) &&
      hasText(evidence.adapterId) &&
      evidence.degradationVerified === true &&
      evidence.auditVerified === true &&
      status !== "HEALTHY" &&
      ["NOT_CONNECTED", "MISCONFIGURED", "RETRYING", "DEAD_LETTER", "UNHEALTHY"].includes(
        status ?? "",
      )
    );
  });
}

function collectThirdPartySystemFamilyScenarioConditionClaims(tests: BrowserE2eTestResult[]) {
  const claims = new Set<string>();
  for (const test of tests) {
    if (
      path.basename(test.file) !== "third-party-system-families-rehearsal.spec.ts" ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected" ||
      !test.title.includes("逐类登记第三方系统族接入并验证断连诚实降级")
    ) {
      continue;
    }
    const attachment = test.attachments?.find(
      (item) => item.name === "third-party-system-family-codes",
    );
    if (!attachment?.body) continue;
    try {
      const parsed = recordValue(JSON.parse(attachment.body));
      const rows = collectStrictScenarioConditionRows(
        parsed?.scenarioConditionEvidence,
        thirdPartySystemFamilyScenarioConditionRows,
      );
      if (!parsed || rows === null) continue;
      for (const expected of thirdPartySystemFamilyScenarioConditionRows) {
        if (
          rows.has(expected.code) &&
          thirdPartySystemFamilyScenarioConditionBackedByEvidence(expected.code, parsed)
        ) {
          claims.add(`scenarioConditionRows:${expected.code}`);
        }
      }
    } catch {
      continue;
    }
  }
  return [...claims];
}

function thirdPartySystemFamilyScenarioConditionBackedByEvidence(
  code: (typeof thirdPartySystemFamilyScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S33__MISSING_DATA":
      return hasCompleteSpdUdiDeviceMissingEvidence(parsed);
    case "S34__MISSING_DATA":
      return hasCompleteResearchEthicsDataMissingEvidence(parsed);
    default:
      return false;
  }
}

function hasCompleteSpdUdiDeviceMissingEvidence(parsed: Record<string, unknown>) {
  if (
    !Array.isArray(parsed.systemFamilyCodes) ||
    !parsed.systemFamilyCodes.includes("SPD_UDI_DEVICE")
  ) {
    return false;
  }
  const scopeStatement = textValue(parsed.scopeStatement) ?? "";
  if (
    !hasNegatedScopeTerm(scopeStatement, "真实消费者") ||
    !hasNegatedScopeTerm(scopeStatement, "标准资源") ||
    !hasNegatedScopeTerm(scopeStatement, "闭环回传") ||
    !hasNegatedScopeTerm(scopeStatement, "完整器械耗材") ||
    !hasNegatedScopeTerm(scopeStatement, "完整 S33")
  ) {
    return false;
  }
  const registration = recordValue(parsed.registrationEvidence);
  const missing = recordValue(parsed.spdUdiDeviceMissingEvidence);
  if (
    !hasCompleteThirdPartySystemFamilyRegistrationEvidence(registration) ||
    !hasCompleteSpdUdiDeviceMissingRow(missing)
  ) {
    return false;
  }
  const consumerRows = Array.isArray(parsed.consumerEvidence) ? parsed.consumerEvidence : [];
  const consumer = consumerRows
    .map((item) => recordValue(item))
    .find(
      (item): item is Record<string, unknown> =>
        item !== null && item.systemFamilyCode === "SPD_UDI_DEVICE",
    );
  return (
    consumer !== undefined &&
    consumer.onboardingId === missing?.onboardingId &&
    consumer.adapterId === missing?.adapterId &&
    consumer.consumerVerified === false &&
    consumer.standardResourceVerified === false &&
    consumer.degradationVerified === true &&
    consumer.auditVerified === true &&
    consumer.healthStatus === missing?.healthStatus
  );
}

function hasCompleteSpdUdiDeviceMissingRow(value: Record<string, unknown> | null) {
  if (!value) return false;
  const status = textValue(value.healthStatus);
  const missingCapabilities = Array.isArray(value.missingCapabilities)
    ? value.missingCapabilities
    : [];
  return (
    value.systemFamilyCode === "SPD_UDI_DEVICE" &&
    hasText(value.onboardingId) &&
    hasText(value.adapterId) &&
    value.consumerVerified === false &&
    value.standardResourceVerified === false &&
    value.degradationVerified === true &&
    value.auditVerified === true &&
    status !== "HEALTHY" &&
    ["NOT_CONNECTED", "MISCONFIGURED", "RETRYING", "DEAD_LETTER", "UNHEALTHY"].includes(
      status ?? "",
    ) &&
    [
      "UDI_TRACEABILITY",
      "DEVICE_RECALL_STOP_USE",
      "TECHNOLOGY_ACCESS_APPROVAL",
      "CONSUMABLE_USAGE_AUDIT",
    ].every((capability) => missingCapabilities.includes(capability))
  );
}

function hasCompleteResearchEthicsDataMissingEvidence(parsed: Record<string, unknown>) {
  if (
    !Array.isArray(parsed.systemFamilyCodes) ||
    !parsed.systemFamilyCodes.includes("RESEARCH_ETHICS_DATA")
  ) {
    return false;
  }
  const scopeStatement = textValue(parsed.scopeStatement) ?? "";
  if (
    !hasNegatedScopeTerm(scopeStatement, "真实消费者") ||
    !hasNegatedScopeTerm(scopeStatement, "标准资源") ||
    !hasNegatedScopeTerm(scopeStatement, "闭环回传") ||
    !hasNegatedScopeTerm(scopeStatement, "完整科研数据服务") ||
    !hasNegatedScopeTerm(scopeStatement, "完整 S34")
  ) {
    return false;
  }
  const registration = recordValue(parsed.registrationEvidence);
  const missing = recordValue(parsed.researchEthicsDataMissingEvidence);
  if (
    !hasCompleteThirdPartySystemFamilyRegistrationEvidence(registration) ||
    !hasCompleteResearchEthicsMissingRow(missing)
  ) {
    return false;
  }
  const consumerRows = Array.isArray(parsed.consumerEvidence) ? parsed.consumerEvidence : [];
  const consumer = consumerRows
    .map((item) => recordValue(item))
    .find(
      (item): item is Record<string, unknown> =>
        item !== null && item.systemFamilyCode === "RESEARCH_ETHICS_DATA",
    );
  return (
    consumer !== undefined &&
    consumer.onboardingId === missing?.onboardingId &&
    consumer.adapterId === missing?.adapterId &&
    consumer.consumerVerified === false &&
    consumer.standardResourceVerified === false &&
    consumer.degradationVerified === true &&
    consumer.auditVerified === true &&
    consumer.healthStatus === missing?.healthStatus
  );
}

function hasCompleteResearchEthicsMissingRow(value: Record<string, unknown> | null) {
  if (!value) return false;
  const status = textValue(value.healthStatus);
  const missingCapabilities = Array.isArray(value.missingCapabilities)
    ? value.missingCapabilities
    : [];
  return (
    value.systemFamilyCode === "RESEARCH_ETHICS_DATA" &&
    hasText(value.onboardingId) &&
    hasText(value.adapterId) &&
    value.consumerVerified === false &&
    value.standardResourceVerified === false &&
    value.degradationVerified === true &&
    value.auditVerified === true &&
    status !== "HEALTHY" &&
    ["NOT_CONNECTED", "MISCONFIGURED", "RETRYING", "DEAD_LETTER", "UNHEALTHY"].includes(
      status ?? "",
    ) &&
    ["DE_IDENTIFIED_COHORT", "ETHICS_AUTHORIZATION", "DATASET_EXPORT", "USAGE_AUDIT"].every(
      (capability) => missingCapabilities.includes(capability),
    )
  );
}

function hasCompleteThirdPartySystemFamilyConsumerEvidence(value: unknown) {
  if (!Array.isArray(value)) return false;
  const evidenceByFamily = new Map<string, Record<string, unknown>>();
  for (const item of value) {
    const evidence = recordValue(item);
    const code = textValue(evidence?.systemFamilyCode);
    if (evidence && code) {
      evidenceByFamily.set(code, evidence);
    }
  }
  return requiredThirdPartySystemFamilyCodes.every((code) => {
    const evidence = evidenceByFamily.get(code);
    return (
      evidence !== undefined &&
      hasText(evidence.onboardingId) &&
      hasText(evidence.adapterId) &&
      evidence.consumerVerified === true &&
      evidence.standardResourceVerified === true &&
      evidence.degradationVerified === true &&
      evidence.auditVerified === true &&
      ["NOT_CONNECTED", "MISCONFIGURED", "RETRYING", "DEAD_LETTER", "HEALTHY"].includes(
        textValue(evidence.healthStatus) ?? "",
      )
    );
  });
}

function hasRequiredS2S4RuntimeMappingAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find((item) => item.name === "s2-s4-runtime-mapping-codes");
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      apiEvidence?: unknown;
      adapter?: unknown;
      terminology?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      inboundResult?: unknown;
      runtimeConsumerReadback?: unknown;
      scenarioEvidence?: unknown;
    };
    const terminology = parseS2S4TerminologyEvidence(parsed.terminology);
    const runtime = parseS2S4RuntimeEvidence(parsed.runtime, terminology);
    if (
      !arrayEquals(parsed.scenarioCodes, requiredS2S4RuntimeMappingScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["DATA_INTEROPERABILITY", "MEDICAL_ASSET"]) ||
      !arrayEquals(parsed.versionedAssets, ["TERMINOLOGY"]) ||
      !arrayEquals(parsed.deliveryShapes, ["MANAGEMENT_WORKSPACE", "API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME"]) ||
      !hasCompleteS2S4RuntimeMappingApiEvidence(parsed.apiEvidence) ||
      !hasCompleteS2S4AdapterEvidence(parsed.adapter, terminology) ||
      !terminology ||
      !runtime ||
      !hasCompleteS2S4ActivationRequest(parsed.activationRequest, terminology) ||
      !hasCompleteS2S4InboundResult(parsed.inboundResult, terminology, runtime.releaseId) ||
      !hasCompleteS2S4RuntimeConsumerReadback(
        parsed.runtimeConsumerReadback,
        terminology,
        runtime,
      ) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredS2S4RuntimeMappingScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredS2S4RuntimeMappingScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function collectS2S4ScenarioConditionClaims(tests: BrowserE2eTestResult[]) {
  return tests.some(
    (test) =>
      path.basename(test.file) === "s2-s4-terminology-integration-rehearsal.spec.ts" &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      test.title.includes("真实入站消息按当前机构生效版本归一") &&
      hasCompleteS2S4ScenarioConditionEvidence(test),
  )
    ? s2s4ScenarioConditionRows.map((row) => `scenarioConditionRows:${row.code}`)
    : [];
}

function hasCompleteS2S4ScenarioConditionEvidence(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find((item) => item.name === "s2-s4-runtime-mapping-codes");
  if (!attachment?.body || !hasRequiredS2S4RuntimeMappingAttachment(test)) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      apiEvidence?: unknown;
      scenarioConditionEvidence?: unknown;
    };
    const apiEvidence = recordValue(parsed.apiEvidence);
    if (!apiEvidence || !Array.isArray(parsed.scenarioConditionEvidence)) return false;
    const rows = new Map<string, Record<string, unknown>>();
    for (const item of parsed.scenarioConditionEvidence) {
      const row = recordValue(item);
      const code = textValue(row?.code);
      const knownRow = s2s4ScenarioConditionRows.some((expected) => expected.code === code);
      if (!row || !code || !knownRow || rows.has(code)) return false;
      rows.set(code, row);
    }
    return (
      rows.size === s2s4ScenarioConditionRows.length &&
      s2s4ScenarioConditionRows.every((expected) => {
        const row = rows.get(expected.code);
        return (
          row !== undefined &&
          row.scenarioCode === expected.scenarioCode &&
          row.condition === expected.condition &&
          row.source === expected.source &&
          hasNonEmptyTextArray(row.evidence) &&
          s2s4ScenarioConditionBackedByApiEvidence(expected.code, apiEvidence)
        );
      })
    );
  } catch {
    return false;
  }
}

function s2s4ScenarioConditionBackedByApiEvidence(
  code: (typeof s2s4ScenarioConditionRows)[number]["code"],
  apiEvidence: Record<string, unknown>,
) {
  switch (code) {
    case "S2__NORMAL":
      return (
        apiEvidence.inboundWebhookAccepted === true &&
        apiEvidence.inboundNormalizedByRuntimeRelease === true
      );
    case "S2__ABNORMAL":
      return apiEvidence.invalidInboundWebhookSignatureRejected === true;
    case "S4__NORMAL":
      return (
        apiEvidence.standardTermRegisteredFromFrontdesk === true &&
        apiEvidence.localTermRegisteredThroughSignedSync === true &&
        apiEvidence.candidateGeneratedFromFrontdesk === true &&
        apiEvidence.candidateConfirmedFromFrontdesk === true &&
        apiEvidence.runtimeContractReadbackMatched === true
      );
    case "S4__ABNORMAL":
      return apiEvidence.invalidMasterDataSignatureRejected === true;
    default:
      return false;
  }
}

function collectFrontdeskScenarioConditionClaims(tests: BrowserE2eTestResult[]) {
  const claims = new Set<string>();
  for (const test of tests) {
    for (const claim of collectDashboardWorkbenchScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectRuntimeReleaseScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectServiceOrganizationScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectIdentityBindingScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectMfaLoginScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectDiagnosisKnowledgeScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectDiagnosisAssistRuntimeScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectImplementationGuideScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectSourceLineageScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectEmbedBusinessHostScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectPathwayLifecycleScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectCdssDeclarativeRuntimeAssetScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectMedicationSafetyScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectQualityManagementScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectClinicalEntryScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectRealFrontdeskScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectDiagnosticCriticalValueScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectRegionalDiagnosticMutualRecognitionScenarioConditionClaimsFromTest(
      test,
    )) {
      claims.add(claim);
    }
    for (const claim of collectNursingContinuityScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectSurgeryAnesthesiaTransfusionScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectPharmacyReviewAntimicrobialScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectInfectionPublicHealthSafetyScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectCriticalEmergencyIcuScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
    for (const claim of collectReportInterpretationScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
  }
  return [...claims];
}

function collectDashboardWorkbenchScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "product-role-journeys.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find((item) =>
    item.name.startsWith("dashboard-workbench-core-actions-codes-"),
  );
  if (!attachment?.body || !hasRequiredDashboardWorkbenchCoreActionsAttachment([test])) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      dashboardWorkbenchScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return dashboardWorkbenchScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          dashboardWorkbenchScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function dashboardWorkbenchScenarioConditionBackedByEvidence(
  code: (typeof dashboardWorkbenchScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S0__NORMAL":
      return (
        hasCompleteDashboardWorkbenchPermissionBoundaryEvidence(
          parsed.permissionBoundaryEvidence,
        ) && hasCompleteDashboardWorkbenchSixStateEvidence(parsed.sixStateEvidence)
      );
    default:
      return false;
  }
}

function collectRuntimeReleaseScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "runtime-release-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "runtime-release-coverage-codes",
  );
  if (
    !attachment?.body ||
    !hasRequiredRuntimeReleaseAttachment(test) ||
    !hasRequiredRuntimeReleasePartialSelectionAttachment(test)
  ) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      runtimeReleaseScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return runtimeReleaseScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          runtimeReleaseScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function collectMultiHospitalRuntimeIsolationClaims(tests: BrowserE2eTestResult[]) {
  return tests.flatMap((test) => collectMultiHospitalRuntimeIsolationClaimsFromTest(test));
}

function collectMultiHospitalRuntimeIsolationClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "runtime-release-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "runtime-release-coverage-codes",
  );
  if (
    !attachment?.body ||
    !hasRequiredRuntimeReleaseAttachment(test) ||
    !hasRequiredRuntimeReleasePartialSelectionAttachment(test)
  ) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    return parsed &&
      hasCompleteRuntimeReleaseMultiHospitalEvidence(parsed.multiHospitalDifferentiation)
      ? multiHospitalRuntimeIsolationClaims
      : [];
  } catch {
    return [];
  }
}

function runtimeReleaseScenarioConditionBackedByEvidence(
  code: (typeof runtimeReleaseScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S13__NORMAL": {
      if (
        !isPositiveNumber(parsed.activatedRevisionNo) ||
        !isPositiveNumber(parsed.rolledBackRevisionNo)
      ) {
        return false;
      }
      const activatedRevisionNo = Number(parsed.activatedRevisionNo);
      const rolledBackRevisionNo = Number(parsed.rolledBackRevisionNo);
      return (
        rolledBackRevisionNo > activatedRevisionNo &&
        hasCompleteRuntimeReleaseApiEvidence(parsed.apiEvidence) &&
        hasCompleteRuntimeReleaseLocalCandidateEvidence(parsed) &&
        hasCompleteRuntimeReleasePartialSelectionEvidence(parsed) &&
        hasCompleteRuntimeReleaseMultiHospitalEvidence(parsed.multiHospitalDifferentiation) &&
        hasCompleteRuntimeReleasePlatformUpgradeEvidence(parsed.platformUpgradeAnalysis) &&
        hasCompleteRuntimeReleaseOfflineDeliveryEvidence(parsed)
      );
    }
    case "S13__DEGRADATION":
      return hasCompleteRuntimeReleaseRollbackDegradationEvidence(parsed);
    default:
      return false;
  }
}

function collectServiceOrganizationScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "service-organization-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "service-organization-scenario-codes",
  );
  if (!attachment?.body || !hasRequiredServiceOrganizationScenarioAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      serviceOrganizationScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return serviceOrganizationScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          serviceOrganizationScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function serviceOrganizationScenarioConditionBackedByEvidence(
  code: (typeof serviceOrganizationScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S1__NORMAL":
      return (
        hasCompleteServiceOrganizationOnboardingEvidence(parsed.onboardingEvidence) &&
        hasCompleteServiceOrganizationAdminBootstrapEvidence(
          parsed.adminBootstrapEvidence,
          parsed.onboardingEvidence,
        ) &&
        hasCompleteServiceOrganizationOrgTreeEvidence(
          parsed.orgTreeEvidence,
          parsed.onboardingEvidence,
        )
      );
    default:
      return false;
  }
}

function collectIdentityBindingScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "identity-binding-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "identity-binding-scenario-codes",
  );
  if (!attachment?.body || !hasRequiredIdentityBindingAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      identityBindingScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return identityBindingScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          identityBindingScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function identityBindingScenarioConditionBackedByEvidence(
  code: (typeof identityBindingScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const binding = parseIdentityBindingEvidence(parsed.binding);
  if (!binding) return false;
  switch (code) {
    case "S14__NORMAL":
      return (
        hasCompleteIdentityBindingApiEvidence(parsed.apiEvidence) &&
        hasCompleteIdentityBindingCreatedPersonnel(parsed.createdPersonnel, binding.userId) &&
        hasCompleteIdentityPlaintextSafetyEvidence(parsed.plaintextSafety) &&
        hasCompleteIdentityUnbindingEvidence(parsed.unbinding, binding) &&
        hasCompleteIdentityCleanupEvidence(parsed.cleanup)
      );
    default:
      return false;
  }
}

function collectMfaLoginScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "mfa-login-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find((item) => item.name === "mfa-login-scenario-codes");
  if (!attachment?.body || !hasRequiredMfaLoginScenarioAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      mfaLoginScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return mfaLoginScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          mfaLoginScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function mfaLoginScenarioConditionBackedByEvidence(
  code: (typeof mfaLoginScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S14__HIGH_RISK":
      return (
        hasCompleteMfaLoginApiEvidence(parsed.apiEvidence) &&
        hasCompleteMfaLoginConfigEvidence(parsed.configEvidence) &&
        hasCompleteMfaTemporaryAdminEvidence(parsed.temporaryAdmin) &&
        hasCompleteMfaBindingEvidence(parsed.mfaBinding) &&
        hasCompleteMfaLoginChallengeEvidence(parsed.loginChallenge) &&
        hasCompleteMfaVerificationEvidence(parsed.verification) &&
        hasCompleteMfaProfileEvidence(parsed.profile, parsed.temporaryAdmin)
      );
    default:
      return false;
  }
}

function collectDiagnosisKnowledgeScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "diagnosis-knowledge-maintenance.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnosis-knowledge-scenario-codes",
  );
  if (!attachment?.body || !hasRequiredDiagnosisKnowledgeScenarioAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      diagnosisKnowledgeScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return diagnosisKnowledgeScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          diagnosisKnowledgeScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function diagnosisKnowledgeScenarioConditionBackedByEvidence(
  code: (typeof diagnosisKnowledgeScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S3__NORMAL":
      return hasCompleteDiagnosisKnowledgeStructuredEvidence(parsed);
    case "S3__ABNORMAL":
      return hasCompleteDiagnosisKnowledgeInvalidAssetCreationRejectionEvidence(parsed);
    default:
      return false;
  }
}

function collectDiagnosisAssistRuntimeScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "diagnosis-assist-runtime.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnosis-assist-runtime-codes",
  );
  if (!attachment?.body) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      diagnosisAssistRuntimeScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return diagnosisAssistRuntimeScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) && hasCompleteDiagnosisAssistRuntimeNormalEvidence(parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function collectImplementationGuideScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "stakeholder-view-rehearsal.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "implementation-guide-entry-core-actions-codes",
  );
  if (!attachment?.body) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      implementationGuideScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return implementationGuideScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          implementationGuideScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function implementationGuideScenarioConditionBackedByEvidence(
  code: (typeof implementationGuideScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S23__ABNORMAL":
      return hasCompleteImplementationGuideDataQualityGapEvidence(parsed);
    default:
      return false;
  }
}

function collectSourceLineageScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "d6-graph-explore.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "source-lineage-scenario-codes",
  );
  if (!attachment?.body || !hasRequiredSourceLineageAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      sourceLineageScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return sourceLineageScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          sourceLineageScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function sourceLineageScenarioConditionBackedByEvidence(
  code: (typeof sourceLineageScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S7__NORMAL":
      return hasCompleteSourceLineageStructuredEvidence(parsed);
    default:
      return false;
  }
}

function collectEmbedBusinessHostScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "embed-business-host.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "embed-business-host-launch-codes",
  );
  if (!attachment?.body || !hasRequiredEmbedBusinessHostAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      embedBusinessHostScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return embedBusinessHostScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          embedBusinessHostScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function embedBusinessHostScenarioConditionBackedByEvidence(
  code: (typeof embedBusinessHostScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S8__DEGRADATION":
      return hasCompleteEmbedBusinessHostDegradationEvidence(parsed);
    default:
      return false;
  }
}

function collectPathwayLifecycleScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "pathway-lifecycle-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "pathway-lifecycle-scenario-codes",
  );
  if (!attachment?.body || !hasRequiredPathwayLifecycleAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      pathwayLifecycleScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return pathwayLifecycleScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          pathwayLifecycleScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function pathwayLifecycleScenarioConditionBackedByEvidence(
  code: (typeof pathwayLifecycleScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S6__NORMAL":
      return (
        hasCompletePathwayLifecycleApiEvidence(parsed.apiEvidence) &&
        hasCompletePathwayOrderSetRuntimeConsumerEvidence(parsed.orderSetRuntimeConsumer)
      );
    default:
      return false;
  }
}

function collectCdssDeclarativeRuntimeAssetScenarioConditionClaimsFromTest(
  test: BrowserE2eTestResult,
) {
  if (
    path.basename(test.file) !== "cdss-runtime-declarative-assets.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "cdss-runtime-declarative-assets-codes",
  );
  if (!attachment?.body || !hasRequiredCdssDeclarativeRuntimeAssetAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      cdssDeclarativeRuntimeAssetScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return cdssDeclarativeRuntimeAssetScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          cdssDeclarativeRuntimeAssetScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function cdssDeclarativeRuntimeAssetScenarioConditionBackedByEvidence(
  code: (typeof cdssDeclarativeRuntimeAssetScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S5__NORMAL":
      return hasCompleteCdssDeclarativeRuntimeEvidence(parsed);
    case "S5__DEGRADATION":
      return hasCompleteCdssDeclarativeModelDisabledRuntimeEvidence(parsed);
    default:
      return false;
  }
}

function hasCompleteCdssDeclarativeRuntimeEvidence(parsed: Record<string, unknown>) {
  const runtime = parseCdssDeclarativeRuntimeEvidence(parsed.runtime);
  const createdAssets = parseCdssDeclarativeCreatedAssets(parsed.createdAssets);
  const ruleRuntimeCandidate = parseCdssRuntimeRuleAsset(parsed.ruleRuntimeCandidate);
  if (!runtime || !createdAssets || !ruleRuntimeCandidate) return false;
  return (
    hasCompleteCdssDeclarativeRuntimeApiEvidence(parsed.apiEvidence) &&
    cdssRuntimeRuleMatchesRuntime(ruleRuntimeCandidate, runtime.ruleAsset) &&
    cdssDeclarativeCreatedAssetsMatchRuntime(createdAssets, runtime.assets) &&
    hasCompleteCdssDeclarativeActivationRequest(
      parsed.activationRequest,
      runtime.assets,
      ruleRuntimeCandidate,
    ) &&
    hasCompleteCdssDeclarativeTriggerEvidence(parsed.clinicalTrigger, runtime.releaseId) &&
    hasCompleteCdssDeclarativeRecommendationEvidence(
      parsed.recommendation,
      {
        releaseId: runtime.releaseId,
        assets: runtime.assets,
        ruleAsset: runtime.ruleAsset,
      },
      parsed.clinicalTrigger,
      ruleRuntimeCandidate,
    )
  );
}

function hasCompleteCdssDeclarativeModelDisabledRuntimeEvidence(parsed: Record<string, unknown>) {
  const recommendation = recordValue(parsed.recommendation);
  const apiEvidence = recordValue(parsed.apiEvidence);
  return (
    recommendation?.modelStatus === "MODEL_DISABLED" &&
    apiEvidence?.recommendationModelDisabledFromRealService === true &&
    hasCompleteCdssDeclarativeRuntimeEvidence(parsed)
  );
}

function collectMedicationSafetyScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "medication-safety-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "medication-safety-frontdesk-codes",
  );
  if (!attachment?.body || !hasRequiredMedicationSafetyFrontdeskAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      medicationSafetyScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return medicationSafetyScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          medicationSafetyScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .flatMap((row) =>
        row.code === "S28__HIGH_RISK"
          ? [`scenarios:${row.scenarioCode}`, `scenarioConditionRows:${row.code}`]
          : [`scenarioConditionRows:${row.code}`],
      );
  } catch {
    return [];
  }
}

function medicationSafetyScenarioConditionBackedByEvidence(
  code: (typeof medicationSafetyScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const assets = parseMedicationSafetyRuntimeEvidence(parsed.runtime);
  switch (code) {
    case "S5__HIGH_RISK":
      return (
        hasCompleteMedicationSafetyApiEvidence(parsed.apiEvidence) &&
        hasCompleteMedicationSafetyRiskMatrix(parsed.riskMatrix) &&
        hasCompleteMedicationSafetyRedline(parsed.safetyRedline, parsed.riskMatrix) &&
        hasCompleteMedicationSafetyClinicalContext(
          parsed.clinicalContext,
          String(recordValue(parsed.runtime)?.releaseId ?? ""),
        ) &&
        hasCompleteMedicationSafetyRecommendationEvidence(
          parsed.recommendation,
          { releaseId: String(recordValue(parsed.runtime)?.releaseId ?? "") },
          parsed.clinicalTrigger,
          parsed.riskMatrix,
          parsed.safetyRedline,
        ) &&
        hasCompleteMedicationSafetyFeedbackEvidence(parsed.feedback)
      );
    case "S28__HIGH_RISK":
      return (
        hasCompleteMedicationSafetyApiEvidence(parsed.apiEvidence) &&
        hasCompleteMedicationSafetyRiskMatrix(parsed.riskMatrix) &&
        hasCompleteMedicationSafetyClinicalContext(
          parsed.clinicalContext,
          String(recordValue(parsed.runtime)?.releaseId ?? ""),
        ) &&
        hasCompleteMedicationSafetySpecialPopulationBoundary(parsed.scopeStatement) &&
        hasCompleteMedicationSafetySpecialPopulationClinicalContext(parsed.clinicalContext) &&
        hasCompleteMedicationSafetySpecialPopulationRedline(
          parsed.specialPopulationRedline,
          parsed.riskMatrix,
          assets ?? undefined,
          parsed.activationRequest,
        ) &&
        hasCompleteMedicationSafetySpecialPopulationRecommendation(
          parsed.specialPopulationRecommendation,
          { releaseId: String(recordValue(parsed.runtime)?.releaseId ?? "") },
          parsed.clinicalTrigger,
          parsed.riskMatrix,
          parsed.specialPopulationRedline,
        ) &&
        hasMedicationSafetyScenarioEvidence(parsed.scenarioEvidence, "S28") &&
        hasCompleteMedicationSafetyFeedbackEvidence(
          parsed.specialPopulationFeedback,
          undefined,
          textValue(recordValue(parsed.specialPopulationRecommendation)?.cardId),
        )
      );
    default:
      return false;
  }
}

function collectQualityManagementScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== qualityManagementEntryCoreActionSpecFile ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "quality-management-entry-core-actions-codes",
  );
  if (!attachment?.body) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    if (!parsed || !hasCompleteQualityManagementEntryCoreActionMatrix(parsed)) return [];
    const rows = collectStrictScenarioConditionRows(
      parsed.scenarioConditionEvidence,
      qualityManagementScenarioConditionRows,
    );
    if (rows === null) return [];
    const entryActions = Array.isArray(parsed.entryActions) ? parsed.entryActions : [];
    return qualityManagementScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          qualityManagementScenarioConditionBackedByEvidence(expected.code, parsed, entryActions),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function qualityManagementScenarioConditionBackedByEvidence(
  code: (typeof qualityManagementScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
  entryActions: unknown[],
) {
  switch (code) {
    case "S9__ABNORMAL":
      return (
        entryActions.some((action) =>
          hasCompleteQualityManagementEntryCoreAction(action, "insurance-audit"),
        ) && hasCompleteMedicalRecordQualityIssueEvidence(parsed.medicalRecordQualityIssueEvidence)
      );
    case "S10__NORMAL":
      return entryActions.some((action) =>
        hasCompleteQualityManagementEntryCoreAction(action, "insurance-audit"),
      );
    case "S11__NORMAL":
      return entryActions.some((action) =>
        hasCompleteQualityManagementEntryCoreAction(action, "qc-alerts"),
      );
    default:
      return false;
  }
}

function hasCompleteMedicalRecordQualityIssueEvidence(value: unknown) {
  const evidence = recordValue(value);
  return (
    evidence !== null &&
    evidence.operation === "CASE_REVIEW_DRG_INSURANCE_AUDIT" &&
    is2xxStatus(evidence.caseReviewStatus) &&
    is2xxStatus(evidence.drgGroupingStatus) &&
    is2xxStatus(evidence.insuranceAuditStatus) &&
    evidence.auditStatus === "ISSUE_FOUND" &&
    hasText(evidence.issueId) &&
    hasText(evidence.evaluationRunId) &&
    hasText(evidence.findingId) &&
    typeof evidence.findingCount === "number" &&
    evidence.findingCount > 0 &&
    typeof evidence.taskCount === "number" &&
    evidence.taskCount > 0
  );
}

function collectClinicalEntryScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== clinicalEntryCoreActionSpecFile ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "clinical-entry-core-actions-codes",
  );
  if (!attachment?.body) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    if (!parsed || !hasCompleteClinicalEntryCoreActionMatrix(parsed)) return [];
    const rows = collectStrictScenarioConditionRows(
      parsed.scenarioConditionEvidence,
      clinicalEntryScenarioConditionRows,
    );
    if (rows === null) return [];
    const entryActions = Array.isArray(parsed.entryActions) ? parsed.entryActions : [];
    return clinicalEntryScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          clinicalEntryScenarioConditionBackedByEvidence(expected.code, entryActions),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function clinicalEntryScenarioConditionBackedByEvidence(
  code: (typeof clinicalEntryScenarioConditionRows)[number]["code"],
  entryActions: unknown[],
) {
  switch (code) {
    case "S11__NORMAL":
      return entryActions.some((action) =>
        hasCompleteClinicalEntryCoreAction(action, "workflow-todos"),
      );
    default:
      return false;
  }
}

function collectRealFrontdeskScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "real-frontdesk-rehearsal.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "real-frontdesk-scenario-codes",
  );
  if (!attachment?.body || !hasRequiredRealFrontdeskScenarioAttachment(test)) return [];
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    if (!parsed) return [];
    const rows = collectStrictScenarioConditionRows(
      parsed.scenarioConditionEvidence,
      realFrontdeskScenarioConditionRows,
    );
    if (rows === null) return [];
    return realFrontdeskScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          realFrontdeskScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function realFrontdeskScenarioConditionBackedByEvidence(
  code: (typeof realFrontdeskScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S12__NORMAL":
      return hasCompleteFollowupS12NormalEvidence(parsed);
    case "S12__ABNORMAL":
      return hasCompleteFollowupS12AbnormalEvidence(parsed);
    default:
      return false;
  }
}

function collectDiagnosticCriticalValueScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "diagnostic-critical-value-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnostic-critical-value-frontdesk-codes",
  );
  if (!attachment?.body || !hasRequiredDiagnosticCriticalValueFrontdeskAttachment(test)) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      diagnosticCriticalValueScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return diagnosticCriticalValueScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          diagnosticCriticalValueScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function diagnosticCriticalValueScenarioConditionBackedByEvidence(
  code: (typeof diagnosticCriticalValueScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const runtime = parseDiagnosticCriticalValueRuntimeEvidence(parsed.runtime);
  if (!runtime) return false;
  switch (code) {
    case "S36__HIGH_RISK":
      return (
        hasCompleteDiagnosticCriticalValueApiEvidence(parsed.apiEvidence) &&
        hasCompleteDiagnosticCriticalValueClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
          parsed.inboundObservation,
          parsed.inboundDiagnosticReport,
        ) &&
        hasCompleteDiagnosticCriticalValueInterpretation(parsed.interpretation, runtime) &&
        hasCompleteDiagnosticCriticalValueRecommendation(parsed.recommendation, runtime) &&
        hasCompleteDiagnosticCriticalValueWorkflowTodo(parsed.workflowTodo, parsed.recommendation)
      );
    case "S36__DEGRADATION":
      return (
        hasCompleteDiagnosticCriticalValueInboundObservation(
          parsed.inboundObservation,
          runtime.releaseId,
        ) &&
        hasCompleteDiagnosticCriticalValueInboundReport(
          parsed.inboundDiagnosticReport,
          runtime.releaseId,
        ) &&
        hasCompleteDiagnosticCriticalValueClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
          parsed.inboundObservation,
          parsed.inboundDiagnosticReport,
        ) &&
        hasCompleteDiagnosticCriticalValueWorkflowTodo(parsed.workflowTodo, parsed.recommendation)
      );
    default:
      return false;
  }
}

function collectRegionalDiagnosticMutualRecognitionScenarioConditionClaimsFromTest(
  test: BrowserE2eTestResult,
) {
  if (
    path.basename(test.file) !== "regional-diagnostic-mutual-recognition-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "regional-diagnostic-mutual-recognition-frontdesk-codes",
  );
  if (
    !attachment?.body ||
    !hasRequiredRegionalDiagnosticMutualRecognitionFrontdeskAttachment(test)
  ) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      regionalDiagnosticMutualRecognitionScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return regionalDiagnosticMutualRecognitionScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          regionalDiagnosticMutualRecognitionScenarioConditionBackedByEvidence(
            expected.code,
            parsed,
          ),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function regionalDiagnosticMutualRecognitionScenarioConditionBackedByEvidence(
  code: (typeof regionalDiagnosticMutualRecognitionScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const runtime = parseRegionalDiagnosticMutualRecognitionRuntimeEvidence(parsed.runtime);
  if (!runtime) return false;
  switch (code) {
    case "S40__DEGRADATION":
      return (
        hasCompleteRegionalDiagnosticMutualRecognitionApiEvidence(parsed.apiEvidence) &&
        hasRegionalDiagnosticMutualRecognitionNotConnectedOnboarding(parsed.fhirOnboarding) &&
        hasCompleteRegionalDiagnosticMutualRecognitionSource(
          parsed.regionalSource,
          parsed.fhirOnboarding,
        ) &&
        hasCompleteRegionalDiagnosticMutualRecognitionInboundReport(
          parsed.inboundDiagnosticReport,
          runtime.releaseId,
          parsed.regionalSource,
        ) &&
        hasCompleteRegionalDiagnosticMutualRecognitionClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
          parsed.inboundDiagnosticReport,
        ) &&
        hasCompleteRegionalDiagnosticMutualRecognitionInterpretation(
          parsed.interpretation,
          runtime,
          parsed.inboundDiagnosticReport,
        ) &&
        hasCompleteRegionalDiagnosticMutualRecognitionRecommendation(
          parsed.recommendation,
          runtime,
          parsed.inboundDiagnosticReport,
        ) &&
        hasCompleteRegionalDiagnosticMutualRecognitionWorkflowTodo(
          parsed.workflowTodo,
          parsed.recommendation,
        )
      );
    default:
      return false;
  }
}

function collectNursingContinuityScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "nursing-continuity-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "nursing-continuity-frontdesk-codes",
  );
  if (!attachment?.body || !hasRequiredNursingContinuityFrontdeskAttachment(test)) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      nursingContinuityScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return nursingContinuityScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          nursingContinuityScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function nursingContinuityScenarioConditionBackedByEvidence(
  code: (typeof nursingContinuityScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const runtime = parseNursingContinuityRuntimeEvidence(parsed.runtime);
  if (!runtime) return false;
  switch (code) {
    case "S20__NORMAL":
      return (
        hasCompleteNursingContinuityApiEvidence(parsed.apiEvidence) &&
        hasCompleteNursingContinuityActivationRequest(parsed.activationRequest, runtime) &&
        hasCompleteNursingContinuityClinicalContext(parsed.clinicalContext, runtime.releaseId) &&
        hasCompleteNursingContinuityFollowupPlan(
          parsed.followupPlan,
          runtime,
          parsed.clinicalContext,
        ) &&
        hasCompleteNursingContinuityQuestionnaire(parsed.questionnaire, parsed.followupPlan) &&
        hasCompleteNursingContinuityBackflow(
          parsed.resultBackflow,
          parsed.backflowContext,
          runtime.releaseId,
          parsed.questionnaire,
        )
      );
    case "S35__ABNORMAL":
      return (
        hasCompleteNursingContinuityApiEvidence(parsed.apiEvidence) &&
        hasCompleteNursingContinuityClinicalContext(parsed.clinicalContext, runtime.releaseId) &&
        hasCompleteNursingContinuityHighRiskAssessment(parsed.clinicalContext) &&
        hasCompleteNursingContinuityFollowupPlan(
          parsed.followupPlan,
          runtime,
          parsed.clinicalContext,
        ) &&
        hasCompleteNursingContinuityHighRiskFollowupExplanation(
          parsed.followupPlan,
          parsed.clinicalContext,
        ) &&
        hasCompleteNursingContinuityAbnormalReport(parsed.abnormalReport, parsed.followupPlan)
      );
    default:
      return false;
  }
}

function collectSurgeryAnesthesiaTransfusionScenarioConditionClaimsFromTest(
  test: BrowserE2eTestResult,
) {
  if (
    path.basename(test.file) !== "surgery-anesthesia-transfusion-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "surgery-anesthesia-transfusion-frontdesk-codes",
  );
  if (!attachment?.body || !hasRequiredSurgeryAnesthesiaTransfusionFrontdeskAttachment(test)) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      surgeryAnesthesiaTransfusionScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return surgeryAnesthesiaTransfusionScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          surgeryAnesthesiaTransfusionScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function surgeryAnesthesiaTransfusionScenarioConditionBackedByEvidence(
  code: (typeof surgeryAnesthesiaTransfusionScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const runtime = parseSurgeryAnesthesiaTransfusionRuntimeEvidence(parsed.runtime);
  if (!runtime) return false;
  switch (code) {
    case "S26__HIGH_RISK":
      return (
        hasCompleteSurgeryAnesthesiaTransfusionApiEvidence(parsed.apiEvidence) &&
        hasCompleteSurgeryAnesthesiaTransfusionSafetyRedline(parsed.safetyRedline) &&
        hasCompleteSurgeryAnesthesiaTransfusionRiskMatrix(parsed.riskMatrix) &&
        hasCompleteSurgeryAnesthesiaTransfusionClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
        ) &&
        hasCompleteSurgeryAnesthesiaTransfusionRecommendation(
          parsed.recommendation,
          runtime,
          parsed.clinicalTrigger,
          parsed.ruleAsset,
        ) &&
        hasCompleteSurgeryAnesthesiaTransfusionManualConfirmation(
          parsed.manualConfirmation,
          runtime.actionCardAsset,
        )
      );
    case "S26__DEGRADATION":
      return (
        hasCompleteSurgeryAnesthesiaTransfusionOutbound(
          parsed.outboundChecklist,
          parsed.adapter,
          parsed.clinicalContext,
        ) &&
        hasCompleteSurgeryAnesthesiaTransfusionClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
        ) &&
        hasCompleteSurgeryAnesthesiaTransfusionRecommendation(
          parsed.recommendation,
          runtime,
          parsed.clinicalTrigger,
          parsed.ruleAsset,
        ) &&
        hasCompleteSurgeryAnesthesiaTransfusionManualConfirmation(
          parsed.manualConfirmation,
          runtime.actionCardAsset,
        )
      );
    case "S26__ABNORMAL":
      return hasCompleteSurgeryAnesthesiaTransfusionRectification(
        parsed.qualityRectification,
        parsed.recommendation,
      );
    default:
      return false;
  }
}

function collectPharmacyReviewAntimicrobialScenarioConditionClaimsFromTest(
  test: BrowserE2eTestResult,
) {
  if (
    path.basename(test.file) !== "pharmacy-review-antimicrobial-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "pharmacy-review-antimicrobial-frontdesk-codes",
  );
  if (!attachment?.body || !hasRequiredPharmacyReviewAntimicrobialFrontdeskAttachment(test)) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      pharmacyReviewAntimicrobialScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return pharmacyReviewAntimicrobialScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          pharmacyReviewAntimicrobialScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function pharmacyReviewAntimicrobialScenarioConditionBackedByEvidence(
  code: (typeof pharmacyReviewAntimicrobialScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const runtime = parsePharmacyReviewRuntimeEvidence(parsed.runtime);
  if (!runtime) return false;
  switch (code) {
    case "S18__HIGH_RISK":
      return (
        hasCompletePharmacyReviewApiEvidence(parsed.apiEvidence) &&
        hasCompletePharmacyReviewRiskMatrix(parsed.riskMatrix) &&
        hasCompletePharmacyReviewSafetyRedline(parsed.safetyRedline, parsed.riskMatrix) &&
        hasCompletePharmacyReviewClinicalContext(parsed.clinicalContext, runtime.releaseId) &&
        hasCompletePharmacyReviewRecommendation(
          parsed.recommendation,
          runtime,
          parsed.clinicalTrigger,
          parsed.riskMatrix,
          parsed.safetyRedline,
        ) &&
        hasCompletePharmacyReviewRuleRecommendation(
          parsed.ruleRecommendation,
          runtime,
          parsed.clinicalTrigger,
          parsed.ruleAsset,
        ) &&
        hasCompleteMedicationSafetyFeedbackEvidence(parsed.feedback, runtime.actionCardAsset)
      );
    case "S31__DEGRADATION":
      return (
        hasCompletePharmacyReviewOutbound(
          parsed.outboundReview,
          parsed.adapter,
          parsed.clinicalContext,
        ) &&
        hasCompletePharmacyReviewClinicalContext(parsed.clinicalContext, runtime.releaseId) &&
        hasCompleteMedicationSafetyFeedbackEvidence(parsed.feedback, runtime.actionCardAsset)
      );
    case "S31__ABNORMAL":
      return hasCompletePharmacyReviewRectification(
        parsed.qualityRectification,
        parsed.recommendation,
      );
    default:
      return false;
  }
}

function collectInfectionPublicHealthSafetyScenarioConditionClaimsFromTest(
  test: BrowserE2eTestResult,
) {
  if (
    path.basename(test.file) !== "infection-public-health-safety-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "infection-public-health-safety-frontdesk-codes",
  );
  if (!attachment?.body || !hasRequiredInfectionPublicHealthSafetyFrontdeskAttachment(test)) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      infectionPublicHealthSafetyScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return infectionPublicHealthSafetyScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          infectionPublicHealthSafetyScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function infectionPublicHealthSafetyScenarioConditionBackedByEvidence(
  code: (typeof infectionPublicHealthSafetyScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const runtime = parseInfectionPublicHealthRuntimeEvidence(parsed.runtime);
  if (!runtime) return false;
  switch (code) {
    case "S21__HIGH_RISK":
      return (
        hasCompleteInfectionPublicHealthApiEvidence(parsed.apiEvidence) &&
        hasHighRiskInfectionPublicHealthClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
        ) &&
        hasHighRiskInfectionPublicHealthInbound(
          parsed.inboundReport,
          parsed.adapter,
          parsed.webhookSignature,
          parsed.outboundPrefill,
          runtime.releaseId,
        ) &&
        hasCompleteInfectionPublicHealthRecommendation(
          parsed.recommendation,
          runtime,
          parsed.clinicalTrigger,
          parsed.ruleAsset,
        ) &&
        hasCompleteInfectionPublicHealthManualReview(parsed.manualReview, runtime.actionCardAsset)
      );
    case "S21__DEGRADATION":
      return (
        hasCompleteInfectionPublicHealthOutbound(
          parsed.outboundPrefill,
          parsed.adapter,
          parsed.clinicalContext,
        ) &&
        hasHighRiskInfectionPublicHealthClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
        ) &&
        hasCompleteInfectionPublicHealthRecommendation(
          parsed.recommendation,
          runtime,
          parsed.clinicalTrigger,
          parsed.ruleAsset,
        ) &&
        hasCompleteInfectionPublicHealthManualReview(parsed.manualReview, runtime.actionCardAsset)
      );
    case "S32__ABNORMAL":
      return (
        hasHighRiskInfectionPublicHealthClinicalContext(
          parsed.clinicalContext,
          runtime.releaseId,
        ) &&
        hasHighRiskInfectionPublicHealthInbound(
          parsed.inboundReport,
          parsed.adapter,
          parsed.webhookSignature,
          parsed.outboundPrefill,
          runtime.releaseId,
        ) &&
        hasCompleteInfectionPublicHealthRectification(
          parsed.qualityRectification,
          parsed.recommendation,
        )
      );
    default:
      return false;
  }
}

function collectCriticalEmergencyIcuScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "critical-emergency-icu-frontdesk.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "critical-emergency-icu-frontdesk-codes",
  );
  if (!attachment?.body || !hasRequiredCriticalEmergencyIcuFrontdeskAttachment(test)) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      criticalEmergencyIcuScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return criticalEmergencyIcuScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          criticalEmergencyIcuScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function criticalEmergencyIcuScenarioConditionBackedByEvidence(
  code: (typeof criticalEmergencyIcuScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  const runtime = parseCriticalEmergencyIcuRuntimeEvidence(parsed.runtime);
  if (!runtime) return false;
  switch (code) {
    case "S19__HIGH_RISK":
    case "S24__HIGH_RISK":
    case "S27__HIGH_RISK":
      return (
        hasCompleteCriticalEmergencyIcuApiEvidence(parsed.apiEvidence) &&
        hasCompleteCriticalEmergencyIcuRiskMatrix(parsed.riskMatrix) &&
        hasCompleteCriticalEmergencyIcuActionCard(parsed.actionCard) &&
        hasCompleteCriticalEmergencyIcuClinicalContext(parsed.clinicalContext, runtime.releaseId) &&
        hasCompleteCriticalEmergencyIcuInbound(
          parsed.inboundMonitoringEvent,
          parsed.monitoringAdapter,
          parsed.webhookSignature,
          parsed.clinicalContext,
          runtime.releaseId,
        ) &&
        hasCompleteCriticalEmergencyIcuRecommendation(
          parsed.recommendation,
          runtime,
          parsed.clinicalTrigger,
          parsed.ruleAsset,
        ) &&
        hasCompleteCriticalEmergencyIcuManualEscalation(
          parsed.manualEscalation,
          runtime.actionCardAsset,
          parsed.recommendation,
        ) &&
        hasCompleteCriticalEmergencyIcuTodo(
          parsed.escalationTodo,
          parsed.recommendation,
          parsed.clinicalContext,
        )
      );
    default:
      return false;
  }
}

function collectReportInterpretationScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  if (
    path.basename(test.file) !== "stakeholder-view-rehearsal.spec.ts" ||
    test.status !== "passed" ||
    (test.outcome ?? "expected") !== "expected"
  ) {
    return [];
  }
  const attachment = test.attachments?.find(
    (item) => item.name === "report-interpretation-scenario-codes",
  );
  if (!attachment?.body || !hasRequiredReportInterpretationScenarioAttachment(test)) {
    return [];
  }
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    const rows = collectStrictScenarioConditionRows(
      parsed?.scenarioConditionEvidence,
      reportInterpretationScenarioConditionRows,
    );
    if (!parsed || rows === null) return [];
    return reportInterpretationScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          reportInterpretationScenarioConditionBackedByEvidence(expected.code, parsed),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function reportInterpretationScenarioConditionBackedByEvidence(
  code: (typeof reportInterpretationScenarioConditionRows)[number]["code"],
  parsed: Record<string, unknown>,
) {
  switch (code) {
    case "S17__NORMAL":
      return hasCompleteReportInterpretationS17NormalEvidence(parsed);
    default:
      return false;
  }
}

function collectSystemProvidersScenarioConditionClaims(tests: BrowserE2eTestResult[]) {
  const claims = new Set<string>();
  for (const test of tests) {
    if (
      path.basename(test.file) !== "system-providers-frontdesk.spec.ts" ||
      test.status !== "passed" ||
      (test.outcome ?? "expected") !== "expected"
    ) {
      continue;
    }
    for (const claim of collectSystemProvidersScenarioConditionClaimsFromTest(test)) {
      claims.add(claim);
    }
  }
  return [...claims];
}

function collectSystemProvidersScenarioConditionClaimsFromTest(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "system-providers-operations-codes",
  );
  if (!attachment?.body) return [];
  try {
    const parsed = JSON.parse(attachment.body) as {
      apiEvidence?: unknown;
      dependencyEvidence?: unknown;
      accessEvidence?: unknown;
      backup?: unknown;
      runtimeContinuityEvidence?: unknown;
      scenarioConditionEvidence?: unknown;
    };
    if (!Array.isArray(parsed.scenarioConditionEvidence)) return [];
    const apiEvidence = recordValue(parsed.apiEvidence);
    const dependencyEvidence = recordValue(parsed.dependencyEvidence);
    const accessEvidence = recordValue(parsed.accessEvidence);
    const rows = collectStrictScenarioConditionRows(
      parsed.scenarioConditionEvidence,
      systemProvidersScenarioConditionRows,
    );
    if (!apiEvidence || !dependencyEvidence || !accessEvidence || rows === null) return [];
    return systemProvidersScenarioConditionRows
      .filter(
        (expected) =>
          rows.has(expected.code) &&
          systemProvidersScenarioConditionBackedByEvidence(
            expected.code,
            apiEvidence,
            dependencyEvidence,
            accessEvidence,
            parsed.backup,
            parsed.runtimeContinuityEvidence,
          ),
      )
      .map((row) => `scenarioConditionRows:${row.code}`);
  } catch {
    return [];
  }
}

function systemProvidersScenarioConditionBackedByEvidence(
  code: (typeof systemProvidersScenarioConditionRows)[number]["code"],
  apiEvidence: Record<string, unknown>,
  dependencyEvidence: Record<string, unknown>,
  accessEvidence: Record<string, unknown>,
  backup: unknown,
  runtimeContinuityEvidence: unknown,
) {
  switch (code) {
    case "S15__NORMAL":
      return (
        apiEvidence.operationsSnapshotRead === true &&
        apiEvidence.backupReadinessObserved === true &&
        apiEvidence.evidenceDetailsObserved === true &&
        apiEvidence.runtimeReadbackObserved === true &&
        apiEvidence.runtimeConsumerReadbackObserved === true &&
        apiEvidence.clinicalSmokeAfterRestore === true &&
        hasCompleteSystemProvidersRuntimeContinuityEvidence(runtimeContinuityEvidence)
      );
    case "S15__DEGRADATION":
      return (
        apiEvidence.operationsSnapshotRead === true &&
        apiEvidence.honestDegradationObserved === true &&
        hasCompleteSystemProvidersDependencyEvidence(dependencyEvidence)
      );
    case "S15__MISSING_DATA":
      return (
        apiEvidence.operationsSnapshotRead === true &&
        apiEvidence.backupReadinessObserved === true &&
        apiEvidence.evidenceDetailsObserved === true &&
        apiEvidence.runtimeReadbackObserved === false &&
        apiEvidence.runtimeConsumerReadbackObserved === false &&
        apiEvidence.clinicalSmokeAfterRestore === false &&
        runtimeContinuityEvidence === undefined &&
        hasSystemProvidersMissingBackupDrillEvidence(backup)
      );
    case "S14__ABNORMAL":
      return (
        apiEvidence.clinicalForbidden === true &&
        hasCompleteSystemProvidersAccessEvidence(accessEvidence)
      );
    default:
      return false;
  }
}

function collectPlatformAdminP1ScenarioConditionClaims(tests: BrowserE2eTestResult[]) {
  const hasCompleteMatrix = hasRequiredPlatformAdminP1EntryCoreActionsAttachment(tests);
  return tests.some(
    (test) =>
      hasCompleteMatrix &&
      path.basename(test.file) === "platform-admin-p1-entry-core-actions-rehearsal.spec.ts" &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      hasCompletePlatformAdminP1UnauthorizedAccessEvidence(test),
  )
    ? platformAdminP1ScenarioConditionRows.map((row) => `scenarioConditionRows:${row.code}`)
    : [];
}

function hasCompletePlatformAdminP1UnauthorizedAccessEvidence(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "platform-admin-p1-system-operations-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    if (!parsed || !hasPlatformAdminP1SystemOperationsScopeBoundary(parsed.scopeStatement)) {
      return false;
    }
    const runtimeDiagnostics = recordValue(parsed.runtimeDiagnosticsEvidence);
    const domesticCheck = recordValue(parsed.domesticCheckEvidence);
    return (
      hasCompleteP1RuntimeDiagnosticsUnauthorizedAccessEvidence(runtimeDiagnostics) &&
      hasCompleteP1DomesticCheckUnauthorizedAccessEvidence(domesticCheck)
    );
  } catch {
    return false;
  }
}

function hasCompleteP1RuntimeDiagnosticsUnauthorizedAccessEvidence(
  evidence: Record<string, unknown> | null,
) {
  if (!evidence) return false;
  return (
    is2xxStatus(evidence.runtimeStatus) &&
    is2xxStatus(evidence.operationsStatus) &&
    is2xxStatus(evidence.apiContractsStatus) &&
    typeof evidence.contractCount === "number" &&
    evidence.contractCount > 0 &&
    evidence.pluginBoundaryObserved === true &&
    evidence.clinicalRuntimeStatus === 403 &&
    evidence.clinicalPageForbidden === true
  );
}

function hasCompleteP1DomesticCheckUnauthorizedAccessEvidence(
  evidence: Record<string, unknown> | null,
) {
  if (!evidence) return false;
  return (
    is2xxStatus(evidence.operationsStatus) &&
    is2xxStatus(evidence.reportStatus) &&
    evidence.reportContainsSummary === true &&
    evidence.issueFilterObserved === true &&
    evidence.unknownFilterObserved === true &&
    evidence.clinicalOperationsStatus === 403 &&
    evidence.clinicalPageForbidden === true
  );
}

function hasPlatformAdminP1SystemOperationsScopeBoundary(value: unknown) {
  if (typeof value !== "string") return false;
  return (
    value.includes("不代表 6 个平台管理员入口全部闭环") &&
    value.includes("不代表 34 个入口全部业务动作闭环") &&
    value.includes("不代表完整上线验收")
  );
}

function hasRequiredReportInterpretationScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "report-interpretation-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    return parsed !== null && hasCompleteReportInterpretationS17NormalEvidence(parsed);
  } catch {
    return false;
  }
}

function hasCompleteReportInterpretationS17NormalEvidence(parsed: Record<string, unknown>) {
  const context = parseReportInterpretationClinicalContext(parsed.clinicalContext);
  const interpretation = recordValue(parsed.interpretation);
  const cardIds = Array.isArray(interpretation?.recommendationCardIds)
    ? interpretation.recommendationCardIds.filter((item): item is string => hasText(item))
    : [];
  const cardId = cardIds[0];
  return (
    hasReportInterpretationS17ScopeBoundary(parsed.scopeStatement) &&
    context !== null &&
    hasCompleteReportInterpretationResponse(interpretation, context, cardId) &&
    hasCompleteReportInterpretationWorkflowTodo(parsed.workflowTodo, cardId) &&
    hasCompleteReportInterpretationCompletedTodoReadback(parsed.completedTodoReadback, cardId)
  );
}

function parseReportInterpretationClinicalContext(value: unknown) {
  const context = recordValue(value);
  if (
    !context ||
    !hasText(context.patientId) ||
    !hasText(context.encounterId) ||
    !hasText(context.contextSnapshotId) ||
    !hasText(context.runtimeReleaseId) ||
    !hasText(context.diagnosticReportId) ||
    !hasText(context.diagnosticReportType) ||
    context.signedStatus !== "FINAL"
  ) {
    return null;
  }
  return {
    patientId: String(context.patientId),
    encounterId: String(context.encounterId),
    contextSnapshotId: String(context.contextSnapshotId),
    runtimeReleaseId: String(context.runtimeReleaseId),
    diagnosticReportId: String(context.diagnosticReportId),
    diagnosticReportType: String(context.diagnosticReportType),
  };
}

function hasCompleteReportInterpretationResponse(
  value: Record<string, unknown> | null,
  context: {
    contextSnapshotId: string;
    runtimeReleaseId: string;
    diagnosticReportId: string;
    diagnosticReportType: string;
  },
  cardId: string | undefined,
) {
  const items = Array.isArray(value?.interpretations) ? value.interpretations : [];
  return (
    value !== null &&
    value.operation === "POST /engine/recommendations/report-interpretation" &&
    is2xxStatus(value.status) &&
    value.runtimeReleaseId === context.runtimeReleaseId &&
    value.contextSnapshotId === context.contextSnapshotId &&
    hasText(cardId) &&
    hasText(value.advisoryNote) &&
    String(value.advisoryNote).includes("不改写已签发报告") &&
    String(value.advisoryNote).includes("不自动开嘱") &&
    items.some((item) => {
      const row = recordValue(item);
      return (
        row?.itemCode === "plat:diagnostic_item:lab-potassium" &&
        row.reportType === context.diagnosticReportType &&
        row.reportId === context.diagnosticReportId &&
        isPositiveNumber(row.sourceVersionId) &&
        hasText(row.versionNo) &&
        hasText(row.summary) &&
        String(row.summary).includes(context.runtimeReleaseId)
      );
    })
  );
}

function hasCompleteReportInterpretationWorkflowTodo(value: unknown, cardId: string | undefined) {
  const todo = recordValue(value);
  return (
    todo?.operation === "POST /api/v1/engine/workflow/todos/{todoId}/complete" &&
    is2xxStatus(todo.status) &&
    hasText(todo.todoId) &&
    hasText(cardId) &&
    todo.sourceId === cardId &&
    todo.category === "REPORT_INTERPRETATION" &&
    todo.completedStatus === "COMPLETED" &&
    hasText(todo.completedBy) &&
    hasText(todo.completionReason) &&
    String(todo.completionReason).includes("不改写已签发报告") &&
    todo.noAutoOrder === true
  );
}

function hasCompleteReportInterpretationCompletedTodoReadback(
  value: unknown,
  cardId: string | undefined,
) {
  const readback = recordValue(value);
  return (
    readback?.operation ===
      "GET /api/v1/engine/workflow/todos?status=COMPLETED&sourceType=REPORT_INTERPRETATION" &&
    is2xxStatus(readback.status) &&
    hasText(readback.todoId) &&
    hasText(cardId) &&
    readback.sourceId === cardId &&
    readback.statusValue === "COMPLETED" &&
    readback.readbackVerified === true
  );
}

function hasReportInterpretationS17ScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("医技报告解读正常态代表切片") &&
    !hasUnnegatedReportInterpretationS17ScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整 S17") &&
    hasNegatedScopeTerm(statement, "完整医技系统") &&
    hasNegatedScopeTerm(statement, "危急值 S36") &&
    hasNegatedScopeTerm(statement, "区域互认 S40") &&
    hasNegatedScopeTerm(statement, "全部医技报告族") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedReportInterpretationS17ScopeClaim(statement: string) {
  return [
    "完整 S17",
    "完整S17",
    "完整医技系统",
    "危急值 S36",
    "危急值S36",
    "区域互认 S40",
    "区域互认S40",
    "全部医技报告族",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function collectStrictScenarioConditionRows<
  T extends readonly {
    code: string;
    scenarioCode: string;
    condition: string;
    source: string;
  }[],
>(value: unknown, expectedRows: T) {
  if (!Array.isArray(value)) return null;
  const rows = new Map<string, Record<string, unknown>>();
  for (const item of value) {
    const row = recordValue(item);
    const code = textValue(row?.code);
    const expected = expectedRows.find((candidate) => candidate.code === code);
    if (!row || !code || !expected || rows.has(code)) return null;
    if (
      row.scenarioCode !== expected.scenarioCode ||
      row.condition !== expected.condition ||
      row.source !== expected.source ||
      !hasNonEmptyTextArray(row.evidence)
    ) {
      return null;
    }
    rows.set(code, row);
  }
  return rows;
}

function hasRequiredCdssDeclarativeRuntimeAssetAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "cdss-runtime-declarative-assets-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      serviceCombinations?: unknown;
      apiEvidence?: unknown;
      createdAssets?: unknown;
      ruleRuntimeCandidate?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalTrigger?: unknown;
      recommendation?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parseCdssDeclarativeRuntimeEvidence(parsed.runtime);
    const createdAssets = parseCdssDeclarativeCreatedAssets(parsed.createdAssets);
    const ruleRuntimeCandidate = parseCdssRuntimeRuleAsset(parsed.ruleRuntimeCandidate);
    if (
      !arrayEquals(parsed.scenarioCodes, requiredCdssDeclarativeRuntimeAssetScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION"]) ||
      !arrayEquals(parsed.versionedAssets, ["VALUE_SET", "FORMULA", "ACTION_CARD"]) ||
      !arrayEquals(parsed.serviceCombinations, ["CLINICAL_RUNTIME"]) ||
      !hasCompleteCdssDeclarativeRuntimeApiEvidence(parsed.apiEvidence) ||
      !createdAssets ||
      !runtime ||
      !ruleRuntimeCandidate ||
      !cdssRuntimeRuleMatchesRuntime(ruleRuntimeCandidate, runtime.ruleAsset) ||
      !cdssDeclarativeCreatedAssetsMatchRuntime(createdAssets, runtime.assets) ||
      !hasCompleteCdssDeclarativeActivationRequest(
        parsed.activationRequest,
        runtime.assets,
        ruleRuntimeCandidate,
      ) ||
      !hasCompleteCdssDeclarativeTriggerEvidence(parsed.clinicalTrigger, runtime.releaseId) ||
      !hasCompleteCdssDeclarativeRecommendationEvidence(
        parsed.recommendation,
        runtime,
        parsed.clinicalTrigger,
        ruleRuntimeCandidate,
      ) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredCdssDeclarativeRuntimeAssetScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredCdssDeclarativeRuntimeAssetScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredMedicationSafetyFrontdeskAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "medication-safety-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      riskMatrix?: unknown;
      safetyRedline?: unknown;
      ruleAsset?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      terminologyGate?: unknown;
      clinicalContext?: unknown;
      clinicalTrigger?: unknown;
      recommendation?: unknown;
      ruleRecommendation?: unknown;
      feedback?: unknown;
      scenarioEvidence?: unknown;
    };
    const assets = parseMedicationSafetyRuntimeEvidence(parsed.runtime);
    if (
      !medicationSafetyScenarioCodesAllowed(parsed.scenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION"]) ||
      !arrayEquals(parsed.versionedAssets, ["SAFETY", "CDSS_RISK", "RULE"]) ||
      !arrayEquals(parsed.serviceCombinations, ["CLINICAL_RUNTIME"]) ||
      !hasText(parsed.scopeStatement) ||
      !String(parsed.scopeStatement).includes("代表切片") ||
      !String(parsed.scopeStatement).includes("不代表完整药事治理") ||
      !hasCompleteMedicationSafetyApiEvidence(parsed.apiEvidence) ||
      !hasCompleteMedicationSafetyRiskMatrix(parsed.riskMatrix) ||
      !hasCompleteMedicationSafetyRedline(parsed.safetyRedline, parsed.riskMatrix) ||
      !hasCompleteMedicationSafetyRuleAsset(parsed.ruleAsset) ||
      !assets ||
      !medicationSafetyAssetMatchesRuntime(parsed.safetyRedline, assets.safetyAsset) ||
      !medicationSafetyAssetMatchesRuntime(parsed.riskMatrix, assets.cdssRiskAsset) ||
      !medicationSafetyRuleMatchesRuntime(parsed.ruleAsset, assets.ruleAsset) ||
      !hasCompleteMedicationSafetyActivationRequest(parsed.activationRequest, assets) ||
      !hasCompleteMedicationSafetyTerminologyGate(
        parsed.terminologyGate,
        parsed.runtime,
        parsed.activationRequest,
      ) ||
      !hasCompleteMedicationSafetyClinicalContext(parsed.clinicalContext, assets.releaseId) ||
      !hasCompleteMedicationSafetyTriggerEvidence(parsed.clinicalTrigger, assets.releaseId) ||
      !hasCompleteMedicationSafetyRecommendationEvidence(
        parsed.recommendation,
        assets,
        parsed.clinicalTrigger,
        parsed.riskMatrix,
        parsed.safetyRedline,
      ) ||
      !hasCompleteMedicationSafetyRuleRecommendationEvidence(
        parsed.ruleRecommendation,
        assets,
        parsed.clinicalTrigger,
        parsed.ruleAsset,
      ) ||
      !hasCompleteMedicationSafetyFeedbackEvidence(parsed.feedback) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredMedicationSafetyFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredMedicationSafetyFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function medicationSafetyScenarioCodesAllowed(value: unknown) {
  if (!Array.isArray(value)) return false;
  const codes = value.filter((item): item is string => typeof item === "string");
  return (
    arrayEquals(codes, requiredMedicationSafetyFrontdeskScenarioCodes) ||
    arrayEquals(codes, allowedMedicationSafetyFrontdeskScenarioCodes)
  );
}

function hasMedicationSafetyScenarioEvidence(value: unknown, code: string) {
  if (!Array.isArray(value)) return false;
  const item = recordValue(value.find((entry) => recordValue(entry)?.code === code));
  const rawStages = Array.isArray(item?.observedStages) ? item.observedStages : [];
  const observedStages = rawStages.filter((stage): stage is string => typeof stage === "string");
  const requiredStages = requiredMedicationSafetyFrontdeskScenarioEvidence[code] ?? [];
  return requiredStages.every((stage) => observedStages.includes(stage));
}

function hasRequiredDiagnosticCriticalValueFrontdeskAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnostic-critical-value-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      inboundObservation?: unknown;
      inboundDiagnosticReport?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalContext?: unknown;
      interpretation?: unknown;
      recommendation?: unknown;
      workflowTodo?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parseDiagnosticCriticalValueRuntimeEvidence(parsed.runtime);
    if (
      !arrayEquals(parsed.scenarioCodes, requiredDiagnosticCriticalValueFrontdeskScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"]) ||
      !arrayEquals(parsed.versionedAssets, ["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"]) ||
      !arrayEquals(parsed.deliveryShapes, ["API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME"]) ||
      !hasText(parsed.scopeStatement) ||
      !String(parsed.scopeStatement).includes("代表切片") ||
      !String(parsed.scopeStatement).includes("不代表完整") ||
      !hasCompleteDiagnosticCriticalValueApiEvidence(parsed.apiEvidence) ||
      !runtime ||
      !hasCompleteDiagnosticCriticalValueActivationRequest(parsed.activationRequest, runtime) ||
      !hasCompleteDiagnosticCriticalValueInboundObservation(
        parsed.inboundObservation,
        runtime.releaseId,
      ) ||
      !hasCompleteDiagnosticCriticalValueInboundReport(
        parsed.inboundDiagnosticReport,
        runtime.releaseId,
      ) ||
      !hasCompleteDiagnosticCriticalValueClinicalContext(
        parsed.clinicalContext,
        runtime.releaseId,
        parsed.inboundObservation,
        parsed.inboundDiagnosticReport,
      ) ||
      !hasCompleteDiagnosticCriticalValueInterpretation(parsed.interpretation, runtime) ||
      !hasCompleteDiagnosticCriticalValueRecommendation(parsed.recommendation, runtime) ||
      !hasCompleteDiagnosticCriticalValueWorkflowTodo(parsed.workflowTodo, parsed.recommendation) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredDiagnosticCriticalValueFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredDiagnosticCriticalValueFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredDiagnosticFamilyConsumerSliceAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnostic-critical-value-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      thirdPartySystemFamilyConsumerSlice?: unknown;
      inboundObservation?: unknown;
      inboundDiagnosticReport?: unknown;
      clinicalContext?: unknown;
      recommendation?: unknown;
      workflowTodo?: unknown;
    };
    return hasCompleteDiagnosticFamilyConsumerSlice(
      parsed.thirdPartySystemFamilyConsumerSlice,
      parsed,
    );
  } catch {
    return false;
  }
}

function hasRequiredDiagnosticReportFamilyMatrixAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnostic-critical-value-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      diagnosticReportFamilyConsumerMatrix?: unknown;
      clinicalContext?: unknown;
      interpretation?: unknown;
      workflowTodo?: unknown;
    };
    return hasCompleteDiagnosticReportFamilyConsumerMatrix(
      parsed.diagnosticReportFamilyConsumerMatrix,
      parsed,
    );
  } catch {
    return false;
  }
}

function hasRequiredRegionalDiagnosticMutualRecognitionFrontdeskAttachment(
  test: BrowserE2eTestResult,
) {
  const attachment = test.attachments?.find(
    (item) => item.name === "regional-diagnostic-mutual-recognition-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      fhirOnboarding?: unknown;
      regionalSource?: unknown;
      inboundDiagnosticReport?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalContext?: unknown;
      interpretation?: unknown;
      recommendation?: unknown;
      workflowTodo?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parseRegionalDiagnosticMutualRecognitionRuntimeEvidence(parsed.runtime);
    if (
      !arrayEquals(
        parsed.scenarioCodes,
        requiredRegionalDiagnosticMutualRecognitionScenarioCodes,
      ) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"]) ||
      !arrayEquals(parsed.versionedAssets, ["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"]) ||
      !arrayEquals(parsed.deliveryShapes, ["API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, [
        "THIRD_PARTY_INTERFACE",
        "CLINICAL_RUNTIME",
        "PROFESSIONAL_COLLABORATION",
      ]) ||
      !hasRegionalDiagnosticMutualRecognitionScopeBoundary(parsed.scopeStatement) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionApiEvidence(parsed.apiEvidence) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionOnboarding(parsed.fhirOnboarding) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionSource(
        parsed.regionalSource,
        parsed.fhirOnboarding,
      ) ||
      !runtime ||
      !hasCompleteRegionalDiagnosticMutualRecognitionActivationRequest(
        parsed.activationRequest,
        runtime,
      ) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionInboundReport(
        parsed.inboundDiagnosticReport,
        runtime.releaseId,
        parsed.regionalSource,
      ) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionClinicalContext(
        parsed.clinicalContext,
        runtime.releaseId,
        parsed.inboundDiagnosticReport,
      ) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionInterpretation(
        parsed.interpretation,
        runtime,
        parsed.inboundDiagnosticReport,
      ) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionRecommendation(
        parsed.recommendation,
        runtime,
        parsed.inboundDiagnosticReport,
      ) ||
      !hasCompleteRegionalDiagnosticMutualRecognitionWorkflowTodo(
        parsed.workflowTodo,
        parsed.recommendation,
      ) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      const row = recordValue(item);
      const code = textValue(row?.code);
      const stages = Array.isArray(row?.observedStages) ? row.observedStages : [];
      if (!code) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredRegionalDiagnosticMutualRecognitionScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredRegionalDiagnosticMutualRecognitionScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredRegionalRemoteConsumerSliceAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "regional-diagnostic-mutual-recognition-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    return parsed !== null && hasCompleteRegionalRemoteConsumerSlice(parsed);
  } catch {
    return false;
  }
}

function hasRequiredNursingContinuityFrontdeskAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "nursing-continuity-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalContext?: unknown;
      followupPlan?: unknown;
      questionnaire?: unknown;
      abnormalReport?: unknown;
      resultBackflow?: unknown;
      backflowContext?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parseNursingContinuityRuntimeEvidence(parsed.runtime);
    if (
      !arrayEquals(parsed.scenarioCodes, requiredNursingContinuityFrontdeskScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION"]) ||
      !arrayEquals(parsed.versionedAssets, ["FOLLOWUP"]) ||
      !arrayEquals(parsed.serviceCombinations, ["CLINICAL_RUNTIME"]) ||
      !hasText(parsed.scopeStatement) ||
      !String(parsed.scopeStatement).includes("代表切片") ||
      !String(parsed.scopeStatement).includes("不代表完整") ||
      !hasCompleteNursingContinuityApiEvidence(parsed.apiEvidence) ||
      !runtime ||
      !hasCompleteNursingContinuityActivationRequest(parsed.activationRequest, runtime) ||
      !hasCompleteNursingContinuityClinicalContext(parsed.clinicalContext, runtime.releaseId) ||
      !hasCompleteNursingContinuityFollowupPlan(
        parsed.followupPlan,
        runtime,
        parsed.clinicalContext,
      ) ||
      !hasCompleteNursingContinuityQuestionnaire(parsed.questionnaire, parsed.followupPlan) ||
      !hasCompleteNursingContinuityAbnormalReport(parsed.abnormalReport, parsed.followupPlan) ||
      !hasCompleteNursingContinuityBackflow(
        parsed.resultBackflow,
        parsed.backflowContext,
        runtime.releaseId,
        parsed.questionnaire,
      ) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      const row = recordValue(item);
      const code = textValue(row?.code);
      const stages = Array.isArray(row?.observedStages) ? row.observedStages : [];
      if (!code) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredNursingContinuityFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredNursingContinuityFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredPharmacyReviewAntimicrobialFrontdeskAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "pharmacy-review-antimicrobial-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      adapter?: unknown;
      webhookSignature?: unknown;
      terminologyGate?: unknown;
      riskMatrix?: unknown;
      safetyRedline?: unknown;
      actionCard?: unknown;
      ruleAsset?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalContext?: unknown;
      outboundReview?: unknown;
      inboundReview?: unknown;
      clinicalTrigger?: unknown;
      recommendation?: unknown;
      ruleRecommendation?: unknown;
      feedback?: unknown;
      qualityRectification?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parsePharmacyReviewRuntimeEvidence(parsed.runtime);
    if (
      !arrayEquals(
        parsed.scenarioCodes,
        requiredPharmacyReviewAntimicrobialFrontdeskScenarioCodes,
      ) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"]) ||
      !arrayEquals(parsed.versionedAssets, [
        "TERMINOLOGY",
        "SAFETY",
        "CDSS_RISK",
        "RULE",
        "ACTION_CARD",
      ]) ||
      !arrayEquals(parsed.deliveryShapes, ["API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, [
        "THIRD_PARTY_INTERFACE",
        "CLINICAL_RUNTIME",
        "PROFESSIONAL_COLLABORATION",
        "QUALITY_IMPROVEMENT",
      ]) ||
      !hasText(parsed.scopeStatement) ||
      !hasPharmacyReviewAntimicrobialScopeBoundary(parsed.scopeStatement) ||
      !hasCompletePharmacyReviewApiEvidence(parsed.apiEvidence) ||
      !hasCompletePharmacyReviewAdapterEvidence(parsed.adapter) ||
      !hasCompletePharmacyReviewWebhookEvidence(parsed.webhookSignature, parsed.adapter) ||
      !runtime ||
      !pharmacyReviewRuntimeAssetMatches(parsed.terminologyGate, runtime.terminologyAsset) ||
      !pharmacyReviewRuntimeAssetMatches(parsed.riskMatrix, runtime.cdssRiskAsset) ||
      !pharmacyReviewRuntimeAssetMatches(parsed.safetyRedline, runtime.safetyAsset) ||
      !pharmacyReviewRuntimeAssetMatches(parsed.ruleAsset, runtime.ruleAsset) ||
      !pharmacyReviewRuntimeAssetMatches(parsed.actionCard, runtime.actionCardAsset) ||
      !hasCompletePharmacyReviewRiskMatrix(parsed.riskMatrix) ||
      !hasCompletePharmacyReviewSafetyRedline(parsed.safetyRedline, parsed.riskMatrix) ||
      !hasCompletePharmacyReviewActionCard(parsed.actionCard) ||
      !hasCompletePharmacyReviewRuleAsset(parsed.ruleAsset) ||
      !hasCompletePharmacyReviewActivationRequest(parsed.activationRequest, runtime) ||
      !hasCompletePharmacyReviewTerminologyGate(
        parsed.terminologyGate,
        parsed.runtime,
        parsed.activationRequest,
      ) ||
      !hasCompletePharmacyReviewClinicalContext(parsed.clinicalContext, runtime.releaseId) ||
      !hasCompletePharmacyReviewOutbound(
        parsed.outboundReview,
        parsed.adapter,
        parsed.clinicalContext,
      ) ||
      !hasCompletePharmacyReviewInbound(
        parsed.inboundReview,
        parsed.adapter,
        parsed.webhookSignature,
        parsed.outboundReview,
        runtime.releaseId,
      ) ||
      !hasCompletePharmacyReviewTrigger(parsed.clinicalTrigger, runtime.releaseId) ||
      !hasCompletePharmacyReviewRecommendation(
        parsed.recommendation,
        runtime,
        parsed.clinicalTrigger,
        parsed.riskMatrix,
        parsed.safetyRedline,
      ) ||
      !hasCompletePharmacyReviewRuleRecommendation(
        parsed.ruleRecommendation,
        runtime,
        parsed.clinicalTrigger,
        parsed.ruleAsset,
      ) ||
      !hasCompleteMedicationSafetyFeedbackEvidence(parsed.feedback, runtime.actionCardAsset) ||
      !hasCompletePharmacyReviewRectification(parsed.qualityRectification, parsed.recommendation) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      const row = recordValue(item);
      const code = textValue(row?.code);
      const stages = Array.isArray(row?.observedStages) ? row.observedStages : [];
      if (!code) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredPharmacyReviewAntimicrobialFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredPharmacyReviewAntimicrobialFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredInfectionPublicHealthSafetyFrontdeskAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "infection-public-health-safety-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      adapter?: unknown;
      webhookSignature?: unknown;
      terminologyGate?: unknown;
      actionCard?: unknown;
      ruleAsset?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalContext?: unknown;
      outboundPrefill?: unknown;
      inboundReport?: unknown;
      clinicalTrigger?: unknown;
      recommendation?: unknown;
      manualReview?: unknown;
      qualityRectification?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parseInfectionPublicHealthRuntimeEvidence(parsed.runtime);
    if (
      !arrayEquals(
        parsed.scenarioCodes,
        requiredInfectionPublicHealthSafetyFrontdeskScenarioCodes,
      ) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"]) ||
      !arrayEquals(parsed.versionedAssets, ["TERMINOLOGY", "RULE", "ACTION_CARD"]) ||
      !arrayEquals(parsed.deliveryShapes, ["API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, [
        "THIRD_PARTY_INTERFACE",
        "CLINICAL_RUNTIME",
        "PROFESSIONAL_COLLABORATION",
        "QUALITY_IMPROVEMENT",
      ]) ||
      !hasText(parsed.scopeStatement) ||
      !hasInfectionPublicHealthSafetyScopeBoundary(parsed.scopeStatement) ||
      !hasCompleteInfectionPublicHealthApiEvidence(parsed.apiEvidence) ||
      !hasCompleteInfectionPublicHealthAdapterEvidence(parsed.adapter) ||
      !hasCompleteInfectionPublicHealthWebhookEvidence(parsed.webhookSignature, parsed.adapter) ||
      !runtime ||
      !infectionPublicHealthRuntimeAssetMatches(parsed.terminologyGate, runtime.terminologyAsset) ||
      !infectionPublicHealthRuntimeAssetMatches(parsed.ruleAsset, runtime.ruleAsset) ||
      !infectionPublicHealthRuntimeAssetMatches(parsed.actionCard, runtime.actionCardAsset) ||
      !hasCompleteInfectionPublicHealthTerminologyGate(parsed.terminologyGate) ||
      !hasCompleteInfectionPublicHealthActionCard(parsed.actionCard) ||
      !hasCompleteInfectionPublicHealthRuleAsset(parsed.ruleAsset) ||
      !hasCompleteInfectionPublicHealthActivationRequest(parsed.activationRequest, runtime) ||
      !hasCompleteInfectionPublicHealthClinicalContext(parsed.clinicalContext, runtime.releaseId) ||
      !hasCompleteInfectionPublicHealthOutbound(
        parsed.outboundPrefill,
        parsed.adapter,
        parsed.clinicalContext,
      ) ||
      !hasCompleteInfectionPublicHealthInbound(
        parsed.inboundReport,
        parsed.adapter,
        parsed.webhookSignature,
        parsed.outboundPrefill,
        runtime.releaseId,
      ) ||
      !hasCompleteInfectionPublicHealthTrigger(parsed.clinicalTrigger, runtime.releaseId) ||
      !hasCompleteInfectionPublicHealthRecommendation(
        parsed.recommendation,
        runtime,
        parsed.clinicalTrigger,
        parsed.ruleAsset,
      ) ||
      !hasCompleteInfectionPublicHealthManualReview(parsed.manualReview, runtime.actionCardAsset) ||
      !hasCompleteInfectionPublicHealthRectification(
        parsed.qualityRectification,
        parsed.recommendation,
      ) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      const row = recordValue(item);
      const code = textValue(row?.code);
      const stages = Array.isArray(row?.observedStages) ? row.observedStages : [];
      if (!code) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredInfectionPublicHealthSafetyFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredInfectionPublicHealthSafetyFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredPublicHealthInfectionRegulatoryConsumerSliceAttachment(
  test: BrowserE2eTestResult,
) {
  const attachment = test.attachments?.find(
    (item) => item.name === "infection-public-health-safety-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    return parsed !== null && hasCompletePublicHealthInfectionRegulatoryConsumerSlice(parsed);
  } catch {
    return false;
  }
}

function hasRequiredSurgeryAnesthesiaTransfusionFrontdeskAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "surgery-anesthesia-transfusion-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      adapter?: unknown;
      webhookSignature?: unknown;
      terminologyGate?: unknown;
      safetyRedline?: unknown;
      riskMatrix?: unknown;
      actionCard?: unknown;
      ruleAsset?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalContext?: unknown;
      outboundChecklist?: unknown;
      inboundSurgeryEvent?: unknown;
      clinicalTrigger?: unknown;
      recommendation?: unknown;
      manualConfirmation?: unknown;
      qualityRectification?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parseSurgeryAnesthesiaTransfusionRuntimeEvidence(parsed.runtime);
    if (
      !arrayEquals(
        parsed.scenarioCodes,
        requiredSurgeryAnesthesiaTransfusionFrontdeskScenarioCodes,
      ) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"]) ||
      !arrayEquals(parsed.versionedAssets, [
        "TERMINOLOGY",
        "SAFETY",
        "CDSS_RISK",
        "RULE",
        "ACTION_CARD",
      ]) ||
      !arrayEquals(parsed.deliveryShapes, ["API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, [
        "THIRD_PARTY_INTERFACE",
        "CLINICAL_RUNTIME",
        "PROFESSIONAL_COLLABORATION",
        "QUALITY_IMPROVEMENT",
      ]) ||
      !hasText(parsed.scopeStatement) ||
      !hasSurgeryAnesthesiaTransfusionScopeBoundary(parsed.scopeStatement) ||
      !hasCompleteSurgeryAnesthesiaTransfusionApiEvidence(parsed.apiEvidence) ||
      !hasCompleteSurgeryAnesthesiaTransfusionAdapterEvidence(parsed.adapter) ||
      !hasCompleteSurgeryAnesthesiaTransfusionWebhookEvidence(
        parsed.webhookSignature,
        parsed.adapter,
      ) ||
      !runtime ||
      !surgeryAnesthesiaTransfusionRuntimeAssetMatches(
        parsed.terminologyGate,
        runtime.terminologyAsset,
      ) ||
      !surgeryAnesthesiaTransfusionRuntimeAssetMatches(parsed.safetyRedline, runtime.safetyAsset) ||
      !surgeryAnesthesiaTransfusionRuntimeAssetMatches(parsed.riskMatrix, runtime.cdssRiskAsset) ||
      !surgeryAnesthesiaTransfusionRuntimeAssetMatches(parsed.ruleAsset, runtime.ruleAsset) ||
      !surgeryAnesthesiaTransfusionRuntimeAssetMatches(
        parsed.actionCard,
        runtime.actionCardAsset,
      ) ||
      !hasCompleteSurgeryAnesthesiaTransfusionTerminologyGate(parsed.terminologyGate) ||
      !hasCompleteSurgeryAnesthesiaTransfusionSafetyRedline(parsed.safetyRedline) ||
      !hasCompleteSurgeryAnesthesiaTransfusionRiskMatrix(parsed.riskMatrix) ||
      !hasCompleteSurgeryAnesthesiaTransfusionActionCard(parsed.actionCard) ||
      !hasCompleteSurgeryAnesthesiaTransfusionRuleAsset(parsed.ruleAsset) ||
      !hasCompleteSurgeryAnesthesiaTransfusionActivationRequest(
        parsed.activationRequest,
        runtime,
      ) ||
      !hasCompleteSurgeryAnesthesiaTransfusionClinicalContext(
        parsed.clinicalContext,
        runtime.releaseId,
      ) ||
      !hasCompleteSurgeryAnesthesiaTransfusionOutbound(
        parsed.outboundChecklist,
        parsed.adapter,
        parsed.clinicalContext,
      ) ||
      !hasCompleteSurgeryAnesthesiaTransfusionInbound(
        parsed.inboundSurgeryEvent,
        parsed.adapter,
        parsed.webhookSignature,
        parsed.outboundChecklist,
        runtime.releaseId,
      ) ||
      !hasCompleteSurgeryAnesthesiaTransfusionTrigger(parsed.clinicalTrigger, runtime.releaseId) ||
      !hasCompleteSurgeryAnesthesiaTransfusionRecommendation(
        parsed.recommendation,
        runtime,
        parsed.clinicalTrigger,
        parsed.ruleAsset,
      ) ||
      !hasCompleteSurgeryAnesthesiaTransfusionManualConfirmation(
        parsed.manualConfirmation,
        runtime.actionCardAsset,
      ) ||
      !hasCompleteSurgeryAnesthesiaTransfusionRectification(
        parsed.qualityRectification,
        parsed.recommendation,
      ) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      const row = recordValue(item);
      const code = textValue(row?.code);
      const stages = Array.isArray(row?.observedStages) ? row.observedStages : [];
      if (!code) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredSurgeryAnesthesiaTransfusionFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredSurgeryAnesthesiaTransfusionFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredSurgeryAnesthesiaTransfusionConsumerSliceAttachment(
  test: BrowserE2eTestResult,
) {
  const attachment = test.attachments?.find(
    (item) => item.name === "surgery-anesthesia-transfusion-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    return parsed !== null && hasCompleteNursingAnesthesiaTransfusionIcuConsumerSlice(parsed);
  } catch {
    return false;
  }
}

function hasRequiredCriticalEmergencyIcuFrontdeskAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "critical-emergency-icu-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      scopeStatement?: unknown;
      apiEvidence?: unknown;
      monitoringAdapter?: unknown;
      emergencyOnboarding?: unknown;
      webhookSignature?: unknown;
      terminologyGate?: unknown;
      riskMatrix?: unknown;
      actionCard?: unknown;
      ruleAsset?: unknown;
      pathwayAsset?: unknown;
      runtime?: unknown;
      activationRequest?: unknown;
      clinicalContext?: unknown;
      inboundMonitoringEvent?: unknown;
      clinicalTrigger?: unknown;
      recommendation?: unknown;
      manualEscalation?: unknown;
      escalationTodo?: unknown;
      scenarioEvidence?: unknown;
    };
    const runtime = parseCriticalEmergencyIcuRuntimeEvidence(parsed.runtime);
    if (
      !arrayEquals(parsed.scenarioCodes, requiredCriticalEmergencyIcuFrontdeskScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"]) ||
      !arrayEquals(parsed.versionedAssets, [
        "TERMINOLOGY",
        "CDSS_RISK",
        "RULE",
        "PATHWAY",
        "ACTION_CARD",
      ]) ||
      !arrayEquals(parsed.deliveryShapes, ["API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, [
        "THIRD_PARTY_INTERFACE",
        "CLINICAL_RUNTIME",
        "PROFESSIONAL_COLLABORATION",
      ]) ||
      !hasText(parsed.scopeStatement) ||
      !hasCriticalEmergencyIcuScopeBoundary(parsed.scopeStatement) ||
      !hasCompleteCriticalEmergencyIcuApiEvidence(parsed.apiEvidence) ||
      !hasCompleteCriticalEmergencyIcuAdapterEvidence(parsed.monitoringAdapter) ||
      !hasCompleteCriticalEmergencyIcuOnboarding(
        parsed.emergencyOnboarding,
        parsed.monitoringAdapter,
      ) ||
      !hasCompleteCriticalEmergencyIcuWebhookEvidence(
        parsed.webhookSignature,
        parsed.monitoringAdapter,
      ) ||
      !runtime ||
      !criticalEmergencyIcuRuntimeAssetMatches(parsed.terminologyGate, runtime.terminologyAsset) ||
      !criticalEmergencyIcuRuntimeAssetMatches(parsed.riskMatrix, runtime.cdssRiskAsset) ||
      !criticalEmergencyIcuRuntimeAssetMatches(parsed.ruleAsset, runtime.ruleAsset) ||
      !criticalEmergencyIcuRuntimeAssetMatches(parsed.pathwayAsset, runtime.pathwayAsset) ||
      !criticalEmergencyIcuRuntimeAssetMatches(parsed.actionCard, runtime.actionCardAsset) ||
      !hasCompleteCriticalEmergencyIcuTerminologyGate(parsed.terminologyGate) ||
      !hasCompleteCriticalEmergencyIcuRiskMatrix(parsed.riskMatrix) ||
      !hasCompleteCriticalEmergencyIcuActionCard(parsed.actionCard) ||
      !hasCompleteCriticalEmergencyIcuRuleAsset(parsed.ruleAsset) ||
      !hasCompleteCriticalEmergencyIcuPathwayAsset(parsed.pathwayAsset) ||
      !hasCompleteCriticalEmergencyIcuActivationRequest(parsed.activationRequest, runtime) ||
      !hasCompleteCriticalEmergencyIcuClinicalContext(parsed.clinicalContext, runtime.releaseId) ||
      !hasCompleteCriticalEmergencyIcuInbound(
        parsed.inboundMonitoringEvent,
        parsed.monitoringAdapter,
        parsed.webhookSignature,
        parsed.clinicalContext,
        runtime.releaseId,
      ) ||
      !hasCompleteCriticalEmergencyIcuTrigger(parsed.clinicalTrigger, runtime.releaseId) ||
      !hasCompleteCriticalEmergencyIcuRecommendation(
        parsed.recommendation,
        runtime,
        parsed.clinicalTrigger,
        parsed.ruleAsset,
      ) ||
      !hasCompleteCriticalEmergencyIcuManualEscalation(
        parsed.manualEscalation,
        runtime.actionCardAsset,
        parsed.recommendation,
      ) ||
      !hasCompleteCriticalEmergencyIcuTodo(
        parsed.escalationTodo,
        parsed.recommendation,
        parsed.clinicalContext,
      ) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      const row = recordValue(item);
      const code = textValue(row?.code);
      const stages = Array.isArray(row?.observedStages) ? row.observedStages : [];
      if (!code) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredCriticalEmergencyIcuFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredCriticalEmergencyIcuFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredLisMonitoringCriticalConsumerSliceAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "critical-emergency-icu-frontdesk-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    return parsed !== null && hasCompleteLisMonitoringCriticalConsumerSlice(parsed);
  } catch {
    return false;
  }
}

function hasRequiredRealFrontdeskScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "real-frontdesk-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      scenarioEvidence?: unknown;
    };
    if (!Array.isArray(parsed.scenarioCodes) || !Array.isArray(parsed.scenarioEvidence)) {
      return false;
    }
    const observedCodes = parsed.scenarioCodes
      .filter((code): code is string => typeof code === "string")
      .sort();
    if (
      JSON.stringify(observedCodes) !==
      JSON.stringify([...requiredRealFrontdeskScenarioCodes].sort())
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredRealFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredRealFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredServiceOrganizationScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "service-organization-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      organizationLevels?: unknown;
      serviceCombinations?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredServiceOrganizationScenarioCodes) ||
      !arrayEquals(parsed.organizationLevels, [
        "HOSPITAL",
        "CAMPUS_OR_MEMBER",
        "DEPARTMENT",
        "WARD",
      ]) ||
      !arrayEquals(parsed.serviceCombinations, [
        "ONBOARDING_INTEGRATION",
        "COMPLIANCE_OPERATIONS",
      ]) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredServiceOrganizationScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredServiceOrganizationScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasCompleteServiceOrganizationOnboardingEvidence(value: unknown) {
  const onboarding = recordValue(value);
  return (
    onboarding?.serviceOperation === "POST /api/v1/admin/tenants" &&
    is2xxStatus(onboarding.serviceStatus) &&
    hasText(onboarding.tenantId) &&
    hasText(onboarding.tenantName) &&
    hasText(onboarding.adminUsername) &&
    onboarding.adminUserId === onboarding.adminUsername &&
    onboarding.temporaryPasswordIssued === true &&
    onboarding.temporaryPasswordDisplayedOnce === true
  );
}

function hasCompleteServiceOrganizationAdminBootstrapEvidence(
  value: unknown,
  onboardingValue: unknown,
) {
  const bootstrap = recordValue(value);
  const onboarding = recordValue(onboardingValue);
  return (
    bootstrap !== null &&
    onboarding !== null &&
    bootstrap.username === onboarding.adminUsername &&
    bootstrap.tenantId === onboarding.tenantId &&
    bootstrap.loginMustChangePwd === true &&
    is2xxStatus(bootstrap.changePasswordStatus) &&
    bootstrap.dashboardReached === true
  );
}

function hasCompleteServiceOrganizationOrgTreeEvidence(value: unknown, onboardingValue: unknown) {
  const orgTree = recordValue(value);
  const onboarding = recordValue(onboardingValue);
  const facility = recordValue(orgTree?.facility);
  const campus = recordValue(orgTree?.campus);
  const department = recordValue(orgTree?.department);
  const ward = recordValue(orgTree?.ward);
  return (
    orgTree !== null &&
    onboarding !== null &&
    facility !== null &&
    campus !== null &&
    department !== null &&
    ward !== null &&
    orgTree.facilityReadbackVerified === true &&
    orgTree.campusReadbackVerified === true &&
    orgTree.departmentReadbackVerified === true &&
    orgTree.wardReadbackVerified === true &&
    hasText(facility.id) &&
    facility.tenantId === onboarding.tenantId &&
    facility.level === "FACILITY" &&
    hasText(facility.name) &&
    facility.status === "ACTIVE" &&
    hasText(campus.id) &&
    campus.tenantId === onboarding.tenantId &&
    campus.parentId === facility.id &&
    campus.level === "CAMPUS" &&
    hasText(campus.name) &&
    campus.status === "ACTIVE" &&
    hasText(department.id) &&
    department.tenantId === onboarding.tenantId &&
    department.parentId === campus.id &&
    department.level === "DEPARTMENT" &&
    hasText(department.name) &&
    department.status === "ACTIVE" &&
    hasText(ward.id) &&
    ward.tenantId === onboarding.tenantId &&
    ward.parentId === department.id &&
    ward.level === "WARD" &&
    hasText(ward.name) &&
    ward.status === "ACTIVE"
  );
}

function hasRequiredMfaLoginScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find((item) => item.name === "mfa-login-scenario-codes");
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      serviceCombinations?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredMfaLoginScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["FOUNDATION_GOVERNANCE"]) ||
      !arrayEquals(parsed.serviceCombinations, ["COMPLIANCE_OPERATIONS"]) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredMfaLoginScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredMfaLoginScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasCompleteMfaLoginApiEvidence(value: unknown) {
  const apiEvidence = recordValue(value);
  return (
    hasApiCallEvidence(apiEvidence?.configRead, "GET /system/configs") &&
    hasApiCallEvidence(apiEvidence?.accountCreated, "POST /compliance/users") &&
    hasApiCallEvidence(apiEvidence?.firstPasswordChanged, "POST /auth/change-password") &&
    hasApiCallEvidence(apiEvidence?.mfaSecretGenerated, "POST /auth/mfa/bind") &&
    hasApiCallEvidence(apiEvidence?.mfaTotpBound, "POST /auth/mfa/bind") &&
    hasApiCallEvidence(apiEvidence?.configEnabled, "PATCH /system/configs/{key}") &&
    hasApiCallEvidence(apiEvidence?.mfaVerify, "POST /auth/mfa/verify") &&
    hasApiCallEvidence(apiEvidence?.profileRead, "GET /security/me") &&
    hasApiCallEvidence(apiEvidence?.configRestored, "PATCH /system/configs/{key}") &&
    hasApiCallEvidence(apiEvidence?.accountDisabled, "PATCH /compliance/users/{userId}/status")
  );
}

function hasApiCallEvidence(value: unknown, operation: string) {
  const evidence = recordValue(value);
  return evidence?.operation === operation && is2xxStatus(evidence.status);
}

function hasCompleteMfaLoginConfigEvidence(value: unknown) {
  const config = recordValue(value);
  const enabledVersion = positiveNumber(config?.enabledVersion);
  const restoredVersion = positiveNumber(config?.restoredVersion);
  if (typeof enabledVersion !== "number" || typeof restoredVersion !== "number") return false;
  return (
    config?.key === "medkernel.auth.mfa.enabled" &&
    config.beforeValue === "false" &&
    config.enabledValue === "true" &&
    config.restoredValue === "false" &&
    config.confirmedHighRisk === true &&
    restoredVersion >= enabledVersion
  );
}

function hasCompleteMfaTemporaryAdminEvidence(value: unknown) {
  const admin = recordValue(value);
  return (
    hasText(admin?.userId) &&
    hasText(admin?.username) &&
    admin?.roleCode === "platform-admin" &&
    admin.created === true &&
    admin.firstPasswordChanged === true &&
    admin.disabledAfterDrill === true &&
    admin.secretPersistedInEvidence === false
  );
}

function hasCompleteMfaBindingEvidence(value: unknown) {
  const binding = recordValue(value);
  return (
    binding?.totpSecretGenerated === true &&
    binding.totpBound === true &&
    binding.secretPersistedInEvidence === false &&
    hasText(binding.deviceLabel)
  );
}

function hasCompleteMfaLoginChallengeEvidence(value: unknown) {
  const challenge = recordValue(value);
  return (
    challenge?.challengeShown === true &&
    challenge.bootstrapUrlReached === true &&
    challenge.dashboardReachedAfterVerify === true
  );
}

function hasCompleteMfaVerificationEvidence(value: unknown) {
  const verification = recordValue(value);
  return verification?.verified === true && is2xxStatus(verification.status);
}

function hasCompleteMfaProfileEvidence(profileValue: unknown, temporaryAdminValue: unknown) {
  const profile = recordValue(profileValue);
  const temporaryAdmin = recordValue(temporaryAdminValue);
  return (
    profile !== null &&
    temporaryAdmin !== null &&
    profile.username === temporaryAdmin.username &&
    Array.isArray(profile.roles) &&
    profile.roles.includes("platform-admin") &&
    profile.mfaRequired === true &&
    profile.mfaBound === true &&
    profile.mfaVerified === true
  );
}

function hasRequiredDiagnosisKnowledgeScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnosis-knowledge-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      semanticFamilies?: unknown;
      specialtyDomains?: unknown;
      apiEvidence?: unknown;
      standardTerm?: unknown;
      diagnosisAsset?: unknown;
      diagnosisCriterion?: unknown;
      validationCase?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredDiagnosisKnowledgeScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["MEDICAL_ASSET"]) ||
      !arrayEquals(parsed.semanticFamilies, ["DISEASE_DIAGNOSIS"]) ||
      !arrayEquals(parsed.specialtyDomains, ["CLINICAL_SPECIALTIES"]) ||
      !hasCompleteDiagnosisKnowledgeStructuredEvidence(parsed) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredDiagnosisKnowledgeScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredDiagnosisKnowledgeScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredDomainFacadeB0EvidenceAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "domain-facade-b0-evidence-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = recordValue(JSON.parse(attachment.body));
    if (!parsed) return false;
    const apiEvidence = recordValue(parsed.apiEvidence);
    const readback = recordValue(apiEvidence?.b0EvidenceReadFromFrontdesk);
    return (
      arrayEquals(parsed.domainFacadeCodes, requiredDomainFacadeB0Codes) &&
      arrayEquals(parsed.domainFacadeB0Coverage, ["CLINICAL_SPECIALTY_DOMAIN_B0_FACADE_CATALOG"]) &&
      readback?.operation === "GET /engine/domain-facades/b0-evidence" &&
      is2xxStatus(readback.status) &&
      hasDomainFacadeB0ScopeBoundary(parsed.scopeStatement) &&
      hasCompleteDomainFacadeB0Rows(parsed.facadeEvidence)
    );
  } catch {
    return false;
  }
}

function hasCompleteDomainFacadeB0Rows(value: unknown) {
  if (!Array.isArray(value) || value.length !== requiredDomainFacadeB0Codes.length) return false;
  const rows = new Map<string, Record<string, unknown>>();
  for (const item of value) {
    const row = recordValue(item);
    const code = textValue(row?.code);
    if (!row || !code || rows.has(code)) return false;
    rows.set(code, row);
  }
  if (
    ![...rows.keys()].every((code) =>
      (requiredDomainFacadeB0Codes as readonly string[]).includes(code),
    )
  ) {
    return false;
  }
  return requiredDomainFacadeB0Codes.every((code) => {
    const row = rows.get(code);
    if (!row || !hasCompleteDomainFacadeB0Row(code, row)) return false;
    if (code === "SPECIALTY-EXT-01") {
      return (
        row.honestEmptyWhenAssetsMissing === true && row.assetSeedPolicy === "NO_SEED_HONEST_EMPTY"
      );
    }
    if (code === "SVC-DOMAIN-01") {
      return hasResolvableDomainFacadeMembers(row, [
        "CRITICAL-01",
        "PERIOP-01",
        "ONCO-RENAL-01",
        "SPECIAL-POP-01",
        "TCM-HEALTH-01",
        "PRIMARY-CARE-01",
        "INFECTION-PH-01",
      ]);
    }
    if (code === "SVC-DOMAIN-02") {
      return hasResolvableDomainFacadeMembers(row, [
        "NURSING-01",
        "PHARMACY-01",
        "REPORT-01",
        "POC-KNOW-01",
        "ALLIED-CARE-01",
        "RWD-01",
        "REGION-COLLAB-01",
      ]);
    }
    return true;
  });
}

function hasCompleteDomainFacadeB0Row(code: string, row: Record<string, unknown>) {
  return (
    row.status === "PASS" &&
    row.evidenceId === `DOMAIN-B0-${code}` &&
    row.b0Executable === true &&
    row.modelRequired === false &&
    row.clinicalContentSeeded === false &&
    row.newBusinessEngineRequired === false &&
    row.serviceCombinationMembersResolvable === true &&
    ["NO_CLINICAL_CONTENT_SEED", "NO_SEED_HONEST_EMPTY"].includes(
      textValue(row.assetSeedPolicy) ?? "",
    ) &&
    hasNonEmptyTextArray(row.b0Workflows) &&
    hasCompleteDomainFacadeEngineEvidence(row.engineEvidence)
  );
}

function hasCompleteDomainFacadeEngineEvidence(value: unknown) {
  if (!Array.isArray(value) || value.length === 0) return false;
  return value.every((item) => {
    const evidence = recordValue(item);
    return (
      evidence !== null &&
      hasText(evidence.engine) &&
      hasText(evidence.sharedHandlerClass) &&
      String(evidence.sharedHandlerClass).startsWith("com.medkernel.engine.") &&
      hasText(evidence.b0Route) &&
      String(evidence.b0Route).startsWith("/api/v1/") &&
      hasText(evidence.b0Assertion) &&
      evidence.deterministic === true &&
      evidence.handlerPresent === true &&
      evidence.clinicalContentSeeded === false
    );
  });
}

function hasResolvableDomainFacadeMembers(
  row: Record<string, unknown>,
  expected: readonly string[],
) {
  return (
    arrayEquals(row.memberFacadeCodes, expected) &&
    arrayEquals(row.verifiedMemberFacadeCodes, expected)
  );
}

function hasDomainFacadeB0ScopeBoundary(value: unknown) {
  const scope = recordValue(value);
  if (!scope || typeof scope.provesOnly !== "string") return false;
  const statement = scope.provesOnly;
  return (
    statement.includes("17 张专业领域门面") &&
    statement.includes("B0") &&
    statement.includes("模型非必需") &&
    statement.includes("无临床内容预置") &&
    scope.notFullSpecialtyDomainCoverage === true &&
    scope.notScenarioConditionRows === true &&
    scope.notFullS0S40Coverage === true &&
    scope.notFullLaunchReadiness === true &&
    arrayEquals(scope.notCompleteScenarioCodes, ["S28", "S29", "S30", "S37", "S38", "S39"]) &&
    !hasUnnegatedDomainFacadeB0ScopeClaim(statement)
  );
}

function hasUnnegatedDomainFacadeB0ScopeClaim(statement: string) {
  return [
    "完整专业领域",
    "完整 S28",
    "完整S28",
    "完整 S29",
    "完整S29",
    "完整 S30",
    "完整S30",
    "完整 S37",
    "完整S37",
    "完整 S38",
    "完整S38",
    "完整 S39",
    "完整S39",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线",
    "完整上线验收",
    "真实消费者",
    "业务闭环",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompleteDiagnosisKnowledgeStructuredEvidence(value: unknown) {
  const evidence = recordValue(value);
  const apiEvidence = recordValue(evidence?.apiEvidence);
  const standardTerm = recordValue(evidence?.standardTerm);
  const diagnosisAsset = recordValue(evidence?.diagnosisAsset);
  const diagnosisCriterion = recordValue(evidence?.diagnosisCriterion);
  const validationCase = recordValue(evidence?.validationCase);
  return (
    evidence !== null &&
    hasCompleteDiagnosisKnowledgeApiEvidence(apiEvidence) &&
    hasCompleteDiagnosisKnowledgeStandardTerm(standardTerm) &&
    hasCompleteDiagnosisKnowledgeAsset(diagnosisAsset) &&
    hasCompleteDiagnosisKnowledgeCriterion(diagnosisCriterion, standardTerm) &&
    hasCompleteDiagnosisKnowledgeValidationCase(validationCase, standardTerm)
  );
}

function hasCompleteDiagnosisKnowledgeApiEvidence(value: Record<string, unknown> | null) {
  const standardTerm = recordValue(value?.standardTermRegisteredFromFrontdesk);
  const diagnosisAsset = recordValue(value?.diagnosisAssetDraftCreatedFromFrontdesk);
  const criterion = recordValue(value?.diagnosisCriterionRegisteredFromFrontdesk);
  const validationCase = recordValue(value?.validationCaseRegisteredFromFrontdesk);
  return (
    standardTerm !== null &&
    diagnosisAsset !== null &&
    criterion !== null &&
    validationCase !== null &&
    standardTerm.operation === "POST /engine/terminology/terms/standard" &&
    diagnosisAsset.operation === "POST /engine/knowledge/diagnosis/assets" &&
    criterion.operation === "POST /criteria" &&
    validationCase.operation === "POST /test-cases" &&
    is2xxStatus(standardTerm.status) &&
    is2xxStatus(diagnosisAsset.status) &&
    is2xxStatus(criterion.status) &&
    is2xxStatus(validationCase.status)
  );
}

function hasCompleteDiagnosisKnowledgeInvalidAssetCreationRejectionEvidence(
  value: Record<string, unknown>,
) {
  if (!hasCompleteDiagnosisKnowledgeStructuredEvidence(value)) return false;
  const apiEvidence = recordValue(value.apiEvidence);
  const rejectionApi = recordValue(apiEvidence?.invalidAssetCreateRejectedFromFrontdesk);
  const readbackApi = recordValue(apiEvidence?.assetReadbackAfterRejection);
  const rejection = recordValue(value.invalidAssetCreationRejection);
  return (
    rejection !== null &&
    rejectionApi !== null &&
    readbackApi !== null &&
    rejection.operation === "POST /engine/knowledge/diagnosis/assets" &&
    rejectionApi.operation === "POST /engine/knowledge/diagnosis/assets" &&
    rejectionApi.status === rejection.status &&
    rejectionApi.errorCode === rejection.errorCode &&
    rejectionApi.traceId === rejection.traceId &&
    rejection.status === 400 &&
    rejection.errorCode === "ENG-API-002" &&
    hasText(rejection.traceId) &&
    hasText(rejection.requestedIdentityCode) &&
    rejection.evidenceExcerptPresentInSource === false &&
    rejection.assetReadbackAbsent === true &&
    rejection.versionIdAbsent === true &&
    rejection.validationCaseAttempted === false &&
    rejection.readbackOperation === "GET /engine/knowledge/identities" &&
    readbackApi.operation === "GET /engine/knowledge/identities" &&
    is2xxStatus(rejection.readbackStatus) &&
    readbackApi.status === rejection.readbackStatus
  );
}

function hasCompleteDiagnosisKnowledgeStandardTerm(value: Record<string, unknown> | null) {
  return (
    value !== null &&
    value.operation === "POST /engine/terminology/terms/standard" &&
    is2xxStatus(value.status) &&
    hasText(value.system) &&
    hasText(value.termCode) &&
    hasText(value.displayName)
  );
}

function hasCompleteDiagnosisKnowledgeAsset(value: Record<string, unknown> | null) {
  return (
    value !== null &&
    value.operation === "POST /engine/knowledge/diagnosis/assets" &&
    is2xxStatus(value.status) &&
    isPositiveNumber(value.identityId) &&
    isPositiveNumber(value.versionId) &&
    hasText(value.identityCode) &&
    hasText(value.requestedIdentityCode) &&
    hasText(value.evidenceExcerpt)
  );
}

function hasCompleteDiagnosisKnowledgeCriterion(
  value: Record<string, unknown> | null,
  standardTerm: Record<string, unknown> | null,
) {
  return (
    value !== null &&
    standardTerm !== null &&
    value.operation === "POST /criteria" &&
    is2xxStatus(value.status) &&
    hasText(value.findingTermCode) &&
    value.findingTermCode === standardTerm.termCode
  );
}

function hasCompleteDiagnosisKnowledgeValidationCase(
  value: Record<string, unknown> | null,
  standardTerm: Record<string, unknown> | null,
) {
  return (
    value !== null &&
    standardTerm !== null &&
    value.operation === "POST /test-cases" &&
    is2xxStatus(value.status) &&
    hasText(value.caseIdentity) &&
    hasText(value.findingTermCode) &&
    value.findingTermCode === standardTerm.termCode
  );
}

function hasCompleteDiagnosisAssistRuntimeNormalEvidence(value: unknown) {
  const evidence = recordValue(value);
  if (!evidence || !arrayEquals(evidence.scenarioCodes, ["S16"])) return false;
  const apiEvidence = recordValue(evidence.apiEvidence);
  const securityProfile = recordValue(evidence.securityProfile);
  const standardTerm = recordValue(evidence.standardTerm);
  const knowledge = recordValue(evidence.knowledge);
  const runtime = recordValue(evidence.diagnosisRuntime);
  const clinicalContext = recordValue(evidence.clinicalContext);
  const diagnosisSupport = recordValue(evidence.diagnosisSupport);
  const recommendationCard = recordValue(evidence.recommendationCard);
  return (
    hasCompleteDiagnosisAssistApiEvidence(apiEvidence) &&
    securityProfile?.role === "clinical-user" &&
    hasText(securityProfile?.tenantId) &&
    hasText(securityProfile?.hospitalId) &&
    hasText(standardTerm?.termCode) &&
    hasText(standardTerm?.displayName) &&
    hasCompleteDiagnosisAssistKnowledge(knowledge) &&
    hasCompleteDiagnosisAssistRuntime(runtime, knowledge) &&
    hasCompleteDiagnosisAssistClinicalContext(clinicalContext, runtime, standardTerm) &&
    hasCompleteDiagnosisAssistServiceEvidence(
      diagnosisSupport,
      runtime,
      clinicalContext,
      knowledge,
      standardTerm,
    ) &&
    hasCompleteDiagnosisAssistRecommendationCard(
      recommendationCard,
      diagnosisSupport,
      runtime,
      knowledge,
    )
  );
}

function hasCompleteDiagnosisAssistApiEvidence(value: Record<string, unknown> | null) {
  const publish = recordValue(value?.diagnosisAssetPublishedFromGovernance);
  const activation = recordValue(value?.runtimeReleaseActivatedWithDiagnosisKnowledge);
  const context = recordValue(value?.contextSnapshotCreatedFromFrontdesk);
  const assist = recordValue(value?.diagnosisAssistEvaluatedFromFrontdesk);
  const card = recordValue(value?.diagnosisRecommendationCardReadback);
  return (
    publish?.operation ===
      "POST /engine/knowledge/diagnosis/identities/{identityId}/versions/{versionId}/publish" &&
    activation?.operation === "POST /engine/releases/hospitals/{hospitalId}/runtime-releases" &&
    context?.operation === "POST /engine/context/snapshots" &&
    assist?.operation === "POST /engine/recommendations/diagnosis-assist" &&
    card?.operation === "GET /engine/recommendations/cards" &&
    is2xxStatus(publish.status) &&
    is2xxStatus(activation.status) &&
    is2xxStatus(context.status) &&
    is2xxStatus(assist.status) &&
    is2xxStatus(card.status)
  );
}

function hasCompleteDiagnosisAssistKnowledge(value: Record<string, unknown> | null) {
  return (
    value !== null &&
    isPositiveNumber(value.identityId) &&
    isPositiveNumber(value.versionId) &&
    hasText(value.identityCode) &&
    hasText(value.versionNo) &&
    value.versionStatus === "ACTIVE"
  );
}

function hasCompleteDiagnosisAssistRuntime(
  value: Record<string, unknown> | null,
  knowledge: Record<string, unknown> | null,
) {
  return (
    value !== null &&
    knowledge !== null &&
    value.operation === "PUBLISH_ACTIVATE_DIAGNOSIS_RUNTIME" &&
    is2xxStatus(value.publishStatus) &&
    is2xxStatus(value.runtimeActivationStatus) &&
    value.identityId === knowledge.identityId &&
    value.identityCode === knowledge.identityCode &&
    value.versionId === knowledge.versionId &&
    value.versionNo === knowledge.versionNo &&
    hasText(value.runtimeReleaseId) &&
    value.runtimeConsumerReadback === true &&
    value.activeRuntimeContainsDiagnosis === true
  );
}

function hasCompleteDiagnosisAssistClinicalContext(
  value: Record<string, unknown> | null,
  runtime: Record<string, unknown> | null,
  standardTerm: Record<string, unknown> | null,
) {
  return (
    value !== null &&
    runtime !== null &&
    standardTerm !== null &&
    value.operation === "POST /engine/context/snapshots" &&
    is2xxStatus(value.status) &&
    hasText(value.contextSnapshotId) &&
    hasText(value.patientId) &&
    value.runtimeReleaseId === runtime.runtimeReleaseId &&
    value.findingTermCode === standardTerm.termCode
  );
}

function hasCompleteDiagnosisAssistServiceEvidence(
  value: Record<string, unknown> | null,
  runtime: Record<string, unknown> | null,
  clinicalContext: Record<string, unknown> | null,
  knowledge: Record<string, unknown> | null,
  standardTerm: Record<string, unknown> | null,
) {
  return (
    value !== null &&
    runtime !== null &&
    clinicalContext !== null &&
    knowledge !== null &&
    standardTerm !== null &&
    value.operation === "POST /engine/recommendations/diagnosis-assist" &&
    is2xxStatus(value.status) &&
    value.contextSnapshotId === clinicalContext.contextSnapshotId &&
    value.runtimeReleaseId === runtime.runtimeReleaseId &&
    value.findingTermCode === standardTerm.termCode &&
    typeof value.candidateCount === "number" &&
    value.candidateCount > 0 &&
    value.candidateIdentityId === knowledge.identityId &&
    value.candidateIdentityCode === knowledge.identityCode &&
    value.candidateVersionId === knowledge.versionId &&
    hasText(value.candidateConfidence) &&
    Array.isArray(value.supportingFindings) &&
    value.supportingFindings.includes(standardTerm.termCode) &&
    hasText(value.traceId) &&
    typeof value.advisoryNote === "string" &&
    value.advisoryNote.includes("需医师确认") &&
    value.advisoryNote.includes("非自动诊断")
  );
}

function hasCompleteDiagnosisAssistRecommendationCard(
  value: Record<string, unknown> | null,
  diagnosisSupport: Record<string, unknown> | null,
  runtime: Record<string, unknown> | null,
  knowledge: Record<string, unknown> | null,
) {
  return (
    value !== null &&
    diagnosisSupport !== null &&
    runtime !== null &&
    knowledge !== null &&
    value.readbackOperation === "GET /engine/recommendations/cards" &&
    is2xxStatus(value.readbackStatus) &&
    hasText(value.cardId) &&
    value.cardType === "DIAGNOSIS" &&
    value.scenarioCode === "S16" &&
    value.contextSnapshotId === diagnosisSupport.contextSnapshotId &&
    value.runtimeReleaseId === runtime.runtimeReleaseId &&
    value.sourceVersionId === knowledge.versionId &&
    value.sourceIdentityCode === knowledge.identityCode &&
    value.requiresPhysicianConfirmation === true &&
    value.aiGenerated === false &&
    value.noAutoDiagnosis === true &&
    value.noAutoOrder === true
  );
}

function hasRequiredRuntimeReleaseAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "runtime-release-coverage-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      productLayers?: unknown;
      versionedAssets?: unknown;
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      apiEvidence?: unknown;
      localCandidate?: unknown;
      unselectedLocalCandidate?: unknown;
      activationRequest?: unknown;
      activationReadback?: unknown;
      runtimeConsumerReadback?: unknown;
      rollbackReadback?: unknown;
      rollbackRuntimeConsumerReadback?: unknown;
      partialSelection?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.productLayers, ["RELEASE_GOVERNANCE"]) ||
      !arrayEquals(parsed.versionedAssets, requiredRuntimeReleaseVersionedAssets) ||
      !arrayEquals(parsed.deliveryShapes, ["MANAGEMENT_WORKSPACE", "API_EVENT"]) ||
      !arrayEquals(parsed.serviceCombinations, ["CLINICAL_RUNTIME", "THIRD_PARTY_INTERFACE"]) ||
      !hasCompleteRuntimeReleaseApiEvidence(parsed.apiEvidence) ||
      !hasCompleteRuntimeReleaseLocalCandidateEvidence(parsed) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const observedStages = parsed.scenarioEvidence.flatMap((item) => {
      if (!item || typeof item !== "object") return [];
      const stages = (item as { observedStages?: unknown }).observedStages;
      return Array.isArray(stages)
        ? stages.filter((stage): stage is string => typeof stage === "string")
        : [];
    });
    return requiredRuntimeReleaseScenarioEvidence.every((stage) => observedStages.includes(stage));
  } catch {
    return false;
  }
}

function hasRequiredRuntimeReleasePartialSelectionAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "runtime-release-coverage-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      apiEvidence?: unknown;
      localCandidate?: unknown;
      unselectedLocalCandidate?: unknown;
      activationRequest?: unknown;
      activationReadback?: unknown;
      runtimeConsumerReadback?: unknown;
      partialSelection?: unknown;
      multiHospitalDifferentiation?: unknown;
      platformUpgradeAnalysis?: unknown;
      offlineDelivery?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !hasCompleteRuntimeReleasePartialSelectionEvidence(parsed) ||
      !hasCompleteRuntimeReleaseMultiHospitalEvidence(parsed.multiHospitalDifferentiation) ||
      !hasCompleteRuntimeReleasePlatformUpgradeEvidence(parsed.platformUpgradeAnalysis) ||
      !hasCompleteRuntimeReleaseOfflineDeliveryEvidence(parsed) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const observedStages = parsed.scenarioEvidence.flatMap((item) => {
      if (!item || typeof item !== "object") return [];
      const stages = (item as { observedStages?: unknown }).observedStages;
      return Array.isArray(stages)
        ? stages.filter((stage): stage is string => typeof stage === "string")
        : [];
    });
    return requiredRuntimeReleasePartialSelectionScenarioEvidence.every((stage) =>
      observedStages.includes(stage),
    );
  } catch {
    return false;
  }
}

function hasCompleteRuntimeReleaseLocalCandidateEvidence(value: {
  localCandidate?: unknown;
  activationRequest?: unknown;
  activationReadback?: unknown;
  runtimeConsumerReadback?: unknown;
  rollbackReadback?: unknown;
  rollbackRuntimeConsumerReadback?: unknown;
}) {
  const candidate = parseRuntimeReleaseCandidate(value.localCandidate);
  if (!candidate) return false;
  return (
    runtimeReleasePayloadContainsCandidate(value.activationRequest, "activeAssets", candidate) &&
    runtimeReleasePayloadContainsCandidate(value.activationReadback, "assets", candidate, {
      requireActive: true,
    }) &&
    runtimeReleasePayloadContainsCandidate(value.runtimeConsumerReadback, "assets", candidate, {
      requireActive: true,
    }) &&
    runtimeReleasePayloadExcludesCandidate(value.rollbackReadback, candidate) &&
    runtimeReleasePayloadExcludesCandidate(value.rollbackRuntimeConsumerReadback, candidate)
  );
}

function hasCompleteRuntimeReleaseRollbackDegradationEvidence(value: Record<string, unknown>) {
  const candidate = parseRuntimeReleaseCandidate(value.localCandidate);
  if (
    !candidate ||
    !isPositiveNumber(value.activatedRevisionNo) ||
    !isPositiveNumber(value.rolledBackRevisionNo)
  ) {
    return false;
  }
  const evidence = recordValue(value.apiEvidence);
  if (
    !evidence ||
    evidence.rollbackPosted !== true ||
    evidence.rollbackCurrentReleaseReadback !== true ||
    evidence.rollbackRuntimeConsumerReadback !== true
  ) {
    return false;
  }
  return (
    Number(value.rolledBackRevisionNo) > Number(value.activatedRevisionNo) &&
    runtimeReleasePayloadExcludesCandidate(value.rollbackReadback, candidate) &&
    runtimeReleasePayloadExcludesCandidate(value.rollbackRuntimeConsumerReadback, candidate)
  );
}

function hasCompleteS2S4RuntimeMappingApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "adapterCreatedFromFrontdesk",
    "fieldMappingConfigured",
    "webhookCreatedFromFrontdesk",
    "standardTermRegisteredFromFrontdesk",
    "localTermRegisteredThroughSignedSync",
    "candidateGeneratedFromFrontdesk",
    "candidateConfirmedFromFrontdesk",
    "terminologyAssetDraftCreatedFromFrontdesk",
    "runtimeReleaseActivatedWithTerminologyAsset",
    "invalidMasterDataSignatureRejected",
    "invalidInboundWebhookSignatureRejected",
    "inboundWebhookAccepted",
    "inboundNormalizedByRuntimeRelease",
    "runtimeContractReadbackMatched",
  ].every((key) => evidence[key] === true);
}

function hasCompleteS2S4AdapterEvidence(
  value: unknown,
  terminology: {
    sourceSystem: string;
    targetDictionaryKey: string;
    category: string;
  } | null,
) {
  if (!value || typeof value !== "object" || Array.isArray(value) || !terminology) return false;
  const adapter = value as Record<string, unknown>;
  if (
    !hasText(adapter.adapterId) ||
    String(adapter.protocolType).toUpperCase() !== "WEBHOOK" ||
    String(adapter.sourceSystem).toUpperCase() !== terminology.sourceSystem.toUpperCase() ||
    !Array.isArray(adapter.fieldMappings)
  ) {
    return false;
  }
  const mappings = adapter.fieldMappings.filter((item): item is Record<string, unknown> =>
    Boolean(item && typeof item === "object" && !Array.isArray(item)),
  );
  const hasPlainField = mappings.some(
    (item) =>
      hasText(item.sourcePath) && hasText(item.targetPath) && !hasText(item.targetDictionaryKey),
  );
  const hasTerminologyField = mappings.some(
    (item) =>
      hasText(item.sourcePath) &&
      hasText(item.targetPath) &&
      item.targetDictionaryKey === terminology.targetDictionaryKey &&
      item.category === terminology.category,
  );
  return hasPlainField && hasTerminologyField;
}

function parseS2S4TerminologyEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const terminology = value as Record<string, unknown>;
  if (
    terminology.assetType !== "TERMINOLOGY" ||
    !hasText(terminology.assetIdentity) ||
    !hasText(terminology.versionId) ||
    !hasText(terminology.standardSystem) ||
    !hasText(terminology.standardCode) ||
    !hasText(terminology.localCode) ||
    !hasText(terminology.sourceSystem) ||
    !hasText(terminology.category) ||
    typeof terminology.mappingId !== "number" ||
    terminology.mappingId <= 0
  ) {
    return null;
  }
  return {
    assetType: "TERMINOLOGY",
    assetIdentity: String(terminology.assetIdentity),
    versionId: String(terminology.versionId),
    targetDictionaryKey: String(terminology.standardSystem),
    standardCode: String(terminology.standardCode),
    localCode: String(terminology.localCode),
    sourceSystem: String(terminology.sourceSystem),
    category: String(terminology.category),
    mappingId: terminology.mappingId,
    standardTermId:
      typeof terminology.standardTermId === "number" && terminology.standardTermId > 0
        ? terminology.standardTermId
        : null,
  };
}

function parseS2S4RuntimeEvidence(
  value: unknown,
  terminology: {
    assetType: string;
    assetIdentity: string;
    versionId: string;
  } | null,
) {
  if (!value || typeof value !== "object" || Array.isArray(value) || !terminology) return null;
  const runtime = value as Record<string, unknown>;
  if (
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    typeof runtime.manifestSha256 !== "string" ||
    !/^[0-9a-f]{64}$/i.test(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets) ||
    !runtime.assets.some((asset) =>
      runtimeReleaseAssetMatchesCandidate(
        asset,
        {
          assetType: terminology.assetType,
          assetIdentity: terminology.assetIdentity,
          versionId: terminology.versionId,
        },
        { requireActive: true },
      ),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: runtime.manifestSha256,
    terminology,
  };
}

function hasCompleteS2S4ActivationRequest(
  value: unknown,
  terminology: {
    assetType: string;
    assetIdentity: string;
    versionId: string;
  },
) {
  return runtimeReleasePayloadContainsCandidate(value, "activeAssets", terminology);
}

function hasCompleteS2S4InboundResult(
  value: unknown,
  terminology: {
    standardCode: string;
    localCode: string;
    sourceSystem: string;
    targetDictionaryKey: string;
    mappingId: number;
    standardTermId: number | null;
  },
  runtimeReleaseId: string,
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const result = value as Record<string, unknown>;
  if (
    result.status !== "SUCCESS" ||
    typeof result.mappedFieldCount !== "number" ||
    result.mappedFieldCount < 2 ||
    result.normalizedCodeCount !== 1 ||
    !hasText(result.clinicalEventStatus) ||
    result.clinicalEventStatus !== "RECEIVED"
  ) {
    return false;
  }
  const normalized = findS2S4NormalizedCode(result.mappedPayload);
  return (
    normalized?.standardCode === terminology.standardCode &&
    normalized.codeSystem === terminology.targetDictionaryKey &&
    normalized.localCode === terminology.localCode &&
    normalized.sourceSystem === terminology.sourceSystem &&
    normalized.runtimeReleaseId === runtimeReleaseId &&
    normalized.mappingId === terminology.mappingId &&
    (terminology.standardTermId === null ||
      normalized.standardTermId === terminology.standardTermId)
  );
}

function hasCompleteS2S4RuntimeConsumerReadback(
  value: unknown,
  terminology: {
    assetType: string;
    assetIdentity: string;
    versionId: string;
  },
  runtime: {
    releaseId: string;
    revisionNo: number;
    manifestSha256: string;
  },
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const readback = value as Record<string, unknown>;
  return (
    readback.releaseId === runtime.releaseId &&
    readback.revisionNo === runtime.revisionNo &&
    readback.manifestSha256 === runtime.manifestSha256 &&
    Array.isArray(readback.assets) &&
    readback.assets.some((asset) =>
      runtimeReleaseAssetMatchesCandidate(asset, terminology, { requireActive: true }),
    )
  );
}

function findS2S4NormalizedCode(value: unknown) {
  const queue = [value];
  while (queue.length > 0) {
    const current = queue.shift();
    if (!current || typeof current !== "object") continue;
    if (Array.isArray(current)) {
      queue.push(...current);
      continue;
    }
    const record = current as Record<string, unknown>;
    if (
      hasText(record.standardCode) &&
      hasText(record.codeSystem) &&
      hasText(record.localCode) &&
      hasText(record.sourceSystem) &&
      hasText(record.runtimeReleaseId) &&
      typeof record.mappingId === "number"
    ) {
      return {
        standardCode: String(record.standardCode),
        codeSystem: String(record.codeSystem),
        localCode: String(record.localCode),
        sourceSystem: String(record.sourceSystem),
        runtimeReleaseId: String(record.runtimeReleaseId),
        mappingId: record.mappingId,
        standardTermId:
          typeof record.standardTermId === "number" ? record.standardTermId : undefined,
      };
    }
    queue.push(...Object.values(record));
  }
  return null;
}

function hasCompleteCdssDeclarativeRuntimeApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "valueSetCreatedFromFrontdesk",
    "formulaCreatedFromFrontdesk",
    "actionCardCreatedFromFrontdesk",
    "declarativeRuntimeActivatedBeforeRuleTestCases",
    "ruleTestSnapshotBoundToDeclarativeRuntime",
    "ruleCreatedWithRuntimeAssetReferences",
    "ruleRuntimeCandidateResolvedFromCurrentHospital",
    "runtimeReleaseActivatedWithDeclarativeAssets",
    "activeSnapshotBoundToRuntimeRelease",
    "cdssEvaluationTriggeredFromFrontdesk",
    "recommendationPersisted",
    "ruleExplanationContainsRuntimeMaterialization",
  ].every((key) => evidence[key] === true);
}

function parseCdssDeclarativeRuntimeEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const runtime = value as Record<string, unknown>;
  if (
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const runtimeAssets = runtime.assets;
  const assets = (["VALUE_SET", "FORMULA", "ACTION_CARD"] as const)
    .map((assetType) => parseCdssDeclarativeAsset(runtimeAssets, assetType))
    .filter((asset): asset is CdssDeclarativeRuntimeAsset => asset !== null);
  if (assets.length !== 3) return null;
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    assets,
    ruleAsset: parseCdssRuntimeRuleAsset(runtime.ruleAsset),
  };
}

function parseCdssDeclarativeCreatedAssets(value: unknown) {
  if (!Array.isArray(value)) return null;
  const assets = (["VALUE_SET", "FORMULA", "ACTION_CARD"] as const)
    .map((assetType) => parseCdssDeclarativeAsset(value, assetType, false))
    .filter((asset): asset is CdssDeclarativeRuntimeAsset => asset !== null);
  return assets.length === 3 ? assets : null;
}

function cdssDeclarativeCreatedAssetsMatchRuntime(
  createdAssets: CdssDeclarativeRuntimeAsset[],
  runtimeAssets: CdssDeclarativeRuntimeAsset[],
) {
  return createdAssets.every((created) =>
    runtimeAssets.some(
      (runtime) =>
        runtime.assetType === created.assetType &&
        runtime.assetIdentity === created.assetIdentity &&
        runtime.versionId === created.versionId &&
        runtime.versionNo === created.versionNo &&
        runtime.contentHash === created.contentHash,
    ),
  );
}

type CdssDeclarativeRuntimeAsset = {
  assetType: "VALUE_SET" | "FORMULA" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type CdssRuntimeRuleAsset = {
  assetType: "RULE";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

function parseCdssDeclarativeAsset(
  values: unknown[],
  assetType: "VALUE_SET" | "FORMULA" | "ACTION_CARD",
  requireActive = true,
): CdssDeclarativeRuntimeAsset | null {
  const asset = values.find((item) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return false;
    const record = item as Record<string, unknown>;
    return record.assetType === assetType && (!requireActive || record.entryState === "ACTIVE");
  });
  if (!asset || typeof asset !== "object" || Array.isArray(asset)) return null;
  const record = asset as Record<string, unknown>;
  if (
    !hasText(record.assetIdentity) ||
    !hasText(record.versionId) ||
    !hasText(record.versionNo) ||
    !isSha256(record.contentHash)
  ) {
    return null;
  }
  return {
    assetType,
    assetIdentity: String(record.assetIdentity),
    versionId: String(record.versionId),
    versionNo: String(record.versionNo),
    contentHash: String(record.contentHash),
  };
}

function hasCompleteCdssDeclarativeActivationRequest(
  value: unknown,
  assets: CdssDeclarativeRuntimeAsset[],
  ruleAsset: CdssRuntimeRuleAsset,
) {
  return [ruleAsset, ...assets].every((asset) =>
    runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
      assetType: asset.assetType,
      assetIdentity: asset.assetIdentity,
      versionId: asset.versionId,
    }),
  );
}

function hasCompleteCdssDeclarativeTriggerEvidence(value: unknown, runtimeReleaseId: string) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const trigger = value as Record<string, unknown>;
  const relatedCardIds = Array.isArray(trigger.relatedCardIds)
    ? trigger.relatedCardIds.filter((cardId): cardId is string => hasText(cardId))
    : [];
  return (
    hasText(trigger.triggerId) &&
    hasText(trigger.contextSnapshotId) &&
    hasText(trigger.cardId) &&
    relatedCardIds.includes(String(trigger.cardId)) &&
    trigger.runtimeReleaseId === runtimeReleaseId
  );
}

function hasCompleteCdssDeclarativeRecommendationEvidence(
  value: unknown,
  runtime: {
    releaseId: string;
    assets: CdssDeclarativeRuntimeAsset[];
    ruleAsset: CdssRuntimeRuleAsset | null;
  },
  triggerValue: unknown,
  ruleAsset: CdssRuntimeRuleAsset,
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const recommendation = value as Record<string, unknown>;
  const trigger = recordValue(triggerValue);
  if (
    !hasText(recommendation.cardId) ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardId !== trigger?.cardId ||
    recommendation.contextSnapshotId !== trigger?.contextSnapshotId
  ) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const runtimeRelease = recordValue(explanation?.runtimeRelease);
  const ruleExplanation = recordValue(explanation?.ruleExplanation);
  if (
    runtimeRelease?.runtimeReleaseId !== runtime.releaseId ||
    runtimeRelease.assetVersionId !== ruleAsset.versionId ||
    runtimeRelease.assetVersionNo !== ruleAsset.versionNo ||
    runtimeRelease.contentHash !== ruleAsset.contentHash ||
    !Array.isArray(ruleExplanation?.conditionEvidence) ||
    !Array.isArray(ruleExplanation?.runtimeAssetEvidence)
  ) {
    return false;
  }
  const runtimeAssetEvidence = ruleExplanation.runtimeAssetEvidence;
  return runtime.assets.every((asset) =>
    cdssRuntimeAssetEvidenceMatches(runtimeAssetEvidence, asset),
  );
}

function parseCdssRuntimeRuleAsset(value: unknown): CdssRuntimeRuleAsset | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const record = value as Record<string, unknown>;
  if (
    record.assetType !== "RULE" ||
    !hasText(record.assetIdentity) ||
    !hasText(record.versionId) ||
    !hasText(record.versionNo) ||
    !isSha256(record.contentHash)
  ) {
    return null;
  }
  return {
    assetType: "RULE",
    assetIdentity: String(record.assetIdentity),
    versionId: String(record.versionId),
    versionNo: String(record.versionNo),
    contentHash: String(record.contentHash),
  };
}

function cdssRuntimeRuleMatchesRuntime(
  candidate: CdssRuntimeRuleAsset,
  runtimeRule: CdssRuntimeRuleAsset | null,
) {
  return (
    runtimeRule?.assetIdentity === candidate.assetIdentity &&
    runtimeRule.versionId === candidate.versionId &&
    runtimeRule.versionNo === candidate.versionNo &&
    runtimeRule.contentHash === candidate.contentHash
  );
}

function cdssRuntimeAssetEvidenceMatches(values: unknown[], asset: CdssDeclarativeRuntimeAsset) {
  return values.some((item) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return false;
    const evidence = item as Record<string, unknown>;
    if (
      evidence.assetType !== asset.assetType ||
      evidence.assetIdentity !== asset.assetIdentity ||
      evidence.assetVersion !== asset.versionNo ||
      evidence.contentHash !== asset.contentHash
    ) {
      return false;
    }
    if (asset.assetType === "VALUE_SET") {
      return typeof evidence.expandedCount === "number" && evidence.expandedCount > 0;
    }
    if (asset.assetType === "FORMULA") {
      return hasText(evidence.runtimeFunction);
    }
    return (
      evidence.actionCardRef === asset.assetIdentity &&
      evidence.resolvedActionCardVersion === asset.versionNo &&
      evidence.resolvedActionCardHash === asset.contentHash &&
      evidence.requiresPhysicianConfirmation === true
    );
  });
}

type MedicationSafetyRuntimeAsset = {
  assetType: "SAFETY" | "CDSS_RISK" | "RULE";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

function hasCompleteMedicationSafetyApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "riskMatrixCreatedFromRealService",
    "safetyRedlineDraftCreated",
    "safetyRedlineDryRunSubmitted",
    "safetyAssetPromoted",
    "terminologyCoverageGateActivated",
    "ruleCreatedForMedicationPrescribe",
    "ruleRuntimeCandidateResolvedFromCurrentHospital",
    "runtimeActivatedWithSafetyRiskAndRule",
    "contextSnapshotCreatedFromFrontdesk",
    "clinicalEvaluationTriggeredFromFrontdesk",
    "pharmacistReviewRecordedWithoutClosingPhysicianConfirmation",
    "physicianConfirmationRecorded",
  ].every((field) => evidence?.[field] === true);
}

function parseMedicationSafetyRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const safetyAsset = parseMedicationSafetyRuntimeAsset(runtime.safetyAsset, "SAFETY");
  const specialPopulationSafetyAsset = parseMedicationSafetyRuntimeAsset(
    runtime.specialPopulationSafetyAsset,
    "SAFETY",
  );
  const cdssRiskAsset = parseMedicationSafetyRuntimeAsset(runtime.cdssRiskAsset, "CDSS_RISK");
  const ruleAsset = parseMedicationSafetyRuntimeAsset(runtime.ruleAsset, "RULE");
  if (!safetyAsset || !cdssRiskAsset || !ruleAsset) return null;
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    safetyAsset,
    specialPopulationSafetyAsset,
    cdssRiskAsset,
    ruleAsset,
  };
}

function parseMedicationSafetyRuntimeAsset(
  value: unknown,
  assetType: MedicationSafetyRuntimeAsset["assetType"],
): MedicationSafetyRuntimeAsset | null {
  const asset = recordValue(value);
  if (
    !asset ||
    asset.assetType !== assetType ||
    !hasText(asset.assetIdentity) ||
    !hasText(asset.versionId) ||
    !hasText(asset.versionNo) ||
    !isSha256(asset.contentHash) ||
    asset.entryState !== "ACTIVE"
  ) {
    return null;
  }
  return {
    assetType,
    assetIdentity: String(asset.assetIdentity),
    versionId: String(asset.versionId),
    versionNo: String(asset.versionNo),
    contentHash: String(asset.contentHash),
  };
}

function hasCompleteMedicationSafetyRiskMatrix(value: unknown) {
  const risk = recordValue(value);
  return (
    risk?.assetType === "CDSS_RISK" &&
    risk.assetIdentity === "CDSS.RISK.MATRIX" &&
    hasText(risk.matrixId) &&
    hasText(risk.matrixVersion) &&
    risk.triggerPoint === "medication-prescribe" &&
    risk.severityLevel === "CRITICAL" &&
    risk.automationLevel === "INFORM_ONLY" &&
    risk.riskLevel === "CRITICAL" &&
    risk.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    typeof risk.silentRunHours === "number" &&
    risk.silentRunHours >= 168 &&
    hasText(risk.releaseGate) &&
    risk.autoExecutionAllowed === false
  );
}

function hasCompleteMedicationSafetyRedline(value: unknown, riskMatrixValue: unknown) {
  const redline = recordValue(value);
  const risk = recordValue(riskMatrixValue);
  return (
    redline?.assetType === "SAFETY" &&
    hasText(redline.assetIdentity) &&
    String(redline.assetIdentity).startsWith("SAFETY.RDL-MED-ALLERGY-") &&
    hasText(redline.redlineId) &&
    hasText(redline.redlineKey) &&
    hasText(redline.redlineVersion) &&
    redline.hazardSeverity === "CRITICAL" &&
    redline.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    redline.lowerTenantOverrideAllowed === false &&
    redline.riskMatrixId === risk?.matrixId &&
    redline.riskMatrixVersion === risk?.matrixVersion &&
    hasText(redline.releaseGate) &&
    hasText(redline.conditionDsl) &&
    String(redline.conditionDsl).includes("allergyIntolerances[].code") &&
    String(redline.conditionDsl).includes('"fact"') &&
    hasText(redline.trialId)
  );
}

function hasCompleteMedicationSafetyRuleAsset(value: unknown) {
  const rule = recordValue(value);
  return (
    rule?.assetType === "RULE" &&
    hasText(rule.assetIdentity) &&
    String(rule.assetIdentity).startsWith("RULE.MEDICATION.SAFETY.") &&
    hasText(rule.ruleId) &&
    hasText(rule.ruleVersionId)
  );
}

function medicationSafetyAssetMatchesRuntime(
  candidateValue: unknown,
  runtimeAsset: MedicationSafetyRuntimeAsset,
) {
  const candidate = recordValue(candidateValue);
  return (
    candidate?.assetType === runtimeAsset.assetType &&
    candidate.assetIdentity === runtimeAsset.assetIdentity &&
    candidate.versionId === runtimeAsset.versionId &&
    candidate.versionNo === runtimeAsset.versionNo &&
    candidate.contentHash === runtimeAsset.contentHash
  );
}

function medicationSafetyRuleMatchesRuntime(
  candidateValue: unknown,
  runtimeAsset: MedicationSafetyRuntimeAsset,
) {
  const candidate = recordValue(candidateValue);
  return (
    candidate?.assetType === "RULE" &&
    candidate.assetIdentity === runtimeAsset.assetIdentity &&
    candidate.versionId === runtimeAsset.versionId &&
    candidate.versionNo === runtimeAsset.versionNo &&
    candidate.contentHash === runtimeAsset.contentHash
  );
}

function hasCompleteMedicationSafetyActivationRequest(
  value: unknown,
  assets: {
    safetyAsset: MedicationSafetyRuntimeAsset;
    cdssRiskAsset: MedicationSafetyRuntimeAsset;
    ruleAsset: MedicationSafetyRuntimeAsset;
  },
) {
  return [assets.safetyAsset, assets.cdssRiskAsset, assets.ruleAsset].every((asset) =>
    runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
      assetType: asset.assetType,
      assetIdentity: asset.assetIdentity,
      versionId: asset.versionId,
    }),
  );
}

function hasCompleteMedicationSafetyTerminologyGate(
  value: unknown,
  runtimeValue: unknown,
  activationRequestValue: unknown,
) {
  const terminology = recordValue(value);
  const runtime = recordValue(runtimeValue);
  if (
    !terminology ||
    terminology.assetType !== "TERMINOLOGY" ||
    !hasText(terminology.assetIdentity) ||
    !hasText(terminology.versionId) ||
    !hasText(terminology.versionNo) ||
    !isSha256(terminology.contentHash) ||
    terminology.standardSystem !== "ATC" ||
    terminology.standardCode !== "J01C" ||
    !hasText(terminology.localCode) ||
    !hasText(terminology.sourceSystem) ||
    terminology.category !== "DRUG" ||
    typeof terminology.mappingId !== "number" ||
    terminology.mappingId <= 0
  ) {
    return false;
  }
  const candidate = {
    assetType: "TERMINOLOGY",
    assetIdentity: String(terminology.assetIdentity),
    versionId: String(terminology.versionId),
  };
  const runtimeAssets = Array.isArray(runtime?.assets) ? runtime.assets : [];
  return (
    runtimeAssets.some((item) => {
      const asset = recordValue(item);
      return (
        asset?.assetType === "TERMINOLOGY" &&
        asset.assetIdentity === terminology.assetIdentity &&
        asset.versionId === terminology.versionId &&
        asset.versionNo === terminology.versionNo &&
        asset.contentHash === terminology.contentHash &&
        asset.entryState === "ACTIVE"
      );
    }) && runtimeReleasePayloadContainsCandidate(activationRequestValue, "activeAssets", candidate)
  );
}

function hasCompleteMedicationSafetyClinicalContext(value: unknown, runtimeReleaseId: string) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const medications = Array.isArray(resources?.medications) ? resources.medications : [];
  const allergies = Array.isArray(resources?.allergyIntolerances)
    ? resources.allergyIntolerances
    : [];
  return (
    hasText(context?.patientId) &&
    hasText(context?.contextSnapshotId) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    medications.some((item) => recordValue(item)?.code === "J01C") &&
    allergies.some((item) => {
      const allergy = recordValue(item);
      return (
        allergy?.code === "J01C" &&
        allergy.category === "medication" &&
        allergy.verificationStatus === "CONFIRMED"
      );
    })
  );
}

function hasCompleteMedicationSafetySpecialPopulationBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("特殊人群") &&
    statement.includes("代表切片") &&
    hasNegatedScopeTerm(statement, "完整妇产儿科老年特殊人群") &&
    hasNegatedScopeTerm(statement, "完整 S28") &&
    !hasScopeCompletionClaimWithoutNegation(statement, "完整 S28") &&
    !hasScopeCompletionClaimWithoutNegation(statement, "完整妇产儿科老年特殊人群")
  );
}

function hasCompleteMedicationSafetySpecialPopulationClinicalContext(value: unknown) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const patient = recordValue(resources?.patient);
  const contextPopulations = Array.isArray(context?.specialPopulations)
    ? context.specialPopulations
    : [];
  const patientPopulations = Array.isArray(patient?.specialPopulations)
    ? patient.specialPopulations
    : [];
  return (
    hasText(context?.patientId) &&
    hasText(patient?.mpi) &&
    medicationSafetyPopulationCodesComplete(contextPopulations) &&
    medicationSafetyPopulationCodesComplete(patientPopulations)
  );
}

function hasCompleteMedicationSafetySpecialPopulationRedline(
  value: unknown,
  riskMatrixValue: unknown,
  assets?: {
    specialPopulationSafetyAsset: MedicationSafetyRuntimeAsset | null;
  },
  activationRequestValue?: unknown,
) {
  const redline = recordValue(value);
  const risk = recordValue(riskMatrixValue);
  const runtimeAsset = assets?.specialPopulationSafetyAsset;
  return (
    redline?.assetType === "SAFETY" &&
    hasText(redline.assetIdentity) &&
    String(redline.assetIdentity).startsWith("SAFETY.RDL-MED-SPECIAL-POPULATION-") &&
    hasText(redline.redlineId) &&
    hasText(redline.redlineKey) &&
    String(redline.redlineKey).startsWith("RDL-MED-SPECIAL-POPULATION-") &&
    redline.category === "SPECIAL_POPULATION_CONTRAINDICATION" &&
    redline.hazardSeverity === "CRITICAL" &&
    redline.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    redline.lowerTenantOverrideAllowed === false &&
    redline.riskMatrixId === risk?.matrixId &&
    redline.riskMatrixVersion === risk?.matrixVersion &&
    redline.doseReviewRequired === true &&
    redline.contraindicationReviewRequired === true &&
    medicationSafetyPopulationCodesComplete(redline.populationCodes) &&
    hasText(redline.conditionDsl) &&
    String(redline.conditionDsl).includes("patient.specialPopulations") &&
    String(redline.conditionDsl).includes("medications[].code") &&
    hasText(redline.trialId) &&
    (runtimeAsset === undefined ||
      (runtimeAsset !== null &&
        medicationSafetyAssetMatchesRuntime(redline, runtimeAsset) &&
        runtimeReleasePayloadContainsCandidate(activationRequestValue, "activeAssets", {
          assetType: runtimeAsset.assetType,
          assetIdentity: runtimeAsset.assetIdentity,
          versionId: runtimeAsset.versionId,
        })))
  );
}

function hasCompleteMedicationSafetySpecialPopulationRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
  },
  triggerValue: unknown,
  riskMatrixValue: unknown,
  redlineValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const risk = recordValue(riskMatrixValue);
  const redline = recordValue(redlineValue);
  if (
    !recommendation ||
    !trigger ||
    !hasText(recommendation.cardId) ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING" ||
    recommendation.requiresPhysicianConfirmation !== true ||
    recommendation.noAutoOrder !== true
  ) {
    return false;
  }
  const relatedCardIds = Array.isArray(trigger.relatedCardIds)
    ? trigger.relatedCardIds.filter((cardId): cardId is string => hasText(cardId))
    : [];
  if (!relatedCardIds.includes(String(recommendation.cardId))) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const redlineExplanation = recordValue(explanation?.redlineExplanation);
  const specialPopulation = recordValue(explanation?.specialPopulation);
  const conditionEvidence = Array.isArray(redlineExplanation?.conditionEvidence)
    ? redlineExplanation.conditionEvidence
    : [];
  const riskMatrixExplanation = recommendation.riskMatrixExplanation;
  return (
    explanation?.matchType === "CLINICAL_REDLINE" &&
    explanation.redlineId === redline?.redlineId &&
    explanation.redlineKey === redline?.redlineKey &&
    explanation.riskMatrixId === risk?.matrixId &&
    explanation.riskMatrixVersion === risk?.matrixVersion &&
    hasText(riskMatrixExplanation) &&
    String(riskMatrixExplanation).includes("医师") &&
    String(riskMatrixExplanation).includes("确认") &&
    String(riskMatrixExplanation).includes("不自动开嘱") &&
    specialPopulation?.doseReviewRequired === true &&
    specialPopulation.contraindicationReviewRequired === true &&
    medicationSafetyPopulationCodesComplete(specialPopulation.populationCodes) &&
    medicationSafetyRuleConditionMatched(conditionEvidence, "patient.specialPopulations") &&
    medicationSafetyRuleConditionMatched(conditionEvidence, "medications[].code")
  );
}

function medicationSafetyPopulationCodesComplete(value: unknown) {
  return (
    Array.isArray(value) &&
    value.includes("PREGNANCY") &&
    value.includes("GERIATRIC") &&
    value.every((item) => typeof item === "string" && item.trim().length > 0)
  );
}

function hasCompleteMedicationSafetyTriggerEvidence(value: unknown, runtimeReleaseId: string) {
  const trigger = recordValue(value);
  if (!trigger) return false;
  const relatedCardIds = Array.isArray(trigger?.relatedCardIds)
    ? trigger.relatedCardIds.filter((cardId): cardId is string => hasText(cardId))
    : [];
  return (
    hasText(trigger?.triggerId) &&
    hasText(trigger?.contextSnapshotId) &&
    hasText(trigger?.cardId) &&
    relatedCardIds.includes(String(trigger.cardId)) &&
    trigger.runtimeReleaseId === runtimeReleaseId
  );
}

function hasCompleteMedicationSafetyRecommendationEvidence(
  value: unknown,
  runtime: {
    releaseId: string;
  },
  triggerValue: unknown,
  riskMatrixValue: unknown,
  redlineValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const risk = recordValue(riskMatrixValue);
  const redline = recordValue(redlineValue);
  if (
    !recommendation ||
    !trigger ||
    !hasText(recommendation.cardId) ||
    recommendation.cardId !== trigger.cardId ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING"
  ) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const redlineExplanation = recordValue(explanation?.redlineExplanation);
  const conditionEvidence = Array.isArray(redlineExplanation?.conditionEvidence)
    ? redlineExplanation.conditionEvidence
    : [];
  const riskMatrixExplanation = recommendation.riskMatrixExplanation;
  if (!hasText(riskMatrixExplanation)) {
    return false;
  }
  return (
    explanation?.matchType === "CLINICAL_REDLINE" &&
    explanation.redlineId === redline?.redlineId &&
    explanation.redlineKey === redline?.redlineKey &&
    explanation.riskMatrixId === risk?.matrixId &&
    explanation.riskMatrixVersion === risk?.matrixVersion &&
    String(riskMatrixExplanation).includes("医师") &&
    String(riskMatrixExplanation).includes("确认") &&
    conditionEvidence.some((item) => {
      const evidence = recordValue(item);
      return (
        evidence?.fact === "allergyIntolerances[].code" &&
        evidence.operator === "contains" &&
        evidence.matched === true
      );
    })
  );
}

function hasCompleteMedicationSafetyRuleRecommendationEvidence(
  value: unknown,
  runtime: {
    releaseId: string;
    ruleAsset: MedicationSafetyRuntimeAsset;
  },
  triggerValue: unknown,
  ruleValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const rule = recordValue(ruleValue);
  if (
    !recommendation ||
    !trigger ||
    !rule ||
    !hasText(recommendation.cardId) ||
    recommendation.cardId === trigger.cardId ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING"
  ) {
    return false;
  }
  const relatedCardIds = Array.isArray(trigger.relatedCardIds)
    ? trigger.relatedCardIds.filter((cardId): cardId is string => hasText(cardId))
    : [];
  if (!relatedCardIds.includes(String(recommendation.cardId))) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const runtimeRelease = recordValue(explanation?.runtimeRelease);
  const ruleExplanation = recordValue(explanation?.ruleExplanation);
  const conditionEvidence = Array.isArray(ruleExplanation?.conditionEvidence)
    ? ruleExplanation.conditionEvidence
    : [];
  return (
    explanation?.matchType === "RULE" &&
    explanation.ruleId === rule.ruleId &&
    explanation.ruleCode === rule.assetIdentity &&
    explanation.ruleVersionId === rule.ruleVersionId &&
    runtimeRelease?.runtimeReleaseId === runtime.releaseId &&
    runtimeRelease.assetVersionId === runtime.ruleAsset.versionId &&
    runtimeRelease.assetVersionNo === runtime.ruleAsset.versionNo &&
    runtimeRelease.contentHash === runtime.ruleAsset.contentHash &&
    hasText(ruleExplanation?.title) &&
    hasText(ruleExplanation?.reason) &&
    medicationSafetyRuleConditionMatched(conditionEvidence, "medications[].code") &&
    medicationSafetyRuleConditionMatched(conditionEvidence, "allergyIntolerances[].code")
  );
}

function medicationSafetyRuleConditionMatched(values: unknown[], fact: string) {
  return values.some((item) => {
    const evidence = recordValue(item);
    return evidence?.fact === fact && evidence.operator === "contains" && evidence.matched === true;
  });
}

function hasCompleteMedicationSafetyFeedbackEvidence(
  value: unknown,
  actionCardAsset?: PharmacyReviewRuntimeAsset,
  expectedCardId?: string | null,
) {
  const feedback = recordValue(value);
  const pharmacist = recordValue(feedback?.pharmacist);
  const physician = recordValue(feedback?.physician);
  const pharmacistPersisted = recordValue(pharmacist?.persisted);
  const physicianPersisted = recordValue(physician?.persisted);
  const actionCardEvidence = recordValue(feedback?.actionCardEvidence);
  return (
    pharmacist !== null &&
    hasText(pharmacist.feedbackId) &&
    (expectedCardId === undefined ||
      (hasText(expectedCardId) &&
        pharmacist.cardId === expectedCardId &&
        pharmacistPersisted?.cardId === expectedCardId)) &&
    pharmacist.cardStatus === "PENDING" &&
    pharmacist.canonicalSessionRole === "clinical-user" &&
    pharmacist.roleEvidence === "BUSINESS_FEEDBACK_ROLE_ONLY" &&
    pharmacistPersisted !== null &&
    pharmacistPersisted.feedbackId === pharmacist.feedbackId &&
    pharmacistPersisted.feedbackType === "VIEW_SOURCE" &&
    pharmacistPersisted.operatorRole === "PHARMACIST" &&
    pharmacistPersisted.reasonCode === "PHARMACIST_REVIEWED" &&
    physician !== null &&
    hasText(physician.feedbackId) &&
    (expectedCardId === undefined ||
      (hasText(expectedCardId) &&
        physician.cardId === expectedCardId &&
        physicianPersisted?.cardId === expectedCardId)) &&
    physician.cardStatus === "ACCEPTED" &&
    physician.canonicalSessionRole === "clinical-user" &&
    physician.roleEvidence === "BUSINESS_FEEDBACK_ROLE_ONLY" &&
    physicianPersisted !== null &&
    physicianPersisted.feedbackId === physician.feedbackId &&
    physicianPersisted.feedbackType === "ACCEPT" &&
    physicianPersisted.operatorRole === "DOCTOR" &&
    physicianPersisted.reasonCode === "CONFIRMED" &&
    hasText(physician.feedbackId) &&
    feedback?.noAutoOrder === true &&
    (actionCardAsset === undefined ||
      hasCompletePharmacyReviewActionCardFeedbackEvidence(actionCardEvidence, actionCardAsset))
  );
}

function hasCompletePharmacyReviewActionCardFeedbackEvidence(
  evidence: Record<string, unknown> | null,
  asset: PharmacyReviewRuntimeAsset,
) {
  return (
    evidence !== null &&
    evidence.assetType === "ACTION_CARD" &&
    evidence.assetIdentity === asset.assetIdentity &&
    evidence.versionId === asset.versionId &&
    evidence.versionNo === asset.versionNo &&
    evidence.contentHash === asset.contentHash &&
    evidence.entryState === "ACTIVE" &&
    evidence.requiresPhysicianConfirmation === true &&
    evidence.noAutoOrder === true
  );
}

type DiagnosticCriticalValueRuntimeAsset = {
  assetType: "KNOWLEDGE" | "FIELD_CATALOG" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
  sourceLayer: "PLATFORM" | "GROUP" | "HOSPITAL";
};

function hasCompleteDiagnosticCriticalValueApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "fhirObservationAccepted",
    "fhirDiagnosticReportAccepted",
    "contextSnapshotContainsInboundResources",
    "currentRuntimeContainsDiagnosticAssets",
    "reportInterpretationTriggeredFromFrontdesk",
    "criticalRecommendationPersisted",
    "workflowTodoCompletedByHuman",
  ].every((field) => evidence?.[field] === true);
}

function parseDiagnosticCriticalValueRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    !hasText(runtime.platformBaselineReleaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const knowledgeAsset = parseDiagnosticCriticalValueRuntimeAsset(
    runtime.knowledgeAsset,
    "KNOWLEDGE",
    "PLATFORM",
  );
  const fieldCatalogAsset = parseDiagnosticCriticalValueRuntimeAsset(
    runtime.fieldCatalogAsset,
    "FIELD_CATALOG",
    "PLATFORM",
  );
  const actionCardAsset = parseDiagnosticCriticalValueRuntimeAsset(
    runtime.actionCardAsset,
    "ACTION_CARD",
    "HOSPITAL",
  );
  if (!knowledgeAsset || !fieldCatalogAsset || !actionCardAsset) return null;
  const runtimeAssets = runtime.assets;
  if (
    ![knowledgeAsset, fieldCatalogAsset, actionCardAsset].every((asset) =>
      runtimeAssets.some((item) =>
        diagnosticCriticalValueRuntimeAssetMatches(item, asset, { requireActive: true }),
      ),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    platformBaselineReleaseId: String(runtime.platformBaselineReleaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    knowledgeAsset,
    fieldCatalogAsset,
    actionCardAsset,
  };
}

function parseDiagnosticCriticalValueRuntimeAsset(
  value: unknown,
  assetType: DiagnosticCriticalValueRuntimeAsset["assetType"],
  sourceLayer: DiagnosticCriticalValueRuntimeAsset["sourceLayer"],
): DiagnosticCriticalValueRuntimeAsset | null {
  const asset = recordValue(value);
  if (
    !asset ||
    asset.assetType !== assetType ||
    !hasText(asset.assetIdentity) ||
    !hasText(asset.versionId) ||
    !hasText(asset.versionNo) ||
    !isSha256(asset.contentHash) ||
    asset.entryState !== "ACTIVE" ||
    asset.sourceLayer !== sourceLayer
  ) {
    return null;
  }
  return {
    assetType,
    assetIdentity: String(asset.assetIdentity),
    versionId: String(asset.versionId),
    versionNo: String(asset.versionNo),
    contentHash: String(asset.contentHash),
    sourceLayer,
  };
}

function diagnosticCriticalValueRuntimeAssetMatches(
  value: unknown,
  asset: DiagnosticCriticalValueRuntimeAsset,
  options: { requireActive?: boolean } = {},
) {
  const candidate = recordValue(value);
  return (
    candidate?.assetType === asset.assetType &&
    candidate.assetIdentity === asset.assetIdentity &&
    candidate.versionId === asset.versionId &&
    candidate.versionNo === asset.versionNo &&
    candidate.contentHash === asset.contentHash &&
    (!options.requireActive || candidate.entryState === "ACTIVE")
  );
}

function hasCompleteDiagnosticCriticalValueActivationRequest(
  value: unknown,
  runtime: {
    platformBaselineReleaseId: string;
    knowledgeAsset: DiagnosticCriticalValueRuntimeAsset;
    fieldCatalogAsset: DiagnosticCriticalValueRuntimeAsset;
    actionCardAsset: DiagnosticCriticalValueRuntimeAsset;
  },
) {
  const request = recordValue(value);
  return Boolean(
    request?.platformBaselineReleaseId === runtime.platformBaselineReleaseId &&
      [runtime.knowledgeAsset, runtime.fieldCatalogAsset].every((asset) =>
        runtimeReleasePayloadContainsPlatformSelection(value, "activeAssets", asset),
      ) &&
      runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
        assetType: runtime.actionCardAsset.assetType,
        assetIdentity: runtime.actionCardAsset.assetIdentity,
        versionId: runtime.actionCardAsset.versionId,
      }),
  );
}

function hasCompleteDiagnosticCriticalValueInboundObservation(
  value: unknown,
  runtimeReleaseId: string,
) {
  const observation = recordValue(value);
  return (
    observation?.fhirResourceType === "Observation" &&
    observation.canonicalResourceType === "OBSERVATION" &&
    hasText(observation.fhirId) &&
    hasText(observation.snapshotId) &&
    observation.runtimeReleaseId === runtimeReleaseId &&
    hasText(observation.patientId) &&
    observation.sourceSystem === "FHIR_R4" &&
    hasText(observation.code) &&
    typeof observation.valueNumeric === "number" &&
    hasText(observation.unit) &&
    hasText(observation.criticalFlag) &&
    ["NOT_CONNECTED", "RETRYING"].includes(String(observation.integrationStatus)) &&
    hasDiagnosticCriticalValueNotConnectedEvidence(observation) &&
    observation.compensationStatus === "NOT_CONNECTED" &&
    hasText(observation.compensationMessageId)
  );
}

function hasCompleteDiagnosticCriticalValueInboundReport(
  value: unknown,
  runtimeReleaseId?: string,
) {
  const report = recordValue(value);
  return (
    report?.fhirResourceType === "DiagnosticReport" &&
    report.canonicalResourceType === "DIAGNOSTIC_REPORT" &&
    hasText(report.fhirId) &&
    hasText(report.snapshotId) &&
    (!runtimeReleaseId || report.runtimeReleaseId === runtimeReleaseId) &&
    hasText(report.patientId) &&
    report.sourceSystem === "FHIR_R4" &&
    hasText(report.reportType) &&
    hasText(report.conclusion) &&
    String(report.conclusion).includes("危急") &&
    report.signedStatus === "FINAL" &&
    ["NOT_CONNECTED", "RETRYING"].includes(String(report.integrationStatus)) &&
    hasDiagnosticCriticalValueNotConnectedEvidence(report) &&
    report.compensationStatus === "NOT_CONNECTED" &&
    hasText(report.compensationMessageId)
  );
}

function hasDiagnosticCriticalValueNotConnectedEvidence(value: Record<string, unknown>) {
  return (
    value.operationOutcomeContainsNotConnected === true ||
    value.compensationStatus === "NOT_CONNECTED"
  );
}

function hasCompleteDiagnosticCriticalValueClinicalContext(
  value: unknown,
  runtimeReleaseId: string,
  observationValue: unknown,
  reportValue: unknown,
) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const observation = recordValue(observationValue);
  const report = recordValue(reportValue);
  const observations = Array.isArray(resources?.observations) ? resources.observations : [];
  const reports = Array.isArray(resources?.diagnosticReports) ? resources.diagnosticReports : [];
  return (
    hasText(context?.patientId) &&
    hasText(context?.contextSnapshotId) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    context.contextSnapshotId === observation?.snapshotId &&
    context.contextSnapshotId === report?.snapshotId &&
    context.patientId === observation?.patientId &&
    context.patientId === report?.patientId &&
    observations.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        row.observationId === observation?.fhirId &&
        row.code === observation?.code &&
        hasText(row.criticalFlag) &&
        row.sourceSystem === "FHIR_R4"
      );
    }) &&
    reports.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        row.reportId === report?.fhirId &&
        row.reportType === report?.reportType &&
        hasText(row.conclusion) &&
        String(row.conclusion).includes("危急") &&
        row.sourceSystem === "FHIR_R4"
      );
    })
  );
}

function hasCompleteDiagnosticCriticalValueInterpretation(
  value: unknown,
  runtime: {
    releaseId: string;
    knowledgeAsset: DiagnosticCriticalValueRuntimeAsset;
  },
) {
  const interpretation = recordValue(value);
  const items = Array.isArray(interpretation?.interpretations)
    ? interpretation.interpretations
    : [];
  return (
    interpretation?.runtimeReleaseId === runtime.releaseId &&
    hasText(interpretation?.contextSnapshotId) &&
    hasText(interpretation?.advisoryNote) &&
    String(interpretation.advisoryNote).includes("不改写已签发报告") &&
    items.some((item) => {
      const row = recordValue(item);
      const recommendations = Array.isArray(row?.recommendations) ? row.recommendations : [];
      return (
        hasText(row?.reportId) &&
        row?.itemCode === runtime.knowledgeAsset.assetIdentity &&
        typeof row.sourceVersionId === "number" &&
        row.versionNo === runtime.knowledgeAsset.versionNo &&
        row.criticalRisk === true &&
        recommendations.some((text) => typeof text === "string" && text.includes("不自动"))
      );
    })
  );
}

function hasCompleteDiagnosticCriticalValueRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
    knowledgeAsset: DiagnosticCriticalValueRuntimeAsset;
    fieldCatalogAsset: DiagnosticCriticalValueRuntimeAsset;
    actionCardAsset: DiagnosticCriticalValueRuntimeAsset;
  },
) {
  const recommendation = recordValue(value);
  const explanation = recordValue(recommendation?.explanation);
  const runtimeAssetEvidence = Array.isArray(explanation?.runtimeAssetEvidence)
    ? explanation.runtimeAssetEvidence
    : [];
  return (
    hasText(recommendation?.cardId) &&
    recommendation?.cardStatus === "PENDING" &&
    recommendation.triggerRuntimeReleaseId === runtime.releaseId &&
    recommendation.cardType === "LAB" &&
    recommendation.requiresPhysicianConfirmation === true &&
    recommendation.aiGenerated === false &&
    explanation?.runtimeReleaseId === runtime.releaseId &&
    explanation.itemCode === runtime.knowledgeAsset.assetIdentity &&
    explanation.sourceContentHash === runtime.knowledgeAsset.contentHash &&
    explanation.criticalRisk === true &&
    diagnosticCriticalValueRuntimeAssetEvidenceMatches(
      runtimeAssetEvidence,
      runtime.fieldCatalogAsset,
    ) &&
    diagnosticCriticalValueRuntimeAssetEvidenceMatches(
      runtimeAssetEvidence,
      runtime.actionCardAsset,
    )
  );
}

function diagnosticCriticalValueRuntimeAssetEvidenceMatches(
  values: unknown[],
  asset: DiagnosticCriticalValueRuntimeAsset,
) {
  return values.some((item) => {
    const evidence = recordValue(item);
    if (
      evidence?.assetType !== asset.assetType ||
      evidence.assetIdentity !== asset.assetIdentity ||
      evidence.assetVersion !== asset.versionNo ||
      evidence.contentHash !== asset.contentHash
    ) {
      return false;
    }
    if (asset.assetType === "FIELD_CATALOG") {
      const fields = Array.isArray(evidence.fields) ? evidence.fields : [];
      return (
        fields.includes("observations[].criticalFlag") &&
        fields.includes("diagnosticReports[].conclusion")
      );
    }
    if (asset.assetType === "ACTION_CARD") {
      return evidence.requiresPhysicianConfirmation === true;
    }
    return true;
  });
}

function hasCompleteDiagnosticCriticalValueWorkflowTodo(
  value: unknown,
  recommendationValue: unknown,
) {
  const todo = recordValue(value);
  const recommendation = recordValue(recommendationValue);
  return (
    hasText(todo?.todoId) &&
    todo?.status === "COMPLETED" &&
    todo.category === "REPORT_INTERPRETATION" &&
    recommendation !== null &&
    hasText(recommendation.cardId) &&
    todo.sourceId === recommendation.cardId &&
    hasText(todo.completedBy) &&
    hasText(todo.completionReason) &&
    String(todo.completionReason).includes("不改写") &&
    todo.noAutoOrder === true
  );
}

function hasCompleteDiagnosticFamilyConsumerSlice(
  value: unknown,
  parsed: {
    inboundObservation?: unknown;
    inboundDiagnosticReport?: unknown;
    clinicalContext?: unknown;
    recommendation?: unknown;
    workflowTodo?: unknown;
  },
) {
  const slice = recordValue(value);
  const observation = recordValue(parsed.inboundObservation);
  const report = recordValue(parsed.inboundDiagnosticReport);
  const context = recordValue(parsed.clinicalContext);
  const resources = recordValue(context?.resources);
  const recommendation = recordValue(parsed.recommendation);
  const todo = recordValue(parsed.workflowTodo);
  const canonicalResources = Array.isArray(slice?.canonicalResources)
    ? slice.canonicalResources
    : [];
  const sourceSystems = Array.isArray(slice?.sourceSystems) ? slice.sourceSystems : [];
  return (
    slice !== null &&
    observation !== null &&
    report !== null &&
    context !== null &&
    recommendation !== null &&
    todo !== null &&
    slice.systemFamilyCode === "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG" &&
    hasText(slice.familyName) &&
    String(slice.familyName).includes("PACS/RIS") &&
    canonicalResources.includes("Observation") &&
    canonicalResources.includes("DiagnosticReport") &&
    sourceSystems.includes("PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG") &&
    sourceSystems.includes("FHIR_R4") &&
    slice.consumer === "REPORT_INTERPRETATION" &&
    slice.consumerVerified === true &&
    slice.standardResourceVerified === true &&
    slice.degradationVerified === true &&
    slice.auditVerified === true &&
    slice.noAutoOrder === true &&
    slice.noReportRewrite === true &&
    hasDiagnosticFamilyConsumerSliceScopeBoundary(slice.scopeStatement) &&
    observation.fhirResourceType === "Observation" &&
    observation.canonicalResourceType === "OBSERVATION" &&
    observation.sourceSystem === "FHIR_R4" &&
    report.fhirResourceType === "DiagnosticReport" &&
    report.canonicalResourceType === "DIAGNOSTIC_REPORT" &&
    report.sourceSystem === "FHIR_R4" &&
    hasDiagnosticCriticalValueNotConnectedEvidence(observation) &&
    hasDiagnosticCriticalValueNotConnectedEvidence(report) &&
    context.runtimeReleaseId === report.runtimeReleaseId &&
    context.patientId === report.patientId &&
    Array.isArray(resources?.diagnosticReports) &&
    resources.diagnosticReports.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        row.reportId === report.fhirId &&
        row.reportType === report.reportType &&
        row.sourceSystem === "FHIR_R4"
      );
    }) &&
    recommendation.cardType === "LAB" &&
    recommendation.triggerRuntimeReleaseId === report.runtimeReleaseId &&
    recommendation.requiresPhysicianConfirmation === true &&
    recommendation.aiGenerated === false &&
    todo.status === "COMPLETED" &&
    todo.category === "REPORT_INTERPRETATION" &&
    todo.sourceId === recommendation.cardId &&
    todo.noAutoOrder === true
  );
}

function hasDiagnosticFamilyConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表消费者切片") &&
    !hasUnnegatedDiagnosticFamilyConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整 PACS/RIS/病理/内镜/心电系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedDiagnosticFamilyConsumerSliceScopeClaim(statement: string) {
  return [
    "完整 PACS/RIS/病理/内镜/心电系统族覆盖",
    "完整PACS/RIS/病理/内镜/心电系统族覆盖",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

const requiredDiagnosticReportFamilyCodes = [
  "PACS_RIS",
  "ULTRASOUND",
  "PATHOLOGY",
  "ENDOSCOPY",
  "ECG",
];

function hasCompleteDiagnosticReportFamilyConsumerMatrix(
  value: unknown,
  parsed: {
    clinicalContext?: unknown;
    interpretation?: unknown;
    workflowTodo?: unknown;
  },
) {
  const matrix = recordValue(value);
  const context = recordValue(parsed.clinicalContext);
  const resources = recordValue(context?.resources);
  const interpretation = recordValue(parsed.interpretation);
  const todo = recordValue(parsed.workflowTodo);
  const rows = Array.isArray(matrix?.rows) ? matrix.rows.map(recordValue) : [];
  const canonicalResources = Array.isArray(matrix?.canonicalResources)
    ? matrix.canonicalResources
    : [];
  if (
    matrix === null ||
    context === null ||
    resources === null ||
    interpretation === null ||
    todo === null ||
    matrix.systemFamilyCode !== "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG" ||
    !hasText(matrix.matrixName) ||
    !String(matrix.matrixName).includes("五类医技报告族") ||
    !canonicalResources.includes("DiagnosticReport") ||
    matrix.consumer !== "REPORT_INTERPRETATION" ||
    matrix.consumerVerified !== true ||
    matrix.standardResourceVerified !== true ||
    matrix.degradationVerified !== true ||
    matrix.auditVerified !== true ||
    matrix.noAutoOrder !== true ||
    matrix.noReportRewrite !== true ||
    !hasText(matrix.runtimeKnowledgeScope) ||
    !String(matrix.runtimeKnowledgeScope).includes("不代表五类专属说明书全量发布") ||
    !hasDiagnosticReportFamilyMatrixScopeBoundary(matrix.scopeStatement) ||
    !Array.isArray(resources.diagnosticReports) ||
    !Array.isArray(interpretation.interpretations) ||
    todo.status !== "COMPLETED" ||
    todo.category !== "REPORT_INTERPRETATION" ||
    todo.noAutoOrder !== true ||
    rows.length !== requiredDiagnosticReportFamilyCodes.length
  ) {
    return false;
  }
  const seen = new Set<string>();
  for (const row of rows) {
    if (!row) return false;
    const familyCode = textValue(row.reportFamilyCode);
    if (!familyCode || !requiredDiagnosticReportFamilyCodes.includes(familyCode)) {
      return false;
    }
    if (seen.has(familyCode)) return false;
    seen.add(familyCode);
    if (!hasCompleteDiagnosticReportFamilyMatrixRow(row)) return false;
    if (
      !resources.diagnosticReports.some((item) =>
        diagnosticReportFamilyMatrixResourceMatches(item, row),
      )
    ) {
      return false;
    }
    if (
      !interpretation.interpretations.some((item) =>
        diagnosticReportFamilyMatrixInterpretationMatches(item, row),
      )
    ) {
      return false;
    }
  }
  return requiredDiagnosticReportFamilyCodes.every((code) => seen.has(code));
}

function hasCompleteDiagnosticReportFamilyMatrixRow(row: Record<string, unknown>) {
  return (
    hasText(row.reportFamilyName) &&
    hasText(row.fhirId) &&
    hasText(row.reportType) &&
    hasText(row.fhirCode) &&
    row.sourceSystem === "FHIR_R4" &&
    row.standardResourceVerified === true &&
    row.consumerVerified === true &&
    row.workflowTodoCompleted === true &&
    row.degradationVerified === true &&
    row.noReportRewrite === true &&
    row.noAutoOrder === true &&
    row.reportInterpretationId === row.fhirId &&
    hasText(row.workflowTodoId)
  );
}

function diagnosticReportFamilyMatrixResourceMatches(value: unknown, row: Record<string, unknown>) {
  const report = recordValue(value);
  if (!report) return false;
  return (
    report.reportId === row.fhirId &&
    report.reportType === row.reportType &&
    report.sourceSystem === "FHIR_R4" &&
    hasText(report.conclusion)
  );
}

function diagnosticReportFamilyMatrixInterpretationMatches(
  value: unknown,
  row: Record<string, unknown>,
) {
  const item = recordValue(value);
  if (!item) return false;
  return (
    item.reportId === row.fhirId &&
    item.reportType === row.reportType &&
    hasText(item.itemCode) &&
    hasText(item.versionNo) &&
    Array.isArray(item.recommendations) &&
    item.recommendations.some(
      (recommendation) =>
        typeof recommendation === "string" &&
        recommendation.includes("不自动") &&
        recommendation.includes("医嘱"),
    )
  );
}

function hasDiagnosticReportFamilyMatrixScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("五类医技报告族真实消费者矩阵代表切片") &&
    !hasUnnegatedDiagnosticReportFamilyMatrixScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整 PACS/RIS/病理/内镜/心电系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedDiagnosticReportFamilyMatrixScopeClaim(statement: string) {
  return [
    "完整 PACS/RIS/病理/内镜/心电系统族覆盖",
    "完整PACS/RIS/病理/内镜/心电系统族覆盖",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompleteRegionalDiagnosticMutualRecognitionApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "regionalRemoteOnboardingCreated",
    "regionalSourceRegisteredAndReadBack",
    "fhirDiagnosticReportAccepted",
    "contextSnapshotContainsRegionalReport",
    "currentRuntimeContainsMutualRecognitionAssets",
    "reportInterpretationTriggeredFromFrontdesk",
    "mutualRecognitionRecommendationPersisted",
    "workflowTodoCompletedByHuman",
  ].every((field) => evidence?.[field] === true);
}

function hasCompleteRegionalDiagnosticMutualRecognitionOnboarding(value: unknown) {
  const onboarding = recordValue(value);
  return (
    hasText(onboarding?.onboardingId) &&
    onboarding?.routeType === "FHIR" &&
    hasText(onboarding.routeReference) &&
    String(onboarding.routeReference).includes("/engine/integration/fhir/R4") &&
    onboarding.systemFamilyCode === "REGIONAL_REMOTE" &&
    onboarding.sourceSystem === "REGIONAL_FHIR" &&
    hasText(onboarding.businessScenario) &&
    String(onboarding.businessScenario).includes("S40") &&
    ["REQUESTED", "AUTH_CONFIGURED", "MAPPING_CONFIGURED", "ONLINE"].includes(
      String(onboarding.status),
    ) &&
    ["NOT_CONNECTED", "DEGRADED", "UNKNOWN", "HEALTHY"].includes(String(onboarding.healthStatus))
  );
}

function hasRegionalDiagnosticMutualRecognitionNotConnectedOnboarding(value: unknown) {
  const onboarding = recordValue(value);
  return (
    hasCompleteRegionalDiagnosticMutualRecognitionOnboarding(value) &&
    onboarding?.healthStatus === "NOT_CONNECTED"
  );
}

function hasCompleteRegionalDiagnosticMutualRecognitionSource(
  value: unknown,
  onboardingValue: unknown,
) {
  const source = recordValue(value);
  const onboarding = recordValue(onboardingValue);
  return (
    source !== null &&
    onboarding !== null &&
    hasText(source.sourceId) &&
    hasText(source.regionalNetworkName) &&
    hasText(source.sourceOrganizationId) &&
    hasText(source.sourceOrganizationName) &&
    ["HIGH", "MEDIUM"].includes(String(source.trustLevel)) &&
    hasText(source.evidenceText) &&
    String(source.evidenceText).includes("可信") &&
    source.onboardingId === onboarding.onboardingId &&
    hasText(source.orgPath) &&
    source.status === "ACTIVE"
  );
}

function hasCompleteRegionalDiagnosticMutualRecognitionInboundReport(
  value: unknown,
  runtimeReleaseId: string,
  sourceValue: unknown,
) {
  const report = recordValue(value);
  const source = recordValue(sourceValue);
  return (
    report !== null &&
    source !== null &&
    report.fhirResourceType === "DiagnosticReport" &&
    report.canonicalResourceType === "DIAGNOSTIC_REPORT" &&
    hasText(report.fhirId) &&
    hasText(report.snapshotId) &&
    report.runtimeReleaseId === runtimeReleaseId &&
    hasText(report.patientId) &&
    report.sourceSystem === "FHIR_R4" &&
    report.sourceRecordId === `DiagnosticReport/${report.fhirId}` &&
    hasText(report.reportType) &&
    hasText(report.conclusion) &&
    report.signedStatus === "FINAL" &&
    hasText(report.signedAt) &&
    report.regionalSourceId === source.sourceId &&
    report.sourceOrganizationId === source.sourceOrganizationId &&
    report.sourceOrganizationName === source.sourceOrganizationName &&
    hasText(report.mutualRecognitionReason) &&
    hasText(report.duplicateExamHint) &&
    String(report.duplicateExamHint).includes("不自动") &&
    ["NOT_CONNECTED", "RETRYING"].includes(String(report.integrationStatus)) &&
    hasDiagnosticCriticalValueNotConnectedEvidence(report) &&
    report.compensationStatus === "NOT_CONNECTED" &&
    hasText(report.compensationMessageId)
  );
}

function hasCompleteRegionalDiagnosticMutualRecognitionClinicalContext(
  value: unknown,
  runtimeReleaseId: string,
  reportValue: unknown,
) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const report = recordValue(reportValue);
  const reports = Array.isArray(resources?.diagnosticReports) ? resources.diagnosticReports : [];
  return (
    context !== null &&
    report !== null &&
    hasText(context.patientId) &&
    hasText(context.contextSnapshotId) &&
    context.runtimeReleaseId === runtimeReleaseId &&
    context.contextSnapshotId === report.snapshotId &&
    context.patientId === report.patientId &&
    reports.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        row.reportId === report.fhirId &&
        row.reportType === report.reportType &&
        hasText(row.conclusion) &&
        row.sourceSystem === "FHIR_R4"
      );
    })
  );
}

function hasCompleteRegionalDiagnosticMutualRecognitionInterpretation(
  value: unknown,
  runtime: {
    releaseId: string;
    knowledgeAsset: DiagnosticCriticalValueRuntimeAsset;
  },
  reportValue: unknown,
) {
  const interpretation = recordValue(value);
  const report = recordValue(reportValue);
  const items = Array.isArray(interpretation?.interpretations)
    ? interpretation.interpretations
    : [];
  return (
    interpretation !== null &&
    report !== null &&
    interpretation.runtimeReleaseId === runtime.releaseId &&
    interpretation.contextSnapshotId === report.snapshotId &&
    hasText(interpretation.advisoryNote) &&
    String(interpretation.advisoryNote).includes("不改写已签发报告") &&
    items.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      const recommendations = Array.isArray(row?.recommendations) ? row.recommendations : [];
      const highlights = Array.isArray(row?.abnormalHighlights) ? row.abnormalHighlights : [];
      return (
        row.reportId === report.fhirId &&
        row.itemCode === runtime.knowledgeAsset.assetIdentity &&
        typeof row.sourceVersionId === "number" &&
        row.versionNo === runtime.knowledgeAsset.versionNo &&
        row.criticalRisk === false &&
        highlights.length > 0 &&
        recommendations.some((text) => typeof text === "string" && text.includes("不自动"))
      );
    })
  );
}

function parseRegionalDiagnosticMutualRecognitionRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    !hasText(runtime.platformBaselineReleaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const knowledgeAsset = parseDiagnosticCriticalValueRuntimeAsset(
    runtime.knowledgeAsset,
    "KNOWLEDGE",
    "HOSPITAL",
  );
  const fieldCatalogAsset = parseDiagnosticCriticalValueRuntimeAsset(
    runtime.fieldCatalogAsset,
    "FIELD_CATALOG",
    "PLATFORM",
  );
  const actionCardAsset = parseDiagnosticCriticalValueRuntimeAsset(
    runtime.actionCardAsset,
    "ACTION_CARD",
    "HOSPITAL",
  );
  if (!knowledgeAsset || !fieldCatalogAsset || !actionCardAsset) return null;
  const runtimeAssets = runtime.assets;
  if (
    ![knowledgeAsset, fieldCatalogAsset, actionCardAsset].every((asset) =>
      runtimeAssets.some((item) =>
        diagnosticCriticalValueRuntimeAssetMatches(item, asset, { requireActive: true }),
      ),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    platformBaselineReleaseId: String(runtime.platformBaselineReleaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    knowledgeAsset,
    fieldCatalogAsset,
    actionCardAsset,
  };
}

function hasCompleteRegionalDiagnosticMutualRecognitionActivationRequest(
  value: unknown,
  runtime: {
    platformBaselineReleaseId: string;
    knowledgeAsset: DiagnosticCriticalValueRuntimeAsset;
    fieldCatalogAsset: DiagnosticCriticalValueRuntimeAsset;
    actionCardAsset: DiagnosticCriticalValueRuntimeAsset;
  },
) {
  const request = recordValue(value);
  return Boolean(
    request?.platformBaselineReleaseId === runtime.platformBaselineReleaseId &&
      runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
        assetType: runtime.knowledgeAsset.assetType,
        assetIdentity: runtime.knowledgeAsset.assetIdentity,
        versionId: runtime.knowledgeAsset.versionId,
      }) &&
      runtimeReleasePayloadContainsPlatformSelection(
        value,
        "activeAssets",
        runtime.fieldCatalogAsset,
      ) &&
      runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
        assetType: runtime.actionCardAsset.assetType,
        assetIdentity: runtime.actionCardAsset.assetIdentity,
        versionId: runtime.actionCardAsset.versionId,
      }),
  );
}

function hasCompleteRegionalDiagnosticMutualRecognitionRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
    knowledgeAsset: DiagnosticCriticalValueRuntimeAsset;
    fieldCatalogAsset: DiagnosticCriticalValueRuntimeAsset;
    actionCardAsset: DiagnosticCriticalValueRuntimeAsset;
  },
  reportValue: unknown,
) {
  const recommendation = recordValue(value);
  const explanation = recordValue(recommendation?.explanation);
  const report = recordValue(reportValue);
  const runtimeAssetEvidence = Array.isArray(explanation?.runtimeAssetEvidence)
    ? explanation.runtimeAssetEvidence
    : [];
  const recommendationTexts = Array.isArray(explanation?.recommendations)
    ? explanation.recommendations
    : [];
  return (
    recommendation !== null &&
    explanation !== null &&
    report !== null &&
    hasText(recommendation.cardId) &&
    recommendation.cardStatus === "PENDING" &&
    recommendation.triggerRuntimeReleaseId === runtime.releaseId &&
    recommendation.cardType === "EXAM" &&
    recommendation.requiresPhysicianConfirmation === true &&
    recommendation.aiGenerated === false &&
    hasText(recommendation.mutualRecognitionReason) &&
    hasText(recommendation.duplicateExamHint) &&
    String(recommendation.duplicateExamHint).includes("不自动") &&
    explanation.reportId === report.fhirId &&
    explanation.runtimeReleaseId === runtime.releaseId &&
    explanation.itemCode === runtime.knowledgeAsset.assetIdentity &&
    explanation.sourceContentHash === runtime.knowledgeAsset.contentHash &&
    explanation.criticalRisk === false &&
    recommendationTexts.some(
      (text) => typeof text === "string" && text.includes("人工") && text.includes("互认"),
    ) &&
    recommendationTexts.some((text) => typeof text === "string" && text.includes("不自动")) &&
    regionalDiagnosticMutualRecognitionRuntimeAssetEvidenceMatches(
      runtimeAssetEvidence,
      runtime.fieldCatalogAsset,
    ) &&
    regionalDiagnosticMutualRecognitionRuntimeAssetEvidenceMatches(
      runtimeAssetEvidence,
      runtime.actionCardAsset,
    )
  );
}

function regionalDiagnosticMutualRecognitionRuntimeAssetEvidenceMatches(
  values: unknown[],
  asset: DiagnosticCriticalValueRuntimeAsset,
) {
  return values.some((item) => {
    const evidence = recordValue(item);
    if (
      evidence?.assetType !== asset.assetType ||
      evidence.assetIdentity !== asset.assetIdentity ||
      evidence.assetVersion !== asset.versionNo ||
      evidence.contentHash !== asset.contentHash
    ) {
      return false;
    }
    if (asset.assetType === "FIELD_CATALOG") {
      const fields = Array.isArray(evidence.fields) ? evidence.fields : [];
      return (
        fields.includes("diagnosticReports[].conclusion") &&
        fields.some(
          (field) =>
            field === "diagnosticReports[].signedAt" || field === "observations[].criticalFlag",
        )
      );
    }
    if (asset.assetType === "ACTION_CARD") {
      return evidence.requiresPhysicianConfirmation === true;
    }
    return true;
  });
}

function hasCompleteRegionalDiagnosticMutualRecognitionWorkflowTodo(
  value: unknown,
  recommendationValue: unknown,
) {
  const todo = recordValue(value);
  const recommendation = recordValue(recommendationValue);
  return (
    todo !== null &&
    recommendation !== null &&
    hasText(todo.todoId) &&
    todo.status === "COMPLETED" &&
    todo.category === "REPORT_INTERPRETATION" &&
    hasText(recommendation.cardId) &&
    todo.sourceId === recommendation.cardId &&
    hasText(todo.completedBy) &&
    hasText(todo.completionReason) &&
    String(todo.completionReason).includes("人工") &&
    String(todo.completionReason).includes("不改写") &&
    todo.noAutoOrder === true &&
    todo.noAutoRecognition === true
  );
}

function hasRegionalDiagnosticMutualRecognitionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedRegionalDiagnosticMutualRecognitionScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整区域平台") &&
    hasNegatedScopeTerm(statement, "完整远程医疗") &&
    hasNegatedScopeTerm(statement, "完整 PACS/RIS/病理/内镜/心电系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S40") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedRegionalDiagnosticMutualRecognitionScopeClaim(statement: string) {
  return [
    "完整区域平台",
    "完整远程医疗",
    "完整 PACS/RIS/病理/内镜/心电系统族覆盖",
    "完整PACS/RIS/病理/内镜/心电系统族覆盖",
    "完整S40",
    "完整 S40",
    "完整S0-S40",
    "完整 S0-S40",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompleteRegionalRemoteConsumerSlice(body: Record<string, unknown>) {
  const runtime = parseRegionalDiagnosticMutualRecognitionRuntimeEvidence(body.runtime);
  if (!runtime) return false;
  const slice = recordValue(body.regionalRemoteConsumerSlice);
  const onboarding = recordValue(body.fhirOnboarding);
  const source = recordValue(body.regionalSource);
  const report = recordValue(body.inboundDiagnosticReport);
  const context = recordValue(body.clinicalContext);
  const recommendation = recordValue(body.recommendation);
  const todo = recordValue(body.workflowTodo);
  return (
    slice !== null &&
    onboarding !== null &&
    source !== null &&
    report !== null &&
    context !== null &&
    recommendation !== null &&
    todo !== null &&
    slice.systemFamilyCode === "REGIONAL_REMOTE" &&
    hasText(slice.familyName) &&
    (String(slice.familyName).includes("区域") || String(slice.familyName).includes("远程")) &&
    slice.consumer === "REGIONAL_DIAGNOSTIC_REPORT_MUTUAL_RECOGNITION" &&
    arrayEquals(slice.canonicalResources, ["Patient", "Encounter", "DiagnosticReport"]) &&
    arrayEquals(slice.sourceSystems, ["REGIONAL_REMOTE", "FHIR_R4", "MEDKERNEL_FRONTDESK"]) &&
    slice.onboardingVerified === true &&
    slice.trustedSourceVerified === true &&
    slice.standardResourceVerified === true &&
    slice.inboundDiagnosticReportVerified === true &&
    slice.degradationVerified === true &&
    (slice.runtimeConsumerVerified === true || slice.recommendationConsumerVerified === true) &&
    slice.interpretationVerified === true &&
    slice.recommendationVerified === true &&
    slice.humanTodoClosureVerified === true &&
    slice.auditVerified === true &&
    slice.permissionVerified === true &&
    slice.sixStateBoundaryVerified === true &&
    slice.requiresPhysicianConfirmation === true &&
    slice.noAutoOrder === true &&
    slice.noAutoRecognition === true &&
    slice.noReportRewrite === true &&
    slice.noExternalSuccessClaim === true &&
    slice.aiGenerated !== true &&
    slice.onboardingId === onboarding.onboardingId &&
    slice.sourceId === source.sourceId &&
    slice.fhirId === report.fhirId &&
    slice.patientId === context.patientId &&
    slice.contextSnapshotId === context.contextSnapshotId &&
    slice.runtimeReleaseId === runtime.releaseId &&
    slice.recommendationCardId === recommendation.cardId &&
    slice.todoId === todo.todoId &&
    slice.compensationMessageId === report.compensationMessageId &&
    slice.onboardingPath === "fhirOnboarding" &&
    slice.sourcePath === "regionalSource" &&
    slice.inboundPath === "inboundDiagnosticReport" &&
    slice.contextPath === "clinicalContext" &&
    slice.recommendationPath === "recommendation" &&
    slice.workflowTodoPath === "workflowTodo" &&
    hasRegionalRemoteConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasCompleteRegionalDiagnosticMutualRecognitionApiEvidence(body.apiEvidence) &&
    hasRegionalDiagnosticMutualRecognitionNotConnectedOnboarding(body.fhirOnboarding) &&
    hasCompleteRegionalDiagnosticMutualRecognitionSource(
      body.regionalSource,
      body.fhirOnboarding,
    ) &&
    hasCompleteRegionalDiagnosticMutualRecognitionActivationRequest(
      body.activationRequest,
      runtime,
    ) &&
    hasCompleteRegionalDiagnosticMutualRecognitionInboundReport(
      body.inboundDiagnosticReport,
      runtime.releaseId,
      body.regionalSource,
    ) &&
    hasCompleteRegionalDiagnosticMutualRecognitionClinicalContext(
      body.clinicalContext,
      runtime.releaseId,
      body.inboundDiagnosticReport,
    ) &&
    hasCompleteRegionalDiagnosticMutualRecognitionInterpretation(
      body.interpretation,
      runtime,
      body.inboundDiagnosticReport,
    ) &&
    hasCompleteRegionalDiagnosticMutualRecognitionRecommendation(
      body.recommendation,
      runtime,
      body.inboundDiagnosticReport,
    ) &&
    hasCompleteRegionalDiagnosticMutualRecognitionWorkflowTodo(
      body.workflowTodo,
      body.recommendation,
    ) &&
    evidencePathsResolve(body, [
      slice.onboardingPath,
      slice.sourcePath,
      slice.inboundPath,
      slice.contextPath,
      slice.recommendationPath,
      slice.workflowTodoPath,
    ])
  );
}

function hasRegionalRemoteConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedRegionalRemoteConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整区域平台") &&
    hasNegatedScopeTerm(statement, "完整远程医疗") &&
    hasNegatedScopeTerm(statement, "完整 PACS/RIS/病理/内镜/心电系统族覆盖") &&
    hasNegatedScopeTerm(statement, "真实外部区域平台成功联通") &&
    hasNegatedScopeTerm(statement, "自动互认") &&
    hasNegatedScopeTerm(statement, "自动开嘱") &&
    hasNegatedScopeTerm(statement, "改写报告") &&
    hasNegatedScopeTerm(statement, "完整 S40") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedRegionalRemoteConsumerSliceScopeClaim(statement: string) {
  return [
    "完整区域平台",
    "完整远程医疗",
    "完整 PACS/RIS/病理/内镜/心电系统族覆盖",
    "完整PACS/RIS/病理/内镜/心电系统族覆盖",
    "真实外部区域平台成功联通",
    "自动互认",
    "自动开嘱",
    "改写报告",
    "完整 S40",
    "完整S40",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线验收",
    "完整上线",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

type NursingContinuityRuntimeAsset = {
  assetType: "FOLLOWUP";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

function hasCompleteNursingContinuityApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "contextSnapshotCreatedFromFrontdesk",
    "nursingAssessmentReadback",
    "carePlanReadback",
    "followupTemplatePublished",
    "runtimeActivatedWithFollowupAsset",
    "followupPlanGeneratedFromFrontdesk",
    "questionnaireSubmitted",
    "abnormalReported",
    "resultBackflowPosted",
    "backflowContextContainsFollowUp",
  ].every((field) => evidence?.[field] === true);
}

function parseNursingContinuityRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const followupAsset = parseNursingContinuityRuntimeAsset(runtime.followupAsset);
  if (!followupAsset) return null;
  const runtimeAssets = runtime.assets;
  if (
    !runtimeAssets.some((item) =>
      nursingContinuityRuntimeAssetMatches(item, followupAsset, { requireActive: true }),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    followupAsset,
  };
}

function parseNursingContinuityRuntimeAsset(value: unknown): NursingContinuityRuntimeAsset | null {
  const asset = recordValue(value);
  if (
    !asset ||
    asset.assetType !== "FOLLOWUP" ||
    !hasText(asset.assetIdentity) ||
    !hasText(asset.versionId) ||
    !hasText(asset.versionNo) ||
    !isSha256(asset.contentHash) ||
    asset.entryState !== "ACTIVE"
  ) {
    return null;
  }
  return {
    assetType: "FOLLOWUP",
    assetIdentity: String(asset.assetIdentity),
    versionId: String(asset.versionId),
    versionNo: String(asset.versionNo),
    contentHash: String(asset.contentHash),
  };
}

function nursingContinuityRuntimeAssetMatches(
  value: unknown,
  asset: NursingContinuityRuntimeAsset,
  options: { requireActive?: boolean } = {},
) {
  const candidate = recordValue(value);
  return (
    candidate?.assetType === asset.assetType &&
    candidate.assetIdentity === asset.assetIdentity &&
    candidate.versionId === asset.versionId &&
    candidate.versionNo === asset.versionNo &&
    candidate.contentHash === asset.contentHash &&
    (!options.requireActive || candidate.entryState === "ACTIVE")
  );
}

function hasCompleteNursingContinuityActivationRequest(
  value: unknown,
  runtime: { followupAsset: NursingContinuityRuntimeAsset },
) {
  return runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
    assetType: runtime.followupAsset.assetType,
    assetIdentity: runtime.followupAsset.assetIdentity,
    versionId: runtime.followupAsset.versionId,
  });
}

function hasCompleteNursingContinuityClinicalContext(value: unknown, runtimeReleaseId: string) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const nursingAssessments = Array.isArray(resources?.nursingAssessments)
    ? resources.nursingAssessments
    : [];
  const carePlans = Array.isArray(resources?.carePlans) ? resources.carePlans : [];
  return (
    hasText(context?.patientId) &&
    hasText(context?.encounterId) &&
    hasText(context?.contextSnapshotId) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    nursingAssessments.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        hasText(row.assessmentId) &&
        hasText(row.assessmentType) &&
        hasText(row.riskLevel) &&
        hasText(row.status) &&
        hasText(row.sourceSystem)
      );
    }) &&
    carePlans.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        hasText(row.planId) &&
        hasText(row.pathwayId) &&
        hasText(row.currentNodeId) &&
        hasText(row.sourceSystem)
      );
    })
  );
}

function hasCompleteNursingContinuityHighRiskAssessment(value: unknown) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const nursingAssessments = Array.isArray(resources?.nursingAssessments)
    ? resources.nursingAssessments
    : [];
  const carePlans = Array.isArray(resources?.carePlans) ? resources.carePlans : [];
  return (
    nursingAssessments.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        hasText(row.assessmentId) &&
        hasText(row.assessmentType) &&
        row.riskLevel === "HIGH" &&
        row.status === "CONFIRMED" &&
        row.sourceSystem === "MEDKERNEL_FRONTDESK"
      );
    }) &&
    carePlans.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        hasText(row.planId) &&
        hasText(row.pathwayId) &&
        hasText(row.currentNodeId) &&
        row.sourceSystem === "MEDKERNEL_FRONTDESK"
      );
    })
  );
}

function hasCompleteNursingContinuityFollowupPlan(
  value: unknown,
  runtime: { releaseId: string; followupAsset: NursingContinuityRuntimeAsset },
  contextValue: unknown,
) {
  const plan = recordValue(value);
  const context = recordValue(contextValue);
  if (!plan || !context) return false;
  const explanation = parseExplanationObject(plan?.generationExplanation);
  const nursingEvidence = Array.isArray(explanation?.nursingAssessmentEvidence)
    ? explanation.nursingAssessmentEvidence
    : [];
  const carePlanEvidence = Array.isArray(explanation?.carePlanEvidence)
    ? explanation.carePlanEvidence
    : [];
  const runtimeAssetEvidence = Array.isArray(explanation?.runtimeAssetEvidence)
    ? explanation.runtimeAssetEvidence
    : [];
  const tasks = Array.isArray(plan?.tasks) ? plan.tasks : [];
  return (
    hasText(plan?.planId) &&
    plan.patientId === context?.patientId &&
    plan.encounterId === context?.encounterId &&
    plan.runtimeReleaseId === runtime.releaseId &&
    hasText(plan.templateId) &&
    typeof plan.templateVersion === "number" &&
    plan.modelStatus === "MODEL_DISABLED" &&
    hasText(plan.generationRuleCode) &&
    explanation?.runtimeReleaseId === runtime.releaseId &&
    nursingEvidence.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return hasText(row.assessmentId) && hasText(row.riskLevel) && hasText(row.status);
    }) &&
    carePlanEvidence.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return hasText(row.planId) && hasText(row.pathwayId) && hasText(row.currentNodeId);
    }) &&
    runtimeAssetEvidence.some((item) => {
      const row = recordValue(item);
      return (
        row?.assetType === "FOLLOWUP" &&
        row.assetIdentity === runtime.followupAsset.assetIdentity &&
        row.assetVersionId === runtime.followupAsset.versionId &&
        row.assetVersionNo === runtime.followupAsset.versionNo &&
        (!hasText(row.contentHash) || row.contentHash === runtime.followupAsset.contentHash)
      );
    }) &&
    tasks.some((item) => {
      const row = recordValue(item);
      return row?.taskType === "QUESTIONNAIRE" && row.status === "COMPLETED";
    })
  );
}

function hasCompleteNursingContinuityHighRiskFollowupExplanation(
  planValue: unknown,
  contextValue: unknown,
) {
  const plan = recordValue(planValue);
  const context = recordValue(contextValue);
  const resources = recordValue(context?.resources);
  const explanation = parseExplanationObject(plan?.generationExplanation);
  if (!plan || !context || !resources || !explanation) return false;
  const nursingEvidence = Array.isArray(explanation.nursingAssessmentEvidence)
    ? explanation.nursingAssessmentEvidence
    : [];
  const carePlanEvidence = Array.isArray(explanation.carePlanEvidence)
    ? explanation.carePlanEvidence
    : [];
  const nursingAssessments = Array.isArray(resources.nursingAssessments)
    ? resources.nursingAssessments
    : [];
  const carePlans = Array.isArray(resources.carePlans) ? resources.carePlans : [];
  return (
    nursingEvidence.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        hasText(row.assessmentId) &&
        row.riskLevel === "HIGH" &&
        row.status === "CONFIRMED" &&
        nursingAssessments.some((assessment) => {
          const clinicalRow = recordValue(assessment);
          if (!clinicalRow) return false;
          return (
            clinicalRow.assessmentId === row.assessmentId &&
            clinicalRow.riskLevel === "HIGH" &&
            clinicalRow.status === "CONFIRMED"
          );
        })
      );
    }) &&
    carePlanEvidence.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        hasText(row.planId) &&
        hasText(row.pathwayId) &&
        hasText(row.currentNodeId) &&
        carePlans.some((carePlan) => {
          const clinicalRow = recordValue(carePlan);
          if (!clinicalRow) return false;
          return (
            clinicalRow.planId === row.planId &&
            clinicalRow.pathwayId === row.pathwayId &&
            clinicalRow.currentNodeId === row.currentNodeId
          );
        })
      );
    })
  );
}

function hasCompleteNursingContinuityQuestionnaire(value: unknown, planValue: unknown) {
  const questionnaire = recordValue(value);
  const plan = recordValue(planValue);
  if (!questionnaire) return false;
  const tasks = Array.isArray(plan?.tasks) ? plan.tasks : [];
  return (
    hasText(questionnaire?.questionnaireId) &&
    hasText(questionnaire.taskId) &&
    hasText(questionnaire.questionnaireTemplateId) &&
    questionnaire.status === "COMPLETED" &&
    tasks.some((item) => {
      const task = recordValue(item);
      if (!task) return false;
      return (
        task.taskId === questionnaire.taskId &&
        task.taskType === "QUESTIONNAIRE" &&
        task.status === "COMPLETED"
      );
    })
  );
}

function hasCompleteNursingContinuityAbnormalReport(value: unknown, planValue: unknown) {
  const abnormal = recordValue(value);
  const plan = recordValue(planValue);
  if (!abnormal) return false;
  const tasks = Array.isArray(plan?.tasks) ? plan.tasks : [];
  return (
    hasText(abnormal?.eventId) &&
    hasText(abnormal.returnTaskId) &&
    hasText(abnormal.notificationEventId) &&
    tasks.some((item) => {
      const task = recordValue(item);
      if (!task) return false;
      return task.taskId === abnormal.returnTaskId && task.taskType === "RETURN_VISIT";
    })
  );
}

function hasCompleteNursingContinuityBackflow(
  resultValue: unknown,
  contextValue: unknown,
  runtimeReleaseId: string,
  questionnaireValue: unknown,
) {
  const result = recordValue(resultValue);
  const context = recordValue(contextValue);
  const questionnaire = recordValue(questionnaireValue);
  if (!result || !context || !questionnaire) return false;
  const resources = recordValue(context?.resources);
  const followUps = Array.isArray(resources?.followUps) ? resources.followUps : [];
  return (
    hasText(result?.eventId) &&
    hasText(result.contextSnapshotId) &&
    result.contextSnapshotId === context?.contextSnapshotId &&
    result.sourceQuestionnaireId === questionnaire?.questionnaireId &&
    hasText(result.abnormalFlag) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    followUps.some((item) => {
      const row = recordValue(item);
      if (!row) return false;
      return (
        row.followUpId === questionnaire.questionnaireId &&
        hasText(row.planType) &&
        hasText(row.questionnaireId) &&
        row.abnormalFlag === result.abnormalFlag &&
        row.sourceSystem === "FOLLOWUP" &&
        row.mappedVersion === "FOLLOWUP_RESULT" &&
        hasText(row.sourceRecordId)
      );
    })
  );
}

type PharmacyReviewRuntimeAsset = {
  assetType: "TERMINOLOGY" | "SAFETY" | "CDSS_RISK" | "RULE" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

function hasCompletePharmacyReviewApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "pharmacyReviewAdapterCreatedThroughRealService",
    "pharmacyReviewWebhookCreatedThroughRealService",
    "webhookSignaturePreviewGenerated",
    "antimicrobialTerminologyActivated",
    "antimicrobialRiskMatrixCreated",
    "antimicrobialSafetyAssetPromoted",
    "antimicrobialActionCardPublished",
    "antimicrobialRuleCreated",
    "runtimeActivatedWithAntimicrobialAssets",
    "contextSnapshotCreatedFromFrontdesk",
    "outboundReviewRequested",
    "inboundReviewAccepted",
    "clinicalEvaluationTriggeredFromFrontdesk",
    "pharmacistReviewRecordedWithoutClosingPhysicianConfirmation",
    "physicianConfirmationRecorded",
    "qualityRectificationSubmittedAndReviewed",
  ].every((field) => evidence?.[field] === true);
}

function parsePharmacyReviewRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const terminologyAsset = parsePharmacyReviewRuntimeAsset(runtime.terminologyAsset, "TERMINOLOGY");
  const safetyAsset = parsePharmacyReviewRuntimeAsset(runtime.safetyAsset, "SAFETY");
  const cdssRiskAsset = parsePharmacyReviewRuntimeAsset(runtime.cdssRiskAsset, "CDSS_RISK");
  const ruleAsset = parsePharmacyReviewRuntimeAsset(runtime.ruleAsset, "RULE");
  const actionCardAsset = parsePharmacyReviewRuntimeAsset(runtime.actionCardAsset, "ACTION_CARD");
  const runtimeAssets = runtime.assets;
  const assets = [terminologyAsset, safetyAsset, cdssRiskAsset, ruleAsset, actionCardAsset];
  if (
    assets.some((asset) => asset === null) ||
    !assets.every((asset) =>
      runtimeAssets.some((item) =>
        pharmacyReviewRuntimeAssetMatches(item, asset as PharmacyReviewRuntimeAsset, {
          requireActive: true,
        }),
      ),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    terminologyAsset: terminologyAsset as PharmacyReviewRuntimeAsset,
    safetyAsset: safetyAsset as PharmacyReviewRuntimeAsset,
    cdssRiskAsset: cdssRiskAsset as PharmacyReviewRuntimeAsset,
    ruleAsset: ruleAsset as PharmacyReviewRuntimeAsset,
    actionCardAsset: actionCardAsset as PharmacyReviewRuntimeAsset,
  };
}

function parsePharmacyReviewRuntimeAsset(
  value: unknown,
  assetType: PharmacyReviewRuntimeAsset["assetType"],
): PharmacyReviewRuntimeAsset | null {
  const asset = recordValue(value);
  if (
    !asset ||
    asset.assetType !== assetType ||
    !hasText(asset.assetIdentity) ||
    !hasText(asset.versionId) ||
    !hasText(asset.versionNo) ||
    !isSha256(asset.contentHash) ||
    asset.entryState !== "ACTIVE"
  ) {
    return null;
  }
  return {
    assetType,
    assetIdentity: String(asset.assetIdentity),
    versionId: String(asset.versionId),
    versionNo: String(asset.versionNo),
    contentHash: String(asset.contentHash),
  };
}

function pharmacyReviewRuntimeAssetMatches(
  value: unknown,
  asset: PharmacyReviewRuntimeAsset,
  options: { requireActive?: boolean } = {},
) {
  const candidate = recordValue(value);
  return (
    candidate?.assetType === asset.assetType &&
    candidate.assetIdentity === asset.assetIdentity &&
    candidate.versionId === asset.versionId &&
    candidate.versionNo === asset.versionNo &&
    candidate.contentHash === asset.contentHash &&
    (!options.requireActive || candidate.entryState === "ACTIVE")
  );
}

function hasCompletePharmacyReviewAdapterEvidence(value: unknown) {
  const adapter = recordValue(value);
  const mappings = Array.isArray(adapter?.fieldMappings) ? adapter.fieldMappings : [];
  return (
    hasText(adapter?.adapterId) &&
    adapter?.systemFamilyCode === "PHARMACY_REVIEW" &&
    adapter.sourceSystem === "PHARMACY_REVIEW" &&
    adapter.targetSystem === "PHARMACY_REVIEW" &&
    adapter.protocolType === "Webhook" &&
    pharmacyReviewMappingTargets(mappings, "/medications/0", "ATC") &&
    pharmacyReviewMappingTargets(mappings, "/conditions/0") &&
    pharmacyReviewMappingTargets(mappings, "/observations/0/code") &&
    pharmacyReviewMappingTargets(mappings, "/observations/0/valueNumeric") &&
    pharmacyReviewMappingTargets(mappings, "/pharmacyReview/reviewResult") &&
    pharmacyReviewMappingTargets(mappings, "/pharmacyReview/pharmacistOpinion")
  );
}

function pharmacyReviewMappingTargets(
  mappings: unknown[],
  targetPath: string,
  targetDictionaryKey?: string,
) {
  return mappings.some((item) => {
    const mapping = recordValue(item);
    return (
      mapping?.targetPath === targetPath &&
      (!targetDictionaryKey || mapping.targetDictionaryKey === targetDictionaryKey)
    );
  });
}

function hasCompletePharmacyReviewWebhookEvidence(webhookValue: unknown, adapterValue: unknown) {
  const webhook = recordValue(webhookValue);
  const adapter = recordValue(adapterValue);
  return (
    webhook !== null &&
    hasText(webhook.webhookId) &&
    webhook.adapterId === adapter?.adapterId &&
    webhook.signatureAlgorithm === "HMAC-SHA256" &&
    webhook.canonicalPayloadIncludesTraceId === true &&
    webhook.previewGenerated === true
  );
}

function hasCompletePharmacyReviewRiskMatrix(value: unknown) {
  const risk = recordValue(value);
  return (
    risk?.assetType === "CDSS_RISK" &&
    risk.assetIdentity === "CDSS.RISK.MATRIX" &&
    hasText(risk.matrixId) &&
    hasText(risk.matrixVersion) &&
    risk.triggerPoint === "medication-prescribe" &&
    risk.severityLevel === "CRITICAL" &&
    risk.automationLevel === "INFORM_ONLY" &&
    risk.riskLevel === "CRITICAL" &&
    risk.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    typeof risk.silentRunHours === "number" &&
    risk.silentRunHours >= 168 &&
    String(risk.releaseGate ?? "").includes("ANTIMICROBIAL") &&
    risk.autoExecutionAllowed === false
  );
}

function hasCompletePharmacyReviewSafetyRedline(value: unknown, riskMatrixValue: unknown) {
  const redline = recordValue(value);
  const risk = recordValue(riskMatrixValue);
  return (
    redline?.assetType === "SAFETY" &&
    hasText(redline.assetIdentity) &&
    String(redline.assetIdentity).startsWith("SAFETY.RDL-ANTIMICROBIAL") &&
    hasText(redline.redlineId) &&
    hasText(redline.redlineKey) &&
    hasText(redline.redlineVersion) &&
    redline.category === "ANTIMICROBIAL_RESTRICTION" &&
    redline.hazardSeverity === "CRITICAL" &&
    redline.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    redline.lowerTenantOverrideAllowed === false &&
    redline.riskMatrixId === risk?.matrixId &&
    redline.riskMatrixVersion === risk?.matrixVersion &&
    hasText(redline.releaseGate) &&
    String(redline.releaseGate).includes("ANTIMICROBIAL") &&
    hasText(redline.conditionDsl) &&
    String(redline.conditionDsl).includes("medications[].code") &&
    String(redline.conditionDsl).includes("observations[].valueNumeric") &&
    hasText(redline.trialId)
  );
}

function hasCompletePharmacyReviewActionCard(value: unknown) {
  const actionCard = recordValue(value);
  return (
    actionCard?.assetType === "ACTION_CARD" &&
    hasText(actionCard.assetIdentity) &&
    String(actionCard.assetIdentity).startsWith("ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL") &&
    actionCard.requiresPhysicianConfirmation === true &&
    actionCard.noAutoOrder === true
  );
}

function hasCompletePharmacyReviewRuleAsset(value: unknown) {
  const rule = recordValue(value);
  return (
    rule?.assetType === "RULE" &&
    hasText(rule.assetIdentity) &&
    String(rule.assetIdentity).startsWith("RULE.MEDICATION.PHARMACY_REVIEW.") &&
    hasText(rule.ruleId) &&
    hasText(rule.ruleVersionId)
  );
}

function hasCompletePharmacyReviewActivationRequest(
  value: unknown,
  runtime: {
    terminologyAsset: PharmacyReviewRuntimeAsset;
    safetyAsset: PharmacyReviewRuntimeAsset;
    cdssRiskAsset: PharmacyReviewRuntimeAsset;
    ruleAsset: PharmacyReviewRuntimeAsset;
    actionCardAsset: PharmacyReviewRuntimeAsset;
  },
) {
  return [
    runtime.terminologyAsset,
    runtime.safetyAsset,
    runtime.cdssRiskAsset,
    runtime.ruleAsset,
    runtime.actionCardAsset,
  ].every((asset) =>
    runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
      assetType: asset.assetType,
      assetIdentity: asset.assetIdentity,
      versionId: asset.versionId,
    }),
  );
}

function hasCompletePharmacyReviewTerminologyGate(
  value: unknown,
  runtimeValue: unknown,
  activationRequestValue: unknown,
) {
  const terminology = recordValue(value);
  const runtime = recordValue(runtimeValue);
  if (
    !terminology ||
    terminology.assetType !== "TERMINOLOGY" ||
    !hasText(terminology.assetIdentity) ||
    !hasText(terminology.versionId) ||
    !hasText(terminology.versionNo) ||
    !isSha256(terminology.contentHash) ||
    terminology.standardSystem !== "ATC" ||
    terminology.standardCode !== "J01C" ||
    !hasText(terminology.localCode) ||
    !hasText(terminology.sourceSystem) ||
    terminology.category !== "DRUG" ||
    typeof terminology.mappingId !== "number" ||
    terminology.mappingId <= 0
  ) {
    return false;
  }
  const diagnosis = recordValue(terminology.diagnosis);
  const pharmacyReview = recordValue(terminology.pharmacyReview);
  const pharmacyReviewDiagnosis = recordValue(terminology.pharmacyReviewDiagnosis);
  if (
    diagnosis?.standardSystem !== "ICD-10" ||
    diagnosis.standardCode !== "J18.900" ||
    !hasText(diagnosis.localCode) ||
    diagnosis.sourceSystem !== terminology.sourceSystem ||
    diagnosis.category !== "DIAGNOSIS" ||
    typeof diagnosis.mappingId !== "number" ||
    diagnosis.mappingId <= 0
  ) {
    return false;
  }
  if (
    pharmacyReview?.standardSystem !== "ATC" ||
    pharmacyReview.standardCode !== "J01C" ||
    pharmacyReview.localCode !== "J01C" ||
    pharmacyReview.sourceSystem !== "PHARMACY_REVIEW" ||
    pharmacyReview.category !== "DRUG" ||
    typeof pharmacyReview.mappingId !== "number" ||
    pharmacyReview.mappingId <= 0 ||
    pharmacyReviewDiagnosis?.standardSystem !== "ICD-10" ||
    pharmacyReviewDiagnosis.standardCode !== "J18.900" ||
    pharmacyReviewDiagnosis.localCode !== "J18.900" ||
    pharmacyReviewDiagnosis.sourceSystem !== "PHARMACY_REVIEW" ||
    pharmacyReviewDiagnosis.category !== "DIAGNOSIS" ||
    typeof pharmacyReviewDiagnosis.mappingId !== "number" ||
    pharmacyReviewDiagnosis.mappingId <= 0
  ) {
    return false;
  }
  const candidate = {
    assetType: "TERMINOLOGY",
    assetIdentity: String(terminology.assetIdentity),
    versionId: String(terminology.versionId),
  };
  const runtimeAssets = Array.isArray(runtime?.assets) ? runtime.assets : [];
  return (
    runtimeAssets.some((item) => {
      const asset = recordValue(item);
      return (
        asset?.assetType === "TERMINOLOGY" &&
        asset.assetIdentity === terminology.assetIdentity &&
        asset.versionId === terminology.versionId &&
        asset.versionNo === terminology.versionNo &&
        asset.contentHash === terminology.contentHash &&
        asset.entryState === "ACTIVE"
      );
    }) && runtimeReleasePayloadContainsCandidate(activationRequestValue, "activeAssets", candidate)
  );
}

function hasCompletePharmacyReviewClinicalContext(value: unknown, runtimeReleaseId: string) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const medications = Array.isArray(resources?.medications) ? resources.medications : [];
  const allergies = Array.isArray(resources?.allergyIntolerances)
    ? resources.allergyIntolerances
    : [];
  const conditions = Array.isArray(resources?.conditions) ? resources.conditions : [];
  const observations = Array.isArray(resources?.observations) ? resources.observations : [];
  return (
    hasText(context?.patientId) &&
    hasText(context?.encounterId) &&
    hasText(context?.contextSnapshotId) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    medications.some((item) => recordValue(item)?.code === "J01C") &&
    allergies.some((item) => {
      const allergy = recordValue(item);
      return (
        allergy?.code === "J01C" &&
        allergy.category === "medication" &&
        allergy.verificationStatus === "CONFIRMED"
      );
    }) &&
    conditions.some((item) => {
      const condition = recordValue(item);
      return condition?.code === "J18.900" && condition.codeSystem === "ICD-10";
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return (
        observation !== null &&
        hasText(observation.code) &&
        typeof observation.valueNumeric === "number" &&
        hasText(observation.unit) &&
        hasText(observation.sourceSystem)
      );
    })
  );
}

function hasCompletePharmacyReviewOutbound(
  value: unknown,
  adapterValue: unknown,
  contextValue: unknown,
) {
  const outbound = recordValue(value);
  const adapter = recordValue(adapterValue);
  const context = recordValue(contextValue);
  const payload = recordValue(outbound?.payload);
  return (
    outbound !== null &&
    payload !== null &&
    hasText(outbound.messageId) &&
    hasText(outbound.traceId) &&
    outbound.adapterId === adapter?.adapterId &&
    outbound.targetSystem === "PHARMACY_REVIEW" &&
    outbound.protocolType === "Webhook" &&
    ["NOT_CONNECTED", "RETRYING"].includes(String(outbound.status)) &&
    outbound.compensationStatus === "NOT_CONNECTED" &&
    hasText(outbound.compensationMessageId) &&
    outbound.blocksMainFlow === false &&
    outbound.compensationRequired === true &&
    payload?.patientId === context?.patientId &&
    payload.contextSnapshotId === context?.contextSnapshotId &&
    payload.medicationCode === "J01C" &&
    payload.infectionCode === "J18.900" &&
    payload.observationCode === "PCT" &&
    typeof payload.pct === "number"
  );
}

function hasCompletePharmacyReviewInbound(
  value: unknown,
  adapterValue: unknown,
  webhookValue: unknown,
  outboundValue: unknown,
  runtimeReleaseId: string,
) {
  const inbound = recordValue(value);
  const adapter = recordValue(adapterValue);
  const webhook = recordValue(webhookValue);
  const outbound = recordValue(outboundValue);
  const outboundPayload = recordValue(outbound?.payload);
  const mappedPayload = recordValue(inbound?.mappedPayload);
  const signedPayload = recordValue(inbound?.signedPayload);
  const review = recordValue(mappedPayload?.pharmacyReview);
  const clinicalEvent = recordValue(inbound?.clinicalEvent);
  const medications = Array.isArray(mappedPayload?.medications) ? mappedPayload.medications : [];
  const conditions = Array.isArray(mappedPayload?.conditions) ? mappedPayload.conditions : [];
  const observations = Array.isArray(mappedPayload?.observations) ? mappedPayload.observations : [];
  return (
    inbound !== null &&
    review !== null &&
    hasText(inbound.messageId) &&
    inbound.traceId === outbound?.traceId &&
    inbound.adapterId === adapter?.adapterId &&
    inbound.webhookId === webhook?.webhookId &&
    inbound.patientId === outboundPayload?.patientId &&
    hasText(inbound.encounterId) &&
    inbound.contextSnapshotId === outboundPayload?.contextSnapshotId &&
    inbound.sourceSystem === "PHARMACY_REVIEW" &&
    inbound.status === "SUCCESS" &&
    inbound.clinicalEventStatus === "RECEIVED" &&
    hasProcessedPharmacyReviewClinicalEvent(clinicalEvent, runtimeReleaseId) &&
    typeof inbound.mappedFieldCount === "number" &&
    inbound.mappedFieldCount >= 7 &&
    signedPayload?.medicationCode === "J01C" &&
    signedPayload.infectionCode === "J18.900" &&
    signedPayload.observationCode === "PCT" &&
    typeof signedPayload.pct === "number" &&
    hasText(review?.reviewResult) &&
    hasText(review.pharmacistOpinion) &&
    medications.some((item) => {
      const medication = recordValue(item);
      return (
        medication?.standardCode === "J01C" && medication.runtimeReleaseId === runtimeReleaseId
      );
    }) &&
    conditions.some((item) => {
      const condition = recordValue(item);
      return (
        condition?.standardCode === "J18.900" &&
        condition.codeSystem === "ICD-10" &&
        condition.sourceSystem === "PHARMACY_REVIEW" &&
        condition.runtimeReleaseId === runtimeReleaseId
      );
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return observation?.code === "PCT" && typeof observation.valueNumeric === "number";
    })
  );
}

function hasProcessedPharmacyReviewClinicalEvent(
  clinicalEvent: Record<string, unknown> | null,
  runtimeReleaseId: string,
) {
  return (
    clinicalEvent !== null &&
    hasText(clinicalEvent.eventId) &&
    clinicalEvent.status === "PROCESSED" &&
    clinicalEvent.runtimeReleaseId === runtimeReleaseId &&
    (clinicalEvent.errorCode === null || clinicalEvent.errorCode === undefined) &&
    (clinicalEvent.errorClass === null || clinicalEvent.errorClass === undefined)
  );
}

function hasPharmacyReviewAntimicrobialScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasPositiveCompleteScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整药事治理") &&
    hasNegatedScopeTerm(statement, "完整抗菌药物分级管理") &&
    hasNegatedScopeTerm(statement, "第三方药房审方系统族完整覆盖")
  );
}

function hasPositiveCompleteScopeClaim(statement: string) {
  return /完整(?:药事治理|抗菌药物分级管理|第三方药房审方系统族完整覆盖)(?:已上线|完整上线|完成上线|已完成|完整覆盖|全面覆盖)/u.test(
    statement,
  );
}

function hasNegatedScopeTerm(statement: string, term: string) {
  const segments = statement
    .split(/[。；;.!?！？\n]/u)
    .map((segment) => segment.trim())
    .filter(Boolean);
  const matchingSegments = segments.filter((segment) => segment.includes(term));
  return (
    matchingSegments.length > 0 &&
    matchingSegments.every((segment) => {
      const termIndex = segment.indexOf(term);
      const prefix = segment.slice(0, termIndex);
      return (
        /(?:不代表|不声明|未完成|不得外推|不能外推|不等于|并非|不是)/u.test(prefix) ||
        /(?:不代表|不声明|未完成|不得外推|不能外推|不等于|并非|不是)/u.test(segment)
      );
    })
  );
}

function hasCompletePharmacyReviewTrigger(value: unknown, runtimeReleaseId: string) {
  return hasCompleteMedicationSafetyTriggerEvidence(value, runtimeReleaseId);
}

function hasCompletePharmacyReviewRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
    actionCardAsset: PharmacyReviewRuntimeAsset;
  },
  triggerValue: unknown,
  riskMatrixValue: unknown,
  redlineValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const risk = recordValue(riskMatrixValue);
  const redline = recordValue(redlineValue);
  if (
    !recommendation ||
    !trigger ||
    !hasText(recommendation.cardId) ||
    recommendation.cardId !== trigger.cardId ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING" ||
    recommendation.requiresPhysicianConfirmation !== true ||
    recommendation.aiGenerated !== false
  ) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const redlineExplanation = recordValue(explanation?.redlineExplanation);
  const conditionEvidence = Array.isArray(redlineExplanation?.conditionEvidence)
    ? redlineExplanation.conditionEvidence
    : [];
  return (
    explanation?.matchType === "CLINICAL_REDLINE" &&
    explanation.redlineId === redline?.redlineId &&
    explanation.redlineKey === redline?.redlineKey &&
    explanation.riskMatrixId === risk?.matrixId &&
    explanation.riskMatrixVersion === risk?.matrixVersion &&
    pharmacyReviewConditionMatched(conditionEvidence, "medications[].code") &&
    pharmacyReviewConditionMatched(conditionEvidence, "observations[].valueNumeric") &&
    hasText(recommendation.riskMatrixExplanation) &&
    String(recommendation.riskMatrixExplanation).includes("医师") &&
    String(recommendation.riskMatrixExplanation).includes("确认")
  );
}

function hasCompletePharmacyReviewRuleRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
    ruleAsset: PharmacyReviewRuntimeAsset;
    actionCardAsset: PharmacyReviewRuntimeAsset;
  },
  triggerValue: unknown,
  ruleValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const rule = recordValue(ruleValue);
  if (
    !recommendation ||
    !trigger ||
    !rule ||
    !hasText(recommendation.cardId) ||
    recommendation.cardId === trigger.cardId ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING"
  ) {
    return false;
  }
  const relatedCardIds = Array.isArray(trigger.relatedCardIds)
    ? trigger.relatedCardIds.filter((cardId): cardId is string => hasText(cardId))
    : [];
  if (!relatedCardIds.includes(String(recommendation.cardId))) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const runtimeRelease = recordValue(explanation?.runtimeRelease);
  const ruleExplanation = recordValue(explanation?.ruleExplanation);
  const conditionEvidence = Array.isArray(ruleExplanation?.conditionEvidence)
    ? ruleExplanation.conditionEvidence
    : [];
  const runtimeAssetEvidence = Array.isArray(ruleExplanation?.runtimeAssetEvidence)
    ? ruleExplanation.runtimeAssetEvidence
    : [];
  return (
    explanation?.matchType === "RULE" &&
    explanation.ruleId === rule.ruleId &&
    explanation.ruleCode === rule.assetIdentity &&
    explanation.ruleVersionId === rule.ruleVersionId &&
    runtimeRelease?.runtimeReleaseId === runtime.releaseId &&
    runtimeRelease.assetVersionId === runtime.ruleAsset.versionId &&
    runtimeRelease.assetVersionNo === runtime.ruleAsset.versionNo &&
    runtimeRelease.contentHash === runtime.ruleAsset.contentHash &&
    hasText(ruleExplanation?.title) &&
    hasText(ruleExplanation?.reason) &&
    pharmacyReviewConditionMatched(conditionEvidence, "medications[].code") &&
    pharmacyReviewConditionMatched(conditionEvidence, "conditions[].code") &&
    pharmacyReviewConditionMatched(conditionEvidence, "observations[].valueNumeric") &&
    hasCompletePharmacyReviewActionCardEvidence(runtimeAssetEvidence, runtime.actionCardAsset)
  );
}

function pharmacyReviewConditionMatched(values: unknown[], fact: string) {
  return values.some((item) => {
    const evidence = recordValue(item);
    return evidence?.fact === fact && evidence.matched === true;
  });
}

function hasCompletePharmacyReviewActionCardEvidence(
  values: unknown[],
  asset: PharmacyReviewRuntimeAsset,
) {
  return values.some((item) => {
    const evidence = recordValue(item);
    return (
      evidence?.assetType === "ACTION_CARD" &&
      evidence.assetIdentity === asset.assetIdentity &&
      evidence.assetVersion === asset.versionNo &&
      evidence.contentHash === asset.contentHash &&
      evidence.requiresPhysicianConfirmation === true
    );
  });
}

function hasCompletePharmacyReviewRectification(value: unknown, recommendationValue: unknown) {
  const rectification = recordValue(value);
  const recommendation = recordValue(recommendationValue);
  return (
    rectification !== null &&
    hasText(rectification.findingId) &&
    rectification.sourceType === "PHARMACY_REVIEW" &&
    recommendation !== null &&
    rectification.sourceId === recommendation.cardId &&
    ["P0", "P1"].includes(String(rectification.severity)) &&
    rectification.findingStatus === "CLOSED" &&
    hasText(rectification.taskId) &&
    rectification.taskStatus === "CLOSED" &&
    rectification.submittedByRole === "engine-operator" &&
    rectification.reviewedByRole === "engine-operator" &&
    rectification.roleEvidence === "CANONICAL_FIXED_ROLE_EVALUATION_REMEDIATE_REVIEW" &&
    hasText(rectification.submittedEvidenceRef) &&
    rectification.reviewDecision === "APPROVED"
  );
}

function hasCompletePharmacyReviewConsumerSlice(body: Record<string, unknown>) {
  const runtime = parsePharmacyReviewRuntimeEvidence(body.runtime);
  if (!runtime) return false;
  const slice = recordValue(body.pharmacyReviewConsumerSlice);
  const context = recordValue(body.clinicalContext);
  const outbound = recordValue(body.outboundReview);
  const inbound = recordValue(body.inboundReview);
  const recommendation = recordValue(body.recommendation);
  const ruleRecommendation = recordValue(body.ruleRecommendation);
  const feedback = recordValue(body.feedback);
  const pharmacist = recordValue(feedback?.pharmacist);
  const physician = recordValue(feedback?.physician);
  const rectification = recordValue(body.qualityRectification);
  const actionCard = recordValue(body.actionCard);
  const rule = recordValue(body.ruleAsset);
  return (
    slice !== null &&
    context !== null &&
    outbound !== null &&
    inbound !== null &&
    recommendation !== null &&
    ruleRecommendation !== null &&
    pharmacist !== null &&
    physician !== null &&
    rectification !== null &&
    actionCard !== null &&
    rule !== null &&
    slice.systemFamilyCode === "PHARMACY_REVIEW" &&
    hasText(slice.familyName) &&
    String(slice.familyName).includes("药房审方") &&
    slice.consumer === "ANTIMICROBIAL_REVIEW_RECOMMENDATION_RECTIFICATION" &&
    arrayEquals(slice.canonicalResources, [
      "Patient",
      "Encounter",
      "Medication",
      "AllergyIntolerance",
      "Condition",
      "Observation",
    ]) &&
    arrayEquals(slice.sourceSystems, ["MEDKERNEL_FRONTDESK", "PHARMACY_REVIEW"]) &&
    (slice.adapterVerified === true || slice.adapterCreatedThroughRealService === true) &&
    (slice.webhookSignatureVerified === true ||
      slice.webhookCreatedThroughRealService === true ||
      slice.signaturePreviewGenerated === true) &&
    (slice.outboundDegradationVerified === true || slice.outboundNotConnectedVerified === true) &&
    (slice.inboundReviewVerified === true ||
      slice.signedInboundProcessedVerified === true ||
      slice.clinicalEventProcessedVerified === true) &&
    (slice.runtimeConsumerVerified === true || slice.recommendationConsumerVerified === true) &&
    slice.pharmacistReviewVerified === true &&
    slice.physicianConfirmationVerified === true &&
    slice.rectificationClosedVerified === true &&
    slice.auditVerified === true &&
    slice.permissionVerified === true &&
    slice.sixStateBoundaryVerified === true &&
    slice.requiresPhysicianConfirmation === true &&
    slice.noAutoOrder === true &&
    slice.noExternalSuccessClaim === true &&
    slice.aiGenerated !== true &&
    slice.patientId === context.patientId &&
    slice.encounterId === context.encounterId &&
    slice.contextSnapshotId === context.contextSnapshotId &&
    slice.runtimeReleaseId === runtime.releaseId &&
    slice.recommendationCardId === recommendation.cardId &&
    slice.ruleRecommendationCardId === ruleRecommendation.cardId &&
    slice.pharmacistFeedbackId === pharmacist.feedbackId &&
    slice.physicianFeedbackId === physician.feedbackId &&
    slice.findingId === rectification.findingId &&
    slice.taskId === rectification.taskId &&
    (!hasText(slice.adapterId) || slice.adapterId === recordValue(body.adapter)?.adapterId) &&
    (!hasText(slice.webhookId) ||
      slice.webhookId === recordValue(body.webhookSignature)?.webhookId) &&
    (!hasText(slice.clinicalEventId) ||
      slice.clinicalEventId === recordValue(inbound.clinicalEvent)?.eventId) &&
    (!hasText(slice.actionCardAssetIdentity) ||
      slice.actionCardAssetIdentity === actionCard.assetIdentity) &&
    (!hasText(slice.ruleAssetIdentity) || slice.ruleAssetIdentity === rule.assetIdentity) &&
    slice.outboundPath === "outboundReview" &&
    slice.inboundPath === "inboundReview" &&
    slice.feedbackPath === "feedback" &&
    slice.rectificationPath === "qualityRectification" &&
    hasPharmacyReviewConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasCompletePharmacyReviewApiEvidence(body.apiEvidence) &&
    hasCompletePharmacyReviewAdapterEvidence(body.adapter) &&
    hasCompletePharmacyReviewWebhookEvidence(body.webhookSignature, body.adapter) &&
    pharmacyReviewRuntimeAssetMatches(body.terminologyGate, runtime.terminologyAsset) &&
    pharmacyReviewRuntimeAssetMatches(body.riskMatrix, runtime.cdssRiskAsset) &&
    pharmacyReviewRuntimeAssetMatches(body.safetyRedline, runtime.safetyAsset) &&
    pharmacyReviewRuntimeAssetMatches(body.ruleAsset, runtime.ruleAsset) &&
    pharmacyReviewRuntimeAssetMatches(body.actionCard, runtime.actionCardAsset) &&
    hasCompletePharmacyReviewRiskMatrix(body.riskMatrix) &&
    hasCompletePharmacyReviewSafetyRedline(body.safetyRedline, body.riskMatrix) &&
    hasCompletePharmacyReviewActionCard(body.actionCard) &&
    hasCompletePharmacyReviewRuleAsset(body.ruleAsset) &&
    hasCompletePharmacyReviewActivationRequest(body.activationRequest, runtime) &&
    hasCompletePharmacyReviewTerminologyGate(
      body.terminologyGate,
      body.runtime,
      body.activationRequest,
    ) &&
    hasCompletePharmacyReviewClinicalContext(body.clinicalContext, runtime.releaseId) &&
    hasCompletePharmacyReviewOutbound(body.outboundReview, body.adapter, body.clinicalContext) &&
    hasCompletePharmacyReviewInbound(
      body.inboundReview,
      body.adapter,
      body.webhookSignature,
      body.outboundReview,
      runtime.releaseId,
    ) &&
    hasCompletePharmacyReviewTrigger(body.clinicalTrigger, runtime.releaseId) &&
    hasCompletePharmacyReviewRecommendation(
      body.recommendation,
      runtime,
      body.clinicalTrigger,
      body.riskMatrix,
      body.safetyRedline,
    ) &&
    hasCompletePharmacyReviewRuleRecommendation(
      body.ruleRecommendation,
      runtime,
      body.clinicalTrigger,
      body.ruleAsset,
    ) &&
    hasCompleteMedicationSafetyFeedbackEvidence(body.feedback, runtime.actionCardAsset) &&
    hasCompletePharmacyReviewRectification(body.qualityRectification, body.recommendation) &&
    evidencePathsResolve(body, [
      slice.outboundPath,
      slice.inboundPath,
      slice.feedbackPath,
      slice.rectificationPath,
    ])
  );
}

function hasPharmacyReviewConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedPharmacyReviewConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整药房审方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整药事治理") &&
    hasNegatedScopeTerm(statement, "完整抗菌药物分级管理") &&
    hasNegatedScopeTerm(statement, "真实外部药房审方成功联通") &&
    hasNegatedScopeTerm(statement, "自动开嘱") &&
    hasNegatedScopeTerm(statement, "完整 S18") &&
    hasNegatedScopeTerm(statement, "完整 S31") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedPharmacyReviewConsumerSliceScopeClaim(statement: string) {
  return [
    "完整药房审方系统族覆盖",
    "完整药房审方系统族",
    "完整药事治理",
    "完整抗菌药物分级管理",
    "真实外部药房审方成功联通",
    "自动开嘱",
    "完整 S18",
    "完整S18",
    "完整 S31",
    "完整S31",
    "完整第三方系统族覆盖",
    "完整上线验收",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

type InfectionPublicHealthRuntimeAsset = {
  assetType: "TERMINOLOGY" | "RULE" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

function hasCompleteInfectionPublicHealthApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "publicHealthAdapterCreatedThroughRealService",
    "publicHealthWebhookCreatedThroughRealService",
    "webhookSignaturePreviewGenerated",
    "infectionTerminologyActivated",
    "publicHealthActionCardPublished",
    "publicHealthRuleCreated",
    "runtimeActivatedWithPublicHealthAssets",
    "contextSnapshotCreatedFromFrontdesk",
    "prefillOutboundRequested",
    "inboundPublicHealthReportAccepted",
    "clinicalEvaluationTriggeredFromFrontdesk",
    "humanReportReviewRecorded",
    "safetyRectificationSubmittedAndReviewed",
  ].every((field) => evidence?.[field] === true);
}

function hasCompletePublicHealthInfectionRegulatoryConsumerSlice(body: Record<string, unknown>) {
  const runtime = parseInfectionPublicHealthRuntimeEvidence(body.runtime);
  if (!runtime) return false;
  const slice = recordValue(body.publicHealthInfectionRegulatoryConsumerSlice);
  const context = recordValue(body.clinicalContext);
  const outbound = recordValue(body.outboundPrefill);
  const inbound = recordValue(body.inboundReport);
  const recommendation = recordValue(body.recommendation);
  const manualReview = recordValue(body.manualReview);
  const rectification = recordValue(body.qualityRectification);
  const adapter = recordValue(body.adapter);
  const webhook = recordValue(body.webhookSignature);
  const clinicalEvent = recordValue(inbound?.clinicalEvent);
  return (
    slice !== null &&
    context !== null &&
    outbound !== null &&
    inbound !== null &&
    recommendation !== null &&
    manualReview !== null &&
    rectification !== null &&
    adapter !== null &&
    webhook !== null &&
    slice.systemFamilyCode === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
    hasText(slice.familyName) &&
    (String(slice.familyName).includes("公卫") || String(slice.familyName).includes("院感")) &&
    slice.consumer === "INFECTION_REPORT_PREFILL_SAFETY_RECTIFICATION" &&
    arrayEquals(slice.canonicalResources, [
      "Patient",
      "Encounter",
      "Condition",
      "Observation",
      "Document",
    ]) &&
    arrayEquals(slice.sourceSystems, [
      "MEDKERNEL_FRONTDESK",
      "PUBLIC_HEALTH_INFECTION_REGULATORY",
    ]) &&
    (slice.adapterVerified === true || slice.adapterCreatedThroughRealService === true) &&
    (slice.webhookSignatureVerified === true ||
      slice.webhookCreatedThroughRealService === true ||
      slice.signaturePreviewGenerated === true) &&
    (slice.outboundDegradationVerified === true || slice.outboundNotConnectedVerified === true) &&
    (slice.inboundReportVerified === true ||
      slice.signedInboundProcessedVerified === true ||
      slice.clinicalEventProcessedVerified === true) &&
    (slice.runtimeConsumerVerified === true || slice.recommendationConsumerVerified === true) &&
    (slice.humanReportReviewVerified === true || slice.manualReportReviewVerified === true) &&
    slice.rectificationClosedVerified === true &&
    slice.auditVerified === true &&
    slice.permissionVerified === true &&
    slice.sixStateBoundaryVerified === true &&
    slice.requiresPhysicianConfirmation === true &&
    slice.noLegalAutoSubmit === true &&
    slice.noExternalSuccessClaim === true &&
    slice.aiGenerated !== true &&
    slice.patientId === context.patientId &&
    slice.encounterId === context.encounterId &&
    slice.contextSnapshotId === context.contextSnapshotId &&
    slice.runtimeReleaseId === runtime.releaseId &&
    slice.recommendationCardId === recommendation.cardId &&
    (slice.feedbackId === manualReview.feedbackId ||
      slice.manualReviewFeedbackId === manualReview.feedbackId) &&
    slice.findingId === rectification.findingId &&
    slice.taskId === rectification.taskId &&
    (!hasText(slice.adapterId) || slice.adapterId === adapter.adapterId) &&
    (!hasText(slice.webhookId) || slice.webhookId === webhook.webhookId) &&
    (!hasText(slice.outboundMessageId) || slice.outboundMessageId === outbound.messageId) &&
    (!hasText(slice.inboundMessageId) || slice.inboundMessageId === inbound.messageId) &&
    (!hasText(slice.clinicalEventId) || slice.clinicalEventId === clinicalEvent?.eventId) &&
    slice.outboundPath === "outboundPrefill" &&
    slice.inboundPath === "inboundReport" &&
    slice.manualReviewPath === "manualReview" &&
    slice.rectificationPath === "qualityRectification" &&
    hasPublicHealthInfectionRegulatoryConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasCompleteInfectionPublicHealthApiEvidence(body.apiEvidence) &&
    hasCompleteInfectionPublicHealthAdapterEvidence(body.adapter) &&
    hasCompleteInfectionPublicHealthWebhookEvidence(body.webhookSignature, body.adapter) &&
    infectionPublicHealthRuntimeAssetMatches(body.terminologyGate, runtime.terminologyAsset) &&
    infectionPublicHealthRuntimeAssetMatches(body.ruleAsset, runtime.ruleAsset) &&
    infectionPublicHealthRuntimeAssetMatches(body.actionCard, runtime.actionCardAsset) &&
    hasCompleteInfectionPublicHealthTerminologyGate(body.terminologyGate) &&
    hasCompleteInfectionPublicHealthActionCard(body.actionCard) &&
    hasCompleteInfectionPublicHealthRuleAsset(body.ruleAsset) &&
    hasCompleteInfectionPublicHealthActivationRequest(body.activationRequest, runtime) &&
    hasCompleteInfectionPublicHealthClinicalContext(body.clinicalContext, runtime.releaseId) &&
    hasCompleteInfectionPublicHealthOutbound(
      body.outboundPrefill,
      body.adapter,
      body.clinicalContext,
    ) &&
    hasCompleteInfectionPublicHealthInbound(
      body.inboundReport,
      body.adapter,
      body.webhookSignature,
      body.outboundPrefill,
      runtime.releaseId,
    ) &&
    hasCompleteInfectionPublicHealthTrigger(body.clinicalTrigger, runtime.releaseId) &&
    hasCompleteInfectionPublicHealthRecommendation(
      body.recommendation,
      runtime,
      body.clinicalTrigger,
      body.ruleAsset,
    ) &&
    hasCompleteInfectionPublicHealthManualReview(body.manualReview, runtime.actionCardAsset) &&
    hasCompleteInfectionPublicHealthRectification(body.qualityRectification, body.recommendation) &&
    evidencePathsResolve(body, [
      slice.outboundPath,
      slice.inboundPath,
      slice.manualReviewPath,
      slice.rectificationPath,
    ])
  );
}

function parseInfectionPublicHealthRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const terminologyAsset = parseInfectionPublicHealthRuntimeAsset(
    runtime.terminologyAsset,
    "TERMINOLOGY",
  );
  const ruleAsset = parseInfectionPublicHealthRuntimeAsset(runtime.ruleAsset, "RULE");
  const actionCardAsset = parseInfectionPublicHealthRuntimeAsset(
    runtime.actionCardAsset,
    "ACTION_CARD",
  );
  const runtimeAssets = runtime.assets;
  const assets = [terminologyAsset, ruleAsset, actionCardAsset];
  if (
    assets.some((asset) => asset === null) ||
    !assets.every((asset) =>
      runtimeAssets.some((item) =>
        infectionPublicHealthRuntimeAssetMatches(item, asset as InfectionPublicHealthRuntimeAsset, {
          requireActive: true,
        }),
      ),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    terminologyAsset: terminologyAsset as InfectionPublicHealthRuntimeAsset,
    ruleAsset: ruleAsset as InfectionPublicHealthRuntimeAsset,
    actionCardAsset: actionCardAsset as InfectionPublicHealthRuntimeAsset,
  };
}

function parseInfectionPublicHealthRuntimeAsset(
  value: unknown,
  assetType: InfectionPublicHealthRuntimeAsset["assetType"],
): InfectionPublicHealthRuntimeAsset | null {
  const asset = recordValue(value);
  if (
    !asset ||
    asset.assetType !== assetType ||
    !hasText(asset.assetIdentity) ||
    !hasText(asset.versionId) ||
    !hasText(asset.versionNo) ||
    !isSha256(asset.contentHash) ||
    asset.entryState !== "ACTIVE"
  ) {
    return null;
  }
  return {
    assetType,
    assetIdentity: String(asset.assetIdentity),
    versionId: String(asset.versionId),
    versionNo: String(asset.versionNo),
    contentHash: String(asset.contentHash),
  };
}

function infectionPublicHealthRuntimeAssetMatches(
  value: unknown,
  asset: InfectionPublicHealthRuntimeAsset,
  options: { requireActive?: boolean } = {},
) {
  const candidate = recordValue(value);
  return (
    candidate?.assetType === asset.assetType &&
    candidate.assetIdentity === asset.assetIdentity &&
    candidate.versionId === asset.versionId &&
    candidate.versionNo === asset.versionNo &&
    candidate.contentHash === asset.contentHash &&
    (!options.requireActive || candidate.entryState === "ACTIVE")
  );
}

function hasCompleteInfectionPublicHealthAdapterEvidence(value: unknown) {
  const adapter = recordValue(value);
  const mappings = Array.isArray(adapter?.fieldMappings) ? adapter.fieldMappings : [];
  return (
    hasText(adapter?.adapterId) &&
    adapter?.systemFamilyCode === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
    adapter.sourceSystem === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
    adapter.targetSystem === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
    adapter.protocolType === "Webhook" &&
    infectionPublicHealthMappingTargets(mappings, "/conditions/0", "ICD-10") &&
    infectionPublicHealthMappingTargets(mappings, "/observations/0/code") &&
    infectionPublicHealthMappingTargets(mappings, "/observations/0/valueString") &&
    infectionPublicHealthMappingTargets(mappings, "/documents/0/documentType") &&
    infectionPublicHealthMappingTargets(mappings, "/documents/0/contentDigest") &&
    infectionPublicHealthMappingTargets(mappings, "/publicHealthReport/reportType") &&
    infectionPublicHealthMappingTargets(mappings, "/publicHealthReport/manualSubmitRequired") &&
    infectionPublicHealthMappingTargets(mappings, "/publicHealthReport/legalSubmissionDelegated") &&
    infectionPublicHealthMappingTargets(mappings, "/safetyEvent/eventType") &&
    infectionPublicHealthMappingTargets(mappings, "/safetyEvent/riskLevel") &&
    infectionPublicHealthMappingTargets(mappings, "/safetyEvent/rectificationRequired")
  );
}

function infectionPublicHealthMappingTargets(
  mappings: unknown[],
  targetPath: string,
  targetDictionaryKey?: string,
) {
  return mappings.some((item) => {
    const mapping = recordValue(item);
    return (
      mapping?.targetPath === targetPath &&
      (!targetDictionaryKey || mapping.targetDictionaryKey === targetDictionaryKey)
    );
  });
}

function hasCompleteInfectionPublicHealthWebhookEvidence(
  webhookValue: unknown,
  adapterValue: unknown,
) {
  const webhook = recordValue(webhookValue);
  const adapter = recordValue(adapterValue);
  return (
    webhook !== null &&
    hasText(webhook.webhookId) &&
    webhook.adapterId === adapter?.adapterId &&
    webhook.signatureAlgorithm === "HMAC-SHA256" &&
    webhook.canonicalPayloadIncludesTraceId === true &&
    webhook.previewGenerated === true
  );
}

function hasCompleteInfectionPublicHealthTerminologyGate(value: unknown) {
  const terminology = recordValue(value);
  return (
    terminology?.assetType === "TERMINOLOGY" &&
    hasText(terminology.assetIdentity) &&
    hasText(terminology.versionId) &&
    hasText(terminology.versionNo) &&
    isSha256(terminology.contentHash) &&
    terminology.standardSystem === "ICD-10" &&
    terminology.standardCode === "U07.100" &&
    terminology.localCode === "PH-COVID-19" &&
    terminology.sourceSystem === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
    terminology.category === "DIAGNOSIS" &&
    typeof terminology.mappingId === "number" &&
    terminology.mappingId > 0
  );
}

function hasCompleteInfectionPublicHealthActionCard(value: unknown) {
  const actionCard = recordValue(value);
  return (
    actionCard?.assetType === "ACTION_CARD" &&
    hasText(actionCard.assetIdentity) &&
    String(actionCard.assetIdentity).startsWith("ACTION_CARD.PUBLIC_HEALTH.INFECTION.") &&
    actionCard.requiresHumanReportReview === true &&
    actionCard.noLegalAutoSubmit === true
  );
}

function hasCompleteInfectionPublicHealthRuleAsset(value: unknown) {
  const rule = recordValue(value);
  return (
    rule?.assetType === "RULE" &&
    hasText(rule.assetIdentity) &&
    String(rule.assetIdentity).startsWith("RULE.PUBLIC_HEALTH.INFECTION.") &&
    hasText(rule.ruleId) &&
    hasText(rule.ruleVersionId)
  );
}

function hasCompleteInfectionPublicHealthActivationRequest(
  value: unknown,
  runtime: {
    terminologyAsset: InfectionPublicHealthRuntimeAsset;
    ruleAsset: InfectionPublicHealthRuntimeAsset;
    actionCardAsset: InfectionPublicHealthRuntimeAsset;
  },
) {
  return [runtime.terminologyAsset, runtime.ruleAsset, runtime.actionCardAsset].every((asset) =>
    runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
      assetType: asset.assetType,
      assetIdentity: asset.assetIdentity,
      versionId: asset.versionId,
    }),
  );
}

function hasCompleteInfectionPublicHealthClinicalContext(value: unknown, runtimeReleaseId: string) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const conditions = Array.isArray(resources?.conditions) ? resources.conditions : [];
  const observations = Array.isArray(resources?.observations) ? resources.observations : [];
  const documents = Array.isArray(resources?.documents) ? resources.documents : [];
  const extensions = recordValue(resources?.extensions);
  const local = recordValue(extensions?.local);
  const publicHealthReport = recordValue(local?.publicHealthReport);
  const safetyEvent = recordValue(local?.safetyEvent);
  return (
    hasText(context?.patientId) &&
    hasText(context?.encounterId) &&
    hasText(context?.contextSnapshotId) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    conditions.some((item) => {
      const condition = recordValue(item);
      return condition?.code === "U07.100" && condition.codeSystem === "ICD-10";
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return observation?.code === "NAT_RESULT" && observation.valueString === "POSITIVE";
    }) &&
    documents.some((item) => {
      const document = recordValue(item);
      return (
        document?.documentType === "PUBLIC_HEALTH_REPORT_PREFILL" && hasText(document.contentDigest)
      );
    }) &&
    publicHealthReport?.manualSubmitRequired === true &&
    publicHealthReport.legalSubmissionDelegated === false &&
    publicHealthReport.prefillStatus === "READY_FOR_HUMAN_REVIEW" &&
    safetyEvent?.eventType === "OCCUPATIONAL_EXPOSURE" &&
    hasText(safetyEvent.riskLevel) &&
    hasText(safetyEvent.rootCause) &&
    safetyEvent.rectificationRequired === true &&
    safetyEvent.reviewRequired === true
  );
}

function hasHighRiskInfectionPublicHealthClinicalContext(value: unknown, runtimeReleaseId: string) {
  if (!hasCompleteInfectionPublicHealthClinicalContext(value, runtimeReleaseId)) return false;
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const extensions = recordValue(resources?.extensions);
  const local = recordValue(extensions?.local);
  const publicHealthReport = recordValue(local?.publicHealthReport);
  const safetyEvent = recordValue(local?.safetyEvent);
  return (
    publicHealthReport?.reportableCondition === "SUSPECTED_COVID_19" &&
    safetyEvent?.riskLevel === "HIGH" &&
    safetyEvent.rootCause === "ISOLATION_PROTOCOL_GAP"
  );
}

function hasCompleteInfectionPublicHealthOutbound(
  value: unknown,
  adapterValue: unknown,
  contextValue: unknown,
) {
  const outbound = recordValue(value);
  const adapter = recordValue(adapterValue);
  const context = recordValue(contextValue);
  const payload = recordValue(outbound?.payload);
  return (
    outbound !== null &&
    payload !== null &&
    hasText(outbound.messageId) &&
    hasText(outbound.traceId) &&
    outbound.adapterId === adapter?.adapterId &&
    outbound.targetSystem === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
    outbound.protocolType === "Webhook" &&
    ["NOT_CONNECTED", "RETRYING"].includes(String(outbound.status)) &&
    outbound.compensationStatus === "NOT_CONNECTED" &&
    hasText(outbound.compensationMessageId) &&
    outbound.blocksMainFlow === false &&
    outbound.compensationRequired === true &&
    payload.patientId === context?.patientId &&
    payload.contextSnapshotId === context?.contextSnapshotId &&
    payload.reportType === "INFECTIOUS_DISEASE_PREFILL" &&
    payload.manualSubmitRequired === true &&
    payload.legalSubmissionDelegated === false
  );
}

function hasCompleteInfectionPublicHealthInbound(
  value: unknown,
  adapterValue: unknown,
  webhookValue: unknown,
  outboundValue: unknown,
  runtimeReleaseId: string,
) {
  const inbound = recordValue(value);
  const adapter = recordValue(adapterValue);
  const webhook = recordValue(webhookValue);
  const outbound = recordValue(outboundValue);
  const outboundPayload = recordValue(outbound?.payload);
  const mappedPayload = recordValue(inbound?.mappedPayload);
  const signedPayload = recordValue(inbound?.signedPayload);
  const publicHealthReport = recordValue(mappedPayload?.publicHealthReport);
  const safetyEvent = recordValue(mappedPayload?.safetyEvent);
  const clinicalEvent = recordValue(inbound?.clinicalEvent);
  const conditions = Array.isArray(mappedPayload?.conditions) ? mappedPayload.conditions : [];
  const observations = Array.isArray(mappedPayload?.observations) ? mappedPayload.observations : [];
  const documents = Array.isArray(mappedPayload?.documents) ? mappedPayload.documents : [];
  return (
    inbound !== null &&
    mappedPayload !== null &&
    hasText(inbound.messageId) &&
    inbound.traceId === outbound?.traceId &&
    inbound.adapterId === adapter?.adapterId &&
    inbound.webhookId === webhook?.webhookId &&
    inbound.patientId === outboundPayload?.patientId &&
    inbound.contextSnapshotId === outboundPayload?.contextSnapshotId &&
    inbound.sourceSystem === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
    inbound.status === "SUCCESS" &&
    inbound.clinicalEventStatus === "RECEIVED" &&
    hasProcessedInfectionPublicHealthClinicalEvent(clinicalEvent, runtimeReleaseId) &&
    typeof inbound.mappedFieldCount === "number" &&
    inbound.mappedFieldCount >= 12 &&
    signedPayload?.infectionCode === "PH-COVID-19" &&
    signedPayload.labCode === "NAT_RESULT" &&
    signedPayload.labResult === "POSITIVE" &&
    publicHealthReport?.manualSubmitRequired === true &&
    publicHealthReport.legalSubmissionDelegated === false &&
    publicHealthReport.prefillStatus === "READY_FOR_HUMAN_REVIEW" &&
    safetyEvent?.eventType === "OCCUPATIONAL_EXPOSURE" &&
    safetyEvent.rectificationRequired === true &&
    safetyEvent.reviewRequired === true &&
    conditions.some((item) => {
      const condition = recordValue(item);
      return (
        condition?.standardCode === "U07.100" &&
        condition.codeSystem === "ICD-10" &&
        condition.sourceSystem === "PUBLIC_HEALTH_INFECTION_REGULATORY" &&
        condition.runtimeReleaseId === runtimeReleaseId
      );
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return observation?.code === "NAT_RESULT" && observation.valueString === "POSITIVE";
    }) &&
    documents.some((item) => {
      const document = recordValue(item);
      return (
        document?.documentType === "PUBLIC_HEALTH_REPORT_PREFILL" && hasText(document.contentDigest)
      );
    })
  );
}

function hasHighRiskInfectionPublicHealthInbound(
  value: unknown,
  adapterValue: unknown,
  webhookValue: unknown,
  outboundValue: unknown,
  runtimeReleaseId: string,
) {
  if (
    !hasCompleteInfectionPublicHealthInbound(
      value,
      adapterValue,
      webhookValue,
      outboundValue,
      runtimeReleaseId,
    )
  ) {
    return false;
  }
  const inbound = recordValue(value);
  const mappedPayload = recordValue(inbound?.mappedPayload);
  const signedPayload = recordValue(inbound?.signedPayload);
  const safetyEvent = recordValue(mappedPayload?.safetyEvent);
  return (
    safetyEvent?.riskLevel === "HIGH" &&
    safetyEvent.rootCause === "ISOLATION_PROTOCOL_GAP" &&
    signedPayload?.infectionCode === "PH-COVID-19" &&
    signedPayload.labResult === "POSITIVE"
  );
}

function hasProcessedInfectionPublicHealthClinicalEvent(
  clinicalEvent: Record<string, unknown> | null,
  runtimeReleaseId: string,
) {
  return (
    clinicalEvent !== null &&
    hasText(clinicalEvent.eventId) &&
    clinicalEvent.status === "PROCESSED" &&
    clinicalEvent.runtimeReleaseId === runtimeReleaseId &&
    (clinicalEvent.errorCode === null || clinicalEvent.errorCode === undefined) &&
    (clinicalEvent.errorClass === null || clinicalEvent.errorClass === undefined)
  );
}

function hasCompleteInfectionPublicHealthTrigger(value: unknown, runtimeReleaseId: string) {
  const trigger = recordValue(value);
  return (
    trigger !== null &&
    hasText(trigger.triggerId) &&
    hasText(trigger.contextSnapshotId) &&
    trigger.runtimeReleaseId === runtimeReleaseId &&
    hasText(trigger.cardId) &&
    Array.isArray(trigger.relatedCardIds) &&
    trigger.relatedCardIds.includes(trigger.cardId)
  );
}

function hasCompleteInfectionPublicHealthRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
    ruleAsset: InfectionPublicHealthRuntimeAsset;
    actionCardAsset: InfectionPublicHealthRuntimeAsset;
  },
  triggerValue: unknown,
  ruleValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const rule = recordValue(ruleValue);
  if (
    !recommendation ||
    !trigger ||
    !rule ||
    !hasText(recommendation.cardId) ||
    recommendation.cardId !== trigger.cardId ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING" ||
    recommendation.requiresPhysicianConfirmation !== true ||
    recommendation.aiGenerated !== false
  ) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const runtimeRelease = recordValue(explanation?.runtimeRelease);
  const ruleExplanation = recordValue(explanation?.ruleExplanation);
  const conditionEvidence = Array.isArray(ruleExplanation?.conditionEvidence)
    ? ruleExplanation.conditionEvidence
    : [];
  const runtimeAssetEvidence = Array.isArray(ruleExplanation?.runtimeAssetEvidence)
    ? ruleExplanation.runtimeAssetEvidence
    : [];
  return (
    explanation?.matchType === "RULE" &&
    explanation.ruleId === rule.ruleId &&
    explanation.ruleCode === rule.assetIdentity &&
    explanation.ruleVersionId === rule.ruleVersionId &&
    runtimeRelease?.runtimeReleaseId === runtime.releaseId &&
    runtimeRelease.assetVersionId === runtime.ruleAsset.versionId &&
    runtimeRelease.assetVersionNo === runtime.ruleAsset.versionNo &&
    runtimeRelease.contentHash === runtime.ruleAsset.contentHash &&
    infectionPublicHealthConditionMatched(conditionEvidence, "conditions[].code") &&
    infectionPublicHealthConditionMatched(conditionEvidence, "observations[].valueString") &&
    infectionPublicHealthConditionMatched(
      conditionEvidence,
      "extensions.local.publicHealthReport.manualSubmitRequired",
    ) &&
    runtimeAssetEvidence.some((item) => {
      const evidence = recordValue(item);
      return (
        evidence?.assetType === "ACTION_CARD" &&
        evidence.assetIdentity === runtime.actionCardAsset.assetIdentity &&
        evidence.assetVersion === runtime.actionCardAsset.versionNo &&
        evidence.contentHash === runtime.actionCardAsset.contentHash &&
        (evidence.requiresHumanReportReview === true ||
          evidence.requiresPhysicianConfirmation === true)
      );
    })
  );
}

function infectionPublicHealthConditionMatched(values: unknown[], fact: string) {
  return values.some((item) => {
    const evidence = recordValue(item);
    return evidence?.fact === fact && evidence.matched === true;
  });
}

function hasCompleteInfectionPublicHealthManualReview(
  value: unknown,
  actionCardAsset: InfectionPublicHealthRuntimeAsset,
) {
  const review = recordValue(value);
  const persisted = recordValue(review?.persisted);
  const actionCardEvidence = recordValue(review?.actionCardEvidence);
  return (
    review !== null &&
    persisted !== null &&
    hasText(review.feedbackId) &&
    review.cardStatus === "ACCEPTED" &&
    review.canonicalSessionRole === "clinical-user" &&
    review.roleEvidence === "BUSINESS_FEEDBACK_ROLE_ONLY" &&
    persisted.feedbackId === review.feedbackId &&
    persisted.feedbackType === "ACCEPT" &&
    hasText(persisted.operatorRole) &&
    review.noLegalAutoSubmit === true &&
    actionCardEvidence?.assetType === "ACTION_CARD" &&
    actionCardEvidence.assetIdentity === actionCardAsset.assetIdentity &&
    actionCardEvidence.versionId === actionCardAsset.versionId &&
    actionCardEvidence.versionNo === actionCardAsset.versionNo &&
    actionCardEvidence.contentHash === actionCardAsset.contentHash &&
    actionCardEvidence.entryState === "ACTIVE" &&
    actionCardEvidence.requiresHumanReportReview === true &&
    actionCardEvidence.noLegalAutoSubmit === true
  );
}

function hasCompleteInfectionPublicHealthRectification(
  value: unknown,
  recommendationValue: unknown,
) {
  const rectification = recordValue(value);
  const recommendation = recordValue(recommendationValue);
  return (
    rectification !== null &&
    recommendation !== null &&
    hasText(rectification.findingId) &&
    rectification.sourceType === "SAFETY_EVENT" &&
    rectification.sourceId === recommendation.cardId &&
    ["P0", "P1"].includes(String(rectification.severity)) &&
    rectification.findingStatus === "CLOSED" &&
    hasText(rectification.taskId) &&
    rectification.taskStatus === "CLOSED" &&
    rectification.submittedByRole === "engine-operator" &&
    rectification.reviewedByRole === "engine-operator" &&
    rectification.roleEvidence === "CANONICAL_FIXED_ROLE_EVALUATION_REMEDIATE_REVIEW" &&
    hasText(rectification.submittedEvidenceRef) &&
    rectification.reviewDecision === "APPROVED"
  );
}

function hasInfectionPublicHealthSafetyScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !/完整(?:院感系统|公卫法定上报|不良事件系统|第三方公卫院感监管系统族完整覆盖)(?:已上线|完整上线|完成上线|已完成|完整覆盖|全面覆盖)/u.test(
      statement,
    ) &&
    hasNegatedScopeTerm(statement, "完整院感系统") &&
    hasNegatedScopeTerm(statement, "完整公卫法定上报") &&
    hasNegatedScopeTerm(statement, "完整不良事件系统") &&
    hasNegatedScopeTerm(statement, "第三方公卫院感监管系统族完整覆盖")
  );
}

function hasPublicHealthInfectionRegulatoryConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedPublicHealthInfectionRegulatoryConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整院感系统") &&
    hasNegatedScopeTerm(statement, "完整公卫法定上报") &&
    hasNegatedScopeTerm(statement, "完整不良事件系统") &&
    hasNegatedScopeTerm(statement, "完整公卫院感监管系统族覆盖") &&
    hasNegatedScopeTerm(statement, "真实外部公卫上报成功联通") &&
    hasNegatedScopeTerm(statement, "自动法定上报") &&
    hasNegatedScopeTerm(statement, "完整 S21") &&
    hasNegatedScopeTerm(statement, "完整 S32") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedPublicHealthInfectionRegulatoryConsumerSliceScopeClaim(statement: string) {
  return [
    "完整院感系统",
    "完整公卫法定上报",
    "完整不良事件系统",
    "完整公卫院感监管系统族覆盖",
    "完整公卫院感监管系统族",
    "真实外部公卫上报成功联通",
    "自动法定上报",
    "完整 S21",
    "完整S21",
    "完整 S32",
    "完整S32",
    "完整第三方系统族覆盖",
    "完整上线验收",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

type SurgeryAnesthesiaTransfusionRuntimeAsset = {
  assetType: "TERMINOLOGY" | "SAFETY" | "CDSS_RISK" | "RULE" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

function hasCompleteSurgeryAnesthesiaTransfusionApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "surgeryAdapterCreatedThroughRealService",
    "surgeryWebhookCreatedThroughRealService",
    "webhookSignaturePreviewGenerated",
    "surgeryTerminologyActivated",
    "surgerySafetyAssetPromoted",
    "surgeryRiskMatrixCreated",
    "surgeryActionCardPublished",
    "surgeryRuleCreated",
    "runtimeActivatedWithSurgeryAssets",
    "contextSnapshotCreatedFromFrontdesk",
    "outboundChecklistRequested",
    "inboundSurgeryEventAccepted",
    "clinicalEvaluationTriggeredFromFrontdesk",
    "humanRiskConfirmationRecorded",
    "qualityRectificationSubmittedAndReviewed",
  ].every((field) => evidence?.[field] === true);
}

function parseSurgeryAnesthesiaTransfusionRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const terminologyAsset = parseSurgeryAnesthesiaTransfusionRuntimeAsset(
    runtime.terminologyAsset,
    "TERMINOLOGY",
  );
  const safetyAsset = parseSurgeryAnesthesiaTransfusionRuntimeAsset(runtime.safetyAsset, "SAFETY");
  const cdssRiskAsset = parseSurgeryAnesthesiaTransfusionRuntimeAsset(
    runtime.cdssRiskAsset,
    "CDSS_RISK",
  );
  const ruleAsset = parseSurgeryAnesthesiaTransfusionRuntimeAsset(runtime.ruleAsset, "RULE");
  const actionCardAsset = parseSurgeryAnesthesiaTransfusionRuntimeAsset(
    runtime.actionCardAsset,
    "ACTION_CARD",
  );
  const runtimeAssets = runtime.assets;
  const assets = [terminologyAsset, safetyAsset, cdssRiskAsset, ruleAsset, actionCardAsset];
  if (
    assets.some((asset) => asset === null) ||
    !assets.every((asset) =>
      runtimeAssets.some((item) =>
        surgeryAnesthesiaTransfusionRuntimeAssetMatches(
          item,
          asset as SurgeryAnesthesiaTransfusionRuntimeAsset,
          { requireActive: true },
        ),
      ),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    terminologyAsset: terminologyAsset as SurgeryAnesthesiaTransfusionRuntimeAsset,
    safetyAsset: safetyAsset as SurgeryAnesthesiaTransfusionRuntimeAsset,
    cdssRiskAsset: cdssRiskAsset as SurgeryAnesthesiaTransfusionRuntimeAsset,
    ruleAsset: ruleAsset as SurgeryAnesthesiaTransfusionRuntimeAsset,
    actionCardAsset: actionCardAsset as SurgeryAnesthesiaTransfusionRuntimeAsset,
  };
}

function parseSurgeryAnesthesiaTransfusionRuntimeAsset(
  value: unknown,
  assetType: SurgeryAnesthesiaTransfusionRuntimeAsset["assetType"],
): SurgeryAnesthesiaTransfusionRuntimeAsset | null {
  const asset = recordValue(value);
  if (
    !asset ||
    asset.assetType !== assetType ||
    !hasText(asset.assetIdentity) ||
    !hasText(asset.versionId) ||
    !hasText(asset.versionNo) ||
    !isSha256(asset.contentHash) ||
    asset.entryState !== "ACTIVE"
  ) {
    return null;
  }
  return {
    assetType,
    assetIdentity: String(asset.assetIdentity),
    versionId: String(asset.versionId),
    versionNo: String(asset.versionNo),
    contentHash: String(asset.contentHash),
  };
}

function surgeryAnesthesiaTransfusionRuntimeAssetMatches(
  value: unknown,
  asset: SurgeryAnesthesiaTransfusionRuntimeAsset,
  options: { requireActive?: boolean } = {},
) {
  const candidate = recordValue(value);
  return (
    candidate?.assetType === asset.assetType &&
    candidate.assetIdentity === asset.assetIdentity &&
    candidate.versionId === asset.versionId &&
    candidate.versionNo === asset.versionNo &&
    candidate.contentHash === asset.contentHash &&
    (!options.requireActive || candidate.entryState === "ACTIVE")
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionAdapterEvidence(value: unknown) {
  const adapter = recordValue(value);
  const mappings = Array.isArray(adapter?.fieldMappings) ? adapter.fieldMappings : [];
  return (
    hasText(adapter?.adapterId) &&
    adapter?.systemFamilyCode === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
    adapter.sourceSystem === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
    adapter.targetSystem === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
    adapter.protocolType === "Webhook" &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/procedures/0", "ICD-9-CM-3") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/observations/0/code") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/observations/0/valueString") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/medications/0/standardCode") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/documents/0/documentType") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/documents/0/contentDigest") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/surgeryPlan/surgeryLevel") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/anesthesiaAssessment/airwayRisk") &&
    surgeryAnesthesiaTransfusionMappingTargets(mappings, "/transfusionRequest/noAutoTransfusion")
  );
}

function surgeryAnesthesiaTransfusionMappingTargets(
  mappings: unknown[],
  targetPath: string,
  targetDictionaryKey?: string,
) {
  return mappings.some((item) => {
    const mapping = recordValue(item);
    return (
      mapping?.targetPath === targetPath &&
      (!targetDictionaryKey || mapping.targetDictionaryKey === targetDictionaryKey)
    );
  });
}

function hasCompleteSurgeryAnesthesiaTransfusionWebhookEvidence(
  webhookValue: unknown,
  adapterValue: unknown,
) {
  const webhook = recordValue(webhookValue);
  const adapter = recordValue(adapterValue);
  return (
    webhook !== null &&
    hasText(webhook.webhookId) &&
    webhook.adapterId === adapter?.adapterId &&
    webhook.signatureAlgorithm === "HMAC-SHA256" &&
    webhook.canonicalPayloadIncludesTraceId === true &&
    webhook.previewGenerated === true
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionTerminologyGate(value: unknown) {
  const terminology = recordValue(value);
  return (
    terminology?.assetType === "TERMINOLOGY" &&
    hasText(terminology.assetIdentity) &&
    hasText(terminology.versionId) &&
    hasText(terminology.versionNo) &&
    isSha256(terminology.contentHash) &&
    terminology.standardSystem === "ICD-9-CM-3" &&
    terminology.standardCode === "47.0901" &&
    terminology.localCode === "OR-LAP-APP" &&
    terminology.sourceSystem === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
    terminology.category === "PROCEDURE" &&
    typeof terminology.mappingId === "number" &&
    terminology.mappingId > 0 &&
    hasCompleteSurgeryAnesthesiaTransfusionConfirmedMapping(
      terminology.confirmedMapping,
      terminology,
    )
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionConfirmedMapping(
  value: unknown,
  terminology: Record<string, unknown>,
) {
  const mapping = recordValue(value);
  return (
    mapping !== null &&
    mapping.mappingId === terminology.mappingId &&
    mapping.localTermId === terminology.localTermId &&
    mapping.standardTermId === terminology.standardTermId &&
    mapping.sourceSystem === terminology.sourceSystem &&
    mapping.category === terminology.category
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionSafetyRedline(value: unknown) {
  const redline = recordValue(value);
  return (
    redline?.assetType === "SAFETY" &&
    hasText(redline.assetIdentity) &&
    String(redline.assetIdentity).startsWith("SAFETY.RDL-SURGERY") &&
    redline.category === "SURGERY_ANESTHESIA_TRANSFUSION" &&
    redline.hazardSeverity === "CRITICAL" &&
    redline.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    redline.noAutoTransfusion === true &&
    redline.noAutoSurgery === true
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionRiskMatrix(value: unknown) {
  const risk = recordValue(value);
  return (
    risk?.assetType === "CDSS_RISK" &&
    risk.assetIdentity === "CDSS.RISK.MATRIX" &&
    risk.triggerPoint === "order-sign" &&
    risk.riskLevel === "CRITICAL" &&
    risk.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    risk.automationLevel === "INFORM_ONLY" &&
    risk.autoExecutionAllowed === false
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionActionCard(value: unknown) {
  const actionCard = recordValue(value);
  return (
    actionCard?.assetType === "ACTION_CARD" &&
    hasText(actionCard.assetIdentity) &&
    String(actionCard.assetIdentity).startsWith("ACTION_CARD.SURGERY.") &&
    actionCard.requiresPhysicianConfirmation === true &&
    actionCard.noAutoOrder === true &&
    actionCard.noAutoTransfusion === true &&
    actionCard.noAutoSurgery === true
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionRuleAsset(value: unknown) {
  const rule = recordValue(value);
  return (
    rule?.assetType === "RULE" &&
    hasText(rule.assetIdentity) &&
    String(rule.assetIdentity).startsWith("RULE.SURGERY.") &&
    hasText(rule.ruleId) &&
    hasText(rule.ruleVersionId)
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionActivationRequest(
  value: unknown,
  runtime: {
    terminologyAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
    safetyAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
    cdssRiskAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
    ruleAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
    actionCardAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
  },
) {
  return [
    runtime.terminologyAsset,
    runtime.safetyAsset,
    runtime.cdssRiskAsset,
    runtime.ruleAsset,
    runtime.actionCardAsset,
  ].every((asset) =>
    runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
      assetType: asset.assetType,
      assetIdentity: asset.assetIdentity,
      versionId: asset.versionId,
    }),
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionClinicalContext(
  value: unknown,
  runtimeReleaseId: string,
) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const procedures = Array.isArray(resources?.procedures) ? resources.procedures : [];
  const observations = Array.isArray(resources?.observations) ? resources.observations : [];
  const medications = Array.isArray(resources?.medications) ? resources.medications : [];
  const documents = Array.isArray(resources?.documents) ? resources.documents : [];
  const extensions = recordValue(resources?.extensions);
  const local = recordValue(extensions?.local);
  const surgeryPlan = recordValue(local?.surgeryPlan);
  const anesthesiaAssessment = recordValue(local?.anesthesiaAssessment);
  const transfusionRequest = recordValue(local?.transfusionRequest);
  return (
    hasText(context?.patientId) &&
    hasText(context?.encounterId) &&
    hasText(context?.contextSnapshotId) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    procedures.some((item) => {
      const procedure = recordValue(item);
      return (
        procedure?.code === "47.0901" &&
        hasText(procedure.displayName) &&
        procedure.anesthesiaType === "GENERAL"
      );
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return observation?.code === "ASA_CLASS" && observation.valueString === "III";
    }) &&
    medications.some((item) => {
      const medication = recordValue(item);
      return medication?.code === "N01AB06" || medication?.standardCode === "N01AB06";
    }) &&
    documents.some((item) => {
      const document = recordValue(item);
      return (
        document?.documentType === "SURGERY_SAFETY_CHECKLIST" && hasText(document.contentDigest)
      );
    }) &&
    surgeryPlan?.surgeryLevel === "LEVEL_3" &&
    surgeryPlan.timeOutRequired === true &&
    anesthesiaAssessment?.airwayRisk === "DIFFICULT_AIRWAY" &&
    anesthesiaAssessment.anesthesiologistReviewRequired === true &&
    transfusionRequest?.crossmatchStatus === "MATCHED" &&
    transfusionRequest.transfusionConsentConfirmed === true &&
    transfusionRequest.noAutoTransfusion === true
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionOutbound(
  value: unknown,
  adapterValue: unknown,
  contextValue: unknown,
) {
  const outbound = recordValue(value);
  const adapter = recordValue(adapterValue);
  const context = recordValue(contextValue);
  const payload = recordValue(outbound?.payload);
  return (
    outbound !== null &&
    payload !== null &&
    hasText(outbound.messageId) &&
    hasText(outbound.traceId) &&
    outbound.adapterId === adapter?.adapterId &&
    outbound.targetSystem === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
    outbound.protocolType === "Webhook" &&
    ["NOT_CONNECTED", "RETRYING"].includes(String(outbound.status)) &&
    outbound.compensationStatus === "NOT_CONNECTED" &&
    hasText(outbound.compensationMessageId) &&
    outbound.blocksMainFlow === false &&
    outbound.compensationRequired === true &&
    payload.patientId === context?.patientId &&
    payload.contextSnapshotId === context?.contextSnapshotId &&
    payload.noAutoTransfusion === true &&
    payload.noAutoSurgery === true
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionInbound(
  value: unknown,
  adapterValue: unknown,
  webhookValue: unknown,
  outboundValue: unknown,
  runtimeReleaseId: string,
) {
  const inbound = recordValue(value);
  const adapter = recordValue(adapterValue);
  const webhook = recordValue(webhookValue);
  const outbound = recordValue(outboundValue);
  const outboundPayload = recordValue(outbound?.payload);
  const mappedPayload = recordValue(inbound?.mappedPayload);
  const signedPayload = recordValue(inbound?.signedPayload);
  const clinicalEvent = recordValue(inbound?.clinicalEvent);
  const procedures = Array.isArray(mappedPayload?.procedures) ? mappedPayload.procedures : [];
  const observations = Array.isArray(mappedPayload?.observations) ? mappedPayload.observations : [];
  const medications = Array.isArray(mappedPayload?.medications) ? mappedPayload.medications : [];
  const documents = Array.isArray(mappedPayload?.documents) ? mappedPayload.documents : [];
  const surgeryPlan = recordValue(mappedPayload?.surgeryPlan);
  const anesthesiaAssessment = recordValue(mappedPayload?.anesthesiaAssessment);
  const transfusionRequest = recordValue(mappedPayload?.transfusionRequest);
  return (
    inbound !== null &&
    mappedPayload !== null &&
    hasText(inbound.messageId) &&
    inbound.traceId === outbound?.traceId &&
    inbound.adapterId === adapter?.adapterId &&
    inbound.webhookId === webhook?.webhookId &&
    inbound.patientId === outboundPayload?.patientId &&
    inbound.contextSnapshotId === outboundPayload?.contextSnapshotId &&
    inbound.sourceSystem === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
    inbound.status === "SUCCESS" &&
    inbound.clinicalEventStatus === "RECEIVED" &&
    hasProcessedSurgeryAnesthesiaTransfusionClinicalEvent(clinicalEvent, runtimeReleaseId) &&
    typeof inbound.mappedFieldCount === "number" &&
    inbound.mappedFieldCount >= 18 &&
    signedPayload?.procedureCode === "OR-LAP-APP" &&
    signedPayload.asaClass === "III" &&
    recordValue(signedPayload.transfusionRequest)?.noAutoTransfusion === true &&
    procedures.some((item) => {
      const procedure = recordValue(item);
      return (
        procedure?.standardCode === "47.0901" &&
        procedure.codeSystem === "ICD-9-CM-3" &&
        procedure.sourceSystem === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
        procedure.runtimeReleaseId === runtimeReleaseId
      );
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return observation?.code === "ASA_CLASS" && observation.valueString === "III";
    }) &&
    medications.some((item) => recordValue(item)?.standardCode === "N01AB06") &&
    documents.some((item) => {
      const document = recordValue(item);
      return (
        document?.documentType === "SURGERY_SAFETY_CHECKLIST" && hasText(document.contentDigest)
      );
    }) &&
    surgeryPlan?.timeOutRequired === true &&
    anesthesiaAssessment?.airwayRisk === "DIFFICULT_AIRWAY" &&
    anesthesiaAssessment.anesthesiologistReviewRequired === true &&
    transfusionRequest?.crossmatchStatus === "MATCHED" &&
    transfusionRequest.transfusionConsentConfirmed === true &&
    transfusionRequest.noAutoTransfusion === true
  );
}

function hasProcessedSurgeryAnesthesiaTransfusionClinicalEvent(
  clinicalEvent: Record<string, unknown> | null,
  runtimeReleaseId: string,
) {
  return (
    clinicalEvent !== null &&
    hasText(clinicalEvent.eventId) &&
    clinicalEvent.status === "PROCESSED" &&
    clinicalEvent.runtimeReleaseId === runtimeReleaseId &&
    (clinicalEvent.errorCode === null || clinicalEvent.errorCode === undefined) &&
    (clinicalEvent.errorClass === null || clinicalEvent.errorClass === undefined)
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionTrigger(value: unknown, runtimeReleaseId: string) {
  const trigger = recordValue(value);
  return (
    trigger !== null &&
    hasText(trigger.triggerId) &&
    hasText(trigger.contextSnapshotId) &&
    trigger.runtimeReleaseId === runtimeReleaseId &&
    trigger.triggerType === "order-sign" &&
    hasText(trigger.cardId) &&
    Array.isArray(trigger.relatedCardIds) &&
    trigger.relatedCardIds.includes(trigger.cardId)
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
    safetyAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
    ruleAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
    actionCardAsset: SurgeryAnesthesiaTransfusionRuntimeAsset;
  },
  triggerValue: unknown,
  ruleValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const rule = recordValue(ruleValue);
  if (
    !recommendation ||
    !trigger ||
    !rule ||
    !hasText(recommendation.cardId) ||
    recommendation.cardId !== trigger.cardId ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING" ||
    recommendation.requiresPhysicianConfirmation !== true ||
    recommendation.aiGenerated !== false
  ) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const runtimeRelease = recordValue(explanation?.runtimeRelease);
  const ruleExplanation = recordValue(explanation?.ruleExplanation);
  const conditionEvidence = Array.isArray(ruleExplanation?.conditionEvidence)
    ? ruleExplanation.conditionEvidence
    : [];
  const runtimeAssetEvidence = Array.isArray(ruleExplanation?.runtimeAssetEvidence)
    ? ruleExplanation.runtimeAssetEvidence
    : [];
  return (
    explanation?.matchType === "RULE" &&
    explanation.ruleId === rule.ruleId &&
    explanation.ruleCode === rule.assetIdentity &&
    explanation.ruleVersionId === rule.ruleVersionId &&
    runtimeRelease?.runtimeReleaseId === runtime.releaseId &&
    runtimeRelease.assetVersionId === runtime.ruleAsset.versionId &&
    runtimeRelease.assetVersionNo === runtime.ruleAsset.versionNo &&
    runtimeRelease.contentHash === runtime.ruleAsset.contentHash &&
    surgeryAnesthesiaTransfusionConditionMatched(conditionEvidence, "procedures[].code") &&
    surgeryAnesthesiaTransfusionConditionMatched(conditionEvidence, "observations[].valueString") &&
    surgeryAnesthesiaTransfusionConditionMatched(
      conditionEvidence,
      "extensions.local.transfusionRequest.noAutoTransfusion",
    ) &&
    runtimeAssetEvidence.some((item) => {
      const evidence = recordValue(item);
      return (
        evidence?.assetType === "ACTION_CARD" &&
        evidence.assetIdentity === runtime.actionCardAsset.assetIdentity &&
        evidence.assetVersion === runtime.actionCardAsset.versionNo &&
        evidence.contentHash === runtime.actionCardAsset.contentHash
      );
    })
  );
}

function surgeryAnesthesiaTransfusionConditionMatched(values: unknown[], fact: string) {
  return values.some((item) => {
    const evidence = recordValue(item);
    return evidence?.fact === fact && evidence.matched === true;
  });
}

function hasCompleteSurgeryAnesthesiaTransfusionManualConfirmation(
  value: unknown,
  actionCardAsset: SurgeryAnesthesiaTransfusionRuntimeAsset,
) {
  const confirmation = recordValue(value);
  const persisted = recordValue(confirmation?.persisted);
  const actionCardEvidence = recordValue(confirmation?.actionCardEvidence);
  return (
    confirmation !== null &&
    persisted !== null &&
    hasText(confirmation.feedbackId) &&
    confirmation.cardStatus === "ACCEPTED" &&
    confirmation.canonicalSessionRole === "clinical-user" &&
    confirmation.roleEvidence === "BUSINESS_FEEDBACK_ROLE_ONLY" &&
    persisted.feedbackId === confirmation.feedbackId &&
    persisted.feedbackType === "ACCEPT" &&
    persisted.operatorRole === "DOCTOR" &&
    persisted.reasonCode === "CONFIRMED" &&
    confirmation.noAutoOrder === true &&
    confirmation.noAutoTransfusion === true &&
    confirmation.noAutoSurgery === true &&
    actionCardEvidence?.assetType === "ACTION_CARD" &&
    actionCardEvidence.assetIdentity === actionCardAsset.assetIdentity &&
    actionCardEvidence.versionId === actionCardAsset.versionId &&
    actionCardEvidence.versionNo === actionCardAsset.versionNo &&
    actionCardEvidence.contentHash === actionCardAsset.contentHash &&
    actionCardEvidence.entryState === "ACTIVE" &&
    actionCardEvidence.noAutoOrder === true &&
    actionCardEvidence.noAutoTransfusion === true &&
    actionCardEvidence.noAutoSurgery === true
  );
}

function hasCompleteSurgeryAnesthesiaTransfusionRectification(
  value: unknown,
  recommendationValue: unknown,
) {
  const rectification = recordValue(value);
  const recommendation = recordValue(recommendationValue);
  return (
    rectification !== null &&
    recommendation !== null &&
    hasText(rectification.findingId) &&
    rectification.sourceType === "SURGERY_TIMELINE" &&
    rectification.sourceId === recommendation.cardId &&
    ["P0", "P1"].includes(String(rectification.severity)) &&
    rectification.findingStatus === "CLOSED" &&
    hasText(rectification.taskId) &&
    rectification.taskStatus === "CLOSED" &&
    rectification.submittedByRole === "engine-operator" &&
    rectification.reviewedByRole === "engine-operator" &&
    rectification.roleEvidence === "CANONICAL_FIXED_ROLE_EVALUATION_REMEDIATE_REVIEW" &&
    hasText(rectification.submittedEvidenceRef) &&
    rectification.reviewDecision === "APPROVED"
  );
}

function hasSurgeryAnesthesiaTransfusionScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedSurgeryAnesthesiaTransfusionScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整围手术期系统") &&
    hasNegatedScopeTerm(statement, "完整手麻系统") &&
    hasNegatedScopeTerm(statement, "完整手术室系统") &&
    hasNegatedScopeTerm(statement, "完整输血系统") &&
    hasNegatedScopeTerm(statement, "护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedSurgeryAnesthesiaTransfusionScopeClaim(statement: string) {
  return [
    "完整围手术期系统",
    "完整手麻系统",
    "完整手术室系统",
    "完整输血系统",
    "器械耗材系统族完整覆盖",
    "完整S0-S40",
    "完整 S0-S40",
    "完整上线",
    "完整上线验收",
    "完整S26",
    "完整 S26",
    "完整S33",
    "完整 S33",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整手麻手术室输血系统",
    "完整手麻手术室输血系统族",
    "护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCompleteNursingAnesthesiaTransfusionIcuConsumerSlice(body: Record<string, unknown>) {
  const runtime = parseSurgeryAnesthesiaTransfusionRuntimeEvidence(body.runtime);
  if (!runtime) return false;
  const slice = recordValue(body.nursingAnesthesiaTransfusionIcuConsumerSlice);
  const context = recordValue(body.clinicalContext);
  const outbound = recordValue(body.outboundChecklist);
  const inbound = recordValue(body.inboundSurgeryEvent);
  const recommendation = recordValue(body.recommendation);
  const manualConfirmation = recordValue(body.manualConfirmation);
  const rectification = recordValue(body.qualityRectification);
  const adapter = recordValue(body.adapter);
  const webhook = recordValue(body.webhookSignature);
  const clinicalEvent = recordValue(inbound?.clinicalEvent);
  return (
    slice !== null &&
    context !== null &&
    outbound !== null &&
    inbound !== null &&
    recommendation !== null &&
    manualConfirmation !== null &&
    rectification !== null &&
    adapter !== null &&
    webhook !== null &&
    slice.systemFamilyCode === "NURSING_ANESTHESIA_TRANSFUSION_ICU" &&
    hasText(slice.familyName) &&
    (String(slice.familyName).includes("护理") ||
      String(slice.familyName).includes("围手术期") ||
      String(slice.familyName).includes("麻醉")) &&
    slice.consumer === "SURGERY_ANESTHESIA_TRANSFUSION_CHECKLIST_RECOMMENDATION_RECTIFICATION" &&
    arrayEquals(slice.canonicalResources, [
      "Patient",
      "Encounter",
      "Procedure",
      "Observation",
      "Medication",
      "Document",
    ]) &&
    arrayEquals(slice.sourceSystems, [
      "MEDKERNEL_FRONTDESK",
      "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    ]) &&
    slice.adapterVerified === true &&
    slice.webhookSignatureVerified === true &&
    slice.outboundDegradationVerified === true &&
    (slice.signedInboundProcessedVerified === true ||
      slice.inboundSurgeryEventVerified === true ||
      slice.clinicalEventProcessedVerified === true) &&
    (slice.runtimeConsumerVerified === true || slice.recommendationConsumerVerified === true) &&
    (slice.manualConfirmationVerified === true || slice.physicianConfirmationVerified === true) &&
    slice.rectificationClosedVerified === true &&
    slice.auditVerified === true &&
    slice.permissionVerified === true &&
    slice.sixStateBoundaryVerified === true &&
    slice.requiresPhysicianConfirmation === true &&
    slice.noAutoOrder === true &&
    slice.noAutoTransfusion === true &&
    slice.noAutoSurgery === true &&
    slice.noExternalSuccessClaim === true &&
    slice.aiGenerated !== true &&
    slice.patientId === context.patientId &&
    slice.encounterId === context.encounterId &&
    slice.contextSnapshotId === context.contextSnapshotId &&
    slice.runtimeReleaseId === runtime.releaseId &&
    slice.adapterId === adapter.adapterId &&
    slice.webhookId === webhook.webhookId &&
    slice.outboundMessageId === outbound.messageId &&
    slice.inboundMessageId === inbound.messageId &&
    slice.clinicalEventId === clinicalEvent?.eventId &&
    slice.recommendationCardId === recommendation.cardId &&
    slice.feedbackId === manualConfirmation.feedbackId &&
    slice.findingId === rectification.findingId &&
    slice.taskId === rectification.taskId &&
    slice.outboundPath === "outboundChecklist" &&
    slice.inboundPath === "inboundSurgeryEvent" &&
    slice.manualConfirmationPath === "manualConfirmation" &&
    slice.rectificationPath === "qualityRectification" &&
    hasNursingAnesthesiaTransfusionIcuConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasCompleteSurgeryAnesthesiaTransfusionApiEvidence(body.apiEvidence) &&
    hasCompleteSurgeryAnesthesiaTransfusionAdapterEvidence(body.adapter) &&
    hasCompleteSurgeryAnesthesiaTransfusionWebhookEvidence(body.webhookSignature, body.adapter) &&
    surgeryAnesthesiaTransfusionRuntimeAssetMatches(
      body.terminologyGate,
      runtime.terminologyAsset,
    ) &&
    surgeryAnesthesiaTransfusionRuntimeAssetMatches(body.safetyRedline, runtime.safetyAsset) &&
    surgeryAnesthesiaTransfusionRuntimeAssetMatches(body.riskMatrix, runtime.cdssRiskAsset) &&
    surgeryAnesthesiaTransfusionRuntimeAssetMatches(body.ruleAsset, runtime.ruleAsset) &&
    surgeryAnesthesiaTransfusionRuntimeAssetMatches(body.actionCard, runtime.actionCardAsset) &&
    hasCompleteSurgeryAnesthesiaTransfusionTerminologyGate(body.terminologyGate) &&
    hasCompleteSurgeryAnesthesiaTransfusionSafetyRedline(body.safetyRedline) &&
    hasCompleteSurgeryAnesthesiaTransfusionRiskMatrix(body.riskMatrix) &&
    hasCompleteSurgeryAnesthesiaTransfusionActionCard(body.actionCard) &&
    hasCompleteSurgeryAnesthesiaTransfusionRuleAsset(body.ruleAsset) &&
    hasCompleteSurgeryAnesthesiaTransfusionActivationRequest(body.activationRequest, runtime) &&
    hasCompleteSurgeryAnesthesiaTransfusionClinicalContext(
      body.clinicalContext,
      runtime.releaseId,
    ) &&
    hasCompleteSurgeryAnesthesiaTransfusionOutbound(
      body.outboundChecklist,
      body.adapter,
      body.clinicalContext,
    ) &&
    hasCompleteSurgeryAnesthesiaTransfusionInbound(
      body.inboundSurgeryEvent,
      body.adapter,
      body.webhookSignature,
      body.outboundChecklist,
      runtime.releaseId,
    ) &&
    hasCompleteSurgeryAnesthesiaTransfusionTrigger(body.clinicalTrigger, runtime.releaseId) &&
    hasCompleteSurgeryAnesthesiaTransfusionRecommendation(
      body.recommendation,
      runtime,
      body.clinicalTrigger,
      body.ruleAsset,
    ) &&
    hasCompleteSurgeryAnesthesiaTransfusionManualConfirmation(
      body.manualConfirmation,
      runtime.actionCardAsset,
    ) &&
    hasCompleteSurgeryAnesthesiaTransfusionRectification(
      body.qualityRectification,
      body.recommendation,
    ) &&
    evidencePathsResolve(body, [
      slice.outboundPath,
      slice.inboundPath,
      slice.manualConfirmationPath,
      slice.rectificationPath,
    ])
  );
}

function hasNursingAnesthesiaTransfusionIcuConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedNursingAnesthesiaTransfusionIcuConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整护理系统") &&
    hasNegatedScopeTerm(statement, "完整围手术期系统") &&
    hasNegatedScopeTerm(statement, "完整手麻系统") &&
    hasNegatedScopeTerm(statement, "完整手术室系统") &&
    hasNegatedScopeTerm(statement, "完整输血系统") &&
    hasNegatedScopeTerm(statement, "完整 ICU 系统") &&
    hasNegatedScopeTerm(statement, "完整护理手麻手术室输血 ICU 第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "真实外部成功联通") &&
    hasNegatedScopeTerm(statement, "自动开嘱") &&
    hasNegatedScopeTerm(statement, "自动输血") &&
    hasNegatedScopeTerm(statement, "自动手术") &&
    hasNegatedScopeTerm(statement, "完整 S26") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedNursingAnesthesiaTransfusionIcuConsumerSliceScopeClaim(statement: string) {
  return [
    "完整护理系统",
    "完整围手术期系统",
    "完整手麻系统",
    "完整手术室系统",
    "完整输血系统",
    "完整 ICU 系统",
    "完整ICU系统",
    "完整护理手麻手术室输血 ICU 第三方系统族覆盖",
    "完整护理手麻手术室输血ICU第三方系统族覆盖",
    "真实外部成功联通",
    "自动开嘱",
    "自动输血",
    "自动手术",
    "完整 S26",
    "完整S26",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线验收",
    "完整上线",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

type CriticalEmergencyIcuRuntimeAsset = {
  assetType: "TERMINOLOGY" | "CDSS_RISK" | "RULE" | "PATHWAY" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

function hasCompleteCriticalEmergencyIcuApiEvidence(value: unknown) {
  const evidence = recordValue(value);
  return [
    "monitoringAdapterCreatedThroughRealService",
    "monitoringWebhookCreatedThroughRealService",
    "emergencyOnboardingCreatedThroughRealService",
    "webhookSignaturePreviewGenerated",
    "terminologyActivated",
    "riskMatrixCreated",
    "ruleCreated",
    "pathwayCreated",
    "actionCardPublished",
    "runtimeActivatedWithCriticalAssets",
    "triageContextCreatedFromFrontdesk",
    "inboundMonitoringEventAccepted",
    "clinicalEvaluationTriggeredFromFrontdesk",
    "humanEscalationConfirmationRecorded",
    "workflowEscalationTodoCompleted",
  ].every((field) => evidence?.[field] === true);
}

function parseCriticalEmergencyIcuRuntimeEvidence(value: unknown) {
  const runtime = recordValue(value);
  if (
    !runtime ||
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo < 1 ||
    !isSha256(runtime.manifestSha256) ||
    !Array.isArray(runtime.assets)
  ) {
    return null;
  }
  const terminologyAsset = parseCriticalEmergencyIcuRuntimeAsset(
    runtime.terminologyAsset,
    "TERMINOLOGY",
  );
  const cdssRiskAsset = parseCriticalEmergencyIcuRuntimeAsset(runtime.cdssRiskAsset, "CDSS_RISK");
  const ruleAsset = parseCriticalEmergencyIcuRuntimeAsset(runtime.ruleAsset, "RULE");
  const pathwayAsset = parseCriticalEmergencyIcuRuntimeAsset(runtime.pathwayAsset, "PATHWAY");
  const actionCardAsset = parseCriticalEmergencyIcuRuntimeAsset(
    runtime.actionCardAsset,
    "ACTION_CARD",
  );
  const runtimeAssets = runtime.assets;
  const assets = [terminologyAsset, cdssRiskAsset, ruleAsset, pathwayAsset, actionCardAsset];
  if (
    assets.some((asset) => asset === null) ||
    !assets.every((asset) =>
      runtimeAssets.some((item) =>
        criticalEmergencyIcuRuntimeAssetMatches(item, asset as CriticalEmergencyIcuRuntimeAsset, {
          requireActive: true,
        }),
      ),
    )
  ) {
    return null;
  }
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    terminologyAsset: terminologyAsset as CriticalEmergencyIcuRuntimeAsset,
    cdssRiskAsset: cdssRiskAsset as CriticalEmergencyIcuRuntimeAsset,
    ruleAsset: ruleAsset as CriticalEmergencyIcuRuntimeAsset,
    pathwayAsset: pathwayAsset as CriticalEmergencyIcuRuntimeAsset,
    actionCardAsset: actionCardAsset as CriticalEmergencyIcuRuntimeAsset,
  };
}

function parseCriticalEmergencyIcuRuntimeAsset(
  value: unknown,
  assetType: CriticalEmergencyIcuRuntimeAsset["assetType"],
): CriticalEmergencyIcuRuntimeAsset | null {
  const asset = recordValue(value);
  if (
    !asset ||
    asset.assetType !== assetType ||
    !hasText(asset.assetIdentity) ||
    !hasText(asset.versionId) ||
    !hasText(asset.versionNo) ||
    !isSha256(asset.contentHash) ||
    asset.entryState !== "ACTIVE"
  ) {
    return null;
  }
  return {
    assetType,
    assetIdentity: String(asset.assetIdentity),
    versionId: String(asset.versionId),
    versionNo: String(asset.versionNo),
    contentHash: String(asset.contentHash),
  };
}

function criticalEmergencyIcuRuntimeAssetMatches(
  value: unknown,
  asset: CriticalEmergencyIcuRuntimeAsset,
  options: { requireActive?: boolean } = {},
) {
  const candidate = recordValue(value);
  return (
    candidate?.assetType === asset.assetType &&
    candidate.assetIdentity === asset.assetIdentity &&
    candidate.versionId === asset.versionId &&
    candidate.versionNo === asset.versionNo &&
    candidate.contentHash === asset.contentHash &&
    (!options.requireActive || candidate.entryState === "ACTIVE")
  );
}

function hasCompleteCriticalEmergencyIcuAdapterEvidence(value: unknown) {
  const adapter = recordValue(value);
  const mappings = Array.isArray(adapter?.fieldMappings) ? adapter.fieldMappings : [];
  return (
    hasText(adapter?.adapterId) &&
    adapter?.systemFamilyCode === "LIS_MONITORING_CRITICAL" &&
    adapter.sourceSystem === "LIS_MONITORING_CRITICAL" &&
    adapter.targetSystem === "LIS_MONITORING_CRITICAL" &&
    adapter.protocolType === "Webhook" &&
    criticalEmergencyIcuMappingTargets(mappings, "/patient/mpi") &&
    criticalEmergencyIcuMappingTargets(mappings, "/observations/0/code") &&
    criticalEmergencyIcuMappingTargets(mappings, "/observations/0/valueNumeric") &&
    criticalEmergencyIcuMappingTargets(mappings, "/observations/1", "LOINC") &&
    criticalEmergencyIcuMappingTargets(mappings, "/observations/1/valueNumeric") &&
    criticalEmergencyIcuMappingTargets(mappings, "/extensions/local/criticalCare/ventilatorMode") &&
    criticalEmergencyIcuMappingTargets(
      mappings,
      "/extensions/local/criticalCare/vasopressorRunning",
    ) &&
    criticalEmergencyIcuMappingTargets(mappings, "/extensions/local/criticalCare/noDeviceControl")
  );
}

function criticalEmergencyIcuMappingTargets(
  mappings: unknown[],
  targetPath: string,
  targetDictionaryKey?: string,
) {
  return mappings.some((item) => {
    const mapping = recordValue(item);
    return (
      mapping?.targetPath === targetPath &&
      (!targetDictionaryKey || mapping.targetDictionaryKey === targetDictionaryKey)
    );
  });
}

function hasCompleteCriticalEmergencyIcuOnboarding(value: unknown, adapterValue: unknown) {
  const onboarding = recordValue(value);
  const adapter = recordValue(adapterValue);
  return (
    onboarding !== null &&
    hasText(onboarding.onboardingId) &&
    onboarding.accessMode === "ADAPTER" &&
    onboarding.adapterId === adapter?.adapterId &&
    onboarding.systemFamilyCode === "LIS_MONITORING_CRITICAL" &&
    onboarding.sourceSystem === "LIS_MONITORING_CRITICAL" &&
    String(onboarding.businessScenario ?? "").includes("S19") &&
    String(onboarding.businessScenario ?? "").includes("S24") &&
    String(onboarding.businessScenario ?? "").includes("S27") &&
    onboarding.healthStatus === "NOT_CONNECTED"
  );
}

function hasCompleteCriticalEmergencyIcuWebhookEvidence(
  webhookValue: unknown,
  adapterValue: unknown,
) {
  const webhook = recordValue(webhookValue);
  const adapter = recordValue(adapterValue);
  return (
    webhook !== null &&
    hasText(webhook.webhookId) &&
    webhook.adapterId === adapter?.adapterId &&
    webhook.signatureAlgorithm === "HMAC-SHA256" &&
    webhook.canonicalPayloadIncludesTraceId === true &&
    webhook.previewGenerated === true
  );
}

function hasCompleteCriticalEmergencyIcuTerminologyGate(value: unknown) {
  const terminology = recordValue(value);
  return (
    terminology?.assetType === "TERMINOLOGY" &&
    hasText(terminology.assetIdentity) &&
    hasText(terminology.versionId) &&
    hasText(terminology.versionNo) &&
    isSha256(terminology.contentHash) &&
    terminology.standardSystem === "LOINC" &&
    terminology.standardCode === "2524-7" &&
    terminology.localCode === "ICU-LAC" &&
    terminology.sourceSystem === "LIS_MONITORING_CRITICAL" &&
    terminology.category === "LAB" &&
    typeof terminology.mappingId === "number" &&
    terminology.mappingId > 0 &&
    hasCompleteCriticalEmergencyIcuConfirmedMapping(terminology.confirmedMapping, terminology)
  );
}

function hasCompleteCriticalEmergencyIcuConfirmedMapping(
  value: unknown,
  terminology: Record<string, unknown>,
) {
  const mapping = recordValue(value);
  return (
    mapping !== null &&
    mapping.mappingId === terminology.mappingId &&
    mapping.localTermId === terminology.localTermId &&
    mapping.standardTermId === terminology.standardTermId &&
    mapping.sourceSystem === terminology.sourceSystem &&
    mapping.category === terminology.category
  );
}

function hasCompleteCriticalEmergencyIcuRiskMatrix(value: unknown) {
  const risk = recordValue(value);
  return (
    risk?.assetType === "CDSS_RISK" &&
    risk.assetIdentity === "CDSS.RISK.MATRIX" &&
    risk.triggerPoint === "patient-view" &&
    risk.riskLevel === "CRITICAL" &&
    risk.reviewRequirement === "PHYSICIAN_CONFIRMATION" &&
    risk.automationLevel === "INFORM_ONLY" &&
    risk.autoExecutionAllowed === false
  );
}

function hasCompleteCriticalEmergencyIcuActionCard(value: unknown) {
  const actionCard = recordValue(value);
  return (
    actionCard?.assetType === "ACTION_CARD" &&
    hasText(actionCard.assetIdentity) &&
    String(actionCard.assetIdentity).startsWith("ACTION_CARD.CRITICAL.") &&
    actionCard.requiresPhysicianConfirmation === true &&
    actionCard.noAutoOrder === true &&
    actionCard.noAutoTransfer === true &&
    actionCard.noDeviceControl === true &&
    actionCard.noAutoVentilatorChange === true
  );
}

function hasCompleteCriticalEmergencyIcuRuleAsset(value: unknown) {
  const rule = recordValue(value);
  return (
    rule?.assetType === "RULE" &&
    hasText(rule.assetIdentity) &&
    String(rule.assetIdentity).startsWith("RULE.CRITICAL.") &&
    hasText(rule.ruleId) &&
    hasText(rule.ruleVersionId)
  );
}

function hasCompleteCriticalEmergencyIcuPathwayAsset(value: unknown) {
  const pathway = recordValue(value);
  return (
    pathway?.assetType === "PATHWAY" &&
    hasText(pathway.assetIdentity) &&
    String(pathway.assetIdentity).startsWith("PATHWAY.CRITICAL.") &&
    hasText(pathway.templateId)
  );
}

function hasCompleteCriticalEmergencyIcuActivationRequest(
  value: unknown,
  runtime: {
    terminologyAsset: CriticalEmergencyIcuRuntimeAsset;
    cdssRiskAsset: CriticalEmergencyIcuRuntimeAsset;
    ruleAsset: CriticalEmergencyIcuRuntimeAsset;
    pathwayAsset: CriticalEmergencyIcuRuntimeAsset;
    actionCardAsset: CriticalEmergencyIcuRuntimeAsset;
  },
) {
  return [
    runtime.terminologyAsset,
    runtime.cdssRiskAsset,
    runtime.ruleAsset,
    runtime.pathwayAsset,
    runtime.actionCardAsset,
  ].every((asset) =>
    runtimeReleasePayloadContainsCandidate(value, "activeAssets", {
      assetType: asset.assetType,
      assetIdentity: asset.assetIdentity,
      versionId: asset.versionId,
    }),
  );
}

function hasCompleteCriticalEmergencyIcuClinicalContext(value: unknown, runtimeReleaseId: string) {
  const context = recordValue(value);
  const resources = recordValue(context?.resources);
  const encounters = Array.isArray(resources?.encounters) ? resources.encounters : [];
  const conditions = Array.isArray(resources?.conditions) ? resources.conditions : [];
  const observations = Array.isArray(resources?.observations) ? resources.observations : [];
  const procedures = Array.isArray(resources?.procedures) ? resources.procedures : [];
  const extensions = recordValue(resources?.extensions);
  const local = recordValue(extensions?.local);
  const emergencyTriage = recordValue(local?.emergencyTriage);
  const criticalCare = recordValue(local?.criticalCare);
  return (
    hasText(context?.patientId) &&
    hasText(context?.encounterId) &&
    hasText(context?.contextSnapshotId) &&
    context?.runtimeReleaseId === runtimeReleaseId &&
    context.clinicalSetting === "ED" &&
    encounters.some((item) => {
      const encounter = recordValue(item);
      return encounter?.encounterType === "ED" && hasText(encounter.departmentId);
    }) &&
    conditions.some((item) => {
      const condition = recordValue(item);
      return condition?.code === "R57.900" && hasText(condition.displayName);
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return observation?.code === "SHOCK_INDEX" && Number(observation.valueNumeric) >= 1.3;
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return (
        observation?.code === "2524-7" &&
        Number(observation.valueNumeric) >= 4 &&
        observation.criticalFlag === "CRITICAL"
      );
    }) &&
    procedures.some((item) => {
      const procedure = recordValue(item);
      return procedure?.code === "5A1955Z" && hasText(procedure.displayName);
    }) &&
    emergencyTriage?.triageLevel === "LEVEL_1" &&
    emergencyTriage.destinationCandidate === "ICU" &&
    emergencyTriage.manualEscalationRequired === true &&
    criticalCare?.ventilatorMode === "SIMV" &&
    criticalCare.vasopressorRunning === true &&
    criticalCare.noDeviceControl === true
  );
}

function hasCompleteCriticalEmergencyIcuInbound(
  value: unknown,
  adapterValue: unknown,
  webhookValue: unknown,
  contextValue: unknown,
  runtimeReleaseId: string,
) {
  const inbound = recordValue(value);
  const adapter = recordValue(adapterValue);
  const webhook = recordValue(webhookValue);
  const context = recordValue(contextValue);
  const mappedPayload = recordValue(inbound?.mappedPayload);
  const signedPayload = recordValue(inbound?.signedPayload);
  const signedCriticalCare = recordValue(signedPayload?.criticalCare) ?? signedPayload;
  const clinicalEvent = recordValue(inbound?.clinicalEvent);
  const observations = Array.isArray(mappedPayload?.observations) ? mappedPayload.observations : [];
  const mappedExtensions = recordValue(mappedPayload?.extensions);
  const mappedLocal = recordValue(mappedExtensions?.local);
  const criticalCare =
    recordValue(mappedPayload?.criticalCare) ?? recordValue(mappedLocal?.criticalCare);
  return (
    inbound !== null &&
    mappedPayload !== null &&
    hasText(inbound.messageId) &&
    hasText(inbound.traceId) &&
    inbound.adapterId === adapter?.adapterId &&
    inbound.webhookId === webhook?.webhookId &&
    inbound.patientId === context?.patientId &&
    inbound.contextSnapshotId === context?.contextSnapshotId &&
    inbound.sourceSystem === "LIS_MONITORING_CRITICAL" &&
    inbound.status === "SUCCESS" &&
    inbound.clinicalEventStatus === "RECEIVED" &&
    hasProcessedCriticalEmergencyIcuClinicalEvent(clinicalEvent, runtimeReleaseId) &&
    typeof inbound.mappedFieldCount === "number" &&
    inbound.mappedFieldCount >= 7 &&
    Number(signedPayload?.shockIndexValue) >= 1.3 &&
    signedPayload?.lactateCode === "ICU-LAC" &&
    Number(signedPayload.lactateValue) >= 4 &&
    signedCriticalCare?.noDeviceControl === true &&
    observations.some((item) => {
      const observation = recordValue(item);
      return observation?.code === "SHOCK_INDEX" && Number(observation.valueNumeric) >= 1.3;
    }) &&
    observations.some((item) => {
      const observation = recordValue(item);
      return (
        observation?.standardCode === "2524-7" &&
        observation.codeSystem === "LOINC" &&
        observation.sourceSystem === "LIS_MONITORING_CRITICAL" &&
        observation.runtimeReleaseId === runtimeReleaseId &&
        Number(observation.valueNumeric) >= 4 &&
        observation.criticalFlag === "CRITICAL"
      );
    }) &&
    criticalCare?.ventilatorMode === "SIMV" &&
    criticalCare.vasopressorRunning === true &&
    criticalCare.noDeviceControl === true
  );
}

function hasProcessedCriticalEmergencyIcuClinicalEvent(
  clinicalEvent: Record<string, unknown> | null,
  runtimeReleaseId: string,
) {
  return (
    clinicalEvent !== null &&
    hasText(clinicalEvent.eventId) &&
    clinicalEvent.status === "PROCESSED" &&
    clinicalEvent.runtimeReleaseId === runtimeReleaseId &&
    (clinicalEvent.errorCode === null || clinicalEvent.errorCode === undefined) &&
    (clinicalEvent.errorClass === null || clinicalEvent.errorClass === undefined)
  );
}

function hasCompleteCriticalEmergencyIcuTrigger(value: unknown, runtimeReleaseId: string) {
  const trigger = recordValue(value);
  return (
    trigger !== null &&
    hasText(trigger.triggerId) &&
    hasText(trigger.contextSnapshotId) &&
    trigger.runtimeReleaseId === runtimeReleaseId &&
    trigger.triggerType === "patient-view" &&
    hasText(trigger.cardId) &&
    Array.isArray(trigger.relatedCardIds) &&
    trigger.relatedCardIds.includes(trigger.cardId)
  );
}

function hasCompleteCriticalEmergencyIcuRecommendation(
  value: unknown,
  runtime: {
    releaseId: string;
    ruleAsset: CriticalEmergencyIcuRuntimeAsset;
    pathwayAsset: CriticalEmergencyIcuRuntimeAsset;
    actionCardAsset: CriticalEmergencyIcuRuntimeAsset;
  },
  triggerValue: unknown,
  ruleValue: unknown,
) {
  const recommendation = recordValue(value);
  const trigger = recordValue(triggerValue);
  const rule = recordValue(ruleValue);
  if (
    !recommendation ||
    !trigger ||
    !rule ||
    !hasText(recommendation.cardId) ||
    recommendation.cardId !== trigger.cardId ||
    recommendation.triggerRuntimeReleaseId !== runtime.releaseId ||
    recommendation.cardStatus !== "PENDING" ||
    recommendation.requiresPhysicianConfirmation !== true ||
    recommendation.aiGenerated !== false
  ) {
    return false;
  }
  const explanation = recordValue(recommendation.explanation);
  const runtimeRelease = recordValue(explanation?.runtimeRelease);
  const ruleExplanation = recordValue(explanation?.ruleExplanation);
  const conditionEvidence = Array.isArray(ruleExplanation?.conditionEvidence)
    ? ruleExplanation.conditionEvidence
    : [];
  const runtimeAssetEvidence = Array.isArray(ruleExplanation?.runtimeAssetEvidence)
    ? ruleExplanation.runtimeAssetEvidence
    : [];
  return (
    explanation?.matchType === "RULE" &&
    explanation.ruleId === rule.ruleId &&
    explanation.ruleCode === rule.assetIdentity &&
    explanation.ruleVersionId === rule.ruleVersionId &&
    runtimeRelease?.runtimeReleaseId === runtime.releaseId &&
    runtimeRelease.assetVersionId === runtime.ruleAsset.versionId &&
    runtimeRelease.assetVersionNo === runtime.ruleAsset.versionNo &&
    runtimeRelease.contentHash === runtime.ruleAsset.contentHash &&
    criticalEmergencyIcuConditionMatched(conditionEvidence, "observations[].criticalFlag") &&
    criticalEmergencyIcuConditionMatched(
      conditionEvidence,
      "extensions.local.emergencyTriage.triageLevel",
    ) &&
    criticalEmergencyIcuConditionMatched(
      conditionEvidence,
      "extensions.local.criticalCare.vasopressorRunning",
    ) &&
    runtimeAssetEvidence.some((item) => {
      const evidence = recordValue(item);
      return (
        evidence?.assetType === "ACTION_CARD" &&
        evidence.assetIdentity === runtime.actionCardAsset.assetIdentity &&
        evidence.assetVersion === runtime.actionCardAsset.versionNo &&
        evidence.contentHash === runtime.actionCardAsset.contentHash
      );
    }) &&
    runtimeAssetEvidence.some((item) => {
      const evidence = recordValue(item);
      return (
        evidence?.assetType === "PATHWAY" &&
        evidence.assetIdentity === runtime.pathwayAsset.assetIdentity &&
        evidence.assetVersion === runtime.pathwayAsset.versionNo &&
        evidence.contentHash === runtime.pathwayAsset.contentHash
      );
    })
  );
}

function criticalEmergencyIcuConditionMatched(values: unknown[], fact: string) {
  return values.some((item) => {
    const evidence = recordValue(item);
    return evidence?.fact === fact && evidence.matched === true;
  });
}

function hasCompleteCriticalEmergencyIcuManualEscalation(
  value: unknown,
  actionCardAsset: CriticalEmergencyIcuRuntimeAsset,
  recommendationValue: unknown,
) {
  const escalation = recordValue(value);
  const persisted = recordValue(escalation?.persisted);
  const actionCardEvidence = recordValue(escalation?.actionCardEvidence);
  const recommendation = recordValue(recommendationValue);
  return (
    escalation !== null &&
    persisted !== null &&
    recommendation !== null &&
    hasText(escalation.feedbackId) &&
    escalation.cardStatus === "ACCEPTED" &&
    escalation.canonicalSessionRole === "clinical-user" &&
    persisted.feedbackId === escalation.feedbackId &&
    persisted.cardId === recommendation.cardId &&
    persisted.feedbackType === "ACCEPT" &&
    persisted.operatorRole === "DOCTOR" &&
    persisted.reasonCode === "CONFIRMED" &&
    escalation.noAutoOrder === true &&
    escalation.noAutoTransfer === true &&
    escalation.noDeviceControl === true &&
    escalation.noAutoVentilatorChange === true &&
    actionCardEvidence?.assetType === "ACTION_CARD" &&
    actionCardEvidence.assetIdentity === actionCardAsset.assetIdentity &&
    actionCardEvidence.versionId === actionCardAsset.versionId &&
    actionCardEvidence.versionNo === actionCardAsset.versionNo &&
    actionCardEvidence.contentHash === actionCardAsset.contentHash &&
    actionCardEvidence.entryState === "ACTIVE" &&
    actionCardEvidence.noAutoOrder === true &&
    actionCardEvidence.noAutoTransfer === true &&
    actionCardEvidence.noDeviceControl === true &&
    actionCardEvidence.noAutoVentilatorChange === true
  );
}

function hasCompleteCriticalEmergencyIcuTodo(
  value: unknown,
  recommendationValue: unknown,
  contextValue: unknown,
) {
  const todo = recordValue(value);
  const recommendation = recordValue(recommendationValue);
  const context = recordValue(contextValue);
  return (
    todo !== null &&
    recommendation !== null &&
    context !== null &&
    hasText(todo.todoId) &&
    todo.sourceType === "RECOMMENDATION_CARD" &&
    todo.sourceId === recommendation.cardId &&
    (todo.priority === "CRITICAL" || todo.priority === "HIGH") &&
    todo.status === "COMPLETED" &&
    todo.completedByRole === "clinical-user" &&
    hasText(todo.completionReason) &&
    String(todo.completionReason).includes("不自动转 ICU") &&
    String(todo.completionReason).includes("不自动开嘱") &&
    String(todo.completionReason).includes("不控制设备") &&
    todo.patientId === context.patientId &&
    todo.encounterId === context.encounterId
  );
}

function hasCompleteLisMonitoringCriticalConsumerSlice(body: Record<string, unknown>) {
  const runtime = parseCriticalEmergencyIcuRuntimeEvidence(body.runtime);
  if (!runtime) return false;
  const slice = recordValue(body.lisMonitoringCriticalConsumerSlice);
  const adapter = recordValue(body.monitoringAdapter);
  const onboarding = recordValue(body.emergencyOnboarding);
  const webhook = recordValue(body.webhookSignature);
  const inbound = recordValue(body.inboundMonitoringEvent);
  const clinicalEvent = recordValue(inbound?.clinicalEvent);
  const context = recordValue(body.clinicalContext);
  const recommendation = recordValue(body.recommendation);
  const manualEscalation = recordValue(body.manualEscalation);
  const todo = recordValue(body.escalationTodo);
  return (
    slice !== null &&
    adapter !== null &&
    onboarding !== null &&
    webhook !== null &&
    inbound !== null &&
    clinicalEvent !== null &&
    context !== null &&
    recommendation !== null &&
    manualEscalation !== null &&
    todo !== null &&
    slice.systemFamilyCode === "LIS_MONITORING_CRITICAL" &&
    hasText(slice.familyName) &&
    (String(slice.familyName).includes("检验") || String(slice.familyName).includes("监护")) &&
    slice.consumer === "CRITICAL_MONITORING_OBSERVATION_INBOUND_RISK_ESCALATION" &&
    arrayEquals(slice.canonicalResources, ["Patient", "Encounter", "Observation"]) &&
    arrayEquals(slice.sourceSystems, ["LIS_MONITORING_CRITICAL", "MEDKERNEL_FRONTDESK"]) &&
    slice.adapterVerified === true &&
    slice.onboardingNotConnectedVerified === true &&
    slice.webhookSignatureVerified === true &&
    slice.signedInboundObservationVerified === true &&
    slice.standardObservationMappedVerified === true &&
    (slice.runtimeConsumerVerified === true || slice.recommendationConsumerVerified === true) &&
    slice.recommendationVerified === true &&
    slice.manualEscalationVerified === true &&
    slice.todoClosureVerified === true &&
    slice.auditVerified === true &&
    slice.permissionVerified === true &&
    slice.sixStateBoundaryVerified === true &&
    slice.requiresPhysicianConfirmation === true &&
    slice.noAutoOrder === true &&
    slice.noAutoTransfer === true &&
    slice.noDeviceControl === true &&
    slice.noAutoVentilatorChange === true &&
    slice.noExternalSuccessClaim === true &&
    slice.aiGenerated !== true &&
    slice.patientId === context.patientId &&
    slice.encounterId === context.encounterId &&
    slice.contextSnapshotId === context.contextSnapshotId &&
    slice.runtimeReleaseId === runtime.releaseId &&
    slice.adapterId === adapter.adapterId &&
    slice.onboardingId === onboarding.onboardingId &&
    slice.webhookId === webhook.webhookId &&
    slice.inboundMessageId === inbound.messageId &&
    slice.clinicalEventId === clinicalEvent.eventId &&
    slice.recommendationCardId === recommendation.cardId &&
    slice.feedbackId === manualEscalation.feedbackId &&
    slice.todoId === todo.todoId &&
    slice.adapterPath === "monitoringAdapter" &&
    slice.onboardingPath === "emergencyOnboarding" &&
    slice.webhookPath === "webhookSignature" &&
    slice.inboundPath === "inboundMonitoringEvent" &&
    slice.contextPath === "clinicalContext" &&
    slice.recommendationPath === "recommendation" &&
    slice.manualEscalationPath === "manualEscalation" &&
    slice.todoPath === "escalationTodo" &&
    hasLisMonitoringCriticalConsumerSliceScopeBoundary(slice.scopeStatement) &&
    hasCompleteCriticalEmergencyIcuApiEvidence(body.apiEvidence) &&
    hasCompleteCriticalEmergencyIcuAdapterEvidence(body.monitoringAdapter) &&
    hasCompleteCriticalEmergencyIcuOnboarding(body.emergencyOnboarding, body.monitoringAdapter) &&
    hasCompleteCriticalEmergencyIcuWebhookEvidence(body.webhookSignature, body.monitoringAdapter) &&
    hasCompleteCriticalEmergencyIcuTerminologyGate(body.terminologyGate) &&
    hasCompleteCriticalEmergencyIcuRiskMatrix(body.riskMatrix) &&
    hasCompleteCriticalEmergencyIcuActionCard(body.actionCard) &&
    hasCompleteCriticalEmergencyIcuActivationRequest(body.activationRequest, runtime) &&
    hasCompleteCriticalEmergencyIcuClinicalContext(body.clinicalContext, runtime.releaseId) &&
    hasCompleteCriticalEmergencyIcuInbound(
      body.inboundMonitoringEvent,
      body.monitoringAdapter,
      body.webhookSignature,
      body.clinicalContext,
      runtime.releaseId,
    ) &&
    hasCompleteCriticalEmergencyIcuTrigger(body.clinicalTrigger, runtime.releaseId) &&
    hasCompleteCriticalEmergencyIcuRecommendation(
      body.recommendation,
      runtime,
      body.clinicalTrigger,
      body.ruleAsset,
    ) &&
    hasCompleteCriticalEmergencyIcuManualEscalation(
      body.manualEscalation,
      runtime.actionCardAsset,
      body.recommendation,
    ) &&
    hasCompleteCriticalEmergencyIcuTodo(
      body.escalationTodo,
      body.recommendation,
      body.clinicalContext,
    ) &&
    evidencePathsResolve(body, [
      slice.adapterPath,
      slice.onboardingPath,
      slice.webhookPath,
      slice.inboundPath,
      slice.contextPath,
      slice.recommendationPath,
      slice.manualEscalationPath,
      slice.todoPath,
    ])
  );
}

function hasLisMonitoringCriticalConsumerSliceScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedLisMonitoringCriticalConsumerSliceScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整 LIS 系统") &&
    hasNegatedScopeTerm(statement, "完整监护设备平台") &&
    hasNegatedScopeTerm(statement, "完整急诊系统") &&
    hasNegatedScopeTerm(statement, "完整 ICU 系统") &&
    hasNegatedScopeTerm(statement, "真实外部成功联通") &&
    hasNegatedScopeTerm(statement, "自动开嘱") &&
    hasNegatedScopeTerm(statement, "自动转 ICU") &&
    hasNegatedScopeTerm(statement, "设备控制") &&
    hasNegatedScopeTerm(statement, "自动调整呼吸机") &&
    hasNegatedScopeTerm(statement, "完整 S19/S24/S27") &&
    hasNegatedScopeTerm(statement, "完整第三方系统族覆盖") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedLisMonitoringCriticalConsumerSliceScopeClaim(statement: string) {
  return [
    "完整 LIS 系统",
    "完整LIS系统",
    "完整监护设备平台",
    "完整急诊系统",
    "完整 ICU 系统",
    "完整ICU系统",
    "真实外部成功联通",
    "自动开嘱",
    "自动转 ICU",
    "自动转ICU",
    "设备控制",
    "自动调整呼吸机",
    "完整 S19/S24/S27",
    "完整S19/S24/S27",
    "完整第三方系统族覆盖",
    "所有第三方系统族完整覆盖",
    "完整 S0-S40",
    "完整S0-S40",
    "完整上线验收",
    "完整上线",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasCriticalEmergencyIcuScopeBoundary(value: unknown) {
  if (!hasText(value)) return false;
  const statement = String(value);
  return (
    statement.includes("代表切片") &&
    !hasUnnegatedCriticalEmergencyIcuScopeClaim(statement) &&
    hasNegatedScopeTerm(statement, "完整急诊系统") &&
    hasNegatedScopeTerm(statement, "完整 ICU 系统") &&
    hasNegatedScopeTerm(statement, "完整生命支持系统") &&
    hasNegatedScopeTerm(statement, "生命支持设备控制") &&
    hasNegatedScopeTerm(statement, "完整 S19/S24/S27") &&
    hasNegatedScopeTerm(statement, "完整 S0-S40") &&
    hasNegatedScopeTerm(statement, "完整上线验收")
  );
}

function hasUnnegatedCriticalEmergencyIcuScopeClaim(statement: string) {
  return [
    "完整急诊系统",
    "完整 ICU 系统",
    "完整ICU系统",
    "完整生命支持系统",
    "生命支持设备控制",
    "完整S19/S24/S27",
    "完整 S19/S24/S27",
    "完整S0-S40",
    "完整 S0-S40",
    "完整上线",
    "完整上线验收",
  ].some((term) => hasScopeCompletionClaimWithoutNegation(statement, term));
}

function hasScopeCompletionClaimWithoutNegation(statement: string, term: string) {
  const segments = statement
    .split(/[。；;.!?！？\n，,]/u)
    .map((segment) => segment.trim())
    .filter(Boolean);
  return segments.some((segment) => {
    const termIndex = segment.indexOf(term);
    if (termIndex < 0) return false;
    const suffix = segment.slice(termIndex + term.length);
    if (!/(?:已上线|完整上线|完成上线|已完成|完成|完整覆盖|全面覆盖)/u.test(suffix)) {
      return false;
    }
    const prefix = segment.slice(0, termIndex);
    return !/(?:不代表|不声明|未完成|不得外推|不能外推|不等于|并非|不是)/u.test(prefix);
  });
}

function parseExplanationObject(value: unknown) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  if (typeof value !== "string" || !value.trim()) return null;
  try {
    return recordValue(JSON.parse(value));
  } catch {
    return null;
  }
}

function hasCompleteRuntimeReleaseMultiHospitalEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  const primary = parseRuntimeReleaseHospitalEvidence(evidence.primaryHospital);
  const secondary = parseRuntimeReleaseHospitalEvidence(evidence.secondaryHospital);
  if (!primary || !secondary) return false;
  if (primary.hospitalId === secondary.hospitalId) return false;
  if (runtimeReleaseSameCandidate(primary.selectedCandidate, secondary.selectedCandidate)) {
    return false;
  }
  return (
    evidence.distinctHospitals === true &&
    evidence.distinctSelectedCandidates === true &&
    evidence.backendReadbacksIsolated === true &&
    evidence.runtimeConsumerReadbacksIsolated === true &&
    primary.excludesOtherHospitalCandidate === true &&
    secondary.excludesOtherHospitalCandidate === true &&
    runtimeReleasePayloadContainsCandidate(
      primary.activationReadback,
      "assets",
      primary.selectedCandidate,
      { requireActive: true },
    ) &&
    runtimeReleasePayloadContainsCandidate(
      primary.runtimeConsumerReadback,
      "assets",
      primary.selectedCandidate,
      { requireActive: true },
    ) &&
    runtimeReleasePayloadContainsCandidate(
      secondary.activationReadback,
      "assets",
      secondary.selectedCandidate,
      { requireActive: true },
    ) &&
    runtimeReleasePayloadContainsCandidate(
      secondary.runtimeConsumerReadback,
      "assets",
      secondary.selectedCandidate,
      { requireActive: true },
    ) &&
    !runtimeReleasePayloadContainsIdentity(
      primary.activationReadback,
      "assets",
      secondary.selectedCandidate,
    ) &&
    !runtimeReleasePayloadContainsIdentity(
      primary.runtimeConsumerReadback,
      "assets",
      secondary.selectedCandidate,
    ) &&
    !runtimeReleasePayloadContainsIdentity(
      secondary.activationReadback,
      "assets",
      primary.selectedCandidate,
    ) &&
    !runtimeReleasePayloadContainsIdentity(
      secondary.runtimeConsumerReadback,
      "assets",
      primary.selectedCandidate,
    )
  );
}

function hasCompleteRuntimeReleasePlatformUpgradeEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  const targetBaseline = parseRuntimeReleasePlatformUpgradeBaseline(evidence.targetBaseline);
  const currentRuntime = parseRuntimeReleasePlatformUpgradeRuntime(evidence.currentRuntime);
  const before = parseRuntimeReleaseOfflineRuntimeSnapshot(evidence.runtimeBefore);
  const after = parseRuntimeReleaseOfflineRuntimeSnapshot(evidence.runtimeAfter);
  const summary = evidence.diffSummary;
  if (
    !targetBaseline ||
    !currentRuntime ||
    !before ||
    !after ||
    !summary ||
    typeof summary !== "object" ||
    Array.isArray(summary) ||
    typeof evidence.analysisDigest !== "string" ||
    !/^[0-9a-f]{64}$/i.test(evidence.analysisDigest) ||
    evidence.runtimeMutation !== false ||
    !Array.isArray(evidence.items)
  ) {
    return false;
  }
  const diffSummary = summary as Record<string, unknown>;
  const diffCounts = ["added", "modified", "disabled", "unchanged", "conflictCount"].map(
    (key) => diffSummary[key],
  );
  if (!diffCounts.every((count) => typeof count === "number" && count >= 0)) return false;
  if (diffSummary.conflictCount !== 0) return false;
  const changedCount =
    Number(diffSummary.added) + Number(diffSummary.modified) + Number(diffSummary.disabled);
  if (changedCount < 1) return false;
  const parsedItems = evidence.items.map(parseRuntimeReleasePlatformUpgradeDiffItem);
  if (parsedItems.some((item) => item === null)) return false;
  const hasStructuredDiff = parsedItems.length > 0;
  const itemCounts = countRuntimeReleasePlatformUpgradeDiffItems(parsedItems);
  return (
    hasStructuredDiff &&
    itemCounts.added === Number(diffSummary.added) &&
    itemCounts.modified === Number(diffSummary.modified) &&
    itemCounts.disabled === Number(diffSummary.disabled) &&
    itemCounts.unchanged === Number(diffSummary.unchanged) &&
    itemCounts.changed > 0 &&
    currentRuntime.platformBaselineReleaseId !== targetBaseline.baselineReleaseId &&
    before.releaseId === currentRuntime.releaseId &&
    after.releaseId === currentRuntime.releaseId &&
    before.revisionNo === after.revisionNo &&
    before.manifestSha256 === after.manifestSha256
  );
}

function parseRuntimeReleasePlatformUpgradeBaseline(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const baseline = value as Record<string, unknown>;
  if (
    typeof baseline.baselineReleaseId !== "string" ||
    typeof baseline.revisionNo !== "number" ||
    typeof baseline.manifestSha256 !== "string"
  ) {
    return null;
  }
  return {
    baselineReleaseId: baseline.baselineReleaseId,
    revisionNo: baseline.revisionNo,
    manifestSha256: baseline.manifestSha256,
  };
}

function parseRuntimeReleasePlatformUpgradeRuntime(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const runtime = value as Record<string, unknown>;
  if (
    typeof runtime.releaseId !== "string" ||
    typeof runtime.revisionNo !== "number" ||
    typeof runtime.platformBaselineReleaseId !== "string" ||
    typeof runtime.manifestSha256 !== "string"
  ) {
    return null;
  }
  return {
    releaseId: runtime.releaseId,
    revisionNo: runtime.revisionNo,
    platformBaselineReleaseId: runtime.platformBaselineReleaseId,
    manifestSha256: runtime.manifestSha256,
  };
}

function parseRuntimeReleasePlatformUpgradeDiffItem(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const item = value as Record<string, unknown>;
  if (
    typeof item.assetType === "string" &&
    typeof item.assetIdentity === "string" &&
    ["ADDED", "MODIFIED", "DISABLED", "UNCHANGED"].includes(
      typeof item.changeType === "string" ? item.changeType : "",
    ) &&
    Array.isArray(item.conflicts)
  ) {
    return item.conflicts.length === 0 ? item : null;
  }
  return null;
}

function countRuntimeReleasePlatformUpgradeDiffItems(
  items: Array<{ changeType?: unknown } | null>,
) {
  const counts = {
    added: 0,
    modified: 0,
    disabled: 0,
    unchanged: 0,
    changed: 0,
  };
  for (const item of items) {
    if (item?.changeType === "ADDED") {
      counts.added++;
      counts.changed++;
    } else if (item?.changeType === "MODIFIED") {
      counts.modified++;
      counts.changed++;
    } else if (item?.changeType === "DISABLED") {
      counts.disabled++;
      counts.changed++;
    } else if (item?.changeType === "UNCHANGED") {
      counts.unchanged++;
    }
  }
  return counts;
}

function hasCompleteRuntimeReleaseOfflineDeliveryEvidence(value: {
  apiEvidence?: unknown;
  offlineDelivery?: unknown;
}) {
  if (
    !value.apiEvidence ||
    typeof value.apiEvidence !== "object" ||
    Array.isArray(value.apiEvidence)
  ) {
    return false;
  }
  const apiEvidence = value.apiEvidence as Record<string, unknown>;
  if (
    apiEvidence.offlineDeliveryExported !== true ||
    apiEvidence.offlineDeliveryFileDownloaded !== true ||
    apiEvidence.offlineDeliveryImportPreviewValidated !== true ||
    apiEvidence.offlineDeliveryRuntimeUnchanged !== true ||
    apiEvidence.offlineDeliveryRestoreExecuted !== true ||
    apiEvidence.offlineDeliveryRestoreCreatedNewRevision !== true ||
    apiEvidence.offlineDeliveryRestoreReadbackMatched !== true ||
    apiEvidence.offlineDeliveryRestoreRuntimeConsumerMatched !== true
  ) {
    return false;
  }
  if (
    !value.offlineDelivery ||
    typeof value.offlineDelivery !== "object" ||
    Array.isArray(value.offlineDelivery)
  ) {
    return false;
  }
  const evidence = value.offlineDelivery as Record<string, unknown>;
  const delivery = parseRuntimeReleaseOfflineDelivery(evidence.delivery);
  const preview = parseRuntimeReleaseOfflineImportPreview(evidence.importPreview);
  const restore = parseRuntimeReleaseOfflineRestore(evidence.restore);
  const file = evidence.downloadedFile;
  if (
    !delivery ||
    !preview ||
    !restore ||
    !file ||
    typeof file !== "object" ||
    Array.isArray(file)
  ) {
    return false;
  }
  const downloaded = file as Record<string, unknown>;
  const runtimeBefore = parseRuntimeReleaseOfflineRuntimeSnapshot(evidence.runtimeBefore);
  const runtimeAfter = parseRuntimeReleaseOfflineRuntimeSnapshot(evidence.runtimeAfter);
  const runtimeBeforeRestore = parseRuntimeReleaseOfflineRuntimeSnapshot(
    evidence.runtimeBeforeRestore,
  );
  const runtimeAfterRestore = parseRuntimeReleaseOfflineRuntimeSnapshotWithSelection(
    evidence.runtimeAfterRestore,
  );
  const runtimeConsumerAfterRestore = parseRuntimeReleaseOfflineRuntimeSnapshotWithSelection(
    evidence.runtimeConsumerAfterRestore,
  );
  return (
    delivery.deliveryKind === "CLINICAL_RUNTIME_RELEASE" &&
    delivery.runtimeMutation === false &&
    delivery.signatureAlgorithm === "SM3_WITH_SM2" &&
    delivery.evidenceId.length > 0 &&
    delivery.fileUri.endsWith(`/snapshots/${delivery.evidenceId}/file`) &&
    delivery.fileDigest.startsWith("sm3:") &&
    delivery.fileDigest.length === 68 &&
    delivery.releaseId === preview.releaseId &&
    delivery.hospitalId === preview.hospitalId &&
    delivery.itemCount > 0 &&
    preview.status === "VALIDATED" &&
    preview.signatureValid === true &&
    preview.manifestMatched === true &&
    preview.runtimeMutation === false &&
    preview.itemCount === delivery.itemCount &&
    downloaded.fileUri === delivery.fileUri &&
    downloaded.containsDeliveryKind === true &&
    downloaded.containsRuntimeMutationFalse === true &&
    downloaded.containsReleaseId === true &&
    Boolean(runtimeBefore) &&
    Boolean(runtimeAfter) &&
    Boolean(runtimeBeforeRestore) &&
    Boolean(runtimeAfterRestore) &&
    Boolean(runtimeConsumerAfterRestore) &&
    runtimeBefore?.releaseId === delivery.releaseId &&
    runtimeAfter?.releaseId === delivery.releaseId &&
    runtimeBefore?.revisionNo === runtimeAfter?.revisionNo &&
    runtimeBefore?.manifestSha256 === runtimeAfter?.manifestSha256 &&
    restore.status === "RESTORED" &&
    restore.runtimeMutation === true &&
    restore.sourceReleaseId === delivery.releaseId &&
    restore.targetHospitalId === delivery.hospitalId &&
    restore.fileDigest === delivery.fileDigest &&
    restore.manifestSha256 === runtimeBefore?.manifestSha256 &&
    restore.itemCount === delivery.itemCount &&
    restore.restoredReleaseId.length > 0 &&
    restore.restoredReleaseId !== delivery.releaseId &&
    restore.restoredReleaseId !== restore.sourceReleaseId &&
    restore.restoredRevisionNo > (runtimeBeforeRestore?.revisionNo ?? 0) &&
    runtimeBeforeRestore?.releaseId !== runtimeBefore?.releaseId &&
    runtimeAfterRestore?.releaseId === restore.restoredReleaseId &&
    runtimeAfterRestore?.revisionNo === restore.restoredRevisionNo &&
    runtimeAfterRestore?.manifestSha256 === restore.manifestSha256 &&
    runtimeAfterRestore?.selectedCandidatePresent === true &&
    runtimeConsumerAfterRestore?.releaseId === runtimeAfterRestore?.releaseId &&
    runtimeConsumerAfterRestore?.revisionNo === runtimeAfterRestore?.revisionNo &&
    runtimeConsumerAfterRestore?.manifestSha256 === runtimeAfterRestore?.manifestSha256 &&
    runtimeConsumerAfterRestore?.selectedCandidatePresent === true
  );
}

function parseRuntimeReleaseOfflineDelivery(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const delivery = value as Record<string, unknown>;
  if (
    typeof delivery.deliveryKind !== "string" ||
    typeof delivery.evidenceId !== "string" ||
    typeof delivery.fileUri !== "string" ||
    typeof delivery.fileDigest !== "string" ||
    typeof delivery.signatureAlgorithm !== "string" ||
    typeof delivery.runtimeMutation !== "boolean" ||
    typeof delivery.releaseId !== "string" ||
    typeof delivery.hospitalId !== "string" ||
    typeof delivery.itemCount !== "number"
  ) {
    return null;
  }
  return {
    deliveryKind: delivery.deliveryKind,
    evidenceId: delivery.evidenceId,
    fileUri: delivery.fileUri,
    fileDigest: delivery.fileDigest,
    signatureAlgorithm: delivery.signatureAlgorithm,
    runtimeMutation: delivery.runtimeMutation,
    releaseId: delivery.releaseId,
    hospitalId: delivery.hospitalId,
    itemCount: delivery.itemCount,
  };
}

function parseRuntimeReleaseOfflineImportPreview(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const preview = value as Record<string, unknown>;
  if (
    typeof preview.status !== "string" ||
    typeof preview.signatureValid !== "boolean" ||
    typeof preview.manifestMatched !== "boolean" ||
    typeof preview.runtimeMutation !== "boolean" ||
    typeof preview.releaseId !== "string" ||
    typeof preview.hospitalId !== "string" ||
    typeof preview.itemCount !== "number"
  ) {
    return null;
  }
  return {
    status: preview.status,
    signatureValid: preview.signatureValid,
    manifestMatched: preview.manifestMatched,
    runtimeMutation: preview.runtimeMutation,
    releaseId: preview.releaseId,
    hospitalId: preview.hospitalId,
    itemCount: preview.itemCount,
  };
}

function parseRuntimeReleaseOfflineRestore(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const restore = value as Record<string, unknown>;
  if (
    typeof restore.status !== "string" ||
    typeof restore.runtimeMutation !== "boolean" ||
    typeof restore.sourceReleaseId !== "string" ||
    typeof restore.targetHospitalId !== "string" ||
    typeof restore.fileDigest !== "string" ||
    typeof restore.manifestSha256 !== "string" ||
    typeof restore.itemCount !== "number" ||
    typeof restore.restoredReleaseId !== "string" ||
    typeof restore.restoredRevisionNo !== "number"
  ) {
    return null;
  }
  return {
    status: restore.status,
    runtimeMutation: restore.runtimeMutation,
    sourceReleaseId: restore.sourceReleaseId,
    targetHospitalId: restore.targetHospitalId,
    fileDigest: restore.fileDigest,
    manifestSha256: restore.manifestSha256,
    itemCount: restore.itemCount,
    restoredReleaseId: restore.restoredReleaseId,
    restoredRevisionNo: restore.restoredRevisionNo,
  };
}

function parseRuntimeReleaseOfflineRuntimeSnapshot(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const snapshot = value as Record<string, unknown>;
  if (
    typeof snapshot.releaseId !== "string" ||
    typeof snapshot.revisionNo !== "number" ||
    typeof snapshot.manifestSha256 !== "string"
  ) {
    return null;
  }
  return {
    releaseId: snapshot.releaseId,
    revisionNo: snapshot.revisionNo,
    manifestSha256: snapshot.manifestSha256,
  };
}

function parseRuntimeReleaseOfflineRuntimeSnapshotWithSelection(value: unknown) {
  const snapshot = parseRuntimeReleaseOfflineRuntimeSnapshot(value);
  if (!snapshot || !value || typeof value !== "object" || Array.isArray(value)) return null;
  const record = value as Record<string, unknown>;
  if (typeof record.selectedCandidatePresent !== "boolean") return null;
  return {
    ...snapshot,
    selectedCandidatePresent: record.selectedCandidatePresent,
  };
}

function parseRuntimeReleaseHospitalEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const hospital = value as Record<string, unknown>;
  const selectedCandidate = parseRuntimeReleaseCandidate(hospital.selectedCandidate);
  if (
    typeof hospital.hospitalId !== "string" ||
    hospital.hospitalId.length === 0 ||
    typeof hospital.hospitalName !== "string" ||
    hospital.hospitalName.length === 0 ||
    !selectedCandidate
  ) {
    return null;
  }
  return {
    hospitalId: hospital.hospitalId,
    hospitalName: hospital.hospitalName,
    selectedCandidate,
    activationReadback: hospital.activationReadback,
    runtimeConsumerReadback: hospital.runtimeConsumerReadback,
    excludesOtherHospitalCandidate: hospital.excludesOtherHospitalCandidate,
  };
}

function hasCompleteRuntimeReleasePartialSelectionEvidence(value: {
  apiEvidence?: unknown;
  localCandidate?: unknown;
  unselectedLocalCandidate?: unknown;
  activationRequest?: unknown;
  activationReadback?: unknown;
  runtimeConsumerReadback?: unknown;
  partialSelection?: unknown;
}) {
  if (
    !value.apiEvidence ||
    typeof value.apiEvidence !== "object" ||
    Array.isArray(value.apiEvidence)
  ) {
    return false;
  }
  if ((value.apiEvidence as Record<string, unknown>).partialSelectionProved !== true) {
    return false;
  }
  const selected = parseRuntimeReleaseCandidate(value.localCandidate);
  const unselected = parseRuntimeReleaseCandidate(value.unselectedLocalCandidate);
  if (!selected || !unselected || runtimeReleaseSameCandidate(selected, unselected)) return false;
  if (
    !value.partialSelection ||
    typeof value.partialSelection !== "object" ||
    Array.isArray(value.partialSelection)
  ) {
    return false;
  }
  const partial = value.partialSelection as Record<string, unknown>;
  return (
    runtimeReleaseCandidateEquals(partial.selectedCandidate, selected) &&
    runtimeReleaseCandidateEquals(partial.unselectedCandidate, unselected) &&
    partial.activationRequestOmitsUnselected === true &&
    partial.activationReadbackOmitsUnselected === true &&
    partial.runtimeConsumerOmitsUnselected === true &&
    !runtimeReleasePayloadContainsIdentity(value.activationRequest, "activeAssets", unselected) &&
    !runtimeReleasePayloadContainsIdentity(value.activationReadback, "assets", unselected) &&
    !runtimeReleasePayloadContainsIdentity(value.runtimeConsumerReadback, "assets", unselected)
  );
}

function parseRuntimeReleaseCandidate(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const candidate = value as Record<string, unknown>;
  if (
    typeof candidate.assetType !== "string" ||
    candidate.assetType.length === 0 ||
    typeof candidate.assetIdentity !== "string" ||
    candidate.assetIdentity.length === 0 ||
    typeof candidate.versionId !== "string" ||
    candidate.versionId.length === 0
  ) {
    return null;
  }
  return {
    assetType: candidate.assetType,
    assetIdentity: candidate.assetIdentity,
    versionId: candidate.versionId,
  };
}

function runtimeReleaseSameCandidate(
  left: { assetType: string; assetIdentity: string; versionId: string },
  right: { assetType: string; assetIdentity: string; versionId: string },
) {
  return (
    left.assetType === right.assetType &&
    left.assetIdentity === right.assetIdentity &&
    left.versionId === right.versionId
  );
}

function runtimeReleaseCandidateEquals(
  value: unknown,
  candidate: { assetType: string; assetIdentity: string; versionId: string },
) {
  const parsed = parseRuntimeReleaseCandidate(value);
  return Boolean(parsed && runtimeReleaseSameCandidate(parsed, candidate));
}

function runtimeReleasePayloadContainsCandidate(
  value: unknown,
  assetField: "activeAssets" | "assets",
  candidate: { assetType: string; assetIdentity: string; versionId: string },
  options: { requireActive?: boolean } = {},
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const assets = (value as Record<string, unknown>)[assetField];
  if (!Array.isArray(assets)) return false;
  return assets.some((item) => runtimeReleaseAssetMatchesCandidate(item, candidate, options));
}

function runtimeReleasePayloadContainsPlatformSelection(
  value: unknown,
  assetField: "activeAssets" | "assets",
  candidate: { assetType: string; assetIdentity: string },
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const assets = (value as Record<string, unknown>)[assetField];
  if (!Array.isArray(assets)) return false;
  return assets.some((item) => {
    if (!runtimeReleaseAssetMatchesIdentity(item, candidate)) return false;
    const asset = item as Record<string, unknown>;
    return asset.versionId == null || asset.versionId === "";
  });
}

function runtimeReleasePayloadContainsIdentity(
  value: unknown,
  assetField: "activeAssets" | "assets",
  candidate: { assetType: string; assetIdentity: string },
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const assets = (value as Record<string, unknown>)[assetField];
  if (!Array.isArray(assets)) return false;
  return assets.some((item) => runtimeReleaseAssetMatchesIdentity(item, candidate));
}

function runtimeReleasePayloadExcludesCandidate(
  value: unknown,
  candidate: { assetType: string; assetIdentity: string; versionId: string },
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const payload = value as Record<string, unknown>;
  if (payload.localCandidateAbsent !== true || !Array.isArray(payload.assets)) return false;
  return !payload.assets.some((item) => runtimeReleaseAssetMatchesIdentity(item, candidate));
}

function parseRollbackNegativeEvidence(value: unknown) {
  const evidence = recordValue(value);
  if (
    !evidence ||
    evidence.rollbackPosted !== true ||
    evidence.currentRuntimeReadbackVerified !== true ||
    evidence.runtimeConsumerReadbackVerified !== true ||
    evidence.consumerProbeMatchedRemovedAssets !== false ||
    !hasText(evidence.consumer) ||
    !Array.isArray(evidence.removedAssets)
  ) {
    return [];
  }
  const removedAssets = evidence.removedAssets
    .map((item) => parseRuntimeReleaseCandidate(item))
    .filter(
      (candidate): candidate is { assetType: string; assetIdentity: string; versionId: string } =>
        candidate !== null,
    );
  if (removedAssets.length === 0) return [];
  const currentRuntime = recordValue(evidence.currentRuntime);
  const runtimeConsumer = recordValue(evidence.runtimeConsumer);
  if (
    !runtimeReadbackExcludesCandidates(currentRuntime, removedAssets) ||
    !runtimeReadbackExcludesCandidates(runtimeConsumer, removedAssets)
  ) {
    return [];
  }
  return Array.from(new Set(removedAssets.map((asset) => asset.assetType)));
}

function runtimeReadbackExcludesCandidates(
  value: Record<string, unknown> | null,
  candidates: Array<{ assetType: string; assetIdentity: string; versionId: string }>,
) {
  if (
    !value ||
    !hasText(value.releaseId) ||
    typeof value.revisionNo !== "number" ||
    value.revisionNo < 1 ||
    !isSha256(value.manifestSha256) ||
    !Array.isArray(value.assets)
  ) {
    return false;
  }
  return candidates.every(
    (candidate) => !runtimeReleasePayloadContainsCandidate(value, "assets", candidate),
  );
}

function runtimeReleaseAssetMatchesCandidate(
  value: unknown,
  candidate: { assetType: string; assetIdentity: string; versionId: string },
  options: { requireActive?: boolean } = {},
) {
  if (!runtimeReleaseAssetMatchesIdentity(value, candidate)) return false;
  const asset = value as Record<string, unknown>;
  return (
    asset.versionId === candidate.versionId &&
    (!options.requireActive || asset.entryState === "ACTIVE")
  );
}

function runtimeReleaseAssetMatchesIdentity(
  value: unknown,
  candidate: { assetType: string; assetIdentity: string },
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const asset = value as Record<string, unknown>;
  return asset.assetType === candidate.assetType && asset.assetIdentity === candidate.assetIdentity;
}

function hasRequiredSourceLineageAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "source-lineage-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      semanticFamilies?: unknown;
      apiEvidence?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredSourceLineageScenarioCodes) ||
      !arrayEquals(parsed.semanticFamilies, ["SOURCE_VALIDITY"]) ||
      !hasCompleteSourceLineageApiEvidence(parsed.apiEvidence) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredSourceLineageScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredSourceLineageScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredEmbedBusinessHostAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "embed-business-host-launch-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      deliveryShapes?: unknown;
      apiEvidence?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredEmbedBusinessHostScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["DELIVERY_FEEDBACK"]) ||
      !arrayEquals(parsed.deliveryShapes, ["EMBEDDED_COMPONENT"]) ||
      !hasCompleteEmbedApiEvidence(parsed.apiEvidence) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredEmbedBusinessHostScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredEmbedBusinessHostScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredPathwayLifecycleAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "pathway-lifecycle-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      versionedAssets?: unknown;
      serviceCombinations?: unknown;
      specialDiseaseStages?: unknown;
      apiEvidence?: unknown;
      orderSetRuntimeConsumer?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredPathwayLifecycleScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION"]) ||
      !arrayEquals(parsed.versionedAssets, ["PATHWAY", "ORDER_SET"]) ||
      !arrayEquals(parsed.serviceCombinations, ["SPECIAL_DISEASE_PATHWAY"]) ||
      !arrayEquals(parsed.specialDiseaseStages, requiredPathwayMilestoneStages) ||
      !hasCompletePathwayLifecycleApiEvidence(parsed.apiEvidence) ||
      !hasCompletePathwayOrderSetRuntimeConsumerEvidence(parsed.orderSetRuntimeConsumer) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredPathwayLifecycleScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredPathwayLifecycleScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredSystemProvidersAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "system-providers-operations-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      deliveryShapes?: unknown;
      serviceCombinations?: unknown;
      apiEvidence?: unknown;
      snapshot?: unknown;
      backup?: unknown;
      dependencyEvidence?: unknown;
      accessEvidence?: unknown;
      runtimeContinuityEvidence?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.deliveryShapes, ["MANAGEMENT_WORKSPACE"]) ||
      !arrayEquals(parsed.serviceCombinations, ["COMPLIANCE_OPERATIONS"]) ||
      !hasCompleteSystemProvidersApiEvidence(parsed.apiEvidence) ||
      !hasCompleteSystemProvidersSnapshot(parsed.snapshot) ||
      !hasCompleteSystemProvidersBackupEvidence(parsed.backup) ||
      !hasCompleteSystemProvidersDependencyEvidence(parsed.dependencyEvidence) ||
      !hasCompleteSystemProvidersAccessEvidence(parsed.accessEvidence) ||
      !hasCompleteSystemProvidersRuntimeContinuityEvidence(parsed.runtimeContinuityEvidence) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const observedStages = parsed.scenarioEvidence.flatMap((item) => {
      if (!item || typeof item !== "object") return [];
      const stages = (item as { observedStages?: unknown }).observedStages;
      return Array.isArray(stages)
        ? stages.filter((stage): stage is string => typeof stage === "string")
        : [];
    });
    return requiredSystemProvidersScenarioEvidence.every((stage) => observedStages.includes(stage));
  } catch {
    return false;
  }
}

function hasRequiredIdentityBindingAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "identity-binding-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      serviceCombinations?: unknown;
      apiEvidence?: unknown;
      createdPersonnel?: unknown;
      binding?: unknown;
      plaintextSafety?: unknown;
      unbinding?: unknown;
      cleanup?: unknown;
      scenarioEvidence?: unknown;
    };
    const binding = parseIdentityBindingEvidence(parsed.binding);
    if (
      !arrayEquals(parsed.scenarioCodes, requiredIdentityBindingScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["FOUNDATION_GOVERNANCE"]) ||
      !arrayEquals(parsed.serviceCombinations, ["COMPLIANCE_OPERATIONS"]) ||
      !hasCompleteIdentityBindingApiEvidence(parsed.apiEvidence) ||
      !hasCompleteIdentityBindingCreatedPersonnel(parsed.createdPersonnel, binding?.userId) ||
      !binding ||
      !hasCompleteIdentityPlaintextSafetyEvidence(parsed.plaintextSafety) ||
      !hasCompleteIdentityUnbindingEvidence(parsed.unbinding, binding) ||
      !hasCompleteIdentityCleanupEvidence(parsed.cleanup) ||
      !Array.isArray(parsed.scenarioEvidence)
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredIdentityBindingScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredIdentityBindingScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function parseIdentityBindingEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const binding = value as Record<string, unknown>;
  if (
    !hasText(binding.bindingId) ||
    !hasText(binding.userId) ||
    binding.providerType !== "EMPLOYEE_NO" ||
    binding.status !== "ACTIVE" ||
    !hasText(binding.subjectHint) ||
    !String(binding.subjectHint).startsWith("****") ||
    typeof binding.version !== "number" ||
    binding.version < 1
  ) {
    return null;
  }
  return {
    bindingId: binding.bindingId,
    userId: binding.userId,
    version: binding.version,
  };
}

function hasCompleteRuntimeReleaseApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "impactSimulationRun",
    "activationPosted",
    "activationRequestCarriesRequiredAssets",
    "currentReleaseReadback",
    "runtimeConsumerReadback",
    "rollbackPosted",
    "rollbackCurrentReleaseReadback",
    "rollbackRuntimeConsumerReadback",
  ].every((key) => evidence[key] === true);
}

function hasCompleteIdentityBindingApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "personnelCreated",
    "bindingPosted",
    "bindingListRead",
    "plaintextNotPersisted",
    "duplicateRejected",
    "unbindPosted",
    "cleanupCompleted",
  ].every((key) => evidence[key] === true);
}

function hasCompleteIdentityBindingCreatedPersonnel(value: unknown, boundUserId?: unknown) {
  if (!Array.isArray(value) || !hasText(boundUserId)) return false;
  const expectedBoundUserId = String(boundUserId);
  const personnel = value.filter((item): item is Record<string, unknown> =>
    Boolean(item && typeof item === "object" && !Array.isArray(item)),
  );
  if (personnel.length < 2) return false;
  const userIds = new Set<string>();
  for (const item of personnel) {
    if (!hasText(item.userId) || !hasText(item.username) || !hasText(item.displayName)) {
      return false;
    }
    userIds.add(String(item.userId));
  }
  return userIds.size >= 2 && userIds.has(expectedBoundUserId);
}

function hasCompleteIdentityPlaintextSafetyEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const safety = value as Record<string, unknown>;
  return (
    safety.subjectHintIncludesTail === true &&
    safety.listOmitsExternalSubjectDigest === true &&
    safety.listOmitsExternalSubjectPlaintext === true &&
    safety.duplicateStatus === 409 &&
    typeof safety.duplicateRejectedMessage === "string" &&
    safety.duplicateRejectedMessage.includes("该外部身份已绑定其他用户")
  );
}

function hasCompleteIdentityUnbindingEvidence(
  value: unknown,
  binding: { bindingId: unknown; version: number },
) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const unbinding = value as Record<string, unknown>;
  return (
    unbinding.bindingId === binding.bindingId &&
    unbinding.status === "UNBOUND" &&
    unbinding.versionAdvanced === true
  );
}

function hasCompleteIdentityCleanupEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const cleanup = value as Record<string, unknown>;
  return (
    cleanup.createdAccountDisabled === true &&
    cleanup.duplicateAccountDisabled === true &&
    cleanup.bindingUnboundOrAlreadyUnbound === true
  );
}

function hasCompleteSystemProvidersApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "operationsSnapshotRead",
    "backupReadinessObserved",
    "honestDegradationObserved",
    "evidenceDetailsObserved",
    "runtimeReadbackObserved",
    "runtimeConsumerReadbackObserved",
    "clinicalSmokeAfterRestore",
    "clinicalForbidden",
  ].every((key) => evidence[key] === true);
}

function hasCompleteSystemProvidersSnapshot(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const snapshot = value as Record<string, unknown>;
  return (
    hasText(snapshot.healthStatus) &&
    hasText(snapshot.databaseDialect) &&
    hasText(snapshot.migrationLocation) &&
    Array.isArray(snapshot.activeProfiles) &&
    snapshot.activeProfiles.every((item) => typeof item === "string")
  );
}

function hasCompleteSystemProvidersBackupEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const backup = value as Record<string, unknown>;
  const drillEvidence = backup.drillEvidence;
  if (!drillEvidence || typeof drillEvidence !== "object" || Array.isArray(drillEvidence)) {
    return false;
  }
  const drill = drillEvidence as Record<string, unknown>;
  return (
    hasText(backup.rpo) &&
    hasText(backup.rto) &&
    typeof backup.checksumPolicy === "string" &&
    backup.checksumPolicy.includes("SHA-256") &&
    typeof backup.backupScript === "string" &&
    backup.backupScript.includes("backup.sh") &&
    typeof backup.restoreScript === "string" &&
    backup.restoreScript.includes("restore.sh") &&
    drill.status === "SUCCESS" &&
    typeof drill.migrationCount === "number" &&
    drill.migrationCount > 0 &&
    hasText(drill.evidenceReference) &&
    hasText(drill.checksumEvidence) &&
    drill.drillDatabaseIsIsolated === true &&
    hasText(drill.rpo) &&
    hasText(drill.rto)
  );
}

function hasSystemProvidersMissingBackupDrillEvidence(value: unknown) {
  const backup = recordValue(value);
  const drill = recordValue(backup?.drillEvidence);
  return (
    backup !== null &&
    drill !== null &&
    hasText(backup.rpo) &&
    hasText(backup.rto) &&
    typeof backup.checksumPolicy === "string" &&
    backup.checksumPolicy.includes("SHA-256") &&
    typeof backup.backupScript === "string" &&
    backup.backupScript.includes("backup.sh") &&
    typeof backup.restoreScript === "string" &&
    backup.restoreScript.includes("restore.sh") &&
    drill.status === "NOT_AVAILABLE" &&
    drill.completedAt == null &&
    drill.migrationCount == null &&
    drill.evidenceReference == null &&
    drill.checksumEvidence == null &&
    drill.drillDatabaseIsIsolated == null &&
    drill.rpo == null &&
    drill.rto == null &&
    hasText(drill.detail) &&
    String(drill.detail).includes("尚未提供隔离恢复演练证据")
  );
}

function hasCompleteSystemProvidersDependencyEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  if (!Array.isArray(evidence.dependencies)) return false;
  const dependencies = evidence.dependencies.filter((item): item is Record<string, unknown> =>
    Boolean(item && typeof item === "object" && !Array.isArray(item)),
  );
  const hasBackup = dependencies.some(
    (item) =>
      item.key === "backup-restore" &&
      item.displayName === "备份恢复" &&
      typeof item.status === "string" &&
      item.status.length > 0,
  );
  const hasHonestDegradation = dependencies.some(
    (item) =>
      typeof item.key === "string" &&
      ["graph-projection", "search-projection", "external-provider"].includes(item.key) &&
      item.status !== "UP",
  );
  return (
    hasBackup &&
    hasHonestDegradation &&
    typeof evidence.honestDegradationText === "string" &&
    evidence.honestDegradationText.includes("核心业务继续走本地确定性主链路")
  );
}

function hasCompleteSystemProvidersAccessEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return (
    typeof evidence.platformAdminOperationsStatus === "number" &&
    evidence.platformAdminOperationsStatus >= 200 &&
    evidence.platformAdminOperationsStatus < 300 &&
    evidence.clinicalOperationsStatus === 403 &&
    evidence.clinicalPageForbidden === true &&
    evidence.clinicalPageNoOperationsData === true
  );
}

function hasCompleteSystemProvidersRuntimeContinuityEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  const currentRuntime = parseSystemProvidersRuntimeIdentity(evidence.currentRuntime);
  const runtimeConsumer = parseSystemProvidersRuntimeIdentity(evidence.runtimeConsumer);
  const clinicalSmoke = parseSystemProvidersClinicalSmokeEvidence(evidence.clinicalSmoke);
  return (
    Boolean(currentRuntime) &&
    Boolean(runtimeConsumer) &&
    Boolean(clinicalSmoke) &&
    runtimeConsumer?.contractVersion === "v1" &&
    runtimeConsumer?.releaseId === currentRuntime?.releaseId &&
    runtimeConsumer?.revisionNo === currentRuntime?.revisionNo &&
    runtimeConsumer?.manifestSha256 === currentRuntime?.manifestSha256 &&
    runtimeConsumer?.assetCount === currentRuntime?.assetCount &&
    clinicalSmoke?.runtimeReleaseId === currentRuntime?.releaseId
  );
}

function parseSystemProvidersRuntimeIdentity(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const runtime = value as Record<string, unknown>;
  const contractVersion =
    typeof runtime.contractVersion === "string" ? runtime.contractVersion : undefined;
  if (
    !hasText(runtime.releaseId) ||
    typeof runtime.revisionNo !== "number" ||
    runtime.revisionNo <= 0 ||
    !isSha256(runtime.manifestSha256) ||
    typeof runtime.assetCount !== "number" ||
    runtime.assetCount <= 0
  ) {
    return null;
  }
  return {
    ...(contractVersion ? { contractVersion } : {}),
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    assetCount: runtime.assetCount,
  };
}

function parseSystemProvidersClinicalSmokeEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const smoke = value as Record<string, unknown>;
  if (
    smoke.role !== "clinical-user" ||
    smoke.page !== "/mpi" ||
    !hasText(smoke.patientId) ||
    !hasText(smoke.contextSnapshotId) ||
    !hasText(smoke.runtimeReleaseId)
  ) {
    return null;
  }
  return {
    role: "clinical-user",
    page: "/mpi",
    patientId: String(smoke.patientId),
    contextSnapshotId: String(smoke.contextSnapshotId),
    runtimeReleaseId: String(smoke.runtimeReleaseId),
  };
}

function hasCompleteEmbedApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "launchTokenIssued",
    "launchExchanged",
    "recommendationsRead",
    "feedbackSubmitted",
    "hostMessageReceived",
  ].every((key) => evidence[key] === true);
}

function hasCompleteEmbedBusinessHostDegradationEvidence(parsed: Record<string, unknown>) {
  if (!hasCompleteEmbedApiEvidence(parsed.apiEvidence)) return false;
  const apiResponses = Array.isArray(parsed.apiResponses) ? parsed.apiResponses : [];
  const clinicalContext = recordValue(parsed.clinicalContext);
  const launchToken = recordValue(parsed.launchToken);
  const recommendation = recordValue(parsed.recommendation);
  const feedback = recordValue(parsed.feedback);
  const hostMessage = recordValue(parsed.hostMessage);
  const runtimeSafety = recordValue(parsed.runtimeSafety);
  if (!clinicalContext || !launchToken || !recommendation || !feedback || !hostMessage) {
    return false;
  }

  const patientId = textValue(clinicalContext.patientId);
  const encounterId = textValue(clinicalContext.encounterId);
  const cardId = textValue(recommendation.cardId);
  const feedbackCardId = textValue(feedback.cardId);
  if (!patientId || !encounterId || !cardId || feedbackCardId !== cardId) return false;

  const requiredResponses = [
    "POST /medkernel/api/v1/engine/embed/launch 200",
    "POST /medkernel/api/v1/engine/embed/recommendations 200",
    "POST /medkernel/api/v1/engine/embed/feedback 200",
  ];
  if (
    !requiredResponses.every((expected) =>
      apiResponses.some((item) => typeof item === "string" && item.includes(expected)),
    )
  ) {
    return false;
  }

  const runtimeErrorsEmpty =
    runtimeSafety === null ||
    (Array.isArray(runtimeSafety.browserErrors) &&
      runtimeSafety.browserErrors.length === 0 &&
      Array.isArray(runtimeSafety.serverErrors) &&
      runtimeSafety.serverErrors.length === 0 &&
      Array.isArray(runtimeSafety.networkFailures) &&
      runtimeSafety.networkFailures.length === 0);

  return (
    clinicalContext.triggerPoint === "patient-view" &&
    launchToken.operation === "ISSUE_AND_EXCHANGE" &&
    is2xxStatus(launchToken.status) &&
    launchToken.integrationMode === "IFRAME" &&
    launchToken.hook === "patient-view" &&
    hasText(launchToken.hookInstance) &&
    launchToken.embedUrlIncludesLaunchToken === true &&
    hasText(launchToken.parentOrigin) &&
    recommendation.operation === "READ_EMBEDDED_RECOMMENDATIONS" &&
    is2xxStatus(recommendation.status) &&
    recommendation.title === "检验危急值需人工确认" &&
    hasText(recommendation.traceId) &&
    recommendation.visibleCardCount === 1 &&
    recommendation.suppressedCardCount === 0 &&
    String(recommendation.sourceSummary ?? "").includes("嵌入宿主真实服务链路演练") &&
    feedback.operation === "SUBMIT_DOCTOR_FEEDBACK" &&
    is2xxStatus(feedback.status) &&
    feedback.actionType === "ADOPT" &&
    feedback.recommendationStatus === "ACCEPTED" &&
    feedback.callbackStatus === "NOT_CONNECTED" &&
    feedback.callbackDelivered === false &&
    hasText(feedback.degradationReason) &&
    hasText(feedback.traceId) &&
    hostMessage.received === true &&
    hostMessage.actionType === "ADOPT" &&
    hostMessage.cardId === cardId &&
    hostMessage.patientId === patientId &&
    hostMessage.encounterId === encounterId &&
    runtimeErrorsEmpty
  );
}

function hasCompleteSourceLineageApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "sourceRegistered",
    "sourceVersionRegistered",
    "sourceFragmentRegistered",
    "knowledgeCandidateSubmitted",
    "citationBound",
    "candidateApproved",
    "graphProjectionRebuilt",
    "provenanceReadback",
    "graphNodeExplored",
    "traceEvidenceVisible",
  ].every((key) => evidence[key] === true);
}

function hasCompleteSourceLineageStructuredEvidence(parsed: Record<string, unknown>) {
  if (!hasCompleteSourceLineageApiEvidence(parsed.apiEvidence)) return false;
  const source = recordValue(parsed.source);
  const candidate = recordValue(parsed.knowledgeCandidate);
  const citation = recordValue(parsed.citation);
  const provenance = recordValue(parsed.provenanceReadback);
  const graph = recordValue(parsed.graphProjection);
  if (!source || !candidate || !citation || !provenance || !graph) return false;

  const sourceDocumentId = positiveNumber(source.sourceDocumentId);
  const sourceVersionId = positiveNumber(source.sourceVersionId);
  const sourceFragmentId = positiveNumber(source.sourceFragmentId);
  const identityId = positiveNumber(candidate.identityId);
  const versionId = positiveNumber(candidate.versionId);
  const citationId = positiveNumber(citation.citationId);
  if (
    !sourceDocumentId ||
    !sourceVersionId ||
    !sourceFragmentId ||
    !identityId ||
    !versionId ||
    !citationId
  ) {
    return false;
  }

  const sourceCode = textValue(source.sourceCode);
  const sourceVersionNo = textValue(source.sourceVersionNo);
  const sourceVersionHash = textValue(source.sourceVersionHash);
  const fragmentHash = textValue(source.fragmentHash);
  const anchorPath = textValue(source.anchorPath);
  const anchorLabel = textValue(source.anchorLabel);
  const identityCode = textValue(candidate.identityCode);
  const candidateRef = textValue(candidate.candidateRef);
  const jobCode = textValue(candidate.jobCode);
  if (
    !sourceCode ||
    !sourceVersionNo ||
    !isSha256(sourceVersionHash) ||
    !isSha256(fragmentHash) ||
    !anchorPath ||
    !anchorLabel ||
    !identityCode ||
    !candidateRef ||
    !jobCode ||
    source.contentHashVerified !== true ||
    source.fragmentHashVerified !== true ||
    candidate.operation !== "GENERATE_REVIEW_APPROVE" ||
    candidate.status !== "ACTIVE" ||
    !positiveNumber(candidate.classificationId) ||
    !positiveNumber(candidate.qualityGateRecordId)
  ) {
    return false;
  }

  if (
    citation.relation !== "DERIVED_FROM" ||
    citation.weight !== 100 ||
    citation.startOffset !== 0 ||
    !positiveNumber(citation.endOffset) ||
    citation.sourceFragmentId !== sourceFragmentId ||
    citation.assetVersionId !== versionId
  ) {
    return false;
  }

  if (
    provenance.identityId !== identityId ||
    provenance.identityCode !== identityCode ||
    provenance.currentVersionId !== versionId ||
    provenance.activeVersionStatus !== "ACTIVE" ||
    provenance.partial !== false ||
    provenance.unresolvedCitationCount !== 0 ||
    provenance.citationId !== citationId ||
    provenance.sourceFragmentId !== sourceFragmentId ||
    provenance.sourceDocumentId !== sourceDocumentId ||
    provenance.sourceVersionId !== sourceVersionId ||
    provenance.sourceCode !== sourceCode ||
    provenance.sourceType !== "GUIDELINE" ||
    provenance.authorityLevel !== "B_GUIDELINE" ||
    !hasText(provenance.authorityLabel) ||
    provenance.sourceVersionNo !== sourceVersionNo ||
    provenance.sourceVersionHash !== sourceVersionHash ||
    provenance.anchorPath !== anchorPath ||
    provenance.anchorLabel !== anchorLabel ||
    provenance.fragmentHash !== fragmentHash ||
    provenance.relation !== "DERIVED_FROM" ||
    provenance.weight !== 100
  ) {
    return false;
  }

  return (
    graph.operation === "REBUILD_AND_EXPLORE" &&
    positiveNumber(graph.sourceCount) !== null &&
    positiveNumber(graph.projectionCount) !== null &&
    graph.projectionMatchesSourceCount === true &&
    graph.graphNodeExplored === true &&
    graph.traceEvidenceVisible === true &&
    Array.isArray(graph.browserErrors) &&
    graph.browserErrors.length === 0
  );
}

function hasCompleteFollowupS12NormalEvidence(parsed: Record<string, unknown>) {
  const clinicalContext = recordValue(parsed.clinicalContext);
  const template = recordValue(parsed.followupTemplate);
  const runtime = recordValue(parsed.followupRuntime);
  const plan = recordValue(parsed.followupPlan);
  const questionnaire = recordValue(parsed.questionnaire);
  const abnormal = recordValue(parsed.abnormalReturn);
  if (!clinicalContext || !template || !runtime || !plan || !questionnaire || !abnormal) {
    return false;
  }

  const patientId = textValue(clinicalContext.patientId);
  const encounterId = textValue(clinicalContext.encounterId);
  const contextSnapshotId = textValue(clinicalContext.contextSnapshotId);
  const templateId = textValue(template.templateId);
  const templateCode = textValue(template.templateCode);
  const runtimeVersionId = textValue(runtime.versionId);
  const runtimeReleaseId = textValue(runtime.runtimeReleaseId);
  const planId = textValue(plan.planId);
  if (
    !patientId ||
    !encounterId ||
    !contextSnapshotId ||
    !templateId ||
    !templateCode ||
    !runtimeVersionId ||
    !runtimeReleaseId ||
    !planId
  ) {
    return false;
  }

  return (
    template.operation === "CREATE_AND_PUBLISH_FOLLOWUP_TEMPLATE" &&
    is2xxStatus(template.createStatus) &&
    is2xxStatus(template.publishStatus) &&
    template.assetStatus === "PUBLISHED" &&
    template.scope === "HOSPITAL" &&
    runtime.operation === "ACTIVATE_HOSPITAL_RUNTIME_WITH_FOLLOWUP" &&
    is2xxStatus(runtime.candidateStatus) &&
    is2xxStatus(runtime.activationStatus) &&
    is2xxStatus(runtime.runtimeReadbackStatus) &&
    runtime.assetType === "FOLLOWUP" &&
    runtime.assetIdentity === templateCode &&
    runtime.sourceLayer === "HOSPITAL" &&
    runtime.entryState === "ACTIVE" &&
    runtime.currentRuntimeContainsAsset === true &&
    plan.operation === "GENERATE_FOLLOWUP_PLAN_FROM_CONTEXT" &&
    is2xxStatus(plan.status) &&
    plan.templateId === templateId &&
    plan.templateCode === templateCode &&
    plan.runtimeReleaseId === runtimeReleaseId &&
    plan.patientId === patientId &&
    plan.encounterId === encounterId &&
    plan.contextSnapshotId === contextSnapshotId &&
    positiveNumber(plan.taskCount) !== null &&
    questionnaire.operation === "SUBMIT_FOLLOWUP_QUESTIONNAIRE" &&
    is2xxStatus(questionnaire.status) &&
    questionnaire.planId === planId &&
    questionnaire.patientId === patientId &&
    questionnaire.source === "PATIENT_SELF_REPORT" &&
    questionnaire.submitted === true &&
    hasText(questionnaire.questionnaireId) &&
    hasText(questionnaire.taskId) &&
    abnormal.operation === "REGISTER_ABNORMAL_RETURN" &&
    is2xxStatus(abnormal.status) &&
    abnormal.planId === planId &&
    abnormal.patientId === patientId &&
    abnormal.riskLevel === "HIGH" &&
    abnormal.registered === true &&
    abnormal.noAutoOrder === true &&
    hasText(abnormal.eventId) &&
    hasText(abnormal.returnTaskId) &&
    hasText(abnormal.notificationEventId)
  );
}

function hasCompleteFollowupS12AbnormalEvidence(parsed: Record<string, unknown>) {
  return hasCompleteFollowupS12NormalEvidence(parsed);
}

function hasCompletePathwayLifecycleApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "templateSaved",
    "templateReadback",
    "draftPreviewRun",
    "templateSimulated",
    "entryCandidatesRead",
    "patientEntered",
    "standardAdvanced",
    "orderSetRuntimeConsumed",
    "varianceRecorded",
    "followupHandoffCreated",
    "clocksRead",
    "variancesRead",
    "followupHandoffObserved",
  ].every((key) => evidence[key] === true);
}

function hasCompletePathwayOrderSetRuntimeConsumerEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  const asset = recordValue(evidence.asset);
  const runtimeRelease = recordValue(evidence.runtimeRelease);
  const patientPathway = recordValue(evidence.patientPathway);
  const advanceResponse = recordValue(evidence.advanceResponse);
  const decisionEvidence = recordValue(advanceResponse?.decisionEvidence);
  const runtimeAssets = Array.isArray(runtimeRelease?.assets) ? runtimeRelease.assets : [];
  const orderSetItems = Array.isArray(decisionEvidence?.["pathway.orderSetItems"])
    ? decisionEvidence["pathway.orderSetItems"]
    : [];
  const assetIdentity = textValue(asset?.assetIdentity);
  const versionId = textValue(asset?.versionId);
  const versionNo = textValue(asset?.versionNo);
  const contentHash = textValue(asset?.contentHash);
  const runtimeReleaseId = textValue(runtimeRelease?.releaseId);
  return (
    asset?.assetType === "ORDER_SET" &&
    hasText(assetIdentity) &&
    hasText(versionId) &&
    hasText(versionNo) &&
    isSha256(contentHash) &&
    hasText(runtimeReleaseId) &&
    runtimeRelease?.assetPresent === true &&
    runtimeAssets.some((item) =>
      runtimeReleaseAssetMatchesCandidate(item, {
        assetType: "ORDER_SET",
        assetIdentity: assetIdentity ?? "",
        versionId: versionId ?? "",
      }),
    ) &&
    textValue(patientPathway?.runtimeReleaseId) === runtimeReleaseId &&
    advanceResponse?.previousNodeCode === "ASSESS" &&
    advanceResponse?.nextNodeCode === "FOLLOWUP" &&
    advanceResponse?.status === "NODE_EXECUTING" &&
    decisionEvidence?.["pathway.currentNodeType"] === "ORDER_SET" &&
    decisionEvidence?.["pathway.orderSetRef"] === assetIdentity &&
    decisionEvidence?.["pathway.orderSetVersion"] === versionNo &&
    decisionEvidence?.["pathway.orderSetHash"] === contentHash &&
    decisionEvidence?.["pathway.orderSetRequiresPhysicianConfirmation"] === true &&
    typeof decisionEvidence?.["pathway.orderSetItemCount"] === "number" &&
    (decisionEvidence["pathway.orderSetItemCount"] as number) > 0 &&
    orderSetItems.length === decisionEvidence["pathway.orderSetItemCount"]
  );
}

function arrayEquals(value: unknown, expected: readonly string[]) {
  if (!Array.isArray(value)) return false;
  const observed = value.filter((item): item is string => typeof item === "string").sort();
  return JSON.stringify(observed) === JSON.stringify([...expected].sort());
}

function hasText(value: unknown) {
  return typeof value === "string" && value.trim().length > 0;
}

function hasNonEmptyTextArray(value: unknown) {
  return Array.isArray(value) && value.length > 0 && value.every((item) => hasText(item));
}

function is2xxStatus(value: unknown) {
  return typeof value === "number" && value >= 200 && value < 300;
}

function isPositiveNumber(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) && value > 0;
}

function positiveNumber(value: unknown) {
  return isPositiveNumber(value) ? value : null;
}

function textValue(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function isSha256(value: unknown) {
  return typeof value === "string" && /^[0-9a-f]{64}$/u.test(value);
}

function mergeClaims(
  target: Record<string, LaunchCoverageRow[]>,
  claims: string[],
  observedAt: string,
) {
  for (const claim of claims) {
    const [key, code] = claim.split(":");
    if (!key || !code) throw new Error(`无效浏览器覆盖声明 ${claim}`);
    target[key] ??= [];
    if (target[key].some((row) => row.code === code)) continue;
    target[key].push({
      code,
      status: "PASSED",
      evidenceKey: `launchCoverage.${key}.${code}`,
      observedAt,
    });
  }
}
