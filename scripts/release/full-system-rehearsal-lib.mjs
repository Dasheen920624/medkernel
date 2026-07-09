import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import { launchCoverageClaims } from "./stage-launch-coverage-lib.mjs";
import { FULL_KNOWLEDGE_DOMAINS } from "../knowledge/full-knowledge-rehearsal-lib.mjs";

const REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const REQUIRED_LAUNCH_COVERAGE = Object.freeze({
  productLayers: {
    label: "六层产品能力",
    codes: [
      "FOUNDATION_GOVERNANCE",
      "DATA_INTEROPERABILITY",
      "MEDICAL_ASSET",
      "RELEASE_GOVERNANCE",
      "CLINICAL_EXECUTION",
      "DELIVERY_FEEDBACK",
    ],
  },
  standardPatientResources: {
    label: "13 类标准患者资源",
    codes: [
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
    ],
  },
  standardPatientResourceConsumerMatrix: {
    label: "13 类标准患者资源真实消费者矩阵",
    codes: ["THIRTEEN_STANDARD_RESOURCES_REPRESENTATIVE"],
  },
  standardPatientResourceRepresentativeRows: {
    label: "13 类标准患者资源真实消费者行",
    codes: [
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
    ],
  },
  versionedAssets: {
    label: "13 类版本化资产",
    codes: [
      "KNOWLEDGE",
      "TERMINOLOGY",
      "RULE",
      "PATHWAY",
      "EVALUATION",
      "FOLLOWUP",
      "FIELD_CATALOG",
      "SAFETY",
      "CDSS_RISK",
      "VALUE_SET",
      "FORMULA",
      "ORDER_SET",
      "ACTION_CARD",
    ],
  },
  versionedAssetSupplyChainMatrix: {
    label: "13 类版本化资产供给链矩阵",
    codes: ["THIRTEEN_VERSIONED_ASSETS_GAP_AWARE_REPRESENTATIVE"],
  },
  versionedAssetRepresentativeRows: {
    label: "13 类版本化资产供给链代表行",
    codes: [
      "KNOWLEDGE",
      "TERMINOLOGY",
      "RULE",
      "PATHWAY",
      "EVALUATION",
      "FOLLOWUP",
      "FIELD_CATALOG",
      "SAFETY",
      "CDSS_RISK",
      "VALUE_SET",
      "FORMULA",
      "ORDER_SET",
      "ACTION_CARD",
    ],
  },
  versionedAssetKnownGaps: {
    label: "13 类版本化资产供给链已知缺口",
    optional: true,
    codes: [
      "KNOWLEDGE",
      "TERMINOLOGY",
      "RULE",
      "PATHWAY",
      "EVALUATION",
      "FOLLOWUP",
      "FIELD_CATALOG",
      "SAFETY",
      "CDSS_RISK",
      "VALUE_SET",
      "FORMULA",
      "ORDER_SET",
      "ACTION_CARD",
    ],
  },
  versionedAssetRollbackRepresentativeMatrix: {
    label: "版本化资产回滚负向消费者矩阵",
    codes: ["GAP_AWARE_RUNTIME_CONSUMER_NEGATIVE_REPRESENTATIVE"],
  },
  versionedAssetRollbackRepresentativeRows: {
    label: "版本化资产回滚负向消费者行",
    codes: [
      "SAFETY",
      "CDSS_RISK",
      "VALUE_SET",
      "FORMULA",
      "PATHWAY",
      "ORDER_SET",
      "EVALUATION",
    ],
  },
  versionedAssetDedicatedReleaseContractMatrix: {
    label: "术语、字段目录与路径专项发布契约矩阵",
    codes: ["TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS"],
  },
  versionedAssetDedicatedReleaseContractRows: {
    label: "术语、字段目录与路径专项发布契约资产行",
    codes: ["TERMINOLOGY", "FIELD_CATALOG", "PATHWAY"],
  },
  knowledgeSupplyChainEvidenceMatrix: {
    label: "知识供给链受控证据矩阵",
    codes: ["CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE"],
  },
  knowledgeSupplyChainEvidenceRows: {
    label: "知识供给链受控证据行",
    codes: [
      "SOURCE_CONTROL",
      "HUMAN_GOVERNANCE",
      "TERMINOLOGY_SYNC",
      "RUNTIME_LIFECYCLE",
      "LINEAGE_CONSUMERS",
      "SAFETY_BOUNDARY",
    ],
  },
  launchReadinessStakeholderMatrix: {
    label: "上线保障三视角代表矩阵",
    codes: ["IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE"],
  },
  launchReadinessStakeholderRows: {
    label: "上线保障三视角代表行",
    codes: [
      "IT_MANAGER_RUNTIME_DIAGNOSTICS",
      "IMPLEMENTATION_ENGINEER_ONBOARDING_GUIDE",
      "HOSPITAL_EXECUTIVE_QUALITY_OVERVIEW",
    ],
  },
  implementationGuideEntryCoreActions: {
    label: "实施与验收入口代表动作矩阵",
    codes: ["IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS"],
  },
  implementationGuideEntryCoreActionRows: {
    label: "实施与验收入口代表动作行",
    codes: ["IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY"],
  },
  dashboardWorkbenchCoreActions: {
    label: "四职责工作台核心动作矩阵",
    codes: ["FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS"],
  },
  dashboardWorkbenchCoreActionRows: {
    label: "四职责工作台核心动作行",
    codes: ["PLATFORM_ADMIN", "ENGINE_OPERATOR", "CLINICAL_USER", "AUDITOR"],
  },
  roleRepresentativeCoreActions: {
    label: "四职责代表主动作矩阵",
    codes: ["FOUR_ROLE_PRIMARY_ACTIONS"],
  },
  entryRepresentativeCoreActions: {
    label: "六入口代表核心动作矩阵",
    codes: ["SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE"],
  },
  platformAdminEntryCoreActions: {
    label: "平台管理员 P0 入口核心动作矩阵",
    codes: ["FOUR_PLATFORM_ADMIN_P0_ENTRY_ACTIONS"],
  },
  platformAdminP1EntryCoreActions: {
    label: "平台管理员 P1 运维入口核心动作矩阵",
    codes: ["RUNTIME_DIAGNOSTICS_DOMESTIC_CHECK"],
  },
  clinicalEntryCoreActions: {
    label: "临床协同入口核心动作矩阵",
    codes: ["CLINICAL_COLLABORATION_CORE_ACTIONS_REPRESENTATIVE"],
  },
  qualityManagementEntryCoreActions: {
    label: "质量管理入口核心动作矩阵",
    codes: ["QUALITY_MANAGEMENT_CORE_ACTIONS_REPRESENTATIVE"],
  },
  knowledgeOperationsAssetEntryCoreActions: {
    label: "知识运营资产入口族核心动作矩阵",
    codes: ["KNOWLEDGE_OPERATIONS_ASSET_ENTRY_FAMILY_REPRESENTATIVE"],
  },
  knowledgeDomains: {
    label: "11 个知识内容分类",
    codes: [...FULL_KNOWLEDGE_DOMAINS],
  },
  databaseMigrationSource: {
    label: "单一数据库模式源",
    codes: ["SINGLE_SCHEMA_GENERATOR_CHECK"],
  },
  databaseDialects: {
    label: "五数据库方言",
    codes: ["POSTGRES", "KINGBASE", "ORACLE", "DM", "H2"],
  },
  semanticFamilies: {
    label: "完整医疗语义族",
    codes: [
      "DISEASE_DIAGNOSIS",
      "SYMPTOM_RISK",
      "DIAGNOSTIC_REPORT",
      "MEDICATION_THERAPY",
      "SURGERY_TECHNOLOGY",
      "DEVICE_CONSUMABLE",
      "GUIDELINE_EVIDENCE",
      "SCALE_FORMULA",
      "NURSING",
      "PATHWAY_CONTINUITY",
      "MEDICAL_RECORD_INSURANCE",
      "INFECTION_PUBLIC_HEALTH",
      "COMPREHENSIVE_CARE",
      "TCM",
      "QUALITY_REGULATION",
      "SOURCE_VALIDITY",
    ],
  },
  specialtyDomains: {
    label: "全医疗专业领域",
    codes: [
      "CLINICAL_SPECIALTIES",
      "NURSING",
      "MEDICAL_TECHNOLOGY",
      "PHARMACY",
      "SURGERY_ANESTHESIA_TRANSFUSION",
      "EMERGENCY_CRITICAL_CARE",
      "SPECIAL_POPULATIONS",
      "ONCOLOGY_DIALYSIS_TRANSPLANT",
      "REHAB_NUTRITION_PAIN_PALLIATIVE",
      "INFECTION_PUBLIC_HEALTH",
      "TCM_INTEGRATIVE",
      "DENTAL_ENT_DERMATOLOGY",
      "INSURANCE_RECORD_QUALITY",
      "RWD_RESEARCH",
      "PRIMARY_REGIONAL_REMOTE",
    ],
  },
  scenarios: {
    label: "S0–S40 业务场景",
    codes: Array.from({ length: 41 }, (_, index) => `S${index}`),
  },
  deliveryShapes: {
    label: "五种交付形态",
    codes: [
      "MANAGEMENT_WORKSPACE",
      "ENGINE_CORE",
      "EMBEDDED_COMPONENT",
      "API_EVENT",
      "OFFLINE_DELIVERY",
    ],
  },
  serviceCombinations: {
    label: "七类业务服务组合",
    codes: [
      "ONBOARDING_INTEGRATION",
      "CLINICAL_RUNTIME",
      "QUALITY_IMPROVEMENT",
      "COMPLIANCE_OPERATIONS",
      "THIRD_PARTY_INTERFACE",
      "SPECIAL_DISEASE_PATHWAY",
      "PROFESSIONAL_COLLABORATION",
    ],
  },
  stakeholderViews: {
    label: "全角色真实体验视角",
    codes: [
      "PHYSICIAN",
      "NURSE",
      "PHARMACIST",
      "MEDICAL_TECHNICIAN",
      "QUALITY_CONTROLLER",
      "PATIENT_PROXY",
      "PLATFORM_ADMIN",
      "ENGINE_OPERATOR",
      "AUDITOR",
      "IT_MANAGER",
      "IMPLEMENTATION_ENGINEER",
      "HOSPITAL_EXECUTIVE",
    ],
  },
  thirdPartySystemFamilies: {
    label: "全部第三方系统族",
    codes: [
      "HIS_EMR_CDR",
      "LIS_MONITORING_CRITICAL",
      "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      "PHARMACY_REVIEW",
      "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      "MEDICAL_RECORD_INSURANCE_PAYMENT",
      "PUBLIC_HEALTH_INFECTION_REGULATORY",
      "FOLLOWUP_PATIENT_SERVICE",
      "CA_OIDC_SSO_HR",
      "REGIONAL_REMOTE",
      "SPD_UDI_DEVICE",
      "RESEARCH_ETHICS_DATA",
      "MODEL_DIFY_AGENT",
    ],
  },
  thirdPartySystemFamilyConsumerSlices: {
    label: "第三方系统族真实消费者代表切片",
    codes: [
      "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      "PHARMACY_REVIEW",
      "PUBLIC_HEALTH_INFECTION_REGULATORY",
      "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      "LIS_MONITORING_CRITICAL",
      "REGIONAL_REMOTE",
    ],
  },
  diagnosticReportFamilyConsumerMatrix: {
    label: "五类医技报告族真实消费者矩阵",
    codes: ["PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG"],
  },
  complianceWorkbenchPersonalEntryMatrix: {
    label: "合规安全与工作台个人入口强证据矩阵",
    codes: ["COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS"],
  },
  complianceWorkbenchPersonalEntryRows: {
    label: "合规安全与工作台个人入口强证据行",
    codes: [
      "SECURITY_BASELINE_CONFIG_CHANGE",
      "AUDIT_EVIDENCE_EXPORT_VERIFY",
      "NOTIFICATION_READBACK",
      "NOTIFICATION_SETTINGS_SAVE",
      "SOURCE_LINEAGE_PROVENANCE_READBACK",
    ],
  },
  organizationLevels: {
    label: "集团组织层级",
    codes: [
      "PLATFORM",
      "GROUP",
      "HOSPITAL",
      "CAMPUS_OR_MEMBER",
      "DEPARTMENT",
      "WARD",
      "CARE_TEAM",
      "SPECIALTY_CENTER",
      "SHARED_CENTER",
    ],
  },
  specialDiseaseStages: {
    label: "专病十阶段",
    codes: [
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
    ],
  },
  modelEnablementSurfaces: {
    label: "全中枢模型赋能矩阵",
    codes: [
      "SOURCE_DISCOVERY",
      "DOCUMENT_EXTRACT",
      "TERMINOLOGY_MAPPING",
      "RULE_QUALITY",
      "PATHWAY_CONTINUITY",
      "DIAGNOSIS_CDSS",
      "NURSING_COLLABORATION",
      "REPORT_INTERPRETATION",
      "EVALUATION_INSURANCE_RECORD",
      "FOLLOWUP_EDUCATION",
      "OPERATIONS_TESTING",
      "NATURAL_LANGUAGE_ACCESS",
    ],
  },
});

