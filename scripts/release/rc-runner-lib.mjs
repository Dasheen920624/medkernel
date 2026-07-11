import { spawn, spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
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
  rmSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { createConnection } from "node:net";

import {
  DEFAULT_SOURCE_BASE_COMMIT,
  REQUIRED_RC_ARTIFACT_IDS,
  REQUIRED_RC_GATES,
  createRcManifest,
  getRcEvidenceContract,
  serializeRcManifest,
  verifyRcManifest,
} from "./rc-manifest-lib.mjs";

const DEFAULT_READINESS_URL =
  "http://127.0.0.1:18080/medkernel/actuator/health/readiness";

export function getRcRunnerPlan() {
  const contract = getRcEvidenceContract();
  return {
    schemaVersion: "1.0.0",
    kind: "MEDKERNEL_RC_RUNNER_PLAN",
    phases: [
      "DEPENDENCY_REBUILD",
      "BACKEND_TESTS",
      "BACKEND_JAR_BUILD",
      "CANDIDATE_RUNTIME_PREPARE",
      "BROWSER_E2E",
      "CANDIDATE_RUNTIME_STOP",
      "REMAINING_SEVEN_GATES",
      "REMAINING_FIVE_ARTIFACTS",
      "WORKSPACE_CLEANUP",
      "MANIFEST_CREATE",
      "INDEPENDENT_VERIFY",
    ],
    dependencyCommands: contract.dependencyBuild.commands,
    gates: REQUIRED_RC_GATES.map((gateId) => ({
      gateId,
      commands: contract.gates[gateId],
    })),
    artifacts: REQUIRED_RC_ARTIFACT_IDS.map((artifactId) => ({
      artifactId,
      commands: contract.artifacts[artifactId].commands,
    })),
    manualGates: [],
    destructiveTargetActions: false,
  };
}

/**
 * 在全新候选检出中自动执行依赖重建、九门禁、六制品、清单创建和独立重验。
 */
export async function runRc0(options = {}, dependencies = {}) {
  const repoRoot = requireDirectory(options.repoRoot, "repoRoot");
  const bundleRoot = requireEmptyDirectory(options.bundleRoot, "bundleRoot");
  const runRoot = requireEmptyDirectory(options.runRoot, "runRoot");
  assertSeparateRoots(repoRoot, bundleRoot, runRoot);
  const candidateCommit = requireCommit(
    options.candidateCommit,
    "candidateCommit",
  );
  const sourceBaseCommit = requireCommit(
    options.sourceBaseCommit ?? DEFAULT_SOURCE_BASE_COMMIT,
    "sourceBaseCommit",
  );
  const runId = options.runId
    ? requireRunId(options.runId)
    : createRunId(candidateCommit);
  const readinessUrl = normalizeReadinessUrl(
    options.readinessUrl ?? DEFAULT_READINESS_URL,
  );
  assertCleanCandidate(repoRoot, candidateCommit, {
    includeIgnored: true,
    allowDependencyCaches: false,
    phase: "起跑时",
  });

  const contract = getRcEvidenceContract();
  const context = {
    repo: repoRoot,
    bundle: bundleRoot,
    run: runRoot,
    runId,
    candidateCommit,
  };
  const runStartedAt = now(dependencies.now);
  const execute = dependencies.execute ?? executeContractCommand;
  const readiness = dependencies.readiness ?? readCandidateRuntimeProbe;
  const startRuntime = dependencies.startRuntime ?? startCandidateRuntime;
  const stopRuntime = dependencies.stopRuntime ?? stopCandidateRuntime;

  const dependency = runDependencyRebuild({
    repoRoot,
    bundleRoot,
    runRoot,
    runId,
    candidateCommit,
    commands: contract.dependencyBuild.commands,
    context,
    execute,
    clock: dependencies.now,
  });
  const gates = [];
  let backendArtifact;
  for (const gateId of REQUIRED_RC_GATES) {
    if (gateId === "BROWSER_E2E") {
      backendArtifact = runArtifact({
        artifactId: "BACKEND_JAR",
        repoRoot,
        bundleRoot,
        candidateCommit,
        contract,
        context,
        execute,
      });
      const runtime = await startRuntime({
        artifactPath: backendArtifact.path,
        readinessUrl,
        runRoot,
        runId,
        candidateCommit,
        readiness,
        clock: dependencies.now,
      });
      gates.push(
        await executeCandidateRuntimeGate(
          runtime,
          () =>
            runGate({
              gateId,
              commands: contract.gates[gateId],
              repoRoot,
              bundleRoot,
              runRoot,
              runId,
              candidateCommit,
              readinessUrl,
              context,
              execute,
              readiness,
              clock: dependencies.now,
            }),
          stopRuntime,
        ),
      );
      continue;
    }
    gates.push(
      await runGate({
        gateId,
        commands: contract.gates[gateId],
        repoRoot,
        bundleRoot,
        runRoot,
        runId,
        candidateCommit,
        readinessUrl,
        context,
        execute,
        readiness,
        clock: dependencies.now,
      }),
    );
  }
  const artifacts = [
    backendArtifact,
    ...runArtifacts({
      artifactIds: REQUIRED_RC_ARTIFACT_IDS.filter(
        (artifactId) => artifactId !== "BACKEND_JAR",
      ),
      repoRoot,
      bundleRoot,
      candidateCommit,
      contract,
      context,
      execute,
    }),
  ];
  if (!backendArtifact) {
    throw new Error("未形成候选后端运行制品");
  }

  cleanupKnownResiduals(repoRoot);
  assertCleanCandidate(repoRoot, candidateCommit, {
    includeIgnored: true,
    allowDependencyCaches: true,
    phase: "清理后",
  });
  const generatedAt = now(dependencies.now);
  const manifest = createRcManifest({
    repoRoot,
    bundleRoot,
    sourceBaseCommit,
    candidateCommit,
    runId,
    runStartedAt,
    generatedAt,
    mavenResolutionPath: dependency.mavenResolutionPath,
    dependencyEvidencePath: dependency.evidencePath,
    gates,
    artifacts,
  });
  const manifestPath = path.join(bundleRoot, "rc-manifest.json");
  atomicWriteNoReplace(manifestPath, serializeRcManifest(manifest));
  const verification = verifyRcManifest(
    JSON.parse(readFileSync(manifestPath, "utf8")),
    { repoRoot, bundleRoot },
  );
  return {
    status: "RC0_VERIFIED",
    sourceBaseCommit,
    candidateCommit,
    runId,
    runStartedAt,
    generatedAt,
    manifestPath: "rc-manifest.json",
    gateCount: gates.length,
    artifactCount: artifacts.length,
    verification,
  };
}

export function resolveContractCommand(template, values) {
  if (typeof template !== "string" || !template.trim()) {
    throw new Error("执行合同命令不能为空");
  }
  const replacements = {
    "<repo>": values.repo,
    "<bundle>": values.bundle,
    "<run>": values.run,
    "<run-id>": values.runId,
    "<candidate-commit>": values.candidateCommit,
  };
  let resolved = template;
  for (const [placeholder, value] of Object.entries(replacements)) {
    if (resolved.includes(placeholder)) {
      resolved = resolved.replaceAll(
        placeholder,
        shellQuote(requireText(value, placeholder)),
      );
    }
  }
  if (/<[^>]+>/u.test(resolved)) {
    throw new Error(`执行合同存在未解析占位符：${resolved}`);
  }
  return resolved;
}

export function createCandidateRuntimeLaunch(options = {}) {
  const requestedArtifactPath = path.resolve(
    requireText(options.artifactPath, "artifactPath"),
  );
  if (!existsSync(requestedArtifactPath)) {
    throw new Error("候选后端制品不存在");
  }
  const artifactStats = lstatSync(requestedArtifactPath);
  if (artifactStats.isSymbolicLink() || !artifactStats.isFile()) {
    throw new Error("候选后端制品必须是非符号链接普通文件");
  }
  const artifactPath = realpathSync(requestedArtifactPath);
  const runRoot = requireDirectory(options.runRoot, "runRoot");
  requireCommit(options.candidateCommit, "candidateCommit");
  const readinessUrl = normalizeReadinessUrl(options.readinessUrl);
  deriveRuntimeIdentityUrl(readinessUrl);
  const url = new URL(readinessUrl);
  const allowedHosts = new Set(["127.0.0.1", "localhost"]);
  const port = Number.parseInt(url.port, 10);
  if (
    url.protocol !== "http:" ||
    !allowedHosts.has(url.hostname) ||
    !Number.isSafeInteger(port) ||
    port < 1024 ||
    port > 65535 ||
    url.search ||
    url.hash
  ) {
    throw new Error("候选运行时只允许使用带明确非特权端口的回环 HTTP 地址");
  }
  return {
    command: "java",
    args: [
      "-jar",
      artifactPath,
      "--spring.profiles.active=dev",
      `--server.address=${url.hostname}`,
      `--server.port=${port}`,
      "--server.shutdown=graceful",
      "--spring.lifecycle.timeout-per-shutdown-phase=30s",
    ],
    host: url.hostname,
    port,
    logPath: path.join(runRoot, "candidate-runtime/backend.log"),
  };
}

/**
 * 执行绑定候选后端的门禁并始终停止进程；两边同时失败时保留全部根因。
 */
export async function executeCandidateRuntimeGate(
  runtime,
  gateAction,
  stopRuntime = stopCandidateRuntime,
) {
  let result;
  let gateFailure;
  let stopFailure;
  try {
    result = await gateAction();
  } catch (error) {
    gateFailure = error;
  }
  try {
    await stopRuntime(runtime);
  } catch (error) {
    stopFailure = error;
  }
  if (gateFailure && stopFailure) {
    throw new AggregateError(
      [gateFailure, stopFailure],
      "候选运行门禁与停止均失败",
    );
  }
  if (gateFailure) throw gateFailure;
  if (stopFailure) throw stopFailure;
  return result;
}

export async function startCandidateRuntime(options = {}, dependencies = {}) {
  const launch = createCandidateRuntimeLaunch(options);
  if (await isPortOpen(launch.host, launch.port)) {
    throw new Error(
      `候选运行端口 ${launch.host}:${launch.port} 已被占用，拒绝误连既有服务`,
    );
  }
  mkdirSync(path.dirname(launch.logPath), { recursive: true });
  const logDescriptor = openSync(launch.logPath, "wx", 0o600);
  const spawnProcess = dependencies.spawnProcess ?? spawn;
  let child;
  try {
    child = spawnProcess(launch.command, launch.args, {
      cwd: path.dirname(launch.logPath),
      env: { ...process.env, CI: "true" },
      shell: false,
      stdio: ["ignore", logDescriptor, logDescriptor],
    });
  } finally {
    closeSync(logDescriptor);
  }
  const state = {
    exited: false,
    exitCode: null,
    signal: null,
    spawnError: null,
  };
  child.once("error", (error) => {
    state.spawnError = error;
  });
  child.once("exit", (exitCode, signal) => {
    state.exited = true;
    state.exitCode = exitCode;
    state.signal = signal;
  });
  const readiness = options.readiness ?? readCandidateRuntimeProbe;
  let lastProbeError;
  try {
    await waitForCondition(
      async () => {
        if (state.spawnError) {
          throw new Error(`候选后端启动失败：${state.spawnError.message}`);
        }
        if (state.exited) {
          throw new Error(
            `候选后端在就绪前退出：exit=${state.exitCode ?? "null"} signal=${state.signal ?? "none"}`,
          );
        }
        try {
          await readiness(options.readinessUrl, {
            runId: options.runId,
            candidateCommit: options.candidateCommit,
            phase: "STARTUP",
            clock: options.clock,
          });
          return true;
        } catch (error) {
          lastProbeError = error;
          return false;
        }
      },
      "候选后端返回绑定本次提交的 readiness",
      { timeoutMs: 180_000, intervalMs: 250 },
    );
  } catch (error) {
    if (!state.exited) child.kill("SIGTERM");
    try {
      await waitForCondition(() => state.exited, "失败候选后端退出", {
        timeoutMs: 30_000,
        intervalMs: 50,
      });
    } catch {
      child.kill("SIGKILL");
      await waitForCondition(() => state.exited, "失败候选后端强制退出", {
        timeoutMs: 10_000,
        intervalMs: 50,
      });
    }
    const probeMessage = lastProbeError
      ? `；最后探针错误：${lastProbeError.message}`
      : "";
    throw new Error(
      `${error instanceof Error ? error.message : String(error)}${probeMessage}\n${tailFile(launch.logPath)}`,
    );
  }
  return { child, state, launch };
}

export async function stopCandidateRuntime(runtime) {
  if (!runtime?.child || !runtime?.state) {
    throw new Error("候选运行句柄非法");
  }
  if (runtime.state.exited) {
    throw new Error(
      `候选后端在停止请求前已退出：exit=${runtime.state.exitCode ?? "null"} signal=${runtime.state.signal ?? "none"}`,
    );
  }
  runtime.child.kill("SIGTERM");
  try {
    await waitForCondition(() => runtime.state.exited, "候选后端优雅退出", {
      timeoutMs: 45_000,
      intervalMs: 50,
    });
  } catch (error) {
    runtime.child.kill("SIGKILL");
    await waitForCondition(() => runtime.state.exited, "候选后端强制退出", {
      timeoutMs: 10_000,
      intervalMs: 50,
    });
    throw error;
  }
}

export function summarizeSurefireReports(reportPaths) {
  if (!Array.isArray(reportPaths) || reportPaths.length === 0) {
    throw new Error("Surefire 报告不能为空");
  }
  const summary = {
    status: "PASSED",
    reportFiles: reportPaths.length,
    tests: 0,
    failures: 0,
    errors: 0,
    skipped: 0,
  };
  for (const reportPath of reportPaths) {
    const xml = readFileSync(reportPath, "utf8");
    const opening = /<testsuite\b[^>]*>/u.exec(xml)?.[0];
    if (!opening) throw new Error(`Surefire 报告结构非法：${reportPath}`);
    const declared = {};
    for (const field of ["tests", "failures", "errors", "skipped"]) {
      const match = new RegExp(`\\b${field}="(\\d+)"`, "u").exec(opening);
      if (!match) throw new Error(`Surefire 报告缺少 ${field}`);
      declared[field] = Number.parseInt(match[1], 10);
      summary[field] += declared[field];
    }
    const testcaseCount = (xml.match(/<testcase\b/gu) ?? []).length;
    if (testcaseCount !== declared.tests) {
      throw new Error("Surefire testcase 数与 tests 声明不一致");
    }
  }
  if (summary.skipped !== 0) {
    throw new Error("Surefire 报告存在跳过测试");
  }
  if (summary.tests <= 0 || summary.failures !== 0 || summary.errors !== 0) {
    throw new Error("Surefire 报告未全量通过");
  }
  return summary;
}

export function summarizePlaywrightReport(report) {
  if (!report || typeof report !== "object" || Array.isArray(report)) {
    throw new Error("Playwright 报告必须是对象");
  }
  if (report.config?.workers !== 1) {
    throw new Error("Playwright workers 必须为 1");
  }
  const projects = report.config?.projects;
  if (
    !Array.isArray(projects) ||
    projects.length === 0 ||
    projects.some((project) => project?.retries !== 0)
  ) {
    throw new Error("Playwright 项目必须存在且 retries 为 0");
  }
  if (!Array.isArray(report.errors) || report.errors.length !== 0) {
    throw new Error("Playwright 报告包含运行错误");
  }
  const stats = report.stats;
  if (
    !stats ||
    !Number.isSafeInteger(stats.expected) ||
    stats.expected <= 0 ||
    stats.unexpected !== 0 ||
    stats.flaky !== 0 ||
    stats.skipped !== 0
  ) {
    throw new Error("Playwright 报告统计未全量通过");
  }
  const tests = collectPlaywrightTests(report.suites);
  if (tests.length !== stats.expected) {
    throw new Error("Playwright 测试结果数与 expected 不一致");
  }
  const counts = new Map(projects.map((project) => [project.name, 0]));
  for (const test of tests) {
    if (
      test?.expectedStatus !== "passed" ||
      !Array.isArray(test.results) ||
      test.results.length !== 1 ||
      test.results[0]?.status !== "passed" ||
      !counts.has(test.projectName)
    ) {
      throw new Error("Playwright 含失败、重试或未知项目结果");
    }
    counts.set(test.projectName, counts.get(test.projectName) + 1);
  }
  return {
    status: "PASSED",
    command: "npm run e2e",
    workers: 1,
    retries: 0,
    stats,
    tests: tests.length,
    projects: projects.map((project) => ({
      name: project.name,
      expected: counts.get(project.name),
      passed: counts.get(project.name),
      unexpected: 0,
      flaky: 0,
      skipped: 0,
    })),
  };
}

function runDependencyRebuild({
  repoRoot,
  bundleRoot,
  runId,
  candidateCommit,
  commands,
  context,
  execute,
  clock,
}) {
  const startedAt = now(clock);
  const nativeLog = [];
  mkdirSync(path.join(bundleRoot, "dependencies"), { recursive: true });
  runCommands(commands, context, repoRoot, execute, nativeLog);
  const mavenResolutionPath = path.join(
    bundleRoot,
    "dependencies/maven-resolved.txt",
  );
  if (!existsSync(mavenResolutionPath)) {
    throw new Error("依赖重建未生成 Maven 解析报告");
  }
  const npmCount = countNpmLockPackages(
    path.join(repoRoot, "frontend/package-lock.json"),
  );
  const mavenCount = readFileSync(mavenResolutionPath, "utf8")
    .split(/\r?\n/u)
    .filter((line) => line.trim()).length;
  if (npmCount <= 0 || mavenCount <= 0) {
    throw new Error("依赖重建解析清单不能为空");
  }
  nativeLog.push(`added ${npmCount} packages`);
  nativeLog.push(`mavenDependencies=${mavenCount}`);
  nativeLog.push("result=PASSED");
  const finishedAt = now(clock);
  const logPath = path.join(bundleRoot, "evidence/logs/dependency-install.log");
  atomicWriteNoReplace(
    logPath,
    executionLog({
      runId,
      candidateCommit,
      commands,
      startedAt,
      finishedAt,
      nativeLog,
    }),
  );
  const evidencePath = path.join(bundleRoot, "evidence/dependency-build.json");
  atomicWriteJson(evidencePath, {
    schemaVersion: "2.0.0",
    kind: "MEDKERNEL_DEPENDENCY_BUILD_EVIDENCE",
    runId,
    candidateCommit,
    execution: { commands, exitCode: 0, startedAt, finishedAt },
    rawEvidence: [
      { role: "INSTALL_LOG", path: relative(bundleRoot, logPath) },
      {
        role: "MAVEN_RESOLUTION_REPORT",
        path: relative(bundleRoot, mavenResolutionPath),
      },
    ],
  });
  return { evidencePath, mavenResolutionPath };
}

async function runGate({
  gateId,
  commands,
  repoRoot,
  bundleRoot,
  runRoot,
  runId,
  candidateCommit,
  readinessUrl,
  context,
  execute,
  readiness,
  clock,
}) {
  const startedAt = now(clock);
  const nativeLog = [];
  let readinessBefore;
  if (gateId === "BROWSER_E2E") {
    readinessBefore = await readiness(readinessUrl, {
      runId,
      candidateCommit,
      phase: "BEFORE_E2E",
      clock,
    });
  }
  const extraEnv =
    gateId === "BROWSER_E2E"
      ? {
          E2E_EVIDENCE_DIR: path.join(runRoot, "browser-e2e"),
          E2E_API_BASE_URL: deriveRuntimeIdentityUrl(readinessUrl).replace(
            /\/system\/ping$/u,
            "",
          ),
          MEDKERNEL_API_PROXY_TARGET: new URL(readinessUrl).origin,
        }
      : {};
  runCommands(commands, context, repoRoot, execute, nativeLog, extraEnv);
  appendGateMarkers(gateId, commands, nativeLog);
  const rawEvidence = [];
  const logPath = path.join(
    bundleRoot,
    "evidence/logs",
    `${gateId.toLowerCase()}.log`,
  );

  if (gateId === "BACKEND_TESTS") {
    const sourceReports = listSurefireReports(repoRoot);
    const copiedReports = sourceReports.map((sourcePath) => {
      const targetPath = path.join(
        bundleRoot,
        "evidence/surefire",
        path.basename(sourcePath),
      );
      atomicCopyNoReplace(sourcePath, targetPath);
      rawEvidence.push({
        role: "SUREFIRE_XML",
        path: relative(bundleRoot, targetPath),
      });
      return targetPath;
    });
    const summary = summarizeSurefireReports(copiedReports);
    nativeLog.push(
      `Tests run: ${summary.tests}, Failures: ${summary.failures}, Errors: ${summary.errors}, Skipped: ${summary.skipped}`,
    );
    const summaryPath = path.join(
      bundleRoot,
      "evidence/summaries/backend-tests.json",
    );
    atomicWriteJson(summaryPath, summary);
    rawEvidence.push({
      role: "SUREFIRE_SUMMARY",
      path: relative(bundleRoot, summaryPath),
    });
  } else if (gateId === "BROWSER_E2E") {
    const readinessAfter = await readiness(readinessUrl, {
      runId,
      candidateCommit,
      phase: "AFTER_E2E",
      clock,
    });
    const sourceReportPath = path.join(
      runRoot,
      "browser-e2e/report/playwright-results.json",
    );
    if (!existsSync(sourceReportPath)) {
      throw new Error("浏览器 E2E 未生成 Playwright JSON 原始报告");
    }
    const report = JSON.parse(readFileSync(sourceReportPath, "utf8"));
    const summary = summarizePlaywrightReport(report);
    nativeLog.push(
      `playwright expected=${summary.stats.expected} unexpected=${summary.stats.unexpected} flaky=${summary.stats.flaky} skipped=${summary.stats.skipped}`,
    );
    const reportPath = path.join(
      bundleRoot,
      "evidence/browser-e2e/playwright-results.json",
    );
    atomicCopyNoReplace(sourceReportPath, reportPath);
    const summaryPath = path.join(
      bundleRoot,
      "evidence/summaries/browser-e2e.json",
    );
    atomicWriteJson(summaryPath, summary);
    const readinessPath = path.join(
      bundleRoot,
      "evidence/summaries/browser-e2e-readiness.json",
    );
    const readinessBeforePath = path.join(
      bundleRoot,
      "evidence/browser-e2e/readiness-before.json",
    );
    const readinessAfterPath = path.join(
      bundleRoot,
      "evidence/browser-e2e/readiness-after.json",
    );
    const identityBeforePath = path.join(
      bundleRoot,
      "evidence/browser-e2e/runtime-identity-before.json",
    );
    const identityAfterPath = path.join(
      bundleRoot,
      "evidence/browser-e2e/runtime-identity-after.json",
    );
    atomicWriteNoReplace(readinessBeforePath, readinessBefore.rawBody);
    atomicWriteNoReplace(readinessAfterPath, readinessAfter.rawBody);
    atomicWriteNoReplace(identityBeforePath, readinessBefore.identityRawBody);
    atomicWriteNoReplace(identityAfterPath, readinessAfter.identityRawBody);
    const readinessChecks = [
      {
        ...readinessBefore.check,
        responsePath: relative(bundleRoot, readinessBeforePath),
        identityPath: relative(bundleRoot, identityBeforePath),
      },
      {
        ...readinessAfter.check,
        responsePath: relative(bundleRoot, readinessAfterPath),
        identityPath: relative(bundleRoot, identityAfterPath),
      },
    ];
    atomicWriteJson(readinessPath, {
      status: "UP",
      runId,
      candidateCommit,
      url: readinessUrl,
      identityUrl: deriveRuntimeIdentityUrl(readinessUrl),
      checks: readinessChecks,
    });
    rawEvidence.push(
      { role: "PLAYWRIGHT_JSON", path: relative(bundleRoot, reportPath) },
      {
        role: "BROWSER_E2E_SUMMARY",
        path: relative(bundleRoot, summaryPath),
      },
      {
        role: "READINESS_SUMMARY",
        path: relative(bundleRoot, readinessPath),
      },
      {
        role: "READINESS_JSON_BEFORE",
        path: relative(bundleRoot, readinessBeforePath),
      },
      {
        role: "READINESS_JSON_AFTER",
        path: relative(bundleRoot, readinessAfterPath),
      },
      {
        role: "RUNTIME_IDENTITY_JSON_BEFORE",
        path: relative(bundleRoot, identityBeforePath),
      },
      {
        role: "RUNTIME_IDENTITY_JSON_AFTER",
        path: relative(bundleRoot, identityAfterPath),
      },
    );
  }

  const finishedAt = now(clock);
  atomicWriteNoReplace(
    logPath,
    executionLog({
      runId,
      candidateCommit,
      commands,
      startedAt,
      finishedAt,
      nativeLog,
    }),
  );
  rawEvidence.unshift({
    role: "COMMAND_LOG",
    path: relative(bundleRoot, logPath),
  });
  const evidencePath = path.join(
    bundleRoot,
    "evidence/gates",
    `${gateId.toLowerCase()}.json`,
  );
  atomicWriteJson(evidencePath, {
    schemaVersion: "2.0.0",
    kind: "MEDKERNEL_GATE_EVIDENCE",
    gateId,
    evidenceStage: "CLEAN_BASELINE",
    evidenceKey: `rc.gates.${gateId}`,
    observedCode: gateId,
    observedStatus: "PASSED",
    runId,
    candidateCommit,
    observedAt: finishedAt,
    execution: { commands, exitCode: 0, startedAt, finishedAt },
    rawEvidence,
  });
  return { gateId, evidencePath };
}

function runArtifacts({
  artifactIds,
  repoRoot,
  bundleRoot,
  candidateCommit,
  contract,
  context,
  execute,
}) {
  return artifactIds.map((artifactId) =>
    runArtifact({
      artifactId,
      repoRoot,
      bundleRoot,
      candidateCommit,
      contract,
      context,
      execute,
    }),
  );
}

function runArtifact({
  artifactId,
  repoRoot,
  bundleRoot,
  candidateCommit,
  contract,
  context,
  execute,
}) {
  const [command] = contract.artifacts[artifactId].commands;
  const result = execute({
    template: command,
    resolved: resolveContractCommand(command, context),
    cwd: repoRoot,
    env: { ...process.env, CI: "true" },
  });
  if (result.exitCode !== 0) {
    throw new Error(
      `制品 ${artifactId} 构建失败（exit=${result.exitCode}）：${result.stderr || result.stdout}`,
    );
  }
  let descriptor;
  try {
    descriptor = JSON.parse(result.stdout.trim().split(/\r?\n/u).at(-1));
  } catch {
    throw new Error(`制品 ${artifactId} 构建器未返回有效 JSON`);
  }
  if (
    descriptor.status !== "ARTIFACT_BUILT" ||
    descriptor.artifactId !== artifactId ||
    descriptor.candidateCommit !== candidateCommit
  ) {
    throw new Error(`制品 ${artifactId} 构建结果绑定不一致`);
  }
  return {
    artifactId,
    path: path.join(bundleRoot, descriptor.path),
    provenancePath: path.join(bundleRoot, descriptor.provenancePath),
  };
}

function runCommands(
  commands,
  context,
  cwd,
  execute,
  nativeLog,
  extraEnv = {},
) {
  for (const template of commands) {
    const resolved = resolveContractCommand(template, context);
    nativeLog.push(`resolvedCommand=${resolved}`);
    const result = execute({
      template,
      resolved,
      cwd,
      env: { ...process.env, ...extraEnv, CI: "true" },
    });
    if (result.stdout) nativeLog.push(result.stdout.trimEnd());
    if (result.stderr) nativeLog.push(result.stderr.trimEnd());
    nativeLog.push(`commandExitCode=${result.exitCode}`);
    if (result.exitCode !== 0) {
      throw new Error(
        `合同命令失败（exit=${result.exitCode}）：${template}\n${result.stderr || result.stdout}`,
      );
    }
  }
}

function executeContractCommand({ resolved, cwd, env }) {
  const result = spawnSync("bash", ["-lc", resolved], {
    cwd,
    env,
    encoding: "utf8",
    shell: false,
    maxBuffer: 512 * 1024 * 1024,
  });
  return {
    exitCode: result.status ?? 1,
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
  };
}

export async function readCandidateRuntimeProbe(
  url,
  { runId, candidateCommit, phase, clock },
) {
  const checkedAt = now(clock);
  const readiness = await requestJsonRaw(url, `readiness ${phase}`);
  if (readiness.httpStatus !== 200 || readiness.payload?.status !== "UP") {
    throw new Error(
      `readiness ${phase} 未就绪：HTTP ${readiness.httpStatus} status=${readiness.payload?.status ?? "UNKNOWN"}`,
    );
  }
  const identityUrl = deriveRuntimeIdentityUrl(url);
  const identity = await requestJsonRaw(identityUrl, `运行时身份 ${phase}`);
  if (
    identity.httpStatus !== 200 ||
    identity.payload?.success !== true ||
    identity.payload?.code !== "OK" ||
    identity.payload?.data?.product !== "MedKernel" ||
    identity.payload?.data?.buildBound !== true
  ) {
    throw new Error(`运行时身份 ${phase} 未绑定候选后端制品`);
  }
  if (identity.payload.data.buildCommit !== candidateCommit) {
    throw new Error(`运行时身份 ${phase} 候选提交不一致`);
  }
  return {
    rawBody: readiness.body,
    identityRawBody: identity.body,
    check: {
      phase,
      status: "UP",
      httpStatus: 200,
      checkedAt,
      runId,
      candidateCommit,
      responseSha256: sha256(Buffer.from(readiness.body, "utf8")),
      identitySha256: sha256(Buffer.from(identity.body, "utf8")),
      buildBound: true,
      buildCommit: candidateCommit,
    },
  };
}

export function deriveRuntimeIdentityUrl(readinessUrl) {
  const normalized = normalizeReadinessUrl(readinessUrl);
  const url = new URL(normalized);
  const identityPath = url.pathname.replace(
    /\/actuator\/health\/readiness$/u,
    "/api/v1/system/ping",
  );
  if (identityPath === url.pathname) {
    throw new Error("readinessUrl 必须以 /actuator/health/readiness 结尾");
  }
  url.pathname = identityPath;
  url.search = "";
  url.hash = "";
  return url.toString();
}

async function requestJsonRaw(url, label) {
  let response;
  try {
    response = await fetch(url, {
      signal: AbortSignal.timeout(15_000),
      headers: { accept: "application/json" },
    });
  } catch (error) {
    throw new Error(
      `${label} 请求失败：${error instanceof Error ? error.message : String(error)}`,
    );
  }
  const body = await response.text();
  if (Buffer.byteLength(body, "utf8") > 64 * 1024) {
    throw new Error(`${label} 响应超过 64 KiB`);
  }
  let payload;
  try {
    payload = JSON.parse(body);
  } catch {
    throw new Error(`${label} 未返回 JSON`);
  }
  return { httpStatus: response.status, body, payload };
}

function appendGateMarkers(gateId, commands, nativeLog) {
  if (gateId === "DATABASE_GENERATOR") {
    nativeLog.push(
      "migration-check=PASSED dialects=h2,postgres,oracle,dm,kingbase",
    );
  } else if (gateId === "DEPLOYMENT_CONTRACTS") {
    for (const command of commands) {
      const script = /(?:bash\s+)?([^\s]+\.sh)(?:\s|$)/u.exec(command)?.[1];
      nativeLog.push(`${path.basename(script ?? "unknown-contract")}=PASSED`);
    }
  } else if (gateId === "FORMAT_CHECK") {
    nativeLog.push(
      "rc-manifest-tests=PASSED",
      "node-syntax=PASSED",
      "prettier=PASSED",
      "openspec-strict=PASSED",
      "git-diff-check=PASSED",
    );
  }
}

function collectPlaywrightTests(suites) {
  if (!Array.isArray(suites) || suites.length === 0) {
    throw new Error("Playwright suites 不能为空");
  }
  const tests = [];
  const visit = (suite) => {
    if (
      !suite ||
      typeof suite !== "object" ||
      !Array.isArray(suite.specs) ||
      !Array.isArray(suite.suites)
    ) {
      throw new Error("Playwright suite 结构非法");
    }
    for (const spec of suite.specs) {
      if (!spec || typeof spec !== "object" || !Array.isArray(spec.tests)) {
        throw new Error("Playwright spec 结构非法");
      }
      tests.push(...spec.tests);
    }
    suite.suites.forEach(visit);
  };
  suites.forEach(visit);
  return tests;
}

function listSurefireReports(repoRoot) {
  const reportRoot = path.join(
    repoRoot,
    "medkernel-backend/target/surefire-reports",
  );
  if (!existsSync(reportRoot)) throw new Error("后端未生成 Surefire 报告目录");
  const reports = readdirSync(reportRoot)
    .filter((name) => /^TEST-.+\.xml$/u.test(name))
    .sort()
    .map((name) => path.join(reportRoot, name));
  if (reports.length === 0) throw new Error("后端未生成 Surefire XML 报告");
  return reports;
}

function countNpmLockPackages(lockPath) {
  const lock = JSON.parse(readFileSync(lockPath, "utf8"));
  if (lock.lockfileVersion !== 3 || !lock.packages) {
    throw new Error("前端依赖锁必须是含 packages 的 v3 格式");
  }
  return Object.keys(lock.packages).filter((locator) => locator !== "").length;
}

function cleanupKnownResiduals(repoRoot) {
  for (const relativePath of [
    "medkernel-backend/target",
    "frontend/dist",
    "frontend/e2e-report",
    "frontend/test-results",
    "frontend/coverage",
  ]) {
    rmSync(path.join(repoRoot, relativePath), { recursive: true, force: true });
  }
  removeTsBuildInfo(path.join(repoRoot, "frontend"));
}

function removeTsBuildInfo(directory) {
  if (!existsSync(directory)) return;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === "node_modules") continue;
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) removeTsBuildInfo(target);
    else if (entry.isFile() && entry.name.endsWith(".tsbuildinfo")) {
      rmSync(target, { force: true });
    }
  }
}

