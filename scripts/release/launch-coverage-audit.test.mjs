import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  buildLaunchCoverageEvidence,
  readLaunchCoverageAuditConfig,
} from "./launch-coverage-audit.mjs";

const MANIFEST_PATH = fileURLToPath(
  new URL(
    "../knowledge/manifests/full-knowledge-rehearsal-1.0.0.json",
    import.meta.url,
  ),
);
const SOURCE = "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17";

test("完整覆盖审计配置固定仓库外证据与 40 位来源提交", () => {
  const env = baseEnv();
  const config = readLaunchCoverageAuditConfig(env, {
    repoRoot: "/workspace/medkernel",
  });

  assert.equal(
    config.evidenceRoot,
    "/var/lib/medkernel/evidence/current-launch",
  );
  assert.equal(
    config.outputPath,
    "/var/lib/medkernel/evidence/current-launch/launch-coverage.json",
  );
  assert.equal(config.manifestPath, MANIFEST_PATH);
  assert.equal(config.source, SOURCE);

  env.LAUNCH_COVERAGE_EVIDENCE_PATH =
    "/workspace/medkernel/tmp/launch-coverage.json";
  assert.throws(
    () =>
      readLaunchCoverageAuditConfig(env, { repoRoot: "/workspace/medkernel" }),
    /完整覆盖审计证据路径必须位于代码仓库之外/u,
  );

  env.LAUNCH_COVERAGE_EVIDENCE_PATH =
    "/var/lib/medkernel/evidence/current-launch/launch-coverage.json";
  env.LAUNCH_SOURCE = "1603b5a7";
  assert.throws(
    () =>
      readLaunchCoverageAuditConfig(env, { repoRoot: "/workspace/medkernel" }),
    /40 位提交哈希/u,
  );
});

