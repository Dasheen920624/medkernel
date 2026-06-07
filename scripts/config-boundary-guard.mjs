import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve, relative } from "node:path";

import {
  listAllCurrentFiles,
  listChangedFiles,
  listTrackedFiles,
} from "./git-scan-files.mjs";

const JAVA_SOURCE = /^medkernel-backend\/src\/main\/java\/.+\.java$/;
const ALLOWED_BOOTSTRAP_KEYS = [
  "medkernel.jwt.dev-secret",
  "medkernel.version",
  "medkernel.stage",
];

function normalizePath(file, root) {
  return relative(root, resolve(root, file)).replaceAll("\\", "/");
}

function lineOf(content, index) {
  return content.slice(0, index).split(/\r?\n/).length;
}

function allowedKey(rawKey) {
  return ALLOWED_BOOTSTRAP_KEYS.some((key) => rawKey === key || rawKey.startsWith(`${key}:`));
}

function addViolations(violations, file, content) {
  const directReadPattern = /\$\{(medkernel\.[^}:"]+)/g;
  for (const match of content.matchAll(directReadPattern)) {
    const key = match[1];
    if (allowedKey(key)) {
      continue;
    }
    violations.push({
      file,
      line: lineOf(content, match.index),
      ruleId: "config-boundary.direct-medkernel-read",
      message: "非启动必需的 medkernel.* 配置必须经配置中心读取，禁止生产代码直接读取 yml/env。",
    });
  }
}

export async function scanFiles(root, files) {
  const violations = [];
  const scannedFiles = [];
  for (const rawFile of files) {
    const file = normalizePath(rawFile, root);
    if (!JAVA_SOURCE.test(file)) {
      continue;
    }
    const fullPath = resolve(root, file);
    if (!existsSync(fullPath)) {
      continue;
    }
    scannedFiles.push(file);
    addViolations(violations, file, readFileSync(fullPath, "utf8"));
  }
  return { scannedFiles, violations };
}

export function hasBlockingViolations(report) {
  return report.violations.length > 0;
}

function parseArgs(argv) {
  const options = { mode: "changed", base: "origin/main" };
  for (const arg of argv) {
    if (arg.startsWith("--mode=")) {
      options.mode = arg.slice("--mode=".length);
    }
    if (arg.startsWith("--base=")) {
      options.base = arg.slice("--base=".length);
    }
  }
  return options;
}

function printReport(report, mode) {
  console.log(`配置边界门禁扫描：mode=${mode}，扫描文件 ${report.scannedFiles.length} 个。`);
  for (const violation of report.violations) {
    console.log(`${violation.file}:${violation.line} [${violation.ruleId}] ${violation.message}`);
  }
  if (report.violations.length === 0) {
    console.log("配置边界门禁通过：未发现阻断项。");
  }
}

async function main() {
  const root = process.cwd();
  const options = parseArgs(process.argv.slice(2));
  const pathspecs = ["medkernel-backend/src/main/java"];
  let files;
  if (options.mode === "changed") {
    files = listChangedFiles(root, options.base, pathspecs);
  } else if (options.mode === "all") {
    files = listAllCurrentFiles(root, pathspecs);
  } else if (options.mode === "inventory") {
    files = listTrackedFiles(root, pathspecs);
  } else {
    throw new Error(`未知 mode：${options.mode}`);
  }
  const report = await scanFiles(root, files);
  printReport(report, options.mode);
  if (options.mode !== "inventory" && hasBlockingViolations(report)) {
    process.exit(1);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await main();
}
