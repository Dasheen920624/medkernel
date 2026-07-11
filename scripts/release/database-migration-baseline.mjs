#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import { launchCoverageClaims } from "./stage-launch-coverage-lib.mjs";

const REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const SCHEMA_SOURCE =
  "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json";
const GENERATOR = "scripts/db/generate-migrations.mjs";
const CONVENTION_GUARD = "scripts/migration-convention-guard.mjs";
const DIALECTS = Object.freeze([
  ["POSTGRES", "postgres"],
  ["KINGBASE", "kingbase"],
  ["ORACLE", "oracle"],
  ["DM", "dm"],
  ["H2", "h2"],
]);

export function buildDatabaseMigrationBaselineEvidence(options = {}) {
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  const now = options.now ?? (() => new Date().toISOString());
  const runCommand = options.runCommand ?? runNodeCommand;

  const generatorCheck = runCommand(repoRoot, [
    GENERATOR,
    "--check",
  ]);
  if (generatorCheck.exitCode !== 0) {
    throw new Error(
      `单一模式源迁移生成检查失败：${trimOutput(generatorCheck.stderr || generatorCheck.stdout)}`,
    );
  }

  const conventionGuard = runCommand(repoRoot, [
    CONVENTION_GUARD,
    "--mode=all",
  ]);
  if (conventionGuard.exitCode !== 0) {
    throw new Error(
      `迁移规约门禁失败：${trimOutput(conventionGuard.stderr || conventionGuard.stdout)}`,
    );
  }

  const generatedAt = now();
  const dialects = DIALECTS.map(([code, directory]) => {
    const dialectRoot = path.join(
      repoRoot,
      "medkernel-backend/src/main/resources/db/migration",
      directory,
    );
    const baselinePath = path.join(dialectRoot, "V1__baseline.sql");
    if (!existsSync(baselinePath)) {
      throw new Error(`${code} 缺少 V1__baseline.sql`);
    }
    const content = readFileSync(baselinePath, "utf8");
    const artifactCount = readdirSync(dialectRoot).filter((entry) =>
      /^V\d+__.+\.sql$/u.test(entry),
    ).length;
    return {
      code,
      baselineFile: path.relative(repoRoot, baselinePath).replace(/\\/g, "/"),
      artifactCount,
      contentSha256: createHash("sha256").update(content).digest("hex"),
    };
  });

  return {
    schemaVersion: "1.0.0",
    status: "PASSED",
    stage: "DATABASE_MIGRATION_BASELINE",
    generatedAt,
    schemaSource: SCHEMA_SOURCE,
    generator: GENERATOR,
    generatorCheck: {
      exitCode: generatorCheck.exitCode,
      checkOnly: true,
    },
    conventionGuard: {
      exitCode: conventionGuard.exitCode,
      scannedFiles: dialects.reduce((sum, item) => sum + item.artifactCount, 0),
    },
    dialects,
    launchCoverage: launchCoverageClaims(
      [
        ["databaseMigrationSource", "SINGLE_SCHEMA_GENERATOR_CHECK"],
        ...dialects.map((item) => ["databaseDialects", item.code]),
      ],
      generatedAt,
    ),
  };
}

function runNodeCommand(repoRoot, args) {
  const result = spawnSync(process.execPath, args, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false,
  });
  return {
    exitCode: result.status ?? 1,
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
  };
}

function trimOutput(value) {
  const text = typeof value === "string" ? value.trim() : "";
  return text.length > 0 ? text : "无输出";
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label}不能为空`);
  }
  return value.trim();
}

if (fileURLToPath(import.meta.url) === path.resolve(process.argv[1] ?? "")) {
  try {
    const outputPath = requireText(
      process.env.LAUNCH_DATABASE_MIGRATION_EVIDENCE_PATH,
      "LAUNCH_DATABASE_MIGRATION_EVIDENCE_PATH",
    );
    writeJsonAtomic(outputPath, buildDatabaseMigrationBaselineEvidence());
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