const REQUIRED_LAUNCH_ACCEPTANCE = Object.freeze([
  {
    code: "LAUNCH-01",
    label: "六层能力均有真实存储、服务、权限、消费者和审计",
    requiredCoverage: ["productLayers"],
  },
  {
    code: "LAUNCH-02",
    label: "13 类标准患者资源可由真实接入落地并驱动运行",
    requiredCoverage: [
      "standardPatientResources",
      "standardPatientResourceConsumerMatrix",
      "standardPatientResourceRepresentativeRows",
    ],
  },
  {
    code: "LAUNCH-03",
    label: "13 类版本化资产均有正文、校验、依赖、发布和运行消费者",
    requiredCoverage: [
      "versionedAssets",
      "versionedAssetSupplyChainMatrix",
      "versionedAssetRepresentativeRows",
      "versionedAssetRollbackRepresentativeMatrix",
      "versionedAssetRollbackRepresentativeRows",
    ],
  },
  {
    code: "LAUNCH-04",
    label: "11 个知识内容分类可生产、审核、发布、替换和回滚",
    requiredCoverage: [
      "knowledgeDomains",
      "knowledgeSupplyChainEvidenceMatrix",
      "knowledgeSupplyChainEvidenceRows",
    ],
  },
  {
    code: "LAUNCH-05",
    label: "全医疗专业领域具备权威代表资产和运行证据闭环",
    requiredCoverage: ["specialtyDomains"],
  },
  {
    code: "LAUNCH-06",
    label: "S0-S40 完成正常、异常、缺数、高风险和降级演练",
    requiredCoverage: ["scenarios"],
  },
  {
    code: "LAUNCH-07",
    label: "平台标准、两机构差异、部分选择、升级和回滚可重放",
    requiredCoverage: [
      "versionedAssetDedicatedReleaseContractMatrix",
      "versionedAssetDedicatedReleaseContractRows",
      "versionedAssetRollbackRepresentativeMatrix",
      "versionedAssetRollbackRepresentativeRows",
    ],
  },
  {
    code: "LAUNCH-08",
    label: "运行端服务端解析当前机构生效版本",
    requiredCoverage: [
      "versionedAssets",
      "versionedAssetDedicatedReleaseContractMatrix",
      "versionedAssetDedicatedReleaseContractRows",
    ],
  },
  {
    code: "LAUNCH-09",
    label: "四职责覆盖全部菜单和运行端点，MFA 默认关闭且可真实开启",
    requiredCoverage: [
      "stakeholderViews",
      "roleRepresentativeCoreActions",
      "entryRepresentativeCoreActions",
      "dashboardWorkbenchCoreActions",
      "dashboardWorkbenchCoreActionRows",
      "platformAdminEntryCoreActions",
      "platformAdminP1EntryCoreActions",
      "clinicalEntryCoreActions",
      "qualityManagementEntryCoreActions",
      "knowledgeOperationsAssetEntryCoreActions",
    ],
  },
  {
    code: "LAUNCH-10",
    label: "模型候选生成可用，关闭模型后 B0 主链完整",
    requiredCoverage: ["modelEnablementSurfaces", "knowledgeSupplyChainEvidenceMatrix"],
  },
  {
    code: "LAUNCH-11",
    label: "五数据库方言由一个模式源生成同版本迁移",
    requiredCoverage: ["databaseMigrationSource", "databaseDialects"],
  },
  {
    code: "LAUNCH-12",
    label: "五种交付形态、七类业务组合和第三方系统族有消费者与降级验证",
    requiredCoverage: [
      "deliveryShapes",
      "serviceCombinations",
      "thirdPartySystemFamilies",
      "thirdPartySystemFamilyConsumerSlices",
      "diagnosticReportFamilyConsumerMatrix",
    ],
  },
  {
    code: "LAUNCH-13",
    label: "集团、医院、院区、科室、病区、团队和跨机构任职得到验证",
    requiredCoverage: ["organizationLevels"],
  },
  {
    code: "LAUNCH-14",
    label: "完整医疗语义、专病十阶段和全中枢模型赋能矩阵具备代表用例",
    requiredCoverage: ["semanticFamilies", "specialDiseaseStages", "modelEnablementSurfaces"],
  },
  {
    code: "LAUNCH-15",
    label: "目标环境完成备份恢复、清库 V1、部署、全功能全知识演练和重启恢复",
    requiredCoverage: [
      "launchReadinessStakeholderMatrix",
      "launchReadinessStakeholderRows",
      "implementationGuideEntryCoreActions",
      "implementationGuideEntryCoreActionRows",
      "deliveryShapes",
      "databaseMigrationSource",
      "databaseDialects",
    ],
  },
]);

