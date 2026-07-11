import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { once } from "node:events";
import {
  mkdtempSync,
  mkdirSync,
  realpathSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { createServer } from "node:http";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, test } from "node:test";

import {
  createCandidateRuntimeLaunch,
  deriveRuntimeIdentityUrl,
  executeCandidateRuntimeGate,
  getRcRunnerPlan,
  readCandidateRuntimeProbe,
  resolveContractCommand,
  runRc0,
  startCandidateRuntime,
  stopCandidateRuntime,
  summarizePlaywrightReport,
  summarizeSurefireReports,
  validatePerformanceSuiteBoundary,
  waitForCandidateRuntimeProbe,
} from "./rc-runner-lib.mjs";

const PROJECT_ROOT = fileURLToPath(new URL("../..", import.meta.url));
const RUNNER_CLI = fileURLToPath(new URL("./rc-runner.mjs", import.meta.url));
const temporaryRoots = [];

afterEach(() => {
  while (temporaryRoots.length > 0) {
    rmSync(temporaryRoots.pop(), { recursive: true, force: true });
  }
});

test("RC 运行器计划覆盖依赖、九门禁、六制品、清单创建和独立重验且没有人工中间门", () => {
  const plan = getRcRunnerPlan();
  assert.equal(plan.schemaVersion, "1.0.0");
  assert.equal(plan.kind, "MEDKERNEL_RC_RUNNER_PLAN");
  assert.deepEqual(plan.phases, [
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
  ]);
  assert.equal(plan.gates.length, 9);
  assert.equal(plan.artifacts.length, 6);
  assert.deepEqual(plan.manualGates, []);
  assert.deepEqual(plan.externalValidationBoundaries, [
    {
      tags: ["docker", "performance"],
      disposition: "TARGET_ENVIRONMENT_GATES",
      reason:
        "需要 Docker 或专项容量环境的测试不在普通 RC0 后端门禁中伪造执行，必须在后续 PostgreSQL 16/openEuler 目标环境与 10 万级容量门禁中独立完成",
      requiredSuites: [
        {
          source:
            "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeExportServiceLargeScaleTest.java",
          testClass:
            "com.medkernel.engine.knowledge.KnowledgeExportServiceLargeScaleTest",
          selectors: ["*"],
        },
        {
          source:
            "medkernel-backend/src/test/java/com/medkernel/engine/terminology/TerminologyRepositoryLargeScaleTest.java",
          testClass:
            "com.medkernel.engine.terminology.TerminologyRepositoryLargeScaleTest",
          selectors: ["*"],
        },
        {
          source:
            "medkernel-backend/src/test/java/com/medkernel/engine/list/LargeListAuditEventRepositoryTest.java",
          testClass:
            "com.medkernel.engine.list.LargeListAuditEventRepositoryTest",
          selectors: ["*"],
        },
        {
          source:
            "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepositoryTest.java",
          testClass:
            "com.medkernel.engine.knowledge.KnowledgeIdentityRepositoryTest",
          selectors: [
            "pageByFilterHandlesHundredThousandKnowledgeIdentitiesWithinLocalBudget",
          ],
        },
        {
          source:
            "medkernel-backend/src/test/java/com/medkernel/perf/B0LargeScaleDialectSmokeTest.java",
          testClass: "com.medkernel.perf.B0LargeScaleDialectSmokeTest",
          selectors: [
            "postgresHandlesHundredThousandKnowledgeAndTerminologyRows",
            "oracleHandlesHundredThousandKnowledgeAndTerminologyRows",
          ],
        },
      ],
    },
  ]);
  assert.doesNotThrow(() =>
    validatePerformanceSuiteBoundary(PROJECT_ROOT, plan),
  );
  assert.deepEqual(
    plan.gates.find(({ gateId }) => gateId === "BACKEND_TESTS").commands,
    [
      "cd medkernel-backend && CI=true mvn -B -q -Dmaven.repo.local=<run>/m2repo -DexcludedGroups=docker,performance clean test",
    ],
  );

  const cli = spawnSync(process.execPath, [RUNNER_CLI, "plan"], {
    cwd: PROJECT_ROOT,
    encoding: "utf8",
    shell: false,
  });
  assert.equal(cli.status, 0, cli.stderr || cli.stdout);
  assert.deepEqual(JSON.parse(cli.stdout), plan);
});

