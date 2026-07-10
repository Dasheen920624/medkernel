import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  existsSync,
  lstatSync,
  readFileSync,
  realpathSync,
  statSync,
} from "node:fs";
import path from "node:path";

export const DEFAULT_SOURCE_BASE_COMMIT =
  "7217504ce82e1aa119c3402e3b5d054f9369e018";

export const REQUIRED_RC_ARTIFACT_IDS = Object.freeze([
  "BACKEND_JAR",
  "FRONTEND_DIST",
  "CLI_PACKAGE",
  "MCP_PACKAGE",
  "DATABASE_MIGRATIONS",
  "ONPREM_DELIVERY",
]);

export const REQUIRED_RC_GATES = Object.freeze([
  "BACKEND_TESTS",
  "BROWSER_E2E",
  "CLI_TESTS",
  "DATABASE_GENERATOR",
  "DEPLOYMENT_CONTRACTS",
  "FORMAT_CHECK",
  "FRONTEND_VERIFY_BUILD",
  "MCP_TESTS",
  "T_GATE",
]);

export const REQUIRED_RC_DEPENDENCY_IDS = Object.freeze([
  "FRONTEND_NPM_DECLARATION",
  "FRONTEND_NPM_LOCK",
  "MAVEN_DECLARATION",
  "MAVEN_RESOLUTION_REPORT",
  "CLI_NO_EXTERNAL_DEPENDENCIES",
  "MCP_NO_EXTERNAL_DEPENDENCIES",
]);

const DEPENDENCY_DEFINITIONS = Object.freeze([
  Object.freeze({
    dependencyId: "FRONTEND_NPM_DECLARATION",
    semantics: "DECLARATION",
    path: "frontend/package.json",
  }),
  Object.freeze({
    dependencyId: "FRONTEND_NPM_LOCK",
    semantics: "LOCKFILE",
    path: "frontend/package-lock.json",
  }),
  Object.freeze({
    dependencyId: "MAVEN_DECLARATION",
    semantics: "DECLARATION",
    path: "medkernel-backend/pom.xml",
  }),
  Object.freeze({
    dependencyId: "MAVEN_RESOLUTION_REPORT",
    semantics: "RESOLVED_DEPENDENCY_REPORT",
  }),
  Object.freeze({
    dependencyId: "CLI_NO_EXTERNAL_DEPENDENCIES",
    semantics: "NO_EXTERNAL_DEPENDENCIES",
    path: "cli/package.json",
    component: "CLI",
  }),
  Object.freeze({
    dependencyId: "MCP_NO_EXTERNAL_DEPENDENCIES",
    semantics: "NO_EXTERNAL_DEPENDENCIES",
    path: "mcp-server/package.json",
    component: "MCP",
  }),
]);

const EXTERNAL_DEPENDENCY_FIELDS = Object.freeze([
  "dependencies",
  "devDependencies",
  "optionalDependencies",
  "peerDependencies",
  "bundleDependencies",
  "bundledDependencies",
]);

const FULL_COMMIT_PATTERN = /^[a-f0-9]{40}$/u;
const SHA256_PATTERN = /^[a-f0-9]{64}$/u;
const RUN_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/u;
const NON_PROMOTABLE_STATUSES = new Set(["FAILED", "UNKNOWN", "SKIPPED"]);
const RESIDUAL_PATH_PATTERN =
  /(?:^|\/)(?:target|dist|build|coverage|test-results|e2e-report|playwright-report|runtime|\.vite)(?:\/|$)|\.(?:log|out|err|pid)$/u;
const DEPENDENCY_CACHE_PATTERN = /(?:^|\/)(?:node_modules|m2repo)(?:\/|$)/u;
const RC_MANIFEST_KEYS = Object.freeze([
  "schemaVersion",
  "kind",
  "rcName",
  "status",
  "promotable",
  "sourceBaseCommit",
  "candidateCommit",
  "runId",
  "runStartedAt",
  "generatedAt",
  "dependencySnapshot",
  "gateEvidenceSetSha256",
  "gates",
  "artifacts",
]);
const DEPENDENCY_SNAPSHOT_KEYS = Object.freeze([
  "candidateCommit",
  "records",
  "setSha256",
]);
const DEPENDENCY_RECORD_KEYS = Object.freeze([
  "dependencyId",
  "semantics",
  "path",
  "size",
  "sha256",
  "candidateCommit",
]);
const GATE_RECORD_KEYS = Object.freeze([
  "gateId",
  "runId",
  "status",
  "candidateCommit",
  "evidenceStage",
  "evidenceKey",
  "observedCode",
  "observedAt",
  "evidencePath",
  "evidenceSize",
  "evidenceSha256",
]);
const ARTIFACT_SET_KEYS = Object.freeze([
  "candidateCommit",
  "files",
  "setSha256",
]);
const ARTIFACT_RECORD_KEYS = Object.freeze([
  "artifactId",
  "path",
  "size",
  "sha256",
  "candidateCommit",
]);

/**
 * 从干净候选提交、真实门禁证据和候选制品形成可独立重验的 RC 清单。
 */