export function readFullSystemRehearsalConfig(env, options = {}) {
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  const required = [
    "MEDKERNEL_RUNTIME_ROOT",
    "LAUNCH_WEB_BASE_URL",
    "LAUNCH_API_BASE_URL",
    "LAUNCH_BOOTSTRAP_TOKEN_FILE",
    "LAUNCH_CREDENTIALS_FILE",
    "LAUNCH_MODEL_PROVIDER_CODE",
    "LAUNCH_MODEL_PROVIDER_TYPE",
    "LAUNCH_MODEL_PROVIDER_ENDPOINT",
    "LAUNCH_MODEL_VERSION",
    "FULL_KNOWLEDGE_MANIFEST_PATH",
    "LAUNCH_SOURCE",
  ];
  for (const key of required) {
    if (!hasText(env?.[key])) throw new Error(`缺少必填环境变量 ${key}`);
  }
  if (env.E2E_IGNORE_HTTPS_ERRORS === "1") {
    throw new Error("完整上线演练禁止忽略 HTTPS 证书错误");
  }

  const runtimeRoot = outsideRepo(
    env.MEDKERNEL_RUNTIME_ROOT,
    repoRoot,
    "运行时根目录",
  );
  const evidenceRoot = outsideRepo(
    env.FULL_SYSTEM_EVIDENCE_ROOT?.trim() ||
      path.join(runtimeRoot, "evidence/current-launch"),
    repoRoot,
    "整套演练证据目录",
  );
  const credentialsPath = outsideRepo(
    env.LAUNCH_CREDENTIALS_FILE,
    repoRoot,
    "统一上线凭据路径",
  );
  const bootstrapTokenPath = outsideRepo(
    env.LAUNCH_BOOTSTRAP_TOKEN_FILE,
    repoRoot,
    "首次接管令牌路径",
  );
  const webBaseUrl = normalizeWebBaseUrl(env.LAUNCH_WEB_BASE_URL);
  const apiBaseUrl = normalizeApiBaseUrl(env.LAUNCH_API_BASE_URL);
  if (!apiBaseUrl.startsWith(`${webBaseUrl}/`)) {
    throw new Error("上线 Web 与 API 地址必须属于同一 /medkernel 部署上下文");
  }

  return {
    repoRoot,
    runtimeRoot,
    evidenceRoot,
    indexPath: path.join(evidenceRoot, "full-system.json"),
    credentialsPath,
    bootstrapTokenPath,
    manifestPath: path.resolve(env.FULL_KNOWLEDGE_MANIFEST_PATH.trim()),
    source: normalizeSource(env.LAUNCH_SOURCE),
    webBaseUrl,
    apiBaseUrl,
    provider: {
      code: requireText(env.LAUNCH_MODEL_PROVIDER_CODE, "Provider 编码"),
      type: requireText(env.LAUNCH_MODEL_PROVIDER_TYPE, "Provider 类型"),
      endpoint: requireText(
        env.LAUNCH_MODEL_PROVIDER_ENDPOINT,
        "Provider 端点",
      ),
      modelVersion: requireText(env.LAUNCH_MODEL_VERSION, "模型版本"),
    },
  };
}

