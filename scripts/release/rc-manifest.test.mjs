import assert from "node:assert/strict";
import {
  cpSync,
  existsSync,
  lstatSync,
  mkdtempSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import os from "node:os";
import path from "node:path";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { afterEach, test } from "node:test";

import {
  DEFAULT_SOURCE_BASE_COMMIT,
  REQUIRED_RC_GATES,
  createRcManifest,
  serializeRcManifest,
  verifyRcManifest,
} from "./rc-manifest-lib.mjs";

const PROJECT_REPO_ROOT = fileURLToPath(new URL("../..", import.meta.url));
const RC_MANIFEST_CLI = fileURLToPath(
  new URL("./rc-manifest.mjs", import.meta.url),
);
const RUN_ID = "rc-run-20260710-001";
const RUN_STARTED_AT = "2026-07-10T07:00:00.000Z";
const GENERATED_AT = "2026-07-10T07:30:00.000Z";
const EXECUTION_STARTED_AT = "2026-07-10T07:05:00.000Z";
const EXECUTION_FINISHED_AT = "2026-07-10T07:20:00.000Z";
const GATE_COMMANDS = Object.freeze({
  BACKEND_TESTS: [
    "cd medkernel-backend && CI=true mvn -B -q -Dmaven.repo.local=<run>/m2repo -DexcludedGroups=docker,performance clean test",
  ],
  BROWSER_E2E: [
    "cd frontend && CI=true npm run e2e -- --workers=1 --retries=0",
  ],
  CLI_TESTS: ["cd cli && CI=true npm test"],
  DATABASE_GENERATOR: [
    "cd . && CI=true node --test scripts/db/generate-migrations.test.mjs",
    "cd . && CI=true node scripts/db/generate-migrations.mjs --check",
  ],
  DEPLOYMENT_CONTRACTS: [
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-deploy.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-mk-publish-package.sh",
    "cd . && CI=true bash scripts/check-shell-test-assertions.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-fresh-deploy.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-ollama-model.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-failure-recovery.sh",
    "cd . && CI=true bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh",
  ],
  FORMAT_CHECK: [
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
  ],
  FRONTEND_VERIFY_BUILD: [
    "cd frontend && CI=true npm run verify",
    "cd frontend && CI=true npm run build",
  ],
  MCP_TESTS: ["cd mcp-server && CI=true npm test"],
  T_GATE: [
    "cd . && CI=true node --test scripts/authenticity-guard.test.mjs",
    "cd . && CI=true node scripts/authenticity-guard.mjs --mode=all",
    "cd . && CI=true node --test scripts/config-boundary-guard.test.mjs",
    "cd . && CI=true node scripts/config-boundary-guard.mjs --mode=all",
    "cd . && CI=true node --test scripts/security/signing-secret-inventory.test.mjs",
    "cd . && CI=true node scripts/security/signing-secret-inventory.mjs",
    "cd . && CI=true node --test scripts/migration-convention-guard.test.mjs",
    "cd . && CI=true node scripts/migration-convention-guard.mjs --mode=all",
    "cd . && CI=true node --test scripts/performance-contract-guard.test.mjs",
    "cd . && CI=true bash scripts/check-comment-zh.sh --self-test",
    "cd . && CI=true bash scripts/check-comment-zh.sh --mode=full",
  ],
});
const DEPENDENCY_COMMANDS = Object.freeze([
  "cd frontend && CI=true npm ci --cache <run>/npm-cache --no-audit --no-fund",
  "cd medkernel-backend && CI=true mvn -B -q -Dmaven.repo.local=<run>/m2repo dependency:tree -DoutputFile=<bundle>/dependencies/maven-resolved.txt",
]);
const REQUIRED_RC_ARTIFACT_IDS = [
  "BACKEND_JAR",
  "FRONTEND_DIST",
  "CLI_PACKAGE",
  "MCP_PACKAGE",
  "DATABASE_MIGRATIONS",
  "ONPREM_DELIVERY",
];
const ARTIFACT_SOURCE_PATHS = Object.freeze({
  BACKEND_JAR: "medkernel-backend",
  FRONTEND_DIST: "frontend",
  CLI_PACKAGE: "cli",
  MCP_PACKAGE: "mcp-server",
  DATABASE_MIGRATIONS: "medkernel-backend/src/main/resources/db",
  ONPREM_DELIVERY: "deploy/onprem",
});
const temporaryRoots = [];
let cliInputSequence = 0;

afterEach(() => {
  while (temporaryRoots.length > 0) {
    rmSync(temporaryRoots.pop(), { recursive: true, force: true });
  }
});

test("干净候选提交可形成绑定来源、依赖、门禁和制品的可提升清单", () => {
  const fixture = createGitFixture();

  const manifest = createFixtureManifest(fixture);

  assert.equal(
    DEFAULT_SOURCE_BASE_COMMIT,
    "7217504ce82e1aa119c3402e3b5d054f9369e018",
  );
  assert.equal(manifest.status, "PROMOTABLE");
  assert.equal(manifest.promotable, true);
  assert.equal(manifest.sourceBaseCommit, fixture.sourceBaseCommit);
  assert.equal(manifest.candidateCommit, fixture.candidateCommit);
  assert.notEqual(manifest.sourceBaseCommit, manifest.candidateCommit);
  assert.equal(manifest.runId, RUN_ID);
  assert.equal(manifest.runStartedAt, RUN_STARTED_AT);
  assert.equal(manifest.generatedAt, GENERATED_AT);
  assert.ok(manifest.dependencySnapshot);
  assert.deepEqual(
    manifest.dependencySnapshot.records.map(
      ({ dependencyId, semantics }) => `${dependencyId}:${semantics}`,
    ),
    [
      "FRONTEND_NPM_DECLARATION:DECLARATION",
      "FRONTEND_NPM_LOCK:LOCKFILE",
      "MAVEN_DECLARATION:DECLARATION",
      "MAVEN_RESOLUTION_REPORT:RESOLVED_DEPENDENCY_REPORT",
      "CLI_NO_EXTERNAL_DEPENDENCIES:NO_EXTERNAL_DEPENDENCIES",
      "MCP_NO_EXTERNAL_DEPENDENCIES:NO_EXTERNAL_DEPENDENCIES",
    ],
  );
  assert.equal(manifest.gates.length, REQUIRED_RC_GATES.length);
  assert.equal(manifest.gates[0].evidenceStage, "CLEAN_BASELINE");
  assert.equal(
    manifest.gates[0].evidenceKey,
    `rc.gates.${REQUIRED_RC_GATES[0]}`,
  );
  assert.equal(manifest.gates[0].observedCode, REQUIRED_RC_GATES[0]);
  assert.equal(manifest.gates[0].observedAt, GENERATED_AT);
  assert.match(manifest.dependencySnapshot.setSha256, /^[a-f0-9]{64}$/u);
  assert.equal(
    manifest.dependencySnapshot.records.find(
      ({ dependencyId }) => dependencyId === "MAVEN_RESOLUTION_REPORT",
    ).path,
    "dependencies/maven-resolved.txt",
  );
  assert.equal(
    manifest.gates[0].evidencePath,
    `evidence/${REQUIRED_RC_GATES[0].toLowerCase()}.json`,
  );
  assert.equal(manifest.artifacts.files[0].path, "artifacts/backend.jar");
  assert.equal(
    [
      ...manifest.dependencySnapshot.records.map(
        ({ path: filePath }) => filePath,
      ),
      ...manifest.gates.map(({ evidencePath }) => evidencePath),
      ...manifest.artifacts.files.map(({ path: filePath }) => filePath),
    ].some((filePath) => path.isAbsolute(filePath)),
    false,
  );
  assert.match(manifest.artifacts.setSha256, /^[a-f0-9]{64}$/u);
  assert.deepEqual(
    manifest.artifacts.files.map(({ artifactId }) => artifactId),
    REQUIRED_RC_ARTIFACT_IDS,
  );
  assert.equal(
    verifyRcManifest(manifest, {
      repoRoot: fixture.repoRoot,
      bundleRoot: fixture.bundleRoot,
    }).status,
    "VERIFIED",
  );
});

test("独立重验拒绝 RC 名称漂移", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  manifest.rcName = "RC1";

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /RC 清单 rcName 必须为 RC0/u,
  );
});

test("拒绝把输入锚点直接提升为候选提交", () => {
  const fixture = createGitFixture();

  assert.throws(
    () =>
      createFixtureManifest(fixture, {
        sourceBaseCommit: fixture.candidateCommit,
      }),
    /sourceBaseCommit 必须固定为 7217504ce82e1aa119c3402e3b5d054f9369e018/u,
  );

  const manifest = createFixtureManifest(fixture);
  manifest.sourceBaseCommit = fixture.candidateCommit;
  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /sourceBaseCommit 必须固定为 7217504ce82e1aa119c3402e3b5d054f9369e018/u,
  );
});

test("拒绝用固定输入锚点之后的其它合法祖先替代固定锚点", () => {
  const fixture = createGitFixture();

  assert.throws(
    () =>
      createFixtureManifest(fixture, {
        sourceBaseCommit: fixture.alternativeAncestorCommit,
      }),
    /sourceBaseCommit 必须固定为 7217504ce82e1aa119c3402e3b5d054f9369e018/u,
  );

  const manifest = createFixtureManifest(fixture);
  manifest.sourceBaseCommit = fixture.alternativeAncestorCommit;
  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /sourceBaseCommit 必须固定为 7217504ce82e1aa119c3402e3b5d054f9369e018/u,
  );
});

test("拒绝 CLI 或 MCP 把含外部依赖的声明冒充无外部依赖合同", () => {
  for (const [component, optionName] of [
    ["CLI", "cliPackageJson"],
    ["MCP", "mcpPackageJson"],
  ]) {
    const fixture = createGitFixture({
      [optionName]: JSON.stringify({
        name: `@medkernel/${component.toLowerCase()}`,
        private: true,
        dependencies: { external: "1.0.0" },
      }),
    });
    assert.throws(
      () => createFixtureManifest(fixture),
      new RegExp(`${component} 无外部依赖合同不成立`, "u"),
    );
  }
});

test("拒绝缺失或空白的 Maven 本次解析报告", () => {
  const missing = createGitFixture();
  assert.throws(
    () =>
      createFixtureManifest(missing, {
        mavenResolutionPath: path.join(missing.inputRoot, "missing.txt"),
      }),
    /Maven 本次解析报告不存在/u,
  );

  const blank = createGitFixture();
  writeFileSync(blank.mavenResolutionPath, "", "utf8");
  assert.throws(
    () => createFixtureManifest(blank),
    /(?:Maven 本次解析报告|依赖重建原始证据).*不能为空/u,
  );
});