export function createRcManifest(options = {}) {
  const repoRoot = path.resolve(requireText(options.repoRoot, "repoRoot"));
  const bundleRoot = validateBundleRoot(options.bundleRoot, repoRoot);
  const sourceBaseCommit = validateSourceBaseCommit(
    options.sourceBaseCommit ?? DEFAULT_SOURCE_BASE_COMMIT,
  );
  const candidateCommit = validateCommit(
    options.candidateCommit,
    "candidateCommit",
  );
  const runId = validateRunId(options.runId);
  const runStartedAt = validateIsoTimestamp(
    options.runStartedAt,
    "runStartedAt",
  );
  const generatedAt = validateIsoTimestamp(options.generatedAt, "generatedAt");
  assertRunWindow(runStartedAt, generatedAt);

  assertRepositoryLineage(repoRoot, sourceBaseCommit, candidateCommit);
  assertCleanWorkspace(repoRoot);

  const dependencySnapshot = describeDependencySnapshot({
    repoRoot,
    bundleRoot,
    mavenResolutionPath: options.mavenResolutionPath,
    candidateCommit,
  });
  const gates = describeGates({
    bundleRoot,
    gates: options.gates,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
  });
  const artifacts = describeArtifacts({
    bundleRoot,
    artifacts: options.artifacts,
    candidateCommit,
  });

  return {
    schemaVersion: "1.0.0",
    kind: "MEDKERNEL_RC_MANIFEST",
    rcName: "RC0",
    status: "PROMOTABLE",
    promotable: true,
    sourceBaseCommit,
    candidateCommit,
    runId,
    runStartedAt,
    generatedAt,
    dependencySnapshot,
    gateEvidenceSetSha256: digestRecords(gates),
    gates,
    artifacts: {
      candidateCommit,
      files: artifacts,
      setSha256: digestRecords(artifacts),
    },
  };
}

/**
 * 不依赖创建阶段内存状态，重新读取 Git、锁文件、证据与制品字节进行核验。
 */
export function verifyRcManifest(manifest, options = {}) {
  requireObject(manifest, "RC 清单");
  assertNoUnknownKeys(manifest, RC_MANIFEST_KEYS, "RC 清单");
  const repoRoot = path.resolve(requireText(options.repoRoot, "repoRoot"));
  const bundleRoot = validateBundleRoot(options.bundleRoot, repoRoot);
  if (manifest.schemaVersion !== "1.0.0") {
    throw new Error("RC 清单 schemaVersion 必须为 1.0.0");
  }
  if (manifest.rcName !== "RC0") {
    throw new Error("RC 清单 rcName 必须为 RC0");
  }
  if (
    manifest.kind !== "MEDKERNEL_RC_MANIFEST" ||
    manifest.status !== "PROMOTABLE" ||
    manifest.promotable !== true
  ) {
    throw new Error("RC 清单不是可提升状态");
  }
  const sourceBaseCommit = validateSourceBaseCommit(manifest.sourceBaseCommit);
  const candidateCommit = validateCommit(
    manifest.candidateCommit,
    "candidateCommit",
  );
  const runId = validateRunId(manifest.runId);
  const runStartedAt = validateIsoTimestamp(
    manifest.runStartedAt,
    "runStartedAt",
  );
  const generatedAt = validateIsoTimestamp(manifest.generatedAt, "generatedAt");
  assertRunWindow(runStartedAt, generatedAt);
  assertRepositoryLineage(repoRoot, sourceBaseCommit, candidateCommit);
  assertCleanWorkspace(repoRoot);

  verifyDependencySnapshot(
    manifest.dependencySnapshot,
    repoRoot,
    bundleRoot,
    candidateCommit,
  );
  verifyGates(
    manifest,
    bundleRoot,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
  );
  verifyArtifacts(manifest.artifacts, bundleRoot, candidateCommit);

  return {
    status: "VERIFIED",
    sourceBaseCommit,
    candidateCommit,
    runId,
  };
}

/**
 * 以递归字段排序输出确定性 JSON；调用者必须显式注入 generatedAt。
 */
export function serializeRcManifest(manifest) {
  requireObject(manifest, "RC 清单");
  return `${JSON.stringify(canonicalize(manifest), null, 2)}\n`;
}

function assertRepositoryLineage(repoRoot, sourceBaseCommit, candidateCommit) {
  assertGitRepository(repoRoot);
  assertCommitExists(repoRoot, sourceBaseCommit, "sourceBaseCommit");
  assertCommitExists(repoRoot, candidateCommit, "candidateCommit");
  const head = runGit(
    repoRoot,
    ["rev-parse", "HEAD"],
    "读取当前 HEAD",
  ).stdout.trim();
  if (head !== candidateCommit) {
    throw new Error(
      `当前 HEAD 与 candidateCommit 不一致：HEAD=${head}，candidateCommit=${candidateCommit}`,
    );
  }
  const ancestor = spawnSync(
    "git",
    ["merge-base", "--is-ancestor", sourceBaseCommit, candidateCommit],
    { cwd: repoRoot, encoding: "utf8", shell: false },
  );
  if (ancestor.status !== 0) {
    throw new Error(
      "sourceBaseCommit 不是 candidateCommit 的祖先，拒绝断裂的收敛来源链",
    );
  }
  if (sourceBaseCommit === candidateCommit) {
    throw new Error(
      "candidateCommit 必须晚于 sourceBaseCommit，输入锚点不得直接提升",
    );
  }
}