export function buildFullSystemStagePlan(config) {
  const databaseMigrationEvidence = path.join(
    config.evidenceRoot,
    "database-migrations.json",
  );
  const accountEvidence = path.join(
    config.evidenceRoot,
    "account-bootstrap.json",
  );
  const modelEvidence = path.join(config.evidenceRoot, "model-provider.json");
  const platformBaselineEvidence = path.join(
    config.evidenceRoot,
    "platform-baseline.json",
  );
  const sandboxRoot = path.join(config.evidenceRoot, "sandbox");
  const knowledgeEvidence = path.join(
    config.evidenceRoot,
    "full-knowledge.json",
  );
  const resilienceEvidence = path.join(
    config.evidenceRoot,
    "runtime-resilience.json",
  );
  const browserRoot = path.join(config.evidenceRoot, "e2e");
  const launchCoverageEvidence = path.join(
    config.evidenceRoot,
    "launch-coverage.json",
  );
  const common = {
    MEDKERNEL_RUNTIME_ROOT: config.runtimeRoot,
  };
  return [
    {
      id: "database-migrations",
      label: "单一模式源五方言迁移基线",
      command: process.execPath,
      args: ["scripts/release/database-migration-baseline.mjs"],
      cwd: config.repoRoot,
      evidencePath: databaseMigrationEvidence,
      env: {
        ...common,
        LAUNCH_DATABASE_MIGRATION_EVIDENCE_PATH: databaseMigrationEvidence,
      },
    },
    {
      id: "account-bootstrap",
      label: "全新接管与四职责账号",
      command: process.execPath,
      args: ["scripts/release/launch-account-bootstrap.mjs"],
      cwd: config.repoRoot,
      evidencePath: accountEvidence,
      env: {
        ...common,
        LAUNCH_API_BASE_URL: config.apiBaseUrl,
        LAUNCH_BOOTSTRAP_TOKEN_FILE: config.bootstrapTokenPath,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
        LAUNCH_ACCOUNT_EVIDENCE_PATH: accountEvidence,
      },
    },
    {
      id: "model-provider",
      label: "真实 Provider 探活与医学回归",
      command: process.execPath,
      args: ["scripts/release/model-provider-launch.mjs"],
      cwd: config.repoRoot,
      evidencePath: modelEvidence,
      env: {
        ...common,
        LAUNCH_API_BASE_URL: config.apiBaseUrl,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
        LAUNCH_MODEL_PROVIDER_CODE: config.provider.code,
        LAUNCH_MODEL_PROVIDER_TYPE: config.provider.type,
        LAUNCH_MODEL_PROVIDER_ENDPOINT: config.provider.endpoint,
        LAUNCH_MODEL_VERSION: config.provider.modelVersion,
        FULL_KNOWLEDGE_MANIFEST_PATH: config.manifestPath,
        LAUNCH_MODEL_EVIDENCE_PATH: modelEvidence,
      },
    },
    {
      id: "full-knowledge",
      label: "11 域全知识与 V2 回滚恢复",
      command: process.execPath,
      args: ["scripts/knowledge/full-knowledge-rehearsal.mjs"],
      cwd: config.repoRoot,
      evidencePath: knowledgeEvidence,
      env: {
        ...common,
        FULL_KNOWLEDGE_API_BASE_URL: config.apiBaseUrl,
        FULL_KNOWLEDGE_CREDENTIALS_FILE: config.credentialsPath,
        FULL_KNOWLEDGE_MANIFEST_PATH: config.manifestPath,
        FULL_KNOWLEDGE_PROVIDER_CODE: config.provider.code,
        FULL_KNOWLEDGE_EVIDENCE_PATH: knowledgeEvidence,
      },
    },
    {
      id: "platform-baseline",
      label: "平台字段目录与全知识权威基线",
      command: process.execPath,
      args: ["scripts/release/platform-baseline-bootstrap.mjs"],
      cwd: config.repoRoot,
      evidencePath: platformBaselineEvidence,
      env: {
        ...common,
        LAUNCH_API_BASE_URL: config.apiBaseUrl,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
        FULL_KNOWLEDGE_MANIFEST_PATH: config.manifestPath,
        LAUNCH_PLATFORM_BASELINE_EVIDENCE_PATH: platformBaselineEvidence,
      },
    },
    {
      id: "sandbox",
      label: "演练机构十规则四十用例与机构生效版本",
      command: process.execPath,
      args: ["scripts/sandbox/seed-scenarios.mjs"],
      cwd: config.repoRoot,
      evidencePath: path.join(sandboxRoot, "seed-summary.json"),
      env: {
        ...common,
        DRILL_BASE_URL: new URL(config.webBaseUrl).origin,
        DRILL_EVIDENCE_DIR: sandboxRoot,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
      },
    },
    {
      id: "runtime-resilience",
      label: "模型关闭诚实降级、B0 核心可用与恢复启用",
      command: process.execPath,
      args: ["scripts/release/runtime-resilience-rehearsal.mjs"],
      cwd: config.repoRoot,
      evidencePath: resilienceEvidence,
      env: {
        ...common,
        LAUNCH_API_BASE_URL: config.apiBaseUrl,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
        RUNTIME_RESILIENCE_PROVIDER_CODE: config.provider.code,
        RUNTIME_RESILIENCE_EVIDENCE_PATH: resilienceEvidence,
      },
    },
    {
      id: "browser-e2e",
      label: "全页面全职责浏览器旅程",
      command: "npm",
      args: ["run", "e2e"],
      cwd: path.join(config.repoRoot, "frontend"),
      evidencePath: path.join(browserRoot, "report/results.json"),
      env: {
        ...common,
        E2E_EXTERNAL_DEPLOYMENT: "1",
        E2E_BASE_URL: config.webBaseUrl,
        E2E_API_BASE_URL: config.apiBaseUrl,
        E2E_ROLE_CREDENTIALS_FILE: config.credentialsPath,
        E2E_EVIDENCE_DIR: browserRoot,
        E2E_EXPECT_MFA_DISABLED: "1",
      },
    },
    {
      id: "launch-coverage",
      label: "完整产品范围覆盖审计",
      command: process.execPath,
      args: ["scripts/release/launch-coverage-audit.mjs"],
      cwd: config.repoRoot,
      evidencePath: launchCoverageEvidence,
      env: {
        ...common,
        FULL_SYSTEM_EVIDENCE_ROOT: config.evidenceRoot,
        LAUNCH_COVERAGE_EVIDENCE_PATH: launchCoverageEvidence,
        FULL_KNOWLEDGE_MANIFEST_PATH: config.manifestPath,
        LAUNCH_SOURCE: config.source,
      },
    },
  ];
}

