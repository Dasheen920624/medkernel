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
const REQUIRED_RC_ARTIFACT_IDS = [
  "BACKEND_JAR",
  "FRONTEND_DIST",
  "CLI_PACKAGE",
  "MCP_PACKAGE",
  "DATABASE_MIGRATIONS",
  "ONPREM_DELIVERY",
];
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
  assert.equal(
    manifest.artifacts.files[0].path,
    `artifacts/${REQUIRED_RC_ARTIFACT_IDS[0].toLowerCase()}.tar`,
  );
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
    /Maven 本次解析报告不能为空/u,
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
    '{"name":"frontend","lockfileVersion":3}\n',
  );
  writeFile(
    repoRoot,
    "cli/package.json",
    `${options.cliPackageJson ?? '{"name":"@medkernel/cli","private":true}'}\n`,
  );
  writeFile(
    repoRoot,
    "mcp-server/package.json",
    `${options.mcpPackageJson ?? '{"name":"@medkernel/mcp-server","private":true}'}\n`,
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
    "com.medkernel:medkernel-backend:jar:0.1.0\n",
    "utf8",
  );
  const gates = REQUIRED_RC_GATES.map((gateId) => {
    const evidencePath = path.join(
      evidenceRoot,
      `${gateId.toLowerCase()}.json`,
    );
    writeFileSync(
      evidencePath,
      `${JSON.stringify({
        gateId,
        evidenceStage: "CLEAN_BASELINE",
        evidenceKey: `rc.gates.${gateId}`,
        observedCode: gateId,
        observedStatus: "PASSED",
        runId: RUN_ID,
        candidateCommit,
        observedAt: GENERATED_AT,
      })}\n`,
      "utf8",
    );
    return { gateId, evidencePath };
  });
  const artifacts = REQUIRED_RC_ARTIFACT_IDS.map((artifactId, index) => {
    const artifactPath = path.join(
      artifactRoot,
      `${artifactId.toLowerCase()}.tar`,
    );
    writeFileSync(artifactPath, `artifact-${index}-real-bytes\n`, "utf8");
    return { artifactId, path: artifactPath };
  });
  return {
    repoRoot,
    inputRoot,
    bundleRoot: inputRoot,
    sourceBaseCommit: DEFAULT_SOURCE_BASE_COMMIT,
    alternativeAncestorCommit,
    candidateCommit,
    mavenResolutionPath,
    gates,
    artifacts,
  };
}

function relocateFixtureBundle(fixture, bundleRoot) {
  const relocate = (sourcePath) =>
    path.join(bundleRoot, path.relative(fixture.bundleRoot, sourcePath));
  return {
    bundleRoot,
    mavenResolutionPath: relocate(fixture.mavenResolutionPath),
    gates: fixture.gates.map((gate) => ({
      ...gate,
      evidencePath: relocate(gate.evidencePath),
    })),
    artifacts: fixture.artifacts.map((artifact) => ({
      ...artifact,
      path: relocate(artifact.path),
    })),
  };
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
    gates: fixture.gates,
    artifacts: fixture.artifacts,
    ...overrides,
  });
}

function replaceEvidence(fixture, gateId, overrides) {
  const gate = fixture.gates.find((item) => item.gateId === gateId);
  const payload = JSON.parse(readFileSync(gate.evidencePath, "utf8"));
  writeFileSync(
    gate.evidencePath,
    `${JSON.stringify({ ...payload, ...overrides })}\n`,
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
