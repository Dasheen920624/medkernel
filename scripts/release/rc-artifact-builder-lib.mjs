import { spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import {
  constants,
  copyFileSync,
  closeSync,
  existsSync,
  fsyncSync,
  linkSync,
  lstatSync,
  mkdirSync,
  openSync,
  readFileSync,
  readdirSync,
  realpathSync,
  renameSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";

import {
  createArtifactBuildMetadata,
  createArtifactProvenance,
  getRcEvidenceContract,
  serializeRcManifest,
} from "./rc-manifest-lib.mjs";

const ARTIFACT_FILES = Object.freeze({
  BACKEND_JAR: "backend.jar",
  FRONTEND_DIST: "frontend-dist.tar.gz",
  CLI_PACKAGE: "cli-package.tgz",
  MCP_PACKAGE: "mcp-package.tgz",
  DATABASE_MIGRATIONS: "database-migrations.tar.gz",
  ONPREM_DELIVERY: "onprem-delivery.tar.gz",
});

/**
 * 从候选提交的 Git 对象导出全新源码，真实构建并形成内嵌提交元数据的不可变制品。
 */
export function buildRcArtifact(options = {}) {
  const artifactId = requireArtifactId(options.artifactId);
  const repoRoot = requireDirectory(options.repoRoot, "repoRoot");
  const bundleRoot = requireDirectory(options.bundleRoot, "bundleRoot");
  const runRoot = requireDirectory(options.runRoot, "runRoot");
  assertOutside(repoRoot, bundleRoot, "bundleRoot");
  assertOutside(repoRoot, runRoot, "runRoot");
  assertOutside(bundleRoot, runRoot, "runRoot");
  const runId = requireText(options.runId, "runId");
  if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/u.test(runId)) {
    throw new Error("runId 格式非法");
  }
  const candidateCommit = requireText(
    options.candidateCommit,
    "candidateCommit",
  );
  if (!/^[a-f0-9]{40}$/u.test(candidateCommit)) {
    throw new Error("候选提交必须是完整 40 位哈希");
  }
  assertCandidate(repoRoot, candidateCommit);

  const relativeArtifactPath = `artifacts/${ARTIFACT_FILES[artifactId]}`;
  const relativeBuildLogPath = `evidence/logs/artifact-${artifactId.toLowerCase()}.log`;
  const relativeProvenancePath = `evidence/provenance/${artifactId.toLowerCase()}.json`;
  const artifactPath = path.join(bundleRoot, relativeArtifactPath);
  const buildLogPath = path.join(bundleRoot, relativeBuildLogPath);
  const provenancePath = path.join(bundleRoot, relativeProvenancePath);
  for (const target of [artifactPath, buildLogPath, provenancePath]) {
    if (existsSync(target)) throw new Error(`输出已存在，拒绝覆盖：${target}`);
    mkdirSync(path.dirname(target), { recursive: true });
  }

  const stagingRoot = path.join(
    runRoot,
    "artifact-staging",
    artifactId.toLowerCase(),
  );
  if (existsSync(stagingRoot)) {
    throw new Error(`制品暂存目录已存在，拒绝复用：${stagingRoot}`);
  }
  const sourceRoot = path.join(stagingRoot, "source");
  mkdirSync(sourceRoot, { recursive: true });
  const contractCommand =
    getRcEvidenceContract().artifacts[artifactId].commands[0];
  const startedAt = new Date().toISOString();
  const nativeLog = [];

  try {
    exportCandidate(repoRoot, candidateCommit, sourceRoot, nativeLog);
    const metadata = `${serializeRcManifest(
      createArtifactBuildMetadata({ artifactId, candidateCommit }),
    )}`;
    const stagedArtifactPath = path.join(
      stagingRoot,
      ARTIFACT_FILES[artifactId],
    );
    buildArtifact({
      artifactId,
      repoRoot,
      runRoot,
      sourceRoot,
      stagingRoot,
      stagedArtifactPath,
      metadata,
      nativeLog,
    });
    atomicCopyNoReplace(stagedArtifactPath, artifactPath);
    const finishedAt = new Date().toISOString();
    const buildLog = [
      `runId=${runId}`,
      `candidateCommit=${candidateCommit}`,
      `startedAt=${startedAt}`,
      `command=${contractCommand}`,
      ...nativeLog,
      `artifactId=${artifactId}`,
      "result=PASSED",
      "exitCode=0",
      `completedAt=${finishedAt}`,
      "",
    ].join("\n");
    atomicWriteNoReplace(buildLogPath, buildLog);
    const provenance = createArtifactProvenance({
      repoRoot,
      bundleRoot,
      artifactId,
      artifactPath,
      buildLogPath,
      runId,
      candidateCommit,
      startedAt,
      finishedAt,
    });
    atomicWriteNoReplace(provenancePath, serializeRcManifest(provenance));
    return {
      status: "ARTIFACT_BUILT",
      artifactId,
      candidateCommit,
      path: normalizeRelative(relativeArtifactPath),
      provenancePath: normalizeRelative(relativeProvenancePath),
      buildLogPath: normalizeRelative(relativeBuildLogPath),
      startedAt,
      finishedAt,
    };
  } catch (error) {
    if (existsSync(artifactPath) && !existsSync(provenancePath)) {
      rmSync(artifactPath, { force: true });
    }
    throw error;
  }
}

function buildArtifact({
  artifactId,
  repoRoot,
  runRoot,
  sourceRoot,
  stagingRoot,
  stagedArtifactPath,
  metadata,
  nativeLog,
}) {
  if (artifactId === "BACKEND_JAR") {
    const backendRoot = path.join(sourceRoot, "medkernel-backend");
    runNative(
      "mvn",
      [
        "-B",
        "-q",
        `-Dmaven.repo.local=${path.join(runRoot, "m2repo")}`,
        "clean",
        "package",
        "-DskipTests",
      ],
      backendRoot,
      nativeLog,
    );
    const executableJars = readdirSync(path.join(backendRoot, "target"))
      .filter(
        (name) =>
          name.endsWith(".jar") &&
          !name.endsWith(".jar.original") &&
          !/-sources\.jar$|-javadoc\.jar$/u.test(name),
      )
      .map((name) => path.join(backendRoot, "target", name))
      .filter((candidate) => jarContainsApplication(candidate));
    if (executableJars.length !== 1) {
      throw new Error(
        `后端构建必须恰生成一个可执行 Spring Boot JAR，实际 ${executableJars.length}`,
      );
    }
    copyFileSync(executableJars[0], stagedArtifactPath);
    const metadataRoot = path.join(stagingRoot, "backend-metadata");
    write(metadataRoot, "META-INF/medkernel-build.json", metadata);
    runNative(
      "jar",
      [
        "uf",
        stagedArtifactPath,
        "-C",
        metadataRoot,
        "META-INF/medkernel-build.json",
      ],
      repoRoot,
      nativeLog,
    );
    return;
  }

  if (artifactId === "FRONTEND_DIST") {
    const frontendRoot = path.join(sourceRoot, "frontend");
    const installedModules = path.join(repoRoot, "frontend/node_modules");
    const stagedModules = path.join(frontendRoot, "node_modules");
    if (existsSync(installedModules) && !existsSync(stagedModules)) {
      symlinkSync(installedModules, stagedModules, "dir");
      nativeLog.push(`dependencySource=${normalizeRelative(installedModules)}`);
    }
    runNative("npm", ["run", "build"], frontendRoot, nativeLog);
    const distRoot = path.join(frontendRoot, "dist");
    if (!existsSync(path.join(distRoot, "index.html"))) {
      throw new Error("前端构建未生成 dist/index.html");
    }
    writeFileSync(path.join(distRoot, "medkernel-build.json"), metadata);
    runNative(
      "tar",
      ["-czf", stagedArtifactPath, "-C", frontendRoot, "dist"],
      repoRoot,
      nativeLog,
    );
    return;
  }

  if (["CLI_PACKAGE", "MCP_PACKAGE"].includes(artifactId)) {
    const packageDirectory =
      artifactId === "CLI_PACKAGE" ? "cli" : "mcp-server";
    const packageRoot = path.join(sourceRoot, packageDirectory);
    writeFileSync(path.join(packageRoot, "medkernel-build.json"), metadata);
    const packRoot = path.join(stagingRoot, "npm-pack");
    mkdirSync(packRoot, { recursive: true });
    const result = runNative(
      "npm",
      ["pack", "--json", "--pack-destination", packRoot],
      packageRoot,
      nativeLog,
    );
    let packed;
    try {
      packed = JSON.parse(result.stdout)?.[0]?.filename;
    } catch {
      throw new Error(`${artifactId} 的 npm pack 未返回有效 JSON`);
    }
    if (typeof packed !== "string" || !packed.endsWith(".tgz")) {
      throw new Error(`${artifactId} 的 npm pack 未返回制品文件名`);
    }
    renameSync(path.join(packRoot, packed), stagedArtifactPath);
    return;
  }

  if (artifactId === "DATABASE_MIGRATIONS") {
    const resourcesRoot = path.join(
      sourceRoot,
      "medkernel-backend/src/main/resources",
    );
    writeFileSync(
      path.join(resourcesRoot, "db/medkernel-build.json"),
      metadata,
    );
    runNative(
      "tar",
      ["-czf", stagedArtifactPath, "-C", resourcesRoot, "db"],
      repoRoot,
      nativeLog,
    );
    return;
  }

  const deployRoot = path.join(sourceRoot, "deploy");
  writeFileSync(path.join(deployRoot, "onprem/medkernel-build.json"), metadata);
  runNative(
    "tar",
    ["-czf", stagedArtifactPath, "-C", deployRoot, "onprem"],
    repoRoot,
    nativeLog,
  );
}

function exportCandidate(repoRoot, candidateCommit, sourceRoot, nativeLog) {
  const exported = spawnSync(
    "git",
    ["archive", "--format=tar", candidateCommit],
    {
      cwd: repoRoot,
      encoding: null,
      shell: false,
      maxBuffer: 1024 * 1024 * 1024,
    },
  );
  if (exported.status !== 0) {
    throw new Error(
      `导出候选提交失败：${exported.stderr?.toString("utf8") || "未知错误"}`,
    );
  }
  const extracted = spawnSync("tar", ["-xf", "-", "-C", sourceRoot], {
    input: exported.stdout,
    encoding: "utf8",
    shell: false,
    maxBuffer: 64 * 1024 * 1024,
  });
  if (extracted.status !== 0) {
    throw new Error(
      `展开候选提交失败：${extracted.stderr || extracted.stdout}`,
    );
  }
  nativeLog.push(`sourceExportCommit=${candidateCommit}`);
}

function runNative(command, args, cwd, nativeLog) {
  nativeLog.push(
    `nativeCommand=${command} ${args.map(shellDisplay).join(" ")}`,
  );
  const result = spawnSync(command, args, {
    cwd,
    env: { ...process.env, CI: "true" },
    encoding: "utf8",
    shell: false,
    maxBuffer: 256 * 1024 * 1024,
  });
  if (result.stdout) nativeLog.push(result.stdout.trimEnd());
  if (result.stderr) nativeLog.push(result.stderr.trimEnd());
  nativeLog.push(`nativeExitCode=${result.status ?? 1}`);
  if (result.status !== 0) {
    throw new Error(
      `${command} 执行失败（exit=${result.status ?? "unknown"}）：${result.stderr || result.stdout}`,
    );
  }
  return result;
}

function jarContainsApplication(candidate) {
  const result = spawnSync("jar", ["tf", candidate], {
    encoding: "utf8",
    shell: false,
  });
  return (
    result.status === 0 &&
    result.stdout
      .split(/\r?\n/u)
      .includes("BOOT-INF/classes/com/medkernel/MedKernelApplication.class")
  );
}

function assertCandidate(repoRoot, candidateCommit) {
  const exists = spawnSync(
    "git",
    ["cat-file", "-e", `${candidateCommit}^{commit}`],
    { cwd: repoRoot, encoding: "utf8", shell: false },
  );
  if (exists.status !== 0) throw new Error("候选提交不存在");
  const head = git(repoRoot, ["rev-parse", "HEAD"]).trim();
  if (head !== candidateCommit) {
    throw new Error("候选提交与当前 HEAD 不一致");
  }
  const dirty = git(repoRoot, [
    "status",
    "--porcelain=v1",
    "--untracked-files=all",
  ]);
  if (dirty) throw new Error("候选工作区存在 tracked 或未跟踪修改");
}

function git(repoRoot, args) {
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false,
  });
  if (result.status !== 0) {
    throw new Error(`Git 操作失败：${result.stderr || result.stdout}`);
  }
  return result.stdout;
}