export function formatFullSystemProgress(event) {
  if (!event || typeof event !== "object") return "[full-system] 进度事件无效";
  switch (event.type) {
    case "stage-start":
      return `[full-system] 阶段 ${event.sequence}/${event.total} 开始：${event.stageLabel}`;
    case "stage-complete":
      return `[full-system] 阶段 ${event.completed}/${event.total} 通过：${event.stageLabel}，用时 ${formatDuration(event.durationMs)}，还剩 ${event.remaining} 个`;
    case "stage-failed":
      return `[full-system] 阶段 ${event.sequence}/${event.total} 失败：${event.stageLabel}，exit=${event.exitCode}`;
    case "rehearsal-complete":
      return `[full-system] 整套上线演练通过：${event.stageCount} 个阶段，证据 ${event.indexPath}`;
    default:
      return `[full-system] ${event.type ?? "未知进度"} ${event.stageId ?? ""}`.trim();
  }
}

export async function runFullSystemRehearsal(config, dependencies = {}) {
  const runCommand = dependencies.runCommand ?? spawnStage;
  const readJson = dependencies.readJson ?? readJsonFile;
  const writeJson = dependencies.writeJson ?? writeJsonAtomic;
  const clock = dependencies.now;
  const progress = createProgressReporter(dependencies.onProgress, clock);
  const startedAt = now(clock);
  const completed = [];
  let launchCoverage = null;
  let launchAcceptance = null;
  const stages = buildFullSystemStagePlan(config);
  const totalStages = stages.length;

  for (const [index, stage] of stages.entries()) {
    progress({
      type: "stage-start",
      stageId: stage.id,
      stageLabel: stage.label,
      sequence: index + 1,
      total: totalStages,
      completed: index,
      remaining: totalStages - index,
      evidencePath: stage.evidencePath,
    });
    const stageStartedAt = now(clock);
    const commandResult = await runCommand(stage);
    if (commandResult?.exitCode !== 0) {
      progress({
        type: "stage-failed",
        stageId: stage.id,
        stageLabel: stage.label,
        sequence: index + 1,
        total: totalStages,
        completed: index,
        remaining: totalStages - index,
        exitCode: commandResult?.exitCode ?? "unknown",
        evidencePath: stage.evidencePath,
      });
      throw new Error(
        `${stage.id} 阶段失败（exit=${commandResult?.exitCode ?? "unknown"}）`,
      );
    }
    const evidence = readJson(stage.evidencePath, stage);
    const summary = validateStageEvidence(stage.id, evidence);
    if (stage.id === "launch-coverage") {
      launchCoverage = evidence.coverage;
      launchAcceptance = evidence.acceptance ?? buildLaunchAcceptance(launchCoverage);
    }
    const stageFinishedAt = now(clock);
    const durationMs = elapsedMs(stageStartedAt, stageFinishedAt);
    completed.push({
      id: stage.id,
      label: stage.label,
      status: "PASSED",
      startedAt: stageStartedAt,
      finishedAt: stageFinishedAt,
      durationMs,
      evidencePath: stage.evidencePath,
      summary,
    });
    progress({
      type: "stage-complete",
      stageId: stage.id,
      stageLabel: stage.label,
      sequence: index + 1,
      total: totalStages,
      completed: index + 1,
      remaining: totalStages - index - 1,
      durationMs,
      evidencePath: stage.evidencePath,
      summary,
    });
  }

  const finishedAt = now(clock);
  const index = {
    schemaVersion: "1.0.0",
    status: "PASSED",
    stage: "FULL_SYSTEM_REHEARSAL",
    source: config.source,
    startedAt,
    finishedAt,
    durationMs: elapsedMs(startedAt, finishedAt),
    webBaseUrl: config.webBaseUrl,
    apiBaseUrl: config.apiBaseUrl,
    coverage: launchCoverage,
    acceptance: launchAcceptance,
    observability: {
      stageCount: completed.length,
      completedStages: completed.length,
      failedStages: 0,
    },
    stages: completed,
  };
  writeJson(config.indexPath, index);
  progress({
    type: "rehearsal-complete",
    status: index.status,
    stageCount: completed.length,
    durationMs: index.durationMs,
    indexPath: config.indexPath,
  });
  return index;
}