test("RC 运行器拒绝漏标或漏登记的 10 万级墙钟套件", () => {
  const root = mkdtempSync(
    path.join(os.tmpdir(), "medkernel-performance-boundary-"),
  );
  temporaryRoots.push(root);
  const relativeSource =
    "medkernel-backend/src/test/java/com/medkernel/perf/ExampleLargeScaleTest.java";
  const sourcePath = path.join(root, relativeSource);
  mkdirSync(path.dirname(sourcePath), { recursive: true });
  const plan = {
    externalValidationBoundaries: [
      {
        tags: ["docker", "performance"],
        requiredSuites: [
          {
            source: relativeSource,
            testClass: "com.medkernel.perf.ExampleLargeScaleTest",
            selectors: ["*"],
          },
        ],
      },
    ],
  };
  writeFileSync(
    sourcePath,
    "package com.medkernel.perf;\nclass ExampleLargeScaleTest { void test() { int total = 100_000; Duration.between(null, null).toMillis(); } }\n",
    "utf8",
  );
  assert.throws(
    () => validatePerformanceSuiteBoundary(root, plan),
    /缺少类级 performance 标签/u,
  );

  writeFileSync(
    sourcePath,
    'package com.medkernel.perf;\n@Tag("performance")\nclass ExampleLargeScaleTest { void test() { int total = 100_000; Duration.between(null, null).toMillis(); } }\n',
    "utf8",
  );
  assert.equal(validatePerformanceSuiteBoundary(root, plan), 1);
  plan.externalValidationBoundaries[0].requiredSuites = [];
  assert.throws(
    () => validatePerformanceSuiteBoundary(root, plan),
    /10 万级墙钟套件登记漂移/u,
  );
});

test("候选运行配置只允许回环端口并直接启动本次后端 JAR", () => {
  const candidateCommit = "a".repeat(40);
  const root = mkdtempSync(path.join(os.tmpdir(), "medkernel-launch-"));
  temporaryRoots.push(root);
  const requestedArtifactPath = path.join(root, "backend.jar");
  writeFileSync(requestedArtifactPath, "fixture\n", "utf8");
  const artifactPath = realpathSync(requestedArtifactPath);
  const launch = createCandidateRuntimeLaunch({
    artifactPath,
    readinessUrl: "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
    candidateCommit,
    runRoot: root,
  });
  assert.equal(launch.command, "java");
  assert.deepEqual(launch.args.slice(0, 2), ["-jar", artifactPath]);
  assert.ok(launch.args.includes("--server.port=18080"));
  assert.ok(launch.args.includes("--spring.profiles.active=dev"));
  assert.equal(launch.host, "127.0.0.1");
  assert.equal(launch.port, 18080);
  assert.match(launch.logPath, /candidate-runtime\/backend\.log$/u);

  assert.throws(
    () =>
      createCandidateRuntimeLaunch({
        artifactPath,
        readinessUrl:
          "https://hospital.example/medkernel/actuator/health/readiness",
        candidateCommit,
        runRoot: root,
      }),
    /回环 HTTP/u,
  );
  assert.throws(
    () =>
      createCandidateRuntimeLaunch({
        artifactPath: path.join(root, "missing.jar"),
        readinessUrl:
          "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
        candidateCommit,
        runRoot: root,
      }),
    /候选后端制品/u,
  );
});

test("候选运行门禁同时保留门禁失败与停止失败，禁止漏报进程清理风险", async () => {
  const gateFailure = new Error("浏览器门禁失败");
  const stopFailure = new Error("候选后端停止失败");

  await assert.rejects(
    executeCandidateRuntimeGate(
      { child: {}, state: {} },
      async () => {
        throw gateFailure;
      },
      async () => {
        throw stopFailure;
      },
    ),
    (error) => {
      assert.ok(error instanceof AggregateError);
      assert.deepEqual(error.errors, [gateFailure, stopFailure]);
      assert.match(error.message, /门禁与停止均失败/u);
      return true;
    },
  );
});

