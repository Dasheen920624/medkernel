#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const REQUIRED_ENGINEERING_CHECK_CODES = Object.freeze([
  "BACKEND_TESTS",
  "FRONTEND_GATES",
  "CLI_TESTS",
  "MCP_TESTS",
  "MIGRATIONS",
  "T_GATE",
  "FRESH_DEPLOY_DRILL",
  "BACKUP_RESTORE",
  "MODEL_PROVIDER",
  "EVALUATION_CASE_EVIDENCE",
  "READINESS_PREFLIGHT",
]);

const REQUIRED_CHECK_SET = new Set(REQUIRED_ENGINEERING_CHECK_CODES);
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const DEFAULT_MANIFEST_PATH = resolve(
  repoRoot,
  "docs/release/evidence/p9-production-golive-20260618/engineering-rehearsal-manifest.json",
);

export function assessEngineeringRehearsal(manifest, options = {}) {
  const failures = [];
  const checks = [];
  const readJson = options.readJson ?? readJsonFile;
  const entries = Array.isArray(manifest?.checks) ? manifest.checks : [];
  const entriesByCode = new Map();

  if (!Array.isArray(manifest?.checks)) {
    failures.push("工程预演 manifest 缺少 checks 数组");
  }

  for (const entry of entries) {
    const code = normalizeText(entry?.code);
    if (!code) {
      failures.push("存在缺少 code 的证据项");
      continue;
    }
    const group = entriesByCode.get(code) ?? [];
    group.push(entry);
    entriesByCode.set(code, group);
  }

  const duplicates = [...entriesByCode.entries()]
    .filter(([, group]) => group.length > 1)
    .map(([code]) => code);
  if (duplicates.length > 0) {
    failures.push(`重复证据类型：${duplicates.join(",")}`);
  }

  const unknown = [...entriesByCode.keys()].filter(
    (code) => !REQUIRED_CHECK_SET.has(code),
  );
  if (unknown.length > 0) {
    failures.push(`未知证据类型：${unknown.join(",")}`);
  }

  const missing = REQUIRED_ENGINEERING_CHECK_CODES.filter(
    (code) => !entriesByCode.has(code),
  );
  if (missing.length > 0) {
    failures.push(`缺少必需证据：${missing.join(",")}`);
  }

  for (const code of REQUIRED_ENGINEERING_CHECK_CODES) {
    const group = entriesByCode.get(code);
    if (!group || group.length !== 1) {
      continue;
    }
    const path = normalizeText(group[0]?.path);
    if (!path || !path.toLowerCase().endsWith(".json")) {
      failures.push(`${code} 未提供显式 JSON 路径`);
      checks.push(unsafeCheck(code, path, "INVALID_PATH"));
      continue;
    }

    let evidence;
    try {
      evidence = readJson(path);
    } catch (error) {
      failures.push(`${code} 证据读取失败：${safeErrorMessage(error)}`);
      checks.push(unsafeCheck(code, path, "READ_ERROR"));
      continue;
    }

    const status = normalizeText(evidence?.status) ?? "<missing>";
    const containsCredentials = evidence?.containsCredentials;
    const containsPatientData = evidence?.containsPatientData;
    checks.push({
      code,
      path,
      status,
      containsCredentials:
        containsCredentials === false
          ? false
          : containsCredentials === true
            ? true
            : null,
      containsPatientData:
        containsPatientData === false
          ? false
          : containsPatientData === true
            ? true
            : null,
    });

    if (status !== "PASSED") {
      failures.push(`${code} 证据状态为 ${status}，要求 PASSED`);
    }
    if (containsCredentials !== false || containsPatientData !== false) {
      failures.push(`${code} 安全数据边界声明不完整`);
    }
  }

  const containsCredentials =
    checks.length !== REQUIRED_ENGINEERING_CHECK_CODES.length ||
    checks.some((check) => check.containsCredentials !== false);
  const containsPatientData =
    checks.length !== REQUIRED_ENGINEERING_CHECK_CODES.length ||
    checks.some((check) => check.containsPatientData !== false);
  const passed =
    failures.length === 0 &&
    checks.length === REQUIRED_ENGINEERING_CHECK_CODES.length &&
    !containsCredentials &&
    !containsPatientData;

  return {
    status: passed ? "PASSED" : "BLOCKED",
    stage: passed ? "REHEARSAL_READY" : "ENGINEERING",
    checks,
    failures,
    containsCredentials,
    containsPatientData,
  };
}

export function assessEngineeringRehearsalManifest(manifestPath) {
  const normalizedManifestPath = resolve(normalizeRequiredPath(manifestPath));
  let manifest;
  try {
    manifest = readJsonFile(normalizedManifestPath);
  } catch (error) {
    return blockedForManifest(
      `工程预演 manifest 读取失败：${safeErrorMessage(error)}`,
    );
  }
  const baseDir = dirname(normalizedManifestPath);
  return assessEngineeringRehearsal(manifest, {
    readJson: (path) =>
      readJsonFile(isAbsolute(path) ? path : resolve(baseDir, path)),
  });
}

export function resolveManifestPath(
  argv = process.argv.slice(2),
  env = process.env,
) {
  const manifestIndex = argv.indexOf("--manifest");
  if (manifestIndex >= 0) {
    const value = argv[manifestIndex + 1];
    if (!normalizeText(value)) {
      throw new Error("--manifest 必须提供 JSON 路径");
    }
    return resolve(value);
  }
  return normalizeText(env.P9_ENGINEERING_REHEARSAL_MANIFEST)
    ? resolve(env.P9_ENGINEERING_REHEARSAL_MANIFEST)
    : DEFAULT_MANIFEST_PATH;
}

function blockedForManifest(message) {
  return {
    status: "BLOCKED",
    stage: "ENGINEERING",
    checks: [],
    failures: [message],
    containsCredentials: true,
    containsPatientData: true,
  };
}

function unsafeCheck(code, path, status) {
  return {
    code,
    path: path ?? null,
    status,
    containsCredentials: null,
    containsPatientData: null,
  };
}

function readJsonFile(path) {
  return JSON.parse(readFileSync(path, "utf8"));
}

function normalizeText(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function normalizeRequiredPath(value) {
  const normalized = normalizeText(value);
  if (!normalized) {
    throw new Error("manifestPath 不能为空");
  }
  return normalized;
}

function safeErrorMessage(error) {
  return error instanceof Error && error.message
    ? error.message
    : "未知读取错误";
}

function isMainModule() {
  return (
    typeof process.argv[1] === "string" &&
    resolve(process.argv[1]) === fileURLToPath(import.meta.url)
  );
}

if (isMainModule()) {
  try {
    const manifestPath = resolveManifestPath();
    const result = assessEngineeringRehearsalManifest(manifestPath);
    console.log(JSON.stringify(result, null, 2));
    if (result.status !== "PASSED") {
      process.exitCode = 1;
    }
  } catch (error) {
    console.error(
      JSON.stringify(
        blockedForManifest(
          `工程预演聚合器启动失败：${safeErrorMessage(error)}`,
        ),
        null,
        2,
      ),
    );
    process.exitCode = 1;
  }
}