test("独立重验会发现 Maven 本次解析报告被替换", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  writeFileSync(fixture.mavenResolutionPath, "replacement dependency tree\n");

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /Maven 本次解析报告摘要漂移/u,
  );
});

test("RC bundle 可迁移到独立位置后重验且清单不保存绝对路径", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  const relocatedBundleRoot = path.join(
    path.dirname(fixture.bundleRoot),
    "relocated-bundle",
  );
  cpSync(fixture.bundleRoot, relocatedBundleRoot, { recursive: true });
  rmSync(fixture.bundleRoot, { recursive: true, force: true });

  assert.equal(
    verifyRcManifest(manifest, {
      repoRoot: fixture.repoRoot,
      bundleRoot: relocatedBundleRoot,
    }).status,
    "VERIFIED",
  );
});

test("独立重验拒绝清单中的绝对路径和父级越界路径", () => {
  const absolute = createGitFixture();
  const absoluteManifest = createFixtureManifest(absolute);
  absoluteManifest.gates[0].evidencePath = absolute.gates[0].evidencePath;
  assert.throws(
    () =>
      verifyRcManifest(absoluteManifest, {
        repoRoot: absolute.repoRoot,
        bundleRoot: absolute.bundleRoot,
      }),
    /门禁 .*证据文件路径不得是绝对路径/u,
  );

  const traversal = createGitFixture();
  const traversalManifest = createFixtureManifest(traversal);
  traversalManifest.artifacts.files[0].path = "../outside.tar";
  assert.throws(
    () =>
      verifyRcManifest(traversalManifest, {
        repoRoot: traversal.repoRoot,
        bundleRoot: traversal.bundleRoot,
      }),
    /候选制品 .*路径不得包含父级越界/u,
  );
});

test("bundleRoot 必须位于仓库外且自身不得为符号链接", () => {
  const insideRepository = createGitFixture();
  assert.throws(
    () =>
      createFixtureManifest(insideRepository, {
        bundleRoot: insideRepository.repoRoot,
      }),
    /bundleRoot 必须位于仓库外/u,
  );

  const linkedRoot = createGitFixture();
  const linkedBundleRoot = path.join(
    path.dirname(linkedRoot.bundleRoot),
    "linked-bundle-root",
  );
  symlinkSync(linkedRoot.bundleRoot, linkedBundleRoot, "dir");
  assert.throws(
    () => createFixtureManifest(linkedRoot, { bundleRoot: linkedBundleRoot }),
    /bundleRoot 不得是符号链接/u,
  );
});

test("bundleRoot 真实路径必须在仓库外，拒绝祖先符号链接指回仓库", () => {
  const fixture = createGitFixture();
  const inRepositoryBundle = path.join(fixture.repoRoot, "runtime/rc-bundle");
  cpSync(fixture.bundleRoot, inRepositoryBundle, { recursive: true });

  const aliasRoot = mkdtempSync(
    path.join(os.tmpdir(), "medkernel-rc-bundle-alias-"),
  );
  temporaryRoots.push(aliasRoot);
  const linkedRepository = path.join(aliasRoot, "linked-repository");
  symlinkSync(fixture.repoRoot, linkedRepository, "dir");
  const aliasedBundleRoot = path.join(linkedRepository, "runtime/rc-bundle");
  const aliasedGates = fixture.gates.map((gate) => ({
    ...gate,
    evidencePath: path.join(
      aliasedBundleRoot,
      path.relative(fixture.bundleRoot, gate.evidencePath),
    ),
  }));
  const aliasedArtifacts = fixture.artifacts.map((artifact) => ({
    ...artifact,
    path: path.join(
      aliasedBundleRoot,
      path.relative(fixture.bundleRoot, artifact.path),
    ),
  }));

  assert.throws(
    () =>
      createFixtureManifest(fixture, {
        bundleRoot: aliasedBundleRoot,
        mavenResolutionPath: path.join(
          aliasedBundleRoot,
          path.relative(fixture.bundleRoot, fixture.mavenResolutionPath),
        ),
        gates: aliasedGates,
        artifacts: aliasedArtifacts,
      }),
    /bundleRoot 必须位于仓库外/u,
  );
});

test("bundleRoot 拒绝 linked worktree 的 Git 元数据目录", () => {
  const fixture = createGitFixture();
  const linkedRepoRoot = path.join(path.dirname(fixture.repoRoot), "linked");
  git(
    fixture.repoRoot,
    "worktree",
    "add",
    "--detach",
    linkedRepoRoot,
    fixture.candidateCommit,
  );
  const commonGitDir = path.resolve(
    linkedRepoRoot,
    git(linkedRepoRoot, "rev-parse", "--git-common-dir"),
  );
  const hiddenBundleRoot = path.join(commonGitDir, "rc-bundle");
  cpSync(fixture.bundleRoot, hiddenBundleRoot, { recursive: true });
  const hiddenBundle = relocateFixtureBundle(fixture, hiddenBundleRoot);

  assert.throws(
    () =>
      createFixtureManifest({
        ...fixture,
        ...hiddenBundle,
        repoRoot: linkedRepoRoot,
      }),
    /bundleRoot 必须位于仓库外/u,
  );
});

test("创建清单拒绝 bundleRoot 外文件和任一路径组件符号链接", () => {
  const outside = createGitFixture();
  const outsideEvidencePath = path.join(
    path.dirname(outside.bundleRoot),
    "outside-evidence.json",
  );
  cpSync(outside.gates[0].evidencePath, outsideEvidencePath);
  const outsideGates = outside.gates.map((gate, index) =>
    index === 0 ? { ...gate, evidencePath: outsideEvidencePath } : gate,
  );
  assert.throws(
    () => createFixtureManifest(outside, { gates: outsideGates }),
    /门禁 .*证据文件必须位于 bundleRoot 内/u,
  );

  const linked = createGitFixture();
  const linkedDirectory = path.join(linked.bundleRoot, "linked-evidence");
  symlinkSync(path.join(linked.bundleRoot, "evidence"), linkedDirectory, "dir");
  const linkedGates = linked.gates.map((gate, index) =>
    index === 0
      ? {
          ...gate,
          evidencePath: path.join(
            linkedDirectory,
            path.basename(gate.evidencePath),
          ),
        }
      : gate,
  );
  assert.throws(
    () => createFixtureManifest(linked, { gates: linkedGates }),
    /门禁 .*证据文件路径包含符号链接/u,
  );
});

test("创建清单拒绝仓库依赖文件通过符号链接逃逸", () => {
  const linkedCandidate = createGitFixture({
    frontendPackageSymlink: true,
  });

  assert.throws(
    () => createFixtureManifest(linkedCandidate),
    /依赖记录 FRONTEND_NPM_DECLARATION路径包含符号链接/u,
  );
});

test("创建清单拒绝索引中物化为普通文件的符号链接依赖", () => {
  const linkedCandidate = createGitFixture({
    frontendPackageIndexMode: "120000",
  });

  assert.equal(
    lstatSync(
      path.join(linkedCandidate.repoRoot, "frontend/package.json"),
    ).isSymbolicLink(),
    false,
  );
  assert.equal(git(linkedCandidate.repoRoot, "status", "--porcelain=v1"), "");
  assert.match(
    git(
      linkedCandidate.repoRoot,
      "ls-files",
      "--stage",
      "--",
      "frontend/package.json",
    ),
    /^120000 /u,
  );

  assert.throws(
    () => createFixtureManifest(linkedCandidate),
    /依赖记录 FRONTEND_NPM_DECLARATION的 Git 索引模式必须是普通文件/u,
  );
});

test("创建和独立重验拒绝被索引标志隐藏的 tracked 漂移", () => {
  for (const indexFlag of ["--skip-worktree", "--assume-unchanged"]) {
    const fixture = createGitFixture();
    const manifest = createFixtureManifest(fixture);
    git(
      fixture.repoRoot,
      "update-index",
      indexFlag,
      "frontend/package-lock.json",
    );
    writeFile(
      fixture.repoRoot,
      "frontend/package-lock.json",
      '{"name":"tampered","lockfileVersion":3}\n',
    );

    for (const operation of [
      () => createFixtureManifest(fixture),
      () =>
        verifyRcManifest(manifest, {
          repoRoot: fixture.repoRoot,
          bundleRoot: fixture.bundleRoot,
        }),
    ]) {
      assert.throws(
        operation,
        /工作区存在隐藏索引标志.*frontend\/package-lock\.json/u,
      );
    }
  }
});

test("拒绝有 tracked 修改的候选工作区", () => {
  const fixture = createGitFixture();
  writeFile(fixture.repoRoot, "cli/package.json", '{"changed":true}\n');

  assert.throws(
    () => createFixtureManifest(fixture),
    /工作区存在 tracked 修改.*cli\/package\.json/u,
  );
});

test("拒绝全部未跟踪文件，不允许以白名单绕过干净检出", () => {
  const fixture = createGitFixture();
  writeFile(fixture.repoRoot, "notes/read-only.txt", "只读说明\n");

  assert.throws(
    () => createFixtureManifest(fixture),
    /工作区存在未跟踪文件.*notes\/read-only\.txt/u,
  );
  assert.throws(
    () =>
      createFixtureManifest(fixture, {
        untrackedReadOnlyAllowlist: ["notes/read-only.txt"],
      }),
    /工作区存在未跟踪文件.*notes\/read-only\.txt/u,
  );
});

test("拒绝被 git 忽略的残留构建和测试结果", () => {
  const fixture = createGitFixture();
  writeFile(
    fixture.repoRoot,
    "medkernel-backend/target/surefire-reports/result.xml",
    "<testsuite/>\n",
  );
  writeFile(fixture.repoRoot, "frontend/test-results/last-run.json", "{}\n");

  assert.throws(
    () => createFixtureManifest(fixture),
    /工作区存在构建、测试或运行证据残留.*(?:frontend\/test-results.*medkernel-backend\/target|medkernel-backend\/target.*frontend\/test-results)/u,
  );
});

test("独立重验拒绝 tracked 修改", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  writeFile(fixture.repoRoot, "cli/package.json", '{"changed":true}\n');

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /工作区存在 tracked 修改.*cli\/package\.json/u,
  );
});

test("独立重验拒绝未跟踪文件", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  writeFile(fixture.repoRoot, "notes/untracked.txt", "untracked\n");

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /工作区存在未跟踪文件.*notes\/untracked\.txt/u,
  );
});

test("独立重验拒绝被忽略的构建测试残留", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  writeFile(
    fixture.repoRoot,
    "medkernel-backend/target/surefire-reports/result.xml",
    "<testsuite/>\n",
  );

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /工作区存在构建、测试或运行证据残留.*medkernel-backend\/target/u,
  );
});

