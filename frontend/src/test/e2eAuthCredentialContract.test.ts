import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";

import type * as AuthSupport from "../../e2e/support/auth.ts";

const runtimeAssetTypes = [
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
] as const;

const runtimeAssetIdentities = {
  KNOWLEDGE: "plat:diagnostic_item:lab-potassium",
  TERMINOLOGY: "TERMINOLOGY.LOCAL.REHEARSAL.BASELINE",
  RULE: "RULE.LOCAL.REHEARSAL.BASELINE",
  PATHWAY: "PATHWAY.LOCAL.REHEARSAL.BASELINE",
  EVALUATION: "EVAL.LOCAL.REHEARSAL.BASELINE",
  FOLLOWUP: "FOLLOWUP.LOCAL.REHEARSAL.BASELINE",
  FIELD_CATALOG: "FIELD.CATALOG.CLINICAL_CONTEXT",
  SAFETY: "SAFETY.RDL-LOCAL-REHEARSAL",
  CDSS_RISK: "CDSS.RISK.MATRIX",
  VALUE_SET: "VALUE_SET.LOCAL.REHEARSAL.BASELINE",
  FORMULA: "FORMULA.LOCAL.REHEARSAL.BASELINE",
  ORDER_SET: "ORDER_SET.LOCAL.REHEARSAL.BASELINE",
  ACTION_CARD: "ACTION_CARD.LOCAL.REHEARSAL.BASELINE",
} as const satisfies Record<(typeof runtimeAssetTypes)[number], string>;
const reportInterpretationActionCardIdentity = "ACTION_CARD.REPORT.CRITICAL_VALUE";
const runtimeAssetClosure = [
  ...runtimeAssetTypes.map((assetType) => ({
    assetType,
    assetIdentity: runtimeAssetIdentities[assetType],
  })),
  {
    assetType: "ACTION_CARD",
    assetIdentity: reportInterpretationActionCardIdentity,
  },
] as const;

const envSnapshot = {
  E2E_API_BASE_URL: process.env.E2E_API_BASE_URL,
  E2E_BASE_URL: process.env.E2E_BASE_URL,
  E2E_LOCAL_REHEARSAL_TENANT_ID: process.env.E2E_LOCAL_REHEARSAL_TENANT_ID,
  E2E_ROLE_CREDENTIALS_FILE: process.env.E2E_ROLE_CREDENTIALS_FILE,
};

let tempDir: string | null = null;

afterEach(() => {
  vi.resetModules();
  if (tempDir) {
    rmSync(tempDir, { recursive: true, force: true });
    tempDir = null;
  }
  restoreEnv("E2E_API_BASE_URL", envSnapshot.E2E_API_BASE_URL);
  restoreEnv("E2E_BASE_URL", envSnapshot.E2E_BASE_URL);
  restoreEnv("E2E_LOCAL_REHEARSAL_TENANT_ID", envSnapshot.E2E_LOCAL_REHEARSAL_TENANT_ID);
  restoreEnv("E2E_ROLE_CREDENTIALS_FILE", envSnapshot.E2E_ROLE_CREDENTIALS_FILE);
});