test("候选运行生命周期按真实探针条件启动并在门禁后优雅停止", async (context) => {
  const candidateCommit = "c".repeat(40);
  const reservation = createServer();
  reservation.listen(0, "127.0.0.1");
  await once(reservation, "listening");
  const reservedAddress = reservation.address();
  assert.ok(reservedAddress && typeof reservedAddress === "object");
  const port = reservedAddress.port;
  await new Promise((resolve, reject) =>
    reservation.close((error) => (error ? reject(error) : resolve())),
  );
  const root = mkdtempSync(path.join(os.tmpdir(), "medkernel-runtime-"));
  temporaryRoots.push(root);
  writeFileSync(path.join(root, "backend.jar"), "fixture\n", "utf8");
  const readinessUrl = `http://127.0.0.1:${port}/medkernel/actuator/health/readiness`;
  const serverProgram = [
    'const http = require("node:http");',
    "const port = Number(process.argv[1]);",
    "const commit = process.argv[2];",
    "const server = http.createServer((request, response) => {",
    'response.setHeader("content-type", "application/json");',
    'if (request.url === "/medkernel/actuator/health/readiness") return response.end(JSON.stringify({status:"UP"}) + "\\n");',
    'if (request.url === "/medkernel/api/v1/system/ping") return response.end(JSON.stringify({success:true,code:"OK",data:{product:"MedKernel",buildBound:true,buildCommit:commit}}) + "\\n");',
    "response.statusCode = 404; response.end('{}\\n');",
    "});",
    'process.on("SIGTERM", () => server.close(() => process.exit(0)));',
    'server.listen(port, "127.0.0.1");',
  ].join("\n");

  const runtime = await startCandidateRuntime(
    {
      artifactPath: path.join(root, "backend.jar"),
      readinessUrl,
      runRoot: root,
      runId: "rc-runtime-20260711-001",
      candidateCommit,
    },
    {
      spawnProcess: (_command, _args, options) =>
        spawn(
          process.execPath,
          ["-e", serverProgram, String(port), candidateCommit],
          options,
        ),
    },
  );
  context.after(async () => {
    if (!runtime.state.exited) await stopCandidateRuntime(runtime);
  });
  assert.equal(runtime.state.exited, false);
  await stopCandidateRuntime(runtime);
  assert.equal(runtime.state.exited, true);
  assert.equal(runtime.state.exitCode, 0);
});