export function validateStageEvidence(stageId, evidence) {
  if (!evidence || typeof evidence !== "object" || Array.isArray(evidence)) {
    throw new Error(`${stageId} 阶段证据不是 JSON 对象`);
  }
  switch (stageId) {
    case "database-migrations":
      return validateDatabaseMigrationEvidence(evidence);
    case "account-bootstrap":
      if (evidence.status !== "PASSED" || evidence.verifiedAccountCount !== 9) {
        throw new Error("四职责账号与系统接管身份未完整验证");
      }
      return { verifiedAccountCount: 9, mfaRequired: evidence.mfaRequired };
    case "model-provider":
      if (
        evidence.status !== "PASSED" ||
        evidence.provider?.enabled !== true ||
        evidence.provider?.status !== "HEALTHY" ||
        evidence.evaluation?.status !== "PASSED" ||
        evidence.evaluation?.totalCases !== 3 ||
        evidence.evaluation?.passedCases !== 3 ||
        evidence.evaluation?.failedCases !== 0
      ) {
        throw new Error("Provider 未同时通过真实探活、医学回归与启用门禁");
      }
      return {
        providerCode: evidence.provider.code,
        evaluationCases: evidence.evaluation.totalCases,
      };
    case "platform-baseline":
      if (
        evidence.status !== "PASSED" ||
        evidence.stage !== "PLATFORM_BASELINE_BOOTSTRAP" ||
        evidence.operator?.tenantId !== "t-1" ||
        evidence.operator?.role !== "engine-operator" ||
        evidence.fieldCatalog?.assetType !== "FIELD_CATALOG" ||
        evidence.fieldCatalog?.assetIdentity !==
          "FIELD.CATALOG.CLINICAL_CONTEXT" ||
        evidence.fieldCatalog?.entryState !== "ACTIVE" ||
        typeof evidence.fieldCatalog?.versionId !== "string" ||
        !evidence.fieldCatalog.versionId.trim() ||
        !Number.isInteger(evidence.baseline?.revisionNo) ||
        !evidence.baseline?.baselineReleaseId
      ) {
        throw new Error("字段目录平台基线未完整发布为当前平台标准版本");
      }
      if (
        evidence.knowledge?.requiredCount !== 11 ||
        evidence.knowledge?.activeCount !== 11 ||
        !Array.isArray(evidence.knowledgeAssets) ||
        evidence.knowledgeAssets.length !== 11 ||
        evidence.knowledgeAssets.some(
          (item) =>
            item?.assetType !== "KNOWLEDGE" ||
            item.entryState !== "ACTIVE" ||
            typeof item.versionId !== "string" ||
            !item.versionId.trim() ||
            typeof item.assetIdentity !== "string" ||
            !item.assetIdentity.trim(),
        )
      ) {
        throw new Error("全知识平台基线未完整发布为当前平台标准版本");
      }
      return {
        baselineReleaseId: evidence.baseline.baselineReleaseId,
        revisionNo: evidence.baseline.revisionNo,
        fieldCatalogVersion: evidence.fieldCatalog.versionId,
        knowledgeActiveCount: evidence.knowledge.activeCount,
      };
    case "sandbox":
      if (
        !Array.isArray(evidence.results) ||
        evidence.results.length !== 10 ||
        evidence.results.some((item) => item?.result !== "PASS") ||
        !Array.isArray(evidence.failures) ||
        evidence.failures.length !== 0 ||
        evidence.runtimeBinding?.ready !== true ||
        evidence.runtimeBinding?.externalSideEffects !== false
      ) {
        throw new Error(
          "演练机构十规则、四十用例或 CURRENT 运行绑定未完整通过",
        );
      }
      return { ruleCount: 10, caseCount: 40, runtimeReady: true };
    case "full-knowledge": {
      const expected = new Set(FULL_KNOWLEDGE_DOMAINS);
      const declared = new Set(evidence.coverage?.expectedDomains ?? []);
      const published = new Set(evidence.coverage?.publishedDomains ?? []);
      const verifiedSources = Array.isArray(evidence.sourceVerification)
        ? evidence.sourceVerification
        : [];
      const knowledgeRows = Array.isArray(evidence.knowledge)
        ? evidence.knowledge
        : [];
      const verifiedSourceDomains = new Set(
        verifiedSources
          .filter(
            (item) =>
              item?.status === "VERIFIED" &&
              Number(item.httpStatus) >= 200 &&
              Number(item.httpStatus) < 300 &&
              hasText(item.sourceUrl) &&
              hasText(item.contentSha256) &&
              Array.isArray(item.matchedTerms) &&
              item.matchedTerms.length > 0,
          )
          .map((item) => item.domain),
      );
      const provenKnowledgeDomains = new Set(
        knowledgeRows
          .filter((item) => hasCompleteKnowledgeProductionEvidence(item))
          .map((item) => item.domain),
      );
      const lifecycle = evidence.versionLifecycle;
      const lifecycleValid =
        lifecycle?.v1VersionId != null &&
        lifecycle?.v2VersionId != null &&
        lifecycle.rollbackActiveVersionId === lifecycle.v1VersionId &&
        lifecycle.restoredActiveVersionId === lifecycle.v2VersionId &&
        lifecycle.finalStatus === "ACTIVE";
      if (
        evidence.status !== "PASSED" ||
        declared.size !== 11 ||
        published.size !== 11 ||
        [...expected].some(
          (domain) => !declared.has(domain) || !published.has(domain),
        ) ||
        !lifecycleValid
      ) {
        throw new Error("正式全知识没有完整覆盖 11 个知识域及 V1/V2 回滚恢复");
      }
      if (
        verifiedSourceDomains.size !== 11 ||
        [...expected].some((domain) => !verifiedSourceDomains.has(domain))
      ) {
        throw new Error("正式全知识来源核验没有完整覆盖 11 个知识域");
      }
      if (
        provenKnowledgeDomains.size !== 11 ||
        [...expected].some((domain) => !provenKnowledgeDomains.has(domain))
      ) {
        throw new Error("正式全知识生产、质量门、影子评测或运行证据不完整");
      }
      if (
        evidence.observability?.completedDomains !== 11 ||
        evidence.observability?.remainingDomains !== 0 ||
        !Array.isArray(evidence.observability?.modelTasks) ||
        evidence.observability.modelTasks.length < 12
      ) {
        throw new Error("正式全知识进度和模型任务证据不完整");
      }
      if (
        evidence.safety?.containsCredentials !== false ||
        evidence.safety?.containsPatientData !== false ||
        evidence.safety?.clinicalActionGenerated !== false ||
        evidence.safety?.automatedOrderGenerated !== false
      ) {
        throw new Error("正式全知识安全边界证据不完整");
      }
      return {
        knowledgeDomainCount: 11,
        finalVersion: "V2",
        finalStatus: "ACTIVE",
      };
    }
    case "runtime-resilience":
      if (
        evidence.status !== "PASSED" ||
        evidence.disabled?.providerEnabled !== false ||
        evidence.disabled?.readinessReady !== false ||
        evidence.disabled?.modelInvocationAllowed !== false ||
        !Array.isArray(evidence.disabled?.blockingRequiredItems) ||
        evidence.disabled.blockingRequiredItems.length !== 1 ||
        evidence.disabled.blockingRequiredItems[0] !== "MODEL_PROVIDER" ||
        evidence.b0?.evidenceCount !== 17 ||
        evidence.b0?.passedCount !== 17 ||
        evidence.b0?.modelRequiredCount !== 0 ||
        evidence.restored?.providerEnabled !== true ||
        evidence.restored?.providerStatus !== "HEALTHY" ||
        evidence.restored?.readinessReady !== true ||
        evidence.restored?.modelInvocationAllowed !== true
      ) {
        throw new Error("模型关闭诚实降级、B0 核心可用或恢复启用证据不完整");
      }
      return {
        disabledBlocker: "MODEL_PROVIDER",
        b0EvidenceCount: 17,
        restored: true,
      };
    case "browser-e2e":
      if (
        !Number.isInteger(evidence.stats?.expected) ||
        evidence.stats.expected <= 0 ||
        evidence.stats.unexpected !== 0 ||
        (evidence.stats.flaky ?? 0) !== 0
      ) {
        throw new Error("浏览器全量旅程存在失败、波动或没有实际执行");
      }
      return { passed: evidence.stats.expected, unexpected: 0, flaky: 0 };
    case "launch-coverage":
      assertCompleteLaunchCoverage(evidence);
      return Object.fromEntries(
        Object.entries(REQUIRED_LAUNCH_COVERAGE)
          .filter(([, requirement]) => !requirement.optional)
          .map(([key, requirement]) => [key, requirement.codes.length]),
      );
    default:
      throw new Error(`未知整套演练阶段 ${stageId}`);
  }
}

export function buildRequiredLaunchCoverage() {
  return Object.fromEntries(
    Object.entries(REQUIRED_LAUNCH_COVERAGE)
      .filter(([, requirement]) => !requirement.optional)
      .map(([key, requirement]) => [
        key,
        requirement.codes.map((code) => ({
          code,
          status: "UNKNOWN",
          evidenceStage: null,
          evidencePath: null,
          evidenceKey: null,
          observedCode: null,
          observedStatus: null,
          observedAt: null,
        })),
      ]),
  );
}