function assertGitRepository(repoRoot) {
  const result = spawnSync("git", ["rev-parse", "--is-inside-work-tree"], {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false,
  });
  if (result.status !== 0 || result.stdout.trim() !== "true") {
    throw new Error(`repoRoot 不是 Git 工作区：${repoRoot}`);
  }
}

function assertCommitExists(repoRoot, commit, label) {
  const result = spawnSync("git", ["cat-file", "-e", `${commit}^{commit}`], {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false,
  });
  if (result.status !== 0) {
    throw new Error(`${label} 指向的提交不存在`);
  }
}

function assertCleanWorkspace(repoRoot) {
  assertNoHiddenIndexFlags(repoRoot);
  const status = runGit(
    repoRoot,
    ["status", "--porcelain=v1", "-z", "--untracked-files=all"],
    "检查工作区状态",
  ).stdout;
  const entries = parsePorcelain(status);
  const tracked = entries
    .filter((entry) => entry.code !== "??")
    .map((entry) => entry.path)
    .sort();
  if (tracked.length > 0) {
    throw new Error(`工作区存在 tracked 修改：${tracked.join("、")}`);
  }
  const untracked = entries
    .filter((entry) => entry.code === "??")
    .map((entry) => entry.path)
    .sort();
  if (untracked.length > 0) {
    throw new Error(`工作区存在未跟踪文件：${untracked.join("、")}`);
  }

  const ignoredStatus = runGit(
    repoRoot,
    [
      "status",
      "--porcelain=v1",
      "-z",
      "--ignored=matching",
      "--untracked-files=all",
    ],
    "检查被忽略的工作区残留",
  ).stdout;
  const ignored = parsePorcelain(ignoredStatus)
    .filter((entry) => entry.code === "!!")
    .map((entry) => entry.path)
    .filter((entryPath) => !DEPENDENCY_CACHE_PATTERN.test(entryPath));
  const residual = ignored
    .filter((entryPath) => RESIDUAL_PATH_PATTERN.test(entryPath))
    .sort();
  if (residual.length > 0) {
    throw new Error(
      `工作区存在构建、测试或运行证据残留：${residual.join("、")}`,
    );
  }
  const otherIgnored = ignored
    .filter((entryPath) => !RESIDUAL_PATH_PATTERN.test(entryPath))
    .sort();
  if (otherIgnored.length > 0) {
    throw new Error(`工作区存在被忽略的未跟踪文件：${otherIgnored.join("、")}`);
  }
}

function assertNoHiddenIndexFlags(repoRoot) {
  const entries = runGit(
    repoRoot,
    ["ls-files", "-v", "-z"],
    "检查 Git 索引标志",
  )
    .stdout.split("\0")
    .filter(Boolean)
    .map((entry) => ({
      tag: entry[0],
      path: normalizeRelativePath(entry.slice(2)),
    }));
  const hidden = entries
    .filter(({ tag }) => tag === "S" || /[a-z]/u.test(tag))
    .map(({ path: entryPath }) => entryPath)
    .sort();
  if (hidden.length > 0) {
    throw new Error(`工作区存在隐藏索引标志：${hidden.join("、")}`);
  }
}

function describeGates({
  bundleRoot,
  gates: input,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
}) {
  if (!Array.isArray(input)) throw new Error("gates 必须是数组");
  const byId = new Map();
  for (const gate of input) {
    requireObject(gate, "门禁登记");
    const gateId = requireText(gate.gateId, "gateId");
    if (byId.has(gateId)) throw new Error(`门禁重复：${gateId}`);
    byId.set(gateId, gate);
  }
  const missing = REQUIRED_RC_GATES.filter((gateId) => !byId.has(gateId));
  if (missing.length > 0)
    throw new Error(`缺少必需门禁：${missing.join("、")}`);
  const unknown = [...byId.keys()].filter(
    (gateId) => !REQUIRED_RC_GATES.includes(gateId),
  );
  if (unknown.length > 0)
    throw new Error(`存在未知门禁：${unknown.sort().join("、")}`);

  return REQUIRED_RC_GATES.map((gateId) => {
    const inputGate = byId.get(gateId);
    const evidence = describeBundleFile(
      bundleRoot,
      inputGate.evidencePath,
      `门禁 ${gateId} 证据文件`,
    );
    assertDeclaredSha(
      inputGate.evidenceSha256,
      evidence.sha256,
      `门禁 ${gateId} 声明的证据摘要不一致`,
    );
    const payload = parseEvidence(evidence.absolutePath, gateId);
    const observation = validateEvidencePayload(payload, {
      gateId,
      runId,
      candidateCommit,
      runStartedAt,
      generatedAt,
    });
    return {
      gateId,
      runId,
      status: "PASSED",
      candidateCommit,
      ...observation,
      evidencePath: evidence.path,
      evidenceSize: evidence.size,
      evidenceSha256: evidence.sha256,
    };
  });
}