test("完整覆盖审计复用统一阶段门禁并生成上线范围矩阵", () => {
  const evidence = buildLaunchCoverageEvidence(auditConfig(), {
    readJson: readKnownEvidence(completeStageEvidence()),
    now: () => "2026-06-22T09:00:00.000Z",
  });

  assert.equal(evidence.status, "PASSED");
  assert.deepEqual(
    Object.values(evidence.stageStatus),
    Array(9).fill("PASSED"),
  );
  assert.equal(evidence.coverage.databaseDialects.length, 5);
  assert.equal(evidence.acceptance.length, 15);
  assert.equal(
    evidence.acceptance.find((item) => item.code === "LAUNCH-11")?.status,
    "PASSED",
  );
  assert.deepEqual(
    evidence.coverage.targetEnvironmentRehearsal.map((item) => item.code),
    [
      "BACKUP_RESTORE_BEFORE_CLEAN",
      "CLEAN_DATABASE_V1_ONLY",
      "DEPLOY_CURRENT_ARTIFACT",
      "FULL_FUNCTION_FULL_KNOWLEDGE_REHEARSAL",
      "RESTART_AND_SECOND_RESTORE",
    ],
  );
  assert.equal(
    evidence.acceptance.find((item) => item.code === "LAUNCH-15")?.status,
    "PASSED",
  );
  assert.equal(evidence.coverage.standardPatientResources.length, 13);
  assert.equal(evidence.coverage.versionedAssets.length, 13);
  assert.equal(evidence.coverage.knowledgeDomains.length, 11);
  assert.equal(evidence.coverage.scenarios.length, 41);
  assert.equal(evidence.coverage.stakeholderViews.length, 12);
  assert.equal(evidence.coverage.scenarios.at(-1).evidenceStage, "browser-e2e");
  assert.equal(
    evidence.coverage.scenarios
      .at(-1)
      .evidencePath.endsWith("/e2e/report/results.json"),
    true,
  );
  assert.equal(
    evidence.coverage.scenarios.at(-1).evidenceKey,
    "launchCoverage.scenarios.S40",
  );
  assert.deepEqual(
    evidence.coverage.stakeholderViews.map((item) => item.code),
    [
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
  );
  assert.deepEqual(
    evidence.coverage.versionedAssetDedicatedReleaseContractMatrix,
    [
      {
        code: "TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
        status: "PASSED",
        evidenceStage: "browser-e2e",
        evidencePath:
          "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
        evidenceKey:
          "launchCoverage.versionedAssetDedicatedReleaseContractMatrix.TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
        observedCode:
          "TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
        observedStatus: "PASSED",
        observedAt: "2026-06-22T09:00:00.000Z",
      },
    ],
  );
  assert.deepEqual(
    evidence.coverage.versionedAssetDedicatedReleaseContractRows.map(
      (item) => item.code,
    ),
    ["TERMINOLOGY", "FIELD_CATALOG", "PATHWAY"],
  );
  assert.deepEqual(evidence.coverage.knowledgeSupplyChainEvidenceMatrix, [
    {
      code: "CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.knowledgeSupplyChainEvidenceMatrix.CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE",
      observedCode: "CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.knowledgeSupplyChainEvidenceRows.map((item) => item.code),
    [
      "SOURCE_CONTROL",
      "HUMAN_GOVERNANCE",
      "TERMINOLOGY_SYNC",
      "RUNTIME_LIFECYCLE",
      "LINEAGE_CONSUMERS",
      "SAFETY_BOUNDARY",
    ],
  );
  assert.deepEqual(evidence.coverage.launchReadinessStakeholderMatrix, [
    {
      code: "IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.launchReadinessStakeholderMatrix.IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE",
      observedCode: "IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.launchReadinessStakeholderRows.map((item) => item.code),
    [
      "IT_MANAGER_RUNTIME_DIAGNOSTICS",
      "IMPLEMENTATION_ENGINEER_ONBOARDING_GUIDE",
      "HOSPITAL_EXECUTIVE_QUALITY_OVERVIEW",
    ],
  );
  assert.deepEqual(evidence.coverage.implementationGuideEntryCoreActions, [
    {
      code: "IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.implementationGuideEntryCoreActions.IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
      observedCode: "IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.implementationGuideEntryCoreActionRows.map(
      (item) => item.code,
    ),
    ["IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY"],
  );
  assert.deepEqual(evidence.coverage.dashboardWorkbenchCoreActions, [
    {
      code: "FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.dashboardWorkbenchCoreActions.FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS",
      observedCode: "FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.dashboardWorkbenchCoreActionRows.map((item) => item.code),
    ["PLATFORM_ADMIN", "ENGINE_OPERATOR", "CLINICAL_USER", "AUDITOR"],
  );
  assert.deepEqual(evidence.coverage.menuEntryCoreActions, [
    {
      code: "ALL_34_MENU_ENTRY_CORE_ACTIONS",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.menuEntryCoreActions.ALL_34_MENU_ENTRY_CORE_ACTIONS",
      observedCode: "ALL_34_MENU_ENTRY_CORE_ACTIONS",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.menuEntryCoreActionRows.map((item) => item.code),
    [
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
    ],
  );
  assert.deepEqual(evidence.coverage.complianceWorkbenchPersonalEntryMatrix, [
    {
      code: "COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.complianceWorkbenchPersonalEntryMatrix.COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
      observedCode: "COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.complianceWorkbenchPersonalEntryRows.map(
      (item) => item.code,
    ),
    [
      "SECURITY_BASELINE_CONFIG_CHANGE",
      "AUDIT_EVIDENCE_EXPORT_VERIFY",
      "NOTIFICATION_READBACK",
      "NOTIFICATION_SETTINGS_SAVE",
      "SOURCE_LINEAGE_PROVENANCE_READBACK",
    ],
  );
  assert.deepEqual(evidence.coverage.thirdPartySystemFamilyConsumerSlices, [
    {
      code: "HIS_EMR_CDR",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.HIS_EMR_CDR",
      observedCode: "HIS_EMR_CDR",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      observedCode: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "PHARMACY_REVIEW",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.PHARMACY_REVIEW",
      observedCode: "PHARMACY_REVIEW",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "PUBLIC_HEALTH_INFECTION_REGULATORY",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.PUBLIC_HEALTH_INFECTION_REGULATORY",
      observedCode: "PUBLIC_HEALTH_INFECTION_REGULATORY",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.NURSING_ANESTHESIA_TRANSFUSION_ICU",
      observedCode: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "LIS_MONITORING_CRITICAL",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.LIS_MONITORING_CRITICAL",
      observedCode: "LIS_MONITORING_CRITICAL",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "REGIONAL_REMOTE",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.REGIONAL_REMOTE",
      observedCode: "REGIONAL_REMOTE",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "MEDICAL_RECORD_INSURANCE_PAYMENT",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.MEDICAL_RECORD_INSURANCE_PAYMENT",
      observedCode: "MEDICAL_RECORD_INSURANCE_PAYMENT",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "FOLLOWUP_PATIENT_SERVICE",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.thirdPartySystemFamilyConsumerSlices.FOLLOWUP_PATIENT_SERVICE",
      observedCode: "FOLLOWUP_PATIENT_SERVICE",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.thirdPartySystemFamilyDegradationRows.map(
      (item) => item.code,
    ),
    [
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
  );
  assert.deepEqual(evidence.coverage.diagnosticReportFamilyConsumerMatrix, [
    {
      code: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath:
        "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.diagnosticReportFamilyConsumerMatrix.PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      observedCode: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
});

test("完整覆盖审计拒绝用静态矩阵替代前置阶段逐项证据", () => {
  const stageEvidence = completeStageEvidence({ includeLaunchCoverage: false });

  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(stageEvidence),
      }),
    /缺少前置阶段证据/u,
  );
});

test("完整覆盖审计拒绝用知识域名称替代完整知识生产证据", () => {
  const missingSourceVerification = completeStageEvidence();
  missingSourceVerification["full-knowledge"].sourceVerification = [];
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingSourceVerification),
      }),
    /全知识来源核验/u,
  );

  const missingRuntimeCitation = completeStageEvidence();
  missingRuntimeCitation["full-knowledge"].knowledge = missingRuntimeCitation[
    "full-knowledge"
  ].knowledge.map((item, index) =>
    index === 0
      ? {
          ...item,
          runtimeEvidence: {
            ...item.runtimeEvidence,
            citationCount: 0,
          },
        }
      : item,
  );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingRuntimeCitation),
      }),
    /质量门、影子评测或运行证据/u,
  );
});

test("完整覆盖审计拒绝把单一浏览器角色切片包装成全量覆盖", () => {
  const stageEvidence = completeStageEvidence();
  stageEvidence["browser-e2e"].launchCoverage = launchCoverageClaims([
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
  ]);

  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(stageEvidence),
      }),
    /六层产品能力.*DATA_INTEROPERABILITY.*缺少前置阶段证据/u,
  );
});

test("完整覆盖审计拒绝缺失 S40、Claim 或第三方系统族的逐项证据", () => {
  const missingS40 = completeStageEvidence();
  missingS40["browser-e2e"].launchCoverage.scenarios = missingS40[
    "browser-e2e"
  ].launchCoverage.scenarios.filter((item) => item.code !== "S40");
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingS40),
      }),
    /S0–S40 业务场景.*S40.*缺少前置阶段证据/u,
  );

  const missingS40Degradation = completeStageEvidence();
  missingS40Degradation["browser-e2e"].launchCoverage.scenarioConditionRows =
    missingS40Degradation[
      "browser-e2e"
    ].launchCoverage.scenarioConditionRows.filter(
      (item) => item.code !== "S40__DEGRADATION",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingS40Degradation),
      }),
    /S0–S40 五态演练行.*S40__DEGRADATION.*缺少前置阶段证据/u,
  );

  const missingClaim = completeStageEvidence();
  missingClaim.sandbox.launchCoverage.standardPatientResources =
    missingClaim.sandbox.launchCoverage.standardPatientResources.filter(
      (item) => item.code !== "Claim",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingClaim),
      }),
    /13 类标准患者资源.*Claim.*缺少前置阶段证据/u,
  );

  const missingThirdParty = completeStageEvidence();
  missingThirdParty["browser-e2e"].launchCoverage.thirdPartySystemFamilies =
    missingThirdParty[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilies.filter(
      (item) => item.code !== "MODEL_DIFY_AGENT",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingThirdParty),
      }),
    /全部第三方系统族.*MODEL_DIFY_AGENT.*缺少前置阶段证据/u,
  );

  const missingThirdPartyDegradation = completeStageEvidence();
  missingThirdPartyDegradation[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyDegradationRows =
    missingThirdPartyDegradation[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyDegradationRows.filter(
      (item) => item.code !== "MODEL_DIFY_AGENT",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingThirdPartyDegradation),
      }),
    /第三方系统族断连降级行.*MODEL_DIFY_AGENT.*缺少前置阶段证据/u,
  );

  const missingDiagnosticConsumer = completeStageEvidence();
  missingDiagnosticConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices =
    missingDiagnosticConsumer[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyConsumerSlices.filter(
      (item) => item.code !== "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingDiagnosticConsumer),
      }),
    /第三方系统族真实消费者代表切片.*PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG.*缺少前置阶段证据/u,
  );

  const missingHisEmrCdrConsumer = completeStageEvidence();
  missingHisEmrCdrConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices =
    missingHisEmrCdrConsumer[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyConsumerSlices.filter(
      (item) => item.code !== "HIS_EMR_CDR",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingHisEmrCdrConsumer),
      }),
    /第三方系统族真实消费者代表切片.*HIS_EMR_CDR.*缺少前置阶段证据/u,
  );

  const missingPharmacyConsumer = completeStageEvidence();
  missingPharmacyConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices =
    missingPharmacyConsumer[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyConsumerSlices.filter(
      (item) => item.code !== "PHARMACY_REVIEW",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingPharmacyConsumer),
      }),
    /第三方系统族真实消费者代表切片.*PHARMACY_REVIEW.*缺少前置阶段证据/u,
  );

  const missingNursingAnesthesiaTransfusionConsumer = completeStageEvidence();
  missingNursingAnesthesiaTransfusionConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices =
    missingNursingAnesthesiaTransfusionConsumer[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyConsumerSlices.filter(
      (item) => item.code !== "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingNursingAnesthesiaTransfusionConsumer),
      }),
    /第三方系统族真实消费者代表切片.*NURSING_ANESTHESIA_TRANSFUSION_ICU.*缺少前置阶段证据/u,
  );

  const missingRegionalConsumer = completeStageEvidence();
  missingRegionalConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices =
    missingRegionalConsumer[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyConsumerSlices.filter(
      (item) => item.code !== "REGIONAL_REMOTE",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingRegionalConsumer),
      }),
    /第三方系统族真实消费者代表切片.*REGIONAL_REMOTE.*缺少前置阶段证据/u,
  );

  const missingMedicalRecordInsuranceConsumer = completeStageEvidence();
  missingMedicalRecordInsuranceConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices =
    missingMedicalRecordInsuranceConsumer[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyConsumerSlices.filter(
      (item) => item.code !== "MEDICAL_RECORD_INSURANCE_PAYMENT",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingMedicalRecordInsuranceConsumer),
      }),
    /第三方系统族真实消费者代表切片.*MEDICAL_RECORD_INSURANCE_PAYMENT.*缺少前置阶段证据/u,
  );

  const missingFollowupPatientServiceConsumer = completeStageEvidence();
  missingFollowupPatientServiceConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices =
    missingFollowupPatientServiceConsumer[
      "browser-e2e"
    ].launchCoverage.thirdPartySystemFamilyConsumerSlices.filter(
      (item) => item.code !== "FOLLOWUP_PATIENT_SERVICE",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingFollowupPatientServiceConsumer),
      }),
    /第三方系统族真实消费者代表切片.*FOLLOWUP_PATIENT_SERVICE.*缺少前置阶段证据/u,
  );

  const missingDiagnosticMatrix = completeStageEvidence();
  missingDiagnosticMatrix[
    "browser-e2e"
  ].launchCoverage.diagnosticReportFamilyConsumerMatrix =
    missingDiagnosticMatrix[
      "browser-e2e"
    ].launchCoverage.diagnosticReportFamilyConsumerMatrix.filter(
      (item) => item.code !== "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingDiagnosticMatrix),
      }),
    /五类医技报告族真实消费者矩阵.*PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG.*缺少前置阶段证据/u,
  );

  const missingCompliancePersonal = completeStageEvidence();
  missingCompliancePersonal[
    "browser-e2e"
  ].launchCoverage.complianceWorkbenchPersonalEntryRows =
    missingCompliancePersonal[
      "browser-e2e"
    ].launchCoverage.complianceWorkbenchPersonalEntryRows.filter(
      (item) => item.code !== "NOTIFICATION_SETTINGS_SAVE",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingCompliancePersonal),
      }),
    /合规安全与工作台个人入口强证据行.*NOTIFICATION_SETTINGS_SAVE.*缺少前置阶段证据/u,
  );

  const missingImplementationGuide = completeStageEvidence();
  missingImplementationGuide[
    "browser-e2e"
  ].launchCoverage.implementationGuideEntryCoreActionRows =
    missingImplementationGuide[
      "browser-e2e"
    ].launchCoverage.implementationGuideEntryCoreActionRows.filter(
      (item) =>
        item.code !== "IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingImplementationGuide),
      }),
    /实施与验收入口代表动作行.*IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY.*缺少前置阶段证据/u,
  );

  const missingDashboardWorkbench = completeStageEvidence();
  missingDashboardWorkbench[
    "browser-e2e"
  ].launchCoverage.dashboardWorkbenchCoreActionRows = missingDashboardWorkbench[
    "browser-e2e"
  ].launchCoverage.dashboardWorkbenchCoreActionRows.filter(
    (item) => item.code !== "CLINICAL_USER",
  );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingDashboardWorkbench),
      }),
    /四职责工作台核心动作行.*CLINICAL_USER.*缺少前置阶段证据/u,
  );

  const missingMenuEntry = completeStageEvidence();
  missingMenuEntry["browser-e2e"].launchCoverage.menuEntryCoreActionRows =
    missingMenuEntry[
      "browser-e2e"
    ].launchCoverage.menuEntryCoreActionRows.filter(
      (item) => item.code !== "insurance-audit",
    );
  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(missingMenuEntry),
      }),
    /34 个产品入口真实核心动作行.*insurance-audit.*缺少前置阶段证据/u,
  );
});

