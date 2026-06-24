import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  buildFullSystemStagePlan,
  readFullSystemRehearsalConfig,
  runFullSystemRehearsal,
  validateStageEvidence,
} from "./full-system-rehearsal-lib.mjs";

const MANIFEST_PATH = fileURLToPath(
  new URL("../knowledge/manifests/full-knowledge-rehearsal-1.0.0.json", import.meta.url),
);

test("整套演练固定覆盖四职责、Provider、沙盘、11 域知识、运行韧性和全量浏览器旅程", () => {
  const config = rehearsalConfig();
  const plan = buildFullSystemStagePlan(config);

  assert.deepEqual(
    plan.map((stage) => stage.id),
    [
      "account-bootstrap",
      "model-provider",
      "sandbox",
      "full-knowledge",
      "runtime-resilience",
      "browser-e2e",
    ],
  );
  assert.equal(plan[0].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[1].env.FULL_KNOWLEDGE_MANIFEST_PATH, MANIFEST_PATH);
  assert.equal(plan[2].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[2].label, "演练机构十规则四十用例与机构生效版本");
  assert.equal(plan[3].env.FULL_KNOWLEDGE_PROVIDER_CODE, "ollama-launch");
  assert.equal(plan[4].env.RUNTIME_RESILIENCE_PROVIDER_CODE, "ollama-launch");
  assert.equal(plan[4].env.LAUNCH_CREDENTIALS_FILE, config.credentialsPath);
  assert.equal(plan[5].cwd.endsWith("/frontend"), true);
  assert.equal(plan[5].env.E2E_EXTERNAL_DEPLOYMENT, "1");
  assert.equal(plan[5].env.E2E_EXPECT_MFA_DISABLED, "1");
  assert.equal(plan[5].env.E2E_IGNORE_HTTPS_ERRORS, undefined);
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
            : {
                status: "PASSED",
                provider: { enabled: true, status: "HEALTHY" },
                evaluation: {
                  status: "PASSED",
                  totalCases: 3,
                  passedCases: 3,
                  failedCases: 0,
                },
              },
      }),
    /sandbox 阶段失败/u,
  );
  assert.deepEqual(executed, ["account-bootstrap", "model-provider", "sandbox"]);
});

test("六阶段证据全部满足正式条件时才生成 PASSED 总索引", async () => {
  const evidenceByStage = {
    "account-bootstrap": { status: "PASSED", verifiedAccountCount: 9 },
    "model-provider": {
      status: "PASSED",
      provider: { enabled: true, status: "HEALTHY" },
      evaluation: { totalCases: 3, passedCases: 3, failedCases: 0, status: "PASSED" },
    },
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
      b0: { fixtureCount: 17, passedCount: 17, modelRequiredCount: 0 },
      restored: {
        providerEnabled: true,
        providerStatus: "HEALTHY",
        readinessReady: true,
        modelInvocationAllowed: true,
      },
    },
    "browser-e2e": { stats: { expected: 82, unexpected: 0, flaky: 0 } },
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
  assert.equal(result.stages.length, 6);
  assert.equal(written.length, 1);
  assert.equal(written[0].value.status, "PASSED");
});

test("全知识缺域或浏览器存在非预期失败时证据门禁拒绝放行", () => {
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