function describeArtifacts({ bundleRoot, artifacts: input, candidateCommit }) {
  if (!Array.isArray(input)) {
    throw new Error("候选制品集合必须是数组");
  }
  const byId = new Map();
  for (const [index, item] of input.entries()) {
    const descriptor = typeof item === "string" ? { path: item } : item;
    requireObject(descriptor, `候选制品 ${index + 1}`);
    const artifactId = requireText(
      descriptor.artifactId,
      `候选制品 ${index + 1} 的 artifactId`,
    );
    if (byId.has(artifactId)) {
      throw new Error(`候选制品类型重复：${artifactId}`);
    }
    byId.set(artifactId, descriptor);
  }
  assertRequiredArtifactIds(byId);

  const records = REQUIRED_RC_ARTIFACT_IDS.map((artifactId) => {
    const descriptor = byId.get(artifactId);
    const file = describeBundleFile(
      bundleRoot,
      descriptor.path,
      `候选制品 ${artifactId}`,
    );
    assertDeclaredSha(
      descriptor.sha256,
      file.sha256,
      `候选制品 ${file.path} 声明的摘要不一致`,
    );
    return {
      artifactId,
      path: file.path,
      size: file.size,
      sha256: file.sha256,
      candidateCommit,
    };
  });
  const duplicates = duplicateValues(records.map((record) => record.path));
  if (duplicates.length > 0)
    throw new Error(`候选制品路径重复：${duplicates.join("、")}`);
  return records;
}

function describeDependencySnapshot({
  repoRoot,
  bundleRoot,
  mavenResolutionPath,
  candidateCommit,
}) {
  const records = DEPENDENCY_DEFINITIONS.map((definition) => {
    if (definition.dependencyId === "MAVEN_RESOLUTION_REPORT") {
      return dependencyRecord(
        definition,
        describeBundleFile(
          bundleRoot,
          mavenResolutionPath,
          "Maven 本次解析报告",
          { nonBlank: true },
        ),
        candidateCommit,
      );
    }

    const file = describeFile(
      repoRoot,
      definition.path,
      `依赖记录 ${definition.dependencyId}`,
      { forceRepoRelative: true },
    );
    if (definition.semantics === "NO_EXTERNAL_DEPENDENCIES") {
      assertNoExternalDependencies(file.absolutePath, definition.component);
    }
    return dependencyRecord(definition, file, candidateCommit);
  });

  return {
    candidateCommit,
    records,
    setSha256: digestRecords(records),
  };
}

function verifyDependencySnapshot(
  snapshot,
  repoRoot,
  bundleRoot,
  candidateCommit,
) {
  requireObject(snapshot, "dependencySnapshot");
  assertNoUnknownKeys(snapshot, DEPENDENCY_SNAPSHOT_KEYS, "dependencySnapshot");
  if (snapshot.candidateCommit !== candidateCommit) {
    throw new Error("依赖快照绑定的 candidateCommit 不一致");
  }
  if (!Array.isArray(snapshot.records)) {
    throw new Error("dependencySnapshot.records 必须是数组");
  }
  const byId = new Map();
  for (const record of snapshot.records) {
    requireObject(record, "依赖快照记录");
    assertNoUnknownKeys(record, DEPENDENCY_RECORD_KEYS, "依赖快照记录");
    const dependencyId = requireText(
      record.dependencyId,
      "依赖快照记录的 dependencyId",
    );
    if (byId.has(dependencyId)) {
      throw new Error(`依赖快照记录重复：${dependencyId}`);
    }
    byId.set(dependencyId, record);
  }

  const unknown = [...byId.keys()].filter(
    (dependencyId) => !REQUIRED_RC_DEPENDENCY_IDS.includes(dependencyId),
  );
  if (unknown.length > 0) {
    throw new Error(`依赖快照包含未知记录：${unknown.sort().join("、")}`);
  }
  const missing = REQUIRED_RC_DEPENDENCY_IDS.filter(
    (dependencyId) => !byId.has(dependencyId),
  );
  if (missing.length > 0) {
    throw new Error(`依赖快照缺少必需记录：${missing.join("、")}`);
  }
  const actualOrder = snapshot.records.map((record) => record.dependencyId);
  if (
    actualOrder.some(
      (dependencyId, index) =>
        dependencyId !== REQUIRED_RC_DEPENDENCY_IDS[index],
    )
  ) {
    throw new Error("依赖快照记录顺序不一致");
  }

  const verified = DEPENDENCY_DEFINITIONS.map((definition) => {
    const expected = byId.get(definition.dependencyId);
    if (expected.semantics !== definition.semantics) {
      throw new Error(`依赖记录 ${definition.dependencyId} 的语义不一致`);
    }
    if (expected.candidateCommit !== candidateCommit) {
      throw new Error(
        `依赖记录 ${definition.dependencyId} 绑定的 candidateCommit 不一致`,
      );
    }
    if (definition.path && expected.path !== definition.path) {
      throw new Error(`依赖记录 ${definition.dependencyId} 的路径不一致`);
    }

    const current =
      definition.dependencyId === "MAVEN_RESOLUTION_REPORT"
        ? describeBundleFile(bundleRoot, expected.path, "Maven 本次解析报告", {
            manifestRelative: true,
            nonBlank: true,
          })
        : describeFile(
            repoRoot,
            definition.path,
            `依赖记录 ${definition.dependencyId}`,
            { forceRepoRelative: true },
          );
    if (definition.semantics === "NO_EXTERNAL_DEPENDENCIES") {
      assertNoExternalDependencies(current.absolutePath, definition.component);
    }
    try {
      assertFileRecord(
        expected,
        current,
        `依赖记录 ${definition.dependencyId}`,
      );
    } catch (error) {
      if (definition.dependencyId === "MAVEN_RESOLUTION_REPORT") {
        throw new Error("Maven 本次解析报告摘要漂移");
      }
      throw error;
    }
    return expected;
  });
  assertSetDigest(snapshot.setSha256, verified, "依赖快照集合摘要漂移");
}

