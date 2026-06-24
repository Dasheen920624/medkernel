#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertCompleteLaunchCoverage,
  buildRequiredLaunchCoverage,
  validateStageEvidence,
} from "./full-system-rehearsal-lib.mjs";
import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import { validateFullKnowledgeManifest } from "../knowledge/full-knowledge-rehearsal-lib.mjs";

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

export function readLaunchCoverageAuditConfig(env, options = {}) {
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  const evidenceRoot = outsideRepo(
    env.FULL_SYSTEM_EVIDENCE_ROOT,
    repoRoot,
    "整套演练证据目录",
  );
  const outputPath = outsideRepo(
    env.LAUNCH_COVERAGE_EVIDENCE_PATH,
    repoRoot,
    "完整覆盖审计证据路径",
  );
  const manifestPath = path.resolve(requireText(env.FULL_KNOWLEDGE_MANIFEST_PATH, "全知识清单路径"));
  const source = normalizeSource(env.LAUNCH_SOURCE);
  return {
    evidenceRoot,
    outputPath,
    manifestPath,
    source,
  };
}

export function buildLaunchCoverageEvidence(config, options = {}) {
  const readJson = options.readJson ?? readJsonFile;
  const now = options.now ?? (() => new Date().toISOString());
  const stageFiles = {
    "account-bootstrap": path.join(config.evidenceRoot, "account-bootstrap.json"),
    "model-provider": path.join(config.evidenceRoot, "model-provider.json"),
    "platform-baseline": path.join(config.evidenceRoot, "platform-baseline.json"),
    sandbox: path.join(config.evidenceRoot, "sandbox/seed-summary.json"),
    "full-knowledge": path.join(config.evidenceRoot, "full-knowledge.json"),
    "runtime-resilience": path.join(config.evidenceRoot, "runtime-resilience.json"),
    "browser-e2e": path.join(config.evidenceRoot, "e2e/report/results.json"),
  };
  const stageStatus = {};
  const failedStages = [];
  for (const [stage, file] of Object.entries(stageFiles)) {
    const evidence = readJson(file, stage);
    try {
      validateStageEvidence(stage, evidence);
      stageStatus[stage] = "PASSED";
    } catch (error) {
      stageStatus[stage] = "FAILED";
      failedStages.push({ stage, detail: error.message });
    }
  }
  if (failedStages.length > 0) {
    throw new Error(
      `完整覆盖审计前置阶段未全部通过：${failedStages
        .map((item) => `${item.stage}（${item.detail}）`)
        .join("；")}`,
    );
  }
  const manifest = readJson(config.manifestPath, "全知识演练清单");
  validateFullKnowledgeManifest(manifest);

  const coverage = buildRequiredLaunchCoverage();
  const evidence = {
    schemaVersion: "1.0.0",
    status: "PASSED",
    source: config.source,
    generatedAt: now(),
    stageStatus,
    coverage,
  };
  assertCompleteLaunchCoverage(evidence);
  return evidence;
}

function readJsonFile(file, label) {
  if (!existsSync(file)) {
    throw new Error(`${label} 阶段证据不存在：${file}`);
  }
  return JSON.parse(readFileSync(file, "utf8"));
}

function outsideRepo(value, repoRoot, label) {
  const target = path.resolve(requireText(value, label));
  const relative = path.relative(repoRoot, target);
  if (relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative))) {
    throw new Error(`${label}必须位于代码仓库之外`);
  }
  return target;
}

function normalizeSource(value) {
  const source = requireText(value, "LAUNCH_SOURCE");
  if (!/^[a-f0-9]{40}$/iu.test(source)) {
    throw new Error("LAUNCH_SOURCE 必须是 40 位提交哈希");
  }
  return source.toLowerCase();
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label}不能为空`);
  }
  return value.trim();
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    const config = readLaunchCoverageAuditConfig(process.env);
    const evidence = buildLaunchCoverageEvidence(config);
    writeJsonAtomic(config.outputPath, evidence);
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
