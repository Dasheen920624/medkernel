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
      entryState: string;
    }> = [
      ...runtimeAssetTypes.map((assetType) => ({
        assetType,
        assetIdentity: runtimeAssetIdentities[assetType],
        versionId: `${assetType.toLowerCase()}-v1`,
        entryState: "ACTIVE",
      })),
      {
        assetType: "RULE",
        assetIdentity: "RULE.DISABLED",
        versionId: "rule-disabled",
        entryState: "DISABLED",
      },
    ];
    const baseline = auth.resolveBaselineRuntimeAssets({
      release: { baselineReleaseId: "baseline-1" },
      items: baselineItems,
    });

    expect(baseline).toEqual({
      baselineReleaseId: "baseline-1",
      activeAssets: runtimeAssetTypes.map((assetType) => ({
        assetType,
        assetIdentity: runtimeAssetIdentities[assetType],
        versionId: null,
      })),
    });
    expect(auth.runtimeAssetsCoverRequiredTypes(baseline.activeAssets)).toBe(true);
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
    ).toEqual({ releaseId: "hospital-release-empty", ready: false });

    expect(
      auth.hospitalRuntimeCoversRequiredAssets({
        release: { releaseId: "hospital-release-ready" },
        items: runtimeAssetTypes.map((assetType) => ({
          assetType,
          assetIdentity: runtimeAssetIdentities[assetType],
          versionId: `${assetType.toLowerCase()}-v1`,
          entryState: "ACTIVE",
        })),
      }),
    ).toEqual({ releaseId: "hospital-release-ready", ready: true });
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
    ).toEqual({ releaseId: "hospital-release-no-knowledge", ready: false });
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
    ).toEqual({ releaseId: "hospital-release-historical-assets", ready: false });
  });

  it("selects every required platform runtime candidate once the rehearsal bootstrap fills gaps", async () => {
    process.env.E2E_API_BASE_URL = "http://localhost:18080/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const candidates = [
      ...runtimeAssetTypes.map((assetType) => ({
        assetType,
        assetIdentity: runtimeAssetIdentities[assetType],
        versionId: `${assetType.toLowerCase()}-v1`,
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
      runtimeAssetTypes.map((assetType) => `${assetType.toLowerCase()}-v1`),
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
    expect(source).toContain('writeApi(page, "put", path, data)');
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
    expect(source).toContain("assertRequiredRuntimeInputsVisibleAndSelected");
    expect(source).toContain("assertThirdPartyRuntimeConsumerCarriesRequiredAssets");
    expect(source).toContain("/engine/integration/knowledge-runtime/runtime-release/current");
    expect(source).toContain('getByRole("checkbox", { name: /启用/ })');
    expect(source).toContain("平台标准内容");
    expect(source).toContain("assetIdentity");
    expect(source).toContain("postDataJSON");
    expect(source).not.toContain('runtimeHasActiveAsset(current.data, "FIELD_CATALOG")');
    expect(source).not.toContain('runtimeHasActiveAsset(current.data, "RULE")');
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
  });

  it("requires service organization rehearsal to cover first-login organization tree setup", () => {
    const source = readFileSync("e2e/service-organization-frontdesk.spec.ts", "utf8");

    expect(source).toContain("provisionServiceOrganizationFromUi");
    expect(source).toContain("completeTenantAdminFirstLoginFromUi");
    expect(source).toContain("createFacilityAndDepartmentFromUi");
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
    expect(source).not.toContain(".catch(() => null)");
  });

  it("requires insurance frontdesk rehearsal to be driven by an active CLAIM evaluation indicator", () => {
    const source = readFileSync("e2e/real-frontdesk-rehearsal.spec.ts", "utf8");

    expect(source).toContain("createActiveClaimEvaluationIndicatorFromUi");
    expect(source).toContain('subjectType: "CLAIM"');
    expect(source).toContain("runInsuranceAuditFromUi(");
    expect(source).toContain("claimIndicator");
    expect(source).toContain("assertInsuranceAuditUsesEvaluationRun");
    expect(source).toContain("evaluationRunId");
    expect(source).toContain("INSURANCE_RULE_MANUAL");
  });

  it("requires identity binding frontdesk rehearsal to prove bind, unbind and plaintext-safety", () => {
    const source = readFileSync("e2e/identity-binding-frontdesk.spec.ts", "utf8");

    expect(source).toContain("bindIdentitySourceFromUi");
    expect(source).toContain("unbindIdentitySourceFromUi");
    expect(source).toContain("assertIdentityPlaintextIsNotPersisted");
    expect(source).toContain("cleanupIdentityBindingRehearsal");
    expect(source).toContain("Promise.allSettled");
    expect(source).toContain(":unbind");
    expect(source).toContain("/compliance/identity-bindings");
    expect(source).toContain('postApi(page, "/compliance/identity-bindings"');
    expect(source).toContain("externalSubjectDigest");
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
    expect(source).not.toContain("postApi(");
    expect(source).not.toContain("exec");
    expect(source).not.toContain("spawn");
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

function restoreEnv(name: string, value: string | undefined) {
  if (value === undefined) {
    delete process.env[name];
  } else {
    process.env[name] = value;
  }
}