function atomicCopyNoReplace(sourcePath, outputPath) {
  const temporaryPath = temporarySibling(outputPath);
  try {
    copyFileSync(sourcePath, temporaryPath, constants.COPYFILE_EXCL);
    const descriptor = openSync(temporaryPath, "r");
    fsyncSync(descriptor);
    closeSync(descriptor);
    publishTemporary(temporaryPath, outputPath);
  } finally {
    if (existsSync(temporaryPath)) unlinkSync(temporaryPath);
  }
}

function atomicWriteNoReplace(outputPath, contents) {
  const temporaryPath = temporarySibling(outputPath);
  let descriptor;
  try {
    descriptor = openSync(temporaryPath, "wx", 0o600);
    writeFileSync(descriptor, contents, "utf8");
    fsyncSync(descriptor);
    closeSync(descriptor);
    descriptor = undefined;
    publishTemporary(temporaryPath, outputPath);
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
    if (existsSync(temporaryPath)) unlinkSync(temporaryPath);
  }
}

function publishTemporary(temporaryPath, outputPath) {
  try {
    linkSync(temporaryPath, outputPath);
  } catch (error) {
    if (error?.code === "EEXIST") {
      throw new Error(`输出已存在，拒绝覆盖：${outputPath}`);
    }
    throw error;
  }
}