test("拒绝门禁观察时间早于本次运行开始", () => {
  const fixture = createGitFixture();
  replaceEvidence(fixture, REQUIRED_RC_GATES[0], {
    observedAt: "2026-07-10T06:59:59.999Z",
  });

  assert.throws(
    () => createFixtureManifest(fixture),
    new RegExp(
      `门禁 ${REQUIRED_RC_GATES[0]} 的 observedAt 早于 runStartedAt`,
      "u",
    ),
  );
});

test("拒绝门禁观察时间晚于清单生成时间", () => {
  const fixture = createGitFixture();
  replaceEvidence(fixture, REQUIRED_RC_GATES[0], {
    observedAt: "2026-07-10T07:30:00.001Z",
  });

  assert.throws(
    () => createFixtureManifest(fixture),
    new RegExp(
      `门禁 ${REQUIRED_RC_GATES[0]} 的 observedAt 晚于 generatedAt`,
      "u",
    ),
  );
});

test("拒绝晚于清单生成时间的运行开始时间，独立重验也重新判断", () => {
  const fixture = createGitFixture();
  assert.throws(
    () =>
      createFixtureManifest(fixture, {
        runStartedAt: "2026-07-10T07:30:00.001Z",
      }),
    /runStartedAt 不得晚于 generatedAt/u,
  );

  const manifest = createFixtureManifest(fixture);
  manifest.runStartedAt = "2026-07-10T07:30:00.001Z";
  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /runStartedAt 不得晚于 generatedAt/u,
  );
});

test("拒绝缺少必需观察字段的门禁证据", () => {
  for (const field of [
    "evidenceStage",
    "evidenceKey",
    "observedCode",
    "observedStatus",
  ]) {
    const fixture = createGitFixture();
    deleteEvidenceField(fixture, REQUIRED_RC_GATES[0], field);
    assert.throws(
      () => createFixtureManifest(fixture),
      new RegExp(`门禁 ${REQUIRED_RC_GATES[0]} 的 ${field}\\s*不能为空`, "u"),
    );
  }
});

test("拒绝阶段、证据键和观察码绑定错误的门禁证据", () => {
  for (const [field, value] of [
    ["evidenceStage", "historical-baseline"],
    ["evidenceKey", "rc.gates.OTHER"],
    ["observedCode", "OTHER"],
  ]) {
    const fixture = createGitFixture();
    replaceEvidence(fixture, REQUIRED_RC_GATES[0], { [field]: value });
    assert.throws(
      () => createFixtureManifest(fixture),
      new RegExp(`门禁 ${REQUIRED_RC_GATES[0]} 的 ${field} 不一致`, "u"),
    );
  }
});

test("拒绝历史 run-id 和其他候选提交的门禁证据", () => {
  const historicalRun = createGitFixture();
  replaceEvidence(historicalRun, REQUIRED_RC_GATES[0], {
    runId: "rc-run-20260709-old",
  });
  assert.throws(
    () => createFixtureManifest(historicalRun),
    new RegExp(`门禁 ${REQUIRED_RC_GATES[0]} 的运行标识不一致`, "u"),
  );

  const otherCommit = createGitFixture();
  replaceEvidence(otherCommit, REQUIRED_RC_GATES[1], {
    candidateCommit: otherCommit.sourceBaseCommit,
  });
  assert.throws(
    () => createFixtureManifest(otherCommit),
    new RegExp(`门禁 ${REQUIRED_RC_GATES[1]} 的候选提交不一致`, "u"),
  );
});

test("拒绝短提交和不存在的候选提交", () => {
  const fixture = createGitFixture();
  assert.throws(
    () =>
      createFixtureManifest(fixture, {
        candidateCommit: fixture.candidateCommit.slice(0, 12),
      }),
    /candidateCommit 必须是完整 40 位/u,
  );
  assert.throws(
    () => createFixtureManifest(fixture, { candidateCommit: "f".repeat(40) }),
    /candidateCommit 指向的提交不存在/u,
  );
});

test("拒绝失败、未知、跳过和缺失的必需门禁", () => {
  for (const status of ["FAILED", "UNKNOWN", "SKIPPED"]) {
    const fixture = createGitFixture();
    replaceEvidence(fixture, REQUIRED_RC_GATES[2], {
      observedStatus: status,
    });
    assert.throws(
      () => createFixtureManifest(fixture),
      new RegExp(`门禁 ${REQUIRED_RC_GATES[2]} 未通过：${status}`, "u"),
    );
  }

  const missingGate = createGitFixture();
  assert.throws(
    () =>
      createFixtureManifest(missingGate, { gates: missingGate.gates.slice(1) }),
    new RegExp(`缺少必需门禁.*${REQUIRED_RC_GATES[0]}`, "u"),
  );
});

test("拒绝缺失证据、不可解析证据和创建前声明的错误摘要", () => {
  const missing = createGitFixture();
  missing.gates[0].evidencePath = path.join(missing.inputRoot, "missing.json");
  assert.throws(
    () => createFixtureManifest(missing),
    /门禁 .* 证据文件不存在/u,
  );

  const invalid = createGitFixture();
  writeFileSync(invalid.gates[0].evidencePath, "not-json", "utf8");
  assert.throws(
    () => createFixtureManifest(invalid),
    /门禁 .* 证据不是有效 JSON/u,
  );

  const declared = createGitFixture();
  declared.gates[0].evidenceSha256 = "0".repeat(64);
  assert.throws(
    () => createFixtureManifest(declared),
    /门禁 .* 声明的证据摘要不一致/u,
  );
});

test("已形成清单在证据字节被替换后独立重验失败", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  const gate = fixture.gates[0];
  const payload = JSON.parse(readFileSync(gate.evidencePath, "utf8"));
  writeFileSync(
    gate.evidencePath,
    `${JSON.stringify({ ...payload, observedAt: "2026-07-10T08:00:00.000Z" })}\n`,
    "utf8",
  );

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    new RegExp(`门禁 ${gate.gateId} 的证据摘要漂移`, "u"),
  );
});

test("独立重验拒绝门禁原始日志被删除，不能只相信自报 JSON", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  rmSync(fixture.gateLogs[0], { force: true });

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /门禁 .*原始证据.*不存在/u,
  );
});

test("拒绝任意命令、非零退出和运行窗外时间伪装门禁通过", () => {
  for (const mutation of [
    { execution: { commands: ["true"] } },
    { execution: { exitCode: 7 } },
    { execution: { startedAt: "2026-07-09T07:05:00.000Z" } },
  ]) {
    const fixture = createGitFixture();
    replaceEvidence(fixture, "CLI_TESTS", {
      execution: {
        ...readEvidence(fixture, "CLI_TESTS").execution,
        ...mutation.execution,
      },
    });
    assert.throws(
      () => createFixtureManifest(fixture),
      /门禁 CLI_TESTS 的(?:执行命令不符合合同|退出码必须为 0|开始时间早于 runStartedAt)/u,
    );
  }
});

test("CLI 与 MCP 门禁接受 Node 24 spec reporter 的原生计数并拒绝不一致", () => {
  for (const gateId of ["CLI_TESTS", "MCP_TESTS"]) {
    const fixture = createGitFixture();
    const logPath = fixture.gateFiles[gateId].COMMAND_LOG;
    const node24Log = readFileSync(logPath, "utf8").replace(
      /^# (tests|pass|fail|skipped) /gmu,
      "ℹ $1 ",
    );
    writeFileSync(logPath, node24Log, "utf8");
    assert.doesNotThrow(() => createFixtureManifest(fixture));
  }

  const inconsistent = createGitFixture();
  const inconsistentLogPath = inconsistent.gateFiles.CLI_TESTS.COMMAND_LOG;
  const inconsistentLog = readFileSync(inconsistentLogPath, "utf8")
    .replace(/^# (tests|pass|fail|skipped) /gmu, "ℹ $1 ")
    .replace(/^ℹ pass 1$/mu, "ℹ pass 0");
  writeFileSync(inconsistentLogPath, inconsistentLog, "utf8");
  assert.throws(
    () => createFixtureManifest(inconsistent),
    /门禁 CLI_TESTS 的 Node 测试日志未全量通过/u,
  );
});

test("数据库生成门禁接受 Node 24 spec reporter 并拒绝真实失败", () => {
  const fixture = createGitFixture();
  const logPath = fixture.gateFiles.DATABASE_GENERATOR.COMMAND_LOG;
  const node24Log = readFileSync(logPath, "utf8").replace(
    /^# (tests|pass|fail|skipped) /gmu,
    "ℹ $1 ",
  );
  writeFileSync(logPath, node24Log, "utf8");
  assert.doesNotThrow(() => createFixtureManifest(fixture));

  const failed = createGitFixture();
  const failedLogPath = failed.gateFiles.DATABASE_GENERATOR.COMMAND_LOG;
  const failedLog = readFileSync(failedLogPath, "utf8")
    .replace(/^# (tests|pass|fail|skipped) /gmu, "ℹ $1 ")
    .replace(/^ℹ fail 0$/mu, "ℹ fail 1");
  writeFileSync(failedLogPath, failedLog, "utf8");
  assert.throws(
    () => createFixtureManifest(failed),
    /门禁 DATABASE_GENERATOR 原始日志缺少生成器全绿结构/u,
  );
});

test("前端门禁忽略原生 ANSI 显示码但仍要求测试与构建证据", () => {
  const fixture = createGitFixture();
  const logPath = fixture.gateFiles.FRONTEND_VERIFY_BUILD.COMMAND_LOG;
  const coloredLog = readFileSync(logPath, "utf8")
    .replace(
      "Test Files  1 passed (1)",
      "\u001b[2m Test Files \u001b[22m \u001b[1m\u001b[32m1 passed\u001b[39m\u001b[22m\u001b[90m (1)\u001b[39m",
    )
    .replace(
      "Tests  2 passed (2)",
      "\u001b[2m      Tests \u001b[22m \u001b[1m\u001b[32m2 passed\u001b[39m\u001b[22m\u001b[90m (2)\u001b[39m",
    )
    .replace(
      "✓ 3 modules transformed.",
      "\u001b[32m✓\u001b[39m 3 modules transformed.",
    )
    .replace("✓ built in 100ms", "\u001b[32m✓ built in 100ms\u001b[39m");
  writeFileSync(logPath, coloredLog, "utf8");
  assert.doesNotThrow(() => createFixtureManifest(fixture));

  const incomplete = createGitFixture();
  const incompleteLogPath =
    incomplete.gateFiles.FRONTEND_VERIFY_BUILD.COMMAND_LOG;
  writeFileSync(
    incompleteLogPath,
    readFileSync(incompleteLogPath, "utf8").replace(
      "✓ built in 100ms",
      "build evidence missing",
    ),
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(incomplete),
    /门禁 FRONTEND_VERIFY_BUILD 原始日志缺少测试或生产构建结构/u,
  );

  const unknownControl = createGitFixture();
  const unknownControlLogPath =
    unknownControl.gateFiles.FRONTEND_VERIFY_BUILD.COMMAND_LOG;
  writeFileSync(
    unknownControlLogPath,
    `${readFileSync(unknownControlLogPath, "utf8")}\u001b[2K`,
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(unknownControl),
    /门禁 FRONTEND_VERIFY_BUILD 原始日志包含非标准 ANSI 控制序列/u,
  );
});

test("Surefire 原始 XML 与汇总必须逐文件存在、结构真实且计数一致", () => {
  const deleted = createGitFixture();
  const manifest = createFixtureManifest(deleted);
  rmSync(deleted.gateFiles.BACKEND_TESTS.SUREFIRE_XML, { force: true });
  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: deleted.repoRoot,
        bundleRoot: deleted.bundleRoot,
      }),
    /门禁 BACKEND_TESTS 原始证据 .*不存在/u,
  );

  const inconsistent = createGitFixture();
  writeFileSync(
    inconsistent.gateFiles.BACKEND_TESTS.SUREFIRE_XML,
    '<testsuite name="Fixture" tests="2" failures="1" errors="0" skipped="0"><testcase name="ok"/><testcase name="failed"><failure/></testcase></testsuite>\n',
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(inconsistent),
    /Surefire.*失败|Surefire.*汇总.*不一致/u,
  );

  const countMismatch = createGitFixture();
  writeFileSync(
    countMismatch.gateFiles.BACKEND_TESTS.SUREFIRE_XML,
    '<testsuite name="Fixture" tests="2" failures="0" errors="0" skipped="0"><testcase name="only"/></testsuite>\n',
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(countMismatch),
    /Surefire.*testcase 数与 tests 声明不一致/u,
  );

  const skipped = createGitFixture();
  writeFileSync(
    skipped.gateFiles.BACKEND_TESTS.SUREFIRE_XML,
    '<testsuite name="Fixture" tests="2" failures="0" errors="0" skipped="1"><testcase name="one"/><testcase name="two"><skipped/></testcase></testsuite>\n',
    "utf8",
  );
  writeFileSync(
    skipped.gateFiles.BACKEND_TESTS.SUREFIRE_SUMMARY,
    `${JSON.stringify({
      status: "PASSED",
      reportFiles: 1,
      tests: 2,
      failures: 0,
      errors: 0,
      skipped: 1,
    })}\n`,
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(skipped),
    /Surefire 原始报告存在跳过测试/u,
  );
});

