#!/usr/bin/env node
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertCompleteLaunchCoverage,
  buildLaunchAcceptance,
  buildLaunchCoverageFromStageEvidence,
  buildRequiredLaunchAcceptance,
  validateStageEvidence,
} from "./full-system-rehearsal-lib.mjs";
import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import { validateFullKnowledgeManifest } from "../knowledge/full-knowledge-rehearsal-lib.mjs";

const REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const LAUNCH_ENTRY_EVIDENCE_SCHEMA_PATH = path.join(
  REPO_ROOT,
  "scripts/release/launch-entry-evidence.schema.json",
);
const PRODUCT_ENTRY_CATALOG_PATH = path.join(
  REPO_ROOT,
  "docs/contracts/product/product-entry-catalog.v1.json",
);
const LAUNCH_LEDGER_SCHEMA_PATH = path.join(
  REPO_ROOT,
  "docs/contracts/release/launch-ledger.v1.schema.json",
);
const launchEntryEvidenceSchema = JSON.parse(
  readFileSync(LAUNCH_ENTRY_EVIDENCE_SCHEMA_PATH, "utf8"),
);
const productEntryCatalog = JSON.parse(
  readFileSync(PRODUCT_ENTRY_CATALOG_PATH, "utf8"),
);
const launchLedgerSchema = JSON.parse(
  readFileSync(LAUNCH_LEDGER_SCHEMA_PATH, "utf8"),
);
const launchLedgerDefinitions = Object.freeze(
  buildRequiredLaunchAcceptance().map((item) =>
    Object.freeze({
      code: item.code,
      label: item.label,
      requiredCoverage: Object.freeze([...item.requiredCoverage]),
    }),
  ),
);
const launchLedgerDefinitionByCode = new Map(
  launchLedgerDefinitions.map((item) => [item.code, item]),
);
const launchLedgerStatuses = Object.freeze([
  ...launchLedgerSchema.$defs.status.enum,
]);
const entryStrengthPolicy = loadEntryStrengthPolicy(launchEntryEvidenceSchema);
const entryStrengthByLevel = new Map(
  entryStrengthPolicy.map((item, index) => [item.level, { ...item, index }]),
);
const fullEntryCapabilities = Object.freeze([
  ...entryStrengthPolicy.at(-1).requiredCapabilities,
]);
const productEntryRequiredStrength =
  launchEntryEvidenceSchema["x-medkernel-product-entry-required-strength"];
if (!entryStrengthByLevel.has(productEntryRequiredStrength)) {
  throw new Error("入口证据 schema 的产品入口要求强度无效");
}
const productEntries = validateProductEntryCatalog(productEntryCatalog);

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
  const manifestPath = path.resolve(
    requireText(env.FULL_KNOWLEDGE_MANIFEST_PATH, "全知识清单路径"),
  );
  const source = normalizeSource(env.LAUNCH_SOURCE);
  const runId = normalizeRunId(env.LAUNCH_RUN_ID);
  const sourceManifestPath = outsideRepo(
    env.LAUNCH_EVIDENCE_SOURCE_MANIFEST_PATH,
    repoRoot,
    "上线前置证据来源清单路径",
  );
  const expectedSourceManifestPath = path.join(
    evidenceRoot,
    "source-provenance.json",
  );
  if (sourceManifestPath !== expectedSourceManifestPath) {
    throw new Error("上线前置证据来源清单必须位于本次证据目录固定路径");
  }
  return {
    evidenceRoot,
    outputPath,
    manifestPath,
    source,
    runId,
    sourceManifestPath,
  };
}

