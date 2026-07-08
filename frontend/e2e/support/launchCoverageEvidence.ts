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
  requiresPharmacyReviewAntimicrobialFrontdeskAttachment?: boolean;
  requiresInfectionPublicHealthSafetyFrontdeskAttachment?: boolean;
  requiresSurgeryAnesthesiaTransfusionFrontdeskAttachment?: boolean;
  requiresCriticalEmergencyIcuFrontdeskAttachment?: boolean;
  requiresRuntimeReleasePartialSelectionAttachment?: boolean;
  requiresFourRoleCoreActionsAttachment?: boolean;
  requiresSixEntryCoreActionsAttachment?: boolean;
  requiresPlatformAdminEntryCoreActionsAttachment?: boolean;
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

const requiredVersionedAssetRollbackRepresentativeAssets = [
  "SAFETY",
  "CDSS_RISK",
  "VALUE_SET",
  "FORMULA",
  "PATHWAY",
  "ORDER_SET",
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
  "productLayers:QUALITY_IMPROVEMENT",
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

const infectionPublicHealthSafetyFrontdeskClaims = [
  "scenarios:S21",
  "scenarios:S32",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "productLayers:QUALITY_IMPROVEMENT",
  "versionedAssets:TERMINOLOGY",
  "versionedAssets:RULE",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:PROFESSIONAL_COLLABORATION",
  "serviceCombinations:QUALITY_IMPROVEMENT",
];

const surgeryAnesthesiaTransfusionFrontdeskClaims = [
  "scenarios:S26",
  "productLayers:CLINICAL_EXECUTION",
  "productLayers:DATA_INTEROPERABILITY",
  "productLayers:QUALITY_IMPROVEMENT",
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

const realFrontdeskScenarioClaims = ["scenarios:S10", "scenarios:S11", "scenarios:S12"];

const serviceOrganizationClaims = [
  "scenarios:S1",
  "scenarios:S14",
  "organizationLevels:HOSPITAL",
  "organizationLevels:DEPARTMENT",
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
const platformAdminEntryCoreActionsClaims = [
  "platformAdminEntryCoreActions:FOUR_PLATFORM_ADMIN_P0_ENTRY_ACTIONS",
];
const platformAdminP1EntryCoreActionsClaims = [
  "platformAdminP1EntryCoreActions:RUNTIME_DIAGNOSTICS_DOMESTIC_CHECK",
];
const clinicalEntryCoreActionsClaims = [
  "clinicalEntryCoreActions:CLINICAL_COLLABORATION_CORE_ACTIONS_REPRESENTATIVE",
];
const qualityManagementEntryCoreActionsClaims = [
  "qualityManagementEntryCoreActions:QUALITY_MANAGEMENT_CORE_ACTIONS_REPRESENTATIVE",
];
const knowledgeOperationsAssetEntryCoreActionsClaims = [
  "knowledgeOperationsAssetEntryCoreActions:KNOWLEDGE_OPERATIONS_ASSET_ENTRY_FAMILY_REPRESENTATIVE",
];

const requiredThirdPartySystemFamilyCodes = thirdPartySystemFamilyClaims
  .filter((claim) => claim.startsWith("thirdPartySystemFamilies:"))
  .map((claim) => claim.split(":")[1]);

const requiredS2S4RuntimeMappingScenarioCodes = ["S2", "S4"];
const requiredCdssDeclarativeRuntimeAssetScenarioCodes = ["S5"];
const requiredMedicationSafetyFrontdeskScenarioCodes = ["S5"];
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
    "前台创建医疗机构与科室",
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
    file: "infection-public-health-safety-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成院感公卫上报预填和医疗安全事件整改代表闭环",
    claims: infectionPublicHealthSafetyFrontdeskClaims,
    requiresInfectionPublicHealthSafetyFrontdeskAttachment: true,
  },
  {
    file: "surgery-anesthesia-transfusion-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成围手术期麻醉输血核查代表闭环",
    claims: surgeryAnesthesiaTransfusionFrontdeskClaims,
    requiresSurgeryAnesthesiaTransfusionFrontdeskAttachment: true,
  },
  {
    file: "critical-emergency-icu-frontdesk.spec.ts",
    titleIncludes: "临床用户与运营员、平台管理员完成急诊分诊与 ICU 生命支持风险代表闭环",
    claims: criticalEmergencyIcuFrontdeskClaims,
    requiresCriticalEmergencyIcuFrontdeskAttachment: true,
  },
  {
    file: "real-frontdesk-rehearsal.spec.ts",
    titleIncludes:
      "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
    claims: realFrontdeskScenarioClaims,
    requiresRealFrontdeskScenarioAttachment: true,
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
    claims: pathwayLifecycleClaims,
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
  if (hasRequiredClinicalEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(evidence.launchCoverage, clinicalEntryCoreActionsClaims, generatedAt);
  }
  if (hasRequiredQualityManagementEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(evidence.launchCoverage, qualityManagementEntryCoreActionsClaims, generatedAt);
  }
  if (hasRequiredKnowledgeOperationsAssetEntryCoreActionsAttachment(input.tests)) {
    mergeClaims(
      evidence.launchCoverage,
      knowledgeOperationsAssetEntryCoreActionsClaims,
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
      (!proof.requiresNursingContinuityFrontdeskAttachment ||
        hasRequiredNursingContinuityFrontdeskAttachment(test)) &&
      (!proof.requiresPharmacyReviewAntimicrobialFrontdeskAttachment ||
        hasRequiredPharmacyReviewAntimicrobialFrontdeskAttachment(test)) &&
      (!proof.requiresInfectionPublicHealthSafetyFrontdeskAttachment ||
        hasRequiredInfectionPublicHealthSafetyFrontdeskAttachment(test)) &&
      (!proof.requiresSurgeryAnesthesiaTransfusionFrontdeskAttachment ||
        hasRequiredSurgeryAnesthesiaTransfusionFrontdeskAttachment(test)) &&
      (!proof.requiresCriticalEmergencyIcuFrontdeskAttachment ||
        hasRequiredCriticalEmergencyIcuFrontdeskAttachment(test)) &&
      (!proof.requiresRuntimeReleasePartialSelectionAttachment ||
        hasRequiredRuntimeReleasePartialSelectionAttachment(test)) &&
      (!proof.requiresFourRoleCoreActionsAttachment ||
        hasRequiredFourRoleCoreActionsAttachment(test)) &&
      (!proof.requiresSixEntryCoreActionsAttachment ||
        hasRequiredSixEntryCoreActionsAttachment(test)) &&
      (!proof.requiresPlatformAdminEntryCoreActionsAttachment ||
        hasRequiredPlatformAdminEntryCoreActionsAttachment([test]))
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
  if (hasRequiredQualityManagementEntryCoreActionsAttachment(tests)) {
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

const requiredPlatformAdminEntryCoreActionPaths: Record<string, string> = {
  "tenant-onboarding": "/tenant/onboarding",
  "identity-bindings": "/security/identity-binding",
  "adapter-hub": "/adapter/hub",
  "system-providers": "/system/providers",
};

const requiredPlatformAdminEntryCoreActionMenuKeys = Object.keys(
  requiredPlatformAdminEntryCoreActionPaths,
);

const requiredPlatformAdminP1EntryCoreActionPaths: Record<string, string> = {
  "runtime-diagnostics": "/system/runtime-diagnostics",
  "domestic-check": "/advanced/domestic",
};

const requiredPlatformAdminP1EntryCoreActionMenuKeys = Object.keys(
  requiredPlatformAdminP1EntryCoreActionPaths,
);

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
  "qc-insurance": "/qc/insurance",
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
  "qc-insurance": [
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

function hasRequiredClinicalEntryCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
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
    }
  }
  return (
    sawAttachment &&
    requiredClinicalEntryCoreActionMenuKeys.every((menuKey) => actionsByMenuKey.has(menuKey))
  );
}

function hasRequiredQualityManagementEntryCoreActionsAttachment(tests: BrowserE2eTestResult[]) {
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
    if (path.basename(test.file) !== qualityManagementEntryCoreActionSpecFile) continue;
    for (const attachment of test.attachments) {
      if (attachment.name !== "quality-management-entry-core-actions-codes") continue;
      if (!attachment.body || attachment.contentType !== "application/json") return false;
      let parsed: unknown;
      try {
        parsed = JSON.parse(attachment.body);
      } catch {
        return false;
      }
      const body = recordValue(parsed);
      if (!body || body.matrixCode !== "QUALITY_MANAGEMENT_ENTRY_CORE_ACTIONS") continue;
      sawAttachment = true;
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
    }
  }
  return (
    sawAttachment &&
    requiredQualityManagementEntryCoreActionMenuKeys.every((menuKey) =>
      actionsByMenuKey.has(menuKey),
    )
  );
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
) {
  const action = recordValue(value);
  if (!action) return false;
  const serviceStatus = action.serviceStatus;
  return (
    action.menuKey === expectedMenuKey &&
    action.role === "platform-admin" &&
    action.path === pathByMenuKey[expectedMenuKey] &&
    hasText(action.frontdeskAction) &&
    hasText(action.serviceOperation) &&
    typeof serviceStatus === "number" &&
    serviceStatus >= 200 &&
    serviceStatus < 300 &&
    action.readbackVerified === true &&
    action.auditVerified === true
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
      !arrayEquals(parsed.scenarioCodes, requiredMedicationSafetyFrontdeskScenarioCodes) ||
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
      !arrayEquals(parsed.productLayers, [
        "CLINICAL_EXECUTION",
        "DATA_INTEROPERABILITY",
        "QUALITY_IMPROVEMENT",
      ]) ||
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
      !arrayEquals(parsed.productLayers, [
        "CLINICAL_EXECUTION",
        "DATA_INTEROPERABILITY",
        "QUALITY_IMPROVEMENT",
      ]) ||
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
      !arrayEquals(parsed.productLayers, [
        "CLINICAL_EXECUTION",
        "DATA_INTEROPERABILITY",
        "QUALITY_IMPROVEMENT",
      ]) ||
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
      !arrayEquals(parsed.organizationLevels, ["HOSPITAL", "DEPARTMENT"]) ||
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
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredDiagnosisKnowledgeScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["MEDICAL_ASSET"]) ||
      !arrayEquals(parsed.semanticFamilies, ["DISEASE_DIAGNOSIS"]) ||
      !arrayEquals(parsed.specialtyDomains, ["CLINICAL_SPECIALTIES"]) ||
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
  const cdssRiskAsset = parseMedicationSafetyRuntimeAsset(runtime.cdssRiskAsset, "CDSS_RISK");
  const ruleAsset = parseMedicationSafetyRuntimeAsset(runtime.ruleAsset, "RULE");
  if (!safetyAsset || !cdssRiskAsset || !ruleAsset) return null;
  return {
    releaseId: String(runtime.releaseId),
    revisionNo: runtime.revisionNo,
    manifestSha256: String(runtime.manifestSha256),
    safetyAsset,
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
      !arrayEquals(parsed.versionedAssets, ["ORDER_SET"]) ||
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

function arrayEquals(value: unknown, expected: string[]) {
  if (!Array.isArray(value)) return false;
  const observed = value.filter((item): item is string => typeof item === "string").sort();
  return JSON.stringify(observed) === JSON.stringify([...expected].sort());
}

function hasText(value: unknown) {
  return typeof value === "string" && value.trim().length > 0;
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
    target[key].push({
      code,
      status: "PASSED",
      evidenceKey: `launchCoverage.${key}.${code}`,
      observedAt,
    });
  }
}
