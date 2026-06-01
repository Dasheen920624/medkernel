#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const MIGRATION_SQL =
  /^medkernel-backend\/src\/main\/resources\/db\/migration\/(h2|postgres|oracle|dm|kingbase)\/(V\d+__[a-z0-9_]+\.sql)$/;
const PRODUCTION_COMMENT_DIALECTS = new Set(["postgres", "oracle", "kingbase"]);
const SYSTEM_TABLE_ALLOWLIST = new Set(["sys_task", "sys_task_dead_letter", "sys_login_attempt"]);
const CJK = /[\u3400-\u9fff]/;
const HIGH_RISK_SQL = /\b(DROP\s+TABLE|DROP\s+COLUMN|TRUNCATE\s+TABLE|DELETE\s+FROM|ALTER\s+TABLE\s+[A-Za-z_][A-Za-z0-9_]*\s+DROP|RENAME\s+TO)\b/i;
const ROLLBACK_NOTE = /(?:--|\/\*)\s*(?:ROLLBACK|COMPENSATION|回滚|补偿)\s*[:：][\s\S]*?[\u3400-\u9fff]/i;

function normalizePath(filePath, root = process.cwd()) {
  const normalized = filePath.replace(/\\/g, "/");
  if (!normalized.startsWith("/") && !/^[A-Za-z]:\//.test(normalized)) return normalized;
  return relative(root, normalized).replace(/\\/g, "/");
}

function lineOf(content, index) {
  return content.slice(0, Math.max(index, 0)).split(/\r?\n/).length;
}

function addViolation(violations, file, content, index, ruleId, message) {
  violations.push({
    file,
    line: lineOf(content, index),
    ruleId,
    message,
  });
}

function tableCommentHasChinese(content, tableName) {
  const escaped = escapeRegExp(tableName);
  const pattern = new RegExp(
    `COMMENT\\s+ON\\s+TABLE\\s+${escaped}\\s+IS\\s+'([^']*)'`,
    "i",
  );
  const match = pattern.exec(content);
  return Boolean(match && CJK.test(match[1]));
}

function parseTables(content) {
  const pattern =
    /CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([\s\S]*?)\);/gi;
  const tables = [];
  let match;
  while ((match = pattern.exec(content))) {
    tables.push({
      name: match[1],
      block: match[2],
      index: match.index,
    });
  }
  return tables;
}

function parseIndexes(content) {
  const pattern =
    /CREATE\s+(?:UNIQUE\s+)?INDEX(?:\s+IF\s+NOT\s+EXISTS)?\s+([A-Za-z_][A-Za-z0-9_]*)\s+ON\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([\s\S]*?)\)\s*;/gi;
  const indexes = [];
  let match;
  while ((match = pattern.exec(content))) {
    indexes.push({
      name: match[1],
      table: match[2],
      columns: match[3],
      index: match.index,
    });
  }
  return indexes;
}

