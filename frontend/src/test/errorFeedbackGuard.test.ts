import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

const SRC_ROOT = join(process.cwd(), "src");
const IGNORED_FILE_PATTERN = /\.(test|spec)\.(ts|tsx)$/;

const forbiddenPatterns = [
  {
    name: "局部错误消息函数",
    pattern: /function\s+(?:getApiErrorMessage|apiErrorMessage)\s*\(/,
  },
  {
    name: "局部 ApiError 类型",
    pattern: /\b(?:type|interface)\s+ApiError(?:Response|Like)?\b/,
  },
  {
    name: "页面直接读取 ProblemDetail 消息",
    pattern: /response\??\.\s*data\??\.\s*(?:detail|message)/,
  },
  {
    name: "页面内联 ApiError 类型断言",
    pattern: /as\s+\{\s*response\??:\s*\{\s*data\??:\s*\{\s*(?:detail|message)/,
  },
];

function listProductionFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const fullPath = join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) return listProductionFiles(fullPath);
    if (!/\.(ts|tsx)$/.test(entry)) return [];
    if (IGNORED_FILE_PATTERN.test(entry)) return [];
    return [fullPath];
  });
}

describe("error feedback guard", () => {
  it("keeps production pages on the shared ProblemDetail parser", () => {
    const offenders = [join(SRC_ROOT, "pages"), join(SRC_ROOT, "features")]
      .flatMap((dir) => listProductionFiles(dir))
      .flatMap((file) => {
        const source = readFileSync(file, "utf8");
        return forbiddenPatterns
          .filter(({ pattern }) => pattern.test(source))
          .map(({ name }) => `${relative(SRC_ROOT, file).replace(/\\/g, "/")}：${name}`);
      });

    expect(offenders).toEqual([]);
  });
});
