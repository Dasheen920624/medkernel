import { describe, expect, it } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const SRC_ROOT = join(process.cwd(), "src");
const INLINE_STYLE_PATTERN = /style=\{\{/;
const DIRECT_BROWSER_STORAGE_PATTERN = /\b(?:window\.)?(?:localStorage|sessionStorage)\s*\./;
const CONSOLE_PATTERN = /\bconsole\./;
const DIRECT_NETWORK_CLIENT_PATTERN = /\b(?:axios\.create\s*\(|fetch\s*\(\s*["'`]https?:\/\/)/;
const EXTERNAL_SERVICE_TARGET_PATTERN =
  /\b(?:baseURL\s*:\s*["'`]https?:\/\/|["'`](?:https?:\/\/[^"'`]*(?:dify|neo4j|openai|anthropic)|(?:bolt|neo4j):\/\/))/i;
const FRONTEND_EXTERNAL_FEATURE_FLAG_PATTERN =
  /\bimport\.meta\.env\.VITE_[A-Z0-9_]*(?:DIFY|MODEL|PROVIDER|GRAPH|NEO4J|OPENAI|EXTERNAL)[A-Z0-9_]*/i;
const CSS_LITERAL_PATTERN = /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(|\b\d+(?:\.\d+)?px\b/;

const ALLOWED_DIRECT_STORAGE_FILES = new Set(["shared/lib/browserStorage.ts"]);
const ALLOWED_NETWORK_CLIENT_FILES = new Set(["shared/api/client.ts"]);
const IGNORED_FILE_PATTERN = /\.(test|spec)\.(ts|tsx)$/;

function listSourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const fullPath = join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) return listSourceFiles(fullPath);
    if (!/\.(ts|tsx)$/.test(entry)) return [];
    if (IGNORED_FILE_PATTERN.test(entry)) return [];
    return [fullPath];
  });
}

function listModuleCssFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const fullPath = join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) return listModuleCssFiles(fullPath);
    return entry.endsWith(".module.css") ? [fullPath] : [];
  });
}

function findViolations(pattern: RegExp, allowList = new Set<string>()) {
  return listSourceFiles(SRC_ROOT)
    .map((file) => ({
      file: relative(SRC_ROOT, file).replace(/\\/g, "/"),
      content: readFileSync(file, "utf8"),
    }))
    .filter(({ file, content }) => !allowList.has(file) && pattern.test(content))
    .map(({ file }) => file)
    .sort();
}

describe("前端视觉债、存储与外部连接门禁", () => {
  it("生产 TSX 不再包含 JSX inline style", () => {
    expect(findViolations(INLINE_STYLE_PATTERN)).toEqual([]);
  });

  it("生产代码不直接访问浏览器存储或 console", () => {
    expect(findViolations(DIRECT_BROWSER_STORAGE_PATTERN, ALLOWED_DIRECT_STORAGE_FILES)).toEqual(
      [],
    );
    expect(findViolations(CONSOLE_PATTERN)).toEqual([]);
  });

  it("生产代码只经统一 API client 调用后端，不从浏览器直连外部能力", () => {
    expect(findViolations(DIRECT_NETWORK_CLIENT_PATTERN, ALLOWED_NETWORK_CLIENT_FILES)).toEqual([]);
    expect(findViolations(EXTERNAL_SERVICE_TARGET_PATTERN)).toEqual([]);
    expect(findViolations(FRONTEND_EXTERNAL_FEATURE_FLAG_PATTERN)).toEqual([]);
  });

  it("体验视图在异步导出前校验敏感内容", () => {
    const viewSource = readFileSync(join(SRC_ROOT, "shared/ui/experienceView.ts"), "utf8");

    expect(viewSource).toContain("assertNoSensitiveSnapshotContent(request)");
    for (const term of ["token", "patient", "idcard", "identity", "身份证", "患者"]) {
      expect(viewSource).toContain(term);
    }
  });

  it("CSS Modules 只能引用设计 token，不写硬编码颜色或 px", () => {
    const violations = listModuleCssFiles(SRC_ROOT)
      .map((file) => ({
        file: relative(SRC_ROOT, file).replace(/\\/g, "/"),
        content: readFileSync(file, "utf8"),
      }))
      .filter(({ content }) => CSS_LITERAL_PATTERN.test(content))
      .map(({ file }) => file)
      .sort();

    expect(violations).toEqual([]);
  });
});