describe("E2E credential contract", () => {
  it("loads canonical platform account credentials during auth support module initialization", async () => {
    tempDir = mkdtempSync(join(tmpdir(), "medkernel-e2e-auth-"));
    const credentialsPath = join(tempDir, "current-launch.json");
    writeFileSync(
      credentialsPath,
      JSON.stringify({
        schemaVersion: "1.0.0",
        status: "READY",
        platform: {
          tenantId: "t-1",
          accounts: {
            "platform-admin": account("platform-admin", "t-1", "platform"),
            "engine-operator": account("engine-operator", "t-1", "platform"),
            "clinical-user": account("clinical-user", "t-1", "platform"),
            auditor: account("auditor", "t-1", "platform"),
          },
        },
        rehearsal: {
          tenantId: "t-rehearsal",
          accounts: {
            "platform-admin": account("platform-admin", "t-rehearsal", "rehearsal"),
            "engine-operator": account("engine-operator", "t-rehearsal", "rehearsal"),
            "clinical-user": account("clinical-user", "t-rehearsal", "rehearsal"),
            auditor: account("auditor", "t-rehearsal", "rehearsal"),
          },
        },
      }),
      "utf8",
    );
    process.env.E2E_API_BASE_URL = "https://127.0.0.1/medkernel/api/v1";
    process.env.E2E_BASE_URL = "https://193.112.107.134/medkernel";
    process.env.E2E_ROLE_CREDENTIALS_FILE = credentialsPath;

    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    expect(auth.roleAccounts).toEqual([
      "platform-admin",
      "engine-operator",
      "clinical-user",
      "auditor",
    ]);
    expect(auth.stablePassword("engine-operator")).toBe("secret-rehearsal-engine-operator");
    expect(auth.stablePassword("engine-operator", "platform")).toBe(
      "secret-platform-engine-operator",
    );
    expect(auth.resolveFrontendApiBase("http://localhost:5173")).toBe(
      "http://localhost:5173/medkernel/api/v1",
    );
    expect(auth.resolveFrontendApiBase("https://193.112.107.134/medkernel")).toBe(
      "https://193.112.107.134/medkernel/api/v1",
    );
    expect(auth.appPath("/dashboard?e2e-session-refresh=clinical-user")).toBe(
      "/medkernel/dashboard?e2e-session-refresh=clinical-user",
    );
  });

  it("keeps local full-role frontdesk rehearsal out of the platform source tenant", async () => {
    delete process.env.E2E_ROLE_CREDENTIALS_FILE;
    delete process.env.E2E_LOCAL_REHEARSAL_TENANT_ID;
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    process.env.E2E_BASE_URL = "http://localhost:5173";
    vi.resetModules();

    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    expect(auth.tenantId).toBe("t-1");
    expect(auth.resolvedTenantIdFor("platform-admin")).toBe("t-e2e-rehearsal-local");
    expect(auth.resolvedTenantIdFor("clinical-user")).toBe("t-e2e-rehearsal-local");
    expect(auth.resolvedTenantIdFor("engine-operator", "platform")).toBe("t-1");
  });

  it("mirrors secure backend cookies only as local proxy cookies for HTTP frontdesk rehearsal", async () => {
    process.env.E2E_API_BASE_URL = "https://127.0.0.1/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const cookie = auth.parseSetCookieForLocalProxy(
      "mk_access=jwt-value; Path=/medkernel; Max-Age=1800; Secure; HttpOnly; SameSite=Strict",
      "http://127.0.0.1:5173",
    );

    expect(cookie).toMatchObject({
      name: "mk_access",
      value: "jwt-value",
      url: "http://127.0.0.1:5173",
      secure: false,
      httpOnly: true,
      sameSite: "Strict",
    });
    expect(
      auth.parseSetCookieForLocalProxy(
        "mk_access=jwt-value; Path=/medkernel; HttpOnly; SameSite=Strict",
        "http://127.0.0.1:5173",
      ),
    ).toBeNull();
  });

  it("exports the shared TOTP calculator for frontdesk MFA rehearsal", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;
    const originalNow = Date.now;
    try {
      Date.now = () => 59_000;

      expect(auth.totp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).toBe("287082");
    } finally {
      Date.now = originalNow;
    }
  });

  it("collects all active platform baseline assets required by local runtime rehearsal", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const baselineItems: Array<{
      assetType: string;
      assetIdentity: string;
      versionId: string;
      versionNo: string;
      contentHash: string;
      entryState: string;
    }> = [
      ...runtimeAssetClosure.map((asset, index) => ({
        assetType: asset.assetType,
        assetIdentity: asset.assetIdentity,
        versionId: runtimeAssetVersionId(asset.assetIdentity),
        versionNo: `V${index + 1}`,
        contentHash: String(index + 1)
          .repeat(64)
          .slice(0, 64),
        entryState: "ACTIVE",
      })),
      {
        assetType: "RULE",
        assetIdentity: "RULE.DISABLED",
        versionId: "rule-disabled",
        versionNo: "V0",
        contentHash: "0".repeat(64),
        entryState: "DISABLED",
      },
    ];
    const baseline = auth.resolveBaselineRuntimeAssets({
      release: { baselineReleaseId: "baseline-1" },
      items: baselineItems,
    });

    expect(baseline).toEqual({
      baselineReleaseId: "baseline-1",
      activeAssets: runtimeAssetClosure.map((asset) => ({
        assetType: asset.assetType,
        assetIdentity: asset.assetIdentity,
        versionId: null,
      })),
      activeAssetVersions: runtimeAssetClosure.map((asset, index) => ({
        assetType: asset.assetType,
        assetIdentity: asset.assetIdentity,
        versionId: runtimeAssetVersionId(asset.assetIdentity),
        versionNo: `V${index + 1}`,
        contentHash: String(index + 1)
          .repeat(64)
          .slice(0, 64),
        entryState: "ACTIVE",
      })),
    });
    expect(auth.runtimeAssetsCoverRequiredTypes(baseline.activeAssets)).toBe(true);
  });

  it("requires the platform baseline evaluation projection to be active before reusing rehearsal runtime", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    expect(
      auth.platformEvaluationProjectionReadyForRehearsal({
        items: [
          {
            indicatorCode: runtimeAssetIdentities.EVALUATION,
            versionNo: 1,
            status: "DRAFT",
          },
        ],
      }),
    ).toBe(false);
    expect(
      auth.platformEvaluationProjectionReadyForRehearsal({
        items: [
          {
            indicatorCode: runtimeAssetIdentities.EVALUATION,
            versionNo: 1,
            status: "ACTIVE",
          },
        ],
      }),
    ).toBe(true);

    const source = readFileSync("e2e/support/auth.ts", "utf8");
    expect(source).toContain("platformEvaluationProjectionReady(page)");
    expect(source).toContain("平台标准版本评价指标投影未激活");
  });

  it("requires hospital runtime rehearsal to self-heal when current release has no active field catalog or rule", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    expect(
      auth.hospitalRuntimeCoversRequiredAssets({
        release: { releaseId: "hospital-release-empty" },
        items: [
          {
            assetType: "FIELD_CATALOG",
            assetIdentity: "CLINICAL_CONTEXT_IDENTITY",
            versionId: "field-v1",
            entryState: "DISABLED",
          },
          {
            assetType: "RULE",
            assetIdentity: "RULE.CLINICAL.RECOMMENDATION",
            versionId: "rule-v1",
            entryState: "DISABLED",
          },
        ],
      }),
    ).toEqual({
      releaseId: "hospital-release-empty",
      platformBaselineReleaseId: null,
      ready: false,
    });

    expect(
      auth.hospitalRuntimeCoversRequiredAssets({
        release: { releaseId: "hospital-release-ready" },
        items: runtimeAssetClosure.map((asset) => ({
          assetType: asset.assetType,
          assetIdentity: asset.assetIdentity,
          versionId: runtimeAssetVersionId(asset.assetIdentity),
          entryState: "ACTIVE",
        })),
      }),
    ).toEqual({
      releaseId: "hospital-release-ready",
      platformBaselineReleaseId: null,
      ready: true,
    });
  });

  it("requires diagnostic-item knowledge for report interpretation runtime rehearsal", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    expect(auth.missingRuntimeCandidateTypes([])).toEqual([...runtimeAssetTypes]);
    expect(
      auth.hospitalRuntimeCoversRequiredAssets({
        release: { releaseId: "hospital-release-no-knowledge" },
        items: [
          {
            assetType: "FIELD_CATALOG",
            assetIdentity: "CLINICAL_CONTEXT_IDENTITY",
            versionId: "field-v1",
            entryState: "ACTIVE",
          },
          {
            assetType: "RULE",
            assetIdentity: "RULE.CLINICAL.RECOMMENDATION",
            versionId: "rule-v1",
            entryState: "ACTIVE",
          },
        ],
      }),
    ).toEqual({
      releaseId: "hospital-release-no-knowledge",
      platformBaselineReleaseId: null,
      ready: false,
    });
  });

  it("requires the report interpretation action card in the shared local runtime rehearsal closure", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const baselineOnlyCard = runtimeAssetTypes.map((assetType) => ({
      assetType,
      assetIdentity: runtimeAssetIdentities[assetType],
      versionId: `${assetType.toLowerCase()}-v1`,
      entryState: "ACTIVE",
    }));
    const reportInterpretationActionCard = {
      assetType: "ACTION_CARD" as const,
      assetIdentity: reportInterpretationActionCardIdentity,
      versionId: "action-card-report-critical-v1",
    };

    expect(auth.missingRuntimeCandidateTypes(baselineOnlyCard)).toEqual(["ACTION_CARD"]);
    expect(auth.runtimeAssetsCoverRequiredTypes(baselineOnlyCard)).toBe(false);
    expect(
      auth.hospitalRuntimeCoversRequiredAssets({
        release: { releaseId: "hospital-release-without-report-card" },
        items: baselineOnlyCard,
      }),
    ).toEqual({
      releaseId: "hospital-release-without-report-card",
      platformBaselineReleaseId: null,
      ready: false,
    });

    expect(
      auth.runtimeAssetsCoverRequiredTypes([...baselineOnlyCard, reportInterpretationActionCard]),
    ).toBe(true);
  });

  it("requires local runtime self-heal to pass the page context when creating action-card candidates", () => {
    const source = readFileSync("e2e/support/auth.ts", "utf8");

    expect(source).toContain("async function ensurePlatformActionCardCandidates(page: Page");
    expect(source).toContain("await ensurePlatformActionCardCandidates(\n      page,");
  });

  it("requires the report interpretation action card bootstrap to use a backend-supported action code", () => {
    const source = readFileSync("e2e/support/auth.ts", "utf8");
    const reportCardContent = source.slice(
      source.indexOf("function platformReportInterpretationActionCardContent()"),
      source.indexOf("async function ensurePlatformPathwayCandidate"),
    );

    expect(reportCardContent).toContain('actionCode: "REMIND"');
    expect(reportCardContent).not.toContain("REPORT_CRITICAL_VALUE_REVIEW");
  });

  it("requires report interpretation E2E to prove runtime knowledge consumption through the frontdesk chain", () => {
    const source = readFileSync("e2e/stakeholder-view-rehearsal.spec.ts", "utf8");

    expect(source).toContain("assertReportInterpretationUsesSnapshotRuntimeKnowledge");
    expect(source).toContain("plat:diagnostic_item:lab-potassium");
    expect(source).toContain("snapshot.runtimeReleaseId");
    expect(source).toContain("interpretation?.runtimeReleaseId");
    expect(source).toContain("reportInterpretationKnowledgeIdentity");
    expect(source).toContain("sourceVersionId");
    expect(source).toContain("versionNo");
    expect(source).toContain("summary");
    expect(source).not.toContain("assertCurrentRuntimeKnowledgeForReportInterpretation");
    expect(source).not.toContain("/engine/integration/knowledge-runtime/runtime-release/current");
  });

  it("requires stakeholder-view rehearsal to attach launch readiness runtime records", () => {
    const source = readFileSync("e2e/stakeholder-view-rehearsal.spec.ts", "utf8");
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");

    expect(source).toContain("stakeholder-view-runtime-records");
    expect(source).toContain("attachRuntimeRecords");
    expect(source).toContain("IT_MANAGER");
    expect(source).toContain('role: "platform-admin"');
    expect(source).toContain('path: "/system/runtime-diagnostics"');
    expect(source).toContain("IMPLEMENTATION_ENGINEER");
    expect(source).toContain('path: "/onboarding/guide"');
    expect(source).toContain("HOSPITAL_EXECUTIVE");
    expect(source).toContain('role: "engine-operator"');
    expect(source).toContain('path: "/qc/dashboard"');
    expect(source).toContain("ADAPTER_QUALITY_REPORT");
    expect(source).toContain("QUALITY_DRILLDOWN");
    expect(source).toContain("record.browserErrors");
    expect(source).toContain("record.serverErrors");
    expect(source).toContain("record.networkFailures");
    expect(source).toContain("/engine/integration/data-quality/reports");
    expect(source).toContain("/engine/quality/dashboard/drilldown");
    expect(source).toContain("selectSnapshotByBackendVerifiedFilter");
    expect(source).toContain("snapshot.snapshotId");
    expect(source).toContain("pageItems(await responseData(snapshots))");
    expect(source).toContain("formatClinicalDateTimeForE2e");
    expect(source).toContain("建立的${noun}");
    expect(source).not.toContain("选择第 1 个临床快照");
    expect(source).not.toContain("选择第 1 个随访上下文快照");
    expect(coverageParser).toContain(
      "launchReadinessStakeholderMatrix:IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE",
    );
    expect(coverageParser).toContain("stakeholder-view-runtime-records");
    expect(coverageParser).toContain("IT_MANAGER_RUNTIME_DIAGNOSTICS");
    expect(coverageParser).toContain("IMPLEMENTATION_ENGINEER_ONBOARDING_GUIDE");
    expect(coverageParser).toContain("HOSPITAL_EXECUTIVE_QUALITY_OVERVIEW");
  });

  it("requires stakeholder-view rehearsal to attach implementation guide service evidence", () => {
    const source = readFileSync("e2e/stakeholder-view-rehearsal.spec.ts", "utf8");
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");
    const auditGate = readFileSync("../scripts/release/launch-coverage-audit.test.mjs", "utf8");

    expect(source).toContain("implementation-guide-entry-core-actions-codes");
    expect(source).toContain("IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS");
    expect(source).toContain("GET /api/v1/engine/tenant/implementation-steps");
    expect(source).toContain("GET /api/v1/engine/tenant/onboarding-readiness");
    expect(source).toContain("POST /api/v1/engine/integration/data-quality/reports");
    expect(source).toContain("implementationStepsReadbackVerified");
    expect(source).toContain("onboardingReadinessReadbackVerified");
    expect(source).toContain("dataQualityReportVerified");
    expect(source).toContain("auditVerified: true");

    expect(coverageParser).toContain("implementationGuideEntryCoreActions");
    expect(coverageParser).toContain("implementationGuideEntryCoreActionRows");
    expect(coverageParser).toContain("hasRequiredImplementationGuideEntryCoreActionsAttachment");
    expect(coverageParser).toContain("IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY");

    expect(auditGate).toContain(
      "implementationGuideEntryCoreActions:IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
    );
    expect(auditGate).toContain(
      "implementationGuideEntryCoreActionRows:IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY",
    );
  });

  it("requires diagnostic critical-value E2E to use backend FHIR R4 compensation message ids", () => {
    const source = readFileSync("e2e/diagnostic-critical-value-frontdesk.spec.ts", "utf8");

    expect(source).toContain("fhir-r4-${options.resourceType.toLowerCase()}-${fhirId}");
    expect(source).not.toContain("fhir-${options.resourceType.toLowerCase()}-${fhirId}");
  });

  it("requires diagnostic critical-value E2E to read context identity from snapshot resources", () => {
    const source = readFileSync("e2e/diagnostic-critical-value-frontdesk.spec.ts", "utf8");

    expect(source).toContain('textFieldAtPath(context, "resources.patient.mpi")');
    expect(source).toContain('textFieldAtPath(context, "resources.encounters[0].encounterId")');
    expect(source).not.toContain('textField(context, "patientId")');
    expect(source).not.toContain('textField(context, "encounterId")');
  });

  it("requires diagnostic critical-value E2E to complete the todo linked to the current card", () => {
    const source = readFileSync("e2e/diagnostic-critical-value-frontdesk.spec.ts", "utf8");

    expect(source).toContain('a[href*="cardId=${options.cardId}"]');
    expect(source).toContain('locator("xpath=ancestor::tr")');
    expect(source).not.toContain(".filter({ hasText: options.reportType })\\n    .first()");
  });

  it("requires diagnostic critical-value E2E to prove five diagnostic report family matrix through real consumers", () => {
    const source = readFileSync("e2e/diagnostic-critical-value-frontdesk.spec.ts", "utf8");

    expect(source).toContain("diagnosticReportFamilyFixtures");
    expect(source).toContain('"PACS_RIS"');
    expect(source).toContain('"ULTRASOUND"');
    expect(source).toContain('"PATHOLOGY"');
    expect(source).toContain('"ENDOSCOPY"');
    expect(source).toContain('"ECG"');
    expect(source).toContain("diagnosticReportFamilyConsumerMatrix");
    expect(source).toContain("assertDiagnosticReportFamilyConsumerMatrix");
    expect(source).toContain("completeReportFamilyMatrixTodos");
    expect(source).toContain("includeReportFamilyMatrixKnowledge: true");
    expect(source).toContain("五类医技报告族真实消费者矩阵代表切片");
    expect(source).not.toContain("完整 PACS/RIS/病理/内镜/心电系统族覆盖已完成");
    expect(source).not.toContain("完整上线验收已完成");
    expect(source).not.toContain('postApi(page, "/engine/workflow/todos"');
  });

  it("requires professional webhook rehearsals to feed the third-party real consumer slice gate without scope inflation", () => {
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");
    const fullSystemGate = readFileSync("../scripts/release/full-system-rehearsal-lib.mjs", "utf8");
    const auditGate = readFileSync("../scripts/release/launch-coverage-audit.test.mjs", "utf8");
    const pharmacySource = readFileSync(
      "e2e/pharmacy-review-antimicrobial-frontdesk.spec.ts",
      "utf8",
    );
    const infectionSource = readFileSync(
      "e2e/infection-public-health-safety-frontdesk.spec.ts",
      "utf8",
    );
    const surgerySource = readFileSync(
      "e2e/surgery-anesthesia-transfusion-frontdesk.spec.ts",
      "utf8",
    );
    const criticalSource = readFileSync("e2e/critical-emergency-icu-frontdesk.spec.ts", "utf8");

    expect(coverageParser).toContain("thirdPartySystemFamilyConsumerSlices:PHARMACY_REVIEW");
    expect(coverageParser).toContain(
      "thirdPartySystemFamilyConsumerSlices:PUBLIC_HEALTH_INFECTION_REGULATORY",
    );
    expect(coverageParser).toContain(
      "thirdPartySystemFamilyConsumerSlices:NURSING_ANESTHESIA_TRANSFUSION_ICU",
    );
    expect(coverageParser).toContain(
      "thirdPartySystemFamilyConsumerSlices:LIS_MONITORING_CRITICAL",
    );
    expect(coverageParser).toContain("thirdPartySystemFamilyConsumerSlices:HIS_EMR_CDR");
    expect(coverageParser).toContain("hasCompletePharmacyReviewConsumerSlice");
    expect(coverageParser).toContain("pharmacyReviewConsumerSlice");
    expect(coverageParser).toContain("requiresPharmacyReviewConsumerSliceAttachment");
    expect(coverageParser).toContain("S31__HIGH_RISK");
    expect(coverageParser).toContain("PHARMACY_REVIEW_ANTIMICROBIAL_HIGH_RISK_GOVERNANCE_REVIEW");
    expect(coverageParser).toContain("hasHighRiskPharmacyReviewGovernance");
    expect(coverageParser).toContain("S18__DEGRADATION");
    expect(coverageParser).toContain(
      "MEDICATION_SAFETY_PHARMACY_REVIEW_NOT_CONNECTED_LOCAL_RECOMMENDATION_CONTINUES",
    );
    expect(coverageParser).toContain("hasCompletePharmacyReviewS18DegradationEvidence");
    expect(coverageParser).toContain("hasCompletePharmacyReviewRectificationAuditEvidence");
    expect(coverageParser).toContain("hasCompletePublicHealthInfectionRegulatoryConsumerSlice");
    expect(coverageParser).toContain("publicHealthInfectionRegulatoryConsumerSlice");
    expect(coverageParser).toContain(
      "requiresPublicHealthInfectionRegulatoryConsumerSliceAttachment",
    );
    expect(coverageParser).toContain("PUBLIC_HEALTH_SAFETY_EVENT_HIGH_RISK_RECTIFICATION_REVIEW");
    expect(coverageParser).toContain("hasHighRiskInfectionPublicHealthRectification");
    expect(coverageParser).toContain("hasCompleteNursingAnesthesiaTransfusionIcuConsumerSlice");
    expect(coverageParser).toContain("nursingAnesthesiaTransfusionIcuConsumerSlice");
    expect(coverageParser).toContain("requiresSurgeryAnesthesiaTransfusionConsumerSliceAttachment");
    expect(coverageParser).toContain("hasCompleteLisMonitoringCriticalConsumerSlice");
    expect(coverageParser).toContain("lisMonitoringCriticalConsumerSlice");
    expect(coverageParser).toContain("requiresLisMonitoringCriticalConsumerSliceAttachment");
    expect(coverageParser).toContain("S19__DEGRADATION");
    expect(coverageParser).toContain(
      "CRITICAL_MONITORING_NOT_CONNECTED_LOCAL_ESCALATION_CONTINUES",
    );
    expect(coverageParser).toContain("hasCompleteCriticalEmergencyIcuS19DegradationEvidence");
    expect(coverageParser).toContain("S24__DEGRADATION");
    expect(coverageParser).toContain(
      "CRITICAL_EMERGENCY_TRIAGE_NOT_CONNECTED_LOCAL_RECOMMENDATION_CONTINUES",
    );
    expect(coverageParser).toContain("hasCompleteCriticalEmergencyIcuS24DegradationEvidence");
    expect(coverageParser).toContain("CRITICAL_MONITORING_OBSERVATION_INBOUND_RISK_ESCALATION");
    expect(coverageParser).toContain("noExternalSuccessClaim");
    expect(coverageParser).toContain("noAutoTransfusion");
    expect(coverageParser).toContain("noAutoSurgery");
    expect(fullSystemGate).toContain('"PHARMACY_REVIEW"');
    expect(fullSystemGate).toContain('"PUBLIC_HEALTH_INFECTION_REGULATORY"');
    expect(fullSystemGate).toContain('"NURSING_ANESTHESIA_TRANSFUSION_ICU"');
    expect(fullSystemGate).toContain('"LIS_MONITORING_CRITICAL"');
    expect(fullSystemGate).toContain('"HIS_EMR_CDR"');
    expect(auditGate).toContain("thirdPartySystemFamilyConsumerSlices:PHARMACY_REVIEW");
    expect(auditGate).toContain(
      "thirdPartySystemFamilyConsumerSlices:PUBLIC_HEALTH_INFECTION_REGULATORY",
    );
    expect(auditGate).toContain(
      "thirdPartySystemFamilyConsumerSlices:NURSING_ANESTHESIA_TRANSFUSION_ICU",
    );
    expect(auditGate).toContain("thirdPartySystemFamilyConsumerSlices:LIS_MONITORING_CRITICAL");
    expect(auditGate).toContain("thirdPartySystemFamilyConsumerSlices:HIS_EMR_CDR");
    expect(pharmacySource).toContain("pharmacy-review-antimicrobial-frontdesk-codes");
    expect(pharmacySource).toContain('"PHARMACY_REVIEW"');
    expect(pharmacySource).toContain("pharmacyReviewConsumerSlice");
    expect(pharmacySource).toContain("waitForPharmacyReviewCompensation");
    expect(pharmacySource).toContain('lastStatus === "NOT_CONNECTED"');
    expect(pharmacySource).not.toContain("完整第三方药房审方系统族已上线");
    expect(infectionSource).toContain("infection-public-health-safety-frontdesk-codes");
    expect(infectionSource).toContain('"PUBLIC_HEALTH_INFECTION_REGULATORY"');
    expect(infectionSource).toContain("publicHealthInfectionRegulatoryConsumerSlice");
    expect(infectionSource).toContain("PUBLIC_HEALTH_SAFETY_EVENT_HIGH_RISK_RECTIFICATION_REVIEW");
    expect(infectionSource).toContain("readRectificationAuditEvidence");
    expect(infectionSource).toContain("waitForAuditEvent");
    expect(infectionSource).toContain("auditEvidence");
    expect(infectionSource).toContain("permissionEvidence");
    expect(infectionSource).toContain("sixStateEvidence");
    expect(infectionSource).not.toContain("auditVerified: true");
    expect(infectionSource).not.toContain("permissionVerified: true");
    expect(infectionSource).not.toContain("sixStateBoundaryVerified: true");
    expect(coverageParser).toContain("hasCompleteInfectionPublicHealthRectificationAuditEvidence");
    expect(coverageParser).toContain(
      "hasCompleteInfectionPublicHealthRectificationPermissionEvidence",
    );
    expect(coverageParser).toContain(
      "hasCompleteInfectionPublicHealthRectificationSixStateEvidence",
    );
    expect(coverageParser).not.toContain("rectification?.auditVerified === true");
    expect(infectionSource).toContain('expect(["NOT_CONNECTED", "RETRYING"].includes(status)');
    expect(infectionSource).toContain('textField(compensation, "status") === "NOT_CONNECTED"');
    expect(infectionSource).not.toContain("完整公卫法定上报已上线");
    expect(surgerySource).toContain("surgery-anesthesia-transfusion-frontdesk-codes");
    expect(surgerySource).toContain('"NURSING_ANESTHESIA_TRANSFUSION_ICU"');
    expect(surgerySource).toContain("nursingAnesthesiaTransfusionIcuConsumerSlice");
    expect(surgerySource).toContain("noExternalSuccessClaim");
    expect(surgerySource).toContain("不代表完整护理系统");
    expect(surgerySource).toContain("不代表真实外部成功联通");
    expect(surgerySource).toContain("不代表自动输血");
    expect(surgerySource).toContain('expect(["NOT_CONNECTED", "RETRYING"].includes(status)');
    expect(surgerySource).toContain('textField(compensation, "status") === "NOT_CONNECTED"');
    expect(surgerySource).not.toContain("完整手麻手术室输血系统已上线");
    expect(criticalSource).toContain("critical-emergency-icu-frontdesk-codes");
    expect(criticalSource).toContain('"LIS_MONITORING_CRITICAL"');
    expect(criticalSource).toContain("lisMonitoringCriticalConsumerSlice");
    expect(criticalSource).toContain('"S19__DEGRADATION"');
    expect(criticalSource).toContain(
      "CRITICAL_MONITORING_NOT_CONNECTED_LOCAL_ESCALATION_CONTINUES",
    );
    expect(criticalSource).toContain("CRITICAL_MONITORING_OBSERVATION_INBOUND_RISK_ESCALATION");
    expect(criticalSource).toContain("noExternalSuccessClaim");
    expect(criticalSource).toContain("不代表完整 LIS 系统");
    expect(criticalSource).toContain("不代表真实外部成功联通");
    expect(criticalSource).toContain("不代表自动调整呼吸机");
    expect(criticalSource).toContain("hisEmrCdrConsumerSlice");
    expect(criticalSource).toContain('"HIS_EMR_CDR"');
    expect(criticalSource).toContain(
      'healthStatus: textField(data, "healthStatus") ?? "NOT_CONNECTED"',
    );
    expect(criticalSource).not.toContain("完整 ICU 系统已上线");
  });

  it("requires medical-record insurance payment consumer slice to stay explicit and bounded", () => {
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");
    const fullSystemGate = readFileSync("../scripts/release/full-system-rehearsal-lib.mjs", "utf8");
    const auditGate = readFileSync("../scripts/release/launch-coverage-audit.test.mjs", "utf8");
    const qualitySource = readFileSync(
      "e2e/quality-management-entry-core-actions-rehearsal.spec.ts",
      "utf8",
    );
    const qualitySupport = readFileSync("e2e/support/qualityManagementEntryCoreActions.ts", "utf8");

    expect(coverageParser).toContain(
      "thirdPartySystemFamilyConsumerSlices:MEDICAL_RECORD_INSURANCE_PAYMENT",
    );
    expect(coverageParser).toContain("hasCompleteMedicalRecordInsurancePaymentConsumerSlice");
    expect(fullSystemGate).toContain('"MEDICAL_RECORD_INSURANCE_PAYMENT"');
    expect(auditGate).toContain(
      "thirdPartySystemFamilyConsumerSlices:MEDICAL_RECORD_INSURANCE_PAYMENT",
    );
    expect(qualitySource).toContain("buildMedicalRecordInsurancePaymentConsumerSliceEvidence");
    expect(qualitySource).toContain('"MEDICAL_RECORD_INSURANCE_PAYMENT"');
    expect(qualitySource).toContain("clinicalContext");
    expect(qualitySupport).toContain("medicalRecordInsurancePaymentConsumerSlice");
    expect(qualitySource).toContain("不代表完整病案医保支付系统族覆盖");
    expect(qualitySource).not.toContain("完整病案医保支付系统族已上线");
  });

  it("requires followup patient service consumer slice to stay explicit and bounded", () => {
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");
    const fullSystemGate = readFileSync("../scripts/release/full-system-rehearsal-lib.mjs", "utf8");
    const auditGate = readFileSync("../scripts/release/launch-coverage-audit.test.mjs", "utf8");
    const realFrontdeskSource = readFileSync("e2e/real-frontdesk-rehearsal.spec.ts", "utf8");

    expect(coverageParser).toContain(
      "thirdPartySystemFamilyConsumerSlices:FOLLOWUP_PATIENT_SERVICE",
    );
    expect(fullSystemGate).toContain('"FOLLOWUP_PATIENT_SERVICE"');
    expect(fullSystemGate).toMatch(
      /thirdPartySystemFamilyConsumerSlices:\s*\{[\s\S]*"FOLLOWUP_PATIENT_SERVICE"/u,
    );
    expect(auditGate).toContain("thirdPartySystemFamilyConsumerSlices:FOLLOWUP_PATIENT_SERVICE");
    expect(realFrontdeskSource).toMatch(
      /followupPatientServiceConsumerSlice|"FOLLOWUP_PATIENT_SERVICE"/u,
    );
  });

  it("keeps third-party family evidence API readback on the real backend API base", () => {
    const source = readFileSync("e2e/third-party-system-families-rehearsal.spec.ts", "utf8");

    expect(source).toContain('import { apiBase, ensureReadySession } from "./support/auth"');
    expect(source).toContain("page.request.get(`${apiBase}/engine/integration/onboardings`");
    expect(source).not.toContain('page.request.get("/api/v1/engine/integration/onboardings"');
  });

  it("requires S2/S4 rehearsal to prove frontdesk mapping and real inbound runtime consumption", () => {
    const source = readFileSync("e2e/s2-s4-terminology-integration-rehearsal.spec.ts", "utf8");

    expect(source).toContain("s2-s4-runtime-mapping-codes");
    expect(source).toContain("平台管理员前台创建 LIS Webhook 适配器并配置字段映射");
    expect(source).toContain('ensureReadySession(page, "platform-admin")');
    expect(source).toContain('ensureReadySession(page, "engine-operator")');
    expect(source).toContain("前台登记标准术语");
    expect(source).toContain("签名主数据同步登记院内术语");
    expect(source).toContain("前台生成并确认术语映射候选");
    expect(source).toContain("前台生成不可变术语资产版本");
    expect(source).toContain("/engine/integration/webhooks/");
    expect(source).toContain("/inbound");
    expect(source).toContain("postExternalSignedApi(");
    expect(source).toContain("context.post(`${apiBase}${path}`");
    expect(source).not.toContain("playwrightRequest.newContext({ baseURL: apiBase })");
    expect(source).toContain("await context.dispose()");
    expect(source).toContain("postApi(");
    expect(source).toContain("options.page");
    expect(source).toContain("normalizedCodeCount");
    expect(source).toContain("runtimeReleaseId");
    expect(source).toContain("mappingId");
    expect(source).toContain("/engine/integration/knowledge-runtime/runtime-release/current");
    expect(source).toContain("runtimeContractReadbackMatched");
    expect(source).toContain("runtimeAssetSelectionForActivation");
    expect(source).toContain('textField(item, "sourceLayer") === "PLATFORM"');
    expect(source).toContain("versionId: null");
    expect(source).toContain("canonicalInboundWebhookPayload(options.request)");
    expect(source).toContain("sha256=${signHmacSha256");
    expect(source).toContain('triggerPoint: "result-review"');
    expect(source).not.toContain('triggerPoint: "RESULT_REVIEW"');
    expect(source).toContain('chooseFieldMappingCategory(page, dialog, 1, "检验")');
    expect(source).toContain("targetDictionaryKey: options.standardSystem");
    expect(source).toContain('category: "LAB"');
    expect(source).not.toContain("page.waitForTimeout");
  });

  it("requires S21/S32 frontdesk rehearsal to choose the actual result-review option label", () => {
    const source = readFileSync("e2e/infection-public-health-safety-frontdesk.spec.ts", "utf8");
    const triggerOptions = readFileSync("src/shared/config/clinicalTriggerPoints.ts", "utf8");

    expect(triggerOptions).toContain('{ value: "result-review", label: "审核结果" }');
    expect(source).toContain('await chooseDialogOption(page, dialog, "触发时点", "审核结果")');
    expect(source).toContain('payload.triggerType === "result-review"');
    expect(source).toContain("localRehearsalQualityDepartmentId");
    expect(source).toContain(
      'const departmentId = await localRehearsalQualityDepartmentId(page, options.suffix);\n  await ensureReadySession(page, "engine-operator");',
    );
    expect(source).toContain(
      'await ensureReadySession(page, "platform-admin");\n  const created = await postApi(page, "/engine/org/org-units", {',
    );
    expect(source).not.toContain('...apiContext(suffix, "evaluation-indicator-create")');
    expect(source).not.toContain("...apiContext(suffix, `evaluation-indicator-${action}`)");
    expect(source).not.toContain(
      'await chooseDialogOption(page, dialog, "触发时点", "检验结果复核")',
    );
  });

  it("requires S2/S4 signature preview to choose and submit the current webhook channel", () => {
    const source = readFileSync("e2e/s2-s4-terminology-integration-rehearsal.spec.ts", "utf8");

    expect(source).toContain("const webhookName = `S2S4 LIS 入站 ${suffix}`");
    expect(source).toContain("await generateWebhookSignaturePreview(page, {");
    expect(source).toContain("webhookName,");
    expect(source).toContain("await expectWebhookVisibleInFrontdesk(page, options.webhookName)");
    expect(source).toContain("selectAntOptionFromSelect(page,");
    expect(source).toContain('signaturePreviewCard.locator(".ant-select").first()');
    expect(source).toContain("options.webhookName");
    expect(source).toContain("selectedAntOptionMatches(select, optionText)");
    expect(source).toContain("clickVisibleAntOption(page, option");
    expect(source).toContain("page.mouse.click(");
    expect(source).toContain("await readRequestJson(response.request())");
    expect(source).toContain(
      'expect(requestBody?.webhookId, "签名预览必须绑定本轮回调通道").toBe(options.webhookId)',
    );
    expect(source).not.toContain(
      'selectAntOption(page, page.locator("main"), "回调通道", `S2S4 LIS 入站`)',
    );
    expect(source).not.toContain("selectAntOptionByPlaceholder(page, signaturePreviewCard");
    expect(source).not.toContain("await option.click();");
  });

  it("requires S2/S4 signed master data sync to continue from the latest server cursor", () => {
    const source = readFileSync("e2e/s2-s4-terminology-integration-rehearsal.spec.ts", "utf8");

    expect(source).toContain("readLatestMasterDataCursor(page, sourceSystem)");
    expect(source).toContain("previousCursor,");
    expect(source).toContain("/engine/integration/master-data/reconciliation?sourceSystem=");
    expect(source).toContain("签名必须基于读取到的最新服务端游标");
    expect(source).not.toContain("previousCursor: null");
  });

  it("requires S2/S4 terminology candidates to use deterministic alias evidence", () => {
    const source = readFileSync("e2e/s2-s4-terminology-integration-rehearsal.spec.ts", "utf8");

    expect(source).toContain("registerStandardTermFromFrontdesk(page, {");
    expect(source).toContain("localCode,");
    expect(source).toContain('getByLabel("规范名称")');
    expect(source).toContain(".fill(`S2S4 血红蛋白标准");
    expect(source).toContain("options.localCode");
    expect(source).toContain("确定性别名");
    expect(source).toContain("canonicalTerminologyAlias(options.localCode)");
    expect(source).toContain('textField(item, "evidenceText")');
    expect(source).toContain('candidateRow.getByRole("button", { name: /确\\s*认/u })');
    expect(source).not.toContain('textField(item, "localCode") === options.localCode');
    expect(source).not.toContain('textField(item, "standardCode") === options.standardCode');
    expect(source).not.toContain('candidateRow.getByRole("button", { name: "确认" })');
  });

  it("detects missing platform runtime candidates before publishing the baseline", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const candidates = [
      {
        assetType: "FIELD_CATALOG",
        assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
        versionId: "field-v1",
        status: "DRAFT",
      },
      {
        assetType: "VALUE_SET",
        assetIdentity: "VS.EXTRA",
        versionId: "value-v1",
        status: "DRAFT",
      },
    ];

    expect(auth.missingRuntimeCandidateTypes(candidates)).toEqual([
      "KNOWLEDGE",
      "TERMINOLOGY",
      "RULE",
      "PATHWAY",
      "EVALUATION",
      "FOLLOWUP",
      "SAFETY",
      "CDSS_RISK",
      "VALUE_SET",
      "FORMULA",
      "ORDER_SET",
      "ACTION_CARD",
    ]);
    expect(auth.runtimeCandidatesCoverRequiredTypes(candidates)).toBe(false);
    expect(auth.versionIdsForRequiredRuntimeCandidates(candidates)).toEqual(["field-v1"]);
  });

  it("does not let same-type historical assets satisfy the local runtime rehearsal baseline", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const historicalCandidates = runtimeAssetTypes.map((assetType) => ({
      assetType,
      assetIdentity: `HISTORICAL.${assetType}`,
      versionId: `${assetType.toLowerCase()}-old`,
      status: "PUBLISHED",
    }));

    expect(auth.missingRuntimeCandidateTypes(historicalCandidates)).toEqual([...runtimeAssetTypes]);
    expect(auth.runtimeCandidatesCoverRequiredTypes(historicalCandidates)).toBe(false);
    expect(auth.versionIdsForRequiredRuntimeCandidates(historicalCandidates)).toEqual([]);
    expect(
      auth.hospitalRuntimeCoversRequiredAssets({
        release: { releaseId: "hospital-release-historical-assets" },
        items: historicalCandidates.map((item) => ({
          ...item,
          entryState: "ACTIVE",
        })),
      }),
    ).toEqual({
      releaseId: "hospital-release-historical-assets",
      platformBaselineReleaseId: null,
      ready: false,
    });
  });

  it("selects every required platform runtime candidate once the rehearsal bootstrap fills gaps", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const candidates = [
      ...runtimeAssetClosure.map((asset) => ({
        assetType: asset.assetType,
        assetIdentity: asset.assetIdentity,
        versionId: runtimeAssetVersionId(asset.assetIdentity),
        status: "DRAFT",
      })),
      {
        assetType: "RULE",
        assetIdentity: "RULE.LOCAL.REHEARSAL.BASELINE",
        versionId: "",
        status: "DRAFT",
      },
    ];

    expect(auth.missingRuntimeCandidateTypes(candidates)).toEqual([]);
    expect(auth.runtimeCandidatesCoverRequiredTypes(candidates)).toBe(true);
    expect(auth.versionIdsForRequiredRuntimeCandidates(candidates)).toEqual(
      runtimeAssetClosure.map((asset) => runtimeAssetVersionId(asset.assetIdentity)),
    );
  });

  it("requires local rehearsal bootstrap to create every platform runtime asset through real services", () => {
    const source = readFileSync("e2e/support/auth.ts", "utf8");

    expect(source).toContain('"/engine/context/field-catalog/drafts"');
    expect(source).toContain('"/engine/rule/rules"');
    expect(source).toContain('"/engine/knowledge/identities"');
    expect(source).toContain('"/engine/authoring/declarative-assets"');
    expect(source).toContain('"/engine/pathway/pathway-templates"');
    expect(source).toContain('"/engine/evaluation/indicators"');
    expect(source).toContain('"/engine/followup/templates"');
    expect(source).toContain('"/engine/cdss/risk-matrix"');
    expect(source).toContain('"/engine/terminology/terms/standard"');
    expect(source).toContain('"/engine/terminology/terms/local"');
    expect(source).toContain('"/engine/terminology/mappings/candidates"');
    expect(source).toContain('"/engine/terminology/assets/drafts"');
    expect(source).toContain('"/engine/safety/redlines"');
    expect(source).toContain('"/engine/safety/redlines:dry-run"');
    expect(source).toContain('"/engine/safety/redlines:promote"');
    expect(source).toContain('arrayField(await responseData(response), "rules")');
    expect(source).toContain("riskMatrixId: riskMatrix.matrixId");
    expect(source).toContain("riskMatrixVersion: riskMatrix.matrixVersion");
    expect(source).not.toContain('riskMatrixId: "risk-matrix-local-rehearsal"');
    expect(source).not.toContain('riskMatrixVersion: "4"');
    for (const assetType of runtimeAssetTypes) {
      expect(source).toContain(`missingTypes.includes("${assetType}")`);
    }
    expect(source).toContain('writeApi(page, "put", path, data, extraHeaders)');
    expect(source).toContain("function ruleDefinition(");
    expect(source).toContain("JSON.stringify({ all: [{ fact, operator, value }] })");
    expect(source).toContain("waitForPollingInterval(250)");
    expect(source).not.toContain("activeAssets: []");
    expect(source).not.toContain("page.waitForTimeout");
  });

  it("requires runtime release frontdesk E2E to assert the exact 13-asset runtime closure", () => {
    const source = readFileSync("e2e/runtime-release-frontdesk.spec.ts", "utf8");

    expect(source).toContain("requiredRuntimeAssetsForRehearsal");
    expect(source).toContain("assertRuntimeReleaseRequestCarriesRequiredAssets");
    expect(source).toContain("assertRuntimeDetailCarriesRequiredAssets");
    expect(source).toContain("assertRuntimeAssetsContainLocalCandidate");
    expect(source).toContain("assertRuntimeAssetsExcludeUnselectedCandidate");
    expect(source).toContain("assertRuntimeAssetsExcludeLocalCandidate");
    expect(source).toContain("assertRequiredRuntimeInputsVisibleAndSelected");
    expect(source).toContain("createHospitalRuntimeReleaseCandidate");
    expect(source).toContain("selectHospitalLocalRuntimeCandidate");
    expect(source).toContain("assertThirdPartyRuntimeConsumerCarriesRequiredAssets");
    expect(source).toContain("attachRuntimeReleaseCoverageEvidence");
    expect(source).toContain("runtime-release-coverage-codes");
    expect(source).toContain("/engine/integration/knowledge-runtime/runtime-release/current");
    expect(source).toContain('getByRole("checkbox", { name: /启用/ })');
    expect(source).toContain("平台标准内容");
    expect(source).toContain("集团与本院内容");
    expect(source).toContain("前台评估机构生效版本发布影响");
    expect(source).toContain("前台只选择本轮部分本院内容进入机构生效版本");
    expect(source).toContain("activationRequestCarriesRequiredAssets");
    expect(source).toContain("partialSelection");
    expect(source).toContain("multiHospitalDifferentiation");
    expect(source).toContain("distinctHospitals");
    expect(source).toContain("distinctSelectedCandidates");
    expect(source).toContain("backendReadbacksIsolated");
    expect(source).toContain("runtimeConsumerReadbacksIsolated");
    expect(source).toContain("excludesOtherHospitalCandidate");
    expect(source).toContain("offlineDelivery");
    expect(source).toContain("exerciseOfflineDelivery");
    expect(source).toContain("ensureSecondHospitalRuntimeReleaseRehearsalContext");
    expect(source).toContain("readThirdPartyRuntimeConsumerForRole");
    expect(source).toContain("前台为第二家医院选择不同本院内容生成机构生效版本");
    expect(source).toContain("两家医院后端与第三方运行契约读回互不串用");
    expect(source).toContain("前台导出机构生效版本离线交付文件");
    expect(source).toContain("下载离线交付文件并校验完整快照");
    expect(source).toContain("离线交付导入预检验签且不改写当前机构生效版本");
    expect(source).toContain("离线交付恢复执行生成新机构生效版本");
    expect(source).toContain("恢复后后端和第三方运行契约读取同一机构生效版本");
    expect(source).toContain('getByRole("button", { name: "导出离线交付文件" })');
    expect(source).toContain('getByRole("button", { name: "校验离线交付文件" })');
    expect(source).toContain('getByRole("button", { name: "恢复为新机构生效版本" })');
    expect(source).toContain("/runtime-releases/offline-delivery");
    expect(source).toContain("/runtime-releases/offline-delivery:validate-import");
    expect(source).toContain("/runtime-releases/offline-delivery:restore");
    expect(source).toContain("apiPathFromFileUri");
    expect(source).toContain("SM3_WITH_SM2");
    expect(source).toContain('"runtimeMutation":false');
    expect(source).toContain("offlineDeliveryRuntimeUnchanged");
    expect(source).toContain("offlineDeliveryRestoreExecuted");
    expect(source).toContain("offlineDeliveryRestoreReadbackMatched");
    expect(source).toContain("offlineDeliveryRestoreRuntimeConsumerMatched");
    expect(source).toContain("unselectedLocalCandidate");
    expect(source).toContain("activationRequestOmitsUnselected");
    expect(source).toContain("activationReadbackOmitsUnselected");
    expect(source).toContain("runtimeConsumerOmitsUnselected");
    expect(source).toContain("activationReadback");
    expect(source).toContain("runtimeConsumerReadback");
    expect(source).toContain("rollbackReadback");
    expect(source).toContain("rollbackRuntimeConsumerReadback");
    expect(source).toContain("assetIdentity");
    expect(source).toContain("versionId === candidate.versionId");
    expect(source).toContain("postDataJSON");
    expect(source).not.toContain('runtimeHasActiveAsset(current.data, "FIELD_CATALOG")');
    expect(source).not.toContain('runtimeHasActiveAsset(current.data, "RULE")');

    const parser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");
    expect(parser).toContain(
      "multiHospitalRuntimeIsolationRows:TWO_HOSPITAL_RUNTIME_RELEASE_ISOLATION",
    );
    expect(parser).toContain("collectMultiHospitalRuntimeIsolationClaimsFromTest");
    expect(parser).toContain("hasCompleteRuntimeReleaseMultiHospitalEvidence");
    expect(parser).not.toContain("organizationLevels:PLATFORM");
    expect(parser).not.toContain("organizationLevels:GROUP");
    expect(parser).not.toContain("organizationLevels:CARE_TEAM");
    expect(parser).not.toContain("organizationLevels:SPECIALTY_CENTER");
    expect(parser).not.toContain("organizationLevels:SHARED_CENTER");

    const releaseGate = readFileSync("../scripts/release/full-system-rehearsal-lib.mjs", "utf8");
    expect(releaseGate).toContain("multiHospitalRuntimeIsolationRows");
    expect(releaseGate).toContain("TWO_HOSPITAL_RUNTIME_RELEASE_ISOLATION");
    expect(releaseGate).toContain('requiredCoverage: ["organizationLevels"]');
    expect(releaseGate).not.toContain(
      'requiredCoverage: ["organizationLevels", "multiHospitalRuntimeIsolationRows"]',
    );
  });

  it("requires quality management entry rehearsal to activate the CLAIM indicator into hospital runtime before snapshot creation", () => {
    const source = readFileSync(
      "e2e/quality-management-entry-core-actions-rehearsal.spec.ts",
      "utf8",
    );
    const scenarioBody = source.slice(
      source.indexOf('test("质量风险概览、质量问题整改、医保审核和评价指标均完成真实前台代表动作"'),
      source.indexOf("await attachQualityManagementEntryCoreActionEvidence"),
    );

    expect(source).toContain("requiredRuntimeAssetsForRehearsal");
    expect(source).toContain("resolveBaselineRuntimeAssets");
    expect(source).toContain("activateHospitalRuntimeWithClaimIndicator");
    expect(source).toContain("activeAssets: uniqueRuntimeAssets");
    expect(source).not.toContain("activeAssets: []");
    expect(source).toContain("snapshot.runtimeReleaseId");
    expect(scenarioBody.indexOf("createActiveClaimIndicatorFromUi")).toBeLessThan(
      scenarioBody.indexOf("activateHospitalRuntimeWithClaimIndicator"),
    );
    expect(scenarioBody.indexOf("activateHospitalRuntimeWithClaimIndicator")).toBeLessThan(
      scenarioBody.indexOf("preparePatientSnapshotFromUi"),
    );
  });

  it("requires knowledge operations asset entry rehearsal to preserve baseline assets and avoid model-direct publishing", () => {
    const source = readFileSync(
      "e2e/knowledge-operations-asset-entry-core-actions-rehearsal.spec.ts",
      "utf8",
    );
    const support = readFileSync("e2e/support/knowledgeOperationsAssetEntryCoreActions.ts", "utf8");
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");

    expect(source).toContain("knowledge-operations-asset-entry-core-actions-codes");
    expect(source).toContain("KNOWLEDGE_OPERATIONS_ASSET_ENTRY_CORE_ACTIONS");
    expect(source).toContain("resolveBaselineRuntimeAssets");
    expect(source).toContain("uniqueRuntimeAssets");
    expect(source).toContain("requiredRuntimeAssetsForRehearsal");
    expect(source).toContain("runtimeConsumerReadbackVerified");
    expect(source).toContain("rollbackReadbackVerified");
    expect(source).toContain("buildKnowledgeSupplyChainEvidence");
    expect(source).toContain("knowledgeSupplyChainEvidence");
    expect(source).toContain("buildKnowledgeDomainSupplyChainRows");
    expect(source).toContain("knowledgeDomainSupplyChainRows");
    expect(source).toContain("buildVersionedAssetSupplyChainRows");
    expect(source).toContain("versionedAssetSupplyChainRows");
    expect(source).toContain("contentHashSeed");
    expect(source).toContain("runtime.runtimeConsumerReadbackVerified");
    expect(source).toContain("runtime.rollbackReadbackVerified");
    expect(support).toContain("versionedAssetSupplyChainRowScopeStatement");
    expect(support).toContain("13 类版本化资产逐类供给链代表子账");
    expect(support).toContain("knowledgeDomainSupplyChainRowScopeStatement");
    expect(support).toContain("11 个知识内容分类逐类生产治理代表子账");
    expect(support).toContain("buildKnowledgeDomainSupplyChainRows");
    expect(support).toContain("publishedToRuntime");
    expect(support).toContain("replacementVerified");
    expect(support).toContain("buildVersionedAssetSupplyChainRows");
    expect(support).toContain("sourceControlEvidencePath");
    expect(support).toContain("noDirectModelPublish");
    expect(support).toContain("continuousIterationVerified");
    expect(coverageParser).toContain("hasKnowledgeDomainSupplyChainLedgerScopeBoundary");
    expect(coverageParser).toContain("hasCompleteKnowledgeDomainSupplyChainRows");
    expect(coverageParser).toContain("parsed.knowledgeDomainSupplyChainRows");
    expect(coverageParser).toContain("hasCompleteVersionedAssetSupplyChainLedgerAttachment");
    expect(coverageParser).toContain("hasCompleteVersionedAssetSupplyChainRows");
    expect(coverageParser).toContain("hasVersionedAssetSupplyChainLedgerScopeBoundary");
    expect(coverageParser).toContain("parsed.versionedAssetSupplyChainRows");
    expect(coverageParser).toContain(
      "!hasRuntimeRelease || !hasKnowledgeOperations || !hasVersionedAssetSupplyChainLedger",
    );
    expect(source).toContain("sourceControl");
    expect(source).toContain("sourceVersionRegistered");
    expect(source).toContain("sourceFragmentRegistered");
    expect(source).toContain("uploadParseJobSucceeded");
    expect(source).toContain("parseResultSourceVersionId");
    expect(source).toContain("parsedFragmentCount");
    expect(source).toContain("sourceFragmentIds");
    expect(source).toContain("uploadParseKnowledgeDocument");
    expect(source).toContain("documents:upload-parse");
    expect(source).toContain("multipart:");
    expect(source).toContain("readParsedSourceFragments");
    expect(source).toContain(
      "/engine/knowledge/sources/versions/${encodeURIComponent(sourceVersionId)}/fragments",
    );
    expect(source).toContain("ensureKnowledgeMaterialRoot");
    expect(source).toContain("citationBound");
    expect(source).toContain("textExcerptVerified");
    expect(source).toContain("options.knowledge.textExcerpt.length > 0");
    expect(source).toContain("options.provenanceAction.sourceLineageVerified === true");
    expect(source).not.toContain('textExcerpt.includes("受控来源")');
    expect(source).toContain("qualityGateRecordCreated");
    expect(source).toContain("humanGovernance");
    expect(source).toContain("candidateApproved");
    expect(source).toContain("terminologySync");
    expect(source).toContain("localTermRegistered");
    expect(source).toContain("terminologyAssetVersionCreated");
    expect(source).toContain("runtimeLifecycle");
    expect(source).toContain("baselineAssetsPreserved");
    expect(source).toContain("lineageConsumers");
    expect(source).toContain("graphProjectionVerified");
    expect(source).toContain("safetyBoundary");
    expect(source).toContain("noAutoClinicalAction");
    expect(source).toContain("function runtimeReleaseId");
    expect(source).toContain("function runtimeReleaseItems");
    expect(source).toContain('arrayValues(recordField(value, "items"))');
    expect(source).toContain('textField(recordField(value, "release"), "releaseId")');
    expect(source).toContain("回滚必须生成新的当前机构生效版本");
    expect(source).toContain("rolledBackConsumer");
    expect(source).toContain("!JSON.stringify(await responseData(rolledBackConsumer)).includes");
    expect(source).toContain("officialProductionInside134: true");
    expect(source).toContain("externalSourcesPreparatoryOnly: true");
    expect(source).toContain("modelDirectPublishBlocked: true");
    expect(source).toContain("noDirectPublishVerified: true");
    expect(source).toContain("ensurePlatformRuntimeAssetApiSession");
    expect(source).toContain("ensureRehearsalRuntimeAssetApiSession");
    expect(source).toContain("platformDiagnosticItemKnowledgeIdentity");
    expect(source).toContain("/engine/knowledge/candidates/${classificationId}/review");
    expect(source).toContain(
      "/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases",
    );
    expect(source).toContain("/engine/integration/knowledge-runtime/runtime-release/current");
    expect(source).not.toContain("activeAssets: []");
    expect(source).not.toContain(
      'numericField(sourceVersionData, "materialId") ?? numericField(sourceVersionData, "id")',
    );
    expect(source).not.toContain("/engine/knowledge/materials/${seed.materialId}");
    const scenarioBody = source.slice(
      source.indexOf('test("知识生产、审核发布、术语、机构版本、规则路径和来源边界完成代表矩阵"'),
      source.indexOf("function buildKnowledgeSupplyChainEvidence"),
    );
    expect(scenarioBody.indexOf('ensureReadySession(page, "platform-admin"')).toBeLessThan(
      scenarioBody.indexOf("ensureKnowledgeMaterialRoot(page, suffix)"),
    );
    expect(scenarioBody.indexOf("ensureKnowledgeMaterialRoot(page, suffix)")).toBeLessThan(
      scenarioBody.indexOf('ensureReadySession(page, "engine-operator"'),
    );
    const prepareKnowledgeBody = source.slice(
      source.indexOf("async function prepareKnowledgeCandidate"),
      source.indexOf("async function prepareTerminologyAsset"),
    );
    expect(prepareKnowledgeBody).toContain("uploadParseKnowledgeDocument");
    expect(prepareKnowledgeBody).toContain("readParsedSourceFragments");
    expect(prepareKnowledgeBody).not.toContain(
      "/engine/knowledge/sources/${sourceDocumentId}/versions",
    );
    expect(prepareKnowledgeBody).not.toContain(
      'postApi(page, "/engine/knowledge/sources/fragments"',
    );
    expect(prepareKnowledgeBody).not.toContain(
      'postApi(page, "/engine/knowledge-production/generate"',
    );
    const ruleEvidenceBody = source.slice(
      source.indexOf("async function createRuleDefinitionEvidence"),
      source.indexOf("async function createPathwayTemplateEvidence"),
    );
    expect(ruleEvidenceBody).toContain("triggers: [");
    expect(ruleEvidenceBody).toContain('trigger_point: "result-review"');
    expect(ruleEvidenceBody).toContain('purpose: "RULE_EXECUTION"');
    expect(ruleEvidenceBody).toContain('ruleType: "QUALITY"');
    expect(ruleEvidenceBody).toContain('authoringMode: "DSL"');
    expect(ruleEvidenceBody).toContain('sourceRef: "local-e2e:knowledge-operations"');
    expect(ruleEvidenceBody).toContain("dsl: {");
    expect(ruleEvidenceBody).toContain("when: { all:");
    expect(ruleEvidenceBody).toContain("then: [");
    expect(ruleEvidenceBody).toContain('actionCode: "REMIND"');
    expect(ruleEvidenceBody).toContain('triggerPoint: "result-review"');
    expect(ruleEvidenceBody).toContain("context: { patient: { age: 42 } }");
    expect(ruleEvidenceBody).not.toContain("condition: {");
    expect(ruleEvidenceBody).not.toContain("facts: { patient");
    expect(ruleEvidenceBody).not.toContain("KNOWLEDGE_OPS_REVIEW");

    const pathwayEvidenceBody = source.slice(
      source.indexOf("async function createPathwayTemplateEvidence"),
      source.indexOf("async function createInstitutionKnowledgeEvidence"),
    );
    expect(pathwayEvidenceBody).toContain("outcomeBindings: []");
    expect(pathwayEvidenceBody).toContain('startNodeCode: "START"');
    expect(pathwayEvidenceBody).toContain("requestedNextNodeCodes: []");
    expect(pathwayEvidenceBody).not.toContain("contextSnapshotId");
    expect(pathwayEvidenceBody).not.toContain("facts:");
    expect(pathwayEvidenceBody).not.toContain("milestoneType");
    expect(pathwayEvidenceBody).not.toContain("dueMinutes");
    const pathwayHelperBody = source.slice(
      source.indexOf("function milestone("),
      source.indexOf("function parseKnowledgeCandidateRef"),
    );
    expect(pathwayHelperBody).toContain("phaseCode");
    expect(pathwayHelperBody).toContain("phaseName");
    expect(pathwayHelperBody).toContain("expectedOffsetMinutes");
    expect(pathwayHelperBody).toContain("responsibleRole");
    expect(pathwayHelperBody).toContain("timeWindowMinutes");
    expect(pathwayHelperBody).toContain("clockSla");
    expect(pathwayHelperBody).toContain("minMinutes");
    expect(pathwayHelperBody).toContain("escalations");
    expect(pathwayHelperBody).toContain("QUALITY_RECORD");
    expect(pathwayHelperBody).not.toContain("milestoneType");
    expect(pathwayHelperBody).not.toContain("dueMinutes");

    const institutionEvidenceBody = source.slice(
      source.indexOf("async function createInstitutionKnowledgeEvidence"),
      source.indexOf("async function createDiagnosisKnowledgeEvidence"),
    );
    expect(institutionEvidenceBody).toContain("ensurePlatformRuntimeAssetApiSession(page)");
    expect(institutionEvidenceBody).toContain(
      "/engine/knowledge/identities/by-code/${encodeURIComponent(platformDiagnosticItemKnowledgeIdentity)}",
    );
    expect(institutionEvidenceBody).toContain("ensureRehearsalRuntimeAssetApiSession(page)");
    expect(institutionEvidenceBody).toContain("platformIdentityId");
    expect(institutionEvidenceBody).toContain("targetOrgUnitId: hospitalId");
    expect(institutionEvidenceBody).toContain('applicableScope: "ALL"');
    expect(institutionEvidenceBody).not.toContain("platformVersionId");
    expect(institutionEvidenceBody).not.toContain("organizationScope");
    expect(institutionEvidenceBody).not.toContain("organizationCode");
    expect(institutionEvidenceBody).not.toContain(
      '...knowledgeContext("knowledge-ops-customization")',
    );
    expect(institutionEvidenceBody).not.toContain("request_id");
    expect(institutionEvidenceBody).not.toContain("trace_id");

    const diagnosisEvidenceBody = source.slice(
      source.indexOf("async function createDiagnosisKnowledgeEvidence"),
      source.indexOf("async function verifyProvenanceEvidence"),
    );
    expect(diagnosisEvidenceBody).toContain("identity: {");
    expect(diagnosisEvidenceBody).toContain("source: {");
    expect(diagnosisEvidenceBody).toContain("version: {");
    expect(diagnosisEvidenceBody).toContain("evidence: {");
    expect(diagnosisEvidenceBody).toContain('direction: "SUPPORTING"');
    expect(diagnosisEvidenceBody).toContain('weight: "MAJOR"');
    expect(diagnosisEvidenceBody).toContain(
      "/engine/knowledge/diagnosis/versions/${versionId}/test-cases",
    );
    expect(diagnosisEvidenceBody).toContain("expectedIdentityId: identityId");
    expect(diagnosisEvidenceBody).toContain('expectedConfidence: "STRONG"');
    expect(diagnosisEvidenceBody).not.toContain("diagnosisName:");
    expect(diagnosisEvidenceBody).not.toContain("evidenceStrength");

    const provenanceEvidenceBody = source.slice(
      source.indexOf("async function verifyProvenanceEvidence"),
      source.indexOf("async function verifyGraphEvidence"),
    );
    expect(provenanceEvidenceBody).toContain('numericField(provenanceData, "currentVersionId")');
    expect(provenanceEvidenceBody).toContain('arrayField(provenanceData, "sourceEvidence").find');
    expect(provenanceEvidenceBody).toContain('numericField(evidence, "citationId")');
    expect(provenanceEvidenceBody).toContain('numericField(evidence, "sourceVersionId")');
    expect(provenanceEvidenceBody).toContain('numericField(evidence, "sourceFragmentId")');
    expect(provenanceEvidenceBody).toContain('textField(evidence, "textExcerpt")');
    expect(provenanceEvidenceBody).not.toContain("seed.materialId");

    const graphEvidenceBody = source.slice(
      source.indexOf("async function verifyGraphEvidence"),
      source.indexOf("async function verifyAiWorkflowSafetyBoundary"),
    );
    expect(graphEvidenceBody).toContain("keyword=${encodeURIComponent");
    expect(graphEvidenceBody).toContain("page=1&size=40");
    expect(graphEvidenceBody).toContain(
      "const identityFacts = pageItems(await responseData(facts))",
    );
    expect(graphEvidenceBody).toContain("identityFacts.some");
    expect(graphEvidenceBody).toContain(
      "const fragmentFacts = pageItems(await responseData(fragmentFactsResponse))",
    );
    expect(graphEvidenceBody).toContain("fragmentFacts.some");
    expect(graphEvidenceBody).not.toContain("query=${encodeURIComponent");

    const aiEvidenceBody = source.slice(
      source.indexOf("async function verifyAiWorkflowSafetyBoundary"),
      source.indexOf("async function prepareKnowledgeCandidate"),
    );
    expect(aiEvidenceBody).toContain('readinessData, "modelInvocationAllowed"');
    expect(aiEvidenceBody).toContain('recordField(readinessData, "items")');
    expect(aiEvidenceBody).toContain("requiredReadinessItems.length");
  });

  it("builds a platform diagnostic-item knowledge request that matches report interpretation E2E", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    expect(auth.platformDiagnosticItemKnowledgeIdentityRequest()).toMatchObject({
      tenant_id: "t-1",
      identitySlug: "lab-potassium",
      domain: "DIAGNOSTIC_ITEM",
      subject: "血钾检验说明书",
    });
    expect(auth.platformDiagnosticItemKnowledgeVersionRequest(1, 1)).toMatchObject({
      tenant_id: "t-1",
      versionNo: "V1",
      sourceDocumentId: 1,
      sourceVersionId: 1,
      content: expect.stringContaining("血钾检验"),
      riskLevel: "LOW",
    });
  });

  it("binds the local platform rehearsal rule to medication prescribing evaluations", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const rule = auth.platformRehearsalRuleRequest();

    expect(rule.triggers).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          trigger_point: "medication-prescribe",
          purpose: "RULE_EXECUTION",
          required_fields: ["patientId", "encounterId", "medications"],
        }),
      ]),
    );
  });

  it("keeps local rehearsal safety redline condition executable by the rule condition evaluator", () => {
    const source = readFileSync("e2e/support/auth.ts", "utf8");

    expect(source).toContain('fact: "medications[].dose"');
    expect(source).not.toContain('field: "medications[].dose"');
  });

  it("keeps platform UI login bound to the explicit login type switch when present", () => {
    const source = readFileSync("e2e/support/auth.ts", "utf8");

    expect(source).toContain("locator('[aria-label=\"登录类型切换\"]')");
    expect(source).toContain('getByRole("button", { name: "平台治理", exact: true })');
    expect(source).toContain("platformTenantSwitch.or(platformHeading)");
    expect(source).toContain('toHaveAttribute("aria-pressed", "true")');
    expect(source).toContain('getByRole("heading", { name: "登录平台治理" })');
  });

  it("requires MFA frontdesk rehearsal to disable the temporary platform admin", () => {
    const source = readFileSync("e2e/mfa-login-frontdesk.spec.ts", "utf8");

    expect(source).toContain("disableMfaAdminAccount");
    expect(source).toContain("/compliance/users/${encodeURIComponent(userId)}/status");
    expect(source).toContain('status: "DISABLED"');
    expect(source).toContain("recordMfaLoginStage");
    expect(source).toContain("attachMfaLoginScenarioEvidence");
    expect(source).toContain("mfa-login-scenario-codes");
    expect(source).toContain('code: "S14"');
    expect(source).toContain("配置中心读取上线默认 MFA 关闭");
    expect(source).toContain("创建 MFA 临时平台管理员账号");
    expect(source).toContain("临时账号完成首次改密并绑定 TOTP");
    expect(source).toContain("配置中心临时开启 MFA");
    expect(source).toContain("登录页要求已绑定账号完成 MFA 验证");
    expect(source).toContain("前台提交真实 TOTP 验证并进入工作台");
    expect(source).toContain("验证后回读权限画像与 MFA 状态");
    expect(source).toContain("恢复 MFA 上线默认关闭状态");
    expect(source).toContain("停用 MFA 演练临时管理员账号");
    expect(source).toContain("FOUNDATION_GOVERNANCE");
    expect(source).toContain("COMPLIANCE_OPERATIONS");
  });

  it("requires service organization rehearsal to cover first-login organization tree setup", () => {
    const source = readFileSync("e2e/service-organization-frontdesk.spec.ts", "utf8");

    expect(source).toContain("provisionServiceOrganizationFromUi");
    expect(source).toContain("completeTenantAdminFirstLoginFromUi");
    expect(source).toContain("createFacilityCampusDepartmentAndWardFromUi");
    expect(source).toContain("createClinicalUserWithDepartmentScopeFromUi");
    expect(source).toContain("disableProvisionedAccountsFromAdminSession");
    expect(source).toContain("adminUserId");
    expect(source).toContain("/engine/org/org-units");
    expect(source).toContain("/compliance/personnel");
    expect(source).toContain("/security/me");
    expect(source).toContain("停用服务机构演练临床账号");
    expect(source).toContain("停用服务机构演练管理员账号");
    expect(source).toContain("assertProvisionedOrgTree");
    expect(source).toContain("assertClinicalUserSecurityScope");
    expect(source).toContain("recordServiceOrganizationStage");
    expect(source).toContain("attachServiceOrganizationScenarioEvidence");
    expect(source).toContain("service-organization-scenario-codes");
    expect(source).toContain('code: "S1"');
    expect(source).toContain('code: "S14"');
    expect(source).toContain("前台开通服务机构");
    expect(source).toContain("机构管理员首次登录并改密");
    expect(source).toContain("前台创建医疗机构、院区、科室与病区");
    expect(source).toContain("前台回读服务机构组织树");
    expect(source).toContain(
      'organizationLevels: ["HOSPITAL", "CAMPUS_OR_MEMBER", "DEPARTMENT", "WARD"]',
    );
    expect(source).toContain("campusReadbackVerified");
    expect(source).toContain("wardReadbackVerified");
    expect(source).toContain('levelLabel: "院区"');
    expect(source).toContain('levelLabel: "病区/护理单元"');
    expect(source).toContain('"所属病区"');
    expect(source).toContain(
      "primaryAppointment?: { organizationId?: string; departmentId?: string; wardId?: string }",
    );
    expect(source).toContain(
      "expect(created.data?.primaryAppointment?.wardId).toBe(options.ward.id)",
    );
    expect(source).toContain("前台创建临床账号并绑定科室职责范围");
    expect(source).toContain("临床账号首次登录后读取权限画像");
    expect(source).toContain("前台停用演练账号");
    expect(source).not.toContain(".catch(() => null)");
  });

  it("requires diagnosis knowledge rehearsal to attach bounded asset-production coverage evidence", () => {
    const source = readFileSync("e2e/diagnosis-knowledge-maintenance.spec.ts", "utf8");

    expect(source).toContain("recordDiagnosisKnowledgeStage");
    expect(source).toContain("attachDiagnosisKnowledgeScenarioEvidence");
    expect(source).toContain("diagnosis-knowledge-scenario-codes");
    expect(source).toContain('code: "S3"');
    expect(source).toContain("前台登记标准发现项术语");
    expect(source).toContain("前台创建证据完整诊断资产草稿");
    expect(source).toContain("前台登记诊断标准");
    expect(source).toContain("前台登记验证病例");
    expect(source).toContain("MEDICAL_ASSET");
    expect(source).toContain("DISEASE_DIAGNOSIS");
    expect(source).toContain("CLINICAL_SPECIALTIES");
    expect(source).not.toContain('code: "S16"');
  });

  it("requires insurance frontdesk rehearsal to be driven by an active CLAIM evaluation indicator", () => {
    const source = readFileSync("e2e/real-frontdesk-rehearsal.spec.ts", "utf8");
    const adapterHubSource = readFileSync("src/pages/tenant/AdapterHub.tsx", "utf8");

    expect(source).toContain("createActiveClaimEvaluationIndicatorFromUi");
    expect(source).toContain('subjectType: "CLAIM"');
    expect(source).toContain("runInsuranceAuditFromUi(");
    expect(source).toContain("claimIndicator");
    expect(source).toContain("assertInsuranceAuditUsesEvaluationRun");
    expect(source).toContain("evaluationRunId");
    expect(source).toContain("INSURANCE_RULE_MANUAL");
    expect(source).toContain("selectInsuranceAuditSnapshotFromUi(page, snapshot)");
    expect(source).toContain("`选择 ${snapshot.snapshotId}`");
    const insuranceAuditBody = source.slice(
      source.indexOf("async function runInsuranceAuditFromUi"),
      source.indexOf("async function assertInsuranceAuditUsesEvaluationRun"),
    );
    expect(insuranceAuditBody).not.toContain(
      'getByRole("button", { name: "选择第 1 个病案快照" })',
    );
    const insuranceSnapshotSelectorBody = source.slice(
      source.indexOf("async function selectInsuranceAuditSnapshotFromUi"),
      source.indexOf("async function closeQualityRectificationFromAlertsUi"),
    );
    expect(insuranceSnapshotSelectorBody).toContain("snapshot.snapshotId");
    expect(insuranceSnapshotSelectorBody).toContain("name: `选择 ${snapshot.snapshotId}`");
    expect(insuranceSnapshotSelectorBody).not.toContain("选择第 1 个病案快照");
    const adapterCreationBody = source.slice(
      source.indexOf("async function createAdapterFromUi"),
      source.indexOf("async function createIntegrationOnboardingFromUi"),
    );
    expect(adapterCreationBody).toContain(
      'await dialog.getByLabel("目标标准字典").fill("ICD-10");',
    );
    expect(adapterCreationBody).toContain(
      'await searchDialogOption(page, dialog, "术语分类", "诊断", "诊断");',
    );
    const adapterFieldMappingBody = adapterHubSource.slice(
      adapterHubSource.indexOf('name={[name, "category"]}'),
      adapterHubSource.indexOf("{fields.length > 1 &&"),
    );
    expect(adapterFieldMappingBody).toContain("showSearch");
    expect(adapterFieldMappingBody).toContain('optionFilterProp="label"');
    expect(adapterFieldMappingBody).toContain("TERM_CATEGORY_OPTIONS");
    const antdOptionBody = source.slice(
      source.indexOf("async function dispatchAntdOptionByText"),
      source.indexOf("async function openAntdSelect"),
    );
    expect(antdOptionBody).toContain(".rc-virtual-list-holder");
    expect(antdOptionBody).toContain("page.mouse.wheel");
    expect(antdOptionBody).toContain("scanPositions");
    expect(antdOptionBody).toContain("requestAnimationFrame");
    expect(antdOptionBody).toContain("wheelAntdVirtualDropdownToOption");
    expect(antdOptionBody).toContain("selectOpenAntdOptionByKeyboard");
    expect(antdOptionBody).not.toContain('new WheelEvent("wheel"');
    expect(antdOptionBody).not.toContain("holder.scrollTop = scrollTop");
    expect(antdOptionBody).not.toContain("if (holder.scrollTop > 0)");
    const runtimeActivationBody = source.slice(
      source.indexOf("async function activateHospitalRuntimeWithClaimIndicatorFromUi"),
      source.indexOf("async function assertCurrentRuntimeContainsClaimIndicator"),
    );
    expect(runtimeActivationBody).toContain(
      "await deselectUnrelatedHospitalLocalCandidates(page, [",
    );
    expect(runtimeActivationBody).toContain("claimIndicator.indicatorCode");
    expect(runtimeActivationBody).toContain("followupTemplate.templateCode");
    expect(runtimeActivationBody).toContain("followupTemplate");
    expect(runtimeActivationBody).toContain("selectHospitalLocalFollowupTemplateCandidate");
    expect(runtimeActivationBody).toMatch(
      /deselectUnrelatedHospitalLocalCandidates\(page, \[[\s\S]*selectHospitalLocalClaimIndicatorCandidate/,
    );
    expect(runtimeActivationBody).toMatch(
      /selectHospitalLocalClaimIndicatorCandidate[\s\S]*selectHospitalLocalFollowupTemplateCandidate/,
    );
    expect(runtimeActivationBody).toContain("assertRuntimeReleaseRequestContainsFollowupTemplate");
    expect(runtimeActivationBody).toContain('hasText: "本院 · 随访内容"');
    expect(runtimeActivationBody).toContain("启用本院随访内容");
    expect(runtimeActivationBody).not.toContain('hasText: "本院 · 随访方案内容"');
    expect(runtimeActivationBody).not.toContain("启用本院随访方案内容");
    const followupPlanBody = source.slice(
      source.indexOf("async function generateFollowupPlanAndHandlePatientFeedbackFromUi"),
      source.indexOf("async function chooseDialogOption"),
    );
    expect(followupPlanBody).toContain("snapshot.snapshotId");
    expect(followupPlanBody).toContain('`button[data-snapshot-id="${snapshot.snapshotId}"]`');
    expect(followupPlanBody).not.toContain("选择第 1 个随访上下文快照");
  });

  it("requires real frontdesk resource evidence to aggregate the 13 standard patient resource consumer matrix", () => {
    const sources = [
      "e2e/medication-safety-frontdesk.spec.ts",
      "e2e/pharmacy-review-antimicrobial-frontdesk.spec.ts",
      "e2e/diagnostic-critical-value-frontdesk.spec.ts",
      "e2e/nursing-continuity-frontdesk.spec.ts",
      "e2e/surgery-anesthesia-transfusion-frontdesk.spec.ts",
      "e2e/real-frontdesk-rehearsal.spec.ts",
    ].map((file) => readFileSync(file, "utf8"));
    const joined = sources.join("\n");

    for (const source of sources) {
      expect(source).toContain("standardPatientResourceConsumerMatrix");
    }
    for (const resourceType of [
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
    ]) {
      expect(joined).toContain(`resourceType: "${resourceType}"`);
    }
    expect(joined).toContain("consumerEvidencePaths");
    expect(joined).toContain("auditEvidencePaths");
    expect(joined).toContain("sourceIdPath");
    expect(joined).toContain('consumer: "INSURANCE_AUDIT"');
    expect(joined).toContain("insuranceAudit.evaluationRunId");
    expect(joined).toContain("qualityRectification.taskId");
    expect(joined).toContain("evaluationRunVerified: true");
    expect(joined).toContain("qualityRectificationVerified: true");
    expect(joined).not.toContain("13 类标准患者资源全量上线完成");
    expect(joined).not.toContain("完整上线验收已完成");
  });

  it("requires CDSS frontdesk rehearsal to select the exact active context snapshot", () => {
    const source = readFileSync("e2e/real-frontdesk-rehearsal.spec.ts", "utf8");

    const cdssBody = source.slice(
      source.indexOf("async function runCdssRecommendationFromUi"),
      source.indexOf("function assertRuntimeRecommendationEvidence"),
    );
    expect(cdssBody).toContain("snapshot.snapshotId");
    expect(cdssBody).toContain("name: `选择 ${snapshot.snapshotId}`");
    expect(cdssBody).not.toContain('name: "选择第 1 个临床快照"');
  });

  it("requires CDSS declarative runtime asset rehearsal to prove frontdesk creation and recommendation materialization", () => {
    const source = readFileSync("e2e/cdss-runtime-declarative-assets.spec.ts", "utf8");

    expect(source).toContain("createDeclarativeAssetFromFrontdesk");
    expect(source).toContain("VALUE_SET.CDSS.RUNTIME");
    expect(source).toContain("FORMULA.CDSS.RUNTIME");
    expect(source).toContain("ACTION_CARD.CDSS.RUNTIME");
    expect(source).toContain("/engine/authoring/declarative-assets");
    expect(source).toContain("/engine/rule/rules");
    expect(source).toContain(
      "/engine/rule/rules/${encodeURIComponent(ruleId)}/governance/transitions",
    );
    expect(source).toContain("/engine/rule/rules/${encodeURIComponent(ruleId)}/test");
    expect(source).toContain("assertRuleReleaseTestRunPassed");
    expect(source).toContain("publishEvidence: ruleGovernancePublishEvidence(targetState)");
    expect(source).toContain("qualityGate");
    expect(source).not.toContain("releaseEvidence: [`${targetState} 推进由真实 E2E 记录`]");
    expect(source).toContain("declarativeRuntime = await activateRuntimeWithDeclarativeAssets");
    expect(source).toContain("ruleTestSnapshot.runtimeReleaseId");
    expect(source).toContain("toBe(declarativeRuntime.releaseId)");
    expect(source).toContain("readHospitalRuntimeCandidate");
    expect(source).toContain("runtime-candidates?assetType=RULE");
    expect(source).toContain('versionId.startsWith("av-")');
    expect(source).toContain(
      "/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases",
    );
    expect(source).toContain("/cdss/fatigue");
    expect(source).toContain('getByRole("button", { name: "登记触发评估" })');
    expect(source).toContain('button[data-snapshot-id="');
    expect(source).not.toContain("name: `选择 ${snapshot.snapshotId}`");
    expect(source).toContain("/engine/recommendations:evaluate");
    expect(source).toContain("readRecommendationTriggerDiagnose");
    expect(source).toContain(
      "/engine/recommendations/triggers/${encodeURIComponent(triggerId)}/diagnose",
    );
    expect(source).toContain("relatedCardIds");
    expect(source).toContain("findMaterializedRecommendationCard");
    expect(source).toContain("/engine/recommendations/cards/${encodeURIComponent(cardId)}");
    expect(source).toContain("assertRecommendationMaterializedDeclarativeAssets");
    expect(source).toContain("runtimeAssetEvidence");
    expect(source).toContain("createdAssets");
    expect(source).toContain("extensions.local.frontdeskContext.heightCm");
    expect(source).toContain("extensions.local.frontdeskContext.weightKg");
    expect(source).toContain("contextSnapshotId");
    expect(source).toContain("resources.encounters[0].encounterId");
    expect(source).toContain("responseCardIds");
    expect(source).not.toContain("resources.encounters.0.encounterId");
    expect(source).not.toContain("cards.0.cardId");
    expect(source).not.toContain('textField(evaluation, "cards[0].cardId")');
    expect(source).toContain("cdss-runtime-declarative-assets-codes");
    expect(source).toContain("attachCdssRuntimeDeclarativeAssetEvidence");
    expect(source).toContain("推荐卡解释证明三类资产按当前机构生效版本物化消费");
    expect(source).not.toContain("page.route(");
    expect(source).not.toContain("选择第 1 个临床快照");
    expect(
      source.indexOf("declarativeRuntime = await activateRuntimeWithDeclarativeAssets"),
    ).toBeLessThan(source.indexOf("ruleTestSnapshot = await createClinicalContextFromFrontdesk"));
    expect(
      source.indexOf("ruleTestSnapshot = await createClinicalContextFromFrontdesk"),
    ).toBeLessThan(source.indexOf("createAndPublishRuleReferencingDeclarativeAssets"));
    expect(source.indexOf("readHospitalRuntimeCandidate")).toBeLessThan(
      source.indexOf("finalRuntime = await activateRuntimeWithDeclarativeAssets"),
    );
    expect(
      source.indexOf("finalRuntime = await activateRuntimeWithDeclarativeAssets"),
    ).toBeLessThan(source.indexOf("snapshot = await createClinicalContextFromFrontdesk"));
  });

  it("requires medication safety frontdesk rehearsal to prove SAFETY/CDSS_RISK/RULE and human confirmation", () => {
    const source = readFileSync("e2e/medication-safety-frontdesk.spec.ts", "utf8");

    expect(source).toContain("medication-safety-frontdesk-codes");
    expect(source).toContain("attachMedicationSafetyEvidence");
    expect(source).toContain('"SAFETY"');
    expect(source).toContain('"CDSS_RISK"');
    expect(source).toContain('"RULE"');
    expect(source).toContain('"CLINICAL_RUNTIME"');
    expect(source).toContain("medication-prescribe");
    expect(source).toContain("/engine/cdss/risk-matrix");
    expect(source).toContain("/engine/safety/redlines");
    expect(source).toContain("/engine/safety/redlines:dry-run");
    expect(source).toContain("/engine/safety/redlines:promote");
    expect(source).toContain("/engine/terminology/terms/standard");
    expect(source).toContain("/engine/terminology/terms/local");
    expect(source).toContain("/engine/terminology/mappings/candidates");
    expect(source).toContain("/engine/terminology/assets/drafts");
    expect(source).toContain("terminologyCoverageGateActivated");
    expect(source).toContain("TERMINOLOGY");
    expect(source).toContain("const localCode = `J01C-${suffix}`");
    expect(source).not.toContain('const localCode = "J01C";');
    const medicationTerminologyBody = source.slice(
      source.indexOf("async function generateAndConfirmMedicationSafetyTermMapping"),
      source.indexOf("async function waitForMedicationSafetyTerminologyCandidate"),
    );
    expect(medicationTerminologyBody).toContain("standardTermId");
    const medicationCandidateWaitBody = source.slice(
      source.indexOf("async function waitForMedicationSafetyTerminologyCandidate"),
      source.indexOf("async function createAndPublishMedicationSafetyRule"),
    );
    expect(medicationCandidateWaitBody).toContain(
      'numberField(item, "standardTermId") === options.standardTermId',
    );
    expect(medicationCandidateWaitBody).not.toContain("includes(options.localCode)");
    expect(source).toContain("运营员补齐 ATC:J01C 术语映射并激活到当前机构生效版本");
    expect(source).toContain("/engine/rule/rules");
    expect(source).toContain(
      "/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases",
    );
    expect(source).toContain("/engine/recommendations:evaluate");
    expect(source).toContain(
      "/engine/recommendations/triggers/${encodeURIComponent(triggerId)}/diagnose",
    );
    expect(source).toContain("/engine/recommendations/cards/${encodeURIComponent(cardId)}");
    expect(source).toContain("allergyIntolerances[].code");
    expect(source).toContain("AllergyIntolerance");
    expect(source).toContain("Medication");
    expect(source).toContain("PHARMACIST_REVIEWED");
    expect(source).toContain("PHYSICIAN_CONFIRMATION");
    expect(source).toContain("requiresPhysicianConfirmation: true");
    expect(source).toContain("autoExecutionAllowed: false");
    expect(source).toContain('button[data-snapshot-id="');
    expect(source).toContain("relatedCardIds");
    expect(source).toContain("contextSnapshotId");
    expect(source).toContain("runtimeReleaseId");
    expect(source).toContain('matchType") === "CLINICAL_REDLINE"');
    expect(source).toContain('cardStatus, "药师复核不能关闭医生待确认链路"');
    expect(source).toContain("noAutoOrder: true");
    expect(source).not.toContain("page.route(");
    expect(source).not.toContain("page.waitForTimeout");
    expect(source).not.toContain("cards[0]");
    expect(source).not.toContain("cards.0.cardId");
    expect(source).not.toContain('riskMatrixId: "risk-matrix-local-rehearsal"');
    expect(source).not.toContain('riskMatrixVersion: "4"');
    expect(source).not.toContain("third-party-system-family-codes");
    expect(source).not.toContain("临床、药师与运营员围绕");
    expect(source.indexOf("createMedicationSafetyRiskMatrix")).toBeLessThan(
      source.indexOf("createPromotedMedicationAllergyRedline"),
    );
    expect(source.indexOf("createPromotedMedicationAllergyRedline")).toBeLessThan(
      source.indexOf("createMedicationSafetyTerminologyGate"),
    );
    expect(source.indexOf("createMedicationSafetyTerminologyGate")).toBeLessThan(
      source.indexOf("const runtime = await activateRuntimeWithMedicationSafetyAssets"),
    );
    expect(
      source.indexOf("const runtime = await activateRuntimeWithMedicationSafetyAssets"),
    ).toBeLessThan(
      source.indexOf("const snapshot = await createMedicationSafetyContextFromFrontdesk"),
    );
    expect(
      source.indexOf("const snapshot = await createMedicationSafetyContextFromFrontdesk"),
    ).toBeLessThan(
      source.indexOf(
        "const recommendation = await triggerMedicationSafetyRecommendationFromFrontdesk",
      ),
    );
    expect(source.indexOf("triggerMedicationSafetyRecommendationFromFrontdesk")).toBeLessThan(
      source.indexOf("completePharmacistAndPhysicianFeedback"),
    );
  });

  it("requires asset-specific runtime rollback negative evidence before declaring rollback representative coverage", () => {
    const cdssSource = readFileSync("e2e/cdss-runtime-declarative-assets.spec.ts", "utf8");
    const medicationSource = readFileSync("e2e/medication-safety-frontdesk.spec.ts", "utf8");
    const criticalSource = readFileSync("e2e/critical-emergency-icu-frontdesk.spec.ts", "utf8");
    const pathwaySource = readFileSync("e2e/pathway-lifecycle-frontdesk.spec.ts", "utf8");
    const qualitySource = readFileSync(
      "e2e/quality-management-entry-core-actions-rehearsal.spec.ts",
      "utf8",
    );

    for (const source of [
      cdssSource,
      medicationSource,
      criticalSource,
      pathwaySource,
      qualitySource,
    ]) {
      expect(source).toContain("rollbackNegativeEvidence");
      expect(source).toContain("runtime-releases:rollback");
      expect(source).toContain("currentRuntimeReadbackVerified");
      expect(source).toContain("runtimeConsumerReadbackVerified");
      expect(source).toContain("consumerProbeMatchedRemovedAssets");
    }
    expect(cdssSource).toContain("VALUE_SET");
    expect(cdssSource).toContain("FORMULA");
    expect(medicationSource).toContain("SAFETY");
    expect(medicationSource).toContain("CDSS_RISK");
    expect(criticalSource).toContain("PATHWAY");
    expect(pathwaySource).toContain("PATHWAY");
    expect(pathwaySource).toContain("ORDER_SET");
    expect(qualitySource).toContain("EVALUATION");
  });

  it("requires terminology, field catalog and pathway rehearsals to attach dedicated release-contract evidence", () => {
    const terminologySource = readFileSync(
      "e2e/s2-s4-terminology-integration-rehearsal.spec.ts",
      "utf8",
    );
    const diagnosticSource = readFileSync(
      "e2e/diagnostic-critical-value-frontdesk.spec.ts",
      "utf8",
    );
    const pathwaySource = readFileSync("e2e/pathway-lifecycle-frontdesk.spec.ts", "utf8");

    for (const source of [terminologySource, diagnosticSource, pathwaySource]) {
      expect(source).toContain("dedicatedReleaseContractEvidence");
      expect(source).toContain("productionRoute");
      expect(source).toContain("releaseContract");
      expect(source).toContain("runtimeConsumerReadbackVerified");
    }
    expect(terminologySource).toContain("S2_S4_TERMINOLOGY_MAPPING_RUNTIME_CONTRACT");
    expect(terminologySource).toContain("producerVerified");
    expect(terminologySource).toContain("reviewerVerified");
    expect(terminologySource).toContain("SIGNED_WEBHOOK_INBOUND_NORMALIZATION");
    expect(diagnosticSource).toContain("DIAGNOSTIC_REPORT_INTERPRETATION_FIELD_CONTRACT");
    expect(diagnosticSource).toContain("FIELD_CATALOG");
    expect(diagnosticSource).toContain("platformBaselineVerified");
    expect(pathwaySource).toContain("SPECIAL_DISEASE_PATHWAY_ENTRY_AND_ADVANCE_CONTRACT");
    expect(pathwaySource).toContain("templateLifecycleVerified");
    expect(pathwaySource).toContain('"PATHWAY", "ORDER_SET"');
  });

  it("requires quality management rehearsal to prove EVALUATION runtime consumer supply chain before closing the known gap", () => {
    const source = readFileSync(
      "e2e/quality-management-entry-core-actions-rehearsal.spec.ts",
      "utf8",
    );

    expect(source).toContain("evaluationAssetSupplyChainEvidence");
    expect(source).toContain("runtimeActivationVerified");
    expect(source).toContain("runtimeConsumerReadbackVerified");
    expect(source).toContain("insuranceAuditEvaluationRunVerified");
    expect(source).toContain("findingBoundToIndicatorVerified");
    expect(source).toContain("QUALITY_MANAGEMENT_EVALUATION_INDICATOR");
    expect(source).toContain("runtime-candidates?assetType=EVALUATION");
    expect(source).toContain("/engine/integration/knowledge-runtime/runtime-release/current");
  });

  it("requires nursing continuity rehearsal to use real nursing resources, followup backflow and strict evidence", () => {
    const mpiSource = readFileSync("src/pages/clinical/Mpi.tsx", "utf8");
    const hooksSource = readFileSync("src/shared/api/hooks.ts", "utf8");
    const followupSource = readFileSync("src/pages/clinical/Followup.tsx", "utf8");
    const e2eSource = readFileSync("e2e/nursing-continuity-frontdesk.spec.ts", "utf8");

    expect(mpiSource).toContain("护理高风险评估");
    expect(mpiSource).toContain('aria-label="护理评估类型"');
    expect(mpiSource).toContain('aria-label="护理风险等级"');
    expect(mpiSource).toContain('aria-label="护理计划路径"');
    expect(hooksSource).toContain("buildFrontdeskNursingAssessmentResources");
    expect(hooksSource).toContain("buildFrontdeskCarePlanResources");
    expect(hooksSource).toContain("nursingAssessments");
    expect(hooksSource).toContain("carePlans");
    expect(hooksSource).not.toContain("nursingAssessments: []");
    expect(hooksSource).not.toContain("carePlans: []");
    expect(hooksSource).toContain("nursingAssessmentCount");
    expect(hooksSource).toContain("carePlanCount");
    expect(hooksSource).toContain("FollowupResultBackflowRequest");
    expect(hooksSource).toContain("/engine/followup/results");
    expect(followupSource).toContain("handleBackflowResult");
    expect(followupSource).toContain("随访结果回流");
    expect(followupSource).toContain("回流上下文");
    expect(e2eSource).toContain("nursing-continuity-frontdesk-codes");
    expect(e2eSource).toContain("attachNursingContinuityEvidence");
    expect(e2eSource).toContain('"S20"');
    expect(e2eSource).toContain('"S35"');
    expect(e2eSource).toContain('"FOLLOWUP"');
    expect(e2eSource).toContain("NursingAssessment");
    expect(e2eSource).toContain("CarePlan");
    expect(e2eSource).toContain("FollowUp");
    expect(e2eSource).toContain('getByLabel("护理评估类型")');
    expect(e2eSource).toContain('getByLabel("护理风险等级")');
    expect(e2eSource).toContain('getByLabel("护理计划路径")');
    expect(e2eSource).toContain("/engine/followup/results");
    expect(e2eSource).toContain("contextSnapshotId");
    expect(e2eSource).toContain("generationExplanation");
    expect(e2eSource).not.toContain("page.route(");
    expect(e2eSource).not.toContain("page.waitForTimeout");
    expect(e2eSource).not.toContain("third-party-system-family-codes");
  });

  it("requires pharmacy review antimicrobial rehearsal to prove real bidirectional review, monitoring facts and rectification closure", () => {
    const mpiSource = readFileSync("src/pages/clinical/Mpi.tsx", "utf8");
    const hooksSource = readFileSync("src/shared/api/hooks.ts", "utf8");
    const e2eSource = readFileSync("e2e/pharmacy-review-antimicrobial-frontdesk.spec.ts", "utf8");
    const pharmacyInboundWebhookSource = e2eSource.slice(
      e2eSource.indexOf("async function postSignedPharmacyReviewInbound"),
      e2eSource.indexOf("async function waitForClinicalEventProcessed"),
    );
    const qualityRunPayload = e2eSource.slice(
      e2eSource.indexOf('const run = await postApi(page, "/engine/evaluation/runs"'),
      e2eSource.indexOf('await expectOk(run, "创建药事治理质量问题")'),
    );

    expect(mpiSource).toContain("监测指标");
    expect(mpiSource).toContain('aria-label="监测指标"');
    expect(hooksSource).toContain("buildFrontdeskObservationResources");
    expect(hooksSource).toContain("observations");
    expect(hooksSource).toContain("observationCount");
    expect(e2eSource).toContain("pharmacy-review-antimicrobial-frontdesk-codes");
    expect(e2eSource).toContain("attachPharmacyReviewAntimicrobialEvidence");
    expect(e2eSource).toContain(
      "临床用户按医生和药师业务任职与运营员、平台管理员完成本轮抗菌药物审方代表切片回传、推荐确认和整改闭环",
    );
    expect(e2eSource).not.toContain("临床用户、药师、运营员与平台管理员完成本轮");
    expect(e2eSource).toContain('"S18"');
    expect(e2eSource).toContain('"S31"');
    expect(e2eSource).toContain('"PHARMACY_REVIEW"');
    expect(e2eSource).toContain('"TERMINOLOGY"');
    expect(e2eSource).toContain('"SAFETY"');
    expect(e2eSource).toContain('"CDSS_RISK"');
    expect(e2eSource).toContain('"RULE"');
    expect(e2eSource).toContain('"ACTION_CARD"');
    expect(e2eSource).toContain("ANTIMICROBIAL_RESTRICTION");
    expect(e2eSource).toContain('standardSystem: "ATC"');
    expect(e2eSource).toContain('standardCode: "J01C"');
    expect(e2eSource).toContain('standardSystem: "ICD-10"');
    expect(e2eSource).toContain('standardCode: "J18.900"');
    expect(e2eSource).toContain("sourceSystem: reviewSourceSystem");
    expect(e2eSource).toContain("pharmacyReviewDiagnosis");
    expect(e2eSource).toContain('targetDictionaryKey: "ICD-10"');
    expect(e2eSource).toContain('sourcePath: "/observationCode"');
    expect(e2eSource).toContain('targetPath: "/observations/0/code"');
    expect(e2eSource).toContain('sourcePath: "/pct"');
    expect(e2eSource).toContain('targetPath: "/observations/0/valueNumeric"');
    expect(e2eSource).toContain('sourcePath: "/pharmacyReview/reviewResult"');
    expect(e2eSource).toContain('targetPath: "/pharmacyReview/pharmacistOpinion"');
    expect(e2eSource).toContain('textField(item, "sourceSystem") === options.sourceSystem');
    expect(e2eSource).toContain("Medication");
    expect(e2eSource).toContain("AllergyIntolerance");
    expect(e2eSource).toContain("Condition");
    expect(e2eSource).toContain("Observation");
    expect(e2eSource).toContain(
      '{ fact: "conditions[].code", operator: "equals", value: "J18.900" }',
    );
    expect(e2eSource).not.toContain(
      '{ fact: "conditions[].code", operator: "exists", value: true }',
    );
    expect(e2eSource).toContain('getByLabel("监测指标")');
    expect(e2eSource).toContain("/engine/integration/adapters");
    expect(e2eSource).toContain("/engine/integration/webhooks");
    expect(e2eSource).toContain("/engine/integration/webhooks/test");
    expect(e2eSource).toContain("/engine/integration/messages/outbound");
    expect(e2eSource).toContain('baseUrl: "https://pharmacy-review.example.test"');
    expect(e2eSource).toContain('outboundPath: "/review-results"');
    expect(e2eSource).not.toContain("endpointUrl");
    expect(e2eSource).not.toContain("deliveryPath");
    expect(e2eSource).toContain("waitForPharmacyReviewCompensation");
    expect(e2eSource).toContain('lastStatus === "NOT_CONNECTED"');
    expect(e2eSource).toMatch(
      /const compensationStatus = requireText\(\s*textField\(compensation, "status"\)/u,
    );
    expect(e2eSource).toContain(
      'const compensationRequired = compensationStatus === "NOT_CONNECTED"',
    );
    expect(e2eSource).toContain("PHARMACY_REVIEW 出站补偿日志");
    expect(e2eSource).toContain("进入非诚实断连状态");
    expect(e2eSource).toContain("compensationStatus,");
    expect(e2eSource).toContain(
      "/engine/integration/webhooks/${encodeURIComponent(webhookId)}/inbound",
    );
    expect(e2eSource).toContain("平台管理员访问真实前台并经真实服务创建");
    expect(e2eSource).toContain("const clinicalEventId = requireText");
    expect(e2eSource).toContain("waitForClinicalEventProcessed");
    expect(e2eSource).toContain('lastDetail.status === "PROCESSED"');
    expect(e2eSource).toContain('runtimeReleaseId: textField(data, "runtimeReleaseId")');
    expect(pharmacyInboundWebhookSource).not.toContain("linkedOutboundMessageId");
    expect(pharmacyInboundWebhookSource).not.toContain("outboundMessageId");
    expect(e2eSource).toContain("signedPayload: request.payload");
    expect(e2eSource).not.toContain("const sourcePayload = request.payload");
    expect(e2eSource).not.toContain("pharmacyReview: sourcePayload.pharmacyReview");
    expect(e2eSource).toContain("/engine/recommendations:evaluate");
    expect(e2eSource).toContain("/engine/recommendations/cards/${encodeURIComponent(cardId)}");
    expect(e2eSource).toContain(
      'const runtimeAssetEvidence = arrayField(ruleExplanation, "runtimeAssetEvidence");',
    );
    expect(e2eSource).toContain('textField(item, "assetType") === "ACTION_CARD"');
    expect(e2eSource).not.toContain("runtimeAssetEvidence: [");
    expect(e2eSource).toContain("PHARMACIST_REVIEWED");
    expect(e2eSource).toContain("persistedFeedback");
    expect(e2eSource).toContain("pharmacistPersisted");
    expect(e2eSource).toContain("physicianPersisted");
    expect(e2eSource).toContain('canonicalSessionRole: "clinical-user"');
    expect(e2eSource).toContain("PHYSICIAN_CONFIRMATION");
    expect(e2eSource).toContain("qualityRectification");
    expect(e2eSource).toContain("medicationSafetyDegradationEvidence");
    expect(e2eSource).toContain("S18__DEGRADATION");
    expect(e2eSource).toContain(
      "MEDICATION_SAFETY_PHARMACY_REVIEW_NOT_CONNECTED_LOCAL_RECOMMENDATION_CONTINUES",
    );
    expect(e2eSource).toContain("pharmacyHighRiskGovernanceEvidence");
    expect(e2eSource).toContain("readRectificationAuditEvidence");
    expect(e2eSource).toContain("/large-lists/audit-events/list");
    expect(e2eSource).toContain('resourceType: "quality_finding"');
    expect(e2eSource).toContain('resourceType: "rectification_task"');
    expect(e2eSource).toContain("auditEvidence");
    expect(e2eSource).toContain("permissionEvidence");
    expect(e2eSource).toContain("sixStateEvidence");
    expect(e2eSource).toContain('"S31__HIGH_RISK"');
    expect(e2eSource).toContain("PHARMACY_REVIEW_ANTIMICROBIAL_HIGH_RISK_GOVERNANCE_REVIEW");
    expect(e2eSource).toContain("/engine/rectifications");
    expect(e2eSource).toContain("localRehearsalQualityDepartmentId");
    expect(e2eSource).toContain("level=DEPARTMENT");
    expect(e2eSource).toContain("ancestorId=");
    expect(e2eSource).toContain('level: "DEPARTMENT"');
    expect(e2eSource).toContain(
      'const departmentId = await localRehearsalQualityDepartmentId(page, options.suffix);\n  await ensureReadySession(page, "engine-operator");',
    );
    expect(e2eSource).not.toContain('"quality-controller"');
    expect(e2eSource).toMatch(
      /await ensureReadySession\(page, "engine-operator"\);\s*const submit = await postApi\(\s*page,\s*`\/engine\/rectifications\/\$\{encodeURIComponent\(taskId\)\}\/submit`/u,
    );
    expect(e2eSource).toContain('await ensureReadySession(page, "platform-admin");');
    expect(e2eSource).not.toContain("sortOrder");
    expect(e2eSource).toContain('runType: "MANUAL_SAMPLE"');
    expect(e2eSource).toContain("manualSampleRuntimeReleaseId: options.runtimeReleaseId");
    expect(e2eSource).toContain("manualSampleContextSnapshotId: options.snapshot.snapshotId");
    expect(qualityRunPayload).not.toContain("runtimeReleaseId: options.runtimeReleaseId,");
    expect(qualityRunPayload).not.toContain("contextSnapshotId: options.snapshot.snapshotId,");
    expect(e2eSource).toContain("denominatorDefinition: JSON.stringify({");
    expect(e2eSource).toContain('fact: "recommendation.matchType"');
    expect(e2eSource).toContain('fact: "observation.pct"');
    expect(e2eSource).toContain("numeratorDefinition: JSON.stringify({");
    expect(e2eSource).toContain("exclusionDefinition: null");
    expect(e2eSource).not.toContain('denominatorDefinition: "本轮触发抗菌药物审方推荐卡的患者"');
    expect(e2eSource).not.toContain('numeratorDefinition: "审方意见和感染指标依据归档完整"');
    expect(e2eSource).not.toContain('exclusionDefinition: "无"');
    expect(e2eSource).not.toContain("const departmentId = await localRehearsalHospitalId(page);");
    expect(e2eSource).toContain('const blocksMainFlow = booleanField(data, "blocksMainFlow")');
    expect(e2eSource).toContain(
      'expect(blocksMainFlow, "审方出站断连不得阻断医生主流程").toBe(false)',
    );
    expect(e2eSource).toContain(
      'const initialCompensationRequired = booleanField(data, "compensationRequired")',
    );
    expect(e2eSource).toContain(
      'const compensationRequired = compensationStatus === "NOT_CONNECTED"',
    );
    expect(e2eSource).toContain(
      'expect(compensationRequired, "审方出站断连最终必须留下补偿证据").toBe(true)',
    );
    expect(e2eSource).toContain("noAutoOrder: true");
    expect(e2eSource).not.toContain("page.route(");
    expect(e2eSource).not.toContain("page.waitForTimeout");
    expect(e2eSource).not.toContain("third-party-system-family-codes");
    expect(e2eSource).not.toContain("平台管理员前台创建 PHARMACY_REVIEW 适配器");
    expect(e2eSource).not.toContain("完整药事治理已上线");
    expect(e2eSource).not.toContain("完整第三方药房审方系统族");
  });

  it("requires surgery anesthesia transfusion rehearsal to prove real S26 frontdesk chain without scope inflation", () => {
    const e2eSource = readFileSync("e2e/surgery-anesthesia-transfusion-frontdesk.spec.ts", "utf8");

    expect(e2eSource).toContain("surgery-anesthesia-transfusion-frontdesk-codes");
    expect(e2eSource).toContain("attachPeriopEvidence");
    expect(e2eSource).toContain('"S26"');
    expect(e2eSource).not.toContain('"S33"');
    expect(e2eSource).toContain("NURSING_ANESTHESIA_TRANSFUSION_ICU");
    expect(e2eSource).toContain("/adapter/hub");
    expect(e2eSource).toContain("/mpi");
    expect(e2eSource).toContain("/cdss/fatigue");
    expect(e2eSource).toContain("签署医嘱");
    expect(e2eSource).toContain('triggerType: "order-sign"');
    expect(e2eSource).not.toContain("PROCEDURE_ORDER");
    expect(e2eSource).toContain("waitForClinicalEventProcessed");
    expect(e2eSource).toContain('last.status === "PROCESSED"');
    expect(e2eSource).toContain("async function postSignedPeriopInbound(\n  page: Page,");
    expect(e2eSource).toContain(
      ') {\n  await ensureReadySession(page, "platform-admin");\n  const positive = options.positive ?? true;',
    );
    expect(e2eSource).toContain(
      'const localTermId = numberField(await responseData(local), "id");',
    );
    expect(e2eSource).toContain(
      'normalizedName: "手术室腹腔镜阑尾切除|OR-LAP-APP|47.0901|腹腔镜阑尾切除术"',
    );
    expect(e2eSource).toContain("localTermId,");
    expect(e2eSource).toContain("/engine/terminology/mappings/candidate-generation-jobs/");
    expect(e2eSource).toContain("generatedCount");
    expect(e2eSource).toContain(
      'expect(textField(jobData, "sourceSystem"), "术语候选任务必须绑定本轮来源系统").toBe(',
    );
    expect(e2eSource).toContain('numberField(item, "localTermId") === expected.localTermId');
    expect(e2eSource).toContain('numberField(item, "standardTermId") === expected.standardTermId');
    expect(e2eSource).toContain('textField(item, "generationJobCode") === jobCode');
    expect(e2eSource).toContain("confirmedMapping: mapping");
    expect(e2eSource).not.toContain("evidence.includes(expected.localCode)");
    expect(e2eSource).not.toContain(
      "waitForTerminologyCandidate(page, jobCode, options.localCode)",
    );
    expect(e2eSource).not.toContain("OR-MINOR-NEG");
    expect(e2eSource).toContain(
      "const ruleValidationAdapterId = await ensureTemporaryAdapterForRuleValidation(page, suffix);",
    );
    expect(e2eSource).not.toContain("adapterId: await ensureTemporaryAdapterForRuleValidation");
    expect(e2eSource).toContain("Procedure");
    expect(e2eSource).toContain("Observation");
    expect(e2eSource).toContain("Medication");
    expect(e2eSource).toContain("Document");
    expect(e2eSource).toContain("extensions.local.surgeryPlan");
    expect(e2eSource).toContain("extensions.local.anesthesiaAssessment");
    expect(e2eSource).toContain("extensions.local.transfusionRequest");
    expect(e2eSource).toContain("noAutoOrder: true");
    expect(e2eSource).toContain("noAutoTransfusion: true");
    expect(e2eSource).toContain("noAutoSurgery: true");
    const periopRuleCardMatcher = e2eSource.slice(
      e2eSource.indexOf("async function findPeriopRuleCard"),
      e2eSource.indexOf("async function completePeriopManualConfirmation"),
    );
    expect(periopRuleCardMatcher).toContain('textField(item, "assetType") === "ACTION_CARD"');
    expect(periopRuleCardMatcher).toContain(
      'textField(item, "contentHash") === options.runtime.actionCardAsset.contentHash',
    );
    expect(periopRuleCardMatcher).not.toContain('booleanField(item, "noAutoOrder")');
    expect(periopRuleCardMatcher).not.toContain('booleanField(item, "noAutoTransfusion")');
    expect(periopRuleCardMatcher).not.toContain('booleanField(item, "noAutoSurgery")');
    expect(e2eSource).toContain('canonicalSessionRole: "clinical-user"');
    expect(e2eSource).toContain("BUSINESS_FEEDBACK_ROLE_ONLY");
    expect(e2eSource).toContain('textField(item, "operatorRole") === "DOCTOR"');
    expect(e2eSource).toContain('textField(item, "reasonCode") === "CONFIRMED"');
    expect(e2eSource).not.toContain('operatorRole: "clinical-user",');
    expect(e2eSource).not.toContain('businessOperatorRole: "SURGEON",');
    expect(e2eSource).toContain("async function createAndPublishPeriopRule");
    expect(e2eSource).toContain('await ensureReadySession(page, "engine-operator");');
    expect(e2eSource).toContain(
      'async function createActiveEvaluationIndicator(page: Page, suffix: string, departmentId: string) {\n  await ensureReadySession(page, "engine-operator");',
    );
    expect(e2eSource).toContain("qualityRectification");
    expect(e2eSource).toContain("const { releaseId } = await activateRuntimeRelease(page, {");
    expect(e2eSource).not.toContain("const releaseId = await activateRuntimeRelease(page, {");
    expect(e2eSource).toContain("const byKey = new Map<string, RuntimeAssetSelection>();");
    expect(e2eSource).toContain("byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);");
    expect(e2eSource).toContain("完整围手术期系统");
    expect(e2eSource).toContain("完整手麻系统");
    expect(e2eSource).toContain("完整手术室系统");
    expect(e2eSource).toContain("完整输血系统");
    expect(e2eSource).toContain("护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖");
    expect(e2eSource).toContain("完整上线验收");
    expect(e2eSource).not.toContain("page.route(");
    expect(e2eSource).not.toContain("page.waitForTimeout");
    expect(e2eSource).not.toContain("third-party-system-family-codes");
    expect(e2eSource).not.toContain('"quality-controller"');
    expect(e2eSource).not.toContain('"surgeon"');
    expect(e2eSource).not.toContain('"anesthesiologist"');
    expect(e2eSource).not.toContain("完整 S26 已上线");
    expect(e2eSource).not.toContain("完整 S33 已上线");
    expect(e2eSource).not.toContain("完整上线验收已完成");
  });

  it("requires critical emergency ICU rehearsal to prove real S19/S24/S27 frontdesk chain without device-control or scope inflation", () => {
    const e2eSource = readFileSync("e2e/critical-emergency-icu-frontdesk.spec.ts", "utf8");

    expect(e2eSource).toContain("critical-emergency-icu-frontdesk-codes");
    expect(e2eSource).toContain("attachCriticalEmergencyIcuEvidence");
    expect(e2eSource).toContain('"S19"');
    expect(e2eSource).toContain('"S24"');
    expect(e2eSource).toContain('"S27"');
    expect(e2eSource).toContain("LIS_MONITORING_CRITICAL");
    expect(e2eSource).toContain("/adapter/hub");
    expect(e2eSource).toContain("/mpi");
    expect(e2eSource).toContain("/cdss/fatigue");
    expect(e2eSource).toContain("/workflow/todos");
    expect(e2eSource).toContain("async function completeCriticalEscalationTodo");
    expect(e2eSource).not.toContain("async function createAndCompleteCriticalEscalationTodo");
    expect(e2eSource).not.toContain('postApi(page, "/engine/workflow/todos"');
    expect(
      e2eSource.indexOf("const recommendation = await triggerCriticalRecommendationFromFrontdesk"),
    ).toBeLessThan(
      e2eSource.indexOf("const escalationTodo = await completeCriticalEscalationTodo"),
    );
    expect(
      e2eSource.indexOf("const escalationTodo = await completeCriticalEscalationTodo"),
    ).toBeLessThan(
      e2eSource.indexOf("const manualEscalation = await completeCriticalManualEscalation"),
    );
    expect(e2eSource).toContain('await chooseDialogOption(page, dialog, "触发时点", "查看患者")');
    expect(e2eSource).not.toContain(
      'await chooseDialogOption(page, dialog, "触发时点", "患者查看")',
    );
    expect(e2eSource).toContain('triggerType: "patient-view"');
    expect(e2eSource).toContain("waitForClinicalEventProcessed");
    expect(e2eSource).toContain('last.status === "PROCESSED"');
    expect(e2eSource).toContain("Observation");
    expect(e2eSource).toContain('eventType: "REPORT"');
    expect(e2eSource).not.toContain('eventType: "OBSERVATION"');
    expect(e2eSource).toContain('clinicalSetting: "ED"');
    expect(e2eSource).not.toContain('clinicalSetting: "EMERGENCY"');
    expect(e2eSource).toContain('encounterType: "ED"');
    expect(e2eSource).not.toContain('encounterType: "EMERGENCY"');
    expect(e2eSource).toContain("extensions.local.emergencyTriage");
    expect(e2eSource).toContain("emergencyTriageDegradationEvidence");
    expect(e2eSource).toContain("S24__DEGRADATION");
    expect(e2eSource).toContain(
      "CRITICAL_EMERGENCY_TRIAGE_NOT_CONNECTED_LOCAL_RECOMMENDATION_CONTINUES",
    );
    expect(e2eSource).toContain("extensions.local.criticalCare");
    expect(e2eSource).toContain("noAutoOrder: true");
    expect(e2eSource).toContain("noAutoTransfer: true");
    expect(e2eSource).toContain("noDeviceControl: true");
    expect(e2eSource).toContain("noAutoVentilatorChange: true");
    expect(e2eSource).toContain('canonicalSessionRole: "clinical-user"');
    expect(e2eSource).toContain(
      'expect(textField(persisted, "cardId"), "人工确认反馈必须绑定本轮推荐卡")',
    );
    expect(e2eSource).toContain(
      'expect(textField(completed, "patientId"), "完成响应必须绑定本轮患者")',
    );
    expect(e2eSource).toContain(
      'expect(textField(completed, "encounterId"), "完成响应必须绑定本轮就诊")',
    );
    expect(e2eSource).not.toContain("persistedWithCard");
    expect(e2eSource).not.toContain("patientId: options.snapshot.patientId");
    expect(e2eSource).not.toContain("encounterId: options.snapshot.encounterId");
    expect(e2eSource).toContain('textField(item, "operatorRole") === "DOCTOR"');
    expect(e2eSource).toContain('textField(item, "reasonCode") === "CONFIRMED"');
    expect(e2eSource).toContain("async function createAndPublishCriticalIcuRule");
    expect(e2eSource).toContain("async function createCriticalIcuPathwayAsset");
    expect(e2eSource).toContain(
      'const allowedStatuses = assetType === "PATHWAY" ? ["DRAFT", "PUBLISHED"] : ["PUBLISHED"];',
    );
    expect(e2eSource).toContain("minMinutes: 0");
    expect(e2eSource).toContain("targetMinutes: timeWindowMinutes");
    expect(e2eSource).toContain("maxMinutes: timeWindowMinutes * 2");
    expect(e2eSource).toContain('level: "REMINDER"');
    expect(e2eSource).toContain('level: "REPORT"');
    expect(e2eSource).toContain('level: "QUALITY_RECORD"');
    expect(e2eSource).toContain("afterMinutes: timeWindowMinutes");
    expect(e2eSource).toContain('await ensureReadySession(page, "platform-admin");');
    expect(e2eSource).toContain('await ensureReadySession(page, "engine-operator");');
    expect(e2eSource).toContain('await ensureReadySession(page, "clinical-user");');
    expect(e2eSource).not.toContain('await ensureReadySession(page, "icu-doctor"');
    expect(e2eSource).not.toContain('await ensureReadySession(page, "emergency-doctor"');
    expect(e2eSource).not.toContain('"icu-doctor"');
    expect(e2eSource).not.toContain('"emergency-doctor"');
    expect(e2eSource).toContain("完整急诊系统");
    expect(e2eSource).toContain("完整 ICU 系统");
    expect(e2eSource).toContain("完整生命支持系统");
    expect(e2eSource).toContain("生命支持设备控制");
    expect(e2eSource).toContain("完整 S19/S24/S27");
    expect(e2eSource).toContain("完整 S0-S40");
    expect(e2eSource).toContain("完整上线验收");
    expect(e2eSource).not.toContain("page.route(");
    expect(e2eSource).not.toContain("page.waitForTimeout");
    expect(e2eSource).not.toContain("third-party-system-family-codes");
    expect(e2eSource).not.toContain("完整急诊系统已上线");
    expect(e2eSource).not.toContain("完整 ICU 系统已上线");
    expect(e2eSource).not.toContain("完整生命支持系统已上线");
    expect(e2eSource).not.toContain("生命支持设备控制已完成");
    expect(e2eSource).not.toContain("完整上线验收已完成");
  });

  it("requires product-role journeys to open every granted menu route with canonical accounts", () => {
    const e2eSource = readFileSync("e2e/product-role-journeys.spec.ts", "utf8");

    expect(e2eSource).toContain("routeMetas");
    expect(e2eSource).toContain("roleMenuReachability");
    expect(e2eSource).toContain("for (const menuKey of expectedMenus[role])");
    expect(e2eSource).toContain("menuReachabilityViewports");
    expect(e2eSource).toContain('name: "desktop-1440"');
    expect(e2eSource).toContain('name: "mobile-390"');
    expect(e2eSource).toContain("openGrantedMenuRoutesForViewport");
    expect(e2eSource).toContain("授权路由直达可达性");
    expect(e2eSource).toContain("真实菜单点击可达性");
    expect(e2eSource).toContain("openGrantedMenuEntryThroughUi");
    expect(e2eSource).toContain("route.placement");
    expect(e2eSource).toContain("打开主菜单");
    expect(e2eSource).toContain("当前用户菜单");
    expect(e2eSource).toContain("role-menu-interaction-codes");
    expect(e2eSource).not.toContain("完整菜单入口均可由 canonical 账号真实打开");
    expect(e2eSource).toContain("routeByMenuKey.get(menuKey)");
    expect(e2eSource).toContain("await page.goto(route.path");
    expect(e2eSource).toContain("main.mk-app-content");
    expect(e2eSource).toContain("role-route-reachability-codes");
    expect(e2eSource).toContain("当前权限不足");
    expect(e2eSource).toContain("serverErrors");
    expect(e2eSource).toContain("browserErrors");
    expect(e2eSource).toContain("networkFailures");
  });

  it("requires four-role core actions to activate report interpretation runtime assets before clinical todo closure", () => {
    const e2eSource = readFileSync("e2e/four-role-core-actions-rehearsal.spec.ts", "utf8");

    expect(e2eSource).toMatch(/ensureDiagnosticCriticalValueRuntime\(\s*page/u);
    expect(e2eSource).toContain("ACTION_CARD.REPORT.CRITICAL_VALUE");
    const runtimePreparationIndex = e2eSource.search(
      /const runtime = await ensureDiagnosticCriticalValueRuntime\(\s*page/u,
    );
    expect(runtimePreparationIndex).toBeGreaterThanOrEqual(0);
    expect(runtimePreparationIndex).toBeLessThan(
      e2eSource.indexOf("createContextSnapshotForReportInterpretation(page)"),
    );
    expect(e2eSource).toContain("snapshot.runtimeReleaseId");
    expect(e2eSource).toContain("interpretation.data?.runtimeReleaseId");
    expect(e2eSource).toContain("报告解读应消费当前机构生效版本知识资产");
    expect(e2eSource).not.toContain("activeAssets: []");
  });

  it("requires report interpretation todo completion readback to target the current source card", () => {
    const e2eSources = [
      readFileSync("e2e/four-role-core-actions-rehearsal.spec.ts", "utf8"),
      readFileSync("e2e/clinical-entry-core-actions-rehearsal.spec.ts", "utf8"),
      readFileSync("e2e/stakeholder-view-rehearsal.spec.ts", "utf8"),
    ];

    for (const source of e2eSources) {
      expect(source).toContain("recommendationCardIds?.[0]");
      expect(source).toContain("报告解读响应必须返回本轮推荐卡来源");
      expect(source).toContain('a[href*="${expectedSourceId}"]');
      expect(source).toContain("waitForCompletedReportTodoReadback(");
      expect(source).toContain('url.searchParams.get("status") === "COMPLETED"');
      expect(source).toContain('url.searchParams.get("sourceType") === "REPORT_INTERPRETATION"');
      expect(source).toContain("expectedSourceId");
      expect(source).toContain(
        "appPath(`/workflow/todos?cardId=${encodeURIComponent(expectedSourceId)}`)",
      );
      expect(source).not.toContain(
        'const completedTodosResponsePromise = waitForGet(page, "/engine/workflow/todos");',
      );
      expect(source).not.toContain("reportTodoSourceId(");
    }
  });

  it("requires clinical entry rehearsal to read hospital runtime with engine operator privileges", () => {
    const e2eSource = readFileSync("e2e/clinical-entry-core-actions-rehearsal.spec.ts", "utf8");
    const functionStart = e2eSource.indexOf("async function currentHospitalRuntimeReleaseId");
    expect(functionStart).toBeGreaterThanOrEqual(0);
    const clinicalProfileIndex = e2eSource.indexOf(
      'const profileResponse = await getApi(page, "/security/me");',
      functionStart,
    );
    const engineOperatorIndex = e2eSource.indexOf(
      'await ensureReadySession(page, "engine-operator");',
      functionStart,
    );
    const runtimeReadIndex = e2eSource.indexOf("/runtime-releases/current", functionStart);

    expect(clinicalProfileIndex).toBeGreaterThan(functionStart);
    expect(engineOperatorIndex).toBeGreaterThan(clinicalProfileIndex);
    expect(engineOperatorIndex).toBeLessThan(runtimeReadIndex);
    expect(e2eSource).toContain(
      'textField(recordField(await responseData(current), "release"), "releaseId")',
    );
    expect(e2eSource).not.toContain('textField(await responseData(current), "release.releaseId")');
    expect(e2eSource).toContain("closeFollowupPlanDrawerIfOpen");
    expect(e2eSource.indexOf("await closeFollowupPlanDrawerIfOpen(page);")).toBeLessThan(
      e2eSource.indexOf('const listResponsePromise = waitForGet(page, "/engine/followup/plans");'),
    );
    expect(e2eSource).toContain('await ensureReadySession(page, "clinical-user");');
    expect(e2eSource).not.toContain("activeAssets: []");
  });

  it("requires entry core action rehearsal to keep representative scope and real service evidence", () => {
    const e2eSource = readFileSync("e2e/entry-core-actions-rehearsal.spec.ts", "utf8");

    expect(e2eSource).toContain("七个路由覆盖六类入口族完成真实前台核心动作代表闭环");
    expect(e2eSource).toContain("entry-core-actions-codes");
    expect(e2eSource).toContain("SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE");
    expect(e2eSource).toContain("七个路由覆盖六类入口族");
    expect(e2eSource).toContain("不代表 34 个入口全部业务动作闭环");
    expect(e2eSource).toContain("不代表完整上线验收");
    expect(e2eSource).toContain("getSystemConfigItem(page, securityConfigKey)");
    expect(e2eSource).toContain("locateSystemConfigRow(page, before.displayName)");
    expect(e2eSource).toContain("arrayData(await responseData(readback))");
    expect(e2eSource).toContain("prepareKnowledgeReviewCandidate(page)");
    expect(e2eSource).toContain("waitForResponseWithQuery");
    expect(e2eSource).toContain("seed.identityCode");
    expect(e2eSource).toContain("entry-core-review=");
    expect(e2eSource).toContain("/engine/knowledge-production/generate");
    expect(e2eSource).toContain('newIdentity: { domain: "GUIDELINE", subject, identityCode }');
    expect(e2eSource).toContain("七入口知识审核候选必须生成审核分类");
    expect(e2eSource).toContain(
      'locator("main").getByRole("button", { name: "search", exact: true })',
    );
    expect(e2eSource).toContain(
      'page.getByRole("dialog").filter({ hasText: "知识候选审核对照" }).last()',
    );
    expect(e2eSource).toContain(
      'const returnReviewButton = page.getByRole("button", { name: /退\\s*修/u }).last()',
    );
    expect(e2eSource).toContain("await returnReviewButton.click()");
    expect(e2eSource).toContain("parseKnowledgeCandidateRef");
    expect(e2eSource).not.toContain("resourceId: versionId");
    expect(e2eSource).toContain("auditEventExistsAsAuditor");
    expect(e2eSource).toContain('ensureReadySession(page, "auditor")');
    expect(e2eSource).toContain('resourceType: "knowledge_candidate_classification"');
    expect(e2eSource).not.toContain('resourceType: "knowledge_asset_version"');
    expect(e2eSource).toContain("platform-knowledge/t-1/literature-materials");
    expect(e2eSource).not.toContain("medkernel://launch-entry-core");
    expect(e2eSource).toContain('getByRole("checkbox", { name: "确认高风险影响" }).check()');
    expect(e2eSource).toContain('chooseDialogOption(page, createDialog, "规则门类", "临床质控")');
    expect(e2eSource).not.toContain(
      'chooseDialogOption(page, createDialog, "规则门类", "质量规则")',
    );
    expect(e2eSource).toContain(
      'chooseDialogOption(page, createDialog, "临床触发场景", "查看患者")',
    );
    expect(e2eSource).not.toContain(
      'chooseDialogOption(page, createDialog, "临床触发场景", "患者概览")',
    );
    expect(e2eSource).toContain('getByRole("tab", { name: /L2 条件树/u }).click()');
    expect(e2eSource).toContain(
      'locator("#rule-condition-fact").fill("observations[].valueNumeric")',
    );
    expect(e2eSource).not.toContain('locator("#rule-condition-fact").fill("observations.0.value")');
    expect(e2eSource).toContain('locator("#rule-condition-value").fill("6")');
    expect(e2eSource).toContain('detailDrawer.getByRole("tab", { name: /发布验证用例/u }).click()');
    expect(e2eSource).toContain(
      'detailDrawer.getByRole("button", { name: "新增验证用例" }).click()',
    );
    expect(e2eSource).toContain("assertContextSnapshotSearchContains(page, snapshot)");
    expect(e2eSource).toContain('const snapshotChoice = caseDialog.getByText("第 1 个临床快照")');
    expect(e2eSource).toContain("await expect(snapshotChoice).toBeVisible");
    expect(e2eSource).toContain("await snapshotChoice.click()");
    expect(e2eSource).toContain('caseDialog.getByText("验证快照已关联")');
    expect(e2eSource).not.toContain('caseDialog.getByText("临床快照已选择")');
    expect(e2eSource).toContain('chooseDialogOption(page, caseDialog, "期望风险等级", "低风险")');
    expect(e2eSource).toContain('chooseDialogOption(page, caseDialog, "期望处置动作", "一般提醒")');
    expect(e2eSource).not.toContain('chooseDialogOption(page, caseDialog, "期望严重程度"');
    expect(e2eSource).not.toContain('chooseDialogOption(page, caseDialog, "期望动作"');
    expect(e2eSource).toContain('const caseId = textField(savedCase, "caseId")');
    expect(e2eSource).toContain('textField(recordField(detail, "definition"), "ruleId")');
    expect(e2eSource).toContain('arrayField(detail, "testCases").some');
    expect(e2eSource).not.toContain('textField(detail, "ruleId") ?? textField(detail, "id")');
    expect(e2eSource).toContain("prepareWorkflowTodoNotification(page)");
    expect(e2eSource).toContain("/engine/recommendations/report-interpretation");
    expect(e2eSource).toContain("/engine/workflow/todos?sourceType=REPORT_INTERPRETATION");
    expect(e2eSource).toContain('textField(item, "sourceType") === "WORKFLOW_TODO"');
    expect(e2eSource).toContain('textField(item, "sourceId") === todoId');
    expect(e2eSource).not.toContain("pageItems(await responseData(unread))[0]");
    expect(e2eSource).not.toContain('data-snapshot-id="${snapshot.snapshotId}"');
    expect(e2eSource).toContain('getByRole("switch", { name: "免打扰偏好" })');
    expect(e2eSource).toContain('getByLabel("免打扰开始时间")');
    expect(e2eSource).toContain('getByLabel("免打扰结束时间")');
    expect(e2eSource).toContain("ensureSandboxEmbedOrigin(page)");
    expect(e2eSource).toContain("new URL(page.url()).origin");
    expect(e2eSource).toContain('postApi(page, "/engine/embed/origins"');
    expect(e2eSource).toContain('getByRole("button", { name: "医生复核并触发 MedKernel" })');
    expect(e2eSource).not.toContain('getByRole("button", { name: "运行真实协同链路" }).first()');
    expect(e2eSource).toContain("prepareProvenanceSeed(page)");
    expect(e2eSource).toContain("/engine/knowledge/citations");
    expect(e2eSource).toContain(
      "/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/publication-quality-records",
    );
    expect(e2eSource).toContain("/engine/knowledge/candidates/${classificationId}/review");
    expect(e2eSource).toContain("assertProvenanceSeedReadback(page, seed)");
    expect(e2eSource).toContain("appPath(`/advanced/provenance?identityId=${seed.identityId}`)");
    expect(e2eSource).toContain("seed.anchorLabel");
    expect(e2eSource).toContain("seed.citationId");
    expect(e2eSource).toContain("seed.textExcerpt");
    expect(e2eSource).toContain('numericField(provenance, "currentVersionId")');
    expect(e2eSource).not.toContain('fill("血钾")');
    expect(e2eSource).not.toContain("identities[0]");
    for (const path of [
      "/security/baseline",
      "/knowledge/governance",
      "/rule/definitions",
      "/notifications",
      "/notifications/settings",
      "/sandbox",
      "/advanced/provenance",
    ]) {
      expect(e2eSource).toContain(`path: "${path}"`);
    }
    expect(e2eSource).toContain("serviceStatus: ");
    expect(e2eSource).toContain("readbackVerified: true");
    expect(e2eSource).toContain("auditVerified");
    expect(e2eSource).toContain('resourceType: "knowledge_identity"');
    expect(e2eSource).toContain("代表核心动作必须能回读真实审计事件");
    expect(e2eSource).not.toContain("auditVerified: true,");
    expect(e2eSource).not.toContain("完整上线验收已完成");
    expect(e2eSource).not.toContain("34 个入口全部业务动作已闭环");
  });

  it("requires compliance and personal entry coverage to aggregate only the two real action evidence specs", () => {
    const fourRoleSource = readFileSync("e2e/four-role-core-actions-rehearsal.spec.ts", "utf8");
    const entrySource = readFileSync("e2e/entry-core-actions-rehearsal.spec.ts", "utf8");
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");
    const fullSystemGate = readFileSync("../scripts/release/full-system-rehearsal-lib.mjs", "utf8");

    expect(fourRoleSource).toContain("four-role-core-actions-codes");
    expect(fourRoleSource).toContain('path: "/admin/audit"');
    expect(fourRoleSource).toContain(
      'serviceOperation: "POST /api/v1/compliance/evidence/snapshots/{evidenceId}/verify"',
    );
    expect(entrySource).toContain("entry-core-actions-codes");
    expect(entrySource).toContain('path: "/security/baseline"');
    expect(entrySource).toContain('serviceOperation: "PATCH /api/v1/system/configs/{key}"');
    expect(entrySource).toContain('path: "/notifications"');
    expect(entrySource).toContain(
      'serviceOperation: "POST /api/v1/engine/notifications/{notificationId}/read"',
    );
    expect(entrySource).toContain('path: "/notifications/settings"');
    expect(entrySource).toContain('serviceOperation: "PUT /api/v1/engine/notifications/settings"');
    expect(entrySource).toContain('path: "/advanced/provenance"');
    expect(entrySource).toContain(
      'serviceOperation: "GET /api/v1/engine/knowledge/identities/{id}/provenance"',
    );
    expect(entrySource).not.toContain(
      'serviceOperation: "PATCH /api/v1/system/config-items/{key}"',
    );
    expect(entrySource).not.toContain(
      'serviceOperation: "POST /api/v1/notifications/{notificationId}/read"',
    );
    expect(entrySource).not.toContain(
      'serviceOperation: "GET /api/v1/provenance/knowledge-identities"',
    );
    expect(coverageParser).toContain(
      "complianceWorkbenchPersonalEntryMatrix:COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS",
    );
    expect(coverageParser).toContain("SECURITY_BASELINE_CONFIG_CHANGE");
    expect(coverageParser).toContain("AUDIT_EVIDENCE_EXPORT_VERIFY");
    expect(coverageParser).toContain("NOTIFICATION_READBACK");
    expect(coverageParser).toContain("NOTIFICATION_SETTINGS_SAVE");
    expect(coverageParser).toContain("SOURCE_LINEAGE_PROVENANCE_READBACK");
    expect(coverageParser).toContain("collectCompleteFourRoleCoreActionsFromTargetSpec");
    expect(coverageParser).toContain("collectCompleteSixEntryCoreActionsFromTargetSpec");
    expect(coverageParser).toContain('"four-role-core-actions-rehearsal.spec.ts"');
    expect(coverageParser).toContain('"entry-core-actions-rehearsal.spec.ts"');
    expect(fullSystemGate).toContain("complianceWorkbenchPersonalEntryMatrix");
    expect(fullSystemGate).toContain("complianceWorkbenchPersonalEntryRows");
  });

  it("requires regional diagnostic mutual-recognition rehearsal to resolve KNOWLEDGE through hospital runtime candidates", () => {
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");
    const fullSystemGate = readFileSync("../scripts/release/full-system-rehearsal-lib.mjs", "utf8");
    const auditGate = readFileSync("../scripts/release/launch-coverage-audit.test.mjs", "utf8");
    const e2eSource = readFileSync(
      "e2e/regional-diagnostic-mutual-recognition-frontdesk.spec.ts",
      "utf8",
    );
    const knowledgeCandidateBody = e2eSource.slice(
      e2eSource.indexOf("async function waitForKnowledgeUnifiedAssetVersion"),
      e2eSource.indexOf("function regionalDiagnosticKnowledgeContent"),
    );

    expect(e2eSource).toContain("regional-diagnostic-mutual-recognition-frontdesk-codes");
    expect(e2eSource).toContain("REGIONAL_REMOTE");
    expect(e2eSource).toContain("/workflow/todos?cardId=");
    expect(e2eSource).toContain('a[href*="cardId=${options.cardId}"]');
    expect(coverageParser).toContain("thirdPartySystemFamilyConsumerSlices:REGIONAL_REMOTE");
    expect(coverageParser).toContain("hasCompleteRegionalRemoteConsumerSlice");
    expect(coverageParser).toContain("regionalRemoteConsumerSlice");
    expect(coverageParser).toContain("requiresRegionalRemoteConsumerSliceAttachment");
    expect(coverageParser).toContain("REGIONAL_DIAGNOSTIC_REPORT_MUTUAL_RECOGNITION");
    expect(coverageParser).toContain("noExternalSuccessClaim");
    expect(coverageParser).toContain("noAutoRecognition");
    expect(coverageParser).toContain("noReportRewrite");
    expect(fullSystemGate).toContain('"REGIONAL_REMOTE"');
    expect(auditGate).toContain("thirdPartySystemFamilyConsumerSlices:REGIONAL_REMOTE");
    expect(auditGate).toContain("missingRegionalConsumer");
    expect(e2eSource).toContain("regionalRemoteConsumerSlice");
    expect(e2eSource).toContain("REGIONAL_DIAGNOSTIC_REPORT_MUTUAL_RECOGNITION");
    expect(e2eSource).toContain("noExternalSuccessClaim");
    expect(e2eSource).toContain("noAutoRecognition");
    expect(e2eSource).toContain("noReportRewrite");
    expect(e2eSource).toContain("不代表真实外部区域平台成功联通");
    expect(e2eSource).toContain("不代表自动互认");
    expect(e2eSource).toContain('"KNOWLEDGE"');
    expect(e2eSource).toContain('"FIELD_CATALOG"');
    expect(e2eSource).toContain('"ACTION_CARD"');
    expect(e2eSource).toContain("DiagnosticReport");
    expect(e2eSource).toContain("createRegionalDiagnosticKnowledgeAsset(page, suffix, hospitalId)");
    expect(e2eSource).toContain("/engine/knowledge-production/generate");
    expect(e2eSource).toContain('targetPipeline: "TENANT_OVERLAY"');
    expect(e2eSource).toContain('domain: "CLINICAL"');
    expect(e2eSource).toContain(
      'newIdentity: { domain: "DIAGNOSTIC_ITEM", subject, identityCode }',
    );
    expect(e2eSource).toContain("parseKnowledgeCandidateRef");
    expect(e2eSource).toContain("/engine/knowledge/citations");
    expect(e2eSource).toContain(
      "/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/publication-quality-records",
    );
    expect(e2eSource).toContain("/engine/knowledge/candidates/${classificationId}/review");
    expect(e2eSource).toContain("qualityGateRecordId");
    expect(e2eSource).not.toContain("activateRegionalDiagnosticKnowledgeVersion");
    expect(e2eSource).not.toContain("/versions/${options.versionId}/activate");
    expect(knowledgeCandidateBody).toContain("/engine/releases/hospitals/${encodeURIComponent(");
    expect(knowledgeCandidateBody).toContain("options.hospitalId");
    expect(knowledgeCandidateBody).toContain("runtime-candidates?assetType=KNOWLEDGE");
    expect(knowledgeCandidateBody).toContain('textField(candidate, "status") === "PUBLISHED"');
    expect(knowledgeCandidateBody).toContain(
      'textField(candidate, "contentHash") === options.contentHash',
    );
    expect(knowledgeCandidateBody).toContain('versionId.startsWith("av-")');
    expect(knowledgeCandidateBody).not.toContain(
      "/engine/authoring/declarative-assets?assetType=KNOWLEDGE",
    );
    expect(e2eSource).not.toContain("page.route(");
    expect(e2eSource).not.toContain("page.waitForTimeout");
    expect(e2eSource).not.toContain("third-party-system-family-codes");
    expect(e2eSource).not.toContain('"quality-controller"');
    expect(e2eSource).not.toContain("完整区域平台已上线");
    expect(e2eSource).not.toContain("完整 S40 已上线");
    expect(e2eSource).not.toContain("完整上线验收已完成");
  });

  it("requires CLAIM runtime activation to select the local evaluation candidate and assert the request payload", () => {
    const source = readFileSync("e2e/real-frontdesk-rehearsal.spec.ts", "utf8");

    expect(source).toContain("requiredRuntimeAssetsForRehearsal");
    expect(source).toContain("selectRequiredPlatformRuntimeAssetsForClaimActivation");
    expect(source).toContain("selectHospitalLocalClaimIndicatorCandidate");
    expect(source).toContain("assertRuntimeReleaseRequestCarriesRequiredBaselineAssets");
    expect(source).toContain("assertRuntimeReleaseRequestContainsClaimIndicator");
    expect(source).toContain("assertCurrentRuntimeContainsRequiredBaselineAssets");
    expect(source).toContain("平台标准内容");
    expect(source).toContain("集团与本院内容");
    expect(source).toContain("本院 · 评价指标内容");
    expect(source).toContain("postDataJSON");
    expect(source).not.toContain(
      'page.getByRole("checkbox", { name: /启用.*评价指标内容/u }).first()',
    );
  });

  it("requires identity binding frontdesk rehearsal to prove bind, unbind and plaintext-safety", () => {
    const source = readFileSync("e2e/identity-binding-frontdesk.spec.ts", "utf8");

    expect(source).toContain("bindIdentitySourceFromUi");
    expect(source).toContain("unbindIdentitySourceFromUi");
    expect(source).toContain("assertIdentityPlaintextIsNotPersisted");
    expect(source).toContain("cleanupIdentityBindingRehearsal");
    expect(source).toContain("attachIdentityBindingScenarioEvidence");
    expect(source).toContain("identity-binding-scenario-codes");
    expect(source).toContain("IdentityBindingScenarioEvidence");
    expect(source).toContain("Promise.allSettled");
    expect(source).toContain(":unbind");
    expect(source).toContain("/compliance/identity-bindings");
    expect(source).toContain('postApi(page, "/compliance/identity-bindings"');
    expect(source).toContain("externalSubjectDigest");
    expect(source).toContain("listOmitsExternalSubjectDigest");
    expect(source).toContain("listOmitsExternalSubjectPlaintext");
    expect(source).toContain("duplicateStatus");
    expect(source).toContain("cleanupCompleted");
    expect(source).toContain("bindingUnboundOrAlreadyUnbound");
    expect(source).toContain("前台绑定院内身份来源");
    expect(source).toContain("列表回读只展示脱敏身份提示");
    expect(source).toContain("后端拒绝重复外部身份绑定");
    expect(source).toContain("前台解绑身份来源并保留历史证据");
    expect(source).toContain("停用身份来源演练账号");
    expect(source).toContain("expectedVersion");
    expect(source).toContain("UNBOUND");
    expect(source).not.toContain("page.request.post(`${apiBase}/compliance/identity-bindings`");
  });

  it("requires system providers frontdesk rehearsal to prove readonly operations readiness", () => {
    const source = readFileSync("e2e/system-providers-frontdesk.spec.ts", "utf8");

    expect(source).toContain("/system/providers");
    expect(source).toContain("/system/operations");
    expect(source).toContain("assertRuntimeOperationsSnapshot");
    expect(source).toContain("assertBackupReadinessCard");
    expect(source).toContain("assertEvidenceDetailsDiagnostics");
    expect(source).toContain("assertClinicalUserCannotReadOperations");
    expect(source).toContain("attachSystemProvidersCoverageEvidence");
    expect(source).toContain("system-providers-operations-codes");
    expect(source).toContain("operationsSnapshotRead");
    expect(source).toContain("backupReadinessObserved");
    expect(source).toContain("honestDegradationObserved");
    expect(source).toContain("evidenceDetailsObserved");
    expect(source).toContain("backupDrillSucceeded");
    expect(source).toContain("readRuntimeContinuityAfterRestore");
    expect(source).toContain(
      "ensureRehearsalRuntimeAssetApiSession(page);\n  const hospitalResponse",
    );
    expect(source).toContain("createClinicalSmokeAfterRestore");
    expect(source).toContain("runtimeReadbackObserved");
    expect(source).toContain("runtimeConsumerReadbackObserved");
    expect(source).toContain("clinicalSmokeAfterRestore");
    expect(source).toContain("备份恢复隔离演练未完成，服务运行保障诚实展示待演练状态");
    expect(source).toContain("恢复后后端当前机构生效版本与第三方运行契约读回一致");
    expect(source).toContain("临床账号恢复后完成患者主索引和上下文主链路冒烟");
    expect(source).toContain("clinicalForbidden");
    expect(source).toContain("临床账号无法读取或展示服务运行保障快照");
    expect(source).toContain("备份恢复");
    expect(source).toContain("证据详情");
    expect(source).toContain("当前权限不足");
    expect(source).toContain("backup-restore-drill.sh");
    expect(source).toContain("backup.sh");
    expect(source).toContain("restore.sh");
    expect(source).toContain("checksumEvidence");
    expect(source).toContain("drillDatabaseIsIsolated");
    expect(source).toContain("证据 RPO");
    expect(source).toContain("证据 RTO");
    expect(source).toContain("/engine/integration/knowledge-runtime/runtime-release/current");
    expect(source).toContain("/engine/releases/hospitals/");
    expect(source).toContain("/mpi");
    expect(source).toContain("新增患者");
    expect(source).toContain("建立当前就诊上下文");
    expect(source).not.toContain("postApi(");
    expect(source).not.toContain("exec");
    expect(source).not.toContain("spawn");
  });

  it("requires platform-admin P0 entry evidence to keep representative service operations", () => {
    const serviceOrganization = readFileSync("e2e/service-organization-frontdesk.spec.ts", "utf8");
    const identityBinding = readFileSync("e2e/identity-binding-frontdesk.spec.ts", "utf8");
    const thirdPartyFamilies = readFileSync(
      "e2e/third-party-system-families-rehearsal.spec.ts",
      "utf8",
    );
    const systemProviders = readFileSync("e2e/system-providers-frontdesk.spec.ts", "utf8");
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");

    expect(serviceOrganization).toContain('serviceOperation: "POST /api/v1/admin/tenants"');
    expect(identityBinding).toContain(
      'serviceOperation: "POST /api/v1/compliance/identity-bindings"',
    );
    expect(thirdPartyFamilies).toContain(
      'serviceOperation: "POST /api/v1/engine/integration/data-quality/reports"',
    );
    expect(systemProviders).toContain('serviceOperation: "GET /api/v1/system/operations"');
    expect(coverageParser).toContain('"tenant-onboarding": ["POST /api/v1/admin/tenants"]');
    expect(coverageParser).toContain(
      '"identity-bindings": ["POST /api/v1/compliance/identity-bindings"]',
    );
    expect(coverageParser).toContain(
      '"adapter-hub": ["POST /api/v1/engine/integration/data-quality/reports"]',
    );
    expect(coverageParser).toContain('"system-providers": ["GET /api/v1/system/operations"]');
  });

  it("requires product role journey rehearsal to attach structured dashboard workbench evidence", () => {
    const source = readFileSync("e2e/product-role-journeys.spec.ts", "utf8");
    const coverageParser = readFileSync("e2e/support/launchCoverageEvidence.ts", "utf8");

    expect(source).toContain("DashboardWorkbenchRoleActionEvidence");
    expect(source).toContain("dashboard-workbench-core-actions-codes-");
    expect(source).toContain("DASHBOARD_WORKBENCH_CORE_ACTIONS");
    expect(source).toContain("四职责工作台核心动作代表矩阵");
    expect(source).toContain("不代表 34 个入口全部业务动作闭环");
    expect(source).toContain("assertDashboardSourceServices");
    expect(source).toContain("GET /api/v1/security/me");
    expect(source).toContain("GET /api/v1/system/operations");
    expect(source).toContain("GET /api/v1/compliance/audit/events");
    expect(source).toContain("GET /api/v1/large-lists/audit-events/list");
    expect(source).toContain("GET /api/v1/engine/tenant/success-plan");
    expect(source).toContain("primaryActionVerified");
    expect(source).toContain("highFrequencyTasksVerified");
    expect(source).toContain("sourceStatusVerified");
    expect(source).toContain("permissionBoundaryEvidence");
    expect(source).toContain("sixStateEvidence");
    expect(source).toContain("roleScopeReadbackVerified");
    expect(source).toContain("noBrowserErrors");
    expect(source).toContain("noServerErrors");
    expect(source).toContain("noNetworkFailures");
    expect(coverageParser).toContain('"GET /api/v1/compliance/audit/events"');
    expect(coverageParser).toContain("dashboardWorkbenchCoreActions");
    expect(coverageParser).toContain("dashboardWorkbenchCoreActionRows");
    expect(coverageParser).toContain("roleScopeFrontdeskActionRepresentativeSlice");
    expect(coverageParser).toContain("FOUR_ROLE_SCOPE_FRONTDESK_ACTION_REPRESENTATIVE");
    expect(coverageParser).toContain("hasRequiredFourRoleCoreActionsFromTargetSpec");
    expect(coverageParser).not.toContain("organizationLevels:PLATFORM");
    expect(coverageParser).not.toContain("organizationLevels:GROUP");
  });

  it("requires embedded business host rehearsal to use real embed services before launch coverage", () => {
    const source = readFileSync("e2e/embed-business-host.spec.ts", "utf8");
    const hostSource = readFileSync("e2e/support/embed-business-host-server.mjs", "utf8");
    const clinicalContextBody = source.slice(
      source.indexOf("async function createClinicalContextFromFrontdesk"),
      source.indexOf("async function createEmbeddedRecommendationCard"),
    );

    expect(source).toContain("/engine/embed/origins");
    expect(source).toContain("/engine/embed/launch-tokens");
    expect(source).toContain("/engine/recommendations:evaluate");
    expect(source).toContain("findEmbeddedRecommendationCard(payload.data?.cards ?? [])");
    expect(source).toContain('card.title === "检验危急值需人工确认"');
    expect(source).toContain('card.sourceSummary?.includes("嵌入宿主真实服务链路演练")');
    expect(source).not.toContain("payload.data?.cards?.[0]");
    const embedLaunchSource = readFileSync("src/pages/clinical/EmbedLaunch.tsx", "utf8");
    expect(embedLaunchSource).toContain("App as AntdApp");
    expect(embedLaunchSource).toContain("AntdApp.useApp()");
    expect(embedLaunchSource).not.toContain("  message,");
    expect(clinicalContextBody).toContain('getByLabel("医技报告项目").fill("血钾检验")');
    expect(clinicalContextBody).toContain(
      'getByLabel("报告结论").fill("血钾 6.3 mmol/L，危急值，已复核")',
    );
    expect(clinicalContextBody.indexOf('getByLabel("医技报告项目")')).toBeLessThan(
      clinicalContextBody.indexOf('getByLabel("异常重点")'),
    );
    expect(clinicalContextBody.indexOf('getByLabel("报告结论")')).toBeLessThan(
      clinicalContextBody.indexOf('getByLabel("异常重点")'),
    );
    expect(source).toContain("const targetRecommendationCard = embeddedRecommendationCard(");
    expect(source).toContain('"检验危急值需人工确认"');
    expect(source).toContain('targetRecommendationCard.getByRole("button", { name: /采纳建议/ })');
    expect(source).not.toContain('frame.getByRole("button", { name: /采纳建议/ }).click()');
    expect(source).toContain("真实签发一次性嵌入启动凭证");
    expect(source).toContain("独立业务系统宿主加载真实 iframe 启动地址");
    expect(source).toContain("嵌入终端真实兑换启动凭证并读取当前就诊上下文");
    expect(source).toContain("嵌入终端真实读取当前就诊推荐卡");
    expect(source).toContain("医师在嵌入终端提交采纳反馈");
    expect(source).toContain("独立业务系统宿主收到医师反馈 postMessage");
    expect(source).toContain("attachEmbedBusinessHostScenarioEvidence");
    expect(source).toContain("embed-business-host-launch-codes");
    expect(source).not.toContain("page.route(");
    expect(source).not.toContain("card-embed-e2e");
    expect(hostSource).toContain('searchParams.get("token")');
    expect(hostSource).toContain("token=");
    expect(hostSource).not.toContain("host-e2e-token");
  });

  it("requires pathway lifecycle rehearsal to prove real S6 frontdesk and service operations before launch coverage", () => {
    const source = readFileSync("e2e/pathway-lifecycle-frontdesk.spec.ts", "utf8");

    expect(source).toContain("recordPathwayLifecycleStage");
    expect(source).toContain("attachPathwayLifecycleScenarioEvidence");
    expect(source).toContain("pathway-lifecycle-scenario-codes");
    expect(source).toContain('code: "S6"');
    expect(source).toContain("CLINICAL_EXECUTION");
    expect(source).toContain("SPECIAL_DISEASE_PATHWAY");
    expect(source).toContain("ORDER_SET.S6.COPD");
    expect(source).toContain("createOrderSetAssetFromFrontdesk");
    expect(source).toContain("assertRuntimeContainsOrderSetAsset");
    expect(source).toContain("assertOrderSetRuntimeConsumerEvidence");
    expect(source).toContain("orderSetRuntimeConsumer");
    expect(source).toContain("orderSetRuntimeConsumed");
    expect(source).toContain("pathway.orderSetRef");
    expect(source).toContain("pathway.orderSetVersion");
    expect(source).toContain("pathway.orderSetHash");
    expect(source).toContain("pathway.orderSetItems");
    expect(source).toContain("pathway.orderSetRequiresPhysicianConfirmation");
    expect(source).toContain('"ORDER_SET"');
    expect(source).toContain("SCREENING_TRIAGE");
    expect(source).toContain("QUALITY_ITERATION");
    expect(source).toContain("/engine/authoring/declarative-assets");
    expect(source).toContain(
      "/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases",
    );
    expect(source).toContain("/engine/pathway/pathway-templates");
    expect(source).toContain("/engine/authoring/preview-run");
    expect(source).toContain(
      "/engine/pathway/pathway-templates/${encodeURIComponent(templateId)}/simulate",
    );
    expect(source).toContain("/engine/pathway/patient-pathways/entry-candidates");
    expect(source).toContain("/engine/pathway/patient-pathways/enter");
    expect(source).toContain(
      "/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/advance",
    );
    expect(source).toContain(
      "/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/clocks",
    );
    expect(source).toContain(
      "/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/variances",
    );
    expect(source).toContain("/engine/followup/plans");
    expect(source).toContain("前台创建专病路径草稿并保存节点边时钟");
    expect(source).toContain("后端回读路径节点边时钟与十阶段里程碑");
    expect(source).toContain("前台使用真实 ACTIVE 快照完成草稿试运行");
    expect(source).toContain("真实服务链路对已保存路径执行仿真");
    expect(source).toContain("临床用户基于当前机构生效版本读取入径候选");
    expect(source).toContain("临床用户办理患者入径并生成首个关键时钟");
    expect(source).toContain("临床用户完成当前节点并标准推进");
    expect(source).toContain("临床用户推进到医嘱套餐节点并消费当前机构生效版本 ORDER_SET");
    expect(source).toContain("真实后端登记路径变异与处置决策");
    expect(source).toContain("真实后端完成随访接续终点节点");
    expect(source).toContain("后端回读关键时钟和变异事实");
    expect(source).toContain("路径完成后生成随访接续证据");
    expect(source).toContain("formatClinicalDateTimeForE2e");
    expect(source).toContain("建立的临床快照");
    expect(source).not.toContain("page.route(");
    expect(source).not.toContain("route.fulfill");
    expect(source).not.toContain("pathway-graph-editor.spec.ts");
    expect(source).not.toContain("选择第 1 个临床快照");
  });
});

function account(role: string, tenantId: string, prefix: string) {
  return {
    tenantId,
    username: `${prefix}-${role}`,
    role,
    password: `secret-${prefix}-${role}`,
  };
}

function runtimeAssetVersionId(assetIdentity: string) {
  return `${assetIdentity.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-v1`;
}

function restoreEnv(name: string, value: string | undefined) {
  if (value === undefined) {
    delete process.env[name];
  } else {
    process.env[name] = value;
  }
}