export function buildRequiredLaunchAcceptance() {
  return REQUIRED_LAUNCH_ACCEPTANCE.map((item) => ({
    code: item.code,
    label: item.label,
    requiredCoverage: [...item.requiredCoverage],
    status: "UNKNOWN",
    missingCoverage: [...item.requiredCoverage],
  }));
}

export { launchCoverageClaims };

export function buildLaunchCoverageFromStageEvidence(stageEvidence) {
  const claims = new Map();
  for (const item of stageEvidence ?? []) {
    const stageId = item?.stageId;
    if (!hasText(stageId) || stageId === "launch-coverage") continue;
    const launchCoverage = item?.evidence?.launchCoverage;
    if (
      !launchCoverage ||
      typeof launchCoverage !== "object" ||
      Array.isArray(launchCoverage)
    ) {
      continue;
    }
    for (const key of Object.keys(launchCoverage)) {
      if (!REQUIRED_LAUNCH_COVERAGE[key]) {
        throw new Error(`${stageId} 阶段声明了未知覆盖矩阵 ${key}`);
      }
    }
    for (const [key, requirement] of Object.entries(REQUIRED_LAUNCH_COVERAGE)) {
      const rows = launchCoverage[key];
      if (!Array.isArray(rows)) continue;
      const expectedCodes = new Set(requirement.codes);
      for (const row of rows) {
        const normalized = normalizeCoverageClaim(row, {
          key,
          stageId,
          evidencePath: item.evidencePath,
          evidence: item.evidence,
        });
        if (!expectedCodes.has(normalized.code)) {
          throw new Error(
            `${stageId} 阶段声明了无效${requirement.label}覆盖项 ${normalized.code}`,
          );
        }
        const claimKey = `${key}:${normalized.code}`;
        if (!claims.has(claimKey)) claims.set(claimKey, normalized);
      }
    }
  }
  return Object.fromEntries(
    Object.entries(REQUIRED_LAUNCH_COVERAGE)
      .filter(([, requirement]) => !requirement.optional)
      .map(([key, requirement]) => [
        key,
        requirement.codes.map((code) => {
          const row = claims.get(`${key}:${code}`);
          if (row) return row;
          return {
            code,
            status: "UNKNOWN",
            evidenceStage: null,
            evidencePath: null,
            evidenceKey: null,
            observedCode: null,
            observedStatus: null,
            observedAt: null,
          };
        }),
      ]),
  );
}

export function buildLaunchAcceptance(coverage) {
  return REQUIRED_LAUNCH_ACCEPTANCE.map((item) => {
    const missingCoverage = item.requiredCoverage.filter(
      (key) => !coverageKeyComplete(coverage, key),
    );
    return {
      code: item.code,
      label: item.label,
      requiredCoverage: [...item.requiredCoverage],
      status: missingCoverage.length === 0 ? "PASSED" : "FAILED",
      missingCoverage,
    };
  });
}

export function assertCompleteLaunchCoverage(evidence) {
  if (!evidence || typeof evidence !== "object" || Array.isArray(evidence)) {
    throw new Error("完整产品范围覆盖证据不是 JSON 对象");
  }
  if (evidence.status !== "PASSED") {
    throw new Error("完整产品范围覆盖审计未通过");
  }
  const coverage = evidence.coverage;
  if (!coverage || typeof coverage !== "object" || Array.isArray(coverage)) {
    throw new Error("完整产品范围覆盖矩阵缺失");
  }
  for (const [key, requirement] of Object.entries(REQUIRED_LAUNCH_COVERAGE)) {
    const rows = coverage[key];
    if (!Array.isArray(rows)) {
      if (requirement.optional) continue;
      throw new Error(`${requirement.label} 覆盖矩阵缺失`);
    }
    const actual = new Set();
    for (const row of rows) {
      if (!row || typeof row !== "object") {
        throw new Error(`${requirement.label} 覆盖行无效`);
      }
      if (row.status === "UNKNOWN") {
        throw new Error(`${requirement.label} ${row.code} 缺少前置阶段证据`);
      }
      if (row.status === "SKIPPED") {
        throw new Error(
          `${requirement.label} ${row.code} 不得为 ${row.status}`,
        );
      }
      if (row.status !== "PASSED") {
        throw new Error(`${requirement.label} ${row.code} 未通过覆盖审计`);
      }
      if (
        !hasText(row.evidenceStage) ||
        row.evidenceStage === "launch-coverage"
      ) {
        throw new Error(
          `${requirement.label} ${row.code} 不能由覆盖审计阶段自证`,
        );
      }
      if (!hasText(row.evidencePath) || !hasText(row.evidenceKey)) {
        throw new Error(
          `${requirement.label} ${row.code} 缺少前置阶段证据引用`,
        );
      }
      if (row.observedCode !== row.code || row.observedStatus !== "PASSED") {
        throw new Error(
          `${requirement.label} ${row.code} 前置阶段观测结果不匹配`,
        );
      }
      if (!hasText(row.observedAt)) {
        throw new Error(
          `${requirement.label} ${row.code} 缺少前置阶段观测时间`,
        );
      }
      actual.add(row.code);
    }
    if (
      !requirement.optional &&
      (actual.size !== requirement.codes.length ||
        requirement.codes.some((code) => !actual.has(code)))
    ) {
      throw new Error(`${requirement.label} 覆盖不完整`);
    }
  }
  const acceptance = evidence.acceptance ?? buildLaunchAcceptance(coverage);
  const failed = acceptance.filter((item) => item.status !== "PASSED");
  if (failed.length > 0 || acceptance.length !== REQUIRED_LAUNCH_ACCEPTANCE.length) {
    throw new Error(
      `完整上线验收总账未全部通过：${failed
        .map((item) => `${item.code} ${item.missingCoverage?.join(",")}`)
        .join("；")}`,
    );
  }
  return true;
}

function coverageKeyComplete(coverage, key) {
  const requirement = REQUIRED_LAUNCH_COVERAGE[key];
  if (!requirement) return false;
  const rows = coverage?.[key];
  if (!Array.isArray(rows)) return false;
  const passed = new Set(
    rows
      .filter(
        (row) =>
          row?.status === "PASSED" &&
          row.observedCode === row.code &&
          row.observedStatus === "PASSED" &&
          hasText(row.evidenceStage) &&
          row.evidenceStage !== "launch-coverage" &&
          hasText(row.evidencePath) &&
          hasText(row.evidenceKey) &&
          hasText(row.observedAt),
      )
      .map((row) => row.code),
  );
  return requirement.codes.every((code) => passed.has(code));
}