function temporarySibling(outputPath) {
  return path.join(
    path.dirname(outputPath),
    `.${path.basename(outputPath)}.tmp-${process.pid}-${randomUUID()}`,
  );
}

function write(root, relativePath, contents) {
  const target = path.join(root, relativePath);
  mkdirSync(path.dirname(target), { recursive: true });
  writeFileSync(target, contents, "utf8");
}

function requireArtifactId(value) {
  const artifactId = requireText(value, "artifactId");
  if (!Object.hasOwn(ARTIFACT_FILES, artifactId)) {
    throw new Error(`不支持的制品类型：${artifactId}`);
  }
  return artifactId;
}

function requireDirectory(value, label) {
  const absolutePath = path.resolve(requireText(value, label));
  if (!existsSync(absolutePath)) throw new Error(`${label} 不存在`);
  const stats = lstatSync(absolutePath);
  if (stats.isSymbolicLink() || !stats.isDirectory()) {
    throw new Error(`${label} 必须是非符号链接目录`);
  }
  return realpathSync(absolutePath);
}

function assertOutside(protectedRoot, candidateRoot, label) {
  const candidateWithinProtected = path.relative(protectedRoot, candidateRoot);
  const protectedWithinCandidate = path.relative(candidateRoot, protectedRoot);
  const isWithin = (relativePath) =>
    relativePath === "" ||
    (!relativePath.startsWith("..") && !path.isAbsolute(relativePath));
  if (
    isWithin(candidateWithinProtected) ||
    isWithin(protectedWithinCandidate)
  ) {
    throw new Error(`${label} 不得与受保护目录重叠`);
  }
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label} 不能为空`);
  }
  return value.trim();
}

function normalizeRelative(value) {
  return value.replace(/\\/gu, "/");
}

function shellDisplay(value) {
  return /^[A-Za-z0-9_./:=+-]+$/u.test(value)
    ? value
    : `'${value.replaceAll("'", "'\\''")}'`;
}