export function buildLaunchCoverageEvidence(config, options = {}) {
  const readJson = options.readJson ?? readJsonFile;
  const now = options.now ?? (() => new Date().toISOString());
  const decidedAt = now();
  if (!isIsoDateTime(decidedAt)) {
    throw new Error("完整覆盖审计判定时间无效");
  }
  const stageFiles = launchStageFiles(config.evidenceRoot);
  const sourceManifest = readJson(
    config.sourceManifestPath,
    "上线前置证据来源清单",
  );
  const sourceRecordByStage = validateEvidenceSourceManifest(
    sourceManifest,
    stageFiles,
    config,
    decidedAt,
  );
  const sourceByEvidencePath = new Map();
  const stageStatus = {};
  const failedStages = [];
  const stageEvidence = [];
  for (const [stage, file] of Object.entries(stageFiles)) {
    const evidence = readJson(file, stage);
    try {
      const source = validateEvidenceSource({
        stageId: stage,
        evidencePath: file,
        evidence,
        sourceRecord: sourceRecordByStage.get(stage),
        expectedPath: file,
        expectedRunId: config.runId,
        expectedCandidateCommit: config.source,
        manifest: sourceManifest,
        decidedAt,
        outputPath: config.outputPath,
      });
      validateStageEvidence(stage, evidence);
      stageStatus[stage] = "PASSED";
      sourceByEvidencePath.set(file, source);
      stageEvidence.push({
        stageId: stage,
        evidencePath: file,
        evidence,
      });
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
  const knowledgeManifest = readJson(config.manifestPath, "全知识演练清单");
  validateFullKnowledgeManifest(knowledgeManifest);

  const coverage = buildLaunchCoverageFromStageEvidence(stageEvidence);
  const generatedAt = decidedAt;
  const evidence = {
    schemaVersion: "1.0.0",
    status: "PASSED",
    source: config.source,
    candidateCommit: config.source,
    runId: config.runId,
    generatedAt,
    ledgerSchemaVersion: launchLedgerSchema["x-medkernel-contract-version"],
    ledgerSchema: "docs/contracts/release/launch-ledger.v1.schema.json",
    stageStatus,
    coverage,
  };
  evidence.acceptance = validateLaunchLedger(
    buildLaunchLedger(
      coverage,
      {
        candidateCommit: config.source,
        runId: config.runId,
        decidedAt: generatedAt,
      },
      sourceByEvidencePath,
    ),
    {
      coverage,
      candidateCommit: config.source,
      runId: config.runId,
      sourceByEvidencePath,
    },
  );
  assertCompleteLaunchCoverage(evidence);
  evidence.entryEvidence = validateProductEntryEvidence(stageEvidence);
  return evidence;
}

function launchStageFiles(evidenceRoot) {
  return {
    "database-migrations": path.join(evidenceRoot, "database-migrations.json"),
    "account-bootstrap": path.join(evidenceRoot, "account-bootstrap.json"),
    "model-provider": path.join(evidenceRoot, "model-provider.json"),
    "platform-baseline": path.join(evidenceRoot, "platform-baseline.json"),
    sandbox: path.join(evidenceRoot, "sandbox/seed-summary.json"),
    "full-knowledge": path.join(evidenceRoot, "full-knowledge.json"),
    "runtime-resilience": path.join(evidenceRoot, "runtime-resilience.json"),
    "target-environment": path.join(evidenceRoot, "target-environment.json"),
    "browser-e2e": path.join(evidenceRoot, "e2e/report/results.json"),
  };
}

function validateEvidenceSourceManifest(
  manifest,
  stageFiles,
  config,
  decidedAt,
) {
  const sourceManifest = requireRecord(manifest, "上线前置证据来源清单");
  requireExactObjectKeys(
    sourceManifest,
    ["schemaVersion", "candidateCommit", "runId", "generatedAt", "sources"],
    "上线前置证据来源清单",
  );
  if (sourceManifest.schemaVersion !== "1.0.0") {
    throw new Error("上线前置证据来源清单 schemaVersion 必须为 1.0.0");
  }
  if (sourceManifest.candidateCommit !== config.source) {
    throw new Error("上线前置证据来源清单候选提交与本次审计不一致");
  }
  if (sourceManifest.runId !== config.runId) {
    throw new Error("上线前置证据来源清单运行标识与本次审计不一致");
  }
  if (
    !isIsoDateTime(sourceManifest.generatedAt) ||
    Date.parse(sourceManifest.generatedAt) > Date.parse(decidedAt)
  ) {
    throw new Error("上线前置证据来源清单生成时间晚于审计判定时间");
  }
  const sources = requireArray(sourceManifest.sources, "上线前置证据来源项");
  const sourceByStage = new Map();
  for (const rawSource of sources) {
    const source = requireRecord(rawSource, "上线前置证据来源项");
    const stageId = requireText(source.stageId, "上线前置证据来源阶段");
    const evidencePath = requireText(
      source.evidencePath,
      `${stageId} 前置证据路径`,
    );
    if (
      stageId === "launch-coverage" ||
      path.resolve(evidencePath) === path.resolve(config.outputPath)
    ) {
      throw new Error("上线前置证据不得引用最终审计自身");
    }
    if (!Object.hasOwn(stageFiles, stageId)) {
      throw new Error(`上线前置证据包含未知阶段 ${stageId}`);
    }
    if (sourceByStage.has(stageId)) {
      throw new Error(`上线前置证据来源阶段重复 ${stageId}`);
    }
    if (path.resolve(evidencePath) !== stageFiles[stageId]) {
      throw new Error(`${stageId} 证据路径不在固定白名单`);
    }
    sourceByStage.set(stageId, source);
  }
  const missingStages = Object.keys(stageFiles).filter(
    (stageId) => !sourceByStage.has(stageId),
  );
  if (
    missingStages.length > 0 ||
    sourceByStage.size !== Object.keys(stageFiles).length
  ) {
    throw new Error(`上线前置证据来源清单缺少 ${missingStages.join("、")}`);
  }
  return sourceByStage;
}

export function validateEvidenceSource({
  stageId,
  evidencePath,
  evidence,
  sourceRecord,
  expectedPath,
  expectedRunId,
  expectedCandidateCommit,
  manifest,
  decidedAt,
  outputPath,
}) {
  const sourceManifest = requireRecord(manifest, "上线前置证据来源清单");
  const source = requireRecord(sourceRecord, `${stageId} 前置证据来源`);
  requireExactObjectKeys(
    source,
    ["stageId", "evidencePath", "contentSha256", "capturedAt"],
    `${stageId} 前置证据来源`,
  );
  const sourceStageId = requireText(
    source.stageId,
    `${stageId} 前置证据来源阶段`,
  );
  const sourceEvidencePath = requireText(
    source.evidencePath,
    `${stageId} 前置证据来源路径`,
  );
  if (
    stageId === "launch-coverage" ||
    path.resolve(evidencePath) === path.resolve(outputPath) ||
    sourceStageId === "launch-coverage" ||
    path.resolve(sourceEvidencePath) === path.resolve(outputPath)
  ) {
    throw new Error("上线前置证据不得引用最终审计自身");
  }
  if (
    path.resolve(evidencePath) !== path.resolve(expectedPath) ||
    path.resolve(sourceEvidencePath) !== path.resolve(expectedPath) ||
    sourceStageId !== stageId
  ) {
    throw new Error(`${stageId} 证据路径不在固定白名单`);
  }
  if (
    sourceManifest.runId !== expectedRunId ||
    sourceManifest.candidateCommit !== expectedCandidateCommit
  ) {
    throw new Error(`${stageId} 前置证据候选或运行标识与本次审计不一致`);
  }
  if (
    !isIsoDateTime(source.capturedAt) ||
    !isIsoDateTime(decidedAt) ||
    Date.parse(source.capturedAt) > Date.parse(decidedAt)
  ) {
    throw new Error(`${stageId} 前置证据捕获时间晚于总账判定时间`);
  }
  if (
    !isIsoDateTime(sourceManifest.generatedAt) ||
    Date.parse(source.capturedAt) > Date.parse(sourceManifest.generatedAt)
  ) {
    throw new Error(`${stageId} 前置证据捕获时间晚于来源清单生成时间`);
  }
  const actualSha256 = sha256Json(evidence);
  if (
    !/^[a-f0-9]{64}$/u.test(source.contentSha256 ?? "") ||
    source.contentSha256 !== actualSha256
  ) {
    throw new Error(`${stageId} 前置证据内容摘要与来源清单不一致`);
  }
  return Object.freeze({
    stageId,
    evidencePath: path.resolve(evidencePath),
    sourceRunId: expectedRunId,
    sourceCandidateCommit: expectedCandidateCommit,
    sourceContentSha256: actualSha256,
    sourceCapturedAt: source.capturedAt,
  });
}

function buildLaunchLedger(coverage, provenance, sourceByEvidencePath) {
  const baseRows = buildLaunchAcceptance(coverage);
  return baseRows.map((item) => {
    const evidenceRefs = item.requiredCoverage.flatMap((coverageKey) =>
      launchLedgerEvidenceRefs(coverage, coverageKey, sourceByEvidencePath),
    );
    const actualEvidenceScope = item.requiredCoverage.filter((coverageKey) =>
      evidenceRefs.some((ref) => ref.coverageKey === coverageKey),
    );
    return {
      code: item.code,
      label: item.label,
      requiredCoverage: [...item.requiredCoverage],
      actualEvidenceScope,
      status: item.status,
      missingCoverage: [...item.missingCoverage],
      evidenceRefs,
      candidateCommit: provenance.candidateCommit,
      runId: provenance.runId,
      decidedAt: provenance.decidedAt,
    };
  });
}

export function validateLaunchLedger(value, options = {}) {
  const rows = requireArray(value, "LAUNCH 总账");
  const rowByCode = new Map();
  for (const rawRow of rows) {
    const row = requireRecord(rawRow, "LAUNCH 总账项");
    const code = requireText(row.code, "LAUNCH 总账编码");
    if (!launchLedgerDefinitionByCode.has(code)) {
      throw new Error(`LAUNCH 总账包含未知编码 ${code}`);
    }
    if (rowByCode.has(code)) {
      throw new Error(`LAUNCH 总账编码重复 ${code}`);
    }
    rowByCode.set(code, row);
  }
  const missingCodes = launchLedgerDefinitions
    .map((item) => item.code)
    .filter((code) => !rowByCode.has(code));
  if (missingCodes.length > 0) {
    throw new Error(`LAUNCH 总账缺少 ${missingCodes.join("、")}`);
  }
  if (rowByCode.size !== launchLedgerDefinitions.length) {
    throw new Error(`LAUNCH 总账必须恰含 ${launchLedgerDefinitions.length} 项`);
  }

  const normalized = launchLedgerDefinitions.map((definition) =>
    validateLaunchLedgerRow(rowByCode.get(definition.code), definition),
  );
  const candidateCommit = normalized[0].candidateCommit;
  const runId = normalized[0].runId;
  const decidedAt = normalized[0].decidedAt;
  for (const row of normalized) {
    if (
      row.candidateCommit !== candidateCommit ||
      row.runId !== runId ||
      row.decidedAt !== decidedAt
    ) {
      throw new Error("LAUNCH 总账十五项必须绑定同一候选、运行标识和判定时间");
    }
  }
  if (
    options.candidateCommit !== undefined &&
    candidateCommit !== options.candidateCommit
  ) {
    throw new Error("LAUNCH 总账候选提交与本次审计不一致");
  }
  if (options.runId !== undefined && runId !== options.runId) {
    throw new Error("LAUNCH 总账运行标识与本次审计不一致");
  }
  if (options.coverage !== undefined) {
    validateLaunchLedgerAgainstCoverage(
      normalized,
      options.coverage,
      options.sourceByEvidencePath,
    );
  }
  return normalized;
}

function validateLaunchLedgerRow(row, definition) {
  requireExactObjectKeys(
    row,
    launchLedgerSchema.$defs.entry.required,
    `${definition.code} 总账项`,
  );
  if (row.label !== definition.label) {
    throw new Error(`${definition.code} 验收语义与固定 schema 不一致`);
  }
  requireExactStringArray(
    row.requiredCoverage,
    definition.requiredCoverage,
    `${definition.code} 要求范围`,
  );
  const actualEvidenceScope = requireCanonicalSubset(
    row.actualEvidenceScope,
    definition.requiredCoverage,
    `${definition.code} 实际证据范围`,
  );
  const missingCoverage = requireCanonicalSubset(
    row.missingCoverage,
    definition.requiredCoverage,
    `${definition.code} 缺失范围`,
  );
  if (!launchLedgerStatuses.includes(row.status)) {
    throw new Error(`${definition.code} 状态必须为 PASSED 或 FAILED`);
  }
  if (
    (row.status === "PASSED" &&
      (missingCoverage.length !== 0 ||
        !arraysEqual(actualEvidenceScope, definition.requiredCoverage))) ||
    (row.status === "FAILED" && missingCoverage.length === 0)
  ) {
    throw new Error(`${definition.code} 状态与实际/缺失范围不一致`);
  }
  const candidateCommit = requireText(
    row.candidateCommit,
    `${definition.code} candidateCommit`,
  );
  if (!/^[a-f0-9]{40}$/u.test(candidateCommit)) {
    throw new Error(
      `${definition.code} candidateCommit 必须为 40 位小写提交哈希`,
    );
  }
  const runId = requireText(row.runId, `${definition.code} runId`);
  if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/u.test(runId)) {
    throw new Error(`${definition.code} runId 格式非法`);
  }
  if (!isIsoDateTime(row.decidedAt)) {
    throw new Error(`${definition.code} 缺少有效判定时间`);
  }
  const evidenceRefs = validateLaunchLedgerEvidenceRefs(
    row.evidenceRefs,
    definition,
    actualEvidenceScope,
  );
  if (
    evidenceRefs.some(
      (ref) =>
        ref.sourceRunId !== runId ||
        ref.sourceCandidateCommit !== candidateCommit ||
        Date.parse(ref.sourceCapturedAt) > Date.parse(row.decidedAt) ||
        Date.parse(ref.observedAt) > Date.parse(ref.sourceCapturedAt) ||
        Date.parse(ref.observedAt) > Date.parse(row.decidedAt),
    )
  ) {
    throw new Error(
      `${definition.code} 前置证据来源未绑定同一候选、运行标识和时间窗`,
    );
  }
  return {
    code: definition.code,
    label: definition.label,
    requiredCoverage: [...definition.requiredCoverage],
    actualEvidenceScope,
    status: row.status,
    missingCoverage,
    evidenceRefs,
    candidateCommit,
    runId,
    decidedAt: row.decidedAt,
  };
}

function validateLaunchLedgerEvidenceRefs(value, definition, actualScope) {
  const refs = requireArray(value, `${definition.code} 前置证据引用`);
  const allowedKeyOrder = new Map(
    definition.requiredCoverage.map((key, index) => [key, index]),
  );
  const actualScopeSet = new Set(actualScope);
  const seen = new Set();
  let previousKeyIndex = -1;
  const normalized = refs.map((rawRef) => {
    const ref = requireRecord(rawRef, `${definition.code} 前置证据引用`);
    requireExactObjectKeys(
      ref,
      launchLedgerSchema.$defs.evidenceRef.required,
      `${definition.code} 前置证据引用`,
    );
    const coverageKey = requireText(
      ref.coverageKey,
      `${definition.code} 证据覆盖键`,
    );
    const keyIndex = allowedKeyOrder.get(coverageKey);
    if (keyIndex === undefined || !actualScopeSet.has(coverageKey)) {
      throw new Error(`${definition.code} 前置证据引用超出实际证据范围`);
    }
    if (keyIndex < previousKeyIndex) {
      throw new Error(`${definition.code} 前置证据引用未按固定范围排序`);
    }
    previousKeyIndex = keyIndex;
    const evidenceStage = requireText(
      ref.evidenceStage,
      `${definition.code} 前置证据阶段`,
    );
    if (evidenceStage === "launch-coverage") {
      throw new Error(`${definition.code} 不得引用最终审计自身作为前置证据`);
    }
    const evidencePath = requireText(
      ref.evidencePath,
      `${definition.code} 前置证据路径`,
    );
    const evidenceKey = requireText(
      ref.evidenceKey,
      `${definition.code} 前置证据键`,
    );
    const observedCode = requireText(
      ref.observedCode,
      `${definition.code} 观察码`,
    );
    if (ref.observedStatus !== "PASSED") {
      throw new Error(`${definition.code} 前置证据观察状态必须为 PASSED`);
    }
    if (!isIsoDateTime(ref.observedAt)) {
      throw new Error(`${definition.code} 前置证据缺少有效观察时间`);
    }
    const sourceRunId = requireText(
      ref.sourceRunId,
      `${definition.code} 前置证据来源运行标识`,
    );
    const sourceCandidateCommit = requireText(
      ref.sourceCandidateCommit,
      `${definition.code} 前置证据来源候选提交`,
    );
    const sourceContentSha256 = requireText(
      ref.sourceContentSha256,
      `${definition.code} 前置证据来源摘要`,
    );
    if (!/^[a-f0-9]{64}$/u.test(sourceContentSha256)) {
      throw new Error(`${definition.code} 前置证据来源摘要必须为 SHA-256`);
    }
    if (!isIsoDateTime(ref.sourceCapturedAt)) {
      throw new Error(`${definition.code} 前置证据来源捕获时间无效`);
    }
    const sourceCapturedAt = ref.sourceCapturedAt;
    const identity = [
      coverageKey,
      evidenceStage,
      evidencePath,
      evidenceKey,
      observedCode,
      sourceRunId,
      sourceCandidateCommit,
      sourceContentSha256,
      sourceCapturedAt,
    ].join("\u0000");
    if (seen.has(identity)) {
      throw new Error(`${definition.code} 前置证据引用重复`);
    }
    seen.add(identity);
    return {
      coverageKey,
      evidenceStage,
      evidencePath,
      evidenceKey,
      observedCode,
      observedStatus: "PASSED",
      observedAt: ref.observedAt,
      sourceRunId,
      sourceCandidateCommit,
      sourceContentSha256,
      sourceCapturedAt,
    };
  });
  const referencedScope = definition.requiredCoverage.filter((coverageKey) =>
    normalized.some((ref) => ref.coverageKey === coverageKey),
  );
  if (!arraysEqual(referencedScope, actualScope)) {
    throw new Error(`${definition.code} 实际证据范围缺少对应前置证据引用`);
  }
  return normalized;
}

function validateLaunchLedgerAgainstCoverage(
  ledger,
  coverage,
  sourceByEvidencePath,
) {
  const expectedBase = buildLaunchAcceptance(coverage);
  for (const [index, row] of ledger.entries()) {
    const expected = expectedBase[index];
    const expectedRefs = expected.requiredCoverage.flatMap((coverageKey) =>
      launchLedgerEvidenceRefs(coverage, coverageKey, sourceByEvidencePath),
    );
    const expectedActualScope = expected.requiredCoverage.filter(
      (coverageKey) =>
        expectedRefs.some((ref) => ref.coverageKey === coverageKey),
    );
    if (
      row.status !== expected.status ||
      !arraysEqual(row.missingCoverage, expected.missingCoverage) ||
      !arraysEqual(row.actualEvidenceScope, expectedActualScope) ||
      JSON.stringify(row.evidenceRefs) !== JSON.stringify(expectedRefs)
    ) {
      throw new Error(`${row.code} 总账判定或证据引用与覆盖矩阵不一致`);
    }
  }
}

function launchLedgerEvidenceRefs(coverage, coverageKey, sourceByEvidencePath) {
  const rows = coverage?.[coverageKey];
  if (!Array.isArray(rows)) return [];
  return rows
    .filter(
      (row) =>
        row?.status === "PASSED" &&
        row.observedStatus === "PASSED" &&
        hasText(row.evidenceStage) &&
        row.evidenceStage !== "launch-coverage" &&
        hasText(row.evidencePath) &&
        hasText(row.evidenceKey) &&
        hasText(row.observedCode) &&
        isIsoDateTime(row.observedAt),
    )
    .map((row) => {
      const source = sourceByEvidencePath?.get(row.evidencePath);
      if (!source) {
        throw new Error(`${coverageKey} 前置证据来源清单绑定缺失`);
      }
      return {
        coverageKey,
        evidenceStage: row.evidenceStage,
        evidencePath: row.evidencePath,
        evidenceKey: row.evidenceKey,
        observedCode: row.observedCode,
        observedStatus: "PASSED",
        observedAt: row.observedAt,
        sourceRunId: source.sourceRunId,
        sourceCandidateCommit: source.sourceCandidateCommit,
        sourceContentSha256: source.sourceContentSha256,
        sourceCapturedAt: source.sourceCapturedAt,
      };
    });
}

export function validateLaunchEntryEvidence(
  row,
  catalogEntry,
  requiredStrength = productEntryRequiredStrength,
) {
  const entry = requireRecord(catalogEntry, "产品入口合同");
  const entryCode = requireText(entry.entryCode, "产品入口编码");
  const evidence = requireRecord(row, `入口 ${entryCode} 证据`);
  const requiredPolicy = entryStrengthByLevel.get(requiredStrength);
  if (!requiredPolicy) {
    throw new Error(`入口 ${entryCode} 要求了未知证据强度 ${requiredStrength}`);
  }
  if (evidence.code !== entryCode) {
    throw new Error(`入口 ${entryCode} 证据对象编码不匹配`);
  }
  if (evidence.status !== "PASSED") {
    throw new Error(`入口 ${entryCode} 证据状态必须为 PASSED`);
  }
  const expectedEvidenceKey = `launchCoverage.menuEntryCoreActionRows.${entryCode}`;
  if (evidence.evidenceKey !== expectedEvidenceKey) {
    throw new Error(`入口 ${entryCode} 证据键必须为 ${expectedEvidenceKey}`);
  }
  if (!isIsoDateTime(evidence.observedAt)) {
    throw new Error(`入口 ${entryCode} 缺少有效观察时间`);
  }
  if (evidence.requiredEvidenceStrength !== requiredStrength) {
    throw new Error(
      `入口 ${entryCode} 自报要求强度 ${evidence.requiredEvidenceStrength ?? "缺失"} 与合同 ${requiredStrength} 不一致`,
    );
  }

  const claimedPolicy = entryStrengthByLevel.get(evidence.evidenceStrength);
  if (!claimedPolicy) {
    throw new Error(
      `入口 ${entryCode} 声明了未知证据强度 ${evidence.evidenceStrength ?? "缺失"}`,
    );
  }
  const capabilities = requireCanonicalCapabilities(
    evidence.verifiedCapabilities,
    entryCode,
  );
  const capabilitySet = new Set(capabilities);
  const derivedPolicy = [...entryStrengthPolicy]
    .reverse()
    .find((policy) =>
      policy.requiredCapabilities.every((capability) =>
        capabilitySet.has(capability),
      ),
    );
  if (!derivedPolicy) {
    throw new Error(`入口 ${entryCode} 证据未验证最基本的 ROUTE 能力`);
  }
  if (evidence.evidenceStrength !== derivedPolicy.level) {
    throw new Error(
      `入口 ${entryCode} 自报证据强度 ${evidence.evidenceStrength} 与能力反算 ${derivedPolicy.level} 不一致`,
    );
  }
  const derivedRank = entryStrengthByLevel.get(derivedPolicy.level).index;
  if (derivedRank < requiredPolicy.index) {
    throw new Error(
      `入口 ${entryCode} 实际证据强度 ${derivedPolicy.level} 不能满足要求 ${requiredStrength}`,
    );
  }

  const verifiedObject = requireRecord(
    evidence.verifiedObject,
    `入口 ${entryCode} 已验证对象`,
  );
  if (
    verifiedObject.entryCode !== entryCode ||
    verifiedObject.route !== entry.route
  ) {
    throw new Error(`入口 ${entryCode} 已验证对象未绑定合同路由`);
  }
  const expectedActionCodes = capabilitySet.has("CORE_ACTION")
    ? requireArray(entry.coreActions, `入口 ${entryCode} 核心动作`).map(
        (action) =>
          requireText(
            requireRecord(action, `入口 ${entryCode} 核心动作`).actionCode,
            `入口 ${entryCode} 核心动作编码`,
          ),
      )
    : [];
  requireExactStringArray(
    verifiedObject.actionCodes,
    expectedActionCodes,
    `入口 ${entryCode} 已验证核心动作`,
  );
  const hasPermissionEvidence = [
    "PERMISSION_ALLOWED",
    "PERMISSION_FORBIDDEN",
    "ORGANIZATION_SCOPE",
  ].some((capability) => capabilitySet.has(capability));
  const expectedPermissionCodes = hasPermissionEvidence
    ? requireTextArray(entry.requiredPermissions, `入口 ${entryCode} 权限合同`)
    : [];
  requireExactStringArray(
    verifiedObject.permissionCodes,
    expectedPermissionCodes,
    `入口 ${entryCode} 已验证权限`,
  );
  const expectedOrganizationScope = capabilitySet.has("ORGANIZATION_SCOPE")
    ? entry.organizationScopeMode
    : null;
  if (verifiedObject.organizationScopeMode !== expectedOrganizationScope) {
    throw new Error(`入口 ${entryCode} 已验证组织范围与合同不一致`);
  }
  const expectedSixStates = requireTextArray(
    entry.sixStates,
    `入口 ${entryCode} 六态合同`,
  ).filter((state) => capabilitySet.has(stateCapability(state)));
  requireExactStringArray(
    verifiedObject.sixStates,
    expectedSixStates,
    `入口 ${entryCode} 已验证六态`,
  );

  const uncoveredScope = fullEntryCapabilities.filter(
    (capability) => !capabilitySet.has(capability),
  );
  requireExactStringArray(
    evidence.uncoveredScope,
    uncoveredScope,
    `入口 ${entryCode} 未覆盖范围`,
  );
  const coverageBoundary = requireRecord(
    evidence.coverageBoundary,
    `入口 ${entryCode} 覆盖边界`,
  );
  const boundaryStatement = requireText(
    coverageBoundary.statement,
    `入口 ${entryCode} 覆盖边界说明`,
  );
  const expectedBoundaryMode =
    uncoveredScope.length === 0 ? "FULL_ENTRY_CONTRACT" : "LIMITED_ENTRY_SLICE";
  if (coverageBoundary.mode !== expectedBoundaryMode) {
    throw new Error(
      `入口 ${entryCode} 覆盖边界模式必须为 ${expectedBoundaryMode}`,
    );
  }
  if (
    expectedBoundaryMode === "FULL_ENTRY_CONTRACT" &&
    (!boundaryStatement.includes("完整入口合同") ||
      boundaryStatement.includes("不代表完整入口合同"))
  ) {
    throw new Error(`入口 ${entryCode} 完整证据缺少准确边界说明`);
  }
  if (
    expectedBoundaryMode === "LIMITED_ENTRY_SLICE" &&
    !boundaryStatement.includes("不代表完整入口合同")
  ) {
    throw new Error(`入口 ${entryCode} 有限证据未声明不代表完整入口合同`);
  }

  return {
    code: entryCode,
    status: "PASSED",
    evidenceKey: expectedEvidenceKey,
    observedAt: evidence.observedAt,
    requiredEvidenceStrength: requiredStrength,
    evidenceStrength: derivedPolicy.level,
    verifiedCapabilities: [...capabilities],
    verifiedObject: {
      entryCode,
      route: entry.route,
      actionCodes: [...expectedActionCodes],
      permissionCodes: [...expectedPermissionCodes],
      organizationScopeMode: expectedOrganizationScope,
      sixStates: [...expectedSixStates],
    },
    coverageBoundary: {
      mode: expectedBoundaryMode,
      statement: boundaryStatement,
    },
    uncoveredScope: [...uncoveredScope],
  };
}

function validateProductEntryEvidence(stageEvidence) {
  const browserStage = stageEvidence.find(
    (item) => item.stageId === "browser-e2e",
  );
  const rows = browserStage?.evidence?.launchCoverage?.menuEntryCoreActionRows;
  if (!Array.isArray(rows)) {
    throw new Error("产品入口证据强度行缺失");
  }
  const rowByCode = new Map();
  for (const row of rows) {
    const code = requireText(row?.code, "产品入口证据编码");
    if (rowByCode.has(code)) {
      throw new Error(`产品入口证据强度行重复：${code}`);
    }
    rowByCode.set(code, row);
  }
  if (rowByCode.size !== productEntries.length) {
    throw new Error(
      `产品入口证据强度必须逐项覆盖 ${productEntries.length} 个入口`,
    );
  }
  const rowsByCatalogOrder = productEntries.map((entry) => {
    const row = rowByCode.get(entry.entryCode);
    if (!row) {
      throw new Error(`产品入口 ${entry.entryCode} 缺少证据强度行`);
    }
    return validateLaunchEntryEvidence(
      row,
      entry,
      productEntryRequiredStrength,
    );
  });
  for (const code of rowByCode.keys()) {
    if (!productEntries.some((entry) => entry.entryCode === code)) {
      throw new Error(`产品入口证据强度包含未知入口：${code}`);
    }
  }
  return {
    schemaVersion: launchEntryEvidenceSchema["x-medkernel-contract-version"],
    schema: "scripts/release/launch-entry-evidence.schema.json",
    requiredEvidenceStrength: productEntryRequiredStrength,
    rows: rowsByCatalogOrder,
  };
}

function loadEntryStrengthPolicy(schema) {
  const expectedLevels = [
    "ROUTE_ONLY",
    "READBACK_ONLY",
    "CORE_ACTION",
    "CORE_ACTION_WITH_PERMISSION",
    "CORE_ACTION_WITH_SIX_STATE",
  ];
  const knownCapabilities = schema?.$defs?.verifiedCapability?.enum;
  const policy = schema?.["x-medkernel-strength-policy"];
  if (
    !Array.isArray(knownCapabilities) ||
    !Array.isArray(policy) ||
    policy.length !== expectedLevels.length
  ) {
    throw new Error("入口证据 schema 的强度策略无效");
  }
  const knownCapabilitySet = new Set(knownCapabilities);
  let previousCapabilities = [];
  const normalized = policy.map((item, index) => {
    if (
      item?.level !== expectedLevels[index] ||
      !Array.isArray(item.requiredCapabilities) ||
      item.requiredCapabilities.length === 0 ||
      new Set(item.requiredCapabilities).size !==
        item.requiredCapabilities.length ||
      item.requiredCapabilities.some(
        (capability) => !knownCapabilitySet.has(capability),
      ) ||
      previousCapabilities.some(
        (capability) => !item.requiredCapabilities.includes(capability),
      )
    ) {
      throw new Error(`入口证据 schema 的 ${expectedLevels[index]} 策略无效`);
    }
    previousCapabilities = [...item.requiredCapabilities];
    return Object.freeze({
      level: item.level,
      requiredCapabilities: Object.freeze([...item.requiredCapabilities]),
    });
  });
  return Object.freeze(normalized);
}

function validateProductEntryCatalog(catalog) {
  const entries = catalog?.entries;
  if (!Array.isArray(entries) || entries.length === 0) {
    throw new Error("产品入口唯一合同为空");
  }
  const codes = entries.map((entry) => entry?.entryCode);
  if (
    codes.some((code) => !hasText(code)) ||
    new Set(codes).size !== codes.length
  ) {
    throw new Error("产品入口唯一合同编码缺失或重复");
  }
  return Object.freeze(entries.map((entry) => Object.freeze(entry)));
}

function requireCanonicalCapabilities(value, entryCode) {
  const capabilities = requireTextArray(value, `入口 ${entryCode} 已验证能力`);
  if (new Set(capabilities).size !== capabilities.length) {
    throw new Error(`入口 ${entryCode} 已验证能力重复`);
  }
  const capabilitySet = new Set(capabilities);
  if (
    capabilities.some(
      (capability) => !fullEntryCapabilities.includes(capability),
    )
  ) {
    throw new Error(`入口 ${entryCode} 包含未知已验证能力`);
  }
  const canonical = fullEntryCapabilities.filter((capability) =>
    capabilitySet.has(capability),
  );
  if (!arraysEqual(capabilities, canonical)) {
    throw new Error(`入口 ${entryCode} 已验证能力未按合同顺序记录`);
  }
  return capabilities;
}

function requireExactObjectKeys(value, expectedKeys, label) {
  const actualKeys = Object.keys(value).sort();
  const requiredKeys = [...expectedKeys].sort();
  if (!arraysEqual(actualKeys, requiredKeys)) {
    throw new Error(`${label}字段必须与固定 schema 完全一致`);
  }
}

function requireCanonicalSubset(value, allowed, label) {
  const actual = requireTextArray(value, label);
  if (
    new Set(actual).size !== actual.length ||
    actual.some((item) => !allowed.includes(item))
  ) {
    throw new Error(`${label}包含重复或未知项`);
  }
  const canonical = allowed.filter((item) => actual.includes(item));
  if (!arraysEqual(actual, canonical)) {
    throw new Error(`${label}未按固定 schema 顺序记录`);
  }
  return actual;
}

function requireExactStringArray(value, expected, label) {
  const actual = requireTextArray(value, label);
  if (
    new Set(actual).size !== actual.length ||
    !arraysEqual(actual, expected)
  ) {
    throw new Error(`${label}与入口合同不一致`);
  }
  return actual;
}

function requireTextArray(value, label) {
  const array = requireArray(value, label);
  if (array.some((item) => !hasText(item))) {
    throw new Error(`${label}必须全部为非空字符串`);
  }
  return array.map((item) => item.trim());
}

function requireArray(value, label) {
  if (!Array.isArray(value)) {
    throw new Error(`${label}必须为数组`);
  }
  return value;
}

function requireRecord(value, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label}必须为对象`);
  }
  return value;
}

function arraysEqual(left, right) {
  return (
    left.length === right.length &&
    left.every((item, index) => item === right[index])
  );
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function isIsoDateTime(value) {
  return hasText(value) && Number.isFinite(Date.parse(value));
}

function sha256Json(value) {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

function stateCapability(state) {
  return `STATE_${state.toUpperCase()}`;
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
  if (
    relative === "" ||
    (!relative.startsWith("..") && !path.isAbsolute(relative))
  ) {
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

function normalizeRunId(value) {
  const runId = requireText(value, "LAUNCH_RUN_ID");
  if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/u.test(runId)) {
    throw new Error("LAUNCH_RUN_ID 格式非法");
  }
  return runId;
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label}不能为空`);
  }
  return value.trim();
}

if (fileURLToPath(import.meta.url) === path.resolve(process.argv[1] ?? "")) {
  try {
    const config = readLaunchCoverageAuditConfig(process.env);
    const evidence = buildLaunchCoverageEvidence(config);
    writeJsonAtomic(config.outputPath, evidence);
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