test("运行器只替换已知占位符并对含空格与单引号的路径做 Shell 安全引用", () => {
  const resolved = resolveContractCommand(
    "cd . && tool --repo <repo> --bundle <bundle> --run <run> --id <run-id> --commit <candidate-commit>",
    {
      repo: "/tmp/repo with space",
      bundle: "/tmp/bundle's evidence",
      run: "/tmp/run",
      runId: "rc-run-20260711-001",
      candidateCommit: "a".repeat(40),
    },
  );
  assert.doesNotMatch(
    resolved,
    /<(?:repo|bundle|run|run-id|candidate-commit)>/u,
  );
  assert.match(resolved, /'\/tmp\/repo with space'/u);
  assert.match(resolved, /'\/tmp\/bundle'\\''s evidence'/u);
  assert.throws(
    () => resolveContractCommand("echo <unknown>", {}),
    /未解析占位符/u,
  );
});

test("运行器从 Playwright 逐测试结果重算项目汇总并拒绝空壳报告", () => {
  const report = playwrightReport();
  assert.deepEqual(summarizePlaywrightReport(report), {
    status: "PASSED",
    command: "npm run e2e",
    workers: 1,
    retries: 0,
    stats: report.stats,
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
  });

  const empty = playwrightReport();
  empty.suites[0].specs = [];
  assert.throws(
    () => summarizePlaywrightReport(empty),
    /测试结果数与 expected 不一致/u,
  );

  const malformedNestedSuites = playwrightReport();
  malformedNestedSuites.suites[0].suites = {};
  assert.throws(
    () => summarizePlaywrightReport(malformedNestedSuites),
    /suite 结构非法/u,
  );
});

test("运行器从每份 Surefire XML 重算真实计数并拒绝声明数与 testcase 数不一致", () => {
  const root = mkdtempSync(path.join(os.tmpdir(), "medkernel-surefire-"));
  temporaryRoots.push(root);
  const one = path.join(root, "TEST-One.xml");
  const two = path.join(root, "TEST-Two.xml");
  writeFileSync(
    one,
    '<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase name="a"/><testcase name="b"/></testsuite>\n',
    "utf8",
  );
  writeFileSync(
    two,
    '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="c"/></testsuite>\n',
    "utf8",
  );
  assert.deepEqual(summarizeSurefireReports([one, two]), {
    status: "PASSED",
    reportFiles: 2,
    tests: 3,
    failures: 0,
    errors: 0,
    skipped: 0,
  });

  const invalid = path.join(root, "TEST-Invalid.xml");
  writeFileSync(
    invalid,
    '<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase name="only"/></testsuite>\n',
    "utf8",
  );
  assert.throws(
    () => summarizeSurefireReports([invalid]),
    /testcase 数与 tests 声明不一致/u,
  );

  const skipped = path.join(root, "TEST-Skipped.xml");
  writeFileSync(
    skipped,
    '<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase name="skip"><skipped/></testcase></testsuite>\n',
    "utf8",
  );
  assert.throws(
    () => summarizeSurefireReports([skipped]),
    /Surefire 报告存在跳过测试/u,
  );
});

test("运行器拒绝带忽略构建残留的伪干净候选起跑", async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), "medkernel-rc-runner-"));
  temporaryRoots.push(root);
  const repoRoot = path.join(root, "repo");
  const bundleRoot = path.join(root, "bundle");
  const runRoot = path.join(root, "run");
  mkdirSync(repoRoot, { recursive: true });
  mkdirSync(bundleRoot, { recursive: true });
  mkdirSync(runRoot, { recursive: true });
  runGit(repoRoot, ["init", "-q"]);
  runGit(repoRoot, ["config", "user.name", "MedKernel Test"]);
  runGit(repoRoot, ["config", "user.email", "test@medkernel.invalid"]);
  writeFileSync(path.join(repoRoot, ".gitignore"), "target/\n", "utf8");
  writeFileSync(path.join(repoRoot, "README.md"), "# fixture\n", "utf8");
  runGit(repoRoot, ["add", "-A"]);
  runGit(repoRoot, ["commit", "-q", "-m", "候选"]);
  mkdirSync(path.join(repoRoot, "target"), { recursive: true });
  writeFileSync(path.join(repoRoot, "target/stale.log"), "stale\n", "utf8");
  const candidateCommit = runGit(repoRoot, ["rev-parse", "HEAD"]).trim();

  await assert.rejects(
    runRc0(
      {
        repoRoot,
        bundleRoot,
        runRoot,
        candidateCommit,
        sourceBaseCommit: candidateCommit,
      },
      {
        execute: () => ({ exitCode: 1, stdout: "", stderr: "不应执行" }),
      },
    ),
    /候选工作区起跑时仍有忽略残留/u,
  );
});

