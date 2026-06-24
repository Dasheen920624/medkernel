import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  buildLaunchCoverageEvidence,
  readLaunchCoverageAuditConfig,
} from "./launch-coverage-audit.mjs";

const MANIFEST_PATH = fileURLToPath(
  new URL("../knowledge/manifests/full-knowledge-rehearsal-1.0.0.json", import.meta.url),
);
const SOURCE = "1603b5a7575dc1b5c6b110ee7bef908ca3d2ce17";

test("完整覆盖审计配置固定仓库外证据与 40 位来源提交", () => {
  const env = baseEnv();
  const config = readLaunchCoverageAuditConfig(env, { repoRoot: "/workspace/medkernel" });

  assert.equal(config.evidenceRoot, "/var/lib/medkernel/evidence/current-launch");
  assert.equal(config.outputPath, "/var/lib/medkernel/evidence/current-launch/launch-coverage.json");
  assert.equal(config.manifestPath, MANIFEST_PATH);
  assert.equal(config.source, SOURCE);

  env.LAUNCH_COVERAGE_EVIDENCE_PATH = "/workspace/medkernel/tmp/launch-coverage.json";
  assert.throws(
    () => readLaunchCoverageAuditConfig(env, { repoRoot: "/workspace/medkernel" }),
    /完整覆盖审计证据路径必须位于代码仓库之外/u,
  );

  env.LAUNCH_COVERAGE_EVIDENCE_PATH = "/var/lib/medkernel/evidence/current-launch/launch-coverage.json";
  env.LAUNCH_SOURCE = "1603b5a7";
  assert.throws(
    () => readLaunchCoverageAuditConfig(env, { repoRoot: "/workspace/medkernel" }),
    /40 位提交哈希/u,
  );
});

test("完整覆盖审计复用统一阶段门禁并生成上线范围矩阵", () => {
  const evidence = buildLaunchCoverageEvidence(auditConfig(), {
    readJson: readKnownEvidence(completeStageEvidence()),
    now: () => "2026-06-22T09:00:00.000Z",
  });

  assert.equal(evidence.status, "PASSED");
  assert.deepEqual(Object.values(evidence.stageStatus), Array(7).fill("PASSED"));
  assert.equal(evidence.coverage.standardPatientResources.length, 13);
  assert.equal(evidence.coverage.versionedAssets.length, 13);
  assert.equal(evidence.coverage.knowledgeDomains.length, 11);
  assert.equal(evidence.coverage.scenarios.length, 41);
});

test("完整覆盖审计遇到任一前置阶段失败时拒绝生成 PASSED 证据", () => {
  const stageEvidence = completeStageEvidence();
  stageEvidence["browser-e2e"] = { stats: { expected: 82, unexpected: 1, flaky: 0 } };

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
    outputPath: "/var/lib/medkernel/evidence/current-launch/launch-coverage.json",
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

function completeStageEvidence() {
  return {
    "account-bootstrap": { status: "PASSED", verifiedAccountCount: 9 },
    "model-provider": {
      status: "PASSED",
      provider: { enabled: true, status: "HEALTHY", code: "ollama-launch" },
      evaluation: { totalCases: 3, passedCases: 3, failedCases: 0, status: "PASSED" },
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
}
