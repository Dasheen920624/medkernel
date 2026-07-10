#!/usr/bin/env node

import { randomUUID } from "node:crypto";
import {
  closeSync,
  existsSync,
  fsyncSync,
  linkSync,
  lstatSync,
  openSync,
  readFileSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";

import {
  createRcManifest,
  serializeRcManifest,
  verifyRcManifest,
} from "./rc-manifest-lib.mjs";

const COMMAND_OPTIONS = Object.freeze({
  create: Object.freeze(["repo-root", "bundle-root", "input", "output"]),
  verify: Object.freeze(["repo-root", "bundle-root", "manifest"]),
});

try {
  const { command, options } = parseArguments(process.argv.slice(2));
  if (command === "create") {
    const input = readJsonFile(options.input, "RC 清单输入");
    const manifest = createRcManifest({
      ...input,
      repoRoot: options["repo-root"],
      bundleRoot: options["bundle-root"],
    });
    writeAtomicNoReplace(options.output, serializeRcManifest(manifest));
    writeJsonLine({
      status: "CREATED",
      sourceBaseCommit: manifest.sourceBaseCommit,
      candidateCommit: manifest.candidateCommit,
      runId: manifest.runId,
      manifestPath: path.resolve(options.output),
    });
  } else {
    const manifest = readJsonFile(options.manifest, "RC 清单");
    writeJsonLine(
      verifyRcManifest(manifest, {
        repoRoot: options["repo-root"],
        bundleRoot: options["bundle-root"],
      }),
    );
  }
} catch (error) {
  process.stderr.write(
    `${error instanceof Error ? error.message : String(error)}\n`,
  );
  process.exitCode = 1;
}

function parseArguments(argv) {
  const [command, ...remaining] = argv;
  const requiredOptions = COMMAND_OPTIONS[command];
  if (!requiredOptions) {
    throw new Error("用法：rc-manifest.mjs <create|verify> [选项]");
  }
  const allowed = new Set(requiredOptions);
  const options = {};
  for (let index = 0; index < remaining.length; index += 2) {
    const flag = remaining[index];
    const value = remaining[index + 1];
    if (typeof flag !== "string" || !flag.startsWith("--")) {
      throw new Error(`存在非法参数：${flag ?? "<空>"}`);
    }
    const name = flag.slice(2);
    if (!allowed.has(name)) throw new Error(`存在未知选项：${flag}`);
    if (Object.hasOwn(options, name)) throw new Error(`选项重复：${flag}`);
    if (typeof value !== "string" || value.startsWith("--") || !value.trim()) {
      throw new Error(`选项缺少值：${flag}`);
    }
    options[name] = value;
  }
  const missing = requiredOptions.filter((name) => !options[name]);
  if (missing.length > 0) {
    throw new Error(
      `缺少必需选项：${missing.map((name) => `--${name}`).join("、")}`,
    );
  }
  return { command, options };
}

function readJsonFile(inputPath, label) {
  const absolutePath = path.resolve(inputPath);
  let payload;
  try {
    payload = JSON.parse(readFileSync(absolutePath, "utf8"));
  } catch (error) {
    if (error?.code === "ENOENT") throw new Error(`${label}文件不存在`);
    throw new Error(`${label}不是有效 JSON`);
  }
  if (
    payload === null ||
    typeof payload !== "object" ||
    Array.isArray(payload)
  ) {
    throw new Error(`${label}必须是 JSON 对象`);
  }
  return payload;
}

function writeAtomicNoReplace(outputPath, contents) {
  const absolutePath = path.resolve(outputPath);
  const parentPath = path.dirname(absolutePath);
  if (!existsSync(parentPath) || !lstatSync(parentPath).isDirectory()) {
    throw new Error("输出清单父目录不存在或不是目录");
  }
  if (existsSync(absolutePath)) throw new Error("输出清单已存在，拒绝覆盖");

  const temporaryPath = path.join(
    parentPath,
    `.${path.basename(absolutePath)}.tmp-${process.pid}-${randomUUID()}`,
  );
  let fileDescriptor;
  try {
    fileDescriptor = openSync(temporaryPath, "wx", 0o600);
    writeFileSync(fileDescriptor, contents, "utf8");
    fsyncSync(fileDescriptor);
    closeSync(fileDescriptor);
    fileDescriptor = undefined;
    try {
      linkSync(temporaryPath, absolutePath);
    } catch (error) {
      if (error?.code === "EEXIST") {
        throw new Error("输出清单已存在，拒绝覆盖");
      }
      throw error;
    }
  } finally {
    if (fileDescriptor !== undefined) closeSync(fileDescriptor);
    if (existsSync(temporaryPath)) unlinkSync(temporaryPath);
  }
}

function writeJsonLine(value) {
  process.stdout.write(`${JSON.stringify(value)}\n`);
}
