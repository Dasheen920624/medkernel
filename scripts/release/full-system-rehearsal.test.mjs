import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  assertCompleteLaunchCoverage,
  buildFullSystemStagePlan,
  buildRequiredLaunchCoverage,
  readFullSystemRehearsalConfig,
  runFullSystemRehearsal,
  validateStageEvidence,
} from "./full-system-rehearsal-lib.mjs";

const MANIFEST_PATH = fileURLToPath(
  new URL("../knowledge/manifests/full-knowledge-rehearsal-1.0.0.json", import.meta.url),
);

test("整套演练固定覆盖四职责、Provider、平台基线、沙盘、11 域知识、运行韧性、全量浏览器旅程和完整范围审计", () => {
  const config = rehearsalConfig();
  const plan = buildFullSystemStagePlan(config);

  assert.deepEqual(
    plan.map((stage) => stage.id),
    [
      "account-bootstrap",
      "model-provider",
      "platform-baseline",
      "sandbox",
      "full-knowledge",
      "runtime-resilience",
      "browser-e2e",
      "launch-coverage",
    ],
  );
  assert.equal(plan[0].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[1].env.FULL_KNOWLEDGE_MANIFEST_PATH, MANIFEST_PATH);
  assert.equal(plan[2].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[2].env.LAUNCH_PLATFORM_BASELINE_EVIDENCE_PATH.endsWith("/platform-baseline.json"), true);
  assert.equal(plan[2].label, "平台字段目录权威基线");
  assert.equal(plan[3].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[3].label, "演练机构十规则四十用例与机构生效版本");
  assert.equal(plan[4].env.FULL_KNOWLEDGE_PROVIDER_CODE, "ollama-launch");
  assert.equal(plan[5].env.RUNTIME_RESILIENCE_PROVIDER_CODE, "ollama-launch");
  assert.equal(plan[5].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[6].cwd.endsWith("/frontend"), true);
  assert.equal(plan[6].env.E2E_EXTERNAL_DEPLOYMENT, "1");
  assert.equal(plan[6].env.E2E_EXPECT_MFA_DISABLED, "1");
  assert.equal(plan[6].env.E2E_IGNORE_HTTPS_ERRORS, undefined);
  assert.equal(plan[7].env.LAUNCH_COVERAGE_EVIDENCE_PATH.endsWith("/launch-coverage.json"), true);
  assert.equal(plan[7].env.FULL_SYSTEM_EVIDENCE_ROOT, config.evidenceRoot);
});

test("整套演练配置拒绝跳过 TLS 校验并把全部证据固定在仓库外", () => {
  const env = baseEnv();
  env.E2E_IGNORE_HTTPS_ERRORS = "1";
  assert.throws(
    () => readFullSystemRehearsalConfig(env, { repoRoot: "/workspace/medkernel" }),
    /禁止忽略 HTTPS 证书错误/u,
  );

  delete env.E2E_IGNORE_HTTPS_ERRORS;
  const config = readFullSystemRehearsalConfig(env, {
    repoRoot: "/workspace/medkernel",
  });
  assert.equal(config.evidenceRoot, "/var/lib/medkernel/evidence/current-launch");
  assert.equal(config.indexPath, "/var/lib/medkernel/evidence/current-launch/full-system.json");
  assert.equal(config.source, "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17");

  delete env.LAUNCH_SOURCE;
  assert.throws(
    () => readFullSystemRehearsalConfig(env, { repoRoot: "/workspace/medkernel" }),
    /LAUNCH_SOURCE/u,
  );
  env.LAUNCH_SOURCE = "1603b5a7";
  assert.throws(
    () => readFullSystemRehearsalConfig(env, { repoRoot: "/workspace/medkernel" }),
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
        readJson: (_path, stage) =>
          stage.id === "account-bootstrap"
            ? { status: "PASSED", verifiedAccountCount: 9 }
            : stage.id === "model-provider"
            ? {
                status: "PASSED",
                provider: { enabled: true, status: "HEALTHY" },
                evaluation: {
                  status: "PASSED",
                  totalCases: 3,
                  passedCases: 3,
                  failedCases: 0,
                }
              }
            : platformBaselineEvidence(),
      }),
    /sandbox 阶段失败/u,
  );
  assert.deepEqual(executed, ["account-bootstrap", "model-provider", "platform-baseline", "sandbox"]);
});

