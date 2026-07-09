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
    Array(7).fill("PASSED"),
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
  assert.deepEqual(evidence.coverage.versionedAssetDedicatedReleaseContractMatrix, [
    {
      code: "TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.versionedAssetDedicatedReleaseContractMatrix.TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
      observedCode: "TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.versionedAssetDedicatedReleaseContractRows.map((item) => item.code),
    ["TERMINOLOGY", "FIELD_CATALOG", "PATHWAY"],
  );
  assert.deepEqual(evidence.coverage.knowledgeSupplyChainEvidenceMatrix, [
    {
      code: "CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
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
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
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
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.implementationGuideEntryCoreActions.IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
      observedCode: "IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.implementationGuideEntryCoreActionRows.map((item) => item.code),
    ["IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY"],
  );
  assert.deepEqual(evidence.coverage.dashboardWorkbenchCoreActions, [
    {
      code: "FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
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
  assert.deepEqual(evidence.coverage.complianceWorkbenchPersonalEntryMatrix, [
    {
      code: "COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey:
        "launchCoverage.complianceWorkbenchPersonalEntryMatrix.COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
      observedCode: "COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(
    evidence.coverage.complianceWorkbenchPersonalEntryRows.map((item) => item.code),
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
      code: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
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
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey: "launchCoverage.thirdPartySystemFamilyConsumerSlices.PHARMACY_REVIEW",
      observedCode: "PHARMACY_REVIEW",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
    {
      code: "PUBLIC_HEALTH_INFECTION_REGULATORY",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
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
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
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
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
      evidenceKey: "launchCoverage.thirdPartySystemFamilyConsumerSlices.LIS_MONITORING_CRITICAL",
      observedCode: "LIS_MONITORING_CRITICAL",
      observedStatus: "PASSED",
      observedAt: "2026-06-22T09:00:00.000Z",
    },
  ]);
  assert.deepEqual(evidence.coverage.diagnosticReportFamilyConsumerMatrix, [
    {
      code: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      status: "PASSED",
      evidenceStage: "browser-e2e",
      evidencePath: "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json",
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

  const missingDiagnosticConsumer = completeStageEvidence();
  missingDiagnosticConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices = missingDiagnosticConsumer[
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

  const missingPharmacyConsumer = completeStageEvidence();
  missingPharmacyConsumer[
    "browser-e2e"
  ].launchCoverage.thirdPartySystemFamilyConsumerSlices = missingPharmacyConsumer[
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

  const missingDiagnosticMatrix = completeStageEvidence();
  missingDiagnosticMatrix[
    "browser-e2e"
  ].launchCoverage.diagnosticReportFamilyConsumerMatrix = missingDiagnosticMatrix[
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
  ].launchCoverage.complianceWorkbenchPersonalEntryRows = missingCompliancePersonal[
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
  ].launchCoverage.implementationGuideEntryCoreActionRows = missingImplementationGuide[
    "browser-e2e"
  ].launchCoverage.implementationGuideEntryCoreActionRows.filter(
    (item) => item.code !== "IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY",
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
    "full-knowledge": {
      status: "PASSED",
      coverage: {
        expectedDomains: [
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
        ],
        publishedDomains: [
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
        ],
      },
      versionLifecycle: {
        v1VersionId: 101,
        v2VersionId: 102,
        rollbackActiveVersionId: 101,
        restoredActiveVersionId: 102,
        finalStatus: "ACTIVE",
      },
    },
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
    "browser-e2e": { stats: { expected: 82, unexpected: 0, flaky: 0 } },
  };
  if (!includeLaunchCoverage) return evidence;
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
  evidence["browser-e2e"].launchCoverage = launchCoverageClaims([
    "productLayers:DATA_INTEROPERABILITY",
    "productLayers:MEDICAL_ASSET",
    "productLayers:RELEASE_GOVERNANCE",
    "productLayers:CLINICAL_EXECUTION",
    "productLayers:DELIVERY_FEEDBACK",
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
    "complianceWorkbenchPersonalEntryMatrix:COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
    "complianceWorkbenchPersonalEntryRows:SECURITY_BASELINE_CONFIG_CHANGE",
    "complianceWorkbenchPersonalEntryRows:AUDIT_EVIDENCE_EXPORT_VERIFY",
    "complianceWorkbenchPersonalEntryRows:NOTIFICATION_READBACK",
    "complianceWorkbenchPersonalEntryRows:NOTIFICATION_SETTINGS_SAVE",
    "complianceWorkbenchPersonalEntryRows:SOURCE_LINEAGE_PROVENANCE_READBACK",
    "thirdPartySystemFamilyConsumerSlices:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    "thirdPartySystemFamilyConsumerSlices:PHARMACY_REVIEW",
    "thirdPartySystemFamilyConsumerSlices:PUBLIC_HEALTH_INFECTION_REGULATORY",
    "thirdPartySystemFamilyConsumerSlices:NURSING_ANESTHESIA_TRANSFUSION_ICU",
    "thirdPartySystemFamilyConsumerSlices:LIS_MONITORING_CRITICAL",
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