function scanMigrationContent(file, dialect, content) {
  const violations = [];
  const tables = parseTables(content);
  const indexes = parseIndexes(content);

  if (HIGH_RISK_SQL.test(content) && !ROLLBACK_NOTE.test(content)) {
    addViolation(
      violations,
      file,
      content,
      HIGH_RISK_SQL.exec(content)?.index ?? 0,
      "migration.rollback-plan",
      "高风险迁移必须提供中文 ROLLBACK/补偿说明。",
    );
  }

  for (const table of tables) {
    if (
      !/^mk_[a-z][a-z0-9]*_[a-z][a-z0-9_]*$/.test(table.name) &&
      !SYSTEM_TABLE_ALLOWLIST.has(table.name.toLowerCase())
    ) {
      addViolation(
        violations,
        file,
        content,
        table.index,
        "migration.table-name",
        "迁移表名必须使用 mk_<域>_<实体> 规约，避免 Oracle/JDBC 标识符映射漂移。",
      );
    }

    if (PRODUCTION_COMMENT_DIALECTS.has(dialect) && !tableCommentHasChinese(content, table.name)) {
      addViolation(
        violations,
        file,
        content,
        table.index,
        "migration.table-comment",
        "新增生产方言建表必须提供中文 COMMENT ON TABLE。",
      );
    }

    if (/\btenant_id\b/i.test(table.block)) {
      const hasTenantIndex = indexes
        .filter((index) => index.table.toLowerCase() === table.name.toLowerCase())
        .some((index) => /\btenant_id\b/i.test(index.columns));
      if (!hasTenantIndex) {
        addViolation(
          violations,
          file,
          content,
          table.index,
          "migration.tenant-index",
          "带 tenant_id 的业务表必须在同一迁移中声明租户索引。",
        );
      }
    }
  }

  for (const index of indexes) {
    if (!/^(idx|uk)_[a-z0-9_]+$/.test(index.name)) {
      addViolation(
        violations,
        file,
        content,
        index.index,
        "migration.index-name",
        "索引名必须使用 idx_<表>_<列> 或 uk_<表>_<列> 规约。",
      );
    }
  }

  const constraintPattern = /(?<!DROP\s)\bCONSTRAINT\s+([A-Za-z_][A-Za-z0-9_]*)/gi;
  let constraintMatch;
  while ((constraintMatch = constraintPattern.exec(content))) {
    const name = constraintMatch[1];
    if (!/^(pk|uk|fk|ck)_[a-z0-9_]+$/.test(name)) {
      addViolation(
        violations,
        file,
        content,
        constraintMatch.index,
        "migration.constraint-name",
        "约束名必须使用 pk_/uk_/fk_/ck_ 前缀规约。",
      );
    }
  }

  return violations;
}

export async function scanSqlFiles(root, files) {
  const violations = [];
  const scannedFiles = [];

  for (const rawFile of files) {
    const file = normalizePath(rawFile, root);
    const match = MIGRATION_SQL.exec(file);
    if (!match) continue;

    const fullPath = resolve(root, file);
    if (!existsSync(fullPath)) continue;

    const content = readFileSync(fullPath, "utf8");
    scannedFiles.push(file);
    violations.push(...scanMigrationContent(file, match[1], content));
  }

  return { scannedFiles, violations };
}

export function hasBlockingViolations(report) {
  return report.violations.length > 0;
}

function changedMigrationFiles(base) {
  execFileSync("git", ["fetch", "origin", "main"], { stdio: "ignore" });
  const mergeBase = execFileSync("git", ["merge-base", base, "HEAD"], { encoding: "utf8" }).trim();
  return execFileSync(
    "git",
    [
      "diff",
      "--name-only",
      "--diff-filter=AM",
      `${mergeBase}...HEAD`,
      "--",
      "medkernel-backend/src/main/resources/db/migration",
    ],
    { encoding: "utf8" },
  )
    .trim()
    .split(/\r?\n/)
    .filter(Boolean);
}

function allMigrationFiles() {
  return execFileSync(
    "git",
    ["ls-files", "medkernel-backend/src/main/resources/db/migration/**/*.sql"],
    { encoding: "utf8" },
  )
    .trim()
    .split(/\r?\n/)
    .filter(Boolean);
}

function printReport(report, mode) {
  console.log(`迁移规约门禁扫描：mode=${mode}，扫描文件 ${report.scannedFiles.length} 个。`);
  for (const violation of report.violations) {
    console.log(
      `${violation.file}:${violation.line} ${violation.ruleId} ${violation.message}`,
    );
  }
  if (report.violations.length === 0) {
    console.log("迁移规约门禁通过：未发现阻断项。");
  }
}

function parseArgs(argv) {
  const options = { mode: "changed", base: "origin/main", files: [] };
  for (const arg of argv) {
    if (arg.startsWith("--mode=")) {
      options.mode = arg.slice("--mode=".length);
      continue;
    }
    if (arg.startsWith("--base=")) {
      options.base = arg.slice("--base=".length);
      continue;
    }
    options.files.push(arg);
  }
  return options;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  let files;
  if (options.mode === "changed") {
    files = changedMigrationFiles(options.base);
  } else if (options.mode === "inventory") {
    files = allMigrationFiles();
  } else if (options.mode === "files") {
    files = options.files;
  } else {
    console.error(`未知模式：${options.mode}`);
    process.exit(2);
  }

  const report = await scanSqlFiles(process.cwd(), files);
  printReport(report, options.mode);

  if (options.mode !== "inventory" && hasBlockingViolations(report)) {
    process.exit(1);
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await main();
}