test("浏览器门禁核对 Playwright 原始报告、汇总和就绪结构", () => {
  const fixture = createGitFixture();
  const reportPath = fixture.gateFiles.BROWSER_E2E.PLAYWRIGHT_JSON;
  const report = JSON.parse(readFileSync(reportPath, "utf8"));
  report.stats.unexpected = 1;
  report.stats.expected = 1;
  writeFileSync(reportPath, `${JSON.stringify(report)}\n`, "utf8");

  assert.throws(
    () => createFixtureManifest(fixture),
    /Playwright.*unexpected.*必须为 0/u,
  );
});

test("浏览器门禁拒绝非单 worker 与没有真实测试结果的空报告", () => {
  const parallel = createGitFixture();
  const parallelReportPath = parallel.gateFiles.BROWSER_E2E.PLAYWRIGHT_JSON;
  const parallelReport = JSON.parse(readFileSync(parallelReportPath, "utf8"));
  parallelReport.config.workers = 2;
  writeFileSync(
    parallelReportPath,
    `${JSON.stringify(parallelReport)}\n`,
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(parallel),
    /Playwright 原始报告 workers 必须为 1/u,
  );

  const empty = createGitFixture();
  const emptyReportPath = empty.gateFiles.BROWSER_E2E.PLAYWRIGHT_JSON;
  const emptyReport = JSON.parse(readFileSync(emptyReportPath, "utf8"));
  emptyReport.suites = [{ title: "empty.spec.ts", specs: [], suites: [] }];
  writeFileSync(emptyReportPath, `${JSON.stringify(emptyReport)}\n`, "utf8");
  assert.throws(
    () => createFixtureManifest(empty),
    /Playwright 原始报告真实测试结果数与 expected 不一致/u,
  );
});

test("清单验证器遵守 Playwright 叶子 suite 的可选 suites 契约", () => {
  const officialLeaf = createGitFixture();
  const officialLeafReportPath =
    officialLeaf.gateFiles.BROWSER_E2E.PLAYWRIGHT_JSON;
  const officialLeafReport = JSON.parse(
    readFileSync(officialLeafReportPath, "utf8"),
  );
  delete officialLeafReport.suites[0].suites;
  writeFileSync(
    officialLeafReportPath,
    `${JSON.stringify(officialLeafReport)}\n`,
    "utf8",
  );
  assert.doesNotThrow(() => createFixtureManifest(officialLeaf));

  const malformed = createGitFixture();
  const malformedReportPath = malformed.gateFiles.BROWSER_E2E.PLAYWRIGHT_JSON;
  const malformedReport = JSON.parse(readFileSync(malformedReportPath, "utf8"));
  malformedReport.suites[0].suites = {};
  writeFileSync(
    malformedReportPath,
    `${JSON.stringify(malformedReport)}\n`,
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(malformed),
    /Playwright 原始报告 suite 结构非法/u,
  );
});

test("浏览器就绪证据必须绑定本次运行、候选、前后检查与响应摘要", () => {
  const fixture = createGitFixture();
  const readinessPath = fixture.gateFiles.BROWSER_E2E.READINESS_SUMMARY;
  const readiness = JSON.parse(readFileSync(readinessPath, "utf8"));
  readiness.checks[1].candidateCommit = fixture.alternativeAncestorCommit;
  writeFileSync(readinessPath, `${JSON.stringify(readiness)}\n`, "utf8");

  assert.throws(
    () => createFixtureManifest(fixture),
    /浏览器 E2E 就绪证据.*候选提交不一致/u,
  );
});

test("浏览器就绪汇总必须绑定可重放的前后原始响应", () => {
  const tampered = createGitFixture();
  writeFileSync(
    tampered.gateFiles.BROWSER_E2E.READINESS_JSON_BEFORE,
    `${JSON.stringify({ status: "DOWN" })}\n`,
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(tampered),
    /浏览器 E2E 就绪证据.*(?:摘要不一致|原始响应.*UP)/u,
  );

  const swapped = createGitFixture();
  const readinessPath = swapped.gateFiles.BROWSER_E2E.READINESS_SUMMARY;
  const readiness = JSON.parse(readFileSync(readinessPath, "utf8"));
  readiness.checks[0].responsePath = path.relative(
    swapped.bundleRoot,
    swapped.gateFiles.BROWSER_E2E.READINESS_JSON_AFTER,
  );
  writeFileSync(readinessPath, `${JSON.stringify(readiness)}\n`, "utf8");
  assert.throws(
    () => createFixtureManifest(swapped),
    /浏览器 E2E 就绪证据.*原始响应路径不一致/u,
  );
});

test("浏览器运行时身份必须来自候选后端制品而非任意 UP 服务", () => {
  const fixture = createGitFixture();
  const identityPath =
    fixture.gateFiles.BROWSER_E2E.RUNTIME_IDENTITY_JSON_BEFORE;
  const identity = JSON.parse(readFileSync(identityPath, "utf8"));
  identity.data.buildCommit = fixture.alternativeAncestorCommit;
  const identityBody = `${JSON.stringify(identity)}\n`;
  writeFileSync(identityPath, identityBody, "utf8");
  const readinessPath = fixture.gateFiles.BROWSER_E2E.READINESS_SUMMARY;
  const readiness = JSON.parse(readFileSync(readinessPath, "utf8"));
  readiness.checks[0].identitySha256 = sha256Fixture(
    Buffer.from(identityBody, "utf8"),
  );
  writeFileSync(readinessPath, `${JSON.stringify(readiness)}\n`, "utf8");

  assert.throws(
    () => createFixtureManifest(fixture),
    /浏览器 E2E 运行时身份.*候选提交不一致/u,
  );
});

test("T-GATE 必须来自全量扫描原始通过输出，运行器自写零阻断标记不能代替", () => {
  const fixture = createGitFixture();
  writeFileSync(
    fixture.gateFiles.T_GATE.COMMAND_LOG,
    executionLog({
      runId: RUN_ID,
      candidateCommit: fixture.candidateCommit,
      commands: GATE_COMMANDS.T_GATE,
      nativeLines: [
        "authenticity-blocking=0",
        "config-boundary-blocking=0",
        "migration-convention-blocking=0",
        "performance-contract-failed=0",
        "chinese-comment-coverage=100%",
      ],
    }),
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(fixture),
    /门禁 T_GATE 原始日志缺少全量扫描通过输出/u,
  );
});

test("T-GATE 接受 Node 24 spec reporter 计数并拒绝真实失败", () => {
  const fixture = createGitFixture();
  const logPath = fixture.gateFiles.T_GATE.COMMAND_LOG;
  const node24Log = readFileSync(logPath, "utf8").replace(
    /^# (tests|pass|fail|skipped) /gmu,
    "ℹ $1 ",
  );
  writeFileSync(logPath, node24Log, "utf8");
  assert.doesNotThrow(() => createFixtureManifest(fixture));

  const failed = createGitFixture();
  const failedLogPath = failed.gateFiles.T_GATE.COMMAND_LOG;
  const failedLog = readFileSync(failedLogPath, "utf8")
    .replace(/^# (tests|pass|fail|skipped) /gmu, "ℹ $1 ")
    .replace(/^ℹ fail 0$/mu, "ℹ fail 1");
  writeFileSync(failedLogPath, failedLog, "utf8");
  assert.throws(
    () => createFixtureManifest(failed),
    /门禁 T_GATE 原始日志缺少全量扫描通过输出/u,
  );
});

test("依赖重建绑定锁输入、安装命令、退出码、日志与可重算解析清单", () => {
  const arbitrary = createGitFixture();
  const dependency = JSON.parse(
    readFileSync(arbitrary.dependencyEvidencePath, "utf8"),
  );
  dependency.execution.commands = ["npm install"];
  writeFileSync(
    arbitrary.dependencyEvidencePath,
    `${JSON.stringify(dependency)}\n`,
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(arbitrary),
    /依赖重建.*执行命令不符合合同/u,
  );

  const deleted = createGitFixture();
  const manifest = createFixtureManifest(deleted);
  rmSync(deleted.dependencyInstallLogPath, { force: true });
  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: deleted.repoRoot,
        bundleRoot: deleted.bundleRoot,
      }),
    /依赖重建原始证据 .*不存在/u,
  );
});

