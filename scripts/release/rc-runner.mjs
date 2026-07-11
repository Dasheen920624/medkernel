#!/usr/bin/env node

import { getRcRunnerPlan, runRc0 } from "./rc-runner-lib.mjs";

const RUN_REQUIRED = Object.freeze([
  "repo-root",
  "bundle-root",
  "run-root",
  "candidate-commit",
]);
const RUN_OPTIONAL = Object.freeze([
  "source-base-commit",
  "run-id",
  "readiness-url",
]);

try {
  const [command, ...remaining] = process.argv.slice(2);
  if (command === "plan") {
    if (remaining.length > 0) throw new Error("plan 命令不接受选项");
    process.stdout.write(`${JSON.stringify(getRcRunnerPlan())}\n`);
  } else if (command === "run") {
    const options = parseOptions(remaining);
    const result = await runRc0({
      repoRoot: options["repo-root"],
      bundleRoot: options["bundle-root"],
      runRoot: options["run-root"],
      candidateCommit: options["candidate-commit"],
      sourceBaseCommit: options["source-base-commit"],
      runId: options["run-id"],
      readinessUrl: options["readiness-url"],
    });
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } else {
    throw new Error("用法：rc-runner.mjs <plan|run> [选项]");
  }
} catch (error) {
  process.stderr.write(
    `${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
}

function parseOptions(argv) {
  if (argv.length % 2 !== 0) throw new Error("run 参数必须成对提供");
  const allowed = new Set([...RUN_REQUIRED, ...RUN_OPTIONAL]);
  const options = {};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (typeof flag !== "string" || !flag.startsWith("--")) {
      throw new Error(`存在非法参数：${flag ?? "<空>"}`);
    }
    const name = flag.slice(2);
    if (!allowed.has(name)) throw new Error(`存在未知选项：${flag}`);
    if (Object.hasOwn(options, name)) throw new Error(`选项重复：${flag}`);
    if (typeof value !== "string" || !value.trim() || value.startsWith("--")) {
      throw new Error(`选项缺少值：${flag}`);
    }
    options[name] = value;
  }
  const missing = RUN_REQUIRED.filter((name) => !options[name]);
  if (missing.length > 0) {
    throw new Error(
      `缺少必需选项：${missing.map((name) => `--${name}`).join("、")}`,
    );
  }
  return options;
}