test("完整覆盖审计遇到任一前置阶段失败时拒绝生成 PASSED 证据", () => {
  const stageEvidence = completeStageEvidence();
  stageEvidence["browser-e2e"] = {
    stats: { expected: 82, unexpected: 1, flaky: 0 },
  };

  assert.throws(
    () =>
      buildLaunchCoverageEvidence(auditConfig(), {
        readJson: readKnownEvidence(stageEvidence),
      }),
    /前置阶段未全部通过.*browser-e2e/u,
  );
});

function auditConfig() {
  return {
    evidenceRoot: "/var/lib/medkernel/evidence/current-launch",
    outputPath:
      "/var/lib/medkernel/evidence/current-launch/launch-coverage.json",
    manifestPath: MANIFEST_PATH,
    source: SOURCE,
  };
}

function baseEnv() {
  return {
    FULL_SYSTEM_EVIDENCE_ROOT: "/var/lib/medkernel/evidence/current-launch",
    LAUNCH_COVERAGE_EVIDENCE_PATH:
      "/var/lib/medkernel/evidence/current-launch/launch-coverage.json",
    FULL_KNOWLEDGE_MANIFEST_PATH: MANIFEST_PATH,
    LAUNCH_SOURCE: SOURCE,
  };
}

function readKnownEvidence(stageEvidence) {
  const manifest = JSON.parse(readFileSync(MANIFEST_PATH, "utf8"));
  return (_file, stage) => {
    if (stage === "全知识演练清单") return manifest;
    if (!stageEvidence[stage]) throw new Error(`测试缺少阶段证据 ${stage}`);
    return stageEvidence[stage];
  };
}