test("八阶段证据全部满足正式条件时才生成 PASSED 总索引", async () => {
  const coverageEvidence = completeLaunchCoverageEvidence();
  const evidenceByStage = {
    "account-bootstrap": { status: "PASSED", verifiedAccountCount: 9 },
    "model-provider": {
      status: "PASSED",
      provider: { enabled: true, status: "HEALTHY" },
      evaluation: { totalCases: 3, passedCases: 3, failedCases: 0, status: "PASSED" },
    },
    "platform-baseline": platformBaselineEvidence(),
    sandbox: {
      results: Array.from({ length: 10 }, (_, index) => ({ ruleCode: `R${index}`, result: "PASS" })),
      failures: [],
      runtimeBinding: { ready: true, externalSideEffects: false },
    },
    "full-knowledge": {
      status: "PASSED",
      coverage: {
        expectedDomains: [
          "GUIDELINE", "DRUG", "PATHWAY_KNOWLEDGE", "NURSING", "DIAGNOSTIC_ITEM", "TCM",
          "PROTOCOL", "POLICY", "LITERATURE", "OTHER", "DIAGNOSIS",
        ],
        publishedDomains: [
          "GUIDELINE", "DRUG", "PATHWAY_KNOWLEDGE", "NURSING", "DIAGNOSTIC_ITEM", "TCM",
          "PROTOCOL", "POLICY", "LITERATURE", "OTHER", "DIAGNOSIS",
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
  assert.equal(result.stages.length, 8);
  assert.deepEqual(result.coverage.scenarios, coverageEvidence.coverage.scenarios);
  assert.deepEqual(result.coverage.versionedAssets, coverageEvidence.coverage.versionedAssets);
  assert.equal(result.coverage.standardPatientResources.length, 13);
  assert.equal(result.coverage.deliveryShapes.length, 5);
  assert.equal(result.coverage.serviceCombinations.length, 7);
  assert.equal(written.length, 1);
  assert.equal(written[0].value.status, "PASSED");
});

test("整套演练持续输出八阶段进度并在总索引记录阶段耗时", async () => {
  const progress = [];
  const result = await runFullSystemRehearsal(rehearsalConfig(), {
    runCommand: async () => ({ exitCode: 0 }),
    readJson: (_path, stage) => completeStageEvidence()[stage.id],
    writeJson: () => {},
    now: steppedClock("2026-06-25T08:00:00.000Z"),
    onProgress: (event) => progress.push(event),
  });

  assert.equal(progress[0].type, "stage-start");
  assert.equal(progress[0].stageId, "account-bootstrap");
  assert.ok(
    progress.some(
      (event) =>
        event.type === "stage-complete" &&
        event.stageId === "full-knowledge" &&
        event.completed === 5 &&
        event.remaining === 3 &&
        event.durationMs > 0,
    ),
  );
  assert.equal(progress.at(-1).type, "rehearsal-complete");
  assert.equal(progress.at(-1).stageCount, 8);
  assert.ok(result.stages.every((stage) => stage.durationMs > 0));
});

test("完整上线覆盖矩阵缺项、跳过或未知时证据门禁拒绝放行", () => {
  const complete = completeLaunchCoverageEvidence();
  assert.doesNotThrow(() => assertCompleteLaunchCoverage(complete));

  const missingScenario = structuredClone(complete);
  missingScenario.coverage.scenarios = missingScenario.coverage.scenarios.filter((item) => item.code !== "S40");
  assert.throws(() => assertCompleteLaunchCoverage(missingScenario), /S0–S40/u);

  const skippedAsset = structuredClone(complete);
  skippedAsset.coverage.versionedAssets[0].status = "SKIPPED";
  assert.throws(() => assertCompleteLaunchCoverage(skippedAsset), /SKIPPED/u);

  assert.throws(
    () => validateStageEvidence("platform-baseline", {
      status: "PASSED",
      fieldCatalog: { assetType: "RULE", entryState: "ACTIVE" },
      baseline: { revisionNo: 1 },
    }),
    /字段目录平台基线/u,
  );
  assert.throws(
    () => validateStageEvidence("full-knowledge", {
      status: "PASSED",
      coverage: { expectedDomains: Array(11).fill("X"), publishedDomains: [] },
      versionLifecycle: {},
    }),
    /11 个知识域/u,
  );
  assert.throws(
    () => validateStageEvidence("browser-e2e", {
      stats: { expected: 81, unexpected: 1, flaky: 0 },
    }),
    /浏览器全量旅程存在失败/u,
  );
});

function completeLaunchCoverageEvidence() {
  return {
    status: "PASSED",
    coverage: buildRequiredLaunchCoverage(),
  };
}

function completeStageEvidence() {
  return {
    "account-bootstrap": { status: "PASSED", verifiedAccountCount: 9 },
    "model-provider": {
      status: "PASSED",
      provider: { enabled: true, status: "HEALTHY" },
      evaluation: { totalCases: 3, passedCases: 3, failedCases: 0, status: "PASSED" },
    },
    "platform-baseline": platformBaselineEvidence(),
    sandbox: {
      results: Array.from({ length: 10 }, (_, index) => ({ ruleCode: `R${index}`, result: "PASS" })),
      failures: [],
      runtimeBinding: { ready: true, externalSideEffects: false },
    },
    "full-knowledge": {
      status: "PASSED",
      coverage: {
        expectedDomains: [
          "GUIDELINE", "DRUG", "PATHWAY_KNOWLEDGE", "NURSING", "DIAGNOSTIC_ITEM", "TCM",
          "PROTOCOL", "POLICY", "LITERATURE", "OTHER", "DIAGNOSIS",
        ],
        publishedDomains: [
          "GUIDELINE", "DRUG", "PATHWAY_KNOWLEDGE", "NURSING", "DIAGNOSTIC_ITEM", "TCM",
          "PROTOCOL", "POLICY", "LITERATURE", "OTHER", "DIAGNOSIS",
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
    "launch-coverage": completeLaunchCoverageEvidence(),
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
    LAUNCH_BOOTSTRAP_TOKEN_FILE: "/var/lib/medkernel/credentials/bootstrap-init-token",
    LAUNCH_CREDENTIALS_FILE: "/var/lib/medkernel/credentials/current-launch.json",
    LAUNCH_MODEL_PROVIDER_CODE: "ollama-launch",
    LAUNCH_MODEL_PROVIDER_TYPE: "OLLAMA",
    LAUNCH_MODEL_PROVIDER_ENDPOINT: "http://127.0.0.1:11434",
    LAUNCH_MODEL_VERSION: "medkernel-qwen25:1.5b-v1",
    FULL_KNOWLEDGE_MANIFEST_PATH: MANIFEST_PATH,
    LAUNCH_SOURCE: "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17",
  };
}
