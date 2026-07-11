#!/usr/bin/env node

import { buildRcArtifact } from "./rc-artifact-builder-lib.mjs";

const REQUIRED_OPTIONS = Object.freeze([
  "artifact-id",
  "repo-root",
  "bundle-root",
  "run-root",
  "run-id",
  "candidate-commit",
]);

try {
  const options = parseOptions(process.argv.slice(2));
  const result = buildRcArtifact({
    artifactId: options["artifact-id"],
    repoRoot: options["repo-root"],
    bundleRoot: options["bundle-root"],
    runRoot: options["run-root"],
    runId: options["run-id"],
    candidateCommit: options["candidate-commit"],
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
} catch (error) {
  process.stderr.write(
    `${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
}

function parseOptions(argv) {
  if (argv.length % 2 !== 0) throw new Error("制品构建参数必须成对提供");
  const allowed = new Set(REQUIRED_OPTIONS);
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
  const missing = REQUIRED_OPTIONS.filter((name) => !options[name]);
  if (missing.length > 0) {
    throw new Error(
      `缺少必需选项：${missing.map((name) => `--${name}`).join("、")}`,
    );
  }
  return options;
}