function dependencyRecord(definition, file, candidateCommit) {
  return {
    dependencyId: definition.dependencyId,
    semantics: definition.semantics,
    path: file.path,
    size: file.size,
    sha256: file.sha256,
    candidateCommit,
  };
}

function assertNoExternalDependencies(absolutePath, component) {
  let packageManifest;
  try {
    packageManifest = JSON.parse(readFileSync(absolutePath, "utf8"));
  } catch {
    throw new Error(`${component} package.json 不是有效 JSON`);
  }
  requireObject(packageManifest, `${component} package.json`);
  const declaredSections = EXTERNAL_DEPENDENCY_FIELDS.filter((field) => {
    const value = packageManifest[field];
    if (value === undefined || value === null) return false;
    if (Array.isArray(value)) return value.length > 0;
    if (typeof value === "object") return Object.keys(value).length > 0;
    return true;
  });
  if (declaredSections.length > 0) {
    throw new Error(
      `${component} 无外部依赖合同不成立：${declaredSections.join("、")}`,
    );
  }
}

function verifyGates(
  manifest,
  bundleRoot,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
) {
  if (!Array.isArray(manifest.gates))
    throw new Error("RC 清单 gates 必须是数组");
  const byId = new Map();
  for (const gate of manifest.gates) {
    requireObject(gate, "RC 清单门禁");
    assertNoUnknownKeys(gate, GATE_RECORD_KEYS, "RC 清单门禁");
    const gateId = requireText(gate.gateId, "gateId");
    if (byId.has(gateId)) throw new Error(`RC 清单门禁重复：${gateId}`);
    byId.set(gateId, gate);
  }
  const verified = REQUIRED_RC_GATES.map((gateId) => {
    const gate = byId.get(gateId);
    if (!gate) throw new Error(`RC 清单缺少必需门禁 ${gateId}`);
    if (gate.runId !== runId)
      throw new Error(`门禁 ${gateId} 的清单运行标识不一致`);
    if (gate.candidateCommit !== candidateCommit) {
      throw new Error(`门禁 ${gateId} 的清单候选提交不一致`);
    }
    if (gate.status !== "PASSED")
      throw new Error(`门禁 ${gateId} 的清单状态未通过`);
    const current = describeBundleFile(
      bundleRoot,
      gate.evidencePath,
      `门禁 ${gateId} 证据文件`,
      { manifestRelative: true },
    );
    if (current.sha256 !== gate.evidenceSha256) {
      throw new Error(`门禁 ${gateId} 的证据摘要漂移`);
    }
    if (current.size !== gate.evidenceSize)
      throw new Error(`门禁 ${gateId} 的证据大小漂移`);
    const payload = parseEvidence(current.absolutePath, gateId);
    const observation = validateEvidencePayload(payload, {
      gateId,
      runId,
      candidateCommit,
      runStartedAt,
      generatedAt,
    });
    for (const field of [
      "evidenceStage",
      "evidenceKey",
      "observedCode",
      "observedAt",
    ]) {
      if (gate[field] !== observation[field]) {
        throw new Error(`门禁 ${gateId} 的清单 ${field} 不一致`);
      }
    }
    return gate;
  });
  if (byId.size !== REQUIRED_RC_GATES.length)
    throw new Error("RC 清单包含未知门禁");
  assertSetDigest(
    manifest.gateEvidenceSetSha256,
    verified,
    "门禁证据集合摘要漂移",
  );
}

