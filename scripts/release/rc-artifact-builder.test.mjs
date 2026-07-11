import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, test } from "node:test";

import { getRcEvidenceContract } from "./rc-manifest-lib.mjs";

const PROJECT_ROOT = fileURLToPath(new URL("../..", import.meta.url));
const BUILDER_CLI = fileURLToPath(
  new URL("./rc-artifact-builder.mjs", import.meta.url),
);
const RUN_ID = "rc-builder-20260711-001";
const temporaryRoots = [];

afterEach(() => {
  while (temporaryRoots.length > 0) {
    rmSync(temporaryRoots.pop(), { recursive: true, force: true });
  }
});

test("六类制品合同统一指向候选提交导出构建器", () => {
  const contract = getRcEvidenceContract();
  for (const [artifactId, artifact] of Object.entries(contract.artifacts)) {
    assert.equal(artifact.commands.length, 1);
    assert.match(
      artifact.commands[0],
      new RegExp(
        `rc-artifact-builder\\.mjs.*--artifact-id ${artifactId}.*--candidate-commit <candidate-commit>`,
        "u",
      ),
    );
  }
});

test("构建器从精确候选导出并真实生成前端、CLI、MCP、迁移和院内交付制品", () => {
  const fixture = createBuilderFixture();
  const expected = {
    FRONTEND_DIST: [
      "dist/index.html",
      "dist/assets/index-fixture123.js",
      "dist/medkernel-build.json",
    ],
    CLI_PACKAGE: [
      "package/package.json",
      "package/src/cli.mjs",
      "package/medkernel-build.json",
    ],
    MCP_PACKAGE: [
      "package/package.json",
      "package/src/server.mjs",
      "package/medkernel-build.json",
    ],
    DATABASE_MIGRATIONS: [
      "db/schema/medkernel.schema.json",
      "db/migration/postgres/V1__baseline.sql",
      "db/medkernel-build.json",
    ],
    ONPREM_DELIVERY: [
      "onprem/README.md",
      "onprem/medkernel-deploy.sh",
      "onprem/mk-publish.ps1",
      "onprem/medkernel-build.json",
    ],
  };

  for (const [artifactId, requiredEntries] of Object.entries(expected)) {
    const result = runBuilder(fixture, artifactId);
    assert.equal(result.status, 0, result.stderr || result.stdout);
    const descriptor = JSON.parse(result.stdout);
    assert.equal(descriptor.status, "ARTIFACT_BUILT");
    assert.equal(descriptor.artifactId, artifactId);
    const artifactPath = path.join(fixture.bundleRoot, descriptor.path);
    const listing = run("tar", ["-tzf", artifactPath], PROJECT_ROOT)
      .stdout.split(/\r?\n/u)
      .filter(Boolean)
      .map((entry) => entry.replace(/\/$/u, ""));
    for (const entry of requiredEntries) {
      assert.ok(listing.includes(entry), `${artifactId} 缺少 ${entry}`);
    }
    if (artifactId === "ONPREM_DELIVERY") {
      const packagedPowerShell = run(
        "tar",
        ["-xOzf", artifactPath, "onprem/mk-publish.ps1"],
        PROJECT_ROOT,
      ).stdout;
      const candidateBlob = run(
        "git",
        ["show", `${fixture.candidateCommit}:deploy/onprem/mk-publish.ps1`],
        fixture.repoRoot,
      ).stdout;
      assert.match(packagedPowerShell, /\r\n/u);
      assert.doesNotMatch(candidateBlob, /\r\n/u);
    }
    const metadataPath = requiredEntries.at(-1);
    const metadata = JSON.parse(
      run("tar", ["-xOzf", artifactPath, metadataPath], PROJECT_ROOT).stdout,
    );
    assert.deepEqual(metadata, {
      artifactId,
      candidateCommit: fixture.candidateCommit,
      kind: "MEDKERNEL_BUILD_METADATA",
      schemaVersion: "1.0.0",
    });
    const provenance = JSON.parse(
      readFileSync(
        path.join(fixture.bundleRoot, descriptor.provenancePath),
        "utf8",
      ),
    );
    assert.equal(provenance.subject.path, descriptor.path);
    assert.equal(provenance.candidateCommit, fixture.candidateCommit);
  }
});

test("构建器拒绝覆盖既有制品并拒绝 HEAD 之外的候选", () => {
  const fixture = createBuilderFixture();
  const first = runBuilder(fixture, "CLI_PACKAGE");
  assert.equal(first.status, 0, first.stderr || first.stdout);

  const overwrite = runBuilder(fixture, "CLI_PACKAGE");
  assert.notEqual(overwrite.status, 0);
  assert.match(overwrite.stderr, /已存在|拒绝覆盖/u);

  const wrong = runBuilder(fixture, "MCP_PACKAGE", {
    candidateCommit: "0".repeat(40),
  });
  assert.notEqual(wrong.status, 0);
  assert.match(wrong.stderr, /候选提交不存在|HEAD/u);
});

