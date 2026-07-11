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
const RC_SCHEMA_VERSION = "2.0.0";
const GATE_EVIDENCE_KIND = "MEDKERNEL_GATE_EVIDENCE";
const DEPENDENCY_EVIDENCE_KIND = "MEDKERNEL_DEPENDENCY_BUILD_EVIDENCE";
const ARTIFACT_PROVENANCE_KIND = "MEDKERNEL_ARTIFACT_PROVENANCE";
const GATE_COMMAND_CONTRACTS = Object.freeze({
  BACKEND_TESTS: Object.freeze([
    "cd medkernel-backend && CI=true mvn -B -q -Dmaven.repo.local=<run>/m2repo -DexcludedGroups=docker,performance clean test",
  ]),
  BROWSER_E2E: Object.freeze([
    "cd frontend && CI=true npm run e2e -- --workers=1 --retries=0",
  ]),
  CLI_TESTS: Object.freeze(["cd cli && CI=true npm test"]),
  DATABASE_GENERATOR: Object.freeze([
    "cd . && CI=true node --test scripts/db/generate-migrations.test.mjs",
    "cd . && CI=true node scripts/db/generate-migrations.mjs --check",
  ]),
  DEPLOYMENT_CONTRACTS: Object.freeze([
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-deploy.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-mk-publish-package.sh",
    "cd . && CI=true bash scripts/check-shell-test-assertions.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-fresh-deploy.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-ollama-model.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-failure-recovery.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh",
  ]),
  FORMAT_CHECK: Object.freeze([
    "cd . && CI=true node --test scripts/release/rc-manifest.test.mjs scripts/release/rc-artifact-builder.test.mjs scripts/release/rc-runner.test.mjs",
    "cd . && CI=true node --check scripts/release/rc-manifest-lib.mjs",
    "cd . && CI=true node --check scripts/release/rc-manifest.mjs",
    "cd . && CI=true node --check scripts/release/rc-manifest.test.mjs",
    "cd . && CI=true node --check scripts/release/rc-artifact-builder-lib.mjs",
    "cd . && CI=true node --check scripts/release/rc-artifact-builder.mjs",
    "cd . && CI=true node --check scripts/release/rc-artifact-builder.test.mjs",
    "cd . && CI=true node --check scripts/release/rc-runner-lib.mjs",
    "cd . && CI=true node --check scripts/release/rc-runner.mjs",
    "cd . && CI=true node --check scripts/release/rc-runner.test.mjs",
    "cd frontend && CI=true npx prettier --check ../scripts/release/rc-manifest-lib.mjs ../scripts/release/rc-manifest.mjs ../scripts/release/rc-manifest.test.mjs ../scripts/release/rc-artifact-builder-lib.mjs ../scripts/release/rc-artifact-builder.mjs ../scripts/release/rc-artifact-builder.test.mjs ../scripts/release/rc-runner-lib.mjs ../scripts/release/rc-runner.mjs ../scripts/release/rc-runner.test.mjs",
    "cd . && CI=true openspec validate converge-full-launch-and-knowledge-platform --strict --no-interactive",
    "cd . && CI=true git diff --check",
  ]),
  FRONTEND_VERIFY_BUILD: Object.freeze([
    "cd frontend && CI=true npm run verify",
    "cd frontend && CI=true npm run build",
  ]),
  MCP_TESTS: Object.freeze(["cd mcp-server && CI=true npm test"]),
  T_GATE: Object.freeze([
    "cd . && CI=true node --test scripts/authenticity-guard.test.mjs",
    "cd . && CI=true node scripts/authenticity-guard.mjs --mode=all",
    "cd . && CI=true node --test scripts/config-boundary-guard.test.mjs",
    "cd . && CI=true node scripts/config-boundary-guard.mjs --mode=all",
    "cd . && CI=true node --test scripts/migration-convention-guard.test.mjs",
    "cd . && CI=true node scripts/migration-convention-guard.mjs --mode=all",
    "cd . && CI=true node --test scripts/performance-contract-guard.test.mjs",
    "cd . && CI=true bash scripts/check-comment-zh.sh --self-test",
    "cd . && CI=true bash scripts/check-comment-zh.sh --mode=full",
  ]),
});
const DEPENDENCY_COMMAND_CONTRACT = Object.freeze([
  "cd frontend && CI=true npm ci --cache <run>/npm-cache --no-audit --no-fund",
  "cd medkernel-backend && CI=true mvn -B -q -Dmaven.repo.local=<run>/m2repo dependency:tree -DoutputFile=<bundle>/dependencies/maven-resolved.txt",
]);
const ARTIFACT_SOURCE_PATHS = Object.freeze({
  BACKEND_JAR: "medkernel-backend",
  FRONTEND_DIST: "frontend",
  CLI_PACKAGE: "cli",
  MCP_PACKAGE: "mcp-server",
  DATABASE_MIGRATIONS: "medkernel-backend/src/main/resources/db",
  ONPREM_DELIVERY: "deploy/onprem",
});
const ARTIFACT_METADATA_PATHS = Object.freeze({
  BACKEND_JAR: "META-INF/medkernel-build.json",
  FRONTEND_DIST: "dist/medkernel-build.json",
  CLI_PACKAGE: "package/medkernel-build.json",
  MCP_PACKAGE: "package/medkernel-build.json",
  DATABASE_MIGRATIONS: "db/medkernel-build.json",
  ONPREM_DELIVERY: "onprem/medkernel-build.json",
});
const ARTIFACT_COMMAND_CONTRACTS = Object.freeze(
  Object.fromEntries(
    REQUIRED_RC_ARTIFACT_IDS.map((artifactId) => [
      artifactId,
      Object.freeze([
        `cd . && CI=true node scripts/release/rc-artifact-builder.mjs --artifact-id ${artifactId} --repo-root <repo> --bundle-root <bundle> --run-root <run> --run-id <run-id> --candidate-commit <candidate-commit>`,
      ]),
    ]),
  ),
);
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
  "buildEvidence",
  "resolvedInventories",
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
  "execution",
  "rawEvidence",
]);
const EXECUTION_RECORD_KEYS = Object.freeze([
  "commands",
  "exitCode",
  "startedAt",
  "finishedAt",
]);
const RAW_EVIDENCE_RECORD_KEYS = Object.freeze([
  "role",
  "path",
  "size",
  "sha256",
]);
const DEPENDENCY_BUILD_RECORD_KEYS = Object.freeze([
  "runId",
  "candidateCommit",
  "execution",
  "evidencePath",
  "evidenceSize",
  "evidenceSha256",
  "rawEvidence",
]);
const INVENTORY_RECORD_KEYS = Object.freeze([
  "ecosystem",
  "recordCount",
  "setSha256",
]);
const GATE_EVIDENCE_KEYS = Object.freeze([
  "schemaVersion",
  "kind",
  "gateId",
  "evidenceStage",
  "evidenceKey",
  "observedCode",
  "observedStatus",
  "runId",
  "candidateCommit",
  "observedAt",
  "execution",
  "rawEvidence",
]);
const DEPENDENCY_EVIDENCE_KEYS = Object.freeze([
  "schemaVersion",
  "kind",
  "runId",
  "candidateCommit",
  "execution",
  "rawEvidence",
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
  "sourcePath",
  "sourceTreeOid",
  "provenancePath",
  "provenanceSize",
  "provenanceSha256",
  "provenanceExecution",
  "provenanceRawEvidence",
  "formatMetadata",
]);
const ARTIFACT_PROVENANCE_KEYS = Object.freeze([
  "schemaVersion",
  "kind",
  "artifactId",
  "runId",
  "candidateCommit",
  "sourcePath",
  "sourceTreeOid",
  "execution",
  "subject",
  "rawEvidence",
]);
const ARTIFACT_SUBJECT_KEYS = Object.freeze(["path", "size", "sha256"]);

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
    dependencyEvidencePath: options.dependencyEvidencePath,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
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
    repoRoot,
    bundleRoot,
    artifacts: options.artifacts,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
  });

  return {
    schemaVersion: RC_SCHEMA_VERSION,
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
  if (manifest.schemaVersion !== RC_SCHEMA_VERSION) {
    throw new Error(`RC 清单 schemaVersion 必须为 ${RC_SCHEMA_VERSION}`);
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
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
  );
  verifyGates(
    manifest,
    bundleRoot,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
  );
  verifyArtifacts(
    manifest.artifacts,
    repoRoot,
    bundleRoot,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
  );

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

/**
 * 输出 RC0 证据生产器必须遵守的唯一版本化合同，避免运行器与核验器各自维护命令。
 */
export function getRcEvidenceContract() {
  return canonicalize({
    schemaVersion: RC_SCHEMA_VERSION,
    kind: "MEDKERNEL_RC_EVIDENCE_CONTRACT",
    gates: GATE_COMMAND_CONTRACTS,
    dependencyBuild: {
      commands: DEPENDENCY_COMMAND_CONTRACT,
    },
    artifacts: Object.fromEntries(
      REQUIRED_RC_ARTIFACT_IDS.map((artifactId) => [
        artifactId,
        {
          sourcePath: ARTIFACT_SOURCE_PATHS[artifactId],
          metadataArchivePath: ARTIFACT_METADATA_PATHS[artifactId],
          commands: ARTIFACT_COMMAND_CONTRACTS[artifactId],
        },
      ]),
    ),
    generators: {
      buildMetadata:
        "cd . && CI=true node scripts/release/rc-manifest.mjs build-metadata --artifact-id <artifact-id> --candidate-commit <candidate-commit> --output <staging>/medkernel-build.json",
      attestArtifact:
        "cd . && CI=true node scripts/release/rc-manifest.mjs attest-artifact --repo-root <repo> --bundle-root <bundle> --artifact-id <artifact-id> --artifact <artifact> --build-log <build-log> --run-id <run-id> --candidate-commit <candidate-commit> --started-at <started-at> --finished-at <finished-at> --output <provenance>",
    },
  });
}

/**
 * 生成应在打包前放入合同指定归档路径的候选绑定构建元数据。
 */
export function createArtifactBuildMetadata(options = {}) {
  const artifactId = validateArtifactId(options.artifactId);
  const candidateCommit = validateCommit(
    options.candidateCommit,
    "candidateCommit",
  );
  return {
    schemaVersion: "1.0.0",
    kind: "MEDKERNEL_BUILD_METADATA",
    artifactId,
    candidateCommit,
  };
}

/**
 * 从真实候选制品和构建日志生成来源证明；创建 RC 清单时仍会独立重验全部字段。
 */
export function createArtifactProvenance(options = {}) {
  const repoRoot = path.resolve(requireText(options.repoRoot, "repoRoot"));
  const bundleRoot = validateBundleRoot(options.bundleRoot, repoRoot);
  const artifactId = validateArtifactId(options.artifactId);
  const runId = validateRunId(options.runId);
  const candidateCommit = validateCommit(
    options.candidateCommit,
    "candidateCommit",
  );
  const startedAt = validateIsoTimestamp(options.startedAt, "startedAt");
  const finishedAt = validateIsoTimestamp(options.finishedAt, "finishedAt");
  if (Date.parse(finishedAt) < Date.parse(startedAt)) {
    throw new Error("finishedAt 不得早于 startedAt");
  }
  assertGitRepository(repoRoot);
  assertCommitExists(repoRoot, candidateCommit, "candidateCommit");

  const artifact = describeBundleFile(
    bundleRoot,
    options.artifactPath,
    `候选制品 ${artifactId}`,
  );
  const buildLog = describeBundleFile(
    bundleRoot,
    options.buildLogPath,
    `候选制品 ${artifactId} 构建日志`,
    { nonBlank: true },
  );
  const execution = validateExecution(
    {
      commands: ARTIFACT_COMMAND_CONTRACTS[artifactId],
      exitCode: 0,
      startedAt,
      finishedAt,
    },
    {
      label: `候选制品 ${artifactId} 来源证明`,
      requiredCommands: ARTIFACT_COMMAND_CONTRACTS[artifactId],
      runStartedAt: startedAt,
      generatedAt: finishedAt,
    },
  );
  validateExecutionLog(readFileSync(buildLog.absolutePath, "utf8"), {
    label: `候选制品 ${artifactId} 来源证明`,
    runId,
    candidateCommit,
    execution,
  });
  const sourcePath = ARTIFACT_SOURCE_PATHS[artifactId];
  const sourceTreeOid = readCandidateTreeOid(
    repoRoot,
    candidateCommit,
    sourcePath,
    artifactId,
  );
  inspectArtifact({
    repoRoot,
    artifactId,
    artifactPath: artifact.absolutePath,
    candidateCommit,
    sourcePath,
  });

  return {
    schemaVersion: RC_SCHEMA_VERSION,
    kind: ARTIFACT_PROVENANCE_KIND,
    artifactId,
    runId,
    candidateCommit,
    sourcePath,
    sourceTreeOid,
    execution,
    subject: {
      path: artifact.path,
      size: artifact.size,
      sha256: artifact.sha256,
    },
    rawEvidence: [{ role: "BUILD_LOG", path: buildLog.path }],
  };
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
    const execution = validateExecution(payload.execution, {
      label: `门禁 ${gateId}`,
      requiredCommands: GATE_COMMAND_CONTRACTS[gateId],
      runStartedAt,
      generatedAt,
      observedAt: observation.observedAt,
    });
    const rawEvidence = describeRawEvidence({
      bundleRoot,
      descriptors: payload.rawEvidence,
      label: `门禁 ${gateId} 原始证据`,
    });
    validateGateEvidenceStructure({
      gateId,
      rawEvidence,
      execution,
      bundleRoot,
      runId,
      candidateCommit,
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
      execution,
      rawEvidence,
    };
  });
}

