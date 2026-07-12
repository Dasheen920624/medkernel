import { existsSync, readFileSync, statSync } from "node:fs";
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
const DEFERRED_ISSUES_PATH = "docs/audit/deferred-issues.md";
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
const TEST_REMEDIATION_FIELDS = Object.freeze([
  "executableTest",
  "observationEvidence",
  "observedCode",
]);
const DATA_REMEDIATION_FIELDS = Object.freeze([
  "coverageContract",
  "productionApiEvidence",
  "publicationReadback",
  "effectiveReleaseReadback",
  "consumerReadback",
  "auditReadback",
]);
const ENVIRONMENT_REMEDIATION_FIELDS = Object.freeze([
  "deferredIssueId",
  "targetResourceKind",
  "targetFactEvidence",
  "observedCode",
]);
const MEDICAL_RESOURCE_COVERAGE_CONTRACT =
  "medkernel-backend/src/main/resources/catalog/medical-resource-coverage.v1.json";
const LAUNCH_CODE_PATTERN = /^LAUNCH-(0[1-9]|1[0-5])$/u;
const GAP_ID_PATTERN = /^GAP-[A-Z0-9][A-Z0-9._-]*$/u;
const DEFERRED_ISSUE_ID_PATTERN = /^DEFER-[0-9]{3}$/u;
const EVIDENCE_KEY_PATTERN = /^[a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+$/u;
const TEST_PATH_PATTERN = /(?:Test\.java|\.(?:test|spec)\.[cm]?[jt]sx?)$/u;
const OBSERVED_CODE_PATTERN = /^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$/u;

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
  const remainingTestGapIds = gaps
    .filter((gap) => gap.classification === "TEST")
    .map((gap) => gap.gapId)
    .sort();
  const remainingDataGapIds = gaps
    .filter((gap) => gap.classification === "DATA")
    .map((gap) => gap.gapId)
    .sort();
  const remainingEnvironmentGaps = gaps.filter(
    (gap) => gap.classification === "ENVIRONMENT",
  );
  const remainingEnvironmentGapIds = remainingEnvironmentGaps
    .map((gap) => gap.gapId)
    .sort();
  const deferredIssueIds = [
    ...new Set(
      remainingEnvironmentGaps.map(
        (gap) => gap.remediationPlan.deferredIssueId,
      ),
    ),
  ].sort();

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
    testClosure: {
      evidenceKey: "launch.gap.test.closed",
      status: remainingTestGapIds.length === 0 ? "CLOSED" : "OPEN",
      remainingGapCount: remainingTestGapIds.length,
      remainingGapIds: remainingTestGapIds,
    },
    dataClosure: {
      evidenceKey: "launch.gap.data.closed",
      status: remainingDataGapIds.length === 0 ? "CLOSED" : "OPEN",
      remainingGapCount: remainingDataGapIds.length,
      remainingGapIds: remainingDataGapIds,
    },
    environmentConstraint: {
      evidenceKey: "launch.gap.environment.honest",
      status: remainingEnvironmentGapIds.length === 0 ? "CLEAR" : "OPEN",
      blocksLaunch: remainingEnvironmentGapIds.length > 0,
      remainingGapCount: remainingEnvironmentGapIds.length,
      remainingGapIds: remainingEnvironmentGapIds,
      deferredIssueIds,
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
  } else if (classification === "TEST") {
    remediationPlan = validateTestRemediationPlan(gap.remediationPlan, label);
  } else if (classification === "DATA") {
    remediationPlan = validateDataRemediationPlan(
      gap.remediationPlan,
      ownerPath,
      label,
    );
  } else if (classification === "ENVIRONMENT") {
    remediationPlan = validateEnvironmentRemediationPlan(
      gap.remediationPlan,
      ownerPath,
      summary,
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

function validateEnvironmentRemediationPlan(value, ownerPath, summary, label) {
  const planLabel = `${label} ENVIRONMENT remediationPlan`;
  if (ownerPath !== DEFERRED_ISSUES_PATH) {
    throw new Error(
      `${label} ENVIRONMENT ownerPath 必须指向当前待处理问题清单 ${DEFERRED_ISSUES_PATH}`,
    );
  }
  const plan = requireRecord(value, planLabel);
  requireExactKeys(plan, ENVIRONMENT_REMEDIATION_FIELDS, planLabel);

  const deferredIssueId = requireText(
    plan.deferredIssueId,
    `${planLabel} deferredIssueId`,
  );
  if (!DEFERRED_ISSUE_ID_PATTERN.test(deferredIssueId)) {
    throw new Error(
      `${planLabel} deferredIssueId 必须是稳定的 DEFER- 三位编号`,
    );
  }
  const fact = loadDeferredIssueFacts().get(deferredIssueId);
  if (!fact) {
    throw new Error(`${planLabel} deferredIssueId ${deferredIssueId} 未登记`);
  }
  if (summary !== fact.summary) {
    throw new Error(`${label} summary 必须与待处理事项一致`);
  }

  const targetResourceKind = requireObservedCode(
    plan.targetResourceKind,
    `${planLabel} targetResourceKind`,
  );
  const targetFactEvidence = requireEvidenceKey(
    plan.targetFactEvidence,
    `${planLabel} targetFactEvidence`,
  );
  const observedCode = requireObservedCode(
    plan.observedCode,
    `${planLabel} observedCode`,
  );
  if (targetResourceKind !== fact.targetResourceKind) {
    throw new Error(`${planLabel} targetResourceKind 与当前待处理事实不一致`);
  }
  if (targetFactEvidence !== fact.targetFactEvidence) {
    throw new Error(`${planLabel} targetFactEvidence 与当前待处理事实不一致`);
  }
  if (observedCode !== fact.observedCode) {
    throw new Error(`${planLabel} observedCode 与当前待处理事实不一致`);
  }

  return {
    deferredIssueId,
    targetResourceKind,
    targetFactEvidence,
    observedCode,
  };
}

function loadDeferredIssueFacts() {
  const content = readFileSync(
    path.resolve(REPO_ROOT, DEFERRED_ISSUES_PATH),
    "utf8",
  );
  const facts = new Map();
  for (const line of content.split(/\r?\n/u)) {
    if (!/^\|\s*DEFER-[0-9]{3}\s*\|/u.test(line)) continue;
    const columns = line
      .slice(1, line.endsWith("|") ? -1 : undefined)
      .split("|")
      .map((column) => column.trim());
    if (columns.length !== 7) {
      throw new Error("当前待处理问题清单必须使用七列机器合同");
    }
    const [
      deferredIssueId,
      targetResourceKind,
      observedCode,
      targetFactEvidence,
      summary,
      currentStatus,
      closureCondition,
    ] = columns;
    if (facts.has(deferredIssueId)) {
      throw new Error(`当前待处理问题清单存在重复 ID：${deferredIssueId}`);
    }
    facts.set(deferredIssueId, {
      targetResourceKind: requireObservedCode(
        targetResourceKind,
        `${deferredIssueId} 目标资源类型`,
      ),
      observedCode: requireObservedCode(
        observedCode,
        `${deferredIssueId} 事实观察码`,
      ),
      targetFactEvidence: requireEvidenceKey(
        targetFactEvidence,
        `${deferredIssueId} 事实证据键`,
      ),
      summary: requireText(summary, `${deferredIssueId} 事项`),
      currentStatus: requireText(currentStatus, `${deferredIssueId} 当前状态`),
      closureCondition: requireText(
        closureCondition,
        `${deferredIssueId} 关闭条件`,
      ),
    });
  }
  return facts;
}

function validateDataRemediationPlan(value, ownerPath, label) {
  const planLabel = `${label} DATA remediationPlan`;
  const plan = requireRecord(value, planLabel);
  requireExactKeys(plan, DATA_REMEDIATION_FIELDS, planLabel);

  const coverageContract = requireRepositoryRelativePath(
    plan.coverageContract,
    `${planLabel} coverageContract`,
  );
  if (
    coverageContract !== MEDICAL_RESOURCE_COVERAGE_CONTRACT ||
    coverageContract !== ownerPath
  ) {
    throw new Error(
      `${planLabel} coverageContract 必须指向唯一医疗资源覆盖矩阵 ${MEDICAL_RESOURCE_COVERAGE_CONTRACT}`,
    );
  }

  const evidence = Object.fromEntries(
    DATA_REMEDIATION_FIELDS.slice(1).map((field) => [
      field,
      requireEvidenceKey(plan[field], `${planLabel} ${field}`),
    ]),
  );
  if (
    new Set(Object.values(evidence)).size !== Object.values(evidence).length
  ) {
    throw new Error(
      `${planLabel} 生产、发布、生效、消费与审计必须使用不同证据键`,
    );
  }

  return {
    coverageContract,
    ...evidence,
  };
}

function validateTestRemediationPlan(value, label) {
  const planLabel = `${label} TEST remediationPlan`;
  const plan = requireRecord(value, planLabel);
  requireExactKeys(plan, TEST_REMEDIATION_FIELDS, planLabel);

  const executableTest = requireRepositoryRelativePath(
    plan.executableTest,
    `${planLabel} executableTest`,
  );
  if (!isTestFilePath(executableTest)) {
    throw new Error(`${planLabel} executableTest 必须指向测试文件`);
  }
  const executableTestPath = path.resolve(REPO_ROOT, executableTest);
  if (
    !existsSync(executableTestPath) ||
    !statSync(executableTestPath).isFile()
  ) {
    throw new Error(`${planLabel} executableTest 必须指向仓内现存测试文件`);
  }

  const observationEvidence = requireEvidenceKey(
    plan.observationEvidence,
    `${planLabel} observationEvidence`,
  );
  const observedCode = requireText(
    plan.observedCode,
    `${planLabel} observedCode`,
  );
  if (!OBSERVED_CODE_PATTERN.test(observedCode)) {
    throw new Error(`${planLabel} observedCode 必须是稳定的大写观察码`);
  }

  return {
    executableTest,
    observationEvidence,
    observedCode,
  };
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

function requireObservedCode(value, label) {
  const observedCode = requireText(value, label);
  if (!OBSERVED_CODE_PATTERN.test(observedCode)) {
    throw new Error(`${label} 必须是稳定的大写观察码`);
  }
  return observedCode;
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
