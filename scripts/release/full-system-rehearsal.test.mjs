import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  assertCompleteLaunchCoverage,
  buildFullSystemStagePlan,
  buildRequiredLaunchAcceptance,
  buildRequiredLaunchCoverage,
  buildTargetEnvironmentRehearsalEvidence,
  readFullSystemRehearsalConfig,
  runFullSystemRehearsal,
  validateStageEvidence,
} from "./full-system-rehearsal-lib.mjs";

const MANIFEST_PATH = fileURLToPath(
  new URL(
    "../knowledge/manifests/full-knowledge-rehearsal-1.0.0.json",
    import.meta.url,
  ),
);
const REPO_ROOT = fileURLToPath(new URL("../..", import.meta.url));

test("整套演练固定覆盖迁移、四职责、Provider、平台基线、沙盘、11 域知识、运行韧性、全量浏览器旅程和完整范围审计", () => {
  const config = rehearsalConfig();
  const plan = buildFullSystemStagePlan(config);

  assert.deepEqual(
    plan.map((stage) => stage.id),
    [
      "database-migrations",
      "account-bootstrap",
      "model-provider",
      "full-knowledge",
      "platform-baseline",
      "sandbox",
      "runtime-resilience",
      "target-environment",
      "browser-e2e",
      "launch-coverage",
    ],
  );
  assert.equal(
    plan[0].env.LAUNCH_DATABASE_MIGRATION_EVIDENCE_PATH.endsWith(
      "/database-migrations.json",
    ),
    true,
  );
  assert.equal(plan[1].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[2].env.FULL_KNOWLEDGE_MANIFEST_PATH, MANIFEST_PATH);
  assert.equal(plan[3].env.FULL_KNOWLEDGE_PROVIDER_CODE, "ollama-launch");
  assert.equal(plan[4].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[4].env.FULL_KNOWLEDGE_MANIFEST_PATH, MANIFEST_PATH);
  assert.equal(
    plan[4].env.LAUNCH_PLATFORM_BASELINE_EVIDENCE_PATH.endsWith(
      "/platform-baseline.json",
    ),
    true,
  );
  assert.equal(plan[4].label, "平台字段目录与全知识权威基线");
  assert.equal(plan[5].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[5].label, "演练机构十规则四十用例与机构生效版本");
  assert.equal(plan[6].env.RUNTIME_RESILIENCE_PROVIDER_CODE, "ollama-launch");
  assert.equal(plan[6].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[7].id, "target-environment");
  assert.equal(
    plan[7].env.LAUNCH_TARGET_ENVIRONMENT_EVIDENCE_PATH.endsWith(
      "/target-environment.json",
    ),
    true,
  );
  assert.equal(plan[7].env.LAUNCH_WEB_BASE_URL, config.webBaseUrl);
  assert.equal(plan[7].env.LAUNCH_API_BASE_URL, config.apiBaseUrl);
  assert.equal(plan[7].env.LAUNCH_SOURCE, config.source);
  assert.equal(plan[8].cwd.endsWith("/frontend"), true);
  assert.equal(plan[8].env.E2E_EXTERNAL_DEPLOYMENT, "1");
  assert.equal(plan[8].env.E2E_EXPECT_MFA_DISABLED, "1");
  assert.equal(plan[8].env.E2E_IGNORE_HTTPS_ERRORS, undefined);
  assert.equal(
    plan[9].env.LAUNCH_COVERAGE_EVIDENCE_PATH.endsWith("/launch-coverage.json"),
    true,
  );
  assert.equal(plan[9].env.FULL_SYSTEM_EVIDENCE_ROOT, config.evidenceRoot);

  const requiredCoverage = buildRequiredLaunchCoverage();
  assert.equal(requiredCoverage.scenarios[0].status, "UNKNOWN");
  assert.equal(requiredCoverage.scenarios[0].evidenceStage, null);
  assert.deepEqual(
    requiredCoverage.databaseDialects.map((item) => item.code),
    ["POSTGRES", "KINGBASE", "ORACLE", "DM", "H2"],
  );
  assert.equal(
    requiredCoverage.deliveryShapes.some(
      (item) => item.code === "MANAGEMENT_WORKSPACE",
    ),
    true,
  );
  assert.equal(
    requiredCoverage.deliveryShapes.some(
      (item) => item.code === "MANAGEMENT_CONSOLE",
    ),
    false,
  );
  assert.equal(requiredCoverage.menuEntryCoreActionRows.length, 34);
  assert.deepEqual(
    requiredCoverage.thirdPartySystemFamilyDegradationRows.map(
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
  assert.deepEqual(
    requiredCoverage.menuEntryCoreActionRows.map((item) => item.code),
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
  const requiredAcceptance = buildRequiredLaunchAcceptance();
  assert.equal(requiredAcceptance.length, 15);
  assert.deepEqual(
    requiredAcceptance.find((item) => item.code === "LAUNCH-11")
      .requiredCoverage,
    ["databaseMigrationSource", "databaseDialects"],
  );
  assert.deepEqual(
    requiredAcceptance.find((item) => item.code === "LAUNCH-15")
      .requiredCoverage,
    ["targetEnvironmentRehearsal"],
  );
  assert.equal(
    requiredAcceptance
      .find((item) => item.code === "LAUNCH-12")
      .requiredCoverage.includes("thirdPartySystemFamilyDegradationRows"),
    true,
  );
  assert.equal(
    requiredAcceptance
      .find((item) => item.code === "LAUNCH-09")
      .requiredCoverage.includes("menuEntryCoreActionRows"),
    true,
  );
});

test("整套演练配置拒绝跳过 TLS 校验并把全部证据固定在仓库外", () => {
  const env = baseEnv();
  env.E2E_IGNORE_HTTPS_ERRORS = "1";
  assert.throws(
    () =>
      readFullSystemRehearsalConfig(env, { repoRoot: "/workspace/medkernel" }),
    /禁止忽略 HTTPS 证书错误/u,
  );

  delete env.E2E_IGNORE_HTTPS_ERRORS;
  const config = readFullSystemRehearsalConfig(env, {
    repoRoot: "/workspace/medkernel",
  });
  assert.equal(
    config.evidenceRoot,
    "/var/lib/medkernel/evidence/current-launch",
  );
  assert.equal(
    config.indexPath,
    "/var/lib/medkernel/evidence/current-launch/full-system.json",
  );
  assert.equal(config.source, "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17");

  delete env.LAUNCH_SOURCE;
  assert.throws(
    () =>
      readFullSystemRehearsalConfig(env, { repoRoot: "/workspace/medkernel" }),
    /LAUNCH_SOURCE/u,
  );
  env.LAUNCH_SOURCE = "1603b5a7";
  assert.throws(
    () =>
      readFullSystemRehearsalConfig(env, { repoRoot: "/workspace/medkernel" }),
    /40 位提交哈希/u,
  );
});

test("任一阶段退出失败立即阻断整场且不执行后续阶段", async () => {
  const executed = [];
  await assert.rejects(
    () =>
      runFullSystemRehearsal(rehearsalConfig(), {
        runCommand: async (stage) => {
          executed.push(stage.id);
          return { exitCode: stage.id === "sandbox" ? 9 : 0 };
        },
        readJson: (_path, stage) => completeStageEvidence()[stage.id],
      }),
    /sandbox 阶段失败/u,
  );
  assert.deepEqual(executed, [
    "database-migrations",
    "account-bootstrap",
    "model-provider",
    "full-knowledge",
    "platform-baseline",
    "sandbox",
  ]);
});

test("十阶段证据全部满足正式条件时才生成 PASSED 总索引", async () => {
  const coverageEvidence = completeLaunchCoverageEvidence();
  const evidenceByStage = {
    "database-migrations": databaseMigrationEvidence(),
    "account-bootstrap": { status: "PASSED", verifiedAccountCount: 9 },
    "model-provider": {
      status: "PASSED",
      provider: { enabled: true, status: "HEALTHY" },
      evaluation: {
        totalCases: 3,
        passedCases: 3,
        failedCases: 0,
        status: "PASSED",
      },
    },
    "platform-baseline": platformBaselineEvidence(),
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
    "launch-coverage": coverageEvidence,
  };
  const written = [];
  const result = await runFullSystemRehearsal(rehearsalConfig(), {
    runCommand: async () => ({ exitCode: 0 }),
    readJson: (_path, stage) => evidenceByStage[stage.id],
    writeJson: (file, value) => written.push({ file, value }),
    now: () => "2026-06-22T09:00:00.000Z",
  });

  assert.equal(result.status, "PASSED");
  assert.equal(result.source, "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17");
  assert.equal(result.stages.length, 10);
  assert.deepEqual(
    result.coverage.scenarios,
    coverageEvidence.coverage.scenarios,
  );
  assert.deepEqual(
    result.coverage.versionedAssets,
    coverageEvidence.coverage.versionedAssets,
  );
  assert.equal(result.coverage.databaseDialects.length, 5);
  assert.equal(result.acceptance.length, 15);
  assert.equal(
    result.acceptance.find((item) => item.code === "LAUNCH-11")?.status,
    "PASSED",
  );
  assert.equal(result.coverage.standardPatientResources.length, 13);
  assert.equal(result.coverage.deliveryShapes.length, 5);
  assert.equal(result.coverage.serviceCombinations.length, 7);
  assert.equal(result.coverage.stakeholderViews.length, 12);
  assert.equal(result.coverage.menuEntryCoreActionRows.length, 34);
  assert.equal(
    result.acceptance.find((item) => item.code === "LAUNCH-09")?.status,
    "PASSED",
  );
  assert.equal(written.length, 1);
  assert.equal(written[0].value.status, "PASSED");
});

test("整套演练持续输出十阶段进度并在总索引记录阶段耗时", async () => {
  const progress = [];
  const result = await runFullSystemRehearsal(rehearsalConfig(), {
    runCommand: async () => ({ exitCode: 0 }),
    readJson: (_path, stage) => completeStageEvidence()[stage.id],
    writeJson: () => {},
    now: steppedClock("2026-06-25T08:00:00.000Z"),
    onProgress: (event) => progress.push(event),
  });

  assert.equal(progress[0].type, "stage-start");
  assert.equal(progress[0].stageId, "database-migrations");
  assert.ok(
    progress.some(
      (event) =>
        event.type === "stage-complete" &&
        event.stageId === "full-knowledge" &&
        event.completed === 4 &&
        event.remaining === 6 &&
        event.durationMs > 0,
    ),
  );
  assert.equal(progress.at(-1).type, "rehearsal-complete");
  assert.equal(progress.at(-1).stageCount, 10);
  assert.ok(result.stages.every((stage) => stage.durationMs > 0));
});

test("完整上线覆盖矩阵缺项、跳过或未知时证据门禁拒绝放行", () => {
  const complete = completeLaunchCoverageEvidence();
  assert.doesNotThrow(() => assertCompleteLaunchCoverage(complete));

  const missingScenario = structuredClone(complete);
  missingScenario.coverage.scenarios =
    missingScenario.coverage.scenarios.filter((item) => item.code !== "S40");
  assert.throws(() => assertCompleteLaunchCoverage(missingScenario), /S0–S40/u);

  const missingDialect = structuredClone(complete);
  missingDialect.coverage.databaseDialects =
    missingDialect.coverage.databaseDialects.filter(
      (item) => item.code !== "DM",
    );
  assert.throws(
    () => assertCompleteLaunchCoverage(missingDialect),
    /五数据库方言/u,
  );

  const missingThirdPartyDegradation = structuredClone(complete);
  missingThirdPartyDegradation.coverage.thirdPartySystemFamilyDegradationRows =
    missingThirdPartyDegradation.coverage.thirdPartySystemFamilyDegradationRows.filter(
      (item) => item.code !== "MODEL_DIFY_AGENT",
    );
  assert.throws(
    () => assertCompleteLaunchCoverage(missingThirdPartyDegradation),
    /第三方系统族断连降级行/u,
  );

  const missingTargetRehearsal = structuredClone(complete);
  delete missingTargetRehearsal.coverage.targetEnvironmentRehearsal;
  assert.throws(
    () => assertCompleteLaunchCoverage(missingTargetRehearsal),
    /目标环境上线复演/u,
  );

  const skippedAsset = structuredClone(complete);
  skippedAsset.coverage.versionedAssets[0].status = "SKIPPED";
  assert.throws(() => assertCompleteLaunchCoverage(skippedAsset), /SKIPPED/u);

  const selfCertified = structuredClone(complete);
  selfCertified.coverage.versionedAssets[0].evidenceStage = "launch-coverage";
  assert.throws(
    () => assertCompleteLaunchCoverage(selfCertified),
    /不能由覆盖审计阶段自证/u,
  );

  assert.throws(
    () =>
      validateStageEvidence("platform-baseline", {
        status: "PASSED",
        fieldCatalog: { assetType: "RULE", entryState: "ACTIVE" },
        baseline: { revisionNo: 1 },
      }),
    /字段目录平台基线/u,
  );
  assert.throws(
    () =>
      validateStageEvidence("platform-baseline", {
        ...platformBaselineEvidence(),
        knowledgeAssets: platformBaselineEvidence().knowledgeAssets.slice(
          0,
          10,
        ),
      }),
    /全知识平台基线/u,
  );
  assert.throws(
    () =>
      validateStageEvidence("full-knowledge", {
        status: "PASSED",
        coverage: {
          expectedDomains: Array(11).fill("X"),
          publishedDomains: [],
        },
        versionLifecycle: {},
      }),
    /11 个知识域/u,
  );
  assert.throws(
    () =>
      validateStageEvidence("full-knowledge", {
        ...completeStageEvidence()["full-knowledge"],
        sourceVerification: [],
      }),
    /全知识来源核验/u,
  );
  assert.throws(
    () =>
      validateStageEvidence("full-knowledge", {
        ...completeStageEvidence()["full-knowledge"],
        knowledge: completeStageEvidence()["full-knowledge"].knowledge.map(
          (item, index) =>
            index === 0
              ? {
                  ...item,
                  runtimeEvidence: {
                    ...item.runtimeEvidence,
                    citationCount: 0,
                  },
                }
              : item,
        ),
      }),
    /质量门、影子评测或运行证据/u,
  );
  assert.throws(
    () =>
      validateStageEvidence("browser-e2e", {
        stats: { expected: 81, unexpected: 1, flaky: 0 },
      }),
    /浏览器全量旅程存在失败/u,
  );
});

test("目标环境复演标准化必须保留五类破坏性上线证据", () => {
  const evidence = buildTargetEnvironmentRehearsalEvidence(
    targetEnvironmentEvidence(),
    {
      now: () => "2026-06-22T09:30:00.000Z",
      webBaseUrl: "https://193.112.107.134/medkernel",
      apiBaseUrl: "https://193.112.107.134/medkernel/api/v1",
      source: "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17",
    },
  );

  assert.equal(evidence.status, "PASSED");
  assert.deepEqual(
    evidence.launchCoverage.targetEnvironmentRehearsal.map((item) => item.code),
    [
      "BACKUP_RESTORE_BEFORE_CLEAN",
      "CLEAN_DATABASE_V1_ONLY",
      "DEPLOY_CURRENT_ARTIFACT",
      "FULL_FUNCTION_FULL_KNOWLEDGE_REHEARSAL",
      "RESTART_AND_SECOND_RESTORE",
    ],
  );
  assert.throws(
    () =>
      buildTargetEnvironmentRehearsalEvidence({
        ...targetEnvironmentEvidence(),
        checks: targetEnvironmentEvidence().checks.slice(0, 4),
      }),
    /目标环境上线复演证据/u,
  );
});

test("目标环境复演 CLI 从外部源证据生成标准阶段证据", () => {
  const tempRoot = mkdtempSync(path.join(tmpdir(), "medkernel-target-env-"));
  try {
    const sourcePath = path.join(tempRoot, "source.json");
    const outputPath = path.join(tempRoot, "target-environment.json");
    writeFileSync(
      sourcePath,
      JSON.stringify(targetEnvironmentEvidence()),
      "utf8",
    );

    const result = spawnSync(
      process.execPath,
      ["scripts/release/target-environment-rehearsal.mjs"],
      {
        cwd: REPO_ROOT,
        encoding: "utf8",
        env: {
          ...process.env,
          LAUNCH_TARGET_ENVIRONMENT_SOURCE_PATH: sourcePath,
          LAUNCH_TARGET_ENVIRONMENT_EVIDENCE_PATH: outputPath,
          LAUNCH_WEB_BASE_URL: "https://193.112.107.134/medkernel",
          LAUNCH_API_BASE_URL: "https://193.112.107.134/medkernel/api/v1",
          LAUNCH_SOURCE: "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17",
        },
      },
    );

    assert.equal(result.status, 0, result.stderr || result.stdout);
    const evidence = JSON.parse(readFileSync(outputPath, "utf8"));
    assert.equal(evidence.stage, "TARGET_ENVIRONMENT_REHEARSAL");
    assert.deepEqual(
      evidence.launchCoverage.targetEnvironmentRehearsal.map(
        (item) => item.code,
      ),
      [
        "BACKUP_RESTORE_BEFORE_CLEAN",
        "CLEAN_DATABASE_V1_ONLY",
        "DEPLOY_CURRENT_ARTIFACT",
        "FULL_FUNCTION_FULL_KNOWLEDGE_REHEARSAL",
        "RESTART_AND_SECOND_RESTORE",
      ],
    );
  } finally {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

function completeLaunchCoverageEvidence() {
  return {
    status: "PASSED",
    coverage: completeLaunchCoverageRows(),
  };
}

function completeLaunchCoverageRows() {
  return Object.fromEntries(
    Object.entries(buildRequiredLaunchCoverage()).map(([key, rows]) => [
      key,
      rows.map((row) => ({
        ...row,
        status: "PASSED",
        evidenceStage: evidenceStageForCoverageKey(key),
        evidencePath: evidencePathForCoverageKey(key),
        evidenceKey: `launchCoverage.${key}.${row.code}`,
        observedCode: row.code,
        observedStatus: "PASSED",
        observedAt: "2026-06-22T09:00:00.000Z",
      })),
    ]),
  );
}

function evidenceStageForCoverageKey(key) {
  if (key === "knowledgeDomains") return "full-knowledge";
  if (key === "databaseDialects" || key === "databaseMigrationSource") {
    return "database-migrations";
  }
  if (key === "targetEnvironmentRehearsal") return "target-environment";
  return "browser-e2e";
}

function evidencePathForCoverageKey(key) {
  if (key === "knowledgeDomains") {
    return "/var/lib/medkernel/evidence/current-launch/full-knowledge.json";
  }
  if (key === "databaseDialects" || key === "databaseMigrationSource") {
    return "/var/lib/medkernel/evidence/current-launch/database-migrations.json";
  }
  if (key === "targetEnvironmentRehearsal") {
    return "/var/lib/medkernel/evidence/current-launch/target-environment.json";
  }
  return "/var/lib/medkernel/evidence/current-launch/e2e/report/results.json";
}

function completeStageEvidence() {
  return {
    "database-migrations": databaseMigrationEvidence(),
    "account-bootstrap": { status: "PASSED", verifiedAccountCount: 9 },
    "model-provider": {
      status: "PASSED",
      provider: { enabled: true, status: "HEALTHY" },
      evaluation: {
        totalCases: 3,
        passedCases: 3,
        failedCases: 0,
        status: "PASSED",
      },
    },
    "platform-baseline": platformBaselineEvidence(),
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
    "launch-coverage": completeLaunchCoverageEvidence(),
  };
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
    launchCoverage: testLaunchCoverageClaims([
      ["databaseMigrationSource", "SINGLE_SCHEMA_GENERATOR_CHECK"],
      ...dialects.map((code) => ["databaseDialects", code]),
    ]),
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
      source: "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17",
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
    launchCoverage: testLaunchCoverageClaims([
      ["targetEnvironmentRehearsal", "BACKUP_RESTORE_BEFORE_CLEAN"],
      ["targetEnvironmentRehearsal", "CLEAN_DATABASE_V1_ONLY"],
      ["targetEnvironmentRehearsal", "DEPLOY_CURRENT_ARTIFACT"],
      ["targetEnvironmentRehearsal", "FULL_FUNCTION_FULL_KNOWLEDGE_REHEARSAL"],
      ["targetEnvironmentRehearsal", "RESTART_AND_SECOND_RESTORE"],
    ]),
  };
}

function testLaunchCoverageClaims(entries) {
  const claims = {};
  for (const [key, code] of entries) {
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

const fullKnowledgeDomains = [
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

function fullKnowledgeEvidence() {
  return {
    status: "PASSED",
    coverage: {
      expectedDomains: fullKnowledgeDomains,
      publishedDomains: fullKnowledgeDomains,
      structuralTemplatesObserved: 11,
    },
    sourceVerification: fullKnowledgeDomains.map((domain) => ({
      domain,
      status: "VERIFIED",
      sourceUrl: `https://example.org/${domain.toLowerCase()}`,
      httpStatus: 200,
      contentSha256: "a".repeat(64),
      matchedTerms: [domain],
    })),
    knowledge: fullKnowledgeDomains.map((domain, index) => ({
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
        ...fullKnowledgeDomains.map((domain, index) => ({
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

function steppedClock(startIso) {
  let current = Date.parse(startIso);
  return () => {
    const value = new Date(current).toISOString();
    current += 1000;
    return value;
  };
}

function platformBaselineEvidence() {
  return {
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
    baseline: {
      baselineReleaseId: "baseline-1",
      revisionNo: 1,
    },
    knowledge: {
      manifestCode: "MEDKERNEL-FULL-KNOWLEDGE-REHEARSAL",
      releaseVersion: "1.0.0",
      requiredCount: 11,
      activeCount: 11,
      missingIdentities: [],
    },
    knowledgeAssets: [
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
    ].map((domain) => ({
      assetType: "KNOWLEDGE",
      assetIdentity: `launch.${domain.toLowerCase()}.asset`,
      entryState: "ACTIVE",
      versionId: `knowledge-${domain.toLowerCase()}-v1`,
      versionNo: "V1",
    })),
  };
}

function rehearsalConfig() {
  return readFullSystemRehearsalConfig(baseEnv(), {
    repoRoot: "/workspace/medkernel",
  });
}

function baseEnv() {
  return {
    MEDKERNEL_RUNTIME_ROOT: "/var/lib/medkernel",
    LAUNCH_WEB_BASE_URL: "https://193.112.107.134/medkernel",
    LAUNCH_API_BASE_URL: "https://193.112.107.134/medkernel/api/v1",
    LAUNCH_BOOTSTRAP_TOKEN_FILE:
      "/var/lib/medkernel/credentials/bootstrap-init-token",
    LAUNCH_CREDENTIALS_FILE:
      "/var/lib/medkernel/credentials/current-launch.json",
    LAUNCH_MODEL_PROVIDER_CODE: "ollama-launch",
    LAUNCH_MODEL_PROVIDER_TYPE: "OLLAMA",
    LAUNCH_MODEL_PROVIDER_ENDPOINT: "http://127.0.0.1:11434",
    LAUNCH_MODEL_VERSION: "medkernel-qwen25:1.5b-v1",
    LAUNCH_TARGET_ENVIRONMENT_SOURCE_PATH:
      "/var/lib/medkernel/evidence/target-environment-source.json",
    FULL_KNOWLEDGE_MANIFEST_PATH: MANIFEST_PATH,
    LAUNCH_SOURCE: "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17",
  };
}