test("拒绝旧版 RC 清单，禁止绕过新版证据合同", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  manifest.schemaVersion = "1.0.0";
  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /schemaVersion 必须为 2\.0\.0/u,
  );
});

test("拒绝普通文本和空壳文件冒充候选制品", () => {
  const fixture = createGitFixture();
  writeFileSync(fixture.artifacts[0].path, "not-a-jar\n", "utf8");
  refreshArtifactProvenanceSubject(fixture.artifacts[0]);
  assert.throws(
    () => createFixtureManifest(fixture),
    /候选制品 BACKEND_JAR.*(?:格式非法|不是有效 JAR|缺少必需条目)/u,
  );
});

test("候选制品必须绑定独立来源证明且拒绝其它提交", () => {
  const missing = createGitFixture();
  const manifest = createFixtureManifest(missing);
  rmSync(missing.artifacts[0].provenancePath, { force: true });
  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: missing.repoRoot,
        bundleRoot: missing.bundleRoot,
      }),
    /候选制品 BACKEND_JAR 来源证明.*不存在/u,
  );

  const otherCommit = createGitFixture();
  const provenance = JSON.parse(
    readFileSync(otherCommit.artifacts[1].provenancePath, "utf8"),
  );
  provenance.candidateCommit = otherCommit.alternativeAncestorCommit;
  writeFileSync(
    otherCommit.artifacts[1].provenancePath,
    `${JSON.stringify(provenance)}\n`,
    "utf8",
  );
  assert.throws(
    () => createFixtureManifest(otherCommit),
    /候选制品 FRONTEND_DIST 来源证明候选提交不一致/u,
  );
});

test("拒绝缺失、重复或未知的候选制品类型", () => {
  const missing = createGitFixture();
  assert.throws(
    () =>
      createFixtureManifest(missing, { artifacts: missing.artifacts.slice(1) }),
    new RegExp(`缺少必需候选制品.*${REQUIRED_RC_ARTIFACT_IDS[0]}`, "u"),
  );

  const duplicate = createGitFixture();
  duplicate.artifacts[1].artifactId = duplicate.artifacts[0].artifactId;
  assert.throws(() => createFixtureManifest(duplicate), /候选制品类型重复/u);

  const unknown = createGitFixture();
  unknown.artifacts[0].artifactId = "UNKNOWN_ARTIFACT";
  assert.throws(() => createFixtureManifest(unknown), /存在未知候选制品/u);
});

test("独立重验拒绝清单缺失必需候选制品类型", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  manifest.artifacts.files = manifest.artifacts.files.slice(1);

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    new RegExp(`缺少必需候选制品.*${REQUIRED_RC_ARTIFACT_IDS[0]}`, "u"),
  );
});

test("拒绝创建前声明的错误制品摘要", () => {
  const declared = createGitFixture();
  declared.artifacts[0].sha256 = "0".repeat(64);
  assert.throws(
    () => createFixtureManifest(declared),
    /候选制品 .* 声明的摘要不一致/u,
  );
});

test("已形成清单在候选制品被替换后独立重验失败", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);
  writeFileSync(fixture.artifacts[0].path, "replacement bytes\n", "utf8");

  assert.throws(
    () =>
      verifyRcManifest(manifest, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /候选制品 .* 摘要漂移/u,
  );
});

test("相同输入生成确定 JSON，只有注入的 generatedAt 可变化", () => {
  const fixture = createGitFixture();
  const first = createFixtureManifest(fixture);
  const second = createFixtureManifest(fixture);
  assert.equal(serializeRcManifest(first), serializeRcManifest(second));

  const later = createFixtureManifest(fixture, {
    generatedAt: "2026-07-10T07:31:00.000Z",
  });
  assert.deepEqual({ ...later, generatedAt: first.generatedAt }, first);
});

test("独立重验会发现提交、依赖锁、运行标识和集合摘要漂移", () => {
  const commitDrift = createGitFixture();
  const commitManifest = createFixtureManifest(commitDrift);
  writeFile(commitDrift.repoRoot, "README.md", "next\n");
  git(commitDrift.repoRoot, "add", "README.md");
  git(commitDrift.repoRoot, "commit", "-m", "next");
  assert.throws(
    () =>
      verifyRcManifest(commitManifest, {
        repoRoot: commitDrift.repoRoot,
        bundleRoot: commitDrift.bundleRoot,
      }),
    /当前 HEAD 与 candidateCommit 不一致/u,
  );

  const lockDrift = createGitFixture();
  const lockManifest = createFixtureManifest(lockDrift);
  writeFile(
    lockDrift.repoRoot,
    "frontend/package-lock.json",
    '{"lockfileVersion":3,"changed":true}\n',
  );
  assert.throws(
    () =>
      verifyRcManifest(lockManifest, {
        repoRoot: lockDrift.repoRoot,
        bundleRoot: lockDrift.bundleRoot,
      }),
    /工作区存在 tracked 修改.*frontend\/package-lock\.json/u,
  );

  const runDrift = createGitFixture();
  const runManifest = createFixtureManifest(runDrift);
  runManifest.gates[0].runId = "rc-run-20260710-other";
  assert.throws(
    () =>
      verifyRcManifest(runManifest, {
        repoRoot: runDrift.repoRoot,
        bundleRoot: runDrift.bundleRoot,
      }),
    /门禁 .* 的清单运行标识不一致/u,
  );

  const setDrift = createGitFixture();
  const setManifest = createFixtureManifest(setDrift);
  setManifest.artifacts.setSha256 = "0".repeat(64);
  assert.throws(
    () =>
      verifyRcManifest(setManifest, {
        repoRoot: setDrift.repoRoot,
        bundleRoot: setDrift.bundleRoot,
      }),
    /候选制品集合摘要漂移/u,
  );
});