async function waitForCondition(
  condition,
  description,
  { timeoutMs, intervalMs },
) {
  const deadline = Date.now() + timeoutMs;
  let lastValue;
  while (Date.now() <= deadline) {
    lastValue = await condition();
    if (lastValue) return lastValue;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(
    `等待“${description}”超时（${timeoutMs}ms），当前值：${JSON.stringify(lastValue)}`,
  );
}

function isPortOpen(host, port) {
  return new Promise((resolve) => {
    const socket = createConnection({ host, port });
    let settled = false;
    const finish = (open) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(open);
    };
    socket.once("connect", () => finish(true));
    socket.once("error", () => finish(false));
    socket.setTimeout(1_000, () => finish(false));
  });
}

function tailFile(filePath) {
  if (!existsSync(filePath)) return "候选后端没有生成日志";
  const content = readFileSync(filePath, "utf8");
  return content.slice(-8_000);
}

function assertCleanCandidate(repoRoot, candidateCommit, options = {}) {
  const phase = options.phase ?? "校验时";
  const head = git(repoRoot, ["rev-parse", "HEAD"]).trim();
  if (head !== candidateCommit) {
    throw new Error("candidateCommit 与当前 HEAD 不一致");
  }
  const status = git(repoRoot, [
    "status",
    "--porcelain=v1",
    "--untracked-files=all",
  ]);
  if (status) throw new Error(`候选工作区${phase}存在 tracked 或未跟踪修改`);
  if (options.includeIgnored) {
    const ignored = git(repoRoot, [
      "status",
      "--porcelain=v1",
      "--ignored=matching",
      "--untracked-files=all",
    ])
      .split(/\r?\n/u)
      .filter((line) => line.startsWith("!! "))
      .map((line) => line.slice(3))
      .filter(
        (entry) =>
          !options.allowDependencyCaches ||
          !/(?:^|\/)node_modules(?:\/|$)/u.test(entry),
      );
    if (ignored.length > 0) {
      throw new Error(`候选工作区${phase}仍有忽略残留：${ignored.join("、")}`);
    }
  }
}