function describeRawEvidence({ bundleRoot, descriptors, label }) {
  if (!Array.isArray(descriptors) || descriptors.length === 0) {
    throw new Error(`${label}缺失`);
  }
  const records = descriptors.map((descriptor, index) => {
    requireObject(descriptor, `${label} ${index + 1}`);
    assertNoUnknownKeys(
      descriptor,
      RAW_EVIDENCE_RECORD_KEYS,
      `${label} ${index + 1}`,
    );
    const role = requireText(descriptor.role, `${label} ${index + 1} 的 role`);
    const file = describeBundleFile(
      bundleRoot,
      descriptor.path,
      `${label} ${index + 1}`,
      { manifestRelative: true, nonBlank: true },
    );
    assertDeclaredSha(
      descriptor.sha256,
      file.sha256,
      `${label} ${file.path} 声明的摘要不一致`,
    );
    return {
      role,
      path: file.path,
      size: file.size,
      sha256: file.sha256,
    };
  });
  const duplicatePaths = duplicateValues(records.map(({ path: item }) => item));
  if (duplicatePaths.length > 0) {
    throw new Error(`${label}路径重复：${duplicatePaths.join("、")}`);
  }
  return records;
}

function describeArtifacts({
  repoRoot,
  bundleRoot,
  artifacts: input,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
}) {
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
    const provenance = describeArtifactProvenance({
      repoRoot,
      bundleRoot,
      evidencePath: descriptor.provenancePath,
      artifactId,
      artifactFile: file,
      runId,
      candidateCommit,
      runStartedAt,
      generatedAt,
    });
    const formatMetadata = inspectArtifact({
      repoRoot,
      artifactId,
      artifactPath: file.absolutePath,
      candidateCommit,
      sourcePath: provenance.sourcePath,
    });
    return {
      artifactId,
      path: file.path,
      size: file.size,
      sha256: file.sha256,
      candidateCommit,
      sourcePath: provenance.sourcePath,
      sourceTreeOid: provenance.sourceTreeOid,
      provenancePath: provenance.evidencePath,
      provenanceSize: provenance.evidenceSize,
      provenanceSha256: provenance.evidenceSha256,
      provenanceExecution: provenance.execution,
      provenanceRawEvidence: provenance.rawEvidence,
      formatMetadata,
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
  dependencyEvidencePath,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
}) {
  const buildEvidence = describeDependencyBuildEvidence({
    bundleRoot,
    evidencePath: dependencyEvidencePath,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
  });
  const mavenRawEvidence = buildEvidence.rawEvidence.find(
    ({ role }) => role === "MAVEN_RESOLUTION_REPORT",
  );
  const records = DEPENDENCY_DEFINITIONS.map((definition) => {
    if (definition.dependencyId === "MAVEN_RESOLUTION_REPORT") {
      const record = dependencyRecord(
        definition,
        describeBundleFile(
          bundleRoot,
          mavenResolutionPath,
          "Maven 本次解析报告",
          { nonBlank: true },
        ),
        candidateCommit,
      );
      if (record.path !== mavenRawEvidence.path) {
        throw new Error("依赖重建证据的 Maven 解析报告路径不一致");
      }
      return record;
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

  const resolvedInventories = describeResolvedInventories(
    repoRoot,
    path.join(bundleRoot, mavenRawEvidence.path),
  );

  return {
    candidateCommit,
    records,
    buildEvidence,
    resolvedInventories,
    setSha256: digestRecords([
      ...records,
      buildEvidence,
      ...resolvedInventories,
    ]),
  };
}

function verifyDependencySnapshot(
  snapshot,
  repoRoot,
  bundleRoot,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
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
  const currentBuildEvidence = describeDependencyBuildEvidence({
    bundleRoot,
    evidencePath: snapshot.buildEvidence?.evidencePath,
    runId,
    candidateCommit,
    runStartedAt,
    generatedAt,
    manifestRelative: true,
  });
  verifyDependencyBuildRecord(snapshot.buildEvidence, currentBuildEvidence);
  const mavenRawEvidence = currentBuildEvidence.rawEvidence.find(
    ({ role }) => role === "MAVEN_RESOLUTION_REPORT",
  );
  const currentInventories = describeResolvedInventories(
    repoRoot,
    path.join(bundleRoot, mavenRawEvidence.path),
  );
  verifyInventories(snapshot.resolvedInventories, currentInventories);
  assertSetDigest(
    snapshot.setSha256,
    [...verified, snapshot.buildEvidence, ...snapshot.resolvedInventories],
    "依赖快照集合摘要漂移",
  );
}

function describeDependencyBuildEvidence({
  bundleRoot,
  evidencePath,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
  manifestRelative = false,
}) {
  const evidence = describeBundleFile(
    bundleRoot,
    evidencePath,
    "依赖重建证据文件",
    { manifestRelative, nonBlank: true },
  );
  const payload = parseJsonEvidence(evidence.absolutePath, "依赖重建证据");
  assertNoUnknownKeys(payload, DEPENDENCY_EVIDENCE_KEYS, "依赖重建证据");
  if (payload.schemaVersion !== RC_SCHEMA_VERSION) {
    throw new Error(`依赖重建证据 schemaVersion 必须为 ${RC_SCHEMA_VERSION}`);
  }
  if (payload.kind !== DEPENDENCY_EVIDENCE_KIND) {
    throw new Error("依赖重建证据 kind 不一致");
  }
  if (payload.runId !== runId) throw new Error("依赖重建运行标识不一致");
  if (payload.candidateCommit !== candidateCommit) {
    throw new Error("依赖重建候选提交不一致");
  }
  const execution = validateExecution(payload.execution, {
    label: "依赖重建",
    requiredCommands: DEPENDENCY_COMMAND_CONTRACT,
    runStartedAt,
    generatedAt,
  });
  const rawEvidence = describeRawEvidence({
    bundleRoot,
    descriptors: payload.rawEvidence,
    label: "依赖重建原始证据",
  });
  const byRole = groupRecordsByRole(rawEvidence);
  const installLog = requireSingleRole(byRole, "INSTALL_LOG", "依赖重建");
  requireSingleRole(byRole, "MAVEN_RESOLUTION_REPORT", "依赖重建");
  if (rawEvidence.length !== 2) {
    throw new Error("依赖重建只允许安装日志和 Maven 解析报告两类原始证据");
  }
  const logText = readFileSync(path.join(bundleRoot, installLog.path), "utf8");
  validateExecutionLog(logText, {
    label: "依赖重建",
    runId,
    candidateCommit,
    execution,
  });
  if (!/(?:added|安装)\s+\d+\s+(?:package|个包)/iu.test(logText)) {
    throw new Error("依赖重建安装日志缺少 npm ci 安装计数");
  }
  if (!/mavenDependencies=\d+/u.test(logText)) {
    throw new Error("依赖重建安装日志缺少 Maven 解析计数");
  }
  return {
    runId,
    candidateCommit,
    execution,
    evidencePath: evidence.path,
    evidenceSize: evidence.size,
    evidenceSha256: evidence.sha256,
    rawEvidence,
  };
}

function verifyDependencyBuildRecord(expected, current) {
  requireObject(expected, "依赖重建清单记录");
  assertNoUnknownKeys(
    expected,
    DEPENDENCY_BUILD_RECORD_KEYS,
    "依赖重建清单记录",
  );
  if (!sameCanonical(expected, current)) {
    throw new Error("依赖重建证据或执行合同漂移");
  }
}

function describeResolvedInventories(repoRoot, mavenResolutionPath) {
  const npmLockPath = path.join(repoRoot, "frontend/package-lock.json");
  const npmLock = parseJsonEvidence(npmLockPath, "前端 package-lock.json");
  if (npmLock.lockfileVersion !== 3 || !npmLock.packages) {
    throw new Error("前端 package-lock.json 必须是含 packages 的 v3 锁文件");
  }
  requireObject(npmLock.packages, "前端 package-lock.json packages");
  const npmRecords = Object.entries(npmLock.packages)
    .filter(([locator]) => locator !== "")
    .map(([locator, descriptor]) => {
      requireObject(descriptor, `npm 锁记录 ${locator}`);
      const version = requireText(
        descriptor.version,
        `npm 锁记录 ${locator} version`,
      );
      const name =
        typeof descriptor.name === "string" && descriptor.name.trim()
          ? descriptor.name.trim()
          : locator.replace(/^.*node_modules\//u, "");
      return {
        locator: normalizeRelativePath(locator),
        name,
        version,
        resolved: descriptor.resolved ?? null,
        integrity: descriptor.integrity ?? null,
      };
    })
    .sort((left, right) => left.locator.localeCompare(right.locator));
  if (npmRecords.length === 0) throw new Error("npm 可重算解析清单不能为空");

  const mavenRecords = readFileSync(mavenResolutionPath, "utf8")
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.replace(/^[|+\\\-\s]+/u, ""))
    .map((coordinate) => {
      if (
        !/^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+){1,3}$/u.test(
          coordinate,
        )
      ) {
        throw new Error(`Maven 解析清单包含非法坐标：${coordinate}`);
      }
      return coordinate;
    })
    .sort();
  if (mavenRecords.length === 0)
    throw new Error("Maven 可重算解析清单不能为空");

  return [
    {
      ecosystem: "NPM",
      recordCount: npmRecords.length,
      setSha256: digestRecords(npmRecords),
    },
    {
      ecosystem: "MAVEN",
      recordCount: mavenRecords.length,
      setSha256: digestRecords(mavenRecords),
    },
  ];
}

function verifyInventories(expected, current) {
  if (!Array.isArray(expected) || expected.length !== current.length) {
    throw new Error("依赖可重算解析清单缺失");
  }
  for (const record of expected) {
    requireObject(record, "依赖解析清单记录");
    assertNoUnknownKeys(record, INVENTORY_RECORD_KEYS, "依赖解析清单记录");
  }
  if (!sameCanonical(expected, current)) {
    throw new Error("依赖可重算解析清单漂移");
  }
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
    const execution = validateExecution(payload.execution, {
      label: `门禁 ${gateId}`,
      requiredCommands: GATE_COMMAND_CONTRACTS[gateId],
      runStartedAt,
      generatedAt,
      observedAt: observation.observedAt,
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
    if (!sameCanonical(gate.execution, execution)) {
      throw new Error(`门禁 ${gateId} 的清单执行合同不一致`);
    }
    const describedRawEvidence = describeRawEvidence({
      bundleRoot,
      descriptors: payload.rawEvidence,
      label: `门禁 ${gateId} 原始证据`,
    });
    if (!sameCanonical(gate.rawEvidence, describedRawEvidence)) {
      throw new Error(`门禁 ${gateId} 的清单原始证据集合不一致`);
    }
    verifyRawEvidence(gate.rawEvidence, bundleRoot, `门禁 ${gateId} 原始证据`);
    validateGateEvidenceStructure({
      gateId,
      rawEvidence: describedRawEvidence,
      execution,
      bundleRoot,
      runId,
      candidateCommit,
    });
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

function verifyRawEvidence(records, bundleRoot, label) {
  if (!Array.isArray(records) || records.length === 0) {
    throw new Error(`${label}缺失`);
  }
  for (const [index, record] of records.entries()) {
    requireObject(record, `${label} ${index + 1}`);
    assertNoUnknownKeys(
      record,
      RAW_EVIDENCE_RECORD_KEYS,
      `${label} ${index + 1}`,
    );
    requireText(record.role, `${label}角色`);
    const current = describeBundleFile(
      bundleRoot,
      record.path,
      `${label} ${index + 1}`,
      { manifestRelative: true, nonBlank: true },
    );
    assertFileRecord(record, current, `${label} ${record.path}`);
  }
}

function describeArtifactProvenance({
  repoRoot,
  bundleRoot,
  evidencePath,
  artifactId,
  artifactFile,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
  manifestRelative = false,
}) {
  const evidence = describeBundleFile(
    bundleRoot,
    evidencePath,
    `候选制品 ${artifactId} 来源证明文件`,
    { manifestRelative, nonBlank: true },
  );
  const payload = parseJsonEvidence(
    evidence.absolutePath,
    `候选制品 ${artifactId} 来源证明`,
  );
  assertNoUnknownKeys(
    payload,
    ARTIFACT_PROVENANCE_KEYS,
    `候选制品 ${artifactId} 来源证明`,
  );
  if (payload.schemaVersion !== RC_SCHEMA_VERSION) {
    throw new Error(
      `候选制品 ${artifactId} 来源证明 schemaVersion 必须为 ${RC_SCHEMA_VERSION}`,
    );
  }
  if (payload.kind !== ARTIFACT_PROVENANCE_KIND) {
    throw new Error(`候选制品 ${artifactId} 来源证明 kind 不一致`);
  }
  if (payload.artifactId !== artifactId) {
    throw new Error(`候选制品 ${artifactId} 来源证明类型不一致`);
  }
  if (payload.runId !== runId) {
    throw new Error(`候选制品 ${artifactId} 来源证明运行标识不一致`);
  }
  if (payload.candidateCommit !== candidateCommit) {
    throw new Error(`候选制品 ${artifactId} 来源证明候选提交不一致`);
  }
  const sourcePath = requireText(
    payload.sourcePath,
    `候选制品 ${artifactId} 来源证明 sourcePath`,
  );
  if (sourcePath !== ARTIFACT_SOURCE_PATHS[artifactId]) {
    throw new Error(`候选制品 ${artifactId} 来源路径不符合合同`);
  }
  const sourceTreeOid = readCandidateTreeOid(
    repoRoot,
    candidateCommit,
    sourcePath,
    artifactId,
  );
  if (payload.sourceTreeOid !== sourceTreeOid) {
    throw new Error(`候选制品 ${artifactId} 来源树摘要不一致`);
  }
  requireObject(payload.subject, `候选制品 ${artifactId} 来源证明 subject`);
  assertNoUnknownKeys(
    payload.subject,
    ARTIFACT_SUBJECT_KEYS,
    `候选制品 ${artifactId} 来源证明 subject`,
  );
  if (payload.subject.path !== artifactFile.path) {
    throw new Error(`候选制品 ${artifactId} 来源证明 subject 路径不一致`);
  }
  assertFileRecord(
    payload.subject,
    artifactFile,
    `候选制品 ${artifactId} 来源证明 subject`,
  );
  const execution = validateExecution(payload.execution, {
    label: `候选制品 ${artifactId} 来源证明`,
    requiredCommands: ARTIFACT_COMMAND_CONTRACTS[artifactId],
    runStartedAt,
    generatedAt,
  });
  const rawEvidence = describeRawEvidence({
    bundleRoot,
    descriptors: payload.rawEvidence,
    label: `候选制品 ${artifactId} 来源证明原始证据`,
  });
  const byRole = groupRecordsByRole(rawEvidence);
  const buildLog = requireSingleRole(
    byRole,
    "BUILD_LOG",
    `候选制品 ${artifactId} 来源证明`,
  );
  if (rawEvidence.length !== 1) {
    throw new Error(`候选制品 ${artifactId} 来源证明只允许一个 BUILD_LOG`);
  }
  validateExecutionLog(
    readFileSync(path.join(bundleRoot, buildLog.path), "utf8"),
    {
      label: `候选制品 ${artifactId} 来源证明`,
      runId,
      candidateCommit,
      execution,
    },
  );
  return {
    sourcePath,
    sourceTreeOid,
    evidencePath: evidence.path,
    evidenceSize: evidence.size,
    evidenceSha256: evidence.sha256,
    execution,
    rawEvidence,
  };
}

function readCandidateTreeOid(
  repoRoot,
  candidateCommit,
  sourcePath,
  artifactId,
) {
  const result = spawnSync(
    "git",
    ["rev-parse", `${candidateCommit}:${sourcePath}`],
    { cwd: repoRoot, encoding: "utf8", shell: false },
  );
  const oid = result.stdout?.trim();
  if (result.status !== 0 || !/^[a-f0-9]{40,64}$/u.test(oid ?? "")) {
    throw new Error(`候选制品 ${artifactId} 来源路径不在候选提交中`);
  }
  return oid;
}

function inspectArtifact({
  repoRoot,
  artifactId,
  artifactPath,
  candidateCommit,
  sourcePath,
}) {
  const bytes = readFileSync(artifactPath);
  if (artifactId === "BACKEND_JAR") {
    if (bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
      throw new Error(`候选制品 ${artifactId} 不是有效 JAR：格式非法`);
    }
    const entries = listJarEntries(artifactPath, artifactId);
    for (const required of [
      "META-INF/MANIFEST.MF",
      "META-INF/medkernel-build.json",
      "BOOT-INF/classes/com/medkernel/MedKernelApplication.class",
    ]) {
      if (!entries.includes(required)) {
        throw new Error(`候选制品 ${artifactId} 缺少必需条目 ${required}`);
      }
    }
    const manifest = extractJarText(
      artifactPath,
      "META-INF/MANIFEST.MF",
      artifactId,
    );
    const mainClass = /^Main-Class:\s*(.+)$/mu.exec(manifest)?.[1]?.trim();
    const startClass = /^Start-Class:\s*(.+)$/mu.exec(manifest)?.[1]?.trim();
    if (
      mainClass !== "org.springframework.boot.loader.launch.JarLauncher" ||
      startClass !== "com.medkernel.MedKernelApplication"
    ) {
      throw new Error(`候选制品 ${artifactId} Spring Boot 元数据非法`);
    }
    validateEmbeddedBuildMetadata(
      extractJarText(artifactPath, "META-INF/medkernel-build.json", artifactId),
      artifactId,
      candidateCommit,
    );
    return {
      format: "JAR",
      entryCount: entries.length,
      mainClass,
      startClass,
      embeddedCandidateCommit: candidateCommit,
    };
  }

  if (bytes.length < 2 || bytes[0] !== 0x1f || bytes[1] !== 0x8b) {
    throw new Error(`候选制品 ${artifactId} 不是有效 tar.gz：格式非法`);
  }
  const entries = listTarEntries(artifactPath, artifactId);
  const contract = artifactTarContract(artifactId);
  for (const required of contract.requiredEntries) {
    if (!entries.includes(required)) {
      throw new Error(`候选制品 ${artifactId} 缺少必需条目 ${required}`);
    }
  }
  validateEmbeddedBuildMetadata(
    extractTarBytes(artifactPath, contract.metadataPath, artifactId).toString(
      "utf8",
    ),
    artifactId,
    candidateCommit,
  );
  let sourceFilesVerified = 0;
  let frontendEntryPoints = 0;
  if (artifactId === "FRONTEND_DIST") {
    frontendEntryPoints = validateFrontendEntryPoints(
      extractTarBytes(artifactPath, "dist/index.html", artifactId).toString(
        "utf8",
      ),
      entries,
    );
  } else if (["CLI_PACKAGE", "MCP_PACKAGE"].includes(artifactId)) {
    sourceFilesVerified = assertArchiveMatchesCandidateTree({
      repoRoot,
      artifactPath,
      entries,
      artifactId,
      candidateCommit,
      sourcePath,
      archiveRoot: "package",
      metadataPath: contract.metadataPath,
    });
    const packageManifest = parseJsonEvidenceBuffer(
      extractTarBytes(artifactPath, "package/package.json", artifactId),
      `候选制品 ${artifactId} package.json`,
    );
    const expectedName =
      artifactId === "CLI_PACKAGE" ? "@medkernel/cli" : "@medkernel/mcp-server";
    if (packageManifest.name !== expectedName || !packageManifest.bin) {
      throw new Error(`候选制品 ${artifactId} package.json 元数据非法`);
    }
  } else if (["DATABASE_MIGRATIONS", "ONPREM_DELIVERY"].includes(artifactId)) {
    sourceFilesVerified = assertArchiveMatchesCandidateTree({
      repoRoot,
      artifactPath,
      entries,
      artifactId,
      candidateCommit,
      sourcePath,
      archiveRoot: contract.archiveRoot,
      metadataPath: contract.metadataPath,
    });
  }
  return {
    format: "TAR_GZIP",
    entryCount: entries.length,
    root: contract.archiveRoot,
    sourceFilesVerified,
    ...(artifactId === "FRONTEND_DIST" ? { frontendEntryPoints } : {}),
    embeddedCandidateCommit: candidateCommit,
  };
}

function validateFrontendEntryPoints(indexHtml, entries) {
  const sourceValues = [
    ...indexHtml.matchAll(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*>/giu),
  ].map((match) => match[1]);
  const entryPoints = sourceValues
    .filter((value) => !/^(?:data:|https?:|\/\/)/iu.test(value))
    .map((value) => value.split(/[?#]/u, 1)[0])
    .filter((value) => /\.m?js$/iu.test(value))
    .map((value) => {
      const withoutRoot = value.replace(/^\/+|^\.\//gu, "");
      const archivePath = path.posix.normalize(`dist/${withoutRoot}`);
      if (!archivePath.startsWith("dist/") || archivePath.includes("../")) {
        throw new Error("候选制品 FRONTEND_DIST 的入口脚本路径越界");
      }
      return archivePath;
    });
  if (entryPoints.length === 0) {
    throw new Error("候选制品 FRONTEND_DIST 缺少 index.html 本地模块入口");
  }
  for (const entryPoint of entryPoints) {
    if (!entries.includes(entryPoint)) {
      throw new Error(
        `候选制品 FRONTEND_DIST 缺少 index.html 引用入口 ${entryPoint}`,
      );
    }
  }
  return new Set(entryPoints).size;
}

function artifactTarContract(artifactId) {
  const contracts = {
    FRONTEND_DIST: {
      archiveRoot: "dist",
      metadataPath: "dist/medkernel-build.json",
      requiredEntries: ["dist/index.html", "dist/medkernel-build.json"],
    },
    CLI_PACKAGE: {
      archiveRoot: "package",
      metadataPath: "package/medkernel-build.json",
      requiredEntries: [
        "package/package.json",
        "package/src/cli.mjs",
        "package/medkernel-build.json",
      ],
    },
    MCP_PACKAGE: {
      archiveRoot: "package",
      metadataPath: "package/medkernel-build.json",
      requiredEntries: [
        "package/package.json",
        "package/src/server.mjs",
        "package/medkernel-build.json",
      ],
    },
    DATABASE_MIGRATIONS: {
      archiveRoot: "db",
      metadataPath: "db/medkernel-build.json",
      requiredEntries: [
        "db/schema/medkernel.schema.json",
        "db/migration/h2/V1__baseline.sql",
        "db/migration/postgres/V1__baseline.sql",
        "db/migration/oracle/V1__baseline.sql",
        "db/migration/dm/V1__baseline.sql",
        "db/migration/kingbase/V1__baseline.sql",
        "db/medkernel-build.json",
      ],
    },
    ONPREM_DELIVERY: {
      archiveRoot: "onprem",
      metadataPath: "onprem/medkernel-build.json",
      requiredEntries: [
        "onprem/README.md",
        "onprem/medkernel-deploy.sh",
        "onprem/medkernel-build.json",
      ],
    },
  };
  const contract = contracts[artifactId];
  if (!contract) throw new Error(`候选制品类型不支持格式核验：${artifactId}`);
  return contract;
}

function listJarEntries(artifactPath, artifactId) {
  const result = spawnSync("jar", ["tf", artifactPath], {
    encoding: "utf8",
    shell: false,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(`候选制品 ${artifactId} 不是有效 JAR`);
  }
  return validateArchiveEntries(result.stdout.split(/\r?\n/u), artifactId);
}

function listTarEntries(artifactPath, artifactId) {
  const listing = spawnSync("tar", ["-tzf", artifactPath], {
    encoding: "utf8",
    shell: false,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (listing.status !== 0) {
    throw new Error(`候选制品 ${artifactId} 不是有效 tar.gz`);
  }
  const verbose = spawnSync("tar", ["-tvzf", artifactPath], {
    encoding: "utf8",
    shell: false,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (
    verbose.status !== 0 ||
    verbose.stdout.split(/\r?\n/u).some((line) => /^[lh]/u.test(line))
  ) {
    throw new Error(`候选制品 ${artifactId} 包含链接条目`);
  }
  return validateArchiveEntries(listing.stdout.split(/\r?\n/u), artifactId);
}

function validateArchiveEntries(input, artifactId) {
  const entries = input
    .filter(Boolean)
    .map((entry) => entry.replace(/\/$/u, ""))
    .filter(Boolean);
  if (entries.length < 3) throw new Error(`候选制品 ${artifactId} 是空壳制品`);
  const duplicate = duplicateValues(entries);
  if (duplicate.length > 0) {
    throw new Error(`候选制品 ${artifactId} 条目重复：${duplicate.join("、")}`);
  }
  for (const entry of entries) {
    if (
      path.posix.isAbsolute(entry) ||
      entry.split("/").includes("..") ||
      entry.startsWith("./") ||
      entry.split("/").some((segment) => segment.startsWith("._"))
    ) {
      throw new Error(`候选制品 ${artifactId} 包含不安全条目：${entry}`);
    }
  }
  return entries;
}

function extractJarText(artifactPath, entry, artifactId) {
  const result = spawnSync("unzip", ["-p", artifactPath, entry], {
    encoding: "utf8",
    shell: false,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.status !== 0 || !result.stdout) {
    throw new Error(`候选制品 ${artifactId} 无法读取 ${entry}`);
  }
  return result.stdout;
}

function extractTarBytes(artifactPath, entry, artifactId) {
  const result = spawnSync("tar", ["-xOzf", artifactPath, entry], {
    encoding: null,
    shell: false,
    maxBuffer: 256 * 1024 * 1024,
  });
  if (result.status !== 0 || !result.stdout?.length) {
    throw new Error(`候选制品 ${artifactId} 无法读取 ${entry}`);
  }
  return result.stdout;
}

function validateEmbeddedBuildMetadata(text, artifactId, candidateCommit) {
  let metadata;
  try {
    metadata = JSON.parse(text);
  } catch {
    throw new Error(`候选制品 ${artifactId} 构建元数据不是有效 JSON`);
  }
  requireObject(metadata, `候选制品 ${artifactId} 构建元数据`);
  assertNoUnknownKeys(
    metadata,
    ["schemaVersion", "kind", "artifactId", "candidateCommit"],
    `候选制品 ${artifactId} 构建元数据`,
  );
  if (
    metadata.schemaVersion !== "1.0.0" ||
    metadata.kind !== "MEDKERNEL_BUILD_METADATA" ||
    metadata.artifactId !== artifactId ||
    metadata.candidateCommit !== candidateCommit
  ) {
    throw new Error(`候选制品 ${artifactId} 构建元数据候选提交或类型不一致`);
  }
}

function assertArchiveMatchesCandidateTree({
  repoRoot,
  artifactPath,
  entries,
  artifactId,
  candidateCommit,
  sourcePath,
  archiveRoot,
  metadataPath,
}) {
  const sourceFiles = runGit(
    repoRoot,
    ["ls-tree", "-r", "--name-only", candidateCommit, "--", sourcePath],
    `读取候选制品 ${artifactId} 来源文件`,
  )
    .stdout.split(/\r?\n/u)
    .filter(Boolean)
    .sort();
  if (sourceFiles.length === 0) {
    throw new Error(`候选制品 ${artifactId} 来源树没有文件`);
  }
  const expectedEntries = sourceFiles.map((sourceFile) =>
    path.posix.join(archiveRoot, path.posix.relative(sourcePath, sourceFile)),
  );
  for (const [index, archiveEntry] of expectedEntries.entries()) {
    if (!entries.includes(archiveEntry)) {
      throw new Error(`候选制品 ${artifactId} 缺少来源文件 ${archiveEntry}`);
    }
    const archiveBytes = extractTarBytes(
      artifactPath,
      archiveEntry,
      artifactId,
    );
    const sourceBytes = readCandidateArchiveBytes(
      repoRoot,
      candidateCommit,
      sourceFiles[index],
      artifactId,
    );
    if (sha256(archiveBytes) !== sha256(sourceBytes)) {
      throw new Error(
        `候选制品 ${artifactId} 来源文件字节不一致：${archiveEntry}`,
      );
    }
  }
  const nonDirectoryEntries = entries.filter((entry) => !entry.endsWith("/"));
  const allowed = new Set([...expectedEntries, metadataPath]);
  const unexpected = nonDirectoryEntries.filter(
    (entry) =>
      !allowed.has(entry) &&
      !expectedEntries.some((file) => file.startsWith(`${entry}/`)),
  );
  if (unexpected.length > 0) {
    throw new Error(
      `候选制品 ${artifactId} 包含非候选来源文件：${unexpected.join("、")}`,
    );
  }
  return sourceFiles.length;
}

function readCandidateArchiveBytes(
  repoRoot,
  candidateCommit,
  sourceFile,
  artifactId,
) {
  const archive = spawnSync(
    "git",
    ["archive", "--format=tar", candidateCommit, "--", sourceFile],
    {
      cwd: repoRoot,
      encoding: null,
      shell: false,
      maxBuffer: 256 * 1024 * 1024,
    },
  );
  if (archive.status !== 0 || !archive.stdout) {
    throw new Error(`候选制品 ${artifactId} 无法读取来源文件 ${sourceFile}`);
  }
  const extracted = spawnSync("tar", ["-xOf", "-", sourceFile], {
    input: archive.stdout,
    encoding: null,
    shell: false,
    maxBuffer: 256 * 1024 * 1024,
  });
  if (extracted.status !== 0 || !extracted.stdout) {
    throw new Error(
      `候选制品 ${artifactId} 无法读取来源导出字节 ${sourceFile}`,
    );
  }
  return extracted.stdout;
}

function parseJsonEvidenceBuffer(bytes, label) {
  let value;
  try {
    value = JSON.parse(bytes.toString("utf8"));
  } catch {
    throw new Error(`${label}不是有效 JSON`);
  }
  requireObject(value, label);
  return value;
}

function verifyArtifacts(
  artifacts,
  repoRoot,
  bundleRoot,
  runId,
  candidateCommit,
  runStartedAt,
  generatedAt,
) {
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
    const provenance = describeArtifactProvenance({
      repoRoot,
      bundleRoot,
      evidencePath: record.provenancePath,
      artifactId: record.artifactId,
      artifactFile: current,
      runId,
      candidateCommit,
      runStartedAt,
      generatedAt,
      manifestRelative: true,
    });
    const expectedProvenance = {
      sourcePath: record.sourcePath,
      sourceTreeOid: record.sourceTreeOid,
      evidencePath: record.provenancePath,
      evidenceSize: record.provenanceSize,
      evidenceSha256: record.provenanceSha256,
      execution: record.provenanceExecution,
      rawEvidence: record.provenanceRawEvidence,
    };
    if (!sameCanonical(expectedProvenance, provenance)) {
      throw new Error(`候选制品 ${record.artifactId} 来源证明漂移`);
    }
    const formatMetadata = inspectArtifact({
      repoRoot,
      artifactId: record.artifactId,
      artifactPath: current.absolutePath,
      candidateCommit,
      sourcePath: record.sourcePath,
    });
    if (!sameCanonical(record.formatMetadata, formatMetadata)) {
      throw new Error(`候选制品 ${record.artifactId} 格式元数据漂移`);
    }
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
  assertNoUnknownKeys(payload, GATE_EVIDENCE_KEYS, `门禁 ${gateId} 证据`);
  if (payload.schemaVersion !== RC_SCHEMA_VERSION) {
    throw new Error(
      `门禁 ${gateId} 证据 schemaVersion 必须为 ${RC_SCHEMA_VERSION}`,
    );
  }
  if (payload.kind !== GATE_EVIDENCE_KIND) {
    throw new Error(`门禁 ${gateId} 证据 kind 不一致`);
  }
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

function validateExecution(
  value,
  { label, requiredCommands, runStartedAt, generatedAt, observedAt },
) {
  requireObject(value, `${label} 的 execution`);
  assertNoUnknownKeys(value, EXECUTION_RECORD_KEYS, `${label} 的 execution`);
  if (!Array.isArray(value.commands) || value.commands.length === 0) {
    throw new Error(`${label} 的执行命令不能为空`);
  }
  const commands = value.commands.map((command, index) =>
    requireText(command, `${label} 的执行命令 ${index + 1}`),
  );
  if (JSON.stringify(commands) !== JSON.stringify(requiredCommands)) {
    throw new Error(`${label} 的执行命令不符合合同`);
  }
  if (value.exitCode !== 0) {
    throw new Error(`${label} 的退出码必须为 0`);
  }
  const startedAt = validateIsoTimestamp(
    value.startedAt,
    `${label} 的 startedAt`,
  );
  const finishedAt = validateIsoTimestamp(
    value.finishedAt,
    `${label} 的 finishedAt`,
  );
  if (Date.parse(startedAt) < Date.parse(runStartedAt)) {
    throw new Error(`${label} 的开始时间早于 runStartedAt`);
  }
  if (Date.parse(finishedAt) < Date.parse(startedAt)) {
    throw new Error(`${label} 的完成时间早于开始时间`);
  }
  if (Date.parse(finishedAt) > Date.parse(generatedAt)) {
    throw new Error(`${label} 的完成时间晚于 generatedAt`);
  }
  if (observedAt && Date.parse(observedAt) < Date.parse(finishedAt)) {
    throw new Error(`${label} 的 observedAt 早于完成时间`);
  }
  return { commands, exitCode: 0, startedAt, finishedAt };
}

function validateGateEvidenceStructure({
  gateId,
  rawEvidence,
  execution,
  bundleRoot,
  runId,
  candidateCommit,
}) {
  const byRole = groupRecordsByRole(rawEvidence);
  const commandLog = requireSingleRole(byRole, "COMMAND_LOG", `门禁 ${gateId}`);
  const logText = readFileSync(path.join(bundleRoot, commandLog.path), "utf8");
  validateExecutionLog(logText, {
    label: `门禁 ${gateId}`,
    runId,
    candidateCommit,
    execution,
  });

  if (gateId === "BACKEND_TESTS") {
    validateSurefireEvidence(byRole, bundleRoot);
  } else if (gateId === "BROWSER_E2E") {
    validateBrowserEvidence(byRole, bundleRoot, {
      runId,
      candidateCommit,
      execution,
    });
  } else {
    validateToolNativeLog(gateId, logText);
  }
}

function groupRecordsByRole(records) {
  const byRole = new Map();
  for (const record of records) {
    const items = byRole.get(record.role) ?? [];
    items.push(record);
    byRole.set(record.role, items);
  }
  return byRole;
}

function requireSingleRole(byRole, role, label) {
  const records = byRole.get(role) ?? [];
  if (records.length !== 1) {
    throw new Error(`${label} 必须恰含一个 ${role} 原始证据`);
  }
  return records[0];
}

function validateExecutionLog(
  logText,
  { label, runId, candidateCommit, execution },
) {
  const values = (key) =>
    [...logText.matchAll(new RegExp(`^${key}=(.*)$`, "gmu"))].map((match) =>
      match[1].trim(),
    );
  if (values("runId").at(-1) !== runId) {
    throw new Error(`${label} 原始日志运行标识不一致`);
  }
  if (values("candidateCommit").at(-1) !== candidateCommit) {
    throw new Error(`${label} 原始日志候选提交不一致`);
  }
  if (values("startedAt").at(-1) !== execution.startedAt) {
    throw new Error(`${label} 原始日志开始时间不一致`);
  }
  if (values("completedAt").at(-1) !== execution.finishedAt) {
    throw new Error(`${label} 原始日志完成时间不一致`);
  }
  if (values("exitCode").at(-1) !== String(execution.exitCode)) {
    throw new Error(`${label} 原始日志退出码不一致`);
  }
  if (
    JSON.stringify(values("command")) !== JSON.stringify(execution.commands)
  ) {
    throw new Error(`${label} 原始日志命令不一致`);
  }
}

function validateSurefireEvidence(byRole, bundleRoot) {
  const summaryRecord = requireSingleRole(
    byRole,
    "SUREFIRE_SUMMARY",
    "门禁 BACKEND_TESTS",
  );
  const xmlRecords = byRole.get("SUREFIRE_XML") ?? [];
  if (xmlRecords.length === 0) {
    throw new Error("门禁 BACKEND_TESTS 缺少 Surefire XML 原始报告");
  }
  const aggregate = {
    reportFiles: xmlRecords.length,
    tests: 0,
    failures: 0,
    errors: 0,
    skipped: 0,
  };
  for (const record of xmlRecords) {
    if (!record.path.endsWith(".xml")) {
      throw new Error(`Surefire 原始报告路径不是 XML：${record.path}`);
    }
    const xml = readFileSync(path.join(bundleRoot, record.path), "utf8");
    const opening = /<testsuite\b[^>]*>/u.exec(xml)?.[0];
    if (!opening || !/<testcase\b/u.test(xml)) {
      throw new Error(`Surefire 原始报告结构非法：${record.path}`);
    }
    let declaredTests;
    for (const field of ["tests", "failures", "errors", "skipped"]) {
      const match = new RegExp(`\\b${field}="(\\d+)"`, "u").exec(opening);
      if (!match) {
        throw new Error(`Surefire 原始报告缺少 ${field}：${record.path}`);
      }
      const value = Number.parseInt(match[1], 10);
      if (field === "tests") declaredTests = value;
      aggregate[field] += value;
    }
    const testcaseCount = (xml.match(/<testcase\b/gu) ?? []).length;
    if (testcaseCount !== declaredTests) {
      throw new Error(
        `Surefire 原始报告 testcase 数与 tests 声明不一致：${record.path}`,
      );
    }
  }
  if (aggregate.tests <= 0) throw new Error("Surefire 测试数必须大于 0");
  if (aggregate.failures !== 0 || aggregate.errors !== 0) {
    throw new Error("Surefire 原始报告存在失败或错误");
  }
  if (aggregate.skipped !== 0) {
    throw new Error("Surefire 原始报告存在跳过测试");
  }
  const summary = parseJsonEvidence(
    path.join(bundleRoot, summaryRecord.path),
    "Surefire 汇总",
  );
  if (summary.status !== "PASSED") throw new Error("Surefire 汇总状态未通过");
  for (const field of [
    "reportFiles",
    "tests",
    "failures",
    "errors",
    "skipped",
  ]) {
    if (summary[field] !== aggregate[field]) {
      throw new Error(`Surefire 汇总 ${field} 与原始报告不一致`);
    }
  }
}

function validateBrowserEvidence(
  byRole,
  bundleRoot,
  { runId, candidateCommit, execution },
) {
  const expectedRoles = new Set([
    "COMMAND_LOG",
    "PLAYWRIGHT_JSON",
    "BROWSER_E2E_SUMMARY",
    "READINESS_SUMMARY",
    "READINESS_JSON_BEFORE",
    "READINESS_JSON_AFTER",
    "RUNTIME_IDENTITY_JSON_BEFORE",
    "RUNTIME_IDENTITY_JSON_AFTER",
  ]);
  if (
    byRole.size !== expectedRoles.size ||
    [...byRole.keys()].some((role) => !expectedRoles.has(role))
  ) {
    throw new Error("门禁 BROWSER_E2E 原始证据角色集合不符合合同");
  }
  const reportRecord = requireSingleRole(
    byRole,
    "PLAYWRIGHT_JSON",
    "门禁 BROWSER_E2E",
  );
  const summaryRecord = requireSingleRole(
    byRole,
    "BROWSER_E2E_SUMMARY",
    "门禁 BROWSER_E2E",
  );
  const readinessRecord = requireSingleRole(
    byRole,
    "READINESS_SUMMARY",
    "门禁 BROWSER_E2E",
  );
  const readinessResponseRecords = [
    requireSingleRole(byRole, "READINESS_JSON_BEFORE", "门禁 BROWSER_E2E"),
    requireSingleRole(byRole, "READINESS_JSON_AFTER", "门禁 BROWSER_E2E"),
  ];
  const runtimeIdentityRecords = [
    requireSingleRole(
      byRole,
      "RUNTIME_IDENTITY_JSON_BEFORE",
      "门禁 BROWSER_E2E",
    ),
    requireSingleRole(
      byRole,
      "RUNTIME_IDENTITY_JSON_AFTER",
      "门禁 BROWSER_E2E",
    ),
  ];
  const report = parseJsonEvidence(
    path.join(bundleRoot, reportRecord.path),
    "Playwright 原始报告",
  );
  requireObject(report.config, "Playwright 原始报告 config");
  if (report.config.workers !== 1) {
    throw new Error("Playwright 原始报告 workers 必须为 1");
  }
  if (
    !Array.isArray(report.config.projects) ||
    report.config.projects.length === 0
  ) {
    throw new Error("Playwright 原始报告缺少 projects");
  }
  if (report.config.projects.some((project) => project?.retries !== 0)) {
    throw new Error("Playwright 原始报告 retries 必须为 0");
  }
  if (!Array.isArray(report.errors) || report.errors.length !== 0) {
    throw new Error("Playwright 原始报告 errors 必须为空");
  }
  requireObject(report.stats, "Playwright 原始报告 stats");
  if (
    !Number.isSafeInteger(report.stats.expected) ||
    report.stats.expected <= 0
  ) {
    throw new Error("Playwright 原始报告 expected 必须大于 0");
  }
  for (const field of ["unexpected", "flaky", "skipped"]) {
    if (report.stats[field] !== 0) {
      throw new Error(`Playwright 原始报告 ${field} 必须为 0`);
    }
  }
  if (!Array.isArray(report.suites) || report.suites.length === 0) {
    throw new Error("Playwright 原始报告 suites 不能为空");
  }
  const rawTests = collectPlaywrightTests(report.suites);
  if (rawTests.length !== report.stats.expected) {
    throw new Error("Playwright 原始报告真实测试结果数与 expected 不一致");
  }
  const projectNames = report.config.projects.map((project) => project.name);
  const rawProjectCounts = new Map(projectNames.map((name) => [name, 0]));
  for (const test of rawTests) {
    if (
      test?.expectedStatus !== "passed" ||
      !Array.isArray(test.results) ||
      test.results.length !== 1 ||
      test.results[0]?.status !== "passed" ||
      !rawProjectCounts.has(test.projectName)
    ) {
      throw new Error("Playwright 原始报告包含未通过、重试或未知项目结果");
    }
    rawProjectCounts.set(
      test.projectName,
      rawProjectCounts.get(test.projectName) + 1,
    );
  }

  const summary = parseJsonEvidence(
    path.join(bundleRoot, summaryRecord.path),
    "浏览器 E2E 汇总",
  );
  if (
    summary.status !== "PASSED" ||
    summary.command !== "npm run e2e" ||
    summary.workers !== 1 ||
    summary.retries !== 0 ||
    summary.tests !== report.stats.expected
  ) {
    throw new Error("浏览器 E2E 汇总结构或执行合同不一致");
  }
  for (const field of ["expected", "unexpected", "flaky", "skipped"]) {
    if (summary.stats?.[field] !== report.stats[field]) {
      throw new Error(`浏览器 E2E 汇总 ${field} 与 Playwright 报告不一致`);
    }
  }
  if (
    !Array.isArray(summary.projects) ||
    summary.projects.length !== projectNames.length ||
    summary.projects.some(
      (project, index) =>
        project.name !== projectNames[index] ||
        project.unexpected !== 0 ||
        project.flaky !== 0 ||
        project.skipped !== 0 ||
        project.passed !== project.expected ||
        project.expected !== rawProjectCounts.get(project.name),
    )
  ) {
    throw new Error("浏览器 E2E 项目汇总与 Playwright 报告不一致");
  }
  const readiness = parseJsonEvidence(
    path.join(bundleRoot, readinessRecord.path),
    "浏览器 E2E 就绪汇总",
  );
  if (
    readiness.status !== "UP" ||
    readiness.runId !== runId ||
    readiness.candidateCommit !== candidateCommit ||
    typeof readiness.url !== "string" ||
    !/^https?:\/\//u.test(readiness.url) ||
    typeof readiness.identityUrl !== "string" ||
    !/^https?:\/\//u.test(readiness.identityUrl) ||
    !Array.isArray(readiness.checks) ||
    readiness.checks.length !== 2
  ) {
    throw new Error("浏览器 E2E 就绪证据结构或运行绑定不一致");
  }
  const readinessUrl = new URL(readiness.url);
  const identityUrl = new URL(readiness.identityUrl);
  const expectedIdentityPath = readinessUrl.pathname.replace(
    /\/actuator\/health\/readiness$/u,
    "/api/v1/system/ping",
  );
  if (
    expectedIdentityPath === readinessUrl.pathname ||
    identityUrl.origin !== readinessUrl.origin ||
    identityUrl.pathname !== expectedIdentityPath ||
    identityUrl.search ||
    identityUrl.hash
  ) {
    throw new Error("浏览器 E2E 运行时身份地址与 readiness 地址不一致");
  }
  const expectedPhases = ["BEFORE_E2E", "AFTER_E2E"];
  for (const [index, check] of readiness.checks.entries()) {
    const responseRecord = readinessResponseRecords[index];
    const identityRecord = runtimeIdentityRecords[index];
    if (
      check?.phase !== expectedPhases[index] ||
      check.status !== "UP" ||
      check.httpStatus !== 200 ||
      check.runId !== runId
    ) {
      throw new Error("浏览器 E2E 就绪证据检查结构或运行标识不一致");
    }
    if (check.candidateCommit !== candidateCommit) {
      throw new Error("浏览器 E2E 就绪证据检查候选提交不一致");
    }
    if (check.buildBound !== true || check.buildCommit !== candidateCommit) {
      throw new Error("浏览器 E2E 运行时身份检查候选提交不一致");
    }
    if (!SHA256_PATTERN.test(check.responseSha256 ?? "")) {
      throw new Error("浏览器 E2E 就绪证据响应摘要非法");
    }
    if (check.responsePath !== responseRecord.path) {
      throw new Error("浏览器 E2E 就绪证据原始响应路径不一致");
    }
    if (check.responseSha256 !== responseRecord.sha256) {
      throw new Error("浏览器 E2E 就绪证据原始响应摘要不一致");
    }
    if (check.identityPath !== identityRecord.path) {
      throw new Error("浏览器 E2E 运行时身份原始响应路径不一致");
    }
    if (
      !SHA256_PATTERN.test(check.identitySha256 ?? "") ||
      check.identitySha256 !== identityRecord.sha256
    ) {
      throw new Error("浏览器 E2E 运行时身份原始响应摘要不一致");
    }
    const responsePayload = parseJsonEvidence(
      path.join(bundleRoot, responseRecord.path),
      `浏览器 E2E 就绪证据 ${check.phase} 原始响应`,
    );
    if (responsePayload.status !== "UP") {
      throw new Error(
        `浏览器 E2E 就绪证据 ${check.phase} 原始响应状态必须为 UP`,
      );
    }
    const identityPayload = parseJsonEvidence(
      path.join(bundleRoot, identityRecord.path),
      `浏览器 E2E 运行时身份 ${check.phase} 原始响应`,
    );
    if (
      identityPayload.success !== true ||
      identityPayload.code !== "OK" ||
      identityPayload.data?.product !== "MedKernel" ||
      identityPayload.data?.buildBound !== true
    ) {
      throw new Error(`浏览器 E2E 运行时身份 ${check.phase} 未绑定后端制品`);
    }
    if (identityPayload.data.buildCommit !== candidateCommit) {
      throw new Error("浏览器 E2E 运行时身份原始响应候选提交不一致");
    }
    const checkedAt = validateIsoTimestamp(
      check.checkedAt,
      `浏览器 E2E 就绪证据 ${check.phase} checkedAt`,
    );
    if (
      Date.parse(checkedAt) < Date.parse(execution.startedAt) ||
      Date.parse(checkedAt) > Date.parse(execution.finishedAt)
    ) {
      throw new Error("浏览器 E2E 就绪证据检查时间超出执行窗口");
    }
  }
}

function collectPlaywrightTests(suites) {
  const tests = [];
  const visit = (suite) => {
    if (!suite || typeof suite !== "object" || Array.isArray(suite)) {
      throw new Error("Playwright 原始报告 suite 结构非法");
    }
    if (!Array.isArray(suite.specs) || !Array.isArray(suite.suites)) {
      throw new Error("Playwright 原始报告 suite 缺少 specs 或 suites");
    }
    for (const spec of suite.specs) {
      if (!spec || typeof spec !== "object" || !Array.isArray(spec.tests)) {
        throw new Error("Playwright 原始报告 spec 结构非法");
      }
      tests.push(...spec.tests);
    }
    suite.suites.forEach(visit);
  };
  suites.forEach(visit);
  return tests;
}

function validateToolNativeLog(gateId, logText) {
  const integerAfter = (pattern, label) => {
    const match = pattern.exec(logText);
    if (!match) throw new Error(`门禁 ${gateId} 原始日志缺少 ${label}`);
    return Number.parseInt(match[1], 10);
  };
  if (["CLI_TESTS", "MCP_TESTS"].includes(gateId)) {
    const tests = integerAfter(/^# tests (\d+)$/mu, "Node tests 计数");
    const passed = integerAfter(/^# pass (\d+)$/mu, "Node pass 计数");
    const failed = integerAfter(/^# fail (\d+)$/mu, "Node fail 计数");
    const skipped = integerAfter(/^# skipped (\d+)$/mu, "Node skipped 计数");
    if (tests <= 0 || passed !== tests || failed !== 0 || skipped !== 0) {
      throw new Error(`门禁 ${gateId} 的 Node 测试日志未全量通过`);
    }
  } else if (gateId === "DATABASE_GENERATOR") {
    if (
      !/^# fail 0$/mu.test(logText) ||
      !/migration-check=PASSED/u.test(logText)
    ) {
      throw new Error("门禁 DATABASE_GENERATOR 原始日志缺少生成器全绿结构");
    }
    for (const dialect of ["h2", "postgres", "oracle", "dm", "kingbase"]) {
      if (!logText.includes(dialect)) {
        throw new Error(`门禁 DATABASE_GENERATOR 原始日志缺少 ${dialect}`);
      }
    }
  } else if (gateId === "DEPLOYMENT_CONTRACTS") {
    const successSignals =
      logText.match(/(?:=PASSED|contract passed|校验通过|\[OK\])/gu) ?? [];
    if (successSignals.length < 7) {
      throw new Error("门禁 DEPLOYMENT_CONTRACTS 原始日志不足 7 个通过合同");
    }
  } else if (gateId === "FORMAT_CHECK") {
    for (const marker of [
      "rc-manifest-tests=PASSED",
      "node-syntax=PASSED",
      "prettier=PASSED",
      "openspec-strict=PASSED",
      "git-diff-check=PASSED",
    ]) {
      if (!logText.includes(marker)) {
        throw new Error(`门禁 FORMAT_CHECK 原始日志缺少 ${marker}`);
      }
    }
  } else if (gateId === "FRONTEND_VERIFY_BUILD") {
    if (
      !/Test Files\s+\d+ passed/u.test(logText) ||
      !/Tests\s+\d+ passed/u.test(logText) ||
      !/\d+ modules transformed/u.test(logText) ||
      !/built in/u.test(logText)
    ) {
      throw new Error(
        "门禁 FRONTEND_VERIFY_BUILD 原始日志缺少测试或生产构建结构",
      );
    }
  } else if (gateId === "T_GATE") {
    const requiredNativeProofs = [
      "真实性门禁扫描：mode=all",
      "真实性门禁通过：未发现阻断项。",
      "配置边界门禁扫描：mode=all",
      "配置边界门禁通过：未发现阻断项。",
      "迁移规约门禁扫描：mode=all",
      "迁移规约门禁通过：未发现阻断项。",
      "=== 全量扫描：engine/** 与 shared/** 各模块类级 Javadoc 中文覆盖率 ===",
      "OK   oracle/V1__baseline.sql",
      "OK   postgres/V1__baseline.sql",
      "OK   kingbase/V1__baseline.sql",
    ];
    if (
      requiredNativeProofs.some((proof) => !logText.includes(proof)) ||
      !/^# fail 0$/mu.test(logText) ||
      !/^\s*100%\s*\(/mu.test(logText)
    ) {
      throw new Error("门禁 T_GATE 原始日志缺少全量扫描通过输出");
    }
  }
}

function parseJsonEvidence(absolutePath, label) {
  let value;
  try {
    value = JSON.parse(readFileSync(absolutePath, "utf8"));
  } catch {
    throw new Error(`${label}不是有效 JSON`);
  }
  requireObject(value, label);
  return value;
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

function sameCanonical(left, right) {
  return (
    JSON.stringify(canonicalize(left)) === JSON.stringify(canonicalize(right))
  );
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

function validateArtifactId(value) {
  const artifactId = requireText(value, "artifactId");
  if (!REQUIRED_RC_ARTIFACT_IDS.includes(artifactId)) {
    throw new Error(`artifactId 不受支持：${artifactId}`);
  }
  return artifactId;
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