test("独立重验拒绝清单及摘要记录中的未知字段", () => {
  const fixture = createGitFixture();
  const manifest = createFixtureManifest(fixture);

  const topLevel = structuredClone(manifest);
  topLevel.dependencyLocks = [];
  assert.throws(
    () =>
      verifyRcManifest(topLevel, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /RC 清单包含未知字段：dependencyLocks/u,
  );

  const dependencyRecord = structuredClone(manifest);
  dependencyRecord.dependencySnapshot.records[0].absolutePath =
    "/tmp/forged-dependency";
  assert.throws(
    () =>
      verifyRcManifest(dependencyRecord, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /依赖快照记录包含未知字段：absolutePath/u,
  );

  const artifactRecord = structuredClone(manifest);
  artifactRecord.artifacts.files[0].absolutePath = "/tmp/forged-artifact";
  assert.throws(
    () =>
      verifyRcManifest(artifactRecord, {
        repoRoot: fixture.repoRoot,
        bundleRoot: fixture.bundleRoot,
      }),
    /候选制品记录包含未知字段：absolutePath/u,
  );
});

test("RC manifest CLI 从输入合同原子创建并独立重验清单", () => {
  const fixture = createGitFixture();
  const inputPath = writeCliInput(fixture);
  const outputPath = path.join(fixture.bundleRoot, "rc-manifest.json");

  const created = runRcManifestCli(
    "create",
    "--repo-root",
    fixture.repoRoot,
    "--bundle-root",
    fixture.bundleRoot,
    "--input",
    inputPath,
    "--output",
    outputPath,
  );
  assert.equal(created.status, 0, created.stderr || created.stdout);
  const manifest = JSON.parse(readFileSync(outputPath, "utf8"));
  assert.equal(manifest.candidateCommit, fixture.candidateCommit);
  assert.equal(path.isAbsolute(manifest.gates[0].evidencePath), false);

  const verified = runRcManifestCli(
    "verify",
    "--repo-root",
    fixture.repoRoot,
    "--bundle-root",
    fixture.bundleRoot,
    "--manifest",
    outputPath,
  );
  assert.equal(verified.status, 0, verified.stderr || verified.stdout);
  assert.deepEqual(JSON.parse(verified.stdout), {
    status: "VERIFIED",
    sourceBaseCommit: fixture.sourceBaseCommit,
    candidateCommit: fixture.candidateCommit,
    runId: RUN_ID,
  });
});

test("RC manifest CLI 输出唯一的 schema 2.0 实际执行合同", () => {
  const result = runRcManifestCli("contract");

  assert.equal(result.status, 0, result.stderr || result.stdout);
  const contract = JSON.parse(result.stdout);
  assert.equal(contract.schemaVersion, "2.0.0");
  assert.equal(contract.kind, "MEDKERNEL_RC_EVIDENCE_CONTRACT");
  assert.deepEqual(contract.gates, GATE_COMMANDS);
  assert.deepEqual(contract.dependencyBuild.commands, DEPENDENCY_COMMANDS);
  assert.deepEqual(contract.artifacts.BACKEND_JAR, {
    commands: artifactCommands("BACKEND_JAR"),
    metadataArchivePath: "META-INF/medkernel-build.json",
    sourcePath: "medkernel-backend",
  });
  assert.deepEqual(contract.artifacts.FRONTEND_DIST, {
    commands: artifactCommands("FRONTEND_DIST"),
    metadataArchivePath: "dist/medkernel-build.json",
    sourcePath: "frontend",
  });
  assert.match(contract.gates.BROWSER_E2E[0], /CI=true.*workers=1.*retries=0/u);
  assert.match(
    contract.gates.BACKEND_TESTS[0],
    /maven\.repo\.local=<run>\/m2repo/u,
  );
  assert.doesNotMatch(
    JSON.stringify(contract),
    /run-(?:gate|rc|artifact)|generate-(?:gate|rc|artifact)-evidence/u,
  );
});

test("RC manifest CLI 可为实际构建生成候选绑定元数据与来源证明", () => {
  const fixture = createGitFixture();
  const artifact = fixture.artifacts.find(
    ({ artifactId }) => artifactId === "FRONTEND_DIST",
  );
  const metadataPath = path.join(
    fixture.bundleRoot,
    "generated/build-metadata.json",
  );
  mkdirSync(path.dirname(metadataPath), { recursive: true });

  const metadataResult = runRcManifestCli(
    "build-metadata",
    "--artifact-id",
    artifact.artifactId,
    "--candidate-commit",
    fixture.candidateCommit,
    "--output",
    metadataPath,
  );
  assert.equal(
    metadataResult.status,
    0,
    metadataResult.stderr || metadataResult.stdout,
  );
  assert.deepEqual(JSON.parse(readFileSync(metadataPath, "utf8")), {
    artifactId: "FRONTEND_DIST",
    candidateCommit: fixture.candidateCommit,
    kind: "MEDKERNEL_BUILD_METADATA",
    schemaVersion: "1.0.0",
  });

  const originalProvenance = JSON.parse(
    readFileSync(artifact.provenancePath, "utf8"),
  );
  const generatedProvenancePath = path.join(
    fixture.bundleRoot,
    "generated/frontend-provenance.json",
  );
  const buildLogPath = path.join(
    fixture.bundleRoot,
    originalProvenance.rawEvidence[0].path,
  );
  const provenanceResult = runRcManifestCli(
    "attest-artifact",
    "--repo-root",
    fixture.repoRoot,
    "--bundle-root",
    fixture.bundleRoot,
    "--artifact-id",
    artifact.artifactId,
    "--artifact",
    artifact.path,
    "--build-log",
    buildLogPath,
    "--run-id",
    RUN_ID,
    "--candidate-commit",
    fixture.candidateCommit,
    "--started-at",
    EXECUTION_STARTED_AT,
    "--finished-at",
    EXECUTION_FINISHED_AT,
    "--output",
    generatedProvenancePath,
  );
  assert.equal(
    provenanceResult.status,
    0,
    provenanceResult.stderr || provenanceResult.stdout,
  );
  assert.deepEqual(
    JSON.parse(readFileSync(generatedProvenancePath, "utf8")),
    originalProvenance,
  );
});

test("RC manifest CLI 拒绝覆盖既有清单且保留原字节", () => {
  const fixture = createGitFixture();
  const inputPath = writeCliInput(fixture);
  const outputPath = path.join(fixture.bundleRoot, "rc-manifest.json");
  const args = [
    "create",
    "--repo-root",
    fixture.repoRoot,
    "--bundle-root",
    fixture.bundleRoot,
    "--input",
    inputPath,
    "--output",
    outputPath,
  ];
  const first = runRcManifestCli(...args);
  assert.equal(first.status, 0, first.stderr || first.stdout);
  const originalBytes = readFileSync(outputPath);

  const second = runRcManifestCli(...args);
  assert.notEqual(second.status, 0);
  assert.match(second.stderr, /输出清单已存在，拒绝覆盖/u);
  assert.deepEqual(readFileSync(outputPath), originalBytes);
});

test("RC manifest CLI 创建失败不留下清单或临时半成品", () => {
  const fixture = createGitFixture();
  const inputPath = writeCliInput(fixture, { candidateCommit: "bad" });
  const outputPath = path.join(fixture.bundleRoot, "failed-manifest.json");

  const result = runRcManifestCli(
    "create",
    "--repo-root",
    fixture.repoRoot,
    "--bundle-root",
    fixture.bundleRoot,
    "--input",
    inputPath,
    "--output",
    outputPath,
  );
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /candidateCommit 必须是完整 40 位/u);
  assert.equal(existsSync(outputPath), false);
  assert.deepEqual(
    readdirSync(fixture.bundleRoot).filter((entry) => entry.includes(".tmp-")),
    [],
  );
});

function writeCliInput(fixture, overrides = {}) {
  const inputPath = path.join(
    path.dirname(fixture.bundleRoot),
    `rc-input-${++cliInputSequence}.json`,
  );
  writeFileSync(
    inputPath,
    `${JSON.stringify({
      sourceBaseCommit: fixture.sourceBaseCommit,
      candidateCommit: fixture.candidateCommit,
      runId: RUN_ID,
      runStartedAt: RUN_STARTED_AT,
      generatedAt: GENERATED_AT,
      mavenResolutionPath: fixture.mavenResolutionPath,
      dependencyEvidencePath: fixture.dependencyEvidencePath,
      gates: fixture.gates,
      artifacts: fixture.artifacts,
      ...overrides,
    })}\n`,
    "utf8",
  );
  return inputPath;
}

function runRcManifestCli(...args) {
  return spawnSync(process.execPath, [RC_MANIFEST_CLI, ...args], {
    cwd: PROJECT_REPO_ROOT,
    encoding: "utf8",
    shell: false,
  });
}

function createGitFixture(options = {}) {
  const fixtureRoot = mkdtempSync(
    path.join(os.tmpdir(), "medkernel-rc-manifest-"),
  );
  temporaryRoots.push(fixtureRoot);
  const repoRoot = path.join(fixtureRoot, "repo");
  const inputRoot = path.join(fixtureRoot, "inputs");
  mkdirSync(repoRoot, { recursive: true });
  mkdirSync(inputRoot, { recursive: true });
  git(repoRoot, "init", "-q");
  git(repoRoot, "config", "user.name", "MedKernel Test");
  git(repoRoot, "config", "user.email", "test@medkernel.invalid");
  if (options.frontendPackageIndexMode) {
    git(repoRoot, "config", "core.symlinks", "false");
  }
  const commonGitDir = git(PROJECT_REPO_ROOT, "rev-parse", "--git-common-dir");
  const sharedObjectRoot = path.resolve(
    PROJECT_REPO_ROOT,
    commonGitDir,
    "objects",
  );
  writeFile(repoRoot, ".git/objects/info/alternates", `${sharedObjectRoot}\n`);
  git(repoRoot, "cat-file", "-e", `${DEFAULT_SOURCE_BASE_COMMIT}^{commit}`);
  git(repoRoot, "update-ref", "refs/heads/main", DEFAULT_SOURCE_BASE_COMMIT);
  git(repoRoot, "symbolic-ref", "HEAD", "refs/heads/main");
  git(repoRoot, "read-tree", "--empty");

  writeFile(
    repoRoot,
    ".gitignore",
    [
      "target/",
      "**/target/",
      "node_modules/",
      "frontend/test-results/",
      "runtime/",
      "*.log",
      "",
    ].join("\n"),
  );
  writeFile(repoRoot, "README.md", "RC candidate ancestor\n");
  writeFile(
    repoRoot,
    "medkernel-backend/pom.xml",
    "<project><version>0.1.0</version></project>\n",
  );
  writeFile(
    repoRoot,
    "medkernel-backend/src/main/java/com/medkernel/MedKernelApplication.java",
    "package com.medkernel; public final class MedKernelApplication {}\n",
  );
  writeFile(
    repoRoot,
    "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json",
    '{"schemaVersion":"fixture"}\n',
  );
  for (const dialect of ["h2", "postgres", "oracle", "dm", "kingbase"]) {
    writeFile(
      repoRoot,
      `medkernel-backend/src/main/resources/db/migration/${dialect}/V1__baseline.sql`,
      `-- ${dialect} fixture\nSELECT 1;\n`,
    );
  }
  if (options.frontendPackageSymlink) {
    const externalPackagePath = path.join(
      inputRoot,
      "external/frontend-package.json",
    );
    writeFile(
      inputRoot,
      "external/frontend-package.json",
      '{"name":"frontend","private":true}\n',
    );
    mkdirSync(path.join(repoRoot, "frontend"), { recursive: true });
    symlinkSync(
      externalPackagePath,
      path.join(repoRoot, "frontend/package.json"),
    );
  } else {
    writeFile(
      repoRoot,
      "frontend/package.json",
      '{"name":"frontend","private":true}\n',
    );
  }
  writeFile(
    repoRoot,
    "frontend/package-lock.json",
    `${JSON.stringify({
      name: "frontend",
      lockfileVersion: 3,
      packages: {
        "": { name: "frontend", version: "1.0.0" },
        "node_modules/example": {
          name: "example",
          version: "1.2.3",
          resolved: "https://registry.invalid/example-1.2.3.tgz",
          integrity: "sha512-fixture",
        },
      },
    })}\n`,
  );
  writeFile(
    repoRoot,
    "cli/package.json",
    `${options.cliPackageJson ?? '{"name":"@medkernel/cli","version":"0.1.0","private":true,"bin":{"medkernel":"./src/cli.mjs"}}'}\n`,
  );
  writeFile(repoRoot, "cli/src/cli.mjs", "export const cli = true;\n");
  writeFile(
    repoRoot,
    "mcp-server/package.json",
    `${options.mcpPackageJson ?? '{"name":"@medkernel/mcp-server","version":"0.1.0","private":true,"bin":{"medkernel-mcp":"./src/server.mjs"}}'}\n`,
  );
  writeFile(
    repoRoot,
    "mcp-server/src/server.mjs",
    "export const server = true;\n",
  );
  writeFile(repoRoot, "frontend/src/main.ts", "export const app = true;\n");
  writeFile(repoRoot, "deploy/onprem/README.md", "# On-prem fixture\n");
  writeFile(
    repoRoot,
    "deploy/onprem/medkernel-deploy.sh",
    "#!/usr/bin/env bash\nset -euo pipefail\n",
  );
  git(repoRoot, "add", "-A");
  if (options.frontendPackageIndexMode) {
    const packageBlob = git(
      repoRoot,
      "hash-object",
      "-w",
      "frontend/package.json",
    );
    git(
      repoRoot,
      "update-index",
      "--add",
      "--cacheinfo",
      `${options.frontendPackageIndexMode},${packageBlob},frontend/package.json`,
    );
  }
  git(repoRoot, "commit", "-q", "-m", "candidate ancestor");
  const alternativeAncestorCommit = git(repoRoot, "rev-parse", "HEAD");

  writeFile(repoRoot, "README.md", "RC candidate commit\n");
  git(repoRoot, "add", "README.md");
  git(repoRoot, "commit", "-q", "-m", "candidate commit");
  const candidateCommit = git(repoRoot, "rev-parse", "HEAD");

  const evidenceRoot = path.join(inputRoot, "evidence");
  const artifactRoot = path.join(inputRoot, "artifacts");
  mkdirSync(evidenceRoot, { recursive: true });
  mkdirSync(artifactRoot, { recursive: true });
  const mavenResolutionPath = path.join(
    inputRoot,
    "dependencies/maven-resolved.txt",
  );
  mkdirSync(path.dirname(mavenResolutionPath), { recursive: true });
  writeFileSync(
    mavenResolutionPath,
    [
      "com.medkernel:medkernel-backend:jar:0.1.0",
      "+- org.example:fixture-core:jar:1.2.3:compile",
      "\\- org.example:fixture-test:jar:4.5.6:test",
      "",
    ].join("\n"),
    "utf8",
  );
  const dependencyInstallLogPath = path.join(
    inputRoot,
    "evidence/logs/dependency-install.log",
  );
  mkdirSync(path.dirname(dependencyInstallLogPath), { recursive: true });
  writeFileSync(
    dependencyInstallLogPath,
    executionLog({
      runId: RUN_ID,
      candidateCommit,
      commands: DEPENDENCY_COMMANDS,
      nativeLines: ["added 1 package", "mavenDependencies=2", "result=PASSED"],
    }),
    "utf8",
  );
  const dependencyEvidencePath = path.join(
    inputRoot,
    "evidence/dependency-build.json",
  );
  writeFileSync(
    dependencyEvidencePath,
    `${JSON.stringify({
      schemaVersion: "2.0.0",
      kind: "MEDKERNEL_DEPENDENCY_BUILD_EVIDENCE",
      runId: RUN_ID,
      candidateCommit,
      execution: {
        commands: DEPENDENCY_COMMANDS,
        exitCode: 0,
        startedAt: EXECUTION_STARTED_AT,
        finishedAt: EXECUTION_FINISHED_AT,
      },
      rawEvidence: [
        {
          role: "INSTALL_LOG",
          path: path.relative(inputRoot, dependencyInstallLogPath),
        },
        {
          role: "MAVEN_RESOLUTION_REPORT",
          path: path.relative(inputRoot, mavenResolutionPath),
        },
      ],
    })}\n`,
    "utf8",
  );
  const gateFiles = {};
  const gates = REQUIRED_RC_GATES.map((gateId) => {
    const evidencePath = path.join(
      evidenceRoot,
      `${gateId.toLowerCase()}.json`,
    );
    const raw = createGateRawEvidence(inputRoot, gateId, candidateCommit);
    gateFiles[gateId] = raw.byRole;
    writeFileSync(
      evidencePath,
      `${JSON.stringify({
        schemaVersion: "2.0.0",
        kind: "MEDKERNEL_GATE_EVIDENCE",
        gateId,
        evidenceStage: "CLEAN_BASELINE",
        evidenceKey: `rc.gates.${gateId}`,
        observedCode: gateId,
        observedStatus: "PASSED",
        runId: RUN_ID,
        candidateCommit,
        observedAt: GENERATED_AT,
        execution: {
          commands: GATE_COMMANDS[gateId],
          exitCode: 0,
          startedAt: EXECUTION_STARTED_AT,
          finishedAt: EXECUTION_FINISHED_AT,
        },
        rawEvidence: raw.records,
      })}\n`,
      "utf8",
    );
    return { gateId, evidencePath };
  });
  const artifacts = REQUIRED_RC_ARTIFACT_IDS.map((artifactId) => {
    const artifactPath = createArtifactFixture({
      fixtureRoot,
      repoRoot,
      artifactRoot,
      artifactId,
      candidateCommit,
    });
    const sourcePath = ARTIFACT_SOURCE_PATHS[artifactId];
    const sourceTreeOid = git(
      repoRoot,
      "rev-parse",
      `${candidateCommit}:${sourcePath}`,
    );
    const buildLogPath = path.join(
      inputRoot,
      "evidence/logs",
      `artifact-${artifactId.toLowerCase()}.log`,
    );
    writeFileSync(
      buildLogPath,
      executionLog({
        runId: RUN_ID,
        candidateCommit,
        commands: artifactCommands(artifactId),
        nativeLines: [`artifactId=${artifactId}`, "result=PASSED"],
      }),
      "utf8",
    );
    const provenancePath = path.join(
      inputRoot,
      "evidence/provenance",
      `${artifactId.toLowerCase()}.json`,
    );
    mkdirSync(path.dirname(provenancePath), { recursive: true });
    writeFileSync(
      provenancePath,
      `${JSON.stringify({
        schemaVersion: "2.0.0",
        kind: "MEDKERNEL_ARTIFACT_PROVENANCE",
        artifactId,
        runId: RUN_ID,
        candidateCommit,
        sourcePath,
        sourceTreeOid,
        execution: {
          commands: artifactCommands(artifactId),
          exitCode: 0,
          startedAt: EXECUTION_STARTED_AT,
          finishedAt: EXECUTION_FINISHED_AT,
        },
        subject: {
          path: path.relative(inputRoot, artifactPath),
          size: readFileSync(artifactPath).byteLength,
          sha256: sha256Fixture(readFileSync(artifactPath)),
        },
        rawEvidence: [
          {
            role: "BUILD_LOG",
            path: path.relative(inputRoot, buildLogPath),
          },
        ],
      })}\n`,
      "utf8",
    );
    return { artifactId, path: artifactPath, provenancePath };
  });
  return {
    repoRoot,
    inputRoot,
    bundleRoot: inputRoot,
    sourceBaseCommit: DEFAULT_SOURCE_BASE_COMMIT,
    alternativeAncestorCommit,
    candidateCommit,
    mavenResolutionPath,
    dependencyEvidencePath,
    dependencyInstallLogPath,
    gates,
    gateLogs: gates.map(({ evidencePath }) =>
      path.join(
        inputRoot,
        "evidence/logs",
        `${path.basename(evidencePath, ".json")}.log`,
      ),
    ),
    gateFiles,
    artifacts,
  };
}

function relocateFixtureBundle(fixture, bundleRoot) {
  const relocate = (sourcePath) =>
    path.join(bundleRoot, path.relative(fixture.bundleRoot, sourcePath));
  return {
    bundleRoot,
    mavenResolutionPath: relocate(fixture.mavenResolutionPath),
    dependencyEvidencePath: relocate(fixture.dependencyEvidencePath),
    gates: fixture.gates.map((gate) => ({
      ...gate,
      evidencePath: relocate(gate.evidencePath),
    })),
    artifacts: fixture.artifacts.map((artifact) => ({
      ...artifact,
      path: relocate(artifact.path),
      provenancePath: relocate(artifact.provenancePath),
    })),
  };
}

function createGateRawEvidence(inputRoot, gateId, candidateCommit) {
  const records = [];
  const byRole = {};
  const add = (role, relativePath, contents) => {
    const absolutePath = path.join(inputRoot, relativePath);
    mkdirSync(path.dirname(absolutePath), { recursive: true });
    writeFileSync(absolutePath, contents, "utf8");
    records.push({ role, path: relativePath });
    byRole[role] = absolutePath;
  };
  const nativeLines = gateNativeLogLines(gateId);
  add(
    "COMMAND_LOG",
    `evidence/logs/${gateId.toLowerCase()}.log`,
    executionLog({
      runId: RUN_ID,
      candidateCommit,
      commands: GATE_COMMANDS[gateId],
      nativeLines,
    }),
  );

  if (gateId === "BACKEND_TESTS") {
    add(
      "SUREFIRE_SUMMARY",
      "evidence/summaries/backend-tests.json",
      `${JSON.stringify({
        status: "PASSED",
        reportFiles: 1,
        tests: 2,
        failures: 0,
        errors: 0,
        skipped: 0,
      })}\n`,
    );
    add(
      "SUREFIRE_XML",
      "evidence/surefire/TEST-Fixture.xml",
      '<testsuite name="Fixture" tests="2" failures="0" errors="0" skipped="0"><testcase name="one"/><testcase name="two"/></testsuite>\n',
    );
  }
  if (gateId === "BROWSER_E2E") {
    const readinessBeforePath = "evidence/browser-e2e/readiness-before.json";
    const readinessAfterPath = "evidence/browser-e2e/readiness-after.json";
    const readinessBeforeBody = `${JSON.stringify({ status: "UP" })}\n`;
    const readinessAfterBody = `${JSON.stringify({ status: "UP" })}\n`;
    const identityBeforePath =
      "evidence/browser-e2e/runtime-identity-before.json";
    const identityAfterPath =
      "evidence/browser-e2e/runtime-identity-after.json";
    const runtimeIdentityBody = `${JSON.stringify({
      success: true,
      code: "OK",
      data: {
        product: "MedKernel",
        buildBound: true,
        buildCommit: candidateCommit,
      },
    })}\n`;
    add(
      "PLAYWRIGHT_JSON",
      "evidence/browser-e2e/playwright-results.json",
      `${JSON.stringify({
        config: {
          workers: 1,
          projects: [
            { name: "chromium", retries: 0 },
            { name: "国产 Chromium 内核仿真（非现场认证）", retries: 0 },
          ],
        },
        errors: [],
        stats: {
          startTime: EXECUTION_STARTED_AT,
          duration: 1000,
          expected: 2,
          unexpected: 0,
          flaky: 0,
          skipped: 0,
        },
        suites: [
          {
            title: "fixture.spec.ts",
            specs: [
              {
                title: "fixture",
                tests: [
                  {
                    expectedStatus: "passed",
                    projectName: "chromium",
                    results: [{ status: "passed" }],
                  },
                  {
                    expectedStatus: "passed",
                    projectName: "国产 Chromium 内核仿真（非现场认证）",
                    results: [{ status: "passed" }],
                  },
                ],
              },
            ],
            suites: [],
          },
        ],
      })}\n`,
    );
    add(
      "BROWSER_E2E_SUMMARY",
      "evidence/summaries/browser-e2e.json",
      `${JSON.stringify({
        status: "PASSED",
        command: "npm run e2e",
        workers: 1,
        retries: 0,
        stats: {
          startTime: EXECUTION_STARTED_AT,
          duration: 1000,
          expected: 2,
          unexpected: 0,
          flaky: 0,
          skipped: 0,
        },
        tests: 2,
        projects: [
          {
            name: "chromium",
            expected: 1,
            passed: 1,
            unexpected: 0,
            flaky: 0,
            skipped: 0,
          },
          {
            name: "国产 Chromium 内核仿真（非现场认证）",
            expected: 1,
            passed: 1,
            unexpected: 0,
            flaky: 0,
            skipped: 0,
          },
        ],
      })}\n`,
    );
    add("READINESS_JSON_BEFORE", readinessBeforePath, readinessBeforeBody);
    add("READINESS_JSON_AFTER", readinessAfterPath, readinessAfterBody);
    add(
      "RUNTIME_IDENTITY_JSON_BEFORE",
      identityBeforePath,
      runtimeIdentityBody,
    );
    add("RUNTIME_IDENTITY_JSON_AFTER", identityAfterPath, runtimeIdentityBody);
    add(
      "READINESS_SUMMARY",
      "evidence/summaries/browser-e2e-readiness.json",
      `${JSON.stringify({
        status: "UP",
        runId: RUN_ID,
        candidateCommit,
        url: "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
        identityUrl: "http://127.0.0.1:18080/medkernel/api/v1/system/ping",
        checks: [
          {
            phase: "BEFORE_E2E",
            status: "UP",
            httpStatus: 200,
            checkedAt: "2026-07-10T07:06:00.000Z",
            runId: RUN_ID,
            candidateCommit,
            responsePath: readinessBeforePath,
            responseSha256: sha256Fixture(
              Buffer.from(readinessBeforeBody, "utf8"),
            ),
            identityPath: identityBeforePath,
            identitySha256: sha256Fixture(
              Buffer.from(runtimeIdentityBody, "utf8"),
            ),
            buildBound: true,
            buildCommit: candidateCommit,
          },
          {
            phase: "AFTER_E2E",
            status: "UP",
            httpStatus: 200,
            checkedAt: "2026-07-10T07:19:00.000Z",
            runId: RUN_ID,
            candidateCommit,
            responsePath: readinessAfterPath,
            responseSha256: sha256Fixture(
              Buffer.from(readinessAfterBody, "utf8"),
            ),
            identityPath: identityAfterPath,
            identitySha256: sha256Fixture(
              Buffer.from(runtimeIdentityBody, "utf8"),
            ),
            buildBound: true,
            buildCommit: candidateCommit,
          },
        ],
      })}\n`,
    );
  }

  return { records, byRole };
}

function gateNativeLogLines(gateId) {
  switch (gateId) {
    case "BACKEND_TESTS":
      return ["Tests run: 2, Failures: 0, Errors: 0, Skipped: 0"];
    case "BROWSER_E2E":
      return ["playwright expected=2 unexpected=0 flaky=0 skipped=0"];
    case "CLI_TESTS":
    case "MCP_TESTS":
      return [
        "TAP version 13",
        "ok 1 - fixture",
        "1..1",
        "# tests 1",
        "# pass 1",
        "# fail 0",
        "# skipped 0",
      ];
    case "DATABASE_GENERATOR":
      return [
        "TAP version 13",
        "# tests 1",
        "# pass 1",
        "# fail 0",
        "migration-check=PASSED dialects=h2,postgres,oracle,dm,kingbase",
      ];
    case "DEPLOYMENT_CONTRACTS":
      return [
        "validate-medkernel-deploy.sh=PASSED",
        "validate-mk-publish-package.sh=PASSED",
        "check-shell-test-assertions.sh=PASSED",
        "validate-medkernel-fresh-deploy.sh=PASSED",
        "validate-ollama-model.sh=PASSED",
        "validate-medkernel-failure-recovery.sh=PASSED",
        "validate-medkernel-post-rehearsal-verify.sh=PASSED",
      ];
    case "FORMAT_CHECK":
      return [
        "rc-manifest-tests=PASSED",
        "node-syntax=PASSED",
        "prettier=PASSED",
        "openspec-strict=PASSED",
        "git-diff-check=PASSED",
      ];
    case "FRONTEND_VERIFY_BUILD":
      return [
        "Test Files  1 passed (1)",
        "Tests  2 passed (2)",
        "✓ 3 modules transformed.",
        "✓ built in 100ms",
      ];
    case "T_GATE":
      return [
        "真实性门禁扫描：mode=all，扫描文件 1 个。",
        "真实性门禁通过：未发现阻断项。",
        "配置边界门禁扫描：mode=all，扫描文件 1 个。",
        "配置边界门禁通过：未发现阻断项。",
        "迁移规约门禁扫描：mode=all，扫描文件 5 个。",
        "迁移规约门禁通过：未发现阻断项。",
        "TAP version 13",
        "# tests 4",
        "# pass 4",
        "# fail 0",
        "# skipped 0",
        "=== 全量扫描：engine/** 与 shared/** 各模块类级 Javadoc 中文覆盖率 ===",
        "100% ( 1/ 1) engine/context",
        "OK   oracle/V1__baseline.sql",
        "OK   postgres/V1__baseline.sql",
        "OK   kingbase/V1__baseline.sql",
      ];
    default:
      throw new Error(`未知测试门禁：${gateId}`);
  }
}

function executionLog({ runId, candidateCommit, commands, nativeLines }) {
  return [
    `runId=${runId}`,
    `candidateCommit=${candidateCommit}`,
    `startedAt=${EXECUTION_STARTED_AT}`,
    ...commands.map((command) => `command=${command}`),
    ...nativeLines,
    "exitCode=0",
    `completedAt=${EXECUTION_FINISHED_AT}`,
    "",
  ].join("\n");
}

function createArtifactFixture({
  fixtureRoot,
  repoRoot,
  artifactRoot,
  artifactId,
  candidateCommit,
}) {
  const fileNames = {
    BACKEND_JAR: "backend.jar",
    FRONTEND_DIST: "frontend-dist.tar.gz",
    CLI_PACKAGE: "cli-package.tgz",
    MCP_PACKAGE: "mcp-package.tgz",
    DATABASE_MIGRATIONS: "database-migrations.tar.gz",
    ONPREM_DELIVERY: "onprem-delivery.tar.gz",
  };
  const artifactPath = path.join(artifactRoot, fileNames[artifactId]);
  const stagingRoot = path.join(
    fixtureRoot,
    "artifact-staging",
    artifactId.toLowerCase(),
  );
  mkdirSync(stagingRoot, { recursive: true });
  const metadata = `${JSON.stringify({
    schemaVersion: "1.0.0",
    kind: "MEDKERNEL_BUILD_METADATA",
    artifactId,
    candidateCommit,
  })}\n`;

  if (artifactId === "BACKEND_JAR") {
    writeFile(stagingRoot, "META-INF/medkernel-build.json", metadata);
    writeFile(
      stagingRoot,
      "BOOT-INF/classes/com/medkernel/MedKernelApplication.class",
      "fixture-bytecode\n",
    );
    const manifestPath = path.join(
      fixtureRoot,
      `manifest-${candidateCommit.slice(0, 12)}.mf`,
    );
    writeFileSync(
      manifestPath,
      [
        "Manifest-Version: 1.0",
        "Main-Class: org.springframework.boot.loader.launch.JarLauncher",
        "Start-Class: com.medkernel.MedKernelApplication",
        "",
      ].join("\n"),
      "utf8",
    );
    runFixtureCommand(
      "jar",
      ["cfm", artifactPath, manifestPath, "-C", stagingRoot, "."],
      "创建 JAR 夹具",
    );
    return artifactPath;
  }

  let archiveRoot;
  if (artifactId === "FRONTEND_DIST") {
    archiveRoot = "dist";
    writeFile(
      stagingRoot,
      "dist/index.html",
      '<!doctype html><main></main><script type="module" src="/assets/index-a1b2c3.js"></script>\n',
    );
    writeFile(
      stagingRoot,
      "dist/assets/index-a1b2c3.js",
      "export const app=true;\n",
    );
    writeFile(stagingRoot, "dist/medkernel-build.json", metadata);
  } else if (artifactId === "CLI_PACKAGE") {
    archiveRoot = "package";
    cpSync(path.join(repoRoot, "cli"), path.join(stagingRoot, archiveRoot), {
      recursive: true,
    });
    writeFile(stagingRoot, "package/medkernel-build.json", metadata);
  } else if (artifactId === "MCP_PACKAGE") {
    archiveRoot = "package";
    cpSync(
      path.join(repoRoot, "mcp-server"),
      path.join(stagingRoot, archiveRoot),
      { recursive: true },
    );
    writeFile(stagingRoot, "package/medkernel-build.json", metadata);
  } else if (artifactId === "DATABASE_MIGRATIONS") {
    archiveRoot = "db";
    cpSync(
      path.join(repoRoot, "medkernel-backend/src/main/resources/db"),
      path.join(stagingRoot, archiveRoot),
      { recursive: true },
    );
    writeFile(stagingRoot, "db/medkernel-build.json", metadata);
  } else if (artifactId === "ONPREM_DELIVERY") {
    archiveRoot = "onprem";
    cpSync(
      path.join(repoRoot, "deploy/onprem"),
      path.join(stagingRoot, archiveRoot),
      { recursive: true },
    );
    writeFile(stagingRoot, "onprem/medkernel-build.json", metadata);
  } else {
    throw new Error(`未知制品夹具：${artifactId}`);
  }
  runFixtureCommand(
    "tar",
    ["-czf", artifactPath, "-C", stagingRoot, archiveRoot],
    `创建 ${artifactId} 夹具`,
  );
  return artifactPath;
}

function artifactCommands(artifactId) {
  return [
    `cd . && CI=true node scripts/release/rc-artifact-builder.mjs --artifact-id ${artifactId} --repo-root <repo> --bundle-root <bundle> --run-root <run> --run-id <run-id> --candidate-commit <candidate-commit>`,
  ];
}

function runFixtureCommand(command, args, label) {
  const result = spawnSync(command, args, {
    encoding: "utf8",
    shell: false,
  });
  if (result.status !== 0) {
    throw new Error(`${label}失败：${result.stderr || result.stdout}`);
  }
}

function sha256Fixture(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function createFixtureManifest(fixture, overrides = {}) {
  return createRcManifest({
    repoRoot: fixture.repoRoot,
    bundleRoot: fixture.bundleRoot,
    sourceBaseCommit: fixture.sourceBaseCommit,
    candidateCommit: fixture.candidateCommit,
    runId: RUN_ID,
    runStartedAt: RUN_STARTED_AT,
    generatedAt: GENERATED_AT,
    mavenResolutionPath: fixture.mavenResolutionPath,
    dependencyEvidencePath: fixture.dependencyEvidencePath,
    gates: fixture.gates,
    artifacts: fixture.artifacts,
    ...overrides,
  });
}

function replaceEvidence(fixture, gateId, overrides) {
  const gate = fixture.gates.find((item) => item.gateId === gateId);
  const payload = readEvidence(fixture, gateId);
  writeFileSync(
    gate.evidencePath,
    `${JSON.stringify({ ...payload, ...overrides })}\n`,
    "utf8",
  );
}

function readEvidence(fixture, gateId) {
  const gate = fixture.gates.find((item) => item.gateId === gateId);
  return JSON.parse(readFileSync(gate.evidencePath, "utf8"));
}

function refreshArtifactProvenanceSubject(artifact) {
  const provenance = JSON.parse(readFileSync(artifact.provenancePath, "utf8"));
  const bytes = readFileSync(artifact.path);
  provenance.subject.size = bytes.byteLength;
  provenance.subject.sha256 = sha256Fixture(bytes);
  writeFileSync(
    artifact.provenancePath,
    `${JSON.stringify(provenance)}\n`,
    "utf8",
  );
}

function deleteEvidenceField(fixture, gateId, field) {
  const gate = fixture.gates.find((item) => item.gateId === gateId);
  const payload = JSON.parse(readFileSync(gate.evidencePath, "utf8"));
  delete payload[field];
  writeFileSync(gate.evidencePath, `${JSON.stringify(payload)}\n`, "utf8");
}

function writeFile(root, relativePath, contents) {
  const target = path.join(root, relativePath);
  mkdirSync(path.dirname(target), { recursive: true });
  writeFileSync(target, contents, "utf8");
}

function git(cwd, ...args) {
  const result = spawnSync("git", args, {
    cwd,
    encoding: "utf8",
    shell: false,
  });
  if (result.status !== 0) {
    throw new Error(
      `git ${args.join(" ")} 失败：${result.stderr || result.stdout}`,
    );
  }
  return result.stdout.trim();
}
