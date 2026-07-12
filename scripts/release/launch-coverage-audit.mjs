#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertCompleteLaunchCoverage,
  buildLaunchAcceptance,
  buildLaunchCoverageFromStageEvidence,
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
const launchEntryEvidenceSchema = JSON.parse(
  readFileSync(LAUNCH_ENTRY_EVIDENCE_SCHEMA_PATH, "utf8"),
);
const productEntryCatalog = JSON.parse(
  readFileSync(PRODUCT_ENTRY_CATALOG_PATH, "utf8"),
);
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
    "database-migrations": path.join(
      config.evidenceRoot,
      "database-migrations.json",
    ),
    "account-bootstrap": path.join(
      config.evidenceRoot,
      "account-bootstrap.json",
    ),
    "model-provider": path.join(config.evidenceRoot, "model-provider.json"),
    "platform-baseline": path.join(
      config.evidenceRoot,
      "platform-baseline.json",
    ),
    sandbox: path.join(config.evidenceRoot, "sandbox/seed-summary.json"),
    "full-knowledge": path.join(config.evidenceRoot, "full-knowledge.json"),
    "runtime-resilience": path.join(
      config.evidenceRoot,
      "runtime-resilience.json",
    ),
    "target-environment": path.join(
      config.evidenceRoot,
      "target-environment.json",
    ),
    "browser-e2e": path.join(config.evidenceRoot, "e2e/report/results.json"),
  };
  const stageStatus = {};
  const failedStages = [];
  const stageEvidence = [];
  for (const [stage, file] of Object.entries(stageFiles)) {
    const evidence = readJson(file, stage);
    try {
      validateStageEvidence(stage, evidence);
      stageStatus[stage] = "PASSED";
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
  const manifest = readJson(config.manifestPath, "全知识演练清单");
  validateFullKnowledgeManifest(manifest);

  const coverage = buildLaunchCoverageFromStageEvidence(stageEvidence);
  const evidence = {
    schemaVersion: "1.0.0",
    status: "PASSED",
    source: config.source,
    generatedAt: now(),
    stageStatus,
    coverage,
  };
  evidence.acceptance = buildLaunchAcceptance(coverage);
  assertCompleteLaunchCoverage(evidence);
  evidence.entryEvidence = validateProductEntryEvidence(stageEvidence);
  return evidence;
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