test("候选运行探针同时校验 readiness 与 JAR 内嵌提交身份", async (context) => {
  const candidateCommit = "a".repeat(40);
  let reportedCommit = candidateCommit;
  const observedConnections = [];
  const server = createServer((request, response) => {
    observedConnections.push(request.headers.connection);
    response.setHeader("content-type", "application/json");
    if (request.url === "/medkernel/actuator/health/readiness") {
      response.end(`${JSON.stringify({ status: "UP" })}\n`);
      return;
    }
    if (request.url === "/medkernel/api/v1/system/ping") {
      response.end(
        `${JSON.stringify({
          success: true,
          code: "OK",
          data: {
            product: "MedKernel",
            buildBound: true,
            buildCommit: reportedCommit,
          },
        })}\n`,
      );
      return;
    }
    response.statusCode = 404;
    response.end("{}\n");
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  context.after(() => server.close());
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const readinessUrl = `http://127.0.0.1:${address.port}/medkernel/actuator/health/readiness`;
  assert.equal(
    deriveRuntimeIdentityUrl(readinessUrl),
    `http://127.0.0.1:${address.port}/medkernel/api/v1/system/ping`,
  );

  const probe = await readCandidateRuntimeProbe(readinessUrl, {
    runId: "rc-probe-20260711-001",
    candidateCommit,
    phase: "BEFORE_E2E",
    clock: () => new Date("2026-07-11T02:00:00.000Z"),
  });
  assert.equal(JSON.parse(probe.rawBody).status, "UP");
  assert.equal(
    JSON.parse(probe.identityRawBody).data.buildCommit,
    candidateCommit,
  );
  assert.equal(probe.check.buildBound, true);
  assert.equal(probe.check.buildCommit, candidateCommit);
  assert.match(probe.check.identitySha256, /^[a-f0-9]{64}$/u);
  assert.deepEqual(observedConnections, ["close", "close"]);

  reportedCommit = "b".repeat(40);
  await assert.rejects(
    readCandidateRuntimeProbe(readinessUrl, {
      runId: "rc-probe-20260711-001",
      candidateCommit,
      phase: "AFTER_E2E",
    }),
    /运行时身份.*候选提交不一致/u,
  );
});

test("候选运行稳定探针只重试传输瞬断并保留最后错误因果", async () => {
  const candidateCommit = "d".repeat(40);
  const expected = { check: { candidateCommit } };
  let attempts = 0;
  const transientReadiness = async () => {
    attempts += 1;
    if (attempts < 3) {
      const socketError = Object.assign(new Error("另一端关闭连接"), {
        code: "UND_ERR_SOCKET",
      });
      throw Object.assign(
        new Error("readiness AFTER_E2E 请求失败：fetch failed"),
        {
          code: "RUNTIME_PROBE_TRANSPORT",
          cause: socketError,
        },
      );
    }
    return expected;
  };

  assert.equal(
    await waitForCandidateRuntimeProbe(
      transientReadiness,
      "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
      {
        runId: "rc-probe-20260711-002",
        candidateCommit,
        phase: "AFTER_E2E",
      },
      { timeoutMs: 100, intervalMs: 1 },
    ),
    expected,
  );
  assert.equal(attempts, 3);

  attempts = 0;
  await assert.rejects(
    waitForCandidateRuntimeProbe(
      async () => {
        attempts += 1;
        throw new Error("运行时身份 AFTER_E2E 候选提交不一致");
      },
      "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
      {
        runId: "rc-probe-20260711-003",
        candidateCommit,
        phase: "AFTER_E2E",
      },
      { timeoutMs: 100, intervalMs: 1 },
    ),
    /候选提交不一致/u,
  );
  assert.equal(attempts, 1);

  await assert.rejects(
    waitForCandidateRuntimeProbe(
      async () => {
        const socketError = Object.assign(new Error("陈旧连接已关闭"), {
          code: "UND_ERR_SOCKET",
        });
        throw Object.assign(new Error("fetch failed"), {
          code: "RUNTIME_PROBE_TRANSPORT",
          cause: socketError,
        });
      },
      "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
      {
        runId: "rc-probe-20260711-004",
        candidateCommit,
        phase: "AFTER_E2E",
      },
      { timeoutMs: 5, intervalMs: 1 },
    ),
    /AFTER_E2E.*最后探针错误.*RUNTIME_PROBE_TRANSPORT.*UND_ERR_SOCKET/su,
  );
});

function runGit(cwd, args) {
  const result = spawnSync("git", args, {
    cwd,
    encoding: "utf8",
    shell: false,
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout;
}

function playwrightReport() {
  return {
    config: {
      workers: 1,
      projects: [
        { name: "chromium", retries: 0 },
        { name: "国产 Chromium 内核仿真（非现场认证）", retries: 0 },
      ],
    },
    errors: [],
    stats: {
      startTime: "2026-07-11T01:00:00.000Z",
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
      },
    ],
  };
}
