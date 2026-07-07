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
        items: runtimeAssetTypes.map((assetType) => ({
          assetType,
          assetIdentity: runtimeAssetIdentities[assetType],
          versionId: `${assetType.toLowerCase()}-v1`,
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

  it("keeps third-party family evidence API readback on the real backend API base", () => {
    const source = readFileSync("e2e/third-party-system-families-rehearsal.spec.ts", "utf8");

    expect(source).toContain('import { apiBase, ensureReadySession } from "./support/auth"');
    expect(source).toContain("page.request.get(`${apiBase}/engine/integration/onboardings`");
    expect(source).not.toContain('page.request.get("/api/v1/engine/integration/onboardings"');
  });

  it("requires S2/S4 rehearsal to prove frontdesk mapping and real inbound runtime consumption", () => {
    const source = readFileSync(
      "e2e/s2-s4-terminology-integration-rehearsal.spec.ts",
      "utf8",
    );

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
    expect(source).toContain("chooseFieldMappingCategory(page, dialog, 1, \"检验\")");
    expect(source).toContain("targetDictionaryKey: options.standardSystem");
    expect(source).toContain('category: "LAB"');
    expect(source).not.toContain("page.waitForTimeout");
  });

  it("requires S2/S4 signature preview to choose and submit the current webhook channel", () => {
    const source = readFileSync(
      "e2e/s2-s4-terminology-integration-rehearsal.spec.ts",
      "utf8",
    );

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
    const source = readFileSync(
      "e2e/s2-s4-terminology-integration-rehearsal.spec.ts",
      "utf8",
    );

    expect(source).toContain("readLatestMasterDataCursor(page, sourceSystem)");
    expect(source).toContain("previousCursor,");
    expect(source).toContain("/engine/integration/master-data/reconciliation?sourceSystem=");
    expect(source).toContain("签名必须基于读取到的最新服务端游标");
    expect(source).not.toContain("previousCursor: null");
  });

  it("requires S2/S4 terminology candidates to use deterministic alias evidence", () => {
    const source = readFileSync(
      "e2e/s2-s4-terminology-integration-rehearsal.spec.ts",
      "utf8",
    );

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
    expect(source).toContain("recordServiceOrganizationStage");
    expect(source).toContain("attachServiceOrganizationScenarioEvidence");
    expect(source).toContain("service-organization-scenario-codes");
    expect(source).toContain('code: "S1"');
    expect(source).toContain('code: "S14"');
    expect(source).toContain("前台开通服务机构");
    expect(source).toContain("机构管理员首次登录并改密");
    expect(source).toContain("前台创建医疗机构与科室");
    expect(source).toContain("前台回读服务机构组织树");
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
    expect(source).toContain("/engine/rule/rules/${encodeURIComponent(ruleId)}/governance/transitions");
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
    expect(source).toContain("versionId.startsWith(\"av-\")");
    expect(source).toContain("/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases");
    expect(source).toContain("/cdss/fatigue");
    expect(source).toContain("getByRole(\"button\", { name: \"登记触发评估\" })");
    expect(source).toContain("name: `选择 ${snapshot.snapshotId}`");
    expect(source).toContain("/engine/recommendations:evaluate");
    expect(source).toContain("readRecommendationTriggerDiagnose");
    expect(source).toContain("/engine/recommendations/triggers/${encodeURIComponent(triggerId)}/diagnose");
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
    expect(source).not.toContain("textField(evaluation, \"cards[0].cardId\")");
    expect(source).toContain("cdss-runtime-declarative-assets-codes");
    expect(source).toContain("attachCdssRuntimeDeclarativeAssetEvidence");
    expect(source).toContain("推荐卡解释证明三类资产按当前机构生效版本物化消费");
    expect(source).not.toContain("page.route(");
    expect(source).not.toContain("选择第 1 个临床快照");
    expect(source.indexOf("declarativeRuntime = await activateRuntimeWithDeclarativeAssets")).toBeLessThan(
      source.indexOf("ruleTestSnapshot = await createClinicalContextFromFrontdesk"),
    );
    expect(source.indexOf("ruleTestSnapshot = await createClinicalContextFromFrontdesk")).toBeLessThan(
      source.indexOf("createAndPublishRuleReferencingDeclarativeAssets"),
    );
    expect(source.indexOf("readHospitalRuntimeCandidate")).toBeLessThan(
      source.indexOf("finalRuntime = await activateRuntimeWithDeclarativeAssets"),
    );
    expect(source.indexOf("finalRuntime = await activateRuntimeWithDeclarativeAssets")).toBeLessThan(
      source.indexOf("snapshot = await createClinicalContextFromFrontdesk"),
    );
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
    expect(source).not.toContain("postApi(");
    expect(source).not.toContain("exec");
    expect(source).not.toContain("spawn");
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
    expect(source).toContain("/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases");
    expect(source).toContain("/engine/pathway/pathway-templates");
    expect(source).toContain("/engine/authoring/preview-run");
    expect(source).toContain("/engine/pathway/pathway-templates/${encodeURIComponent(templateId)}/simulate");
    expect(source).toContain("/engine/pathway/patient-pathways/entry-candidates");
    expect(source).toContain("/engine/pathway/patient-pathways/enter");
    expect(source).toContain("/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/advance");
    expect(source).toContain("/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/clocks");
    expect(source).toContain("/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/variances");
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

function restoreEnv(name: string, value: string | undefined) {
  if (value === undefined) {
    delete process.env[name];
  } else {
    process.env[name] = value;
  }
}
