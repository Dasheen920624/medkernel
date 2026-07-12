import { existsSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const LAUNCH_GAP_CLASSIFICATIONS = Object.freeze([
  "IMPLEMENTATION",
  "TEST",
  "DATA",
  "ENVIRONMENT",
]);

export const LAUNCH_GAP_KINDS = Object.freeze({
  IMPLEMENTATION_ABSENT: "IMPLEMENTATION",
  EXECUTABLE_EVIDENCE_ABSENT: "TEST",
  PUBLISHED_RUNTIME_DATA_ABSENT: "DATA",
  UNCONTROLLED_TARGET_RESOURCE_ABSENT: "ENVIRONMENT",
});

const REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const LAUNCH_GAP_REQUIRED_FIELDS = Object.freeze([
  "gapId",
  "launchCode",
  "evidenceKey",
  "gapKind",
  "classification",
  "summary",
  "ownerPath",
]);
const LAUNCH_GAP_OPTIONAL_FIELDS = Object.freeze(["remediationPlan"]);
const IMPLEMENTATION_REMEDIATION_FIELDS = Object.freeze([
  "failingTest",
  "implementationPath",
  "consumerReadback",
  "auditReadback",
]);
const LAUNCH_CODE_PATTERN = /^LAUNCH-(0[1-9]|1[0-5])$/u;
const GAP_ID_PATTERN = /^GAP-[A-Z0-9][A-Z0-9._-]*$/u;
const EVIDENCE_KEY_PATTERN = /^[a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+$/u;
const TEST_PATH_PATTERN = /(?:Test\.java|\.(?:test|spec)\.[cm]?[jt]sx?)$/u;

export function classifyLaunchGaps(value) {
  if (!Array.isArray(value)) {
    throw new Error("上线缺口必须是数组");
  }

  const gapIds = new Set();
  const gapKeys = new Set();
  const classificationCounts = Object.fromEntries(
    LAUNCH_GAP_CLASSIFICATIONS.map((classification) => [classification, 0]),
  );
  const gaps = value.map((candidate, index) => {
    const gap = validateLaunchGap(candidate, index);
    if (gapIds.has(gap.gapId)) {
      throw new Error(`存在重复缺口 ID：${gap.gapId}`);
    }
    const gapKey = JSON.stringify([gap.launchCode, gap.evidenceKey]);
    if (gapKeys.has(gapKey)) {
      throw new Error(`存在重复缺口键：${gap.launchCode} / ${gap.evidenceKey}`);
    }
    gapIds.add(gap.gapId);
    gapKeys.add(gapKey);
    classificationCounts[gap.classification] += 1;
    return gap;
  });
  const remainingImplementationGapIds = gaps
    .filter((gap) => gap.classification === "IMPLEMENTATION")
    .map((gap) => gap.gapId)
    .sort();

  return {
    schemaVersion: "1.0.0",
    evidenceKey: "launch.gap.classification",
    gapCount: gaps.length,
    unclassifiedCount: 0,
    classificationCounts,
    implementationClosure: {
      evidenceKey: "launch.gap.implementation.closed",
      status: remainingImplementationGapIds.length === 0 ? "CLOSED" : "OPEN",
      remainingGapCount: remainingImplementationGapIds.length,
      remainingGapIds: remainingImplementationGapIds,
    },
    gaps,
  };
}

function validateLaunchGap(candidate, index) {
  const label = `第 ${index + 1} 项缺口`;
  const gap = requireRecord(candidate, label);
  requireObjectKeys(
    gap,
    LAUNCH_GAP_REQUIRED_FIELDS,
    LAUNCH_GAP_OPTIONAL_FIELDS,
    label,
  );

  const gapId = requireText(gap.gapId, `${label} gapId`);
  if (!GAP_ID_PATTERN.test(gapId)) {
    throw new Error(`${label} gapId 必须是稳定的 GAP- 大写标识`);
  }

  const launchCode = requireText(gap.launchCode, `${label} launchCode`);
  if (!LAUNCH_CODE_PATTERN.test(launchCode)) {
    throw new Error(`${label} launchCode 必须位于 LAUNCH-01 至 LAUNCH-15`);
  }

  const evidenceKey = requireText(gap.evidenceKey, `${label} evidenceKey`);
  const gapKind = requireText(gap.gapKind, `${label} gapKind`);
  if (!Object.hasOwn(LAUNCH_GAP_KINDS, gapKind)) {
    throw new Error(`未知缺口原因：${gapKind}`);
  }
  const expectedClassification = LAUNCH_GAP_KINDS[gapKind];

  if (typeof gap.classification !== "string") {
    throw new Error(`${label} classification 只能声明一个固定分类`);
  }
  const classification = gap.classification.trim();
  if (!LAUNCH_GAP_CLASSIFICATIONS.includes(classification)) {
    throw new Error(`未知缺口分类：${classification}`);
  }
  if (classification !== expectedClassification) {
    throw new Error(
      `${gapKind} 只能归入 ${expectedClassification}，不能归入 ${classification}`,
    );
  }

  const summary = requireText(gap.summary, `${label} summary`);
  const ownerPath = requireRepositoryRelativePath(
    gap.ownerPath,
    `${label} ownerPath`,
  );
  let remediationPlan;
  if (classification === "IMPLEMENTATION") {
    remediationPlan = validateImplementationRemediationPlan(
      gap.remediationPlan,
      ownerPath,
      label,
    );
  } else if (Object.hasOwn(gap, "remediationPlan")) {
    throw new Error(`${label} ${classification} remediationPlan 尚未定义`);
  }

  const result = {
    gapId,
    launchCode,
    evidenceKey,
    gapKind,
    classification,
    summary,
    ownerPath,
  };
  if (remediationPlan !== undefined) {
    result.remediationPlan = remediationPlan;
  }
  return result;
}

function validateImplementationRemediationPlan(value, ownerPath, label) {
  const planLabel = `${label} IMPLEMENTATION remediationPlan`;
  const plan = requireRecord(value, planLabel);
  requireExactKeys(plan, IMPLEMENTATION_REMEDIATION_FIELDS, planLabel);

  const failingTest = requireRepositoryRelativePath(
    plan.failingTest,
    `${planLabel} failingTest`,
  );
  if (!isTestFilePath(failingTest)) {
    throw new Error(`${planLabel} failingTest 必须指向测试文件`);
  }
  const failingTestPath = path.resolve(REPO_ROOT, failingTest);
  if (!existsSync(failingTestPath) || !statSync(failingTestPath).isFile()) {
    throw new Error(`${planLabel} failingTest 必须指向仓内现存测试文件`);
  }

  const implementationPath = requireRepositoryRelativePath(
    plan.implementationPath,
    `${planLabel} implementationPath`,
  );
  if (implementationPath !== ownerPath) {
    throw new Error(`${planLabel} implementationPath 必须与 ownerPath 一致`);
  }
  const consumerReadback = requireEvidenceKey(
    plan.consumerReadback,
    `${planLabel} consumerReadback`,
  );
  const auditReadback = requireEvidenceKey(
    plan.auditReadback,
    `${planLabel} auditReadback`,
  );
  if (consumerReadback === auditReadback) {
    throw new Error(`${planLabel} 消费者回读与审计回读必须是不同证据键`);
  }

  return {
    failingTest,
    implementationPath,
    consumerReadback,
    auditReadback,
  };
}

function isTestFilePath(value) {
  return (
    TEST_PATH_PATTERN.test(value) ||
    value
      .split("/")
      .some((segment) => ["test", "tests", "__tests__"].includes(segment))
  );
}

function requireRecord(value, label) {
  if (
    value === null ||
    typeof value !== "object" ||
    Array.isArray(value) ||
    Object.getPrototypeOf(value) !== Object.prototype
  ) {
    throw new Error(`${label} 必须是普通对象`);
  }
  return value;
}

function requireExactKeys(value, expectedKeys, label) {
  const actualKeys = Object.keys(value).sort();
  const canonicalKeys = [...expectedKeys].sort();
  if (
    actualKeys.length !== canonicalKeys.length ||
    actualKeys.some((key, index) => key !== canonicalKeys[index])
  ) {
    const missing = canonicalKeys.filter((key) => !actualKeys.includes(key));
    const unknown = actualKeys.filter((key) => !canonicalKeys.includes(key));
    if (missing.length > 0) {
      throw new Error(`${label} ${missing.join("、")} 不能为空`);
    }
    throw new Error(`${label} 包含未知字段：${unknown.join("、")}`);
  }
}

function requireObjectKeys(value, requiredKeys, optionalKeys, label) {
  const actualKeys = Object.keys(value).sort();
  const missing = requiredKeys.filter((key) => !actualKeys.includes(key));
  if (missing.length > 0) {
    throw new Error(`${label} ${missing.join("、")} 不能为空`);
  }
  const allowed = new Set([...requiredKeys, ...optionalKeys]);
  const unknown = actualKeys.filter((key) => !allowed.has(key));
  if (unknown.length > 0) {
    throw new Error(`${label} 包含未知字段：${unknown.join("、")}`);
  }
}

function requireText(value, label) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${label} 不能为空`);
  }
  if (value !== value.trim()) {
    throw new Error(`${label} 不得包含首尾空白`);
  }
  return value;
}

function requireEvidenceKey(value, label) {
  const evidenceKey = requireText(value, label);
  if (!EVIDENCE_KEY_PATTERN.test(evidenceKey)) {
    throw new Error(`${label} 必须是规范的点分证据键`);
  }
  return evidenceKey;
}

function requireRepositoryRelativePath(value, label) {
  const ownerPath = requireText(value, label);
  const normalized = path.posix.normalize(ownerPath);
  if (
    ownerPath.includes("\\") ||
    ownerPath.includes("\0") ||
    path.posix.isAbsolute(ownerPath) ||
    normalized !== ownerPath ||
    ownerPath === "." ||
    ownerPath === ".." ||
    ownerPath.startsWith("../") ||
    ownerPath.endsWith("/")
  ) {
    throw new Error(`${label} 必须是规范的仓库相对路径`);
  }
  return ownerPath;
}