function completeStageEvidence(options = {}) {
  const includeLaunchCoverage = options.includeLaunchCoverage !== false;
  const evidence = {
    "database-migrations": databaseMigrationEvidence(),
    "account-bootstrap": { status: "PASSED", verifiedAccountCount: 9 },
    "model-provider": {
      status: "PASSED",
      provider: { enabled: true, status: "HEALTHY", code: "ollama-launch" },
      evaluation: {
        totalCases: 3,
        passedCases: 3,
        failedCases: 0,
        status: "PASSED",
      },
    },
    "platform-baseline": {
      status: "PASSED",
      stage: "PLATFORM_BASELINE_BOOTSTRAP",
      operator: { tenantId: "t-1", role: "engine-operator" },
      fieldCatalog: {
        assetType: "FIELD_CATALOG",
        assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
        entryState: "ACTIVE",
        versionId: "field-v1",
        versionNo: "1",
      },
      baseline: { baselineReleaseId: "baseline-1", revisionNo: 1 },
      knowledge: {
        manifestCode: "MEDKERNEL-FULL-KNOWLEDGE-REHEARSAL",
        releaseVersion: "1.0.0",
        requiredCount: 11,
        activeCount: 11,
        missingIdentities: [],
      },
      knowledgeAssets: knowledgeDomains().map((domain) => ({
        assetType: "KNOWLEDGE",
        assetIdentity: `launch.${domain.toLowerCase()}.asset`,
        entryState: "ACTIVE",
        versionId: `knowledge-${domain.toLowerCase()}-v1`,
        versionNo: "V1",
      })),
    },
    sandbox: {
      results: Array.from({ length: 10 }, (_, index) => ({
        ruleCode: `R${index}`,
        result: "PASS",
      })),
      failures: [],
      runtimeBinding: { ready: true, externalSideEffects: false },
    },
    "full-knowledge": fullKnowledgeEvidence(),
    "runtime-resilience": {
      status: "PASSED",
      disabled: {
        providerEnabled: false,
        readinessReady: false,
        modelInvocationAllowed: false,
        blockingRequiredItems: ["MODEL_PROVIDER"],
      },
      b0: { evidenceCount: 17, passedCount: 17, modelRequiredCount: 0 },
      restored: {
        providerEnabled: true,
        providerStatus: "HEALTHY",
        readinessReady: true,
        modelInvocationAllowed: true,
      },
    },
    "target-environment": targetEnvironmentEvidence(),
    "browser-e2e": { stats: { expected: 82, unexpected: 0, flaky: 0 } },
  };
  if (!includeLaunchCoverage) return evidence;
  evidence["database-migrations"].launchCoverage = launchCoverageClaims([
    "databaseMigrationSource:SINGLE_SCHEMA_GENERATOR_CHECK",
    "databaseDialects:POSTGRES",
    "databaseDialects:KINGBASE",
    "databaseDialects:ORACLE",
    "databaseDialects:DM",
    "databaseDialects:H2",
  ]);
  evidence["account-bootstrap"].launchCoverage = launchCoverageClaims([
    "productLayers:FOUNDATION_GOVERNANCE",
    "organizationLevels:PLATFORM",
    "organizationLevels:GROUP",
    "organizationLevels:HOSPITAL",
    "organizationLevels:CAMPUS_OR_MEMBER",
    "organizationLevels:DEPARTMENT",
    "organizationLevels:WARD",
    "organizationLevels:CARE_TEAM",
    "organizationLevels:SPECIALTY_CENTER",
    "organizationLevels:SHARED_CENTER",
  ]);
  evidence["model-provider"].launchCoverage = launchCoverageClaims([
    "modelEnablementSurfaces:SOURCE_DISCOVERY",
    "modelEnablementSurfaces:DOCUMENT_EXTRACT",
    "modelEnablementSurfaces:TERMINOLOGY_MAPPING",
    "modelEnablementSurfaces:RULE_QUALITY",
    "modelEnablementSurfaces:PATHWAY_CONTINUITY",
    "modelEnablementSurfaces:DIAGNOSIS_CDSS",
    "modelEnablementSurfaces:NURSING_COLLABORATION",
    "modelEnablementSurfaces:REPORT_INTERPRETATION",
    "modelEnablementSurfaces:EVALUATION_INSURANCE_RECORD",
    "modelEnablementSurfaces:FOLLOWUP_EDUCATION",
    "modelEnablementSurfaces:OPERATIONS_TESTING",
    "modelEnablementSurfaces:NATURAL_LANGUAGE_ACCESS",
  ]);
  evidence["platform-baseline"].launchCoverage = launchCoverageClaims([
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
  ]);
  evidence["full-knowledge"].launchCoverage = launchCoverageClaims(
    knowledgeDomains().map((code) => `knowledgeDomains:${code}`),
  );
  evidence.sandbox.launchCoverage = launchCoverageClaims([
    "standardPatientResources:Patient",
    "standardPatientResources:AllergyIntolerance",
    "standardPatientResources:Encounter",
    "standardPatientResources:Condition",
    "standardPatientResources:NursingAssessment",
    "standardPatientResources:Observation",
    "standardPatientResources:DiagnosticReport",
    "standardPatientResources:Medication",
    "standardPatientResources:Procedure",
    "standardPatientResources:Document",
    "standardPatientResources:CarePlan",
    "standardPatientResources:FollowUp",
    "standardPatientResources:Claim",
    "serviceCombinations:CLINICAL_RUNTIME",
    "serviceCombinations:SPECIAL_DISEASE_PATHWAY",
  ]);
  evidence["runtime-resilience"].launchCoverage = launchCoverageClaims([
    "deliveryShapes:ENGINE_CORE",
    "serviceCombinations:QUALITY_IMPROVEMENT",
    "serviceCombinations:COMPLIANCE_OPERATIONS",
  ]);
  evidence["target-environment"].launchCoverage = launchCoverageClaims([
    "targetEnvironmentRehearsal:BACKUP_RESTORE_BEFORE_CLEAN",
    "targetEnvironmentRehearsal:CLEAN_DATABASE_V1_ONLY",
    "targetEnvironmentRehearsal:DEPLOY_CURRENT_ARTIFACT",
    "targetEnvironmentRehearsal:FULL_FUNCTION_FULL_KNOWLEDGE_REHEARSAL",
    "targetEnvironmentRehearsal:RESTART_AND_SECOND_RESTORE",
  ]);
  evidence["browser-e2e"].launchCoverage = launchCoverageClaims([
    "productLayers:DATA_INTEROPERABILITY",
    "productLayers:MEDICAL_ASSET",
    "productLayers:RELEASE_GOVERNANCE",
    "productLayers:CLINICAL_EXECUTION",
    "productLayers:DELIVERY_FEEDBACK",
    "standardPatientResourceConsumerMatrix:THIRTEEN_STANDARD_RESOURCES_REPRESENTATIVE",
    "standardPatientResourceRepresentativeRows:Patient",
    "standardPatientResourceRepresentativeRows:AllergyIntolerance",
    "standardPatientResourceRepresentativeRows:Encounter",
    "standardPatientResourceRepresentativeRows:Condition",
    "standardPatientResourceRepresentativeRows:NursingAssessment",
    "standardPatientResourceRepresentativeRows:Observation",
    "standardPatientResourceRepresentativeRows:DiagnosticReport",
    "standardPatientResourceRepresentativeRows:Medication",
    "standardPatientResourceRepresentativeRows:Procedure",
    "standardPatientResourceRepresentativeRows:Document",
    "standardPatientResourceRepresentativeRows:CarePlan",
    "standardPatientResourceRepresentativeRows:FollowUp",
    "standardPatientResourceRepresentativeRows:Claim",
    "versionedAssetSupplyChainMatrix:THIRTEEN_VERSIONED_ASSETS_GAP_AWARE_REPRESENTATIVE",
    "versionedAssetRepresentativeRows:KNOWLEDGE",
    "versionedAssetRepresentativeRows:TERMINOLOGY",
    "versionedAssetRepresentativeRows:RULE",
    "versionedAssetRepresentativeRows:PATHWAY",
    "versionedAssetRepresentativeRows:EVALUATION",
    "versionedAssetRepresentativeRows:FOLLOWUP",
    "versionedAssetRepresentativeRows:FIELD_CATALOG",
    "versionedAssetRepresentativeRows:SAFETY",
    "versionedAssetRepresentativeRows:CDSS_RISK",
    "versionedAssetRepresentativeRows:VALUE_SET",
    "versionedAssetRepresentativeRows:FORMULA",
    "versionedAssetRepresentativeRows:ORDER_SET",
    "versionedAssetRepresentativeRows:ACTION_CARD",
    "versionedAssetRollbackRepresentativeMatrix:GAP_AWARE_RUNTIME_CONSUMER_NEGATIVE_REPRESENTATIVE",
    "versionedAssetRollbackRepresentativeRows:SAFETY",
    "versionedAssetRollbackRepresentativeRows:CDSS_RISK",
    "versionedAssetRollbackRepresentativeRows:VALUE_SET",
    "versionedAssetRollbackRepresentativeRows:FORMULA",
    "versionedAssetRollbackRepresentativeRows:PATHWAY",
    "versionedAssetRollbackRepresentativeRows:ORDER_SET",
    "versionedAssetRollbackRepresentativeRows:EVALUATION",
    "versionedAssetDedicatedReleaseContractMatrix:TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
    "versionedAssetDedicatedReleaseContractRows:TERMINOLOGY",
    "versionedAssetDedicatedReleaseContractRows:FIELD_CATALOG",
    "versionedAssetDedicatedReleaseContractRows:PATHWAY",
    "knowledgeSupplyChainEvidenceMatrix:CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE",
    "knowledgeSupplyChainEvidenceRows:SOURCE_CONTROL",
    "knowledgeSupplyChainEvidenceRows:HUMAN_GOVERNANCE",
    "knowledgeSupplyChainEvidenceRows:TERMINOLOGY_SYNC",
    "knowledgeSupplyChainEvidenceRows:RUNTIME_LIFECYCLE",
    "knowledgeSupplyChainEvidenceRows:LINEAGE_CONSUMERS",
    "knowledgeSupplyChainEvidenceRows:SAFETY_BOUNDARY",
    "launchReadinessStakeholderMatrix:IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE",
    "launchReadinessStakeholderRows:IT_MANAGER_RUNTIME_DIAGNOSTICS",
    "launchReadinessStakeholderRows:IMPLEMENTATION_ENGINEER_ONBOARDING_GUIDE",
    "launchReadinessStakeholderRows:HOSPITAL_EXECUTIVE_QUALITY_OVERVIEW",
    "implementationGuideEntryCoreActions:IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
    "implementationGuideEntryCoreActionRows:IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY",
    "dashboardWorkbenchCoreActions:FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS",
    "dashboardWorkbenchCoreActionRows:PLATFORM_ADMIN",
    "dashboardWorkbenchCoreActionRows:ENGINE_OPERATOR",
    "dashboardWorkbenchCoreActionRows:CLINICAL_USER",
    "dashboardWorkbenchCoreActionRows:AUDITOR",
    "roleRepresentativeCoreActions:FOUR_ROLE_PRIMARY_ACTIONS",
    "entryRepresentativeCoreActions:SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE",
    "platformAdminEntryCoreActions:FOUR_PLATFORM_ADMIN_P0_ENTRY_ACTIONS",
    "platformAdminP1EntryCoreActions:RUNTIME_DIAGNOSTICS_DOMESTIC_CHECK",
    "clinicalEntryCoreActions:CLINICAL_COLLABORATION_CORE_ACTIONS_REPRESENTATIVE",
    "qualityManagementEntryCoreActions:QUALITY_MANAGEMENT_CORE_ACTIONS_REPRESENTATIVE",
    "knowledgeOperationsAssetEntryCoreActions:KNOWLEDGE_OPERATIONS_ASSET_ENTRY_FAMILY_REPRESENTATIVE",
    "menuEntryCoreActions:ALL_34_MENU_ENTRY_CORE_ACTIONS",
    "menuEntryCoreActionRows:workbench",
    "menuEntryCoreActionRows:tenant-onboarding",
    "menuEntryCoreActionRows:admin-users",
    "menuEntryCoreActionRows:identity-bindings",
    "menuEntryCoreActionRows:admin-audit",
    "menuEntryCoreActionRows:security-baseline",
    "menuEntryCoreActionRows:implementation-guide",
    "menuEntryCoreActionRows:adapter-hub",
    "menuEntryCoreActionRows:system-providers",
    "menuEntryCoreActionRows:runtime-diagnostics",
    "menuEntryCoreActionRows:domestic-check",
    "menuEntryCoreActionRows:notifications",
    "menuEntryCoreActionRows:notification-settings",
    "menuEntryCoreActionRows:knowledge-governance",
    "menuEntryCoreActionRows:runtime-releases",
    "menuEntryCoreActionRows:institution-knowledge",
    "menuEntryCoreActionRows:diagnosis-knowledge",
    "menuEntryCoreActionRows:terminology-mapping",
    "menuEntryCoreActionRows:rule-definitions",
    "menuEntryCoreActionRows:pathway-templates",
    "menuEntryCoreActionRows:provenance",
    "menuEntryCoreActionRows:graph-explore",
    "menuEntryCoreActionRows:knowledge-production",
    "menuEntryCoreActionRows:ai-workflows",
    "menuEntryCoreActionRows:clinical-followup",
    "menuEntryCoreActionRows:sandbox",
    "menuEntryCoreActionRows:qc-dashboard",
    "menuEntryCoreActionRows:qc-alerts",
    "menuEntryCoreActionRows:insurance-audit",
    "menuEntryCoreActionRows:qc-eval-sets",
    "menuEntryCoreActionRows:mpi",
    "menuEntryCoreActionRows:patient-pathways",
    "menuEntryCoreActionRows:cdss-fatigue",
    "menuEntryCoreActionRows:workflow-todos",
    "complianceWorkbenchPersonalEntryMatrix:COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
    "complianceWorkbenchPersonalEntryRows:SECURITY_BASELINE_CONFIG_CHANGE",
    "complianceWorkbenchPersonalEntryRows:AUDIT_EVIDENCE_EXPORT_VERIFY",
    "complianceWorkbenchPersonalEntryRows:NOTIFICATION_READBACK",
    "complianceWorkbenchPersonalEntryRows:NOTIFICATION_SETTINGS_SAVE",
    "complianceWorkbenchPersonalEntryRows:SOURCE_LINEAGE_PROVENANCE_READBACK",
    "thirdPartySystemFamilyConsumerSlices:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    "thirdPartySystemFamilyConsumerSlices:HIS_EMR_CDR",
    "thirdPartySystemFamilyConsumerSlices:PHARMACY_REVIEW",
    "thirdPartySystemFamilyConsumerSlices:PUBLIC_HEALTH_INFECTION_REGULATORY",
    "thirdPartySystemFamilyConsumerSlices:NURSING_ANESTHESIA_TRANSFUSION_ICU",
    "thirdPartySystemFamilyConsumerSlices:LIS_MONITORING_CRITICAL",
    "thirdPartySystemFamilyConsumerSlices:REGIONAL_REMOTE",
    "thirdPartySystemFamilyConsumerSlices:MEDICAL_RECORD_INSURANCE_PAYMENT",
    "thirdPartySystemFamilyConsumerSlices:FOLLOWUP_PATIENT_SERVICE",
    "diagnosticReportFamilyConsumerMatrix:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    "semanticFamilies:DISEASE_DIAGNOSIS",
    "semanticFamilies:SYMPTOM_RISK",
    "semanticFamilies:DIAGNOSTIC_REPORT",
    "semanticFamilies:MEDICATION_THERAPY",
    "semanticFamilies:SURGERY_TECHNOLOGY",
    "semanticFamilies:DEVICE_CONSUMABLE",
    "semanticFamilies:GUIDELINE_EVIDENCE",
    "semanticFamilies:SCALE_FORMULA",
    "semanticFamilies:NURSING",
    "semanticFamilies:PATHWAY_CONTINUITY",
    "semanticFamilies:MEDICAL_RECORD_INSURANCE",
    "semanticFamilies:INFECTION_PUBLIC_HEALTH",
    "semanticFamilies:COMPREHENSIVE_CARE",
    "semanticFamilies:TCM",
    "semanticFamilies:QUALITY_REGULATION",
    "semanticFamilies:SOURCE_VALIDITY",
    "specialtyDomains:CLINICAL_SPECIALTIES",
    "specialtyDomains:NURSING",
    "specialtyDomains:MEDICAL_TECHNOLOGY",
    "specialtyDomains:PHARMACY",
    "specialtyDomains:SURGERY_ANESTHESIA_TRANSFUSION",
    "specialtyDomains:EMERGENCY_CRITICAL_CARE",
    "specialtyDomains:SPECIAL_POPULATIONS",
    "specialtyDomains:ONCOLOGY_DIALYSIS_TRANSPLANT",
    "specialtyDomains:REHAB_NUTRITION_PAIN_PALLIATIVE",
    "specialtyDomains:INFECTION_PUBLIC_HEALTH",
    "specialtyDomains:TCM_INTEGRATIVE",
    "specialtyDomains:DENTAL_ENT_DERMATOLOGY",
    "specialtyDomains:INSURANCE_RECORD_QUALITY",
    "specialtyDomains:RWD_RESEARCH",
    "specialtyDomains:PRIMARY_REGIONAL_REMOTE",
    ...Array.from({ length: 41 }, (_, index) => `scenarios:S${index}`),
    ...Array.from({ length: 41 }, (_, index) => `S${index}`).flatMap(
      (scenario) =>
        ["NORMAL", "ABNORMAL", "MISSING_DATA", "HIGH_RISK", "DEGRADATION"].map(
          (condition) => `scenarioConditionRows:${scenario}__${condition}`,
        ),
    ),
    "deliveryShapes:MANAGEMENT_WORKSPACE",
    "deliveryShapes:EMBEDDED_COMPONENT",
    "deliveryShapes:API_EVENT",
    "deliveryShapes:OFFLINE_DELIVERY",
    "serviceCombinations:ONBOARDING_INTEGRATION",
    "serviceCombinations:THIRD_PARTY_INTERFACE",
    "serviceCombinations:PROFESSIONAL_COLLABORATION",
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
    "thirdPartySystemFamilyDegradationRows:HIS_EMR_CDR",
    "thirdPartySystemFamilyDegradationRows:LIS_MONITORING_CRITICAL",
    "thirdPartySystemFamilyDegradationRows:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    "thirdPartySystemFamilyDegradationRows:PHARMACY_REVIEW",
    "thirdPartySystemFamilyDegradationRows:NURSING_ANESTHESIA_TRANSFUSION_ICU",
    "thirdPartySystemFamilyDegradationRows:MEDICAL_RECORD_INSURANCE_PAYMENT",
    "thirdPartySystemFamilyDegradationRows:PUBLIC_HEALTH_INFECTION_REGULATORY",
    "thirdPartySystemFamilyDegradationRows:FOLLOWUP_PATIENT_SERVICE",
    "thirdPartySystemFamilyDegradationRows:CA_OIDC_SSO_HR",
    "thirdPartySystemFamilyDegradationRows:REGIONAL_REMOTE",
    "thirdPartySystemFamilyDegradationRows:SPD_UDI_DEVICE",
    "thirdPartySystemFamilyDegradationRows:RESEARCH_ETHICS_DATA",
    "thirdPartySystemFamilyDegradationRows:MODEL_DIFY_AGENT",
    "specialDiseaseStages:SCREENING_TRIAGE",
    "specialDiseaseStages:DIAGNOSIS_DIFFERENTIAL",
    "specialDiseaseStages:RISK_STRATIFICATION",
    "specialDiseaseStages:TREATMENT_DECISION",
    "specialDiseaseStages:EXECUTION_CANDIDATE",
    "specialDiseaseStages:MONITORING_WARNING",
    "specialDiseaseStages:DISCHARGE_REFERRAL",
    "specialDiseaseStages:REHAB_EDUCATION_FOLLOWUP",
    "specialDiseaseStages:OUTCOME_EVALUATION",
    "specialDiseaseStages:QUALITY_ITERATION",
  ]);
  return evidence;
}