function validateDatabaseMigrationEvidence(evidence) {
  const expectedDialectCodes = REQUIRED_LAUNCH_COVERAGE.databaseDialects.codes;
  const dialects = Array.isArray(evidence.dialects) ? evidence.dialects : [];
  const actual = new Set(dialects.map((item) => item?.code));
  if (
    evidence.status !== "PASSED" ||
    evidence.stage !== "DATABASE_MIGRATION_BASELINE" ||
    evidence.schemaSource !==
      "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json" ||
    evidence.generator !== "scripts/db/generate-migrations.mjs" ||
    evidence.generatorCheck?.exitCode !== 0 ||
    evidence.generatorCheck?.checkOnly !== true ||
    evidence.conventionGuard?.exitCode !== 0 ||
    !Number.isInteger(evidence.conventionGuard?.scannedFiles) ||
    evidence.conventionGuard.scannedFiles < expectedDialectCodes.length ||
    dialects.length !== expectedDialectCodes.length ||
    expectedDialectCodes.some((code) => !actual.has(code))
  ) {
    throw new Error("单一模式源五方言迁移基线证据不完整");
  }
  for (const dialect of dialects) {
    if (
      !hasText(dialect.baselineFile) ||
      !dialect.baselineFile.endsWith("/V1__baseline.sql") ||
      !Number.isInteger(dialect.artifactCount) ||
      dialect.artifactCount < 1 ||
      !/^[a-f0-9]{64}$/iu.test(dialect.contentSha256 ?? "")
    ) {
      throw new Error(`五数据库方言 ${dialect.code ?? ""} 迁移产物证据不完整`);
    }
  }
  return {
    schemaSource: evidence.schemaSource,
    dialectCount: expectedDialectCodes.length,
    scannedFiles: evidence.conventionGuard.scannedFiles,
  };
}

function normalizeCoverageClaim(row, context) {
  const code = typeof row === "string" ? row : row?.code;
  const observedStatus =
    typeof row === "string" ? "PASSED" : (row.observedStatus ?? row.status);
  const observedAt =
    typeof row === "string"
      ? (context.evidence.finishedAt ??
        context.evidence.generatedAt ??
        context.evidence.startedAt)
      : (row.observedAt ??
        context.evidence.finishedAt ??
        context.evidence.generatedAt ??
        context.evidence.startedAt);
  return {
    code,
    status: observedStatus === "PASSED" ? "PASSED" : observedStatus,
    evidenceStage: context.stageId,
    evidencePath: context.evidencePath,
    evidenceKey:
      typeof row === "string"
        ? `launchCoverage.${context.key}.${row}`
        : row.evidenceKey,
    observedCode:
      typeof row === "string" ? row : (row.observedCode ?? row.code),
    observedStatus,
    observedAt,
  };
}

function spawnStage(stage) {
  return new Promise((resolve, reject) => {
    const child = spawn(stage.command, stage.args, {
      cwd: stage.cwd,
      env: { ...process.env, ...stage.env },
      stdio: "inherit",
      shell: false,
    });
    child.once("error", reject);
    child.once("close", (exitCode) => resolve({ exitCode }));
  });
}

function readJsonFile(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(`无法读取阶段证据 ${file}：${error.message}`);
  }
}

function normalizeWebBaseUrl(value) {
  const normalized = normalizeHttpsUrl(value, "上线 Web 地址");
  if (!new URL(normalized).pathname.endsWith("/medkernel")) {
    throw new Error("上线 Web 地址必须以 /medkernel 结尾");
  }
  return normalized;
}

function normalizeApiBaseUrl(value) {
  const normalized = normalizeHttpsUrl(value, "上线 API 地址");
  if (!new URL(normalized).pathname.endsWith("/medkernel/api/v1")) {
    throw new Error("上线 API 地址必须以 /medkernel/api/v1 结尾");
  }
  return normalized;
}

function normalizeHttpsUrl(value, label) {
  const normalized = requireText(value, label).replace(/\/+$/u, "");
  const parsed = new URL(normalized);
  if (
    parsed.protocol !== "https:" ||
    parsed.username ||
    parsed.password ||
    parsed.search ||
    parsed.hash
  ) {
    throw new Error(`${label}必须使用不含凭据、查询和片段的 HTTPS 地址`);
  }
  return normalized;
}

function normalizeSource(value) {
  const source = requireText(value, "LAUNCH_SOURCE");
  if (!/^[a-f0-9]{40}$/iu.test(source)) {
    throw new Error("LAUNCH_SOURCE 必须是 40 位提交哈希");
  }
  return source.toLowerCase();
}

function outsideRepo(value, repoRoot, label) {
  const target = path.resolve(requireText(value, label));
  const relative = path.relative(repoRoot, target);
  if (
    relative === "" ||
    (!relative.startsWith("..") && !path.isAbsolute(relative))
  ) {
    throw new Error(`${label}必须位于代码仓库之外`);
  }
  return target;
}

function now(clock) {
  const value = clock ? clock() : new Date();
  return value instanceof Date
    ? value.toISOString()
    : new Date(value).toISOString();
}

function createProgressReporter(onProgress, clock) {
  if (typeof onProgress !== "function") return () => {};
  return (event) => {
    onProgress({
      at: now(clock),
      ...event,
    });
  };
}

function elapsedMs(startedAt, finishedAt) {
  const started = Date.parse(startedAt);
  const finished = Date.parse(finishedAt);
  if (!Number.isFinite(started) || !Number.isFinite(finished)) return 0;
  return Math.max(0, finished - started);
}

function formatDuration(value) {
  const milliseconds = Number.isFinite(value) ? Math.max(0, value) : 0;
  if (milliseconds < 1000) return `${milliseconds}ms`;
  const seconds = Math.round(milliseconds / 100) / 10;
  return `${seconds}s`;
}

function hasCompleteKnowledgeProductionEvidence(item) {
  const technical = item?.technicalEvidence;
  const runtime = item?.runtimeEvidence;
  return (
    hasText(item?.domain) &&
    hasText(item.identityCode) &&
    hasText(item.sourceCode) &&
    item.sourceVersionId != null &&
    hasText(item.sourceContentHash) &&
    hasText(item.jobCode) &&
    hasText(item.modelTaskId) &&
    hasText(item.modelMode) &&
    item.modelMode !== "B0" &&
    hasText(item.modelVersion) &&
    hasText(item.promptVersion) &&
    hasText(item.toolVersion) &&
    Number(item.modelTaskDurationMs) > 0 &&
    hasText(item.candidateRef) &&
    item.classificationId != null &&
    item.versionId != null &&
    hasText(String(item.versionNo ?? "")) &&
    item.status === "ACTIVE" &&
    Number(technical?.gateCount) > 0 &&
    hasText(technical?.triageAction) &&
    technical.triageAction.endsWith("REVIEW") &&
    (technical.shadowStatus === "PASSED" ||
      technical.shadowStatus === "PENDING_REVIEW") &&
    Number(technical.shadowCaseCount) > 0 &&
    item.qualityGateRecordId != null &&
    runtime?.activeVersionId === item.versionId &&
    Number(runtime.citationCount) > 0 &&
    Number(runtime.sourceEvidenceCount) > 0
  );
}

function requireText(value, label) {
  if (!hasText(value)) throw new Error(`${label}不能为空`);
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}