function verifyArtifacts(artifacts, bundleRoot, candidateCommit) {
  requireObject(artifacts, "artifacts");
  assertNoUnknownKeys(artifacts, ARTIFACT_SET_KEYS, "artifacts");
  if (artifacts.candidateCommit !== candidateCommit) {
    throw new Error("候选制品集合绑定的 candidateCommit 不一致");
  }
  if (!Array.isArray(artifacts.files)) {
    throw new Error("候选制品集合必须是数组");
  }
  const byId = new Map();
  for (const record of artifacts.files) {
    requireObject(record, "候选制品记录");
    assertNoUnknownKeys(record, ARTIFACT_RECORD_KEYS, "候选制品记录");
    const artifactId = requireText(
      record.artifactId,
      "候选制品记录的 artifactId",
    );
    if (byId.has(artifactId)) {
      throw new Error(`候选制品类型重复：${artifactId}`);
    }
    byId.set(artifactId, record);
  }
  assertRequiredArtifactIds(byId);
  const ordered = REQUIRED_RC_ARTIFACT_IDS.map((artifactId) =>
    byId.get(artifactId),
  );
  const duplicates = duplicateValues(ordered.map((record) => record.path));
  if (duplicates.length > 0)
    throw new Error(`候选制品路径重复：${duplicates.join("、")}`);
  for (const record of ordered) {
    if (record.candidateCommit !== candidateCommit) {
      throw new Error(`候选制品 ${record.path} 绑定的 candidateCommit 不一致`);
    }
    const current = describeBundleFile(
      bundleRoot,
      record.path,
      `候选制品 ${record.artifactId}`,
      { manifestRelative: true },
    );
    assertFileRecord(record, current, `候选制品 ${record.path}`);
  }
  assertSetDigest(artifacts.setSha256, ordered, "候选制品集合摘要漂移");
}

function assertRequiredArtifactIds(byId) {
  const unknown = [...byId.keys()].filter(
    (artifactId) => !REQUIRED_RC_ARTIFACT_IDS.includes(artifactId),
  );
  if (unknown.length > 0) {
    throw new Error(`存在未知候选制品：${unknown.sort().join("、")}`);
  }
  const missing = REQUIRED_RC_ARTIFACT_IDS.filter(
    (artifactId) => !byId.has(artifactId),
  );
  if (missing.length > 0) {
    throw new Error(`缺少必需候选制品：${missing.join("、")}`);
  }
}

function validateEvidencePayload(
  payload,
  { gateId, runId, candidateCommit, runStartedAt, generatedAt },
) {
  requireObject(payload, `门禁 ${gateId} 证据`);
  if (payload.gateId !== gateId)
    throw new Error(`门禁 ${gateId} 的证据标识不一致`);
  if (payload.runId !== runId)
    throw new Error(`门禁 ${gateId} 的运行标识不一致`);
  if (payload.candidateCommit !== candidateCommit) {
    throw new Error(`门禁 ${gateId} 的候选提交不一致`);
  }

  const evidenceStage = requireText(
    payload.evidenceStage,
    `门禁 ${gateId} 的 evidenceStage`,
  );
  if (evidenceStage !== "CLEAN_BASELINE") {
    throw new Error(`门禁 ${gateId} 的 evidenceStage 不一致`);
  }
  const evidenceKey = requireText(
    payload.evidenceKey,
    `门禁 ${gateId} 的 evidenceKey`,
  );
  if (evidenceKey !== `rc.gates.${gateId}`) {
    throw new Error(`门禁 ${gateId} 的 evidenceKey 不一致`);
  }
  const observedCode = requireText(
    payload.observedCode,
    `门禁 ${gateId} 的 observedCode`,
  );
  if (observedCode !== gateId) {
    throw new Error(`门禁 ${gateId} 的 observedCode 不一致`);
  }
  const observedStatus = requireText(
    payload.observedStatus,
    `门禁 ${gateId} 的 observedStatus`,
  );
  if (observedStatus !== "PASSED") {
    const status = NON_PROMOTABLE_STATUSES.has(observedStatus)
      ? observedStatus
      : "非法状态";
    throw new Error(`门禁 ${gateId} 未通过：${status}`);
  }
  const observedAt = validateIsoTimestamp(
    payload.observedAt,
    `门禁 ${gateId} 的 observedAt`,
  );
  if (Date.parse(observedAt) < Date.parse(runStartedAt)) {
    throw new Error(`门禁 ${gateId} 的 observedAt 早于 runStartedAt`);
  }
  if (Date.parse(observedAt) > Date.parse(generatedAt)) {
    throw new Error(`门禁 ${gateId} 的 observedAt 晚于 generatedAt`);
  }
  return { evidenceStage, evidenceKey, observedCode, observedAt };
}

function parseEvidence(absolutePath, gateId) {
  try {
    return JSON.parse(readFileSync(absolutePath, "utf8"));
  } catch {
    throw new Error(`门禁 ${gateId} 证据不是有效 JSON`);
  }
}