function databaseMigrationEvidence() {
  const dialects = ["POSTGRES", "KINGBASE", "ORACLE", "DM", "H2"];
  return {
    status: "PASSED",
    stage: "DATABASE_MIGRATION_BASELINE",
    schemaSource:
      "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json",
    generator: "scripts/db/generate-migrations.mjs",
    generatorCheck: { exitCode: 0, checkOnly: true },
    conventionGuard: { exitCode: 0, scannedFiles: 5 },
    dialects: dialects.map((code) => ({
      code,
      baselineFile: `medkernel-backend/src/main/resources/db/migration/${code.toLowerCase()}/V1__baseline.sql`,
      artifactCount: 1,
      contentSha256: "c".repeat(64),
    })),
  };
}

function targetEnvironmentEvidence() {
  return {
    schemaVersion: "1.0.0",
    status: "PASSED",
    stage: "TARGET_ENVIRONMENT_REHEARSAL",
    environment: {
      host: "193.112.107.134",
      webBaseUrl: "https://193.112.107.134/medkernel",
      apiBaseUrl: "https://193.112.107.134/medkernel/api/v1",
      source: SOURCE,
    },
    checks: [
      {
        code: "BACKUP_RESTORE_BEFORE_CLEAN",
        status: "PASSED",
        evidenceRef:
          "/zoesoft/medkernel-data/evidence/backup-before-clean.json",
        checksumSha256: "d".repeat(64),
      },
      {
        code: "CLEAN_DATABASE_V1_ONLY",
        status: "PASSED",
        evidenceRef: "/zoesoft/medkernel-data/evidence/clean-v1.json",
        checksumSha256: "e".repeat(64),
      },
      {
        code: "DEPLOY_CURRENT_ARTIFACT",
        status: "PASSED",
        evidenceRef: "/zoesoft/medkernel-data/evidence/deploy-current.json",
        checksumSha256: "f".repeat(64),
      },
      {
        code: "FULL_FUNCTION_FULL_KNOWLEDGE_REHEARSAL",
        status: "PASSED",
        evidenceRef: "/zoesoft/medkernel-data/evidence/full-system.json",
        checksumSha256: "1".repeat(64),
      },
      {
        code: "RESTART_AND_SECOND_RESTORE",
        status: "PASSED",
        evidenceRef: "/zoesoft/medkernel-data/evidence/restart-restore.json",
        checksumSha256: "2".repeat(64),
      },
    ],
    destructiveConfirmed: true,
    patientDataExported: false,
    credentialsInEvidence: false,
  };
}