test("构建器拒绝运行目录与仓库或证据目录形成祖先重叠", () => {
  const fixture = createBuilderFixture();
  const result = runBuilder(fixture, "CLI_PACKAGE", {
    runRoot: fixture.root,
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /不得与受保护目录重叠|受保护目录外/u);
});

function runBuilder(fixture, artifactId, overrides = {}) {
  return spawnSync(
    process.execPath,
    [
      BUILDER_CLI,
      "--artifact-id",
      artifactId,
      "--repo-root",
      fixture.repoRoot,
      "--bundle-root",
      fixture.bundleRoot,
      "--run-root",
      overrides.runRoot ?? fixture.runRoot,
      "--run-id",
      RUN_ID,
      "--candidate-commit",
      overrides.candidateCommit ?? fixture.candidateCommit,
    ],
    { cwd: PROJECT_ROOT, encoding: "utf8", shell: false },
  );
}

function createBuilderFixture() {
  const root = mkdtempSync(path.join(os.tmpdir(), "medkernel-rc-builder-"));
  temporaryRoots.push(root);
  const repoRoot = path.join(root, "repo");
  const bundleRoot = path.join(root, "bundle");
  const runRoot = path.join(root, "run");
  mkdirSync(repoRoot, { recursive: true });
  mkdirSync(bundleRoot, { recursive: true });
  mkdirSync(runRoot, { recursive: true });
  run("git", ["init", "-q"], repoRoot);
  run("git", ["config", "user.name", "MedKernel Test"], repoRoot);
  run("git", ["config", "user.email", "test@medkernel.invalid"], repoRoot);
  write(
    repoRoot,
    ".gitattributes",
    "* text=auto eol=lf\n*.ps1 text eol=crlf\n",
  );
  write(
    repoRoot,
    "frontend/package.json",
    `${JSON.stringify({
      name: "fixture-frontend",
      private: true,
      scripts: { build: "node build.mjs" },
    })}\n`,
  );
  write(
    repoRoot,
    "frontend/build.mjs",
    [
      'import { mkdirSync, writeFileSync } from "node:fs";',
      'mkdirSync("dist/assets", { recursive: true });',
      'writeFileSync("dist/index.html", \'<script type="module" src="/assets/index-fixture123.js"></script>\\n\');',
      'writeFileSync("dist/assets/index-fixture123.js", "export const fixture=true;\\n");',
      "",
    ].join("\n"),
  );
  write(
    repoRoot,
    "cli/package.json",
    '{"name":"@medkernel/cli","version":"0.1.0","private":true,"bin":{"medkernel":"./src/cli.mjs"}}\n',
  );
  write(repoRoot, "cli/src/cli.mjs", "export const cli=true;\n");
  write(
    repoRoot,
    "mcp-server/package.json",
    '{"name":"@medkernel/mcp-server","version":"0.1.0","private":true,"bin":{"medkernel-mcp":"./src/server.mjs"}}\n',
  );
  write(repoRoot, "mcp-server/src/server.mjs", "export const server=true;\n");
  write(
    repoRoot,
    "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json",
    '{"schemaVersion":"fixture"}\n',
  );
  for (const dialect of ["h2", "postgres", "oracle", "dm", "kingbase"]) {
    write(
      repoRoot,
      `medkernel-backend/src/main/resources/db/migration/${dialect}/V1__baseline.sql`,
      `-- ${dialect}\nSELECT 1;\n`,
    );
  }
  write(repoRoot, "deploy/onprem/README.md", "# fixture\n");
  write(
    repoRoot,
    "deploy/onprem/medkernel-deploy.sh",
    "#!/usr/bin/env bash\nset -euo pipefail\n",
  );
  write(
    repoRoot,
    "deploy/onprem/mk-publish.ps1",
    "\uFEFF<#\n.SYNOPSIS\n候选发布入口\n#>\nWrite-Output 'fixture'\n",
  );
  run("git", ["add", "-A"], repoRoot);
  run("git", ["commit", "-q", "-m", "候选"], repoRoot);
  return {
    root,
    repoRoot,
    bundleRoot,
    runRoot,
    candidateCommit: run("git", ["rev-parse", "HEAD"], repoRoot).stdout.trim(),
  };
}

function write(root, relativePath, contents) {
  const target = path.join(root, relativePath);
  mkdirSync(path.dirname(target), { recursive: true });
  writeFileSync(target, contents, "utf8");
}

function run(command, args, cwd) {
  const result = spawnSync(command, args, {
    cwd,
    encoding: "utf8",
    shell: false,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(
      `${command} ${args.join(" ")} 失败：${result.stderr || result.stdout}`,
    );
  }
  return result;
}