function validateBundleRoot(value, repoRoot) {
  const bundleRoot = path.resolve(requireText(value, "bundleRoot"));
  if (!existsSync(bundleRoot)) throw new Error("bundleRoot 不存在");
  const stats = lstatSync(bundleRoot);
  if (stats.isSymbolicLink()) throw new Error("bundleRoot 不得是符号链接");
  if (!stats.isDirectory()) throw new Error("bundleRoot 必须是目录");
  const realBundleRoot = realpathSync(bundleRoot);
  const protectedRoots = [
    realpathSync(repoRoot),
    ...resolveGitMetadataRoots(repoRoot),
  ];
  if (
    protectedRoots.some(
      (protectedRoot) =>
        isInside(protectedRoot, realBundleRoot) ||
        isInside(realBundleRoot, protectedRoot),
    )
  ) {
    throw new Error("bundleRoot 必须位于仓库外");
  }
  return bundleRoot;
}

function resolveGitMetadataRoots(repoRoot) {
  return [
    ...new Set(
      ["--git-dir", "--git-common-dir"].map((argument) => {
        const reportedPath = runGit(
          repoRoot,
          ["rev-parse", argument],
          `读取 ${argument}`,
        ).stdout.trim();
        const absolutePath = path.isAbsolute(reportedPath)
          ? reportedPath
          : path.resolve(repoRoot, reportedPath);
        return realpathSync(absolutePath);
      }),
    ),
  ];
}

function describeBundleFile(bundleRoot, inputPath, label, options = {}) {
  const supplied = requireText(inputPath, `${label}路径`);
  if (options.manifestRelative) {
    if (path.isAbsolute(supplied)) {
      throw new Error(`${label}路径不得是绝对路径`);
    }
    const pathSegments = supplied.replace(/\\/gu, "/").split("/");
    if (pathSegments.includes("..")) {
      throw new Error(`${label}路径不得包含父级越界`);
    }
    const normalized = normalizeRelativePath(path.normalize(supplied));
    if (normalized !== supplied.replace(/\\/gu, "/") || normalized === ".") {
      throw new Error(`${label}路径必须是规范相对路径`);
    }
  }

  const absolutePath = path.isAbsolute(supplied)
    ? path.resolve(supplied)
    : path.resolve(bundleRoot, supplied);
  if (!isInside(bundleRoot, absolutePath)) {
    throw new Error(`${label}必须位于 bundleRoot 内`);
  }
  assertBundlePathHasNoSymlink(bundleRoot, absolutePath, label);
  if (!existsSync(absolutePath)) throw new Error(`${label}不存在`);
  const stats = statSync(absolutePath);
  if (!stats.isFile()) throw new Error(`${label}不是普通文件`);
  const bytes = readFileSync(absolutePath);
  if (options.nonBlank && !bytes.toString("utf8").trim()) {
    throw new Error(`${label}不能为空`);
  }
  return {
    path: normalizeRelativePath(path.relative(bundleRoot, absolutePath)),
    size: bytes.byteLength,
    sha256: sha256(bytes),
    absolutePath,
  };
}

function assertBundlePathHasNoSymlink(bundleRoot, absolutePath, label) {
  const relativePath = path.relative(bundleRoot, absolutePath);
  let currentPath = bundleRoot;
  for (const segment of relativePath.split(path.sep).filter(Boolean)) {
    currentPath = path.join(currentPath, segment);
    let stats;
    try {
      stats = lstatSync(currentPath);
    } catch (error) {
      if (error?.code === "ENOENT" || error?.code === "ENOTDIR") return;
      throw error;
    }
    if (stats.isSymbolicLink()) {
      throw new Error(`${label}路径包含符号链接`);
    }
  }
}

function describeFile(repoRoot, inputPath, label, options = {}) {
  const supplied = requireText(inputPath, `${label}路径`);
  const absolutePath = path.isAbsolute(supplied)
    ? path.resolve(supplied)
    : path.resolve(repoRoot, supplied);
  if (options.forceRepoRelative && !isInside(repoRoot, absolutePath)) {
    throw new Error(`${label}必须位于仓库内`);
  }
  assertBundlePathHasNoSymlink(repoRoot, absolutePath, label);
  if (options.forceRepoRelative) {
    assertGitIndexRegularFile(
      repoRoot,
      normalizeRelativePath(path.relative(repoRoot, absolutePath)),
      label,
    );
  }
  if (!existsSync(absolutePath)) throw new Error(`${label}不存在`);
  const stats = statSync(absolutePath);
  if (!stats.isFile()) throw new Error(`${label}不是普通文件`);
  const bytes = readFileSync(absolutePath);
  return {
    path: displayPath(repoRoot, absolutePath),
    size: bytes.byteLength,
    sha256: sha256(bytes),
    absolutePath,
  };
}

function assertGitIndexRegularFile(repoRoot, relativePath, label) {
  const entries = runGit(
    repoRoot,
    ["ls-files", "--stage", "-z", "--", relativePath],
    `检查 ${label} 的 Git 索引模式`,
  )
    .stdout.split("\0")
    .filter(Boolean);
  if (entries.length !== 1) {
    throw new Error(`${label}必须是候选提交中的唯一 tracked 普通文件`);
  }
  const match = /^(\d{6}) [a-f0-9]+ ([0-3])\t(.+)$/u.exec(entries[0]);
  if (
    match === null ||
    match[2] !== "0" ||
    normalizeRelativePath(match[3]) !== relativePath ||
    !["100644", "100755"].includes(match[1])
  ) {
    throw new Error(`${label}的 Git 索引模式必须是普通文件`);
  }
}

