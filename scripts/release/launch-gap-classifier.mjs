import path from "node:path";

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

const LAUNCH_GAP_FIELDS = Object.freeze([
  "gapId",
  "launchCode",
  "evidenceKey",
  "gapKind",
  "classification",
  "summary",
  "ownerPath",
]);
const LAUNCH_CODE_PATTERN = /^LAUNCH-(0[1-9]|1[0-5])$/u;
const GAP_ID_PATTERN = /^GAP-[A-Z0-9][A-Z0-9._-]*$/u;

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

  return {
    schemaVersion: "1.0.0",
    evidenceKey: "launch.gap.classification",
    gapCount: gaps.length,
    unclassifiedCount: 0,
    classificationCounts,
    gaps,
  };
}

function validateLaunchGap(candidate, index) {
  const label = `第 ${index + 1} 项缺口`;
  const gap = requireRecord(candidate, label);
  requireExactKeys(gap, LAUNCH_GAP_FIELDS, label);

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

  return {
    gapId,
    launchCode,
    evidenceKey,
    gapKind,
    classification,
    summary,
    ownerPath,
  };
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

function requireText(value, label) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${label} 不能为空`);
  }
  if (value !== value.trim()) {
    throw new Error(`${label} 不得包含首尾空白`);
  }
  return value;
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