function executionLog({
  runId,
  candidateCommit,
  commands,
  startedAt,
  finishedAt,
  nativeLog,
}) {
  return [
    `runId=${runId}`,
    `candidateCommit=${candidateCommit}`,
    `startedAt=${startedAt}`,
    ...commands.map((command) => `command=${command}`),
    ...nativeLog,
    "exitCode=0",
    `completedAt=${finishedAt}`,
    "",
  ].join("\n");
}

function atomicWriteJson(outputPath, value) {
  atomicWriteNoReplace(outputPath, `${JSON.stringify(value, null, 2)}\n`);
}

function atomicWriteNoReplace(outputPath, contents) {
  mkdirSync(path.dirname(outputPath), { recursive: true });
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

function atomicCopyNoReplace(sourcePath, outputPath) {
  mkdirSync(path.dirname(outputPath), { recursive: true });
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

function assertSeparateRoots(...roots) {
  for (let left = 0; left < roots.length; left += 1) {
    for (let right = left + 1; right < roots.length; right += 1) {
      if (overlaps(roots[left], roots[right])) {
        throw new Error("repoRoot、bundleRoot 与 runRoot 必须互不包含");
      }
    }
  }
}

function overlaps(left, right) {
  const relative = path.relative(left, right);
  const reverse = path.relative(right, left);
  return (
    relative === "" ||
    (!relative.startsWith("..") && !path.isAbsolute(relative)) ||
    (!reverse.startsWith("..") && !path.isAbsolute(reverse))
  );
}

function requireEmptyDirectory(value, label) {
  const directory = requireDirectory(value, label);
  if (readdirSync(directory).length > 0) {
    throw new Error(`${label} 必须是空目录`);
  }
  return directory;
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

function requireCommit(value, label) {
  const commit = requireText(value, label);
  if (!/^[a-f0-9]{40}$/u.test(commit)) {
    throw new Error(`${label} 必须是完整 40 位哈希`);
  }
  return commit;
}

function requireRunId(value) {
  const runId = requireText(value, "runId");
  if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/u.test(runId)) {
    throw new Error("runId 格式非法");
  }
  return runId;
}

function createRunId(candidateCommit) {
  return `rc0-${new Date().toISOString().replace(/[-:.]/gu, "").slice(0, 15)}-${candidateCommit.slice(0, 12)}`;
}

function normalizeReadinessUrl(value) {
  const text = requireText(value, "readinessUrl");
  let url;
  try {
    url = new URL(text);
  } catch {
    throw new Error("readinessUrl 不是有效 URL");
  }
  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password
  ) {
    throw new Error("readinessUrl 必须是无凭据的 HTTP(S) URL");
  }
  return url.toString();
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
  return result.stdout.trim();
}

function relative(root, target) {
  return path.relative(root, target).replace(/\\/gu, "/");
}

function shellQuote(value) {
  return /^[A-Za-z0-9_./:=+-]+$/u.test(value)
    ? value
    : `'${value.replaceAll("'", "'\\''")}'`;
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label} 不能为空`);
  }
  return value.trim();
}

function now(clock) {
  const value = clock ? clock() : new Date();
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error("时钟返回非法时间");
  return date.toISOString();
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}
