#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const FRONTEND_SOURCE = /^frontend\/src\/(?:pages|features)\/.+\.(?:ts|tsx)$/;
const FRONTEND_SHARED_API = /^frontend\/src\/shared\/api\/.+\.(?:ts|tsx)$/;
const FRONTEND_CSS = /^frontend\/src\/.+\.module\.css$/;
const BACKEND_JAVA = /^medkernel-backend\/src\/main\/java\/.+\.java$/;
const FRONTEND_ALLOWLIST =
  /\.(?:test|spec|stories)\.(?:ts|tsx)$|^frontend\/src\/(?:test|mocks)\//;

const FRONTEND_RULES = [
  {
    ruleId: "frontend.no-medkernel-disable",
    message: "前端生产文件禁止使用 eslint-disable medkernel/* 绕过真实性门禁。",
    pattern: /eslint-disable(?:-next-line|-line)?\s+[^*\n]*medkernel\//m,
  },
  {
    ruleId: "frontend.mock-bypass-language",
    message: "前端生产文件禁止用规避门禁话术包装本地假数据闭环。",
    pattern:
      /规避\s*(?:no-page-mock|真实性门禁)|防止\s*ESLint\s*AST\s*扫描|防\s*ESLint\s*静态\s*AST\s*检测|通过\s*AST\s*门禁|杜绝硬编码\s*Mock/i,
  },
  {
    ruleId: "frontend.mock-import",
    message: "前端生产文件禁止引入 mock / fixture / MockAdapter。",
    pattern: /\bMockAdapter\b|from\s+["'][^"']*(?:mock|mocks|fixture|fixtures)[^"']*["']/i,
  },
  {
    ruleId: "frontend.hardcoded-medical-constant",
    message: "前端生产文件禁止写死疾病、药品、编码等医学常量。",
    pattern: /高血压|糖尿病|DRUG-001|I10|E11|J18|肺炎|心梗|脑卒中/,
  },
  {
    ruleId: "frontend.technical-object-visible",
    message: "客户面默认视图禁止裸露 JSON / font-mono 等技术对象。",
    pattern: /font-mono|<pre\b[\s\S]{0,240}JSON\.stringify|JSON\.stringify[\s\S]{0,120}<\/pre>/m,
  },
  {
    ruleId: "frontend.random-business-value",
    message: "前端生产文件禁止使用 Math.random() 伪造业务值、trace 或指标。",
    pattern: /Math\.random\s*\(/,
  },
  {
    ruleId: "frontend.fake-hash",
    message: "前端生产文件禁止伪造 hash 或证据指纹。",
    pattern: /SHA-256-MOCK-HASH|fake(?:Hash|hash)|randHash|sha256-[^"'`+]*\s*\+\s*Math\.floor/i,
  },
  {
    ruleId: "frontend.catch-success",
    message: "前端生产文件禁止 catch 后 message.success 或返回成功，失败必须诚实暴露。",
    catchBlockPattern: /(?:message\.success|return\s+(?:success|ApiResult\.success|ResponseEntity\.ok))/m,
  },
];

const FRONTEND_SHARED_API_RULES = [
  {
    ruleId: "frontend.demo-snapshot-export",
    message: "共享 API 层禁止导出演示/模拟快照供生产页面调用，页面必须读取真实接口或诚实空态。",
    pattern: /\b(?:DEMO|MOCK|FAKE)_?[A-Z0-9_]*\s*=\s*\[/,
  },
  {
    ruleId: "frontend.mock-import",
    message: "共享 API 层禁止引入 mock / fixture / MockAdapter。",
    pattern: /\bMockAdapter\b|from\s+["'][^"']*(?:mock|mocks|fixture|fixtures)[^"']*["']/i,
  },
  {
    ruleId: "frontend.random-business-value",
    message: "共享 API 层禁止使用 Math.random() 伪造业务值、trace 或指标。",
    pattern: /Math\.random\s*\(/,
  },
];

const FRONTEND_CSS_RULES = [
  {
    ruleId: "frontend.css-hardcoded-color",
    message: "CSS Module 禁止 hex/rgb/hsl 字面量，颜色必须走设计 token 变量。",
    pattern: /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/,
  },
  {
    ruleId: "frontend.css-hardcoded-px-token",
    message: "CSS Module 禁止字号/圆角 px token 硬编码，必须走设计 token 变量。",
    pattern: /\b(?:border-radius|font-size)\s*:\s*\d+(?:\.\d+)?px\b/,
  },
];

const BACKEND_RULES = [
  {
    ruleId: "backend.random-business-value",
    message: "后端生产代码禁止使用 Math.random() 伪造业务值、RTT、健康分或重试结果。",
    pattern: /Math\.random\s*\(/,
  },
  {
    ruleId: "backend.hardcoded-medical-constant",
    message: "后端生产代码禁止写死疾病、药品、编码等医学常量。",
    pattern: /高血压|糖尿病|DRUG-001|I10|E11|J18|肺炎|心梗|脑卒中/,
  },
  {
    ruleId: "backend.catch-success",
    message: "后端生产代码禁止 catch 后返回 success / ok 伪造成功。",
    catchBlockPattern: /return\s+(?:ApiResult\.success|ResponseEntity\.ok|success)\b/m,
  },
  {
    ruleId: "backend.uuid-as-hash",
    message: "后端生产代码禁止用 UUID 伪造数据完整性 hash。",
    pattern:
      /(?:hash|Hash|HASH)[\s\S]{0,160}UUID\.randomUUID\s*\(\s*\)\.toString\s*\(\s*\)|UUID\.randomUUID\s*\(\s*\)\.toString\s*\(\s*\)[\s\S]{0,160}(?:hash|Hash|HASH)/m,
  },
  {
    ruleId: "backend.placeholder-javadoc",
    message: "后端生产 Javadoc 禁止出现模拟、仿真、演示、占位或 placeholder。",
    javadocBlockPattern: /模拟|仿真|演示|占位|placeholder/i,
  },
];

function normalizePath(filePath, root = process.cwd()) {
  const normalized = filePath.replace(/\\/g, "/");
  if (!normalized.startsWith("/") && !/^[A-Za-z]:\//.test(normalized)) return normalized;
  return relative(root, normalized).replace(/\\/g, "/");
}

function lineOf(content, index) {
  return content.slice(0, Math.max(index, 0)).split(/\r?\n/).length;
}

function firstMatch(content, rule) {
  if (rule.catchBlockPattern) return firstCatchBlockMatch(content, rule.catchBlockPattern);
  if (rule.javadocBlockPattern) return firstJavadocBlockMatch(content, rule.javadocBlockPattern);
  const match = rule.pattern.exec(content);
  if (!match) return null;
  return { index: match.index, text: match[0] };
}

function firstJavadocBlockMatch(content, pattern) {
  const javadocPattern = /\/\*\*[\s\S]*?\*\//g;
  let match;
  while ((match = javadocPattern.exec(content))) {
    if (pattern.test(match[0])) {
      return { index: match.index, text: match[0] };
    }
  }
  return null;
}

function firstCatchBlockMatch(content, pattern) {
  const catchPattern = /\bcatch\s*(?:\([^)]*\))?\s*\{/g;
  let match;
  while ((match = catchPattern.exec(content))) {
    const openIndex = catchPattern.lastIndex - 1;
    const closeIndex = findMatchingBrace(content, openIndex);
    if (closeIndex === -1) break;

    const block = content.slice(openIndex + 1, closeIndex);
    if (pattern.test(block)) {
      return { index: match.index, text: content.slice(match.index, closeIndex + 1) };
    }
    catchPattern.lastIndex = closeIndex + 1;
  }
  return null;
}

function findMatchingBrace(content, openIndex) {
  let depth = 0;
  let state = "code";
  let quote = "";
  let escaped = false;

  for (let index = openIndex; index < content.length; index += 1) {
    const char = content[index];
    const next = content[index + 1];

    if (state === "line-comment") {
      if (char === "\n") state = "code";
      continue;
    }

    if (state === "block-comment") {
      if (char === "*" && next === "/") {
        state = "code";
        index += 1;
      }
      continue;
    }

    if (state === "string") {
      if (escaped) {
        escaped = false;
        continue;
      }
      if (char === "\\") {
        escaped = true;
        continue;
      }
      if (char === quote) state = "code";
      continue;
    }

    if (char === "/" && next === "/") {
      state = "line-comment";
      index += 1;
      continue;
    }
    if (char === "/" && next === "*") {
      state = "block-comment";
      index += 1;
      continue;
    }
    if (char === '"' || char === "'" || char === "`") {
      state = "string";
      quote = char;
      continue;
    }
    if (char === "{") {
      depth += 1;
      continue;
    }
    if (char === "}") {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

function addRuleViolations(violations, file, content, rules) {
  for (const rule of rules) {
    const match = firstMatch(content, rule);
    if (!match) continue;
    violations.push({
      file,
      line: lineOf(content, match.index),
      ruleId: rule.ruleId,
      message: rule.message,
    });
  }
}

function rulesForFile(file) {
  if (FRONTEND_ALLOWLIST.test(file)) return [];
  if (FRONTEND_SOURCE.test(file)) return FRONTEND_RULES;
  if (FRONTEND_SHARED_API.test(file)) return FRONTEND_SHARED_API_RULES;
  if (FRONTEND_CSS.test(file)) return FRONTEND_CSS_RULES;
  if (BACKEND_JAVA.test(file)) return BACKEND_RULES;
  return [];
}

export async function scanFiles(root, files) {
  const violations = [];
  const scannedFiles = [];

  for (const rawFile of files) {
    const file = normalizePath(rawFile, root);
    const rules = rulesForFile(file);
    if (rules.length === 0) continue;

    const fullPath = resolve(root, file);
    if (!existsSync(fullPath)) continue;

    const content = readFileSync(fullPath, "utf8");
    scannedFiles.push(file);
    addRuleViolations(violations, file, content, rules);
  }

  return { scannedFiles, violations };
}

export function hasBlockingViolations(report) {
  return report.violations.length > 0;
}

function git(root, args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8" }).trim();
}

function listTrackedFiles(root) {
  const output = git(root, ["ls-files"]);
  return output ? output.split(/\r?\n/) : [];
}

function listChangedFiles(root, base) {
  const candidates = [
    ["diff", "--name-only", "--diff-filter=ACMR", `${base}...HEAD`],
    ["diff", "--name-only", "--diff-filter=ACMR", `${base}..HEAD`],
    ["diff", "--name-only", "--diff-filter=ACMR", "HEAD^..HEAD"],
  ];

  for (const args of candidates) {
    try {
      const output = git(root, args);
      return output ? output.split(/\r?\n/) : [];
    } catch {
      // 尝试下一个 base 形式。
    }
  }
  return [];
}

function resolveBase(root, explicitBase) {
  if (explicitBase) return explicitBase;
  if (process.env.GITHUB_BASE_REF) return `origin/${process.env.GITHUB_BASE_REF}`;
  try {
    git(root, ["rev-parse", "--verify", "origin/main"]);
    return "origin/main";
  } catch {
    return "HEAD^";
  }
}

function summarizeByRule(violations) {
  const groups = new Map();
  for (const violation of violations) {
    const group = groups.get(violation.ruleId) ?? { count: 0, files: new Set() };
    group.count += 1;
    group.files.add(violation.file);
    groups.set(violation.ruleId, group);
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
}

function printReport(report, { mode }) {
  console.log(`真实性门禁扫描：mode=${mode}，扫描文件 ${report.scannedFiles.length} 个。`);

  if (report.violations.length === 0) {
    console.log("真实性门禁通过：未发现阻断项。");
    return;
  }

  console.log(`真实性门禁发现 ${report.violations.length} 个阻断项：`);
  for (const violation of report.violations.slice(0, mode === "inventory" ? 50 : undefined)) {
    console.log(`${violation.file}:${violation.line} [${violation.ruleId}] ${violation.message}`);
    if (process.env.GITHUB_ACTIONS && mode !== "inventory") {
      console.log(
        `::error file=${violation.file},line=${violation.line},title=${violation.ruleId}::${violation.message}`,
      );
    }
  }

  if (mode === "inventory" && report.violations.length > 50) {
    console.log(`... 其余 ${report.violations.length - 50} 个阻断项省略，按规则汇总见下。`);
  }

  console.log("按规则汇总：");
  for (const [ruleId, group] of summarizeByRule(report.violations)) {
    const files = [...group.files].slice(0, 10).join(", ");
    const suffix = group.files.size > 10 ? ` 等 ${group.files.size} 个文件` : "";
    console.log(`- ${ruleId}: ${group.count} 项；${files}${suffix}`);
  }
}

function parseArgs(argv) {
  const options = { mode: "changed", base: undefined };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--mode") {
      options.mode = argv[i + 1];
      i += 1;
    } else if (arg.startsWith("--mode=")) {
      options.mode = arg.slice("--mode=".length);
    } else if (arg === "--base") {
      options.base = argv[i + 1];
      i += 1;
    } else if (arg.startsWith("--base=")) {
      options.base = arg.slice("--base=".length);
    }
  }
  return options;
}

async function main() {
  const root = process.cwd();
  const options = parseArgs(process.argv.slice(2));
  if (!["changed", "all", "inventory"].includes(options.mode)) {
    throw new Error(`未知 mode：${options.mode}`);
  }

  const files =
    options.mode === "changed"
      ? listChangedFiles(root, resolveBase(root, options.base))
      : listTrackedFiles(root);
  const report = await scanFiles(root, files);
  printReport(report, options);

  if (options.mode !== "inventory" && hasBlockingViolations(report)) {
    process.exitCode = 1;
  }
}

const currentModulePath = fileURLToPath(import.meta.url);
if (process.argv[1] && resolve(process.argv[1]) === currentModulePath) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