function launchCoverageClaims(entries) {
  const claims = {};
  for (const entry of entries) {
    const [key, code] = entry.split(":");
    claims[key] ??= [];
    claims[key].push({
      code,
      status: "PASSED",
      evidenceKey: `launchCoverage.${key}.${code}`,
      observedAt: "2026-06-22T09:00:00.000Z",
    });
  }
  return claims;
}

function fullKnowledgeEvidence() {
  const domains = knowledgeDomains();
  return {
    status: "PASSED",
    coverage: {
      expectedDomains: domains,
      publishedDomains: domains,
      structuralTemplatesObserved: 11,
    },
    sourceVerification: domains.map((domain) => ({
      domain,
      status: "VERIFIED",
      sourceUrl: `https://example.org/${domain.toLowerCase()}`,
      httpStatus: 200,
      contentSha256: "a".repeat(64),
      matchedTerms: [domain],
    })),
    knowledge: domains.map((domain, index) => ({
      domain,
      identityCode: `launch.${domain.toLowerCase()}.asset`,
      sourceCode: `SOURCE-${domain}`,
      sourceVersionId: 100 + index,
      sourceContentHash: "b".repeat(64),
      jobCode: `job-${index + 1}`,
      modelTaskId: `task-${index + 1}`,
      modelMode: "LOCAL_MODEL",
      modelVersion: "medkernel-qwen25:1.5b-v1",
      promptVersion: "prompt-v1",
      toolVersion: "tool-v1",
      modelTaskDurationMs: 1000 + index,
      candidateRef: `kv:${index + 1}:1`,
      classificationId: 200 + index,
      versionId: 1000 + index,
      versionNo: "1",
      status: "ACTIVE",
      technicalEvidence: {
        gateCount: 3,
        triageAction: "STANDARD_REVIEW",
        shadowStatus: "PASSED",
        shadowCaseCount: 5,
      },
      qualityGateRecordId: 300 + index,
      runtimeEvidence: {
        activeVersionId: 1000 + index,
        citationCount: 1,
        sourceEvidenceCount: 1,
      },
    })),
    versionLifecycle: {
      identityCode: "launch.guideline.governance-boundary",
      v1VersionId: 1000,
      v2VersionId: 2000,
      rollbackActiveVersionId: 1000,
      restoredActiveVersionId: 2000,
      finalStatus: "ACTIVE",
      v2ModelTask: {
        domain: "GUIDELINE",
        phase: "V2",
        modelTaskId: "task-v2",
        modelTaskDurationMs: 1200,
      },
    },
    observability: {
      totalDomains: 11,
      completedDomains: 11,
      remainingDomains: 0,
      modelTasks: [
        ...domains.map((domain, index) => ({
          domain,
          phase: "V1",
          modelTaskId: `task-${index + 1}`,
          modelTaskDurationMs: 1000 + index,
        })),
        {
          domain: "GUIDELINE",
          phase: "V2",
          modelTaskId: "task-v2",
          modelTaskDurationMs: 1200,
        },
      ],
    },
    safety: {
      containsCredentials: false,
      containsPatientData: false,
      clinicalActionGenerated: false,
      automatedOrderGenerated: false,
      mfaRequired: false,
    },
  };
}

function knowledgeDomains() {
  return [
    "GUIDELINE",
    "DRUG",
    "PATHWAY_KNOWLEDGE",
    "NURSING",
    "DIAGNOSTIC_ITEM",
    "TCM",
    "PROTOCOL",
    "POLICY",
    "LITERATURE",
    "OTHER",
    "DIAGNOSIS",
  ];
}