function assertFileRecord(expected, current, label) {
  if (!Number.isSafeInteger(expected.size) || expected.size < 0) {
    throw new Error(`${label} 的清单大小非法`);
  }
  if (!SHA256_PATTERN.test(expected.sha256 ?? "")) {
    throw new Error(`${label} 的清单摘要非法`);
  }
  if (current.sha256 !== expected.sha256) throw new Error(`${label} 摘要漂移`);
  if (current.size !== expected.size) throw new Error(`${label} 大小漂移`);
}

function assertDeclaredSha(declared, actual, message) {
  if (declared === undefined) return;
  if (!SHA256_PATTERN.test(declared) || declared !== actual)
    throw new Error(message);
}

function assertSetDigest(expected, records, message) {
  if (
    !SHA256_PATTERN.test(expected ?? "") ||
    expected !== digestRecords(records)
  ) {
    throw new Error(message);
  }
}

function digestRecords(records) {
  return sha256(Buffer.from(JSON.stringify(canonicalize(records)), "utf8"));
}

function canonicalize(value) {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value)
        .sort((left, right) => left.localeCompare(right, "en"))
        .map((key) => [key, canonicalize(value[key])]),
    );
  }
  return value;
}

function parsePorcelain(output) {
  return output
    .split("\0")
    .filter((entry) => entry.length >= 4 && entry[2] === " ")
    .map((entry) => ({
      code: entry.slice(0, 2),
      path: normalizeRelativePath(entry.slice(3)),
    }));
}

function uniqueRecordsByPath(records, label) {
  const byPath = new Map();
  for (const record of records) {
    requireObject(record, label);
    const recordPath = requireText(record.path, `${label}路径`);
    if (byPath.has(recordPath))
      throw new Error(`${label}路径重复：${recordPath}`);
    byPath.set(recordPath, record);
  }
  return byPath;
}

function duplicateValues(values) {
  const seen = new Set();
  const duplicates = new Set();
  for (const value of values) {
    if (seen.has(value)) duplicates.add(value);
    seen.add(value);
  }
  return [...duplicates].sort();
}

function assertRunWindow(runStartedAt, generatedAt) {
  if (Date.parse(runStartedAt) > Date.parse(generatedAt)) {
    throw new Error("runStartedAt 不得晚于 generatedAt");
  }
}

function validateSourceBaseCommit(value) {
  const sourceBaseCommit = validateCommit(value, "sourceBaseCommit");
  if (sourceBaseCommit !== DEFAULT_SOURCE_BASE_COMMIT) {
    throw new Error(
      `sourceBaseCommit 必须固定为 ${DEFAULT_SOURCE_BASE_COMMIT}`,
    );
  }
  return sourceBaseCommit;
}

function validateCommit(value, label) {
  const commit = requireText(value, label).toLowerCase();
  if (!FULL_COMMIT_PATTERN.test(commit)) {
    throw new Error(`${label} 必须是完整 40 位十六进制提交哈希`);
  }
  return commit;
}

function validateRunId(value) {
  const runId = requireText(value, "runId");
  if (!RUN_ID_PATTERN.test(runId)) {
    throw new Error(
      "runId 必须是 8 至 128 位且仅含字母、数字、点、下划线、冒号或连字符",
    );
  }
  return runId;
}

function validateIsoTimestamp(value, label) {
  const timestamp = requireText(value, label);
  const parsed = new Date(timestamp);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString() !== timestamp) {
    throw new Error(`${label} 必须是带毫秒的 UTC ISO-8601 时间`);
  }
  return timestamp;
}

function displayPath(repoRoot, absolutePath) {
  return isInside(repoRoot, absolutePath)
    ? normalizeRelativePath(path.relative(repoRoot, absolutePath))
    : absolutePath.replace(/\\/gu, "/");
}

function isInside(parent, child) {
  const relative = path.relative(parent, child);
  return (
    relative === "" ||
    (!relative.startsWith("..") && !path.isAbsolute(relative))
  );
}

function normalizeRelativePath(value) {
  return value.replace(/\\/gu, "/").replace(/^\.\//u, "");
}

function withoutAbsolutePath(record) {
  const { absolutePath: _absolutePath, ...portable } = record;
  return portable;
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function runGit(repoRoot, args, label) {
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false,
  });
  if (result.status !== 0) throw new Error(`${label}失败`);
  return result;
}

function assertNoUnknownKeys(value, allowedKeys, label) {
  const allowed = new Set(allowedKeys);
  const unknown = Object.keys(value)
    .filter((key) => !allowed.has(key))
    .sort((left, right) => left.localeCompare(right, "en"));
  if (unknown.length > 0) {
    throw new Error(`${label}包含未知字段：${unknown.join("、")}`);
  }
}

function requireObject(value, label) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label}必须是对象`);
  }
  return value;
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label}不能为空`);
  }
  return value.trim();
}
