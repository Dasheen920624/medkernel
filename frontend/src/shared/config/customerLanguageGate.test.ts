import { readFileSync, readdirSync } from "node:fs";
import { extname, join, relative, resolve } from "node:path";
import ts from "typescript";
import { describe, expect, it } from "vitest";

import {
  customerDisplayText,
  customerEnumLabel,
  customerSafeDisplayText,
  hasTechnicalDetailText,
} from "./customerLabels";

const sourceRoot = resolve(process.cwd(), "src");
const scanRoots = [
  "pages/clinical",
  "pages/compliance",
  "pages/quality",
  "pages/tenant",
  "shared/ui",
  "widgets",
];
const visibleAttributeNames = new Set([
  "aria-label",
  "content",
  "description",
  "emptyText",
  "label",
  "message",
  "placeholder",
  "title",
]);
const forbiddenCustomerTokens =
  /\b(?:traceId|Trace ID|Provider|NOT_CONNECTED|TENANT|ASSET|MEDIUM)\b/;
const technicalFormatTokens = /\b(?:JSON|DSL)\b/;
const expertFormatFiles = new Set([
  "pages/tenant/AdapterHub.tsx",
  "pages/tenant/PathwayTemplates.tsx",
  "pages/tenant/RuleDefinitions.tsx",
]);

describe("customer language gate", () => {
  it("translates standalone and embedded backend enum values", () => {
    expect(customerEnumLabel("NOT_CONNECTED")).toBe("未接通");
    expect(customerDisplayText("HIS 适配器仍为 NOT_CONNECTED")).toBe("HIS 适配器仍为 未接通");
    expect(customerDisplayText("权限维度 ASSET 缺少授权")).toBe("权限维度 治理资产 缺少授权");
    expect(customerDisplayText("MEDIUM")).toBe("中风险");
  });

  it("replaces raw technical details with customer-safe fallback text", () => {
    const rawError = "GET /api/v1/terminology failed: ECONNREFUSED 127.0.0.1:8080";

    expect(hasTechnicalDetailText(rawError)).toBe(true);
    expect(customerSafeDisplayText(rawError, "数据读取失败，请联系信息科。")).toBe(
      "数据读取失败，请联系信息科。",
    );
    expect(customerSafeDisplayText("HIS 适配器仍为 NOT_CONNECTED", "数据读取失败")).toBe(
      "HIS 适配器仍为 未接通",
    );
  });

  it("keeps raw technical tokens out of customer-visible source strings", () => {
    const violations = customerSourceFiles().flatMap(scanVisibleStrings);
    expect(violations).toEqual([]);
  });
});

function customerSourceFiles(): string[] {
  return scanRoots.flatMap((root) => walk(resolve(sourceRoot, root)));
}

function walk(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return walk(path);
    if (extname(entry.name) !== ".tsx" || entry.name.endsWith(".test.tsx")) return [];
    return [path];
  });
}

function scanVisibleStrings(path: string): string[] {
  const sourceText = readFileSync(path, "utf8");
  const source = ts.createSourceFile(
    path,
    sourceText,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  );
  const violations: string[] = [];
  const sourcePath = relative(sourceRoot, path);

  const record = (node: ts.Node, text: string) => {
    const hasForbiddenToken = forbiddenCustomerTokens.test(text);
    const hasForbiddenFormat =
      !expertFormatFiles.has(sourcePath) && technicalFormatTokens.test(text);
    if (!hasForbiddenToken && !hasForbiddenFormat) return;
    const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
    violations.push(`${sourcePath}:${line} ${text.trim()}`);
  };

  const visit = (node: ts.Node) => {
    if (ts.isJsxText(node)) {
      record(node, node.text);
    } else if (ts.isJsxAttribute(node) && visibleAttributeNames.has(node.name.getText(source))) {
      if (node.initializer && ts.isStringLiteral(node.initializer)) {
        record(node, node.initializer.text);
      }
    } else if (
      ts.isPropertyAssignment(node) &&
      visibleAttributeNames.has(node.name.getText(source))
    ) {
      if (
        ts.isStringLiteral(node.initializer) ||
        ts.isNoSubstitutionTemplateLiteral(node.initializer)
      ) {
        record(node, node.initializer.text);
      }
    } else if (
      ts.isCallExpression(node) &&
      ts.isPropertyAccessExpression(node.expression) &&
      ["error", "info", "success", "warning"].includes(node.expression.name.text)
    ) {
      const firstArgument = node.arguments[0];
      if (
        firstArgument &&
        (ts.isStringLiteral(firstArgument) || ts.isNoSubstitutionTemplateLiteral(firstArgument))
      ) {
        record(firstArgument, firstArgument.text);
      }
    }

    ts.forEachChild(node, visit);
  };

  visit(source);
  return violations;
}
